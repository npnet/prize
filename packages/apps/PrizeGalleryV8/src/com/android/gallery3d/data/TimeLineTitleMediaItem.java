/*
 * Copyright (c) 2015, The Linux Foundation. All rights reserved.
 * Not a Contribution
 *
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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapRegionDecoder;

import com.android.gallery3d.common.Utils;
import com.android.gallery3d.ui.SelectionManager;
import com.android.gallery3d.ui.TimeLineTitleMaker;
import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.util.ThreadPool.Job;

public class TimeLineTitleMediaItem extends MediaItem {
    private static final String TAG = "TimeLineTitleMediaItem";
    private String mTitle;
    private int mDatetaken;
    private int mCount;

    public TimeLineTitleMediaItem(Path path) {
        super(path);
        Log.e(TAG, "TimeLineTitleMediaItem path=" + path.toString());
    }


    public Job<Bitmap> requestTitle(int type, TimeLineTitleMaker maker, SelectionManager selectionManager) {
        Log.e(TAG, "requestTitle mCount=" + mCount + " path=" + getPath());
        if (maker != null) {
            return maker.requestTimeLineTitle(getTitle(maker.getContext()), mCount, selectionManager, getPath());
        }
        return null;
    }

    private String getTitle(Context context) {
        return TimeClustering.getTimeTitleName(context.getApplicationContext(), mDatetaken);
    }

    public void setTitle(String title) {
        mTitle = title;
    }

    public void setDatetaken(int datetaken) {
        mDatetaken = datetaken;
    }

    public Job<Bitmap> requestImage(int type) {
        return null;
    }

    public Job<BitmapRegionDecoder> requestLargeImage() {
        Log.e(TAG, "Operation not supported");
        return null;
    }

    public String getMimeType() {
        return MediaObject.MEDIA_TYPE_IMAGE_STRING;
    }

    @Override
    public int getMediaType() {
        return MediaObject.MEDIA_TYPE_TIMELINE_TITLE;
    }

    public void setCount(int images) {
        Log.i(TAG, "setCount images=" + images + " path=" + getPath() + "  this=" + this);
        mCount = images;
    }

    public int getWidth() {
        return 0;
    }

    public int getHeight() {
        return 0;
    }

}
