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

import android.content.ContentResolver;
import android.provider.Settings;

/**
 * A helper class which serves only to make it easier to lookup timeout values. This class should
 * never be instantiated, and only accessed through the {@link #get(String, long)} method.
 *
 * These methods are safe to call from any thread, including the UI thread.
 */
public final class Timeouts {
    public static class Adapter {
        public Adapter() { }

        public long getCallScreeningTimeoutMillis(ContentResolver cr) {
            return Timeouts.getCallScreeningTimeoutMillis(cr);
        }

        public long getCallRemoveUnbindInCallServicesDelay(ContentResolver cr) {
            return Timeouts.getCallRemoveUnbindInCallServicesDelay(cr);
        }
    }

    /** A prefix to use for all keys so to not clobber the global namespace. */
    private static final String PREFIX = "telecomm.";

    ///M: use to cache session timeout value @{
    private static long SESSION_CLEANUP_TIMEOUT_MILLIS = Long.MAX_VALUE;
    /// @}

    private Timeouts() {}

    /**
     * Returns the timeout value from Settings or the default value if it hasn't been changed. This
     * method is safe to call from any thread, including the UI thread.
     *
     * @param contentResolver The content resolved.
     * @param key Settings key to retrieve.
     * @param defaultValue Default value, in milliseconds.
     * @return The timeout value from Settings or the default value if it hasn't been changed.
     */
    private static long get(ContentResolver contentResolver, String key, long defaultValue) {
        return Settings.Secure.getLong(contentResolver, PREFIX + key, defaultValue);
    }

    /**
     * Returns the amount of time to wait before disconnecting a call that was canceled via
     * NEW_OUTGOING_CALL broadcast. This timeout allows apps which repost the call using a gateway
     * to reuse the existing call, preventing the call from causing a start->end->start jank in the
     * in-call UI.
     */
    public static long getNewOutgoingCallCancelMillis(ContentResolver contentResolver) {
        return get(contentResolver, "new_outgoing_call_cancel_ms", 100000L);
    }

    /**
     * Returns the amount of time to play each DTMF tone after post dial continue.
     * This timeout allows the current tone to play for a certain amount of time before either being
     * interrupted by the next tone or terminated.
     */
    public static long getDelayBetweenDtmfTonesMillis(ContentResolver contentResolver) {
        return get(contentResolver, "delay_between_dtmf_tones_ms", 300L);
    }

    /**
     * Returns the amount of time to wait for an emergency call to be placed before routing to
     * a different call service. A value of 0 or less means no timeout should be used.
     */
    public static long getEmergencyCallTimeoutMillis(ContentResolver contentResolver) {
        return get(contentResolver, "emergency_call_timeout_millis", 25000L /* 25 seconds */);
    }

    /**
     * Returns the amount of time to wait for an emergency call to be placed before routing to
     * a different call service. This timeout is used only when the radio is powered off (for
     * example in airplane mode). A value of 0 or less means no timeout should be used.
     */
    public static long getEmergencyCallTimeoutRadioOffMillis(ContentResolver contentResolver) {
        return get(contentResolver, "emergency_call_timeout_radio_off_millis",
                60000L /* 1 minute */);
    }

    /**
     * Returns the amount of delay before unbinding the in-call services after all the calls
     * are removed.
     */
    public static long getCallRemoveUnbindInCallServicesDelay(ContentResolver contentResolver) {
        return get(contentResolver, "call_remove_unbind_in_call_services_delay",
                2000L /* 2 seconds */);
    }

    /**
     * Returns the amount of time for which bluetooth is considered connected after requesting
     * connection. This compensates for the amount of time it takes for the audio route to
     * actually change to bluetooth.
     */
    public static long getBluetoothPendingTimeoutMillis(ContentResolver contentResolver) {
        return get(contentResolver, "bluetooth_pending_timeout_millis", 5000L);
    }

    /**
     * Returns the amount of time to wait before retrying the connectAudio call. This is
     * necessary to account for the HeadsetStateMachine sometimes not being ready when we want to
     * connect to bluetooth audio immediately after a device connects.
     */
    public static long getRetryBluetoothConnectAudioBackoffMillis(ContentResolver contentResolver) {
        return get(contentResolver, "retry_bluetooth_connect_audio_backoff_millis", 500L);
    }

    /**
     * Returns the amount of time after a Logging session has been started that Telecom is set to
     * perform a sweep to check and make sure that the session is still not incomplete (stale).
     */
    public static long getStaleSessionCleanupTimeoutMillis(ContentResolver contentResolver) {
        ///M: @{
        /* Google original
        return get(contentResolver, "stale_session_cleanup_timeout_millis",
                Log.DEFAULT_SESSION_TIMEOUT_MS);
        */
        if (SESSION_CLEANUP_TIMEOUT_MILLIS == Long.MAX_VALUE) {
            SESSION_CLEANUP_TIMEOUT_MILLIS = get(contentResolver,
                    "stale_session_cleanup_timeout_millis",
                    Log.DEFAULT_SESSION_TIMEOUT_MS);
        }
        return SESSION_CLEANUP_TIMEOUT_MILLIS;
        /// @}
    }

    /**
     * Returns the amount of time to wait for the call screening service to allow or disallow a
     * call.
     */
    public static long getCallScreeningTimeoutMillis(ContentResolver contentResolver) {
        return get(contentResolver, "call_screening_timeout", 5000L /* 5 seconds */);
    }
}
