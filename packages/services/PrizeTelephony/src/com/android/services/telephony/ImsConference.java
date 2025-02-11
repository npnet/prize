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
 * limitations under the License
 */

package com.android.services.telephony;

import android.content.Context;
import android.graphics.drawable.Icon;

import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemProperties;
import android.os.PersistableBundle;
import android.telecom.Conference;
import android.telecom.ConferenceParticipant;
import android.telecom.Connection.VideoProvider;
import android.telecom.Connection;
import android.telecom.DisconnectCause;
import android.telecom.Log;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.StatusHints;
import android.telecom.VideoProfile;
import android.telephony.CarrierConfigManager;
import android.telephony.PhoneNumberUtils;
import android.util.Pair;

/// M: For VoLTE enhanced conference call. @{
import android.telephony.PhoneNumberUtils;
import android.telephony.SubscriptionManager;
/// M: For query local MSISDN.
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import android.widget.Toast;
/// @}

import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.phone.PhoneGlobals;
import com.android.phone.PhoneUtils;
import com.android.phone.R;

import com.mediatek.internal.telephony.RadioCapabilitySwitchUtil;
/// M: ALPS02136977. Prints debug logs for telephony.
import com.mediatek.telecom.FormattedLog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Represents an IMS conference call.
 * <p>
 * An IMS conference call consists of a conference host connection and potentially a list of
 * conference participants.  The conference host connection represents the radio connection to the
 * IMS conference server.  Since it is not a connection to any one individual, it is not represented
 * in Telecom/InCall as a call.  The conference participant information is received via the host
 * connection via a conference event package.  Conference participant connections do not represent
 * actual radio connections to the participants; they act as a virtual representation of the
 * participant, keyed by a unique endpoint {@link android.net.Uri}.
 * (MediaTek: It should use user entity instead of endpoint)
 * <p>
 * The {@link ImsConference} listens for conference event package data received via the host
 * connection and is responsible for managing the conference participant connections which represent
 * the participants.
 */
public class ImsConference extends Conference {

    // Sensitive log task
    private static final String PROP_FORCE_DEBUG_KEY = "persist.log.tag.tel_dbg";
    private static final boolean SENLOG = TextUtils.equals(Build.TYPE, "user");
    private static final boolean TELDBG = (SystemProperties.getInt(PROP_FORCE_DEBUG_KEY, 0) == 1);

    /**
     * Listener used to respond to changes to conference participants.  At the conference level we
     * are most concerned with handling destruction of a conference participant.
     */
    private final Connection.Listener mParticipantListener = new Connection.Listener() {
        /**
         * Participant has been destroyed.  Remove it from the conference.
         *
         * @param connection The participant which was destroyed.
         */
        @Override
        public void onDestroyed(Connection connection) {
            ConferenceParticipantConnection participant =
                    (ConferenceParticipantConnection) connection;
            removeConferenceParticipant(participant);
            updateManageConference();
        }

    };

    /**
     * Listener used to respond to changes to the underlying radio connection for the conference
     * host connection.  Used to respond to SRVCC changes.
     */
    private final TelephonyConnection.TelephonyConnectionListener mTelephonyConnectionListener =
            new TelephonyConnection.TelephonyConnectionListener() {

        @Override
        public void onOriginalConnectionConfigured(TelephonyConnection c) {
            if (c == mConferenceHost) {
               handleOriginalConnectionChange();
            }
        }

        /// M: VoLTE. @{
        /**
         * For VoLTE enhanced conference call, notify invite conf. participants completed.
         * @param isSuccess is success or not.
         */
        @Override
        public void onConferenceParticipantsInvited(boolean isSuccess) {
            mIsDuringAddingParticipants = false;
        }

        /**
         * For VoLTE conference SRVCC, notify when new participant connections maded.
         * @param radioConnections new participant connections.
         */
        @Override
        public void onConferenceConnectionsConfigured(
                ArrayList<com.android.internal.telephony.Connection> radioConnections) {
            handleConferenceSRVCC(radioConnections);
        }
        /// @}
    };

    /**
     * Listener used to respond to changes to the connection to the IMS conference server.
     */
    private final android.telecom.Connection.Listener mConferenceHostListener =
            new android.telecom.Connection.Listener() {

        /**
         * Updates the state of the conference based on the new state of the host.
         *
         * @param c The host connection.
         * @param state The new state
         */
        @Override
        public void onStateChanged(android.telecom.Connection c, int state) {
            /// M: ALPS02136977. Prints debug messages for telephony. @{
            if (mConferenceHost != null && mConferenceHost.getOriginalConnection() != null) {
                logDebugMsgWithNotifyFormat(
                        TelephonyConnection.callStateToFormattedNotifyString(
                                mConferenceHost.getOriginalConnection().getState()), null);
            }
            /// @}

            setState(state);
        }

        /**
         * Disconnects the conference when its host connection disconnects.
         *
         * @param c The host connection.
         * @param disconnectCause The host connection disconnect cause.
         */
        @Override
        public void onDisconnected(android.telecom.Connection c, DisconnectCause disconnectCause) {
            setDisconnected(disconnectCause);
        }

        /**
         * Handles destruction of the host connection; once the host connection has been
         * destroyed, cleans up the conference participant connection.
         *
         * @param connection The host connection.
         */
        @Override
        public void onDestroyed(android.telecom.Connection connection) {
            disconnectConferenceParticipants();
        }

        /**
         * Handles changes to conference participant data as reported by the conference host
         * connection.
         *
         * @param c The connection.
         * @param participants The participant information.
         */
        @Override
        public void onConferenceParticipantsChanged(android.telecom.Connection c,
                List<ConferenceParticipant> participants) {

            if (c == null || participants == null) {
                return;
            }
            Log.v(this, "onConferenceParticipantsChanged: %d participants", participants.size());

            /// M: ALPS02136977. Prints debug messages for telephony. @{
            StringBuilder sb = new StringBuilder();
            sb.append(" participants:");
            for (ConferenceParticipant participant : participants) {
                sb.append(participant.toString());
            }
            logDebugMsgWithNotifyFormat("ConfXMLNotify", sb.toString());
            /// @}

            TelephonyConnection telephonyConnection = (TelephonyConnection) c;
            handleConferenceParticipantsUpdate(telephonyConnection, participants);
        }

        @Override
        public void onVideoStateChanged(android.telecom.Connection c, int videoState) {
            Log.d(this, "onVideoStateChanged video state %d", videoState);
            setVideoState(c, videoState);
        }

        @Override
        public void onVideoProviderChanged(android.telecom.Connection c,
                Connection.VideoProvider videoProvider) {
            Log.d(this, "onVideoProviderChanged: Connection: %s, VideoProvider: %s", c,
                    videoProvider);
            setVideoProvider(c, videoProvider);
        }

        @Override
        public void onConnectionCapabilitiesChanged(Connection c, int connectionCapabilities) {
            Log.d(this, "onConnectionCapabilitiesChanged: Connection: %s," +
                    " connectionCapabilities: %s", c, connectionCapabilities);
            int capabilites = ImsConference.this.getConnectionCapabilities();
            boolean isVideoConferencingSupported = mConferenceHost == null ? false :
                    mConferenceHost.isCarrierVideoConferencingSupported();
            setConnectionCapabilities(applyHostCapabilities(capabilites, connectionCapabilities,
                    isVideoConferencingSupported));
        }

        @Override
        public void onConnectionPropertiesChanged(Connection c, int connectionProperties) {
            Log.d(this, "onConnectionPropertiesChanged: Connection: %s," +
                    " connectionProperties: %s", c, connectionProperties);
            int properties = ImsConference.this.getConnectionProperties();
            setConnectionProperties(applyHostProperties(properties, connectionProperties));
        }

        @Override
        public void onStatusHintsChanged(Connection c, StatusHints statusHints) {
            Log.v(this, "onStatusHintsChanged");
            updateStatusHints();
        }

        @Override
        public void onExtrasChanged(Connection c, Bundle extras) {
            Log.v(this, "onExtrasChanged: c=" + c + " Extras=" + extras);
            putExtras(extras);
        }

        @Override
        public void onExtrasRemoved(Connection c, List<String> keys) {
            Log.v(this, "onExtrasRemoved: c=" + c + " key=" + keys);
            removeExtras(keys);
        }
    };

