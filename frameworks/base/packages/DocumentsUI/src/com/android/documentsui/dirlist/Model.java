/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.documentsui.dirlist;

import static com.android.documentsui.Shared.DEBUG;
import static com.android.documentsui.State.SORT_ORDER_DISPLAY_NAME;
import static com.android.documentsui.State.SORT_ORDER_LAST_MODIFIED;
import static com.android.documentsui.State.SORT_ORDER_SIZE;
import static com.android.documentsui.model.DocumentInfo.getCursorLong;
import static com.android.documentsui.model.DocumentInfo.getCursorString;

import android.database.Cursor;
import android.database.MergeCursor;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import android.provider.MediaStore;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.util.Log;

import com.android.documentsui.DocumentsFeatureOption;
import com.android.documentsui.DirectoryResult;
import com.android.documentsui.RootCursorWrapper;
import com.android.documentsui.Shared;
import com.android.documentsui.dirlist.MultiSelectManager.Selection;
import com.android.documentsui.model.DocumentInfo;
import static com.android.documentsui.model.DocumentInfo.getCursorInt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/// M: Add to support drm
import com.mediatek.omadrm.OmaDrmStore;


/**
 * The data model for the current loaded directory.
 */
@VisibleForTesting
public class Model {
    private static final String TAG = "Model";
    private final Object mLock = new Object();

    private boolean mIsLoading;
    private List<UpdateListener> mUpdateListeners = new ArrayList<>();
    @Nullable private Cursor mCursor;
    private int mCursorCount;

    /// M: add to support drm, only show these drm files match given drm level.
    /// mCount is the index at which useful cursor item will be present
    private int mCount = 0;
    private int mDrmLevel = -1;


    /** Maps Model ID to cursor positions, for looking up items by Model ID. */
    private Map<String, Integer> mPositions = new HashMap<>();
    /**
     * A sorted array of model IDs for the files currently in the Model.  Sort order is determined
     * by {@link #mSortOrder}
     */
    private String mIds[] = new String[0];
    private int mSortOrder = SORT_ORDER_DISPLAY_NAME;

    @Nullable String info;
    @Nullable String error;
    @Nullable DocumentInfo doc;

    private void notifyUpdateListeners() {
        for (UpdateListener listener: mUpdateListeners) {
            listener.onModelUpdate(this);
        }
    }

    private void notifyUpdateListeners(Exception e) {
        for (UpdateListener listener: mUpdateListeners) {
            listener.onModelUpdateFailed(e);
        }
    }

    void update(DirectoryResult result) {
        if (DEBUG) Log.i(TAG, "Updating model with new result set.");

        if (result == null) {
            mCursor = null;
            mCursorCount = 0;
            mIds = new String[0];
            mPositions.clear();
            info = null;
            error = null;
            doc = null;
            mIsLoading = false;
            notifyUpdateListeners();
            return;
        }

        if (result.exception != null) {
            mCursor = result.cursor;
            Log.e(TAG, "Error while loading directory contents", result.exception);
            notifyUpdateListeners(result.exception);
            return;
        }

        mCursor = result.cursor;
        mCursorCount = mCursor.getCount();
        mSortOrder = result.sortOrder;
        doc = result.doc;

        try {
            updateModelData();
        } catch (Exception e) {
            Log.e(TAG, "Error while accessing cursors", e);
            notifyUpdateListeners(e);
            return;
        }

        final Bundle extras = mCursor.getExtras();
        if (extras != null) {
            info = extras.getString(DocumentsContract.EXTRA_INFO);
            error = extras.getString(DocumentsContract.EXTRA_ERROR);
            mIsLoading = extras.getBoolean(DocumentsContract.EXTRA_LOADING, false);
        }

        notifyUpdateListeners();
    }

    void update(DirectoryResult result, boolean requireUpdate) {
        if (DEBUG) Log.i(TAG, "Updating model with new result set. requireUpdate = "
            + requireUpdate);
        if (DEBUG) Log.i(TAG, "Updating model with new result set. result = " + result);

        if (result == null) {
            mCursor = null;
            mCursorCount = 0;
            mIds = new String[0];
            mPositions.clear();
            info = null;
            error = null;
            doc = null;
            mIsLoading = false;
            if (requireUpdate)
                notifyUpdateListeners();
            return;
        }

        if (result.exception != null) {
            mCursor = result.cursor;
            Log.e(TAG, "Error while loading directory contents", result.exception);
            if (requireUpdate)
                notifyUpdateListeners(result.exception);
            return;
        }

        mCursor = result.cursor;
        mCursorCount = mCursor.getCount();
        mSortOrder = result.sortOrder;
        doc = result.doc;

        try {
            if (requireUpdate)
                updateModelData();
        } catch (Exception e) {
            Log.e(TAG, "Error while accessing cursors", e);
            if (requireUpdate)
                notifyUpdateListeners(e);
            return;
        }

        final Bundle extras = mCursor.getExtras();
        if (extras != null) {
            info = extras.getString(DocumentsContract.EXTRA_INFO);
            error = extras.getString(DocumentsContract.EXTRA_ERROR);
            mIsLoading = extras.getBoolean(DocumentsContract.EXTRA_LOADING, false);
        }
        if (requireUpdate)
            notifyUpdateListeners();
    }

