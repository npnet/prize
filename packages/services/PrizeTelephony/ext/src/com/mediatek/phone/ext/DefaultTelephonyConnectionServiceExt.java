package com.mediatek.phone.ext;

import android.content.Context;
import android.os.SystemProperties;
import android.telephony.ServiceState;
import android.util.Log;

import java.util.ArrayList;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;

import com.mediatek.common.MPlugin;
import com.mediatek.common.telephony.ILteDataOnlyController;

/**
 * Telephony connection service extension plugin for op09.
*/
public class DefaultTelephonyConnectionServiceExt implements ITelephonyConnectionServiceExt {

    /**
     * Check courrent mode is 4G data only mode.
     *
     * @param context from telephony connection service.
     * @param phone is call via by user
     * @return true if in 4G data only mode.
     */
     public boolean isDataOnlyMode(Context context, Phone phone) {
        //Log.d("context : " + context + " phone : " + phone);
        if (!"1".equals(SystemProperties.get("ro.mtk_tdd_data_only_support"))) {
            return false;
        }
        if (null == context || null == phone) {
            return false;
        }
        int state = phone.getServiceState().getState();
        int phoneType = phone.getPhoneType();
        Log.d("DefaultTelephonyConnectionServiceExt", "isDataOnlyMode, phoneType:"
                + phoneType + ", state:" + state);
        ILteDataOnlyController lteDataOnlyController = MPlugin.createInstance(
                ILteDataOnlyController.class.getName(), context);
        if (lteDataOnlyController != null
                && !lteDataOnlyController.checkPermission(phone.getSubId())) {
            return true;
        }
        return false;
    }

    /**
     * Customize strings which contains 'SIM', replace 'SIM' by 'UIM' etc.
     * @param stringList string list
     * @param slotId slot id
     * @return new string list
     */
    @Override
    public ArrayList<String> customizeSimDisplayString(ArrayList<String> stringList, int slotId) {
        return stringList;
    }
}

