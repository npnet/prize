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

package com.android.gallery3d.app;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;

import com.android.gallery3d.data.ContentListener;
import com.android.gallery3d.data.DataManager;
import com.android.gallery3d.data.DownloadCache;
import com.android.gallery3d.data.ImageCacheService;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.gadget.WidgetUtils;
import com.android.gallery3d.picasasource.PicasaSource;
import com.android.gallery3d.util.CacheManager;
import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.util.LightCycleHelper;
import com.android.gallery3d.util.MediaSetUtils;
import com.android.gallery3d.util.ThreadPool;
import com.android.gallery3d.util.UsageStatistics;

import com.bumptech.glide.Glide;
import com.mediatek.gallery3d.adapter.FeatureHelper;
import com.mediatek.gallery3d.adapter.PhotoPlayFacade;
import com.mediatek.galleryframework.util.GalleryPluginUtils;
import com.prize.sticker.db.AssetsDatabaseManager;
import com.prize.sticker.db.DatabaseHelper;
import com.prize.util.OptionConfig;

import java.io.File;
import java.util.ArrayList;

public class GalleryAppImpl extends Application implements GalleryApp {

    private static final String TAG = "Gallery2/GalleryAppImpl";

    private static final String DOWNLOAD_FOLDER = "download";
    private static final long DOWNLOAD_CAPACITY = 64 * 1024 * 1024; // 64M

    private ImageCacheService mImageCacheService;

    private Object mLock = new Object();
    private DataManager mDataManager;
    private ThreadPool mThreadPool;
    private DownloadCache mDownloadCache;

    @Override
    public void onCreate() {
        super.onCreate();
        initializeAsyncTask();
        GalleryUtils.initialize(this);
        WidgetUtils.initialize(this);
        PicasaSource.initialize(this);
        UsageStatistics.initialize(this);
        /// M: [FEATURE.ADD] @{
        PhotoPlayFacade.initialize(this, MediaItem.getTargetSize(MediaItem.TYPE_MICROTHUMBNAIL),
                MediaItem.getTargetSize(MediaItem.TYPE_THUMBNAIL),
                MediaItem.getTargetSize(MediaItem.TYPE_HIGHQUALITYTHUMBNAIL));
        /// @}
        /// M: [FEATURE.ADD] plugin @{
        GalleryPluginUtils.initialize(this);
        /// @}
        /// M: [BUG.ADD] for closing cache when SD card got unmounted@{
        registerStorageReceiver();
        /// @}

        preLoadImage();
		
		/** PRIZE-watermark-wanzhijuan-2016-1-21-start **/
        OptionConfig.configV6Point3(this);
        if (OptionConfig.SHOW_WATERMARK) {
        	AssetsDatabaseManager.initManager(this);
            AssetsDatabaseManager.getManager().initDatabase(DatabaseHelper.DATABASE_NAME);
        }
		/** PRIZE-watermark-wanzhijuan-2016-1-21-end **/
    }

    private ArrayList<MediaItem> mMediaItemList = new ArrayList<>(64);
    private MediaSet mSource;
    private AsyncTask mAsyncTask;
    public void preLoadImage() {
        if (mAsyncTask != null) mAsyncTask.cancel(true);
        mAsyncTask = new AsyncTask<Void, Void, ArrayList<MediaItem>> () {

            @Override
            protected ArrayList<MediaItem> doInBackground(Void... params) {
                String mLocalAllPath =  getDataManager().getTopSetPath(
                        DataManager.INCLUDE_LOCAL_ALL_ONLY)+"/"+ MediaSetUtils.CAMERA_BUCKET_ID;
                String newTimePath = FilterUtils.switchClusterPath(mLocalAllPath, FilterUtils.CLUSTER_BY_TIME);
                mSource = getDataManager().getMediaSet(newTimePath);
				ArrayList<MediaItem> items = null;
				if (mSource != null) {
                	mSource.reload();
                	items = mSource.getMediaItem(0, Math.min(mSource.getMediaItemCount(), 64));
				}
                return items;
            }

            @Override
            protected void onPostExecute(ArrayList<MediaItem> items) {
                updatePreTimeItem(items);
                int loadCount = 0;
                for (int i = 0, count = (items == null ? 0 : items.size()); i < count && loadCount < 28; i++) {
                    MediaItem item = items.get(i);
                    if (item.isSelectable()) {
                        Glide.with(GalleryAppImpl.this).load(item.getFilePath()).asBitmap().centerCrop().override(GalleryUtils.sBitmapWidth, GalleryUtils.sBitmapWidth).preload(GalleryUtils.sBitmapWidth, GalleryUtils.sBitmapWidth);
                    }
                }
            }

        }.execute();
    }

    private MySourceListener mSourceListener = new MySourceListener();
    private class MySourceListener implements ContentListener {
        @Override
        public void onContentDirty() {
            preLoadImage();
        }
    }

    public void isActive(boolean isActive) {
        if (mSource != null) {
            if (isActive) {
                mSource.removeContentListener(mSourceListener);
            } else {
                mSource.addContentListener(mSourceListener);
            }
        }
    }

