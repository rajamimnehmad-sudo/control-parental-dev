#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$ROOT_DIR/.env"
EXAMPLE_ENV_FILE="$ROOT_DIR/.env.example"
DEV_SQL="$ROOT_DIR/supabase/dev_setup_all.sql"
TEST_DATA_SQL="$ROOT_DIR/supabase/dev_test_data.sql"
DEV_CODES_SQL="$ROOT_DIR/supabase/dev_activation_codes_batch.sql"
DEV_STORAGE_SQL="$ROOT_DIR/supabase/dev_storage_setup.sql"

red() { printf '\033[31m%s\033[0m\n' "$1"; }
green() { printf '\033[32m%s\033[0m\n' "$1"; }
yellow() { printf '\033[33m%s\033[0m\n' "$1"; }
info() { printf '%s\n' "$1"; }

read_env_value() {
    local key="$1"
    if [[ ! -f "$ENV_FILE" ]]; then
        return 0
    fi

    awk -F= -v key="$key" '
        $0 !~ /^[[:space:]]*#/ && $1 == key {
            value = substr($0, index($0, "=") + 1)
            gsub(/^[[:space:]]+|[[:space:]]+$/, "", value)
            gsub(/^"|"$/, "", value)
            gsub(/^'\''|'\''$/, "", value)
            print value
            exit
        }
    ' "$ENV_FILE"
}

decode_jwt_role() {
    local token="$1"
    python3 - "$token" <<'PY' 2>/dev/null || true
import base64
import json
import sys

token = sys.argv[1]
parts = token.split(".")
if len(parts) < 2:
    sys.exit(0)

payload = parts[1].replace("-", "+").replace("_", "/")
payload += "=" * (-len(payload) % 4)
try:
    data = json.loads(base64.b64decode(payload).decode("utf-8"))
except Exception:
    sys.exit(0)

role = data.get("role")
if role:
    print(role)
PY
}

info ""
info "== Supabase dev setup check =="
info "Proyecto: $ROOT_DIR"
info ""

if [[ ! -f "$ENV_FILE" ]]; then
    red "Falta .env"
    if [[ -f "$EXAMPLE_ENV_FILE" ]]; then
        info "Crea el archivo local con:"
        info "  cp .env.example .env"
        info ""
        info "Luego completa:"
        info "  SUPABASE_URL=https://tu-proyecto.supabase.co"
        info "  SUPABASE_ANON_KEY=tu-anon-key-publica"
    fi
    exit 1
fi

SUPABASE_URL="$(read_env_value SUPABASE_URL)"
SUPABASE_ANON_KEY="$(read_env_value SUPABASE_ANON_KEY)"
UPDATE_MANIFEST_URL="$(read_env_value UPDATE_MANIFEST_URL)"
UPDATE_MANIFEST_URL_USER="$(read_env_value UPDATE_MANIFEST_URL_USER)"
UPDATE_MANIFEST_URL_ADMIN="$(read_env_value UPDATE_MANIFEST_URL_ADMIN)"

missing=0
if [[ -z "$SUPABASE_URL" ]]; then
    red "Falta SUPABASE_URL en .env"
    missing=1
fi

if [[ -z "$SUPABASE_ANON_KEY" ]]; then
    red "Falta SUPABASE_ANON_KEY en .env"
    missing=1
fi

if [[ "$missing" -ne 0 ]]; then
    info ""
    info "Completa .env y vuelve a ejecutar:"
    info "  scripts/setup_supabase_dev.sh"
    exit 1
fi

