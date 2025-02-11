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

package com.mediatek.camera.mode.facebeauty;

import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.Face;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.ShutterCallback;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.View;

import com.android.camera.CameraActivity;
import com.android.camera.FeatureSwitcher;
import com.android.camera.R;
import com.android.camera.bridge.CameraAppUiImpl;
import com.mediatek.camera.AdditionManager;
import com.mediatek.camera.ICameraAddition;
import com.mediatek.camera.ICameraContext;
import com.mediatek.camera.ICameraAddition.AdditionActionType;
import com.mediatek.camera.ICameraMode.ModeState;
import com.mediatek.camera.ISettingRule;
import com.mediatek.camera.mode.CameraMode;
import com.mediatek.camera.mode.facebeauty.FaceBeautyParametersHelper.ParameterListener;
import com.mediatek.camera.platform.ICameraAppUi.SpecViewType;
import com.mediatek.camera.platform.ICameraAppUi.ViewState;
import com.mediatek.camera.platform.ICameraDeviceManager.ICameraDevice.AutoFocusMvCallback;
import com.mediatek.camera.platform.ICameraDeviceManager.ICameraDevice.cFbOriginalCallback;
import com.mediatek.camera.platform.ICameraView;
import com.mediatek.camera.platform.IFocusManager;
import com.mediatek.camera.platform.IFileSaver.FILE_TYPE;
import com.mediatek.camera.platform.IFileSaver.OnFileSavedListener;
import com.mediatek.camera.setting.SettingConstants;
import com.mediatek.camera.util.Log;
import com.mediatek.camera.util.Util;

import junit.framework.Assert;

import java.util.ArrayList;
import java.util.Locale;
import static com.android.internal.R.id.locale;

