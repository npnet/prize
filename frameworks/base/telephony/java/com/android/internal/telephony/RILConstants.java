/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.android.internal.telephony;
import android.os.SystemProperties;

/**
 * TODO: This should probably not be an interface see
 * http://www.javaworld.com/javaworld/javaqa/2001-06/01-qa-0608-constants.html and google with
 * http://www.google.com/search?q=interface+constants&ie=utf-8&oe=utf-8&aq=t&rls=com.ubuntu:en-US:unofficial&client=firefox-a
 *
 * Also they should all probably be static final.
 */

import android.os.SystemProperties;

/**
 * {@hide}
 */
public interface RILConstants {
    // From the top of ril.cpp
    int RIL_ERRNO_INVALID_RESPONSE = -1;

    // from RIL_Errno
    int SUCCESS = 0;
    int RADIO_NOT_AVAILABLE = 1;              /* If radio did not start or is resetting */
    int GENERIC_FAILURE = 2;
    int PASSWORD_INCORRECT = 3;               /* for PIN/PIN2 methods only! */
    int SIM_PIN2 = 4;                         /* Operation requires SIM PIN2 to be entered */
    int SIM_PUK2 = 5;                         /* Operation requires SIM PIN2 to be entered */
    int REQUEST_NOT_SUPPORTED = 6;
    int REQUEST_CANCELLED = 7;
    int OP_NOT_ALLOWED_DURING_VOICE_CALL = 8; /* data operation is not allowed during voice call in
                                                 class C */
    int OP_NOT_ALLOWED_BEFORE_REG_NW = 9;     /* request is not allowed before device registers to
                                                 network */
    int SMS_SEND_FAIL_RETRY = 10;             /* send sms fail and need retry */
    int SIM_ABSENT = 11;                      /* ICC card is absent */
    int SUBSCRIPTION_NOT_AVAILABLE = 12;      /* fail to find CDMA subscription from specified
                                                 location */
    int MODE_NOT_SUPPORTED = 13;              /* HW does not support preferred network type */
    int FDN_CHECK_FAILURE = 14;               /* send operation barred error when FDN is enabled */
    int ILLEGAL_SIM_OR_ME = 15;               /* network selection failure due
                                                 to wrong SIM/ME and no
                                                 retries needed */
    int MISSING_RESOURCE = 16;                /* no logical channel available */
    int NO_SUCH_ELEMENT = 17;                 /* application not found on SIM */
    int DIAL_MODIFIED_TO_USSD = 18;           /* DIAL request modified to USSD */
    int DIAL_MODIFIED_TO_SS = 19;             /* DIAL request modified to SS */
    int DIAL_MODIFIED_TO_DIAL = 20;           /* DIAL request modified to DIAL with different data*/
    int USSD_MODIFIED_TO_DIAL = 21;           /* USSD request modified to DIAL */
    int USSD_MODIFIED_TO_SS = 22;             /* USSD request modified to SS */
    int USSD_MODIFIED_TO_USSD = 23;           /* USSD request modified to different USSD request */
    int SS_MODIFIED_TO_DIAL = 24;             /* SS request modified to DIAL */
    int SS_MODIFIED_TO_USSD = 25;             /* SS request modified to USSD */

    int SUBSCRIPTION_NOT_SUPPORTED = 26;      /* Subscription not supported */
    int SS_MODIFIED_TO_SS = 27;               /* SS request modified to different SS request */
    int SIM_ALREADY_POWERED_OFF = 29;         /* SAP: 0x03, Error card aleready powered off */
    int SIM_ALREADY_POWERED_ON = 30;          /* SAP: 0x05, Error card already powered on */
    int SIM_DATA_NOT_AVAILABLE = 31;          /* SAP: 0x06, Error data not available */
    int SIM_SAP_CONNECT_FAILURE = 32;
    int SIM_SAP_MSG_SIZE_TOO_LARGE = 33;
    int SIM_SAP_MSG_SIZE_TOO_SMALL = 34;
    int SIM_SAP_CONNECT_OK_CALL_ONGOING = 35;
    int LCE_NOT_SUPPORTED = 36;               /* Link Capacity Estimation (LCE) not supported */
    int NO_MEMORY = 37;                       /* Not sufficient memory to process the request */
    int INTERNAL_ERR = 38;                    /* Hit unexpected vendor internal error scenario */
    int SYSTEM_ERR = 39;                      /* Hit platform or system error */
    int MODEM_ERR = 40;                       /* Hit unexpected modem error */
    int INVALID_STATE = 41;                   /* Unexpected request for the current state */
    int NO_RESOURCES = 42;                    /* Not sufficient resource to process the request */
    int SIM_ERR = 43;                         /* Received error from SIM card */
    int INVALID_ARGUMENTS = 44;               /* Received invalid arguments in request */
    int INVALID_SIM_STATE = 45;               /* Can not process the request in current SIM state */
    int INVALID_MODEM_STATE = 46;             /* Can not process the request in current Modem state */
    int INVALID_CALL_ID = 47;                 /* Received invalid call id in request */
    int NO_SMS_TO_ACK = 48;                   /* ACK received when there is no SMS to ack */
    int NETWORK_ERR = 49;                     /* Received error from network */
    int REQUEST_RATE_LIMITED = 50;            /* Operation denied due to overly-frequent requests */
    int SIM_BUSY = 51;                        /* SIM is busy */
    int SIM_FULL = 52;                        /* The target EF is full */
    int NETWORK_REJECT = 53;                  /* Request is rejected by network */
    int OPERATION_NOT_ALLOWED = 54;           /* Not allowed the request now */
    int EMPTY_RECORD = 55;                    /* The request record is empty */
    int INVALID_SMS_FORMAT = 56;              /* Invalid sms format */
    int ENCODING_ERR = 57;                    /* Message not encoded properly */
    int INVALID_SMSC_ADDRESS = 58;            /* SMSC address specified is invalid */
    int NO_SUCH_ENTRY = 59;                   /* No such entry present to perform the request */
    int NETWORK_NOT_READY = 60;               /* Network is not ready to perform the request */
    int NOT_PROVISIONED = 61;                 /* Device doesnot have this value provisioned */
    int NO_SUBSCRIPTION = 62;                 /* Device doesnot have subscription */
    int NO_NETWORK_FOUND = 63;                /* Network cannot be found */
    int DEVICE_IN_USE = 64;                   /* Operation cannot be performed because the device
                                                 is currently in use */
    int ABORTED = 65;                         /* Operation aborted */
    // Below is list of OEM specific error codes which can by used by OEMs in case they don't want to
    // reveal particular replacement for Generic failure
    int OEM_ERROR_1 = 501;
    int OEM_ERROR_2 = 502;
    int OEM_ERROR_3 = 503;
    int OEM_ERROR_4 = 504;
    int OEM_ERROR_5 = 505;
    int OEM_ERROR_6 = 506;
    int OEM_ERROR_7 = 507;
    int OEM_ERROR_8 = 508;
    int OEM_ERROR_9 = 509;
    int OEM_ERROR_10 = 510;
    int OEM_ERROR_11 = 511;
    int OEM_ERROR_12 = 512;
    int OEM_ERROR_13 = 513;
    int OEM_ERROR_14 = 514;
    int OEM_ERROR_15 = 515;
    int OEM_ERROR_16 = 516;
    int OEM_ERROR_17 = 517;
    int OEM_ERROR_18 = 518;
    int OEM_ERROR_19 = 519;
    int OEM_ERROR_20 = 520;
    int OEM_ERROR_21 = 521;
    int OEM_ERROR_22 = 522;
    int OEM_ERROR_23 = 523;
    int OEM_ERROR_24 = 524;
    int OEM_ERROR_25 = 525;

    int EXTERNAL_APP_CAUSE_BEGIN = 2000;
    int INVALID_PARAMETER = 2004;

    /// M: ALPS02147333 call control part
    int CC_CALL_HOLD_FAILED_CAUSED_BY_TERMINATED = 6000;
    /// M: Ims provisioning error
    int IMS_PROVISION_NO_DEFAULT_ERROR = 6604;

    /* NETWORK_MODE_* See ril.h RIL_REQUEST_SET_PREFERRED_NETWORK_TYPE */
    int NETWORK_MODE_WCDMA_PREF     = 0; /* GSM/WCDMA (WCDMA preferred) */
    int NETWORK_MODE_GSM_ONLY       = 1; /* GSM only */
    int NETWORK_MODE_WCDMA_ONLY     = 2; /* WCDMA only */
    int NETWORK_MODE_GSM_UMTS       = 3; /* GSM/WCDMA (auto mode, according to PRL)
                                            AVAILABLE Application Settings menu*/
    int NETWORK_MODE_CDMA           = 4; /* CDMA and EvDo (auto mode, according to PRL)
                                            AVAILABLE Application Settings menu*/
    int NETWORK_MODE_CDMA_NO_EVDO   = 5; /* CDMA only */
    int NETWORK_MODE_EVDO_NO_CDMA   = 6; /* EvDo only */
    int NETWORK_MODE_GLOBAL         = 7; /* GSM/WCDMA, CDMA, and EvDo (auto mode, according to PRL)
                                            AVAILABLE Application Settings menu*/
    int NETWORK_MODE_LTE_CDMA_EVDO  = 8; /* LTE, CDMA and EvDo */
    int NETWORK_MODE_LTE_GSM_WCDMA  = 9; /* LTE, GSM/WCDMA */
    int NETWORK_MODE_LTE_CDMA_EVDO_GSM_WCDMA = 10; /* LTE, CDMA, EvDo, GSM/WCDMA */
    int NETWORK_MODE_LTE_ONLY       = 11; /* LTE Only mode. */
    int NETWORK_MODE_LTE_WCDMA      = 12; /* LTE/WCDMA */
    int NETWORK_MODE_TDSCDMA_ONLY            = 13; /* TD-SCDMA only */
    int NETWORK_MODE_TDSCDMA_WCDMA           = 14; /* TD-SCDMA and WCDMA */
    int NETWORK_MODE_LTE_TDSCDMA             = 15; /* TD-SCDMA and LTE */
    int NETWORK_MODE_TDSCDMA_GSM             = 16; /* TD-SCDMA and GSM */
    int NETWORK_MODE_LTE_TDSCDMA_GSM         = 17; /* TD-SCDMA,GSM and LTE */
    int NETWORK_MODE_TDSCDMA_GSM_WCDMA       = 18; /* TD-SCDMA, GSM/WCDMA */
    int NETWORK_MODE_LTE_TDSCDMA_WCDMA       = 19; /* TD-SCDMA, WCDMA and LTE */
    int NETWORK_MODE_LTE_TDSCDMA_GSM_WCDMA   = 20; /* TD-SCDMA, GSM/WCDMA and LTE */
    int NETWORK_MODE_TDSCDMA_CDMA_EVDO_GSM_WCDMA  = 21; /*TD-SCDMA,EvDo,CDMA,GSM/WCDMA*/
    int NETWORK_MODE_LTE_TDSCDMA_CDMA_EVDO_GSM_WCDMA = 22; /* TD-SCDMA/LTE/GSM/WCDMA, CDMA, and EvDo */
    /// M: Add the new network type. @{
    int NETWORK_MODE_LTE_GSM        = 30; /*LTE/GSM */
    int NETWORK_MODE_LTE_TDD_ONLY   = 31; /* LTE TDD Only mode. */
    int NETWORK_MODE_CDMA_GSM                         = 32; /* CDMA,GSM(2G Global) */
    int NETWORK_MODE_CDMA_EVDO_GSM                    = 33; /* CDMA,EVDO,GSM */
    int NETWORK_MODE_LTE_CDMA_EVDO_GSM                = 34;/* LTE,CDMA,EVDO,GSM(4G Global, 4M) */
    /// @}
    /* AOSP
    int PREFERRED_NETWORK_MODE      = SystemProperties.getInt("ro.telephony.default_network",
            NETWORK_MODE_WCDMA_PREF);
    */
    int PREFERRED_NETWORK_MODE =
           SystemProperties.get("ro.mtk_protocol1_rat_config").equals("C/Lf") ?
           NETWORK_MODE_LTE_CDMA_EVDO :
           (SystemProperties.get("ro.boot.opt_c2k_support").equals("1") ?
           (SystemProperties.get("ro.boot.opt_lte_support").equals("1") ?
           NETWORK_MODE_LTE_CDMA_EVDO_GSM_WCDMA : NETWORK_MODE_GLOBAL) :
           (SystemProperties.get("ro.boot.opt_lte_support").equals("1") ?
           NETWORK_MODE_LTE_GSM_WCDMA : NETWORK_MODE_WCDMA_PREF));

