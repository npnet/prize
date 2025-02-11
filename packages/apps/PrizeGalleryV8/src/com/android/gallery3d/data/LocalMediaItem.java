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

import android.database.Cursor;
import android.text.TextUtils;

import com.android.gallery3d.util.GalleryUtils;

import java.io.File;
import java.text.DateFormat;
import java.util.Date;

//
// LocalMediaItem is an abstract class captures those common fields
// in LocalImage and LocalVideo.
//
public abstract class LocalMediaItem extends MediaItem {

    @SuppressWarnings("unused")
    private static final String TAG = "Gallery2/LocalMediaItem";

    // database fields
    public int id;
    public String caption;
    public String mimeType;
    public long fileSize;
    public double latitude = INVALID_LATLNG;
    public double longitude = INVALID_LATLNG;
    public long dateTakenInMs;
    public long dateAddedInSec;
    public long dateModifiedInSec;
    public String filePath;
    public int bucketId;
    public int width;
    public int height;
    
    /** PRIZE-Baidu location-2015-6-12-start*/
    public String dbAddr;
    /** PRIZE-Baidu location-2015-6-12-end*/

    public LocalMediaItem(Path path, long version) {
        super(path, version);
    }

    public LocalMediaItem(Path path) {
        super(path);
    }

    @Override
    public long getDateInMs() {
        return dateTakenInMs;
    }
    
    private String getFileName() {
    	if (!TextUtils.isEmpty(filePath)) {
    		int separator = filePath.lastIndexOf(File.separator);
    		String fileName = filePath;
    		if (separator > -1 && separator < filePath.length()) {
    			fileName = filePath.substring(filePath.lastIndexOf(File.separator) + 1,
    					filePath.length());
    		}
    		int dot = fileName.lastIndexOf('.');    
            if ((dot >-1) && (dot < (fileName.length()))) {    
                return fileName.substring(0, dot);    
            }    
            return fileName;
    	}
    	return caption;
    }

    @Override
    public String getName() {
        return getFileName();
    }

    @Override
    public void getLatLong(double[] latLong) {
        latLong[0] = latitude;
        latLong[1] = longitude;
    }

    abstract protected boolean updateFromCursor(Cursor cursor);

    public int getBucketId() {
        return bucketId;
    }

    /// M: [FEATURE.MODIFY] @{
    // When reload ContainerSet, updateContent will be called,
    // so set as public
    /*protected void updateContent(Cursor cursor) {*/
    public void updateContent(Cursor cursor) {
    /// @}
        if (updateFromCursor(cursor)) {
            mDataVersion = nextVersionNumber();
        }
    }

    @Override
    public MediaDetails getDetails() {
        MediaDetails details = super.getDetails();
        details.addDetail(MediaDetails.INDEX_PATH, filePath);
        details.addDetail(MediaDetails.INDEX_TITLE, getName());
        DateFormat formater = DateFormat.getDateTimeInstance();
        details.addDetail(MediaDetails.INDEX_DATETIME,
                formater.format(new Date(dateModifiedInSec * 1000)));
        details.addDetail(MediaDetails.INDEX_WIDTH, width);
        details.addDetail(MediaDetails.INDEX_HEIGHT, height);

        if (GalleryUtils.isValidLocation(latitude, longitude)) {
            details.addDetail(MediaDetails.INDEX_LOCATION, new double[] {latitude, longitude});
        }
        /** PRIZE-Baidu location-2015-6-12-start*/
        details.addDetail(MediaDetails.INDEX_ADDR, dbAddr);
        /** PRIZE-Baidu location-2015-6-12-start*/
        if (fileSize > 0) details.addDetail(MediaDetails.INDEX_SIZE, fileSize);
        return details;
    }

    @Override
    public String getMimeType() {
        return mimeType;
    }

    @Override
    public long getSize() {
        return fileSize;
    }
}
