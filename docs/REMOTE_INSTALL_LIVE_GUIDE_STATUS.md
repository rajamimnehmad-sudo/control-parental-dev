# Glosh Remote — Samsung System Bubble Guided Installer

Updated: 2026-08-25 18:54 ART

## Executive status

`REMOTE-SAMSUNG-SYSTEM-UI-GUIDE-12`: **PASS AUTOMATED / PENDING PHYSICAL S22**.

`REMOTE-SAMSUNG-BUBBLE-STABLE-SIGNING-13`: **PASS AUTOMATED / PACKAGING FIX COMPLETE**.

`REMOTE-SAMSUNG-OVERLAY-GUIDE-11`: **FAILED PHYSICAL S22 / SUPERSEDED FOR SETTINGS**.

`REMOTE-INSTALL-CONNECTION-00`: **PASS FINAL DEV / CLOSED**.

Accessibility remains **SUPERSEDED BY PRODUCT DECISION**. PiP remains **FAILED PHYSICAL / SUPERSEDED**. The custom `TYPE_APPLICATION_OVERLAY` line remains **FAILED PHYSICAL** because Samsung Settings suppresses it. The secure broker, local Wireless ADB pairing and Mac relay connection remain unchanged.

## Current route

The current candidate uses an Android **system-managed notification Bubble** instead of PiP or an application overlay. The expanded Bubble hosts a normal Glosh Activity, so Android/SystemUI owns the floating surface while Samsung Settings stays foreground.

Implemented behavior:

1. Samsung-only visual guide remains seven explicit user-confirmed steps.
2. No Accessibility service, PiP or `SYSTEM_ALERT_WINDOW` is required.
3. Glosh creates a conversation notification + long-lived shortcut + `Notification.BubbleMetadata`.
4. When the user opens Samsung Settings, Glosh asks SystemUI to auto-expand the Bubble.
5. Expanded Bubble contains Glosh's own white/lime guide UI with real Back/Next confirmation buttons.
6. Bubble positioning is owned by SystemUI; Glosh performs no overlay coordinates or Settings gestures.
7. At the pairing-code step, the Bubble contains a six-digit numeric input and Connect button while the Samsung code can remain visible behind it.
8. The existing pairing service accepts a pending six-digit code before mDNS finishes discovering the pairing endpoint and continues automatically once the endpoint is ready.
9. Persistent notification remains the fallback if Samsung does not expose/keep the Bubble as expected.
10. The proven pairing/ADB/relay/crypto path was not changed.

Bubble behavior over the exact Samsung Settings pages is **not yet physical PASS**. That remains the next customer-like S22 gate.

## Packaging defect found and fixed

Physical installation of a later Glosh Remote build failed with Android's `conflicto con un paquete`. The cause was the DEV packaging configuration: GitHub Actions was using runner-local default debug signing, so APKs from different runs could have different signing certificates even though the `applicationId` was identical.

The current Bubble line now has a dedicated **DEV-only stable signing identity** and monotonically higher DEV `versionCode`:

- package: `com.glosh.remote.spike`;
- versionCode: `13`;
- versionName: `0.1.0-dev13`;
- stable DEV signer SHA-256: `8c8c1d52c15b55b2239a8ba06f40de3d866a4b80d8a8700e237993b0495459f1`;
- the gate independently verifies the APK certificate against the configured DEV keystore after assemble;
- this signer is DEV-only and is not configured for Production/release signing.

Because the S22 currently has an older Glosh Remote signed by a transient debug key whose private key is not preserved, the migration to this stable line requires **one uninstall/reinstall only**. After the first stable-signed install, later DEV builds using this same signer plus increasing `versionCode` can update in place.

## Current frozen candidate for physical S22

Implementation branch:

`work/remote-samsung-bubble-stable-signing-13-chatgpt`

Exact candidate HEAD:

`8c9497b772bace102665724d18ce0935c1aa7edc`

Immutable gate branch:

`gate/remote-samsung-bubble-stable-signing-13-8c9497b`

GitHub Actions run:

`32903285095`

Automated result:

- product architecture guard: **PASS** — Samsung-only + system Bubble + no Accessibility/PiP/app-overlay + stable DEV signing;
- Python protocol/broker/standby: **14/14 PASS**;
- Android JVM unit suite: **PASS**;
- Android lint: **PASS**;
- Android assemble: **PASS**;
- stable DEV certificate verification: **PASS**;
- artifact upload: **PASS**.

The Bubble UX/connection base is inherited from the already-reviewed Bubble candidate `7821b3a2eaf8b2be1ce878ba3abaf17f24640024`; this follow-up changes only DEV packaging/signing/versioning and the corresponding gate/CI evidence.

## Frozen APK for physical S22 gate

- delivered filename: `GloshRemote-Samsung-Bubble-Stable-S22.apk`;
- HEAD: `8c9497b772bace102665724d18ce0935c1aa7edc`;
- versionCode: `13`;
- size: `19,321,064` bytes;
- SHA-256: `333a1e126b888aa952910af14b6510dddb7d1c29790030d054cb72bb192639bd`;
- DEV signer SHA-256: `8c8c1d52c15b55b2239a8ba06f40de3d866a4b80d8a8700e237993b0495459f1`;
- workflow run: `32903285095`;
- artifact ID: `9583931770`;
- artifact ZIP digest: `sha256:c83503d8b2805a0cb82746048d32fc454cb51e40f93756c4967e221dba04fef2`.

Do not use the prior transient-signed Bubble APK `6ddf3278…` as the physical gate candidate anymore.

## Next physical gate — S22

Migration pre-step: uninstall the currently installed old-signature `Glosh Remote` once, then install the exact stable-signed APK above.

Validate without recompiling:

1. installation succeeds after the one-time old-signature uninstall;
2. notifications allowed;
3. Samsung bubble permission/configuration can be enabled for Glosh Remote;
4. tapping/opening Settings produces a visible Glosh Bubble while Settings is foreground;
5. Bubble can expand and show Glosh custom content;
6. Back/Next/confirmation advance the guide without bringing MainActivity to foreground;
7. steps remain synchronized with the persistent notification;
8. Wireless Debugging remains user-controlled and loop-free;
9. mDNS advances the state to code entry;
10. six digits can be entered inside the Bubble while Samsung's code remains visible;
11. pairing → local ADB → secure Mac relay → connected completes;
12. cancel/cleanup leaves no residual access;
13. if Bubble is unavailable or dismissed, persistent notification fallback remains usable.

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
- stable DEV signing/versioning fix: **PASS AUTOMATED / COMPLETE**;
- exact physical candidate is the stable-signed APK frozen above;
- no merge, PR, Production, deploy or Supabase mutation performed.
