/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.services.telephony;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
/// M: Choose Radio off but SIM-inserted phone for ECC @{
import android.os.SystemProperties;
/// @}
/// M: @{
import android.provider.Settings;
/// @}
import android.telecom.Conference;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.ConnectionService;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import android.telephony.CarrierConfigManager;
import android.telephony.PhoneNumberUtils;
import android.telephony.RadioAccessFamily;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Pair;
/// M: @{
import android.widget.Toast;
/// @}

import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.SubscriptionController;
import com.android.internal.telephony.imsphone.ImsExternalCallTracker;
import com.android.internal.telephony.imsphone.ImsPhone;
import com.android.phone.MMIDialogActivity;
import com.android.phone.PhoneUtils;
import com.android.phone.R;

/// M: CC: ECC phone selection rule
// Choose 3G-capable phone for ECC
import com.mediatek.internal.telephony.RadioCapabilitySwitchUtil;

/// M: CC: Error message due to VoLTE SS checking
import com.mediatek.telecom.TelecomManagerEx;

/// M: CC: Get iccid from system property @{
import com.mediatek.telephony.TelephonyManagerEx;
/// @}

/// M: CC: to check whether the device has on-going ECC
import com.mediatek.internal.telephony.RadioManager;

/// M: For ECC change feature @{
import com.mediatek.services.telephony.SwitchPhoneHelper;
/// @}
/// M: CC: Vzw/CTVolte ECC @{
import com.android.internal.telephony.TelephonyDevController;
/// @}

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Service for making GSM and CDMA connections.
 */
public class TelephonyConnectionService extends ConnectionService {
    private static final String TAG = "TeleConnService";

    // If configured, reject attempts to dial numbers matching this pattern.
    private static final Pattern CDMA_ACTIVATION_CODE_REGEX_PATTERN =
            Pattern.compile("\\*228[0-9]{0,2}");

    private final TelephonyConferenceController mTelephonyConferenceController =
            new TelephonyConferenceController(this);
    private final CdmaConferenceController mCdmaConferenceController =
            new CdmaConferenceController(this);
    private final ImsConferenceController mImsConferenceController =
            new ImsConferenceController(this);

    private ComponentName mExpectedComponentName = null;
    private EmergencyCallHelper mEmergencyCallHelper;
    private EmergencyTonePlayer mEmergencyTonePlayer;
    /// M: For ECC change feature @{
    private SwitchPhoneHelper mSwitchPhoneHelper;
    /// @}

    // Contains one TelephonyConnection that has placed a call and a memory of which Phones it has
    // already tried to connect with. There should be only one TelephonyConnection trying to place a
    // call at one time. We also only access this cache from a TelephonyConnection that wishes to
    // redial, so we use a WeakReference that will become stale once the TelephonyConnection is
    // destroyed.
    private Pair<WeakReference<TelephonyConnection>, List<Phone>> mEmergencyRetryCache;

    /// M: CC: Vzw/CTVolte ECC @{
    TelephonyDevController telDevController = TelephonyDevController.getInstance();
    private boolean hasC2kOverImsModem() {
        if (telDevController != null && telDevController.getModem(0) != null &&
                telDevController.getModem(0).hasC2kOverImsModem() == true) {
            return true;
        }
        return false;
    }
    /// @}

