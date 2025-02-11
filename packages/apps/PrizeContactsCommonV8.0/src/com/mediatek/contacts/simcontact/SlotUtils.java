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
package com.mediatek.contacts.simcontact;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import com.android.contacts.common.R;
import com.android.internal.telephony.IIccPhoneBook;
import com.android.internal.telephony.PhoneConstants;

import com.mediatek.contacts.ContactsSystemProperties;
import com.mediatek.contacts.util.ContactsPortableUtils;
import com.mediatek.contacts.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * [Gemini+] slot helper class. all slot related method placed here.
 */
public final class SlotUtils {
    private static final String TAG = "SlotUtils";

    private static final int PHONE_SLOT_NUM = 2; /** PhoneConstants.GEMINI_SIM_NUM */
    private static final int FIRST_SLOT_ID = PhoneConstants.SIM_ID_1;
    private static final int DEFAULT_SUBINFO_COUNT = 0;
    private static HashMap<Integer, PhbInfoWrapper> mActiveUsimPhbInfoMap = null;

    private SlotUtils() {
    }

    /**
     * [Gemini+] each slot information defined in this class
     */
    private static final class SlotInfo {

        private static final String SIM_PHONE_BOOK_SERVICE_NAME_FOR_SINGLE_SLOT = "simphonebook";
        private static final String ICC_SDN_URI_FOR_SINGLE_SLOT = "content://icc/sdn";
        private static final String ICC_ADN_URI_FOR_SINGLE_SLOT = "content://icc/adn";
        private static final String ICC_PBR_URI_FOR_SINGLE_SLOT = "content://icc/pbr";

        int mSlotId;
        Uri mIccUri;
        Uri mIccUsimUri;
        Uri mSdnUri;
        String mVoiceMailNumber;
        String mSimPhoneBookServiceName;
        boolean mIsSlotServiceRunning = false;
        int mResId;
        PhbInfoWrapper mPhbInfo;

        public SlotInfo(int slotId) {
            mSlotId = slotId;
            generateIccUri();
            generateIccUsimUri();
            generateSdnUri();
            generateSimPhoneBook();
            updateVoiceMailNumber();
            generateResId();
            mPhbInfo = new PhbInfoWrapper(slotId);
        }

        /**
         * the resource should be limited to only one string
         */
        private void generateResId() {
            switch (mSlotId) {
            case 0:
                mResId = R.string.sim1;
                break;
            case 1:
                mResId = R.string.sim2;
                break;
            case 2:
                mResId = R.string.sim3;
                break;
            case 3:
                mResId = R.string.sim4;
                break;
            default:
                Log.e(TAG, "[generateResId]no res for slot:" + mSlotId);
            }
        }

        /**
         * slot 0 ==> simphonebook slot 1 ==> simphonebook2
         */
        private void generateSimPhoneBook() {
            mSimPhoneBookServiceName = SIM_PHONE_BOOK_SERVICE_NAME_FOR_SINGLE_SLOT;
            if (mSlotId > 0) {
                mSimPhoneBookServiceName = mSimPhoneBookServiceName + (mSlotId + 1);
            }
        }

        public String getSimPhoneBookServiceName() {
            return mSimPhoneBookServiceName;
        }

        public void updateVoiceMailNumber() {
            if (SlotUtils.isGeminiEnabled()) {
                mVoiceMailNumber = TelephonyManager.getDefault().getVoiceMailNumber(
                        getSubIdBySlot(mSlotId));
            } else {
                mVoiceMailNumber = TelephonyManager.getDefault().getVoiceMailNumber();
            }
        }

        private int getSubIdBySlot(int slotId) {
            Log.d(TAG, "[getSubIdBySlot]SlotId = " + slotId);
            if (slotId < 0 || slotId > 1) {
                return SubInfoUtils.getInvalidSubId();
            }
            int[] subId = SubscriptionManager.getSubId(slotId);
            return (subId != null) ? subId[0] : SubInfoUtils.getInvalidSubId();
        }

        public String getVoiceMailNumber() {
            return mVoiceMailNumber;
        }

