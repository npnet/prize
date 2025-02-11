/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 *
 * MediaTek Inc. (C) 2010. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER ON
 * AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
 * NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
 * SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
 * SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES TO LOOK ONLY TO SUCH
 * THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. RECEIVER EXPRESSLY ACKNOWLEDGES
 * THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES
 * CONTAINED IN MEDIATEK SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK
 * SOFTWARE RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND
 * CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
 * AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
 * OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY RECEIVER TO
 * MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek Software")
 * have been modified by MediaTek Inc. All revisions are subject to any receiver's
 * applicable license agreements with MediaTek Inc.
 */
package com.mediatek.simprocessor;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.os.AsyncTask;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.Settings;
import android.telephony.SubscriptionInfo;
import android.telephony.TelephonyManager;
//import android.util.Log;
import android.widget.Toast;

import com.android.internal.telephony.ITelephony;
import com.mediatek.internal.telephony.ITelephonyEx;
import com.mediatek.simprocessor.SimServiceUtils;
import com.mediatek.simprocessor.Log;

import java.util.HashMap;
import java.util.List;


public class SimCardUtils {
    private static final String TAG = "SimCardUtils";

    public interface SimType {
        String SIM_TYPE_USIM_TAG = "USIM";
        String SIM_TYPE_SIM_TAG = "SIM";
        String SIM_TYPE_RUIM_TAG = "RUIM";
        String SIM_TYPE_CSIM_TAG = "CSIM";

        int SIM_TYPE_SIM = 0;
        int SIM_TYPE_USIM = 1;
        int SIM_TYPE_RUIM = 2;
        int SIM_TYPE_CSIM = 3;
        int SIM_TYPE_UNKNOWN = -1;
    }

    public static boolean isSimPinRequest(long slotId) {
        Boolean v = (Boolean) getPresetObject(String.valueOf(slotId), SIM_KEY_WITHSLOT_PIN_REQUEST);
        if (v != null) {
            Log.w(TAG, "[isSimPinRequest]slotId:" + slotId + ",v:" + v);
            return v;
        }

        boolean isPinRequest = (TelephonyManager.SIM_STATE_PIN_REQUIRED == TelephonyManager
                .getDefault().getSimState((int) slotId));
        Log.d(TAG, "[isSimPinRequest]slotId:" + slotId + ",isPukRequest:" + isPinRequest);

        return isPinRequest;
    }

    public static boolean isSimStateReady(long slotId) {
        Boolean v = (Boolean) getPresetObject(String.valueOf(slotId), SIM_KEY_WITHSLOT_STATE_READY);
        if (v != null) {
            Log.w(TAG, "[isSimStateReady]slotId:" + slotId + ",v:" + v);
            return v;
        }

        boolean isSimStateReady = (TelephonyManager.SIM_STATE_READY == TelephonyManager
                .getDefault().getSimState((int) slotId));
        Log.d(TAG, "[isSimStateReady]slotId:" + slotId + ",isPukRequest:" + isSimStateReady);

        return isSimStateReady;
    }

    public static boolean isSimInserted(int slotId) {
        Boolean v = (Boolean) getPresetObject(String.valueOf(slotId),
                SIM_KEY_WITHSLOT_SIM_INSERTED);
        if (v != null) {
            Log.w(TAG, "[isSimInserted]slotId:" + slotId + ",v:" + v);
            return v;
        }

        final ITelephony iTel = ITelephony.Stub.asInterface(ServiceManager
                .getService(Context.TELEPHONY_SERVICE));
        boolean isSimInsert = false;
        try {
            if (iTel != null) {
                isSimInsert = iTel.hasIccCardUsingSlotId(slotId);
            }
        } catch (RemoteException e) {
            Log.e(TAG, "[isSimInserted]catch exception:");
            e.printStackTrace();
            isSimInsert = false;
        }

        Log.d(TAG, "[isSimInserted]slotId:" + slotId + ",isSimInsert:" + isSimInsert);

        return isSimInsert;
    }

    /**
     * check PhoneBook State is ready if ready, then return true.
     *
     * @param subId
     * @return
     */
    public static boolean isPhoneBookReady(int subId) {
        Boolean v = (Boolean) getPresetObject(String.valueOf(subId), SIM_KEY_WITHSLOT_PHB_READY);
        if (v != null) {
            Log.w(TAG, "[isPhoneBookReady]subId:" + subId + ",v:" + v);
            return v;
        }

        final ITelephonyEx telephonyEx = ITelephonyEx.Stub.asInterface(ServiceManager
                .getService("phoneEx"));

        if (null == telephonyEx) {
            Log.w(TAG, "[isPhoneBookReady]phoneEx == null");
            return false;
        }

        boolean isPbReady = false;
        try {
            isPbReady = telephonyEx.isPhbReady(subId);
        } catch (RemoteException e) {
            Log.e(TAG, "[isPhoneBookReady]catch exception:");
            e.printStackTrace();
        }

        Log.d(TAG, "[isPhoneBookReady]subId:" + subId + ", isPbReady:" + isPbReady);

        return isPbReady;
    }