    int BAND_MODE_UNSPECIFIED = 0;      //"unspecified" (selected by baseband automatically)
    int BAND_MODE_EURO = 1;             //"EURO band" (GSM-900 / DCS-1800 / WCDMA-IMT-2000)
    int BAND_MODE_USA = 2;              //"US band" (GSM-850 / PCS-1900 / WCDMA-850 / WCDMA-PCS-1900)
    int BAND_MODE_JPN = 3;              //"JPN band" (WCDMA-800 / WCDMA-IMT-2000)
    int BAND_MODE_AUS = 4;              //"AUS band" (GSM-900 / DCS-1800 / WCDMA-850 / WCDMA-IMT-2000)
    int BAND_MODE_AUS_2 = 5;            //"AUS band 2" (GSM-900 / DCS-1800 / WCDMA-850)
    int BAND_MODE_CELL_800 = 6;         //"Cellular" (800-MHz Band)
    int BAND_MODE_PCS = 7;              //"PCS" (1900-MHz Band)
    int BAND_MODE_JTACS = 8;            //"Band Class 3" (JTACS Band)
    int BAND_MODE_KOREA_PCS = 9;        //"Band Class 4" (Korean PCS Band)
    int BAND_MODE_5_450M = 10;          //"Band Class 5" (450-MHz Band)
    int BAND_MODE_IMT2000 = 11;         //"Band Class 6" (2-GMHz IMT2000 Band)
    int BAND_MODE_7_700M_2 = 12;        //"Band Class 7" (Upper 700-MHz Band)
    int BAND_MODE_8_1800M = 13;         //"Band Class 8" (1800-MHz Band)
    int BAND_MODE_9_900M = 14;          //"Band Class 9" (900-MHz Band)
    int BAND_MODE_10_800M_2 = 15;       //"Band Class 10" (Secondary 800-MHz Band)
    int BAND_MODE_EURO_PAMR_400M = 16;  //"Band Class 11" (400-MHz European PAMR Band)
    int BAND_MODE_AWS = 17;             //"Band Class 15" (AWS Band)
    int BAND_MODE_USA_2500M = 18;       //"Band Class 16" (US 2.5-GHz Band)

    int DIAL_STRING_TOO_LONG = 1001;
    int TEXT_STRING_TOO_LONG = 1002;
    int SIM_MEM_FULL = 1003;
    int ADDITIONAL_NUMBER_STRING_TOO_LONG = 1010;
    int ADN_LIST_NOT_EXIST = 1011;
    int ADDITIONAL_NUMBER_SAVE_FAILURE = 1012;
    int EMAIL_SIZE_LIMIT = 1005;
    int EMAIL_NAME_TOOLONG = 1006;
    int SNE_SIZE_LIMIT = 1007;
    int SNE_NAME_TOOLONG = 1008;

    int CDMA_CELL_BROADCAST_SMS_DISABLED = 1;
    int CDMA_CELL_BROADCAST_SMS_ENABLED  = 0;

    int NO_PHONE = 0;
    int GSM_PHONE = 1;
    int CDMA_PHONE = 2;
    int SIP_PHONE  = 3;
    int THIRD_PARTY_PHONE = 4;
    int IMS_PHONE = 5;
    int CDMA_LTE_PHONE = 6;

    int LTE_ON_CDMA_UNKNOWN = -1;
    int LTE_ON_CDMA_FALSE = 0;
    int LTE_ON_CDMA_TRUE = 1;

    int CDM_TTY_MODE_DISABLED = 0;
    int CDM_TTY_MODE_ENABLED = 1;

    int CDM_TTY_FULL_MODE = 1;
    int CDM_TTY_HCO_MODE = 2;
    int CDM_TTY_VCO_MODE = 3;

    /* Setup a packet data connection. See ril.h RIL_REQUEST_SETUP_DATA_CALL */
    int SETUP_DATA_TECH_CDMA      = 0;
    int SETUP_DATA_TECH_GSM       = 1;

    int SETUP_DATA_AUTH_NONE      = 0;
    int SETUP_DATA_AUTH_PAP       = 1;
    int SETUP_DATA_AUTH_CHAP      = 2;
    int SETUP_DATA_AUTH_PAP_CHAP  = 3;

    String SETUP_DATA_PROTOCOL_IP     = "IP";
    String SETUP_DATA_PROTOCOL_IPV6   = "IPV6";
    String SETUP_DATA_PROTOCOL_IPV4V6 = "IPV4V6";

    /* Deactivate data call reasons */
    int DEACTIVATE_REASON_NONE = 0;
    int DEACTIVATE_REASON_RADIO_OFF = 1;
    int DEACTIVATE_REASON_PDP_RESET = 2;

    /* M: Deactivate data call reasons for ipv6 RA failed feature */
    int DEACTIVATE_REASON_RA_BASE = 2000;
    int DEACTIVATE_REASON_NORMAL = DEACTIVATE_REASON_RA_BASE + 1;
    int DEACTIVATE_REASON_RA_INITIAL_FAIL = DEACTIVATE_REASON_RA_BASE + 2;
    int DEACTIVATE_REASON_NO_PCSCF = DEACTIVATE_REASON_RA_BASE + 3;
    int DEACTIVATE_REASON_RA_REFRESH_FAIL = DEACTIVATE_REASON_RA_BASE + 4;

    /* NV config radio reset types. */
    int NV_CONFIG_RELOAD_RESET = 1;
    int NV_CONFIG_ERASE_RESET = 2;
    int NV_CONFIG_FACTORY_RESET = 3;

    /* LCE service related constants. */
    int LCE_NOT_AVAILABLE = -1;
    int LCE_STOPPED = 0;
    int LCE_ACTIVE = 1;

    /* PHB Storage type, PHB_XDN*/
    int PHB_ADN = 0;
    int PHB_FDN = 1;
    int PHB_MSISDN = 2;
    int PHB_ECC = 3;

    /* Max PHB entryies to be read at once,
        Refer to RIL_MAX_PHB_ENTRY defined in the ril_sim.c */
    int PHB_MAX_ENTRY = 10;

/*
cat include/telephony/ril.h | \
   egrep '^#define' | \
   sed -re 's/^#define +([^ ]+)* +([^ ]+)/    int \1 = \2;/' \
   >>java/android/com.android.internal.telephony/gsm/RILConstants.java
*/

    /**
     * No restriction at all including voice/SMS/USSD/SS/AV64
     * and packet data.
     */
    int RIL_RESTRICTED_STATE_NONE = 0x00;
    /**
     * Block emergency call due to restriction.
     * But allow all normal voice/SMS/USSD/SS/AV64.
     */
    int RIL_RESTRICTED_STATE_CS_EMERGENCY = 0x01;
    /**
     * Block all normal voice/SMS/USSD/SS/AV64 due to restriction.
     * Only Emergency call allowed.
     */
    int RIL_RESTRICTED_STATE_CS_NORMAL = 0x02;
    /**
     * Block all voice/SMS/USSD/SS/AV64
     * including emergency call due to restriction.
     */
    int RIL_RESTRICTED_STATE_CS_ALL = 0x04;
    /**
     * Block packet data access due to restriction.
     */
    int RIL_RESTRICTED_STATE_PS_ALL = 0x10;

    /** Data profile for RIL_REQUEST_SETUP_DATA_CALL */
    public static final int DATA_PROFILE_DEFAULT   = 0;
    public static final int DATA_PROFILE_TETHERED  = 1;
    public static final int DATA_PROFILE_IMS       = 2;
    public static final int DATA_PROFILE_FOTA      = 3;
    public static final int DATA_PROFILE_CBS       = 4;
    public static final int DATA_PROFILE_OEM_BASE  = 1000;
    // M: VDF MMS over ePDG @{
    public static final int DATA_PROFILE_MMS = DATA_PROFILE_OEM_BASE + 1;
    public static final int DATA_PROFILE_SUPL = DATA_PROFILE_OEM_BASE + 2;
    public static final int DATA_PROFILE_HIPRI = DATA_PROFILE_OEM_BASE + 3;
    public static final int DATA_PROFILE_DM = DATA_PROFILE_OEM_BASE + 4;
    public static final int DATA_PROFILE_WAP = DATA_PROFILE_OEM_BASE + 5;
    public static final int DATA_PROFILE_NET = DATA_PROFILE_OEM_BASE + 6;
    public static final int DATA_PROFILE_CMMAIL = DATA_PROFILE_OEM_BASE + 7;
    public static final int DATA_PROFILE_RCSE = DATA_PROFILE_OEM_BASE + 8;
    public static final int DATA_PROFILE_EMERGENCY = DATA_PROFILE_OEM_BASE + 9;
    public static final int DATA_PROFILE_XCAP = DATA_PROFILE_OEM_BASE + 10;
    public static final int DATA_PROFILE_RCS = DATA_PROFILE_OEM_BASE + 11;
    /// @}
    public static final int DATA_PROFILE_INVALID   = 0xFFFFFFFF;

