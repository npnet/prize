/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.systemui.recents.views;

import static android.app.ActivityManager.StackId.INVALID_STACK_ID;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.ActivityManager;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Outline;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.FloatProperty;
import android.util.Property;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewDebug;
import android.view.ViewOutlineProvider;
import android.widget.TextView;
import android.widget.Toast;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.MetricsProto.MetricsEvent;
import com.android.systemui.Interpolators;
import com.android.systemui.R;
import com.android.systemui.recents.Recents;
import com.android.systemui.recents.RecentsActivity;
import com.android.systemui.recents.RecentsConfiguration;
import com.android.systemui.recents.events.EventBus;
import com.android.systemui.recents.events.activity.LaunchTaskEvent;
import com.android.systemui.recents.events.ui.DismissTaskViewEvent;
import com.android.systemui.recents.events.ui.TaskViewDismissedEvent;
import com.android.systemui.recents.events.ui.dragndrop.DragEndCancelledEvent;
import com.android.systemui.recents.events.ui.dragndrop.DragEndEvent;
import com.android.systemui.recents.events.ui.dragndrop.DragStartEvent;
import com.android.systemui.recents.misc.ReferenceCountedTrigger;
import com.android.systemui.recents.misc.SystemServicesProxy;
import com.android.systemui.recents.misc.Utilities;
import com.android.systemui.recents.model.Task;
import com.android.systemui.recents.model.TaskStack;

import java.util.ArrayList;
/*prize-import-liufan-2015-11-03-start*/
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
/*prize-import-liufan-2015-11-03-end*/

import com.mediatek.common.prizeoption.PrizeOption; // prize-add-split screen-liyongli-20170727 add
import android.provider.Settings; // prize-add-split screen-liyongli  for PRIZE_OLD_LAUNCHER

/*prize-add by lihuangyuan,for fingerapplock-2017-07-04-start*/
import android.widget.RelativeLayout;
import android.widget.ImageView;
import com.mediatek.common.prizeoption.PrizeOption;
import android.util.Log;
import com.android.systemui.statusbar.phone.BlurPic;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
/*prize-add by lihuangyuan,for fingerapplock-2017-07-04-end*/

/*prize add by xiarui 2017-11-21 start*/
import android.graphics.Bitmap;
import android.content.res.Configuration;
import com.android.systemui.recents.LogUtils;
/*prize add by xiarui 2017-11-21 end*/

/**
 * A {@link TaskView} represents a fixed view of a task. Because the TaskView's layout is directed
 * solely by the {@link TaskStackView}, we make it a fixed size layout which allows relayouts down
 * the view hierarchy, but not upwards from any of its children (the TaskView will relayout itself
 * with the previous bounds if any child requests layout).
 */