    /**
     * The telephony connection service; used to add new participant connections to Telecom.
     */
    private TelephonyConnectionService mTelephonyConnectionService;

    /**
     * The connection to the conference server which is hosting the conference.
     */
    private TelephonyConnection mConferenceHost;

    /**
     * The PhoneAccountHandle of the conference host.
     */
    private PhoneAccountHandle mConferenceHostPhoneAccountHandle;

    /**
     * The address of the conference host.
     */
    private Uri[] mConferenceHostAddress;

    /**
     * The known conference participant connections.  The HashMap is keyed by endpoint Uri.
     * (MediaTek: according to ts_124147 and rfc4575, it should use user entity Uri as the key.)
     * The known conference participant connections.  The HashMap is keyed by a Pair containing
     * the handle and endpoint Uris.
     * Access to the hashmap is protected by the {@link #mUpdateSyncRoot}.
     */
    private final HashMap<Pair<Uri, Uri>, ConferenceParticipantConnection>
            mConferenceParticipantConnections = new HashMap<>();

    /**
     * Sychronization root used to ensure that updates to the
     * {@link #mConferenceParticipantConnections} happen atomically are are not interleaved across
     * threads.  There are some instances where the network will send conference event package
     * data closely spaced.  If that happens, it is possible that the interleaving of the update
     * will cause duplicate participant info to be added.
     */
    private final Object mUpdateSyncRoot = new Object();

    /// M: For VoLTE enhanced conference call. @{
    private boolean mIsDuringAddingParticipants = false;
    /// @}

    /// M: ALPS02209724. Filter host call in manage conference screen, and support addMember. @{
    private Uri mHostCallAddress = null;
    /// @}
    /**
     * Max. numbers of participants in a IMS conference.
     */
    public static final int IMS_CONFERENCE_MAX_SIZE = 5;

    /// M: ALPS02487069. Keep the state of conference host. @{
    private int mHostCallState = Connection.STATE_NEW;
    /// @}

    public void updateConferenceParticipantsAfterCreation() {
        if (mConferenceHost != null) {
            Log.v(this, "updateConferenceStateAfterCreation :: process participant update");
            handleConferenceParticipantsUpdate(mConferenceHost,
                    mConferenceHost.getConferenceParticipants());
        } else {
            Log.v(this, "updateConferenceStateAfterCreation :: null mConferenceHost");
        }
    }

    /**
     * Initializes a new {@link ImsConference}.
     *
     * @param telephonyConnectionService The connection service responsible for adding new
     *                                   conferene participants.
     * @param conferenceHost The telephony connection hosting the conference.
     * @param phoneAccountHandle The phone account handle associated with the conference.
     */
    public ImsConference(TelephonyConnectionService telephonyConnectionService,
            TelephonyConnection conferenceHost, PhoneAccountHandle phoneAccountHandle) {

        super(phoneAccountHandle);

        // Specify the connection time of the conference to be the connection time of the original
        // connection.
        /// M: ALPS02653397, original connection may be null @{
        if (conferenceHost != null && conferenceHost.getOriginalConnection() != null) {
        /// @}
            long connectTime = conferenceHost.getOriginalConnection().getConnectTime();
            setConnectTimeMillis(connectTime);
            // Set the connectTime in the connection as well.
            conferenceHost.setConnectTimeMillis(connectTime);
        }

        mTelephonyConnectionService = telephonyConnectionService;
        setConferenceHost(conferenceHost);

        int capabilities = Connection.CAPABILITY_MUTE |
                Connection.CAPABILITY_CONFERENCE_HAS_NO_CHILDREN;
        if (canHoldImsCalls()) {
            capabilities |= Connection.CAPABILITY_SUPPORT_HOLD | Connection.CAPABILITY_HOLD;
        }
        /// M: ALPS02065487. For VoLTE conference inviteParticipant. @{
        /// Enable CAPABILITY_INVITE_PARTICIPANTS by checking if current phone
        /// has VOLTE_ENHANCED_CONFERENCE feature.
        if (conferenceHost != null && conferenceHost.getOriginalConnection() != null
                && conferenceHost.getOriginalConnection().getCall().getPhone() != null) {
            if (conferenceHost.getOriginalConnection().getCall().getPhone()
                    .isFeatureSupported(Phone.FeatureType.VOLTE_ENHANCED_CONFERENCE)) {
                capabilities |= Connection.CAPABILITY_INVITE_PARTICIPANTS;
            }
        }
        /// @}

        capabilities = applyHostCapabilities(capabilities,
                mConferenceHost.getConnectionCapabilities(),
                mConferenceHost.isCarrierVideoConferencingSupported());

        /// M : For WFC, @{
        // N migration, API not ready
        //capabilities = applyWifiCapabiliities(capabilities);
        /// @}

        setConnectionCapabilities(capabilities);

        /// M: Set the properties. @{
        int properties = Connection.PROPERTY_VOLTE;
        properties = applyHostProperties(properties,
                mConferenceHost.getConnectionProperties());
        Log.d(this, "ImsConference: properties=" + properties);
        setConnectionProperties(properties);
        /// @}
    }

