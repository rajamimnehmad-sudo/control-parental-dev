#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="$ROOT_DIR/build/dev-updates"
DAG_OUTPUT_DIR="$ROOT_DIR/app-dag-browser/build/outputs/apk/dev/debug"
DAG_APK="$DAG_OUTPUT_DIR/DagBrowser-dev-debug.apk"
DAG_META="$DAG_OUTPUT_DIR/output-metadata.json"

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

for required_file in "$DAG_APK" "$DAG_META"; do
    if [[ ! -f "$required_file" ]]; then
        printf 'Falta %s. Compila DAG DEV primero.\n' "$required_file" >&2
        exit 1
    fi
done

metadata_value() {
    local key="$1"
    python3 - "$DAG_META" "$key" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as handle:
    element = json.load(handle)["elements"][0]

print(element[sys.argv[2]])
PY
}

DAG_VERSION_CODE="$(metadata_value versionCode)"
DAG_VERSION_NAME="$(metadata_value versionName)"
NOTES_FILE="$ROOT_DIR/release-notes/dev/$DAG_VERSION_CODE-dag.txt"
if [[ ! -f "$NOTES_FILE" ]]; then
    printf 'Faltan las novedades de DAG para versionCode %s: %s\n' "$DAG_VERSION_CODE" "$NOTES_FILE" >&2
    exit 1
fi

DAG_APK_NAME="app-dag-browser-dev-$DAG_VERSION_CODE-debug.apk"
mkdir -p "$OUT_DIR"
cp "$DAG_APK" "$OUT_DIR/$DAG_APK_NAME"
DAG_SHA="$(shasum -a 256 "$OUT_DIR/$DAG_APK_NAME" | awk '{print $1}')"
BUCKET_URL="${SUPABASE_URL%/}/storage/v1/object/public/dev-updates"

python3 - \
    "$OUT_DIR/app-dag-browser-dev-manifest.json" \
    "$DAG_VERSION_CODE" \
    "$DAG_VERSION_NAME" \
    "$BUCKET_URL/$DAG_APK_NAME" \
    "$DAG_SHA" \
    "$NOTES_FILE" <<'PY'
import json
import sys

target, version_code, version_name, apk_url, apk_sha, notes_file = sys.argv[1:]
with open(notes_file, "r", encoding="utf-8") as handle:
    notes = handle.read().strip()

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

printf 'DAG DEV preparado localmente:\n'
printf '  %s\n' "$OUT_DIR/$DAG_APK_NAME"
printf '  %s\n' "$OUT_DIR/app-dag-browser-dev-manifest.json"
printf '  SHA-256: %s\n' "$DAG_SHA"
printf 'No se publicó ningún archivo remoto.\n'
