/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.telecom;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.os.Trace;
import android.os.UserHandle;
import android.telecom.CallAudioState;
import android.telecom.ConnectionService;
import android.telecom.DefaultDialerManager;
import android.telecom.InCallService;
import android.telecom.ParcelableCall;
import android.telecom.TelecomManager;
import android.telecom.VideoCallImpl;
import android.text.TextUtils;
import android.util.ArrayMap;

import com.android.internal.annotations.VisibleForTesting;
// TODO: Needed for move to system service: import com.android.internal.R;
import com.android.internal.telecom.IInCallService;
import com.android.internal.util.IndentingPrintWriter;
import com.android.server.telecom.SystemStateProvider.SystemStateListener;
import com.android.server.telecom.TelecomServiceImpl.DefaultDialerManagerAdapter;
import com.mediatek.telecom.PerformanceTracker;
// M: Add for upgrade debug
import com.mediatek.telecom.TelecomUtils;
import com.mediatek.telecom.ext.ExtensionManager;
import com.mediatek.telecom.recording.PhoneRecorderHandler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Binds to {@link IInCallService} and provides the service to {@link CallsManager} through which it
 * can send updates to the in-call app. This class is created and owned by CallsManager and retains
 * a binding to the {@link IInCallService} (implemented by the in-call app).
 */
public final class InCallController extends CallsManagerListenerBase {

    public class InCallServiceConnection {
        public class Listener {
            public void onDisconnect(InCallServiceConnection conn) {}
        }

        protected Listener mListener;

        public boolean connect(Call call) { return false; }
        public void disconnect() {}
        public void setHasEmergency(boolean hasEmergency) {}
        public void setListener(Listener l) {
            mListener = l;
        }
        public void dump(IndentingPrintWriter pw) {}
    }

    private class InCallServiceInfo {
        private final ComponentName mComponentName;
        private boolean mIsExternalCallsSupported;
        private final int mType;

        public InCallServiceInfo(ComponentName componentName,
                boolean isExternalCallsSupported,
                int type) {
            mComponentName = componentName;
            mIsExternalCallsSupported = isExternalCallsSupported;
            mType = type;
        }

        public ComponentName getComponentName() {
            return mComponentName;
        }

        public boolean isExternalCallsSupported() {
            return mIsExternalCallsSupported;
        }

        public int getType() {
            return mType;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            InCallServiceInfo that = (InCallServiceInfo) o;

            if (mIsExternalCallsSupported != that.mIsExternalCallsSupported) {
                return false;
            }
            return mComponentName.equals(that.mComponentName);

        }

        @Override
        public int hashCode() {
            return Objects.hash(mComponentName, mIsExternalCallsSupported);
        }

        @Override
        public String toString() {
            return "[" + mComponentName + " supportsExternal? " + mIsExternalCallsSupported + "]";
        }
    }

    private class InCallServiceBindingConnection extends InCallServiceConnection {

        private final ServiceConnection mServiceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                Log.startSession("ICSBC.oSC");
                synchronized (mLock) {
                    try {
                        Log.d(this, "onServiceConnected: %s %b %b", name, mIsBound, mIsConnected);
                        ///M: [Performance Track] @{
                        for (Call call : mCallsManager.getCalls()) {
                            PerformanceTracker.getInstance().trackMO(call.getId(),
                                    PerformanceTracker.MO_BIND_INCALL_SERVICE_END);
                        }
                        ///@}

                        mIsBound = true;
                        if (mIsConnected) {
                            // Only proceed if we are supposed to be connected.
                            onConnected(service);
                        }
                    } finally {
                        Log.endSession();
                    }
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                Log.startSession("ICSBC.oSD");
                synchronized (mLock) {
                    try {
                        Log.d(this, "onDisconnected: %s", name);
                        mIsBound = false;
                        onDisconnected();
                    } finally {
                        Log.endSession();
                    }
                }
            }
        };

        private final InCallServiceInfo mInCallServiceInfo;
        private boolean mIsConnected = false;
        private boolean mIsBound = false;

        public InCallServiceBindingConnection(InCallServiceInfo info) {
            mInCallServiceInfo = info;
        }

        @Override
        public boolean connect(Call call) {
            if (mIsConnected) {
                Log.event(call, Log.Events.INFO, "Already connected, ignoring request.");
                return true;
            }

            Intent intent = new Intent(InCallService.SERVICE_INTERFACE);
            intent.setComponent(mInCallServiceInfo.getComponentName());
            if (call != null && !call.isIncoming() && !call.isExternalCall()){
                intent.putExtra(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS,
                        call.getIntentExtras());
                intent.putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE,
                        call.getTargetPhoneAccount());
            }

            Log.i(this, "Attempting to bind to InCall %s, with %s", mInCallServiceInfo, intent);
            mIsConnected = true;
            ///M: [Performance Track] @{
            if (call != null && call.isOutgoing()) {
                PerformanceTracker.getInstance().trackMO(
                        call.getId(), PerformanceTracker.MO_BIND_INCALL_SERVICE_BEGIN);
            }
            ///@}

            if (!mContext.bindServiceAsUser(intent, mServiceConnection,
                        Context.BIND_AUTO_CREATE | Context.BIND_FOREGROUND_SERVICE,
                        UserHandle.CURRENT)) {
                Log.w(this, "Failed to connect.");
                mIsConnected = false;
                ///M: [Performance Track] @{
                if (call != null && call.isOutgoing()) {
                    PerformanceTracker.discardData(call.getId(), PerformanceTracker.TEST_MO);
                }
                ///@}
            }


            if (call != null && mIsConnected) {
                call.getAnalytics().addInCallService(
                        mInCallServiceInfo.getComponentName().flattenToShortString(),
                        mInCallServiceInfo.getType());
            }

            /// M: Register voice recording listener @{
            if (mIsConnected) {
                PhoneRecorderHandler.getInstance().setListener(mRecorderListener);
            }
            /// @}