    // MTK-START, SIM power
    public static final int SIM_POWER_OFF   = 0;
    public static final int SIM_POWER_ON    = 1;
    public static final int SIM_POWER_RESET = 2;
    // MTK-END, SIM power

    int RIL_REQUEST_GET_SIM_STATUS = 1;
    int RIL_REQUEST_ENTER_SIM_PIN = 2;
    int RIL_REQUEST_ENTER_SIM_PUK = 3;
    int RIL_REQUEST_ENTER_SIM_PIN2 = 4;
    int RIL_REQUEST_ENTER_SIM_PUK2 = 5;
    int RIL_REQUEST_CHANGE_SIM_PIN = 6;
    int RIL_REQUEST_CHANGE_SIM_PIN2 = 7;
    int RIL_REQUEST_ENTER_NETWORK_DEPERSONALIZATION = 8;
    int RIL_REQUEST_GET_CURRENT_CALLS = 9;
    int RIL_REQUEST_DIAL = 10;
    int RIL_REQUEST_GET_IMSI = 11;
    int RIL_REQUEST_HANGUP = 12;
    int RIL_REQUEST_HANGUP_WAITING_OR_BACKGROUND = 13;
    int RIL_REQUEST_HANGUP_FOREGROUND_RESUME_BACKGROUND = 14;
    int RIL_REQUEST_SWITCH_WAITING_OR_HOLDING_AND_ACTIVE = 15;
    int RIL_REQUEST_CONFERENCE = 16;
    int RIL_REQUEST_UDUB = 17;
    int RIL_REQUEST_LAST_CALL_FAIL_CAUSE = 18;
    int RIL_REQUEST_SIGNAL_STRENGTH = 19;
    int RIL_REQUEST_VOICE_REGISTRATION_STATE = 20;
    int RIL_REQUEST_DATA_REGISTRATION_STATE = 21;
    int RIL_REQUEST_OPERATOR = 22;
    int RIL_REQUEST_RADIO_POWER = 23;
    int RIL_REQUEST_DTMF = 24;
    int RIL_REQUEST_SEND_SMS = 25;
    int RIL_REQUEST_SEND_SMS_EXPECT_MORE = 26;
    int RIL_REQUEST_SETUP_DATA_CALL = 27;
    int RIL_REQUEST_SIM_IO = 28;
    int RIL_REQUEST_SEND_USSD = 29;
    int RIL_REQUEST_CANCEL_USSD = 30;
    int RIL_REQUEST_GET_CLIR = 31;
    int RIL_REQUEST_SET_CLIR = 32;
    int RIL_REQUEST_QUERY_CALL_FORWARD_STATUS = 33;
    int RIL_REQUEST_SET_CALL_FORWARD = 34;
    int RIL_REQUEST_QUERY_CALL_WAITING = 35;
    int RIL_REQUEST_SET_CALL_WAITING = 36;
    int RIL_REQUEST_SMS_ACKNOWLEDGE = 37;
    int RIL_REQUEST_GET_IMEI = 38;
    int RIL_REQUEST_GET_IMEISV = 39;
    int RIL_REQUEST_ANSWER = 40;
    int RIL_REQUEST_DEACTIVATE_DATA_CALL = 41;
    int RIL_REQUEST_QUERY_FACILITY_LOCK = 42;
    int RIL_REQUEST_SET_FACILITY_LOCK = 43;
    int RIL_REQUEST_CHANGE_BARRING_PASSWORD = 44;
    int RIL_REQUEST_QUERY_NETWORK_SELECTION_MODE = 45;
    int RIL_REQUEST_SET_NETWORK_SELECTION_AUTOMATIC = 46;
    int RIL_REQUEST_SET_NETWORK_SELECTION_MANUAL = 47;
    int RIL_REQUEST_QUERY_AVAILABLE_NETWORKS = 48;
    int RIL_REQUEST_DTMF_START = 49;
    int RIL_REQUEST_DTMF_STOP = 50;
    int RIL_REQUEST_BASEBAND_VERSION = 51;
    int RIL_REQUEST_SEPARATE_CONNECTION = 52;
    int RIL_REQUEST_SET_MUTE = 53;
    int RIL_REQUEST_GET_MUTE = 54;
    int RIL_REQUEST_QUERY_CLIP = 55;
    int RIL_REQUEST_LAST_DATA_CALL_FAIL_CAUSE = 56;
    int RIL_REQUEST_DATA_CALL_LIST = 57;
    int RIL_REQUEST_RESET_RADIO = 58;
    int RIL_REQUEST_OEM_HOOK_RAW = 59;
    int RIL_REQUEST_OEM_HOOK_STRINGS = 60;
    int RIL_REQUEST_SCREEN_STATE = 61;
    int RIL_REQUEST_SET_SUPP_SVC_NOTIFICATION = 62;
    int RIL_REQUEST_WRITE_SMS_TO_SIM = 63;
    int RIL_REQUEST_DELETE_SMS_ON_SIM = 64;
    int RIL_REQUEST_SET_BAND_MODE = 65;
    int RIL_REQUEST_QUERY_AVAILABLE_BAND_MODE = 66;
    int RIL_REQUEST_STK_GET_PROFILE = 67;
    int RIL_REQUEST_STK_SET_PROFILE = 68;
    int RIL_REQUEST_STK_SEND_ENVELOPE_COMMAND = 69;
    int RIL_REQUEST_STK_SEND_TERMINAL_RESPONSE = 70;
    int RIL_REQUEST_STK_HANDLE_CALL_SETUP_REQUESTED_FROM_SIM = 71;
    int RIL_REQUEST_EXPLICIT_CALL_TRANSFER = 72;
    int RIL_REQUEST_SET_PREFERRED_NETWORK_TYPE = 73;
    int RIL_REQUEST_GET_PREFERRED_NETWORK_TYPE = 74;
    int RIL_REQUEST_GET_NEIGHBORING_CELL_IDS = 75;
    int RIL_REQUEST_SET_LOCATION_UPDATES = 76;
    int RIL_REQUEST_CDMA_SET_SUBSCRIPTION_SOURCE = 77;
    int RIL_REQUEST_CDMA_SET_ROAMING_PREFERENCE = 78;
    int RIL_REQUEST_CDMA_QUERY_ROAMING_PREFERENCE = 79;
    int RIL_REQUEST_SET_TTY_MODE = 80;
    int RIL_REQUEST_QUERY_TTY_MODE = 81;
    int RIL_REQUEST_CDMA_SET_PREFERRED_VOICE_PRIVACY_MODE = 82;
    int RIL_REQUEST_CDMA_QUERY_PREFERRED_VOICE_PRIVACY_MODE = 83;
    int RIL_REQUEST_CDMA_FLASH = 84;
    int RIL_REQUEST_CDMA_BURST_DTMF = 85;
    int RIL_REQUEST_CDMA_VALIDATE_AND_WRITE_AKEY = 86;
    int RIL_REQUEST_CDMA_SEND_SMS = 87;
    int RIL_REQUEST_CDMA_SMS_ACKNOWLEDGE = 88;
    int RIL_REQUEST_GSM_GET_BROADCAST_CONFIG = 89;
    int RIL_REQUEST_GSM_SET_BROADCAST_CONFIG = 90;
    int RIL_REQUEST_GSM_BROADCAST_ACTIVATION = 91;
    int RIL_REQUEST_CDMA_GET_BROADCAST_CONFIG = 92;
    int RIL_REQUEST_CDMA_SET_BROADCAST_CONFIG = 93;
    int RIL_REQUEST_CDMA_BROADCAST_ACTIVATION = 94;
    int RIL_REQUEST_CDMA_SUBSCRIPTION = 95;
    int RIL_REQUEST_CDMA_WRITE_SMS_TO_RUIM = 96;
    int RIL_REQUEST_CDMA_DELETE_SMS_ON_RUIM = 97;
    int RIL_REQUEST_DEVICE_IDENTITY = 98;
    int RIL_REQUEST_EXIT_EMERGENCY_CALLBACK_MODE = 99;
    int RIL_REQUEST_GET_SMSC_ADDRESS = 100;
    int RIL_REQUEST_SET_SMSC_ADDRESS = 101;
    int RIL_REQUEST_REPORT_SMS_MEMORY_STATUS = 102;
    int RIL_REQUEST_REPORT_STK_SERVICE_IS_RUNNING = 103;
    int RIL_REQUEST_CDMA_GET_SUBSCRIPTION_SOURCE = 104;
    int RIL_REQUEST_ISIM_AUTHENTICATION = 105;
    int RIL_REQUEST_ACKNOWLEDGE_INCOMING_GSM_SMS_WITH_PDU = 106;
    int RIL_REQUEST_STK_SEND_ENVELOPE_WITH_STATUS = 107;
    int RIL_REQUEST_VOICE_RADIO_TECH = 108;
    int RIL_REQUEST_GET_CELL_INFO_LIST = 109;
    int RIL_REQUEST_SET_UNSOL_CELL_INFO_LIST_RATE = 110;
    int RIL_REQUEST_SET_INITIAL_ATTACH_APN = 111;
    int RIL_REQUEST_IMS_REGISTRATION_STATE = 112;
    int RIL_REQUEST_IMS_SEND_SMS = 113;
    int RIL_REQUEST_SIM_TRANSMIT_APDU_BASIC = 114;
    int RIL_REQUEST_SIM_OPEN_CHANNEL = 115;
    int RIL_REQUEST_SIM_CLOSE_CHANNEL = 116;
    int RIL_REQUEST_SIM_TRANSMIT_APDU_CHANNEL = 117;
    int RIL_REQUEST_NV_READ_ITEM = 118;
    int RIL_REQUEST_NV_WRITE_ITEM = 119;
    int RIL_REQUEST_NV_WRITE_CDMA_PRL = 120;
    int RIL_REQUEST_NV_RESET_CONFIG = 121;
    int RIL_REQUEST_SET_UICC_SUBSCRIPTION = 122;
    int RIL_REQUEST_ALLOW_DATA = 123;
    int RIL_REQUEST_GET_HARDWARE_CONFIG = 124;
    int RIL_REQUEST_SIM_AUTHENTICATION = 125;
    int RIL_REQUEST_GET_DC_RT_INFO = 126;
    int RIL_REQUEST_SET_DC_RT_INFO_RATE = 127;
    int RIL_REQUEST_SET_DATA_PROFILE = 128;
    int RIL_REQUEST_SHUTDOWN = 129;
    int RIL_REQUEST_GET_RADIO_CAPABILITY = 130;
    int RIL_REQUEST_SET_RADIO_CAPABILITY = 131;
    int RIL_REQUEST_START_LCE = 132;
    int RIL_REQUEST_STOP_LCE = 133;
    int RIL_REQUEST_PULL_LCEDATA = 134;
    int RIL_REQUEST_GET_ACTIVITY_INFO = 135;
    int RIL_REQUEST_SET_ALLOWED_CARRIERS = 136;
    int RIL_REQUEST_GET_ALLOWED_CARRIERS = 137;

