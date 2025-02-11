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
 * limitations under the License
 */

package com.android.contacts.activities;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.provider.ContactsContract.Intents;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import com.android.contacts.R;
import com.android.contacts.editor.ContactEditorUtils;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.common.util.AccountsListAdapter;
import com.android.contacts.common.util.AccountsListAdapter.AccountListFilter;
import com.android.contacts.common.util.ImplicitIntentsUtil;

import com.mediatek.contacts.ContactsApplicationEx;
import com.mediatek.contacts.activities.ActivitiesUtils;
import com.mediatek.contacts.model.AccountWithDataSetEx;
import com.mediatek.contacts.simcontact.SimCardUtils;
import com.mediatek.contacts.simcontact.SubInfoUtils;
import com.mediatek.contacts.util.AccountTypeUtils;
import com.mediatek.contacts.util.ContactsSettingsUtils;
import com.mediatek.contacts.util.Log;

import java.util.List;
import java.util.ArrayList;

/**
 * This activity can be shown to the user when creating a new contact to inform the user about
 * which account the contact will be saved in. There is also an option to add an account at
 * this time. The {@link Intent} in the activity result will contain an extra
 * {@link #Intents.Insert.ACCOUNT} that contains the {@link AccountWithDataSet} to create
 * the new contact in. If the activity result doesn't contain intent data, then there is no
 * account for this contact.
 */
public class ContactEditorAccountsChangedActivity extends Activity {

	private static final String TAG = ContactEditorAccountsChangedActivity.class.getSimpleName();

	private static final int SUBACTIVITY_ADD_NEW_ACCOUNT = 1;

	private AccountsListAdapter mAccountListAdapter;
	private ContactEditorUtils mEditorUtils;

