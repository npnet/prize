/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 *
 * MediaTek Inc. (C) 2010. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER ON
 * AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
 * NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
 * SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
 * SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES TO LOOK ONLY TO SUCH
 * THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. RECEIVER EXPRESSLY ACKNOWLEDGES
 * THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES
 * CONTAINED IN MEDIATEK SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK
 * SOFTWARE RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND
 * CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
 * AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
 * OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY RECEIVER TO
 * MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek Software")
 * have been modified by MediaTek Inc. All revisions are subject to any receiver's
 * applicable license agreements with MediaTek Inc.
 */
package com.mediatek.contacts.list;


import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.ComponentName;
import android.content.Context;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.widget.Toast;

import com.android.contacts.R;
import com.mediatek.contacts.ExtensionManager;

import com.mediatek.contacts.list.MultiBasePickerAdapter.PickListItemCache;
import com.mediatek.contacts.list.MultiBasePickerAdapter.PickListItemCache.PickListItemData;
import com.mediatek.contacts.list.service.MultiChoiceHandlerListener;
import com.mediatek.contacts.list.service.MultiChoiceRequest;
import com.mediatek.contacts.list.service.MultiChoiceService;
import com.mediatek.contacts.util.Log;

import java.util.ArrayList;
import java.util.List;
/*prize-add-hpf-2018-2-26-start*/
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
/*prize-add-hpf-2018-2-26-end*/
/*prize add for bug58893 by zhaojian 20180525 start*/
import android.app.ActivityManager;
import com.android.contacts.common.vcard.ProcessorBase;
/*prize add for bug58893 by zhaojian 20180525 end*/

public class MultiDeletionPickerFragment extends MultiBasePickerFragment {
    private static final String TAG = "MultiDeletionPickerFragment";

    public static final boolean DEBUG = true;
    private static final String DIALOG_FRAGMENT_TAG = "confirm";

    private SendRequestHandler mRequestHandler;
    private HandlerThread mHandlerThread;

    private DeleteRequestConnection mConnection;

    private int mRetryCount = 20;

    /*prize add for bug58893 by zhaojian 20180524 start*/
    public MultiChoiceHandlerListener mMultiChoiceHandlerListener;
    /*prize add for bug58893 by zhaojian 20180524 end*/

    @Override
    public CursorLoader createCursorLoader(Context context) {
        return new CursorLoader(context, null, null, null, null, null);
    }

