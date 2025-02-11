package com.mediatek.hotknot;

//import java.util.concurrent.atomic.AtomicBoolean;

import android.annotation.SdkConstant;
import android.annotation.SdkConstant.SdkConstantType;
import android.app.Activity;
import android.content.ComponentName;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.ServiceManager;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.UserHandle;
import android.database.Cursor;
import android.database.ContentObserver;
import android.util.Log;

//import com.mediatek.sef.proxy.FeatureProxyBase;
/**
 * Represents the local HotKnot adapter.
 * <p>
 * Use the helper {@link #getDefaultAdapter(Context)} to get the default HotKnot
 * adapter for the Android device.
 *
 */
public final class HotKnotAdapter {
    static final String TAG = "HotKnotAdapter";

    static final String HOTKNOT_SERVICE = "hotknot_service";

    /**
     * Intent to start an Activity when a HotKnot data message (payload) is discovered.
     * <p>The system inspects the MIME type of the HotKnot message and sends this intent
     * containing the MIME type in its type field. This allows the Activity to register
     * IntentFilters targeting a specific MIME type. Activities should register the
     * most specific intent filters
     *
     * @hide
     * @internal
     */
    public static final String ACTION_MESSAGE_DISCOVERED =
                                                   "com.mediatek.hotknot.action.MESSAGE_DISCOVERED";

    /**
     * Mandatory extra containing a byte array of the discovered message data for the
     * {@link #ACTION_MESSAGE_DISCOVERED} intents.
     *
     * @hide
     * @internal
     */
    public static final String EXTRA_DATA = "com.mediatek.hotknot.extra.DATA";


    /**
     * Broadcast action: The state of local HotKnot adapter has been
     * changed, for example HotKnot has been enabled or disabled.
     * <p>Always contains the extra field {@link #EXTRA_ADAPTER_STATE}
     * CreateHotKnotBeamUrisCallback
     *
     * @hide
     * @internal
     */
    public static final String ACTION_ADAPTER_STATE_CHANGED =
        "com.mediatek.hotknot.action.ADAPTER_STATE_CHANGED";

    /**
     * Used as an int extra field in {@link #ACTION_ADAPTER_STATE_CHANGED}
     * intents to request the current HotKnot adapter state. Possible values:
     * {@link #STATE_DISABLED}
     * {@link #STATE_ENABLED}
     *
     * @hide
     * @internal
     */
    public static final String EXTRA_ADAPTER_STATE = "com.mediatek.hotknot.extra.ADAPTER_STATE";

    /**
     * Activity action: Shows HotKnot settings.
     * <p>
     * This shows a UI that enables the uer to turn HotKnot on or off.
     * <p>
     * In some cases, a matching Activity may not exist, so be sure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing
     * @see #isEnabled
     *
     * @hide
     * @internal
     */
    public static final String ACTION_HOTKNOT_SETTINGS = "mediatek.settings.HOTKNOT_SETTINGS";

    /**
     * HotKnot is disabled.
     * @hide
     * @internal
     */
    public static final int STATE_DISABLED = 1;

    /**
     * HotKnot is enabled.
     * @hide
     * @internal
     */
    public static final int STATE_ENABLED = 2;

    /**
     * Return code for setHotKnotMessage API call when the operation was successful.
     * @hide
     * @internal
     */
    public static final int ERROR_SUCCESS = 0;

    /**
     * Return code for setHotKnotMessage API call when HotKnotMessage data size over the 1 KB limit.
     */
    public static final int ERROR_DATA_TOO_LARGE = 1;

    // Locker
    private static Object sLock = new Object();
    // Default HotKnot service link
    private static IHotKnotAdapter sDefaultService;
    // HotKnot Native Status
    private static boolean sIsNativeSupport;
    // HotKnot service link
    HotKnotProxySevice mService;

    /**
     * Class for interacting with the main interface of the backend.
     */
    private ServiceConnection mConnection;

