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

package com.android.gallery3d.data;

import android.net.Uri;
import android.os.Handler;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Video;

import com.android.gallery3d.R;
import com.android.gallery3d.app.GalleryApp;
import com.android.gallery3d.data.BucketHelper.BucketEntry;
import com.android.gallery3d.util.Future;
import com.android.gallery3d.util.FutureListener;
import com.android.gallery3d.util.MediaSetUtils;
import com.android.gallery3d.util.ThreadPool;
import com.android.gallery3d.util.ThreadPool.JobContext;
import com.mediatek.gallery3d.util.TraceHelper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;

// LocalAlbumSet lists all image or video albums in the local storage.
// The path should be "/local/image", "local/video" or "/local/all"
public class LocalAlbumSet extends MediaSet
        implements FutureListener<ArrayList<MediaSet>> {
    @SuppressWarnings("unused")
    private static final String TAG = "Gallery2/LocalAlbumSet";

    public static final Path PATH_ALL = Path.fromString("/local/all");
    public static final Path PATH_IMAGE = Path.fromString("/local/image");
    public static final Path PATH_VIDEO = Path.fromString("/local/video");

    private static final Uri[] mWatchUris =
        {Images.Media.EXTERNAL_CONTENT_URI, Video.Media.EXTERNAL_CONTENT_URI};

    private final GalleryApp mApplication;
    private final int mType;
    private ArrayList<MediaSet> mAlbums = new ArrayList<MediaSet>();
    private final ChangeNotifier mNotifier;
    private final String mName;
    private final Handler mHandler;
    private boolean mIsLoading;

    private Future<ArrayList<MediaSet>> mLoadTask;
    private ArrayList<MediaSet> mLoadBuffer;

    public LocalAlbumSet(Path path, GalleryApp application) {
        super(path, nextVersionNumber());
        mApplication = application;
        mHandler = new Handler(application.getMainLooper());
        mType = getTypeFromPath(path);
        mNotifier = new ChangeNotifier(this, mWatchUris, application);
        mName = application.getResources().getString(
                R.string.set_label_local_albums);
    }

    private static int getTypeFromPath(Path path) {
        String name[] = path.split();
        if (name.length < 2) {
            throw new IllegalArgumentException(path.toString());
        }
        return getTypeFromString(name[1]);
    }

    @Override
    public MediaSet getSubMediaSet(int index) {
        return mAlbums.get(index);
    }

    @Override
    public int getSubMediaSetCount() {
        return mAlbums.size();
    }

    @Override
    public String getName() {
        return mName;
    }

    private static int findBucket(BucketEntry entries[], int bucketId) {
        for (int i = 0, n = entries.length; i < n; ++i) {
            if (entries[i].bucketId == bucketId) return i;
        }
        return -1;
    }

    private class AlbumsLoader implements ThreadPool.Job<ArrayList<MediaSet>> {

        @Override
        @SuppressWarnings("unchecked")
        public ArrayList<MediaSet> run(JobContext jc) {
            /// M: [DEBUG.ADD] @{
            TraceHelper.traceBegin(">>>>LocalAlbumSet-AlbumsLoader");
            /// @}
            // Note: it will be faster if we only select media_type and bucket_id.
            //       need to test the performance if that is worth
            BucketEntry[] entries = BucketHelper.loadBucketEntries(
                    jc, mApplication.getContentResolver(), mType);

            /// M: [DEBUG.MODIFY] @{
            /*if (jc.isCancelled()) return null;*/
            if (jc.isCancelled()) {
                TraceHelper.traceEnd();
                return null;
            }
            /// @}

            int offset = 0;
            // Move camera and download bucket to the front, while keeping the
            // order of others.
            int index = findBucket(entries, MediaSetUtils.CAMERA_BUCKET_ID);
            if (index != -1) {
                circularShiftRight(entries, offset++, index);
            }
            index = findBucket(entries, MediaSetUtils.DOWNLOAD_BUCKET_ID);
            if (index != -1) {
                circularShiftRight(entries, offset++, index);
            }
            index = findBucket(entries, MediaSetUtils.SNAPSHOT_BUCKET_ID);
            if (index != -1) {
                circularShiftRight(entries, offset++, index);
            }

            ArrayList<MediaSet> albums = new ArrayList<MediaSet>();
            DataManager dataManager = mApplication.getDataManager();
            for (BucketEntry entry : entries) {
                MediaSet album = getLocalAlbum(dataManager,
                        mType, mPath, entry.bucketId, entry.bucketName, entry.id, entry.mediaType, entry.rotation, entry.filePath, entry.count);
                albums.add(album);
            }
            /// M: [DEBUG.ADD] @{
            TraceHelper.traceEnd();
            /// @}
            return albums;
        }
    }

    private MediaSet getLocalAlbum(
            DataManager manager, int type, Path parent, int id, String name) {
        synchronized (DataManager.LOCK) {
            Path path = parent.getChild(id);
            MediaObject object = manager.peekMediaObject(path);
            if (object != null) {
                if (object instanceof LocalMergeAlbum) {
                    ((LocalMergeAlbum) object).coverItemReset();
                }
                return (MediaSet) object;
            }
            switch (type) {
                case MEDIA_TYPE_IMAGE:
                    return new LocalAlbum(path, mApplication, id, true, name);
                case MEDIA_TYPE_VIDEO:
                    return new LocalAlbum(path, mApplication, id, false, name);
                case MEDIA_TYPE_ALL:
                    Comparator<MediaItem> comp = DataManager.sDateTakenComparator;
                    return new LocalMergeAlbum(path, comp, new MediaSet[] {
                            getLocalAlbum(manager, MEDIA_TYPE_IMAGE, PATH_IMAGE, id, name),
                            getLocalAlbum(manager, MEDIA_TYPE_VIDEO, PATH_VIDEO, id, name)}, id);
            }
            throw new IllegalArgumentException(String.valueOf(type));
        }
    }

    private MediaSet getLocalAlbum(
            DataManager manager, int type, Path parent, int id, String name, int coverId, int coverType, int rotation, String filePath, int count) {
        synchronized (DataManager.LOCK) {
            Path path = parent.getChild(id);
            MediaObject object = manager.peekMediaObject(path);
            if (object != null) {
                if (object instanceof LocalMergeAlbum) {
                    ((LocalMergeAlbum) object).setCoverInfo(coverId, coverType, rotation, filePath, count);
                }
                return (MediaSet) object;
            }
            switch (type) {
                case MEDIA_TYPE_IMAGE:
                    return new LocalAlbum(path, mApplication, id, true, name);
                case MEDIA_TYPE_VIDEO:
                    return new LocalAlbum(path, mApplication, id, false, name);
                case MEDIA_TYPE_ALL:
                    Comparator<MediaItem> comp = DataManager.sDateTakenComparator;
                    return new LocalMergeAlbum(path, comp, new MediaSet[] {
                            getLocalAlbum(manager, MEDIA_TYPE_IMAGE, PATH_IMAGE, id, name),
                            getLocalAlbum(manager, MEDIA_TYPE_VIDEO, PATH_VIDEO, id, name)}, id, coverId, coverType, rotation, filePath, count);
            }
            throw new IllegalArgumentException(String.valueOf(type));
        }
    }

    @Override
    public synchronized boolean isLoading() {
        return mIsLoading;
    }

    @Override
    // synchronized on this function for
    //   1. Prevent calling reload() concurrently.
    //   2. Prevent calling onFutureDone() and reload() concurrently
    public synchronized long reload() {
        if (mNotifier.isDirty()) {
            if (mLoadTask != null) mLoadTask.cancel();
            mIsLoading = true;
            mLoadTask = mApplication.getThreadPool().submit(new AlbumsLoader(), this);
        }
        if (mLoadBuffer != null) {
            mAlbums = mLoadBuffer;
            mLoadBuffer = null;
            for (MediaSet album : mAlbums) {
                album.reload();
            }
            mDataVersion = nextVersionNumber();
        }
        return mDataVersion;
    }

    @Override
    public synchronized void onFutureDone(Future<ArrayList<MediaSet>> future) {
        if (mLoadTask != future) return; // ignore, wait for the latest task
        mLoadBuffer = future.get();
        /// M: [BUG.MARK] @{
        /* mIsLoading = false; */
        /// @}
        if (mLoadBuffer == null) mLoadBuffer = new ArrayList<MediaSet>();
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                notifyContentChanged();
                /// M: [BUG.ADD] @{
                // To avoid timing issue of isLoading and notifyContentChanged,
                // set mIsLoading as false here.
                mIsLoading = false;
                /// @}
            }
        });
    }

    // For debug only. Fake there is a ContentObserver.onChange() event.
    void fakeChange() {
        mNotifier.fakeChange();
    }

    // Circular shift the array range from a[i] to a[j] (inclusive). That is,
    // a[i] -> a[i+1] -> a[i+2] -> ... -> a[j], and a[j] -> a[i]
    private static <T> void circularShiftRight(T[] array, int i, int j) {
        T temp = array[j];
        for (int k = j; k > i; k--) {
            array[k] = array[k - 1];
        }
        array[i] = temp;
    }

    // ********************************************************************
    // *                             MTK                                  *
    // ********************************************************************

    // synchronous reload to avoid loading item before it's got from db
    @Override
    public synchronized long reloadForSlideShow() {
        if (mNotifier.isDirty()) {
            if (mLoadTask != null) {
                mLoadTask.cancel();
            }
            mIsLoading = true;
            mLoadTask = mApplication.getThreadPool().submit(new AlbumsLoader(), null);
            mLoadBuffer = mLoadTask.get();
            mIsLoading = false;
        }
        if (mLoadBuffer != null) {
            mAlbums = mLoadBuffer;
            mLoadBuffer = null;
            for (MediaSet album : mAlbums) {
                album.reloadForSlideShow();
            }
            mDataVersion = nextVersionNumber();
        }
        return mDataVersion;
    }

    /**
     * Special reload flow for cluster. Wait to the AlbumsLoader task finished,
     * then trigger the reload flow for per album.
     *
     * @return the new version number
     */
    public synchronized long synchronizedAlbumData() {
        if (mNotifier.isDirty()) {
            if (mLoadTask != null) {
                mLoadTask.cancel();
            }
            mIsLoading = true;
            mLoadTask = mApplication.getThreadPool().submit(new AlbumsLoader(), null);
            mLoadBuffer = mLoadTask.get();
            mIsLoading = false;
        }
        if (mLoadBuffer != null) {
            mAlbums = mLoadBuffer;
            mLoadBuffer = null;
            for (MediaSet album : mAlbums) {
                album.reload();
            }
            mDataVersion = nextVersionNumber();
        }
        return mDataVersion;
    }
}
