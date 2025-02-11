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

package com.android.gallery3d.app;

import android.os.Handler;
import android.os.Message;
import android.os.Process;

import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.ContentListener;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.data.MediaObject;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.data.ClusterAlbumSet;
import com.android.gallery3d.ui.SynchronizedHandler;
import com.android.gallery3d.util.LogUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

public class TimeLineDataLoader {
    @SuppressWarnings("unused")
    private static final String TAG = "TimeLineDataLoader";
    private static final int DATA_CACHE_SIZE = 1000;

    private static final int MSG_LOAD_START = 1;
    private static final int MSG_LOAD_FINISH = 2;
    private static final int MSG_RUN_OBJECT = 3;

    private static final int MIN_LOAD_COUNT = 32;
    private static final int MAX_LOAD_COUNT = 96/*96 * 2*/;

    private final MediaItem[] mData;
    private final long[] mItemVersion;
    private final long[] mSetVersion;

    public static interface DataListener {
        public void onContentChanged(int index);
        public void onSizeChanged();
        public void onUpdateContent();
    }

    private int mActiveStart = 0;
    private int mActiveEnd = 0;

    private int mContentStart = 0;
    private int mContentEnd = 0;

    private final MediaSet mSource;
    private long mSourceVersion = MediaObject.INVALID_DATA_VERSION;

    private final Handler mMainHandler;
    private int mSize = 0;

    private ArrayList<DataListener> mDataListener = new ArrayList<>();
    private MySourceListener mSourceListener = new MySourceListener();
    private LoadingListener mLoadingListener;

    private ReloadTask mReloadTask;
    // the data version on which last loading failed
    private long mFailedVersion = MediaObject.INVALID_DATA_VERSION;
    protected GalleryApp mGalleryApp;

    public TimeLineDataLoader(AbstractGalleryActivity context, MediaSet mediaSet) {
        mGalleryApp = (GalleryApp) context.getApplicationContext();
        mSource = mediaSet;
        mData = new MediaItem[DATA_CACHE_SIZE];
        mItemVersion = new long[DATA_CACHE_SIZE];
        mSetVersion = new long[DATA_CACHE_SIZE];
        Arrays.fill(mItemVersion, MediaObject.INVALID_DATA_VERSION);
        Arrays.fill(mSetVersion, MediaObject.INVALID_DATA_VERSION);

        mMainHandler = new SynchronizedHandler(context.getGLRoot()) {
            @Override
            public void handleMessage(Message message) {
                switch (message.what) {
                    case MSG_RUN_OBJECT:
                        ((Runnable) message.obj).run();
                        return;
                    case MSG_LOAD_START:
                        if (mLoadingListener != null) mLoadingListener.onLoadingStarted();
                        return;
                    case MSG_LOAD_FINISH:
                        if (mLoadingListener != null) {
                            boolean loadingFailed =
                                    (mFailedVersion != MediaObject.INVALID_DATA_VERSION);
                            mLoadingListener.onLoadingFinished(loadingFailed);
                        }
                        return;
                }
            }
        };
        preLoadItems();
    }

    private void preLoadItems() {
        final ArrayList<MediaItem> items = mGalleryApp.getPreTimeItem();
        for (int i = 0, count = items.size(); i < count; i++) {
            MediaItem item = items.get(i);
            int index = i % DATA_CACHE_SIZE;
            mData[index] = item;
            mItemVersion[index] = item.getDataVersion();
        }
    }

    public void resume() {
        mSource.addContentListener(mSourceListener);
        mReloadTask = new ReloadTask();
        mReloadTask.start();
    }

    public void pause() {
        if (mReloadTask != null) {
            mReloadTask.terminate();
            mReloadTask = null;
            mSource.removeContentListener(mSourceListener);
        }
    }

    public MediaSet getMediaSet(int index) {
        return ((ClusterAlbumSet) mSource).getAlbumFromindex(index);
    }

    public MediaItem get(int index) {
        if (!isActive(index)) {
            ArrayList<MediaItem> items = mSource.getMediaItem(index, 1);
            if (items != null && items.size() > 0) {
                return items.get(0);
            }
        }
        return mData[index % mData.length];
    }

    public ArrayList<MediaItem> get(int start, int count) {
        ArrayList<MediaItem> list = mSource.getMediaItem(start, count);
        return  list;
    }

    public int getTimeLineTitlesCount() {
        return mSource.getSubMediaSetCount();
    }

    public boolean isActive(int index) {
        return index >= mActiveStart && index < mActiveEnd;
    }

    public int size() {
        return mSize;
    }

    public int[] getSubMediaSetCount() {
        ClusterAlbumSet set = (ClusterAlbumSet) mSource;
        int albumCount = set.getSubMediaSetCount();
        int[] counts = new int[albumCount];
        for (int i = albumCount - 1; i >= 0; --i) {
            counts[i] = set.getSubMediaSet(i).getSelectableItemCount();
        }
        return counts;
    }

