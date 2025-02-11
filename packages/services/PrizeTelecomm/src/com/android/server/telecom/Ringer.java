/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
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

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Vibrator;
import android.provider.Settings;

import com.android.internal.annotations.VisibleForTesting;

import com.mediatek.telecom.TelecomUtils;
/*--PRIZE-Add-SIMPLE_LAUNCHER_TTS-hpf-2017_11_25-start--*/
import com.android.server.telecom.VoiceUtils;
import com.mediatek.common.prizeoption.PrizeOption;
/*--PRIZE-Add-SIMPLE_LAUNCHER_TTS-hpf-2017_11_25-end--*/

/**
 * Controls the ringtone player.
 */
@VisibleForTesting
public class Ringer {
    private static final long[] VIBRATION_PATTERN = new long[] {
        0, // No delay before starting
        1000, // How long to vibrate
        1000, // How long to wait before vibrating again
    };

    private static final AudioAttributes VIBRATION_ATTRIBUTES = new AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .build();

    /** Indicate that we want the pattern to repeat at the step which turns on vibration. */
    private static final int VIBRATION_PATTERN_REPEAT = 1;

    /**
     * Used to keep ordering of unanswered incoming calls. There can easily exist multiple incoming
     * calls and explicit ordering is useful for maintaining the proper state of the ringer.
     */

    private final SystemSettingsUtil mSystemSettingsUtil;
    private final InCallTonePlayer.Factory mPlayerFactory;
    private final AsyncRingtonePlayer mRingtonePlayer;
    private final Context mContext;
    private final Vibrator mVibrator;
    private final InCallController mInCallController;

    private InCallTonePlayer mCallWaitingPlayer;
    private RingtoneFactory mRingtoneFactory;

    /**
     * Call objects that are ringing or call-waiting. These are used only for logging purposes.
     */
    private Call mRingingCall;
    private Call mCallWaitingCall;

    /**
     * Used to track the status of {@link #mVibrator} in the case of simultaneous incoming calls.
     */
    private boolean mIsVibrating = false;

    /// M: MO vibrate @{
    private final static int KEY_MO_VIBRATE_CONFIG = 0x00000002;
    private final static long MO_CALL_VIBRATE_TIME = 200;
    /// @}

    /** Initializes the Ringer. */
    @VisibleForTesting
    public Ringer(
            InCallTonePlayer.Factory playerFactory,
            Context context,
            SystemSettingsUtil systemSettingsUtil,
            AsyncRingtonePlayer asyncRingtonePlayer,
            RingtoneFactory ringtoneFactory,
            Vibrator vibrator,
            InCallController inCallController) {

        mSystemSettingsUtil = systemSettingsUtil;
        mPlayerFactory = playerFactory;
        mContext = context;
        // We don't rely on getSystemService(Context.VIBRATOR_SERVICE) to make sure this
        // vibrator object will be isolated from others.
        mVibrator = vibrator;
        mRingtonePlayer = asyncRingtonePlayer;
        mRingtoneFactory = ringtoneFactory;
        mInCallController = inCallController;
    }