public class TaskView extends FixedSizeFrameLayout implements Task.TaskCallbacks,
        TaskStackAnimationHelper.Callbacks, View.OnClickListener, View.OnLongClickListener {

    /** The TaskView callbacks */
    interface TaskViewCallbacks {
        void onTaskViewClipStateChanged(TaskView tv);
    }

    /**
     * The dim overlay is generally calculated from the task progress, but occasionally (like when
     * launching) needs to be animated independently of the task progress.  This call is only used
     * when animating the task into Recents, when the header dim is already applied
     */
    public static final Property<TaskView, Float> DIM_ALPHA_WITHOUT_HEADER =
            new FloatProperty<TaskView>("dimAlphaWithoutHeader") {
                @Override
                public void setValue(TaskView tv, float dimAlpha) {
                    tv.setDimAlphaWithoutHeader(dimAlpha);
                }

                @Override
                public Float get(TaskView tv) {
                    return tv.getDimAlpha();
                }
            };

    /**
     * The dim overlay is generally calculated from the task progress, but occasionally (like when
     * launching) needs to be animated independently of the task progress.
     */
    public static final Property<TaskView, Float> DIM_ALPHA =
            new FloatProperty<TaskView>("dimAlpha") {
                @Override
                public void setValue(TaskView tv, float dimAlpha) {
                    tv.setDimAlpha(dimAlpha);
                }

                @Override
                public Float get(TaskView tv) {
                    return tv.getDimAlpha();
                }
            };

    /**
     * The dim overlay is generally calculated from the task progress, but occasionally (like when
     * launching) needs to be animated independently of the task progress.
     */
    public static final Property<TaskView, Float> VIEW_OUTLINE_ALPHA =
            new FloatProperty<TaskView>("viewOutlineAlpha") {
                @Override
                public void setValue(TaskView tv, float alpha) {
                    tv.getViewBounds().setAlpha(alpha);
                }

                @Override
                public Float get(TaskView tv) {
                    return tv.getViewBounds().getAlpha();
                }
            };

    @ViewDebug.ExportedProperty(category="recents")
    private float mDimAlpha;
    /*prize add by xiarui 2017-11-09 start*/
    private ImageView mLockAtAppImgView;
    private float mHeaderAlpha;
    /*prize add by xiarui 2017-11-09 end*/
    private float mActionButtonTranslationZ;

    @ViewDebug.ExportedProperty(deepExport=true, prefix="task_")
    private Task mTask;
    @ViewDebug.ExportedProperty(category="recents")
    private boolean mClipViewInStack = true;
    @ViewDebug.ExportedProperty(category="recents")
    private boolean mTouchExplorationEnabled;
    @ViewDebug.ExportedProperty(category="recents")
    private boolean mIsDisabledInSafeMode;
    @ViewDebug.ExportedProperty(deepExport=true, prefix="view_bounds_")
    private AnimateableViewBounds mViewBounds;

    private AnimatorSet mTransformAnimation;
    private ObjectAnimator mDimAnimator;
    private ObjectAnimator mOutlineAnimator;
    private final TaskViewTransform mTargetAnimationTransform = new TaskViewTransform();
    private ArrayList<Animator> mTmpAnimators = new ArrayList<>();

    @ViewDebug.ExportedProperty(deepExport=true, prefix="thumbnail_")
    TaskViewThumbnail mThumbnailView;
    @ViewDebug.ExportedProperty(deepExport=true, prefix="header_")
    TaskViewHeader mHeaderView;
    private View mActionButtonView;
    private View mIncompatibleAppToastView;
    private TaskViewCallbacks mCb;

    @ViewDebug.ExportedProperty(category="recents")
    private Point mDownTouchPos = new Point();
    /*prize-add by lihuangyuan,for fingerapplock-2017-07-04-start*/
    RelativeLayout mBlurScrimLayout;
    ImageView mBlurScrimIcon;
    /*prize-add by lihuangyuan,for fingerapplock-2017-07-04-end*/
    private Toast mDisabledAppToast;

    public TaskView(Context context) {
        this(context, null);
    }

    public TaskView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TaskView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public TaskView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        RecentsConfiguration config = Recents.getConfiguration();
        Resources res = context.getResources();
        mViewBounds = new AnimateableViewBounds(this, res.getDimensionPixelSize(
                R.dimen.recents_task_view_shadow_rounded_corners_radius));
        if (config.fakeShadows) {
            setBackground(new FakeShadowDrawable(res, config));
        }
        setOutlineProvider(mViewBounds);
        setOnLongClickListener(this);
    }

    /** Set callback */
    void setCallbacks(TaskViewCallbacks cb) {
        mCb = cb;
    }

    /**
     * Called from RecentsActivity when it is relaunched.
     */
    void onReload(boolean isResumingFromVisible) {
        resetNoUserInteractionState();
        if (!isResumingFromVisible) {
            resetViewProperties();
        }
    }

    /** Gets the task */
    public Task getTask() {
        return mTask;
    }

    /** Returns the view bounds. */
    AnimateableViewBounds getViewBounds() {
        return mViewBounds;
    }

    @Override
    protected void onFinishInflate() {
        //prize add by xiarui 2017-11-10 start*/
        if (PrizeOption.PRIZE_SYSTEMUI_RECENTS) {
            mLockAtAppImgView = (ImageView) findViewById(R.id.lock_at_app_icon_src);
            /* prize disable by xiarui for bug#53405 2018-3-23
            mLockAtAppImgView.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    mTask.setLock(getContext(), false);
                    setLockLayoutVisible(mTask.isLock);
                }
            });
            */
        }
        //prize add by xiarui 2017-11-10 end*/
        // Bind the views
        mHeaderView = (TaskViewHeader) findViewById(R.id.task_view_bar);
        mThumbnailView = (TaskViewThumbnail) findViewById(R.id.task_view_thumbnail);
        mThumbnailView.updateClipToTaskBar(mHeaderView);
        /*prize-add by lihuangyuan,for fingerapplock-2017-07-04-start*/
        mBlurScrimLayout = (RelativeLayout) findViewById(R.id.blur_scrim_layout);
        mBlurScrimIcon = (ImageView) findViewById(R.id.blur_scrim_icon);
        /*prize-add by lihuangyuan,for fingerapplock-2017-07-04-end*/
        mActionButtonView = findViewById(R.id.lock_to_app_fab);

        /*prize delete by xiarui 2017-11-28 start*/
        if (!PrizeOption.PRIZE_SYSTEMUI_RECENTS) {
            mActionButtonView.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    // Set the outline to match the FAB background
                    outline.setOval(0, 0, mActionButtonView.getWidth(), mActionButtonView.getHeight());
                    outline.setAlpha(0.35f);
                }
            });
        }
        /*prize delete by xiarui 2017-11-28 end*/

        mActionButtonView.setOnClickListener(this);
        mActionButtonTranslationZ = mActionButtonView.getTranslationZ();
    }

    /**
     * Update the task view when the configuration changes.
     */
    void onConfigurationChanged() {
        mHeaderView.onConfigurationChanged();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0) {
            mHeaderView.onTaskViewSizeChanged(w, h);
            mThumbnailView.onTaskViewSizeChanged(w, h);

            /*prize delete by xiarui 2017-11-28 start*/
            if (!PrizeOption.PRIZE_SYSTEMUI_RECENTS) {
                mActionButtonView.setTranslationX(w - getMeasuredWidth());
                mActionButtonView.setTranslationY(h - getMeasuredHeight());
            }
            /*prize delete by xiarui 2017-11-28 end*/
        }
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            mDownTouchPos.set((int) (ev.getX() * getScaleX()), (int) (ev.getY() * getScaleY()));
        }
        return super.onInterceptTouchEvent(ev);
    }


    @Override
    protected void measureContents(int width, int height) {
        int widthWithoutPadding = width - mPaddingLeft - mPaddingRight;
        int heightWithoutPadding = height - mPaddingTop - mPaddingBottom;
        int widthSpec = MeasureSpec.makeMeasureSpec(widthWithoutPadding, MeasureSpec.EXACTLY);
        int heightSpec = MeasureSpec.makeMeasureSpec(heightWithoutPadding, MeasureSpec.EXACTLY);

        // Measure the content
        measureChildren(widthSpec, heightSpec);

        setMeasuredDimension(width, height);
    }

    void updateViewPropertiesToTaskTransform(TaskViewTransform toTransform,
            AnimationProps toAnimation, ValueAnimator.AnimatorUpdateListener updateCallback) {
        RecentsConfiguration config = Recents.getConfiguration();
        cancelTransformAnimation();

        // Compose the animations for the transform
        mTmpAnimators.clear();
        toTransform.applyToTaskView(this, mTmpAnimators, toAnimation, !config.fakeShadows);
        if (toAnimation.isImmediate()) {
            if (Float.compare(getDimAlpha(), toTransform.dimAlpha) != 0) {
                setDimAlpha(toTransform.dimAlpha);
            }
            if (Float.compare(mViewBounds.getAlpha(), toTransform.viewOutlineAlpha) != 0) {
                mViewBounds.setAlpha(toTransform.viewOutlineAlpha);
            }
            // Manually call back to the animator listener and update callback
            if (toAnimation.getListener() != null) {
                toAnimation.getListener().onAnimationEnd(null);
            }
            if (updateCallback != null) {
                updateCallback.onAnimationUpdate(null);
            }
        } else {
            // Both the progress and the update are a function of the bounds movement of the task
            if (Float.compare(getDimAlpha(), toTransform.dimAlpha) != 0) {
                mDimAnimator = ObjectAnimator.ofFloat(this, DIM_ALPHA, getDimAlpha(),
                        toTransform.dimAlpha);
                mTmpAnimators.add(toAnimation.apply(AnimationProps.BOUNDS, mDimAnimator));
            }
            if (Float.compare(mViewBounds.getAlpha(), toTransform.viewOutlineAlpha) != 0) {
                mOutlineAnimator = ObjectAnimator.ofFloat(this, VIEW_OUTLINE_ALPHA,
                        mViewBounds.getAlpha(), toTransform.viewOutlineAlpha);
                mTmpAnimators.add(toAnimation.apply(AnimationProps.BOUNDS, mOutlineAnimator));
            }
            if (updateCallback != null) {
                ValueAnimator updateCallbackAnim = ValueAnimator.ofInt(0, 1);
                updateCallbackAnim.addUpdateListener(updateCallback);
                mTmpAnimators.add(toAnimation.apply(AnimationProps.BOUNDS, updateCallbackAnim));
            }

            // Create the animator
            mTransformAnimation = toAnimation.createAnimator(mTmpAnimators);
            mTransformAnimation.start();
            mTargetAnimationTransform.copyFrom(toTransform);
        }
    }

    /** Resets this view's properties */
    void resetViewProperties() {
        cancelTransformAnimation();
        setDimAlpha(0);
        setVisibility(View.VISIBLE);
        getViewBounds().reset();
        getHeaderView().reset();
        TaskViewTransform.reset(this);

        mActionButtonView.setScaleX(1f);
        mActionButtonView.setScaleY(1f);
        mActionButtonView.setAlpha(0f);
        mActionButtonView.setTranslationX(0f);
        mActionButtonView.setTranslationY(0f);
        mActionButtonView.setTranslationZ(mActionButtonTranslationZ);
        if (mIncompatibleAppToastView != null) {
            mIncompatibleAppToastView.setVisibility(View.INVISIBLE);
        }
    }

    /**
     * @return whether we are animating towards {@param transform}
     */
    boolean isAnimatingTo(TaskViewTransform transform) {
        return mTransformAnimation != null && mTransformAnimation.isStarted()
                && mTargetAnimationTransform.isSame(transform);
    }

    /**
     * Cancels any current transform animations.
     */
    public void cancelTransformAnimation() {
        Utilities.cancelAnimationWithoutCallbacks(mTransformAnimation);
        Utilities.cancelAnimationWithoutCallbacks(mDimAnimator);
        Utilities.cancelAnimationWithoutCallbacks(mOutlineAnimator);
    }

    /** Enables/disables handling touch on this task view. */
    void setTouchEnabled(boolean enabled) {
        setOnClickListener(enabled ? this : null);
    }

    /** Animates this task view if the user does not interact with the stack after a certain time. */
    void startNoUserInteractionAnimation() {
        mHeaderView.startNoUserInteractionAnimation();
    }

    /** Mark this task view that the user does has not interacted with the stack after a certain time. */
    void setNoUserInteractionState() {
        mHeaderView.setNoUserInteractionState();
    }

    /** Resets the state tracking that the user has not interacted with the stack after a certain time. */
    void resetNoUserInteractionState() {
        mHeaderView.resetNoUserInteractionState();
    }

    /** Dismisses this task. */
    void dismissTask() {
        // Animate out the view and call the callback
        final TaskView tv = this;
        DismissTaskViewEvent dismissEvent = new DismissTaskViewEvent(tv);
        dismissEvent.addPostAnimationCallback(new Runnable() {
            @Override
            public void run() {
                EventBus.getDefault().send(new TaskViewDismissedEvent(mTask, tv,
                        new AnimationProps(TaskStackView.DEFAULT_SYNC_STACK_DURATION,
                                Interpolators.FAST_OUT_SLOW_IN)));
            }
        });
        EventBus.getDefault().send(dismissEvent);
    }
	
    /*prize-dismiss task-liufan-2016-11-18-start*/
    public void dismissTask(final Runnable r) {
        // Animate out the view and call the callback
        final TaskView tv = this;
        DismissTaskViewEvent dismissEvent = new DismissTaskViewEvent(tv);
        dismissEvent.addPostAnimationCallback(new Runnable() {
            @Override
            public void run() {
                if(r!=null){
                    r.run();
                }
            }
        });
        EventBus.getDefault().send(dismissEvent);
    }

    public void dismissTask(long delayed) {
        postDelayed(new Runnable(){
            @Override
            public void run() {
                dismissTask(null);
            }
        }, delayed);
    }

    public void dismissTask(long delayed, final Runnable r) {
        postDelayed(new Runnable(){
            @Override
            public void run() {
                dismissTask(r);
            }
        }, delayed);
    }
    /*prize-dismiss task-liufan-2016-11-18-end*/

    /**
     * Returns whether this view should be clipped, or any views below should clip against this
     * view.
     */
    boolean shouldClipViewInStack() {
        // Never clip for freeform tasks or if invisible
        if (mTask.isFreeformTask() || getVisibility() != View.VISIBLE) {
            return false;
        }
        return mClipViewInStack;
    }

    /** Sets whether this view should be clipped, or clipped against. */
    void setClipViewInStack(boolean clip) {
        if (clip != mClipViewInStack) {
            mClipViewInStack = clip;
            if (mCb != null) {
                mCb.onTaskViewClipStateChanged(this);
            }
        }
    }

    public TaskViewHeader getHeaderView() {
        return mHeaderView;
    }

    /**
     * Sets the current dim.
     */
    public void setDimAlpha(float dimAlpha) {
        mDimAlpha = dimAlpha;
        /*prize delete by xiarui 2017-11-09 start*/
        if (!PrizeOption.PRIZE_SYSTEMUI_RECENTS) {
            mThumbnailView.setDimAlpha(dimAlpha);
            mHeaderView.setDimAlpha(dimAlpha);
        }
        /*prize delete by xiarui 2017-11-09 end*/
    }

    /**prize add by xiarui 2017-11-09 start**/

    /**
     * set header alpha
     * @param headerAlpha
     */
    public void setHeaderAlpha(float headerAlpha) {
        mHeaderAlpha = headerAlpha;
        mHeaderView.setHeaderAlhpa(headerAlpha);
    }

    /**
     * get header alpha
     * @return
     */
    public float getHeaderAlpha() {
        return mHeaderAlpha;
    }
    /**prize add by xiarui 2017-11-09 end**/

    /**
     * Sets the current dim without updating the header's dim.
     */
    public void setDimAlphaWithoutHeader(float dimAlpha) {
        mDimAlpha = dimAlpha;
        mThumbnailView.setDimAlpha(dimAlpha);
    }

    /**
     * Returns the current dim.
     */
    public float getDimAlpha() {
        return mDimAlpha;
    }

    /**
     * Explicitly sets the focused state of this task.
     */
    public void setFocusedState(boolean isFocused, boolean requestViewFocus) {
        if (isFocused) {
            if (requestViewFocus && !isFocused()) {
                requestFocus();
            }
        } else {
            if (isAccessibilityFocused() && mTouchExplorationEnabled) {
                clearAccessibilityFocus();
            }
        }
    }

    /**
     * Shows the action button.
     * @param fadeIn whether or not to animate the action button in.
     * @param fadeInDuration the duration of the action button animation, only used if
     *                       {@param fadeIn} is true.
     */
    public void showActionButton(boolean fadeIn, int fadeInDuration) {
        mActionButtonView.setVisibility(View.VISIBLE);
        if (fadeIn && mActionButtonView.getAlpha() < 1f) {
            mActionButtonView.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(fadeInDuration)
                    .setInterpolator(Interpolators.ALPHA_IN)
                    .start();
        } else {
            mActionButtonView.setScaleX(1f);
            mActionButtonView.setScaleY(1f);
            mActionButtonView.setAlpha(1f);
            mActionButtonView.setTranslationZ(mActionButtonTranslationZ);
        }
    }

    /**
     * Immediately hides the action button.
     *
     * @param fadeOut whether or not to animate the action button out.
     */
    public void hideActionButton(boolean fadeOut, int fadeOutDuration, boolean scaleDown,
            final Animator.AnimatorListener animListener) {
            if (fadeOut && mActionButtonView.getAlpha() > 0f) {
                if (scaleDown) {
                    float toScale = 0.9f;
                    mActionButtonView.animate()
                            .scaleX(toScale)
                            .scaleY(toScale);
                }
                mActionButtonView.animate()
                        .alpha(0f)
                        .setDuration(fadeOutDuration)
                        .setInterpolator(Interpolators.ALPHA_OUT)
                        .withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                if (animListener != null) {
                                    animListener.onAnimationEnd(null);
                                }
                                mActionButtonView.setVisibility(View.INVISIBLE);
                            }
                        })
                        .start();
            } else {
                mActionButtonView.setAlpha(0f);
                mActionButtonView.setVisibility(View.INVISIBLE);
                if (animListener != null) {
                    animListener.onAnimationEnd(null);
                }
            }
    }

    /**** TaskStackAnimationHelper.Callbacks Implementation ****/

    @Override
    public void onPrepareLaunchTargetForEnterAnimation() {
        // These values will be animated in when onStartLaunchTargetEnterAnimation() is called
        setDimAlphaWithoutHeader(0);
        mActionButtonView.setAlpha(0f);
        if (mIncompatibleAppToastView != null &&
                mIncompatibleAppToastView.getVisibility() == View.VISIBLE) {
            mIncompatibleAppToastView.setAlpha(0f);
        }
    }

    /*prize-get and save App lock flag-liufan-2015-11-26-start*/
    public static final String[] default_LockList = new String[]{
        "com.prize.appcenter",
        "com.prize.music",
        "com.tencent.mm",
        "com.tencent.mobileqq"
    };
    public static boolean isLock(Task task, Context context){
        if(task != null){
            String key = task.key.getComponent().getClassName();
            SharedPreferences sp = context.getSharedPreferences("Lock_at_app", Context.MODE_PRIVATE);
            int result = sp.getInt(key, -1);
            if(result == -1){
                boolean isDefaultLock = false;
                for(String name : default_LockList){
                    if(key.contains(name)){
                        isDefaultLock = true;
                        break;
                    }
                }
                return isDefaultLock;
            }else{
                return result == 0 ? false : true;
            }
        }
        return false;
    }
