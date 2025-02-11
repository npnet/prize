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
import android.provider.MediaStore;

import com.android.gallery3d.app.GalleryApp;
import com.android.gallery3d.common.ApiHelper;
import com.android.gallery3d.common.Utils;

import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

// MergeAlbum merges items from two or more MediaSets. It uses a Comparator to
// determine the order of items. The items are assumed to be sorted in the input
// media sets (with the same order that the Comparator uses).
//
// This only handles MediaItems, not SubMediaSets.
public class LocalMergeAlbum extends MediaSet implements ContentListener {
    @SuppressWarnings("unused")
    private static final String TAG = "Gallery2/LocalMergeAlbum";
    /// M: [PERF.MODIFY] @{
    // adjust PAGE_SIZE by media item count in this album
    /* private static final int PAGE_SIZE = 64; */
    private int PAGE_SIZE = 0;
    /// @}

    private final Comparator<MediaItem> mComparator;
    private final MediaSet[] mSources;

    private FetchCache[] mFetcher;
    private int mSupportedOperation;
    private int mBucketId;
    private int mCoverId = -1;
    private int mCoverType = -1;
    private int mRotation = -1;
    public String mFilePath;
    private int mCount = -1;

    // mIndex maps global position to the position of each underlying media sets.
    private TreeMap<Integer, int[]> mIndex = new TreeMap<Integer, int[]>();

    public LocalMergeAlbum(
            Path path, Comparator<MediaItem> comparator, MediaSet[] sources, int bucketId, int coverId, int coverType, int rotation, String filePath, int count) {
        super(path, INVALID_DATA_VERSION);
        mComparator = comparator;
        mSources = sources;
        mBucketId = bucketId;
        mCoverType = coverType;
        mCoverId = coverId;
        mRotation = rotation;
        mFilePath = filePath;
        mCount = count;
        for (MediaSet set : mSources) {
            set.addContentListener(this);
        }
        reload();
    }

    public void setCoverInfo(int coverId, int coverType, int rotation, String filePath, int count) {
        mCoverType = coverType;
        mCoverId = coverId;
        mRotation = rotation;
        mFilePath = filePath;
        mCount = count;
    }

    public LocalMergeAlbum(
            Path path, Comparator<MediaItem> comparator, MediaSet[] sources, int bucketId) {
        super(path, INVALID_DATA_VERSION);
        mComparator = comparator;
        mSources = sources;
        mBucketId = bucketId;
        for (MediaSet set : mSources) {
			if(set != null){
                set.addContentListener(this);
			}
        }
        reload();
    }

    @Override
    public boolean isCameraRoll() {
        if (mSources.length == 0) return false;
        for(MediaSet set : mSources) {
            if (!set.isCameraRoll()) return false;
        }
        return true;
    }

    /// M: [BUG.MODIFY] @{
    // add synchronized to avoid JE occur
    // when access updateData and getMediatem in different thread at the same time
    /* private void updateData() { */
    private synchronized void updateData() {
    /// @}
        ArrayList<MediaSet> matches = new ArrayList<MediaSet>();
        int supported = mSources.length == 0 ? 0 : MediaItem.SUPPORT_ALL;
        mFetcher = new FetchCache[mSources.length];
        for (int i = 0, n = mSources.length; i < n; ++i) {
            mFetcher[i] = new FetchCache(mSources[i]);
            supported &= mSources[i].getSupportedOperations();
        }
        mSupportedOperation = supported;
        mIndex.clear();
        mIndex.put(0, new int[mSources.length]);
    }

    private ClusterType[] sortTwoSortedArray(ClusterType[] video, ClusterType[] image) {
        if (video == null || image == null) {
            if (video == null) {
                return image;
            }
            return video;
        }
        int vLen = video.length;
        int iLen = image.length;
        int newLen = vLen + iLen;
        ClusterType[] result = new ClusterType[newLen];
        int i = 0, j = 0, k = 0;

        while (i < vLen && j < iLen) {
            if (video[i].mDatetaken > image[j].mDatetaken) {
                result[k++] = video[i++];
            } else if (video[i].mDatetaken == image[j].mDatetaken) {
                video[i].mCount += image[j].mCount;
                result[k++] = video[i];
                j++;
                i++;
            } else {
                result[k++] = image[j++];
            }
        }

        while (i < vLen) {
            result[k++] = video[i++];
        }

        while (j < iLen) {
            result[k++] = image[j++];
        }

        if (k == vLen + iLen) {
            return result;
        } else {
            ClusterType[] clusterTypes = new ClusterType[k];
            for (int m = 0, len = k; m < len; m++) {
                clusterTypes[m] = result[m];
            }
            return clusterTypes;
        }
    }
    private synchronized void invalidateCache() {
    /// @}
        for (int i = 0, n = mSources.length; i < n; i++) {
            mFetcher[i].invalidate();
        }
        mIndex.clear();
        mIndex.put(0, new int[mSources.length]);
        /// M: [PERF.ADD] @{
        // Optimize the performance to get cover for the 2nd/3rd/.. time
        synchronized (mCoverCacheLock) {
            mCoverCache = null;
        }
        /// @}
    }