    int RIL_RESPONSE_ACKNOWLEDGEMENT = 800;

    int RIL_UNSOL_RESPONSE_BASE = 1000;
    int RIL_UNSOL_RESPONSE_RADIO_STATE_CHANGED = 1000;
    int RIL_UNSOL_RESPONSE_CALL_STATE_CHANGED = 1001;
    int RIL_UNSOL_RESPONSE_VOICE_NETWORK_STATE_CHANGED = 1002;
    int RIL_UNSOL_RESPONSE_NEW_SMS = 1003;
    int RIL_UNSOL_RESPONSE_NEW_SMS_STATUS_REPORT = 1004;
    int RIL_UNSOL_RESPONSE_NEW_SMS_ON_SIM = 1005;
    int RIL_UNSOL_ON_USSD = 1006;
    int RIL_UNSOL_ON_USSD_REQUEST = 1007;
    int RIL_UNSOL_NITZ_TIME_RECEIVED = 1008;
    int RIL_UNSOL_SIGNAL_STRENGTH = 1009;
    int RIL_UNSOL_DATA_CALL_LIST_CHANGED = 1010;
    int RIL_UNSOL_SUPP_SVC_NOTIFICATION = 1011;
    int RIL_UNSOL_STK_SESSION_END = 1012;
    int RIL_UNSOL_STK_PROACTIVE_COMMAND = 1013;
    int RIL_UNSOL_STK_EVENT_NOTIFY = 1014;
    int RIL_UNSOL_STK_CALL_SETUP = 1015;
    int RIL_UNSOL_SIM_SMS_STORAGE_FULL = 1016;
    int RIL_UNSOL_SIM_REFRESH = 1017;
    int RIL_UNSOL_CALL_RING = 1018;
    int RIL_UNSOL_RESPONSE_SIM_STATUS_CHANGED = 1019;
    int RIL_UNSOL_RESPONSE_CDMA_NEW_SMS = 1020;
    int RIL_UNSOL_RESPONSE_NEW_BROADCAST_SMS = 1021;
    int RIL_UNSOL_CDMA_RUIM_SMS_STORAGE_FULL = 1022;
    int RIL_UNSOL_RESTRICTED_STATE_CHANGED = 1023;
    int RIL_UNSOL_ENTER_EMERGENCY_CALLBACK_MODE = 1024;
    int RIL_UNSOL_CDMA_CALL_WAITING = 1025;
    int RIL_UNSOL_CDMA_OTA_PROVISION_STATUS = 1026;
    int RIL_UNSOL_CDMA_INFO_REC = 1027;
    int RIL_UNSOL_OEM_HOOK_RAW = 1028;
    int RIL_UNSOL_RINGBACK_TONE = 1029;
    int RIL_UNSOL_RESEND_INCALL_MUTE = 1030;
    int RIL_UNSOL_CDMA_SUBSCRIPTION_SOURCE_CHANGED = 1031;
    int RIL_UNSOl_CDMA_PRL_CHANGED = 1032;
    int RIL_UNSOL_EXIT_EMERGENCY_CALLBACK_MODE = 1033;
    int RIL_UNSOL_RIL_CONNECTED = 1034;
    int RIL_UNSOL_VOICE_RADIO_TECH_CHANGED = 1035;
    int RIL_UNSOL_CELL_INFO_LIST = 1036;
    int RIL_UNSOL_RESPONSE_IMS_NETWORK_STATE_CHANGED = 1037;
    int RIL_UNSOL_UICC_SUBSCRIPTION_STATUS_CHANGED = 1038;
    int RIL_UNSOL_SRVCC_STATE_NOTIFY = 1039;
    int RIL_UNSOL_HARDWARE_CONFIG_CHANGED = 1040;
    int RIL_UNSOL_DC_RT_INFO_CHANGED = 1041;
    int RIL_UNSOL_RADIO_CAPABILITY = 1042;
    int RIL_UNSOL_ON_SS = 1043;
    int RIL_UNSOL_STK_CC_ALPHA_NOTIFY = 1044;
    int RIL_UNSOL_LCEDATA_RECV = 1045;
    int RIL_UNSOL_PCO_DATA = 1046;

    /* Add MTK proprietary below */
    int RIL_REQUEST_VENDOR_BASE = 2000;
    int RIL_REQUEST_GET_COLP = (RIL_REQUEST_VENDOR_BASE + 0);
    int RIL_REQUEST_SET_COLP = (RIL_REQUEST_VENDOR_BASE + 1);
    int RIL_REQUEST_GET_COLR = (RIL_REQUEST_VENDOR_BASE + 2);
    int RIL_REQUEST_GET_CCM = (RIL_REQUEST_VENDOR_BASE + 3);
    int RIL_REQUEST_GET_ACM = (RIL_REQUEST_VENDOR_BASE + 4);
    int RIL_REQUEST_GET_ACMMAX = (RIL_REQUEST_VENDOR_BASE + 5);
    int RIL_REQUEST_GET_PPU_AND_CURRENCY = (RIL_REQUEST_VENDOR_BASE + 6);
    int RIL_REQUEST_SET_ACMMAX = (RIL_REQUEST_VENDOR_BASE + 7);
    int RIL_REQUEST_RESET_ACM = (RIL_REQUEST_VENDOR_BASE + 8);
    int RIL_REQUEST_SET_PPU_AND_CURRENCY = (RIL_REQUEST_VENDOR_BASE + 9);
    int RIL_REQUEST_MODEM_POWEROFF = (RIL_REQUEST_VENDOR_BASE + 10);
    int RIL_REQUEST_DUAL_SIM_MODE_SWITCH = (RIL_REQUEST_VENDOR_BASE + 11);
    int RIL_REQUEST_QUERY_PHB_STORAGE_INFO = (RIL_REQUEST_VENDOR_BASE + 12);
    int RIL_REQUEST_WRITE_PHB_ENTRY = (RIL_REQUEST_VENDOR_BASE + 13);
    int RIL_REQUEST_READ_PHB_ENTRY = (RIL_REQUEST_VENDOR_BASE + 14);
    //Add by mtk80372 for Barcode Number
    int RIL_REQUEST_MOBILEREVISION_AND_IMEI = (RIL_REQUEST_VENDOR_BASE + 15);
    int RIL_REQUEST_QUERY_SIM_NETWORK_LOCK = (RIL_REQUEST_VENDOR_BASE + 16);
    int RIL_REQUEST_SET_SIM_NETWORK_LOCK = (RIL_REQUEST_VENDOR_BASE + 17);
    int RIL_REQUEST_SET_NETWORK_SELECTION_MANUAL_WITH_ACT = (RIL_REQUEST_VENDOR_BASE + 18);
    int RIL_REQUEST_USIM_AUTHENTICATION = (RIL_REQUEST_VENDOR_BASE + 19);
    int RIL_REQUEST_MODEM_POWERON = (RIL_REQUEST_VENDOR_BASE + 20);
    int RIL_REQUEST_GET_SMS_SIM_MEM_STATUS = (RIL_REQUEST_VENDOR_BASE + 21);
    /* User controlled PLMN selector with Access Technology  begin */
    int RIL_REQUEST_GET_POL_CAPABILITY = (RIL_REQUEST_VENDOR_BASE + 22);
    int RIL_REQUEST_GET_POL_LIST = (RIL_REQUEST_VENDOR_BASE + 23);
    int RIL_REQUEST_SET_POL_ENTRY = (RIL_REQUEST_VENDOR_BASE + 24);
    /* User controlled PLMN selector with Access Technology  end */
    /* UPB start */
    int RIL_REQUEST_QUERY_UPB_CAPABILITY = (RIL_REQUEST_VENDOR_BASE + 25);
    int RIL_REQUEST_EDIT_UPB_ENTRY = (RIL_REQUEST_VENDOR_BASE + 26);
    int RIL_REQUEST_DELETE_UPB_ENTRY = (RIL_REQUEST_VENDOR_BASE + 27);
    int RIL_REQUEST_READ_UPB_GAS_LIST = (RIL_REQUEST_VENDOR_BASE + 28);
    int RIL_REQUEST_READ_UPB_GRP = (RIL_REQUEST_VENDOR_BASE + 29);
    int RIL_REQUEST_WRITE_UPB_GRP = (RIL_REQUEST_VENDOR_BASE + 30);
    /* UPB end */
    int RIL_REQUEST_SET_TRM = (RIL_REQUEST_VENDOR_BASE + 31);
    int RIL_REQUEST_GET_CALIBRATION_DATA = (RIL_REQUEST_VENDOR_BASE + 32);

     //For LGE APIs start
    int RIL_REQUEST_GET_PHB_STRING_LENGTH = (RIL_REQUEST_VENDOR_BASE + 33);
    int RIL_REQUEST_GET_PHB_MEM_STORAGE = (RIL_REQUEST_VENDOR_BASE + 34);
    int RIL_REQUEST_SET_PHB_MEM_STORAGE = (RIL_REQUEST_VENDOR_BASE + 35);
    int RIL_REQUEST_READ_PHB_ENTRY_EXT = (RIL_REQUEST_VENDOR_BASE + 36);
    int RIL_REQUEST_WRITE_PHB_ENTRY_EXT = (RIL_REQUEST_VENDOR_BASE + 37);

    // requests for read/write EFsmsp
    int RIL_REQUEST_GET_SMS_PARAMS = (RIL_REQUEST_VENDOR_BASE + 38);
    int RIL_REQUEST_SET_SMS_PARAMS = (RIL_REQUEST_VENDOR_BASE + 39);

    // NFC SEEK start
    int RIL_REQUEST_SIM_TRANSMIT_BASIC = (RIL_REQUEST_VENDOR_BASE + 40);
    int RIL_REQUEST_SIM_TRANSMIT_CHANNEL = (RIL_REQUEST_VENDOR_BASE + 41);
    int RIL_REQUEST_SIM_GET_ATR = (RIL_REQUEST_VENDOR_BASE + 42);
    // NFC SEEK end

    // MTK-START, SMS part, CB extension
    int RIL_REQUEST_SET_CB_CHANNEL_CONFIG_INFO = (RIL_REQUEST_VENDOR_BASE + 43);
    int RIL_REQUEST_SET_CB_LANGUAGE_CONFIG_INFO = (RIL_REQUEST_VENDOR_BASE + 44);
    int RIL_REQUEST_GET_CB_CONFIG_INFO = (RIL_REQUEST_VENDOR_BASE + 45);
    int RIL_REQUEST_SET_ALL_CB_LANGUAGE_ON = (RIL_REQUEST_VENDOR_BASE + 46);
    // MTK-END, SMS part, CB extension

