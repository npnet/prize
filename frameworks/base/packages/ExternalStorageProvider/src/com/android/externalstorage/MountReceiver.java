/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.externalstorage;

import android.content.BroadcastReceiver;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class MountReceiver extends BroadcastReceiver {
    private static final String TAG = "MountReceiver";
    @Override
    public void onReceive(Context context, Intent intent) {
        final ContentProviderClient client = context.getContentResolver()
                .acquireContentProviderClient(ExternalStorageProvider.AUTHORITY);
        if (client != null) {
        try {
            ((ExternalStorageProvider) client.getLocalContentProvider()).updateVolumes();
        } finally {
            ContentProviderClient.releaseQuietly(client);
        }
        } else {
            Log.d(TAG, "MountReceiver : onReceive client is null");
        }
    }
}
