/*
 * Copyright (C) 2014 MediaTek Inc.
 * Modification based on code covered by the mentioned copyright
 * and/or permission notice(s).
 */
/*
 * Copyright (C) 2009 The Android Open Source Project
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

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.DialogInterface.OnShowListener;
import android.content.res.Resources;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.Metadata;
import android.media.audiofx.Virtualizer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.animation.Animation;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import android.widget.ImageView;

import com.android.gallery3d.R;
import com.android.gallery3d.common.ApiHelper;
import com.android.gallery3d.common.BlobCache;
import com.android.gallery3d.util.CacheManager;
import com.android.gallery3d.util.GalleryUtils;
import com.mediatek.gallery3d.video.DefaultMovieItem;
import com.mediatek.gallery3d.ext.IRewindAndForwardExtension;
import com.mediatek.gallery3d.ext.IServerTimeoutExtension;
import com.mediatek.gallery3d.video.Bookmarker;
import com.mediatek.gallery3d.video.Bookmarker.BookmarkerInfo;
import com.mediatek.gallery3d.video.ErrorDialogFragment;
import com.mediatek.gallery3d.video.ExtensionHelper;
import com.mediatek.gallery3d.video.IContrllerOverlayExt;
import com.mediatek.gallery3d.video.IMovieDrmExtension;
import com.mediatek.gallery3d.video.IMovieDrmExtension.IMovieDrmCallback;
import com.mediatek.gallery3d.video.DefaultMovieListLoader;
import com.mediatek.gallery3d.video.IMovieList;
import com.mediatek.gallery3d.video.IMovieListLoader;
import com.mediatek.gallery3d.video.IMovieListLoader.LoaderListener;
import com.mediatek.gallery3d.video.IMoviePlayer;
import com.mediatek.gallery3d.video.IMovieItem;
import com.mediatek.gallery3d.video.MediaPlayerWrapper;
import com.mediatek.gallery3d.video.MovieUtils;
import com.mediatek.gallery3d.video.MovieView;
import com.mediatek.gallery3d.video.MtkVideoFeature;
import com.mediatek.gallery3d.video.PowerSavingManager;
import com.mediatek.gallery3d.video.RemoteConnection;
import com.mediatek.gallery3d.video.SlowMotionItem;
import com.mediatek.gallery3d.video.VideoGestureController;
import com.prize.gallery3d.video.VideoZoomController;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class MoviePlayer implements MediaPlayerWrapper.Listener,
        ControllerOverlay.Listener, LoaderListener,
        AudioManager.OnAudioFocusChangeListener,
        MediaPlayerWrapper.MultiWindowListener {
    @SuppressWarnings("unused")
    private static final String TAG = "Gallery2/VideoPlayer/MoviePlayer";
    private static final String TEST_CASE_TAG = "Gallery2PerformanceTestCase2";

    private static final String KEY_VIDEO_POSITION = "video-position";
    private static final String KEY_RESUMEABLE_TIME = "resumeable-timeout";

    // / M: for more detail in been killed case @{
    private static final String KEY_CONSUMED_DRM_RIGHT = "consumed_drm_right";
    private static final String KEY_VIDEO_CAN_SEEK = "video_can_seek";
    private static final String KEY_VIDEO_CAN_PAUSE = "video_can_pause";
    private static final String KEY_VIDEO_LAST_DURATION = "video_last_duration";
    private static final String KEY_VIDEO_LAST_DISCONNECT_TIME = "last_disconnect_time";
    private static final String KEY_VIDEO_STATE = "video_state";
    private static final String KEY_VIDEO_STREAMING_TYPE = "video_streaming_type";
    private static final String KEY_VIDEO_CURRENT_URI = "video_current_uri";
    // / @}

    private static final int RETURN_ERROR = -1;
    private static final int NONE_TRACK_INFO = -1;
    private static final int TYPE_TRACK_INFO_BOTH = -1;
    private static final int ERROR_CANNOT_CONNECT = -1003;
    private static final int ERROR_FORBIDDEN = -1100;
    private static final int ERROR_INVALID_OPERATION = -38;
    private static final int ERROR_ALREADY_EXISTS = -17;

    // M: Error code that the mediaplayer notified {@
    private static int MEDIA_ERROR_BASE = -1000;
    private static int ERROR_BUFFER_DEQUEUE_FAIL = MEDIA_ERROR_BASE - 100 - 6;
    /**
     * Bad file, decoder cannot decode this file.
     */
    private static final int MEDIA_ERROR_BAD_FILE = 260;
    /**
     * Can not connect to server for network failure.
     */
    private static final int MEDIA_ERROR_CANNOT_CONNECT_TO_SERVER = 261;
    /**
     * Current media type does not supported.
     */
    private static final int MEDIA_ERROR_TYPE_NOT_SUPPORTED = 262;
    /**
     * DRM files not supported.
     */
    private static final int MEDIA_ERROR_DRM_NOT_SUPPORTED = 263;
    /**
     * Invalid link for phone to connect. (403 Forbidden)
     */
    private static final int MEDIA_ERROR_INVALID_CONNECTION = 264;
    // @}

    /**
     * Status constant indicating asynchronous pause or play succeed.(Add for
     * RTSP play/pause asynchronous processing feature)
     */
    private static final int PAUSE_PLAY_SUCCEED = 0;

    /**
     * Define timeout unit as 500ms, it's used for some delay action
     */
    private static final long TIMEOUT_UNIT = 500;

    /**
     * try to connecting the server 1500ms later
     */
    private static final long RETRY_TIME = 1500;
    /**
     * If we resume the acitivty with in RESUMEABLE_TIMEOUT, we will keep
     * playing.  Otherwise, we pause the player.
     */
    private static final long RESUMEABLE_TIMEOUT = 3 * 60 * 1000; // 3 mins

    private Activity mActivityContext;  // for dialog and toast context

    /**
     * add for streaming, it will be set to mediaplayer if streaming video
     */
    private String mCookie = null;
    private Context mContext;
    private View mRootView;
    private MovieView mMovieView;
    /// M: To avoid surfaceview flicker issue, add a black place holder above
    //  the surfaceview, and hide it if video rendering started {@
    private View mPlaceHolder;
    // @}
    private Bookmarker mBookmarker;
    private Handler mHandler;
    private AudioBecomingNoisyReceiver mAudioBecomingNoisyReceiver;
    private MovieControllerOverlay mController;
    private VideoZoomController mZoomController;

    private long mResumeableTime = Long.MAX_VALUE;
    private int mVideoPosition = 0;
    private boolean mHasPaused = false;
    private int mLastSystemUiVis = 0;

    private int mVideoLastDuration; // for duration displayed in init state
    private boolean mFirstBePlayed = false; // for toast more info if first playing
    private boolean mVideoCanPause = false;
    private boolean mVideoCanSeek = false;
    private boolean mCanReplay;
    private boolean mError = false;
    // If the time bar is being dragged.
    private boolean mDragging;
    // If the time bar is visible.
    private boolean mShowing;
    // M: the type of the video
    private int mVideoType = MovieUtils.VIDEO_TYPE_LOCAL;

    private TState mTState = TState.PLAYING;
    ///M: for expression audio focus whether granted
    private int mAudiofocusState = AudioManager.AUDIOFOCUS_REQUEST_FAILED;
    private IMovieItem mMovieItem;
    private RetryExtension mRetryExt = new RetryExtension();
    private IServerTimeoutExtension mServerTimeoutExt;
    private IRewindAndForwardExtension mControllerRewindAndForwardExt;
    public MoviePlayerExtension mPlayerExt = new MoviePlayerExtension();
    private IContrllerOverlayExt mOverlayExt;
    private static final String VIRTUALIZE_EXTRA = "virtualize";
    private Virtualizer mVirtualizer;
    // /M:Whether streaming video is buffering or not
    private boolean mIsBuffering = false;
    // /M: the position which seek move to
    private int mSeekMovePosition;
    // /M: Whether is support preroll
    private static final boolean MTK_PREROLL_SUPPORT = false;
    // /M:The media details dialog is shown or not.To avoid MediaPlayerService
    // may pause fail when play RTSP,should pause video in the time buffering
    // end.
    private boolean mIsDialogShow = false;

    private MediaPlayerWrapper mMediaPlayerWrapper;
