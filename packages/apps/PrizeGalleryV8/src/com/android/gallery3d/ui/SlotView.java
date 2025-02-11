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

package com.android.gallery3d.ui;

import java.util.ArrayList;
import java.util.HashMap;

import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Handler;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.animation.DecelerateInterpolator;

import com.android.gallery3d.anim.Animation;
import com.android.gallery3d.app.AbstractGalleryActivity;
import com.android.gallery3d.app.AlbumSetPage;
import com.android.gallery3d.app.Config;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.glrenderer.GLCanvas;
import com.android.gallery3d.data.LocalVideo;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.data.MediaObject;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.app.GalleryActionBar;

import com.android.gallery3d.util.LogUtil;
import com.mediatek.gallery3d.layout.FancyHelper;
import com.mediatek.gallery3d.layout.FancyLayout;
import com.mediatek.gallery3d.layout.Layout;
import com.mediatek.gallery3d.layout.FancyPaper;
import com.mediatek.galleryfeature.config.FeatureConfig;
import com.mediatek.galleryframework.base.MediaData.MediaType;
import com.prize.slideselect.ISelectMode;
import com.prize.util.GloblePrizeUtil;

/// M: [FEATURE.MODIFY] fancy layout @{
//public class SlotView extends GLView {
/**
 * Implement Layout.DataChangeListener.
 */
public class SlotView extends GLView implements Layout.DataChangeListener {
/// @}
    @SuppressWarnings("unused")
    private static final String TAG = "Gallery2/SlotView";

    /// M: [FEATURE.MODIFY] fancy layout @{
    private static final boolean mIsFancyLayoutSupported = FancyHelper.isFancyLayoutSupported();
    /*private static final boolean WIDE = true;*/
    private static final boolean WIDE = false;
    //private static final boolean WIDE = mIsFancyLayoutSupported ? false : true;
    /// @}
    private static final int INDEX_NONE = -1;

    public static final int RENDER_MORE_PASS = 1;
    public static final int RENDER_MORE_FRAME = 2;

    public interface Listener {
        public void onDown(int index);
        public void onUp(boolean followedByLongPress);
        public void onSingleTapUp(int index);
        public void onLongTap(int index);
        public void onScrollPositionChanged(int position, int total);
    }

    public static class SimpleListener implements Listener {
        @Override public void onDown(int index) {}
        @Override public void onUp(boolean followedByLongPress) {}
        @Override public void onSingleTapUp(int index) {}
        @Override public void onLongTap(int index) {}
        @Override public void onScrollPositionChanged(int position, int total) {}
    }

    public static interface SlotRenderer {
        public void prepareDrawing();
        public void onVisibleRangeChanged(int visibleStart, int visibleEnd);
        public void onSlotSizeChanged(int width, int height);
        public int renderSlot(GLCanvas canvas, int index, int pass, int width, int height);
    }

    private final GestureDetector mGestureDetector;
    private final ScrollerHelper mScroller;
    /// M: [FEATURE.MODIFY] fancy layout @{
    //private final Paper mPaper = new Paper();
    private final Paper mPaper = mIsFancyLayoutSupported ? new FancyPaper() : new Paper();
    /// @}
    private Listener mListener;
    private UserInteractionListener mUIListener;

    private boolean mMoreAnimation = false;
    private SlotAnimation mAnimation = null;
    /// M: [FEATURE.MODIFY] fancy layout @{
    //private final Layout mLayout = new Layout();
    private Layout mLayout;
    /// @}

    private int mStartIndex = INDEX_NONE;

    // whether the down action happened while the view is scrolling.
    private boolean mDownInScrolling;
    private int mOverscrollEffect = OVERSCROLL_NONE;
    private final Handler mHandler;

    private SlotRenderer mRenderer;

    private int[] mRequestRenderSlots = new int[16];

    public static final int OVERSCROLL_3D = 0;
    public static final int OVERSCROLL_SYSTEM = 1;
    public static final int OVERSCROLL_NONE = 2;

    // to prevent allocating memory
    private final Rect mTempRect = new Rect();
    
    private final boolean isSetPage;
    private ISelectMode mSelectMode;

    public SlotView(AbstractGalleryActivity activity, Spec spec) {
    	this.isSetPage = false;
        mGestureDetector = new GestureDetector(activity, new MyGestureListener());
        mScroller = new ScrollerHelper(activity);
        mHandler = new SynchronizedHandler(activity.getGLRoot());
        /// M: [FEATURE.ADD] fancy layout @{
        mLayout = new DefaultLayout();
        /// @}
        setSlotSpec(spec);
        mActivity = activity;
    }

    /// M: [FEATURE.ADD] fancy layout @{
    /**
     * Another construction for fancy, specify whether enable fancy layout.
     */
    public SlotView(AbstractGalleryActivity activity, Spec spec, boolean enableFancy) {
    	isSetPage = false;
        mGestureDetector = new GestureDetector(activity, new MyGestureListener());
        mScroller = new ScrollerHelper(activity);
        mHandler = new SynchronizedHandler(activity.getGLRoot());
        mActivity = activity;
        if (enableFancy) {
            mLayoutArray.add(FancyHelper.DEFAULT_LAYOUT, new DefaultLayout(
                    FancyHelper.DEFAULT_LAYOUT));
            mLayoutArray.add(FancyHelper.FANCY_LAYOUT, new FancyLayout(
                    FancyHelper.FANCY_LAYOUT));
            mLayoutArray.add(FancyHelper.MULTI_WINDOW_LAYOUT, new DefaultLayout(
                    FancyHelper.MULTI_WINDOW_LAYOUT));
            switchLayout(FancyHelper.FANCY_LAYOUT);
            for (int i = 0; i < COL_NUM; i++) {
                mSlotMapByColumn.put(i, new ArrayList<SlotEntry>());
            }
        } else {
            mLayout = new DefaultLayout();
        }
        setSlotSpec(spec);
        if (enableFancy) {
            mLayout.setSlotArray(mSlotArray, mSlotMapByColumn);
        }
    }
    /// @}
	
	public SlotView(AbstractGalleryActivity activity, Spec spec, boolean enableFancy,boolean isSetPage){
    	  this.isSetPage = isSetPage;
    	  mGestureDetector = new GestureDetector(activity, new MyGestureListener());
          mScroller = new ScrollerHelper(activity);
          mHandler = new SynchronizedHandler(activity.getGLRoot());
          if (enableFancy) {
              mLayoutArray.add(FancyHelper.DEFAULT_LAYOUT, new DefaultLayout(
                      FancyHelper.DEFAULT_LAYOUT));
              mLayoutArray.add(FancyHelper.FANCY_LAYOUT, new FancyLayout(
                      FancyHelper.FANCY_LAYOUT));
              mLayoutArray.add(FancyHelper.MULTI_WINDOW_LAYOUT, new DefaultLayout(
                      FancyHelper.MULTI_WINDOW_LAYOUT));
              switchLayout(FancyHelper.FANCY_LAYOUT);
              for (int i = 0; i < COL_NUM; i++) {
                  mSlotMapByColumn.put(i, new ArrayList<SlotEntry>());
              }
          } else {
              mLayout = new DefaultLayout();
          }
          setSlotSpec(spec);
          if (enableFancy) {
              mLayout.setSlotArray(mSlotArray, mSlotMapByColumn);
              //mActivity = activity; //use this to get ActionBar height // default
          }
          /// @prize fanjunchen 2015-04-23 {
          mActivity = activity;
          /// @prize }
    } 

    public void setSelectMode(ISelectMode selectMode) {
        mSelectMode = selectMode;
    }

    private boolean isSelectMode() {
        if (mSelectMode != null) {
            return mSelectMode.isSelectMode();
        }
        return false;
    }

