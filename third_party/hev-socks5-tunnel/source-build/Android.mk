TOP_PATH := $(HEV_SOURCE_DIR)

ifeq ($(filter $(modules-get-list),yaml),)
include $(TOP_PATH)/third-part/yaml/Android.mk
endif
ifeq ($(filter $(modules-get-list),lwip),)
include $(TOP_PATH)/third-part/lwip/Android.mk
endif
ifeq ($(filter $(modules-get-list),hev-task-system),)
include $(TOP_PATH)/third-part/hev-task-system/Android.mk
endif

LOCAL_PATH := $(TOP_PATH)
SRCDIR := $(LOCAL_PATH)/src
include $(LOCAL_PATH)/build.mk

# Glosh uses the stable C library API through its own JNI bridge. The optional
# upstream Android Java adapter hard-codes hev/htproxy/TProxyService in
# JNI_OnLoad and therefore must not be linked into this library variant.
HEV_SOCKS5_TUNNEL_SRC := $(patsubst $(SRCDIR)/%,src/%,$(SRCFILES))
HEV_SOCKS5_TUNNEL_SRC := $(filter-out \
    src/hev-jni.c \
    src/misc/hev-wintun.c \
    src/hev-tunnel-freebsd.c \
    src/hev-tunnel-macos.c \
    src/hev-tunnel-netbsd.c \
    src/hev-tunnel-windows.c, \
    $(HEV_SOCKS5_TUNNEL_SRC))
HEV_SOCKS5_TUNNEL_INCLUDES := \
    $(LOCAL_PATH)/src \
    $(LOCAL_PATH)/src/misc \
    $(LOCAL_PATH)/src/core/include \
    $(LOCAL_PATH)/third-part/yaml/include \
    $(LOCAL_PATH)/third-part/lwip/src/include \
    $(LOCAL_PATH)/third-part/lwip/src/ports/include \
    $(LOCAL_PATH)/third-part/hev-task-system/include

include $(CLEAR_VARS)
LOCAL_MODULE := hev-socks5-tunnel
LOCAL_SRC_FILES := $(HEV_SOCKS5_TUNNEL_SRC)
LOCAL_C_INCLUDES := $(HEV_SOCKS5_TUNNEL_INCLUDES)
LOCAL_CFLAGS += -DFD_SET_DEFINED -DSOCKLEN_T_DEFINED -DENABLE_LIBRARY
LOCAL_CFLAGS += $(VERSION_CFLAGS)
ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
LOCAL_CFLAGS += -mfpu=neon
endif
LOCAL_STATIC_LIBRARIES := yaml lwip hev-task-system
LOCAL_LDFLAGS += -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384
include $(BUILD_SHARED_LIBRARY)
