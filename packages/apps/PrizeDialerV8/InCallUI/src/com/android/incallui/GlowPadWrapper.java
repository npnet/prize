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
 * limitations under the License
 */

package com.android.incallui;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.telecom.VideoProfile;
import android.util.AttributeSet;
import android.view.View;

import com.android.dialer.R;
import com.android.incallui.widget.multiwaveview.GlowPadView;

/**
 *
 */
public class GlowPadWrapper extends GlowPadView implements GlowPadView.OnTriggerListener {

    // Parameters for the GlowPadView "ping" animation; see triggerPing().
    private static final int PING_MESSAGE_WHAT = 101;
    private static final boolean ENABLE_PING_AUTO_REPEAT = true;
    private static final long PING_REPEAT_DELAY_MS = 1200;

    private final Handler mPingHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case PING_MESSAGE_WHAT:
                    triggerPing();
                    break;
            }
        }
    };

    private AnswerFragment mAnswerFragment;
    private boolean mPingEnabled = true;
    private boolean mTargetTriggered = false;
    private int mVideoState = VideoProfile.STATE_BIDIRECTIONAL;

    public GlowPadWrapper(Context context) {
        super(context);
        Log.d(this, "class created " + this + " ");
    }

    public GlowPadWrapper(Context context, AttributeSet attrs) {
        super(context, attrs);
        Log.d(this, "class created " + this);
    }

    @Override
    protected void onFinishInflate() {
        Log.d(this, "onFinishInflate()");
        super.onFinishInflate();
        setOnTriggerListener(this);
    }

    public void startPing() {
        Log.d(this, "startPing");
        mPingEnabled = true;
        triggerPing();
    }

    public void stopPing() {
        Log.d(this, "stopPing");
        mPingEnabled = false;
        mPingHandler.removeMessages(PING_MESSAGE_WHAT);
    }

    private void triggerPing() {
        Log.d(this, "triggerPing(): " + mPingEnabled + " " + this);
        if (mPingEnabled && !mPingHandler.hasMessages(PING_MESSAGE_WHAT)) {
            ping();

            if (ENABLE_PING_AUTO_REPEAT) {
                mPingHandler.sendEmptyMessageDelayed(PING_MESSAGE_WHAT, PING_REPEAT_DELAY_MS);
            }
        }
    }

    @Override
    public void onGrabbed(View v, int handle) {
        Log.d(this, "onGrabbed()");
        stopPing();
    }

    @Override
    public void onReleased(View v, int handle) {
        Log.d(this, "onReleased()");
        if (mTargetTriggered) {
            mTargetTriggered = false;
        } else {
            startPing();
        }
    }

    @Override
    public void onTrigger(View v, int target) {
        Log.d(this, "onTrigger() view=" + v + " target=" + target);
        final int resId = getResourceIdForTarget(target);
        if (resId == R.drawable.ic_lockscreen_answer) {
            mAnswerFragment.onAnswer(VideoProfile.STATE_AUDIO_ONLY, getContext());
            mTargetTriggered = true;
        } else if (resId == R.drawable.ic_lockscreen_decline) {
            mAnswerFragment.onDecline(getContext());
            mTargetTriggered = true;
        } else if (resId == R.drawable.ic_lockscreen_text) {
            mAnswerFragment.onText();
            mTargetTriggered = true;
        } else if (resId == R.drawable.ic_videocam || resId == R.drawable.ic_lockscreen_answer_video) {
            mAnswerFragment.onAnswer(mVideoState, getContext());
            mTargetTriggered = true;
        } else if (resId == R.drawable.ic_lockscreen_decline_video) {
            mAnswerFragment.onDeclineUpgradeRequest(getContext());
            mTargetTriggered = true;
        } else {
            // Code should never reach here.
            Log.e(this, "Trigger detected on unhandled resource. Skipping.");
        }
    }

    @Override
    public void onGrabbedStateChange(View v, int handle) {

    }

    @Override
    public void onFinishFinalAnimation() {

    }

    public void setAnswerFragment(AnswerFragment fragment) {
        mAnswerFragment = fragment;
    }

    /**
     * Sets the video state represented by the "video" icon on the glow pad.
     *
     * @param videoState The new video state.
     */
    public void setVideoState(int videoState) {
        mVideoState = videoState;
    }
}