    @Override
    public Context getAndroidContext() {
        return this;
    }

    @Override
    public ArrayList<MediaItem> getPreTimeItem() {
        return mMediaItemList;
    }

    @Override
    public void updatePreTimeItem(ArrayList<MediaItem> items) {
		if (items == null || items.size() == 0) {
            return;
        }
        mMediaItemList.clear();
        mMediaItemList.addAll(items);
    }

    @Override
    public synchronized DataManager getDataManager() {
        if (mDataManager == null) {
            mDataManager = new DataManager(this);
            mDataManager.initializeSourceMap();
        }
        return mDataManager;
    }


    @Override
    public ImageCacheService getImageCacheService() {
        // This method may block on file I/O so a dedicated lock is needed here.
        synchronized (mLock) {
            if (mImageCacheService == null) {
                mImageCacheService = new ImageCacheService(getAndroidContext());
            }
            return mImageCacheService;
        }
    }

    @Override
    public synchronized ThreadPool getThreadPool() {
        if (mThreadPool == null) {
            mThreadPool = new ThreadPool();
        }
        return mThreadPool;
    }

    @Override
    public synchronized DownloadCache getDownloadCache() {
        if (mDownloadCache == null) {
            /// M: [BEHAVIOR.MODIFY] @{
            /* File cacheDir = new File(getExternalCacheDir(), DOWNLOAD_FOLDER);*/
            File dir = FeatureHelper.getExternalCacheDir(this);
            if (dir == null) {
                Log.i(TAG, "<getDownloadCache> failed to get cache dir");
                return null;
            }
            File cacheDir = new File(dir, DOWNLOAD_FOLDER);
            /// @}

            if (!cacheDir.isDirectory()) cacheDir.mkdirs();

            if (!cacheDir.isDirectory()) {
                throw new RuntimeException(
                        "fail to create: " + cacheDir.getAbsolutePath());
            }
            mDownloadCache = new DownloadCache(this, cacheDir, DOWNLOAD_CAPACITY);
        }
        return mDownloadCache;
    }

    private void initializeAsyncTask() {
        // AsyncTask class needs to be loaded in UI thread.
        // So we load it here to comply the rule.
        try {
            Class.forName(AsyncTask.class.getName());
        } catch (ClassNotFoundException e) {
        }
    }

    /// M: [BUG.ADD] for closing cache when SD card got unmounted.@{
    private BroadcastReceiver mStorageReceiver;

    private void registerStorageReceiver() {
        Log.d(TAG, ">> registerStorageReceiver");
        // register BroadcastReceiver for SD card mount/unmount broadcast
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_MEDIA_EJECT);
        filter.addAction(Intent.ACTION_MEDIA_MOUNTED);
        filter.addDataScheme("file");
        mStorageReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                handleStorageIntentAsync(intent);
            }
        };
        registerReceiver(mStorageReceiver, filter);
        Log.d(TAG, "<< registerStorageReceiver: receiver registered");
    }

    private void handleStorageIntentAsync(final Intent intent) {
        new Thread() {
            public void run() {
                String action = intent.getAction();
                /// M: [BUG.MODIFY] fix je issue @{
                /*
                 * String storagePath = intent.getData().getPath();
                 * */
                String storagePath = "";
                if (null != intent.getData()) {
                    storagePath = intent.getData().getPath();
                }
                /// @}
                String defaultPath = FeatureHelper.getDefaultPath();
                Log.d(TAG, "storage receiver: action=" + action);
                Log.d(TAG, "intent path=" + storagePath + ", default path="
                        + defaultPath);

                if (storagePath == null
                        || !storagePath.equalsIgnoreCase(defaultPath)) {
                    Log.w(TAG, "ejecting storage is not cache storage!!");
                    return;
                }
                if (Intent.ACTION_MEDIA_EJECT.equals(action)) {
                    // close and disable cache
                    Log.i(TAG, "-> closing CacheManager");
                    CacheManager.storageStateChanged(false);
                    Log.i(TAG, "<- closing CacheManager");
                    // clear refs in ImageCacheService
                    if (mImageCacheService != null) {
                        Log.i(TAG, "-> closing cache service");
                        mImageCacheService.closeCache();
                        Log.i(TAG, "<- closing cache service");
                    }
                } else if (Intent.ACTION_MEDIA_MOUNTED.equals(action)) {
                    // enable cache but not open it explicitly
                    Log.i(TAG, "-> opening CacheManager");
                    CacheManager.storageStateChanged(true);
                    Log.i(TAG, "<- opening CacheManager");
                    // re-open cache in ImageCacheService
                    if (mImageCacheService != null) {
                        Log.i(TAG, "-> opening cache service");
                        mImageCacheService.openCache();
                        Log.i(TAG, "<- opening cache service");
                    }
                } else {
                    Log.w(TAG, "undesired action '" + action
                            + "' for storage receiver!");
                }
            }
        } .start();
    }
    /// @}
}
