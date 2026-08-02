#!/usr/bin/env bash
set -euo pipefail

readonly PACKAGE="com.contentfilter.dagbrowser.dev"
readonly ACTIVITY="com.contentfilter.dagbrowser.DagBrowserActivity"
readonly MINIMUM_SDK="29"
readonly EXPECTED_ABI="arm64-v8a"

usage() {
  cat <<'EOF'
Usage: tools/dag_perf_lab/run_live_site.sh --serial SERIAL --url HTTPS_URL --label LABEL [options]

Required:
  --serial SERIAL       Exact adb serial. Never selected automatically.
  --url HTTPS_URL       Exact public HTTPS page to measure.
  --label LABEL         Safe artifact label: letters, digits, dot, underscore or dash.

Options:
  --settle SECONDS      Observation window after launch (default: 24, max: 55).
  --swipes COUNT        Deterministic upward swipes (default: 3, max: 8).
  --warm                Do not force-stop DAG before launch.
  --capture-screen      Save a screenshot of the exact requested public page.
  --output DIR          Explicit artifact directory.

Safety:
  - Never clears a profile, cache or Logcat.
  - Never changes browser roles, settings, certificates or another app.
  - Only stops/launches DAG DEV and only navigates to the exact HTTPS URL.
EOF
}

serial=""
url=""
label=""
settle="24"
swipes="3"
cold="1"
capture_screen="0"
output_dir=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --serial) serial="${2:-}"; shift 2 ;;
    --url) url="${2:-}"; shift 2 ;;
    --label) label="${2:-}"; shift 2 ;;
    --settle) settle="${2:-}"; shift 2 ;;
    --swipes) swipes="${2:-}"; shift 2 ;;
    --warm) cold="0"; shift ;;
    --capture-screen) capture_screen="1"; shift ;;
    --output) output_dir="${2:-}"; shift 2 ;;
    --help|-h) usage; exit 0 ;;
    *) echo "Unknown argument: $1" >&2; usage >&2; exit 2 ;;
  esac
done

