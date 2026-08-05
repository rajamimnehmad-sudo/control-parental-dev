#!/usr/bin/env bash
set -euo pipefail

readonly PACKAGE="com.contentfilter.dagbrowser.dev"
readonly LAB_PACKAGE="com.contentfilter.dagbrowser.lab"
readonly ACTIVITY="com.contentfilter.dagbrowser.DagBrowserActivity"
readonly MINIMUM_SDK="29"
readonly EXPECTED_ABI="arm64-v8a"

usage() {
  cat <<'EOF'
Usage: tools/dag_perf_lab/run_a23_fixture.sh --serial SERIAL [options]

Required:
  --serial SERIAL       Exact adb serial of the dedicated Android device.

Options:
  --port PORT           Local/reversed fixture port (default: 8765).
  --settle SECONDS      Observation window after launch (default: 24, max: 55).
  --swipes COUNT        Deterministic upward swipes for the lazy grid (default: 3).
  --warm                Do not force-stop DAG before launch.
  --output DIR          Explicit artifact directory.
  --expected-model NAME Require one exact model for a reproducible comparison.
  --lab                 Use isolated lab APK and HTTP loopback fixture.
  --help                Show this help.

Safety contract:
  - Never clears an app profile, cache or Logcat.
  - Never changes browser roles, Android settings or certificates.
  - Never invokes, stops or inspects Chrome.
  - Only stops/launches the selected DAG package and removes its own adb reverse.
EOF
}

serial=""
port="8765"
settle="24"
swipes="3"
cold="1"
expected_model=""
output_dir=""
lab="0"
package_name="$PACKAGE"
scheme="https"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --serial) serial="${2:-}"; shift 2 ;;
    --port) port="${2:-}"; shift 2 ;;
    --settle) settle="${2:-}"; shift 2 ;;
    --swipes) swipes="${2:-}"; shift 2 ;;
    --warm) cold="0"; shift ;;
    --output) output_dir="${2:-}"; shift 2 ;;
    --expected-model) expected_model="${2:-}"; shift 2 ;;
    --lab) lab="1"; package_name="$LAB_PACKAGE"; scheme="http"; shift ;;
    --help|-h) usage; exit 0 ;;
    *) echo "Unknown argument: $1" >&2; usage >&2; exit 2 ;;
  esac
done

if [[ -z "$serial" ]]; then
  echo "--serial is required; automatic device selection is intentionally disabled." >&2
  exit 2
fi
case "$serial" in
  *[!A-Za-z0-9._:-]*) echo "Invalid adb serial." >&2; exit 2 ;;
esac
case "$port" in *[!0-9]*|'') echo "Invalid port." >&2; exit 2 ;; esac
case "$settle" in *[!0-9]*|'') echo "Invalid settle value." >&2; exit 2 ;; esac
case "$swipes" in *[!0-9]*|'') echo "Invalid swipe count." >&2; exit 2 ;; esac
case "$expected_model" in *[!A-Za-z0-9._\ -]*) echo "Invalid expected model." >&2; exit 2 ;; esac
if (( port < 1024 || port > 65535 )); then echo "Port must be 1024..65535." >&2; exit 2; fi
if (( settle < 5 || settle > 55 )); then echo "Settle must be 5..55 seconds." >&2; exit 2; fi
if (( swipes < 0 || swipes > 8 )); then echo "Swipes must be 0..8." >&2; exit 2; fi
if (( settle < 4 + swipes )); then echo "Settle must cover the 4-second initial wait plus every swipe." >&2; exit 2; fi

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/../.." && pwd)"
if [[ -n "${ADB:-}" ]]; then
  adb_bin="$ADB"
elif [[ -n "${ANDROID_HOME:-}" && -x "${ANDROID_HOME}/platform-tools/adb" ]]; then
  adb_bin="${ANDROID_HOME}/platform-tools/adb"
elif command -v adb >/dev/null 2>&1; then
  adb_bin="$(command -v adb)"
elif [[ -x "$HOME/Library/Android/sdk/platform-tools/adb" ]]; then
  adb_bin="$HOME/Library/Android/sdk/platform-tools/adb"
else
  echo "adb not found; set ADB or ANDROID_HOME." >&2
  exit 1
fi

run_id="$(date -u +%Y%m%dT%H%M%SZ)-${serial}"
if [[ -z "$output_dir" ]]; then
  output_dir="$repo_root/.codex-tmp/dag-perf-lab/runs/$run_id"
fi
if [[ -d "$output_dir" ]] && [[ -n "$(find "$output_dir" -mindepth 1 -maxdepth 1 -print -quit)" ]]; then
  echo "Refusing to overwrite non-empty output directory: $output_dir" >&2
  exit 1
fi
mkdir -p "$output_dir"
output_dir="$(cd "$output_dir" && pwd)"
tls_dir="$repo_root/.codex-tmp/dag-perf-lab/tls"
mkdir -p "$tls_dir"

