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

package com.android.internal.policy;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.util.Log;
import android.view.Display;
import android.view.DisplayInfo;

import java.util.ArrayList;

import com.mediatek.common.prizeoption.PrizeOption; // prize-add-split screen-liyongli-20170826

/**
 * Calculates the snap targets and the snap position given a position and a velocity. All positions
 * here are to be interpreted as the left/top edge of the divider rectangle.
 *
 * @hide
 */
public class DividerSnapAlgorithm {

    private static final int MIN_FLING_VELOCITY_DP_PER_SECOND = 400;
    private static final int MIN_DISMISS_VELOCITY_DP_PER_SECOND = 600;

    /**
     * 3 snap targets: left/top has 16:9 ratio (for videos), 1:1, and right/bottom has 16:9 ratio
     */
    private static final int SNAP_MODE_16_9 = 0;

    /**
     * 3 snap targets: fixed ratio, 1:1, (1 - fixed ratio)
     */
    private static final int SNAP_FIXED_RATIO = 1;

    /**
     * 1 snap target: 1:1
     */
    private static final int SNAP_ONLY_1_1 = 2;

    private final float mMinFlingVelocityPxPerSecond;
    private final float mMinDismissVelocityPxPerSecond;
    private final int mDisplayWidth;
    private final int mDisplayHeight;
    private final int mDividerSize;
    private final ArrayList<SnapTarget> mTargets = new ArrayList<>();
    private final Rect mInsets = new Rect();
    private final int mSnapMode;
    private final int mMinimalSizeResizableTask;
    private final float mFixedRatio;
    private boolean mIsHorizontalDivision;

    /** The first target which is still splitting the screen */
    private final SnapTarget mFirstSplitTarget;

    /** The last target which is still splitting the screen */
    private final SnapTarget mLastSplitTarget;

    private final SnapTarget mDismissStartTarget;
    private final SnapTarget mDismissEndTarget;
    private final SnapTarget mMiddleTarget;

    public static DividerSnapAlgorithm create(Context ctx, Rect insets) {
        DisplayInfo displayInfo = new DisplayInfo();
        ctx.getSystemService(DisplayManager.class).getDisplay(
                Display.DEFAULT_DISPLAY).getDisplayInfo(displayInfo);
        int dividerWindowWidth = ctx.getResources().getDimensionPixelSize(
                com.android.internal.R.dimen.docked_stack_divider_thickness);
        int dividerInsets = 0;
        /* prize-add-split screen-liyongli-20170826-start */
        if(PrizeOption.PRIZE_SPLIT_SCREEN_BG_HINT_FOCUS) {
            dividerInsets = ctx.getResources().getDimensionPixelSize(
                    com.android.internal.R.dimen.prize_docked_stack_divider_insets);
        }else/* prize-add-split screen-liyongli-20170826-end */
        {
            dividerInsets = ctx.getResources().getDimensionPixelSize(
                com.android.internal.R.dimen.docked_stack_divider_insets);
        }
        
        return new DividerSnapAlgorithm(ctx.getResources(),
                displayInfo.logicalWidth, displayInfo.logicalHeight,
                dividerWindowWidth - 2 * dividerInsets,
                ctx.getApplicationContext().getResources().getConfiguration().orientation
                        == Configuration.ORIENTATION_PORTRAIT,
                insets);
    }

    public DividerSnapAlgorithm(Resources res, int displayWidth, int displayHeight, int dividerSize,
            boolean isHorizontalDivision, Rect insets) {
        mMinFlingVelocityPxPerSecond =
                MIN_FLING_VELOCITY_DP_PER_SECOND * res.getDisplayMetrics().density;
        mMinDismissVelocityPxPerSecond =
                MIN_DISMISS_VELOCITY_DP_PER_SECOND * res.getDisplayMetrics().density;
        mDividerSize = dividerSize;
        mDisplayWidth = displayWidth;
        mDisplayHeight = displayHeight;
        mIsHorizontalDivision = isHorizontalDivision;
        mInsets.set(insets);
        mSnapMode = res.getInteger(
                com.android.internal.R.integer.config_dockedStackDividerSnapMode);
        mFixedRatio = res.getFraction(
                com.android.internal.R.fraction.docked_stack_divider_fixed_ratio, 1, 1);
        mMinimalSizeResizableTask = res.getDimensionPixelSize(
                com.android.internal.R.dimen.default_minimal_size_resizable_task);
        calculateTargets(isHorizontalDivision);
        mFirstSplitTarget = mTargets.get(1);
        mLastSplitTarget = mTargets.get(mTargets.size() - 2);
        mDismissStartTarget = mTargets.get(0);
        mDismissEndTarget = mTargets.get(mTargets.size() - 1);
        mMiddleTarget = mTargets.get(mTargets.size() / 2);
    }

