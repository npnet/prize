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

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.os.Looper;

import com.android.gallery3d.data.DataManager;
import com.android.gallery3d.data.DownloadCache;
import com.android.gallery3d.data.ImageCacheService;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.util.ThreadPool;

import java.util.ArrayList;

public interface GalleryApp {
    public DataManager getDataManager();

    public ImageCacheService getImageCacheService();
    public DownloadCache getDownloadCache();
    public ThreadPool getThreadPool();

    public Context getAndroidContext();
    public Looper getMainLooper();
    public ContentResolver getContentResolver();
    public Resources getResources();

    public ArrayList<MediaItem> getPreTimeItem();

    public void updatePreTimeItem(ArrayList<MediaItem> items);
}
