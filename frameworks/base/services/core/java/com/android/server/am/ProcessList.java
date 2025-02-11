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

package com.android.server.am;

import static com.android.server.am.ActivityManagerDebugConfig.TAG_AM;
import static com.android.server.am.ActivityManagerDebugConfig.TAG_WITH_CLASS_NAME;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import android.app.ActivityManager;
import android.os.Build;
import android.os.SystemClock;
import com.android.internal.util.MemInfoReader;
import com.android.server.wm.WindowManagerService;

import android.content.res.Resources;
import android.graphics.Point;
import android.os.SystemProperties;
import android.net.LocalSocketAddress;
import android.net.LocalSocket;
import android.util.Slog;
import android.view.Display;
// add for adjustment of min free of low memory killer. prize-linkh-20160922
import com.mediatek.common.prizeoption.PrizeOption;

/**
 * Activity manager code dealing with processes.
 */
final class ProcessList {
    private static final String TAG = TAG_WITH_CLASS_NAME ? "ProcessList" : TAG_AM;

    // The minimum time we allow between crashes, for us to consider this
    // application to be bad and stop and its services and reject broadcasts.
    static final int MIN_CRASH_INTERVAL = 60*1000;

    // OOM adjustments for processes in various states:

    // Uninitialized value for any major or minor adj fields
    static final int INVALID_ADJ = -10000;

    // Adjustment used in certain places where we don't know it yet.
    // (Generally this is something that is going to be cached, but we
    // don't know the exact value in the cached range to assign yet.)
    static final int UNKNOWN_ADJ = 1001;

    // This is a process only hosting activities that are not visible,
    // so it can be killed without any disruption.
    static final int CACHED_APP_MAX_ADJ = 906;
    static final int CACHED_APP_MIN_ADJ = 900;

    // The B list of SERVICE_ADJ -- these are the old and decrepit
    // services that aren't as shiny and interesting as the ones in the A list.
    static final int SERVICE_B_ADJ = 800;

    // This is the process of the previous application that the user was in.
    // This process is kept above other things, because it is very common to
    // switch back to the previous app.  This is important both for recent
    // task switch (toggling between the two top recent apps) as well as normal
    // UI flow such as clicking on a URI in the e-mail app to view in the browser,
    // and then pressing back to return to e-mail.
    static final int PREVIOUS_APP_ADJ = 700;

    // This is a process holding the home application -- we want to try
    // avoiding killing it, even if it would normally be in the background,
    // because the user interacts with it so much.
    static final int HOME_APP_ADJ = 600;

    // This is a process holding an application service -- killing it will not
    // have much of an impact as far as the user is concerned.
    static final int SERVICE_ADJ = 500;

    // This is a process with a heavy-weight application.  It is in the
    // background, but we want to try to avoid killing it.  Value set in
    // system/rootdir/init.rc on startup.
    static final int HEAVY_WEIGHT_APP_ADJ = 400;

    // This is a process currently hosting a backup operation.  Killing it
    // is not entirely fatal but is generally a bad idea.
    static final int BACKUP_APP_ADJ = 300;

    // This is a process only hosting components that are perceptible to the
    // user, and we really want to avoid killing them, but they are not
    // immediately visible. An example is background music playback.
    static final int PERCEPTIBLE_APP_ADJ = 200;

    // This is a process only hosting activities that are visible to the
    // user, so we'd prefer they don't disappear.
    static final int VISIBLE_APP_ADJ = 100;
    static final int VISIBLE_APP_LAYER_MAX = PERCEPTIBLE_APP_ADJ - VISIBLE_APP_ADJ - 1;

    // This is the process running the current foreground app.  We'd really
    // rather not kill it!
    static final int FOREGROUND_APP_ADJ = 0;

    // This is a process that the system or a persistent process has bound to,
    // and indicated it is important.
    static final int PERSISTENT_SERVICE_ADJ = -700;

    // This is a system persistent process, such as telephony.  Definitely
    // don't want to kill it, but doing so is not completely fatal.
    static final int PERSISTENT_PROC_ADJ = -800;

    // The system process runs at the default adjustment.
    static final int SYSTEM_ADJ = -900;

    // Special code for native processes that are not being managed by the system (so
    // don't have an oom adj assigned by the system).
    static final int NATIVE_ADJ = -1000;

    // Memory pages are 4K.
    static final int PAGE_SIZE = 4*1024;

    // Activity manager's version of Process.THREAD_GROUP_BG_NONINTERACTIVE
    static final int SCHED_GROUP_BACKGROUND = 0;
    // Activity manager's version of Process.THREAD_GROUP_DEFAULT
    static final int SCHED_GROUP_DEFAULT = 1;
    // Activity manager's version of Process.THREAD_GROUP_TOP_APP
    static final int SCHED_GROUP_TOP_APP = 2;
    // Activity manager's version of Process.THREAD_GROUP_TOP_APP
    // Disambiguate between actual top app and processes bound to the top app
    static final int SCHED_GROUP_TOP_APP_BOUND = 3;

    // The minimum number of cached apps we want to be able to keep around,
    // without empty apps being able to push them out of memory.
    static final int MIN_CACHED_APPS = 2;

    // The maximum number of cached processes we will keep around before killing them.
    // NOTE: this constant is *only* a control to not let us go too crazy with
    // keeping around processes on devices with large amounts of RAM.  For devices that
    // are tighter on RAM, the out of memory killer is responsible for killing background
    // processes as RAM is needed, and we should *never* be relying on this limit to
    // kill them.  Also note that this limit only applies to cached background processes;
    // we have no limit on the number of service, visible, foreground, or other such
    // processes and the number of those processes does not count against the cached
    // process limit.
    static final int MAX_CACHED_APPS = 32;

    // We allow empty processes to stick around for at most 30 minutes.
    static final long MAX_EMPTY_TIME = 30*60*1000;

    // The maximum number of empty app processes we will let sit around.
    private static final int MAX_EMPTY_APPS = computeEmptyProcessLimit(MAX_CACHED_APPS);

    // The number of empty apps at which we don't consider it necessary to do
    // memory trimming.
    static final int TRIM_EMPTY_APPS = MAX_EMPTY_APPS/2;

    // The number of cached at which we don't consider it necessary to do
    // memory trimming.
    static final int TRIM_CACHED_APPS = (MAX_CACHED_APPS-MAX_EMPTY_APPS)/3;

