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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import android.net.Uri;
import android.telecom.Conference;
import android.telecom.ConferenceParticipant;
import android.telecom.Conferenceable;
import android.telecom.Connection;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccountHandle;
import com.android.phone.PhoneUtils;

import com.android.internal.telephony.Call;
/// M: for SRVCC. @{
import com.android.internal.telephony.CallStateException;
/// @}

/**
 * Maintains a list of all the known TelephonyConnections connections and controls GSM and
 * default IMS conference call behavior. This functionality is characterized by the support of
 * two top-level calls, in contrast to a CDMA conference call which automatically starts a
 * conference when there are two calls.
 */
final class TelephonyConferenceController {
    private static final int TELEPHONY_CONFERENCE_MAX_SIZE = 5;
    private static final String TAG = "TeleConfCtrler";

    private final Connection.Listener mConnectionListener = new Connection.Listener() {
        @Override
        public void onStateChanged(Connection c, int state) {
            Log.v(this, "onStateChange triggered in Conf Controller : connection = "+ c
                 + " state = " + state);
            recalculate();
        }

        /** ${inheritDoc} */
        @Override
        public void onDisconnected(Connection c, DisconnectCause disconnectCause) {
            recalculate();
        }

        @Override
        public void onDestroyed(Connection connection) {
            remove(connection);
        }
    };

    /** The known connections. */
    private final List<TelephonyConnection> mTelephonyConnections = new ArrayList<>();

    private final TelephonyConnectionService mConnectionService;
    private boolean mTriggerRecalculate = false;

    public TelephonyConferenceController(TelephonyConnectionService connectionService) {
        mConnectionService = connectionService;
    }

    /** The TelephonyConference connection object. */
    private TelephonyConference mTelephonyConference;

    /// M: for Ims Conference SRVCC @{
    private TelephonyConference mHandoverTelephonyConference = null;
    /// @}

    boolean shouldRecalculate() {
        Log.d(this, "shouldRecalculate is " + mTriggerRecalculate);
        return mTriggerRecalculate;
    }

    void add(TelephonyConnection connection) {
        if (mTelephonyConnections.contains(connection)) {
            // Adding a duplicate realistically shouldn't happen.
            Log.w(this, "add - connection already tracked; connection=%s", connection);
            return;
        }

        mTelephonyConnections.add(connection);
        connection.addConnectionListener(mConnectionListener);
        recalculate();
    }

    void remove(Connection connection) {
        if (!mTelephonyConnections.contains(connection)) {
            // Debug only since TelephonyConnectionService tries to clean up the connections tracked
            // when the original connection changes.  It does this proactively.
            Log.d(this, "remove - connection not tracked; connection=%s", connection);
            return;
        }
        connection.removeConnectionListener(mConnectionListener);
        mTelephonyConnections.remove(connection);
        recalculate();
    }

    void recalculate() {
        /**
               *  mTelephonyConference is the list of all conferenced/nonconferenced connections.
               *  mTelephonyConference is created upon conference is activated.
               *
               *  recalculateConferece() functions as below:
               *  Update ConferencedConnections in mTelephonyConference first, based on call state
               *  of all connections in mTelephonyConference.
               *  For the first "merge", it will create a new mTelephonyConference obj.
               *  If mTelephonyConference already exists,
               *  update latest connections within mTelephonyConference.
               *
               *  recalculateConferenceable() functions as below:
               *  (1) Update ConferenceableConnections for each connection in GsmConnections, based
               *       on its call state.
               *  (2) Update ConferenceableConnections of mTelephonyConference based on conferenced-
               *       Connections i.e. nonConferencedConnections
               *
               *  Since mTelephonyConference needs to be updated before ConferenceableConnections,
               *  re-arrange the sequence of methods. Otherwise the ConferenceableConnetions in
               *  mTelephonyConference will be INCORRECT.
               */
        recalculateConference();
        recalculateConferenceable();
    }

    private boolean isFullConference(Conference conference) {
        return conference.getConnections().size() >= TELEPHONY_CONFERENCE_MAX_SIZE;
    }

