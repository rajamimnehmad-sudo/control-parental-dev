#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

export PATH="/opt/homebrew/bin:/usr/local/bin:$PATH"

if [[ ! -f ".env" ]]; then
    printf 'Falta .env en %s\n' "$ROOT_DIR" >&2
    exit 1
fi

if ! command -v supabase >/dev/null 2>&1; then
    printf 'Falta Supabase CLI. Instalar: brew install supabase/tap/supabase\n' >&2
    exit 1
fi

if ! command -v java >/dev/null 2>&1; then
    printf 'Falta Java en esta sesion. Abre Android Studio una vez o configura JAVA_HOME.\n' >&2
    exit 1
fi

env_value() {
    local key="$1"
    awk -F= -v key="$key" '
        $0 !~ /^[[:space:]]*#/ && $1 == key {
            value = substr($0, index($0, "=") + 1)
            gsub(/^[[:space:]]+|[[:space:]]+$/, "", value)
            gsub(/^"|"$/, "", value)
            print value
            exit
        }
    ' .env
}

manifest_version_code() {
    python3 - "$1" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as handle:
    print(json.load(handle)["versionCode"])
PY
}

remote_manifest_version_code() {
    local url="$1"
    python3 - "$url" <<'PY'
import json
import sys
import urllib.error
import urllib.request

try:
    with urllib.request.urlopen(sys.argv[1], timeout=20) as response:
        print(json.load(response)["versionCode"])
except urllib.error.HTTPError as error:
    if error.code == 404:
        print("")
    else:
        raise
PY
}

assert_version_code_increased() {
    local app_name="$1"
    local local_manifest="$2"
    local remote_url="$3"
    local local_version
    local remote_version

    local_version="$(manifest_version_code "$local_manifest")"
    remote_version="$(remote_manifest_version_code "$remote_url")"

    if [[ -z "$remote_version" ]]; then
        printf '%s: no hay manifest DEV publicado previo; versionCode local %s.\n' "$app_name" "$local_version"
        return
    fi

    if (( local_version <= remote_version )); then
        printf '%s: versionCode DEV no subio. Local=%s, publicado=%s. Abortando publicacion.\n' \
            "$app_name" "$local_version" "$remote_version" >&2
        exit 1
    fi

    printf '%s: versionCode DEV OK (%s > %s).\n' "$app_name" "$local_version" "$remote_version"
}

printf 'Compilando App Usuario y App Admin DEV...\n'
./gradlew :app-user:assembleDevDebug :app-admin:assembleDevDebug -x uploadDevUpdatesToStorage

printf '\nEjecutando tests...\n'
./gradlew test -x uploadDevUpdatesToStorage

printf '\nPreparando y publicando APKs DEV...\n'
scripts/prepare_dev_updates.sh

SUPABASE_URL="$(env_value SUPABASE_URL)"
SUPABASE_URL="${SUPABASE_URL%/}"
if [[ -z "$SUPABASE_URL" ]]; then
    printf 'Falta SUPABASE_URL en .env\n' >&2
    exit 1
fi

assert_version_code_increased \
    "Usuario" \
    "build/dev-updates/app-user-dev-manifest.json" \
    "$SUPABASE_URL/storage/v1/object/public/dev-updates/app-user-dev-manifest.json"
assert_version_code_increased \
    "Admin" \
    "build/dev-updates/app-admin-dev-manifest.json" \
    "$SUPABASE_URL/storage/v1/object/public/dev-updates/app-admin-dev-manifest.json"

scripts/upload_dev_updates.sh

printf '\nUsuario publicado.\n'
printf 'Admin publicado.\n'
printf 'Manifiestos actualizados.\n'