    // Returns the index of the MediaItem with the given path or
    // -1 if the path is not cached
    public int findItem(Path id) {
        return getIndex(id, true);
    }

    /**
     * @param id            given path
     * @param needTitleItem timeline title items will be filtered out if true.
     * @return the index of the MediaItem with the given path or -1 if the path is not cached.
     */
    public int getIndex(Path id, final boolean needTitleItem) {
        for (int i = mContentStart, offset = 0; i < mContentEnd; i++) {
            MediaItem item = mData[i % DATA_CACHE_SIZE];
            if (item != null) {
                if (!needTitleItem && !item.isSelectable()) {
                    offset++;
                } else if (id == item.getPath()) {
                    return i - offset;
                }
            }
        }
        return -1;
    }

    private void clearSlot(int slotIndex) {
        mData[slotIndex] = null;
        mItemVersion[slotIndex] = MediaObject.INVALID_DATA_VERSION;
        mSetVersion[slotIndex] = MediaObject.INVALID_DATA_VERSION;
    }

    private void setContentWindow(int contentStart, int contentEnd) {
        if (contentStart == mContentStart && contentEnd == mContentEnd) return;
        int end = mContentEnd;
        int start = mContentStart;

        // We need change the content window before calling reloadData(...)
        synchronized (this) {
            mContentStart = contentStart;
            mContentEnd = contentEnd;
        }
        if (contentStart >= end || start >= contentEnd) {
            for (int i = start, n = end; i < n; ++i) {
                clearSlot(i % DATA_CACHE_SIZE);
            }
        } else {
            for (int i = start; i < contentStart; ++i) {
                clearSlot(i % DATA_CACHE_SIZE);
            }
            for (int i = contentEnd, n = end; i < n; ++i) {
                clearSlot(i % DATA_CACHE_SIZE);
            }
        }
        if (mReloadTask != null) mReloadTask.notifyDirty();
    }

    public void setActiveWindow(int start, int end) {
        if (start == mActiveStart && end == mActiveEnd) return;

        Utils.assertTrue(start <= end
                && end - start <= mData.length && end <= mSize);

        int length = mData.length;
        mActiveStart = start;
        mActiveEnd = end;

        // If no data is visible, keep the cache content
        if (start == end) return;

        int contentStart = Utils.clamp((start + end) / 2 - length / 2,
                0, Math.max(0, mSize - length));
        int contentEnd = Math.min(contentStart + length, mSize);
        if (mContentStart > start || mContentEnd < end
                || Math.abs(contentStart - mContentStart) > MIN_LOAD_COUNT) {
            setContentWindow(contentStart, contentEnd);
        }
    }

    private class MySourceListener implements ContentListener {
        @Override
        public void onContentDirty() {
            if (mReloadTask != null) mReloadTask.notifyDirty();
        }
    }

    public void setDataListener(DataListener listener) {
        mDataListener.add(listener);
    }

    public void removeDataListener(DataListener listener) {
        mDataListener.remove(listener);
    }

    public void setLoadingListener(LoadingListener listener) {
        mLoadingListener = listener;
    }

