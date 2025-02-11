/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2009 The Android Open Source Project
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

import android.Manifest;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.gallery3d.R;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.DataManager;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.gadget.WidgetUtils;
import com.android.gallery3d.picasasource.PicasaSource;
import com.android.gallery3d.util.GalleryUtils;

import com.mediatek.gallery3d.adapter.FeatureHelper;
import com.mediatek.gallery3d.util.PermissionHelper;
import com.mediatek.gallery3d.util.TraceHelper;
import com.mediatek.galleryfeature.config.FeatureConfig;
import com.mediatek.galleryframework.base.MediaFilter;
import com.mediatek.galleryframework.base.MediaFilterSetting;

public final class GalleryActivity extends AbstractGalleryActivity implements OnCancelListener {
    public static final String EXTRA_SLIDESHOW = "slideshow";
    public static final String EXTRA_DREAM = "dream";
    public static final String EXTRA_CROP = "crop";
    /// M: [BUG.ADD] @{
    public static final String EXTRA_FROM_WIDGET = "fromWidget";
    /// @}

    public static final String ACTION_REVIEW = "com.android.camera.action.REVIEW";
    public static final String KEY_GET_CONTENT = "get-content";
    public static final String KEY_GET_ALBUM = "get-album";
    public static final String KEY_TYPE_BITS = "type-bits";
    public static final String KEY_MEDIA_TYPES = "mediaTypes";
    public static final String KEY_DISMISS_KEYGUARD = "dismiss-keyguard";

    private static final String TAG = "Gallery2/GalleryActivity";
    private Dialog mVersionCheckDialog;

    private View mTabView;
    private View mPhotoTabView;
    private View mPhotoSetTabView;
    private View view;
	public View getTabView() {
		return mTabView;
	}
    /// M: [TESTCASE.ADD] add for performance test case@{
    public long mStopTime = 0;
    /// @}

    private void findTabViews() {
        mTabView = findViewById(R.id.tabview);
        mPhotoTabView = findViewById(R.id.tab_photo);
        mPhotoSetTabView = findViewById(R.id.tab_photoset);
    }

