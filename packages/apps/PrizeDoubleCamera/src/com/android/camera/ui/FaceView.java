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

package com.android.camera.ui;

import java.util.ArrayList;

import android.R.integer;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.hardware.Camera.Face;
import android.hardware.Camera.Parameters;
import android.util.AttributeSet;
import android.view.View;

import com.android.camera.CameraActivity;
import com.android.camera.FeatureSwitcher;
import com.android.camera.Log;
import com.android.camera.R;
import com.android.camera.Util;
import com.android.camera.manager.FrameManager;


public class FaceView extends FrameView {
    private static final String TAG = "FaceView";
    private int mLastFaceNum;
    
    public FaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mFaceIndicator = mFaceStatusIndicator[FrameManager.FACE_FOCUSING];
    }
    
    public void setFaces(Face[] faces) {
        int num = faces.length;
        if (mPause || (num == 0 && mLastFaceNum == 0)) {
            return;
        }
        mFaces = faces;
        mLastFaceNum = num;
        if (num > 0 && mFocusIndicatorRotateLayout != null
                && mFocusIndicatorRotateLayout.isFocusing()) {
            mFocusIndicatorRotateLayout.clear();
        }
        invalidate();
    }
    
    public void setMirror(boolean mirror) {
        mMirror = mirror;
        Log.d(TAG, "mMirror=" + mirror);
    }
    
    public boolean faceExists() {
        return (mFaces != null && mFaces.length > 0);
    }
    
    @Override
    public void showStart() {
        mFaceIndicator = mFaceStatusIndicator[FrameManager.FACE_FOCUSING];
        invalidate();
    }
    
    // Ignore the parameter. No autofocus animation for face detection.
    @Override
    public void showSuccess(boolean timeout) {
        mFaceIndicator = mFaceStatusIndicator[FrameManager.FACE_FOCUSED];
        invalidate();
    }
    
    // Ignore the parameter. No autofocus animation for face detection.
    @Override
    public void showFail(boolean timeout) {
        mFaceIndicator = mFaceStatusIndicator[FrameManager.FACE_FOCUSFAILD];
        invalidate();
    }
    
    @Override
    public void clear() {
        // Face indicator is displayed during preview. Do not clear the
        // drawable.
        mFaceIndicator = mFaceStatusIndicator[FrameManager.FACE_FOCUSING];
        mFaces = null;
        invalidate();
    }
    
    public void enableFaceBeauty(boolean enable) {
        mEnableBeauty = enable;
        if (!mEnableBeauty) {
            mFaceIndicator = mFaceStatusIndicator[FrameManager.FACE_FOCUSING];
        }
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        Log.d(TAG, "onDraw,mEnableBeauty  =" + mEnableBeauty +",mFaces = " + mFaces);
        if (mFaces != null && mFaces.length > 0) {
            if (mEnableBeauty && mFaceIndicator == mFaceStatusIndicator[FrameManager.FACE_FOCUSING]) {
                mFaceIndicator = mFaceStatusIndicator[FrameManager.FACE_BEAUTY];
            }
            int rw, rh;
            rw = mContext.getUnCropWidth();
            rh = mContext.getUnCropHeight();
            if (((rh > rw) && ((mDisplayOrientation == 0) || (mDisplayOrientation == 180)))
                    || ((rh < rw) && ((mDisplayOrientation == 90) || (mDisplayOrientation == 270)))) {
                int temp = rw;
                rw = rh;
                rh = temp;
            }
            // Prepare the matrix.
            Util.prepareMatrix(mMatrix, mMirror, mDisplayOrientation, rw, rh);

            float dx = (getWidth() - rw) / 2;
            float dy = (getHeight() - rh) / 2;
            Matrix pointMatrix = new Matrix();
            float[] pointes = new float[] { dx, dy };
            // Focus indicator is directional. Rotate the matrix and the canvas
            // so it looks correctly in all orientations.
            canvas.save();
            mMatrix.postRotate(mOrientation); // postRotate is clockwise
            canvas.rotate(-mOrientation); // rotate is counter-clockwise (for
                                          // canvas)
            pointMatrix.postRotate(mContext.getOrientationCompensation());
            pointMatrix.mapPoints(pointes);
            for (int i = 0; i < mFaces.length; i++) {
                // Transform the coordinates.
                mRect.set(mFaces[i].rect);
                Util.dumpRect(mRect, "Original rect");
                mMatrix.mapRect(mRect);
                Util.dumpRect(mRect, "Transformed rect");
                // add for vFB -->begin
                if (mEnableBeauty && FeatureSwitcher.isVfbEnable()) {
                    // when score is 100,means the face have face beauty effect
                    // so will store the value to the array for when capture set
                    // the value to native
                    Log.i(TAG, "[vFB]onDraw,mFace[" + i + "] score = " + mFaces[i].score);
                    mFaceIndicator = mFaceStatusIndicator[FrameManager.FACE_BEAUTY];
                }
                mRect.offset(pointes[0], pointes[1]);
                // add for vFB <--end
                /*prize-modify-bugid:53182 faceview display error on back camera -xiaoping-20180319-start*/
                if (getContext() instanceof CameraActivity) {
                	Log.d(TAG, "getCameraId: "+((CameraActivity) getContext()).getCameraId());
    				if (((CameraActivity) getContext()).getCameraId() == 0) {
    					mFaceIndicator.setBounds(Math.round(mRect.left), Math.round(mRect.top), Math.round(mRect.right),
    							Math.round(mRect.bottom));
    				} else {
    					mFaceIndicator.setBounds(Math.round(mRect.left + 60), Math.round(mRect.top - 60),
    							Math.round(mRect.right) - 20, Math.round(mRect.bottom) - 60);
    				} 
				}
                /*prize-modify-bugid:53182 faceview display error on back camera -xiaoping-20180319-end*/
                mFaceIndicator.draw(canvas);
            }
            canvas.restore();
        }
        super.onDraw(canvas);
    }
}