public class FaceBeautyMode extends CameraMode implements ICameraAddition.Listener,
        IFocusManager.FocusListener {
    private static final String TAG = "FaceBeautyMode";

    protected static final int SUPPORTED_FB_PROPERTIES_MAX_NUMBER = 4;

    protected static final int ON_CAMERA_PARAMETERS_READY = 101;
    protected static final int INFO_FACE_DETECTED = 102;
    protected static final int ORIENTATION_CHANGED = 103;
    protected static final int ON_BACK_PRESSED = 104;
    protected static final int HIDE_EFFECTS_ITEM = 105;
    protected static final int ON_FULL_SCREEN_CHANGED = 106;
    protected static final int ON_CAMERA_CLOSED = 107;
    protected static final int ON_SETTING_BUTTON_CLICK = 108;
    protected static final int ON_LEAVE_FACE_BEAUTY_MODE = 109;
    protected static final int ON_SELFTIMER_CAPTUEING = 110;
    protected static final int IN_PICTURE_TAKEN_PROGRESS = 111;
    protected static final int REMVOE_BACK_TO_NORMAL = 112;
    protected static final int MSG_TAKEPICTURE = 113;
    protected static final int MSG_HIDE_COVER = 114;
    protected static final int MSG_SHOW_ANIM_COVER = 115;
    /*prize-xuchunming-201804026-add spotlight-start*/ 
    protected static final int ON_SPOTLIGHT_VISIBLE = 116;
    /*prize-xuchunming-201804026-add spotlight-end*/ 

    private static final int ON_CONFIGURATION_CHANGED = 201;
    private static final int RE_OPEN_GESTURE_SHOT = 202;
    protected static final int ACTION_MODE_BLUE_DONE = 203;
    private static final int RE_OPEN_GESTURE_SHOT_TIME = 1500;

    private CfbCallback mCfbCallback = new CfbCallback();
    private Handler mHandler;
    private FaceBeautyPreviewSize mFaceBeautyPreviewSize;
    private FaceBeautyParametersHelper mFaceBeautyParametersHelper;
    private ParameterListener mParameterListener;

    private ICameraView mICameraView;
    private ArrayList<Integer> mVfbFacesPoint = new ArrayList<Integer>();
    private AdditionManager mAdditionManager;

    private boolean mIsAutoFocusCallback = false;
    // record the image count in compressing stage, it will increase when onCanCapture,
    // and it will decrease when onPictureTaken(Jpeg);
    // usually,Application restartPreview in onPictureTaken(Jpeg),
    // but if application receive onCanCapture firstly, move the restartPreview to onCanCapture.
    private int mCapturedImageCount = 0;
    
    private ISettingRule mFlashRule;
    public FaceBeautyMode(ICameraContext cameraContext) {
        super(cameraContext);
        Log.i(TAG, "[FaceBeautyMode]constructor...");
        // first check the camera device

        mFaceBeautyParametersHelper = new FaceBeautyParametersHelper(cameraContext);
        mParameterListener = mFaceBeautyParametersHelper.getListener();

        if (mIFeatureConfig.isVfbEnable()) {
            // just Vfb have View,so if current project is only support cFB,so
            // not have view
            mICameraView = mICameraAppUi.getCameraView(SpecViewType.MODE_FACE_BEAUTY);
            mICameraView.init(mActivity, mICameraAppUi, mIModuleCtrl);
            mICameraView.setListener(mParameterListener);
            mICameraAppUi.changeBackToVFBModeStatues(false);
        } else if (mIFeatureConfig.isSlowMotionSupport()) {
            setCfbSlowMotionRule();
        }
        mHandler = new MainHandler(mActivity.getMainLooper());
        mAdditionManager = cameraContext.getAdditionManager();
        if (mICameraContext.getFeatureConfig().isLowRamOptSupport()) {
            //setFBLowRamRule();
        }
        /*prize-xuchunming-20170119-bug:picture blur-start*/
        if(FeatureSwitcher.isRearCameraSub() == false) {
            //setFBGmoRule();
        }
        /*prize-xuchunming-20170119-bug:picture blur-end*/
        if (mIFeatureConfig.isVfbEnable()) {
            //setVFBPreviewSizeRule();
        }
       
        deleteSettingRule();
    }

    @Override
    public void pause() {
        super.pause();
        Log.i(TAG, "[pause()] mICameraView = " + mICameraView);
        // Need hide the view when activity is onPause
        if (mICameraView != null) {
            mICameraView.hide();
        }
    }

    @Override
    public void resume() {
        super.resume();
        Log.i(TAG, "[resume()]");
        // for case: pressing home key to exit when capturing, and can not
        // slide to gallery after re-lunch.
        if (mICameraView != null) {
            mICameraView.show();
        }
        mICameraAppUi.setSwipeEnabled(true);
    }

    @Override
    public boolean close() {
        Log.i(TAG, "[closeMode]NextMode = " + mIModuleCtrl.getNextMode());
        /*prize-xuchunming-20180504-bugid:56566-start*/
        super.close();
        /*prize-xuchunming-20180504-bugid:56566-end*/
        if (mIModuleCtrl.getNextMode() != null) {
            // VFB photo mode to Video VR,not need set the face-beauty= false
            // 1:because video not know before mode is VFB;
            // 2:if set face-beauty= false,will found the video former parts not
            // have the face beauty effects
            if (mIFeatureConfig.isVfbEnable()) {
                if (CameraModeType.EXT_MODE_VIDEO == mIModuleCtrl.getNextMode()) {
                    //first need update the effects item if current effects is expanded
                    mICameraView.update(HIDE_EFFECTS_ITEM);
                } else {
                    setVFBPs(false);
                }
            }

            if (mHandler != null) {
                mHandler.sendEmptyMessage(ON_LEAVE_FACE_BEAUTY_MODE);
            }
            if (mICameraView != null) {
                mICameraView.update(REMVOE_BACK_TO_NORMAL);
            }

            // when leave out face beauty mode,need change the face view beauty
            // tag; because into this mode ,you have set true;
            changeFaceBeautyStatues(false);
        }
        // when close the mode ,need remove all the Msg when not execute
        removeAllMsg();
        mAdditionManager.close(true);
        resetoreSettingRule();
        return true;
    }

    @Override
    public boolean execute(ActionType type, Object... arg) {
        boolean returnValue = true;

        mAdditionManager.execute(type, true, arg);
        switch (type) {
        case ACTION_ON_CAMERA_OPEN:
            super.updateDevice();
            break;

        case ACTION_ON_CAMERA_CLOSE:
            mAdditionManager.close(true);
            ModeState state = getModeState();
            Log.d(TAG, "[onCameraClose]ModeState=" + state);
            if (ModeState.STATE_FOCUSING == state) {
                mICameraAppUi.restoreViewState();
            } else if (ModeState.STATE_CAPTURING == state) {
                mICameraAppUi.restoreViewState();
            }
            setModeState(ModeState.STATE_CLOSED);
            if (mIFeatureConfig.isVfbEnable()) {
                mICameraView.update(ON_CAMERA_CLOSED);
            }
            break;

        case ACTION_ON_START_PREVIEW:
            Assert.assertTrue(arg.length == 1);
            startPreview((Boolean) arg[0]);
            break;

        case ACTION_ON_CAMERA_PARAMETERS_READY:
            super.updateDevice();
            super.updateFocusManager();
            if (mIFocusManager != null) {
                mIFocusManager.setListener(this);
            }
            setModeState(ModeState.STATE_IDLE);

            // when user change the face beauty value to :Off need back to
            // PhotoMode
            if (isVfbOff()) {
                // leave faceBeauty mode
                mICameraAppUi.setCurrentMode(CameraModeType.EXT_MODE_PHOTO);
                Log.i(TAG, "[execute]set CameraModeType.EXT_MODE_PHOTO");
                break;
            }

            // just Vfb need stopFD,because when native will get the preview
            // buffer itself will
            // get the FD Client,if we not stopFD,means the FD client is catched
            // by us;
            if (mIFeatureConfig.isVfbEnable()) {
                //mIModuleCtrl.stopFaceDetection();
            }
            mFaceBeautyParametersHelper.updateParameters(mICameraDevice);
            mHandler.sendEmptyMessage(ON_CAMERA_PARAMETERS_READY);

            mAdditionManager.onCameraParameterReady(true);
            break;

        case ACTION_FACE_DETECTED:
            Face[] faces = (Face[]) arg;
            if (mIFeatureConfig.isVfbEnable() && faces != null) {
                mICameraView.update(INFO_FACE_DETECTED, faces.length);
                // update the face beauty point
                storeFaceBeautyLocation(faces);
                // PhotoActor/3rd party not set the face, because you have call
                // stopFD
                mIModuleCtrl.setFaces(faces);
            }
            break;

        case ACTION_ON_COMPENSATION_CHANGED:
            if (mICameraView != null) {
                mICameraView.update(ORIENTATION_CHANGED, mIModuleCtrl.getOrientationCompensation());
            }
            break;

        case ACTION_ON_FULL_SCREEN_CHANGED:
            if (mICameraView != null) {
                mICameraView.update(ON_FULL_SCREEN_CHANGED, (Boolean) arg[0]);
            }
            break;

        case ACTION_SHUTTER_BUTTON_FOCUS:
            //when focus is detected, not need to do AF
            break;

        case ACTION_PHOTO_SHUTTER_BUTTON_CLICK:
            Log.i(TAG, "ACTION_PHOTO_SHUTTER_BUTTON_CLICK, mode state = " + getModeState());
            if (ModeState.STATE_CAPTURING != getModeState()) {
                // first need hide the effects item if the effects have expanded
                if (mICameraView != null) {
                    mICameraView.update(HIDE_EFFECTS_ITEM);
                }
                
                if(FeatureSwitcher.isRearCameraSub() == true){
	                if (mICameraView != null) {
	                    ((FaceBeautyView)mICameraView).showCameraLayoutCover();
	                }
	                mHandler.removeMessages(MSG_TAKEPICTURE);
	        	    mHandler.sendEmptyMessage(MSG_TAKEPICTURE);
                }else{
                    mIFocusManager.focusAndCapture();
                }
                
               
            }
            break;

        case ACTION_SHUTTER_BUTTON_LONG_PRESS:
        	Log.i(TAG, "[execute] ACTION_SHUTTER_BUTTON_LONG_PRESS");
            String showinfo = null;
            if (isLanguageOfEN()) {
                showinfo = mActivity.getString(R.string.continuous_not_supported);
            } else {
                showinfo = mActivity.getString(R.string.pref_camera_capturemode_enrty_fb) + mActivity.getString(R.string.camera_continuous_not_supported);
            }
            mICameraAppUi.showInfo(showinfo,3000,(int)mActivity.getResources().getDimension(R.dimen.info_facebeauty_bottom));
            break;

        case ACTION_ON_SINGLE_TAP_UP:
            Assert.assertTrue(arg.length == 3);
            onSinlgeTapUp((View) arg[0], (Integer) arg[1], (Integer) arg[2]);
            break;

        case ACTION_ON_BACK_KEY_PRESS:
            // need callback ,if true means not need activity action on-back
            // pressed
            // when just supported Cfb, so need the supper onbackpressed
            if (mICameraView != null && !mICameraView.update(ON_BACK_PRESSED)
                    || !mIFeatureConfig.isVfbEnable()) {
                // returnValue is false means need super action the
                // onBackPressed
                returnValue = false;
            }
            break;

        case ACTION_ON_SETTING_BUTTON_CLICK:
            // when user go to setting turn off the face beauty,before change
            // the setting we have hide the VFB UI, when the setting have
            // changed,this time not need show the UI
            if (mICameraView != null && !isVfbOff()) {
                mICameraView.update(ON_SETTING_BUTTON_CLICK, arg[0]);
            }
            break;

        case ACTION_ON_CONFIGURATION_CHANGED:
            if (mHandler != null) {
                mHandler.sendEmptyMessage(ON_CONFIGURATION_CHANGED);
            }
            break;

        case ACTION_ON_SELFTIMER_STATE:
            if (mICameraView != null) {
                mICameraView.update(ON_SELFTIMER_CAPTUEING, (Boolean) arg[0]);
            }
            break;
        case ACTION_MODE_BLUE_CAHNGE:
        	if(mIFeatureConfig.isVfbEnable() && ((CameraActivity)mActivity).isSwitchingCamera() == false){
        		mICameraView.hide(); 
        	}
            break;
        case ACTION_MODE_BLUE_DONE:
            mHandler.sendEmptyMessage(ACTION_MODE_BLUE_DONE);
            break;
        case ACTION_NAVIGAT_CHANGE:
        	Assert.assertTrue(arg.length == 1);
            if(mICameraView != null){
            	((FaceBeautyView)mICameraView).onChangeNavigationBar(((Boolean)arg[0]));
            }
        case ACTION_ONESHOT_PREVIEW_CALLBACK:
        	Log.i(TAG, "[execute] ACTION_ONESHOT_PREVIEW_CALLBACK");
        	
            break;
        /*prize-xuchunming-201804026-add spotlight-start*/ 
        case ACTION_SPOTLIGHT_VISIBLE:
            // when user go to setting turn off the face beauty,before change
            // the setting we have hide the VFB UI, when the setting have
            // changed,this time not need show the UI
            if (mICameraView != null && !isVfbOff()) {
                mICameraView.update(ON_SPOTLIGHT_VISIBLE, arg[0]);
            }
            break;
         /*prize-xuchunming-201804026-add spotlight-end*/ 
            
        /*prize-xuchunming-20180523-add shortcuts-start*/
        case ACTION_ON_CAMERA_FIRST_OPEN_DONE:
        	if(mICameraView != null) {
        		mICameraView.show();
        	}
            break;
        /*prize-xuchunming-20180523-add shortcuts-end*/
        default:
            return false;
        }

        if (ActionType.ACTION_FACE_DETECTED != type) {
            Log.i(TAG, "[execute]type =" + type + ",returnValue = " + returnValue);
        }
        return returnValue;
    }

    @Override
    public boolean open() {
        mAdditionManager.setListener(this);
        mAdditionManager.open(true);
        super.open();
        return true;
    }

    //------> ICameraAddition.Listener
    @Override
    public boolean capture() {
        Log.i(TAG, "[capture]...");
        startCapture();
        return true;
    }

    @Override
    public boolean restartPreview(boolean needStop) {
        return false;
    }
    
    @Override
    public void onFileSaveing() {
    }
    //<------ICameraAddition.Listener

    //------> IFocusManager.FocusListener
    @Override
    public void autoFocus() {
        Log.i(TAG, "[autoFocus]...");
        mICameraDevice.autoFocus(mAutoFocusCallback);
        mICameraAppUi.setViewState(ViewState.VIEW_STATE_FOCUSING);
        setModeState(ModeState.STATE_FOCUSING);
    }

    @Override
    public void cancelAutoFocus() {
        Log.i(TAG, "[cancelAutoFocus]...");
        mICameraDevice.cancelAutoFocus();
        setFocusParameters();
        /*prize-add-bugid:44263 can not switch the camera mode during the delay photo mode -xiaoping-20171205-start*/
        if (!mISelfTimeManager.isSelfTimerCounting() && ModeState.STATE_CAPTURING != getModeState()) {
            mICameraAppUi.restoreViewState();
            setModeState(ModeState.STATE_IDLE);
        }
        /*prize-add-bugid:44263 can not switch the camera mode during the delay photo mode -xiaoping-20171205-end*/
    }

    @Override
    public void startFaceDetection() {
        Log.i(TAG, "[startFaceDetection]...");
        mIModuleCtrl.startFaceDetection();
    }

    @Override
    public void stopFaceDetection() {
        Log.i(TAG, "[stopFaceDetection]...");
        mIModuleCtrl.stopFaceDetection();
    }

    @Override
    public void setFocusParameters() {
        Log.i(TAG, "[setFocusParameters]mIsAutoFocusCallback = " + mIsAutoFocusCallback);
        mIModuleCtrl.applyFocusParameters(!mIsAutoFocusCallback);
        mIsAutoFocusCallback = false;
    }

    @Override
    public void playSound(int soundId) {
    	/*prize Camera mute wanzhijuan 2016-5-30 start*/
    	if (isCameraUnMute()) {
    		mCameraSound.play(soundId);
    	}
    	/*prize Camera mute wanzhijuan 2016-5-30 end*/
    }

    // <------IFocusManager.FocusListener

    private void removeAllMsg() {
        if (mHandler != null) {
            mHandler.removeMessages(ON_CAMERA_PARAMETERS_READY);
            mHandler.removeMessages(INFO_FACE_DETECTED);
            mHandler.removeMessages(ORIENTATION_CHANGED);
            mHandler.removeMessages(RE_OPEN_GESTURE_SHOT);
        }
    }

    private class MainHandler extends Handler {

        public MainHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            Log.i(TAG, "[handleMessage],msg = " + msg.what);

            switch (msg.what) {
            // this msg just used for VFB,so if you want use cFB,please be
            // careful
            case ON_CAMERA_PARAMETERS_READY:
                if (mICameraView != null) {
                    mICameraView.update(ON_CAMERA_PARAMETERS_READY);
                }
                
                // because you have call stopFD,so PhotoActor/3rd party maybe
                // have clear the face view
                // so you need initialize yourself
                mIModuleCtrl.initializeFrameView(false);
                setVFBParameters();
                //after set the parameters to native ,enable face beauty frame
                changeFaceBeautyStatues(true);

                break;

            case ON_LEAVE_FACE_BEAUTY_MODE:
                if (mICameraView != null) {
                    mICameraView.update(ON_LEAVE_FACE_BEAUTY_MODE);
                }
                break;

            case ON_CONFIGURATION_CHANGED:
                // because configuration change,so need re-inflate the view
                // layout
               /* if (mICameraView != null) {
                    mICameraView.uninit();
                    mICameraView.init(mActivity, mICameraAppUi, mIModuleCtrl);
                }*/
                break;

            case RE_OPEN_GESTURE_SHOT:
                mICameraDevice.startGestureDetection();
                Log.i(TAG, "[handleMessage],end of re-startGD ");
                break;
            case ACTION_MODE_BLUE_DONE:
            	 if (mIFeatureConfig.isVfbEnable()) {
            		 mICameraView.show();
            	 }
                break;
            case MSG_TAKEPICTURE:
            	  /*prize-xuchunming-20170119-bug:picture blur-start*/
            	if(mIFocusManager != null){
            		if(FeatureSwitcher.isRearCameraSub() == true){
                		mISettingCtrl.onSettingChanged(SettingConstants.KEY_CAMERA_ZSD, "off");
                       ((CameraActivity)mActivity).applyParametersToServer();
                       startPreview(true);
                       mIFocusManager.focusAndCapture();
                	}
            	}
                /*prize-xuchunming-20170119-bug:picture blur-end*/
               
               break;  
            case MSG_HIDE_COVER:
            	 if (mICameraView != null) {
            		 if(((FaceBeautyView)mICameraView).isBeautyLayoutCoverShow()){
            			 ((FaceBeautyView)mICameraView).hideCameraLayoutCover();
                        //mICameraAppUi.restoreViewState();
            		 }
                 }
              break;
            case MSG_SHOW_ANIM_COVER:
                //prize-add-for fb animation cover, make it show only under front cam && ratio is 4:3 scenario-20170318-pengcancan-start
				if (mISettingCtrl != null && mISettingCtrl.getSettingValue(SettingConstants.KEY_CAMERA_ZSD) != null
						&& mISettingCtrl.getSettingValue(SettingConstants.KEY_CAMERA_ZSD).equals("off")) {
					if (mICameraView != null) {
						((FaceBeautyView) mICameraView).showCameraLayoutCover();
					}
				}

				if (mICameraView != null && mICameraDevice.getCameraId() == 1 && mISettingCtrl != null && mISettingCtrl.getSettingValue(SettingConstants.KEY_PICTURE_RATIO) != null) {
					((FaceBeautyView) mICameraView).startFbCoverAnimation();
				}
                //prize-add-for fb animation cover, make it show only under front cam && ratio is 4:3 scenario-20170318-pengcancan-end
                break;
            default:
                break;
            }
        }
    }

    private void setVFBParameters() {
        if (mICameraDevice != null && mIModuleCtrl.isNonePickIntent()
                && mIFeatureConfig.isVfbEnable()) {
            // if face-beauty value is false,means current need to start VFB
            Log.d(TAG, "face-beauty will set to :ture");
            if (isOnlyMultiFaceBeautySupported()) {
                mICameraDevice.setParameter(Util.KEY_FB_EXTREME_BEAUTY, "false");
            }
            setVFBPs(true);
        }
    }

    private void changeFaceBeautyStatues(boolean enable) {
        /**
         * true : means will set face beauty view to true, so the UI will show a
         * face beauty frame false: means will not show the full color face
         * frame,just is white color
         */
        Log.i(TAG, "[changeFaceBeautyStatues] enable = " + enable);
        mIModuleCtrl.setFaceBeautyEnalbe(enable);
    }

    private boolean isOnlyMultiFaceBeautySupported() {
        // fb-extreme-beauty-supported is false means just supported Multi face
        // mode,
        // so need remove the single face mode in settings
        //boolean isOnlySupported = "false".equals(mICameraDevice.getParameters().get(
        //        Util.KEY_FB_EXTEME_BEAUTY_SUPPORTED));
        boolean isOnlySupported = "false".equals(mICameraDevice
                .getParameter(Util.KEY_FB_EXTEME_BEAUTY_SUPPORTED));
        Log.i(TAG, "isOnlyMultiFaceBeautySupported = " + isOnlySupported);
        return isOnlySupported;
    }

    private void setVFBPs(boolean isStart) {
       /*prize-xuchunming-20160602-cancle mtk vfb-start*/
      /*  String value = isStart ? Util.VIDEO_FACE_BEAUTY_ENABLE : Util.VIDEO_FACE_BEAUTY_DISABLE;
        Log.i(TAG, "[setVFBPs] isStart = " + isStart + ",value = " + value);
        mICameraDevice.setParameter(Util.KEY_VIDEO_FACE_BEAUTY, value);
        // before set the ps,need update the ps
        mICameraDevice.applyParameters();*/
    	/*prize-xuchunming-20160602-cancle mtk vfb-end*/
    }

    private void setFBLowRamRule() {
        FaceBeautyPictureSizeRule pictureSizeRule = new FaceBeautyPictureSizeRule(mISettingCtrl,
                mICameraContext);
        mISettingCtrl.addRule(SettingConstants.KEY_FACE_BEAUTY, SettingConstants.KEY_PICTURE_SIZE,
                pictureSizeRule);
        pictureSizeRule.addLimitation("on", null, null);
    }

    private void setFBGmoRule() {
        FaceBeautyZsdRule zsdRule = new FaceBeautyZsdRule(mISettingCtrl, mICameraContext);
        mISettingCtrl.addRule(SettingConstants.KEY_FACE_BEAUTY, SettingConstants.KEY_CAMERA_ZSD,
                zsdRule);
        zsdRule.addLimitation("on", null, null);
    }

    private void setCfbSlowMotionRule(){
        CaptureFaceBeautyRule slowMotionRule = new CaptureFaceBeautyRule(mICameraContext);
        mISettingCtrl.addRule(SettingConstants.KEY_FACE_BEAUTY,
                SettingConstants.KEY_SLOW_MOTION, slowMotionRule);
        slowMotionRule.addLimitation("on", null, null);
    }
    
    private void setVFBPreviewSizeRule() {
        mFaceBeautyPreviewSize = new FaceBeautyPreviewSize(mICameraContext);
        mISettingCtrl.addRule(SettingConstants.KEY_FACE_BEAUTY, SettingConstants.KEY_PICTURE_RATIO,
                mFaceBeautyPreviewSize);
        mFaceBeautyPreviewSize.addLimitation("on", null, null);
    }

    private void onShutterButtonLongPress() {
        mICameraAppUi.showInfo(mActivity.getString(R.string.pref_camera_capturemode_enrty_fb)
                + mActivity.getString(R.string.camera_continuous_not_supported));
    }

    private void onSinlgeTapUp(View view, int x, int y) {
        Log.i(TAG, "[onSingleTapUp] mIFocusManager = " + mIFocusManager);
        if (ModeState.STATE_IDLE != getModeState()) {
            Log.i(TAG, "[onSingleTapUp] current state is = " + getModeState() + " ,so returen");
            return;
        }
        String focusMode = null;
        if (mIFocusManager != null) {
            focusMode = mIFocusManager.getFocusMode();
            Log.i(TAG, "[onSingleTapUp] current focusMode = " + focusMode);
        }
        if (mICameraDevice == null || focusMode == null
                || (Parameters.FOCUS_MODE_INFINITY.equals(focusMode))) {
            Log.i(TAG, "[onSinlgeTapUp]mICameraDevice = " + mICameraDevice + ",focusMode = "
                    + focusMode);
            /*prize Front camera touch screen camera wanzhijuan 2016-5-30 start*/
            checkFrontTouchShutter();
            /*prize Front camera touch screen camera wanzhijuan 2016-5-30 end*/
            return;
        }
        if (!mIFocusManager.getFocusAreaSupported()) {
            Log.i(TAG, "[onSinlgeTapUp]this project not supported Touch AF");
            /*prize Front camera touch screen camera wanzhijuan 2016-5-30 start*/
            checkFrontTouchShutter();
            /*prize Front camera touch screen camera wanzhijuan 2016-5-30 end*/
            return;
        }
        if (mICameraView != null) {
            mICameraView.update(HIDE_EFFECTS_ITEM);
        }
        mIFocusManager.onSingleTapUp(x, y);
        Log.i(TAG, "[onSingleTapUp] end ");
    }

    private boolean startCapture() {
        if ((ModeState.STATE_IDLE != getModeState()) || !isEnoughSpace()) {
            Log.i(TAG, "[startCapture],invalid state, return!");
            return false;
        }
        Log.i(TAG, "[startCapture]...");
        if (mICameraView != null) {
            mICameraView.update(IN_PICTURE_TAKEN_PROGRESS, true);
        }
        // need set FB original capture callback and here
        mICameraAppUi.setSwipeEnabled(false);
        mICameraAppUi.showRemaining();
        mICameraAppUi.setViewState(ViewState.VIEW_STATE_CAPTURE);
  
         /*prize-xuchunming-20160602-cancle mtk vfb-start*/
        //mICameraDevice.setcFBOrignalCallback(mCfbCallback);
        /*prize-xuchunming-20160602-cancle mtk vfb-end*/
        if (mIFeatureConfig.isVfbEnable()) {
            // because native not know the face beauty location when take
            // picture,
            // so need set the beauty location
        	/*prize-xuchunming-20160602-cancle mtk vfb-start*/
            //setvFBFacePoints();
        	/*prize-xuchunming-20160602-cancle mtk vfb-end*/
        }
        setModeState(ModeState.STATE_CAPTURING);
        mHandler.removeMessages(MSG_SHOW_ANIM_COVER);
        mHandler.removeMessages(MSG_HIDE_COVER);
        mHandler.sendEmptyMessage(MSG_SHOW_ANIM_COVER);
        mICameraDevice.takePicture(mShutterCallback, null, null,
                mJpegPictureCallback);

        return true;
    }

    private ShutterCallback mShutterCallback = new ShutterCallback() {

        @Override
        public void onShutter() {
            Log.d(TAG, "[mShutterCallback], time = " + System.currentTimeMillis());

        }
    };

    private PictureCallback mRawPictureCallback = new PictureCallback() {

        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            Log.d(TAG, "[mRawPictureCallback], time = " + System.currentTimeMillis());

        }
    };

    private PictureCallback mPostViewPictureCallback = new PictureCallback() {

        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            Log.d(TAG, "[mPostViewPictureCallback], time = " + System.currentTimeMillis());

        }
    };

    private final PictureCallback mUncompressedImageCallback = new PictureCallback() {
        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            Log.i(TAG, "[UncompressedImageCallback]onCanCapture");
            ++mCapturedImageCount;
            startPreview(false);
        }
    };

    private PictureCallback mJpegPictureCallback = new PictureCallback() {

        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            Log.d(TAG, "VFBCallback[mJpegPictureCallback], time = " + System.currentTimeMillis()
                    + ",data = " + data);

            if (ModeState.STATE_CLOSED != getModeState()) {
                // Ensure focus indicator
                mIFocusManager.updateFocusUI();
                // Need Restart preview ,synchronize Normal photo
                // ZSD don't call stop preview when Capture done.
                boolean needRestartPreivew = !"on".equals(mISettingCtrl
                        .getSettingValue(SettingConstants.KEY_CAMERA_ZSD))
                        && (ModeState.STATE_CAPTURING == getModeState()
                        && (mCapturedImageCount == 0));
                /*prize-xuchunming-20170119-bug:picture blur-start*/
                if(FeatureSwitcher.isRearCameraSub() == true){
	                mISettingCtrl.onSettingChanged(SettingConstants.KEY_CAMERA_ZSD, "on");
	                ((CameraActivity)mActivity).applyParametersToServer();
                }
                /*prize-xuchunming-20170119-bug:picture blur-end*/
                startPreview(needRestartPreivew);
                ((CameraActivity)mActivity).setOneShotPreviewCallback();
                mIModuleCtrl.startFaceDetection();
                // check due to the onCanCapture don't send to application in some condition.
                if (mCapturedImageCount > 0) {
                    --mCapturedImageCount;
                }
                boolean isGestureShotOn = "on".equals(mISettingCtrl
                        .getSettingValue(SettingConstants.KEY_GESTURE_SHOT));
                Log.i(TAG, "[onPictureTaken] isGestureShotOn = " + isGestureShotOn);
                if (isGestureShotOn) {
                    mHandler.sendEmptyMessageAtTime(RE_OPEN_GESTURE_SHOT, RE_OPEN_GESTURE_SHOT_TIME);
                }
            }
            if (mICameraView != null) {
                mICameraView.update(IN_PICTURE_TAKEN_PROGRESS, false);
            }
            if (data != null) {
                // prepare the save request
                mIFileSaver.init(FILE_TYPE.JPEG, 0, null, -1);
                long time = System.currentTimeMillis();
                mIFileSaver.savePhotoFile(data, null, time, mIModuleCtrl.getLocation(),
                        0, mFileSavedListener);
            }
            mHandler.removeMessages(MSG_HIDE_COVER);
            mHandler.sendEmptyMessage(MSG_HIDE_COVER);
        }
    };

    private void storeFaceBeautyLocation(Face[] faces) {
        int index = 0;
        // First :clear last time stored values
        if (mVfbFacesPoint != null && mVfbFacesPoint.size() != 0) {
            mVfbFacesPoint.clear();
        }
        // Seconded: store the new values to the list
        if (faces != null) {
            for (int i = 0; i < faces.length; i++) {
                if (100 == faces[i].score) {
                    int x = faces[i].rect.left + (faces[i].rect.right - faces[i].rect.left) / 2;
                    int y = faces[i].rect.top + (faces[i].rect.bottom - faces[i].rect.top) / 2;
                    mVfbFacesPoint.add(index++, x);
                    mVfbFacesPoint.add(index++, y);
                }
            }
        }
    }

    private void setvFBFacePoints() {
        if (mVfbFacesPoint == null) {
            Log.i(TAG, "[vFB] mVfbFacesPoint,current points is null,return");
            return;
        }
        String value = setFacePose();
        if (value != null) {
            mICameraDevice.setParameter(FaceBeautyParametersHelper.KEY_VIDED_FACE_BEAUTY_FACE,
                    value);
            mICameraDevice.applyParameters();
        }
    }

    private String setFacePose() {
        String value = "";
        for (int i = 0; i < mVfbFacesPoint.size(); i++) {
            value += mVfbFacesPoint.get(i);
            // why need (i +1) != mvFBFacesPoint.size() ?
            // because at the end of value,not need any symbol
            // the value format is: xxx:yyy,x1:y1
            if ((i + 1) != mVfbFacesPoint.size()) {
                if (i % 2 != 0) {
                    value += ",";
                } else {
                    value += ":";
                }
            }
        }
        Log.i(TAG, "[vFB] setFacePose,vaue = " + value);
        return value;
    }

    private OnFileSavedListener mFileSavedListener = new OnFileSavedListener() {
        @Override
        public void onFileSaved(Uri uri) {
            Log.i(TAG, "[onFileSaved]uri = " + uri);
        }
    };

    // CFB
    public class CfbCallback implements cFbOriginalCallback {

        @Override
        public void onOriginalCallback(byte[] data) {
            Log.d(TAG, "cFBCallback,[onOriginalCallback],data.length = " + data.length);
            if (!mIFeatureConfig.isVfbEnable()) {
                mIFileSaver.init(FILE_TYPE.JPEG, 0, null, -1);
                long time = System.currentTimeMillis();
                mIFileSaver.savePhotoFile(data, null, time, mIModuleCtrl.getLocation(), 0,
                        mFileSavedListener);
            }
        }
    }

    private boolean isVfbOff() {
        boolean isOff = mActivity.getResources().getString(R.string.pref_face_beauty_mode_off)
                .equals(mISettingCtrl.getSettingValue(SettingConstants.KEY_MULTI_FACE_BEAUTY));

        Log.i(TAG, "VFB isOff = " + isOff);

        return isOff;
    }


    private final AutoFocusCallback mAutoFocusCallback = new AutoFocusCallback() {

        @Override
        public void onAutoFocus(boolean success, Camera camera) {
            Log.i(TAG, "[onAutoFocus] success = " + success +",current state = " + getModeState());
            if (ModeState.STATE_CLOSED == getModeState()) {
                Log.i(TAG, "[onAutoFocus]camera is closed,so return");
                return;
            }
            if (ModeState.STATE_FOCUSING == getModeState()) {
                mICameraAppUi.restoreViewState();
                setModeState(ModeState.STATE_IDLE);
            }
            mIFocusManager.onAutoFocus(success);
            mIsAutoFocusCallback = true;
        }
    };

    private final AutoFocusMvCallback mAutoFocusMvCallback = new AutoFocusMvCallback() {

        @Override
        public void onAutoFocusMoving(boolean start, Camera camera) {
            Log.i(TAG, "[onAutoFocusMoving]moving = " + start);
            mIFocusManager.onAutoFocusMoving(start);
        }

    };

    private void startPreview(boolean needStop) {
        Log.i(TAG, "[startPreview]needStop = " + needStop);

        mIsAutoFocusCallback = false;

        mActivity.runOnUiThread(new Runnable() {
            public void run() {
                mIFocusManager.resetTouchFocus();
            }
        });
        if (needStop) {
            stopPreview();
        }
        mIFocusManager.setAeLock(false); // Unlock AE and AWB.
        mIFocusManager.setAwbLock(false);

        mIModuleCtrl.applyFocusParameters(false);
        Log.i(TAG, "set setFocusParameters normal");

        mICameraDevice.startPreview();
        /*prize-xuchunming-20170322-bugid:30303-start*/
        if(mICameraAppUi.getViewState() != ViewState.VIEW_STATE_SHOW_BLUR){
        	mICameraAppUi.restoreViewState();
        }
        /*prize-xuchunming-20170322-bugid:30303-end*/
        mICameraDevice.setAutoFocusMoveCallback(mAutoFocusMvCallback);
        mIFocusManager.onPreviewStarted();
        // when preview is started,need notify current additional manager,
        // such as GS will set setSelfTimerDuration to zero
        // otherwise when take picture again after the Gesture shot,will
        // found there have a gesture shot sound and view by click shutter
        // button. ALPS01837639
        mAdditionManager.execute(AdditionActionType.ACTION_ON_START_PREVIEW);
        setModeState(ModeState.STATE_IDLE);
        mICameraDevice.setUncompressedImageCallback(mUncompressedImageCallback);
    }

    private void stopPreview() {
        Log.i(TAG, "[stopPreview]mCurrentState = " + getModeState());
        if (ModeState.STATE_CLOSED == getModeState()) {
            Log.i(TAG, "[stopPreview]Preview is stopped.");
            return;
        }
        mICameraDevice.cancelAutoFocus(); // Reset the focus.
        mICameraDevice.stopPreview();
        if (mIFocusManager != null) {
            mActivity.runOnUiThread(new Runnable() {
                public void run() {
                    mIFocusManager.onPreviewStopped();
                }
            });
        }
    }
    
    public void deleteSettingRule(){
    	mFlashRule = mISettingCtrl.getRule(SettingConstants.KEY_FLASH, SettingConstants.KEY_HDR);
    	mISettingCtrl.addRule(SettingConstants.KEY_FLASH, SettingConstants.KEY_HDR, null);
    }
    
    public void resetoreSettingRule(){
    	mISettingCtrl.addRule(SettingConstants.KEY_FLASH, SettingConstants.KEY_HDR, mFlashRule);
    }

    private boolean isLanguageOfEN() {
        Locale locale = mActivity.getResources().getConfiguration().locale;
        return locale.getLanguage().endsWith("en");
    }
}
