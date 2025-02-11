/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein is
 * confidential and proprietary to MediaTek Inc. and/or its licensors. Without
 * the prior written permission of MediaTek inc. and/or its licensors, any
 * reproduction, modification, use or disclosure of MediaTek Software, and
 * information contained herein, in whole or in part, shall be strictly
 * prohibited.
 * 
 * MediaTek Inc. (C) 2014. All rights reserved.
 * 
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER
 * ON AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL
 * WARRANTIES, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR
 * NONINFRINGEMENT. NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH
 * RESPECT TO THE SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY,
 * INCORPORATED IN, OR SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES
 * TO LOOK ONLY TO SUCH THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO.
 * RECEIVER EXPRESSLY ACKNOWLEDGES THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO
 * OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES CONTAINED IN MEDIATEK
 * SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK SOFTWARE
 * RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S
 * ENTIRE AND CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE
 * RELEASED HEREUNDER WILL BE, AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE
 * MEDIATEK SOFTWARE AT ISSUE, OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE
 * CHARGE PAID BY RECEIVER TO MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek
 * Software") have been modified by MediaTek Inc. All revisions are subject to
 * any receiver's applicable license agreements with MediaTek Inc.
 */
package com.android.camera.manager;

import android.view.View;
import android.widget.TextView;

import com.android.camera.CameraActivity;
import com.android.camera.Log;
import com.android.camera.R;

import com.mediatek.camera.mode.gyfacebeauty.GyBokehHelper;

/**
* gangyun tech add
*/
public class GyBokehManager extends ViewManager{
    private static final String TAG = "GYLog GyBokehManager";
    
    private android.widget.SeekBar mSeekBar;
    private static int mSmoothValue = -1;
    private static int mWhiteningValue = 0;
    private int  mWhitening = 3;
    private boolean isInPopupSetting = false;
    private CameraActivity mContext;
    private com.android.camera.ui.RotateLayout mRotateLayout;

    private GyBokehHelper gyBokehHelper;
	

	
    public GyBokehManager(CameraActivity context) {
       super(context);
	   mContext = context;
	   Log.d(TAG, "GyBokehManager");
       //gyBokehHelper = GyBokehHelper.getInstance(mContext);

    }
    
    @Override
    protected View getView() {
  
        //View view = inflate(R.layout.gy_levelseekbar);
		TextView view = new TextView(mContext);
		view.setVisibility(android.view.View.GONE);
        return view;
 
    }
    
    
    @Override
    protected void onRefresh() {
        Log.d(TAG, "gangyun tech onRefresh" );    	
   	
    }

@Override
public void show() {
	Log.d(TAG, "show");
    super.show();
	if (gyBokehHelper != null){
        //gyBokehHelper.gyBokehShow();
	}   
}


@Override
public void hide() {
	Log.d(TAG, "hide");
    super.hide();	
	if (gyBokehHelper != null){
       //gyBokehHelper.gyBokehHide();
	}

}

@Override
public void onRelease(){
	Log.d(TAG, "onRelease");
	if (gyBokehHelper != null){
		//gyBokehHelper.gyBokehclose();
	}
}

@Override
public void onOrientationChanged(int orientation) {
 //Log.d(TAG, "gangyun onOrientationChanged orientation ="+orientation );

}


}