    /**
     * Transfers capabilities from the conference host to the conference itself.
     *
     * @param conferenceCapabilities The current conference capabilities.
     * @param capabilities The new conference host capabilities.
     * @param isVideoConferencingSupported Whether video conferencing is supported.
     * @return The merged capabilities to be applied to the conference.
     */
    private int applyHostCapabilities(int conferenceCapabilities, int capabilities,
            boolean isVideoConferencingSupported) {

        conferenceCapabilities = changeBitmask(conferenceCapabilities,
                    Connection.CAPABILITY_SUPPORTS_VT_LOCAL_BIDIRECTIONAL,
                    can(capabilities, Connection.CAPABILITY_SUPPORTS_VT_LOCAL_BIDIRECTIONAL));

        if (isVideoConferencingSupported) {
            conferenceCapabilities = changeBitmask(conferenceCapabilities,
                    Connection.CAPABILITY_SUPPORTS_VT_REMOTE_BIDIRECTIONAL,
                    can(capabilities, Connection.CAPABILITY_SUPPORTS_VT_REMOTE_BIDIRECTIONAL));
            conferenceCapabilities = changeBitmask(conferenceCapabilities,
                    Connection.CAPABILITY_CAN_UPGRADE_TO_VIDEO,
                    can(capabilities, Connection.CAPABILITY_CAN_UPGRADE_TO_VIDEO));
        } else {
            // If video conferencing is not supported, explicitly turn off the remote video
            // capability and the ability to upgrade to video.
            Log.v(this, "applyHostCapabilities : video conferencing not supported");
            conferenceCapabilities = changeBitmask(conferenceCapabilities,
                    Connection.CAPABILITY_SUPPORTS_VT_REMOTE_BIDIRECTIONAL, false);
            conferenceCapabilities = changeBitmask(conferenceCapabilities,
                    Connection.CAPABILITY_CAN_UPGRADE_TO_VIDEO, false);
        }

        conferenceCapabilities = changeBitmask(conferenceCapabilities,
                Connection.CAPABILITY_CANNOT_DOWNGRADE_VIDEO_TO_AUDIO,
                can(capabilities, Connection.CAPABILITY_CANNOT_DOWNGRADE_VIDEO_TO_AUDIO));

        conferenceCapabilities = changeBitmask(conferenceCapabilities,
                    Connection.CAPABILITY_HOLD,
                    can(capabilities, Connection.CAPABILITY_HOLD));

        return conferenceCapabilities;
    }

    /**
     * Transfers properties from the conference host to the conference itself.
     *
     * @param conferenceProperties The current conference properties.
     * @param properties The new conference host properties.
     * @return The merged properties to be applied to the conference.
     */
    private int applyHostProperties(int conferenceProperties, int properties) {
        conferenceProperties = changeBitmask(conferenceProperties,
                Connection.PROPERTY_HIGH_DEF_AUDIO,
                can(properties, Connection.PROPERTY_HIGH_DEF_AUDIO));

        conferenceProperties = changeBitmask(conferenceProperties,
                Connection.PROPERTY_WIFI,
                can(properties, Connection.PROPERTY_WIFI));

        conferenceProperties = changeBitmask(conferenceProperties,
                Connection.PROPERTY_IS_EXTERNAL_CALL,
                can(properties, Connection.PROPERTY_IS_EXTERNAL_CALL));

        /// M: For VoLTE. @{
        conferenceProperties = changeBitmask(conferenceProperties,
                Connection.PROPERTY_VOLTE,
                can(properties, Connection.PROPERTY_VOLTE));
        /// @}

        return conferenceProperties;
    }

    /**
     * Not used by the IMS conference controller.
     *
     * @return {@code Null}.
     */
    @Override
    public android.telecom.Connection getPrimaryConnection() {
        return null;
    }

    /**
     * Returns VideoProvider of the conference. This can be null.
     *
     * @hide
     */
    @Override
    public VideoProvider getVideoProvider() {
        if (mConferenceHost != null) {
            return mConferenceHost.getVideoProvider();
        }
        return null;
    }

    /**
     * Returns video state of conference
     *
     * @hide
     */
    @Override
    public int getVideoState() {
        if (mConferenceHost != null) {
            return mConferenceHost.getVideoState();
        }
        return VideoProfile.STATE_AUDIO_ONLY;
    }

    /**
     * Invoked when the Conference and all its {@link Connection}s should be disconnected.
     * <p>
     * Hangs up the call via the conference host connection.  When the host connection has been
     * successfully disconnected, the {@link #mConferenceHostListener} listener receives an
     * {@code onDestroyed} event, which triggers the conference participant connections to be
     * disconnected.
     */
    @Override
    public void onDisconnect() {
        Log.v(this, "onDisconnect: hanging up conference host.");
        mHostCallAddress = null;
        mHostCallState = Connection.STATE_DISCONNECTED;
        if (mConferenceHost == null) {
            return;
        }

        Call call = mConferenceHost.getCall();
        if (call != null) {
            try {
                call.hangup();
            } catch (CallStateException e) {
                Log.e(this, e, "Exception thrown trying to hangup conference");
            }
        }
    }

    /**
     * Invoked when the specified {@link android.telecom.Connection} should be separated from the
     * conference call.
     * <p>
     * IMS does not support separating connections from the conference.
     *
     * @param connection The connection to separate.
     */
    @Override
    public void onSeparate(android.telecom.Connection connection) {
        Log.wtf(this, "Cannot separate connections from an IMS conference.");
    }