    // Threshold of number of cached+empty where we consider memory critical.
    static final int TRIM_CRITICAL_THRESHOLD = 3;

    // Threshold of number of cached+empty where we consider memory critical.
    static final int TRIM_LOW_THRESHOLD = 5;

    // Low Memory Killer Daemon command codes.
    // These must be kept in sync with the definitions in lmkd.c
    //
    // LMK_TARGET <minfree> <minkillprio> ... (up to 6 pairs)
    // LMK_PROCPRIO <pid> <uid> <prio>
    // LMK_PROCREMOVE <pid>
    static final byte LMK_TARGET = 0;
    static final byte LMK_PROCPRIO = 1;
    static final byte LMK_PROCREMOVE = 2;

    // These are the various interesting memory levels that we will give to
    // the OOM killer.  Note that the OOM killer only supports 6 slots, so we
    // can't give it a different value for every possible kind of process.
    private int[] mOomAdj;

    // These are the low-end OOM level limits.  This is appropriate for an
    // HVGA or smaller phone with less than 512MB.  Values are in KB.
    private final int[] mOomMinFreeLow = new int[] {
            12288, 18432, 24576,
            36864, 43008, 49152
    };
    // These are the high-end OOM level limits.  This is appropriate for a
    // 1280x800 or larger screen with around 1GB RAM.  Values are in KB.
    private final int[] mOomMinFreeHigh = new int[] {
            73728, 92160, 110592,
            129024, 147456, 184320
    };
    
    // add for adjustment of min free of low memory killer. prize-linkh-20160922
    // Note: We can retrieve data from resource and system property. 
    //       If both are defined, then we use data prior that are from system property.
    private static final String TAG_LMK = "LMK";
    private static final String SYS_PROP_MIN_FREE = "persist.sys.lmk.min.free";
    // retrieve data from resource.
    private boolean mCustomMinFreeFromResource;
    // retrieve data from system property.
    private boolean mCustomMinFreeFromSysProp;
    // END....
    
    // The actual OOM killer memory levels we are using.
    private int[] mOomMinFree;

    private final long mTotalMemMb;

    /// M: Define mOomMinFree[] and mOomAdj[] by project @{
    final long LOW_LEVEL_SIZE = 512;
    final long HIGH_LEVEL_SIZE = 1024;
    /// @}

    private long mCachedRestoreLevel;

    private boolean mHaveDisplaySize;

    private static LocalSocket sLmkdSocket;
    private static OutputStream sLmkdOutputStream;

    ProcessList() {
        MemInfoReader minfo = new MemInfoReader();
        minfo.readMemInfo();
        mTotalMemMb = minfo.getTotalSize()/(1024*1024);

        /// M: Define mOomMinFree[] and mOomAdj[] by project @{
        mOomAdj = Resources.getSystem().getIntArray(
            com.mediatek.internal.R.array.config_lowMemoryKillerOomAdj);
        mOomMinFree = Resources.getSystem().getIntArray(
            com.mediatek.internal.R.array.config_lowMemoryKillerMinFreeKbytes);

        if ((mOomAdj == null) || (mOomMinFree == null) || (mOomMinFree[0] == 0)) {
            if (mTotalMemMb <= LOW_LEVEL_SIZE) {
                mOomAdj = new int[] {
                    FOREGROUND_APP_ADJ,
                    VISIBLE_APP_ADJ,
                    PERCEPTIBLE_APP_ADJ,
                    BACKUP_APP_ADJ,
                    CACHED_APP_MIN_ADJ,
                    CACHED_APP_MAX_ADJ
                };
                mOomMinFree = new int[] {
                    24576, // 24 * 1024 (ADJ 0 -> 24MB)
                    31744, // 31 * 1024 (ADJ 1 -> 31MB)
                    38912, // 38 * 1024 (ADJ 2 -> 38MB)
                    49152, // 48 * 1024 (ADJ 3 -> 48MB)
                    122880, // 120 * 1024 (ADJ 9 -> 120MB) (based on AMR performance test)
                    122880  // 120 * 1024 (ADJ 15 -> 120MB) (based on AMR performance test)
                };
            } else if (mTotalMemMb <= HIGH_LEVEL_SIZE) {
                mOomAdj = new int[] {
                    FOREGROUND_APP_ADJ,
                    VISIBLE_APP_ADJ,
                    PERCEPTIBLE_APP_ADJ,
                    BACKUP_APP_ADJ,
                    CACHED_APP_MIN_ADJ,
                    CACHED_APP_MAX_ADJ
                };
                mOomMinFree = new int[] {
                    36864, // 36 * 1024 (ADJ 0 -> 36MB)
                    49152, // 48 * 1024 (ADJ 1 -> 48MB)
                    61440, // 60 * 1024 (ADJ 2 -> 60MB)
                    73728, // 72 * 1024 (ADJ 3 -> 72MB)
                    204800, // 200 * 1024 (ADJ 9 -> 200MB) (based on performance test)
                    296960  // 290 * 1024 (ADJ 15 -> 290MB (= 200MB x 1.25 x 1.75 / 1.5)
                };
            } else {
                // These are the various interesting memory levels that we will give to
                // the OOM killer.  Note that the OOM killer only supports 6 slots, so we
                // can't give it a different value for every possible kind of process.
                mOomAdj = new int[] {
                    FOREGROUND_APP_ADJ, VISIBLE_APP_ADJ, PERCEPTIBLE_APP_ADJ,
                    BACKUP_APP_ADJ, CACHED_APP_MIN_ADJ, CACHED_APP_MAX_ADJ
                };
                // The actual OOM killer memory levels we are using.
                mOomMinFree = new int[mOomAdj.length];
            }
        }
        /// @}

        // add for adjustment of min free of low memory killer. prize-linkh-20160922
        String minFree = SystemProperties.get(SYS_PROP_MIN_FREE, "0");
        if ("0".equals(minFree)) {
            if (PrizeOption.PRIZE_LOW_MEMORY_OPTIMIZE) {
                mCustomMinFreeFromResource = Resources.getSystem().getBoolean(
                        com.prize.internal.R.bool.enable_custom_lmk_min_free);
            } else {
                mCustomMinFreeFromResource = Resources.getSystem().getBoolean(
                        com.prize.internal.R.bool.enable_custom_lmk_min_free_for_high_ram);
            }
            
            if (mCustomMinFreeFromResource) {
                final int[] oomAdj = new int[] {
                    FOREGROUND_APP_ADJ, VISIBLE_APP_ADJ, PERCEPTIBLE_APP_ADJ,
                    BACKUP_APP_ADJ, CACHED_APP_MIN_ADJ, CACHED_APP_MAX_ADJ
                };
                
                final int[] oomMinFree = Resources.getSystem().getIntArray(
                    com.prize.internal.R.array.custom_lmk_min_free);
                
                if (oomAdj.length != oomMinFree.length) {
                    Slog.w(TAG_LMK, "Invalid custom min free. request size " + oomAdj.length
                                + ", real size " + oomMinFree.length);
                    // disable.
                    mCustomMinFreeFromResource = false;
                } else {
                    mOomAdj = oomAdj;
                    mOomMinFree = oomMinFree;
                    Slog.i(TAG_LMK, "Enable custom min free from resource!");
                }
            }
        } else {
            final String[] minFreeArray = minFree.split(",");
            if (minFreeArray != null && minFreeArray.length == 6) {
                final int[] oomMinFree = new int[minFreeArray.length];
                boolean success = true;
                for (int i = 0; i < minFreeArray.length; ++i) {
                    try {
                        // assume these value are KB unit.
                        oomMinFree[i] = Integer.parseInt(minFreeArray[i]);
                    } catch (NumberFormatException e) {
                        success = false;
                    }
            
                    if (!success) {
                        break;
                    }
                }
            
                if (success) {
                    mOomAdj = new int[] {
                        FOREGROUND_APP_ADJ, VISIBLE_APP_ADJ, PERCEPTIBLE_APP_ADJ,
                        BACKUP_APP_ADJ, CACHED_APP_MIN_ADJ, CACHED_APP_MAX_ADJ
                    };
                    
                    mOomMinFree = oomMinFree;
                    mCustomMinFreeFromSysProp = true;
                    Slog.i(TAG_LMK, "Enable custom min free from system prop!");
                } else {
                    Slog.w(TAG_LMK, "invalid min free from system prop!");
                }
            } else {
                Slog.w(TAG_LMK, "invalid min free from system prop!");
            }
        }
        //END....
        updateOomLevels(0, 0, false);
    }
    
