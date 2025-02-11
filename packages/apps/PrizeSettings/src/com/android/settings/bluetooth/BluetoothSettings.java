/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.settings.bluetooth;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v7.preference.Preference;
import android.support.v7.preference.Preference.OnPreferenceClickListener;
import android.support.v7.preference.PreferenceCategory;
import android.support.v7.preference.PreferenceGroup;
import android.support.v7.preference.PreferenceScreen;
import android.text.BidiFormatter;
import android.text.Spannable;
import android.text.style.TextAppearanceSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.MetricsProto.MetricsEvent;
import com.android.settings.LinkifyUtils;
import com.android.settings.R;
import com.android.settings.SettingsActivity;
import com.android.settings.dashboard.SummaryLoader;
import com.android.settings.location.ScanningSettings;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.search.Indexable;
import com.android.settings.search.SearchIndexableRaw;
import com.android.settings.widget.SwitchBar;
import com.android.settingslib.bluetooth.BluetoothCallback;
import com.android.settingslib.bluetooth.BluetoothDeviceFilter;
import com.android.settingslib.bluetooth.CachedBluetoothDevice;
import com.android.settingslib.bluetooth.LocalBluetoothManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static android.os.UserManager.DISALLOW_CONFIG_BLUETOOTH;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.database.ContentObserver;
import android.os.Handler;
/**
 * BluetoothSettings is the Settings screen for Bluetooth configuration and
 * connection management.
 */
public final class BluetoothSettings extends DeviceListPreferenceFragment implements Indexable {
    private static final String TAG = "BluetoothSettings";

    private static final int MENU_ID_SCAN = Menu.FIRST;
    private static final int MENU_ID_RENAME_DEVICE = Menu.FIRST + 1;
    private static final int MENU_ID_SHOW_RECEIVED = Menu.FIRST + 2;

    /* Private intent to show the list of received files */
    private static final String BTOPP_ACTION_OPEN_RECEIVED_FILES =
            "android.btopp.intent.action.OPEN_RECEIVED_FILES";

    private static final String KEY_PAIRED_DEVICES = "paired_devices";
    
    private static final String KEY_LOCAL_DEVICES = "local_devices";

    private static View mSettingsDialogView = null;

    private BluetoothEnabler mBluetoothEnabler;

    private PrizeDeviceNamePreference mDeviceNamePreference;
    private PreferenceGroup mPairedDevicesCategory;
    private PreferenceGroup mAvailableDevicesCategory;
    private boolean mAvailableDevicesCategoryIsPresent;

    private boolean mInitialScanStarted;
    private boolean mInitiateDiscoverable;

    private SwitchBar mSwitchBar;
    
    /* Modify by zhudaopeng at 2016-11-23 Start */
    private ViewGroup mRefreshGroup;
    private ImageView mImageView;
    private LinearLayout refreshLinearLayout;
    /* Modify by zhudaopeng at 2016-11-23 End */

    private final IntentFilter mIntentFilter;


