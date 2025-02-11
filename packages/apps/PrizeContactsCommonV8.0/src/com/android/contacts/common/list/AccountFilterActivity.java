/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
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

package com.android.contacts.common.list;

import android.app.ActionBar;
import android.app.Activity;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.AsyncTaskLoader;
import android.content.Context;
import android.content.Intent;
import android.content.Loader;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
//import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ListView;

import com.android.contacts.common.R;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.account.AccountType;
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.internal.telephony.TelephonyIntents;
import com.google.common.collect.Lists;

import android.widget.ProgressBar;
import android.widget.TextView;

import com.mediatek.contacts.eventhandler.BaseEventHandlerActivity;
import com.mediatek.contacts.eventhandler.GeneralEventHandler;
import com.mediatek.contacts.util.ContactsCommonListUtils;
import com.mediatek.contacts.util.Log;
import com.mediatek.contacts.widget.WaitCursorView;
import java.util.ArrayList;
import java.util.List;

/**
 * Shows a list of all available accounts, letting the user select under which account to view
 * contacts.
 */
public class AccountFilterActivity extends BaseEventHandlerActivity
        implements AdapterView.OnItemClickListener {

    private static final String TAG = AccountFilterActivity.class.getSimpleName();

    private static final int SUBACTIVITY_CUSTOMIZE_FILTER = 0;

    public static final String KEY_EXTRA_CONTACT_LIST_FILTER = "contactListFilter";
    public static final String KEY_EXTRA_CURRENT_FILTER = "currentFilter";

    private static final int FILTER_LOADER_ID = 0;

    private ListView mListView;

    private ContactListFilter mCurrentFilter;

    private ContactListFilterView mCustomFilterView; // the "Customize" filter

    private boolean mIsCustomFilterViewSelected;
    private FilterListAdapter mFilterListAdapter;//prize-add for dido os 8.0 -hpf-2017-8-31

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        /** M: Bug Fix:ALPS00115673 Descriptions: add wait cursor */
        View listLayout = getLayoutInflater().inflate(R.layout.contact_list_filter, null);
        setContentView(listLayout);

        mListView = (ListView) findViewById(android.R.id.list);
        mListView.setOnItemClickListener(this);

        /** M: Bug Fix:ALPS00115673 Descriptions: add wait cursor */
        mWaitCursorView = ContactsCommonListUtils.initLoadingView(this, listLayout,
                mLoadingContainer, mLoadingContact, mProgress);

        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
        	/*prize-change for dido os 8.0 -hpf-2017-7-29-start*/
            //actionBar.setDisplayHomeAsUpEnabled(true);
        	actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM);
        	actionBar.setDisplayShowCustomEnabled(false);
        	actionBar.setDisplayHomeAsUpEnabled(true);
        	actionBar.setDisplayShowTitleEnabled(true);
        	actionBar.setDisplayUseLogoEnabled(false);
        	//actionBar.setCustomView(R.layout.prize_custom_center_actionbar_2016_7_21);
        	//TextView TitleText = (TextView)actionBar.getCustomView().findViewById(R.id.title);
        	actionBar.setTitle(R.string.menu_contacts_filter);
        	//ImageButton BackButton = (ImageButton)actionBar.getCustomView().findViewById(R.id.back_button);
        	/*BackButton.setOnClickListener(new OnClickListener() {
				
				@Override
				public void onClick(View v) {
					// TODO Auto-generated method stub
					onBackPressed();
				}
			});*/
        	/*prize-change for dido os 8.0 -hpf-2017-7-29-end*/
        }

        mCurrentFilter = getIntent().getParcelableExtra(KEY_EXTRA_CURRENT_FILTER);

        getLoaderManager().initLoader(FILTER_LOADER_ID, null, new MyLoaderCallbacks());
    }

    private static class FilterLoader extends AsyncTaskLoader<List<ContactListFilter>> {
        private Context mContext;

        public FilterLoader(Context context) {
            super(context);
            mContext = context;
        }

        @Override
        public List<ContactListFilter> loadInBackground() {
            return loadAccountFilters(mContext);
        }

        @Override
        protected void onStartLoading() {
            forceLoad();
        }

        @Override
        protected void onStopLoading() {
            cancelLoad();
        }

        @Override
        protected void onReset() {
            onStopLoading();
        }
    }

    private static List<ContactListFilter> loadAccountFilters(Context context) {
        final ArrayList<ContactListFilter> result = Lists.newArrayList();
        final ArrayList<ContactListFilter> accountFilters = Lists.newArrayList();
        final AccountTypeManager accountTypes = AccountTypeManager.getInstance(context);
        List<AccountWithDataSet> accounts = accountTypes.getAccounts(false);

        for (AccountWithDataSet account : accounts) {
            AccountType accountType = accountTypes.getAccountType(account.type, account.dataSet);
            if (accountType != null && accountType.isExtension() && !account.hasData(context)) {
                // Hide extensions with no raw_contacts.
                continue;
            }
            /// M: For MTK multiuser in 3gdatasms @{
            if (!ContactsCommonListUtils.isUserOwner() && accountType != null
                && accountType.isIccCardAccount()) {
                continue;
            }
            /// @}

            /// M: Change feature for ALPS00233786. @{
            if (accountType != null && accountType.isIccCardAccount()) {
                /// M: ALPS913966 cache displayname in account filter and  push to intent.
                ContactsCommonListUtils.addToAccountFilter(context, account, accountFilters,
                        accountType);
            } else {
                Drawable icon = accountType != null ? accountType.getDisplayIcon(context) : null;
                accountFilters.add(ContactListFilter.createAccountFilter(
                        account.type, account.name, account.dataSet, icon, null));
            }
            /// @}
        }

        // Always show "All", even when there's no accounts.  (We may have local contacts)
        result.add(ContactListFilter.createFilterWithType(
                ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS));

        final int count = accountFilters.size();
        if (count >= 1) {
            // If we only have one account, don't show it as "account", instead show it as "all"
            if (count > 1) {
                result.addAll(accountFilters);
            }
            result.add(ContactListFilter.createFilterWithType(
                    ContactListFilter.FILTER_TYPE_CUSTOM));
        }
        return result;
    }

    private class MyLoaderCallbacks implements LoaderCallbacks<List<ContactListFilter>> {
        @Override
        public Loader<List<ContactListFilter>> onCreateLoader(int id, Bundle args) {

            /** M: Bug Fix ALPS00115673 Descriptions: add wait cursor @{ */
            Log.d(TAG, "onCreateLoader");
            if (mLoadingContainer != null) {
                mLoadingContainer.setVisibility(View.GONE);
            }
            if (mWaitCursorView != null) {
                mWaitCursorView.startWaitCursor();
            }
            /** @} */

            return new FilterLoader(AccountFilterActivity.this);
        }

        @Override
        public void onLoadFinished(
                Loader<List<ContactListFilter>> loader, List<ContactListFilter> data) {
            /// M:check whether the Activity's status still ok @{
            if (isActivityFinished()) {
                Log.w(TAG, "[onLoadFinished],This Activity is finishing.");
                return;
            }
            ///@}

            Log.d(TAG, "[onLoadFinished]");
            /** M: Bug Fix ALPS00115673 Descriptions: add wait cursor */
            mWaitCursorView.stopWaitCursor();

            if (data == null) { // Just in case...
                Log.d(TAG, "Failed to load filters");
                return;
            }
            /*prize-change for dido os 8.0 -hpf-2017-8-31-start*/
            mFilterListAdapter = new FilterListAdapter(AccountFilterActivity.this, data, mCurrentFilter);
            mListView.setAdapter(mFilterListAdapter);
            /*prize-change for dido os 8.0 -hpf-2017-8-31-end*/
        }

        @Override
        public void onLoaderReset(Loader<List<ContactListFilter>> loader) {
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        final ContactListFilterView listFilterView = (ContactListFilterView) view;
        final ContactListFilter filter = (ContactListFilter) view.getTag();
        if (filter == null) return; // Just in case
        /*prize-add for dido os 8.0 -hpf-2017-8-31-start*/
        for(int i = 0; i < mFilterListAdapter.getCount(); i++){
        	((ContactListFilterView)mListView.getChildAt(i)).setActivated(false);
        }
        /*prize-add for dido os 8.0 -hpf-2017-8-31-end*/
        if (filter.filterType == ContactListFilter.FILTER_TYPE_CUSTOM) {
            mCustomFilterView = listFilterView;
            mIsCustomFilterViewSelected = listFilterView.isChecked();
            final Intent intent = new Intent(this,
                    CustomContactListFilterActivity.class);
            listFilterView.setActivated(true);
            // Switching activity has the highest priority. So when we open another activity, the
            // announcement that indicates an account is checked will be interrupted. This is the
            // way to overcome -- View.announceForAccessibility(CharSequence text);
            listFilterView.announceForAccessibility(listFilterView.generateContentDescription());
            startActivityForResult(intent, SUBACTIVITY_CUSTOMIZE_FILTER);
        } else {
            listFilterView.setActivated(true);
            listFilterView.announceForAccessibility(listFilterView.generateContentDescription());
            final Intent intent = new Intent();
            intent.putExtra(KEY_EXTRA_CONTACT_LIST_FILTER, filter);
            setResult(Activity.RESULT_OK, intent);
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_CANCELED && mCustomFilterView != null &&
                !mIsCustomFilterViewSelected) {
            mCustomFilterView.setActivated(false);
            return;
        }

        if (resultCode != Activity.RESULT_OK) {
            return;
        }

        switch (requestCode) {
            case SUBACTIVITY_CUSTOMIZE_FILTER: {
                final Intent intent = new Intent();
                ContactListFilter filter = ContactListFilter.createFilterWithType(
                        ContactListFilter.FILTER_TYPE_CUSTOM);
                intent.putExtra(KEY_EXTRA_CONTACT_LIST_FILTER, filter);
                setResult(Activity.RESULT_OK, intent);
                finish();
                break;
            }
        }
    }

    private static class FilterListAdapter extends BaseAdapter {
        private final List<ContactListFilter> mFilters;
        private final LayoutInflater mLayoutInflater;
        private final AccountTypeManager mAccountTypes;
        private final ContactListFilter mCurrentFilter;

        public FilterListAdapter(
                Context context, List<ContactListFilter> filters, ContactListFilter current) {
            mLayoutInflater = (LayoutInflater) context.getSystemService
                    (Context.LAYOUT_INFLATER_SERVICE);
            mFilters = filters;
            mCurrentFilter = current;
            mAccountTypes = AccountTypeManager.getInstance(context);
        }

        @Override
        public int getCount() {
            return mFilters.size();
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public ContactListFilter getItem(int position) {
            return mFilters.get(position);
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            final ContactListFilterView view;
            if (convertView != null) {
                view = (ContactListFilterView) convertView;
            } else {
                view = (ContactListFilterView) mLayoutInflater.inflate(
                        R.layout.contact_list_filter_item, parent, false);
            }
            view.setSingleAccount(mFilters.size() == 1);
            final ContactListFilter filter = mFilters.get(position);
            view.setContactListFilter(filter);
            view.bindView(mAccountTypes);
            view.setTag(filter);
            view.setActivated(filter.equals(mCurrentFilter));
            return view;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                // We have two logical "up" Activities: People and Phone.
                // Instead of having one static "up" direction, behave like back as an
                // exceptional case.
                onBackPressed();
                return true;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    /** M: Bug Fix ALPS00115673 Descriptions: add wait cursor @{ */
    @Override
    protected void onDestroy() {
        mFinished = true;
        super.onDestroy();
    }

    public boolean isActivityFinished() {
        return mFinished;
    }

    private TextView mLoadingContact;
    private ProgressBar mProgress;
    private View mLoadingContainer;
    private WaitCursorView mWaitCursorView;
    private boolean mFinished = false;
    /** @} */

    /// M: refactor phb state change.@{
    @Override
    public void onReceiveEvent(String eventType, Intent extraData) {
        Log.i(TAG, "[onReceiveEvent] eventType: " + eventType + ", extraData: " +
                extraData);
        if (GeneralEventHandler.EventType.PHB_STATE_CHANGE_EVENT.equals(eventType)
                && (!isFinishing())) {
            Log.i(TAG, "[onReceiveEvent] phb state changed, finish!");
            finish();
        }
    }
    /// @}
}
