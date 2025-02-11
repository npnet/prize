package com.example.longshotscreen.manager;

import android.app.Notification;
import android.app.Notification.Builder;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import com.example.longshotscreen.utils.Log;

public class SuperShotNotificationManager {
	private static SuperShotNotificationManager mNoticeficationManager;
	private Context mContext;
	private NotificationManager mManager;

	public SuperShotNotificationManager(Context context) {
		mNoticeficationManager = this;
		this.mContext = context;
		this.mManager = ((NotificationManager) context.getSystemService("notification"));
	}

	public void reomveNoticeficationByID(int paramInt) {
		Log.i("SuperShotNotificationManager", "reomveNoticeficationByID "
				+ paramInt);
		if (this.mManager != null) {
			// return;
			this.mManager.cancel(paramInt);
		}
	}

	public void sendImageSaveNoticefication(NoticeParam paramNoticeParam) {
		Log.d("SuperShotNotificationManager","[sendImageSaveNoticefication]   paramNoticeParam = " + paramNoticeParam);
		/*prize-add for android N-fix bug[42769]-hpf-2017-11-22-start*/
		try{
			android.content.pm.PackageManager pm = mContext.getPackageManager();
			android.content.pm.ApplicationInfo ai = pm.getApplicationInfo("com.example.longshotscreen", android.content.pm.PackageManager.GET_ACTIVITIES);
            Log.d("SuperShotNotificationManager", "[onCreate]" + ai.uid);
			android.app.INotificationManager sINM = android.app.INotificationManager.Stub.asInterface(
					android.os.ServiceManager.getService(Context.NOTIFICATION_SERVICE));
			sINM.setNotificationsEnabledForPackage("com.example.longshotscreen", ai.uid, true);
		}catch(Exception e){
			e.printStackTrace();
		}
		/*prize-add for android N-fix bug[42769]-hpf-2017-11-22-end*/
		if (paramNoticeParam != null) {
			PendingIntent localPendingIntent = null;
			if (paramNoticeParam.getIntent() != null) {
				localPendingIntent = PendingIntent.getActivity(this.mContext,
						0, paramNoticeParam.getIntent(), 0);
			}
			Notification localNotification = new Notification.Builder(
					this.mContext).setAutoCancel(true)
					.setTicker(paramNoticeParam.getTicker())
					.setSmallIcon(paramNoticeParam.getIcon())
					.setContentTitle(paramNoticeParam.getTitle())
					.setContentText(paramNoticeParam.getContent())
					.setWhen(System.currentTimeMillis())
					//.setFullScreenIntent(localPendingIntent,false)//prize-remove-fix bug[46224]-hpf-2017-12-29
					.setContentIntent(localPendingIntent).build();
			if (this.mManager != null) {
				reomveNoticeficationByID(paramNoticeParam.getId());
				this.mManager.notify(paramNoticeParam.getId(),
						localNotification);
				if (paramNoticeParam.getId() != 10005) {
					reomveNoticeficationByID(10005);
				}
			}
		}
	}
}
