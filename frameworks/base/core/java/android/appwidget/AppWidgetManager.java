/*
 * Copyright (C) 2006 The Android Open Source Project
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

package android.appwidget;

import android.annotation.Nullable;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ParceledListSlice;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.widget.RemoteViews;

import com.android.internal.appwidget.IAppWidgetService;

import java.util.Collections;
import java.util.List;
import android.os.SystemProperties;

/**
 * Updates AppWidget state; gets information about installed AppWidget providers and other
 * AppWidget related state.
 *
 * <div class="special reference">
 * <h3>Developer Guides</h3>
 * <p>For more information about creating app widgets, read the
 * <a href="{@docRoot}guide/topics/appwidgets/index.html">App Widgets</a> developer guide.</p>
 * </div>
 */
public class AppWidgetManager {

    /**
     * Activity action to launch from your {@link AppWidgetHost} activity when you want to
     * pick an AppWidget to display.  The AppWidget picker activity will be launched.
     * <p>
     * You must supply the following extras:
     * <table>
     *   <tr>
     *     <td>{@link #EXTRA_APPWIDGET_ID}</td>
     *     <td>A newly allocated appWidgetId, which will be bound to the AppWidget provider
     *         once the user has selected one.</td>
     *  </tr>
     * </table>
     *
     * <p>
     * The system will respond with an onActivityResult call with the following extras in
     * the intent:
     * <table>
     *   <tr>
     *     <td>{@link #EXTRA_APPWIDGET_ID}</td>
     *     <td>The appWidgetId that you supplied in the original intent.</td>
     *  </tr>
     * </table>
     * <p>
     * When you receive the result from the AppWidget pick activity, if the resultCode is
     * {@link android.app.Activity#RESULT_OK}, an AppWidget has been selected.  You should then
     * check the AppWidgetProviderInfo for the returned AppWidget, and if it has one, launch its
     * configuration activity.  If {@link android.app.Activity#RESULT_CANCELED} is returned, you
     * should delete the appWidgetId.
     *
     * @see #ACTION_APPWIDGET_CONFIGURE
     */
    public static final String ACTION_APPWIDGET_PICK = "android.appwidget.action.APPWIDGET_PICK";

    /**
     * @hide
     */
    private final static String TAG = "AppWidgetManager";

    /**
     * Similar to ACTION_APPWIDGET_PICK, but used from keyguard
     * @hide
     */
    public static final String
            ACTION_KEYGUARD_APPWIDGET_PICK = "android.appwidget.action.KEYGUARD_APPWIDGET_PICK";

    /**
     * Activity action to launch from your {@link AppWidgetHost} activity when you want to bind
     * an AppWidget to display and bindAppWidgetIdIfAllowed returns false.
     * <p>
     * You must supply the following extras:
     * <table>
     *   <tr>
     *     <td>{@link #EXTRA_APPWIDGET_ID}</td>
     *     <td>A newly allocated appWidgetId, which will be bound to the AppWidget provider
     *         you provide.</td>
     *  </tr>
     *  <tr>
     *     <td>{@link #EXTRA_APPWIDGET_PROVIDER}</td>
     *     <td>The BroadcastReceiver that will be the AppWidget provider for this AppWidget.
     *     </td>
     *  </tr>
     *  <tr>
     *     <td>{@link #EXTRA_APPWIDGET_PROVIDER_PROFILE}</td>
     *     <td>An optional handle to a user profile under which runs the provider
     *     for this AppWidget.
     *     </td>
     *  </tr>
     * </table>
     *
     * <p>
     * The system will respond with an onActivityResult call with the following extras in
     * the intent:
     * <table>
     *   <tr>
     *     <td>{@link #EXTRA_APPWIDGET_ID}</td>
     *     <td>The appWidgetId that you supplied in the original intent.</td>
     *  </tr>
     * </table>
     * <p>
     * When you receive the result from the AppWidget bind activity, if the resultCode is
     * {@link android.app.Activity#RESULT_OK}, the AppWidget has been bound.  You should then
     * check the AppWidgetProviderInfo for the returned AppWidget, and if it has one, launch its
     * configuration activity.  If {@link android.app.Activity#RESULT_CANCELED} is returned, you
     * should delete the appWidgetId.
     *
     * @see #ACTION_APPWIDGET_CONFIGURE
     *
     */
    public static final String ACTION_APPWIDGET_BIND = "android.appwidget.action.APPWIDGET_BIND";