	// / @prize }
    public void setSlotRenderer(SlotRenderer slotDrawer) {
        mRenderer = slotDrawer;
        if (mRenderer != null) {
            /// M: [FEATURE.MODIFY] fancy layout @{
            //mRenderer.onSlotSizeChanged(mLayout.mSlotWidth, mLayout.mSlotHeight);
            mRenderer.onSlotSizeChanged(mLayout.getSlotWidth(), mLayout.getSlotHeight());
            /// @}
            mRenderer.onVisibleRangeChanged(getVisibleStart(), getVisibleEnd());
        }
        /// M: [FEATURE.ADD] fancy layout @{
        if (mIsFancyLayoutSupported) {
            mLayout.setSlotRenderer(mRenderer);
        }
        /// @}
    }

    public void setCenterIndex(int index) {
        /// M: [FEATURE.MODIFY] fancy layout @{
        //int slotCount = mLayout.mSlotCount;
        int slotCount = mLayout.getSlotCount();
        /// @}
        if (index < 0 || index >= slotCount) {
            return;
        }
        Rect rect = mLayout.getSlotRect(index, mTempRect);
        int position = WIDE
                ? (rect.left + rect.right - getWidth()) / 2
                : (rect.top + rect.bottom - getHeight()) / 2;
        setScrollPosition(position);
    }

    public void makeSlotVisible(int index) {
        Rect rect = mLayout.getSlotRect(index, mTempRect);
        int visibleBegin = WIDE ? mScrollX : mScrollY;
        int visibleLength = WIDE ? getWidth() : getHeight();
        int visibleEnd = visibleBegin + visibleLength;
        int slotBegin = WIDE ? rect.left : rect.top;
        int slotEnd = WIDE ? rect.right : rect.bottom;

        int position = visibleBegin;
        if (visibleLength < slotEnd - slotBegin) {
            position = visibleBegin;
        } else if (slotBegin < visibleBegin) {
            position = slotBegin;
        } else if (slotEnd > visibleEnd) {
            position = slotEnd - visibleLength;
        }

        setScrollPosition(position);
    }

    public void setScrollPosition(int position) {
        position = Utils.clamp(position, 0, mLayout.getScrollLimit());
        /// @prize fanjunchen 2015-04-23 {
        if (WIDE)
        	mScroller.setPosition(position); // default
        else
        	mScroller.setPositionY(position);
        /// @prize }
        updateScrollPosition(position, false);
    }

    public void setSlotSpec(Spec spec) {
        mLayout.setSlotSpec(spec);
        /// M: [FEATURE.ADD] fancy layout @{
        if (mIsFancyLayoutSupported) {
            mDefaultLayoutSpec = spec;
        }
        /// @}
    }

    @Override
    public void addComponent(GLView view) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void onLayout(boolean changeSize, int l, int t, int r, int b) {
        if (!changeSize) return;

        // Make sure we are still at a resonable scroll position after the size
        // is changed (like orientation change). We choose to keep the center
        // visible slot still visible. This is arbitrary but reasonable.
        int visibleIndex = 0;
        if(mLayout!=null){
             visibleIndex =
                    (mLayout.getVisibleStart() + mLayout.getVisibleEnd()) / 2;
           
            mLayout.setSize(r - l, b - t);
        }

        /// M: [FEATURE.ADD] fancy layout @{
        if (mIsFancyLayoutSupported && mLayout != null && (mActivity != null)) {
            // mLayout.setSize() may not be called when switch to Fancy
            // so mViewHeight will be 0, then visible Range will be calculated incorrectly
            // so backup this value
            if (FeatureConfig.IS_TABLET) {
                int orientation = mActivity.getResources().getConfiguration().orientation;
                if ((b - t) > (r - l) && orientation == Configuration.ORIENTATION_PORTRAIT) {
                    mLayout.setViewHeightWhenPortrait(b - t);
                }
            } else {
                if ((b - t) > (r - l)) {
                    mLayout.setViewHeightWhenPortrait(b - t);
                }
            }
        }
        /// @}
        makeSlotVisible(visibleIndex);
        if (mOverscrollEffect == OVERSCROLL_3D) {
        	/// @prize fanjunchen 2015-05-04 {
        	if (!WIDE) {
        		mPaper.setSize(b - t, r - l);
        	}
        	else
        	/// }
        		mPaper.setSize(r - l, b - t);
        }
    }

    public void startScatteringAnimation(RelativePosition position) {
        mAnimation = new ScatteringAnimation(position);
        mAnimation.start();
        /// M: [FEATURE.MODIFY] fancy layout @{
        //if (mLayout.mSlotCount != 0) invalidate();
        if (mLayout.getSlotCount() != 0) {
            invalidate();
        }
        /// @}
    }

    public void startRisingAnimation() {
        mAnimation = new RisingAnimation();
        mAnimation.start();
        /// M: [FEATURE.MODIFY] fancy layout @{
        //if (mLayout.mSlotCount != 0) invalidate();
        if (mLayout.getSlotCount() != 0) {
            invalidate();
        }
        /// @}
    }

    private void updateScrollPosition(int position, boolean force) {
        if (!force && (WIDE ? position == mScrollX : position == mScrollY)) return;
        if (WIDE) {
            mScrollX = position;
        } else {
            mScrollY = position;
        }
        mLayout.setScrollPosition(position);
        onScrollPositionChanged(position);
    }

    protected void onScrollPositionChanged(int newPosition) {
        int limit = mLayout.getScrollLimit();
        /// M: [FEATURE.MODIFY] fancy layout @{
        //mListener.onScrollPositionChanged(newPosition, limit);
        if (mListener != null) {
            mListener.onScrollPositionChanged(newPosition, limit);
        }
        /// @}
    }

    public Rect getSlotRect(int slotIndex) {
        return mLayout.getSlotRect(slotIndex, new Rect());
    }

    private float mDownX;
    private float mDownY;
    private float mMoveX;
    private float mMoveY;
    private boolean mIsSlideSelect;
    private SlotCell mDownSlotCell;
    private SlotCell mCurrentCell;

    private void handleSlotCell(SlotCell slotCell) {
        LogUtil.i(TAG, "&&&&&&&&handleSlotCell slotCell=" + slotCell + " mDownSlotCell=" + mDownSlotCell + " mCurrentCell=" + mCurrentCell);
        if (slotCell == null || mDownSlotCell == null) {
            LogUtil.i(TAG, "&&&&&&&&handleSlotCell slotCell == null || mDownSlotCell == null");
            return;
        }
        if (mCurrentCell == null || !mCurrentCell.equals(slotCell)) {
            if (mCurrentCell == null) {
                if (mSelectMode != null) {
                    mSelectMode.slideControlStart();
                }
            }
            mCurrentCell = slotCell;
            publishSlotCell();
        }
    }

    private void publishSlotCell() {
        if (mSelectMode != null) {
            mSelectMode.slideControlSelect(true, mDownSlotCell.slotIndex, mCurrentCell.slotIndex);
        }
    }

    private void publishSlotCellEnd() {
        if (mSelectMode != null) {
            mSelectMode.slideControlEnd();
        }
    }

