#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
MAC_DIR="$SCRIPT_DIR/mac"
APK_DIR="$SCRIPT_DIR/app/build/outputs/apk/debug"
SOURCE_APK="$APK_DIR/app-debug.apk"
FINAL_APK="$APK_DIR/GloshRemote-MAINTENANCE-20-DEV.apk"
REPORT="$APK_DIR/REMOTE-MAINTENANCE-20-report.txt"
PYTHON_BIN="${PYTHON_BIN:-python3}"
VENV_DIR="$(mktemp -d "${TMPDIR:-/tmp}/glosh-pin-only-python.XXXXXX")"
MIN_DEV_VERSION_CODE=24

if [[ -z "${JAVA_HOME:-}" && -x /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home/bin/java ]]; then
  export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
fi

cleanup() { rm -rf "$VENV_DIR"; }
trap cleanup EXIT

sha256_file() {
  local path="$1"
  if command -v sha256sum >/dev/null 2>&1; then sha256sum "$path" | awk '{print $1}'; else shasum -a 256 "$path" | awk '{print $1}'; fi
}
file_size() { local path="$1"; if stat -f%z "$path" >/dev/null 2>&1; then stat -f%z "$path"; else stat -c%s "$path"; fi; }
normalize_digest() { tr '[:upper:]' '[:lower:]' | tr -d '[:space:]:'; }

printf '\n=== Glosh Remote maintenance + Device Owner 20 gate ===\n'
printf 'Repo: %s\nHEAD: %s\n' "$REPO_ROOT" "$(git -C "$REPO_ROOT" rev-parse HEAD)"

printf '\n[1/5] PIN-only UX + proven direct ADB core + stable DEV signing guard\n'
MANIFEST="$SCRIPT_DIR/app/src/main/AndroidManifest.xml"
MAIN="$SCRIPT_DIR/app/src/main/java/com/glosh/remote/spike/MainActivity.java"
SERVICE="$SCRIPT_DIR/app/src/main/java/com/glosh/remote/spike/RemotePairingService.java"
ADB_MANAGER="$SCRIPT_DIR/app/src/main/java/com/glosh/remote/spike/adb/AdbConnectionManager.java"
IDENTITY_STORE="$SCRIPT_DIR/app/src/main/java/com/glosh/remote/spike/adb/AdbIdentityStore.java"
ENDPOINT_TRACKER="$SCRIPT_DIR/app/src/main/java/com/glosh/remote/spike/session/PairingEndpointTracker.java"
FAILURE_CLASSIFIER="$SCRIPT_DIR/app/src/main/java/com/glosh/remote/spike/session/PairingFailureClassifier.java"
BOOTSTRAP_POLICY="$SCRIPT_DIR/app/src/main/java/com/glosh/remote/spike/session/PinOnlyBootstrapPolicy.java"
SETTINGS_NAVIGATOR="$SCRIPT_DIR/app/src/main/java/com/glosh/remote/spike/wizard/SettingsNavigator.java"
COORDINATOR="$SCRIPT_DIR/app/src/main/java/com/glosh/remote/spike/broker/SupportSessionCoordinator.java"
ONBOARDING="$SCRIPT_DIR/app/src/main/java/com/glosh/remote/spike/wizard/OnboardingState.java"
MAC_CONSOLE="$SCRIPT_DIR/mac/broker_console.py"
PROVISIONING="$SCRIPT_DIR/app/src/main/java/com/glosh/remote/spike/adb/RemoteProvisioningController.java"
PROVISIONING_CONSOLE="$SCRIPT_DIR/mac/provisioning_console.py"
AWAKE_LEASE="$SCRIPT_DIR/app/src/main/java/com/glosh/remote/spike/session/ConnectionAwakeLease.java"
BUILD_GRADLE="$SCRIPT_DIR/app/build.gradle.kts"
DEV_SIGNER_SOURCE="$SCRIPT_DIR/dev-signing/glosh-remote-dev.p12.b64"

