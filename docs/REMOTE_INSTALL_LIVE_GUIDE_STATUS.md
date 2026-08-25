# Glosh Remote — Adaptive Remote Installer

Updated: 2026-08-24 22:47 ART

## Connection base

`REMOTE-INSTALL-CONNECTION-00`: **PASS FINAL DEV / CLOSED**.

The proven no-link stack remains frozen: broker → relay/WSS → Glosh Remote → local Wireless ADB, with HMAC/AES, fixed allowlist and no direct ADB exposure. Adaptive Autopilot must reuse it, not redesign it.

## Superseded guide

`REMOTE-INSTALL-LIVE-GUIDE-03`: **FAILED UX PHYSICAL / SUPERSEDED**.

Historical implementation chain before Autopilot:
`475bd35b... → 1a537f7a... → 99fcf2bb... → 122c45b9... → 93735b1c...`

A prior Codex run left a technically buildable guide-first candidate and proposed rerunning Android/Python tests, `lintDebug`, `assembleDebug`, generating an APK and doing a physical S22 trial. That candidate is preserved but **must not be used as the final UX trial**, because guide-first was superseded afterward by Adaptive Autopilot.

## Active product route — Adaptive Autopilot

`REMOTE-INSTALL-LIVE-GUIDE-V2-04`: **PAUSED / PRE-CODEX REFERENCE COMPLETE / ANDROID PORT PENDING**.

Pause reason: the available phones are currently reserved by another project front. This is an intentional resource pause, not a functional BLOCKED state and not a failure.

Product direction is frozen as:

**Samsung-first implementation + universal state engine + shortest-path Autopilot + minimal guided fallback.**

Normal target UX after the one-time Accessibility bootstrap:

`INICIAR → detect real state → shortest safe path → enable Wireless Debugging only if needed → open pairing dialog → auto-read one contextual 6-digit code when safe → existing local pairing/ADB → existing secure support connection`.

Mandatory shortcuts/preconditions:
- support already connected → DONE;
- valid local ADB → skip Settings/pairing;
- previous pairing → try reconnect before opening Settings;
- Restricted Settings blocks Accessibility → user clears prerequisite;
- Accessibility disabled → user enables it once;
- no usable Wi-Fi → request Wi-Fi;
- Wireless Debugging blocked by policy → safe BLOCKED result;
- Developer Options already available → skip Build Number;
- Wireless Debugging already ON → skip its toggle;
- pairing dialog already open → pair immediately;
- credential prompt → wait for user credential, then resume from a fresh probe.

## Authoritative GitHub reference

Branch:
`coordination/remote-install-live-guide-v2`

Architecture/status:
- `docs/REMOTE_INSTALL_ADAPTIVE_AUTOPILOT_ARCHITECTURE.md`
- `docs/REMOTE_INSTALL_AUTOPILOT_DECISION.md`
- `docs/REMOTE_INSTALL_LIVE_GUIDE_STATUS.md`

Reference implementation:
- `docs/artifacts/remote-install-autopilot/reference/AUTOPILOT_STATE_MACHINE.json`
- `docs/artifacts/remote-install-autopilot/reference/SAMSUNG_RECIPE_V1.json`
- `docs/artifacts/remote-install-autopilot/reference/SCENARIO_MATRIX.md`
- `docs/artifacts/remote-install-autopilot/reference/adaptive_engine.py`
- `docs/artifacts/remote-install-autopilot/reference/test_adaptive_engine.py`
- `docs/artifacts/remote-install-autopilot/reference/AdaptiveAutopilotPlanner.kt`
- `docs/artifacts/remote-install-autopilot/reference/AdaptiveAutopilotPlannerTest.kt`
- `docs/artifacts/remote-install-autopilot/reference/AutopilotUiModel.kt`
- `docs/artifacts/remote-install-autopilot/reference/SettingsWindowSelector.kt`
- `docs/artifacts/remote-install-autopilot/reference/SamsungSettingsClassifier.kt`
- `docs/artifacts/remote-install-autopilot/reference/PairingCodeDetector.kt`
- `docs/artifacts/remote-install-autopilot/reference/AutopilotActionGate.kt`
- `docs/artifacts/remote-install-autopilot/reference/AutopilotUiPureTest.kt`
- `docs/artifacts/remote-install-autopilot/reference/CODEX_MINIMAL_PORT.md`
- `docs/artifacts/remote-install-autopilot/reference/REFERENCE_VALIDATION.md`

Reference validation completed by ChatGPT:
- Python planner/state: **38/38 PASS**;
- Kotlin planner: **40/40 reference checks PASS** after `kotlinc` compilation;
- Kotlin pure UI/safety layer: **18/18 PASS** after `kotlinc` compilation.

## Safety/action contract

Every automatic click is a verified transaction:

`stable trusted Settings snapshot → HIGH/unique classification → fresh live-node reacquisition → one ACTION_CLICK → invalidate generation → observe an allowed next state`.

One wrong automatic click = FAIL. PIN/password/pattern are never automated or captured. Ambiguity causes a safe stop/fallback.

## Device strategy after pause

### A23
Samsung SM-A235M / Android 14 / API 34 is currently unsuitable for the Accessibility physical gate because its existing Device Owner policy blocks the Glosh Remote AccessibilityService. Do not change/remove Device Owner merely to make this test pass while the A23 is serving another front.

### S22
Use the S22 later as the primary cable-free real-user Samsung UX gate once it is available. No USB is required for that product-experience trial.

## Resume sequence

When the front is resumed:
1. Codex checks the actual Remote Installer branch/worktree/owner and preserves any interrupted local work.
2. Port the frozen planner + pure UI reference into `tools/glosh-remote-spike/**`; do not redesign.
3. Implement only the Android binding from `AccessibilityWindowInfo`/`AccessibilityNodeInfo` to immutable snapshots and fresh-node click execution.
4. Run all Android/JVM tests plus Python/broker regressions.
5. Run `lintDebug` and `assembleDebug`.
6. Create a clean local commit and exact APK path/size/SHA-256.
7. Do not spend a physical trial on the superseded guide-first APK.
8. When S22 is free, send the exact Adaptive Autopilot APK to it and run one cable-free real-user gate.
9. Reuse the frozen PASS connection stack once local ADB is ready.
10. Return diff/tests/evidence to ChatGPT for review before final closure.

No push/PR/merge/deploy/Production/Supabase.

## Coordination

- `REMOTE-INSTALL-CONNECTION-00`: PASS FINAL DEV / CLOSED.
- `REMOTE-INSTALL-LIVE-GUIDE-03`: FAILED UX / SUPERSEDED.
- `REMOTE-INSTALL-LIVE-GUIDE-V2-04`: PAUSED intentionally; reference complete, Android port pending, no active writer now.
- `REMOTE-INSTALL-MAC-OPERATOR-04`: preserved; waits for installer UX stability.
- `REMOTE-INSTALL-PRECHECK-05`, `REMOTE-INSTALL-PIPELINE-06`, `REMOTE-INSTALL-DEVICE-OWNER-COMMIT-07`: preserved.
- `REMOTE-ADAPTIVE-INSTALL-PILOT-01`: pending Adaptive Autopilot S22 cable-free gate.
- Do not touch Chrome, GloshIA, DAG, App Usuario/Admin, Supabase or production Device Owner logic.
