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

package com.android.services.telephony;

import android.telecom.Conference;
import android.telecom.Connection;
import android.telecom.PhoneAccountHandle;

import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.Phone;

import java.util.List;

/**
 * TelephonyConnection-based conference call for GSM conferences and IMS conferences (which may
 * be either GSM-based or CDMA-based).
 */
public class TelephonyConference extends Conference {
    private static final String TAG = "TelephonyConf";

    public TelephonyConference(PhoneAccountHandle phoneAccount) {
        super(phoneAccount);
        setConnectionCapabilities(
                Connection.CAPABILITY_SUPPORT_HOLD |
                Connection.CAPABILITY_HOLD |
                Connection.CAPABILITY_MUTE |
                Connection.CAPABILITY_MANAGE_CONFERENCE);
        setActive();
    }

    /**
     * Invoked when the Conference and all it's {@link Connection}s should be disconnected.
     */
    @Override
    public void onDisconnect() {
        for (Connection connection : getConnections()) {
            if (disconnectCall(connection)) {
                break;
            }
        }
    }

    /**
     * Disconnect the underlying Telephony Call for a connection.
     *
     * @param connection The connection.
     * @return {@code True} if the call was disconnected.
     */
    private boolean disconnectCall(Connection connection) {
        Call call = getMultipartyCallForConnection(connection, "onDisconnect");
        if (call != null) {
            Log.d(this, "Found multiparty call to hangup for conference.");
            try {
                call.hangup();
                return true;
            } catch (CallStateException e) {
                Log.e(this, e, "Exception thrown trying to hangup conference");
            }
        }
        return false;
    }

    /**
     * Invoked when the specified {@link Connection} should be separated from the conference call.
     *
     * @param connection The connection to separate.
     */
    @Override
    public void onSeparate(Connection connection) {
        com.android.internal.telephony.Connection radioConnection =
                getOriginalConnection(connection);
        try {
            radioConnection.separate();
        } catch (CallStateException e) {
            Log.e(this, e, "Exception thrown trying to separate a conference call");
        }
    }

