#!/bin/bash

set -eu

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
RUNNER="$SCRIPT_DIR/glosh-ai-autorun"
TEST_ROOT="$(mktemp -d /tmp/glosh-ai-autorun-test.XXXXXX)"
trap 'rm -rf "$TEST_ROOT"' EXIT HUP INT TERM

if [ "${1:-}" = "--smoke" ]; then
  export GLOSH_AI_AUTORUN_HOME="$TEST_ROOT/smoke/state"
  export GLOSH_AI_AUTORUN_TEST_TICKET="$TEST_ROOT/smoke/AI_NEXT_TICKET.md"
  export GLOSH_AI_AUTORUN_TEST_SHA="smoke-read-only-001"
  export GLOSH_AI_AUTORUN_SMOKE_MODE=1
  mkdir -p "$GLOSH_AI_AUTORUN_HOME"
  printf '# AI NEXT TICKET\n\n## AUTORUN-SMOKE\n\n**Esfuerzo Codex:** Bajo\n' > "$GLOSH_AI_AUTORUN_TEST_TICKET"
  printf 'repo=%s\ncodex_bin=%s\n' "$TEST_ROOT/fake-repo" "/Applications/ChatGPT.app/Contents/Resources/codex" > "$GLOSH_AI_AUTORUN_HOME/config"
  "$RUNNER" run-once
  grep -q 'AUTORUN_SMOKE_PASS' "$GLOSH_AI_AUTORUN_HOME/logs/codex-exec.log"
  status="$($RUNNER status)"
  printf '%s\n' "$status" | grep -q '^applied_effort=low$'
  printf '%s\n' "$status" | grep -q '^status=completed$'
  printf 'PASS smoke: '
  printf '%s\n' "$status" | grep '^token_usage='
  exit 0
fi

export GLOSH_AI_AUTORUN_TEST_MODE=1

run_effort_case() {
  case_id="$1"
  declared="$2"
  expected="$3"
  expected_model="$4"
  export GLOSH_AI_AUTORUN_HOME="$TEST_ROOT/$case_id/state"
  export GLOSH_AI_AUTORUN_TEST_TICKET="$TEST_ROOT/$case_id/AI_NEXT_TICKET.md"
  export GLOSH_AI_AUTORUN_TEST_EXEC_LOG="$TEST_ROOT/$case_id/executions.log"
  export GLOSH_AI_AUTORUN_TEST_SHA="self-test-$case_id"
  mkdir -p "$GLOSH_AI_AUTORUN_HOME"
  {
    printf '# AI NEXT TICKET\n\n## SELF-TEST-%s\n' "$case_id"
    if [ -n "$declared" ]; then
      printf '\n**Esfuerzo Codex:** %s\n' "$declared"
    fi
  } > "$GLOSH_AI_AUTORUN_TEST_TICKET"
  : > "$GLOSH_AI_AUTORUN_TEST_EXEC_LOG"
  printf 'repo=%s\ncodex_bin=%s\n' "$TEST_ROOT/fake-repo" "/usr/bin/false" > "$GLOSH_AI_AUTORUN_HOME/config"
  "$RUNNER" run-once
  status="$($RUNNER status)"
  printf '%s\n' "$status" | grep -q "^applied_effort=$expected$"
  printf '%s\n' "$status" | grep -q "^effective_model=$expected_model$"
  grep -q " $expected lean $expected_model$" "$GLOSH_AI_AUTORUN_TEST_EXEC_LOG"
}

run_effort_case bajo Bajo low gpt-5.6-luna
run_effort_case medio Medio medium gpt-5.6-terra
run_effort_case alto Alto high gpt-5.6-sol
run_effort_case extra_alto 'Extra alto' xhigh gpt-5.6-sol
run_effort_case invalid Invalido medium gpt-5.6-terra
run_effort_case missing '' medium gpt-5.6-terra

FAKE_CODEX="$TEST_ROOT/fake-codex"
FAKE_ARGS="$TEST_ROOT/fake-codex.args"
export GLOSH_AI_AUTORUN_FAKE_ARGS="$FAKE_ARGS"
printf '%s\n' '#!/bin/bash' 'printf "CODEX_HOME=%s ARGS=%s\n" "${CODEX_HOME:-}" "$*" >> "${GLOSH_AI_AUTORUN_FAKE_ARGS:?}"' 'printf "%s\n" '\''{"type":"turn.completed","usage":{"input_tokens":120,"cached_input_tokens":40,"output_tokens":20}}'\''' > "$FAKE_CODEX"
chmod 700 "$FAKE_CODEX"

export GLOSH_AI_AUTORUN_HOME="$TEST_ROOT/cli-args/state"
export GLOSH_AI_AUTORUN_TEST_TICKET="$TEST_ROOT/cli-args/AI_NEXT_TICKET.md"
export GLOSH_AI_AUTORUN_TEST_SHA="self-test-cli-args"
export GLOSH_AI_AUTORUN_SMOKE_MODE=1
unset GLOSH_AI_AUTORUN_TEST_MODE
mkdir -p "$GLOSH_AI_AUTORUN_HOME"
printf '# AI NEXT TICKET\n\n## SELF-TEST-CLI\n\n**Esfuerzo Codex:** Bajo\n' > "$GLOSH_AI_AUTORUN_TEST_TICKET"
: > "$FAKE_ARGS"
printf 'repo=%s\ncodex_bin=%s\n' "$TEST_ROOT/fake-repo" "$FAKE_CODEX" > "$GLOSH_AI_AUTORUN_HOME/config"
"$RUNNER" run-once
grep -q -- '--ignore-user-config' "$FAKE_ARGS"
grep -q -- '--ephemeral' "$FAKE_ARGS"
grep -q -- '--json' "$FAKE_ARGS"
grep -q -- 'model_reasoning_effort="low"' "$FAKE_ARGS"
grep -q -- '--model gpt-5.6-luna' "$FAKE_ARGS"
grep -q "CODEX_HOME=$GLOSH_AI_AUTORUN_HOME/codex-home-lean" "$FAKE_ARGS"
grep -q -- '--disable plugins' "$FAKE_ARGS"
status="$($RUNNER status)"
printf '%s\n' "$status" | grep -q '^token_usage=input:120,cached_input:40,output:20,total:140$'

export GLOSH_AI_AUTORUN_TEST_MODE=1
unset GLOSH_AI_AUTORUN_SMOKE_MODE
export GLOSH_AI_AUTORUN_HOME="$TEST_ROOT/single-flight/state"
export GLOSH_AI_AUTORUN_TEST_TICKET="$TEST_ROOT/single-flight/AI_NEXT_TICKET.md"
export GLOSH_AI_AUTORUN_TEST_EXEC_LOG="$TEST_ROOT/single-flight/executions.log"
export GLOSH_AI_AUTORUN_TEST_SHA="self-test-sha-001"
mkdir -p "$GLOSH_AI_AUTORUN_HOME"
printf '# AI NEXT TICKET\n\n## SELF-TEST-01\n\n**Esfuerzo Codex:** Medio\n' > "$GLOSH_AI_AUTORUN_TEST_TICKET"
: > "$GLOSH_AI_AUTORUN_TEST_EXEC_LOG"
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
printf '%s\n' "$status" | grep -q '^applied_effort=medium$'
[ ! -d "$GLOSH_AI_AUTORUN_HOME/run.lock" ]

printf 'PASS self-test: effort=6/6 cli_args=PASS usage_parser=PASS detection=1 execution=1 duplicate=0 single-flight=PASS state=completed\n'
