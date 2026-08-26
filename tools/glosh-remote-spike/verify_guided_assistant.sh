#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
MAC_DIR="$SCRIPT_DIR/mac"
APK_DIR="$SCRIPT_DIR/app/build/outputs/apk/debug"
SOURCE_APK="$APK_DIR/app-debug.apk"
FINAL_APK="$APK_DIR/GloshRemote-Notification-PIN-DEV.apk"
REPORT="$APK_DIR/REMOTE-NOTIFICATION-PIN-20-report.txt"
PYTHON_BIN="${PYTHON_BIN:-python3}"
VENV_DIR="$(mktemp -d "${TMPDIR:-/tmp}/glosh-pin-only-python.XXXXXX")"
MIN_DEV_VERSION_CODE=20

# macOS may expose /usr/bin/java as a GUI-install stub even though Android Studio's
# JBR is available and Gradle can use it. apksigner/keytool need the same real JDK.
if ! java -version >/dev/null 2>&1 \
    && [[ -x "/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home/bin/java" ]]; then
  export JAVA_HOME="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
  export PATH="$JAVA_HOME/bin:$PATH"
elif ! java -version >/dev/null 2>&1 \
    && [[ -x "/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/java" ]]; then
  export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
  export PATH="$JAVA_HOME/bin:$PATH"
fi

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

printf '\n=== Glosh Remote notification-PIN gate ===\n'
printf 'Repo: %s\n' "$REPO_ROOT"
printf 'HEAD: %s\n' "$(git -C "$REPO_ROOT" rev-parse HEAD)"

printf '\n[1/5] Product architecture + stable DEV signing guard\n'
MANIFEST="$SCRIPT_DIR/app/src/main/AndroidManifest.xml"
MAIN="$SCRIPT_DIR/app/src/main/java/com/glosh/remote/spike/MainActivity.java"
SERVICE="$SCRIPT_DIR/app/src/main/java/com/glosh/remote/spike/RemotePairingService.java"
START_HANDOFF="$SCRIPT_DIR/app/src/main/java/com/glosh/remote/spike/session/ServiceStartHandoff.java"
COORDINATOR="$SCRIPT_DIR/app/src/main/java/com/glosh/remote/spike/broker/SupportSessionCoordinator.java"
ONBOARDING="$SCRIPT_DIR/app/src/main/java/com/glosh/remote/spike/wizard/OnboardingState.java"
MAC_CONSOLE="$SCRIPT_DIR/mac/broker_console.py"
BUILD_GRADLE="$SCRIPT_DIR/app/build.gradle.kts"
DEV_SIGNER_SOURCE="$SCRIPT_DIR/dev-signing/glosh-remote-dev.p12.b64"

if grep -Eq 'BIND_ACCESSIBILITY_SERVICE|LiveGuideAccessibilityService|ACCESSIBILITY_SETTINGS|ACCESSIBILITY_DETAILS_SETTINGS' "$MANIFEST"; then
  echo "ERROR: Accessibility volvió al manifiesto." >&2
  exit 3
fi
if grep -q 'android.permission.SYSTEM_ALERT_WINDOW' "$MANIFEST"; then
  echo "ERROR: SYSTEM_ALERT_WINDOW volvió al manifiesto." >&2
  exit 3
fi
if grep -q 'supportsPictureInPicture' "$MANIFEST"; then
  echo "ERROR: PiP volvió al manifiesto." >&2
  exit 3
fi
if grep -Eq 'GuideBubbleActivity|APP_NOTIFICATION_BUBBLE_SETTINGS' "$MANIFEST"; then
  echo "ERROR: la ruta PIN-only no debe exponer Bubble/guide en el manifiesto." >&2
  exit 3
fi
if grep -Eq 'GuideOverlayController|GuideNotification|SamsungGuideStep|SettingsNavigator|requestOverlayPermission' "$MAIN"; then
  echo "ERROR: MainActivity volvió a depender de la guía/Bubble superseded." >&2
  exit 3