    int RIL_REQUEST_SET_ETWS = (RIL_REQUEST_VENDOR_BASE + 47);

    // [New R8 modem FD]
    int RIL_REQUEST_SET_FD_MODE = (RIL_REQUEST_VENDOR_BASE + 48);

    /// M: [C2K][IR] Support SVLTE IR feature. @{
    int RIL_REQUEST_SET_REG_SUSPEND_ENABLED  = (RIL_REQUEST_VENDOR_BASE + 49);
    int RIL_REQUEST_RESUME_REGISTRATION  = (RIL_REQUEST_VENDOR_BASE + 50);
    /// M: [C2K][IR] Support SVLTE IR feature. @}
    int RIL_REQUEST_STORE_MODEM_TYPE = (RIL_REQUEST_VENDOR_BASE + 51);

    //MTK-START [mtk80776] WiFi Calling
    int RIL_REQUEST_UICC_SELECT_APPLICATION = (RIL_REQUEST_VENDOR_BASE + 52);
    int RIL_REQUEST_UICC_DEACTIVATE_APPLICATION = (RIL_REQUEST_VENDOR_BASE + 53);
    int RIL_REQUEST_UICC_APPLICATION_IO = (RIL_REQUEST_VENDOR_BASE + 54);
    int RIL_REQUEST_UICC_AKA_AUTHENTICATE = (RIL_REQUEST_VENDOR_BASE + 55);
    int RIL_REQUEST_UICC_GBA_AUTHENTICATE_BOOTSTRAP = (RIL_REQUEST_VENDOR_BASE + 56);
    int RIL_REQUEST_UICC_GBA_AUTHENTICATE_NAF = (RIL_REQUEST_VENDOR_BASE + 57);
    //MTK-END [mtk80776] WiFi Calling
    int RIL_REQUEST_STK_EVDL_CALL_BY_AP = (RIL_REQUEST_VENDOR_BASE + 58);

    // Femtocell (CSG)
    int RIL_REQUEST_GET_FEMTOCELL_LIST  = (RIL_REQUEST_VENDOR_BASE + 59);
    int RIL_REQUEST_ABORT_FEMTOCELL_LIST = (RIL_REQUEST_VENDOR_BASE + 60);
    int RIL_REQUEST_SELECT_FEMTOCELL = (RIL_REQUEST_VENDOR_BASE + 61);

    // For PLMN List abort
    int RIL_REQUEST_ABORT_QUERY_AVAILABLE_NETWORKS = (RIL_REQUEST_VENDOR_BASE + 62);

    /// M: CC: HangupAll for FTA 31.4.4.2
    int RIL_REQUEST_HANGUP_ALL = (RIL_REQUEST_VENDOR_BASE + 63);
    /// M: CC: Hangup special handling
    int RIL_REQUEST_FORCE_RELEASE_CALL = (RIL_REQUEST_VENDOR_BASE + 64);
    /// M: CC: Proprietary incoming call handling
    int RIL_REQUEST_SET_CALL_INDICATION = (RIL_REQUEST_VENDOR_BASE + 65);
    /// M: CC: Proprietary ECC handling @{
    int RIL_REQUEST_EMERGENCY_DIAL = (RIL_REQUEST_VENDOR_BASE + 66);
    int RIL_REQUEST_SET_ECC_SERVICE_CATEGORY = (RIL_REQUEST_VENDOR_BASE + 67);
    int RIL_REQUEST_SET_ECC_LIST = (RIL_REQUEST_VENDOR_BASE + 68);
    /// @}

    //New SIM Authentication
    int RIL_REQUEST_GENERAL_SIM_AUTH = (RIL_REQUEST_VENDOR_BASE + 69);
    //ISIM
    int RIL_REQUEST_OPEN_ICC_APPLICATION = (RIL_REQUEST_VENDOR_BASE + 70);
    int RIL_REQUEST_GET_ICC_APPLICATION_STATUS = (RIL_REQUEST_VENDOR_BASE + 71);
    //SIM_IO_EX
    int RIL_REQUEST_SIM_IO_EX = (RIL_REQUEST_VENDOR_BASE + 72);

    // IMS
    int RIL_REQUEST_SET_IMS_ENABLE = (RIL_REQUEST_VENDOR_BASE + 73);
    int RIL_REQUEST_QUERY_AVAILABLE_NETWORKS_WITH_ACT = (RIL_REQUEST_VENDOR_BASE + 74);

    /* M: SS part */
    ///M: For query CNAP
    int RIL_REQUEST_SEND_CNAP = (RIL_REQUEST_VENDOR_BASE + 75);
    int RIL_REQUEST_SET_CLIP = (RIL_REQUEST_VENDOR_BASE + 76);
    /* M: SS part end */


    // MTK-START, SMS part, CB extension
    int RIL_REQUEST_REMOVE_CB_MESSAGE = (RIL_REQUEST_VENDOR_BASE + 83);
    // MTK-END, SMS part, CB extension

    // NAS configuration for voice call
    // 0: voice centric
    // 1: data centric
    int RIL_REQUEST_SET_DATA_CENTRIC = (RIL_REQUEST_VENDOR_BASE + 84);

    /// M: IMS feature. @{
    int RIL_REQUEST_ADD_IMS_CONFERENCE_CALL_MEMBER = (RIL_REQUEST_VENDOR_BASE + 85);
    int RIL_REQUEST_REMOVE_IMS_CONFERENCE_CALL_MEMBER = (RIL_REQUEST_VENDOR_BASE + 86);
    int RIL_REQUEST_DIAL_WITH_SIP_URI = (RIL_REQUEST_VENDOR_BASE + 87);
    int RIL_REQUEST_RESUME_CALL = (RIL_REQUEST_VENDOR_BASE + 88);
    /// @}

    /// M: CC: GSA HD Voice for 2/3G network support
    int RIL_REQUEST_SET_SPEECH_CODEC_INFO = (RIL_REQUEST_VENDOR_BASE + 89);
    /// M: CC33 LTE
    int RIL_REQUEST_SET_DATA_ON_TO_MD = (RIL_REQUEST_VENDOR_BASE + 90);
    int RIL_REQUEST_SET_REMOVE_RESTRICT_EUTRAN_MODE = (RIL_REQUEST_VENDOR_BASE + 91);

    /// M: CC010: Add RIL interface @{
    int RIL_REQUEST_SET_IMS_CALL_STATUS = (RIL_REQUEST_VENDOR_BASE + 92);
    /// @}

    /// M: CC: For 3G VT only @{
    int RIL_REQUEST_VT_DIAL = (RIL_REQUEST_VENDOR_BASE + 93);
    int RIL_REQUEST_VOICE_ACCEPT = (RIL_REQUEST_VENDOR_BASE + 94);
    int RIL_REQUEST_REPLACE_VT_CALL = (RIL_REQUEST_VENDOR_BASE + 95);
    /// @}

    //C2K SVLTE remote SIM access
    int RIL_REQUEST_CONFIG_MODEM_STATUS = (RIL_REQUEST_VENDOR_BASE + 96);

    // M: [C2K] MD IRAT RIL requests.
    int RIL_REQUEST_SET_ACTIVE_PS_SLOT = RIL_REQUEST_VENDOR_BASE + 97;
    int RIL_REQUEST_CONFIRM_INTER_3GPP_IRAT_CHANGE = RIL_REQUEST_VENDOR_BASE + 98;

    /// M: [C2K][SVLTE] Set the SVLTE RAT mode. @{
    int RIL_REQUEST_SET_SVLTE_RAT_MODE = (RIL_REQUEST_VENDOR_BASE + 99);
    /// M: [C2K][SVLTE] Set the SVLTE RAT mode. @}

    /// M: IMS VoLTe conference dial feature. @{
    int RIL_REQUEST_CONFERENCE_DIAL = (RIL_REQUEST_VENDOR_BASE + 100);
    /// @}

    /// M: CC072: Add Customer proprietary-IMS RIL interface. @{
    /* To transfer IMS call context to modem */
    int RIL_REQUEST_SET_SRVCC_CALL_CONTEXT_TRANSFER = (RIL_REQUEST_VENDOR_BASE + 101);
    /* To update IMS registration status to modem */
    int RIL_REQUEST_UPDATE_IMS_REGISTRATION_STATUS = (RIL_REQUEST_VENDOR_BASE + 102);
    /// @}

    int RIL_REQUEST_RELOAD_MODEM_TYPE = (RIL_REQUEST_VENDOR_BASE + 103);

    /// M: IMS feature. @{
    int RIL_REQUEST_HOLD_CALL = (RIL_REQUEST_VENDOR_BASE + 104);
    /// @}

    int RIL_REQUEST_SWITCH_ANTENNA = (RIL_REQUEST_VENDOR_BASE + 105);

    int RIL_REQUEST_AT_COMMAND_WITH_PROXY = (RIL_REQUEST_VENDOR_BASE + 106);
    int RIL_REQUEST_SWITCH_CARD_TYPE = (RIL_REQUEST_VENDOR_BASE + 107);

    int RIL_REQUEST_ENABLE_MD3_SLEEP = (RIL_REQUEST_VENDOR_BASE + 108);

    /// M: IMS ViLTe feature. @{
    int RIL_REQUEST_VIDEO_CALL_ACCEPT = (RIL_REQUEST_VENDOR_BASE + 109);
    /// @}

    // M: [LTE][Low Power][UL traffic shaping] Start
    int RIL_REQUEST_SET_LTE_ACCESS_STRATUM_REPORT = (RIL_REQUEST_VENDOR_BASE + 110);
    int RIL_REQUEST_SET_LTE_UPLINK_DATA_TRANSFER = (RIL_REQUEST_VENDOR_BASE + 111);
    // M: [LTE][Low Power][UL traffic shaping] End

    /// M: USSI RIL request @{
    int RIL_REQUEST_SEND_USSI = (RIL_REQUEST_VENDOR_BASE + 113);
    int RIL_REQUEST_CANCEL_USSI = (RIL_REQUEST_VENDOR_BASE + 114);
    /// @}

