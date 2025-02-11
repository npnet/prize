package com.mediatek.incallui.videocall;

import android.content.Context;
import android.os.SystemProperties;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;
import com.android.incallui.Call;
import com.android.incallui.CallList;
import com.android.incallui.InCallPresenter;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * M: management for video call features.
 */
public class VideoFeatures {
    private final Call mCall;
    private final String VIDEO_FULL_SCREEN_KEY = "incall_video_screen_key";
    /**
     * M: [video call]for management of video call features.
     *
     * @param call the call associate with current VideoFeatures instance.
     */
    public VideoFeatures(Call call) {
        mCall = call;
    }

    /**
     * M: whether this call supports rotation.
     * make sure this is a video call before checking this feature.
     *
     * @return true if support.
     */
    public boolean supportsRotation() {
        return !isCsCall();
    }

    /**
     * M: whether this call supports downgrade.
     * make sure this is a video call before checking this feature.
     *
     * @return true if support.
     */
    public boolean supportsDowngrade() {
        return !isCsCall();
    }

    /**
     * M: whether this call supports answer as voice.
     * make sure this is a video call before checking this feature.
     *
     * @return true if support.
     */
    public boolean supportsAnswerAsVoice() {
        return !isCsCall();
    }

    /**
     * M: whether this call supports pause (turn off camera).
     * make sure this is a video call before checking this feature.
     *
     * @return
     */
    public boolean supportsPauseVideo() {
        return !isCsCall()
                && !UNSUPPORT_ONE_WAY_VIDEO_OPERATOR_LIST.contains(getOperatorName(mCall));
    }

    /**
     * M: whether this call supports cancel upgrade .
     * make sure this is a video call before checking this feature.
     *
     * @return true if support
     */
    public boolean supportsCancelUpgradeVideo() {
        return !isCsCall()
                && SUPPORT_CANCEL_UPGRADE_VIDEO_OPERATOR_LIST.contains(getOperatorName(mCall));
    }

    /// M: Add for video feature change due to camera error @{
    private boolean mCameraErrorHappened = false;
    public void setCameraErrorHappened(boolean happen) {
        mCameraErrorHappened = happen;
    }
    /// @}

    /**
     * M: check the current call can upgrade to video call or not
     *
     * @return false if without subject call, active and hold call with the same account which
     * belongs to some operator, else depends on whether it is not a CS Call.
     */
    public boolean canUpgradeToVideoCall() {
        if (mCall == null) {
            return false;
        }

        if (UNSUPPORT_MULTI_VIDEO_OPERATOR_LIST.contains(getOperatorName(mCall))) {
            //FIXME: support vilte capability when multi calls exist with different accounts.
            if (CallList.getInstance().getBackgroundCall() == null
                    || !Objects.equals(mCall.getTelecomCall().getDetails().getAccountHandle(),
                    CallList.getInstance().getBackgroundCall().getTelecomCall().getDetails()
                            .getAccountHandle())) {
                return !isCsCall() && !mCameraErrorHappened;
            } else {
                // return false if Active and Hold Calls from the same account
                return false;
            }
        }

        return !isCsCall() && !mCameraErrorHappened;
    }

    /**
     * M: Check whether it's ims(video) call or 3gvt call
     */
    public boolean isImsCall() {
        return !isCsCall();
    }

    /**
     * M: whether this call supports hold.
     * make sure this is a video call before checking this feature.
     *
     * @return
     */
    public boolean supportsHold() {
        return !isCsCall();
    }

    public boolean supportsHidePreview() {
        return !isCsCall();
    }

    public boolean supportsAutoDeclineUpgradeRequest() {
        return true;
    }

    /**
     * M: for debugging use.
     * if user set this system property by adb command, the "upgrade to video" button would appear.
     *     adb shell setprop manual.enable.video.call 1
     *
     * @return true if the user enable the video feature by adb command.
     */
    public boolean forceEnableVideo() {
        return (SystemProperties.getInt("manual.enable.video.call", -1) == 1);
    }

    private boolean isCsCall() {
        return !mCall.hasProperty(android.telecom.Call.Details.PROPERTY_VOLTE);
    }
     /**
     * M: whether this support auto fullScreen for video call.
     * @return
     */

    public boolean supportAutoIntoFullScreen() {
        return "1".equals(SystemProperties.get(VIDEO_FULL_SCREEN_KEY));
    }

    /**
     * FIXME: If some operator do not support some video features, the info should be passed
     * by PhoneAccount instead of hard coding in InCallUI.
     * M: A list or operators who doesn't support one_way video call. Should hide the turn
     * off video button for them.
     */
    private final static List<String> UNSUPPORT_ONE_WAY_VIDEO_OPERATOR_LIST = Arrays.asList(
            "46000", "46002", "46004", "46007", "46008");
    /**
     * FIXME: maybe need to extend the designated operator's mnc if necessary
     * M: A list of operator that support cancel upgrade.
     */
    private final static List<String> SUPPORT_CANCEL_UPGRADE_VIDEO_OPERATOR_LIST = Arrays.asList(
               "46000", "46002", "46004","46007", "46008" );

    /**
     * FIXME: maybe need to extend the designated operator's mnc if necessary
     * M: A list of operator that not support multiple video call.
     */
    private final static List<String> UNSUPPORT_MULTI_VIDEO_OPERATOR_LIST = Arrays.asList(
            "46000", "46002", "46004", "46007", "46008");

    /**
     * FIXME: If some operator do not support some video features, the info should be passed
     * by PhoneAccount instead of hard coding in InCallUI.
     * M: The way to get operator name from call.
     */
    private String getOperatorName(Call call) {
        PhoneAccountHandle phoneAccountHandle = call.getAccountHandle();
        if (phoneAccountHandle == null) {
            return "";
        }
        TelecomManager telecomManager = (TelecomManager) InCallPresenter.getInstance()
                .getContext().getSystemService(Context.TELECOM_SERVICE);
        PhoneAccount phoneAccount = telecomManager.getPhoneAccount(phoneAccountHandle);
        if (phoneAccount == null) {
            return "";
        }
        TelephonyManager telephonyManager = (TelephonyManager) InCallPresenter.getInstance()
                .getContext().getSystemService(Context.TELEPHONY_SERVICE);
        int subId = telephonyManager.getSubIdForPhoneAccount(phoneAccount);
        String mccMnc = telephonyManager.getSimOperator(subId);
        return mccMnc;
    }
}
