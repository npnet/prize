/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.layoutlib.bridge.android;

import com.android.internal.os.IResultReceiver;

import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.view.DragEvent;
import android.view.IWindow;

/**
 * Implementation of {@link IWindow} to pass to the AttachInfo.
 */
public final class BridgeWindow implements IWindow {

    @Override
    public void dispatchAppVisibility(boolean arg0) throws RemoteException {
        // pass for now.
    }

    @Override
    public void dispatchGetNewSurface() throws RemoteException {
        // pass for now.
    }

    @Override
    public void executeCommand(String arg0, String arg1, ParcelFileDescriptor arg2)
            throws RemoteException {
        // pass for now.
    }

    @Override
    public void resized(Rect rect, Rect rect2, Rect rect3, Rect rect4, Rect rect5, Rect rect6,
            boolean b, Configuration configuration, Rect rect7, boolean b2, boolean b3)
            throws RemoteException {
        // pass for now.
    }

    @Override
    public void moved(int arg0, int arg1) throws RemoteException {
        // pass for now.
    }

    @Override
    public void windowFocusChanged(boolean arg0, boolean arg1) throws RemoteException {
        // pass for now.
    }

    @Override
    public void dispatchWallpaperOffsets(float x, float y, float xStep, float yStep,
            boolean sync) {
        // pass for now.
    }

    @Override
    public void dispatchWallpaperCommand(String action, int x, int y,
            int z, Bundle extras, boolean sync) {
        // pass for now.
    }

    @Override
    public void closeSystemDialogs(String reason) {
        // pass for now.
    }

    @Override
    public void dispatchDragEvent(DragEvent event) {
        // pass for now.
    }

    @Override
    public void updatePointerIcon(float x, float y) {
        // pass for now
    }

    @Override
    public void dispatchSystemUiVisibilityChanged(int seq, int globalUi,
            int localValue, int localChanges) {
        // pass for now.
    }

    @Override
    public void dispatchWindowShown() {
    }

    @Override
    public void requestAppKeyboardShortcuts(
            IResultReceiver receiver, int deviceId) throws RemoteException {
    }

    @Override
    public IBinder asBinder() {
        // pass for now.
        return null;
    }

    /** M: Add API to enable/disable window log*/
    public void enableLog(boolean enable) {
        // pass for now.
    }

    /// M: add API to dump InputEvent status for ANR analysis @{
    public void dumpInputDispatchingStatus() {
        // pass for now.
    }
    /// @}
}
