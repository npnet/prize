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
 * limitations under the License
 */

package com.android.systemui.statusbar.phone;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.RelativeLayout;
import com.android.systemui.qs.QSPanel;
import com.android.systemui.qs.QSPanel.Callback;
import com.android.systemui.statusbar.policy.BatteryController;
import com.android.systemui.statusbar.policy.NetworkControllerImpl;
import com.android.systemui.statusbar.policy.NextAlarmController;
import com.android.systemui.statusbar.policy.UserInfoController;

public abstract class BaseStatusBarHeader extends RelativeLayout implements
        NetworkControllerImpl.EmergencyListener {

    public BaseStatusBarHeader(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public abstract int getCollapsedHeight();
    public abstract int getExpandedHeight();

    public abstract void setExpanded(boolean b);
    public abstract void setExpansion(float headerExpansionFraction);
    public abstract void setListening(boolean listening);
    public abstract void updateEverything();
    public abstract void setActivityStarter(ActivityStarter activityStarter);
    public abstract void setQSPanel(QSPanel qSPanel);
    public abstract void setBatteryController(BatteryController batteryController);
    public abstract void setNextAlarmController(NextAlarmController nextAlarmController);
    public abstract void setUserInfoController(UserInfoController userInfoController);
    public abstract void setCallback(Callback qsPanelCallback);

    /*PRIZE-dismiss edit icon,bugid:43965-liufan-2017-11-30-start*/
    public void setPhoneStatusBar(PhoneStatusBar phonestatusbar){};
    /*PRIZE-dismiss edit icon,bugid:43965-liufan-2017-11-30-end*/
}
