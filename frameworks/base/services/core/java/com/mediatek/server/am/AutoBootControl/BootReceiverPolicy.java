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
 * MediaTek Inc. (C) 2016. All rights reserved.
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

package com.mediatek.server.am.AutoBootControl;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/** @hide */
public class BootReceiverPolicy {
    private String TAG = "BootReceiverPolicy" ;
    private List<String> mBootIntentFilter = new ArrayList<String>();

    private static BootReceiverPolicy sInstance = null ;
    private Context mContext = null ;

    /**
     * Customized boot related intent here. AutoBootControl mechanism will monitor
     * applications which receive these intents.
     * @param context Context
     * @return the instance of BootReceiverPolicy
     */
    public static BootReceiverPolicy getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new BootReceiverPolicy(context) ;
        }
        return sInstance ;
    }

    private BootReceiverPolicy(Context context) {
        mContext = context ;
        initBootIntentFilterList() ;
    }

    private void initBootIntentFilterList()  {
        if (mContext != null) {
            String[] intentList = mContext.getResources().getStringArray(
                            com.mediatek.internal.R.array.config_auto_boot_policy_intent_list);
            if (intentList != null) {
                for (String intent : intentList) {
                    Log.d(TAG, "initBootIntentFilterList() - add monitored intent = " + intent) ;
                    mBootIntentFilter.add(intent);
                }
            }
        }
    }

    public List<String> getBootPolicy() {
        return mBootIntentFilter;
    }

    /** Check if the intent is monitored or not.
     * @param intent the action name of intent
     * @return the intent we monitor or not
     */
    public boolean match(String intent) {
        return mBootIntentFilter.contains(intent);
    }
}