//    private VideoGestureController mVideoGestureController;

    private boolean mIsOnlyAudio = false;

    // / M: add for change video to previous/next this is make movielist and use
    // in movieplayer when media key pressed @{
    private IMovieListLoader mMovieLoader;
    public IMovieList mMovieList;

    // / @}

    /// PowerSaving @{
    private PowerSavingManager mPowerSavingManager = null;
    private int mPowerSavingPosition;
    private enum PowerSavingEvent {
        EVENT_NEED_RESTORE, EVENT_NONE
    }
    PowerSavingEvent mPowerSavingEvent = PowerSavingEvent.EVENT_NONE;
    /// @}

    // for more killed case, same as videoview's state and controller's state.
    // will use it to sync player's state.
    // here doesn't use videoview's state and controller's state for that
    // videoview's state doesn't have reconnecting state and controller's state
    // has temporary state.
    private enum TState {
        PLAYING, PAUSED, STOPED, COMPELTED, RETRY_ERROR
    }

    // /M:@ { add for dismiss error dialog when activity onResume
    private final String TAG_ERROR_DIALOG = "ERROR_DIALOG_TAG";
    private FragmentManager mFragmentManager;

    public void dismissAllowingStateLoss() {
        if (mFragmentManager == null) {
            mFragmentManager = mActivityContext.getFragmentManager();
        }
        DialogFragment oldFragment = (DialogFragment) mFragmentManager
                .findFragmentByTag(TAG_ERROR_DIALOG);
        if (null != oldFragment) {
            oldFragment.dismissAllowingStateLoss();
        }
    }

    // / @}

    private final Runnable mPlayingChecker = new Runnable() {
        @Override
        public void run() {
            boolean isplaying = mMediaPlayerWrapper.isPlaying();
            // /M:Only when start command has performed as well as video is not
            // buffering should the playing information can be shown@{
            if (isplaying && !mIsBuffering) {
                // live streaming can't pause ,
                // but showPlaying() will set right string for live streaming
                if (mIsDialogShow && !MovieUtils.isLiveStreaming(mVideoType)) {
                    Log.v(TAG, "mPlayingChecker.run() pauseIfNeed");
                    mPlayerExt.pauseIfNeed();
                } else {
                    Log.v(TAG, "mPlayingChecker.run() showPlaying");
                    mController.showPlaying();
                }
            } else {
                mHandler.postDelayed(mPlayingChecker, 250);
            }
        }
    };

    private final Runnable mProgressChecker = new Runnable() {
        @Override
        public void run() {
            int pos = setProgress();
            mHandler.postDelayed(mProgressChecker, 1000 - (pos % 1000));
        }
    };

    public MoviePlayer(View rootView, final MovieActivity movieActivity,
            IMovieItem movieItem, Bundle savedInstance, boolean canReplay,
            String cookie) {
        Log.v(TAG, "new MoviePlayer, rootView = " + rootView
                + ", movieActivity = " + movieActivity
                + ", movieItem = " + movieItem
                + ", savedInstance = " + savedInstance
                + ", canReplay = " + canReplay
                + ", cookie = " + cookie);
        initialize(rootView, movieActivity, movieItem, canReplay, cookie);
        if (savedInstance != null) { // this is a resumed activity
            mVideoPosition = savedInstance.getInt(KEY_VIDEO_POSITION, 0);
            mResumeableTime = savedInstance.getLong(KEY_RESUMEABLE_TIME,
                    Long.MAX_VALUE);
            mHasPaused = true;
            onRestoreInstanceState(savedInstance);
        } else {
            mTState = TState.PLAYING;
            mFirstBePlayed = true;
            final BookmarkerInfo bookmark = mBookmarker.getBookmark(movieItem
                    .getUri());
            if (bookmark != null && (bookmark.mBookmark / 1000 != bookmark.mDuration / 1000 && bookmark.mBookmark / 1000 > 0 && bookmark.mDuration - bookmark.mBookmark >= 1000)) {
//              showResumeDialog(movieActivity, bookmark);
            	seekPlay(bookmark);
            } else {
                // if is a new activity, directly start video when instantiate
                // MoviePlayer,
                // if not, start video when invoke onResume
                doStartVideoCareDrm(false, 0, 0);
                if (mMovieItem.isSlowMotion()) {
                    mController.show();
                }
            }
        }
    }

    private void initialize(View rootView, final MovieActivity movieActivity,
            IMovieItem movieItem, boolean canReplay, String cookie) {
        mRootView = rootView;
        mMovieView = (MovieView) mRootView.findViewById(R.id.movie_view);
        mPlaceHolder = mRootView.findViewById(R.id.place_holder);
        mActivityContext = movieActivity;
        mContext = movieActivity.getApplicationContext();
        mMovieItem = movieItem;
        mCanReplay = canReplay;
        mCookie = cookie;
        mMediaPlayerWrapper = new MediaPlayerWrapper(mContext, mMovieView);
        mMediaPlayerWrapper.setListener(this);
        mMediaPlayerWrapper.setMultiWindowListener(this);
        mHandler = new Handler();
        mBookmarker = new Bookmarker(movieActivity);
        // add movie controller
        mController = new MovieControllerOverlay(movieActivity, mMediaPlayerWrapper, mMovieItem);
        ((ViewGroup) rootView).addView(mController.getView());
        mController.setListener(this);
        mController.setCanReplay(canReplay);
        mZoomController = new VideoZoomController(movieActivity, mMovieView, mController);
        mServerTimeoutExt = ExtensionHelper.getServerTimeoutExtension(mContext);
        mControllerRewindAndForwardExt = mController.getRewindAndForwardExtension();
        // TODO set MoviePlayerExtension for MovieHooker
        movieActivity.setMovieHookerParameter(null, mPlayerExt);
        mOverlayExt = mController.getOverlayExt();
        mVideoType = MovieUtils.judgeStreamingType(movieItem.getUri(),
                movieItem.getMimeType());

        // set audio effect {@
        Intent ai = movieActivity.getIntent();
        boolean virtualize = ai.getBooleanExtra(VIRTUALIZE_EXTRA, false);
        if (virtualize) {
            int session = mMediaPlayerWrapper.getAudioSessionId();
            if (session != 0) {
                mVirtualizer = new Virtualizer(0, session);
                mVirtualizer.setEnabled(true);
            } else {
                Log.w(TAG, "no audio session to virtualize");
            }
        }
        // @}

        setOnSystemUiVisibilityChangeListener();
        // Hide system UI by default
        showSystemUi(false);

        mAudioBecomingNoisyReceiver = new AudioBecomingNoisyReceiver();
        mAudioBecomingNoisyReceiver.register();

        enablePowerSavingIfNeed();
//        mVideoGestureController = new VideoGestureController(mContext,
//                mRootView, mController);

        initMovieList();
    }

    // / M: [FEATURE.ADD] add for make movielist will be used in moviePlayer
    // to change previous/next when media key pressed {@
    private void initMovieList() {
        mMovieLoader = new DefaultMovieListLoader();
        mMovieLoader.fillVideoList(mContext, mActivityContext.getIntent(), this, mMovieItem);
    }

    // IMovieListLoader.LoaderListener {@
    @Override
    public void onListLoaded(IMovieList movieList) {
        mMovieList = movieList;
        Log.v(TAG, "onListLoaded() "
                + (mMovieList != null ? mMovieList.size() : "null"));
    }
    
    /*PRIZE-lock funtion bug:452-wanzhijuan-2015-5-14-start*/
    /**
     * 
     * Method description: return key processing
     * @param Parameter name Description
     * @returnThe return type Boolean indicates that the true function is locked, and the false is not locked.
     * @see Class / class / class complete complete # method
     */
    public boolean onBack() {
    	return mController.onBack();
    }
    
    /**
     * 
     * Method description: menu key processing
     * @param Parameter name Description
     * @return return type Boolean indicates that the true function is locked, and the false is not locked.
     * @see Class / class / class complete complete # method
     */
    public boolean onMenu() {
    	return mController.onMenu();
    }
    /*PRIZE-lock funtion bug:452-wanzhijuan-2015-5-14-end*/

    // Below are key events passed from MovieActivity.
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        Log.v(TAG, "onKeyDown keyCode = " + keyCode);
        /*PRIZE-lock funtion bug:452-wanzhijuan-2015-5-14-start*/
        if (keyCode == KeyEvent.KEYCODE_MENU) {
        	if (onMenu()) {
        		Log.v(TAG, "onKeyDown KEYCODE_MENU = true");
        		return true;
        	}
        }
        /*PRIZE-lock funtion bug:452-wanzhijuan-2015-5-14-end*/
        // Some headsets will fire off 7-10 events on a single click
        if (event.getRepeatCount() > 0) {
            return isMediaKey(keyCode);
        }

        if (!mController.isTimeBarEnabled()) {
            Log.w(TAG, "onKeyDown, can not play or pause");
            return isMediaKey(keyCode);
        }

        switch (keyCode) {
            case KeyEvent.KEYCODE_HEADSETHOOK:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                if (mMediaPlayerWrapper.isPlaying() && mMediaPlayerWrapper.canPause()) {
                    pauseVideo();
                } else {
                    playVideo();
                }
                return true;
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                if (mMediaPlayerWrapper.isPlaying() && mMediaPlayerWrapper.canPause()) {
                    pauseVideo();
                }
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY:
                if (!mMediaPlayerWrapper.isPlaying()) {
                    playVideo();
                }
                return true;
                /*PRIZE-Support suspension function-wanzhijuan-2015-4-13-start*/
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return playPre();
            case KeyEvent.KEYCODE_MEDIA_NEXT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return playNext();
                /*PRIZE-Support suspension function-wanzhijuan-2015-4-13-end*/
        }
        return false;
    }
    
    /*PRIZE-Support suspension function-wanzhijuan-2015-4-13-start*/
    // To switch the headset, the upper and lower video switching of the floating window of the common code to extract
    /**
     * 
     * Method description: play before a video
     * @param Parameter name Description
     * @return return type description
     * @see class/complete class/complete class name#method
     */
    private boolean playPre() {
    	if (mMovieList != null) {
            mPlayerExt.startNextVideo(mMovieList.getPrevious(mMovieItem));
            return true;
        }
        
        return false;
    }
    
    /**
     * 
     *Method description: play next a video
     * @param Parameter name Description
     * @return return type description
     * @see class/complete class/complete class name#method

     */
    private boolean playNext() {
    	if (mMovieList != null) {
            mPlayerExt.startNextVideo(mMovieList.getNext(mMovieItem));
            return true;
        }
        return false;
    }
    /*PRIZE-Support suspension function-wanzhijuan-2015-4-13-end*/

    /*PRIZE-lock funtion bug:452-wanzhijuan-2015-5-14-start*/
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return isLockMenu(keyCode) || isMediaKey(keyCode);
    }
    
	/**
	 * Send broadcast to PrizeVideo to update play history.
	 * added by pengcancan - 20151121
	 * @param item
	 */

    private boolean isLockMenu(int keyCode) {
    	return (keyCode == KeyEvent.KEYCODE_MENU) && mController.isLock();
    }
    /*PRIZE-lock bug:452-wanzhijuan-2015-5-14-end*/

    private static boolean isMediaKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_HEADSETHOOK
                || keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS
                || keyCode == KeyEvent.KEYCODE_MEDIA_NEXT
                || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY
                || keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE;
    }
    // / @}

    // M: [FEATURE.ADD] add for control Wfd/Mhl power saving {@
    private void enablePowerSavingIfNeed() {
        if (mPowerSavingManager == null) {
            mPowerSavingManager = new PowerSavingManager(mActivityContext,mRootView,mEventListener);
        } else {
            mPowerSavingManager.refreshRemoteDisplay();
        }
    }

    private RemoteConnection.ConnectionEventListener mEventListener =
            new RemoteConnection.ConnectionEventListener() {
        @Override
        public void onEvent(int what) {
            Log.v(TAG, "onEvent() what= " + what);
            if (what == EVENT_CONTINUE_PLAY || what == EVENT_STAY_PAUSE) {
                mPowerSavingPosition = mMediaPlayerWrapper.getCurrentPosition();
                mPowerSavingEvent = PowerSavingEvent.EVENT_NEED_RESTORE;
            } else if (what == EVENT_FINISH_NOW) {
                // M: [Mutil-window] stop video when disconnect in extension mode
                // for can not finish normal
                if (((MovieActivity) mActivityContext).isMultiWindowMode()) {
                    mPlayerExt.stopVideo();
                } else {
                    mActivityContext.finish();
                }
            } else {
                mPowerSavingEvent = PowerSavingEvent.EVENT_NONE;
            }
        }
    };

    private void playbackControlforPowerSaving() {
        Log.v(TAG, "playbackControlforPowerSaving() mPowerSavingPosition= " + mPowerSavingPosition
             + " mVideoPosition= " + mVideoPosition + " mTState= " + mTState);
        if (mPowerSavingPosition == 0) {
            mPowerSavingPosition = mVideoPosition;
        }
        if (mTState == TState.PLAYING) {
            mMediaPlayerWrapper.seekTo(mPowerSavingPosition);
            playVideo();
        } else if (mTState == TState.PAUSED) {
            mMediaPlayerWrapper.seekTo(mPowerSavingPosition);
            pauseVideo();
        }
        mPowerSavingEvent = PowerSavingEvent.EVENT_NONE;
        mPowerSavingPosition = 0;
    }

    private void disablePowerSavingIfNeed() {
        mPowerSavingManager.release();
    }
    /// @}

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void setOnSystemUiVisibilityChangeListener() {
        if (!ApiHelper.HAS_VIEW_SYSTEM_UI_FLAG_HIDE_NAVIGATION)
            return;

        // When the user touches the screen or uses some hard key, the framework
        // will change system ui visibility from invisible to visible. We show
        // the media control and enable system UI (e.g. ActionBar) to be visible
        // at this point
        mRootView.setOnSystemUiVisibilityChangeListener(
                new View.OnSystemUiVisibilityChangeListener() {
                    @Override
                    public void onSystemUiVisibilityChange(int visibility) {
                        boolean finish = (mActivityContext == null ? true
                                : mActivityContext.isFinishing());
                        int diff = mLastSystemUiVis ^ visibility;
                        mLastSystemUiVis = visibility;
                        if ((diff & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) != 0
                                && (visibility & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0
                                || (diff & View.SYSTEM_UI_FLAG_LOW_PROFILE) != 0
                                && (visibility & View.SYSTEM_UI_FLAG_LOW_PROFILE) == 0) {
                            mController.show();
                            mRootView.setBackgroundColor(Color.BLACK);
                        }
                        Log.v(TAG, "onSystemUiVisibilityChange("
                                + visibility + ") finishing()=" + finish);
                    }
                });
    }
    

    @SuppressWarnings("deprecation")
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void showSystemUi(boolean visible) {
        // / M:isFirstOpen mark for first open
        Log.v(TAG, "showSystemUi() visible " + visible);
        if (!ApiHelper.HAS_VIEW_SYSTEM_UI_FLAG_LAYOUT_STABLE)
            return;
        int flag = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
              ///M: Add for video zoom, KK new method for full screen.
                | View.SYSTEM_UI_FLAG_IMMERSIVE
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        if (!visible) {
            // We used the deprecated "STATUS_BAR_HIDDEN" for unbundling
            flag |= View.STATUS_BAR_HIDDEN
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
            /// M: need manually close popup menu from android N {@
            mActivityContext.closeOptionsMenu();
            // @}
        }
        mRootView.setSystemUiVisibility(flag);
    }

    public void onSaveInstanceState(Bundle outState) {
        outState.putInt(KEY_VIDEO_POSITION, mVideoPosition);
        outState.putLong(KEY_RESUMEABLE_TIME, mResumeableTime);
        onSaveInstanceStateMore(outState);
    }

    private void showResumeDialog(Context context, final BookmarkerInfo bookmark) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.resume_playing_title);
        builder.setMessage(String.format(
                context.getString(R.string.resume_playing_message),
                GalleryUtils.formatDuration(context, bookmark.mBookmark / 1000)));
        builder.setOnCancelListener(new OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                onCompletion();
                mIsShowResumingDialog = false;
            }
        });
        builder.setPositiveButton(R.string.resume_playing_resume,
                new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    	seekPlay(bookmark);
                    }
                });
        builder.setNegativeButton(R.string.resume_playing_restart,
                new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        doStartVideoCareDrm(true, 0, bookmark.mDuration);
                        mIsShowResumingDialog = false;
                        mHandler.removeCallbacks(mProgressChecker);
                        mHandler.post(mProgressChecker);
                    }
                });
        builder.show();
        mIsShowResumingDialog = true;
    }
    
    /**
     * 
	 * method description: continue playing
	 * @param parameter name Description
	 * @return return type description
	 * / class / @see class name complete complete class # method name
     */
    private void seekPlay(final BookmarkerInfo bookmark) {
    	//here try to seek for bookmark
        //Note: if current video can not be sought, it will not has any bookmark.
        ///M: MTK_AUDIO_CHANGE_SUPPORT & MTK_SUBTITLE_SUPPORT
        ///@{
        mVideoCanSeek = true;
        doStartVideoCareDrm(true, bookmark.mBookmark,
                bookmark.mDuration);
        // add for wfd extension restore action
        mVideoPosition = bookmark.mBookmark;
        mIsShowResumingDialog = false;
        mHandler.removeCallbacks(mProgressChecker);
        mHandler.post(mProgressChecker);
    }

    public void onPause() {
        Log.v(TAG, "onPause()");
        if (!((MovieActivity) mActivityContext).isMultiWindowMode()) {
            doOnPause();
        } else {
            /// M: [Mutil-window] In multi window mode, onSaveInstance() will
            // executed before onStop() and after onPause(), need update below
            // parameter values at here {@
            int position = mMediaPlayerWrapper.getCurrentPosition();
            mVideoPosition = position >= 0 ? position : mVideoPosition;
            int duration = mMediaPlayerWrapper.getDuration();
            mVideoLastDuration = duration > 0 ? duration : mVideoLastDuration;
            mResumeableTime = System.currentTimeMillis() + RESUMEABLE_TIMEOUT;
            Log.i(TAG, "onPause(), update parameters, mVideoPosition = "
                    + mVideoPosition + ", mVideoLastDuration = " + mVideoLastDuration
                    + ", mResumeableTime = " + mResumeableTime);
            /// @}
        }
    }

    // we should stop video anyway after this function called.
    public void onStop() {
        Log.v(TAG, "onStop()");
        doOnPause();
    }

    // For some purpose, such as multi window, need stop video when Activity#onStop() invoked
    private void doOnPause() {
        Log.v(TAG, "doOnPause() mHasPaused= " + mHasPaused);
        if (mHasPaused) {
            return;
        }
        mHasPaused = true;
        ///M: unregister audio focus changes listener
        abandonAudiofocus();
        mHandler.removeCallbacksAndMessages(null);
        // /M:Cancel hiding controller when video stop play.
        mOverlayExt.onCancelHiding();
        // /M: set background black here for avoid screen maybe flash when exit
        // MovieActivity
        mHandler.removeCallbacks(mRemoveBackground);
        mRootView.setBackgroundColor(Color.BLACK);
        /// M: [Multi-Window] In multi window mode, onStop() maybe executed after
        //  surface destroyed. we can't get values from media player at here, and
        //  need move to onPause() {@
        if (!((MovieActivity)mActivityContext).isMultiWindowMode()) {
            int position = mMediaPlayerWrapper.getCurrentPosition();
            mVideoPosition = position >= 0 ? position : mVideoPosition;
            int duration = mMediaPlayerWrapper.getDuration();
            mVideoLastDuration = duration > 0 ? duration : mVideoLastDuration;
        }
        /// @}
        mBookmarker.setBookmark(mMovieItem.getUri(), mVideoPosition,
                mVideoLastDuration);

        mMediaPlayerWrapper.stop(); // change suspend to release for sync paused and
                               // killed case
        mIsBuffering = false;
        mResumeableTime = System.currentTimeMillis() + RESUMEABLE_TIMEOUT;
        mMediaPlayerWrapper.setResumed(false); // avoid open video after stop  activity
        // / if activity will be finished, will not set movie view invisible @{
        if (!mActivityContext.isFinishing()) {
            mMovieView.setVisibility(View.INVISIBLE); // Workaround for
                                                      // last-seek frame
                                                      // difference
            // / M: [BUG.ADD] @{
            // set controller background drawable to null to avoid screen flash
            // when
            // play an audio only video
            mOverlayExt.setBottomPanel(false, false);
            // / @}
        }
        // / @}

        mOverlayExt.clearBuffering(); // to end buffer state
        mServerTimeoutExt.recordDisconnectTime();
        disablePowerSavingIfNeed();

        Log.v(TAG, "onPause() mVideoPosition=" + mVideoPosition
                    + ", mResumeableTime=" + mResumeableTime
                    + ", mVideoLastDuration=" + mVideoLastDuration
                    + ", mIsShowResumingDialog=" + mIsShowResumingDialog);
    }

    public void onResume() {
        dump();
        /*PRIZE-funtion lock bug:452-wanzhijuan-2015-5-14-start*/
        // when press homekey ,Restore previous screen orientation
        mController.unLock();
        /*PRIZE-funtion lock bug:452-wanzhijuan-2015-5-14-start*/
        if (mHasPaused) {
            mDragging = false; // clear drag info
            // / M: [DEBUG.ADD] @{
            // Toast info of video or audio is supported should be shown when video
            // resume to play.
            mFirstBePlayed = true;
            // / @}

            // MovieView has been set invisible when pause, and should set
            // visible when resume, to avoid transparent surfaceview problem,
            // delay 500ms to wait first frame arrived
            mMovieView.removeCallbacks(mDelayVideoRunnable);
            mMovieView.postDelayed(mDelayVideoRunnable, TIMEOUT_UNIT);

            enablePowerSavingIfNeed();
            if (mServerTimeoutExt.handleOnResume() || mIsShowResumingDialog) {
                return;
            }

            switch (mTState) {
                case RETRY_ERROR:
                    mRetryExt.showRetry();
                    break;
                case STOPED:
                    mController.showEnded();
                    break;
                case COMPELTED:
                    mController.showEnded();
                    if (mVideoCanSeek || mMediaPlayerWrapper.canSeekForward()) {
                        mMediaPlayerWrapper.seekTo(mVideoPosition);
                    }
                    mMediaPlayerWrapper.setDuration(mVideoLastDuration);
                    break;
                case PAUSED:
                    // if video was paused, so it should be started, and then
                    // pause.
                    doStartVideo(true, mVideoPosition, mVideoLastDuration);
                    pauseVideo();
                    break;
                default:
                    if (mConsumedDrmRight) {
                        doStartVideo(true, mVideoPosition, mVideoLastDuration);
                    } else {
                        doStartVideoCareDrm(true, mVideoPosition,
                                mVideoLastDuration);
                    }
                    pauseVideoMoreThanThreeMinutes();
                    break;
            }
            mHasPaused = false;
        }
    }

    private void pauseVideoMoreThanThreeMinutes() {
        // If we have slept for too long, pause the play
        // If is live streaming, do not pause it too
        long now = System.currentTimeMillis();
        if (now > mResumeableTime && !MovieUtils.isLiveStreaming(mVideoType)
                && ExtensionHelper.shouldEnableCheckLongSleep(mActivityContext)) {
            if (mVideoCanPause || mMediaPlayerWrapper.canPause()) {
                Log.i(TAG, "pauseVideoMoreThanThreeMinutes() now=" + now);
                pauseVideo();
            }
        }
    }

    public void onDestroy() {
        Log.v(TAG, "onDestroy");
        if (mVirtualizer != null) {
            mVirtualizer.release();
            mVirtualizer = null;
        }
        mAudioBecomingNoisyReceiver.unregister();
        mServerTimeoutExt.clearTimeoutDialog();
    }

    // This updates the time bar display (if necessary). It is called every
    // second by mProgressChecker and also from places where the time bar needs
    // to be updated immediately.
    private int setProgress() {
        if (mDragging) {
            return 0;
        }

        int position = mMediaPlayerWrapper.getCurrentPosition();
        int duration = mMediaPlayerWrapper.getDuration();

        mController.setTimes(position, duration, 0, 0);
        if(mMovieItem.isSlowMotion()) {
            mController.setSlowMotionBarTimes(position, duration);
        }

        if (mControllerRewindAndForwardExt != null
                && mController.isPlayPauseEanbled()) {
            mControllerRewindAndForwardExt.updateView();
        }
        return position;
    }

    private void doStartVideo(final boolean enableFasten, final int position,
            final int duration) {
        Log.v(TAG, "doStartVideo(" + enableFasten + ", " + position + ", " + duration);
        requestAudioFocus();
        // /M:dismiss some error dialog and if error still it will show again
        dismissAllowingStateLoss();
        Uri uri = mMovieItem.getUri();
        String mimeType = mMovieItem.getMimeType();
        if (!MovieUtils.isLocalFile(uri, mimeType)) {
            Map<String, String> header = new HashMap<String, String>(2);
            mController.showLoading(false);
            mOverlayExt.setPlayingInfo(MovieUtils.isLiveStreaming(mVideoType));
            mHandler.removeCallbacks(mPlayingChecker);
            mHandler.postDelayed(mPlayingChecker, 250);
            Log.v(TAG, "doStartVideo() mCookie is " + mCookie);
            // / M: add play/pause asynchronous processing @{
            if (isRtsp()) {
                // /M: add for streaming cookie
                if (mCookie != null) {
                    header.put(MovieActivity.COOKIE, mCookie);
                }
                mMediaPlayerWrapper.setVideoURI(mMovieItem.getUri(), header);
                // / @}
            } else {
                if (mCookie != null) {
                    // /M: add for streaming cookie
                    header.put(MovieActivity.COOKIE, mCookie);
                    mMediaPlayerWrapper.setVideoURI(mMovieItem.getUri(), header);
                } else {
                    mMediaPlayerWrapper.setVideoURI(mMovieItem.getUri(), null);
                }
            }
        } else {
            mController.showPlaying();
			if(mimeType.startsWith("audio/")){
				Log.v(TAG, "11 doStartVideo() mimeType is " + mimeType);
            }else{
				mController.hide();
            }
            mMediaPlayerWrapper.setVideoURI(mMovieItem.getUri(), null);
        }

        mHandler.removeCallbacks(mRemoveBackground);
        mHandler.postDelayed(mRemoveBackground, 2 * TIMEOUT_UNIT);

        mMediaPlayerWrapper.start();

        if (position > 0 && (mVideoCanSeek || mMediaPlayerWrapper.canSeekForward())) {
            mMediaPlayerWrapper.seekTo(position);
        }
        if (enableFasten) {
            mMediaPlayerWrapper.setDuration(duration);
        }
        mHandler.removeCallbacks(mProgressChecker);
        mHandler.post(mProgressChecker);
    }

    // / M: for drm feature @{
    private boolean mConsumedDrmRight = false;
    private IMovieDrmExtension mDrmExt = ExtensionHelper
            .getMovieDrmExtension(mActivityContext);

    private void doStartVideoCareDrm(final boolean enableFasten,
            final int position, final int duration) {
        Log.v(TAG, "doStartVideoCareDrm(" + enableFasten + ", " + position
                    + ", " + duration + ")");
        mTState = TState.PLAYING;
        if (!mDrmExt.handleDrmFile(mActivityContext, mMovieItem,
                new IMovieDrmCallback() {
                    @Override
                    public void onContinue() {
                        doStartVideo(enableFasten, position, duration);
                        mConsumedDrmRight = true;
                    }

                    @Override
                    public void onStop() {
                        mPlayerExt.setLoop(false);
                        onCompletion(null);
                    }
                })) {
            doStartVideo(enableFasten, position, duration);
        }
    }

    // / @}

    private void playVideo() {
        Log.v(TAG, "playVideo()");
        if (!hasAudiofocus() && !requestAudioFocus()) {
            Toast.makeText(mContext.getApplicationContext(),
            mContext.getString(R.string.m_audiofocus_request_failed_message),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        // / M: resume mPauseBuffering to false for show buffering info to user.
        mPlayerExt.mPauseBuffering = false;
        mTState = TState.PLAYING;
        mMediaPlayerWrapper.start();
        mController.showPlaying();
        setProgress();
        mHandler.removeCallbacks(mProgressChecker);
        mHandler.post(mProgressChecker);
    }

    private void pauseVideo() {
        Log.v(TAG, "pauseVideo()");
        mTState = TState.PAUSED;
        mMediaPlayerWrapper.pause();
        mController.showPaused();
        setProgress();
        mHandler.removeCallbacks(mProgressChecker);
    }

    public void pauseWithoutStateChange() {
        Log.v(TAG, "pauseWithoutStateChange()");
        mMediaPlayerWrapper.pause();
    }

    public int getCurrentPosition() {
        return mMediaPlayerWrapper.getCurrentPosition();
    }

    public void onCompletion() {
    }

    public boolean isPlaying() {
        return mMediaPlayerWrapper.isPlaying();
    }

    // We want to pause when the headset is unplugged.
    private class AudioBecomingNoisyReceiver extends BroadcastReceiver {

        public void register() {
            mContext.registerReceiver(this, new IntentFilter(
                    AudioManager.ACTION_AUDIO_BECOMING_NOISY));
        }

        public void unregister() {
            mContext.unregisterReceiver(this);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.v(TAG, "AudioBecomingNoisyReceiver onReceive");
            if (!mController.isTimeBarEnabled()) {
                Log.w(TAG, "AudioBecomingNoisyReceiver, can not play or pause");
                return;
            }
            if (mMediaPlayerWrapper.isPlaying() && mMediaPlayerWrapper.canPause()) {
                pauseVideo();
            }
        }
    }

    public SurfaceView getVideoSurface() {
        return mMovieView;
    }

    public MediaPlayerWrapper getPlayerWrapper() {
        return mMediaPlayerWrapper;
    }

    private void onSaveInstanceStateMore(Bundle outState) {
        // for more details
        mServerTimeoutExt.onSaveInstanceState(outState);
        outState.putInt(KEY_VIDEO_LAST_DURATION, mVideoLastDuration);
        outState.putBoolean(KEY_VIDEO_CAN_PAUSE, mMediaPlayerWrapper.canPause());
        // / M: add this for deal with change language
        // / or other case which cause activity destory but not save right state
        // / @{
        if (mVideoCanSeek || mMediaPlayerWrapper.canSeekForward()) {
            outState.putBoolean(KEY_VIDEO_CAN_SEEK, true);
        } else {
            outState.putBoolean(KEY_VIDEO_CAN_SEEK, false);
        }
        // / @}
        outState.putBoolean(KEY_CONSUMED_DRM_RIGHT, mConsumedDrmRight);
        outState.putInt(KEY_VIDEO_STREAMING_TYPE, mVideoType);
        outState.putString(KEY_VIDEO_STATE, String.valueOf(mTState));
        outState.putString(KEY_VIDEO_CURRENT_URI, mMovieItem.getUri()
                .toString());
        mRetryExt.onSaveInstanceState(outState);
        mPlayerExt.onSaveInstanceState(outState);

        Log.v(TAG, "onSaveInstanceState(" + outState + ")");
    }

    private void onRestoreInstanceState(Bundle icicle) {
        mVideoLastDuration = icicle.getInt(KEY_VIDEO_LAST_DURATION);
        mVideoCanPause = icicle.getBoolean(KEY_VIDEO_CAN_PAUSE);
        mVideoCanSeek = icicle.getBoolean(KEY_VIDEO_CAN_SEEK);
        mConsumedDrmRight = icicle.getBoolean(KEY_CONSUMED_DRM_RIGHT);
        mVideoType = icicle.getInt(KEY_VIDEO_STREAMING_TYPE);
        mTState = TState.valueOf(icicle.getString(KEY_VIDEO_STATE));
        mMovieItem.setUri(Uri.parse(icicle.getString(KEY_VIDEO_CURRENT_URI)));
        mServerTimeoutExt.onRestoreInstanceState(icicle);
        mRetryExt.onRestoreInstanceState(icicle);
        mPlayerExt.onRestoreInstanceState(icicle);

        Log.v(TAG, "onRestoreInstanceState(" + icicle + ")");
    }

    // / @}

    private void clearVideoInfo() {
        mVideoPosition = 0;
        mVideoLastDuration = 0;
        mIsOnlyAudio = false;
        mConsumedDrmRight = false;
        mIsBuffering = false;
        if (mServerTimeoutExt != null) {
            mServerTimeoutExt.clearServerInfo();
        }

        if (mRetryExt != null) {
            mRetryExt.removeRetryRunnable();
        }
    }

    private void getVideoInfo(MediaPlayer mp) {
        Uri uri = mMovieItem.getUri();
        String mimeType = mMovieItem.getMimeType();
        if (!MovieUtils.isLocalFile(uri, mimeType)) {
            Metadata data = mp.getMetadata(MediaPlayer.METADATA_ALL,
                    MediaPlayer.BYPASS_METADATA_FILTER);
            if (data != null) {
                mServerTimeoutExt.setVideoInfo(data);
            } else {
                Log.w(TAG, "Metadata is null!");
            }
            int duration = mp.getDuration();
            // /M:For http streaming does not has live streaming,so do not set a
            // live streaming type to a http streaming whether its duration is
            // bigger or smaller than 0 or not @{
            if (duration <= 0 && !MovieUtils.isHttpStreaming(uri, mimeType)) {
                Log.v(TAG, "getVideoInfo(), correct type as live streaming");
                mVideoType = MovieUtils.VIDEO_TYPE_LIVE; // correct it
            }

            Log.v(TAG, "getVideoInfo() duration =" + duration
                        + ", video type = " + mVideoType);
        }
    }

    // / M: check video playing status.
    // sometimes onInfo() will notify waring message, such as network interrupt,
    // only has audio or video track. In these case, need prompt user and
    // continue play {@
    private void checkPlayStatus(int what, int extra) {
        if (mFirstBePlayed) {
            int messageId = 0;
            if (extra == ERROR_CANNOT_CONNECT
                    || extra == MediaPlayer.MEDIA_ERROR_UNSUPPORTED
                    || extra == ERROR_FORBIDDEN) {
                messageId = R.string.VideoView_info_text_network_interrupt;
            } else {
                if (what == MediaPlayer.MEDIA_INFO_VIDEO_NOT_SUPPORTED) {
                    messageId = R.string.VideoView_info_text_video_not_supported;
                } else if (what == MediaPlayer.MEDIA_INFO_AUDIO_NOT_SUPPORTED) {
                    messageId = R.string.audio_not_supported;
                }
            }
            if (messageId != 0) {
                String message = mActivityContext.getString(messageId);
                Toast.makeText(mActivityContext, message, Toast.LENGTH_SHORT)
                        .show();
                mFirstBePlayed = false;
                Log.v(TAG, "checkPlayStatus: " + message);
            }
        }
    }

    // / @}

    // /M:HLS_audio-only_02 The mediaplayer shall support metadata
    // embedded in the MPEG2-TS file @{
    private void handleMetadataUpdate(MediaPlayer mp, int what, int extra) {
        Log.v(TAG, "handleMetadataUpdate entry");
        Metadata data = mp.getMetadata(MediaPlayer.METADATA_ALL,
                MediaPlayer.BYPASS_METADATA_FILTER);
        Log.v(TAG, "handleMetadataUpdate data is " + data);
        if (data == null) {
            return;
        }
        String mimeType = new String();
        byte[] album = null;
        if (data.has(Metadata.MIME_TYPE)) {
            mimeType = data.getString(Metadata.MIME_TYPE);
            Log.v(TAG, "handleMetadataUpdate mimeType is " + mimeType);
        }
        if (data.has(Metadata.ALBUM_ART)) {
            album = data.getByteArray(Metadata.ALBUM_ART);
            if (album != null) {
                mOverlayExt.setLogoPic(album);
                Log.v(TAG, "handleMetadataUpdate album size is " + album.length);
            } else {
                mOverlayExt.setBottomPanel(true, true);
                Log.v(TAG, "handleMetadataUpdate album is null");
            }
        }
    }// / @}

    // /M:For http streaming, show loading while seek to a new position. {@
    private void handleBuffering(int what, int extra) {
        Log.v(TAG, "handleBuffering what is " + what + " mIsDialogShow is "
                + mIsDialogShow);
        if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) {
            mIsBuffering = true;
            if (MovieUtils.isHttpStreaming(mMovieItem.getUri(),
                    mMovieItem.getMimeType())
                    || MovieUtils.isHttpLiveStreaming(mMovieItem.getUri(),
                            mMovieItem.getMimeType())) {
                mController.showLoading(true);
            }
        } else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END) {
            // /M: The video should restore to its previous state after
            // buffering end.
            Log.v(TAG, "handleBuffering mTState is " + mTState);
            mIsBuffering = false;
            if (mIsDialogShow || !hasAudiofocus()) {
                mPlayerExt.pauseIfNeed();
            }
            if (MovieUtils.isHttpStreaming(mMovieItem.getUri(),
                    mMovieItem.getMimeType())
                    || MovieUtils.isHttpLiveStreaming(mMovieItem.getUri(),
                            mMovieItem.getMimeType())) {
                if (mTState == TState.PAUSED) {
                    mController.showPaused();
                } else {
                    mController.showPlaying();
                }
            }
        }
    } // / @}

    private void dump() {
        Log.v(TAG, "dump() mHasPaused = " + mHasPaused + ", mVideoPosition="
                + mVideoPosition + ", mResumeableTime=" + mResumeableTime
                + ", mVideoLastDuration=" + mVideoLastDuration
                + ", mDragging=" + mDragging + ", mConsumedDrmRight="
                + mConsumedDrmRight + ", mVideoCanSeek=" + mVideoCanSeek
                + ", mVideoCanPause=" + mVideoCanPause + ", mTState="
                + mTState + ", mIsShowResumingDialog="
                + mIsShowResumingDialog);
    }

    interface Restorable {
        void onRestoreInstanceState(Bundle icicle);

        void onSaveInstanceState(Bundle outState);
    }

    // set background as null to avoid lower power after start playing,
    // because GPU is always running if not set. can not set too early,
    // onShow() will set background as black
    private final Runnable mRemoveBackground = new Runnable() {
        @Override
        public void run() {
            Log.v(TAG, "mRemoveBackground.run()");
            mRootView.setBackground(null);
        }
    };

    // / M: same as launch case to delay transparent. @{
    private Runnable mDelayVideoRunnable = new Runnable() {
        @Override
        public void run() {
            Log.v(TAG, "mDelayVideoRunnable.run(), set MovieView visible");
            mMovieView.setVisibility(View.VISIBLE);
        }
    };
    // / @}

    // / M: when show resuming dialog, suspend->wake up, will play video. @{
    private boolean mIsShowResumingDialog;

    // / @}

    // / M: implement ControllerOverlay.Listener {@
    // void onPlayPause();
    // void onSeekStart();
    // void onSeekMove(int time);
    // void onSeekEnd(int time, int trimStartTime, int trimEndTime);
    // void onShown();
    // void onHidden();
    // void onReplay();
    // boolean powerSavingNeedShowController();

    @Override
    public void onPlayPause() {
        if (mMediaPlayerWrapper.isPlaying()) {
            if (mMediaPlayerWrapper.canPause()) {
                pauseVideo();
            }
        } else {
            playVideo();
        }
    }
    
    private void onPauseVideo() {
    	if (mMediaPlayerWrapper.canPause()) {
            pauseVideo();
        }
    }
    
    /*PRIZE-Support suspension function-wanzhijuan-2015-4-13-start*/
    @Override
    public void onPlayPre() {
        playPre();
    }
    
    @Override
    public void onPlayNext() {
        playNext();
    }
    
    /*PRIZE-Touch screen to adjust brightness, volume, progress-wanzhijuan-2015-3-30-start*/
    @Override
    public void onSlideStart() {
        
        onSeekStart();
    }

    @Override
    public void onSlideMove(int time) {
        
        onSeekMove(time);
    }

    @Override
    public void onSlideEnd(int time, int trimStartTime, int trimEndTime) {
        onSeekEnd(time, 0, 0);
    }
    
    /*PRIZE-Touch screen to adjust brightness, volume, progress-wanzhijuan-2015-3-30-end*/
    
    /*PRIZE-E9 bug:452 Video playback interface, the suspension window to add the phone status monitor-wanzhijuan-2015-5-6-start*/
    
    private int mOrientation;
	@Override
	public void onLockScreen(boolean isLock) {
		
		// TODO Auto-generated method stub
		if (isLock) {
			mActivityContext.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
			mOrientation = mActivityContext.getResources().getConfiguration().orientation;
		} else {
			mActivityContext.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
		}
	}
	/*PRIZE-E9 bug:452Video playback interface, the suspension window to add the phone status monitor-wanzhijuan-2015-5-6-end*/

    @Override
    public void onSeekStart() {
        Log.v(TAG, "onSeekStart() mDragging=" + mDragging);
        mSeekMovePosition = -1;
        mDragging = true;

        if (mMovieItem.isSlowMotion() && mTState == TState.PLAYING) {
            mMediaPlayerWrapper.pause();
        }
    }

    @Override
    public void onSeekMove(int time) {
        Log.v(TAG, "onSeekMove(" + time + ") mDragging=" + mDragging);
        // /M: If local video dosn't support preroll, do real-time seek,
        // otherwise only record seek position
        if (!MTK_PREROLL_SUPPORT
                && MovieUtils.isLocalFile(mMovieItem.getUri(),
                        mMovieItem.getMimeType())) {
            mMediaPlayerWrapper.seekTo(time);
            mSeekMovePosition = time;
            /*PRIZE-Touch screen to adjust brightness, volume, progress-wanzhijuan-2015-3-30-start*/
            mController.setDragTimes(time);
            /*PRIZE-Touch screen to adjust brightness, volume, progress-wanzhijuan-2015-3-30-end*/
            return;
        }
    }

    @Override
    public void onSeekEnd(int time, int start, int end) {
        Log.v(TAG, "onSeekEnd(" + time + ") mDragging=" + mDragging
                    + ", mSeekMovePosition=" + mSeekMovePosition);
        mDragging = false;
        // /M:No need to seek to the same position twice
        if (mSeekMovePosition != time) {
            mMediaPlayerWrapper.seekTo(time);
        }

        if (mMovieItem.isSlowMotion() && mTState == TState.PLAYING) {
            mMediaPlayerWrapper.start();
        }
    }

    @Override
    public void onShown() {
        Log.v(TAG, "onShown");
        mPowerSavingManager.endPowerSaving();
        mHandler.removeCallbacks(mRemoveBackground);
        mRootView.setBackgroundColor(Color.BLACK);
        mShowing = true;
        setProgress();
        showSystemUi(true);
    }

    @Override
    public void onHidden() {
        Log.v(TAG, "onHidden");
        /*--modify by liangchangwei fix bug 53962  2018-4-12---*/
		if(((MovieActivity) mActivityContext).isShowShareMenu() == false){
			mShowing = false;
			mPowerSavingManager.startPowerSaving();
			showSystemUi(false);

			// /M: set background to null avoid lower power,
			// because GPU is always running, if not set null.
			// delay 1000ms is to avoid ghost image when action bar do slide
			// animation,
			mHandler.removeCallbacks(mRemoveBackground);
			mHandler.postDelayed(mRemoveBackground, 3 * TIMEOUT_UNIT);
		}
        /*--modify by liangchangwei fix bug 53962  2018-4-12---*/
    }

    private boolean isRtsp() {
        return MovieUtils.isRTSP(mVideoType);
    }

    @Override
    public void onReplay() {
        Log.v(TAG, "onReplay()");
        mFirstBePlayed = true;
        if (mRetryExt.handleOnReplay()) {
            return;
        }
        doStartVideoCareDrm(false, 0, 0);
    }

    @Override
    public boolean powerSavingNeedShowController() {
        // if in power saving extension mode will not hide action bar @{
        return mPowerSavingManager.isInExtensionDisplay();
    }

    // / @}

    private boolean requestAudioFocus() {
        mAudiofocusState =
              ((AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE)).requestAudioFocus(
                this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        Log.v(TAG, "requestAudioFocus mAudiofocusState= " + mAudiofocusState);
        if (hasAudiofocus()) {
            return true;
        }
        return false;
    }

    private void abandonAudiofocus() {
        ((AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE)).abandonAudioFocus(this);
        mAudiofocusState = AudioManager.AUDIOFOCUS_REQUEST_FAILED;
    }

    private boolean hasAudiofocus() {
        return mAudiofocusState == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    @Override
    public void onAudioFocusChange(int focusChange){
        Log.v(TAG, "AudioFocusChange state is " + focusChange);
        if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
            mAudiofocusState = AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
			//fixbug: 55315 lcw 2018-4-24
            mPlayerExt.resumeIfNeed();
        } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS
                || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            mAudiofocusState = AudioManager.AUDIOFOCUS_REQUEST_FAILED;
            mPlayerExt.pauseIfNeed();
        }
    }

    public class MoviePlayerExtension implements IMoviePlayer, Restorable {
        private static final String KEY_VIDEO_IS_LOOP = "video_is_loop";

        private boolean mIsLoop;
        private boolean mLastPlaying;
        private boolean mLastCanPaused;
        private boolean mPauseBuffering;
        private boolean mResumeNeed = false;

        @Override
        public void stopVideo() {
            Log.v(TAG, "stopVideo()");
            mTState = TState.STOPED;
            mMediaPlayerWrapper.clearSeek();
            mMediaPlayerWrapper.clearDuration();
            mMediaPlayerWrapper.stop();
            mMediaPlayerWrapper.setResumed(false);
            mMovieView.setVisibility(View.INVISIBLE);
            mMovieView.setVisibility(View.VISIBLE);
            clearVideoInfo();
            mFirstBePlayed = false;
            mController.setCanReplay(true);
            mController.showEnded();
            setProgress();
            mHandler.removeCallbacks(mProgressChecker);
        }

        @Override
        public boolean canStop() {
            boolean stopped = false;
            if (mController != null && mOverlayExt != null) {
                stopped = mOverlayExt.isPlayingEnd();
            }
            Log.v(TAG, "canStop() stopped=" + stopped);
            return !stopped;
        }

        @Override
        public boolean getLoop() {
            Log.v(TAG, "getLoop() return " + mIsLoop);
            return mIsLoop;
        }

        @Override
        public void setLoop(boolean loop) {
            Log.v(TAG, "setLoop(" + loop + ") mIsLoop=" + mIsLoop);
            if (MovieUtils.isLocalFile(mVideoType)) {
                mIsLoop = loop;
                if (mTState != TState.STOPED) {
                    mActivityContext.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mController.setCanReplay(mIsLoop);
                        }
                    });
                }
            }
        }

        @Override
        public void startNextVideo(IMovieItem item) {
            IMovieItem next = item;
            if (next != null && next != mMovieItem) {
                int position = mMediaPlayerWrapper.getCurrentPosition();
                int duration = mMediaPlayerWrapper.getDuration();
                mBookmarker
                        .setBookmark(mMovieItem.getUri(), position, duration);
                mMediaPlayerWrapper.stop();
                mMovieView.setVisibility(View.INVISIBLE);
                clearVideoInfo();
                mMovieItem = next;
                ((MovieActivity) mActivityContext).refreshMovieInfo(mMovieItem);
                mVideoType = MovieUtils.judgeStreamingType(mMovieItem.getUri(),
                        mMovieItem.getMimeType());
                mController.refreshSlowMotionMovieInfo(mMovieItem);
                mFirstBePlayed = true;
                doStartVideoCareDrm(false, 0, 0);
                if (mMovieItem.isSlowMotion()) {
                    mController.show();
                }
                mMovieView.setVisibility(View.VISIBLE);
            } else {
                Log.e(TAG, "Cannot play the next video! " + item);
            }
            mActivityContext.closeOptionsMenu();
        }

        @Override
        public void onRestoreInstanceState(Bundle icicle) {
            mIsLoop = icicle.getBoolean(KEY_VIDEO_IS_LOOP, false);
            if (mIsLoop) {
                mController.setCanReplay(true);
            } // else will get can replay from intent.
        }

        @Override
        public void onSaveInstanceState(Bundle outState) {
            outState.putBoolean(KEY_VIDEO_IS_LOOP, mIsLoop);
        }

        private void pauseIfNeed() {
            mLastCanPaused = canStop() && mMediaPlayerWrapper.canPause();
            if (mLastCanPaused) {
                Log.v(TAG, "pauseIfNeed mTState= " + mTState);
                mLastPlaying = (mTState == TState.PLAYING);
                // /M: Reset flag , we don't want use the last result.
                if (!MovieUtils.isLiveStreaming(mVideoType) && isPlaying()
                        && !mIsBuffering) {
                    mPauseBuffering = true;
                    mOverlayExt.clearBuffering();
                    pauseVideo();
                }
            }
            Log.v(TAG, "pauseIfNeed() mLastPlaying=" + mLastPlaying
                    + ", mLastCanPaused=" + mLastCanPaused
                    + ", mPauseBuffering= " + mPauseBuffering + " mTState="
                    + mTState);
        }

        private void resumeIfNeed() {
            if (mLastCanPaused) {
                if (mLastPlaying) {
                    mPauseBuffering = false;
                    // /M: restore mTsate firstly. Because playvideo() maybe
                    // happened in onInfo().
                    mTState = TState.PLAYING;
				    //fixbug: 55315 lcw 2018-4-24
                    playVideo();
                }
            }
            Log.v(TAG, "resumeIfNeed() mLastPlaying=" + mLastPlaying
                    + ", mLastCanPaused=" + mLastCanPaused
                    + ", mPauseBuffering=" + mPauseBuffering);
        }

        public boolean pauseBuffering() {
            return mPauseBuffering;
        }

        @Override
        public int getVideoType() {
            return mVideoType;
        }

        @Override
        public int getVideoPosition() {
            return mVideoPosition;
        }

        @Override
        public int getVideoLastDuration() {
            return mVideoLastDuration;
        }

        @Override
        public void startVideo(final boolean enableFasten, final int position,
                final int duration) {
            doStartVideoCareDrm(enableFasten, position, duration);
        }

        @Override
        public void notifyCompletion() {
            onCompletion();
        }

        public SurfaceView getVideoSurface() {
            return getVideoSurface();
        }

        public boolean canSeekForward() {
            return mMediaPlayerWrapper.canSeekForward();
        }

        public boolean canSeekBackward() {
            return mMediaPlayerWrapper.canSeekBackward();
        }

        public boolean isVideoCanSeek() {
            return mVideoCanSeek;
        }

        public void seekTo(int msec) {
            mMediaPlayerWrapper.seekTo(msec);
        }

        public void setDuration(int duration) {
            mMediaPlayerWrapper.setDuration(duration);
        }

        public int getCurrentPosition() {
            return mMediaPlayerWrapper.getCurrentPosition();
        }

        public int getDuration() {
            return mMediaPlayerWrapper.getDuration();
        }

        public Animation getHideAnimation() {
            return mController.getHideAnimation();
        }

        public boolean isTimeBarEnabled() {
            return mController.isTimeBarEnabled();
        }

        public void updateProgressBar() {
            mHandler.post(mProgressChecker);
        }

        public void showEnded() {
            mController.showEnded();
        }

        public void showMovieController() {
            mController.show();
        }

        @Override
        public void showSubtitleViewSetDialog() {
            // TODO Auto-generated method stub
        }
    }

    private class RetryExtension implements Restorable,
            MediaPlayer.OnErrorListener, MediaPlayer.OnInfoListener {
        private static final String KEY_VIDEO_RETRY_COUNT = "video_retry_count";
        private int mRetryDuration;
        private int mRetryPosition;
        private int mRetryCount;

        private final Runnable mRetryRunnable = new Runnable() {
            @Override
            public void run() {
                Log.v(TAG, "mRetryRunnable.run()");
                retry();
            }
        };

        public void removeRetryRunnable() {
            mHandler.removeCallbacks(mRetryRunnable);
        }

        public void retry() {
            doStartVideoCareDrm(true, mRetryPosition, mRetryDuration);
            Log.v(TAG, "retry() mRetryCount=" + mRetryCount
                    + ", mRetryPosition=" + mRetryPosition);
        }

        public void clearRetry() {
            Log.v(TAG, "clearRetry() mRetryCount=" + mRetryCount);
            mRetryCount = 0;
        }

        public boolean reachRetryCount() {
            Log.v(TAG, "reachRetryCount() mRetryCount=" + mRetryCount);
            if (mRetryCount > 3) {
                return true;
            }
            return false;
        }

        public int getRetryCount() {
            Log.v(TAG, "getRetryCount() return " + mRetryCount);
            return mRetryCount;
        }

        public boolean isRetrying() {
            boolean retry = false;
            if (mRetryCount > 0) {
                retry = true;
            }
            Log.v(TAG, "isRetrying() mRetryCount=" + mRetryCount);
            return retry;
        }

        @Override
        public void onRestoreInstanceState(Bundle icicle) {
            mRetryCount = icicle.getInt(KEY_VIDEO_RETRY_COUNT);
        }

        @Override
        public void onSaveInstanceState(Bundle outState) {
            outState.putInt(KEY_VIDEO_RETRY_COUNT, mRetryCount);
        }

        @Override
        public boolean onError(MediaPlayer mp, int what, int extra) {
            if (what == MEDIA_ERROR_CANNOT_CONNECT_TO_SERVER) {
                // get the last position for retry
                mRetryPosition = mMediaPlayerWrapper.getCurrentPosition();
                mRetryDuration = mMediaPlayerWrapper.getDuration();
                mRetryCount++;
                mTState = TState.RETRY_ERROR;
                if (reachRetryCount()) {
                    mOverlayExt.showReconnectingError();
                    // / M: set replay is true for user can reload video when
                    // media error can not connect to server
                    mController.setCanReplay(true);
                    // When it reach retry count and streaming can not connect
                    // to server,the rewind and forward button should be
                    // disabled
                    if (mMediaPlayerWrapper.canPause()) {
                        mOverlayExt.setCanScrubbing(false);
                        if (mControllerRewindAndForwardExt != null
                                && mControllerRewindAndForwardExt.getView() != null) {
                            mControllerRewindAndForwardExt.setViewEnabled(false);
                        }
                    }
                } else {
                    mOverlayExt.showReconnecting(mRetryCount);
                    mHandler.postDelayed(mRetryRunnable, RETRY_TIME);
                }
                return true;
            }
            return false;
        }

        @Override
        public boolean onInfo(MediaPlayer mp, int what, int extra) {
            if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END) {
                clearRetry();
                return true;
            }
            return false;
        }

        public boolean handleOnReplay() {
            if (isRetrying()) { // from connecting error
                clearRetry();
                int errorPosition = mMediaPlayerWrapper.getCurrentPosition();
                int errorDuration = mMediaPlayerWrapper.getDuration();
                doStartVideoCareDrm(errorPosition > 0, errorPosition,
                        errorDuration);
                Log.v(TAG, "onReplay() errorPosition=" + errorPosition
                            + ", errorDuration=" + errorDuration);
                return true;
            }
            return false;
        }

        public void showRetry() {
            mOverlayExt.showReconnectingError();
            if (mVideoCanSeek || mMediaPlayerWrapper.canSeekForward()) {
                mMediaPlayerWrapper.seekTo(mVideoPosition);
            }
            mMediaPlayerWrapper.setDuration(mVideoLastDuration);
            mRetryPosition = mVideoPosition;
            mRetryDuration = mVideoLastDuration;
        }
    }

    // / Implement MoviePlayerWrapper.Listener {@

    @Override
    public void onSeekComplete(MediaPlayer mp) {
        setProgress();
    }

    @Override
    public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
        if (width == 0 && height == 0) {
            mIsOnlyAudio = true;
        } else {
            mIsOnlyAudio = false;
        }
        if (mOverlayExt != null) {
            mOverlayExt.setBottomPanel(mIsOnlyAudio, true);
        }
        Log.d(TAG, "onVideoSizeChanged(" + width + ", " + height
                + ", mIsAudioOnly = " + mIsOnlyAudio);
    }

    @Override
    public boolean onInfo(MediaPlayer mp, int what, int extra) {
        mRetryExt.onInfo(mp, what, extra);
        // check current video playing status
        checkPlayStatus(what, extra);

        // the first frame showing
        if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
            // / M: performance audo test, begin time record in
            // Gallery2PerformanceTestCase2.java {@
            long endTime = System.currentTimeMillis();
            Log.i(TEST_CASE_TAG,
                    "[Performance Auto Test][VideoPlayback] The duration of open a video end ["
                            + endTime + "]");
            Log.i(TAG,
                    "[CMCC Performance test][Gallery2][Video Playback] open mp4 file end ["
                            + endTime + "]");
            // / @}

            // dismiss place holder
            if (mPlaceHolder != null) {
                mPlaceHolder.setVisibility(View.GONE);
            }

            if (mMovieItem.isSlowMotion() && mController != null) {
                mController.updateSlowMotionSpeed();
            }

            // /M: pause video if video started during a call
            if (!hasAudiofocus()) {
                mPlayerExt.pauseIfNeed();
            }
        }
        // /M:For http streaming, show spinner while seek to a new
        // position.
        handleBuffering(what, extra);
        // /M:HLS_audio-only_02 The mediaplayer shall support metadata
        // embedded in the MPEG2-TS file
        if (what == MediaPlayer.MEDIA_INFO_METADATA_UPDATE) {
            handleMetadataUpdate(mp, what, extra);
        }

        return false;
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        getVideoInfo(mp);
        boolean canPause = mMediaPlayerWrapper.canPause();
        boolean canSeek = mMediaPlayerWrapper.canSeekForward()
                && mMediaPlayerWrapper.canSeekBackward();
        /// M: Restore play state when MHL cable inserted and
        // pause video when MHL cable removed @{
        if (mPowerSavingEvent != PowerSavingEvent.EVENT_NONE) {
            playbackControlforPowerSaving();
        }
        ///@}
        if (!MovieUtils.isLocalFile(mVideoType)) {
            mOverlayExt.setPlayingInfo(MovieUtils.isLiveStreaming(mVideoType));
        }
        mOverlayExt.setCanPause(canPause);
        mOverlayExt.setCanScrubbing(canSeek);
        if (mControllerRewindAndForwardExt != null) {
            mControllerRewindAndForwardExt.updateView();
        }
    }

    @Override
    public void onBufferingUpdate(MediaPlayer mp, int percent) {
        Log.d(TAG,
                "onBufferingUpdate, pauseBuffering = "
                        + mPlayerExt.pauseBuffering());
        if (!mPlayerExt.pauseBuffering()) {
            boolean fullBuffer = !(MovieUtils.isRTSP(mVideoType)
                    || MovieUtils.isLiveStreaming(mVideoType));
            mOverlayExt.showBuffering(fullBuffer, percent);
        }
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        // SetProgress when receive EOS to avoid that sometimes the progress
        // bar is not right because the position got from media player is
        // not in time.Notice that even if the position got again when receive
        // EOS,the progress bar may not exactly right for native may return
        // an inaccurate position.
        setProgress();

        if (mError) {
            Log.e(TAG, "error occured, exit the video player");
            mActivityContext.finish();
            return;
        }

        if (mPlayerExt.getLoop()) {
            onReplay();
        } else {
            mTState = TState.COMPELTED;
            if (mCanReplay) {
                /// M: When some videos play completed, the last position is not
                //  equal to duration. If lock and unlock screen, video will
                //  restore previous and seek to last position, it will cause
                //  replay is playing from last position, not the beginning of
                //  video.
                //  If video play completed, and support replay, stop video
                //  instead of just show ended button {@
                mPlayerExt.stopVideo();
                //  @}
            } else {
                if (!((MovieActivity) mActivityContext).isMultiWindowMode()) {
                    onCompletion();
                } else {
                    Log.v(TAG, "don't finish activity in multi window mode");
                    mPlayerExt.stopVideo();
                }
            }
        }
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        mError = true;
        // Clear mConsumedDrmRight flag when error happened if need, to avoid
        // play drm video without drm right consume
        if (mConsumedDrmRight) {
            Log.v(TAG, "onError, clear mConsumedDrmRight flag");
            mConsumedDrmRight = false;
        }
        if (mServerTimeoutExt != null
                && mServerTimeoutExt.onError(mp, what, extra)) {
            return true;
        }
        if (mRetryExt.onError(mp, what, extra)) {
            return true;
        }

        mHandler.removeCallbacksAndMessages(null);
        mHandler.post(mProgressChecker);
        mController.showErrorMessage("");

        // show error dialog
        if (mMovieView.getWindowToken() != null) {
            int messageId;

            if (what == MEDIA_ERROR_BAD_FILE) {
                if (extra == ERROR_BUFFER_DEQUEUE_FAIL) {
                    return true;
                }
                messageId = R.string.VideoView_error_text_bad_file;
            } else if (what == MEDIA_ERROR_CANNOT_CONNECT_TO_SERVER) {
                messageId = R.string.VideoView_error_text_cannot_connect_to_server;
            } else if (what == MEDIA_ERROR_TYPE_NOT_SUPPORTED) {
                messageId = R.string.VideoView_error_text_type_not_supported;
            } else if (what == MEDIA_ERROR_DRM_NOT_SUPPORTED) {
                messageId = R.string.VideoView_error_text_drm_not_supported;
            } else if (what == MEDIA_ERROR_INVALID_CONNECTION) {
                messageId = R.string.VideoView_error_text_invalid_connection;
            } else if (what == MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK) {
                messageId = com.android.internal.R
                        .string.VideoView_error_text_invalid_progressive_playback;
            } else {
                messageId = com.android.internal.R.string.VideoView_error_text_unknown;
            }
            dismissAllowingStateLoss();
            // do not call error dialog showing when activity has finished
            if (!mActivityContext.isFinishing()) {
                DialogFragment newFragment = ErrorDialogFragment
                        .newInstance(mActivityContext, messageId);
                newFragment.show(mFragmentManager, TAG_ERROR_DIALOG);
                mFragmentManager.executePendingTransactions();
            }
        }

        return true;
    }
    // / @}

    public void onSurfaceDestroyed() {
        if (((MovieActivity) mActivityContext).isMultiWindowMode()) {
            if (!mPowerSavingManager.isInExtensionDisplay()) {
                Log.d(TAG, "MultiWindowListener.onSurfaceDestroyed()");
                doOnPause();
            }
        }
    }
}
