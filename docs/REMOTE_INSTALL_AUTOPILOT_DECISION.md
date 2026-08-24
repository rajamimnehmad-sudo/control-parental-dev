# Glosh Remote — Autopilot First decision

Updated: 2026-08-24

## Decision

Live Guide V2 changes route from **guide-first** to **autopilot-first with guided fallback**.

Goal: on known OEMs, Glosh Remote should open Settings and perform only high-confidence Accessibility `ACTION_CLICK` steps automatically until the Android Wireless Debugging pairing-code screen is reached. The user intervenes only for credentials/secure confirmations or when confidence is insufficient.

## Why

The previous guide-first route over-invests in visual coaching, scroll UX and human navigation even though the same Accessibility tree can safely activate clickable Settings nodes. Android exposes accessibility click actions on clickable nodes; this makes an automated known-route pilot technically viable.

## Safety core retained

Autopilot does NOT remove the V2 safety work. Before every click it still requires:
- exactly one trusted Settings TYPE_APPLICATION window;
- immutable stable snapshot;
- generation/window/fingerprint anti-stale token;
- OEM recipe + exact alias/context;
- single HIGH-confidence candidate with margin;
- fresh node reacquisition immediately before ACTION_CLICK;
- expected-screen transition after every click;
- fail-closed on ambiguity or unexpected UI.

No coordinate gestures as normal path.

## Samsung pilot route

Preferred Samsung route:
1. Open Settings / About phone.
2. Click `Información de software` automatically.
3. Click `Número de compilación` seven times when Developer Options are not already available.
4. If Android requests device credential or another protected confirmation, STOP automation and ask the user to complete it.
5. Open Developer options.
6. Click `Depuración inalámbrica` / enable it when safely represented as a clickable/toggle node.
7. Click `Vincular dispositivo con código de vinculación` (localized alias as applicable).
8. Reach the six-digit pairing-code dialog.
9. Minimum product goal: stop there and make pairing trivial. Optional enhancement: read exactly one contextual six-digit pairing code and submit it through the existing guarded pairing path.

Fast path: if Wireless Debugging is already available, skip the build-number sequence entirely.

## Fallback

If any target is not HIGH confidence, screen transition is unexpected, credential prompt appears, node cannot be re-acquired, or OEM is unsupported:
- stop auto-clicking immediately;
- fall back to the thin Live Guide/manual instruction;
- never guess or continue blindly.

Thus the guide remains useful but is no longer the primary path.

## Scope impact

De-prioritize perfection work on:
- large guided navigation flows;
- autonomous guide scrolling;
- extensive `MOSTRARME` mechanics;
- human-scroll heuristics as primary behavior.

Retain minimal fallback guide support.

Prioritize:
- safe click engine;
- expected-state transitions;
- Samsung pilot end-to-end to six-digit code;
- A23 technical validation;
- S22 no-cable UX validation afterward.

## Gate strategy

A23 USB lab gate:
- run the Samsung route repeatedly;
- zero wrong clicks;
- credential/ambiguous state always stops safely;
- reach pairing-code screen reliably;
- no crash/ANR.

S22 real-user gate:
- no USB;
- same APK;
- user starts Glosh Remote and watches Autopilot;
- confirm it reaches six-digit code with minimal/no manual navigation.

One wrong automatic click is a FAIL.

## Coordination

This decision supersedes guide-first as the active UX route for `REMOTE-INSTALL-LIVE-GUIDE-V2-04`. The structural V2 audit remains authoritative because its window/snapshot/anti-stale guarantees are prerequisites for safe automatic clicks.