    /* M: Add C2K proprietary start */
    int RIL_REQUEST_QUERY_UIM_INSERTED = (RIL_REQUEST_VENDOR_BASE + 115);
    int RIL_REQUEST_SWITCH_HPF = (RIL_REQUEST_VENDOR_BASE + 116);
    int RIL_REQUEST_AGPS_TCP_CONNIND = (RIL_REQUEST_VENDOR_BASE + 117);
    /// M: [C2K][IR] Support SVLTE IR feature. @{
    int RIL_REQUEST_RESUME_REGISTRATION_CDMA = (RIL_REQUEST_VENDOR_BASE + 118);
    int RIL_REQUEST_SET_REG_SUSPEND_ENABLED_CDMA = (RIL_REQUEST_VENDOR_BASE + 119);
    /// M: [C2K][IR] Support SVLTE IR feature. @}
    int RIL_REQUEST_SET_ETS_DEV = (RIL_REQUEST_VENDOR_BASE + 120);
    int RIL_REQUEST_WRITE_MDN = (RIL_REQUEST_VENDOR_BASE + 121);

    // [C2K][SVLTE] C2K SVLTE CDMA eHPRD control
    int RIL_REQUEST_CONFIG_EVDO_MODE = (RIL_REQUEST_VENDOR_BASE + 122);
    int RIL_REQUEST_AT_COMMAND_WITH_PROXY_CDMA = (RIL_REQUEST_VENDOR_BASE + 123);

    /// M: [EPDG] WiFi disconnect indication @{
    int RIL_REQUEST_WIFI_DISCONNECT_IND = (RIL_REQUEST_VENDOR_BASE + 124);
    /// @}

    /// M: EPDG feature. Update PS state from MAL @{
    int RIL_REQUEST_MAL_PS_RGEGISTRATION_STATE = (RIL_REQUEST_VENDOR_BASE + 125);
    /// @}
    int RIL_REQUEST_OEM_HOOK_RAW_WITH_PROXY = (RIL_REQUEST_VENDOR_BASE + 126);

    /// M:set Ims capability to MD @{
    int RIL_REQUEST_SET_VOLTE_ENABLE = (RIL_REQUEST_VENDOR_BASE + 127);
    int RIL_REQUEST_SET_WFC_ENABLE = (RIL_REQUEST_VENDOR_BASE + 128);
    int RIL_REQUEST_SET_IMS_VOICE_ENABLE = (RIL_REQUEST_VENDOR_BASE + 129);
    int RIL_REQUEST_SET_IMS_VIDEO_ENABLE = (RIL_REQUEST_VENDOR_BASE + 130);
    /// @}

    /// M: IMS ViLTe feature. @{
    int RIL_REQUEST_VT_DIAL_WITH_SIP_URI = (RIL_REQUEST_VENDOR_BASE + 131);
    /// @}

    /// M: IMS Deregistration request. @{
    int RIL_REQUEST_IMS_DEREG_NOTIFICATION = (RIL_REQUEST_VENDOR_BASE + 132);
    /// @}

    // MTK-START, SIM power
    int RIL_REQUEST_SET_SIM_POWER = (RIL_REQUEST_VENDOR_BASE + 133);
    // MTK-END, SIM power

    // VzW
    int RIL_REQUEST_SYNC_APN_TABLE = (RIL_REQUEST_VENDOR_BASE + 134);

    /* M: eMBMS feature */
    int RIL_REQUEST_EMBMS_ENABLE = (RIL_REQUEST_VENDOR_BASE + 135);
    int RIL_REQUEST_EMBMS_DISABLE = (RIL_REQUEST_VENDOR_BASE + 136);
    int RIL_REQUEST_EMBMS_START_SESSION = (RIL_REQUEST_VENDOR_BASE + 137);
    int RIL_REQUEST_EMBMS_STOP_SESSION = (RIL_REQUEST_VENDOR_BASE + 138);
    int RIL_REQUEST_EMBMS_SWITCH_SESSION = (RIL_REQUEST_VENDOR_BASE + 139);
    int RIL_REQUEST_EMBMS_GET_TIME = (RIL_REQUEST_VENDOR_BASE + 140);
    int RIL_REQUEST_EMBMS_GET_COVERAGE_STATE = (RIL_REQUEST_VENDOR_BASE + 141);

    /// M: ECC change feature for single SIM project. @{
    int RIL_REQUEST_SWITCH_MODE_FOR_ECC = (RIL_REQUEST_VENDOR_BASE + 142);
    /// @}
    int RIL_REQUEST_QUERY_UPB_AVAILABLE = (RIL_REQUEST_VENDOR_BASE + 143);
    int RIL_REQUEST_READ_EMAIL_ENTRY = (RIL_REQUEST_VENDOR_BASE + 144);
    int RIL_REQUEST_READ_SNE_ENTRY = (RIL_REQUEST_VENDOR_BASE + 145);
    int RIL_REQUEST_READ_ANR_ENTRY = (RIL_REQUEST_VENDOR_BASE + 146);
    int RIL_REQUEST_READ_UPB_AAS_LIST = (RIL_REQUEST_VENDOR_BASE + 147);
    /// M: [C2K][SVLTE] Set the STK UTK mode. @{
    int RIL_REQUEST_SET_STK_UTK_MODE = (RIL_REQUEST_VENDOR_BASE + 148);
    /// M: [C2K][SVLTE] Set the STK UTK mode. @}

    /// M: IMS Provisioning. @{
    int RIL_REQUEST_GET_PROVISION_VALUE = (RIL_REQUEST_VENDOR_BASE + 149);
    int RIL_REQUEST_SET_PROVISION_VALUE = (RIL_REQUEST_VENDOR_BASE + 150);
    /// @}
    /// M: For attach when needed mode.
    int RIL_REQUEST_SET_PS_REGISTRATION = (RIL_REQUEST_VENDOR_BASE + 151);

    // VzW
    int RIL_REQUEST_SYNC_DATA_SETTINGS_TO_MD = (RIL_REQUEST_VENDOR_BASE + 152);

    /// M: APC. @{
    int RIL_REQUEST_SET_PSEUDO_BS_ENABLE = (RIL_REQUEST_VENDOR_BASE + 153);
    int RIL_REQUEST_GET_PSEUDO_BS_RECORDS = (RIL_REQUEST_VENDOR_BASE + 154);
    /// @}

    // M: VDF MMS over ePDG @{
    int RIL_REQUEST_SYNC_APN_TABLE_TO_RDS = (RIL_REQUEST_VENDOR_BASE + 155);
    /// @}

    /// M: IMS blind/assured ECT @{
    int RIL_REQUEST_IMS_ECT = (RIL_REQUEST_VENDOR_BASE + 156);
    /// @}

    /* Femtocell (CSG) start */
    int RIL_REQUEST_QUERY_FEMTOCELL_SYSTEM_SELECTION_MODE = (RIL_REQUEST_VENDOR_BASE + 157);
    int RIL_REQUEST_SET_FEMTOCELL_SYSTEM_SELECTION_MODE = (RIL_REQUEST_VENDOR_BASE + 158);
    /* Femtocell (CSG) end */
    // MTK_TC1_FEATURE for Antenna Testing start
    int RIL_REQUEST_VSS_ANTENNA_CONF = (RIL_REQUEST_VENDOR_BASE + 159);
    int RIL_REQUEST_VSS_ANTENNA_INFO = (RIL_REQUEST_VENDOR_BASE + 160);
    // MTK_TC1_FEATURE for Antenna Testing end

    /// M: CC: Vzw/CTVolte ECC @{
    int RIL_REQUEST_CURRENT_STATUS = (RIL_REQUEST_VENDOR_BASE + 161);
    /// @}
    int RIL_REQUEST_GET_SMS_RUIM_MEM_STATUS = (RIL_REQUEST_VENDOR_BASE + 162);

    // To set language configuration for GSM cell broadcast
    int RIL_REQUEST_GSM_SET_BROADCAST_LANGUAGE = (RIL_REQUEST_VENDOR_BASE + 163);
    // M: To get language configuration for GSM cell broadcast
    int RIL_REQUEST_GSM_GET_BROADCAST_LANGUAGE = (RIL_REQUEST_VENDOR_BASE + 164);

    // M:set Ims capability to MD, ViLTE and ViWifi @{
    int RIL_REQUEST_SET_VILTE_ENABLE = (RIL_REQUEST_VENDOR_BASE + 165);
    int RIL_REQUEST_SET_VIWIFI_ENABLE = (RIL_REQUEST_VENDOR_BASE + 166);
    /// @}

    // M: To response IMS bearer operation status to MD
    int RIL_REQUEST_IMS_BEARER_ACTIVATION_DONE = (RIL_REQUEST_VENDOR_BASE + 167);
    int RIL_REQUEST_IMS_BEARER_DEACTIVATION_DONE = (RIL_REQUEST_VENDOR_BASE + 168);
    /// @}

    /// M:set Ims capabilit to MD, EIMSCFG in 93  @{
    int RIL_REQUEST_SET_IMSCFG = (RIL_REQUEST_VENDOR_BASE + 169);
    /// @}

    int RIL_REQUEST_GSM_SET_BROADCAST_CONFIG_EX = (RIL_REQUEST_VENDOR_BASE + 170);
    int RIL_REQUEST_GSM_GET_BROADCAST_CONFIG_EX = (RIL_REQUEST_VENDOR_BASE + 171);

    /// M: send wfc profile info to MD  @{
    int RIL_REQUEST_SET_WFC_PROFILE = (RIL_REQUEST_VENDOR_BASE + 172);
    /// @}
    // MTK-START Carrier Restrictions
    int RIL_REQUEST_SET_CARRIER_RESTRICTION_STATE = (RIL_REQUEST_VENDOR_BASE + 173);
    int RIL_REQUEST_GET_CARRIER_RESTRICTION_STATE = (RIL_REQUEST_VENDOR_BASE + 174);
    // MTK-END Carrier Restrictions

    /// M: IMS Base, set IMS registration report @{
    int RIL_REQUEST_SET_IMS_REGISTRATION_REPORT = (RIL_REQUEST_VENDOR_BASE + 175);
    ///// @}
    int RIL_REQUEST_SET_SERVICE_STATE = (RIL_REQUEST_VENDOR_BASE + 176);

    /// M: Mobile Wifi interactive @{
    int RIL_REQUEST_SET_WIFI_ENABLED = (RIL_REQUEST_VENDOR_BASE + 177);
    int RIL_REQUEST_SET_WIFI_ASSOCIATED = (RIL_REQUEST_VENDOR_BASE + 178);
    int RIL_REQUEST_SET_WIFI_SIGNAL_LEVEL = (RIL_REQUEST_VENDOR_BASE + 179);
    int RIL_REQUEST_SET_WIFI_IP_ADDRESS = (RIL_REQUEST_VENDOR_BASE + 180);
    /// @}

    /// M: WFC @{
    int RIL_REQUEST_SET_GEO_LOCATION = (RIL_REQUEST_VENDOR_BASE + 181);
    int RIL_REQUEST_SET_EMERGENCY_ACCESS_ID = (RIL_REQUEST_VENDOR_BASE + 182);
    /// @}
    // M: Data Retry, reset the data retry count in modem
    int RIL_REQUEST_RESET_MD_DATA_RETRY_COUNT = (RIL_REQUEST_VENDOR_BASE + 183);