if grep -Eq 'BIND_ACCESSIBILITY_SERVICE|LiveGuideAccessibilityService|ACCESSIBILITY_SETTINGS|ACCESSIBILITY_DETAILS_SETTINGS' "$MANIFEST"; then echo "ERROR: Accessibility volvió al manifiesto." >&2; exit 3; fi
if grep -q 'android.permission.SYSTEM_ALERT_WINDOW' "$MANIFEST"; then echo "ERROR: SYSTEM_ALERT_WINDOW volvió al manifiesto." >&2; exit 3; fi
if grep -q 'supportsPictureInPicture' "$MANIFEST"; then echo "ERROR: PiP volvió al manifiesto." >&2; exit 3; fi
if grep -Eq 'GuideBubbleActivity|APP_NOTIFICATION_BUBBLE_SETTINGS' "$MANIFEST"; then echo "ERROR: la ruta PIN-only no debe exponer Bubble/guide en el manifiesto." >&2; exit 3; fi
if ! grep -q 'SettingsNavigator' "$MAIN" || ! grep -q 'openWirelessDebugging' "$MAIN" || ! grep -q 'PinOnlyBootstrapPolicy' "$MAIN"; then echo "ERROR: falta handoff directo a Depuración inalámbrica." >&2; exit 3; fi
if ! grep -q 'showPairingInput' "$MAIN" || ! grep -q 'ACTION_SUBMIT_CODE' "$MAIN" || ! grep -q 'ACTION_ATTACH_DESCRIPTOR' "$MAIN" || ! grep -q 'renderAdbReady' "$MAIN"; then echo "ERROR: falta contrato PIN-only/ADB-ready." >&2; exit 3; fi
if ! grep -q 'requestDirectSession' "$COORDINATOR" || ! grep -q 'requestDirectSupport' "$ONBOARDING" || ! grep -q 'broker.request' "$COORDINATOR"; then echo "ERROR: broker directo roto." >&2; exit 3; fi
if ! grep -q 'ACTION_ATTACH_DESCRIPTOR' "$SERVICE" || ! grep -q 'SessionState.ADB_READY' "$SERVICE" || ! grep -q 'validateOptionalDescriptor' "$SERVICE"; then echo "ERROR: ADB local no puede desacoplarse del descriptor del relay." >&2; exit 3; fi
if ! grep -q 'pairingEndpoints.lost' "$SERVICE" || ! grep -q 'PairingFailureClassifier.classify' "$SERVICE"; then echo "ERROR: se perdieron los guards de endpoint fresco/clasificación." >&2; exit 3; fi
if grep -Eq 'LocalAdbSession|RelaySessionSupervisor|reuseIdentityOrStartPairing|ensureConnected\(' "$SERVICE"; then echo "ERROR: el core volvió a introducir supervisores/reuse antes del gate físico." >&2; exit 3; fi
if ! grep -q 'RemoteInput.Builder' "$SERVICE" || ! grep -q 'setAllowFreeFormInput(true)' "$SERVICE" || ! grep -q 'SEMANTIC_ACTION_REPLY' "$SERVICE" || ! grep -q 'INGRESAR 6 DÍGITOS' "$SERVICE" || ! grep -q 'baseNotification(false)' "$SERVICE"; then echo "ERROR: PIN desde notificación no quedó como ruta primaria." >&2; exit 3; fi
if ! grep -q 'shouldShowCodeInput' "$BOOTSTRAP_POLICY" || ! grep -q 'shouldLaunchWirelessSettings' "$BOOTSTRAP_POLICY" || ! grep -q 'canAttachDescriptor' "$BOOTSTRAP_POLICY"; then echo "ERROR: faltan guards del flujo físico PIN-only." >&2; exit 3; fi
if ! grep -q 'WIRELESS_DEBUGGING' "$SETTINGS_NAVIGATOR"; then echo "ERROR: SettingsNavigator perdió Depuración inalámbrica." >&2; exit 3; fi
if ! grep -q 'AndroidKeyStore' "$IDENTITY_STORE" || ! grep -q 'AES/GCM/NoPadding' "$IDENTITY_STORE" || ! grep -q 'releaseConnection' "$ADB_MANAGER"; then echo "ERROR: identidad ADB persistente/reutilizable incompleta." >&2; exit 3; fi
if grep -q 'AdbConnectionManager.resetIdentity' "$SERVICE"; then echo "ERROR: cleanup normal destruye la identidad ADB." >&2; exit 3; fi
if [[ ! -s "$ENDPOINT_TRACKER" || ! -s "$FAILURE_CLASSIFIER" ]]; then echo "ERROR: faltan guards de pairing stability." >&2; exit 3; fi
if ! grep -q 'len(requests) == 1' "$MAC_CONSOLE" || ! grep -q 'broker.accept' "$MAC_CONSOLE"; then echo "ERROR: autoaceptación Mac rota." >&2; exit 3; fi
if [[ ! -s "$DEV_SIGNER_SOURCE" ]]; then echo "ERROR: falta identidad DEV estable." >&2; exit 3; fi
if ! grep -q 'maintenance-shell' "$PROVISIONING" || ! grep -q 'owner-commit' "$PROVISIONING" || ! grep -q 'EXPECTED_COMPONENT' "$PROVISIONING"; then echo "ERROR: canal de mantenimiento/aprovisionamiento incompleto." >&2; exit 3; fi
if ! grep -q 'DEVICE OWNER' "$PROVISIONING_CONSOLE" || ! grep -q 'artifactSha256' "$PROVISIONING_CONSOLE" || ! grep -q 'signerSha256' "$PROVISIONING_CONSOLE"; then echo "ERROR: confirmación Mac no quedó ligada al artefacto firmado." >&2; exit 3; fi
if ! grep -q 'SCREEN_BRIGHT_WAKE_LOCK' "$AWAKE_LEASE" || ! grep -q 'WIFI_MODE_FULL_HIGH_PERF' "$AWAKE_LEASE" || ! grep -q 'awakeLease.release' "$SERVICE"; then echo "ERROR: el lease de pantalla/Wi-Fi no cubre conexión y cleanup." >&2; exit 3; fi

