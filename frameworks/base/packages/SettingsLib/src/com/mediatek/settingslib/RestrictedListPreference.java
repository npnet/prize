/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.mediatek.settingslib;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.UserHandle;
import android.support.v4.content.res.TypedArrayUtils;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.preference.PreferenceViewHolder;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

import com.android.settingslib.R;
import com.android.settingslib.RestrictedPreferenceHelper;
import static com.android.settingslib.RestrictedLockUtils.EnforcedAdmin;

/**
 * Preference class that supports being disabled by a user restriction
 * set by a device admin.
 */
public class RestrictedListPreference extends ListPreference {
    RestrictedPreferenceHelper mHelper;
    boolean mUseAdditionalSummary = false;
    String mRestrictedSwitchSummary = null;

    public RestrictedListPreference(Context context, AttributeSet attrs,
            int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setWidgetLayoutResource(R.layout.restricted_icon);
        mHelper = new RestrictedPreferenceHelper(context, this, attrs);
        if (attrs != null) {
            final TypedArray attributes = context.obtainStyledAttributes(attrs,
                    R.styleable.RestrictedSwitchPreference);
            final TypedValue useAdditionalSummary = attributes.peekValue(
                    R.styleable.RestrictedSwitchPreference_useAdditionalSummary);
            if (useAdditionalSummary != null) {
                mUseAdditionalSummary =
                        (useAdditionalSummary.type == TypedValue.TYPE_INT_BOOLEAN
                                && useAdditionalSummary.data != 0);
            }

            final TypedValue restrictedSwitchSummary = attributes.peekValue(
                    R.styleable.RestrictedSwitchPreference_restrictedSwitchSummary);
            CharSequence data = null;
            if (restrictedSwitchSummary != null
                    && restrictedSwitchSummary.type == TypedValue.TYPE_STRING) {
                if (restrictedSwitchSummary.resourceId != 0) {
                    data = context.getString(restrictedSwitchSummary.resourceId);
                } else {
                    data = restrictedSwitchSummary.string;
                }
            }
            mRestrictedSwitchSummary = data == null ? null : data.toString();
        }
        if (mRestrictedSwitchSummary == null) {
            mRestrictedSwitchSummary = context.getString(R.string.disabled_by_admin);
        }
        if (mUseAdditionalSummary) {
            setLayoutResource(R.layout.restricted_switch_preference);
            useAdminDisabledSummary(false);
        }
    }

    public RestrictedListPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public RestrictedListPreference(Context context, AttributeSet attrs) {
        this(context, attrs, TypedArrayUtils.getAttr(context,
                android.support.v7.preference.R.attr.preferenceStyle,
                android.R.attr.preferenceStyle));
    }

    public RestrictedListPreference(Context context) {
        this(context, null);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        mHelper.onBindViewHolder(holder);
        final View restrictedIcon = holder.findViewById(R.id.restricted_icon);
        if (restrictedIcon != null) {
            restrictedIcon.setVisibility(isDisabledByAdmin() ? View.VISIBLE : View.GONE);
        }
        if (mUseAdditionalSummary) {
            final TextView additionalSummaryView = (TextView) holder.findViewById(
                    R.id.additional_summary);
            if (additionalSummaryView != null) {
                if (isDisabledByAdmin()) {
                    additionalSummaryView.setText(mRestrictedSwitchSummary);
                    additionalSummaryView.setVisibility(View.VISIBLE);
                } else {
                    additionalSummaryView.setVisibility(View.GONE);
                }
            }
        } else {
            final TextView summaryView = (TextView) holder.findViewById(android.R.id.summary);
            if (summaryView != null) {
                if (isDisabledByAdmin()) {
                    summaryView.setText(mRestrictedSwitchSummary);
                    summaryView.setVisibility(View.VISIBLE);
                }
                // No need to change the visibility to GONE in the else case here since Preference
                // class would have already changed it if there is no summary to display.
            }
        }
    }

    @Override
    public void performClick() {
        if (!mHelper.performClick()) {
            super.performClick();
        }
    }

    public void useAdminDisabledSummary(boolean useSummary) {
        mHelper.useAdminDisabledSummary(useSummary);
    }

    @Override
    protected void onAttachedToHierarchy(PreferenceManager preferenceManager) {
        mHelper.onAttachedToHierarchy();
        super.onAttachedToHierarchy(preferenceManager);
    }

    public void checkRestrictionAndSetDisabled(String userRestriction) {
        mHelper.checkRestrictionAndSetDisabled(userRestriction, UserHandle.myUserId());
    }

    public void checkRestrictionAndSetDisabled(String userRestriction, int userId) {
        mHelper.checkRestrictionAndSetDisabled(userRestriction, userId);
    }

    @Override
    public void setEnabled(boolean enabled) {
        if (enabled && isDisabledByAdmin()) {
            mHelper.setDisabledByAdmin(null);
            return;
        }
        super.setEnabled(enabled);
    }

    public void setDisabledByAdmin(EnforcedAdmin admin) {
        if (mHelper.setDisabledByAdmin(admin)) {
            notifyChanged();
        }
    }

    public boolean isDisabledByAdmin() {
        return mHelper.isDisabledByAdmin();
    }
}
