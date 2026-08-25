# Glosh Remote — Samsung Custom Overlay Guided Installer

Updated: 2026-08-25 16:24 ART

## Executive status

`REMOTE-SAMSUNG-OVERLAY-GUIDE-11`: **PASS AUTOMATED / PENDING S22 PHYSICAL RETEST**.

`REMOTE-SAMSUNG-PIP-GUIDE-10`: **FAILED PHYSICAL S22 / SUPERSEDED**.

`REMOTE-INSTALL-CONNECTION-00`: **PASS FINAL DEV / CLOSED**.

The secure broker, local Wireless ADB pairing and Mac relay connection remain unchanged. Accessibility remains removed from the installer connection flow.

## Physical evidence that superseded PiP

The frozen PiP candidate `d2a801a… / 0b97e3a7…` was tested on the S22 / One UI 8 / Android 16.

Findings:

1. PiP did not appear when Glosh itself launched Samsung Settings.
2. PiP did appear when the customer manually left Glosh with Home, proving PiP capability and permission were functional on the device.
3. Samsung rendered PiP actions as old-style media/video controls. The intended branded `Atrás / Ya está` UX was therefore not achievable through native PiP actions.
4. The PiP coach was visually oversized for the desired instructor role.
5. The previous DEV build blocked screenshots because `FLAG_SECURE` was always active.

PiP is therefore **superseded as the product interaction surface**, not because Samsung lacks PiP, but because the transition and native-control UX do not satisfy the product goal.

## Current product route

The user selected the custom floating-window direction.

Glosh Remote is Samsung-only for this phase and uses an app-owned Android overlay:

- one-time `Mostrar sobre otras apps` / `SYSTEM_ALERT_WINDOW` permission;
- `TYPE_APPLICATION_OVERLAY` custom Glosh card above real Samsung Settings;
- compact white/lime card with title, instruction and native/vector animation;
- real custom textual buttons `Atrás` and step-specific `Ya está` actions;
- draggable by the card header;
- synchronized persistent notification as backup/control surface;
- six-digit pairing code still entered through notification `RemoteInput`, with in-app fallback;
- mDNS/session state continues to advance the guide automatically where the connection stack already has real state;
- no Accessibility, PiP, OCR or MediaProjection.

DEV builds now allow screenshots so physical UX can be reviewed. `FLAG_SECURE` remains intended for non-debug/release builds.

## Samsung seven-step guide

1. `Abrí “Acerca del teléfono”` — Step 1 deliberately starts from Samsung Settings home so the visual guide actually teaches the route.
2. `Abrí “Información de software”`.
3. `Tocá 7 veces “Número de compilación”` — animated tap effect progresses to ×7.
4. `Abrí “Opciones de desarrollador”`.
5. `Activá “Depuración inalámbrica”`.
6. `Tocá “Vincular dispositivo con código”`.
7. `Ingresá los 6 números` — notification RemoteInput + in-app fallback.

Safe Settings intents remain accelerators only. Every Android-protected tap stays under customer control.

## Interaction boundaries

Current route intentionally has:

- automatic Settings clicks: **0**;
- programmatic Settings scrolls: **0**;
- coordinate gestures: **0**;
- Accessibility: **0**;
- PiP dependency: **0**;
- OCR / MediaProjection for the six digits: **0**;
- root: **0**;
- public ADB / `adb tcpip 5555`: **0**.

## Frozen source

Implementation branch:

`work/remote-samsung-overlay-guide-11-chatgpt`

Exact final HEAD:

`f4b1bccae5c85ff11de282afd69f762f12f84ffd`

Immutable physical gate branch:

`gate/remote-samsung-overlay-f4b1bcc`

Any product-code SHA other than `f4b1bcc…` is a different candidate.

## Automated gate — PASS

GitHub Actions run:

`32888917996`

Final result:

- product architecture guard: **PASS** — Samsung-only + custom overlay + user-confirmed Settings + no Accessibility/PiP;
- Python protocol/broker/standby: **14/14 PASS**;
- Android JVM unit tests: **PASS**;
- Android lint: **PASS**;
- Android assemble: **PASS**;
- artifact upload: **PASS**.

The report's dirty status is only the workflow `chmod +x` on the gate script, not product-code drift.

## Frozen APK — current S22 candidate

- delivered filename: `GloshRemote-Samsung-Overlay-S22.apk`;
- source artifact: `GloshRemote-Samsung-Overlay-DEV.apk`;
- HEAD: `f4b1bccae5c85ff11de282afd69f762f12f84ffd`;
- size: `19,303,762` bytes;
- SHA-256: `e04e5cb8246859c7c664183f176e7c99014879155427c7616959c5a7633ef2ce`;
- workflow run: `32888917996`;
- artifact ID: `9578720826`;
- artifact ZIP digest: `sha256:d6a150765445260fe5b59f627613455a7ca5efe89160bbfff2f5f6c82de2c347`.

## Next physical gate — S22

Install the exact APK above and validate customer-like:

- `COMENZAR` leads once to `Mostrar sobre otras apps` if permission is missing;
- returning from that permission screen continues without Accessibility/restricted-settings;
- Step 1 opens Samsung Settings home;
- the custom Glosh card appears immediately over Settings;
- card size is comfortable and does not obstruct the target excessively;
- card can be dragged from its header;
- custom `Atrás` and `Ya lo abrí / Ya está activo / Ya estoy ahí / Ya la activé / Ya veo el código` buttons work;
- screenshots work in this DEV candidate;
- ×7 animation is clear;
- Wireless Debugging/manual pairing has no navigation loop;
- mDNS transitions to code stage;
- six digits submit from notification while Android code stays visible;
- in-app code fallback works;
- pairing → local ADB → secure Mac relay completes;
- cancel/finish removes the temporary overlay/connection cleanly.

Physical PASS is not claimed until this S22 run is completed.

## Coordination

- connection base: PASS FINAL DEV / CLOSED;
- Accessibility route: SUPERSEDED BY PRODUCT DECISION;
- PiP route `d2a801a… / 0b97e3a7…`: FAILED PHYSICAL / SUPERSEDED;
- custom overlay route `f4b1bcc… / e04e5cb8…`: PASS AUTOMATED / FROZEN / PENDING S22 PHYSICAL;
- no active Remote writer after this cycle;
- no merge, PR, Production, deploy or Supabase mutation performed.
