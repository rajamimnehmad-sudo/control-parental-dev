# Glosh Remote — Samsung PiP Guided Installer

Updated: 2026-08-25 15:23 ART

## Executive status

`REMOTE-SAMSUNG-PIP-GUIDE-10`: **PASS AUTOMATED / PENDING S22 PHYSICAL RETEST**.

`REMOTE-INSTALL-CONNECTION-00`: **PASS FINAL DEV / CLOSED**.

The previous Accessibility-based Guided Assistant line, including `690dac8… / 624f8127…`, is **SUPERSEDED BY PRODUCT DECISION**. The secure broker, local Wireless ADB pairing and Mac relay connection remain unchanged.

## Product route now

Glosh Remote is Samsung-only for this phase and no longer requires Accessibility for the connection workflow.

Customer flow:

1. Open Glosh Remote and tap `COMENZAR`.
2. Follow a seven-step Samsung visual guide.
3. When Glosh opens real Samsung Settings, Glosh remains available as a native Picture-in-Picture instructor when the device/user PiP policy permits it.
4. The PiP exposes native `Atrás` and step-specific `Ya está / Siguiente` actions.
5. A persistent synchronized notification is the fallback/control surface if PiP is unavailable or disabled.
6. Build Number has an animated tap effect progressing to ×7.
7. Safe Settings intents are used only as navigation accelerators; the customer performs every Android-protected tap.
8. When Wireless Debugging pairing exposes the local mDNS endpoint, Glosh advances automatically to the code stage.
9. The customer keeps Android's six-digit code visible and enters it through the Glosh notification `RemoteInput` without returning to the app. The in-app six-digit field remains as fallback.
10. Pairing, local ADB and secure Mac relay then continue automatically.

No Accessibility service is declared in the current manifest. The dormant Accessibility service/debug/status entry points were removed from this installer route.

## Interaction boundaries

Current route intentionally has:

- automatic Settings clicks: **0**;
- programmatic Settings scrolls: **0**;
- coordinate gestures: **0**;
- OCR / MediaProjection for the six digits: **0**;
- root: **0**;
- public ADB / `adb tcpip 5555`: **0**.

If the device is not Samsung, this phase fails safely rather than guessing another OEM's Settings structure.

## Improvements included in this cycle

Beyond the requested Samsung walkthrough, this candidate includes:

- native PiP instead of a custom Accessibility overlay;
- redundant native PiP entry paths: Android 12+ auto-enter plus explicit `onUserLeaveHint()` fallback;
- notification fallback if PiP is unavailable;
- a lightweight native/vector-style animated coach instead of GIF assets, avoiding decode/memory/quality dependency;
- durable current-step persistence so returning from Settings resumes the same instruction;
- PiP and notification `Atrás`/`Siguiente` actions;
- broker preparation started while the customer follows the developer steps so later connection work overlaps the manual setup;
- mDNS-driven automatic transition to code entry;
- six-digit notification reply and in-app fallback;
- pending-code handling if the customer replies just before the pairing endpoint is fully discovered.

## Frozen source

Implementation branch:

`work/remote-samsung-pip-guide-10-chatgpt`

Exact final HEAD:

`d2a801a931b713202579fb47b83eaeb02a94c22b`

Immutable gate branch:

`gate/remote-samsung-pip-d2a801a`

Any product-code SHA other than `d2a801a…` is a different physical candidate.

## Automated gate — PASS

GitHub Actions run:

`32882544807`

Final result:

- product architecture guard: **PASS** — Samsung-only + PiP + user-confirmed Settings + no Accessibility;
- Python protocol/broker/standby: **14/14 PASS**;
- Android JVM unit suite: **PASS**, including the Samsung seven-step contract;
- Android lint: **PASS**;
- Android assemble: **PASS**;
- artifact upload: **PASS**.

The report's dirty `git status` is only the workflow's `chmod +x` on `verify_guided_assistant.sh`; it is not source drift and does not change the APK.

## Frozen APK — only authorized Samsung/S22 candidate

- delivered filename: `GloshRemote-Samsung-PiP-S22.apk`;
- source artifact: `GloshRemote-Samsung-PiP-DEV.apk`;
- HEAD: `d2a801a931b713202579fb47b83eaeb02a94c22b`;
- size: `19,287,362` bytes;
- SHA-256: `0b97e3a7b41cd5fe064a0202ee6cc65040d119b76b016ce3489b16fdb3193475`;
- workflow run: `32882544807`;
- artifact ID: `9576402195`;
- artifact ZIP digest: `sha256:41abffcd05fa38aa5c0cbe5e31edfac3d64d058381ba75a44f45f796492fbdd3`.

Do not rebuild or substitute this APK before the S22 physical retest.

## Next physical gate — S22 customer-like

Validate the exact frozen APK above:

- no Accessibility or restricted-settings prompt appears in the main route;
- `COMENZAR` launches the Samsung guide cleanly;
- all seven visual steps are understandable;
- Build Number clearly shows the ×7 tap effect;
- PiP appears over real Samsung Settings when PiP is permitted;
- PiP `Atrás` and `Ya está / Siguiente` actions work;
- notification progress stays synchronized and remains useful if PiP is disabled;
- safe Settings destinations behave acceptably on One UI 8 / Android 16;
- Wireless Debugging can be activated manually without loops;
- pairing mDNS transitions automatically to step 7;
- six digits can be submitted from the notification while the Android code remains visible;
- in-app code entry works as fallback;
- pairing → local ADB → secure Mac relay completes;
- cancel/finish leaves no residual temporary connection;
- non-Samsung fails safely.

Physical PASS is not claimed until this run is completed on the S22.

## Coordination

- connection base: PASS FINAL DEV / CLOSED;
- Accessibility route `690dac8… / 624f8127…`: SUPERSEDED BY PRODUCT DECISION;
- Samsung/PiP route `d2a801a… / 0b97e3a7…`: PASS AUTOMATED / FROZEN;
- S22 physical retest: next;
- no active Remote writer after this cycle;
- no merge, PR, Production, deploy or Supabase mutation performed.
