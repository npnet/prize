package com.mediatek.galleryfeature.video;


import android.graphics.Rect;
import android.media.MediaMetadataRetriever;

import com.mediatek.galleryframework.base.Generator;
import com.mediatek.galleryframework.base.MediaData;
import com.mediatek.galleryframework.util.MtkLog;

public class VideoToVideoGenerator extends Generator {
    private static final String TAG = "MtkGallery2/VideoToVideoGenerator";
    private static final String NO_TRANSCODING_IN_PROCESS
        = "now no transcoding in process; here we go! ";
    private static String sDbgTranscodingProcessTracking
        = NO_TRANSCODING_IN_PROCESS;
    private static final Object LOCK_DEBUG_TRCK = new Object();
    private static long sDbgLastTranscodingStartTime = 0;

    private static Rect getTargetRect(int srcWidth, int srcHeight, int maxWidth, int maxHeight) {
        if ((srcWidth <= maxWidth) || (srcHeight <= maxHeight)) {
            return new Rect(0, 0, srcWidth, srcHeight);
        }

        float rSrc = (float) srcWidth / srcHeight;
        float rMax = (float) maxWidth / maxHeight;

        int targetWidth;
        int targetHeight;

        // crop and scale
        if (rSrc < rMax) {
            targetWidth = maxWidth;
            targetHeight = targetWidth * srcHeight / srcWidth;
        } else {
            targetHeight = maxHeight;
            targetWidth = targetHeight * srcWidth / srcHeight;
            // width must be the factor of 16, find closest but smallest factor
            if (targetWidth % 16 != 0) {
                targetWidth = (targetWidth - 15) >> 4 << 4;
                targetHeight = targetWidth * srcHeight / srcWidth;
            }
        }

        return new Rect(0, 0, targetWidth, targetHeight);
    }

    protected int generate(MediaData item, int videoType, final String targetFilePath) {
        synchronized (LOCK_DEBUG_TRCK) {
            MtkLog.d(TAG, "<generate>" + sDbgTranscodingProcessTracking + sDbgLastTranscodingStartTime);
        }
        int res;
        synchronized (VideoToVideoGenerator.class) {
            res = innerGenerate(item, videoType, targetFilePath);
        }
        return res;
    }

    protected int innerGenerate(MediaData item, int videoType, final String targetFilePath) {

        // the width and height stored in MediaStore may be reversed
        // here we use MediaMetadataRetriever instead to get the real width and height
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        int videoWidth;
        int videoHeight;
        try {
            MtkLog.v(TAG, "doTranscode: set retriever.setDataSource begin <"
                    + item.filePath + ">");
            retriever.setDataSource(item.filePath);
            MtkLog.v(TAG, "doTranscode: set retriever.setDataSource end");
            videoWidth = Integer
                    .parseInt(retriever
                            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
            videoHeight = Integer
                    .parseInt(retriever
                            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
        } catch (IllegalArgumentException e) {
            videoWidth = item.width;
            videoHeight = item.height;
        } catch (Exception e) {
            // native layer would throw runtime exception if setDatasource() fails
            // setDataSource() could fail because of simple matters such as "can not find a
            // corresponding parser", which is however said to be google default.
            // so it looks ok and reasonable to catch such runtime exception here
            e.printStackTrace();
            videoWidth = item.width;
            videoHeight = item.height;
        }
        retriever.release();
        retriever = null;
        Rect srcRect = new Rect(0, 0, videoWidth, videoHeight);
        Rect targetRect = getTargetRect(srcRect.width(), srcRect.height(), VideoConfig.ENCODE_WIDTH,
                                        VideoConfig.ENCODE_HEIGHT);
        MtkLog.v(TAG, "srcRect: " + srcRect + " targetRect: " + targetRect);
        // duration is not so accurate as gotten from meta retriever,
        // but it's already enough
        long duration = item.duration;
        long startTime = duration / 3;  // eh, magic number?
        long endTime = Math.min(duration, startTime + VideoConfig.MAX_THUMBNAIL_DURATION);
        startTime = Math.max(0, endTime - VideoConfig.MAX_THUMBNAIL_DURATION);

        long width = (long) targetRect.width();
        long height = (long) targetRect.height();

        MtkLog.v(TAG, "start transcoding: " + item.filePath + " to " + videoPath[videoType] + ", target width = " + width + ", target height = " + height);
        MtkLog.v(TAG, "starttime = " + startTime + ", endtime = " + endTime);

        return -1;
    }

    public void onCancelRequested(MediaData item, int videoType) {
    }
}