    /**
     * Sent when it is time to configure your AppWidget while it is being added to a host.
     * This action is not sent as a broadcast to the AppWidget provider, but as a startActivity
     * to the activity specified in the {@link AppWidgetProviderInfo AppWidgetProviderInfo
     * meta-data}.
     *
     * <p>
     * The intent will contain the following extras:
     * <table>
     *   <tr>
     *     <td>{@link #EXTRA_APPWIDGET_ID}</td>
     *     <td>The appWidgetId to configure.</td>
     *  </tr>
     * </table>
     *
     * <p>If you return {@link android.app.Activity#RESULT_OK} using
     * {@link android.app.Activity#setResult Activity.setResult()}, the AppWidget will be added,
     * and you will receive an {@link #ACTION_APPWIDGET_UPDATE} broadcast for this AppWidget.
     * If you return {@link android.app.Activity#RESULT_CANCELED}, the host will cancel the add
     * and not display this AppWidget, and you will receive a {@link #ACTION_APPWIDGET_DELETED}
     * broadcast.
     */
    public static final String ACTION_APPWIDGET_CONFIGURE = "android.appwidget.action.APPWIDGET_CONFIGURE";

    /**
     * An intent extra that contains one appWidgetId.
     * <p>
     * The value will be an int that can be retrieved like this:
     * {@sample frameworks/base/tests/appwidgets/AppWidgetHostTest/src/com/android/tests/appwidgethost/AppWidgetHostActivity.java getExtra_EXTRA_APPWIDGET_ID}
     */
    public static final String EXTRA_APPWIDGET_ID = "appWidgetId";

    /**
     * A bundle extra that contains the lower bound on the current width, in dips, of a widget instance.
     */
    public static final String OPTION_APPWIDGET_MIN_WIDTH = "appWidgetMinWidth";

    /**
     * A bundle extra that contains the lower bound on the current height, in dips, of a widget instance.
     */
    public static final String OPTION_APPWIDGET_MIN_HEIGHT = "appWidgetMinHeight";

    /**
     * A bundle extra that contains the upper bound on the current width, in dips, of a widget instance.
     */
    public static final String OPTION_APPWIDGET_MAX_WIDTH = "appWidgetMaxWidth";

    /**
     * A bundle extra that contains the upper bound on the current width, in dips, of a widget instance.
     */
    public static final String OPTION_APPWIDGET_MAX_HEIGHT = "appWidgetMaxHeight";

    /**
     * A bundle extra that hints to the AppWidgetProvider the category of host that owns this
     * this widget. Can have the value {@link
     * AppWidgetProviderInfo#WIDGET_CATEGORY_HOME_SCREEN} or {@link
     * AppWidgetProviderInfo#WIDGET_CATEGORY_KEYGUARD} or {@link
     * AppWidgetProviderInfo#WIDGET_CATEGORY_SEARCHBOX}.
     */
    public static final String OPTION_APPWIDGET_HOST_CATEGORY = "appWidgetCategory";

    /**
     * An intent extra which points to a bundle of extra information for a particular widget id.
     * In particular this bundle can contain {@link #OPTION_APPWIDGET_MIN_WIDTH},
     * {@link #OPTION_APPWIDGET_MIN_HEIGHT}, {@link #OPTION_APPWIDGET_MAX_WIDTH},
     * {@link #OPTION_APPWIDGET_MAX_HEIGHT}.
     */
    public static final String EXTRA_APPWIDGET_OPTIONS = "appWidgetOptions";

    /**
     * An intent extra that contains multiple appWidgetIds.
     * <p>
     * The value will be an int array that can be retrieved like this:
     * {@sample frameworks/base/tests/appwidgets/AppWidgetHostTest/src/com/android/tests/appwidgethost/TestAppWidgetProvider.java getExtra_EXTRA_APPWIDGET_IDS}
     */
    public static final String EXTRA_APPWIDGET_IDS = "appWidgetIds";

    /**
     * An intent extra that contains the component name of a AppWidget provider.
     * <p>
     * The value will be an {@link android.content.ComponentName}.
     */
    public static final String EXTRA_APPWIDGET_PROVIDER = "appWidgetProvider";

    /**
     * An intent extra that contains the user handle of the profile under
     * which an AppWidget provider is registered.
     * <p>
     * The value will be a {@link android.os.UserHandle}.
     */
    public static final String EXTRA_APPWIDGET_PROVIDER_PROFILE = "appWidgetProviderProfile";

    /**
     * An intent extra to pass to the AppWidget picker containing a {@link java.util.List} of
     * {@link AppWidgetProviderInfo} objects to mix in to the list of AppWidgets that are
     * installed.  (This is how the launcher shows the search widget).
     */
    public static final String EXTRA_CUSTOM_INFO = "customInfo";

    /**
     * An intent extra attached to the {@link #ACTION_APPWIDGET_HOST_RESTORED} broadcast,
     * indicating the integer ID of the host whose widgets have just been restored.
     */
    public static final String EXTRA_HOST_ID = "hostId";

    /**
     * An intent extra to pass to the AppWidget picker containing a {@link java.util.List} of
     * {@link android.os.Bundle} objects to mix in to the list of AppWidgets that are
     * installed.  It will be added to the extras object on the {@link android.content.Intent}
     * that is returned from the picker activity.
     *
     * {@more}
     */
    public static final String EXTRA_CUSTOM_EXTRAS = "customExtras";