        private void generateSdnUri() {
            String str = ICC_SDN_URI_FOR_SINGLE_SLOT;
            if (isGeminiEnabled()) {
                // like:"content://icc/sdn2"
                str += (mSlotId + 1);
            }
            mSdnUri = Uri.parse(str);
        }

        private void generateIccUri() {
            String str = ICC_ADN_URI_FOR_SINGLE_SLOT;
            if (isGeminiEnabled()) {
                // like:"content://icc/adn2"
                str += (mSlotId + 1);
            }
            mIccUri = Uri.parse(str);
        }

        private void generateIccUsimUri() {
            String str = ICC_PBR_URI_FOR_SINGLE_SLOT;
            if (isGeminiEnabled()) {
                // like:"content://icc/pbr2"
                str += (mSlotId + 1);
            }
            mIccUsimUri = Uri.parse(str);
        }

        public void updateSimServiceRunningState(boolean isRunning) {
            Log.i(TAG, "[updateSimServiceRunningState]slotid: " + mSlotId
                    + ",service running state changed from " + mIsSlotServiceRunning + " to "
                    + isRunning);
            mIsSlotServiceRunning = isRunning;
        }

        public boolean isSimServiceRunning() {
            return mIsSlotServiceRunning;
        }

        public Uri getIccUri() {
            return SimCardUtils.isUsimOrCsimType(mSlotId) ? mIccUsimUri : mIccUri;
        }

        public Uri getSdnUri() {
            return mSdnUri;
        }

        public int getResId() {
            return mResId;
        }
    }

    private final static class PhbInfoWrapper {
        private int mSubId = SubInfoUtils.getInvalidSubId();
        private int mUsimGroupMaxNameLength;
        private int mUsimGroupCount;
        private int mUsimAnrCount;
        private int mUsimEmailCount;
        // add for Aas&Sne
        private int mUsimAasCount;
        private int mUsimAasMaxNameLength;
        private int mUsimSneMaxNameLength;
        private boolean mHasSne;
        private boolean mInitialized;
        private static final int INFO_NOT_READY = -1;

        public PhbInfoWrapper(int subId) {
            mSubId = subId;
            resetPhbInfo();
            if (ContactsPortableUtils.MTK_PHONE_BOOK_SUPPORT) {
                refreshPhbInfo();
            }
        }

        private void resetPhbInfo() {
            mUsimGroupMaxNameLength = INFO_NOT_READY;
            mUsimGroupCount = INFO_NOT_READY;
            mUsimAnrCount = INFO_NOT_READY;
            mUsimEmailCount = INFO_NOT_READY;
            mInitialized = false;
            // add for Aas&Sne
            mUsimAasCount = INFO_NOT_READY;
            mUsimAasMaxNameLength = INFO_NOT_READY;
            mUsimSneMaxNameLength = INFO_NOT_READY;
            mHasSne = false;
        }

        private void refreshPhbInfo() {
            Log.i(TAG, "[refreshPhbInfo]refreshing phb info for subId: " + mSubId);
            if (!SimCardUtils.isPhoneBookReady(mSubId)) {
                Log.e(TAG, "[refreshPhbInfo]phb not ready, refresh aborted. slot: " + mSubId);
                mInitialized = false;
                return;
            }
            // /TODO: currently, Usim or Csim is necessary for phb infos.
            if (!SimCardUtils.isUsimOrCsimType(mSubId)) {
                Log.i(TAG, "[refreshPhbInfo]not usim phb, nothing to refresh, keep default "
                            + ", subId: "  + mSubId);
                mInitialized = true;
                return;
            }

            if (!mInitialized) {
                new GetSimInfoTask(this).execute(mSubId);
            }
        }

        private int getUsimGroupMaxNameLength() {
            if (!mInitialized) {
                refreshPhbInfo();
            }
            Log.d(TAG, "[getUsimGroupMaxNameLength] subId = " + mSubId
                    + ",length = " + mUsimGroupMaxNameLength);
            return mUsimGroupMaxNameLength;
        }

        private int getUsimGroupCount() {
            if (!mInitialized) {
                refreshPhbInfo();
            }
            Log.d(TAG, "[getUsimGroupMaxCount] subId = " + mSubId
                    + ", count = " + mUsimGroupCount);
            return mUsimGroupCount;
        }

