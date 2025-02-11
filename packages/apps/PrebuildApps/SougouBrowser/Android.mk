LOCAL_PATH:= $(call my-dir)

#add SougouBrowser start
ifeq ($(strip $(L_PBAPK_PRIZE_SOUGOU_BROWSER)), yes)
include $(CLEAR_VARS)
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE := SougouBrowser
LOCAL_MODULE_CLASS := APPS
LOCAL_CERTIFICATE := PRESIGNED
LOCAL_MODULE_SUFFIX := $(COMMON_ANDROID_PACKAGE_SUFFIX)
LOCAL_MODULE_PATH := $(TARGET_OUT)/vendor/operator/app
ifeq ($(strip $(PRIZE_CUSTOMER_NAME)), koobee)
LOCAL_SRC_FILES := ./koobee/SougouBrowser.apk
else
LOCAL_SRC_FILES := ./pcba/SougouBrowser.apk
endif
#LOCAL_DEX_PREOPT := false
LOCAL_MULTILIB := 32
include $(BUILD_PREBUILT)
endif

ifeq ($(strip $(L_PBAPK_PRIZE_SOUGOU_BROWSER_SYS)), yes)
include $(CLEAR_VARS)
# Module name should match apk name to be installed
LOCAL_MODULE := SougouBrowser
LOCAL_MODULE_TAGS := optional
ifeq ($(strip $(PRIZE_CUSTOMER_NAME)), koobee)
LOCAL_SRC_FILES := ./koobee/SougouBrowser.apk
else
LOCAL_SRC_FILES := ./pcba/SougouBrowser.apk
endif
LOCAL_MODULE_CLASS := APPS
LOCAL_MODULE_PATH := $(TARGET_OUT)/prebuilt-app
LOCAL_CERTIFICATE := PRESIGNED
LOCAL_EXTRACT_JNI_LIBS_PRIZE := yes
#LOCAL_DEX_PREOPT := false
LOCAL_MULTILIB := 32
include $(BUILD_PREBUILT)
endif


