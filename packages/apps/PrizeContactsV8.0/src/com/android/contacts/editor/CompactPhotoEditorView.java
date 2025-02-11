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

package com.android.contacts.editor;

import com.android.contacts.R;
import com.android.contacts.common.ContactPhotoManager;
import com.android.contacts.common.model.ValuesDelta;
import com.android.contacts.common.util.MaterialColorMapUtils.MaterialPalette;
import com.android.contacts.util.SchedulingUtils;
import com.android.contacts.widget.QuickContactImageView;

import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.ContactsContract;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import com.mediatek.contacts.util.Log;

/**
 * Displays a photo and calls the host back when the user clicks it.
 */
public class CompactPhotoEditorView extends RelativeLayout implements View.OnClickListener {

    /**
     * Callbacks for the host of this view.
     */
    public interface Listener {

        /**
         * Invoked when the user wants to change their photo.
         */
        void onPhotoEditorViewClicked();
    }

    private Listener mListener;

    private final float mLandscapePhotoRatio;
    private final float mPortraitPhotoRatio;
    private final boolean mIsTwoPanel;

    private final int mActionBarHeight;
    private final int mStatusBarHeight;

    private QuickContactImageView mPhotoImageView;
/* prize zhangzhonghao change photo click view start */	
//    private View mPhotoIcon;
//    private View mPhotoIconOverlay;
//    private View mPhotoTouchInterceptOverlay;
/* prize zhangzhonghao change photo click view end */
    private boolean mReadOnly;
    private boolean mIsNonDefaultPhotoBound;

    public CompactPhotoEditorView(Context context) {
        this(context, null);
    }

    public CompactPhotoEditorView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mLandscapePhotoRatio = getTypedFloat(R.dimen.quickcontact_landscape_photo_ratio);
        mPortraitPhotoRatio = getTypedFloat(R.dimen.editor_portrait_photo_ratio);
        mIsTwoPanel = getResources().getBoolean(R.bool.contacteditor_two_panel);

        final TypedArray styledAttributes = getContext().getTheme().obtainStyledAttributes(
                new int[] { android.R.attr.actionBarSize });
        mActionBarHeight = (int) styledAttributes.getDimension(0, 0);
        styledAttributes.recycle();

