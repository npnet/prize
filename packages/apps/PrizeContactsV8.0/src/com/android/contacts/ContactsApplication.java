/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.contacts;

import android.app.Application;
import android.app.FragmentManager;
import android.app.LoaderManager;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.provider.ContactsContract.Contacts;

import com.android.contacts.common.testing.InjectedServices;
import com.android.contacts.common.util.Constants;
import com.android.contacts.commonbind.analytics.AnalyticsUtil;

import com.android.contacts.common.testing.NeededForTesting;
import com.google.common.annotations.VisibleForTesting;

import com.mediatek.contacts.ContactsApplicationEx;
import com.mediatek.contacts.simcontact.SlotUtils;
import com.mediatek.contacts.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@NeededForTesting
public class ContactsApplication extends Application {
    private static final boolean ENABLE_LOADER_LOG = false; // Don't submit with true
    private static final boolean ENABLE_FRAGMENT_LOG = false; // Don't submit with true

    private static InjectedServices sInjectedServices;
    /**
     * Log tag for enabling/disabling StrictMode violation log.
     * To enable: adb shell setprop log.tag.ContactsStrictMode DEBUG
     */
    public static final String STRICT_MODE_TAG = "ContactsStrictMode";

    /**
     * Overrides the system services with mocks for testing.
     */
    @VisibleForTesting
    public static void injectServices(InjectedServices services) {
        sInjectedServices = services;
    }

    public static InjectedServices getInjectedServices() {
        return sInjectedServices;
    }

    @Override
    public ContentResolver getContentResolver() {
        if (sInjectedServices != null) {
            ContentResolver resolver = sInjectedServices.getContentResolver();
            if (resolver != null) {
                return resolver;
            }
        }
        return super.getContentResolver();
    }

    @Override
    public SharedPreferences getSharedPreferences(String name, int mode) {
        if (sInjectedServices != null) {
            SharedPreferences prefs = sInjectedServices.getSharedPreferences();
            if (prefs != null) {
                return prefs;
            }
        }

        return super.getSharedPreferences(name, mode);
    }

    @Override
    public Object getSystemService(String name) {
        if (sInjectedServices != null) {
            Object service = sInjectedServices.getSystemService(name);
            if (service != null) {
                return service;
            }
        }

        return super.getSystemService(name);
    }

    @Override
    public void onCreate() {
        super.onCreate();

//        if (Log.isLoggable(Constants.PERFORMANCE_TAG, Log.DEBUG)) {
//            Log.d(Constants.PERFORMANCE_TAG, "ContactsApplication.onCreate start");
//        }

        if (ENABLE_FRAGMENT_LOG) FragmentManager.enableDebugLogging(true);
        if (ENABLE_LOADER_LOG) LoaderManager.enableDebugLogging(true);

//        if (Log.isLoggable(STRICT_MODE_TAG, Log.DEBUG)) {
//            StrictMode.setThreadPolicy(
//                    new StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build());
//        }

        /// M: Set the application context to some class and clear notification. @{
        ContactsApplicationEx.onCreateEx(this);
        /// @}

        // Perform the initialization that doesn't have to finish immediately.
        // We use an async task here just to avoid creating a new thread.
        (new DelayedInitializer()).execute();

//        if (Log.isLoggable(Constants.PERFORMANCE_TAG, Log.DEBUG)) {
//            Log.d(Constants.PERFORMANCE_TAG, "ContactsApplication.onCreate finish");
//        }

        AnalyticsUtil.initialize(this);
    }

    private class DelayedInitializer extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            final Context context = ContactsApplication.this;

            // Warm up the preferences and the contacts provider.  We delay initialization
            // of the account type manager because we may not have the contacts group permission
            // (and thus not have the get accounts permission).
            PreferenceManager.getDefaultSharedPreferences(context);
            getContentResolver().getType(ContentUris.withAppendedId(Contacts.CONTENT_URI, 1));
            return null;
        }

        public void execute() {
            executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,
                    (Void[]) null);
        }
    }

    /// M: @{

    /// M: Single thread, don't simultaneously handle contacts copy-delete-import-export request.
    private final ExecutorService mSingleTaskService = Executors.newSingleThreadExecutor();

    /**
     * M: Get the ContactsApplication Instance.
     */
    public static ContactsApplication getInstance() {
        return ContactsApplicationEx.getContactsApplication();
    }

    /**
     * M: Get Application Task Sevice.
     */
    public ExecutorService getApplicationTaskService() {
        return mSingleTaskService;
    }

    /// @}
}
