/*
 * Copyright (C) 2015 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.server.telecom;


import android.app.ActivityManagerNative;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.UserInfo;
import android.media.AudioManager;
import android.media.IAudioService;
import android.os.Binder;
import android.os.Message;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.telecom.CallAudioState;
import android.util.SparseArray;

import com.android.internal.util.IState;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;

import java.util.HashMap;

/**
 * This class describes the available routes of a call as a state machine.
 * Transitions are caused solely by the commands sent as messages. Possible values for msg.what
 * are defined as event constants in this file.
 *
 * The eight states are all instances of the abstract base class, {@link AudioState}. Each state
 * is a combination of one of the four audio routes (earpiece, wired headset, bluetooth, and
 * speakerphone) and audio focus status (active or quiescent).
 *
 * Messages are processed first by the processMessage method in the base class, AudioState.
 * Any messages not completely handled by AudioState are further processed by the same method in
 * the route-specific abstract classes: {@link EarpieceRoute}, {@link HeadsetRoute},
 * {@link BluetoothRoute}, and {@link SpeakerRoute}. Finally, messages that are not handled at
 * this level are then processed by the classes corresponding to the state instances themselves.
 *
 * There are several variables carrying additional state. These include:
 * mAvailableRoutes: A bitmask describing which audio routes are available
 * mWasOnSpeaker: A boolean indicating whether we should switch to speakerphone after disconnecting
 *     from a wired headset
 * mIsMuted: a boolean indicating whether the audio is muted
 */
public class CallAudioRouteStateMachine extends StateMachine {
    /** Direct the audio stream through the device's earpiece. */
    public static final int ROUTE_EARPIECE      = CallAudioState.ROUTE_EARPIECE;

    /** Direct the audio stream through Bluetooth. */
    public static final int ROUTE_BLUETOOTH     = CallAudioState.ROUTE_BLUETOOTH;

    /** Direct the audio stream through a wired headset. */
    public static final int ROUTE_WIRED_HEADSET = CallAudioState.ROUTE_WIRED_HEADSET;

    /** Direct the audio stream through the device's speakerphone. */
    public static final int ROUTE_SPEAKER       = CallAudioState.ROUTE_SPEAKER;

    /** Valid values for msg.what */
    public static final int CONNECT_WIRED_HEADSET = 1;
    public static final int DISCONNECT_WIRED_HEADSET = 2;
    public static final int CONNECT_BLUETOOTH = 3;
    public static final int DISCONNECT_BLUETOOTH = 4;
    public static final int CONNECT_DOCK = 5;
    public static final int DISCONNECT_DOCK = 6;

    public static final int SWITCH_EARPIECE = 1001;
    public static final int SWITCH_BLUETOOTH = 1002;
    public static final int SWITCH_HEADSET = 1003;
    public static final int SWITCH_SPEAKER = 1004;
    // Wired headset, earpiece, or speakerphone, in that order of precedence.
    public static final int SWITCH_BASELINE_ROUTE = 1005;
    public static final int BT_AUDIO_DISCONNECT = 1006;

    public static final int USER_SWITCH_EARPIECE = 1101;
    public static final int USER_SWITCH_BLUETOOTH = 1102;
    public static final int USER_SWITCH_HEADSET = 1103;
    public static final int USER_SWITCH_SPEAKER = 1104;
    public static final int USER_SWITCH_BASELINE_ROUTE = 1105;

    public static final int UPDATE_SYSTEM_AUDIO_ROUTE = 1201;

    public static final int MUTE_ON = 3001;
    public static final int MUTE_OFF = 3002;
    public static final int TOGGLE_MUTE = 3003;
    ///M: ALPS03573205 Add restore mute message. @{
    public static final int RESTORE_MUTE = 3004;
    /// @}

    public static final int SWITCH_FOCUS = 4001;

    // Used in testing to execute verifications. Not compatible with subsessions.
    public static final int RUN_RUNNABLE = 9001;

    /** Valid values for mAudioFocusType */
    public static final int NO_FOCUS = 1;
    public static final int ACTIVE_FOCUS = 2;
    public static final int RINGING_FOCUS = 3;

    private static final SparseArray<String> AUDIO_ROUTE_TO_LOG_EVENT = new SparseArray<String>() {{
        put(CallAudioState.ROUTE_BLUETOOTH, Log.Events.AUDIO_ROUTE_BT);
        put(CallAudioState.ROUTE_EARPIECE, Log.Events.AUDIO_ROUTE_EARPIECE);
        put(CallAudioState.ROUTE_SPEAKER, Log.Events.AUDIO_ROUTE_SPEAKER);
        put(CallAudioState.ROUTE_WIRED_HEADSET, Log.Events.AUDIO_ROUTE_HEADSET);
    }};

    private static final SparseArray<String> MESSAGE_CODE_TO_NAME = new SparseArray<String>() {{
        put(CONNECT_WIRED_HEADSET, "CONNECT_WIRED_HEADSET");
        put(DISCONNECT_WIRED_HEADSET, "DISCONNECT_WIRED_HEADSET");
        put(CONNECT_BLUETOOTH, "CONNECT_BLUETOOTH");
        put(DISCONNECT_BLUETOOTH, "DISCONNECT_BLUETOOTH");
        put(CONNECT_DOCK, "CONNECT_DOCK");
        put(DISCONNECT_DOCK, "DISCONNECT_DOCK");

        put(SWITCH_EARPIECE, "SWITCH_EARPIECE");
        put(SWITCH_BLUETOOTH, "SWITCH_BLUETOOTH");
        put(SWITCH_HEADSET, "SWITCH_HEADSET");
        put(SWITCH_SPEAKER, "SWITCH_SPEAKER");
        put(SWITCH_BASELINE_ROUTE, "SWITCH_BASELINE_ROUTE");
        put(BT_AUDIO_DISCONNECT, "BT_AUDIO_DISCONNECT");

        put(USER_SWITCH_EARPIECE, "USER_SWITCH_EARPIECE");
        put(USER_SWITCH_BLUETOOTH, "USER_SWITCH_BLUETOOTH");
        put(USER_SWITCH_HEADSET, "USER_SWITCH_HEADSET");
        put(USER_SWITCH_SPEAKER, "USER_SWITCH_SPEAKER");
        put(USER_SWITCH_BASELINE_ROUTE, "USER_SWITCH_BASELINE_ROUTE");

        put(UPDATE_SYSTEM_AUDIO_ROUTE, "UPDATE_SYSTEM_AUDIO_ROUTE");

        put(MUTE_ON, "MUTE_ON");
        put(MUTE_OFF, "MUTE_OFF");
        put(TOGGLE_MUTE, "TOGGLE_MUTE");
        ///M: ALPS03573205 Add restore mute message. @{
        put(RESTORE_MUTE, "RESTORE_MUTE");
        /// @}
        put(SWITCH_FOCUS, "SWITCH_FOCUS");

        put(RUN_RUNNABLE, "RUN_RUNNABLE");
    }};