    /**
     * An intent extra to pass to the AppWidget picker which allows the picker to filter
     * the list based on the {@link AppWidgetProviderInfo#widgetCategory}.
     *
     * @hide
     */
    public static final String EXTRA_CATEGORY_FILTER = "categoryFilter";

    /**
     * An intent extra to pass to the AppWidget picker to specify whether or not to sort
     * the list of caller-specified extra AppWidgets along with the rest of the AppWidgets
     * @hide
     */
    public static final String EXTRA_CUSTOM_SORT = "customSort";

    /**
     * A sentinel value that the AppWidget manager will never return as a appWidgetId.
     */
    public static final int INVALID_APPWIDGET_ID = 0;

    /**
     * Sent when it is time to update your AppWidget.
     *
     * <p>This may be sent in response to a new instance for this AppWidget provider having
     * been instantiated, the requested {@link AppWidgetProviderInfo#updatePeriodMillis update interval}
     * having lapsed, or the system booting.
     *
     * <p>
     * The intent will contain the following extras:
     * <table>
     *   <tr>
     *     <td>{@link #EXTRA_APPWIDGET_IDS}</td>
     *     <td>The appWidgetIds to update.  This may be all of the AppWidgets created for this
     *     provider, or just a subset.  The system tries to send updates for as few AppWidget
     *     instances as possible.</td>
     *  </tr>
     * </table>
     *
     * @see AppWidgetProvider#onUpdate AppWidgetProvider.onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds)
     */
    public static final String ACTION_APPWIDGET_UPDATE = "android.appwidget.action.APPWIDGET_UPDATE";

    /**
     * Sent when the custom extras for an AppWidget change.
     *
     * <p class="note">This is a protected intent that can only be sent
     * by the system.
     *
     * @see AppWidgetProvider#onAppWidgetOptionsChanged
     *      AppWidgetProvider.onAppWidgetOptionsChanged(Context context,
     *      AppWidgetManager appWidgetManager, int appWidgetId, Bundle newExtras)
     */
    public static final String ACTION_APPWIDGET_OPTIONS_CHANGED = "android.appwidget.action.APPWIDGET_UPDATE_OPTIONS";

    /**
     * Sent when an instance of an AppWidget is deleted from its host.
     *
     * <p class="note">This is a protected intent that can only be sent
     * by the system.
     *
     * @see AppWidgetProvider#onDeleted AppWidgetProvider.onDeleted(Context context, int[] appWidgetIds)
     */
    public static final String ACTION_APPWIDGET_DELETED = "android.appwidget.action.APPWIDGET_DELETED";

    /**
     * Sent when the last AppWidget of this provider is removed from the last host.
     *
     * <p class="note">This is a protected intent that can only be sent
     * by the system.
     *
     * @see AppWidgetProvider#onEnabled AppWidgetProvider.onDisabled(Context context)
     */
    public static final String ACTION_APPWIDGET_DISABLED = "android.appwidget.action.APPWIDGET_DISABLED";

    /**
     * Sent when an instance of an AppWidget is added to a host for the first time.
     * This broadcast is sent at boot time if there is a AppWidgetHost installed with
     * an instance for this provider.
     *
     * <p class="note">This is a protected intent that can only be sent
     * by the system.
     *
     * @see AppWidgetProvider#onEnabled AppWidgetProvider.onEnabled(Context context)
     */
    public static final String ACTION_APPWIDGET_ENABLED = "android.appwidget.action.APPWIDGET_ENABLED";

    /**
     * Sent to an {@link AppWidgetProvider} after AppWidget state related to that provider has
     * been restored from backup. The intent contains information about how to translate AppWidget
     * ids from the restored data to their new equivalents.
     *
     * <p>The intent will contain the following extras:
     *
     * <table>
     *   <tr>
     *     <td>{@link #EXTRA_APPWIDGET_OLD_IDS}</td>
     *     <td>The set of appWidgetIds represented in a restored backup that have been successfully
     *     incorporated into the current environment.  This may be all of the AppWidgets known
     *     to this application, or just a subset.  Each entry in this array of appWidgetIds has
     *     a corresponding entry in the {@link #EXTRA_APPWIDGET_IDS} extra.</td>
     *  </tr>
     *   <tr>
     *     <td>{@link #EXTRA_APPWIDGET_IDS}</td>
     *     <td>The set of appWidgetIds now valid for this application.  The app should look at
     *     its restored widget configuration and translate each appWidgetId in the
     *     {@link #EXTRA_APPWIDGET_OLD_IDS} array to its new value found at the corresponding
     *     index within this array.</td>
     *  </tr>
     * </table>
     *
     * <p class="note">This is a protected intent that can only be sent
     * by the system.
     *
     * @see #ACTION_APPWIDGET_HOST_RESTORED
     */
    public static final String ACTION_APPWIDGET_RESTORED
            = "android.appwidget.action.APPWIDGET_RESTORED";

