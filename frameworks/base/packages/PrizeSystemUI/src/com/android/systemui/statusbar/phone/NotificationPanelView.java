/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.systemui.statusbar.phone;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.ActivityManager;
import android.app.StatusBarManager;
import android.content.Context;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.os.SystemProperties;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.MathUtils;
import android.util.EventLog;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.view.accessibility.AccessibilityEvent;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.android.internal.logging.MetricsLogger;
import com.android.keyguard.KeyguardStatusView;
import com.android.systemui.AutoReinflateContainer;
import com.android.systemui.AutoReinflateContainer.InflateListener;
import com.android.systemui.DejankUtils;
import com.android.systemui.EventLogConstants;
import com.android.systemui.EventLogTags;
import com.android.systemui.Interpolators;
import com.android.systemui.R;
import com.android.systemui.classifier.FalsingManager;
import com.android.systemui.qs.QSContainer;
import com.android.systemui.statusbar.ExpandableNotificationRow;
import com.android.systemui.statusbar.ExpandableView;
import com.android.systemui.statusbar.FlingAnimationUtils;
import com.android.systemui.statusbar.GestureRecorder;
import com.android.systemui.statusbar.KeyguardAffordanceView;
import com.android.systemui.statusbar.NotificationData;
import com.android.systemui.statusbar.StatusBarState;
import com.android.systemui.statusbar.notification.NotificationUtils;
import com.android.systemui.statusbar.policy.HeadsUpManager;
import com.android.systemui.statusbar.policy.KeyguardUserSwitcher;
import com.android.systemui.statusbar.stack.NotificationStackScrollLayout;
import com.android.systemui.statusbar.stack.StackStateAnimator;

import java.util.List;

/*PRIZE-import package- liufan-2015-05-19-start*/
import com.android.systemui.statusbar.phone.FeatureOption;
import android.util.Log;
import android.os.SystemClock;
import android.graphics.Bitmap;
import android.content.ContentValues;
import android.net.Uri;
import android.os.SystemProperties;
import android.provider.Settings;
import com.android.systemui.statusbar.policy.BrightnessMirrorController;
import java.util.HashSet;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.WindowManager;
import android.view.SurfaceControl;
import android.graphics.Matrix;
import android.util.DisplayMetrics;
import android.view.Display;
import com.mediatek.keyguard.Clock.ClockView;

import android.content.Intent;
import android.graphics.Bitmap.Config;
import android.os.Debug;
/*PRIZE-import package- liufan-2015-05-19-end*/
/*PRIZE-PowerExtendMode-wangxianzhen-2015-05-30-start*/
import android.os.PowerManager;
import com.mediatek.common.prizeoption.PrizeOption;
/*PRIZE-PowerExtendMode-wangxianzhen-2015-05-30-end*/
import android.widget.LinearLayout;

/*PRIZE-PowerExtendMode-yuhao-2016-12-10-start*/
import com.android.systemui.qs.QSPanel;
/*PRIZE-PowerExtendMode-yuhao-2016-12-10-end*/
/*-modify for haokan-liufan-2017-10-10-start-*/
import com.orangecat.reflectdemo.activity.HaokanLockView;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.graphics.BitmapFactory;
import java.io.InputStream;
import com.android.systemui.keyguard.KeyguardViewMediator;
/*-modify for haokan-liufan-2017-10-10-end-*/

