# Glosh Remote — Samsung PiP Guided Installer

Updated: 2026-08-25 15:44 ART

## Executive status

`REMOTE-SAMSUNG-PIP-GUIDE-10`: **FAILED PHYSICAL S22 — PiP ENTRY + NATIVE-CONTROLS UX / DIAGNOSIS ACTIVE**.

`REMOTE-INSTALL-CONNECTION-00`: **PASS FINAL DEV / CLOSED**.

The previous Accessibility-based Guided Assistant line, including `690dac8… / 624f8127…`, remains **SUPERSEDED BY PRODUCT DECISION**. The secure broker, local Wireless ADB pairing and Mac relay connection remain unchanged.

## Latest physical evidence

The exact frozen Samsung/PiP candidate `d2a801a… / 0b97e3a7…` was installed on the S22.

Physical findings:

1. PiP does **not** appear when Glosh itself launches Samsung Settings from the guide.
2. PiP **does** appear when the customer leaves Glosh with Home. Therefore Samsung PiP capability and the per-app permission are functional on this S22; the failure is isolated to the Glosh→Settings transition/entry path rather than generic PiP support.
3. Tapping the PiP exposes the native actions, but Samsung renders them as old-style media controls. Only back/forward-like icons are visibly useful; this is not acceptable as the intended polished `Atrás / Ya está` UX.
4. The current PiP is visually oversized for the desired coach role. Android/Samsung owns the final PiP sizing; Glosh can influence aspect ratio/content but not freely size it like a normal custom floating window.
5. Screenshots are blocked by the current DEV build because `MainActivity` sets `FLAG_SECURE`. This is unrelated to PiP capability and should be reconsidered for diagnostic builds.

This means PiP remains technically viable as a lightweight visual instructor, but native PiP actions should not be relied on as the primary polished control surface. Product code is not being changed yet.

## Product route under diagnosis

Glosh Remote remains Samsung-only for this phase and does not require Accessibility for the connection workflow.

Intended customer flow remains:

1. Open Glosh Remote and tap `COMENZAR`.
2. Follow the Samsung visual guide.
3. Use a floating visual instructor while real Samsung Settings remains interactive.
4. Keep navigation user-controlled; no automatic Settings clicks, scrolls or coordinates.
5. When Wireless Debugging pairing exposes the local mDNS endpoint, Glosh advances automatically to the code stage.
6. Keep Android's six-digit code visible and enter it through Glosh without requiring Accessibility/OCR.
7. Pairing, local ADB and secure Mac relay continue automatically.

No Accessibility service is declared in the current manifest.

## Interaction boundaries

Current route intentionally has:

- automatic Settings clicks: **0**;
- programmatic Settings scrolls: **0**;
- coordinate gestures: **0**;
- OCR / MediaProjection for the six digits: **0**;
- root: **0**;
- public ADB / `adb tcpip 5555`: **0**.

## Frozen source under diagnosis

Implementation branch:

`work/remote-samsung-pip-guide-10-chatgpt`

Exact candidate HEAD:

`d2a801a931b713202579fb47b83eaeb02a94c22b`

Immutable gate branch:

`gate/remote-samsung-pip-d2a801a`

## Automated gate — PASS, physical UX gate — FAIL

GitHub Actions run:

`32882544807`

Automated result:

- product architecture guard: **PASS**;
- Python protocol/broker/standby: **14/14 PASS**;
- Android JVM unit suite: **PASS**;
- Android lint: **PASS**;
- Android assemble: **PASS**;
- artifact upload: **PASS**.

Physical S22 result:

- PiP capability via Home: **PASS**;
- automatic PiP when Glosh opens Settings: **FAIL**;
- native PiP control UX: **FAIL PRODUCT UX**;
- PiP coach sizing: **needs reduction/rework**.

## Frozen APK used for the failure

- delivered filename: `GloshRemote-Samsung-PiP-S22.apk`;
- HEAD: `d2a801a931b713202579fb47b83eaeb02a94c22b`;
- size: `19,287,362` bytes;
- SHA-256: `0b97e3a7b41cd5fe064a0202ee6cc65040d119b76b016ce3489b16fdb3193475`;
- workflow run: `32882544807`;
- artifact ID: `9576402195`.

Do not call this candidate physical PASS or reuse it as a final installer without resolving the floating-guide transition and control UX.

## Candidate UX directions before code changes

Two viable directions are now explicit:

### A. PiP as visual-only coach

- keep PiP only for the animation/instruction;
- remove reliance on Samsung's media-style PiP actions;
- use the persistent notification for textual `Atrás`, `Siguiente/Ya está` and six-digit `RemoteInput`;
- fix the Glosh→Settings PiP entry timing/transition;
- reduce visual density/aspect ratio as far as Samsung allows.

Advantages: no extra special overlay permission. Limitation: controls remain outside the PiP.

### B. Custom application overlay coach

- request Android/Samsung `Mostrar sobre otras apps` / `SYSTEM_ALERT_WINDOW` once;
- render a small fully custom Glosh floating card over Settings with arbitrary size, typography, animation and real `Atrás / Ya está` buttons;
- keep notification as fallback because Android may suppress overlays on selected sensitive system surfaces;
- no Accessibility required.

Advantages: professional fully controllable visual UX. Cost: one extra special-access permission and OEM-sensitive overlay behavior.

No choice has been implemented yet.

## Coordination

- connection base: PASS FINAL DEV / CLOSED;
- Accessibility route: SUPERSEDED BY PRODUCT DECISION;
- Samsung/PiP candidate `d2a801a… / 0b97e3a7…`: FAILED PHYSICAL S22 for Settings-entry and native-control UX;
- PiP itself is confirmed functional via Home on S22;
- diagnosis active, no product-code edit yet;
- no merge, PR, Production, deploy or Supabase mutation performed.
