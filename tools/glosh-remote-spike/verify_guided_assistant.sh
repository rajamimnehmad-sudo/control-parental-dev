#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
MAC_DIR="$SCRIPT_DIR/mac"
APK_DIR="$SCRIPT_DIR/app/build/outputs/apk/debug"
SOURCE_APK="$APK_DIR/app-debug.apk"
FINAL_APK="$APK_DIR/GloshRemote-Samsung-Overlay-DEV.apk"
REPORT="$APK_DIR/REMOTE-SAMSUNG-OVERLAY-GUIDE-11-report.txt"
PYTHON_BIN="${PYTHON_BIN:-python3}"
VENV_DIR="$(mktemp -d "${TMPDIR:-/tmp}/glosh-samsung-overlay-python.XXXXXX")"
EXPECTED_DEV_CERT_SHA256="8c8c1d52c15b55b2239a8ba06f40de3d866a4b80d8a8700e237993b0495459f1"

cleanup() {
  rm -rf "$VENV_DIR"
}
trap cleanup EXIT

sha256_file() {
  local path="$1"
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$path" | awk '{print $1}'
  else
    shasum -a 256 "$path" | awk '{print $1}'
  fi
}

file_size() {
  local path="$1"
  if stat -f%z "$path" >/dev/null 2>&1; then
    stat -f%z "$path"
  else
    stat -c%s "$path"
  fi
}

printf '\n=== Glosh Remote Samsung custom overlay guide gate ===\n'
printf 'Repo: %s\n' "$REPO_ROOT"
printf 'HEAD: %s\n' "$(git -C "$REPO_ROOT" rev-parse HEAD)"

printf '\n[1/5] Product architecture guard\n'
MANIFEST="$SCRIPT_DIR/app/src/main/AndroidManifest.xml"
MAIN="$SCRIPT_DIR/app/src/main/java/com/glosh/remote/spike/MainActivity.java"
SERVICE="$SCRIPT_DIR/app/src/main/java/com/glosh/remote/spike/RemotePairingService.java"
SAMSUNG_STEP="$SCRIPT_DIR/app/src/main/java/com/glosh/remote/spike/wizard/SamsungGuideStep.java"
OVERLAY="$SCRIPT_DIR/app/src/main/java/com/glosh/remote/spike/wizard/GuideOverlayController.java"
BUILD_GRADLE="$SCRIPT_DIR/app/build.gradle.kts"
DEV_SIGNER_SOURCE="$SCRIPT_DIR/dev-signing/glosh-remote-dev.p12.b64"

if grep -Eq 'BIND_ACCESSIBILITY_SERVICE|LiveGuideAccessibilityService|ACCESSIBILITY_SETTINGS|ACCESSIBILITY_DETAILS_SETTINGS' "$MANIFEST"; then
  echo "ERROR: Accessibility volvió al manifiesto del instalador Samsung." >&2
  exit 3
fi
if grep -Eq 'GuideServiceStatus|LiveGuideRuntime|ACTION_CLICK|performAction|scrollForward|FreshNodeClickExecutor|FreshSettingsScrollExecutor' "$MAIN"; then
  echo "ERROR: MainActivity recuperó autoridad de Accessibility/click/scroll." >&2
  exit 3
fi
if grep -Eq 'LiveGuideRuntime|GuideStage' "$SERVICE"; then
  echo "ERROR: pairing service volvió a depender del runtime de Accessibility." >&2
  exit 3
fi
if ! grep -q 'android.permission.SYSTEM_ALERT_WINDOW' "$MANIFEST"; then
  echo "ERROR: falta el permiso explícito para la guía flotante propia." >&2
  exit 3
fi
if grep -q 'supportsPictureInPicture' "$MANIFEST"; then
  echo "ERROR: PiP volvió al manifiesto; la ruta vigente es overlay propio." >&2
  exit 3
fi
if ! grep -q 'TYPE_APPLICATION_OVERLAY' "$OVERLAY"; then
  echo "ERROR: la guía no usa TYPE_APPLICATION_OVERLAY." >&2
  exit 3
fi
if ! grep -q 'Settings.canDrawOverlays' "$OVERLAY"; then
  echo "ERROR: falta comprobación real del permiso de overlay." >&2
  exit 3
fi
if ! grep -q 'TOTAL_STEPS = 7' "$SAMSUNG_STEP"; then
  echo "ERROR: el contrato Samsung de 7 pasos cambió sin actualizar el gate." >&2
  exit 3
fi
if grep -R -Eq 'SamsungPipCoachView|PictureInPictureParams|RemoteAction' \
    "$SCRIPT_DIR/app/src/main/java/com/glosh/remote/spike"; then
  echo "ERROR: quedan dependencias de la UX PiP superseded." >&2
  exit 3
