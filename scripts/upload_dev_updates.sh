#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROJECT_REF="${SUPABASE_PROJECT_REF:-syeycayasyufedwoprea}"
OUT_DIR="$ROOT_DIR/build/dev-updates"
ARCHIVE_SUFFIX="bak-$(date +%Y%m%d-%H%M%S)"

require_file() {
    local file="$1"
    if [[ ! -f "$file" ]]; then
        printf 'Falta %s\n' "$file" >&2
        printf 'Ejecuta primero: ./gradlew :app-user:assembleDevDebug :app-admin:assembleDevDebug && scripts/prepare_dev_updates.sh\n' >&2
        exit 1
    fi
}

if ! command -v supabase >/dev/null 2>&1; then
    printf 'Falta Supabase CLI. Instalar en macOS: brew install supabase/tap/supabase\n' >&2
    exit 1
fi

require_file "$OUT_DIR/app-user-dev-manifest.json"
require_file "$OUT_DIR/app-admin-dev-manifest.json"
require_file "$OUT_DIR/app-user-dev-debug.apk"
require_file "$OUT_DIR/app-admin-dev-debug.apk"

cd "$ROOT_DIR"

if [[ ! -f "$ROOT_DIR/supabase/.temp/project-ref" ]]; then
    printf 'Linkeando proyecto Supabase DEV: %s\n' "$PROJECT_REF"
    supabase link --project-ref "$PROJECT_REF"
fi

printf 'Subiendo manifiestos y APKs al bucket publico dev-updates...\n'
printf 'Archivando artefactos dev anteriores si existen...\n'
supabase storage mv --experimental --linked ss:///dev-updates/app-user-dev-manifest.json "ss:///dev-updates/app-user-dev-manifest.json.$ARCHIVE_SUFFIX" >/dev/null 2>&1 || true
supabase storage mv --experimental --linked ss:///dev-updates/app-admin-dev-manifest.json "ss:///dev-updates/app-admin-dev-manifest.json.$ARCHIVE_SUFFIX" >/dev/null 2>&1 || true
supabase storage mv --experimental --linked ss:///dev-updates/app-user-dev-debug.apk "ss:///dev-updates/app-user-dev-debug.apk.$ARCHIVE_SUFFIX" >/dev/null 2>&1 || true
supabase storage mv --experimental --linked ss:///dev-updates/app-admin-dev-debug.apk "ss:///dev-updates/app-admin-dev-debug.apk.$ARCHIVE_SUFFIX" >/dev/null 2>&1 || true

supabase storage cp --experimental --linked --content-type application/json build/dev-updates/app-user-dev-manifest.json ss:///dev-updates/app-user-dev-manifest.json
supabase storage cp --experimental --linked --content-type application/json build/dev-updates/app-admin-dev-manifest.json ss:///dev-updates/app-admin-dev-manifest.json
supabase storage cp --experimental --linked --content-type application/vnd.android.package-archive build/dev-updates/app-user-dev-debug.apk ss:///dev-updates/app-user-dev-debug.apk
supabase storage cp --experimental --linked --content-type application/vnd.android.package-archive build/dev-updates/app-admin-dev-debug.apk ss:///dev-updates/app-admin-dev-debug.apk

cat <<EOF
Listo.

Manifiestos:
  https://$PROJECT_REF.supabase.co/storage/v1/object/public/dev-updates/app-user-dev-manifest.json
  https://$PROJECT_REF.supabase.co/storage/v1/object/public/dev-updates/app-admin-dev-manifest.json

APKs:
  https://$PROJECT_REF.supabase.co/storage/v1/object/public/dev-updates/app-user-dev-debug.apk
  https://$PROJECT_REF.supabase.co/storage/v1/object/public/dev-updates/app-admin-dev-debug.apk

APK Usuario publicada.
APK Admin publicada.
Manifiestos actualizados.
Las apps pueden actualizarse desde el boton Actualizaciones.
EOF
