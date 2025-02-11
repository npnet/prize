/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein is
 * confidential and proprietary to MediaTek Inc. and/or its licensors. Without
 * the prior written permission of MediaTek inc. and/or its licensors, any
 * reproduction, modification, use or disclosure of MediaTek Software, and
 * information contained herein, in whole or in part, shall be strictly
 * prohibited.
 *
 * MediaTek Inc. (C) 2014. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER
 * ON AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL
 * WARRANTIES, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR
 * NONINFRINGEMENT. NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH
 * RESPECT TO THE SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY,
 * INCORPORATED IN, OR SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES
 * TO LOOK ONLY TO SUCH THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO.
 * RECEIVER EXPRESSLY ACKNOWLEDGES THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO
 * OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES CONTAINED IN MEDIATEK
 * SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK SOFTWARE
 * RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S
 * ENTIRE AND CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE
 * RELEASED HEREUNDER WILL BE, AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE
 * MEDIATEK SOFTWARE AT ISSUE, OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE
 * CHARGE PAID BY RECEIVER TO MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek
 * Software") have been modified by MediaTek Inc. All revisions are subject to
 * any receiver's applicable license agreements with MediaTek Inc.
 */
package com.android.camera.manager;

import android.content.Context;
import android.view.View;
import android.view.animation.Animation;

import com.android.camera.CameraActivity;
import com.android.camera.Log;
import com.android.camera.SettingUtils;
import com.android.camera.Util;
import com.android.internal.view.RotationPolicy;

/*prize-xuchunming-20171218-bugid:45614-start*/
/*prize-xuchunming-20171218-bugid:45614-end*/

public abstract class ViewManager implements CameraActivity.OnOrientationListener {
    private static final String TAG = "ViewManager";

    public static final int VIEW_LAYER_BOTTOM = -1;
    public static final int VIEW_LAYER_NORMAL = 0;
    public static final int VIEW_LAYER_TOP = 1;
    public static final int VIEW_LAYER_SHUTTER = 2;
    public static final int VIEW_LAYER_SETTING = 3;
    public static final int VIEW_LAYER_OVERLAY = 4;
    /*PRIZE-add setting items-wanzhijuan-2016-05-03-start*/ 
    public static final int VIEW_LAYER_GRID = 5;
    /*PRIZE-add setting items-wanzhijuan-2016-05-03-end*/ 
	public static final int VIEW_LAYER_THUMBNAIL = 6;
	public static final int VIEW_LAYER_SURFACECOVER = 7;
    public static final int UNKNOWN = -1;

    private CameraActivity mContext;
    private View mView;
    private final int mViewLayer;
    private boolean mShowing;
    private int mOrientation;
    private boolean mEnabled = true;
    private boolean mFilter = true;
    private Animation mFadeIn;
    private Animation mFadeOut;
    private boolean mShowAnimationEnabled = true;
    private boolean mHideAnimationEnabled = false;
    private int mConfigOrientation = UNKNOWN;
    /*prize-xuchunming-20171218-bugid:45614-start*/
    private int mUiOrientation;
    /*prize-xuchunming-20171218-bugid:45614-end*/
    /*prize=modify-after turning off the automatic rotation, the camera UI orientation will not refresh-xiaoping-20180413-start*/
    private boolean isFirstRefreshOnLocked = false;
    /*prize=modify-after turning off the automatic rotation, the camera UI orientation will not refresh-xiaoping-20180413-end*/
    public ViewManager(CameraActivity context, int layer) {
        mContext = context;
        mContext.addViewManager(this);
        mContext.addOnOrientationListener(this);
        mOrientation = mContext.getOrientationCompensation();
        mViewLayer = layer;
    }

    public ViewManager(CameraActivity context) {
        this(context, VIEW_LAYER_NORMAL);
    }

    @Override
    public void onOrientationChanged(int orientation) {
    	Log.i(TAG, "orientation: "+orientation+",  mOrientation: "+mOrientation+",isFirstRefreshOnLocked: "+isFirstRefreshOnLocked);
    	/*prize-xuchunming-20171218-bugid:45614-start*/
    	if(isRotationLocked(mContext) == false){
	        if (mOrientation != orientation) {
	            mOrientation = orientation;
	            Util.setOrientation(mView, mOrientation, true);
	            mUiOrientation = mOrientation;
	            isFirstRefreshOnLocked = true;
	        }
    	}else {
    		if (mOrientation != orientation && isFirstRefreshOnLocked) {
	            mOrientation = orientation;
	            /*prize=modify-after turning off the automatic rotation, the camera UI orientation will not refresh-xiaoping-20180413-start*/
	            Util.setOrientation(mView, mOrientation, true);
	            mUiOrientation = mOrientation;
	            isFirstRefreshOnLocked = false;
	            /*prize=modify-after turning off the automatic rotation, the camera UI orientation will not refresh-xiaoping-20180413-end*/
	        } else if (orientation == 0 && mOrientation != 0) {//modefiy bugid:56655 The dialog is not displayed vertically when the vertical screen is locked-xiaoping-20180507
                mOrientation = orientation;
                Util.setOrientation(mView, mOrientation, true);
                mUiOrientation = mOrientation;
                isFirstRefreshOnLocked = false;
            }
        }
    	/*prize-xuchunming-20171218-bugid:45614-end*/
    }