    public void coverItemReset() {
        mCoverId = -1;
        mCoverType = -1;
        mRotation = -1;
        mFilePath = null;
        mCount = -1;
    }

    @Override
    public Uri getContentUri() {
        String bucketId = String.valueOf(mBucketId);
        if (ApiHelper.HAS_MEDIA_PROVIDER_FILES_TABLE) {
            return MediaStore.Files.getContentUri("external").buildUpon()
                    .appendQueryParameter(LocalSource.KEY_BUCKET_ID, bucketId)
                    .build();
        } else {
            // We don't have a single URL for a merged image before ICS
            // So we used the image's URL as a substitute.
            return MediaStore.Images.Media.EXTERNAL_CONTENT_URI.buildUpon()
                    .appendQueryParameter(LocalSource.KEY_BUCKET_ID, bucketId)
                    .build();
        }
    }

    @Override
    public String getName() {
        return mSources.length == 0 ? "" : mSources[0].getName();
    }

    @Override
    public int getMediaItemCount() {
        return getTotalMediaItemCount();
    }

    @Override
    /// M: [BUG.MODIFY] @{
    // add synchronized to avoid JE occur
    // when access updateData and getMediatem in different thread at the same time
    /* public ArrayList<MediaItem> getMediaItem(int start, int count) { */
    public synchronized ArrayList<MediaItem> getMediaItem(int start, int count) {
    /// @}
        /// M: [PERF.ADD] @{
        // adjust PAGE_SIZE by media item count in this album
        if (PAGE_SIZE == 0) {
            PAGE_SIZE = adjustPageSize();
        }
        /// @}
        // First find the nearest mark position <= start.
        SortedMap<Integer, int[]> head = mIndex.headMap(start + 1);
		//os-fix:31950-monkey test failed-20170411-pengcancan-start
        if (head == null || head.isEmpty()) {
             return new ArrayList<MediaItem>();
        }
        //os-fix:31950-monkey test failed-20170411-pengcancan-end
        int markPos = head.lastKey();
        if (head.get(markPos) == null) {
            return new ArrayList<MediaItem>();
        }
        int[] subPos = head.get(markPos).clone();
        MediaItem[] slot = new MediaItem[mSources.length];

        int size = mSources.length;

        // fill all slots
        for (int i = 0; i < size; i++) {
            slot[i] = mFetcher[i].getItem(subPos[i]);
        }

        ArrayList<MediaItem> result = new ArrayList<MediaItem>();

        for (int i = markPos; i < start + count; i++) {
            int k = -1;  // k points to the best slot up to now.
            for (int j = 0; j < size; j++) {
                if (slot[j] != null) {
                    if (k == -1 || mComparator.compare(slot[j], slot[k]) < 0) {
                        k = j;
                    }
                }
            }

            // If we don't have anything, all streams are exhausted.
            if (k == -1) break;

            // Pick the best slot and refill it.
            subPos[k]++;
            if (i >= start) {
                result.add(slot[k]);
            }
            slot[k] = mFetcher[k].getItem(subPos[k]);

            // Periodically leave a mark in the index, so we can come back later.
            if ((i + 1) % PAGE_SIZE == 0) {
                mIndex.put(i + 1, subPos.clone());
            }
        }

        return result;
    }

    @Override
    public ArrayList<String> getSubMediaItemLocation(int dataTaken) {
        ArrayList<ArrayList<String>> locationList = new ArrayList<>(mSources.length);
        ArrayList<String> locations = new ArrayList<>();
        for (MediaSet set : mSources) {
            ArrayList<String> sub = set.getSubMediaItemLocation(dataTaken);
            if (sub != null) {
                locationList.add(sub);
            }
        }
        for (ArrayList<String> locationArr : locationList) {
            for (int i = 0; i < locationArr.size(); i++) {
                if (!locations.contains(locationArr.get(i))) {
                    locations.add(locationArr.get(i));
                }
            }
        }
        return locations;
    }

    @Override
    public ClusterType[] getSubMediaItemType() {
        ArrayList<ClusterType[]> clusterTypes = new ArrayList<>(mSources.length);
        for (MediaSet set : mSources) {
            clusterTypes.add(set.getSubMediaItemType());
        }
        ClusterType[] result = sortTwoSortedArray(clusterTypes.get(0), clusterTypes.get(1));
        if (result != null) {
            for (int i = 0; i < result.length; i++) {
                Log.i(TAG, "getSubMediaItemType ClusterType=" + result[i]);
            }
        } else {
            Log.i(TAG, "getSubMediaItemType result==null");
        }

        return result;
    }