    // accessed from inner class (not private to avoid thunks)
    Preference mMyDevicePreference;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            final int state =
                intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);

            if (action.equals(BluetoothAdapter.ACTION_LOCAL_NAME_CHANGED)) {
                updateDeviceName(context);
            }

            if (state == BluetoothAdapter.STATE_ON) {
                mInitiateDiscoverable = true;
            }
        }

        private void updateDeviceName(Context context) {
            if (mLocalAdapter.isEnabled() && mMyDevicePreference != null) {
                final Resources res = context.getResources();
                final Locale locale = res.getConfiguration().getLocales().get(0);
                final BidiFormatter bidiFormatter = BidiFormatter.getInstance(locale);
                mMyDevicePreference.setSummary(res.getString(
                            R.string.bluetooth_is_visible_message,
                            bidiFormatter.unicodeWrap(mLocalAdapter.getName())));
                mDeviceNamePreference.setDeviceName(mLocalAdapter.getName());
            }
        }
    };
    
    /* Modify by zhudaopeng at 2016-11-23 Start */
    private final View.OnClickListener refreshViewListener = new View.OnClickListener() {
    	@Override
    	public void onClick(View arg0) {
    		// TODO Auto-generated method stub
    		Log.d(TAG, "RefreshView Click");
    		if (mLocalAdapter.getBluetoothState() == BluetoothAdapter.STATE_ON) {
                MetricsLogger.action(getActivity(), MetricsEvent.ACTION_BLUETOOTH_SCAN);
                startScanning();
            }
    	}
    };
    /* Modify by zhudaopeng at 2016-11-23 End */

    public BluetoothSettings() {
        super(DISALLOW_CONFIG_BLUETOOTH);
        mIntentFilter = new IntentFilter(BluetoothAdapter.ACTION_LOCAL_NAME_CHANGED);
    }

    @Override
    protected int getMetricsCategory() {
        return MetricsEvent.BLUETOOTH;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mInitialScanStarted = false;
        mInitiateDiscoverable = true;

        // final SettingsActivity activity = (SettingsActivity) getActivity();
        // mSwitchBar = activity.getSwitchBar();
        
        // mBluetoothEnabler = new BluetoothEnabler(activity, mSwitchBar);
        // mBluetoothEnabler.setupSwitchBar();
    }
	
		
	@Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        final View root = super.onCreateView(inflater, container, savedInstanceState);
        
        RecyclerView listView = getListView();
		listView.setNestedScrollingEnabled(false);
        mSwitchBar = (SwitchBar) root.findViewById(R.id.prize_switch_bar);
		
        return root;
    }
	
	
    @Override
    public void onStart() {
        super.onStart();
		final SettingsActivity activity = (SettingsActivity) getActivity();
		if(mSwitchBar != null){
			mBluetoothEnabler = new BluetoothEnabler(activity, mSwitchBar);
			mBluetoothEnabler.setupSwitchBar();
		}
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        mBluetoothEnabler.teardownSwitchBar();
    }

    @Override
    void addPreferencesForActivity() {
        addPreferencesFromResource(R.xml.bluetooth_settings);
        
        /* Modify by zhudaopeng at 2016-11-23 Start */
        mDeviceNamePreference = new PrizeDeviceNamePreference(getPrefContext());
        mDeviceNamePreference.setTitle(R.string.phone_name);
        mDeviceNamePreference.setDeviceName(mLocalAdapter.getName());
        mDeviceNamePreference.setKey(KEY_LOCAL_DEVICES);
        mDeviceNamePreference.setOrder(0);
        getPreferenceScreen().addPreference(mDeviceNamePreference);
        /* Modify by zhudaopeng at 2016-11-23 End */
        
        mPairedDevicesCategory = new PreferenceCategory(getPrefContext());
        mPairedDevicesCategory.setKey(KEY_PAIRED_DEVICES);
        mPairedDevicesCategory.setOrder(1);
        getPreferenceScreen().addPreference(mPairedDevicesCategory);

        mAvailableDevicesCategory = new BluetoothProgressCategory(getActivity());
        mAvailableDevicesCategory.setSelectable(false);
        mAvailableDevicesCategory.setOrder(2);
        getPreferenceScreen().addPreference(mAvailableDevicesCategory);

        mMyDevicePreference = new Preference(getPrefContext());
        mMyDevicePreference.setSelectable(false);
        mMyDevicePreference.setWidgetLayoutResource(0);
        mMyDevicePreference.setOrder(3);
        getPreferenceScreen().addPreference(mMyDevicePreference);

        setHasOptionsMenu(true);
    }
    
    /* Modify by zhudaopeng at 2016-11-23 Start */
    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
    	super.onViewCreated(view, savedInstanceState);
    	mRefreshGroup = (ViewGroup)view.findViewById(R.id.refresh_ll);
    	// mRefreshGroup.setVisibility(View.VISIBLE);
    	
    	mImageView = (ImageView) mRefreshGroup.findViewById(R.id.bluetooth_refresh);
    	refreshLinearLayout = (LinearLayout)mRefreshGroup.findViewById(R.id.go_refresh);
    	mRefreshGroup.setOnClickListener(refreshViewListener);
    }
    /* Modify by zhudaopeng at 2016-11-23 End */
    
    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
    	Log.d(TAG, "onPreferenceTreeClick()");
    	if (KEY_LOCAL_DEVICES.equals(preference.getKey())) {
    		MetricsLogger.action(getActivity(), MetricsEvent.ACTION_BLUETOOTH_RENAME);
            new BluetoothNameDialogFragment().show(
                    getFragmentManager(), "rename device");
            return true;
    	}
    	return super.onPreferenceTreeClick(preference);
    }

    @Override
    public void onResume() {
        // resume BluetoothEnabler before calling super.onResume() so we don't get
        // any onDeviceAdded() callbacks before setting up view in updateContent()
        if (mBluetoothEnabler != null) {
            mBluetoothEnabler.resume(getActivity());
        }
        super.onResume();

        mInitiateDiscoverable = true;

        if (isUiRestricted()) {
            setDeviceListGroup(getPreferenceScreen());
            if (!isUiRestrictedByOnlyAdmin()) {
                getEmptyTextView().setText(R.string.bluetooth_empty_list_user_restricted);
            }
            removeAllDevices();
            return;
        }

        getActivity().registerReceiver(mReceiver, mIntentFilter);
        if (mLocalAdapter != null) {
            updateContent(mLocalAdapter.getBluetoothState());
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mBluetoothEnabler != null) {
            mBluetoothEnabler.pause();
        }

        // Make the device only visible to connected devices.
        mLocalAdapter.setScanMode(BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE);

        if (isUiRestricted()) {
            return;
        }

        getActivity().unregisterReceiver(mReceiver);
    }

    public void onDestroy() {
        super.onDestroy();

        if (mPairedDevicesCategory != null) {
            mPairedDevicesCategory.removeAll();
        }
        if (mAvailableDevicesCategory != null) {
            mAvailableDevicesCategory.removeAll();
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        if (mLocalAdapter == null) return;
        // If the user is not allowed to configure bluetooth, do not show the menu.
        if (isUiRestricted()) return;

        boolean bluetoothIsEnabled = mLocalAdapter.getBluetoothState() == BluetoothAdapter.STATE_ON;
        boolean isDiscovering = mLocalAdapter.isDiscovering();
        Log.d(TAG, "onCreateOptionsMenu, isDiscovering " + isDiscovering);
        int textId = isDiscovering ? R.string.bluetooth_searching_for_devices :
            R.string.bluetooth_search_for_devices;
        menu.add(Menu.NONE, MENU_ID_SCAN, 0, textId)
                .setEnabled(bluetoothIsEnabled && !isDiscovering)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        /* Modify by zhudaopeng at 2016-11-23 Start */
        // menu.add(Menu.NONE, MENU_ID_RENAME_DEVICE, 0, R.string.bluetooth_rename_device)
        //        .setEnabled(bluetoothIsEnabled)
        //        .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        /* Modify by zhudaopeng at 2016-11-23 End */
        menu.add(Menu.NONE, MENU_ID_SHOW_RECEIVED, 0, R.string.bluetooth_show_received_files)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_ID_SCAN:
                if (mLocalAdapter.getBluetoothState() == BluetoothAdapter.STATE_ON) {
                    MetricsLogger.action(getActivity(), MetricsEvent.ACTION_BLUETOOTH_SCAN);
                    startScanning();
                }
                return true;

            case MENU_ID_RENAME_DEVICE:
                MetricsLogger.action(getActivity(), MetricsEvent.ACTION_BLUETOOTH_RENAME);
                new BluetoothNameDialogFragment().show(
                        getFragmentManager(), "rename device");
                return true;

            case MENU_ID_SHOW_RECEIVED:
                MetricsLogger.action(getActivity(), MetricsEvent.ACTION_BLUETOOTH_FILES);
                /* prize-modify-by-lijimeng-for bugid 54173-20180330-start*/
//                Intent intent = new Intent(BTOPP_ACTION_OPEN_RECEIVED_FILES);
//                getActivity().sendBroadcast(intent);
                Intent in = new Intent();
                ComponentName componentName = new ComponentName("com.android.bluetooth","com.android.bluetooth.opp.BluetoothOppTransferHistory");
                in.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                in.setComponent(componentName);
                in.putExtra("direction", 1);
                in.putExtra("android.btopp.intent.extra.SHOW_ALL", true);
                try {
                    getActivity().startActivity(in);
                }catch (ActivityNotFoundException e){
                    Log.d(TAG,"ActivityNotFoundException:"+e.toString());
                }
                /* prize-modify-by-lijimeng-for bugid 54173-20180330-end*/
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void startScanning() {
        if (isUiRestricted()) {
            return;
        }

        if (!mAvailableDevicesCategoryIsPresent) {
            getPreferenceScreen().addPreference(mAvailableDevicesCategory);
            mAvailableDevicesCategoryIsPresent = true;
        }

        if (mAvailableDevicesCategory != null) {
            setDeviceListGroup(mAvailableDevicesCategory);
            removeAllDevices();
        }

        mLocalManager.getCachedDeviceManager().clearNonBondedDevices();
        mAvailableDevicesCategory.removeAll();
        mInitialScanStarted = true;
        mLocalAdapter.startScanning(true);
    }

    @Override
    void onDevicePreferenceClick(BluetoothDevicePreference btPreference) {
        mLocalAdapter.stopScanning();
        super.onDevicePreferenceClick(btPreference);
    }

    private void addDeviceCategory(PreferenceGroup preferenceGroup, int titleId,
            BluetoothDeviceFilter.Filter filter, boolean addCachedDevices) {
        cacheRemoveAllPrefs(preferenceGroup);
        preferenceGroup.setTitle(titleId);
        setFilter(filter);
        setDeviceListGroup(preferenceGroup);
        if (addCachedDevices) {
            addCachedDevices();
        }
        preferenceGroup.setEnabled(true);
        removeCachedPrefs(preferenceGroup);
    }

    private void updateContent(int bluetoothState) {
        final PreferenceScreen preferenceScreen = getPreferenceScreen();
        int messageId = 0;

        switch (bluetoothState) {
            case BluetoothAdapter.STATE_ON:
            	mImageView.setEnabled(true);
            	refreshLinearLayout.setEnabled(true);
            	
                mDevicePreferenceMap.clear();

                if (isUiRestricted()) {
                    messageId = R.string.bluetooth_empty_list_user_restricted;
                    break;
                }
                getPreferenceScreen().removeAll();
                /* Modify by zhudaopeng at 2016-11-23 Start */
                getPreferenceScreen().addPreference(mDeviceNamePreference);
                /* Modify by zhudaopeng at 2016-11-23 End */
                getPreferenceScreen().addPreference(mPairedDevicesCategory);
                getPreferenceScreen().addPreference(mAvailableDevicesCategory);
                getPreferenceScreen().addPreference(mMyDevicePreference);

                // Paired devices category
                addDeviceCategory(mPairedDevicesCategory,
                        R.string.bluetooth_preference_paired_devices,
                        BluetoothDeviceFilter.BONDED_DEVICE_FILTER, true);
                int numberOfPairedDevices = mPairedDevicesCategory.getPreferenceCount();

                if (isUiRestricted() || numberOfPairedDevices <= 0) {
                    if (preferenceScreen.findPreference(KEY_PAIRED_DEVICES) != null) {
                        preferenceScreen.removePreference(mPairedDevicesCategory);
                    }
                } else {
                    if (preferenceScreen.findPreference(KEY_PAIRED_DEVICES) == null) {
                        preferenceScreen.addPreference(mPairedDevicesCategory);
                    }
                }

                // Available devices category
                addDeviceCategory(mAvailableDevicesCategory,
                        R.string.bluetooth_preference_found_devices,
                        BluetoothDeviceFilter.UNBONDED_DEVICE_FILTER, mInitialScanStarted);

                if (!mInitialScanStarted) {
                    startScanning();
                }

                final Resources res = getResources();
                final Locale locale = res.getConfiguration().getLocales().get(0);
                final BidiFormatter bidiFormatter = BidiFormatter.getInstance(locale);
                mMyDevicePreference.setSummary(res.getString(
                           R.string.bluetooth_is_visible_message,
                           bidiFormatter.unicodeWrap(mLocalAdapter.getName())));

                getActivity().invalidateOptionsMenu();

                // mLocalAdapter.setScanMode is internally synchronized so it is okay for multiple
                // threads to execute.
                if (mInitiateDiscoverable) {
                    // Make the device visible to other devices.
                    mLocalAdapter.setScanMode(BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE);
                    mInitiateDiscoverable = false;
                }
                return; // not break

            case BluetoothAdapter.STATE_TURNING_OFF:
                messageId = R.string.bluetooth_turning_off;
                break;

            case BluetoothAdapter.STATE_OFF:
            	mImageView.setEnabled(false);
            	refreshLinearLayout.setEnabled(false);
                setOffMessage();
                if (isUiRestricted()) {
                    messageId = R.string.bluetooth_empty_list_user_restricted;
                }
                break;

            case BluetoothAdapter.STATE_TURNING_ON:
                messageId = R.string.bluetooth_turning_on;
                mInitialScanStarted = false;
                break;
        }

        setDeviceListGroup(preferenceScreen);
        removeAllDevices();
        if (messageId != 0) {
            getEmptyTextView().setText(messageId);
        }
        if (!isUiRestricted()) {
            getActivity().invalidateOptionsMenu();
        }
    }

    private void setOffMessage() {
        final TextView emptyView = getEmptyTextView();
        if (emptyView == null) {
            return;
        }
        final CharSequence briefText = getText(R.string.bluetooth_empty_list_bluetooth_off);
        final ContentResolver resolver = getActivity().getContentResolver();
        final boolean bleScanningMode = Settings.Global.getInt(
                resolver, Settings.Global.BLE_SCAN_ALWAYS_AVAILABLE, 0) == 1;

        if (!bleScanningMode) {
            // Show only the brief text if the scanning mode has been turned off.
            emptyView.setText(briefText, TextView.BufferType.SPANNABLE);
        } else {
            final StringBuilder contentBuilder = new StringBuilder();
            contentBuilder.append(briefText);
            contentBuilder.append("\n\n");
            contentBuilder.append(getText(R.string.ble_scan_notify_text));
            LinkifyUtils.linkify(emptyView, contentBuilder, new LinkifyUtils.OnClickListener() {
                @Override
                public void onClick() {
                    final SettingsActivity activity =
                            (SettingsActivity) BluetoothSettings.this.getActivity();
                    activity.startPreferencePanel(ScanningSettings.class.getName(), null,
                            R.string.location_scanning_screen_title, null, null, 0);
                }
            });
        }
        /// M: getPreferenceScreen().removeAll() will not remove preference in category @{
        if(mAvailableDevicesCategory != null) {
            mAvailableDevicesCategory.removeAll();
        }
        /// @}
        getPreferenceScreen().removeAll();
        Spannable boldSpan = (Spannable) emptyView.getText();
        boldSpan.setSpan(
                new TextAppearanceSpan(getActivity(), R.style.prize_empty_text_style), 0,
                briefText.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    @Override
    public void onBluetoothStateChanged(int bluetoothState) {
        super.onBluetoothStateChanged(bluetoothState);
        // If BT is turned off/on staying in the same BT Settings screen
        // discoverability to be set again
        if (BluetoothAdapter.STATE_ON == bluetoothState)
            mInitiateDiscoverable = true;
        updateContent(bluetoothState);
    }

    @Override
    public void onScanningStateChanged(boolean started) {
        Log.d(TAG, "onScanningStateChanged() started : " + started);
        super.onScanningStateChanged(started);
        // Update options' enabled state
        if (getActivity() != null) {
            getActivity().invalidateOptionsMenu();
        }
    }

    @Override
    public void onDeviceBondStateChanged(CachedBluetoothDevice cachedDevice, int bondState) {
        setDeviceListGroup(getPreferenceScreen());
        removeAllDevices();
        updateContent(mLocalAdapter.getBluetoothState());
    }

    private final View.OnClickListener mDeviceProfilesListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            // User clicked on advanced options icon for a device in the list
            if (!(v.getTag() instanceof CachedBluetoothDevice)) {
                Log.w(TAG, "onClick() called for other View: " + v);
                return;
            }

            final CachedBluetoothDevice device = (CachedBluetoothDevice) v.getTag();
            Log.d(TAG, "onClick " + device.getName());
            Bundle args = new Bundle();
            args.putString(DeviceProfilesSettings.ARG_DEVICE_ADDRESS,
                    device.getDevice().getAddress());
            DeviceProfilesSettings profileSettings = new DeviceProfilesSettings();
            profileSettings.setArguments(args);
            profileSettings.show(getFragmentManager(),
                    DeviceProfilesSettings.class.getSimpleName());
        }
    };

    /**
     * Add a listener, which enables the advanced settings icon.
     * @param preference the newly added preference
     */
    @Override
    void initDevicePreference(BluetoothDevicePreference preference) {
        CachedBluetoothDevice cachedDevice = preference.getCachedDevice();
        if (cachedDevice.getBondState() == BluetoothDevice.BOND_BONDED) {
            // Only paired device have an associated advanced settings screen
            preference.setOnSettingsClickListener(mDeviceProfilesListener);
        }
    }

    @Override
    protected int getHelpResource() {
        return R.string.help_url_bluetooth;
    }

    private static class SummaryProvider
            implements SummaryLoader.SummaryProvider, BluetoothCallback {

        private final LocalBluetoothManager mBluetoothManager;
        private final Context mContext;
        private final SummaryLoader mSummaryLoader;

        private boolean mEnabled;
        private boolean mConnected;

        public SummaryProvider(Context context, SummaryLoader summaryLoader) {
            mBluetoothManager = Utils.getLocalBtManager(context);
            mContext = context;
            mSummaryLoader = summaryLoader;
        }

        @Override
        public void setListening(boolean listening) {
            BluetoothAdapter defaultAdapter = BluetoothAdapter.getDefaultAdapter();
            if (defaultAdapter == null) return;
            if (listening) {
                mEnabled = defaultAdapter.isEnabled();
                mConnected =
                        defaultAdapter.getConnectionState() == BluetoothAdapter.STATE_CONNECTED;
                mSummaryLoader.setSummary(this, getSummary());
                mBluetoothManager.getEventManager().registerCallback(this);
            } else {
                mBluetoothManager.getEventManager().unregisterCallback(this);
            }
        }

        private CharSequence getSummary() {
            return mContext.getString(!mEnabled ? R.string.bluetooth_disabled
                    : mConnected ? R.string.bluetooth_connected
                    : R.string.bluetooth_disconnected);
        }

        @Override
        public void onBluetoothStateChanged(int bluetoothState) {
            mEnabled = bluetoothState == BluetoothAdapter.STATE_ON;
            mSummaryLoader.setSummary(this, getSummary());
        }

        @Override
        public void onConnectionStateChanged(CachedBluetoothDevice cachedDevice, int state) {
            mConnected = state == BluetoothAdapter.STATE_CONNECTED;
            mSummaryLoader.setSummary(this, getSummary());
        }

        @Override
        public void onScanningStateChanged(boolean started) {

        }

        @Override
        public void onDeviceAdded(CachedBluetoothDevice cachedDevice) {

        }

        @Override
        public void onDeviceDeleted(CachedBluetoothDevice cachedDevice) {

        }

        @Override
        public void onDeviceBondStateChanged(CachedBluetoothDevice cachedDevice, int bondState) {

        }
    }

    public static final SummaryLoader.SummaryProviderFactory SUMMARY_PROVIDER_FACTORY
            = new SummaryLoader.SummaryProviderFactory() {
        @Override
        public SummaryLoader.SummaryProvider createSummaryProvider(Activity activity,
                                                                   SummaryLoader summaryLoader) {
            return new SummaryProvider(activity, summaryLoader);
        }
    };

    public static final SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
        new BaseSearchIndexProvider() {
            @Override
            public List<SearchIndexableRaw> getRawDataToIndex(Context context, boolean enabled) {

                final List<SearchIndexableRaw> result = new ArrayList<SearchIndexableRaw>();

                final Resources res = context.getResources();

                // Add fragment title
                SearchIndexableRaw data = new SearchIndexableRaw(context);
                data.title = res.getString(R.string.bluetooth_settings);
                data.screenTitle = res.getString(R.string.bluetooth_settings);
                result.add(data);

                // Add cached paired BT devices
                LocalBluetoothManager lbtm = Utils.getLocalBtManager(context);
                // LocalBluetoothManager.getInstance can return null if the device does not
                // support bluetooth (e.g. the emulator).
                if (lbtm != null) {
                    Set<BluetoothDevice> bondedDevices =
                            lbtm.getBluetoothAdapter().getBondedDevices();

                    for (BluetoothDevice device : bondedDevices) {
                        data = new SearchIndexableRaw(context);
                        data.title = device.getName();
                        data.screenTitle = res.getString(R.string.bluetooth_settings);
                        data.enabled = enabled;
                        result.add(data);
                    }
                }
                return result;
            }
        };
	 @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
		activity.setTheme(R.style.WifiTheme);
    }
	
	private ContentObserver mAirplaneMode = new ContentObserver(new Handler()) {
       @Override
       public void onChange(boolean selfChange) {
           final boolean enabled = Settings.Global.getInt(getActivity().getContentResolver(),Settings.Global.AIRPLANE_MODE_ON, 0) == 1;
		   if(mSwitchBar != null){
			   if(mSwitchBar.isChecked() && enabled){
				   mSwitchBar.setChecked(false);
			   }
			   mSwitchBar.setEnabled(!enabled);
		   }
       }
    };
}
