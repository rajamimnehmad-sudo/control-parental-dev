#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="$ROOT_DIR/build/dev-updates"

SUPABASE_URL="${SUPABASE_URL:-}"
if [[ -z "$SUPABASE_URL" && -f "$ROOT_DIR/.env" ]]; then
    SUPABASE_URL="$(
        awk -F= '
            $0 !~ /^[[:space:]]*#/ && $1 == "SUPABASE_URL" {
                value = substr($0, index($0, "=") + 1)
                gsub(/^[[:space:]]+|[[:space:]]+$/, "", value)
                gsub(/^"|"$/, "", value)
                print value
                exit
            }
        ' "$ROOT_DIR/.env"
    )"
fi

if [[ -z "$SUPABASE_URL" ]]; then
    printf 'Falta SUPABASE_URL en el entorno o en .env\n' >&2
    exit 1
fi

SUPABASE_URL="${SUPABASE_URL%/}"
BUCKET_URL="$SUPABASE_URL/storage/v1/object/public/dev-updates"

USER_APK="$ROOT_DIR/app-user/build/outputs/apk/dev/debug/app-user-dev-debug.apk"
ADMIN_APK="$ROOT_DIR/app-admin/build/outputs/apk/dev/debug/app-admin-dev-debug.apk"
USER_META="$ROOT_DIR/app-user/build/outputs/apk/dev/debug/output-metadata.json"
ADMIN_META="$ROOT_DIR/app-admin/build/outputs/apk/dev/debug/output-metadata.json"

release_notes() {
    local app="$1"
    local version_code="$2"
    local file="$ROOT_DIR/release-notes/dev/$version_code-$app.txt"
    require_file "$file"
    printf '%s' "$(< "$file")"
}

require_file() {
    local file="$1"
    if [[ ! -f "$file" ]]; then
        printf 'Falta %s. Ejecuta los builds dev debug primero.\n' "$file" >&2
        exit 1
    fi
}

metadata_value() {
    local file="$1"
    local key="$2"
    python3 - "$file" "$key" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as handle:
    data = json.load(handle)

print(data["elements"][0][sys.argv[2]])
PY
}

sha256_file() {
    shasum -a 256 "$1" | awk '{print $1}'
}

write_manifest() {
    local target="$1"
    local version_code="$2"
    local version_name="$3"
    local apk_name="$4"
    local apk_sha="$5"
    local notes="$6"

    python3 - "$target" "$version_code" "$version_name" "$BUCKET_URL/$apk_name" "$apk_sha" "$notes" <<'PY'
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

require_file "$USER_APK"
require_file "$ADMIN_APK"
require_file "$USER_META"
require_file "$ADMIN_META"

USER_VERSION_CODE="$(metadata_value "$USER_META" versionCode)"
USER_VERSION_NAME="$(metadata_value "$USER_META" versionName)"
ADMIN_VERSION_CODE="$(metadata_value "$ADMIN_META" versionCode)"
ADMIN_VERSION_NAME="$(metadata_value "$ADMIN_META" versionName)"
USER_NOTES="$(release_notes user "$USER_VERSION_CODE")"
ADMIN_NOTES="$(release_notes admin "$ADMIN_VERSION_CODE")"
USER_APK_NAME="app-user-dev-$USER_VERSION_CODE-debug.apk"
ADMIN_APK_NAME="app-admin-dev-$ADMIN_VERSION_CODE-debug.apk"

mkdir -p "$OUT_DIR"
rm -f "$OUT_DIR/app-user-dev-debug.apk" "$OUT_DIR/app-admin-dev-debug.apk"
cp "$USER_APK" "$OUT_DIR/$USER_APK_NAME"
cp "$ADMIN_APK" "$OUT_DIR/$ADMIN_APK_NAME"

USER_SHA="$(sha256_file "$OUT_DIR/$USER_APK_NAME")"
ADMIN_SHA="$(sha256_file "$OUT_DIR/$ADMIN_APK_NAME")"

write_manifest \
    "$OUT_DIR/app-user-dev-manifest.json" \
    "$USER_VERSION_CODE" \
    "$USER_VERSION_NAME" \
    "$USER_APK_NAME" \
    "$USER_SHA" \
    "$USER_NOTES"

write_manifest \
    "$OUT_DIR/app-admin-dev-manifest.json" \
    "$ADMIN_VERSION_CODE" \
    "$ADMIN_VERSION_NAME" \
    "$ADMIN_APK_NAME" \
    "$ADMIN_SHA" \
    "$ADMIN_NOTES"

cat <<EOF
Archivos listos en:
  $OUT_DIR

Subir al bucket publico dev-updates con:
  scripts/upload_dev_updates.sh

O manualmente con Supabase CLI 2.x:
  supabase storage cp --experimental --linked --content-type application/json build/dev-updates/app-user-dev-manifest.json ss:///dev-updates/app-user-dev-manifest.json
  supabase storage cp --experimental --linked --content-type application/json build/dev-updates/app-admin-dev-manifest.json ss:///dev-updates/app-admin-dev-manifest.json
  supabase storage cp --experimental --linked --content-type application/vnd.android.package-archive build/dev-updates/$USER_APK_NAME ss:///dev-updates/$USER_APK_NAME
  supabase storage cp --experimental --linked --content-type application/vnd.android.package-archive build/dev-updates/$ADMIN_APK_NAME ss:///dev-updates/$ADMIN_APK_NAME

URLs:
  $BUCKET_URL/app-user-dev-manifest.json
  $BUCKET_URL/app-admin-dev-manifest.json
  $BUCKET_URL/$USER_APK_NAME
  $BUCKET_URL/$ADMIN_APK_NAME

SHA-256:
  Usuario: $USER_SHA
  Admin:   $ADMIN_SHA
EOF
