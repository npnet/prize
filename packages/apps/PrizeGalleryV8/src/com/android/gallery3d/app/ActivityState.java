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

import android.app.ActionBar;
import android.app.Activity;
import android.app.StatusBarManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.BatteryManager;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import com.android.gallery3d.R;
import com.android.gallery3d.anim.StateTransitionAnimation;
import com.android.gallery3d.glrenderer.RawTexture;
import com.android.gallery3d.ui.GLView;
import com.android.gallery3d.ui.PreparePageFadeoutTexture;
import com.android.gallery3d.util.GalleryUtils;
// Nav bar color customized feature. prize-linkh-20170901 @{
import com.mediatek.common.prizeoption.PrizeOption;
// @}

abstract public class ActivityState {
    protected static final int FLAG_HIDE_ACTION_BAR = 1;
    protected static final int FLAG_HIDE_STATUS_BAR = 2;
    protected static final int FLAG_SCREEN_ON_WHEN_PLUGGED = 4;
    protected static final int FLAG_SCREEN_ON_ALWAYS = 8;
    protected static final int FLAG_ALLOW_LOCK_WHILE_SCREEN_ON = 16;
    protected static final int FLAG_SHOW_WHEN_LOCKED = 32;

    protected AbstractGalleryActivity mActivity;
    protected Bundle mData;
    protected int mFlags;

    protected ResultEntry mReceivedResults;
    protected ResultEntry mResult;

    protected static class ResultEntry {
        public int requestCode;
        public int resultCode = Activity.RESULT_CANCELED;
        public Intent resultData;
    }

    private boolean mDestroyed = false;
    private boolean mPlugged = false;
    boolean mIsFinishing = false;

    private static final String KEY_TRANSITION_IN = "transition-in";

    private StateTransitionAnimation.Transition mNextTransition =
            StateTransitionAnimation.Transition.None;
    private StateTransitionAnimation mIntroAnimation;
    private GLView mContentPane;

    protected ActivityState() {
    }

    protected void setContentPane(GLView content) {
        mContentPane = content;
        if (mIntroAnimation != null) {
            mContentPane.setIntroAnimation(mIntroAnimation);
            mIntroAnimation = null;
        }
        mContentPane.setBackgroundColor(getBackgroundColor());
        mActivity.getGLRoot().setContentPane(mContentPane);
    }

    void initialize(AbstractGalleryActivity activity, Bundle data) {
        mActivity = activity;
        mData = data;
    }

    public Bundle getData() {
        return mData;
    }

    protected void onBackPressed() {
        mActivity.getStateManager().finishState(this);
    }
    
    protected void onBackPressed(boolean isPhotoPage) {
        mActivity.getStateManager().finishState(this, isPhotoPage);
    }

    protected void setStateResult(int resultCode, Intent data) {
        if (mResult == null) return;
        mResult.resultCode = resultCode;
        mResult.resultData = data;
    }

    protected void onConfigurationChanged(Configuration config) {
    }

    protected void onSaveState(Bundle outState) {
    }

    protected void onStateResult(int requestCode, int resultCode, Intent data) {
    }

    protected float[] mBackgroundColor;

    protected int getBackgroundColorId() {
        return R.color.default_background;
    }

    protected float[] getBackgroundColor() {
        return mBackgroundColor;
    }

    protected void onCreate(Bundle data, Bundle storedState) {
        mBackgroundColor = GalleryUtils.intColorToFloatARGBArray(
                mActivity.getResources().getColor(getBackgroundColorId()));
    }

    protected void showTabView(View tabView) {
    	tabView.setVisibility(View.GONE);
    }

    protected void showContainerTip(View containerTip) {
        if (containerTip != null) {
            containerTip.setVisibility(View.GONE);
        }
    }

