# Codex minimal remaining work — Adaptive Autopilot

Architecture/product decisions are closed. Do not redesign.

## Read first
- `docs/REMOTE_INSTALL_ADAPTIVE_AUTOPILOT_ARCHITECTURE.md`
- `docs/artifacts/remote-install-autopilot/reference/AUTOPILOT_STATE_MACHINE.json`
- `docs/artifacts/remote-install-autopilot/reference/SAMSUNG_RECIPE_V1.json`
- `docs/artifacts/remote-install-autopilot/reference/SCENARIO_MATRIX.md`
- `docs/artifacts/remote-install-autopilot/reference/AdaptiveAutopilotPlanner.kt`
- `docs/artifacts/remote-install-autopilot/reference/AdaptiveAutopilotPlannerTest.kt`
- Python reference engine/tests are also present as a cross-check.

Reference validation completed by ChatGPT before publication:
- Python: **33/33 tests PASS**;
- Kotlin: compiles with `kotlinc` and **35 reference checks PASS**.

## Required local-only work
1. Verify current `work/remote-install-live-guide-03` or successor and exact HEAD/worktree ownership. Do not assume historical `93735b1c...` is still current.
2. Map current Live Guide classes into the frozen modules/roles; preserve useful existing matcher/OEM/pairing code.
3. Port `AdaptiveAutopilotPlanner.kt` into the Android Remote module rather than recreating the state logic.
4. Implement the Samsung Android bindings under `tools/glosh-remote-spike/**`: window selection, immutable snapshots, classifier, fresh-node resolver and exact ACTION_CLICK execution.
5. Do not modify broker/relay/HMAC/AES/allowlist/no-link connection base.
6. Port the reference cases into Android/JVM equivalents and add real A23 tree fixtures only where the platform binding needs them.
7. Run Android tests, Python/broker regression tests, lint and assemble.
8. A23 physical gate: start from each relevant precondition (Developer Options OFF; Developer Options ON/Wireless OFF; Wireless ON; pairing dialog already open; credential interruption) with zero wrong clicks.
9. Stop at device credential for user input; resume automatically afterward.
10. Pair automatically when exactly one contextual six-digit code is readable; manual six-box fallback otherwise.
11. Once local ADB is connected, hand off to the existing proven connection stack and verify only a regression smoke, not redesign/rebuild it.
12. Produce exact APK SHA and Taildrop the same APK to S22 for cable-free UX confirmation.

## Gate rule

**One wrong automatic click = FAIL.** A safe stop/fallback on ambiguity is expected behavior, not failure.

No push/PR/merge/deploy/Production/Supabase.
