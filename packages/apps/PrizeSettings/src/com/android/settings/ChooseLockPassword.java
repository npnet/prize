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

package com.android.settings;

import com.android.internal.widget.LockPatternChecker;
import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.PasswordEntryKeyboardHelper;
import com.android.internal.widget.PasswordEntryKeyboardView;
import com.android.internal.widget.TextViewInputDisabler;
import com.android.settings.notification.RedactionInterstitial;

import android.app.ActionBar;
import android.app.Activity;
import android.app.Fragment;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.inputmethodservice.KeyboardView;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.UserHandle;
import android.text.Editable;
import android.text.InputType;
import android.text.Selection;
import android.text.Spannable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import java.util.Timer;
import java.util.TimerTask;

import com.android.internal.logging.MetricsProto.MetricsEvent;
import com.android.internal.widget.LockPatternUtils.RequestThrottledException;
// PRIZE-add by liyu for fingerprint factory test-20160705-start
import android.provider.Settings;
import android.util.Base64;
// PRIZE-add by liyu for fingerprint factory test-20160705-end

public class ChooseLockPassword extends SettingsActivity {
    public static final String PASSWORD_MIN_KEY = "lockscreen.password_min";
    public static final String PASSWORD_MAX_KEY = "lockscreen.password_max";
    public static final String PASSWORD_MIN_LETTERS_KEY = "lockscreen.password_min_letters";
    public static final String PASSWORD_MIN_LOWERCASE_KEY = "lockscreen.password_min_lowercase";
    public static final String PASSWORD_MIN_UPPERCASE_KEY = "lockscreen.password_min_uppercase";
    public static final String PASSWORD_MIN_NUMERIC_KEY = "lockscreen.password_min_numeric";
    public static final String PASSWORD_MIN_SYMBOLS_KEY = "lockscreen.password_min_symbols";
    public static final String PASSWORD_MIN_NONLETTER_KEY = "lockscreen.password_min_nonletter";

    private static final String TAG = "ChooseLockPassword";

    @Override
    public Intent getIntent() {
        Intent modIntent = new Intent(super.getIntent());
        modIntent.putExtra(EXTRA_SHOW_FRAGMENT, getFragmentClass().getName());
        return modIntent;
    }

    public static Intent createIntent(Context context, int quality,
            int minLength, final int maxLength, boolean requirePasswordToDecrypt,
            boolean confirmCredentials) {
        Intent intent = new Intent().setClass(context, ChooseLockPassword.class);
        intent.putExtra(LockPatternUtils.PASSWORD_TYPE_KEY, quality);
        intent.putExtra(PASSWORD_MIN_KEY, minLength);
        intent.putExtra(PASSWORD_MAX_KEY, maxLength);
        intent.putExtra(ChooseLockGeneric.CONFIRM_CREDENTIALS, confirmCredentials);
        intent.putExtra(EncryptionInterstitial.EXTRA_REQUIRE_PASSWORD, requirePasswordToDecrypt);
        return intent;
    }

    public static Intent createIntent(Context context, int quality,
            int minLength, final int maxLength, boolean requirePasswordToDecrypt,
            boolean confirmCredentials, int userId) {
        Intent intent = createIntent(context, quality, minLength, maxLength,
                requirePasswordToDecrypt, confirmCredentials);
        intent.putExtra(Intent.EXTRA_USER_ID, userId);
        return intent;
    }

    public static Intent createIntent(Context context, int quality,
            int minLength, final int maxLength, boolean requirePasswordToDecrypt, String password) {
        Intent intent = createIntent(context, quality, minLength, maxLength,
                requirePasswordToDecrypt, false);
        intent.putExtra(ChooseLockSettingsHelper.EXTRA_KEY_PASSWORD, password);
        return intent;
    }

    public static Intent createIntent(Context context, int quality, int minLength,
            int maxLength, boolean requirePasswordToDecrypt, String password, int userId) {
        Intent intent = createIntent(context, quality, minLength, maxLength,
                requirePasswordToDecrypt, password);
        intent.putExtra(Intent.EXTRA_USER_ID, userId);
        return intent;
    }

    public static Intent createIntent(Context context, int quality,
            int minLength, final int maxLength, boolean requirePasswordToDecrypt, long challenge) {
        Intent intent = createIntent(context, quality, minLength, maxLength,
                requirePasswordToDecrypt, false);
        intent.putExtra(ChooseLockSettingsHelper.EXTRA_KEY_HAS_CHALLENGE, true);
        intent.putExtra(ChooseLockSettingsHelper.EXTRA_KEY_CHALLENGE, challenge);
        return intent;
    }