    /**
     * Invoked when the specified {@link android.telecom.Connection} should be merged into the
     * conference call.
     *
     * @param connection The {@code Connection} to merge.
     */
    @Override
    public void onMerge(android.telecom.Connection connection) {
        /// M: For VoLTE enhanced conference call. @{
        if (mIsDuringAddingParticipants) {
            toastWhenIsAddingParticipants();
            return;
        }
        /// @}

        /// M: ALPS02551190, Show toast if the conference is full @{
        if (getNumbOfParticipants() >= IMS_CONFERENCE_MAX_SIZE) {
            if (mConferenceHost != null && mConferenceHost.getPhone() != null &&
                    !removeToastOfConfMaxParticipants(mConferenceHost.getPhone().getContext())) {
                if (ignoreAddRequestToFullConference() || isShowFullToast()) {
                    toastWhenConferenceIsFull(mConferenceHost.getPhone().getContext());
                }
                if (ignoreAddRequestToFullConference()) {
                    Log.d(this, "Ignore the add request");
                    return;
                }
            }
        }
        /// @}

        try {
            Phone phone = mConferenceHost.getPhone();
            if (phone != null) {
                phone.conference();
            }
        } catch (CallStateException e) {
            Log.e(this, e, "Exception thrown trying to merge call into a conference");
        }
    }

    /**
     * Invoked when the conference should be put on hold.
     */
    @Override
    public void onHold() {
        /// M: For VoLTE enhanced conference call. @{
        if (mIsDuringAddingParticipants) {
            toastWhenIsAddingParticipants();
            return;
        }
        /// @}

        if (mConferenceHost == null) {
            return;
        }
        mConferenceHost.performHold();
    }

    /**
     * Invoked when the conference should be moved from hold to active.
     */
    @Override
    public void onUnhold() {
        /// M: For VoLTE enhanced conference call. @{
        if (mIsDuringAddingParticipants) {
            toastWhenIsAddingParticipants();
            return;
        }
        /// @}

        if (mConferenceHost == null) {
            return;
        }
        mConferenceHost.performUnhold();
    }

    /**
     * Invoked to play a DTMF tone.
     *
     * @param c A DTMF character.
     */
    @Override
    public void onPlayDtmfTone(char c) {
        if (mConferenceHost == null) {
            return;
        }
        mConferenceHost.onPlayDtmfTone(c);
    }

    /**
     * Invoked to stop playing a DTMF tone.
     */
    @Override
    public void onStopDtmfTone() {
        if (mConferenceHost == null) {
            return;
        }
        mConferenceHost.onStopDtmfTone();
    }

    /**
     * Handles the addition of connections to the {@link ImsConference}.  The
     * {@link ImsConferenceController} does not add connections to the conference.
     *
     * @param connection The newly added connection.
     */
    @Override
    public void onConnectionAdded(android.telecom.Connection connection) {
        // No-op
    }

    /**
     * Changes a bit-mask to add or remove a bit-field.
     *
     * @param bitmask The bit-mask.
     * @param bitfield The bit-field to change.
     * @param enabled Whether the bit-field should be set or removed.
     * @return The bit-mask with the bit-field changed.
     */
    private int changeBitmask(int bitmask, int bitfield, boolean enabled) {
        if (enabled) {
            return bitmask | bitfield;
        } else {
            return bitmask & ~bitfield;
        }
    }

    /**
     * Determines if this conference is hosted on the current device or the peer device.
     *
     * @return {@code true} if this conference is hosted on the current device, {@code false} if it
     *      is hosted on the peer device.
     */
    public boolean isConferenceHost() {
        if (mConferenceHost == null) {
            return false;
        }
        com.android.internal.telephony.Connection originalConnection =
                mConferenceHost.getOriginalConnection();

        return originalConnection != null && originalConnection.isMultiparty() &&
                originalConnection.isConferenceHost();
    }

    /**
     * Updates the manage conference capability of the conference.  Where there are one or more
     * conference event package participants, the conference management is permitted.  Where there
     * are no conference event package participants, conference management is not permitted.
     * <p>
     * Note: We add and remove {@link Connection#CAPABILITY_CONFERENCE_HAS_NO_CHILDREN} to ensure
     * that the conference is represented appropriately on Bluetooth devices.
     */
    private void updateManageConference() {
        boolean couldManageConference = can(Connection.CAPABILITY_MANAGE_CONFERENCE);
        boolean canManageConference = !mConferenceParticipantConnections.isEmpty();
        Log.v(this, "updateManageConference was :%s is:%s", couldManageConference ? "Y" : "N",
                canManageConference ? "Y" : "N");

        if (couldManageConference != canManageConference) {
            int capabilities = getConnectionCapabilities();

            if (canManageConference) {
                capabilities |= Connection.CAPABILITY_MANAGE_CONFERENCE;
                capabilities &= ~Connection.CAPABILITY_CONFERENCE_HAS_NO_CHILDREN;
            } else {
                capabilities &= ~Connection.CAPABILITY_MANAGE_CONFERENCE;
                capabilities |= Connection.CAPABILITY_CONFERENCE_HAS_NO_CHILDREN;
            }

            setConnectionCapabilities(capabilities);
        }
    }

    /**
     * Sets the connection hosting the conference and registers for callbacks.
     *
     * @param conferenceHost The connection hosting the conference.
     */
    private void setConferenceHost(TelephonyConnection conferenceHost) {
        if (Log.VERBOSE) {
            Log.v(this, "setConferenceHost " + conferenceHost);
        }

        mConferenceHost = conferenceHost;

        // Attempt to get the conference host's address (e.g. the host's own phone number).
        // We need to look at the default phone for the ImsPhone when creating the phone account
        // for the
        if (mConferenceHost.getPhone() != null &&
                mConferenceHost.getPhone().getPhoneType() == PhoneConstants.PHONE_TYPE_IMS) {
            // Look up the conference host's address; we need this later for filtering out the
            // conference host in conference event package data.
            Phone imsPhone = mConferenceHost.getPhone();
            mConferenceHostPhoneAccountHandle =
                    PhoneUtils.makePstnPhoneAccountHandle(imsPhone.getDefaultPhone());
            Uri hostAddress = TelecomAccountRegistry.getInstance(mTelephonyConnectionService)
                    .getAddress(mConferenceHostPhoneAccountHandle);

            ArrayList<Uri> hostAddresses = new ArrayList<>();

            // add address from TelecomAccountRegistry
            if (hostAddress != null) {
                hostAddresses.add(hostAddress);
            }

            // add addresses from phone
            if (imsPhone.getCurrentSubscriberUris() != null) {
                hostAddresses.addAll(
                        new ArrayList<>(Arrays.asList(imsPhone.getCurrentSubscriberUris())));
            }

            mConferenceHostAddress = new Uri[hostAddresses.size()];
            mConferenceHostAddress = hostAddresses.toArray(mConferenceHostAddress);
        }

        mConferenceHost.addConnectionListener(mConferenceHostListener);
        mConferenceHost.addTelephonyConnectionListener(mTelephonyConnectionListener);
        setConnectionCapabilities(applyHostCapabilities(getConnectionCapabilities(),
                mConferenceHost.getConnectionCapabilities(),
                mConferenceHost.isCarrierVideoConferencingSupported()));
        setConnectionProperties(applyHostProperties(getConnectionProperties(),
                mConferenceHost.getConnectionProperties()));

        setState(mConferenceHost.getState());
        updateStatusHints();
        /// M: For VT provider id, clone the extras from host connection. @{
        if (conferenceHost != null && conferenceHost.getOriginalConnection() != null) {
            Bundle extras = conferenceHost.getOriginalConnection().getConnectionExtras();
            Log.d(this, "set extras" + extras);
            if (extras != null) {
                putExtras(extras);
            }
        }
        /// @}
    }

