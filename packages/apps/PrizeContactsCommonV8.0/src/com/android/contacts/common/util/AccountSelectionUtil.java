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

package com.android.contacts.common.util;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.contacts.common.R;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.account.AccountType;
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.common.vcard.ImportVCardActivity;

import com.mediatek.contacts.model.AccountWithDataSetEx;
import com.mediatek.contacts.util.ImportExportUtils;

import com.android.internal.telephony.PhoneConstants;
import java.util.List;

/**
 * Utility class for selecting an Account for importing contact(s)
 */
public class AccountSelectionUtil {
    // TODO: maybe useful for EditContactActivity.java...
    private static final String LOG_TAG = "AccountSelectionUtil";

    public static boolean mVCardShare = false;

    public static Uri mPath;

    public static class AccountSelectedListener
            implements DialogInterface.OnClickListener {

        final private Activity mActivity;
        final private int mResId;
        final private int mSubscriptionId;

        final protected List<AccountWithDataSet> mAccountList;

        public AccountSelectedListener(Activity activity, List<AccountWithDataSet> accountList,
                int resId, int subscriptionId) {
            if (accountList == null || accountList.size() == 0) {
                Log.e(LOG_TAG, "The size of Account list is 0.");
            }
            mActivity = activity;
            mAccountList = accountList;
            mResId = resId;
            mSubscriptionId = subscriptionId;
        }

        public AccountSelectedListener(Activity activity, List<AccountWithDataSet> accountList,
                int resId) {
            // Subscription id is only needed for importing from SIM card. We can safely ignore
            // its value for SD card importing.
            this(activity, accountList, resId, /* subscriptionId = */ -1);
        }

        public void onClick(DialogInterface dialog, int which) {
            dialog.dismiss();
            doImport(mActivity, mResId, mAccountList.get(which), mSubscriptionId);
        }
    }

    public static Dialog getSelectAccountDialog(Activity activity, int resId) {
        return getSelectAccountDialog(activity, resId, null, null);
    }

    public static Dialog getSelectAccountDialog(Activity activity, int resId,
            DialogInterface.OnClickListener onClickListener) {
        return getSelectAccountDialog(activity, resId, onClickListener, null);
    }

    /**
     * When OnClickListener or OnCancelListener is null, uses a default listener.
     * The default OnCancelListener just closes itself with {@link Dialog#dismiss()}.
     */
    public static Dialog getSelectAccountDialog(Activity activity, int resId,
            DialogInterface.OnClickListener onClickListener,
            DialogInterface.OnCancelListener onCancelListener) {
        final AccountTypeManager accountTypes = AccountTypeManager.getInstance(activity);
        final List<AccountWithDataSet> writableAccountList = accountTypes.getAccounts(true);

        Log.i(LOG_TAG, "The number of available accounts: " + writableAccountList.size());

        // Assume accountList.size() > 1

        // Wrap our context to inflate list items using correct theme
        final Context dialogContext = new ContextThemeWrapper(
                activity, android.R.style.Theme_Light);
        final LayoutInflater dialogInflater = (LayoutInflater)dialogContext
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final ArrayAdapter<AccountWithDataSet> accountAdapter =
            new ArrayAdapter<AccountWithDataSet>(
                    activity, R.layout.account_selector_list_item_condensed, writableAccountList) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    convertView = dialogInflater.inflate(
                            R.layout.account_selector_list_item_condensed,
                            parent, false);
                }

                final TextView text1 = (TextView) convertView.findViewById(android.R.id.text1);
                final TextView text2 = (TextView) convertView.findViewById(android.R.id.text2);
                final ImageView icon = (ImageView) convertView.findViewById(android.R.id.icon);

                final AccountWithDataSet account = this.getItem(position);
                final AccountType accountType = accountTypes.getAccountType(
                        account.type, account.dataSet);
                final Context context = getContext();

                text1.setText(accountType.getDisplayLabel(context));
                text2.setText(account.name);
                icon.setImageDrawable(accountType.getDisplayIcon(getContext()));