    public static Intent createIntent(Context context, int quality, int minLength,
            int maxLength, boolean requirePasswordToDecrypt, long challenge, int userId) {
        Intent intent = createIntent(context, quality, minLength, maxLength,
                requirePasswordToDecrypt, challenge);
        intent.putExtra(Intent.EXTRA_USER_ID, userId);
        return intent;
    }

    @Override
    protected boolean isValidFragment(String fragmentName) {
        if (ChooseLockPasswordFragment.class.getName().equals(fragmentName)) return true;
        return false;
    }

    /* package */ Class<? extends Fragment> getFragmentClass() {
        return ChooseLockPasswordFragment.class;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // TODO: Fix on phones
        // Disable IME on our window since we provide our own keyboard
        //getWindow().setFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
                //WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        super.onCreate(savedInstanceState);
        //prize start pyx Password actionbar
        /*CharSequence msg = getText(R.string.lockpassword_choose_your_password_header);
        setTitle(msg);

        ActionBar mActionBar = getActionBar();
        mActionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM);
        mActionBar.setCustomView(R.layout.prize_pattern_actionbar);
        mActionBar.setDisplayShowTitleEnabled(false);
        mActionBar.setDisplayShowHomeEnabled(false);
        ImageView ig=(ImageView)mActionBar.getCustomView().findViewById(R.id.prize_pattern_back);
        ig.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                finish();
            }
        });*/
        //prize end pyx Password actionbar

        /*PRIZE-Add-M_Fingerprint-wangzhong-2016_6_28-start*/
        setTitle(getText(R.string.prize_choose_lock_password_title));
        getActionBar().setDisplayHomeAsUpEnabled(true);
        /*PRIZE-Add-M_Fingerprint-wangzhong-2016_6_28-end*/
    }

    /*PRIZE-Add-M_Fingerprint-wangzhong-2016_6_28-start*/
    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    /*PRIZE-Add-M_Fingerprint-wangzhong-2016_6_28-end*/

