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
package com.android.camera;

import java.util.List;

import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;

import com.android.camera.manager.ModePicker;

public class ModeChecker {
    private static final String TAG = "ModeChecker";

    private static final int UNKONW_ID = -1;
    private static final String[] MODE_STRING_NORMAL = new String[ModePicker.MODE_NUM_ALL - 1];
    private static final boolean[][] MATRIX_NORMAL_ENABLE = new boolean[ModePicker.MODE_NUM_ALL][];
    private static final boolean[][] MATRIX_PREVIEW3D_ENABLE =
            new boolean[ModePicker.MODE_NUM_ALL][];
    private static final boolean[][] MATRIX_SINGLE3D_ENABLE =
            new boolean[ModePicker.MODE_NUM_ALL][];

    static {
        MODE_STRING_NORMAL[ModePicker.MODE_PHOTO] = Parameters.CAPTURE_MODE_NORMAL;
        MODE_STRING_NORMAL[ModePicker.MODE_HDR] = Parameters.SCENE_MODE_HDR;
        MODE_STRING_NORMAL[ModePicker.MODE_FACE_BEAUTY] = Parameters.CAPTURE_MODE_FB;
        // Parameters.CAPTURE_MODE_PANORAMA_SHOT;
        MODE_STRING_NORMAL[ModePicker.MODE_PANORAMA] = "autorama";
        MODE_STRING_NORMAL[ModePicker.MODE_ASD] = Parameters.CAPTURE_MODE_ASD;

        // back front
        MATRIX_NORMAL_ENABLE[ModePicker.MODE_PHOTO] = new boolean[] { true, true };
        MATRIX_NORMAL_ENABLE[ModePicker.MODE_HDR] = new boolean[] { true, false };
        MATRIX_NORMAL_ENABLE[ModePicker.MODE_FACE_BEAUTY] = new boolean[] { true, true };
        MATRIX_NORMAL_ENABLE[ModePicker.MODE_PANORAMA] = new boolean[] { true, true };
        MATRIX_NORMAL_ENABLE[ModePicker.MODE_ASD] = new boolean[] { true, false };
        MATRIX_NORMAL_ENABLE[ModePicker.MODE_VIDEO] = new boolean[] { true, true };
        MATRIX_NORMAL_ENABLE[ModePicker.MODE_PHOTO_PIP] = new boolean[] { true, true };
        MATRIX_NORMAL_ENABLE[ModePicker.MODE_VIDEO_PIP] = new boolean[] { false, false };
        MATRIX_NORMAL_ENABLE[ModePicker.MODE_STEREO_CAMERA]     = new boolean[]{true, false};
        MATRIX_NORMAL_ENABLE[ModePicker.MODE_WATERMARK]    		= new boolean[] { true, true };
        MATRIX_NORMAL_ENABLE[ModePicker.MODE_PREVIDEO] = new boolean[] { true, true };
        MATRIX_NORMAL_ENABLE[ModePicker.MODE_DOUBLECAMERA] = new boolean[] { true, true };
		//gangyun tech add begin
        MATRIX_NORMAL_ENABLE[ModePicker.MODE_FACEART]     = new boolean[]{false, false};
	    MATRIX_NORMAL_ENABLE[ModePicker.MODE_BOKEH]     = new boolean[]{true, true}; 
		//gangyun tech add end
        /*arc add start*/
        MATRIX_NORMAL_ENABLE[ModePicker.MODE_PICTURE_ZOOM] = new boolean[] { true, true };
        MATRIX_NORMAL_ENABLE[ModePicker.MODE_LOWLIGHT_SHOT] = new boolean[] { true, true };
        /*arc add end*/
        MATRIX_NORMAL_ENABLE[ModePicker.MODE_SHORT_PREVIDEO] = new boolean[] { true, true };
        MATRIX_NORMAL_ENABLE[ModePicker.MODE_PORTRAIT] = new boolean[] { true, true };
        MATRIX_PREVIEW3D_ENABLE[ModePicker.MODE_PHOTO] = new boolean[] { true, false };
        MATRIX_PREVIEW3D_ENABLE[ModePicker.MODE_HDR] = new boolean[] { false, false };
        MATRIX_PREVIEW3D_ENABLE[ModePicker.MODE_FACE_BEAUTY] = new boolean[] { false, false };
        MATRIX_PREVIEW3D_ENABLE[ModePicker.MODE_PANORAMA] = new boolean[] { false, false };
        MATRIX_PREVIEW3D_ENABLE[ModePicker.MODE_ASD] = new boolean[] { false, false };
        MATRIX_PREVIEW3D_ENABLE[ModePicker.MODE_VIDEO] = new boolean[] { true, false };
        MATRIX_PREVIEW3D_ENABLE[ModePicker.MODE_PHOTO_PIP] = new boolean[] { false, false };

        MATRIX_SINGLE3D_ENABLE[ModePicker.MODE_PHOTO] = new boolean[] { true, false };
        MATRIX_SINGLE3D_ENABLE[ModePicker.MODE_HDR] = new boolean[] { false, false };
        MATRIX_SINGLE3D_ENABLE[ModePicker.MODE_FACE_BEAUTY] = new boolean[] { false, false };
        MATRIX_SINGLE3D_ENABLE[ModePicker.MODE_PANORAMA] = new boolean[] { true, false };
        MATRIX_SINGLE3D_ENABLE[ModePicker.MODE_ASD] = new boolean[] { false, false };
        MATRIX_SINGLE3D_ENABLE[ModePicker.MODE_VIDEO] = new boolean[] { false, false };
        MATRIX_SINGLE3D_ENABLE[ModePicker.MODE_PHOTO_PIP] = new boolean[] { false, false };
    }