    public boolean startRinging(Call foregroundCall) {
        AudioManager audioManager =
                (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        boolean isRingerAudible = audioManager.getStreamVolume(AudioManager.STREAM_RING) > 0;

        if (mSystemSettingsUtil.isTheaterModeOn(mContext)) {
            Log.d(this, "[Debug]startRinging() -> false for !isTheaterModeOn()");
            return false;
        }

        if (foregroundCall == null) {

            /// M: ALPS02801645 @{
            // Consider case: 1A+1W, airplane mode on => receive disconnect of active call first.
            // => audio change to ringing. When we come here, W has been removed in another thread.
            // Original code:
            // Log.wtf(this, "startRinging called with null foreground call.");
            Log.d(this, "startRinging called with null foreground call.");
            /// @}

            return false;

        }

        if (mInCallController.doesConnectedDialerSupportRinging()) {
            Log.event(foregroundCall, Log.Events.SKIP_RINGING);
            Log.d(this, "[Debug]startRinging() -> false for SKIP_RINGING");
            return isRingerAudible;
        }

        stopCallWaiting();

        if (!shouldRingForContact(foregroundCall.getContactUri())) {
            Log.d(this, "[Debug]startRinging() -> false for !shouldRingForContact()");
            return false;
        }

        if (isRingerAudible) {
            mRingingCall = foregroundCall;
            Log.event(foregroundCall, Log.Events.START_RINGER);
            // Because we wait until a contact info query to complete before processing a
            // call (for the purposes of direct-to-voicemail), the information about custom
            // ringtones should be available by the time this code executes. We can safely
            // request the custom ringtone from the call and expect it to be current.
            
            /*--PRIZE-change-SIMPLE_LAUNCHER_TTS-hpf-2017_11_25-start--*/
            if(PrizeOption.PRIZE_TTS_SUPPORT && VoiceUtils.getKey(VoiceUtils.PRIZE_VOICE_KEY, mContext) == 1 
                && VoiceUtils.getKey(VoiceUtils.PRIZE_VOICE_CALL_KEY, mContext) == 1){
              //do nothing
            }else{
                mRingtonePlayer.play(mRingtoneFactory, foregroundCall);
            }						
            /*--PRIZE-change-SIMPLE_LAUNCHER_TTS-hpf-2017_11_25-end--*/
        } else {
            Log.i(this, "startRingingOrCallWaiting, skipping because volume is 0");
        }

        if (shouldVibrate(mContext) && !mIsVibrating) {
            mVibrator.vibrate(VIBRATION_PATTERN, VIBRATION_PATTERN_REPEAT,
                    VIBRATION_ATTRIBUTES);
            mIsVibrating = true;
        }

        return isRingerAudible;
    }

    public void startCallWaiting(Call call) {
        if (mSystemSettingsUtil.isTheaterModeOn(mContext)) {
            return;
        }

        if (mInCallController.doesConnectedDialerSupportRinging()) {
            Log.event(call, Log.Events.SKIP_RINGING);
            return;
        }

        Log.v(this, "Playing call-waiting tone.");

        stopRinging();

        if (mCallWaitingPlayer == null) {
            Log.event(call, Log.Events.START_CALL_WAITING_TONE);
            mCallWaitingCall = call;
            mCallWaitingPlayer =
                    mPlayerFactory.createPlayer(InCallTonePlayer.TONE_CALL_WAITING);
            mCallWaitingPlayer.startTone();
        }
    }

    public void stopRinging() {
        if (mRingingCall != null) {
            Log.event(mRingingCall, Log.Events.STOP_RINGER);
            mRingingCall = null;
        }

        mRingtonePlayer.stop();

        if (mIsVibrating) {
            mVibrator.cancel();
            mIsVibrating = false;
        }
    }

    // M: fix CR:ALPS03137578,multiple thread invoke stopCallWaiting,
    // so occur mCallWaitingPlyer object is null to occur JE seldom.
    public synchronized void stopCallWaiting() {
        Log.v(this, "stop call waiting.");
        if (mCallWaitingPlayer != null) {
            if (mCallWaitingCall != null) {
                Log.event(mCallWaitingCall, Log.Events.STOP_CALL_WAITING_TONE);
                mCallWaitingCall = null;
            }

            mCallWaitingPlayer.stopTone();
            mCallWaitingPlayer = null;
        }
    }

    private boolean shouldRingForContact(Uri contactUri) {
        Log.d(this, "[Debug]shouldRingForContact()... contactUri : " + contactUri);
        final NotificationManager manager =
                (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        final Bundle extras = new Bundle();
        if (contactUri != null) {
            extras.putStringArray(Notification.EXTRA_PEOPLE, new String[] {contactUri.toString()});
        }
        Log.d(this, "[Debug]shouldRingForContact()... extras : " + extras);
        return manager.matchesCallFilter(extras);
    }

    private boolean shouldVibrate(Context context) {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        int ringerMode = audioManager.getRingerModeInternal();
        if (getVibrateWhenRinging(context)) {
            return ringerMode != AudioManager.RINGER_MODE_SILENT;
        } else {
            return ringerMode == AudioManager.RINGER_MODE_VIBRATE;
        }
    }

    private boolean getVibrateWhenRinging(Context context) {
        if (!mVibrator.hasVibrator()) {
            return false;
        }
        return mSystemSettingsUtil.canVibrateWhenRinging(context);
    }

    /**
     * M: vibreate when MO is connceted @{
     * @param call: the related call.
     * @param oldState: previous call state.
     * @param newState: new call state.
     */
    @VisibleForTesting
    public void vibrateMOConnected(Call call, int oldState, int newState) {
        if (newState == CallState.ACTIVE
                && (oldState == CallState.DIALING || oldState == CallState.CONNECTING)
                /// M: CDMA MO call special handling. @{
                // For cdma call, framework will vibrate when the call be 'really' answered
                // by remote side, at this point the CDMA MO call maybe not in
                // real ACTIVE state, so skip this for CDMA MO call.
                && !call.isCdmaDialingCall()) {
                /// @}
            int emSetting = Settings.Global.getInt(mContext.getContentResolver(),
                    Settings.Global.TELEPHONY_MISC_FEATURE_CONFIG, KEY_MO_VIBRATE_CONFIG);
            boolean enabled = (emSetting & KEY_MO_VIBRATE_CONFIG) != 0;
            if (enabled) {
                mVibrator.vibrate(MO_CALL_VIBRATE_TIME);
            }
        }
    }
    /** @}   */
}
