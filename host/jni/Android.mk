LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := libusbhost
LOCAL_SRC_FILES := libusbhost/libusbhost.so
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/libusbhost/include
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_ADDITIONAL_DEPENDENCIES := $(LOCAL_PATH)/Android.mk
LOCAL_MODULE_TAGS := optional
LOCAL_SRC_FILES := ipcclient.c aoaproxy.c
LOCAL_MODULE := aoaproxy
LOCAL_SHARED_LIBRARIES := libusbhost libcutils
include $(BUILD_EXECUTABLE)
