# Glosh Remote — Samsung System Bubble Guided Installer

Updated: 2026-08-25 17:34 ART

## Executive status

`REMOTE-SAMSUNG-SYSTEM-UI-GUIDE-12`: **PASS AUTOMATED / PENDING PHYSICAL S22**.

`REMOTE-SAMSUNG-OVERLAY-GUIDE-11`: **FAILED PHYSICAL S22 / SUPERSEDED FOR SETTINGS**.

`REMOTE-INSTALL-CONNECTION-00`: **PASS FINAL DEV / CLOSED**.

Accessibility remains **SUPERSEDED BY PRODUCT DECISION**. PiP remains **FAILED PHYSICAL / SUPERSEDED**. The custom `TYPE_APPLICATION_OVERLAY` line remains **FAILED PHYSICAL** because Samsung Settings suppresses it. The secure broker, local Wireless ADB pairing and Mac relay connection remain unchanged.

## Current route

The new candidate uses an Android **system-managed notification Bubble** instead of PiP or an application overlay. The expanded Bubble hosts a normal Glosh Activity, so Android/SystemUI owns the floating surface while Samsung Settings stays foreground.

Implemented behavior:

1. Samsung-only visual guide remains seven explicit user-confirmed steps.
2. No Accessibility service, PiP or `SYSTEM_ALERT_WINDOW` is required.
3. Glosh creates a conversation notification + long-lived shortcut + `Notification.BubbleMetadata`.
4. When the user opens Samsung Settings, Glosh asks SystemUI to auto-expand the Bubble.
5. Expanded Bubble contains Glosh's own white/lime guide UI with real Back/Next confirmation buttons.
6. Bubble positioning is owned by SystemUI; Glosh performs no overlay coordinates or Settings gestures.
7. At the pairing-code step, the Bubble contains a six-digit numeric input and Connect button while the Samsung code can remain visible behind it.
8. The existing pairing service already accepts a pending six-digit code before mDNS finishes discovering the pairing endpoint and continues automatically once the endpoint is ready.
9. Persistent notification remains the fallback if Samsung does not expose/keep the Bubble as expected.
10. The proven pairing/ADB/relay/crypto path was not changed.

Bubble behavior over the exact Samsung Settings pages is **not yet physical PASS**. That is the next customer-like S22 gate.

## Current frozen candidate

Implementation branch:

`work/remote-samsung-bubble-guide-12-chatgpt`

Exact candidate HEAD:

`7821b3a2eaf8b2be1ce878ba3abaf17f24640024`

Immutable gate branch:

`gate/remote-samsung-bubble-7821b3a`

GitHub Actions run:

`32895712969`

Automated result:

- product architecture guard: **PASS** — Samsung-only + system Bubble + user-confirmed Settings + no Accessibility/PiP/app-overlay;
- Python protocol/broker/standby: **14/14 PASS**;
- Android JVM unit suite: **115 tests PASS**;
- Android lint: **PASS**;
- Android assemble: **PASS**;
- artifact upload: **PASS**;
- git status evidence: **clean**.

## Frozen APK for physical S22 gate

- delivered filename: `GloshRemote-Samsung-Bubble-S22.apk`;
- HEAD: `7821b3a2eaf8b2be1ce878ba3abaf17f24640024`;
- size: `19,321,056` bytes;
- SHA-256: `6ddf32781c34311c25294aabc1dce6aa92c8ee75a42c27a07d5cf1b947191cbd`;
- workflow run: `32895712969`;
- artifact ID: `9581203489`;
- artifact ZIP digest: `sha256:36cab50723266b374588ebd3a8ea3bf332c4274eb6d9f9b8af149bdffd9b8aab`.

## Next physical gate — S22

Validate the exact APK above, without recompiling:

1. notifications allowed;
2. Samsung bubble permission/configuration can be enabled for Glosh Remote;
3. tapping/opening Settings produces a visible Glosh Bubble while Settings is foreground;
4. Bubble can expand and show Glosh custom content;
5. Back/Next/confirmation advance the guide without bringing MainActivity to foreground;
6. steps remain synchronized with the persistent notification;
7. Wireless Debugging remains user-controlled and loop-free;
8. mDNS advances the state to code entry;
9. six digits can be entered inside the Bubble while Samsung's code remains visible;
10. pairing → local ADB → secure Mac relay → connected completes;
11. cancel/cleanup leaves no residual access;
12. if Bubble is unavailable or dismissed, persistent notification fallback remains usable.

Do not call the Bubble route physical PASS until this exact S22 gate succeeds.

## Interaction boundaries

The product continues to require:

- automatic Settings clicks: **0**;
- programmatic Settings scrolls: **0**;
- coordinate gestures: **0**;
- OCR / MediaProjection for the six digits: **0**;
- root: **0**;
- public ADB / `adb tcpip 5555`: **0**;
- Accessibility required for base connection: **0**;
- application overlay permission required: **0**.

## Historical physical failures preserved

### PiP

Frozen source `gate/remote-samsung-pip-d2a801a`, HEAD `d2a801a931b713202579fb47b83eaeb02a94c22b`, APK SHA `0b97e3a7b41cd5fe064a0202ee6cc65040d119b76b016ce3489b16fdb3193475`.

PiP appears when leaving Glosh with Home, but not reliably during the Glosh→Settings launch path; Samsung also renders its native actions as media/video controls. Superseded.

### Custom application overlay

Frozen source `gate/remote-samsung-overlay-f4b1bcc`, HEAD `f4b1bccae5c85ff11de282afd69f762f12f84ffd`, APK SHA `e04e5cb8246859c7c664183f176e7c99014879155427c7616959c5a7633ef2ce`.

The card is alive and visible in Recents, but Samsung Settings hides the non-system overlay while foreground. Do not retry this surface for Settings.

## Coordination

- connection base: **PASS FINAL DEV / CLOSED**;
- Accessibility route: **SUPERSEDED BY PRODUCT DECISION**;
- PiP route: **FAILED PHYSICAL / SUPERSEDED**;
- custom application overlay route: **FAILED PHYSICAL / SUPERSEDED FOR SETTINGS**;
- system Bubble route: **PASS AUTOMATED / PENDING PHYSICAL S22**;
- exact Bubble source and APK are frozen above;
- no Remote writer remains active after this handoff;
- no merge, PR, Production, deploy or Supabase mutation performed.
