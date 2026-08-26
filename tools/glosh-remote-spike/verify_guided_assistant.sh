#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
MAC_DIR="$SCRIPT_DIR/mac"
APK_DIR="$SCRIPT_DIR/app/build/outputs/apk/debug"
SOURCE_APK="$APK_DIR/app-debug.apk"
FINAL_APK="$APK_DIR/GloshRemote-Samsung-Bubble-DEV.apk"
REPORT="$APK_DIR/REMOTE-SAMSUNG-BUBBLE-GUIDE-12-report.txt"
PYTHON_BIN="${PYTHON_BIN:-python3}"
VENV_DIR="$(mktemp -d "${TMPDIR:-/tmp}/glosh-samsung-bubble-python.XXXXXX")"
MIN_DEV_VERSION_CODE=14

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

normalize_digest() {
  tr '[:upper:]' '[:lower:]' | tr -d '[:space:]:'
}

printf '\n=== Glosh Remote Samsung system Bubble guide gate ===\n'
printf 'Repo: %s\n' "$REPO_ROOT"
printf 'HEAD: %s\n' "$(git -C "$REPO_ROOT" rev-parse HEAD)"

printf '\n[1/5] Product architecture + stable DEV signing guard\n'
MANIFEST="$SCRIPT_DIR/app/src/main/AndroidManifest.xml"
MAIN="$SCRIPT_DIR/app/src/main/java/com/glosh/remote/spike/MainActivity.java"
SERVICE="$SCRIPT_DIR/app/src/main/java/com/glosh/remote/spike/RemotePairingService.java"
SAMSUNG_STEP="$SCRIPT_DIR/app/src/main/java/com/glosh/remote/spike/wizard/SamsungGuideStep.java"
BUBBLE_NOTIFICATION="$SCRIPT_DIR/app/src/main/java/com/glosh/remote/spike/wizard/GuideNotification.java"
BUBBLE_ACTIVITY="$SCRIPT_DIR/app/src/main/java/com/glosh/remote/spike/GuideBubbleActivity.java"
FLOATING_CONTROLLER="$SCRIPT_DIR/app/src/main/java/com/glosh/remote/spike/wizard/GuideOverlayController.java"
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
if grep -q 'android.permission.SYSTEM_ALERT_WINDOW' "$MANIFEST"; then
  echo "ERROR: SYSTEM_ALERT_WINDOW volvió al manifiesto; Samsung Settings ocultó físicamente esa ruta." >&2
  exit 3
fi
if grep -q 'supportsPictureInPicture' "$MANIFEST"; then
  echo "ERROR: PiP volvió al manifiesto; la ruta PiP ya falló UX física." >&2
  exit 3
fi
if ! grep -q 'GuideBubbleActivity' "$MANIFEST" \
  || ! grep -q 'android:allowEmbedded="true"' "$MANIFEST" \
  || ! grep -q 'android:resizeableActivity="true"' "$MANIFEST"; then
  echo "ERROR: falta la Activity embebible/redimensionable requerida por Bubble." >&2
  exit 3
fi
if ! grep -q 'Notification.BubbleMetadata.Builder' "$BUBBLE_NOTIFICATION" \
  || ! grep -q 'setShortcutId' "$BUBBLE_NOTIFICATION" \
  || ! grep -q 'setConversationId' "$BUBBLE_NOTIFICATION"; then
  echo "ERROR: la notificación no cumple el contrato de Bubble/conversation Android 11+." >&2
  exit 3
fi
# The compact v17 Bubble intentionally removed the old full-size GuideOverlayView. Keep the
# architecture gate semantic: SystemUI Bubble, direct lifecycle-independent controls, and a
# bounded local six-digit ADB entry that submits through the existing pairing service contract.
if grep -q 'GuideOverlayView' "$BUBBLE_ACTIVITY" \
  || ! grep -q 'compactPanel' "$BUBBLE_ACTIVITY" \
  || ! grep -q 'handleBubbleBack' "$BUBBLE_ACTIVITY" \
  || ! grep -q 'handleBubbleNext' "$BUBBLE_ACTIVITY" \
  || ! grep -q 'ACTION_SUBMIT_CODE' "$BUBBLE_ACTIVITY" \
  || ! grep -q 'InputFilter.LengthFilter(6)' "$BUBBLE_ACTIVITY" \
  || ! grep -q '\\d{6}' "$BUBBLE_ACTIVITY"; then
  echo "ERROR: la Bubble compacta debe conservar controles directos y entrada local validada de 6 dígitos." >&2
  exit 3