    /* PrizeDebugTool: dynamically adjust min free of LMK. prize-linkh-20160923 */
    int[] getOomAdjOfLMK() {
        if (mOomAdj == null) {
            return null;
        }
        
        int[] oomAdj = new int[mOomAdj.length];
        for (int i = 0; i < mOomAdj.length; ++i) {
            oomAdj[i] = mOomAdj[i];
        }

        return oomAdj;
    }
    
    int[] getMinfreeOfLMK() {
        if (mOomMinFree == null) {
            return null;
        }
        
        int[] minfree = new int[mOomMinFree.length];
        for (int i = 0; i < mOomMinFree.length; ++i) {
            minfree[i] = mOomMinFree[i];
        }

        return minfree;
    }
    
    void setMinfreeOfLMK(int[] minfree) {
        if (mOomMinFree == null || minfree == null) {
            return;
        }

        if (minfree.length == 1 && minfree[0] == 0) {
            //Restore min free.            
            Slog.i(TAG_LMK, "Restore min free!");
            if (!"0".equals(SystemProperties.get(SYS_PROP_MIN_FREE, "0"))) {
                SystemProperties.set(SYS_PROP_MIN_FREE, "0");
            }
        } else if (minfree.length == mOomMinFree.length) {
            boolean hasSame = true;
            for (int i = 0; i < minfree.length; ++i) {
                if (minfree[i] != mOomMinFree[i]) {
                    hasSame = false;
                    break;
                }
            }

            if (hasSame) {
                Slog.i(TAG_LMK, "Same as existent min free!");
            } else {                
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < minfree.length; ++i) {
                    sb.append(minfree[i]);
                    if (i < minfree.length - 1) {
                        sb.append(",");
                    }
                }
                
                Slog.i(TAG_LMK, "Set new min free [" + sb.toString() + "]");
                SystemProperties.set(SYS_PROP_MIN_FREE, sb.toString());           
            }
        }
    }

    private void printLmkInfo(int adj, int minfree) {        
        StringBuilder sb = new StringBuilder();
        sb.append("Adj ");
        sb.append(adj);
        sb.append(" => ");
        sb.append(minfree / 1024);
        sb.append("M (");
        sb.append((minfree * 1024) / PAGE_SIZE);
        sb.append(" pages)");                    
        Slog.i(TAG_LMK, sb.toString());
    }
    // END....

    void applyDisplaySize(WindowManagerService wm) {
        if (!mHaveDisplaySize) {
            Point p = new Point();
            wm.getBaseDisplaySize(Display.DEFAULT_DISPLAY, p);
            if (p.x != 0 && p.y != 0) {
                updateOomLevels(p.x, p.y, true);
                mHaveDisplaySize = true;
            }
        }
    }

    private void updateOomLevels(int displayWidth, int displayHeight, boolean write) {
        // add for adjustment of min free of low memory killer. prize-linkh-20160922
        if (mCustomMinFreeFromSysProp || mCustomMinFreeFromResource) {
            Slog.i(TAG_LMK, "Apply custom min free. from sys prop ? " + mCustomMinFreeFromSysProp);
            
            mCachedRestoreLevel = (getMemLevel(ProcessList.CACHED_APP_MAX_ADJ) / 1024) / 3;
            Slog.i(TAG_LMK, "mCachedRestoreLevel = " + Long.toString(mCachedRestoreLevel));

            if (write) {
                ByteBuffer buf = ByteBuffer.allocate(4 * (2 * mOomAdj.length + 1));
                buf.putInt(LMK_TARGET);
                for (int i = 0; i < mOomAdj.length; i++) {
                    buf.putInt((mOomMinFree[i] * 1024) / PAGE_SIZE);
                    buf.putInt(mOomAdj[i]);

                    printLmkInfo(mOomAdj[i], mOomMinFree[i]);
                }
                writeLmkd(buf);
            }

            return;

        } //END...

        /// M: Define mOomMinFree[] and mOomAdj[] by project @{
        if (mTotalMemMb <= HIGH_LEVEL_SIZE) {
            // Set mCachedRestoreLevel
            mCachedRestoreLevel = (getMemLevel(ProcessList.CACHED_APP_MAX_ADJ) / 1024) / 3;
            Slog.i(TAG, "mCachedRestoreLevel = " + Long.toString(mCachedRestoreLevel));

            if (write) {
                ByteBuffer buf = ByteBuffer.allocate(4 * (2 * mOomAdj.length + 1));
                buf.putInt(LMK_TARGET);
                for (int i = 0; i < mOomAdj.length; i++) {
                    buf.putInt((mOomMinFree[i] * 1024) / PAGE_SIZE);
                    buf.putInt(mOomAdj[i]);
                }
                writeLmkd(buf);
                // Reserve extra free kbytes
                if (mTotalMemMb > LOW_LEVEL_SIZE) {
                    int reserve = Resources.getSystem().getInteger
                            (com.mediatek.internal.R.integer.config_extraFreeKbytes);
                    if (reserve < 0) {
                        reserve = displayWidth * displayHeight * 4 * 3 / 1024;
                    }
                    SystemProperties.set("sys.sysctl.extra_free_kbytes", Integer.toString(reserve));
                }
            }
            return;
        }
        /// @}

        // Scale buckets from avail memory: at 300MB we use the lowest values to
        // 700MB or more for the top values.
        float scaleMem = ((float)(mTotalMemMb-350))/(700-350);

        // Scale buckets from screen size.
        int minSize = 480*800;  //  384000
        int maxSize = 1280*800; // 1024000  230400 870400  .264
        float scaleDisp = ((float)(displayWidth*displayHeight)-minSize)/(maxSize-minSize);
        if (false) {
            Slog.i("XXXXXX", "scaleMem=" + scaleMem);
            Slog.i("XXXXXX", "scaleDisp=" + scaleDisp + " dw=" + displayWidth
                    + " dh=" + displayHeight);
        }

        float scale = scaleMem > scaleDisp ? scaleMem : scaleDisp;
        if (scale < 0) scale = 0;
        else if (scale > 1) scale = 1;
        int minfree_adj = Resources.getSystem().getInteger(
                com.android.internal.R.integer.config_lowMemoryKillerMinFreeKbytesAdjust);
        int minfree_abs = Resources.getSystem().getInteger(
                com.android.internal.R.integer.config_lowMemoryKillerMinFreeKbytesAbsolute);
        if (false) {
            Slog.i("XXXXXX", "minfree_adj=" + minfree_adj + " minfree_abs=" + minfree_abs);
        }

        final boolean is64bit = Build.SUPPORTED_64_BIT_ABIS.length > 0;

        for (int i=0; i<mOomAdj.length; i++) {
            int low = mOomMinFreeLow[i];
            int high = mOomMinFreeHigh[i];
			//adjust LMK for 2G RAM by hushuai 20171214
			if(mTotalMemMb<=2048){
			
			mOomMinFree = new int[] {
                    36864, // 36 * 1024 (ADJ 0 -> 36MB)
                    49152, // 48 * 1024 (ADJ 1 -> 48MB)
                    61440, // 60 * 1024 (ADJ 2 -> 60MB)
                    73728, // 72 * 1024 (ADJ 3 -> 72MB)
                    204800, // 200 * 1024 (ADJ 9 -> 200MB) (based on performance test)
                    296960  // 290 * 1024 (ADJ 15 -> 290MB (= 200MB x 1.25 x 1.75 / 1.5)
                };
			
			}else{
            if (is64bit) {
                // Increase the high min-free levels for cached processes for 64-bit
                if (i == 4) high = (high*3)/2;
                else if (i == 5) high = (high*7)/4;
            }
            mOomMinFree[i] = (int)(low + ((high-low)*scale));
           }
        }

        if (minfree_abs >= 0) {
            for (int i=0; i<mOomAdj.length; i++) {
                mOomMinFree[i] = (int)((float)minfree_abs * mOomMinFree[i]
                        / mOomMinFree[mOomAdj.length - 1]);
            }
        }

        if (minfree_adj != 0) {
            for (int i=0; i<mOomAdj.length; i++) {
                mOomMinFree[i] += (int)((float)minfree_adj * mOomMinFree[i]
                        / mOomMinFree[mOomAdj.length - 1]);
                if (mOomMinFree[i] < 0) {
                    mOomMinFree[i] = 0;
                }
            }
        }

        // The maximum size we will restore a process from cached to background, when under
        // memory duress, is 1/3 the size we have reserved for kernel caches and other overhead
        // before killing background processes.
        mCachedRestoreLevel = (getMemLevel(ProcessList.CACHED_APP_MAX_ADJ)/1024) / 3;
        Slog.i(TAG, "mCachedRestoreLevel = " + Long.toString(mCachedRestoreLevel));

        // Ask the kernel to try to keep enough memory free to allocate 3 full
        // screen 32bpp buffers without entering direct reclaim.
        int reserve = displayWidth * displayHeight * 4 * 3 / 1024;
        int reserve_adj = Resources.getSystem().getInteger(com.android.internal.R.integer.config_extraFreeKbytesAdjust);
        int reserve_abs = Resources.getSystem().getInteger(com.android.internal.R.integer.config_extraFreeKbytesAbsolute);

        if (reserve_abs >= 0) {
            reserve = reserve_abs;
        }

        if (reserve_adj != 0) {
            reserve += reserve_adj;
            if (reserve < 0) {
                reserve = 0;
            }
        }

        if (write) {
            ByteBuffer buf = ByteBuffer.allocate(4 * (2*mOomAdj.length + 1));
            buf.putInt(LMK_TARGET);
            for (int i=0; i<mOomAdj.length; i++) {
                buf.putInt((mOomMinFree[i]*1024)/PAGE_SIZE);
                buf.putInt(mOomAdj[i]);

                // Log this information. prize-linkh-20160930
                printLmkInfo(mOomAdj[i], mOomMinFree[i]);
                // END...
            }

            writeLmkd(buf);
            SystemProperties.set("sys.sysctl.extra_free_kbytes", Integer.toString(reserve));
        }
        // GB: 2048,3072,4096,6144,7168,8192
        // HC: 8192,10240,12288,14336,16384,20480
    }

    public static int computeEmptyProcessLimit(int totalProcessLimit) {
        return totalProcessLimit/2;
    }

    private static String buildOomTag(String prefix, String space, int val, int base) {
        if (val == base) {
            if (space == null) return prefix;
            return prefix + "  ";
        }
        return prefix + "+" + Integer.toString(val-base);
    }

    public static String makeOomAdjString(int setAdj) {
        if (setAdj >= ProcessList.CACHED_APP_MIN_ADJ) {
            return buildOomTag("cch", "  ", setAdj, ProcessList.CACHED_APP_MIN_ADJ);
        } else if (setAdj >= ProcessList.SERVICE_B_ADJ) {
            return buildOomTag("svcb ", null, setAdj, ProcessList.SERVICE_B_ADJ);
        } else if (setAdj >= ProcessList.PREVIOUS_APP_ADJ) {
            return buildOomTag("prev ", null, setAdj, ProcessList.PREVIOUS_APP_ADJ);
        } else if (setAdj >= ProcessList.HOME_APP_ADJ) {
            return buildOomTag("home ", null, setAdj, ProcessList.HOME_APP_ADJ);
        } else if (setAdj >= ProcessList.SERVICE_ADJ) {
            return buildOomTag("svc  ", null, setAdj, ProcessList.SERVICE_ADJ);
        } else if (setAdj >= ProcessList.HEAVY_WEIGHT_APP_ADJ) {
            return buildOomTag("hvy  ", null, setAdj, ProcessList.HEAVY_WEIGHT_APP_ADJ);
        } else if (setAdj >= ProcessList.BACKUP_APP_ADJ) {
            return buildOomTag("bkup ", null, setAdj, ProcessList.BACKUP_APP_ADJ);
        } else if (setAdj >= ProcessList.PERCEPTIBLE_APP_ADJ) {
            return buildOomTag("prcp ", null, setAdj, ProcessList.PERCEPTIBLE_APP_ADJ);
        } else if (setAdj >= ProcessList.VISIBLE_APP_ADJ) {
            return buildOomTag("vis  ", null, setAdj, ProcessList.VISIBLE_APP_ADJ);
        } else if (setAdj >= ProcessList.FOREGROUND_APP_ADJ) {
            return buildOomTag("fore ", null, setAdj, ProcessList.FOREGROUND_APP_ADJ);
        } else if (setAdj >= ProcessList.PERSISTENT_SERVICE_ADJ) {
            return buildOomTag("psvc ", null, setAdj, ProcessList.PERSISTENT_SERVICE_ADJ);
        } else if (setAdj >= ProcessList.PERSISTENT_PROC_ADJ) {
            return buildOomTag("pers ", null, setAdj, ProcessList.PERSISTENT_PROC_ADJ);
        } else if (setAdj >= ProcessList.SYSTEM_ADJ) {
            return buildOomTag("sys  ", null, setAdj, ProcessList.SYSTEM_ADJ);
        } else if (setAdj >= ProcessList.NATIVE_ADJ) {
            return buildOomTag("ntv  ", null, setAdj, ProcessList.NATIVE_ADJ);
        } else {
            return Integer.toString(setAdj);
        }
    }

    public static String makeProcStateString(int curProcState) {
        String procState;
        switch (curProcState) {
            case -1:
                procState = "N ";
                break;
            case ActivityManager.PROCESS_STATE_PERSISTENT:
                procState = "P ";
                break;
            case ActivityManager.PROCESS_STATE_PERSISTENT_UI:
                procState = "PU";
                break;
            case ActivityManager.PROCESS_STATE_TOP:
                procState = "T ";
                break;
            case ActivityManager.PROCESS_STATE_BOUND_FOREGROUND_SERVICE:
                procState = "SB";
                break;
            case ActivityManager.PROCESS_STATE_FOREGROUND_SERVICE:
                procState = "SF";
                break;
            case ActivityManager.PROCESS_STATE_TOP_SLEEPING:
                procState = "TS";
                break;
            case ActivityManager.PROCESS_STATE_IMPORTANT_FOREGROUND:
                procState = "IF";
                break;
            case ActivityManager.PROCESS_STATE_IMPORTANT_BACKGROUND:
                procState = "IB";
                break;
            case ActivityManager.PROCESS_STATE_BACKUP:
                procState = "BU";
                break;
            case ActivityManager.PROCESS_STATE_HEAVY_WEIGHT:
                procState = "HW";
                break;
            case ActivityManager.PROCESS_STATE_SERVICE:
                procState = "S ";
                break;
            case ActivityManager.PROCESS_STATE_RECEIVER:
                procState = "R ";
                break;
            case ActivityManager.PROCESS_STATE_HOME:
                procState = "HO";
                break;
            case ActivityManager.PROCESS_STATE_LAST_ACTIVITY:
                procState = "LA";
                break;
            case ActivityManager.PROCESS_STATE_CACHED_ACTIVITY:
                procState = "CA";
                break;
            case ActivityManager.PROCESS_STATE_CACHED_ACTIVITY_CLIENT:
                procState = "Ca";
                break;
            case ActivityManager.PROCESS_STATE_CACHED_EMPTY:
                procState = "CE";
                break;
            default:
                procState = "??";
                break;
        }
        return procState;
    }

    public static void appendRamKb(StringBuilder sb, long ramKb) {
        for (int j=0, fact=10; j<6; j++, fact*=10) {
            if (ramKb < fact) {
                sb.append(' ');
            }
        }
        sb.append(ramKb);
    }

    // How long after a state change that it is safe to collect PSS without it being dirty.
    public static final int PSS_SAFE_TIME_FROM_STATE_CHANGE = 1000;

    // The minimum time interval after a state change it is safe to collect PSS.
    public static final int PSS_MIN_TIME_FROM_STATE_CHANGE = 15*1000;

    // The maximum amount of time we want to go between PSS collections.
    public static final int PSS_MAX_INTERVAL = 30*60*1000;

    // The minimum amount of time between successive PSS requests for *all* processes.
    public static final int PSS_ALL_INTERVAL = 10*60*1000;

    // The minimum amount of time between successive PSS requests for a process.
    private static final int PSS_SHORT_INTERVAL = 2*60*1000;

    // The amount of time until PSS when a process first becomes top.
    private static final int PSS_FIRST_TOP_INTERVAL = 10*1000;

    // The amount of time until PSS when a process first goes into the background.
    private static final int PSS_FIRST_BACKGROUND_INTERVAL = 20*1000;

    // The amount of time until PSS when a process first becomes cached.
    private static final int PSS_FIRST_CACHED_INTERVAL = 30*1000;

    // The amount of time until PSS when an important process stays in the same state.
    private static final int PSS_SAME_IMPORTANT_INTERVAL = 15*60*1000;

    // The amount of time until PSS when a service process stays in the same state.
    private static final int PSS_SAME_SERVICE_INTERVAL = 20*60*1000;

    // The amount of time until PSS when a cached process stays in the same state.
    private static final int PSS_SAME_CACHED_INTERVAL = 30*60*1000;

    // The minimum time interval after a state change it is safe to collect PSS.
    public static final int PSS_TEST_MIN_TIME_FROM_STATE_CHANGE = 10*1000;

    // The amount of time during testing until PSS when a process first becomes top.
    private static final int PSS_TEST_FIRST_TOP_INTERVAL = 3*1000;

    // The amount of time during testing until PSS when a process first goes into the background.
    private static final int PSS_TEST_FIRST_BACKGROUND_INTERVAL = 5*1000;

    // The amount of time during testing until PSS when an important process stays in same state.
    private static final int PSS_TEST_SAME_IMPORTANT_INTERVAL = 10*1000;

    // The amount of time during testing until PSS when a background process stays in same state.
    private static final int PSS_TEST_SAME_BACKGROUND_INTERVAL = 15*1000;

    public static final int PROC_MEM_PERSISTENT = 0;
    public static final int PROC_MEM_TOP = 1;
    public static final int PROC_MEM_IMPORTANT = 2;
    public static final int PROC_MEM_SERVICE = 3;
    public static final int PROC_MEM_CACHED = 4;

    private static final int[] sProcStateToProcMem = new int[] {
        PROC_MEM_PERSISTENT,            // ActivityManager.PROCESS_STATE_PERSISTENT
        PROC_MEM_PERSISTENT,            // ActivityManager.PROCESS_STATE_PERSISTENT_UI
        PROC_MEM_TOP,                   // ActivityManager.PROCESS_STATE_TOP
        PROC_MEM_IMPORTANT,             // ActivityManager.PROCESS_STATE_BOUND_FOREGROUND_SERVICE
        PROC_MEM_IMPORTANT,             // ActivityManager.PROCESS_STATE_FOREGROUND_SERVICE
        PROC_MEM_TOP,                   // ActivityManager.PROCESS_STATE_TOP_SLEEPING
        PROC_MEM_IMPORTANT,             // ActivityManager.PROCESS_STATE_IMPORTANT_FOREGROUND
        PROC_MEM_IMPORTANT,             // ActivityManager.PROCESS_STATE_IMPORTANT_BACKGROUND
        PROC_MEM_IMPORTANT,             // ActivityManager.PROCESS_STATE_BACKUP
        PROC_MEM_IMPORTANT,             // ActivityManager.PROCESS_STATE_HEAVY_WEIGHT
        PROC_MEM_SERVICE,               // ActivityManager.PROCESS_STATE_SERVICE
        PROC_MEM_CACHED,                // ActivityManager.PROCESS_STATE_RECEIVER
        PROC_MEM_CACHED,                // ActivityManager.PROCESS_STATE_HOME
        PROC_MEM_CACHED,                // ActivityManager.PROCESS_STATE_LAST_ACTIVITY
        PROC_MEM_CACHED,                // ActivityManager.PROCESS_STATE_CACHED_ACTIVITY
        PROC_MEM_CACHED,                // ActivityManager.PROCESS_STATE_CACHED_ACTIVITY_CLIENT
        PROC_MEM_CACHED,                // ActivityManager.PROCESS_STATE_CACHED_EMPTY
    };

    private static final long[] sFirstAwakePssTimes = new long[] {
        PSS_SHORT_INTERVAL,             // ActivityManager.PROCESS_STATE_PERSISTENT
        PSS_SHORT_INTERVAL,             // ActivityManager.PROCESS_STATE_PERSISTENT_UI
        PSS_FIRST_TOP_INTERVAL,         // ActivityManager.PROCESS_STATE_TOP
        PSS_FIRST_BACKGROUND_INTERVAL,  // ActivityManager.PROCESS_STATE_BOUND_FOREGROUND_SERVICE
        PSS_FIRST_BACKGROUND_INTERVAL,  // ActivityManager.PROCESS_STATE_FOREGROUND_SERVICE
        PSS_FIRST_BACKGROUND_INTERVAL,  // ActivityManager.PROCESS_STATE_TOP_SLEEPING
        PSS_FIRST_BACKGROUND_INTERVAL,  // ActivityManager.PROCESS_STATE_IMPORTANT_FOREGROUND
        PSS_FIRST_BACKGROUND_INTERVAL,  // ActivityManager.PROCESS_STATE_IMPORTANT_BACKGROUND
        PSS_FIRST_BACKGROUND_INTERVAL,  // ActivityManager.PROCESS_STATE_BACKUP
        PSS_FIRST_BACKGROUND_INTERVAL,  // ActivityManager.PROCESS_STATE_HEAVY_WEIGHT
        PSS_FIRST_BACKGROUND_INTERVAL,  // ActivityManager.PROCESS_STATE_SERVICE
        PSS_FIRST_CACHED_INTERVAL,      // ActivityManager.PROCESS_STATE_RECEIVER
        PSS_FIRST_CACHED_INTERVAL,      // ActivityManager.PROCESS_STATE_HOME
        PSS_FIRST_CACHED_INTERVAL,      // ActivityManager.PROCESS_STATE_LAST_ACTIVITY
        PSS_FIRST_CACHED_INTERVAL,      // ActivityManager.PROCESS_STATE_CACHED_ACTIVITY
        PSS_FIRST_CACHED_INTERVAL,      // ActivityManager.PROCESS_STATE_CACHED_ACTIVITY_CLIENT
        PSS_FIRST_CACHED_INTERVAL,      // ActivityManager.PROCESS_STATE_CACHED_EMPTY
    };

    private static final long[] sSameAwakePssTimes = new long[] {
        PSS_SAME_IMPORTANT_INTERVAL,    // ActivityManager.PROCESS_STATE_PERSISTENT
        PSS_SAME_IMPORTANT_INTERVAL,    // ActivityManager.PROCESS_STATE_PERSISTENT_UI
        PSS_SHORT_INTERVAL,             // ActivityManager.PROCESS_STATE_TOP
        PSS_SAME_IMPORTANT_INTERVAL,    // ActivityManager.PROCESS_STATE_BOUND_FOREGROUND_SERVICE
        PSS_SAME_IMPORTANT_INTERVAL,    // ActivityManager.PROCESS_STATE_FOREGROUND_SERVICE
        PSS_SAME_IMPORTANT_INTERVAL,    // ActivityManager.PROCESS_STATE_TOP_SLEEPING
        PSS_SAME_IMPORTANT_INTERVAL,    // ActivityManager.PROCESS_STATE_IMPORTANT_FOREGROUND
        PSS_SAME_IMPORTANT_INTERVAL,    // ActivityManager.PROCESS_STATE_IMPORTANT_BACKGROUND
        PSS_SAME_IMPORTANT_INTERVAL,    // ActivityManager.PROCESS_STATE_BACKUP
        PSS_SAME_IMPORTANT_INTERVAL,    // ActivityManager.PROCESS_STATE_HEAVY_WEIGHT
        PSS_SAME_SERVICE_INTERVAL,      // ActivityManager.PROCESS_STATE_SERVICE
        PSS_SAME_SERVICE_INTERVAL,      // ActivityManager.PROCESS_STATE_RECEIVER
        PSS_SAME_CACHED_INTERVAL,       // ActivityManager.PROCESS_STATE_HOME
        PSS_SAME_CACHED_INTERVAL,       // ActivityManager.PROCESS_STATE_LAST_ACTIVITY
        PSS_SAME_CACHED_INTERVAL,       // ActivityManager.PROCESS_STATE_CACHED_ACTIVITY
        PSS_SAME_CACHED_INTERVAL,       // ActivityManager.PROCESS_STATE_CACHED_ACTIVITY_CLIENT
        PSS_SAME_CACHED_INTERVAL,       // ActivityManager.PROCESS_STATE_CACHED_EMPTY
    };

    private static final long[] sTestFirstAwakePssTimes = new long[] {
        PSS_TEST_FIRST_TOP_INTERVAL,        // ActivityManager.PROCESS_STATE_PERSISTENT
        PSS_TEST_FIRST_TOP_INTERVAL,        // ActivityManager.PROCESS_STATE_PERSISTENT_UI
        PSS_TEST_FIRST_TOP_INTERVAL,        // ActivityManager.PROCESS_STATE_TOP
        PSS_FIRST_BACKGROUND_INTERVAL,      // ActivityManager.PROCESS_STATE_BOUND_FOREGROUND_SERVICE
        PSS_FIRST_BACKGROUND_INTERVAL,      // ActivityManager.PROCESS_STATE_FOREGROUND_SERVICE
        PSS_FIRST_BACKGROUND_INTERVAL,      // ActivityManager.PROCESS_STATE_TOP_SLEEPING
        PSS_TEST_FIRST_BACKGROUND_INTERVAL, // ActivityManager.PROCESS_STATE_IMPORTANT_FOREGROUND
        PSS_TEST_FIRST_BACKGROUND_INTERVAL, // ActivityManager.PROCESS_STATE_IMPORTANT_BACKGROUND
        PSS_TEST_FIRST_BACKGROUND_INTERVAL, // ActivityManager.PROCESS_STATE_BACKUP
        PSS_TEST_FIRST_BACKGROUND_INTERVAL, // ActivityManager.PROCESS_STATE_HEAVY_WEIGHT
        PSS_TEST_FIRST_BACKGROUND_INTERVAL, // ActivityManager.PROCESS_STATE_SERVICE
        PSS_TEST_FIRST_BACKGROUND_INTERVAL, // ActivityManager.PROCESS_STATE_RECEIVER
        PSS_TEST_FIRST_BACKGROUND_INTERVAL, // ActivityManager.PROCESS_STATE_HOME
        PSS_TEST_FIRST_BACKGROUND_INTERVAL, // ActivityManager.PROCESS_STATE_LAST_ACTIVITY
        PSS_TEST_FIRST_BACKGROUND_INTERVAL, // ActivityManager.PROCESS_STATE_CACHED_ACTIVITY
        PSS_TEST_FIRST_BACKGROUND_INTERVAL, // ActivityManager.PROCESS_STATE_CACHED_ACTIVITY_CLIENT
        PSS_TEST_FIRST_BACKGROUND_INTERVAL, // ActivityManager.PROCESS_STATE_CACHED_EMPTY
    };

    private static final long[] sTestSameAwakePssTimes = new long[] {
        PSS_TEST_SAME_BACKGROUND_INTERVAL,  // ActivityManager.PROCESS_STATE_PERSISTENT
        PSS_TEST_SAME_BACKGROUND_INTERVAL,  // ActivityManager.PROCESS_STATE_PERSISTENT_UI
        PSS_TEST_SAME_IMPORTANT_INTERVAL,   // ActivityManager.PROCESS_STATE_TOP
        PSS_TEST_SAME_IMPORTANT_INTERVAL,   // ActivityManager.PROCESS_STATE_BOUND_FOREGROUND_SERVICE
        PSS_TEST_SAME_IMPORTANT_INTERVAL,   // ActivityManager.PROCESS_STATE_FOREGROUND_SERVICE
        PSS_TEST_SAME_IMPORTANT_INTERVAL,   // ActivityManager.PROCESS_STATE_TOP_SLEEPING
        PSS_TEST_SAME_IMPORTANT_INTERVAL,   // ActivityManager.PROCESS_STATE_IMPORTANT_FOREGROUND
        PSS_TEST_SAME_IMPORTANT_INTERVAL,   // ActivityManager.PROCESS_STATE_IMPORTANT_BACKGROUND
        PSS_TEST_SAME_IMPORTANT_INTERVAL,   // ActivityManager.PROCESS_STATE_BACKUP
        PSS_TEST_SAME_IMPORTANT_INTERVAL,   // ActivityManager.PROCESS_STATE_HEAVY_WEIGHT
        PSS_TEST_SAME_BACKGROUND_INTERVAL,  // ActivityManager.PROCESS_STATE_SERVICE
        PSS_TEST_SAME_BACKGROUND_INTERVAL,  // ActivityManager.PROCESS_STATE_RECEIVER
        PSS_TEST_SAME_BACKGROUND_INTERVAL,  // ActivityManager.PROCESS_STATE_HOME
        PSS_TEST_SAME_BACKGROUND_INTERVAL,  // ActivityManager.PROCESS_STATE_LAST_ACTIVITY
        PSS_TEST_SAME_BACKGROUND_INTERVAL,  // ActivityManager.PROCESS_STATE_CACHED_ACTIVITY
        PSS_TEST_SAME_BACKGROUND_INTERVAL,  // ActivityManager.PROCESS_STATE_CACHED_ACTIVITY_CLIENT
        PSS_TEST_SAME_BACKGROUND_INTERVAL,  // ActivityManager.PROCESS_STATE_CACHED_EMPTY
    };

    public static boolean procStatesDifferForMem(int procState1, int procState2) {
        return sProcStateToProcMem[procState1] != sProcStateToProcMem[procState2];
    }

    public static long minTimeFromStateChange(boolean test) {
        return test ? PSS_TEST_MIN_TIME_FROM_STATE_CHANGE : PSS_MIN_TIME_FROM_STATE_CHANGE;
    }

    public static long computeNextPssTime(int procState, boolean first, boolean test,
            boolean sleeping, long now) {
        final long[] table = test
                ? (first
                        ? sTestFirstAwakePssTimes
                        : sTestSameAwakePssTimes)
                : (first
                        ? sFirstAwakePssTimes
                        : sSameAwakePssTimes);
        return now + table[procState];
    }

    long getMemLevel(int adjustment) {
        for (int i=0; i<mOomAdj.length; i++) {
            if (adjustment <= mOomAdj[i]) {
                return mOomMinFree[i] * 1024;
            }
        }
        return mOomMinFree[mOomAdj.length-1] * 1024;
    }

    /**
     * Return the maximum pss size in kb that we consider a process acceptable to
     * restore from its cached state for running in the background when RAM is low.
     */
    long getCachedRestoreThresholdKb() {
        return mCachedRestoreLevel;
    }

    /**
     * Set the out-of-memory badness adjustment for a process.
     *
     * @param pid The process identifier to set.
     * @param uid The uid of the app
     * @param amt Adjustment value -- lmkd allows -16 to +15.
     *
     * {@hide}
     */
    public static final void setOomAdj(int pid, int uid, int amt) {
        if (amt == UNKNOWN_ADJ)
            return;

        long start = SystemClock.elapsedRealtime();
        ByteBuffer buf = ByteBuffer.allocate(4 * 4);
        buf.putInt(LMK_PROCPRIO);
        buf.putInt(pid);
        buf.putInt(uid);
        buf.putInt(amt);
        writeLmkd(buf);
        long now = SystemClock.elapsedRealtime();
        if ((now-start) > 250) {
            Slog.w("ActivityManager", "SLOW OOM ADJ: " + (now-start) + "ms for pid " + pid
                    + " = " + amt);
        }
    }

    /*
     * {@hide}
     */
    public static final void remove(int pid) {
        ByteBuffer buf = ByteBuffer.allocate(4 * 2);
        buf.putInt(LMK_PROCREMOVE);
        buf.putInt(pid);
        writeLmkd(buf);
    }

    private static boolean openLmkdSocket() {
        try {
            sLmkdSocket = new LocalSocket(LocalSocket.SOCKET_SEQPACKET);
            sLmkdSocket.connect(
                new LocalSocketAddress("lmkd",
                        LocalSocketAddress.Namespace.RESERVED));
            sLmkdOutputStream = sLmkdSocket.getOutputStream();
        } catch (IOException ex) {
            Slog.w(TAG, "lowmemorykiller daemon socket open failed");
            sLmkdSocket = null;
            return false;
        }

        return true;
    }

    private static void writeLmkd(ByteBuffer buf) {

        for (int i = 0; i < 3; i++) {
            if (sLmkdSocket == null) {
                    if (openLmkdSocket() == false) {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException ie) {
                        }
                        continue;
                    }
            }

            try {
                sLmkdOutputStream.write(buf.array(), 0, buf.position());
                return;
            } catch (IOException ex) {
                Slog.w(TAG, "Error writing to lowmemorykiller socket");

                try {
                    sLmkdSocket.close();
                } catch (IOException ex2) {
                }

                sLmkdSocket = null;
            }
        }
    }

    /// M: Mediatek added functions start

    /// M: ALPS01995207, Customized process adj configuration @{
    static {
        // Export ADJ values
        com.mediatek.am.ProcessADJ.INVALID_ADJ = INVALID_ADJ;
        com.mediatek.am.ProcessADJ.UNKNOWN_ADJ = UNKNOWN_ADJ;
        com.mediatek.am.ProcessADJ.CACHED_APP_MAX_ADJ = CACHED_APP_MAX_ADJ;
        com.mediatek.am.ProcessADJ.CACHED_APP_MIN_ADJ = CACHED_APP_MIN_ADJ;
        com.mediatek.am.ProcessADJ.SERVICE_B_ADJ = SERVICE_B_ADJ;
        com.mediatek.am.ProcessADJ.PREVIOUS_APP_ADJ = PREVIOUS_APP_ADJ;
        com.mediatek.am.ProcessADJ.HOME_APP_ADJ = HOME_APP_ADJ;
        com.mediatek.am.ProcessADJ.SERVICE_ADJ = SERVICE_ADJ;
        com.mediatek.am.ProcessADJ.HEAVY_WEIGHT_APP_ADJ = HEAVY_WEIGHT_APP_ADJ;
        com.mediatek.am.ProcessADJ.BACKUP_APP_ADJ = BACKUP_APP_ADJ;
        com.mediatek.am.ProcessADJ.PERCEPTIBLE_APP_ADJ = PERCEPTIBLE_APP_ADJ;
        com.mediatek.am.ProcessADJ.VISIBLE_APP_ADJ = VISIBLE_APP_ADJ;
        com.mediatek.am.ProcessADJ.FOREGROUND_APP_ADJ = FOREGROUND_APP_ADJ;
        com.mediatek.am.ProcessADJ.PERSISTENT_SERVICE_ADJ = PERSISTENT_SERVICE_ADJ;
        com.mediatek.am.ProcessADJ.PERSISTENT_PROC_ADJ = PERSISTENT_PROC_ADJ;
        com.mediatek.am.ProcessADJ.SYSTEM_ADJ = SYSTEM_ADJ;
        com.mediatek.am.ProcessADJ.NATIVE_ADJ = NATIVE_ADJ;
    }

    /// M: ALPS02960761, Use exported ADJ values in AWS @{
    static void exportProcessADJ() {
        // Export in static block
    }
    /// M: ALPS02960761, Use exported ADJ values in AWS @}

    /// M: ALPS01995207, Customized process adj configuration @}

    /// M: Mediatek added functions end
}
