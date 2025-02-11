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
package com.android.contacts.common.vcard;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.provider.OpenableColumns;
import android.os.Message;
import android.os.Messenger;
import android.text.BidiFormatter;
import android.text.TextDirectionHeuristics;
import android.text.TextUtils;

import com.android.contacts.common.R;
import com.android.contacts.common.activity.RequestImportVCardPermissionsActivity;

import com.mediatek.contacts.eventhandler.BaseEventHandlerActivity;
import com.mediatek.contacts.eventhandler.GeneralEventHandler;
import com.mediatek.contacts.util.ContactsPortableUtils;
import com.mediatek.contacts.util.ImportExportUtils;
import com.mediatek.contacts.util.Log;
import com.mediatek.contacts.util.VcardUtils;

import java.io.File;
import java.util.List;

/**
 * Shows a dialog confirming the export and asks actual vCard export to {@link VCardService}
 *
 * This Activity first connects to VCardService and ask an available file name and shows it to
 * a user. After the user's confirmation, it send export request with the file name, assuming the
 * file name is not reserved yet.
 */
public class ExportVCardActivity extends BaseEventHandlerActivity implements ServiceConnection,
        DialogInterface.OnClickListener, DialogInterface.OnCancelListener {
    private static final String LOG_TAG = "ExportVCardActivity";
    protected static final boolean DEBUG = VCardService.DEBUG;

    private static final int REQUEST_CREATE_DOCUMENT = 100;

    // prize add for bug 54802 by zhaojian 20180413 start
    public static boolean sIsActivityActive = false;
    // prize add for bug 54802 by zhaojian 20180413 end

    /**
     * Handler used when some Message has come from {@link VCardService}.
     */
    private class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            if (DEBUG) Log.d(LOG_TAG, "IncomingHandler received message.");

            if (msg.arg1 != 0) {
                Log.i(LOG_TAG, "Message returned from vCard server contains error code.");
                if (msg.obj != null) {
                    mErrorReason = (String)msg.obj;
                }
                showDialog(msg.arg1);
                return;
            }

            switch (msg.what) {
            case VCardService.MSG_SET_AVAILABLE_EXPORT_DESTINATION:
                if (msg.obj == null) {
                    Log.w(LOG_TAG, "Message returned from vCard server doesn't contain valid path");
                    mErrorReason = getString(R.string.fail_reason_unknown);
                    showDialog(R.id.dialog_fail_to_export_with_reason);
                } else {
                    mTargetFileName = (String)msg.obj;
                    if (TextUtils.isEmpty(mTargetFileName)) {
                        Log.w(LOG_TAG, "Destination file name coming from vCard service is empty.");
                        mErrorReason = getString(R.string.fail_reason_unknown);
                        showDialog(R.id.dialog_fail_to_export_with_reason);
                    } else {
                        if (DEBUG) {
                            Log.d(LOG_TAG,
                                    String.format("Target file name is set (%s). " +
                                            "Show confirmation dialog", mTargetFileName));
                        }
                        showDialog(R.id.dialog_export_confirmation);
                    }
                }
                break;
            default:
                Log.w(LOG_TAG, "Unknown message type: " + msg.what);
                super.handleMessage(msg);
            }
        }
    }

    /**
     * True when this Activity is connected to {@link VCardService}.
     *
     * Should be touched inside synchronized block.
     */
    protected boolean mConnected;

    /**
     * True when users need to do something and this Activity should not disconnect from
     * VCardService. False when all necessary procedures are done (including sending export request)
     * or there's some error occured.
     */
    private volatile boolean mProcessOngoing = true;

    protected VCardService mService;
    private final Messenger mIncomingMessenger = new Messenger(new IncomingHandler());
    private static final BidiFormatter mBidiFormatter = BidiFormatter.getInstance();

    // Used temporarily when asking users to confirm the file name
    private String mTargetFileName;

    // String for storing error reason temporarily.
    private String mErrorReason;

    private class ExportConfirmationListener implements DialogInterface.OnClickListener {
        private final Uri mDestinationUri;

        public ExportConfirmationListener(String path) {
            this(Uri.parse("file://" + path));
        }

        public ExportConfirmationListener(Uri uri) {
            mDestinationUri = uri;
        }

        public void onClick(DialogInterface dialog, int which) {
            if (which == DialogInterface.BUTTON_POSITIVE) {
                if (DEBUG) {
                    Log.d(LOG_TAG,
                            String.format("Try sending export request (uri: %s)", mDestinationUri));
                }
                final ExportRequest request = new ExportRequest(mDestinationUri);
                // The connection object will call finish().
                mService.handleExportRequest(request, new NotificationImportExportListener(
                        ExportVCardActivity.this));
                /** Bug Fix CR ID: ALPS00110214 */
                setResult(ImportExportUtils.RESULT_CODE);
                /** @} */
            }
            unbindAndFinish();
        }
    }

    @Override
    protected void onCreate(Bundle bundle) {
        Log.d(LOG_TAG, "[onCreate]");
        super.onCreate(bundle);

        if (RequestImportVCardPermissionsActivity.startPermissionActivity(this)) {
            return;
        }

        if (!hasExportIntentHandler()) {
            Log.e(LOG_TAG, "Couldn't find export intent handler");
            showErrorDialog();
            return;
        }

        /// M:Check directory is available.@{
        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            Log.w(LOG_TAG, "[onCreate] External storage is in state " +
                    Environment.getExternalStorageState() + ". Cancelling export");
            showDialog(R.id.dialog_sdcard_not_found);
            return;
        }

        final File targetDirectory = Environment.getExternalStorageDirectory();
        if (!(targetDirectory.exists() &&
                targetDirectory.isDirectory() &&
                targetDirectory.canRead()) &&
                !targetDirectory.mkdirs()) {
            showDialog(R.id.dialog_sdcard_not_found);
            return;
        }
        /// @}

        connectVCardService();
    }

    // prize add for bug 54802 by zhaojian 20180413 start
    @Override
    protected void onStart() {
        super.onStart();
        Log.d(LOG_TAG, "[onStart]");
        sIsActivityActive = true;
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Log.d(LOG_TAG, "[onRestart]");
        sIsActivityActive = true;
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(LOG_TAG, "[onStop]");
        sIsActivityActive = false;
    }
    // prize add for bug 54802 by zhaojian 20180413 end


    private void connectVCardService() {
        final String callingActivity = getIntent().getExtras()
                .getString(VCardCommonArguments.ARG_CALLING_ACTIVITY);
        Log.d(LOG_TAG, "[connectVCardService]callingActivity = " + callingActivity);
        Intent intent = new Intent(this, VCardService.class);
        intent.putExtra(VCardCommonArguments.ARG_CALLING_ACTIVITY, callingActivity);

        if (startService(intent) == null) {
            Log.e(LOG_TAG, "Failed to start vCard service");
            showErrorDialog();
            return;
        }

        if (!bindService(intent, this, Context.BIND_AUTO_CREATE)) {
            Log.e(LOG_TAG, "Failed to connect to vCard service.");
            showErrorDialog();
        }
        // Continued to onServiceConnected()
    }

    private boolean hasExportIntentHandler() {
        final Intent intent = getCreateDocIntent();
        final List<ResolveInfo> receivers = getPackageManager().queryIntentActivities(intent,
                PackageManager.MATCH_DEFAULT_ONLY);
        return receivers != null && receivers.size() > 0;
    }

    private Intent getCreateDocIntent() {
        final Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(VCardService.X_VCARD_MIME_TYPE);
        intent.putExtra(Intent.EXTRA_TITLE, mBidiFormatter.unicodeWrap(
                getString(R.string.exporting_vcard_filename), TextDirectionHeuristics.LTR));
        return intent;
    }

    private void showErrorDialog() {
        mErrorReason = getString(R.string.fail_reason_unknown);
        showDialog(R.id.dialog_fail_to_export_with_reason);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CREATE_DOCUMENT) {
            if (resultCode == Activity.RESULT_OK && mService != null &&
                    data != null && data.getData() != null) {
                final Uri targetFileName = data.getData();
                if (DEBUG) Log.d(LOG_TAG, "exporting to " + targetFileName);
                final ExportRequest request = new ExportRequest(targetFileName);
                // The connection object will call finish().
                mService.handleExportRequest(request, new NotificationImportExportListener(
                        ExportVCardActivity.this));
            } else if (DEBUG) {
                if (mService == null) {
                    Log.d(LOG_TAG, "No vCard service.");
                } else {
                    Log.d(LOG_TAG, "create document cancelled or no data returned");
                }
            }
            unbindAndFinish();
        }
    }

    @Override
    public synchronized void onServiceConnected(ComponentName name, IBinder binder) {
        if (DEBUG) Log.d(LOG_TAG, "connected to service, requesting a destination file name");
        mConnected = true;
        mService = ((VCardService.MyBinder) binder).getService();

        if (!ContactsPortableUtils.MTK_PHONE_BOOK_SUPPORT) {
            // Have the user choose where vcards will be exported to
            startActivityForResult(getCreateDocIntent(), REQUEST_CREATE_DOCUMENT);
        } else {
            /**
             * M: New Feature @{
             * Description: The multiple contacts could be selected to export to SD card.
             */
            VcardUtils.setDestSelection(mService, this);
            mService.handleRequestAvailableExportDestination(mIncomingMessenger);
            // Wait until MSG_SET_AVAILABLE_EXPORT_DESTINATION message is available.
            /** @} */
        }
    }

    // Use synchronized since we don't want to call unbindAndFinish() just after this call.
    @Override
    public synchronized void onServiceDisconnected(ComponentName name) {
        if (DEBUG) Log.d(LOG_TAG, "onServiceDisconnected()");
        mService = null;
        mConnected = false;
        if (mProcessOngoing) {
            // Unexpected disconnect event.
            Log.w(LOG_TAG, "Disconnected from service during the process ongoing.");
            showErrorDialog();
        }
    }

    /**
     * M: Returns the name of the target path with additional formatting characters to improve its
     * appearance in bidirectional text.
     */
    private String getTargetFileForDisplay() {
        if (mTargetFileName == null) {
            return null;
        }
        return mBidiFormatter.unicodeWrap(mTargetFileName, TextDirectionHeuristics.LTR);
    }

    @Override
    protected Dialog onCreateDialog(int id, Bundle bundle) {
        switch (id) {
            /// M:@{
            case R.id.dialog_export_confirmation: {
                return new AlertDialog.Builder(this)
                        .setTitle(R.string.confirm_export_title)
                        .setMessage(getString(R.string.confirm_export_message,
                                getTargetFileForDisplay()))
                        .setPositiveButton(android.R.string.ok,
                                new ExportConfirmationListener(mTargetFileName))
                        .setNegativeButton(android.R.string.cancel, this)
                        .setOnCancelListener(this)
                        .create();
            }
            case R.id.dialog_sdcard_not_found: {
                mProcessOngoing = false;
                return new AlertDialog.Builder(this)
                        .setMessage(R.string.no_sdcard_message)
                        .setPositiveButton(android.R.string.ok, this).create();
            }
            /// @}

            case R.id.dialog_fail_to_export_with_reason: {
                mProcessOngoing = false;
                return new AlertDialog.Builder(this)
                        .setTitle(R.string.exporting_contact_failed_title)
                        .setMessage(getString(R.string.exporting_contact_failed_message,
                                mErrorReason != null ? mErrorReason :
                                        getString(R.string.fail_reason_unknown)))
                        .setPositiveButton(android.R.string.ok, this)
                        .setOnCancelListener(this)
                        .create();
            }

        }
        return super.onCreateDialog(id, bundle);
    }

    @Override
    protected void onPrepareDialog(int id, Dialog dialog, Bundle args) {
        if (id == R.id.dialog_fail_to_export_with_reason) {
            ((AlertDialog)dialog).setMessage(mErrorReason);
        ///M:
        } else if (id == R.id.dialog_export_confirmation) {
            ((AlertDialog)dialog).setMessage(
                    getString(R.string.confirm_export_message,
                            VcardUtils.getSaveFilePathDescription(mTargetFileName, this)));
        } else {
            super.onPrepareDialog(id, dialog, args);
        }
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (DEBUG) Log.d(LOG_TAG, "ExportVCardActivity#onClick() is called");
        unbindAndFinish();
		finish();//prize-add for custom progressbar -hpf-2017-12-27
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        if (DEBUG) Log.d(LOG_TAG, "ExportVCardActivity#onCancel() is called");
        mProcessOngoing = false;
        unbindAndFinish();
        finish();//prize-add for custom progressbar -hpf-2017-12-27
    }

    @Override
    public void unbindService(ServiceConnection conn) {
        mProcessOngoing = false;
        super.unbindService(conn);
    }

    /**
     * Returns the display name for the given openable Uri or null if it could not be resolved. */
    static String getOpenableUriDisplayName(Context context, Uri uri) {
        if (uri == null) return null;
        final Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
        try {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
            }
        } finally {
            if (cursor != null)  {
                cursor.close();
            }
        }
        return null;
    }

    protected synchronized void unbindAndFinish() {
        if (mConnected) {
            unbindService(this);
            mConnected = false;
        }
        //finish();//prize-remove for custom progressbar -hpf-2017-12-27
    }

    /** M: @{ */
    private static final String FILE_TARGET_NAME = "file_target_name";
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(FILE_TARGET_NAME, mTargetFileName);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mTargetFileName = savedInstanceState.getString(FILE_TARGET_NAME);
    }

    /** @} */

}