    @Override
    public void onMerge(Connection connection) {
        try {
            Phone phone = ((TelephonyConnection) connection).getPhone();
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
        final TelephonyConnection connection = getFirstConnection();
        if (connection != null) {
            connection.performHold();
        }
    }

    /// M: CC: For DSDS/DSDA Two-action operation @{
    /**
     * Invoked when the Conference and all it's {@link Connection}s should be disconnected,
     * with pending call action, answer?
     * @param pendingCallAction The pending call action.
     */
    @Override
    public void onDisconnect(String pendingCallAction) {
        for (Connection connection : getConnections()) {
            if (disconnectCall(connection, pendingCallAction)) {
                break;
            }
        }
    }

    /**
     * Disconnect the underlying Telephony Call for a connection.
     * with pending call action, answer?
     *
     * @param connection The connection.
     * @param pendingCallAction The pending call action.
     * @return {@code True} if the call was disconnected.
     */
    private boolean disconnectCall(Connection connection, String pendingCallAction) {
        Call call = getMultipartyCallForConnection(connection, "onDisconnect");
        if (call != null) {
            log("Found multiparty call to hangup for conference, with pending action");
            /// M: CC: Hangup Conference special handling @{
            // To avoid hangupForegroundResumeBackground() is invoked by hangup(GsmCall),
            // hangup all connections in a conference one by one via conn.hangup()
            // [ALPS02408504] [ALPS01814074]
            //
            // To avoid hangup a disconnected conn by checking conn state first [ALPS01941738]
            // FIXME: mConnections in GsmCall is NOT removed when a conn in a conf is disconnected.
            log("pendingCallAction = " + pendingCallAction);
            if ("answer".equals(pendingCallAction) || "unhold".equals(pendingCallAction)) {
                for (com.android.internal.telephony.Connection conn: call.getConnections()) {
                    if (conn != null && conn.isAlive()) {
                        try {
                            conn.hangup();
                        } catch (CallStateException e) {
                            Log.e(TAG, e, "Exception thrown trying to hangup conference member");
                            return false;
                        }
                    }
                }
                return true;
            } else {
                try {
                    call.hangup();
                    return true;
                } catch (CallStateException e) {
                    Log.e(TAG, e, "Exception thrown trying to hangup conference");
                }
            }
            /// @}
        }
        return false;
    }
    /// @}

    /**
     * Invoked when the conference should be moved from hold to active.
     */
    @Override
    public void onUnhold() {
        final TelephonyConnection connection = getFirstConnection();
        if (connection != null) {
            connection.performUnhold();
        }
    }

    @Override
    public void onPlayDtmfTone(char c) {
        final TelephonyConnection connection = getFirstConnection();
        if (connection != null) {
            connection.onPlayDtmfTone(c);
        }
    }

    @Override
    public void onStopDtmfTone() {
        final TelephonyConnection connection = getFirstConnection();
        if (connection != null) {
            connection.onStopDtmfTone();
        }
    }

    @Override
    public void onConnectionAdded(Connection connection) {
        // If the conference was an IMS connection currently or before, disable MANAGE_CONFERENCE
        // as the default behavior. If there is a conference event package, this may be overridden.
        // If a conference event package was received, do not attempt to remove manage conference.
        if (connection instanceof TelephonyConnection &&
                ((TelephonyConnection) connection).wasImsConnection()) {
            removeCapability(Connection.CAPABILITY_MANAGE_CONFERENCE);
        }

        /// M: CC: For Telephonyconference, add connectTime (sync with ImsConference behavior) @{
        com.android.internal.telephony.Connection originalConnection =
                getOriginalConnection(connection);
        if (originalConnection != null) {
            setConnectTimeMillis(originalConnection.getCall().getEarliestConnectTime());
        }
        /// @}
    }

    /// M: CC: HangupAll for FTA 31.4.4.2 @{
    /**
     * Invoked when the Conference and all it's {@link Connection}s should be disconnected.
     * @hide
     */
    @Override
    public void onHangupAll() {
        final TelephonyConnection connection = getFirstConnection();
        if (connection != null) {
            try {
                Phone phone = connection.getPhone();
                if (phone != null) {
                    phone.hangupAll();
                } else {
                    Log.w(TAG, "Attempting to hangupAll a connection without backing phone.");
                }
            } catch (CallStateException e) {
                Log.e(TAG, e, "Exception thrown trying to hangupAll a conference");
            }
        }
    }
    /// @}

    @Override
    public Connection getPrimaryConnection() {

        List<Connection> connections = getConnections();
        if (connections == null || connections.isEmpty()) {
            return null;
        }

        // Default to the first connection.
        Connection primaryConnection = connections.get(0);

        // Otherwise look for a connection where the radio connection states it is multiparty.
        for (Connection connection : connections) {
            com.android.internal.telephony.Connection radioConnection =
                    getOriginalConnection(connection);

            if (radioConnection != null && radioConnection.isMultiparty()) {
                primaryConnection = connection;
                break;
            }
        }

        return primaryConnection;
    }

    private Call getMultipartyCallForConnection(Connection connection, String tag) {
        com.android.internal.telephony.Connection radioConnection =
                getOriginalConnection(connection);
        if (radioConnection != null) {
            Call call = radioConnection.getCall();
            if (call != null && call.isMultiparty()) {
                return call;
            }
        }
        return null;
    }

    protected com.android.internal.telephony.Connection getOriginalConnection(
            Connection connection) {

        if (connection instanceof TelephonyConnection) {
            return ((TelephonyConnection) connection).getOriginalConnection();
        } else {
            return null;
        }
    }

    private TelephonyConnection getFirstConnection() {
        final List<Connection> connections = getConnections();
        if (connections.isEmpty()) {
            return null;
        }
        return (TelephonyConnection) connections.get(0);
    }

    private void log(String s) {
        Log.d(TAG, s);
    }
 }