    @VisibleForTesting
    int getItemCount() {
        /// M: add to support drm, we filter some drm with given drm level,
        /// so we need return real cursor count.
        return mCount;
    }

    /**
     * Scan over the incoming cursor data, generate Model IDs for each row, and sort the IDs
     * according to the current sort order.
     */
    private void updateModelData() {
    Log.v(TAG, "Model updateModelData mCursorCount = " + mCursorCount);
            /// M: No need to update when there is no data
            if (mCursorCount <= 0)
                return;
        int[] positions = new int[mCursorCount];
        mIds = new String[mCursorCount];
        boolean[] isDirs = new boolean[mCursorCount];
        String[] displayNames = null;
        long[] longValues = null;

        switch (mSortOrder) {
            case SORT_ORDER_DISPLAY_NAME:
                displayNames = new String[mCursorCount];
                break;
            case SORT_ORDER_LAST_MODIFIED:
            case SORT_ORDER_SIZE:
                longValues = new long[mCursorCount];
                break;
        }

        /// M: Add to support drm. we only show match given drm level in intent extra,
        /// if don't limit drm level, we will show all drm files to user. {@
        int needShowDrmMethod = 0;
        if (DocumentsFeatureOption.IS_SUPPORT_DRM) {
            switch (mDrmLevel) {
                case OmaDrmStore.DrmIntentExtra.LEVEL_FL:
                    needShowDrmMethod = OmaDrmStore.Method.FL;
                    break;
                case OmaDrmStore.DrmIntentExtra.LEVEL_SD:
                    needShowDrmMethod = OmaDrmStore.Method.SD;
                    break;
                case OmaDrmStore.DrmIntentExtra.LEVEL_ALL:
                    needShowDrmMethod = OmaDrmStore.Method.FL |
                      OmaDrmStore.Method.CD
                            | OmaDrmStore.Method.SD | OmaDrmStore.Method.FLSD;
                    break;
                default:
                    needShowDrmMethod = OmaDrmStore.Method.FL |
                      OmaDrmStore.Method.CD
                            | OmaDrmStore.Method.SD | OmaDrmStore.Method.FLSD;
                    break;
            }
        }
        /// @}


        String mimeType;

        mCursor.moveToPosition(-1);
        /// M: Always set mCount as zero before start to count valid
        mCount = 0;
        for (int pos = 0; pos < mCursorCount; ++pos) {
            if (!mCursor.moveToNext()) {
                Log.e(TAG, "Fail to move cursor to next pos: " + pos);
                return;
            }

            /// M: If it's no need show drmMethod, ignore it and make it hidden. {@
            if (DocumentsFeatureOption.IS_SUPPORT_DRM) {
                boolean isDrm = getCursorInt(mCursor, MediaStore.MediaColumns.IS_DRM) > 0;
                int drmMethod = getCursorInt(mCursor, MediaStore.MediaColumns.DRM_METHOD);
                /// M: If IS_DRM is true but drm_method is invalid(-1)
                /// with given need show drm level(mDrmLevel>0),
                /// this may happen when drm file has been deleted,
                /// we don't need show these drm files.
                if (isDrm && ((mDrmLevel > 0 && drmMethod < 0) ||
                              (needShowDrmMethod & drmMethod) == 0)) {
                    continue;
                }
            }
            positions[mCount] = mCursor.getPosition();
            /// @}

            /// M: Mark Google code here as all the "pos" shall not be used
            //positions[pos] = pos;

            // Generates a Model ID for a cursor entry that refers to a document. The Model ID is a
            // unique string that can be used to identify the document referred to by the cursor.
            // If the cursor is a merged cursor over multiple authorities, then prefix the ids
            // with the authority to avoid collisions.
            if (mCursor instanceof MergeCursor) {
                mIds[mCount] = getCursorString(mCursor, RootCursorWrapper.COLUMN_AUTHORITY) + "|" +
                        getCursorString(mCursor, Document.COLUMN_DOCUMENT_ID);
            } else {
                mIds[mCount] = getCursorString(mCursor, Document.COLUMN_DOCUMENT_ID);
            }

            mimeType = getCursorString(mCursor, Document.COLUMN_MIME_TYPE);
            isDirs[mCount] = Document.MIME_TYPE_DIR.equals(mimeType);

            switch(mSortOrder) {
                case SORT_ORDER_DISPLAY_NAME:
                    final String displayName = getCursorString(
                            mCursor, Document.COLUMN_DISPLAY_NAME);
                    displayNames[mCount] = displayName;
                    break;
                case SORT_ORDER_LAST_MODIFIED:
                    longValues[mCount] = getLastModified(mCursor);
                    break;
                case SORT_ORDER_SIZE:
                    longValues[mCount] = getCursorLong(mCursor, Document.COLUMN_SIZE);
                    break;
            }
            mCount++;
            if (DEBUG) Log.v(TAG, "Model updateModelData increase mCount = " + mCount);
        }

        switch (mSortOrder) {
            case SORT_ORDER_DISPLAY_NAME:
                /// M: Change sort to use mCount
                binarySort(displayNames, isDirs, positions, mIds, mCount);
                break;
            case SORT_ORDER_LAST_MODIFIED:
            case SORT_ORDER_SIZE:
                /// M: Change sort to use mCount
                binarySort(longValues, isDirs, positions, mIds, mCount);
                break;
        }

        // Populate the positions.
        /// M: Only work for useful mCount and not complete mCursorCount here
        mPositions.clear();
        for (int i = 0; i < mCount; ++i) {
            mPositions.put(mIds[i], positions[i]);
        }
    }

