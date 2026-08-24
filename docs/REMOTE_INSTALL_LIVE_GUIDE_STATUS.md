# Glosh Remote — Live Settings Guide

Updated: 2026-08-24 17:49 ART

## REMOTE-INSTALL-LIVE-GUIDE-03

Status: **FAILED UX PHYSICAL / SUPERSEDED BY V2**.

The no-link broker/relay/ADB connection remains PASS FINAL DEV. The physical failure is isolated to the temporary Settings guidance layer.

Authoritative local implementation chain before V2:
`475bd35b... → 1a537f7a... → 99fcf2bb... → 122c45b9... → 93735b1c...`

Last reported technical gates on that local branch:
- Android 59/59 PASS;
- Python/broker 6/6 PASS;
- lint 0 errors / 8 warnings;
- assembleDebug PASS.

Those technical gates do not override the failed physical UX result.

## REMOTE-INSTALL-LIVE-GUIDE-V2-04

Status: **READ-ONLY AUDIT CONFIRMED / TEXT-ONLY GITHUB HANDOFF READY / IMPLEMENTATION PENDING**.

Codex audited the real Android code at exact local HEAD `93735b1c2a9f493dcd491556b23920c50c3d66f2`, worktree `/private/tmp/glosh-remote-live-guide-03-recovered`, clean, with no code changes.

Confirmed structural findings:
- current scanner relies on `getRootInActiveWindow()` instead of explicit Settings `TYPE_APPLICATION` selection;
- overlay/IME exclusion is not formally guaranteed by window type;
- scanner retains live `AccessibilityNodeInfo`, creating stale-node/action risk;
- no generation/window/fingerprint guard exists;
- stale callbacks can alter rescue/highlight after screen changes;
- scroll currently occurs without `MOSTRARME` and allows up to six attempts;
- Samsung `collapsing_toolbar` caused the previous false-rescue issue; `122c45b9...` fixes only that narrow case;
- human scroll is not distinguished and no 1.4 s cooldown exists.

Frozen V2 direction:
- progressive assistance;
- one trusted Settings `TYPE_APPLICATION` window as scan authority;
- overlays, IME and unrelated apps excluded;
- stable immutable snapshots + anti-stale generation guard;
- no screen movement until user taps `MOSTRARME`;
- max 3 reveal scroll actions;
- human-scroll cancellation + cooldown;
- thin coach bar instead of large draggable card;
- ambiguity = fail closed;
- manual six-box pairing remains guaranteed;
- pairing/CONNECTED transitions fail closed;
- Gate A guide-only → Gate B pairing UX → Gate C full remote session.

### GitHub handoff — authoritative

Branch:
`coordination/remote-install-live-guide-v2`

**Do not use** the old binary ZIP:
`docs/artifacts/remote-install-live-guide-v2/glosh_remote_live_guide_v2_prototype.zip`

That blob failed the integrity gate and is deprecated.

Use these text files instead:
- `docs/REMOTE_INSTALL_LIVE_GUIDE_PRO_AUDIT_04.md`
- `docs/artifacts/remote-install-live-guide-v2/reference/README.md`
- `docs/artifacts/remote-install-live-guide-v2/reference/ANDROID_PORTING_CONTRACT.md`
- `docs/artifacts/remote-install-live-guide-v2/reference/ACCEPTANCE_GATES.md`
- `docs/artifacts/remote-install-live-guide-v2/reference/HANDOFF_MANIFEST.json`
- `docs/artifacts/remote-install-live-guide-v2/reference/fixtures/samsung_s22_es.json`

Reference integrity:
- Android porting contract: `59675fe9ff7186169fe43119c6935a3dfcecf20b1fa6511a4cf38cfe6d732c1f`, 4788 bytes.
- Acceptance gates: `eacd65660a01d48ad951935ffb38c49d25b2df5847dda56cf73e8831d5aea4d8`, 2561 bytes.
- Handoff manifest: `095317612340860640b7a38f32402b0c68e3127bd8e37dc1770a49472646d7e1`, 3309 bytes.
- Samsung fixture: `c0bc2ab22c3dff7028c9b8ab0a59809193bdf5e7db191ce6aa44abb368e38eac`, 1788 bytes.

Immediate next step:
1. Codex fetches only `coordination/remote-install-live-guide-v2` metadata/files without checkout/merge over the Android worktree;
2. verifies the text-file hashes above;
3. implements V2 only under `tools/glosh-remote-spike/**`;
4. runs technical tests/lint/assemble;
5. executes physical Gate A Samsung first and stops at the first reproducible failure;
6. Gate B and Gate C only after Gate A PASS;
7. ChatGPT reviews exact diff/evidence before final closure.

## Coordination

- `REMOTE-INSTALL-CONNECTION-00`: PASS FINAL DEV / CLOSED.
- `REMOTE-INSTALL-LIVE-GUIDE-03`: FAILED UX / SUPERSEDED.
- `REMOTE-INSTALL-LIVE-GUIDE-V2-04`: text-only GitHub handoff ready; implementation is active route.
- `REMOTE-INSTALL-MAC-OPERATOR-04`: preserved; waits for V2 guide stability.
- `REMOTE-INSTALL-PRECHECK-05`, `REMOTE-INSTALL-PIPELINE-06`, `REMOTE-INSTALL-DEVICE-OWNER-COMMIT-07`: preserved; integration waits for V2 gate.
- `REMOTE-ADAPTIVE-INSTALL-PILOT-01`: waiting for V2 Samsung gate.
- Do not touch Chrome, GloshIA, DAG, App Usuario/Admin, Supabase or production Device Owner logic.
