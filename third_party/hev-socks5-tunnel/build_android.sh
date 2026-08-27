#!/bin/sh
set -eu

if [ "$#" -ne 2 ]; then
    echo "usage: $0 <ndk-root> <verified-hev-source-root>" >&2
    exit 2
fi

NDK_ROOT=$1
HEV_SOURCE=$2
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
REPO_ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)

check_commit() {
    actual=$(git -C "$1" rev-parse HEAD)
    if [ "$actual" != "$2" ]; then
        echo "commit mismatch: $1 expected=$2 actual=$actual" >&2
        exit 3
    fi
}

check_commit "$HEV_SOURCE" 9a06bc6e7989da54e3d32ff701ef7a7ce4995d3a
check_commit "$HEV_SOURCE/src/core" 162dd996299fc2d2bff2dd63728f8a2cd71ed31a
check_commit "$HEV_SOURCE/third-part/hev-task-system" 328f35d903221b51811b3d02b277d665dfbdc75f
check_commit "$HEV_SOURCE/third-part/lwip" 2a11c14c7a32887af25a034e82ef18b0b12076ac
check_commit "$HEV_SOURCE/third-part/yaml" efa36117a8646d26d12b58e05bac472d7854a70d

"$NDK_ROOT/ndk-build" \
    -C "$SCRIPT_DIR/source-build" \
    NDK_PROJECT_PATH=. \
    NDK_APPLICATION_MK=Application.mk \
    APP_BUILD_SCRIPT=Android.mk \
    HEV_SOURCE_DIR="$HEV_SOURCE" \
    NDK_OUT="$HEV_SOURCE/obj" \
    NDK_LIBS_OUT="$HEV_SOURCE/libs" \
    APP_MODULES=hev-socks5-tunnel

"$NDK_ROOT/ndk-build" \
    -C "$SCRIPT_DIR/bridge" \
    NDK_PROJECT_PATH=. \
    NDK_APPLICATION_MK=Application.mk \
    APP_BUILD_SCRIPT=Android.mk \
    HEV_SOURCE_DIR="$HEV_SOURCE"

for abi in arm64-v8a armeabi-v7a x86_64 x86; do
    destination="$REPO_ROOT/feature-vpn/src/main/jniLibs/$abi"
    mkdir -p "$destination"
    cp "$HEV_SOURCE/libs/$abi/libhev-socks5-tunnel.so" "$destination/"
    cp "$SCRIPT_DIR/bridge/libs/$abi/libglosh-hev-bridge.so" "$destination/"
done