    private <T> T executeAndWait(Callable<T> callable) {
        FutureTask<T> task = new FutureTask<T>(callable);
        mMainHandler.sendMessage(
                mMainHandler.obtainMessage(MSG_RUN_OBJECT, task));
        try {
            return task.get();
        } catch (InterruptedException e) {
            return null;
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private static class UpdateInfo {
        public long version;
        public int reloadStart;
        public int reloadCount;

        public int size;
        public ArrayList<MediaItem> items;
    }

    private class GetUpdateInfo implements Callable<UpdateInfo> {
        private final long mVersion;

        public GetUpdateInfo(long version) {
            mVersion = version;
        }

        @Override
        public UpdateInfo call() throws Exception {
            if (mFailedVersion == mVersion) {
                // previous loading failed, return null to pause loading
                return null;
            }
            UpdateInfo info = new UpdateInfo();
            long version = mVersion;
            info.version = mSourceVersion;
            info.size = mSize;
            long setVersion[] = mSetVersion;
            for (int i = mActiveStart, n = mContentEnd; i < n; ++i) {
                int index = i % DATA_CACHE_SIZE;
                if (setVersion[index] != version) {
                    info.reloadStart = i;
                    info.reloadCount = Math.min(MAX_LOAD_COUNT, n - i);
                    return info;
                }
            }

            for (int i = mContentStart, n = mActiveStart; i < n; ++i) {
                int index = i % DATA_CACHE_SIZE;
                if (setVersion[index] != version) {
                    info.reloadStart = i;
                    info.reloadCount = Math.min(MAX_LOAD_COUNT, n - i);
                    return info;
                }
            }
            return mSourceVersion == mVersion ? null : info;
        }
    }

    private class UpdateContent implements Callable<Void> {

        private UpdateInfo mUpdateInfo;

        public UpdateContent(UpdateInfo info) {
            mUpdateInfo = info;
        }

        @Override
        public Void call() throws Exception {
            UpdateInfo info = mUpdateInfo;
            mSourceVersion = info.version;
            if (mSize != info.size) {
                mSize = info.size;
                if (mDataListener != null)
                    for (DataListener l : mDataListener) {
                        l.onSizeChanged();
                    }
                if (mContentEnd > mSize) mContentEnd = mSize;
                if (mActiveEnd > mSize) mActiveEnd = mSize;
            }

            ArrayList<MediaItem> items = info.items;

            mFailedVersion = MediaObject.INVALID_DATA_VERSION;
            if ((items == null) || items.isEmpty()) {
                if (info.reloadCount > 0) {
                    mFailedVersion = info.version;
                    LogUtil.d(TAG, "loading failed: " + mFailedVersion);
                }
                return null;
            }
            int start = Math.max(info.reloadStart, mContentStart);
            int end = Math.min(info.reloadStart + items.size(), mContentEnd);
            boolean isUpdate = false;
            for (int i = start; i < end; ++i) {
                int index = i % DATA_CACHE_SIZE;
                mSetVersion[index] = info.version;
                MediaItem updateItem = items.get(i - info.reloadStart);
                if (updateItem != null) {
                    long itemVersion = updateItem.getDataVersion();
                    if (mItemVersion[index] != itemVersion) {
                        mItemVersion[index] = itemVersion;
                        mData[index] = updateItem;
                        if (mDataListener != null && i >= mActiveStart && i < mActiveEnd) {
                            for (DataListener l : mDataListener) {
                                l.onContentChanged(i);
                                isUpdate = true;
                            }
                        }
                    }
               }
            }
            if (isUpdate && mDataListener != null) {
                for (DataListener l : mDataListener) {
                    l.onUpdateContent();
                }
                if (start == 0) {
                    mGalleryApp.updatePreTimeItem(items);
                }
            }
            return null;
        }
    }

    /*
     * The thread model of ReloadTask
     *      *
     * [Reload Task]       [Main Thread]
     *       |                   |
     * getUpdateInfo() -->       |           (synchronous call)
     *     (wait) <----    getUpdateInfo()
     *       |                   |
     *   Load Data               |
     *       |                   |
     * updateContent() -->       |           (synchronous call)
     *     (wait)          updateContent()
     *       |                   |
     *       |                   |
     */
    private class ReloadTask extends Thread {

        private volatile boolean mActive = true;
        private volatile boolean mDirty = true;
        private boolean mIsLoading = false;

        private void updateLoading(boolean loading) {
            if (mIsLoading == loading) return;
            mIsLoading = loading;
            mMainHandler.sendEmptyMessage(loading ? MSG_LOAD_START : MSG_LOAD_FINISH);
        }

        @Override
        public void run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            boolean updateComplete = false;
            long version = MediaObject.INVALID_DATA_VERSION;
            while (mActive) {
                synchronized (this) {
                    if (mActive && !mDirty && updateComplete
                            && version != MediaObject.INVALID_DATA_VERSION) {
                        updateLoading(false);
                        Utils.waitWithoutInterrupt(this);
                        continue;
                    }
                    mDirty = false;
                }
                updateLoading(true);
                version = mSource.reload();
                UpdateInfo info = executeAndWait(new GetUpdateInfo(version));
                updateComplete = info == null;
                LogUtil.i(TAG, "Source=" + mSource + " path=" + mSource.getPath() + " updateComplete=" + updateComplete);
                if (updateComplete) {
                    continue;
                }
                if (info.version != version) {
                    info.size = mSource.getMediaItemCount();
                    LogUtil.i(TAG, "info.size=" + info.size);
                    info.version = version;
                }
                if (info.reloadCount > 0) {
                    int start = Math.max(info.reloadStart, mContentStart);
                    int end = Math.min(info.reloadStart + info.reloadCount, mContentEnd);
                    info.items = mSource.getMediaItem(start, end - start);
                    LogUtil.d(TAG, "ReloadTask run info.reloadStart=" + info.reloadStart + " info.reloadCount=" + info.reloadCount);
                }
                executeAndWait(new UpdateContent(info));
            }
            updateLoading(false);
        }

        public synchronized void notifyDirty() {
            mDirty = true;
            notifyAll();
        }

        public synchronized void terminate() {
            mActive = false;
            notifyAll();
        }
    }
}
