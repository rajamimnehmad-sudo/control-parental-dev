#!/usr/bin/env bash
set -euo pipefail

repo_root="$(git rev-parse --show-toplevel)"
cd "$repo_root"

migration="$(find supabase/migrations -maxdepth 1 -type f -name '*_dag_v2_calibration_collector.sql' -print)"
function_dir="supabase/functions/dag-v2-calibration"

if [[ -z "$migration" || "$(printf '%s\n' "$migration" | wc -l | tr -d ' ')" != "1" ]]; then
  echo "ERROR: expected exactly one DAG v2 calibration migration" >&2
  exit 1
fi

required_migration_patterns=(
  'create table public.dag_v2_calibration_samples'
  'create table public.dag_v2_calibration_labels'
  'create table public.dag_v2_calibration_audit'
  'alter table public.dag_v2_calibration_samples enable row level security'
  'alter table public.dag_v2_calibration_labels enable row level security'
  'alter table public.dag_v2_calibration_audit enable row level security'
  "'dag-v2-calibration'"
  'false,'
  'file_size_limit'
  'allowed_mime_types'
  'dag_v2_calibration_authorize_and_consume'
  'dag_v2_calibration_submission_authorized'
  'dag_v2_calibration_register_sample'
  'dag_v2_calibration_hamming_distance'
  '<= 5'
  'dag_v2_calibration_upsert_label'
  "'show', 'hide', 'unsure'"
  "'label_changed'"
  "'submission_attempt'"
  'grant execute on function public.dag_v2_calibration'
  'to service_role'
)

for pattern in "${required_migration_patterns[@]}"; do
  if ! rg -F -q "$pattern" "$migration"; then
    echo "ERROR: migration contract missing: $pattern" >&2
    exit 1
  fi
done

if rg -n 'grant (select|insert|update|delete|all).*to (anon|authenticated)' "$migration"; then
  echo "ERROR: calibration tables must not be exposed directly to Android roles" >&2
  exit 1
fi

if rg -n '\b(dag_calibration_reviews|dag_calibration_versions|dag_calibration_models|dag_calibration_audit)\b' "$migration" "$function_dir"; then
  echo "ERROR: DAG v2 calibration must not write DAG v1 calibration objects" >&2
  exit 1
fi

required_function_patterns=(
  'multipart/form-data'
  'x-device-token'
  'dag_v2_calibration_authorize_and_consume'
  'dag_v2_calibration_register_sample'
  'dag_v2_calibration_upsert_label'
  'sha256Hex(bytes)'
  'jpegDimensions(bytes)'
  'upsert: false'
  'dag-v2-calibration'
  'existing_content_sha256'
)

for pattern in "${required_function_patterns[@]}"; do
  if ! rg -F -q "$pattern" "$function_dir"; then
    echo "ERROR: Edge Function contract missing: $pattern" >&2
    exit 1
  fi
done

for forbidden in model_version thresholds resource_url document_url cookies referer headers; do
  if rg -n "\"$forbidden\"" "$function_dir/index.ts"; then
    echo "ERROR: forbidden field present in upload implementation: $forbidden" >&2
    exit 1
  fi
done

if rg -n 'from\("dag_calibration|\.from\('\''dag_calibration|storage[[:space:]]*\.[[:space:]]*from\("dag-calibration' "$function_dir"; then
  echo "ERROR: Edge Function references DAG v1 calibration storage or tables" >&2
  exit 1
fi

echo "DAG v2 calibration contract verified: RLS, private storage, DEV authorization, deduplication, audit, and zero DAG v1 writes."
