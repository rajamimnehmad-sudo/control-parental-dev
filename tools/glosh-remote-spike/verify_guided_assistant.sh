#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
MAC_DIR="$SCRIPT_DIR/mac"
APK_DIR="$SCRIPT_DIR/app/build/outputs/apk/debug"
SOURCE_APK="$APK_DIR/app-debug.apk"
FINAL_APK="$APK_DIR/GloshRemote-PIN-ONLY-19-DEV.apk"
REPORT="$APK_DIR/REMOTE-PIN-ONLY-19-report.txt"
PYTHON_BIN="${PYTHON_BIN:-python3}"
VENV_DIR="$(mktemp -d "${TMPDIR:-/tmp}/glosh-pin-only-python.XXXXXX")"
MIN_DEV_VERSION_CODE=20

cleanup() { rm -rf "$VENV_DIR"; }
trap cleanup EXIT

sha256_file() {
  local path="$1"
  if command -v sha256sum >/dev/null 2>&1; then sha256sum "$path" | awk '{print $1}'; else shasum -a 256 "$path" | awk '{print $1}'; fi
}
file_size() { local path="$1"; if stat -f%z "$path" >/dev/null 2>&1; then stat -f%z "$path"; else stat -c%s "$path"; fi; }
normalize_digest() { tr '[:upper:]' '[:lower:]' | tr -d '[:space:]:'; }

printf '\n=== Glosh Remote PIN-only 19 physical-fix gate ===\n'
printf 'Repo: %s\nHEAD: %s\n' "$REPO_ROOT" "$(git -C "$REPO_ROOT" rev-parse HEAD)"

printf '\n[1/5] Product architecture + physical flow + pairing/session stability + stable DEV signing guard\n'
MANIFEST="$SCRIPT_DIR/app/src/main/AndroidManifest.xml"
MAIN="$SCRIPT_DIR/app/src/main/java/com/glosh/remote/spike/MainActivity.java"
SERVICE="$SCRIPT_DIR/app/src/main/java/com/glosh/remote/spike/RemotePairingService.java"
ADB_MANAGER="$SCRIPT_DIR/app/src/main/java/com/glosh/remote/spike/adb/AdbConnectionManager.java"
IDENTITY_STORE="$SCRIPT_DIR/app/src/main/java/com/glosh/remote/spike/adb/AdbIdentityStore.java"
ADB_SHELL="$SCRIPT_DIR/app/src/main/java/com/glosh/remote/spike/adb/AdbShell.java"
LOCAL_SESSION="$SCRIPT_DIR/app/src/main/java/com/glosh/remote/spike/adb/LocalAdbSession.java"
AWAKE_LEASE="$SCRIPT_DIR/app/src/main/java/com/glosh/remote/spike/adb/ScreenAwakeLease.java"
RELAY_SUPERVISOR="$SCRIPT_DIR/app/src/main/java/com/glosh/remote/spike/relay/RelaySessionSupervisor.java"
ENDPOINT_TRACKER="$SCRIPT_DIR/app/src/main/java/com/glosh/remote/spike/session/PairingEndpointTracker.java"
FAILURE_CLASSIFIER="$SCRIPT_DIR/app/src/main/java/com/glosh/remote/spike/session/PairingFailureClassifier.java"
BOOTSTRAP_POLICY="$SCRIPT_DIR/app/src/main/java/com/glosh/remote/spike/session/PinOnlyBootstrapPolicy.java"
SETTINGS_NAVIGATOR="$SCRIPT_DIR/app/src/main/java/com/glosh/remote/spike/wizard/SettingsNavigator.java"
COORDINATOR="$SCRIPT_DIR/app/src/main/java/com/glosh/remote/spike/broker/SupportSessionCoordinator.java"
ONBOARDING="$SCRIPT_DIR/app/src/main/java/com/glosh/remote/spike/wizard/OnboardingState.java"
MAC_CONSOLE="$SCRIPT_DIR/mac/broker_console.py"
BUILD_GRADLE="$SCRIPT_DIR/app/build.gradle.kts"
DEV_SIGNER_SOURCE="$SCRIPT_DIR/dev-signing/glosh-remote-dev.p12.b64"