        private int getUsimAnrCount() {
            if (!mInitialized) {
                refreshPhbInfo();
            }
            Log.d(TAG, "[getUsimAnrCount] subId = " + mSubId
                    + ", count = " + mUsimAnrCount);
            return mUsimAnrCount;
        }

        private int getUsimEmailCount() {
            if (!mInitialized) {
                refreshPhbInfo();
            }
            Log.d(TAG, "[getUsimEmailCount] subId = " + mSubId
                    + ", count = " + mUsimEmailCount);
            return mUsimEmailCount;
        }

        private int getUsimAasCount() {
            if (!mInitialized) {
                refreshPhbInfo();
            }
            Log.d(TAG, "[getUsimAasCount] subId = " + mSubId
                    + ", count = " + mUsimAasCount);
            return mUsimAasCount;
        }

        private boolean usimHasSne() {
            if (!mInitialized) {
                refreshPhbInfo();
            }
            Log.d(TAG, "[usimHasSne] subId = " + mSubId
                    + ", count = " + mHasSne);
            return mHasSne;
        }

        private int getUsimAasMaxNameLength() {
            if (!mInitialized) {
                refreshPhbInfo();
            }
            Log.d(TAG, "[getUsimAasMaxNameLength] subId = " + mSubId
                    + ", length = " + mUsimAasMaxNameLength);
            return mUsimAasMaxNameLength;
        }

        private int getUsimSneMaxNameLength() {
            if (!mInitialized) {
                refreshPhbInfo();
            }
            Log.d(TAG, "[getUsimSneMaxNameLength] subId = " + mSubId
                    + ", length = " + mUsimSneMaxNameLength);
            return mUsimSneMaxNameLength;
        }
    }

    private static final class GetSimInfoTask extends AsyncTask<Integer, Void, PhbInfoWrapper> {
        private PhbInfoWrapper mPhbInfoWrapper;

        public GetSimInfoTask(PhbInfoWrapper phbInfoWrapper) {
            mPhbInfoWrapper = phbInfoWrapper;
        }

        @Override
        protected PhbInfoWrapper doInBackground(Integer... params) {
            final int subId = params[0];
            String serviceName = SubInfoUtils.getPhoneBookServiceName();
            try {
                final IIccPhoneBook iIccPhb = IIccPhoneBook.Stub.asInterface(ServiceManager
                        .getService(serviceName));
                if (iIccPhb == null) {
                    Log.e(TAG, "[GetSimInfoTask] IIccPhoneBook is null!");
                    mPhbInfoWrapper.mInitialized = false;
                    return null;
                }
                mPhbInfoWrapper.mUsimGroupMaxNameLength = iIccPhb.getUsimGrpMaxNameLen(subId);
                mPhbInfoWrapper.mUsimGroupCount = iIccPhb.getUsimGrpMaxCount(subId);
                mPhbInfoWrapper.mUsimAnrCount = iIccPhb.getAnrCount(subId);
                mPhbInfoWrapper.mUsimEmailCount = iIccPhb.getEmailCount(subId);
                mPhbInfoWrapper.mHasSne = iIccPhb.hasSne(subId);
                mPhbInfoWrapper.mUsimAasCount = iIccPhb.getUsimAasMaxCount(subId);
                mPhbInfoWrapper.mUsimAasMaxNameLength = iIccPhb.getUsimAasMaxNameLen(subId);
                mPhbInfoWrapper.mUsimSneMaxNameLength = iIccPhb.getSneRecordLen(subId);
                if (PhbInfoWrapper.INFO_NOT_READY == mPhbInfoWrapper.mUsimGroupMaxNameLength
                        || PhbInfoWrapper.INFO_NOT_READY == mPhbInfoWrapper.mUsimGroupCount
                        || PhbInfoWrapper.INFO_NOT_READY == mPhbInfoWrapper.mUsimAnrCount
                        || PhbInfoWrapper.INFO_NOT_READY == mPhbInfoWrapper.mUsimEmailCount
                        || PhbInfoWrapper.INFO_NOT_READY == mPhbInfoWrapper.mUsimAasCount
                        || PhbInfoWrapper.INFO_NOT_READY == mPhbInfoWrapper.mUsimAasMaxNameLength
                        || PhbInfoWrapper.INFO_NOT_READY == mPhbInfoWrapper.mUsimSneMaxNameLength) {
                    mPhbInfoWrapper.mInitialized = false;
                    Log.d(TAG, "[GetSimInfoTask] Initialize = false. Not all info ready,"
                            + "still need refresh next time");
                } else {
                    mPhbInfoWrapper.mInitialized = true;
                    Log.d(TAG, "[GetSimInfoTask] Initialize = true");
                }
            } catch (RemoteException e) {
                Log.e(TAG, "[refreshPhbInfo]Exception happened when refreshing phb info");
                e.printStackTrace();
                mPhbInfoWrapper.mInitialized = false;
                return null;
            }

            Log.i(TAG, "[refreshPhbInfo]refreshing done,UsimGroupMaxNameLenght = "
                    + mPhbInfoWrapper.mUsimGroupMaxNameLength
                    + ", UsimGroupMaxCount = " + mPhbInfoWrapper.mUsimGroupCount
                    + ", UsimAnrCount = " + mPhbInfoWrapper.mUsimAnrCount
                    + ", UsimEmailCount = " + mPhbInfoWrapper.mUsimEmailCount
                    + ", mHasSne = " + mPhbInfoWrapper.mHasSne
                    + ", mUsimAasMaxCount = " + mPhbInfoWrapper.mUsimAasCount
                    + ", mUsimAasMaxNameLength = " + mPhbInfoWrapper.mUsimAasMaxNameLength
                    + ", mUsimSneMaxNameLength = " + mPhbInfoWrapper.mUsimSneMaxNameLength);
            return mPhbInfoWrapper;
        }
    }

