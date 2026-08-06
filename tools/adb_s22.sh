#!/usr/bin/env bash
set -u

readonly DEVICE_MDNS_PREFIX="adb-R5CT717BZTZ-"
readonly ADB_SERVICE="_adb-tls-connect._tcp"
readonly DEFAULT_INTERVAL_SECONDS=10

find_adb() {
  if command -v adb >/dev/null 2>&1; then
    command -v adb
  elif [[ -x "${ANDROID_HOME:-}/platform-tools/adb" ]]; then
    printf '%s\n' "${ANDROID_HOME}/platform-tools/adb"
  elif [[ -x "${HOME}/Library/Android/sdk/platform-tools/adb" ]]; then
    printf '%s\n' "${HOME}/Library/Android/sdk/platform-tools/adb"
  else
    printf 'No se encontró adb. Instalá Android SDK Platform Tools.\n' >&2
    return 1
  fi
}

discover_endpoint() {
  "$adb_bin" mdns services 2>/dev/null | awk \
    -v prefix="$DEVICE_MDNS_PREFIX" \
    -v service="$ADB_SERVICE" \
    '$1 ~ ("^" prefix) && $2 == service { print $3; exit }'
}

connect_once() {
  local endpoint
  endpoint="$(discover_endpoint)"
  if [[ -z "$endpoint" ]]; then
    printf 'S22 no visible. Desbloquealo y verificá Depuración inalámbrica.\n' >&2
    return 1
  fi

  "$adb_bin" connect "$endpoint" >/dev/null || return 1
  printf '%s\n' "$endpoint"
}

watch_connection() {
  local endpoint=""
  local next_endpoint
  printf 'Vigilando ADB inalámbrico del S22 (Ctrl-C para detener)...\n'
  while true; do
    next_endpoint="$(discover_endpoint)"
    if [[ -n "$next_endpoint" ]]; then
      if [[ "$next_endpoint" != "$endpoint" ]] || \
        ! "$adb_bin" -s "$next_endpoint" get-state 2>/dev/null | grep -q '^device$'; then
        if "$adb_bin" connect "$next_endpoint" >/dev/null 2>&1; then
          endpoint="$next_endpoint"
          printf 'Conectado: %s\n' "$endpoint"
        fi
      fi
    fi
    sleep "${ADB_WATCH_INTERVAL_SECONDS:-$DEFAULT_INTERVAL_SECONDS}"
  done
}

adb_bin="$(find_adb)" || exit 1
case "${1:-connect}" in
  connect) connect_once ;;
  watch) watch_connection ;;
  *)
    printf 'Uso: %s [connect|watch]\n' "$0" >&2
    exit 2
    ;;
esac
