package com.android.camera;

import com.android.camera.R;

import android.R.bool;
import android.R.integer;
import android.app.ActivityManager;
import android.app.Service;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.graphics.Color;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Face;
import android.hardware.Camera.Parameters;
import android.location.Location;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.MessageQueue;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.Vibrator;
import android.os.storage.StorageVolume;
import android.provider.MediaStore;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import com.android.camera.GestureDispatcher.GestureDispatcherListener;
import com.android.camera.actor.CameraActor;
import com.android.camera.actor.PhotoActor;
import com.android.camera.actor.VideoActor;
import com.android.camera.bridge.CameraAppUiImpl;
import com.android.camera.bridge.CameraDeviceCtrl;
import com.android.camera.bridge.CameraDeviceCtrl.PreviewCallbackListen;
import com.android.camera.bridge.CameraDeviceManagerImpl;
import com.android.camera.bridge.FeatureConfigImpl;
import com.android.camera.bridge.FileSaverImpl;
import com.android.camera.bridge.SelfTimerManager;
import com.android.camera.externaldevice.ExternalDeviceManager;
import com.android.camera.externaldevice.IExternalDeviceCtrl;
import com.android.camera.manager.FrameManager;
import com.android.camera.manager.GridManager;
import com.android.camera.manager.ModePicker;
import com.android.camera.manager.PickerManager;
import com.android.camera.manager.SettingManager;
import com.android.camera.manager.ViewManager;
import com.android.camera.permission.PermissionManager;
import com.android.camera.ui.FrameView;
import com.android.camera.ui.PreviewFrameLayout;
import com.android.camera.ui.PreviewSurfaceView;
import com.android.camera.ui.RotateLayout;
import com.android.internal.view.RotationPolicy;
import com.mediatek.camera.ICameraMode.CameraModeType;
import com.mediatek.camera.ICameraMode.ModeState;
import com.mediatek.camera.ISettingCtrl;
import com.mediatek.camera.ModuleManager;
import com.mediatek.camera.mode.gyfacebeauty.GyBokehMode;
import com.mediatek.camera.platform.ICameraAppUi;
import com.mediatek.camera.platform.ICameraAppUi.CommonUiType;
import com.mediatek.camera.platform.ICameraAppUi.GestureListener;
import com.mediatek.camera.platform.ICameraAppUi.ViewState;
import com.mediatek.camera.platform.ICameraDeviceManager;
import com.mediatek.camera.platform.IFeatureConfig;
import com.mediatek.camera.platform.IFileSaver;
import com.mediatek.camera.platform.IModuleCtrl;
import com.mediatek.camera.platform.ISelfTimeManager;
import com.mediatek.camera.setting.ParametersHelper;
import com.mediatek.camera.setting.SettingConstants;
import com.mediatek.camera.setting.SettingUtils;
import com.mediatek.camera.setting.preference.ListPreference;
import com.mediatek.camera.util.CameraPerformanceTracker;
import com.mediatek.common.prizeoption.PrizeOption;
import com.prize.setting.BDLocationManager;
import com.prize.setting.NavigationBarUtils;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
/*prize-xuchunming-20180411-bugid:50825-start*/
import android.view.WindowManagerPolicy;

import static com.baidu.location.b.g.r;
import static com.baidu.location.f.mC;
/*prize-xuchunming-20180411-bugid:50825-end*/
// Nav bar color customized feature. prize-linkh-2017.08.28 @{
// @}

/*
 * --ActivityBase
 * ----Camera //will control CameraActor, ModePicker, CameraPicker, SettingChecker,
 *      FocusManager, RemainingManager
 * --CameraActor
 * ------VideoActor
 * ------PhotoActor
 * --------NormalActor //contains continuous shot
 * --------HdrActor
 * --------FaceBeautyActor
 * --------AsdActor
 * --------SmileShotActor
 * --------PanaromaActor
 */
