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

package com.mediatek.location;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.io.File;
import java.util.Calendar;
import java.util.HashMap;

import com.android.server.location.GnssLocationProvider;
import com.android.server.LocationManagerService;
import com.mediatek.location.NlpUtils;
import com.mediatek.lbsutils.LbsUtils;


public class LocationExt {
    private static final String TAG = "MtkLocationExt";

    public static final int SUPL_CONN_UNAVALIABLE = 0;
    public static final int SUPL_CONN_LOST        = 1;
    public static final int SUPL_CONN_AVAILABLE   = 2;

    private static final boolean DEBUG = LocationManagerService.D;

    private static final int UPDATE_NETWORK_STATE = 4;

    // these need to match GpsLocationFlags enum in gps.h
    //private static final int LOCATION_INVALID = 0;
    private static final int LOCATION_HAS_LAT_LONG = 1;
    //private static final int LOCATION_HAS_ALTITUDE = 2;
    //private static final int LOCATION_HAS_SPEED = 4;
    //private static final int LOCATION_HAS_BEARING = 8;
    //private static final int LOCATION_HAS_ACCURACY = 16;

    /// mtk added deleting aiding data flags
    private static final int GPS_DELETE_HOT_STILL = 0x2000;
    private static final int GPS_DELETE_EPO = 0x4000;

    private static LocationExt sSingleton;

    private final GnssLocationProvider mGnssProvider;
    private final Context mContext;
    private final Handler mGpsHandler;
    private final ConnectivityManager mConnMgr;

    /*mGpsTimeSyncFlag : true: need to check the time sync, false: no need to check the time sync*/
    private boolean mGpsTimeSyncFlag = true;
    /*isEmergencyCallDialed: [true] IMS emergency call is dialed,
    [false] IMS emergency call is ended*/
    private boolean mIsEmergencyCallDialed;
    private int mRouteNetworkType = ConnectivityManager.TYPE_MOBILE_SUPL;
    private GnssSvStatusHolder mGnssSvStatusHolder = new GnssSvStatusHolder();
    private C2kAgpsInterface mAgpsInterface;
    private NlpUtils nlpUtils;
    private String mFeature = Phone.FEATURE_ENABLE_SUPL;
    private AgpsHelper mAgpsHelper; // for supl (framework <--> agpsd.supl)

    //============================================================================================
    // APIs for GnssLocationProvider

    public static synchronized LocationExt getInstance(
            GnssLocationProvider gnssProvider,
            Context context,
            Handler gpsHandler,
            ConnectivityManager connMgr) {
        if (null == sSingleton && null != gnssProvider) {
            sSingleton = new LocationExt(gnssProvider, context, gpsHandler, connMgr);
        }
        return sSingleton;
    }

    public static boolean isEnabled() {
        return (null != sSingleton);
    }

    // Return true to allow sending SuplInit to the native
    public static boolean checkWapSuplInit(Intent intent) {
        if (!isEnabled()) return true;

        boolean ret = sSingleton.isWapPushLegal(intent);
        if (DEBUG) Log.d(TAG, "[agps] WARNING: checkWapSuplInit ret=" + ret);
        return ret;
    }

    public static int deleteAidingData(Bundle extras, int flags) {
        if (!isEnabled()) return flags;
        if (extras != null) {
            if (extras.getBoolean("hot-still")) flags |= GPS_DELETE_HOT_STILL;
            if (extras.getBoolean("epo")) flags |= GPS_DELETE_EPO;
        }
        Log.d(TAG, "deleteAidingData extras:" + extras + "flags:" + flags);
        return flags;
    }

    public static boolean setGpsTimeSyncFlag(boolean flag) {
        if (!isEnabled()) return false;
        sSingleton.mGpsTimeSyncFlag = flag;
        if (DEBUG) Log.d(TAG, "setGpsTimeSyncFlag: " + flag);
        return flag;
    }

    public static void startNavigating(boolean singleShot) {
        setGpsTimeSyncFlag(true);
    }

