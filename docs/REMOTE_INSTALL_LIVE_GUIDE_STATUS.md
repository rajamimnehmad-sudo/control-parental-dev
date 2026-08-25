# Glosh Remote — Adaptive Remote Installer

Updated: 2026-08-24 21:15 ART

## Connection base

`REMOTE-INSTALL-CONNECTION-00`: **PASS FINAL DEV / CLOSED**.

The already proven no-link connection remains frozen: broker → relay/WSS → Glosh Remote → local Wireless ADB, with HMAC/AES, allowlist and no direct ADB exposure. Autopilot must not redesign this stack.

## Old guide

`REMOTE-INSTALL-LIVE-GUIDE-03`: **FAILED UX PHYSICAL / SUPERSEDED**.

Historical pre-Autopilot implementation chain:
`475bd35b... → 1a537f7a... → 99fcf2bb... → 122c45b9... → 93735b1c...`

The read-only Codex audit at `93735b1c2a9f493dcd491556b23920c50c3d66f2` confirmed the structural risks already recorded: active-root authority, live node retention, missing generation/window/fingerprint guard, stale callbacks and uncontrolled scrolling.

## Active route — Adaptive Autopilot

`REMOTE-INSTALL-LIVE-GUIDE-V2-04`: **PRE-CODEX REFERENCE COMPLETE / SAMSUNG ANDROID PORT PENDING**.

Product direction is frozen as:

**Samsung-first implementation + universal state engine + minimal guided fallback.**

Normal UX after one-time Accessibility bootstrap:

`INICIAR → detect real state → shortest safe Settings path → enable Wireless Debugging if needed → open pairing dialog → auto-read exactly one contextual 6-digit code when possible → existing local pairing/ADB → existing secure support connection`.

Shortest-path prerequisites/shortcuts are mandatory:
- support already connected → DONE;
- valid local ADB → skip Settings/pairing;
- previous pairing exists → try reconnect before opening Settings;
- Accessibility blocked by Restricted Settings → user clears that prerequisite first;
- Accessibility disabled → user enables it once;
- no usable Wi-Fi → stop and ask for Wi-Fi;
- Wireless Debugging blocked by device/admin policy → BLOCKED, no guessed workaround;
- Developer Options already available → skip Build Number;
- Wireless Debugging already ON → skip toggle/confirmation;
- pairing dialog already open → pair immediately;
- credential prompt → user enters credential, then Autopilot resumes from a fresh probe.

Do not infer Developer Options from `Settings.Global.DEVELOPMENT_SETTINGS_ENABLED`; Android reports it as 0 to third-party apps. Use public Settings intents + trusted UI classification.

## Authoritative GitHub reference

Branch:
`coordination/remote-install-live-guide-v2`

Architecture:
- `docs/REMOTE_INSTALL_ADAPTIVE_AUTOPILOT_ARCHITECTURE.md`
- `docs/REMOTE_INSTALL_AUTOPILOT_DECISION.md`

Planner/state reference:
- `docs/artifacts/remote-install-autopilot/reference/AUTOPILOT_STATE_MACHINE.json`
- `docs/artifacts/remote-install-autopilot/reference/SAMSUNG_RECIPE_V1.json`
- `docs/artifacts/remote-install-autopilot/reference/SCENARIO_MATRIX.md`
- `docs/artifacts/remote-install-autopilot/reference/adaptive_engine.py`
- `docs/artifacts/remote-install-autopilot/reference/test_adaptive_engine.py`
- `docs/artifacts/remote-install-autopilot/reference/AdaptiveAutopilotPlanner.kt`
- `docs/artifacts/remote-install-autopilot/reference/AdaptiveAutopilotPlannerTest.kt`

Pure Kotlin UI/safety reference:
- `docs/artifacts/remote-install-autopilot/reference/AutopilotUiModel.kt`
- `docs/artifacts/remote-install-autopilot/reference/SettingsWindowSelector.kt`
- `docs/artifacts/remote-install-autopilot/reference/SamsungSettingsClassifier.kt`
- `docs/artifacts/remote-install-autopilot/reference/PairingCodeDetector.kt`
- `docs/artifacts/remote-install-autopilot/reference/AutopilotActionGate.kt`
- `docs/artifacts/remote-install-autopilot/reference/AutopilotUiPureTest.kt`
- `docs/artifacts/remote-install-autopilot/reference/CODEX_MINIMAL_PORT.md`