//
//    public void setLock(boolean isLock, boolean force){//force to 0
//        String key = mTask.key.getComponent().getClassName();
//        SharedPreferences sp = getContext().getSharedPreferences("Lock_at_app", Context.MODE_PRIVATE);
//        Editor ed = sp.edit();
//        ed.putInt(key, isLock ? 1 : force ? 0 : sp.getInt(key, -1));
//        ed.commit();
//        mTask.isLock = isLock;
//    }

    public static void setLock(Context context, String key, boolean isLock){
        SharedPreferences sp = context.getSharedPreferences("Lock_at_app", Context.MODE_PRIVATE);
        Editor ed = sp.edit();
        ed.putInt(key, isLock ? 1 : 0);
        ed.commit();
    }

    /**prize add by xiarui 2017-11-09 start**/

    /**
     * set lock layout visible
     * @param isVisible
     */
    public void setLockLayoutVisible(boolean isVisible){
        int visible = isVisible ? VISIBLE : INVISIBLE;
        if (mLockAtAppImgView.getVisibility() != visible) {
            mLockAtAppImgView.setVisibility(visible);
            requestLayout();
        }
    }
    /**prize add by xiarui 2017-11-09 end**/

    /*prize-get and save App lock flag-liufan-2015-11-26-end*/

    @Override
    public void onStartLaunchTargetEnterAnimation(TaskViewTransform transform, int duration,
            boolean screenPinningEnabled, ReferenceCountedTrigger postAnimationTrigger) {
        Utilities.cancelAnimationWithoutCallbacks(mDimAnimator);

        // Dim the view after the app window transitions down into recents
        postAnimationTrigger.increment();
        AnimationProps animation = new AnimationProps(duration, Interpolators.ALPHA_OUT);
        mDimAnimator = animation.apply(AnimationProps.DIM_ALPHA, ObjectAnimator.ofFloat(this,
                DIM_ALPHA_WITHOUT_HEADER, getDimAlpha(), transform.dimAlpha));
        mDimAnimator.addListener(postAnimationTrigger.decrementOnAnimationEnd());
        mDimAnimator.start();

        if (screenPinningEnabled) {
            showActionButton(true /* fadeIn */, duration /* fadeInDuration */);
        }

        if (mIncompatibleAppToastView != null &&
                mIncompatibleAppToastView.getVisibility() == View.VISIBLE) {
            mIncompatibleAppToastView.animate()
                    .alpha(1f)
                    .setDuration(duration)
                    .setInterpolator(Interpolators.ALPHA_IN)
                    .start();
        }
    }

    @Override
    public void onStartLaunchTargetLaunchAnimation(int duration, boolean screenPinningRequested,
            ReferenceCountedTrigger postAnimationTrigger) {
        Utilities.cancelAnimationWithoutCallbacks(mDimAnimator);

        // Un-dim the view before/while launching the target
        AnimationProps animation = new AnimationProps(duration, Interpolators.ALPHA_OUT);
        mDimAnimator = animation.apply(AnimationProps.DIM_ALPHA, ObjectAnimator.ofFloat(this,
                DIM_ALPHA, getDimAlpha(), 0));
        mDimAnimator.start();

        postAnimationTrigger.increment();
        hideActionButton(true /* fadeOut */, duration,
                !screenPinningRequested /* scaleDown */,
                postAnimationTrigger.decrementOnAnimationEnd());
    }

    @Override
    public void onStartFrontTaskEnterAnimation(boolean screenPinningEnabled) {
        if (screenPinningEnabled) {
            showActionButton(false /* fadeIn */, 0 /* fadeInDuration */);
        }
    }

    /**** TaskCallbacks Implementation ****/

    public void onTaskBound(Task t, boolean touchExplorationEnabled, int displayOrientation,
            Rect displayRect) {
        SystemServicesProxy ssp = Recents.getSystemServices();
        mTouchExplorationEnabled = touchExplorationEnabled;
        mTask = t;
        mTask.addCallback(this);
        mIsDisabledInSafeMode = !mTask.isSystemApp && ssp.isInSafeMode();
        mThumbnailView.bindToTask(mTask, mIsDisabledInSafeMode, displayOrientation, displayRect);
        mHeaderView.bindToTask(mTask, mTouchExplorationEnabled, mIsDisabledInSafeMode);

        if (!t.isDockable && ssp.hasDockedTask()) {
            if (mIncompatibleAppToastView == null) {
                /* prize-add-split screen-liyongli-20170727-start */
                if(PrizeOption.PRIZE_SPLIT_SCREEN_HINT_NOT_SPLITE) {
                    mIncompatibleAppToastView = Utilities.findViewStubById(this,
                            R.id.prize_incompatible_app_toast_stub).inflate();
                    TextView msg = (TextView) findViewById(R.id.message);
                    msg.setText(R.string.recents_incompatible_app_message);
                }else
                /* prize-add-split screen-liyongli-20170727-end */
                {
                mIncompatibleAppToastView = Utilities.findViewStubById(this,
                        R.id.incompatible_app_toast_stub).inflate();
                TextView msg = (TextView) findViewById(com.android.internal.R.id.message);
                msg.setText(R.string.recents_incompatible_app_message);
                }
            }
            mIncompatibleAppToastView.setVisibility(View.VISIBLE);
        } else if (mIncompatibleAppToastView != null) {
            mIncompatibleAppToastView.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    public void onTaskDataLoaded(Task task, ActivityManager.TaskThumbnailInfo thumbnailInfo) {
        // Update each of the views to the new task data
        mThumbnailView.onTaskDataLoaded(thumbnailInfo);
        mHeaderView.onTaskDataLoaded();
        /*prize-add by lihuangyuan,for fingerapplock-2017-07-04-start*/
        //Log.d("EncryptionData","TaskView.onTaskDataLoaded!");
        /**
         * prize delete by xiarui 2017-11-16
         * see  {@link TaskViewThumbnail#setThumbnail(Bitmap, ActivityManager.TaskThumbnailInfo)} {@link TaskViewThumbnail#onDraw(Canvas)}
         * **/
        if (!PrizeOption.PRIZE_SYSTEMUI_RECENTS) {
            blurTaskView(mTask);
        }
        /*prize-add by lihuangyuan,for fingerapplock-2017-07-04-end*/

        /*prize add by xiarui 2017-11-10 start*/
        if (PrizeOption.PRIZE_SYSTEMUI_RECENTS) {
            setLockLayoutVisible(task.isLock(getContext()));
        }
        /*prize add by xiarui 2017-11-10 end*/
    }

    @Override
    public void onTaskDataUnloaded() {
        // Unbind each of the views from the task and remove the task callback
        mTask.removeCallback(this);
        mThumbnailView.unbindFromTask();
        mHeaderView.unbindFromTask(mTouchExplorationEnabled);
        /*prize-add by lihuangyuan,for fingerapplock-2017-07-04-start*/
        //Log.d("EncryptionData","TaskView.onTaskDataUnloaded!");
        /**prize delete by xiarui 2017-11-16 start
         * see {@link TaskView#onTaskDataLoaded } has delete blurTaskView(Task t), replace by  {@link  TaskViewThumbnail#onDraw(Canvas)}
         * **/
        if (!PrizeOption.PRIZE_SYSTEMUI_RECENTS) {
            recycleBlurBitmap(mTask);
        }
        /**prize delete by xiarui 2017-11-16 end**/
        /*prize-add by lihuangyuan,for fingerapplock-2017-07-04-end*/
    }

    @Override
    public void onTaskStackIdChanged() {
        // Force rebind the header, the thumbnail does not change due to stack changes
        mHeaderView.bindToTask(mTask, mTouchExplorationEnabled, mIsDisabledInSafeMode);
        mHeaderView.onTaskDataLoaded();
    }
    /*prize-add by lihuangyuan,for fingerapplock-2017-07-04-start*/
    private void blurTaskView(Task t) {
        Log.d("EncryptionData","t.isEncryption:"+t.isEncryption+",t.encryption.className:"+t.key.getComponent().getClassName());
        if(PrizeOption.PRIZE_FINGERPRINT_APPLOCK && t.isEncryption && mBlurScrimLayout != null) {
            if (t.blurScrimeBitmap != null && !t.blurScrimeBitmap.isRecycled()) {
                t.blurScrimeBitmap.recycle();
                t.blurScrimeBitmap = null;
            }
            if (t.thumbnail != null) {
                t.blurScrimeBitmap = BlurPic.blurScaleOtherRadius(t.thumbnail,3);
                Canvas canvas = new Canvas(t.blurScrimeBitmap);
                canvas.drawColor(0x1a000000);
                mBlurScrimLayout.setBackground(new BitmapDrawable(getContext().getResources(), t.blurScrimeBitmap));
            }
            if(t.launcherIcon!=null){
                mBlurScrimIcon.setImageDrawable(t.launcherIcon);
            } else if(t.icon !=null){
                mBlurScrimIcon.setImageDrawable(t.icon);
            } /*else if(t.applicationIcon!=null){
                mBlurScrimIcon.setImageDrawable(t.applicationIcon);
            }*/
            mBlurScrimLayout.setVisibility(View.VISIBLE);
        }else{
            mBlurScrimLayout.setVisibility(View.GONE);
        }
    }

    private void recycleBlurBitmap(Task t){
        mBlurScrimLayout.setBackground(null);
        mBlurScrimIcon.setImageDrawable(null);
        mBlurScrimLayout.setVisibility(View.GONE);
        if(t.blurScrimeBitmap!=null && !t.blurScrimeBitmap.isRecycled()) {
            t.blurScrimeBitmap.recycle();
            t.blurScrimeBitmap = null;
        }
    }
    /*prize-add by lihuangyuan,for fingerapplock-2017-07-04-start*/
    /**** View.OnClickListener Implementation ****/

    @Override
     public void onClick(final View v) {
        if (mIsDisabledInSafeMode) {
            Context context = getContext();
            String msg = context.getString(R.string.recents_launch_disabled_message, mTask.title);
            if (mDisabledAppToast != null) {
                mDisabledAppToast.cancel();
            }
            mDisabledAppToast = Toast.makeText(context, msg, Toast.LENGTH_SHORT);
            mDisabledAppToast.show();
            return;
        }

        boolean screenPinningRequested = false;
        if (v == mActionButtonView) {
            // Reset the translation of the action button before we animate it out
            mActionButtonView.setTranslationZ(0f);
            screenPinningRequested = true;
        }
        EventBus.getDefault().send(new LaunchTaskEvent(this, mTask, null, INVALID_STACK_ID,
                screenPinningRequested));

        MetricsLogger.action(v.getContext(), MetricsEvent.ACTION_OVERVIEW_SELECT,
                mTask.key.getComponent().toString());
    }

    /**** View.OnLongClickListener Implementation ****/

    @Override
    public boolean onLongClick(View v) {
        /* prize-add-split screen, PRIZE_OLD_LAUNCHER not use split screnn-liyongli-20171125-start */
        if (PrizeOption.PRIZE_OLD_LAUNCHER){
            int isOldLauncher = Settings.System.getInt(getContext().getContentResolver(), Settings.System.PRIZE_OLD_LAUNCHER, 0);
            if(isOldLauncher==1){
                Log.i("systemui"," onLongClick TaskView, PRIZE_OLD_LAUNCHER return");
                return false;
            }
        }/* prize-add-split screen, PRIZE_OLD_LAUNCHER not use split screnn-liyongli-20171125-end */
        
        SystemServicesProxy ssp = Recents.getSystemServices();
        // Since we are clipping the view to the bounds, manually do the hit test
        Rect clipBounds = new Rect(mViewBounds.mClipBounds);
        clipBounds.scale(getScaleX());
        boolean inBounds = clipBounds.contains(mDownTouchPos.x, mDownTouchPos.y);
        if (v == this && inBounds && !ssp.hasDockedTask()) {
            // Start listening for drag events
            setClipViewInStack(false);

            mDownTouchPos.x += ((1f - getScaleX()) * getWidth()) / 2;
            mDownTouchPos.y += ((1f - getScaleY()) * getHeight()) / 2;

            EventBus.getDefault().register(this, RecentsActivity.EVENT_BUS_PRIORITY + 1);
            EventBus.getDefault().send(new DragStartEvent(mTask, this, mDownTouchPos));
            return true;
        }
        return false;
    }

    /**** Events ****/

    public final void onBusEvent(DragEndEvent event) {
        if (!(event.dropTarget instanceof TaskStack.DockState)) {
            event.addPostAnimationCallback(() -> {
                // Reset the clip state for the drag view after the end animation completes
                setClipViewInStack(true);
            });
        }
        EventBus.getDefault().unregister(this);
    }

    public final void onBusEvent(DragEndCancelledEvent event) {
        // Reset the clip state for the drag view after the cancel animation completes
        event.addPostAnimationCallback(() -> {
            setClipViewInStack(true);
        });
    }
}