    public static void doSystemTimeSyncByGps(int flags, long timestamp) {
        if (!isEnabled()) return;
        if (sSingleton.mGpsTimeSyncFlag &&
                (flags & LOCATION_HAS_LAT_LONG) == LOCATION_HAS_LAT_LONG) {
            if (sSingleton.getAutoGpsState()) {
                sSingleton.mGpsTimeSyncFlag = false;
                if (DEBUG) Log.d(TAG, "GPS time sync is enabled");
                if (DEBUG) Log.d(TAG, " ########## Auto-sync time with GPS: timestamp = "
                        + timestamp + " ########## ");
                Calendar c = Calendar.getInstance();
                c.setTimeInMillis(timestamp);
                long when = c.getTimeInMillis();
                if (when / 1000 < Integer.MAX_VALUE) {
                    SystemClock.setCurrentTimeMillis(when);
                }
            } else {
                if (DEBUG) Log.d(TAG, "Auto-sync time with GPS is disabled by user settings!");
                if (DEBUG) Log.d(TAG, "GPS time sync is disabled");
            }
        }
    }

    public static GnssSvStatusHolder getGnssSvStatusHolder() {
        if (!isEnabled()) return null;
        return sSingleton.mGnssSvStatusHolder;
    }

    public static int getRouteNetworkType() {
        if (!isEnabled()) return ConnectivityManager.TYPE_MOBILE_SUPL;
        return sSingleton.mRouteNetworkType;
    }

    public static int doStartUsingNetwork(ConnectivityManager connMgr, int networkType,
            String feature) {
        if (!isEnabled()) {
            return -1;
        } else {
            return sSingleton.doMtkStartUsingNetwork();
        }
    }


    public static int doStopUsingNetwork(ConnectivityManager connMgr, int networkType,
            String feature) {
        if (!isEnabled()) {
            return -1;
        } else {
            sSingleton.doMtkStopUsingNetwork();
            return 0;
        }
    }

    public static int suplConnectionCallback (int state, Network network){
        if (!isEnabled()) {
            return -1;
        } else {
            sSingleton.doMtkSuplConnectionCallback(state, network);
            return 0;
        }
    }

    public static void updateNetworkAvailable(Network network) {
        if (!isEnabled()) {
            return;
        } else {
            sSingleton.doUpdateNetworkAvailable(network);
        }
    }

    //============================================================================================
    // Utilties

    public static boolean isFileExists(String path) {
        File f = new File(path);
        return f.exists();
    }

    public static boolean isESUPL() {
        return isFileExists("/data/agps_supl/isESUPL");
    }

    public static boolean isCtwap() {
        return isFileExists("/data/agps_supl/ctwap");
    }

    //============================================================================================
    // Internal implementation

    private LocationExt(GnssLocationProvider gnssProvider, Context context,
            Handler gpsHandler, ConnectivityManager connMgr) {
        mGnssProvider = gnssProvider;
        mContext = context;
        mGpsHandler = gpsHandler;
        mConnMgr = connMgr;
        mAgpsInterface = new C2kAgpsInterface(connMgr);
        listenForBroadcasts();
        nlpUtils = new NlpUtils(context, mGpsHandler);
        mAgpsHelper = new AgpsHelper(this, context, connMgr);

        // M: init lbs utilities
        LbsUtils lbsUtils = LbsUtils.getInstance(mContext);
        Resources resources = mContext.getResources();
        String[] gmsLpPkgs = resources.getStringArray(
                com.android.internal.R.array.config_locationProviderPackageNames);
        String[] vendorLpPkgs = resources.getStringArray(
                com.mediatek.internal.R.array.config_cnLocationProviderPackageNames);
        lbsUtils.setHandler(gpsHandler);
        lbsUtils.listenPhoneState(gmsLpPkgs);
        lbsUtils.setVendorLpPkgs(vendorLpPkgs);

        // M: init gps provider for E911 NI request
        if (mGnssProvider != null && !isAllowedByUserSettingsLocked(LocationManager.GPS_PROVIDER)) {
            if (DEBUG) Log.d(TAG, "init GPS in location off mode");
            mGnssProvider.enable();
            mGnssProvider.disable();
        }
    }

    private boolean isAllowedByUserSettingsLocked(String provider) {
        // Use system settings
        ContentResolver resolver = mContext.getContentResolver();
        int mCurrentUserId = UserHandle.USER_OWNER;

        return Settings.Secure.isLocationProviderEnabledForUser(resolver, provider, mCurrentUserId);
    }