    private boolean participatesInFullConference(Connection connection) {
        return connection.getConference() != null &&
                isFullConference(connection.getConference());
    }

    /**
     * Calculates the conference-capable state of all GSM connections in this connection service.
     */
    private void recalculateConferenceable() {
        Log.v(this, "recalculateConferenceable : %d", mTelephonyConnections.size());
        HashSet<Connection> conferenceableConnections = new HashSet<>(mTelephonyConnections.size());

        // Loop through and collect all calls which are active or holding
        for (TelephonyConnection connection : mTelephonyConnections) {
            Log.d(this, "recalc - %s %s supportsConf? %s", connection.getState(), connection,
                    connection.isConferenceSupported());

            if (connection.isConferenceSupported() && !participatesInFullConference(connection)) {
                switch (connection.getState()) {
                    case Connection.STATE_ACTIVE:
                        //fall through
                    case Connection.STATE_HOLDING:
                        conferenceableConnections.add(connection);
                        continue;
                    default:
                        break;
                }
            }

            connection.setConferenceableConnections(Collections.<Connection>emptyList());
        }

        Log.v(this, "conferenceable: " + conferenceableConnections.size());

        // Go through all the conferenceable connections and add all other conferenceable
        // connections that is not the connection itself
        for (Connection c : conferenceableConnections) {
            List<Connection> connections = conferenceableConnections
                    .stream()
                    // Filter out this connection from the list of connections
                    .filter(connection -> c != connection)
                    .collect(Collectors.toList());
            c.setConferenceableConnections(connections);
        }

        // Set the conference as conferenceable with all of the connections that are not in the
        // conference.
        /// M: CC: Always update nonConferencedConnections to Telecom [ALPS01781919] @{
        /**
        *  TelephonyConferenceController should always update nonConferencedConnections to Telecom
        *  even if the connections of mTelephonyConference is more than 5.
        *  If connection of mTelephonyConference is more than 5,
        *  it needs to update a empty nonConferencedConnections to Telecom.
        */
        if (mTelephonyConference != null) {
            List<Connection> nonConferencedConnections = mTelephonyConnections
                    .stream()
                    // Only retrieve Connections that are not in a conference (but support
                    // conferences).
                    .filter(c -> c.isConferenceSupported() && c.getConference() == null
                            && !isFullConference(mTelephonyConference))
                    .collect(Collectors.toList());
            mTelephonyConference.setConferenceableConnections(nonConferencedConnections);
        }
        /// @}

        // TODO: Do not allow conferencing of already conferenced connections.
    }

