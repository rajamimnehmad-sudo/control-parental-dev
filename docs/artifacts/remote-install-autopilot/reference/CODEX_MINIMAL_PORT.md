# Codex minimal remaining work — Adaptive Autopilot

Architecture/product decisions are closed. Do not redesign.

## Read first
- `docs/REMOTE_INSTALL_ADAPTIVE_AUTOPILOT_ARCHITECTURE.md`
- `docs/artifacts/remote-install-autopilot/reference/AUTOPILOT_STATE_MACHINE.json`
- `docs/artifacts/remote-install-autopilot/reference/SAMSUNG_RECIPE_V1.json`
- `docs/artifacts/remote-install-autopilot/reference/SCENARIO_MATRIX.md`
- `docs/artifacts/remote-install-autopilot/reference/adaptive_engine.py`
- `docs/artifacts/remote-install-autopilot/reference/test_adaptive_engine.py`

Reference engine was executed by ChatGPT before publication: **33/33 tests PASS**.

## Required local-only work
1. Verify current `work/remote-install-live-guide-03` or successor and exact HEAD/worktree ownership.
2. Map current Live Guide classes into the frozen modules/roles; preserve useful existing matcher/OEM/pairing code.
3. Implement Autopilot-first Samsung route under `tools/glosh-remote-spike/**` only.
4. Do not modify broker/relay/HMAC/AES/allowlist/no-link connection base.
5. Port the 33 reference cases into Android/JVM equivalents and add tree fixtures from the A23 where necessary.
6. Run Android tests, Python/broker regression tests, lint and assemble.
7. A23 physical gate: repeated start from each relevant precondition (dev off, dev on/wireless off, wireless on, pairing dialog) with zero wrong clicks.
8. Stop at device credential for user input; resume automatically afterward.
9. Pair automatically when exactly one contextual six-digit code is readable; manual six-box fallback otherwise.
10. Once local ADB is connected, use the existing proven connection stack and verify no regression.
11. Produce exact APK SHA and Taildrop the same APK to S22 for cable-free UX confirmation.

## One wrong automatic click = FAIL

No push/PR/merge/deploy/Production/Supabase.