    public static void clearActiveUsimPhbInfoMap() {
        Log.d(TAG, "clearActiveUsimPhbInfoMap");
        mActiveUsimPhbInfoMap = null;
    }

    public static HashMap<Integer, PhbInfoWrapper> getActiveUsimPhbInfoMap() {
        if (mActiveUsimPhbInfoMap == null) {
            mActiveUsimPhbInfoMap = new HashMap<Integer, PhbInfoWrapper>();
            List<SubscriptionInfo> subscriptionInfoList = SubInfoUtils.getActivatedSubInfoList();
            Log.d(TAG, "[getActiveUsimPhbInfoMap] subscriptionInfoList: " + subscriptionInfoList);
            if (subscriptionInfoList != null && subscriptionInfoList.size() > 0) {
                for (SubscriptionInfo subscriptionInfo : subscriptionInfoList) {
                    mActiveUsimPhbInfoMap.put(subscriptionInfo.getSubscriptionId(),
                            new PhbInfoWrapper(subscriptionInfo.getSubscriptionId()));
                }
            }
        }
        return mActiveUsimPhbInfoMap;
    }

    public static void refreshActiveUsimPhbInfoMap(Boolean isPhbReady, Integer subId) {
        Log.i(TAG, "[refreshActiveUsimPhbInfoMap] subId: " + subId + ", isPhbReady: " +
                isPhbReady + ",mActiveUsimPhbInfoMap: " + mActiveUsimPhbInfoMap);
        if (mActiveUsimPhbInfoMap == null) {
            getActiveUsimPhbInfoMap();
            Log.i(TAG, "[refreshActiveUsimPhbInfoMap] get all PhbInfoMap done," +
                    ",mActiveUsimPhbInfoMap: " + mActiveUsimPhbInfoMap);
            return;
        }
        if (subId < 0) {
            Log.d(TAG, "refreshActiveUsimPhbInfoMap subId wrong");
            return;
        }
        if (isPhbReady) {
            Log.d(TAG, "[refreshActiveUsimPhbInfoMap] phb ready, put subId = " + subId);
            mActiveUsimPhbInfoMap.put(subId, new PhbInfoWrapper(subId));
        } else {
            Log.d(TAG, "[refreshActiveUsimPhbInfoMap] phb not ready, try to remove subId:" + subId);
            if (mActiveUsimPhbInfoMap.containsKey(subId)) {
                Log.d(TAG, "[refreshActiveUsimPhbInfoMap] remove subId: " + subId);
                mActiveUsimPhbInfoMap.remove(subId);
            }
        }
    }