    public static class ChooseLockPasswordFragment extends InstrumentedFragment
            implements OnClickListener, OnEditorActionListener,  TextWatcher,
            SaveAndFinishWorker.Listener {
        private static final String KEY_FIRST_PIN = "first_pin";
        private static final String KEY_UI_STAGE = "ui_stage";
        private static final String KEY_CURRENT_PASSWORD = "current_password";
        private static final String FRAGMENT_TAG_SAVE_AND_FINISH = "save_and_finish_worker";

        private String mCurrentPassword;
        private String mChosenPassword;
        private boolean mHasChallenge;
        private long mChallenge;
        private TextView mPasswordEntry;
        private TextViewInputDisabler mPasswordEntryInputDisabler;
        private int mPasswordMinLength = LockPatternUtils.MIN_LOCK_PASSWORD_SIZE;
        private int mPasswordMaxLength = 16;
        private int mPasswordMinLetters = 0;
        private int mPasswordMinUpperCase = 0;
        private int mPasswordMinLowerCase = 0;
        private int mPasswordMinSymbols = 0;
        private int mPasswordMinNumeric = 0;
        private int mPasswordMinNonLetter = 0;
        private LockPatternUtils mLockPatternUtils;
        private SaveAndFinishWorker mSaveAndFinishWorker;
        private int mRequestedQuality = DevicePolicyManager.PASSWORD_QUALITY_NUMERIC;
        private ChooseLockSettingsHelper mChooseLockSettingsHelper;
        private Stage mUiStage = Stage.Introduction;

        private TextView mHeaderText;
        private String mFirstPin;
        private KeyboardView mKeyboardView;
        private PasswordEntryKeyboardHelper mKeyboardHelper;
        private boolean mIsAlphaMode;
        private Button mCancelButton;
        private Button mNextButton;
        private static final int CONFIRM_EXISTING_REQUEST = 58;
        static final int RESULT_FINISHED = RESULT_FIRST_USER;
        private static final long ERROR_MESSAGE_TIMEOUT = 3000;
        private static final int MSG_SHOW_ERROR = 1;

        private int mUserId;
        private boolean mHideDrawer = false;
        
        private Timer mTimer = new Timer();

        private Handler mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == MSG_SHOW_ERROR) {
                    updateStage((Stage) msg.obj);
                }
                /*PRIZE-Add-M_Fingerprint-wangzhong-2016_6_28-start*/
                else if (msg.what == MSG_NOTICE_COMPLETE) {
                    if (null != mPasswordEntry && null != msg.obj) {
                        mPasswordEntry.setText((String) msg.obj);
                    }
                } else if (msg.what == MSG_NOTICE_ERROE) {
                    if (null != mPinLockView) {
                        mPinLockView.resetPinLockView();//The second input errors, restore the mPinLockView.
                    }
                }
                /*PRIZE-Add-M_Fingerprint-wangzhong-2016_6_28-end*/
            }
        };

        /**
         * Keep track internally of where the user is in choosing a pattern.
         */
        protected enum Stage {

            Introduction(R.string.lockpassword_choose_your_password_header,
                    R.string.lockpassword_choose_your_pin_header,
                    R.string.lockpassword_continue_label),

            NeedToConfirm(R.string.lockpassword_confirm_your_password_header,
                    R.string.lockpassword_confirm_your_pin_header,
                    /*R.string.lockpassword_ok_label*/R.string.finish_button_label),//prize pyx add

            ConfirmWrong(R.string.lockpassword_confirm_passwords_dont_match,
                    R.string.lockpassword_confirm_pins_dont_match,
                    R.string.lockpassword_continue_label);

            Stage(int hintInAlpha, int hintInNumeric, int nextButtonText) {
                this.alphaHint = hintInAlpha;
                this.numericHint = hintInNumeric;
                this.buttonText = nextButtonText;
            }

            public final int alphaHint;
            public final int numericHint;
            public final int buttonText;
        }

        // required constructor for fragments
        public ChooseLockPasswordFragment() {

        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            mLockPatternUtils = new LockPatternUtils(getActivity());
            Intent intent = getActivity().getIntent();
            if (!(getActivity() instanceof ChooseLockPassword)) {
                throw new SecurityException("Fragment contained in wrong activity");
            }
            // Only take this argument into account if it belongs to the current profile.
            mUserId = Utils.getUserIdFromBundle(getActivity(), intent.getExtras());
            mRequestedQuality = Math.max(intent.getIntExtra(LockPatternUtils.PASSWORD_TYPE_KEY,
                    mRequestedQuality), mLockPatternUtils.getRequestedPasswordQuality(
                    mUserId));
            mPasswordMinLength = Math.max(Math.max(
                    LockPatternUtils.MIN_LOCK_PASSWORD_SIZE,
                    intent.getIntExtra(PASSWORD_MIN_KEY, mPasswordMinLength)),
                    mLockPatternUtils.getRequestedMinimumPasswordLength(mUserId));
            mPasswordMaxLength = intent.getIntExtra(PASSWORD_MAX_KEY, mPasswordMaxLength);
            mPasswordMinLetters = Math.max(intent.getIntExtra(PASSWORD_MIN_LETTERS_KEY,
                    mPasswordMinLetters), mLockPatternUtils.getRequestedPasswordMinimumLetters(
                    mUserId));
            mPasswordMinUpperCase = Math.max(intent.getIntExtra(PASSWORD_MIN_UPPERCASE_KEY,
                    mPasswordMinUpperCase), mLockPatternUtils.getRequestedPasswordMinimumUpperCase(
                    mUserId));
            mPasswordMinLowerCase = Math.max(intent.getIntExtra(PASSWORD_MIN_LOWERCASE_KEY,
                    mPasswordMinLowerCase), mLockPatternUtils.getRequestedPasswordMinimumLowerCase(
                    mUserId));
            mPasswordMinNumeric = Math.max(intent.getIntExtra(PASSWORD_MIN_NUMERIC_KEY,
                    mPasswordMinNumeric), mLockPatternUtils.getRequestedPasswordMinimumNumeric(
                    mUserId));
            mPasswordMinSymbols = Math.max(intent.getIntExtra(PASSWORD_MIN_SYMBOLS_KEY,
                    mPasswordMinSymbols), mLockPatternUtils.getRequestedPasswordMinimumSymbols(
                    mUserId));
            mPasswordMinNonLetter = Math.max(intent.getIntExtra(PASSWORD_MIN_NONLETTER_KEY,
                    mPasswordMinNonLetter), mLockPatternUtils.getRequestedPasswordMinimumNonLetter(
                    mUserId));

            mChooseLockSettingsHelper = new ChooseLockSettingsHelper(getActivity());
            mHideDrawer = getActivity().getIntent().getBooleanExtra(EXTRA_HIDE_DRAWER, false);

            if (intent.getBooleanExtra(
                    ChooseLockSettingsHelper.EXTRA_KEY_FOR_CHANGE_CRED_REQUIRED_FOR_BOOT, false)) {
                SaveAndFinishWorker w = new SaveAndFinishWorker();
                final boolean required = getActivity().getIntent().getBooleanExtra(
                        EncryptionInterstitial.EXTRA_REQUIRE_PASSWORD, true);
                String current = intent.getStringExtra(
                        ChooseLockSettingsHelper.EXTRA_KEY_PASSWORD);
                w.setBlocking(true);
                w.setListener(this);
                w.start(mChooseLockSettingsHelper.utils(), required,
                        false, 0, current, current, mRequestedQuality, mUserId);
            }
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            //prize start pyx 2016-07-04 for UI password
            mIsAlphaMode = DevicePolicyManager.PASSWORD_QUALITY_ALPHABETIC == mRequestedQuality
                    || DevicePolicyManager.PASSWORD_QUALITY_ALPHANUMERIC == mRequestedQuality
                    || DevicePolicyManager.PASSWORD_QUALITY_COMPLEX == mRequestedQuality;
            if(mIsAlphaMode){
                return inflater.inflate(R.layout.choose_lock_complex_password, container, false);
            }
            //prize end pyx 2016-07-04 for UI password

            /*PRIZE-Change-M_PINLock-wangzhong-2016_6_28-start*/
            /*return inflater.inflate(R.layout.choose_lock_password, container, false);*/
            View layout = inflater.inflate(R.layout.prize_choose_lock_simple_password, container, false);
            mPinLockView = (com.android.settings.pinlockview.PinLockView) layout.findViewById(R.id.pin_lock_view);
            mIndicatorDots = (com.android.settings.pinlockview.IndicatorDots) layout.findViewById(R.id.indicator_dots);
            mPinLockView.attachIndicatorDots(mIndicatorDots);
            mPinLockView.setPinLockListener(mPinLockListener);
            return layout;
            /*PRIZE-Change-M_PINLock-wangzhong-2016_6_28-end*/
        }

        /*PRIZE-Add-M_PINLock-wangzhong-2016_6_28-start*/
        private static final int MSG_NOTICE_COMPLETE = 189;
        private static final int MSG_NOTICE_ERROE = 190;
        private static final int NOTICE_COMPLETE_MESSAGE_TIMEOUT = 300;
        private com.android.settings.pinlockview.PinLockView mPinLockView;
        private com.android.settings.pinlockview.IndicatorDots mIndicatorDots;
        private com.android.settings.pinlockview.PinLockListener mPinLockListener = 
        		new com.android.settings.pinlockview.PinLockListener() {
            @Override
            public void onComplete(String pin) {
                Log.d("john", "Pin complete:  mUiStage : " + mUiStage.toString());
                if (mUiStage == Stage.Introduction) {
                    updatePinLockViewDelayed(MSG_NOTICE_COMPLETE, pin);
                } else if (mUiStage == Stage.NeedToConfirm || mUiStage == Stage.ConfirmWrong) {
                    if (mFirstPin.equals(pin)) {
                        updatePinLockViewDelayed(MSG_NOTICE_COMPLETE, pin);
                    } else {
                        if (null != mPasswordEntry && null != mPinLockView) {
                            mPinLockView.updatePinLockViewError();
                            mPasswordEntry.setText(pin);
                        }
                    }
                }
            }

            @Override
            public void onEmpty() {
                //Log.d("john", "Pin empty");
            }

            @Override
            public void onPinChange(int pinLength, String intermediatePin) {
                //Log.d("john", "Pin changed, new length " + pinLength + " with intermediate pin " + intermediatePin);
            }
        };

        private void updatePinLockViewDelayed(int what, String pin) {
            Message msg = mHandler.obtainMessage(what, pin);
            mHandler.removeMessages(what);
            mHandler.sendMessageDelayed(msg, NOTICE_COMPLETE_MESSAGE_TIMEOUT);
        }
        /*PRIZE-Add-M_PINLock-wangzhong-2016_6_28-end*/

        @Override
        public void onViewCreated(View view, Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);

            mCancelButton = (Button) view.findViewById(R.id.cancel_button);
            mCancelButton.setOnClickListener(this);
            mNextButton = (Button) view.findViewById(R.id.next_button);
            mNextButton.setOnClickListener(this);

            mIsAlphaMode = DevicePolicyManager.PASSWORD_QUALITY_ALPHABETIC == mRequestedQuality
                    || DevicePolicyManager.PASSWORD_QUALITY_ALPHANUMERIC == mRequestedQuality
                    || DevicePolicyManager.PASSWORD_QUALITY_COMPLEX == mRequestedQuality;
            mKeyboardView = (PasswordEntryKeyboardView) view.findViewById(R.id.keyboard);
            mPasswordEntry = (TextView) view.findViewById(R.id.password_entry);
            mPasswordEntry.setOnEditorActionListener(this);
            mPasswordEntry.addTextChangedListener(this);
            mPasswordEntryInputDisabler = new TextViewInputDisabler(mPasswordEntry);

            final Activity activity = getActivity();
            mKeyboardHelper = new PasswordEntryKeyboardHelper(activity,
                    mKeyboardView, mPasswordEntry);
            mKeyboardHelper.setKeyboardMode(mIsAlphaMode ?
                    PasswordEntryKeyboardHelper.KEYBOARD_MODE_ALPHA
                    : PasswordEntryKeyboardHelper.KEYBOARD_MODE_NUMERIC);

            mHeaderText = (TextView) view.findViewById(R.id.headerText);
            mKeyboardView.requestFocus();

            /*PRIZE-Change-M_Fingerprint-wangzhong-2016_6_28-start*/
            /*int currentType = mPasswordEntry.getInputType();
            mPasswordEntry.setInputType(mIsAlphaMode ? currentType
                    : (InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD));*/
            if (mIsAlphaMode) {
                int currentType = mPasswordEntry.getInputType();
                mPasswordEntry.setInputType(mIsAlphaMode ? currentType
                        : (InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD));
            }
            /*PRIZE-Change-M_Fingerprint-wangzhong-2016_6_28-end*/

            Intent intent = getActivity().getIntent();
            final boolean confirmCredentials = intent.getBooleanExtra(
                    ChooseLockGeneric.CONFIRM_CREDENTIALS, true);
            mCurrentPassword = intent.getStringExtra(ChooseLockSettingsHelper.EXTRA_KEY_PASSWORD);
            mHasChallenge = intent.getBooleanExtra(
                    ChooseLockSettingsHelper.EXTRA_KEY_HAS_CHALLENGE, false);
            mChallenge = intent.getLongExtra(ChooseLockSettingsHelper.EXTRA_KEY_CHALLENGE, 0);
            if (savedInstanceState == null) {
                updateStage(Stage.Introduction);
                if (confirmCredentials) {
                    mChooseLockSettingsHelper.launchConfirmationActivity(CONFIRM_EXISTING_REQUEST,
                            getString(R.string.unlock_set_unlock_launch_picker_title), true,
                            mUserId);
                }
            } else {
                // restore from previous state
                mFirstPin = savedInstanceState.getString(KEY_FIRST_PIN);
                final String state = savedInstanceState.getString(KEY_UI_STAGE);
                if (state != null) {
                    mUiStage = Stage.valueOf(state);
                    updateStage(mUiStage);
                }

                if (mCurrentPassword == null) {
                    mCurrentPassword = savedInstanceState.getString(KEY_CURRENT_PASSWORD);
                }

                // Re-attach to the exiting worker if there is one.
                mSaveAndFinishWorker = (SaveAndFinishWorker) getFragmentManager().findFragmentByTag(
                        FRAGMENT_TAG_SAVE_AND_FINISH);
            }
            
            /*PRIZE-Delete-M_Fingerprint-wangzhong-2016_6_28-start*/
            /*if (activity instanceof SettingsActivity) {
                final SettingsActivity sa = (SettingsActivity) activity;
                int id = mIsAlphaMode ? R.string.lockpassword_choose_your_password_header
                        : R.string.lockpassword_choose_your_pin_header;
                CharSequence title = getText(id);
                sa.setTitle(title);
            }*/
            /*PRIZE-Delete-M_Fingerprint-wangzhong-2016_6_28-end*/
            /*PRIZE-Add-M_PINLock-wangzhong-2016_6_28-start*/
            if (!mIsAlphaMode) {
                mTimer.schedule(new TimerTask(){
    				public void run(){
    					InputMethodManager inputManager = (InputMethodManager) 
    	                		getActivity().getApplicationContext().getSystemService(Context.INPUT_METHOD_SERVICE); 
    					inputManager.hideSoftInputFromWindow(mPasswordEntry.getWindowToken(),0);
    				}
    			},400);
            }
        }

        @Override
        protected int getMetricsCategory() {
            return MetricsEvent.CHOOSE_LOCK_PASSWORD;
        }

        @Override
        public void onResume() {
            super.onResume();
            updateStage(mUiStage);
            if (mSaveAndFinishWorker != null) {
                mSaveAndFinishWorker.setListener(this);
            } else {
                mKeyboardView.requestFocus();
            }
            
        }

        @Override
        public void onPause() {
            mHandler.removeMessages(MSG_SHOW_ERROR);
            if (mSaveAndFinishWorker != null) {
                mSaveAndFinishWorker.setListener(null);
            }

            super.onPause();
        }

        @Override
        public void onSaveInstanceState(Bundle outState) {
            super.onSaveInstanceState(outState);
            outState.putString(KEY_UI_STAGE, mUiStage.name());
            outState.putString(KEY_FIRST_PIN, mFirstPin);
            outState.putString(KEY_CURRENT_PASSWORD, mCurrentPassword);
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode,
                Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            switch (requestCode) {
                case CONFIRM_EXISTING_REQUEST:
                    if (resultCode != Activity.RESULT_OK) {
                        getActivity().setResult(RESULT_FINISHED);
                        getActivity().finish();
                    } else {
                        mCurrentPassword = data.getStringExtra(
                                ChooseLockSettingsHelper.EXTRA_KEY_PASSWORD);
                    }
                    break;
            }
        }

        protected Intent getRedactionInterstitialIntent(Context context) {
            return RedactionInterstitial.createStartIntent(context, mUserId);
        }

        protected void updateStage(Stage stage) {
            final Stage previousStage = mUiStage;
            mUiStage = stage;
            updateUi();

            // If the stage changed, announce the header for accessibility. This
            // is a no-op when accessibility is disabled.
            if (previousStage != stage) {
                mHeaderText.announceForAccessibility(mHeaderText.getText());
            }
        }

        /**
         * Validates PIN and returns a message to display if PIN fails test.
         * @param password the raw password the user typed in
         * @return error message to show to user or null if password is OK
         */
        private String validatePassword(String password) {
            if (password.length() < mPasswordMinLength) {
                return getString(mIsAlphaMode ?
                        R.string.lockpassword_password_too_short
                        : R.string.lockpassword_pin_too_short, mPasswordMinLength);
            }
            if (password.length() > mPasswordMaxLength) {
                return getString(mIsAlphaMode ?
                        R.string.lockpassword_password_too_long
                        : R.string.lockpassword_pin_too_long, mPasswordMaxLength + 1);
            }
            int letters = 0;
            int numbers = 0;
            int lowercase = 0;
            int symbols = 0;
            int uppercase = 0;
            int nonletter = 0;
            for (int i = 0; i < password.length(); i++) {
                char c = password.charAt(i);
                // allow non control Latin-1 characters only
                if (c < 32 || c > 127) {
                    return getString(R.string.lockpassword_illegal_character);
                }
                if (c >= '0' && c <= '9') {
                    numbers++;
                    nonletter++;
                } else if (c >= 'A' && c <= 'Z') {
                    letters++;
                    uppercase++;
                } else if (c >= 'a' && c <= 'z') {
                    letters++;
                    lowercase++;
                } else {
                    symbols++;
                    nonletter++;
                }
            }
            if (DevicePolicyManager.PASSWORD_QUALITY_NUMERIC == mRequestedQuality
                    || DevicePolicyManager.PASSWORD_QUALITY_NUMERIC_COMPLEX == mRequestedQuality) {
                if (letters > 0 || symbols > 0) {
                    // This shouldn't be possible unless user finds some way to bring up
                    // soft keyboard
                    return getString(R.string.lockpassword_pin_contains_non_digits);
                }
                // Check for repeated characters or sequences (e.g. '1234', '0000', '2468')
                final int sequence = LockPatternUtils.maxLengthSequence(password);
                if (DevicePolicyManager.PASSWORD_QUALITY_NUMERIC_COMPLEX == mRequestedQuality
                        && sequence > LockPatternUtils.MAX_ALLOWED_SEQUENCE) {
                    return getString(R.string.lockpassword_pin_no_sequential_digits);
                }
            } else if (DevicePolicyManager.PASSWORD_QUALITY_COMPLEX == mRequestedQuality) {
                if (letters < mPasswordMinLetters) {
                    return String.format(getResources().getQuantityString(
                            R.plurals.lockpassword_password_requires_letters, mPasswordMinLetters),
                            mPasswordMinLetters);
                } else if (numbers < mPasswordMinNumeric) {
                    return String.format(getResources().getQuantityString(
                            R.plurals.lockpassword_password_requires_numeric, mPasswordMinNumeric),
                            mPasswordMinNumeric);
                } else if (lowercase < mPasswordMinLowerCase) {
                    return String.format(getResources().getQuantityString(
                            R.plurals.lockpassword_password_requires_lowercase, mPasswordMinLowerCase),
                            mPasswordMinLowerCase);
                } else if (uppercase < mPasswordMinUpperCase) {
                    return String.format(getResources().getQuantityString(
                            R.plurals.lockpassword_password_requires_uppercase, mPasswordMinUpperCase),
                            mPasswordMinUpperCase);
                } else if (symbols < mPasswordMinSymbols) {
                    return String.format(getResources().getQuantityString(
                            R.plurals.lockpassword_password_requires_symbols, mPasswordMinSymbols),
                            mPasswordMinSymbols);
                } else if (nonletter < mPasswordMinNonLetter) {
                    return String.format(getResources().getQuantityString(
                            R.plurals.lockpassword_password_requires_nonletter, mPasswordMinNonLetter),
                            mPasswordMinNonLetter);
                }
            } else {
                final boolean alphabetic = DevicePolicyManager.PASSWORD_QUALITY_ALPHABETIC
                        == mRequestedQuality;
                final boolean alphanumeric = DevicePolicyManager.PASSWORD_QUALITY_ALPHANUMERIC
                        == mRequestedQuality;
                if ((alphabetic || alphanumeric) && letters == 0) {
                    return getString(R.string.lockpassword_password_requires_alpha);
                }
                if (alphanumeric && numbers == 0) {
                    return getString(R.string.lockpassword_password_requires_digit);
                }
            }
            if(mLockPatternUtils.checkPasswordHistory(password, mUserId)) {
                return getString(mIsAlphaMode ? R.string.lockpassword_password_recently_used
                        : R.string.lockpassword_pin_recently_used);
            }

            return null;
        }

        public void handleNext() {
            if (mSaveAndFinishWorker != null) return;
            mChosenPassword = mPasswordEntry.getText().toString();
            if (TextUtils.isEmpty(mChosenPassword)) {
                return;
            }
            String errorMsg = null;
            if (mUiStage == Stage.Introduction) {
                errorMsg = validatePassword(mChosenPassword);
                if (errorMsg == null) {
                    mFirstPin = mChosenPassword;
                    mPasswordEntry.setText("");
                    /*PRIZE-Add-M_PINLock-wangzhong-2016_6_28-start*/
                    if (null != mPinLockView) {
                        mPinLockView.resetPinLockView();//The first pin.
                    }
                    /*PRIZE-Add-M_PINLock-wangzhong-2016_6_28-end*/
                    updateStage(Stage.NeedToConfirm);
                }
            } else if (mUiStage == Stage.NeedToConfirm) {
                if (mFirstPin.equals(mChosenPassword)) {
                    startSaveAndFinish();
                } else {
                    CharSequence tmp = mPasswordEntry.getText();
                    if (tmp != null) {
                        Selection.setSelection((Spannable) tmp, 0, tmp.length());
                    }
                    updateStage(Stage.ConfirmWrong);
                    /*PRIZE-Add-M_PINLock-wangzhong-2016_6_28-start*/
                    updatePinLockViewDelayed(MSG_NOTICE_ERROE, "");
                    /*PRIZE-Add-M_PINLock-wangzhong-2016_6_28-end*/
                }
            }
            if (errorMsg != null) {
                showError(errorMsg, mUiStage);
            }
        }

        protected void setNextEnabled(boolean enabled) {
            mNextButton.setEnabled(enabled);
        }

        protected void setNextText(int text) {
            mNextButton.setText(text);
        }

        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.next_button:
                    handleNext();
                    break;

                case R.id.cancel_button:
                    /// M: ALPS01285009 {@
                    if (mPasswordEntry != null && getActivity() != null) {
                        InputMethodManager imm = (InputMethodManager) getActivity()
                                .getSystemService(Context.INPUT_METHOD_SERVICE);
                        imm.hideSoftInputFromWindow(mPasswordEntry.getWindowToken(), 0);
                    }
                    /// @}
                    getActivity().finish();
                    break;
            }
        }

        private void showError(String msg, final Stage next) {
            mHeaderText.setText(msg);
            mHeaderText.announceForAccessibility(mHeaderText.getText());
            Message mesg = mHandler.obtainMessage(MSG_SHOW_ERROR, next);
            mHandler.removeMessages(MSG_SHOW_ERROR);
            mHandler.sendMessageDelayed(mesg, ERROR_MESSAGE_TIMEOUT);
        }

        public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
            // Check if this was the result of hitting the enter or "done" key
            if (actionId == EditorInfo.IME_NULL
                    || actionId == EditorInfo.IME_ACTION_DONE
                    || actionId == EditorInfo.IME_ACTION_NEXT) {
                handleNext();
                return true;
            }
            return false;
        }

        /**
         * Update the hint based on current Stage and length of password entry
         */
        private void updateUi() {
            final boolean canInput = mSaveAndFinishWorker == null;
            String password = mPasswordEntry.getText().toString();
            final int length = password.length();
            if (mUiStage == Stage.Introduction) {
                if (length < mPasswordMinLength) {
                    String msg = getString(mIsAlphaMode ? R.string.lockpassword_password_too_short
                            : R.string.lockpassword_pin_too_short, mPasswordMinLength);
                    mHeaderText.setText(msg);
                    setNextEnabled(false);
                } else {
                    String error = validatePassword(password);
                    if (error != null) {
                        mHeaderText.setText(error);
                        setNextEnabled(false);
                    } else {
                        mHeaderText.setText(R.string.lockpassword_press_continue);
                        setNextEnabled(true);
                    }
                }
            } else {
                mHeaderText.setText(mIsAlphaMode ? mUiStage.alphaHint : mUiStage.numericHint);
                setNextEnabled(length > 0);
            }
            setNextText(mUiStage.buttonText);
            /*prize--PIN limit for 4 bit--chenlong--20150729--start*/
            //if(!android.os.SystemProperties.get("ro.prize_finger_print").equals("")){
            	if(mRequestedQuality == DevicePolicyManager.PASSWORD_QUALITY_NUMERIC){
            		if (length == 4) {
            			handleNext();
            		}
            	}
            //}
            /*prize--PIN limit for 4 bit--chenlong--20150729--end*/	
        }

        public void afterTextChanged(Editable s) {
            // Changing the text while error displayed resets to NeedToConfirm state
            if (mUiStage == Stage.ConfirmWrong) {
                mUiStage = Stage.NeedToConfirm;
            }
            updateUi();
        }

        public void beforeTextChanged(CharSequence s, int start, int count, int after) {

        }

        public void onTextChanged(CharSequence s, int start, int before, int count) {

        }

        private void startSaveAndFinish() {
            if (mSaveAndFinishWorker != null) {
                Log.w(TAG, "startSaveAndFinish with an existing SaveAndFinishWorker.");
                return;
            }

            mPasswordEntryInputDisabler.setInputEnabled(false);
            setNextEnabled(false);

            mSaveAndFinishWorker = new SaveAndFinishWorker();
            mSaveAndFinishWorker.setListener(this);

            getFragmentManager().beginTransaction().add(mSaveAndFinishWorker,
                    FRAGMENT_TAG_SAVE_AND_FINISH).commit();
            getFragmentManager().executePendingTransactions();

            final boolean required = getActivity().getIntent().getBooleanExtra(
                    EncryptionInterstitial.EXTRA_REQUIRE_PASSWORD, true);
            mSaveAndFinishWorker.start(mLockPatternUtils, required, mHasChallenge, mChallenge,
                    mChosenPassword, mCurrentPassword, mRequestedQuality, mUserId);
        }

        @Override
        public void onChosenLockSaveFinished(boolean wasSecureBefore, Intent resultData) {
            getActivity().setResult(RESULT_FINISHED, resultData);

            if (!wasSecureBefore) {
                Intent intent = getRedactionInterstitialIntent(getActivity());
                if (intent != null) {
                    intent.putExtra(EXTRA_HIDE_DRAWER, mHideDrawer);
                    startActivity(intent);
                }
            }
            getActivity().finish();
        }
    }

    private static class SaveAndFinishWorker extends SaveChosenLockWorkerBase {

        private String mChosenPassword;
        private String mCurrentPassword;
        private int mRequestedQuality;

        public void start(LockPatternUtils utils, boolean required,
                boolean hasChallenge, long challenge,
                String chosenPassword, String currentPassword, int requestedQuality, int userId) {
            prepare(utils, required, hasChallenge, challenge, userId);

            mChosenPassword = chosenPassword;
            mCurrentPassword = currentPassword;
            mRequestedQuality = requestedQuality;
            mUserId = userId;

            start();
        }

        @Override
        protected Intent saveAndVerifyInBackground() {
            Intent result = null;
            mUtils.saveLockPassword(mChosenPassword, mCurrentPassword, mRequestedQuality,
                    mUserId);

            if (mHasChallenge) {
                byte[] token;
                try {
                    token = mUtils.verifyPassword(mChosenPassword, mChallenge, mUserId);
                } catch (RequestThrottledException e) {
                    token = null;
                }

                if (token == null) {
                    Log.e(TAG, "critical: no token returned for known good password.");
                }

                result = new Intent();
                result.putExtra(ChooseLockSettingsHelper.EXTRA_KEY_CHALLENGE_TOKEN, token);
				
				/*PRIZE-add by liyu-for fingerprint factory test-20160705-start*/
				Settings.System.putString(getActivity().getContentResolver(),
								Settings.System.PRIZE_FINGERPRINT_TOKEN,new String(Base64.encode(token, Base64.DEFAULT)));
				/*PRIZE-add by liyu-for fingerprint factory test-20160705-end*/
				
            }

            return result;
        }
    }
}
