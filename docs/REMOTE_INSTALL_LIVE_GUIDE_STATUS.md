# Glosh Remote — Professional Guided Assistant

Updated: 2026-08-25 13:42 ART

## Executive status

`REMOTE-INSTALL-GUIDED-ASSISTANT-08`: **PASS AUTOMATED / NEW S22 PHYSICAL RETEST CANDIDATE FROZEN**.

`REMOTE-INSTALL-RESTRICTED-RECOVERY-09`: **PASS AUTOMATED / PENDING S22 PHYSICAL RETEST**.

`REMOTE-INSTALL-CONNECTION-00`: **PASS FINAL DEV / CLOSED**.

The secure connection stack remains unchanged. This cycle corrects only the customer guidance around Android restricted settings / Accessibility plus the previously observed One UI 8 UX defects.

## Latest physical evidence

On the S22 Ultra / One UI 8 / Android 16 the proactive App Info instruction was proven invalid: the `⋮` overflow that exposes `Permitir configuración restringida` is **not guaranteed to exist before Android has actually rejected an Accessibility enable attempt**.

Therefore the previous candidate:

- source HEAD `1c25d36de83964bb6284c493e427efa3c5476f28`;
- APK SHA-256 `d63ea7afb5464d96f9364261625591df95eccc80cf5d40dc240bcdafb48c49f2`;

is **SUPERSEDED FOR PHYSICAL UX** and must not be reused.

The older `1313eea… / 14ed9879…` candidate remains superseded as well.

## Correct restricted-settings flow

Glosh no longer sends every Android 13+ sideload user proactively to App Info.

The flow is now reactive:

1. Step 1 opens Glosh Remote Accessibility normally.
2. The customer attempts to enable Glosh Remote.
3. If the Accessibility service becomes enabled, that real Android system state is the only success authority and Glosh continues automatically.
4. Only if the customer returns with Accessibility still disabled does Glosh offer the restricted-settings recovery.
5. Recovery says, conditionally:
   - if Android showed `A la app se le negó el acceso`, open App Info;
   - **if** `⋮` exists, choose `Permitir configuración restringida`;
   - **if it does not exist**, return to Accessibility and attempt the blocked activation again before retrying recovery.
6. No local “confirmed” bit can bypass or substitute the actual Accessibility enabled state.
7. Attempt state is cleared after true Accessibility enable, cancellation, or a new guided run.

This directly matches the physical S22 evidence and no longer promises that Samsung will expose a menu before Android is ready to show it.

## Other S22 UX fixes retained

The same candidate retains the previous physical-evidence fixes:

- One UI 8 / Android 16 semantic recognition for About phone, Software information, Developer options and Wireless debugging;
- Developer Options authoritative detection through `Settings.Global.DEVELOPMENT_SETTINGS_ENABLED`;
- automatic skip of build-number ×7 when Developer mode is already enabled;
- finite Developer→Wireless deep-link policy: no reopen loop, at most one user-driven retry, then stable visual fallback;
- no duplicate build-number copy;
- credential/PIN/IME-safe floating coach at the top of the screen;
- proper `Esperá… / Verificando…` wait states;
- zero automatic Settings clicks;
- zero programmatic Settings scrolls;
- zero coordinate gestures.

## Frozen source

Exact product-code checkpoint:

`690dac8ca7d2a9537316987d46cad728d83454a9`

Immutable physical gate branch:

`gate/remote-guided-restricted-recovery-690dac8`

Implementation branch contains later documentation-only commits; they do not change this APK candidate. Any product-code SHA other than `690dac8…` requires a new automated gate.

## Automated gate

GitHub Actions run:

`32872587243`

The first attempt stopped at one timing-sensitive unchanged Mac heartbeat test. No broker/relay/heartbeat implementation had changed in this batch. The exact same source job was rerun without code changes and completed successfully.

Final rerun result:

- HEAD: `690dac8ca7d2a9537316987d46cad728d83454a9`;
- product architecture guard: **PASS** (`direct-route + observe/explain only`);
- Python protocol/broker/standby: **14/14 PASS**;
- Android JVM unit tests: **PASS**, including restricted-recovery and S22 physical-evidence tests;
- Android lint: **PASS**;
- Android assemble: **PASS**;
- artifact upload: **PASS**.

The first heartbeat failure is classified as timing flakiness, not a product regression, because the exact unchanged source and implementation passed on immediate rerun and the connection stack was untouched.

## Frozen APK — only authorized S22 candidate

- delivered filename: `GloshRemote-Professional-S22-Reactive-Fixed.apk`;
- source artifact: `GloshRemote-Guided-DEV.apk`;
- HEAD: `690dac8ca7d2a9537316987d46cad728d83454a9`;
- size: `19,303,950` bytes;
- SHA-256: `624f8127dfd3152e6883204b4995b0ad73f43daa915992bd98711ca9e06a35a9`;
- artifact ID: `9572893860`;
- artifact ZIP digest: `sha256:f3e5eed68c272f087523e1e13dd6d82f87f477b80fa0115a5c141ca8eacf6214`.

Do not rebuild or substitute this APK before the S22 physical retest.

## Next physical retest

Use the exact candidate above on the S22 as a customer-like run.

Critical Step 1 expectations:

- first attempt goes to Accessibility, **not App Info**;
- if Accessibility enables normally, Glosh immediately continues;
- if Android denies access, returning to Glosh reveals the recovery;
- recovery never claims `⋮` must exist;
- if `⋮` exists, customer can choose `Permitir configuración restringida`;
- if `⋮` does not exist, guidance returns the customer to Accessibility to trigger the Android denial path;
- Glosh advances only after the service is truly enabled.

Then validate the already-retained flow:

- Developer mode already enabled is auto-detected and skipped;
- One UI 8 screens are recognized without stale instructions;
- PIN coach does not cover keyboard;
- waiting states are explicit;
- Developer→Wireless reopen loop = 0;
- pairing code auto-read/fallback works;
- local ADB and secure Mac support work;
- residual overlay = 0 on cancel/finish.

## Coordination

- connection base: PASS FINAL DEV / CLOSED;
- previous S22 candidate `d63ea7af…`: SUPERSEDED by physical evidence;
- reactive restricted-settings candidate `624f8127…`: PASS AUTOMATED / FROZEN;
- S22 physical retest: next;
- no active Remote writer after this cycle;
- no merge, PR, Production, deploy or Supabase mutation performed.