    /// M: CC: Vzw/CTVolte ECC @{
    int RIL_REQUEST_ECC_PREFERRED_RAT = (RIL_REQUEST_VENDOR_BASE + 184);
    /// @}

    // IMS Call pull
    int RIL_REQUEST_PULL_CALL = (RIL_REQUEST_VENDOR_BASE + 185);

   /// M: IMS RTP Report @{
   int RIL_REQUEST_SET_IMS_RTP_REPORT = (RIL_REQUEST_VENDOR_BASE + 186);
    /// @}
    int RIL_REQUEST_GET_GSM_SMS_BROADCAST_ACTIVATION  = (RIL_REQUEST_VENDOR_BASE + 187);

    /// M: IMS SS @{
    int RIL_REQUEST_SET_COLR = (RIL_REQUEST_VENDOR_BASE + 188);
    int RIL_REQUEST_QUERY_CALL_FORWARD_IN_TIME_SLOT = (RIL_REQUEST_VENDOR_BASE + 189);
    int RIL_REQUEST_SET_CALL_FORWARD_IN_TIME_SLOT = (RIL_REQUEST_VENDOR_BASE + 190);
    int RIL_REQUEST_RUN_GBA = (RIL_REQUEST_VENDOR_BASE + 191);
    /// @}

    // IMS data request from MAL
    int RIL_REQUEST_SETUP_DATA_CALL_ALT = (RIL_REQUEST_VENDOR_BASE + 192);
    int RIL_REQUEST_LAST_DATA_CALL_FAIL_CAUSE_ALT = (RIL_REQUEST_VENDOR_BASE + 193);
    int RIL_REQUEST_PCSCF_DISCOVERY_PCO_ALT  = (RIL_REQUEST_VENDOR_BASE + 194);

    int RIL_REQUEST_SET_MD_IMSCFG = (RIL_REQUEST_VENDOR_BASE + 195);

    /// M: Set IMS EVS @{
    int RIL_REQUEST_SET_IMS_EVS_ENABLE = (RIL_REQUEST_VENDOR_BASE + 196);
    /// @}

    int RIL_UNSOL_VENDOR_BASE = 3000;
    int RIL_UNSOL_NEIGHBORING_CELL_INFO = (RIL_UNSOL_VENDOR_BASE + 0);
    int RIL_UNSOL_NETWORK_INFO = (RIL_UNSOL_VENDOR_BASE + 1);
    int RIL_UNSOL_PHB_READY_NOTIFICATION = (RIL_UNSOL_VENDOR_BASE + 2);
    int RIL_UNSOL_RADIO_TEMPORARILY_UNAVAILABLE = (RIL_UNSOL_VENDOR_BASE + 3);
    int RIL_UNSOL_ME_SMS_STORAGE_FULL = (RIL_UNSOL_VENDOR_BASE + 4);
    int RIL_UNSOL_SMS_READY_NOTIFICATION = (RIL_UNSOL_VENDOR_BASE + 5);
    int RIL_UNSOL_SIM_MISSING = (RIL_UNSOL_VENDOR_BASE + 6);
    //MTK-START [mtk04070][120208][ALPS00233196] ATCI for unsolicited response
    int RIL_UNSOL_ATCI_RESPONSE = (RIL_UNSOL_VENDOR_BASE + 7);
    //MTK-END [mtk04070][120208][ALPS00233196] ATCI for unsolicited response
    int RIL_UNSOL_SIM_RECOVERY = (RIL_UNSOL_VENDOR_BASE + 8);
    int RIL_UNSOL_VIRTUAL_SIM_ON = (RIL_UNSOL_VENDOR_BASE + 9);
    int RIL_UNSOL_VIRTUAL_SIM_OFF = (RIL_UNSOL_VENDOR_BASE + 10);
    int RIL_UNSOL_INVALID_SIM = (RIL_UNSOL_VENDOR_BASE + 11);
    int RIL_UNSOL_RESPONSE_PS_NETWORK_STATE_CHANGED = (RIL_UNSOL_VENDOR_BASE + 12);
    int RIL_UNSOL_RESPONSE_ACMT = (RIL_UNSOL_VENDOR_BASE + 13);
    int RIL_UNSOL_EF_CSP_PLMN_MODE_BIT = (RIL_UNSOL_VENDOR_BASE + 14);
    int RIL_UNSOL_IMEI_LOCK = (RIL_UNSOL_VENDOR_BASE + 15);
    int RIL_UNSOL_RESPONSE_MMRR_STATUS_CHANGED = (RIL_UNSOL_VENDOR_BASE + 16);
    int RIL_UNSOL_SIM_PLUG_OUT = (RIL_UNSOL_VENDOR_BASE + 17);
    int RIL_UNSOL_SIM_PLUG_IN = (RIL_UNSOL_VENDOR_BASE + 18);
    int RIL_UNSOL_RESPONSE_ETWS_NOTIFICATION = (RIL_UNSOL_VENDOR_BASE + 19);
    int RIL_UNSOL_RESPONSE_PLMN_CHANGED = (RIL_UNSOL_VENDOR_BASE + 20);
    int RIL_UNSOL_RESPONSE_REGISTRATION_SUSPENDED = (RIL_UNSOL_VENDOR_BASE + 21);
    int RIL_UNSOL_STK_EVDL_CALL = (RIL_UNSOL_VENDOR_BASE + 22);
    int RIL_UNSOL_FEMTOCELL_INFO = (RIL_UNSOL_VENDOR_BASE + 23);
    int RIL_UNSOL_STK_SETUP_MENU_RESET = (RIL_UNSOL_VENDOR_BASE + 24);
    int RIL_UNSOL_APPLICATION_SESSION_ID_CHANGED = (RIL_UNSOL_VENDOR_BASE + 25);
    /// M: For updating call ids for conference call after SRVCC is done.
    int RIL_UNSOL_ECONF_SRVCC_INDICATION = (RIL_UNSOL_VENDOR_BASE + 26);
    // IMS
    int RIL_UNSOL_IMS_ENABLE_DONE = (RIL_UNSOL_VENDOR_BASE + 27);
    int RIL_UNSOL_IMS_DISABLE_DONE = (RIL_UNSOL_VENDOR_BASE + 28);
    int RIL_UNSOL_IMS_REGISTRATION_INFO = (RIL_UNSOL_VENDOR_BASE + 29);

    //[VoLTE]Conf. call merged/added result
    int RIL_UNSOL_ECONF_RESULT_INDICATION = (RIL_UNSOL_VENDOR_BASE + 33);

    //Remote SIM ME lock related APIs [Start]
    int RIL_UNSOL_MELOCK_NOTIFICATION = (RIL_UNSOL_VENDOR_BASE + 34);
    //Remote SIM ME lock related APIs [END]

    /// M: SS: CFU for bootup
    int RIL_UNSOL_CALL_FORWARDING = (RIL_UNSOL_VENDOR_BASE + 35);
    /// M: CC: Proprietary CRSS handling
    int RIL_UNSOL_CRSS_NOTIFICATION = (RIL_UNSOL_VENDOR_BASE + 36);
    /// M: CC: Proprietary incoming call handling
    int RIL_UNSOL_INCOMING_CALL_INDICATION = (RIL_UNSOL_VENDOR_BASE + 37);
    /// M: CC: GSM 02.07 B.1.26 Ciphering Indicator support
    int RIL_UNSOL_CIPHER_INDICATION = (RIL_UNSOL_VENDOR_BASE + 38);

    int RIL_UNSOL_SIM_COMMON_SLOT_NO_CHANGED = (RIL_UNSOL_VENDOR_BASE + 39);
    //Combine attach
    int RIL_UNSOL_DATA_ALLOWED = (RIL_UNSOL_VENDOR_BASE + 40);
    int RIL_UNSOL_STK_CALL_CTRL = (RIL_UNSOL_VENDOR_BASE + 41);
    int RIL_UNSOL_VOLTE_EPS_NETWORK_FEATURE_SUPPORT = (RIL_UNSOL_VENDOR_BASE + 42);

    /// M: IMS feature. @{
    int RIL_UNSOL_CALL_INFO_INDICATION = (RIL_UNSOL_VENDOR_BASE + 43);
    /// @}

    int RIL_UNSOL_VOLTE_EPS_NETWORK_FEATURE_INFO = (RIL_UNSOL_VENDOR_BASE + 44);
    int RIL_UNSOL_SRVCC_HANDOVER_INFO_INDICATION = (RIL_UNSOL_VENDOR_BASE + 45);
    /// M: CC: GSA HD Voice for 2/3G network support
    int RIL_UNSOL_SPEECH_CODEC_INFO = (RIL_UNSOL_VENDOR_BASE + 46);

    //MTK-START for MD state change
    int RIL_UNSOL_MD_STATE_CHANGE = (RIL_UNSOL_VENDOR_BASE + 47);
    //MTK-END for MD state change
    // M: CC33 URC
    int RIL_UNSOL_REMOVE_RESTRICT_EUTRAN = (RIL_UNSOL_VENDOR_BASE + 48);

    // IMS client on AP shall get the information of MO Data Barring and SSAC barring
    int RIL_UNSOL_SSAC_BARRING_INFO = (RIL_UNSOL_VENDOR_BASE + 49);
    int RIL_UNSOL_SIP_CALL_PROGRESS_INDICATOR = (RIL_UNSOL_VENDOR_BASE + 50);

    int RIL_UNSOL_ABNORMAL_EVENT = (RIL_UNSOL_VENDOR_BASE + 51);

    /// M: CC071: Add Customer proprietary-IMS RIL interface . @{
    int RIL_UNSOL_EMERGENCY_BEARER_SUPPORT_NOTIFY = (RIL_UNSOL_VENDOR_BASE + 52);
    /// @}

    /// M: [C2K] MD IRAT RIL URCs.
    int RIL_UNSOL_INTER_3GPP_IRAT_STATE_CHANGE = RIL_UNSOL_VENDOR_BASE + 53;

    /// M: [C2K][IR][MD-IRAT] URC for GMSS RAT changed. @{
    int RIL_UNSOL_GMSS_RAT_CHANGED = (RIL_UNSOL_VENDOR_BASE + 54);
    /// M: [C2K][IR][MD-IRAT] URC for GMSS RAT changed. @}

    int RIL_UNSOL_CDMA_CARD_TYPE = (RIL_UNSOL_VENDOR_BASE + 55);

