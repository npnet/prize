package com.android.gallery3d.data;

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore.Files;
import android.provider.MediaStore.Files.FileColumns;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Images.ImageColumns;
import android.provider.MediaStore.Video;
import android.util.Log;

import com.android.gallery3d.common.ApiHelper;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.util.ThreadPool.JobContext;
import com.mediatek.galleryframework.base.MediaFilterSetting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;

class BucketHelper {

    private static final String TAG = "Gallery2/BucketHelper";
    private static final String EXTERNAL_MEDIA = "external";

    // BUCKET_DISPLAY_NAME is a string like "Camera" which is the directory
    // name of where an image or video is in. BUCKET_ID is a hash of the path
    // name of that directory (see computeBucketValues() in MediaProvider for
    // details). MEDIA_TYPE is video, image, audio, etc.
    //
    // The "albums" are not explicitly recorded in the database, but each image
    // or video has the two columns (BUCKET_ID, MEDIA_TYPE). We define an
    // "album" to be the collection of images/videos which have the same value
    // for the two columns.
    //
    // The goal of the query (used in loadSubMediaSetsFromFilesTable()) is to
    // find all albums, that is, all unique values for (BUCKET_ID, MEDIA_TYPE).
    // In the meantime sort them by the timestamp of the latest image/video in
    // each of the album.
    //
    // The order of columns below is important: it must match to the index in
    // MediaStore.
    private static final String[] PROJECTION_BUCKET = {
            ImageColumns.BUCKET_ID,
            FileColumns.MEDIA_TYPE,
            ImageColumns.BUCKET_DISPLAY_NAME,
            ImageColumns._ID};

    // The indices should match the above projections.
    private static final int INDEX_BUCKET_ID = 0;
    private static final int INDEX_MEDIA_TYPE = 1;
    private static final int INDEX_BUCKET_NAME = 2;
    private static final int INDEX_ID = 3;
    private static final int INDEX_ORIENTATION = 4;
    private static final int INDEX_FILE_PATH = 5;
    private static final int INDEX_COUNT = 6;

    // We want to order the albums by reverse chronological order. We abuse the
    // "WHERE" parameter to insert a "GROUP BY" clause into the SQL statement.
    // The template for "WHERE" parameter is like:
    //    SELECT ... FROM ... WHERE (%s)
    // and we make it look like:
    //    SELECT ... FROM ... WHERE (1) GROUP BY 1,(2)
    // The "(1)" means true. The "1,(2)" means the first two columns specified
    // after SELECT. Note that because there is a ")" in the template, we use
    // "(2" to match it.
    private static final String BUCKET_GROUP_BY = "1) GROUP BY 1,(2";

    private static final String BUCKET_ORDER_BY = "MAX(datetaken) DESC";

    // Before HoneyComb there is no Files table. Thus, we need to query the
    // bucket info from the Images and Video tables and then merge them
    // together.
    //
    // A bucket can exist in both tables. In this case, we need to find the
    // latest timestamp from the two tables and sort ourselves. So we add the
    // MAX(date_taken) to the projection and remove the media_type since we
    // already know the media type from the table we query from.
    private static final String[] PROJECTION_BUCKET_IN_ONE_TABLE = {
            ImageColumns.BUCKET_ID,
            "MAX(datetaken)",
            ImageColumns.BUCKET_DISPLAY_NAME};

    // We keep the INDEX_BUCKET_ID and INDEX_BUCKET_NAME the same as
    // PROJECTION_BUCKET so we can reuse the values defined before.
    private static final int INDEX_DATE_TAKEN = 1;

    // When query from the Images or Video tables, we only need to group by BUCKET_ID.
    private static final String BUCKET_GROUP_BY_IN_ONE_TABLE = "1) GROUP BY (1";

    public static BucketEntry[] loadBucketEntries(
            JobContext jc, ContentResolver resolver, int type) {
        if (ApiHelper.HAS_MEDIA_PROVIDER_FILES_TABLE) {
            return loadBucketEntriesFromFilesTable(jc, resolver, type);
        } else {
            return loadBucketEntriesFromImagesAndVideoTable(jc, resolver, type);
        }
    }