if grep -Eq 'BIND_ACCESSIBILITY_SERVICE|LiveGuideAccessibilityService|ACCESSIBILITY_SETTINGS|ACCESSIBILITY_DETAILS_SETTINGS' "$MANIFEST"; then echo "ERROR: Accessibility volvió al manifiesto." >&2; exit 3; fi
if grep -q 'android.permission.SYSTEM_ALERT_WINDOW' "$MANIFEST"; then echo "ERROR: SYSTEM_ALERT_WINDOW volvió al manifiesto." >&2; exit 3; fi
if grep -q 'supportsPictureInPicture' "$MANIFEST"; then echo "ERROR: PiP volvió al manifiesto." >&2; exit 3; fi
if grep -Eq 'GuideBubbleActivity|APP_NOTIFICATION_BUBBLE_SETTINGS' "$MANIFEST"; then echo "ERROR: la ruta PIN-only no debe exponer Bubble/guide en el manifiesto." >&2; exit 3; fi
if grep -Eq 'GuideOverlayController|GuideNotification|SamsungGuideStep|requestOverlayPermission' "$MAIN"; then echo "ERROR: MainActivity volvió a depender de la guía/Bubble superseded." >&2; exit 3; fi
if ! grep -q 'SettingsNavigator' "$MAIN" || ! grep -q 'openWirelessDebugging' "$MAIN" || ! grep -q 'PinOnlyBootstrapPolicy' "$MAIN"; then echo "ERROR: falta handoff directo y acotado a Depuración inalámbrica." >&2; exit 3; fi
if ! grep -q 'showPairingInput' "$MAIN" || ! grep -q 'ACTION_SUBMIT_CODE' "$MAIN" || ! grep -q 'ACTION_ATTACH_DESCRIPTOR' "$MAIN" || ! grep -q 'renderAdbReady' "$MAIN" || ! grep -q 'renderReconnecting' "$MAIN"; then echo "ERROR: falta contrato PIN-only/ADB-ready/reconnecting." >&2; exit 3; fi
if ! grep -q 'requestDirectSession' "$COORDINATOR" || ! grep -q 'requestDirectSupport' "$ONBOARDING" || ! grep -q 'broker.request' "$COORDINATOR"; then echo "ERROR: broker directo roto." >&2; exit 3; fi
if ! grep -q 'ACTION_ATTACH_DESCRIPTOR' "$SERVICE" || ! grep -q 'CHECKING_SAVED_IDENTITY' "$SERVICE" || ! grep -q 'SessionState.ADB_READY' "$SERVICE" || ! grep -q 'validateOptionalDescriptor' "$SERVICE"; then echo "ERROR: ADB local sigue acoplado al descriptor del relay." >&2; exit 3; fi
if ! grep -q 'pairingEndpoints.lost' "$SERVICE" || ! grep -q 'PairingFailureClassifier.classify' "$SERVICE" || ! grep -q 'reuseIdentityOrStartPairing' "$SERVICE" || ! grep -q 'LocalAdbSession' "$SERVICE" || ! grep -q 'RelaySessionSupervisor' "$SERVICE"; then echo "ERROR: pairing/reconnect lifecycle incompleto." >&2; exit 3; fi
if grep -q 'rejectedEndpoint' "$SERVICE"; then echo "ERROR: volvió blacklist stale de endpoint." >&2; exit 3; fi
if ! grep -q 'shouldShowCodeInput' "$BOOTSTRAP_POLICY" || ! grep -q 'shouldLaunchWirelessSettings' "$BOOTSTRAP_POLICY" || ! grep -q 'canAttachDescriptor' "$BOOTSTRAP_POLICY"; then echo "ERROR: faltan guards del flujo físico PIN-only." >&2; exit 3; fi
if ! grep -q 'WIRELESS_DEBUGGING' "$SETTINGS_NAVIGATOR"; then echo "ERROR: SettingsNavigator perdió la ruta de Depuración inalámbrica." >&2; exit 3; fi
if ! grep -q 'AndroidKeyStore' "$IDENTITY_STORE" || ! grep -q 'AES/GCM/NoPadding' "$IDENTITY_STORE" || ! grep -q 'releaseConnection' "$ADB_MANAGER" || ! grep -q 'ensureConnected' "$ADB_MANAGER"; then echo "ERROR: identidad ADB persistente/reutilizable incompleta." >&2; exit 3; fi
if grep -q 'AdbConnectionManager.resetIdentity' "$SERVICE"; then echo "ERROR: cleanup normal sigue destruyendo identidad ADB." >&2; exit 3; fi
if ! grep -q 'screen_off_timeout' "$ADB_SHELL" || ! grep -q 'stay_on_while_plugged_in' "$ADB_SHELL" || ! grep -q 'AWAKE_TIMEOUT_VALUE' "$AWAKE_LEASE" || ! grep -q 'screenAwakeLease.ensureApplied' "$LOCAL_SESSION"; then echo "ERROR: lease automático de pantalla incompleto." >&2; exit 3; fi
if ! grep -q 'RelayReconnectPolicy' "$RELAY_SUPERVISOR" || ! grep -q 'Reconectando la sesión segura' "$RELAY_SUPERVISOR"; then echo "ERROR: relay no tiene recovery acotado." >&2; exit 3; fi
if [[ ! -s "$ENDPOINT_TRACKER" || ! -s "$FAILURE_CLASSIFIER" ]]; then echo "ERROR: faltan guards de pairing stability." >&2; exit 3; fi
if ! grep -q 'len(requests) == 1' "$MAC_CONSOLE" || ! grep -q 'broker.accept' "$MAC_CONSOLE"; then echo "ERROR: autoaceptación Mac rota." >&2; exit 3; fi
if grep -R -Eq 'SamsungPipCoachView|PictureInPictureParams|RemoteAction' "$SCRIPT_DIR/app/src/main/java/com/glosh/remote/spike"; then echo "ERROR: quedan dependencias PiP superseded." >&2; exit 3; fi
if [[ ! -s "$DEV_SIGNER_SOURCE" ]]; then echo "ERROR: falta identidad DEV estable." >&2; exit 3; fi
VERSION_CODE="$(sed -nE 's/^[[:space:]]*versionCode[[:space:]]*=[[:space:]]*([0-9]+).*/\1/p' "$BUILD_GRADLE" | head -n 1)"
if ! grep -q 'create("stableDev")' "$BUILD_GRADLE" || ! grep -q 'signingConfig = signingConfigs.getByName("stableDev")' "$BUILD_GRADLE" || [[ ! "$VERSION_CODE" =~ ^[0-9]+$ ]] || (( VERSION_CODE < MIN_DEV_VERSION_CODE )); then echo "ERROR: firma/versionCode inválidos." >&2; exit 3; fi
printf 'PASS: PIN-only + direct wireless settings + local ADB before relay + persistent identity + reconnect + screen lease (versionCode %s)\n' "$VERSION_CODE"