    public static void updateModeMatrix(CameraActivity camera, int cameraId) {
        Log.i(TAG, "updateModeMatrix,cameraId = " + cameraId);
        List<String> supported = camera.getParameters().getSupportedCaptureMode();
        List<String> scenemode = camera.getParameters().getSupportedSceneModes();
        Log.d(TAG, "updateModeMatrix: scenemode = " + scenemode);
        if (FeatureSwitcher.isStereo3dEnable() && camera.isStereoMode()) {
            return;
        }
        // video modes can not judge form feature table
        for (int i = 0; i < ModePicker.MODE_VIDEO; i++) {
            if (MATRIX_NORMAL_ENABLE[i][cameraId] && supported != null && supported.indexOf(MODE_STRING_NORMAL[i]) < 0) {

                if (i != ModePicker.MODE_HDR) {
                    MATRIX_NORMAL_ENABLE[i][cameraId] = false;
                } else if (scenemode.indexOf(MODE_STRING_NORMAL[i]) < 0) {
                    MATRIX_NORMAL_ENABLE[i][cameraId] = false;
                }
            }
            Log.d(TAG, "Camera " + cameraId + "'s " + MODE_STRING_NORMAL[i] + " = "
                    + MATRIX_NORMAL_ENABLE[i][cameraId]);
        }

        MATRIX_NORMAL_ENABLE[ModePicker.MODE_PHOTO_PIP][cameraId] =
                ParametersHelper.isNativePIPSupported(camera.getParameters());

        // video mode judge from feature table
        if (cameraId == CameraInfo.CAMERA_FACING_BACK) {
            MATRIX_NORMAL_ENABLE[ModePicker.MODE_STEREO_CAMERA][cameraId] =
                    ParametersHelper.isImageRefocusSupported(camera.getParameters());
        }
        //if back camera or front camera is invalid, don't support pip
        if (CameraHolder.instance().getBackCameraId() == UNKONW_ID
                || CameraHolder.instance().getFrontCameraId() == UNKONW_ID) {
            MATRIX_NORMAL_ENABLE[ModePicker.MODE_PHOTO_PIP][cameraId] = false;
        }
        
        MATRIX_NORMAL_ENABLE[ModePicker.MODE_PANORAMA][cameraId] = true;
		/**********gangyun tech add*******/
	//MATRIX_NORMAL_ENABLE[ModePicker.MODE_VIDEO][cameraId] = true;
	    if(FeatureSwitcher.isGyBeautySupported())//FeatureOption.GANGYUN_CAMERA_BEAUTY
		{
	    MATRIX_NORMAL_ENABLE[ModePicker.MODE_FACEART][cameraId] = true;
	}
    else
	{
		    MATRIX_NORMAL_ENABLE[ModePicker.MODE_FACEART][cameraId] = false;
	}
      /***********end******************/ 
    }

