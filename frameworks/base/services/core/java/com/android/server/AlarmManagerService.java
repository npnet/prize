/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.android.server;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManagerNative;
import android.app.AlarmManager;
import android.app.AppOpsManager;
import android.app.BroadcastOptions;
import android.app.IAlarmCompleteListener;
import android.app.IAlarmListener;
import android.app.IAlarmManager;
import android.app.IUidObserver;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.WorkSource;
import android.os.Build;

/// M: Uplink Traffic Shaping feature start
import com.mediatek.datashaping.IDataShapingManager;

import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.Time;
import android.text.format.DateFormat;
import android.util.ArrayMap;
import android.util.KeyValueListParser;
import android.util.Log;
import android.util.Slog;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.util.SparseLongArray;
import android.util.TimeUtils;

import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Random;
import java.util.TimeZone;
import java.util.TreeSet;

import static android.app.AlarmManager.RTC_WAKEUP;
import static android.app.AlarmManager.RTC;
import static android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP;
import static android.app.AlarmManager.ELAPSED_REALTIME;

import com.android.internal.util.LocalLog;
import com.mediatek.amplus.AlarmManagerPlus;
import com.mediatek.common.dm.DmAgent;


// Intercept wake up alarms. prize-linkh-20160318
import android.content.ComponentName;
import android.util.ArraySet;
import android.content.pm.PackageManager;
import android.content.pm.ApplicationInfo;
import com.mediatek.common.prizeoption.PrizeOption;
import android.provider.Settings;
import android.database.ContentObserver;
//end..
import android.hardware.display.DisplayManager;
import android.hardware.display.WifiDisplayStatus;

class AlarmManagerService extends SystemService {
    private static final int RTC_WAKEUP_MASK = 1 << RTC_WAKEUP;
    private static final int RTC_MASK = 1 << RTC;
    private static final int ELAPSED_REALTIME_WAKEUP_MASK = 1 << ELAPSED_REALTIME_WAKEUP;
    private static final int ELAPSED_REALTIME_MASK = 1 << ELAPSED_REALTIME;
    static final int TIME_CHANGED_MASK = 1 << 16;
    static final int IS_WAKEUP_MASK = RTC_WAKEUP_MASK|ELAPSED_REALTIME_WAKEUP_MASK;

    // Mask for testing whether a given alarm type is wakeup vs non-wakeup
    static final int TYPE_NONWAKEUP_MASK = 0x1; // low bit => non-wakeup

    static final String TAG = "AlarmManager";
    static final String ClockReceiver_TAG = "ClockReceiver";
    static boolean localLOGV = false;
    static boolean DEBUG_BATCH = localLOGV || false;
    static boolean DEBUG_VALIDATE = localLOGV || false;
    static final boolean DEBUG_ALARM_CLOCK = localLOGV || false;
    static final boolean DEBUG_LISTENER_CALLBACK = localLOGV || false;
    static final boolean RECORD_ALARMS_IN_HISTORY = true;
    static final boolean RECORD_DEVICE_IDLE_ALARMS = false;
    static final int ALARM_EVENT = 1;
    static final String TIMEZONE_PROPERTY = "persist.sys.timezone";

    /// M: Background Service Priority Adjustment @{
    private final Intent mBackgroundIntent
            = new Intent().addFlags(Intent.FLAG_FROM_BACKGROUND);
            // to do: add Intent.FLAG_WITH_BACKGROUND_PRIORITY
    /// @}
    static final IncreasingTimeOrder sIncreasingTimeOrder = new IncreasingTimeOrder();

    static final boolean WAKEUP_STATS = false;

