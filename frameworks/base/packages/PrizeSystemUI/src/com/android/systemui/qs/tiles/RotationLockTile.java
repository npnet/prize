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

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;

import android.provider.Settings;
import android.widget.Switch;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.MetricsProto.MetricsEvent;
import com.android.systemui.R;
import com.android.systemui.qs.QSTile;
import com.android.systemui.statusbar.policy.RotationLockController;
import com.android.systemui.statusbar.policy.RotationLockController.RotationLockControllerCallback;

/** Quick settings tile: Rotation **/
public class RotationLockTile extends QSTile<QSTile.BooleanState> {
    private final AnimationIcon mPortraitToAuto
            = new AnimationIcon(R.drawable.ic_portrait_to_auto_rotate_animation,
            R.drawable.ic_portrait_from_auto_rotate);
    private final AnimationIcon mAutoToPortrait
            = new AnimationIcon(R.drawable.ic_portrait_from_auto_rotate_animation,
            R.drawable.ic_portrait_to_auto_rotate);

    private final AnimationIcon mLandscapeToAuto
            = new AnimationIcon(R.drawable.ic_landscape_to_auto_rotate_animation,
            R.drawable.ic_landscape_from_auto_rotate);
    private final AnimationIcon mAutoToLandscape
            = new AnimationIcon(R.drawable.ic_landscape_from_auto_rotate_animation,
            R.drawable.ic_landscape_to_auto_rotate);

    private final RotationLockController mController;

    public RotationLockTile(Host host) {
        super(host);
        mController = host.getRotationLockController();
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    public void setListening(boolean listening) {
        if (mController == null) return;
        if (listening) {
            mController.addRotationLockControllerCallback(mCallback);
        } else {
            mController.removeRotationLockControllerCallback(mCallback);
        }
    }

    @Override
    public Intent getLongClickIntent() {
        return new Intent(Settings.ACTION_DISPLAY_SETTINGS);
    }

    @Override
    protected void handleClick() {
        if (mController == null) return;
        MetricsLogger.action(mContext, getMetricsCategory(), !mState.value);
        final boolean newState = !mState.value;
        mController.setRotationLocked(!newState);
        refreshState(newState);
    }

    @Override
    public CharSequence getTileLabel() {
        return getState().label;
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        if (mController == null) return;
        final boolean rotationLocked = mController.isRotationLocked();
        // TODO: Handle accessibility rotation lock and whatnot.

        state.value = !rotationLocked;
        final boolean portrait = isCurrentOrientationLockPortrait(mController, mContext);
        if (rotationLocked) {
            final int label = portrait ? R.string.quick_settings_rotation_locked_portrait_label
                    : R.string.quick_settings_rotation_locked_landscape_label;
            state.label = mContext.getString(label);
            //state.icon = portrait ? mAutoToPortrait : mAutoToLandscape;
            state.icon = ResourceIcon.get(R.drawable.ic_qs_rotation_off);
            state.colorId = 0;
        } else {
            state.label = mContext.getString(R.string.quick_settings_rotation_unlocked_label);
            //state.icon = portrait ? mPortraitToAuto : mLandscapeToAuto;
            state.icon = ResourceIcon.get(R.drawable.ic_qs_rotation_on);
            state.colorId = 1;
        }
        state.contentDescription = getAccessibilityString(rotationLocked);
        state.minimalAccessibilityClassName = state.expandedAccessibilityClassName
                = Switch.class.getName();
    }

    public static boolean isCurrentOrientationLockPortrait(RotationLockController controller,
            Context context) {
        int lockOrientation = controller.getRotationLockOrientation();
        if (lockOrientation == Configuration.ORIENTATION_UNDEFINED) {
            // Freely rotating device; use current rotation
            return context.getResources().getConfiguration().orientation
                    != Configuration.ORIENTATION_LANDSCAPE;
        } else {
            return lockOrientation != Configuration.ORIENTATION_LANDSCAPE;
        }
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.QS_ROTATIONLOCK;
    }

    /**
     * Get the correct accessibility string based on the state
     *
     * @param locked Whether or not rotation is locked.
     */
    private String getAccessibilityString(boolean locked) {
        if (locked) {
            return mContext.getString(R.string.accessibility_quick_settings_rotation) + ","
                    + mContext.getString(R.string.accessibility_quick_settings_rotation_value,
                    isCurrentOrientationLockPortrait(mController, mContext)
                            ? mContext.getString(
                                    R.string.quick_settings_rotation_locked_portrait_label)
                            : mContext.getString(
                                    R.string.quick_settings_rotation_locked_landscape_label));

        } else {
            return mContext.getString(R.string.accessibility_quick_settings_rotation);
        }
    }

    @Override
    protected String composeChangeAnnouncement() {
        return getAccessibilityString(mState.value);
    }

    private final RotationLockControllerCallback mCallback = new RotationLockControllerCallback() {
        @Override
        public void onRotationLockStateChanged(boolean rotationLocked, boolean affordanceVisible) {
            refreshState(rotationLocked);
        }
    };
}
