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
 * limitations under the License.
 */

package com.prize.permissionmanage;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.prize.permissionmanage.utils.DeviceUtils;
import com.prize.permissionmanage.R;

public abstract class SettingsWithHeader extends PermissionsFrameFragment
        implements OnClickListener {

    private View mHeader;
    protected Intent mInfoIntent;
    protected Drawable mIcon;
    protected CharSequence mLabel;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        ViewGroup root = (ViewGroup) super.onCreateView(inflater, container, savedInstanceState);

        if (shouldShowHeader()) { /// M: CTA requirement - permission control
            mHeader = inflater.inflate(R.layout.header, root, false);
            getPreferencesContainer().addView(mHeader, 0);
            updateHeader();
        }

        return root;
    }

    public void setHeader(Drawable icon, CharSequence label, Intent infoIntent) {
        mIcon = icon;
        mLabel = label;
        mInfoIntent = infoIntent;
        updateHeader();
    }

    private void updateHeader() {
        if (mHeader != null) {
            final ImageView appIcon = (ImageView) mHeader.findViewById(R.id.icon);
            appIcon.setImageDrawable(mIcon);

            final TextView appName = (TextView) mHeader.findViewById(R.id.name);
            appName.setText(mLabel);

            final View info = mHeader.findViewById(R.id.info);
            if (mInfoIntent == null) {
                info.setVisibility(View.GONE);
            } else {
                info.setVisibility(View.GONE);//modify by lijimeng 20180102
                info.setClickable(true);
                info.setOnClickListener(this);
            }
        }
    }

    @Override
    public void onClick(View v) {
        getActivity().startActivity(mInfoIntent);
    }

    /// M: CTA requirement - permission control  @{
    protected boolean shouldShowHeader() {
       // return !DeviceUtils.isTelevision(getContext());
        return false;
    }
    ///@}

}