public class CameraActivity extends ActivityBase implements
        PreviewFrameLayout.OnSizeChangedListener,
        ActivityCompat.OnRequestPermissionsResultCallback {
    private static final String TAG = "CameraActivity";
    private static final String SETTING_ON = "on";
    private static final String SETTING_OFF = "off";
    public static final int UNKNOWN = -1;
    public static final int STATE_PREVIEW_STOPPED = 0;
    public static final int STATE_IDLE = 1; // preview is active
    // ocus is in progress. The exact focus state is in Focus.java.
    public static final int STATE_FOCUSING = 2;
    public static final int STATE_SNAPSHOT_IN_PROGRESS = 3;
    public static final int STATE_RECORDING_IN_PROGRESS = STATE_SNAPSHOT_IN_PROGRESS;
    public static final int STATE_SWITCHING_CAMERA = 4;

    private static final int MSG_CAMERA_PARAMETERS_READY = 2;
    private static final int MSG_CHECK_DISPLAY_ROTATION = 4;
    private static final int MSG_SWITCH_CAMERA = 5;
    private static final int MSG_CLEAR_SCREEN_DELAY = 7;
    private static final int MSG_APPLY_PARAMETERS_WHEN_IDEL = 12;
    private static final int MSG_DELAY_SHOW_ONSCREEN_INDICATOR = 16;
    private static final int MSG_UPDATE_SWITCH_ACTOR_STATE = 17;

    // add for stereo Camera features type
    private static final int DUAL_CAMERA_START = 1;
    private static final int DUAL_CAMERA_ENHANCE_ENABLE = 3;
    private static final int DUAL_CAMERA_ENHANCE_DISABLE = 4;
    private static final int DUAL_CAMERA_SWITCH_IN_REFOCUS = 2;
    private static final int ON = 0;
    private static final int OFF = 1;

    private static final int DELAY_MSG_SCREEN_SWITCH = 2 * 60 * 1000;
    private static final int SHOW_INFO_LENGTH_LONG = 5 * 1000;
    // This is the timeout to keep the camera in onPause for the first time
    // after screen on if the activity is started from secure lock screen.
    private static final int KEEP_CAMERA_TIMEOUT = 1000; // ms

    private int mDelayOtherMessageTime;
    private int mCameraState = STATE_PREVIEW_STOPPED;

    private int mNumberOfCameras;
    private int mPendingSwitchCameraId = UNKNOWN;
    private ActivityManager mActivityManager;
    private int mDisplayRotation;
    private int mOrientation = 0;
    private int mOrientationCompensation = 0;
    // TODO
    private long mOnResumeTime;

    private boolean mIsModeChanged;
    private boolean mIsAppGuideFinished;
    private boolean mNeedRestoreIfOpenFailed = false;
    protected boolean mIsStereoMode = false;

    private ISettingCtrl mISettingCtrl;

    private CameraActor mCameraActor;
    private PreviewFrameLayout mPreviewFrameLayout;
    private MyOrientationEventListener mOrientationListener;
    private ModePicker mModePicker;
    private FileSaver mFileSaver;
    private FrameManager mFrameManager;
    private RotateLayout mFocusAreaIndicator;
    private ComboPreferences mPreferences;
    /*PRIZE-modify geographical position-wanzhijuan-2016-05-03-start*/
    private BDLocationManager mLocationManager;
    /*PRIZE-modify geographical position-wanzhijuan-2016-05-03-end*/
    private PermissionManager mPermissionManager;
    // Gesture
    private GestureRecognizer mGestureRecognizer;
    private GestureDispatcher mGestureDispatcher;
    private PowerManager mPowerManager;
    private Vibrator mVibrator;
    private CharSequence mDelayShowInfo;
    private CameraAppUiImpl mCameraAppUi;
    private ISelfTimeManager mISelfTimeManager;
    private ModuleManager mModuleManager;
    private CameraDeviceCtrl mCameraDeviceCtrl;
    private ExternalDeviceManager mOtherDeviceConectedManager;

    private int mNextMode = ModePicker.MODE_PHOTO;
    private int mPrevMode = ModePicker.MODE_PHOTO;
    private boolean mIsBackPressed = false;
    private boolean mIsAPI2Inited = false;
	Handler mHandler;
	int EXIT_APP_TIMER=2*60*1000;
    private boolean mIsCheckingLocationPermission = false;
    //prize-add for showing the camera user is switching to -pengcancna-20161210-start
    private boolean isSwitchingCameraDone = true;
    /*prize-xuchunming-20180411-bugid:50825-start*/
    private int mCameraId =0;
    /*prize-xuchunming-20180411-bugid:50825-end*/
    public boolean isCameraSwitchingDone(){
        return isSwitchingCameraDone;
    }
    //prize-add for showing the camera user is switching to -pengcancna-20161210-end
	
	
	//prize-public-bug:12376,No hint while using flash when the battery level is under 15%-pengcancan-20160226-start
    private final Runnable toastRunnable = new Runnable() {
		
		@Override
		public void run() {
			if(getCurrentMode() == ModePicker.MODE_FACE_BEAUTY || getCurrentMode() == ModePicker.MODE_PORTRAIT) {
				mCameraAppUi.showInfo(getString(R.string.flash_failed_when_battery_is_low),5 * 1000,(int)getResources().getDimension(R.dimen.info_facebeauty_bottom));
			}else {
				mCameraAppUi.showInfo(getString(R.string.flash_failed_when_battery_is_low),5 * 1000,(int)getResources().getDimension(R.dimen.info_bottom));
			}
			/*prize-xuchunming-20180503-bugid:56870-start*/
			notifyLowPower(true);
			/*prize-xuchunming-20180503-bugid:56870-end*/
		}
	};
    
    private boolean isBatteryLow = false;
    public boolean isBatteryLow() {
		return isBatteryLow;
	}
	private IntentFilter intentFilter;	
	
    private BroadcastReceiver batteryStatusBR = new BroadcastReceiver() {		
		@Override
		public void onReceive(Context context, Intent intent) {
			if (Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) {
				int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
				int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
				int batteryPct = (int) (level * 100 / (float)scale);
                //prize-public-bug:15309 forbid using camera when the power is low-pengcancan-20160426-start
				if (batteryPct <= 5) {/*
                    prize-modify-bugid:44267 lock screen interface does not pop up prompts-xiaoping-20171207-start
                    Toast toast = new Toast(CameraActivity.this);
                    WindowManager.LayoutParams layoutParams = toast.getWindowParams();
                    layoutParams.type = WindowManager.LayoutParams.TYPE_STATUS_BAR_PANEL;
                    View view = LayoutInflater.from(CameraActivity.this).inflate(R.layout.prize_lowpower_exit_toast, null);
                    toast.setView(view);
                    toast.setDuration(Toast.LENGTH_SHORT);
                    toast.setGravity(Gravity.BOTTOM, 0, (int)getResources().getDimension(R.dimen.combinview_back_camera_marginend));
                    toast.show();
                    prize-modify-bugid:44267 lock screen interface does not pop up prompts-xiaoping-20171207-end
					CameraActivity.this.finish();
				*/}
				if (batteryPct <= 15) {
					if (!isBatteryLow) {
						mMainHandler.postDelayed(toastRunnable, 1000);
					}					
					isBatteryLow = true;
				}else {
					/*prize-xuchunming-20180503-bugid:56870-start*/
					if(isBatteryLow == true){
						notifyLowPower(false);
					}
					/*prize-xuchunming-20180503-bugid:56870-end*/
					isBatteryLow = false;
				}
				
			}
		}
	};
	//prize-public-bug:12376,No hint while using flash when the battery level is under 15%-pengcancan-20160226-end 
	
    private ContentProviderClient mMediaProviderClient;
    //prize-public-bug:cannot open camera while TORCH APP is on-pengcancan-20160129-start
    //private Uri uri = Uri.parse("content://com.android.flash/systemflashs");
    public void closeFlashLight(){
    	try{
            //prize-modify-by-zhongweilin-start
            int isSystemFlashOn = Settings.System.getInt(getContentResolver(), Settings.System.PRIZE_FLASH_STATUS, -1);
            if (isSystemFlashOn == 3 || isSystemFlashOn == 1 || isSystemFlashOn == 0) {
                /*prize-bugid: 33953-liufan-2017-05-23-start*/
                new Thread(){
                    public void run(){
                        try{
                            Thread.sleep(50);
                        } catch(Exception e){
                        }
                        Settings.System.putInt(getContentResolver(), Settings.System.PRIZE_FLASH_STATUS, 4);
                    }
                }.start();
                /*prize-bugid: 33953-liufan-2017-05-23-end*/
            }
    		/*ContentValues values = new ContentValues();  
            values.put("flashstatus","2"); 
    		getContentResolver().update(uri, values, null, null);*/
            //prize-modify-by-zhongweilin-end
    	}catch(Exception e){
    		Log.d(TAG, "closeFlashLight error");
    	}
    }
	 private boolean isOncreate = true;
    // PreviewFrameLayout size has changed.implement
    // PreviewFrameLayout.OnSizeChangedListener @{
    @Override
    public void onSizeChanged(int width, int height) {
        if (FeatureSwitcher.isApi2Enable(this)) {
            return;
        }
        mCameraDeviceCtrl.onSizeChanged(width, height);
        /*PRIZE-12383-wanzhijuan-2016-03-02-start*/
        //mCameraAppUi.onSizeChanged(width, height);
        /*PRIZE-12383-wanzhijuan-2016-03-02-end*/
    }

    // @}

    @Override
    protected ICameraActivityBridge getCameraActivityBridge() {
        if (mCameraActivityBridge == null) {
            mCameraActivityBridge = CameraActivityBridgeFactory
                    .getCameraActivityBridge(this);
        }
        return mCameraActivityBridge;
    }

    @Override
    public void onCreate(Bundle icicle) {
        CameraPerformanceTracker.onEvent(TAG,
                CameraPerformanceTracker.NAME_CAMERA_ON_CREATE,
                CameraPerformanceTracker.ISBEGIN);
        super.onCreate(icicle);
        Log.i(TAG, "onCreate start");
        /*prize-bugid: 33953-liufan-2017-05-23-start*/
        closeFlashLight();
        /*prize-bugid: 33953-liufan-2017-05-23-end*/
        mHasNavigationBar = NavigationBarUtils.checkDeviceHasNavigationBar(this);
        mPermissionManager = new PermissionManager(this);
        systemUIMode();
        if (FeatureSwitcher.isApi2Enable(this)) {
            getCameraActivityBridge().onCreate(icicle);
            return;
        }
        mActivityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        mPreferences = new ComboPreferences(this, isSecureCamera());
        Storage.setContext(this);
        SettingUtils.resetCameraId(mPreferences.getGlobal());
        /*prize-xuchunming-20180523-add shortcuts-start*/
        parseIntent();
        /*prize-xuchunming-20180523-add shortcuts-end*/
        mCameraDeviceCtrl = new CameraDeviceCtrl(this, mPreferences);

        if (mPermissionManager.requestCameraLaunchPermissions()) {
            mCameraDeviceCtrl.openCamera();
        }

        ModuleCtrlImpl moduleCtrl = new ModuleCtrlImpl(this);
        CameraPerformanceTracker.onEvent(TAG,
                CameraPerformanceTracker.NAME_CAMERA_INIT_VIEW_MANAGER,
                CameraPerformanceTracker.ISBEGIN);
        mCameraAppUi = new CameraAppUiImpl(this);
        mCameraAppUi.createCommonView();
        initializeCommonManagers();
        mCameraAppUi.initializeCommonView();
        CameraPerformanceTracker.onEvent(TAG,
                CameraPerformanceTracker.NAME_CAMERA_INIT_VIEW_MANAGER,
                CameraPerformanceTracker.ISEND);

        // just set content view for preview
        CameraPerformanceTracker.onEvent(TAG,
                CameraPerformanceTracker.NAME_CAMERA_VIEW_OPERATION,
                CameraPerformanceTracker.ISBEGIN);
        setContentView(R.layout.camera);
        ViewGroup appRoot = (ViewGroup) findViewById(R.id.camera_app_root);
        appRoot.bringToFront();
        CameraPerformanceTracker.onEvent(TAG,
                CameraPerformanceTracker.NAME_CAMERA_VIEW_OPERATION,
                CameraPerformanceTracker.ISEND);
        mCameraDeviceCtrl.attachSurfaceViewLayout();

        mCameraDeviceCtrl.setCameraAppUi(mCameraAppUi);
        IFileSaver fileSaver = new FileSaverImpl(mFileSaver);
        IFeatureConfig featureConfig = new FeatureConfigImpl();
        ICameraDeviceManager deviceManager = new CameraDeviceManagerImpl(this,
                mCameraDeviceCtrl);
        mISelfTimeManager = new SelfTimerManager(this, mCameraAppUi);
        /*prize-xuchunming-20180523-add shortcuts-start*/
        //parseIntent();
        /*prize-xuchunming-20180523-add shortcuts-end*/
        CameraPerformanceTracker.onEvent(TAG,
                CameraPerformanceTracker.NAME_CAMERA_CREATE_MODULE,
                CameraPerformanceTracker.ISBEGIN);
        mModuleManager = new ModuleManager(this, fileSaver, mCameraAppUi,
                featureConfig, deviceManager, moduleCtrl, mISelfTimeManager);
        mISettingCtrl = mModuleManager.getSettingController();
        mCameraAppUi.setSettingCtrl(mISettingCtrl);
        if (isVideoCaptureIntent() || isVideoWallPaperIntent()) {
            mCameraActor = new VideoActor(this, mModuleManager,
                    ModePicker.MODE_VIDEO);
        /*prize-xuchunming-20180523-add shortcuts-start*/
        }else if(isShortCut() == true){
			changeModeForShortCut();
		/*prize-xuchunming-20180523-add shortcuts-end*/
		} else {
        	/*prize-xuchunming-20160919-add double camera activity boot-start*/
			mCameraActor = new PhotoActor(this, mModuleManager,ModePicker.MODE_PHOTO);
        	/*prize-xuchunming-20160919-add double camera activity boot-end*/
        }
        CameraPerformanceTracker.onEvent(TAG,
                CameraPerformanceTracker.NAME_CAMERA_CREATE_MODULE,
                CameraPerformanceTracker.ISEND);
        mCameraDeviceCtrl.setModuleManager(mModuleManager);
        mCameraDeviceCtrl.setSettingCtrl(mISettingCtrl);
        mCameraDeviceCtrl.setCameraActor(mCameraActor);
        mCameraDeviceCtrl.resumeStartUpThread();
        mFileSaver.bindSaverService();
        mOtherDeviceConectedManager = new ExternalDeviceManager(this);
        mOtherDeviceConectedManager.onCreate();
        mOtherDeviceConectedManager.addListener(mListener);

        // reset smile shot, hdr, asd sharepreference as off to avoid error
        // when kill camera through IPO or other abnormal ways.
        if (isNonePickIntent()) {
            SettingUtils.updateSettingCaptureModePreferences(mPreferences
                    .getLocal());
        }
        // @}
        SettingUtils.upgradeGlobalPreferences(mPreferences.getGlobal(),
                CameraHolder.instance().getNumberOfCameras());
        initializeStereo3DMode();
        mVibrator = (Vibrator) getSystemService(Service.VIBRATOR_SERVICE);
        mDisplayRotation = Util.getDisplayRotation(this);

        // for photo view loading camera folder
        ExtensionHelper.getCameraFeatureExtension(this);
        Storage.initializeStorageState();
        CameraPerformanceTracker.onEvent(TAG,
                CameraPerformanceTracker.NAME_CAMERA_CREATE_SCREENNAIL,
                CameraPerformanceTracker.ISBEGIN);
        CameraPerformanceTracker.onEvent(TAG,
                CameraPerformanceTracker.NAME_CAMERA_CREATE_SCREENNAIL,
                CameraPerformanceTracker.ISEND);
        // only initialize some thing for open
        initializeForOpeningProcess();
        initializeAfterPreview();
        CameraPerformanceTracker.onEvent(TAG,
                CameraPerformanceTracker.NAME_CAMERA_ON_CREATE,
                CameraPerformanceTracker.ISEND);

		mHandler = new Handler();

        // Nav bar color customized feature. prize-linkh-2017.08.28 @{
        if(PrizeOption.PRIZE_NAVBAR_COLOR_CUST) {
            getWindow().setDisableCustNavBarColor(true);
        } // @}
        

    }
    
    /*prize-xuchunming-20160919-add double camera activity boot-start*/
    public boolean isDoubleCameraLauncher() {
		// TODO Auto-generated method stub
    	Intent i = getIntent();
    	String acitivtyName = i.getComponent().getClassName();
    	if(acitivtyName.equals("com.android.camera.DoubleCameraActivity")){
    		return true;
    	}
		return false;
	}
    /*prize-xuchunming-20160919-add double camera activity boot-end*/
    
    private Handler mNavigationBarHandler = new Handler(){
    	public void dispatchMessage(Message msg) {
    		Log.i(TAG, "NavigationBarHandler what=" + msg.what);
    		switch (msg.what) {
			case 0:
				boolean isShow = NavigationBarUtils.isShowNavigationBar(CameraActivity.this.getApplicationContext());
				mCameraAppUi.onChangeNavigationBar(isShow);
				break;
			default:
				break;
			}
    	};
    };
    
    private boolean mHasNavigationBar;
    
    private ContentObserver mContentObserver = new ContentObserver(mHandler) {
    	
    	@Override
    	public void onChange(boolean selfChange) {
    		// TODO Auto-generated method stub
    		mNavigationBarHandler.sendEmptyMessage(0);
    		super.onChange(selfChange);
    	}
    	
    	@Override
    	public void onChange(boolean selfChange, Uri uri) {
    		// TODO Auto-generated method stub
    		super.onChange(selfChange, uri);
    	}
	};
	
    private void registerNavigationBarListener() {
    	if(mContentObserver!=null && mHasNavigationBar) {
    	   	getContentResolver().registerContentObserver(Settings.System.getUriFor(NavigationBarUtils.PRIZE_NAVBAR_STATE), true, mContentObserver);
    	}
    }
    
    private void unregisterNavigationBarListener() {
    	if(mContentObserver!=null && mHasNavigationBar) {
    		getContentResolver().unregisterContentObserver(mContentObserver);
    	}
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        if (FeatureSwitcher.isApi2Enable(this)) {
            getCameraActivityBridge().onRestart();
            return;
        }

        if (isNonePickIntent() && isMountPointChanged()) {
            finish();
            mForceFinishing = true;
            startActivity(getIntent());
        } else if (isMountPointChanged()) {
            Storage.updateDefaultDirectory();
        }
        Log.i(TAG, "onRestart() mForceFinishing=" + mForceFinishing);
    }
    
    

    @Override
	protected void onStart() {
		super.onStart();
		registerNavigationBarListener();
	}

    @Override
    protected void onResume() {
        Log.i(TAG, "onResume(),    mForceFinishing=" + mForceFinishing);
        closeFlashLight();
        keepMediaProviderInstance();
        CameraPerformanceTracker.onEvent(TAG,
                CameraPerformanceTracker.NAME_CAMERA_ON_RESUME,
                CameraPerformanceTracker.ISBEGIN);
        super.onResume();
        if (FeatureSwitcher.isApi2Enable(this)) {
            if (mPermissionManager.requestCameraLaunchPermissions()) {
                mIsAPI2Inited = true;
                getCameraActivityBridge().onResume();
            }
            return;
        }
        parseIntent();
        mOtherDeviceConectedManager.onResume();
        if (mForceFinishing || mCameraDeviceCtrl.isOpenCameraFail()) {
            mNeedRestoreIfOpenFailed = false;
            return;
        }
        ViewGroup appRoot = (ViewGroup) findViewById(R.id.camera_app_root);
        int flag = View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        appRoot.setSystemUiVisibility(flag);
        CameraPerformanceTracker.onEvent(TAG,
                CameraPerformanceTracker.NAME_RESUME_NOTIFY,
                CameraPerformanceTracker.ISBEGIN);
        
        if (mModuleManager != null) {
            mModuleManager.resume();
        }

        // need check permission
        if (mPermissionManager.checkCameraLaunchPermissions()) {
            mCameraDeviceCtrl.onResume();
        }

        CameraPerformanceTracker.onEvent(TAG,
                CameraPerformanceTracker.NAME_RESUME_NOTIFY,
                CameraPerformanceTracker.ISEND);
        // For onResume()->onPause()->onResume() quickly case,
        // onFullScreenChanged(true) may be called after onPause() and before
        // onResume().
        // So, force update app view.
        CameraPerformanceTracker.onEvent(TAG,
                CameraPerformanceTracker.NAME_UPDATE_APP_VIEW,
                CameraPerformanceTracker.ISBEGIN);
        doOnResume();
        CameraPerformanceTracker.onEvent(TAG,
                CameraPerformanceTracker.NAME_UPDATE_APP_VIEW,
                CameraPerformanceTracker.ISEND);
        // for the case camera onPause and then edit pic in gallery, when resume
        // camera, thumbnail will not update to new.
        mCameraAppUi.forceThumbnailUpdate();
        mNeedRestoreIfOpenFailed = true;
        Util.enterCameraPQMode();
        if (!isCameraOpened()) {
            mCameraAppUi.setViewState(ViewState.VIEW_STATE_CAMERA_CLOSED);
            Log.i(TAG, "[onResume],camera device is opening,set view state.");
        }
        CameraPerformanceTracker.onEvent(TAG,
                CameraPerformanceTracker.NAME_CAMERA_ON_RESUME,
                CameraPerformanceTracker.ISEND);
		mHandler.postDelayed(mRunnable, EXIT_APP_TIMER);
		
		isBatteryLow = false;
		intentFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
		registerReceiver(batteryStatusBR, intentFilter);
		
	    //setScreenBrightness(255);
	    if(mCameraAppUi != null){
        	//mCameraAppUi.onModeChange(getCurrentMode());

            /*prize-add-BUG:40733 press the Home button to stop the animation when you exit-xiaoping-20171012-start*/
            mCameraAppUi.showPhotoShutter();
            /*prize-add-BUG:40733 press the Home button to stop the animation when you exit-xiaoping-20171012-end*/
        }
	    /*prize-xuchunming-20161223-show info when operation long time in bokehmode-start*/
	    if(mModePicker != null){
        	mModePicker.onResume();
            /*prize-add-bug:40356、40685 The other application calls the camera ,camera mode display exception-xiaoping-20171016-start*/
            mModePicker.switchCamera(getCameraId());
            /*prize-add-bug:40356、40685 The other application calls the camera ,camera mode display exception-xiaoping-20171016-end*/
        }
	    /*prize-xuchunming-20161223-show info when operation long time in bokehmode-end*/
	    /*prize-add-bugid:54688 photo details interface location is not displayed-xiaoping-20180409-start*/
	    mLocationManager.recordLocation(true);
	    /*prize-add-bugid:54688 photo details interface location is not displayed-xiaoping-20180409-end*/
	    
    }

    /**
     * the result if CameraActivity permission check.
     * there is four permissions that must be all on, the camera can be launch normally,
     * otherwise exit the camera app.
     * @param requestCode
     *            camera permission check code, used when requested permissions and
     *            the code will be back in the permissions requested result.
     * @param permissions
     *            the dangerous permissions that the activity defined in manifest.
     * @param grantResults
     *            the permission result for every permission.
     */
    public void onRequestPermissionsResult(int requestCode,
            String permissions[], int[] grantResults) {
        Log.i(TAG, "onRequestPermissionsResult(), requestCode = " + requestCode);
        mIsCheckingLocationPermission = false;
        if (grantResults.length <= 0) {
            finish();
            return;
        }
        if (mPermissionManager.getCameraLaunchPermissionRequestCode()
                == requestCode) {
            if (mPermissionManager.isCameraLaunchPermissionsResultReady(
                    permissions, grantResults)) {
                // permission was granted
                // resume again, and open camera
                if (FeatureSwitcher.isApi2Enable(this)) {
                    mIsAPI2Inited = true;
                }
            } else {
                // more than one critical permission was denied
                // activity finish, exit and destroy
                Toast.makeText(this, com.mediatek.internal.R.string.denied_required_permission,
                        Toast.LENGTH_LONG).show();
                finish();
            }
        } else if (mPermissionManager.getCameraLocationPermissionRequestCode()
                == requestCode) {
            if (mPermissionManager.isCameraLocationPermissionsResultReady(
                    permissions, grantResults)) {
                mLocationManager.recordLocation(true);
            } else {
                //set location off to setting
                ListPreference pref = mISettingCtrl.getListPreference(
                        SettingConstants.KEY_RECORD_LOCATION);
                pref.setValue(SETTING_OFF);
                if (!ActivityCompat.shouldShowRequestPermissionRationale(this, permissions[0])) {
                    Log.i(TAG, "onRequestPermissionsResult(), toast show");
                    Toast.makeText(this, com.mediatek.internal.R.string.denied_required_permission,
                            Toast.LENGTH_LONG).show();
                }
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions,
                    grantResults);
            return;
        }
    }

    @Override
    protected void onPause() {
        Log.i(TAG, "onPause() mForceFinishing=" + mForceFinishing+",mPendingSwitchCameraId: "+mPendingSwitchCameraId);
        /*prize-xuchunming-20180505-bugid:57017-start*/
        Settings.System.putInt(getContentResolver(), "camera_capturing", 0);
        /*prize-xuchunming-20180505-bugid:57017-end*/
        if (mMediaProviderClient != null) {
            Log.i(TAG, "onPause() release mMediaProviderClient");
            mMediaProviderClient.release();
            mMediaProviderClient = null;
        }
        CameraPerformanceTracker.onEvent(TAG,
                CameraPerformanceTracker.NAME_CAMERA_ON_PAUSE,
                CameraPerformanceTracker.ISBEGIN);
        super.onPause();
        /*prize-add-press the Home button to stop the animation when you exit-xiaoping-20171001-start*/
        mCameraAppUi.hideCaptureAnimation();
        /*prize-add-press the Home button to stop the animation when you exit-xiaoping-20171001-end*/
        if (FeatureSwitcher.isApi2Enable(this)) {
            if (mIsAPI2Inited) {
                getCameraActivityBridge().onPause();
                mIsAPI2Inited = false;
            }
            return;
        }

        /*prize-add-bugid: 60160 mPendingSwitchCameraId abnormality occurred when pressing the Home button after switching camera-xiaoping-20180605-start*/
/*        if (mPendingSwitchCameraId != UNKNOWN) {
            mPendingSwitchCameraId = UNKNOWN;
        }*/
        /*prize-add-bugid: 60160 mPendingSwitchCameraId abnormality occurred when pressing the Home button after switching camera-xiaoping-20180605-end*/
        /*prize-xuchunming-20171216-bugid:44558-start*/
        mModePicker.onpause();
        /*prize-xuchunming-20171216-bugid:44558-end*/
        
        /*prize-xuchunming-20170215-bugid:28339-start*/
        if(isNonePickIntent() == true){
        	saveOverrideValue();
        }
        /*prize-xuchunming-20170215-bugid:28339-end*/
        mCameraDeviceCtrl.onPause();
        if (mForceFinishing || mCameraDeviceCtrl.isOpenCameraFail()) {
            // M: patch for Picutre quality enhance
            Log.i(TAG, "onPause(),release surface texture.");
            Util.exitCameraPQMode();
            // when camera is open fail,need notify otherDeviceConnectMananger
            mOtherDeviceConectedManager.onPause();

            /*prize-for bugid:35822-liufan-2017-07-06-start*/
            mCameraDeviceCtrl.removeOpenCameraFailMessage();
            Log.i("xxyy", "onPause(),finish camera activity onPause.");
            finish();
            /*prize-for bugid:35822-liufan-2017-07-06-end*/
            return;
        }
        // when camera start up thread is after onPause(),
        // so the case :mCameraDeviceCtrl.isOpenCameraFail() is false
        // but when handler msg will receive the msg:open file.
        // in this case we not need do the MSG
        mNeedRestoreIfOpenFailed = false;
        /*prize-modify-bugid:50105 the settingmangger is not collospace when press Home button-xiaoping-20180306-start*/
        //mCameraAppUi.collapseViewManager(true);
        /*prize-modify-bugid:50105 the settingmangger is not collospace when press Home button-xiaoping-20180306-end*/
        mModuleManager.pause();
        keepCameraForSecure();
        clearFocusAndFace();
        uninstallIntentFilter();
        callResumablePause();
        mOrientationListener.disable();
        mLocationManager.recordLocation(false);
        // when close camera, should reset mOnResumeTime
        mOnResumeTime = 0L;
        // make sure this field is false when resume camera next time
        // mIsNeedUpdateOrientationToParameters = false;
        mMainHandler.removeCallbacksAndMessages(null);
        // actor will set screen on if needed, here reset it.
        resetScreenOn();
        // M: patch for Picutre quality enhance
        Util.exitCameraPQMode();
        mOtherDeviceConectedManager.onPause();
        //setScreenBrightness(getScreenBrightness());
        CameraPerformanceTracker.onEvent(TAG,
                CameraPerformanceTracker.NAME_CAMERA_ON_PAUSE,
                CameraPerformanceTracker.ISEND);
    	mHandler.removeCallbacks(mRunnable);
    	unregisterReceiver(batteryStatusBR);

    }

    @Override
    protected void onStop() {
    	mHandler.removeCallbacks(mRunnable);
    	super.onStop();
    	unregisterNavigationBarListener();
    	/*prize-xuchunming-20161105-hide modechange blur effect when app exit-start*/
    	mModePicker.onStop();
    	/*prize-xuchunming-20161105-hide modechange blur effect when app exit-end*/

    }
    @Override
    protected void onDestroy() {
        Log.i(TAG, "onDestroy() isChangingConfigurations()="
                + isChangingConfigurations() + ", mForceFinishing="
                + mForceFinishing);
        CameraPerformanceTracker.onEvent(TAG,
                CameraPerformanceTracker.NAME_CAMERA_ON_DESTROY,
                CameraPerformanceTracker.ISBEGIN);
        super.onDestroy();
        /*prize-xuchunming-20161223-show info when operation long time in bokehmode-start*/
        if(mModePicker != null){
        	mModePicker.onDestory();
        }
        /*prize-xuchunming-20161223-show info when operation long time in bokehmode-end*/
        
        if (FeatureSwitcher.isApi2Enable(this)) {
            getCameraActivityBridge().onDestroy();
            CameraActivityBridgeFactory.destroyCameraActivityBridge(this);
            return;
        }

        mNextMode = UNKNOWN;
        // we finish worker thread when current activity destroyed.
        callResumableFinish();
        if (mFileSaver != null) {
            mFileSaver.unBindSaverService();
        }
        if (mCameraActor != null) {
            mCameraActor.release();
        }
        if (mISelfTimeManager != null) {
            ((SelfTimerManager) mISelfTimeManager).releaseSelfTimer();
            mISelfTimeManager = null;
        }
        mModuleManager.destory();
        mCameraDeviceCtrl.onDestory();
        if (mForceFinishing) {
            return;
        }
        /*PRIZE-modify geographical position-wanzhijuan-2016-05-03*/
        mLocationManager.stop();
        /*PRIZE-modify geographical position-wanzhijuan-2016-05-03-end*/
        if (mIsBackPressed) {
            clearUserSettings();
            mIsBackPressed = false;
        }
        CameraPerformanceTracker.onEvent(TAG,
                CameraPerformanceTracker.NAME_CAMERA_ON_DESTROY,
                CameraPerformanceTracker.ISEND);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (FeatureSwitcher.isApi2Enable(this)) {
            getCameraActivityBridge().onActivityResult(requestCode, resultCode,
                    data);
            return;
        }
        mCameraActor.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onBackPressed() {
        Log.i(TAG, "onBackPressed()");
        if (FeatureSwitcher.isApi2Enable(this)) {
            if (getCameraActivityBridge().onBackPressed()) {
                super.onBackPressed();
                return;
            }
            return;
        }
        if (mPaused || mForceFinishing) {
            return;
        }
        if (mCameraDeviceCtrl.isOpenCameraFail()) {
            super.onBackPressed();
            return;
        }
        if ((!mCameraAppUi.collapseViewManager(false) && !mCameraActor
                .onBackPressed())) {
            super.onBackPressed();
        }
        mIsBackPressed = true;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (FeatureSwitcher.isApi2Enable(this)) {
            getCameraActivityBridge().onConfigurationChanged(newConfig);
            return;
        }
        Log.i(TAG, "onConfigurationChanged(" + newConfig + ")");
        // before the view collapse,get current camera view state
        boolean isSettingsViewState = mCameraAppUi.getViewState() == ViewState.VIEW_STATE_SETTING;
        Log.d(TAG, "mCameraState = " + mCameraAppUi.getViewState()
                + ",isSettingsView = " + isSettingsViewState);
        clearFocusAndFace();

        /*prize-xuchunming-20160905-bugid:20765-start*/
        /*ViewGroup appRoot = (ViewGroup) findViewById(R.id.camera_app_root);
        appRoot.removeAllViews();
        getLayoutInflater().inflate(R.layout.preview_frame, appRoot, true);
        getLayoutInflater().inflate(R.layout.view_layers, appRoot, true);

        mCameraAppUi.removeAllView();*/
        /*prize-xuchunming-20160905-bugid:20765-end*/

        // unlock orientation for new config
        setOrientation(false, OrientationEventListener.ORIENTATION_UNKNOWN);
        mCameraDeviceCtrl.setDisplayOrientation();
        initializeForOpeningProcess();
        // Here we should update aspect ratio for reflate preview frame layout.
        mCameraDeviceCtrl.setPreviewFrameLayoutAspectRatio();
        updateFocusAndFace();
        /*prize-xuchunming-20160905-bugid:20765-start*/
        //mCameraAppUi.onConfigurationChanged();
        /*prize-xuchunming-20160905-bugid:20765-end*/
        notifyOrientationChanged();
        mModuleManager.configurationChanged();
        if(mCameraAppUi != null){
        	mCameraAppUi.onModeChange(getCurrentMode());
        }
        
    }

    public void onSingleTapUp(View view, int x, int y) {
        if (FeatureSwitcher.isApi2Enable(this)) {
            return;
        }
        // Gallery use onSingleTapConfirmed() instead of onSingleTapUp().
        Log.i(TAG, "onSingleTapUp(" + view + ", " + x + ", " + y + ")");
        // we do nothing for dialog is showing
        boolean isPogressShowing = mCameraAppUi.getCameraView(
                CommonUiType.ROTATE_PROGRESS).isShowing();
        boolean isDialogShowing = mCameraAppUi.getCameraView(
                CommonUiType.ROTATE_DIALOG).isShowing();
        if (!isDialogShowing && !isPogressShowing) {

            // For tablet
            if (FeatureSwitcher.isSubSettingEnabled()) {
                mCameraAppUi.collapseSubSetting(true); // need to check it?

            }

            if (isCancelSingleTapUp()) {
                Log.i(TAG, "will cancel this singleTapUp event");
                return;
            }

            if(isGrayArea(y) == true){
            	Log.i(TAG, "GrayArea will cancel this singleTapUp event");
            	return;
            }
            
            if (!mCameraAppUi.collapseSetting(true)) {
                if (mCameraActor.getonSingleTapUpListener() != null) {
                    mCameraActor.getonSingleTapUpListener().onSingleTapUp(view,
                            x, y);
                }
            }
        }
    }

    private boolean isGrayArea(int y) {
		// TODO Auto-generated method stub
    	int topGray = (int)getResources().getDimension(R.dimen.picker_button_size);
    	int bottomGray = (int)getResources().getDimension(R.dimen.shutter_group_height);		
		int nvaHeight = 0;
        if(NavigationBarUtils.isShowNavigationBar(getApplicationContext())){
        	nvaHeight = NavigationBarUtils.getNavigationBarHeight(getApplicationContext());
        }
        int maxY = getPreviewSurfaceView().getHeight() - (bottomGray - nvaHeight);
    	if(getPreviewSurfaceView() != null && Math.abs((getPreviewSurfaceView().getAspectRatio() - SettingUtils.getFullScreenRatio())) <= Util.ASPECT_TOLERANCE){//full screen
    		if(y > maxY){
        		return true;
        	}
    	}
		return false;
	}

    public void onLongPress(View view, int x, int y) {
        if (FeatureSwitcher.isApi2Enable(this)) {
            return;
        }
        Log.i(TAG, "OnLongPress(" + view + ", " + x + ", " + y + ")"
                + ",mCurrentViewState = " + mCameraAppUi.getViewState());
        if (mCameraAppUi.getViewState() == ViewState.VIEW_STATE_LOMOEFFECT_SETTING) {
            return;
        }
        // we do nothing for dialog is showing
        boolean isPogressShowing = mCameraAppUi.getCameraView(
                CommonUiType.ROTATE_PROGRESS).isShowing();
        boolean isDialogShowing = mCameraAppUi.getCameraView(
                CommonUiType.ROTATE_DIALOG).isShowing();
        if (!isDialogShowing && !isPogressShowing) {
            if (!mCameraAppUi.collapseSetting(true)) {
                if (mCameraActor.getonLongPressListener() != null) {
                    mCameraActor.getonLongPressListener().onLongPress(view, x, y);
                }
            }
        }
    }

    public void onSingleTapUpBorder(View view, int x, int y) {
        if (FeatureSwitcher.isApi2Enable(this)) {
            return;
        }
        // Just collapse setting if touch border
        boolean isPogressShowing = mCameraAppUi.getCameraView(
                CommonUiType.ROTATE_PROGRESS).isShowing();
        boolean isDialogShowing = mCameraAppUi.getCameraView(
                CommonUiType.ROTATE_DIALOG).isShowing();
        if (!isDialogShowing && !isPogressShowing) {
            mCameraAppUi.collapseSetting(true);
            // For tablet
            if (FeatureSwitcher.isSubSettingEnabled()) {
                mCameraAppUi.collapseSubSetting(true);
            }
        }
    }

    @Override
    public void onUserInteraction() {
        if (FeatureSwitcher.isApi2Enable(this)) {
            if (!getCameraActivityBridge().onUserInteraction()) {
                super.onUserInteraction();
            }
            return;
        }
        if (mCameraActor == null || !mCameraActor.onUserInteraction()) {
            super.onUserInteraction();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (FeatureSwitcher.isApi2Enable(this)) {
            if (!getCameraActivityBridge().onKeyDown(keyCode, event)) {
                return super.onKeyDown(keyCode, event);
            }
            return true;
        }
        // Do not handle any key if the activity is paused.
        if (mPaused) {
            return true;
        }
        if (isFullScreen() && KeyEvent.KEYCODE_MENU == keyCode
                && event.getRepeatCount() == 0
                && mCameraAppUi.performSettingClick()) {
            return true;
        }
        if (!mCameraActor.onKeyDown(keyCode, event)) {
            if(mModePicker.getCurrentMode() == 13) {
                /*prize-modify-Save the bokeh file to prohibit exit-xiaoping-20170601-start*/
                if (mCameraAppUi.getViewState() == ViewState.VIEW_STATE_SETTING || (mModuleManager.getModeState()  != ModeState.STATE_CAPTURING && mFileSaver.isMakeBokehFile)) {
                    return super.onKeyDown(keyCode, event);
                } else {
                    Toast.makeText(CameraActivity.this, R.string.save_information_after_photo_toast, Toast.LENGTH_SHORT).show();
                }
                 /*prize-modify-Save the bokeh file to prohibit exit-xiaoping-20170601-end*/ 
            } else{
                return super.onKeyDown(keyCode, event); 
            }
        }
          return true;
    }

	/*PRIZE-menu key-wanzhijuan-2015-10-30-start*/
    public boolean handleMenu() {
    	//HidePaper();
    	boolean isHandle = mCameraAppUi.performSettingClick();
    	//mBidirSlidingLayout.scrollToContent();
//    	leavePhotoView();
		return isHandle;
    }
	/*PRIZE-menu key-wanzhijuan-2015-10-30-end*/
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (FeatureSwitcher.isApi2Enable(this)) {
            if (!getCameraActivityBridge().onKeyUp(keyCode, event)) {
                return super.onKeyUp(keyCode, event);
            }
            return true;
        }
        if (mPaused) {
            return true;
        }
        /*prize-xuchunming-20180411-bugid:50825-start*/
		if(Settings.System.getInt(getContentResolver(), "camera_capturing", 0) == 1) {
			if(keyCode == KeyEvent.KEYCODE_HOME) {
				Log.w(TAG, "do not exec KEYCODE_HOME operation");
				return true;
			}
			
			if(keyCode == KeyEvent.KEYCODE_BACK) {
				Log.w(TAG, "do not exec KEYCODE_BACK operation");
				return true;
			}
			
		}else {
			if(keyCode == KeyEvent.KEYCODE_HOME) {
				gotoHome();
				return true;
			}
		}
		/*prize-xuchunming-20180411-bugid:50825-end*/

    
        if (!mCameraActor.onKeyUp(keyCode, event)) {
            return super.onKeyUp(keyCode, event);
        }
        return true;
    }

    public void gotoGallery() {
        Intent intent = null;
        if (mCameraAppUi.getThumbnailMimeType().contains("image/")) {
            intent = new Intent(Intent.ACTION_VIEW);
            /**prize-add-by-zhongweilin-start*/
            Log.e(TAG, "[startGalleryActivity] setIntent gallery3d ");
            ComponentName cn = new ComponentName("com.android.gallery3d", "com.android.gallery3d.app.GalleryActivity");              
            intent.setComponent(cn);  
            /**prize-add-by-zhongweilin-start*/
        } else {
            Log.e(TAG, "[startGalleryActivity] setIntent camera review action ");
            intent = new Intent(Util.REVIEW_ACTION);
        }
        intent.setDataAndType(mCameraAppUi.getThumbnailUri(),
                mCameraAppUi.getThumbnailMimeType());
        intent.putExtra(Util.IS_CAMERA, true);
        if (isSecureCamera()) {
            intent.putExtra(Util.IS_SECURE_CAMERA, true);
            intent.putExtra(Util.SECURE_ALBUM, getSecureAlbum());
            intent.putExtra(Util.SECURE_PATH, getPath());
            notifyGotoGallery();
        }
        // add this for screen pinning
        if (mActivityManager.LOCK_TASK_MODE_PINNED == mActivityManager
                .getLockTaskModeState()) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT
                    | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        }
        try {
            startActivity(intent);
			overridePendingTransition(com.android.camera.R.anim.prize_goto_gallery_in_anim,
                com.android.camera.R.anim.prize_goto_gallery_out_anim);
        } catch (ActivityNotFoundException ex) {
            Log.e(TAG, "[startGalleryActivity] Couldn't view ", ex);
        }
    }

    public ISettingCtrl getISettingCtrl() {
        return mISettingCtrl;
    }

    public void resetScreenOn() {
        if (FeatureSwitcher.isApi2Enable(this)) {
            return;
        }
        Log.d(TAG, "resetScreenOn()");
        mMainHandler.removeMessages(MSG_CLEAR_SCREEN_DELAY);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    public void keepScreenOnAwhile() {
        if (FeatureSwitcher.isApi2Enable(this)) {
            return;
        }
        Log.d(TAG, "keepScreenOnAwhile()");
        mMainHandler.removeMessages(MSG_CLEAR_SCREEN_DELAY);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mMainHandler.sendEmptyMessageDelayed(MSG_CLEAR_SCREEN_DELAY,
                DELAY_MSG_SCREEN_SWITCH);
    }

    public void keepScreenOn() {
        if (FeatureSwitcher.isApi2Enable(this)) {
            return;
        }
        Log.d(TAG, "keepScreenOn()");
        mMainHandler.removeMessages(MSG_CLEAR_SCREEN_DELAY);
        if (getWindow() != null) {
            getWindow()
                    .addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    public void setGestureListener(GestureListener listener) {
        mGestureDispatcher.setGestureListener(listener);
    }

    public void setGestureDispatcherListener(GestureDispatcherListener listener) {
        mGestureDispatcher.setGestureDispatcherListener(listener);
    }

    public interface OnOrientationListener {
        void onOrientationChanged(int orientation);
    }

    public interface OnParametersReadyListener {
        void onCameraParameterReady();
    }

    public interface OnPreferenceReadyListener {
        void onPreferenceReady();
    }

    public interface Resumable {
        void begin();

        void resume();

        void pause();

        void finish();
    }

    public interface OnSingleTapUpListener {
        void onSingleTapUp(View view, int x, int y);
    }

    public interface OnLongPressListener {
        void onLongPress(View view, int x, int y);
    }

    public Vibrator getVibrator() {
        return mVibrator;
    }

    public SharedPreferences getSharePreferences() {
        if (FeatureSwitcher.isApi2Enable(this)) {
            return null;
        }
        int cameraId = mCameraDeviceCtrl.getCameraId();
        return mPreferences.getSharedPreference((Context) CameraActivity.this,
                cameraId);
    }

    public SharedPreferences getSharePreferences(int cameraId) {
        if (FeatureSwitcher.isApi2Enable(this)) {
            return null;
        }
        return mPreferences.getSharedPreference((Context) CameraActivity.this,
                cameraId);
    }

    public void setCameraState(int state) {
        if (FeatureSwitcher.isApi2Enable(this)) {
            return;
        }
        Log.i(TAG, "setCameraState(" + state + ")");
        mCameraState = state;
    }

    public int getCameraState() {
        return mCameraState;
    }

    public boolean isCameraOpened() {
        if (FeatureSwitcher.isApi2Enable(this)) {
            return false;
        }
        return mCameraDeviceCtrl.isCameraOpened();
    }

    public boolean isModeChanged() {
        return mIsModeChanged;
    }

    public int getNextMode() {
        return mNextMode;
    }

    public int getPrevMode() {
        return mPrevMode;
    }

    public int getCurrentRecordingFps(Parameters ps) {
        if (FeatureSwitcher.isApi2Enable(this)) {
            return -1;
        }
        Log.i(TAG, "getCurrentRecordingFps ");
        int value = -1;
        boolean isEISOn = ps.get(ParametersHelper.KEY_VIDEO_STABLILIZATION)
                .equals(ParametersHelper.VIDEO_STABLILIZATION_ON);
        if (isEISOn) {
            String typeValueString = ps
                    .get(ParametersHelper.KEY_VIDEO_RECORIND_FEATURE_MAX_FPS);
            Log.i(TAG, "getCurrentRecordingFps, typeValueString = "
                    + typeValueString);
            if (typeValueString != null) {
                int index = typeValueString.indexOf("@");
                if (index >= 0) {
                    value = Integer.parseInt(typeValueString
                            .substring(0, index));
                }
            }
        }
        Log.i(TAG, "getCurrentRecordingFps, value = " + value);
        return value;
    }

    public ISelfTimeManager getSelfTimeManager() {
        Log.i(TAG, "[getSelfTimeManager] mISelfTimeManager = "
                + mISelfTimeManager);
        return mISelfTimeManager;
    }

    public ICameraAppUi getCameraAppUI() {
        return mCameraAppUi;
    }

    public FrameView getFrameView() {
        return mFrameManager.getFrameView();
    }

    public FrameManager getFrameManager() {
        return mFrameManager;
    }

    public ComboPreferences getPreferences() { // not recommended
        return mPreferences;
    }

    public ListPreference getListPreference(int row) {
        String key = SettingConstants.getSettingKey(row);
        return getListPreference(key);
    }

    public ListPreference getListPreference(String key) {
        return mISettingCtrl.getListPreference(key);
    }

    public FileSaver getFileSaver() {
        return mFileSaver;
    }

    public BDLocationManager getLocationManager() {
        return mLocationManager;
    }

    public int getCurrentMode() {
        return mCameraActor.getMode();
    }

    public ModePicker getModePicker() {
        return mModePicker;
    }

    // activity
    public int getDisplayRotation() {
        return mDisplayRotation;
    }

    // sensor
    public int getOrietation() {
        return mOrientation;
    }

    // activity + sensor
    public int getOrientationCompensation() {
        return mOrientationCompensation;
    }

    public int getCameraCount() {
        return mNumberOfCameras;
    }

    public CameraActor getCameraActor() {
        return mCameraActor;
    }

    public ModuleManager getModuleManager() {
        return mModuleManager;
    }

    public int getPreviewFrameWidth() {
    	/*prize-modify-do not display focus view from 19:9 full screen-xiaoping-20180509-start*/
    	if(getCurrentMode() == ModePicker.MODE_BOKEH){
    		if(mCameraActor instanceof PhotoActor && ((PhotoActor)mCameraActor).getICameraMode() instanceof GyBokehMode){
    			GyBokehMode gymode = ((GyBokehMode)((PhotoActor)mCameraActor).getICameraMode());
    			if(gymode.getGlSurfaceView() != null){
    				return gymode.getGlSurfaceView().getPreviewWidth();
    			}
    		}
    		return 0;
    	}else{
        	/*prize-add-18:9 full screen-xiaoping-20180423-start*/
            return getPreviewSurfaceView().getWidth();
            /*prize-add-18:9 full screen-xiaoping-20180423-end*/
    	}
    	/*prize-modify-do not display focus view from 19:9 full screen-xiaoping-20180509-end*/
    }

    public int getPreviewFrameHeight() {
    	if(getCurrentMode() == ModePicker.MODE_BOKEH){
    		if(mCameraActor instanceof PhotoActor && ((PhotoActor)mCameraActor).getICameraMode() instanceof GyBokehMode){
    			GyBokehMode gymode = ((GyBokehMode)((PhotoActor)mCameraActor).getICameraMode());
    			if(gymode.getGlSurfaceView() != null){
    				return gymode.getGlSurfaceView().getPreviewHeight();
    			}
    		}
    		return 0;
    	}else{
            /*prize-modify-get the correct preview area height-xiaoping-20171017-start*/
    		 return getPreviewSurfaceView().getHeight();
            /*prize-modify-get the correct preview area height-xiaoping-20171017-end*/
    	}
       
    }

    public View getPreviewFrameLayout() {
        return mPreviewFrameLayout;
    }

    public int getUnCropWidth() {
        return mCameraDeviceCtrl.getUnCropWidth();
    }

    public int getUnCropHeight() {
        return mCameraDeviceCtrl.getUnCropHeight();
    }

    public GestureRecognizer getGestureRecognizer() {
        return mGestureRecognizer;
    }

    public void showBorder(boolean show) {
        if (FeatureSwitcher.isApi2Enable(this)) {
            return;
        }
        mPreviewFrameLayout.showBorder(show);
    }

    public View inflate(int layoutId, int layer) {
        return mCameraAppUi.inflate(layoutId, layer);
    }

    public void addView(View view, int layer) {
        mCameraAppUi.addView(view, layer);
    }

    public void removeView(View view, int layer) {
        mCameraAppUi.removeView(view, layer);
    }

    public boolean addOnPreferenceReadyListener(OnPreferenceReadyListener l) {
        if (!mPreferenceListeners.contains(l)) {
            return mPreferenceListeners.add(l);
        }
        return false;
    }

    public boolean addOnParametersReadyListener(OnParametersReadyListener l) {
        if (!mParametersListeners.contains(l)) {
            return mParametersListeners.add(l);
        }
        return false;
    }

    public boolean removeOnParametersReadyListener(OnParametersReadyListener l) {
        return mParametersListeners.remove(l);
    }

    public boolean addViewManager(ViewManager viewManager) {
        return mCameraAppUi.addViewManager(viewManager);
    }

    public boolean removeViewManager(ViewManager viewManager) {
        return mCameraAppUi.removeViewManager(viewManager);
    }

    public boolean addResumable(Resumable resumable) {
        if (!mResumables.contains(resumable)) {
            return mResumables.add(resumable);
        }
        return false;
    }

    public boolean removeResumable(Resumable resumable) {
        return mResumables.remove(resumable);
    }

    // Becasue SMB will need this
    public void changeOrientationTag(boolean lock, int orientationNum) {
    }

    private Handler mMainHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            Log.i(TAG, "handleMessage(" + msg.what + ")");
            switch (msg.what) {
            case MSG_CAMERA_PARAMETERS_READY:
                notifyParametersReady();
                break;
            case MSG_CHECK_DISPLAY_ROTATION:
                // Set the display orientation if display rotation has changed.
                // Sometimes this happens when the device is held upside
                // down and camera app is opened. Rotation animation will
                // take some time and the rotation value we have got may be
                // wrong. Framework does not have a callback for this now.
                if (Util.getDisplayRotation(CameraActivity.this) != mDisplayRotation) {
                    mCameraDeviceCtrl.setDisplayOrientation();
                    mOrientation = OrientationEventListener.ORIENTATION_UNKNOWN;
                    mCameraActor.onDisplayRotate();
                }
                if (SystemClock.uptimeMillis() - mOnResumeTime < 5000) {
                    mMainHandler.sendEmptyMessageDelayed(
                            MSG_CHECK_DISPLAY_ROTATION, 100);
                }
                notifyOrientationChanged();
                break;
            case MSG_SWITCH_CAMERA:
            	/*prize-modify-bugid:54752 camera crash on switch camera -xiaoping-20180409-start*/
            	if (mPendingSwitchCameraId == -1) {
					mPendingSwitchCameraId = mCameraId;
				}
            	/*prize-modify-bugid:54752 camera crash on switch camera -xiaoping-20180409-end*/
            	mCameraDeviceCtrl.switchCamera(mPendingSwitchCameraId);
                break;
            case MSG_CLEAR_SCREEN_DELAY:
                getWindow().clearFlags(
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                break;
            case MSG_APPLY_PARAMETERS_WHEN_IDEL:
                mCameraDeviceCtrl.applyParameters(false);
                break;
            case MSG_DELAY_SHOW_ONSCREEN_INDICATOR:
                // will handle the delay hide the remain when is show remain
                mCameraAppUi.showText(mDelayShowInfo);
                mCameraAppUi.showIndicator(mDelayOtherMessageTime);
                break;
            case MSG_UPDATE_SWITCH_ACTOR_STATE:
                mModePicker.setEnabled(true);
                break;
            default:
                break;
            }
        };
    };

    public void onCameraOpenFailed() {
        restoreWhenCameraOpenFailed();
    }

    public void onCameraOpenDone() {
    	if(mPendingSwitchCameraId != UNKNOWN){
    		/*prize-xuchunming-20180307-bugid:50956-start*/
    		//mModePicker.hideSurfaceCover();
    		/*prize-xuchunming-20180307-bugid:50956-end*/
    	}
        mPendingSwitchCameraId = UNKNOWN;
        isSwitchingCameraDone = true;//prize-add for showing the camera user is switching to -pengcancna-20161210
        mMainHandler.sendEmptyMessage(MSG_CHECK_DISPLAY_ROTATION);
    }

    public void onCameraPreferenceReady() {
        notifyPreferenceReady();
    }

    public void onCameraParametersReady() {
        notifyParametersReady();
    }

    private void restoreWhenCameraOpenFailed() {
        Log.i(TAG, "restoreWhenCameraOpenFailed(), mNeedRestoreIfOpenFailed:"
                + mNeedRestoreIfOpenFailed);
        // some setting should be restore when camera open failed, because they
        // may
        // be set in onResume() method.
        if (mNeedRestoreIfOpenFailed) {
            uninstallIntentFilter();
            callResumablePause();
            mCameraAppUi.collapseViewManager(true);
            mOrientationListener.disable();
            mLocationManager.recordLocation(false);
            mMainHandler.removeCallbacksAndMessages(null);
            // actor will set screen on if needed, here reset it.
            resetScreenOn();
            // setLoadingAnimationVisible(true);
            // M: patch for Picutre quality enhance
            Util.exitCameraPQMode();
        }
    }

    private void keepCameraForSecure() {
        // When camera is started from secure lock screen for the first time
        // after screen on, the activity gets
        // onCreate->onResume->onPause->onResume.
        // To reduce the latency, keep the camera for a short time so it does
        // not need to be opened again.
        if (isSecureCamera() && isFirstStartAfterScreenOn()) {
            resetFirstStartAfterScreenOn();
            int cameraId = mCameraDeviceCtrl.getCameraId();
            CameraHolder.instance().keep(KEEP_CAMERA_TIMEOUT, cameraId);
        }
    }

    private void initializeAfterPreview() {
        long start = System.currentTimeMillis();
        callResumableBegin();
        mCameraAppUi.initializeAfterPreview();

        addIdleHandler(); // why no flag to disable it after checked.
        long stop = System.currentTimeMillis();

        Log.v(TAG, "initializeAfterPreview() consume:" + (stop - start));
    }

    // Here should be lightweight functions!!!
    private void initializeCommonManagers() {
        mModePicker = new ModePicker(this);
        mFileSaver = new FileSaver(this);
        mFrameManager = new FrameManager(this);
        mModePicker.setListener(mModeChangedListener);
        mCameraAppUi.setModeChangedListener(mModeChangedListener);
        mCameraAppUi.setSettingListener(mSettingListener);
        mCameraAppUi.setPickerListener(mPickerListener);
        mCameraAppUi.addFileSaver(mFileSaver);
        mPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        Log.v(TAG, "getSystemService,mPowerManager =" + mPowerManager);
        // For tablet
        if (FeatureSwitcher.isSubSettingEnabled()) {
            mCameraAppUi.setSubSettingListener(mSettingListener);
        }
    }

    private void initializeForOpeningProcess() {
        CameraPerformanceTracker.onEvent(TAG,
                CameraPerformanceTracker.NAME_CAMERA_INIT_OPEN_PROCESS,
                CameraPerformanceTracker.ISBEGIN);
        mNumberOfCameras = CameraHolder.instance().getNumberOfCameras();
        mCameraAppUi.initializeViewGroup();
        // for focus manager used
        mFocusAreaIndicator = (RotateLayout) findViewById(R.id.focus_indicator_rotate_layout);
        if (mGestureDispatcher == null) {
            mGestureDispatcher = new GestureDispatcher(this);
            mGestureRecognizer = new GestureRecognizer(this, mGestureDispatcher);
        }
        setGestureListener(mModePicker.getGestureListener());
        // startPreview needs this.
        mPreviewFrameLayout = (PreviewFrameLayout) findViewById(R.id.frame);
        // Set touch focus listener.
        mGestureDispatcher.setSingleTapUpListener(mPreviewFrameLayout);
        // Set Long Press for Object Tracking listener.
        mGestureDispatcher.setLongPressListener(mPreviewFrameLayout);
        mPreviewFrameLayout.setOnSizeChangedListener(this);
        // for other info.
        if (mLocationManager == null) {
            mLocationManager = new BDLocationManager(this, null);
        }
        if (mOrientationListener == null) {
            mOrientationListener = new MyOrientationEventListener(this);
        }
        Log.i(TAG, "initializeForOpeningProcess() mNumberOfCameras="
                + mNumberOfCameras);
        CameraPerformanceTracker.onEvent(TAG,
                CameraPerformanceTracker.NAME_CAMERA_INIT_OPEN_PROCESS,
                CameraPerformanceTracker.ISEND);
    }

    private void updateFocusAndFace() {
        if (getFrameView() != null) {
            getFrameView().clear();
            getFrameView().setVisibility(View.VISIBLE);
            // getFrameView().setDisplayOrientation(mDisplayOrientation);
            int cameraId = mCameraDeviceCtrl.getCameraId();
            CameraInfo info = CameraHolder.instance().getCameraInfo()[cameraId];
            getFrameView().setMirror(
                    info.facing == CameraInfo.CAMERA_FACING_FRONT);
            getFrameView().resume();
        }
        FocusManager focusManager = mCameraDeviceCtrl.getFocusManager();
        if (focusManager != null) {
            focusManager.setFocusAreaIndicator(mFocusAreaIndicator);
            View mFocusIndicator = mFocusAreaIndicator
                    .findViewById(R.id.focus_indicator);
            // Set the length of focus indicator according to preview frame
            // size.
            int len = Math.min(getPreviewFrameWidth(), getPreviewFrameHeight()) / 4;
            ViewGroup.LayoutParams layout = mFocusIndicator.getLayoutParams();
            layout.width = len;
            layout.height = len;
        }
        if (mFrameManager != null && getFrameView() != null) {
            // these case(configuration change), OT is must be false.
            // default view is face view.
            mFrameManager.initializeFrameView(false);
        }
    }

    private void doOnResume() {
        long start = System.currentTimeMillis();
        mOrientationListener.enable();
        /**
         * when launch video camera with MMS open the flash, between the switch
         * camera a call is coming and then reject it,will found the camera view
         * is unclickable because the camera view state is :switch camera so
         * need to restore the view state at here
         * But we should not restore view state when ReviewManager is showing,
         * otherwise, setting/flash/switch camera icons will be shown on UI.
         */

        installIntentFilter();
        callResumableResume();
        mCameraAppUi.checkViewManagerConfiguration();
        // change the file saver listener for foreground Activity.
        long stop = System.currentTimeMillis();
        Log.d(TAG, "doOnResume() consume:" + (stop - start));
    }

    private void clearFocusAndFace() {
        if (getFrameView() != null) {
            getFrameView().clear();
        }
        FocusManager focusManager = mCameraDeviceCtrl.getFocusManager();
        if (focusManager != null) {
            focusManager.removeMessages();
        }
    }

    private boolean isCancelSingleTapUp() {
        if (mCameraAppUi.getViewState() == ViewState.VIEW_STATE_LOMOEFFECT_SETTING) {
            return true;
        }
        return false;
    }
    private List<OnPreferenceReadyListener> mPreferenceListeners =
            new CopyOnWriteArrayList<OnPreferenceReadyListener>();

    private void notifyPreferenceReady() {
		/*prize add Storage path selection function wanzhijuan 2016-10-19 start*/
    	String type = mISettingCtrl.getSettingValue(SettingConstants.KEY_STORAGE_PATH);
    	String path;
    	if (type.equals("external") && Storage.isHaveExternalSDCard()) {
    		updateStorageDirectory();
    		mCameraAppUi.clearRemainAvaliableSpace();
            mCameraAppUi.showRemainHint();
    	} else if (!Storage.isHaveExternalSDCard()) {
    		mISettingCtrl.onSettingChanged(SettingConstants.KEY_SD_MOUNT, String.valueOf(false));
    	}
		/*prize add Storage path selection function wanzhijuan 2016-10-19 end*/
        for (OnPreferenceReadyListener listener : mPreferenceListeners) {
            if (listener != null) {
                listener.onPreferenceReady();
            }
        }
    }

    private List<OnParametersReadyListener> mParametersListeners =
            new CopyOnWriteArrayList<OnParametersReadyListener>();

    private void notifyParametersReady() {
        ViewState curState = mCameraAppUi.getViewState();
        if ((isNonePickIntent()
                && mCameraActor.getMode() != ModePicker.MODE_VIDEO
                && mCameraActor.getMode() != ModePicker.MODE_VIDEO_PIP
                && curState != ViewState.VIEW_STATE_SETTING
                && curState != ViewState.VIEW_STATE_SUB_SETTING
                && curState != ViewState.VIEW_STATE_RECORDING)
                || isStereo3DImageCaptureIntent()) {
            mModePicker.show();
        }
        if (!isSecureCamera()) {
            updateCameraLocationInfo();
        }
        for (OnParametersReadyListener listener : mParametersListeners) {
            if (listener != null) {
                listener.onCameraParameterReady();
            }
        }
        mCameraAppUi.notifyParametersReady();
    }

    private void updateCameraLocationInfo() {
        boolean isLocationOpened = SETTING_ON.equals(mISettingCtrl.getSettingValue(
                SettingConstants.KEY_RECORD_LOCATION));
        boolean isLocationPermissionReady = false;
        if (!isLocationOpened) {
            mLocationManager.recordLocation(false);
            return;
        }
        if (!mIsCheckingLocationPermission) {
            /**
             * it may send permission request when camera launch, so must check whether
             * location is on in camera setting
             */
            isLocationPermissionReady = mPermissionManager.requestCameraLocationPermissions();
            if (isLocationPermissionReady) {
                mLocationManager.recordLocation(true);
            } else {
                /**
                 * it send permission request, and show dialog for user check
                 * the result will be in onRequestPermissionsResult.
                 */
                mIsCheckingLocationPermission = true;
            }
        }
    }

    private List<Resumable> mResumables = new CopyOnWriteArrayList<Resumable>();

    private void callResumableBegin() {
        for (Resumable resumable : mResumables) {
            resumable.begin();
        }
    }

    private void callResumableResume() {
        for (Resumable resumable : mResumables) {
            resumable.resume();
        }
    }

    private void callResumablePause() {
        for (Resumable resumable : mResumables) {
            resumable.pause();
        }
    }

    private void callResumableFinish() {
        for (Resumable resumable : mResumables) {
            resumable.finish();
        }
    }

    // / @}

    // / M: mode change logic @{
    private boolean mIsFromRestore = false;
    private int mOriCameraId = UNKNOWN;
    private ModePicker.OnModeChangedListener mModeChangedListener =
            new ModePicker.OnModeChangedListener() {
        @Override
        public void onModeChanged(int newMode) {
            Log.i(TAG, "onModeChanged(" + newMode + ") current mode = "
                    + mCameraActor.getMode() + ", state=" + mCameraState);
            mPrevMode = mCameraActor.getMode();
            mNextMode = newMode;
            int oldMode = mPrevMode;

            if (mCameraActor.getMode() != newMode) {
                // when mode changed,remaining manager should check whether to
                // show
                mIsModeChanged = true;
                // releaseCameraActor has change mLastMode
                String oldCameraMode = mISettingCtrl
                        .getCameraMode(getModeSettingKey(oldMode));
                String newCameraMode = mISettingCtrl
                        .getCameraMode(getModeSettingKey(newMode));
                /*prize-modify-bugid:53777 preVideo change to video not nedd restartPreview-xiaoping-20180330-start*/
                boolean needRestart = true;
                if ((Integer.parseInt(newCameraMode) == Parameters.CAMERA_MODE_MTK_VDO && Integer.parseInt(oldCameraMode) == Parameters.CAMERA_MODE_MTK_PRV) 
                		|| (Integer.parseInt(newCameraMode) == Parameters.CAMERA_MODE_MTK_PRV && Integer.parseInt(oldCameraMode) == Parameters.CAMERA_MODE_MTK_VDO)) {
                    needRestart = false;
				} else {
	                 needRestart = (!oldCameraMode.equals(newCameraMode))
	                        || Parameters.CAMERA_MODE_MTK_VDO == Integer.parseInt(newCameraMode)
	                        || newMode == ModePicker.MODE_DOUBLECAMERA
	                	    || oldMode == ModePicker.MODE_BOKEH
	                	    || newMode == ModePicker.MODE_BOKEH;
				}
                Log.i(TAG, "needRestart = " + needRestart+"oldCameraMode: "+oldCameraMode+",newCameraMode: "+newCameraMode);
                /*prize-modify-bugid:53777 preVideo change to video not nedd restartPreview-xiaoping-20180330-end*/
                
                // if need restart preview, should do stop preview in last mode
                if (needRestart) {
                	mCameraActor.stopPreview();
                }
                releaseCameraActor(oldMode, newMode);
                mModuleManager.setModeSettingValue(
                        mCameraActor.getCameraModeType(oldMode), SETTING_OFF);
                /*prize-xuchunming-20171010-bugid:40261-start*/
                if(oldMode == ModePicker.MODE_PHOTO) {
                	if(mISettingCtrl.getListPreference(SettingConstants.KEY_HDR) != null) {
                		mISettingCtrl.getListPreference(SettingConstants.KEY_HDR).setValue(mISettingCtrl.getSettingValue(SettingConstants.KEY_HDR));
                	}
                }
                /*prize-xuchunming-20171010-bugid:40261-end*/
                if (isPIPModeSwitch(oldMode, newMode)) {
                    // when user does pip mode switch more quickly,
                    // here try to close camera, camera start up thread may not
                    // done,
                    // should wait to not disturb camera status
                    if (!isPIPMode(oldMode)) {
                        mOriCameraId = getCameraId();
                    }
                    mCameraDeviceCtrl.closeCamera();
                }
                
                /*if(newMode == ModePicker.MODE_DOUBLECAMERA){
                	mCameraDeviceCtrl.closeCamera();
                }*/
                
                if (isFastAfDisabled()
                        && isRefocusSwitchVideo(oldMode, newMode)) {
                    if (newMode == ModePicker.MODE_VIDEO) {
                        mIsStereoToVideoMode = true;
                    }
                    initializeDualCamera(true);
                }
                if (isRefocusSwitchNormal(oldMode, newMode)) {
                    mCameraDeviceCtrl.closeCamera();
                }
                switch (newMode) {
                case ModePicker.MODE_PHOTO:
                    mCameraActor = new PhotoActor(CameraActivity.this,
                            mModuleManager, newMode);
                    break;
                case ModePicker.MODE_FACE_BEAUTY:
                    mCameraActor = new PhotoActor(CameraActivity.this,
                            mModuleManager, newMode);
                    break;
                case ModePicker.MODE_PANORAMA:
                    mCameraActor = new PhotoActor(CameraActivity.this,
                            mModuleManager, newMode);
                    break;
                case ModePicker.MODE_PHOTO_PIP:
                    mCameraActor = new PhotoActor(CameraActivity.this,
                            mModuleManager, newMode);
                    break;
                case ModePicker.MODE_VIDEO:
                    mCameraActor = new VideoActor(CameraActivity.this,
                            mModuleManager, newMode);
                    break;
                case ModePicker.MODE_VIDEO_PIP:
                    mCameraActor = new VideoActor(CameraActivity.this,
                            mModuleManager, newMode);
                    break;
                case ModePicker.MODE_STEREO_CAMERA:
                    mCameraActor = new PhotoActor(CameraActivity.this,
                            mModuleManager, newMode);
                    break;
                case ModePicker.MODE_WATERMARK:
                    mCameraActor = new PhotoActor(CameraActivity.this,
                            mModuleManager, newMode);
                    break;
                case ModePicker.MODE_PREVIDEO:
                	mCameraActor = new PhotoActor(CameraActivity.this,
                            mModuleManager, newMode);
                	break;
                case ModePicker.MODE_DOUBLECAMERA:
                	mCameraActor = new PhotoActor(CameraActivity.this,
                            mModuleManager, newMode);
                	break;
				//gangyun tech add begin
	            case ModePicker.MODE_FACEART:
                    mCameraActor = new PhotoActor(CameraActivity.this, mModuleManager, newMode);
                    break;
		        case ModePicker.MODE_BOKEH:
                    mCameraActor = new PhotoActor(CameraActivity.this, mModuleManager, newMode);
                    /*prize-add-gybokeh mode moved to the second menu-xiaoping-20171114-start*/
                    mModePicker.setDefaultMode(newMode);
                    /*prize-add-gybokeh mode moved to the second menu-xiaoping-20171114-end*/
                    break;
	         	//gangyun tech add end 
                //arc add start  
		        case ModePicker.MODE_PICTURE_ZOOM:
                    mCameraActor = new PhotoActor(CameraActivity.this, mModuleManager, newMode);
                    break;
		        case ModePicker.MODE_LOWLIGHT_SHOT:
                    mCameraActor = new PhotoActor(CameraActivity.this, mModuleManager, newMode);
                    break;
                //arc add end
                //short prevideo
                case ModePicker.MODE_SHORT_PREVIDEO:
                    mCameraActor = new PhotoActor(CameraActivity.this,mModuleManager,newMode);
                    break;
                //add portrait mode
                    case ModePicker.MODE_PORTRAIT:
                        mCameraActor = new PhotoActor(CameraActivity.this,mModuleManager,newMode);
                        break;
                default:
                    mCameraActor = new PhotoActor(CameraActivity.this,
                            mModuleManager, newMode);
                    break;
                }
                // after apply settings, should change preview surface
                // immediately
                mCameraDeviceCtrl.setCameraActor(mCameraActor);
                // startup thread will apply these things after onResume
                if (mPaused || mCameraState == STATE_SWITCHING_CAMERA) {
                    mIsModeChanged = false;
                    mCameraAppUi.onModeChange(newMode, oldMode);
                    Log.i(TAG, "onModeChanged return mPaused = " + mPaused);
                    return;
                }

                if (isPIPModeSwitch(oldMode, mCameraActor.getMode())) {
                    // the first time to update PickerManager when pip changed
                    // to other mode
                    doPIPModeChanged(mOriCameraId);
                    mIsModeChanged = false;
                    Log.i(TAG, "onModeChanged isPIPModeSwitch return");
                    return;
                }
                
                /*if(newMode == ModePicker.MODE_DOUBLECAMERA){
                	 mCameraDeviceCtrl.openCamera(0);
                	 mIsModeChanged = false;
                     Log.i(TAG, "onModeChanged DoubleCamera return");
                }*/
                mModuleManager.setModeSettingValue(
                        mCameraActor.getCameraModeType(newMode), SETTING_ON);
                if (isRefocusSwitchNormal(oldMode, newMode)) {
                    initializeDualCamera(false);
                    mIsModeChanged = false;
                    Log.i(TAG, "onModeChanged isRefocusSwitchNormal return");
                    return;
                }
               
                if(mPendingSwitchCameraId == UNKNOWN && (oldMode == ModePicker.MODE_DOUBLECAMERA || oldMode == ModePicker.MODE_BOKEH)){
                	//mPendingSwitchCameraId = getCameraId();
                }
                
                if(isSwitchingCamera()){
                	 mMainHandler.removeMessages(MSG_SWITCH_CAMERA);
                	 mMainHandler.sendEmptyMessage(MSG_SWITCH_CAMERA);
                	 /*prize-xuchunming-20160919-add double camera activity boot-start*/
                	 mCameraAppUi.setViewState(ViewState.VIEW_STATE_CAMERA_CLOSED);
                	 /*prize-xuchunming-20160919-add double camera activity boot-end*/
                }else{
                	// reset default focus modes.
                    notifyOrientationChanged();
                    mCameraDeviceCtrl.onModeChanged(needRestart);
                }
                mCameraAppUi.onModeChange(newMode, oldMode);
                mIsModeChanged = false;
            }else{
            	if(isSwitchingCamera()){
	               	 mMainHandler.removeMessages(MSG_SWITCH_CAMERA);
	               	 mMainHandler.sendEmptyMessage(MSG_SWITCH_CAMERA);
	               	/*prize-xuchunming-20160919-add double camera activity boot-start*/
	               	 mCameraAppUi.setViewState(ViewState.VIEW_STATE_CAMERA_CLOSED);
	               	/*prize-xuchunming-20160919-add double camera activity boot-end*/
	            }
            }
        }
    };

    private String getModeSettingKey(int mode) {
        String key = null;
        switch (mode) {
        case ModePicker.MODE_PHOTO:
            key = SettingConstants.KEY_NORMAL;
            break;
        case ModePicker.MODE_PANORAMA:
            key = SettingConstants.KEY_PANORAMA;
            break;
        case ModePicker.MODE_VIDEO:
            key = SettingConstants.KEY_VIDEO;
            break;
        case ModePicker.MODE_VIDEO_PIP:
            key = SettingConstants.KEY_VIDEO_PIP;
            break;
        case ModePicker.MODE_PHOTO_PIP:
            key = SettingConstants.KEY_PHOTO_PIP;
            break;
        case ModePicker.MODE_FACE_BEAUTY:
            key = SettingConstants.KEY_FACE_BEAUTY;
            break;
        case ModePicker.MODE_STEREO_CAMERA:
            key = SettingConstants.KEY_REFOCUS;
            break;
        case ModePicker.MODE_WATERMARK:
            key = SettingConstants.KEY_WATERMARK;
            break;
        case ModePicker.MODE_PREVIDEO:
            key = SettingConstants.KEY_PREVIDEO;
            break;
        case ModePicker.MODE_DOUBLECAMERA:
            key = SettingConstants.KEY_NORMAL;
            break;
			
	     //gangyun tech add begin
	    case ModePicker.MODE_FACEART:
            key = SettingConstants.KEY_GYBEAUTY_MODE;
            break;
	    case ModePicker.MODE_BOKEH:
            key = SettingConstants.KEY_GYBOKEH_MODE;
            break;
     	//gangyun tech add end	
        /*arc add start*/
        case ModePicker.MODE_PICTURE_ZOOM:
             key = SettingConstants.KEY_ARC_PICTURE_ZOOM_MODE;
             break;
        case ModePicker.MODE_LOWLIGHT_SHOT:
            key = SettingConstants.KEY_ARC_LOWLIGHT_SHOOT_MODE;
            break;
		/*arc add end*/
		case ModePicker.MODE_SHORT_PREVIDEO:
             key = SettingConstants.KEY_SHORT_PREVIDEO;
             break;
        //add portrait mode
        case ModePicker.MODE_PORTRAIT:
             key = SettingConstants.KEY_MODE_PORTRAIT;
             break;
        default:
            break;
        }
        return key;
    }

    private void releaseCameraActor(int oldMode, int newMode) {
        Log.i(TAG, "releaseCameraActor() mode=" + mCameraActor.getMode());
        boolean shouldHideSetting = false;
        shouldHideSetting = (newMode != ModePicker.MODE_ASD
                && newMode != ModePicker.MODE_HDR
                && oldMode != ModePicker.MODE_ASD && oldMode != ModePicker.MODE_HDR);
        if (shouldHideSetting) {
            mCameraAppUi.collapseViewManager(true);
        }

        mCameraActor.release();
        if (newMode == ModePicker.MODE_VIDEO
                || newMode == ModePicker.MODE_VIDEO_3D
                || newMode == ModePicker.MODE_VIDEO_PIP) {
            Log.i(TAG,
                    "releaseCameraActor setSwipingEnabled(false)  newMode = "
                            + newMode);
        }
    }

    public void onSettingChanged(String key, String value) {
        mISettingCtrl.onSettingChanged(key, value);
    }

    // Camera Decoupling --->begin
    /**
     * because current on Listener and cancel Listener is add on the shutter
     * button and video button so when we want show on and cancel button, first
     * need set the video and photo listener to null
     * @param okOnClickListener callback
     * @param cancelOnClickListener callback
     * @param retatekOnClickListener callback
     * @param playOnClickListener callback
     */
    public void applyReviewCallbacks(OnClickListener okOnClickListener,
            OnClickListener cancelOnClickListener,
            OnClickListener retatekOnClickListener,
            OnClickListener playOnClickListener) {
        mPlayListener = playOnClickListener;
        mRetakeListener = retatekOnClickListener;
    }

    private OnClickListener mPlayListener;
    private OnClickListener mRetakeListener;

    public OnClickListener getPlayListener() {
        return mPlayListener;
    }

    public OnClickListener getRetakeLisenter() {
        return mRetakeListener;
    }

    public void enableOrientationListener() {
        mOrientationListener.enable();
    }

    public void disableOrientationListener() {
        mOrientationListener.disable();
    }
    
    /*PRIZE-12404-wanzhijuan-2016-03-01-start*/
    public void updateGridParameter() {
    	for (OnParametersReadyListener listener : mParametersListeners) {
            if (listener != null && listener instanceof GridManager) {
                listener.onCameraParameterReady();
            }
        }
    }
    /*PRIZE-12404-wanzhijuan-2016-03-01-end*/
    
    private SettingManager.SettingListener mSettingListener = new SettingManager.SettingListener() {
        @Override
        public void onSharedPreferenceChanged(ListPreference preference) {
            Log.d(TAG, "[onSharedPreferenceChanged]");
            if (!isCameraOpened()) {
                return;
            }
            if (preference != null) {
                String settingKey = preference.getKey();
                String value = preference.getValue();
                mISettingCtrl.onSettingChanged(settingKey, value);
                mCameraActor.onSettingChange(settingKey, value);
				/*prize add Storage path selection function wanzhijuan 2016-10-19 start*/
                if (settingKey.equals(SettingConstants.KEY_STORAGE_PATH)) {
                	updateStorageDirectory();
            		mCameraAppUi.clearRemainAvaliableSpace();
                    mCameraAppUi.showRemainHint();
                }
				/*prize add Storage path selection function wanzhijuan 2016-10-19 end*/
            }
            mCameraDeviceCtrl.applyParameters(false);
        }

        @Override
        public void onRestorePreferencesClicked() {
            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG, "[onRestorePreferencesClicked.run]");
                    mIsFromRestore = true;
                    mCameraActor.onRestoreSettings();
                    mCameraAppUi.collapseViewManager(true);
                    /*PRIZE-modify resetSetting-wanzhijuan-2016-05-03-start*/
//                    mCameraAppUi.resetSettings();
                    /*PRIZE-modify resetSetting-wanzhijuan-2016-05-03-end*/
                    SharedPreferences globalPref = mPreferences.getGlobal();
                    SharedPreferences.Editor editor = globalPref.edit();
                    editor.clear();
                    editor.apply();
                    SettingUtils.upgradeGlobalPreferences(globalPref,
                            CameraHolder.instance().getNumberOfCameras());
                    SettingUtils.writePreferredCameraId(globalPref,
                            mCameraDeviceCtrl.getCameraId());

                    int backCameraId = CameraHolder.instance()
                            .getBackCameraId();
                    SettingUtils.restorePreferences(
                            (Context) CameraActivity.this,
                            getSharePreferences(backCameraId),
                            mCameraDeviceCtrl.getParametersExt(),
                            isNonePickIntent());

                    // restore front camera setting
                    int frontCameraId = CameraHolder.instance()
                            .getFrontCameraId();
                    SettingUtils.restorePreferences(
                            (Context) CameraActivity.this,
                            getSharePreferences(frontCameraId),
                            mCameraDeviceCtrl.getParametersExt(),
                            isNonePickIntent());

                    SettingUtils.initialCameraPictureSize(
                            (Context) CameraActivity.this,
                            mCameraDeviceCtrl.getParametersExt(),
                            getSharePreferences());

                    mISettingCtrl.restoreSetting(backCameraId);
                    mISettingCtrl.restoreSetting(frontCameraId);

                    mCameraAppUi.resetZoom();
                    //prize-public-bug:27104 front mode didn't go back to face beauty mode after reset-pengcancan-20161229-start
                    mCameraAppUi.resetSettings();
                    //prize-public-bug:27104 front mode didn't go back to face beauty mode after reset-pengcancan-20161229-end
                    // we should apply parameters if mode is default too.
                    int mode = mCameraActor.getMode();
                    if(getCameraId()==1){
                    	if(getCurrentMode() == ModePicker.MODE_FACE_BEAUTY){
                    		mISettingCtrl.onSettingChanged(SettingConstants.KEY_FACE_BEAUTY, "on");
                    	}else{
                    		mModePicker.setCurrentMode(ModePicker.MODE_FACE_BEAUTY);
                    	}
                    	mCameraActor.onModeBlueDone();
                    	mCameraDeviceCtrl.applyParameters(false);
                    }else if (ModePicker.MODE_PHOTO == mode || !isNonePickIntent()
                            || ModePicker.MODE_PHOTO_SGINLE_3D == mode
                            || ModePicker.MODE_PHOTO_3D == mode) {
                        if (ModePicker.MODE_VIDEO == mode
                                && !isNonePickIntent()) {
                            mISettingCtrl.onSettingChanged(
                                    SettingConstants.KEY_VIDEO, SETTING_ON);
                        }
                        mCameraDeviceCtrl.applyParameters(false);
                    } else {
                        mModePicker.setModePreference(null);
                        mModePicker.setCurrentMode(ModePicker.MODE_PHOTO);
 						/*PRIZE-12404-wanzhijuan-2016-03-01-start*/
                        mCameraDeviceCtrl.updateGridParameter();
                        /*PRIZE-12404-wanzhijuan-2016-03-01-end*/
                    }
                    mIsFromRestore = false;
                    /*PRIZE-modify resetSetting-wanzhijuan-2016-05-03-start*/
                    updateStorageDirectory();
                    /*PRIZE-modify resetSetting-wanzhijuan-2016-05-03-end*/
                }
            };
            /*prize-modify-bugid:55878 the dialog is not displayed vertically when the vertical screen is locked-xiaoping-20180507-start*/
            if (RotationPolicy.isRotationLocked(CameraActivity.this)) {
                Log.i(TAG,"isRotationLocked true ,set orientation 0");
                mOrientationListener.updateCompensation(0);
            }
            /*prize-modify-bugid:55878 the dialog is not displayed vertically when the vertical screen is locked-xiaoping-20180507-end*/
            mCameraAppUi.showAlertDialog(getString(R.string.prize_setting_reset),
                    getString(R.string.confirm_restore_message),
                    getString(android.R.string.cancel), null,
                    getString(android.R.string.ok), runnable);
        }

        @Override
        public void onSettingContainerShowing(boolean show) {
            mModuleManager.onSettingContainerShowing(show);
            if (show) {
            } else {
                if (isFaceBeautyEnable()
                        && getCurrentMode() == ModePicker.MODE_FACE_BEAUTY
                        && !mIsFromRestore) {
                    // when face is detected will show the icon
                    // otherwise don't show
                    Log.i(TAG,
                            "onSettingContainerShowing, will set modify icon stautes true,"
                                    + "and show FB icon");
                    // mLomoEffectsManager.show();
                }
            }
        }

        @Override
        public void onVoiceCommandChanged(int commandId) {
            mModuleManager.onVoiceCommandNotify(commandId);
        }

        @Override
        public void onStereoCameraPreferenceChanged(ListPreference preference,
                int type) {
            if (preference != null
                    && preference.getKey().equals(
                            SettingConstants.KEY_DUAL_CAMERA_MODE)) {
                Log.i(TAG, "onStereoCameraPreferenceChanged, type = " + type);
                if (getCurrentMode() == ModePicker.MODE_STEREO_CAMERA) {
                    if (type == DUAL_CAMERA_ENHANCE_ENABLE) {
                        enableDualCameraExtras();
                    }
                    if (type == DUAL_CAMERA_ENHANCE_DISABLE) {
                        disableDualCameraExtras();
                    }
                    if (type == DUAL_CAMERA_START) {
                        singleDualCameraExtras();
                        mCameraDeviceCtrl.applyParameters(false);
                        return;
                    }
                    mCameraDeviceCtrl.applyParameters(false);
                    return;
                } else {
                    if (type == DUAL_CAMERA_ENHANCE_ENABLE) {
                        enableDualCameraExtras();
                    }
                    if (type == DUAL_CAMERA_ENHANCE_DISABLE) {
                        disableDualCameraExtras();
                    }
                    if (type == DUAL_CAMERA_START) {
                        singleDualCameraExtras();
                        initializeDualCamera(false);
                        return;
                    }
                    if (type == DUAL_CAMERA_SWITCH_IN_REFOCUS) {
                        singleDualCameraExtras();
                        mCameraDeviceCtrl.applyParameters(false);
                        return;
                    }
                    initializeDualCamera(false);
                    return;
                }
            }
        }
    };

    private void enableDualCameraExtras() {
        getListPreference(SettingConstants.ROW_SETTING_DISTANCE).setValueIndex(ON);
        getListPreference(SettingConstants.ROW_SETTING_FAST_AF).setValueIndex(ON);
        onSettingChanged(SettingConstants.KEY_FAST_AF, SETTING_ON);
        onSettingChanged(SettingConstants.KEY_DISTANCE, SETTING_ON);
    }

    private void disableDualCameraExtras() {
        getListPreference(SettingConstants.ROW_SETTING_DISTANCE).setValueIndex(OFF);
        getListPreference(SettingConstants.ROW_SETTING_FAST_AF).setValueIndex(OFF);
        onSettingChanged(SettingConstants.KEY_FAST_AF, SETTING_OFF);
        onSettingChanged(SettingConstants.KEY_DISTANCE, SETTING_OFF);
    }

    private void singleDualCameraExtras() {
        if (SETTING_OFF.equals(getListPreference(SettingConstants.KEY_FAST_AF).getValue())) {
            onSettingChanged(SettingConstants.KEY_FAST_AF, SETTING_OFF);
        } else {
            onSettingChanged(SettingConstants.KEY_FAST_AF, SETTING_ON);
        }
        if (SETTING_OFF.equals(getListPreference(SettingConstants.KEY_DISTANCE).getValue())) {
            onSettingChanged(SettingConstants.KEY_DISTANCE, SETTING_OFF);
        } else {
            onSettingChanged(SettingConstants.KEY_DISTANCE, SETTING_ON);
        }
    }

    public void notifyPreferenceChanged(ListPreference preference) {
        mSettingListener.onSharedPreferenceChanged(preference);
        mCameraAppUi.getCameraView(CommonUiType.SETTING).refresh();
    }

    // / M: for photo info @{
    public String getSelfTimer() {
        String seflTimer = mISettingCtrl
                .getSettingValue(SettingConstants.KEY_SELF_TIMER);
        Log.d(TAG, "getSelfTimer() return " + seflTimer);
        return seflTimer;
    }

    // / M: orientation case @{
    // / M: for listener and resumable convenient functions @{
    private List<OnOrientationListener> mOrientationListeners =
            new CopyOnWriteArrayList<OnOrientationListener>();

    public boolean addOnOrientationListener(OnOrientationListener l) {
        if (!mOrientationListeners.contains(l)) {
            return mOrientationListeners.add(l);
        }
        return false;
    }

    public boolean removeOnOrientationListener(OnOrientationListener l) {
        return mOrientationListeners.remove(l);
    }

    public void setOrientation(boolean lock, int orientation) {
        // Log.i(TAG, "setOrientation orientation=" + orientation +
        // " mOrientationListener.getLock()="
        // + mOrientationListener.getLock() + " lock = " + lock);
        mOrientationListener.setLock(false);
        if (lock) {
            mOrientationListener.onOrientationChanged(orientation);
            mOrientationListener.setLock(true);

        } else {
            mOrientationListener.restoreOrientation();
        }
    }

    private PickerManager.PickerListener mPickerListener = new PickerManager.PickerListener() {
        @Override
        public boolean onCameraPicked(int cameraId) {
            Log.i(TAG, "onCameraPicked(" + cameraId + ") mPaused=" + mPaused
                    + " mPendingSwitchCameraId=" + mPendingSwitchCameraId);
            if (mPaused
                    || mPendingSwitchCameraId != UNKNOWN
                    || !ModeChecker.getModePickerVisible(CameraActivity.this,
                            cameraId, getCurrentMode())
                    || !mCameraDeviceCtrl.isCameraOpened()) {
                return false;
            }

            // Here we always return false for switchCamera will change
            // preference after real switching.
            int frontCameraId = CameraHolder.instance().getFrontCameraId();
            if (!mModuleManager.switchDevice() && isDualCameraDeviceEnable()
                    && frontCameraId != UNKNOWN) {
                return false;
            }
            //prize-public-bug:24918 close torch the time user switching the camera-20161203-pengcancan-start
            if (Parameters.FLASH_MODE_TORCH.equals(mCameraDeviceCtrl.getParameters().getFlashMode())) {
				ParametersHelper.setParametersValue(getModuleManager().getCameraDeviceManager().getCameraDevice(getCameraId()).getParameters()
						, getCameraId(),SettingConstants.KEY_FLASH, Parameters.FLASH_MODE_OFF);
				applyParametersToServer();
			}
            //prize-public-bug:24918 close torch the time user switching the camera-20161203-pengcancan-end

            // Disable all camera controls.
            // setCameraState(STATE_SWITCHING_CAMERA);
            // We need to keep a preview frame for the animation before
            // releasing the camera. This will trigger onPreviewTextureCopied.
            // mRendererManager.copyTexture();
            // mMainHandler.sendEmptyMessage(MSG_SWITCH_CAMERA);
            isSwitchingCameraDone = false;//prize-add for showing the camera user is switching to -pengcancna-20161210
            mCameraAppUi.switchCamera(cameraId);
            mPendingSwitchCameraId = cameraId;
            /*prize-modify-bugid:54752 camera crash on switch camera -xiaoping-20180409-start*/
            mCameraId = cameraId;
            /*prize-modify-bugid:54752 camera crash on switch camera -xiaoping-20180409-start*/
            mModePicker.switchCamera(cameraId);
            /*prize-add-bugid:43497 other apps can not change the cameraid -xiaoping-start*/
            mCameraDeviceCtrl.setCameraId(cameraId);
            /*prize-add-bugid:43497 other apps can not change the cameraid -xiaoping-end*/
            
            /*prize-xuchunming-20180321-bugid:52488-start*/
            mModePicker.setWaitCameraSwitch(true);
            /*prize-xuchunming-20180321-bugid:52488-end*/
            
            if(isNonePickIntent() && cameraId == 1 && getCurrentMode() != ModePicker.MODE_FACE_BEAUTY){ //front camera mode = MODE_FACE_BEAUTY
            	mModePicker.setCurrentMode(ModePicker.MODE_FACE_BEAUTY);
            }else if(isNonePickIntent() && cameraId == 0 && getCurrentMode() != ModePicker.MODE_PHOTO){ //back camera mode = MODE_PHOTO
            	mModePicker.setCurrentMode(ModePicker.MODE_PHOTO);
            }else{                                                                //currentmode is default mode 
            	mModePicker.showSurfaceCover();
            	mMainHandler.removeMessages(MSG_SWITCH_CAMERA);
           	 	//mMainHandler.sendEmptyMessage(MSG_SWITCH_CAMERA);
				mCameraAppUi.setViewState(ViewState.VIEW_STATE_CAMERA_CLOSED);
            }
            /*prize-xuchunming-20160919-add double camera activity boot-start*/
            //mCameraAppUi.setViewState(ViewState.VIEW_STATE_CAMERA_CLOSED);
            /*prize-xuchunming-20160919-add double camera activity boot-end*/
            
            /*prize-update for bugid: 34099-liufan-2017-05-25-start*/
            return true;
            /*prize-update for bugid: 34099-liufan-2017-05-25-end*/
        }

        @Override
        public boolean onSlowMotionPicked(String turnon) {
            mCameraAppUi.resetSettings();
            mISettingCtrl.onSettingChanged(SettingConstants.KEY_SLOW_MOTION,
                    turnon);
            mCameraDeviceCtrl.applyParameters(false);
            return true;
        }

        @Override
        public boolean onHdrPicked(String value) {
            Log.i(TAG, "[onHdrPicked], value:" + value);
            //mCameraActor.stopPreview();
            mISettingCtrl.onSettingChanged(SettingConstants.KEY_HDR, value);
            if (SETTING_ON.equals(value)) {
                mCameraAppUi.showInfo(getString(R.string.hdr_guide_capture),
                        SHOW_INFO_LENGTH_LONG,(int)getResources().getDimension(R.dimen.info_top));
            }
            /*prize-xuchunming-20160907-bugid:21212-start*/
            else{
            	mCameraAppUi.showIndicator(1);
            }
            /*prize-xuchunming-20160907-bugid:21212-end*/
            
            // open/close hdr need to stop/start preview.
            /*prize-modify-bugid:57649 constantly restarting the preview causes a black screen-xiaoping-20180522-start*/
            mCameraDeviceCtrl.applyParameters(false);
            /*prize-modify-bugid:57649 constantly restarting the preview causes a black screen-xiaoping-20180522-end*/
            return true;
        }

        @Override
        public boolean onGesturePicked(String value) {
            Log.i(TAG, "[onGesturePicked], value:" + value);
            mISettingCtrl.onSettingChanged(SettingConstants.KEY_GESTURE_SHOT, value);
            mCameraDeviceCtrl.applyParameters(false);
            return true;
        }

        @Override
        public boolean onSmilePicked(String value) {
            Log.i(TAG, "[onGesturePicked], value:" + value);
            mISettingCtrl.onSettingChanged(SettingConstants.KEY_SMILE_SHOT, value);
            mCameraDeviceCtrl.applyParameters(false);
            return true;
        }

        @Override
        public boolean onFlashPicked(String flashMode) {
            mISettingCtrl.onSettingChanged(SettingConstants.KEY_FLASH, flashMode);
            applyParametersToServer();
            //mMainHandler.sendEmptyMessage(MSG_APPLY_PARAMETERS_WHEN_IDEL);
            return true;
        }

        @Override
        public boolean onStereoPicked(boolean stereoType) {
            // in n3d mode, press home key to exit camera, press camera icon to
            // launch camera and click 2d/3d switch icon quickly
            if (mPaused || mPendingSwitchCameraId != UNKNOWN) {
                return false;
            }
            // switch 2d <---> 3d mode
            mIsStereoMode = !SettingUtils.readPreferredCamera3DMode(
                    mPreferences).equals(SettingUtils.STEREO3D_ENABLE);
            SettingUtils.writePreferredCamera3DMode(mPreferences,
                    mIsStereoMode ? SettingUtils.STEREO3D_ENABLE
                            : SettingUtils.STEREO3D_DISABLE);

            if (mModePicker != null) {
                mModePicker.setCurrentMode(getCurrentMode());
            }
            mCameraAppUi.refreshModeRelated();
            // mSettingManager.reInflate();

            return true;
        }

        @Override
        public boolean onModePicked(int mode, String value,
                ListPreference preference) {
            mModePicker.setModePreference(preference);
            // when click hdr, smile shot, the ModePicker view should be
            // disabled during changed mode
            mModePicker.setEnabled(false);
            if (getCurrentMode() == mode) {
                // if current mode is HDR or Smile shot, then should change to
                // photo mode.
                mModePicker.setCurrentMode(ModePicker.MODE_PHOTO);
            } else {
                // if current mode is not HDR or Smile shot, it must be photo
                // mode, then
                // set current mode as HDR or Smile shot.
                mModePicker.setCurrentMode(mode);
            }
            return true;
        }
        /*arc add start*/
		@Override
		public boolean onPicSelfiePicked(String value) {
			
			// TODO Auto-generated method stub
			mISettingCtrl.onSettingChanged(SettingConstants.KEY_ARC_PICSELFIE_ENABLE, value);
	        mCameraDeviceCtrl.applyParameters(false);

            /*prize-add-picSelfie Turn off the prompt-xiaoping-20171012-start*/
            if (SETTING_ON.equals(value)) {
                mCameraAppUi.showInfo(getString(R.string.picselfie_open_tips_on),
                        SHOW_INFO_LENGTH_LONG,(int)getResources().getDimension(R.dimen.info_top));
            } else if (SETTING_OFF.equals(value)){
                mCameraAppUi.showInfo(getString(R.string.picselfie_open_tips_off),
                        SHOW_INFO_LENGTH_LONG,(int)getResources().getDimension(R.dimen.info_top));
            }
            /*prize-add-picSelfie Turn off the prompt-xiaoping-20171012-end*/
			return true;
		}
		/*arc add end*/
    };

    public boolean isSwitchingCamera() {
        return mPendingSwitchCameraId != UNKNOWN;
    }

    public boolean isNonePickIntent() {
    	/*prize-xuchunming-20180523-add shortcuts-start*/
        return PICK_TYPE_NORMAL == mPickType || isShortCut();
        /*prize-xuchunming-20180523-add shortcuts-end*/
    }

    public boolean isImageCaptureIntent() {
        return PICK_TYPE_PHOTO == mPickType;
    }

    public boolean isVideoCaptureIntent() {
        return PICK_TYPE_VIDEO == mPickType;
    }

    public boolean isVideoWallPaperIntent() {
        return PICK_TYPE_WALLPAPER == mPickType;
    }

    public boolean isStereo3DImageCaptureIntent() {
        return PICK_TYPE_STEREO3D == mPickType;
    }

    public float getWallpaperPickAspectio() {
        return mWallpaperAspectio;
    }

    public boolean isQuickCapture() {
        return mQuickCapture;
    }

    public Uri getSaveUri() {
        return mSaveUri;
    }

    public PreviewSurfaceView getPreviewSurfaceView() {
        return (mCameraDeviceCtrl == null) ? null : mCameraDeviceCtrl
                .getSurfaceView();
    }

    
    public String getCropValue() {
        return mCropValue;
    }

    public boolean isDualCameraDeviceEnable() {
        return isPIPMode(getCurrentMode());
    }

    public void setResultExAndFinish(int resultCode) {
        setResultEx(resultCode);
        finish();
        clearUserSettings();
    }

    public void setResultExAndFinish(int resultCode, Intent data) {
        setResultEx(resultCode, data);
        finish();
        clearUserSettings();
    }

    public boolean isVideoMode() {
        Log.d(TAG, "isVideoMode() getCurrentMode()=" + getCurrentMode());
        return (ModePicker.MODE_VIDEO == getCurrentMode()
                || ModePicker.MODE_VIDEO_3D == getCurrentMode()
                || ModePicker.MODE_VIDEO_PIP == getCurrentMode());
    }

    private void notifyOrientationChanged() {
        Log.i(TAG, "[notifyOrientationChanged] mOrientationCompensation="
                + mOrientationCompensation + ", mDisplayRotation="
                + mDisplayRotation);
        /*prize-xuchunming-20161201-bugid:25478-start*/
        
        /*prize-xuchunming-20171218-bugid:45614-start*/
        //if(isRotationLocked(getApplicationContext()) == false){
        /*prize-xuchunming-20171218-bugid:45614-end*/
        	for (OnOrientationListener listener : mOrientationListeners) {
                if (listener != null) {
                    listener.onOrientationChanged(mOrientationCompensation);
                } 
            }
        /*prize-xuchunming-20171218-bugid:45614-start*/
        //}
        /*prize-xuchunming-20171218-bugid:45614-end*/
    }

    private class MyOrientationEventListener extends OrientationEventListener {
        private boolean mLock = false;
        private int mRestoreOrientation;

        public MyOrientationEventListener(Context context) {
            super(context);
        }

        public void setLock(boolean lock) {
            mLock = lock;
        }

        public void restoreOrientation() {
            onOrientationChanged(mRestoreOrientation);
        }

        @Override
        public void onOrientationChanged(int orientation) {
            // We keep the last known orientation. So if the user first orient
            // the camera then point the camera to floor or sky, we still have
            // the correct orientation.
            if (orientation == ORIENTATION_UNKNOWN) {
                Log.w(TAG,
                        "[onOrientationChanged]orientation is ORIENTATION_UNKNOWN,return.");
                return;
            }
            int newOrientation = Util.roundOrientation(orientation,
                    mRestoreOrientation);

            if (!mLock) {
                updateOrientation(newOrientation);
                /*prize-modify-bugid:40009 Horizontal state prompt-xiaoping-20171012-start*/
                mCameraAppUi.setOrientation(newOrientation);
                /*prize-modify-bugid:40009 Horizontal state prompt-xiaoping-20171012-end*/
            }

            if (mRestoreOrientation != newOrientation) {
                mRestoreOrientation = newOrientation;
                mModuleManager.onOrientationChanged(mRestoreOrientation);
            }

        }

        private void updateOrientation(int orientation) {
            int newDisplayRotation = Util.getDisplayRotation(CameraActivity.this);
            if (mOrientation == orientation
                    && newDisplayRotation == mDisplayRotation) {
                return;
            }
            Log.d(TAG, "[updateOrientation]orientation:" + orientation
                    + ",mOrientation:" + mOrientation + ",newDisplayRotation:"
                    + newDisplayRotation + ",mDisplayRotation:"
                    + mDisplayRotation);
            if (newDisplayRotation != mDisplayRotation) {
                mDisplayRotation = newDisplayRotation;
                mCameraDeviceCtrl.setDisplayOrientation();
            }

            mOrientation = orientation;
            updateCompensation(mOrientation);
            mCameraDeviceCtrl.onOrientationChanged(mOrientation);

        }

        private void updateCompensation(int orientation) {
            int orientationCompensation = (orientation + Util
                    .getDisplayRotation(CameraActivity.this)) % 360;
            if (mOrientationCompensation != orientationCompensation) {
                Log.d(TAG, "[updateCompensation] mCompensation:"
                        + mOrientationCompensation + ", compensation:"
                        + orientationCompensation);
                mOrientationCompensation = orientationCompensation;
                mModuleManager.onCompensationChanged(mOrientationCompensation);
                notifyOrientationChanged();
            }
        }
    }

    // / @}

    // / M: for pick logic @{
    /**
     * An unpublished intent flag requesting to start recording straight away
     * and return as soon as recording is stopped. TODO: consider publishing by
     * moving into MediaStore.
     */
    private static final String EXTRA_QUICK_CAPTURE = "android.intent.extra.quickCapture";
    private static final String EXTRA_VIDEO_WALLPAPER_IDENTIFY = "identity"; // String
    private static final String EXTRA_VIDEO_WALLPAPER_RATION = "ratio"; // float
    private static final String EXTRA_VIDEO_WALLPAPER_IDENTIFY_VALUE = "com.mediatek.vlw";
    private static final String EXTRA_PHOTO_CROP_VALUE = "crop";
    private static final String ACTION_STEREO3D = "android.media.action.IMAGE_CAPTURE_3D";
    private static final String EXTRA_RESOLUTION_LIMIT =
            "mediatek.intent.extra.EXTRA_RESOLUTION_LIMIT";
    private static final float WALLPAPER_DEFAULT_ASPECTIO = 1.2f;

    private static final int PICK_TYPE_NORMAL = 0;
    private static final int PICK_TYPE_PHOTO = 1;
    private static final int PICK_TYPE_VIDEO = 2;
    private static final int PICK_TYPE_WALLPAPER = 3;
    private static final int PICK_TYPE_STEREO3D = 4;
    /*prize-xuchunming-20180523-add shortcuts-start*/
	private static final int PICK_TYPE_SHORTCUT_PORTRAIT = 5;
	private static final int PICK_TYPE_SHORTCUT_SUPERZOOM = 6;
	private static final int PICK_TYPE_SHORTCUT_VIDEO = 7;
	private static final int PICK_TYPE_SHORTCUT_FACEBEAUTY = 8;
	/*prize-xuchunming-20180523-add shortcuts-end*/
    private int mPickType;
    private boolean mQuickCapture;
    private float mWallpaperAspectio;
    private Uri mSaveUri;
    private long mLimitedSize;
    private String mCropValue;
    private int mLimitedDuration;
    private int mLimitedResoltion;

    // add for MMS ->VideoCamera->playVideo,and then pause the
    // video,at this time ,we don't want share the video
    public boolean mCanShowVideoShare = true;
    public static final String CAN_SHARE = "CanShare";

    private void parseIntent() {
        Intent intent = getIntent();
        String action = intent.getAction();
        if (MediaStore.ACTION_IMAGE_CAPTURE.equals(action)
                || MediaStore.ACTION_IMAGE_CAPTURE_SECURE.equals(action)) {
            mPickType = PICK_TYPE_PHOTO;
        } else if (EXTRA_VIDEO_WALLPAPER_IDENTIFY_VALUE.equals(intent
                .getStringExtra(EXTRA_VIDEO_WALLPAPER_IDENTIFY))) {
            mWallpaperAspectio = intent.getFloatExtra(EXTRA_VIDEO_WALLPAPER_RATION,
                    WALLPAPER_DEFAULT_ASPECTIO);
            intent.putExtra(EXTRA_QUICK_CAPTURE, true);
            mPickType = PICK_TYPE_WALLPAPER;
        } else if (MediaStore.ACTION_VIDEO_CAPTURE.equals(action)) {
            mPickType = PICK_TYPE_VIDEO;
        } else if (ACTION_STEREO3D.equals(action)) {
            mPickType = PICK_TYPE_STEREO3D;
        /*prize-xuchunming-20180523-add shortcuts-start*/
        }else if(setShortCut(action)){
			Log.i(TAG, "parseIntent() this action is shortcut :"+action);
		/*prize-xuchunming-20180523-add shortcuts-end*/
		} else {
            mPickType = PICK_TYPE_NORMAL;
            SettingUtils.setLimitResolution(0);
        }
        /*prize-xuchunming-20180523-add shortcuts-start*/
        if (mPickType != PICK_TYPE_NORMAL && isShortCut() == false) {
        /*prize-xuchunming-20180523-add shortcuts-end*/
            mQuickCapture = intent.getBooleanExtra(EXTRA_QUICK_CAPTURE, false);
            mSaveUri = intent.getParcelableExtra(MediaStore.EXTRA_OUTPUT);
            mLimitedSize = intent.getLongExtra(MediaStore.EXTRA_SIZE_LIMIT, 0L);
            mCropValue = intent.getStringExtra(EXTRA_PHOTO_CROP_VALUE);
            mLimitedDuration = intent.getIntExtra(MediaStore.EXTRA_DURATION_LIMIT, 0);
            mLimitedResoltion = intent.getIntExtra(EXTRA_RESOLUTION_LIMIT, 0);
            SettingUtils.setLimitResolution(mLimitedResoltion);
            mIsAppGuideFinished = true;
        }
        Log.i(TAG, "parseIntent() mPickType=" + mPickType + ", mQuickCapture="
                + mQuickCapture + ", mSaveUri=" + mSaveUri + ", mLimitedSize="
                + mLimitedSize + ", mCropValue=" + mCropValue
                + ", mLimitedDuration=" + mLimitedDuration);
        if (true) {
            Log.d(TAG, "parseIntent() action=" + intent.getAction());
            Bundle extra = intent.getExtras();
            if (extra != null) {
                mCanShowVideoShare = extra.getBoolean(CAN_SHARE, true);
                for (String key : extra.keySet()) {
                    Log.v(TAG, "parseIntent() extra[" + key + "]=" + extra.get(key));
                }
            }
            if (intent.getCategories() != null) {
                for (String key : intent.getCategories()) {
                    Log.v(TAG, "parseIntent() getCategories=" + key);
                }
            }
            Log.v(TAG, "parseIntent() data=" + intent.getData());
            Log.v(TAG, "parseIntent() flag=" + intent.getFlags());
            Log.v(TAG, "parseIntent() package=" + intent.getPackage());
            Log.v(TAG, "mCanShowVideoShare = " + mCanShowVideoShare);
        }
    }

    // / M: We will restart activity for changed default path. @{
    private boolean mForceFinishing; // forcing finish

    private boolean isMountPointChanged() {
        boolean changed = false;
        String mountPoint = Storage.getMountPoint();
        Storage.updateDefaultDirectory();
        if (!mountPoint.equals(Storage.getMountPoint())) {
            changed = true;
        }
        Log.d(TAG, "isMountPointChanged() old=" + mountPoint + ", new="
                + Storage.getMountPoint() + ", return " + changed);
        return changed;
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "mReceiver.onReceive(" + intent + ")");
            String action = intent.getAction();
            if (action == null) {
                Log.d(TAG, "[mReceiver.onReceive] action is null");
                return;
            }

            switch (action) {

            case Intent.ACTION_MEDIA_EJECT:
				/*prize add Storage path selection function wanzhijuan 2016-10-19 start*/
            	updateStoragePath(false);
				/*prize add Storage path selection function wanzhijuan 2016-10-19 end*/
                if (isSameStorage(intent)) {
                    Storage.setStorageReady(false);
                    mCameraActor.onMediaEject();
                }
                break;

            case Intent.ACTION_MEDIA_UNMOUNTED:
				/*prize add Storage path selection function wanzhijuan 2016-10-19 start*/
            	updateStoragePath(false);
				/*prize add Storage path selection function wanzhijuan 2016-10-19 end*/
                if (isSameStorage(intent)) {
                    String internal = Storage.getInternalVolumePath();
                    if (internal != null) {
                        if (!Storage.updateDirectory(internal)) {
                            setPath(Storage.getCameraScreenNailPath());
                        }
                    }
                } else {
                    if (!FeatureSwitcher.is2SdCardSwapSupport()) {
                        updateStorageDirectory();
                    }
                }
                mCameraAppUi.clearRemainAvaliableSpace();
                mCameraAppUi.showRemainHint();
                break;

            case Intent.ACTION_MEDIA_MOUNTED:
				/*prize add Storage path selection function wanzhijuan 2016-10-19 start*/
            	updateStoragePath(true);
				/*prize add Storage path selection function wanzhijuan 2016-10-19 end*/
                updateStorageDirectory();
                if (isSameStorage(intent)) {
                    Storage.setStorageReady(true);
                    mCameraAppUi.clearRemainAvaliableSpace();
                    mCameraAppUi.showRemainHint();
                }
                break;

            case Intent.ACTION_MEDIA_CHECKING:
                if (isSameStorage(intent)) {
                    mCameraAppUi.clearRemainAvaliableSpace();
                    mCameraAppUi.showRemainHint();
                }
                break;

            case Intent.ACTION_MEDIA_SCANNER_STARTED:
                if (!FeatureSwitcher.is2SdCardSwapSupport()) {
                    updateStorageDirectory();
                }
                if (isSameStorage(intent.getData())) {
                    mCameraAppUi.showToast(R.string.wait);
                }
                break;

            case Intent.ACTION_MEDIA_SCANNER_FINISHED:
                if (isSameStorage(intent.getData())) {
                    mCameraAppUi.clearRemainAvaliableSpace();
                    mCameraAppUi.showRemainHint();
                    mCameraAppUi.forceThumbnailUpdate();
                }
                break;

            default:
                break;
            }
        }
    };
    
	/*prize add Storage path selection function wanzhijuan 2016-10-19 start*/
    private void updateStoragePath(boolean isMount) {
    	if (isMount) {
    		if (Storage.isHaveExternalSDCard()) { //Sd card mount
//    			ListPreference preference = mISettingCtrl.getListPreference(SettingConstants.KEY_STORAGE_PATH);
//    			mSettingListener.onSharedPreferenceChanged(preference);
    			mISettingCtrl.onSettingChanged(SettingConstants.KEY_SD_MOUNT, String.valueOf(true));
    			mCameraAppUi.refreshSetting();
    		}
    	} else {
    		if (!Storage.isHaveExternalSDCard()) { //Sd card unmount
//    			ListPreference preference = mISettingCtrl.getListPreference(SettingConstants.KEY_STORAGE_PATH);
//    			mSettingListener.onSharedPreferenceChanged(preference);
    			mISettingCtrl.onSettingChanged(SettingConstants.KEY_SD_MOUNT, String.valueOf(false));
    			mCameraAppUi.refreshSetting();
    		}
    	}
    }
	/*prize add Storage path selection function wanzhijuan 2016-10-19 end*/

    private void updateStorageDirectory() {
        if (!Storage.updateDefaultDirectory()) {
            setPath(Storage.getCameraScreenNailPath());
        }
    }

    private boolean isSameStorage(Intent intent) {
        StorageVolume storage = (StorageVolume) intent
                .getParcelableExtra(StorageVolume.EXTRA_STORAGE_VOLUME);

        boolean same = false;
        String mountPoint = null;
        String intentPath = null;
        if (storage != null) {
            mountPoint = Storage.getMountPoint();
            intentPath = storage.getPath();
            if (mountPoint != null && mountPoint.equals(intentPath)) {
                same = true;
            }
        }
        Log.d(TAG, "isSameStorage() mountPoint=" + mountPoint + ", intentPath="
                + intentPath + ", return " + same);
        return same;
    }

    private boolean isSameStorage(Uri uri) {
        if (!Storage.updateDefaultDirectory()) {
            Log.i(TAG, "isSameStorage(uri)/same= updateDefaultDirectory");
            setPath(Storage.getCameraScreenNailPath());
        }
        boolean same = false;
        String mountPoint = null;
        String intentPath = null;
        if (uri != null) {
            mountPoint = Storage.getMountPoint();
            intentPath = uri.getPath();
            if (mountPoint != null && mountPoint.equals(intentPath)) {
                same = true;
            }
        }
        Log.d(TAG, "isSameStorage(" + uri + ") mountPoint=" + mountPoint
                + ", intentPath=" + intentPath + ", return " + same);
        mCameraAppUi.forceThumbnailUpdate();
        return same;
    }

    private void installIntentFilter() {
        // install an intent filter to receive SD card related events.
        IntentFilter intentFilter = new IntentFilter(
                Intent.ACTION_MEDIA_MOUNTED);
        intentFilter.addAction(Intent.ACTION_MEDIA_EJECT);
        intentFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        intentFilter.addAction(Intent.ACTION_MEDIA_SCANNER_STARTED);
        intentFilter.addAction(Intent.ACTION_MEDIA_SCANNER_FINISHED);
        intentFilter.addAction(Intent.ACTION_MEDIA_CHECKING);
        intentFilter.addDataScheme("file");
        registerReceiver(mReceiver, intentFilter);
    }

    private void uninstallIntentFilter() {
        unregisterReceiver(mReceiver);
    }

    private void clearUserSettings() {
        Log.d(TAG, "clearUserSettings() isFinishing()=" + isFinishing());
        if (mISettingCtrl != null && isFinishing()) {
            mISettingCtrl.resetSetting();
        }
    }

    // / M: for other things @{
    private void addIdleHandler() {
        MessageQueue queue = Looper.myQueue();
        queue.addIdleHandler(new MessageQueue.IdleHandler() {
            @Override
            public boolean queueIdle() {
                Storage.ensureOSXCompatible();
                return false;
            }
        });
    }

    // / @}

    private boolean isAppGuideFinished() {
        return mIsAppGuideFinished;
    }

    private void setAppGuideFinished(boolean mAppGuideFinished) {
        this.mIsAppGuideFinished = mAppGuideFinished;
    }

    // 3D Related
    public boolean isStereoMode() {
        return mIsStereoMode;
    }

    // SurfaceView For Native 3D
    // For SurfaceTexture need GPU,Native Need GPU,Then GPU crushed!

    private void initializeStereo3DMode() {
        if (isStereo3DImageCaptureIntent()) {
            mIsStereoMode = true;
            SettingUtils.writePreferredCamera3DMode(mPreferences,
                    SettingUtils.STEREO3D_ENABLE);
        } else {
            // when initialize we always write disable state
            mIsStereoMode = false;
            SettingUtils.writePreferredCamera3DMode(mPreferences,
                    SettingUtils.STEREO3D_DISABLE);
        }
    }

    private boolean isFaceBeautyEnable() {
        String facebautyMode = getPreferences().getString(
                SettingConstants.KEY_MULTI_FACE_BEAUTY,
                getResources().getString(R.string.face_beauty_single_mode));
        // Log.i(TAG, "facebautyMode = " + facebautyMode);
        return !getResources().getString(R.string.pref_face_beauty_mode_off)
                .equals(facebautyMode);
    }

    private IExternalDeviceCtrl.Listener mListener = new IExternalDeviceCtrl.Listener() {

        @Override
        public void onStateChanged(boolean enabled) {
            Log.i(TAG, "[onStateChanged] enable = " + enabled);
            //mModuleManager.setVideoRecorderEnable(!enabled);
        }
    };

    //**********************************************************TODO PIP and stereo
    private boolean mIsStereoToVideoMode;
    public boolean isNeedOpenStereoCamera() {
        boolean enable = false;
        enable = (SettingUtils.readPreferredStereoCamera(mPreferences))
                .equals(SettingUtils.STEREO_CAMERA_ON)
                || getCurrentMode() == ModePicker.MODE_STEREO_CAMERA;
        Log.d(TAG, "[isNeedOpenStereoCamera] mIsStereoToVideoMode:"
                + mIsStereoToVideoMode + ",mode:" + getCurrentMode()
                + ",enable:" + enable);
        if (mIsModeChanged
                && getListPreference(SettingConstants.ROW_SETTING_FAST_AF) != null
                && SETTING_ON.equals(getListPreference(
                        SettingConstants.ROW_SETTING_FAST_AF).getValue())) {
            enable = true;
        }
        if (mIsStereoToVideoMode) {
            enable = false;
            mIsStereoToVideoMode = false;
        }
        if (getCurrentMode() == ModePicker.MODE_PHOTO_PIP) {
            enable = false;
        }
        enable = enable && isNonePickIntent();
        Log.i(TAG, "needOpenStereoCamera enable = " + enable);
        return enable;
    }

    private boolean isPIPMode(int mode) {
        return mode == ModePicker.MODE_VIDEO_PIP
                || mode == ModePicker.MODE_PHOTO_PIP;
    }

    private boolean isPIPModeSwitch(int lastMode, int newMode) {
        return (isPIPMode(lastMode) && !isPIPMode(newMode))
                || (!isPIPMode(lastMode) && isPIPMode(newMode));
    }

    private boolean isRefocusSwitchNormal(int lastMode, int newMode) {
        return (lastMode == ModePicker.MODE_STEREO_CAMERA && newMode != ModePicker.MODE_VIDEO)
                || (lastMode != ModePicker.MODE_STEREO_CAMERA
                && newMode == ModePicker.MODE_STEREO_CAMERA);
    }

    private boolean isRefocusSwitchVideo(int lastMode, int newMode) {
        Log.i(TAG, "isRefocusSwitchVideo lastMode = " + lastMode
                + ", newMode = " + newMode);
        return lastMode == ModePicker.MODE_STEREO_CAMERA
                && newMode == ModePicker.MODE_VIDEO;
    }

    private boolean isFastAfDisabled() {
        Log.i(TAG, "isFastAfDisabled"
                + getListPreference(SettingConstants.KEY_FAST_AF));
        if (getListPreference(SettingConstants.KEY_FAST_AF) == null) {
            return true;
        } else if (SETTING_OFF.equals(getListPreference(SettingConstants.KEY_FAST_AF)
                .getValue())) {
            return true;
        } else {
            return false;
        }
    }

    private void initializeDualCamera(boolean needSync) {
        Log.i(TAG, "initializeDualCamera");
        int cameraId = CameraHolder.instance().getBackCameraId();
        mCameraDeviceCtrl.openStereoCamera(cameraId, needSync);
        mPendingSwitchCameraId = cameraId;
    }

    public int getPendingSwitchCameraId(){
    	return mPendingSwitchCameraId;
    }
    private void doPIPModeChanged(int cameraId) {
        Log.i(TAG, "doPIPModeChanged");
        // when switch to open main and sub camera, should close first
        mCameraAppUi.collapseViewManager(true);
        clearFocusAndFace();
        mCameraDeviceCtrl.unInitializeFocusManager();

        // here set these variables null to initialize them again.
        mPreferences.setLocalId(CameraActivity.this, cameraId);
        SettingUtils.upgradeLocalPreferences(mPreferences.getLocal());
        SettingUtils.writePreferredCameraId(mPreferences, cameraId);

        mCameraDeviceCtrl.openCamera(cameraId);
    }

    // ****************************************************************
    public CameraManager.CameraProxy getCameraDevice() {
        return mCameraDeviceCtrl.getCameraDevice();
    }

	//gangyun tech add begin
	public CameraDeviceCtrl getCameraDeviceCtrl() {
		return mCameraDeviceCtrl;
	}
	//gangyun tech add end
	

    public FocusManager getFocusManager() {
        return mCameraDeviceCtrl.getFocusManager();
    }

    public Parameters getParameters() {
        return mCameraDeviceCtrl.getParameters();
    }

    public Parameters getTopParameters() {
        return mCameraDeviceCtrl.getTopParameters();
    }

    public int getCameraId() {
        return mCameraDeviceCtrl.getCameraId();
    }

    // for PIP
    public int getOriCameraId() {
        return mOriCameraId;
    }

    public int getTopCameraId() {
        return mCameraDeviceCtrl.getTopCameraId();
    }

    public void applyParameterForCapture(final SaveRequest request) {
        mCameraDeviceCtrl.applyParameterForCapture(request);
    }

    public void applyParameterForFocus(final boolean setArea) {
        mCameraDeviceCtrl.applyParameterForFocus(setArea);
    }

    public int getDisplayOrientation() {
        return mCameraDeviceCtrl.getDisplayOrientation();
    }

    public void startAsyncZoom(final int zoomValue) {
        mCameraDeviceCtrl.startAsyncZoom(zoomValue);
    }

    public boolean isCameraIdle() {
        return mCameraDeviceCtrl.isCameraIdle();
    }

    public void hideRootCover() {
        mCameraDeviceCtrl.hideRootCover();
    }

    private void keepMediaProviderInstance() {
        // We want to keep a reference to MediaProvider in camera's lifecycle.
        // TODO: Utilize mMediaProviderClient instance to replace
        // ContentResolver calls.
        Log.i(TAG, "keepMediaProviderInstance() entry mMediaProviderClient =  "
                + mMediaProviderClient);
        if (mMediaProviderClient == null) {
            mMediaProviderClient = getContentResolver().acquireContentProviderClient(
                    MediaStore.AUTHORITY);
            Log.i(TAG, "keepMediaProviderInstance() exit mMediaProviderClient =  "
                    + mMediaProviderClient);
        }
    }

    private class ModuleCtrlImpl implements IModuleCtrl {
        private CameraActivity mCamera;

        public ModuleCtrlImpl(CameraActivity camera) {
            mCamera = camera;
        }

        @Override
        public boolean applyFocusParameters(boolean setArea) {
            mCameraDeviceCtrl.applyParameterForFocus(setArea);
            return true;
        }

        @Override
        public int getOrientation() {
            return mOrientation;
        }

        @Override
        public int getDisplayOrientation() {
            return mCameraDeviceCtrl.getDisplayOrientation();
        }

        @Override
        public int getDisplayRotation() {
            return mDisplayRotation;
        }

        @Override
        public int getOrientationCompensation() {
            return mOrientationCompensation;
        }

        @Override
        public boolean lockOrientation() {
            Log.d(TAG, "[lockOrientation]...");
            mCamera.setOrientation(true,
                    OrientationEventListener.ORIENTATION_UNKNOWN);
            return true;
        }

        @Override
        public boolean unlockOrientation() {
            Log.d(TAG, "[unlockOrientation]...");
            mCamera.setOrientation(false,
                    OrientationEventListener.ORIENTATION_UNKNOWN);
            return true;
        }

        @Override
        public void setOrientation(boolean lock, int orientation) {
            Log.d(TAG, "[setOrientation] lock = " + lock + ", orientation = "
                    + orientation);
            mCamera.setOrientation(lock, orientation);
        }

        @Override
        public boolean enableOrientationListener() {
            mOrientationListener.enable();
            return true;
        }

        @Override
        public boolean disableOrientationListener() {
            mOrientationListener.disable();
            return true;
        }

        public Location getLocation() {
            return mLocationManager.getCurrentLocation();
        }

        public Uri getSaveUri() {
            return mSaveUri;
        }

        public String getCropValue() {
            return mCropValue;
        }

        public int getResolution() {
            return mLimitedResoltion;
        }

        public void setResultAndFinish(int resultCode) {
            mCamera.setResultExAndFinish(resultCode);
        }

        public void setResultAndFinish(int resultCode, Intent data) {
            mCamera.setResultExAndFinish(resultCode, data);
        }

        public boolean isSecureCamera() {
            return mSecureCamera;
        }

        public boolean isImageCaptureIntent() {
            return PICK_TYPE_PHOTO == mPickType;
        }

        @Override
        public void startFaceDetection() {
            mCameraActor.startFaceDetection();
        }

        @Override
        public void stopFaceDetection() {
            mCameraActor.stopFaceDetection();
        }

        @Override
        public boolean isVideoCaptureIntent() {
            return PICK_TYPE_VIDEO == mPickType;
        }

        @Override
        public boolean isNonePickIntent() {
        	/*prize-xuchunming-20180523-add shortcuts-start*/
            return PICK_TYPE_NORMAL == mPickType || isShortCut() == true;
            /*prize-xuchunming-20180523-add shortcuts-end*/
        }

        @Override
        public Intent getIntent() {
            return mCamera.getIntent();
        }

        @Override
        public boolean isQuickCapture() {
            return mQuickCapture;
        }

        @Override
        public void backToLastMode() {
            mCameraDeviceCtrl.waitCameraStartUpThread(false);
            setPreviewCallback();
            mModePicker.setCurrentMode(mPrevMode);
        }

        @Override
        public void backToCallingActivity(int resultCode, Intent data) {
            setResultExAndFinish(resultCode, data);
        }

        @Override
        public boolean getSurfaceTextureReady() {
            // TODO
            return true;
        }

        @Override
        public ComboPreferences getComboPreferences() {
            return mCamera.getPreferences();
        }

        @Override
        public void switchCameraDevice() {
            mCameraDeviceCtrl.doSwitchCameraDevice();
        }

        @Override
        public CameraModeType getNextMode() {
            return mCameraActor.getCameraModeType(mNextMode);
        }

        @Override
        public CameraModeType getPrevMode() {
            return mCameraActor.getCameraModeType(mPrevMode);
        }

        @Override
        public boolean setFaceBeautyEnalbe(boolean isEnable) {
            mFrameManager.enableFaceBeauty(isEnable);
            return isEnable;
        }

        @Override
        public boolean initializeFrameView(boolean isEnableObject) {
            mFrameManager.initializeFrameView(isEnableObject);
            return false;
        }

        @Override
        public boolean clearFrameView() {
            getFrameView().clear();
            return true;
        }

        @Override
        public void setFaces(Face[] faces) {
            getFrameView().setFaces(faces);
        }

        @Override
        public Surface getPreviewSurface() {
            return mCameraDeviceCtrl.getSurfaceView().getHolder().getSurface();
        }

        @Override
        public boolean isFirstStartUp() {
            return mCameraDeviceCtrl.isFirstStartUp();
        }
    }
    protected void systemUIMode() {
		if(NavigationBarUtils.checkDeviceHasNavigationBar(this)){
			int flag = View.SYSTEM_UI_FLAG_FULLSCREEN
	                | View.SYSTEM_UI_FLAG_IMMERSIVE | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
	    	Window window = getWindow();
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS                                                                                          
                    | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION); 
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
	    	window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
	    	window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
	        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
	    	window.setStatusBarColor(Color.TRANSPARENT);
	        window.setNavigationBarColor(Color.TRANSPARENT);
	    	window.getDecorView().setSystemUiVisibility(flag);

		}
    }
    private void setScreenBrightness(int paramInt){
		Window localWindow = getWindow();
		WindowManager.LayoutParams localLayoutParams = localWindow.getAttributes();
		float f = paramInt / 255.0F;
		localLayoutParams.screenBrightness = f;
		localWindow.setAttributes(localLayoutParams);
	}
    private int getScreenBrightness(){
		int screenBrightness=255;
		try{
			screenBrightness = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
		}catch (Exception localException){

		}
		return screenBrightness;
	}
   
    public void applyParametersToServer(){
    	mCameraDeviceCtrl.applyParametersToServer();
    }
    
    public void setPreviewCallback(){
    	mCameraDeviceCtrl.setPreviewCallback();
    }
    
    public void setOneShotPreviewCallback(){
    	mCameraDeviceCtrl.setOneShotPreviewCallback();
    }
	@Override
	public boolean dispatchTouchEvent(MotionEvent event) {
		if(mHandler != null) {
			synchronized (mHandler) {
				switch (event.getAction()) {
				case MotionEvent.ACTION_UP:
					mHandler.postDelayed(mRunnable, EXIT_APP_TIMER);
					Log.d(TAG, "MotionEvent.ACTION_UP");
					break;
				case MotionEvent.ACTION_DOWN:
					mHandler.removeCallbacks(mRunnable);
					Log.d(TAG, "MotionEvent.ACTION_DOWN");
					break;
				default:
					break;
				}
			}
		}
		
		return super.dispatchTouchEvent(event);
	}

	@Override
	public boolean dispatchKeyEvent(KeyEvent event) {
        //PRIZE-add-disable KeyEvent.KEYCODE_ENTER event - 20170314-pengcancan-start
        if (event.getKeyCode() == KeyEvent.KEYCODE_ENTER){
            return true;
        }
        //PRIZE-add-disable KeyEvent.KEYCODE_ENTER event - 20170314-pengcancan-end
		synchronized (mHandler) {
			switch (event.getAction()) {
			case KeyEvent.ACTION_DOWN:
				mHandler.removeCallbacks(mRunnable);
				Log.d(TAG, "KeyEvent.ACTION_DOWN");
				break;
			case KeyEvent.ACTION_UP:
				mHandler.postDelayed(mRunnable, EXIT_APP_TIMER);
				Log.d(TAG, "KeyEvent.ACTION_UP");
				break;
			default:
				break;
			}
		}
		return super.dispatchKeyEvent(event);
	}

	Runnable mRunnable = new Runnable() {

		@Override
		public void run() {
			if (getCurrentMode() != ModePicker.MODE_VIDEO&&isFullScreen()) {
				CameraActivity.this.finish();
			}
			
		}
	};
	
	public boolean isRestoring(){
		return mIsFromRestore;
	}
	
	public boolean isLayoutRtl(){
        if (mCameraAppUi.getShutterManager().getPhotoShutter() == null)
            return false;
		return mCameraAppUi.getShutterManager().getPhotoShutter().isLayoutRtl();
	}
	
	public void removePreviewCallback(PreviewCallbackListen listen){
    	mCameraDeviceCtrl.removePreviewCallbackListen(listen);
    }
	
	public void detachSurfaceViewLayout(){
		mCameraDeviceCtrl.detachSurfaceViewLayout();
	}
	
	public void reStartOpenCamera(){
		mCameraDeviceCtrl.switchCamera(getCameraId());
	}
	
	public boolean isRotationLocked(Context mContext) {
        return RotationPolicy.isRotationLocked(mContext);
    }
	
	public boolean isCameraOpening() {
        return mCameraDeviceCtrl.isCameraOpened() ? false : true;
    }
	
	/*prize-xuchunming-20170215-bugid:28339-start*/
	public void saveOverrideValue(){	
		if(mISettingCtrl.getListPreference(SettingConstants.KEY_HDR) != null && mISettingCtrl.getListPreference(SettingConstants.KEY_HDR).isEnabled()== true && mISettingCtrl.getListPreference(SettingConstants.KEY_HDR).getOverrideValue() != null){
			mISettingCtrl.getListPreference(SettingConstants.KEY_HDR).setValue(mISettingCtrl.getListPreference(SettingConstants.KEY_HDR).getOverrideValue());
		}
		
	}
	/*prize-xuchunming-20170215-bugid:28339-end*/
	
	@Override
	public void finish() {
		// TODO Auto-generated method stub
		Log.d(TAG, "camera-debug-activityfinish", new Throwable());
		super.finish();
	}
	
	/* prize-add-set value of arc_front_superzoom -xiaoping-20180313-start */
	public boolean isPicselfieOpen() {
	    if (getParameters() != null && getParameters().get(SettingConstants.KEY_ARC_PICSELFIE_ENABLE) != null) {
            return getParameters().get(SettingConstants.KEY_ARC_PICSELFIE_ENABLE).equals("1") ? true : false;
        } else {
            return false;
        }
	}
	/* prize-add-set value of arc_front_superzoom -xiaoping-20180313-end */
	
	/*prize-xuchunming-20180411-bugid:50825-start*/
	public boolean isPostviewCapture() {
		if(isNonePickIntent() == true && mCameraId == 0  && mModePicker != null && mModePicker.getCurrentMode() == ModePicker.MODE_PHOTO) {
			if(mISettingCtrl != null && mISettingCtrl.getSettingValue(SettingConstants.KEY_HDR) != null && mISettingCtrl.getSettingValue(SettingConstants.KEY_HDR).equals("on")) {
				Log.d(TAG, "isPostviewCapture = false");
				return false;
			}
			Log.d(TAG, "isPostviewCapture = true");
			return true;
		}
		Log.d(TAG, "isPostviewCapture = false");
		return false;
	}
	
	public void gotoHome() {
		Intent mHomeIntent =  new Intent(Intent.ACTION_MAIN, null);
        mHomeIntent.addCategory(Intent.CATEGORY_HOME);
        mHomeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        mHomeIntent.putExtra(WindowManagerPolicy.EXTRA_FROM_HOME_KEY, true);
        startActivity(mHomeIntent);
    }
	/*prize-xuchunming-20180411-bugid:50825-end*/

	/*prize-xuchunming-201804026-add spotlight-start*/  
	public void onSpotLightVisibleChange(boolean isVisible) {
		if(mCameraDeviceCtrl != null) {
			mCameraDeviceCtrl.onSpotLightVisibleChange(isVisible);
		}
	}
	/*prize-xuchunming-201804026-add spotlight-end*/  
	
	/*prize-xuchunming-20180503-bugid:56870-start*/
	public interface OnLowPowerListener {
        void onLowPower(boolean isLowPower);
    }
	
	private List<OnLowPowerListener> mLowPowerListeners = new CopyOnWriteArrayList<OnLowPowerListener>();
	
	public boolean addOnLowPowerListeners(OnLowPowerListener l) {
        if (!mLowPowerListeners.contains(l)) {
            return mLowPowerListeners.add(l);
        }
        return false;
    }
	
	private void notifyLowPower(boolean isLowPower) {
		Log.d(TAG, "notifyLowPower:"+isLowPower);
        for (OnLowPowerListener listener : mLowPowerListeners) {
            if (listener != null) {
                listener.onLowPower(isLowPower);
            }
        }
	}
	
	public void showLowPowerInfo(){
		if(mMainHandler != null) {
			mMainHandler.post(toastRunnable);
		}
	}
	/*prize-xuchunming-20180503-bugid:56870-end*/
	
	/*prize-xuchunming-20180508-bugid:54875-start*/
	public interface OnContinueShotListener {
        void onContinueShot(boolean isShoting);
    }
	
	private List<OnContinueShotListener> mOnContinueShotListeners = new CopyOnWriteArrayList<OnContinueShotListener>();
	
	public boolean addOnContinueShotListeners(OnContinueShotListener l) {
        if (!mOnContinueShotListeners.contains(l)) {
            return mOnContinueShotListeners.add(l);
        }
        return false;
    }
	
	public void notifyContinueShot(boolean isShoting) {
        for (OnContinueShotListener listener : mOnContinueShotListeners) {
            if (listener != null) {
                listener.onContinueShot(isShoting);
            }
        }
	}
	/*prize-xuchunming-20180508-bugid:54875-end*/
	
	/*prize-xuchunming-20180523-add shortcuts-start*/
	public boolean setShortCut(String action){
		if(action.equals("com.mediatek.camera.shortcut.portrait")){
			mPickType = PICK_TYPE_SHORTCUT_PORTRAIT;
			return true;
		}else if(action.equals("com.mediatek.camera.shortcut.supperzoom")){
			mPickType = PICK_TYPE_SHORTCUT_SUPERZOOM;
			return true;
		}else if(action.equals("com.mediatek.camera.shortcut.video")){
			mPickType = PICK_TYPE_SHORTCUT_VIDEO;
			return true;
		}else if(action.equals("com.mediatek.camera.shortcut.facebeauty")){
			getIntent().putExtra(com.android.camera.Util.EXTRAS_CAMERA_FACING, 1);
			mPickType = PICK_TYPE_SHORTCUT_FACEBEAUTY;
			return true;
		}
		return false;
	}
	
	public boolean isShortCut(){
		switch(mPickType){
			case PICK_TYPE_SHORTCUT_PORTRAIT:
			return true;
			case PICK_TYPE_SHORTCUT_SUPERZOOM:
			return true;
			case PICK_TYPE_SHORTCUT_VIDEO:
			return true;
			case PICK_TYPE_SHORTCUT_FACEBEAUTY:
			return true;
			default:
			return false;
		}
	}
	public void changeModeForShortCut(){
		switch(mPickType){
			case PICK_TYPE_SHORTCUT_PORTRAIT:
			mCameraActor = new PhotoActor(this, mModuleManager,ModePicker.MODE_PORTRAIT);
			break;
			case PICK_TYPE_SHORTCUT_SUPERZOOM:
			mCameraActor = new PhotoActor(this, mModuleManager,ModePicker.MODE_PICTURE_ZOOM);
			break;
			case PICK_TYPE_SHORTCUT_VIDEO:
			mCameraActor = new PhotoActor(this, mModuleManager,ModePicker.MODE_PREVIDEO);
			break;
			case PICK_TYPE_SHORTCUT_FACEBEAUTY:
			mCameraActor = new PhotoActor(this, mModuleManager,ModePicker.MODE_FACE_BEAUTY);
			break;
			default:
			mCameraActor = new PhotoActor(this, mModuleManager,ModePicker.MODE_PHOTO);
		}
	}
	/*prize-xuchunming-20180523-add shortcuts-end*/
}
