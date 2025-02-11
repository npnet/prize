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
package com.mediatek.contacts.list.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.Process;
import android.widget.Toast;

import com.android.contacts.R;
import com.mediatek.contacts.util.ContactsIntent;
import com.mediatek.contacts.util.ErrorCause;
import com.mediatek.contacts.util.Log;
/*prize-add for custom progressbar -hpf-2017-12-27-start*/
import com.android.contacts.common.prize.PrizeCirclePercentView;
import com.android.contacts.common.prize.PrizeCirclePercentView.OnFinishListener;

import android.view.Display;
import android.view.View;
import android.view.Window;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.content.DialogInterface;
import android.content.ComponentName;
import java.util.List;
/*prize-add for custom progressbar -hpf-2017-12-27-end*/
/*prize add by zhaojian 20180412 start*/
import com.mediatek.contacts.list.ContactListMultiChoiceActivity;
/*prize add by zhaojian 20180412 end*/

public class MultiChoiceHandlerListener{
    private static final String TAG = "MultiChoiceHandlerListener";

    /**
     * the key used for {@link #onFinished(int, int, int)} called time. This is
     * help the test case to get the really time finished.
     */

    public static final String KEY_FINISH_TIME = "key_finish_time";

    static final String DEFAULT_NOTIFICATION_TAG = "MultiChoiceServiceProgress";

    static final String FAILURE_NOTIFICATION_TAG = "MultiChoiceServiceFailure";

    /*prize add for bug58893 by zhaojian 20180524 start*/
    static final String PRIZE_DISABLE_NAVIGATION = "prize_disable_navigation";
    /*prize add for bug58893 by zhaojian 20180524 end*/

    //private final NotificationManager mNotificationManager;//prize-remove for custom progressbar -hpf-2017-12-27

    // context should be the object of MultiChoiceService
    private final /*Service*/Activity mContext;//prize-change for custom progressbar -hpf-2017-12-27

    /*add for support dialer to use mtk contactImportExport @{*/
    private String mCallingActivityName = null;

    // prize add by zhaojian for bug 37801 20171021 start
    private HandlerThread messageThread;
    private ServiceHandler mHandler;
    // prize add by zhaojian for bug 37801 20171021 end
    /*prize-add for custom progressbar -hpf-2017-12-27-start*/
    private PrizeCirclePercentView mPg;
    private TextView mPgTvTitle;
    private AlertDialog mProgressDialog;
    private AlertDialog mProgressDialogWaitting;
    private boolean mShouldCancelJob = false;
    private ProgressBar mWaittingProgressBar;
    private int mDialogWidth = 0;
    private int mDeleteJobId = 0;
    private String mStrFinishAmount = "";
    private int mRequestType = 0;
    
    private final static int CASE_TOAST = 0;
    private final static int CASE_SHOW_DIALOG = 1;
    private final static int CASE_UPDATE_PROGRESS = 2;
    private final static int CASE_HIDE_DIALOG = 3;
    /*prize-add for custom progressbar -hpf-2017-12-27-start*/
    /*prize-remove for custom progressbar -hpf-2017-12-27-start*/
    /*public MultiChoiceHandlerListener(Service service, String callingActivityName) {
        this(service);
        mCallingActivityName = callingActivityName;
    }*/
    //@}
    /*public MultiChoiceHandlerListener(Service service) {
        mContext = service;
        mNotificationManager = (NotificationManager) mContext
                .getSystemService(Context.NOTIFICATION_SERVICE);
    }*/
    /*prize-remove for custom progressbar -hpf-2017-12-27-end*/
    
    /*prize-add for custom progressbar -hpf-2017-12-27-start*/
    public MultiChoiceHandlerListener(Activity context) {
    	mContext = context;
        initHandler();
        mDialogWidth = (int) mContext.getResources().getDimension(R.dimen.prize_process_dialog_width);
        mStrFinishAmount = mContext.getString(R.string.prize_finished_amount);
    }
    /*prize-add for custom progressbar -hpf-2017-12-27-end*/

    /*prize add for bug58893 by zhaojian 20180525 start*/
    public int getDeleteJobId(){
        return mDeleteJobId;
    }
    /*prize add for bug58893 by zhaojian 20180525 end*/

    // prize add by zhaojian for bug 37801 20171021 start
    private final class ServiceHandler extends Handler {
        public ServiceHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case CASE_TOAST:
                	Log.d(TAG, "[handleMessage]case CASE_TOAST");
                    Toast.makeText(mContext, (String)msg.obj, Toast.LENGTH_SHORT).show();
                    break;
                case CASE_SHOW_DIALOG:
    	    		Log.d(TAG, "[handleMessage]case CASE_SHOW_DIALOG");
    	    		String title = (String) msg.obj;
    	    		showProgressDialog(title);
    	    		break;
    	    		
