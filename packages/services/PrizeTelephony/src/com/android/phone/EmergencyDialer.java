/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.phone;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.StatusBarManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.telecom.PhoneAccount;
import android.telephony.CarrierConfigManager;
import android.telephony.PhoneNumberUtils;
import android.telephony.SubscriptionManager;
import android.text.Editable;
import android.text.InputType;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.DialerKeyListener;
import android.text.style.TtsSpan;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;

import com.android.phone.common.dialpad.DialpadKeyButton;
import com.android.phone.common.util.ViewUtil;
// add for plug in.
import com.mediatek.phone.ext.ExtensionManager;
import com.mediatek.phone.ext.IEmergencyDialerExt;

import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.view.Window;
import android.graphics.Color;
/**
 * EmergencyDialer is a special dialer that is used ONLY for dialing emergency calls.
 *
 * It's a simplified version of the regular dialer (i.e. the TwelveKeyDialer
 * activity from apps/Contacts) that:
 *   1. Allows ONLY emergency calls to be dialed
 *   2. Disallows voicemail functionality
 *   3. Uses the FLAG_SHOW_WHEN_LOCKED window manager flag to allow this
 *      activity to stay in front of the keyguard.
 *
 * TODO: Even though this is an ultra-simplified version of the normal
 * dialer, there's still lots of code duplication between this class and
 * the TwelveKeyDialer class from apps/Contacts.  Could the common code be
 * moved into a shared base class that would live in the framework?
 * Or could we figure out some way to move *this* class into apps/Contacts
 * also?
 */
