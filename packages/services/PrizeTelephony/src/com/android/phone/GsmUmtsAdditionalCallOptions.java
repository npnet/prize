package com.android.phone;

import android.app.ActionBar;
import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.util.Log;
import android.view.MenuItem;

import com.android.internal.telephony.Phone;
import com.mediatek.settings.cdma.TelephonyUtilsEx;

import java.util.ArrayList;

public class GsmUmtsAdditionalCallOptions extends TimeConsumingPreferenceActivity
        implements PhoneGlobals.SubInfoUpdateListener {
    private static final String LOG_TAG = "GsmUmtsAdditionalCallOptions";
    private final boolean DBG = true;//(PhoneGlobals.DBG_LEVEL >= 2);

    private static final String BUTTON_CLIR_KEY  = "button_clir_key";
    private static final String BUTTON_CW_KEY    = "button_cw_key";

    private static final String KEY_TOGGLE = "toggle";
    private static final String KEY_STATE = "state";

    private CLIRListPreference mCLIRButton;
    private CallWaitingCheckBoxPreference mCWButton;

    private final ArrayList<Preference> mPreferences = new ArrayList<Preference>();
    private int mInitIndex = 0;
    private Phone mPhone;
    private SubscriptionInfoHelper mSubscriptionInfoHelper;
    private int mSubId;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        /*PRIZE-Change-DialerV8-wangzhong-2017_7_19-start*/
        /*addPreferencesFromResource(R.xml.gsm_umts_additional_options);*/
        addPreferencesFromResource(R.xml.prize_gsm_umts_additional_options);
        getListView().setPadding(0, getResources().getDimensionPixelOffset(R.dimen.prize_preferences_bg_margin2),
                0, getResources().getDimensionPixelOffset(R.dimen.prize_preferences_bg_margin2));
        getListView().setBackgroundColor(getResources().getColor(R.color.prize_preferences_lowest_bg));
        getListView().setDivider(null);
        android.graphics.drawable.ColorDrawable drawable = new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT);
        getListView().setSelector(drawable);
        /*PRIZE-Change-DialerV8-wangzhong-2017_7_19-end*/

        mSubscriptionInfoHelper = new SubscriptionInfoHelper(this, getIntent());
        mSubscriptionInfoHelper.setActionBarTitle(
                getActionBar(), getResources(), R.string.additional_gsm_call_settings_with_label);
        mPhone = mSubscriptionInfoHelper.getPhone();

        /// M: Add for MTK hotswap
        PhoneGlobals.getInstance().addSubInfoUpdateListener(this);
        if (mPhone == null) {
            Log.d(LOG_TAG, "onCreate: mPhone is null, finish!!!");
            finish();
            return;
        }
        mSubId = mPhone.getSubId();

        PreferenceScreen prefSet = getPreferenceScreen();
        mCLIRButton = (CLIRListPreference) prefSet.findPreference(BUTTON_CLIR_KEY);
        mCWButton = (CallWaitingCheckBoxPreference) prefSet.findPreference(BUTTON_CW_KEY);

        mPreferences.add(mCLIRButton);
        mPreferences.add(mCWButton);

        if (TelephonyUtilsEx.isSmartFren4gSim(mPhone.getContext(), mSubId)) {
            mSubscriptionInfoHelper.setActionBarTitle(
                getActionBar(), getResources(), R.string.mtk_caller_id_with_label);
            prefSet.removePreference(mCWButton);
            mPreferences.remove(mCWButton);
        }
        /// M: adjust the waiting dialog show time firstly
        mIsForeground = true;

        if (icicle == null) {
            if (DBG) Log.d(LOG_TAG, "start to init ");
            mCLIRButton.init(this, false, mPhone);
        } else {
            if (DBG) Log.d(LOG_TAG, "restore stored states");
            mInitIndex = mPreferences.size();
            mCLIRButton.init(this, true, mPhone);
            if (!TelephonyUtilsEx.isSmartFren4gSim(mPhone.getContext(), mSubId)) {
                mCWButton.init(this, true, mPhone);
            }
            int[] clirArray = icicle.getIntArray(mCLIRButton.getKey());
            if (clirArray != null) {
                if (DBG) Log.d(LOG_TAG, "onCreate:  clirArray[0]="
                        + clirArray[0] + ", clirArray[1]=" + clirArray[1]);
                mCLIRButton.handleGetCLIRResult(clirArray);
            } else {
                mCLIRButton.init(this, false, mPhone);
            }
            if (!TelephonyUtilsEx.isSmartFren4gSim(mPhone.getContext(), mSubId)) {
                Bundle bundle = icicle.getParcelable(mCWButton.getKey());
                if (bundle != null) {
                    mCWButton.setChecked(bundle.getBoolean(KEY_TOGGLE));
                    mCWButton.setEnabled(bundle.getBoolean(KEY_STATE));
                }
            }
        }

        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            // android.R.id.home will be triggered in onOptionsItemSelected()
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        if (mCLIRButton != null && mCLIRButton.clirArray != null) {
            outState.putIntArray(mCLIRButton.getKey(), mCLIRButton.clirArray);
        }
        if (!TelephonyUtilsEx.isSmartFren4gSim(mPhone.getContext(), mSubId) && mCWButton != null) {
            Bundle bundle = new Bundle();
            bundle.putBoolean(KEY_TOGGLE, mCWButton.isChecked());
            bundle.putBoolean(KEY_STATE, mCWButton.isEnabled());
            outState.putBundle(mCWButton.getKey(), bundle);
        }
    }

    @Override
    public void onFinished(Preference preference, boolean reading) {
        if (mInitIndex < mPreferences.size()-1 && !isFinishing()) {
            mInitIndex++;
            Preference pref = mPreferences.get(mInitIndex);
            if (!TelephonyUtilsEx.isSmartFren4gSim(mPhone.getContext(), mSubId) &&
                 pref instanceof CallWaitingCheckBoxPreference) {
                ((CallWaitingCheckBoxPreference) pref).init(this, false, mPhone);
            }
        }
        super.onFinished(preference, reading);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == android.R.id.home) {  // See ActionBar#setDisplayHomeAsUpEnabled()
            CallFeaturesSetting.goUpToTopLevelSetting(this, mSubscriptionInfoHelper);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onDestroy() {
        PhoneGlobals.getInstance().removeSubInfoUpdateListener(this);
        super.onDestroy();
    }

    @Override
    public void handleSubInfoUpdate() {
        finish();
    }
}
