# Glosh Remote — Adaptive Remote Installer

Updated: 2026-08-24 22:55 ART

## Connection base

`REMOTE-INSTALL-CONNECTION-00`: **PASS FINAL DEV / CLOSED**.

The proven no-link stack remains frozen: broker → relay/WSS → Glosh Remote → local Wireless ADB, with HMAC/AES, fixed allowlist and no direct ADB exposure. Adaptive Autopilot reuses this stack and does not redesign it.

## Superseded guide

`REMOTE-INSTALL-LIVE-GUIDE-03`: **FAILED UX PHYSICAL / SUPERSEDED**.

Historical implementation chain before Autopilot:
`475bd35b... → 1a537f7a... → 99fcf2bb... → 122c45b9... → 93735b1c...`

The previous guide-first UX remains superseded. Its useful matcher/pairing pieces may be reused only where compatible with Adaptive Autopilot.

## Active product route — Adaptive Autopilot

`REMOTE-INSTALL-LIVE-GUIDE-V2-04`: **PASS AUTOMATED / PENDING S22 PHYSICAL GATE**.

Codex completed the environment-bound Android integration without using a phone for the final gate.

Exact local result:
- HEAD final: `eaa44f1ba5da204d66a720ae6f5f805699ee22ee`;
- local commit: `feat(remote): add adaptive Samsung install autopilot`;
- Android tests: **83/83 PASS**;
- Python/broker regression: **6/6 PASS**;
- `lintDebug`: **0 errors**, 11 warnings reported as pre-existing/external;
- `assembleDebug`: **PASS**;
- APK: `GloshRemote-LiveGuide-V2-DEV.apk`;
- APK size: `19,287,534` bytes;
- APK SHA-256: `ca222dbfabaa776a9e304c0e643923caf5ce394ad785a72481c8a58654b14920`;
- worktree: clean;
- relay/Quick Tunnel: closed;
- broker: `available=false`;
- no push / PR / merge / deploy;
- Taildrop to S22: not sent yet.

This is an **automated technical PASS**, not final product closure. The only remaining acceptance gate for this candidate is a single real-user physical trial on the S22 without USB, using this exact APK SHA.

Product direction remains frozen as:

**Samsung-first implementation + universal state engine + shortest-path Autopilot + minimal guided fallback.**

Target UX after one-time Accessibility bootstrap:

`INICIAR → detect real state → shortest safe path → enable Wireless Debugging only if needed → open pairing dialog → auto-read one contextual 6-digit code when safe → existing local pairing/ADB → existing secure support connection`.

Mandatory shortcuts/preconditions remain:
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

Pre-port reference validation completed by ChatGPT:
- Python planner/state: **38/38 PASS**;
- Kotlin planner: **40/40 reference checks PASS**;
- Kotlin pure UI/safety layer: **18/18 PASS**.

## Safety/action contract

Every automatic click remains a verified transaction:

`stable trusted Settings snapshot → HIGH/unique classification → fresh live-node reacquisition → one ACTION_CLICK → invalidate generation → observe an allowed next state`.

One wrong automatic click = FAIL. PIN/password/pattern are never automated or captured. Ambiguity causes a safe stop/fallback.

## Device strategy

### A23
Samsung SM-A235M / Android 14 / API 34 is not required for the remaining gate. Its current Device Owner policy blocks the Glosh Remote AccessibilityService, so it should not be modified merely to validate this candidate while serving another front.

### S22 — only remaining gate
Use the S22 as the cable-free real-user Samsung gate.

Requirements:
1. use exactly `GloshRemote-LiveGuide-V2-DEV.apk` with SHA-256 `ca222dbfabaa776a9e304c0e643923caf5ce394ad785a72481c8a58654b14920`;
2. no USB instrumentation;
3. start from Glosh Remote and tap `INICIAR`;
4. verify the Autopilot takes the shortest safe path for the S22's actual current state;
5. no wrong automatic click is allowed;
6. credential/security prompts may pause for the user and then resume;
7. pairing may be automatic when exactly one contextual code is safe, otherwise manual six-box fallback is acceptable;
8. once ADB is ready, reuse the already-proven secure support stack;
9. UX confirmation must come from the user, not only logs.

## Final closure rule

Do not mark `REMOTE-INSTALL-LIVE-GUIDE-V2-04` PASS FINAL until:
- the exact APK SHA above is installed on S22;
- one cable-free physical run completes or yields a reproducible failure;
- ChatGPT reviews the resulting physical evidence and the local diff/code evidence.

No push/PR/merge/deploy/Production/Supabase.

## Coordination

- `REMOTE-INSTALL-CONNECTION-00`: PASS FINAL DEV / CLOSED.
- `REMOTE-INSTALL-LIVE-GUIDE-03`: FAILED UX / SUPERSEDED.
- `REMOTE-INSTALL-LIVE-GUIDE-V2-04`: PASS AUTOMATED; only S22 physical gate pending.
- `REMOTE-INSTALL-MAC-OPERATOR-04`: preserved; waits for installer UX stability.
- `REMOTE-INSTALL-PRECHECK-05`, `REMOTE-INSTALL-PIPELINE-06`, `REMOTE-INSTALL-DEVICE-OWNER-COMMIT-07`: preserved.
- `REMOTE-ADAPTIVE-INSTALL-PILOT-01`: pending the exact-candidate S22 cable-free gate.
- Do not touch Chrome, GloshIA, DAG, App Usuario/Admin, Supabase or production Device Owner logic.
