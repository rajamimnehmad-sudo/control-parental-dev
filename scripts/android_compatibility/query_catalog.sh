#!/usr/bin/env bash
set -euo pipefail

project_id="${1:-${FIREBASE_TEST_PROJECT_ID:-}}"
output_path="${2:-build/compatibility/firebase-device-catalog.json}"

if [[ -z "$project_id" ]]; then
  echo "Falta FIREBASE_TEST_PROJECT_ID o el primer argumento (proyecto de pruebas dedicado)." >&2
  exit 2
fi
if ! command -v gcloud >/dev/null 2>&1; then
  echo "gcloud no está instalado." >&2
  exit 2
fi
if ! gcloud auth list --filter=status:ACTIVE --format='value(account)' | grep -q .; then
  echo "gcloud no tiene una identidad activa. Configure credenciales dedicadas de Test Lab." >&2
  exit 2
fi

mkdir -p "$(dirname "$output_path")"
gcloud firebase test android models list \
  --project "$project_id" \
  --format=json >"$output_path"
gcloud firebase test android versions list \
  --project "$project_id" \
  --format=json >"$(dirname "$output_path")/firebase-android-versions.json"
gcloud firebase test android locales list \
  --project "$project_id" \
  --format=json >"$(dirname "$output_path")/firebase-android-locales.json"
echo "$output_path"
