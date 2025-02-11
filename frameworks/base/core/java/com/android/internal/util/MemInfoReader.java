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

package com.android.internal.util;

import android.os.Debug;
import android.os.StrictMode;

public final class MemInfoReader {
    final long[] mInfos = new long[Debug.MEMINFO_COUNT];

    public void readMemInfo() {
        // Permit disk reads here, as /proc/meminfo isn't really "on
        // disk" and should be fast.  TODO: make BlockGuard ignore
        // /proc/ and /sys/ files perhaps?
        StrictMode.ThreadPolicy savedPolicy = StrictMode.allowThreadDiskReads();
        try {
            Debug.getMemInfo(mInfos);
        } finally {
            StrictMode.setThreadPolicy(savedPolicy);
        }
    }

    public void readExtraMemInfo() {
        // Permit disk reads here, as /proc/meminfo isn't really "on
        // disk" and should be fast.  TODO: make BlockGuard ignore
        // /proc/ and /sys/ files perhaps?
        StrictMode.ThreadPolicy savedPolicy = StrictMode.allowThreadDiskReads();
        try {
            Debug.getExtraMemInfo(mInfos);
        } finally {
            StrictMode.setThreadPolicy(savedPolicy);
        }
    }

    /**
     * Total amount of RAM available to the kernel.
     */
    public long getTotalSize() {
        return mInfos[Debug.MEMINFO_TOTAL] * 1024;
    }

    /**
     * Amount of RAM that is not being used for anything.
     */
    public long getFreeSize() {
        return mInfos[Debug.MEMINFO_FREE] * 1024;
    }

    /// M: @{ Amount of RAM that cahces that are mapped in to processes.
    /**
     * Amount of RAM used to map devices, files, or libraries using the mmap command.
     * @internal
     */
    public long getMappedSize() {
        return mInfos[Debug.MEMINFO_MAPPED] * 1024;
    }
    /**
     * Amount of RAM used for file buffers.
     * @internal
     */
    public long getBuffersSize() {
        return mInfos[Debug.MEMINFO_BUFFERS] * 1024;
    }
    /// @}

    /**
     * Amount of RAM that the kernel is being used for caches, not counting caches
     * that are mapped in to processes.
     */
    public long getCachedSize() {
        return getCachedSizeKb() * 1024;
    }

    /**
     * Amount of RAM that is in use by the kernel for actual allocations.
     */
    public long getKernelUsedSize() {
        return getKernelUsedSizeKb() * 1024;
    }

    /**
     * Total amount of RAM available to the kernel.
     */
    public long getTotalSizeKb() {
        return mInfos[Debug.MEMINFO_TOTAL];
    }

    /**
     * Amount of RAM that is not being used for anything.
     */
    public long getFreeSizeKb() {
        return mInfos[Debug.MEMINFO_FREE];
    }

    /**
     * Amount of RAM that the kernel is being used for caches, not counting caches
     * that are mapped in to processes.
     */
    public long getCachedSizeKb() {
        return mInfos[Debug.MEMINFO_BUFFERS]
                + mInfos[Debug.MEMINFO_CACHED] - mInfos[Debug.MEMINFO_MAPPED];
    }

    /**
     * Amount of RAM that is in use by the kernel for actual allocations.
     */
    public long getKernelUsedSizeKb() {
        return mInfos[Debug.MEMINFO_SHMEM] + mInfos[Debug.MEMINFO_SLAB]
                + mInfos[Debug.MEMINFO_VM_ALLOC_USED] + mInfos[Debug.MEMINFO_PAGE_TABLES]
                + mInfos[Debug.MEMINFO_KERNEL_STACK];
    }

    public long getSwapTotalSizeKb() {
        return mInfos[Debug.MEMINFO_SWAP_TOTAL];
    }

    public long getSwapFreeSizeKb() {
        return mInfos[Debug.MEMINFO_SWAP_FREE];
    }

    public long getZramTotalSizeKb() {
        return mInfos[Debug.MEMINFO_ZRAM_TOTAL];
    }

    public long[] getRawInfo() {
        return mInfos;
    }
}
