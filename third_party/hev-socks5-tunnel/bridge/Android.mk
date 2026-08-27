LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := hev-socks5-tunnel
LOCAL_SRC_FILES := $(HEV_SOURCE_DIR)/libs/$(TARGET_ARCH_ABI)/libhev-socks5-tunnel.so
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := glosh-hev-bridge
LOCAL_SRC_FILES := hev_glosh_jni.c
LOCAL_C_INCLUDES := $(HEV_SOURCE_DIR)/include
LOCAL_SHARED_LIBRARIES := hev-socks5-tunnel
LOCAL_LDFLAGS += -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384
include $(BUILD_SHARED_LIBRARY)