    /**
     * BroadcastReceiver used to track changes in the notification interruption filter.  This
     * ensures changes to the notification interruption filter made by the user during a call are
     * respected when restoring the notification interruption filter state.
     */
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.startSession("CARSM.oR");
            try {
                String action = intent.getAction();

                if (action.equals(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)) {
                    if (mAreNotificationSuppressed) {
                        // If we've already set the interruption filter, and the user changes it to
                        // something other than INTERRUPTION_FILTER_ALARMS, assume we will no longer
                        // try to change it back if the audio route changes.
                        mAreNotificationSuppressed =
                                mInterruptionFilterProxy.getCurrentInterruptionFilter()
                                        == NotificationManager.INTERRUPTION_FILTER_ALARMS;
                    }
                }
            } finally {
                Log.endSession();
            }
        }
    };

    private static final String ACTIVE_EARPIECE_ROUTE_NAME = "ActiveEarpieceRoute";
    private static final String ACTIVE_BLUETOOTH_ROUTE_NAME = "ActiveBluetoothRoute";
    private static final String ACTIVE_SPEAKER_ROUTE_NAME = "ActiveSpeakerRoute";
    private static final String ACTIVE_HEADSET_ROUTE_NAME = "ActiveHeadsetRoute";
    private static final String RINGING_BLUETOOTH_ROUTE_NAME = "RingingBluetoothRoute";
    private static final String QUIESCENT_EARPIECE_ROUTE_NAME = "QuiescentEarpieceRoute";
    private static final String QUIESCENT_BLUETOOTH_ROUTE_NAME = "QuiescentBluetoothRoute";
    private static final String QUIESCENT_SPEAKER_ROUTE_NAME = "QuiescentSpeakerRoute";
    private static final String QUIESCENT_HEADSET_ROUTE_NAME = "QuiescentHeadsetRoute";

    public static final String NAME = CallAudioRouteStateMachine.class.getName();

    @Override
    protected void onPreHandleMessage(Message msg) {
        if (msg.obj != null && msg.obj instanceof Session) {
            String messageCodeName = MESSAGE_CODE_TO_NAME.get(msg.what, "unknown");
            Log.continueSession((Session) msg.obj, "CARSM.pM_" + messageCodeName);
            Log.i(this, "Message received: %s=%d, arg1=%d", messageCodeName, msg.what, msg.arg1);
        }
    }

    @Override
    protected void onPostHandleMessage(Message msg) {
        Log.endSession();
    }

    abstract class AudioState extends State {
        @Override
        public void enter() {
            super.enter();
            Log.event(mCallsManager.getForegroundCall(), Log.Events.AUDIO_ROUTE,
                    "Entering state " + getName());
        }

        @Override
        public void exit() {
            Log.event(mCallsManager.getForegroundCall(), Log.Events.AUDIO_ROUTE,
                    "Leaving state " + getName());
            super.exit();
        }

        @Override
        public boolean processMessage(Message msg) {
            switch (msg.what) {
                case CONNECT_WIRED_HEADSET:
                    Log.event(mCallsManager.getForegroundCall(), Log.Events.AUDIO_ROUTE,
                            "Wired headset connected");
                    mAvailableRoutes &= ~ROUTE_EARPIECE;
                    mAvailableRoutes |= ROUTE_WIRED_HEADSET;
                    return NOT_HANDLED;
                case CONNECT_BLUETOOTH:
                    Log.event(mCallsManager.getForegroundCall(), Log.Events.AUDIO_ROUTE,
                            "Bluetooth connected");
                    mAvailableRoutes |= ROUTE_BLUETOOTH;
                    return NOT_HANDLED;
                case DISCONNECT_WIRED_HEADSET:
                    Log.event(mCallsManager.getForegroundCall(), Log.Events.AUDIO_ROUTE,
                            "Wired headset disconnected");
                    mAvailableRoutes &= ~ROUTE_WIRED_HEADSET;
                    if (mDoesDeviceSupportEarpieceRoute) {
                        mAvailableRoutes |= ROUTE_EARPIECE;
                    }
                    return NOT_HANDLED;
                case DISCONNECT_BLUETOOTH:
                    Log.event(mCallsManager.getForegroundCall(), Log.Events.AUDIO_ROUTE,
                            "Bluetooth disconnected");
                    mAvailableRoutes &= ~ROUTE_BLUETOOTH;
                    return NOT_HANDLED;
                case SWITCH_BASELINE_ROUTE:
                    sendInternalMessage(calculateBaselineRouteMessage(false));
                    return HANDLED;
                case USER_SWITCH_BASELINE_ROUTE:
                    sendInternalMessage(calculateBaselineRouteMessage(true));
                    return HANDLED;
                case SWITCH_FOCUS:
                    mAudioFocusType = msg.arg1;
                    return NOT_HANDLED;
                default:
                    return NOT_HANDLED;
            }
        }

        // Behavior will depend on whether the state is an active one or a quiescent one.
        abstract public void updateSystemAudioState();
        abstract public boolean isActive();
    }

    class ActiveEarpieceRoute extends EarpieceRoute {
        @Override
        public String getName() {
            return ACTIVE_EARPIECE_ROUTE_NAME;
        }

        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public void enter() {
            super.enter();
            setSpeakerphoneOn(false);
            setBluetoothOn(false);
            if (mAudioFocusType == ACTIVE_FOCUS) {
                setNotificationsSuppressed(true);
            }
            CallAudioState newState = new CallAudioState(mIsMuted, ROUTE_EARPIECE,
                    mAvailableRoutes);
            setSystemAudioState(newState);
            updateInternalCallAudioState();
        }

        @Override
        public void exit() {
            super.exit();
            setNotificationsSuppressed(false);
        }

        @Override
        public void updateSystemAudioState() {
            updateInternalCallAudioState();
            setSystemAudioState(mCurrentCallAudioState);
        }

        @Override
        public boolean processMessage(Message msg) {
            if (super.processMessage(msg) == HANDLED) {
                return HANDLED;
            }
            switch (msg.what) {
                case SWITCH_EARPIECE:
                case USER_SWITCH_EARPIECE:
                    // Nothing to do here
                    return HANDLED;
                case SWITCH_BLUETOOTH:
                case USER_SWITCH_BLUETOOTH:
                    if ((mAvailableRoutes & ROUTE_BLUETOOTH) != 0) {
                        transitionTo(mAudioFocusType == ACTIVE_FOCUS ?
                                mActiveBluetoothRoute : mRingingBluetoothRoute);
                    } else {
                        Log.w(this, "Ignoring switch to bluetooth command. Not available.");
                    }
                    return HANDLED;
                case SWITCH_HEADSET:
                case USER_SWITCH_HEADSET:
                    if ((mAvailableRoutes & ROUTE_WIRED_HEADSET) != 0) {
                        transitionTo(mActiveHeadsetRoute);
                    } else {
                        Log.w(this, "Ignoring switch to headset command. Not available.");
                    }
                    return HANDLED;
                case SWITCH_SPEAKER:
                case USER_SWITCH_SPEAKER:
                    transitionTo(mActiveSpeakerRoute);
                    return HANDLED;
                case SWITCH_FOCUS:
                    if (msg.arg1 == ACTIVE_FOCUS) {
                        setNotificationsSuppressed(true);
                    }

                    if (msg.arg1 == NO_FOCUS) {
                        reinitialize();
                    }
                    return HANDLED;
                default:
                    return NOT_HANDLED;
            }
        }
    }

    class QuiescentEarpieceRoute extends EarpieceRoute {
        @Override
        public String getName() {
            return QUIESCENT_EARPIECE_ROUTE_NAME;
        }

        @Override
        public boolean isActive() {
            return false;
        }

        @Override
        public void enter() {
            super.enter();
            mHasUserExplicitlyLeftBluetooth = false;
            updateInternalCallAudioState();
        }

        @Override
        public void updateSystemAudioState() {
            updateInternalCallAudioState();
        }

        @Override
        public boolean processMessage(Message msg) {
            if (super.processMessage(msg) == HANDLED) {
                return HANDLED;
            }
            switch (msg.what) {
                case SWITCH_EARPIECE:
                case USER_SWITCH_EARPIECE:
                    // Nothing to do here
                    return HANDLED;
                case SWITCH_BLUETOOTH:
                case USER_SWITCH_BLUETOOTH:
                    if ((mAvailableRoutes & ROUTE_BLUETOOTH) != 0) {
                        transitionTo(mQuiescentBluetoothRoute);
                    } else {
                        Log.w(this, "Ignoring switch to bluetooth command. Not available.");
                    }
                    return HANDLED;
                case SWITCH_HEADSET:
                case USER_SWITCH_HEADSET:
                    if ((mAvailableRoutes & ROUTE_WIRED_HEADSET) != 0) {
                        transitionTo(mQuiescentHeadsetRoute);
                    } else {
                        Log.w(this, "Ignoring switch to headset command. Not available.");
                    }
                    return HANDLED;
                case SWITCH_SPEAKER:
                case USER_SWITCH_SPEAKER:
                    transitionTo(mQuiescentSpeakerRoute);
                    return HANDLED;
                case SWITCH_FOCUS:
                    if (msg.arg1 == ACTIVE_FOCUS || msg.arg1 == RINGING_FOCUS) {
                        transitionTo(mActiveEarpieceRoute);
                    }
                    return HANDLED;
                default:
                    return NOT_HANDLED;
            }
        }
    }

    abstract class EarpieceRoute extends AudioState {
        @Override
        public boolean processMessage(Message msg) {
            if (super.processMessage(msg) == HANDLED) {
                return HANDLED;
            }
            switch (msg.what) {
                case CONNECT_WIRED_HEADSET:
                    sendInternalMessage(SWITCH_HEADSET);
                    return HANDLED;
                case CONNECT_BLUETOOTH:
                    if (!mHasUserExplicitlyLeftBluetooth) {
                        sendInternalMessage(SWITCH_BLUETOOTH);
                    } else {
                        Log.i(this, "Not switching to BT route from earpiece because user has " +
                                "explicitly disconnected.");
                        updateSystemAudioState();
                    }
                    return HANDLED;
                case DISCONNECT_BLUETOOTH:
                    updateSystemAudioState();
                    // No change in audio route required
                    return HANDLED;
                case DISCONNECT_WIRED_HEADSET:
                    Log.e(this, new IllegalStateException(),
                            "Wired headset should not go from connected to not when on " +
                            "earpiece");
                    updateSystemAudioState();
                    return HANDLED;
                case BT_AUDIO_DISCONNECT:
                    // This may be sent as a confirmation by the BT stack after switch off BT.
                    return HANDLED;
                case CONNECT_DOCK:
                    sendInternalMessage(SWITCH_SPEAKER);
                    return HANDLED;
                case DISCONNECT_DOCK:
                    // Nothing to do here
                    return HANDLED;
                default:
                    return NOT_HANDLED;
            }
        }
    }

    class ActiveHeadsetRoute extends HeadsetRoute {
        @Override
        public String getName() {
            return ACTIVE_HEADSET_ROUTE_NAME;
        }

        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public void enter() {
            super.enter();
            setSpeakerphoneOn(false);
            setBluetoothOn(false);
            CallAudioState newState = new CallAudioState(mIsMuted, ROUTE_WIRED_HEADSET,
                    mAvailableRoutes);
            setSystemAudioState(newState);
            updateInternalCallAudioState();
        }

        @Override
        public void updateSystemAudioState() {
            updateInternalCallAudioState();
            setSystemAudioState(mCurrentCallAudioState);
        }

        @Override
        public boolean processMessage(Message msg) {
            if (super.processMessage(msg) == HANDLED) {
                return HANDLED;
            }
            switch (msg.what) {
                case SWITCH_EARPIECE:
                case USER_SWITCH_EARPIECE:
                    if ((mAvailableRoutes & ROUTE_EARPIECE) != 0) {
                        transitionTo(mActiveEarpieceRoute);
                    } else {
                        Log.w(this, "Ignoring switch to earpiece command. Not available.");
                    }
                    return HANDLED;
                case SWITCH_BLUETOOTH:
                case USER_SWITCH_BLUETOOTH:
                    if ((mAvailableRoutes & ROUTE_BLUETOOTH) != 0) {
                        transitionTo(mAudioFocusType == ACTIVE_FOCUS ?
                                mActiveBluetoothRoute : mRingingBluetoothRoute);
                    } else {
                        Log.w(this, "Ignoring switch to bluetooth command. Not available.");
                    }
                    return HANDLED;
                case SWITCH_HEADSET:
                case USER_SWITCH_HEADSET:
                    // Nothing to do
                    return HANDLED;
                case SWITCH_SPEAKER:
                case USER_SWITCH_SPEAKER:
                    transitionTo(mActiveSpeakerRoute);
                    return HANDLED;
                case SWITCH_FOCUS:
                    if (msg.arg1 == NO_FOCUS) {
                        reinitialize();
                    }
                    return HANDLED;
                default:
                    return NOT_HANDLED;
            }
        }
    }

    class QuiescentHeadsetRoute extends HeadsetRoute {
        @Override
        public String getName() {
            return QUIESCENT_HEADSET_ROUTE_NAME;
        }

        @Override
        public boolean isActive() {
            return false;
        }

        @Override
        public void enter() {
            super.enter();
            mHasUserExplicitlyLeftBluetooth = false;
            updateInternalCallAudioState();
        }

        @Override
        public void updateSystemAudioState() {
            updateInternalCallAudioState();
        }

        @Override
        public boolean processMessage(Message msg) {
            if (super.processMessage(msg) == HANDLED) {
                return HANDLED;
            }
            switch (msg.what) {
                case SWITCH_EARPIECE:
                case USER_SWITCH_EARPIECE:
                    if ((mAvailableRoutes & ROUTE_EARPIECE) != 0) {
                        transitionTo(mQuiescentEarpieceRoute);
                    } else {
                        Log.w(this, "Ignoring switch to earpiece command. Not available.");
                    }
                    return HANDLED;
                case SWITCH_BLUETOOTH:
                case USER_SWITCH_BLUETOOTH:
                    if ((mAvailableRoutes & ROUTE_BLUETOOTH) != 0) {
                        transitionTo(mQuiescentBluetoothRoute);
                    } else {
                        Log.w(this, "Ignoring switch to bluetooth command. Not available.");
                    }
                    return HANDLED;
                case SWITCH_HEADSET:
                case USER_SWITCH_HEADSET:
                    // Nothing to do
                    return HANDLED;
                case SWITCH_SPEAKER:
                case USER_SWITCH_SPEAKER:
                    transitionTo(mQuiescentSpeakerRoute);
                    return HANDLED;
                case SWITCH_FOCUS:
                    if (msg.arg1 == ACTIVE_FOCUS || msg.arg1 == RINGING_FOCUS) {
                        transitionTo(mActiveHeadsetRoute);
                    }
                    return HANDLED;
                default:
                    return NOT_HANDLED;
            }
        }
    }

    abstract class HeadsetRoute extends AudioState {
        @Override
        public boolean processMessage(Message msg) {
            if (super.processMessage(msg) == HANDLED) {
                return HANDLED;
            }
            switch (msg.what) {
                case CONNECT_WIRED_HEADSET:
                    Log.e(this, new IllegalStateException(),
                            "Wired headset should already be connected.");
                    mAvailableRoutes |= ROUTE_WIRED_HEADSET;
                    updateSystemAudioState();
                    return HANDLED;
                case CONNECT_BLUETOOTH:
                    if (!mHasUserExplicitlyLeftBluetooth) {
                        sendInternalMessage(SWITCH_BLUETOOTH);
                    } else {
                        Log.i(this, "Not switching to BT route from headset because user has " +
                                "explicitly disconnected.");
                        updateSystemAudioState();
                    }
                    return HANDLED;
                case DISCONNECT_BLUETOOTH:
                    updateSystemAudioState();
                    // No change in audio route required
                    return HANDLED;
                case DISCONNECT_WIRED_HEADSET:
                    if (mWasOnSpeaker) {
                        sendInternalMessage(SWITCH_SPEAKER);
                    } else {
                        sendInternalMessage(SWITCH_BASELINE_ROUTE);
                    }
                    return HANDLED;
                case BT_AUDIO_DISCONNECT:
                    // This may be sent as a confirmation by the BT stack after switch off BT.
                    return HANDLED;
                case CONNECT_DOCK:
                    // Nothing to do here
                    return HANDLED;
                case DISCONNECT_DOCK:
                    // Nothing to do here
                    return HANDLED;
                default:
                    return NOT_HANDLED;
            }
        }
    }

    class ActiveBluetoothRoute extends BluetoothRoute {
        @Override
        public String getName() {
            return ACTIVE_BLUETOOTH_ROUTE_NAME;
        }

        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public void enter() {
            super.enter();
            setSpeakerphoneOn(false);
            setBluetoothOn(true);
            CallAudioState newState = new CallAudioState(mIsMuted, ROUTE_BLUETOOTH,
                    mAvailableRoutes);
            setSystemAudioState(newState);
            updateInternalCallAudioState();
        }

        @Override
        public void updateSystemAudioState() {
            updateInternalCallAudioState();
            setSystemAudioState(mCurrentCallAudioState);
        }

        @Override
        public boolean processMessage(Message msg) {
            if (super.processMessage(msg) == HANDLED) {
                return HANDLED;
            }
            switch (msg.what) {
                case USER_SWITCH_EARPIECE:
                    mHasUserExplicitlyLeftBluetooth = true;
                    // fall through
                case SWITCH_EARPIECE:
                    if ((mAvailableRoutes & ROUTE_EARPIECE) != 0) {
                        transitionTo(mActiveEarpieceRoute);
                    } else {
                        Log.w(this, "Ignoring switch to earpiece command. Not available.");
                    }
                    return HANDLED;
                case SWITCH_BLUETOOTH:
                case USER_SWITCH_BLUETOOTH:
                    // Nothing to do
                    return HANDLED;
                case USER_SWITCH_HEADSET:
                    mHasUserExplicitlyLeftBluetooth = true;
                    // fall through
                case SWITCH_HEADSET:
                    if ((mAvailableRoutes & ROUTE_WIRED_HEADSET) != 0) {
                        transitionTo(mActiveHeadsetRoute);
                    } else {
                        Log.w(this, "Ignoring switch to headset command. Not available.");
                    }
                    return HANDLED;
                case USER_SWITCH_SPEAKER:
                    mHasUserExplicitlyLeftBluetooth = true;
                    // fall through
                case SWITCH_SPEAKER:
                    transitionTo(mActiveSpeakerRoute);
                    return HANDLED;
                case SWITCH_FOCUS:
                    if (msg.arg1 == NO_FOCUS) {
                        reinitialize();
                    } else if (msg.arg1 == RINGING_FOCUS) {
                        transitionTo(mRingingBluetoothRoute);
                    }
                    return HANDLED;
                case BT_AUDIO_DISCONNECT:
                    sendInternalMessage(SWITCH_BASELINE_ROUTE);
                    return HANDLED;
                default:
                    return NOT_HANDLED;
            }
        }
    }

    class RingingBluetoothRoute extends BluetoothRoute {
        @Override
        public String getName() {
            return RINGING_BLUETOOTH_ROUTE_NAME;
        }

        @Override
        public boolean isActive() {
            return false;
        }

        @Override
        public void enter() {
            super.enter();
            setSpeakerphoneOn(false);
            // Do not enable SCO audio here, since RING is being sent to the headset.
            CallAudioState newState = new CallAudioState(mIsMuted, ROUTE_BLUETOOTH,
                    mAvailableRoutes);
            setSystemAudioState(newState);
            updateInternalCallAudioState();
        }

        @Override
        public void updateSystemAudioState() {
            updateInternalCallAudioState();
            setSystemAudioState(mCurrentCallAudioState);
        }

        @Override
        public boolean processMessage(Message msg) {
            if (super.processMessage(msg) == HANDLED) {
                return HANDLED;
            }
            switch (msg.what) {
                case USER_SWITCH_EARPIECE:
                    mHasUserExplicitlyLeftBluetooth = true;
                    // fall through
                case SWITCH_EARPIECE:
                    if ((mAvailableRoutes & ROUTE_EARPIECE) != 0) {
                        transitionTo(mActiveEarpieceRoute);
                    } else {
                        Log.w(this, "Ignoring switch to earpiece command. Not available.");
                    }
                    return HANDLED;
                case SWITCH_BLUETOOTH:
                case USER_SWITCH_BLUETOOTH:
                    // Nothing to do
                    return HANDLED;
                case USER_SWITCH_HEADSET:
                    mHasUserExplicitlyLeftBluetooth = true;
                    // fall through
                case SWITCH_HEADSET:
                    if ((mAvailableRoutes & ROUTE_WIRED_HEADSET) != 0) {
                        transitionTo(mActiveHeadsetRoute);
                    } else {
                        Log.w(this, "Ignoring switch to headset command. Not available.");
                    }
                    return HANDLED;
                case USER_SWITCH_SPEAKER:
                    mHasUserExplicitlyLeftBluetooth = true;
                    // fall through
                case SWITCH_SPEAKER:
                    transitionTo(mActiveSpeakerRoute);
                    return HANDLED;
                case SWITCH_FOCUS:
                    if (msg.arg1 == NO_FOCUS) {
                        reinitialize();
                    } else if (msg.arg1 == ACTIVE_FOCUS) {
                        transitionTo(mActiveBluetoothRoute);
                    }
                    return HANDLED;
                case BT_AUDIO_DISCONNECT:
                    // Ignore BT_AUDIO_DISCONNECT when ringing, since SCO audio should not be
                    // connected.
                    return HANDLED;
                default:
                    return NOT_HANDLED;
            }
        }
    }

    class QuiescentBluetoothRoute extends BluetoothRoute {
        @Override
        public String getName() {
            return QUIESCENT_BLUETOOTH_ROUTE_NAME;
        }

        @Override
        public boolean isActive() {
            return false;
        }

        @Override
        public void enter() {
            super.enter();
            mHasUserExplicitlyLeftBluetooth = false;
            updateInternalCallAudioState();
        }

        @Override
        public void updateSystemAudioState() {
            updateInternalCallAudioState();
        }

        @Override
        public boolean processMessage(Message msg) {
            if (super.processMessage(msg) == HANDLED) {
                return HANDLED;
            }
            switch (msg.what) {
                case SWITCH_EARPIECE:
                case USER_SWITCH_EARPIECE:
                    if ((mAvailableRoutes & ROUTE_EARPIECE) != 0) {
                        transitionTo(mQuiescentEarpieceRoute);
                    } else {
                        Log.w(this, "Ignoring switch to earpiece command. Not available.");
                    }
                    return HANDLED;
                case SWITCH_BLUETOOTH:
                case USER_SWITCH_BLUETOOTH:
                    // Nothing to do
                    return HANDLED;
                case SWITCH_HEADSET:
                case USER_SWITCH_HEADSET:
                    if ((mAvailableRoutes & ROUTE_WIRED_HEADSET) != 0) {
                        transitionTo(mQuiescentHeadsetRoute);
                    } else {
                        Log.w(this, "Ignoring switch to headset command. Not available.");
                    }
                    return HANDLED;
                case SWITCH_SPEAKER:
                case USER_SWITCH_SPEAKER:
                    transitionTo(mQuiescentSpeakerRoute);
                    return HANDLED;
                case SWITCH_FOCUS:
                    if (msg.arg1 == ACTIVE_FOCUS) {
                        transitionTo(mActiveBluetoothRoute);
                    } else if (msg.arg1 == RINGING_FOCUS) {
                        transitionTo(mRingingBluetoothRoute);
                    }
                    return HANDLED;
                case BT_AUDIO_DISCONNECT:
                    // Ignore this -- audio disconnecting while quiescent should not cause a
                    // route switch, since the device is still connected.
                    return HANDLED;
                default:
                    return NOT_HANDLED;
            }
        }
    }

    abstract class BluetoothRoute extends AudioState {
        @Override
        public boolean processMessage(Message msg) {
            if (super.processMessage(msg) == HANDLED) {
                return HANDLED;
            }
            switch (msg.what) {
                case CONNECT_WIRED_HEADSET:
                    //chenteng-20180226-Update system audio state only if we're already in the bluetooth route.
                    //sendInternalMessage(SWITCH_HEADSET);
                    updateSystemAudioState();
                    return HANDLED;
                case CONNECT_BLUETOOTH:
                    // We can't tell when a change in bluetooth state corresponds to an
                    // actual connection or disconnection, so we'll just ignore it if we're already
                    // in the bluetooth route.
                    return HANDLED;
                case DISCONNECT_BLUETOOTH:
                    sendInternalMessage(SWITCH_BASELINE_ROUTE);
                    mWasOnSpeaker = false;
                    return HANDLED;
                case DISCONNECT_WIRED_HEADSET:
                    updateSystemAudioState();
                    // No change in audio route required
                    return HANDLED;
                case CONNECT_DOCK:
                    // Nothing to do here
                    return HANDLED;
                case DISCONNECT_DOCK:
                    // Nothing to do here
                    return HANDLED;
                default:
                    return NOT_HANDLED;
            }
        }
    }

    class ActiveSpeakerRoute extends SpeakerRoute {
        @Override
        public String getName() {
            return ACTIVE_SPEAKER_ROUTE_NAME;
        }

        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public void enter() {
            super.enter();
            mWasOnSpeaker = true;
            setSpeakerphoneOn(true);
            setBluetoothOn(false);
            CallAudioState newState = new CallAudioState(mIsMuted, ROUTE_SPEAKER,
                    mAvailableRoutes);
            setSystemAudioState(newState);
            updateInternalCallAudioState();
        }

        @Override
        public void updateSystemAudioState() {
            updateInternalCallAudioState();
            setSystemAudioState(mCurrentCallAudioState);
        }

        @Override
        public boolean processMessage(Message msg) {
            if (super.processMessage(msg) == HANDLED) {
                return HANDLED;
            }
            switch(msg.what) {
                case USER_SWITCH_EARPIECE:
                    mWasOnSpeaker = false;
                    // fall through
                case SWITCH_EARPIECE:
                    if ((mAvailableRoutes & ROUTE_EARPIECE) != 0) {
                        transitionTo(mActiveEarpieceRoute);
                    } else {
                        Log.w(this, "Ignoring switch to earpiece command. Not available.");
                    }
                    return HANDLED;
                case USER_SWITCH_BLUETOOTH:
                    mWasOnSpeaker = false;
                    // fall through
                case SWITCH_BLUETOOTH:
                    if ((mAvailableRoutes & ROUTE_BLUETOOTH) != 0) {
                        transitionTo(mAudioFocusType == ACTIVE_FOCUS ?
                                mActiveBluetoothRoute : mRingingBluetoothRoute);
                    } else {
                        Log.w(this, "Ignoring switch to bluetooth command. Not available.");
                    }
                    return HANDLED;
                case USER_SWITCH_HEADSET:
                    mWasOnSpeaker = false;
                    // fall through
                case SWITCH_HEADSET:
                    if ((mAvailableRoutes & ROUTE_WIRED_HEADSET) != 0) {
                        transitionTo(mActiveHeadsetRoute);
                    } else {
                        Log.w(this, "Ignoring switch to headset command. Not available.");
                    }
                    return HANDLED;
                case SWITCH_SPEAKER:
                case USER_SWITCH_SPEAKER:
                    // Nothing to do
                    return HANDLED;
                case SWITCH_FOCUS:
                    if (msg.arg1 == NO_FOCUS) {
                        reinitialize();
                    }
                    return HANDLED;
                default:
                    return NOT_HANDLED;
            }
        }
    }

    class QuiescentSpeakerRoute extends SpeakerRoute {
        @Override
        public String getName() {
            return QUIESCENT_SPEAKER_ROUTE_NAME;
        }

        @Override
        public boolean isActive() {
            return false;
        }

        @Override
        public void enter() {
            super.enter();
            mHasUserExplicitlyLeftBluetooth = false;
            // Omit setting mWasOnSpeaker to true here, since this does not reflect a call
            // actually being on speakerphone.
            updateInternalCallAudioState();
        }

        @Override
        public void updateSystemAudioState() {
            updateInternalCallAudioState();
        }

        @Override
        public boolean processMessage(Message msg) {
            if (super.processMessage(msg) == HANDLED) {
                return HANDLED;
            }
            switch(msg.what) {
                case SWITCH_EARPIECE:
                case USER_SWITCH_EARPIECE:
                    if ((mAvailableRoutes & ROUTE_EARPIECE) != 0) {
                        transitionTo(mQuiescentEarpieceRoute);
                    } else {
                        Log.w(this, "Ignoring switch to earpiece command. Not available.");
                    }
                    return HANDLED;
                case SWITCH_BLUETOOTH:
                case USER_SWITCH_BLUETOOTH:
                    if ((mAvailableRoutes & ROUTE_BLUETOOTH) != 0) {
                        transitionTo(mQuiescentBluetoothRoute);
                    } else {
                        Log.w(this, "Ignoring switch to bluetooth command. Not available.");
                    }
                    return HANDLED;
                case SWITCH_HEADSET:
                case USER_SWITCH_HEADSET:
                    if ((mAvailableRoutes & ROUTE_WIRED_HEADSET) != 0) {
                        transitionTo(mQuiescentHeadsetRoute);
                    } else {
                        Log.w(this, "Ignoring switch to headset command. Not available.");
                    }
                    return HANDLED;
                case SWITCH_SPEAKER:
                case USER_SWITCH_SPEAKER:
                    // Nothing to do
                    return HANDLED;
                case SWITCH_FOCUS:
                    if (msg.arg1 == ACTIVE_FOCUS || msg.arg1 == RINGING_FOCUS) {
                        transitionTo(mActiveSpeakerRoute);
                    }
                    return HANDLED;
                default:
                    return NOT_HANDLED;
            }
        }
    }

    abstract class SpeakerRoute extends AudioState {
        @Override
        public boolean processMessage(Message msg) {
            if (super.processMessage(msg) == HANDLED) {
                return HANDLED;
            }
            switch (msg.what) {
                case CONNECT_WIRED_HEADSET:
                    sendInternalMessage(SWITCH_HEADSET);
                    return HANDLED;
                case CONNECT_BLUETOOTH:
                    if (!mHasUserExplicitlyLeftBluetooth) {
                        sendInternalMessage(SWITCH_BLUETOOTH);
                    } else {
                        Log.i(this, "Not switching to BT route from speaker because user has " +
                                "explicitly disconnected.");
                        updateSystemAudioState();
                    }
                    return HANDLED;
                case DISCONNECT_BLUETOOTH:
                    updateSystemAudioState();
                    // No change in audio route required
                    return HANDLED;
                case DISCONNECT_WIRED_HEADSET:
                    updateSystemAudioState();
                    // No change in audio route required
                    return HANDLED;
                case BT_AUDIO_DISCONNECT:
                    // This may be sent as a confirmation by the BT stack after switch off BT.
                    return HANDLED;
                case CONNECT_DOCK:
                    // Nothing to do here
                    return HANDLED;
                case DISCONNECT_DOCK:
                    sendInternalMessage(SWITCH_BASELINE_ROUTE);
                    return HANDLED;
               default:
                    return NOT_HANDLED;
            }
        }
    }

    private final ActiveEarpieceRoute mActiveEarpieceRoute = new ActiveEarpieceRoute();
    private final ActiveHeadsetRoute mActiveHeadsetRoute = new ActiveHeadsetRoute();
    private final ActiveBluetoothRoute mActiveBluetoothRoute = new ActiveBluetoothRoute();
    private final ActiveSpeakerRoute mActiveSpeakerRoute = new ActiveSpeakerRoute();
    private final RingingBluetoothRoute mRingingBluetoothRoute = new RingingBluetoothRoute();
    private final QuiescentEarpieceRoute mQuiescentEarpieceRoute = new QuiescentEarpieceRoute();
    private final QuiescentHeadsetRoute mQuiescentHeadsetRoute = new QuiescentHeadsetRoute();
    private final QuiescentBluetoothRoute mQuiescentBluetoothRoute = new QuiescentBluetoothRoute();
    private final QuiescentSpeakerRoute mQuiescentSpeakerRoute = new QuiescentSpeakerRoute();

    /**
     * A few pieces of hidden state. Used to avoid exponential explosion of number of explicit
     * states
     */
    private int mAvailableRoutes;
    private int mAudioFocusType;
    private boolean mWasOnSpeaker;
    private boolean mIsMuted;
    private boolean mAreNotificationSuppressed = false;

    private final Context mContext;
    private final CallsManager mCallsManager;
    private final AudioManager mAudioManager;
    private final BluetoothManager mBluetoothManager;
    private final WiredHeadsetManager mWiredHeadsetManager;
    private final StatusBarNotifier mStatusBarNotifier;
    private final CallAudioManager.AudioServiceFactory mAudioServiceFactory;
    private final InterruptionFilterProxy mInterruptionFilterProxy;
    private final boolean mDoesDeviceSupportEarpieceRoute;
    private final TelecomSystem.SyncRoot mLock;
    private boolean mHasUserExplicitlyLeftBluetooth = false;

    private HashMap<String, Integer> mStateNameToRouteCode;
    private HashMap<Integer, AudioState> mRouteCodeToQuiescentState;

    // CallAudioState is used as an interface to communicate with many other system components.
    // No internal state transitions should depend on this variable.
    private CallAudioState mCurrentCallAudioState;
    private CallAudioState mLastKnownCallAudioState;

    public CallAudioRouteStateMachine(
            Context context,
            CallsManager callsManager,
            BluetoothManager bluetoothManager,
            WiredHeadsetManager wiredHeadsetManager,
            StatusBarNotifier statusBarNotifier,
            CallAudioManager.AudioServiceFactory audioServiceFactory,
            InterruptionFilterProxy interruptionFilterProxy,
            boolean doesDeviceSupportEarpieceRoute) {
        super(NAME);
        addState(mActiveEarpieceRoute);
        addState(mActiveHeadsetRoute);
        addState(mActiveBluetoothRoute);
        addState(mActiveSpeakerRoute);
        addState(mRingingBluetoothRoute);
        addState(mQuiescentEarpieceRoute);
        addState(mQuiescentHeadsetRoute);
        addState(mQuiescentBluetoothRoute);
        addState(mQuiescentSpeakerRoute);

        mContext = context;
        mCallsManager = callsManager;
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        mBluetoothManager = bluetoothManager;
        mWiredHeadsetManager = wiredHeadsetManager;
        mStatusBarNotifier = statusBarNotifier;
        mAudioServiceFactory = audioServiceFactory;
        mInterruptionFilterProxy = interruptionFilterProxy;
        // Register for misc other intent broadcasts.
        IntentFilter intentFilter =
                new IntentFilter(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED);
        context.registerReceiver(mReceiver, intentFilter);
        mDoesDeviceSupportEarpieceRoute = doesDeviceSupportEarpieceRoute;
        mLock = callsManager.getLock();

        mStateNameToRouteCode = new HashMap<>(8);
        mStateNameToRouteCode.put(mQuiescentEarpieceRoute.getName(), ROUTE_EARPIECE);
        mStateNameToRouteCode.put(mQuiescentBluetoothRoute.getName(), ROUTE_BLUETOOTH);
        mStateNameToRouteCode.put(mQuiescentHeadsetRoute.getName(), ROUTE_WIRED_HEADSET);
        mStateNameToRouteCode.put(mQuiescentSpeakerRoute.getName(), ROUTE_SPEAKER);
        mStateNameToRouteCode.put(mRingingBluetoothRoute.getName(), ROUTE_BLUETOOTH);
        mStateNameToRouteCode.put(mActiveEarpieceRoute.getName(), ROUTE_EARPIECE);
        mStateNameToRouteCode.put(mActiveBluetoothRoute.getName(), ROUTE_BLUETOOTH);
        mStateNameToRouteCode.put(mActiveHeadsetRoute.getName(), ROUTE_WIRED_HEADSET);
        mStateNameToRouteCode.put(mActiveSpeakerRoute.getName(), ROUTE_SPEAKER);

        mRouteCodeToQuiescentState = new HashMap<>(4);
        mRouteCodeToQuiescentState.put(ROUTE_EARPIECE, mQuiescentEarpieceRoute);
        mRouteCodeToQuiescentState.put(ROUTE_BLUETOOTH, mQuiescentBluetoothRoute);
        mRouteCodeToQuiescentState.put(ROUTE_SPEAKER, mQuiescentSpeakerRoute);
        mRouteCodeToQuiescentState.put(ROUTE_WIRED_HEADSET, mQuiescentHeadsetRoute);
    }

    /**
     * Initializes the state machine with info on initial audio route, supported audio routes,
     * and mute status.
     */
    public void initialize() {
        CallAudioState initState = getInitialAudioState();
        initialize(initState);
    }

    public void initialize(CallAudioState initState) {
        mCurrentCallAudioState = initState;
        mLastKnownCallAudioState = initState;
        mAvailableRoutes = initState.getSupportedRouteMask();
        mIsMuted = initState.isMuted();
        mWasOnSpeaker = false;

        mStatusBarNotifier.notifyMute(initState.isMuted());
        mStatusBarNotifier.notifySpeakerphone(initState.getRoute() == CallAudioState.ROUTE_SPEAKER);
        setInitialState(mRouteCodeToQuiescentState.get(initState.getRoute()));
        start();
    }

    /**
     * Getter for the current CallAudioState object that the state machine is keeping track of.
     * Used for compatibility purposes.
     */
    public CallAudioState getCurrentCallAudioState() {
        return mCurrentCallAudioState;
    }

    public void sendMessageWithSessionInfo(int message, int arg) {
        sendMessage(message, arg, 0, Log.createSubsession());
    }

    public void sendMessageWithSessionInfo(int message) {
        sendMessage(message, 0, 0, Log.createSubsession());
    }

    /**
     * This is for state-independent changes in audio route (i.e. muting or runnables)
     * @param msg that couldn't be handled.
     */
    @Override
    protected void unhandledMessage(Message msg) {
        CallAudioState newCallAudioState;
        switch (msg.what) {
            case MUTE_ON:
                setMuteOn(true);
                newCallAudioState = new CallAudioState(mIsMuted,
                        mCurrentCallAudioState.getRoute(),
                        mAvailableRoutes);
                setSystemAudioState(newCallAudioState);
                updateInternalCallAudioState();
                return;
            case MUTE_OFF:
                setMuteOn(false);
                newCallAudioState = new CallAudioState(mIsMuted,
                        mCurrentCallAudioState.getRoute(),
                        mAvailableRoutes);
                setSystemAudioState(newCallAudioState);
                updateInternalCallAudioState();
                return;
            case TOGGLE_MUTE:
                if (mIsMuted) {
                    sendInternalMessage(MUTE_OFF);
                } else {
                    sendInternalMessage(MUTE_ON);
                }
                return;
            ///M: ALPS03573205 Add restore mute message. @{
            case RESTORE_MUTE:
                restoreMuteOnWhenInCallMode();
                return;
            /// @}
            case UPDATE_SYSTEM_AUDIO_ROUTE:
                resendSystemAudioState();
                return;
            case RUN_RUNNABLE:
                java.lang.Runnable r = (java.lang.Runnable) msg.obj;
                r.run();
                return;
            default:
                Log.e(this, new IllegalStateException(),
                        "Unexpected message code");
        }
    }

    public void quitStateMachine() {
        quitNow();
    }

    /**
     * Sets whether notifications should be suppressed or not.  Used when in a call to ensure the
     * device will not vibrate due to notifications.
     * Alarm-only filtering is activated when
     *
     * @param on {@code true} when notification suppression should be activated, {@code false} when
     *                       it should be deactivated.
     */
    private void setNotificationsSuppressed(boolean on) {
        if (mInterruptionFilterProxy == null) {
            return;
        }

        Log.i(this, "setNotificationsSuppressed: on=%s; suppressed=%s", (on ? "yes" : "no"),
                (mAreNotificationSuppressed ? "yes" : "no"));
        if (on) {
            if (!mAreNotificationSuppressed) {
                // Enabling suppression of notifications.
                int interruptionFilter = mInterruptionFilterProxy.getCurrentInterruptionFilter();
                if (interruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL) {
                    // No interruption filter is specified, so suppress notifications by setting the
                    // current filter to alarms-only.
                    mAreNotificationSuppressed = true;
                    mInterruptionFilterProxy.setInterruptionFilter(
                            NotificationManager.INTERRUPTION_FILTER_ALARMS);
                } else {
                    // Interruption filter is already chosen by the user, so do not attempt to change
                    // it.
                    mAreNotificationSuppressed = false;
                }
            }
        } else {
            // Disabling suppression of notifications.
            if (mAreNotificationSuppressed) {
                // We have implemented the alarms-only policy and the user has not changed it since
                // we originally set it, so reset the notification filter.
                mInterruptionFilterProxy.setInterruptionFilter(
                        NotificationManager.INTERRUPTION_FILTER_ALL);
            }
            mAreNotificationSuppressed = false;
        }
    }

    private void setSpeakerphoneOn(boolean on) {
        if (mAudioManager.isSpeakerphoneOn() != on) {
            Log.i(this, "turning speaker phone %s", on);
            mAudioManager.setSpeakerphoneOn(on);
            mStatusBarNotifier.notifySpeakerphone(on);
        }
    }

    private void setBluetoothOn(boolean on) {
        if (mBluetoothManager.isBluetoothAvailable()) {
            boolean isAlreadyOn = mBluetoothManager.isBluetoothAudioConnectedOrPending();
            if (on != isAlreadyOn) {
                Log.i(this, "connecting bluetooth %s", on);
                if (on) {
                    ///M: AOSP bug @{
                    // google designs to connect BT SCO when MT is ringing state
                    // it violate BT spec
                    // MTK do workaround to avoid connect BT SCO this time
                    // the SCO connect is left to BT stack to handle according to call state
                    if (mCallsManager.isAllCallRinging()) {
                        Log.d(this, "do not connect BT because all calls are ringing");
                        return;
                    }
                    /// @}
                    mBluetoothManager.connectBluetoothAudio();
                } else {
                    mBluetoothManager.disconnectBluetoothAudio();
                }
            }
        }
    }

    private void setMuteOn(boolean mute) {
        mIsMuted = mute;
        Log.event(mCallsManager.getForegroundCall(), mute ? Log.Events.MUTE : Log.Events.UNMUTE);

        if (mute != mAudioManager.isMicrophoneMute() && isInActiveState()) {
            IAudioService audio = mAudioServiceFactory.getAudioService();
            Log.i(this, "changing microphone mute state to: %b [serviceIsNull=%b]",
                    mute, audio == null);
            if (audio != null) {
                try {
                    // We use the audio service directly here so that we can specify
                    // the current user. Telecom runs in the system_server process which
                    // may run as a separate user from the foreground user. If we
                    // used AudioManager directly, we would change mute for the system's
                    // user and not the current foreground, which we want to avoid.
                    audio.setMicrophoneMute(
                            mute, mContext.getOpPackageName(), getCurrentUserId());
                    mStatusBarNotifier.notifyMute(mute);

                } catch (RemoteException e) {
                    Log.e(this, e, "Remote exception while toggling mute.");
                }
                // TODO: Check microphone state after attempting to set to ensure that
                // our state corroborates AudioManager's state.
            }
        }
    }

    /**
     * Updates the CallAudioState object from current internal state. The result is used for
     * external communication only.
     */
    private void updateInternalCallAudioState() {
        IState currentState = getCurrentState();
        if (currentState == null) {
            Log.e(this, new IllegalStateException(), "Current state should never be null" +
                    " when updateInternalCallAudioState is called.");
            mCurrentCallAudioState = new CallAudioState(
                    mIsMuted, mCurrentCallAudioState.getRoute(), mAvailableRoutes);
            return;
        }
        int currentRoute = mStateNameToRouteCode.get(currentState.getName());
        mCurrentCallAudioState = new CallAudioState(mIsMuted, currentRoute, mAvailableRoutes);
    }

    private void setSystemAudioState(CallAudioState newCallAudioState) {
        ///M: ALPS02797725 @{
        // show mute and speaker icon in status bar
        mStatusBarNotifier.notifyMute(newCallAudioState.isMuted());
        mStatusBarNotifier.notifySpeakerphone(newCallAudioState.getRoute() ==
            CallAudioState.ROUTE_SPEAKER);
        /// @}
        setSystemAudioState(newCallAudioState, false);
    }

    private void resendSystemAudioState() {
        setSystemAudioState(mLastKnownCallAudioState, true);
    }

    private void setSystemAudioState(CallAudioState newCallAudioState, boolean force) {
        /// M: [ALPS03135183]fix CTS failure caused by timing.
        /// Google added this lock in commit: 609992b to fix unit tests.
        /// Since this method would always run in the State Machine Thread, it's not necessary to
        /// synchronized here except in test cases.
        /// As a result of this lock, there would be 70ms or more delay for the Telecom to
        /// set AudioState to ConnectionService by CallsManager#onCallAudioStateChanged.
        /// This delay would then cause NullPointerException in cts test case:
        /// ExtendedInCallServiceTest#testSwitchAudioRoutes (Although it seems more like a defect
        /// of the cts case itself)
        /// Google default code:
        /// synchronized (mLock) {
            Log.i(this, "setSystemAudioState: changing from %s to %s", mLastKnownCallAudioState,
                    newCallAudioState);
            if (force || !newCallAudioState.equals(mLastKnownCallAudioState)) {
                if (newCallAudioState.getRoute() != mLastKnownCallAudioState.getRoute()) {
                    Log.event(mCallsManager.getForegroundCall(),
                            AUDIO_ROUTE_TO_LOG_EVENT.get(newCallAudioState.getRoute(),
                                    Log.Events.AUDIO_ROUTE));
                }

                mCallsManager.onCallAudioStateChanged(mLastKnownCallAudioState, newCallAudioState);
                updateAudioForForegroundCall(newCallAudioState);
                mLastKnownCallAudioState = newCallAudioState;
            }
        /// }
    }

    private void updateAudioForForegroundCall(CallAudioState newCallAudioState) {
        Call call = mCallsManager.getForegroundCall();
        if (call != null && call.getConnectionService() != null) {
            call.getConnectionService().onCallAudioStateChanged(call, newCallAudioState);
        }
    }

    private int calculateSupportedRoutes() {
        int routeMask = CallAudioState.ROUTE_SPEAKER;

        if (mWiredHeadsetManager.isPluggedIn()) {
            routeMask |= CallAudioState.ROUTE_WIRED_HEADSET;
        } else if (mDoesDeviceSupportEarpieceRoute){
            routeMask |= CallAudioState.ROUTE_EARPIECE;
        }

        if (mBluetoothManager.isBluetoothAvailable()) {
            routeMask |=  CallAudioState.ROUTE_BLUETOOTH;
        }

        return routeMask;
    }

    private void sendInternalMessage(int messageCode) {
        // Internal messages are messages which the state machine sends to itself in the
        // course of processing externally-sourced messages. We want to send these messages at
        // the front of the queue in order to make actions appear atomic to the user and to
        // prevent scenarios such as these:
        // 1. State machine handler thread is suspended for some reason.
        // 2. Headset gets connected (sends CONNECT_HEADSET).
        // 3. User switches to speakerphone in the UI (sends SWITCH_SPEAKER).
        // 4. State machine handler is un-suspended.
        // 5. State machine handler processes the CONNECT_HEADSET message and sends
        //    SWITCH_HEADSET at end of queue.
        // 6. State machine handler processes SWITCH_SPEAKER.
        // 7. State machine handler processes SWITCH_HEADSET.
        Session subsession = Log.createSubsession();
        if(subsession != null) {
            sendMessageAtFrontOfQueue(messageCode, subsession);
        } else {
            sendMessageAtFrontOfQueue(messageCode);
        }
    }

    private CallAudioState getInitialAudioState() {
        int supportedRouteMask = calculateSupportedRoutes();
        final int route;

        if ((supportedRouteMask & ROUTE_BLUETOOTH) != 0) {
            route = ROUTE_BLUETOOTH;
        } else if ((supportedRouteMask & ROUTE_WIRED_HEADSET) != 0) {
            route = ROUTE_WIRED_HEADSET;
        } else if ((supportedRouteMask & ROUTE_EARPIECE) != 0) {
            route = ROUTE_EARPIECE;
        } else {
            route = ROUTE_SPEAKER;
        }

        return new CallAudioState(false, route, supportedRouteMask);
    }

    private int getCurrentUserId() {
        final long ident = Binder.clearCallingIdentity();
        try {
            UserInfo currentUser = ActivityManagerNative.getDefault().getCurrentUser();
            return currentUser.id;
        } catch (RemoteException e) {
            // Activity manager not running, nothing we can do assume user 0.
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
        return UserHandle.USER_OWNER;
    }

    private boolean isInActiveState() {
        AudioState currentState = (AudioState) getCurrentState();
        if (currentState == null) {
            Log.w(this, "Current state is null, assuming inactive state");
            return false;
        }
        return currentState.isActive();
    }

    public static boolean doesDeviceSupportEarpieceRoute() {
        String[] characteristics = SystemProperties.get("ro.build.characteristics").split(",");
        for (String characteristic : characteristics) {
            if ("watch".equals(characteristic)) {
                return false;
            }
        }
        return true;
    }

    private int calculateBaselineRouteMessage(boolean isExplicitUserRequest) {
        if ((mAvailableRoutes & ROUTE_EARPIECE) != 0) {
            return isExplicitUserRequest ? USER_SWITCH_EARPIECE : SWITCH_EARPIECE;
        } else if ((mAvailableRoutes & ROUTE_WIRED_HEADSET) != 0) {
            return isExplicitUserRequest ? USER_SWITCH_HEADSET : SWITCH_HEADSET;
        } else if (!mDoesDeviceSupportEarpieceRoute) {
            return isExplicitUserRequest ? USER_SWITCH_SPEAKER : SWITCH_SPEAKER;
        } else {
            Log.e(this, new IllegalStateException(),
                    "Neither headset nor earpiece are available, but there is an " +
                            "earpiece on the device. Defaulting to earpiece.");
            return isExplicitUserRequest ? USER_SWITCH_EARPIECE : SWITCH_EARPIECE;
        }
    }

    private void reinitialize() {
        CallAudioState initState = getInitialAudioState();
        mAvailableRoutes = initState.getSupportedRouteMask();
        mIsMuted = initState.isMuted();
        setMuteOn(mIsMuted);
        mWasOnSpeaker = false;
        mHasUserExplicitlyLeftBluetooth = false;
        mLastKnownCallAudioState = initState;
        transitionTo(mRouteCodeToQuiescentState.get(initState.getRoute()));
    }

    /**
     * M: restore mute operation @{
     * like hold cdma call, then unhold cdma call,
     * or Gsm/cdma call swap on SVLTE based mute is on
     */
    void restoreMuteOnWhenInCallMode() {
        Log.event(mCallsManager.getForegroundCall(), Log.Events.MUTE,
                mIsMuted ? "on" : "off");
        if (mIsMuted && isInActiveState()) {
            IAudioService audio = mAudioServiceFactory.getAudioService();
            Log.i(this, "changing microphone mute state to: %b [serviceIsNull=%b]",
                    mIsMuted, audio == null);
            if (audio != null) {
                try {
                    // We use the audio service directly here so that we can specify
                    // the current user. Telecom runs in the system_server process which
                    // may run as a separate user from the foreground user. If we
                    // used AudioManager directly, we would change mute for the system's
                    // user and not the current foreground, which we want to avoid.
                    audio.setMicrophoneMute(
                            mIsMuted, mContext.getOpPackageName(), getCurrentUserId());

                } catch (RemoteException e) {
                    Log.e(this, e, "Remote exception while toggling mute.");
                }
                // TODO: Check microphone state after attempting to set to ensure that
                // our state corroborates AudioManager's state.
            }
        }
    }
    /** @}   */
}
