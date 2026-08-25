# Adaptive Autopilot reference validation

Validated by ChatGPT before Android port on 2026-08-24.

## Results

- Python planner/state suite: **38/38 PASS**.
- Kotlin planner: `kotlinc` compile PASS + **40 reference checks PASS**.
- Kotlin pure UI/safety layer: `kotlinc` compile PASS + **18/18 checks PASS**.

## Covered before Codex

- support/ADB shortest-path shortcuts;
- Android <11 fallback;
- Restricted Settings bootstrap;
- Accessibility bootstrap;
- no usable Wi-Fi;
- policy-blocked Wireless Debugging;
- reuse/reconnect of a previous pairing;
- direct Developer Settings fast-path;
- Samsung About phone → Software information → Build number path;
- credential interruption;
- Wireless Debugging OFF/ON;
- network confirmation;
- pairing dialog automatic unique-code vs manual fallback;
- strict Settings `APPLICATION` window selection;
- overlay/IME rejection;
- ambiguous Settings-window rejection;
- immutable snapshot fingerprinting;
- Samsung screen/target classification;
- duplicate target confidence downgrade;
- contextual six-digit code detection;
- stale generation/action-token rejection;
- unknown-screen fail-closed.

## What this does NOT validate

This reference validation does not claim Android physical PASS. It intentionally leaves only platform/environment work for Codex:

- mapping real `AccessibilityWindowInfo`/`AccessibilityNodeInfo` to the immutable reference model;
- fresh live-node reacquisition and real `ACTION_CLICK`;
- exact Samsung A23 tree variants/aliases where the physical UI differs;
- build/lint Android integration;
- physical A23 zero-wrong-click gate;
- identical APK cable-free UX gate on S22.

The existing broker/relay/crypto/no-link connection is a separate frozen PASS base and is not reimplemented here.
