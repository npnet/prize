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

package com.mediatek.camera.mode.pip.pipwrapping;

import android.graphics.SurfaceTexture;
import android.view.Surface;

/**
 * Recordable EGL window surface.
 * <p>
 * It's good practice to explicitly release() the surface, preferably from a
 * "finally" block. This object owns the Surface; releasing this object will
 * release the Surface as well.
 */
public class WindowSurface extends EglSurfaceBase {
    private Surface mSurface;

    /**
     * Associates an EGL surface with the native window surface. The Surface
     * will be owned by WindowSurface, and released when release() is called.
     */
    public WindowSurface(EglCore eglBase, Surface surface) {
        super(eglBase);
        createWindowSurface(surface);
        mSurface = surface;
    }

    /**
     * Associates an EGL surface with the SurfaceTexture.
     */
    public WindowSurface(EglCore eglBase, SurfaceTexture surfaceTexture) {
        super(eglBase);
        createWindowSurface(surfaceTexture);
    }

    /**
     * Releases any resources associated with the Surface and the EGL surface.
     */
    public void release() {
        releaseEglSurface();
        if (mSurface != null) {
            mSurface.release();
            mSurface = null;
        }
    }

    /**
     * Recreate the EGLSurface, using the new EglBase. The caller should have
     * already freed the old EGLSurface with releaseEglSurface().
     * <p>
     * This is useful when we want to update the EGLSurface associated with a
     * Surface. For example, if we want to share with a different EGLContext,
     * which can only be done by tearing down and recreating the context.
     * (That's handled by the caller; this just creates a new EGLSurface for the
     * Surface we were handed earlier.)
     * <p>
     * If the previous EGLSurface isn't fully destroyed, e.g. it's still current
     * on a context somewhere, the create call will fail with complaints from
     * the Surface about already being connected.
     */
    public void recreate(EglCore newEglBase) {
        if (mSurface == null) {
            throw new RuntimeException("not yet implemented for SurfaceTexture");
        }
        mEglBase = newEglBase; // switch to new context
        createWindowSurface(mSurface); // create new surface
    }

    //TODO
    /**
     * Get Surface for when surfaceView destroyed, we need stop use the surface
     */
    public Surface getSurface() {
        return mSurface;
    }
}
