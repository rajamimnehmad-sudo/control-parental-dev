# Glosh Remote — Samsung PiP Guided Installer

Updated: 2026-08-25 15:44 ART

## Executive status

`REMOTE-SAMSUNG-PIP-GUIDE-10`: **FAILED PHYSICAL S22 — PiP DID NOT APPEAR / DIAGNOSIS ACTIVE**.

`REMOTE-INSTALL-CONNECTION-00`: **PASS FINAL DEV / CLOSED**.

The previous Accessibility-based Guided Assistant line, including `690dac8… / 624f8127…`, remains **SUPERSEDED BY PRODUCT DECISION**. The secure broker, local Wireless ADB pairing and Mac relay connection remain unchanged.

## Latest physical evidence

The exact frozen Samsung/PiP candidate `d2a801a… / 0b97e3a7…` was installed on the S22 and the native Picture-in-Picture instructor did **not** appear when the guide opened Samsung Settings.

This invalidates physical PASS for the current PiP entry implementation. No conclusion is yet made about whether the cause is per-app/user PiP permission, Samsung policy, or the Glosh transition logic. Product code is not being changed until the failure is isolated with a short physical diagnostic.

## Product route under diagnosis

Glosh Remote remains Samsung-only for this phase and does not require Accessibility for the connection workflow.

Intended customer flow:

1. Open Glosh Remote and tap `COMENZAR`.
2. Follow a seven-step Samsung visual guide.
3. When Glosh opens real Samsung Settings, Glosh should remain available as a native Picture-in-Picture instructor when device/user PiP policy permits it.
4. The PiP should expose native `Atrás` and step-specific `Ya está / Siguiente` actions.
5. A persistent synchronized notification remains the fallback/control surface.
6. Build Number has an animated tap effect progressing to ×7.
7. Safe Settings intents are used only as navigation accelerators; the customer performs every Android-protected tap.
8. When Wireless Debugging pairing exposes the local mDNS endpoint, Glosh advances automatically to the code stage.
9. The customer keeps Android's six-digit code visible and enters it through the Glosh notification `RemoteInput` without returning to the app. The in-app six-digit field remains as fallback.
10. Pairing, local ADB and secure Mac relay then continue automatically.

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

## Automated gate — PASS, physical PiP gate — FAIL

GitHub Actions run:

`32882544807`

Automated result:

- product architecture guard: **PASS**;
- Python protocol/broker/standby: **14/14 PASS**;
- Android JVM unit suite: **PASS**;
- Android lint: **PASS**;
- Android assemble: **PASS**;
- artifact upload: **PASS**.

Physical result on S22:

- PiP instructor when entering Settings: **FAIL — did not appear**.

## Frozen APK used for the failure

- delivered filename: `GloshRemote-Samsung-PiP-S22.apk`;
- HEAD: `d2a801a931b713202579fb47b83eaeb02a94c22b`;
- size: `19,287,362` bytes;
- SHA-256: `0b97e3a7b41cd5fe064a0202ee6cc65040d119b76b016ce3489b16fdb3193475`;
- workflow run: `32882544807`;
- artifact ID: `9576402195`.

Do not call this candidate physical PASS or reuse it as a final installer without resolving the PiP entry failure.

## Immediate diagnostic gate

Before changing code, isolate:

1. Whether Glosh Remote is allowed to use Picture-in-Picture in Samsung special app access.
2. Whether manually leaving Glosh with Home/swipe causes Glosh to enter PiP.
3. Whether the failure occurs specifically only when Glosh launches Samsung Settings.
4. Whether the synchronized notification remains present when Settings opens.

If manual Home enters PiP but launching Settings does not, the likely defect is Glosh's PiP transition/entry timing rather than Samsung PiP capability.

## Coordination

- connection base: PASS FINAL DEV / CLOSED;
- Accessibility route: SUPERSEDED BY PRODUCT DECISION;
- Samsung/PiP candidate `d2a801a… / 0b97e3a7…`: FAILED PHYSICAL S22 at PiP entry;
- diagnosis active, no product-code edit yet;
- no merge, PR, Production, deploy or Supabase mutation performed.
