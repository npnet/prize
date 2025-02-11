/*
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 */
/* MediaTek Inc. (C) 2011. All rights reserved.
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

import android.content.ContentUris;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.util.Log;

import com.mediatek.simprocessor.GlobalEnv;

import java.util.List;

public class SubInfoUtils {
    private static final String TAG = "SubInfoUtils";

    public static final String PHONE_BOOK_SERVICE_NAME = "simphonebook";

    public static final String ICC_PROVIDER_SDN_URI = "content://icc/sdn/subId";
    public static final String ICC_PROVIDER_ADN_URI = "content://icc/adn/subId";
    public static final String ICC_PROVIDER_PBR_URI = "content://icc/pbr/subId";

    public static List<SubscriptionInfo> getActivatedSubInfoList() {
        Context context = GlobalEnv.getApplicationContext();
        // If has no basic permission of Phone, it shouldn't call getActiveSubscriptionInfoList.
        if (SimProcessorUtils.hasBasicPermissions(context)) {
            return SubscriptionManager.from(context).getActiveSubscriptionInfoList();
        } else {
            Log.w(TAG, "getActivatedSubInfoList has no basic permissions!");
            return null;
        }
    }

    public static Uri getIccProviderUri(int subId) {
        if (!checkSubscriber(subId)) {
            return null;
        }
        if (SimCardUtils.isUsimOrCsimType(subId)) {
            return ContentUris.withAppendedId(Uri.parse(ICC_PROVIDER_PBR_URI), subId);
        } else {
            return ContentUris.withAppendedId(Uri.parse(ICC_PROVIDER_ADN_URI), subId);
        }
    }

    public static Uri getIccProviderSdnUri(int subId) {
        if (!checkSubscriber(subId)) {
            return null;
        }
        return ContentUris.withAppendedId(Uri.parse(ICC_PROVIDER_SDN_URI), subId);
    }

    public static boolean checkSubscriber(int subId) {
        if (subId < 1) {
            Log.w(TAG, "[checkSubscriber], invalid subId: " + subId);
            return false;
        }
        return true;
    }

    public static String getPhoneBookServiceName() {
        return PHONE_BOOK_SERVICE_NAME;
    }
}