    /**
     * @return whether it's feasible to enable split screen in the current configuration, i.e. when
     *         snapping in the middle both tasks are larger than the minimal task size.
     */
    public boolean isSplitScreenFeasible() {
        int statusBarSize = mInsets.top;
        int navBarSize = mIsHorizontalDivision ? mInsets.bottom : mInsets.right;
        int size = mIsHorizontalDivision
                ? mDisplayHeight
                : mDisplayWidth;
        int availableSpace = size - navBarSize - statusBarSize - mDividerSize;
        return availableSpace / 2 >= mMinimalSizeResizableTask;
    }

    public SnapTarget calculateSnapTarget(int position, float velocity) {
        return calculateSnapTarget(position, velocity, true /* hardDismiss */);
    }

    /**
     * @param position the top/left position of the divider
     * @param velocity current dragging velocity
     * @param hardDismiss if set, make it a bit harder to get reach the dismiss targets
     */
    public SnapTarget calculateSnapTarget(int position, float velocity, boolean hardDismiss) {
        if (position < mFirstSplitTarget.position && velocity < -mMinDismissVelocityPxPerSecond) {
            return mDismissStartTarget;
        }
        if (position > mLastSplitTarget.position && velocity > mMinDismissVelocityPxPerSecond) {
            return mDismissEndTarget;
        }
        if (Math.abs(velocity) < mMinFlingVelocityPxPerSecond) {
            return snap(position, hardDismiss);
        }
        if (velocity < 0) {
            return mFirstSplitTarget;
        } else {
            return mLastSplitTarget;
        }
    }

    public SnapTarget calculateNonDismissingSnapTarget(int position) {
        SnapTarget target = snap(position, false /* hardDismiss */);
        if (target == mDismissStartTarget) {
            return mFirstSplitTarget;
        } else if (target == mDismissEndTarget) {
            return mLastSplitTarget;
        } else {
            return target;
        }
    }

    public float calculateDismissingFraction(int position) {
        if (position < mFirstSplitTarget.position) {
            return 1f - (float) (position - getStartInset())
                    / (mFirstSplitTarget.position - getStartInset());
        } else if (position > mLastSplitTarget.position) {
            return (float) (position - mLastSplitTarget.position)
                    / (mDismissEndTarget.position - mLastSplitTarget.position - mDividerSize);
        }
        return 0f;
    }

    public SnapTarget getClosestDismissTarget(int position) {
        if (position < mFirstSplitTarget.position) {
            return mDismissStartTarget;
        } else if (position > mLastSplitTarget.position) {
            return mDismissEndTarget;
        } else if (position - mDismissStartTarget.position
                < mDismissEndTarget.position - position) {
            return mDismissStartTarget;
        } else {
            return mDismissEndTarget;
        }
    }

    public SnapTarget getFirstSplitTarget() {
        return mFirstSplitTarget;
    }

    public SnapTarget getLastSplitTarget() {
        return mLastSplitTarget;
    }

    public SnapTarget getDismissStartTarget() {
        return mDismissStartTarget;
    }

    public SnapTarget getDismissEndTarget() {
        return mDismissEndTarget;
    }

    private int getStartInset() {
        if (mIsHorizontalDivision) {
            return mInsets.top;
        } else {
            return mInsets.left;
        }
    }

    private int getEndInset() {
        if (mIsHorizontalDivision) {
            return mInsets.bottom;
        } else {
            return mInsets.right;
        }
    }

    private SnapTarget snap(int position, boolean hardDismiss) {
        int minIndex = -1;
        float minDistance = Float.MAX_VALUE;
        int size = mTargets.size();
        for (int i = 0; i < size; i++) {
            SnapTarget target = mTargets.get(i);
            float distance = Math.abs(position - target.position);
            if (hardDismiss) {
                distance /= target.distanceMultiplier;
            }
            if (distance < minDistance) {
                minIndex = i;
                minDistance = distance;
            }
        }
        return mTargets.get(minIndex);
    }

    private void calculateTargets(boolean isHorizontalDivision) {
        mTargets.clear();
        int dividerMax = isHorizontalDivision
                ? mDisplayHeight
                : mDisplayWidth;
        mTargets.add(new SnapTarget(-mDividerSize, -mDividerSize, SnapTarget.FLAG_DISMISS_START,
                0.35f));
        switch (mSnapMode) {
            case SNAP_MODE_16_9:
                addRatio16_9Targets(isHorizontalDivision, dividerMax);
                break;
            case SNAP_FIXED_RATIO:
                addFixedDivisionTargets(isHorizontalDivision, dividerMax);
                break;
            case SNAP_ONLY_1_1:
                addMiddleTarget(isHorizontalDivision);
                break;
        }
        int navBarSize = isHorizontalDivision ? mInsets.bottom : mInsets.right;
        mTargets.add(new SnapTarget(dividerMax - navBarSize, dividerMax,
                SnapTarget.FLAG_DISMISS_END, 0.35f));
    }

    private void addNonDismissingTargets(boolean isHorizontalDivision, int topPosition,
            int bottomPosition, int dividerMax) {
        maybeAddTarget(topPosition, topPosition - mInsets.top);
        addMiddleTarget(isHorizontalDivision);
        maybeAddTarget(bottomPosition, dividerMax - mInsets.bottom
                - (bottomPosition + mDividerSize));
    }