public class NotificationPanelView extends PanelView implements
        ExpandableView.OnHeightChangedListener,
        View.OnClickListener, NotificationStackScrollLayout.OnOverscrollTopChangedListener,
        KeyguardAffordanceHelper.Callback, NotificationStackScrollLayout.OnEmptySpaceClickListener,
        HeadsUpManager.OnHeadsUpChangedListener {

    private static final boolean DEBUG = false;

    // Cap and total height of Roboto font. Needs to be adjusted when font for the big clock is
    // changed.
    private static final int CAP_HEIGHT = 1456;
    private static final int FONT_HEIGHT = 2163;

    private static final float LOCK_ICON_ACTIVE_SCALE = 1.2f;

    static final String COUNTER_PANEL_OPEN = "panel_open";
    static final String COUNTER_PANEL_OPEN_QS = "panel_open_qs";
    private static final String COUNTER_PANEL_OPEN_PEEK = "panel_open_peek";

    private static final Rect mDummyDirtyRect = new Rect(0, 0, 1, 1);

    public static final long DOZE_ANIMATION_DURATION = 700;

    private KeyguardAffordanceHelper mAfforanceHelper;
    private KeyguardUserSwitcher mKeyguardUserSwitcher;
    private KeyguardStatusBarView mKeyguardStatusBar;
    protected QSContainer mQsContainer;
    private AutoReinflateContainer mQsAutoReinflateContainer;
    private KeyguardStatusView mKeyguardStatusView;
    private TextView mClockView;
    private View mReserveNotificationSpace;
    private View mQsNavbarScrim;
    protected NotificationsQuickSettingsContainer mNotificationContainerParent;
    protected NotificationStackScrollLayout mNotificationStackScroller;
    private boolean mAnimateNextTopPaddingChange;

    private int mTrackingPointer;
    private VelocityTracker mVelocityTracker;
    private boolean mQsTracking;

    /**
     * If set, the ongoing touch gesture might both trigger the expansion in {@link PanelView} and
     * the expansion for quick settings.
     */
    private boolean mConflictingQsExpansionGesture;

    /**
     * Whether we are currently handling a motion gesture in #onInterceptTouchEvent, but haven't
     * intercepted yet.
     */
    private boolean mIntercepting;
    private boolean mPanelExpanded;
    private boolean mQsExpanded;
    private boolean mQsExpandedWhenExpandingStarted;
    private boolean mQsFullyExpanded;
    private boolean mKeyguardShowing;
    private boolean mDozing;
    private boolean mDozingOnDown;
    private int mStatusBarState;
    private float mInitialHeightOnTouch;
    private float mInitialTouchX;
    private float mInitialTouchY;
    private float mLastTouchX;
    private float mLastTouchY;
    protected float mQsExpansionHeight;
    protected int mQsMinExpansionHeight;
    protected int mQsMaxExpansionHeight;
    private int mQsPeekHeight;
    private boolean mStackScrollerOverscrolling;
    private boolean mQsExpansionFromOverscroll;
    private float mLastOverscroll;
    protected boolean mQsExpansionEnabled = true;
    private ValueAnimator mQsExpansionAnimator;
    private FlingAnimationUtils mFlingAnimationUtils;
    private int mStatusBarMinHeight;
    private boolean mUnlockIconActive;
    private int mNotificationsHeaderCollideDistance;
    private int mUnlockMoveDistance;
    private float mEmptyDragAmount;

    private ObjectAnimator mClockAnimator;
    private int mClockAnimationTarget = -1;
    private int mTopPaddingAdjustment;
    private KeyguardClockPositionAlgorithm mClockPositionAlgorithm =
            new KeyguardClockPositionAlgorithm();
    private KeyguardClockPositionAlgorithm.Result mClockPositionResult =
            new KeyguardClockPositionAlgorithm.Result();
    private boolean mIsExpanding;

    private boolean mBlockTouches;
    private int mNotificationScrimWaitDistance;
    // Used for two finger gesture as well as accessibility shortcut to QS.
    private boolean mQsExpandImmediate;
    private boolean mTwoFingerQsExpandPossible;

    /**
     * If we are in a panel collapsing motion, we reset scrollY of our scroll view but still
     * need to take this into account in our panel height calculation.
     */
    private boolean mQsAnimatorExpand;
    private boolean mIsLaunchTransitionFinished;
    private boolean mIsLaunchTransitionRunning;
    private Runnable mLaunchAnimationEndRunnable;
    private boolean mOnlyAffordanceInThisMotion;
    private boolean mKeyguardStatusViewAnimating;
    private ValueAnimator mQsSizeChangeAnimator;

    private boolean mShadeEmpty;

    private boolean mQsScrimEnabled = true;
    private boolean mLastAnnouncementWasQuickSettings;
    private boolean mQsTouchAboveFalsingThreshold;
    private int mQsFalsingThreshold;

    private float mKeyguardStatusBarAnimateAlpha = 1f;
    private int mOldLayoutDirection;
    private HeadsUpTouchHelper mHeadsUpTouchHelper;
    private boolean mIsExpansionFromHeadsUp;
    private boolean mListenForHeadsUp;
    private int mNavigationBarBottomHeight;
    private boolean mExpandingFromHeadsUp;
    private boolean mCollapsedOnDown;
    private int mPositionMinSideMargin;
    private int mMaxFadeoutHeight;
    private int mLastOrientation = -1;
    private boolean mClosingWithAlphaFadeOut;
    private boolean mHeadsUpAnimatingAway;
    private boolean mLaunchingAffordance;
    private FalsingManager mFalsingManager;
    private String mLastCameraLaunchSource = KeyguardBottomAreaView.CAMERA_LAUNCH_SOURCE_AFFORDANCE;

    /*PRIZE-KeyguardChargeAnimationView- liufan-2015-07-15-start*/
    //private KeyguardChargeAnimationView mKeyguardChargeAnimationView;
    private float downY;
    private boolean isMoveWhenKeyguardCharge = false;
    /*PRIZE-KeyguardChargeAnimationView- liufan-2015-07-15-end*/


    /*PRIZE-update for lockscreen clock view-liufan-2016-11-07-start*/
    private static boolean bA1Support = false;
    private ClockView mMtkClockView;
    /*PRIZE-update for lockscreen clock view-liufan-2016-11-07-end*/

	/*PRIZE-PowerExtendMode-yuhao-2016-12-10-start*/
    BaseStatusBarHeader mHeader_new;
    private QSPanel mQSPanel_new;
	/*PRIZE-PowerExtendMode-yuhao-2016-12-10-end*/

    /*-modify for haokan-liufan-2017-10-09-start-*/
    private HaokanLockView mHaokanView;
    private LinearLayout mBlurHaoKanLayout;

    public static final String MAGAZINE_TAG = "prize_magazine";
    public static final boolean DEBUG_MAGAZINE = true;
    public static boolean IS_ShowHaoKanView = true;//show haokan on/off

    public  static  boolean IS_ShowNotification_WhenShowHaoKan = true;//show notice

    private Runnable mHeadsUpExistenceChangedRunnable = new Runnable() {
        @Override
        public void run() {
            mHeadsUpAnimatingAway = false;
            notifyBarPanelExpansionChanged();
        }
    };
    private NotificationGroupManager mGroupManager;

    public NotificationPanelView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(!DEBUG);
        mFalsingManager = FalsingManager.getInstance(context);
    }

    public void setStatusBar(PhoneStatusBar bar) {
        mStatusBar = bar;
    }

    /*PRIZE-add open flashlight view- liufan-2015-12-04-start*/
    private TextView mOpenFlashlightTxt;
    /*PRIZE-add open flashlight view- liufan-2015-12-04-end*/

    //prize add by xiarui 2018-05-03 @{
    private boolean isOpenFaceId() {
        boolean isOpen = Settings.System.getInt(mContext.getContentResolver(), Settings.System.PRIZE_FACEID_SWITCH , 1) == 1;
        Log.d("flashlisght", "isOpenFaceId : " + isOpen);
        return isOpen;
    }

    private boolean isHaveFace() {
        boolean isHaveFace = SystemProperties.get("persist.sys.ishavaface", "no").equals("yes");
        Log.d("flashlisght", "isHaveFace : " + isHaveFace);
        return isHaveFace;
    }

    private boolean isFaceIdAvailable() {
        return isOpenFaceId() && isHaveFace();
    }
    //@}

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mKeyguardStatusBar = (KeyguardStatusBarView) findViewById(R.id.keyguard_header);
        mKeyguardStatusView = (KeyguardStatusView) findViewById(R.id.keyguard_status_view);

        /*PRIZE-update for lockscreen clock view-liufan-2016-11-07-start*/
        if (bA1Support) {
            mClockView = (TextView) findViewById(R.id.clock_view);
        } else {
            mMtkClockView = (ClockView) findViewById(R.id.clock_view);
        }
        /*PRIZE-update for lockscreen clock view-liufan-2016-11-07-end*/

        mNotificationContainerParent = (NotificationsQuickSettingsContainer)
                findViewById(R.id.notification_container_parent);
        mNotificationStackScroller = (NotificationStackScrollLayout)
                findViewById(R.id.notification_stack_scroller);
        mNotificationStackScroller.setOnHeightChangedListener(this);
        mNotificationStackScroller.setOverscrollTopChangedListener(this);
        mNotificationStackScroller.setOnEmptySpaceClickListener(this);
        mKeyguardBottomArea = (KeyguardBottomAreaView) findViewById(R.id.keyguard_bottom_area);
        /*PRIZE-open flashlight- liufan-2015-12-04-start*/
        mOpenFlashlightTxt = (TextView) findViewById(R.id.open_flashlight_txt);
        mOpenFlashlightTxt.setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View arg0) {
                if(mStatusBarState == StatusBarState.KEYGUARD && !mQsExpanded) {
			        /*PRIZE-PowerExtendMode-lihuangyuan-2018-01-10-start*/
                    if (PrizeOption.PRIZE_POWER_EXTEND_MODE && PowerManager.isSuperSaverMode()) {
                        Log.d("lhy", "SuperSaverMode can not open flash");
                        return true;
                    }
                    //prize add by xiarui 2018-05-03 @{
                    if (isFaceIdAvailable()) {
                        return true;
                    }
                    //@}
                    /*PRIZE-PowerExtendMode-lihuangyuan-2018-01-10-start*/
                    //prize-modify-by-zhongweilin
                    //int isSystemFlashOn = SystemProperties.getInt("persist.sys.prizeflash",0);
                    int isSystemFlashOn = Settings.System.getInt(getContext().getContentResolver(), Settings.System.PRIZE_FLASH_STATUS, -1);
                    if(isSystemFlashOn==3 || isSystemFlashOn==1){
                        closeFlash();
                    }else{
                        startFlash();
                    }
                    return true;
                }
                return false;
            }
        });
        /*PRIZE-open flashlight- liufan-2015-12-04-end*/
        mQsNavbarScrim = findViewById(R.id.qs_navbar_scrim);
        mAfforanceHelper = new KeyguardAffordanceHelper(this, getContext());
        mKeyguardBottomArea.setAffordanceHelper(mAfforanceHelper);
        mLastOrientation = getResources().getConfiguration().orientation;

        mQsAutoReinflateContainer =
                (AutoReinflateContainer) findViewById(R.id.qs_auto_reinflate_container);
        mQsAutoReinflateContainer.addInflateListener(new InflateListener() {
            @Override
            public void onInflated(View v) {
                mQsContainer = (QSContainer) v.findViewById(R.id.quick_settings_container);
                mQsContainer.setPanelView(NotificationPanelView.this);

				/*PRIZE-PowerExtendMode-yuhao-2016-12-10-start*/
				mHeader_new = mQsContainer.getHeader(); 
				mQSPanel_new = mQsContainer.getQsPanel();
				/*PRIZE-PowerExtendMode-yuhao-2016-12-10-end*/
				
                /*PRIZE-modify the head color of notification- liufan-2015-06-16-start*/
                if (VersionControl.CUR_VERSION == VersionControl.COLOR_BG_VER) {
                    //mHeader.setBackgroundResource(R.drawable.notification_header_bg_define);
                    mQsContainer.setBackgroundResource(R.drawable.qs_background_primary_define);
                }else if(VersionControl.CUR_VERSION == VersionControl.BLUR_BG_VER){
                    //mHeader.setBackground(null);
                    mQsContainer.setBackground(null);
                }
                //mKeyguardChargeAnimationView = (KeyguardChargeAnimationView)findViewById(R.id.keyguard_charge_animation_view);
                /*PRIZE-modify the head color of notification- liufan-2015-06-16-end*/
                mQsContainer.getHeader().findViewById(R.id.expand_indicator)
                        .setOnClickListener(NotificationPanelView.this);

                // recompute internal state when qspanel height changes
                mQsContainer.addOnLayoutChangeListener(new OnLayoutChangeListener() {
                    @Override
                    public void onLayoutChange(View v, int left, int top, int right, int bottom,
                            int oldLeft, int oldTop, int oldRight, int oldBottom) {
                        final int height = bottom - top;
                        final int oldHeight = oldBottom - oldTop;
                        if (height != oldHeight) {
                            onQsHeightChanged();
                        }
                    }
                });
                mNotificationStackScroller.setQsContainer(mQsContainer);
            }
        });
    }

    /*PRIZE-open and close flashlight method- liufan-2015-12-04-start*/
    private void startFlash() {  
        /*prize-modify-by-zhongweilin
        ContentValues values = new ContentValues();  
        values.put("flashstatus","3"); 
        getContext().getContentResolver().update(Uri.parse("content://com.android.flash/systemflashs"), values, null, null);
        */
        Settings.System.putInt(getContext().getContentResolver(), Settings.System.PRIZE_FLASH_STATUS, 3);
    }
    private void closeFlash() {  
        /*modify-by-zhongweilin
        ContentValues values = new ContentValues();  
        values.put("flashstatus","0"); 
        mContext.getContentResolver().update(Uri.parse("content://com.android.flash/systemflashs"), values, null, null);
        */
        Settings.System.putInt(getContext().getContentResolver(), Settings.System.PRIZE_FLASH_STATUS, 0);
    }
    /*PRIZE-open and close flashlight method- liufan-2015-12-04-end*/

    /*PRIZE-send the blurbitmap to the NotificationStackScrollLayout-liufan-2015-9-15-start*/
    public void setBottomBlurBg(Bitmap mWallPapaerBitmap){
        float fraction = getQsExpansionFraction();
        mNotificationStackScroller.setNotificationRowBg(mWallPapaerBitmap,fraction);
    }
    /*PRIZE-send the blurbitmap to the NotificationStackScrollLayout-liufan-2015-9-15-end*/

    /*-modify for haokan-liufan-2017-10-09-start-*/
    public static void debugMagazine(String msg){
        if(DEBUG_MAGAZINE){
            Log.e(MAGAZINE_TAG, msg);
        }
    }
    
    public String getMagazineViewClass(){
        if(mHaokanView != null){
            return mHaokanView.getMagazineViewClass();
        }
        return null;
    }

    public void initAndRefreshHaoKanViewVisibilty(){
        if(IS_ShowHaoKanView){
            initHaoKanView();
            setHaokanViewVisible(true);
            setNotificationUpperVisible(true);
            setNotificationUpperAlpha(1.0f);
        }else{
            setHaokanViewVisible(false);
            setNotificationUpperVisible(false);
        }
    }

    public void initHaoKanView(){
        if(mHaokanView == null){
            debugMagazine("initHaoKanView");
            mHaokanView = (HaokanLockView)findViewById(R.id.haokanview);
            mBlurHaoKanLayout = (LinearLayout)findViewById(R.id.blur_haokan_bg);
            mStatusBar.setHaoKanBlurLayout(mBlurHaoKanLayout);
            IS_ShowHaoKanView = true;
            IS_ShowNotification_WhenShowHaoKan = true;
            mHaokanView.init(getContext());
            mHaokanView.setNotificationPanelView(this);
            mHaokanView.setVisibility(View.GONE);

            if(mStatusBar!=null){
                StatusBarKeyguardViewManager mStatusBarKeyguardViewManager = mStatusBar.getStatusBarKeyguardViewManager();
                if(mStatusBarKeyguardViewManager!=null) mStatusBarKeyguardViewManager.setNotificationPanelView(this);
            }

            //registerScreenActionReceiver();
        }
    }

    /*private void registerScreenActionReceiver(){
        final IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        getContext().registerReceiver(receiver, filter);

    }*/

    public void onBackPressedForHaoKan(){
        debugMagazine("notification panelview onBackPressedForHaoKan");
        if(mHaokanView != null) mHaokanView.onKeyCodeBack();
    }

    public void setNotificationUpperVisible(boolean visible){
         if(mKeyguardStatusView==null||mKeyguardBottomArea==null||mNotificationStackScroller==null){
            return;
        }
        debugMagazine("NotificationPanelView setNotificationUpVisible = " + visible);
        if(visible) {
            mKeyguardStatusView.setVisibility(View.VISIBLE);
            mKeyguardBottomArea.setVisibility(View.VISIBLE);
            IS_ShowNotification_WhenShowHaoKan=true;
            mStatusBar.isShowBlurBgWhenLockscreen(false);
        }else {
            mKeyguardStatusView.setVisibility(View.GONE);
            mKeyguardBottomArea.setVisibility(View.GONE);
            //update for bugid:51597-liufan-2018-3-20
            if(IS_ShowNotification_WhenShowHaoKan != visible && mStatusBar.getBarState() == StatusBarState.KEYGUARD){
                mKeyguardBottomArea.stopFaceId();
            }
            //arrow animation
            mKeyguardBottomArea.setShowArrawImgViewFlag(false);
            mKeyguardBottomArea.stopShowArrowImgViewAnim();

            IS_ShowNotification_WhenShowHaoKan=false;
            if(mBlurHaoKanLayout!=null) mBlurHaoKanLayout.setVisibility(View.GONE);
        }
        if(mStatusBar!=null){
            mStatusBar.updateNoticeStates(IS_ShowNotification_WhenShowHaoKan);
        }

    }

    public void setNotificationUpperAlpha(float alpha){
        debugMagazine("NotificationPanelView setNotificationUpperAlpha=" + alpha);
        mKeyguardStatusView.setAlpha(alpha);
        mKeyguardBottomArea.setAlpha(alpha);
        mNotificationStackScroller.setAlpha(alpha);
    }

    public void notifyInverseNumToSystemui(int inverse){
        if(mStatusBar==null){
            return;
        }
        mStatusBar.notifyInverseNumToSystemui(inverse);
    }

    public boolean isShouldIgnoreInverse(){
        if(mStatusBar==null){
            return false;
        }
        return mStatusBar.isShouldIgnoreInverse();
    }

    public void setHaokanViewVisible(boolean show){
        if(mHaokanView==null){
            return;
        }
        debugMagazine("NotificationPanelView setHaokanViewVisible show =" + show);
        if(show){
            mHaokanView.setVisibility(View.VISIBLE);
        }else{
            mHaokanView.setVisibility(View.GONE);
        }
    }
    
    public void showKeyguardViewWhenCloseHaoKan(){
        if(mKeyguardStatusView != null && mKeyguardStatusView.getVisibility() != View.VISIBLE){
            mKeyguardStatusView.setVisibility(View.VISIBLE);
        }
        if(mKeyguardBottomArea != null && mKeyguardBottomArea.getVisibility() != View.VISIBLE){
            mKeyguardBottomArea.setVisibility(View.VISIBLE);
        }
        if(mKeyguardBottomArea != null) {
            mKeyguardBottomArea.updateLeftAffordanceIcon();

            mKeyguardBottomArea.setShowArrawImgViewFlag(false);
            mKeyguardBottomArea.stopShowArrowImgViewAnim();
        }
    }

    private boolean isWillOccluded = false;
    public void setWillOccluded(boolean occluded){
        isWillOccluded = occluded;
    }
    public boolean isWillOccluded(){
        return isWillOccluded;
    }
    public boolean isHaoKanViewShow(){
        if(mHaokanView != null && mHaokanView.getVisibility() == View.VISIBLE || isWillOccluded){
            return true;
        }
        return false;
    }

    public boolean isShowKeyguardStatusView(int statusBarState){
        boolean result = true;
        /*if(USE_VLIFE || USE_ZOOKING){
            result = false;
        }*/
        if(statusBarState == StatusBarState.SHADE){
            result = false;
        }else{
            if(mStatusBar.isUseHaoKan() && !IS_ShowNotification_WhenShowHaoKan){
                result = false;
            }
        }
        debugMagazine("isShowKeyguardStatusView result = " + result);
        return result;
    }
    
    public void startActivityBySystemUI(Intent intent){
        if(intent==null||mStatusBar==null){
            return;
        }
        mStatusBar.postStartActivityDismissingKeyguard(intent,500);
    }
    public void dismissKeyguard(){
        if(mStatusBar==null){
            return;
        }
        mStatusBar.dismissKeyguard();
    }

    public void  setTitleAndUrlName(String title,String urlName){
      /*if(mTitleTxt==null||mTitleUrlName==null){
            return;
        }
		if(title==null){
            mTitleTxt.setText("");
            mTitleUrlName.setText("");
            return;
        }
        mTitleTxt.setText(title);
        if(urlName!=null){
            mTitleUrlName.setText(urlName);
        }*/

    }
    public void updateScreenOn(){
        if(!(PrizeOption.PRIZE_SYSTEMUI_HAOKAN_SCREENVIEW && mStatusBar.isOpenMagazine())){
            debugMagazine("updateScreenOn not open magazine");
            return;
        }
        if(mHaokanView==null){
            return;
        }
        debugMagazine("updateScreenOn");
        mHaokanView.screenOn();
        if(mStatusBar !=null) {
            mStatusBar.isShowBlurBgWhenLockscreen(false);
        }
        if(mKeyguardBottomArea !=null) {
            mKeyguardBottomArea.setShowArrawImgViewFlag(true);
            mKeyguardBottomArea.decideShowArrowImgView();
        }

    }
    public  void updateScreenOff(){
        if(!(PrizeOption.PRIZE_SYSTEMUI_HAOKAN_SCREENVIEW && mStatusBar.isOpenMagazine())){
            debugMagazine("updateScreenOff not open magazine");
            return;
        }
        if(mHaokanView==null){
            return;
        }
        /*PRIZE-modify for bugid:54922-liufan-2018-05-07-start*/
        int state = mStatusBar.getBarState();
        debugMagazine("updateScreenOff, state = " + state);
        if(state == StatusBarState.SHADE){
            debugMagazine("updateScreenOff : don't show magazine view when state is 0");
            return ;
        }
        /*PRIZE-modify for bugid:54922-liufan-2018-05-07-end*/
        mHaokanView.screenOff();
        IS_ShowHaoKanView = true;
        setHaokanViewVisible(true);
        setNotificationUpperVisible(true);
        if(mKeyguardBottomArea !=null) {
            mKeyguardBottomArea.setShowArrawImgViewFlag(false);
            mKeyguardBottomArea.stopShowArrowImgViewAnim();
        }
    }

    public void setShowHaokanFunction(boolean show){
        debugMagazine("setShowHaokanFunction show : " + show + ", IS_ShowHaoKanView : " + IS_ShowHaoKanView);
        IS_ShowHaoKanView=show;
        initAndRefreshHaoKanViewVisibilty();
        mKeyguardBottomArea.refreshView();
    }

    public KeyguardBottomAreaView getKeyguardBottomAreaView(){
        return mKeyguardBottomArea;
    }

    public String blurImgKeyguardUrl = null;
    public Bitmap blurImgKeyguard = null;

    public Bitmap getCurrentImage() {
        if (mHaokanView == null) {
            return null;
        }
        try {
            String tempUrl = mHaokanView.getCurrentImageUri();
            debugMagazine("getCurrentImageUri() = " + tempUrl);
            if (!tempUrl.equals("")) {
                blurImgKeyguardUrl = tempUrl;
                /*if (tempUrl.startsWith("android")) {
                    Context context = mContext.createPackageContext(mHaokanView.getPackage(), Context.CONTEXT_INCLUDE_CODE
                            | Context.CONTEXT_IGNORE_SECURITY);
                    InputStream open = context.getAssets().open(tempUrl);
                    blurImgKeyguard = BitmapFactory.decodeStream(open);
                } else*/
                if(tempUrl.startsWith("/storage")){
                    blurImgKeyguard = BitmapFactory.decodeFile(tempUrl);
                } else {
                    Context context = mContext.createPackageContext(mHaokanView.getPackage(), Context.CONTEXT_INCLUDE_CODE
                            | Context.CONTEXT_IGNORE_SECURITY);
                    InputStream stream = null;
                    try {
                        stream = context.getContentResolver().openInputStream(Uri.parse(tempUrl));
                        blurImgKeyguard = BitmapFactory.decodeStream(stream);
                    } catch (Exception e) {
                        debugMagazine("Unable to open content: " + tempUrl + "\n e = " + e);
                    } finally {
                        if (stream != null) {
                            try {
                                stream.close();
                            } catch (Exception e) {
                                debugMagazine("Unable to close content: " + tempUrl + "\n e = " + e);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        debugMagazine("getCurrentImage mBitmap = " + blurImgKeyguard);
        return blurImgKeyguard;
    }

    public void showMagazineView(){
        if(PrizeOption.PRIZE_SYSTEMUI_HAOKAN_SCREENVIEW && (mStatusBar != null && mStatusBar.isUseHaoKan()
            && mStatusBar.getBarState() != StatusBarState.SHADE)){
            debugMagazine("NotificationPanelView showMagazineView");
            mStatusBar.showHaoKanWallPaperInBackdropBack();
        }
    }

    public void refreshMagazineDescriptionColor(int color){
        if(mHaokanView != null){
            mHaokanView.refreshMagazineDescriptionColor(color);
        }
    }

    public void clickDetails(Runnable runnable) {
        if(mStatusBar != null){
            debugMagazine("NotificationPanelView clickDetails");
            mStatusBar.executeRunnableDismissingKeyguard(runnable,null,true,true,true);
        }
    }
    /*-modify for haokan-liufan-2017-10-09-end-*/

    @Override
    protected void loadDimens() {
        super.loadDimens();
        mFlingAnimationUtils = new FlingAnimationUtils(getContext(), 0.4f);
        mStatusBarMinHeight = getResources().getDimensionPixelSize(
                com.android.internal.R.dimen.status_bar_height);
        mQsPeekHeight = getResources().getDimensionPixelSize(R.dimen.qs_peek_height);
        mNotificationsHeaderCollideDistance =
                getResources().getDimensionPixelSize(R.dimen.header_notifications_collide_distance);
        mUnlockMoveDistance = getResources().getDimensionPixelOffset(R.dimen.unlock_move_distance);
        mClockPositionAlgorithm.loadDimens(getResources());
        mNotificationScrimWaitDistance =
                getResources().getDimensionPixelSize(R.dimen.notification_scrim_wait_distance);
        mQsFalsingThreshold = getResources().getDimensionPixelSize(
                R.dimen.qs_falsing_threshold);
        mPositionMinSideMargin = getResources().getDimensionPixelSize(
                R.dimen.notification_panel_min_side_margin);
        mMaxFadeoutHeight = getResources().getDimensionPixelSize(
                R.dimen.max_notification_fadeout_height);
    }

    public void updateResources() {
        int panelGravity = getResources().getInteger(R.integer.notification_panel_layout_gravity);
        /*PRIZE-widen the notification's width- liufan-2015-06-12-start*/
        int panelWidth = getResources().getDimensionPixelSize(R.dimen.notification_panel_width);
        if (VersionControl.CUR_VERSION == VersionControl.BLUR_BG_VER) {
            if(panelWidth > 0){
                panelWidth += 120;
            }
        }
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mQsAutoReinflateContainer.getLayoutParams();
        if (lp.width != panelWidth) {
            lp.width = panelWidth;
            lp.gravity = panelGravity;
            mQsAutoReinflateContainer.setLayoutParams(lp);
            mQsContainer.post(mUpdateHeader);
        }

        /*PRIZE-widen the notification's width- liufan-2015-06-12-end*/ 
        lp = (FrameLayout.LayoutParams) mNotificationStackScroller.getLayoutParams();
        if (lp.width != panelWidth) {
            lp.width = panelWidth;
            lp.gravity = panelGravity;
            mNotificationStackScroller.setLayoutParams(lp);
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        // Update Clock Pivot
        mKeyguardStatusView.setPivotX(getWidth() / 2);

        /*PRIZE-update for lockscreen clock view-liufan-2016-11-07-start*/
        if (bA1Support) {
            mKeyguardStatusView.setPivotY((FONT_HEIGHT - CAP_HEIGHT) / 2048f
                    * mClockView.getTextSize());
        } else {
		    mKeyguardStatusView.setPivotY((FONT_HEIGHT - CAP_HEIGHT) / 2048f
			        * mMtkClockView.getHeight());
        }
        /*PRIZE-update for lockscreen clock view-liufan-2016-11-07-end*/

        // Calculate quick setting heights.
        int oldMaxHeight = mQsMaxExpansionHeight;
        mQsMinExpansionHeight = mKeyguardShowing ? 0 : mQsContainer.getQsMinExpansionHeight();
        mQsMaxExpansionHeight = mQsContainer.getDesiredHeight();
        positionClockAndNotifications();
        if (mQsExpanded && mQsFullyExpanded) {
            mQsExpansionHeight = mQsMaxExpansionHeight;
            requestScrollerTopPaddingUpdate(false /* animate */);
            requestPanelHeightUpdate();

            // Size has changed, start an animation.
            if (mQsMaxExpansionHeight != oldMaxHeight) {
                startQsSizeChangeAnimation(oldMaxHeight, mQsMaxExpansionHeight);
            }
        } else if (!mQsExpanded) {
            setQsExpansion(mQsMinExpansionHeight + mLastOverscroll);
        }
        updateExpandedHeight(getExpandedHeight());
        updateHeader();

        // If we are running a size change animation, the animation takes care of the height of
        // the container. However, if we are not animating, we always need to make the QS container
        // the desired height so when closing the QS detail, it stays smaller after the size change
        // animation is finished but the detail view is still being animated away (this animation
        // takes longer than the size change animation).
        if (mQsSizeChangeAnimator == null) {
            mQsContainer.setHeightOverride(mQsContainer.getDesiredHeight());
        }
        updateMaxHeadsUpTranslation();
    }

    private void startQsSizeChangeAnimation(int oldHeight, final int newHeight) {
        if (mQsSizeChangeAnimator != null) {
            oldHeight = (int) mQsSizeChangeAnimator.getAnimatedValue();
            mQsSizeChangeAnimator.cancel();
        }
        mQsSizeChangeAnimator = ValueAnimator.ofInt(oldHeight, newHeight);
        mQsSizeChangeAnimator.setDuration(300);
        mQsSizeChangeAnimator.setInterpolator(Interpolators.FAST_OUT_SLOW_IN);
        mQsSizeChangeAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                requestScrollerTopPaddingUpdate(false /* animate */);
                requestPanelHeightUpdate();
                int height = (int) mQsSizeChangeAnimator.getAnimatedValue();
                mQsContainer.setHeightOverride(height);
            }
        });
        mQsSizeChangeAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mQsSizeChangeAnimator = null;
            }
        });
        mQsSizeChangeAnimator.start();
    }

    /**
     * Positions the clock and notifications dynamically depending on how many notifications are
     * showing.
     */
    private void positionClockAndNotifications() {
        boolean animate = mNotificationStackScroller.isAddOrRemoveAnimationPending();
        int stackScrollerPadding;
        if (mStatusBarState != StatusBarState.KEYGUARD) {
            stackScrollerPadding = mQsContainer.getHeader().getHeight() + mQsPeekHeight;
            mTopPaddingAdjustment = 0;
        } else {
            mClockPositionAlgorithm.setup(
                    mStatusBar.getMaxKeyguardNotifications(),
                    getMaxPanelHeight(),
                    getExpandedHeight(),
                    mNotificationStackScroller.getNotGoneChildCount(),
                    getHeight(),
                    mKeyguardStatusView.getHeight(),
                    mEmptyDragAmount);
            mClockPositionAlgorithm.run(mClockPositionResult);
            /*PRIZE-modify the position of the time layout- liyao-2015-05-20-start*/
            if(! FeatureOption.PRIZE_QS_SORT){
                if (animate || mClockAnimator != null) {
                    startClockAnimation(mClockPositionResult.clockY);
                } else {
                    mKeyguardStatusView.setY(mClockPositionResult.clockY);
                }
            }
            /*PRIZE-modify the position of the time layout- liyao-2015-05-20-end*/
            updateClock(mClockPositionResult.clockAlpha, mClockPositionResult.clockScale);
            stackScrollerPadding = mClockPositionResult.stackScrollerPadding;
            mTopPaddingAdjustment = mClockPositionResult.stackScrollerPaddingAdjustment;
        }
        mNotificationStackScroller.setIntrinsicPadding(stackScrollerPadding);
        requestScrollerTopPaddingUpdate(animate);
    }

    /**
     * @param maximum the maximum to return at most
     * @return the maximum keyguard notifications that can fit on the screen
     */
    public int computeMaxKeyguardNotifications(int maximum) {
        float minPadding = mClockPositionAlgorithm.getMinStackScrollerPadding(getHeight(),
                mKeyguardStatusView.getHeight());
        int notificationPadding = Math.max(1, getResources().getDimensionPixelSize(
                R.dimen.notification_divider_height));
        final int overflowheight = getResources().getDimensionPixelSize(
                R.dimen.notification_summary_height);
        float bottomStackSize = mNotificationStackScroller.getKeyguardBottomStackSize();
        float availableSpace = mNotificationStackScroller.getHeight() - minPadding - overflowheight
                - bottomStackSize;
        int count = 0;
        for (int i = 0; i < mNotificationStackScroller.getChildCount(); i++) {
            ExpandableView child = (ExpandableView) mNotificationStackScroller.getChildAt(i);
            if (!(child instanceof ExpandableNotificationRow)) {
                continue;
            }
            ExpandableNotificationRow row = (ExpandableNotificationRow) child;
            boolean suppressedSummary = mGroupManager.isSummaryOfSuppressedGroup(
                    row.getStatusBarNotification());
            if (suppressedSummary) {
                continue;
            }
            if (!mStatusBar.shouldShowOnKeyguard(row.getStatusBarNotification())) {
                continue;
            }
            if (row.isRemoved()) {
                continue;
            }
            availableSpace -= child.getMinHeight() + notificationPadding;
            if (availableSpace >= 0 && count < maximum) {
                count++;
            } else {
                return count;
            }
        }
        return count;
    }

    private void startClockAnimation(int y) {
        if (mClockAnimationTarget == y) {
            return;
        }
        mClockAnimationTarget = y;
        getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                getViewTreeObserver().removeOnPreDrawListener(this);
                if (mClockAnimator != null) {
                    mClockAnimator.removeAllListeners();
                    mClockAnimator.cancel();
                }
                mClockAnimator = ObjectAnimator
                        .ofFloat(mKeyguardStatusView, View.Y, mClockAnimationTarget);
                mClockAnimator.setInterpolator(Interpolators.FAST_OUT_SLOW_IN);
                mClockAnimator.setDuration(StackStateAnimator.ANIMATION_DURATION_STANDARD);
                mClockAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        mClockAnimator = null;
                        mClockAnimationTarget = -1;
                    }
                });
                mClockAnimator.start();
                return true;
            }
        });
    }

    private void updateClock(float alpha, float scale) {
        if (!mKeyguardStatusViewAnimating) {
            mKeyguardStatusView.setAlpha(alpha);

        }
        mKeyguardStatusView.setScaleX(scale);
        mKeyguardStatusView.setScaleY(scale);
    }

    public void animateToFullShade(long delay) {
        mAnimateNextTopPaddingChange = true;
        mNotificationStackScroller.goToFullShade(delay);
        requestLayout();
    }

    public void setQsExpansionEnabled(boolean qsExpansionEnabled) {
        mQsExpansionEnabled = qsExpansionEnabled;
        mQsContainer.setHeaderClickable(qsExpansionEnabled);
    }

    @Override
    public void resetViews() {
        mIsLaunchTransitionFinished = false;
        mBlockTouches = false;
        mUnlockIconActive = false;
        if (!mLaunchingAffordance) {
            mAfforanceHelper.reset(false);
            mLastCameraLaunchSource = KeyguardBottomAreaView.CAMERA_LAUNCH_SOURCE_AFFORDANCE;
        }
        closeQs();
        mStatusBar.dismissPopups();
        mNotificationStackScroller.setOverScrollAmount(0f, true /* onTop */, false /* animate */,
                true /* cancelAnimators */);
        mNotificationStackScroller.resetScrollPosition();
    }

    public void closeQs() {
        cancelQsAnimation();
        setQsExpansion(mQsMinExpansionHeight);
    }

    public void animateCloseQs() {
        if (mQsExpansionAnimator != null) {
            if (!mQsAnimatorExpand) {
                return;
            }
            float height = mQsExpansionHeight;
            mQsExpansionAnimator.cancel();
            setQsExpansion(height);
        }
        flingSettings(0 /* vel */, false);
    }

    public void openQs() {
        cancelQsAnimation();
        if (mQsExpansionEnabled) {
            setQsExpansion(mQsMaxExpansionHeight);
        }
    }

    public void expandWithQs() {
        if (mQsExpansionEnabled) {
            mQsExpandImmediate = true;
        }
        expand(true /* animate */);
    }

    @Override
    public void fling(float vel, boolean expand) {
        GestureRecorder gr = ((PhoneStatusBarView) mBar).mBar.getGestureRecorder();
        if (gr != null) {
            gr.tag("fling " + ((vel > 0) ? "open" : "closed"), "notifications,v=" + vel);
        }
        super.fling(vel, expand);
    }

    @Override
    protected void flingToHeight(float vel, boolean expand, float target,
            float collapseSpeedUpFactor, boolean expandBecauseOfFalsing) {
        mHeadsUpTouchHelper.notifyFling(!expand);
        setClosingWithAlphaFadeout(!expand
                && mNotificationStackScroller.getFirstChildIntrinsicHeight() <= mMaxFadeoutHeight
                && getFadeoutAlpha() == 1.0f);
        super.flingToHeight(vel, expand, target, collapseSpeedUpFactor, expandBecauseOfFalsing);
    }

    @Override
    public boolean dispatchPopulateAccessibilityEventInternal(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            event.getText().add(getKeyguardOrLockScreenString());
            mLastAnnouncementWasQuickSettings = false;
            return true;
        }
        return super.dispatchPopulateAccessibilityEventInternal(event);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (mBlockTouches || mQsContainer.isCustomizing()) {
            return false;
        }
        initDownStates(event);
        if (mHeadsUpTouchHelper.onInterceptTouchEvent(event)) {
            mIsExpansionFromHeadsUp = true;
            MetricsLogger.count(mContext, COUNTER_PANEL_OPEN, 1);
            MetricsLogger.count(mContext, COUNTER_PANEL_OPEN_PEEK, 1);
            return true;
        }
        if (!isFullyCollapsed() && onQsIntercept(event)) {
            return true;
        }
        return super.onInterceptTouchEvent(event);
    }

    /*PRIZE-for phone top notification bg alpha- liufan-2016-09-20-start*/
    private boolean isWhenCollapseAllPanels;
    /*PRIZE-for phone top notification bg alpha- liufan-2016-09-20-end*/

    private boolean onQsIntercept(MotionEvent event) {
        /*PRIZE-for phone top notification bg alpha- liufan-2016-09-20-start*/
        if(PhoneStatusBar.isCollapseAllPanelsAnim || isWhenCollapseAllPanels){
            isWhenCollapseAllPanels = true;
            if(event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL){
                isWhenCollapseAllPanels = false;
                //add for bugid:53665-liufan-2018-3-24
                PhoneStatusBar.isCollapseAllPanelsAnim = false;
                Log.d(TAG,"onQsIntercept isCollapseAllPanelsAnim trun false");
            }
            return true;
        }
        /*PRIZE-for phone top notification bg alpha- liufan-2016-09-20-end*/
        int pointerIndex = event.findPointerIndex(mTrackingPointer);
        if (pointerIndex < 0) {
            pointerIndex = 0;
            mTrackingPointer = event.getPointerId(pointerIndex);
        }
        final float x = event.getX(pointerIndex);
        final float y = event.getY(pointerIndex);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mIntercepting = true;
                mInitialTouchY = y;
                mInitialTouchX = x;
                initVelocityTracker();
                trackMovement(event);
                if (shouldQuickSettingsIntercept(mInitialTouchX, mInitialTouchY, 0)) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                if (mQsExpansionAnimator != null) {
                    onQsExpansionStarted();
                    mInitialHeightOnTouch = mQsExpansionHeight;
                    mQsTracking = true;
                    mIntercepting = false;
                    mNotificationStackScroller.removeLongPressCallback();
                }
                break;
            case MotionEvent.ACTION_POINTER_UP:
                final int upPointer = event.getPointerId(event.getActionIndex());
                if (mTrackingPointer == upPointer) {
                    // gesture is ongoing, find a new pointer to track
                    final int newIndex = event.getPointerId(0) != upPointer ? 0 : 1;
                    mTrackingPointer = event.getPointerId(newIndex);
                    mInitialTouchX = event.getX(newIndex);
                    mInitialTouchY = event.getY(newIndex);
                }
                break;

            case MotionEvent.ACTION_MOVE:
                final float h = y - mInitialTouchY;
                trackMovement(event);
                if (mQsTracking) {

                    // Already tracking because onOverscrolled was called. We need to update here
                    // so we don't stop for a frame until the next touch event gets handled in
                    // onTouchEvent.
                    setQsExpansion(h + mInitialHeightOnTouch);
                    trackMovement(event);
                    mIntercepting = false;
                    return true;
                }
                if (Math.abs(h) > mTouchSlop && Math.abs(h) > Math.abs(x - mInitialTouchX)
                        && shouldQuickSettingsIntercept(mInitialTouchX, mInitialTouchY, h)) {
                    mQsTracking = true;
                    onQsExpansionStarted();
                    notifyExpandingFinished();
                    mInitialHeightOnTouch = mQsExpansionHeight;
                    mInitialTouchY = y;
                    mInitialTouchX = x;
                    mIntercepting = false;
                    mNotificationStackScroller.removeLongPressCallback();
                    return true;
                }
                break;

            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                trackMovement(event);
                if (mQsTracking) {
                    flingQsWithCurrentVelocity(y,
                            event.getActionMasked() == MotionEvent.ACTION_CANCEL);
                    mQsTracking = false;
                }
                mIntercepting = false;
                break;
        }
        return false;
    }

    @Override
    protected boolean isInContentBounds(float x, float y) {
        float stackScrollerX = mNotificationStackScroller.getX();
        return !mNotificationStackScroller.isBelowLastNotification(x - stackScrollerX, y)
                && stackScrollerX < x && x < stackScrollerX + mNotificationStackScroller.getWidth();
    }

    private void initDownStates(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            /*PRIZE-for phone top notification bg alpha- liufan-2016-09-20-start*/
            isWhenCollapseAllPanels = false;
            /*PRIZE-for phone top notification bg alpha- liufan-2016-09-20-end*/
            mOnlyAffordanceInThisMotion = false;
            mQsTouchAboveFalsingThreshold = mQsFullyExpanded;
            mDozingOnDown = isDozing();
            mCollapsedOnDown = isFullyCollapsed();
            mListenForHeadsUp = mCollapsedOnDown && mHeadsUpManager.hasPinnedHeadsUp();
        }
    }

    private void flingQsWithCurrentVelocity(float y, boolean isCancelMotionEvent) {
        float vel = getCurrentVelocity();
        final boolean expandsQs = flingExpandsQs(vel);
        if (expandsQs) {
            logQsSwipeDown(y);
        }
        /*PRIZE-Don't allow swipe when lockscreen-liufan-2015-09-03-start*/
        if(VersionControl.isAllowDropDown(getContext())){
            flingSettings(vel, flingExpandsQs(vel) && !isCancelMotionEvent);
        }else{
            if(mStatusBar.getBarState() != StatusBarState.KEYGUARD){
                flingSettings(vel, flingExpandsQs(vel) && !isCancelMotionEvent);
            } else {
                flingSettings(vel, false);
            }
        }
        /*PRIZE-Don't allow swipe when lockscreen-liufan-2015-09-03-end*/
    }

    private void logQsSwipeDown(float y) {
        float vel = getCurrentVelocity();
        final int gesture = mStatusBarState == StatusBarState.KEYGUARD
                ? EventLogConstants.SYSUI_LOCKSCREEN_GESTURE_SWIPE_DOWN_QS
                : EventLogConstants.SYSUI_SHADE_GESTURE_SWIPE_DOWN_QS;
        EventLogTags.writeSysuiLockscreenGesture(
                gesture,
                (int) ((y - mInitialTouchY) / mStatusBar.getDisplayDensity()),
                (int) (vel / mStatusBar.getDisplayDensity()));
    }

    private boolean flingExpandsQs(float vel) {
        if (isFalseTouch()) {
            return false;
        }
        if (Math.abs(vel) < mFlingAnimationUtils.getMinVelocityPxPerSecond()) {
            return getQsExpansionFraction() > 0.5f;
        } else {
            return vel > 0;
        }
    }

    private boolean isFalseTouch() {
        if (!needsAntiFalsing()) {
            return false;
        }
        if (mFalsingManager.isClassiferEnabled()) {
            return mFalsingManager.isFalseTouch();
        }
        return !mQsTouchAboveFalsingThreshold;
    }

    private float getQsExpansionFraction() {
        return Math.min(1f, (mQsExpansionHeight - mQsMinExpansionHeight)
                / (getTempQsMaxExpansion() - mQsMinExpansionHeight));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        Log.d(TOUCH_TAG, "NotificationPanelView---event:" +event);
        if(!VersionControl.isNewLockscreenAllowDropDown(getContext())){
            Log.d(TOUCH_TAG, "NotificationPanelView---onTouchEvent not allow drop down");
            return false;
        }
        if (mBlockTouches || mQsContainer.isCustomizing()) {
            Log.d(TOUCH_TAG, "NotificationPanelView---onTouchEvent mBlockTouches = " + mBlockTouches
                + ", mQsContainer.isCustomizing() = " + mQsContainer.isCustomizing());
            return false;
        }
        /*prize-control drop down, bugid：18388-liufan-2016-08-01-start*/
        if(HeadsUpTouchHelper.isSpecialAppHeadsUpNotification){
            if(event.getActionMasked() == MotionEvent.ACTION_CANCEL || event.getActionMasked() == MotionEvent.ACTION_UP){
                HeadsUpTouchHelper.isSpecialAppHeadsUpNotification = false;
                Log.d(TAG,"isSpecialAppHeadsUpNotification------->turn false");
            }
            return true;
        }
        /*prize-control drop down, bugid：18388-liufan-2016-08-01-end*/
        initDownStates(event);
        if (mListenForHeadsUp && !mHeadsUpTouchHelper.isTrackingHeadsUp()
                && mHeadsUpTouchHelper.onInterceptTouchEvent(event)) {
            mIsExpansionFromHeadsUp = true;
            MetricsLogger.count(mContext, COUNTER_PANEL_OPEN_PEEK, 1);
        }
        if ((!mIsExpanding || mHintAnimationRunning)
                && !mQsExpanded
                && mStatusBar.getBarState() != StatusBarState.SHADE) {
            mAfforanceHelper.onTouchEvent(event);
        }
        /*PRIZE-dismiss KeyguardChargeAnimationView when move happened at lockscreen-liufan-2015-07-14-start*/
        if(event.getActionMasked() == MotionEvent.ACTION_DOWN){
            downY = event.getY();
            isMoveWhenKeyguardCharge = false;
        } else if(event.getActionMasked() == MotionEvent.ACTION_MOVE){
            if((event.getY()-downY) < 0){
                isMoveWhenKeyguardCharge = true;
                //mStatusBar.isShowKeyguardChargingAnimation(false,false,false);
            }
        }else if(event.getActionMasked() == MotionEvent.ACTION_UP && !mIsLaunchTransitionRunning){
            if(isMoveWhenKeyguardCharge){
                //mStatusBar.isShowKeyguardChargingAnimation(true,true,false);
            }
        }
        /*PRIZE-dismiss KeyguardChargeAnimationView when move happened at lockscreen-liufan-2015-07-14-end*/
        if (mOnlyAffordanceInThisMotion) {
            Log.d(TOUCH_TAG, "NotificationPanelView---onTouchEvent mOnlyAffordanceInThisMotion is true");
            return true;
        }
        mHeadsUpTouchHelper.onTouchEvent(event);
        /*PRIZE-for phone top notification bg alpha- liufan-2016-09-20-start*/
        if(PhoneStatusBar.isCollapseAllPanelsAnim || isWhenCollapseAllPanels){
            isWhenCollapseAllPanels = true;
            if(mQsExpansionHeight != mQsMinExpansionHeight){
                animateCloseQs();
            }
            if(event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL){
                isWhenCollapseAllPanels = false;
                //add for bugid:53665-liufan-2018-3-24
                PhoneStatusBar.isCollapseAllPanelsAnim = false;
                Log.d(TAG,"onTouchEvent isCollapseAllPanelsAnim trun false");
                mQsTracking = false;
                onTrackingStopped(false);
            }
            Log.d(TOUCH_TAG, "NotificationPanelView---onTouchEvent isWhenCollapseAllPanels = " + isWhenCollapseAllPanels);
            return true;
        }
        /*PRIZE-for phone top notification bg alpha- liufan-2016-09-20-end*/
        if (!mHeadsUpTouchHelper.isTrackingHeadsUp() && handleQsTouch(event)) {
            Log.d(TOUCH_TAG, "NotificationPanelView---onTouchEvent isTrackingHeadsUp & handleQsTouch(event)");
            return true;
        }

        /*PRIZE-dismiss the blur layout when lockscreen-liufan-2015-06-15-start*/
        if (VersionControl.CUR_VERSION == VersionControl.BLUR_BG_VER) {
            if (event.getActionMasked() == MotionEvent.ACTION_CANCEL
                    || event.getActionMasked() == MotionEvent.ACTION_UP){
                float fraction = getQsExpansionFraction();
                if(mStatusBar.getBarState() == StatusBarState.KEYGUARD && fraction == 0 && !mQsExpanded){
                    mStatusBar.cancelNotificationBackground();
                    /**PRIZE-haokanscreen iteration one-liufan-2016-09-13-start */
                    //setKeyguardStatusViewVisibility(true);
                    /**PRIZE-haokanscreen iteration one-liufan-2016-09-13-end */
                }
            }
        }
        /*PRIZE-dismiss the blur layout when lockscreen-liufan-2015-06-15-end*/
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN && isFullyCollapsed()) {
            MetricsLogger.count(mContext, COUNTER_PANEL_OPEN, 1);
            updateVerticalPanelPosition(event.getX());
        }
        super.onTouchEvent(event);
		/**PRIZE-haokanscreen iteration one-liufan-2016-06-23-start */
		//if(PrizeOption.PRIZE_SYSTEMUI_HAOKAN_SCREENVIEW && mStatusBar.getBarState()== StatusBarState.KEYGUARD && ScreenView.isloaded && mStatusBar.isUseHaoKan()){
        //    return false;
        //} else {
            return true;
        //}
        /**PRIZE-haokanscreen iteration one-liufan-2016-06-23-end */
    }

    private boolean handleQsTouch(MotionEvent event) {
        final int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN && getExpandedFraction() == 1f
                && mStatusBar.getBarState() != StatusBarState.KEYGUARD && !mQsExpanded
                && mQsExpansionEnabled) {

            // Down in the empty area while fully expanded - go to QS.
            mQsTracking = true;
            mConflictingQsExpansionGesture = true;
            onQsExpansionStarted();
            mInitialHeightOnTouch = mQsExpansionHeight;
            mInitialTouchY = event.getX();
            mInitialTouchX = event.getY();
        }
        if (!isFullyCollapsed()) {
            handleQsDown(event);
        }
        if (!mQsExpandImmediate && mQsTracking) {
            onQsTouch(event);
            if (!mConflictingQsExpansionGesture) {
                return true;
            }
        }
        if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
            mConflictingQsExpansionGesture = false;
        }
        if (action == MotionEvent.ACTION_DOWN && isFullyCollapsed()
                && mQsExpansionEnabled) {
            mTwoFingerQsExpandPossible = true;
        }
        if (mTwoFingerQsExpandPossible && isOpenQsEvent(event)
                && event.getY(event.getActionIndex()) < mStatusBarMinHeight) {
            MetricsLogger.count(mContext, COUNTER_PANEL_OPEN_QS, 1);
            mQsExpandImmediate = true;
            requestPanelHeightUpdate();

            // Normally, we start listening when the panel is expanded, but here we need to start
            // earlier so the state is already up to date when dragging down.
            setListening(true);
        }
        return false;
    }

    
    /*PRIZE-pull to quick setting when there is no notification-liufan-2015-06-10-start*/
    public boolean getQsExpandImmediate(){
        return mQsExpandImmediate;
    }
    public void setQsExpandImmediate(boolean isQsExpandImmediate){
        mQsExpandImmediate = isQsExpandImmediate;
    }
    public void expandImmediateSetting(){
        if(mStatusBar.getBarState() == StatusBarState.SHADE && mExpandedHeight == 0 && mQsExpansionEnabled){
            if(mShadeEmpty && !mQsExpanded){
                mQsExpandImmediate = true;
                requestPanelHeightUpdate();
                setListening(true);
            }
        }
    }
    /*PRIZE-pull to quick setting when there is no notification-liufan-2015-06-10-end*/

    /*PRIZE-weather pull to quick setting immediately-liufan-2015-09-16-start*/
    public boolean isShouldExpandQs(){
        if(mStatusBar.getBarState() == StatusBarState.SHADE && mExpandedHeight == 0 && mQsExpansionEnabled){
            if(mShadeEmpty && !mQsExpanded){
                return true;
            }
        }
        return false;
    }
    /*PRIZE-weather pull to quick setting immediately-liufan-2015-09-16-end*/

    private boolean isInQsArea(float x, float y) {
        return (x >= mQsAutoReinflateContainer.getX()
                && x <= mQsAutoReinflateContainer.getX() + mQsAutoReinflateContainer.getWidth())
                && (y <= mNotificationStackScroller.getBottomMostNotificationBottom()
                || y <= mQsContainer.getY() + mQsContainer.getHeight());
    }

    private boolean isOpenQsEvent(MotionEvent event) {
        final int pointerCount = event.getPointerCount();
        final int action = event.getActionMasked();

        final boolean twoFingerDrag = action == MotionEvent.ACTION_POINTER_DOWN
                && pointerCount == 2;

        final boolean stylusButtonClickDrag = action == MotionEvent.ACTION_DOWN
                && (event.isButtonPressed(MotionEvent.BUTTON_STYLUS_PRIMARY)
                        || event.isButtonPressed(MotionEvent.BUTTON_STYLUS_SECONDARY));

        final boolean mouseButtonClickDrag = action == MotionEvent.ACTION_DOWN
                && (event.isButtonPressed(MotionEvent.BUTTON_SECONDARY)
                        || event.isButtonPressed(MotionEvent.BUTTON_TERTIARY));

        return twoFingerDrag || stylusButtonClickDrag || mouseButtonClickDrag;
    }

    private void handleQsDown(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN
                && shouldQuickSettingsIntercept(event.getX(), event.getY(), -1)) {
            mFalsingManager.onQsDown();
            mQsTracking = true;
            onQsExpansionStarted();
            mInitialHeightOnTouch = mQsExpansionHeight;
            mInitialTouchY = event.getX();
            mInitialTouchX = event.getY();

            // If we interrupt an expansion gesture here, make sure to update the state correctly.
            notifyExpandingFinished();
        }
    }

    @Override
    protected boolean flingExpands(float vel, float vectorVel, float x, float y) {
        boolean expands = super.flingExpands(vel, vectorVel, x, y);

        // If we are already running a QS expansion, make sure that we keep the panel open.
        if (mQsExpansionAnimator != null) {
            expands = true;
        }
        return expands;
    }

    @Override
    protected boolean hasConflictingGestures() {
        return mStatusBar.getBarState() != StatusBarState.SHADE;
    }

    @Override
    protected boolean shouldGestureIgnoreXTouchSlop(float x, float y) {
        return !mAfforanceHelper.isOnAffordanceIcon(x, y);
    }

    private void onQsTouch(MotionEvent event) {
        int pointerIndex = event.findPointerIndex(mTrackingPointer);
        if (pointerIndex < 0) {
            pointerIndex = 0;
            mTrackingPointer = event.getPointerId(pointerIndex);
        }
        final float y = event.getY(pointerIndex);
        final float x = event.getX(pointerIndex);
        final float h = y - mInitialTouchY;

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                /*PRIZE-add for bugid: 34326-zhudaopeng-2017-06-13-Start*/
                mEvent = event;
                /*PRIZE-add for bugid: 34326-zhudaopeng-2017-06-13-End*/
                mQsTracking = true;
                mInitialTouchY = y;
                mInitialTouchX = x;
                onQsExpansionStarted();
                mInitialHeightOnTouch = mQsExpansionHeight;
                initVelocityTracker();
                trackMovement(event);
                break;

            case MotionEvent.ACTION_POINTER_UP:
                final int upPointer = event.getPointerId(event.getActionIndex());
                if (mTrackingPointer == upPointer) {
                    // gesture is ongoing, find a new pointer to track
                    final int newIndex = event.getPointerId(0) != upPointer ? 0 : 1;
                    final float newY = event.getY(newIndex);
                    final float newX = event.getX(newIndex);
                    mTrackingPointer = event.getPointerId(newIndex);
                    mInitialHeightOnTouch = mQsExpansionHeight;
                    mInitialTouchY = newY;
                    mInitialTouchX = newX;
                }
                break;

            case MotionEvent.ACTION_MOVE:
                /*PRIZE-add for bugid: 34326-zhudaopeng-2017-06-13-Start*/
                mEvent = event;
                /*PRIZE-add for bugid: 34326-zhudaopeng-2017-06-13-End*/
                /*PRIZE-show the blur background when pull at lockscreen-liufan-2015-06-15-start*/
                if (VersionControl.CUR_VERSION == VersionControl.BLUR_BG_VER) {
                    if(mStatusBarState == StatusBarState.KEYGUARD){
                        float height = h + mInitialHeightOnTouch;
                        height = Math.min(Math.max(height, mQsMinExpansionHeight), mQsMaxExpansionHeight);
                        if (height > mQsMinExpansionHeight && !mQsExpanded && !mStackScrollerOverscrolling) {
                            //锁屏时，屏蔽滑动--liufan-2015-09-03
                            if(VersionControl.isAllowDropDown(getContext())){
                                mStatusBar.showBlurWallPaper();
                                mKeyguardStatusView.setVisibility(View.GONE);//隐藏KeyguardStatusView
                            }
                        }
                    }
                }
                /*PRIZE-show the blur background when pull at lockscreen-liufan-2015-06-15-end*/
                /*PRIZE-Don't allow swipe when lockscreen-liufan-2015-09-03-start*/
                if(VersionControl.isAllowDropDown(getContext())){
                    setQsExpansion(h + mInitialHeightOnTouch);
                }else{
                    if(mStatusBar.getBarState() != StatusBarState.KEYGUARD){
                        setQsExpansion(h + mInitialHeightOnTouch);
                    }
                }
                /*PRIZE-Don't allow swipe when lockscreen-liufan-2015-09-03-end*/
                if (h >= getFalsingThreshold()) {
                    mQsTouchAboveFalsingThreshold = true;
                }
                trackMovement(event);
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                /*PRIZE-add for bugid: 34326-zhudaopeng-2017-06-13-Start*/
                mEvent = null;
                /*PRIZE-add for bugid: 34326-zhudaopeng-2017-06-13-End*/
                mQsTracking = false;
                mTrackingPointer = -1;
                trackMovement(event);
                float fraction = getQsExpansionFraction();
                if (fraction != 0f || y >= mInitialTouchY) {
                    flingQsWithCurrentVelocity(y,
                            event.getActionMasked() == MotionEvent.ACTION_CANCEL);
                }
                /*PRIZE-dismiss the blur layout when lockscreen-liufan-2015-06-15-start*/
				 else {

                    if (VersionControl.CUR_VERSION == VersionControl.BLUR_BG_VER) {
                        if (event.getActionMasked() == MotionEvent.ACTION_CANCEL
                                || event.getActionMasked() == MotionEvent.ACTION_UP){
                            if(mStatusBar.getBarState() == StatusBarState.KEYGUARD && fraction == 0 && !mQsExpanded){
                                mStatusBar.cancelNotificationBackground();
                                mKeyguardStatusView.setVisibility(View.VISIBLE);
                            }
                        }
                    }
                }
                /*PRIZE-dismiss the blur layout when lockscreen-liufan-2015-06-15-end*/
                if (mVelocityTracker != null) {
                    mVelocityTracker.recycle();
                    mVelocityTracker = null;
                }
                break;
        }
    }

    private int getFalsingThreshold() {
        float factor = mStatusBar.isWakeUpComingFromTouch() ? 1.5f : 1.0f;
        return (int) (mQsFalsingThreshold * factor);
    }

    @Override
    public void onOverscrollTopChanged(float amount, boolean isRubberbanded) {
        cancelQsAnimation();
        if (!mQsExpansionEnabled) {
            amount = 0f;
        }
        float rounded = amount >= 1f ? amount : 0f;
        setOverScrolling(rounded != 0f && isRubberbanded);
        mQsExpansionFromOverscroll = rounded != 0f;
        mLastOverscroll = rounded;
        updateQsState();
        setQsExpansion(mQsMinExpansionHeight + rounded);
    }

    @Override
    public void flingTopOverscroll(float velocity, boolean open) {
        mLastOverscroll = 0f;
        mQsExpansionFromOverscroll = false;
        setQsExpansion(mQsExpansionHeight);
        flingSettings(!mQsExpansionEnabled && open ? 0f : velocity, open && mQsExpansionEnabled,
                new Runnable() {
                    @Override
                    public void run() {
                        mStackScrollerOverscrolling = false;
                        setOverScrolling(false);
                        updateQsState();
                    }
                }, false /* isClick */);
    }

    private void setOverScrolling(boolean overscrolling) {
        mStackScrollerOverscrolling = overscrolling;
        mQsContainer.setOverscrolling(overscrolling);
    }

    private void onQsExpansionStarted() {
        onQsExpansionStarted(0);
    }

    private void onQsExpansionStarted(int overscrollAmount) {
        cancelQsAnimation();
        cancelHeightAnimator();

        // Reset scroll position and apply that position to the expanded height.
        float height = mQsExpansionHeight - overscrollAmount;
        setQsExpansion(height);
        requestPanelHeightUpdate();
    }

    private void setQsExpanded(boolean expanded) {
        boolean changed = mQsExpanded != expanded;
        if (changed) {
            mQsExpanded = expanded;
            updateQsState();
            requestPanelHeightUpdate();
            mFalsingManager.setQsExpanded(expanded);
            mStatusBar.setQsExpanded(expanded);
            mNotificationContainerParent.setQsExpanded(expanded);
            /*PRIZE-set mOpenFlashlightTxt GONE when QsExpanded- liufan-2015-12-04-start*/
            if(mOpenFlashlightTxt != null && mStatusBarState == StatusBarState.KEYGUARD){
                if(mQsExpanded){
                    mOpenFlashlightTxt.setVisibility(View.GONE);
                }else{
                    mOpenFlashlightTxt.setVisibility(View.VISIBLE);
                }
            }
            /*PRIZE-set mOpenFlashlightTxt GONE when QsExpanded- liufan-2015-12-04-end*/
        }
        if(!expanded && mStatusBarState == StatusBarState.KEYGUARD){
            mNotificationStackScroller.cancelAllNotificationRowBg();
        }
    }

    public void setBarState(int statusBarState, boolean keyguardFadingAway,
            boolean goingToFullShade) {
        int oldState = mStatusBarState;
        boolean keyguardShowing = statusBarState == StatusBarState.KEYGUARD;
        setKeyguardStatusViewVisibility(statusBarState, keyguardFadingAway, goingToFullShade);
        setKeyguardBottomAreaVisibility(statusBarState, goingToFullShade);

        mStatusBarState = statusBarState;
        mKeyguardShowing = keyguardShowing;
        mQsContainer.setKeyguardShowing(mKeyguardShowing);

        if (oldState == StatusBarState.KEYGUARD
                && (goingToFullShade || statusBarState == StatusBarState.SHADE_LOCKED)) {
            animateKeyguardStatusBarOut();
            long delay = mStatusBarState == StatusBarState.SHADE_LOCKED
                    ? 0 : mStatusBar.calculateGoingToFullShadeDelay();
            mQsContainer.animateHeaderSlidingIn(delay);
        } else if (oldState == StatusBarState.SHADE_LOCKED
                && statusBarState == StatusBarState.KEYGUARD) {
            animateKeyguardStatusBarIn(StackStateAnimator.ANIMATION_DURATION_STANDARD);
            mQsContainer.animateHeaderSlidingOut();
        } else {
            mKeyguardStatusBar.setAlpha(1f);
            mKeyguardStatusBar.setVisibility(keyguardShowing ? View.VISIBLE : View.INVISIBLE);
            if (keyguardShowing && oldState != mStatusBarState) {
                mKeyguardBottomArea.onKeyguardShowingChanged();
                mQsContainer.hideImmediately();
            }
        }
        if (keyguardShowing) {
            updateDozingVisibilities(false /* animate */);
        }
        resetVerticalPanelPosition();
        updateQsState();
        /*PRIZE-set mOpenFlashlightTxt GONE when hideKeyguard- liufan-2015-12-04-start*/
        if(mOpenFlashlightTxt != null){
            if(mStatusBarState == StatusBarState.SHADE){
                mOpenFlashlightTxt.setVisibility(View.GONE);
            }else{
                if(PrizeOption.PRIZE_SYSTEMUI_HAOKAN_SCREENVIEW && mStatusBar.isOpenMagazine()){//&& !HaokanShow
                    mOpenFlashlightTxt.setVisibility(View.GONE);
                }else{
                    mOpenFlashlightTxt.setVisibility(View.VISIBLE);
                }
            }
        }
        /*PRIZE-set mOpenFlashlightTxt GONE when hideKeyguard- liufan-2015-12-04-end*/
    }

    private final Runnable mAnimateKeyguardStatusViewInvisibleEndRunnable = new Runnable() {
        @Override
        public void run() {
            mKeyguardStatusViewAnimating = false;
            /**PRIZE-haokanscreen iteration one-liufan-2016-09-13-start */
            if(isShowKeyguardStatusView(mStatusBarState)){
                mKeyguardStatusView.setVisibility(View.VISIBLE);
            }else{
                mKeyguardStatusView.setVisibility(View.GONE);
            }
            /**PRIZE-haokanscreen iteration one-liufan-2016-09-13-end */
        }
    };

    private final Runnable mAnimateKeyguardStatusViewVisibleEndRunnable = new Runnable() {
        @Override
        public void run() {
            mKeyguardStatusViewAnimating = false;
        }
    };

    private final Runnable mAnimateKeyguardStatusBarInvisibleEndRunnable = new Runnable() {
        @Override
        public void run() {
            mKeyguardStatusBar.setVisibility(View.INVISIBLE);
            mKeyguardStatusBar.setAlpha(1f);
            mKeyguardStatusBarAnimateAlpha = 1f;
        }
    };

    private void animateKeyguardStatusBarOut() {
        ValueAnimator anim = ValueAnimator.ofFloat(mKeyguardStatusBar.getAlpha(), 0f);
        anim.addUpdateListener(mStatusBarAnimateAlphaListener);
        anim.setStartDelay(mStatusBar.isKeyguardFadingAway()
                ? mStatusBar.getKeyguardFadingAwayDelay()
                : 0);
        anim.setDuration(mStatusBar.isKeyguardFadingAway()
                ? mStatusBar.getKeyguardFadingAwayDuration() / 2
                : StackStateAnimator.ANIMATION_DURATION_STANDARD);
        anim.setInterpolator(Interpolators.LINEAR_OUT_SLOW_IN);
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mAnimateKeyguardStatusBarInvisibleEndRunnable.run();
            }
        });
        anim.start();
    }

    private final ValueAnimator.AnimatorUpdateListener mStatusBarAnimateAlphaListener =
            new ValueAnimator.AnimatorUpdateListener() {
        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
            mKeyguardStatusBarAnimateAlpha = (float) animation.getAnimatedValue();
            updateHeaderKeyguardAlpha();
        }
    };

    private void animateKeyguardStatusBarIn(long duration) {
        mKeyguardStatusBar.setVisibility(View.VISIBLE);
        mKeyguardStatusBar.setAlpha(0f);
        ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
        anim.addUpdateListener(mStatusBarAnimateAlphaListener);
        anim.setDuration(duration);
        anim.setInterpolator(Interpolators.LINEAR_OUT_SLOW_IN);
        anim.start();
    }

    private final Runnable mAnimateKeyguardBottomAreaInvisibleEndRunnable = new Runnable() {
        @Override
        public void run() {
            mKeyguardBottomArea.setVisibility(View.GONE);
        }
    };

    private void setKeyguardBottomAreaVisibility(int statusBarState,
            boolean goingToFullShade) {
        mKeyguardBottomArea.animate().cancel();
        if (goingToFullShade) {
            mKeyguardBottomArea.animate()
                    .alpha(0f)
                    .setStartDelay(mStatusBar.getKeyguardFadingAwayDelay())
                    .setDuration(mStatusBar.getKeyguardFadingAwayDuration() / 2)
                    .setInterpolator(Interpolators.ALPHA_OUT)
                    .withEndAction(mAnimateKeyguardBottomAreaInvisibleEndRunnable)
                    .start();
        } else if (statusBarState == StatusBarState.KEYGUARD
                || statusBarState == StatusBarState.SHADE_LOCKED) {
			//update by haokan-liufan-2016-10-12-start
            if (!mDozing) {
                /*-modify for haokan-liufan-2017-10-09-start-*/
                if(IS_ShowHaoKanView) {
                    if(statusBarState == StatusBarState.KEYGUARD){
                    }
                }else{
                    mKeyguardBottomArea.setVisibility(View.VISIBLE);
                }
                /*-modify for haokan-liufan-2017-10-09-end-*/
            }
            mKeyguardBottomArea.setAlpha(1f);
			//update by haokan-liufan-2016-10-12-end
        } else {

            mKeyguardBottomArea.setVisibility(View.GONE);
            mKeyguardBottomArea.setAlpha(1f);
        }
    }

    private void setKeyguardStatusViewVisibility(int statusBarState, boolean keyguardFadingAway,
            boolean goingToFullShade) {
        if ((!keyguardFadingAway && mStatusBarState == StatusBarState.KEYGUARD
                && statusBarState != StatusBarState.KEYGUARD) || goingToFullShade) {
            mKeyguardStatusView.animate().cancel();
            mKeyguardStatusViewAnimating = true;
            mKeyguardStatusView.animate()
                    .alpha(0f)
                    .setStartDelay(0)
                    .setDuration(160)
                    .setInterpolator(Interpolators.ALPHA_OUT)
                    .withEndAction(mAnimateKeyguardStatusViewInvisibleEndRunnable);
            if (keyguardFadingAway) {
                mKeyguardStatusView.animate()
                        .setStartDelay(mStatusBar.getKeyguardFadingAwayDelay())
                        .setDuration(mStatusBar.getKeyguardFadingAwayDuration()/2)
                        .start();
            }
        } else if (mStatusBarState == StatusBarState.SHADE_LOCKED
                && statusBarState == StatusBarState.KEYGUARD) {
            mKeyguardStatusView.animate().cancel();
                /**PRIZE-haokanscreen iteration one-liufan-2016-09-13-start */
                if(isShowKeyguardStatusView(statusBarState)){
                    mKeyguardStatusView.setVisibility(View.VISIBLE);
                }else{
                    mKeyguardStatusView.setVisibility(View.GONE);
                }
                /**PRIZE-haokanscreen iteration one-liufan-2016-09-13-end */
            mKeyguardStatusViewAnimating = true;
            mKeyguardStatusView.setAlpha(0f);
            mKeyguardStatusView.animate()
                    .alpha(1f)
                    .setStartDelay(0)
                    .setDuration(320)
                    .setInterpolator(Interpolators.ALPHA_IN)
                    .withEndAction(mAnimateKeyguardStatusViewVisibleEndRunnable);
        } else if (statusBarState == StatusBarState.KEYGUARD) {
            mKeyguardStatusView.animate().cancel();
            mKeyguardStatusViewAnimating = false;
                /**PRIZE-haokanscreen iteration one-liufan-2016-09-13-start */
                if(isShowKeyguardStatusView(statusBarState)){
                    mKeyguardStatusView.setVisibility(View.VISIBLE);
                }else{
                    mKeyguardStatusView.setVisibility(View.GONE);
                }
                /**PRIZE-haokanscreen iteration one-liufan-2016-09-13-end */
            mKeyguardStatusView.setAlpha(1f);
        } else {
            mKeyguardStatusView.animate().cancel();
            mKeyguardStatusViewAnimating = false;
            /**PRIZE-haokanscreen iteration one-liufan-2016-09-13-start */
            if(isShowKeyguardStatusView(statusBarState)){
                mKeyguardStatusView.setVisibility(View.VISIBLE);
            }else{
                mKeyguardStatusView.setVisibility(View.GONE);
            }
            /**PRIZE-haokanscreen iteration one-liufan-2016-09-13-end */
            mKeyguardStatusView.setAlpha(1f);
        }
    }

    private void updateQsState() {
        mQsContainer.setExpanded(mQsExpanded);
        mNotificationStackScroller.setQsExpanded(mQsExpanded);
        mNotificationStackScroller.setScrollingEnabled(
                mStatusBarState != StatusBarState.KEYGUARD && (!mQsExpanded
                        || mQsExpansionFromOverscroll));
        /*PRIZE-update for brightness controller- liufan-2016-06-29-start*/
        /*if(!BrightnessMirrorController.isRegulateBrightness){
            mScrollView.setTouchEnabled(mQsExpanded);
        }*/
        /*PRIZE-update for brightness controller- liufan-2016-06-29-end*/

		Log.d(TAG,"yuhao updateQsState: and PRIZE_POWER_EXTEND_MODE is "+PrizeOption.PRIZE_POWER_EXTEND_MODE+", isSuperSaverMode is "+PowerManager.isSuperSaverMode());
		/*PRIZE-PowerExtendMode-wangxianzhen-2015-05-30-start*/
        if (PrizeOption.PRIZE_POWER_EXTEND_MODE && PowerManager.isSuperSaverMode()){
                 Log.d("lhy","NotificationPanelView updateQsState PowerExtendMode ");
                 // mQsContainer.setVisibility(View.GONE);
		   //mHeader_new.setVisibility(View.VISIBLE);
		   //mHeader_new.setVisibility(View.GONE);
                 mQSPanel_new.setVisibility(View.GONE);
        }else{
	     //mHeader_new.setVisibility(View.VISIBLE);
            if(mQsExpanded) mQSPanel_new.setVisibility(View.VISIBLE);
            mQsContainer.setVisibility(View.VISIBLE);
        }
        /*PRIZE-PowerExtendMode-wangxianzhen-2015-05-30-end*/

		
        updateEmptyShadeView();
        mQsNavbarScrim.setVisibility(mStatusBarState == StatusBarState.SHADE && mQsExpanded
                && !mStackScrollerOverscrolling && mQsScrimEnabled
                        ? View.VISIBLE
                        : View.INVISIBLE);
        if (mKeyguardUserSwitcher != null && mQsExpanded && !mStackScrollerOverscrolling) {
            mKeyguardUserSwitcher.hideIfNotSimple(true /* animate */);
        }
    }

    private void setQsExpansion(float height) {
        height = Math.min(Math.max(height, mQsMinExpansionHeight), mQsMaxExpansionHeight);
        mQsFullyExpanded = height == mQsMaxExpansionHeight && mQsMaxExpansionHeight != 0;
        if (height > mQsMinExpansionHeight && !mQsExpanded && !mStackScrollerOverscrolling) {
            setQsExpanded(true);
            /*PRIZE-add judgement,decide to show blur bg or scrim bg-liufan-2016-05-18-start*/
            if(!PhoneStatusBar.BLUR_BG_CONTROL && mStatusBar.getBarState() == StatusBarState.KEYGUARD){
                postDelayed(new Runnable(){
                    @Override
                    public void run() {
                        mStatusBar.setBlurBackVisibility(View.INVISIBLE);
                    }
                },100);
            }
            /*PRIZE-add judgement,decide to show blur bg or scrim bg-liufan-2016-05-18-end*/
        } else if (height <= mQsMinExpansionHeight && mQsExpanded) {
            setQsExpanded(false);
            /*PRIZE-add judgement,decide to show blur bg or scrim bg-liufan-2016-05-18-start*/
            if(!PhoneStatusBar.BLUR_BG_CONTROL && mStatusBar.getBarState() == StatusBarState.KEYGUARD){
                mStatusBar.isShowBlurBgWhenLockscreen(false);
            }
            /*PRIZE-add judgement,decide to show blur bg or scrim bg-liufan-2016-05-18-end*/
            if (mLastAnnouncementWasQuickSettings && !mTracking && !isCollapsing()) {
                announceForAccessibility(getKeyguardOrLockScreenString());
                mLastAnnouncementWasQuickSettings = false;
            }
        }
        mQsExpansionHeight = height;
        updateQsExpansion();
        requestScrollerTopPaddingUpdate(false /* animate */);
        if (mKeyguardShowing) {
            updateHeaderKeyguardAlpha();
        }
        if (mStatusBarState == StatusBarState.SHADE_LOCKED
                || mStatusBarState == StatusBarState.KEYGUARD) {
            updateKeyguardBottomAreaAlpha();
        }
        if (mStatusBarState == StatusBarState.SHADE && mQsExpanded
                && !mStackScrollerOverscrolling && mQsScrimEnabled) {
            mQsNavbarScrim.setAlpha(getQsExpansionFraction());
        }

        // Upon initialisation when we are not layouted yet we don't want to announce that we are
        // fully expanded, hence the != 0.0f check.
        if (height != 0.0f && mQsFullyExpanded && !mLastAnnouncementWasQuickSettings) {
            announceForAccessibility(getContext().getString(
                    R.string.accessibility_desc_quick_settings));
            mLastAnnouncementWasQuickSettings = true;
        }
        if (mQsFullyExpanded && mFalsingManager.shouldEnforceBouncer()) {
            mStatusBar.executeRunnableDismissingKeyguard(null, null /* cancelAction */,
                    false /* dismissShade */, true /* afterKeyguardGone */, false /* deferred */);
        }
        if (DEBUG) {
            invalidate();
        }
    }

    protected void updateQsExpansion() {
        mQsContainer.setQsExpansion(getQsExpansionFraction(), getHeaderTranslation());
    }

    private String getKeyguardOrLockScreenString() {
        if (mQsContainer.isCustomizing()) {
            return getContext().getString(R.string.accessibility_desc_quick_settings_edit);
        } else if (mStatusBarState == StatusBarState.KEYGUARD) {
            return getContext().getString(R.string.accessibility_desc_lock_screen);
        } else {
            return getContext().getString(R.string.accessibility_desc_notification_shade);
        }
    }

    private float calculateQsTopPadding() {
        if (mKeyguardShowing
                && (mQsExpandImmediate || mIsExpanding && mQsExpandedWhenExpandingStarted)) {

            // Either QS pushes the notifications down when fully expanded, or QS is fully above the
            // notifications (mostly on tablets). maxNotifications denotes the normal top padding
            // on Keyguard, maxQs denotes the top padding from the quick settings panel. We need to
            // take the maximum and linearly interpolate with the panel expansion for a nice motion.
            int maxNotifications = mClockPositionResult.stackScrollerPadding
                    - mClockPositionResult.stackScrollerPaddingAdjustment;
            int maxQs = getTempQsMaxExpansion();
            int max = mStatusBarState == StatusBarState.KEYGUARD
                    ? Math.max(maxNotifications, maxQs)
                    : maxQs;
            return (int) interpolate(getExpandedFraction(),
                    mQsMinExpansionHeight, max);
        } else if (mQsSizeChangeAnimator != null) {
            return (int) mQsSizeChangeAnimator.getAnimatedValue();
        } else if (mKeyguardShowing) {

            // We can only do the smoother transition on Keyguard when we also are not collapsing
            // from a scrolled quick settings.
            return interpolate(getQsExpansionFraction(),
                    mNotificationStackScroller.getIntrinsicPadding(),
                    mQsMaxExpansionHeight);
        } else {
            return mQsExpansionHeight;
        }
    }

    protected void requestScrollerTopPaddingUpdate(boolean animate) {
        mNotificationStackScroller.updateTopPadding(calculateQsTopPadding(),
                mAnimateNextTopPaddingChange || animate,
                mKeyguardShowing
                        && (mQsExpandImmediate || mIsExpanding && mQsExpandedWhenExpandingStarted));
        mAnimateNextTopPaddingChange = false;
    }

    private void trackMovement(MotionEvent event) {
        if (mVelocityTracker != null) mVelocityTracker.addMovement(event);
        mLastTouchX = event.getX();
        mLastTouchY = event.getY();
    }

    private void initVelocityTracker() {
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
        }
        mVelocityTracker = VelocityTracker.obtain();
    }

    private float getCurrentVelocity() {
        if (mVelocityTracker == null) {
            return 0;
        }
        mVelocityTracker.computeCurrentVelocity(1000);
        return mVelocityTracker.getYVelocity();
    }

    private void cancelQsAnimation() {
        if (mQsExpansionAnimator != null) {
            mQsExpansionAnimator.cancel();
        }
    }

    public void flingSettings(float vel, boolean expand) {
        flingSettings(vel, expand, null, false /* isClick */);
    }

    private void flingSettings(float vel, boolean expand, final Runnable onFinishRunnable,
            boolean isClick) {
        float target = expand ? mQsMaxExpansionHeight : mQsMinExpansionHeight;
        if (target == mQsExpansionHeight) {
            if (onFinishRunnable != null) {
                onFinishRunnable.run();
            }
            return;
        }
        boolean belowFalsingThreshold = isFalseTouch();
        if (belowFalsingThreshold) {
            vel = 0;
        }
        ValueAnimator animator = ValueAnimator.ofFloat(mQsExpansionHeight, target);
        if (isClick) {
            animator.setInterpolator(Interpolators.TOUCH_RESPONSE);
            animator.setDuration(368);
        } else {
            mFlingAnimationUtils.apply(animator, mQsExpansionHeight, target, vel);
        }
        if (belowFalsingThreshold) {
            animator.setDuration(350);
        }
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                setQsExpansion((Float) animation.getAnimatedValue());
            }
        });
        /*PRIZE-show blur background when pull at lockscreen-liufan-2015-06-15-start*/
        final boolean flag = expand;
        animator.addListener(new AnimatorListenerAdapter() {
            private boolean mCancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                mCancelled = true;
            }
            
            @Override
            public void onAnimationEnd(Animator animation) {
                mQsExpansionAnimator = null;
                if (onFinishRunnable != null) {
                    onFinishRunnable.run();
                }
                
                if (VersionControl.CUR_VERSION == VersionControl.BLUR_BG_VER) {
                    float fraction = getQsExpansionFraction();
                    if(mStatusBarState == StatusBarState.KEYGUARD && !mCancelled && fraction == 0 && !mQsExpanded){
                        if(!flag){
                            Log.e("liufan","flingSettings-----cancelNotificationBackground---->");
                            mStatusBar.cancelNotificationBackground();
                                /**PRIZE-haokanscreen iteration one-liufan-2016-09-13-start */
                                if(isShowKeyguardStatusView(mStatusBarState)){
                                    mKeyguardStatusView.setVisibility(View.VISIBLE);
                                }else{
                                    mKeyguardStatusView.setVisibility(View.GONE);
                                }
                                /**PRIZE-haokanscreen iteration one-liufan-2016-09-13-end */
                        }
                    }
                }
                
            }
        });
        /*PRIZE-show blur background when pull at lockscreen-liufan-2015-06-15-end*/
        animator.start();
        mQsExpansionAnimator = animator;
        mQsAnimatorExpand = expand;
    }

    /**
     * @return Whether we should intercept a gesture to open Quick Settings.
     */
    private boolean shouldQuickSettingsIntercept(float x, float y, float yDiff) {
        if (!mQsExpansionEnabled || mCollapsedOnDown) {
            return false;
        }
        View header = mKeyguardShowing ? mKeyguardStatusBar : mQsContainer.getHeader();
        boolean onHeader = x >= mQsAutoReinflateContainer.getX()
                && x <= mQsAutoReinflateContainer.getX() + mQsAutoReinflateContainer.getWidth()
                && y >= header.getTop() && y <= header.getBottom();
        if (mQsExpanded) {
            /*PRIZE-Don't unlock the lockscreen when click the bottom of the notification at the lockscreen-liufan-2015-06-15-start*/
            if (VersionControl.CUR_VERSION == VersionControl.BLUR_BG_VER) {
                if(mKeyguardShowing){
                    return onHeader || yDiff < 0 ;
                }
            }
            /*PRIZE-Don't unlock the lockscreen when click the bottom of the notification at the lockscreen-liufan-2015-06-15-end*/
            return onHeader || (yDiff < 0 && isInQsArea(x, y));
        } else {
            return onHeader;
        }
    }

    @Override
    protected boolean isScrolledToBottom() {
        if (!isInSettings()) {
            return mStatusBar.getBarState() == StatusBarState.KEYGUARD
                    || mNotificationStackScroller.isScrolledToBottom();
        } else {
            return true;
        }
    }

    @Override
    protected int getMaxPanelHeight() {
        int min = mStatusBarMinHeight;
        if (mStatusBar.getBarState() != StatusBarState.KEYGUARD
                && mNotificationStackScroller.getNotGoneChildCount() == 0) {
            int minHeight = (int) (mQsMinExpansionHeight + getOverExpansionAmount());
            min = Math.max(min, minHeight);
        }
        int maxHeight;
        if (mQsExpandImmediate || mQsExpanded || mIsExpanding && mQsExpandedWhenExpandingStarted) {
            maxHeight = calculatePanelHeightQsExpanded();
        } else {
            maxHeight = calculatePanelHeightShade();
        }
        maxHeight = Math.max(maxHeight, min);
        return maxHeight;
    }

    public boolean isInSettings() {
        return mQsExpanded;
    }

    public boolean isExpanding() {
        return mIsExpanding;
    }

    @Override
    protected void onHeightUpdated(float expandedHeight) {
        if (!mQsExpanded || mQsExpandImmediate || mIsExpanding && mQsExpandedWhenExpandingStarted) {
            positionClockAndNotifications();
			/*PRIZE-PowerExtendMode-wangxianzhen-2015-07-20-start*/
			if (PrizeOption.PRIZE_POWER_EXTEND_MODE && !PowerManager.isSuperSaverMode())
			{
				if(mHeader_new!= null) mHeader_new.setVisibility(View.VISIBLE);
			}
			/*PRIZE-PowerExtendMode-wangxianzhen-2015-07-20-end*/
			
        }
        if (mQsExpandImmediate || mQsExpanded && !mQsTracking && mQsExpansionAnimator == null
                && !mQsExpansionFromOverscroll) {
            float t;
            if (mKeyguardShowing) {

                // On Keyguard, interpolate the QS expansion linearly to the panel expansion
                t = expandedHeight / getMaxPanelHeight();
            } else {

                // In Shade, interpolate linearly such that QS is closed whenever panel height is
                // minimum QS expansion + minStackHeight
                float panelHeightQsCollapsed = mNotificationStackScroller.getIntrinsicPadding()
                        + mNotificationStackScroller.getLayoutMinHeight();
                float panelHeightQsExpanded = calculatePanelHeightQsExpanded();
                t = (expandedHeight - panelHeightQsCollapsed)
                        / (panelHeightQsExpanded - panelHeightQsCollapsed);
            }
            setQsExpansion(mQsMinExpansionHeight
                    + t * (getTempQsMaxExpansion() - mQsMinExpansionHeight));
        }
        updateExpandedHeight(expandedHeight);
        updateHeader();
        updateUnlockIcon();
        updateNotificationTranslucency();
        updatePanelExpanded();
        mNotificationStackScroller.setShadeExpanded(!isFullyCollapsed());
        if (DEBUG) {
            invalidate();
        }
    }

    private void updatePanelExpanded() {
        boolean isExpanded = !isFullyCollapsed();
        if (mPanelExpanded != isExpanded) {
            mHeadsUpManager.setIsExpanded(isExpanded);
            mStatusBar.setPanelExpanded(isExpanded);
            mPanelExpanded = isExpanded;
        }
    }

    /**
     * @return a temporary override of {@link #mQsMaxExpansionHeight}, which is needed when
     *         collapsing QS / the panel when QS was scrolled
     */
    private int getTempQsMaxExpansion() {
        return mQsMaxExpansionHeight;
    }

    private int calculatePanelHeightShade() {
        int emptyBottomMargin = mNotificationStackScroller.getEmptyBottomMargin();
        int maxHeight = mNotificationStackScroller.getHeight() - emptyBottomMargin
                - mTopPaddingAdjustment;
        maxHeight += mNotificationStackScroller.getTopPaddingOverflow();
        return maxHeight;
    }

    private int calculatePanelHeightQsExpanded() {
        float notificationHeight = mNotificationStackScroller.getHeight()
                - mNotificationStackScroller.getEmptyBottomMargin()
                - mNotificationStackScroller.getTopPadding();

        // When only empty shade view is visible in QS collapsed state, simulate that we would have
        // it in expanded QS state as well so we don't run into troubles when fading the view in/out
        // and expanding/collapsing the whole panel from/to quick settings.
        if (mNotificationStackScroller.getNotGoneChildCount() == 0
                && mShadeEmpty) {
            notificationHeight = mNotificationStackScroller.getEmptyShadeViewHeight()
                    + mNotificationStackScroller.getBottomStackPeekSize()
                    + mNotificationStackScroller.getBottomStackSlowDownHeight();
        }
        int maxQsHeight = mQsMaxExpansionHeight;

        // If an animation is changing the size of the QS panel, take the animated value.
        if (mQsSizeChangeAnimator != null) {
            maxQsHeight = (int) mQsSizeChangeAnimator.getAnimatedValue();
        }
        float totalHeight = Math.max(
                maxQsHeight, mStatusBarState == StatusBarState.KEYGUARD
                        ? mClockPositionResult.stackScrollerPadding - mTopPaddingAdjustment
                        : 0)
                + notificationHeight + mNotificationStackScroller.getTopPaddingOverflow();
        if (totalHeight > mNotificationStackScroller.getHeight()) {
            float fullyCollapsedHeight = maxQsHeight
                    + mNotificationStackScroller.getLayoutMinHeight();
            totalHeight = Math.max(fullyCollapsedHeight, mNotificationStackScroller.getHeight());
        }
        return (int) totalHeight;
    }

    private void updateNotificationTranslucency() {
        float alpha = 1f;
        if (mClosingWithAlphaFadeOut && !mExpandingFromHeadsUp && !mHeadsUpManager.hasPinnedHeadsUp()) {
            alpha = getFadeoutAlpha();
        }
        mNotificationStackScroller.setAlpha(alpha);
    }

    private float getFadeoutAlpha() {
        float alpha = (getNotificationsTopY() + mNotificationStackScroller.getFirstItemMinHeight())
                / (mQsMinExpansionHeight + mNotificationStackScroller.getBottomStackPeekSize()
                - mNotificationStackScroller.getBottomStackSlowDownHeight());
        alpha = Math.max(0, Math.min(alpha, 1));
        alpha = (float) Math.pow(alpha, 0.75);
        return alpha;
    }

    @Override
    protected float getOverExpansionAmount() {
        return mNotificationStackScroller.getCurrentOverScrollAmount(true /* top */);
    }

    @Override
    protected float getOverExpansionPixels() {
        return mNotificationStackScroller.getCurrentOverScrolledPixels(true /* top */);
    }

    private void updateUnlockIcon() {
        if (mStatusBar.getBarState() == StatusBarState.KEYGUARD
                || mStatusBar.getBarState() == StatusBarState.SHADE_LOCKED) {
            boolean active = getMaxPanelHeight() - getExpandedHeight() > mUnlockMoveDistance;
            KeyguardAffordanceView lockIcon = mKeyguardBottomArea.getLockIcon();
            if (active && !mUnlockIconActive && mTracking) {
                lockIcon.setImageAlpha(1.0f, true, 150, Interpolators.FAST_OUT_LINEAR_IN, null);
                lockIcon.setImageScale(LOCK_ICON_ACTIVE_SCALE, true, 150,
                        Interpolators.FAST_OUT_LINEAR_IN);
            } else if (!active && mUnlockIconActive && mTracking) {
                lockIcon.setImageAlpha(lockIcon.getRestingAlpha(), true /* animate */,
                        150, Interpolators.FAST_OUT_LINEAR_IN, null);
                lockIcon.setImageScale(1.0f, true, 150,
                        Interpolators.FAST_OUT_LINEAR_IN);
            }
            mUnlockIconActive = active;
        }
    }

    /**
     * Hides the header when notifications are colliding with it.
     */
    private void updateHeader() {
        if (mStatusBar.getBarState() == StatusBarState.KEYGUARD) {
            updateHeaderKeyguardAlpha();
        }
        updateQsExpansion();
    }

    protected float getHeaderTranslation() {
        if (mStatusBar.getBarState() == StatusBarState.KEYGUARD) {
            return 0;
        }
        float translation = NotificationUtils.interpolate(-mQsMinExpansionHeight, 0,
                mNotificationStackScroller.getAppearFraction(mExpandedHeight));
        return Math.min(0, translation);
    }

    /**
     * @return the alpha to be used to fade out the contents on Keyguard (status bar, bottom area)
     *         during swiping up
     */
    private float getKeyguardContentsAlpha() {
        float alpha;
        if (mStatusBar.getBarState() == StatusBarState.KEYGUARD) {

            // When on Keyguard, we hide the header as soon as the top card of the notification
            // stack scroller is close enough (collision distance) to the bottom of the header.
            alpha = getNotificationsTopY()
                    /
                    (mKeyguardStatusBar.getHeight() + mNotificationsHeaderCollideDistance);
        } else {

            // In SHADE_LOCKED, the top card is already really close to the header. Hide it as
            // soon as we start translating the stack.
            alpha = getNotificationsTopY() / mKeyguardStatusBar.getHeight();
        }
        alpha = MathUtils.constrain(alpha, 0, 1);
        alpha = (float) Math.pow(alpha, 0.75);
        return alpha;
    }

    private void updateHeaderKeyguardAlpha() {
        float alphaQsExpansion = 1 - Math.min(1, getQsExpansionFraction() * 2);
        mKeyguardStatusBar.setAlpha(Math.min(getKeyguardContentsAlpha(), alphaQsExpansion)
                * mKeyguardStatusBarAnimateAlpha);
        /*PRIZE-add for haokan-liufan-2018-1-25-start*/
        boolean visible = mKeyguardStatusBar.getAlpha() != 0f && !mDozing;
        if(mStatusBar.isUseHaoKan()){
            visible = IS_ShowNotification_WhenShowHaoKan ? visible : false;
        }
        mKeyguardStatusBar.setVisibility(visible ? VISIBLE : INVISIBLE);
        /*PRIZE-add for haokan-liufan-2018-1-25-end*/
    }

    private void updateKeyguardBottomAreaAlpha() {
        float alpha = Math.min(getKeyguardContentsAlpha(), 1 - getQsExpansionFraction());
        mKeyguardBottomArea.setAlpha(alpha);
        mKeyguardBottomArea.setImportantForAccessibility(alpha == 0f
                ? IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                : IMPORTANT_FOR_ACCESSIBILITY_AUTO);
    }

    private float getNotificationsTopY() {
        if (mNotificationStackScroller.getNotGoneChildCount() == 0) {
            return getExpandedHeight();
        }
        return mNotificationStackScroller.getNotificationsTopY();
    }

    @Override
    protected void onExpandingStarted() {
        super.onExpandingStarted();
        /*PRIZE-show blur background - liufan-2015-06-10-start*/
        if (VersionControl.CUR_VERSION == VersionControl.BLUR_BG_VER) {
            Log.e(TOUCH_TAG,"onExpandingStarted mExpandedHeight = " + mExpandedHeight);
            if (mExpandedHeight == 0f) {
                mStatusBar.cancelNotificationBackground();
                mStatusBar.showBlurBackground();
            }
        }
        /*PRIZE-show blur background - liufan-2015-06-10-end*/
        mNotificationStackScroller.onExpansionStarted();
        mIsExpanding = true;
        mQsExpandedWhenExpandingStarted = mQsFullyExpanded;
        if (mQsExpanded) {
            onQsExpansionStarted();
        }
        // Since there are QS tiles in the header now, we need to make sure we start listening
        // immediately so they can be up to date.
        mQsContainer.setHeaderListening(true);
    }

    @Override
    protected void onExpandingFinished() {
        super.onExpandingFinished();
        mNotificationStackScroller.onExpansionStopped();
        mHeadsUpManager.onExpandingFinished();
        mIsExpanding = false;
        if (isFullyCollapsed()) {
            DejankUtils.postAfterTraversal(new Runnable() {
                @Override
                public void run() {
                    setListening(false);
                }
            });

            // Workaround b/22639032: Make sure we invalidate something because else RenderThread
            // thinks we are actually drawing a frame put in reality we don't, so RT doesn't go
            // ahead with rendering and we jank.
            postOnAnimation(new Runnable() {
                @Override
                public void run() {
                    getParent().invalidateChild(NotificationPanelView.this, mDummyDirtyRect);
                }
            });
        } else {
            setListening(true);
        }
        mQsExpandImmediate = false;
        mTwoFingerQsExpandPossible = false;
        mIsExpansionFromHeadsUp = false;
        mNotificationStackScroller.setTrackingHeadsUp(false);
        mExpandingFromHeadsUp = false;
        setPanelScrimMinFraction(0.0f);
    }

    /*PRIZE-show blur background - liufan-2015-06-10-start*/
    public void showBlurBackground(){
        mStatusBar.showBlurBackground();
    }
    public void cancelNotificationBackground(){
        mStatusBar.cancelNotificationBackground();
    }
    /*PRIZE-show blur background - liufan-2015-06-10-end*/

    private void setListening(boolean listening) {
        mQsContainer.setListening(listening);
        mKeyguardStatusBar.setListening(listening);
    }

    @Override
    public void expand(boolean animate) {
        /*PRIZE-overlapping - liufan-2015-09-04-start*/
        Log.d(TOUCH_TAG,"mQsExpandImmediate----------instantExpand---");
        Log.d(TOUCH_TAG, Debug.getCallers(15));
        mQsExpandImmediate = false;
        /*PRIZE-overlapping - liufan-2015-09-04-end*/
        super.expand(animate);
        setListening(true);
    }

    @Override
    protected void setOverExpansion(float overExpansion, boolean isPixels) {
        if (mConflictingQsExpansionGesture || mQsExpandImmediate) {
            return;
        }
        if (mStatusBar.getBarState() != StatusBarState.KEYGUARD) {
            mNotificationStackScroller.setOnHeightChangedListener(null);
            if (isPixels) {
                mNotificationStackScroller.setOverScrolledPixels(
                        overExpansion, true /* onTop */, false /* animate */);
            } else {
                mNotificationStackScroller.setOverScrollAmount(
                        overExpansion, true /* onTop */, false /* animate */);
            }
            mNotificationStackScroller.setOnHeightChangedListener(this);
        }
    }

    @Override
    protected void onTrackingStarted() {
        mFalsingManager.onTrackingStarted();
        super.onTrackingStarted();
        if (mQsFullyExpanded) {
            mQsExpandImmediate = true;
        }
        /*PRIZE-Don't allow pull at the lockscreen- liufan-2015-09-04-start*/
        if (mStatusBar.getBarState() == StatusBarState.KEYGUARD
                || mStatusBar.getBarState() == StatusBarState.SHADE_LOCKED){
            mQsExpandImmediate = false;
        }
        /*PRIZE-Don't allow pull at the lockscreen- liufan-2015-09-04-end*/
        if (mStatusBar.getBarState() == StatusBarState.KEYGUARD
                || mStatusBar.getBarState() == StatusBarState.SHADE_LOCKED) {
            mAfforanceHelper.animateHideLeftRightIcon();
        }
        mNotificationStackScroller.onPanelTrackingStarted();
    }

    @Override
    protected void onTrackingStopped(boolean expand) {
        mFalsingManager.onTrackingStopped();
        super.onTrackingStopped(expand);
        if (expand) {
            mNotificationStackScroller.setOverScrolledPixels(
                    0.0f, true /* onTop */, true /* animate */);
        }
        mNotificationStackScroller.onPanelTrackingStopped();
        if (expand && (mStatusBar.getBarState() == StatusBarState.KEYGUARD
                || mStatusBar.getBarState() == StatusBarState.SHADE_LOCKED)) {
            if (!mHintAnimationRunning) {
                mAfforanceHelper.reset(true);
            }
        }
        if (!expand && (mStatusBar.getBarState() == StatusBarState.KEYGUARD
                || mStatusBar.getBarState() == StatusBarState.SHADE_LOCKED)) {
            KeyguardAffordanceView lockIcon = mKeyguardBottomArea.getLockIcon();
            lockIcon.setImageAlpha(0.0f, true, 100, Interpolators.FAST_OUT_LINEAR_IN, null);
            lockIcon.setImageScale(2.0f, true, 100, Interpolators.FAST_OUT_LINEAR_IN);
        }
    }

    /*PRIZE-add for headsup notification- liufan-2016-07-02-start*/
    public void dismissNotificationBgWhenHeadsUp(boolean expand){
        if(!isHaveHeadsUpNotification){
            return ;
        }
        for(ExpandableNotificationRow headsUp : headsUpLayoutSet){
            if(!expand){
                headsUp.findViewById(R.id.notification_scrim_view).setVisibility(View.VISIBLE);
                mStatusBar.dismissNotificationBackgroundAnimation(true,null);
            } else {
                headsUp.findViewById(R.id.notification_scrim_view).setVisibility(View.GONE);
            }
        }
    }
    /*PRIZE-add for headsup notification- liufan-2016-07-02-end*/

    @Override
    public void onHeightChanged(ExpandableView view, boolean needsAnimation) {

        // Block update if we are in quick settings and just the top padding changed
        // (i.e. view == null).
        if (view == null && mQsExpanded) {
            return;
        }
        ExpandableView firstChildNotGone = mNotificationStackScroller.getFirstChildNotGone();
        ExpandableNotificationRow firstRow = firstChildNotGone instanceof ExpandableNotificationRow
                ? (ExpandableNotificationRow) firstChildNotGone
                : null;
        if (firstRow != null
                && (view == firstRow || (firstRow.getNotificationParent() == firstRow))) {
            requestScrollerTopPaddingUpdate(false);
        }
        requestPanelHeightUpdate();
    }

    @Override
    public void onReset(ExpandableView view) {
    }

    public void onQsHeightChanged() {
        mQsMaxExpansionHeight = mQsContainer.getDesiredHeight();
        if (mQsExpanded && mQsFullyExpanded) {
            mQsExpansionHeight = mQsMaxExpansionHeight;
            requestScrollerTopPaddingUpdate(false /* animate */);
            requestPanelHeightUpdate();
        }
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mAfforanceHelper.onConfigurationChanged();
        if (newConfig.orientation != mLastOrientation) {
            resetVerticalPanelPosition();
        }
        mLastOrientation = newConfig.orientation;
    }

    @Override
    public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        mNavigationBarBottomHeight = insets.getStableInsetBottom();
        updateMaxHeadsUpTranslation();
        return insets;
    }

    private void updateMaxHeadsUpTranslation() {
        mNotificationStackScroller.setHeadsUpBoundaries(getHeight(), mNavigationBarBottomHeight);
    }

    @Override
    public void onRtlPropertiesChanged(int layoutDirection) {
        if (layoutDirection != mOldLayoutDirection) {
            mAfforanceHelper.onRtlPropertiesChanged();
            mOldLayoutDirection = layoutDirection;
        }
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.expand_indicator) {
            onQsExpansionStarted();
            if (mQsExpanded) {
                flingSettings(0 /* vel */, false /* expand */, null, true /* isClick */);
            } else if (mQsExpansionEnabled) {
                EventLogTags.writeSysuiLockscreenGesture(
                        EventLogConstants.SYSUI_TAP_TO_OPEN_QS,
                        0, 0);
                flingSettings(0 /* vel */, true /* expand */, null, true /* isClick */);
            }
        }
    }

    @Override
    public void onAnimationToSideStarted(boolean rightPage, float translation, float vel) {
        boolean start = getLayoutDirection() == LAYOUT_DIRECTION_RTL ? rightPage : !rightPage;
        mIsLaunchTransitionRunning = true;
        mLaunchAnimationEndRunnable = null;
        float displayDensity = mStatusBar.getDisplayDensity();
        int lengthDp = Math.abs((int) (translation / displayDensity));
        int velocityDp = Math.abs((int) (vel / displayDensity));
        if (start) {
            EventLogTags.writeSysuiLockscreenGesture(
                    EventLogConstants.SYSUI_LOCKSCREEN_GESTURE_SWIPE_DIALER, lengthDp, velocityDp);

            mFalsingManager.onLeftAffordanceOn();
            if (mFalsingManager.shouldEnforceBouncer()) {
                mStatusBar.executeRunnableDismissingKeyguard(new Runnable() {
                    @Override
                    public void run() {
                        mKeyguardBottomArea.launchLeftAffordance();
                    }
                }, null, true /* dismissShade */, false /* afterKeyguardGone */,
                        true /* deferred */);
            }
            else {
                mKeyguardBottomArea.launchLeftAffordance();
            }
        } else {
            if (KeyguardBottomAreaView.CAMERA_LAUNCH_SOURCE_AFFORDANCE.equals(
                    mLastCameraLaunchSource)) {
                EventLogTags.writeSysuiLockscreenGesture(
                        EventLogConstants.SYSUI_LOCKSCREEN_GESTURE_SWIPE_CAMERA,
                        lengthDp, velocityDp);
            }
            mFalsingManager.onCameraOn();
            if (mFalsingManager.shouldEnforceBouncer()) {
                mStatusBar.executeRunnableDismissingKeyguard(new Runnable() {
                    @Override
                    public void run() {
                        mKeyguardBottomArea.launchCamera(mLastCameraLaunchSource);
                    }
                }, null, true /* dismissShade */, false /* afterKeyguardGone */,
                    true /* deferred */);
            }
            else {
                mKeyguardBottomArea.launchCamera(mLastCameraLaunchSource);
            }
        }
        mStatusBar.startLaunchTransitionTimeout();
        mBlockTouches = true;
    }

    @Override
    public void onAnimationToSideEnded() {
        mIsLaunchTransitionRunning = false;
        mIsLaunchTransitionFinished = true;
        if (mLaunchAnimationEndRunnable != null) {
            mLaunchAnimationEndRunnable.run();
            mLaunchAnimationEndRunnable = null;
        }
    }

    @Override
    protected void startUnlockHintAnimation() {
        super.startUnlockHintAnimation();
        startHighlightIconAnimation(getCenterIcon());
    }

    /**
     * Starts the highlight (making it fully opaque) animation on an icon.
     */
    private void startHighlightIconAnimation(final KeyguardAffordanceView icon) {
        icon.setImageAlpha(1.0f, true, KeyguardAffordanceHelper.HINT_PHASE1_DURATION,
                Interpolators.FAST_OUT_SLOW_IN, new Runnable() {
                    @Override
                    public void run() {
                        icon.setImageAlpha(icon.getRestingAlpha(),
                                true /* animate */, KeyguardAffordanceHelper.HINT_PHASE1_DURATION,
                                Interpolators.FAST_OUT_SLOW_IN, null);
                    }
                });
    }

    @Override
    public float getMaxTranslationDistance() {
        return (float) Math.hypot(getWidth(), getHeight());
    }

    @Override
    public void onSwipingStarted(boolean rightIcon) {
        mFalsingManager.onAffordanceSwipingStarted(rightIcon);
        boolean camera = getLayoutDirection() == LAYOUT_DIRECTION_RTL ? !rightIcon
                : rightIcon;
        if (camera) {
            mKeyguardBottomArea.bindCameraPrewarmService();
        }
        requestDisallowInterceptTouchEvent(true);
        mOnlyAffordanceInThisMotion = true;
        mQsTracking = false;
    }

    @Override
    public void onSwipingAborted() {
        mFalsingManager.onAffordanceSwipingAborted();
        mKeyguardBottomArea.unbindCameraPrewarmService(false /* launched */);
    }

    @Override
    public void onIconClicked(boolean rightIcon) {
        if (mHintAnimationRunning) {
            return;
        }
        mHintAnimationRunning = true;
        mAfforanceHelper.startHintAnimation(rightIcon, new Runnable() {
            @Override
            public void run() {
                mHintAnimationRunning = false;
                mStatusBar.onHintFinished();
            }
        });
        rightIcon = getLayoutDirection() == LAYOUT_DIRECTION_RTL ? !rightIcon : rightIcon;
        if (rightIcon) {
            mStatusBar.onCameraHintStarted();
        } else {
            if (mKeyguardBottomArea.isLeftVoiceAssist()) {
                mStatusBar.onVoiceAssistHintStarted();
            } else {
                mStatusBar.onPhoneHintStarted();
            }
        }
    }

    @Override
    public KeyguardAffordanceView getLeftIcon() {
        return getLayoutDirection() == LAYOUT_DIRECTION_RTL
                ? mKeyguardBottomArea.getRightView()
                : mKeyguardBottomArea.getLeftView();
    }

    @Override
    public KeyguardAffordanceView getCenterIcon() {
        return mKeyguardBottomArea.getLockIcon();
    }

    @Override
    public KeyguardAffordanceView getRightIcon() {
        return getLayoutDirection() == LAYOUT_DIRECTION_RTL
                ? mKeyguardBottomArea.getLeftView()
                : mKeyguardBottomArea.getRightView();
    }

    @Override
    public View getLeftPreview() {
        return getLayoutDirection() == LAYOUT_DIRECTION_RTL
                ? mKeyguardBottomArea.getRightPreview()
                : mKeyguardBottomArea.getLeftPreview();
    }

    @Override
    public View getRightPreview() {
        return getLayoutDirection() == LAYOUT_DIRECTION_RTL
                ? mKeyguardBottomArea.getLeftPreview()
                : mKeyguardBottomArea.getRightPreview();
    }

    @Override
    public float getAffordanceFalsingFactor() {
        return mStatusBar.isWakeUpComingFromTouch() ? 1.5f : 1.0f;
    }

    @Override
    public boolean needsAntiFalsing() {
        return mStatusBarState == StatusBarState.KEYGUARD;
    }

    @Override
    protected float getPeekHeight() {
        if (mNotificationStackScroller.getNotGoneChildCount() > 0) {
            return mNotificationStackScroller.getPeekHeight();
        } else {
            return mQsMinExpansionHeight;
        }
    }

    @Override
    protected float getCannedFlingDurationFactor() {
        if (mQsExpanded) {
            return 0.7f;
        } else {
            return 0.6f;
        }
    }

    @Override
    protected boolean fullyExpandedClearAllVisible() {
        return mNotificationStackScroller.isDismissViewNotGone()
                && mNotificationStackScroller.isScrolledToBottom() && !mQsExpandImmediate;
    }

    @Override
    protected boolean isClearAllVisible() {
        return mNotificationStackScroller.isDismissViewVisible();
    }

    @Override
    protected int getClearAllHeight() {
        return mNotificationStackScroller.getDismissViewHeight();
    }

    @Override
    protected boolean isTrackingBlocked() {
        return mConflictingQsExpansionGesture && mQsExpanded;
    }

    public boolean isQsExpanded() {
        return mQsExpanded;
    }

    public boolean isQsDetailShowing() {
        return mQsContainer.isShowingDetail();
    }

    public void closeQsDetail() {
        mQsContainer.getQsPanel().closeDetail();
    }

    @Override
    public boolean shouldDelayChildPressedState() {
        return true;
    }

    public boolean isLaunchTransitionFinished() {
        return mIsLaunchTransitionFinished;
    }

    public boolean isLaunchTransitionRunning() {
        return mIsLaunchTransitionRunning;
    }

    public void setLaunchTransitionEndRunnable(Runnable r) {
        mLaunchAnimationEndRunnable = r;
    }

    public void setEmptyDragAmount(float amount) {
        float factor = 0.8f;
        if (mNotificationStackScroller.getNotGoneChildCount() > 0) {
            factor = 0.4f;
        } else if (!mStatusBar.hasActiveNotifications()) {
            factor = 0.4f;
        }
        mEmptyDragAmount = amount * factor;
        positionClockAndNotifications();
    }

    private static float interpolate(float t, float start, float end) {
        return (1 - t) * start + t * end;
    }

    public void setDozing(boolean dozing, boolean animate) {
        if (dozing == mDozing) return;
        mDozing = dozing;
        if (mStatusBarState == StatusBarState.KEYGUARD) {
            updateDozingVisibilities(animate);
        }
    }

    private void updateDozingVisibilities(boolean animate) {
        if (mDozing) {
            mKeyguardStatusBar.setVisibility(View.INVISIBLE);
            mKeyguardBottomArea.setVisibility(View.INVISIBLE);
        } else {
            mKeyguardBottomArea.setVisibility(View.VISIBLE);
            mKeyguardStatusBar.setVisibility(View.VISIBLE);
            if (animate) {
                animateKeyguardStatusBarIn(DOZE_ANIMATION_DURATION);
                mKeyguardBottomArea.startFinishDozeAnimation();
            }
        }
    }

    @Override
    public boolean isDozing() {
        return mDozing;
    }

    public void setShadeEmpty(boolean shadeEmpty) {
        mShadeEmpty = shadeEmpty;
        updateEmptyShadeView();
    }

    private void updateEmptyShadeView() {

        // Hide "No notifications" in QS.
        mNotificationStackScroller.updateEmptyShadeView(mShadeEmpty && !mQsExpanded);
    }

    public void setQsScrimEnabled(boolean qsScrimEnabled) {
        boolean changed = mQsScrimEnabled != qsScrimEnabled;
        mQsScrimEnabled = qsScrimEnabled;
        if (changed) {
            updateQsState();
        }
    }

    public void setKeyguardUserSwitcher(KeyguardUserSwitcher keyguardUserSwitcher) {
        mKeyguardUserSwitcher = keyguardUserSwitcher;
    }

    private final Runnable mUpdateHeader = new Runnable() {
        @Override
        public void run() {
            mQsContainer.getHeader().updateEverything();
        }
    };

    public void onScreenTurningOn() {
        mKeyguardStatusView.refreshTime();
    }

    @Override
    public void onEmptySpaceClicked(float x, float y) {
        onEmptySpaceClick(x);
    }

    @Override
    protected boolean onMiddleClicked() {
        switch (mStatusBar.getBarState()) {
            case StatusBarState.KEYGUARD:
                if (!mDozingOnDown) {
                    EventLogTags.writeSysuiLockscreenGesture(
                            EventLogConstants.SYSUI_LOCKSCREEN_GESTURE_TAP_UNLOCK_HINT,
                            0 /* lengthDp - N/A */, 0 /* velocityDp - N/A */);
                    startUnlockHintAnimation();
                }
                return true;
            case StatusBarState.SHADE_LOCKED:
                if (!mQsExpanded) {
                    mStatusBar.goToKeyguard();
                }
                return true;
            case StatusBarState.SHADE:

                // This gets called in the middle of the touch handling, where the state is still
                // that we are tracking the panel. Collapse the panel after this is done.
                post(mPostCollapseRunnable);
                return false;
            default:
                return true;
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (DEBUG) {
            Paint p = new Paint();
            p.setColor(Color.RED);
            p.setStrokeWidth(2);
            p.setStyle(Paint.Style.STROKE);
            canvas.drawLine(0, getMaxPanelHeight(), getWidth(), getMaxPanelHeight(), p);
            p.setColor(Color.BLUE);
            canvas.drawLine(0, getExpandedHeight(), getWidth(), getExpandedHeight(), p);
            p.setColor(Color.GREEN);
            canvas.drawLine(0, calculatePanelHeightQsExpanded(), getWidth(),
                    calculatePanelHeightQsExpanded(), p);
            p.setColor(Color.YELLOW);
            canvas.drawLine(0, calculatePanelHeightShade(), getWidth(),
                    calculatePanelHeightShade(), p);
            p.setColor(Color.MAGENTA);
            canvas.drawLine(0, calculateQsTopPadding(), getWidth(),
                    calculateQsTopPadding(), p);
            p.setColor(Color.CYAN);
            canvas.drawLine(0, mNotificationStackScroller.getTopPadding(), getWidth(),
                    mNotificationStackScroller.getTopPadding(), p);
        }
    }

    @Override
    public void onHeadsUpPinnedModeChanged(final boolean inPinnedMode) {
        if (inPinnedMode) {
            mHeadsUpExistenceChangedRunnable.run();
            updateNotificationTranslucency();
        } else {
            mHeadsUpAnimatingAway = true;
            mNotificationStackScroller.runAfterAnimationFinished(
                    mHeadsUpExistenceChangedRunnable);
        }
    }

    /*PRIZE-add notificationscrimview-liufan-2016-06-18-start*/
    private Bitmap mNotificationScrimBitmap ;
    public static boolean isHaveHeadsUpNotification = false;
    HashSet<ExpandableNotificationRow> headsUpLayoutSet = new HashSet<ExpandableNotificationRow>();
    /*PRIZE-add notificationscrimview-liufan-2016-06-18-end*/

    @Override
    public void onHeadsUpPinned(ExpandableNotificationRow headsUp) {
        /*PRIZE-set flag true,set background color dark for headsup notification-liufan-2016-06-08-start*/
        isHaveHeadsUpNotification = true;
        Log.d(TAG,"onHeadsUpPinned--->"+headsUpLayoutSet.size());
        NotificationHeaderLayout mNotificationScrimView = (NotificationHeaderLayout)headsUp.findViewById(R.id.notification_scrim_view);
        if(isFullyCollapsed()) findViewById(R.id.notification_bg).setAlpha(0f);
        if(PhoneStatusBar.BLUR_BG_CONTROL){
            if(mNotificationScrimBitmap == null){
                mNotificationScrimBitmap = blur(screenshot());
                //mNotificationScrimBitmap = mStatusBar.blur(null);//update scrim_bg-2017-12-18-liufan
            }
            mNotificationScrimView.setBg(mNotificationScrimBitmap);
        } else {
            mNotificationScrimView.setBackgroundColor(0xdd000000);
        }
        /*PRIZE-Modify for bugid: 31652、31896-zhudaopeng-2017-04-10-Start*/
        // mNotificationScrimView.setVisibility(View.VISIBLE);
        Log.d(TAG,"onHeadsUpPinned---> mExpandedHeight = "+mExpandedHeight);
        if(isFullyCollapsed()) mNotificationScrimView.setVisibility(View.VISIBLE);
        /*PRIZE-Modify for bugid: 31652、31896-zhudaopeng-2017-04-10-End*/
        headsUpLayoutSet.add(headsUp);
        headsUp.setNotificationHeadsUp(true);
        /*PRIZE-Modify for bugid: 31652、31896-zhudaopeng-2017-04-10-Start*/
        // headsUp.findViewById(R.id.notification_head_line).setAlpha(0f);
        if(isFullyCollapsed()) headsUp.findViewById(R.id.notification_head_line).setAlpha(0f);
        /*PRIZE-Modify for bugid: 31652、31896-zhudaopeng-2017-04-10-End*/
        /*PRIZE-set flag true,set background color dark for headsup notification-liufan-2016-06-08-end*/
        mNotificationStackScroller.generateHeadsUpAnimation(headsUp, true);
    }

    @Override
    public void onHeadsUpUnPinned(ExpandableNotificationRow headsUp) {
        /*PRIZE-cancel flag, cancel background color-liufan-2016-06-08-start*/
        final ExpandableNotificationRow row = headsUp;
        final NotificationHeaderLayout mNotificationScrimView = (NotificationHeaderLayout)headsUp.findViewById(R.id.notification_scrim_view);
        headsUpLayoutSet.remove(headsUp);
        Log.d(TAG,"onHeadsUpUnPinned--->"+headsUpLayoutSet.size());
        postDelayed(new Runnable(){
            @Override
            public void run() {
                /*PRIZE-update for bugid:52810-liufan-2018-03-26-start*/
                boolean isContain = headsUpLayoutSet.contains(headsUp);
                if(!isContain){
                    Log.d(TAG,"onHeadsUpUnPinned--isContain is false");
                    mNotificationScrimView.setVisibility(View.GONE);
                    mNotificationScrimView.setBg(null);
                }
                /*PRIZE-update for bugid:52810-liufan-2018-03-26-end*/
            }
        },200);
        if(headsUpLayoutSet.size() == 0) {
            //mStatusBar.showBlurBackground();
            postDelayed(new Runnable(){
                @Override
                public void run() {
                    if(headsUpLayoutSet.size() == 0){
                        Log.d(TAG,"onHeadsUpUnPinned set notification_bg alpha 1");
                        findViewById(R.id.notification_bg).setAlpha(1f);
                        if(mNotificationScrimBitmap != null && !mNotificationScrimBitmap.isRecycled()){
                            mNotificationScrimBitmap.recycle();
                            mNotificationScrimBitmap = null;
                        }
                        isHaveHeadsUpNotification = false;
                    }
                }
            },400);
        }
        headsUp.findViewById(R.id.notification_head_line).setAlpha(1f);
        headsUp.setNotificationHeadsUp(false);
        /*PRIZE-cancel flag, cancel background color-liufan-2016-06-08-end*/
    }

    @Override
    public void onHeadsUpStateChanged(NotificationData.Entry entry, boolean isHeadsUp) {
        mNotificationStackScroller.generateHeadsUpAnimation(entry.row, isHeadsUp);
    }

    @Override
    public void setHeadsUpManager(HeadsUpManager headsUpManager) {
        super.setHeadsUpManager(headsUpManager);
        mHeadsUpTouchHelper = new HeadsUpTouchHelper(headsUpManager, mNotificationStackScroller,
                this);
    }

    public void setTrackingHeadsUp(boolean tracking) {
        if (tracking) {
            mNotificationStackScroller.setTrackingHeadsUp(true);
            mExpandingFromHeadsUp = true;
        }
        // otherwise we update the state when the expansion is finished
    }
    
    /*PRIZE-notification headsup background-liufan-2016-07-07-start*/
    /**
    * 方法描述：模糊图片算法
    */
    private Bitmap blur(Bitmap bitmap){
        long time1 = System.currentTimeMillis();
        if(bitmap!=null){
            bitmap = BlurPic.blurScaleOtherRadius(bitmap,5);
            if(bitmap!=null){
                Canvas canvas = new Canvas(bitmap);
                canvas.drawColor(0xaa000000);
            }
        } else {
            Log.d(TAG,"blur screenshot is null");
            bitmap = Bitmap.createBitmap(8, 8, Config.ARGB_8888);
            if(bitmap!=null){
                Canvas canvas = new Canvas(bitmap);
                canvas.drawColor(0xf4000000);
            }
        }
        long time2 = System.currentTimeMillis();
        Log.d(TAG,"Blur time ------>"+(time2-time1));
        return bitmap;
    }
    
    /**
    * 方法描述：截屏
    */
    private Bitmap screenshot(){
        long time1 = System.currentTimeMillis();
        Bitmap mScreenBitmap = null;
        WindowManager mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        Display mDisplay = mWindowManager.getDefaultDisplay();
        DisplayMetrics mDisplayMetrics = new DisplayMetrics();
        mDisplay.getRealMetrics(mDisplayMetrics);
        float[] dims = {mDisplayMetrics.widthPixels , mDisplayMetrics.heightPixels };
        if (dims[0]>dims[1]) {
            mScreenBitmap = SurfaceControl.screenshot((int) dims[1], (int) dims[0]);
            Matrix matrix = new Matrix();  
            matrix.reset();
            int rotation = mDisplay.getRotation();
            if(rotation==3){//rotation==3 右转
                matrix.setRotate(90);
            }else{//rotation==1 左转
                matrix.setRotate(-90);
            }
            Bitmap bitmap = mScreenBitmap;
            mScreenBitmap = Bitmap.createBitmap(bitmap,0,0, bitmap.getWidth(), bitmap.getHeight(),matrix, true);
            Log.e(TAG,"mScreenBitmap------------rotation-------->"+mScreenBitmap+", width---->"+mScreenBitmap.getWidth()+", height----->"+mScreenBitmap.getHeight());
            bitmap.recycle();
            bitmap = null;
        }else{
            mScreenBitmap = SurfaceControl.screenshot((int) dims[0], ( int) dims[1]);
        }
        long time2 = System.currentTimeMillis();
        Log.d(TAG,"screenshot time ------>"+(time2-time1));
        return mScreenBitmap;
    }
    /*PRIZE-notification headsup background-liufan-2016-07-07-end*/

    @Override
    protected void onClosingFinished() {
        super.onClosingFinished();
        resetVerticalPanelPosition();
        setClosingWithAlphaFadeout(false);
    }

    private void setClosingWithAlphaFadeout(boolean closing) {
        mClosingWithAlphaFadeOut = closing;
        mNotificationStackScroller.forceNoOverlappingRendering(closing);
    }

    /**
     * Updates the vertical position of the panel so it is positioned closer to the touch
     * responsible for opening the panel.
     *
     * @param x the x-coordinate the touch event
     */
    protected void updateVerticalPanelPosition(float x) {
        // M: Fix the display wrong issue when the width = 0 unexpected.
        // Step: set screen lock as none and reboot then
        if (mNotificationStackScroller.getWidth() <= 0
                || mNotificationStackScroller.getWidth() * 1.75f > getWidth()) {
            resetVerticalPanelPosition();
            return;
        }
        float leftMost = mPositionMinSideMargin + mNotificationStackScroller.getWidth() / 2;
        float rightMost = getWidth() - mPositionMinSideMargin
                - mNotificationStackScroller.getWidth() / 2;
        if (Math.abs(x - getWidth() / 2) < mNotificationStackScroller.getWidth() / 4) {
            x = getWidth() / 2;
        }
        x = Math.min(rightMost, Math.max(leftMost, x));
        setVerticalPanelTranslation(x -
                (mNotificationStackScroller.getLeft() + mNotificationStackScroller.getWidth() / 2));
     }

    private void resetVerticalPanelPosition() {
        setVerticalPanelTranslation(0f);
    }

    protected void setVerticalPanelTranslation(float translation) {
        mNotificationStackScroller.setTranslationX(translation);
        mQsAutoReinflateContainer.setTranslationX(translation);
    }

    protected void updateExpandedHeight(float expandedHeight) {
        mNotificationStackScroller.setExpandedHeight(expandedHeight);
        updateKeyguardBottomAreaAlpha();
    }

    public void setPanelScrimMinFraction(float minFraction) {
        mBar.panelScrimMinFractionChanged(minFraction);
    }

    public void clearNotificationEffects() {
        mStatusBar.clearNotificationEffects();
    }

    @Override
    protected boolean isPanelVisibleBecauseOfHeadsUp() {
        return mHeadsUpManager.hasPinnedHeadsUp() || mHeadsUpAnimatingAway;
    }

    @Override
    public boolean hasOverlappingRendering() {
        return !mDozing;
    }

    public void launchCamera(boolean animate, int source) {
        if (source == StatusBarManager.CAMERA_LAUNCH_SOURCE_POWER_DOUBLE_TAP) {
            mLastCameraLaunchSource = KeyguardBottomAreaView.CAMERA_LAUNCH_SOURCE_POWER_DOUBLE_TAP;
        } else if (source == StatusBarManager.CAMERA_LAUNCH_SOURCE_WIGGLE) {
            mLastCameraLaunchSource = KeyguardBottomAreaView.CAMERA_LAUNCH_SOURCE_WIGGLE;
        } else {

            // Default.
            mLastCameraLaunchSource = KeyguardBottomAreaView.CAMERA_LAUNCH_SOURCE_AFFORDANCE;
        }

        // If we are launching it when we are occluded already we don't want it to animate,
        // nor setting these flags, since the occluded state doesn't change anymore, hence it's
        // never reset.
        if (!isFullyCollapsed()) {
            mLaunchingAffordance = true;
            setLaunchingAffordance(true);
        } else {
            animate = false;
        }
        mAfforanceHelper.launchAffordance(animate, getLayoutDirection() == LAYOUT_DIRECTION_RTL);
    }

    public void onAffordanceLaunchEnded() {
        mLaunchingAffordance = false;
        setLaunchingAffordance(false);
    }

    @Override
    public void setAlpha(float alpha) {
        super.setAlpha(alpha);
        mNotificationStackScroller.setParentFadingOut(alpha != 1.0f);
    }

    /**
     * Set whether we are currently launching an affordance. This is currently only set when
     * launched via a camera gesture.
     */
    private void setLaunchingAffordance(boolean launchingAffordance) {
        getLeftIcon().setLaunchingAffordance(launchingAffordance);
        getRightIcon().setLaunchingAffordance(launchingAffordance);
        getCenterIcon().setLaunchingAffordance(launchingAffordance);
    }

    /**
     * Whether the camera application can be launched for the camera launch gesture.
     *
     * @param keyguardIsShowing whether keyguard is being shown
     */
    public boolean canCameraGestureBeLaunched(boolean keyguardIsShowing) {
        if (!mStatusBar.isCameraAllowedByAdmin()) {
            EventLog.writeEvent(0x534e4554, "63787722", -1, "");
            return false;
        }
        ResolveInfo resolveInfo = mKeyguardBottomArea.resolveCameraIntent();
        String packageToLaunch = (resolveInfo == null || resolveInfo.activityInfo == null)
                ? null : resolveInfo.activityInfo.packageName;
        return packageToLaunch != null &&
               (keyguardIsShowing || !isForegroundApp(packageToLaunch)) &&
               !mAfforanceHelper.isSwipingInProgress();
    }

    /**
     * Return true if the applications with the package name is running in foreground.
     *
     * @param pkgName application package name.
     */
    private boolean isForegroundApp(String pkgName) {
        ActivityManager am = getContext().getSystemService(ActivityManager.class);
        List<ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(1);
        return !tasks.isEmpty() && pkgName.equals(tasks.get(0).topActivity.getPackageName());
    }

    public void setGroupManager(NotificationGroupManager groupManager) {
        mGroupManager = groupManager;
    }

    /*PRIZE-add for bugid:43587-liufan-2017-12-12-start*/
    public void changePadding(int padding){
        if(mNotificationContainerParent != null){
            mNotificationContainerParent.changePadding(padding);
        }
    }
    /*PRIZE-add for bugid:43587-liufan-2017-12-12-end*/

	/**PRIZE-haokanscreen iteration one-liufan-2016-09-13-start 
	public static boolean HaokanShow = true;
	public static boolean mIsMove = false;
	public void showLockIcon(boolean isShow){
		if(!mStatusBar.isUseHaoKan() || USE_VLIFE || USE_ZOOKING){
		    mKeyguardBottomArea.refreshView();
			return;
		}
		HaokanShow = isShow;
		setKeyguardStatusViewVisibility(isShow);
		//mStatusBar.isShowBlurBgWhenLockscreen(false);
		mKeyguardBottomArea.refreshView();
		if(isShow){
			//mKeyguardStatusView.setVisibility(View.VISIBLE);
			//mNotificationContainerParent.setVisibility(View.VISIBLE);
			if ((USE_VLIFE && HaokanShow) || USE_ZOOKING) {
				mOpenFlashlightTxt.setVisibility(View.GONE);
			} else {
				mOpenFlashlightTxt.setVisibility(View.VISIBLE);
			}
			if(mStatusBarState == StatusBarState.SHADE_LOCKED
                || mStatusBarState == StatusBarState.KEYGUARD){
			    mKeyguardBottomArea.setVisibility(View.VISIBLE);
			    mKeyguardBottomArea.getCameraImageView().setVisibility(View.VISIBLE);
			    mKeyguardBottomArea.getLeftView().setVisibility(View.VISIBLE);
			} else {
			    mKeyguardBottomArea.setVisibility(View.GONE);
				mKeyguardBottomArea.getCameraImageView().setVisibility(View.GONE);
				mKeyguardBottomArea.getLeftView().setVisibility(View.GONE);
			}
			if(mStatusBar.isShowBlurBg()){
				mStatusBar.setBlurBackBg(0);
			    mLockscreenBlurLayout.setVisibility(View.VISIBLE);
			}else{
			    mLockscreenBlurLayout.setVisibility(View.GONE);
			}
		} else {
			//mKeyguardStatusView.setVisibility(View.GONE);
			//mNotificationContainerParent.setVisibility(View.GONE);
			mOpenFlashlightTxt.setVisibility(View.GONE);
			mKeyguardBottomArea.setVisibility(View.GONE);
			mKeyguardBottomArea.getCameraImageView().setVisibility(View.GONE);
			mKeyguardBottomArea.getLeftView().setVisibility(View.GONE);
			mLockscreenBlurLayout.setVisibility(View.GONE);
		}
		mStatusBar.updateNotifications();
    }
	public void setKeyguardStatusViewVisibility(boolean isShow){
		if(!mStatusBar.isUseHaoKan() || USE_VLIFE || USE_ZOOKING){
			return;
		}

		if((mStatusBarState == StatusBarState.SHADE_LOCKED
                || mStatusBarState == StatusBarState.KEYGUARD) && isShow && HaokanShow){
    		mKeyguardStatusView.setVisibility(View.VISIBLE);
    	} else {
    		mKeyguardStatusView.setVisibility(View.GONE);
    	}
	}

	public void setOpenFlashlightTxtVisibility(boolean isShow){
		if(!mStatusBar.isUseHaoKan() || USE_VLIFE || USE_ZOOKING){//modify by zookingsoft 20161116
			return;
		}
		if(isShow && HaokanShow){
    		mOpenFlashlightTxt.setVisibility(View.VISIBLE);
    	} else {
    		mOpenFlashlightTxt.setVisibility(View.GONE);
    	}
	}
	public void setKeyguardBottomAreaVisibility(boolean isShow){
		if(!mStatusBar.isUseHaoKan() || USE_VLIFE || USE_ZOOKING){//modify by zookingsoft 20161116
			return;
		}
		if((mStatusBarState == StatusBarState.SHADE_LOCKED
                || mStatusBarState == StatusBarState.KEYGUARD) && isShow && HaokanShow){
    		mKeyguardBottomArea.setVisibility(View.VISIBLE);
    		mKeyguardBottomArea.getCameraImageView().setVisibility(View.VISIBLE);
    		mKeyguardBottomArea.getLeftView().setVisibility(View.VISIBLE);
    	} else {
    		mKeyguardBottomArea.setVisibility(View.GONE);
    		mKeyguardBottomArea.getCameraImageView().setVisibility(View.GONE);
    		mKeyguardBottomArea.getLeftView().setVisibility(View.GONE);
    	}
	}

	public void setNPViewAlpha(float offset){
		if(!mStatusBar.isUseHaoKan() || USE_VLIFE || USE_ZOOKING){//modify by zookingsoft 20161116
			return;
		}
		

 		//DensityUtil.screenHeight(mContext);

   	 
		//onHeightUpdated(float expandedHeight)
		
		
		mKeyguardStatusView.setAlpha(offset);
		//mNotificationContainerParent.setAlpha(offset);
		mScrollView.setAlpha(offset);
		mNotificationStackScroller.setAlpha(offset);
		mKeyguardBottomArea.setAlpha(offset);
		mKeyguardBottomArea.getCameraImageView().setAlpha(offset);
		mLockscreenBlurLayout.setAlpha(offset);
		
		//mKeyguardStatusView.setScaleType(View.ScaleType.CENTER);

	}
	
	public void openHaoKanSettings(){
		if(mStatusBar!=null){
			mStatusBar.openHaoKanSettings();
		}
	}
    
    public void startActivityBySystemUI(Intent intent) {
        if(mStatusBar!=null){
            mStatusBar.startActivity(intent, false);
        }
    }
	
	private LinearLayout mLockscreenBlurLayout;

	public void setLockscreenBlurLayout(LinearLayout linearLayout){
        this.mLockscreenBlurLayout = linearLayout;
    }
	
	public void isMovePage(boolean isMove){
    	mIsMove = isMove;
    }
	/**PRIZE-haokanscreen iteration one-liufan-2016-09-13-end */
	
	/*prize-xuchunming-20180413:KeyguradBottomAreaView set faceid info at press powerkey-start*/
	public void nofityPowerKeyPower(boolean interactive) {
		if(mKeyguardBottomArea != null){
			mKeyguardBottomArea.nofityPowerKeyPower(interactive);
		}
    }
	/*prize-xuchunming-20180413:KeyguradBottomAreaView set faceid info at press powerkey-end*/
}
