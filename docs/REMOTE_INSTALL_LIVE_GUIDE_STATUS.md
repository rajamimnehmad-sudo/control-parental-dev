# Glosh Remote — Live Settings Guide

Updated: 2026-08-24 16:50 ART

## REMOTE-INSTALL-LIVE-GUIDE-03

Status: **FAILED UX PHYSICAL / SUPERSEDED BY V2**.

The no-link broker/relay/ADB connection remains PASS FINAL DEV. The physical failure is isolated to the temporary Settings guidance layer.

Observed physical result on Samsung S22:
- Accessibility guide enabled;
- Settings reached `Información de software`;
- Glosh overlay appeared;
- matcher/rescue behavior was unreliable even when `Número de compilación` was visible;
- user reports the floating guide works very poorly;
- no final physical closure is accepted for this implementation.

Authoritative local implementation chain remains:
`475bd35b... → 1a537f7a... → 99fcf2bb... → 122c45b9... → 93735b1c...`

Last reported technical gates on that local branch:
- Android 59/59 PASS;
- Python/broker 6/6 PASS;
- lint 0 errors / 8 warnings;
- assembleDebug PASS.

Those technical gates do not override the failed UX gate.

## REMOTE-INSTALL-LIVE-GUIDE-V2-04

Status: **PRE-CODEX REFERENCE COMPLETE / LOCAL ANDROID INTEGRATION PENDING**.

Professional audit: `docs/REMOTE_INSTALL_LIVE_GUIDE_PRO_AUDIT_04.md`.

Frozen V2 direction:
- progressive assistance instead of continuous autonomy;
- fast-path probe for devices that already expose Wireless Debugging;
- exactly one trusted Settings `TYPE_APPLICATION` window is scan authority;
- `TYPE_ACCESSIBILITY_OVERLAY`, IME and unrelated apps never participate in matching;
- stable fresh snapshots before matching/highlighting;
- monotonic generation/fingerprint guard drops stale async results;
- hidden target never moves the screen until user taps `MOSTRARME`;
- one bounded reveal sequence, max 3 scroll actions, with human-scroll cooldown;
- thin coach bar instead of large draggable card;
- ambiguity means no guess;
- manual six-box PIN remains guaranteed; automatic read is optional only;
- pairing and CONNECTED transitions are fail-closed;
- safe local pilot telemetry without node text or secrets;
- physical validation split into Guide-only, Pairing UX and Full Session gates.

ChatGPT isolated reference package:
- deterministic Samsung/Motorola/Xiaomi/Generic recipes;
- matcher + window selector + stability gate + generation guard + scroll policy + PIN detector + state machine;
- Samsung S22 Spanish fixture;
- Android porting contract;
- acceptance gates;
- interactive HTML mock;
- **30/30 tests PASS**;
- artifact `glosh_remote_live_guide_v2_prototype.zip`;
- size `35,807` bytes;
- SHA-256 `b84c2ddb88bdc0a4d36fc01f5fdbdecc0c32789ec9fc6e005391aa39fb5ce3cd`;
- `HANDOFF_MANIFEST.json` contains per-file SHA-256s.

Next work requiring Codex/Mac:
1. restore/read actual local HEAD `93735b1c...` or successor;
2. answer the audit hypotheses against exact Android code before editing;
3. integrate V2 only under `tools/glosh-remote-spike/**`;
4. run Android tests + Python/broker + lint + assemble;
5. run physical Gate A first; only then B, then C;
6. provide exact diff/evidence for ChatGPT review.

## Coordination

- `REMOTE-INSTALL-CONNECTION-00`: PASS FINAL DEV / CLOSED.
- `REMOTE-INSTALL-LIVE-GUIDE-03`: FAILED UX / SUPERSEDED.
- `REMOTE-INSTALL-LIVE-GUIDE-V2-04`: current active route.
- `REMOTE-INSTALL-MAC-OPERATOR-04`: architecture/prototype preserved; waits until V2 guide gate is stable.
- `REMOTE-INSTALL-PRECHECK-05`, `REMOTE-INSTALL-PIPELINE-06`, `REMOTE-INSTALL-DEVICE-OWNER-COMMIT-07`: designs/prototypes preserved; integration waits for current local Remote HEAD and V2 gate.
- `REMOTE-ADAPTIVE-INSTALL-PILOT-01`: waiting for V2 Samsung gate.
- Do not touch Chrome, GloshIA, DAG, App Usuario/Admin, Supabase or production Device Owner logic.