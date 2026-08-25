# Glosh Remote — Samsung Guided Installer

Updated: 2026-08-25 16:33 ART

## Executive status

`REMOTE-SAMSUNG-OVERLAY-GUIDE-11`: **FAILED PHYSICAL S22 — SETTINGS SUPPRESSES NON-SYSTEM OVERLAY**.

`REMOTE-INSTALL-CONNECTION-00`: **PASS FINAL DEV / CLOSED**.

The previous Accessibility-based Guided Assistant line remains **SUPERSEDED BY PRODUCT DECISION**. The PiP line also remains **FAILED PHYSICAL / SUPERSEDED**. The secure broker, local Wireless ADB pairing and Mac relay connection remain unchanged.

## Latest physical evidence

The exact frozen custom-overlay candidate `f4b1bcc… / e04e5cb8…` was installed on the S22.

Physical findings:

1. The Glosh `TYPE_APPLICATION_OVERLAY` card is alive and visible over Samsung Recents, proving `SYSTEM_ALERT_WINDOW` permission and the overlay implementation itself work.
2. The same card is not visible while Samsung Settings is the foreground window.
3. This matches Android's security model: apps can mark sensitive windows to hide non-system application overlays (`Window.setHideOverlayWindows(true)` / `HIDE_OVERLAY_WINDOWS`). A normal app cannot override another app's decision to hide `TYPE_APPLICATION_OVERLAY` windows.
4. Therefore a custom application overlay cannot be the mandatory instructor surface for Samsung Settings on this S22.
5. The DEV screenshot path works and supplied the decisive evidence.

## Route decision

Do **not** spend another cycle trying to force `TYPE_APPLICATION_OVERLAY` above Samsung Settings. Treat that as an Android/OEM security boundary, not a positioning bug.

The next candidate must use a **system-owned surface** if we want guidance while Settings is foreground:

- preferred experiment: Android/Samsung notification bubble with a compact Glosh activity; closest UX to a floating window and does not require Accessibility;
- guaranteed fallback: persistent notification with explicit step text/actions and six-digit `RemoteInput`;
- keep the normal in-app guide when Glosh itself is foreground.

Bubble behavior over the exact Samsung Settings pages is **not yet physical PASS** and must be tested before becoming the route of record.

## Interaction boundaries

The product continues to require:

- automatic Settings clicks: **0**;
- programmatic Settings scrolls: **0**;
- coordinate gestures: **0**;
- OCR / MediaProjection for the six digits: **0**;
- root: **0**;
- public ADB / `adb tcpip 5555`: **0**;
- Accessibility required for base connection: **0**.

## Failed custom-overlay candidate

Implementation branch:

`work/remote-samsung-overlay-guide-11-chatgpt`

Exact candidate HEAD:

`f4b1bccae5c85ff11de282afd69f762f12f84ffd`

Immutable gate branch:

`gate/remote-samsung-overlay-f4b1bcc`

Automated gate run:

`32888917996`

Automated result:

- product architecture guard: **PASS**;
- Python protocol/broker/standby: **14/14 PASS**;
- Android JVM unit suite: **PASS**;
- Android lint: **PASS**;
- Android assemble: **PASS**;
- artifact upload: **PASS**.

Physical S22 result:

- custom overlay permission: **PASS**;
- custom overlay visible in Recents: **PASS**;
- custom overlay visible over Samsung Settings: **FAIL**;
- route suitability as mandatory Settings coach: **FAIL PRODUCT/PLATFORM**.

## Frozen APK used for the failure

- delivered filename: `GloshRemote-Samsung-Overlay-S22.apk`;
- HEAD: `f4b1bccae5c85ff11de282afd69f762f12f84ffd`;
- size: `19,303,762` bytes;
- SHA-256: `e04e5cb8246859c7c664183f176e7c99014879155427c7616959c5a7633ef2ce`;
- workflow run: `32888917996`;
- artifact ID: `9578720826`.

Do not call this candidate physical PASS or continue polishing its overlay for Settings.

## Coordination

- connection base: PASS FINAL DEV / CLOSED;
- Accessibility route: SUPERSEDED BY PRODUCT DECISION;
- Samsung/PiP candidate: FAILED PHYSICAL / SUPERSEDED;
- Samsung Custom Overlay candidate `f4b1bcc… / e04e5cb8…`: FAILED PHYSICAL because Settings suppresses it;
- next route: evaluate system-owned bubble with persistent notification fallback;
- no product-code edit for the bubble has been made in this cycle;
- no merge, PR, Production, deploy or Supabase mutation performed.
