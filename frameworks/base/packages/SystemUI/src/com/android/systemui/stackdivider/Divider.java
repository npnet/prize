/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.systemui.stackdivider;

import android.content.res.Configuration;
import android.os.RemoteException;
import android.view.IDockedStackListener;
import android.view.LayoutInflater;
import android.view.View;

import com.android.systemui.R;
import com.android.systemui.SystemUI;
import com.android.systemui.recents.Recents;
import com.android.systemui.recents.misc.SystemServicesProxy;

import static android.content.res.Configuration.ORIENTATION_LANDSCAPE;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

import java.io.FileDescriptor;
import java.io.PrintWriter;

/**
 * Controls the docked stack divider.
 */
public class Divider extends SystemUI {
    private DividerWindowManager mWindowManager;
    private DividerView mView;
    private final DividerState mDividerState = new DividerState();
    private DockDividerVisibilityListener mDockDividerVisibilityListener;
    private boolean mVisible = false;
    private boolean mMinimized = false;
    private boolean mAdjustedForIme = false;
    private ForcedResizableInfoActivityController mForcedResizableController;

    @Override
    public void start() {
        mWindowManager = new DividerWindowManager(mContext);
        update(mContext.getResources().getConfiguration());
        putComponent(Divider.class, this);
        mDockDividerVisibilityListener = new DockDividerVisibilityListener();
        SystemServicesProxy ssp = Recents.getSystemServices();
        ssp.registerDockedStackListener(mDockDividerVisibilityListener);
        mForcedResizableController = new ForcedResizableInfoActivityController(mContext);
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        update(newConfig);
    }

    public DividerView getView() {
        return mView;
    }

    private void addDivider(Configuration configuration) {
        mView = (DividerView)
                LayoutInflater.from(mContext).inflate(R.layout.docked_stack_divider, null);
        mView.setVisibility(mVisible ? View.VISIBLE : View.INVISIBLE);
        final int size = mContext.getResources().getDimensionPixelSize(
                com.android.internal.R.dimen.docked_stack_divider_thickness);
        final boolean landscape = configuration.orientation == ORIENTATION_LANDSCAPE;
        final int width = landscape ? size : MATCH_PARENT;
        final int height = landscape ? MATCH_PARENT : size;
        mWindowManager.add(mView, width, height);
        mView.injectDependencies(mWindowManager, mDividerState);
    }

    private void removeDivider() {
        mWindowManager.remove();
    }

    private void update(Configuration configuration) {
        removeDivider();
        addDivider(configuration);
        if (mMinimized) {
            mView.setMinimizedDockStack(true);
            updateTouchable();
        }
    }

    private void updateVisibility(final boolean visible) {
        mView.post(new Runnable() {
            @Override
            public void run() {
                if (mVisible != visible) {
                    mVisible = visible;
                    mView.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);

                    // Update state because animations won't finish.
                    mView.setMinimizedDockStack(mMinimized);
                }
            }
        });
    }

    private void updateMinimizedDockedStack(final boolean minimized, final long animDuration) {
        mView.post(new Runnable() {
            @Override
            public void run() {
                if (mMinimized != minimized) {
                    mMinimized = minimized;
                    updateTouchable();
                    if (animDuration > 0) {
                        mView.setMinimizedDockStack(minimized, animDuration);
                    } else {
                        mView.setMinimizedDockStack(minimized);
                    }
                }
            }
        });
    }

    private void notifyDockedStackExistsChanged(final boolean exists) {
        mView.post(new Runnable() {
            @Override
            public void run() {
                mForcedResizableController.notifyDockedStackExistsChanged(exists);
            }
        });
    }

    private void updateTouchable() {
        mWindowManager.setTouchable(!mMinimized && !mAdjustedForIme);
    }

    @Override
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.print("  mVisible="); pw.println(mVisible);
        pw.print("  mMinimized="); pw.println(mMinimized);
        pw.print("  mAdjustedForIme="); pw.println(mAdjustedForIme);
    }

    class DockDividerVisibilityListener extends IDockedStackListener.Stub {

        @Override
        public void onDividerVisibilityChanged(boolean visible) throws RemoteException {
            updateVisibility(visible);
        }

        @Override
        public void onDockedStackExistsChanged(boolean exists) throws RemoteException {
            notifyDockedStackExistsChanged(exists);
        }

        @Override
        public void onDockedStackMinimizedChanged(boolean minimized, long animDuration)
                throws RemoteException {
            updateMinimizedDockedStack(minimized, animDuration);
        }

        @Override
        public void onAdjustedForImeChanged(boolean adjustedForIme, long animDuration)
                throws RemoteException {
            mView.post(() -> {
                if (mAdjustedForIme != adjustedForIme) {
                    mAdjustedForIme = adjustedForIme;
                    updateTouchable();
                    if (!mMinimized) {
                        if (animDuration > 0) {
                            mView.setAdjustedForIme(adjustedForIme, animDuration);
                        } else {
                            mView.setAdjustedForIme(adjustedForIme);
                        }
                    }
                }
            });
        }

        @Override
        public void onDockSideChanged(final int newDockSide) throws RemoteException {
            mView.post(() -> mView.notifyDockSideChanged(newDockSide));
        }
                /* prize-add-split screen-liyongli-20170708-start */
                @Override
                public void prizeOnFocusStackChanged(int focusedStackId, int lastFocusedStackId) throws RemoteException {
                }
                /* prize-add-split screen-liyongli-20170708-end */
                /* prize-add-split screen, return btn exit split screen-liyongli-20170724-start */
                @Override
                public void prizeOnRequestExitSplitScreen(int btnType, int focusedStackId) throws RemoteException {
                }
                /* prize-add-split screen, return btn exit split screen-liyongli-20170724-end */
    }
}
