/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
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

package com.android.gallery3d.app;

import android.app.Activity;
import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;

import com.android.gallery3d.app.CommonControllerOverlay.PlayControlExt;
import com.android.gallery3d.common.ApiHelper;
import com.mediatek.gallery3d.video.MtkVideoFeature;
import com.mediatek.gallery3d.video.SlowMotionItem;
import com.mediatek.gallery3d.video.SlowMotionTrimTimebar;
/**
 * The controller for the Trimming Video.
 */
public class TrimControllerOverlay extends CommonControllerOverlay  {

    public TrimControllerOverlay(Context context) {
        super(context);
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        int padding = (int) (metrics.density * 10);
        mTimeBar.setPadding(padding, 0, padding, 0);
    }

    @Override
    protected void createTimeBar(Context context) {
        if (MtkVideoFeature.isSlowMotionSupport()) {
            SlowMotionItem item = new SlowMotionItem(context, ((Activity) context).getIntent()
                    .getData());
            if (item.isSlowMotionVideo()) {
                mTimeBar = new SlowMotionTrimTimebar(context, this);
            } else {
                mTimeBar = new TrimTimeBar(context, this);
            }
        } else {
            mTimeBar = new TrimTimeBar(context, this);
        }
    }
	
	@Override
	protected void createPlayControlExt(Context context) {
		
    	mPlayControlExt = new PlayControlExt(context, this, true);
	}

    @Override
    protected void onPlayerWrapperChanged() {
        if (mTimeBar != null && mTimeBar instanceof SlowMotionTrimTimebar) {
            ((SlowMotionTrimTimebar)mTimeBar).setMediaPlayerWrapper(mMediaPlayerWrapper);
        }
    }

    private void hidePlayButtonIfPlaying() {
        /*PRIZE-Support suspension function-wanzhijuan-2015-4-13-start*/
        // Update access play pause interface
        if (mState == State.PLAYING) {
            mPlayControlExt.getPlayPauseIm().setVisibility(View.INVISIBLE);
        }
        if (ApiHelper.HAS_OBJECT_ANIMATION) {
            mPlayControlExt.getPlayPauseIm().setAlpha(1f);
        }
        /*PRIZE-Support suspension function-wanzhijuan-2015-4-13-end*/
    }

    @Override
    public void showPlaying() {
        super.showPlaying();
        if (ApiHelper.HAS_OBJECT_ANIMATION) {
        // Add animation to hide the play button while playing.
        /*PRIZE-Support suspension function-wanzhijuan-2015-4-13-start*/
        // Update access play pause interface
        ObjectAnimator anim = ObjectAnimator.ofFloat(mPlayControlExt.getPlayPauseIm(), "alpha", 1f, 0f);
        /*PRIZE-Support suspension function-wanzhijuan-2015-4-13-end*/
        anim.setDuration(200);
        anim.start();
        anim.addListener(new AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                hidePlayButtonIfPlaying();
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                hidePlayButtonIfPlaying();
            }

            @Override
            public void onAnimationRepeat(Animator animation) {
            }
          });
        } else {
            hidePlayButtonIfPlaying();
        }
    }

    @Override
    public void setTimes(int currentTime, int totalTime, int trimStartTime, int trimEndTime) {
        mTimeBar.setTime(currentTime, totalTime, trimStartTime, trimEndTime);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (super.onTouchEvent(event)) {
            return true;
        }

        // The special thing here is that the State.ENDED include both cases of
        // the video completed and current == trimEnd. Both request a replay.
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (mState == State.PLAYING || mState == State.PAUSED) {
                    mListener.onPlayPause();
                } else if (mState == State.ENDED) {
                    if (mCanReplay) {
                        mListener.onReplay();
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
                break;
        }
        return true;
    }

    /*PRIZE-touch screen to adjust brightness, volume, progress-wanzhijuan-2015-3-30-start*/
    @Override
    public void setDragTimes(int position) {
    }
    /*PRIZE-touch screen to adjust brightness, volume, progress-wanzhijuan-2015-3-30-end*/
}