    private static void updateBucketEntriesFromTable(JobContext jc,
            ContentResolver resolver, Uri tableUri, HashMap<Integer, BucketEntry> buckets) {
        /// M: [FEATURE.MARK] @{
        /*Cursor cursor = resolver.query(tableUri, PROJECTION_BUCKET_IN_ONE_TABLE,
         BUCKET_GROUP_BY_IN_ONE_TABLE, null, null);*/
        String whereGroup = MediaFilterSetting.getExtWhereClause(null);
        if (null == whereGroup || whereGroup.equals("")) {
            whereGroup = PURE_BUCKET_GROUP_BY_IN_ONE_TABLE;
        } else {
            whereGroup = "(" + whereGroup + ")" + PURE_BUCKET_GROUP_BY_IN_ONE_TABLE;
        }
        Cursor cursor = resolver.query(tableUri, PROJECTION_BUCKET_IN_ONE_TABLE,
                whereGroup, null, null);
        /// @}
        if (cursor == null) {
            Log.w(TAG, "cannot open media database: " + tableUri);
            return;
        }
        try {
            while (cursor.moveToNext()) {
                int bucketId = cursor.getInt(INDEX_BUCKET_ID);
                int dateTaken = cursor.getInt(INDEX_DATE_TAKEN);
                BucketEntry entry = buckets.get(bucketId);
                if (entry == null) {
                    entry = new BucketEntry(bucketId, cursor.getString(INDEX_BUCKET_NAME));
                    buckets.put(bucketId, entry);
                    entry.dateTaken = dateTaken;
                } else {
                    entry.dateTaken = Math.max(entry.dateTaken, dateTaken);
                }
            }
        } finally {
            Utils.closeSilently(cursor);
        }
    }

    private static BucketEntry[] loadBucketEntriesFromImagesAndVideoTable(
            JobContext jc, ContentResolver resolver, int type) {
        HashMap<Integer, BucketEntry> buckets = new HashMap<Integer, BucketEntry>(64);
        if ((type & MediaObject.MEDIA_TYPE_IMAGE) != 0) {
            updateBucketEntriesFromTable(
                    jc, resolver, Images.Media.EXTERNAL_CONTENT_URI, buckets);
        }
        if ((type & MediaObject.MEDIA_TYPE_VIDEO) != 0) {
            updateBucketEntriesFromTable(
                    jc, resolver, Video.Media.EXTERNAL_CONTENT_URI, buckets);
        }
        BucketEntry[] entries = buckets.values().toArray(new BucketEntry[buckets.size()]);
        Arrays.sort(entries, new Comparator<BucketEntry>() {
            @Override
            public int compare(BucketEntry a, BucketEntry b) {
                // sorted by dateTaken in descending order
                return b.dateTaken - a.dateTaken;
            }
        });
        return entries;
    }

    private static String getConshotWhere() {
        StringBuilder sb = new StringBuilder();
        sb.append("( " + Images.Media.GROUP_ID + " = 0");
        sb.append(" OR (" + Images.Media.GROUP_ID + " IS NOT NULL"
                + " AND " + ImageColumns.TITLE + " NOT LIKE 'IMG%CS')");
        sb.append(" OR " + Images.Media.GROUP_ID + " IS NULL)");
        sb.append(" OR ");
        sb.append("_id in (SELECT min(_id) FROM images WHERE ");
        sb.append(Images.Media.GROUP_ID + " != 0");
        sb.append(" AND " + Images.Media.TITLE + " LIKE 'IMG%CS'");
        sb.append(" GROUP BY " + Images.Media.GROUP_ID + ")");
        return sb.toString();
    }

    private static BucketEntry[] loadBucketEntriesFromFilesTable(
            JobContext jc, ContentResolver resolver, int type) {
        Uri uri = getFilesContentUri();
        /// M: [FEATURE.MARK] @{
        /*Cursor cursor = resolver.query(uri,
         PROJECTION_BUCKET, BUCKET_GROUP_BY,
         null, BUCKET_ORDER_BY);*/
        String whereGroup = MediaFilterSetting.getExtWhereClause(null);
        if (null == whereGroup || whereGroup.equals("")) {
            whereGroup = VIDEO_IMAGE_CLAUSE/* + PURE_BUCKET_GROUP_BY*/;
        } else {
            whereGroup = "((" + VIDEO_IMAGE_CLAUSE + ") AND (" + whereGroup + "))" /*+
                    PURE_BUCKET_GROUP_BY*/;
        }
        whereGroup = "((" + whereGroup + ") AND (" + getConshotWhere() + "))";
        /*Cursor cursor = resolver.query(uri,
                PROJECTION_BUCKET, whereGroup,
                null, BUCKET_ORDER_BY);*/
        Cursor cursor =
                resolver.query(uri,
                        new String[] {"bucket_id, media_type, bucket_display_name, _id, orientation, _data, count(*) from (select * "
                                + "from files where " + whereGroup + " order by datetaken asc, _id asc) 'temp' group by parent order by parent desc--"},
                        null,
                        null,
                        null);
        /// @}
        if (cursor == null) {
            Log.w(TAG, "cannot open local database: " + uri);
            return new BucketEntry[0];
        }
        ArrayList<BucketEntry> buffer = new ArrayList<BucketEntry>();
        int typeBits = 0;
        if ((type & MediaObject.MEDIA_TYPE_IMAGE) != 0) {
            typeBits |= (1 << FileColumns.MEDIA_TYPE_IMAGE);
        }
        if ((type & MediaObject.MEDIA_TYPE_VIDEO) != 0) {
            typeBits |= (1 << FileColumns.MEDIA_TYPE_VIDEO);
        }
        try {
            while (cursor.moveToNext()) {
                int mediaType = cursor.getInt(INDEX_MEDIA_TYPE);
                if ((typeBits & (1 << mediaType)) != 0) {
                    BucketEntry entry = new BucketEntry(
                            cursor.getInt(INDEX_BUCKET_ID),
                            cursor.getString(INDEX_BUCKET_NAME), cursor.getInt(INDEX_ID), mediaType, cursor.getInt(INDEX_ORIENTATION), cursor.getString(INDEX_FILE_PATH), cursor.getInt(INDEX_COUNT));
                    if (!buffer.contains(entry)) {
                        buffer.add(entry);
                    }
                }
                if (jc.isCancelled()) return null;
            }
        } finally {
            Utils.closeSilently(cursor);
        }
        return buffer.toArray(new BucketEntry[buffer.size()]);
    }