    @SuppressLint("UseSparseArrays")
    private static Map<Integer, SlotInfo> sSlotInfoMap = null;

    public static Map<Integer, SlotInfo> getSlotInfoMap() {
        if (sSlotInfoMap == null) {
            sSlotInfoMap = new HashMap<Integer, SlotInfo>();
            Log.d(TAG, "[SlotUtils] update the phb info");
            for (int i = 0; i < PHONE_SLOT_NUM; i++) {
                int slotId = FIRST_SLOT_ID + i;
                sSlotInfoMap.put(slotId, new SlotInfo(slotId));
            }
        }
        return sSlotInfoMap;
    }

    /**
     * get all slot Ids
     *
     * @return the list contains all slot ids
     */
    public static List<Integer> getAllSlotIds() {
        return new ArrayList<Integer>(getSlotInfoMap().keySet());
    }

    /**
     * [Gemini+] get voice mail number for slot
     *
     * @param slotId
     * @return string
     */
    public static String getVoiceMailNumberForSlot(int slotId) {
        if (isSlotValid(slotId)) {
            SlotInfo slotInfo = getSlotInfoMap().get(slotId);
            if (slotInfo != null) {
                return slotInfo.getVoiceMailNumber();
            } else {
                Log.w(TAG, "[getVoiceMailNumberForSlot],slotInfo is null.");
                return null;
            }
        }

        Log.d(TAG, "[getVoiceMailNumberForSlot] slot " + slotId + " is invalid!");
        return null;
    }

    /**
     * [Gemini+] update the saved voice mail number
     */
    public static void updateVoiceMailNumber() {
        for (SlotInfo slot : getSlotInfoMap().values()) {
            slot.updateVoiceMailNumber();
        }
    }

    /**
     * [Gemini+] get current device total slot count
     *
     * @return count
     */
    public static int getSlotCount() {
        return getSlotInfoMap().size();
    }

    /**
     * [Gemini+] check whether the slot is valid
     *
     * @param slotId
     * @return true if valid
     */
    public static boolean isSlotValid(long slotId) {
        boolean isValid = getSlotInfoMap().containsKey(slotId);
        if (!isValid) {
            Log.w(TAG, "[isSlotValid]slot " + slotId + " is invalid!");
        }
        return isValid;
    }

    /**
     * [Gemini+] slot ids are defined in array like 0, 1, 2, ...
     *
     * @return the first id of all slotIds
     */
    public static int getFirstSlotId() {
        return FIRST_SLOT_ID;
    }

    /**
     * [Gemini+] get an invalid slot id, to indicate that this is not a sim
     * slot.
     *
     * @return negative value
     */
    public static int getNonSlotId() {
        return -1;
    }

    /**
     * [Gemini+] in single card phone, the only slot has a slot id this
     * method to retrieve the id.
     *
     * @return the only slot id of a single card phone
     */
    public static int getSingleSlotId() {
        return FIRST_SLOT_ID;
    }

    /**
     * [Gemini+] get string resource id for the corresponding slot id
     *
     * @param slotId
     * @return
     */
    public static int getResIdForSlot(int slotId) {
        SlotInfo slotInfo = getSlotInfoMap().get(slotId);
        if (slotInfo != null) {
            return slotInfo.getResId();
        } else {
            Log.w(TAG, "[getResIdForSlot],slotId:" + slotId);
            return -1;
        }
    }

    /**
     * [Gemini+] resource is just string like "SIM1", "SIM2"
     *
     * @param resId
     * @return if no slot matches, return NonSlotId
     */
    public static int getSlotIdFromSimResId(int resId) {
        for (int slotId : getAllSlotIds()) {
            if (getSlotInfoMap().get(slotId).mResId == resId) {
                return slotId;
            }
        }
        return getNonSlotId();
    }