        final int resourceId = getResources().getIdentifier(
                "status_bar_height", "dimen", "android");
        mStatusBarHeight = resourceId > 0 ? getResources().getDimensionPixelSize(resourceId) : 0;
    }

    private float getTypedFloat(int resourceId) {
        final TypedValue typedValue = new TypedValue();
        getResources().getValue(resourceId, typedValue, /* resolveRefs =*/ true);
        return typedValue.getFloat();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mPhotoImageView = (QuickContactImageView) findViewById(R.id.photo);
/* prize zhangzhonghao change photo click view start */		
//        mPhotoIcon = findViewById(R.id.photo_icon);
//        mPhotoIconOverlay = findViewById(R.id.photo_icon_overlay);
//        mPhotoTouchInterceptOverlay = findViewById(R.id.photo_touch_intercept_overlay);
/* prize zhangzhonghao change photo click view end */
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    public void setReadOnly(boolean readOnly) {
        mReadOnly = readOnly;
        if (mReadOnly) {
/* prize zhangzhonghao change photo click view start */		
//            mPhotoIcon.setVisibility(View.GONE);
//            mPhotoIconOverlay.setVisibility(View.GONE);
/* prize zhangzhonghao change photo click view end */
        } else {
            
            /* prize zhangzhonghao remove 20160413 start */
//            mPhotoTouchInterceptOverlay.setOnClickListener(this);
            mPhotoImageView.setOnClickListener(this);
            /* prize zhangzhonghao remove 20160413 end */
            
        }
    }

    /**
     * Tries to bind a full size photo or a bitmap loaded from the given ValuesDelta,
     * and falls back to the default avatar, tinted using the given MaterialPalette (if it's not
     * null);
     */
    public void setPhoto(ValuesDelta valuesDelta, MaterialPalette materialPalette) {
        // Check if we can update to the full size photo immediately
        final Long photoFileId = EditorUiUtils.getPhotoFileId(valuesDelta);
        if (photoFileId != null) {
            final Uri photoUri = ContactsContract.DisplayPhoto.CONTENT_URI.buildUpon()
                    .appendPath(photoFileId.toString()).build();
            setFullSizedPhoto(photoUri);
            adjustDimensions();
            return;
        }

        // Use the bitmap image from the values delta
        final Bitmap bitmap = EditorUiUtils.getPhotoBitmap(valuesDelta);
        if (bitmap != null) {
            setPhoto(bitmap);
            adjustDimensions();
            return;
        }

        setDefaultPhoto(materialPalette);
        adjustDimensions();
    }

    private void adjustDimensions() {
        // Follow the same logic as MultiShrinkScroll.initialize
        SchedulingUtils.doOnPreDraw(this, /* drawNextFrame =*/ false, new Runnable() {
            @Override
            public void run() {
                final int photoHeight, photoWidth;
                if (mIsTwoPanel) {
                    photoHeight = getContentViewHeight();
                    photoWidth = (int) (photoHeight * mLandscapePhotoRatio);
                } else {
                    // Make the photo slightly shorter that it is wide
                    photoWidth = getContentViewWidth();
                    photoHeight = (int) (photoWidth / mPortraitPhotoRatio);
                }
                final ViewGroup.LayoutParams layoutParams = getLayoutParams();
                layoutParams.height = /*photoHeight*/176; //zhangzhonghao 20160412
                layoutParams.width = photoWidth;
                setLayoutParams(layoutParams);
            }
        });
    }

    private int getContentViewWidth() {
        final Activity activity = (Activity) getContext();
        final DisplayMetrics displayMetrics = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        return displayMetrics.widthPixels;
    }

    // We're calculating the height the hard way because using the height of the content view
    // (found using android.view.Window.ID_ANDROID_CONTENT) with the soft keyboard up when
    // going from portrait to landscape mode results in a very small height value.
    // See b/20526470
    private int getContentViewHeight() {
        final Activity activity = (Activity) getContext();
        final DisplayMetrics displayMetrics = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        return displayMetrics.heightPixels - mActionBarHeight - mStatusBarHeight;
    }

    /**
     * Whether a removable, non-default photo is bound to this view.
     */
    public boolean isWritablePhotoSet() {
        return !mReadOnly && mIsNonDefaultPhotoBound;
    }

    /**
     * Binds the given bitmap.
     */
    private void setPhoto(Bitmap bitmap) {
        mPhotoImageView.setImageBitmap(bitmap);
        mIsNonDefaultPhotoBound = true;
    }

    private void setDefaultPhoto(MaterialPalette materialPalette) {
        EditorUiUtils.setDefaultPhoto(mPhotoImageView, getResources(), materialPalette);
    }

    /**
     * Binds a full size photo loaded from the given Uri.
     */
    public void setFullSizedPhoto(Uri photoUri) {
        EditorUiUtils.loadPhoto(ContactPhotoManager.getInstance(getContext()),
                mPhotoImageView, photoUri);
        mIsNonDefaultPhotoBound = true;
    }

    /**
     * Removes the current bound photo bitmap.
     */
    public void removePhoto() {
        mPhotoImageView.setImageBitmap(/* bitmap =*/ null);
        mIsNonDefaultPhotoBound = false;
        setDefaultPhoto(/* materialPalette =*/ null);
    }

    @Override
    public void onClick(View view) {
        if (mListener != null) {
            mListener.onPhotoEditorViewClicked();
        }
    }

    /// M: ALPS02698016.sim account should unable to select photo when switch account.@{
    public void setSelectPhotoEnable(boolean isEnable) {
       /* if (isEnable) {
            mPhotoIcon.setVisibility(View.VISIBLE);
            mPhotoIconOverlay.setVisibility(View.VISIBLE);
            mPhotoTouchInterceptOverlay.setVisibility(View.VISIBLE);
        } else {
            mPhotoIcon.setVisibility(View.GONE);
            mPhotoIconOverlay.setVisibility(View.GONE);
            mPhotoTouchInterceptOverlay.setVisibility(View.GONE);
        }*/
    }
    /// @}
}