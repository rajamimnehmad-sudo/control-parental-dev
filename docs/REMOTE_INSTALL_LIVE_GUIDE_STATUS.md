# Glosh Remote — Live Settings Guide

Updated: 2026-08-24

## REMOTE-INSTALL-LIVE-GUIDE-03

Status: **FAILED UX PHYSICAL / SUPERSEDED BY V2 AUDIT**.

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

Status: **AUDIT + ISOLATED PROTOTYPE COMPLETE / LOCAL ANDROID INTEGRATION PENDING**.

Professional audit: `docs/REMOTE_INSTALL_LIVE_GUIDE_PRO_AUDIT_04.md`.

V2 decisions:
- progressive assistance instead of continuous autonomy;
- fast-path probe for devices that already expose Wireless Debugging;
- scan only trusted Settings application windows;
- exclude `TYPE_ACCESSIBILITY_OVERLAY`, keyboards and unrelated apps;
- wait for stable fresh snapshots before matching/highlighting;
- one-shot scroll only after `MOSTRARME`, with bounded attempts and user-scroll cooldown;
- thin coach bar instead of large floating card;
- ambiguity means no guess;
- manual six-box PIN remains guaranteed; automatic reading is optional only;
- safe local pilot telemetry without node text or secrets;
- physical validation split into Guide-only, Pairing UX and Full Session gates.

ChatGPT isolated prototype:
- deterministic Samsung/Motorola/Xiaomi/Generic recipes;
- matcher, stability gate, scroll policy, PIN detector and state machine;
- interactive HTML mock;
- 19/19 tests PASS;
- artifact `glosh_remote_live_guide_v2_prototype.zip`;
- SHA-256 `ff20a59bc0d25afd8e5595284dd17da7e359f0dcac8123e8a4341077830347f1`.

Next work requiring Codex/Mac:
1. restore/read actual local HEAD `93735b1c...` or successor;
2. audit exact implementation against V2 findings;
3. integrate V2 only under `tools/glosh-remote-spike/**`;
4. run separate physical Gates A/B/C on Samsung S22;
5. provide diff/evidence for ChatGPT review.

## Coordination

- `REMOTE-INSTALL-CONNECTION-00`: PASS FINAL DEV / CLOSED.
- `REMOTE-INSTALL-LIVE-GUIDE-03`: FAILED UX / SUPERSEDED.
- `REMOTE-INSTALL-LIVE-GUIDE-V2-04`: current active route.
- `REMOTE-ADAPTIVE-INSTALL-PILOT-01`: waiting for V2 Samsung gate.
- Do not touch Chrome, GloshIA, DAG, App Usuario/Admin, Supabase or production Device Owner logic.