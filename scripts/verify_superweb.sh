#!/usr/bin/env bash
set -euo pipefail

base_url="${1:-https://web-super-admin-nine.vercel.app}"
expected_commit="${2:-}"
expected_project_ref="syeycayasyufedwoprea"

health_file="$(mktemp)"
trap 'rm -f "$health_file"' EXIT

health_code="$(curl --fail-with-body --silent --show-error --output "$health_file" --write-out '%{http_code}' "$base_url/api/health")"
if [[ "$health_code" != "200" ]]; then
  echo "Superweb health returned HTTP $health_code" >&2
  exit 1
fi

jq -e \
  --arg project "$expected_project_ref" \
  '.status == "ok" and .environment == "DEV" and .supabaseProjectRef == $project' \
  "$health_file" >/dev/null

if [[ -n "$expected_commit" ]]; then
  deployed_commit="$(jq -r '.commit' "$health_file")"
  if [[ "$deployed_commit" != "$expected_commit" ]]; then
    echo "Superweb serves commit $deployed_commit instead of $expected_commit" >&2
    exit 1
  fi
fi

for route in communities dag-usage dag-calibration alerts announcements; do
  headers="$(curl --silent --show-error --head "$base_url/$route")"
  status_code="$(awk 'toupper($1) ~ /^HTTP\// { code=$2 } END { print code }' <<<"$headers")"
  location="$(awk 'tolower($1) == "location:" { print $2 }' <<<"$headers" | tr -d '\r')"
  if [[ "$status_code" != "307" && "$status_code" != "302" ]]; then
    echo "/$route returned HTTP $status_code instead of an authentication redirect" >&2
    exit 1
  fi
  if [[ "$location" != "/login" ]]; then
    echo "/$route redirects to $location instead of /login" >&2
    exit 1
  fi
done

echo "Superweb verified: DEV health and protected routes are current."