    @Override
    public void onOptionAction() {
        if (getCheckedItemIds().length == 0) {
            Toast.makeText(this.getContext(), R.string.multichoice_no_select_alert,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        prizeShowBottomDialog();
        /*ConfirmDialog cDialog = new ConfirmDialog();
        cDialog.setTargetFragment(this, 0);
        cDialog.setArguments(this.getArguments());
        cDialog.show(this.getFragmentManager(), DIALOG_FRAGMENT_TAG);*/
    }

    public static class ConfirmDialog extends DialogFragment {

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this.getActivity()).setTitle(
                    R.string.multichoice_delete_confirm_title).setMessage(
                    R.string.multichoice_delete_confirm_message).setNegativeButton(
                    android.R.string.cancel, null).setPositiveButton(android.R.string.ok,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            final MultiDeletionPickerFragment target =
                                    (MultiDeletionPickerFragment) getTargetFragment();
                            if (target != null) {
                                target.handleDelete();
                            }
                        }
                    });
            return builder.create();
        }

        @Override
        public void onDestroyView() {
            super.onDestroyView();
            setTargetFragment(null, 0);
        }
    }
    
    /*prize-add-hpf-2018-2-26-start*/
    private	AlertDialog mDialog;
	private void prizeShowBottomDialog() {
		Log.d(TAG,"[prizeShowBottomDialog]");
		View rootView = View.inflate(getActivity(), R.layout.prize_contacts_delete_dialog, null);
		View delete = rootView.findViewById(R.id.delete_contact);
		View cancel = rootView.findViewById(R.id.cancel_btn);
		delete.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				handleDelete();
				mDialog.dismiss();
			}
		});
		
		cancel.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				mDialog.dismiss();
			}
		});
		mDialog = new AlertDialog.Builder(getActivity()).setView(rootView).create();
		Window dialogWindow = mDialog.getWindow();
		dialogWindow.getDecorView().setPadding(0, 0, 0, 0);
		dialogWindow.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, 
				WindowManager.LayoutParams.FLAG_FULLSCREEN);
		dialogWindow.setBackgroundDrawableResource(android.R.color.transparent);
		WindowManager.LayoutParams mParams = dialogWindow.getAttributes();
		mParams.width = WindowManager.LayoutParams.MATCH_PARENT;
		mParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
		mParams.gravity = Gravity.BOTTOM;
		dialogWindow.setAttributes(mParams);
		dialogWindow.setWindowAnimations(R.style.GetDialogBottomMenuAnimation);
		mDialog.show();
	}
	/*prize-add-hpf-2018-2-26-end*/

    private void handleDelete() {
        if (mConnection != null) {
            Log.w(TAG, "[handleDelete]abort due to mConnection is not null,return.");
            return ;
        }

        //[fix ALPS01206556]
        //don't start delete if the data hasn't been loaded to the listview.
        final MultiBasePickerAdapter adapter =
                (MultiBasePickerAdapter) getAdapter();
        int listItemSize = adapter == null ? -1 :
            adapter.getListItemCache() == null ? -1 : adapter.getListItemCache().getCacheSize();
        if (listItemSize <= 0) {
            Log.w(TAG, "[handleDelete] there is no items,listItemSize:" + listItemSize);
            return;
        }
        //[fix ALPS01206556] end

        startDeleteService();

        if (mHandlerThread == null) {
            mHandlerThread = new HandlerThread(TAG);
            mHandlerThread.start();
            mRequestHandler = new SendRequestHandler(mHandlerThread.getLooper());
        }

        List<MultiChoiceRequest> requests = new ArrayList<MultiChoiceRequest>();

        final PickListItemCache listItemCacher = adapter.getListItemCache();
        final long[] checkedIds = getCheckedItemIds();
        Log.i(TAG, "[handleDelete] listItemSize:" + listItemSize +
                ",checkedItemSize:" + checkedIds.length);
        for (long id : checkedIds) {
            PickListItemData item = listItemCacher.getItemData(id);
            requests.add(new MultiChoiceRequest(item.contactIndicator, item.simIndex,
                    (int) id, item.displayName));
        }

        /*
         * Bug Fix by Mediatek Begin.
         *
         * CR ID: ALPS00233127
         */
        if (requests.size() > 0) {
            mRequestHandler.sendMessage(mRequestHandler.obtainMessage(
                    SendRequestHandler.MSG_REQUEST, requests));
        } else {
            mRequestHandler.sendMessage(mRequestHandler.obtainMessage(SendRequestHandler.MSG_END));
        }
        /*
         * Bug Fix by Mediatek End.
         */
    }

    private class DeleteRequestConnection implements ServiceConnection {
        public MultiChoiceService mService;
        public boolean sendDeleteRequest(final List<MultiChoiceRequest> requests) {
            Log.d(TAG, "[sendDeleteRequest]");
            if (mService == null) {
                Log.i(TAG, "[sendDeleteRequest] mService is not ready");
                return false;
            }
            /*prize add for bug58893 by zhaojian 20180524 start*/
           /* mService.handleDeleteRequest(requests, new MultiChoiceHandlerListener(*//*mService*//*
            		MultiDeletionPickerFragment.this.getActivity()));//prize-change for custom progressbar -hpf-2017-12-27*/
            mMultiChoiceHandlerListener =  new MultiChoiceHandlerListener(MultiDeletionPickerFragment.this.getActivity());
            mService.handleDeleteRequest(requests, mMultiChoiceHandlerListener);
            /*prize add for bug58893 by zhaojian 20180524 end*/
            return true;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            Log.d(TAG, "[onServiceConnected]");
            mService = ((MultiChoiceService.MyBinder) binder).getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "[onServiceDisconnected] Disconnected from MultiChoiceService");
        }
    }

    private class SendRequestHandler extends Handler {

        public static final int MSG_REQUEST = 100;
        public static final int MSG_END = 200;

        public SendRequestHandler(Looper looper) {
            super(looper);
        }

        @SuppressWarnings("unchecked")
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == MSG_REQUEST) {
                if (!mConnection.sendDeleteRequest((List<MultiChoiceRequest>) msg.obj)) {
                    if (mRetryCount-- > 0) {
                        sendMessageDelayed(obtainMessage(msg.what, msg.obj), 500);
                    }
                    else {
                        sendMessage(obtainMessage(MSG_END));
                    }
                } else {
                    sendMessage(obtainMessage(MSG_END));
                }
                return;
            } else if (msg.what == MSG_END) {
                destroyMyself();
                return;
            }
            super.handleMessage(msg);
        }

    }

    void startDeleteService() {
        mConnection = new DeleteRequestConnection();

        Log.i(TAG, "[startDeleteService] Bind to MultiChoiceService.");
        // We don't want the service finishes itself just after this connection.
        Intent intent = new Intent(this.getActivity(), MultiChoiceService.class);
        getContext().startService(intent);
        getContext().bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    void destroyMyself() {
        Log.i(TAG, "[destroyMyself]mHandlerThread:" + mHandlerThread);
        if (mConnection != null) {
            getContext().unbindService(mConnection);
            mConnection = null;
        }
        if (mHandlerThread != null) {
            mHandlerThread.quit();
            mHandlerThread = null;
        }
        if (getActivity() != null) {
            //getActivity().finish();//prize-change for custom progressbar -hpf-2017-12-27
        }
    }

    /**
     * [ALPS01040180]the dialog of delete confirming, should be dismissed when
     * fragment start. this would lead to better user experience
     */
    private void dismissDialogIfNeeded() {
        DialogFragment dialog = (DialogFragment) getFragmentManager().
                findFragmentByTag(DIALOG_FRAGMENT_TAG);
        if (dialog != null) {
            Log.i(TAG, "[dismissDialogIfNeeded]dismiss the dialog fragment: " + dialog);
            dialog.dismiss();
        } else {
            Log.d(TAG, "[dismissDialogIfNeeded]no dialog found");
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        dismissDialogIfNeeded();
    }

    @Override
    public int getMultiChoiceLimitCount() {
        //M:op01 max support 5000
        int defaultCount = super.getMultiChoiceLimitCount();
        return ExtensionManager.getInstance().getOp01Extension().
                getMultiChoiceLimitCount(defaultCount);
    }

    /**
     * M: In contacts delete screen, don't show sdn number.
     * @return whether show the sdn number.
     */
    public boolean isShowSdnNumber() {
        return false;
    }
    
//    /*prize-add-fix bug [55320] -hpf-2018-05-17-start*/
//    //The parent method in ContactEntryListFragment
//    @Override
//    public boolean isDeletionProcessorRun(){
//    	boolean isProcessing = MultiChoiceService.isProcessing(MultiChoiceService.TYPE_DELETE);
//    	Log.d(TAG, "[isDeletionProcessorRun]  isProcessing = " + isProcessing);
//    	return isProcessing;
//    }
//    /*prize-add-fix bug [55320] -hpf-2018-05-17-end*/


    /*prize add for bug58893 by zhaojian 20180525 start*/
    @Override
    public void onDestroy() {
        super.onDestroy();
        boolean isMultiChoiceServiceWork = isServiceWork(getActivity(),"com.mediatek.contacts.list.service.MultiChoiceService");
        Log.i(TAG, "[onDestroy],isMultiChoiceServiceWork = " + isMultiChoiceServiceWork);
        if(isMultiChoiceServiceWork){
            if(mMultiChoiceHandlerListener != null){
                int jobId = mMultiChoiceHandlerListener.getDeleteJobId();

                final ProcessorBase processor = MultiChoiceService.RUNNINGJOBMAP.remove(jobId);
                if (processor != null) {
                    processor.cancel(true);
                } else {
                    Log.w(TAG, "[handleCancelRequest]"
                            + String.format("Tried to remove unknown job (id: %d)", jobId));
                }
            }
        }
    }

    private boolean isServiceWork(Context mContext, String serviceName) {
        boolean isWork = false;
        ActivityManager myAM = (ActivityManager) mContext
                .getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningServiceInfo> myList = myAM.getRunningServices(40);
        if (myList.size() <= 0) {
            return false;
        }
        for (int i = 0; i < myList.size(); i++) {
            String mName = myList.get(i).service.getClassName().toString();
            if (mName.equals(serviceName)) {
                isWork = true;
                break;
            }
        }
        return isWork;
    }
    /*prize add for bug58893 by zhaojian 20180525 end*/
}
