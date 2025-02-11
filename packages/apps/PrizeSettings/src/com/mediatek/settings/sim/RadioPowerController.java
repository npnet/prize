package com.mediatek.settings.sim;

import android.content.Context;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.Settings;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.util.Log;

import com.android.internal.telephony.ITelephony;
import com.mediatek.internal.telephony.ITelephonyEx;
import com.mediatek.settings.UtilsExt;
import com.mediatek.settings.ext.ISimManagementExt;

/**
 * Radio power manager to control radio state.
 */
public class RadioPowerController {

    private static final String TAG = "RadioPowerController";
    private Context mContext;
    private static final int MODE_PHONE1_ONLY = 1;
    private ISimManagementExt mExt;
    private static RadioPowerController sInstance = null;

   /**
    * Constructor.
    * @param context Context
    */
    private RadioPowerController(Context context) {
        mContext = context;
        mExt = UtilsExt.getSimManagmentExtPlugin(mContext);
    }

    private static synchronized void createInstance(Context context) {
        if(sInstance == null) {
            sInstance = new RadioPowerController(context);
        }
    }

    public static RadioPowerController getInstance(Context context) {
        if(sInstance == null) {
            createInstance(context);
        }
        return sInstance;
    }

    public boolean setRadionOn(int subId, boolean turnOn) {
        Log.d(TAG, "setRadioOn, turnOn: " + turnOn + ", subId = " + subId);
        boolean isSuccessful = false;
        int slot = SubscriptionManager.getSlotId(subId);
        if (!SubscriptionManager.isValidSubscriptionId(subId)) {
            return isSuccessful;
        }
        ITelephony telephony = ITelephony.Stub.asInterface(ServiceManager.getService(
                Context.TELEPHONY_SERVICE));
        try {
            if (telephony != null &&
                    telephony.isRadioOnForSubscriber(subId, mContext.getPackageName()) != turnOn) {
                isSuccessful = telephony.setRadioForSubscriber(subId, turnOn);
                if (isSuccessful) {
                    updateRadioMsimDb(subId, turnOn);
                    /// M: for plug-in
                    mExt.setRadioPowerState(subId, turnOn);
                }
            } else {
                Log.d(TAG, "telephony is null");
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        Log.d(TAG, "setRadionOn, isSuccessful: " + isSuccessful);
        return isSuccessful;
    }

    private void updateRadioMsimDb(int subId, boolean turnOn) {
        int priviousSimMode = Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.MSIM_MODE_SETTING, -1);
        Log.i(TAG, "updateRadioMsimDb, The current dual sim mode is " + priviousSimMode
                + ", with subId = " + subId);
        int currentSimMode;
        boolean isPriviousRadioOn = false;
        int slot = SubscriptionManager.getSlotId(subId);
        int modeSlot = MODE_PHONE1_ONLY << slot;
        if ((priviousSimMode & modeSlot) > 0) {
            currentSimMode = priviousSimMode & (~modeSlot);
            isPriviousRadioOn = true;
        } else {
            currentSimMode = priviousSimMode | modeSlot;
            isPriviousRadioOn = false;
        }

        Log.d(TAG, "currentSimMode=" + currentSimMode + " isPriviousRadioOn =" + isPriviousRadioOn
                + ", turnOn: " + turnOn);
        if (turnOn != isPriviousRadioOn) {
            Settings.Global.putInt(mContext.getContentResolver(),
                    Settings.Global.MSIM_MODE_SETTING, currentSimMode);
        } else {
            Log.w(TAG, "quickly click don't allow.");
        }
    }

    /**
     * whether radio switch finish on subId, according to the radio state.
     */
    public boolean isRadioSwitchComplete(final int subId) {
        if (!SubscriptionManager.isValidSubscriptionId(subId)) {
            return false;
        }
        int slotId = SubscriptionManager.getSlotId(subId);

        boolean isRadioOn = TelephonyUtils.isRadioOn(subId, mContext);
        Log.d(TAG, "isRadioSwitchComplete: slot: " + slotId + ", isRadioOn: " + isRadioOn);
        if (!isRadioOn || (isExpectedRadioStateOn(slotId) && isRadioOn)) {
            return true;
        }
        return false;
    }

    public boolean isExpectedRadioStateOn(int slot) {
        int currentSimMode = Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.MSIM_MODE_SETTING, -1);
        boolean expectedRadioOn = (currentSimMode & (MODE_PHONE1_ONLY << slot)) != 0;
        Log.d(TAG, "isExpectedRadioStateOn: slot: " + slot +
                ", expectedRadioOn: " + expectedRadioOn);
        return expectedRadioOn;
    }
}