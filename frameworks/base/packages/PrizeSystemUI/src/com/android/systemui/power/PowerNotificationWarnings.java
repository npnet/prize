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

package com.android.systemui.power;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Slog;

import com.android.systemui.R;
import com.android.systemui.SystemUI;
import com.android.systemui.statusbar.phone.PhoneStatusBar;
import com.android.systemui.statusbar.phone.SystemUIDialog;

import java.io.PrintWriter;
import java.text.NumberFormat;

public class PowerNotificationWarnings implements PowerUI.WarningsUI {
    private static final String TAG = PowerUI.TAG + ".Notification";
    private static final boolean DEBUG = PowerUI.DEBUG;

    private static final String TAG_NOTIFICATION = "low_battery";
    /*PRIZE-dismiss the low power icon liyao-2015-07-09 start*/
    public static final int ID_NOTIFICATION = 100;
    /*PRIZE-dismiss the low power icon liyao-2015-07-09 end*/
    private static final int SHOWING_NOTHING = 0;
    private static final int SHOWING_WARNING = 1;
    private static final int SHOWING_INVALID_CHARGER = 3;
    private static final String[] SHOWING_STRINGS = {
        "SHOWING_NOTHING",
        "SHOWING_WARNING",
        "SHOWING_SAVER",
        "SHOWING_INVALID_CHARGER",
    };

    private static final String ACTION_SHOW_BATTERY_SETTINGS = "PNW.batterySettings";
    private static final String ACTION_START_SAVER = "PNW.startSaver";
    private static final String ACTION_DISMISSED_WARNING = "PNW.dismissedWarning";

    private static final AudioAttributes AUDIO_ATTRIBUTES = new AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .build();

    private final Context mContext;
    private final NotificationManager mNoMan;
    private final PowerManager mPowerMan;
    private final Handler mHandler = new Handler();
    private final Receiver mReceiver = new Receiver();
    private final Intent mOpenBatterySettings = settings(Intent.ACTION_POWER_USAGE_SUMMARY);

    private int mBatteryLevel;
    private int mBucket;
    private long mScreenOffTime;
    private int mShowing;

    private long mBucketDroppedNegativeTimeMs;

    private boolean mWarning;
    private boolean mPlaySound;
    private boolean mInvalidCharger;
    private SystemUIDialog mSaverConfirmation;

    public PowerNotificationWarnings(Context context, PhoneStatusBar phoneStatusBar) {
        mContext = context;
        mNoMan = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        mPowerMan = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        mReceiver.init();
    }

    @Override
    public void dump(PrintWriter pw) {
        pw.print("mWarning="); pw.println(mWarning);
        pw.print("mPlaySound="); pw.println(mPlaySound);
        pw.print("mInvalidCharger="); pw.println(mInvalidCharger);
        pw.print("mShowing="); pw.println(SHOWING_STRINGS[mShowing]);
        pw.print("mSaverConfirmation="); pw.println(mSaverConfirmation != null ? "not null" : null);
    }

    @Override
    public void update(int batteryLevel, int bucket, long screenOffTime) {
        mBatteryLevel = batteryLevel;
        if (bucket >= 0) {
            mBucketDroppedNegativeTimeMs = 0;
        } else if (bucket < mBucket) {
            mBucketDroppedNegativeTimeMs = System.currentTimeMillis();
        }
        mBucket = bucket;
        mScreenOffTime = screenOffTime;
    }

    private void updateNotification() {
        if (DEBUG) Slog.d(TAG, "updateNotification mWarning=" + mWarning + " mPlaySound="
                + mPlaySound + " mInvalidCharger=" + mInvalidCharger);
        if (mInvalidCharger) {
            showInvalidChargerNotification();
            mShowing = SHOWING_INVALID_CHARGER;
        } else if (mWarning) {
            showWarningNotification();
            mShowing = SHOWING_WARNING;
        } else {
            mNoMan.cancelAsUser(TAG_NOTIFICATION, R.id.notification_power, UserHandle.ALL);
            mShowing = SHOWING_NOTHING;
        }
    }