fi
if grep -q 'TYPE_APPLICATION_OVERLAY' "$FLOATING_CONTROLLER"; then
  echo "ERROR: el controller volvió a usar TYPE_APPLICATION_OVERLAY." >&2
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
  echo "ERROR: falta la identidad DEV estable." >&2
  exit 3
fi
VERSION_CODE="$(sed -nE 's/^[[:space:]]*versionCode[[:space:]]*=[[:space:]]*([0-9]+).*/\1/p' "$BUILD_GRADLE" | head -n 1)"
if ! grep -q 'create("stableDev")' "$BUILD_GRADLE" \
  || ! grep -q 'signingConfig = signingConfigs.getByName("stableDev")' "$BUILD_GRADLE" \
  || [[ ! "$VERSION_CODE" =~ ^[0-9]+$ ]] \
  || (( VERSION_CODE < MIN_DEV_VERSION_CODE )); then
  echo "ERROR: Bubble debug debe usar stableDev y versionCode >= $MIN_DEV_VERSION_CODE (actual=${VERSION_CODE:-missing})." >&2
  exit 3
fi
printf 'PASS: Samsung-only + compact system Bubble + direct controls + local 6-digit entry + stable DEV signing (versionCode %s)\n' "$VERSION_CODE"

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

printf '\n[5/5] Android assemble + signer verification\n'
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
  echo "ERROR: apksigner no disponible." >&2
  exit 2
fi
DEV_KEYSTORE="$SCRIPT_DIR/app/build/stable-dev-signing/glosh-remote-dev.p12"
if [[ ! -f "$DEV_KEYSTORE" ]]; then
  echo "ERROR: keystore DEV decodificado no encontrado." >&2
  exit 2
fi

# Some apksigner revisions send certificate details through stderr. Capture both streams and
# parse the digest flexibly so the gate validates the certificate rather than its output channel.
APKSIGNER_OUTPUT="$($APKSIGNER verify --verbose --print-certs "$SOURCE_APK" 2>&1)"
APK_CERT_SHA256="$(printf '%s\n' "$APKSIGNER_OUTPUT" \
  | awk 'BEGIN{IGNORECASE=1} /certificate SHA-256 digest:/{sub(/.*digest:[[:space:]]*/, ""); print; exit}' \
  | normalize_digest)"
KEYSTORE_CERT_SHA256="$(keytool -list -v \
  -keystore "$DEV_KEYSTORE" \
  -storetype PKCS12 \
  -storepass 'GloshRemoteDev2026!' \
  -alias 'glosh-remote-dev' \
  | awk -F': ' '/SHA256:/{print $2; exit}' \
  | normalize_digest)"
if [[ -z "$APK_CERT_SHA256" || -z "$KEYSTORE_CERT_SHA256" \
  || "$APK_CERT_SHA256" != "$KEYSTORE_CERT_SHA256" ]]; then
  echo "ERROR: APK no quedó firmada por la identidad DEV estable." >&2
  echo "APK=$APK_CERT_SHA256 KEYSTORE=$KEYSTORE_CERT_SHA256" >&2
  printf '%s\n' "$APKSIGNER_OUTPUT" >&2
  exit 3
fi
printf 'PASS: stable DEV signer %s\n' "$APK_CERT_SHA256"

cp -f "$SOURCE_APK" "$FINAL_APK"
APK_SHA="$(sha256_file "$FINAL_APK")"
APK_SIZE="$(file_size "$FINAL_APK")"
HEAD_SHA="$(git -C "$REPO_ROOT" rev-parse HEAD)"
# CI chmods the gate script before execution. Ignore only file-mode changes so the report still
# fails visibly on any content/untracked modification.
STATUS="$(git -C "$REPO_ROOT" -c core.fileMode=false status --short)"

{
  echo "TASK=REMOTE-SAMSUNG-COMPACT-BUBBLE-CODE-17"
  echo "RESULT=PASS_AUTOMATED"
  echo "HEAD=$HEAD_SHA"
  echo "ARCHITECTURE_GUARD=PASS"
  echo "PYTHON_TESTS=PASS"
  echo "ANDROID_UNIT_TESTS=PASS"
  echo "LINT=PASS"
  echo "ASSEMBLE=PASS"
  echo "VERSION_CODE=$VERSION_CODE"
  echo "DEV_SIGNER_SHA256=$APK_CERT_SHA256"
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
printf 'VERSION_CODE: %s\n' "$VERSION_CODE"
printf 'DEV_SIGNER_SHA256: %s\n' "$APK_CERT_SHA256"
printf 'APK: %s\n' "$FINAL_APK"
printf 'APK_SIZE_BYTES: %s\n' "$APK_SIZE"
printf 'APK_SHA256: %s\n' "$APK_SHA"
printf 'REPORT: %s\n' "$REPORT"