    public void show() {
        Log.d(TAG, "show() " + this);
        if (mView == null) {
            mConfigOrientation = mContext.getResources().getConfiguration().orientation;
            mView = getView();
            if (mView != null) {
                getContext().addView(mView, mViewLayer);
                Util.setOrientation(mView, mOrientation, false);
                /*prize-xuchunming-20171218-bugid:45614-start*/
                mUiOrientation = mOrientation;
                /*prize-xuchunming-20171218-bugid:45614-end*/
            }

        }
        if (mView != null && !mShowing) {
            mShowing = true;
            setEnabled(mEnabled);
            refresh(); // refresh view state
            /*PRIZE-add setting items-wanzhijuan-2016-05-03-start*/ 
            onShow();
            /*PRIZE-add setting items-wanzhijuan-2016-05-03-end*/ 
        } else if (mShowing) {
            refresh();
        }
    }

    /*PRIZE-add setting items-wanzhijuan-2016-05-03-start*/ 
    /**
     * 
     * GridManager Override onShow
     */
    protected void onShow() {
    	fadeIn();
        mView.setVisibility(View.VISIBLE);
    }
    /*PRIZE-add setting items-wanzhijuan-2016-05-03-end*/ 
    public void hide() {
        Log.d(TAG, "hide() " + this);
        if (mView != null && mShowing) {
            mShowing = false;
            fadeOut();
            mView.setVisibility(View.GONE);
        }
    }

    public final void uninit() {
        hide();
        if (mView != null) {
            getContext().removeView(mView, mViewLayer);
        }
        onRelease();
        mView = null;
        mContext.removeViewManager(this);
        mContext.removeOnOrientationListener(this);
    }

    public boolean isShowing() {
        return mShowing;
    }

    public boolean isEnabled() {
        return mEnabled;
    }

    public int getViewHeight() {
        int height = mView == null ? 0 : mView.getHeight();
        Log.i(TAG, "getViewHeight height = " + height);
        return height;
    }

    public int getViewWidth() {
        int width = mView == null ? 0 : mView.getWidth();
        Log.i(TAG, "getViewWidth width = " + width);
        return width;
    }

    public final CameraActivity getContext() {
        return mContext;
    }

    public int getViewLayer() {
        return mViewLayer;
    }

    public void setFileter(boolean filter) {
        mFilter = filter;
    }

    public int getOrientation() {
        return mOrientation;
    }

    public void setAnimationEnabled(boolean showAnimationEnabled, boolean hideAnimationEnabled) {
        mShowAnimationEnabled = showAnimationEnabled;
        mHideAnimationEnabled = hideAnimationEnabled;
    }

    public boolean getShowAnimationEnabled() {
        return mShowAnimationEnabled;
    }

    public boolean getHideAnimationEnabled() {
        return mHideAnimationEnabled;
    }

    public void checkConfiguration() {
        int newConfigOrientation = mContext.getResources().getConfiguration().orientation;
        if (mConfigOrientation != UNKNOWN && newConfigOrientation != mConfigOrientation) {
           // reInflate();
        }
    }

    protected void fadeIn() {
        if (mShowAnimationEnabled) {
            if (mFadeIn == null) {
                mFadeIn = getFadeInAnimation();
            }
            if (mFadeIn != null) {
                mView.startAnimation(mFadeIn);
            } else {
                Util.fadeIn(mView);
            }
        }
    }

    protected void fadeOut() {
        if (mHideAnimationEnabled) {
            if (mFadeOut == null) {
                mFadeOut = getFadeOutAnimation();
            }
            if (mFadeOut != null) {
                mView.startAnimation(mFadeOut);
            } else {
                Util.fadeOut(mView);
            }
        }
    }

    public final View inflate(int layoutId) {
        return getContext().inflate(layoutId, mViewLayer);
    }

    public final void reInflate() {
        boolean showing = mShowing;
        hide();
        if (mView != null) {
            getContext().removeView(mView, mViewLayer);
        }
        onRelease();
        mView = null;
        if (showing) {
            show();
        }
    }

    public final void refresh() {
        if (mShowing) {
            onRefresh();
        }
    }

    public boolean collapse(boolean force) {
        return false;
    }

    public void setEnabled(boolean enabled) {
        mEnabled = enabled;
        if (mView != null) {
            mView.setEnabled(mEnabled);
            if (mFilter) {
                SettingUtils.setEnabledState(mView, mEnabled);
            }
        }
    }

    /**
     * will be called when app call release() to unload views from view
     * hierarchy.
     */
    protected void onRelease() {
    }

    /**
     * Will be called when App call refresh and isShowing().
     */
    protected void onRefresh() {
    }

    /**
     * will be called if app want to show current view which hasn't been
     * created.
     *
     * @return
     */
    protected abstract View getView();

    protected Animation getFadeInAnimation() {
        return null;
    }

    protected Animation getFadeOutAnimation() {
        return null;
    }
    
    public View getContainView(){
    	return mView;
    }
    
    /*prize-xuchunming-20171218-bugid:45614-start*/
    public int getUiOrientation() {
        return mUiOrientation;
    }
    
    public boolean isRotationLocked(Context mContext) {
        return RotationPolicy.isRotationLocked(mContext);
    }
    /*prize-xuchunming-20171218-bugid:45614-start*/
}
