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

import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageItemInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionGroupInfo;
import android.content.pm.PermissionInfo;
import android.os.Build;
import android.os.Process;
import android.os.UserHandle;
import android.util.ArrayMap;
import android.util.Log;
import android.app.Activity;

import com.prize.permissionmanage.R;
import com.prize.permissionmanage.utils.ArrayUtils;
import com.prize.permissionmanage.utils.LocationUtils;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/// M: CTA requirement - permission control  @{
import com.mediatek.cta.CtaUtils;
///@}

public final class AppPermissionGroup implements Comparable<AppPermissionGroup> {
    private static final String PLATFORM_PACKAGE_NAME = "android";

    private static final String KILL_REASON_APP_OP_CHANGE = "Permission related app op changed";

    private final Context mContext;
    private final UserHandle mUserHandle;
    private final PackageManager mPackageManager;
    private final AppOpsManager mAppOps;
    private final ActivityManager mActivityManager;

    private final PackageInfo mPackageInfo;
    private final String mName;
    private final String mDeclaringPackage;
    private final CharSequence mLabel;
    private final CharSequence mDescription;
    private final ArrayMap<String, Permission> mPermissions = new ArrayMap<>();
    private final String mIconPkg;
    private final int mIconResId;
	private static final String TAG = "AppPermissionGroup";

    private final boolean mAppSupportsRuntimePermissions;

    public static AppPermissionGroup create(Context context, PackageInfo packageInfo,
            String permissionName) {
        PermissionInfo permissionInfo;
        try {
            permissionInfo = context.getPackageManager().getPermissionInfo(permissionName, 0);//根据权限名获得权限信息
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }

        if (permissionInfo.protectionLevel != PermissionInfo.PROTECTION_DANGEROUS
                || (permissionInfo.flags & PermissionInfo.FLAG_INSTALLED) == 0
                || (permissionInfo.flags & PermissionInfo.FLAG_REMOVED) != 0) {
            return null;
        }

        PackageItemInfo groupInfo = permissionInfo;
        if (permissionInfo.group != null) {
            try {
                Log.d("mengge","permissionInfo.group == "+permissionInfo.group);
                groupInfo = context.getPackageManager().getPermissionGroupInfo(
                        permissionInfo.group, 0);//根据权限信息获得权限组名继而获得权限组信息
            } catch (PackageManager.NameNotFoundException e) {
                /* ignore */
            }
        }

        List<PermissionInfo> permissionInfos = null;
        if (groupInfo instanceof PermissionGroupInfo) {
            try {
                Log.d("mengge","groupInfo.name == "+groupInfo.name);
                permissionInfos = context.getPackageManager().queryPermissionsByGroup(
                        groupInfo.name, 0);//根据组名拿到组里面的权限信息集合
            } catch (PackageManager.NameNotFoundException e) {
                /* ignore */
            }
        }

        return create(context, packageInfo, groupInfo, permissionInfos,
                Process.myUserHandle());
    }