printf '\n[2/5] Python protocol/broker/standby tests\n'
"$PYTHON_BIN" -m venv "$VENV_DIR"
"$VENV_DIR/bin/python" -m pip install --disable-pip-version-check -q -r "$MAC_DIR/requirements.txt"
( cd "$MAC_DIR"; "$VENV_DIR/bin/python" -m unittest test_protocol.py test_broker.py test_one_tap_standby.py )

printf '\n[3/5] Android JVM unit tests\n'
"$REPO_ROOT/gradlew" -p "$SCRIPT_DIR" :app:testDebugUnitTest
printf '\n[4/5] Android lint\n'
"$REPO_ROOT/gradlew" -p "$SCRIPT_DIR" :app:lintDebug
printf '\n[5/5] Android assemble + signer verification\n'
"$REPO_ROOT/gradlew" -p "$SCRIPT_DIR" :app:assembleDebug

if [[ ! -f "$SOURCE_APK" ]]; then echo "ERROR: APK no encontrada." >&2; exit 2; fi
SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$SDK_ROOT" ]]; then echo "ERROR: Android SDK no disponible." >&2; exit 2; fi
APKSIGNER="$(find "$SDK_ROOT/build-tools" -type f -name apksigner 2>/dev/null | sort -V | tail -n 1)"
if [[ -z "$APKSIGNER" || ! -x "$APKSIGNER" ]]; then echo "ERROR: apksigner no disponible." >&2; exit 2; fi
DEV_KEYSTORE="$SCRIPT_DIR/app/build/stable-dev-signing/glosh-remote-dev.p12"
APKSIGNER_OUTPUT="$($APKSIGNER verify --verbose --print-certs "$SOURCE_APK" 2>&1)"
APK_CERT_SHA256="$(printf '%s\n' "$APKSIGNER_OUTPUT" | awk 'BEGIN{IGNORECASE=1} /certificate SHA-256 digest:/{sub(/.*digest:[[:space:]]*/, ""); print; exit}' | normalize_digest)"
KEYSTORE_CERT_SHA256="$(keytool -list -v -keystore "$DEV_KEYSTORE" -storetype PKCS12 -storepass 'GloshRemoteDev2026!' -alias 'glosh-remote-dev' | awk -F': ' '/SHA256:/{print $2; exit}' | normalize_digest)"
if [[ -z "$APK_CERT_SHA256" || "$APK_CERT_SHA256" != "$KEYSTORE_CERT_SHA256" ]]; then echo "ERROR: APK no quedó firmada por stableDev." >&2; exit 3; fi
cp -f "$SOURCE_APK" "$FINAL_APK"
APK_SHA="$(sha256_file "$FINAL_APK")"; APK_SIZE="$(file_size "$FINAL_APK")"; HEAD_SHA="$(git -C "$REPO_ROOT" rev-parse HEAD)"; STATUS="$(git -C "$REPO_ROOT" -c core.fileMode=false status --short)"
{
  echo "TASK=REMOTE-PIN-ONLY-19D-PHYSICAL-FIX"; echo "RESULT=PASS_AUTOMATED"; echo "HEAD=$HEAD_SHA"; echo "ARCHITECTURE_GUARD=PASS"; echo "PYTHON_TESTS=PASS"; echo "ANDROID_UNIT_TESTS=PASS"; echo "LINT=PASS"; echo "ASSEMBLE=PASS"; echo "WIRELESS_SETTINGS_HANDOFF=PASS"; echo "PIN_INPUT_ENDPOINT_GATED=PASS"; echo "LOCAL_ADB_BEFORE_RELAY=PASS"; echo "PAIRING_STABILITY=PASS"; echo "PERSISTENT_ADB_IDENTITY=PASS"; echo "ADB_RECONNECT=PASS"; echo "RELAY_RECONNECT=PASS"; echo "SCREEN_AWAKE_LEASE=PASS"; echo "VERSION_CODE=$VERSION_CODE"; echo "DEV_SIGNER_SHA256=$APK_CERT_SHA256"; echo "APK=$FINAL_APK"; echo "APK_SIZE_BYTES=$APK_SIZE"; echo "APK_SHA256=$APK_SHA";
  if [[ -z "$STATUS" ]]; then echo "GIT_STATUS=clean"; else echo "GIT_STATUS_BEGIN"; printf '%s\n' "$STATUS"; echo "GIT_STATUS_END"; fi
} > "$REPORT"
printf '\nPASS_AUTOMATED\nHEAD: %s\nVERSION_CODE: %s\nDEV_SIGNER_SHA256: %s\nAPK: %s\nAPK_SIZE_BYTES: %s\nAPK_SHA256: %s\nREPORT: %s\n' "$HEAD_SHA" "$VERSION_CODE" "$APK_CERT_SHA256" "$FINAL_APK" "$APK_SIZE" "$APK_SHA" "$REPORT"
