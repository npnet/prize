/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.gallery3d.filtershow.crop;

import android.Manifest;
import android.app.ActionBar;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.provider.MediaStore.Images;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import com.android.gallery3d.R;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.filtershow.cache.ImageLoader;
import com.android.gallery3d.exif.ExifInterface;
import com.android.gallery3d.filtershow.tools.SaveImage;
import com.android.gallery3d.util.GalleryUtils;
import com.mediatek.gallery3d.util.DecodeSpecLimitor;
import com.mediatek.gallery3d.util.PermissionHelper;
import com.mediatek.galleryfeature.config.FeatureConfig;
import com.mediatek.galleryframework.util.BitmapUtils;
import com.prize.util.GloblePrizeUtil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.TimeZone;



/**
 * Activity for cropping an image.
 */
public class CropActivity extends Activity {
    private static final String LOGTAG = "Gallery2/CropActivity";
    public static final String CROP_ACTION = "com.android.camera.action.CROP";
    private CropExtras mCropExtras = null;
    private LoadBitmapTask mLoadBitmapTask = null;

    private int mOutputX = 0;
    private int mOutputY = 0;
    private Bitmap mOriginalBitmap = null;
    private RectF mOriginalBounds = null;
    private int mOriginalRotation = 0;
    private Uri mSourceUri = null;
    private CropView mCropView = null;
    private View mSaveButton = null;
    private View mCancelTv;
    private boolean finalIOGuard = false;
    /// M: [BUG.ADD] The "My Picture" will become small when you select it from Gallery.@{
    public static final String KEY_SCALE_UP_IF_NEEDED = "scaleUpIfNeeded";
    /// @}
    /// M: [BUG.ADD] added for support high resolution.@{
    private static final long LIMIT_SUPPORTS_HIGHRES = 134217728; // 128Mb
    /// @}
    private static final int SELECT_PICTURE = 1; // request code for picker

    private static final int DEFAULT_COMPRESS_QUALITY = 90;
    /**
     * The maximum bitmap size we allow to be returned through the intent.
     * Intents have a maximum of 1MB in total size. However, the Bitmap seems to
     * have some overhead to hit so that we go way below the limit here to make
     * sure the intent stays below 1MB.We should consider just returning a byte
     * array instead of a Bitmap instance to avoid overhead.
     */
    /// M: [BUG.MODIFY] Intents have a maximum of 1MB in total size,
    // 750000 is too large for bitmap. so resize to 300000. @{
    /* public static final int MAX_BMAP_IN_INTENT = 750000;*/
    public static final int MAX_BMAP_IN_INTENT = 30000;
    /// @}
    // Flags
    private static final int DO_SET_WALLPAPER = 1;
    private static final int DO_RETURN_DATA = 1 << 1;
    private static final int DO_EXTRA_OUTPUT = 1 << 2;

    private static final int FLAG_CHECK = DO_SET_WALLPAPER | DO_RETURN_DATA | DO_EXTRA_OUTPUT;

    private boolean mHasNavigationBar;

    private int mNavigationBarHeight;

    private Handler mNavigationBarHandler = new Handler(){
        public void dispatchMessage(Message msg) {
            switch (msg.what) {
                case 0:
                    updateActionBar();
                    break;
                default:
                    break;
            }
        };
    };

    private ContentObserver mContentObserver = new ContentObserver(null) {

        @Override
        public void onChange(boolean selfChange) {
            // TODO Auto-generated method stub
            mNavigationBarHandler.sendEmptyMessage(0);
            super.onChange(selfChange);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            // TODO Auto-generated method stub
            super.onChange(selfChange, uri);
        }
    };

    private void updateActionBar() {
        if (mSaveButton != null) {
            int padding = GalleryUtils.dpToPixel(10);
            if (mHasNavigationBar && GloblePrizeUtil.isShowNavigationBar(this)
                    && this.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE){
                mSaveButton.setPadding(padding, padding, padding + mNavigationBarHeight, padding);
            } else {
                mSaveButton.setPadding(padding, padding, padding, padding);
            }
        }
    }

    private void registerNavigationBarListener() {
        if(mContentObserver!=null && mHasNavigationBar) {
            this.getContentResolver().registerContentObserver(Settings.System.getUriFor(GloblePrizeUtil.PRIZE_NAVBAR_STATE), true, mContentObserver);
        }
    }

