/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.camera.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.accessibility.AccessibilityEvent;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.Switch;

import com.android.camera.CameraActivity;
import com.android.camera.Log;
import com.android.camera.R;
import com.android.camera.Util;
import com.android.camera.manager.ViewManager;

import com.mediatek.camera.setting.SettingConstants;
import com.mediatek.camera.setting.preference.ListPreference;

/* A switch setting control which turns on/off the setting. */
public class InLineSettingSwitch extends InLineSettingItem implements
        SettingSwitchSublistLayout.Listener, CameraActivity.OnOrientationListener {
    private static final String TAG = "InLineSettingSwitch";

    private SettingSwitchSublistLayout mSettingLayout;
    private CameraActivity mContext;
    private Switch mSwitch;
    private boolean mShowingChildList;

    OnCheckedChangeListener mCheckedChangeListener = new OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean desiredState) {
            changeIndex(desiredState ? 1 : 0);
        }
    };

    public InLineSettingSwitch(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = (CameraActivity) context;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mSwitch = (Switch) findViewById(R.id.setting_switch);
        mSwitch.setOnCheckedChangeListener(mCheckedChangeListener);
        setOnClickListener(mOnClickListener);
    }

    @Override
    public void initialize(ListPreference preference) {
        super.initialize(preference);
        // Add content descriptions for the increment and decrement buttons.
        mSwitch.setContentDescription(getContext().getResources().getString(
                R.string.accessibility_switch, mPreference.getTitle()));
    }

    @Override
    protected void updateView() {
        mSwitch.setOnCheckedChangeListener(null);
        mOverrideValue = mPreference.getOverrideValue();
        if (mOverrideValue == null) {
            mSwitch.setChecked(mIndex == 1);
        } else {
            int index = mPreference.findIndexOfValue(mOverrideValue);
            mSwitch.setChecked(index == 1);
        }
        setEnabled(mPreference.isEnabled());
        mSwitch.setOnCheckedChangeListener(mCheckedChangeListener);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (mSwitch != null) {
            mSwitch.setEnabled(enabled);
        }
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        onPopulateAccessibilityEvent(event);
        return true;
    }

    @Override
    public void onPopulateAccessibilityEvent(AccessibilityEvent event) {
        super.onPopulateAccessibilityEvent(event);
        event.getText().add(mPreference.getTitle());
    }

    private OnClickListener mOnClickListener = new OnClickListener() {
        @Override
        public void onClick(View view) {
            Log.d(TAG, "onClick() mPreference=" + mPreference);
            if (mPreference != null && mPreference.isClickable() && mPreference.isEnabled()) {
            	/*PRIZE-modify setting show-wanzhijuan-2016-04-26-start*/
                /*if (mListener != null) {
                    mListener.onShow(InLineSettingSwitch.this);
                }
                if (mPreference.getKey().equals(SettingConstants.KEY_VOICE)) {
                    if (!mShowingChildList) {
                        extendChild();
                    } else {
                        collapseChild();
                    }
                } else*/ {
                	if (mListener != null) {
                        if (!mListener.onShow(InLineSettingSwitch.this)) {
                        	/*  PRIZE-modify-by-mafei-20160620-bugid-35023-start*/
                        	if (mSwitch != null && mSwitch.isEnabled()) {
                               // mSwitch.performClick(); 
                        		mSwitch.setChecked(!mSwitch.isChecked());
                        		sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CLICKED);
                        		/*PRIZE-modify-by-mafei-20160620-bugid-35023-end-*/
                            }
                        }
                    }
                	/*PRIZE-modify setting show-wanzhijuan-2016-04-26-end*/
                }
            }
        }
    };

    public boolean extendChild() {
        boolean extend = false;
        if (!mShowingChildList) {
            mShowingChildList = true;
//            mContext.getVoiceManager().setVoiceSublistShow(mShowingChildList);
            mSettingLayout = (SettingSwitchSublistLayout) mContext.inflate(
                    R.layout.setting_switch_sublist_layout, ViewManager.VIEW_LAYER_SETTING);
            mSettingLayout.initialize(mPreference);
            mContext.addView(mSettingLayout, ViewManager.VIEW_LAYER_SETTING);
            mContext.addOnOrientationListener(this);
            mSettingLayout.setSettingChangedListener(this);
            setOrientation(mContext.getOrientationCompensation(), false);
            fadeIn(mSettingLayout);
            /*PRIZE-modify setting show-wanzhijuan-2016-04-26-start*/
//            mSwitch.setClickable(false);
            /*PRIZE-modify setting show-wanzhijuan-2016-04-26-end*/
            extend = true;
        }
        Log.d(TAG, "extendChild() return " + extend);
        return extend;
    }

    public boolean collapseChild() {
        boolean collapse = false;
        if (mShowingChildList) {
            mContext.removeView(mSettingLayout, ViewManager.VIEW_LAYER_SETTING);
            mContext.removeOnOrientationListener(this);
            fadeOut(mSettingLayout);
            mSettingLayout = null;
            mShowingChildList = false;
            if (mListener != null) {
                mListener.onDismiss(this);
            }
            /*PRIZE-modify setting show-wanzhijuan-2016-04-26-start*/
//            mSwitch.setClickable(true);
            /*PRIZE-modify setting show-wanzhijuan-2016-04-26-end*/
            collapse = true;
        }
        Log.d(TAG, "collapseChild() return " + collapse);
        return collapse;
    }

    @Override
    public void onOrientationChanged(int orientation) {
        setOrientation(orientation, true);
    }

    private void setOrientation(int orientation, boolean animation) {
        Log.d(TAG, "setOrientation(" + orientation + "," + animation + ")");
        if (mShowingChildList) {
            Util.setOrientation(mSettingLayout, orientation, animation);
        }
    }

    @Override
    public void onVoiceCommandChanged(int index) {
        if (mListener != null) {
            mListener.onVoiceCommandChanged(index);
        }
    }
}
