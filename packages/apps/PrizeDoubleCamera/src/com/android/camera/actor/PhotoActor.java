/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein is
 * confidential and proprietary to MediaTek Inc. and/or its licensors. Without
 * the prior written permission of MediaTek inc. and/or its licensors, any
 * reproduction, modification, use or disclosure of MediaTek Software, and
 * information contained herein, in whole or in part, shall be strictly
 * prohibited.
 * 
 * MediaTek Inc. (C) 2014. All rights reserved.
 * 
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER
 * ON AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL
 * WARRANTIES, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR
 * NONINFRINGEMENT. NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH
 * RESPECT TO THE SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY,
 * INCORPORATED IN, OR SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES
 * TO LOOK ONLY TO SUCH THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO.
 * RECEIVER EXPRESSLY ACKNOWLEDGES THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO
 * OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES CONTAINED IN MEDIATEK
 * SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK SOFTWARE
 * RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S
 * ENTIRE AND CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE
 * RELEASED HEREUNDER WILL BE, AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE
 * MEDIATEK SOFTWARE AT ISSUE, OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE
 * CHARGE PAID BY RECEIVER TO MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek
 * Software") have been modified by MediaTek Inc. All revisions are subject to
 * any receiver's applicable license agreements with MediaTek Inc.
 */
package com.android.camera.actor;

import com.android.camera.CameraActivity;
import com.android.camera.CameraActivity.OnLongPressListener;
import com.android.camera.CameraActivity.OnSingleTapUpListener;
import com.android.camera.CameraErrorCallback;
import com.android.camera.FeatureSwitcher;
import com.android.camera.FocusManager;
import com.android.camera.FocusManager.Listener;
import com.android.camera.Log;
import com.android.camera.R;
import com.android.camera.SaveRequest;
import com.android.camera.Storage;
import com.android.camera.bridge.CameraDeviceCtrl.PreviewCallbackListen;
import com.android.camera.bridge.SelfTimerManager;
import com.android.camera.bridge.SelfTimerManager.SelfTimerListener;
import com.android.camera.manager.ModePicker;
import com.android.camera.ui.ShutterButton;
import com.android.camera.ui.ShutterButton.OnShutterButtonListener;

import android.app.Activity;
import android.content.Intent;
import android.hardware.Camera.ErrorCallback;
import android.hardware.Camera.Face;
import android.hardware.Camera.FaceDetectionListener;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PreviewCallback;
import android.inputmethodservice.Keyboard.Key;
import android.media.MediaActionSound;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.view.KeyEvent;
import android.view.OrientationEventListener;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;

import com.mediatek.camera.ICameraMode;
import com.mediatek.camera.ICameraMode.CameraModeType;
import com.mediatek.camera.ICameraMode.ModeState;
import com.mediatek.camera.ISettingCtrl;
import com.mediatek.camera.ModuleManager;
import com.mediatek.camera.platform.ICameraAppUi;
import com.mediatek.camera.platform.ICameraAppUi.CommonUiType;
import com.mediatek.camera.platform.ICameraAppUi.ShutterButtonType;
import com.mediatek.camera.platform.ICameraAppUi.ViewState;
import com.mediatek.camera.setting.SettingConstants;
import com.mediatek.camera.setting.SettingUtils;