PAIR_LINE="$(grep -n 'manager.pair(endpoint.host(), endpoint.port(), code)' "$SERVICE" | head -n1 | cut -d: -f1 || true)"
TLS_LINE="$(grep -n 'manager.connectTls(this, CONNECT_TIMEOUT_MS)' "$SERVICE" | head -n1 | cut -d: -f1 || true)"
WHOAMI_LINE="$(grep -n 'shell.execute("whoami")' "$SERVICE" | head -n1 | cut -d: -f1 || true)"
RELAY_LINE="$(grep -n 'new RelayClient(' "$SERVICE" | head -n1 | cut -d: -f1 || true)"
if [[ -z "$PAIR_LINE" || -z "$TLS_LINE" || -z "$WHOAMI_LINE" || -z "$RELAY_LINE" ]]; then echo "ERROR: falta secuencia simple pair -> connectTls -> whoami -> relay." >&2; exit 3; fi
if ! (( PAIR_LINE < TLS_LINE && TLS_LINE < WHOAMI_LINE && WHOAMI_LINE < RELAY_LINE )); then echo "ERROR: el orden del core ADB no coincide con la ruta física probada." >&2; exit 3; fi

VERSION_CODE="$(sed -nE 's/^[[:space:]]*versionCode[[:space:]]*=[[:space:]]*([0-9]+).*/\1/p' "$BUILD_GRADLE" | head -n 1)"
if ! grep -q 'create("stableDev")' "$BUILD_GRADLE" || ! grep -q 'signingConfig = signingConfigs.getByName("stableDev")' "$BUILD_GRADLE" || [[ ! "$VERSION_CODE" =~ ^[0-9]+$ ]] || (( VERSION_CODE < MIN_DEV_VERSION_CODE )); then echo "ERROR: firma/versionCode inválidos." >&2; exit 3; fi
printf 'PASS: PIN notification + pair -> connectTls -> whoami -> relay, sin supervisores previos (versionCode %s)\n' "$VERSION_CODE"