    private void addFixedDivisionTargets(boolean isHorizontalDivision, int dividerMax) {
        int start = isHorizontalDivision ? mInsets.top : mInsets.left;
        int end = isHorizontalDivision
                ? mDisplayHeight - mInsets.bottom
                : mDisplayWidth - mInsets.right;
        int size = (int) (mFixedRatio * (end - start)) - mDividerSize / 2;
        int topPosition = start + size;
        int bottomPosition = end - size - mDividerSize;
        addNonDismissingTargets(isHorizontalDivision, topPosition, bottomPosition, dividerMax);
    }

    private void addRatio16_9Targets(boolean isHorizontalDivision, int dividerMax) {
        int start = isHorizontalDivision ? mInsets.top : mInsets.left;
        int end = isHorizontalDivision
                ? mDisplayHeight - mInsets.bottom
                : mDisplayWidth - mInsets.right;
        int startOther = isHorizontalDivision ? mInsets.left : mInsets.top;
        int endOther = isHorizontalDivision
                ? mDisplayWidth - mInsets.right
                : mDisplayHeight - mInsets.bottom;
        float size = 9.0f / 16.0f * (endOther - startOther);
        int sizeInt = (int) Math.floor(size);
        int topPosition = start + sizeInt;
        int bottomPosition = end - sizeInt - mDividerSize;
        addNonDismissingTargets(isHorizontalDivision, topPosition, bottomPosition, dividerMax);
    }

    /**
     * Adds a target at {@param position} but only if the area with size of {@param smallerSize}
     * meets the minimal size requirement.
     */
    private void maybeAddTarget(int position, int smallerSize) {
        if (smallerSize >= mMinimalSizeResizableTask) {
            mTargets.add(new SnapTarget(position, position, SnapTarget.FLAG_NONE));
        }
    }

    private void addMiddleTarget(boolean isHorizontalDivision) {
        int position = DockedDividerUtils.calculateMiddlePosition(isHorizontalDivision,
                mInsets, mDisplayWidth, mDisplayHeight, mDividerSize);
        mTargets.add(new SnapTarget(position, position, SnapTarget.FLAG_NONE));
    }

    public SnapTarget getMiddleTarget() {
        return mMiddleTarget;
    }

    public SnapTarget getNextTarget(SnapTarget snapTarget) {
        int index = mTargets.indexOf(snapTarget);
        if (index != -1 && index < mTargets.size() - 1) {
            return mTargets.get(index + 1);
        }
        return snapTarget;
    }

    public SnapTarget getPreviousTarget(SnapTarget snapTarget) {
        int index = mTargets.indexOf(snapTarget);
        if (index != -1 && index > 0) {
            return mTargets.get(index - 1);
        }
        return snapTarget;
    }

    public boolean isFirstSplitTargetAvailable() {
        return mFirstSplitTarget != mMiddleTarget;
    }

    public boolean isLastSplitTargetAvailable() {
        return mLastSplitTarget != mMiddleTarget;
    }

    /**
     * Cycles through all non-dismiss targets with a stepping of {@param increment}. It moves left
     * if {@param increment} is negative and moves right otherwise.
     */
    public SnapTarget cycleNonDismissTarget(SnapTarget snapTarget, int increment) {
        int index = mTargets.indexOf(snapTarget);
        if (index != -1) {
            SnapTarget newTarget = mTargets.get((index + mTargets.size() + increment)
                    % mTargets.size());
            if (newTarget == mDismissStartTarget) {
                return mLastSplitTarget;
            } else if (newTarget == mDismissEndTarget) {
                return mFirstSplitTarget;
            } else {
                return newTarget;
            }
        }
        return snapTarget;
    }

    /**
     * Represents a snap target for the divider.
     */
    public static class SnapTarget {
        public static final int FLAG_NONE = 0;

        /** If the divider reaches this value, the left/top task should be dismissed. */
        public static final int FLAG_DISMISS_START = 1;

        /** If the divider reaches this value, the right/bottom task should be dismissed */
        public static final int FLAG_DISMISS_END = 2;

        /** Position of this snap target. The right/bottom edge of the top/left task snaps here. */
        public final int position;

        /**
         * Like {@link #position}, but used to calculate the task bounds which might be different
         * from the stack bounds.
         */
        public final int taskPosition;

        public final int flag;

        /**
         * Multiplier used to calculate distance to snap position. The lower this value, the harder
         * it's to snap on this target
         */
        private final float distanceMultiplier;

        public SnapTarget(int position, int taskPosition, int flag) {
            this(position, taskPosition, flag, 1f);
        }

        public SnapTarget(int position, int taskPosition, int flag, float distanceMultiplier) {
            this.position = position;
            this.taskPosition = taskPosition;
            this.flag = flag;
            this.distanceMultiplier = distanceMultiplier;
        }
    }
}
