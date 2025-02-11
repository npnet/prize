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
 * limitations under the License.
 */
package com.android.systemui.statusbar.policy;

import android.content.Context;
import android.content.Intent;
import android.net.NetworkCapabilities;
import android.os.Looper;
import android.os.SystemProperties;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;

import com.android.ims.ImsManager;
import com.android.ims.ImsConfig;
import com.android.ims.ImsException;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.cdma.EriInfo;
import com.android.systemui.R;
import com.android.systemui.statusbar.policy.NetworkController.IconState;
import com.android.systemui.statusbar.policy.NetworkController.SignalCallback;
import com.android.systemui.statusbar.policy.NetworkControllerImpl.Config;
import com.android.systemui.statusbar.policy.NetworkControllerImpl.SubscriptionDefaults;
import com.mediatek.systemui.ext.IMobileIconExt;
import com.mediatek.systemui.ext.ISystemUIStatusBarExt;
import com.mediatek.systemui.PluginManager;
import com.mediatek.systemui.statusbar.networktype.NetworkTypeUtils;

import com.mediatek.telephony.TelephonyManagerEx;

import java.io.PrintWriter;
import java.util.BitSet;
import java.util.Objects;
//add for statusbar inverse. prize-linkh-20150903
import com.mediatek.common.prizeoption.PrizeOption;
import com.android.systemui.statusbar.phone.PrizeStatusBarStyleListener;
import android.app.StatusBarManager;
import com.android.systemui.statusbar.phone.FeatureOption;