    	    	case CASE_UPDATE_PROGRESS:
    	    		Log.d(TAG, "[handleMessage]case CASE_UPDATE_PROGRESS");
    	    		float currentCount = msg.arg1;
    	    		float totalCount = msg.arg2;
    	    		float percent = currentCount/totalCount;
    	    		if(mPg != null && mPg.getVisibility() == View.VISIBLE){
    	    			mPg.setPercent(percent);
    	    		}
    	    		break;
    	    	
    	    	case CASE_HIDE_DIALOG:
    	    		Log.d(TAG, "[handleMessage]case CASE_HIDE_DIALOG");
    	    		int requestType = msg.arg1;
    	    		hideProgressDialog();
    	    		hideWaittingDialog();
    	    		if (requestType != MultiChoiceService.TYPE_DELETE) {
    	    			//messageThread.quit();
    	    			mContext.finish();	
    	    		}
    	    		
    	    		break;
                default:
                    break;
            }
        }
    }

    public void initHandler(){
        messageThread = new HandlerThread("MessageThread", Process.THREAD_PRIORITY_BACKGROUND);
        messageThread.start();

        Looper looper = messageThread.getLooper();
        mHandler = new ServiceHandler(looper);

    }
    // prize add by zhaojian for bug 37801 20171021 end

    synchronized void onProcessed(final int requestType, final int jobId, final int currentCount,
            final int totalCount, final String contactName) {
        Log.i(TAG, "[onProcessed]requestType = " + requestType + ",jobId = " + jobId
                + ",currentCount = " + currentCount + ",totalCount = " + totalCount
                + ",contactName = " + contactName);
        
        /*prize-add for custom progressbar -hpf-2017-12-27-start*/
        mDeleteJobId = jobId;
    	Message msg = mHandler.obtainMessage();
    	msg.what = CASE_UPDATE_PROGRESS;
    	msg.arg1 = currentCount;
    	msg.arg2 = totalCount;
    	mHandler.sendMessage(msg);
    	/*prize-add for custom progressbar -hpf-2017-12-27-end*/
    	
        if (currentCount % 10 != 0 && currentCount != 1 && currentCount != totalCount) {
            Log.w(TAG, "[onProcessed]return");
            return;
        }
        // prize delete for bug 37801 by zhaojian 20171020 start
//        final String totalCountString = String.valueOf(totalCount);
//        final String tickerText;
//        final String description;
//        int statIconId = 0;
//        if (requestType == MultiChoiceService.TYPE_DELETE) {
//            tickerText = mContext.getString(R.string.notifier_progress_delete_message,
//                    String.valueOf(currentCount), totalCountString, contactName);
//            if (totalCount == -1) {
//                description = mContext
//                        .getString(R.string.notifier_progress__delete_will_start_message);    
//            } else {
//                description = mContext.getString(R.string.notifier_progress_delete_description,     
//                        contactName);
//            }
//            statIconId = android.R.drawable.ic_menu_delete;
//        } else {
//            tickerText = mContext.getString(R.string.notifier_progress_copy_message,
//                    String.valueOf(currentCount), totalCountString, contactName);
//            if (totalCount == -1) {
//                description = mContext
//                        .getString(R.string.notifier_progress__copy_will_start_message);        
//            } else {
//                description = mContext.getString(R.string.notifier_progress_copy_description,       
//                        contactName);
//            }
//            statIconId = R.drawable.mtk_ic_menu_copy_holo_dark;
//        }
//        Log.d(TAG, "[onProcessed] notify DEFAULT_NOTIFICATION_TAG,description: " + description);
//        final Notification notification = constructProgressNotification(
//                mContext.getApplicationContext(), requestType, description, tickerText, jobId,
//                totalCount, currentCount, statIconId);
//        /*prize-change-delete-contact-no-show-notify-huangliemin 2016-6-2 start*/
//        //mNotificationManager.notify(DEFAULT_NOTIFICATION_TAG, jobId, notification);
//        if(requestType != MultiChoiceService.TYPE_DELETE) {
//        	mNotificationManager.notify(DEFAULT_NOTIFICATION_TAG, jobId, notification);
//        }
//        /*prize-change-delete-contact-no-show-notify-huangliemin 2016-6-2 end*/
        // prize delete for bug 37801 by zhaojian 20171020 end

        // prize add for bug 37801 by zhaojian 20171123 start
        String description = null;
        if(requestType == MultiChoiceService.TYPE_DELETE){
            if(totalCount == -1) {
                //description = mContext.getString(R.string.prize_removing_message);          
                //mHandler.obtainMessage(0, description).sendToTarget();
            	mRequestType = requestType;
            	Message msgDel = mHandler.obtainMessage();
            	msgDel.what = CASE_SHOW_DIALOG;
            	final String message = mContext.getString(R.string.prize_deleting);
            	msgDel.obj = message;
            	mHandler.sendMessage(msgDel);
            }
        }else {
            if(totalCount == -1) {
                //description = mContext.getString(R.string.prize_copying_message);         
                //mHandler.obtainMessage(0, description).sendToTarget();
            	mRequestType = requestType;
            	Message msgCopy = mHandler.obtainMessage();
            	msgCopy.what = CASE_SHOW_DIALOG;
            	final String message = mContext.getString(R.string.prize_copying_message);
            	msgCopy.obj = message;
            	mHandler.sendMessage(msgCopy);
            }
        }
        // prize add for bug 37801 by zhaojian 20171123 end
    }

    synchronized void onFinished(final int requestType, final int jobId, final int total) {
        long currentTimeMillis = System.currentTimeMillis();
        Log.i(TAG, "[onFinished] jobId = " + jobId + " total = " + total + " requestType = "
                + requestType);
        long endTime = System.currentTimeMillis();
        Log.d(TAG, "[CMCC Performance test][Contacts] delete 1500 contacts end [" + endTime
                + "]");
        // Dismiss MultiChoiceConfirmActivity
        Intent i = new Intent()
                .setAction(ContactsIntent.MULTICHOICE.ACTION_MULTICHOICE_PROCESS_FINISH);
        i.putExtra(KEY_FINISH_TIME, currentTimeMillis);
        mContext.sendBroadcast(i);
        i = null;
		
        final String title;
        final String description;
        final int statIconId;

        if (requestType == MultiChoiceService.TYPE_DELETE) {
            // A good experience is to cache the resource.
            title = mContext.getString(R.string.notifier_finish_delete_title);
            description = mContext.getString(R.string.notifier_finish_delete_content, total);
            // statIconId = R.drawable.ic_stat_delete;
            statIconId = android.R.drawable.ic_menu_delete;
        } else {
            title = mContext.getString(R.string.notifier_finish_copy_title);
            description = mContext.getString(R.string.notifier_finish_copy_content, total);
            statIconId = R.drawable.mtk_ic_menu_copy_holo_dark;

        }

        /*prize-add for custom progressbar -hpf-2017-12-27-start*/
        mHandler.obtainMessage(0, title+","+mStrFinishAmount+total).sendToTarget();
        Message msg = mHandler.obtainMessage();
        msg.arg1 = requestType;
        msg.what = CASE_HIDE_DIALOG;
        mHandler.sendMessage(msg);
        /*prize-add for custom progressbar -hpf-2017-12-27-end*/
        /*
         * support Dialer to use mtk contacts import/export when finished exported between phone
         * contacts and sim contacts.click Notification will jump to callingActivity.@{
         */
        // prize delete for bug 37801 by zhaojian 20171020 start
//        final Intent intent = new Intent();
//        if(mCallingActivityName != null && mCallingActivityName.indexOf(
//                ExportProcessor.DIALER_PACKAGENAME) >= 0) {//DialtactsActivity start
//            intent.setComponent(new ComponentName(ExportProcessor.DIALER_PACKAGENAME,
//                    mCallingActivityName));
//        } else {// Contacts start
//            intent.setClassName(mContext, PeopleActivity.class.getName());
//        }
//        Log.i(TAG, "[onFinished] mCallingActivityName = " + mCallingActivityName +
//                ",intent = " + intent.toString());
//        //final Intent intent = new Intent(mContext, PeopleActivity.class);
//        //@}
//
//        final Notification notification = constructFinishNotification(mContext, title, description,
//                intent, statIconId);
//        /*prize-change-delete-contacts-no-show-notify-huangliemin-2016-6-2 start*/
//        //mNotificationManager.notify(DEFAULT_NOTIFICATION_TAG, jobId, notification);
//        if(requestType != MultiChoiceService.TYPE_DELETE) {
//        	mNotificationManager.notify(DEFAULT_NOTIFICATION_TAG, jobId, notification);
//        }
        // prize delete for bug 37801 by zhaojian 20171020 end
        /*prize-change-delete-contacts-no-show-notify-huangliemin-2016-6-2 end*/
        Log.d(TAG, "[onFinished] notify DEFAULT_NOTIFICATION_TAG");
    }

    synchronized void onFailed(final int requestType, final int jobId, final int total,
            final int succeeded, final int failed) {
        Log.i(TAG, "[onFailed] requestType =" + requestType + " jobId = " + jobId
                + " total = " + total + " succeeded = " + succeeded + " failed = " + failed);
        // prize modify for bug 41637 by zhaojian 20171104 start
//        final int titleId;
//        final int contentId;
//        if (requestType == MultiChoiceService.TYPE_DELETE) {
//            titleId = R.string.notifier_fail_delete_title;          
//            contentId = R.string.notifier_multichoice_process_report;     
//        } else {
//            titleId = R.string.notifier_fail_copy_title;            
//            contentId = R.string.notifier_multichoice_process_report;
//        }
//        /*
//         * Bug Fix by Mediatek Begin. Original Android's code: xxx CR ID:
//         * ALPS00251890 Descriptions:
//         */
//        /**
//         * M: fixed CR ALPS00783536 @{
//         */
//        ReportDialogInfo reportDialogInfo = new ReportDialogInfo();
//        reportDialogInfo.setmTitleId(titleId);
//        reportDialogInfo.setmContentId(contentId);
//        reportDialogInfo.setmJobId(jobId);
//        reportDialogInfo.setmTotalNumber(total);
//        reportDialogInfo.setmSucceededNumber(succeeded);
//        reportDialogInfo.setmFailedNumber(failed);
//        /** @} */
//        final Notification notification = constructReportNotification(mContext, reportDialogInfo);
//        /*
//         * Bug Fix by Mediatek End.
//         */
//        mNotificationManager.notify(DEFAULT_NOTIFICATION_TAG, jobId, notification);
//        Log.d(TAG, "[onFailed] onProcessed notify DEFAULT_NOTIFICATION_TAG");

        final String content;
        if (requestType == MultiChoiceService.TYPE_DELETE) {
        	content = mContext.getString(R.string.notifier_fail_delete_title);
        } else {
        	content = mContext.getString(R.string.notifier_fail_copy_title);
        }
        mHandler.obtainMessage(0, content + "," + mStrFinishAmount + succeeded).sendToTarget();
        /*prize-add for custom progressbar -hpf-2017-12-27-start*/
        Message msg = mHandler.obtainMessage();
        msg.arg1 = requestType;
        msg.what = CASE_HIDE_DIALOG;
        mHandler.sendMessage(msg);
        /*prize-add for custom progressbar -hpf-2017-12-27-end*/
        // prize modify for bug 41637 by zhaojian 20171104 end

    }

    synchronized void onFailed(final int requestType, final int jobId, final int total,
            final int succeeded, final int failed, final int errorCause) {
        Log.d(TAG, "[onFailed] requestType =" + requestType + " jobId = " + jobId
                + " total = " + total + " succeeded = " + succeeded + " failed = " + failed
                + " errorCause = " + errorCause + " ");
        // prize modify for bug 41637 by zhaojian 20171106 start
//        int titleId;
//        final int contentId;
//        if (requestType == MultiChoiceService.TYPE_DELETE) {
//            titleId = R.string.notifier_fail_delete_title;          
//            contentId = R.string.notifier_multichoice_process_report;
//        } else {
//            titleId = R.string.notifier_fail_copy_title;                
//            if (errorCause == ErrorCause.SIM_NOT_READY) {
//                int notifierFailureSimNotready = R.string.notifier_failure_sim_notready;        
//                contentId = notifierFailureSimNotready;
//            } else if (errorCause == ErrorCause.SIM_STORAGE_FULL) {
//                int notifierFailureBySimFull = R.string.notifier_failure_by_sim_full;          
//                contentId = notifierFailureBySimFull;
//            } else if (errorCause == ErrorCause.ERROR_USIM_EMAIL_LOST) {
//                if (failed == 0) {
//                    titleId = R.string.notifier_finish_copy_title;
//                }
//                contentId = R.string.error_import_usim_contact_email_lost;
//            } else {
//                contentId = R.string.notifier_multichoice_process_report;
//            }
//        }
//        /*
//         * Bug Fix by Mediatek Begin. Original Android's code: xxx CR ID:
//         * ALPS00251890 Descriptions:
//         */
//        /**
//         * M: fixed CR ALPS00783536 @{
//         */
//        ReportDialogInfo reportDialogInfo = new ReportDialogInfo();
//        reportDialogInfo.setmTitleId(titleId);
//        reportDialogInfo.setmContentId(contentId);
//        reportDialogInfo.setmJobId(jobId);
//        reportDialogInfo.setmTotalNumber(total);
//        reportDialogInfo.setmSucceededNumber(succeeded);
//        reportDialogInfo.setmFailedNumber(failed);
//        reportDialogInfo.setmErrorCauseId(errorCause);
//        /** @} */
//        final Notification notification = constructReportNotification(mContext, reportDialogInfo);
//        /*
//         * Bug Fix by Mediatek End.
//         */
//        mNotificationManager.notify(DEFAULT_NOTIFICATION_TAG, jobId, notification);
        
        /*prize-add for custom progressbar -hpf-2017-12-27-start*/
        String content = "";
        if (requestType == MultiChoiceService.TYPE_DELETE) {
        	content = mContext.getString(R.string.notifier_fail_delete_title);
        } else {
        	if (errorCause == ErrorCause.SIM_NOT_READY) {
        		content = mContext.getString(R.string.prize_notifier_failure_sim_notready);
        	} else if (errorCause == ErrorCause.SIM_STORAGE_FULL) {
        		content = mContext.getString(R.string.prize_notifier_failure_by_sim_full);
        	} else if (errorCause == ErrorCause.ERROR_USIM_EMAIL_LOST) {
        		content = mContext.getString(R.string.prize_error_import_usim_contact_email_lost);
        	}
        }
        mHandler.obtainMessage(0, content + "," + mStrFinishAmount + succeeded).sendToTarget();
        /*prize-add for custom progressbar -hpf-2017-12-27-start*/
        Message msg = mHandler.obtainMessage();
        msg.arg1 = requestType;
        msg.what = CASE_HIDE_DIALOG;
        mHandler.sendMessage(msg);
        /*prize-add for custom progressbar -hpf-2017-12-27-end*/
        // prize modify for bug 41637 by zhaojian 20171106 end
        Log.d(TAG, "[onFailed]onProcessed notify DEFAULT_NOTIFICATION_TAG");

    }

    synchronized void onCanceled(final int requestType, final int jobId, final int total,
            final int succeeded, final int failed) {
        Log.i(TAG, "[onCanceled] requestType =" + requestType + " jobId = " + jobId
                + " total = " + total + " succeeded = " + succeeded + " failed = " + failed);
        // prize modify for bug 41637 by zhaojian 20171106 start
//        final int titleId;
//        final int contentId;
//        if (requestType == MultiChoiceService.TYPE_DELETE) {
//            titleId = R.string.notifier_cancel_delete_title;        
//        } else {
//            titleId = R.string.notifier_cancel_copy_title;          
//        }
//        if (total != -1) {
//            contentId = R.string.notifier_multichoice_process_report;
//        } else {
//            contentId = -1;
//        }
//        /*
//         * Bug Fix by Mediatek Begin. Original Android's code: xxx CR ID:
//         * ALPS00251890 Descriptions:
//         */
//
//        /**
//         * M: fixed CR ALPS00783536 @{
//         */
//        ReportDialogInfo reportDialogInfo = new ReportDialogInfo();
//        reportDialogInfo.setmTitleId(titleId);
//        reportDialogInfo.setmContentId(contentId);
//        reportDialogInfo.setmJobId(jobId);
//        reportDialogInfo.setmTotalNumber(total);
//        reportDialogInfo.setmSucceededNumber(succeeded);
//        reportDialogInfo.setmFailedNumber(failed);
//        /** @} */
//        final Notification notification = constructReportNotification(mContext, reportDialogInfo);
//        /*
//         * Bug Fix by Mediatek End.
//         */
//        mNotificationManager.notify(DEFAULT_NOTIFICATION_TAG, jobId, notification);
//        Log.d(
//                TAG,
//                "[onCanceled]onProcessed notify DEFAULT_NOTIFICATION_TAG: "
//                        + mContext.getString(titleId));

        final String title;
        final String content;
        if (requestType == MultiChoiceService.TYPE_DELETE) {
            title = mContext.getString(R.string.notifier_cancel_delete_title);
        } else {
            title = mContext.getString(R.string.notifier_cancel_copy_title);
        }
        mHandler.obtainMessage(0, title+","+mStrFinishAmount+succeeded).sendToTarget();
        /*prize-add for custom progressbar -hpf-2017-12-27-start*/
        Message msg = mHandler.obtainMessage();
        msg.arg1 = requestType;
        msg.what = CASE_HIDE_DIALOG;
        mHandler.sendMessage(msg);
        /*prize-add for custom progressbar -hpf-2017-12-27-end*/
        // prize modify for bug 41637 by zhaojian 20171106 end
    }

    /*
     * Bug Fix by Mediatek Begin. Original Android's code: xxx CR ID:
     * ALPS00249590 Descriptions:
     */
    synchronized void onCanceling(final int requestType, final int jobId) {
        Log.i(TAG, "[onCanceling] requestType : " + requestType + " | jobId : " + jobId);
        // prize modify for bug 41637 by zhaojian 20171106 start
//        final String description;
//        int statIconId = 0;
//        if (requestType == MultiChoiceService.TYPE_DELETE) {
//            description = mContext.getString(R.string.multichoice_confirmation_title_delete);           
//            statIconId = android.R.drawable.ic_menu_delete;
//        } else {
//            description = "";
//        }
//
//        final Notification notification = constructCancelingNotification(mContext, description,
//                jobId, statIconId);
//        mNotificationManager.notify(DEFAULT_NOTIFICATION_TAG, jobId, notification);
//        Log.d(TAG, "[onCanceling] description: " + description);
        // prize modify for bug 41637 by zhaojian 20171106 end
    }

    /*
     * Bug Fix by Mediatek End.
     */
    /**
     * Constructs a Notification telling users the process is finished.
     *
     * @param context
     * @param title
     * @param description
     *            Content of the Notification
     * @param intent
     *            Intent to be launched when the Notification is clicked. Can be
     *            null.
     * @param statIconId
     */
    public static Notification constructFinishNotification(Context context, String title,
            String description, Intent intent, final int statIconId) {
        Log.i(TAG, "[constructFinishNotification] title : " + title + " | description : "
                + description + ",statIconId = " + statIconId);
        return new Notification.Builder(context)
                .setAutoCancel(true)
                .setSmallIcon(statIconId)
                .setContentTitle(title)
                .setContentText(description)
                .setTicker(title + "\n" + description)
                .setContentIntent(
                        PendingIntent.getActivity(context, 0, (intent != null ? intent
                                : new Intent()), 0)).getNotification();
    }

    /**
     * Constructs a {@link Notification} showing the current status of
     * import/export. Users can cancel the process with the Notification.
     *
     * @param context
     *            The service of MultichoiceService
     * @param requestType
     *            delete
     * @param description
     *            Content of the Notification.
     * @param tickerText
     * @param jobId
     * @param totalCount
     *            The number of vCard entries to be imported. Used to show
     *            progress bar. -1 lets the system show the progress bar with
     *            "indeterminate" state.
     * @param currentCount
     *            The index of current vCard. Used to show progress bar.
     * @param statIconId
     */
    public static Notification constructProgressNotification(Context context, int requestType,
            String description, String tickerText, int jobId, int totalCount, int currentCount,
            int statIconId) {
        Log.i(TAG, "[constructProgressNotification]requestType = " + requestType
                + ",description = " + description + ",tickerText = " + tickerText + ",jobId = "
                + jobId + ",totalCount = " + totalCount + ",currentCount = " + currentCount
                + ",statIconId = " + statIconId);
        Intent cancelIntent = new Intent(context, MultiChoiceConfirmActivity.class);
        cancelIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        cancelIntent.putExtra(MultiChoiceConfirmActivity.JOB_ID, jobId);
        cancelIntent.putExtra(MultiChoiceConfirmActivity.ACCOUNT_INFO, "TODO finish");
        cancelIntent.putExtra(MultiChoiceConfirmActivity.TYPE, requestType);

        final Notification.Builder builder = new Notification.Builder(context);
        // builder.setOngoing(true).setProgress(totalCount, currentCount,
        // totalCount == -1).setTicker(
        // tickerText).setContentTitle(description).setSmallIcon(statIconId).setContentIntent(
        // PendingIntent.getActivity(context, 0, cancelIntent,
        // PendingIntent.FLAG_UPDATE_CURRENT));
        builder.setOngoing(true)
                .setProgress(totalCount, currentCount, totalCount == -1)
                .setContentTitle(description)
                .setSmallIcon(statIconId)
                .setContentIntent(
                        PendingIntent.getActivity(context, jobId, cancelIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT));
        if (totalCount > 0) {
            builder.setContentText(context.getString(R.string.percentage,
                    String.valueOf(currentCount * 100 / totalCount)));
        }
        return builder.getNotification();
    }

    /**
     * Constructs a Notification telling users the process is canceled.
     *
     * @param context
     * @param description
     *            Content of the Notification
     */
    /*
     * Bug Fix by Mediatek Begin. Original Android's code: xxx CR ID:
     * ALPS00251890 Descriptions: add int jobId
     */

    public static Notification constructReportNotification(Context context,
            ReportDialogInfo reportDialogInfo) {
        Log.i(TAG, "[constructReportNotification]");
        Intent reportIntent = new Intent(context, MultiChoiceConfirmActivity.class);
        reportIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        reportIntent.putExtra(MultiChoiceConfirmActivity.REPORTDIALOG, true);
        reportIntent.putExtra(MultiChoiceConfirmActivity.REPORT_DIALOG_INFO, reportDialogInfo);

        /**
         * M: fixed CR ALPS00783536 @{
         */
        String title;
        String content;
        int titleId = reportDialogInfo.mTitleId;
        int contentId = reportDialogInfo.mContentId;
        int totalNumber = reportDialogInfo.mTotalNumber;
        int succeededNumber = reportDialogInfo.mSucceededNumber;
        int failedNumber = reportDialogInfo.mFailedNumber;
        int jobIdNumber = reportDialogInfo.mJobId;
        int errorCauseId = reportDialogInfo.mErrorCauseId;

        if ((errorCauseId == ErrorCause.ERROR_USIM_EMAIL_LOST) && (failedNumber == 0)) {
            title = context.getString(titleId);
        } else {
            title = context.getString(titleId, totalNumber);
        }

        if (contentId == -1) {
            content = "";
        } else {
            content = context.getString(contentId, succeededNumber, failedNumber);
        }
        /** @} */

        if (content == null || content.isEmpty()) {
            return new Notification.Builder(context)
                    .setAutoCancel(true)
                    .setSmallIcon(android.R.drawable.stat_notify_error)
                    .setContentTitle(title)
                    .setTicker(title)
                    .setContentIntent(
                            PendingIntent.getActivity(context, jobIdNumber, new Intent(),
                                    PendingIntent.FLAG_UPDATE_CURRENT)).getNotification();
        } else {
            return new Notification.Builder(context)
                    .setAutoCancel(true)
                    .setSmallIcon(android.R.drawable.stat_notify_error)
                    .setContentTitle(title)
                    .setContentText(content)
                    .setTicker(title + "\n" + content)
                    .setContentIntent(
                            PendingIntent.getActivity(context, jobIdNumber, reportIntent,
                                    PendingIntent.FLAG_UPDATE_CURRENT)).getNotification();
        }
    }

    /*
     * Bug Fix by Mediatek End.
     */
    /*
     * Bug Fix by Mediatek Begin. Original Android's code: xxx CR ID:
     * ALPS00249590 Descriptions:
     */
    public static Notification constructCancelingNotification(Context context, String description,
            int jobId, int statIconId) {
        Log.i(TAG, "[constructCancelingNotification]description = " + description
                + ",jobId = " + jobId + ",statIconId = " + statIconId);
        final Notification.Builder builder = new Notification.Builder(context);
        builder.setOngoing(true)
                .setProgress(-1, -1, true)
                .setContentTitle(description)
                .setSmallIcon(statIconId)
                .setContentIntent(
                        PendingIntent.getActivity(context, jobId, new Intent(),
                                PendingIntent.FLAG_UPDATE_CURRENT));

        return builder.getNotification();
    }

    /*
     * Bug Fix by Mediatek End.
     */

    // visible for test
    public void cancelAllNotifition() {
        //mNotificationManager.cancelAll();//prize-remove-custom progressbar -hpf-2017-12-27
        Log.i(TAG, "[cancelAllNotifition]");
    }

    /**
     * M: fixed CR ALPS00783536 @{
     */
    public static class ReportDialogInfo implements Parcelable {

        private int mTitleId;
        private int mContentId;
        private int mJobId;
        private int mErrorCauseId = -1;
        private int mTotalNumber;
        private int mSucceededNumber;
        private int mFailedNumber;

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(mTitleId);
            dest.writeInt(mContentId);
            dest.writeInt(mJobId);
            dest.writeInt(mErrorCauseId);
            dest.writeInt(mTotalNumber);
            dest.writeInt(mSucceededNumber);
            dest.writeInt(mFailedNumber);
        }

        public static final Parcelable.Creator<ReportDialogInfo> CREATOR =
                new Parcelable.Creator<ReportDialogInfo>() {
            public ReportDialogInfo createFromParcel(Parcel in) {
                final ReportDialogInfo values = new ReportDialogInfo();
                values.mTitleId = in.readInt();
                values.mContentId = in.readInt();
                values.mJobId = in.readInt();
                values.mErrorCauseId = in.readInt();
                values.mTotalNumber = in.readInt();
                values.mSucceededNumber = in.readInt();
                values.mFailedNumber = in.readInt();
                return values;
            }

            @Override
            public ReportDialogInfo[] newArray(int size) {
                return new ReportDialogInfo[size];
            }
        };

        public int getmTitleId() {
            return mTitleId;
        }

        public void setmTitleId(int titleId) {
            this.mTitleId = titleId;
        }

        public int getmContentId() {
            return mContentId;
        }

        public void setmContentId(int contentId) {
            this.mContentId = contentId;
        }

        public int getmJobId() {
            return mJobId;
        }

        public void setmJobId(int jobId) {
            this.mJobId = jobId;
        }

        public int getmErrorCauseId() {
            return mErrorCauseId;
        }

        public void setmErrorCauseId(int errorCauseId) {
            this.mErrorCauseId = errorCauseId;
        }

        public int getmTotalNumber() {
            return mTotalNumber;
        }

        public void setmTotalNumber(int totalNumber) {
            this.mTotalNumber = totalNumber;
        }

        public int getmSucceededNumber() {
            return mSucceededNumber;
        }

        public void setmSucceededNumber(int succeededNumber) {
            this.mSucceededNumber = succeededNumber;
        }

        public int getmFailedNumber() {
            return mFailedNumber;
        }

        public void setmFailedNumber(int failedNumber) {
            this.mFailedNumber = failedNumber;
        }
    }
    /** @ */
    
    /*prize-add for custom progressbar -hpf-2017-12-27-start*/
	private void showProgressDialog(String title) {
		Log.d(TAG, "[showProgressDialog]");

		/*prize add for bug58893 by zhaojian 20180524 start*/
        android.provider.Settings.System.putInt(mContext.getContentResolver(), PRIZE_DISABLE_NAVIGATION ,1);
        /*prize add for bug58893 by zhaojian 20180524 end*/

		hideWaittingDialog();
		if (!isActivityRunning(mContext)) return;
		View rootView = View.inflate(mContext, R.layout.prize_contacts_progress_dialog, null);
		mPg = (PrizeCirclePercentView) rootView.findViewById(R.id.custom_progressBar);
		mPg.setOnFinishListener(new OnFinishListener() {
			
			@Override
			public void onFinish() {
			    // prize add if-judge for bug 54060 by zhaojian 20180402 start
                if(ContactListMultiChoiceActivity.sIsActivityActive){
                    showWaittingDialog();
                }
                // prize add if-judge for bug 54060 by zhaojian 20180402 end
			}
		});
		mPgTvTitle = (TextView) rootView.findViewById(R.id.tv_title);
		mPgTvTitle.setText(title);
		AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
		builder.setView(rootView);
		builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				mShouldCancelJob = true;
				if (mOnJobCancelListener != null) {
					mOnJobCancelListener.onJobCancel(mDeleteJobId);
                    // prize add if-judge for bug 54060 by zhaojian 20180402 start
                    if(ContactListMultiChoiceActivity.sIsActivityActive){
                        showWaittingDialog();
                    }
                    // prize add if-judge for bug 54060 by zhaojian 20180402 end
                }
			}
		});
		mProgressDialog = builder.create();
		mProgressDialog.setCanceledOnTouchOutside(false);
		//mProgressDialog.getWindow().setType((WindowManager.LayoutParams.TYPE_SYSTEM_ALERT));
		mProgressDialog.setCancelable(false);
		mProgressDialog.show();
		Window dialogWindow = mProgressDialog.getWindow();
		WindowManager.LayoutParams p = dialogWindow.getAttributes();
		p.width = mDialogWidth;
		dialogWindow.setAttributes(p);

	}
	private void showWaittingDialog(){
		Log.d(TAG, "[showWaittingDialog]");
		hideProgressDialog();
		if(!isActivityRunning(mContext)) return;
		View rootView = View.inflate(mContext, R.layout.prize_contacts_progress_dialog_waitting, null);
		mWaittingProgressBar = (ProgressBar) rootView.findViewById(R.id.pg_waitting);
		TextView waittigTitle = (TextView) rootView.findViewById(R.id.tv_waitting_title);
		waittigTitle.setText(mContext.getResources().getString(R.string.prize_neatening));
		AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
		builder.setView(rootView);
		builder.setNegativeButton(R.string.prize_neaten_background, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				if (mRequestType != MultiChoiceService.TYPE_DELETE) {
	    			mContext.finish();	
	    		}
			}
		});
		mProgressDialogWaitting = builder.create();
		mProgressDialogWaitting.setCanceledOnTouchOutside(false);
		//mProgressDialogWaitting.getWindow().setType((WindowManager.LayoutParams.TYPE_SYSTEM_ALERT));
		mProgressDialogWaitting.setCancelable(false);
        // prize add if-judge for bug 54060 by zhaojian 20180402 start
        if(ContactListMultiChoiceActivity.sIsActivityActive) {
            mProgressDialogWaitting.show();
            Window dialogWindow = mProgressDialogWaitting.getWindow();
            WindowManager.LayoutParams p = dialogWindow.getAttributes();
            p.width = mDialogWidth;
            dialogWindow.setAttributes(p);
        }
        // prize add if-judge for bug 54060 by zhaojian 20180402 end
	}
	
	private void hideWaittingDialog() {
		if (mProgressDialogWaitting != null){
			Log.d(TAG, "[hideWaittingDialog]");
			mProgressDialogWaitting.dismiss();
		}
	}

	private void hideProgressDialog() {
		if (mProgressDialog != null){
			Log.d(TAG, "[hideProgressDialog]");
			mProgressDialog.dismiss();
		}
		/*prize add for bug58893 by zhaojian 20180524 start*/
        android.provider.Settings.System.putInt(mContext.getContentResolver(), PRIZE_DISABLE_NAVIGATION ,0);
		/*prize add for bug58893 by zhaojian 20180524 end*/
	}
	
	private OnJobCancelListener mOnJobCancelListener;
	public interface OnJobCancelListener{
		void onJobCancel(int jobId);
	}
	
	public void setOnJobCancelListener(OnJobCancelListener onJobCancelListener){
		mOnJobCancelListener = onJobCancelListener;
	}
	
	private boolean isActivityRunning(Activity activity) {
		String packageName = activity.getLocalClassName();
		android.util.Log.d(TAG, "[isActivityRunning]  packageName = " + packageName);    //prize modify Log by zhaojian 20180402
		if("com.mediatek.contacts.list.ContactListMultiChoiceActivity".equals(packageName)
				|| "common.vcard.ImportVCardActivity".equals(packageName)){
			Log.d(TAG, "[isActivityRunning] return true");
			return true;
		}
		
		Log.d(TAG, "[isActivityRunning] return false");
		return false;
	}
	/*prize-add for custom progressbar -hpf-2017-12-27-end*/
    
    
}