    /**
     * Handles state changes for conference participant(s).  The participants data passed in
     *
     * @param parent The connection which was notified of the conference participant.
     * @param participants The conference participant information.
     */
    private void handleConferenceParticipantsUpdate(
            TelephonyConnection parent, List<ConferenceParticipant> participants) {

        if (participants == null) {
            return;
        }

        Log.i(this, "handleConferenceParticipantsUpdate: size=%d", participants.size());

        // Perform the update in a synchronized manner.  It is possible for the IMS framework to
        // trigger two onConferenceParticipantsChanged callbacks in quick succession.  If the first
        // update adds new participants, and the second does something like update the status of one
        // of the participants, we can get into a situation where the participant is added twice.
        synchronized (mUpdateSyncRoot) {
            boolean newParticipantsAdded = false;
            boolean oldParticipantsRemoved = false;
            ArrayList<ConferenceParticipant> newParticipants = new ArrayList<>(participants.size());
            HashSet<Pair<Uri,Uri>> participantUserEntities = new HashSet<>(participants.size());

            // Add any new participants and update existing.
            boolean ignoreFirst = true;
            for (ConferenceParticipant participant : participants) {
                Pair<Uri,Uri> userEntity = new Pair<>(participant.getHandle(),
                    participant.getEndpoint());

                /// M: ignore the first one, who is host. @{
                if (ignoreFirst) {
                    Log.w(this, "ignore first" + participant.getHandle());
                    ignoreFirst = false;
                    mHostCallAddress = participant.getHandle();
                    continue;
                }
                /// @}

                participantUserEntities.add(userEntity);
                if (!mConferenceParticipantConnections.containsKey(userEntity)) {
                    // Some carriers will also include the conference host in the CEP.  We will
                    // filter that out here.
                    if (!isParticipantHost(mConferenceHostAddress, participant.getHandle())) {
                        createConferenceParticipantConnection(parent, participant);
                        newParticipants.add(participant);
                        newParticipantsAdded = true;
                    }
                } else {
                    /// M: @{
                    Log.w(this, "update existing participant: " + userEntity);
                    /// @}
                    ConferenceParticipantConnection connection =
                            mConferenceParticipantConnections.get(userEntity);
                    Log.i(this, "handleConferenceParticipantsUpdate: updateState, participant = %s",
                            participant);
                    connection.updateState(participant.getState());
                }
            }

            // Set state of new participants.
            if (newParticipantsAdded) {
                // Set the state of the new participants at once and add to the conference
                for (ConferenceParticipant newParticipant : newParticipants) {
                    /// M: @{
                    Log.w(this, "add new participant: " + newParticipant.getHandle().toString());
                    /// @}
                    ConferenceParticipantConnection connection =
                            mConferenceParticipantConnections.get(new Pair<>(
                                    newParticipant.getHandle(),
                                    newParticipant.getEndpoint()));
                    connection.updateState(newParticipant.getState());
                }
            }

            // Finally, remove any participants from the conference that no longer exist in the
            // conference event package data.
            Iterator<Map.Entry<Pair<Uri, Uri>, ConferenceParticipantConnection>> entryIterator =
                    mConferenceParticipantConnections.entrySet().iterator();
            while (entryIterator.hasNext()) {
                Map.Entry<Pair<Uri, Uri>, ConferenceParticipantConnection> entry =
                        entryIterator.next();

                if (!participantUserEntities.contains(entry.getKey())) {
                    /// M: for debug messages. @{
                    Log.w(this, "remove existing participant: " + entry.getKey());
                    /// @}
                    ConferenceParticipantConnection participant = entry.getValue();
                    participant.setDisconnected(new DisconnectCause(DisconnectCause.CANCELED));
                    participant.removeConnectionListener(mParticipantListener);
                    mTelephonyConnectionService.removeConnection(participant);
                    removeConnection(participant);
                    entryIterator.remove();
                    oldParticipantsRemoved = true;
                }
            }

            // If new participants were added or old ones were removed, we need to ensure the state
            // of the manage conference capability is updated.
            if (newParticipantsAdded || oldParticipantsRemoved) {
                updateManageConference();
            }
        }
    }

    /**
     * Creates a new {@link ConferenceParticipantConnection} to represent a
     * {@link ConferenceParticipant}.
     * <p>
     * The new connection is added to the conference controller and connection service.
     *
     * @param parent The connection which was notified of the participant change (e.g. the
     *                         parent connection).
     * @param participant The conference participant information.
     */
    private void createConferenceParticipantConnection(
            TelephonyConnection parent, ConferenceParticipant participant) {

        // Create and add the new connection in holding state so that it does not become the
        // active call.
        ConferenceParticipantConnection connection = new ConferenceParticipantConnection(
                parent.getOriginalConnection(), participant);
        connection.addConnectionListener(mParticipantListener);
        connection.setConnectTimeMillis(parent.getConnectTimeMillis());

        Log.i(this, "createConferenceParticipantConnection: participant=%s, connection=%s",
                participant, connection);

        synchronized(mUpdateSyncRoot) {
            mConferenceParticipantConnections.put(new Pair<>(participant.getHandle(),
                    participant.getEndpoint()), connection);
        }
        mTelephonyConnectionService.addExistingConnection(mConferenceHostPhoneAccountHandle,
                connection);
        addConnection(connection);
    }