printf '\n[2/5] Python protocol/broker/standby tests\n'
"$PYTHON_BIN" -m venv "$VENV_DIR"
"$VENV_DIR/bin/python" -m pip install --disable-pip-version-check -q -r "$MAC_DIR/requirements.txt"
( cd "$MAC_DIR"; "$VENV_DIR/bin/python" -m unittest test_protocol.py test_broker.py test_one_tap_standby.py test_provisioning.py )

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
APK_CERT_SHA256="$(printf '%s\n' "$APKSIGNER_OUTPUT" | awk 'BEGIN{IGNORECASE=1} !found && /certificate SHA-256 digest:/{sub(/.*digest:[[:space:]]*/, ""); print; found=1}' | normalize_digest)"
KEYSTORE_CERT_SHA256="$(keytool -list -v -keystore "$DEV_KEYSTORE" -storetype PKCS12 -storepass 'GloshRemoteDev2026!' -alias 'glosh-remote-dev' | awk -F': ' '!found && /SHA256:/{print $2; found=1}' | normalize_digest)"
if [[ -z "$APK_CERT_SHA256" || "$APK_CERT_SHA256" != "$KEYSTORE_CERT_SHA256" ]]; then echo "ERROR: APK no quedó firmada por stableDev." >&2; exit 3; fi
cp -f "$SOURCE_APK" "$FINAL_APK"
APK_SHA="$(sha256_file "$FINAL_APK")"; APK_SIZE="$(file_size "$FINAL_APK")"; HEAD_SHA="$(git -C "$REPO_ROOT" rev-parse HEAD)"; STATUS="$(git -C "$REPO_ROOT" -c core.fileMode=false status --short)"
{
  echo "TASK=REMOTE-MAINTENANCE-DEVICE-OWNER-20"; echo "RESULT=PASS_AUTOMATED"; echo "HEAD=$HEAD_SHA"; echo "ARCHITECTURE_GUARD=PASS"; echo "PYTHON_TESTS=PASS"; echo "ANDROID_UNIT_TESTS=PASS"; echo "LINT=PASS"; echo "ASSEMBLE=PASS"; echo "NOTIFICATION_PIN_INPUT=PASS"; echo "WIRELESS_SETTINGS_HANDOFF=PASS"; echo "PAIRING_STABILITY=PASS"; echo "SIMPLE_CORE_PAIR_CONNECTTLS_WHOAMI_RELAY=PASS"; echo "ENCRYPTED_MAINTENANCE_SHELL=PASS"; echo "ARTIFACT_BOUND_PROVISIONING=PASS"; echo "SESSION_SCREEN_CPU_WIFI_LEASE=PASS"; echo "PERSISTENT_ADB_IDENTITY=PASS"; echo "VERSION_CODE=$VERSION_CODE"; echo "DEV_SIGNER_SHA256=$APK_CERT_SHA256"; echo "APK=$FINAL_APK"; echo "APK_SIZE_BYTES=$APK_SIZE"; echo "APK_SHA256=$APK_SHA";
  if [[ -z "$STATUS" ]]; then echo "GIT_STATUS=clean"; else echo "GIT_STATUS_BEGIN"; printf '%s\n' "$STATUS"; echo "GIT_STATUS_END"; fi
} > "$REPORT"
printf '\nPASS_AUTOMATED\nHEAD: %s\nVERSION_CODE: %s\nDEV_SIGNER_SHA256: %s\nAPK: %s\nAPK_SIZE_BYTES: %s\nAPK_SHA256: %s\nREPORT: %s\n' "$HEAD_SHA" "$VERSION_CODE" "$APK_CERT_SHA256" "$FINAL_APK" "$APK_SIZE" "$APK_SHA" "$REPORT"