    final  HotKnotActivityManager mHotKnotActivityManager;
    final  Context mContext;
    /**
     * A callback invoked when the system successfully delivers your {@link HotKnotMessage}
     * to another device.
     * @see #setOnHotKnotCompleteCallback
     */
    public interface OnHotKnotCompleteCallback {
        /**
         * Called when a HotKnot message has been sent successfully.
         *
         * <p>This callback is usually made on a binder thread (not the UI thread).
         *
         * @see #setHotKnotMessageCallback
         */
        public void onHotKnotComplete(int reason);
    }

    /**
     * A callback invoked when another device capable of HotKnot message transfer is within range.
     * <p>Implement this interface and pass it to {@link
     * HotKnotAdapter#setHotKnotMessageCallback setHotKnotMessageCallback()} in order to create an
     * {@link HotKnotMessage} when another HotKnot capable device is within range. You would use
     * this option where your application wants to:</p>
     * <li> supply the data to be sent dynamically through an Activity</li>
     * <li> wait until a HotKnot capable device is within range</li>
     * <p>Where you wish to do none of the above and send static data only, you can call
     * {@link #setHotKnotMessage setHotKnotMessage()}</p>
     */
    public interface CreateHotKnotMessageCallback {
        /**
         * Called to provide a {@link HotKnotMessage} to send.
         *
         * <p>This callback is usually made on a binder thread (not the UI thread).
         *
         * <p>Called when this device is in range of another HotKnot capable device. It enables the
         * applications to create a HotKnot message only when it is required.
         *
         * <p>HotKnot exchange cannot occur until this method returns, so do not
         * block for too long.
         *
         * <p>The Android operating system usually shows a system UI
         * on top of your Activity at this time, so do not try to request
         * input from the user to complete the callback. The user probably will not see it.
         *
         * @return HotKnot message to send, or null to not provide a message
         */
        public HotKnotMessage createHotKnotMessage();
    }

    /**
     * A callback invoked when another device capable of HotKnot Beam transfer is within range.
     * <p>Implement this interface and pass it to {@link
     * HotKnotAdapter#setHotKnotBeamUrisCallback setHotKnotBeamUrisCallback()} in order to package
     * one or more URIs for sending when another device capable of HotKnot transfer is within range.
     * You would use this option where your application wants to:</p>
     * <li> supply the URIs to be sent dynamically through an Activity</li>
     * <li> wait until a HotKnot capable device is within range</li>
     * <p>Where you wish to do none of the above and send static URIs only, you can call {@link
     * #setHotKnotBeamUris setHotKnotBeamUris()} instead</p>
     */
    public interface CreateHotKnotBeamUrisCallback {
        public Uri[] createHotKnotBeamUris();
    }

     class HotKnotProxySevice {
          public HotKnotProxySevice() {
                Log.v(TAG, "[HotKnotProxySevice] HotKnotProxySevice constructor");
          }

          public boolean isEnabled() {
                Log.v(TAG, "[HotKnotProxySevice] isEnabled()");
                if (getService() == false) {
                     return false;
                } else {
                    try {
                        return sDefaultService.isEnabled();
                    } catch (RemoteException e) {
                        return false;
                    }
                }
          }

          public boolean enable() {
              Log.v(TAG, "[HotKnotProxySevice] enable()");
              if (getService() == false) {
                  Log.v(TAG, "Starting hotknot service");
                  Intent serviceIntent = new Intent(IHotKnotAdapter.class.getName());
                  serviceIntent.setClassName("com.mediatek.hotknot.service",
                                             "com.mediatek.hotknot.service.HotKnotService");
                  serviceIntent.addFlags(1); //enable HotKnot Service
                  serviceIntent.putExtra("packageName", mContext.getPackageName());
                  mContext.startServiceAsUser(serviceIntent, UserHandle.CURRENT);
                  return true;
             } else {
                  try {
                        return sDefaultService.enable();
                  } catch (RemoteException e) {
                        return false;
                  }
                }
          }
          public boolean disable(boolean saveState) {
              Log.v(TAG, "[HotKnotProxySevice] disable()");
              if (getService() == false) {
                  return false;
              } else {
                  try {
                      return sDefaultService.disable(saveState);
                  } catch (RemoteException e) {
                      return false;
                  }
                }
          }

