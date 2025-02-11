/*
 * Copyright (C) 2014 The Android Open Source Project
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

#include "DrawFrameTask.h"

#include <utils/Log.h>
#include <utils/Trace.h>

#include "../DeferredLayerUpdater.h"
#include "../DisplayList.h"
#include "../RenderNode.h"
#include "CanvasContext.h"
#include "RenderThread.h"

/// M: PerfBoost include
#include <dlfcn.h>
#include "PerfServiceNative.h"
#include "mediatek/MTKFrameBudgetIndicator.h"
void (*perfNotifyRenderTime)(float) = NULL;
typedef void (*notify_render_time)(float);
#define NANOS_TO_MILLIS_FLOAT(nanos) ((nanos) * 0.000001f)

namespace android {
namespace uirenderer {
namespace renderthread {

DrawFrameTask::DrawFrameTask()
        : mRenderThread(nullptr)
        , mContext(nullptr)
        , mSyncResult(SyncResult::OK) {

    /// M: PerfBoost
    void *handle, *func;
    handle = dlopen("libperfservicenative.so", RTLD_NOW);

    func = dlsym(handle, "PerfServiceNative_notifyRenderTime");
    perfNotifyRenderTime = reinterpret_cast<notify_render_time>(func);
    if (perfNotifyRenderTime == NULL) {
        ALOGE("PerfServiceNative_notifyRenderTime init fail!");
    }
    /// @}
}

DrawFrameTask::~DrawFrameTask() {
}

void DrawFrameTask::setContext(RenderThread* thread, CanvasContext* context,
        RenderNode* targetNode) {
    mRenderThread = thread;
    mContext = context;
    mTargetNode = targetNode;
}

void DrawFrameTask::pushLayerUpdate(DeferredLayerUpdater* layer) {
    LOG_ALWAYS_FATAL_IF(!mContext, "Lifecycle violation, there's no context to pushLayerUpdate with!");

    for (size_t i = 0; i < mLayers.size(); i++) {
        if (mLayers[i].get() == layer) {
            return;
        }
    }
    mLayers.push_back(layer);
}

void DrawFrameTask::removeLayerUpdate(DeferredLayerUpdater* layer) {
    for (size_t i = 0; i < mLayers.size(); i++) {
        if (mLayers[i].get() == layer) {
            mLayers.erase(mLayers.begin() + i);
            return;
        }
    }
}

int DrawFrameTask::drawFrame(TreeObserver* observer) {
    LOG_ALWAYS_FATAL_IF(!mContext, "Cannot drawFrame with no CanvasContext!");

    mSyncResult = SyncResult::OK;
    mSyncQueued = systemTime(CLOCK_MONOTONIC);
    mObserver = observer;
    postAndWait();

    return mSyncResult;
}

void DrawFrameTask::postAndWait() {
    AutoMutex _lock(mLock);
    mRenderThread->queue(this);
    mSignal.wait(mLock);
}

void DrawFrameTask::run() {
    ATRACE_NAME("DrawFrame");

    bool canUnblockUiThread;
    bool canDrawThisFrame;
    /// M: PerfBoost
    nsecs_t begin = systemTime(CLOCK_MONOTONIC);
    {
        TreeInfo info(TreeInfo::MODE_FULL, *mContext);
        info.observer = mObserver;
        canUnblockUiThread = syncFrameState(info);
        canDrawThisFrame = info.out.canDrawThisFrame;
    }

    // Grab a copy of everything we need
    CanvasContext* context = mContext;

    // From this point on anything in "this" is *UNSAFE TO ACCESS*
    if (canUnblockUiThread) {
        unblockUiThread();
    }

    if (CC_LIKELY(canDrawThisFrame)) {
        context->draw();
    } else {
        // wait on fences so tasks don't overlap next frame
        context->waitOnFences();
        notifyNoRender();
    }

    if (!canUnblockUiThread) {
        unblockUiThread();
    }

    /// M:PerfBoost include @{
    nsecs_t end = systemTime(CLOCK_MONOTONIC);
    if(perfNotifyRenderTime) perfNotifyRenderTime(NANOS_TO_MILLIS_FLOAT(end - begin));
    /// @}
}

bool DrawFrameTask::syncFrameState(TreeInfo& info) {
    ATRACE_CALL();
    int64_t vsync = mFrameInfo[static_cast<int>(FrameInfoIndex::Vsync)];
    mRenderThread->timeLord().vsyncReceived(vsync);
    bool canDraw = mContext->makeCurrent();
    Caches::getInstance().textureCache.resetMarkInUse(mContext);

    for (size_t i = 0; i < mLayers.size(); i++) {
        mLayers[i]->apply();
    }
    mLayers.clear();
    mContext->prepareTree(info, mFrameInfo, mSyncQueued, mTargetNode);

    // This is after the prepareTree so that any pending operations
    // (RenderNode tree state, prefetched layers, etc...) will be flushed.
    if (CC_UNLIKELY(!mContext->hasSurface() || !canDraw)) {
        if (!mContext->hasSurface()) {
            mSyncResult |= SyncResult::LostSurfaceRewardIfFound;
        } else {
            // If we have a surface but can't draw we must be stopped
            mSyncResult |= SyncResult::ContextIsStopped;
        }
        info.out.canDrawThisFrame = false;
    }

    if (info.out.hasAnimations) {
        if (info.out.requiresUiRedraw) {
            mSyncResult |= SyncResult::UIRedrawRequired;
        }
    }
    // If prepareTextures is false, we ran out of texture cache space
    return info.prepareTextures;
}

void DrawFrameTask::unblockUiThread() {
    AutoMutex _lock(mLock);
    ATRACE_CALL_L1();
    mSignal.signal();
}

} /* namespace renderthread */
} /* namespace uirenderer */
} /* namespace android */