    @Override
    protected boolean onTouch(MotionEvent event) {
        if (mUIListener != null) mUIListener.onUserInteraction();
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mDownInScrolling = !mScroller.isFinished();
                mScroller.forceFinished();
                mDownX = event.getX();
                mDownY = event.getY();
                mIsSlideSelect = false;
                mGestureDetector.onTouchEvent(event);
                if (isSelectMode()) {
                    mDownSlotCell = mLayout.getSlotCellByPosition(mDownX, mDownY);
                    mCurrentCell = null;
                } else {
                    mDownSlotCell = null;
                }
                LogUtil.i(TAG, "&&&&&&&&onTouch down downx=" + mDownX + " downy=" + mDownY + " mDownSlotCell=" + mDownSlotCell);
                break;
            case MotionEvent.ACTION_MOVE:
                mMoveX = event.getX();
                mMoveY = event.getY();
                if (mIsSlideSelect) {
                    SlotCell slotCell = mLayout.getSlotCellByPosition(mMoveX, mMoveY);
                    handleSlotCell(slotCell);
                    if (mMoveY < 0 && mScrollY != 0) {
                        if (!mScroller.isFinished()) {
                            mScroller.forceFinished();
                        }
                        mScroller.startScrollY(-mLayout.getSlotHeight() / 20, 0, mLayout.getScrollLimit());
                        invalidate();
                    } else if (mMoveY > mLayout.getViewHeight() - mLayout.getSlotHeight() && mScrollY != mLayout.getScrollLimit()) {
                        if (!mScroller.isFinished()) {
                            mScroller.forceFinished();
                        }
                        mScroller.startScrollY(mLayout.getSlotHeight() / 20, 0, mLayout.getScrollLimit());
                        invalidate();
                    } else {
                        mScroller.forceFinished();
                    }
                } else {
                    mGestureDetector.onTouchEvent(event);
                }
                LogUtil.i(TAG, "&&&&&&&&onTouch downx=" + mDownX + " downy=" + mDownY + "movex=" + mMoveX + " mMovey=" + mMoveY + " mIsSlideSelect=" + mIsSlideSelect);
                break;
            case MotionEvent.ACTION_UP:
                mPaper.onRelease();
                invalidate();
                float upX = event.getX();
                float upY = event.getY();
                if (mIsSlideSelect) {
                    SlotCell slotCell = mLayout.getSlotCellByPosition(upX, upY);
                    handleSlotCell(slotCell);
                    publishSlotCellEnd();
                } else {
                    mGestureDetector.onTouchEvent(event);
                }
                LogUtil.i(TAG, "&&&&&&&&onTouch up downx=" + mDownX + " downy=" + mDownY + " mIsSlideSelect=" + mIsSlideSelect);
                break;
        }
        return true;
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }
    
    
    public Layout getmLayout(){
          return mLayout;
    }

    public void setUserInteractionListener(UserInteractionListener listener) {
        mUIListener = listener;
    }

    public void setOverscrollEffect(int kind) {
        mOverscrollEffect = kind;
        mScroller.setOverfling(kind == OVERSCROLL_SYSTEM);
    }

    private static int[] expandIntArray(int array[], int capacity) {
        while (array.length < capacity) {
            array = new int[array.length * 2];
        }
        return array;
    }

    @Override
    protected void render(GLCanvas canvas) {
        super.render(canvas);

        if (mRenderer == null) return;
        mRenderer.prepareDrawing();

        long animTime = AnimationTime.get();
        boolean more = mScroller.advanceAnimation(animTime);
        more |= mLayout.advanceAnimation(animTime);
        int oldX = 0;
        /// @prize fanjunchen 2015-04-23 {
        if (WIDE) {
        	oldX = mScrollX;
        	updateScrollPosition(mScroller.getPosition(), false); // default
        }
        else
        {
        	oldX = mScrollY;
        	updateScrollPosition(mScroller.getPositionY(), false);
        }
        /// @prize }

        boolean paperActive = false;
        if (mOverscrollEffect == OVERSCROLL_3D) {
            // Check if an edge is reached and notify mPaper if so.
            int newX = mScrollX;
            if (WIDE)
            	newX = mScrollX;
            else
            	newX = mScrollY;
            int limit = mLayout.getScrollLimit();
            if (oldX > 0 && newX == 0 || oldX < limit && newX == limit) {
                float v = mScroller.getCurrVelocity();
                if (newX == limit) v = -v;

                // I don't know why, but getCurrVelocity() can return NaN.
                if (!Float.isNaN(v)) {
                    mPaper.edgeReached(v);
                }
            }
            paperActive = mPaper.advanceAnimation();
        }

        more |= paperActive;

        if (mAnimation != null) {
            more |= mAnimation.calculate(animTime);
        }

        canvas.translate(-mScrollX, -mScrollY);

        int requestCount = 0;
        /// M: [FEATURE.MODIFY] fancy layout @{
        /*
        int requestedSlot[] = expandIntArray(mRequestRenderSlots,
                mLayout.mVisibleEnd - mLayout.mVisibleStart);
        */
        int requestedSlot[] = expandIntArray(mRequestRenderSlots,
                mLayout.getVisibleEnd() - mLayout.getVisibleStart());
        /*for (int i = mLayout.mVisibleEnd - 1; i >= mLayout.mVisibleStart; --i) {*/
        for (int i = mLayout.getVisibleEnd() - 1; i >= mLayout.getVisibleStart(); --i) {
        /// @}
            int r = renderItem(canvas, i, 0, paperActive);
            if ((r & RENDER_MORE_FRAME) != 0) more = true;
            if ((r & RENDER_MORE_PASS) != 0) requestedSlot[requestCount++] = i;
        }

        for (int pass = 1; requestCount != 0; ++pass) {
            int newCount = 0;
            for (int i = 0; i < requestCount; ++i) {
                int r = renderItem(canvas,
                        requestedSlot[i], pass, paperActive);
                if ((r & RENDER_MORE_FRAME) != 0) more = true;
                if ((r & RENDER_MORE_PASS) != 0) requestedSlot[newCount++] = i;
            }
            requestCount = newCount;
        }

        canvas.translate(mScrollX, mScrollY);

        if (more) invalidate();

        final UserInteractionListener listener = mUIListener;
        if (mMoreAnimation && !more && listener != null) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    listener.onUserInteractionEnd();
                }
            });
        }
        mMoreAnimation = more;
    }

    private int renderItem(
            GLCanvas canvas, int index, int pass, boolean paperActive) {
        canvas.save(GLCanvas.SAVE_FLAG_ALPHA | GLCanvas.SAVE_FLAG_MATRIX);
        Rect rect = mLayout.getSlotRect(index, mTempRect);
		if (rect == null) {
			return 0;
		}
        if (paperActive) {
            /// M: [FEATURE.MODIFY] fancy layout @{
            //canvas.multiplyMatrix(mPaper.getTransform(rect, mScrollX), 0);
            if (mIsFancyLayoutSupported) {
                canvas.multiplyMatrix(mPaper.getTransform(rect, mScrollY), 0);
            }
            else if (!WIDE) {
            	canvas.multiplyMatrix(mPaper.getTransformY(rect, mScrollY), 0);
            }
            else {
                canvas.multiplyMatrix(mPaper.getTransform(rect, mScrollX), 0);
            }
            /// @}
        } else {
            canvas.translate(rect.left, rect.top, 0);
        }
        if (mAnimation != null && mAnimation.isActive()) {
            mAnimation.apply(canvas, index, rect);
        }
        int result = mRenderer.renderSlot(
                canvas, index, pass, rect.right - rect.left, rect.bottom - rect.top);
        canvas.restore();
        return result;
    }

    public static abstract class SlotAnimation extends Animation {
        protected float mProgress = 0;

        public SlotAnimation() {
            setInterpolator(new DecelerateInterpolator(4));
            setDuration(1500);
        }

        @Override
        protected void onCalculate(float progress) {
            mProgress = progress;
        }

        abstract public void apply(GLCanvas canvas, int slotIndex, Rect target);
    }

    public static class RisingAnimation extends SlotAnimation {
        private static final int RISING_DISTANCE = 128;

        @Override
        public void apply(GLCanvas canvas, int slotIndex, Rect target) {
            canvas.translate(0, 0, RISING_DISTANCE * (1 - mProgress));
        }
    }

    public static class ScatteringAnimation extends SlotAnimation {
        private int PHOTO_DISTANCE = 1000;
        private RelativePosition mCenter;

        public ScatteringAnimation(RelativePosition center) {
            mCenter = center;
        }

        @Override
        public void apply(GLCanvas canvas, int slotIndex, Rect target) {
            canvas.translate(
                    (mCenter.getX() - target.centerX()) * (1 - mProgress),
                    (mCenter.getY() - target.centerY()) * (1 - mProgress),
                    slotIndex * PHOTO_DISTANCE * (1 - mProgress));
            canvas.setAlpha(mProgress);
        }
    }

    // This Spec class is used to specify the size of each slot in the SlotView.
    // There are two ways to do it:
    //
    // (1) Specify slotWidth and slotHeight: they specify the width and height
    //     of each slot. The number of rows and the gap between slots will be
    //     determined automatically.
    // (2) Specify rowsLand, rowsPort, and slotGap: they specify the number
    //     of rows in landscape/portrait mode and the gap between slots. The
    //     width and height of each slot is determined automatically.
    //
    // The initial value of -1 means they are not specified.
    public static class Spec {
        public int slotWidth = -1;
        public int slotHeight = -1;
        public int slotHeightAdditional = 0;

        public int rowsLand = -1;
        public int rowsPort = -1;
        public int slotGap = -1;
        /** prize 2015-07-07 liudong start */
        public int slotGapFile = -1;
        /** prize 2015-07-07 liudong end */
        /// M: [FEATURE.ADD] fancy layout @{
        public int colsLand = -1;
        public int colsPort = -1;
        /// @}

		// / @prize fanjunchen {
		public int labelHeight = 60;
		public int labelWidth = -1;
		// / @prize }
    }

    public static class SlotCell {
        public int rowId;
        public int colId;
        public int slotIndex;

        public SlotCell(int rId, int cId, int index) {
            rowId = rId;
            colId = cId;
            slotIndex = index;
        }

        @Override
        public String toString() {
            return "[" + rowId + " ," + colId + " ," + slotIndex + "]";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            SlotCell slotCell = (SlotCell) o;

            if (rowId != slotCell.rowId) return false;
            return colId == slotCell.colId;

        }

        @Override
        public int hashCode() {
            int result = rowId;
            result = 31 * result + colId;
            return result;
        }
    }

    /// M: [FEATURE.MODIFY] fancy layout @{
    //public class Layout {
    /**
     * Implement Layout.
     */
    public class DefaultLayout extends Layout {
    /// @}

        private int mVisibleStart;
        private int mVisibleEnd;

        private int mSlotCount;
        private int mSlotWidth;
        private int mSlotHeight;
        private int mSlotGap;

        private Spec mSpec;

        private int mWidth;
        private int mHeight;

        private int mUnitCount;
        private int mContentLength;
        private int mScrollPosition;

        private IntegerAnimation mVerticalPadding = new IntegerAnimation();
        private IntegerAnimation mHorizontalPadding = new IntegerAnimation();

        /// M: [FEATURE.ADD] Multi-window. @{
        /**
         * Constructor.
         * @param layoutType layout type
         */
        public DefaultLayout(int layoutType) {
            super(layoutType);
        }

        /**
         * Constructor.
         */
        public DefaultLayout() {
            super();
        }
        /// @}

        public void setSlotSpec(Spec spec) {
            mSpec = spec;
        }

        public boolean setSlotCount(int slotCount) {
            /// M: [FEATURE.MODIFY] fancy layout @{
            //if (slotCount == mSlotCount) return false;
            if (!mForceRefreshFlag && slotCount == mSlotCount) {
                return false;
            }
            /// @}
            if (mSlotCount != 0) {
                mHorizontalPadding.setEnabled(true);
                mVerticalPadding.setEnabled(true);
            }
            mSlotCount = slotCount;
            int hPadding = mHorizontalPadding.getTarget();
            int vPadding = mVerticalPadding.getTarget();
            initLayoutParameters();
            return vPadding != mVerticalPadding.getTarget()
                    || hPadding != mHorizontalPadding.getTarget();
        }

        /// M: [FEATURE.ADD] fancy layout @{
        private boolean mForceRefreshFlag = false;

        public int getSlotCount() {
            return mSlotCount;
        }

        public Spec getSlotSpec() {
            return mSpec;
        }

        public int getSlotGap() {
            return mSpec.slotGap;
        }

        public int getViewWidth() {
            return mWidth;
        }

        public int getViewHeight() {
            return mHeight;
        }

        public void setForceRefreshFlag(boolean needForceRefresh) {
            mForceRefreshFlag = needForceRefresh;
        }

        /**
         * clear slot map of specified column.
         * @param index
         * @param clearAll
         */
        public void clearColumnArray(int index, boolean clearAll) {
            if (mSlotMapByColumn == null || mSlotArray == null) {
                return;
            }
            for (int i = 0; i < COL_NUM; i++) {
                ArrayList<SlotEntry> array = mSlotMapByColumn.get(i);
                if (array == null) {
                    return;
                }
                if (clearAll) {
                    array.clear();
                } else {
                    int arraySize = array.size();
                    for (int j = arraySize - 1; j >= 0; j--) {
                        if (array.get(j).slotIndex >= index) {
                            array.remove(j);
                        }
                    }
                }
            }
        }
        /// @}

        public Rect getSlotRect(int index, Rect rect) {
            if (index >= mVisibleStart && index < mVisibleEnd && mVisibleEnd != 0) {
                int col, row;
                if (WIDE) {
                    col = index / mUnitCount;
                    row = index - col * mUnitCount;
                } else {
                    row = index / mUnitCount;
                    col = index - row * mUnitCount;
                }

                int x =  col * (mSlotWidth + mSlotGap/2)+ mSlotGap/2+2;
                int y = mVerticalPadding.get() + row * (mSlotHeight + mSlotGap/2);
                rect.set(x, y, x + mSlotWidth, y + mSlotHeight);
                return rect;
            }
            return new Rect(0, 0, 1, 1);
        }

        public int getSlotWidth() {
            return mSlotWidth;
        }

        public int getSlotHeight() {
            return mSlotHeight;
        }

        // Calculate
        // (1) mUnitCount: the number of slots we can fit into one column (or row).
        // (2) mContentLength: the width (or height) we need to display all the
        //     columns (rows).
        // (3) padding[]: the vertical and horizontal padding we need in order
        //     to put the slots towards to the center of the display.
        //
        // The "major" direction is the direction the user can scroll. The other
        // direction is the "minor" direction.
        //
        // The comments inside this method are the description when the major
        // directon is horizontal (X), and the minor directon is vertical (Y).
        private void initLayoutParameters(
                int majorLength, int minorLength,  /* The view width and height */
                int majorUnitSize, int minorUnitSize,  /* The slot width and height */
                int[] padding) {
        	int unitCount = 0;
        	if((majorUnitSize + mSlotGap)!=0){
        		unitCount = majorLength  / (majorUnitSize + mSlotGap/2);
        	}
            if (unitCount == 0) unitCount = 1;
            mUnitCount = unitCount;

            // We put extra padding above and below the column is 4.
//            int availableUnits = Math.min(mUnitCount, mSlotCount);
//            int usedMinorLength = availableUnits * minorUnitSize +
//                    (availableUnits - 1) * mSlotGap;
            padding[0] = 4;

            // Then calculate how many columns we need for all slots.
            int count = ((mSlotCount + mUnitCount - 1) / mUnitCount);
            mContentLength = count * minorUnitSize + (count + 1) * mSlotGap/2;

            // If the content length is less then the screen width, put
            // extra padding in left and right is 4.
            padding[1] = 4;
        }

        private void initLayoutParameters() {
            // Initialize mSlotWidth and mSlotHeight from mSpec
            if (mSpec.slotWidth != -1) {
                mSlotGap = 0;
                mSlotWidth = mSpec.slotWidth;
                mSlotHeight = mSpec.slotHeight;
            } else {
                /// M: [FEATURE.MODIFY] fancy layout @{
                /*
                int rows = (mWidth > mHeight) ? mSpec.rowsLand : mSpec.rowsPort;
                mSlotGap = mSpec.slotGap;
                mSlotHeight = Math.max(1, (mHeight - (rows - 1) * mSlotGap) / rows);
                mSlotWidth = mSlotHeight - mSpec.slotHeightAdditional;
                */
                if (mIsFancyLayoutSupported) {
                    int cols = (mWidth > mHeight) ? mSpec.colsLand : mSpec.colsPort;
                    mSlotGap = mSpec.slotGap;
                    mSlotWidth = Math.max(1, (mWidth - (cols + 1) * mSlotGap/2) / cols);
                    mSlotHeight = mSlotWidth;
                } 
              /// @prize fanjunchen 2015-04-23 {
                else if (WIDE) {
                	int rows = (mWidth > mHeight) ? mSpec.rowsLand : mSpec.rowsPort;
                    mSlotGap = mSpec.slotGap;
                    mSlotHeight = Math.max(1, (mHeight - (rows + 1) * mSlotGap/2) / rows);
                    mSlotWidth = mSlotHeight - mSpec.slotHeightAdditional;
                }
              /// @prize }
                else {
                	/* default
                	 * int rows = (mWidth > mHeight) ? mSpec.rowsLand : mSpec.rowsPort;
                    mSlotGap = mSpec.slotGap;
                    mSlotHeight = Math.max(1, (mHeight - (rows - 1) * mSlotGap) / rows);
                    mSlotWidth = mSlotHeight - mSpec.slotHeightAdditional;
                	 */
                	int cols = (mWidth > mHeight) ? mSpec.colsLand : mSpec.colsPort;
                	/*if(isSetPage){
                		mSlotGap = mSpec.slotGapFile;
                	}else{
                		mSlotGap = mSpec.slotGap;
                	}*/

                    mSlotGap = mSpec.slotGap;
                    mSlotWidth = Math.max(1, (mWidth - (cols + 1) * mSlotGap/2) / cols);
    
                    if (isSetPage) {
                    	mSlotHeight = mSlotWidth - mSlotGap/2;//(int) (mSlotWidth * 0.67);
                    }else {
                    	mSlotHeight = mSlotWidth;
                    }
                }
                /// @}
            }

            if (mRenderer != null) {
                mRenderer.onSlotSizeChanged(mSlotWidth, mSlotHeight);
            }

            int[] padding = new int[2];
            /// @default {
            /*if (WIDE) {
                initLayoutParameters(mWidth, mHeight, mSlotWidth, mSlotHeight, padding);
                mVerticalPadding.startAnimateTo(padding[0]);
                mHorizontalPadding.startAnimateTo(padding[1]);
            } else {
                initLayoutParameters(mHeight, mWidth, mSlotHeight, mSlotWidth, padding);
                mVerticalPadding.startAnimateTo(padding[1]);
                mHorizontalPadding.startAnimateTo(padding[0]);
            }*/
            /// @default }
            /// @prize fanjunchen 2015-04-23 {
            initLayoutParameters(mWidth, mHeight, mSlotWidth, mSlotHeight, padding);
            mVerticalPadding.startAnimateTo(padding[0]);
            mHorizontalPadding.startAnimateTo(padding[1]);
            /// @prize }
            updateVisibleSlotRange();
        }

        public void setSize(int width, int height) {
            mWidth = width;
            mHeight = height;
            initLayoutParameters();
        }

        private void updateVisibleSlotRange() {
            int position = mScrollPosition;

            if (WIDE) {
                int startCol = position / (mSlotWidth + mSlotGap/2);
                int start = Math.max(0, mUnitCount * startCol);
                int endCol = (position + mWidth + mSlotWidth + mSlotGap - 1) /
                        (mSlotWidth + mSlotGap);
                int end = Math.min(mSlotCount, mUnitCount * endCol);
                setVisibleRange(start, end);
            } else {
            	 int startRow = 0; 
            	if((mSlotHeight + mSlotGap)!=0){
            		startRow = position / (mSlotHeight + mSlotGap/2);
            	}
               
                int start = Math.max(0, mUnitCount * startRow);
                int endRow = (position + mHeight + mSlotHeight + mSlotGap/2 - 1) /
                        (mSlotHeight + mSlotGap/2);
                int end = Math.min(mSlotCount, mUnitCount * endRow);
                setVisibleRange(start, end);
            }
        }

        public void setScrollPosition(int position) {
            /// M: [FEATURE.MODIFY] fancy layout @{
            //if (mScrollPosition == position) return;
            if (!mForceRefreshFlag && mScrollPosition == position) {
                return;
            }
            /// @}
            mScrollPosition = position;
            updateVisibleSlotRange();
            if (mIsSlideSelect) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        SlotCell slotCell = getSlotCellByPosition(mMoveX, mMoveY);
                        handleSlotCell(slotCell);
                    }
                });
            }
        }

        private void setVisibleRange(int start, int end) {
            /// M: [FEATURE.MODIFY] fancy layout @{
            //if (start == mVisibleStart && end == mVisibleEnd) return;
            if (!mForceRefreshFlag && start == mVisibleStart && end == mVisibleEnd) {
                return;
            }
            /// @}
            if (start < end) {
                mVisibleStart = start;
                mVisibleEnd = end;
            } else {
                mVisibleStart = mVisibleEnd = 0;
            }
            if (mRenderer != null) {
                mRenderer.onVisibleRangeChanged(mVisibleStart, mVisibleEnd);
            }
        }

        public int getVisibleStart() {
            return mVisibleStart;
        }

        public int getVisibleEnd() {
            return mVisibleEnd;
        }

        public SlotCell getSlotCellByPosition(float x, float y) {
            int absoluteX = Math.round(x) + (WIDE ? mScrollPosition : 0);
            int absoluteY = Math.round(y) + (WIDE ? 0 : mScrollPosition);

            absoluteX -= mHorizontalPadding.get();
            absoluteY -= mVerticalPadding.get();
            LogUtil.i(TAG, "&&&&&&getSlotCellByPosition absoluteX=" + absoluteX + " absoluteY=" + absoluteY);
            if (absoluteX < 0 || absoluteY < 0) {
                return null;
            }

            int columnIdx = absoluteX / (mSlotWidth + mSlotGap/2);
            int rowIdx = absoluteY / (mSlotHeight + mSlotGap/2);
            LogUtil.i(TAG, "&&&&&&getSlotCellByPosition columnIdx=" + columnIdx + " rowIdx=" + rowIdx + " mUnitCount=" + mUnitCount);
            if (!WIDE && columnIdx >= mUnitCount) {
                return null;
            }

            if (WIDE && rowIdx >= mUnitCount) {
                return null;
            }

            /*if (absoluteX % (mSlotWidth + mSlotGap/2) >= mSlotWidth) {
                LogUtil.i(TAG, "&&&&&&getSlotCellByPosition null width");
                return null;
            }

            if (absoluteY % (mSlotHeight + mSlotGap/2) >= mSlotHeight) {
                LogUtil.i(TAG, "&&&&&&getSlotCellByPosition null height");
                return null;
            }*/

            int index = WIDE
                    ? (columnIdx * mUnitCount + rowIdx)
                    : (rowIdx * mUnitCount + columnIdx);

            SlotCell slotCell = WIDE
                    ? new SlotCell(columnIdx, rowIdx, index)
                    : new SlotCell(rowIdx, columnIdx, index);

            return slotCell;
        }

        public int getSlotIndexByPosition(float x, float y) {
            int absoluteX = Math.round(x) + (WIDE ? mScrollPosition : 0);
            int absoluteY = Math.round(y) + (WIDE ? 0 : mScrollPosition);

            absoluteX -= mHorizontalPadding.get();
            absoluteY -= mVerticalPadding.get();

            if (absoluteX < 0 || absoluteY < 0) {
                return INDEX_NONE;
            }

            int columnIdx = absoluteX / (mSlotWidth + mSlotGap/2);
            int rowIdx = absoluteY / (mSlotHeight + mSlotGap/2);

            if (!WIDE && columnIdx >= mUnitCount) {
                return INDEX_NONE;
            }

            if (WIDE && rowIdx >= mUnitCount) {
                return INDEX_NONE;
            }

            if (absoluteX % (mSlotWidth + mSlotGap/2) >= mSlotWidth) {
                return INDEX_NONE;
            }

            if (absoluteY % (mSlotHeight + mSlotGap/2) >= mSlotHeight) {
                return INDEX_NONE;
            }

            int index = WIDE
                    ? (columnIdx * mUnitCount + rowIdx)
                    : (rowIdx * mUnitCount + columnIdx);

            return index >= mSlotCount ? INDEX_NONE : index;
        }

        public int getScrollLimit() {
            int limit = WIDE ? mContentLength - mWidth : mContentLength - mHeight;
            return limit <= 0 ? 0 : limit;
        }

        public boolean advanceAnimation(long animTime) {
            // use '|' to make sure both sides will be executed
            return mVerticalPadding.calculate(animTime) | mHorizontalPadding.calculate(animTime);
        }
    }

    private class MyGestureListener implements GestureDetector.OnGestureListener {
        private boolean isDown;
        private boolean isFirstScroll;

        // We call the listener's onDown() when our onShowPress() is called and
        // call the listener's onUp() when we receive any further event.
        @Override
        public void onShowPress(MotionEvent e) {
            LogUtil.i(TAG, "&&&&&&&&onShowPress");
            if (isSelectMode()) {
                return;
            }
            GLRoot root = getGLRoot();
            if (root != null) {
                root.lockRenderThread();
                try {
                    if (isDown) return;
                    int index = mLayout.getSlotIndexByPosition(e.getX(), e.getY());
                    if (index != INDEX_NONE) {
                        isDown = true;
                        mListener.onDown(index);
                    }
                } finally {
                    root.unlockRenderThread();
                }
            }
        }

        private void cancelDown(boolean byLongPress) {
            LogUtil.i(TAG, "&&&&&&&&cancelDown isDown=" + isDown);
            if (!isDown) return;
            isDown = false;
            mListener.onUp(byLongPress);
        }

        @Override
        public boolean onDown(MotionEvent e) {
            LogUtil.i(TAG, "&&&&&&&&onDown");
            isFirstScroll = true;
            return false;
        }

        @Override
        public boolean onFling(MotionEvent e1,
                MotionEvent e2, float velocityX, float velocityY) {
            LogUtil.i(TAG, "&&&&&&&&onFling velocityX=" + velocityX + " velocityY=" + velocityY);
            if (mIsSlideSelect) {
                return true;
            }
            LogUtil.i(TAG, "&&&&&&&&onFling start");
            cancelDown(false);
            int scrollLimit = mLayout.getScrollLimit();

            int xLen = (int)(e2.getX() - e1.getX());
            float fY = velocityY;
            if (Math.abs(fY) < 1)
            	fY = 1;
            int vRate = (int)Math.abs(velocityX / fY);
            int yLen = (int)(e2.getY() - e1.getY());
            if (scrollLimit == 0) return false;
            float velocity = WIDE ? velocityX : velocityY;
            /// @prize fanjunchen 2015-04-23 {

            if (WIDE)
            	mScroller.fling((int) -velocity / 2, 0, scrollLimit);
            else {
            	//mScroller.flingY((int) -velocity, 0, scrollLimit);
//            	int v = (int)Math.abs(velocity);
//            	if (v > 80) {
            		// mScroller.startY((int)velocity, scrollLimit);
            		mScroller.flingY((int) -velocity / 2, 0, scrollLimit);
//            	}
            }
            if (mUIListener != null) mUIListener.onUserInteractionBegin();
            invalidate();
            return true;
        }

        @Override
        public boolean onScroll(MotionEvent e1,
                MotionEvent e2, float distanceX, float distanceY) {
            LogUtil.i(TAG, "&&&&&&&&onScroll distanceX=" + distanceX + " distanceY=" + distanceY + " isFirstScroll=" + isFirstScroll);
            if (mIsSlideSelect || (isFirstScroll && (isSelectMode() && (Math.abs(distanceX) * 0.839f > Math.abs(distanceY))))) { // 40
                mIsSlideSelect = true;
                return true;
            }
            LogUtil.i(TAG, "&&&&&&&&onScroll start");
            isFirstScroll = false;
            cancelDown(false);
            float distance = WIDE ? distanceX : distanceY;
            /// @prize fanjunchen 2015-04-23 {
            int overDistance = 0;
            if (WIDE) {
            	overDistance = mScroller.startScroll(
                    Math.round(distance), 0, mLayout.getScrollLimit());
            }
            else {
            	overDistance = mScroller.startScrollY(
                        Math.round(distance), 0, mLayout.getScrollLimit());
            }
          /// @prize }
            if (mOverscrollEffect == OVERSCROLL_3D && overDistance != 0) {
                mPaper.overScroll(overDistance);
            }
            invalidate();
            return true;
        }

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            LogUtil.i(TAG, "&&&&&&&&onSingleTapUp");
            cancelDown(false);
            if (mDownInScrolling) return true;
            int index = mLayout.getSlotIndexByPosition(e.getX(), e.getY());
            if (index != INDEX_NONE) mListener.onSingleTapUp(index);
            return true;
        }

        @Override
        public void onLongPress(MotionEvent e) {
            LogUtil.i(TAG, "&&&&&&&&onLongPress");
            if (isSelectMode()) {
                mIsSlideSelect = true;
                return;
            }
            cancelDown(true);
            if (mDownInScrolling) return;
            lockRendering();
            try {
                int index = mLayout.getSlotIndexByPosition(e.getX(), e.getY());
                if (index != INDEX_NONE) mListener.onLongTap(index);
            } finally {
                unlockRendering();
            }
        }
    }

    public void setStartIndex(int index) {
        mStartIndex = index;
    }

    // Return true if the layout parameters have been changed
    public boolean setSlotCount(int slotCount) {
        boolean changed = mLayout.setSlotCount(slotCount);

        /// M: [FEATURE.ADD] fancy layout @{
        if (mIsFancyLayoutSupported) {
            mBackUpSlotCount = slotCount;
        }
        /// @}
        // mStartIndex is applied the first time setSlotCount is called.
        if (mStartIndex != INDEX_NONE) {
            setCenterIndex(mStartIndex);
            mStartIndex = INDEX_NONE;
        }
        // Reset the scroll position to avoid scrolling over the updated limit.
        setScrollPosition(WIDE ? mScrollX : mScrollY);
        return changed;
    }

    public int getVisibleStart() {
        return mLayout.getVisibleStart();
    }

    public int getVisibleEnd() {
        return mLayout.getVisibleEnd();
    }

    public int getScrollX() {
        return mScrollX;
    }

    public int getScrollY() {
        return mScrollY;
    }

    public Rect getSlotRect(int slotIndex, GLView rootPane) {
        // Get slot rectangle relative to this root pane.
        Rect offset = new Rect();
        rootPane.getBoundsOf(this, offset);
        Rect r = getSlotRect(slotIndex);
        r.offset(offset.left - getScrollX(),
                offset.top - getScrollY());
        return r;
    }

    private static class IntegerAnimation extends Animation {
        private int mTarget;
        private int mCurrent = 0;
        private int mFrom = 0;
        private boolean mEnabled = false;

        public void setEnabled(boolean enabled) {
            mEnabled = enabled;
        }

        public void startAnimateTo(int target) {
            if (!mEnabled) {
                mTarget = mCurrent = target;
                return;
            }
            if (target == mTarget) return;

            mFrom = mCurrent;
            mTarget = target;
            setDuration(180);
            start();
        }

        public int get() {
            return mCurrent;
        }

        public int getTarget() {
            return mTarget;
        }

        @Override
        protected void onCalculate(float progress) {
            mCurrent = Math.round(mFrom + progress * (mTarget - mFrom));
            if (progress == 1f) mEnabled = false;
        }
    }
    /// M: [FEATURE.ADD] fancy layout @{
    private ArrayList<SlotEntry> mSlotArray = new ArrayList<SlotEntry>();
    private HashMap<Integer, ArrayList<SlotEntry>> mSlotMapByColumn =
        new HashMap<Integer, ArrayList<SlotEntry>>();

    /**
     * slotEntry stands for each slot.
     */
    public static class SlotEntry {
        public int slotIndex;
        public int imageWidth;
        public int imageHeight;
        public int scaledWidth;
        // use these info to judge if slot is changed
        public int oriImageWidth;
        public int oriImageHeight;
        public String mimeType;
        public int rotation;
        public String albumName;

        public int scaledHeight;
        public int inWhichCol;
        public int inWhichRow;
        public Rect slotRect;
        public boolean isLandCameraFolder = false;
        private MediaType mMediaType = MediaType.INVALID;

        /**
         * Construction of SlotEntry.
         * @param index The index of slot
         * @param oritation The orientation of item, PORT or LAND
         * @param imageW the width of slot image
         * @param imageH the height of slot image
         * @param item the MediaItem of current index
         * @param imageRotation the rotation of media item
         * @param slotW the width of slot
         * @param gap the gap between slot
         * @param isCameraFolder if current folder is camera or not
         * @param name the name of current item
         */
        public SlotEntry(int index, int oritation, int imageW, int imageH, MediaItem item,
                int imageRotation, int slotW, int gap, boolean isCameraFolder, String name) {
            slotIndex = index;
            imageWidth = imageW;
            imageHeight = imageH;
            rotation = imageRotation;
            oriImageWidth = item.getWidth();
            oriImageHeight = item.getHeight();
            mimeType = item.getMimeType();
            albumName = name;

            if (imageWidth <= 0 || imageHeight <= 0) {
                // fix JE: item width or height is 0 in db
                imageWidth = slotW;
                imageHeight = slotW;
                isLandCameraFolder = false;
                scaledWidth = scaledHeight = slotW;
                Log.d(TAG, "<SlotEntry> imageWidth or imageHeight is 0, make default entry!!");
            } else {
                if (isCameraFolder && oritation == LAND) {
                    isLandCameraFolder = true;
                    scaledWidth = 2 * slotW + gap;
                    // scaledWidth and scaledHeight must meet 5:2 or 2:5
                    scaledHeight = Utils.clamp(imageHeight * scaledWidth / imageWidth,
                            (int) (scaledWidth * FancyHelper.FANCY_CROP_RATIO_LAND),
                            (int) (scaledWidth * FancyHelper.FANCY_CROP_RATIO));
                    MediaType mediaType = item.getMediaData().mediaType;
                    if (mediaType != MediaType.VIDEO && mediaType != MediaType.CONTAINER) {                           
                        scaledHeight = Math.round((float) scaledWidth / FancyHelper.FANCY_CROP_RATIO_CAMERA);
                    }
                    mMediaType = mediaType;
                } else {
                    scaledWidth = slotW;
                    // scaledWidth and scaledHeight must meet 5:2 or 2:5
                    scaledHeight = Utils.clamp(imageHeight * scaledWidth / imageWidth,
                            (int) (scaledWidth * FancyHelper.FANCY_CROP_RATIO_LAND),
                            (int) (scaledWidth * FancyHelper.FANCY_CROP_RATIO));
                }
            }
        }

        /**
         * Update the parameters of slot entry.
         * @param slotWidth
         * @param slotGap
         */
        public void update(int slotWidth, int slotGap) {
            if (imageWidth == 0 || imageHeight == 0) {
                scaledWidth = scaledHeight = slotWidth;
            } else {
                if (isLandCameraFolder) {
                    scaledWidth = 2 * slotWidth + slotGap;
                    // scaledWidth and scaledHeight must meet 5:2 or 2:5
                    scaledHeight = Utils.clamp(imageHeight * scaledWidth / imageWidth,
                            (int) (scaledWidth * FancyHelper.FANCY_CROP_RATIO_LAND),
                            (int) (scaledWidth * FancyHelper.FANCY_CROP_RATIO));
                    if (mMediaType != MediaType.VIDEO && mMediaType != MediaType.CONTAINER) {
                        scaledHeight = Math.round((float) scaledWidth / FancyHelper.FANCY_CROP_RATIO_CAMERA);
                    }
                } else {
                    scaledWidth = slotWidth;
                    // scaledWidth and scaledHeight must meet 5:2 or 2:5
                    scaledHeight = Utils.clamp(imageHeight * scaledWidth / imageWidth,
                            (int) (scaledWidth * FancyHelper.FANCY_CROP_RATIO_LAND),
                            (int) (scaledWidth * FancyHelper.FANCY_CROP_RATIO));
                }
            }
        }
    }

    // use this Array to collect all slots
    public ArrayList mSlotEntry = new ArrayList<SlotEntry>();
    private AbstractGalleryActivity mActivity = null;

    public static final int COL_NUM = 2;
    public static final int LAND = 0;
    public static final int PORT = 1;

    public ArrayList <Layout> mLayoutArray = new ArrayList();
    private Spec mDefaultLayoutSpec;

    private int mBackUpSlotCount = -1;
    private Layout mSwitchFromLayout;

    /// M: [FEATURE.ADD] Multi-window. @{
    private Spec mMultiWindowLayoutSpec;

    /**
     * Set SlotView spec of multi-window mode.
     * @param spec spec
     */
    public void setMultiWindowSpec(Spec spec) {
        mMultiWindowLayoutSpec = spec;
    }
    /// @}


    /**
     * Switch layout between default layout and fancy layout.
     * @param whichLayout
     */
    public void switchLayout(int whichLayout) {
        int visibleIndex = -1;
        int visibleStart = -1;
        Rect visibleStartRect;

        if (mSwitchFromLayout != null) {
            visibleStart = mSwitchFromLayout.getVisibleStart();
            LogUtil.i(TAG, "<switchLayout> <Fancy> visibleStart " + visibleStart);
        }

        switch (whichLayout) {
            case FancyHelper.FANCY_LAYOUT:
                if (mLayout != null && mLayout.getLayoutType() == FancyHelper.FANCY_LAYOUT) {
                    return;
                }

                LogUtil.i(TAG, "<switchLayout> <Fancy> switch to FANCY_LAYOUT");
                mLayout = mLayoutArray.get(FancyHelper.FANCY_LAYOUT);
                mLayout.clearColumnArray(0, true);
                mLayout.refreshSlotMap(0);

                // mLayout.setSize() may not be called when switch to Fancy
                // use backuped value to set viewHeight and ActionBarHeight
                if (mLayout != null) {
                    int viewHeightWhenPortrait = mLayout.getViewHeightWhenPortrait();
                    if (viewHeightWhenPortrait != -1) {
                        mLayout.setViewHeight(viewHeightWhenPortrait);
                    } else {
                        mLayout.setViewHeight(Math.max(FancyHelper
                                .getWidthPixels(), FancyHelper.getHeightPixels()));
                    }
                }
                if (mSwitchFromLayout != null) {
                    mLayout.setForceRefreshFlag(true);
                    mLayout.updateSlotCount(Math.max(0, mBackUpSlotCount));
                    if (mBackUpSlotCount <= 0) {
                        forceSetScrollPosition(0);
                    } else {
                        visibleStart = Utils.clamp(visibleStart, 0, mBackUpSlotCount - 1);
                        visibleStartRect = mLayout.getSlotRect(visibleStart, mTempRect);
                        forceSetScrollPosition(visibleStartRect.top);
                    }
                    mLayout.setForceRefreshFlag(false);
                }
                break;

            case FancyHelper.DEFAULT_LAYOUT:
                if (mLayout != null && mLayout.getLayoutType() == FancyHelper.DEFAULT_LAYOUT) {
                    return;
                }

                Log.i(TAG, "<switchLayout> <Fancy> switch to DEFAULT_LAYOUT");
                mLayout = mLayoutArray.get(FancyHelper.DEFAULT_LAYOUT);
                if (mSwitchFromLayout != null) {
                    mLayout.setForceRefreshFlag(true);
                    mLayout.setSlotSpec(mDefaultLayoutSpec);
                    setSlotCount(Math.max(0, mBackUpSlotCount));
                    if (mBackUpSlotCount <= 0) {
                        forceSetScrollPosition(0);
                    } else {
                        visibleStart = Utils.clamp(visibleStart, 0, mBackUpSlotCount - 1);
                        visibleStartRect = mLayout.getSlotRect(visibleStart, mTempRect);
                        forceSetScrollPosition(visibleStartRect.top);
                    }
                    mLayout.setForceRefreshFlag(false);
                }
                break;

            /// M: [FEATURE.ADD] Multi-window. @{
            case FancyHelper.MULTI_WINDOW_LAYOUT:
                if (mLayout != null
                        && mLayout.getLayoutType() == FancyHelper.MULTI_WINDOW_LAYOUT) {
                    return;
                }

                Log.i(TAG, "<switchLayout> switch to MULTI_WINDOW_LAYOUT");
                mLayout = mLayoutArray.get(FancyHelper.MULTI_WINDOW_LAYOUT);
                if (mSwitchFromLayout != null) {
                    mLayout.setForceRefreshFlag(true);
                    mLayout.setSlotSpec(mMultiWindowLayoutSpec);
                    setSlotCount(Math.max(0, mBackUpSlotCount));
                    if (mBackUpSlotCount <= 0) {
                        forceSetScrollPosition(0);
                    } else {
                        visibleStart = Utils.clamp(visibleStart, 0, mBackUpSlotCount - 1);
                        visibleStartRect = mLayout.getSlotRect(visibleStart, mTempRect);
                        forceSetScrollPosition(visibleStartRect.top);
                    }
                    mLayout.setForceRefreshFlag(false);
                }
                break;
                /// @}

            default:
                break;
        }
        mSwitchFromLayout = mLayout;
    }

    @Override
    public void onDataChange(int index, MediaItem item, int size, boolean isCameraFolder,
            String albumName) {
        if (mSlotArray == null || mLayout == null) {
            return;
        }
        if (size == 0) {
            mSlotArray.clear();
            mLayout.clearColumnArray(0, true);
            mLayout.refreshSlotMap(0);
            if (mLayout.getLayoutType() == FancyHelper.FANCY_LAYOUT) {
                mLayout.setForceRefreshFlag(true);
                mLayout.setScrollPosition(0);
                mLayout.setForceRefreshFlag(false);
            }
            invalidate();
            LogUtil.i(TAG, "<onDataChange> <Fancy> size = 0, no album in Gallery");
            return;
        }

        int slotArraySize  = mSlotArray.size();
        // no need to remove all mSlotArray, cause cluster won't reload all album from dirty album
        if (size > 0 && slotArraySize > size) {
            for (int i = slotArraySize - 1; i >= size; i--) {
                mSlotArray.remove(i);
            }
            mLayout.clearColumnArray(size - 1, false);
            mLayout.onDataChange(size - 1, null, 0, false);
            // refresh scroller bar when data changed
            mScroller.startScroll(0, 0, mLayout.getScrollLimit());
            invalidate();
        }
        if (item == null) {
            // when cover is null or when size changed, it goes here
            LogUtil.i(TAG, "<onDataChange> <Fancy> index " + index + ", item == null, return!!!");
            return;
        }
        if (index < slotArraySize) {
            SlotEntry entry = mSlotArray.get(index);
            // for camera folder, refresh every time
            // for normal folder, skip refreshing if same info
            if (!isCameraFolder && !entry.isLandCameraFolder
                    && entry.oriImageWidth == item.getWidth()
                    && entry.oriImageHeight == item.getHeight()
                    && entry.rotation == item.getRotation()
                    && entry.mimeType.equals(item.getMimeType())) {
                return;
            }
        }

        mLayout.clearColumnArray(index, false);

        addSlot(index, item, size, isCameraFolder, albumName);
        Log.i(TAG, "<onDataChange> <Fancy> index " + index + ", size " + size + ", isCameraFolder "
                + isCameraFolder);
        mLayout.onDataChange(index, item, size, isCameraFolder);
        // refresh scroller bar when data changed
        mScroller.startScroll(0, 0, mLayout.getScrollLimit());
        invalidate();
    }

    private void addSlot(int index, MediaItem item, int size, boolean isCameraFolder,
            String albumName) {
        if (item == null || mLayout == null || mSlotArray == null) {
            return;
        }

        SlotEntry slotEntry;
        int screenWidth = FancyHelper.getScreenWidthAtFancyMode();
        int slotGap = mLayout.getSlotGap();
        int slotWidth = (screenWidth - (COL_NUM - 1) * slotGap) / 2;
        int rotation = -1;
        int fancyOritation = -1;

        if (item.getMediaType() == MediaObject.MEDIA_TYPE_VIDEO) {
            // also constraint video width and height, e.g. 16x1080 video
            rotation = ((LocalVideo) item).getOrientation();
            fancyOritation = (rotation == 90 || rotation == 270) ? PORT : LAND;
        } else {
            rotation = item.getRotation();
            if (rotation == 90 || rotation == 270) {
                fancyOritation = item.getWidth() >= item.getHeight() ? PORT : LAND;
            } else {
                fancyOritation = item.getHeight() >= item.getWidth() ? PORT : LAND;
            }
        }

        int width = item.getMediaData().mediaType == MediaType.DRM ? item.getExtItem().getWidth()
                    : item.getWidth();
        int height = item.getMediaData().mediaType == MediaType.DRM ? item.getExtItem().getHeight()
                    : item.getHeight();
        // avoid DIVIDE_BY_ZERO exception.
        if (width <= 0 || height <= 0) {
            Log.i(TAG, "<addSlot> <Fancy> width <= 0 || height <= 0, file path: "
                    + item.getFilePath());
            width = slotWidth;
            height = slotWidth;
        }
        int constrainedWidth = width;
        int constrainedHeight = height;

        if (rotation == 90 || rotation == 270) {
            if ((float) width / (float) height > FancyHelper.FANCY_CROP_RATIO) {
                constrainedWidth = Math.round((float) height * FancyHelper.FANCY_CROP_RATIO);
            }
            slotEntry =
                    new SlotEntry(index, fancyOritation, constrainedHeight, constrainedWidth,
                            item, rotation, slotWidth, slotGap, isCameraFolder, albumName);
        } else {
            if ((float) height / (float) width > FancyHelper.FANCY_CROP_RATIO) {
                constrainedHeight = Math.round((float) width * FancyHelper.FANCY_CROP_RATIO);
            }
            slotEntry =
                    new SlotEntry(index, fancyOritation, constrainedWidth, constrainedHeight,
                            item, rotation, slotWidth, slotGap, isCameraFolder, albumName);
        }

        if (mSlotArray.size() > index) {
            mSlotArray.set(index, slotEntry);
        } else {
            mSlotArray.add(slotEntry);
        }
    }

    /**
     * Set scroll position forced.
     * @param position
     */
    public void forceSetScrollPosition(int position) {
        position = Utils.clamp(position, 0, mLayout.getScrollLimit());
        mScroller.setPosition(position);
        updateScrollPosition(position, true);
    }

    /**
     * Set padding spec of layout dynamic.
     * @param paddingTop
     * @param paddingBottom
     */
    public void setPaddingSpec(int paddingTop, int paddingBottom) {
        if (mLayout != null) {
            mLayout.setPaddingSpec(paddingTop, paddingBottom);
        }
    }
    /// @}
}
