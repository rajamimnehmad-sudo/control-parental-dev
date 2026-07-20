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

    if (( local_version == remote_version )) && [[ "${ALLOW_SAME_VERSION_REPAIR:-false}" == "true" ]]; then
        printf '%s: reparacion confirmada de la misma version DEV (%s).\n' "$app_name" "$local_version"
        return
    fi

    if (( local_version <= remote_version )); then
        printf '%s: versionCode DEV no subio. Local=%s, publicado=%s. Abortando publicacion.\n' \
            "$app_name" "$local_version" "$remote_version" >&2
        exit 1
    fi

    printf '%s: versionCode DEV OK (%s > %s).\n' "$app_name" "$local_version" "$remote_version"
}

assert_stable_dev_signature() {
    local expected_digest="d51bc0dabd280ce1b0f098ae168eb57758faeba301156cde835737835f8a8832"
    local build_tools_dir="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}/build-tools"
    local apksigner
    local apk
    local actual_digest

    apksigner="$(find "$build_tools_dir" -mindepth 2 -maxdepth 2 -type f -name apksigner | sort -V | tail -n 1)"
    if [[ -z "$apksigner" ]]; then
        printf 'No se encontro apksigner para validar la firma DEV.\n' >&2
        exit 1
    fi

    for apk in \
        app-user/build/outputs/apk/dev/debug/app-user-dev-debug.apk \
        app-admin/build/outputs/apk/dev/debug/app-admin-dev-debug.apk; do
        actual_digest="$(
            "$apksigner" verify --print-certs "$apk" 2>&1 |
                awk -F': ' '/Signer #1 certificate SHA-256 digest/ { print tolower($2); exit }'
        )"
        if [[ "$actual_digest" != "$expected_digest" ]]; then
            printf 'Firma DEV incorrecta en %s. Esperada=%s, actual=%s.\n' \
                "$apk" "$expected_digest" "${actual_digest:-ausente}" >&2
            exit 1
        fi
    done

    printf 'Firma DEV estable verificada en ambos APK (%s).\n' "$expected_digest"
}

printf 'Compilando App Usuario y App Admin DEV...\n'
./gradlew :app-user:assembleDevDebug :app-admin:assembleDevDebug -x uploadDevUpdatesToStorage

assert_stable_dev_signature

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
