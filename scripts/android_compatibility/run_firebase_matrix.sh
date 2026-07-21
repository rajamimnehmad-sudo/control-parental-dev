#!/usr/bin/env bash
set -euo pipefail

kind="${MATRIX_KIND:-virtual}"
project_id="${FIREBASE_TEST_PROJECT_ID:-}"
confirmation="${FIREBASE_TEST_CONFIRMATION:-}"
app_apk=""
test_apk=""
results_bucket="${FIREBASE_TEST_RESULTS_BUCKET:-}"
results_dir="${FIREBASE_TEST_RESULTS_DIR:-compatibility-$(date -u +%Y%m%dT%H%M%SZ)}"

while (($#)); do
  case "$1" in
    --kind) kind="$2"; shift 2 ;;
    --project) project_id="$2"; shift 2 ;;
    --confirm) confirmation="$2"; shift 2 ;;
    --app) app_apk="$2"; shift 2 ;;
    --test) test_apk="$2"; shift 2 ;;
    --results-bucket) results_bucket="$2"; shift 2 ;;
    --results-dir) results_dir="$2"; shift 2 ;;
    *) echo "Argumento desconocido: $1" >&2; exit 2 ;;
  esac
done

[[ "$confirmation" == "EJECUTAR_TEST_LAB" ]] || { echo "Confirmación inválida." >&2; exit 2; }
[[ "$kind" == "virtual" || "$kind" == "physical" ]] || { echo "MATRIX_KIND debe ser virtual o physical." >&2; exit 2; }
[[ -n "$project_id" ]] || { echo "Falta proyecto dedicado de Test Lab." >&2; exit 2; }
[[ -n "$results_bucket" ]] || { echo "Falta bucket dedicado para resultados temporales." >&2; exit 2; }
[[ -f "$app_apk" && -f "$test_apk" ]] || { echo "Faltan APK de app o instrumentación." >&2; exit 2; }
if [[ "$kind" == "physical" && "${ALLOW_PHYSICAL_TESTS:-false}" != "true" ]]; then
  echo "La matriz física exige ALLOW_PHYSICAL_TESTS=true además de la confirmación." >&2
  exit 2
fi

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
catalog="$root_dir/build/compatibility/firebase-device-catalog.json"
"$root_dir/scripts/android_compatibility/query_catalog.sh" "$project_id" "$catalog" >/dev/null
locales="$root_dir/build/compatibility/firebase-android-locales.json"

min_api="$(sed -n 's/^val minSupportedApi = \([0-9][0-9]*\)$/\1/p' "$root_dir/app-user/build.gradle.kts")"
target_api="$(sed -n 's/^val targetSupportedApi = \([0-9][0-9]*\)$/\1/p' "$root_dir/app-user/build.gradle.kts")"
admin_min_api="$(sed -n 's/^val minSupportedApi = \([0-9][0-9]*\)$/\1/p' "$root_dir/app-admin/build.gradle.kts")"
admin_target_api="$(sed -n 's/^val targetSupportedApi = \([0-9][0-9]*\)$/\1/p' "$root_dir/app-admin/build.gradle.kts")"
[[ -n "$min_api" && -n "$target_api" ]] || { echo "No se pudo resolver el rango Android soportado." >&2; exit 3; }
[[ "$min_api" == "$admin_min_api" && "$target_api" == "$admin_target_api" ]] || {
  echo "Usuario y Admin declaran rangos Android distintos; se requiere una matriz explícita por app." >&2
  exit 3
}
config="$root_dir/scripts/android_compatibility/config/${kind}-matrix.json"
resolved=()
while IFS= read -r row; do
  resolved+=("$row")
done < <(
  python3 "$root_dir/scripts/android_compatibility/resolve_matrix.py" \
    --catalog "$catalog" --locales "$locales" --config "$config" --kind "$kind" \
    --min-api "$min_api" --target-api "$target_api"
)
(( ${#resolved[@]} > 0 )) || { echo "La matriz resuelta quedó vacía." >&2; exit 3; }

device_args=()
for row in "${resolved[@]}"; do
  device_args+=(--device "${row#*$'\t'}")
done

mkdir -p "$root_dir/build/compatibility"
gcloud firebase test android run \
  --project "$project_id" \
  --type instrumentation \
  --app "$app_apk" \
  --test "$test_apk" \
  "${device_args[@]}" \
  --timeout 12m \
  --num-flaky-test-attempts 0 \
  --no-record-video \
  --no-performance-metrics \
  --results-bucket "$results_bucket" \
  --results-dir "$results_dir" \
  --format=json | tee "$root_dir/build/compatibility/${results_dir}.json"