    /**
     * Sent to widget hosts after AppWidget state related to the host has been restored from
     * backup. The intent contains information about how to translate AppWidget ids from the
     * restored data to their new equivalents.  If an application maintains multiple separate
     * widget host instances, it will receive this broadcast separately for each one.
     *
     * <p>The intent will contain the following extras:
     *
     * <table>
     *   <tr>
     *     <td>{@link #EXTRA_APPWIDGET_OLD_IDS}</td>
     *     <td>The set of appWidgetIds represented in a restored backup that have been successfully
     *     incorporated into the current environment.  This may be all of the AppWidgets known
     *     to this application, or just a subset.  Each entry in this array of appWidgetIds has
     *     a corresponding entry in the {@link #EXTRA_APPWIDGET_IDS} extra.</td>
     *  </tr>
     *   <tr>
     *     <td>{@link #EXTRA_APPWIDGET_IDS}</td>
     *     <td>The set of appWidgetIds now valid for this application.  The app should look at
     *     its restored widget configuration and translate each appWidgetId in the
     *     {@link #EXTRA_APPWIDGET_OLD_IDS} array to its new value found at the corresponding
     *     index within this array.</td>
     *  </tr>
     *  <tr>
     *     <td>{@link #EXTRA_HOST_ID}</td>
     *     <td>The integer ID of the widget host instance whose state has just been restored.</td>
     *  </tr>
     * </table>
     *
     * <p class="note">This is a protected intent that can only be sent
     * by the system.
     *
     * @see #ACTION_APPWIDGET_RESTORED
     */
    public static final String ACTION_APPWIDGET_HOST_RESTORED
            = "android.appwidget.action.APPWIDGET_HOST_RESTORED";

    /**
     * An intent extra that contains multiple appWidgetIds.  These are id values as
     * they were provided to the application during a recent restore from backup.  It is
     * attached to the {@link #ACTION_APPWIDGET_RESTORED} broadcast intent.
     *
     * <p>
     * The value will be an int array that can be retrieved like this:
     * {@sample frameworks/base/tests/appwidgets/AppWidgetHostTest/src/com/android/tests/appwidgethost/TestAppWidgetProvider.java getExtra_EXTRA_APPWIDGET_IDS}
     */
    public static final String EXTRA_APPWIDGET_OLD_IDS = "appWidgetOldIds";

    /**
     * Field for the manifest meta-data tag.
     *
     * @see AppWidgetProviderInfo
     */
    public static final String META_DATA_APPWIDGET_PROVIDER = "android.appwidget.provider";

    private final String mPackageName;

    private final IAppWidgetService mService;

    private final DisplayMetrics mDisplayMetrics;

    /**
     * Get the AppWidgetManager instance to use for the supplied {@link android.content.Context
     * Context} object.
     */
    public static AppWidgetManager getInstance(Context context) {
        return (AppWidgetManager) context.getSystemService(Context.APPWIDGET_SERVICE);
    }

    /**
     * Creates a new instance.
     *
     * @param context The current context in which to operate.
     * @param service The backing system service.
     * @hide
     */
    public AppWidgetManager(Context context, IAppWidgetService service) {
        mPackageName = context.getOpPackageName();
        mService = service;
        mDisplayMetrics = context.getResources().getDisplayMetrics();
    }