    /**
     * Removes a conference participant from the conference.
     *
     * @param participant The participant to remove.
     */
    private void removeConferenceParticipant(ConferenceParticipantConnection participant) {
        Log.i(this, "removeConferenceParticipant: %s", participant);

        participant.removeConnectionListener(mParticipantListener);
        synchronized(mUpdateSyncRoot) {
            mConferenceParticipantConnections.remove(participant.getUserEntity());
        }
        mTelephonyConnectionService.removeConnection(participant);
    }

    /**
     * Disconnects all conference participants from the conference.
     */
    private void disconnectConferenceParticipants() {
        Log.v(this, "disconnectConferenceParticipants");

        synchronized(mUpdateSyncRoot) {
            for (ConferenceParticipantConnection connection :
                    mConferenceParticipantConnections.values()) {

                connection.removeConnectionListener(mParticipantListener);
                // Mark disconnect cause as cancelled to ensure that the call is not logged in the
                // call log.
                connection.setDisconnected(new DisconnectCause(DisconnectCause.CANCELED));
                mTelephonyConnectionService.removeConnection(connection);
                connection.destroy();
            }
            mConferenceParticipantConnections.clear();
        }
    }

    /**
     * Determines if the passed in participant handle is the same as the conference host's handle.
     * Starts with a simple equality check.  However, the handles from a conference event package
     * will be a SIP uri, so we need to pull that apart to look for the participant's phone number.
     *
     * @param hostHandles The handle(s) of the connection hosting the conference.
     * @param handle The handle of the conference participant.
     * @return {@code true} if the host's handle matches the participant's handle, {@code false}
     *      otherwise.
     */
    private boolean isParticipantHost(Uri[] hostHandles, Uri handle) {
        // If there is no host handle or no participant handle, bail early.
        if (hostHandles == null || hostHandles.length == 0 || handle == null) {
            Log.v(this, "isParticipantHost(N) : host or participant uri null");
            return false;
        }

        // Conference event package participants are identified using SIP URIs (see RFC3261).
        // A valid SIP uri has the format: sip:user:password@host:port;uri-parameters?headers
        // Per RFC3261, the "user" can be a telephone number.
        // For example: sip:1650555121;phone-context=blah.com@host.com
        // In this case, the phone number is in the user field of the URI, and the parameters can be
        // ignored.
        //
        // A SIP URI can also specify a phone number in a format similar to:
        // sip:+1-212-555-1212@something.com;user=phone
        // In this case, the phone number is again in user field and the parameters can be ignored.
        // We can get the user field in these instances by splitting the string on the @, ;, or :
        // and looking at the first found item.

        String number = handle.getSchemeSpecificPart();
        String numberParts[] = number.split("[@;:]");

        if (numberParts.length == 0) {
            Log.v(this, "isParticipantHost(N) : no number in participant handle");
            return false;
        }
        number = numberParts[0];

        for (Uri hostHandle : hostHandles) {
            if (hostHandle == null) {
                continue;
            }
            // The host number will be a tel: uri.  Per RFC3966, the part after tel: is the phone
            // number.
            String hostNumber = hostHandle.getSchemeSpecificPart();

            // Use a loose comparison of the phone numbers.  This ensures that numbers that differ
            // by special characters are counted as equal.
            // E.g. +16505551212 would be the same as 16505551212
            boolean isHost = PhoneNumberUtils.compare(hostNumber, number);

            Log.v(this, "isParticipantHost(%s) : host: %s, participant %s", (isHost ? "Y" : "N"),
                    Log.pii(hostNumber), Log.pii(number));

            if (isHost) {
                return true;
            }
        }
        return false;
    }

    /**
     * Handles a change in the original connection backing the conference host connection.  This can
     * happen if an SRVCC event occurs on the original IMS connection, requiring a fallback to
     * GSM or CDMA.
     * <p>
     * If this happens, we will add the conference host connection to telecom and tear down the
     * conference.
     */
    private void handleOriginalConnectionChange() {
        if (mConferenceHost == null) {
            Log.w(this, "handleOriginalConnectionChange; conference host missing.");
            return;
        }

        com.android.internal.telephony.Connection originalConnection =
                mConferenceHost.getOriginalConnection();

        if (originalConnection != null &&
                originalConnection.getPhoneType() != PhoneConstants.PHONE_TYPE_IMS) {
            Log.i(this,
                    "handleOriginalConnectionChange : handover from IMS connection to " +
                            "new connection: %s", originalConnection);

            PhoneAccountHandle phoneAccountHandle = null;
            if (mConferenceHost.getPhone() != null) {
                if (mConferenceHost.getPhone().getPhoneType() == PhoneConstants.PHONE_TYPE_IMS) {
                    Phone imsPhone = mConferenceHost.getPhone();
                    // The phone account handle for an ImsPhone is based on the default phone (ie
                    // the base GSM or CDMA phone, not on the ImsPhone itself).
                    phoneAccountHandle =
                            PhoneUtils.makePstnPhoneAccountHandle(imsPhone.getDefaultPhone());
                } else {
                    // In the case of SRVCC, we still need a phone account, so use the top level
                    // phone to create a phone account.
                    phoneAccountHandle = PhoneUtils.makePstnPhoneAccountHandle(
                            mConferenceHost.getPhone());
                }
            }

            if (mConferenceHost.getPhone().getPhoneType() == PhoneConstants.PHONE_TYPE_GSM) {
                Log.i(this,"handleOriginalConnectionChange : SRVCC to GSM");
                /// M: CC: Vzw/CTVolte ECC @{
                //GsmConnection c = new GsmConnection(originalConnection, getTelecomCallId());
                GsmCdmaConnection c = new GsmCdmaConnection(PhoneConstants.PHONE_TYPE_GSM,
                        originalConnection, getTelecomCallId(), null, false, false);
                // This is a newly created conference connection as a result of SRVCC
                c.setConferenceSupported(true);
                c.addCapability(Connection.CAPABILITY_CONFERENCE_HAS_NO_CHILDREN);
                c.setConnectionProperties(
                        c.getConnectionProperties() | Connection.PROPERTY_IS_DOWNGRADED_CONFERENCE);
                c.updateState();
                // Copy the connect time from the conferenceHost
                c.setConnectTimeMillis(mConferenceHost.getConnectTimeMillis());
                mTelephonyConnectionService.addExistingConnection(phoneAccountHandle, c);
                mTelephonyConnectionService.addConnectionToConferenceController(c);
            } // CDMA case not applicable for SRVCC
            mConferenceHost.removeConnectionListener(mConferenceHostListener);
            mConferenceHost.removeTelephonyConnectionListener(mTelephonyConnectionListener);
            mConferenceHost = null;
            setDisconnected(new DisconnectCause(DisconnectCause.OTHER));
            disconnectConferenceParticipants();
            destroy();
        }

        updateStatusHints();
    }