case "$serial" in ''|*[!A-Za-z0-9._:-]*) echo "Invalid adb serial." >&2; exit 2 ;; esac
case "$label" in ''|*[!A-Za-z0-9._-]*) echo "Invalid label." >&2; exit 2 ;; esac
case "$settle" in ''|*[!0-9]*) echo "Invalid settle value." >&2; exit 2 ;; esac
case "$swipes" in ''|*[!0-9]*) echo "Invalid swipe count." >&2; exit 2 ;; esac
if [[ ! "$url" =~ ^https://[^[:space:]]+$ ]]; then
  echo "Only an explicit HTTPS URL is accepted." >&2
  exit 2
fi
if (( settle < 5 || settle > 55 )); then echo "Settle must be 5..55 seconds." >&2; exit 2; fi
if (( swipes < 0 || swipes > 8 )); then echo "Swipes must be 0..8." >&2; exit 2; fi
if (( settle < 4 + swipes )); then echo "Settle must cover launch and every swipe." >&2; exit 2; fi

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

run_id="$(date -u +%Y%m%dT%H%M%SZ)-${serial}-${label}"
if [[ -z "$output_dir" ]]; then
  output_dir="$repo_root/.codex-tmp/dag-perf-lab/live-runs/$run_id"
fi
if [[ -e "$output_dir" ]]; then
  echo "Refusing to overwrite output path: $output_dir" >&2
  exit 1
fi
mkdir -p "$output_dir"
output_dir="$(cd "$output_dir" && pwd)"

logcat_pid=""
cleanup() {
  local status=$?
  trap - EXIT INT TERM
  if [[ -n "$logcat_pid" ]] && kill -0 "$logcat_pid" 2>/dev/null; then
    kill "$logcat_pid" 2>/dev/null || true
    wait "$logcat_pid" 2>/dev/null || true
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
  echo "DAG requires Android API $MINIMUM_SDK or newer." >&2
  exit 1
fi
if [[ "$abi" != "$EXPECTED_ABI" ]]; then
  echo "This DAG artifact requires $EXPECTED_ABI, found ${abi:-unknown}." >&2
  exit 1
fi
if ! "$adb_bin" -s "$serial" shell pm path "$PACKAGE" >/dev/null 2>&1; then
  echo "$PACKAGE is not installed on $serial." >&2
  exit 1
fi

{
  echo "schema=dag-live-perf-run-v1"
  echo "run_id=$run_id"
  echo "serial=$serial"
  echo "model=$model"
  echo "sdk=$sdk"
  echo "abi=$abi"
  echo "package=$PACKAGE"
  echo "cold=$cold"
  echo "settle_seconds=$settle"
  echo "swipes=$swipes"
  echo "url=$url"
  "$adb_bin" -s "$serial" shell getprop ro.build.version.release | tr -d '\r' | sed 's/^/android=/'
  "$adb_bin" -s "$serial" shell dumpsys package "$PACKAGE" |
    sed -n 's/^[[:space:]]*versionCode=/versionCode=/p; s/^[[:space:]]*versionName=/versionName=/p' | head -2
} >"$output_dir/run-metadata.txt"

"$adb_bin" -s "$serial" shell dumpsys meminfo "$PACKAGE" >"$output_dir/meminfo-before.txt" 2>&1 || true
"$adb_bin" -s "$serial" shell dumpsys thermalservice >"$output_dir/thermal-before.txt" 2>&1 || true
"$adb_bin" -s "$serial" shell dumpsys activity exit-info "$PACKAGE" >"$output_dir/exit-info-before.txt" 2>&1 || true

run_start_epoch_seconds="$($adb_bin -s "$serial" shell date +%s | tr -d '\r')"
logcat_since="$($adb_bin -s "$serial" shell "date '+%m-%d %H:%M:%S.000'" | tr -d '\r')"
if [[ ! "$run_start_epoch_seconds" =~ ^[0-9]{10}$ ]]; then
  echo "Could not read a trustworthy device timestamp." >&2
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

if [[ "$cold" == "1" ]]; then "$adb_bin" -s "$serial" shell am force-stop "$PACKAGE"; fi
"$adb_bin" -s "$serial" shell dumpsys gfxinfo "$PACKAGE" reset >"$output_dir/gfxinfo-reset.txt" 2>&1 || true
"$adb_bin" -s "$serial" shell am start -W \
  -a android.intent.action.VIEW -d "$url" -n "$PACKAGE/$ACTIVITY" \
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
    "$adb_bin" -s "$serial" shell input swipe "$swipe_x" "$swipe_start" "$swipe_x" "$swipe_end" 300
    sleep 1
  done
fi
remaining=$((settle - 4 - swipes))
if (( remaining > 0 )); then sleep "$remaining"; fi

if [[ "$capture_screen" == "1" ]]; then
  "$adb_bin" -s "$serial" exec-out screencap -p >"$output_dir/screen.png"
fi
"$adb_bin" -s "$serial" shell dumpsys gfxinfo "$PACKAGE" >"$output_dir/gfxinfo-after.txt" 2>&1 || true
"$adb_bin" -s "$serial" shell dumpsys meminfo "$PACKAGE" >"$output_dir/meminfo-after.txt" 2>&1 || true
"$adb_bin" -s "$serial" shell dumpsys thermalservice >"$output_dir/thermal-after.txt" 2>&1 || true
"$adb_bin" -s "$serial" shell dumpsys activity exit-info "$PACKAGE" >"$output_dir/exit-info-after.txt" 2>&1 || true
"$adb_bin" -s "$serial" shell log -p i -t DagPerfHarness "run_end=$run_id"
sleep 0.2
kill "$logcat_pid" 2>/dev/null || true
wait "$logcat_pid" 2>/dev/null || true
logcat_pid=""

python3 "$script_dir/summarize_run.py" "$output_dir" >/dev/null
echo "DAG live run complete: $output_dir"
echo "Summary: $output_dir/summary.json"