    // IMS
    int RIL_UNSOL_IMS_ENABLE_START = (RIL_UNSOL_VENDOR_BASE + 56);
    int RIL_UNSOL_IMS_DISABLE_START = (RIL_UNSOL_VENDOR_BASE + 57);
    int RIL_UNSOL_IMSI_REFRESH_DONE = (RIL_UNSOL_VENDOR_BASE + 58);
    // M: Notify RILJ that the AT+EUSIM was received
    int RIL_UNSOL_EUSIM_READY = (RIL_UNSOL_VENDOR_BASE + 59);
    /// M: BIP {
    int RIL_UNSOL_STK_BIP_PROACTIVE_COMMAND = (RIL_UNSOL_VENDOR_BASE + 60);
    /// M: BIP }
    //WorldMode
    int RIL_UNSOL_WORLD_MODE_CHANGED = (RIL_UNSOL_VENDOR_BASE + 61);

    /// M: CC: For 3G VT only @{
    int RIL_UNSOL_VT_STATUS_INFO = (RIL_UNSOL_VENDOR_BASE + 62);
    int RIL_UNSOL_VT_RING_INFO = (RIL_UNSOL_VENDOR_BASE + 63);
    /// @}

    //External SIM [Start]
    int RIL_UNSOL_VSIM_OPERATION_INDICATION = (RIL_UNSOL_VENDOR_BASE + 64);
    //External SIM [End]

    //Reset Attach APN
    int RIL_UNSOL_SET_ATTACH_APN = (RIL_UNSOL_VENDOR_BASE + 65);

    /// M: MAL @{
    int RIL_UNSOL_MAL_AT_INFO = (RIL_UNSOL_VENDOR_BASE + 66);
    //info the current 3G SIM
    int RIL_UNSOL_MAIN_SIM_INFO = (RIL_UNSOL_VENDOR_BASE + 67);
    /// @}
    // M: The event for pluggin in the tray in common slot project
    int RIL_UNSOL_TRAY_PLUG_IN = (RIL_UNSOL_VENDOR_BASE + 68);

    // M: volte for call mode change notification
    int RIL_UNSOL_CALLMOD_CHANGE_INDICATOR = (RIL_UNSOL_VENDOR_BASE + 69);

    // M: vilte for video capability notification
    int RIL_UNSOL_VIDEO_CAPABILITY_INDICATOR = (RIL_UNSOL_VENDOR_BASE + 70);

    // M: [LTE][Low Power][UL traffic shaping] Start
    int RIL_UNSOL_LTE_ACCESS_STRATUM_STATE_CHANGE = (RIL_UNSOL_VENDOR_BASE + 71);
    // M: [LTE][Low Power][UL traffic shaping] End

    /// M: USSI URC @{
    int RIL_UNSOL_ON_USSI = (RIL_UNSOL_VENDOR_BASE + 73);
    /// @}

    /// M: C2K URC list
    int RIL_UNSOL_CDMA_CALL_ACCEPTED = (RIL_UNSOL_VENDOR_BASE + 74);
    int RIL_UNSOL_VIA_GPS_EVENT = (RIL_UNSOL_VENDOR_BASE + 75);
    /// M: [C2K][IR] Support SVLTE IR feature. @{
    int RIL_UNSOL_CDMA_PLMN_CHANGED = (RIL_UNSOL_VENDOR_BASE + 76);
    /// M: [C2K][IR] Support SVLTE IR feature. @}
    int RIL_UNSOL_VIA_INVALID_SIM_DETECTED = (RIL_UNSOL_VENDOR_BASE + 77);
    /// M: For c2k eng mode
    int RIL_UNSOL_ENG_MODE_NETWORK_INFO = (RIL_UNSOL_VENDOR_BASE + 78);
    int RIL_UNSOL_CDMA_IMSI_READY = (RIL_UNSOL_VENDOR_BASE + 79);
    // M: Notify RILJ that the AT+EFNM was received
    //int RIL_UNSOL_CDMA_TONE_SIGNALS = (RIL_UNSOL_VENDOR_BASE + 80); //obsolete
    // M: add for ECC only
    int RIL_UNSOL_NETWORK_EXIST = (RIL_UNSOL_VENDOR_BASE + 81);
    /* M: Add C2K proprietary end */
    int RIL_UNSOL_MODULATION_INFO = (RIL_UNSOL_VENDOR_BASE + 82);
    int RIL_UNSOL_NETWORK_EVENT = (RIL_UNSOL_VENDOR_BASE + 83);

    // M: MBIM
    int RIL_UNSOL_MBIM_RESPONSE = (RIL_UNSOL_VENDOR_BASE + 84);

    /// M: IMS Deregistration @{
    int RIL_UNSOL_IMS_DEREG_DONE = (RIL_UNSOL_VENDOR_BASE + 85);
    /// @}

    /// M: IMS RTP Report @{
    int RIL_UNSOL_IMS_RTP_INFO = (RIL_UNSOL_VENDOR_BASE + 102);
    /// @}

    /* M: eMBMS feature */
    int RIL_UNSOL_EMBMS_CELL_INFO_NOTIFICATION = (RIL_UNSOL_VENDOR_BASE + 86);
    int RIL_UNSOL_EMBMS_COVERAGE_STATE = (RIL_UNSOL_VENDOR_BASE + 87);
    int RIL_UNSOL_EMBMS_ACTIVE_SESSION = (RIL_UNSOL_VENDOR_BASE + 88);
    int RIL_UNSOL_EMBMS_AVAILABLE_SESSION = (RIL_UNSOL_VENDOR_BASE + 89);
    int RIL_UNSOL_EMBMS_SAI_LIST_NOTIFICATION = (RIL_UNSOL_VENDOR_BASE + 90);
    int RIL_UNSOL_EMBMS_OOS_NOTIFICATION = (RIL_UNSOL_VENDOR_BASE + 91);
    int RIL_UNSOL_EMBMS_MODEM_EE_NOTIFICATION = (RIL_UNSOL_VENDOR_BASE + 92);
    int RIL_UNSOL_EMBMS_SESSION_STATUS = (RIL_UNSOL_VENDOR_BASE + 93);

    /// M: MAL @{
    int RIL_UNSOL_RADIO_CAPABILITY_SWITCH_START = (RIL_UNSOL_VENDOR_BASE + 94);
    /// @}

    int RIL_UNSOL_PCO_STATUS = (RIL_UNSOL_VENDOR_BASE + 95);

    // M: IA-change attach APN
    int RIL_UNSOL_DATA_ATTACH_APN_CHANGED = (RIL_UNSOL_VENDOR_BASE + 96);

    /// M: IMS Provisioning URC. @{
    int RIL_UNSOL_GET_PROVISION_DONE = (RIL_UNSOL_VENDOR_BASE + 97);
    /// @}
    /// M: To trigger OTASP @{
    int RIL_UNSOL_TRIGGER_OTASP = (RIL_UNSOL_VENDOR_BASE + 98);
    /// @}
    /// M: CC: Vzw ECC/hVoLTE redial
    int RIL_UNSOL_CALL_REDIAL_STATE = (RIL_UNSOL_VENDOR_BASE + 99);

    // MTK-START, SMS part
    // CDMA Card inital ESN or MEID for CT self register
    int RIL_UNSOL_CDMA_CARD_INITIAL_ESN_OR_MEID = (RIL_UNSOL_VENDOR_BASE + 100);
    // MTK-END, SMS part

    // M: IMS ECT @{
    int RIL_UNSOL_ECT_INDICATION = (RIL_UNSOL_VENDOR_BASE + 101);
    /// @}

    // APC
    int RIL_UNSOL_PSEUDO_BS_INFO_LIST = (RIL_UNSOL_VENDOR_BASE + 103);

    /// M: Sync volte setting value. @{
    int RIL_UNSOL_VOLTE_SETTING = (RIL_UNSOL_VENDOR_BASE + 104);
    // M: IMS Bearer @{
    int RIL_UNSOL_IMS_BEARER_ACTIVATION = (RIL_UNSOL_VENDOR_BASE + 105);
    int RIL_UNSOL_IMS_BEARER_DEACTIVATION = (RIL_UNSOL_VENDOR_BASE + 106);
    /// @}

    /// M: USSI URC @{
    int RIL_UNSOL_ON_XUI = (RIL_UNSOL_VENDOR_BASE + 107);
    /// @}

    /// M: IMS Event package @{
    int RIL_UNSOL_IMS_EVENT_PACKAGE_INDICATION = (RIL_UNSOL_VENDOR_BASE + 108);
    /// @}

    /// M: Cellular data registration state to MD
    int RIL_UNSOL_RESPONSE_CELLULAR_DATA_REG_STATE = (RIL_UNSOL_VENDOR_BASE + 109);
    /// @}

    /// M: MWis @{
    int RIL_UNSOL_MOBILE_WIFI_ROVEOUT = (RIL_UNSOL_VENDOR_BASE + 110);
    int RIL_UNSOL_MOBILE_WIFI_HANDOVER = (RIL_UNSOL_VENDOR_BASE + 111);
    int RIL_UNSOL_ACTIVE_WIFI_PDN_COUNT = (RIL_UNSOL_VENDOR_BASE + 112);
    int RIL_UNSOL_WIFI_RSSI_MONITORING_CONFIG = (RIL_UNSOL_VENDOR_BASE + 113);
    int RIL_UNSOL_WIFI_PDN_ERROR = (RIL_UNSOL_VENDOR_BASE + 114);
    int RIL_UNSOL_REQUEST_GEO_LOCATION = (RIL_UNSOL_VENDOR_BASE + 115);
    /// @}
    // M: Data Retry, MD data retry count reset
    int RIL_UNSOL_MD_DATA_RETRY_COUNT_RESET = (RIL_UNSOL_VENDOR_BASE + 116);
    /// M: RADIO_STATE_CHANGE for IMS only (Only used in 93 + Android N) @{
    int RIL_UNSOL_IMS_RADIO_STATE_CHANGED = (RIL_UNSOL_VENDOR_BASE + 117);
    /// @}
    int RIL_UNSOL_RESPONSE_CS_NETWORK_STATE_CHANGED = (RIL_UNSOL_VENDOR_BASE + 118);
    /// M: IMS ECC SUPPORT @{
    int RIL_UNSOL_IMS_SUPPORT_ECC = (RIL_UNSOL_VENDOR_BASE + 119);
    /// @}
    /// M: IMS ECC SUPPORT @{
    int RIL_UNSOL_IMS_BEARER_INIT = (RIL_UNSOL_VENDOR_BASE + 120);
    /// @}
    /// M: MWis @{
    int RIL_UNSOL_WFC_PDN_STATE = (RIL_UNSOL_VENDOR_BASE + 121);
    /// @}
    int RIL_UNSOL_TX_POWER = (RIL_UNSOL_VENDOR_BASE + 122);
}
