#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

export PATH="/opt/homebrew/bin:/usr/local/bin:$PATH"

TARGET="${1:-}"
PROJECT_REF="${SUPABASE_PROJECT_REF:-syeycayasyufedwoprea}"
OUT_DIR="$ROOT_DIR/build/dev-updates"
ARCHIVE_SUFFIX="bak-$(date +%Y%m%d-%H%M%S)"
STAGING_PREFIX=".staging-$ARCHIVE_SUFFIX"

case "$TARGET" in
    admin)
        MODULE="app-admin"
        LABEL="Admin"
        FILE_PREFIX="app-admin"
        ;;
    usuario|user)
        MODULE="app-user"
        LABEL="Usuario"
        FILE_PREFIX="app-user"
        ;;
    *)
        printf 'Uso: %s admin|usuario\n' "$0" >&2
        exit 1
        ;;
esac

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

json_value() {
    local file="$1"
    local key="$2"
    python3 - "$file" "$key" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as handle:
    data = json.load(handle)

if "elements" in data:
    print(data["elements"][0][sys.argv[2]])
else:
    print(data[sys.argv[2]])
PY
}

remote_version_code() {
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

write_manifest() {
    local target="$1"
    local version_code="$2"
    local version_name="$3"
    local apk_url="$4"
    local apk_sha="$5"
    local notes="$6"

    python3 - "$target" "$version_code" "$version_name" "$apk_url" "$apk_sha" "$notes" <<'PY'
import json
import sys

target, version_code, version_name, apk_url, apk_sha, notes = sys.argv[1:]
manifest = {
    "versionCode": int(version_code),
    "versionName": version_name,
    "apkUrl": apk_url,
    "apkSha256": apk_sha,
    "releaseNotes": notes,
}

with open(target, "w", encoding="utf-8") as handle:
    json.dump(manifest, handle, indent=2, ensure_ascii=True)
    handle.write("\n")
PY
}

SUPABASE_URL="$(env_value SUPABASE_URL)"
SUPABASE_URL="${SUPABASE_URL%/}"
if [[ -z "$SUPABASE_URL" ]]; then
    printf 'Falta SUPABASE_URL en .env\n' >&2
    exit 1
fi

BUCKET_URL="$SUPABASE_URL/storage/v1/object/public/dev-updates"
REMOTE_MANIFEST="$BUCKET_URL/$FILE_PREFIX-dev-manifest.json"
LOCAL_APK="$ROOT_DIR/$MODULE/build/outputs/apk/dev/debug/$FILE_PREFIX-dev-debug.apk"
LOCAL_META="$ROOT_DIR/$MODULE/build/outputs/apk/dev/debug/output-metadata.json"
OUT_APK="$OUT_DIR/$FILE_PREFIX-dev-debug.apk"
OUT_MANIFEST="$OUT_DIR/$FILE_PREFIX-dev-manifest.json"

printf 'Compilando App %s DEV...\n' "$LABEL"
./gradlew ":$MODULE:assembleDevDebug" ":$MODULE:testDevDebugUnitTest" -x uploadDevUpdatesToStorage

mkdir -p "$OUT_DIR"
cp "$LOCAL_APK" "$OUT_APK"

VERSION_CODE="$(json_value "$LOCAL_META" versionCode)"
VERSION_NAME="$(json_value "$LOCAL_META" versionName)"
APK_SHA="$(shasum -a 256 "$OUT_APK" | awk '{print $1}')"
REMOTE_VERSION="$(remote_version_code "$REMOTE_MANIFEST")"

if [[ -n "$REMOTE_VERSION" && "$VERSION_CODE" -le "$REMOTE_VERSION" ]]; then
    printf '%s: versionCode DEV no subio. Local=%s, publicado=%s. Abortando publicacion.\n' \
        "$LABEL" "$VERSION_CODE" "$REMOTE_VERSION" >&2
    exit 1
fi

write_manifest \
    "$OUT_MANIFEST" \
    "$VERSION_CODE" \
    "$VERSION_NAME" \
    "$BUCKET_URL/$FILE_PREFIX-dev-debug.apk" \
    "$APK_SHA" \
    "Build DEV $LABEL preparado para pruebas internas."

if [[ ! -f "$ROOT_DIR/supabase/.temp/project-ref" ]]; then
    printf 'Linkeando proyecto Supabase DEV: %s\n' "$PROJECT_REF"
    supabase link --project-ref "$PROJECT_REF"
fi

printf 'Subiendo App %s DEV a staging...\n' "$LABEL"
supabase storage cp --experimental --linked --content-type application/json "$OUT_MANIFEST" "ss:///dev-updates/$STAGING_PREFIX/$FILE_PREFIX-dev-manifest.json"
supabase storage cp --experimental --linked --content-type application/vnd.android.package-archive "$OUT_APK" "ss:///dev-updates/$STAGING_PREFIX/$FILE_PREFIX-dev-debug.apk"

printf 'Archivando App %s DEV anterior si existe...\n' "$LABEL"
supabase storage mv --experimental --linked "ss:///dev-updates/$FILE_PREFIX-dev-manifest.json" "ss:///dev-updates/$FILE_PREFIX-dev-manifest.json.$ARCHIVE_SUFFIX" >/dev/null 2>&1 || true
supabase storage mv --experimental --linked "ss:///dev-updates/$FILE_PREFIX-dev-debug.apk" "ss:///dev-updates/$FILE_PREFIX-dev-debug.apk.$ARCHIVE_SUFFIX" >/dev/null 2>&1 || true

printf 'Publicando App %s DEV...\n' "$LABEL"
supabase storage mv --experimental --linked "ss:///dev-updates/$STAGING_PREFIX/$FILE_PREFIX-dev-manifest.json" "ss:///dev-updates/$FILE_PREFIX-dev-manifest.json"
supabase storage mv --experimental --linked "ss:///dev-updates/$STAGING_PREFIX/$FILE_PREFIX-dev-debug.apk" "ss:///dev-updates/$FILE_PREFIX-dev-debug.apk"

cat <<EOF
Listo.

App $LABEL versionCode $VERSION_CODE publicada en DEV.
Manifest:
  $BUCKET_URL/$FILE_PREFIX-dev-manifest.json
APK:
  $BUCKET_URL/$FILE_PREFIX-dev-debug.apk
SHA-256:
  $APK_SHA
EOF