    private static final Intent NEXT_ALARM_CLOCK_CHANGED_INTENT =
            new Intent(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED)
                    .addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);

    final LocalLog mLog = new LocalLog(TAG);

    AppOpsManager mAppOps;
    DeviceIdleController.LocalService mLocalDeviceIdleController;

    final Object mLock = new Object();

    long mNativeData;
    private static int mAlarmMode = 2; //M: use AlarmGrouping v2 default
    private static boolean mSupportAlarmGrouping = false;
    private long mNextWakeup;
    private long mNextNonWakeup;
    private long mLastWakeupSet;
    private long mLastWakeup;
    int mBroadcastRefCount = 0;
    PowerManager.WakeLock mWakeLock;
    boolean mLastWakeLockUnimportantForLogging;
    ArrayList<Alarm> mPendingNonWakeupAlarms = new ArrayList<>();
    ArrayList<InFlight> mInFlight = new ArrayList<>();
    final AlarmHandler mHandler = new AlarmHandler();
    ClockReceiver mClockReceiver;
    InteractiveStateReceiver mInteractiveStateReceiver;
    private UninstallReceiver mUninstallReceiver;
    final DeliveryTracker mDeliveryTracker = new DeliveryTracker();
    PendingIntent mTimeTickSender;
    PendingIntent mDateChangeSender;
    Random mRandom;
    boolean mInteractive = true;
    boolean mNeedRebatchForRepeatingAlarm = false;
    long mNonInteractiveStartTime;
    long mNonInteractiveTime;
    long mLastAlarmDeliveryTime;
    long mStartCurrentDelayTime;
    long mNextNonWakeupDeliveryTime;
    long mLastTimeChangeClockTime;
    long mLastTimeChangeRealtime;
    long mAllowWhileIdleMinTime;
    int mNumTimeChanged;
    /// M: For handling non-wakeup alarms while WFD is connected
    WFDStatusChangedReceiver mWFDStatusChangedReceiver;
    boolean mIsWFDConnected = false;

    /**
     * The current set of user whitelisted apps for device idle mode, meaning these are allowed
     * to freely schedule alarms.
     */
    int[] mDeviceIdleUserWhitelist = new int[0];

    /**
     * For each uid, this is the last time we dispatched an "allow while idle" alarm,
     * used to determine the earliest we can dispatch the next such alarm.
     */
    final SparseLongArray mLastAllowWhileIdleDispatch = new SparseLongArray();

    final static class IdleDispatchEntry {
        int uid;
        String pkg;
        String tag;
        String op;
        long elapsedRealtime;
        long argRealtime;
    }
    final ArrayList<IdleDispatchEntry> mAllowWhileIdleDispatches = new ArrayList();

    /**
     * Broadcast options to use for FLAG_ALLOW_WHILE_IDLE.
     */
    Bundle mIdleOptions;

    private final SparseArray<AlarmManager.AlarmClockInfo> mNextAlarmClockForUser =
            new SparseArray<>();
    private final SparseArray<AlarmManager.AlarmClockInfo> mTmpSparseAlarmClockArray =
            new SparseArray<>();
    private final SparseBooleanArray mPendingSendNextAlarmClockChangedForUser =
            new SparseBooleanArray();
    private boolean mNextAlarmClockMayChange;

    // May only use on mHandler's thread, locking not required.
    private final SparseArray<AlarmManager.AlarmClockInfo> mHandlerSparseAlarmClockArray =
            new SparseArray<>();

    /**
     * All times are in milliseconds. These constants are kept synchronized with the system
     * global Settings. Any access to this class or its fields should be done while
     * holding the AlarmManagerService.mLock lock.
     */
    private final class Constants extends ContentObserver {
        // Key names stored in the settings value.
        private static final String KEY_MIN_FUTURITY = "min_futurity";
        private static final String KEY_MIN_INTERVAL = "min_interval";
        private static final String KEY_ALLOW_WHILE_IDLE_SHORT_TIME = "allow_while_idle_short_time";
        private static final String KEY_ALLOW_WHILE_IDLE_LONG_TIME = "allow_while_idle_long_time";
        private static final String KEY_ALLOW_WHILE_IDLE_WHITELIST_DURATION
                = "allow_while_idle_whitelist_duration";
        private static final String KEY_LISTENER_TIMEOUT = "listener_timeout";

        private static final long DEFAULT_MIN_FUTURITY = 5 * 1000;
        private static final long DEFAULT_MIN_INTERVAL = 60 * 1000;
        private static final long DEFAULT_ALLOW_WHILE_IDLE_SHORT_TIME = DEFAULT_MIN_FUTURITY;
        private static final long DEFAULT_ALLOW_WHILE_IDLE_LONG_TIME = 9*60*1000;
        private static final long DEFAULT_ALLOW_WHILE_IDLE_WHITELIST_DURATION = 10*1000;

        private static final long DEFAULT_LISTENER_TIMEOUT = 5 * 1000;

        // Minimum futurity of a new alarm
        public long MIN_FUTURITY = DEFAULT_MIN_FUTURITY;

        // Minimum alarm recurrence interval
        public long MIN_INTERVAL = DEFAULT_MIN_INTERVAL;

        // Minimum time between ALLOW_WHILE_IDLE alarms when system is not idle.
        public long ALLOW_WHILE_IDLE_SHORT_TIME = DEFAULT_ALLOW_WHILE_IDLE_SHORT_TIME;

        // Minimum time between ALLOW_WHILE_IDLE alarms when system is idling.
        public long ALLOW_WHILE_IDLE_LONG_TIME = DEFAULT_ALLOW_WHILE_IDLE_LONG_TIME;

        // BroadcastOptions.setTemporaryAppWhitelistDuration() to use for FLAG_ALLOW_WHILE_IDLE.
        public long ALLOW_WHILE_IDLE_WHITELIST_DURATION
                = DEFAULT_ALLOW_WHILE_IDLE_WHITELIST_DURATION;

        // Direct alarm listener callback timeout
        public long LISTENER_TIMEOUT = DEFAULT_LISTENER_TIMEOUT;

        private ContentResolver mResolver;
        private final KeyValueListParser mParser = new KeyValueListParser(',');
        private long mLastAllowWhileIdleWhitelistDuration = -1;

        public Constants(Handler handler) {
            super(handler);
            updateAllowWhileIdleMinTimeLocked();
            updateAllowWhileIdleWhitelistDurationLocked();
        }

        public void start(ContentResolver resolver) {
            mResolver = resolver;
            mResolver.registerContentObserver(Settings.Global.getUriFor(
                    Settings.Global.ALARM_MANAGER_CONSTANTS), false, this);
            updateConstants();
        }

        public void updateAllowWhileIdleMinTimeLocked() {
            mAllowWhileIdleMinTime = mPendingIdleUntil != null
                    ? ALLOW_WHILE_IDLE_LONG_TIME : ALLOW_WHILE_IDLE_SHORT_TIME;
        }

        public void updateAllowWhileIdleWhitelistDurationLocked() {
            if (mLastAllowWhileIdleWhitelistDuration != ALLOW_WHILE_IDLE_WHITELIST_DURATION) {
                mLastAllowWhileIdleWhitelistDuration = ALLOW_WHILE_IDLE_WHITELIST_DURATION;
                BroadcastOptions opts = BroadcastOptions.makeBasic();
                opts.setTemporaryAppWhitelistDuration(ALLOW_WHILE_IDLE_WHITELIST_DURATION);
                mIdleOptions = opts.toBundle();
            }
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            updateConstants();
        }

        private void updateConstants() {
            synchronized (mLock) {
                try {
                    mParser.setString(Settings.Global.getString(mResolver,
                            Settings.Global.ALARM_MANAGER_CONSTANTS));
                } catch (IllegalArgumentException e) {
                    // Failed to parse the settings string, log this and move on
                    // with defaults.
                    Slog.e(TAG, "Bad device idle settings", e);
                }

                MIN_FUTURITY = mParser.getLong(KEY_MIN_FUTURITY, DEFAULT_MIN_FUTURITY);
                MIN_INTERVAL = mParser.getLong(KEY_MIN_INTERVAL, DEFAULT_MIN_INTERVAL);
                ALLOW_WHILE_IDLE_SHORT_TIME = mParser.getLong(KEY_ALLOW_WHILE_IDLE_SHORT_TIME,
                        DEFAULT_ALLOW_WHILE_IDLE_SHORT_TIME);
                ALLOW_WHILE_IDLE_LONG_TIME = mParser.getLong(KEY_ALLOW_WHILE_IDLE_LONG_TIME,
                        DEFAULT_ALLOW_WHILE_IDLE_LONG_TIME);
                ALLOW_WHILE_IDLE_WHITELIST_DURATION = mParser.getLong(
                        KEY_ALLOW_WHILE_IDLE_WHITELIST_DURATION,
                        DEFAULT_ALLOW_WHILE_IDLE_WHITELIST_DURATION);
                LISTENER_TIMEOUT = mParser.getLong(KEY_LISTENER_TIMEOUT,
                        DEFAULT_LISTENER_TIMEOUT);

                updateAllowWhileIdleMinTimeLocked();
                updateAllowWhileIdleWhitelistDurationLocked();
            }
        }

        void dump(PrintWriter pw) {
            pw.println("  Settings:");

            pw.print("    "); pw.print(KEY_MIN_FUTURITY); pw.print("=");
            TimeUtils.formatDuration(MIN_FUTURITY, pw);
            pw.println();

            pw.print("    "); pw.print(KEY_MIN_INTERVAL); pw.print("=");
            TimeUtils.formatDuration(MIN_INTERVAL, pw);
            pw.println();

            pw.print("    "); pw.print(KEY_LISTENER_TIMEOUT); pw.print("=");
            TimeUtils.formatDuration(LISTENER_TIMEOUT, pw);
            pw.println();

            pw.print("    "); pw.print(KEY_ALLOW_WHILE_IDLE_SHORT_TIME); pw.print("=");
            TimeUtils.formatDuration(ALLOW_WHILE_IDLE_SHORT_TIME, pw);
            pw.println();

            pw.print("    "); pw.print(KEY_ALLOW_WHILE_IDLE_LONG_TIME); pw.print("=");
            TimeUtils.formatDuration(ALLOW_WHILE_IDLE_LONG_TIME, pw);
            pw.println();

            pw.print("    "); pw.print(KEY_ALLOW_WHILE_IDLE_WHITELIST_DURATION); pw.print("=");
            TimeUtils.formatDuration(ALLOW_WHILE_IDLE_WHITELIST_DURATION, pw);
            pw.println();
        }
    }

    final Constants mConstants;

    // /M:add for DM feature ,@{
    private DMReceiver mDMReceiver = null;
    private boolean mDMEnable = true;
    private boolean mPPLEnable = true;
    private Object mDMLock = new Object();
    private ArrayList<PendingIntent> mDmFreeList = null;
    private ArrayList<String> mAlarmIconPackageList = null;
    private ArrayList<Alarm> mDmResendList = null;
    // /@}

    /// M: BG powerSaving feature start @{
    private AlarmManagerPlus mAmPlus;
    private boolean mNeedGrouping = true;
    /// M: BG powerSaving feature end @}

    /// M: Uplink Traffic Shaping feature start
    private IDataShapingManager dataShapingManager;

    // Intercept wake up alarms. prize-linkh-20160318
    private static final String LOG_TAG = TAG + "-iwa";
    private static final String INTERCEPTED_PKG_TAG = "+";
    private static final String INTERCEPTED_IGNORED_PKG_TAG = "$";

    private ArrayMap<String, InterceptPkgItem> mInterceptPkgsMap = new ArrayMap<>();
    //used for the same action with different packages. eg. "com.xiaomi.push.PING_TIMER" action.
    private ArraySet<String> mInterceptActionsSet = new ArraySet<>();
    
    private static final boolean DUMP_STATE_FOR_INTERCEPTING_WA = false;
    // flag indicating that if user enables this feature.
    private boolean mEnableInterceptWakeupAlarm = true;
    //Intercept all wakeup alarms of data apps(include system apps that are actually third apps)
    private final boolean mInterceptWakeupAlarmOfAllThirdApps = true;
    //If it's an alarm with alarm clock info, we don't intercept it.
    private final boolean mIgnoreWakeupAlarmWithAlarmClockInfo = true;
    
    private static final int MY_PID = Process.myPid();
    private PackageManager mPackageManager;
    private final ContentObserver mContentObserver = new ContentObserver(mHandler) {
        @Override
        public void onChange(boolean selfChange) {
            final boolean enable = Settings.System.getInt(
                    getContext().getContentResolver(), Settings.System.PRIZE_INTERCEPT_WAKEUP_ALARM_STATE, 1) == 1;
            if(localLOGV) {
                Slog.d(LOG_TAG, "onChange(). mEnableInterceptWakeupAlarm=" + mEnableInterceptWakeupAlarm + ", enable=" + enable);
            }
            //Note: Although it isn't thread safe to set mEnableInterceptWakeupAlarm.
            // we will post a delay message to rebatch all alarms and it can make us ignoring the thread safe problem.
            if(mEnableInterceptWakeupAlarm != enable) {
                mEnableInterceptWakeupAlarm = enable;
                Slog.d(LOG_TAG, "onChange(). mEnableInterceptWakeupAlarm=" + mEnableInterceptWakeupAlarm);
                mHandler.removeMessages(AlarmHandler.INTERCEPT_WAKEUP_ALARM_STATE_CHANGED);
                mHandler.sendMessageDelayed(mHandler.obtainMessage(AlarmHandler.INTERCEPT_WAKEUP_ALARM_STATE_CHANGED), 5000);
            }
        }
    };

    final class StoreState {
        public boolean hasRetrievedPackageInfo;
        public boolean fromSystemApp;
        public boolean ignoreIntercepted;
    }
    
    final class InterceptPkgItem {
        public String mPackage;
        private boolean mIgnorePackage = false;
        private ArrayMap<String, ArraySet<ComponentName>> mActionToComponentsMap = new ArrayMap<>();

        InterceptPkgItem(String pkg, String act, ComponentName comp, boolean ignorePkg) {
            mPackage = pkg;
            mIgnorePackage = ignorePkg;
            addData(act, comp);
        }

        InterceptPkgItem(String pkg, String act, ComponentName cn) {
            this(pkg, act, cn, false);
        }
        
        InterceptPkgItem(String pkg, String act) {
            this(pkg, act, null, false);
        }
        
        InterceptPkgItem(String pkg, boolean ignorePkg) {
            this(pkg, null, null, ignorePkg);
        }
        
        InterceptPkgItem(String pkg) {
            this(pkg, null, null, false);
        }

        public void setIgnorePackage(boolean ignored) {
            mIgnorePackage = ignored;
        }
        
        public void addActionData(String act) {
            addData(act, null);
        }

        public void addData(String act, ComponentName comp) {
            if(act != null) {
                ArraySet<ComponentName> comps = mActionToComponentsMap.get(act);
                if(comps == null) {
                    comps = new ArraySet<ComponentName>();
                    mActionToComponentsMap.put(act, comps);
                }
                if(comp != null) {
                    comps.add(comp);
                } else {
                    //it represents that it only wants to match a specific action.
                    comps.clear();
                }
            }
        }

        public void removeActionData(String act) {
            ArraySet<ComponentName> comps = mActionToComponentsMap.remove(act);
            if(comps != null) {
                comps.clear();
            }
        }
        
        public void removeAllData() {
            for(int idx = 0; idx < mActionToComponentsMap.size(); ++idx) {
                mActionToComponentsMap.valueAt(idx).clear();
            }

            mActionToComponentsMap.clear();
        }

        public void removeComponentData(String act, String comp, boolean pure) {
            boolean clearAll = (comp == null);
            ArraySet<ComponentName> comps = mActionToComponentsMap.get(act);
            if(comps != null) {
                if(clearAll) {
                    comps.clear();
                    if(pure) {
                        mActionToComponentsMap.remove(act);
                    }
                } else {
                    comps.remove(comp);
                }
            }
        }
        
        public void removeAllComponentsData(String act) {
            removeComponentData(act, null, true);
        }
        
        public void removeAllComponentsData(String act, boolean pure) {
            removeComponentData(act, null, pure);
        }

        public boolean shouldIntercepted(String act, ComponentName comp) {
            if(localLOGV) {
                Slog.d(LOG_TAG, "shouldIntercepted(): act=" + act + ", cn=" + comp);
            }
            boolean intercepted = !mIgnorePackage;
            
            if(act != null && mActionToComponentsMap.size() > 0) {
                ArraySet<ComponentName> comps = mActionToComponentsMap.get(act);
                if(comps == null) {
                    intercepted = false;
                } else if(comps.size() > 0 && comp != null && !comps.contains(comp)) {
                    intercepted = false;
                }
            }
            
            if(localLOGV && intercepted) {
                Slog.d(LOG_TAG, "shouldIntercepted(): intercept it!");
            }
            return intercepted;
        }

        public boolean shouldIntercepted(Intent intent) {
            if(intent != null) {
                return shouldIntercepted(intent.getAction(), intent.getComponent());
            }
            return !mIgnorePackage;
        }

        // If this item only contains pkg data, doesn't include any actions/components data, and
        // doesn't ignore pkg adujstment, then return true. It used for on shot alarm that accepts
        // IAlarmListener object.
        public boolean shouldIntercepted() {
            if(localLOGV) {
                Slog.d(LOG_TAG, "shouldIntercepted() ...");
            }
            
            boolean intercepted = !mIgnorePackage;
            
            if (intercepted && mActionToComponentsMap.size() > 0) {
                intercepted = false;
            }
            
            if(localLOGV && intercepted) {
                Slog.d(LOG_TAG, "shouldIntercepted(): intercept it for one-shot alarm that accepts IAlarmListener object!");
            }            
            return intercepted;
        }        
    }    

    private void initInterceptPkgData() {
        Slog.d(LOG_TAG, "initInterceptPkgData()....");
        
        mInterceptActionsSet.clear();
        mInterceptPkgsMap.clear();
        
        String[] list = getContext().getResources().getStringArray(com.prize.internal.R.array.intercepted_only_actions_for_wakeup_alarm);
        for(String action : list) {
            mInterceptActionsSet.add(action);
        }        
        list = getContext().getResources().getStringArray(com.prize.internal.R.array.intercepted_pkg_components_for_wakeup_alarm);
        parseInterceptedComponents(list);

        getContext().getContentResolver().registerContentObserver(
            Settings.System.getUriFor(Settings.System.PRIZE_INTERCEPT_WAKEUP_ALARM_STATE), false, mContentObserver);
        mEnableInterceptWakeupAlarm = Settings.System.getInt(
            getContext().getContentResolver(), Settings.System.PRIZE_INTERCEPT_WAKEUP_ALARM_STATE, 1) == 1;
        
        Slog.d(LOG_TAG, "mEnableInterceptWakeupAlarm=" + mEnableInterceptWakeupAlarm);
    }

    private void parseInterceptedComponents(String[] list) {
        if(list == null || list.length < 1) {
            return;
        }
        
        int index = 0;
        final int count = list.length;
        while(index < count) {
            String pkgName = list[index];
            String action = null;
            ComponentName component = null;
            boolean ignorePkg = false;
            boolean hasIgnorePkgTag = false;            
            boolean needMoreData = false;
            
            if(pkgName == null) {
                Slog.e(LOG_TAG, "Null package name for intecepted components! Abort parsing!");
                break;
            }
            
            int tagIndex = pkgName.lastIndexOf(INTERCEPTED_IGNORED_PKG_TAG);
            if(tagIndex < 0) {
                tagIndex = pkgName.lastIndexOf(INTERCEPTED_PKG_TAG);
            } else if(tagIndex != 0) {
                hasIgnorePkgTag = true;
            }

            if(tagIndex == 0) {
                Slog.e(LOG_TAG, "invalid pkg name for intecepted components! Abort parsing!");
                break;
            }         
            if(tagIndex > 0) {
                pkgName = pkgName.substring(0, tagIndex);
                needMoreData = true;
            }
            
            if(needMoreData) {
                int more_items_to_need = 2;
                if(hasIgnorePkgTag) {
                    more_items_to_need += 1;
                }
                if((index + more_items_to_need) >= count) { // 2 or 3 items. action, component, ignoredPkg
                    Log.e(LOG_TAG, "Not enough items for intecepted components! Abort parsing!");
                    break;
                }
                
                action = list[++index];
                String componentStr = list[++index];
                if(componentStr != null) {
                    component = ComponentName.unflattenFromString(componentStr);
                }
                if(hasIgnorePkgTag) {
                    String ignorePkgStr = list[++index];
                    if("true".equals(ignorePkgStr) || "false".equals(ignorePkgStr)) {
                        ignorePkg = "true".equals(ignorePkgStr);
                    } else {
                        Log.e(LOG_TAG, "invalid ignore pkg tag for intecepted components! Abort parsing!");
                        break;
                    }
                }                
            }
            
            index++;
            InterceptPkgItem item = new InterceptPkgItem(pkgName, action, component, ignorePkg);
            mInterceptPkgsMap.put(item.mPackage, item);
        }
    }
    //end..
    
    // Alarm delivery ordering bookkeeping
    static final int PRIO_TICK = 0;
    static final int PRIO_WAKEUP = 1;
    static final int PRIO_NORMAL = 2;

    final class PriorityClass {
        int seq;
        int priority;

        PriorityClass() {
            seq = mCurrentSeq - 1;
            priority = PRIO_NORMAL;
        }
    }

    final HashMap<String, PriorityClass> mPriorities = new HashMap<>();
    int mCurrentSeq = 0;

    static final class WakeupEvent {
        public long when;
        public int uid;
        public String action;

        public WakeupEvent(long theTime, int theUid, String theAction) {
            when = theTime;
            uid = theUid;
            action = theAction;
        }
    }

    final LinkedList<WakeupEvent> mRecentWakeups = new LinkedList<WakeupEvent>();
    final long RECENT_WAKEUP_PERIOD = 1000L * 60 * 60 * 24; // one day

    final class Batch {
        long start;     // These endpoints are always in ELAPSED
        long end;
        int flags;      // Flags for alarms, such as FLAG_STANDALONE.

        final ArrayList<Alarm> alarms = new ArrayList<Alarm>();

        Batch() {
            start = 0;
            end = Long.MAX_VALUE;
            flags = 0;
        }

        Batch(Alarm seed) {
            start = seed.whenElapsed;
            end = seed.maxWhenElapsed;
            flags = seed.flags;
            alarms.add(seed);
        }

        int size() {
            return alarms.size();
        }

        Alarm get(int index) {
            return alarms.get(index);
        }

        boolean canHold(long whenElapsed, long maxWhen) {
            return (end >= whenElapsed) && (start <= maxWhen);
        }

        boolean add(Alarm alarm) {
            boolean newStart = false;
            // narrows the batch if necessary; presumes that canHold(alarm) is true
            int index = Collections.binarySearch(alarms, alarm, sIncreasingTimeOrder);
            if (index < 0) {
                index = 0 - index - 1;
            }
            alarms.add(index, alarm);
            if (DEBUG_BATCH) {
                Slog.v(TAG, "Adding " + alarm + " to " + this);
            }
            if (alarm.whenElapsed > start) {
                start = alarm.whenElapsed;
                newStart = true;
            }
            if (alarm.maxWhenElapsed < end) {
                end = alarm.maxWhenElapsed;
            }
            flags |= alarm.flags;

            if (DEBUG_BATCH) {
                Slog.v(TAG, "    => now " + this);
            }
            return newStart;
        }

        boolean remove(final PendingIntent operation, final IAlarmListener listener) {
            if (operation == null && listener == null) {
                if (localLOGV) {
                    Slog.w(TAG, "requested remove() of null operation",
                            new RuntimeException("here"));
                }
                return false;
            }
            boolean didRemove = false;
            long newStart = 0;  // recalculate endpoints as we go
            long newEnd = Long.MAX_VALUE;
            int newFlags = 0;
            for (int i = 0; i < alarms.size(); ) {
                Alarm alarm = alarms.get(i);
                if (alarm.matches(operation, listener)) {
                    alarms.remove(i);
                    didRemove = true;
                    if (alarm.alarmClock != null) {
                        mNextAlarmClockMayChange = true;
                    }
                } else {
                    if (alarm.whenElapsed > newStart) {
                        newStart = alarm.whenElapsed;
                    }
                    if (alarm.maxWhenElapsed < newEnd) {
                        newEnd = alarm.maxWhenElapsed;
                    }
                    newFlags |= alarm.flags;
                    i++;
                }
            }
            if (didRemove) {
                // commit the new batch bounds
                start = newStart;
                end = newEnd;
                flags = newFlags;
            }
            return didRemove;
        }

        boolean remove(final String packageName) {
            if (packageName == null) {
                if (localLOGV) {
                    Slog.w(TAG, "requested remove() of null packageName",
                            new RuntimeException("here"));
                }
                return false;
            }
            boolean didRemove = false;
            long newStart = 0;  // recalculate endpoints as we go
            long newEnd = Long.MAX_VALUE;
            int newFlags = 0;
            for (int i = alarms.size()-1; i >= 0; i--) {
                Alarm alarm = alarms.get(i);
                if (alarm.matches(packageName)) {
                    alarms.remove(i);
                    didRemove = true;
                    if (alarm.alarmClock != null) {
                        mNextAlarmClockMayChange = true;
                    }
                } else {
                    if (alarm.whenElapsed > newStart) {
                        newStart = alarm.whenElapsed;
                    }
                    if (alarm.maxWhenElapsed < newEnd) {
                        newEnd = alarm.maxWhenElapsed;
                    }
                    newFlags |= alarm.flags;
                }
            }
            if (didRemove) {
                // commit the new batch bounds
                start = newStart;
                end = newEnd;
                flags = newFlags;
            }
            return didRemove;
        }

        boolean removeForStopped(final int uid) {
            boolean didRemove = false;
            long newStart = 0;  // recalculate endpoints as we go
            long newEnd = Long.MAX_VALUE;
            int newFlags = 0;
            for (int i = alarms.size()-1; i >= 0; i--) {
                Alarm alarm = alarms.get(i);
                try {
                    if (alarm.uid == uid && ActivityManagerNative.getDefault().getAppStartMode(
                            uid, alarm.packageName) == ActivityManager.APP_START_MODE_DISABLED) {
                        alarms.remove(i);
                        didRemove = true;
                        if (alarm.alarmClock != null) {
                            mNextAlarmClockMayChange = true;
                        }
                    } else {
                        if (alarm.whenElapsed > newStart) {
                            newStart = alarm.whenElapsed;
                        }
                        if (alarm.maxWhenElapsed < newEnd) {
                            newEnd = alarm.maxWhenElapsed;
                        }
                        newFlags |= alarm.flags;
                    }
                } catch (RemoteException e) {
                }
            }
            if (didRemove) {
                // commit the new batch bounds
                start = newStart;
                end = newEnd;
                flags = newFlags;
            }
            return didRemove;
        }

        boolean remove(final int userHandle) {
            boolean didRemove = false;
            long newStart = 0;  // recalculate endpoints as we go
            long newEnd = Long.MAX_VALUE;
            for (int i = 0; i < alarms.size(); ) {
                Alarm alarm = alarms.get(i);
                if (UserHandle.getUserId(alarm.creatorUid) == userHandle) {
                    alarms.remove(i);
                    didRemove = true;
                    if (alarm.alarmClock != null) {
                        mNextAlarmClockMayChange = true;
                    }
                } else {
                    if (alarm.whenElapsed > newStart) {
                        newStart = alarm.whenElapsed;
                    }
                    if (alarm.maxWhenElapsed < newEnd) {
                        newEnd = alarm.maxWhenElapsed;
                    }
                    i++;
                }
            }
            if (didRemove) {
                // commit the new batch bounds
                start = newStart;
                end = newEnd;
            }
            return didRemove;
        }

        boolean hasPackage(final String packageName) {
            final int N = alarms.size();
            for (int i = 0; i < N; i++) {
                Alarm a = alarms.get(i);
                if (a.matches(packageName)) {
                    return true;
                }
            }
            return false;
        }

        boolean hasWakeups() {
            final int N = alarms.size();
            for (int i = 0; i < N; i++) {
                Alarm a = alarms.get(i);
                // non-wakeup alarms are types 1 and 3, i.e. have the low bit set
                if ((a.type & TYPE_NONWAKEUP_MASK) == 0) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public String toString() {
            StringBuilder b = new StringBuilder(40);
            b.append("Batch{"); b.append(Integer.toHexString(this.hashCode()));
            b.append(" num="); b.append(size());
            b.append(" start="); b.append(start);
            b.append(" end="); b.append(end);
            if (flags != 0) {
                b.append(" flgs=0x");
                b.append(Integer.toHexString(flags));
            }
            b.append('}');
            return b.toString();
        }
    }

    static class BatchTimeOrder implements Comparator<Batch> {
        public int compare(Batch b1, Batch b2) {
            long when1 = b1.start;
            long when2 = b2.start;
            if (when1 > when2) {
                return 1;
            }
            if (when1 < when2) {
                return -1;
            }
            return 0;
        }
    }

    final Comparator<Alarm> mAlarmDispatchComparator = new Comparator<Alarm>() {
        @Override
        public int compare(Alarm lhs, Alarm rhs) {
            // priority class trumps everything.  TICK < WAKEUP < NORMAL
            if (lhs.priorityClass.priority < rhs.priorityClass.priority) {
                return -1;
            } else if (lhs.priorityClass.priority > rhs.priorityClass.priority) {
                return 1;
            }

            // within each class, sort by nominal delivery time
            if (lhs.whenElapsed < rhs.whenElapsed) {
                return -1;
            } else if (lhs.whenElapsed > rhs.whenElapsed) {
                return 1;
            }

            // same priority class + same target delivery time
            return 0;
        }
    };

    void calculateDeliveryPriorities(ArrayList<Alarm> alarms) {
        final int N = alarms.size();
        for (int i = 0; i < N; i++) {
            Alarm a = alarms.get(i);

            final int alarmPrio;
            if (a.operation != null
                    && Intent.ACTION_TIME_TICK.equals(a.operation.getIntent().getAction())) {
                alarmPrio = PRIO_TICK;
            } else if (a.wakeup) {
                alarmPrio = PRIO_WAKEUP;
            } else {
                alarmPrio = PRIO_NORMAL;
            }

            PriorityClass packagePrio = a.priorityClass;
            String alarmPackage = (a.operation != null)
                    ? a.operation.getCreatorPackage()
                    : a.packageName;
            if (packagePrio == null) packagePrio = mPriorities.get(alarmPackage);
            if (packagePrio == null) {
                packagePrio = a.priorityClass = new PriorityClass(); // lowest prio & stale sequence
                mPriorities.put(alarmPackage, packagePrio);
            }
            a.priorityClass = packagePrio;

            if (packagePrio.seq != mCurrentSeq) {
                // first alarm we've seen in the current delivery generation from this package
                packagePrio.priority = alarmPrio;
                packagePrio.seq = mCurrentSeq;
            } else {
                // Multiple alarms from this package being delivered in this generation;
                // bump the package's delivery class if it's warranted.
                // TICK < WAKEUP < NORMAL
                if (alarmPrio < packagePrio.priority) {
                    packagePrio.priority = alarmPrio;
                }
            }
        }
    }

    // minimum recurrence period or alarm futurity for us to be able to fuzz it
    static final long MIN_FUZZABLE_INTERVAL = 10000;
    static final BatchTimeOrder sBatchOrder = new BatchTimeOrder();
    final ArrayList<Batch> mAlarmBatches = new ArrayList<>();

    // set to null if in idle mode; while in this mode, any alarms we don't want
    // to run during this time are placed in mPendingWhileIdleAlarms
    Alarm mPendingIdleUntil = null;
    Alarm mNextWakeFromIdle = null;
    ArrayList<Alarm> mPendingWhileIdleAlarms = new ArrayList<>();

    public AlarmManagerService(Context context) {
        super(context);
        mConstants = new Constants(mHandler);
    }

    static long convertToElapsed(long when, int type) {
        final boolean isRtc = (type == RTC || type == RTC_WAKEUP);
        if (isRtc) {
            when -= System.currentTimeMillis() - SystemClock.elapsedRealtime();
        }
        return when;
    }

    // Apply a heuristic to { recurrence interval, futurity of the trigger time } to
    // calculate the end of our nominal delivery window for the alarm.
    static long maxTriggerTime(long now, long triggerAtTime, long interval) {
        // Current heuristic: batchable window is 75% of either the recurrence interval
        // [for a periodic alarm] or of the time from now to the desired delivery time,
        // with a minimum delay/interval of 10 seconds, under which we will simply not
        // defer the alarm.
        long futurity = (interval == 0)
                ? (triggerAtTime - now)
                : interval;
        if (futurity < MIN_FUZZABLE_INTERVAL) {
            futurity = 0;
        }
        return triggerAtTime + (long)(.75 * futurity);
    }

    // returns true if the batch was added at the head
    static boolean addBatchLocked(ArrayList<Batch> list, Batch newBatch) {
        int index = Collections.binarySearch(list, newBatch, sBatchOrder);
        if (index < 0) {
            index = 0 - index - 1;
        }
        list.add(index, newBatch);
        return (index == 0);
    }

    // Return the index of the matching batch, or -1 if none found.
    int attemptCoalesceLocked(long whenElapsed, long maxWhen) {
        final int N = mAlarmBatches.size();
        for (int i = 0; i < N; i++) {
            Batch b = mAlarmBatches.get(i);
            if (mSupportAlarmGrouping && (mAmPlus != null)) {
                //M mark b.flags for check condition
                 if (b.canHold(whenElapsed, maxWhen)) {
                    return i;
                 }
            } else {
             if ((b.flags & AlarmManager.FLAG_STANDALONE) == 0
                  && b.canHold(whenElapsed, maxWhen)) {
                     if (b.canHold(whenElapsed, maxWhen)) {
                         return i;
                     }
                 }
            }
        }
        return -2;
    }

    // The RTC clock has moved arbitrarily, so we need to recalculate all the batching
    void rebatchAllAlarms() {
        synchronized (mLock) {
            rebatchAllAlarmsLocked(true);
        }
    }

    void rebatchAllAlarmsLocked(boolean doValidate) {
        ArrayList<Batch> oldSet = (ArrayList<Batch>) mAlarmBatches.clone();
        mAlarmBatches.clear();
        Alarm oldPendingIdleUntil = mPendingIdleUntil;
        final long nowElapsed = SystemClock.elapsedRealtime();
        final int oldBatches = oldSet.size();
        if (DEBUG_BATCH) {
            Slog.d(TAG, "rebatchAllAlarmsLocked begin oldBatches count = " + oldBatches);
        }
        for (int batchNum = 0; batchNum < oldBatches; batchNum++) {
            Batch batch = oldSet.get(batchNum);
            final int N = batch.size();
            if (DEBUG_BATCH) {
                Slog.d(TAG, "rebatchAllAlarmsLocked  batch.size() = " + batch.size());
            }
            for (int i = 0; i < N; i++) {
                reAddAlarmLocked(batch.get(i), nowElapsed, doValidate);
            }
        }
        if (oldPendingIdleUntil != null && oldPendingIdleUntil != mPendingIdleUntil) {
            Slog.wtf(TAG, "Rebatching: idle until changed from " + oldPendingIdleUntil
                    + " to " + mPendingIdleUntil);
            if (mPendingIdleUntil == null) {
                // Somehow we lost this...  we need to restore all of the pending alarms.
                restorePendingWhileIdleAlarmsLocked();
            }
        }
        rescheduleKernelAlarmsLocked();
        updateNextAlarmClockLocked();
    }

    void reAddAlarmLocked(Alarm a, long nowElapsed, boolean doValidate) {
        a.when = a.origWhen;
        long whenElapsed = convertToElapsed(a.when, a.type);
        //M using the powerSaving feature
        long maxElapsed;
        if (mSupportAlarmGrouping && (mAmPlus != null)) {
            // M: BG powerSaving feature
            maxElapsed = mAmPlus.getMaxTriggerTime(a.type, whenElapsed, a.windowLength,
                         a.repeatInterval, a.operation, mAlarmMode, true);
            if (maxElapsed < 0) {
                maxElapsed = 0 - maxElapsed;
                a.needGrouping = false;
            } else {
                a.needGrouping = true;
            }
        } else if (a.windowLength == AlarmManager.WINDOW_EXACT) {
            maxElapsed = whenElapsed;
        } else if (a.windowLength < 0) {
            maxElapsed = maxTriggerTime(nowElapsed, whenElapsed, a.repeatInterval);
            // Fix this window in place, so that as time approaches we don't collapse it.
            a.windowLength = maxElapsed - whenElapsed;
        } else {
            maxElapsed = whenElapsed + a.windowLength;
        }
        /*
        if (a.windowLength == AlarmManager.WINDOW_EXACT) {
            // Exact
            maxElapsed = whenElapsed;
        } else {
            // Not exact.  Preserve any explicit window, otherwise recalculate
            // the window based on the alarm's new futurity.  Note that this
            // reflects a policy of preferring timely to deferred delivery.
            maxElapsed = (a.windowLength > 0)
                    ? (whenElapsed + a.windowLength)
                    : maxTriggerTime(nowElapsed, whenElapsed, a.repeatInterval);
        }
        */
        a.whenElapsed = whenElapsed;
        a.maxWhenElapsed = maxElapsed;
        if (DEBUG_BATCH) {
             Slog.d(TAG, "reAddAlarmLocked a.whenElapsed  = " + a.whenElapsed
                   + " a.maxWhenElapsed = "  + a.maxWhenElapsed);
        }
        //Intercept wake up alarms. prize-linkh-20160318
        if(PrizeOption.PRIZE_INTERCEPT_WAKEUP_ALARMS) {
            a.type = convertWakeupAlarmToNonWakeup(a, a.originalType, a.operation, a.listener, "reAddAlarmLocked");
        }
        //end...
        setImplLocked(a, true, doValidate);
    }

    void restorePendingWhileIdleAlarmsLocked() {
        if (RECORD_DEVICE_IDLE_ALARMS) {
            IdleDispatchEntry ent = new IdleDispatchEntry();
            ent.uid = 0;
            ent.pkg = "FINISH IDLE";
            ent.elapsedRealtime = SystemClock.elapsedRealtime();
            mAllowWhileIdleDispatches.add(ent);
        }

        // Bring pending alarms back into the main list.
        if (mPendingWhileIdleAlarms.size() > 0) {
            ArrayList<Alarm> alarms = mPendingWhileIdleAlarms;
            mPendingWhileIdleAlarms = new ArrayList<>();
            final long nowElapsed = SystemClock.elapsedRealtime();
            for (int i=alarms.size() - 1; i >= 0; i--) {
                Alarm a = alarms.get(i);
                reAddAlarmLocked(a, nowElapsed, false);
            }
        }

        // Make sure we are using the correct ALLOW_WHILE_IDLE min time.
        mConstants.updateAllowWhileIdleMinTimeLocked();

        // Reschedule everything.
        rescheduleKernelAlarmsLocked();
        updateNextAlarmClockLocked();

        // And send a TIME_TICK right now, since it is important to get the UI updated.
        try {
            mTimeTickSender.send();
        } catch (PendingIntent.CanceledException e) {
        }
        //Slog.d(TAG, "rebatchAllAlarmsLocked end");
    }

    // /M:add for IPO and powerOffAlarm feature ,@{
    private Object mWaitThreadlock = new Object();
    private boolean mIPOShutdown = false;
    private Object mPowerOffAlarmLock = new Object();
    private final ArrayList<Alarm> mPoweroffAlarms = new ArrayList<Alarm>();
    // /@}


    static final class InFlight {
        final PendingIntent mPendingIntent;
        final IBinder mListener;
        final WorkSource mWorkSource;
        final int mUid;
        final String mTag;
        final BroadcastStats mBroadcastStats;
        final FilterStats mFilterStats;
        final int mAlarmType;

        InFlight(AlarmManagerService service, PendingIntent pendingIntent, IAlarmListener listener,
                WorkSource workSource, int uid, String alarmPkg, int alarmType, String tag,
                long nowELAPSED) {
            mPendingIntent = pendingIntent;
            mListener = listener != null ? listener.asBinder() : null;
            mWorkSource = workSource;
            mUid = uid;
            mTag = tag;
            mBroadcastStats = (pendingIntent != null)
                    ? service.getStatsLocked(pendingIntent)
                    : service.getStatsLocked(uid, alarmPkg);
            FilterStats fs = mBroadcastStats.filterStats.get(mTag);
            if (fs == null) {
                fs = new FilterStats(mBroadcastStats, mTag);
                mBroadcastStats.filterStats.put(mTag, fs);
            }
            fs.lastTime = nowELAPSED;
            mFilterStats = fs;
            mAlarmType = alarmType;
        }
    }

    static final class FilterStats {
        final BroadcastStats mBroadcastStats;
        final String mTag;

        long lastTime;
        long aggregateTime;
        int count;
        int numWakeup;
        long startTime;
        int nesting;

        FilterStats(BroadcastStats broadcastStats, String tag) {
            mBroadcastStats = broadcastStats;
            mTag = tag;
        }
    }

    static final class BroadcastStats {
        final int mUid;
        final String mPackageName;

        long aggregateTime;
        int count;
        int numWakeup;
        long startTime;
        int nesting;
        final ArrayMap<String, FilterStats> filterStats = new ArrayMap<String, FilterStats>();

        BroadcastStats(int uid, String packageName) {
            mUid = uid;
            mPackageName = packageName;
        }
    }

    final SparseArray<ArrayMap<String, BroadcastStats>> mBroadcastStats
            = new SparseArray<ArrayMap<String, BroadcastStats>>();

    int mNumDelayedAlarms = 0;
    long mTotalDelayTime = 0;
    long mMaxDelayTime = 0;

    @Override
    public void onStart() {
        mNativeData = init();
        mNextWakeup = mNextNonWakeup = 0;

        if (SystemProperties.get("ro.mtk_bg_power_saving_support").equals("1")) {
        //M:enable PowerSaving
            mSupportAlarmGrouping = true;
        }

        // Intercept wake up alarms. prize-linkh-20160520
        mPackageManager = getContext().getPackageManager();
        if(PrizeOption.PRIZE_INTERCEPT_WAKEUP_ALARMS) {
            initInterceptPkgData();
        }
        //end..     
        
        // We have to set current TimeZone info to kernel
        // because kernel doesn't keep this after reboot
        setTimeZoneImpl(SystemProperties.get(TIMEZONE_PROPERTY));

        /// M: BG powerSaving feature start @{

        if (mSupportAlarmGrouping && (mAmPlus == null)) {
            try {
                    mAmPlus = new AlarmManagerPlus(getContext());
                } catch (Exception e) {
                    e.printStackTrace();
                }
        }
        /// M: BG powerSaving feature end @}

        PowerManager pm = (PowerManager) getContext().getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "*alarm*");

        mTimeTickSender = PendingIntent.getBroadcastAsUser(getContext(), 0,
                new Intent(Intent.ACTION_TIME_TICK).addFlags(
                        Intent.FLAG_RECEIVER_REGISTERED_ONLY
                        | Intent.FLAG_RECEIVER_FOREGROUND), 0,
                        UserHandle.ALL);
        Intent intent = new Intent(Intent.ACTION_DATE_CHANGED);
        intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
        mDateChangeSender = PendingIntent.getBroadcastAsUser(getContext(), 0, intent,
                Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT, UserHandle.ALL);

        // now that we have initied the driver schedule the alarm
        mClockReceiver = new ClockReceiver();
        mClockReceiver.scheduleTimeTickEvent();
        mClockReceiver.scheduleDateChangedEvent();
        mInteractiveStateReceiver = new InteractiveStateReceiver();
        mWFDStatusChangedReceiver = new WFDStatusChangedReceiver();
        mUninstallReceiver = new UninstallReceiver();

        mAlarmIconPackageList = new ArrayList<String>();
        mAlarmIconPackageList.add("com.android.deskclock");
        // /M:add for DM feature ,@{
        try {
            IBinder binder = ServiceManager.getService("DmAgent");
            if (binder != null) {
                DmAgent agent = DmAgent.Stub.asInterface(binder);
                boolean locked = agent.isLockFlagSet();
                Slog.i(TAG, "dm state lock is " + locked);
                mDMEnable = !locked;
            } else {
                Slog.e(TAG, "dm binder is null!");
            }
        } catch (RemoteException e) {
            Slog.e(TAG, "remote error");
        }
        mDMReceiver = new DMReceiver();
        mDmFreeList = new ArrayList<PendingIntent>();
        mDmFreeList.add(mTimeTickSender);
        mDmFreeList.add(mDateChangeSender);
        mDmResendList = new ArrayList<Alarm>();
        // /@}

        if (mNativeData != 0) {
            AlarmThread waitThread = new AlarmThread();
            waitThread.start();
        } else {
            Slog.w(TAG, "Failed to open alarm driver. Falling back to a handler.");
        }

        try {
            ActivityManagerNative.getDefault().registerUidObserver(new UidObserver(),
                    ActivityManager.UID_OBSERVER_IDLE);
        } catch (RemoteException e) {
            // ignored; both services live in system_server
        }

        // /M:add for IPO and PoerOffAlarm feature ,@{
        if (SystemProperties.get("ro.mtk_ipo_support").equals("1")) {
            IntentFilter filter = new IntentFilter();
            filter.addAction("android.intent.action.ACTION_BOOT_IPO");
            filter.addAction("android.intent.action.ACTION_SHUTDOWN");
            filter.addAction("android.intent.action.ACTION_SHUTDOWN_IPO");
            getContext().registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if ("android.intent.action.ACTION_SHUTDOWN".equals(intent.getAction())
                            || "android.intent.action.ACTION_SHUTDOWN_IPO".equals(intent
                                    .getAction())) {
                        shutdownCheckPoweroffAlarm();
                        mIPOShutdown = true;
                        if (mNativeData != -1 && "android.intent.action.ACTION_SHUTDOWN_IPO".equals(intent
                                    .getAction())) {
                            Slog.d(TAG, "receive ACTION_SHUTDOWN_IPO , so close the fd ");
                            close(mNativeData);
                            mNativeData = -1;
                        }
                        /*set(ELAPSED_REALTIME, 100, 0, 0, PendingIntent.getBroadcast(context,
                                0,
                                new Intent(Intent.ACTION_TIME_TICK), 0), null); // whatever. */
                    } else if ("android.intent.action.ACTION_BOOT_IPO".equals(intent.getAction())) {
                        mIPOShutdown = false;
                        mNativeData = init();
                        mNextWakeup = mNextNonWakeup = 0;
                        //Slog.i(TAG, "ipo mNativeData is " + Integer.toString(mNativeData));

                        Intent timeChangeIntent = new Intent(Intent.ACTION_TIME_CHANGED);
                        timeChangeIntent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
                        context.sendBroadcast(timeChangeIntent);

                        mClockReceiver.scheduleTimeTickEvent();
                        mClockReceiver.scheduleDateChangedEvent();
                        synchronized (mWaitThreadlock) {
                            mWaitThreadlock.notify();
                        }
                    }
                }
            }, filter);
        }
        publishBinderService(Context.ALARM_SERVICE, mService);
        publishLocalService(LocalService.class, new LocalService());
    }

    /**
     *This API for app to get the boot reason
     */
    public boolean bootFromPoweroffAlarm() {
        String bootReason = SystemProperties.get("sys.boot.reason");
        boolean ret = (bootReason != null && bootReason.equals("1")) ? true : false;
        return ret;
    }

    @Override
    public void onBootPhase(int phase) {
        if (phase == PHASE_SYSTEM_SERVICES_READY) {
            mConstants.start(getContext().getContentResolver());
            mAppOps = (AppOpsManager) getContext().getSystemService(Context.APP_OPS_SERVICE);
            mLocalDeviceIdleController
                    = LocalServices.getService(DeviceIdleController.LocalService.class);
            // /@}
            /// M: Uplink Traffic Shaping feature start @{
            dataShapingManager = (IDataShapingManager) ServiceManager.
                             getService(Context.DATA_SHAPING_SERVICE);
            // / M: end @}
        }
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            close(mNativeData);
        } finally {
            super.finalize();
        }
    }

    void setTimeZoneImpl(String tz) {
        if (TextUtils.isEmpty(tz)) {
            return;
        }

        TimeZone zone = TimeZone.getTimeZone(tz);
        // Prevent reentrant calls from stepping on each other when writing
        // the time zone property
        boolean timeZoneWasChanged = false;
        synchronized (this) {
            String current = SystemProperties.get(TIMEZONE_PROPERTY);
            if (current == null || !current.equals(zone.getID())) {
                if (localLOGV) {
                    Slog.v(TAG, "timezone changed: " + current + ", new=" + zone.getID());
                }
                timeZoneWasChanged = true;
                SystemProperties.set(TIMEZONE_PROPERTY, zone.getID());
            }

            // Update the kernel timezone information
            // Kernel tracks time offsets as 'minutes west of GMT'
            int gmtOffset = zone.getOffset(System.currentTimeMillis());
            setKernelTimezone(mNativeData, -(gmtOffset / 60000));
        }

        TimeZone.setDefault(null);

        if (timeZoneWasChanged) {
            Intent intent = new Intent(Intent.ACTION_TIMEZONE_CHANGED);
            intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
            intent.putExtra("time-zone", zone.getID());
            getContext().sendBroadcastAsUser(intent, UserHandle.ALL);
        }
    }

    void removeImpl(PendingIntent operation) {
        if (operation == null) {
            return;
        }
        synchronized (mLock) {
            removeLocked(operation, null);
        }
    }

    // Intercept wake up alarms. prize-linkh-20160318 
    int convertWakeupAlarmToNonWakeup(Alarm alarm, int originalType, PendingIntent operation, 
        IAlarmListener listener, String reason) {
        return convertWakeupAlarmToNonWakeup(alarm, originalType, operation, listener, null, null, reason);
    }
    int convertWakeupAlarmToNonWakeup(int originalType, PendingIntent operation, IAlarmListener listener, 
        String callingPkg, StoreState state, String reason) {
        return convertWakeupAlarmToNonWakeup(null, originalType, operation, listener, callingPkg, state, reason);
    }    
    int convertWakeupAlarmToNonWakeup(Alarm alarm, int originalType, PendingIntent operation, IAlarmListener listener, 
        String callingPkg, StoreState state, String reason) {
        if(!mEnableInterceptWakeupAlarm) {
            return originalType;
        }
        
        if(localLOGV) {
            if (listener == null) {
                Slog.d(LOG_TAG, "convertWakeupAlarmToNonWakeup(): type=" + originalType + ", op=" + operation + ", reason=" + reason);
            } else {
                Slog.d(LOG_TAG, "convertWakeupAlarmToNonWakeup(): type=" + originalType + ", listener=" + listener + ", reason=" + reason);
            }
        }

        if(originalType != RTC_WAKEUP && originalType != ELAPSED_REALTIME_WAKEUP) {
            return originalType;
        }
        if(alarm != null) {
            if(alarm.ignoreIntercepted || (mIgnoreWakeupAlarmWithAlarmClockInfo && alarm.alarmClock != null)) {
                return originalType;
            }
        }        

        if (operation == null && listener == null) {
            return originalType;
        } else if (alarm != null && alarm.listener != listener) {
            // inconsistent state.
            return originalType;
        }
        
        int newType = originalType;
      
        
        boolean needCheckAgain = true;
        ApplicationInfo ai = null;
        boolean hasAlarmListener = false;
        boolean isSystemApp = false;
        final String callingPackage;
        
        if (operation == null) {
            // This is a one shot alarm that accepted IAlarmListener object.
            hasAlarmListener = true;
            if (callingPkg == null && alarm != null) {
                callingPkg = alarm.packageName;
            }

            if (callingPkg == null) {
                return originalType;
            }

            callingPackage = callingPkg;
        } else {
            callingPackage = operation.getCreatorPackage();
        }

        if (callingPackage == null || callingPackage.equals("android")) {
            if(alarm != null) {
                alarm.hasRetrievedPackageInfo = alarm.fromSystemApp = 
                    callingPackage == null ? false : true;
            }
            if(state != null) {
                state.hasRetrievedPackageInfo = state.fromSystemApp = 
                    callingPackage == null ? false : true;
            }
            
            return originalType;
        }
        
        boolean shouldRetrievePkgInfo = true;
        if(alarm != null && alarm.hasRetrievedPackageInfo) {
            shouldRetrievePkgInfo = false;
        }
        
        if(localLOGV) {
            Slog.d(LOG_TAG, "convertWakeupAlarmToNonWakeup(): shouldRetrievePkgInfo=" 
                + shouldRetrievePkgInfo + ", callingPkg=" + callingPackage);
        }
        
        if(shouldRetrievePkgInfo) {
            try {
                if(localLOGV) {
                    Slog.d(LOG_TAG, "convertWakeupAlarmToNonWakeup(): retrieve pkg " + callingPackage);
                }
                ai = mPackageManager.getApplicationInfo(callingPackage, 0);
                if(localLOGV) {
                    Slog.d(LOG_TAG, "convertWakeupAlarmToNonWakeup(): finish to retrieve. ai=" + ai);
                }
                
                if(ai != null) {
                    //If it's null, we always retrieve it.
                    if(alarm != null) {
                        alarm.hasRetrievedPackageInfo = true;
                    }
                    if(state != null) {
                        state.hasRetrievedPackageInfo = true;
                    }
                }
                
                if(ai != null && (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                    && (ai.prizeFlags & ApplicationInfo.FLAG_IS_PREBUILT_THIRD_APPS) == 0) {
                    if(alarm != null) {
                        alarm.fromSystemApp = true;
                    }
                    if(state != null) {
                        state.fromSystemApp = true;
                    }

                    isSystemApp = true;
                } else {
                    if(alarm != null) {
                        alarm.fromSystemApp = false;
                    }
                    if(state != null) {
                        state.fromSystemApp = false;
                    }
                }
            } catch(Exception e) {
                Slog.w(LOG_TAG, "faild to retrieve pkg " + callingPackage + ": " + e);
            }
        } else {
            isSystemApp = alarm.fromSystemApp;
            
            if(state != null) {
                state.hasRetrievedPackageInfo = true;
            }             

            if(state != null) {
                state.fromSystemApp = alarm.fromSystemApp;
            }
        }
        
        if(localLOGV) {
            Slog.d(LOG_TAG, "convertWakeupAlarmToNonWakeup(): A system App ? " + isSystemApp);
        }

        if (isSystemApp) {                
            InterceptPkgItem item = mInterceptPkgsMap.get(callingPackage);
            
            if (hasAlarmListener) {
                if (item == null || !item.shouldIntercepted()) {
                    return originalType;
                }
            } else {
                
                long identity = 0;
                boolean hasClearedCallingIdentity = false;
                if (Binder.getCallingPid() != MY_PID) {
                    // from external. Calling getIntent() maybe throw permission denied exception. so 
                    // we must clear binder identity here.
                    identity = Binder.clearCallingIdentity();
                    hasClearedCallingIdentity = true;
                    if(localLOGV) {
                        Slog.d(LOG_TAG, "convertWakeupAlarmToNonWakeup(): clear calling identity for getting intent!");
                    }
                }
                
                Intent intent = operation.getIntent();
                if (hasClearedCallingIdentity) {
                    Binder.restoreCallingIdentity(identity);
                    if(localLOGV) {
                        Slog.d(LOG_TAG, "convertWakeupAlarmToNonWakeup(): restore calling identity!");
                    }                        
                }
                
                if(item == null) {
                    if(!mInterceptActionsSet.contains(intent.getAction())) {
                        return originalType;
                    }
                } else if(!item.shouldIntercepted(intent) && !mInterceptActionsSet.contains(intent.getAction())) {
                    return originalType;
                }
            }

        }

        
        //Calendar cal = Calendar.getInstance();
        //int hour_of_day = (cal != null) ? cal.get(Calendar.HOUR_OF_DAY) : -1;
        if(true) { //(hour_of_day >= 0 && hour_of_day < 6) { // 0 -6 
            if(originalType == RTC_WAKEUP) {
                newType = RTC;
            } else if(originalType == ELAPSED_REALTIME_WAKEUP) {
                newType = ELAPSED_REALTIME;
            }
            
            if(localLOGV) {
                Slog.d(LOG_TAG, "convertWakeupAlarmToNonWakeup(): Change to non wake-up type! type=" + newType);
            }
        }

        return newType;
    } 
    //end......
    
    void setImpl(int type, long triggerAtTime, long windowLength, long interval,
            PendingIntent operation, IAlarmListener directReceiver, String listenerTag,
            int flags, WorkSource workSource, AlarmManager.AlarmClockInfo alarmClock,
            int callingUid, String callingPackage) {
        // must be *either* PendingIntent or AlarmReceiver, but not both
        if ((operation == null && directReceiver == null)
                || (operation != null && directReceiver != null)) {
            Slog.w(TAG, "Alarms must either supply a PendingIntent or an AlarmReceiver");
            // NB: previous releases failed silently here, so we are continuing to do the same
            // rather than throw an IllegalArgumentException.
            return;
        }
        /*
        if (mAmPlus.isPowerSavingStart()) {
            isStandalone = false;
        }
        */
        // /M:add for IPO,when shut down,do not set alarm to driver ,@{
        if (mIPOShutdown && (mNativeData == -1)) {
            Slog.w(TAG, "IPO Shutdown so drop the alarm");
            return;
        }
        // /@}

        // Sanity check the window length.  This will catch people mistakenly
        // trying to pass an end-of-window timestamp rather than a duration.
        if (windowLength > AlarmManager.INTERVAL_HALF_DAY) {
            Slog.w(TAG, "Window length " + windowLength
                    + "ms suspiciously long; limiting to 1 hour");
            windowLength = AlarmManager.INTERVAL_HOUR;
        }

        // Sanity check the recurrence interval.  This will catch people who supply
        // seconds when the API expects milliseconds.
        final long minInterval = mConstants.MIN_INTERVAL;
        if (interval > 0 && interval < minInterval) {
            Slog.w(TAG, "Suspiciously short interval " + interval
                    + " millis; expanding to " + (minInterval/1000)
                    + " seconds");
            interval = minInterval;
        }

        if (triggerAtTime < 0) {
            final long what = Binder.getCallingPid();
            Slog.w(TAG, "Invalid alarm trigger time! " + triggerAtTime + " from uid=" + callingUid
                    + " pid=" + what);
            triggerAtTime = 0;
        }

        // /M:add for PowerOffAlarm feature type 7 for seetings,type 8 for
        // deskcolck ,@{
        if (type == 7 || type == 8) {
            if (mNativeData == -1) {
                Slog.w(TAG, "alarm driver not open ,return!");
                return;
            }

            Slog.d(TAG, "alarm set type 7 8, package name " + operation.getTargetPackage());
            String packageName = operation.getTargetPackage();

            String setPackageName = null;
            long nowTime = System.currentTimeMillis();
            if (triggerAtTime < nowTime) {
                Slog.w(TAG, "power off alarm set time is wrong! nowTime = " + nowTime + " ; triggerAtTime = " + triggerAtTime);
                return;
            }

            synchronized (mPowerOffAlarmLock) {
                removePoweroffAlarmLocked(operation.getTargetPackage());
                final int poweroffAlarmUserId = UserHandle.getCallingUserId();
                Alarm alarm = new Alarm(type, triggerAtTime, 0, 0, 0,
                        interval, operation, directReceiver, listenerTag,
                        workSource, 0, alarmClock,
                        poweroffAlarmUserId, callingPackage, true, type, true, true, true/* Intercept wake up alarms. prize-linkh-20160602*/);
                addPoweroffAlarmLocked(alarm);
                if (mPoweroffAlarms.size() > 0) {
                    resetPoweroffAlarm(mPoweroffAlarms.get(0));
                }
            }
            type = RTC_WAKEUP;

        }
        // /@}

        final long nowElapsed = SystemClock.elapsedRealtime();
        final long nominalTrigger = convertToElapsed(triggerAtTime, type);
        // Try to prevent spamming by making sure we aren't firing alarms in the immediate future
        final long minTrigger = nowElapsed + mConstants.MIN_FUTURITY;
        final long triggerElapsed = (nominalTrigger > minTrigger) ? nominalTrigger : minTrigger;

        long maxElapsed;
        if (mSupportAlarmGrouping && (mAmPlus != null)) {
            // M: BG powerSaving feature
            maxElapsed = mAmPlus.getMaxTriggerTime(type, triggerElapsed, windowLength, interval, operation, mAlarmMode, true);
            if (maxElapsed < 0) {
                maxElapsed = 0 - maxElapsed;
                mNeedGrouping = false;
            } else {
                mNeedGrouping = true;
                //isStandalone = false; //ALPS02190343
            }
        } else if (windowLength == AlarmManager.WINDOW_EXACT) {
            maxElapsed = triggerElapsed;
        } else if (windowLength < 0) {
            maxElapsed = maxTriggerTime(nowElapsed, triggerElapsed, interval);
            // Fix this window in place, so that as time approaches we don't collapse it.
            windowLength = maxElapsed - triggerElapsed;
        } else {
            maxElapsed = triggerElapsed + windowLength;
        }

        synchronized (mLock) {
            if (true) {
                if (operation == null) {
                    Slog.v(TAG, "APP set with listener(" + listenerTag + ") : type=" + type
                        + " triggerAtTime=" + triggerAtTime + " win=" + windowLength
                        + " tElapsed=" + triggerElapsed + " maxElapsed=" + maxElapsed
                        + " interval=" + interval + " flags=0x" + Integer.toHexString(flags));
                } else {
                    Slog.v(TAG, "APP set(" + operation + ") : type=" + type
                        + " triggerAtTime=" + triggerAtTime + " win=" + windowLength
                        + " tElapsed=" + triggerElapsed + " maxElapsed=" + maxElapsed
                        + " interval=" + interval + " flags=0x" + Integer.toHexString(flags));
                }
            }
            // Intercept wake up alarms. prize-linkh-20160318
            final int originalType = type;
            final StoreState tempState = new StoreState();
            if(PrizeOption.PRIZE_INTERCEPT_WAKEUP_ALARMS) {
                //If this alarm has an alam clock info, we will ignore intercepting it.
                if(!mIgnoreWakeupAlarmWithAlarmClockInfo || alarmClock == null) {
                    type = convertWakeupAlarmToNonWakeup(type, operation, directReceiver, callingPackage, tempState, "setImpl");
                }
            }
            //end...        
            setImplLocked(type, triggerAtTime, triggerElapsed, windowLength, maxElapsed,
                    interval, operation, directReceiver, listenerTag, flags, true, workSource,
                    alarmClock, callingUid, callingPackage, mNeedGrouping,
                    /* Intercept wake up alarms. prize-linkh-20160602*/
                    originalType, tempState.fromSystemApp, tempState.hasRetrievedPackageInfo, tempState.ignoreIntercepted);//end...
        }
    }

    private void setImplLocked(int type, long when, long whenElapsed, long windowLength,
            long maxWhen, long interval, PendingIntent operation, IAlarmListener directReceiver,
            String listenerTag, int flags, boolean doValidate, WorkSource workSource,
            AlarmManager.AlarmClockInfo alarmClock, int callingUid, String callingPackage,
            boolean mNeedGrouping,
            /* Intercept wake up alarms. prize-linkh-20160602*/
            int originalType, boolean fromSystemApp, boolean hasRetrievedPackageInfo, boolean ignoreIntercepted) {//end...
        Alarm a = new Alarm(type, when, whenElapsed, windowLength, maxWhen, interval,
                operation, directReceiver, listenerTag, workSource, flags, alarmClock,
                callingUid, callingPackage, mNeedGrouping,
                /* Intercept wake up alarms. prize-linkh-20160602*/
                originalType, fromSystemApp, hasRetrievedPackageInfo, ignoreIntercepted);//end...
        try {
            if (ActivityManagerNative.getDefault().getAppStartMode(callingUid, callingPackage)
                    == ActivityManager.APP_START_MODE_DISABLED) {
                Slog.w(TAG, "Not setting alarm from " + callingUid + ":" + a
                        + " -- package not allowed to start");
                return;
            }
        } catch (RemoteException e) {
        }

        removeLocked(operation, directReceiver);
        setImplLocked(a, false, doValidate);
    }

    private void setImplLocked(Alarm a, boolean rebatching, boolean doValidate) {
        if ((a.flags&AlarmManager.FLAG_IDLE_UNTIL) != 0) {
            // This is a special alarm that will put the system into idle until it goes off.
            // The caller has given the time they want this to happen at, however we need
            // to pull that earlier if there are existing alarms that have requested to
            // bring us out of idle at an earlier time.
            if (mNextWakeFromIdle != null && a.whenElapsed > mNextWakeFromIdle.whenElapsed) {
                a.when = a.whenElapsed = a.maxWhenElapsed = mNextWakeFromIdle.whenElapsed;
            }
            // Add fuzz to make the alarm go off some time before the actual desired time.
            final long nowElapsed = SystemClock.elapsedRealtime();
            final int fuzz = fuzzForDuration(a.whenElapsed-nowElapsed);
            if (fuzz > 0) {
                if (mRandom == null) {
                    mRandom = new Random();
                }
                final int delta = mRandom.nextInt(fuzz);
                a.whenElapsed -= delta;
                if (false) {
                    Slog.d(TAG, "Alarm when: " + a.whenElapsed);
                    Slog.d(TAG, "Delta until alarm: " + (a.whenElapsed-nowElapsed));
                    Slog.d(TAG, "Applied fuzz: " + fuzz);
                    Slog.d(TAG, "Final delta: " + delta);
                    Slog.d(TAG, "Final when: " + a.whenElapsed);
                }
                a.when = a.maxWhenElapsed = a.whenElapsed;
            }

        } else if (mPendingIdleUntil != null) {
            // We currently have an idle until alarm scheduled; if the new alarm has
            // not explicitly stated it wants to run while idle, then put it on hold.
            if ((a.flags&(AlarmManager.FLAG_ALLOW_WHILE_IDLE
                    | AlarmManager.FLAG_ALLOW_WHILE_IDLE_UNRESTRICTED
                    | AlarmManager.FLAG_WAKE_FROM_IDLE))
                    == 0) {
                mPendingWhileIdleAlarms.add(a);
                return;
            }
        }

        if (RECORD_DEVICE_IDLE_ALARMS) {
            if ((a.flags & AlarmManager.FLAG_ALLOW_WHILE_IDLE) != 0) {
                IdleDispatchEntry ent = new IdleDispatchEntry();
                ent.uid = a.uid;
                ent.pkg = a.operation.getCreatorPackage();
                ent.tag = a.operation.getTag("");
                ent.op = "SET";
                ent.elapsedRealtime = SystemClock.elapsedRealtime();
                ent.argRealtime = a.whenElapsed;
                mAllowWhileIdleDispatches.add(ent);
            }
        }

        if (DEBUG_BATCH) {
             Slog.d(TAG, "a.whenElapsed =" + a.whenElapsed
             + " a.needGrouping= " + a.needGrouping
             + "  a.flags= " + a.flags);
        }
        int whichBatch = 0;
        if (mSupportAlarmGrouping && (mAmPlus != null)) {
             // M using a.needGrouping for check condition
             // a.needGrouping is false -> run default flow
             // a.needGrouping is true  -> run find batch flow
             if (a.needGrouping == false) {
                 whichBatch = ((a.flags & AlarmManager.FLAG_STANDALONE) != 0)
                     ? -1 : attemptCoalesceLocked(a.whenElapsed, a.maxWhenElapsed);
             }
             else {
                 whichBatch = attemptCoalesceLocked(a.whenElapsed, a.maxWhenElapsed);
             }
        } else {
             Slog.d(TAG, "default path for whichBatch");
             whichBatch = ((a.flags & AlarmManager.FLAG_STANDALONE) != 0)
                 ? -1 : attemptCoalesceLocked(a.whenElapsed, a.maxWhenElapsed);
        }
        //if ((DEBUG_BATCH) || (Build.TYPE.equals("eng"))) {
        if (DEBUG_BATCH) {
            Slog.d(TAG, " whichBatch = " + whichBatch);
        }
        if (whichBatch < 0) {
            Batch batch = new Batch(a);
            addBatchLocked(mAlarmBatches, batch);
        } else {
            Batch batch = mAlarmBatches.get(whichBatch);
            if (DEBUG_BATCH) {
                Slog.d(TAG, " alarm = " + a + " add to " + batch);
            }
            if (batch.add(a)) {
                // The start time of this batch advanced, so batch ordering may
                // have just been broken.  Move it to where it now belongs.
                mAlarmBatches.remove(whichBatch);
                addBatchLocked(mAlarmBatches, batch);
            }
        }

        if (a.alarmClock != null) {
            mNextAlarmClockMayChange = true;
        }

        boolean needRebatch = false;

        if ((a.flags&AlarmManager.FLAG_IDLE_UNTIL) != 0) {
            if (RECORD_DEVICE_IDLE_ALARMS) {
                if (mPendingIdleUntil == null) {
                    IdleDispatchEntry ent = new IdleDispatchEntry();
                    ent.uid = 0;
                    ent.pkg = "START IDLE";
                    ent.elapsedRealtime = SystemClock.elapsedRealtime();
                    mAllowWhileIdleDispatches.add(ent);
                }
            }
            mPendingIdleUntil = a;
            mConstants.updateAllowWhileIdleMinTimeLocked();
            needRebatch = true;
        } else if ((a.flags&AlarmManager.FLAG_WAKE_FROM_IDLE) != 0) {
            if (mNextWakeFromIdle == null || mNextWakeFromIdle.whenElapsed > a.whenElapsed) {
                mNextWakeFromIdle = a;
                // If this wake from idle is earlier than whatever was previously scheduled,
                // and we are currently idling, then we need to rebatch alarms in case the idle
                // until time needs to be updated.
                if (mPendingIdleUntil != null) {
                    needRebatch = true;
                }
            }
        }

        if (!rebatching) {
            if (DEBUG_VALIDATE) {
                if (doValidate && !validateConsistencyLocked()) {
                    Slog.v(TAG, "Tipping-point operation: type=" + a.type + " when=" + a.when
                            + " when(hex)=" + Long.toHexString(a.when)
                            + " whenElapsed=" + a.whenElapsed
                            + " maxWhenElapsed=" + a.maxWhenElapsed
                            + " interval=" + a.repeatInterval + " op=" + a.operation
                            + " flags=0x" + Integer.toHexString(a.flags));
                    rebatchAllAlarmsLocked(false);
                    needRebatch = false;
                }
            }

            if (needRebatch) {
                rebatchAllAlarmsLocked(false);
            }

            rescheduleKernelAlarmsLocked();
            updateNextAlarmClockLocked();
        }
    }

    private final IBinder mService = new IAlarmManager.Stub() {
        @Override
        public void set(String callingPackage,
                int type, long triggerAtTime, long windowLength, long interval, int flags,
                PendingIntent operation, IAlarmListener directReceiver, String listenerTag,
                WorkSource workSource, AlarmManager.AlarmClockInfo alarmClock) {
            final int callingUid = Binder.getCallingUid();

            // make sure the caller is not lying about which package should be blamed for
            // wakelock time spent in alarm delivery
            mAppOps.checkPackage(callingUid, callingPackage);

            // Repeating alarms must use PendingIntent, not direct listener
            if (interval != 0) {
                if (directReceiver != null) {
                    throw new IllegalArgumentException("Repeating alarms cannot use AlarmReceivers");
                }
            }

            if (workSource != null) {
                getContext().enforcePermission(
                        android.Manifest.permission.UPDATE_DEVICE_STATS,
                        Binder.getCallingPid(), callingUid, "AlarmManager.set");
            }

            // No incoming callers can request either WAKE_FROM_IDLE or
            // ALLOW_WHILE_IDLE_UNRESTRICTED -- we will apply those later as appropriate.
            flags &= ~(AlarmManager.FLAG_WAKE_FROM_IDLE
                    | AlarmManager.FLAG_ALLOW_WHILE_IDLE_UNRESTRICTED);

            // Only the system can use FLAG_IDLE_UNTIL -- this is used to tell the alarm
            // manager when to come out of idle mode, which is only for DeviceIdleController.
            if (callingUid != Process.SYSTEM_UID) {
                flags &= ~AlarmManager.FLAG_IDLE_UNTIL;
            }

            // If this is an exact time alarm, then it can't be batched with other alarms.
            if (windowLength == AlarmManager.WINDOW_EXACT) {
                flags |= AlarmManager.FLAG_STANDALONE;
            }

            // If this alarm is for an alarm clock, then it must be standalone and we will
            // use it to wake early from idle if needed.
            if (alarmClock != null) {
                flags |= AlarmManager.FLAG_WAKE_FROM_IDLE | AlarmManager.FLAG_STANDALONE;

            // If the caller is a core system component or on the user's whitelist, and not calling
            // to do work on behalf of someone else, then always set ALLOW_WHILE_IDLE_UNRESTRICTED.
            // This means we will allow these alarms to go off as normal even while idle, with no
            // timing restrictions.
            } else if (workSource == null && (callingUid < Process.FIRST_APPLICATION_UID
                    || Arrays.binarySearch(mDeviceIdleUserWhitelist,
                            UserHandle.getAppId(callingUid)) >= 0)) {
                flags |= AlarmManager.FLAG_ALLOW_WHILE_IDLE_UNRESTRICTED;
                flags &= ~AlarmManager.FLAG_ALLOW_WHILE_IDLE;
            }

            setImpl(type, triggerAtTime, windowLength, interval, operation, directReceiver,
                    listenerTag, flags, workSource, alarmClock, callingUid, callingPackage);
        }

        @Override
        public boolean setTime(long millis) {
            getContext().enforceCallingOrSelfPermission(
                    "android.permission.SET_TIME",
                    "setTime");

            if (mNativeData == 0 || mNativeData == -1) {
                Slog.w(TAG, "Not setting time since no alarm driver is available.");
                return false;
            }

            synchronized (mLock) {
                Slog.d(TAG, "setKernelTime  setTime = " + millis);
                return setKernelTime(mNativeData, millis) == 0;
            }
        }

        @Override
        public void setTimeZone(String tz) {
            getContext().enforceCallingOrSelfPermission(
                    "android.permission.SET_TIME_ZONE",
                    "setTimeZone");

            final long oldId = Binder.clearCallingIdentity();
            try {
                setTimeZoneImpl(tz);
            } finally {
                Binder.restoreCallingIdentity(oldId);
            }
        }

        @Override
        public void remove(PendingIntent operation, IAlarmListener listener) {
            if (operation == null && listener == null) {
                Slog.w(TAG, "remove() with no intent or listener");
                return;
            }
            synchronized (mLock) {
                if(DEBUG_ALARM_CLOCK){
                    Slog.d(TAG, "manual remove option = " + operation);
                }
                removeLocked(operation, listener);
            }
        }

        @Override
        public long getNextWakeFromIdleTime() {
            return getNextWakeFromIdleTimeImpl();
        }

        @Override
        public void cancelPoweroffAlarm(String name) {
            cancelPoweroffAlarmImpl(name);

        }

        @Override
        public void removeFromAms(String packageName) {
            removeFromAmsImpl(packageName);
        }

        @Override
        public boolean lookForPackageFromAms(String packageName) {
            return lookForPackageFromAmsImpl(packageName);
        }

        @Override
        public AlarmManager.AlarmClockInfo getNextAlarmClock(int userId) {

            userId = ActivityManager.handleIncomingUser(Binder.getCallingPid(),
                    Binder.getCallingUid(), userId, false /* allowAll */, false /* requireFull */,
                    "getNextAlarmClock", null);

            return getNextAlarmClockImpl(userId);
        }

        @Override
        protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
            if (getContext().checkCallingOrSelfPermission(android.Manifest.permission.DUMP)
                    != PackageManager.PERMISSION_GRANTED) {
                pw.println("Permission Denial: can't dump AlarmManager from from pid="
                        + Binder.getCallingPid()
                        + ", uid=" + Binder.getCallingUid());
                return;
            }
            /// M: Dynamically enable alarmManager logs @{
            int opti = 0;
            while (opti < args.length) {
                String opt = args[opti];
                if (opt == null || opt.length() <= 0 || opt.charAt(0) != '-') {
                    break;
                }
                opti++;
                if ("-h".equals(opt)) {
                    pw.println("alarm manager dump options:");
                    pw.println("  log  [on/off]");
                    pw.println("  Example:");
                    pw.println("  $adb shell dumpsys alarm log on");
                    pw.println("  $adb shell dumpsys alarm log off");
                    return;
                } else {
                    pw.println("Unknown argument: " + opt + "; use -h for help");
                }
            }

            if (opti < args.length) {
                String cmd = args[opti];
                opti++;
                 if ("log".equals(cmd)) {
                    configLogTag(pw, args, opti);
                    return;
                }
            }

            dumpImpl(pw, args);
        }
    };

    public final class LocalService {
        public void setDeviceIdleUserWhitelist(int[] appids) {
            setDeviceIdleUserWhitelistImpl(appids);
        }

        // Support controlling app network for sleeping. prize-linkh-20161111
        // Prize app network manager service need to get intercepted wakeup alarm list data 
        // through this local service.
        public ArraySet<String> getInterceptedPkgList() {
            if (mInterceptPkgsMap == null) {
                return null;
            }
            
            ArraySet<String> data = null;
            for (int i = 0; i < mInterceptPkgsMap.size(); ++i) {
                if(data == null) {
                    data = new ArraySet<>();
                }
                data.add(mInterceptPkgsMap.keyAt(i));
            }
            return data;
        } //END....

    }

     /// M:Add dynamic enable alarmManager log @{
    protected void configLogTag(PrintWriter pw, String[] args, int opti) {

        if (opti >= args.length) {
            pw.println("  Invalid argument!");
        } else {
            if ("on".equals(args[opti])) {
                localLOGV = true;
                DEBUG_BATCH = true;
                DEBUG_VALIDATE = true;
            } else if ("off".equals(args[opti])) {
                localLOGV = false;
                DEBUG_BATCH = false;
                DEBUG_VALIDATE = false;
            } else if ("0".equals(args[opti])) {
                mAlarmMode = 0;
                Slog.v(TAG, "mAlarmMode = " + mAlarmMode);
            } else if ("1".equals(args[opti])) {
                mAlarmMode = 1;
                Slog.v(TAG, "mAlarmMode = " + mAlarmMode);
            } else if ("2".equals(args[opti])) {
                mAlarmMode = 2;
                Slog.v(TAG, "mAlarmMode = " + mAlarmMode);
            } else {
                pw.println("  Invalid argument!");
            }
        }
    }
    /// @}


    void dumpImpl(PrintWriter pw, String[] args) {
    /// M: Dynamically enable alarmManager logs @{
    int opti = 0;
    while (opti < args.length) {
             String opt = args[opti];
             if (opt == null || opt.length() <= 0 || opt.charAt(0) != '-') {
        break;
             }
             opti++;
             if ("-h".equals(opt)) {
                 pw.println("alarm manager dump options:");
                 pw.println("  log  [on/off]");
                 pw.println("  Example:");
                 pw.println("  $adb shell dumpsys alarm log on");
                 pw.println("  $adb shell dumpsys alarm log off");
                 return;
             } else {
                 pw.println("Unknown argument: " + opt + "; use -h for help");
             }
        }

        if (opti < args.length) {
            String cmd = args[opti];
            opti++;
            if ("log".equals(cmd)) {
                configLogTag(pw, args, opti);
                return;
            }
        }
        /// @}
        synchronized (mLock) {
            pw.println("Current Alarm Manager state:");
            mConstants.dump(pw);
            pw.println();

            final long nowRTC = System.currentTimeMillis();
            final long nowELAPSED = SystemClock.elapsedRealtime();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

            pw.print("  nowRTC="); pw.print(nowRTC);
            pw.print("="); pw.print(sdf.format(new Date(nowRTC)));
            pw.print(" nowELAPSED="); pw.print(nowELAPSED);
            pw.println();
            pw.print("  mLastTimeChangeClockTime="); pw.print(mLastTimeChangeClockTime);
            pw.print("="); pw.println(sdf.format(new Date(mLastTimeChangeClockTime)));
            pw.print("  mLastTimeChangeRealtime=");
            TimeUtils.formatDuration(mLastTimeChangeRealtime, pw);
            pw.println();
            if (!mInteractive) {
                pw.print("  Time since non-interactive: ");
                TimeUtils.formatDuration(nowELAPSED - mNonInteractiveStartTime, pw);
                pw.println();
                pw.print("  Max wakeup delay: ");
                TimeUtils.formatDuration(currentNonWakeupFuzzLocked(nowELAPSED), pw);
                pw.println();
                pw.print("  Time since last dispatch: ");
                TimeUtils.formatDuration(nowELAPSED - mLastAlarmDeliveryTime, pw);
                pw.println();
                pw.print("  Next non-wakeup delivery time: ");
                TimeUtils.formatDuration(nowELAPSED - mNextNonWakeupDeliveryTime, pw);
                pw.println();
            }

            long nextWakeupRTC = mNextWakeup + (nowRTC - nowELAPSED);
            long nextNonWakeupRTC = mNextNonWakeup + (nowRTC - nowELAPSED);
            pw.print("  Next non-wakeup alarm: ");
                    TimeUtils.formatDuration(mNextNonWakeup, nowELAPSED, pw);
                    pw.print(" = "); pw.println(sdf.format(new Date(nextNonWakeupRTC)));
            pw.print("  Next wakeup: "); TimeUtils.formatDuration(mNextWakeup, nowELAPSED, pw);
                    pw.print(" = "); pw.println(sdf.format(new Date(nextWakeupRTC)));
            pw.print("  Last wakeup: "); TimeUtils.formatDuration(mLastWakeup, nowELAPSED, pw);
            pw.print(" set at "); TimeUtils.formatDuration(mLastWakeupSet, nowELAPSED, pw);
            pw.println();
            pw.print("  Num time change events: "); pw.println(mNumTimeChanged);
            pw.println("  mDeviceIdleUserWhitelist=" + Arrays.toString(mDeviceIdleUserWhitelist));

            pw.println();
            pw.println("  Next alarm clock information: ");
            final TreeSet<Integer> users = new TreeSet<>();
            for (int i = 0; i < mNextAlarmClockForUser.size(); i++) {
                users.add(mNextAlarmClockForUser.keyAt(i));
            }
            for (int i = 0; i < mPendingSendNextAlarmClockChangedForUser.size(); i++) {
                users.add(mPendingSendNextAlarmClockChangedForUser.keyAt(i));
            }
            for (int user : users) {
                final AlarmManager.AlarmClockInfo next = mNextAlarmClockForUser.get(user);
                final long time = next != null ? next.getTriggerTime() : 0;
                final boolean pendingSend = mPendingSendNextAlarmClockChangedForUser.get(user);
                pw.print("    user:"); pw.print(user);
                pw.print(" pendingSend:"); pw.print(pendingSend);
                pw.print(" time:"); pw.print(time);
                if (time > 0) {
                    pw.print(" = "); pw.print(sdf.format(new Date(time)));
                    pw.print(" = "); TimeUtils.formatDuration(time, nowRTC, pw);
                }
                pw.println();
            }
            if (mAlarmBatches.size() > 0) {
                pw.println();
                pw.print("  Pending alarm batches: ");
                pw.println(mAlarmBatches.size());
                for (Batch b : mAlarmBatches) {
                    pw.print(b); pw.println(':');
                    dumpAlarmList(pw, b.alarms, "    ", nowELAPSED, nowRTC, sdf);
                }
            }
            if (mPendingIdleUntil != null || mPendingWhileIdleAlarms.size() > 0) {
                pw.println();
                pw.println("    Idle mode state:");
                pw.print("      Idling until: ");
                if (mPendingIdleUntil != null) {
                    pw.println(mPendingIdleUntil);
                    mPendingIdleUntil.dump(pw, "        ", nowRTC, nowELAPSED, sdf);
                } else {
                    pw.println("null");
                }
                pw.println("      Pending alarms:");
                dumpAlarmList(pw, mPendingWhileIdleAlarms, "      ", nowELAPSED, nowRTC, sdf);
            }
            if (mNextWakeFromIdle != null) {
                pw.println();
                pw.print("  Next wake from idle: "); pw.println(mNextWakeFromIdle);
                mNextWakeFromIdle.dump(pw, "    ", nowRTC, nowELAPSED, sdf);
            }

            pw.println();
            pw.print("  Past-due non-wakeup alarms: ");
            if (mPendingNonWakeupAlarms.size() > 0) {
                pw.println(mPendingNonWakeupAlarms.size());
                dumpAlarmList(pw, mPendingNonWakeupAlarms, "    ", nowELAPSED, nowRTC, sdf);
            } else {
                pw.println("(none)");
            }
            pw.print("    Number of delayed alarms: "); pw.print(mNumDelayedAlarms);
            pw.print(", total delay time: "); TimeUtils.formatDuration(mTotalDelayTime, pw);
            pw.println();
            pw.print("    Max delay time: "); TimeUtils.formatDuration(mMaxDelayTime, pw);
            pw.print(", max non-interactive time: ");
            TimeUtils.formatDuration(mNonInteractiveTime, pw);
            pw.println();

            pw.println();
            pw.print("  Broadcast ref count: "); pw.println(mBroadcastRefCount);
            pw.println();

            if (mInFlight.size() > 0) {
                pw.println("Outstanding deliveries:");
                for (int i = 0; i < mInFlight.size(); i++) {
                    pw.print("   #"); pw.print(i); pw.print(": ");
                    pw.println(mInFlight.get(i));
                }
                pw.println();
            }

            pw.print("  mAllowWhileIdleMinTime=");
            TimeUtils.formatDuration(mAllowWhileIdleMinTime, pw);
            pw.println();
            if (mLastAllowWhileIdleDispatch.size() > 0) {
                pw.println("  Last allow while idle dispatch times:");
                for (int i=0; i<mLastAllowWhileIdleDispatch.size(); i++) {
                    pw.print("  UID ");
                    UserHandle.formatUid(pw, mLastAllowWhileIdleDispatch.keyAt(i));
                    pw.print(": ");
                    TimeUtils.formatDuration(mLastAllowWhileIdleDispatch.valueAt(i),
                            nowELAPSED, pw);
                    pw.println();
                }
            }
            pw.println();

            if (mLog.dump(pw, "  Recent problems", "    ")) {
                pw.println();
            }

            final FilterStats[] topFilters = new FilterStats[10];
            final Comparator<FilterStats> comparator = new Comparator<FilterStats>() {
                @Override
                public int compare(FilterStats lhs, FilterStats rhs) {
                    if (lhs.aggregateTime < rhs.aggregateTime) {
                        return 1;
                    } else if (lhs.aggregateTime > rhs.aggregateTime) {
                        return -1;
                    }
                    return 0;
                }
            };
            int len = 0;
            for (int iu=0; iu<mBroadcastStats.size(); iu++) {
                ArrayMap<String, BroadcastStats> uidStats = mBroadcastStats.valueAt(iu);
                for (int ip=0; ip<uidStats.size(); ip++) {
                    BroadcastStats bs = uidStats.valueAt(ip);
                    for (int is=0; is<bs.filterStats.size(); is++) {
                        FilterStats fs = bs.filterStats.valueAt(is);
                        int pos = len > 0
                                ? Arrays.binarySearch(topFilters, 0, len, fs, comparator) : 0;
                        if (pos < 0) {
                            pos = -pos - 1;
                        }
                        if (pos < topFilters.length) {
                            int copylen = topFilters.length - pos - 1;
                            if (copylen > 0) {
                                System.arraycopy(topFilters, pos, topFilters, pos+1, copylen);
                            }
                            topFilters[pos] = fs;
                            if (len < topFilters.length) {
                                len++;
                            }
                        }
                    }
                }
            }
            if (len > 0) {
                pw.println("  Top Alarms:");
                for (int i=0; i<len; i++) {
                    FilterStats fs = topFilters[i];
                    pw.print("    ");
                    if (fs.nesting > 0) pw.print("*ACTIVE* ");
                    TimeUtils.formatDuration(fs.aggregateTime, pw);
                    pw.print(" running, "); pw.print(fs.numWakeup);
                    pw.print(" wakeups, "); pw.print(fs.count);
                    pw.print(" alarms: "); UserHandle.formatUid(pw, fs.mBroadcastStats.mUid);
                    pw.print(":"); pw.print(fs.mBroadcastStats.mPackageName);
                    pw.println();
                    pw.print("      "); pw.print(fs.mTag);
                    pw.println();
                }
            }

            pw.println(" ");
            pw.println("  Alarm Stats:");
            final ArrayList<FilterStats> tmpFilters = new ArrayList<FilterStats>();
            for (int iu=0; iu<mBroadcastStats.size(); iu++) {
                ArrayMap<String, BroadcastStats> uidStats = mBroadcastStats.valueAt(iu);
                for (int ip=0; ip<uidStats.size(); ip++) {
                    BroadcastStats bs = uidStats.valueAt(ip);
                    pw.print("  ");
                    if (bs.nesting > 0) pw.print("*ACTIVE* ");
                    UserHandle.formatUid(pw, bs.mUid);
                    pw.print(":");
                    pw.print(bs.mPackageName);
                    pw.print(" "); TimeUtils.formatDuration(bs.aggregateTime, pw);
                            pw.print(" running, "); pw.print(bs.numWakeup);
                            pw.println(" wakeups:");
                    tmpFilters.clear();
                    for (int is=0; is<bs.filterStats.size(); is++) {
                        tmpFilters.add(bs.filterStats.valueAt(is));
                    }
                    Collections.sort(tmpFilters, comparator);
                    for (int i=0; i<tmpFilters.size(); i++) {
                        FilterStats fs = tmpFilters.get(i);
                        pw.print("    ");
                                if (fs.nesting > 0) pw.print("*ACTIVE* ");
                                TimeUtils.formatDuration(fs.aggregateTime, pw);
                                pw.print(" "); pw.print(fs.numWakeup);
                                pw.print(" wakes " ); pw.print(fs.count);
                                pw.print(" alarms, last ");
                                TimeUtils.formatDuration(fs.lastTime, nowELAPSED, pw);
                                pw.println(":");
                        pw.print("      ");
                                pw.print(fs.mTag);
                                pw.println();
                    }
                }
            }

            if (RECORD_DEVICE_IDLE_ALARMS) {
                pw.println();
                pw.println("  Allow while idle dispatches:");
                for (int i = 0; i < mAllowWhileIdleDispatches.size(); i++) {
                    IdleDispatchEntry ent = mAllowWhileIdleDispatches.get(i);
                    pw.print("    ");
                    TimeUtils.formatDuration(ent.elapsedRealtime, nowELAPSED, pw);
                    pw.print(": ");
                    UserHandle.formatUid(pw, ent.uid);
                    pw.print(":");
                    pw.println(ent.pkg);
                    if (ent.op != null) {
                        pw.print("      ");
                        pw.print(ent.op);
                        pw.print(" / ");
                        pw.print(ent.tag);
                        if (ent.argRealtime != 0) {
                            pw.print(" (");
                            TimeUtils.formatDuration(ent.argRealtime, nowELAPSED, pw);
                            pw.print(")");
                        }
                        pw.println();
                    }
                }
            }

            if (WAKEUP_STATS) {
                pw.println();
                pw.println("  Recent Wakeup History:");
                long last = -1;
                for (WakeupEvent event : mRecentWakeups) {
                    pw.print("    "); pw.print(sdf.format(new Date(event.when)));
                    pw.print('|');
                    if (last < 0) {
                        pw.print('0');
                    } else {
                        pw.print(event.when - last);
                    }
                    last = event.when;
                    pw.print('|'); pw.print(event.uid);
                    pw.print('|'); pw.print(event.action);
                    pw.println();
                }
                pw.println();
            }
        }
    }

    private void logBatchesLocked(SimpleDateFormat sdf) {
        ByteArrayOutputStream bs = new ByteArrayOutputStream(2048);
        PrintWriter pw = new PrintWriter(bs);
        final long nowRTC = System.currentTimeMillis();
        final long nowELAPSED = SystemClock.elapsedRealtime();
        final int NZ = mAlarmBatches.size();
        for (int iz = 0; iz < NZ; iz++) {
            Batch bz = mAlarmBatches.get(iz);
            pw.append("Batch "); pw.print(iz); pw.append(": "); pw.println(bz);
            dumpAlarmList(pw, bz.alarms, "  ", nowELAPSED, nowRTC, sdf);
            pw.flush();
            Slog.v(TAG, bs.toString());
            bs.reset();
        }
    }

    private boolean validateConsistencyLocked() {
        if (DEBUG_VALIDATE) {
            long lastTime = Long.MIN_VALUE;
            final int N = mAlarmBatches.size();
            for (int i = 0; i < N; i++) {
                Batch b = mAlarmBatches.get(i);
                if (b.start >= lastTime) {
                    // duplicate start times are okay because of standalone batches
                    lastTime = b.start;
                } else {
                    Slog.e(TAG, "CONSISTENCY FAILURE: Batch " + i + " is out of order");
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    logBatchesLocked(sdf);
                    return false;
                }
            }
        }
        return true;
    }

    private Batch findFirstWakeupBatchLocked() {
        final int N = mAlarmBatches.size();
        for (int i = 0; i < N; i++) {
            Batch b = mAlarmBatches.get(i);
            if (b.hasWakeups()) {
                return b;
            }
        }
        return null;
    }

    long getNextWakeFromIdleTimeImpl() {
        synchronized (mLock) {
            return mNextWakeFromIdle != null ? mNextWakeFromIdle.whenElapsed : Long.MAX_VALUE;
        }
    }

    void setDeviceIdleUserWhitelistImpl(int[] appids) {
        synchronized (mLock) {
            mDeviceIdleUserWhitelist = appids;
        }
    }

    AlarmManager.AlarmClockInfo getNextAlarmClockImpl(int userId) {
        Slog.d(TAG, "getNextAlarmClockImpl is called before Lock ");
        synchronized (mLock) {
            Slog.d(TAG, "getNextAlarmClockImpl is called in Lock ");
            return mNextAlarmClockForUser.get(userId);
        }
    }

    /**
     * Recomputes the next alarm clock for all users.
     */
    private void updateNextAlarmClockLocked() {
        if (!mNextAlarmClockMayChange) {
            return;
        }
        mNextAlarmClockMayChange = false;

        SparseArray<AlarmManager.AlarmClockInfo> nextForUser = mTmpSparseAlarmClockArray;
        nextForUser.clear();

        final int N = mAlarmBatches.size();
        for (int i = 0; i < N; i++) {
            ArrayList<Alarm> alarms = mAlarmBatches.get(i).alarms;
            final int M = alarms.size();

            for (int j = 0; j < M; j++) {
                Alarm a = alarms.get(j);
                if (a.alarmClock != null) {
                    final int userId = UserHandle.getUserId(a.uid);
                    AlarmManager.AlarmClockInfo current = mNextAlarmClockForUser.get(userId);

                    if (DEBUG_ALARM_CLOCK) {
                        Log.v(TAG, "Found AlarmClockInfo " + a.alarmClock + " at " +
                                formatNextAlarm(getContext(), a.alarmClock, userId) +
                                " for user " + userId);
                    }

                    // Alarms and batches are sorted by time, no need to compare times here.
                    if (nextForUser.get(userId) == null) {
                        nextForUser.put(userId, a.alarmClock);
                    } else if (a.alarmClock.equals(current)
                            && current.getTriggerTime() <= nextForUser.get(userId).getTriggerTime()) {
                        // same/earlier time and it's the one we cited before, so stick with it
                        nextForUser.put(userId, current);
                    }
                }
            }
        }

        // Update mNextAlarmForUser with new values.
        final int NN = nextForUser.size();
        for (int i = 0; i < NN; i++) {
            AlarmManager.AlarmClockInfo newAlarm = nextForUser.valueAt(i);
            int userId = nextForUser.keyAt(i);
            AlarmManager.AlarmClockInfo currentAlarm = mNextAlarmClockForUser.get(userId);
            if (!newAlarm.equals(currentAlarm)) {
                updateNextAlarmInfoForUserLocked(userId, newAlarm);
            }
        }

        // Remove users without any alarm clocks scheduled.
        final int NNN = mNextAlarmClockForUser.size();
        for (int i = NNN - 1; i >= 0; i--) {
            int userId = mNextAlarmClockForUser.keyAt(i);
            if (nextForUser.get(userId) == null) {
                updateNextAlarmInfoForUserLocked(userId, null);
            }
        }
    }

    private void updateNextAlarmInfoForUserLocked(int userId,
            AlarmManager.AlarmClockInfo alarmClock) {
        if (alarmClock != null) {
            if (DEBUG_ALARM_CLOCK) {
                Log.v(TAG, "Next AlarmClockInfoForUser(" + userId + "): " +
                        formatNextAlarm(getContext(), alarmClock, userId));
            }
            mNextAlarmClockForUser.put(userId, alarmClock);
        } else {
            if (DEBUG_ALARM_CLOCK) {
                Log.v(TAG, "Next AlarmClockInfoForUser(" + userId + "): None");
            }
            mNextAlarmClockForUser.remove(userId);
        }

        mPendingSendNextAlarmClockChangedForUser.put(userId, true);
        mHandler.removeMessages(AlarmHandler.SEND_NEXT_ALARM_CLOCK_CHANGED);
        mHandler.sendEmptyMessage(AlarmHandler.SEND_NEXT_ALARM_CLOCK_CHANGED);
    }

    /**
     * Updates NEXT_ALARM_FORMATTED and sends NEXT_ALARM_CLOCK_CHANGED_INTENT for all users
     * for which alarm clocks have changed since the last call to this.
     *
     * Do not call with a lock held. Only call from mHandler's thread.
     *
     * @see AlarmHandler#SEND_NEXT_ALARM_CLOCK_CHANGED
     */
    private void sendNextAlarmClockChanged() {
        SparseArray<AlarmManager.AlarmClockInfo> pendingUsers = mHandlerSparseAlarmClockArray;
        pendingUsers.clear();

        Slog.w(TAG, "sendNextAlarmClockChanged begin");
        synchronized (mLock) {
            final int N  = mPendingSendNextAlarmClockChangedForUser.size();
            for (int i = 0; i < N; i++) {
                int userId = mPendingSendNextAlarmClockChangedForUser.keyAt(i);
                pendingUsers.append(userId, mNextAlarmClockForUser.get(userId));
            }
            mPendingSendNextAlarmClockChangedForUser.clear();
        }

        final int N = pendingUsers.size();
        for (int i = 0; i < N; i++) {
            int userId = pendingUsers.keyAt(i);
            AlarmManager.AlarmClockInfo alarmClock = pendingUsers.valueAt(i);
            Settings.System.putStringForUser(getContext().getContentResolver(),
                    Settings.System.NEXT_ALARM_FORMATTED,
                    formatNextAlarm(getContext(), alarmClock, userId),
                    userId);

            getContext().sendBroadcastAsUser(NEXT_ALARM_CLOCK_CHANGED_INTENT,
                    new UserHandle(userId));
        }
        Slog.w(TAG, "sendNextAlarmClockChanged end");
    }

    /**
     * Formats an alarm like platform/packages/apps/DeskClock used to.
     */
    private static String formatNextAlarm(final Context context, AlarmManager.AlarmClockInfo info,
            int userId) {
        String skeleton = DateFormat.is24HourFormat(context, userId) ? "EHm" : "Ehma";
        String pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton);
        return (info == null) ? "" :
                DateFormat.format(pattern, info.getTriggerTime()).toString();
    }

    void rescheduleKernelAlarmsLocked() {
        // Schedule the next upcoming wakeup alarm.  If there is a deliverable batch
        // prior to that which contains no wakeups, we schedule that as well.

    // /M:add for IPO feature,do not set alarm when shut down,@{
    if (mIPOShutdown && (mNativeData == -1)) {
        Slog.w(TAG, "IPO Shutdown so drop the repeating alarm");
        return;
    }
    // /@}

        long nextNonWakeup = 0;
        if (mAlarmBatches.size() > 0) {
            final Batch firstWakeup = findFirstWakeupBatchLocked();
            final Batch firstBatch = mAlarmBatches.get(0);
            // always update the kernel alarms, as a backstop against missed wakeups
            if (firstWakeup != null && mNextWakeup != firstWakeup.start) {
                mNextWakeup = firstWakeup.start;
                mLastWakeupSet = SystemClock.elapsedRealtime();
                setLocked(ELAPSED_REALTIME_WAKEUP, firstWakeup.start);
            }
            if (firstBatch != firstWakeup) {
                nextNonWakeup = firstBatch.start;
            }
        }
        if (mPendingNonWakeupAlarms.size() > 0) {
            if (nextNonWakeup == 0 || mNextNonWakeupDeliveryTime < nextNonWakeup) {
                nextNonWakeup = mNextNonWakeupDeliveryTime;
            }
        }
        // always update the kernel alarm, as a backstop against missed wakeups
        if (nextNonWakeup != 0 && mNextNonWakeup != nextNonWakeup) {
            mNextNonWakeup = nextNonWakeup;
            setLocked(ELAPSED_REALTIME, nextNonWakeup);
        }
    }

    private void removeLocked(PendingIntent operation, IAlarmListener directReceiver) {
        boolean didRemove = false;
        for (int i = mAlarmBatches.size() - 1; i >= 0; i--) {
            Batch b = mAlarmBatches.get(i);
            didRemove |= b.remove(operation, directReceiver);
            if (b.size() == 0) {
                mAlarmBatches.remove(i);
            }
        }
        for (int i = mPendingWhileIdleAlarms.size() - 1; i >= 0; i--) {
            if (mPendingWhileIdleAlarms.get(i).matches(operation, directReceiver)) {
                // Don't set didRemove, since this doesn't impact the scheduled alarms.
                mPendingWhileIdleAlarms.remove(i);
            }
        }

        if (didRemove) {
            if (true) {
                Slog.d(TAG, "remove(operation) changed bounds; rebatching operation = " + operation);
            }
            boolean restorePending = false;
            if (mPendingIdleUntil != null && mPendingIdleUntil.matches(operation, directReceiver)) {
                mPendingIdleUntil = null;
                restorePending = true;
            }
            if (mNextWakeFromIdle != null && mNextWakeFromIdle.matches(operation, directReceiver)) {
                mNextWakeFromIdle = null;
            }
            ///M: fix too much batch issue
            if (mAlarmBatches.size() < 300) {
                rebatchAllAlarmsLocked(true);
            } else {
                Slog.d(TAG, "mAlarmBatches.size() is larger than 300 , do not rebatch");
            }
           ///M:end
            if (restorePending) {
                restorePendingWhileIdleAlarmsLocked();
            }
            updateNextAlarmClockLocked();
        }
    }

    void removeLocked(String packageName) {
        boolean didRemove = false;
        for (int i = mAlarmBatches.size() - 1; i >= 0; i--) {
            Batch b = mAlarmBatches.get(i);
            didRemove |= b.remove(packageName);
            if (b.size() == 0) {
                mAlarmBatches.remove(i);
            }
        }
        for (int i = mPendingWhileIdleAlarms.size() - 1; i >= 0; i--) {
            final Alarm a = mPendingWhileIdleAlarms.get(i);
            if (a.matches(packageName)) {
                // Don't set didRemove, since this doesn't impact the scheduled alarms.
                mPendingWhileIdleAlarms.remove(i);
            }
        }

        if (didRemove) {
            if (true) {
                Slog.v(TAG, "remove(package) changed bounds; rebatching");
            }
            rebatchAllAlarmsLocked(true);
            rescheduleKernelAlarmsLocked();
            updateNextAlarmClockLocked();
        }
    }

    void removeForStoppedLocked(int uid) {
        boolean didRemove = false;
        for (int i = mAlarmBatches.size() - 1; i >= 0; i--) {
            Batch b = mAlarmBatches.get(i);
            didRemove |= b.removeForStopped(uid);
            if (b.size() == 0) {
                mAlarmBatches.remove(i);
            }
        }
        for (int i = mPendingWhileIdleAlarms.size() - 1; i >= 0; i--) {
            final Alarm a = mPendingWhileIdleAlarms.get(i);
            try {
                if (a.uid == uid && ActivityManagerNative.getDefault().getAppStartMode(
                        uid, a.packageName) == ActivityManager.APP_START_MODE_DISABLED) {
                    // Don't set didRemove, since this doesn't impact the scheduled alarms.
                    mPendingWhileIdleAlarms.remove(i);
                }
            } catch (RemoteException e) {
            }
        }

        if (didRemove) {
            if (DEBUG_BATCH) {
                Slog.v(TAG, "remove(package) changed bounds; rebatching");
            }
            rebatchAllAlarmsLocked(true);
            rescheduleKernelAlarmsLocked();
            updateNextAlarmClockLocked();
        }
    }

    boolean removeInvalidAlarmLocked(PendingIntent operation, IAlarmListener listener) {
        boolean didRemove = false;
        for (int i = mAlarmBatches.size() - 1; i >= 0; i--) {
            Batch b = mAlarmBatches.get(i);
            didRemove |= b.remove(operation, listener);
            if (b.size() == 0) {
                mAlarmBatches.remove(i);
            }
        }
        return didRemove;
    }

    void removeUserLocked(int userHandle) {
        boolean didRemove = false;
        for (int i = mAlarmBatches.size() - 1; i >= 0; i--) {
            Batch b = mAlarmBatches.get(i);
            didRemove |= b.remove(userHandle);
            if (b.size() == 0) {
                mAlarmBatches.remove(i);
            }
        }
        for (int i = mPendingWhileIdleAlarms.size() - 1; i >= 0; i--) {
            if (UserHandle.getUserId(mPendingWhileIdleAlarms.get(i).creatorUid)
                    == userHandle) {
                // Don't set didRemove, since this doesn't impact the scheduled alarms.
                mPendingWhileIdleAlarms.remove(i);
            }
        }
        for (int i = mLastAllowWhileIdleDispatch.size() - 1; i >= 0; i--) {
            if (UserHandle.getUserId(mLastAllowWhileIdleDispatch.keyAt(i)) == userHandle) {
                mLastAllowWhileIdleDispatch.removeAt(i);
            }
        }

        if (didRemove) {
            if (DEBUG_BATCH) {
                Slog.v(TAG, "remove(user) changed bounds; rebatching");
            }
            rebatchAllAlarmsLocked(true);
            rescheduleKernelAlarmsLocked();
            updateNextAlarmClockLocked();
        }
    }

    void interactiveStateChangedLocked(boolean interactive) {
        if (mInteractive != interactive) {
            mInteractive = interactive;
            final long nowELAPSED = SystemClock.elapsedRealtime();
            if (interactive) {
                if (mPendingNonWakeupAlarms.size() > 0) {
                    final long thisDelayTime = nowELAPSED - mStartCurrentDelayTime;
                    mTotalDelayTime += thisDelayTime;
                    if (mMaxDelayTime < thisDelayTime) {
                        mMaxDelayTime = thisDelayTime;
                    }
                    deliverAlarmsLocked(mPendingNonWakeupAlarms, nowELAPSED);
                    mPendingNonWakeupAlarms.clear();
                }
                if (mNonInteractiveStartTime > 0) {
                    long dur = nowELAPSED - mNonInteractiveStartTime;
                    if (dur > mNonInteractiveTime) {
                        mNonInteractiveTime = dur;
                    }
                }
            } else {
                mNonInteractiveStartTime = nowELAPSED;
            }
        }
    }

    boolean lookForPackageLocked(String packageName) {
        for (int i = 0; i < mAlarmBatches.size(); i++) {
            Batch b = mAlarmBatches.get(i);
            if (b.hasPackage(packageName)) {
                return true;
            }
        }
        for (int i = 0; i < mPendingWhileIdleAlarms.size(); i++) {
            final Alarm a = mPendingWhileIdleAlarms.get(i);
            if (a.matches(packageName)) {
                return true;
            }
        }
        return false;
    }

    private void setLocked(int type, long when) {
        if (mNativeData != 0 && mNativeData != -1) {
            // The kernel never triggers alarms with negative wakeup times
            // so we ensure they are positive.
            long alarmSeconds, alarmNanoseconds;
            if (when < 0) {
                alarmSeconds = 0;
                alarmNanoseconds = 0;
            } else {
                alarmSeconds = when / 1000;
                alarmNanoseconds = (when % 1000) * 1000 * 1000;
            }
            Slog.d(TAG, "set alarm to RTC " + when + " Type: "+ type);
            set(mNativeData, type, alarmSeconds, alarmNanoseconds);
        } else {
            Slog.d(TAG, "the mNativeData from RTC is abnormal,  mNativeData = " + mNativeData);
            Message msg = Message.obtain();
            msg.what = ALARM_EVENT;

            mHandler.removeMessages(ALARM_EVENT);
            mHandler.sendMessageAtTime(msg, when);
        }
    }

    private static final void dumpAlarmList(PrintWriter pw, ArrayList<Alarm> list,
            String prefix, String label, long nowRTC, long nowELAPSED, SimpleDateFormat sdf) {
        for (int i=list.size()-1; i>=0; i--) {
            Alarm a = list.get(i);
            pw.print(prefix); pw.print(label); pw.print(" #"); pw.print(i);
                    pw.print(": "); pw.println(a);
            a.dump(pw, prefix + "  ", nowRTC, nowELAPSED, sdf);
        }
    }

    private static final String labelForType(int type) {
        switch (type) {
        case RTC: return "RTC";
        case RTC_WAKEUP : return "RTC_WAKEUP";
        case ELAPSED_REALTIME : return "ELAPSED";
        case ELAPSED_REALTIME_WAKEUP: return "ELAPSED_WAKEUP";
        default:
            break;
        }
        return "--unknown--";
    }

    private static final void dumpAlarmList(PrintWriter pw, ArrayList<Alarm> list,
            String prefix, long nowELAPSED, long nowRTC, SimpleDateFormat sdf) {
        for (int i=list.size()-1; i>=0; i--) {
            Alarm a = list.get(i);
            final String label = labelForType(a.type);
            pw.print(prefix); pw.print(label); pw.print(" #"); pw.print(i);
                    pw.print(": "); pw.println(a);
            a.dump(pw, prefix + "  ", nowRTC, nowELAPSED, sdf);
        }
    }

    private native long init();
    private native void close(long nativeData);
    private native void set(long nativeData, int type, long seconds, long nanoseconds);
    private native int waitForAlarm(long nativeData);
    private native int setKernelTime(long nativeData, long millis);
    private native int setKernelTimezone(long nativeData, int minuteswest);

    // /M:add for PoerOffAlarm feature,@{
    private native boolean bootFromAlarm(int fd);

    // /@}

    boolean triggerAlarmsLocked(ArrayList<Alarm> triggerList, final long nowELAPSED,
            final long nowRTC) {
        boolean hasWakeup = false;
        // batches are temporally sorted, so we need only pull from the
        // start of the list until we either empty it or hit a batch
        // that is not yet deliverable
        while (mAlarmBatches.size() > 0) {
            Batch batch = mAlarmBatches.get(0);
            if (batch.start > nowELAPSED) {
                // Everything else is scheduled for the future
                break;
            }
            // We will (re)schedule some alarms now; don't let that interfere
            // with delivery of this current batch
            mAlarmBatches.remove(0);

            final int N = batch.size();
            for (int i = 0; i < N; i++) {
                Alarm alarm = batch.get(i);

                if ((alarm.flags&AlarmManager.FLAG_ALLOW_WHILE_IDLE) != 0) {
                    // If this is an ALLOW_WHILE_IDLE alarm, we constrain how frequently the app can
                    // schedule such alarms.
                    long lastTime = mLastAllowWhileIdleDispatch.get(alarm.uid, 0);
                    long minTime = lastTime + mAllowWhileIdleMinTime;
                    if (nowELAPSED < minTime) {
                        // Whoops, it hasn't been long enough since the last ALLOW_WHILE_IDLE
                        // alarm went off for this app.  Reschedule the alarm to be in the
                        // correct time period.
                        alarm.whenElapsed = minTime;
                        if (alarm.maxWhenElapsed < minTime) {
                            alarm.maxWhenElapsed = minTime;
                        }
                        if (RECORD_DEVICE_IDLE_ALARMS) {
                            IdleDispatchEntry ent = new IdleDispatchEntry();
                            ent.uid = alarm.uid;
                            ent.pkg = alarm.operation.getCreatorPackage();
                            ent.tag = alarm.operation.getTag("");
                            ent.op = "RESCHEDULE";
                            ent.elapsedRealtime = nowELAPSED;
                            ent.argRealtime = lastTime;
                            mAllowWhileIdleDispatches.add(ent);
                        }
                        setImplLocked(alarm, true, false);
                        continue;
                    }
                }

                alarm.count = 1;
                triggerList.add(alarm);
                if ((alarm.flags&AlarmManager.FLAG_WAKE_FROM_IDLE) != 0) {
                    EventLogTags.writeDeviceIdleWakeFromIdle(mPendingIdleUntil != null ? 1 : 0,
                            alarm.statsTag);
                }
                if (mPendingIdleUntil == alarm) {
                    mPendingIdleUntil = null;
                    rebatchAllAlarmsLocked(false);
                    restorePendingWhileIdleAlarmsLocked();
                }
                if (mNextWakeFromIdle == alarm) {
                    mNextWakeFromIdle = null;
                    rebatchAllAlarmsLocked(false);
                }

                // Recurring alarms may have passed several alarm intervals while the
                // phone was asleep or off, so pass a trigger count when sending them.
                if (alarm.repeatInterval > 0) {
                    // this adjustment will be zero if we're late by
                    // less than one full repeat interval
                    alarm.count += (nowELAPSED - alarm.whenElapsed) / alarm.repeatInterval;

                    // Also schedule its next recurrence
                    final long delta = alarm.count * alarm.repeatInterval;
                    final long nextElapsed = alarm.whenElapsed + delta;
                    final long maxElapsed;
                    if (mSupportAlarmGrouping && (mAmPlus != null)) {
                         // M: BG powerSaving feature
                        maxElapsed = mAmPlus.getMaxTriggerTime(alarm.type, nextElapsed, alarm.windowLength,
                        alarm.repeatInterval, alarm.operation, mAlarmMode, true);
                    } else {
                        maxElapsed = maxTriggerTime(nowELAPSED, nextElapsed, alarm.repeatInterval);
                    }
                    alarm.needGrouping = true;
                    
                    //Intercept wake up alarms. prize-linkh-20160318
                    if(PrizeOption.PRIZE_INTERCEPT_WAKEUP_ALARMS) {
                        alarm.type = convertWakeupAlarmToNonWakeup(alarm, alarm.originalType, 
                            alarm.operation, alarm.listener, "triggerAlarmsLocked");
                    }
                    //end...
                    setImplLocked(alarm.type, alarm.when + delta, nextElapsed, alarm.windowLength,
                            maxElapsed,
                            alarm.repeatInterval, alarm.operation, null, null, alarm.flags, true,
                            alarm.workSource, alarm.alarmClock, alarm.uid, alarm.packageName,
                            alarm.needGrouping,
                            /* Intercept wake up alarms. prize-linkh-20160602*/
                            alarm.originalType, alarm.fromSystemApp, alarm.hasRetrievedPackageInfo, alarm.ignoreIntercepted);//end..
                }

                if (alarm.wakeup) {
                    hasWakeup = true;
                }

                // We removed an alarm clock. Let the caller recompute the next alarm clock.
                if (alarm.alarmClock != null) {
                    mNextAlarmClockMayChange = true;
                }
            }
        }

        // This is a new alarm delivery set; bump the sequence number to indicate that
        // all apps' alarm delivery classes should be recalculated.
        mCurrentSeq++;
        calculateDeliveryPriorities(triggerList);
        Collections.sort(triggerList, mAlarmDispatchComparator);

        if (localLOGV) {
            for (int i=0; i<triggerList.size(); i++) {
                Slog.v(TAG, "Triggering alarm #" + i + ": " + triggerList.get(i));
            }
        }

        return hasWakeup;
    }

    /**
     * This Comparator sorts Alarms into increasing time order.
     */
    public static class IncreasingTimeOrder implements Comparator<Alarm> {
        public int compare(Alarm a1, Alarm a2) {
            long when1 = a1.when;
            long when2 = a2.when;
            if (when1 > when2) {
                return 1;
            }
            if (when1 < when2) {
                return -1;
            }
            return 0;
        }
    }

    private static class Alarm {
        /*Intercept wake up alarms. prize-linkh-20160602*/
        //remove final keyword.
        //public final int type;
        public int type;
        //end...
        public final long origWhen;
        public final boolean wakeup;
        public final PendingIntent operation;
        public final IAlarmListener listener;
        public final String listenerTag;
        public final String statsTag;
        public final WorkSource workSource;
        public final int flags;
        public final AlarmManager.AlarmClockInfo alarmClock;
        public final int uid;
        public final int creatorUid;
        public final String packageName;
        public int count;
        public long when;
        public long windowLength;
        public long whenElapsed;    // 'when' in the elapsed time base
        public long maxWhenElapsed; // also in the elapsed time base
        public long repeatInterval;
        public PriorityClass priorityClass;
        public boolean needGrouping;
        // Intercept wake up alarms. prize-linkh-20160602
        // orignal alarm type.
        public int originalType;
        // Note: we always assume this alarm is from system app. Because 
        // If we can't retrieve package info, we maybe intercept
        // the wakeup alarm unexpectly.
        public boolean fromSystemApp = true;
        // used to figure out if we have retrieve package info for this alarm.
        public boolean hasRetrievedPackageInfo;
        // if true, this alarm can't be intercepted to non-wakeup alarm.
        public boolean ignoreIntercepted;
        //end...
        public Alarm(int _type, long _when, long _whenElapsed, long _windowLength, long _maxWhen,
                long _interval, PendingIntent _op, IAlarmListener _rec, String _listenerTag,
                WorkSource _ws, int _flags, AlarmManager.AlarmClockInfo _info,
                int _uid, String _pkgName, boolean mNeedGrouping,
                /*Intercept wake up alarms. prize-linkh-20160602*/
                int _originalType, boolean _fromSystemApp, boolean _hasRetrievedPackageInfo, boolean _ignoreIntercepted) { //end...

            type = _type;
            origWhen = _when;
            wakeup = _type == AlarmManager.ELAPSED_REALTIME_WAKEUP
                    || _type == AlarmManager.RTC_WAKEUP;
            when = _when;
            whenElapsed = _whenElapsed;
            windowLength = _windowLength;
            maxWhenElapsed = _maxWhen;
            repeatInterval = _interval;
            operation = _op;
            listener = _rec;
            listenerTag = _listenerTag;
            statsTag = makeTag(_op, _listenerTag, _type);
            workSource = _ws;
            flags = _flags;
            alarmClock = _info;
            uid = _uid;
            packageName = _pkgName;
            needGrouping = mNeedGrouping;

            creatorUid = (operation != null) ? operation.getCreatorUid() : uid;
            // Intercept wake up alarms. prize-linkh-20160602
            originalType = _originalType;
            fromSystemApp = _fromSystemApp;
            hasRetrievedPackageInfo = _hasRetrievedPackageInfo;
            ignoreIntercepted = _ignoreIntercepted;
            //end..
        }

        public static String makeTag(PendingIntent pi, String tag, int type) {
            final String alarmString = type == ELAPSED_REALTIME_WAKEUP || type == RTC_WAKEUP
                    ? "*walarm*:" : "*alarm*:";
            return (pi != null) ? pi.getTag(alarmString) : (alarmString + tag);
        }

        public WakeupEvent makeWakeupEvent(long nowRTC) {
            return new WakeupEvent(nowRTC, creatorUid,
                    (operation != null)
                        ? operation.getIntent().getAction()
                        : ("<listener>:" + listenerTag));
        }

        // Returns true if either matches
        public boolean matches(PendingIntent pi, IAlarmListener rec) {
            return (operation != null)
                    ? operation.equals(pi)
                    : rec != null && listener.asBinder().equals(rec.asBinder());
        }

        public boolean matches(String packageName) {
            return (operation != null)
                    ? packageName.equals(operation.getTargetPackage())
                    : packageName.equals(this.packageName);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(128);
            sb.append("Alarm{");
            sb.append(Integer.toHexString(System.identityHashCode(this)));
            sb.append(" type ");
            sb.append(type);
            // Intercept wake up alarms. prize-linkh-20160603
            if(PrizeOption.PRIZE_INTERCEPT_WAKEUP_ALARMS && DUMP_STATE_FOR_INTERCEPTING_WA) {
                if(type != originalType) {
                    sb.append(" oriType ");
                    sb.append(originalType);
                }
                if(ignoreIntercepted) {
                    sb.append(" ignoreIntercepted ");
                    sb.append(ignoreIntercepted);
                }
            } //end...            
            sb.append(" when ");
            sb.append(when);
            sb.append(" ");
            if (operation != null) {
                sb.append(operation.getTargetPackage());
            } else {
                sb.append(packageName);
            }
            sb.append('}');
            return sb.toString();
        }

        public void dump(PrintWriter pw, String prefix, long nowRTC, long nowELAPSED,
                SimpleDateFormat sdf) {
            final boolean isRtc = (type == RTC || type == RTC_WAKEUP);
            pw.print(prefix); pw.print("tag="); pw.println(statsTag);
            pw.print(prefix); pw.print("type="); pw.print(type);
            // Intercept wake up alarms. prize-linkh-20160603
            if(PrizeOption.PRIZE_INTERCEPT_WAKEUP_ALARMS && DUMP_STATE_FOR_INTERCEPTING_WA) {
                if(type != originalType) {
                    pw.print(" oriType="); pw.print(originalType);
                }
                if(ignoreIntercepted) {
                    pw.print(" ignoreIntercepted="); pw.print(ignoreIntercepted);
                }
            } //end...                
                    pw.print(" whenElapsed="); TimeUtils.formatDuration(whenElapsed,
                            nowELAPSED, pw);
                    pw.print(" when=");
                    if (isRtc) {
                        pw.print(sdf.format(new Date(when)));
                    } else {
                        TimeUtils.formatDuration(when, nowELAPSED, pw);
                    }
                    pw.println();
            pw.print(prefix); pw.print("window="); TimeUtils.formatDuration(windowLength, pw);
                    pw.print(" repeatInterval="); pw.print(repeatInterval);
                    pw.print(" count="); pw.print(count);
                    pw.print(" flags=0x"); pw.println(Integer.toHexString(flags));
            if (alarmClock != null) {
                pw.print(prefix); pw.println("Alarm clock:");
                pw.print(prefix); pw.print("  triggerTime=");
                pw.println(sdf.format(new Date(alarmClock.getTriggerTime())));
                pw.print(prefix); pw.print("  showIntent="); pw.println(alarmClock.getShowIntent());
            }
            pw.print(prefix); pw.print("operation="); pw.println(operation);
            if (listener != null) {
                pw.print(prefix); pw.print("listener="); pw.println(listener.asBinder());
            }
        }
    }

    void recordWakeupAlarms(ArrayList<Batch> batches, long nowELAPSED, long nowRTC) {
        final int numBatches = batches.size();
        for (int nextBatch = 0; nextBatch < numBatches; nextBatch++) {
            Batch b = batches.get(nextBatch);
            if (b.start > nowELAPSED) {
                break;
            }

            final int numAlarms = b.alarms.size();
            for (int nextAlarm = 0; nextAlarm < numAlarms; nextAlarm++) {
                Alarm a = b.alarms.get(nextAlarm);
                mRecentWakeups.add(a.makeWakeupEvent(nowRTC));
            }
        }
    }

    long currentNonWakeupFuzzLocked(long nowELAPSED) {
        long timeSinceOn = nowELAPSED - mNonInteractiveStartTime;
        if (timeSinceOn < 5*60*1000) {
            // If the screen has been off for 5 minutes, only delay by at most two minutes.
            return 2*60*1000;
        } else if (timeSinceOn < 30*60*1000) {
            // If the screen has been off for 30 minutes, only delay by at most 15 minutes.
            return 15*60*1000;
        } else {
            // Otherwise, we will delay by at most an hour.
            return 60*60*1000;
        }
    }

    static int fuzzForDuration(long duration) {
        if (duration < 15*60*1000) {
            // If the duration until the time is less than 15 minutes, the maximum fuzz
            // is the duration.
            return (int)duration;
        } else if (duration < 90*60*1000) {
            // If duration is less than 1 1/2 hours, the maximum fuzz is 15 minutes,
            return 15*60*1000;
        } else {
            // Otherwise, we will fuzz by at most half an hour.
            return 30*60*1000;
        }
    }

    boolean checkAllowNonWakeupDelayLocked(long nowELAPSED) {
        if (mInteractive) {
            return false;
        }
        if (mLastAlarmDeliveryTime <= 0) {
            return false;
        }

        /// M :If WFD is connected, do not delay the alarms {@
        if(mIsWFDConnected){
            Slog.v(TAG, "[Digvijay]checkAllowNonWakeupDelayLocked return FALSE WFD connected");
            return false;
        }
        /// @}

        if (mPendingNonWakeupAlarms.size() > 0 && mNextNonWakeupDeliveryTime < nowELAPSED) {
            // This is just a little paranoia, if somehow we have pending non-wakeup alarms
            // and the next delivery time is in the past, then just deliver them all.  This
            // avoids bugs where we get stuck in a loop trying to poll for alarms.
            return false;
        }
        long timeSinceLast = nowELAPSED - mLastAlarmDeliveryTime;
        return timeSinceLast <= currentNonWakeupFuzzLocked(nowELAPSED);
    }

    void deliverAlarmsLocked(ArrayList<Alarm> triggerList, long nowELAPSED) {
        mLastAlarmDeliveryTime = nowELAPSED;
        final long nowRTC = System.currentTimeMillis();
        mNeedRebatchForRepeatingAlarm = false;

        /// M: Uplink Traffic Shaping feature start @{
        boolean openLteGateSuccess = false;
        if (dataShapingManager != null) {
            try {
                openLteGateSuccess = dataShapingManager.openLteDataUpLinkGate(false);
            } catch (Exception e) {
                Log.e(TAG, "Error openLteDataUpLinkGate false" + e);
            }
        } else {
            Slog.v(TAG, "dataShapingManager is null");
        }
        Slog.v(TAG, "openLteGateSuccess = " + openLteGateSuccess);
        /// M: Uplink Traffic Shaping feature end @{

        for (int i=0; i<triggerList.size(); i++) {
            Alarm alarm = triggerList.get(i);
            final boolean allowWhileIdle = (alarm.flags&AlarmManager.FLAG_ALLOW_WHILE_IDLE) != 0;
            // /M:add for PowerOffAlarm feature,@{
            updatePoweroffAlarm(nowRTC);
            // /@}
            // /M:add for DM feature,@{
            synchronized (mDMLock) {
                if (mDMEnable == false || mPPLEnable == false) {
                    FreeDmIntent(triggerList, mDmFreeList, nowELAPSED, mDmResendList);
                    break;
                }
            }
            // /@}

            // /M:add for IPO feature,@{
            if (SystemProperties.get("ro.mtk_ipo_support").equals("1")) {
            if (mIPOShutdown)
                continue;
            }
            // /@}

            try {
                if (localLOGV) Slog.v(TAG, "sending alarm " + alarm);
                if (alarm.type == RTC_WAKEUP || alarm.type == ELAPSED_REALTIME_WAKEUP) {
                    if (alarm.operation == null) {
                        Slog.d(TAG, "wakeup alarm = " + alarm
                            + "; listener package = " + alarm.listenerTag
                            + "needGrouping = " + alarm.needGrouping);
                    } else {
                        Slog.d(TAG, "wakeup alarm = " + alarm
                            + "; package = " + alarm.operation.getTargetPackage()
                            + "needGrouping = " + alarm.needGrouping);
                    }
                }
                if (RECORD_ALARMS_IN_HISTORY) {
                    if (alarm.workSource != null && alarm.workSource.size() > 0) {
                        for (int wi=0; wi<alarm.workSource.size(); wi++) {
                            ActivityManagerNative.noteAlarmStart(
                                    alarm.operation, alarm.workSource.get(wi), alarm.statsTag);
                        }
                    } else {
                        ActivityManagerNative.noteAlarmStart(
                                alarm.operation, alarm.uid, alarm.statsTag);
                    }
                }

                mDeliveryTracker.deliverLocked(alarm, nowELAPSED, allowWhileIdle);
            } catch (RuntimeException e) {
                Slog.w(TAG, "Failure sending alarm.", e);
            }
        }
        if (mNeedRebatchForRepeatingAlarm) {
            Slog.v(TAG, " deliverAlarmsLocked removeInvalidAlarmLocked then rebatch ");
            rebatchAllAlarmsLocked(true);
            rescheduleKernelAlarmsLocked();
            updateNextAlarmClockLocked();
        }
    }

    private class AlarmThread extends Thread
    {
        public AlarmThread()
        {
            super("AlarmManager");
        }

        public void run()
        {
            ArrayList<Alarm> triggerList = new ArrayList<Alarm>();

            while (true)
            {

                // /M:add for IPO feature,when shut down,this thread goto
                // sleep,@{
                if (SystemProperties.get("ro.mtk_ipo_support").equals("1")) {
                    if (mIPOShutdown) {
                        try {
                            if (mNativeData != -1) {
                                synchronized (mLock) {
                                    mAlarmBatches.clear();
                                }
                            }
                            synchronized (mWaitThreadlock) {
                                mWaitThreadlock.wait();
                            }
                        } catch (InterruptedException e) {
                            Slog.v(TAG, "InterruptedException ");
                        }
                    }
                }
                // /@}

                int result = waitForAlarm(mNativeData);
                mLastWakeup = SystemClock.elapsedRealtime();

                triggerList.clear();

                final long nowRTC = System.currentTimeMillis();
                final long nowELAPSED = SystemClock.elapsedRealtime();

                if ((result & TIME_CHANGED_MASK) != 0) {
                    // The kernel can give us spurious time change notifications due to
                    // small adjustments it makes internally; we want to filter those out.
                    final long lastTimeChangeClockTime;
                    final long expectedClockTime;
                    synchronized (mLock) {
                        lastTimeChangeClockTime = mLastTimeChangeClockTime;
                        expectedClockTime = lastTimeChangeClockTime
                                + (nowELAPSED - mLastTimeChangeRealtime);
                    }
                    if (lastTimeChangeClockTime == 0 || nowRTC < (expectedClockTime-500)
                            || nowRTC > (expectedClockTime+500)) {
                        // The change is by at least +/- 500 ms (or this is the first change),
                        // let's do it!
                        if (DEBUG_BATCH) {
                            Slog.v(TAG, "Time changed notification from kernel; rebatching");
                        }
                        removeImpl(mTimeTickSender);
                        removeImpl(mDateChangeSender);
                        rebatchAllAlarms();
                        mClockReceiver.scheduleTimeTickEvent();
                        mClockReceiver.scheduleDateChangedEvent();
                        synchronized (mLock) {
                            mNumTimeChanged++;
                            mLastTimeChangeClockTime = nowRTC;
                            mLastTimeChangeRealtime = nowELAPSED;
                        }
                        Intent intent = new Intent(Intent.ACTION_TIME_CHANGED);
                        intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING
                                | Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
                        getContext().sendBroadcastAsUser(intent, UserHandle.ALL);

                        // The world has changed on us, so we need to re-evaluate alarms
                        // regardless of whether the kernel has told us one went off.
                        result |= IS_WAKEUP_MASK;
                    }
                }

                if (result != TIME_CHANGED_MASK) {
                    // If this was anything besides just a time change, then figure what if
                    // anything to do about alarms.
                    synchronized (mLock) {
                        if (localLOGV) Slog.v(
                            TAG, "Checking for alarms... rtc=" + nowRTC
                            + ", elapsed=" + nowELAPSED);

                        if (WAKEUP_STATS) {
                            if ((result & IS_WAKEUP_MASK) != 0) {
                                long newEarliest = nowRTC - RECENT_WAKEUP_PERIOD;
                                int n = 0;
                                for (WakeupEvent event : mRecentWakeups) {
                                    if (event.when > newEarliest) break;
                                    n++; // number of now-stale entries at the list head
                                }
                                for (int i = 0; i < n; i++) {
                                    mRecentWakeups.remove();
                                }

                                recordWakeupAlarms(mAlarmBatches, nowELAPSED, nowRTC);
                            }
                        }

                        boolean hasWakeup = triggerAlarmsLocked(triggerList, nowELAPSED, nowRTC);
                        if (!hasWakeup && checkAllowNonWakeupDelayLocked(nowELAPSED)) {
                            // if there are no wakeup alarms and the screen is off, we can
                            // delay what we have so far until the future.
                            if (mPendingNonWakeupAlarms.size() == 0) {
                                mStartCurrentDelayTime = nowELAPSED;
                                mNextNonWakeupDeliveryTime = nowELAPSED
                                        + ((currentNonWakeupFuzzLocked(nowELAPSED)*3)/2);
                            }
                            mPendingNonWakeupAlarms.addAll(triggerList);
                            mNumDelayedAlarms += triggerList.size();
                            rescheduleKernelAlarmsLocked();
                            updateNextAlarmClockLocked();
                        } else {
                            // now deliver the alarm intents; if there are pending non-wakeup
                            // alarms, we need to merge them in to the list.  note we don't
                            // just deliver them first because we generally want non-wakeup
                            // alarms delivered after wakeup alarms.
                            rescheduleKernelAlarmsLocked();
                            updateNextAlarmClockLocked();
                            if (mPendingNonWakeupAlarms.size() > 0) {
                                calculateDeliveryPriorities(mPendingNonWakeupAlarms);
                                triggerList.addAll(mPendingNonWakeupAlarms);
                                Collections.sort(triggerList, mAlarmDispatchComparator);
                                final long thisDelayTime = nowELAPSED - mStartCurrentDelayTime;
                                mTotalDelayTime += thisDelayTime;
                                if (mMaxDelayTime < thisDelayTime) {
                                    mMaxDelayTime = thisDelayTime;
                                }
                                mPendingNonWakeupAlarms.clear();
                            }
                            deliverAlarmsLocked(triggerList, nowELAPSED);
                        }
                    }

                } else {
                    // Just in case -- even though no wakeup flag was set, make sure
                    // we have updated the kernel to the next alarm time.
                    synchronized (mLock) {
                        rescheduleKernelAlarmsLocked();
                    }
                }
            }
        }
    }

    /**
     * Attribute blame for a WakeLock.
     * @param pi PendingIntent to attribute blame to if ws is null.
     * @param ws WorkSource to attribute blame.
     * @param knownUid attribution uid; < 0 if we need to derive it from the PendingIntent sender
     */
    void setWakelockWorkSource(PendingIntent pi, WorkSource ws, int type, String tag,
            int knownUid, boolean first) {
        try {
            final boolean unimportant = pi == mTimeTickSender;
            mWakeLock.setUnimportantForLogging(unimportant);
            if (first || mLastWakeLockUnimportantForLogging) {
                mWakeLock.setHistoryTag(tag);
            } else {
                mWakeLock.setHistoryTag(null);
            }
            mLastWakeLockUnimportantForLogging = unimportant;
            if (ws != null) {
                mWakeLock.setWorkSource(ws);
                return;
            }

            final int uid = (knownUid >= 0)
                    ? knownUid
                    : ActivityManagerNative.getDefault().getUidForIntentSender(pi.getTarget());
            if (uid >= 0) {
                mWakeLock.setWorkSource(new WorkSource(uid));
                return;
            }
        } catch (Exception e) {
        }

        // Something went wrong; fall back to attributing the lock to the OS
        mWakeLock.setWorkSource(null);
    }

    private class AlarmHandler extends Handler {
        public static final int ALARM_EVENT = 1;
        public static final int SEND_NEXT_ALARM_CLOCK_CHANGED = 2;
        public static final int LISTENER_TIMEOUT = 3;
        public static final int REPORT_ALARMS_ACTIVE = 4;
        // Intercept wake up alarms. prize-linkh-20160603
        public static final int INTERCEPT_WAKEUP_ALARM_STATE_CHANGED = 10;
        //end...
        public AlarmHandler() {
        }
        
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case ALARM_EVENT: {
                    ArrayList<Alarm> triggerList = new ArrayList<Alarm>();
                    synchronized (mLock) {
                        final long nowRTC = System.currentTimeMillis();
                        final long nowELAPSED = SystemClock.elapsedRealtime();
                        triggerAlarmsLocked(triggerList, nowELAPSED, nowRTC);
                        updateNextAlarmClockLocked();
                    }

                    // now trigger the alarms without the lock held
                    for (int i=0; i<triggerList.size(); i++) {
                        Alarm alarm = triggerList.get(i);
                        try {
                            alarm.operation.send();
                        } catch (PendingIntent.CanceledException e) {
                            if (alarm.repeatInterval > 0) {
                                // This IntentSender is no longer valid, but this
                                // is a repeating alarm, so toss the hoser.
                                removeImpl(alarm.operation);
                            }
                        }
                    }
                    break;
                }

                case SEND_NEXT_ALARM_CLOCK_CHANGED:
                    sendNextAlarmClockChanged();
                    break;

                case LISTENER_TIMEOUT:
                    mDeliveryTracker.alarmTimedOut((IBinder) msg.obj);
                    break;

                case REPORT_ALARMS_ACTIVE:
                    if (mLocalDeviceIdleController != null) {
                        mLocalDeviceIdleController.setAlarmsActive(msg.arg1 != 0);
                    }
                    break;
                // Intercept wake up alarms. prize-linkh-20160603
                case INTERCEPT_WAKEUP_ALARM_STATE_CHANGED:
                    Slog.d(LOG_TAG, "Handle message: INTERCEPT_WAKEUP_ALARM_STATE_CHANGED. Rebatch alarms.");
                    rebatchAllAlarms();
                    break;
                //end..

                default:
                    // nope, just ignore it
                    break;
            }
        }
    }
    
    class ClockReceiver extends BroadcastReceiver {
        public ClockReceiver() {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_TIME_TICK);
            filter.addAction(Intent.ACTION_DATE_CHANGED);
            getContext().registerReceiver(this, filter);
        }
        
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_TIME_TICK)) {
                if (DEBUG_BATCH) {
                    Slog.v(TAG, "Received TIME_TICK alarm; rescheduling");
                }
                Slog.v(TAG, "mSupportAlarmGrouping = " + mSupportAlarmGrouping +
                            "  mAmPlus = " + mAmPlus);
                scheduleTimeTickEvent();
            } else if (intent.getAction().equals(Intent.ACTION_DATE_CHANGED)) {
                // Since the kernel does not keep track of DST, we need to
                // reset the TZ information at the beginning of each day
                // based off of the current Zone gmt offset + userspace tracked
                // daylight savings information.
                TimeZone zone = TimeZone.getTimeZone(SystemProperties.get(TIMEZONE_PROPERTY));
                int gmtOffset = zone.getOffset(System.currentTimeMillis());
                setKernelTimezone(mNativeData, -(gmtOffset / 60000));
                scheduleDateChangedEvent();
            }
        }
        
        public void scheduleTimeTickEvent() {
            final long currentTime = System.currentTimeMillis();
            final long nextTime = 60000 * ((currentTime / 60000) + 1);

            // Schedule this event for the amount of time that it would take to get to
            // the top of the next minute.
            final long tickEventDelay = nextTime - currentTime;

            final WorkSource workSource = null; // Let system take blame for time tick events.
            setImpl(ELAPSED_REALTIME, SystemClock.elapsedRealtime() + tickEventDelay, 0,
                    0, mTimeTickSender, null, null, AlarmManager.FLAG_STANDALONE, workSource,
                    null, Process.myUid(), "android");
        }

        public void scheduleDateChangedEvent() {
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(System.currentTimeMillis());
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            calendar.add(Calendar.DAY_OF_MONTH, 1);

            final WorkSource workSource = null; // Let system take blame for date change events.
            setImpl(RTC, calendar.getTimeInMillis(), 0, 0, mDateChangeSender, null, null,
                    AlarmManager.FLAG_STANDALONE, workSource, null,
                    Process.myUid(), "android");
        }
    }

    class WFDStatusChangedReceiver extends BroadcastReceiver {
        public WFDStatusChangedReceiver() {
            IntentFilter filter = new IntentFilter();
            // mIsWFDConnected registerReceiver
            filter.addAction(DisplayManager.ACTION_WIFI_DISPLAY_STATUS_CHANGED);
            getContext().registerReceiver(this, filter);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (DisplayManager.ACTION_WIFI_DISPLAY_STATUS_CHANGED.equals(intent.getAction())) {

                WifiDisplayStatus wfdStatus = (WifiDisplayStatus)
                intent.getParcelableExtra(DisplayManager.EXTRA_WIFI_DISPLAY_STATUS);
                mIsWFDConnected =
                (WifiDisplayStatus.DISPLAY_STATE_CONNECTED == wfdStatus.getActiveDisplayState());
                Slog.v(TAG, "Wfd Status changed new  = " + mIsWFDConnected);
            }
        }
    }

    class InteractiveStateReceiver extends BroadcastReceiver {
        public InteractiveStateReceiver() {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            filter.addAction(Intent.ACTION_SCREEN_ON);
            filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
            getContext().registerReceiver(this, filter);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            synchronized (mLock) {
                interactiveStateChangedLocked(Intent.ACTION_SCREEN_ON.equals(intent.getAction()));
            }
        }
    }
    /*prize-add by lihuangyuan,for smart-killed app clear alarms-2018-03-30-start*/
    private static final String KILLED_APPS_INTENT_ACTION = "prize.intent.action.killed.apps";
    private static final String KILLED_APPS_INTENT_EXTRA_KILLED_APPS_KEY = "apps";
    private static final String TAG_KILL = "KillRestart-alarm";
    /*prize-add by lihuangyuan,for smart-killed app clear alarms-2018-03-30-end*/
    class UninstallReceiver extends BroadcastReceiver {
        public UninstallReceiver() {
            IntentFilter filter = new IntentFilter();            
            filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
            filter.addAction(Intent.ACTION_PACKAGE_RESTARTED);
            filter.addAction(Intent.ACTION_QUERY_PACKAGE_RESTART);
            filter.addDataScheme("package");
            getContext().registerReceiver(this, filter);
             // Register for events related to sdcard installation.
            IntentFilter sdFilter = new IntentFilter();
            sdFilter.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE);
            sdFilter.addAction(Intent.ACTION_USER_STOPPED);
            sdFilter.addAction(Intent.ACTION_UID_REMOVED);
            /*prize-add by lihuangyuan,for smart-killed app clear alarms-2018-03-30-start*/
            if(PrizeOption.PRIZE_SUPPRORT_SYS_RES_MON)
            {
                sdFilter.addAction(KILLED_APPS_INTENT_ACTION);
            }
            /*prize-add by lihuangyuan,for smart-killed app clear alarms-2018-03-30-end*/
            getContext().registerReceiver(this, sdFilter);
        }
        
        @Override
        public void onReceive(Context context, Intent intent) {
            synchronized (mLock) {
                Slog.d(TAG, "UninstallReceiver  action = " + intent.getAction());
                String action = intent.getAction();
                String pkgList[] = null;
                if (Intent.ACTION_QUERY_PACKAGE_RESTART.equals(action)) {
                    pkgList = intent.getStringArrayExtra(Intent.EXTRA_PACKAGES);
                    for (String packageName : pkgList) {
                        if (lookForPackageLocked(packageName)) {
                        // /M:add for ALPS01013485,@{
                            if (!"android".equals(packageName)) {
                        // /@}
                                setResultCode(Activity.RESULT_OK);
                                return;
                        // /M:add for ALPS01013485,@{
                            }
                        // /@}
                        }
                    }
                    return;
                } else if (Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE.equals(action)) {
                    pkgList = intent.getStringArrayExtra(Intent.EXTRA_CHANGED_PACKAGE_LIST);
                } else if (Intent.ACTION_USER_STOPPED.equals(action)) {
                    int userHandle = intent.getIntExtra(Intent.EXTRA_USER_HANDLE, -1);
                    if (userHandle >= 0) {
                        removeUserLocked(userHandle);
                    }
                } else if (Intent.ACTION_UID_REMOVED.equals(action)) {
                    int uid = intent.getIntExtra(Intent.EXTRA_UID, -1);
                    if (uid >= 0) {
                        mLastAllowWhileIdleDispatch.delete(uid);
                    }
                /*prize-add by lihuangyuan,for smart-killed app clear alarms-2018-03-30-start*/
                }else if(KILLED_APPS_INTENT_ACTION.equals(action)){
                    Slog.d(TAG_KILL, "Receive action: " + action);
                    String[] apps = intent.getStringArrayExtra(KILLED_APPS_INTENT_EXTRA_KILLED_APPS_KEY);
                    if (apps != null) 
                    {
                        Slog.d(TAG_KILL, "Killed Apps: " + apps.length);
                        long start_time = SystemClock.elapsedRealtime();
                        if (apps.length > 0) 
                        {
                            for (int k=0;k<apps.length;k++) 
                            {
                                String pkg = apps[k];   
                                
                                Slog.d(TAG_KILL, "Killed app:"+pkg+",remove alarm");
                                removeLocked(pkg);
                                mPriorities.remove(pkg);
                                for (int i=mBroadcastStats.size()-1; i>=0; i--) 
                                {
                                    ArrayMap<String, BroadcastStats> uidStats = mBroadcastStats.valueAt(i);
                                    if (uidStats.remove(pkg) != null) 
                                    {
                                        if (uidStats.size() <= 0) 
                                        {
                                            mBroadcastStats.removeAt(i);
                                        }
                                    }
                                }
                            }                            
                        }
                        long usetime = SystemClock.elapsedRealtime() -  start_time;
                        Slog.d(TAG_KILL,"cost time:"+usetime+"ms");
                    } 
                    else 
                    {
                        Slog.d(TAG, "Can't get valid apps from intent!");
                    }
                /*prize-add by lihuangyuan,for smart-killed app clear alarms-2018-03-30-end*/
                } else {
                    if (Intent.ACTION_PACKAGE_REMOVED.equals(action)
                            && intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                        // This package is being updated; don't kill its alarms.
                        return;
                    }
                    Uri data = intent.getData();
                    if (data != null) {
                        String pkg = data.getSchemeSpecificPart();
                        if (pkg != null) {
                            pkgList = new String[]{pkg};
                        }
                    }
                }
                if (pkgList != null && (pkgList.length > 0)) {
                    for (String pkg : pkgList) {
                        // /M:add for ALPS01013485,@{
                        if ("android".equals(pkg)) {
                            continue;
                        }
                        // /@}
                        //Prize add by xiarui 2018-01-30 @{
                        if (PrizeOption.PRIZE_SMART_CLEANER && "com.prize.smartcleaner".equals(pkg)) {
                            continue;
                        }
                        //@}
                        removeLocked(pkg);
                        mPriorities.remove(pkg);
                        for (int i=mBroadcastStats.size()-1; i>=0; i--) {
                            ArrayMap<String, BroadcastStats> uidStats = mBroadcastStats.valueAt(i);
                            if (uidStats.remove(pkg) != null) {
                                if (uidStats.size() <= 0) {
                                    mBroadcastStats.removeAt(i);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    final class UidObserver extends IUidObserver.Stub {
        @Override public void onUidStateChanged(int uid, int procState) throws RemoteException {
        }

        @Override public void onUidGone(int uid) throws RemoteException {
        }

        @Override public void onUidActive(int uid) throws RemoteException {
        }

        @Override public void onUidIdle(int uid) throws RemoteException {
            synchronized (mLock) {
                removeForStoppedLocked(uid);
            }
        }
    };

    private final BroadcastStats getStatsLocked(PendingIntent pi) {
        String pkg = pi.getCreatorPackage();
        int uid = pi.getCreatorUid();
        return getStatsLocked(uid, pkg);
    }

    private final BroadcastStats getStatsLocked(int uid, String pkgName) {
        ArrayMap<String, BroadcastStats> uidStats = mBroadcastStats.get(uid);
        if (uidStats == null) {
            uidStats = new ArrayMap<String, BroadcastStats>();
            mBroadcastStats.put(uid, uidStats);
        }
        BroadcastStats bs = uidStats.get(pkgName);
        if (bs == null) {
            bs = new BroadcastStats(uid, pkgName);
            uidStats.put(pkgName, bs);
        }
        return bs;
    }

    class DeliveryTracker extends IAlarmCompleteListener.Stub implements PendingIntent.OnFinished {
        private InFlight removeLocked(PendingIntent pi, Intent intent) {
            for (int i = 0; i < mInFlight.size(); i++) {
                if (mInFlight.get(i).mPendingIntent == pi) {
                    return mInFlight.remove(i);
                }
            }
            mLog.w("No in-flight alarm for " + pi + " " + intent);
            return null;
        }

        private InFlight removeLocked(IBinder listener) {
            for (int i = 0; i < mInFlight.size(); i++) {
                if (mInFlight.get(i).mListener == listener) {
                    return mInFlight.remove(i);
                }
            }
            mLog.w("No in-flight alarm for listener " + listener);
            return null;
        }

        private void updateStatsLocked(InFlight inflight) {
            final long nowELAPSED = SystemClock.elapsedRealtime();
            BroadcastStats bs = inflight.mBroadcastStats;
            bs.nesting--;
            if (bs.nesting <= 0) {
                bs.nesting = 0;
                bs.aggregateTime += nowELAPSED - bs.startTime;
            }
            FilterStats fs = inflight.mFilterStats;
            fs.nesting--;
            if (fs.nesting <= 0) {
                fs.nesting = 0;
                fs.aggregateTime += nowELAPSED - fs.startTime;
            }
            if (RECORD_ALARMS_IN_HISTORY) {
                if (inflight.mWorkSource != null && inflight.mWorkSource.size() > 0) {
                    for (int wi=0; wi<inflight.mWorkSource.size(); wi++) {
                        ActivityManagerNative.noteAlarmFinish(
                                inflight.mPendingIntent, inflight.mWorkSource.get(wi), inflight.mTag);
                    }
                } else {
                    ActivityManagerNative.noteAlarmFinish(
                            inflight.mPendingIntent, inflight.mUid, inflight.mTag);
                }
            }
        }

        private void updateTrackingLocked(InFlight inflight) {
            if (inflight != null) {
                updateStatsLocked(inflight);
            }
            mBroadcastRefCount--;
            if (mBroadcastRefCount == 0) {
                mHandler.obtainMessage(AlarmHandler.REPORT_ALARMS_ACTIVE, 0).sendToTarget();
                mWakeLock.release();
                if (mInFlight.size() > 0) {
                    mLog.w("Finished all dispatches with " + mInFlight.size()
                            + " remaining inflights");
                    for (int i=0; i<mInFlight.size(); i++) {
                        mLog.w("  Remaining #" + i + ": " + mInFlight.get(i));
                    }
                    mInFlight.clear();
                }
            } else {
                // the next of our alarms is now in flight.  reattribute the wakelock.
                if (mInFlight.size() > 0) {
                    InFlight inFlight = mInFlight.get(0);
                    setWakelockWorkSource(inFlight.mPendingIntent, inFlight.mWorkSource,
                            inFlight.mAlarmType, inFlight.mTag, -1, false);
                } else {
                    // should never happen
                    mLog.w("Alarm wakelock still held but sent queue empty");
                    mWakeLock.setWorkSource(null);
                }
            }
        }

        /**
         * Callback that arrives when a direct-call alarm reports that delivery has finished
         */
        @Override
        public void alarmComplete(IBinder who) {
            if (who == null) {
                Slog.w(TAG, "Invalid alarmComplete: uid=" + Binder.getCallingUid()
                        + " pid=" + Binder.getCallingPid());
                return;
            }

            final long ident = Binder.clearCallingIdentity();
            try {
                synchronized (mLock) {
                    mHandler.removeMessages(AlarmHandler.LISTENER_TIMEOUT, who);
                    InFlight inflight = removeLocked(who);
                    if (inflight != null) {
                        if (DEBUG_LISTENER_CALLBACK) {
                            Slog.i(TAG, "alarmComplete() from " + who);
                        }
                        updateTrackingLocked(inflight);
                    } else {
                        // Delivery timed out, and the timeout handling already took care of
                        // updating our tracking here, so we needn't do anything further.
                        if (DEBUG_LISTENER_CALLBACK) {
                            Slog.i(TAG, "Late alarmComplete() from " + who);
                        }
                    }
                }
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        /**
         * Callback that arrives when a PendingIntent alarm has finished delivery
         */
        @Override
        public void onSendFinished(PendingIntent pi, Intent intent, int resultCode,
                String resultData, Bundle resultExtras) {
            Slog.d(TAG, "onSendFinished begin");
            synchronized (mLock) {
                updateTrackingLocked(removeLocked(pi, intent));
            }
        }

        /**
         * Timeout of a direct-call alarm delivery
         */
        public void alarmTimedOut(IBinder who) {
            synchronized (mLock) {
                InFlight inflight = removeLocked(who);
                if (inflight != null) {
                    // TODO: implement ANR policy for the target
                    if (DEBUG_LISTENER_CALLBACK) {
                        Slog.i(TAG, "Alarm listener " + who + " timed out in delivery");
                    }
                    updateTrackingLocked(inflight);
                } else {
                    if (DEBUG_LISTENER_CALLBACK) {
                        Slog.i(TAG, "Spurious timeout of listener " + who);
                    }
                }
            }
        }

        /**
         * Deliver an alarm and set up the post-delivery handling appropriately
         */
        public void deliverLocked(Alarm alarm, long nowELAPSED, boolean allowWhileIdle) {
            if (alarm.operation != null) {
                // PendingIntent alarm
                try {
                    alarm.operation.send(getContext(), 0,
                            mBackgroundIntent.putExtra(
                                    Intent.EXTRA_ALARM_COUNT, alarm.count),
                                    mDeliveryTracker, mHandler, null,
                                    allowWhileIdle ? mIdleOptions : null);
                     Slog.v(TAG, "sending alarm " + alarm + " success");
                } catch (PendingIntent.CanceledException e) {
                    if (alarm.repeatInterval > 0) {
                        // This IntentSender is no longer valid, but this
                        // is a repeating alarm, so toss it
                        mNeedRebatchForRepeatingAlarm = removeInvalidAlarmLocked(alarm.operation,
                                      alarm.listener) || mNeedRebatchForRepeatingAlarm;
                    }
                    // No actual delivery was possible, so the delivery tracker's
                    // 'finished' callback won't be invoked.  We also don't need
                    // to do any wakelock or stats tracking, so we have nothing
                    // left to do here but go on to the next thing.
                    return;
                }
            } else {
                // Direct listener callback alarm
                try {
                    if (DEBUG_LISTENER_CALLBACK) {
                        Slog.v(TAG, "Alarm to uid=" + alarm.uid
                                + " listener=" + alarm.listener.asBinder());
                    }
                    alarm.listener.doAlarm(this);
                    mHandler.sendMessageDelayed(
                            mHandler.obtainMessage(AlarmHandler.LISTENER_TIMEOUT,
                                    alarm.listener.asBinder()),
                            mConstants.LISTENER_TIMEOUT);
                } catch (Exception e) {
                    if (DEBUG_LISTENER_CALLBACK) {
                        Slog.i(TAG, "Alarm undeliverable to listener "
                                + alarm.listener.asBinder(), e);
                    }
                    // As in the PendingIntent.CanceledException case, delivery of the
                    // alarm was not possible, so we have no wakelock or timeout or
                    // stats management to do.  It threw before we posted the delayed
                    // timeout message, so we're done here.
                    return;
                }
            }

            // The alarm is now in flight; now arrange wakelock and stats tracking
            if (mBroadcastRefCount == 0) {
                setWakelockWorkSource(alarm.operation, alarm.workSource,
                        alarm.type, alarm.statsTag, (alarm.operation == null) ? alarm.uid : -1,
                        true);
                mWakeLock.acquire();
                mHandler.obtainMessage(AlarmHandler.REPORT_ALARMS_ACTIVE, 1).sendToTarget();
            }
            final InFlight inflight = new InFlight(AlarmManagerService.this,
                    alarm.operation, alarm.listener, alarm.workSource, alarm.uid,
                    alarm.packageName, alarm.type, alarm.statsTag, nowELAPSED);
            mInFlight.add(inflight);
            mBroadcastRefCount++;

            if (allowWhileIdle) {
                // Record the last time this uid handled an ALLOW_WHILE_IDLE alarm.
                mLastAllowWhileIdleDispatch.put(alarm.uid, nowELAPSED);
                if (RECORD_DEVICE_IDLE_ALARMS) {
                    IdleDispatchEntry ent = new IdleDispatchEntry();
                    ent.uid = alarm.uid;
                    ent.pkg = alarm.packageName;
                    ent.tag = alarm.statsTag;
                    ent.op = "DELIVER";
                    ent.elapsedRealtime = nowELAPSED;
                    mAllowWhileIdleDispatches.add(ent);
                }
            }

            final BroadcastStats bs = inflight.mBroadcastStats;
            bs.count++;
            if (bs.nesting == 0) {
                bs.nesting = 1;
                bs.startTime = nowELAPSED;
            } else {
                bs.nesting++;
            }
            final FilterStats fs = inflight.mFilterStats;
            fs.count++;
            if (fs.nesting == 0) {
                fs.nesting = 1;
                fs.startTime = nowELAPSED;
            } else {
                fs.nesting++;
            }
            if (alarm.type == ELAPSED_REALTIME_WAKEUP
                    || alarm.type == RTC_WAKEUP) {
                bs.numWakeup++;
                fs.numWakeup++;
                if (alarm.workSource != null && alarm.workSource.size() > 0) {
                    for (int wi=0; wi<alarm.workSource.size(); wi++) {
                        final String wsName = alarm.workSource.getName(wi);
                        ActivityManagerNative.noteWakeupAlarm(
                                alarm.operation, alarm.workSource.get(wi),
                                (wsName != null) ? wsName : alarm.packageName,
                                alarm.statsTag);
                    }
                } else {
                    ActivityManagerNative.noteWakeupAlarm(
                            alarm.operation, alarm.uid, alarm.packageName, alarm.statsTag);
                }
            }
        }
    }

    class DMReceiver extends BroadcastReceiver {
        public DMReceiver() {
            IntentFilter filter = new IntentFilter();
            filter.addAction("com.mediatek.dm.LAWMO_LOCK");
            filter.addAction("com.mediatek.dm.LAWMO_UNLOCK");
            filter.addAction("com.mediatek.ppl.NOTIFY_LOCK");
            filter.addAction("com.mediatek.ppl.NOTIFY_UNLOCK");
            getContext().registerReceiver(this, filter);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (action.equals("com.mediatek.dm.LAWMO_LOCK")) {
                mDMEnable = false;
            } else if (action.equals("com.mediatek.dm.LAWMO_UNLOCK")) {
                mDMEnable = true;
                enableDm();
            } else if (action.equals("com.mediatek.ppl.NOTIFY_LOCK")) {
                mPPLEnable = false;
            } else if (action.equals("com.mediatek.ppl.NOTIFY_UNLOCK")) {
                mPPLEnable = true;
                enableDm();
            }
        }
    }

    /**
     *For DM feature, to enable DM
     */
    public int enableDm() {

        synchronized (mDMLock) {
            if (mDMEnable && mPPLEnable) {
                    /*
                     * boolean needIcon = false; needIcon =
                     * SearchAlarmListForPackage(mRtcWakeupAlarms,
                     * mAlarmIconPackageList); if (!needIcon) { Intent
                     * alarmChanged = new
                     * Intent("android.intent.action.ALARM_CHANGED");
                     * alarmChanged.putExtra("alarmSet", false);
                     * mContext.sendBroadcast(alarmChanged); }
                     */
                    // Intent alarmChanged = new
                    // Intent("android.intent.action.ALARM_RESET");
                    // mContext.sendBroadcast(alarmChanged);
                    resendDmPendingList(mDmResendList);
                    mDmResendList = null;
                    mDmResendList = new ArrayList<Alarm>();
            }
        }
        return -1;
    }

    /*boolean SearchAlarmListForPackage(ArrayList<Alarm> mRtcWakeupAlarms,
            ArrayList<String> mAlarmIconPackageList) {
        for (int i = 0; i < mRtcWakeupAlarms.size(); i++) {
            Alarm tempAlarm = mRtcWakeupAlarms.get(i);
            for (int j = 0; j < mAlarmIconPackageList.size(); j++) {
                if (mAlarmIconPackageList.get(j).equals(tempAlarm.operation.getTargetPackage())) {
                    return true;
                }
            }
        }
        return false;
    }*/

    /**
     *For DM feature, to Free DmIntent
     */
    private void FreeDmIntent(ArrayList<Alarm> triggerList, ArrayList<PendingIntent> mDmFreeList,
                              long nowELAPSED, ArrayList<Alarm> resendList) {
        Iterator<Alarm> it = triggerList.iterator();
        boolean isFreeIntent = false;
        while (it.hasNext()) {
            isFreeIntent = false;
            Alarm alarm = it.next();
            // if with null operation, skip this alarm
            if (alarm.operation == null) {
                Slog.v(TAG, "FreeDmIntent skip with null operation APP listener("
                    + alarm.listenerTag + ") : type = " + alarm.type
                    + " triggerAtTime = " + alarm.when);
                continue;
            }
            try {
                for (int i = 0; i < mDmFreeList.size(); i++) {
                    if (alarm.operation.equals(mDmFreeList.get(i))) {
                        if (localLOGV)
                            Slog.v(TAG, "sending alarm " + alarm);
                        alarm.operation.send(getContext(), 0,
                                mBackgroundIntent.putExtra(
                                        Intent.EXTRA_ALARM_COUNT, alarm.count),
                                mDeliveryTracker, mHandler);
                        // we have an active broadcast so stay awake.
                        if (mBroadcastRefCount == 0) {
                setWakelockWorkSource(alarm.operation, alarm.workSource,
                    alarm.type, alarm.statsTag, alarm.uid,true);
                            mWakeLock.acquire();
                        }

            final InFlight inflight = new InFlight(AlarmManagerService.this,
                alarm.operation, alarm.listener, alarm.workSource, alarm.uid,
                alarm.packageName, alarm.type, alarm.statsTag, 0); //ALPS02190343

                        mInFlight.add(inflight);
                        mBroadcastRefCount++;
                        final BroadcastStats bs = inflight.mBroadcastStats;
                        bs.count++;
                        if (bs.nesting == 0) {
                            bs.nesting = 1;
                            bs.startTime = nowELAPSED;
                        } else {
                            bs.nesting++;
                        }
                        final FilterStats fs = inflight.mFilterStats;
                        fs.count++;
                        if (fs.nesting == 0) {
                            fs.nesting = 1;
                            fs.startTime = nowELAPSED;
                        } else {
                            fs.nesting++;
                        }
                        if (alarm.type == ELAPSED_REALTIME_WAKEUP
                                || alarm.type == RTC_WAKEUP) {
                            bs.numWakeup++;
                            fs.numWakeup++;
                            //ActivityManagerNative.noteWakeupAlarm(
                                    //alarm.operation);
                        }
                        isFreeIntent = true;
                        break;
                    }

                }
                if (!isFreeIntent) {
                    resendList.add(alarm);
                    isFreeIntent = false;
                }
            } catch (PendingIntent.CanceledException e) {
                if (alarm.repeatInterval > 0) {
                    // This IntentSender is no longer valid, but this
                    // is a repeating alarm, so toss the hoser.
                    //remove(alarm.operation);
                }
            }
        }
    }

    /**
     *For DM feature, to resend DmPendingList
     */
    private void resendDmPendingList(ArrayList<Alarm> DmResendList) {
        Iterator<Alarm> it = DmResendList.iterator();
        while (it.hasNext()) {
            Alarm alarm = it.next();
            // if with null operation, skip this alarm
            if (alarm.operation == null) {
                Slog.v(TAG, "resendDmPendingList skip with null operation, APP listener("
                    + alarm.listenerTag + ") : type = " + alarm.type
                    + " triggerAtTime = " + alarm.when);
                continue;
            }
            try {
                if (localLOGV)
                    Slog.v(TAG, "sending alarm " + alarm);
                alarm.operation.send(getContext(), 0,
                        mBackgroundIntent.putExtra(
                                Intent.EXTRA_ALARM_COUNT, alarm.count),
                                mDeliveryTracker, mHandler);

                // we have an active broadcast so stay awake.
                if (mBroadcastRefCount == 0) {
                    setWakelockWorkSource(alarm.operation, alarm.workSource,
                            alarm.type, alarm.statsTag, alarm.uid,true);
                    mWakeLock.acquire();
                }
                final InFlight inflight = new InFlight(AlarmManagerService.this,
                alarm.operation, alarm.listener, alarm.workSource, alarm.uid,
                alarm.packageName, alarm.type, alarm.statsTag, 0); //ALPS02190343
                mInFlight.add(inflight);
                mBroadcastRefCount++;
                final BroadcastStats bs = inflight.mBroadcastStats;
                bs.count++;
                if (bs.nesting == 0) {
                    bs.nesting = 1;
                    bs.startTime = SystemClock.elapsedRealtime();
                } else {
                    bs.nesting++;
                }
                final FilterStats fs = inflight.mFilterStats;
                fs.count++;
                if (fs.nesting == 0) {
                    fs.nesting = 1;
                    fs.startTime = SystemClock.elapsedRealtime();
                } else {
                    fs.nesting++;
                }
                if (alarm.type == ELAPSED_REALTIME_WAKEUP
                        || alarm.type == RTC_WAKEUP) {
                    bs.numWakeup++;
                    fs.numWakeup++;
                    //ActivityManagerNative.noteWakeupAlarm(
                           //alarm.operation);
                }
            } catch (PendingIntent.CanceledException e) {
                if (alarm.repeatInterval > 0) {
                    // This IntentSender is no longer valid, but this
                    // is a repeating alarm, so toss the hoser.
                    //remove(alarm.operation);
                }
            }
        }
    }

    /**
     *For PowerOffalarm feature, to query if boot from alarm
     */
    private boolean isBootFromAlarm(int fd) {
        return bootFromAlarm(fd);
    }

    /**
     *For PowerOffalarm feature, to update Poweroff Alarm
     */
    private void updatePoweroffAlarm(long nowRTC) {

        synchronized (mPowerOffAlarmLock) {

            if (mPoweroffAlarms.size() == 0) {

                return;
            }

            if (mPoweroffAlarms.get(0).when > nowRTC) {

                return;
            }

            Iterator<Alarm> it = mPoweroffAlarms.iterator();

            while (it.hasNext())
            {
                Alarm alarm = it.next();

                if (alarm.when > nowRTC) {
                    // don't fire alarms in the future
                    break;
                }
                Slog.w(TAG, "power off alarm update deleted");
                // remove the alarm from the list
                it.remove();
            }

            if (mPoweroffAlarms.size() > 0) {
                resetPoweroffAlarm(mPoweroffAlarms.get(0));
            }
        }
    }

    private int addPoweroffAlarmLocked(Alarm alarm) {
        ArrayList<Alarm> alarmList = mPoweroffAlarms;

        int index = Collections.binarySearch(alarmList, alarm, sIncreasingTimeOrder);
        if (index < 0) {
            index = 0 - index - 1;
        }
        if (localLOGV) Slog.v(TAG, "Adding alarm " + alarm + " at " + index);
        alarmList.add(index, alarm);

        if (localLOGV) {
            // Display the list of alarms for this alarm type
            Slog.v(TAG, "alarms: " + alarmList.size() + " type: " + alarm.type);
            int position = 0;
            for (Alarm a : alarmList) {
                Time time = new Time();
                time.set(a.when);
                String timeStr = time.format("%b %d %I:%M:%S %p");
                Slog.v(TAG, position + ": " + timeStr
                        + " " + a.operation.getTargetPackage());
                position += 1;
            }
        }

        return index;
    }

    private void removePoweroffAlarmLocked(String packageName) {
        ArrayList<Alarm> alarmList = mPoweroffAlarms;
        if (alarmList.size() <= 0) {
            return;
        }

        // iterator over the list removing any it where the intent match
        Iterator<Alarm> it = alarmList.iterator();

        while (it.hasNext()) {
            Alarm alarm = it.next();
            if (alarm.operation.getTargetPackage().equals(packageName)) {
                it.remove();
            }
        }
    }

    /**
     *For PowerOffalarm feature, this function is used for AlarmManagerService
     * to set the latest alarm registered
     */
    private void resetPoweroffAlarm(Alarm alarm) {

        String setPackageName = alarm.operation.getTargetPackage();
        long latestTime = alarm.when;

        // [Note] Power off Alarm +
        if (mNativeData != 0 && mNativeData != -1) {
            if (setPackageName.equals("com.android.deskclock")) {
                Slog.i(TAG, "mBootPackage = " + setPackageName + " set Prop 1");
                SystemProperties.set("persist.sys.bootpackage", "1"); // for
                                                                  // deskclock
                set(mNativeData, 6, latestTime / 1000, (latestTime % 1000) * 1000 * 1000);
            } else if (setPackageName.equals("com.mediatek.schpwronoff")) {
                Slog.i(TAG, "mBootPackage = " + setPackageName + " set Prop 2");
                SystemProperties.set("persist.sys.bootpackage", "2"); // for
                                                                  // settings
                set(mNativeData, 7, latestTime / 1000, (latestTime % 1000) * 1000 * 1000);
            // For settings to test powronoff
            } else if (setPackageName.equals("com.mediatek.poweronofftest")) {
                Slog.i(TAG, "mBootPackage = " + setPackageName + " set Prop 2");
                SystemProperties.set("persist.sys.bootpackage", "2"); // for
                                                                  // poweronofftest
                set(mNativeData, 7, latestTime / 1000, (latestTime % 1000) * 1000 * 1000);
            } else {
                Slog.w(TAG, "unknown package (" + setPackageName + ") to set power off alarm");
            }
        // [Note] Power off Alarm -

            Slog.i(TAG, "reset power off alarm is " + setPackageName);
            SystemProperties.set("sys.power_off_alarm", Long.toString(latestTime / 1000));
            } else {
            Slog.i(TAG, " do not set alarm to RTC when fd close ");
    }

    }

    /**
     * For PowerOffalarm feature, this function is used for APP to
     * cancelPoweroffAlarm
     */
    public void cancelPoweroffAlarmImpl(String name) {
        Slog.i(TAG, "remove power off alarm pacakge name " + name);
        // not need synchronized
        synchronized (mPowerOffAlarmLock) {
            removePoweroffAlarmLocked(name);
            // AlarmPair tempAlarmPair = mPoweroffAlarms.remove(name);
            // it will always to cancel the alarm in alarm driver
            String bootReason = SystemProperties.get("persist.sys.bootpackage");
            if (bootReason != null && mNativeData != 0 && mNativeData != -1) {
                if (bootReason.equals("1") && name.equals("com.android.deskclock")) {
                    set(mNativeData, 6, 0, 0);
                    SystemProperties.set("sys.power_off_alarm", Long.toString(0));
                } else if (bootReason.equals("2") && (name.equals("com.mediatek.schpwronoff")
                           || name.equals("com.mediatek.poweronofftest"))) {
                    set(mNativeData, 7, 0, 0);
                    SystemProperties.set("sys.power_off_alarm", Long.toString(0));
                }
            }
            if (mPoweroffAlarms.size() > 0) {
                resetPoweroffAlarm(mPoweroffAlarms.get(0));
            }
        }
    }

    /**
     * For IPO feature, this function is used for reset alarm when shut down
     */
    private void shutdownCheckPoweroffAlarm() {
        Slog.i(TAG, "into shutdownCheckPoweroffAlarm()!!");
        String setPackageName = null;
        long latestTime;
        long nowTime = System.currentTimeMillis();
        synchronized (mPowerOffAlarmLock) {
            Iterator<Alarm> it = mPoweroffAlarms.iterator();
            ArrayList<Alarm> mTempPoweroffAlarms = new ArrayList<Alarm>();
            while (it.hasNext()) {
                Alarm alarm = it.next();
                latestTime = alarm.when;
                setPackageName = alarm.operation.getTargetPackage();

                if ((latestTime - 30 * 1000) <= nowTime) {
                    Slog.i(TAG, "get target latestTime < 30S!!");
                    mTempPoweroffAlarms.add(alarm);
                }
            }
            Iterator<Alarm> tempIt = mTempPoweroffAlarms.iterator();
            while (tempIt.hasNext()) {
                Alarm alarm = tempIt.next();
                latestTime = alarm.when;
                    //set(alarm.type, (latestTime + 60 * 1000), 0, 0, alarm.operation, null);
                if (mNativeData != 0 && mNativeData != -1) {
                    set(mNativeData, alarm.type, latestTime / 1000, (latestTime % 1000) * 1000 * 1000);
                }
            }
        }
        Slog.i(TAG, "away shutdownCheckPoweroffAlarm()!!");
    }

    /**
     * For LCA project,AMS can remove alrms
     */
    public void removeFromAmsImpl(String packageName) {
        if (packageName == null) {
            return;
        }
        synchronized (mLock) {
            removeLocked(packageName);
        }
    }

    /**
     * For LCA project,AMS can query alrms
     */
    public boolean lookForPackageFromAmsImpl(String packageName) {
        if (packageName == null) {
            return false;
        }
        synchronized (mLock) {
            return lookForPackageLocked(packageName);
        }
    }
}