    private void showInvalidChargerNotification() {
        final Notification.Builder nb = new Notification.Builder(mContext)
                /*PRIZE-power notification icon- liufan-2016-07-14-start*/
                .setSmallIcon(R.drawable.ic_power_low_prize)
                /*PRIZE-power notification icon- liufan-2016-07-14-end*/
                .setWhen(0)
                .setShowWhen(false)
                .setOngoing(true)
                .setContentTitle(mContext.getString(R.string.invalid_charger_title))
                .setContentText(mContext.getString(R.string.invalid_charger_text))
                .setPriority(Notification.PRIORITY_MAX)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setColor(mContext.getColor(
                        com.android.internal.R.color.system_notification_accent_color));
        SystemUI.overrideNotificationAppName(mContext, nb);
        final Notification n = nb.build();
        /*PRIZE-retain power notification icon- liufan-2016-07-14-start*/
        n.flags |= Notification.FLAG_KEEP_NOTIFICATION_ICON;
        /*PRIZE-retain power notification icon- liufan-2016-07-14-end*/
        mNoMan.notifyAsUser(TAG_NOTIFICATION, R.id.notification_power, n, UserHandle.ALL);
    }

    private void showWarningNotification() {
        final int textRes = R.string.battery_low_percent_format;
        final String percentage = NumberFormat.getPercentInstance().format((double) mBatteryLevel / 100.0);
        final Notification.Builder nb = new Notification.Builder(mContext)
                /*PRIZE-power notification icon- liufan-2016-07-14-start*/
                .setSmallIcon(R.drawable.ic_power_low_prize)
                /*PRIZE-power notification icon- liufan-2016-07-14-end*/
                // Bump the notification when the bucket dropped.
                .setWhen(mBucketDroppedNegativeTimeMs)
                .setShowWhen(false)
                .setContentTitle(mContext.getString(R.string.battery_low_title))
                .setContentText(mContext.getString(textRes, percentage))
                .setOnlyAlertOnce(true)
                .setDeleteIntent(pendingBroadcast(ACTION_DISMISSED_WARNING))
                /*PRIZE cancel set Priority Notification.PRIORITY_DEFAULT 1601 liyao 2015-06-19 start */
                .setTicker(mContext.getString(R.string.battery_low_title)+mContext.getString(textRes, percentage))
                //.setPriority(Notification.PRIORITY_HIGH)
                /*PRIZE cancel set Priority Notification.PRIORITY_DEFAULT 1601 liyao 2015-06-19 end */
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setColor(mContext.getColor(
                        com.android.internal.R.color.battery_saver_mode_color));
        if (hasBatterySettings()) {
            nb.setContentIntent(pendingBroadcast(ACTION_SHOW_BATTERY_SETTINGS));
        }
        /*PRIZE-cancel the action- liufan-2016-12-01-start*/
        /*nb.addAction(0,
                mContext.getString(R.string.battery_saver_start_action),
                pendingBroadcast(ACTION_START_SAVER));*/
        /*PRIZE-cancel the action- liufan-2016-12-01-end*/
        if (mPlaySound) {
            attachLowBatterySound(nb);
            /*PRIZE play sound when low power(Bug 1009) 2015-06-01 start */
            //mPlaySound = false;
           /*PRIZE play sound when low power(Bug 1009) 2015-06-01 end */
        }
        /*PRIZE-retain power notification icon- liufan-2016-07-14-start*/
        final Notification n = nb.build();
        n.flags |= Notification.FLAG_KEEP_NOTIFICATION_ICON;
        SystemUI.overrideNotificationAppName(mContext, nb);
        mNoMan.notifyAsUser(TAG_NOTIFICATION, R.id.notification_power, n, UserHandle.ALL);
        /*PRIZE-retain power notification icon- liufan-2016-07-14-end*/

    }

    private PendingIntent pendingActivity(Intent intent) {
        return PendingIntent.getActivityAsUser(mContext,
                0, intent, 0, null, UserHandle.CURRENT);
    }

    private PendingIntent pendingBroadcast(String action) {
        return PendingIntent.getBroadcastAsUser(mContext,
                0, new Intent(action), 0, UserHandle.CURRENT);
    }

    private static Intent settings(String action) {
        return new Intent(action).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                | Intent.FLAG_ACTIVITY_NO_HISTORY
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    }

    @Override
    public boolean isInvalidChargerWarningShowing() {
        return mInvalidCharger;
    }

    @Override
    public void updateLowBatteryWarning() {
        updateNotification();
    }

    @Override
    public void dismissLowBatteryWarning() {
        if (DEBUG) Slog.d(TAG, "dismissing low battery warning: level=" + mBatteryLevel);
        dismissLowBatteryNotification();
    }

