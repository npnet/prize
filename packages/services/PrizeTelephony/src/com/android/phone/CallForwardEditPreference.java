package com.android.phone;

import com.android.ims.ImsManager;
import com.android.internal.telephony.CallForwardInfo;
import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.Phone;
import com.mediatek.phone.TimeConsumingPreferenceListener;
import com.mediatek.phone.ext.ExtensionManager;
import com.mediatek.settings.TelephonyUtils;
import com.mediatek.settings.cdma.CdmaCallForwardOptions;
import com.mediatek.settings.cdma.TelephonyUtilsEx;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.TypedArray;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.PhoneNumberUtils;
import android.telecom.TelecomManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.text.BidiFormatter;
import android.text.SpannableString;
import android.text.TextDirectionHeuristics;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.WindowManager.BadTokenException;
import android.view.WindowManager;

import static com.android.phone.TimeConsumingPreferenceActivity.RESPONSE_ERROR;
import static com.android.phone.TimeConsumingPreferenceActivity.EXCEPTION_ERROR;

import com.mediatek.phone.ext.ExtensionManager;

public class CallForwardEditPreference extends EditPhoneNumberPreference {
    private static final String LOG_TAG = "CallForwardEditPreference";
    private static final boolean DBG = true;//(PhoneGlobals.DBG_LEVEL >= 2);

    private static final String SRC_TAGS[]       = {"{0}"};
    /// M: Wait for xcap server updates call forward completely @{
    private static final int DELAY_TIME = 1500;
    /// }@
    private CharSequence mSummaryOnTemplate;
    /**
     * Remembers which button was clicked by a user. If no button is clicked yet, this should have
     * {@link DialogInterface#BUTTON_NEGATIVE}, meaning "cancel".
     *
     * TODO: consider removing this variable and having getButtonClicked() in
     * EditPhoneNumberPreference instead.
     */
    private int mButtonClicked;
    private int mServiceClass;
    private MyHandler mHandler = new MyHandler();
    int reason;
    private Phone mPhone;
    CallForwardInfo callForwardInfo;
    private TimeConsumingPreferenceListener mTcpListener;

    public CallForwardEditPreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        mSummaryOnTemplate = this.getSummaryOn();

        TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.CallForwardEditPreference, 0, R.style.EditPhoneNumberPreference);
        mServiceClass = a.getInt(R.styleable.CallForwardEditPreference_serviceClass,
                CommandsInterface.SERVICE_CLASS_VOICE);
        reason = a.getInt(R.styleable.CallForwardEditPreference_reason,
                CommandsInterface.CF_REASON_UNCONDITIONAL);
        a.recycle();

        if (DBG) Log.d(LOG_TAG, "mServiceClass=" + mServiceClass + ", reason=" + reason);
    }

    public CallForwardEditPreference(Context context) {
        this(context, null);
    }

    void init(TimeConsumingPreferenceListener listener, boolean skipReading, Phone phone) {
        mPhone = phone;
        mTcpListener = listener;
        PersistableBundle carrierConfig =
                PhoneGlobals.getInstance().getCarrierConfigForSubId(mPhone.getSubId());
        if (!skipReading) {
            /// M: for Plug-in @{
            if (!ExtensionManager.getCallForwardExt().getCallForwardInTimeSlot(this, null, mHandler)) {
            /// }@
                if (carrierConfig.getBoolean(CarrierConfigManager.KEY_SUPPORT_VT_SS_BOOL)) {
                    Log.d(LOG_TAG, "service class: " + mServiceClass);

                    mPhone.getCallForwardingOptionForServiceClass(reason, mServiceClass,
                            mHandler.obtainMessage(MyHandler.MESSAGE_GET_CF,
                                     CommandsInterface.CF_ACTION_DISABLE,
                                     MyHandler.MESSAGE_GET_CF, null));
                } else {
                    mPhone.getCallForwardingOption(reason,
                            mHandler.obtainMessage(MyHandler.MESSAGE_GET_CF,
                                    // unused in this case
                                    CommandsInterface.CF_ACTION_DISABLE,
                                    MyHandler.MESSAGE_GET_CF, null));
                }
            }
            if (mTcpListener != null) {
                mTcpListener.onStarted(this, true);
            }
        } else {
            updateSummaryText();
        }
    }

    @Override
    protected void onBindDialogView(View view) {
        // default the button clicked to be the cancel button.
        mButtonClicked = DialogInterface.BUTTON_NEGATIVE;
        super.onBindDialogView(view);
        /// M: for Plug-in @{
        ExtensionManager.getCallForwardExt().onBindDialogView(this, view);
        /// }@
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {

        /// Add for [VoLTE_SS] @{
        if (which != DialogInterface.BUTTON_NEGATIVE ) {
            if (TelephonyUtils.shouldShowOpenMobileDataDialog(
                    getContext(), mPhone.getSubId()) && !TelephonyUtils.isSupportMims() &&
                (SystemProperties.getInt("ro.mtk_multiple_ims_support", 1) == 1)) {
                TelephonyUtils.showOpenMobileDataDialog(getContext(), mPhone.getSubId());
                return;
            }
        }
        /// @}

        super.onClick(dialog, which);
        mButtonClicked = which;
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);

        PersistableBundle carrierConfig =
                PhoneGlobals.getInstance().getCarrierConfigForSubId(mPhone.getSubId());

        if (DBG) Log.d(LOG_TAG, "mButtonClicked=" + mButtonClicked
                + ", positiveResult=" + positiveResult);
        // Ignore this event if the user clicked the cancel button, or if the dialog is dismissed
        // without any button being pressed (back button press or click event outside the dialog).
        if (this.mButtonClicked != DialogInterface.BUTTON_NEGATIVE) {
            int action = (isToggled() || (mButtonClicked == DialogInterface.BUTTON_POSITIVE)) ?
                    CommandsInterface.CF_ACTION_REGISTRATION :
                    CommandsInterface.CF_ACTION_DISABLE;
            int time = (reason != CommandsInterface.CF_REASON_NO_REPLY) ? 0 : 20;
            final String number = getPhoneNumber();

            if (DBG) Log.d(LOG_TAG, "callForwardInfo=" + callForwardInfo);

            if (action == CommandsInterface.CF_ACTION_REGISTRATION
                    && callForwardInfo != null
                    && callForwardInfo.status == 1
                    && number.equals(callForwardInfo.number)) {
                /// M: for Plug-in @{
                if (ExtensionManager.getCallForwardExt().onDialogClosed(this, action)) {
                    if (DBG) Log.d(LOG_TAG, "time slot is same!");
                    return;
                }
                if (ExtensionManager.getCallForwardExt().setCallForwardInTimeSlot(this, action,
                        number, time, mHandler)) {
                    if (mTcpListener != null) {
                        mTcpListener.onStarted(this, false);
                    }
                    return;
                }
                if (DBG) Log.d(LOG_TAG, "no change, do nothing");
                /// }@
                // no change, do nothing
                //if (DBG) Log.d(LOG_TAG, "no change, do nothing");

            } else {
                // set to network
                if (DBG) Log.d(LOG_TAG, "reason=" + reason + ", action=" + action
                        + ", number=" + number);

                /// M: for Plug-in @{
                if (ExtensionManager.getCallForwardExt().onDialogClosed(this, action)) {
                /// }@
                    return;
                }

                // Display no forwarding number while we're waiting for
                // confirmation
                setSummaryOn("");

                // the interface of Phone.setCallForwardingOption has error:
                // should be action, reason...
                /// M: for Plug-in @{
                if (!ExtensionManager.getCallForwardExt().setCallForwardInTimeSlot(this, action,
                        number, time, mHandler)) {
                /// }@
                    /// Add for [VoLTE_SS] @{
                    if (TelephonyUtils.shouldShowOpenMobileDataDialog(
                            getContext(), mPhone.getSubId()) && !TelephonyUtils.isSupportMims() &&
                            (SystemProperties.getInt("ro.mtk_multiple_ims_support", 1) == 1)) {
                        TelephonyUtils.showOpenMobileDataDialog(getContext(), mPhone.getSubId());
                        return;
                    }
                    /// @}
                    if (carrierConfig.getBoolean(CarrierConfigManager.KEY_SUPPORT_VT_SS_BOOL)) {
                        Log.d(LOG_TAG, "service class: " + mServiceClass);

                        mPhone.setCallForwardingOptionForServiceClass(reason,
                                action,
                                number,
                                time,
                                mServiceClass,
                                mHandler.obtainMessage(MyHandler.MESSAGE_SET_CF,
                                        action,
                                        MyHandler.MESSAGE_SET_CF));
                    } else {
                        mPhone.setCallForwardingOption(action,
                                reason,
                                number,
                                time,
                                mHandler.obtainMessage(MyHandler.MESSAGE_SET_CF,
                                        action,
                                        MyHandler.MESSAGE_SET_CF));
                    }
                }
                if (mTcpListener != null) {
                    mTcpListener.onStarted(this, false);
                }
            }
        }
    }

    void handleCallForwardResult(CallForwardInfo cf) {
        callForwardInfo = cf;
        if (DBG) Log.d(LOG_TAG, "handleGetCFResponse done, callForwardInfo=" + callForwardInfo);

        setToggled(callForwardInfo.status == 1);
        setPhoneNumber(callForwardInfo.number);
        ///M: SmartCallFwd : Disable callforward (when unreachable)
        ///if smart call forwarding is active @ {
        ExtensionManager.getCallFeaturesSettingExt().disableCallFwdPref(
                getContext(), (Object) mPhone, CallForwardEditPreference.this, cf.reason);
        /// @}
    }

    private void updateSummaryText() {
        if (isToggled()) {
            final String number = getRawPhoneNumber();
            if (number != null && number.length() > 0) {
                // Wrap the number to preserve presentation in RTL languages.
                String wrappedNumber = BidiFormatter.getInstance().unicodeWrap(
                        number, TextDirectionHeuristics.LTR);
                String values[] = { wrappedNumber };
                String summaryOn = String.valueOf(
                        TextUtils.replace(mSummaryOnTemplate, SRC_TAGS, values));
                int start = summaryOn.indexOf(wrappedNumber);

                SpannableString spannableSummaryOn = new SpannableString(summaryOn);
                PhoneNumberUtils.addTtsSpan(spannableSummaryOn,
                        start, start + wrappedNumber.length());
                setSummaryOn(spannableSummaryOn);

                /// M: for Plug-in @{
                ExtensionManager.getCallForwardExt().updateSummaryTimeSlotText(this, values);
                /// }@
            } else {
                setSummaryOn(getContext().getString(R.string.sum_cfu_enabled_no_number));
            }
        }

    }

    // Message protocol:
    // what: get vs. set
    // arg1: action -- register vs. disable
    // arg2: get vs. set for the preceding request
    private class MyHandler extends Handler {
        static final int MESSAGE_GET_CF = 0;
        static final int MESSAGE_SET_CF = 1;

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_GET_CF:
                    handleGetCFResponse(msg);
                    break;
                case MESSAGE_SET_CF:
                    handleSetCFResponse(msg);
                    break;
            }
        }

        private boolean isUtError(CommandException.Error er) {
            boolean error = (er == CommandException.Error.UT_XCAP_403_FORBIDDEN
                             || er == CommandException.Error.UT_UNKNOWN_HOST
                             || er == CommandException.Error.OEM_ERROR_2
                             || er == CommandException.Error.OEM_ERROR_3);
          Log.d(LOG_TAG, "Has UT Error: " + error);
          return error;
        }

        private void handleGetCFResponse(Message msg) {
            if (DBG) Log.d(LOG_TAG, "handleGetCFResponse: done");

            /// M: for Plug-in @{
            if (ExtensionManager.getCallForwardExt().handleGetCFInTimeSlotResponse(CallForwardEditPreference.this, msg)) {
                return;
            }
            /// }@

            AsyncResult ar = (AsyncResult) msg.obj;

            /// M: [CT VOLTE] @{
            if (((TelephonyUtilsEx.isCtVolteEnabled() && TelephonyUtilsEx
                            .isCt4gSim(mPhone.getSubId())) || TelephonyUtilsEx
                            .isSmartFren4gSim(getContext(), mPhone.getSubId()))
                    && ar.exception != null && ar.exception instanceof CommandException) {
                CommandException commandException = (CommandException) ar.exception;
                mHasUtError = isUtError(commandException.getCommandError());
            } else {
                mHasUtError = false;
            }
            /// @}
            mTcpListener.onFinished(CallForwardEditPreference.this, msg.arg2 != MESSAGE_SET_CF);

            callForwardInfo = null;
            if (ar.exception != null) {
                if (DBG) Log.d(LOG_TAG, "handleGetCFResponse: ar.exception=" + ar.exception);
                if (ar.exception instanceof CommandException) {
                    /// M: [CT VOLTE] @{
                    CommandException commandException = (CommandException) ar.exception;
                    if (((TelephonyUtilsEx.isCtVolteEnabled() && TelephonyUtilsEx
                            .isCt4gSim(mPhone.getSubId())) || TelephonyUtilsEx
                            .isSmartFren4gSim(getContext(), mPhone.getSubId()))
                            && isUtError(commandException.getCommandError())) {

                        Log.d(LOG_TAG, "403 received, path to CS...");
                        setEnabled(false);
                        if (ImsManager.isEnhanced4gLteModeSettingEnabledByUser(
                                getContext(), mPhone.getPhoneId()) &&
                                (TelephonyUtilsEx.isCapabilityPhone(mPhone)
                                 || TelephonyUtils.isSupportMims())) {
                            Log.d(LOG_TAG, "volte enabled, show alert...");
                            AlertDialog.Builder b = new AlertDialog.Builder(getContext());
                            b.setMessage(R.string.alert_turn_off_volte);
                            b.setCancelable(false);
                            b.setPositiveButton(R.string.alert_dialog_ok,
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            mParentActivity.finish();
                                        }
                                    });
                            try {
                                AlertDialog dialog = b.create();
                                // make the dialog more obvious by bluring the
                                // background.
                                dialog.show();
                            } catch (BadTokenException e) {
                                Log.w(LOG_TAG, "BadTokenException, not show alert dialog!");
                            }
                        } else {
                            startCdmaCallForwardOptions();
                            mParentActivity.finish();
                        }
                    } else {
                    /// @}
                        mTcpListener.onException(CallForwardEditPreference.this,
                                (CommandException) ar.exception);
                    }
                } else {
                    // Most likely an ImsException and we can't handle it the same way as
                    // a CommandException. The best we can do is to handle the exception
                    // the same way as mTcpListener.onException() does when it is not of type
                    // FDN_CHECK_FAILURE.
                    mTcpListener.onError(CallForwardEditPreference.this, EXCEPTION_ERROR);
                }
            } else {
                if (ar.userObj instanceof Throwable) {
                    mTcpListener.onError(CallForwardEditPreference.this, RESPONSE_ERROR);
                }
                CallForwardInfo cfInfoArray[] = (CallForwardInfo[]) ar.result;
                if (cfInfoArray == null || cfInfoArray.length == 0) {
                    if (DBG) Log.d(LOG_TAG, "handleGetCFResponse: cfInfoArray.length==0");
                    setEnabled(false);
                    mTcpListener.onError(CallForwardEditPreference.this, RESPONSE_ERROR);
                } else {
                    for (int i = 0, length = cfInfoArray.length; i < length; i++) {
                        if (DBG) Log.d(LOG_TAG, "handleGetCFResponse, cfInfoArray[" + i + "]="
                                + cfInfoArray[i]);
                        if ((mServiceClass & cfInfoArray[i].serviceClass) != 0) {
                            // corresponding class
                            CallForwardInfo info = cfInfoArray[i];
                            handleCallForwardResult(info);

                            // Show an alert if we got a success response but
                            // with unexpected values.
                            // Currently only handle the fail-to-disable case
                            // since we haven't observed fail-to-enable.
                            if (msg.arg2 == MESSAGE_SET_CF &&
                                    msg.arg1 == CommandsInterface.CF_ACTION_DISABLE &&
                                    info.status == 1) {
                                CharSequence s;
                                switch (reason) {
                                    case CommandsInterface.CF_REASON_BUSY:
                                        s = getContext().getText(R.string.disable_cfb_forbidden);
                                        break;
                                    case CommandsInterface.CF_REASON_NO_REPLY:
                                        s = getContext().getText(R.string.disable_cfnry_forbidden);
                                        break;
                                    default: // not reachable
                                        s = getContext().getText(R.string.disable_cfnrc_forbidden);
                                }
                                try {
                                    AlertDialog.Builder builder = new AlertDialog.Builder(
                                            getContext());
                                    builder.setNeutralButton(R.string.close_dialog, null);
                                    builder.setTitle(getContext().getText(
                                            R.string.error_updating_title));
                                    builder.setMessage(s);
                                    builder.setCancelable(true);
                                    builder.create().show();
                                /// M: 02990751, if activity finished, will be exception, catch
                                // the exception to make a minimal solution @{
                                } catch (BadTokenException e) {
                                    Log.w(LOG_TAG, "BadTokenException, not show alert dialog!");
                                }
                                /// @}
                            }
                        }
                    }
                    ///M : CRALPS02113837 @{
                    ExtensionManager.getCallFeaturesSettingExt()
                            .resetImsPdnOverSSComplete(getContext(), msg.arg2);
                    /// @}
                }
            }

            // Now whether or not we got a new number, reset our enabled
            // summary text since it may have been replaced by an empty
            // placeholder.
            updateSummaryText();
        }

        private void handleSetCFResponse(final Message msg) {
            final AsyncResult ar = (AsyncResult) msg.obj;

            PersistableBundle carrierConfig =
                    PhoneGlobals.getInstance().getCarrierConfigForSubId(mPhone.getSubId());
            if (ar.exception != null) {
                if (DBG) Log.d(LOG_TAG, "handleSetCFResponse: ar.exception=" + ar.exception);
                // setEnabled(false);
            }
            if (DBG) {
                Log.d(LOG_TAG, "handleSetCFResponse: re get start");
            }
            /// M: modem has limitation that if query result immediately set, will
            //  not get the right result, so we need wait 1s to query. just AP workaround @{
            final int arg1 = msg.arg1;
            Runnable runnable = new Runnable() {
            final Message msgCopy = Message.obtain(msg);
                @Override
                public void run() {
                    if (DBG) {
                        Log.d(LOG_TAG, "handleSetCFResponse: re get");
                    }
                    /// M: for Plug-in @{
                    if (ExtensionManager.getCallForwardExt().getCallForwardInTimeSlot(
                            CallForwardEditPreference.this, msgCopy, mHandler)) {
                        return;
                    }
                    /// }@
                    if (carrierConfig.getBoolean(CarrierConfigManager.KEY_SUPPORT_VT_SS_BOOL)) {
                        Log.d(LOG_TAG, "service class: " + mServiceClass);

                        mPhone.getCallForwardingOptionForServiceClass(reason, mServiceClass,
                                obtainMessage(MESSAGE_GET_CF, arg1, MESSAGE_SET_CF, ar.exception));
                    } else {
                        mPhone.getCallForwardingOption(reason,
                                obtainMessage(MESSAGE_GET_CF, arg1, MESSAGE_SET_CF, ar.exception));
                    }
                }
            };
            postDelayed(runnable, DELAY_TIME);
            // @}
        }
    }

    public void setServiceClass(int serviceClass) {
        mServiceClass = serviceClass;

        Log.d(LOG_TAG, "set service class to: " + mServiceClass);
    }
    /// ----------------------------------------------------MTK------------------------------------

    /// M: [CT VOLTE]
    private boolean mHasUtError = false;
    private void startCdmaCallForwardOptions() {
        Log.d(LOG_TAG, "startCdmaCallForwardOptions to sub " + mPhone.getSubId());
        Intent intent = new Intent(getContext(), CdmaCallForwardOptions.class);
        SubscriptionInfoHelper.addExtrasToIntent(intent, SubscriptionManager.from(getContext())
                .getSubscriptionInfo(mPhone.getSubId()));
        getContext().startActivity(intent);
    }

    public boolean hasUtError() {
        return mHasUtError;
    }
}
