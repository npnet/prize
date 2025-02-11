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

package com.android.keyguard;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.TextView;
import android.util.Log;
import android.os.Handler;
import android.os.SystemProperties;

import com.android.internal.widget.LockPatternUtils;

/* prize add by lijimeng 2016-12-5 bugid:25907 start*/
import com.mediatek.keyguard.AntiTheft.AntiTheftManager;
import com.android.keyguard.KeyguardSecurityModel;
import com.android.keyguard.KeyguardSecurityModel.SecurityMode;
/* prize add by lijimeng 2016-12-5 bugid:25907 end*/
public class NumPadKey extends ViewGroup {
    // list of "ABC", etc per digit, starting with '0'
    static String sKlondike[];

    private int mDigit = -1;
    private int mTextViewResId;
    private PasswordTextView mTextView;
    private TextView mDigitText;
    private TextView mKlondikeText;
    private boolean mEnableHaptics;
    private PowerManager mPM;
	int commit_len = 4;/*prize-length of pin/puk code-zhangjialong-20150917*/

    /* prize add by lijimeng 2016-12-5 bugid:25907 start*/
	private KeyguardSecurityModel mKeyguardSecurityModel;
	/* prize add by lijimeng 2016-12-5 bugid:25907 end*/
    private View.OnClickListener mListener = new View.OnClickListener() {
        @Override
        public void onClick(View thisView) {
            if (mTextView == null && mTextViewResId > 0) {
                final View v = NumPadKey.this.getRootView().findViewById(mTextViewResId);
                if (v != null && v instanceof PasswordTextView) {
                    mTextView = (PasswordTextView) v;
                }
            }
			/* prize add by lijimeng 2016-12-5 bugid:25907 start*/
			if(mKeyguardSecurityModel == null){
				mKeyguardSecurityModel = new KeyguardSecurityModel(getContext());
			}
			/* prize add by lijimeng 2016-12-5 bugid:25907 end*/
            /*PRIZE-limit PIN pwd length-liufan-2015-07-22-start*/ 
            boolean isFpUnlock = SystemProperties.get("persist.sys.prize_fp_enable").equals("1");
            isFpUnlock = true;
            if(isFpUnlock){
                String text = mTextView.getText();
				
                /*prize-length of pin/puk -zhangjialong-20150917-start*/
				boolean mPuk= mTextView.getKeyguardPinBasedInputView().isPukInput();  
				boolean mPin= mTextView.getKeyguardPinBasedInputView().isPinInput();

                if(mPin){
                    commit_len =8;//for pin
                } else
				if(mPuk){
                    commit_len =8;//for puk , cmcc ...etc
				/* prize add by lijimeng 2016-12-5 bugid:25907 start*/
				}else if(AntiTheftManager.AntiTheftMode.PplLock == AntiTheftManager.getCurrentAntiTheftMode() && mKeyguardSecurityModel.getSecurityMode() == SecurityMode.AntiTheft){
						commit_len = SystemProperties.getInt("persist.sys.prize_fp_password",6);
				/* prize add by lijimeng 2016-12-5 bugid:25907 end*/
				}else{
                    commit_len=4; //for pin
				}		
	        	/*prize-length of pin/puk -zhangjialong-20150917-end*/
				
                if (mTextView != null && mTextView.isEnabled() && text.length() < commit_len) {
                    mTextView.append(Character.forDigit(mDigit, 10));
                }
                text = mTextView.getText();

                if(mPin || mPuk){
                    if(text.length() >= commit_len){
                        
                    }
                }else{
                    if(text.length() >= commit_len && mTextView.getKeyguardPinBasedInputView() != null){
                        postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                mTextView.getKeyguardPinBasedInputView().commit();
                            }
                        }, 200);
                    }
                }
            }else{
                if (mTextView != null && mTextView.isEnabled()) {
                    mTextView.append(Character.forDigit(mDigit, 10));
                }
            }
            /*PRIZE-limit PIN pwd length-liufan-2015-07-22-end*/
            userActivity();
            doHapticKeyClick();
            mTextView.getKeyguardPinBasedInputView().setPwStatus();
        }
    };

    public void userActivity() {
        mPM.userActivity(SystemClock.uptimeMillis(), false);
    }

    public NumPadKey(Context context) {
        this(context, null);
    }

    public NumPadKey(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public NumPadKey(Context context, AttributeSet attrs, int defStyle) {
        this(context, attrs, defStyle, R.layout.keyguard_num_pad_key);
    }

    protected NumPadKey(Context context, AttributeSet attrs, int defStyle, int contentResource) {
        super(context, attrs, defStyle);
        setFocusable(true);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.NumPadKey);

        try {
            mDigit = a.getInt(R.styleable.NumPadKey_digit, mDigit);
            mTextViewResId = a.getResourceId(R.styleable.NumPadKey_textView, 0);
        } finally {
            a.recycle();
        }

        setOnClickListener(mListener);
        setOnHoverListener(new LiftToActivateListener(context));
        setAccessibilityDelegate(new ObscureSpeechDelegate(context));

        mEnableHaptics = new LockPatternUtils(context).isTactileFeedbackEnabled();

        mPM = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(contentResource, this, true);

        mDigitText = (TextView) findViewById(R.id.digit_text);
        mDigitText.setText(Integer.toString(mDigit));
        mKlondikeText = (TextView) findViewById(R.id.klondike_text);

        if (mDigit >= 0) {
            if (sKlondike == null) {
                sKlondike = getResources().getStringArray(R.array.lockscreen_num_pad_klondike);
            }
            if (sKlondike != null && sKlondike.length > mDigit) {
                String klondike = sKlondike[mDigit];
                final int len = klondike.length();
                if (len > 0) {
                    mKlondikeText.setText(klondike);
                } else {
                    mKlondikeText.setVisibility(View.INVISIBLE);
                }
            }
        }

        /**prize-keyguard-hekeyi-2015.12.21-begin*/
        /*a = context.obtainStyledAttributes(attrs, android.R.styleable.View);
        if (!a.hasValueOrEmpty(android.R.styleable.View_background)) {
            //setBackground(mContext.getDrawable(R.drawable.ripple_drawable));
        }
        a.recycle();*/
        /**prize-keyguard-hekeyi-2015.12.21-end*/
        setContentDescription(mDigitText.getText().toString());
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            doHapticKeyClick();
        }
        return super.onTouchEvent(event);
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        // Reset the "announced headset" flag when detached.
        ObscureSpeechDelegate.sAnnouncedHeadset = false;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        measureChildren(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int digitHeight = mDigitText.getMeasuredHeight();
        int klondikeHeight = mKlondikeText.getMeasuredHeight();
        int totalHeight = digitHeight + klondikeHeight;
        int top = getHeight() / 2 - totalHeight / 2;
        int centerX = getWidth() / 2;
        int left = centerX - mDigitText.getMeasuredWidth() / 2;
        int bottom = top + digitHeight;
        mDigitText.layout(left, top, left + mDigitText.getMeasuredWidth(), bottom);
        top = (int) (bottom - klondikeHeight * 0.35f);
        bottom = top + klondikeHeight;

        left = centerX - mKlondikeText.getMeasuredWidth() / 2;
        mKlondikeText.layout(left, top, left + mKlondikeText.getMeasuredWidth(), bottom);
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    // Cause a VIRTUAL_KEY vibration
    public void doHapticKeyClick() {
        if (mEnableHaptics) {
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY,
                    HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                    | HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        }
    }
}