    private void recalculateConference() {
        Set<Connection> conferencedConnections = new HashSet<>();
        int numGsmConnections = 0;

        for (TelephonyConnection connection : mTelephonyConnections) {
            com.android.internal.telephony.Connection radioConnection =
                connection.getOriginalConnection();

            if (radioConnection != null) {
                Call.State state = radioConnection.getState();
                Call call = radioConnection.getCall();
                if ((state == Call.State.ACTIVE || state == Call.State.HOLDING) &&
                        (call != null && call.isMultiparty())) {

                    numGsmConnections++;
                    conferencedConnections.add(connection);
                }
            }
        }

        Log.d(this, "Recalculate conference calls %s %s.",
                mTelephonyConference, conferencedConnections);

        /// M: for Ims Conference SRVCC. @{
        if (mTelephonyConference != null && mHandoverTelephonyConference != null) {
            Log.e(TAG, new CallStateException(), "SRVCC: abnormal case!");
        }
        /// @}

        // Check if all conferenced connections are in Connection Service
        boolean allConnInService = true;
        Collection<Connection> allConnections = mConnectionService.getAllConnections();
        for (Connection connection : conferencedConnections) {
            Log.v (this, "Finding connection in Connection Service for " + connection);
            if (!allConnections.contains(connection)) {
                allConnInService = false;
                Log.v(this, "Finding connection in Connection Service Failed");
                break;
            }
        }

        Log.d(this, "Is there a match for all connections in connection service " +
            allConnInService);

        // If this is a GSM conference and the number of connections drops below 2, we will
        // terminate the conference.
        if (numGsmConnections < 2) {
            Log.d(this, "not enough connections to be a conference!");

            // No more connections are conferenced, destroy any existing conference.
            if (mTelephonyConference != null) {
                Log.d(this, "with a conference to destroy!");
                mTelephonyConference.destroy();
                mTelephonyConference = null;
            }
        } else {
            if (mTelephonyConference != null) {
                List<Connection> existingConnections = mTelephonyConference.getConnections();
                // Remove any that no longer exist
                for (Connection connection : existingConnections) {
                    if (connection instanceof TelephonyConnection &&
                            !conferencedConnections.contains(connection)) {
                        mTelephonyConference.removeConnection(connection);
                    }
                }
                if (allConnInService) {
                    mTriggerRecalculate = false;
                    // Add any new ones
                    for (Connection connection : conferencedConnections) {
                        if (!existingConnections.contains(connection)) {
                            mTelephonyConference.addConnection(connection);
                        }
                    }
                } else {
                    Log.d(this, "Trigger recalculate later");
                    mTriggerRecalculate = true;
                }
            } else {
                if (allConnInService) {
                    mTriggerRecalculate = false;
                    // Get PhoneAccount from one of the conferenced connections and use it to set
                    // the phone account on the conference.
                    PhoneAccountHandle phoneAccountHandle = null;
                    if (!conferencedConnections.isEmpty()) {
                        TelephonyConnection telephonyConnection =
                                (TelephonyConnection) conferencedConnections.iterator().next();
                        phoneAccountHandle = PhoneUtils.makePstnPhoneAccountHandle(
                                telephonyConnection.getPhone());
                    }

                    mTelephonyConference = new TelephonyConference(phoneAccountHandle);
                    /// M: for Ims Conference SRVCC @{
                    //mTelephonyConference = new TelephonyConference(null);
                    boolean hasHandoverConference = false;
                    if (mHandoverTelephonyConference != null) {
                        log("SRVCC: assign handover conference to telephonyConference");
                        mTelephonyConference = mHandoverTelephonyConference;
                        mHandoverTelephonyConference = null;
                        hasHandoverConference = true;
                    }
                    /// @}

                    for (Connection connection : conferencedConnections) {
                        Log.d(this, "Adding a connection to a conference call: %s %s",
                                mTelephonyConference, connection);
                        mTelephonyConference.addConnection(connection);
                    }

                    /// M: for Ims Conference SRVCC @{
                    //mConnectionService.addConference(mTelephonyConference);
                    if (hasHandoverConference) {
                        // just reset the handover conference here,
                        // since it is configured in telephonyConnectionService.
                        log("SRVCC: skip adding conference to connectionService");
                    } else {
                        mConnectionService.addConference(mTelephonyConference);
                    }
                    /// @}
                } else {
                    Log.d(this, "Trigger recalculate later");
                    mTriggerRecalculate = true;
                }
            }
            if (mTelephonyConference != null) {
                Connection conferencedConnection = mTelephonyConference.getPrimaryConnection();
                Log.v(this, "Primary Conferenced connection is " + conferencedConnection);
                if (conferencedConnection != null) {
                    switch (conferencedConnection.getState()) {
                        case Connection.STATE_ACTIVE:
                            Log.v(this, "Setting conference to active");
                            mTelephonyConference.setActive();
                            break;
                        case Connection.STATE_HOLDING:
                            Log.v(this, "Setting conference to hold");
                            mTelephonyConference.setOnHold();
                            break;
                    }
                }
            }
        }
    }

    /// M: For VoLTE conference SRVCC. @{
    /**
     * When VoLTE conference SRVCC, it will be switched to TelephonyConference.
     * Telecomm should be unware of this.
     * @param conference the new replaced TelephonyConference.
     * @hide
     */
    void setHandoveredConference(TelephonyConference conference) {
        log("config the handover conference!");
        mHandoverTelephonyConference = conference;
    }
    /// @}

    private void log(String s) {
        Log.d(TAG, s);
    }
}