    /* M: [Google Issue]ALPS03284299, 1/3
     * fix: multiple AlertDialog will be created and cause their windows overlap together @{ */
    private AlertDialog mDialog;
    /* @} */
	private final OnItemClickListener mAccountListItemClickListener = new OnItemClickListener() {
		@Override
		public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
			if (mAccountListAdapter == null || mCheckCount >= 1) {
				Log.w(TAG, "mAccountListAdapter = " + mAccountListAdapter + "; mCheckCount = " + mCheckCount);
				return;
			}
			/**
			 * M: New Feature Descriptions: get sim info for create sim
			 * contact @{
			 */
			String accountType = mAccountListAdapter.getItem(position).type.toString();
			if (AccountTypeUtils.isAccountTypeIccCard(accountType)) {
				AccountWithDataSet ads = mAccountListAdapter.getItem(position);

				mSubId = SubInfoUtils.getInvalidSubId();
				if (ads instanceof AccountWithDataSetEx) {
					mSubId = ((AccountWithDataSetEx) ads).getSubId();
				}
				/** M: change for PHB Status refactoring. @{ */
				Log.d(TAG, "the account is " + mAccountListAdapter.getItem(position).type + " the name is = "
						+ mAccountListAdapter.getItem(position).name);
				Log.d(TAG, "the mCheckCount = " + mCheckCount);
				mCheckCount++;
				checkPHBStateAndSaveAccount(position);
				/** @} */
			} else {
				mCheckCount++;
				saveAccountAndReturnResult(mAccountListAdapter.getItem(position));
			}
			/** @} */
		}
	};

	private final OnClickListener mAddAccountClickListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			
			final Intent intent = ImplicitIntentsUtil.getIntentForAddingAccount();
			Log.d(TAG,"[onClick]  intent = "+intent);
			startActivityForResult(intent, SUBACTIVITY_ADD_NEW_ACCOUNT);
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		/// M: Bug fix ALPS00251666, can not add contact when in delete
		/// processing.
		if (ContactsApplicationEx.isContactsApplicationBusy()) {
			Log.w(TAG, "[onCreate]contacts busy, should not edit, finish");
			finish();
		}
		mEditorUtils = ContactEditorUtils.getInstance(this);
	}

	private String mTitle = "";//prize-add-hpf-2018-1-2
	
	@Override
	protected void onResume() {
		super.onResume();
		/// M: Add account type for handling special case @{
		final List<AccountWithDataSet> tempAccounts = AccountTypeManager.getInstance(this).getAccounts(true);
		List<AccountWithDataSet> accounts = new ArrayList<AccountWithDataSet>(tempAccounts);
		ActivitiesUtils.customAccountsList(accounts, this);
		/// @}
		final int numAccounts = accounts.size();
		if (numAccounts < 0) {
			throw new IllegalStateException("Cannot have a negative number of accounts");
		}

		final View view;
		if (numAccounts >= 2) {
			// When the user has 2+ writable accounts, show a list of accounts
			// so the user can pick
			// which account to create a contact in.
			view = View.inflate(this, R.layout.contact_editor_accounts_changed_activity_with_picker, null);
			
			/* prize-change-hpf-2018-1-2-start */
			/*final TextView textView = (TextView) view.findViewById(R.id.text);
			textView.setText(getString(R.string.store_contact_to));*/
			mTitle = getString(R.string.store_contact_to);
			/* prize-change-hpf-2018-1-2-end */
			
			/*prize-remove for dido os8.0-hpf-2017-8-25-start*/
			/*final Button button = (Button) view.findViewById(R.id.add_account_button);
			button.setText(getString(R.string.add_new_account));
			button.setOnClickListener(mAddAccountClickListener);*/
			/*prize-remove for dido os8.0-hpf-2017-8-25-end*/

			final ListView accountListView = (ListView) view.findViewById(R.id.account_list);
            /** M: ALPS03621760 create adapter for none sim.
             * @ { original code
             * mAccountListAdapter = new AccountsListAdapter(this,
             *        AccountListFilter.ACCOUNTS_CONTACT_WRITABLE);
             * @ }
             * @ { */
            int type = this.getIntent().getIntExtra(ContactsSettingsUtils.ACCOUNT_TYPE,
                    ContactsSettingsUtils.ALL_TYPE_ACCOUNT);
            if (type == ContactsSettingsUtils.NONE_SIM_TYPE_ACCOUNT) {
                mAccountListAdapter = new AccountsListAdapter(this,
                                 AccountListFilter.ACCOUNTS_CONTACT_WRITABLE_NONE_SIM);
            } else {
                mAccountListAdapter = new AccountsListAdapter(this,
                        AccountListFilter.ACCOUNTS_CONTACT_WRITABLE);
            }
            /** @ } */
			accountListView.setAdapter(mAccountListAdapter);
			accountListView.setOnItemClickListener(mAccountListItemClickListener);
		} else if (numAccounts == 1) {
			// If the user has 1 writable account we will just show the user a
			// message with 2
			// possible action buttons.
			view = View.inflate(this, R.layout.contact_editor_accounts_changed_activity_with_text, null);

			final TextView textView = (TextView) view.findViewById(R.id.text);
			final Button leftButton = (Button) view.findViewById(R.id.left_button);
			/*prize-add for dido os8.0-hpf-2017-9-26-start*/
			leftButton.setVisibility(View.GONE);
			/*prize-add for dido os8.0-hpf-2017-8-26-end*/
			final Button rightButton = (Button) view.findViewById(R.id.right_button);

			final AccountWithDataSet account = accounts.get(0);
			/**
			 * M: Fix CR ALPS00839693,the "Phone" should be translated into
			 * Chinese
			 */
			if (AccountTypeUtils.ACCOUNT_NAME_LOCAL_PHONE.equals(account.name)) {
				textView.setText(
						getString(R.string.contact_editor_prompt_one_account, getString(R.string.account_phone_only)));
			} else {
				textView.setText(getString(R.string.contact_editor_prompt_one_account, account.name));
			}

			// This button allows the user to add a new account to the device
			// and return to
			// this app afterwards.
			/*prize-remove for dido os8.0-hpf-2017-9-26-start*/
			//leftButton.setText(getString(R.string.add_new_account));
			//leftButton.setOnClickListener(mAddAccountClickListener);
			/*prize-remove for dido os8.0-hpf-2017-9-26-end*/

			// This button allows the user to continue creating the contact in
			// the specified
			// account.
			rightButton.setText(getString(android.R.string.ok));
			rightButton.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					saveAccountAndReturnResult(account);
				}
			});
		} else {
			// If the user has 0 writable accounts, we will just show the user a
			// message with 2
			// possible action buttons.
			view = View.inflate(this, R.layout.contact_editor_accounts_changed_activity_with_text, null);

			final TextView textView = (TextView) view.findViewById(R.id.text);
			final Button leftButton = (Button) view.findViewById(R.id.left_button);
			final Button rightButton = (Button) view.findViewById(R.id.right_button);

			textView.setText(getString(R.string.contact_editor_prompt_zero_accounts));

			// This button allows the user to continue editing the contact as a
			// phone-only
			// local contact.
			leftButton.setText(getString(R.string.keep_local));
			leftButton.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					// Remember that the user wants to create local contacts, so
					// the user is not
					// prompted again with this activity.
					mEditorUtils.saveDefaultAndAllAccounts(null);
					setResult(RESULT_OK);
					finish();
				}
			});

			// This button allows the user to add a new account to the device
			// and return to
			// this app afterwards.
			rightButton.setText(getString(R.string.add_account));
			rightButton.setOnClickListener(mAddAccountClickListener);
		}

        /* M: [Google Issue]ALPS03284299, 2/3
         * Original code: @{
         new AlertDialog.Builder(this)
                .setView(view)
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        finish();
                    }
                })
                .create()
                .show();
         * @}
         * New code: @{ */
        mDialog = new AlertDialog.Builder(this)
                .setView(view)
                .setTitle("".equals(mTitle)?getString(R.string.reminder):mTitle)//prize-add-hpf-2017-12-15
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        finish();
                    }
                })
                .create();
        mDialog.show();
    }

    /* M: [Google Issue]ALPS03284299, 3/3 @{ */
    @Override
    protected void onStop() {
        super.onStop();
        if (mDialog != null) {
            mDialog.dismiss();
        }
    }
    /* @} */

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, "[onActivityResult] requestCode:" + requestCode + ",resultCode:"
                + resultCode + ",data:" + data);
		if (requestCode == SUBACTIVITY_ADD_NEW_ACCOUNT) {
			// If the user canceled the account setup process, then keep this
			// activity visible to
			// the user.
			if (resultCode != RESULT_OK) {
				return;
			}
			// Subactivity was successful, so pass the result back and finish
			// the activity.
			AccountWithDataSet account = mEditorUtils.getCreatedAccount(resultCode, data);
			Log.d(TAG, "[onActivityResult]  account = "+account);
			if (account == null) {
				Log.w(TAG, "[onActivityResult] account is null...");
				setResult(resultCode);
				finish();
				return;
			}
			saveAccountAndReturnResult(account);
		}
		
	}

	private void saveAccountAndReturnResult(AccountWithDataSet account) {
		// Save this as the default account
		mEditorUtils.saveDefaultAndAllAccounts(account);

		// Pass account info in activity result intent
		Intent intent = new Intent();
		intent.putExtra(Intents.Insert.EXTRA_ACCOUNT, account);
		/**
		 * M: New Feature Descriptions: get sim info for create sim contact @{
		 */
		intent.putExtra("mSubId", mSubId);
		intent.putExtra("mSimId", mSimId);
		intent.putExtra("mIsSimType", mNewSimType);
		Log.d(TAG, " the mSubId and msimid is = " + mSubId + "   " + mSimId + " | mNewSimType : " + mNewSimType);
		/** @} */
		setResult(RESULT_OK, intent);
		finish();
	}

	/** M: @{ */

    /// M: Change for PHB Status refactoring. @{
    private void checkPHBStateAndSaveAccount(int position) {
        Log.d(TAG, "[checkPHBStateAndSaveAccount] mSubId=" + mSubId);
        if (!SimCardUtils.checkPHBState(this, mSubId)) {
            finish();
            return;
        }
        mSimId = mSubId;
        Log.d(TAG, "[checkPHBStateAndSaveAccount] mSimSelectionDialog mSimId is " + mSimId);
        mNewSimType = true;
        saveAccountAndReturnResult(mAccountListAdapter.getItem(position));
        return;
    }
    /// @}

	/// M: New Feature Descriptions: get sim info for create sim contact. @{
	private boolean mNewSimType = false;
	private static final int REQUEST_TYPE = 304;
	private int mSubId = -1;
	private int mSimId = -1;
	int mCheckCount = 0;
	/// @}

	/** @} */
}