    protected void clearStateResult() {
    }
    protected void systemUIMode() {
    	Window window = mActivity.getWindow();
//    	window.getDecorView().setSystemUiVisibility(0);
//    	window.setStatusBarColor(R.color.status_bar);
//    	window.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
    	window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN                                                                                               
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_VISIBLE); 
    	window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);     
        window.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        window.setStatusBarColor(Color.WHITE); 
        window.setNavigationBarColor(Color.BLACK);
        WindowManager.LayoutParams lp = window.getAttributes();
        lp.statusBarInverse = StatusBarManager.STATUS_BAR_INVERSE_GRAY;
        window.setAttributes(lp);
        // Nav bar color customized feature. prize-linkh-20170901 @{
        if(PrizeOption.PRIZE_NAVBAR_COLOR_CUST) {
            window.setDisableCustNavBarColor(false);
        } // @}
    }
    
    protected void overflowIcon(GalleryActionBar actionBar) {
    	actionBar.setOverflowIcon(R.drawable.overflow_new);
    }

    BroadcastReceiver mPowerIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
                boolean plugged = (0 != intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0));

                if (plugged != mPlugged) {
                    mPlugged = plugged;
                    setScreenFlags();
                }
            }
        }
    };

    private void setScreenFlags() {
        final Window win = mActivity.getWindow();
        final WindowManager.LayoutParams params = win.getAttributes();
        if ((0 != (mFlags & FLAG_SCREEN_ON_ALWAYS)) ||
                (mPlugged && 0 != (mFlags & FLAG_SCREEN_ON_WHEN_PLUGGED))) {
            params.flags |= WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
        } else {
            params.flags &= ~WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
        }
        if (0 != (mFlags & FLAG_ALLOW_LOCK_WHILE_SCREEN_ON)) {
            params.flags |= WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON;
        } else {
            params.flags &= ~WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON;
        }
        if (0 != (mFlags & FLAG_SHOW_WHEN_LOCKED)) {
            params.flags |= WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;
        } else {
            params.flags &= ~WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;
        }
        win.setAttributes(params);
    }

    protected void transitionOnNextPause(Class<? extends ActivityState> outgoing,
            Class<? extends ActivityState> incoming, StateTransitionAnimation.Transition hint) {
        if (outgoing == SinglePhotoPage.class && incoming == AlbumPage.class) {
            mNextTransition = StateTransitionAnimation.Transition.Outgoing;
        } else if (outgoing == AlbumPage.class && incoming == SinglePhotoPage.class) {
            mNextTransition = StateTransitionAnimation.Transition.PhotoIncoming;
        } else {
            mNextTransition = hint;
        }
    }

    protected void performHapticFeedback(int feedbackConstant) {
        mActivity.getWindow().getDecorView().performHapticFeedback(feedbackConstant,
                HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
    }

    protected void onPause() {
        if (0 != (mFlags & FLAG_SCREEN_ON_WHEN_PLUGGED)) {
            ((Activity) mActivity).unregisterReceiver(mPowerIntentReceiver);
        }
        if (mNextTransition != StateTransitionAnimation.Transition.None) {
            mActivity.getTransitionStore().put(KEY_TRANSITION_IN, mNextTransition);
            PreparePageFadeoutTexture.prepareFadeOutTexture(mActivity, mContentPane);
            mNextTransition = StateTransitionAnimation.Transition.None;
        }
    }

    // should only be called by StateManager
    void resume() {
        systemUIMode();
    	if (mActivity instanceof GalleryActivity) {
    		GalleryActivity galleryActivity = (GalleryActivity) mActivity;
            showContainerTip(mActivity.getContainerTipView());
    		showTabView(galleryActivity.getTabView());
    	}
        AbstractGalleryActivity activity = mActivity;
        /*ActionBar actionBar = activity.getActionBar();
        if (actionBar != null) {
            /// M: [BUG.ADD] @{
            // Avoid to set ActionBar visibility in some cases
            if (mNotSetActionBarVisibiltyWhenResume == false) {
            /// @}
                if ((mFlags & FLAG_HIDE_ACTION_BAR) != 0) {
                    actionBar.hide();
                } else {
                    actionBar.show();
                }
            /// M: [BUG.ADD] @{
            }
            /// @}
            int stateCount = mActivity.getStateManager().getStateCount();
            mActivity.getGalleryActionBar().setDisplayOptions(stateCount > 1, true);
            // Default behavior, this can be overridden in ActivityState's onResume.
            actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
            overflowIcon(mActivity.getGalleryActionBar());
        }*/

        activity.invalidateOptionsMenu();

        setScreenFlags();

        boolean lightsOut = ((mFlags & FLAG_HIDE_STATUS_BAR) != 0);
        mActivity.getGLRoot().setLightsOutMode(lightsOut);

        ResultEntry entry = mReceivedResults;
        if (entry != null) {
            mReceivedResults = null;
            onStateResult(entry.requestCode, entry.resultCode, entry.resultData);
        }

        if (0 != (mFlags & FLAG_SCREEN_ON_WHEN_PLUGGED)) {
            // we need to know whether the device is plugged in to do this correctly
            final IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_BATTERY_CHANGED);
            activity.registerReceiver(mPowerIntentReceiver, filter);
        }

        onResume();

        // the transition store should be cleared after resume;
        mActivity.getTransitionStore().clear();
    }

    // a subclass of ActivityState should override the method to resume itself
    protected void onResume() {
    	systemUIMode();
        RawTexture fade = mActivity.getTransitionStore().get(
                PreparePageFadeoutTexture.KEY_FADE_TEXTURE);
        mNextTransition = mActivity.getTransitionStore().get(
                KEY_TRANSITION_IN, StateTransitionAnimation.Transition.None);
        if (mNextTransition != StateTransitionAnimation.Transition.None) {
            mIntroAnimation = new StateTransitionAnimation(mNextTransition, fade);
            mNextTransition = StateTransitionAnimation.Transition.None;
        }
    }

    protected boolean onCreateActionBar(Menu menu) {
        // TODO: we should return false if there is no menu to show
        //       this is a workaround for a bug in system
        return true;
    }

    protected boolean onItemSelected(MenuItem item) {
        return false;
    }

    protected void onDestroy() {
        mDestroyed = true;
    }

    boolean isDestroyed() {
        return mDestroyed;
    }

    public boolean isFinishing() {
        return mIsFinishing;
    }

    protected MenuInflater getSupportMenuInflater() {
        return mActivity.getMenuInflater();
    }

    //********************************************************************
    //*                              MTK                                 *
    //********************************************************************
    /// M: [BUG.ADD] dataManager object key.@{
    protected static final String KEY_DATA_OBJECT = "data-manager-object";
    protected static final String KEY_PROCESS_ID = "process-id";
    ///@}
    // Avoid to set ActionBar visibility in some cases
    protected boolean mNotSetActionBarVisibiltyWhenResume = false;

    public boolean onPrepareOptionsMenu(Menu menu) {
        return true;
    }

    /// M: [FEATURE.ADD] [Runtime permission] @{
    /**
     * Dispatch the onRequestPermissionsResult call back from Activity to
     * ActivityState.
     *
     * @param requestCode
     *            The request code passed in requestPermissions(Activity,
     *            String[], int)
     * @param permissions
     *            The request permissions. Never null.
     * @param grantResults
     *            The grant results for the corresponding permissions which is
     *            either PERMISSION_GRANTED or PERMISSION_DENIED. Never null.
     */
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
            int[] grantResults) {
    }
    /// @}

    /// M: [PERF.ADD] add for delete many files performance improve @{
    /**
     * Set if ActivityState is sensitive to change of data.
     *
     * @param isProviderSensive
     *            If ActivityState is sensitive to change of data
     */
    public void setProviderSensive(boolean isProviderSensive) {
    }

    /**
     * Notify that the content is dirty and trigger some operations that only
     * occur when content really changed.
     */
    public void fakeProviderChange() {
    }
    /// @}
}