    /**
     * [Gemini+] get sim type integer by subId sim type is integer defined in
     * SimCardUtils.SimType
     *
     * @param subId
     * @return SimCardUtils.SimType
     */
    public static int getSimTypeBySubId(int subId) {
        Integer v = (Integer) getPresetObject(String.valueOf(subId), SIM_KEY_WITHSLOT_SIM_TYPE);
        if (v != null) {
            Log.w(TAG, "[getSimTypeBySubId]subId:" + subId + ",v:" + v);
            return v;
        }
        int simType = -1;

        final ITelephonyEx iTel = ITelephonyEx.Stub.asInterface(ServiceManager
                .getService(Context.TELEPHONY_SERVICE_EX));
        if (iTel == null) {
            Log.w(TAG, "[getSimTypeBySubId]iTel == null");
            return simType;
        }

        try {
            String iccCardType = iTel.getIccCardType(subId);
            if (SimType.SIM_TYPE_USIM_TAG.equals(iccCardType)) {
                simType = SimType.SIM_TYPE_USIM;
            } else if (SimType.SIM_TYPE_RUIM_TAG.equals(iccCardType)) {
                simType = SimType.SIM_TYPE_RUIM;
            } else if (SimType.SIM_TYPE_SIM_TAG.equals(iccCardType)) {
                simType = SimType.SIM_TYPE_SIM;
            } else if (SimType.SIM_TYPE_CSIM_TAG.equals(iccCardType)) {
                simType = SimType.SIM_TYPE_CSIM;
            }
        } catch (RemoteException e) {
            Log.e(TAG, "[getSimTypeBySubId]catch exception:");
            e.printStackTrace();
        }

        Log.d(TAG, "[getSimTypeBySubId]subId:" + subId + ",simType:" + simType);

        return simType;
    }

    public static String getIccCardType(int subId) {
        final ITelephonyEx iTel = ITelephonyEx.Stub.asInterface(ServiceManager
                .getService(Context.TELEPHONY_SERVICE_EX));
        if (iTel == null) {
            Log.w(TAG, "[getIccCardType]iTel == null");
            return null;
        }

        String iccCardType = null;
        try {
            iccCardType = iTel.getIccCardType(subId);
        } catch (RemoteException e) {
            Log.e(TAG, "[getIccCardType]catch exception:");
            e.printStackTrace();
        }

        Log.d(TAG, "[getIccCardType]subId:" + subId + ",iccCardType:" + iccCardType);

        return iccCardType;
    }

    /**
     * [Gemini+] check whether a slot is insert a usim card
     *
     * @param subId
     * @return true if it is usim card
     */
    public static boolean isUsimType(int subId) {
        Boolean v = (Boolean) getPresetObject(String.valueOf(subId), SIM_KEY_WITHSLOT_IS_USIM);
        if (v != null) {
            Log.w(TAG, "[isSimUsimType]subId:" + subId + ",v:" + v);
            return v;
        }

        boolean isUsim = false;
        final ITelephonyEx iTel = ITelephonyEx.Stub.asInterface(ServiceManager
                .getService(Context.TELEPHONY_SERVICE_EX));
        if (iTel == null) {
            Log.w(TAG, "[isSimUsimType]iTel == null");
            return isUsim;
        }

        try {
            if (SimType.SIM_TYPE_USIM_TAG.equals(iTel.getIccCardType(subId))) {
                isUsim = true;
            }
        } catch (RemoteException e) {
            Log.e(TAG, "[isSimUsimType]catch exception:");
            e.printStackTrace();
        }

        Log.d(TAG, "[isSimUsimType]subId:" + subId + ",isUsim:" + isUsim);

        return isUsim;
    }

    /**
     * [Gemini+] check whether a slot is insert a usim or csim card
     *
     * @param subId
     * @return true if it is usim or csim card
     */
    public static boolean isUsimOrCsimType(int subId) {
        Boolean v = (Boolean) getPresetObject(String.valueOf(subId), SIM_KEY_WITHSLOT_IS_USIM);
        if (v != null) {
            Log.w(TAG, "[isUsimOrCsimType]subId:" + subId + ",v:" + v);
            return v;
        }

        boolean isUsimOrCsim = false;
        final ITelephonyEx iTel = ITelephonyEx.Stub.asInterface(ServiceManager
                .getService(Context.TELEPHONY_SERVICE_EX));
        if (iTel == null) {
            Log.w(TAG, "[isUsimOrCsimType]iTel == null");
            return isUsimOrCsim;
        }

        try {
            if (SimType.SIM_TYPE_USIM_TAG.equals(iTel.getIccCardType(subId))
                    || SimType.SIM_TYPE_CSIM_TAG.equals(iTel.getIccCardType(subId))) {
                isUsimOrCsim = true;
            }
        } catch (RemoteException e) {
            Log.e(TAG, "[isUsimOrCsimType]catch exception:");
            e.printStackTrace();
        }

        Log.d(TAG, "[isUsimOrCsimType]subId:" + subId + ",isUsimOrCsim:" + isUsimOrCsim);

        return isUsimOrCsim;
    }

