#!/usr/bin/env bash
set -euo pipefail

source_uri="${1:-}"
destination="${2:-build/compatibility/downloaded-results}"
[[ "$source_uri" == gs://* ]] || { echo "Indique un URI gs:// del bucket dedicado." >&2; exit 2; }
command -v gcloud >/dev/null 2>&1 || { echo "gcloud no está instalado." >&2; exit 2; }

mkdir -p "$destination"
gcloud storage cp --recursive "$source_uri" "$destination"
python3 "$(dirname "$0")/summarize_results.py" "$destination" >"$destination/summary.md"
echo "$destination/summary.md"