    private static String getBucketNameInTable(
            ContentResolver resolver, Uri tableUri, int bucketId) {
        String selectionArgs[] = new String[] {String.valueOf(bucketId)};
        Uri uri = tableUri.buildUpon()
                .appendQueryParameter("limit", "1")
                .build();
        Cursor cursor = resolver.query(uri, PROJECTION_BUCKET_IN_ONE_TABLE,
                "bucket_id = ?", selectionArgs, null);
        try {
            if (cursor != null && cursor.moveToNext()) {
                /// M: [BUG.ADD] @{
                if (cursor.getString(INDEX_BUCKET_NAME) == null) {
                    Log.d(TAG, "<getBucketNameInTable> bucket name not found, uri=" + uri
                            + " bucket_id=" + bucketId);
                }
                /// @}
                return cursor.getString(INDEX_BUCKET_NAME);
            }
        } finally {
            Utils.closeSilently(cursor);
        }
        /// M: [DEBUG.ADD] @{
        Log.d(TAG, "<getBucketNameInTable> getBucketNameInTable return null, uri=" + uri
                + " bucket_id=" + bucketId);
        /// @}
        return null;
    }

    @TargetApi(ApiHelper.VERSION_CODES.HONEYCOMB)
    private static Uri getFilesContentUri() {
        return Files.getContentUri(EXTERNAL_MEDIA);
    }

    public static String getBucketName(ContentResolver resolver, int bucketId) {
        if (ApiHelper.HAS_MEDIA_PROVIDER_FILES_TABLE) {
            String result = getBucketNameInTable(resolver, getFilesContentUri(), bucketId);
            return result == null ? "" : result;
        } else {
            String result = getBucketNameInTable(
                    resolver, Images.Media.EXTERNAL_CONTENT_URI, bucketId);
            if (result != null) return result;
            result = getBucketNameInTable(
                    resolver, Video.Media.EXTERNAL_CONTENT_URI, bucketId);
            return result == null ? "" : result;
        }
    }

    public static class BucketEntry {
        public String bucketName;
        public int bucketId;
        public int dateTaken;
        public int id;
        public int mediaType;
        public int rotation;
        public String filePath;
        public int count;

        public BucketEntry(int id, String name) {
            bucketId = id;
            bucketName = Utils.ensureNotNull(name);
        }

        public BucketEntry(int bucketId, String name, int id, int mediaType, int rotation, String filePath, int count) {
            this.bucketId = bucketId;
            bucketName = Utils.ensureNotNull(name);
            this.id = id;
            this.mediaType = mediaType;
            this.rotation = rotation;
            this.filePath = filePath;
            this.count = count;
        }

        @Override
        public int hashCode() {
            return bucketId;
        }

        @Override
        public boolean equals(Object object) {
            if (!(object instanceof BucketEntry)) return false;
            BucketEntry entry = (BucketEntry) object;
            return bucketId == entry.bucketId;
        }
    }

    //********************************************************************
    //*                              MTK                                 *
    //********************************************************************

    private static final String PURE_BUCKET_GROUP_BY = ") GROUP BY 1,(2";
    private static final String PURE_BUCKET_GROUP_BY_IN_ONE_TABLE = ") GROUP BY (1";
    // as we only care about videos and images, add condition that only queries
    // videos and images from files table
    private static final String VIDEO_IMAGE_CLAUSE = FileColumns.MEDIA_TYPE
            + "=" + FileColumns.MEDIA_TYPE_IMAGE + " OR "
            + FileColumns.MEDIA_TYPE + "=" + FileColumns.MEDIA_TYPE_VIDEO;
}