    /**
     * Set the RemoteViews to use for the specified appWidgetIds.
     * <p>
     * Note that the RemoteViews parameter will be cached by the AppWidgetService, and hence should
     * contain a complete representation of the widget. For performing partial widget updates, see
     * {@link #partiallyUpdateAppWidget(int[], RemoteViews)}.
     *
     * <p>
     * It is okay to call this method both inside an {@link #ACTION_APPWIDGET_UPDATE} broadcast,
     * and outside of the handler.
     * This method will only work when called from the uid that owns the AppWidget provider.
     *
     * <p>
     * The total Bitmap memory used by the RemoteViews object cannot exceed that required to
     * fill the screen 1.5 times, ie. (screen width x screen height x 4 x 1.5) bytes.
     *
     * @param appWidgetIds The AppWidget instances for which to set the RemoteViews.
     * @param views The RemoteViews object to show.
     */
    public void updateAppWidget(int[] appWidgetIds, RemoteViews views) {
        if (mService == null) {
            return;
        }
        try {
            mService.updateAppWidgetIds(mPackageName, appWidgetIds, views);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Update the extras for a given widget instance.
     * <p>
     * The extras can be used to embed additional information about this widget to be accessed
     * by the associated widget's AppWidgetProvider.
     *
     * @see #getAppWidgetOptions(int)
     *
     * @param appWidgetId The AppWidget instances for which to set the RemoteViews.
     * @param options The options to associate with this widget
     */
    public void updateAppWidgetOptions(int appWidgetId, Bundle options) {
        if (mService == null) {
            return;
        }
        try {
            mService.updateAppWidgetOptions(mPackageName, appWidgetId, options);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Get the extras associated with a given widget instance.
     * <p>
     * The extras can be used to embed additional information about this widget to be accessed
     * by the associated widget's AppWidgetProvider.
     *
     * @see #updateAppWidgetOptions(int, Bundle)
     *
     * @param appWidgetId The AppWidget instances for which to set the RemoteViews.
     * @return The options associated with the given widget instance.
     */
    public Bundle getAppWidgetOptions(int appWidgetId) {
        if (mService == null) {
            return Bundle.EMPTY;
        }
        try {
            return mService.getAppWidgetOptions(mPackageName, appWidgetId);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Set the RemoteViews to use for the specified appWidgetId.
     * <p>
     * Note that the RemoteViews parameter will be cached by the AppWidgetService, and hence should
     * contain a complete representation of the widget. For performing partial widget updates, see
     * {@link #partiallyUpdateAppWidget(int, RemoteViews)}.
     *
     * <p>
     * It is okay to call this method both inside an {@link #ACTION_APPWIDGET_UPDATE} broadcast,
     * and outside of the handler.
     * This method will only work when called from the uid that owns the AppWidget provider.
     *
     * <p>
     * The total Bitmap memory used by the RemoteViews object cannot exceed that required to
     * fill the screen 1.5 times, ie. (screen width x screen height x 4 x 1.5) bytes.
     *
     * @param appWidgetId      The AppWidget instance for which to set the RemoteViews.
     * @param views         The RemoteViews object to show.
     */
    public void updateAppWidget(int appWidgetId, RemoteViews views) {
        if (mService == null) {
            return;
        }
        updateAppWidget(new int[] { appWidgetId }, views);
    }

    /**
     * Perform an incremental update or command on the widget(s) specified by appWidgetIds.
     * <p>
     * This update  differs from {@link #updateAppWidget(int[], RemoteViews)} in that the
     * RemoteViews object which is passed is understood to be an incomplete representation of the
     * widget, and hence does not replace the cached representation of the widget. As of API
     * level 17, the new properties set within the views objects will be appended to the cached
     * representation of the widget, and hence will persist.
     *
     * Use with {@link RemoteViews#showNext(int)}, {@link RemoteViews#showPrevious(int)},
     * {@link RemoteViews#setScrollPosition(int, int)} and similar commands.
     *
     * <p>
     * It is okay to call this method both inside an {@link #ACTION_APPWIDGET_UPDATE} broadcast,
     * and outside of the handler.
     * This method will only work when called from the uid that owns the AppWidget provider.
     *
     * <p>
     * This method will be ignored if a widget has not received a full update via
     * {@link #updateAppWidget(int[], RemoteViews)}.
     *
     * @param appWidgetIds     The AppWidget instances for which to set the RemoteViews.
     * @param views            The RemoteViews object containing the incremental update / command.
     */
    public void partiallyUpdateAppWidget(int[] appWidgetIds, RemoteViews views) {
        if (mService == null) {
            return;
        }
        try {
            mService.partiallyUpdateAppWidgetIds(mPackageName, appWidgetIds, views);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Perform an incremental update or command on the widget specified by appWidgetId.
     * <p>
     * This update  differs from {@link #updateAppWidget(int, RemoteViews)} in that the RemoteViews
     * object which is passed is understood to be an incomplete representation of the widget, and
     * hence is not cached by the AppWidgetService. Note that because these updates are not cached,
     * any state that they modify that is not restored by restoreInstanceState will not persist in
     * the case that the widgets are restored using the cached version in AppWidgetService.
     *
     * Use with {@link RemoteViews#showNext(int)}, {@link RemoteViews#showPrevious(int)},
     * {@link RemoteViews#setScrollPosition(int, int)} and similar commands.
     *
     * <p>
     * It is okay to call this method both inside an {@link #ACTION_APPWIDGET_UPDATE} broadcast,
     * and outside of the handler.
     * This method will only work when called from the uid that owns the AppWidget provider.
     *
     * <p>
     * This method will be ignored if a widget has not received a full update via
     * {@link #updateAppWidget(int[], RemoteViews)}.
     *
     * @param appWidgetId      The AppWidget instance for which to set the RemoteViews.
     * @param views            The RemoteViews object containing the incremental update / command.
     */
    public void partiallyUpdateAppWidget(int appWidgetId, RemoteViews views) {
        if (mService == null) {
            return;
        }
        partiallyUpdateAppWidget(new int[] { appWidgetId }, views);
    }

    /**
     * Set the RemoteViews to use for all AppWidget instances for the supplied AppWidget provider.
     *
     * <p>
     * It is okay to call this method both inside an {@link #ACTION_APPWIDGET_UPDATE} broadcast,
     * and outside of the handler.
     * This method will only work when called from the uid that owns the AppWidget provider.
     *
     * @param provider      The {@link ComponentName} for the {@link
     * android.content.BroadcastReceiver BroadcastReceiver} provider
     *                      for your AppWidget.
     * @param views         The RemoteViews object to show.
     */
    public void updateAppWidget(ComponentName provider, RemoteViews views) {
        if (mService == null) {
            return;
        }
        try {
            mService.updateAppWidgetProvider(provider, views);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Notifies the specified collection view in all the specified AppWidget instances
     * to invalidate their data.
     *
     * @param appWidgetIds  The AppWidget instances to notify of view data changes.
     * @param viewId        The collection view id.
     */
    public void notifyAppWidgetViewDataChanged(int[] appWidgetIds, int viewId) {
        if (mService == null) {
            return;
        }
        try {
            mService.notifyAppWidgetViewDataChanged(mPackageName, appWidgetIds, viewId);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Notifies the specified collection view in the specified AppWidget instance
     * to invalidate its data.
     *
     * @param appWidgetId  The AppWidget instance to notify of view data changes.
     * @param viewId       The collection view id.
     */
    public void notifyAppWidgetViewDataChanged(int appWidgetId, int viewId) {
        if (mService == null) {
            return;
        }
        notifyAppWidgetViewDataChanged(new int[] { appWidgetId }, viewId);
    }

    /**
     * Gets the AppWidget providers for the given user profile. User profile can only
     * be the current user or a profile of the current user. For example, the current
     * user may have a corporate profile. In this case the parent user profile has a
     * child profile, the corporate one.
     *
     * @param profile The profile for which to get providers. Passing null is equivaled
     *         to passing only the current user handle.
     * @return The intalled providers.
     *
     * @see android.os.Process#myUserHandle()
     * @see android.os.UserManager#getUserProfiles()
     */
    public List<AppWidgetProviderInfo> getInstalledProvidersForProfile(@Nullable UserHandle profile) {
        if (mService == null) {
            return Collections.emptyList();
        }
        return getInstalledProvidersForProfile(AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN,
                profile);
    }

    /**
     * Return a list of the AppWidget providers that are currently installed.
     */
    public List<AppWidgetProviderInfo> getInstalledProviders() {
        if (mService == null) {
            return Collections.emptyList();
        }
        return getInstalledProvidersForProfile(AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN,
                null);
    }

    /**
     * Gets the AppWidget providers for the current user.
     *
     * @param categoryFilter Will only return providers which register as any of the specified
     *        specified categories. See {@link AppWidgetProviderInfo#widgetCategory}.
     * @return The intalled providers.
     *
     * @see android.os.Process#myUserHandle()
     * @see android.os.UserManager#getUserProfiles()
     *
     * @hide
     */
    public List<AppWidgetProviderInfo> getInstalledProviders(int categoryFilter) {
        if (mService == null) {
            return Collections.emptyList();
        }
        return getInstalledProvidersForProfile(categoryFilter, null);
    }

    /**
     * Gets the AppWidget providers for the given user profile. User profile can only
     * be the current user or a profile of the current user. For example, the current
     * user may have a corporate profile. In this case the parent user profile has a
     * child profile, the corporate one.
     *
     * @param categoryFilter Will only return providers which register as any of the specified
     *        specified categories. See {@link AppWidgetProviderInfo#widgetCategory}.
     * @param profile A profile of the current user which to be queried. The user
     *        is itself also a profile. If null, the providers only for the current user
     *        are returned.
     * @return The intalled providers.
     *
     * @see android.os.Process#myUserHandle()
     * @see android.os.UserManager#getUserProfiles()
     *
     * @hide
     */
    public List<AppWidgetProviderInfo> getInstalledProvidersForProfile(int categoryFilter,
            UserHandle profile) {
        if (mService == null) {
            return Collections.emptyList();
        }

        if (profile == null) {
            profile = Process.myUserHandle();
        }

        try {
            ParceledListSlice<AppWidgetProviderInfo> providers = mService.getInstalledProvidersForProfile(
                    categoryFilter, profile.getIdentifier());
            if (providers == null) {
                return Collections.emptyList();
            }
            for (AppWidgetProviderInfo info : providers.getList()) {
                // Converting complex to dp.
                convertSizesToPixels(info);
            }
            return providers.getList();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Get the available info about the AppWidget.
     *
     * @return A appWidgetId.  If the appWidgetId has not been bound to a provider yet, or
     * you don't have access to that appWidgetId, null is returned.
     */
    public AppWidgetProviderInfo getAppWidgetInfo(int appWidgetId) {
        if (mService == null) {
            return null;
        }
        try {
            AppWidgetProviderInfo info = mService.getAppWidgetInfo(mPackageName, appWidgetId);
            if (info != null) {
                // Converting complex to dp.
                convertSizesToPixels(info);
            }
            return info;
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Set the component for a given appWidgetId.
     *
     * <p class="note">You need the BIND_APPWIDGET permission or the user must have enabled binding
     *         widgets always for your component. This method is used by the AppWidget picker and
     *         should not be used by other apps.
     *
     * @param appWidgetId     The AppWidget instance for which to set the RemoteViews.
     * @param provider      The {@link android.content.BroadcastReceiver} that will be the AppWidget
     *                      provider for this AppWidget.
     * @hide
     */
    public void bindAppWidgetId(int appWidgetId, ComponentName provider) {
        if (mService == null) {
            return;
        }
        bindAppWidgetId(appWidgetId, provider, null);
    }

    /**
     * Set the component for a given appWidgetId.
     *
     * <p class="note">You need the BIND_APPWIDGET permission or the user must have enabled binding
     *         widgets always for your component. This method is used by the AppWidget picker and
     *         should not be used by other apps.
     *
     * @param appWidgetId     The AppWidget instance for which to set the RemoteViews.
     * @param provider      The {@link android.content.BroadcastReceiver} that will be the AppWidget
     *                      provider for this AppWidget.
     * @param options       Bundle containing options for the AppWidget. See also
     *                      {@link #updateAppWidgetOptions(int, Bundle)}
     *
     * @hide
     */
    public void bindAppWidgetId(int appWidgetId, ComponentName provider, Bundle options) {
        if (mService == null) {
            return;
        }
        bindAppWidgetIdIfAllowed(appWidgetId, Process.myUserHandle(), provider, options);
    }

    /**
     * Set the component for a given appWidgetId.
     *
     * <p class="note">You need the BIND_APPWIDGET permission or the user must have enabled binding
     *         widgets always for your component. Should be used by apps that host widgets; if this
     *         method returns false, call {@link #ACTION_APPWIDGET_BIND} to request permission to
     *         bind
     *
     * @param appWidgetId   The AppWidget id under which to bind the provider.
     * @param provider      The {@link android.content.BroadcastReceiver} that will be the AppWidget
     *                      provider for this AppWidget.
     * @return true if this component has permission to bind the AppWidget
     */
    public boolean bindAppWidgetIdIfAllowed(int appWidgetId, ComponentName provider) {
        if (mService == null) {
            return false;
        }
        return bindAppWidgetIdIfAllowed(appWidgetId, UserHandle.myUserId(), provider, null);
    }

    /**
     * Set the component for a given appWidgetId.
     *
     * <p class="note">You need the BIND_APPWIDGET permission or the user must have enabled binding
     *         widgets always for your component. Should be used by apps that host widgets; if this
     *         method returns false, call {@link #ACTION_APPWIDGET_BIND} to request permission to
     *         bind
     *
     * @param appWidgetId The AppWidget id under which to bind the provider.
     * @param provider      The {@link android.content.BroadcastReceiver} that will be the AppWidget
     *                      provider for this AppWidget.
     * @param options       Bundle containing options for the AppWidget. See also
     *                      {@link #updateAppWidgetOptions(int, Bundle)}
     *
     * @return true if this component has permission to bind the AppWidget
     */
    public boolean bindAppWidgetIdIfAllowed(int appWidgetId, ComponentName provider,
            Bundle options) {
        if (mService == null) {
            return false;
        }
        return bindAppWidgetIdIfAllowed(appWidgetId, UserHandle.myUserId(), provider, options);
    }

    /**
     * Set the provider for a given appWidgetId if the caller has a permission.
     * <p>
     * <strong>Note:</strong> You need the {@link android.Manifest.permission#BIND_APPWIDGET}
     * permission or the user must have enabled binding widgets always for your component.
     * Should be used by apps that host widgets. If this method returns false, call {@link
     * #ACTION_APPWIDGET_BIND} to request permission to bind.
     * </p>
     *
     * @param appWidgetId The AppWidget id under which to bind the provider.
     * @param user The user id in which the provider resides.
     * @param provider The component name of the provider.
     * @param options An optional Bundle containing options for the AppWidget.
     *
     * @return true if this component has permission to bind the AppWidget
     */
    public boolean bindAppWidgetIdIfAllowed(int appWidgetId, UserHandle user,
            ComponentName provider, Bundle options) {
        if (mService == null) {
            return false;
        }
        return bindAppWidgetIdIfAllowed(appWidgetId, user.getIdentifier(), provider, options);
    }

    /**
     * Query if a given package was granted permission by the user to bind app widgets
     *
     * <p class="note">You need the MODIFY_APPWIDGET_BIND_PERMISSIONS permission
     *
     * @param packageName The package for which the permission is being queried
     * @param userId The user id of the user under which the package runs.
     * @return true if the package was granted permission by the user to bind app widgets
     * @hide
     */
    public boolean hasBindAppWidgetPermission(String packageName, int userId) {
        if (mService == null) {
            return false;
        }
        try {
            return mService.hasBindAppWidgetPermission(packageName, userId);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Query if a given package was granted permission by the user to bind app widgets
     *
     * <p class="note">You need the MODIFY_APPWIDGET_BIND_PERMISSIONS permission
     *
     * @param packageName        The package for which the permission is being queried
     * @return true if the package was granted permission by the user to bind app widgets
     * @hide
     */
    public boolean hasBindAppWidgetPermission(String packageName) {
        if (mService == null) {
            return false;
        }
        try {
            return mService.hasBindAppWidgetPermission(packageName, UserHandle.myUserId());
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Changes any user-granted permission for the given package to bind app widgets
     *
     * <p class="note">You need the MODIFY_APPWIDGET_BIND_PERMISSIONS permission
     *
     * @param packageName The package whose permission is being changed
     * @param permission Whether to give the package permission to bind widgets
     *
     * @hide
     */
    public void setBindAppWidgetPermission(String packageName, boolean permission) {
        if (mService == null) {
            return;
        }
        setBindAppWidgetPermission(packageName, UserHandle.myUserId(), permission);
    }

    /**
     * Changes any user-granted permission for the given package to bind app widgets
     *
     * <p class="note">You need the MODIFY_APPWIDGET_BIND_PERMISSIONS permission
     *
     * @param packageName The package whose permission is being changed
     * @param userId The user under which the package is running.
     * @param permission Whether to give the package permission to bind widgets
     *
     * @hide
     */
    public void setBindAppWidgetPermission(String packageName, int userId, boolean permission) {
        if (mService == null) {
            return;
        }
        try {
            mService.setBindAppWidgetPermission(packageName, userId, permission);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Binds the RemoteViewsService for a given appWidgetId and intent.
     *
     * The appWidgetId specified must already be bound to the calling AppWidgetHost via
     * {@link android.appwidget.AppWidgetManager#bindAppWidgetId AppWidgetManager.bindAppWidgetId()}.
     *
     * @param packageName   The package from which the binding is requested.
     * @param appWidgetId   The AppWidget instance for which to bind the RemoteViewsService.
     * @param intent        The intent of the service which will be providing the data to the
     *                      RemoteViewsAdapter.
     * @param connection    The callback interface to be notified when a connection is made or lost.
     * @hide
     */
    public void bindRemoteViewsService(String packageName, int appWidgetId, Intent intent,
            IBinder connection) {
        if (mService == null) {
            return;
        }
        try {
            mService.bindRemoteViewsService(packageName, appWidgetId, intent, connection);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Unbinds the RemoteViewsService for a given appWidgetId and intent.
     *
     * The appWidgetId specified muse already be bound to the calling AppWidgetHost via
     * {@link android.appwidget.AppWidgetManager#bindAppWidgetId AppWidgetManager.bindAppWidgetId()}.
     *
     * @param packageName   The package from which the binding is requested.
     * @param appWidgetId   The AppWidget instance for which to bind the RemoteViewsService.
     * @param intent        The intent of the service which will be providing the data to the
     *                      RemoteViewsAdapter.
     * @hide
     */
    public void unbindRemoteViewsService(String packageName, int appWidgetId, Intent intent) {
        if (mService == null) {
            return;
        }
        try {
            mService.unbindRemoteViewsService(packageName, appWidgetId, intent);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Get the list of appWidgetIds that have been bound to the given AppWidget
     * provider.
     *
     * @param provider The {@link android.content.BroadcastReceiver} that is the
     *            AppWidget provider to find appWidgetIds for.
     */
    public int[] getAppWidgetIds(ComponentName provider) {
        if (mService == null) {
            return new int[0];
        }
        try {
            return mService.getAppWidgetIds(provider);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * @hide
     */
    public boolean isBoundWidgetPackage(String packageName, int userId) {
        if (mService == null) {
            return false;
        }
        try {
            return mService.isBoundWidgetPackage(packageName, userId);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    private boolean bindAppWidgetIdIfAllowed(int appWidgetId, int profileId,
            ComponentName provider, Bundle options) {
        if (mService == null) {
            return false;
        }
        try {
            return mService.bindAppWidgetId(mPackageName, appWidgetId,
                    profileId, provider, options);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    private void convertSizesToPixels(AppWidgetProviderInfo info) {
        // Converting complex to dp.
        info.minWidth = TypedValue.complexToDimensionPixelSize(info.minWidth,
                mDisplayMetrics);
        info.minHeight = TypedValue.complexToDimensionPixelSize(info.minHeight,
                mDisplayMetrics);
        info.minResizeWidth = TypedValue.complexToDimensionPixelSize(info.minResizeWidth,
                mDisplayMetrics);
        info.minResizeHeight = TypedValue.complexToDimensionPixelSize(info.minResizeHeight,
                mDisplayMetrics);
    }

    /**
     * @hide
     */
    public List<ComponentName> getAppWidgetOfHost(String pkg, int uid) {
        if (mService == null || !isRunningBoosterSupport()) {
            return Collections.emptyList();
        }
        try {
            return mService.getAppWidgetOfHost(pkg, uid);
        }
        catch (RemoteException e) {
            throw new RuntimeException("system server dead?", e);
        }
    }

    private boolean isRunningBoosterSupport() {
        return SystemProperties.get("persist.runningbooster.support").equals("1");
    }
    /**
     * @hide
     *
     * SysResMonitor apk: get widgets that are showing in launcher.  prize-linkh-20180112 @{
     */
     public List<ComponentName> getEnabledWidgets() {
        if (mService == null) {
            return Collections.emptyList();
        }
        
        try {
            return mService.getEnabledWidgets();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
     }  // @}
}