    private void unregisterNavigationBarListener() {
        if(mContentObserver!=null && mHasNavigationBar) {
            this.getContentResolver().unregisterContentObserver(mContentObserver);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        Intent intent = getIntent();
        setResult(RESULT_CANCELED, new Intent());
        mCropExtras = getExtrasFromIntent(intent);

        if (mCropExtras != null && mCropExtras.getShowWhenLocked()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
          
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);  
		/* PRIZE-bug 12608 Cut screen picture when the drop-down box at the top of the cutting edge will pull down the status bar-wanzhijuan-2016-3-4-start*/
        int flag = View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        getWindow().getDecorView().setSystemUiVisibility(flag);
		/* PRIZE-bug 12608 Cut screen picture when the drop-down box at the top of the cutting edge will pull down the status bar-wanzhijuan-2016-3-4-end*/
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);  
        
        setContentView(R.layout.crop_activity);

        mHasNavigationBar = GloblePrizeUtil.checkContianNavigationBar(this);
        if (mHasNavigationBar) {
            mNavigationBarHeight = GloblePrizeUtil.getNavigationBarHeight(this);
        }
        mCropView = (CropView) findViewById(R.id.cropView);
		/* PRIZE-bug 12608 Cut screen picture when the drop-down box at the top of the cutting edge will pull down the status bar-wanzhijuan-2016-3-4-start*/
        mSaveButton = findViewById(R.id.tv_done);
        mCancelTv = findViewById(R.id.tv_cancel);
        mCancelTv.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				onBackPressed();
			}
		});
        mSaveButton.setEnabled(false);
        /// @}
        mSaveButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                startFinishOutput();
            }
        });
		
		
		/*ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM);
            actionBar.setCustomView(R.layout.filtershow_actionbar);

            /// M: [BUG.MODIFY]set the savebutton disable till load image finish. @{
            mSaveButton = actionBar.getCustomView();
            mSaveButton.setEnabled(false);
            /// @}
            mSaveButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    startFinishOutput();
                }
            });
        }*/
        boolean granted = PermissionHelper.checkAndRequestForGallery(this);
        if (granted) {
            if (intent.getData() != null) {
                mSourceUri = intent.getData();
                startLoadBitmap(mSourceUri);
            } else {
                pickImage();
            }
        } else {
            View loading = findViewById(R.id.loading);
            loading.setVisibility(View.INVISIBLE);
        }
        /// @}
    }

    private void enableSave(boolean enable) {
        if (mSaveButton != null) {
            mSaveButton.setEnabled(enable);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerNavigationBarListener();
        updateActionBar();
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterNavigationBarListener();
    }

    @Override
    protected void onDestroy() {
        if (mLoadBitmapTask != null) {
            mLoadBitmapTask.cancel(false);
        }
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged (Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mCropView = (CropView) findViewById(R.id.cropView);
        mCropView.configChanged();
      
		/* PRIZE-bug 12608 Cut screen picture when the drop-down box at the top of the cutting edge will pull down the status bar-wanzhijuan-2016-3-4-start*/
        mSaveButton = findViewById(R.id.tv_done);
//        mSaveButton.setEnabled(false);
        /// @}
        mSaveButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                startFinishOutput();
            }
        });
        
        mCancelTv = findViewById(R.id.tv_cancel);
        mCancelTv.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				onBackPressed();
			}
		});
        updateActionBar();
        
        /// M: [BUG.ADD] @{
                //update resource for text string after configuration changed @{
        /*ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM);
            actionBar.setCustomView(R.layout.filtershow_actionbar);

            mSaveButton = actionBar.getCustomView();
            mSaveButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    startFinishOutput();
                }
            });
        }*/
        /// @}

    }

    /**
     * Opens a selector in Gallery to chose an image for use when none was given
     * in the CROP intent.
     */
    private void pickImage() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, getString(R.string.select_image)),
                SELECT_PICTURE);
    }

    /**
     * Callback for pickImage().
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK && requestCode == SELECT_PICTURE) {
            mSourceUri = data.getData();
            startLoadBitmap(mSourceUri);
        }
    }

    /**
     * Gets screen size metric.
     */
    private int getScreenImageSize() {
        DisplayMetrics outMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(outMetrics);
        return (int) Math.max(outMetrics.heightPixels, outMetrics.widthPixels);
    }

    /**
     * Method that loads a bitmap in an async task.
     */
    private void startLoadBitmap(Uri uri) {
        /// M: [BUG.MODIFY] @{
        /* if (uri != null) {*/
        boolean outOfDecodeSpec = DecodeSpecLimitor.isOutOfSpecLimit(getApplicationContext(), uri);
        if (!outOfDecodeSpec && uri != null) {
        /// @}
            enableSave(false);
            final View loading = findViewById(R.id.loading);
            loading.setVisibility(View.VISIBLE);
            mLoadBitmapTask = new LoadBitmapTask();
            mLoadBitmapTask.execute(uri);
        } else {
            cannotLoadImage();
            done();
        }
    }

    /**
     * Method called on UI thread with loaded bitmap.
     */
    private void doneLoadBitmap(Bitmap bitmap, RectF bounds, int orientation) {
        final View loading = findViewById(R.id.loading);
        loading.setVisibility(View.GONE);
        /// M: [BUG.ADD] set the savebutton disable till load image finish.@{
        mSaveButton.setEnabled(true);
        /// @}
        mOriginalBitmap = bitmap;
        mOriginalBounds = bounds;
        mOriginalRotation = orientation;
        if (bitmap != null && bitmap.getWidth() != 0 && bitmap.getHeight() != 0) {
            RectF imgBounds = new RectF(0, 0, bitmap.getWidth(), bitmap.getHeight());
            mCropView.initialize(bitmap, imgBounds, imgBounds, orientation);
            if (mCropExtras != null) {
                int aspectX = mCropExtras.getAspectX();
                int aspectY = mCropExtras.getAspectY();
                mOutputX = mCropExtras.getOutputX();
                mOutputY = mCropExtras.getOutputY();
                if (mOutputX > 0 && mOutputY > 0) {
                    mCropView.applyAspect(mOutputX, mOutputY);

                }
                float spotX = mCropExtras.getSpotlightX();
                float spotY = mCropExtras.getSpotlightY();
                if (spotX > 0 && spotY > 0) {
                    mCropView.setWallpaperSpotlight(spotX, spotY);
                }
                if (aspectX > 0 && aspectY > 0) {
                    mCropView.applyAspect(aspectX, aspectY);
                }
            }
            enableSave(true);
        } else {
            Log.w(LOGTAG, "could not load image for cropping");
            cannotLoadImage();
            setResult(RESULT_CANCELED, new Intent());
            done();
        }
    }

    /**
     * Display toast for image loading failure.
     */
    private void cannotLoadImage() {
        CharSequence text = getString(R.string.cannot_load_image);
        Toast toast = Toast.makeText(this, text, Toast.LENGTH_SHORT);
        toast.show();
    }

    /**
     * AsyncTask for loading a bitmap into memory.
     *
     * @see #startLoadBitmap(Uri)
     * @see #doneLoadBitmap(Bitmap)
     */
    private class LoadBitmapTask extends AsyncTask<Uri, Void, Bitmap> {
        int mBitmapSize;
        Context mContext;
        Rect mOriginalBounds;
        int mOrientation;

        public LoadBitmapTask() {
            mBitmapSize = getScreenImageSize();
            mContext = getApplicationContext();
            mOriginalBounds = new Rect();
            mOrientation = 0;
        }

        @Override
        protected Bitmap doInBackground(Uri... params) {
            Uri uri = params[0];

            /// M: [BUG.ADD] added for support hight resolution@{
            if (Runtime.getRuntime().maxMemory() < LIMIT_SUPPORTS_HIGHRES) {
                mBitmapSize = mBitmapSize / 2;
                }
            /// @}

            Bitmap bmap = ImageLoader.loadConstrainedBitmap(uri, mContext, mBitmapSize,
                    mOriginalBounds, false);
            mOrientation = ImageLoader.getMetadataRotation(mContext, uri);
            return bmap;
        }

        @Override
        protected void onPostExecute(Bitmap result) {
            doneLoadBitmap(result, new RectF(mOriginalBounds), mOrientation);
        }
    }

    protected void startFinishOutput() {
        if (finalIOGuard) {
            return;
        } else {
            finalIOGuard = true;
        }
        enableSave(false);
        /// M: [BUG.ADD] @{
        // don't change crop region after click save
        mCropView.enableTouchMotion(false);
        /// @}

        Uri destinationUri = null;
        int flags = 0;
        if (mOriginalBitmap != null && mCropExtras != null) {
            if (mCropExtras.getExtraOutput() != null) {
                destinationUri = mCropExtras.getExtraOutput();
                if (destinationUri != null) {
                    flags |= DO_EXTRA_OUTPUT;
                }
            }
            if (mCropExtras.getSetAsWallpaper()) {
                flags |= DO_SET_WALLPAPER;
            }
            if (mCropExtras.getReturnData()) {
                flags |= DO_RETURN_DATA;
            }
        }
       
        if (flags == 0) {
        	/** Photos after editing, save the original cut after disappear update by liudong 2015-08-26*/
            destinationUri = SaveImage.makeSaveInsertUri(this, mSourceUri);
//            destinationUri = SaveImage.makeAndInsertUri(this, mSourceUri);
            Log.d(LOGTAG, "destinationUri="+destinationUri+"   mSourceUri="+mSourceUri);
            if (destinationUri != null) {
                flags |= DO_EXTRA_OUTPUT;
            }
        }
        if ((flags & FLAG_CHECK) != 0 && mOriginalBitmap != null) {
            RectF photo = new RectF(0, 0, mOriginalBitmap.getWidth(), mOriginalBitmap.getHeight());
            RectF crop = getBitmapCrop(photo);
            startBitmapIO(flags, mOriginalBitmap, mSourceUri, destinationUri, crop,
                    photo, mOriginalBounds,
                    (mCropExtras == null) ? null : mCropExtras.getOutputFormat(), mOriginalRotation);
            return;
        }
        setResult(RESULT_CANCELED, new Intent());
        done();
        return;
    }

    private void startBitmapIO(int flags, Bitmap currentBitmap, Uri sourceUri, Uri destUri,
            RectF cropBounds, RectF photoBounds, RectF currentBitmapBounds, String format,
            int rotation) {
        if (cropBounds == null || photoBounds == null || currentBitmap == null
                || currentBitmap.getWidth() == 0 || currentBitmap.getHeight() == 0
                || cropBounds.width() == 0 || cropBounds.height() == 0 || photoBounds.width() == 0
                || photoBounds.height() == 0) {
            return; // fail fast
        }
        if ((flags & FLAG_CHECK) == 0) {
            return; // no output options
        }
        if ((flags & DO_SET_WALLPAPER) != 0) {
            Toast.makeText(this, R.string.setting_wallpaper, Toast.LENGTH_LONG).show();
        }

        final View loading = findViewById(R.id.loading);
        loading.setVisibility(View.VISIBLE);
        BitmapIOTask ioTask = new BitmapIOTask(sourceUri, destUri, format, flags, cropBounds,
                photoBounds, currentBitmapBounds, rotation, mOutputX, mOutputY);
        ioTask.execute(currentBitmap);
    }

    private void doneBitmapIO(boolean success, Intent intent) {
        final View loading = findViewById(R.id.loading);
        loading.setVisibility(View.GONE);
        if (success) {
            setResult(RESULT_OK, intent);
        } else {
            setResult(RESULT_CANCELED, intent);
        }
        done();
    }

    private class BitmapIOTask extends AsyncTask<Bitmap, Void, Boolean> {

        private final WallpaperManager mWPManager;
        InputStream mInStream = null;
        OutputStream mOutStream = null;
        String mOutputFormat = null;
        Uri mOutUri = null;
        Uri mInUri = null;
        int mFlags = 0;
        RectF mCrop = null;
        RectF mPhoto = null;
        RectF mOrig = null;
        Intent mResultIntent = null;
        int mRotation = 0;
        /// M: [BUG.ADD] @{
        File mFile;
        /// @}
        // Helper to setup input stream
        private void regenerateInputStream() {
            if (mInUri == null) {
                Log.w(LOGTAG, "cannot read original file, no input URI given");
            } else {
                Utils.closeSilently(mInStream);
                try {
                    mInStream = getContentResolver().openInputStream(mInUri);
                } catch (FileNotFoundException e) {
                    Log.w(LOGTAG, "cannot read file: " + mInUri.toString(), e);
                }
            }
        }

        public BitmapIOTask(Uri sourceUri, Uri destUri, String outputFormat, int flags,
                RectF cropBounds, RectF photoBounds, RectF originalBitmapBounds, int rotation,
                int outputX, int outputY) {
            mOutputFormat = outputFormat;
            mOutStream = null;
            mOutUri = destUri;
            mInUri = sourceUri;
            mFlags = flags;
            mCrop = cropBounds;
            mPhoto = photoBounds;
            mOrig = originalBitmapBounds;
            mWPManager = WallpaperManager.getInstance(getApplicationContext());
            mResultIntent = new Intent();
            mRotation = (rotation < 0) ? -rotation : rotation;
            mRotation %= 360;
            mRotation = 90 * (int) (mRotation / 90);  // now mRotation is a multiple of 90
            mOutputX = outputX;
            mOutputY = outputY;
            /// M: [BUG.MARK] Get mFile and mOutStream after decode bitmap.
            // In case of mOutUri equal mInUri. Get mFile and mOutStream
            // before decode bitmap, should cause decode bitmap fail. @{
            /*
            if ((flags & DO_EXTRA_OUTPUT) != 0) {
                if (mOutUri == null) {
                    Log.w(LOGTAG, "cannot write file, no output URI given");
                } else {
                    try {
                        mOutStream = getContentResolver().openOutputStream(
                                mOutUri);
                    } catch (FileNotFoundException e) {
                        Log.w(LOGTAG, "cannot write file: "
                                + mOutUri.toString(), e);
                    }
                }
            }*/
            /// @}

            if ((flags & (DO_EXTRA_OUTPUT | DO_SET_WALLPAPER)) != 0) {
                regenerateInputStream();
            }
        }

        @Override
        protected Boolean doInBackground(Bitmap... params) {
            boolean failure = false;
            Bitmap img = params[0];

            // Set extra for crop bounds
            if (mCrop != null && mPhoto != null && mOrig != null) {
                RectF trueCrop = CropMath.getScaledCropBounds(mCrop, mPhoto, mOrig);
                Matrix m = new Matrix();
                m.setRotate(mRotation);
                m.mapRect(trueCrop);
                if (trueCrop != null) {
                    Rect rounded = new Rect();
                    trueCrop.roundOut(rounded);
                    mResultIntent.putExtra(CropExtras.KEY_CROPPED_RECT, rounded);
                }
            }

            // Find the small cropped bitmap that is returned in the intent
            /// M: [BUG.MODIFY] @{
            // RETURN_DATA and RETURN_DATA_COMPRESS is almost same flow
            /* if ((mFlags & DO_RETURN_DATA) != 0) {*/
            if ((mFlags & DO_RETURN_DATA) != 0 ||
                    (mCropExtras != null && mCropExtras.getReturnDataCompressed())) {
            /// @}
                assert (img != null);
                Bitmap ret = getCroppedImage(img, mCrop, mPhoto);
                /// M: [BUG.MODIFY] @{
                // If it need scale up, no need to down size to MAX_BMAP_IN_INTENT
                /* if (ret != null) {*/
                if (ret != null &&
                        !(mCropExtras != null && mCropExtras.getScaleUp())) {
                /// @}
                    ret = getDownsampledBitmap(ret, MAX_BMAP_IN_INTENT);
                }
                if (ret == null) {
                    Log.w(LOGTAG, "could not downsample bitmap to return in data");
                    failure = true;
                } else {
                    if (mRotation > 0) {
                        Matrix m = new Matrix();
                        m.setRotate(mRotation);
                        Bitmap tmp = Bitmap.createBitmap(ret, 0, 0, ret.getWidth(),
                                ret.getHeight(), m, true);
                        if (tmp != null) {
                            ret = tmp;
                        }
                    }

                    /// M: [BUG.MODIFY] @{
                    /* mResultIntent.putExtra(CropExtras.KEY_DATA, ret); */
                    // Scale bitmap if needed
                    Bitmap outputBmp = null;
                    Rect rect = new Rect(0, 0, ret.getWidth(), ret.getHeight());
                    if (mCropExtras != null && (mCropExtras.getScaleUp()
                            || (mOutputX > 0 && mOutputY > 0))) {
                        outputBmp = Bitmap.createBitmap(mOutputX, mOutputY, Config.ARGB_8888);
                        Canvas c = new Canvas(outputBmp);
                        c.drawBitmap(ret, rect, new Rect(0, 0, mOutputX, mOutputY), null);
                    } else {
                        outputBmp = ret;
                    }
                    // Return compressed data if needed, or else return bitmap directly
                    if (mCropExtras != null && mCropExtras.getReturnDataCompressed()) {
                        byte[] dataCompressed = BitmapUtils.compressToBytes(outputBmp);
                        outputBmp.recycle();
                        mResultIntent.putExtra(CropExtras.KEY_DATA_COMPRESS, dataCompressed);
                    } else {
                        mResultIntent.putExtra(CropExtras.KEY_DATA, outputBmp);
                    }
                    /// @}

                    /// M: [BUG.ADD] add source URI for Photo widget build fail.@{
                    mResultIntent.setData(mInUri);
                    /// @}
                }
            }

            // Do the large cropped bitmap and/or set the wallpaper
            if ((mFlags & (DO_EXTRA_OUTPUT | DO_SET_WALLPAPER)) != 0 && mInStream != null) {
                // Find crop bounds (scaled to original image size)
                RectF trueCrop = CropMath.getScaledCropBounds(mCrop, mPhoto, mOrig);
                if (trueCrop == null) {
                    Log.w(LOGTAG, "cannot find crop for full size image");
                    failure = true;
                    return false;
                }
                Rect roundedTrueCrop = new Rect();
                trueCrop.roundOut(roundedTrueCrop);

                if (roundedTrueCrop.width() <= 0 || roundedTrueCrop.height() <= 0) {
                    Log.w(LOGTAG, "crop has bad values for full size image");
                    failure = true;
                    return false;
                }

                // Attempt to open a region decoder
                BitmapRegionDecoder decoder = null;
                try {
                    decoder = BitmapRegionDecoder.newInstance(mInStream, true);
                } catch (IOException e) {
                    Log.w(LOGTAG, "cannot open region decoder for file: " + mInUri.toString(), e);
                }

                Bitmap crop = null;
                if (decoder != null) {
                    // Do region decoding to get crop bitmap
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inMutable = true;
                    /// M: [BUG.ADD] increase sample size to avoid OOM for extremely large image @{
                    int sampleSize = (int) Math.ceil((double) roundedTrueCrop.width()
                            * (double) roundedTrueCrop.height()
                            / (double) CROP_PHOTO_SIZE_LIMIT);
                    if (sampleSize < 1) {
                        sampleSize = 1;
                    }
                    Log.i(LOGTAG,
                            "<BitmapIOTask.doInBackground> sIsLowRamDevice "
                                    + FeatureConfig.sIsLowRamDevice
                                    + ", decode sample size " + sampleSize);
                    options.inSampleSize = sampleSize;
                    /// @}
                    crop = decoder.decodeRegion(roundedTrueCrop, options);
                    decoder.recycle();
                }
                if (crop == null) {
                    // BitmapRegionDecoder has failed, try to crop in-memory
                    regenerateInputStream();
                    Bitmap fullSize = null;
                    if (mInStream != null) {
                        fullSize = BitmapFactory.decodeStream(mInStream);
                    }
                    if (fullSize != null) {
                        crop = Bitmap.createBitmap(fullSize, roundedTrueCrop.left,
                                roundedTrueCrop.top, roundedTrueCrop.width(),
                                roundedTrueCrop.height());
                    }
                }
                /// M: Add background color for alpha bitmap. @{
                crop = BitmapUtils.replaceBackgroundColor(crop, true);
                /// @}
                if (crop == null) {
                    Log.w(LOGTAG, "cannot decode file: " + mInUri.toString());
                    failure = true;
                    return false;
                }
                if (mOutputX > 0 && mOutputY > 0) {
                    Matrix m = new Matrix();
                    RectF cropRect = new RectF(0, 0, crop.getWidth(), crop.getHeight());
                    if (mRotation > 0) {
                        m.setRotate(mRotation);
                        m.mapRect(cropRect);
                    }
                    RectF returnRect = new RectF(0, 0, mOutputX, mOutputY);
                    m.setRectToRect(cropRect, returnRect, Matrix.ScaleToFit.FILL);
                    m.preRotate(mRotation);
                    Bitmap tmp = Bitmap.createBitmap((int) returnRect.width(),
                            (int) returnRect.height(), Bitmap.Config.ARGB_8888);
                    if (tmp != null) {
                        Canvas c = new Canvas(tmp);
                        c.drawBitmap(crop, m, new Paint());
                        crop = tmp;
                    }
                } else if (mRotation > 0) {
                    Matrix m = new Matrix();
                    m.setRotate(mRotation);
                    Bitmap tmp = Bitmap.createBitmap(crop, 0, 0, crop.getWidth(),
                            crop.getHeight(), m, true);
                    if (tmp != null) {
                        crop = tmp;
                    }
                }
                /// M: [BUG.MARK] Get mFile and mOutStream after decode bitmap.
                // In case of mOutUri equal mInUri. Get mFile and mOutStream
                // before decode bitmap, should cause decode bitmap fail. @{
                if ((mFlags & DO_EXTRA_OUTPUT) != 0) {
                    mFile = getFile(mOutUri);
                    if (mFile != null) {
                        mOutStream = getOutputStream(mFile);
                    }
                    if (mOutStream == null) {
                        mOutStream = getOutputStream(mOutUri);
                    }
                }
                /// @}
                // Get output compression format
                CompressFormat cf =
                        convertExtensionToCompressFormat(getFileExtension(mOutputFormat));
                /// M: [BUG.ADD] @{
                // insert orientation and time to JPE head @{
                if (mOutputFormat == null || mOutputFormat.equalsIgnoreCase("jpg")) {
                    mOutStream = getExifData(mOutStream);
                }
                /// @}

                // If we only need to output to a URI, compress straight to file
                if (mFlags == DO_EXTRA_OUTPUT) {
                    if (mOutStream == null
                            || !crop.compress(cf, DEFAULT_COMPRESS_QUALITY, mOutStream)) {
                        Log.w(LOGTAG, "failed to compress bitmap to file: " + mOutUri.toString());
                        failure = true;
                    } else {
                        /// M: [BUG.ADD] @{
                        int width = crop.getWidth();
                        int height = crop.getHeight();
                        SaveImage.updataImageDimensionInDB(getApplicationContext(),
                                mFile , width, height);
                        /// @}
                        mResultIntent.setData(mOutUri);
                    }
                } else {
                    // Compress to byte array
                    ByteArrayOutputStream tmpOut = new ByteArrayOutputStream(2048);
                    if (crop.compress(cf, DEFAULT_COMPRESS_QUALITY, tmpOut)) {

                        // If we need to output to a Uri, write compressed
                        // bitmap out
                        if ((mFlags & DO_EXTRA_OUTPUT) != 0) {
                            if (mOutStream == null) {
                                Log.w(LOGTAG, "failed to compress bitmap to file: " +
                                        mOutUri.toString());
                                failure = true;
                            } else {
                                try {
                                    mOutStream.write(tmpOut.toByteArray());
                                    int width = crop.getWidth();
                                    int height = crop.getHeight();
                                    SaveImage.updataImageDimensionInDB(getApplicationContext(),
                                            mFile , width, height);
                                    mResultIntent.setData(mOutUri);
                                } catch (IOException e) {
                                    Log.w(LOGTAG,
                                            "failed to compress bitmap to file: "
                                                    + mOutUri.toString(), e);
                                    failure = true;
                                }
                            }
                        }

                        // If we need to set to the wallpaper, set it
                        if ((mFlags & DO_SET_WALLPAPER) != 0 && mWPManager != null) {
                            if (mWPManager == null) {
                                Log.w(LOGTAG, "no wallpaper manager");
                                failure = true;
                            } else {
                                try {
                                    mWPManager.setStream(new ByteArrayInputStream(tmpOut
                                            .toByteArray()));
                                } catch (IOException e) {
                                    Log.w(LOGTAG, "cannot write stream to wallpaper", e);
                                    failure = true;
                                }
                            }
                        }
                    } else {
                        Log.w(LOGTAG, "cannot compress bitmap");
                        failure = true;
                    }
                }
            }
            return !failure; // True if any of the operations failed
        }

        @Override
        protected void onPostExecute(Boolean result) {
            Utils.closeSilently(mOutStream);
            Utils.closeSilently(mInStream);
            doneBitmapIO(result.booleanValue(), mResultIntent);
        }

    }

    private void done() {
        finish();
    }

    protected static Bitmap getCroppedImage(Bitmap image, RectF cropBounds, RectF photoBounds) {
        RectF imageBounds = new RectF(0, 0, image.getWidth(), image.getHeight());
        RectF crop = CropMath.getScaledCropBounds(cropBounds, photoBounds, imageBounds);
        if (crop == null) {
            return null;
        }
        Rect intCrop = new Rect();
        crop.roundOut(intCrop);
        return Bitmap.createBitmap(image, intCrop.left, intCrop.top, intCrop.width(),
                intCrop.height());
    }

    protected static Bitmap getDownsampledBitmap(Bitmap image, int max_size) {
        if (image == null || image.getWidth() == 0 || image.getHeight() == 0 || max_size < 16) {
            throw new IllegalArgumentException("Bad argument to getDownsampledBitmap()");
        }
        int shifts = 0;
        int size = CropMath.getBitmapSize(image);
        while (size > max_size) {
            shifts++;
            size /= 4;
        }
        Bitmap ret = Bitmap.createScaledBitmap(image, image.getWidth() >> shifts,
                image.getHeight() >> shifts, true);
        if (ret == null) {
            return null;
        }
        // Handle edge case for rounding.
        if (CropMath.getBitmapSize(ret) > max_size) {
            return Bitmap.createScaledBitmap(ret, ret.getWidth() >> 1, ret.getHeight() >> 1, true);
        }
        return ret;
    }
    
    @Override
	public void onWindowFocusChanged(boolean hasFocus) {
		// TODO Auto-generated method stub
		if (hasFocus) {
			int flag = View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        	getWindow().getDecorView().setSystemUiVisibility(flag);
		}
		super.onWindowFocusChanged(hasFocus);
	}

    /**
     * Gets the crop extras from the intent, or null if none exist.
     */
    protected static CropExtras getExtrasFromIntent(Intent intent) {
        Bundle extras = intent.getExtras();
        if (extras != null) {
            return new CropExtras(extras.getInt(CropExtras.KEY_OUTPUT_X, 0),
                    extras.getInt(CropExtras.KEY_OUTPUT_Y, 0),
                    extras.getBoolean(CropExtras.KEY_SCALE, true) &&
                            extras.getBoolean(CropExtras.KEY_SCALE_UP_IF_NEEDED, false),
                    extras.getInt(CropExtras.KEY_ASPECT_X, 0),
                    extras.getInt(CropExtras.KEY_ASPECT_Y, 0),
                    extras.getBoolean(CropExtras.KEY_SET_AS_WALLPAPER, false),
                    extras.getBoolean(CropExtras.KEY_RETURN_DATA, false),
                    (Uri) extras.getParcelable(MediaStore.EXTRA_OUTPUT),
                    extras.getString(CropExtras.KEY_OUTPUT_FORMAT),
                    extras.getBoolean(CropExtras.KEY_SHOW_WHEN_LOCKED, false),
                    extras.getFloat(CropExtras.KEY_SPOTLIGHT_X),
                    /// M: [BUG.MODIFY] @{
                    // Initialize if return compressed data
                    /* extras.getFloat(CropExtras.KEY_SPOTLIGHT_Y));*/
                    extras.getFloat(CropExtras.KEY_SPOTLIGHT_Y),
                    extras.getBoolean(CropExtras.KEY_RETURN_DATA_COMPRESS));
                    /// @}
        }
        return null;
    }

    protected static CompressFormat convertExtensionToCompressFormat(String extension) {
        return extension.equals("png") ? CompressFormat.PNG : CompressFormat.JPEG;
    }

    protected static String getFileExtension(String requestFormat) {
        String outputFormat = (requestFormat == null)
                ? "jpg"
                : requestFormat;
        outputFormat = outputFormat.toLowerCase();
        return (outputFormat.equals("png") || outputFormat.equals("gif"))
                ? "png" // We don't support gif compression.
                : "jpg";
    }

    private RectF getBitmapCrop(RectF imageBounds) {
        RectF crop = mCropView.getCrop();
        RectF photo = mCropView.getPhoto();
        if (crop == null || photo == null) {
            Log.w(LOGTAG, "could not get crop");
            return null;
        }
        RectF scaledCrop = CropMath.getScaledCropBounds(crop, photo, imageBounds);
        if (scaledCrop != null) {
            /// M: [BUG.ADD] @{
            // while cropped Image width or height is 0, should reset the width or height as 1. @{
            if (scaledCrop.height() == 0) {
                scaledCrop.inset(0, -1);
            }
            if (scaledCrop.width() == 0) {
                scaledCrop.inset(-1, 0);
            }

            /// @}
        }
        return scaledCrop;
    }

    // ********************************************************************
    // *                             MTK                                   *
    // ********************************************************************

    @Override
    public void onRequestPermissionsResult(int requestCode,
            String[] permissions, int[] grantResults) {
        if (PermissionHelper.isAllPermissionsGranted(permissions, grantResults)) {
            Log.i(LOGTAG, "<onRequestPermissionsResult> all permission granted");
            Intent intent = getIntent();
            if (intent.getData() != null) {
                mSourceUri = intent.getData();
                startLoadBitmap(mSourceUri);
            } else {
                pickImage();
            }
        } else {
            for (int i = 0; i < permissions.length; i++) {
                if (Manifest.permission.READ_EXTERNAL_STORAGE.equals(permissions[i])
                        && grantResults[i] == PackageManager.PERMISSION_DENIED) {
                    PermissionHelper.showDeniedPrompt(this);
                    break;
                }

                if (Manifest.permission.WRITE_EXTERNAL_STORAGE.equals(permissions[i])
                        && grantResults[i] == PackageManager.PERMISSION_DENIED) {
                    PermissionHelper.showDeniedPrompt(this);
                    break;
                }
            }
            Log.i(LOGTAG, "<onRequestPermissionsResult> permission denied, finish");
            finish();
        }
    }

    private File getFile(Uri outUri) {
        if (outUri == null) {
            return null;
        } else {
            return SaveImage.getOutPutFile(getApplicationContext(), outUri);
        }
    }

    private OutputStream getOutputStream(Uri outUri) {
        if (outUri == null) {
            return null;
        } else {
            OutputStream mOutStream = null;
            try {
                mOutStream = getContentResolver().openOutputStream(outUri);
            } catch (FileNotFoundException e) {
                Log.w(LOGTAG, "cannot getOutPutStrem: " + outUri.toString(), e);
            } catch (Exception e) {
            	Log.w(LOGTAG, "cannot getOutPutStrem: " + outUri.toString(), e);
			}
            return mOutStream;
        }
    }

    private OutputStream getOutputStream(File file) {
        if (file == null) {
            return null;
        } else {
            OutputStream mOutStream = null;
            try {
                mOutStream = new FileOutputStream(file);
            } catch (FileNotFoundException e) {
                Log.w(LOGTAG, "cannot getOutPutStrem: ", e);
            } catch (Exception e) {
            	Log.w(LOGTAG, "cannot getOutPutStrem: ", e);
			}
            return mOutStream;
        }
    }

    private long availableMemory() {
        ActivityManager am = (ActivityManager) (getApplicationContext()
                .getSystemService(Context.ACTIVITY_SERVICE));
        android.app.ActivityManager.MemoryInfo mi = new android.app.ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        long availableMemory = mi.availMem;
        Log.d(LOGTAG,
                "<availableMemory>current available memory: "
                        + availableMemory);
        return availableMemory;
    }


    //use ExifInterface insert orientation and time to JPG EXIF
    private OutputStream getExifData(OutputStream outStream) {
        OutputStream stream = null;
        if (outStream != null) {
            ExifInterface exif = new ExifInterface();
            long time = System.currentTimeMillis();
            updateExifData(exif, time);
            // Compress to byte array
            stream = exif.getExifWriterStream(outStream);
            return stream;
        } else {
            return outStream;
        }
    }

    private void updateExifData(ExifInterface exif, long time) {
        // Set tags
        exif.addDateTimeStampTag(ExifInterface.TAG_DATE_TIME, time,
                TimeZone.getDefault());
        exif.setTag(exif.buildTag(ExifInterface.TAG_ORIENTATION,
                ExifInterface.Orientation.TOP_LEFT));
        // Remove old thumbnail
        exif.removeCompressedThumbnail();
    }

    /// M: [BUG.ADD] adjust sample size according size limit @{
    private final static int CROP_PHOTO_SIZE_LIMIT = FeatureConfig.sIsLowRamDevice ? 5 * 1024 * 1024
            : 10 * 1024 * 1024;
    /// @}
}
