/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 */
/* MediaTek Inc. (C) 2010. All rights reserved.
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

/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.providers.media;

import android.os.Build;
import android.os.SystemProperties;

public final class MediaUtils {
    /// M: feature support
    public static final boolean IS_SUPPORT_DRM = SystemProperties.getBoolean("ro.mtk_oma_drm_support", false);
    public static final boolean IS_SUPPORT_SDCARD_SWAP = SystemProperties.getBoolean("ro.mtk_2sdcard_swap", false);
    public static final boolean IS_SUPPORT_SHARED_SDCARD = SystemProperties.getBoolean("ro.mtk_shared_sdcard", false);
    public static final boolean IS_SUPPORT_MULTI_PARTITION = SystemProperties.getBoolean("ro.mtk_multi_partition", false);

    /// M: debug switch
    private static final boolean ENG_LOAD = "eng".equals(Build.TYPE);
    public static final boolean LOG_QUERY = SystemProperties.getBoolean("debug.log_query", false);
    public static final boolean LOG_INSERT = SystemProperties.getBoolean("debug.log_insert", ENG_LOAD);
    public static final boolean LOG_UPDATE = SystemProperties.getBoolean("debug.log_update", ENG_LOAD);
    public static final boolean LOG_DELETE = SystemProperties.getBoolean("debug.log_delete", ENG_LOAD);
    public static final boolean LOG_SCAN = SystemProperties.getBoolean("debug.log_scan", ENG_LOAD);

    /// M: call method(trigger from static MediaScannerReceiver)
    public final static String ACTION_MEDIA_UNMOUNTED = "action_media_unmounted";
    /// M: call method(trigger from MediaScannerService for clearing overtime data in db)
    public final static String ACTION_REMOVE_OVERTIME = "action_remove_overtime";
    /// M: call method(trigger from MediaScannerService for notify MediaProvider that
    //prescan started)
    public final static String ACTION_PRESCAN_STARTED = "action_prescan_started";
    /// M: call method(trigger from MediaScannerService for notify MediaProvider that prescan done)
    public final static String ACTION_PRESCAN_DONE = "action_prescan_done";
}
