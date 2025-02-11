/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein is
 * confidential and proprietary to MediaTek Inc. and/or its licensors. Without
 * the prior written permission of MediaTek inc. and/or its licensors, any
 * reproduction, modification, use or disclosure of MediaTek Software, and
 * information contained herein, in whole or in part, shall be strictly
 * prohibited.
 *
 * MediaTek Inc. (C) 2015. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER
 * ON AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL
 * WARRANTIES, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR
 * NONINFRINGEMENT. NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH
 * RESPECT TO THE SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY,
 * INCORPORATED IN, OR SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES
 * TO LOOK ONLY TO SUCH THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO.
 * RECEIVER EXPRESSLY ACKNOWLEDGES THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO
 * OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES CONTAINED IN MEDIATEK
 * SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK SOFTWARE
 * RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S
 * ENTIRE AND CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE
 * RELEASED HEREUNDER WILL BE, AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE
 * MEDIATEK SOFTWARE AT ISSUE, OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE
 * CHARGE PAID BY RECEIVER TO MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek
 * Software") have been modified by MediaTek Inc. All revisions are subject to
 * any receiver's applicable license agreements with MediaTek Inc.
 */

package com.mediatek.internal.telephony.cdma.pluscode;

/**
 * The Telephony PlusCode Utility interface.
 * @hide
 */
public interface IPlusCodeUtils {

    static final String PROPERTY_OPERATOR_MCC = "cdma.operator.mcc";
    static final String PROPERTY_OPERATOR_SID = "cdma.operator.sid";
    static final String PROPERTY_TIME_LTMOFFSET = "cdma.operator.ltmoffset";
    static final String PROPERTY_ICC_CDMA_OPERATOR_MCC = "cdma.icc.operator.mcc";
    static final String PROPERTY_NITZ_TIME_ZONE_ID = "cdma.operator.nitztimezoneid";

    /**
     * Check mcc by sid ltm off.
     * @param mccMnc the MCCMNC
     * @return the MCCMNC
     */
    String checkMccBySidLtmOff(String mccMnc);

    /**
     * @return if can convert plus code to IddNdd.
     */
    boolean canFormatPlusToIddNdd();

    /**
     * @return if can format plus code for sms.
     */
    boolean canFormatPlusCodeForSms();

    /**
     * Replace plus code with IDD or NDD input: the number input by the user.
     * @param number the number value
     * @return the number after deal with plus code
     */
    String replacePlusCodeWithIddNdd(String number);

    /**
     * Replace puls code, the phone number for MT or sender of sms or mms.
     * @param number the number value
     * @return the number after deal with plus code
     */
    String replacePlusCodeForSms(String number);

    /**
     * Replace puls code with IDD or NDD input: the phone number for MT or
     * sender of sms or mms.
     * @param number the number value
     * @return the number after deal with plus code
     */
    String removeIddNddAddPlusCodeForSms(String number);

    /**
     * Replace puls code with IDD or NDD input: the phone number for MT or
     * sender of sms or mms.
     * @param number the number value
     * @return the number after deal with plus code
     */
    String removeIddNddAddPlusCode(String number);
}