    /**
     * Changes the state of the Ims conference.
     *
     * @param state the new state.
     */
    public void setState(int state) {
        Log.v(this, "setState %s", Connection.stateToString(state));

        switch (state) {
            case Connection.STATE_INITIALIZING:
            case Connection.STATE_NEW:
            //case Connection.STATE_RINGING:
                // No-op -- not applicable.
                break;
            /// M: For enhanced conference. @{
            case Connection.STATE_RINGING:
                setRinging();
                break;
                /// @}
            case Connection.STATE_DIALING:
                setDialing();
                break;
            case Connection.STATE_DISCONNECTED:
                DisconnectCause disconnectCause;
                /// M: ALPS02904764, fix the timing issue for conference start and disconnect
                /// in a short time. The disconnect will set the original connection as null. @{
                //if (mConferenceHost == null) {
                if (mConferenceHost == null || mConferenceHost.getOriginalConnection() == null) {
                /// @}
                    disconnectCause = new DisconnectCause(DisconnectCause.CANCELED);
                } else {
                    disconnectCause = DisconnectCauseUtil.toTelecomDisconnectCause(
                            mConferenceHost.getOriginalConnection().getDisconnectCause());
                }
                setDisconnected(disconnectCause);
                disconnectConferenceParticipants();
                destroy();
                break;
            case Connection.STATE_ACTIVE:
                setActive();
                break;
            case Connection.STATE_HOLDING:
                setOnHold();
                break;
        }
    }

    private void updateStatusHints() {
        if (mConferenceHost == null) {
            setStatusHints(null);
            return;
        }

        if (mConferenceHost.isWifi()) {
            Phone phone = mConferenceHost.getPhone();
            if (phone != null) {
                Context context = phone.getContext();
                setStatusHints(new StatusHints(
                        context.getString(R.string.status_hint_label_wifi_call),
                        Icon.createWithResource(
                                context.getResources(),
                                R.drawable.ic_signal_wifi_4_bar_24dp),
                        null /* extras */));
            }
        } else {
            setStatusHints(null);
        }
    }

    /**
     * Builds a string representation of the {@link ImsConference}.
     *
     * @return String representing the conference.
     */
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[ImsConference objId:");
        sb.append(System.identityHashCode(this));
        sb.append(" telecomCallID:");
        sb.append(getTelecomCallId());
        sb.append(" state:");
        sb.append(Connection.stateToString(getState()));
        sb.append("capability:");
        sb.append(Connection.capabilitiesToString(getConnectionCapabilities()));
        sb.append(" hostConnection:");
        sb.append(mConferenceHost);
        sb.append(" participants:");
        sb.append(mConferenceParticipantConnections.size());
        sb.append("]");
        return sb.toString();
    }

    private boolean canHoldImsCalls() {
        PersistableBundle b = getCarrierConfig();
        // Return true if the CarrierConfig is unavailable
        return b == null || b.getBoolean(CarrierConfigManager.KEY_ALLOW_HOLD_IN_IMS_CALL_BOOL);
    }

    private PersistableBundle getCarrierConfig() {
        if (mConferenceHost == null) {
            return null;
        }

        Phone phone = mConferenceHost.getPhone();
        if (phone == null) {
            return null;
        }
        return PhoneGlobals.getInstance().getCarrierConfigForSubId(phone.getSubId());
    }

    /// M: @{
    int getNumbOfParticipants() {
        return mConferenceParticipantConnections.size();
    }

    Phone getPhone() {
        if (mConferenceHost == null) {
            return null;
        }
        return mConferenceHost.getPhone();
    }
    /// @}

    /// M: For VoLTE enhanced conference call. @{
    @Override
    public void onInviteConferenceParticipants(List<String> numbers) {
        if (mConferenceHost == null) {
            return;
        }

        // Judge whether the invited number has already existed in the conference
        Iterator<String> iter = numbers.iterator();
        while (iter.hasNext()) {
            if (hasExistedInConference(iter.next())) {
                iter.remove();
            }
        }
        if (numbers.size() == 0) {
            return;
        }

        ///  M: ALPS02209724. Show toast if the conference is full. @{
        if (getNumbOfParticipants() + numbers.size() > IMS_CONFERENCE_MAX_SIZE) {
            if (mConferenceHost.getPhone() != null &&
                    !removeToastOfConfMaxParticipants(mConferenceHost.getPhone().getContext())) {
                if (ignoreAddRequestToFullConference() || isShowFullToast()) {
                    toastWhenConferenceIsFull(mConferenceHost.getPhone().getContext());
                }
                if (ignoreAddRequestToFullConference()) {
                    Log.d(this, "Ignore the add request");
                    return;
                }
            }
        }
        /// @}

        mConferenceHost.performInviteConferenceParticipants(numbers);
        mIsDuringAddingParticipants = true;
    }

    private boolean hasExistedInConference(String number) {
        Iterator<Map.Entry<Pair<Uri, Uri>, ConferenceParticipantConnection>> entryIterator =
            mConferenceParticipantConnections.entrySet().iterator();
        while (entryIterator.hasNext()) {
            Map.Entry<Pair<Uri, Uri>, ConferenceParticipantConnection> entry =
                entryIterator.next();
            Pair<Uri, Uri> key = entry.getKey();
            Uri userEntity =  key.first;
            String participantNumber = userEntity.getSchemeSpecificPart();
            Log.w(this, "The invited number is %s and participant number is %s",
                    number, participantNumber);
            if (PhoneNumberUtils.compare(number, participantNumber)) {
                Log.v(this, "The invited number has already existed in the conference");
                return true;
            }
        }

        if (mHostCallAddress != null && mHostCallAddress.getSchemeSpecificPart() != null) {
            if (PhoneNumberUtils.compare(number, mHostCallAddress.getSchemeSpecificPart())) {
                Log.v(this, "The invited number is the host connection address.");
                return true;
            }
        }

        return false;
    }