    private void setTabViewListeners() {
        mPhotoTabView.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                mPhotoTabView.setSelected(true);
                mPhotoSetTabView.setSelected(false);
                /*String mLocalAllPath = getDataManager().getTopSetPath(
                        DataManager.INCLUDE_LOCAL_ALL_ONLY)+"/" + MediaSetUtils.CAMERA_BUCKET_ID;
                String timePath = FilterUtils.switchClusterPath(mLocalAllPath, FilterUtils.CLUSTER_BY_TIME);
                Bundle data = new Bundle();
                int typeBits = DataManager.INCLUDE_LOCAL_ALL_ONLY;
                data.putInt(KEY_TYPE_BITS, typeBits);
                data.putString(TimeLinePage.KEY_MEDIA_PATH, timePath);
                getStateManager().switchState(getStateManager().getTopState(), TimeLinePage.class, data);*/
				getStateManager().setAlbumSetPage(0);
            }
        });

        mPhotoSetTabView.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                mPhotoTabView.setSelected(false);
                mPhotoSetTabView.setSelected(true);

                /*int typeBits = DataManager.INCLUDE_ALL;
                Bundle data = new Bundle();
                data.putInt(KEY_TYPE_BITS, typeBits);
                data.putString(AlbumSetPage.KEY_MEDIA_PATH,
                        getDataManager().getTopSetPath(typeBits));
                getStateManager().switchState(getStateManager().getTopState(), AlbumSetPage.class, data);*/
				getStateManager().setAlbumSetPage(1);
            }
        });
    }

    public void setTabTitle(int page) {
        if (page == 0) { //time
            mPhotoTabView.setSelected(true);
            mPhotoSetTabView.setSelected(false);
        } else { // all
            mPhotoTabView.setSelected(false);
            mPhotoSetTabView.setSelected(true);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        /// M: [DEBUG.ADD] @{
        TraceHelper.traceBegin(">>>>Gallery-onCreate");
        /// @}
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_ACTION_BAR);
        requestWindowFeature(Window.FEATURE_ACTION_BAR_OVERLAY);

        if (getIntent().getBooleanExtra(KEY_DISMISS_KEYGUARD, false)) {
            getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        }

        setContentView(R.layout.main);

//        ((GalleryAppImpl) getApplication()).preLoadImage();
        
        findTabViews();

        setTabViewListeners();
//		view = findViewById(R.id.gl_root_cover);
        /// M: [BUG.ADD] set gl_root_cover visible if open from widget or launch by @{
        // launcher, or else it will flash
        Intent intent = getIntent();
        if (intent != null
                && (intent.getBooleanExtra(EXTRA_FROM_WIDGET, false) || (intent
                        .getAction() != null && intent.getAction().equals(
                        intent.ACTION_MAIN)))) {
            view = findViewById(R.id.gl_root_cover);
            if (view != null) {
                view.setVisibility(View.VISIBLE);
                Log.i(TAG, "<onCreate> from widget or launcher, set gl_root_cover VISIBLE");
            }
        }
        /// @}
        /// M: [FEATURE.MODIFY] [Runtime permission] @{
        /*
        if (savedInstanceState != null) {
            getStateManager().restoreFromState(savedInstanceState);
        } else {
            initializeByIntent();
        }
         */
        boolean granted = PermissionHelper.checkAndRequestForGallery(this);
        if (granted) {
            if (savedInstanceState != null) {
                getStateManager().restoreFromState(savedInstanceState);
            } else {
                initializeByIntent();
            }
        } else {
            mSaveInstanceState = savedInstanceState;
        }
        /// @}
        /// M: [PERF.ADD] Modify CPU boost policy for first launch performance @{
        FeatureHelper.modifyBoostPolicy(this);
        /// @}
        /// M: [DEBUG.ADD] @{
        TraceHelper.traceEnd();
        /// @}
    }
    
    private boolean mIsPhotoPage = true;

    @Override
	public void finish() {
    	Log.i(TAG, "finish()");
        super.finish();
	    // remove for fix bug 41219
	    /*
    	if (!mIsPhotoPage && view != null && isFinishing()) {
    		view.setVisibility(View.VISIBLE);
    	}
        */
	}
    
    public void setShowMask(boolean isPhotoPage) {
    	mIsPhotoPage = isPhotoPage;
    }

    private void initializeByIntent() {
        Intent intent = getIntent();
        String action = intent.getAction();

        if (Intent.ACTION_GET_CONTENT.equalsIgnoreCase(action)) {
            startGetContent(intent);
        } else if (Intent.ACTION_PICK.equalsIgnoreCase(action)) {
            // We do NOT really support the PICK intent. Handle it as
            // the GET_CONTENT. However, we need to translate the type
            // in the intent here.
            Log.w(TAG, "action PICK is not supported");
            String type = Utils.ensureNotNull(intent.getType());
            if (type.startsWith("vnd.android.cursor.dir/")) {
                if (type.endsWith("/image")) intent.setType("image/*");
                if (type.endsWith("/video")) intent.setType("video/*");
            }
            startGetContent(intent);
        } else if (Intent.ACTION_VIEW.equalsIgnoreCase(action)
                || ACTION_REVIEW.equalsIgnoreCase(action)) {
            startViewAction(intent);
        /// M: [FEATURE.ADD] <CTA Data Protection> @{
        } else if (ACTION_VIEW_LOCKED_FILE.equalsIgnoreCase(action)) {
            if (FeatureConfig.SUPPORT_CTA) {
                startViewLockedFileAction();
            }
        /// @}
        } else {
            startDefaultPage();
        }
    }

    public void startDefaultPage() {
        PicasaSource.showSignInReminder(this);
        Bundle data = new Bundle();
        data.putString(AlbumSetPage.KEY_MEDIA_PATH,
                getDataManager().getTopSetPath(DataManager.INCLUDE_ALL));
        getStateManager().startState(AlbumSetPage.class, data);
        mVersionCheckDialog = PicasaSource.getVersionCheckDialog(this);
        if (mVersionCheckDialog != null) {
            mVersionCheckDialog.setOnCancelListener(this);
        }
    }

    private void startGetContent(Intent intent) {
        Bundle data = intent.getExtras() != null
                ? new Bundle(intent.getExtras())
                : new Bundle();
        data.putBoolean(KEY_GET_CONTENT, true);
        int typeBits = GalleryUtils.determineTypeBits(this, intent);
        data.putInt(KEY_TYPE_BITS, typeBits);
        data.putString(AlbumSetPage.KEY_MEDIA_PATH,
                getDataManager().getTopSetPath(typeBits));
        getStateManager().startState(AlbumSetPage.class, data);
    }

    private String getContentType(Intent intent) {
        String type = intent.getType();
        if (type != null) {
            return GalleryUtils.MIME_TYPE_PANORAMA360.equals(type)
                ? MediaItem.MIME_TYPE_JPEG : type;
        }

        Uri uri = intent.getData();
        try {
            return getContentResolver().getType(uri);
        } catch (Throwable t) {
            Log.w(TAG, "get type fail", t);
            return null;
        }
    }

    private void startViewAction(Intent intent) {
        Boolean slideshow = intent.getBooleanExtra(EXTRA_SLIDESHOW, false);
        if (slideshow) {
            getActionBar().hide();
            DataManager manager = getDataManager();
            Path path = manager.findPathByUri(intent.getData(), intent.getType());
            if (path == null || manager.getMediaObject(path)
                    instanceof MediaItem) {
                path = Path.fromString(
                        manager.getTopSetPath(DataManager.INCLUDE_IMAGE));
            }
            Bundle data = new Bundle();
            data.putString(SlideshowPage.KEY_SET_PATH, path.toString());
            data.putBoolean(SlideshowPage.KEY_RANDOM_ORDER, true);
            data.putBoolean(SlideshowPage.KEY_REPEAT, true);
            if (intent.getBooleanExtra(EXTRA_DREAM, false)) {
                data.putBoolean(SlideshowPage.KEY_DREAM, true);
            }
            getStateManager().startState(SlideshowPage.class, data);
        } else {
            Bundle data = new Bundle();
            DataManager dm = getDataManager();
            Uri uri = intent.getData();
            String contentType = getContentType(intent);
            if (contentType == null) {
                Toast.makeText(this,
                        R.string.no_such_item, Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            if (uri == null) {
                int typeBits = GalleryUtils.determineTypeBits(this, intent);
                data.putInt(KEY_TYPE_BITS, typeBits);
                data.putString(AlbumSetPage.KEY_MEDIA_PATH,
                        getDataManager().getTopSetPath(typeBits));
                getStateManager().startState(AlbumSetPage.class, data);
            } else if (contentType.startsWith(
                    ContentResolver.CURSOR_DIR_BASE_TYPE)) {
                int mediaType = intent.getIntExtra(KEY_MEDIA_TYPES, 0);
                if (mediaType != 0) {
                    uri = uri.buildUpon().appendQueryParameter(
                            KEY_MEDIA_TYPES, String.valueOf(mediaType))
                            .build();
                }
                Path setPath = dm.findPathByUri(uri, null);
                MediaSet mediaSet = null;
                if (setPath != null) {
                    mediaSet = (MediaSet) dm.getMediaObject(setPath);
                }
                if (mediaSet != null) {
                    if (mediaSet.isLeafAlbum()) {
                        data.putString(AlbumPage.KEY_MEDIA_PATH, setPath.toString());
                        data.putString(AlbumPage.KEY_PARENT_MEDIA_PATH,
                                dm.getTopSetPath(DataManager.INCLUDE_ALL));
                        getStateManager().startState(AlbumPage.class, data);
                    } else {
                        data.putString(AlbumSetPage.KEY_MEDIA_PATH, setPath.toString());
                        getStateManager().startState(AlbumSetPage.class, data);
                    }
                } else {
                    startDefaultPage();
                }
            } else {
                /// M: [FEATURE.ADD] @{
                MediaFilter mf = MediaFilterSetting.getCurrentFilter();
                if (mf != null) {
                    // enable show drm image/video
                    mf.setFlagEnable(MediaFilter.INCLUDE_DRM_ALL);
                }
                uri = FeatureHelper.tryContentMediaUri(this, uri);
                Log.d(TAG, "<startViewAction> uri:" + uri);
                /// @}

                /// M: [BUG.ADD] @{
                // If current URI is not local, do not pop up the "No storage" dialog
                if (!FeatureHelper.isLocalUri(uri)) {
                    Log.d(TAG, "<startViewAction>: uri=" + uri
                            + ", not local!!");
                    mShouldCheckStorageState = false;
                }
                /// @}
                Path itemPath = dm.findPathByUri(uri, contentType);

                /// M: [BUG.ADD] modify for item not exit,show toast and finish.@{
                if (itemPath == null) {
                    Toast.makeText(this, R.string.no_such_item,
                            Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
                /// @}

                /// M: [BUG.MODIFY] @{
                /*Path albumPath = dm.getDefaultSetOf(itemPath);*/
                Path albumPath = null;
                //clear old mediaObject, query database again
                itemPath.clearObject();
                albumPath = dm.getDefaultSetOf(itemPath);
                /// @}

                data.putString(PhotoPage.KEY_MEDIA_ITEM_PATH, itemPath.toString());
                data.putBoolean(PhotoPage.KEY_READONLY, true);

                // TODO: Make the parameter "SingleItemOnly" public so other
                //       activities can reference it.
                boolean singleItemOnly = (albumPath == null)
                        || intent.getBooleanExtra("SingleItemOnly", false);
                if (!singleItemOnly) {
                    data.putString(PhotoPage.KEY_MEDIA_SET_PATH, albumPath.toString());
                    // when FLAG_ACTIVITY_NEW_TASK is set, (e.g. when intent is fired
                    // from notification), back button should behave the same as up button
                    // rather than taking users back to the home screen
                    if (intent.getBooleanExtra(PhotoPage.KEY_TREAT_BACK_AS_UP, false)
                            || ((intent.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0)) {
                        data.putBoolean(PhotoPage.KEY_TREAT_BACK_AS_UP, true);
                    }
                }
                /// M: [FEATURE.ADD] @{
                FeatureHelper.setExtBundle(this, intent, data, itemPath);
                /// @}
				mTabView.setVisibility(View.GONE);
                //add by liangchangwei 2018-4-12 fix bug 54818
                data.putBoolean(PhotoPage.KEY_SINGLE_PHOTO_PAGE, true);
                getStateManager().startState(SinglePhotoPage.class, data);
            }
        }
    }

    @Override
    protected void onResume() {
        /// M: [DEBUG.ADD] @{
        TraceHelper.traceBegin(">>>>Gallery-onResume");
        /// @}
        /// M: [FEATURE.MARK] [Runtime permission] @{
        /*Utils.assertTrue(getStateManager().getStateCount() > 0);*/
        /// @}
        super.onResume();
        if (mVersionCheckDialog != null) {
            mVersionCheckDialog.show();
        }
        /// M: [DEBUG.ADD] @{
        TraceHelper.traceEnd();
        /// @}
        /// M: [PERF.ADD]add for performance test case @{
        mStopTime = 0;
        /// @}
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mVersionCheckDialog != null) {
            mVersionCheckDialog.dismiss();
        }
        /// M: [FEATURE.ADD] <CTA Data Protection> @{
        pauseViewLockedFileAction();
        /// @}
        /// M: [PERF.ADD] @{
        mStopTime = System.currentTimeMillis();
        /// @}
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        if (dialog == mVersionCheckDialog) {
            mVersionCheckDialog = null;
        }
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        final boolean isTouchPad = (event.getSource()
                & InputDevice.SOURCE_CLASS_POSITION) != 0;
        if (isTouchPad) {
            float maxX = event.getDevice().getMotionRange(MotionEvent.AXIS_X).getMax();
            float maxY = event.getDevice().getMotionRange(MotionEvent.AXIS_Y).getMax();
            View decor = getWindow().getDecorView();
            float scaleX = decor.getWidth() / maxX;
            float scaleY = decor.getHeight() / maxY;
            float x = event.getX() * scaleX;
            //x = decor.getWidth() - x; // invert x
            float y = event.getY() * scaleY;
            //y = decor.getHeight() - y; // invert y
            MotionEvent touchEvent = MotionEvent.obtain(event.getDownTime(),
                    event.getEventTime(), event.getAction(), x, y, event.getMetaState());
            return dispatchTouchEvent(touchEvent);
        }
        return super.onGenericMotionEvent(event);
    }

    //********************************************************************
    //*                              MTK                                 *
    //********************************************************************

    // <CTA Data Protection> @{
    // Start with CTA DataProtection action,
    // View the Image as FL drm file format.
    private static final String ACTION_VIEW_LOCKED_FILE =
            "com.mediatek.dataprotection.ACTION_VIEW_LOCKED_FILE";
    private String mToken = null;
    private String mTokenKey = null;

    private void startViewLockedFileAction() {
        Intent intent = getIntent();
        mToken = intent.getStringExtra("TOKEN");
        mTokenKey = intent.getStringExtra("TOKEN_KEY");
        if (intent.getData() == null) {
            Log.i(TAG, "<startViewLockedFileAction> intent.getData() is null, finish activity");
            finish();
        }

        if (null == mToken
                || !FeatureHelper.isTokenValid(this, mTokenKey, mToken)) {
            Log.i(TAG, "<startViewLockedFileAction> token invalid, finish activity");
            finish();
        }
        Bundle data = new Bundle();
        DataManager dm = getDataManager();
        Path itemPath = dm.findPathByUri(intent.getData(), intent.getType());
        if (itemPath == null) {
            Toast.makeText(this, R.string.no_such_item, Toast.LENGTH_LONG)
                    .show();
            Log.i(TAG, "<startViewLockedFileAction> find path is null, finish activity");
            finish();
            return;
        }
        /// M: [BUG.MODIFY] Clear old mediaObject, query database again @{
        itemPath.clearObject();
        /// @}
        data.putBoolean(PhotoPage.KEY_READONLY, true);
        data.putString(SinglePhotoPage.KEY_MEDIA_ITEM_PATH, itemPath.toString());
        Log.i(TAG, "<startViewLockedFileAction> startState SinglePhotoPage, path = "
                        + itemPath);
        getStateManager().startState(SinglePhotoPage.class, data);
    }

    private void pauseViewLockedFileAction() {
        if (mToken == null) {
            return;
        }
        Log.i(TAG, "<pauseViewLockedFileAction> Finish activity when pause");
        FeatureHelper.clearToken(this, mTokenKey, mToken);
        finish();
    }
    // @}

    // add for log trace @{
    protected void onStart() {
        TraceHelper.traceBegin(">>>>Gallery-onStart");
        super.onStart();
        TraceHelper.traceEnd();
    }
    // @}

    // [Runtime permission] @{
    private Bundle mSaveInstanceState;

    @Override
    public void onRequestPermissionsResult(int requestCode,
            String[] permissions, int[] grantResults) {
        // when gallery permssion changed, notify all widgets to update
        WidgetUtils.notifyAllWidgetViewChanged();
        if (getStateManager().getStateCount() != 0) {
            Log.i(TAG, "<onRequestPermissionsResult> dispatch to ActivityState");
            getStateManager().getTopState().onRequestPermissionsResult(requestCode, permissions,
                    grantResults);
        } else if (PermissionHelper.isAllPermissionsGranted(permissions, grantResults)) {
            Log.i(TAG, "<onRequestPermissionsResult> all permission granted");
            if (mSaveInstanceState != null) {
                getStateManager().restoreFromState(mSaveInstanceState);
            } else {
                initializeByIntent();
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
            Log.i(TAG, "<onRequestPermissionsResult> permission denied, finish");
            finish();
        }
    }
    // @}
}