    /**
     * Sorts model data. Takes three columns of index-corresponded data. The first column is the
     * sort key. Rows are sorted in ascending alphabetical order on the sort key.
     * Directories are always shown first. This code is based on TimSort.binarySort().
     *
     * @param sortKey Data is sorted in ascending alphabetical order.
     * @param isDirs Array saying whether an item is a directory or not.
     * @param positions Cursor positions to be sorted.
     * @param ids Model IDs to be sorted.
     */
    private static void binarySort(String[] sortKey, boolean[] isDirs, int[] positions,
                                   String[] ids, int count) {
        //final int count = positions.length;
        for (int start = 1; start < count; start++) {
            final int pivotPosition = positions[start];
            final String pivotValue = sortKey[start];
            final boolean pivotIsDir = isDirs[start];
            final String pivotId = ids[start];

            int left = 0;
            int right = start;

            while (left < right) {
                int mid = (left + right) >>> 1;

                // Directories always go in front.
                int compare = 0;
                final boolean rhsIsDir = isDirs[mid];
                if (pivotIsDir && !rhsIsDir) {
                    compare = -1;
                } else if (!pivotIsDir && rhsIsDir) {
                    compare = 1;
                } else {
                    final String lhs = pivotValue;
                    final String rhs = sortKey[mid];
                    compare = Shared.compareToIgnoreCaseNullable(lhs, rhs);
                }

                if (compare < 0) {
                    right = mid;
                } else {
                    left = mid + 1;
                }
            }

            int n = start - left;
            switch (n) {
                case 2:
                    positions[left + 2] = positions[left + 1];
                    sortKey[left + 2] = sortKey[left + 1];
                    isDirs[left + 2] = isDirs[left + 1];
                    ids[left + 2] = ids[left + 1];
                case 1:
                    positions[left + 1] = positions[left];
                    sortKey[left + 1] = sortKey[left];
                    isDirs[left + 1] = isDirs[left];
                    ids[left + 1] = ids[left];
                    break;
                default:
                    System.arraycopy(positions, left, positions, left + 1, n);
                    System.arraycopy(sortKey, left, sortKey, left + 1, n);
                    System.arraycopy(isDirs, left, isDirs, left + 1, n);
                    System.arraycopy(ids, left, ids, left + 1, n);
            }

            positions[left] = pivotPosition;
            sortKey[left] = pivotValue;
            isDirs[left] = pivotIsDir;
            ids[left] = pivotId;
        }
    }

