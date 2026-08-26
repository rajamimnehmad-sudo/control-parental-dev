#!/bin/sh
set -eu

if [ "$#" -ne 2 ]; then
  echo "usage: $0 ARTIFACT_DIRECTORY EVENT_LOG" >&2
  exit 2
fi

task_root=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
task_artifacts=$1
task_events=$2
task_adb=${ANDROID_ADB:-/Users/yejielnehmad/Library/Android/sdk/platform-tools/adb}
task_extension_id=$(node "$task_root/verify-source.mjs" --print-id)
task_nonce=${GLOSH_EXTENSION_SESSION_NONCE:-$(openssl rand -hex 32)}
task_component="com.contentfilter.user.dev/com.contentfilter.user.chromeextension.ChromeExtensionPolicyLabReceiver"
task_server_pid=""
task_heartbeat_pid=""

restore() {
  if "$task_adb" get-state >/dev/null 2>&1; then
    "$task_adb" shell am broadcast \
      -a com.contentfilter.user.chromeextension.command.RESTORE \
      -n "$task_component" >/dev/null 2>&1 || true
    "$task_adb" reverse --remove tcp:8765 >/dev/null 2>&1 || true
  fi
  if [ -n "$task_heartbeat_pid" ]; then kill "$task_heartbeat_pid" >/dev/null 2>&1 || true; fi
  if [ -n "$task_server_pid" ]; then kill "$task_server_pid" >/dev/null 2>&1 || true; fi
}
trap restore EXIT HUP INT TERM

test -f "$task_artifacts/extension.crx"
test -f "$task_artifacts/update.xml"
"$task_adb" get-state >/dev/null
"$task_adb" reverse tcp:8765 tcp:8765

GLOSH_EXTENSION_ID="$task_extension_id" \
GLOSH_EXTENSION_SESSION_NONCE="$task_nonce" \
GLOSH_EXTENSION_ARTIFACT_DIR="$task_artifacts" \
GLOSH_EXTENSION_EVENT_LOG="$task_events" \
node "$task_root/bridge-server.mjs" &
task_server_pid=$!

task_ready=0
task_attempt=0
while [ "$task_attempt" -lt 20 ]; do
  if curl -fsS http://127.0.0.1:8765/health >/dev/null 2>&1; then task_ready=1; break; fi
  task_attempt=$((task_attempt + 1))
  sleep 1
done
test "$task_ready" -eq 1

"$task_adb" shell am broadcast \
  -a com.contentfilter.user.chromeextension.command.SNAPSHOT \
  -n "$task_component"
sleep 2
"$task_adb" shell am broadcast \
  -a com.contentfilter.user.chromeextension.command.APPLY \
  -n "$task_component" \
  --es extension_id "$task_extension_id" \
  --es update_url http://127.0.0.1:8765/update.xml \
  --el lease_millis 120000

(
  while kill -0 "$task_server_pid" >/dev/null 2>&1; do
    "$task_adb" shell am broadcast \
      -a com.contentfilter.user.chromeextension.command.HEARTBEAT \
      -n "$task_component" \
      --es extension_id "$task_extension_id" \
      --el lease_millis 120000 >/dev/null 2>&1 || exit 0
    sleep 15
  done
) &
task_heartbeat_pid=$!

printf 'policy_gate=active extensionId=%s events=%s\n' "$task_extension_id" "$task_events"
wait "$task_server_pid"
