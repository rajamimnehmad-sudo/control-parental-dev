#!/bin/sh
set -eu

if [ "$#" -ne 3 ]; then
  echo "usage: $0 ARTIFACT_DIRECTORY EVENT_LOG ACCESS_LOG" >&2
  exit 2
fi

task_root=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
task_artifacts=$1
task_events=$2
task_access=$3
task_adb=${ANDROID_ADB:-/Users/yejielnehmad/Library/Android/sdk/platform-tools/adb}
task_extension_id=$(node "$task_root/verify-source.mjs" --print-id)
task_nonce=${GLOSH_EXTENSION_SESSION_NONCE:-$(openssl rand -hex 32)}
task_component="com.contentfilter.user.dev/com.contentfilter.user.chromeextension.ChromeExtensionPolicyLabReceiver"
task_device_port=8765
task_host_port=${GLOSH_EXTENSION_HOST_PORT:-18765}
task_server_pid=""
task_heartbeat_pid=""
task_preflight=$(mktemp -d /tmp/glosh-13bp-preflight.XXXXXX)

restore() {
  if "$task_adb" get-state >/dev/null 2>&1; then
    "$task_adb" shell am broadcast \
      -a com.contentfilter.user.chromeextension.command.RESTORE \
      -n "$task_component" >/dev/null 2>&1 || true
    "$task_adb" reverse --remove "tcp:$task_device_port" >/dev/null 2>&1 || true
  fi
  if [ -n "$task_heartbeat_pid" ]; then kill "$task_heartbeat_pid" >/dev/null 2>&1 || true; fi
  if [ -n "$task_server_pid" ]; then kill "$task_server_pid" >/dev/null 2>&1 || true; fi
  rm -rf "$task_preflight"
}
trap restore EXIT HUP INT TERM

test -f "$task_artifacts/extension.crx"
test -f "$task_artifacts/update.xml"
"$task_adb" get-state >/dev/null
"$task_adb" reverse "tcp:$task_device_port" "tcp:$task_host_port"

GLOSH_EXTENSION_ID="$task_extension_id" \
GLOSH_EXTENSION_SESSION_NONCE="$task_nonce" \
GLOSH_EXTENSION_ARTIFACT_DIR="$task_artifacts" \
GLOSH_EXTENSION_EVENT_LOG="$task_events" \
GLOSH_EXTENSION_ACCESS_LOG="$task_access" \
GLOSH_EXTENSION_BRIDGE_PORT="$task_host_port" \
node "$task_root/bridge-server.mjs" &
task_server_pid=$!

task_ready=0
task_attempt=0
while [ "$task_attempt" -lt 20 ]; do
  if curl -fsS "http://127.0.0.1:$task_host_port/health" >/dev/null 2>&1; then task_ready=1; break; fi
  task_attempt=$((task_attempt + 1))
  sleep 1
done
test "$task_ready" -eq 1

task_device_health=$(
  "$task_adb" shell \
    "(printf 'GET /health HTTP/1.0\\r\\nHost: 127.0.0.1\\r\\nConnection: close\\r\\n\\r\\n'; sleep 3) | toybox nc -4 -w 5 -W 5 127.0.0.1 8765"
)
case "$task_device_health" in
  *ready*) printf 'device_health=ready\n' ;;
  *) echo "device health preflight failed" >&2; exit 1 ;;
esac

curl -fsS "http://127.0.0.1:$task_host_port/update.xml" -o "$task_preflight/update.xml"
curl -fsS "http://127.0.0.1:$task_host_port/extension.crx" -o "$task_preflight/extension.crx"
cmp "$task_artifacts/update.xml" "$task_preflight/update.xml"
cmp "$task_artifacts/extension.crx" "$task_preflight/extension.crx"
printf 'preflight_hashes\n'
shasum -a 256 "$task_preflight/update.xml" "$task_preflight/extension.crx"

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

printf 'policy_gate=active extensionId=%s events=%s access=%s\n' \
  "$task_extension_id" "$task_events" "$task_access"
wait "$task_server_pid"