    /**
     * M: [Gemini+] not only ready, but also idle for all sim operations its
     * requirement is: 1. iccCard is insert 2. radio is on 3. FDN is off 4. PHB
     * is ready 5. simstate is ready 6. simService is not running
     *
     * @param slotId
     *            the slotId to check
     * @return true if idle
     */
    public static boolean isSimStateIdle(int subId) {
        Log.i(TAG, "[isSimStateIdle] subId: " + subId);
        if (!SubInfoUtils.checkSubscriber(subId)) {
            return false;
        }
        // /change for SIM Service Refactoring
        boolean isSimServiceRunning = SimServiceUtils.isServiceRunning(subId);
        Log.i(TAG, "[isSimStateIdle], isSimServiceRunning = " + isSimServiceRunning);
        return isPhoneBookReady(subId) && !isSimServiceRunning;
    }

    /**
     * M: change for CR ALPS00707504 & ALPS00721348 @ { remove condition about
     * isSimServiceRunningOnSlot
     */
    public static boolean isSimReady(int subId) {
        boolean isPhoneBookReady = isPhoneBookReady(subId);
        Log.i(TAG, "[isSimReady] isPhoneBookReady=" + isPhoneBookReady);
        return isPhoneBookReady;
    }

    /** @ } */

    /**
     * M: [Gemini+] wrapper gemini & common API
     *
     * @param slotId
     * @return
     */
    public static int getSimIndicatorState(int slotId) {
        Integer v = (Integer) getPresetObject(String.valueOf(slotId),
                SIM_KEY_WITHSLOT_GET_SIM_INDICATOR_STATE);
        if (v != null) {
            Log.w(TAG, "[getSimIndicatorState]slotId:" + slotId + ",v:" + v);
            return v;
        }

        final ITelephony iTel = ITelephony.Stub.asInterface(ServiceManager
                .getService(Context.TELEPHONY_SERVICE));
        final ITelephonyEx iTelEx = ITelephonyEx.Stub.asInterface(ServiceManager
                .getService(Context.TELEPHONY_SERVICE_EX));
        if (iTel == null) {
            Log.w(TAG, "[getSimIndicatorState]iTel is null.");
            return -1;
        }

        int simIndicatorState = -1;
        Log.d(TAG, "[getSimIndicatorState]slotId:" + slotId + "|simIndicatorState:"
                + simIndicatorState);

        return simIndicatorState;
    }

    /**
     * M: [Gemini+] wrapper gemini & common API
     *
     * @param input
     * @param subId
     * @return
     */
    public static boolean handlePinMmi(String input, int subId) {
        // /M:Lego Sim API Refactoring
        final ITelephony phone = ITelephony.Stub.asInterface(ServiceManager.checkService("phone"));
        if (phone == null) {
            Log.w(TAG, "[handlePinMmi] fail to get phone for subId " + subId);
            return false;
        }
        boolean isHandled;
        try {
            isHandled = phone.handlePinMmiForSubscriber(subId, input);
        } catch (RemoteException e) {
            Log.e(TAG, "[handlePinMmi]exception : ");
            e.printStackTrace();
            isHandled = false;
        }
        Log.d(TAG, "[handlePinMmi]subId:" + subId + "|input:" + input + "|isHandled:"
                + isHandled);

        return isHandled;
    }

    /** @ } */

    /**
     * Check that whether the phone book is ready only
     *
     * @param context
     *            the caller's context.
     * @param subId
     *            the slot to check.
     * @return true the phb is ready false the phb is not ready
     */
    public static boolean isPhoneBookReady(Context context, int subId) {
        boolean hitError = false;
        if (!isPhoneBookReady(subId)) {
            hitError = true;
        }
        if (context == null) {
            Log.w(TAG, "[isPhoneBookReady] context is null,subId:" + subId);
        }
        if (hitError && context != null) {
            Log.d(TAG, "[isPhoneBookReady] hitError=" + hitError);
        }
        return !hitError;
    }