    /**
     * Sorts model data. Takes four columns of index-corresponded data. The first column is the sort
     * key, and the second is an array of mime types. The rows are first bucketed by mime type
     * (directories vs documents) and then each bucket is sorted independently in descending
     * numerical order on the sort key. This code is based on TimSort.binarySort().
     *
     * @param sortKey Data is sorted in descending numerical order.
     * @param isDirs Array saying whether an item is a directory or not.
     * @param positions Cursor positions to be sorted.
     * @param ids Model IDs to be sorted.
     */
    private static void binarySort(
            long[] sortKey, boolean[] isDirs, int[] positions, String[] ids, int count) {
        //final int count = positions.length;
        for (int start = 1; start < count; start++) {
            final int pivotPosition = positions[start];
            final long pivotValue = sortKey[start];
            final boolean pivotIsDir = isDirs[start];
            final String pivotId = ids[start];

            int left = 0;
            int right = start;

            while (left < right) {
                int mid = ((left + right) >>> 1);

                // Directories always go in front.
                int compare = 0;
                final boolean rhsIsDir = isDirs[mid];
                if (pivotIsDir && !rhsIsDir) {
                    compare = -1;
                } else if (!pivotIsDir && rhsIsDir) {
                    compare = 1;
                } else {
                    final long lhs = pivotValue;
                    final long rhs = sortKey[mid];
                    // Sort in descending numerical order. This matches legacy behaviour, which
                    // yields largest or most recent items on top.
                    compare = -Long.compare(lhs, rhs);
                }

                // If numerical comparison yields a tie, use document ID as a tie breaker.  This
                // will yield stable results even if incoming items are continually shuffling and
                // have identical numerical sort keys.  One common example of this scenario is seen
                // when sorting a set of active downloads by mod time.
                if (compare == 0) {
                    compare = pivotId.compareTo(ids[mid]);
                }

                if (compare < 0) {
                    right = mid;
                } else {
                    left = mid + 1;
                }
            }

            int n = start - left;
            switch (n) {
                case 2:
                    positions[left + 2] = positions[left + 1];
                    sortKey[left + 2] = sortKey[left + 1];
                    isDirs[left + 2] = isDirs[left + 1];
                    ids[left + 2] = ids[left + 1];
                case 1:
                    positions[left + 1] = positions[left];
                    sortKey[left + 1] = sortKey[left];
                    isDirs[left + 1] = isDirs[left];
                    ids[left + 1] = ids[left];
                    break;
                default:
                    System.arraycopy(positions, left, positions, left + 1, n);
                    System.arraycopy(sortKey, left, sortKey, left + 1, n);
                    System.arraycopy(isDirs, left, isDirs, left + 1, n);
                    System.arraycopy(ids, left, ids, left + 1, n);
            }

            positions[left] = pivotPosition;
            sortKey[left] = pivotValue;
            isDirs[left] = pivotIsDir;
            ids[left] = pivotId;
        }
    }

    /**
     * @return Timestamp for the given document. Some docs (e.g. active downloads) have a null
     * timestamp - these will be replaced with MAX_LONG so that such files get sorted to the top
     * when sorting by date.
     */
    long getLastModified(Cursor cursor) {
        long l = getCursorLong(mCursor, Document.COLUMN_LAST_MODIFIED);
        return (l == -1) ? Long.MAX_VALUE : l;
    }

    public @Nullable Cursor getItem(String modelId) {
        Integer pos = mPositions.get(modelId);
        if (pos == null) {
            if (DEBUG) Log.d(TAG, "Unabled to find cursor position for modelId: " + modelId);
            return null;
        }
        synchronized (mLock) {
            if (DEBUG) Log.d(TAG, "getItem modelId: " + modelId + "pos " + pos);

        if (mCursor != null && !mCursor.moveToPosition(pos)) {
            if (DEBUG) Log.d(TAG,
                    "Unabled to move cursor to position " + pos + " for modelId: " + modelId);
            return null;
        }
        }
        return mCursor;
    }

    boolean isEmpty() {
        return mCursorCount == 0;
    }

    boolean isLoading() {
        return mIsLoading;
    }

    List<DocumentInfo> getDocuments(Selection items) {
        final int size = (items != null) ? items.size() : 0;

        final List<DocumentInfo> docs =  new ArrayList<>(size);
        synchronized (mLock) {

            Log.d(TAG, "getDocuments items.size: " + size);
        for (String modelId: items.getAll()) {
            final Cursor cursor = getItem(modelId);
            if (cursor == null) {
                Log.w(TAG,
                        "Skipping document. Unabled to obtain cursor for modelId: " + modelId);
                continue;
            }
            docs.add(DocumentInfo.fromDirectoryCursor(cursor));
        }
        }
        return docs;
    }

    void addUpdateListener(UpdateListener listener) {
        mUpdateListeners.add(listener);
    }

    void removeUpdateListener(UpdateListener listener) {
        mUpdateListeners.remove(listener);
    }

    static interface UpdateListener {
        /**
         * Called when a successful update has occurred.
         */
        void onModelUpdate(Model model);

        /**
         * Called when an update has been attempted but failed.
         */
        void onModelUpdateFailed(Exception e);
    }

    /**
     * @return An ordered array of model IDs representing the documents in the model. It is sorted
     *         according to the current sort order, which was set by the last model update.
     */
    public String[] getModelIds() {
        return mIds;
    }
}