    @Override
    public int getTotalMediaItemCount() {
        int count = 0;
        for (MediaSet set : mSources) {
            count += set.getTotalMediaItemCount();
        }
        return count;
    }

    @Override
    public int getAlbumSetTotalMediaItemCount() {
        if (mCount != -1) {
            return mCount;
        }
        return getTotalMediaItemCount();
    }

    @Override
    public long reload() {
        boolean changed = false;
        for (int i = 0, n = mSources.length; i < n; ++i) {
            if (mSources[i].reload() > mDataVersion) changed = true;
        }
        if (changed) {
            mDataVersion = nextVersionNumber();
            updateData();
            invalidateCache();
        }
        return mDataVersion;
    }

    @Override
    public void onContentDirty() {
        notifyContentChanged();
    }

    @Override
    public int getSupportedOperations() {
        return mSupportedOperation;
    }

    @Override
    public void delete() {
        for (MediaSet set : mSources) {
            set.delete();
        }
    }

    @Override
    public void rotate(int degrees) {
        for (MediaSet set : mSources) {
            set.rotate(degrees);
        }
    }

    /// M: [FEATURE.MODIFY] @{
    /* private static class FetchCache { */
    private class FetchCache {
    /// @}
        private MediaSet mBaseSet;
        private SoftReference<ArrayList<MediaItem>> mCacheRef;
        private int mStartPos;

        public FetchCache(MediaSet baseSet) {
            mBaseSet = baseSet;
        }

        public void invalidate() {
            mCacheRef = null;
        }

        public MediaItem getItem(int index) {
            /// M: [PERF.ADD] @{
            // avoid PAGE_SIZE not initialized
            if (PAGE_SIZE == 0) {
                PAGE_SIZE = adjustPageSize();
            }
            /// @}
            boolean needLoading = false;
            ArrayList<MediaItem> cache = null;
            if (mCacheRef == null
                    || index < mStartPos || index >= mStartPos + PAGE_SIZE) {
                needLoading = true;
            } else {
                cache = mCacheRef.get();
                if (cache == null) {
                    needLoading = true;
                }
            }

            if (needLoading) {
                cache = mBaseSet.getMediaItem(index, PAGE_SIZE);
                mCacheRef = new SoftReference<ArrayList<MediaItem>>(cache);
                mStartPos = index;
            }

            if (index < mStartPos || index >= mStartPos + cache.size()) {
                return null;
            }

            return cache.get(index - mStartPos);
        }
    }

    @Override
    public boolean isLeafAlbum() {
        return true;
    }

    //********************************************************************
    //*                              MTK                                 *
    //********************************************************************
    private static final int PAGE_SIZE_ADJUST_PARAM = 10;
    private static final int MAX_PAGE_SIZE = 1024;
    private static final int MIN_PAGE_SIZE = 64;

    private int adjustPageSize() {
        int pageSize = getMediaItemCount() / PAGE_SIZE_ADJUST_PARAM;
        pageSize = pageSize <= 0 ? 1 : pageSize;
        pageSize = Utils.nextPowerOf2(pageSize);
        if (pageSize > MAX_PAGE_SIZE) {
            pageSize = MAX_PAGE_SIZE;
        } else if (pageSize < MIN_PAGE_SIZE) {
            pageSize = MIN_PAGE_SIZE;
        }
        return pageSize;
    }

    /// M: [PERF.ADD] @{
    // Optimize the performance to get cover for the 2nd/3rd/.. time
    private MediaItem mCoverCache;
    private Object mCoverCacheLock = new Object();

    @Override
    public MediaItem getCoverMediaItem() {
        synchronized (mCoverCacheLock) {
            if (mCoverCache != null) {
                return mCoverCache;
            }
        }
        MediaItem cover = super.getCoverMediaItem();
        synchronized (mCoverCacheLock) {
            mCoverCache = cover;
            return mCoverCache;
        }
    }
    /// @}

    @Override
    public MediaItem getCoverMediaItem(GalleryApp galleryApp) {
        if (galleryApp != null && mCoverId != -1 && mCoverType != -1) {
            if (mCoverType == 1) {//image
                Path path = LocalImage.ITEM_PATH.getChild(mCoverId);
                return new LocalImage(path, galleryApp, mRotation, mFilePath);
            } else { //video
                Path path = LocalVideo.ITEM_PATH.getChild(mCoverId);
                return new LocalVideo(path, galleryApp, mFilePath);
            }
            /*MediaItem item = LocalAlbum.getMediaItemById(galleryApp, mCoverType == 1, mCoverId);
            if (item != null) {
                return item;
            }*/
        }
        return getCoverMediaItem();
    }
}