          public void setHotKnotCallback(IHotKnotCallback callback) {
                  Log.v(TAG, "[HotKnotProxySevice] setHotKnotCallback()");
                  if (getService() == false) {
                      return;
                  } else {
                      try {
                          sDefaultService.setHotKnotCallback(callback);
                      } catch (RemoteException e) {
                          Log.e(TAG, "setHotKnotCallback()", e);
                      }
                  }
          }
    }

    private boolean getService() {

        IBinder binder = ServiceManager.getService(HOTKNOT_SERVICE);
        if (binder == null) {
            sDefaultService = null;
            Log.v(TAG, "[getService] sDefaultService = null");
            return false;
        } else {
            if (binder.pingBinder() == false) {
                Log.v(TAG, "[getService] pingBinder is false");
                return false;
            }
            sDefaultService = IHotKnotAdapter.Stub.asInterface(binder);
            Log.v(TAG, "[getService] sDefaultService = " + sDefaultService);
            return true;
        }
    }

    /**
     * Helpers to get the default HotKnot adapter.
     * @hide
     * @internal
     */
    public static HotKnotAdapter getDefaultAdapter(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("context cannot be null");
        }

        context = context.getApplicationContext();
        if (context == null) {
            throw new IllegalArgumentException(
                "context not associated with any application (using a mock context?)");
        }

        synchronized(sLock) {
            if(sIsNativeSupport) {
                HotKnotAdapter adapter = new HotKnotAdapter(context);
                Log.v(TAG, "[getDefaultAdapter] exist adapter = " + adapter + ", mService = "
                      + adapter.mService);
                return adapter;
            }
        }

        Uri queryUri= Uri.parse("content://com.mediatek.hotknot.Service/native_support");
        Cursor cursor = context.getContentResolver().query(queryUri, null, null, null, null);

        if (cursor == null) {
           Log.v(TAG, "[getDefaultAdapter] cursor = null");

           Log.v(TAG, "[getDefaultAdapter] Starting hotknot service for checking native service");
           Intent serviceIntent = new Intent();
           serviceIntent.setClassName("com.mediatek.hotknot.service",
                                      "com.mediatek.hotknot.service.HotKnotService");
           context.startService(serviceIntent);
           return null;
        } else {
           try {
                if(cursor.moveToFirst()) {
                    String isSupport = cursor.getString(1);
                    if (isSupport.equalsIgnoreCase("false") == true) {
                        Log.v(TAG, "[getDefaultAdapter] isSupport = " + isSupport);
                        return null;
                    }
                }
                else {
                    Log.v(TAG, "[getDefaultAdapter] isSupport = false, cause by null data");
                    return null;
                }
           }
           catch(Exception e) {
                e.printStackTrace();
                return null;
           }
           catch(Error e) {
                e.printStackTrace();
                return null;
           }
           finally {
                cursor.close();
           }
        }