            return mIsConnected;
        }

        @Override
        public void disconnect() {
            if (mIsConnected) {
                mContext.unbindService(mServiceConnection);
                mIsConnected = false;
            } else {
                Log.event(null, Log.Events.INFO, "Already disconnected, ignoring request.");
            }
        }

        @Override
        public void dump(IndentingPrintWriter pw) {
            pw.append("BindingConnection [");
            pw.append(mIsConnected ? "" : "not ").append("connected, ");
            pw.append(mIsBound ? "" : "not ").append("bound]\n");
        }

        protected void onConnected(IBinder service) {
            boolean shouldRemainConnected =

                    InCallController.this.onConnected(mInCallServiceInfo, service);
                    Log.d(this, "shouldRemainConntected = " + shouldRemainConnected);

            if (!shouldRemainConnected) {
                // Sometimes we can opt to disconnect for certain reasons, like if the
                // InCallService rejected our initialization step, or the calls went away
                // in the time it took us to bind to the InCallService. In such cases, we go
                // ahead and disconnect ourselves.
                disconnect();
                //M: the connection has been disconnect, remove InCallController's connection.
                InCallController.this.mInCallServiceConnection = null;
            }
        }

        protected void onDisconnected() {
            InCallController.this.onDisconnected(mInCallServiceInfo.getComponentName());
            disconnect();  // Unbind explicitly if we get disconnected.
            if (mListener != null) {
                mListener.onDisconnect(InCallServiceBindingConnection.this);
            }
        }
    }

    /**
     * A version of the InCallServiceBindingConnection that proxies all calls to a secondary
     * connection until it finds an emergency call, or the other connection dies. When one of those
     * two things happen, this class instance will take over the connection.
     */
    private class EmergencyInCallServiceConnection extends InCallServiceBindingConnection {
        private boolean mIsProxying = true;
        private boolean mIsConnected = false;
        private final InCallServiceConnection mSubConnection;

        private Listener mSubListener = new Listener() {
            @Override
            public void onDisconnect(InCallServiceConnection subConnection) {
                if (subConnection == mSubConnection) {
                    if (mIsConnected && mIsProxying) {
                        // At this point we know that we need to be connected to the InCallService
                        // and we are proxying to the sub connection.  However, the sub-connection
                        // just died so we need to stop proxying and connect to the system in-call
                        // service instead.
                        mIsProxying = false;
                        connect(null);
                    }
                }
            }
        };

        public EmergencyInCallServiceConnection(
                InCallServiceInfo info, InCallServiceConnection subConnection) {

            super(info);
            mSubConnection = subConnection;
            if (mSubConnection != null) {
                mSubConnection.setListener(mSubListener);
            }
            mIsProxying = (mSubConnection != null);
        }

        @Override
        public boolean connect(Call call) {
            mIsConnected = true;
            if (mIsProxying) {
                if (mSubConnection.connect(call)) {
                    return true;
                }
                // Could not connect to child, stop proxying.
                mIsProxying = false;
            }

            // If we are here, we didn't or could not connect to child. So lets connect ourselves.
            return super.connect(call);
        }

        @Override
        public void disconnect() {
            Log.i(this, "Disconnect forced!");
            if (mIsProxying) {
                mSubConnection.disconnect();
            } else {
                super.disconnect();
            }
            mIsConnected = false;
        }

        @Override
        public void setHasEmergency(boolean hasEmergency) {
            if (hasEmergency) {
                takeControl();
            }
        }

        @Override
        protected void onDisconnected() {
            // Save this here because super.onDisconnected() could force us to explicitly
            // disconnect() as a cleanup step and that sets mIsConnected to false.
            boolean shouldReconnect = mIsConnected;
            super.onDisconnected();
            // We just disconnected.  Check if we are expected to be connected, and reconnect.
            if (shouldReconnect && !mIsProxying) {
                connect(null);  // reconnect
            }
        }

        @Override
        public void dump(IndentingPrintWriter pw) {
            pw.println("Emergency ICS Connection");
            pw.increaseIndent();
            pw.print("Emergency: ");
            super.dump(pw);
            if (mSubConnection != null) {
                pw.print("Default-Dialer: ");
                mSubConnection.dump(pw);
            }
            pw.decreaseIndent();
        }

        /**
         * Forces the connection to take control from it's subConnection.
         */
        private void takeControl() {
            if (mIsProxying) {
                mIsProxying = false;
                if (mIsConnected) {
                    mSubConnection.disconnect();
                    super.connect(null);
                }
            }
        }
    }

    /**
     * A version of InCallServiceConnection which switches UI between two separate sub-instances of
     * InCallServicesConnections.
     */
    private class CarSwappingInCallServiceConnection extends InCallServiceConnection {
        private final InCallServiceConnection mDialerConnection;
        private final InCallServiceConnection mCarModeConnection;
        private InCallServiceConnection mCurrentConnection;
        private boolean mIsCarMode = false;
        private boolean mIsConnected = false;

        public CarSwappingInCallServiceConnection(
                InCallServiceConnection dialerConnection,
                InCallServiceConnection carModeConnection) {
            mDialerConnection = dialerConnection;
            mCarModeConnection = carModeConnection;
            mCurrentConnection = getCurrentConnection();
        }

        public synchronized void setCarMode(boolean isCarMode) {
            Log.i(this, "carmodechange: " + mIsCarMode + " => " + isCarMode);
            if (isCarMode != mIsCarMode) {
                mIsCarMode = isCarMode;
                InCallServiceConnection newConnection = getCurrentConnection();
                if (newConnection != mCurrentConnection) {
                    if (mIsConnected) {
                        mCurrentConnection.disconnect();
                        newConnection.connect(null);
                    }
                    mCurrentConnection = newConnection;
                }
            }
        }

        @Override
        public boolean connect(Call call) {
            if (mIsConnected) {
                Log.i(this, "already connected");
                return true;
            } else {
                if (mCurrentConnection.connect(call)) {
                    mIsConnected = true;
                    return true;
                }
            }

            return false;
        }

        @Override
        public void disconnect() {
            if (mIsConnected) {
                mCurrentConnection.disconnect();
                mIsConnected = false;
            } else {
                Log.i(this, "already disconnected");
            }
        }

        @Override
        public void setHasEmergency(boolean hasEmergency) {
            if (mDialerConnection != null) {
                mDialerConnection.setHasEmergency(hasEmergency);
            }
            if (mCarModeConnection != null) {
                mCarModeConnection.setHasEmergency(hasEmergency);
            }
        }

        @Override
        public void dump(IndentingPrintWriter pw) {
            pw.println("Car Swapping ICS");
            pw.increaseIndent();
            if (mDialerConnection != null) {
                pw.print("Dialer: ");
                mDialerConnection.dump(pw);
            }
            if (mCarModeConnection != null) {
                pw.print("Car Mode: ");
                mCarModeConnection.dump(pw);
            }
        }

        private InCallServiceConnection getCurrentConnection() {
            if (mIsCarMode && mCarModeConnection != null) {
                return mCarModeConnection;
            } else {
                return mDialerConnection;
            }
        }
    }

    private class NonUIInCallServiceConnectionCollection extends InCallServiceConnection {
        private final List<InCallServiceBindingConnection> mSubConnections;

        public NonUIInCallServiceConnectionCollection(
                List<InCallServiceBindingConnection> subConnections) {
            mSubConnections = subConnections;
        }

        @Override
        public boolean connect(Call call) {
            for (InCallServiceBindingConnection subConnection : mSubConnections) {
                subConnection.connect(call);
            }
            return true;
        }

        @Override
        public void disconnect() {
            for (InCallServiceBindingConnection subConnection : mSubConnections) {
                subConnection.disconnect();
            }
        }

        @Override
        public void dump(IndentingPrintWriter pw) {
            pw.println("Non-UI Connections:");
            pw.increaseIndent();
            for (InCallServiceBindingConnection subConnection : mSubConnections) {
                subConnection.dump(pw);
            }
            pw.decreaseIndent();
        }
    }

    private final Call.Listener mCallListener = new Call.ListenerBase() {
        @Override
        public void onConnectionCapabilitiesChanged(Call call) {
            updateCall(call);
        }

        @Override
        public void onConnectionPropertiesChanged(Call call) {
            updateCall(call);
        }

        @Override
        public void onCannedSmsResponsesLoaded(Call call) {
            updateCall(call);
        }

        @Override
        public void onVideoCallProviderChanged(Call call) {
            updateCall(call, true /* videoProviderChanged */);
        }

        @Override
        public void onStatusHintsChanged(Call call) {
            updateCall(call);
        }

        /**
         * Listens for changes to extras reported by a Telecom {@link Call}.
         *
         * Extras changes can originate from a {@link ConnectionService} or an {@link InCallService}
         * so we will only trigger an update of the call information if the source of the extras
         * change was a {@link ConnectionService}.
         *
         * @param call The call.
         * @param source The source of the extras change ({@link Call#SOURCE_CONNECTION_SERVICE} or
         *               {@link Call#SOURCE_INCALL_SERVICE}).
         * @param extras The extras.
         */
        @Override
        public void onExtrasChanged(Call call, int source, Bundle extras) {
            // Do not inform InCallServices of changes which originated there.
            if (source == Call.SOURCE_INCALL_SERVICE) {
                return;
            }
            updateCall(call);
        }

        /**
         * Listens for changes to extras reported by a Telecom {@link Call}.
         *
         * Extras changes can originate from a {@link ConnectionService} or an {@link InCallService}
         * so we will only trigger an update of the call information if the source of the extras
         * change was a {@link ConnectionService}.
         *  @param call The call.
         * @param source The source of the extras change ({@link Call#SOURCE_CONNECTION_SERVICE} or
         *               {@link Call#SOURCE_INCALL_SERVICE}).
         * @param keys The extra key removed
         */
        @Override
        public void onExtrasRemoved(Call call, int source, List<String> keys) {
            // Do not inform InCallServices of changes which originated there.
            if (source == Call.SOURCE_INCALL_SERVICE) {
                return;
            }
            updateCall(call);
        }

        @Override
        public void onHandleChanged(Call call) {
            updateCall(call);
        }

        @Override
        public void onCallerDisplayNameChanged(Call call) {
            updateCall(call);
        }

        @Override
        public void onVideoStateChanged(Call call, int previousVideoState, int newVideoState) {
            updateCall(call);
        }

        @Override
        public void onTargetPhoneAccountChanged(Call call) {
            updateCall(call);
        }

        @Override
        public void onConferenceableCallsChanged(Call call) {
            updateCall(call);
        }

        @Override
        public void onConnectionEvent(Call call, String event, Bundle extras) {
            notifyConnectionEvent(call, event, extras);
        }

        /// M: For volte @{
        @Override
        public void onExtraUpdated(Call call) {
            updateCall(call);
        }
        /// @}
    };

    /// M: voice recording
    private final PhoneRecorderHandler.Listener mRecorderListener = new PhoneRecorderHandler.Listener() {

        @Override
        public void requestUpdateRecordState(int state, int customValue) {
            if (!mInCallServices.isEmpty()) {
                Log.i(this, "updateRecordState state:" + state + ", customValue:" + customValue);
                for (IInCallService inCallService : mInCallServices.values()) {
                    try {
                        inCallService.updateRecordState(state, customValue);
                        /// M: fix CR:ALPS03160911,when begin to start record,set record permission,
                        /// lead to kill dialer process,unbind incallservice.Bind inCallservice
                        /// again unable to get phoneRecord State in time,so add to update
                        /// phoneRecord state in bind inCallService. @{
                        mPhoneRecorderState = state;
                        mCustomValue = customValue;
                        ///@}
                    } catch (RemoteException ignored) {
                    }
                }
            }
        }

        @Override
        public void onStorageFull() {
            if (!mInCallServices.isEmpty()) {
                Log.i(this, "onStorageFull");
                for (IInCallService inCallService : mInCallServices.values()) {
                    try {
                        inCallService.onStorageFull();
                    } catch (RemoteException ignored) {
                    }
                }
            }
        }
    };

    private final SystemStateListener mSystemStateListener = new SystemStateListener() {
        @Override
        public void onCarModeChanged(boolean isCarMode) {
            if (mInCallServiceConnection != null) {
                mInCallServiceConnection.setCarMode(shouldUseCarModeUI());
            }
        }
    };

    private static final int IN_CALL_SERVICE_TYPE_INVALID = 0;
    private static final int IN_CALL_SERVICE_TYPE_DIALER_UI = 1;
    private static final int IN_CALL_SERVICE_TYPE_SYSTEM_UI = 2;
    private static final int IN_CALL_SERVICE_TYPE_CAR_MODE_UI = 3;
    private static final int IN_CALL_SERVICE_TYPE_NON_UI = 4;

    /** The in-call app implementations, see {@link IInCallService}. */
    private final Map<InCallServiceInfo, IInCallService> mInCallServices = new ArrayMap<>();

    /**
     * The {@link ComponentName} of the bound In-Call UI Service.
     */
    private ComponentName mInCallUIComponentName;

    private final CallIdMapper mCallIdMapper = new CallIdMapper(Call::getId);

    /** The {@link ComponentName} of the default InCall UI. */
    private final ComponentName mSystemInCallComponentName;

    private final Context mContext;
    private final TelecomSystem.SyncRoot mLock;
    private final CallsManager mCallsManager;
    private final SystemStateProvider mSystemStateProvider;
    private final DefaultDialerManagerAdapter mDefaultDialerAdapter;
    private final Timeouts.Adapter mTimeoutsAdapter;
    private CarSwappingInCallServiceConnection mInCallServiceConnection;
    private NonUIInCallServiceConnectionCollection mNonUIInCallServiceConnections;

    /// M: fix CR:ALPS03160911,when begin to start record,set record permission,
    /// lead to kill dialer process,unbind incallservice.Bind inCallservice again
    /// unable to get phoneRecord State in time,so add to update phoneRecord state
    /// in bind inCallService. @{
    public int mPhoneRecorderState = 0;
    public int mCustomValue = 0;
    ///@}

    public InCallController(Context context, TelecomSystem.SyncRoot lock, CallsManager callsManager,
            SystemStateProvider systemStateProvider,
            DefaultDialerManagerAdapter defaultDialerAdapter, Timeouts.Adapter timeoutsAdapter) {
        mContext = context;
        mLock = lock;
        mCallsManager = callsManager;
        mSystemStateProvider = systemStateProvider;
        mDefaultDialerAdapter = defaultDialerAdapter;
        mTimeoutsAdapter = timeoutsAdapter;

        Resources resources = mContext.getResources();
        mSystemInCallComponentName = new ComponentName(
                resources.getString(R.string.ui_default_package),
                resources.getString(R.string.incall_default_class));

        mSystemStateProvider.addListener(mSystemStateListener);
    }

    @Override
    public void onCallAdded(Call call) {
        if (!isBoundToServices()) {
            bindToServices(call);
        } else {
            adjustServiceBindingsForEmergency();

            Log.d(this, "onCallAdded: %s", call);
            // Track the call if we don't already know about it.
            addCall(call);

            List<ComponentName> componentsUpdated = new ArrayList<>();
            for (Map.Entry<InCallServiceInfo, IInCallService> entry : mInCallServices.entrySet()) {
                InCallServiceInfo info = entry.getKey();

                if (call.isExternalCall() && !info.isExternalCallsSupported()) {
                    continue;
                }

                componentsUpdated.add(info.getComponentName());
                IInCallService inCallService = entry.getValue();

                ParcelableCall parcelableCall = ParcelableCallUtils.toParcelableCall(call,
                        true /* includeVideoProvider */, mCallsManager.getPhoneAccountRegistrar(),
                        info.isExternalCallsSupported());
                try {
                    inCallService.addCall(parcelableCall);
                } catch (RemoteException ignored) {
                }
            }
            Log.i(this, "Call added to components: %s", componentsUpdated);
        }
    }

    @Override
    public void onCallRemoved(Call call) {
        Log.d(this, "onCallRemoved: %s", call);
        if (mCallsManager.getCalls().isEmpty()) {
            /** Let's add a 2 second delay before we send unbind to the services to hopefully
             *  give them enough time to process all the pending messages.
             */
            Handler handler = new Handler(Looper.getMainLooper());
            handler.postDelayed(new Runnable("ICC.oCR", mLock) {
                @Override
                public void loggedRun() {
                        // Check again to make sure there are no active calls.
                        /// M: [EccDisconnectAll], mustn't unbind service if pending Ecc exists.
                        if (mCallsManager.getCalls().isEmpty() && !mCallsManager.hasPendingEcc()) {
                            unbindFromServices();
                        }
                }
            }.prepare(), mTimeoutsAdapter.getCallRemoveUnbindInCallServicesDelay(
                            mContext.getContentResolver()));
        }
        call.removeListener(mCallListener);
        mCallIdMapper.removeCall(call);
    }

    @Override
    public void onExternalCallChanged(Call call, boolean isExternalCall) {
        Log.i(this, "onExternalCallChanged: %s -> %b", call, isExternalCall);

        List<ComponentName> componentsUpdated = new ArrayList<>();
        if (!isExternalCall) {
            // The call was external but it is no longer external.  We must now add it to any
            // InCallServices which do not support external calls.
            for (Map.Entry<InCallServiceInfo, IInCallService> entry : mInCallServices.entrySet()) {
                InCallServiceInfo info = entry.getKey();

                if (info.isExternalCallsSupported()) {
                    // For InCallServices which support external calls, the call will have already
                    // been added to the connection service, so we do not need to add it again.
                    continue;
                }

                componentsUpdated.add(info.getComponentName());
                IInCallService inCallService = entry.getValue();

                ParcelableCall parcelableCall = ParcelableCallUtils.toParcelableCall(call,
                        true /* includeVideoProvider */, mCallsManager.getPhoneAccountRegistrar(),
                        info.isExternalCallsSupported());
                try {
                    inCallService.addCall(parcelableCall);
                } catch (RemoteException ignored) {
                }
            }
            Log.i(this, "Previously external call added to components: %s", componentsUpdated);
        } else {
            // The call was regular but it is now external.  We must now remove it from any
            // InCallServices which do not support external calls.
            // Remove the call by sending a call update indicating the call was disconnected.
            ParcelableCall parcelableCall = ParcelableCallUtils.toParcelableCall(
                    call,
                    false /* includeVideoProvider */,
                    mCallsManager.getPhoneAccountRegistrar(),
                    false /* supportsExternalCalls */,
                    android.telecom.Call.STATE_DISCONNECTED /* overrideState */);

            Log.i(this, "Removing external call %s ==> %s", call, parcelableCall);
            for (Map.Entry<InCallServiceInfo, IInCallService> entry : mInCallServices.entrySet()) {
                InCallServiceInfo info = entry.getKey();
                if (info.isExternalCallsSupported()) {
                    // For InCallServices which support external calls, we do not need to remove
                    // the call.
                    continue;
                }

                componentsUpdated.add(info.getComponentName());
                IInCallService inCallService = entry.getValue();

                try {
                    inCallService.updateCall(parcelableCall);
                } catch (RemoteException ignored) {
                }
            }
            Log.i(this, "External call removed from components: %s", componentsUpdated);
        }
    }

    @Override
    public void onCallStateChanged(Call call, int oldState, int newState) {
        updateCall(call);
    }

    @Override
    public void onConnectionServiceChanged(
            Call call,
            ConnectionServiceWrapper oldService,
            ConnectionServiceWrapper newService) {
        updateCall(call);
    }

    @Override
    public void onCallAudioStateChanged(CallAudioState oldCallAudioState,
            CallAudioState newCallAudioState) {
        if (!mInCallServices.isEmpty()) {
            Log.i(this, "Calling onAudioStateChanged, audioState: %s -> %s", oldCallAudioState,
                    newCallAudioState);
            for (IInCallService inCallService : mInCallServices.values()) {
                try {
                    inCallService.onCallAudioStateChanged(newCallAudioState);
                } catch (RemoteException ignored) {
                }
            }
        }
    }

    @Override
    public void onCanAddCallChanged(boolean canAddCall) {
        if (!mInCallServices.isEmpty()) {
            Log.i(this, "onCanAddCallChanged : %b", canAddCall);
            for (IInCallService inCallService : mInCallServices.values()) {
                try {
                    inCallService.onCanAddCallChanged(canAddCall);
                } catch (RemoteException ignored) {
                }
            }
        }
    }

    void onPostDialWait(Call call, String remaining) {
        if (!mInCallServices.isEmpty()) {
            Log.i(this, "Calling onPostDialWait, remaining = %s", remaining);
            for (IInCallService inCallService : mInCallServices.values()) {
                try {
                    inCallService.setPostDialWait(mCallIdMapper.getCallId(call), remaining);
                } catch (RemoteException ignored) {
                }
            }
        }
    }

    @Override
    public void onIsConferencedChanged(Call call) {
        Log.d(this, "onIsConferencedChanged %s", call);
        updateCall(call);
    }

    @Override
    public void onCdmaCallAccepted(Call call) {
        Log.d(this, "onCdmaCallAccepted %s", call);
        updateCall(call);
    }

    void bringToForeground(boolean showDialpad) {
        if (!mInCallServices.isEmpty()) {
            for (IInCallService inCallService : mInCallServices.values()) {
                try {
                    inCallService.bringToForeground(showDialpad);
                } catch (RemoteException ignored) {
                }
            }
        } else {
            Log.w(this, "Asking to bring unbound in-call UI to foreground.");
        }
    }

    void silenceRinger() {
        if (!mInCallServices.isEmpty()) {
            for (IInCallService inCallService : mInCallServices.values()) {
                try {
                    inCallService.silenceRinger();
                } catch (RemoteException ignored) {
                }
            }
        }
    }

    private void notifyConnectionEvent(Call call, String event, Bundle extras) {
        if (!mInCallServices.isEmpty()) {
            for (IInCallService inCallService : mInCallServices.values()) {
                try {
                    Log.i(this, "notifyConnectionEvent {Call: %s, Event: %s, Extras:[%s]}",
                            (call != null ? call.toString() :"null"),
                            (event != null ? event : "null") ,
                            (extras != null ? extras.toString() : "null"));
                    inCallService.onConnectionEvent(mCallIdMapper.getCallId(call), event, extras);
                } catch (RemoteException ignored) {
                }
            }
        }
    }

    /**
     * Unbinds an existing bound connection to the in-call app.
     */
    private void unbindFromServices() {
        if (isBoundToServices()) {
            if (mInCallServiceConnection != null) {
                mInCallServiceConnection.disconnect();
                mInCallServiceConnection = null;
            }
            if (mNonUIInCallServiceConnections != null) {
                mNonUIInCallServiceConnections.disconnect();
                mNonUIInCallServiceConnections = null;
            }
        }
        /// M: Unregister voice recording listener
        PhoneRecorderHandler.getInstance().clearListener(mRecorderListener);
    }

    /**
     * Binds to all the UI-providing InCallService as well as system-implemented non-UI
     * InCallServices. Method-invoker must check {@link #isBoundToServices()} before invoking.
     *
     * @param call The newly added call that triggered the binding to the in-call services.
     */
    @VisibleForTesting
    public void bindToServices(Call call) {
        InCallServiceConnection dialerInCall = null;

        InCallServiceInfo defaultDialerComponentInfo = getDefaultDialerComponent();
//        Log.i(this, "defaultDialer: " + defaultDialerComponentInfo);
        if (defaultDialerComponentInfo != null &&
                !defaultDialerComponentInfo.getComponentName().equals(mSystemInCallComponentName)) {
            dialerInCall = new InCallServiceBindingConnection(defaultDialerComponentInfo);

        }
        Log.i(this, "defaultDialer: " + defaultDialerComponentInfo + " " + dialerInCall);

        InCallServiceInfo systemInCallInfo =  getInCallServiceComponent(mSystemInCallComponentName,
                IN_CALL_SERVICE_TYPE_SYSTEM_UI);
        EmergencyInCallServiceConnection systemInCall =
                new EmergencyInCallServiceConnection(systemInCallInfo, dialerInCall);
        systemInCall.setHasEmergency(mCallsManager.hasEmergencyCall());

        InCallServiceConnection carModeInCall = null;
        InCallServiceInfo carModeComponentInfo = getCarModeComponent();
        if (carModeComponentInfo != null &&
                !carModeComponentInfo.getComponentName().equals(mSystemInCallComponentName)) {
            carModeInCall = new InCallServiceBindingConnection(carModeComponentInfo);
        }

        mInCallServiceConnection =
            new CarSwappingInCallServiceConnection(systemInCall, carModeInCall);
        mInCallServiceConnection.setCarMode(shouldUseCarModeUI());
        mInCallServiceConnection.connect(call);

        List<InCallServiceInfo> nonUIInCallComponents =
                getInCallServiceComponents(IN_CALL_SERVICE_TYPE_NON_UI);
        List<InCallServiceBindingConnection> nonUIInCalls = new LinkedList<>();
        for (InCallServiceInfo serviceInfo : nonUIInCallComponents) {
            nonUIInCalls.add(new InCallServiceBindingConnection(serviceInfo));
        }
        mNonUIInCallServiceConnections = new NonUIInCallServiceConnectionCollection(nonUIInCalls);
        mNonUIInCallServiceConnections.connect(call);
    }

    private InCallServiceInfo getDefaultDialerComponent() {
        String packageName = mDefaultDialerAdapter.getDefaultDialerApplication(
                mContext, mCallsManager.getCurrentUserHandle().getIdentifier());
        Log.d(this, "Default Dialer package: " + packageName);

        return getInCallServiceComponent(packageName, IN_CALL_SERVICE_TYPE_DIALER_UI);
    }

    private InCallServiceInfo getCarModeComponent() {
        // Seems strange to cast a String to null, but the signatures of getInCallServiceComponent
        // differ in the types of the first parameter, and passing in null is inherently ambiguous.
        return getInCallServiceComponent((String) null, IN_CALL_SERVICE_TYPE_CAR_MODE_UI);
    }

    private InCallServiceInfo getInCallServiceComponent(ComponentName componentName, int type) {
        List<InCallServiceInfo> list = getInCallServiceComponents(componentName, type);
        if (list != null && !list.isEmpty()) {
            return list.get(0);
        } else {
            // Last Resort: Try to bind to the ComponentName given directly.
            Log.e(this, new Exception(), "Package Manager could not find ComponentName: "
                    + componentName +". Trying to bind anyway.");
            return new InCallServiceInfo(componentName, false, type);
        }
    }

    private InCallServiceInfo getInCallServiceComponent(String packageName, int type) {
        List<InCallServiceInfo> list = getInCallServiceComponents(packageName, type);
        if (list != null && !list.isEmpty()) {
            return list.get(0);
        }
        return null;
    }

    private List<InCallServiceInfo> getInCallServiceComponents(int type) {
        return getInCallServiceComponents(null, null, type);
    }

    private List<InCallServiceInfo> getInCallServiceComponents(String packageName, int type) {
        return getInCallServiceComponents(packageName, null, type);
    }

    private List<InCallServiceInfo> getInCallServiceComponents(ComponentName componentName,
            int type) {
        return getInCallServiceComponents(null, componentName, type);
    }

    private List<InCallServiceInfo> getInCallServiceComponents(String packageName,
            ComponentName componentName, int requestedType) {

        List<InCallServiceInfo> retval = new LinkedList<>();

        Intent serviceIntent = new Intent(InCallService.SERVICE_INTERFACE);
        if (packageName != null) {
            serviceIntent.setPackage(packageName);
        }
        if (componentName != null) {
            serviceIntent.setComponent(componentName);
        }

        PackageManager packageManager = mContext.getPackageManager();
        for (ResolveInfo entry : packageManager.queryIntentServicesAsUser(
                serviceIntent,
                PackageManager.GET_META_DATA,
                mCallsManager.getCurrentUserHandle().getIdentifier())) {
            ServiceInfo serviceInfo = entry.serviceInfo;

            if (serviceInfo != null) {
                boolean isExternalCallsSupported = serviceInfo.metaData != null &&
                        serviceInfo.metaData.getBoolean(
                                TelecomManager.METADATA_INCLUDE_EXTERNAL_CALLS, false);
                if (requestedType == 0 || requestedType == getInCallServiceType(entry.serviceInfo,
                        packageManager)) {

                    retval.add(new InCallServiceInfo(
                            new ComponentName(serviceInfo.packageName, serviceInfo.name),
                            isExternalCallsSupported, requestedType));
                }
            }
        }

        return retval;
    }

    private boolean shouldUseCarModeUI() {
        return mSystemStateProvider.isCarMode();
    }

    /**
     * Returns the type of InCallService described by the specified serviceInfo.
     */
    private int getInCallServiceType(ServiceInfo serviceInfo, PackageManager packageManager) {
        // Verify that the InCallService requires the BIND_INCALL_SERVICE permission which
        // enforces that only Telecom can bind to it.
        boolean hasServiceBindPermission = serviceInfo.permission != null &&
                serviceInfo.permission.equals(
                        Manifest.permission.BIND_INCALL_SERVICE);
        if (!hasServiceBindPermission) {
            Log.w(this, "InCallService does not require BIND_INCALL_SERVICE permission: " +
                    serviceInfo.packageName);
            return IN_CALL_SERVICE_TYPE_INVALID;
        }

        if (mSystemInCallComponentName.getPackageName().equals(serviceInfo.packageName) &&
                mSystemInCallComponentName.getClassName().equals(serviceInfo.name)) {
            return IN_CALL_SERVICE_TYPE_SYSTEM_UI;
        }

        // Check to see if the service is a car-mode UI type by checking that it has the
        // CONTROL_INCALL_EXPERIENCE (to verify it is a system app) and that it has the
        // car-mode UI metadata.
        boolean hasControlInCallPermission = packageManager.checkPermission(
                Manifest.permission.CONTROL_INCALL_EXPERIENCE,
                serviceInfo.packageName) == PackageManager.PERMISSION_GRANTED;
        boolean isCarModeUIService = serviceInfo.metaData != null &&
                serviceInfo.metaData.getBoolean(
                        TelecomManager.METADATA_IN_CALL_SERVICE_CAR_MODE_UI, false) &&
                hasControlInCallPermission;
        if (isCarModeUIService) {
            return IN_CALL_SERVICE_TYPE_CAR_MODE_UI;
        }

        // Check to see that it is the default dialer package
        boolean isDefaultDialerPackage = Objects.equals(serviceInfo.packageName,
                mDefaultDialerAdapter.getDefaultDialerApplication(
                    mContext, mCallsManager.getCurrentUserHandle().getIdentifier()));
        boolean isUIService = serviceInfo.metaData != null &&
                serviceInfo.metaData.getBoolean(
                        TelecomManager.METADATA_IN_CALL_SERVICE_UI, false);
        if (isDefaultDialerPackage && isUIService) {
            return IN_CALL_SERVICE_TYPE_DIALER_UI;
        }

        // Also allow any in-call service that has the control-experience permission (to ensure
        // that it is a system app) and doesn't claim to show any UI.
        if (hasControlInCallPermission && !isUIService) {
            return IN_CALL_SERVICE_TYPE_NON_UI;
        }

        // Anything else that remains, we will not bind to.
        Log.i(this, "Skipping binding to %s:%s, control: %b, car-mode: %b, ui: %b",
                serviceInfo.packageName, serviceInfo.name, hasControlInCallPermission,
                isCarModeUIService, isUIService);
        return IN_CALL_SERVICE_TYPE_INVALID;
    }

    private void adjustServiceBindingsForEmergency() {
        // The connected UI is not the system UI, so lets check if we should switch them
        // if there exists an emergency number.
        if (mCallsManager.hasEmergencyCall()) {
            mInCallServiceConnection.setHasEmergency(true);
        }
    }

    /**
     * Persists the {@link IInCallService} instance and starts the communication between
     * this class and in-call app by sending the first update to in-call app. This method is
     * called after a successful binding connection is established.
     *
     * @param info Info about the service, including its {@link ComponentName}.
     * @param service The {@link IInCallService} implementation.
     * @return True if we successfully connected.
     */

    private boolean onConnected(InCallServiceInfo info, IBinder service) {
        Trace.beginSection("onConnected: " + info.getComponentName());
        Log.i(this, "onConnected to %s", info.getComponentName());

        IInCallService inCallService = IInCallService.Stub.asInterface(service);
        mInCallServices.put(info, inCallService);

        try {
            inCallService.setInCallAdapter(
                    new InCallAdapter(
                            mCallsManager,
                            mCallIdMapper,
                            mLock,
                            info.getComponentName().getPackageName()));
        } catch (RemoteException e) {
            Log.e(this, e, "Failed to set the in-call adapter.");
            Trace.endSection();
            return false;
        }

        // Upon successful connection, send the state of the world to the service.
               List<Call> calls = orderCallsWithChildrenFirst(mCallsManager.getCalls());
                Log.i(this, "Adding %s calls to InCallService after onConnected: %s, including external " +
                        "calls", calls.size(), info.getComponentName());
                int numCallsSent = 0;
                for (Call call : calls) {
                    try {
                        if (call.isExternalCall() && !info.isExternalCallsSupported()) {
                            continue;
                        }

                        /// M: [log optimize]
                        Log.d(this, "Adding %s", call);
                        // Track the call if we don't already know about it.
                        addCall(call);
                        numCallsSent += 1;
                        inCallService.addCall(ParcelableCallUtils.toParcelableCall(
                                call,
                                true /* includeVideoProvider */,
                                mCallsManager.getPhoneAccountRegistrar(),
                                info.isExternalCallsSupported()));
                        ///M: [Performance Track] @{
                       PerformanceTracker.getInstance().trackMO(call.getId(),
                               PerformanceTracker.MO_ADD_CALL_TO_INCALL_END);
                        ///@}
                    } catch (RemoteException ignored) {
                    }
                }
        try {
            /// M: fix CR:ALPS03160911,when begin to start record,set record permission,
            /// lead to kill dialer process,unbind incallservice.Bind inCallservice again
            /// unable to get phoneRecord State in time,so add to update phoneRecord state
            /// in bind inCallService. @{
            inCallService.updateRecordState(mPhoneRecorderState, mCustomValue);
            ///@}
            inCallService.onCallAudioStateChanged(mCallsManager.getAudioState());
            inCallService.onCanAddCallChanged(mCallsManager.canAddCall());
        } catch (RemoteException ignored) {
        }
        Log.i(this, "%s calls sent to InCallService.", numCallsSent);
        Trace.endSection();
        return true;
    }

    /**
     * Cleans up an instance of in-call app after the service has been unbound.
     *
     * @param disconnectedComponent The {@link ComponentName} of the service which disconnected.
     */
    private void onDisconnected(ComponentName disconnectedComponent) {
        Log.i(this, "onDisconnected from %s", disconnectedComponent);

        mInCallServices.remove(disconnectedComponent);
        // One of the services that we were bound to has disconnected. If the default in-call UI
        // has disconnected, disconnect all calls and un-bind all other InCallService
        // implementations.
        if (disconnectedComponent.equals(mInCallUIComponentName)) {
            Log.i(this, "In-call UI %s disconnected.", disconnectedComponent);

            // TODO: We need to not disconnect all calls if there exists an emergency call.
            // TODO: IF the default-dialer crashed, we need to rebind to the system UI.
            mCallsManager.disconnectAllCalls();
            /// M: For ALPS01777577. @{
            // Added for notify UI the call disconnected and maybe need update ui.
            sendBroadcastToMainUI();
            /// @}
        }
    }

    /**
     * Informs all {@link InCallService} instances of the updated call information.
     *
     * @param call The {@link Call}.
     */
    private void updateCall(Call call) {
        updateCall(call, false /* videoProviderChanged */);
    }

    /**
     * Informs all {@link InCallService} instances of the updated call information.
     *
     * @param call The {@link Call}.
     * @param videoProviderChanged {@code true} if the video provider changed, {@code false}
     *      otherwise.
     */
    private void updateCall(Call call, boolean videoProviderChanged) {
        // M: Add log for upgrade debug.
        if (TelecomUtils.getUpgradeLoggingEnabled()) {
            Log.i(this, "updateCall mInCallServices size = " + mInCallServices.size());
        }
        if (!mInCallServices.isEmpty()) {
            Log.i(this, "Sending updateCall %s", call);
            List<ComponentName> componentsUpdated = new ArrayList<>();
            for (Map.Entry<InCallServiceInfo, IInCallService> entry : mInCallServices.entrySet()) {
                InCallServiceInfo info = entry.getKey();
                if (call.isExternalCall() && !info.isExternalCallsSupported()) {
                    continue;
                }

                ParcelableCall parcelableCall = ParcelableCallUtils.toParcelableCall(
                        call,
                        videoProviderChanged /* includeVideoProvider */,
                        mCallsManager.getPhoneAccountRegistrar(),
                        info.isExternalCallsSupported());
                ComponentName componentName = info.getComponentName();
                IInCallService inCallService = entry.getValue();
                componentsUpdated.add(componentName);

                try {
                    inCallService.updateCall(parcelableCall);
                    ///M: [Performance Track] @{
                    if (call.isActive()) {
                        PerformanceTracker.getInstance().trackAnswerCall(call.getId(),
                                PerformanceTracker.MT_UPDATE_ACTIVE_TO_INCALL);
                    }
                    if (call.isDisconnected()) {
                        PerformanceTracker.getInstance().trackEndCall(call.getId(),
                                PerformanceTracker.END_UPDATE_DISCONNECTED_TO_INCALL);
                    }
                    ///@}
                } catch (RemoteException ignored) {
                }
            }
        }
    }

    /**
     * Adds the call to the list of calls tracked by the {@link InCallController}.
     * @param call The call to add.
     */
    private void addCall(Call call) {
        if (mCallIdMapper.getCallId(call) == null) {
            mCallIdMapper.addCall(call);
            call.addListener(mCallListener);
        }
    }

    private boolean isBoundToServices() {
        return mInCallServiceConnection != null;
    }

    /**
     * Dumps the state of the {@link InCallController}.
     *
     * @param pw The {@code IndentingPrintWriter} to write the state to.
     */
    public void dump(IndentingPrintWriter pw) {
        pw.println("mInCallServices (InCalls registered):");
        pw.increaseIndent();
        for (InCallServiceInfo info : mInCallServices.keySet()) {
            pw.println(info);
        }
        pw.decreaseIndent();

        pw.println("ServiceConnections (InCalls bound):");
        pw.increaseIndent();
        if (mInCallServiceConnection != null) {
            mInCallServiceConnection.dump(pw);
        }
        pw.decreaseIndent();
    }

    public boolean doesConnectedDialerSupportRinging() {
        String ringingPackage =  null;
        if (mInCallUIComponentName != null) {
            ringingPackage = mInCallUIComponentName.getPackageName().trim();
        }

        if (TextUtils.isEmpty(ringingPackage)) {
            // The current in-call UI returned nothing, so lets use the default dialer.
            ringingPackage = DefaultDialerManager.getDefaultDialerApplication(
                    mContext, UserHandle.USER_CURRENT);
        }
        if (TextUtils.isEmpty(ringingPackage)) {
            return false;
        }

        Intent intent = new Intent(InCallService.SERVICE_INTERFACE)
            .setPackage(ringingPackage);
        List<ResolveInfo> entries = mContext.getPackageManager().queryIntentServicesAsUser(
                intent, PackageManager.GET_META_DATA,
                mCallsManager.getCurrentUserHandle().getIdentifier());
        if (entries.isEmpty()) {
            return false;
        }

        ResolveInfo info = entries.get(0);
        if (info.serviceInfo == null || info.serviceInfo.metaData == null) {
            return false;
        }

        return info.serviceInfo.metaData
                .getBoolean(TelecomManager.METADATA_IN_CALL_SERVICE_RINGING, false);
    }

    private List<Call> orderCallsWithChildrenFirst(Collection<Call> calls) {
        LinkedList<Call> parentCalls = new LinkedList<>();
        LinkedList<Call> childCalls = new LinkedList<>();
        for (Call call : calls) {
            if (call.getChildCalls().size() > 0) {
                parentCalls.add(call);
            } else {
                childCalls.add(call);
            }
        }
        childCalls.addAll(parentCalls);
        return childCalls;
    }

    /**
     * Send call all disconnected to InCallUI and maybe need update ui.
     */
    private void sendBroadcastToMainUI() {
        if (mContext == null) {
            Log.d(this, "sendBroadcastToUI, mContext is nulll.");
            return;
        }
        Intent intent = new Intent("com.android.incallui.ACTION_UPDATE_UI_FORCED");
        Log.d(this, "Sending broadcast to ui receiver");
        mContext.sendBroadcast(intent);
    }
}