    private void listenForBroadcasts() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("android.location.agps.EMERGENCY_CALL");
        mContext.registerReceiver(mBroadcastReceiver, intentFilter, null, mGpsHandler);

        //check airplane mode immediatly
        mAgpsInterface.setFlightMode(isAirplaneModeOn());
        intentFilter = new IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        mContext.registerReceiver(mBroadcastReceiver, intentFilter, null, mGpsHandler);
    }

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (DEBUG) Log.d(TAG, "receive broadcast intent, action: " + action);
            if ("android.location.agps.EMERGENCY_CALL".equals(action)) {
                Bundle bundle = intent.getExtras();
                if (bundle != null) {
                    int state = bundle.getInt("EM_Call_State");
                    if (1 == state) {
                        if (DEBUG) Log.d(TAG, "E911 dialed");
                        mIsEmergencyCallDialed = true;
                    } else {
                        if (DEBUG) Log.d(TAG, "E911 ended");
                        mIsEmergencyCallDialed = false;
                    }
                } else {
                    Log.e(TAG, "E911 null bundle");
                }
            } else if (Intent.ACTION_AIRPLANE_MODE_CHANGED.equals(action)) {
                boolean enabled = intent.getBooleanExtra("state", false);
                if (DEBUG) Log.d(TAG, "ACTION_AIRPLANE_MODE_CHANGED enabled =" + enabled);
                mAgpsInterface.setFlightMode(enabled);
            }
        }
    };

    private boolean isWapPushLegal(Intent intent) {
        try {
            String type = intent.getType();
            if (type == null || !type.equals("application/vnd.omaloc-supl-init")) {
                Log.e(TAG, "[agps] ERR: content type is [" + type
                        + "], but we expect [application/vnd.omaloc-supl-init]");
                return false;
            }
            Bundle bundle = intent.getExtras();
            if (bundle == null) {
                Log.e(TAG, "[agps] ERR: wspBundle is null");
                return false;
            }
            HashMap<String, String> wspHeaders = (HashMap<String, String>) bundle.get("wspHeaders");
            if (wspHeaders == null) {
                Log.e(TAG, "[agps] ERR: wspHeader is null");
                return false;
            }
            String appId = wspHeaders.get("X-Wap-Application-Id");
            if (appId == null) {
                Log.e(TAG, "[agps] ERR: appId(X-Wap-Application-Id) is null");
                return false;
            }
            if (!appId.equals("x-oma-application:ulp.ua")) {
                Log.e(TAG, "[agps] ERR: appId is [" + appId
                        + "], but we expect [x-oma-application:ulp.ua]");
                return false;
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean getAutoGpsState() {
        try {
            return Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.AUTO_TIME_GPS) > 0;
        } catch (SettingNotFoundException snfe) {
            return false;
        }
    }

    public class GnssSvStatusHolder {
        // preallocated arrays, to avoid memory allocation in reportStatus()
        public static final int MAX_GNSS_SVS = 256;
        public int mGnssSvs[] = new int[MAX_GNSS_SVS];
        public float mGnssSnrs[] = new float[MAX_GNSS_SVS];
        public float mGnssSvElevations[] = new float[MAX_GNSS_SVS];
        public float mGnssSvAzimuths[] = new float[MAX_GNSS_SVS];
        public boolean mGnssSvEphemeris[] = new boolean[MAX_GNSS_SVS];
        public boolean mGnssSvAlmanac[] = new boolean[MAX_GNSS_SVS];
        public boolean mGnssSvInFix[] = new boolean[MAX_GNSS_SVS];

        public int reportGnssSvStatusStep2(int svCount) {
            if (DEBUG) {
                Log.v(TAG, "GNSS SV count: " + svCount);
                for (int i = 0; i < svCount; i++) {
                    Log.v(TAG, "sv: " + mGnssSvs[i] +
                            " snr: " + mGnssSnrs[i] / 10 +
                            " elev: " + mGnssSvElevations[i] +
                            " azimuth: " + mGnssSvAzimuths[i] +
                            ((mGnssSvEphemeris[i]) ? " E" : " ") +
                            ((mGnssSvAlmanac[i]) ? " A" : " ") +
                            ((mGnssSvInFix[i]) ? " U" : " "));
                }
            }
            int svFixCount = 0;
            for (boolean value : mGnssSvInFix) {
                if (value) {
                    svFixCount++;
                }
            }
            return svFixCount;
        }

        public boolean reportGnssSvStatusStep3(boolean navigating, int gpsStatus,
                long lastFixTime, long recentFixTimeout) {
            if (navigating && gpsStatus == LocationProvider.AVAILABLE && lastFixTime > 0 &&
                SystemClock.elapsedRealtime() - lastFixTime > recentFixTimeout) {
                // send an intent to notify that the GPS is no longer receiving fixes.
                Intent intent = new Intent(LocationManager.GPS_FIX_CHANGE_ACTION);
                intent.putExtra(LocationManager.EXTRA_GPS_ENABLED, false);
                mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
                return true;
            }
            return false;
        }
    }

    boolean isEmergencyCallDialed() {
        return (isFileExists("/data/agps_supl/isEmergencyCallDialed") ||
                mIsEmergencyCallDialed);
    }

    boolean hasIccCard() {
        TelephonyManager tpMgr = (TelephonyManager)
                mContext.getSystemService(Context.TELEPHONY_SERVICE);
        if (tpMgr != null) {
            return tpMgr.hasIccCard();
        }
        return false;
    }

    boolean isAirplaneModeOn() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.AIRPLANE_MODE_ON, 0) == 1;
    }

    private int doMtkStartUsingNetwork() {
        String feature = Phone.FEATURE_ENABLE_SUPL;
        mRouteNetworkType = ConnectivityManager.TYPE_MOBILE_SUPL;

        TelephonyManager phone = (TelephonyManager)
                mContext.getSystemService(Context.TELEPHONY_SERVICE);

        //IR92 requirements for emergency location
        int phoneNetwokrType = phone.getNetworkType();

        Log.d(TAG, "[agps] WARNING: GnssLocationProvider  phoneNetwokrType=[" +
            phoneNetwokrType + "] isESUPL=[" + isESUPL() + "] isEmergencyCallDialed=["
            + isEmergencyCallDialed() + "]");
        if (phoneNetwokrType == TelephonyManager.NETWORK_TYPE_LTE && isESUPL()) {
            if (isEmergencyCallDialed()) {
                feature = Phone.FEATURE_ENABLE_EMERGENCY;
                mRouteNetworkType = ConnectivityManager.TYPE_MOBILE_EMERGENCY;
            } else {
                feature = Phone.FEATURE_ENABLE_IMS;
                mRouteNetworkType = ConnectivityManager.TYPE_MOBILE_IMS;
            }
        } else if (isCtwap()) {
            feature = Phone.FEATURE_ENABLE_MMS;
            mRouteNetworkType = ConnectivityManager.TYPE_MOBILE_MMS;
            mAgpsInterface.requestNetwork();
            return PhoneConstants.APN_REQUEST_STARTED;
        }

        if ((Phone.FEATURE_ENABLE_SUPL == feature && hasIccCard() == false)
            || isAirplaneModeOn()) {
            Log.d(TAG, "[agps] APN_REQUEST_FAILED: hasIccCard=" +
                    hasIccCard() + " isAirplaneModeOn="
                    + isAirplaneModeOn());
            return PhoneConstants.APN_REQUEST_FAILED;
        }

        mFeature = feature;
        return mConnMgr.startUsingNetworkFeature(ConnectivityManager.TYPE_MOBILE, feature);
    }

    private void doMtkStopUsingNetwork() {
        if (mRouteNetworkType == ConnectivityManager.TYPE_MOBILE_MMS) {
            mAgpsInterface.releaseNetwork();
        } else {
            mConnMgr.stopUsingNetworkFeature(ConnectivityManager.TYPE_MOBILE, mFeature);
        }
    }

    private void doMtkSuplConnectionCallback(int state, Network network) {
        mAgpsInterface.doMtkSuplConnectionCallback(state, network);
    }

    private void doUpdateNetworkAvailable(Network network) {
        mGpsHandler.obtainMessage(UPDATE_NETWORK_STATE, 0, 0, network).sendToTarget();
    }
}