public class EmergencyDialer extends Activity implements View.OnClickListener,
        View.OnLongClickListener, View.OnKeyListener, TextWatcher, DialpadKeyButton.OnPressedListener{
// @}
    // Keys used with onSaveInstanceState().
    private static final String LAST_NUMBER = "lastNumber";

    // Intent action for this activity.
    public static final String ACTION_DIAL = "com.android.phone.EmergencyDialer.DIAL";

    // List of dialer button IDs.
    private static final int[] DIALER_KEYS = new int[] {
            R.id.one, R.id.two, R.id.three,
            R.id.four, R.id.five, R.id.six,
            R.id.seven, R.id.eight, R.id.nine,
            R.id.star, R.id.zero, R.id.pound };

    // Debug constants.
    private static final boolean DBG = false;
    private static final String LOG_TAG = "EmergencyDialer";

    private StatusBarManager mStatusBarManager;

    /** The length of DTMF tones in milliseconds */
    private static final int TONE_LENGTH_MS = 150;

    /** The DTMF tone volume relative to other sounds in the stream */
    private static final int TONE_RELATIVE_VOLUME = 80;

    /** Stream type used to play the DTMF tones off call, and mapped to the volume control keys */
    private static final int DIAL_TONE_STREAM_TYPE = AudioManager.STREAM_DTMF;

    private static final int BAD_EMERGENCY_NUMBER_DIALOG = 0;

    // private static final int USER_ACTIVITY_TIMEOUT_WHEN_NO_PROX_SENSOR = 15000; // millis

    EditText mDigits;
    private View mDialButton;
    private View mDelete;

    private ToneGenerator mToneGenerator;
    private Object mToneGeneratorLock = new Object();

    // determines if we want to playback local DTMF tones.
    private boolean mDTMFToneEnabled;

  
    private EmergencyActionGroup mEmergencyActionGroup;

    // close activity when screen turns off
    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                finishAndRemoveTask();
            }
        }
    };

    private String mLastNumber; // last number we tried to dial. Used to restore error dialog.

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        // Do nothing
    }

    @Override
    public void onTextChanged(CharSequence input, int start, int before, int changeCount) {
        // Do nothing
    }

    @Override
    public void afterTextChanged(Editable input) {
        // Check for special sequences, in particular the "**04" or "**05"
        // sequences that allow you to enter PIN or PUK-related codes.
        //
        // But note we *don't* allow most other special sequences here,
        // like "secret codes" (*#*#<code>#*#*) or IMEI display ("*#06#"),
        // since those shouldn't be available if the device is locked.
        //
        // So we call SpecialCharSequenceMgr.handleCharsForLockedDevice()
        // here, not the regular handleChars() method.

        /// M: For ALPS02836874 @{
        // Here skip handle MMI when under non-Owner mode, which will use non-existed PhoneGlobals.
        /* Original Code:
        if (SpecialCharSequenceMgr.handleCharsForLockedDevice(this, input.toString(), this)) {
        */
        if (UserHandle.myUserId() == 0
               && SpecialCharSequenceMgr.handleCharsForLockedDevice(this, input.toString(), this)) {
        /// @}
            // A special sequence was entered, clear the digits
            mDigits.getText().clear();
        }

        updateDialAndDeleteButtonStateEnabledAttr();
        updateTtsSpans();
    }

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        /**public-prize-emergencyDialer-hekeyi-20151222-begin*/
        initStatusBar();
        /**public-prize-emergencyDialer-hekeyi-20151222-end*/
        mStatusBarManager = (StatusBarManager) getSystemService(Context.STATUS_BAR_SERVICE);

        // Allow this activity to be displayed in front of the keyguard / lockscreen.
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.flags |= WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;

        // When no proximity sensor is available, use a shorter timeout.
        // TODO: Do we enable this for non proximity devices any more?
        // lp.userActivityTimeout = USER_ACTIVITY_TIMEOUT_WHEN_NO_PROX_SENSOR;

        getWindow().setAttributes(lp);
        // Hide the soft input method
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        setContentView(R.layout.emergency_dialer);

        mDigits = (EditText) findViewById(R.id.digits);
        mDigits.setKeyListener(DialerKeyListener.getInstance());
        mDigits.setOnClickListener(this);
        mDigits.setOnKeyListener(this);
        mDigits.setLongClickable(false);
        mDigits.setInputType(InputType.TYPE_NULL);
        maybeAddNumberFormatting();
        
        /*prize-add-hpf-2017-10-24-start*/
        TextView prizeTvBackBtn = (TextView)findViewById(R.id.prize_tv_back_btn);
        prizeTvBackBtn.setOnClickListener(new OnClickListener(){
            @Override
            public void onClick(View arg0) {
                onBackPressed();
            }        	
        });
        /*prize-add-hpf-2017-10-24-end*/

        // Check for the presence of the keypad
        View view = findViewById(R.id.one);
        if (view != null) {
            setupKeypad();
        }

        mDelete = findViewById(R.id.deleteButton);
        mDelete.setOnClickListener(this);
        mDelete.setOnLongClickListener(this);

        mDialButton = findViewById(R.id.floating_action_button);

        // Check whether we should show the onscreen "Dial" button and co.
        // Read carrier config through the public API because PhoneGlobals is not available when we
        // run as a secondary user.
        CarrierConfigManager configMgr =
                (CarrierConfigManager) getSystemService(Context.CARRIER_CONFIG_SERVICE);
        PersistableBundle carrierConfig =
                configMgr.getConfigForSubId(SubscriptionManager.getDefaultVoiceSubscriptionId());
        if (carrierConfig.getBoolean(CarrierConfigManager.KEY_SHOW_ONSCREEN_DIAL_BUTTON_BOOL)) {
            mDialButton.setOnClickListener(this);
        } else {
            mDialButton.setVisibility(View.GONE);
        }
        //ViewUtil.setupFloatingActionButton(mDialButton, getResources());  //hekeyi

        ///M: add for CT plug in. @{
        ExtensionManager.getEmergencyDialerExt(this).onCreate(this);
        ///  @}

        if (icicle != null) {
            super.onRestoreInstanceState(icicle);
        }

        // Extract phone number from intent
        Uri data = getIntent().getData();
        if (data != null && (PhoneAccount.SCHEME_TEL.equals(data.getScheme()))) {
            String number = PhoneNumberUtils.getNumberFromIntent(getIntent(), this);
            if (number != null) {
                mDigits.setText(number);
            }
        }

        // if the mToneGenerator creation fails, just continue without it.  It is
        // a local audio signal, and is not as important as the dtmf tone itself.
        synchronized (mToneGeneratorLock) {
            if (mToneGenerator == null) {
                try {
                    mToneGenerator = new ToneGenerator(DIAL_TONE_STREAM_TYPE, TONE_RELATIVE_VOLUME);
                } catch (RuntimeException e) {
                    Log.w(LOG_TAG, "Exception caught while creating local tone generator: " + e);
                    mToneGenerator = null;
                }
            }
        }

        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(mBroadcastReceiver, intentFilter);

        mEmergencyActionGroup = (EmergencyActionGroup) findViewById(R.id.emergency_action_group);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        synchronized (mToneGeneratorLock) {
            if (mToneGenerator != null) {
                mToneGenerator.release();
                mToneGenerator = null;
            }
        }
        unregisterReceiver(mBroadcastReceiver);
        //add for plug in.@{
        ExtensionManager.getEmergencyDialerExt(this).onDestroy(this);
        //add for plug in.@}
    }

    @Override
    protected void onRestoreInstanceState(Bundle icicle) {
        mLastNumber = icicle.getString(LAST_NUMBER);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(LAST_NUMBER, mLastNumber);
    }

    /**
     * Explicitly turn off number formatting, since it gets in the way of the emergency
     * number detector
     */
    protected void maybeAddNumberFormatting() {
        // Do nothing.
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        // This can't be done in onCreate(), since the auto-restoring of the digits
        // will play DTMF tones for all the old digits if it is when onRestoreSavedInstanceState()
        // is called. This method will be called every time the activity is created, and
        // will always happen after onRestoreSavedInstanceState().
        mDigits.addTextChangedListener(this);
    }

    private void setupKeypad() {
        // Setup the listeners for the buttons
        for (int id : DIALER_KEYS) {
            /*PRIZE-Change-Prevent_Bad_Contact-wangzhong-2017_10_31-start*/
            /*final DialpadKeyButton key = (DialpadKeyButton) findViewById(id);
            key.setOnPressedListener(this);*/
            final android.widget.ImageView dialpadKey = (android.widget.ImageView) findViewById(id);
            dialpadKey.setOnTouchListener(new View.OnTouchListener() {

                @Override
                public boolean onTouch(View arg0, android.view.MotionEvent arg1) {
                    switch (arg1.getAction()) {
                        case android.view.MotionEvent.ACTION_DOWN:
                            onPressed(dialpadKey, true);
                            return false;
                        // TODO:This MotionEvent.ACTION_CANCEL handling multi-touch failure cause button voice can't stop when SanZhiJiePing is opened.
                        case android.view.MotionEvent.ACTION_CANCEL:
                        case android.view.MotionEvent.ACTION_UP:
                            dialpadKey.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_CLICKED);
                            onPressed(dialpadKey, false);
                            return false;
                        default:
                            return false;
                    }
                }
            });
            dialpadKey.setOnLongClickListener(this);
            /*PRIZE-Change-Prevent_Bad_Contact-wangzhong-2017_10_31-end*/
        }

        View view = findViewById(R.id.zero);
        view.setOnLongClickListener(this);
    }

    /**
     * handle key events
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            // Happen when there's a "Call" hard button.
            case KeyEvent.KEYCODE_CALL: {
                if (TextUtils.isEmpty(mDigits.getText().toString())) {
                    // if we are adding a call from the InCallScreen and the phone
                    // number entered is empty, we just close the dialer to expose
                    // the InCallScreen under it.
                    finish();
                } else {
                    // otherwise, we place the call.
                    placeCall();
                }
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    private void keyPressed(int keyCode) {
        mDigits.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, keyCode);
        mDigits.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKey(View view, int keyCode, KeyEvent event) {
        switch (view.getId()) {
            case R.id.digits:
                // Happen when "Done" button of the IME is pressed. This can happen when this
                // Activity is forced into landscape mode due to a desk dock.
                if (keyCode == KeyEvent.KEYCODE_ENTER
                        && event.getAction() == KeyEvent.ACTION_UP) {
                    placeCall();
                    return true;
                }
                break;
        }
        return false;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        mEmergencyActionGroup.onPreTouchEvent(ev);
        boolean handled = super.dispatchTouchEvent(ev);
        mEmergencyActionGroup.onPostTouchEvent(ev);
        return handled;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.deleteButton: {
                keyPressed(KeyEvent.KEYCODE_DEL);
                return;
            }
            case R.id.floating_action_button: {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                placeCall();
                return;
            }
            case R.id.digits: {
                if (mDigits.length() != 0) {
                    mDigits.setCursorVisible(true);
                }
                return;
            }
        }
    }

    @Override
    public void onPressed(View view, boolean pressed) {
        if (!pressed) {
            return;
        }
        switch (view.getId()) {
            case R.id.one: {
                playTone(ToneGenerator.TONE_DTMF_1);
                keyPressed(KeyEvent.KEYCODE_1);
                return;
            }
            case R.id.two: {
                playTone(ToneGenerator.TONE_DTMF_2);
                keyPressed(KeyEvent.KEYCODE_2);
                return;
            }
            case R.id.three: {
                playTone(ToneGenerator.TONE_DTMF_3);
                keyPressed(KeyEvent.KEYCODE_3);
                return;
            }
            case R.id.four: {
                playTone(ToneGenerator.TONE_DTMF_4);
                keyPressed(KeyEvent.KEYCODE_4);
                return;
            }
            case R.id.five: {
                playTone(ToneGenerator.TONE_DTMF_5);
                keyPressed(KeyEvent.KEYCODE_5);
                return;
            }
            case R.id.six: {
                playTone(ToneGenerator.TONE_DTMF_6);
                keyPressed(KeyEvent.KEYCODE_6);
                return;
            }
            case R.id.seven: {
                playTone(ToneGenerator.TONE_DTMF_7);
                keyPressed(KeyEvent.KEYCODE_7);
                return;
            }
            case R.id.eight: {
                playTone(ToneGenerator.TONE_DTMF_8);
                keyPressed(KeyEvent.KEYCODE_8);
                return;
            }
            case R.id.nine: {
                playTone(ToneGenerator.TONE_DTMF_9);
                keyPressed(KeyEvent.KEYCODE_9);
                return;
            }
            case R.id.zero: {
                playTone(ToneGenerator.TONE_DTMF_0);
                keyPressed(KeyEvent.KEYCODE_0);
                return;
            }
            case R.id.pound: {
                playTone(ToneGenerator.TONE_DTMF_P);
                keyPressed(KeyEvent.KEYCODE_POUND);
                return;
            }
            case R.id.star: {
                playTone(ToneGenerator.TONE_DTMF_S);
                keyPressed(KeyEvent.KEYCODE_STAR);
                return;
            }
        }
    }

    /**
     * called for long touch events
     */
    @Override
    public boolean onLongClick(View view) {
        int id = view.getId();
        switch (id) {
            case R.id.deleteButton: {
                mDigits.getText().clear();
                return true;
            }
            case R.id.zero: {
                removePreviousDigitIfPossible();
                keyPressed(KeyEvent.KEYCODE_PLUS);
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onResume() {
        super.onResume();

        /// M: Disable Status bar in advance to avoid timing issue
        // (pull status bar before this code take effect). see ALPS02842919 @{
        // Disable the status bar and set the poke lock timeout to medium.
        // There is no need to do anything with the wake lock.
        if (DBG) Log.d(LOG_TAG, "disabling status bar, set to long timeout");
        mStatusBarManager.disable(StatusBarManager.DISABLE_EXPAND);
        /// @}

        // retrieve the DTMF tone play back setting.
        mDTMFToneEnabled = Settings.System.getInt(getContentResolver(),
                Settings.System.DTMF_TONE_WHEN_DIALING, 1) == 1;

        // if the mToneGenerator creation fails, just continue without it.  It is
        // a local audio signal, and is not as important as the dtmf tone itself.
        synchronized (mToneGeneratorLock) {
            if (mToneGenerator == null) {
                try {
                    mToneGenerator = new ToneGenerator(AudioManager.STREAM_DTMF,
                            TONE_RELATIVE_VOLUME);
                } catch (RuntimeException e) {
                    Log.w(LOG_TAG, "Exception caught while creating local tone generator: " + e);
                    mToneGenerator = null;
                }
            }
        }

        /// M: has already been moved to the beginning of this function. @{
        /* Original Code:
        // Disable the status bar and set the poke lock timeout to medium.
        // There is no need to do anything with the wake lock.
        if (DBG) Log.d(LOG_TAG, "disabling status bar, set to long timeout");
        mStatusBarManager.disable(StatusBarManager.DISABLE_EXPAND);
        */
        /// @}

        updateDialAndDeleteButtonStateEnabledAttr();
    }

    @Override
    public void onPause() {
        // Reenable the status bar and set the poke lock timeout to default.
        // There is no need to do anything with the wake lock.
        if (DBG) Log.d(LOG_TAG, "reenabling status bar and closing the dialer");
        mStatusBarManager.disable(StatusBarManager.DISABLE_NONE);

        super.onPause();

        synchronized (mToneGeneratorLock) {
            if (mToneGenerator != null) {
                mToneGenerator.release();
                mToneGenerator = null;
            }
        }
    }

    /**
     * place the call, but check to make sure it is a viable number.
     */
    private void placeCall() {
        mLastNumber = mDigits.getText().toString();
        // Convert into emergency number if necessary
        // This is required in some regions (e.g. Taiwan).
        if (PhoneNumberUtils.isConvertToEmergencyNumberEnabled()) {
            mLastNumber = PhoneNumberUtils.convertToEmergencyNumber(mLastNumber);
        }
        /*-prize-add by lihuangyuan,for check emergencynumber-2018-05-22-start*/
        boolean isRefused = false;
        for(int i=0;i<mLastNumber.length();i++)
        {
            if(mLastNumber.charAt(i) == '*' ||  mLastNumber.charAt(i) == '#')
            {
                isRefused = true;
                break;
            }
        }
        /*-prize-add by lihuangyuan,for check emergencynumber-2018-05-22-end*/
        if (PhoneNumberUtils.isLocalEmergencyNumber(this, mLastNumber) && !isRefused) {
            if (DBG) Log.d(LOG_TAG, "placing call to " + mLastNumber);

            // place the call if it is a valid number
            if (mLastNumber == null || !TextUtils.isGraphic(mLastNumber)) {
                // There is no number entered.
                playTone(ToneGenerator.TONE_PROP_NACK);
                return;
            }
            Intent intent = new Intent(Intent.ACTION_CALL_EMERGENCY);
            intent.setData(Uri.fromParts(PhoneAccount.SCHEME_TEL, mLastNumber, null));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } else {
            if (DBG) Log.d(LOG_TAG, "rejecting bad requested number " + mLastNumber);

            showDialog(BAD_EMERGENCY_NUMBER_DIALOG);
        }
        mDigits.getText().delete(0, mDigits.getText().length());
    }

    /**
     * Plays the specified tone for TONE_LENGTH_MS milliseconds.
     *
     * The tone is played locally, using the audio stream for phone calls.
     * Tones are played only if the "Audible touch tones" user preference
     * is checked, and are NOT played if the device is in silent mode.
     *
     * @param tone a tone code from {@link ToneGenerator}
     */
    void playTone(int tone) {
        // if local tone playback is disabled, just return.
        if (!mDTMFToneEnabled) {
            return;
        }

        // Also do nothing if the phone is in silent mode.
        // We need to re-check the ringer mode for *every* playTone()
        // call, rather than keeping a local flag that's updated in
        // onResume(), since it's possible to toggle silent mode without
        // leaving the current activity (via the ENDCALL-longpress menu.)
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        int ringerMode = audioManager.getRingerMode();
        if ((ringerMode == AudioManager.RINGER_MODE_SILENT)
            || (ringerMode == AudioManager.RINGER_MODE_VIBRATE)) {
            return;
        }

        synchronized (mToneGeneratorLock) {
            if (mToneGenerator == null) {
                Log.w(LOG_TAG, "playTone: mToneGenerator == null, tone: " + tone);
                return;
            }

            // Start the new tone (will stop any playing tone)
            mToneGenerator.startTone(tone, TONE_LENGTH_MS);
        }
    }

    private CharSequence createErrorMessage(String number) {
        if (!TextUtils.isEmpty(number)) {
            String errorString = getString(R.string.dial_emergency_error, number);
            int startingPosition = errorString.indexOf(number);
            int endingPosition = startingPosition + number.length();
            Spannable result = new SpannableString(errorString);
            PhoneNumberUtils.addTtsSpan(result, startingPosition, endingPosition);
            return result;
        } else {
            return getText(R.string.dial_emergency_empty_error).toString();
        }
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        AlertDialog dialog = null;
        if (id == BAD_EMERGENCY_NUMBER_DIALOG) {
            // construct dialog
            dialog = new AlertDialog.Builder(this)
                    .setTitle(getText(R.string.emergency_enable_radio_dialog_title))
                    .setMessage(createErrorMessage(mLastNumber))
                    .setPositiveButton(R.string.ok, null)
                    .setCancelable(true).create();

            // blur stuff behind the dialog
            dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
            dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        }
        return dialog;
    }

    @Override
    protected void onPrepareDialog(int id, Dialog dialog) {
        super.onPrepareDialog(id, dialog);
        if (id == BAD_EMERGENCY_NUMBER_DIALOG) {
            AlertDialog alert = (AlertDialog) dialog;
            alert.setMessage(createErrorMessage(mLastNumber));
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Update the enabledness of the "Dial" and "Backspace" buttons if applicable.
     */
    private void updateDialAndDeleteButtonStateEnabledAttr() {
        final boolean notEmpty = mDigits.length() != 0;

        mDelete.setEnabled(notEmpty);
    }

    /**
     * Remove the digit just before the current position. Used by various long pressed callbacks
     * to remove the digit that was populated as a result of the short click.
     */
    private void removePreviousDigitIfPossible() {
        final int currentPosition = mDigits.getSelectionStart();
        if (currentPosition > 0) {
            mDigits.setSelection(currentPosition);
            mDigits.getText().delete(currentPosition - 1, currentPosition);
        }
    }

    /**
     * Update the text-to-speech annotations in the edit field.
     */
    private void updateTtsSpans() {
        for (Object o : mDigits.getText().getSpans(0, mDigits.getText().length(), TtsSpan.class)) {
            mDigits.getText().removeSpan(o);
        }
        PhoneNumberUtils.ttsSpanAsPhoneNumber(mDigits.getText(), 0, mDigits.getText().length());
    }
    
    /**public-prize-emergencyDialer-hekeyi-20151222-begin*/
    private void initStatusBar() {
		Window window = getWindow();
		window.requestFeature(Window.FEATURE_NO_TITLE);
		if(VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
			window = getWindow();
			window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS 
					| WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
			window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
					//| View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
					| View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
			window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
			window.setStatusBarColor(Color.TRANSPARENT);
			//window.setNavigationBarColor(Color.TRANSPARENT);
		}
	}
    /**public-prize-emergencyDialer-hekeyi-20151222-end*/
}
