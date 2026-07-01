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

printf 'Compilando App Usuario y App Admin DEV...\n'
./gradlew :app-user:assembleDevDebug :app-admin:assembleDevDebug -x uploadDevUpdatesToStorage

printf '\nEjecutando tests...\n'
./gradlew test -x uploadDevUpdatesToStorage

printf '\nPreparando y publicando APKs DEV...\n'
scripts/prepare_dev_updates.sh
scripts/upload_dev_updates.sh

printf '\nUsuario publicado.\n'
printf 'Admin publicado.\n'
printf 'Manifiestos actualizados.\n'
