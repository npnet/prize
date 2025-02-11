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

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Message;

import com.android.gallery3d.common.BitmapUtils;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.data.UriImage;
import com.android.gallery3d.ui.BitmapScreenNail;
import com.android.gallery3d.ui.PhotoView;
import com.android.gallery3d.ui.ScreenNail;
import com.android.gallery3d.ui.SynchronizedHandler;
import com.android.gallery3d.ui.TileImageViewAdapter;
import com.android.gallery3d.util.Future;
import com.android.gallery3d.util.FutureListener;
import com.android.gallery3d.util.Log;
import com.android.gallery3d.util.ThreadPool;

import com.mediatek.gallery3d.adapter.FeatureHelper;
import com.mediatek.galleryframework.util.GalleryPluginUtils;

public class SinglePhotoDataAdapter extends TileImageViewAdapter
        implements PhotoPage.Model {

    private static final String TAG = "Gallery2/SinglePhotoDataAdapter";
    private static final int SIZE_BACKUP = 1024;
    private static final int MSG_UPDATE_IMAGE = 1;

    private MediaItem mItem;
    private boolean mHasFullImage;
    private Future<?> mTask;
    private Handler mHandler;

    private PhotoView mPhotoView;
    private ThreadPool mThreadPool;
    private int mLoadingState = LOADING_INIT;
    private BitmapScreenNail mBitmapScreenNail;

    public SinglePhotoDataAdapter(
            AbstractGalleryActivity activity, PhotoView view, MediaItem item) {
        mItem = Utils.checkNotNull(item);
        mHasFullImage = (item.getSupportedOperations() &
                MediaItem.SUPPORT_FULL_IMAGE) != 0;
        Log.i(TAG, "<SinglePhotoDataAdapter> hasFullImage " + mHasFullImage);
        mPhotoView = Utils.checkNotNull(view);
        mHandler = new SynchronizedHandler(activity.getGLRoot()) {
            @Override
            @SuppressWarnings("unchecked")
            public void handleMessage(Message message) {
                Utils.assertTrue(message.what == MSG_UPDATE_IMAGE);
                if (mHasFullImage) {
                    onDecodeLargeComplete((ImageBundle) message.obj);
                } else {
                    onDecodeThumbComplete((Future<Bitmap>) message.obj);
                }
            }
        };
        mThreadPool = activity.getThreadPool();
    }

    private static class ImageBundle {
        public final BitmapRegionDecoder decoder;
        public final Bitmap backupImage;

        public ImageBundle(BitmapRegionDecoder decoder, Bitmap backupImage) {
            this.decoder = decoder;
            this.backupImage = backupImage;
        }
    }

    private FutureListener<BitmapRegionDecoder> mLargeListener =
            new FutureListener<BitmapRegionDecoder>() {
        @Override
        public void onFutureDone(Future<BitmapRegionDecoder> future) {
            BitmapRegionDecoder decoder = future.get();
            /// M: [BUG.MODIFY] @{
            // if (decoder == null) return;
            // Some special images which support FULL_IMAGE cannot get
            // BitmapRegionDecoder successfully,
            // but can decode thumbnail, so we try to decode thumbnail again
            if (decoder == null) {
                Log.i(TAG,
                        "<mLargeListener.onFutureDone> get RegionDecoder fail, uri = "
                                + mItem.getContentUri()
                                + ", try to decode thumb");
                mHasFullImage = false;
                mTask = mThreadPool.submit(
                        mItem.requestImage(MediaItem.TYPE_THUMBNAIL),
                        mThumbListener);
                return;
            }
            /// @}
            int width = decoder.getWidth();
            int height = decoder.getHeight();

            /// M: [BUG.ADD] The large picture can not be decoded clearly.@{
            if ((mLoadingState == LOADING_FAIL)
                    && FeatureHelper.isJpegOutOfLimit(mItem.getMimeType(),
                            width, height)) {
                Log.d(TAG, String.format(
                        "out of limitation: %s [mime type: %s, width: %d, height: %d]", mItem
                                .getPath().toString(), mItem.getMimeType(), width, height));
                decoder.recycle();
                return;
            }
            /// @}

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = BitmapUtils.computeSampleSize(
                    (float) SIZE_BACKUP / Math.max(width, height));
            Bitmap bitmap = decoder.decodeRegion(new Rect(0, 0, width, height), options);
            /// M: [BUG.ADD] @{
            //Add background for alpha bitmap.
            bitmap =
                    com.mediatek.galleryframework.util.BitmapUtils
                            .replaceBackgroundColor(bitmap, true);
            /// @}
            mHandler.sendMessage(mHandler.obtainMessage(
                    MSG_UPDATE_IMAGE, new ImageBundle(decoder, bitmap)));
        }
    };

    private FutureListener<Bitmap> mThumbListener =
            new FutureListener<Bitmap>() {
        @Override
        public void onFutureDone(Future<Bitmap> future) {
            mHandler.sendMessage(
                    mHandler.obtainMessage(MSG_UPDATE_IMAGE, future));
        }
    };

    @Override
    public boolean isEmpty() {
        return false;
    }

    private void setScreenNail(Bitmap bitmap, int width, int height) {
        /// M: [FEATURE.MODIFY] plugin @{
        // mBitmapScreenNail = new BitmapScreenNail(bitmap);
        mBitmapScreenNail = new BitmapScreenNail(bitmap, mItem);
        /// @}
        setScreenNail(mBitmapScreenNail, width, height);
        /// M: [FEATURE.ADD] plugin @{
        // update ImageWidth&height in mTileProvider
        GalleryPluginUtils.getImageOptionsPlugin()
                .updateTileProviderWithScreenNail(this, mBitmapScreenNail);
        /// @}
    }

    private void onDecodeLargeComplete(ImageBundle bundle) {
        try {
            setScreenNail(bundle.backupImage,
                    bundle.decoder.getWidth(), bundle.decoder.getHeight());
            setRegionDecoder(bundle.decoder);
            mPhotoView.notifyImageChange(0);
            /// M: [FEATURE.ADD] plugin updateFullPicture after notifyImageChange @{
            mPhotoView.updateFullPicture(mItem);
            /// @}
        } catch (Throwable t) {
            Log.w(TAG, "fail to decode large", t);
        }
    }

    private void onDecodeThumbComplete(Future<Bitmap> future) {
        try {
            Bitmap backup = future.get();
            if (backup == null) {
                mLoadingState = LOADING_FAIL;
                /// M: [BUG.ADD] refresh for Fail text if loading fail. @{
                mPhotoView.notifyImageChange(0);
                /// @}
                /// M: [FEATURE.ADD] plugin updateFullPicture after notifyImageChange @{
                mPhotoView.updateFullPicture(mItem);
                /// @}
                return;
            } else {
                mLoadingState = LOADING_COMPLETE;
            }
            setScreenNail(backup, backup.getWidth(), backup.getHeight());
            mPhotoView.notifyImageChange(0);
            /// M: [FEATURE.ADD] plugin updateFullPicture after notifyImageChange @{
            mPhotoView.updateFullPicture(mItem);
            /// @}
        } catch (Throwable t) {
            Log.w(TAG, "fail to decode thumb", t);
        }
    }

    @Override
    public void resume() {
        /// M: [BUG.ADD] Init center box mediaType as soon as possible, for
        // avoid error draw size @{
        if (mItem != null && mItem instanceof UriImage) {
            mPhotoView.updateFullPicture(mItem);
        }
        /// @}
        if (mTask == null) {
            if (mHasFullImage) {
                mTask = mThreadPool.submit(
                        mItem.requestLargeImage(), mLargeListener);
            } else {
                mTask = mThreadPool.submit(
                        mItem.requestImage(MediaItem.TYPE_THUMBNAIL),
                        mThumbListener);
            }
        }
    }

    @Override
    public void pause() {
        Future<?> task = mTask;
        task.cancel();
        task.waitDone();
        /// M: [BUG.MODIFY] reset task as null, Renew task on onResume, @{
        /*
         * if (task.get() == null) {
            mTask = null;
        }*/
        mTask = null;
        /// @}
        if (mBitmapScreenNail != null) {
            mBitmapScreenNail.recycle();
            mBitmapScreenNail = null;
        }
        /// M: [BUG.ADD] reset mLoadingState flag when pause @{
        mLoadingState = LOADING_INIT;
        /// @}
        /// M: [BUG.ADD] @{
        // Clear the information of TileImageViewAdapter to release RegionDecoder as soon as
        // possible.
        clearAndRecycle();
        /// @}
    }

    @Override
    public void moveTo(int index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void getImageSize(int offset, PhotoView.Size size) {
        if (offset == 0) {
            size.width = mItem.getWidth();
            size.height = mItem.getHeight();
        } else {
            size.width = 0;
            size.height = 0;
        }
    }

    @Override
    public int getImageRotation(int offset) {
        return (offset == 0) ? mItem.getFullImageRotation() : 0;
    }

    @Override
    public ScreenNail getScreenNail(int offset) {
        return (offset == 0) ? getScreenNail() : null;
    }

    @Override
    public void setNeedFullImage(boolean enabled) {
        // currently not necessary.
    }

    @Override
    public boolean isCamera(int offset) {
        return false;
    }

    @Override
    public boolean isPanorama(int offset) {
        return false;
    }

    @Override
    public boolean isStaticCamera(int offset) {
        return false;
    }

    @Override
    public boolean isVideo(int offset) {
        return mItem.getMediaType() == MediaItem.MEDIA_TYPE_VIDEO;
    }

    @Override
    public boolean isDeletable(int offset) {
        /// M: [BUG.MODIFY] @{
        // Item in SinglePhotoDataAdapter should always be undeletable,
        // since it does not have a containing set, and might cause JE
        // when deleting it in film mode.
        /* return (mItem.getSupportedOperations() & MediaItem.SUPPORT_DELETE) != 0; */
        return false;
        /// @}
     }

    @Override
    public MediaItem getMediaItem(int offset) {
        return offset == 0 ? mItem : null;
    }

    @Override
    public int getCurrentIndex() {
        return 0;
    }

    @Override
    public int getTotalCount() {
        return 0;
    }

    @Override
    public void setCurrentPhoto(Path path, int indexHint) {
        // ignore
    }

    @Override
    public void setFocusHintDirection(int direction) {
        // ignore
    }

    @Override
    public void setFocusHintPath(Path path) {
        // ignore
    }

    @Override
    public int getLoadingState(int offset) {
        return mLoadingState;
    }

    //********************************************************************
    //*                              MTK                                 *
    //********************************************************************
    @Override
    public int getImageHeight() {
        return mItem.getHeight();
    }

    @Override
    public int getImageWidth() {
        return mItem.getWidth();
    }
}
