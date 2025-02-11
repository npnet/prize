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

package com.prize.permissionmanage.model;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.BidiFormatter;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

public final class AppPermissions {
    private final ArrayList<AppPermissionGroup> mGroups = new ArrayList<>();

    private final LinkedHashMap<String, AppPermissionGroup> mNameToGroupMap = new LinkedHashMap<>();

    private final Context mContext;

    private final String[] mFilterPermissions;

    private final CharSequence mAppLabel;

    private final Runnable mOnErrorCallback;

    private final boolean mSortGroups;

    private PackageInfo mPackageInfo;

    public AppPermissions(Context context, PackageInfo packageInfo, String[] permissions,
            boolean sortGroups, Runnable onErrorCallback) {
        mContext = context;
        mPackageInfo = packageInfo;
        mFilterPermissions = permissions;
        mAppLabel = BidiFormatter.getInstance().unicodeWrap(
                packageInfo.applicationInfo.loadSafeLabel(
                context.getPackageManager()).toString());
        mSortGroups = sortGroups;
        mOnErrorCallback = onErrorCallback;
        loadPermissionGroups();
    }

    public PackageInfo getPackageInfo() {
        return mPackageInfo;
    }

    public void refresh() {
        loadPackageInfo();
        loadPermissionGroups();
    }

    public CharSequence getAppLabel() {
        return mAppLabel;
    }

    public AppPermissionGroup getPermissionGroup(String name) {
        if((name != null) && mNameToGroupMap.containsKey(name)){
			return mNameToGroupMap.get(name);
        }else{
            return null;
        }
    }

    public List<AppPermissionGroup> getPermissionGroups() {
        return mGroups;
    }

    public boolean isReviewRequired() {
        /// M: CTA requirement - permission control  @{
        if (!Build.isPermissionReviewRequired()) {
            return false;
        }
        final int groupCount = mGroups.size();
        for (int i = 0; i < groupCount; i++) {
            AppPermissionGroup group = mGroups.get(i);
            if (group.isReviewRequired()) {
                return true;
            }
        }
        return false;
    }

    private void loadPackageInfo() {
        try {
            mPackageInfo = mContext.getPackageManager().getPackageInfo(
                    mPackageInfo.packageName, PackageManager.GET_PERMISSIONS);
        } catch (PackageManager.NameNotFoundException e) {
            if (mOnErrorCallback != null) {
                mOnErrorCallback.run();
            }
        }
    }

    private void loadPermissionGroups() {
        mGroups.clear();

        if (mPackageInfo.requestedPermissions == null) {
            return;
        }

        if (mFilterPermissions != null) {
            for (String filterPermission : mFilterPermissions) {
                for (String requestedPerm : mPackageInfo.requestedPermissions) {
                    if (!filterPermission.equals(requestedPerm)) {
                        continue;
                    }

                    if (hasGroupForPermission(requestedPerm)) {
                        break;
                    }

                    AppPermissionGroup group = AppPermissionGroup.create(mContext,
                            mPackageInfo, requestedPerm);
                    if (group == null) {
                        break;
                    }

                    mGroups.add(group);
                    break;
                }
            }
        } else {
            for (String requestedPerm : mPackageInfo.requestedPermissions) {
                Log.d("mengge","requestedPerm == "+requestedPerm);
                if (hasGroupForPermission(requestedPerm)) {
                    continue;
                }

                AppPermissionGroup group = AppPermissionGroup.create(mContext,
                        mPackageInfo, requestedPerm);
                if (group == null) {
                    continue;
                }

                mGroups.add(group);
            }
        }

        if (mSortGroups) {
            Collections.sort(mGroups);
        }

        mNameToGroupMap.clear();
        for (AppPermissionGroup group : mGroups) {
            mNameToGroupMap.put(group.getName(), group);
        }
    }

    private boolean hasGroupForPermission(String permission) {
        for (AppPermissionGroup group : mGroups) {
            if (group.hasPermission(permission)) {
                return true;
            }
        }
        return false;
    }
}