    /**
     * A listener to actionable events specific to the TelephonyConnection.
     */
    private final TelephonyConnection.TelephonyConnectionListener mTelephonyConnectionListener =
            new TelephonyConnection.TelephonyConnectionListener() {
        @Override
        public void onOriginalConnectionConfigured(TelephonyConnection c) {
            addConnectionToConferenceController(c);
        }

        @Override
        public void onOriginalConnectionRetry(TelephonyConnection c) {
            retryOutgoingOriginalConnection(c);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        mExpectedComponentName = new ComponentName(this, this.getClass());
        mEmergencyTonePlayer = new EmergencyTonePlayer(this);
        TelecomAccountRegistry.getInstance(this).setTelephonyConnectionService(this);
        /// M: CC: Use TelephonyConnectionServiceUtil
        TelephonyConnectionServiceUtil.getInstance().setService(this);
    }

    /// M: CC: Use TelephonyConnectionServiceUtil @{
    @Override
    public void onDestroy() {
        /// M: CC: to check whether the device has on-going ECC
        TelephonyConnectionServiceUtil.getInstance().setInEcc(false);
        TelephonyConnectionServiceUtil.getInstance().unsetService();
        mCdmaConferenceController.onDestroy();
        /// M: For ECC change feature @{
        if (mSwitchPhoneHelper != null) {
            mSwitchPhoneHelper.onDestroy();
        }
        /// @}
        /// M: CC: Cleanup all listeners to avoid callbacks after service destroyed. @{
        if (mEmergencyCallHelper != null) {
            mEmergencyCallHelper.cleanup();
        }
        /// @}
        super.onDestroy();
    }
    /// @}

    @Override
    public Connection onCreateOutgoingConnection(
            PhoneAccountHandle connectionManagerPhoneAccount,
            final ConnectionRequest request) {
        Log.i(this, "onCreateOutgoingConnection, request: " + request);
        /// M: clarify the correct PhoneAccountHandle used.@{
        log("onCreateOutgoingConnection, handle:" + request.getAccountHandle());
        /// @}

        Uri handle = request.getAddress();
        if (handle == null) {
            Log.d(this, "onCreateOutgoingConnection, handle is null");
            return Connection.createFailedConnection(
                    DisconnectCauseUtil.toTelecomDisconnectCause(
                            android.telephony.DisconnectCause.NO_PHONE_NUMBER_SUPPLIED,
                            "No phone number supplied"));
        }

        /// M: [ALPS02340908] To avoid JE @{
        if (request.getAccountHandle() == null) {
            log("onCreateOutgoingConnection, PhoneAccountHandle is null");
            return Connection.createFailedConnection(
                    DisconnectCauseUtil.toTelecomDisconnectCause(
                            android.telephony.DisconnectCause.NO_PHONE_NUMBER_SUPPLIED,
                            "No phone number supplied"));
        }
        /// @}
        /// M: CC: ECC Retry @{
        if (TelephonyConnectionServiceUtil.getInstance().isEccRetryOn()) {
            int phoneId = SubscriptionManager.INVALID_PHONE_INDEX;
            try {
                phoneId = Integer.parseInt(request.getAccountHandle().getId());
            } catch (NumberFormatException e) {
                phoneId = SubscriptionManager.INVALID_PHONE_INDEX;
            } finally {
                if (PhoneFactory.getPhone(phoneId) == null) {
                    Log.d(this, "onCreateOutgoingConnection, phone is null, clear ECC param");
                    TelephonyConnectionServiceUtil.getInstance().clearEccRetryParams();
                    /// M: CC: to check whether the device has on-going ECC
                    TelephonyConnectionServiceUtil.getInstance().setInEcc(false);
                    log("onCreateOutgoingConnection, phone is null");
                    return Connection.createFailedConnection(
                            DisconnectCauseUtil.toTelecomDisconnectCause(
                                    android.telephony.DisconnectCause.OUT_OF_SERVICE,
                                    "Phone is null"));
                }
            }
        }
        /// @}

        String scheme = handle.getScheme();
        String number;
        if (PhoneAccount.SCHEME_VOICEMAIL.equals(scheme)) {
            // TODO: We don't check for SecurityException here (requires
            // CALL_PRIVILEGED permission).
            final Phone phone = getPhoneForAccount(request.getAccountHandle(), false);
            if (phone == null) {
                Log.d(this, "onCreateOutgoingConnection, phone is null");
                return Connection.createFailedConnection(
                        DisconnectCauseUtil.toTelecomDisconnectCause(
                                android.telephony.DisconnectCause.OUT_OF_SERVICE,
                                "Phone is null"));
            }
            number = phone.getVoiceMailNumber();
            if (TextUtils.isEmpty(number)) {
                Log.d(this, "onCreateOutgoingConnection, no voicemail number set.");
                return Connection.createFailedConnection(
                        DisconnectCauseUtil.toTelecomDisconnectCause(
                                android.telephony.DisconnectCause.VOICEMAIL_NUMBER_MISSING,
                                "Voicemail scheme provided but no voicemail number set."));
            }

            // Convert voicemail: to tel:
            handle = Uri.fromParts(PhoneAccount.SCHEME_TEL, number, null);
        } else {
            /// M: [ALPS01906649] For VoLTE, Allow SIP URI to be dialed out @{
            if (!PhoneAccount.SCHEME_TEL.equals(scheme) && !PhoneAccount.SCHEME_SIP.equals(scheme)) {
                Log.d(this, "onCreateOutgoingConnection, Handle %s is not type tel or sip", scheme);
                return Connection.createFailedConnection(
                        DisconnectCauseUtil.toTelecomDisconnectCause(
                                android.telephony.DisconnectCause.INVALID_NUMBER,
                                "Handle scheme is not type tel or sip"));
            }
            /// @}

            number = handle.getSchemeSpecificPart();
            if (TextUtils.isEmpty(number)) {
                Log.d(this, "onCreateOutgoingConnection, unable to parse number");
                return Connection.createFailedConnection(
                        DisconnectCauseUtil.toTelecomDisconnectCause(
                                android.telephony.DisconnectCause.INVALID_NUMBER,
                                "Unable to parse number"));
            }


            /// M: CC: ECC Retry @{
            //final Phone phone = getPhoneForAccount(request.getAccountHandle(), false);
            Phone phone = null;
            if (!TelephonyConnectionServiceUtil.getInstance().isEccRetryOn()) {
                phone = getPhoneForAccount(request.getAccountHandle(), false);
            }
            /// @}

            if (phone != null && CDMA_ACTIVATION_CODE_REGEX_PATTERN.matcher(number).matches()) {
                // Obtain the configuration for the outgoing phone's SIM. If the outgoing number
                // matches the *228 regex pattern, fail the call. This number is used for OTASP, and
                // when dialed could lock LTE SIMs to 3G if not prohibited..
                boolean disableActivation = false;
                CarrierConfigManager cfgManager = (CarrierConfigManager)
                        phone.getContext().getSystemService(Context.CARRIER_CONFIG_SERVICE);
                if (cfgManager != null) {
                    disableActivation = cfgManager.getConfigForSubId(phone.getSubId())
                            .getBoolean(CarrierConfigManager.KEY_DISABLE_CDMA_ACTIVATION_CODE_BOOL);
                }

                if (disableActivation) {
                    return Connection.createFailedConnection(
                            DisconnectCauseUtil.toTelecomDisconnectCause(
                                    android.telephony.DisconnectCause
                                            .CDMA_ALREADY_ACTIVATED,
                                    "Tried to dial *228"));
                }
            }
        }

        // Convert into emergency number if necessary
        // This is required in some regions (e.g. Taiwan).
        if (PhoneNumberUtils.isConvertToEmergencyNumberEnabled() &&
                !PhoneNumberUtils.isLocalEmergencyNumber(this, number)) {
            final Phone phone = getPhoneForAccount(request.getAccountHandle(), false);
            // We only do the conversion if the phone is not in service. The un-converted
            // emergency numbers will go to the correct destination when the phone is in-service,
            // so they will only need the special emergency call setup when the phone is out of
            // service.
            if (phone == null || phone.getServiceState().getState()
                    != ServiceState.STATE_IN_SERVICE) {
                String convertedNumber = PhoneNumberUtils.convertToEmergencyNumber(number);
                if (!TextUtils.equals(convertedNumber, number)) {
                    Log.i(this, "onCreateOutgoingConnection, converted to emergency number");
                    number = convertedNumber;
                    handle = Uri.fromParts(PhoneAccount.SCHEME_TEL, number, null);
                }
            }
        }
        final String numberToDial = number;

        final boolean isEmergencyNumber =
                PhoneNumberUtils.isLocalEmergencyNumber(this, numberToDial);

        /* If current phone number will be treated as normal call in Telephony Framework,
             do not need to enable ECC retry mechanism */
        final boolean isDialedByEmergencyCommand = PhoneNumberUtils.isEmergencyNumber(
                                    numberToDial);

        /// M: CC: ECC Retry @{
        if (!isEmergencyNumber && TelephonyConnectionServiceUtil.getInstance().isEccRetryOn()) {
            Log.d(this, "ECC Retry : clear ECC param due to SIM state/phone type change, not ECC");
            TelephonyConnectionServiceUtil.getInstance().clearEccRetryParams();
            /// M: CC: to check whether the device has on-going ECC
            TelephonyConnectionServiceUtil.getInstance().setInEcc(false);
            Log.d(this, "onCreateOutgoingConnection, phone is null");
            return Connection.createFailedConnection(
                    DisconnectCauseUtil.toTelecomDisconnectCause(
                            android.telephony.DisconnectCause.OUT_OF_SERVICE,
                            "Phone is null"));
        }
        /// @}

        if (isEmergencyNumber) {
            /// M: CC: to check whether the device has on-going ECC
            TelephonyConnectionServiceUtil.getInstance().setInEcc(true);

            final Uri emergencyHandle = handle;
            // By default, Connection based on the default Phone, since we need to return to
            // Telecom now.
            final int defaultPhoneType = PhoneFactory.getDefaultPhone().getPhoneType();
            final Connection emergencyConnection = getTelephonyConnection(request, numberToDial,
                    isEmergencyNumber, emergencyHandle, PhoneFactory.getDefaultPhone());

            /// M: CC: Vzw/CTVolte ECC
            TelephonyConnectionServiceUtil.getInstance().setEmergencyNumber(numberToDial);

            if (hasC2kOverImsModem() || PhoneFactory.getDefaultPhone().useVzwLogic()) {
                mSwitchPhoneHelper = null;
            } else if (mSwitchPhoneHelper == null) {
                mSwitchPhoneHelper = new SwitchPhoneHelper(this, number);
            }

            /// M: For ECC change feature @{
            if (mSwitchPhoneHelper != null && mSwitchPhoneHelper.needToPrepareForDial()) {
                mSwitchPhoneHelper.prepareForDial(
                        new SwitchPhoneHelper.Callback() {
                            @Override
                            public void onComplete(boolean success) {
                                if (emergencyConnection.getState()
                                        == Connection.STATE_DISCONNECTED) {
                                    Log.d(this, "prepareForDial, connection disconnect");
                                    /// M: CC: to check whether the device has on-going ECC
                                    TelephonyConnectionServiceUtil.getInstance().setInEcc(false);
                                    return;
                                } else if (success) {
                                    Log.d(this, "startTurnOnRadio");
                                    startTurnOnRadio(emergencyConnection, request,
                                            emergencyHandle, numberToDial);
                                } else {
                                    /// M: CC: ECC Retry @{
                                    // Assume only one ECC exists
                                    // Not trigger retry since MD fails to power on should be a bug
                                    if (TelephonyConnectionServiceUtil.getInstance()
                                            .isEccRetryOn()) {
                                        Log.d(this, "ECC Retry : clear ECC param");
                                        TelephonyConnectionServiceUtil.getInstance()
                                                .clearEccRetryParams();
                                    }
                                    /// @}
                                    Log.d(this, "prepareForDial, failed to turn on radio");
                                    emergencyConnection.setDisconnected(
                                            DisconnectCauseUtil.toTelecomDisconnectCause(
                                            android.telephony.DisconnectCause.POWER_OFF,
                                            "Failed to turn on radio."));
                                    /// M: CC: to check whether the device has on-going ECC
                                    TelephonyConnectionServiceUtil.getInstance().setInEcc(false);
                                    emergencyConnection.destroy();
                                }
                            }
                        });
                // Return the still unconnected GsmConnection and wait for the Radios to boot before
                // connecting it to the underlying Phone.
                return emergencyConnection;
            }
            /// @}

            /// M: ECC special handle, select phone by ECC rule @{
            final Phone defaultPhone = getPhoneForAccount(request.getAccountHandle(),
                    isEmergencyNumber);
            final Phone phone = TelephonyConnectionServiceUtil.getInstance()
                    .selectPhoneBySpecialEccRule(request.getAccountHandle(),
                    numberToDial, defaultPhone);
            /// @}

            /// M: Radio maybe on even airplane mode on @{
            boolean isAirplaneModeOn = false;
            if (Settings.Global.getInt(this.getContentResolver(),
                    Settings.Global.AIRPLANE_MODE_ON, 0) > 0) {
                isAirplaneModeOn = true;
            }
            /// @}

            /// M: Check ECC phone radio state and airplane mode also.
            if (!isRadioOn() || !phone.isRadioOn() || isAirplaneModeOn) {
                if (mEmergencyCallHelper == null) {
                    mEmergencyCallHelper = new EmergencyCallHelper(this);
                }
                mEmergencyCallHelper.enableEmergencyCalling(
                        new EmergencyCallStateListener.Callback() {
                    @Override
                    public void onComplete(EmergencyCallStateListener listener,
                            boolean isRadioReady) {
                        // Make sure the Call has not already been canceled by the user.
                        if (emergencyConnection.getState() == Connection.STATE_DISCONNECTED) {
                            Log.i(this, "Emergency call disconnected before the outgoing call was "
                                    + "placed. Skipping emergency call placement.");
                            /// M: CC: to check whether the device has on-going ECC
                            TelephonyConnectionServiceUtil.getInstance().setInEcc(false);
                            return;
                        }
                        if (isRadioReady) {
                            ///M: MTK ECC choose phone rule is different @{
                            // Get the right phone object since the radio has been turned on
                            // successfully.
                            //final Phone phone = getPhoneForAccount(request.getAccountHandle(),
                            //    isEmergencyNumber);
                            Phone newDefaultPhone = getPhoneForAccount(request.getAccountHandle(),
                                    isEmergencyNumber);
                            /// @}

                            /// M: CC: Vzw/CTVolte ECC @{
                            Phone newPhone = TelephonyConnectionServiceUtil.getInstance()
                                    .selectPhoneBySpecialEccRule(request.getAccountHandle(),
                                    numberToDial, newDefaultPhone);
                            Log.d(this, "Select phone after turning off airplane mode again"
                                    + ", orig phone=" + phone + " , new phone=" + newPhone);
                            /// @}

                            /// M: CC: ECC Retry @{
                            if ((!TelephonyConnectionServiceUtil.getInstance().isEccRetryOn()) &&
                                 isDialedByEmergencyCommand) {
                                Log.d(this, "ECC Retry : set param with Intial ECC.");
                                TelephonyConnectionServiceUtil.getInstance().setEccRetryParams(
                                        request, newPhone.getPhoneId());
                            }
                            /// @}

                            ///M: 4G data only @{
                            if (TelephonyConnectionServiceUtil.getInstance()
                                    .isDataOnlyMode(newPhone)) {
                                Log.d(this, "enableEmergencyCalling, 4G data only");
                                /// M: CC: ECC Retry @{
                                // Assume only one ECC exists
                                if (TelephonyConnectionServiceUtil.getInstance().isEccRetryOn()) {
                                    Log.d(this, "ECC Retry : clear ECC param");
                                    TelephonyConnectionServiceUtil.getInstance()
                                            .clearEccRetryParams();
                                }
                                /// @}
                                emergencyConnection.setDisconnected(
                                        DisconnectCauseUtil.toTelecomDisconnectCause(
                                        android.telephony.DisconnectCause.OUTGOING_CANCELED, null));
                                emergencyConnection.destroy();
                                /// M: CC: to check whether the device has on-going ECC
                                TelephonyConnectionServiceUtil.getInstance().setInEcc(false);
                                return;
                            }
                            /// @}

                            /// M: CC: Vzw/CTVolte ECC @{
                            if (TelephonyManager.getDefault().getPhoneCount() > 1) {
                                TelephonyConnectionServiceUtil.getInstance()
                                        .enterEmergencyMode(newPhone, 1/*airplane*/);
                            }
                            /// @}

                            // If the PhoneType of the Phone being used is different than Default
                            // Phone, then we need create a new Connection using that PhoneType and
                            // replace it in Telecom.
                            if (newPhone.getPhoneType() != defaultPhoneType) {
                                Connection repConnection = getTelephonyConnection(request,
                                        numberToDial, isEmergencyNumber, emergencyHandle, newPhone);
                                /// M: Modify the follow to handle the no sound issue. @{
                                // 1. Add the new connection into Telecom;
                                // 2. Disconnect the old connection;
                                // 3. Place the new connection.
                                if (repConnection instanceof TelephonyConnection) {
                                    // Notify Telecom of the new Connection type.
                                    // TODO: Switch out the underlying connection instead of
                                    // creating a new one and causing UI Jank.
                                    addExistingConnection(
                                            PhoneUtils.makePstnPhoneAccountHandle(newPhone),
                                            repConnection);
                                    //M: Reset emergency call flag for destroying old connection.
                                    resetTreatAsEmergencyCall(emergencyConnection);
                                    // Remove the old connection from Telecom after.
                                    emergencyConnection.setDisconnected(
                                            DisconnectCauseUtil.toTelecomDisconnectCause(
                                            android.telephony.DisconnectCause.OUTGOING_CANCELED,
                                            "Reconnecting outgoing Emergency Call."));
                                } else {
                                    /// M: CC: ECC Retry @{
                                    // Assume only one ECC exists
                                    if (TelephonyConnectionServiceUtil.getInstance()
                                            .isEccRetryOn()) {
                                        Log.d(this, "ECC Retry : clear ECC param");
                                        TelephonyConnectionServiceUtil.getInstance()
                                            .clearEccRetryParams();
                                    }
                                    /// @}
                                    emergencyConnection.setDisconnected(
                                            repConnection.getDisconnectCause());
                                    /// M: CC: to check whether the device has on-going ECC
                                    TelephonyConnectionServiceUtil.getInstance().setInEcc(false);
                                }
                                emergencyConnection.destroy();
                                /// @}

                                // If there was a failure, the resulting connection will not be a
                                // TelephonyConnection, so don't place the call, just return!
                                if (repConnection instanceof TelephonyConnection) {
                                    placeOutgoingConnection((TelephonyConnection) repConnection,
                                            newPhone, request);
                                }
                            } else {
                                placeOutgoingConnection((TelephonyConnection) emergencyConnection,
                                        newPhone, request);
                            }
                        } else {
                            /// M: CC: ECC Retry @{
                            // Assume only one ECC exists
                            // Not trigger retry since Modem fails to power on should be a bug
                            if (TelephonyConnectionServiceUtil.getInstance().isEccRetryOn()) {
                                Log.d(this, "ECC Retry : clear ECC param");
                                TelephonyConnectionServiceUtil.getInstance().clearEccRetryParams();
                            }
                            /// @}

                            Log.w(this, "onCreateOutgoingConnection, failed to turn on radio");
                            emergencyConnection.setDisconnected(
                                    DisconnectCauseUtil.toTelecomDisconnectCause(
                                    android.telephony.DisconnectCause.POWER_OFF,
                                    "Failed to turn on radio."));
                            /// M: CC: to check whether the device has on-going ECC
                            TelephonyConnectionServiceUtil.getInstance().setInEcc(false);
                            emergencyConnection.destroy();
                        }
                    }
                });
            } else {
                /// M: CC: ECC Retry @{
                if ((!TelephonyConnectionServiceUtil.getInstance().isEccRetryOn()) &&
                     isDialedByEmergencyCommand) {
                    Log.d(this, "ECC Retry : set param with Intial ECC.");
                    TelephonyConnectionServiceUtil.getInstance().setEccRetryParams(
                            request,
                            phone.getPhoneId());
                }
                /// @}

                // If the PhoneType of the Phone being used is different than the Default
                // Phone, then we need create a new Connection using that PhoneType and
                // replace it in Telecom.
                if (phone.getPhoneType() != defaultPhoneType) {
                    Connection repConnection = getTelephonyConnection(request, numberToDial,
                            isEmergencyNumber, emergencyHandle, phone);
                    // If there was a failure, the resulting connection will not be a
                    // TelephonyConnection, so don't place the call, just return!
                    if (repConnection instanceof TelephonyConnection) {
                        // M: CC: avoid redundant emergency number checking
                        placeOutgoingConnection((TelephonyConnection) repConnection, phone,
                                request, isEmergencyNumber);
                    }
                    /// M: CC: ECC Retry @{
                    // Assume only one ECC exists
                    else if (TelephonyConnectionServiceUtil.getInstance().isEccRetryOn()) {
                        Log.d(this, "ECC Retry : clear ECC param");
                        TelephonyConnectionServiceUtil.getInstance().clearEccRetryParams();
                        /// M: CC: to check whether the device has on-going ECC
                        TelephonyConnectionServiceUtil.getInstance().setInEcc(false);
                    }
                    /// @}

                    /// M: Reset the emergency call flag for destroying old connection.
                    resetTreatAsEmergencyCall(emergencyConnection);

                    // Notify Telecom of the new Connection type.
                    // TODO: Switch out the underlying connection instead of creating a new
                    // one and causing UI Jank.
                    // addExistingConnection(PhoneUtils.makePstnPhoneAccountHandle(phone),
                    //         repConnection);
                    // Remove the old connection from Telecom after.
                    emergencyConnection.setDisconnected(
                            DisconnectCauseUtil.toTelecomDisconnectCause(
                            android.telephony.DisconnectCause.OUTGOING_CANCELED,
                            "Reconnecting outgoing Emergency Call."));
                    emergencyConnection.destroy();
                    /// M: Return the new connection to Telecom directly.
                    return repConnection;
                } else {
                    // M: CC: avoid redundant emergency number checking
                    placeOutgoingConnection((TelephonyConnection) emergencyConnection,
                            phone, request, isEmergencyNumber);
                }
            }
            // Return the still unconnected GsmConnection and wait for the Radios to boot before
            // connecting it to the underlying Phone.
            return emergencyConnection;
        } else {
            if (!canAddCall() && !isEmergencyNumber) {
                Log.d(this, "onCreateOutgoingConnection, cannot add call .");
                return Connection.createFailedConnection(
                        new DisconnectCause(DisconnectCause.ERROR,
                                getApplicationContext().getText(
                                        R.string.incall_error_cannot_add_call),
                                getApplicationContext().getText(
                                        R.string.incall_error_cannot_add_call),
                                "Add call restricted due to ongoing video call"));
            }

            // Get the right phone object from the account data passed in.
            final Phone phone = getPhoneForAccount(request.getAccountHandle(), isEmergencyNumber);
            Connection resultConnection = getTelephonyConnection(request, numberToDial,
                    isEmergencyNumber, handle, phone);
            // If there was a failure, the resulting connection will not be a TelephonyConnection,
            // so don't place the call!
            if(resultConnection instanceof TelephonyConnection) {
                // M: CC: avoid redundant emergency number checking
                placeOutgoingConnection((TelephonyConnection) resultConnection,
                        phone, request, isEmergencyNumber);
            }
            return resultConnection;
        }
    }

    /**
     * @return {@code true} if any other call is disabling the ability to add calls, {@code false}
     *      otherwise.
     */
    private boolean canAddCall() {
        Collection<Connection> connections = getAllConnections();
        for (Connection connection : connections) {
            if (connection.getExtras() != null &&
                    connection.getExtras().getBoolean(Connection.EXTRA_DISABLE_ADD_CALL, false)) {
                return false;
            }
        }
        return true;
    }

    private Connection getTelephonyConnection(final ConnectionRequest request, final String number,
            boolean isEmergencyNumber, final Uri handle, Phone phone) {

        if (phone == null) {
            final Context context = getApplicationContext();
            if (context.getResources().getBoolean(R.bool.config_checkSimStateBeforeOutgoingCall)) {
                // Check SIM card state before the outgoing call.
                // Start the SIM unlock activity if PIN_REQUIRED.
                final Phone defaultPhone = PhoneFactory.getDefaultPhone();
                final IccCard icc = defaultPhone.getIccCard();
                IccCardConstants.State simState = IccCardConstants.State.UNKNOWN;
                if (icc != null) {
                    simState = icc.getState();
                }
                if (simState == IccCardConstants.State.PIN_REQUIRED) {
                    final String simUnlockUiPackage = context.getResources().getString(
                            R.string.config_simUnlockUiPackage);
                    final String simUnlockUiClass = context.getResources().getString(
                            R.string.config_simUnlockUiClass);
                    if (simUnlockUiPackage != null && simUnlockUiClass != null) {
                        Intent simUnlockIntent = new Intent().setComponent(new ComponentName(
                                simUnlockUiPackage, simUnlockUiClass));
                        simUnlockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        try {
                            context.startActivity(simUnlockIntent);
                        } catch (ActivityNotFoundException exception) {
                            Log.e(this, exception, "Unable to find SIM unlock UI activity.");
                        }
                    }
                    return Connection.createFailedConnection(
                            DisconnectCauseUtil.toTelecomDisconnectCause(
                                    android.telephony.DisconnectCause.OUT_OF_SERVICE,
                                    "SIM_STATE_PIN_REQUIRED"));
                }
            }

            Log.d(this, "onCreateOutgoingConnection, phone is null");
            /// M: CC: Error message due to CellConnMgr checking @{
            log("onCreateOutgoingConnection, use default phone for cellConnMgr");
            if (TelephonyConnectionServiceUtil.getInstance().
                    cellConnMgrShowAlerting(PhoneFactory.getDefaultPhone().getSubId())) {
                log("onCreateOutgoingConnection, cellConnMgrShowAlerting() check fail");
                return Connection.createFailedConnection(
                        DisconnectCauseUtil.toTelecomDisconnectCause(
                                android.telephony.DisconnectCause.OUTGOING_CANCELED_BY_SERVICE,
                                "cellConnMgrShowAlerting() check fail"));
            }
            /// @}
            return Connection.createFailedConnection(
                    DisconnectCauseUtil.toTelecomDisconnectCause(
                            android.telephony.DisconnectCause.OUT_OF_SERVICE, "Phone is null"));
        }

        /// M: Timing issue, radio maybe on even airplane mode on @{
        boolean isAirplaneModeOn = false;
        if (Settings.Global.getInt(phone.getContext().getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 0) > 0) {
            isAirplaneModeOn = true;
        }
        ///@}
        ///M: 4G data only @{
        if (!isAirplaneModeOn
                && TelephonyConnectionServiceUtil.getInstance().isDataOnlyMode(phone)) {
            /// M: CC: ECC Retry @{
            // Assume only one ECC exists
            // Not trigger retry since Modem fails to power on should be a bug
            if (TelephonyConnectionServiceUtil.getInstance().isEccRetryOn()) {
                log("4G data only, ECC Retry : clear ECC param");
                TelephonyConnectionServiceUtil.getInstance().clearEccRetryParams();
            }
            /// @}
            return Connection.createFailedConnection(
                    DisconnectCauseUtil.toTelecomDisconnectCause(
                    android.telephony.DisconnectCause.OUTGOING_CANCELED, null));
        }
        /// @}

        // Check both voice & data RAT to enable normal CS call,
        // when voice RAT is OOS but Data RAT is present.
        int state = phone.getServiceState().getState();
        if (state == ServiceState.STATE_OUT_OF_SERVICE) {
            int dataNetType = phone.getServiceState().getDataNetworkType();
            if (dataNetType == TelephonyManager.NETWORK_TYPE_LTE ||
                    dataNetType == TelephonyManager.NETWORK_TYPE_LTE_CA) {
                state = phone.getServiceState().getDataRegState();
            }
        }

        /// M : WFC <TO make MO call when WFC is on and radio is off> @{
        boolean isWfcEnabled = ((TelephonyManager)phone.getContext()
                .getSystemService(Context.TELEPHONY_SERVICE)).isWifiCallingAvailable();
        if (!phone.isRadioOn() && isWfcEnabled) {
            state = ServiceState.STATE_IN_SERVICE;
        }
        log("Service state:" + state + ", isAirplaneModeOn:" + isAirplaneModeOn);
        /// @}

        // If we're dialing a non-emergency number and the phone is in ECM mode, reject the call if
        // carrier configuration specifies that we cannot make non-emergency calls in ECM mode.
        if (!isEmergencyNumber && phone.isInEcm()) {
            boolean allowNonEmergencyCalls = true;
            CarrierConfigManager cfgManager = (CarrierConfigManager)
                    phone.getContext().getSystemService(Context.CARRIER_CONFIG_SERVICE);
            if (cfgManager != null) {
                allowNonEmergencyCalls = cfgManager.getConfigForSubId(phone.getSubId())
                        .getBoolean(CarrierConfigManager.KEY_ALLOW_NON_EMERGENCY_CALLS_IN_ECM_BOOL);
            }

            if (!allowNonEmergencyCalls) {
                return Connection.createFailedConnection(
                        DisconnectCauseUtil.toTelecomDisconnectCause(
                                android.telephony.DisconnectCause.CDMA_NOT_EMERGENCY,
                                "Cannot make non-emergency call in ECM mode."
                        ));
            }
        }

        if (!isEmergencyNumber) {
            /// M: CC: Error message due to VoLTE SS checking @{
            if (TelephonyConnectionServiceUtil.getInstance().
                    shouldOpenDataConnection(number, phone)) {
                log("onCreateOutgoingConnection, shouldOpenDataConnection() check fail");
                return Connection.createFailedConnection(
                        DisconnectCauseUtil.toTelecomDisconnectCause(
                                android.telephony.DisconnectCause.VOLTE_SS_DATA_OFF,
                                TelecomManagerEx.DISCONNECT_REASON_VOLTE_SS_DATA_OFF));
            }
            /// @}

            /// M: CC: Error message due to CellConnMgr checking @{
            if (TelephonyConnectionServiceUtil.getInstance().
                    cellConnMgrShowAlerting(phone.getSubId())) {
                log("onCreateOutgoingConnection, cellConnMgrShowAlerting() check fail");
                return Connection.createFailedConnection(
                        DisconnectCauseUtil.toTelecomDisconnectCause(
                                android.telephony.DisconnectCause.OUTGOING_CANCELED_BY_SERVICE,
                                "cellConnMgrShowAlerting() check fail"));
            }
            /// @}

            switch (state) {
                case ServiceState.STATE_IN_SERVICE:
                case ServiceState.STATE_EMERGENCY_ONLY:
                    break;
                case ServiceState.STATE_OUT_OF_SERVICE:
                    if (phone.isUtEnabled() && number.endsWith("#")) {
                        Log.d(this, "onCreateOutgoingConnection dial for UT");
                        break;
                    } else {
                        /// M: CC: FTA requires call should be dialed out even out of service @{
                        if (SystemProperties.getInt("gsm.gcf.testmode", 0) == 2) {
                            break;
                        }
                        /// @}
                        return Connection.createFailedConnection(
                                DisconnectCauseUtil.toTelecomDisconnectCause(
                                        android.telephony.DisconnectCause.OUT_OF_SERVICE,
                                        "ServiceState.STATE_OUT_OF_SERVICE"));
                    }
                case ServiceState.STATE_POWER_OFF:
                    return Connection.createFailedConnection(
                            DisconnectCauseUtil.toTelecomDisconnectCause(
                                    android.telephony.DisconnectCause.POWER_OFF,
                                    "ServiceState.STATE_POWER_OFF"));
                default:
                    Log.d(this, "onCreateOutgoingConnection, unknown service state: %d", state);
                    return Connection.createFailedConnection(
                            DisconnectCauseUtil.toTelecomDisconnectCause(
                                    android.telephony.DisconnectCause.OUTGOING_FAILURE,
                                    "Unknown service state " + state));
            }

            /// M: CC: TelephonyConnectionService canDial check @{
            if (!canDial(request.getAccountHandle(), number)) {
                log("onCreateOutgoingConnection, canDial() check fail");
                return Connection.createFailedConnection(
                        DisconnectCauseUtil.toTelecomDisconnectCause(
                                android.telephony.DisconnectCause.OUTGOING_FAILURE,
                                "canDial() check fail"));
            }
            /// @}
        }

        final Context context = getApplicationContext();
        if (VideoProfile.isVideo(request.getVideoState()) && isTtyModeEnabled(context) &&
                !isEmergencyNumber) {
            return Connection.createFailedConnection(DisconnectCauseUtil.toTelecomDisconnectCause(
                    android.telephony.DisconnectCause.VIDEO_CALL_NOT_ALLOWED_WHILE_TTY_ENABLED));
        }

        // Check for additional limits on CDMA phones.
        final Connection failedConnection = checkAdditionalOutgoingCallLimits(phone);
        if (failedConnection != null) {
            return failedConnection;
        }

        final TelephonyConnection connection =
                createConnectionFor(phone, null, true /* isOutgoing */, request.getAccountHandle(),
                        request.getTelecomCallId(), request.getAddress(), request.getVideoState());
        if (connection == null) {
            /// M: CC: ECC Retry @{
            // Not trigger retry since connection is null should be a bug
            // Assume only one ECC exists
            if (TelephonyConnectionServiceUtil.getInstance().isEccRetryOn()) {
                log("Fail to create connection, ECC Retry : clear ECC param");
                TelephonyConnectionServiceUtil.getInstance().clearEccRetryParams();
            }
            /// @}
            return Connection.createFailedConnection(
                    DisconnectCauseUtil.toTelecomDisconnectCause(
                            android.telephony.DisconnectCause.OUTGOING_FAILURE,
                            "Invalid phone type"));
        }

        /// M: CC: ECC Retry @{
        connection.setEmergency(isEmergencyNumber);
        /// @}

        /// M: CC: Set PhoneAccountHandle for ECC @{
        //[ALPS01794357]
        if (isEmergencyNumber) {
            final PhoneAccountHandle phoneAccountHandle;
            /// M: CC: Get iccid from system property @{
            // when IccRecords is null, (updated as RILD is reinitialized).
            // [ALPS02312211] [ALPS02325107]
            String phoneIccId = phone.getFullIccSerialNumber();
            int slotId = SubscriptionController.getInstance().getSlotId(phone.getSubId());
            if (slotId != SubscriptionManager.INVALID_SIM_SLOT_INDEX) {
                phoneIccId = !TextUtils.isEmpty(phoneIccId) ?
                        phoneIccId : TelephonyManagerEx.getDefault().getSimSerialNumber(slotId);
            }
            /// @}
            if (TextUtils.isEmpty(phoneIccId)) {
                // If No SIM is inserted, the corresponding IccId will be null,
                // take phoneId as PhoneAccountHandle::mId which is IccId originally
                phoneAccountHandle = PhoneUtils.makePstnPhoneAccountHandle(
                        Integer.toString(phone.getPhoneId()));
            } else {
                phoneAccountHandle = PhoneUtils.makePstnPhoneAccountHandle(phoneIccId);
            }
            log("ECC PhoneAccountHandle mId: " + phoneAccountHandle.getId() +
                    ", iccId: " + phoneIccId);
            connection.setAccountHandle(phoneAccountHandle);
        }
        /// @}

        connection.setAddress(handle, PhoneConstants.PRESENTATION_ALLOWED);
        connection.setInitializing();
        connection.setVideoState(request.getVideoState());

        return connection;
    }

    @Override
    public Connection onCreateIncomingConnection(
            PhoneAccountHandle connectionManagerPhoneAccount,
            ConnectionRequest request) {
        Log.i(this, "onCreateIncomingConnection, request: " + request);
        // If there is an incoming emergency CDMA Call (while the phone is in ECBM w/ No SIM),
        // make sure the PhoneAccount lookup retrieves the default Emergency Phone.
        PhoneAccountHandle accountHandle = request.getAccountHandle();
        boolean isEmergency = false;
        if (accountHandle != null && PhoneUtils.EMERGENCY_ACCOUNT_HANDLE_ID.equals(
                accountHandle.getId())) {
            log("Emergency PhoneAccountHandle is being used for incoming call... " +
                    "Treat as an Emergency Call.");
            isEmergency = true;
        }
        Phone phone = getPhoneForAccount(accountHandle, isEmergency);
        if (phone == null) {
            return Connection.createFailedConnection(
                    DisconnectCauseUtil.toTelecomDisconnectCause(
                            android.telephony.DisconnectCause.ERROR_UNSPECIFIED,
                            "Phone is null"));
        }

        Call call = phone.getRingingCall();
        if (!call.getState().isRinging()) {
            Log.i(this, "onCreateIncomingConnection, no ringing call");
            return Connection.createFailedConnection(
                    DisconnectCauseUtil.toTelecomDisconnectCause(
                            android.telephony.DisconnectCause.INCOMING_MISSED,
                            "Found no ringing call"));
        }

        com.android.internal.telephony.Connection originalConnection =
                call.getState() == Call.State.WAITING ?
                    call.getLatestConnection() : call.getEarliestConnection();
        if (isOriginalConnectionKnown(originalConnection)) {
            Log.i(this, "onCreateIncomingConnection, original connection already registered");
            return Connection.createCanceledConnection();
        }

        // We should rely on the originalConnection to get the video state.  The request coming
        // from Telecom does not know the video state of the incoming call.
        int videoState = originalConnection != null ? originalConnection.getVideoState() :
                VideoProfile.STATE_AUDIO_ONLY;

        Connection connection =
                createConnectionFor(phone, originalConnection, false /* isOutgoing */,
                        request.getAccountHandle(), request.getTelecomCallId(),
                        request.getAddress(), videoState);
        if (connection == null) {
            return Connection.createCanceledConnection();
        } else {
            return connection;
        }
    }

    @Override
    public void triggerConferenceRecalculate() {
        if (mTelephonyConferenceController.shouldRecalculate()) {
            mTelephonyConferenceController.recalculate();
        }
    }

    @Override
    public Connection onCreateUnknownConnection(PhoneAccountHandle connectionManagerPhoneAccount,
            ConnectionRequest request) {
        Log.i(this, "onCreateUnknownConnection, request: " + request);
        // Use the registered emergency Phone if the PhoneAccountHandle is set to Telephony's
        // Emergency PhoneAccount
        PhoneAccountHandle accountHandle = request.getAccountHandle();
        boolean isEmergency = false;
        if (accountHandle != null && PhoneUtils.EMERGENCY_ACCOUNT_HANDLE_ID.equals(
                accountHandle.getId())) {
            log("Emergency PhoneAccountHandle is being used for unknown call... " +
                    "Treat as an Emergency Call.");
            isEmergency = true;
        }
        Phone phone = getPhoneForAccount(accountHandle, isEmergency);
        if (phone == null) {
            return Connection.createFailedConnection(
                    DisconnectCauseUtil.toTelecomDisconnectCause(
                            android.telephony.DisconnectCause.ERROR_UNSPECIFIED,
                            "Phone is null"));
        }
        Bundle extras = request.getExtras();

        final List<com.android.internal.telephony.Connection> allConnections = new ArrayList<>();

        // Handle the case where an unknown connection has an IMS external call ID specified; we can
        // skip the rest of the guesswork and just grad that unknown call now.
        if (phone.getImsPhone() != null && extras != null &&
                extras.containsKey(ImsExternalCallTracker.EXTRA_IMS_EXTERNAL_CALL_ID)) {

            ImsPhone imsPhone = (ImsPhone) phone.getImsPhone();
            ImsExternalCallTracker externalCallTracker = imsPhone.getExternalCallTracker();
            int externalCallId = extras.getInt(ImsExternalCallTracker.EXTRA_IMS_EXTERNAL_CALL_ID,
                    -1);

            if (externalCallTracker != null) {
                com.android.internal.telephony.Connection connection =
                        externalCallTracker.getConnectionById(externalCallId);

                if (connection != null) {
                    allConnections.add(connection);
                }
            }
        }

        if (allConnections.isEmpty()) {
            final Call ringingCall = phone.getRingingCall();
            if (ringingCall.hasConnections()) {
                allConnections.addAll(ringingCall.getConnections());
            }
            final Call foregroundCall = phone.getForegroundCall();
            if ((foregroundCall.getState() != Call.State.DISCONNECTED)
                    && (foregroundCall.hasConnections())) {
                allConnections.addAll(foregroundCall.getConnections());
            }
            if (phone.getImsPhone() != null) {
                final Call imsFgCall = phone.getImsPhone().getForegroundCall();
                if ((imsFgCall.getState() != Call.State.DISCONNECTED) && imsFgCall
                        .hasConnections()) {
                    allConnections.addAll(imsFgCall.getConnections());
                }
            }
            final Call backgroundCall = phone.getBackgroundCall();
            if (backgroundCall.hasConnections()) {
                allConnections.addAll(phone.getBackgroundCall().getConnections());
            }
        }

        com.android.internal.telephony.Connection unknownConnection = null;
        for (com.android.internal.telephony.Connection telephonyConnection : allConnections) {
            if (!isOriginalConnectionKnown(telephonyConnection)) {
                unknownConnection = telephonyConnection;
                Log.d(this, "onCreateUnknownConnection: conn = " + unknownConnection);
                break;
            }
        }

        if (unknownConnection == null) {
            Log.i(this, "onCreateUnknownConnection, did not find previously unknown connection.");
            return Connection.createCanceledConnection();
        }

        // We should rely on the originalConnection to get the video state.  The request coming
        // from Telecom does not know the video state of the unknown call.
        int videoState = unknownConnection != null ? unknownConnection.getVideoState() :
                VideoProfile.STATE_AUDIO_ONLY;

        TelephonyConnection connection =
                createConnectionFor(phone, unknownConnection,
                        !unknownConnection.isIncoming() /* isOutgoing */,
                        request.getAccountHandle(), request.getTelecomCallId(),
                        request.getAddress(), videoState);

        if (connection == null) {
            return Connection.createCanceledConnection();
        } else {
            connection.updateState();
            return connection;
        }
    }

    /**
     * Conferences two connections.
     *
     * Note: The {@link android.telecom.RemoteConnection#setConferenceableConnections(List)} API has
     * a limitation in that it can only specify conferenceables which are instances of
     * {@link android.telecom.RemoteConnection}.  In the case of an {@link ImsConference}, the
     * regular {@link Connection#setConferenceables(List)} API properly handles being able to merge
     * a {@link Conference} and a {@link Connection}.  As a result when, merging a
     * {@link android.telecom.RemoteConnection} into a {@link android.telecom.RemoteConference}
     * require merging a {@link ConferenceParticipantConnection} which is a child of the
     * {@link Conference} with a {@link TelephonyConnection}.  The
     * {@link ConferenceParticipantConnection} class does not have the capability to initiate a
     * conference merge, so we need to call
     * {@link TelephonyConnection#performConference(Connection)} on either {@code connection1} or
     * {@code connection2}, one of which is an instance of {@link TelephonyConnection}.
     *
     * @param connection1 A connection to merge into a conference call.
     * @param connection2 A connection to merge into a conference call.
     */
    @Override
    public void onConference(Connection connection1, Connection connection2) {
        if (connection1 instanceof TelephonyConnection) {
            ((TelephonyConnection) connection1).performConference(connection2);
        } else if (connection2 instanceof TelephonyConnection) {
            ((TelephonyConnection) connection2).performConference(connection1);
        } else {
            Log.w(this, "onConference - cannot merge connections " +
                    "Connection1: %s, Connection2: %2", connection1, connection2);
        }
    }

    private boolean isRadioOn() {
        boolean result = false;
        for (Phone phone : PhoneFactory.getPhones()) {
            result |= phone.isRadioOn();
        }
        return result;
    }

    private Pair<WeakReference<TelephonyConnection>, List<Phone>> makeCachedConnectionPhonePair(
            TelephonyConnection c) {
        List<Phone> phones = new ArrayList<>(Arrays.asList(PhoneFactory.getPhones()));
        return new Pair<>(new WeakReference<>(c), phones);
    }

    // Check the mEmergencyRetryCache to see if it contains the TelephonyConnection. If it doesn't,
    // then it is stale. Create a new one!
    private void updateCachedConnectionPhonePair(TelephonyConnection c) {
        if (mEmergencyRetryCache == null) {
            Log.i(this, "updateCachedConnectionPhonePair, cache is null. Generating new cache");
            mEmergencyRetryCache = makeCachedConnectionPhonePair(c);
        } else {
            // Check to see if old cache is stale. If it is, replace it
            WeakReference<TelephonyConnection> cachedConnection = mEmergencyRetryCache.first;
            if (cachedConnection.get() != c) {
                Log.i(this, "updateCachedConnectionPhonePair, cache is stale. Regenerating.");
                mEmergencyRetryCache = makeCachedConnectionPhonePair(c);
            }
        }
    }

    /**
     * Returns the first Phone that has not been used yet to place the call. Any Phones that have
     * been used to place a call will have already been removed from mEmergencyRetryCache.second.
     * The phone that it excluded will be removed from mEmergencyRetryCache.second in this method.
     * @param phoneToExclude The Phone object that will be removed from our cache of available
     * phones.
     * @return the first Phone that is available to be used to retry the call.
     */
    private Phone getPhoneForRedial(Phone phoneToExclude) {
        List<Phone> cachedPhones = mEmergencyRetryCache.second;
        if (cachedPhones.contains(phoneToExclude)) {
            Log.i(this, "getPhoneForRedial, removing Phone[" + phoneToExclude.getPhoneId() +
                    "] from the available Phone cache.");
            cachedPhones.remove(phoneToExclude);
        }
        return cachedPhones.isEmpty() ? null : cachedPhones.get(0);
    }

    private void retryOutgoingOriginalConnection(TelephonyConnection c) {
        updateCachedConnectionPhonePair(c);
        Phone newPhoneToUse = getPhoneForRedial(c.getPhone());
        if (newPhoneToUse != null) {
            int videoState = c.getVideoState();
            Bundle connExtras = c.getExtras();
            Log.i(this, "retryOutgoingOriginalConnection, redialing on Phone Id: " + newPhoneToUse);
            c.clearOriginalConnection();
            placeOutgoingConnection(c, newPhoneToUse, videoState, connExtras);
        } else {
            // We have run out of Phones to use. Disconnect the call and destroy the connection.
            Log.i(this, "retryOutgoingOriginalConnection, no more Phones to use. Disconnecting.");
            c.setDisconnected(new DisconnectCause(DisconnectCause.ERROR));
            c.clearOriginalConnection();
            c.destroy();
        }
    }

    /// M: CC: add placeOutgoingConnection() with isEmergencyNumber parameter
    // to avoid redundant emergency number checking. @{
    private void placeOutgoingConnection(
            TelephonyConnection connection, Phone phone, ConnectionRequest request) {
        String number = connection.getAddress().getSchemeSpecificPart();
        boolean isEmergencyNumber = PhoneNumberUtils.isLocalEmergencyNumber(this, number);
        placeOutgoingConnection(connection, phone, request, isEmergencyNumber);
    }

    private void placeOutgoingConnection(
            TelephonyConnection connection, Phone phone, ConnectionRequest request,
            boolean isEmergencyNumber) {
        placeOutgoingConnection(connection, phone, request.getVideoState(), request.getExtras(),
                isEmergencyNumber);
    }

    private void placeOutgoingConnection(
            TelephonyConnection connection, Phone phone, int videoState, Bundle extras) {
        String number = connection.getAddress().getSchemeSpecificPart();
        boolean isEmergencyNumber = PhoneNumberUtils.isLocalEmergencyNumber(this, number);
        placeOutgoingConnection(connection, phone, videoState, extras, isEmergencyNumber);
    }

    private void placeOutgoingConnection(
            TelephonyConnection connection, Phone phone, int videoState, Bundle extras,
            boolean isEmergencyNumber) {
        String number = connection.getAddress().getSchemeSpecificPart();
        /// M: CC: Set PhoneAccountHandle for ECC @{
        //[ALPS01794357]
        if (isEmergencyNumber) {
            final PhoneAccountHandle phoneAccountHandle;
            String phoneIccId = phone.getFullIccSerialNumber();
            int slotId = SubscriptionController.getInstance().getSlotId(phone.getSubId());
            if (slotId != SubscriptionManager.INVALID_SIM_SLOT_INDEX) {
                phoneIccId = !TextUtils.isEmpty(phoneIccId) ?
                        phoneIccId : TelephonyManagerEx.getDefault().getSimSerialNumber(slotId);
            }
            if (TextUtils.isEmpty(phoneIccId)) {
                // If No SIM is inserted, the corresponding IccId will be null,
                // take phoneId as PhoneAccountHandle::mId which is IccId originally
                phoneAccountHandle = PhoneUtils.makePstnPhoneAccountHandle(
                        Integer.toString(phone.getPhoneId()));
            } else {
                phoneAccountHandle = PhoneUtils.makePstnPhoneAccountHandle(phoneIccId);
            }
            log("placeOutgoingConnection, set back account mId: " + phoneAccountHandle.getId() +
                    ", iccId: " + phoneIccId);
            connection.setAccountHandle(phoneAccountHandle);
            TelephonyConnectionServiceUtil.getInstance().setEccPhoneType(phone.getPhoneType());
        }
        /// @}

        com.android.internal.telephony.Connection originalConnection = null;
        try {
            if (phone != null) {
                originalConnection = phone.dial(number, null, videoState, extras);
            }
        } catch (CallStateException e) {
            Log.e(this, e, "placeOutgoingConnection, phone.dial exception: " + e);
            int cause = android.telephony.DisconnectCause.OUTGOING_FAILURE;
            if (e.getError() == CallStateException.ERROR_DISCONNECTED) {
                cause = android.telephony.DisconnectCause.OUT_OF_SERVICE;
            }
            /// M: Since ussd is through 3G protocol  it will cause ims call is disconnected. @{
            if (ImsPhone.USSD_DURING_IMS_INCALL.equals(e.getMessage())) {
                Context context = phone.getContext();
                //ALPS02765764, modify disconnect cause to avoid InCallUI to display dialog
                cause = android.telephony.DisconnectCause.DIALED_MMI;
                Toast.makeText(context,
                    context.getString(R.string.incall_error_call_failed), Toast.LENGTH_SHORT)
                        .show();
            }
            /// @}
            /// M: CC: ECC Retry @{
            // Assume only one ECC exists
            if (TelephonyConnectionServiceUtil.getInstance().isEccRetryOn()) {
                log("ECC Retry : clear ECC param");
                TelephonyConnectionServiceUtil.getInstance().clearEccRetryParams();
            }
            /// @}
            connection.setDisconnected(DisconnectCauseUtil.toTelecomDisconnectCause(
                    cause, e.getMessage()));
            /// M: CC: to check whether the device has on-going ECC
            TelephonyConnectionServiceUtil.getInstance().setInEcc(false);
            /// M: CC: Destroy TelephonyConnection if framework fails to dial @{
            connection.destroy();
            /// @}
            return;
        }

        if (originalConnection == null) {
            int telephonyDisconnectCause = android.telephony.DisconnectCause.OUTGOING_FAILURE;
            // On GSM phones, null connection means that we dialed an MMI code
            if (phone.getPhoneType() == PhoneConstants.PHONE_TYPE_GSM) {
                Log.d(this, "dialed MMI code");
                telephonyDisconnectCause = android.telephony.DisconnectCause.DIALED_MMI;
                final Intent intent = new Intent(this, MMIDialogActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
                /// M: CC: DSDS bug fix @{
                // Pass phoneID via intent to MMIDialog
                intent.putExtra("ID", phone.getPhoneId());
                /// @}
                startActivity(intent);
            }
            Log.d(this, "placeOutgoingConnection, phone.dial returned null");
            /// M: CC: ECC Retry @{
            // Assume only one ECC exists
            if (TelephonyConnectionServiceUtil.getInstance().isEccRetryOn()) {
                Log.d(this, "ECC Retry : clear ECC param");
                TelephonyConnectionServiceUtil.getInstance().clearEccRetryParams();
            }
            /// @}
            connection.setDisconnected(DisconnectCauseUtil.toTelecomDisconnectCause(
                    telephonyDisconnectCause, "Connection is null"));
            /// M: CC: to check whether the device has on-going ECC
            TelephonyConnectionServiceUtil.getInstance().setInEcc(false);
            /// M: CC: Destroy TelephonyConnection if framework fails to dial @{
            connection.destroy();
            /// @}
        } else {
            connection.setOriginalConnection(originalConnection);
        }
    }
    /// @}

    private TelephonyConnection createConnectionFor(
            Phone phone,
            com.android.internal.telephony.Connection originalConnection,
            boolean isOutgoing,
            PhoneAccountHandle phoneAccountHandle,
            String telecomCallId,
            Uri address,
            int videoState) {
        TelephonyConnection returnConnection = null;
        int phoneType = phone.getPhoneType();
        /// M: CC: Vzw/CTVolte ECC @{
        /*
        if (phoneType == TelephonyManager.PHONE_TYPE_GSM) {
            returnConnection = new GsmConnection(originalConnection, telecomCallId);
        } else if (phoneType == TelephonyManager.PHONE_TYPE_CDMA) {
            boolean allowsMute = allowsMute(phone);
            returnConnection = new CdmaConnection(originalConnection, mEmergencyTonePlayer,
                    allowsMute, isOutgoing, telecomCallId);
        }
        */
        boolean allowsMute = allowsMute(phone);
        returnConnection = new GsmCdmaConnection(phoneType, originalConnection, telecomCallId,
                mEmergencyTonePlayer, allowsMute, isOutgoing);
        /// @}
        if (returnConnection != null) {
            // Listen to Telephony specific callbacks from the connection
            returnConnection.addTelephonyConnectionListener(mTelephonyConnectionListener);
            returnConnection.setVideoPauseSupported(
                    TelecomAccountRegistry.getInstance(this).isVideoPauseSupported(
                            phoneAccountHandle));
        }
        return returnConnection;
    }

    private boolean isOriginalConnectionKnown(
            com.android.internal.telephony.Connection originalConnection) {
        for (Connection connection : getAllConnections()) {
            if (connection instanceof TelephonyConnection) {
                TelephonyConnection telephonyConnection = (TelephonyConnection) connection;
                if (telephonyConnection.getOriginalConnection() == originalConnection) {
                    return true;
                }
            }
        }
        return false;
    }

    private Phone getPhoneForAccount(PhoneAccountHandle accountHandle, boolean isEmergency) {
        Phone chosenPhone = null;
        int subId = PhoneUtils.getSubIdForPhoneAccountHandle(accountHandle);
        if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            int phoneId = SubscriptionController.getInstance().getPhoneId(subId);
            chosenPhone = PhoneFactory.getPhone(phoneId);
        }
        // If this is an emergency call and the phone we originally planned to make this call
        // with is not in service or was invalid, try to find one that is in service, using the
        // default as a last chance backup.
        if (isEmergency && (chosenPhone == null || ServiceState.STATE_IN_SERVICE != chosenPhone
                .getServiceState().getState())) {
            Log.d(this, "getPhoneForAccount: phone for phone acct handle %s is out of service "
                    + "or invalid for emergency call.", accountHandle);
            chosenPhone = getFirstPhoneForEmergencyCall();
            Log.d(this, "getPhoneForAccount: using subId: " +
                    (chosenPhone == null ? "null" : chosenPhone.getSubId()));
        }
        return chosenPhone;
    }

    /**
     * Retrieves the most sensible Phone to use for an emergency call using the following Priority
     *  list (for multi-SIM devices):
     *  1) The User's SIM preference for Voice calling
     *  2) The First Phone that is currently IN_SERVICE or is available for emergency calling
     *  3) The Phone with more Capabilities.
     *  4) The First Phone that has a SIM card in it (Starting from Slot 0...N)
     *  5) The Default Phone (Currently set as Slot 0)
     */
    private Phone getFirstPhoneForEmergencyCall() {
        // 1)
        int phoneId = SubscriptionManager.getDefaultVoicePhoneId();
        if (phoneId != SubscriptionManager.INVALID_PHONE_INDEX) {
            Phone defaultPhone = PhoneFactory.getPhone(phoneId);
            if (defaultPhone != null && isAvailableForEmergencyCalls(defaultPhone)) {
                return defaultPhone;
            }
        }

        Phone firstPhoneWithSim = null;
        int phoneCount = TelephonyManager.getDefault().getPhoneCount();
        List<Pair<Integer, Integer>> phoneNetworkType = new ArrayList<>(phoneCount);
        for (int i = 0; i < phoneCount; i++) {
            Phone phone = PhoneFactory.getPhone(i);
            if (phone == null)
                continue;
            // 2)
            if (isAvailableForEmergencyCalls(phone)) {
                // the slot has the radio on & state is in service.
                Log.i(this, "getFirstPhoneForEmergencyCall, radio on & in service, Phone Id:" + i);
                return phone;
            }
            // 3)
            // Store the RAF Capabilities for sorting later only if there are capabilities to sort.
            int radioAccessFamily = phone.getRadioAccessFamily();
            if(RadioAccessFamily.getHighestRafCapability(radioAccessFamily) != 0) {
                phoneNetworkType.add(new Pair<>(i, radioAccessFamily));
                Log.i(this, "getFirstPhoneForEmergencyCall, RAF:" +
                        Integer.toHexString(radioAccessFamily) + " saved for Phone Id:" + i);
            }
            // 4)
            if (firstPhoneWithSim == null && TelephonyManager.getDefault().hasIccCard(i)) {
                // The slot has a SIM card inserted, but is not in service, so keep track of this
                // Phone. Do not return because we want to make sure that none of the other Phones
                // are in service (because that is always faster).
                Log.i(this, "getFirstPhoneForEmergencyCall, SIM card inserted, Phone Id:" + i);
                firstPhoneWithSim = phone;
            }
        }
        // 5)
        if (firstPhoneWithSim == null && phoneNetworkType.isEmpty()) {
            // No SIMs inserted, get the default.
            ///M: Pick proper phone (with main capability) for ECC @{
            //Log.d(this, "getFirstPhoneForEmergencyCall, return default phone");
            //return PhoneFactory.getDefaultPhone();
            phoneId = findMainCapabilityPhoneId();
            if (phoneId == -1) {
                Log.i(this, "getFirstPhoneForEmergencyCall, return default phone");
                return PhoneFactory.getDefaultPhone();
            }
            log("getFirstPhoneForEmergencyCall, return 3G-capable Phone Id:" + phoneId);
            return PhoneFactory.getPhone(phoneId);
            /// @}
        } else {
            // 3)
            final Phone firstOccupiedSlot = firstPhoneWithSim;
            if (!phoneNetworkType.isEmpty()) {
                // Only sort if there are enough elements to do so.
                if(phoneNetworkType.size() > 1) {
                    Collections.sort(phoneNetworkType, (o1, o2) -> {
                        // First start by sorting by number of RadioAccessFamily Capabilities.
                        int compare = Integer.bitCount(o1.second) - Integer.bitCount(o2.second);
                        if (compare == 0) {
                            // Sort by highest RAF Capability if the number is the same.
                            compare = RadioAccessFamily.getHighestRafCapability(o1.second) -
                                    RadioAccessFamily.getHighestRafCapability(o2.second);
                            if (compare == 0 && firstOccupiedSlot != null) {
                                // If the RAF capability is the same, choose based on whether or not
                                // any of the slots are occupied with a SIM card (if both are,
                                // always choose the first).
                                if (o1.first == firstOccupiedSlot.getPhoneId()) {
                                    return 1;
                                } else if (o2.first == firstOccupiedSlot.getPhoneId()) {
                                    return -1;
                                }
                                // Compare is still 0, return equal.
                            }
                        }
                        return compare;
                    });
                }
                int mostCapablePhoneId = phoneNetworkType.get(phoneNetworkType.size()-1).first;
                Log.i(this, "getFirstPhoneForEmergencyCall, Using Phone Id: " + mostCapablePhoneId +
                        "with highest capability");
                return PhoneFactory.getPhone(mostCapablePhoneId);
            } else {
                // 4)
                return firstPhoneWithSim;
            }
        }
    }

    ///M: Pick proper phone (with main capability) for ECC @{
    private int findMainCapabilityPhoneId() {
        int result = 0;
        // TODO: This may change if sim switch implementation change
        int switchStatus = Integer.valueOf(
                SystemProperties.get("persist.radio.simswitch", "1"));
        result = switchStatus - 1;
        if (result < 0 || result >= TelephonyManager.getDefault().getPhoneCount()) {
            log("findMainCapabilityPhoneId(): Invalid phone id, return -1");
            return -1;
        } else {
            log("findMainCapabilityPhoneId(): return " + result);
            return result;
        }
    }
    /// @}


    /**
     * Returns true if the state of the Phone is IN_SERVICE or available for emergency calling only.
     */
    private boolean isAvailableForEmergencyCalls(Phone phone) {
        return ServiceState.STATE_IN_SERVICE == phone.getServiceState().getState() ||
                phone.getServiceState().isEmergencyOnly();
    }

    /**
     * Determines if the connection should allow mute.
     *
     * @param phone The current phone.
     * @return {@code True} if the connection should allow mute.
     */
    private boolean allowsMute(Phone phone) {
        // For CDMA phones, check if we are in Emergency Callback Mode (ECM).  Mute is disallowed
        // in ECM mode.
        if (phone.getPhoneType() == TelephonyManager.PHONE_TYPE_CDMA) {
            if (phone.isInEcm()) {
                return false;
            }
        }

        return true;
    }

    @Override
    public void removeConnection(Connection connection) {
        /// M: CC: ECC Retry @{
        boolean handleEcc = false;
        if (TelephonyConnectionServiceUtil.getInstance().isEccRetryOn()) {
            if (connection instanceof TelephonyConnection) {
                if (((TelephonyConnection) connection).shouldTreatAsEmergencyCall()) {
                    handleEcc = true;
                }
            }
        }

        if (handleEcc) {
            log("ECC Retry: remove connection.");
            TelephonyConnectionServiceUtil.getInstance().setEccRetryCallId(
                    super.removeConnectionInternal(connection));
        } else { //Original flow
            super.removeConnection(connection);
        }
        /// @}
        if (connection instanceof TelephonyConnection) {
            TelephonyConnection telephonyConnection = (TelephonyConnection) connection;
            telephonyConnection.removeTelephonyConnectionListener(mTelephonyConnectionListener);
        }
    }

    /**
     * When a {@link TelephonyConnection} has its underlying original connection configured,
     * we need to add it to the correct conference controller.
     *
     * @param connection The connection to be added to the controller
     */
    public void addConnectionToConferenceController(TelephonyConnection connection) {
        // TODO: Need to revisit what happens when the original connection for the
        // TelephonyConnection changes.  If going from CDMA --> GSM (for example), the
        // instance of TelephonyConnection will still be a CdmaConnection, not a GsmConnection.
        // The CDMA conference controller makes the assumption that it will only have CDMA
        // connections in it, while the other conference controllers aren't as restrictive.  Really,
        // when we go between CDMA and GSM we should replace the TelephonyConnection.
        if (connection.isImsConnection()) {
            Log.d(this, "Adding IMS connection to conference controller: " + connection);
            mImsConferenceController.add(connection);
            mTelephonyConferenceController.remove(connection);
            /// M: CC: Vzw/CTVolte ECC @{
            //if (connection instanceof CdmaConnection) {
            if (((GsmCdmaConnection) connection).getPhoneType() ==
                                PhoneConstants.PHONE_TYPE_CDMA) {
            ///@}
                mCdmaConferenceController.remove((GsmCdmaConnection) connection);
            }
        } else {
            int phoneType = connection.getCall().getPhone().getPhoneType();
            if (phoneType == TelephonyManager.PHONE_TYPE_GSM) {
                // TODO: M: SS project ECC change feature
                Log.d(this, "Adding GSM connection to conference controller: " + connection);
                mTelephonyConferenceController.add(connection);
                /// M: CC: Vzw/CTVolte ECC @{
                //if (connection instanceof CdmaConnection) {
                //    mCdmaConferenceController.remove((CdmaConnection) connection);
                if (((GsmCdmaConnection) connection).getPhoneType() ==
                        PhoneConstants.PHONE_TYPE_CDMA) {
                    mCdmaConferenceController.remove((GsmCdmaConnection) connection);
                /// @}
                }
            } else if (phoneType == TelephonyManager.PHONE_TYPE_CDMA) {
                /// M: CC: Vzw/CTVolte ECC @{
                //if (connection instanceof CdmaConnection) {
                if (((GsmCdmaConnection) connection).getPhoneType() ==
                        PhoneConstants.PHONE_TYPE_CDMA) {
                    Log.d(this, "Adding CDMA connection to conference controller: " + connection);
                    //mCdmaConferenceController.add((CdmaConnection) connection);
                    mCdmaConferenceController.add((GsmCdmaConnection) connection);
                    mTelephonyConferenceController.remove(connection);
                }
            }
            Log.d(this, "Removing connection from IMS conference controller: " + connection);
            mImsConferenceController.remove(connection);
        }
    }

    /**
     * Create a new CDMA connection. CDMA connections have additional limitations when creating
     * additional calls which are handled in this method.  Specifically, CDMA has a "FLASH" command
     * that can be used for three purposes: merging a call, swapping unmerged calls, and adding
     * a new outgoing call. The function of the flash command depends on the context of the current
     * set of calls. This method will prevent an outgoing call from being made if it is not within
     * the right circumstances to support adding a call.
     */
    private Connection checkAdditionalOutgoingCallLimits(Phone phone) {
        if (phone.getPhoneType() == TelephonyManager.PHONE_TYPE_CDMA) {
            // Check to see if any CDMA conference calls exist, and if they do, check them for
            // limitations.
            for (Conference conference : getAllConferences()) {
                if (conference instanceof CdmaConference) {
                    CdmaConference cdmaConf = (CdmaConference) conference;

                    // If the CDMA conference has not been merged, add-call will not work, so fail
                    // this request to add a call.
                    if (cdmaConf.can(Connection.CAPABILITY_MERGE_CONFERENCE)) {
                        return Connection.createFailedConnection(new DisconnectCause(
                                    DisconnectCause.RESTRICTED,
                                    null,
                                    getResources().getString(R.string.callFailed_cdma_call_limit),
                                    "merge-capable call exists, prevent flash command."));
                    }
                }
            }
        }

        return null; // null means nothing went wrong, and call should continue.
    }

    private boolean isTtyModeEnabled(Context context) {
        return (android.provider.Settings.Secure.getInt(
                context.getContentResolver(),
                android.provider.Settings.Secure.PREFERRED_TTY_MODE,
                TelecomManager.TTY_MODE_OFF) != TelecomManager.TTY_MODE_OFF);
    }

    /// M: CC: TelephonyConnectionService canDial check @{
    protected TelephonyConnection getFgConnection() {

        for (Connection c : getAllConnections()) {

            if (!(c instanceof TelephonyConnection)) {
                // the connection may be ConferenceParticipantConnection.
                continue;
            }

            TelephonyConnection tc = (TelephonyConnection) c;

            if (tc.getCall() == null) {
                continue;
            }

            Call.State s = tc.getCall().getState();

            // it assume that only one Fg call at the same time
            if (s == Call.State.ACTIVE || s == Call.State.DIALING || s == Call.State.ALERTING) {
                return tc;
            }
        }
        return null;
    }

    protected List<TelephonyConnection> getBgConnection() {

        ArrayList<TelephonyConnection> connectionList = new ArrayList<TelephonyConnection>();

        for (Connection c : getAllConnections()) {

            if (!(c instanceof TelephonyConnection)) {
                // the connection may be ConferenceParticipantConnection.
                continue;
            }

            TelephonyConnection tc = (TelephonyConnection) c;

            if (tc.getCall() == null) {
                continue;
            }

            Call.State s = tc.getCall().getState();

            // it assume the ringing call won't have more than one connection
            if (s == Call.State.HOLDING) {
                connectionList.add(tc);
            }
        }
        return connectionList;
    }

    protected List<TelephonyConnection> getRingingConnection() {

        ArrayList<TelephonyConnection> connectionList = new ArrayList<TelephonyConnection>();

        for (Connection c : getAllConnections()) {

            if (!(c instanceof TelephonyConnection)) {
                // the connection may be ConferenceParticipantConnection.
                continue;
            }

            TelephonyConnection tc = (TelephonyConnection) c;

            if (tc.getCall() == null) {
                continue;
            }

            // it assume the ringing call won't have more than one connection
            if (tc.getCall().getState().isRinging()) {
                connectionList.add(tc);
            }
        }
        return connectionList;
    }

    protected int getFgCallCount() {
        if (getFgConnection() != null) {
            return 1;
        }
        return 0;
    }

    protected int getBgCallCount() {
        return getBgConnection().size();
    }

    protected int getRingingCallCount() {
        return getRingingConnection().size();
    }

    @Override
    public boolean canDial(PhoneAccountHandle accountHandle, String dialString) {

        boolean hasRingingCall = (getRingingCallCount() > 0);
        boolean hasActiveCall = (getFgCallCount() > 0);
        boolean bIsInCallMmiCommands = isInCallMmiCommands(dialString);
        Call.State fgCallState = Call.State.IDLE;

        Phone phone = getPhoneForAccount(accountHandle, false);

        /* bIsInCallMmiCommands == true only when dialphone == activephone */
        if (bIsInCallMmiCommands && hasActiveCall) {
            /// M: ALPS02123516. IMS incall MMI checking. @{
            /// M: ALPS02344383. null pointer check. @{
            if (phone != null && phone != getFgConnection().getPhone()
                    && phone.getImsPhone() != null
                    && phone.getImsPhone() != getFgConnection().getPhone()) {
                bIsInCallMmiCommands = false;
                log("phone is different, set bIsInCallMmiCommands to false");
            }
            /// @}
        }

        TelephonyConnection fConnection = getFgConnection();
        if (fConnection != null) {
            Call fCall = fConnection.getCall();
            if (fCall != null) {
                fgCallState = fCall.getState();
            }
        }

        /* Block dial if one of the following cases happens
        * 1. ECC exists in either phone
        * 2. has ringing call and the current dialString is not inCallMMI
        * 3. foreground connections in TelephonyConnectionService (both phones) are DISCONNECTING
        *
        * Different from AOSP canDial() in CallTracker which only checks state of current phone
        */
        boolean isECCExists = TelephonyConnectionServiceUtil.getInstance().isECCExists();
        boolean result = (!isECCExists
                && !(hasRingingCall && !bIsInCallMmiCommands)
                && (fgCallState != Call.State.DISCONNECTING));

        if (result == false) {
            log("canDial"
                    + " hasRingingCall=" + hasRingingCall
                    + " hasActiveCall=" + hasActiveCall
                    + " fgCallState=" + fgCallState
                    + " getFgConnection=" + fConnection
                    + " getRingingConnection=" + getRingingConnection()
                    + " bECCExists=" + isECCExists);
        }
        return result;
    }

    private boolean isInCallMmiCommands(String dialString) {
        boolean result = false;
        char ch = dialString.charAt(0);

        switch (ch) {
            case '0':
            case '3':
            case '4':
            case '5':
                if (dialString.length() == 1) {
                    result = true;
                }
                break;

            case '1':
            case '2':
                if (dialString.length() == 1 || dialString.length() == 2) {
                    result = true;
                }
                break;

            default:
                break;
        }

        return result;
    }
    /// @}

    /// M: CC: Interface for ECT @{
    @Override
    public boolean canTransfer(Connection bgConnection) {

        if (bgConnection == null) {
            log("canTransfer: connection is null");
            return false;
        }

        if (!(bgConnection instanceof TelephonyConnection)) {
            // the connection may be ConferenceParticipantConnection.
            log("canTransfer: the connection isn't telephonyConnection");
            return false;
        }

        TelephonyConnection bConnection = (TelephonyConnection) bgConnection;

        Phone activePhone = null;
        Phone heldPhone = null;

        TelephonyConnection fConnection = getFgConnection();
        if (fConnection != null) {
            activePhone = fConnection.getPhone();
        }

        if (bgConnection != null) {
            heldPhone = bConnection.getPhone();
        }

        return (heldPhone == activePhone && activePhone.canTransfer());
    }

    /**
     * Check whether IMS ECT can be performed on a certain connection.
     *
     * @param connection The connection to be transferred
     * @return true allowed false disallowed
     * @hide
     */
    public boolean canBlindAssuredTransfer(Connection connection) {
        if (connection == null) {
            Log.d(this, "canBlindAssuredTransfer: connection is null");
            return false;
        }

        if (!(connection instanceof TelephonyConnection)) {
            // the connection may be ConferenceParticipantConnection.
            Log.d(this, "canBlindAssuredTransfer: the connection isn't telephonyConnection");
            return false;
        } else if (((TelephonyConnection) connection).isImsConnection() == false) {
            Log.d(this, "canBlindAssuredTransfer: the connection is not an IMS connection");
            return false;
        } else if (canTransfer(connection)) {
            // We only allow one kind of transfer at same time. If it can execute consultative
            // transfer, then we disable blind/assured transfer capability.
            Log.d(this, "canBlindAssuredTransfer: the connection has consultative ECT capability");
            return false;
        }

        return true;
    }
    /// @}

    /// M: CC: Proprietary CRSS handling @{
    @Override
    protected void forceSuppMessageUpdate(Connection conn) {
        TelephonyConnectionServiceUtil.getInstance().forceSuppMessageUpdate(
                (TelephonyConnection) conn);
    }
    /// @}

    /// M: For VoLTE enhanced conference call. @{
    /**
     * This can be used by telecom to either create a new outgoing conference call or
     * attach to an existing incoming conference call.
     */
    @Override
    protected Conference onCreateConference(
            final PhoneAccountHandle callManagerAccount,
            final String conferenceCallId,
            final ConnectionRequest request,
            final List<String> numbers,
            boolean isIncoming) {

        if (!numbers.isEmpty() && !canDial(request.getAccountHandle(), numbers.get(0))) {
            Log.d(this, "onCreateConference(), canDial check fail");
            /// M: ALPS02331568.  Should reture the failed conference. @{
            return TelephonyConnectionServiceUtil.getInstance().createFailedConference(
                android.telephony.DisconnectCause.OUTGOING_FAILURE,
                "canDial() check fail");
            /// @}
        }

        Phone phone = getPhoneForAccount(request.getAccountHandle(), false);

        /// M: ALPS02209724. Toast if there are more than 5 numbers.
        /// M: ALPS02331568. Take away null-check for numbers. @{
        if (!isIncoming
                && numbers.size() > ImsConference.IMS_CONFERENCE_MAX_SIZE) {
            Log.d(this, "onCreateConference(), more than 5 numbers");
            if (phone != null) {
                ImsConference.toastWhenConferenceIsFull(phone.getContext());
            }
            return TelephonyConnectionServiceUtil.getInstance().createFailedConference(
                    android.telephony.DisconnectCause.OUTGOING_FAILURE,
                    "more than 5 numbers");
        }
        /// @}

        return TelephonyConnectionServiceUtil.getInstance().createConference(
            mImsConferenceController,
            phone,
            request,
            numbers,
            isIncoming);
    }
    /// @}

    /// M: For VoLTE conference SRVCC. @{
    /**
     * perform Ims Conference SRVCC.
     * @param imsConf the ims conference.
     * @param radioConnections the new created radioConnection
     * @hide
     */
    void performImsConferenceSRVCC(
            Conference imsConf,
            ArrayList<com.android.internal.telephony.Connection> radioConnections,
            String telecomCallId) {
        if (imsConf == null) {
            Log.e(this, new CallStateException(),
                "performImsConferenceSRVCC(): abnormal case, imsConf is null");
            return;
        }

        if (radioConnections == null || radioConnections.size() < 2) {
            Log.e(this, new CallStateException(),
                "performImsConferenceSRVCC(): abnormal case, newConnections is null");
            return;
        }

        if (radioConnections.get(0) == null || radioConnections.get(0).getCall() == null ||
                radioConnections.get(0).getCall().getPhone() == null) {
            Log.e(this, new CallStateException(),
                "performImsConferenceSRVCC(): abnormal case, can't get phone instance");
            return;
        }

        /// M: CC: new TelephonyConference with phoneAccountHandle @{
        Phone phone = radioConnections.get(0).getCall().getPhone();
        PhoneAccountHandle handle = PhoneUtils.makePstnPhoneAccountHandle(phone);
        TelephonyConference newConf = new TelephonyConference(handle);
        /// @}

        replaceConference(imsConf, (Conference) newConf);
        mTelephonyConferenceController.setHandoveredConference(newConf);

        // we need to follow the order below:
        // 1. new empty GsmConnection
        // 2. addExistingConnection (and it will be added to TelephonyConferenceController)
        // 3. config originalConnection.
        // Then UI will not flash the participant calls during SRVCC.
        ArrayList<GsmCdmaConnection> newGsmCdmaConnections = new ArrayList<GsmCdmaConnection>();
        for (com.android.internal.telephony.Connection radioConn : radioConnections) {
            GsmCdmaConnection connection = new GsmCdmaConnection(PhoneConstants.PHONE_TYPE_GSM,
                    null, telecomCallId, null, false, false);
            /// M: ALPS02136977. Sets address first for formatted dump log.
            connection.setAddress(
                    Uri.fromParts(PhoneAccount.SCHEME_TEL, radioConn.getAddress(), null),
                    PhoneConstants.PRESENTATION_ALLOWED);
            newGsmCdmaConnections.add(connection);

            addExistingConnection(handle, connection);
            connection.addTelephonyConnectionListener(mTelephonyConnectionListener);
        }

        for (int i = 0; i < newGsmCdmaConnections.size(); i++) {
            newGsmCdmaConnections.get(i).setOriginalConnection(radioConnections.get(i));
        }
    }
    /// @}

    /// M: For ECC change feature @{
    private void startTurnOnRadio(final Connection connection,
            final ConnectionRequest request, final Uri emergencyHandle, String number) {
        // Get the right phone object from the account data passed in.
        final Phone defaultPhone = getPhoneForAccount(request.getAccountHandle(), true);
        final Phone phone = TelephonyConnectionServiceUtil.getInstance()
                .selectPhoneBySpecialEccRule(request.getAccountHandle(), number, defaultPhone);
        ///M: 4G data only @{
        if (TelephonyConnectionServiceUtil.getInstance().isDataOnlyMode(phone)) {
            log("startTurnOnRadio, 4G data only");
            /// M: CC: ECC Retry @{
            // Assume only one ECC exists
            // Not trigger retry since Modem fails to power on should be a bug
            if (TelephonyConnectionServiceUtil.getInstance().isEccRetryOn()) {
                log("ECC Retry : clear ECC param");
                TelephonyConnectionServiceUtil.getInstance().clearEccRetryParams();
            }
            /// @}
            connection.setDisconnected(
                    DisconnectCauseUtil.toTelecomDisconnectCause(
                    android.telephony.DisconnectCause.OUTGOING_CANCELED,
                    null));
            /// M: CC: to check whether the device has on-going ECC
            TelephonyConnectionServiceUtil.getInstance().setInEcc(false);
            connection.destroy();
            return;
        }
        /// @}
        /// M: CC: ECC Retry @{
        /* If current phone number will be treated as normal call in Telephony Framework,
           do not need to enable ECC retry mechanism */
        boolean isDialedByEmergencyCommand = PhoneNumberUtils.isEmergencyNumber(number);
        if ((!TelephonyConnectionServiceUtil.getInstance().isEccRetryOn()) &&
              isDialedByEmergencyCommand) {
            log("ECC Retry : set param with Intial ECC.");
            TelephonyConnectionServiceUtil.getInstance().setEccRetryParams(
                    request,
                    phone.getPhoneId());
        }
        /// @}

        final int defaultPhoneType = PhoneFactory.getDefaultPhone().getPhoneType();
        if (mEmergencyCallHelper == null) {
            mEmergencyCallHelper = new EmergencyCallHelper(this);
        }
        mEmergencyCallHelper.enableEmergencyCalling(new EmergencyCallStateListener.Callback() {
            @Override
            public void onComplete(EmergencyCallStateListener listener, boolean isRadioReady) {
                if (connection.getState() == Connection.STATE_DISCONNECTED) {
                    Log.d(this, "startTurnOnRadio, connection disconnect");
                    /// M: CC: to check whether the device has on-going ECC
                    TelephonyConnectionServiceUtil.getInstance().setInEcc(false);
                    return;
                }
                if (isRadioReady) {
                    // If the PhoneType of the Phone being used is different than the Default
                    // Phone, then we need create a new Connection using that PhoneType and
                    // replace it in Telecom.
                    if (phone.getPhoneType() != defaultPhoneType) {
                        Connection repConnection = getTelephonyConnection(request, number,
                                true, emergencyHandle, phone);
                        /// M: Modify the follow to handle the no sound issue. @{
                        // 1. Add the new connection into Telecom;
                        // 2. Disconnect the old connection;
                        // 3. Place the new connection.
                        if (repConnection instanceof TelephonyConnection) {
                            addExistingConnection(PhoneUtils.makePstnPhoneAccountHandle(phone),
                                    repConnection);
                            // Reset the emergency call flag for destroying old connection.
                            resetTreatAsEmergencyCall(connection);
                            connection.setDisconnected(
                                    DisconnectCauseUtil.toTelecomDisconnectCause(
                                            android.telephony.DisconnectCause.OUTGOING_CANCELED,
                                            "Reconnecting outgoing Emergency Call."));
                        } else {
                            /// M: CC: ECC Retry @{
                            // Assume only one ECC exists
                            if (TelephonyConnectionServiceUtil.getInstance().isEccRetryOn()) {
                                Log.d(this, "ECC Retry : clear ECC param");
                                TelephonyConnectionServiceUtil.getInstance().clearEccRetryParams();
                            }
                            /// @}
                            connection.setDisconnected(repConnection.getDisconnectCause());
                            /// M: CC: to check whether the device has on-going ECC
                            TelephonyConnectionServiceUtil.getInstance().setInEcc(false);
                        }
                        connection.destroy();
                        /// @}

                        // If there was a failure, the resulting connection will not be a
                        // TelephonyConnection, so don't place the call, just return!
                        if (repConnection instanceof TelephonyConnection) {
                            placeOutgoingConnection((TelephonyConnection) repConnection,
                                    phone, request);
                        }
                        // Notify Telecom of the new Connection type.
                        // TODO: Switch out the underlying connection instead of creating a new
                        // one and causing UI Jank.
                        // addExistingConnection(PhoneUtils.makePstnPhoneAccountHandle(phone),
                        //         repConnection);
                        // Remove the old connection from Telecom after.
                        // connection.setDisconnected(
                        //         DisconnectCauseUtil.toTelecomDisconnectCause(
                        //                 android.telephony.DisconnectCause.OUTGOING_CANCELED,
                        //                 "Reconnecting outgoing Emergency Call."));
                        // connection.destroy();
                    } else {
                        placeOutgoingConnection((TelephonyConnection) connection,
                                phone, request);
                    }
                } else {
                    /// M: CC: ECC Retry @{
                    // Assume only one ECC exists
                    // Not trigger retry since Modem fails to power on should be a bug
                    if (TelephonyConnectionServiceUtil.getInstance().isEccRetryOn()) {
                        Log.d(this, "ECC Retry : clear ECC param");
                        TelephonyConnectionServiceUtil.getInstance()
                                .clearEccRetryParams();
                    }
                    /// @}
                    Log.d(this, "startTurnOnRadio, failed to turn on radio");
                    connection.setDisconnected(
                            DisconnectCauseUtil.toTelecomDisconnectCause(
                            android.telephony.DisconnectCause.POWER_OFF,
                            "Failed to turn on radio."));
                    /// M: CC: to check whether the device has on-going ECC
                    TelephonyConnectionServiceUtil.getInstance().setInEcc(false);
                    connection.destroy();
                }
            }
        });
    }
    /// @}

    /// M: Reset the emergency call flag for ECC retry. @{
    // Used for destroy the old connection when ECC phone type is not default phone type.
    private void resetTreatAsEmergencyCall(Connection connection) {
        if (connection instanceof TelephonyConnection) {
            ((TelephonyConnection) connection).resetTreatAsEmergencyCall();
        }
    }
    /// @}

    private void log(String s) {
        Log.d(TAG, s);
    }
}

