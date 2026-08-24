# Glosh Remote — Live Settings Guide

Updated: 2026-08-24 18:33 ART

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

### Physical route approved

Use the Samsung A23 as the instrumented lab device for the heavy technical gate. USB/ADB is allowed there only for installation, logs, repeated navigation and evidence. This does not change the no-PC product architecture.

A23 technical route:
- Samsung SM-A235M / Android 14 / API 34;
- implement V2 and run all technical gates;
- Gate A guide-only first;
- if Gate A passes, continue Gate B pairing UX and Gate C full remote session on A23 where useful;
- stop at the first reproducible failure and preserve evidence.

S22 product-experience route:
- after the A23 technical gates are green, generate the exact V2 APK and send it to the S22 without using USB for the user-experience trial;
- user performs the flow as a real remote customer and reports the visual/interaction experience;
- S22 is the final real-world Samsung UX confirmation, not the primary instrumentation device.

Immediate next step:
1. Codex verifies the text-only GitHub handoff;
2. implements V2 only under `tools/glosh-remote-spike/**`;
3. runs Android tests + Python/broker + lint + assemble;
4. uses A23 as the instrumented device for Gate A, then B/C only after prior gates pass;
5. produces exact APK path/size/SHA and evidence;
6. sends final candidate to S22 for cable-free remote UX verification;
7. ChatGPT reviews exact diff/evidence before final closure.

## Coordination

- `REMOTE-INSTALL-CONNECTION-00`: PASS FINAL DEV / CLOSED.
- `REMOTE-INSTALL-LIVE-GUIDE-03`: FAILED UX / SUPERSEDED.
- `REMOTE-INSTALL-LIVE-GUIDE-V2-04`: text-only GitHub handoff ready; A23 technical implementation/gate is the active route.
- `REMOTE-INSTALL-MAC-OPERATOR-04`: preserved; waits for V2 guide stability.
- `REMOTE-INSTALL-PRECHECK-05`, `REMOTE-INSTALL-PIPELINE-06`, `REMOTE-INSTALL-DEVICE-OWNER-COMMIT-07`: preserved; integration waits for V2 gate.
- `REMOTE-ADAPTIVE-INSTALL-PILOT-01`: waiting for V2 A23 technical gate + S22 UX confirmation.
- Do not touch Chrome, GloshIA, DAG, App Usuario/Admin, Supabase or production Device Owner logic.