    /**
     * Popup toast when user performs hold/unhold conference if adding
     * participants has not been yet completed.
     */
    private void toastWhenIsAddingParticipants() {
        if (mConferenceHost == null) {
            return;
        }

        Context context;
        Phone phone = mConferenceHost.getPhone();
        if (phone != null) {
            context = phone.getContext();
            Toast.makeText(context,
                    context.getString(R.string.volte_is_adding_participants), Toast.LENGTH_SHORT).
                        show();
        }
    }
    /// @}

    /// M: ALPS02209724. Popup toast when conference reach the maximum participants. @{
    /**
     * For below cases, PhoneApp popups the toast to hint user the conference is full.
     * case-1: when merging the 6th call into a conference.
     * case-2: when inviting participant to cause conference has more than 6 participants.
     * case-3: when dialing a conference with over 6 participants.
     *
     * @param context the context used to show toast.
     * @hide
     */
    static void toastWhenConferenceIsFull(Context context) {
        if (context == null) {
            return;
        }

        Toast.makeText(context,
                context.getString(R.string.volte_conf_member_reach_max), Toast.LENGTH_SHORT)
                .show();
    }

    static boolean isShowFullToast() {
        String operator = SystemProperties.get("persist.operator.optr", "OM");
        return operator.equals("OP01");
    }
    /// @}

    /// M: For conference SRVCC. @{
    private void handleConferenceSRVCC(
            ArrayList<com.android.internal.telephony.Connection> radioConnections) {
        Log.w(this, "handleConferenceSRVCC");

        if (mConferenceHost == null) {
            Log.w(this, "onConferenceConnectionsConfigured: conference host missing.");
            return;
        }

        if (radioConnections == null || radioConnections.size() < 2) {
            Log.w(this, "onConferenceConnectionsConfigured: failed at radioConnections.");
            return;
        }

        disconnectConferenceParticipants();
        mTelephonyConnectionService.performImsConferenceSRVCC(this, radioConnections,
                getTelecomCallId());

        mConferenceHost.removeConnectionListener(mConferenceHostListener);
        mConferenceHost.removeTelephonyConnectionListener(mTelephonyConnectionListener);
        mConferenceHost = null;
        destroy();
    }
    /// @}

    /// M : For WFC, @{
    /*private int applyWifiCapabiliities(int capabilities) {
        capabilities = changeBitmask(capabilities, Connection.PROPERTY_WIFI,
                mConferenceHost.isWifi());
        return capabilities;
    }*/
    /// @}

    /// M: ALPS02136977. Prints debug logs for telephony. @{
    /**
     * Logs unified debug log messages, for "Notify".
     * Format: [category][Module][Notify][Action][call-number][local-call-ID] Msg. String
     *
     * @param action the action name. (e.q. Dial, Hold, MT, Onhold, etc.)
     * @param msg the optional messages
     * @hide
     */
    private void logDebugMsgWithNotifyFormat(String action, String msg) {
        FormattedLog formattedLog = new FormattedLog.Builder()
                .setCategory("CC")
                .setServiceName("Telephony")
                .setOpType(FormattedLog.OpType.NOTIFY)
                .setActionName(action)
                .setCallNumber("conferenceCall")
                .setCallId(Integer.toString(System.identityHashCode(this)))
                .setExtraMessage(msg)
                .buildDebugMsg();

        if (formattedLog != null) {
            if (!SENLOG || TELDBG) {
                Log.w(this, formattedLog.toString());
            }
        }
    }

    @Override
    protected FormattedLog.Builder configDumpLogBuilder(FormattedLog.Builder builder) {
        if (builder == null) {
            return null;
        }

        super.configDumpLogBuilder(builder);
        return builder.setServiceName("Telephony").setStatusInfo("type", "ims");
    }
    /// @}

    /// M: ALPS02611493, Implementation hangup all @{
    /**
     * To hang up all connections.
     * @hide
     */
    @Override
    public void onHangupAll() {
        Log.w(this, "onHangupAll()");
        if (mConferenceHost == null) {
            return;
        }
        try {
            Phone phone = mConferenceHost.getPhone();
            if (phone != null) {
                phone.hangupAll();
            } else {
                Log.w(this, "Attempting to hangupAll a conference without backing phone.");
            }
        } catch (CallStateException e) {
            Log.e(this, e, "Call to phone.hangupAll() failed with exception");
        }
    }
    /// @}

    /// M: Customize for specific operator and location.
    private boolean removeToastOfConfMaxParticipants(Context context) {
        Log.d(this, "removeToastOfConfMaxParticipants");
        boolean removeToast = false;
        PersistableBundle b = null;
        int phoneId = RadioCapabilitySwitchUtil.getMainCapabilityPhoneId();
        int subId = SubscriptionManager.getSubIdUsingPhoneId(phoneId);
        CarrierConfigManager configMgr = (CarrierConfigManager) context
                .getSystemService(Context.CARRIER_CONFIG_SERVICE);
        if (configMgr != null) {
            b = configMgr.getConfigForSubId(subId);
            if (b != null) {
                removeToast = b.getBoolean(CarrierConfigManager
                        .KEY_REMOVE_CONFERENCE_TOAST_BOOL);
            }
        }
        Log.d(this, "removeToast: %s" + removeToast);
        return removeToast;
    }

    private boolean ignoreAddRequestToFullConference() {
        PersistableBundle b = getCarrierConfig();
        boolean ret = false;
        if (b != null) {
            ret = b.getBoolean(CarrierConfigManager.KEY_IMS_NO_CONF_REQ_AFTER_MAX_CONNECTION_BOOL);
        }
        // Return true if the CarrierConfig is unavailable
        return ret;
    }

    /// M: get host address @{
    @Override
    public Uri getHostAddress() {
        String number;
        if (mConferenceHost == null || mConferenceHost.getOriginalConnection() == null) {
            number = null;
        } else {
            number = mConferenceHost.getOriginalConnection().getAddress();
        }
        Uri address = getAddressFromNumber(number);
        Log.d(this, "number = " + number + "address = " + address);
        return address;
    }

    private static Uri getAddressFromNumber(String number) {
        // Address can be null for blocked calls.
        if (number == null) {
            number = "";
        }
        return Uri.fromParts(PhoneAccount.SCHEME_TEL, number, null);
    }
    /// @}
}