server_pid=""
logcat_pid=""
reverse_added="0"
cleanup() {
  local status=$?
  trap - EXIT INT TERM
  if [[ -n "$logcat_pid" ]] && kill -0 "$logcat_pid" 2>/dev/null; then
    kill "$logcat_pid" 2>/dev/null || true
    wait "$logcat_pid" 2>/dev/null || true
  fi
  if [[ -n "$server_pid" ]] && kill -0 "$server_pid" 2>/dev/null; then
    kill "$server_pid" 2>/dev/null || true
    wait "$server_pid" 2>/dev/null || true
  fi
  if [[ "$reverse_added" == "1" ]]; then
    "$adb_bin" -s "$serial" reverse --remove "tcp:$port" >/dev/null 2>&1 || true
  fi
  exit "$status"
}
trap cleanup EXIT INT TERM

state="$($adb_bin -s "$serial" get-state 2>/dev/null || true)"
if [[ "$state" != "device" ]]; then
  echo "Device $serial is not ready (state: ${state:-missing})." >&2
  exit 1
fi
model="$($adb_bin -s "$serial" shell getprop ro.product.model | tr -d '\r')"
sdk="$($adb_bin -s "$serial" shell getprop ro.build.version.sdk | tr -d '\r')"
abi="$($adb_bin -s "$serial" shell getprop ro.product.cpu.abi | tr -d '\r')"
if [[ ! "$sdk" =~ ^[0-9]+$ ]] || (( sdk < MINIMUM_SDK )); then
  echo "Refusing Android SDK '${sdk:-unknown}'; DAG requires API $MINIMUM_SDK or newer." >&2
  exit 1
fi
if [[ "$abi" != "$EXPECTED_ABI" ]]; then
  echo "Refusing ABI '${abi:-unknown}'; this DAG artifact requires $EXPECTED_ABI." >&2
  exit 1
fi
if [[ -n "$expected_model" && "$model" != "$expected_model" ]]; then
  echo "Refusing model '$model'; this run requires '$expected_model'." >&2
  exit 1
fi
if ! "$adb_bin" -s "$serial" shell pm path "$package_name" >/dev/null 2>&1; then
  echo "$package_name is not installed on $serial." >&2
  exit 1
fi
if "$adb_bin" -s "$serial" reverse --list | awk -v port="tcp:$port" \
  '$1 == port || $2 == port { found = 1 } END { exit(found ? 0 : 1) }'; then
  echo "Refusing to replace an existing adb reverse on $serial tcp:$port." >&2
  exit 1
fi

server_args=(
  --port "$port"
  --cert-dir "$tls_dir"
  --event-log "$output_dir/fixture-events.jsonl"
)
if [[ "$lab" == "1" ]]; then
  server_args+=(--http)
fi
python3 "$script_dir/fixture_server.py" "${server_args[@]}" \
  >"$output_dir/fixture-server.log" 2>&1 &
server_pid=$!
for _ in {1..50}; do
  if curl -kfsS --connect-timeout 1 "$scheme://127.0.0.1:$port/healthz" >/dev/null 2>&1; then
    break
  fi
  if ! kill -0 "$server_pid" 2>/dev/null; then
    echo "Fixture server exited; see $output_dir/fixture-server.log" >&2
    exit 1
  fi
  sleep 0.1
done
if ! curl -kfsS --connect-timeout 1 "$scheme://127.0.0.1:$port/healthz" >/dev/null 2>&1; then
  echo "Fixture server did not become ready." >&2
  exit 1
fi

"$adb_bin" -s "$serial" reverse "tcp:$port" "tcp:$port" >"$output_dir/adb-reverse.txt"
reverse_added="1"

{
  echo "schema=dag-controlled-perf-run-v1"
  echo "run_id=$run_id"
  echo "serial=$serial"
  echo "model=$model"
  echo "sdk=$sdk"
  echo "abi=$abi"
  echo "expected_model=${expected_model:-any-compatible}"
  echo "package=$package_name"
  echo "cold=$cold"
  echo "settle_seconds=$settle"
  echo "swipes=$swipes"
  echo "lab=$lab"
  echo "fixture_url=$scheme://localhost:$port/fixture/?run=$run_id"
  "$adb_bin" -s "$serial" shell getprop ro.build.version.release | tr -d '\r' | sed 's/^/android=/'
  "$adb_bin" -s "$serial" shell dumpsys package "$package_name" | sed -n 's/^[[:space:]]*versionCode=/versionCode=/p; s/^[[:space:]]*versionName=/versionName=/p' | head -2
} >"$output_dir/run-metadata.txt"

"$adb_bin" -s "$serial" shell dumpsys meminfo "$package_name" >"$output_dir/meminfo-before.txt" 2>&1 || true
"$adb_bin" -s "$serial" shell dumpsys thermalservice >"$output_dir/thermal-before.txt" 2>&1 || true
"$adb_bin" -s "$serial" shell dumpsys activity exit-info "$package_name" >"$output_dir/exit-info-before.txt" 2>&1 || true

