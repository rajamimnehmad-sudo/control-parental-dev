#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROJECT_REF="${SUPABASE_PROJECT_REF:-syeycayasyufedwoprea}"
OUT_DIR="$ROOT_DIR/build/dev-updates"
ARCHIVE_SUFFIX="bak-$(date +%Y%m%d-%H%M%S)"
STAGING_PREFIX=".staging-$ARCHIVE_SUFFIX"

require_file() {
    local file="$1"
    if [[ ! -f "$file" ]]; then
        printf 'Falta %s\n' "$file" >&2
        printf 'Ejecuta primero: ./gradlew :app-user:assembleDevDebug :app-admin:assembleDevDebug && scripts/prepare_dev_updates.sh\n' >&2
        exit 1
    fi
}

manifest_apk_name() {
    python3 - "$1" <<'PY'
import json
import sys
import urllib.parse

with open(sys.argv[1], "r", encoding="utf-8") as handle:
    apk_url = json.load(handle)["apkUrl"]

name = urllib.parse.unquote(urllib.parse.urlparse(apk_url).path.rsplit("/", 1)[-1])
if not name or "/" in name or name in {".", ".."}:
    raise SystemExit("Nombre de APK invalido en manifest")
print(name)
PY
}

archive_public_object_if_present() {
    local object_name="$1"
    local status
    status="$(curl --silent --show-error --output /dev/null --write-out '%{http_code}' --range 0-0 \
        "https://$PROJECT_REF.supabase.co/storage/v1/object/public/dev-updates/$object_name")"

    case "$status" in
        200|206)
            supabase storage mv --experimental --linked \
                "ss:///dev-updates/$object_name" \
                "ss:///dev-updates/$object_name.$ARCHIVE_SUFFIX"
            ;;
        400|404)
            ;;
        *)
            printf 'No se pudo comprobar %s antes de publicar (HTTP %s).\n' "$object_name" "$status" >&2
            return 1
            ;;
    esac
}

if ! command -v supabase >/dev/null 2>&1; then
    printf 'Falta Supabase CLI. Instalar en macOS: brew install supabase/tap/supabase\n' >&2
    exit 1
fi

require_file "$OUT_DIR/app-user-dev-manifest.json"
require_file "$OUT_DIR/app-admin-dev-manifest.json"
USER_APK_NAME="$(manifest_apk_name "$OUT_DIR/app-user-dev-manifest.json")"
ADMIN_APK_NAME="$(manifest_apk_name "$OUT_DIR/app-admin-dev-manifest.json")"
require_file "$OUT_DIR/$USER_APK_NAME"
require_file "$OUT_DIR/$ADMIN_APK_NAME"

cd "$ROOT_DIR"

if [[ ! -f "$ROOT_DIR/supabase/.temp/project-ref" ]]; then
    printf 'Linkeando proyecto Supabase DEV: %s\n' "$PROJECT_REF"
    supabase link --project-ref "$PROJECT_REF"
fi

printf 'Subiendo manifiestos y APKs al bucket publico dev-updates...\n'
printf 'Subiendo artefactos nuevos a staging...\n'
supabase storage cp --experimental --linked --content-type application/json build/dev-updates/app-user-dev-manifest.json "ss:///dev-updates/$STAGING_PREFIX/app-user-dev-manifest.json"
supabase storage cp --experimental --linked --content-type application/json build/dev-updates/app-admin-dev-manifest.json "ss:///dev-updates/$STAGING_PREFIX/app-admin-dev-manifest.json"
supabase storage cp --experimental --linked --content-type application/vnd.android.package-archive "build/dev-updates/$USER_APK_NAME" "ss:///dev-updates/$STAGING_PREFIX/$USER_APK_NAME"
supabase storage cp --experimental --linked --content-type application/vnd.android.package-archive "build/dev-updates/$ADMIN_APK_NAME" "ss:///dev-updates/$STAGING_PREFIX/$ADMIN_APK_NAME"

printf 'Archivando artefactos dev anteriores si existen...\n'
archive_public_object_if_present app-user-dev-manifest.json
archive_public_object_if_present app-admin-dev-manifest.json
archive_public_object_if_present "$USER_APK_NAME"
archive_public_object_if_present "$ADMIN_APK_NAME"

printf 'Publicando artefactos staged...\n'
supabase storage mv --experimental --linked "ss:///dev-updates/$STAGING_PREFIX/$USER_APK_NAME" "ss:///dev-updates/$USER_APK_NAME"
supabase storage mv --experimental --linked "ss:///dev-updates/$STAGING_PREFIX/$ADMIN_APK_NAME" "ss:///dev-updates/$ADMIN_APK_NAME"
supabase storage mv --experimental --linked "ss:///dev-updates/$STAGING_PREFIX/app-user-dev-manifest.json" ss:///dev-updates/app-user-dev-manifest.json
supabase storage mv --experimental --linked "ss:///dev-updates/$STAGING_PREFIX/app-admin-dev-manifest.json" ss:///dev-updates/app-admin-dev-manifest.json

cat <<EOF
Listo.

Manifiestos:
  https://$PROJECT_REF.supabase.co/storage/v1/object/public/dev-updates/app-user-dev-manifest.json
  https://$PROJECT_REF.supabase.co/storage/v1/object/public/dev-updates/app-admin-dev-manifest.json

APKs:
  https://$PROJECT_REF.supabase.co/storage/v1/object/public/dev-updates/$USER_APK_NAME
  https://$PROJECT_REF.supabase.co/storage/v1/object/public/dev-updates/$ADMIN_APK_NAME

APK Usuario publicada.
APK Admin publicada.
Manifiestos actualizados.
Las apps pueden actualizarse desde el boton Actualizaciones.
EOF