fi
if [[ ! -s "$DEV_SIGNER_SOURCE" ]]; then
  echo "ERROR: falta la identidad de firma DEV estable." >&2
  exit 3
fi
if ! grep -q 'create("stableDev")' "$BUILD_GRADLE" \
    || ! grep -q 'versionCode = 12' "$BUILD_GRADLE" \
    || ! grep -q 'signingConfig = signingConfigs.getByName("stableDev")' "$BUILD_GRADLE"; then
  echo "ERROR: build debug no está fijado a stableDev/versionCode 12." >&2
  exit 3
fi
printf 'PASS: Samsung-only + custom overlay + user-confirmed Settings + no Accessibility/PiP + stable DEV signing\n'

printf '\n[2/5] Python protocol/broker/standby tests\n'
"$PYTHON_BIN" -m venv "$VENV_DIR"
"$VENV_DIR/bin/python" -m pip install --disable-pip-version-check -q \
  -r "$MAC_DIR/requirements.txt"
(
  cd "$MAC_DIR"
  "$VENV_DIR/bin/python" -m unittest \
    test_protocol.py \
    test_broker.py \
    test_one_tap_standby.py
)

printf '\n[3/5] Android JVM unit tests\n'
"$REPO_ROOT/gradlew" -p "$SCRIPT_DIR" :app:testDebugUnitTest

printf '\n[4/5] Android lint\n'
"$REPO_ROOT/gradlew" -p "$SCRIPT_DIR" :app:lintDebug

printf '\n[5/5] Android assemble + stable signer verification\n'
"$REPO_ROOT/gradlew" -p "$SCRIPT_DIR" :app:assembleDebug

if [[ ! -f "$SOURCE_APK" ]]; then
  echo "ERROR: APK no encontrada en $SOURCE_APK" >&2
  exit 2
fi

SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$SDK_ROOT" ]]; then
  echo "ERROR: ANDROID_HOME/ANDROID_SDK_ROOT no disponible para verificar firma." >&2
  exit 2
fi
APKSIGNER="$(find "$SDK_ROOT/build-tools" -type f -name apksigner 2>/dev/null | sort -V | tail -n 1)"
if [[ -z "$APKSIGNER" || ! -x "$APKSIGNER" ]]; then
  echo "ERROR: apksigner no disponible para verificar la identidad DEV." >&2
  exit 2
fi
DEV_CERT_SHA256="$($APKSIGNER verify --print-certs "$SOURCE_APK" \
  | awk -F': ' '/Signer #1 certificate SHA-256 digest:/{gsub(/[[:space:]]/,"",$2); print tolower($2); exit}')"
if [[ "$DEV_CERT_SHA256" != "$EXPECTED_DEV_CERT_SHA256" ]]; then
  echo "ERROR: firma DEV inesperada: $DEV_CERT_SHA256" >&2
  exit 3
fi
printf 'PASS: stable DEV signer %s\n' "$DEV_CERT_SHA256"

cp -f "$SOURCE_APK" "$FINAL_APK"
APK_SHA="$(sha256_file "$FINAL_APK")"
APK_SIZE="$(file_size "$FINAL_APK")"
HEAD_SHA="$(git -C "$REPO_ROOT" rev-parse HEAD)"
STATUS="$(git -C "$REPO_ROOT" status --short)"

{
  echo "TASK=REMOTE-SAMSUNG-OVERLAY-GUIDE-11"
  echo "RESULT=PASS_AUTOMATED"
  echo "HEAD=$HEAD_SHA"
  echo "ARCHITECTURE_GUARD=PASS"
  echo "PYTHON_TESTS=PASS"
  echo "ANDROID_UNIT_TESTS=PASS"
  echo "LINT=PASS"
  echo "ASSEMBLE=PASS"
  echo "DEV_SIGNER_SHA256=$DEV_CERT_SHA256"
  echo "VERSION_CODE=12"
  echo "APK=$FINAL_APK"
  echo "APK_SIZE_BYTES=$APK_SIZE"
  echo "APK_SHA256=$APK_SHA"
  if [[ -z "$STATUS" ]]; then
    echo "GIT_STATUS=clean"
  else
    echo "GIT_STATUS_BEGIN"
    printf '%s\n' "$STATUS"
    echo "GIT_STATUS_END"
  fi
} > "$REPORT"

printf '\nPASS_AUTOMATED\n'
printf 'HEAD: %s\n' "$HEAD_SHA"
printf 'DEV_SIGNER_SHA256: %s\n' "$DEV_CERT_SHA256"
printf 'APK: %s\n' "$FINAL_APK"
printf 'APK_SIZE_BYTES: %s\n' "$APK_SIZE"
printf 'APK_SHA256: %s\n' "$APK_SHA"
printf 'REPORT: %s\n' "$REPORT"