    public static AppPermissionGroup create(Context context, PackageInfo packageInfo,
            PackageItemInfo groupInfo, List<PermissionInfo> permissionInfos,
            UserHandle userHandle) {

        AppPermissionGroup group = new AppPermissionGroup(context, packageInfo, groupInfo.name,
                groupInfo.packageName, groupInfo.loadLabel(context.getPackageManager()),
                loadGroupDescription(context, groupInfo), groupInfo.packageName, groupInfo.icon,
                userHandle);

        if (groupInfo instanceof PermissionInfo) {
            permissionInfos = new ArrayList<>();
            permissionInfos.add((PermissionInfo) groupInfo);
        }

        if (permissionInfos == null || permissionInfos.isEmpty()) {
            return null;
        }

        final int permissionCount = packageInfo.requestedPermissions.length;
        for (int i = 0; i < permissionCount; i++) {
            String requestedPermission = packageInfo.requestedPermissions[i];

            PermissionInfo requestedPermissionInfo = null;

            for (PermissionInfo permissionInfo : permissionInfos) {
                if (requestedPermission.equals(permissionInfo.name)) {
                    requestedPermissionInfo = permissionInfo;
                    break;
                }
            }

            if (requestedPermissionInfo == null) {
                continue;
            }

            // Collect only runtime permissions.
            if (requestedPermissionInfo.protectionLevel != PermissionInfo.PROTECTION_DANGEROUS) {
                continue;
            }

            // Don't allow toggling non-platform permission groups for legacy apps via app ops.
            /// M: CTA requirement - permission control @{
            if (packageInfo.applicationInfo.targetSdkVersion <= Build.VERSION_CODES.LOLLIPOP_MR1
                    && !CtaUtils.isPlatformPermissionGroup(groupInfo.packageName, groupInfo.name)) {
                continue;
            }
            ///@}

            final boolean granted = (packageInfo.requestedPermissionsFlags[i]
                    & PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0;

            /// M: CTA requirement - permission control @{
            final String appOp = CtaUtils.isPlatformPermission(requestedPermissionInfo.packageName,
                    requestedPermissionInfo.name)
                    ? AppOpsManager.permissionToOp(requestedPermissionInfo.name) : null;
            ///@}

            final boolean appOpAllowed = appOp != null
                    && context.getSystemService(AppOpsManager.class).checkOpNoThrow(appOp,
                    packageInfo.applicationInfo.uid, packageInfo.packageName)
                    == AppOpsManager.MODE_ALLOWED;

            final int flags = context.getPackageManager().getPermissionFlags(
                    requestedPermission, packageInfo.packageName, userHandle);

            Permission permission = new Permission(requestedPermission, granted,
                    appOp, appOpAllowed, flags);
            group.addPermission(permission);
        }

        return group;
    }

    private static CharSequence loadGroupDescription(Context context, PackageItemInfo group) {
        CharSequence description = null;
        if (group instanceof PermissionGroupInfo) {
            description = ((PermissionGroupInfo) group).loadDescription(
                    context.getPackageManager());
        } else if (group instanceof PermissionInfo) {
            description = ((PermissionInfo) group).loadDescription(
                    context.getPackageManager());
        }

        if (description == null || description.length() <= 0) {
            description = context.getString(R.string.default_permission_description);
        }

        return description;
    }

    private AppPermissionGroup(Context context, PackageInfo packageInfo, String name,
            String declaringPackage, CharSequence label, CharSequence description,
            String iconPkg, int iconResId, UserHandle userHandle) {
        mContext = context;
        mUserHandle = userHandle;
        mPackageManager = mContext.getPackageManager();
        mPackageInfo = packageInfo;
        mAppSupportsRuntimePermissions = packageInfo.applicationInfo
                .targetSdkVersion > Build.VERSION_CODES.LOLLIPOP_MR1;
        mAppOps = context.getSystemService(AppOpsManager.class);
        mActivityManager = context.getSystemService(ActivityManager.class);
        mDeclaringPackage = declaringPackage;
        mName = name;
        mLabel = label;
        mDescription = description;
        if (iconResId != 0) {
            mIconPkg = iconPkg;
            mIconResId = iconResId;
        } else {
            mIconPkg = context.getPackageName();
            mIconResId = R.drawable.ic_perm_device_info;
        }
    }

    public boolean hasRuntimePermission() {
        return mAppSupportsRuntimePermissions;
    }

    public boolean isReviewRequired() {
        /// M: CTA requirement - review UI for all apps  @{
        if (mAppSupportsRuntimePermissions &&
                !CtaUtils.isCtaSupported()) {
            return false;
        }
        ///@}
        final int permissionCount = mPermissions.size();
        for (int i = 0; i < permissionCount; i++) {
            Permission permission = mPermissions.valueAt(i);
            if (permission.isReviewRequired()) {
                return true;
            }
        }
        return false;
    }

    public void resetReviewRequired() {
        final int permissionCount = mPermissions.size();
        for (int i = 0; i < permissionCount; i++) {
            Permission permission = mPermissions.valueAt(i);
            if (permission.isReviewRequired()) {
                permission.resetReviewRequired();
                mPackageManager.updatePermissionFlags(permission.getName(),
                        mPackageInfo.packageName,
                        PackageManager.FLAG_PERMISSION_REVIEW_REQUIRED,
                        0, mUserHandle);
            }
        }
    }

    public boolean hasGrantedByDefaultPermission() {
        final int permissionCount = mPermissions.size();
        for (int i = 0; i < permissionCount; i++) {
            Permission permission = mPermissions.valueAt(i);
            if (permission.isGrantedByDefault()) {
                return true;
            }
        }
        return false;
    }

    public PackageInfo getApp() {
        return mPackageInfo;
    }

    public String getName() {
        return mName;
    }

    public String getDeclaringPackage() {
        return mDeclaringPackage;
    }

    public String getIconPkg() {
        return mIconPkg;
    }

    public int getIconResId() {
        return mIconResId;
    }

    public CharSequence getLabel() {
        return mLabel;
    }

    public CharSequence getDescription() {
        return mDescription;
    }

    public int getUserId() {
        return mUserHandle.getIdentifier();
    }

    public boolean hasPermission(String permission) {
        return mPermissions.get(permission) != null;
    }

    public boolean areRuntimePermissionsGranted() {
        return areRuntimePermissionsGranted(null);
    }

    public boolean areRuntimePermissionsGranted(String[] filterPermissions) {
        if (LocationUtils.isLocationGroupAndProvider(mName, mPackageInfo.packageName)) {
            return LocationUtils.isLocationEnabled(mContext);
        }
        final int permissionCount = mPermissions.size();
        for (int i = 0; i < permissionCount; i++) {
            Permission permission = mPermissions.valueAt(i);
            if (filterPermissions != null
                    && !ArrayUtils.contains(filterPermissions, permission.getName())) {
                continue;
            }
            if (mAppSupportsRuntimePermissions) {
                if (permission.isGranted()) {
                    return true;
                }
            } else if (permission.isGranted() && (permission.getAppOp() == null
                    || permission.isAppOpAllowed())) {
                return true;
            }
        }
        return false;
    }

    public boolean grantRuntimePermissions(boolean fixedByTheUser) {
        return grantRuntimePermissions(fixedByTheUser, null);
    }

    public boolean grantRuntimePermissions(boolean fixedByTheUser, String[] filterPermissions) {
        final int uid = mPackageInfo.applicationInfo.uid;

        // We toggle permissions only to apps that support runtime
        // permissions, otherwise we toggle the app op corresponding
        // to the permission if the permission is granted to the app.
        for (Permission permission : mPermissions.values()) {
            if (filterPermissions != null
                    && !ArrayUtils.contains(filterPermissions, permission.getName())) {
                Log.i(TAG,"grantRuntimePermissions 111");
                continue;
            }

            if (mAppSupportsRuntimePermissions) {
                // Do not touch permissions fixed by the system.
                if (permission.isSystemFixed()) {
					Log.i(TAG,"grantRuntimePermissions 222");
					Toast.makeText((Activity)mContext, R.string.prize_forbid_modify_system_permission, Toast.LENGTH_SHORT).show();
                    return false;
                }

                // Ensure the permission app op enabled before the permission grant.
                if (permission.hasAppOp() && !permission.isAppOpAllowed()) {
                    permission.setAppOpAllowed(true);
                    mAppOps.setUidMode(permission.getAppOp(), uid, AppOpsManager.MODE_ALLOWED);
                }

                // Grant the permission if needed.
                if (!permission.isGranted()) {
                    permission.setGranted(true);
					Log.i(TAG,"grantRuntimePermissions 333");
                    mPackageManager.grantRuntimePermission(mPackageInfo.packageName,
                            permission.getName(), mUserHandle);
                }

				Log.i(TAG,"grantRuntimePermissions 444 fixedByTheUser = " + fixedByTheUser);
                // Update the permission flags.
                if (!fixedByTheUser) {
                    // Now the apps can ask for the permission as the user
                    // no longer has it fixed in a denied state.
                    if (permission.isUserFixed() || permission.isUserSet()) {
                        permission.setUserFixed(false);
                        permission.setUserSet(true);
    					mPackageManager.updatePermissionFlags(permission.getName(),
    							mPackageInfo.packageName,
    							PackageManager.FLAG_PERMISSION_USER_SET
    									| PackageManager.FLAG_PERMISSION_USER_FIXED,
    							PackageManager.FLAG_PERMISSION_USER_FIXED,
    							mUserHandle);
                    }
                }else{
					if (permission.isUserSet() || !permission.isUserFixed()) {
						permission.setUserSet(false);
						permission.setUserFixed(true);
						mPackageManager.updatePermissionFlags(permission.getName(),
								mPackageInfo.packageName,
								PackageManager.FLAG_PERMISSION_USER_SET
										| PackageManager.FLAG_PERMISSION_USER_FIXED,
								PackageManager.FLAG_PERMISSION_USER_FIXED,
								mUserHandle);
					}
                }
            } else {
                // Legacy apps cannot have a not granted permission but just in case.
                if (!permission.isGranted()) {
                    continue;
                }

                int killUid = -1;
                int mask = 0;

                // If the permissions has no corresponding app op, then it is a
                // third-party one and we do not offer toggling of such permissions.
                if (permission.hasAppOp()) {
                    if (!permission.isAppOpAllowed()) {
                        permission.setAppOpAllowed(true);
                        // Enable the app op.
                        mAppOps.setUidMode(permission.getAppOp(), uid, AppOpsManager.MODE_ALLOWED);

                        // Legacy apps do not know that they have to retry access to a
                        // resource due to changes in runtime permissions (app ops in this
                        // case). Therefore, we restart them on app op change, so they
                        // can pick up the change.
                        killUid = uid;
                    }

                    // Mark that the permission should not be be granted on upgrade
                    // when the app begins supporting runtime permissions.
                    if (permission.shouldRevokeOnUpgrade()) {
                        permission.setRevokeOnUpgrade(false);
                        mask |= PackageManager.FLAG_PERMISSION_REVOKE_ON_UPGRADE;
                    }
                }

                if (mask != 0) {
                    mPackageManager.updatePermissionFlags(permission.getName(),
                            mPackageInfo.packageName, mask, 0, mUserHandle);
                }

                if (killUid != -1) {
                    mActivityManager.killUid(uid, KILL_REASON_APP_OP_CHANGE);
                }
            }
        }

        return true;
    }

    public boolean revokeRuntimePermissions(boolean fixedByTheUser) {
        return revokeRuntimePermissions(fixedByTheUser, null);
    }

    public boolean revokeRuntimePermissions(boolean fixedByTheUser, String[] filterPermissions) {
        final int uid = mPackageInfo.applicationInfo.uid;

        // We toggle permissions only to apps that support runtime
        // permissions, otherwise we toggle the app op corresponding
        // to the permission if the permission is granted to the app.
        for (Permission permission : mPermissions.values()) {
            if (filterPermissions != null
                    && !ArrayUtils.contains(filterPermissions, permission.getName())) {
                Log.i(TAG,"revokeRuntimePermissions 111");
                continue;
            }

            if (mAppSupportsRuntimePermissions) {
                // Do not touch permissions fixed by the system.
                if (permission.isSystemFixed()) {
					Log.i(TAG,"revokeRuntimePermissions 222");
					Toast.makeText((Activity)mContext, R.string.prize_forbid_modify_system_permission, Toast.LENGTH_SHORT).show();
                    return false;
                }

                // Revoke the permission if needed.
                if (permission.isGranted()) {
                    permission.setGranted(false);
					Log.i(TAG,"revokeRuntimePermissions mPackageInfo.packageName = " + mPackageInfo.packageName + " permission.getName() = " + permission.getName());
                    mPackageManager.revokeRuntimePermission(mPackageInfo.packageName,
                            permission.getName(), mUserHandle);
                }

                // Update the permission flags.
				Log.i(TAG,"revokeRuntimePermissions fixedByTheUser = " + fixedByTheUser);
                if (fixedByTheUser) {
                    // Take a note that the user fixed the permission.
                    if (permission.isUserSet() || !permission.isUserFixed()) {
                        permission.setUserSet(false);
                        permission.setUserFixed(true);
                        mPackageManager.updatePermissionFlags(permission.getName(),
                                mPackageInfo.packageName,
                                PackageManager.FLAG_PERMISSION_USER_SET
                                        | PackageManager.FLAG_PERMISSION_USER_FIXED,
                                PackageManager.FLAG_PERMISSION_USER_FIXED,
                                mUserHandle);
                    }
                } else {
                    if (permission.isUserFixed() || permission.isUserSet()) {
                        permission.setUserFixed(false);
                        permission.setUserSet(true);
                        mPackageManager.updatePermissionFlags(permission.getName(),
                                mPackageInfo.packageName,
                                PackageManager.FLAG_PERMISSION_USER_FIXED
                                        | PackageManager.FLAG_PERMISSION_USER_SET,
                                0, mUserHandle);
                    }
                }
            } else {
                // Legacy apps cannot have a non-granted permission but just in case.
                if (!permission.isGranted()) {
                    continue;
                }

                int mask = 0;
                int flags = 0;
                int killUid = -1;

                // If the permission has no corresponding app op, then it is a
                // third-party one and we do not offer toggling of such permissions.
                if (permission.hasAppOp()) {
                    if (permission.isAppOpAllowed()) {
                        permission.setAppOpAllowed(false);
                        // Disable the app op.
                        mAppOps.setUidMode(permission.getAppOp(), uid, AppOpsManager.MODE_IGNORED);

                        // Disabling an app op may put the app in a situation in which it
                        // has a handle to state it shouldn't have, so we have to kill the
                        // app. This matches the revoke runtime permission behavior.
                        killUid = uid;
                    }

                    // Mark that the permission should not be granted on upgrade
                    // when the app begins supporting runtime permissions.
                    if (!permission.shouldRevokeOnUpgrade()) {
                        permission.setRevokeOnUpgrade(true);
                        mask |= PackageManager.FLAG_PERMISSION_REVOKE_ON_UPGRADE;
                        flags |= PackageManager.FLAG_PERMISSION_REVOKE_ON_UPGRADE;
                    }
                }

                if (mask != 0) {
                    mPackageManager.updatePermissionFlags(permission.getName(),
                            mPackageInfo.packageName, mask, flags, mUserHandle);
                }

                if (killUid != -1) {
                    mActivityManager.killUid(uid, KILL_REASON_APP_OP_CHANGE);
                }
            }
        }
        return true;
    }

    public void setPolicyFixed() {
        final int permissionCount = mPermissions.size();
        for (int i = 0; i < permissionCount; i++) {
            Permission permission = mPermissions.valueAt(i);
            permission.setPolicyFixed(true);
            mPackageManager.updatePermissionFlags(permission.getName(),
                    mPackageInfo.packageName,
                    PackageManager.FLAG_PERMISSION_POLICY_FIXED,
                    PackageManager.FLAG_PERMISSION_POLICY_FIXED,
                    mUserHandle);
        }
    }

    public List<Permission> getPermissions() {
        return new ArrayList<>(mPermissions.values());
    }

    public int getFlags() {
        int flags = 0;
        final int permissionCount = mPermissions.size();
        for (int i = 0; i < permissionCount; i++) {
            Permission permission = mPermissions.valueAt(i);
            flags |= permission.getFlags();
        }
        return flags;
    }

    public boolean isUserFixed() {
        final int permissionCount = mPermissions.size();
        for (int i = 0; i < permissionCount; i++) {
            Permission permission = mPermissions.valueAt(i);
            if (!permission.isUserFixed()) {
                return false;
            }
        }
        return true;
    }

    public boolean isPolicyFixed() {
        final int permissionCount = mPermissions.size();
        for (int i = 0; i < permissionCount; i++) {
            Permission permission = mPermissions.valueAt(i);
            if (permission.isPolicyFixed()) {
                return true;
            }
        }
        return false;
    }

    public boolean isUserSet() {
        final int permissionCount = mPermissions.size();
        for (int i = 0; i < permissionCount; i++) {
            Permission permission = mPermissions.valueAt(i);
            if (!permission.isUserSet()) {
                return false;
            }
        }
        return true;
    }

    public boolean isSystemFixed() {
        final int permissionCount = mPermissions.size();
        for (int i = 0; i < permissionCount; i++) {
            Permission permission = mPermissions.valueAt(i);
            if (permission.isSystemFixed()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int compareTo(AppPermissionGroup another) {
        final int result = mLabel.toString().compareTo(another.mLabel.toString());
        if (result == 0) {
            // Unbadged before badged.
            return mPackageInfo.applicationInfo.uid
                    - another.mPackageInfo.applicationInfo.uid;
        }
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj == null) {
            return false;
        }

        if (getClass() != obj.getClass()) {
            return false;
        }

        AppPermissionGroup other = (AppPermissionGroup) obj;

        if (mName == null) {
            if (other.mName != null) {
                return false;
            }
        } else if (!mName.equals(other.mName)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return mName != null ? mName.hashCode() : 0;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(getClass().getSimpleName());
        builder.append("{name=").append(mName);
        if (!mPermissions.isEmpty()) {
            builder.append(", <has permissions>}");
        } else {
            builder.append('}');
        }
        return builder.toString();
    }

    private void addPermission(Permission permission) {
        Log.d("mengge","addPermission  permission.getName() == "+permission.getName());
        mPermissions.put(permission.getName(), permission);
    }

    /// M: CTA requirement - permission control  @{
    public Permission getPermission(String permissionName) {
        return mPermissions.get(permissionName);
    }
    ///@}
}