        synchronized(sLock) {
           sIsNativeSupport = true;
           HotKnotAdapter adapter = new HotKnotAdapter(context);
           Log.v(TAG, "[getDefaultAdapter] adapter = " + adapter + ", mService = "
                 + adapter.mService);
           return adapter;
        }
    }

    HotKnotAdapter(Context context) {
        mContext = context;
        mHotKnotActivityManager = new HotKnotActivityManager(this);

        mService = new HotKnotProxySevice();
    }

    /**
     * Returns true if this HotKnot adapter has any feature enabled.
     *
     * <p>If this method returns false, the HotKnot hardware is guaranteed not to
     * generate or respond to any HotKnot communication over its HotKnot link.
     * <p>Applications can use this to check if HotKnot is enabled. Applications
     * can request Settings UI to allow the user to toggle HotKnot using:
     * <p><pre>startActivity(new Intent({@link HotKnotAdapter#ACTION_HOTKNOT_SETTINGS
     * HotKnotAdapter.ACTION_HOTKNOT_SETTINGS}))</pre>
     *
     * @return true if this HotKnot adapter has any features enabled
     * @hide
     * @internal
     */
    public boolean isEnabled() {
        return mService.isEnabled();
    }

    /**
     * Enables HotKnot hardware.
     *
     * <p>This call is asynchronous. Listen to
     * {@link #ACTION_ADAPTER_STATE_CHANGED} broadcasts to find out when the
     * operation is completed.
     *
     * <p>If this returns true, either HotKnot will be enabled, or
     * a {@link #ACTION_ADAPTER_STATE_CHANGED} broadcast will be sent
     * to indicate a state transition. If this returns false,
     * there will be some problems that prevent an attempt to enable
     * HotKnot.
     *
     * @hide
     * @internal
     */
    public boolean enable() {
        return mService.enable();
    }

    /**
     * Disables HotKnot hardware.
     *
     * <p>No HotKnot feature will work after this call, and the hardware
     * will not perform or respond to any HotKnot communication.
     *
     * <p>This call is asynchronous. Listen to
     * {@link #ACTION_ADAPTER_STATE_CHANGED} broadcasts to find out when the
     * operation is completed.
     *
     * <p>If this returns true, either HotKnot will be disabled, or
     * a {@link #ACTION_ADAPTER_STATE_CHANGED} broadcast will be sent
     * to indicate a state transition. If this returns false,
     * there will be some problems that prevent an attempt to disable HotKnot
     *
     * @hide
     * @internal
     */

    public boolean disable() {
        return mService.disable(true);
    }


    /**
     * Sets up a static {@link HotKnotMessage} to send using HotKnot.
     *
     * <p>This method may be called any time before Activity's onDestroy,
     * but the HotKnot message is only made available for HotKnot send when the
     * specified activity(s) are in resumed (foreground) state. The recommended
     * approach is calling this method during your Activity's onCreate (see the example
     * code below). This method does not immediately perform any I/O or blocking work,
     * so it is safe to call on your main thread.
     *
     * <p>Only one HotKnot message can be sent by the currently resumed Activity.
     * If both {@link #setHotKnotMessage} and
     * {@link #setHotKnotMessageCallback} are set,
     * the callback will take priority.
     * <p>If {@link #setHotKnotMessage} is called with a null HotKnot message,
     * and/or {@link #setHotKnotMessageCallback} is called with a null callback,
     * HotKnot exchange will be disabled for the specified activity(s).
     *
     * <p>The API allows multiple Activities to be specified at a time,
     * but it is strongly recommended to register only one at a time
     * and to do so during Activity's onCreate. For example:
     * <pre>
     * protected void onCreate(Bundle savedInstanceState) {
     *     super.onCreate(savedInstanceState);
     *     HotKnotAdapter hotKnotAdapter = HotKnotAdapter.getDefaultAdapter(this);
     *     if (hotKnotAdapter == null) return;  // HotKnot not available on this device
     *     hotKnotAdapter.setHotKnotMessage(HotKnotMessage, this);
     * }</pre>
     * Only one call per Activity is necessary. The Android
     * OS will automatically release its references to the HotKnot message and the
     * Activity object when it is destroyed if you follow this pattern.
     *
     * <p>If your Activity is to dynamically generate an HotKnot message,
     * set a callback using {@link #setHotKnotMessageCallback} instead
     * of a static message.
     *
     * <p class="note">Do not pass in an Activity that has already been through
     * Activity's onDestroy. This is guaranteed if you call this API
     * during Activity's onCreate.
     *
     * <p class="note">Requires "android.permission.HOTKNOT" permission.
     *
     * @param message HotKnot message to send over HotKnot, or null to disable
     * @param activity Activity for which the HotKnot message will be sent
     * @param activities Optional additional activities, however we strongly recommend
     *        to only register one at a time, and to do so in that Activity's
     *        Activity's onCreate.
     * @hide
     * @internal
     */
    public void setHotKnotMessage(HotKnotMessage message, Activity activity,
                                  Activity ... activities) {
        try {
            if (activity == null) {
                throw new NullPointerException("activity cannot be null");
            }
            mHotKnotActivityManager.setHotKnotMessage(activity, message, 0);
            for (Activity a : activities) {
                if (a == null) {
                    throw new NullPointerException("activities cannot contain null");
                }
                mHotKnotActivityManager.setHotKnotMessage(a, message, 0);
            }
        } catch (IllegalStateException e) {
            // Prevent new applications from making this mistake, re-throw
            throw(e);
        }
    }

    /**
     * @hide
     */
    public void setHotKnotMessage(HotKnotMessage message, Activity activity, int flags) {
        if (activity == null) {
            throw new NullPointerException("activity cannot be null");
        }
        mHotKnotActivityManager.setHotKnotMessage(activity, message, flags);
    }

    /**
     * Sets up a callback that dynamically generates HotKnot messages to send using HotKnot.
     *
     * <p>This method may be called any time before Activity's onDestroy,
     * but the HotKnot message callback can only occur when the
     * specified activity(s) are in resumed (foreground) state. The recommended
     * approach is calling this method during your Activity's onCreate (see the example
     * code below). This method does not immediately perform any I/O or blocking work,
     * so it is safe to call on your main thread.
     *
     * <p>Only one HotKnot message can be sent by the currently resumed Activity.
     * If both {@link #setHotKnotMessage} and
     * {@link #setHotKnotMessageCallback} are set,
     * the callback will take priority.
     *
     * <p>If {@link #setHotKnotMessage} is called with a null HotKnot message,
     * and/or {@link #setHotKnotMessageCallback} is called with a null callback,
     * HotKnot exchange will be disabled for the specified activity(s).
     *
     * <p>The API allows multiple Activities to be specified at a time,
     * but it is strongly recommended to register only one at a time
     * and to do so during Activity's onCreate. For example:
     * <pre>
     * protected void onCreate(Bundle savedInstanceState) {
     *     super.onCreate(savedInstanceState);
     *     HotKnotAdapter hotKnotAdapter = HotKnotAdapter.getDefaultAdapter(this);
     *     if (hotKnotAdapter == null) return;  // HotKnot not available on this device
     *     hotKnotAdapter.setHotKnotMessageCallback(callback, this);
     * }</pre>
     * Only one call per Activity is necessary. The Android
     * OS will automatically release its references to the callback and the
     * Activity object when it is destroyed if you follow this pattern.
     *
     * <p class="note">Do not pass in an Activity that has already been through
     * Activity's onDestroy. This is guaranteed if you call this API
     * during Activity's onCreate.
     *
     * <p class="note">Requires "android.permission.HOTKNOT" permission.
     *
     * @param callback Callback, or null to disable
     * @param activity Activity for which the HotKnot message will be sent
     * @param activities Optional additional activities, however we strongly recommend
     *        to only register one at a time, and to do so in that Activity's
     *        Activity's onCreate.
     * @hide
     * @internal
     */
    public void setHotKnotMessageCallback(CreateHotKnotMessageCallback callback, Activity activity,
                                          Activity ... activities) {
        try {
            if (activity == null) {
                throw new NullPointerException("activity cannot be null");
            }
            mHotKnotActivityManager.setHotKnotMessageCallback(activity, callback, 0);
            for (Activity a : activities) {
                if (a == null) {
                    throw new NullPointerException("activities cannot contain null");
                }
                mHotKnotActivityManager.setHotKnotMessageCallback(a, callback, 0);
            }
        } catch (IllegalStateException e) {
            // Prevent new applications from making this mistake, re-throw
            throw(e);
        }
    }

    /**
     * @hide
     */
    public void setHotKnotMessageCallback(CreateHotKnotMessageCallback callback, Activity activity,
                                          int flags) {
        if (activity == null) {
            throw new NullPointerException("activity cannot be null");
        }
        mHotKnotActivityManager.setHotKnotMessageCallback(activity, callback, flags);
    }

    /**
     * Sets up a callback on successful HotKnot.
     *
     * <p>This method may be called any time before Activity's onDestroy,
     * but the callback can only occur when the
     * specified activity(s) are in resumed (foreground) state. The recommended
     * approach is calling this method during your Activity's onCreate (see the example
     * code below). This method does not immediately perform any I/O or blocking work,
     * so it is safe to call on your main thread.
     *
     * <p>The API allows multiple Activities to be specified at a time,
     * but it is strongly recommended to register only one at a time
     * and to do so during Activity's onCreate. For example:
     * <pre>
     * protected void onCreate(Bundle savedInstanceState) {
     *     super.onCreate(savedInstanceState);
     *     HotKnotAdapter hotKnotAdapter = HotKnotAdapter.getDefaultAdapter(this);
     *     if (hotKnotAdapter == null) return;  // HotKnot not available on this device
     *     hotKnotAdapter.setOnHotKnotCompleteCallback(callback, this);
     * }</pre>
     * Only one call per Activity is necessary. The Android
     * OS will automatically release its references to the callback and the
     * Activity object when it is destroyed if you follow this pattern.
     *
     * <p class="note">Do not pass in an Activity that has already been through
     * Activity's onDestroy. This is guaranteed if you call this API
     * during Activity's onCreate.
     *
     * <p class="note">Requires "android.permission.HOTKNOT" permission.
     *
     * @param callback Callback, or null to disable
     * @param activity Activity for which the HotKnot message will be sent
     * @param activities Optional additional activities, however we strongly recommend
     *        to only register one at a time, and to do so in that Activity's
     *        Activity's onCreate.
     * @hide
     * @internal
     */
    public void setOnHotKnotCompleteCallback(OnHotKnotCompleteCallback callback,
            Activity activity, Activity ... activities) {
        try {
            if (activity == null) {
                throw new NullPointerException("activity cannot be null");
            }
            mHotKnotActivityManager.setOnHotKnotCompleteCallback(activity, callback);
            for (Activity a : activities) {
                if (a == null) {
                    throw new NullPointerException("activities cannot contain null");
                }
                mHotKnotActivityManager.setOnHotKnotCompleteCallback(a, callback);
            }
        } catch (IllegalStateException e) {
            // Prevent new applications from making this mistake, re-throw
            throw(e);
        }
    }

    /**
     * Sets up one or more URIs to send using HotKnot. Every
     * URI you provide must have either scheme 'file' or scheme 'content'.
     *
     * <p>For the data provided through this method, HotKnot tries to
     * switch to alternate transports such as Wi-Fi Direct to achieve a fast
     * transfer speed. Hence this method is suitable
     * for transferring large files such as pictures or songs.
     *
     * <p>The receiving side stores the content of each URI in
     * a file and presents a notification to the user to open the file
     * with an android.content.Intent with action android.content.Intent.ACTION_VIEW.
     * If multiple URIs are sent, android.content.Intent will refer
     * to the first of the stored files.
     *
     * <p>This method may be called any time before Activity's onDestroy,
     * but the URI(s) are only made available for HotKnot when the
     * specified activity(s) are in resumed (foreground) state. The recommended
     * approach is calling this method during your Activity's onCreate (see the example code below).
     * This method does not immediately perform any I/O or blocking work,
     * so it is safe to call on your main thread.
     *
     * <p>{@link #setHotKnotBeamUris} and {@link #setHotKnotBeamUrisCallback}
     * have priority over both {@link #setHotKnotMessage} and
     * {@link #setHotKnotMessageCallback}.
     *
     * <p>If {@link #setHotKnotBeamUris} is called with a null URI array,
     * and/or {@link #setHotKnotBeamUrisCallback} is called with a null callback,
     * the URI push will be disabled for the specified activity(s).
     *
     * <p>Code example:
     * <pre>
     * protected void onCreate(Bundle savedInstanceState) {
     *     super.onCreate(savedInstanceState);
     *     HotKnotAdapter hotKnotAdapter = HotKnotAdapter.getDefaultAdapter(this);
     *     if (hotKnotAdapter == null) return;  // HotKnot not available on this device
     *     hotKnotAdapter.setHotKnotBeamUris(new Uri[] {uri1, uri2}, this);
     * }</pre>
     * Only one call per Activity is necessary. The Android
     * OS will automatically release its references to the URI(s) and the
     * Activity object when it is destroyed if you follow this pattern.
     *
     * <p>If your Activity is to dynamically supply URI(s),
     * set a callback using {@link #setHotKnotBeamUrisCallback} instead
     * of using this method.
     *
     * <p class="note">Do not pass in an Activity that has already been through
     * Activity's onDestroy. This is guaranteed if you call this API
     * during Activity's onCreate.
     *
     * <p class="note">Requires "android.permission.HOTKNOT" permission.
     *
     * @param uris An array of URI(s) to be sent over HotKnot
     * @param activity Activity for which the URI(s) will be pushed
     * @hide
     * @internal
     */
    public void setHotKnotBeamUris(Uri[] uris, Activity activity) {
        if (activity == null) {
            throw new NullPointerException("activity cannot be null");
        }
        if (uris != null) {
            for (Uri uri : uris) {
                if (uri == null) throw new NullPointerException("Uri not " +
                        "allowed to be null");
                String scheme = uri.getScheme();
                if (scheme == null || (!scheme.equalsIgnoreCase("file") &&
                        !scheme.equalsIgnoreCase("content"))) {
                    throw new IllegalArgumentException("URI needs to have " +
                            "either scheme file or scheme content");
                }
            }
        }
        mHotKnotActivityManager.setHotKnotContent(activity, uris);
    }

    /**
     * Sets up a callback that dynamically generates one or more URIs
     * to send using HotKnot. Every URI the callback provides
     * must have either scheme 'file' or scheme 'content'.
     *
     * <p>For the data provided through this callback, HotKnot tries to
     * switch to alternate transports such as Wi-Fi Direct to achieve a fast
     * transfer speed. Hence this method is suitable
     * for transferring large files such as pictures or songs.
     *
     * <p>The receiving side stores the content of each URI in
     * a file and present a notification to the user to open the file
     * with an android.content.Intent with action android.content.Intent.ACTION_VIEW.
     * If multiple URIs are sent, android.content.Intent will refer
     * to the first of the stored files.
     *
     * <p>This method may be called any time before Activity's onDestroy,
     * but the URI(s) are only made available for HotKnot when the
     * specified activity(s) are in resumed (foreground) state. The recommended
     * approach is calling this method during your Activity's
     * Activity's onCreate (see the example code below).
     * This method does not immediately perform any I/O or blocking work,
     * so it is safe to call on your main thread.
     *
     * <p>{@link #setHotKnotBeamUris} and {@link #setHotKnotBeamUrisCallback}
     * have priority over both {@link #setHotKnotMessage} and
     * {@link #setHotKnotMessageCallback}.
     *
     * <p>If {@link #setHotKnotBeamUris} is called with a null URI array,
     * and/or {@link #setHotKnotBeamUrisCallback} is called with a null callback,
     * the URI push will be disabled for the specified activity(s).
     *
     * <p>Code example:
     * <pre>
     * protected void onCreate(Bundle savedInstanceState) {
     *     super.onCreate(savedInstanceState);
     *     HotKnotAdapter hotKnotAdapter = HotKnotAdapter.getDefaultAdapter(this);
     *     if (hotKnotAdapter == null) return;  // HotKnot not available on this device
     *     hotKnotAdapter.setHotKnotBeamUrisCallback(callback, this);
     * }</pre>
     * Only one call per Activity is necessary. The Android
     * OS will automatically release its references to the URI(s) and the
     * Activity object when it is destroyed if you follow this pattern.
     *
     * <p class="note">Do not pass in an Activity that has already been through
     * Activity's onDestroy. This is guaranteed if you call this API
     * during Activity's onCreate}.
     *
     * <p class="note">Requires "android.permission.HOTKNOT" permission.
     *
     * @param callback Callback, or null to disable
     * @param activity Activity for which the URI(s) will be pushed
     * @hide
     * @internal
     */
    public void setHotKnotBeamUrisCallback(
                    CreateHotKnotBeamUrisCallback callback,
                    Activity activity) {

        if (activity == null) {
            throw new NullPointerException("activity cannot be null");
        }
        mHotKnotActivityManager.setHotKnotContentCallback(activity, callback);
    }

}