fi
if grep -Eq 'showPairingInput|focusPairingInput|ACTION_SUBMIT_CODE|pendingPairingCode|ContextualPairingCodeDetector' "$MAIN"; then
  echo "ERROR: MainActivity volvió a aceptar/capturar el PIN fuera de la notificación ligada al endpoint." >&2
  exit 3
fi
if ! grep -q 'requestDirectSession' "$MAIN" \
  || ! grep -q 'POST_NOTIFICATIONS' "$MAIN" \
  || ! grep -q 'onRequestPermissionsResult' "$MAIN" \
  || ! grep -q 'CONECTAR CON SOPORTE' "$MAIN"; then
  echo "ERROR: falta el contrato notification-PIN: una acción, permiso explícito y broker directo." >&2
  exit 3
fi
if grep -q 'SERVICE_START_GRACE_MS' "$MAIN" \
  || ! grep -q 'ServiceStartHandoff.Decision.ACKNOWLEDGE' "$MAIN" \
  || ! grep -q 'ServiceStartHandoff.Decision.WAIT' "$MAIN" \
  || ! grep -q 'coordinator.descriptor()' "$MAIN" \
  || ! grep -q 'coordinator.markSessionStarted()' "$MAIN"; then
  echo "ERROR: el handoff broker -> foreground service volvió a depender de un timeout o perdió el acuse en dos fases." >&2
  exit 3
fi
if grep -Eq 'currentTimeMillis|elapsedRealtime|nanoTime' "$START_HANDOFF" \
  || ! grep -q 'Decision.DISPATCH' "$START_HANDOFF" \
  || ! grep -q 'Decision.WAIT' "$START_HANDOFF" \
  || ! grep -q 'Decision.ACKNOWLEDGE' "$START_HANDOFF" \
  || ! grep -q 'Decision.FINISH' "$START_HANDOFF"; then
  echo "ERROR: el contrato de handoff dejó de ser dirigido por estado y libre de reloj." >&2
  exit 3
fi
if ! grep -q 'requestDirectSession' "$COORDINATOR" \
  || ! grep -q 'requestDirectSupport' "$ONBOARDING" \
  || ! grep -q 'broker.request' "$COORDINATOR"; then
  echo "ERROR: el broker no se solicita directamente desde la ruta PIN-only." >&2
  exit 3
fi
if ! grep -q 'AdbMdns.SERVICE_TYPE_TLS_PAIRING' "$SERVICE" \
  || ! grep -q 'manager.pair(host, port, code)' "$SERVICE" \
  || ! grep -q 'connectTls' "$SERVICE" \
  || ! grep -q 'RelayClient' "$SERVICE" \
  || ! grep -q 'RemoteInput.Builder' "$SERVICE" \
  || ! grep -q 'ACTION_REPLY' "$SERVICE" \
  || ! grep -q 'VISIBILITY_SECRET' "$SERVICE"; then
  echo "ERROR: pairing ADB local/mDNS/relay dejó de cumplir el contrato seguro existente." >&2
  exit 3
fi
if ! grep -q 'len(requests) == 1' "$MAC_CONSOLE" \
  || ! grep -q 'broker.accept' "$MAC_CONSOLE"; then
  echo "ERROR: el operador Mac perdió la autoaceptación del cliente único." >&2
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
  echo "ERROR: notification-PIN debug debe usar stableDev y versionCode >= $MIN_DEV_VERSION_CODE (actual=${VERSION_CODE:-missing})." >&2
  exit 3
fi
printf 'PASS: notification RemoteInput + direct broker + local ADB pairing + Mac autoaccept + stable DEV signing (versionCode %s)\n' "$VERSION_CODE"

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
STATUS="$(git -C "$REPO_ROOT" -c core.fileMode=false status --short)"

{
  echo "TASK=REMOTE-NOTIFICATION-PIN-20"
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
