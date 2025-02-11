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
package com.mediatek.contacts.util;

import com.android.internal.telephony.IccProvider;

public final class ErrorCause {

    /// Flag map with IccProvider @{
    public static final int SIM_ERROR_UNKNOWN =
            IccProvider.ERROR_ICC_PROVIDER_UNKNOWN; //0
    public static final int SIM_NO_ERROR =
            IccProvider.ERROR_ICC_PROVIDER_SUCCESS; //1
    public static final int SIM_NUMBER_TOO_LONG =
            IccProvider.ERROR_ICC_PROVIDER_NUMBER_TOO_LONG; // -1
    public static final int SIM_NAME_TOO_LONG =
            IccProvider.ERROR_ICC_PROVIDER_TEXT_TOO_LONG; // -2
    public static final int SIM_STORAGE_FULL =
            IccProvider.ERROR_ICC_PROVIDER_STORAGE_FULL; // -3
    public static final int SIM_ICC_NOT_READY =
            IccProvider.ERROR_ICC_PROVIDER_NOT_READY; // -4
    public static final int SIM_PASSWORD_ERROR =
            IccProvider.ERROR_ICC_PROVIDER_PASSWORD_ERROR; // -5
    public static final int SIM_ANR_TOO_LONG =
            IccProvider.ERROR_ICC_PROVIDER_ANR_TOO_LONG; // -6
    public static final int SIM_GENERIC_FAILURE =
            IccProvider.ERROR_ICC_PROVIDER_GENERIC_FAILURE; // -10
    public static final int SIM_ADN_LIST_NOT_EXIT =
            IccProvider.ERROR_ICC_PROVIDER_ADN_LIST_NOT_EXIST; // -11
    public static final int SIM_EMAIL_FULL =
            IccProvider.ERROR_ICC_PROVIDER_EMAIL_FULL; //-12
    public static final int SIM_EMAIL_TOOLONG =
            IccProvider.ERROR_ICC_PROVIDER_EMAIL_TOO_LONG; //-13
    public static final int SIM_ANR_SAVE_FAILURE =
            IccProvider.ERROR_ICC_PROVIDER_ANR_SAVE_FAILURE; //-14
    public static final int SIM_WRONG_ADN_FORMAT =
            IccProvider.ERROR_ICC_PROVIDER_WRONG_ADN_FORMAT; //-15
    public static final int SIM_WRONG_SNE_FULL =
            IccProvider.ERROR_ICC_PROVIDER_SNE_FULL; //-16
    public static final int SIM_WRONG_SNE_TOO_LONG =
            IccProvider.ERROR_ICC_PROVIDER_SNE_TOO_LONG; //-17
    /// @}

    public static final int NO_ERROR = 0;
    public static final int ERROR_UNKNOWN = 1;
    public static final int USER_CANCEL = 2;
    public static final int SIM_NOT_READY = 3;
    public static final int USIM_GROUP_NAME_OUT_OF_BOUND = 4;
    public static final int USIM_GROUP_NUMBER_OUT_OF_BOUND = 5;
    public static final int ERROR_USIM_EMAIL_LOST = 6;
}
