#
# Copyright (C) 2014 MediaTek Inc.
# Modification based on code covered by the mentioned copyright
# and/or permission notice(s).
#
# Copyright 2011, The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
ifeq ($(PRIZE_GALLERY2_APP),V8)
LOCAL_PATH := $(call my-dir)

# Build the com.android.emailcommon static library. At the moment, this includes
# the emailcommon files themselves plus everything under src/org (apache code).  All of our
# AIDL files are also compiled into the static library

include $(CLEAR_VARS)

LOCAL_MODULE := com.android.gallery3d.common2
LOCAL_SRC_FILES := $(call all-java-files-under, src)
LOCAL_JAVA_LIBRARIES += org.apache.http.legacy
#LOCAL_SDK_VERSION := 16

include $(BUILD_STATIC_JAVA_LIBRARY)
endif
