#!/usr/bin/env bash
set -euo pipefail

repo_root="$(git rev-parse --show-toplevel)"
cd "$repo_root"

find_manifest() {
  local variant="$1"
  find app-user/build/intermediates -type f -name AndroidManifest.xml -path "*/${variant}/*" -print |
    while IFS= read -r candidate; do
      if rg -q '<manifest' "$candidate"; then
        printf '%s\n' "$candidate"
        break
      fi
    done
}

dev_manifest="$(find_manifest devDebug)"
beta_manifest="$(find_manifest betaDebug)"
prod_manifest="$(find_manifest prodDebug)"

if [[ -z "$dev_manifest" || -z "$beta_manifest" || -z "$prod_manifest" ]]; then
  echo "ERROR: one or more merged manifests are missing" >&2
  exit 1
fi

if ! rg -q 'com\.contentfilter\.user\.dag2\.DagV2LabActivity' "$dev_manifest"; then
  echo "ERROR: DEV merged manifest does not include DAG v2 Lab" >&2
  exit 1
fi
if rg -q 'com\.contentfilter\.user\.dag2|dag-v2-calibration' "$beta_manifest" "$prod_manifest"; then
  echo "ERROR: Beta or Production exposes DAG v2 calibration" >&2
  exit 1
fi

app_build="app-user/build.gradle.kts"
if [[ "$(rg -F -c 'DAG_V2_CALIBRATION_AVAILABLE", "true' "$app_build")" != "1" ]]; then
  echo "ERROR: calibration must be true in DEV exactly once" >&2
  exit 1
fi
if [[ "$(rg -F -c 'DAG_V2_CALIBRATION_AVAILABLE", "false' "$app_build")" != "2" ]]; then
  echo "ERROR: calibration must be false in Beta and Production" >&2
  exit 1
fi
if ! rg -F -q 'add("devImplementation", project(":feature-dag2"))' "$app_build"; then
  echo "ERROR: feature-dag2 must remain a DEV-only dependency" >&2
  exit 1
fi

echo "DAG v2 calibration packaging verified: DEV only; Beta and Production absent."