Reference validation by ChatGPT:
- Python planner/state: **38/38 tests PASS**;
- Kotlin planner: **40 reference checks PASS** after successful `kotlinc` compilation;
- Kotlin pure UI/safety layer: **18/18 checks PASS** after successful `kotlinc` compilation.

The old binary `glosh_remote_live_guide_v2_prototype.zip` is deprecated and must not be used.

## Safety/action contract

Every automatic click is a separate verified transaction:

`stable trusted Settings snapshot → classify HIGH/unique → fresh node reacquisition → exactly one ACTION_CLICK → invalidate generation → observe allowed next state`.

Requirements:
- exactly one trusted Settings `TYPE_APPLICATION` window;
- overlay/IME/unrelated apps excluded;
- immutable logical snapshots;
- current generation/window/fingerprint token;
- OEM recipe allows the target;
- HIGH confidence, unique winner, margin over second candidate;
- fresh live node before action;
- known expected next-state set;
- one wrong automatic click = FAIL.

Never automate device PIN/password/pattern. Never guess an ambiguous screen or pairing code.

## Samsung v1 path

Fast path first:
1. Existing ADB/reconnect probe.
2. `ACTION_APPLICATION_DEVELOPMENT_SETTINGS`.
3. If Developer Options recognized → `Depuración inalámbrica`.
4. If unavailable → `ACTION_DEVICE_INFO_SETTINGS` → `Información de software` → `Número de compilación` up to 7 individually revalidated taps.
5. Credential prompt, if any, pauses for user.
6. Re-open Developer Settings directly.
7. Enable Wireless Debugging only if OFF.
8. Accept exact expected network confirmation when safely classified.
9. Open `Vincular dispositivo con código de vinculación`.
10. Exactly one contextual 6-digit code → existing pairing service automatically; otherwise six-box manual fallback.
11. Existing secure connection stack takes over once ADB is ready.

## Scope / universality

The core state engine is universal. Samsung is the only OEM-specific automatic enable-development recipe implemented in this cycle. Unsupported OEMs may still use common fast paths when Developer Options/Wireless Debugging are already reachable; otherwise they fail closed into guided fallback. Motorola/Xiaomi become later adapter additions, not state-machine rewrites.

## Next work that genuinely requires Codex/Mac

Only Android/environment-bound work remains:
1. verify actual current local Remote Installer HEAD/worktree and owner;
2. port the frozen planner + pure UI classes into `tools/glosh-remote-spike/**`, preserving useful current guide/pairing code;
3. implement the thin Android adapter from `AccessibilityWindowInfo`/`AccessibilityNodeInfo` into the immutable reference model and fresh live-node resolver before `ACTION_CLICK`;
4. port full reference coverage to Android/JVM tests;
5. compile/lint/regression tests;
6. use Samsung A23 SM-A235M / Android 14 / API 34 as instrumented lab device;
7. physically validate all important start states with **zero wrong automatic clicks**;
8. verify automatic code pairing when unambiguous and manual six-box fallback otherwise;
9. reuse the frozen PASS connection base once local ADB is ready;
10. generate exact APK and send the same candidate to S22 for cable-free real-user UX validation;
11. return diff/tests/evidence for ChatGPT review.

No push/PR/merge/deploy/Production/Supabase.

## Coordination

- `REMOTE-INSTALL-CONNECTION-00`: PASS FINAL DEV / CLOSED.
- `REMOTE-INSTALL-LIVE-GUIDE-03`: FAILED UX / SUPERSEDED.
- `REMOTE-INSTALL-LIVE-GUIDE-V2-04`: PRE-CODEX REFERENCE COMPLETE; Samsung Android port is next.
- `REMOTE-INSTALL-MAC-OPERATOR-04`: preserved; waits for installer UX stability.
- `REMOTE-INSTALL-PRECHECK-05`, `REMOTE-INSTALL-PIPELINE-06`, `REMOTE-INSTALL-DEVICE-OWNER-COMMIT-07`: preserved.
- `REMOTE-ADAPTIVE-INSTALL-PILOT-01`: waits for A23 Autopilot physical gate + S22 cable-free UX confirmation.
- Do not touch Chrome, GloshIA, DAG, App Usuario/Admin, Supabase or production Device Owner logic.