public class MobileSignalController extends SignalController<
        MobileSignalController.MobileState, MobileSignalController.MobileIconGroup> implements PrizeStatusBarStyleListener{
    private static final String TAG = "MobileSignalController";

    private final TelephonyManager mPhone;
    private final SubscriptionDefaults mDefaults;
    private final String mNetworkNameDefault;
    private final String mNetworkNameSeparator;
    @VisibleForTesting
    final PhoneStateListener mPhoneStateListener;
    // Save entire info for logging, we only use the id.
    /// M: Fix bug ALPS02416794
    /*final*/ SubscriptionInfo mSubscriptionInfo;

    // @VisibleForDemoMode
    final SparseArray<MobileIconGroup> mNetworkToIconLookup;

    // Since some pieces of the phone state are interdependent we store it locally,
    // this could potentially become part of MobileState for simplification/complication
    // of code.
    private int mDataNetType = TelephonyManager.NETWORK_TYPE_UNKNOWN;
    private int mDataState = TelephonyManager.DATA_DISCONNECTED;
    private ServiceState mServiceState;
    private SignalStrength mSignalStrength;
    private MobileIconGroup mDefaultIcons;
    private Config mConfig;

    /// M: Add for Plugin feature. @ {
    private IMobileIconExt mMobileIconExt;
    private ISystemUIStatusBarExt mStatusBarExt;
    /// @ }

    // TODO: Reduce number of vars passed in, if we have the NetworkController, probably don't
    // need listener lists anymore.
    public MobileSignalController(Context context, Config config, boolean hasMobileData,
            TelephonyManager phone, CallbackHandler callbackHandler,
            NetworkControllerImpl networkController, SubscriptionInfo info,
            SubscriptionDefaults defaults, Looper receiverLooper) {
        super("MobileSignalController(" + info.getSubscriptionId() + ")", context,
                NetworkCapabilities.TRANSPORT_CELLULAR, callbackHandler,
                networkController);
        mNetworkToIconLookup = new SparseArray<>();
        mConfig = config;
        mPhone = phone;
        mDefaults = defaults;
        mSubscriptionInfo = info;
        /// M: Init plugin @ {
        mMobileIconExt = PluginManager.getMobileIconExt(context);
        mStatusBarExt = PluginManager.getSystemUIStatusBarExt(context);
        /// @ }
        mPhoneStateListener = new MobilePhoneStateListener(info.getSubscriptionId(),
                receiverLooper);
        mNetworkNameSeparator = getStringIfExists(R.string.status_bar_network_name_separator);
        mNetworkNameDefault = getStringIfExists(
                com.android.internal.R.string.lockscreen_carrier_default);

        mapIconSets();

        String networkName = info.getCarrierName() != null ? info.getCarrierName().toString()
                : mNetworkNameDefault;
        mLastState.networkName = mCurrentState.networkName = networkName;
        mLastState.networkNameData = mCurrentState.networkNameData = networkName;
        mLastState.enabled = mCurrentState.enabled = hasMobileData;
        mLastState.iconGroup = mCurrentState.iconGroup = mDefaultIcons;
        /// M: Support volte icon
        initImsRegisterState();
        // Get initial data sim state.
        updateDataSim();
    }

    /// M: Support volte icon @{
    private void initImsRegisterState(){
        int phoneId = SubscriptionManager.getPhoneId(mSubscriptionInfo.getSubscriptionId());
        try {
            boolean imsRegStatus = ImsManager
                    .getInstance(mContext, phoneId).getImsRegInfo();
            mCurrentState.imsRegState = imsRegStatus
                    ? ServiceState.STATE_IN_SERVICE : ServiceState.STATE_OUT_OF_SERVICE;
            Log.d(mTag, "init imsRegState:" + mCurrentState.imsRegState
                    + ",phoneId:" + phoneId);
        } catch (ImsException ex) {
            Log.e(mTag, "Fail to get Ims Status");
        }
    }
    /// @}
    public void setConfiguration(Config config) {
        mConfig = config;
        mapIconSets();
        updateTelephony();
    }

    public int getDataContentDescription() {
        return getIcons().mDataContentDescription;
    }

    public void setAirplaneMode(boolean airplaneMode) {
        mCurrentState.airplaneMode = airplaneMode;
        notifyListenersIfNecessary();
    }

    public void setUserSetupComplete(boolean userSetup) {
        mCurrentState.userSetup = userSetup;
        notifyListenersIfNecessary();
    }

    @Override
    public void updateConnectivity(BitSet connectedTransports, BitSet validatedTransports) {
        boolean isValidated = validatedTransports.get(mTransportType);
        mCurrentState.isDefault =
                connectedTransports.get(mTransportType) &&
                // M: Add one more condition to judge whether the cellular connection is this subid
                mNetworkController.isCellularConnected(mSubscriptionInfo.getSubscriptionId());

        // Only show this as not having connectivity if we are default.
        mCurrentState.inetCondition = (isValidated || !mCurrentState.isDefault) ? 1 : 0;
        Log.d(mTag,"mCurrentState.inetCondition = " + mCurrentState.inetCondition);
        /// M: Disable inetCondition check as this condition is not sufficient in some cases.
        /// So always set it is in net with value 1. @ {
        mCurrentState.inetCondition =
                mMobileIconExt.customizeMobileNetCondition(mCurrentState.inetCondition);
        /// @}
        notifyListenersIfNecessary();
    }

    public void setCarrierNetworkChangeMode(boolean carrierNetworkChangeMode) {
        mCurrentState.carrierNetworkChangeMode = carrierNetworkChangeMode;
        updateTelephony();
    }

    /**
     * Start listening for phone state changes.
     */
    public void registerListener() {
        mPhone.listen(mPhoneStateListener,
                PhoneStateListener.LISTEN_SERVICE_STATE
                        | PhoneStateListener.LISTEN_SIGNAL_STRENGTHS
                        | PhoneStateListener.LISTEN_CALL_STATE
                        | PhoneStateListener.LISTEN_DATA_CONNECTION_STATE
                        | PhoneStateListener.LISTEN_DATA_ACTIVITY
                        | PhoneStateListener.LISTEN_CARRIER_NETWORK_CHANGE);
        mStatusBarExt.registerOpStateListener();
    }

    /**
     * Stop listening for phone state changes.
     */
    public void unregisterListener() {
        mPhone.listen(mPhoneStateListener, 0);
    }

    /**
     * Produce a mapping of data network types to icon groups for simple and quick use in
     * updateTelephony.
     */
    private void mapIconSets() {
        mNetworkToIconLookup.clear();

        mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_EVDO_0, TelephonyIcons.THREE_G);
        mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_EVDO_A, TelephonyIcons.THREE_G);
        mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_EVDO_B, TelephonyIcons.THREE_G);
        mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_EHRPD, TelephonyIcons.THREE_G);
        mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_UMTS, TelephonyIcons.THREE_G);

        if (!mConfig.showAtLeast3G) {
            mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_UNKNOWN,
                    TelephonyIcons.UNKNOWN);
            mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_EDGE, TelephonyIcons.E);
            mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_CDMA, TelephonyIcons.ONE_X);
            mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_1xRTT, TelephonyIcons.ONE_X);

            mDefaultIcons = TelephonyIcons.G;
        } else {
            mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_UNKNOWN,
                    TelephonyIcons.THREE_G);
            mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_EDGE,
                    TelephonyIcons.THREE_G);
            mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_CDMA,
                    TelephonyIcons.THREE_G);
            mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_1xRTT,
                    TelephonyIcons.THREE_G);
            mDefaultIcons = TelephonyIcons.THREE_G;
        }

        MobileIconGroup hGroup = TelephonyIcons.THREE_G;
        if (mConfig.hspaDataDistinguishable) {
            hGroup = TelephonyIcons.H;
        }
        mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_HSDPA, hGroup);
        mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_HSUPA, hGroup);
        mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_HSPA, hGroup);
        mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_HSPAP, hGroup);

        if (mConfig.show4gForLte) {
            mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_LTE, TelephonyIcons.FOUR_G);
            if (mConfig.hideLtePlus) {
                mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_LTE_CA,
                        TelephonyIcons.FOUR_G);
            } else {
                mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_LTE_CA,
                        TelephonyIcons.FOUR_G_PLUS);
            }
        } else {
            mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_LTE, TelephonyIcons.LTE);
            if (mConfig.hideLtePlus) {
                mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_LTE_CA,
                        TelephonyIcons.LTE);
            } else {
                mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_LTE_CA,
                        TelephonyIcons.LTE_PLUS);
            }
        }
        mNetworkToIconLookup.put(TelephonyManager.NETWORK_TYPE_IWLAN, TelephonyIcons.WFC);
    }

    @Override
    public void notifyListeners(SignalCallback callback) {
        MobileIconGroup icons = getIcons();

        String contentDescription = getStringIfExists(getContentDescription());
        String dataContentDescription = getStringIfExists(icons.mDataContentDescription);
        final boolean dataDisabled = mCurrentState.iconGroup == TelephonyIcons.DATA_DISABLED
                && mCurrentState.userSetup;

        /// M: Customize the signal strength icon id. @ {
        int iconId = getCurrentIconId();
        iconId = mStatusBarExt.getCustomizeSignalStrengthIcon(
                    mSubscriptionInfo.getSubscriptionId(),
                    iconId,
                    mSignalStrength,
                    mDataNetType,
                    mServiceState);
        /// @ }

        // Show icon in QS when we are connected or need to show roaming or data is disabled.
        boolean showDataIcon = mCurrentState.dataConnected
                || mCurrentState.iconGroup == TelephonyIcons.ROAMING
                || dataDisabled;
        IconState statusIcon = new IconState(mCurrentState.enabled && !mCurrentState.airplaneMode,
                PluginManager.isDefaultSystemUIStatusBarExt() ? getCurrentIconId() : iconId, contentDescription);
		/*PRIZE-get statusIconGray-liufan-2016-04-20-start*/
        IconState statusIconGray = new IconState(mCurrentState.enabled && !mCurrentState.airplaneMode,
                getCurrentGrayIconId(), contentDescription);
		/*PRIZE-get statusIconGray-liufan-2016-04-20-end*/

        int qsTypeIcon = 0;
        IconState qsIcon = null;
        String description = null;
        // Only send data sim callbacks to QS.
        if (mCurrentState.dataSim) {
            qsTypeIcon = showDataIcon ? icons.mQsDataType : 0;
            qsIcon = new IconState(mCurrentState.enabled
                    && !mCurrentState.isEmergency, getQsCurrentIconId(), contentDescription);
            description = mCurrentState.isEmergency ? null : mCurrentState.networkName;
        }
        boolean activityIn = mCurrentState.dataConnected
                        && !mCurrentState.carrierNetworkChangeMode
                        && mCurrentState.activityIn;
        boolean activityOut = mCurrentState.dataConnected
                        && !mCurrentState.carrierNetworkChangeMode
                        && mCurrentState.activityOut;
        showDataIcon &= mCurrentState.isDefault
                || mCurrentState.iconGroup == TelephonyIcons.ROAMING
                || dataDisabled;
        int typeIcon = showDataIcon ? icons.mDataType : 0;
        /** M: Support [Network Type on StatusBar], change the implement methods.
          * Get the network icon base on service state.
          * Add one more parameter for network type.
          * @ { **/
        int networkIcon = mCurrentState.networkIcon;

        /// M: Support volte icon.Bug fix when airplane mode is on go to hide volte icon
        int volteIcon = mCurrentState.airplaneMode && !isWfcEnable()
                ? 0 : mCurrentState.volteIcon;

        /// M: when data disabled, common show data icon as x, but op do not need show it @ {
        mStatusBarExt.isDataDisabled(mSubscriptionInfo.getSubscriptionId(), dataDisabled);
        /// @ }

        /// M: Customize the data type icon id. @ {
        typeIcon = mStatusBarExt.getDataTypeIcon(
                        mSubscriptionInfo.getSubscriptionId(),
                        typeIcon,
                        mDataNetType,
                        mCurrentState.dataConnected ? TelephonyManager.DATA_CONNECTED :
                            TelephonyManager.DATA_DISCONNECTED,
                        mServiceState);
        /// @ }
        /// M: Customize the network type icon id. @ {
        networkIcon = mStatusBarExt.getNetworkTypeIcon(
                        mSubscriptionInfo.getSubscriptionId(),
                        networkIcon,
                        mDataNetType,
                        mServiceState);
        /// @ }

        callback.setMobileDataIndicators(statusIcon, statusIconGray, qsIcon, typeIcon, networkIcon, volteIcon,
                qsTypeIcon, activityIn, activityOut, dataContentDescription, description,
                icons.mIsWide, mSubscriptionInfo.getSubscriptionId());
        /** @ }*/

        /// M: update plmn label @{
        mNetworkController.refreshPlmnCarrierLabel();
        /// @}
    }

    public void notifyListenersForInverse() {
        MobileIconGroup icons = getIcons();

        String contentDescription = getStringIfExists(getContentDescription());
        String dataContentDescription = getStringIfExists(icons.mDataContentDescription);
        final boolean dataDisabled = mCurrentState.iconGroup == TelephonyIcons.DATA_DISABLED
                && mCurrentState.userSetup;

        /// M: Customize the signal strength icon id. @ {
        int iconId = getCurrentIconId();
        iconId = mStatusBarExt.getCustomizeSignalStrengthIcon(
                    mSubscriptionInfo.getSubscriptionId(),
                    iconId,
                    mSignalStrength,
                    mDataNetType,
                    mServiceState);
        /// @ }

        // Show icon in QS when we are connected or need to show roaming or data is disabled.
        boolean showDataIcon = mCurrentState.dataConnected
                || mCurrentState.iconGroup == TelephonyIcons.ROAMING
                || dataDisabled;
        IconState statusIcon = new IconState(mCurrentState.enabled && !mCurrentState.airplaneMode,
                iconId, contentDescription);
		/*PRIZE-get statusIconGray-liufan-2016-04-20-start*/
        IconState statusIconGray = new IconState(mCurrentState.enabled && !mCurrentState.airplaneMode,
                getCurrentGrayIconId(), contentDescription);
		/*PRIZE-get statusIconGray-liufan-2016-04-20-end*/

        int qsTypeIcon = 0;
        IconState qsIcon = null;
        String description = null;
        // Only send data sim callbacks to QS.
        if (mCurrentState.dataSim) {
            qsTypeIcon = showDataIcon ? icons.mQsDataType : 0;
            qsIcon = new IconState(mCurrentState.enabled
                    && !mCurrentState.isEmergency, getQsCurrentIconId(), contentDescription);
            description = mCurrentState.isEmergency ? null : mCurrentState.networkName;
        }
        boolean activityIn = mCurrentState.dataConnected
                        && !mCurrentState.carrierNetworkChangeMode
                        && mCurrentState.activityIn;
        boolean activityOut = mCurrentState.dataConnected
                        && !mCurrentState.carrierNetworkChangeMode
                        && mCurrentState.activityOut;
        showDataIcon &= mCurrentState.isDefault
                || mCurrentState.iconGroup == TelephonyIcons.ROAMING
                || dataDisabled;
        int typeIcon = showDataIcon ? icons.mDataType : 0;
        /** M: Support [Network Type on StatusBar], change the implement methods.
          * Get the network icon base on service state.
          * Add one more parameter for network type.
          * @ { **/
        int networkIcon = mCurrentState.networkIcon;

        /// M: Support volte icon.Bug fix when airplane mode is on go to hide volte icon
        int volteIcon = mCurrentState.airplaneMode && !isWfcEnable()
                ? 0 : mCurrentState.volteIcon;

        /// M: when data disabled, common show data icon as x, but op do not need show it @ {
        mStatusBarExt.isDataDisabled(mSubscriptionInfo.getSubscriptionId(), dataDisabled);
        /// @ }

        /// M: Customize the data type icon id. @ {
        typeIcon = mStatusBarExt.getDataTypeIcon(
                        mSubscriptionInfo.getSubscriptionId(),
                        typeIcon,
                        mDataNetType,
                        mCurrentState.dataConnected ? TelephonyManager.DATA_CONNECTED :
                            TelephonyManager.DATA_DISCONNECTED,
                        mServiceState);
        /// @ }
        /// M: Customize the network type icon id. @ {
        networkIcon = mStatusBarExt.getNetworkTypeIcon(
                        mSubscriptionInfo.getSubscriptionId(),
                        networkIcon,
                        mDataNetType,
                        mServiceState);
        /// @ }

        mCallbackHandler.setMobileDataIndicatorsForInverse(statusIcon, statusIconGray, qsIcon, typeIcon, networkIcon, volteIcon,
                qsTypeIcon, activityIn, activityOut, dataContentDescription, description,
                icons.mIsWide, mSubscriptionInfo.getSubscriptionId());
        mCallbackHandler.notifyDataActivityChanged(mCurStatusBarStyle, mInOutDirection, mSubscriptionInfo);
        /** @ }*/

        /// M: update plmn label @{
        mNetworkController.refreshPlmnCarrierLabel();
        /// @}
    }
	
	/*PRIZE-get gray SignalStrengthIcon-liufan-2016-07-07-start*/
    public int getCurrentGrayIconId() {
        if (mCurrentState.connected) {
            return TelephonyIcons.getSignalStrengthIcon(1, mCurrentState.level);
        } else if (mCurrentState.enabled) {
            return getIcons().mSbDiscState;
        } else {
            return getIcons().mSbNullState;
        }
    }
	/*PRIZE-get gray SignalStrengthIcon-liufan-2016-07-07-end*/

    /*PRIZE-add for budid:47658-liufan-2018-01-16-start*/
    private int mInOutDirection = 0;
    /*PRIZE-add for budid:47658-liufan-2018-01-16-end*/
	/*PRIZE-PrizeStatusBarStyleListener callback-liufan-2016-04-20-start*/
    private int mCurStatusBarStyle = StatusBarManager.STATUS_BAR_INVERSE_DEFALUT;
    @Override
    public void onStatusBarStyleChanged(int style) {
        Log.d(TAG, "onStatusBarStyleChanged(). curStyle=" + mCurStatusBarStyle + ", newStyle=" + style);
        if(mCurStatusBarStyle != style) {
            mCurStatusBarStyle = style;
            /*PRIZE-add for budid:47658-liufan-2018-01-16-start*/
            mCallbackHandler.notifyDataActivityChanged(mCurStatusBarStyle, mInOutDirection, mSubscriptionInfo);
            /*PRIZE-add for budid:47658-liufan-2018-01-16-end*/
        }
    }
	/*PRIZE-PrizeStatusBarStyleListener callback-liufan-2016-04-20-end*/

    @Override
    protected MobileState cleanState() {
        return new MobileState();
    }

    private boolean hasService() {
        if (mServiceState != null) {
            // Consider the device to be in service if either voice or data
            // service is available. Some SIM cards are marketed as data-only
            // and do not support voice service, and on these SIM cards, we
            // want to show signal bars for data service as well as the "no
            // service" or "emergency calls only" text that indicates that voice
            // is not available.
            switch (mServiceState.getVoiceRegState()) {
                case ServiceState.STATE_POWER_OFF:
                    return false;
                case ServiceState.STATE_OUT_OF_SERVICE:
                case ServiceState.STATE_EMERGENCY_ONLY:
                    return mServiceState.getDataRegState() == ServiceState.STATE_IN_SERVICE;
                default:
                    return true;
            }
        } else {
            return false;
        }
    }

    private boolean isCdma() {
        return (mSignalStrength != null) && !mSignalStrength.isGsm();
    }

    public boolean isEmergencyOnly() {
        return (mServiceState != null && mServiceState.isEmergencyOnly());
    }

    private boolean isRoaming() {
        if (isCdma()) {
            /// M: fix ALPS02742814
            if (mServiceState == null) {
                return false;
            }
            final int iconMode = mServiceState.getCdmaEriIconMode();
            return mServiceState != null
                    && mServiceState.getCdmaEriIconIndex() != EriInfo.ROAMING_INDICATOR_OFF
                    && (iconMode == EriInfo.ROAMING_ICON_MODE_NORMAL
                        || iconMode == EriInfo.ROAMING_ICON_MODE_FLASH);
        } else {
            boolean isInRoaming =  mServiceState != null && mServiceState.getRoaming();
            return mStatusBarExt.needShowRoamingIcons(isInRoaming);
        }
    }

    /// M: Support VoLte @{
    public boolean isLteNetWork() {
        return (mDataNetType == TelephonyManager.NETWORK_TYPE_LTE
            || mDataNetType == TelephonyManager.NETWORK_TYPE_LTE_CA);
    }
    /// M: @}

    private boolean isCarrierNetworkChangeActive() {
        return mCurrentState.carrierNetworkChangeMode;
    }

    public void handleBroadcast(Intent intent) {
        String action = intent.getAction();
        if (action.equals(TelephonyIntents.SPN_STRINGS_UPDATED_ACTION)) {
            updateNetworkName(intent.getBooleanExtra(TelephonyIntents.EXTRA_SHOW_SPN, false),
                    intent.getStringExtra(TelephonyIntents.EXTRA_SPN),
                    intent.getStringExtra(TelephonyIntents.EXTRA_DATA_SPN),
                    intent.getBooleanExtra(TelephonyIntents.EXTRA_SHOW_PLMN, false),
                    intent.getStringExtra(TelephonyIntents.EXTRA_PLMN));
            notifyListenersIfNecessary();
        } else if (action.equals(TelephonyIntents.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED)) {
            updateDataSim();
            notifyListenersIfNecessary();
        } else if (action.equals(ImsManager.ACTION_IMS_STATE_CHANGED)) {
          /// M: support dual Ims. @{
            handleImsAction(intent);
            notifyListenersIfNecessary();
            /// @}
        }
    }

    /// M: Add for volte @{
    private void handleImsAction(Intent intent){
        mCurrentState.imsRegState = intent.getIntExtra(ImsManager.EXTRA_IMS_REG_STATE_KEY,
                ServiceState.STATE_OUT_OF_SERVICE);
        mCurrentState.imsCap = getImsEnableCap(intent);
        mCurrentState.volteIcon = getVolteIcon();
        /// M: add for disconnected volte feature. @{
        mStatusBarExt.setImsRegInfo(mSubscriptionInfo.getSubscriptionId(),
                mCurrentState.imsRegState, isImsOverWfc());
        /// @}
        Log.d(mTag, "handleImsAction imsRegstate=" + mCurrentState.imsRegState + ",imsCap = " +
                mCurrentState.imsCap + ",volteIconId=" + mCurrentState.volteIcon);
    }
    private int getVolteIcon() {
        int icon = 0;
        if (isImsOverWfc()) {
            boolean isNonSsProject
                = !(SystemProperties.get("persist.radio.multisim.config", "ss").equals("ss"));
            if (isNonSsProject) {
                icon = NetworkTypeUtils.WFC_ICON;
            }
        } else if (isImsOverVoice() && isLteNetWork() &&
            mCurrentState.imsRegState == ServiceState.STATE_IN_SERVICE) {
            icon = NetworkTypeUtils.VOLTE_ICON;
        }
        return icon;
    }
    private int getImsEnableCap(Intent intent) {
        int cap = ImsConfig.FeatureConstants.FEATURE_TYPE_UNKNOWN;
        boolean[] enabledFeatures =
                intent.getBooleanArrayExtra(ImsManager.EXTRA_IMS_ENABLE_CAP_KEY);
        if (enabledFeatures != null) {
            if (enabledFeatures[ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_WIFI]) {
                cap = ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_WIFI;
            } else if (enabledFeatures[ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_LTE]) {
                cap = ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_LTE;
            }
        }
        return cap;
    }
    public boolean isImsOverWfc() {
        return mCurrentState.imsCap == ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_WIFI;
    }
    private boolean isImsOverVoice() {
        return mCurrentState.imsCap == ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_LTE;
    }

    public boolean isWfcEnable() {
        boolean isWfcEnabled = TelephonyManagerEx.getDefault().isWifiCallingEnabled(
            mSubscriptionInfo.getSubscriptionId());
        return isWfcEnabled;
    }
    /// @}

    private void updateDataSim() {
        int defaultDataSub = mDefaults.getDefaultDataSubId();
        if (SubscriptionManager.isValidSubscriptionId(defaultDataSub)) {
            mCurrentState.dataSim = defaultDataSub == mSubscriptionInfo.getSubscriptionId();
        } else {
            // There doesn't seem to be a data sim selected, however if
            // there isn't a MobileSignalController with dataSim set, then
            // QS won't get any callbacks and will be blank.  Instead
            // lets just assume we are the data sim (which will basically
            // show one at random) in QS until one is selected.  The user
            // should pick one soon after, so we shouldn't be in this state
            // for long.
            mCurrentState.dataSim = true;
        }
    }

    /**
     * Updates the network's name based on incoming spn and plmn.
     */
    void updateNetworkName(boolean showSpn, String spn, String dataSpn,
            boolean showPlmn, String plmn) {
        if (CHATTY) {
            Log.d("CarrierLabel", "updateNetworkName showSpn=" + showSpn
                    + " spn=" + spn + " dataSpn=" + dataSpn
                    + " showPlmn=" + showPlmn + " plmn=" + plmn);
        }
        StringBuilder str = new StringBuilder();
        StringBuilder strData = new StringBuilder();
        if (showPlmn && plmn != null) {
            str.append(plmn);
            strData.append(plmn);
        }
        if (showSpn && spn != null) {
            if (str.length() != 0) {
                str.append(mNetworkNameSeparator);
            }
            str.append(spn);
        }
        if (str.length() != 0) {
            mCurrentState.networkName = str.toString();
        } else {
            mCurrentState.networkName = mNetworkNameDefault;
        }
        if (showSpn && dataSpn != null) {
            if (strData.length() != 0) {
                strData.append(mNetworkNameSeparator);
            }
            strData.append(dataSpn);
        }

        // M: ALPS02744648 for C2K, there isn't dataspn parameter, when no plmn
        // and no dataspn, show spn instead "no service" here @{
        if (strData.length() == 0 && showSpn && spn != null) {
            Log.d("CarrierLabel", "show spn instead 'no service' here: " + spn);
            strData.append(spn);
        }
        // @}

        if (strData.length() != 0) {
            mCurrentState.networkNameData = strData.toString();
        } else {
            mCurrentState.networkNameData = mNetworkNameDefault;
        }
    }

    /**
     * Updates the current state based on mServiceState, mSignalStrength, mDataNetType,
     * mDataState, and mSimState.  It should be called any time one of these is updated.
     * This will call listeners if necessary.
     */
    private final void updateTelephony() {
        if (DEBUG) {
            Log.d(mTag, "updateTelephonySignalStrength: hasService=" + hasService()
                    + " ss=" + mSignalStrength);
        }
        mCurrentState.connected = hasService() && mSignalStrength != null;
        handleIWLANNetwork();
        if (mCurrentState.connected) {
            if (!mSignalStrength.isGsm() && mConfig.alwaysShowCdmaRssi) {
                mCurrentState.level = mSignalStrength.getCdmaLevel();
            } else {
                mCurrentState.level = mSignalStrength.getLevel();
            }
            /// M: Customize the signal strength level. @ {
            mCurrentState.level = mStatusBarExt.getCustomizeSignalStrengthLevel(
                    mCurrentState.level, mSignalStrength, mServiceState);
            /// @ }
        }
        if (mNetworkToIconLookup.indexOfKey(mDataNetType) >= 0) {
            mCurrentState.iconGroup = mNetworkToIconLookup.get(mDataNetType);
        } else {
            mCurrentState.iconGroup = mDefaultIcons;
        }
        /// M: Add for data network type.
        mCurrentState.dataNetType = mDataNetType;
        mCurrentState.dataConnected = mCurrentState.connected
                && mDataState == TelephonyManager.DATA_CONNECTED;
        /// M: Add for op network tower type.
        mCurrentState.customizedState = mStatusBarExt.getCustomizeCsState(mServiceState,
                mCurrentState.customizedState);
        /// M: Add for op signal strength tower icon.
        mCurrentState.customizedSignalStrengthIcon = mStatusBarExt.getCustomizeSignalStrengthIcon(
                mSubscriptionInfo.getSubscriptionId(),
                mCurrentState.customizedSignalStrengthIcon,
                mSignalStrength,
                mDataNetType,
                mServiceState);

        if (isCarrierNetworkChangeActive()) {
            mCurrentState.iconGroup = TelephonyIcons.CARRIER_NETWORK_CHANGE;
        } else if (isRoaming()) {
            mCurrentState.iconGroup = TelephonyIcons.ROAMING;
        } else if (isDataDisabled()) {
            mCurrentState.iconGroup = TelephonyIcons.DATA_DISABLED;
        }
        if (isEmergencyOnly() != mCurrentState.isEmergency) {
            mCurrentState.isEmergency = isEmergencyOnly();
            mNetworkController.recalculateEmergency();
        }
        // Fill in the network name if we think we have it.
        if (mCurrentState.networkName == mNetworkNameDefault && mServiceState != null
                && !TextUtils.isEmpty(mServiceState.getOperatorAlphaShort())) {
            mCurrentState.networkName = mServiceState.getOperatorAlphaShort();
        }
        /// M: For network type big icon.
        mCurrentState.networkIcon =
            NetworkTypeUtils.getNetworkTypeIcon(mServiceState, mConfig, hasService());
        /// M: For volte type icon.
        mCurrentState.volteIcon = getVolteIcon();

        notifyListenersIfNecessary();
    }

    private boolean isDataDisabled() {
        return !mPhone.getDataEnabled(mSubscriptionInfo.getSubscriptionId());
    }

    /// M: bug fix for ALPS02603527.
    /** IWLAN is special case in which the transmission via WIFI, no need cellular network, then
    whenever PS type is IWLAN, cellular network is not connected. However, in special case, CS may
    still connect under IWLAN with valid network type.
    **/
     private void handleIWLANNetwork() {
        /// M: fix ALPS02742814
        if (mCurrentState.connected && mServiceState != null &&
            mServiceState.getDataNetworkType() == TelephonyManager.NETWORK_TYPE_IWLAN &&
            mServiceState.getVoiceNetworkType() == TelephonyManager.NETWORK_TYPE_UNKNOWN) {
            Log.d(mTag,"Current is IWLAN network only, no cellular network available");
            mCurrentState.connected = false;
        }
        /// M: Add for plugin wifi-only mode.
        mCurrentState.connected = mStatusBarExt.updateSignalStrengthWifiOnlyMode(
            mServiceState, mCurrentState.connected);
    }

    @VisibleForTesting
    void setActivity(int activity) {
        mCurrentState.activityIn = activity == TelephonyManager.DATA_ACTIVITY_INOUT
                || activity == TelephonyManager.DATA_ACTIVITY_IN;
        mCurrentState.activityOut = activity == TelephonyManager.DATA_ACTIVITY_INOUT
                || activity == TelephonyManager.DATA_ACTIVITY_OUT;
        notifyListenersIfNecessary();
    }

    @Override
    public void dump(PrintWriter pw) {
        super.dump(pw);
        pw.println("  mSubscription=" + mSubscriptionInfo + ",");
        pw.println("  mServiceState=" + mServiceState + ",");
        pw.println("  mSignalStrength=" + mSignalStrength + ",");
        pw.println("  mDataState=" + mDataState + ",");
        pw.println("  mDataNetType=" + mDataNetType + ",");
    }

    class MobilePhoneStateListener extends PhoneStateListener {
        public MobilePhoneStateListener(int subId, Looper looper) {
            super(subId, looper);
        }

        @Override
        public void onSignalStrengthsChanged(SignalStrength signalStrength) {
            if (DEBUG) {
                Log.d(mTag, "onSignalStrengthsChanged signalStrength=" + signalStrength +
                        ((signalStrength == null) ? "" : (" level=" + signalStrength.getLevel())));
            }
            mSignalStrength = signalStrength;
            updateTelephony();
        }

        @Override
        public void onServiceStateChanged(ServiceState state) {
            if (DEBUG) {
                Log.d(mTag, "onServiceStateChanged voiceState=" + state.getVoiceRegState()
                        + " dataState=" + state.getDataRegState());
            }
            mServiceState = state;
            mDataNetType = state.getDataNetworkType();
            //TODO:: Double check with FWK
            if (mDataNetType == TelephonyManager.NETWORK_TYPE_LTE && mServiceState != null &&
                    mServiceState.isUsingCarrierAggregation()) {
                mDataNetType = TelephonyManager.NETWORK_TYPE_LTE_CA;
            }
            updateTelephony();
        }

        @Override
        public void onDataConnectionStateChanged(int state, int networkType) {
            if (DEBUG) {
                Log.d(mTag, "onDataConnectionStateChanged: state=" + state
                        + " type=" + networkType);
            }
            mDataState = state;
            mDataNetType = networkType;
            //TODO:: Double check with FWK
            if (mDataNetType == TelephonyManager.NETWORK_TYPE_LTE && mServiceState != null &&
                    mServiceState.isUsingCarrierAggregation()) {
                mDataNetType = TelephonyManager.NETWORK_TYPE_LTE_CA;
            }
            updateTelephony();
        }

        @Override
        public void onDataActivity(int direction) {
            if (DEBUG) {
                Log.d(mTag, "onDataActivity: direction=" + direction);
            }
            setActivity(direction);
            /*PRIZE-add for budid:47658-liufan-2018-01-16-start*/
            mInOutDirection = direction;
            /*PRIZE-add for budid:47658-liufan-2018-01-16-end*/
			/*PRIZE-notify data activity changed-liufan-2016-04-20-start*/
            if(FeatureOption.PRIZE_QS_SORT){
                mCallbackHandler.notifyDataActivityChanged(mCurStatusBarStyle, direction, mSubscriptionInfo);
			}
			/*PRIZE-notify data activity changed-liufan-2016-04-20-end*/
        }

        @Override
        public void onCarrierNetworkChange(boolean active) {
            if (DEBUG) {
                Log.d(mTag, "onCarrierNetworkChange: active=" + active);
            }
            mCurrentState.carrierNetworkChangeMode = active;

            updateTelephony();
        }
    };

    static class MobileIconGroup extends SignalController.IconGroup {
        final int mDataContentDescription; // mContentDescriptionDataType
        final int mDataType;
        final boolean mIsWide;
        final int mQsDataType;

        public MobileIconGroup(String name, int[][] sbIcons, int[][] qsIcons, int[] contentDesc,
                int sbNullState, int qsNullState, int sbDiscState, int qsDiscState,
                int discContentDesc, int dataContentDesc, int dataType, boolean isWide,
                int qsDataType) {
            super(name, sbIcons, qsIcons, contentDesc, sbNullState, qsNullState, sbDiscState,
                    qsDiscState, discContentDesc);
            mDataContentDescription = dataContentDesc;
            mDataType = dataType;
            mIsWide = isWide;
            mQsDataType = qsDataType;
        }
    }

    static class MobileState extends SignalController.State {
        String networkName;
        String networkNameData;
        boolean dataSim;
        boolean dataConnected;
        boolean isEmergency;
        boolean airplaneMode;
        boolean carrierNetworkChangeMode;
        boolean isDefault;
        boolean userSetup;

        /// M: For network type big icon.
        int networkIcon;
        /// M: Add for data network type.
        int dataNetType;
        /// M: Add for op network tower type.
        int customizedState;
        /// M: Add for op signal strength tower icon.
        int customizedSignalStrengthIcon;
        /// M: Add for volte @{
        int imsRegState = ServiceState.STATE_POWER_OFF;
        int imsCap;
        int volteIcon;
        /// @}
        @Override
        public void copyFrom(State s) {
            super.copyFrom(s);
            MobileState state = (MobileState) s;
            dataSim = state.dataSim;
            networkName = state.networkName;
            networkNameData = state.networkNameData;
            dataConnected = state.dataConnected;
            isDefault = state.isDefault;
            isEmergency = state.isEmergency;
            airplaneMode = state.airplaneMode;
            carrierNetworkChangeMode = state.carrierNetworkChangeMode;
            userSetup = state.userSetup;

            /// M: For network type big icon.
            networkIcon = state.networkIcon;
            /// M: Add for data network type.
            dataNetType = state.dataNetType;
            /// M: Add for op network tower type.
            customizedState = state.customizedState;
            /// M: Add for op signal strength tower icon.
            customizedSignalStrengthIcon = state.customizedSignalStrengthIcon;
            /// M: Add for volte
            imsRegState = state.imsRegState;
            imsCap = state.imsCap;
            volteIcon = state.volteIcon;
        }

        @Override
        protected void toString(StringBuilder builder) {
            super.toString(builder);
            builder.append(',');
            builder.append("dataSim=").append(dataSim).append(',');
            builder.append("networkName=").append(networkName).append(',');
            builder.append("networkNameData=").append(networkNameData).append(',');
            builder.append("dataConnected=").append(dataConnected).append(',');
            builder.append("isDefault=").append(isDefault).append(',');
            builder.append("isEmergency=").append(isEmergency).append(',');
            builder.append("airplaneMode=").append(airplaneMode).append(',');
            builder.append("carrierNetworkChangeMode=").append(carrierNetworkChangeMode)
                    .append(',');
            builder.append("userSetup=").append(userSetup);

            /// M: For network type big icon.
            builder.append("networkIcon").append(networkIcon).append(',');
            /// M: Add for data network type.
            builder.append("dataNetType").append(dataNetType).append(',');
            /// M: Add for op network tower type.
            builder.append("customizedState").append(customizedState).append(',');
            /// M: Add for op signal strength tower icon.
            builder.append("customizedSignalStrengthIcon").append(customizedSignalStrengthIcon)
                    .append(',');
            /// M: Add for volte.
            builder.append("imsRegState=").append(imsRegState).append(',');
            builder.append("imsCap=").append(imsCap).append(',');
            builder.append("volteIconId=").append(volteIcon).append(',');
            builder.append("carrierNetworkChangeMode=").append(carrierNetworkChangeMode);
        }

        @Override
        public boolean equals(Object o) {
            return super.equals(o)
                    && Objects.equals(((MobileState) o).networkName, networkName)
                    && Objects.equals(((MobileState) o).networkNameData, networkNameData)
                    && ((MobileState) o).dataSim == dataSim
                    && ((MobileState) o).dataConnected == dataConnected
                    && ((MobileState) o).isEmergency == isEmergency
                    && ((MobileState) o).airplaneMode == airplaneMode
                    && ((MobileState) o).carrierNetworkChangeMode == carrierNetworkChangeMode
                    /// M: For network type big icon.
                    && ((MobileState) o).networkIcon == networkIcon
                    && ((MobileState) o).volteIcon == volteIcon
                    /// M: Add for data network type.
                    && ((MobileState) o).dataNetType == dataNetType
                    /// M: Add for op network tower type.
                    && ((MobileState) o).customizedState == customizedState
                    /// M: Add for op signal strength tower icon.
                    && ((MobileState) o).customizedSignalStrengthIcon ==
                                             customizedSignalStrengthIcon
                    && ((MobileState) o).userSetup == userSetup
                    && ((MobileState) o).isDefault == isDefault;
        }
    }

    /// M: Support for PLMN. @{
    public SubscriptionInfo getControllerSubInfo() {
        return mSubscriptionInfo;
    }

    public boolean getControllserHasService() {
        return hasService();
    }
    /// M: Support for PLMN. @}
}