                return convertView;
            }
        };

        if (onClickListener == null) {
            AccountSelectedListener accountSelectedListener =
                new AccountSelectedListener(activity, writableAccountList, resId);
            onClickListener = accountSelectedListener;
        }
        if (onCancelListener == null) {
            onCancelListener = new DialogInterface.OnCancelListener() {
                public void onCancel(DialogInterface dialog) {
                    dialog.dismiss();
                }
            };
        }
        return new AlertDialog.Builder(activity)
            .setTitle(R.string.dialog_new_contact_account)
            .setSingleChoiceItems(accountAdapter, 0, onClickListener)
            .setOnCancelListener(onCancelListener)
            .create();
    }

   public static void doImport(Activity activity, int resId, AccountWithDataSet account,
            int subscriptionId) {
        if (resId == R.string.import_from_sim) {
            doImportFromSim(activity, account, subscriptionId);
        } else if (resId == R.string.import_from_vcf_file) {
            doImportFromVcfFile(activity, account);
        }
    }

    public static void doImportFromSim(Context context, AccountWithDataSet account,
            int subscriptionId) {
        Intent importIntent = new Intent(Intent.ACTION_VIEW);
        importIntent.setType("vnd.android.cursor.item/sim-contact");
        if (account != null) {
            importIntent.putExtra("account_name", account.name);
            importIntent.putExtra("account_type", account.type);
            importIntent.putExtra("data_set", account.dataSet);
            /** M for CR 01760084 @{*/
            if (account instanceof AccountWithDataSetEx) {
                importIntent.putExtra(PhoneConstants.SUBSCRIPTION_KEY,
                        ((AccountWithDataSetEx) account).getSubId());
            }
            /** @}*/
        }
        importIntent.putExtra("subscription_id", (Integer) subscriptionId);
        importIntent.setClassName("com.android.phone", "com.android.phone.SimContacts");
        context.startActivity(importIntent);
    }

    public static void doImportFromVcfFile(Activity activity, AccountWithDataSet account) {
        Intent importIntent = new Intent(activity, ImportVCardActivity.class);
        if (account != null) {
            importIntent.putExtra("account_name", account.name);
            importIntent.putExtra("account_type", account.type);
            importIntent.putExtra("data_set", account.dataSet);
        }

        if (mVCardShare) {
            importIntent.setAction(Intent.ACTION_VIEW);
            importIntent.setData(mPath);
        }
        mVCardShare = false;
        mPath = null;
        activity.startActivityForResult(importIntent, ImportExportUtils.REQUEST_CODE);
        /*
         * Bug Fix by Mediatek Begin.
         *   Original Android's code:
              context.startActivity(importIntent);
         *   CR ID: ALPS00110214
         */
        //if (context instanceof Activity) {
        //    Activity activity = (Activity) context;
        //    activity.startActivityForResult(importIntent, ImportExportUtils.REQUEST_CODE);
        //} else {
        //    context.startActivity(importIntent);
        //}
        /*
         * Bug Fix by Mediatek End.
         */
    }

    // The following lines are provided and maintained by Mediatek Inc.

    public static void doImportFromSdCard(Context context, String sourceStorage,
            AccountWithDataSet account) {
        Log.i(LOG_TAG,"[doImportFromSdCard]sourceStorage = " + sourceStorage);
        Intent importIntent = new Intent(context,
                com.android.contacts.common.vcard.ImportVCardActivity.class);
        if (account != null) {
            importIntent.putExtra("account_name", account.name);
            importIntent.putExtra("account_type", account.type);
            importIntent.putExtra("data_set", account.dataSet);
            importIntent.putExtra("source_path", sourceStorage);
        }

        Activity activity = (Activity) context;
        activity.startActivityForResult(importIntent, ImportExportUtils.REQUEST_CODE);
    }

    // The previous lines are provided and maintained by Mediatek Inc.
}