    /**
     * Check subid and return the sim type value.
     *
     * @param subId
     *            The sim card subid.
     * @return sim type string value.
     */
    public static String getSimTypeTagBySubId(int subId) {
        int simType = getSimTypeBySubId(subId);
        String value;
        switch (simType) {
        case SimType.SIM_TYPE_SIM:
            value = "SIM";
            break;
        case SimType.SIM_TYPE_USIM:
            value = "USIM";
            break;
        case SimType.SIM_TYPE_RUIM:
            value = "UIM";
            break;
        case SimType.SIM_TYPE_CSIM:
            value = "UIM";
            break;
        default:
            value = "UNKNOWN";
            break;
        }
        Log.d(TAG, "[getSimTypeTagBySubId] simType=" + simType + " | subId : " + subId
                + " | value : " + value);

        return value;
    }

    /**
     * For test
     */
    private static HashMap<String, ContentValues> sPresetSimData = null;

    private static Object getPresetObject(String key1, String key2) {
        if (sPresetSimData != null) {
            ContentValues values = sPresetSimData.get(key1);
            if (values != null) {
                Object v = values.get(key2);
                if (v != null) {
                    return v;
                }
            }
        }

        return null;
    }

    private static final String NO_SLOT = String.valueOf(-1);
    private static final String SIM_KEY_WITHSLOT_PUK_REQUEST = "isSimPukRequest";
    private static final String SIM_KEY_WITHSLOT_PIN_REQUEST = "isSimPinRequest";
    private static final String SIM_KEY_WITHSLOT_STATE_READY = "isSimStateReady";
    private static final String SIM_KEY_WITHSLOT_SIM_INSERTED = "isSimInserted";
    private static final String SIM_KEY_WITHSLOT_FDN_ENABLED = "isFdnEnabed";
    private static final String SIM_KEY_WITHSLOT_SET_RADIO_ON = "isSetRadioOn";
    private static final String SIM_KEY_WITHSLOT_PHB_READY = "isPhoneBookReady";
    private static final String SIM_KEY_WITHSLOT_SIM_TYPE = "getSimTypeBySlot";
    private static final String SIM_KEY_WITHSLOT_IS_USIM = "isSimUsimType";
    private static final String SIM_KEY_SIMINFO_READY = "isSimInfoReady";
    private static final String SIM_KEY_WITHSLOT_RADIO_ON = "isRadioOn";
    private static final String SIM_KEY_WITHSLOT_HAS_ICC_CARD = "hasIccCard";
    private static final String SIM_KEY_WITHSLOT_GET_SIM_INDICATOR_STATE = "getSimIndicatorState";

    public static void preSetSimData(int slot, Boolean fdnEnabled, Boolean isUsim,
            Boolean phbReady, Boolean pinRequest, Boolean pukRequest, Boolean isRadioOn,
            Boolean isSimInserted, Integer simType, Boolean simStateReady, Boolean simInfoReady) {
        ContentValues value1 = new ContentValues();
        if (fdnEnabled != null) {
            value1.put(SIM_KEY_WITHSLOT_FDN_ENABLED, fdnEnabled);
        }
        if (isUsim != null) {
            value1.put(SIM_KEY_WITHSLOT_IS_USIM, isUsim);
        }
        if (phbReady != null) {
            value1.put(SIM_KEY_WITHSLOT_PHB_READY, phbReady);
        }
        if (pinRequest != null) {
            value1.put(SIM_KEY_WITHSLOT_PIN_REQUEST, pinRequest);
        }
        if (pukRequest != null) {
            value1.put(SIM_KEY_WITHSLOT_PUK_REQUEST, pukRequest);
        }
        if (isRadioOn != null) {
            value1.put(SIM_KEY_WITHSLOT_SET_RADIO_ON, isRadioOn);
        }
        if (isSimInserted != null) {
            value1.put(SIM_KEY_WITHSLOT_SIM_INSERTED, isSimInserted);
        }
        if (simType != null) {
            value1.put(SIM_KEY_WITHSLOT_SIM_TYPE, simType);
        }
        if (simStateReady != null) {
            value1.put(SIM_KEY_WITHSLOT_STATE_READY, simStateReady);
        }
        if (sPresetSimData == null) {
            sPresetSimData = new HashMap<String, ContentValues>();
        }
        if (value1 != null && value1.size() > 0) {
            String key1 = String.valueOf(slot);
            if (sPresetSimData.containsKey(key1)) {
                sPresetSimData.remove(key1);
            }
            sPresetSimData.put(key1, value1);
        }

        ContentValues value2 = new ContentValues();
        if (simInfoReady != null) {
            value2.put(SIM_KEY_SIMINFO_READY, simInfoReady);
        }
        if (value2 != null && value2.size() > 0) {
            if (sPresetSimData.containsKey(NO_SLOT)) {
                sPresetSimData.remove(NO_SLOT);
            }
            sPresetSimData.put(NO_SLOT, value2);
        }
    }
}
