#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
scope="$root/scripts/android_ci_scope.sh"

assert_scope() {
    local expected="$1"
    local label="$2"
    shift 2
    local actual
    actual="$(printf '%s\n' "$@" | "$scope")"
    if [[ "$actual" != "$expected" ]]; then
        echo "$label: expected $expected, got $actual" >&2
        exit 1
    fi
}

assert_scope none "benchmark tooling" \
    "tools/dag-v2-benchmark/dag_v2_benchmark.py" \
    "tools/dag-v2-benchmark/evidence/04a/summary.json"
assert_scope none "isolated benchmark reviewer" \
    "tools/dag-v2-benchmark/policy-reviewer/app/src/main/AndroidManifest.xml" \
    "tools/dag-v2-benchmark/dag_v2_policy_eval.py" \
    "tools/dag-v2-benchmark/evidence/04b/split.lock.jsonl"
assert_scope none "benchmark documentation" \
    "docs/dag/v2/DAG_V2_NO_GPU_BENCHMARK_RESULTS.md"
assert_scope none "benchmark workflow" \
    ".github/workflows/dag-v2-benchmark-ci.yml" \
    "scripts/test_android_ci_scope.sh"
assert_scope user "user application" \
    "app-user/src/main/java/com/contentfilter/user/MainActivity.kt"
assert_scope admin "admin application" \
    "app-admin/src/main/java/com/contentfilter/admin/MainActivity.kt"
assert_scope both "shared Android module" \
    "core-network/src/main/java/com/contentfilter/core/network/Client.kt"
assert_scope both "both applications" \
    "app-user/build.gradle.kts" \
    "app-admin/build.gradle.kts"
assert_scope user "benchmark plus user" \
    "tools/dag-v2-benchmark/README.md" \
    "app-user/src/main/AndroidManifest.xml"

echo "android_ci_scope_tests=ok"