    /**
     * [Gemini+] if gemini feature enabled on this device
     *
     * @return
     */
    public static boolean isGeminiEnabled() {
        return ContactsSystemProperties.MTK_GEMINI_SUPPORT;
    }

    public static int getUsimGroupMaxNameLength(int subId) {
        PhbInfoWrapper usimPhbInfo = getActiveUsimPhbInfoMap().get(subId);
        if (usimPhbInfo != null) {
            return usimPhbInfo.getUsimGroupMaxNameLength();
        }
        return -1;
    }

    public static int getUsimGroupMaxCount(int subId) {
        PhbInfoWrapper usimPhbInfo = getActiveUsimPhbInfoMap().get(subId);
        if (null == usimPhbInfo) {
            return DEFAULT_SUBINFO_COUNT;
        }
        int count = usimPhbInfo.getUsimGroupCount();
        if (PhbInfoWrapper.INFO_NOT_READY == count) {
            return DEFAULT_SUBINFO_COUNT;
        }
        return count;
    }

    public static int getUsimAnrCount(int subId) {
        PhbInfoWrapper usimPhbInfo = getActiveUsimPhbInfoMap().get(subId);
        if (null == usimPhbInfo) {
            return DEFAULT_SUBINFO_COUNT;
        }
        int count = usimPhbInfo.getUsimAnrCount();
        if (PhbInfoWrapper.INFO_NOT_READY == count) {
            return DEFAULT_SUBINFO_COUNT;
        }
        return count;
    }

    public static int getUsimEmailCount(int subId) {
        PhbInfoWrapper usimPhbInfo = getActiveUsimPhbInfoMap().get(subId);
        if (null == usimPhbInfo) {
            return DEFAULT_SUBINFO_COUNT;
        }
        int count = usimPhbInfo.getUsimEmailCount();
        if (PhbInfoWrapper.INFO_NOT_READY == count) {
            return DEFAULT_SUBINFO_COUNT;
        }
        return count;
    }

    public static int getUsimAasCount(int subId) {
        PhbInfoWrapper usimPhbInfo = getActiveUsimPhbInfoMap().get(subId);
        if (null == usimPhbInfo) {
            return DEFAULT_SUBINFO_COUNT;
        }
        int count = usimPhbInfo.getUsimAasCount();
        if (PhbInfoWrapper.INFO_NOT_READY == count) {
            return DEFAULT_SUBINFO_COUNT;
        }
        return count;
    }

    public static boolean usimHasSne(int subId) {
        PhbInfoWrapper usimPhbInfo = getActiveUsimPhbInfoMap().get(subId);
        if (usimPhbInfo != null) {
            return usimPhbInfo.usimHasSne();
        }
        return false;
    }

    public static int getUsimAasMaxNameLength(int subId) {
        PhbInfoWrapper usimPhbInfo = getActiveUsimPhbInfoMap().get(subId);
        if (usimPhbInfo != null) {
            return usimPhbInfo.getUsimAasMaxNameLength();
        }
        return -1;
    }

    public static int getUsimSneMaxNameLength(int subId) {
        PhbInfoWrapper usimPhbInfo = getActiveUsimPhbInfoMap().get(subId);
        if (usimPhbInfo != null) {
            return usimPhbInfo.getUsimSneMaxNameLength();
        }
        return -1;
    }

    /**
     * Time Consuming, run in background to refresh the PHB info, read from
     * IccPhb, might access Modem, so, would be time consuming. this must be
     * called, once PHB state changed.
     *
     * @param slotId
     */
    public static void refreshPhbInfoBySlot(int slotId) {
        getSlotInfoMap().get(slotId).mPhbInfo.refreshPhbInfo();
    }

    /**
     * reset the PHB info cache to the un-init state. this state means, any
     * requirement trying to access the phb info, it would re-init immediately.
     *
     * @param slotId
     */
    public static void resetPhbInfoBySlot(int slotId) {
        Log.i(TAG, "[resetPhbInfoBySlot]slotId:" + slotId);
        getSlotInfoMap().get(slotId).mPhbInfo.resetPhbInfo();
    }
}