    public static boolean getStereoPickerVisibile(CameraActivity camera) {
        if (!FeatureSwitcher.isStereo3dEnable()) {
            return false;
        }
        // if the intent's actions is "IMAGE_CAPTURE", stereo3D icons is
        // invisible.
        if (camera.isImageCaptureIntent()) {
            return false;
        }
        boolean visible = false;
        int mode = camera.getCurrentMode();
        int cameraId = camera.getCameraId();
        boolean[][] matrix3d;
        if (FeatureSwitcher.isStereoSingle3d()) {
            matrix3d = MATRIX_SINGLE3D_ENABLE;
        } else {
            matrix3d = MATRIX_PREVIEW3D_ENABLE;
        }

        int index = mode % 100;
        visible = matrix3d[index][cameraId] && MATRIX_NORMAL_ENABLE[index][cameraId];
        Log.d(TAG, "getStereoPickerVisibile(" + mode + ", " + cameraId + ") return " + visible);
        return visible;
    }

    public static boolean getCameraPickerVisible(CameraActivity camera, int cameraId) {
        int cameranum = camera.getCameraCount();
        if (cameranum < 2) {
            return false;
        }
        int mode = camera.getCurrentMode();
        boolean stereo = camera.isStereoMode();
        boolean[][] matrix;
        if (FeatureSwitcher.isStereoSingle3d() && stereo) {
            matrix = MATRIX_SINGLE3D_ENABLE;
        } else if (stereo) {
            matrix = MATRIX_PREVIEW3D_ENABLE;
        } else {
            matrix = MATRIX_NORMAL_ENABLE;
        }
        int index = mode % 100;
        boolean visible = matrix[index][cameraId] ;
        Log.d(TAG, "getCameraPickerVisible(" + mode + ", " + stereo + ") return " + visible);
        return visible;
    }

    public static boolean getModePickerVisible(CameraActivity camera, int cameraId, int mode) {
        cameraId = updateCameraId(camera, cameraId);
        boolean visible = false;
        boolean stereo = camera.isStereoMode();
        boolean[][] matrix;
        if (FeatureSwitcher.isStereoSingle3d() && stereo) {
            matrix = MATRIX_SINGLE3D_ENABLE;
        } else if (stereo) {
            matrix = MATRIX_PREVIEW3D_ENABLE;
        } else {
            matrix = MATRIX_NORMAL_ENABLE;
        }
        int index = mode % 100;
        visible = matrix[index][cameraId];
        if (ModePicker.MODE_VIDEO == mode || ModePicker.MODE_VIDEO_3D == mode) {
            visible = true;
        }
        Log.d(TAG, "getModePickerVisible(" + cameraId + ", " + mode + ", " + stereo + ") return "
                + visible);
        return visible;
    }

    /**
     * Return the number of modes supported by special camera.
     * @param camera The camera activity instance.
     * @param cameraId The id of camera.
     * @return Return the number of modes supported.
     */
    public static int modesShowInPicker(CameraActivity camera, int cameraId) {
        int count = 0;
        boolean stereo = camera.isStereoMode();
        boolean[][] matrix;
        if (FeatureSwitcher.isStereoSingle3d() && stereo) {
            matrix = MATRIX_SINGLE3D_ENABLE;
        } else if (stereo) {
            matrix = MATRIX_PREVIEW3D_ENABLE;
        } else {
            matrix = MATRIX_NORMAL_ENABLE;
        }
        for (int i = 0; i < ModePicker.MODE_NUM_ALL; i++) { // gangyun tech change MODE_VIDEO to MODE_NUM_ALL
            // asd, smile shot, hdr, video is not show in ModePicker, so filter
            // them
            if (matrix[i][cameraId]
                    && i != ModePicker.MODE_ASD
					&& i != ModePicker.MODE_VIDEO //gangyun tech add  MODE_VIDEO
                    && i != ModePicker.MODE_HDR) {
                count++;
            }
        }
        // If vfb is supported, face beauty mode will be not shown in mode picker,
        // so count decrease 1
        if (matrix[ModePicker.MODE_FACE_BEAUTY][cameraId]
                && FeatureSwitcher.isVfbEnable()) {
            count--;
        }
        return count;
    }

    private static int updateCameraId(CameraActivity camera, int cameraId) {
        // pip switched, should always show original cameraId's capture mode
        if (camera.isDualCameraDeviceEnable()) {
            return camera.getOriCameraId();
        }
        return cameraId;
    }
}