run_start_epoch_seconds="$($adb_bin -s "$serial" shell date +%s | tr -d '\r')"
logcat_since="$($adb_bin -s "$serial" shell "date '+%m-%d %H:%M:%S.000'" | tr -d '\r')"
if [[ ! "$run_start_epoch_seconds" =~ ^[0-9]{10}$ ]]; then
  echo "Could not read a trustworthy epoch timestamp from the device." >&2
  exit 1
fi
if [[ ! "$logcat_since" =~ ^[0-9]{2}-[0-9]{2}\ [0-9]{2}:[0-9]{2}:[0-9]{2}\.[0-9]{3}$ ]]; then
  echo "Could not read a trustworthy Logcat timestamp from the device." >&2
  exit 1
fi
printf '%s\n' "$run_start_epoch_seconds" >"$output_dir/run-start-epoch-seconds.txt"
printf '%s\n' "$logcat_since" >"$output_dir/logcat-since.txt"
printf 'run_start_epoch_seconds=%s\nlogcat_since=%s\n' \
  "$run_start_epoch_seconds" "$logcat_since" >>"$output_dir/run-metadata.txt"

"$adb_bin" -s "$serial" logcat -T "$logcat_since" -v threadtime \
  -s DagPerfHarness:I DagPerformance:I DagMediaTransport:I '*:S' \
  >"$output_dir/logcat.txt" 2>&1 &
logcat_pid=$!
sleep 0.1
"$adb_bin" -s "$serial" shell log -p i -t DagPerfHarness "run_start=$run_id"

if [[ "$cold" == "1" ]]; then
  "$adb_bin" -s "$serial" shell am force-stop "$package_name"
fi
"$adb_bin" -s "$serial" shell dumpsys gfxinfo "$package_name" reset >"$output_dir/gfxinfo-reset.txt" 2>&1 || true

fixture_url="$scheme://localhost:$port/fixture/?run=$run_id"
"$adb_bin" -s "$serial" shell am start -W \
  -a android.intent.action.VIEW \
  -d "$fixture_url" \
  -n "$package_name/$ACTIVITY" \
  >"$output_dir/am-start.txt" 2>&1

sleep 4
screen_size="$($adb_bin -s "$serial" shell wm size | tr -d '\r' | sed -n 's/.*Physical size: //p' | tail -1)"
screen_width="${screen_size%x*}"
screen_height="${screen_size#*x}"
if [[ "$screen_width" =~ ^[0-9]+$ && "$screen_height" =~ ^[0-9]+$ ]]; then
  swipe_x=$((screen_width / 2))
  swipe_start=$((screen_height * 4 / 5))
  swipe_end=$((screen_height / 4))
  for ((index = 0; index < swipes; index += 1)); do
    "$adb_bin" -s "$serial" shell input swipe "$swipe_x" "$swipe_start" "$swipe_x" "$swipe_end" 420
    sleep 1
  done
else
  echo "Could not parse screen size; lazy-grid swipes skipped." >>"$output_dir/run-metadata.txt"
fi

remaining=$((settle - 4 - swipes))
if (( remaining > 0 )); then sleep "$remaining"; fi

"$adb_bin" -s "$serial" shell dumpsys gfxinfo "$package_name" >"$output_dir/gfxinfo-after.txt" 2>&1 || true
"$adb_bin" -s "$serial" shell dumpsys gfxinfo "$package_name" framestats >"$output_dir/gfxinfo-framestats.txt" 2>&1 || true
"$adb_bin" -s "$serial" shell dumpsys meminfo "$package_name" >"$output_dir/meminfo-after.txt" 2>&1 || true
"$adb_bin" -s "$serial" shell dumpsys thermalservice >"$output_dir/thermal-after.txt" 2>&1 || true
"$adb_bin" -s "$serial" shell dumpsys activity exit-info "$package_name" >"$output_dir/exit-info-after.txt" 2>&1 || true

if [[ -s "$output_dir/fixture-events.jsonl" ]]; then
  "$adb_bin" -s "$serial" exec-out screencap -p >"$output_dir/fixture-screen.png" 2>/dev/null || true
else
  echo "Fixture emitted no event. A fresh DAG profile may require a one-time Gecko certificate exception." >&2
  echo "No screenshot was captured, preventing accidental capture of a non-fixture page." >&2
fi

kill "$logcat_pid" 2>/dev/null || true
wait "$logcat_pid" 2>/dev/null || true
logcat_pid=""
python3 "$script_dir/summarize_run.py" "$output_dir" --output "$output_dir/summary.json" >/dev/null

echo "DAG controlled run complete: $output_dir"
echo "Summary: $output_dir/summary.json"