if [[ ! "$SUPABASE_URL" =~ ^https://[A-Za-z0-9.-]+\.supabase\.co/?$ ]]; then
    yellow "SUPABASE_URL no parece una URL estándar de Supabase:"
    info "  $SUPABASE_URL"
fi

if grep -Eiq 'SERVICE[_-]?ROLE|SUPABASE_SERVICE|SERVICE_ROLE_KEY' "$ENV_FILE"; then
    red "Se detectó una variable o comentario relacionado con Service Role en .env."
    red "No guardes Service Role Key en este proyecto ni en Android."
    exit 1
fi

decoded_role="$(decode_jwt_role "$SUPABASE_ANON_KEY")"
if [[ "$decoded_role" == "service_role" ]]; then
    red "SUPABASE_ANON_KEY contiene una key con role=service_role."
    red "Reemplázala por la public anon key de Supabase."
    exit 1
fi

if [[ -n "$decoded_role" && "$decoded_role" != "anon" ]]; then
    yellow "La key decodifica role=$decoded_role. Verifica que sea la public anon key."
fi

green ".env encontrado y variables mínimas presentes."
green "No se detectó Service Role Key."
info ""
info "SUPABASE_URL: $SUPABASE_URL"
info "SUPABASE_ANON_KEY: presente (${#SUPABASE_ANON_KEY} caracteres)"
if [[ -n "$UPDATE_MANIFEST_URL" ]]; then
    info "UPDATE_MANIFEST_URL: presente"
else
    info "UPDATE_MANIFEST_URL: vacío (ok para esta prueba)"
fi
if [[ -n "$UPDATE_MANIFEST_URL_USER" ]]; then
    info "UPDATE_MANIFEST_URL_USER: presente"
else
    info "UPDATE_MANIFEST_URL_USER: vacío"
fi
if [[ -n "$UPDATE_MANIFEST_URL_ADMIN" ]]; then
    info "UPDATE_MANIFEST_URL_ADMIN: presente"
else
    info "UPDATE_MANIFEST_URL_ADMIN: vacío"
fi

info ""
info "== SQL listo para ejecutar =="
info "Opción recomendada para reducir pasos manuales:"
info "  1. En Supabase > Authentication > Users, crea el usuario de prueba."
info "  2. Copia su User UID."
info "  3. Abre $DEV_SQL"
info "  4. Reemplaza DEV_AUTH_USER_ID_HERE por ese User UID."
info "  5. Pega y ejecuta todo el archivo en Supabase SQL Editor."
info ""
info "Alternativa en dos pasos:"
info "  1. Ejecuta supabase/schema.sql"
info "  2. Ejecuta supabase/rls.sql"
info "  3. Edita y ejecuta $TEST_DATA_SQL"
info "  4. Ejecuta $DEV_CODES_SQL para crear códigos DEV 1-100"
info "  5. Ejecuta $DEV_STORAGE_SQL para crear el bucket dev-updates"

info ""
info "== Comandos posibles si usas CLI local =="
if command -v psql >/dev/null 2>&1; then
    info "psql está instalado. Si tienes una DATABASE_URL temporal, puedes ejecutar:"
    info "  psql \"postgresql://postgres:[DB_PASSWORD]@[HOST]:5432/postgres\" -f supabase/dev_setup_all.sql"
else
    yellow "psql no está instalado o no está en PATH."
fi

if command -v supabase >/dev/null 2>&1; then
    info "Supabase CLI está instalado. Si el proyecto está linkeado:"
    info "  supabase db push"
    info "Para este flujo guiado, SQL Editor sigue siendo más directo."
else
    yellow "Supabase CLI no está instalado o no está en PATH."
fi

info ""
info "== Próximos pasos =="
info "1. Ejecuta el SQL de desarrollo con el User UID real."
info "2. Recompila ambos APKs:"
info "   ./gradlew :app-user:assembleDevDebug :app-admin:assembleDevDebug"
info "3. Prepara manifiestos/APKs de actualización:"
info "   scripts/prepare_dev_updates.sh"
info "4. Sube los 4 archivos de build/dev-updates al bucket dev-updates"
info "   Supabase CLI 2.x requiere --experimental para storage cp."
info "5. Instala y activa:"
info "   Usuario: cualquier código 1-50"
info "   Admin:   cualquier código 51-100"
info "6. Sigue la checklist en docs/SUPABASE_E2E_TEST.md"
