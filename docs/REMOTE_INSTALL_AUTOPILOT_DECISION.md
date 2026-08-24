# Glosh Remote — Autopilot First decision

Updated: 2026-08-24 20:40 ART

## Decision

Live Guide V2 changes route from **guide-first** to **autopilot-first with guided fallback**.

The implementation strategy is **Samsung-first with a universal core**:
- build the generic state engine, safety core, snapshot/matcher, click executor and expected-transition logic once;
- implement only the Samsung OEM adapter/recipe in the current cycle;
- do not spend current effort on Motorola/Xiaomi/Pixel variants;
- do not hardcode A23/S22-specific behavior that would prevent later OEM adapters.

Goal: on Samsung, Glosh Remote should take the shortest safe path from the phone's CURRENT state to the Android Wireless Debugging pairing-code screen. It must not replay unnecessary setup steps if Developer Options, Wireless Debugging, the pairing screen, or an authenticated ADB session are already available.

## Product distribution constraint

Glosh Remote is planned as an out-of-Play installer/support component. Google Play publication-policy compliance is therefore **not a design goal for this component**. Do not add complexity solely to satisfy Play Store policy.

Android/Samsung platform security boundaries still apply and remain authoritative: user-protected credential prompts, permissions the app cannot self-grant, Settings behavior, Device Owner preconditions, and any OS-enforced restrictions must be handled rather than bypassed.

## Shortest-path state resolver

Before navigating, resolve the current state and choose the nearest valid target.

Priority order:
1. Existing authenticated Glosh Remote/ADB session for the current support session -> skip pairing/navigation.
2. Pairing-code dialog already open -> capture/use guarded pairing flow or manual six-box fallback.
3. Wireless Debugging screen available/enabled -> go directly to `Vincular dispositivo con código de vinculación`.
4. Developer Options available but Wireless Debugging off -> open Developer Options, enable/open Wireless Debugging, then pairing.
5. Developer Options unavailable -> execute Samsung activation recipe (`Acerca del teléfono` -> `Información de software` -> `Número de compilación` x7), handling protected credential prompt manually when Android requires it, then jump to Developer Options / Wireless Debugging.
6. Unknown/ambiguous state -> stop Autopilot and use thin guided/manual fallback.

Never perform a known earlier step merely because it exists in the recipe if a later state has already been proven.

## Why

The previous guide-first route over-invests in visual coaching, scroll UX and human navigation even though the same Accessibility tree can activate exact clickable Settings nodes. The goal is not to automate visual gestures; it is a deterministic state machine that performs one verified action at a time and checks the resulting state.

## Safety core retained

Autopilot does NOT remove the V2 safety work. Before every click it still requires:
- exactly one trusted Settings TYPE_APPLICATION window;
- immutable stable snapshot;
- generation/window/fingerprint anti-stale token;
- Samsung recipe + exact alias/context;
- single HIGH-confidence candidate with margin;
- fresh node reacquisition immediately before ACTION_CLICK;
- expected-screen transition after every click;
- fail-closed on ambiguity or unexpected UI.

No coordinate gestures as normal path.

## Samsung current-cycle routes

### Route A — already paired/authenticated
Skip all Settings navigation and continue to remote precheck.

### Route B — pairing dialog already visible
Use the pairing code through the guarded existing path when confidence is HIGH; otherwise show six-box manual entry.

### Route C — Wireless Debugging already enabled
Open Wireless Debugging if needed -> `Vincular dispositivo con código de vinculación` -> pairing dialog.

### Route D — Developer Options ON, Wireless Debugging OFF
Open Developer Options directly -> enable/open Wireless Debugging -> pairing dialog.

### Route E — Developer Options OFF
Open Settings / About phone -> `Información de software` -> `Número de compilación` seven times -> if Android asks for PIN/password/pattern, stop automation and let the user complete the protected prompt -> verify Developer Options became available -> jump directly to Developer Options -> Wireless Debugging -> pairing dialog.

### Route F — unexpected Samsung UI
Stop auto-clicking. Thin fallback guide/manual instructions only. Never guess.

## Pairing target

Minimum current milestone: reliably reach the six-digit Wireless Debugging pairing-code dialog on Samsung using the shortest state-dependent route.

Preferred enhancement if safe: detect exactly one contextual six-digit code and pass it to the existing single guarded pairing submit path. Manual six-box entry remains guaranteed fallback.

## Fallback

If any target is not HIGH confidence, screen transition is unexpected, credential prompt appears, node cannot be re-acquired, or the Samsung state is unsupported:
- stop auto-clicking immediately;
- fall back to the thin Live Guide/manual instruction;
- never guess or continue blindly.

The guide remains useful but is no longer the primary path.

## Scope impact

De-prioritize current-cycle work on:
- Motorola/Xiaomi/Pixel adapters;
- broad OEM coverage;
- large guided navigation flows;
- autonomous guide scrolling;
- extensive `MOSTRARME` mechanics;
- human-scroll heuristics as primary behavior;
- Google Play policy-specific UX/architecture.

Prioritize:
- universal state resolver/core interfaces;
- Samsung adapter only;
- shortest-path detection;
- safe click engine;
- expected-state transitions;
- A23 technical validation;
- S22 no-cable UX validation afterward.

## Future universality

The architecture should allow later adapters such as `SamsungAdapter`, `AospMotorolaAdapter`, `XiaomiAdapter`, etc., but only Samsung is implemented/validated now. Adding another OEM should primarily add recipes/aliases/state probes, not rewrite the core engine.

## Gate strategy

A23 USB lab gate:
- exercise Routes C/D/E and any reachable shortcut states;
- zero wrong clicks;
- prove that already-enabled Developer Options skips build-number work;
- prove that already-enabled Wireless Debugging skips Developer Options work;
- credential/ambiguous state always stops safely;
- reach pairing-code screen reliably;
- no crash/ANR.

S22 real-user gate:
- no USB;
- same APK;
- user starts Glosh Remote in whatever state the S22 currently has;
- Autopilot resolves current state rather than assuming a clean phone;
- confirm it reaches the six-digit code with minimal/no manual navigation.

One wrong automatic click is a FAIL.

## Coordination

This decision supersedes guide-first as the active UX route for `REMOTE-INSTALL-LIVE-GUIDE-V2-04` and narrows the current implementation scope to **Samsung-first / universal-core / shortest-path**. The structural V2 audit remains authoritative because its window/snapshot/anti-stale guarantees are prerequisites for safe automatic clicks.
