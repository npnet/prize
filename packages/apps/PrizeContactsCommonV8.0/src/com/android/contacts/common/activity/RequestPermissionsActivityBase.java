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

package com.android.contacts.common.activity;

import com.android.contacts.common.R;
import com.android.contacts.common.model.AccountTypeManager;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Trace;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.widget.Toast;

import com.mediatek.contacts.util.Log;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Activity that asks the user for all {@link #getDesiredPermissions} if any of
 * {@link #getRequiredPermissions} are missing.
 *
 * NOTE: As a result of b/22095159, this can behave oddly in the case where the final permission
 * you are requesting causes an application restart.
 */
public abstract class RequestPermissionsActivityBase extends Activity
        implements ActivityCompat.OnRequestPermissionsResultCallback {

    private static final String TAG = "RequestPermissionsActivityBase";

    public static final String PREVIOUS_ACTIVITY_INTENT = "previous_intent";

    /** Whether the permissions activity was already started. */
    protected static final String STARTED_PERMISSIONS_ACTIVITY = "started_permissions_activity";

    protected static final String EXTRA_IS_CALLER_SELF = "is_caller_self";

    private static final int PERMISSIONS_REQUEST_ALL_PERMISSIONS = 1;

    /**
     * @return list of permissions that are needed in order for {@link #PREVIOUS_ACTIVITY_INTENT} to
     * operate. You only need to return a single permission per permission group you care about.
     */
    protected abstract String[] getRequiredPermissions();

    /**
     * @return list of permissions that would be useful for {@link #PREVIOUS_ACTIVITY_INTENT} to
     * operate. You only need to return a single permission per permission group you care about.
     */
    protected abstract String[] getDesiredPermissions();

    protected Intent mPreviousActivityIntent;

    /** If true then start the target activity "for result" after permissions are granted. */
    protected boolean mIsCallerSelf;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPreviousActivityIntent = (Intent) getIntent().getExtras().get(PREVIOUS_ACTIVITY_INTENT);
        mIsCallerSelf = getIntent().getBooleanExtra(EXTRA_IS_CALLER_SELF, false);

        // Only start a requestPermissions() flow when first starting this activity the first time.
        // The process is likely to be restarted during the permission flow (necessary to enable
        // permissions) so this is important to track.
        if (savedInstanceState == null) {
            requestPermissions();
        }
    }

    /**
     * If any permissions the Contacts app needs are missing, open an Activity
     * to prompt the user for these permissions. Moreover, finish the current activity.
     *
     * This is designed to be called inside {@link android.app.Activity#onCreate}
     */
    protected static boolean startPermissionActivity(Activity activity,
            String[] requiredPermissions, Class<?> newActivityClass) {
        return startPermissionActivity(activity, requiredPermissions, /* isCallerSelf */ false,
                newActivityClass);
    }

    protected static boolean startPermissionActivity(Activity activity,
            String[] requiredPermissions, boolean isCallerSelf, Class<?> newActivityClass) {
        if (!hasPermissions(activity, requiredPermissions)) {
            final Intent intent = new Intent(activity,  newActivityClass);
            activity.getIntent().putExtra(STARTED_PERMISSIONS_ACTIVITY, true);
            intent.putExtra(PREVIOUS_ACTIVITY_INTENT, activity.getIntent());
            intent.putExtra(EXTRA_IS_CALLER_SELF, isCallerSelf);
            activity.startActivity(intent);
            activity.finish();
            return true;
        }

        // Account type initialization must be delayed until the Contacts permission group
        // has been granted (since GET_ACCOUNTS) falls under that groups.  Previously it
        // was initialized in ContactApplication which would cause problems as
        // AccountManager.getAccounts would return an empty array. See b/22690336
        AccountTypeManager.getInstance(activity);

        return false;
    }

    protected boolean isAllGranted(String permissions[], int[] grantResult) {
        for (int i = 0; i < permissions.length; i++) {
            if (grantResult[i] != PackageManager.PERMISSION_GRANTED
                    && isPermissionRequired(permissions[i])) {
                return false;
            }
        }
        return true;
    }

    private boolean isPermissionRequired(String p) {
        return Arrays.asList(getRequiredPermissions()).contains(p);
    }

    private void requestPermissions() {
        Trace.beginSection("requestPermissions");
        try {
            // Construct a list of missing permissions
            final ArrayList<String> unsatisfiedPermissions = new ArrayList<>();
            for (String permission : getDesiredPermissions()) {
                if (checkSelfPermission(permission)
                        != PackageManager.PERMISSION_GRANTED) {
                    unsatisfiedPermissions.add(permission);
                }
            }
            if (unsatisfiedPermissions.size() == 0) {
                /**
                 * M:[ALPS03375762]
                 * ori code:
                 * throw new RuntimeException("Request permission activity was called even"
                 *        + " though all permissions are satisfied.");
                 * mtk modify: @{
                 */
                Log.e(TAG, "[requestPermissions] activity=" + this.getClass().getSimpleName() +
                        ", getDesiredPermissions()=" + Arrays.toString(getDesiredPermissions()));
                finish();
                return;
                /** @} */
            }
            ActivityCompat.requestPermissions(
                    this,
                    unsatisfiedPermissions.toArray(new String[unsatisfiedPermissions.size()]),
                    PERMISSIONS_REQUEST_ALL_PERMISSIONS);
        } finally {
            Trace.endSection();
        }
    }

    @Override
    public int checkSelfPermission(String permission) {
        return ContextCompat.checkSelfPermission(this, permission);
    }

    protected static boolean hasPermissions(Context context, String[] permissions) {
        Trace.beginSection("hasPermission");
        try {
            for (String permission : permissions) {
                if (ContextCompat.checkSelfPermission(context, permission)
                        != PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "[hasPermission] no permission:" + permission);
                    return false;
                }
            }
            return true;
        } finally {
            Trace.endSection();
        }
    }
}
