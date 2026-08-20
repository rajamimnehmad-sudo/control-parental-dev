#!/bin/bash

set -eu

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
RUNNER="$SCRIPT_DIR/glosh-ai-autorun"
TEST_ROOT="$(mktemp -d /tmp/glosh-ai-autorun-test.XXXXXX)"
trap 'rm -rf "$TEST_ROOT"' EXIT HUP INT TERM

export GLOSH_AI_AUTORUN_HOME="$TEST_ROOT/state"
export GLOSH_AI_AUTORUN_TEST_TICKET="$TEST_ROOT/AI_NEXT_TICKET.md"
export GLOSH_AI_AUTORUN_TEST_EXEC_LOG="$TEST_ROOT/executions.log"
export GLOSH_AI_AUTORUN_TEST_MODE=1
export GLOSH_AI_AUTORUN_TEST_SHA="self-test-sha-001"

printf '# AI NEXT TICKET\n\n## SELF-TEST-01\n' > "$GLOSH_AI_AUTORUN_TEST_TICKET"
: > "$GLOSH_AI_AUTORUN_TEST_EXEC_LOG"
mkdir -p "$GLOSH_AI_AUTORUN_HOME"
printf 'repo=%s\ncodex_bin=%s\n' "$TEST_ROOT/fake-repo" "/usr/bin/false" > "$GLOSH_AI_AUTORUN_HOME/config"

export GLOSH_AI_AUTORUN_TEST_SLEEP=2
"$RUNNER" run-once &
first_pid=$!
sleep 1
"$RUNNER" run-once
wait "$first_pid"

export GLOSH_AI_AUTORUN_TEST_SLEEP=0
"$RUNNER" run-once

count="$(wc -l < "$GLOSH_AI_AUTORUN_TEST_EXEC_LOG" | tr -d ' ')"
[ "$count" = "1" ] || { printf 'FAIL: expected one execution, got %s\n' "$count"; exit 1; }

status="$($RUNNER status)"
printf '%s\n' "$status" | grep -q '^executed_sha=self-test-sha-001$'
printf '%s\n' "$status" | grep -q '^status=completed$'
[ ! -d "$GLOSH_AI_AUTORUN_HOME/run.lock" ]

printf 'PASS self-test: detection=1 execution=1 duplicate=0 single-flight=PASS state=completed\n'