public class PhotoActor extends CameraActor implements FocusManager.Listener,
        OnShutterButtonListener{
    private static final String TAG = "PhotoActor";

    private static final int PARAMETER_CHANGE_DONE = 102;
    private static final int RESTART_PREVIEW = 103;
    
    // These latency time are for the CameraLatency test.
    private long mAutoFocusTime;
    private long mFocusStartTime;
    
    private int mCurrentMode = ModePicker.MODE_PHOTO;
    
    private boolean mIsInitialized = false;
    private boolean mIsCameraClosed = false;
    private boolean mIsSnapshotOnIdle = false;
    private boolean mIsSelftimerCounting = false;
    private boolean mIsZSDEnabled;
    private boolean mIsAutoFocusCallback = false;
    private boolean mIsKeyHalfPressed = false;
    private boolean mIsCameraKeyLongPressed = false;
    private boolean mIsReleaseActor = false;
    
    private CameraActivity mCameraActivity;
    private SaveRequest mSaveRequest;
    private CameraCategory mCameraCategory;
    
    private MediaActionSound mCameraSound;
    private ModuleManager mModuleManager;
    private SelfTimerManager mSelfTimerManager;

    private final Handler mHandler = new MainHandler();
    private final AutoFocusCallback mAutoFocusCallback = new AutoFocusCallback();
    private final AutoFocusMoveCallback mAutoFocusMoveCallback = new AutoFocusMoveCallback();
    private final ICameraAppUi mICameraAppUi;
    
    public PhotoActor(CameraActivity context, ModuleManager moduleManager, int mode) {
        super(context);
        Log.i(TAG, "[PhotoActor]constructor");

        mCameraActivity = context;
        mCameraCategory = new CameraCategory();
        mICameraAppUi = context.getCameraAppUI();

        if (mCameraActivity.isImageCaptureIntent()) {
            mICameraAppUi.switchShutterType(ShutterButtonType.SHUTTER_TYPE_PHOTO);
        } else {
            mICameraAppUi.switchShutterType(ShutterButtonType.SHUTTER_TYPE_PHOTO_VIDEO);
        }
        mCameraSound = new MediaActionSound();
        mCameraSound.load(MediaActionSound.FOCUS_COMPLETE);
        mModuleManager = moduleManager;
        prepareCurrentMode(mode);
        mICameraAppUi.setReviewListener(mRetakeListener, null);
        mSelfTimerManager = (SelfTimerManager) mContext.getSelfTimeManager();
    }

    @Override
    public void onCameraParameterReady(boolean startPreview) {
        super.onCameraParameterReady(startPreview);
        Log.i(TAG, "[onCameraParameterReady]startPreview = " + startPreview);
        mModuleManager.onCameraParameterReady(startPreview);
        if (startPreview) {
            if (!mModuleManager.startPreview(true)) {
                startPreview(true);
            }
        } 
        if (mCameraActivity.getISettingCtrl() != null && mCameraActivity.getISettingCtrl()
                          .getSettingValue(SettingConstants.KEY_SELF_TIMER) != null) {
            String seflTimer = mCameraActivity.getISettingCtrl().getSettingValue(
                   SettingConstants.KEY_SELF_TIMER);
            mSelfTimerManager.setSelfTimerDuration(seflTimer);
        }
        mCameraActivity.setCameraState(CameraActivity.STATE_IDLE);
        mHandler.removeMessages(PARAMETER_CHANGE_DONE);
        mHandler.sendEmptyMessage(PARAMETER_CHANGE_DONE);
    }

    
    @Override
    public void stopPreview() {
        Log.i(TAG, "[stopPreview] getCameraState()=" + mCameraActivity.getCameraState());
        if (mCameraActivity.getCameraState() != CameraActivity.STATE_PREVIEW_STOPPED) {
            mIsZSDEnabled = "on".equals(mCameraActivity.getISettingCtrl().getSettingValue(
                    SettingConstants.KEY_CAMERA_ZSD));
            if ((!mIsZSDEnabled || (mIsZSDEnabled && mCameraActivity
                    .getCameraState() != CameraActivity.STATE_SNAPSHOT_IN_PROGRESS))) {
                // stop preview may not be called in main thread,we need to
                // synchronized "stopPreview" and
                // "sFaceDetectionStarted = false"
                // Exception Case: touch focus between onCamearOpenDone and
                // startPreview Done,press home key to exit camera and then
                // enter
                // immediately
                mCameraActivity
                        .setCameraState(CameraActivity.STATE_PREVIEW_STOPPED);
                if (mModuleManager.stopPreview()) {
                    return;
                }
                if (mCameraActivity.getCameraDevice() != null) {
                    // Reset focus
                    mCameraActivity.getCameraDevice().cancelAutoFocus();
                    mCameraActivity.getCameraDevice().stopPreview();
                    if (mModuleManager.getModeState() == ModeState.STATE_IDLE) {
                        mICameraAppUi.restoreViewState();
                    }
                }
            mCameraActivity.runOnUiThread(new Runnable() {
                public void run() {
                    if (mCameraActivity.getFocusManager() != null) {
                        mCameraActivity.getFocusManager().onPreviewStopped();
                    }
                }
            });
        }
        }
    }

    @Override
    public void onShutterButtonLongPressed(ShutterButton button) {
        Log.i(TAG, "[onShutterButtonLongPressed] button = " + button );
        int cameraState = mCameraActivity.getCameraState();
        if (CameraActivity.STATE_SWITCHING_CAMERA == cameraState
                || CameraActivity.STATE_PREVIEW_STOPPED == cameraState) {
            Log.i(TAG, "current state  = "+ cameraState +" so return");
            return;
        }

        if (mModuleManager.onShutterButtonLongPressed()) {
            Log.i(TAG, "[onShutterButtonLongPressed]onShutterButtonLongPressed is true.");
            return;
        }
        
        if (mCameraActivity.isImageCaptureIntent()) {
            mICameraAppUi.showInfo(mCameraActivity
                    .getString(R.string.normal_camera_continuous_not_supported),3 * 1000,(int)mCameraActivity.getResources().getDimension(R.dimen.info_bottom));
            Log.i(TAG, "[onShutterButtonLongPressed] isImageCaptureIntent is true.");
            return;
        }
    }
    
    @Override
    public void onShutterButtonFocus(ShutterButton button, boolean pressed) {
        Log.i(TAG, "[onShutterButtonFocus]pressed = " + pressed +",mIsCameraClosed = " + mIsCameraClosed);
        if (pressed && isCameraReady()) {
            mModuleManager.onShutterButtonFocus(true);
        } else if (!pressed && !mIsCameraClosed) {
            mModuleManager.onShutterButtonFocus(false);
        }
    }

    @Override
    public void onShutterButtonClick(ShutterButton button) {
        int cameraState = mCameraActivity.getCameraState();
        ViewState currentViewState = mICameraAppUi.getViewState();
        Log.i(TAG, "[onShutterButtonClick] cameraState = " + cameraState
                + ", currentViewState = " + currentViewState);

        if (ViewState.VIEW_STATE_LOMOEFFECT_SETTING == currentViewState
                || ViewState.VIEW_STATE_CONTINUOUS_CAPTURE == currentViewState
                || ViewState.VIEW_STATE_CAMERA_CLOSED == currentViewState) {
            return;
        }

        if (mICameraAppUi.updateRemainStorage() > 0) {
            if (!(CameraActivity.STATE_SWITCHING_CAMERA == cameraState || CameraActivity.STATE_PREVIEW_STOPPED == cameraState)) {
                if (mSelfTimerManager.startSelfTimer()) {
                    Log.i(TAG, "[onShutterButtonClick] start self timer");
                    mModuleManager.onSelfTimerState(true);
                    mICameraAppUi.setSwipeEnabled(false);
                    mICameraAppUi.setViewState(ViewState.VIEW_STATE_CAPTURE);
                    mIsSelftimerCounting = true;
                    return;
                } else {
                    mIsSelftimerCounting = false;
                }
                
                mModuleManager.onPhotoShutterButtonClick();
            }
        } else {
            Log.i(TAG, "remain storage is less than 0");
            mICameraAppUi.showRemaining();
        }
    }

    @Override
    public void autoFocus() {
        Log.i(TAG, "[autoFocus]...");
        if (mCameraActivity.getCameraDevice() == null) {
            Log.e(TAG, "[autoFocus]device is null,return!");
            return;
        }
        mFocusStartTime = System.currentTimeMillis();
        mCameraActivity.getCameraDevice().autoFocus(mAutoFocusCallback);
        mCameraActivity.setCameraState(CameraActivity.STATE_FOCUSING);
        mICameraAppUi.setViewState(ViewState.VIEW_STATE_FOCUSING);
    }
    
    @Override
    public void cancelAutoFocus() {
        Log.i(TAG, "[cancelAutoFocus] mode state:" + mModuleManager.getModeState());
        if (mCameraActivity.getCameraDevice() == null
                || ModeState.STATE_CLOSED == mModuleManager.getModeState()) {
            Log.e(TAG, "[cancelAutoFocus]device is null,return!");
            return;
        }
        mCameraActivity.getCameraDevice().cancelAutoFocus();
        if (!mIsSelftimerCounting
                && mCameraActivity.getCameraState() != CameraActivity.STATE_SNAPSHOT_IN_PROGRESS
                && mCameraActivity.getCameraState() != CameraActivity.STATE_PREVIEW_STOPPED) {
            mCameraActivity.setCameraState(CameraActivity.STATE_IDLE);
            if (mModuleManager.getModeState() == ModeState.STATE_IDLE) {
                mICameraAppUi.restoreViewState();
            }
        }
        setFocusParameters();
    }
    
    @Override
    public void playSound(int soundId) {
        Log.i(TAG, "[playSound]soundId =" + soundId);
        /*prize Camera mute wanzhijuan 2016-5-30 start*/
        if (isCameraUnMute()) {
        	mCameraSound.play(soundId);
        }
        /*prize Camera mute wanzhijuan 2016-5-30 end*/
    }
    
    @Override
    public void setFocusParameters() {
        Log.i(TAG, "[setFocusParameters]sIsAutoFocusCallback =" + mIsAutoFocusCallback);
        mCameraActivity.applyParameterForFocus(!mIsAutoFocusCallback);
        mIsAutoFocusCallback = false;
    }
    
    @Override
    public void release() {
        Log.i(TAG, "[release]...");
        // remove message, it will be unused after release
        mIsReleaseActor = true;
        mHandler.removeMessages(PARAMETER_CHANGE_DONE);
        // onDestroy path will call onCameraClose and release,
        // do not let resetPhotoActor and onLeaveActor call twice
        if (mCameraSound != null) {
            mCameraSound.release();
            mCameraSound = null;
        }

        resetPhotoActor();
        mCameraCategory.onLeaveActor();
        mModuleManager.closeMode();
        
    }
    
    @Override
    public void onRestoreSettings() {
        mModuleManager.onRestoreSettings();
    }
    
    @Override
    public int getMode() {
        return mCurrentMode;
    }
    
    @Override
    public OnShutterButtonListener getPhotoShutterButtonListener() {
        return this;
    }
    
    @Override
    public FaceDetectionListener getFaceDetectionListener() {
        return mFaceDetectionListener;
    }
    
    @Override
    public OnClickListener getOkListener() {
        return mOkListener;
    }
    
    @Override
    public OnClickListener getCancelListener() {
        return mCancelListener;
    }
    
    @Override
    public ErrorCallback getErrorCallback() {
        return new CameraErrorCallback();
    }
    
    @Override
    public OnSingleTapUpListener getonSingleTapUpListener() {
        return mOnSingleTapListener;
    }
    
    @Override
    public OnLongPressListener getonLongPressListener() {
        return mOnLongPressListener;
    }
    
    @Override
    public void onCameraOpenDone() {
        Log.i(TAG, "[onCameraOpenDone]...");
        mIsCameraClosed = false;
    }
    
    @Override
    public boolean onBackPressed() {
        Log.i(TAG,
                "[onBackPressed] isCameraIdle =" + isCameraIdle() + ",CameraState:"
                        + mContext.getCameraState() +",mIsSelftimerCounting = " + mIsSelftimerCounting);
        
        if (!isCameraIdle()) {
            if (mIsSelftimerCounting) {
                mSelfTimerManager.stopSelfTimer();
                mIsSelftimerCounting = false;
                mCameraActivity.setCameraState(CameraActivity.STATE_IDLE);
                mModuleManager.onSelfTimerState(false);
                mICameraAppUi.setSwipeEnabled(true);
                mICameraAppUi.restoreViewState();
            }

            if (mCameraActivity.isImageCaptureIntent()
                    && mICameraAppUi.getShutterType() == ShutterButtonType.SHUTTER_TYPE_OK_CANCEL) {
                mCancelListener.onClick(null);
            }
            // camera should exit even if focus do not complete when press back
            // key.
            if (mContext.getCameraState() == CameraActivity.STATE_FOCUSING) {
                return false;
            }
            return true;
        }
        return mModuleManager.onBackPressed();
    }
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        Log.i(TAG, "[onKeyDown] keyCode = " + keyCode);
        switch (keyCode) {
        case KeyEvent.KEYCODE_FOCUS:
            if (mIsInitialized && mCameraActivity.isFullScreen() && event.getRepeatCount() == 0) {
                // onShutterButtonFocus(true);
                mICameraAppUi.collapseViewManager(true);
                if (mCameraActivity.getCameraState() == CameraActivity.STATE_SNAPSHOT_IN_PROGRESS) {
                    return true;
                }
                
                if (!canTakePicture()) {
                    Log.w(TAG, "[onKeyDown]Do not do focus if there is not enough storage,return!");
                    return true;
                }
                mIsKeyHalfPressed = true;
                mCameraActivity.getFocusManager().onShutterDown();
            }
            return true;
        case KeyEvent.KEYCODE_VOLUME_DOWN: 
        case KeyEvent.KEYCODE_VOLUME_UP:  
        case KeyEvent.KEYCODE_CAMERA:
            if (isCanPhotograph() && mIsInitialized && mCameraActivity.isFullScreen() && !mIsCameraKeyLongPressed
                    && event.getRepeatCount() > 0) {
                if (mCameraActivity.getOrietation() == OrientationEventListener.ORIENTATION_UNKNOWN) {
                    Log.w(TAG,
                            "[onKeyDown]Delay capturing action to make sure orientation is in correct state,return!");
                    return false;
                }
                onShutterButtonLongPressed(null);
                mIsCameraKeyLongPressed = true;
            }else if (isCanVideo()) {
                if (event.getRepeatCount() == 0 && !mICameraAppUi.getCameraView(CommonUiType.REVIEW).isShowing()) {
                	if (mCameraActivity.getOrietation() == OrientationEventListener.ORIENTATION_UNKNOWN) {
                        Log.w(TAG,
                                "[onKeyUp]getOrietation is ORIENTATION_UNKNOWN,Delay capturing action to make sure orientation is in correct state.return! ");
                        return false;
                    }
                    
                    if (mSaveRequest != null && mSaveRequest.isQueueFull()) {
                        Log.w(TAG,
                                "[onKeyUp]not response camera physical key, when numbers of saveTask over 3,.return! ");
                        return false;
                    }
                    mICameraAppUi.getVideoShutter().performClick();
                }
                return true;
            }
            return true;
        default:
            break;
        }
        
        return false;
    }
    
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        Log.i(TAG, "[onKeyUp]keyCode = " + keyCode);
        switch (keyCode) {
        case KeyEvent.KEYCODE_FOCUS:
            if (mIsInitialized) {
                onShutterButtonFocus(null, false);
                if (mCameraActivity.getCameraState() == CameraActivity.STATE_SNAPSHOT_IN_PROGRESS) {
                    Log.w(TAG, "[onKeyUp]getCameraState is STATE_SNAPSHOT_IN_PROGRESS,return! ");
                    return true;
                }
                mIsKeyHalfPressed = false;
                mCameraActivity.getFocusManager().onShutterUp();
            }
            return true;
        case KeyEvent.KEYCODE_VOLUME_DOWN:
        case KeyEvent.KEYCODE_VOLUME_UP:
        case KeyEvent.KEYCODE_CAMERA:
            if (isCanPhotograph() && mIsInitialized && !mIsCameraKeyLongPressed && event.getRepeatCount() == 0
                    && mCameraActivity.isFullScreen()) {
                if (mCameraActivity.getOrietation() == OrientationEventListener.ORIENTATION_UNKNOWN) {
                    Log.w(TAG,
                            "[onKeyUp]getOrietation is ORIENTATION_UNKNOWN,Delay capturing action to make sure orientation is in correct state.return! ");
                    return false;
                }
                
                if (mSaveRequest != null && mSaveRequest.isQueueFull()) {
                    Log.w(TAG,
                            "[onKeyUp]not response camera physical key, when numbers of saveTask over 3,.return! ");
                    return false;
                }
                // mCamera.setSwipingEnabled(false);
                if(mICameraAppUi.getPhotoShutter() != null){
                	mICameraAppUi.getPhotoShutter().performClick();
                }
            }else {
            	if (mCameraActivity.getModePicker().getCurrentMode() == ModePicker.MODE_PANORAMA) {
    				mModuleManager.stopPanoramaCaptureByVolumeKey();
    			}
			}
            if(mIsCameraKeyLongPressed == true){
            	onShutterButtonFocus(null, false);
            }
            mIsCameraKeyLongPressed = false;
            return true;
            
        default:
            break;
        }
        
        return false;
    }
    
    @Override
    public boolean handleFocus() {
        // when camera slip to gallery, the focus mode should maintain as
        // INFINITY, not be overridden as NULL again.
    	/*prize-xuchunming-20180301-bugid:51298-start*/
        if (!mCameraActivity.isFullScreen() || mIsCameraClosed == true) {
        /*prize-xuchunming-20180301-bugid:51298-end*/
            Log.i(TAG, "[handleFocus] is not full screen.");
            return false;
        }
        
        Log.i(TAG, "[handleFocus]mKeyHalfPressed = " + mIsKeyHalfPressed);
        boolean canAutoFocus = SettingUtils.isSupported(Parameters.FOCUS_MODE_AUTO,
                                mCameraActivity.getParameters().getSupportedFocusModes());
        if (mIsKeyHalfPressed && canAutoFocus) {
            overrideFocusMode(Parameters.FOCUS_MODE_AUTO);
            return true;
        } else {
            overrideFocusMode(null);
            return false;
        }
    }
    
    @Override
    public void startFaceDetection() {
        Log.i(TAG, "[startFaceDetection]");
        ModeState modeState = mModuleManager.getModeState();
        if (ModeState.STATE_CLOSED == modeState || ModeState.STATE_FOCUSING == modeState
                || !isSupportFaceDetect()) {
            Log.w(TAG, "[startFaceDetection]Don't support FD detection");
            return;
        }
        
        if (mCameraActivity.getCameraDevice() != null
                && mCameraActivity.getParameters().getMaxNumDetectedFaces() > 0) {
            // run in ui thread
            mCameraActivity.runOnUiThread(new Runnable() {
                public void run() {
                    mCameraActivity.getFrameManager().initializeFrameView(false);
                }
            });
            if (FeatureSwitcher.isVfbEnable()
                    && mCameraActivity.getCurrentMode() == ModePicker.MODE_FACE_BEAUTY) {
                Log.i(TAG, "[vFB]current is in VFB mode,not need startFD,so return.");
                /*prize-xuchunming-20160602-startFaceDetection when cancle mtk vfb-start*/
                //return;
                /*prize-xuchunming-20160602-startFaceDetection when cancle mtk vfb-end*/
            }
            mCameraActivity.getCameraDevice().startFaceDetection();
        }
    }
    
    @Override
    public void stopFaceDetection() {
        Log.i(TAG, "[stopFaceDetection]");
        if (mCameraActivity.getCameraDevice() != null
                && mCameraActivity.getCameraDevice().getFaceDetectionStatus()
                && mCameraActivity.getParameters().getMaxNumDetectedFaces() > 0) {
            Log.i(TAG, "[stopFaceDetection]will call stopFaceDetection ");
            mCameraActivity.getCameraDevice().stopFaceDetection();
            // run in ui thread
            mCameraActivity.runOnUiThread(new Runnable() {
                public void run() {
                    if (mCameraActivity.getFrameView() != null) {
                        mCameraActivity.getFrameView().clear();
                    }
                }
            });
        }
    }
    
    @Override
    public void onSettingChange(String key, String value) {
    	// TODO Auto-generated method stub
    	mModuleManager.onSettingChange(key, value);
    }
    
    @Override
    public boolean capture() {
        return false;
    }
    
    @Override
    public boolean readyToCapture() {
        return false;
    }
    
    public boolean onUserInteraction() {
        mCameraActivity.keepScreenOnAwhile();
        return true;
    }
    
    public Listener getFocusManagerListener() {
        return this;
    }
    
    public void onCameraClose() {
        Log.i(TAG,
                "[onCameraClose]mCameraClosed =" + mIsCameraClosed
                        + ", SelfTimerManagerIsCounting = "
                        + mSelfTimerManager.isSelfTimerCounting());
        mIsCameraClosed = true;

        if (mSelfTimerManager.isSelfTimerCounting()) {
            mSelfTimerManager.stopSelfTimer();
            mICameraAppUi.setSwipeEnabled(true);
            mICameraAppUi.setViewState(ViewState.VIEW_STATE_NORMAL);
            mIsSelftimerCounting = false;
        }

        // the RESTART_PREVIEW message should be removed when camera is closed
        mHandler.removeMessages(RESTART_PREVIEW);
        // remove message, it will restart after
        // onresume->reopen->restartpreview
        mHandler.removeMessages(PARAMETER_CHANGE_DONE);

        // resetPhotoActor();
        //mCameraCategory.onLeaveActor();
        if (!mModuleManager.stopPreview()) {
            stopPreview();
        }
        mModuleManager.onCameraClose();
    }
    
    private void initializeAfterPreview() {
        Log.i(TAG, "[initializeAfterPreview]...mSelfTimerManager = " + mSelfTimerManager);
        if (mIsCameraClosed || mCameraActivity.getCameraDevice() == null) {
            Log.w(TAG, "[initializeAfterPreview mCamera]mCameraClosed= " + mIsCameraClosed);
            return;
        }
        // for auto focus moving callback
        mIsAutoFocusCallback = false;
        // if the value of smile shot in sharepreference is on, we should turn
        // it as off when gesture shot is on
        // in Face Beauty mode. Because capture mode may be changed to normal
        // mode from Face Beauty mode, while
        // gesture shot and smile shot is mutually exclusive.
        mCameraActivity.keepScreenOnAwhile();
        // for capture intent
        // If Current is in capture or in recording state, cann't change the shutter UI.
        // the reason is initializeAfterPreview() maybe delay execute.
        if (ModeState.STATE_CAPTURING != mModuleManager.getModeState()
                && ModeState.STATE_RECORDING != mModuleManager.getModeState()) {
            mCameraCategory.switchShutterButton();
        }
        mSelfTimerManager.setTimerListener(mSelfTimerListener);
        // face Detection
        if (isSupportFaceDetect()) {
            startFaceDetection();
        } else {
            stopFaceDetection();
        }
        if (mIsInitialized) {
            Log.i(TAG, "[initializeAfterPreview mCamera]has initialized.");
            return;
        }
        // The next steps will be excuted only at the first time.
        mCameraActivity.getFrameManager().initializeFrameView(false);
        mIsInitialized = true;
    }
    
    public void startPreview(boolean needStop) {//gangyun modif private to public
        Log.i(TAG, "[startPreview]needStop = " + needStop);
        mCameraActivity.runOnUiThread(new Runnable() {
            public void run() {
                mCameraActivity.getFocusManager().resetTouchFocus();
            }
        });
        // continuous shot neednot stop preview after capture
        if (needStop) {
            stopPreview();
        }
        
        if (!mIsSnapshotOnIdle) {
            // If the focus mode is continuous autofocus, call cancelAutoFocus
            // to
            // resume it because it may have been paused by autoFocus call.
            if (Parameters.FOCUS_MODE_CONTINUOUS_PICTURE.equals(mCameraActivity.getFocusManager()
                    .getFocusMode())) {
                mCameraActivity.getCameraDevice().cancelAutoFocus();
                mCameraActivity.getCameraDevice().setAutoFocusMoveCallback(mAutoFocusMoveCallback);
            }
            mCameraActivity.getFocusManager().setAeLock(false); // Unlock AE and
                                                                // AWB.
            mCameraActivity.getFocusManager().setAwbLock(false);
        }
        if (isPowerDebug()) {
            if (SettingUtils.isSupported(Parameters.FOCUS_MODE_INFINITY, mCameraActivity
                    .getParameters().getSupportedFocusModes())) {
                overrideFocusMode(Parameters.FOCUS_MODE_INFINITY);
                mCameraActivity.getParameters().setFocusMode(
                        mCameraActivity.getFocusManager().getFocusMode());
                //mCameraActivity.applyParametersToServer();
                Log.i(TAG, "set debug focus     FOCUS_MODE_INFINITY ");
            }
        } else {
            setFocusParameters();
            Log.i(TAG, "[startPreview]set setFocusParameters normal");
        }
        mCameraActivity.getCameraDevice().startPreviewAsync();
        mCameraActivity.getFocusManager().onPreviewStarted();
    }
    
    private void prepareCurrentMode(int newMode) {
        Log.i(TAG, "[prepareCurrentMode] mCurrentMode:" + mCurrentMode + ",newMode:" + newMode);
        mCurrentMode = newMode;
        CameraModeType mode = getCameraModeType(mCurrentMode);
        if (mode == null) {
            mode = CameraModeType.EXT_MODE_PHOTO;
        }
        mModuleManager.createMode(mode);
    }
    
    public void restartPreview(boolean needStop) {//gangyun modif private to public
        Log.d(TAG, "[restartPreview]needStop = " + needStop);
        if (!mModuleManager.startPreview(needStop)) {
            startPreview(needStop);
        }
        mCameraActivity.setCameraState(CameraActivity.STATE_IDLE);
        mICameraAppUi.restoreViewState();
        startFaceDetection();
    }
    
    private void overrideFocusMode(String focusMode) {
        Log.d(TAG, "[overrideFocusMode]focusMode = " + focusMode);
        if (focusMode != null
                && !SettingUtils.isSupported(focusMode, mCameraActivity.getParameters()
                        .getSupportedFocusModes())) {
            focusMode = Parameters.FOCUS_MODE_INFINITY;
        }
        if (!mIsCameraClosed && mCameraActivity.getFocusManager() != null) {
            mCameraActivity.getFocusManager().overrideFocusMode(focusMode);
        }
    }

    private void resetPhotoActor() {
        mIsAutoFocusCallback = false;
        mICameraAppUi.dismissInfo();
    }
    
    private class CameraCategory {
        public void switchShutterButton() {
            ISettingCtrl settingCtrl = mModuleManager.getSettingController();
            boolean isSlowMotionOn = false;
            if (settingCtrl == null) {
                isSlowMotionOn = "on".equals(settingCtrl.getSettingValue(SettingConstants.KEY_SLOW_MOTION));
            }
            if (mCameraActivity.isImageCaptureIntent()) {
                mICameraAppUi.switchShutterType(ShutterButtonType.SHUTTER_TYPE_PHOTO);
            } else if (isSlowMotionOn) {
                mICameraAppUi.switchShutterType(ShutterButtonType.SHUTTER_TYPE_SLOW_VIDEO);
            } else {
                mICameraAppUi.switchShutterType(ShutterButtonType.SHUTTER_TYPE_PHOTO_VIDEO);
                mICameraAppUi.getCameraView(CommonUiType.SHUTTER).refresh();
            }
        }
        
        public boolean canshot() {
            return 1 <= Storage.getLeftSpace();
        }
        
        public void onLeaveActor() {
            Log.i(TAG, "[onLeaveActor]");
            if (mIsCameraClosed
                    && mCameraActivity.getFocusManager() != null
                    && Parameters.FOCUS_MODE_AUTO.equals(mCameraActivity.getFocusManager()
                            .getCurrentFocusMode(mContext))
                    && mCameraActivity.getCameraDevice() != null) {
                // when VFB mode -> move face out of preview and in the
                // 5S do auto focus;but when the auto focus not callback,
                // current VFB mode will auto back to normal mode,so AP not
                // do cancel the auto focus command,at now preview with face
                // the fd client because not save the cancel command,so will
                // not do FD algorithm;
                Log.i(TAG, "[onLeaveActor]will cancel auto focus.");
                mCameraActivity.getCameraDevice().cancelAutoFocus();
            }
            //mICameraAppUi.restoreViewState();
            if (mCameraActivity.isImageCaptureIntent()) {
                mICameraAppUi.hideReview();
                mICameraAppUi.switchShutterType(ShutterButtonType.SHUTTER_TYPE_PHOTO);
            }
        }
    }
    
    private boolean isPowerDebug() {
        return SystemProperties.getInt("camera_af_power_debug", 0) == 1 ? true : false;
    }
    
    private final class AutoFocusCallback implements android.hardware.Camera.AutoFocusCallback {
        @Override
        public void onAutoFocus(boolean focused, android.hardware.Camera camera) {
            Log.i(TAG, "[onAutoFocus]mIsCameraClosed = " + mIsCameraClosed);
            if (mIsCameraClosed) {
                return;
            }
            
            mAutoFocusTime = System.currentTimeMillis() - mFocusStartTime;
            Log.i(TAG, "mAutoFocusTime = " + mAutoFocusTime + "ms" + ",cameraState = "
                    + mCameraActivity.getCameraState());
            if (!mIsSelftimerCounting
                    && mCameraActivity.getCameraState() == CameraActivity.STATE_FOCUSING) {
                mCameraActivity.setCameraState(CameraActivity.STATE_IDLE);
            }
            mCameraActivity.getFocusManager().onAutoFocus(focused);
            mIsAutoFocusCallback = true;
        }
    }
    
    private final class AutoFocusMoveCallback implements
            android.hardware.Camera.AutoFocusMoveCallback {
        @Override
        public void onAutoFocusMoving(boolean moving, android.hardware.Camera camera) {
            Log.i(TAG, "[onAutoFocusMoving]moving = " + moving);
            mCameraActivity.getFocusManager().onAutoFocusMoving(moving);
        }
    }
    
    private boolean canTakePicture() {
        return isCameraIdle() && (mCameraCategory.canshot());
    }
    
    private boolean isCameraIdle() {
        return !mIsSelftimerCounting
                && ((mCameraActivity.getCameraState() == CameraActivity.STATE_IDLE) || ((mCameraActivity
                        .getFocusManager() != null)
                        && mCameraActivity.getFocusManager().isFocusCompleted() && (mCameraActivity
                        .getCameraState() != CameraActivity.STATE_SWITCHING_CAMERA)));
    }
    
    private void doCancel() {
        mCameraActivity.setResultExAndFinish(Activity.RESULT_CANCELED, new Intent());
    }
    
    private boolean isSupportFaceDetect() {
        String faceDetection = mModuleManager.getSettingController().getSettingValue(
                SettingConstants.KEY_CAMERA_FACE_DETECT);
        Log.d(TAG, "[isSupportFaceDetect]faceDetection =" + faceDetection);
        return "on".equals(faceDetection);
    }
    
    /**
     * This Handler is used to post message back onto the main thread of the
     * application
     */
    private class MainHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            Log.i(TAG, "[handleMessage]msg.what =" + msg.what + ",mIsReleaseActor = "
                    + mIsReleaseActor + ",mIsCameraClosed = " + mIsCameraClosed);
            switch (msg.what) {
            case PARAMETER_CHANGE_DONE:
                // onModechange()->leave Acotor ->sendMsg:preview done or
                // change_done
                // this time will null pointer :mSelfTimerManager in
                // initializeAfterPreview();
                // in this case,not need to handler this msg
                if (!mIsReleaseActor) {
                    initializeAfterPreview();
                }
                break;

            case RESTART_PREVIEW:
                if (!mIsCameraClosed) {
                    restartPreview(true);
                }
                break;

            default:
                break;
            }
        }
    }

    private OnClickListener mOkListener = new OnClickListener() {
        
        @Override
        public void onClick(View view) {
            mModuleManager.onOkButtonPress();
        }
    };
    
    private OnClickListener mCancelListener = new OnClickListener() {
        
        @Override
        public void onClick(View view) {
            if (mModuleManager.onCancelButtonPress()) {
                return;
            }
            doCancel();
        }
    };
    
    private OnClickListener mRetakeListener = new OnClickListener() {
        
        @Override
        public void onClick(View view) {
            if (mIsCameraClosed) {
                Log.i(TAG, "[onClick]mIsCameraClosed = " + mIsCameraClosed);
                return;
            }
            
            mICameraAppUi.hideReview();
            mICameraAppUi.switchShutterType(ShutterButtonType.SHUTTER_TYPE_PHOTO);
            restartPreview(true);
            /*prize-xuchunming-20161105-hide modechange blur effect when app exit-start*/
            mCameraActivity.setPreviewCallback();
            /*prize-xuchunming-20161105-hide modechange blur effect when app exit-end*/
        }
    };
    
    private CameraActivity.OnSingleTapUpListener mOnSingleTapListener = new CameraActivity.OnSingleTapUpListener() {
        @Override
        public void onSingleTapUp(View view, int x, int y) {
        	/*prize-xuchunming-20161010-bugid:22263-start*/
        	if(mCameraActivity.getModePicker().isEnabled() == false){
        		Log.w(TAG, "[mOnSingleTapListener] ModePicker isEnabled == false ,return.");
            	return;
            }
        	/*prize-xuchunming-20161010-bugid:22263-end*/
            if (mIsCameraClosed || ViewState.VIEW_STATE_CAMERA_CLOSED == mICameraAppUi.getViewState()) {
                Log.w(TAG, "[mOnSingleTapListener]mIsCameraClosed is true,return.");
                return;
            }
            if (mSelfTimerManager.isSelfTimerCounting()) {
                Log.w(TAG, "[mOnSingleTapListener] self timer is counting,return.");
                return;
            }

            FocusManager focusManager = mCameraActivity.getFocusManager();
            if (focusManager == null) {
                return;
            }
            
            if (mModuleManager.onSingleTapUp(view, x, y)) {
                Log.i(TAG, "[onSingleTapUp] module manager has handled it,return.");
                return;
            }

            focusManager.onSingleTapUp(x, y);
        }
    };
    
    private CameraActivity.OnLongPressListener mOnLongPressListener = new CameraActivity.OnLongPressListener() {
        @Override
        public void onLongPress(View view, int x, int y) {
            if (mIsCameraClosed) {
                Log.w(TAG, "[mOnLongPressListener]mIsCameraClosed is true,return.");
                return;
            }
            if (mSelfTimerManager.isSelfTimerCounting()) {
                Log.w(TAG, "[mOnLongPressListener] self timer is counting,return.");
                return;
            }
            mModuleManager.onLongPress(view, x, y);
        }
    };
    
    private FaceDetectionListener mFaceDetectionListener = new FaceDetectionListener() {
        @Override
        public void onFaceDetection(Face[] faces, android.hardware.Camera camera) {
            //Log.d(TAG, "[onFaceDetection]length = " + faces.length);
            if (mCameraActivity.getCameraDevice() != null
                    && mCameraActivity.getCameraDevice().getFaceDetectionStatus()) {
                mCameraActivity.getFrameView().setFaces(faces);
            }
            mModuleManager.onFaceDetected(faces);
            return;
        }
    };

    private SelfTimerListener mSelfTimerListener = new SelfTimerListener() {

        @Override
        public void onTimerStart() {
            mModuleManager.onSelfTimerState(true);
        }

        @Override
        public void onTimerStop() {
        }

        @Override
        public void onTimerTimeout() {
            Log.i(TAG, "[onTimerTimeout]");
             onShutterButtonClick(null);
            mModuleManager.onSelfTimerState(false);
        }
    };

    private boolean isCameraReady() {
        boolean isReady = true;
        int cameraState = mCameraActivity.getCameraState();
        isReady = !(mIsCameraClosed ||CameraActivity.STATE_SWITCHING_CAMERA == cameraState
                || CameraActivity.STATE_PREVIEW_STOPPED == cameraState || mSelfTimerManager
                .isSelfTimerEnabled());
        Log.i(TAG, "[isCameraReady] cameraState = " + cameraState + ", isSelfTimerEnalbe = "
                + mSelfTimerManager.isSelfTimerEnabled() + ",isCameraReady = " + isReady);
        return isReady;
    }
   
    private boolean isCanPhotograph() {
    	if (mICameraAppUi.getPhotoShutter() == null || mICameraAppUi.getPhotoShutter().getVisibility() != View.VISIBLE
    			|| !mICameraAppUi.getPhotoShutter().isEnabled()) {
    		Log.i(TAG, "[isCanPhotograph] PhotoShutter not can take a picture");
    		return false;
    	}
    	else if (mICameraAppUi.getViewState() == ViewState.VIEW_STATE_SETTING 
    			|| mICameraAppUi.getViewState() == ViewState.VIEW_STATE_SUB_SETTING
    			|| mICameraAppUi.getViewState() == ViewState.VIEW_STATE_PRE_RECORDING
    			|| mICameraAppUi.getViewState() == ViewState.VIEW_STATE_RECORDING) {
    		return false;
    	} 
    	return true;
    }

    
    private boolean isCanVideo() {
    	if (mICameraAppUi.getVideoShutter() == null || mICameraAppUi.getVideoShutter().getVisibility() != View.VISIBLE
    			|| !mICameraAppUi.getVideoShutter().isEnabled() || (mCameraActivity.getCurrentMode() != ModePicker.MODE_PREVIDEO && mCameraActivity.getCurrentMode() != ModePicker.MODE_SHORT_PREVIDEO)
                ) {
    		Log.i(TAG, "[isCanVideo] VideoShutter not can Recording");
    		return false;
    	}else if (mICameraAppUi.getViewState() == ViewState.VIEW_STATE_SETTING 
    			|| mICameraAppUi.getViewState() == ViewState.VIEW_STATE_SUB_SETTING) {
    		return false;
    	} 
    	return true;
    }
    
    @Override
    public void onShowInfo() {
    	// TODO Auto-generated method stub
    	mModuleManager.onShowInfo();
    }
    
    @Override
    public void onHideInfo() {
    	// TODO Auto-generated method stub
    	mModuleManager.onHideInfo();
    }
    
    @Override
    public void onModeBlueChange() {
    	// TODO Auto-generated method stub
    	mModuleManager.onModeBlueChange();
    	
    }
    
    @Override
    public void onModeBlueDone() {
    	// TODO Auto-generated method stub
    	mModuleManager.onModeBlueDone();
    }
	
	@Override
    public void onNavigatChange(boolean isShow) {
    	// TODO Auto-generated method stub
    	mModuleManager.onNavigatChange(isShow);
    }
    
    @Override
    public void openFrontCaemra() {
    	// TODO Auto-generated method stub
    	mModuleManager.openFrontCaemra();
    }
    
    @Override
    public void onPause() {
    	// TODO Auto-generated method stub
    	mModuleManager.onPause();
    }
    
    /*prize-xuchunming-20160829-add previewCallback interface-start*/
    public PreviewCallbackListen getPreviewCallback(){
		return mModuleManager.getPreviewCallback();
	}
    /*prize-xuchunming-20160829-add previewCallback interface-end*/
 @Override
    public void onAutoFocusing() {
    	// TODO Auto-generated method stub
    	mModuleManager.onAutoFocusing();
    }
    
    @Override
    public void onAutoFocused() {
    	// TODO Auto-generated method stub
    	mModuleManager.onAutoFocused();
    }
    
    @Override
    public void onMvFocusing() {
    	// TODO Auto-generated method stub
    	mModuleManager.onMvFocusing();
    }
    
    @Override
    public void onMvFocused() {
    	// TODO Auto-generated method stub
    	mModuleManager.onMvFocused();
    }
    
    @Override
    public PreviewCallbackListen getPreviewCallbackListen() {
    	// TODO Auto-generated method stub
    	return mModuleManager.getPreviewCallback();
    }
    
    public ICameraMode getICameraMode(){
    	return mModuleManager.getICameraMode();
    }
}