    private void dismissLowBatteryNotification() {
        if (mWarning) Slog.i(TAG, "dismissing low battery notification");
        mWarning = false;
        updateNotification();
    }

    private boolean hasBatterySettings() {
        return mOpenBatterySettings.resolveActivity(mContext.getPackageManager()) != null;
    }

    @Override
    public void showLowBatteryWarning(boolean playSound) {
        Slog.i(TAG,
                "show low battery warning: level=" + mBatteryLevel
                + " [" + mBucket + "] playSound=" + playSound);
        mPlaySound = playSound;
        mWarning = true;
        updateNotification();
    }

    private void attachLowBatterySound(Notification.Builder b) {
        final ContentResolver cr = mContext.getContentResolver();

        final int silenceAfter = Settings.Global.getInt(cr,
                Settings.Global.LOW_BATTERY_SOUND_TIMEOUT, 0);
        final long offTime = SystemClock.elapsedRealtime() - mScreenOffTime;
        if (silenceAfter > 0
                && mScreenOffTime > 0
                && offTime > silenceAfter) {
            Slog.i(TAG, "screen off too long (" + offTime + "ms, limit " + silenceAfter
                    + "ms): not waking up the user with low battery sound");
            return;
        }

        if (DEBUG) {
            Slog.d(TAG, "playing low battery sound. pick-a-doop!"); // WOMP-WOMP is deprecated
        }

        if (Settings.Global.getInt(cr, Settings.Global.POWER_SOUNDS_ENABLED, 1) == 1) {
            final String soundPath = Settings.Global.getString(cr,
                    Settings.Global.LOW_BATTERY_SOUND);
            if (soundPath != null) {
                final Uri soundUri = Uri.parse("file://" + soundPath);
                if (soundUri != null) {
                    b.setSound(soundUri, AUDIO_ATTRIBUTES);
                    if (DEBUG) Slog.d(TAG, "playing sound " + soundUri);
                }
            }
        }
    }

    @Override
    public void dismissInvalidChargerWarning() {
        dismissInvalidChargerNotification();
    }

    private void dismissInvalidChargerNotification() {
        if (mInvalidCharger) Slog.i(TAG, "dismissing invalid charger notification");
        mInvalidCharger = false;
        updateNotification();
    }

    @Override
    public void showInvalidChargerWarning() {
        mInvalidCharger = true;
        updateNotification();
    }

    @Override
    public void userSwitched() {
        updateNotification();
    }

    private void showStartSaverConfirmation() {
        if (mSaverConfirmation != null) return;
        final SystemUIDialog d = new SystemUIDialog(mContext);
        d.setTitle(R.string.battery_saver_confirmation_title);
        d.setMessage(com.android.internal.R.string.battery_saver_description);
        d.setNegativeButton(android.R.string.cancel, null);
        d.setPositiveButton(R.string.battery_saver_confirmation_ok, mStartSaverMode);
        d.setShowForAllUsers(true);
        d.setOnDismissListener(new OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                mSaverConfirmation = null;
            }
        });
        d.show();
        mSaverConfirmation = d;
    }

    private void setSaverMode(boolean mode) {
        mPowerMan.setPowerSaveMode(mode);
    }

    private final class Receiver extends BroadcastReceiver {

        public void init() {
            IntentFilter filter = new IntentFilter();
            filter.addAction(ACTION_SHOW_BATTERY_SETTINGS);
            filter.addAction(ACTION_START_SAVER);
            filter.addAction(ACTION_DISMISSED_WARNING);
            mContext.registerReceiverAsUser(this, UserHandle.ALL, filter,
                    android.Manifest.permission.STATUS_BAR_SERVICE, mHandler);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            Slog.i(TAG, "Received " + action);
            if (action.equals(ACTION_SHOW_BATTERY_SETTINGS)) {
                dismissLowBatteryNotification();
                mContext.startActivityAsUser(mOpenBatterySettings, UserHandle.CURRENT);
            } else if (action.equals(ACTION_START_SAVER)) {
                dismissLowBatteryNotification();
                showStartSaverConfirmation();
            } else if (action.equals(ACTION_DISMISSED_WARNING)) {
                dismissLowBatteryWarning();
            }
        }
    }

    private final OnClickListener mStartSaverMode = new OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    setSaverMode(true);
                }
            });
        }
    };
}
