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

package com.android.systemui.qs.tiles;

import android.content.Intent;
import android.provider.Settings;
import android.provider.Settings.Secure;
import android.widget.Switch;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.MetricsProto.MetricsEvent;
import com.android.systemui.R;
import com.android.systemui.qs.QSTile;
import com.android.systemui.qs.SecureSetting;

/** Quick settings tile: Invert colors **/
public class ColorInversionTile extends QSTile<QSTile.BooleanState> {

    private final AnimationIcon mEnable
            = new AnimationIcon(R.drawable.ic_invert_colors_enable_animation,
            R.drawable.ic_invert_colors_disable);
    private final AnimationIcon mDisable
            = new AnimationIcon(R.drawable.ic_invert_colors_disable_animation,
            R.drawable.ic_invert_colors_enable);
    private final SecureSetting mSetting;

    private boolean mListening;

    public ColorInversionTile(Host host) {
        super(host);

        mSetting = new SecureSetting(mContext, mHandler,
                Secure.ACCESSIBILITY_DISPLAY_INVERSION_ENABLED) {
            @Override
            protected void handleValueChanged(int value, boolean observedChange) {
                handleRefreshState(value);
            }
        };
    }

    @Override
    protected void handleDestroy() {
        super.handleDestroy();
        mSetting.setListening(false);
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    public void setListening(boolean listening) {
        mSetting.setListening(listening);
    }

    @Override
    protected void handleUserSwitch(int newUserId) {
        mSetting.setUserId(newUserId);
        handleRefreshState(mSetting.getValue());
    }

    @Override
    public Intent getLongClickIntent() {
        return new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
    }

    @Override
    protected void handleClick() {
        MetricsLogger.action(mContext, getMetricsCategory(), !mState.value);
        mSetting.setValue(mState.value ? 0 : 1);
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_settings_inversion_label);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        final int value = arg instanceof Integer ? (Integer) arg : mSetting.getValue();
        final boolean enabled = value != 0;
        state.value = enabled;
        state.label = mContext.getString(R.string.quick_settings_inversion_label);
        state.icon = ResourceIcon.get(enabled ? R.drawable.ic_qs_inversion_on
                : R.drawable.ic_qs_inversion_off);//enabled ? mEnable : mDisable;
        state.colorId = enabled ? 1 : 0;
        state.minimalAccessibilityClassName = state.expandedAccessibilityClassName
                = Switch.class.getName();
        state.contentDescription = state.label;
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.QS_COLORINVERSION;
    }

    @Override
    protected String composeChangeAnnouncement() {
        if (mState.value) {
            return mContext.getString(
                    R.string.accessibility_quick_settings_color_inversion_changed_on);
        } else {
            return mContext.getString(
                    R.string.accessibility_quick_settings_color_inversion_changed_off);
        }
    }
}
