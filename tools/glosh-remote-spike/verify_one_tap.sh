#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
MAC_DIR="$SCRIPT_DIR/mac"
APK_DIR="$SCRIPT_DIR/app/build/outputs/apk/debug"
SOURCE_APK="$APK_DIR/app-debug.apk"
FINAL_APK="$APK_DIR/GloshRemote-Simple-Notification-DEV.apk"
REPORT="$APK_DIR/REMOTE-SIMPLE-NOTIFICATION-23-report.txt"
PYTHON_BIN="${PYTHON_BIN:-python3}"
PYTHON_VENV=""

cleanup() {
  if [[ -n "$PYTHON_VENV" && -d "$PYTHON_VENV" ]]; then
    rm -rf "$PYTHON_VENV"
  fi
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

prepare_python() {
  if ! command -v "$PYTHON_BIN" >/dev/null 2>&1; then
    echo "ERROR: no se encontró $PYTHON_BIN" >&2
    return 1
  fi
  if [[ ! -f "$MAC_DIR/requirements.txt" ]]; then
    echo "ERROR: falta $MAC_DIR/requirements.txt" >&2
    return 1
  fi

  PYTHON_VENV="$(mktemp -d "${TMPDIR:-/tmp}/glosh-one-tap-python.XXXXXX")"
  "$PYTHON_BIN" -m venv "$PYTHON_VENV"
  "$PYTHON_VENV/bin/python" -m pip install \
    --disable-pip-version-check \
    --no-input \
    -r "$MAC_DIR/requirements.txt"
}

printf '\n=== Glosh Remote Simple Notification gate ===\n'
printf 'Repo: %s\n' "$REPO_ROOT"
printf 'HEAD: %s\n' "$(git -C "$REPO_ROOT" rev-parse HEAD)"

printf '\n[0/5] Notification-only architecture guard\n'
"$PYTHON_BIN" "$SCRIPT_DIR/verify_simple_notification_architecture.py"

printf '\n[1/5] Python isolated environment\n'
prepare_python

printf '\n[2/5] Python protocol/broker/standby tests\n'
(
  cd "$MAC_DIR"
  "$PYTHON_VENV/bin/python" -m unittest \
    test_protocol.py \
    test_broker.py \
    test_one_tap_standby.py
)

printf '\n[3/5] Android JVM unit tests\n'
"$REPO_ROOT/gradlew" -p "$SCRIPT_DIR" :app:testDebugUnitTest

printf '\n[4/5] Android lint\n'
"$REPO_ROOT/gradlew" -p "$SCRIPT_DIR" :app:lintDebug

printf '\n[5/5] Android assemble\n'
"$REPO_ROOT/gradlew" -p "$SCRIPT_DIR" :app:assembleDebug

if [[ ! -f "$SOURCE_APK" ]]; then
  echo "ERROR: APK no encontrada en $SOURCE_APK" >&2
  exit 2
fi

cp -f "$SOURCE_APK" "$FINAL_APK"
APK_SHA="$(sha256_file "$FINAL_APK")"
APK_SIZE="$(file_size "$FINAL_APK")"
HEAD_SHA="$(git -C "$REPO_ROOT" rev-parse HEAD)"
STATUS="$(git -C "$REPO_ROOT" status --short)"

{
  echo "TASK=REMOTE-SIMPLE-NOTIFICATION-23"
  echo "RESULT=PASS"
  echo "HEAD=$HEAD_SHA"
  echo "ARCHITECTURE_GUARD=PASS"
  echo "PYTHON_ENV=isolated-venv"
  echo "PYTHON_TESTS=PASS"
  echo "ANDROID_UNIT_TESTS=PASS"
  echo "LINT=PASS"
  echo "ASSEMBLE=PASS"
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

printf '\nPASS\n'
printf 'HEAD: %s\n' "$HEAD_SHA"
printf 'APK: %s\n' "$FINAL_APK"
printf 'APK_SIZE_BYTES: %s\n' "$APK_SIZE"
printf 'APK_SHA256: %s\n' "$APK_SHA"
printf 'REPORT: %s\n' "$REPORT"
