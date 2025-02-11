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

package com.android.systemui.recents.events.ui;

import com.android.systemui.recents.events.EventBus;
import com.android.systemui.recents.model.Task;
import com.android.systemui.recents.views.AnimationProps;
import com.android.systemui.recents.views.TaskView;

/**
 * This event is sent when a {@link TaskView} has been dismissed and is no longer visible.
 */
public class TaskViewDismissedEvent extends EventBus.Event {

    public final Task task;
    public final TaskView taskView;
    public final AnimationProps animation;
    public boolean forceStop = false; //prize add by xiarui 2017-12-16 for Bug#45761

    public TaskViewDismissedEvent(Task task, TaskView taskView, AnimationProps animation) {
        this.task = task;
        this.taskView = taskView;
        this.animation = animation;
    }

    //prize add by xiarui 2017-12-16 for Bug#45761 @{
    public TaskViewDismissedEvent(Task task, TaskView taskView, AnimationProps animation, boolean forceStop) {
        this.task = task;
        this.taskView = taskView;
        this.animation = animation;
        this.forceStop = forceStop;
    }
    //@}
}
