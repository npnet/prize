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

package com.android.systemui.statusbar.policy;

import com.android.systemui.R;
//add for statusbar inverse. prize-linkh-20150903
import android.app.StatusBarManager;

public class WifiIcons {
    static final int[][] WIFI_SIGNAL_STRENGTH = {
            { R.drawable.stat_sys_wifi_signal_0,
              R.drawable.stat_sys_wifi_signal_1,
              R.drawable.stat_sys_wifi_signal_2,
              R.drawable.stat_sys_wifi_signal_3,
              R.drawable.stat_sys_wifi_signal_4 },
            { R.drawable.stat_sys_wifi_signal_0_fully,
              R.drawable.stat_sys_wifi_signal_1_fully,
              R.drawable.stat_sys_wifi_signal_2_fully,
              R.drawable.stat_sys_wifi_signal_3_fully,
              R.drawable.stat_sys_wifi_signal_4_fully }
        };

    public static final int[][] QS_WIFI_SIGNAL_STRENGTH = {
            { R.drawable.ic_qs_wifi_0,
              R.drawable.ic_qs_wifi_1,
              R.drawable.ic_qs_wifi_2,
              R.drawable.ic_qs_wifi_3,
              R.drawable.ic_qs_wifi_4 },
            { R.drawable.ic_qs_wifi_full_0,
              R.drawable.ic_qs_wifi_full_1,
              R.drawable.ic_qs_wifi_full_2,
              R.drawable.ic_qs_wifi_full_3,
              R.drawable.ic_qs_wifi_full_4 }
        };

    static final int QS_WIFI_NO_NETWORK = R.drawable.ic_qs_wifi_no_network;
    static final int WIFI_NO_NETWORK = R.drawable.stat_sys_wifi_signal_null;

    static final int WIFI_LEVEL_COUNT = WIFI_SIGNAL_STRENGTH[0].length;

    /// M: [WIFI StatusBar Active Icon] add icons for feature @ {
    static final int[][] WIFI_SIGNAL_STRENGTH_INOUT = {
        { R.drawable.stat_sys_wifi_signal_0_fully,
          R.drawable.stat_sys_wifi_signal_0_fully,
          R.drawable.stat_sys_wifi_signal_0_fully,
          R.drawable.stat_sys_wifi_signal_0_fully },

        { R.drawable.stat_sys_wifi_signal_1_fully,
          R.drawable.stat_sys_wifi_signal_1_fully_in,
          R.drawable.stat_sys_wifi_signal_1_fully_out,
          R.drawable.stat_sys_wifi_signal_1_fully_inout },

        { R.drawable.stat_sys_wifi_signal_2_fully,
          R.drawable.stat_sys_wifi_signal_2_fully_in,
          R.drawable.stat_sys_wifi_signal_2_fully_out,
          R.drawable.stat_sys_wifi_signal_2_fully_inout },

        { R.drawable.stat_sys_wifi_signal_3_fully,
          R.drawable.stat_sys_wifi_signal_3_fully_in,
          R.drawable.stat_sys_wifi_signal_3_fully_out,
          R.drawable.stat_sys_wifi_signal_3_fully_inout },

        { R.drawable.stat_sys_wifi_signal_4_fully,
          R.drawable.stat_sys_wifi_signal_4_fully_in,
          R.drawable.stat_sys_wifi_signal_4_fully_out,
          R.drawable.stat_sys_wifi_signal_4_fully_inout }
    };
    /// @ }
    /*PRIZE 修改状态栏wifi图标(信号格数和数据交互) liyao 20150615 start*/
    static final int[] WIFI_SIGNAL_STRENGTH_PRIZE = {
          R.drawable.stat_sys_wifi_signal_0_prize,
          R.drawable.stat_sys_wifi_signal_1_prize,
          R.drawable.stat_sys_wifi_signal_2_prize,
          R.drawable.stat_sys_wifi_signal_3_prize,
          R.drawable.stat_sys_wifi_signal_4_prize,
    };

    static final int[] WIFI_INOUT_PRIZE = {
          0,
          R.drawable.stat_sys_wifi_in_prize,
          R.drawable.stat_sys_wifi_out_prize,
          R.drawable.stat_sys_wifi_inout_prize,
    };
    /*PRIZE 修改状态栏wifi图标(信号格数和数据交互) liyao 20150615 end*/

    //add for statusbar inverse. prize-linkh-20150903
    static final int[] WIFI_SIGNAL_STRENGTH_GRAY_PRIZE = {
          R.drawable.stat_sys_wifi_signal_0_gray_prize,
          R.drawable.stat_sys_wifi_signal_1_gray_prize,
          R.drawable.stat_sys_wifi_signal_2_gray_prize,
          R.drawable.stat_sys_wifi_signal_3_gray_prize,
          R.drawable.stat_sys_wifi_signal_4_gray_prize,
    };

    static final int[] WIFI_INOUT_GRAY_PRIZE = {
          0,
          R.drawable.stat_sys_wifi_in_gray_prize,
          R.drawable.stat_sys_wifi_out_gray_prize,
          R.drawable.stat_sys_wifi_inout_gray_prize,
    };
    static int getWifiSignalStrengthIcon(int style, int level) {
        int icon = 0;
        if(style == StatusBarManager.STATUS_BAR_INVERSE_WHITE) {
            icon = WIFI_SIGNAL_STRENGTH_PRIZE[level];
        } else if(style == StatusBarManager.STATUS_BAR_INVERSE_GRAY) {
            icon = WIFI_SIGNAL_STRENGTH_GRAY_PRIZE[level];
        }
        
        return icon;
    }
    public static int getWifiInOutIcon(int style, int activity) {
        int icon = 0;
        if(style == StatusBarManager.STATUS_BAR_INVERSE_WHITE) {
            icon = WIFI_INOUT_PRIZE[activity];
        } else if(style == StatusBarManager.STATUS_BAR_INVERSE_GRAY) {
            icon = WIFI_INOUT_GRAY_PRIZE[activity];
        }
        
        return icon;
    } //end...
}
