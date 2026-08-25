# Glosh Remote — Professional Guided Assistant

Updated: 2026-08-25 12:40 ART

## Executive status

`REMOTE-INSTALL-GUIDED-ASSISTANT-08`: **PASS AUTOMATED / NEW S22 PHYSICAL RETEST CANDIDATE FROZEN**.

`REMOTE-INSTALL-CONNECTION-00`: **PASS FINAL DEV / CLOSED**.

The secure connection stack remains unchanged. This cycle closes the customer-guidance defects observed physically on the S22 Ultra / One UI 8 / Android 16.

## Physical evidence that triggered this batch

The previous Guided Assistant APK exposed four concrete UX defects on the S22:

1. Android showed `A la app se le negó el acceso` because sideloaded Accessibility was blocked by restricted settings, with no useful Glosh preflight for an inexperienced customer.
2. Glosh had already opened `Acerca del teléfono`, but the coach still said `Tocá “Acerca del teléfono”` and incorrectly reported that it was not the expected screen.
3. On `Información de software`, the build-number instruction was duplicated in title and body.
4. During device credential/PIN entry, the floating coach occupied the lower screen and could cover the keyboard.

The same physical run also established that Samsung may resolve `android.settings.WIRELESS_DEBUGGING_SETTINGS` back to Developer options, causing the earlier Developer→Wireless reopen loop.

Previous physical candidate is superseded:

- HEAD: `1313eea1324903348c6e375b3ce9327120b31ff9`;
- APK SHA-256: `14ed9879f2b18559fdbef914fa2a89e572bef2b98082c1c10e935d3e0a6ecd10`;
- result: **FAILED PHYSICAL S22 / DO NOT REUSE**.

## Final implementation

Implementation branch:

`work/remote-install-guided-assistant-08-chatgpt`

Exact final HEAD:

`1c25d36de83964bb6284c493e427efa3c5476f28`

Immutable gate branch:

`gate/remote-guided-s22-uxfix-1c25d36`

All product-code changes remain isolated under `tools/glosh-remote-spike/**`; the only repository-level addition is the dedicated artifact workflow `.github/workflows/remote-guided-artifact.yml` used to build and return the exact APK.

No Chrome, GloshIA, DAG, App Usuario/Admin, Supabase, Production, broker protocol, relay crypto or Device Owner behavior was changed.

## Fixes completed

### 1. Restricted-settings preflight

Android 13+ sideloaded builds now present a dedicated first-time explanation before trying Accessibility:

`Primero permití el acceso`

Glosh opens its own App Info screen and tells the customer to use the three-dot menu and choose `Permitir configuración restringida`, then return and continue. Because Android does not expose a normal public API to query that exact user choice, the confirmation is explicitly user-driven and stored locally for this app install.

The customer should no longer discover the restriction only after receiving Android's denial dialog.

### 2. One UI 8 screen recognition

Samsung screen classification now uses both canonical titles and conservative visible semantic markers.

Recognized physical fallbacks include:

- About phone: `Nombre del producto`, `Nombre del modelo`, `Número de serie`;
- Software information: `Versión de One UI`, `Versión de Android`, `Número de compilación`;
- Developer options: `Depuración USB`, `Depuración inalámbrica`, `Permanecer activo`;
- Wireless debugging: pairing-row evidence such as `Vincular dispositivo con código...`.

`GuideTargetLocator` also advances the fallback state when the desired Samsung destination is already open, so Glosh no longer tells the customer to tap the screen they are already viewing.

### 3. Developer Options authoritative detection

New `DeveloperOptionsProbe` reads Android's authoritative:

`Settings.Global.DEVELOPMENT_SETTINGS_ENABLED`

Therefore:

- if Developer options are already enabled, Glosh skips About phone / Software information / build-number ×7 automatically;
- if the master Developer switch is still off, Glosh waits for the customer to enable it;
- after credential confirmation, Glosh rechecks the real system state before advancing.

### 4. Developer→Wireless route is finite

`WirelessDirectRoutePolicy` remains the bounded owner:

- first direct Wireless route attempt once;
- repeated identical Developer-options snapshots never relaunch;
- one genuine user-driven state change may permit one final retry;
- a second return becomes permanent visual fallback for that run;
- no third attempt exists;
- no automatic Settings click, programmatic scroll or coordinate gesture is introduced.

### 5. Copy de-duplication

The coach no longer repeats the same instruction in title and body. Example:

- title: `Tocá 7 veces “Número de compilación”`;
- body: `Android te avisará cuando quede habilitado. Glosh lo verificará automáticamente.`

### 6. Keyboard/PIN-safe coach

When Android shows a credential prompt or the IME is visible:

- coach is forced to the top;
- vertical padding is reduced;
- body collapses to one line;
- it no longer occupies the lower keyboard region.

Glosh never reads the customer's PIN, pattern or password.

### 7. Real waiting state

Transitions now have a proper waiting presentation:

- title: `Esperá…`;
- spinner microanimation;
- current step number preserved;
- copy such as `Verificando el cambio…`, `Abriendo Depuración inalámbrica…`, `Leyendo el código de vinculación…`.

A stale action instruction is no longer left on screen while Glosh is waiting for the next trusted state.

### 8. Single navigation owner

When the Accessibility service is active, MainActivity changes the guide stage and returns; the serialized guide coordinator owns Settings navigation. This avoids duplicate launches from the Activity and service racing each other.

## Regression coverage

New physical-evidence tests include:

- `SamsungOneUi8RecognitionTest` for About phone, Software information, Developer options and Wireless debugging markers;
- `GuidePresentationPhysicalUxTest` for non-duplicated build-number copy, waiting state and restricted-settings copy;
- `WirelessDirectRoutePolicyTest` for the bounded Samsung direct-route behavior;
- previous Guided Assistant architecture guard remains: no click/scroll executor ownership.

## Automated gate — PASS

Dedicated GitHub Actions run:

- workflow: `Remote Guided Assistant APK`;
- run ID: `32866694674`;
- built HEAD: `1c25d36de83964bb6284c493e427efa3c5476f28`;
- architecture guard: **PASS**;
- Python protocol/broker/standby: **14/14 PASS**;
- Android JVM unit suite: **PASS**, including new physical-evidence tests;
- Android lint: **PASS**;
- Android assemble: **PASS**;
- artifact upload: **PASS**.

The gate's local `GIT_STATUS` notes only the workflow's `chmod +x` on `verify_guided_assistant.sh`; it is not source drift and does not alter the APK contents.

## Frozen APK for S22 retest

Only authorized new physical candidate:

- filename delivered to user: `GloshRemote-Professional-S22-Fixed.apk`;
- source artifact filename: `GloshRemote-Guided-DEV.apk`;
- exact HEAD: `1c25d36de83964bb6284c493e427efa3c5476f28`;
- size: `19,303,950` bytes;
- SHA-256: `d63ea7afb5464d96f9364261625591df95eccc80cf5d40dc240bcdafb48c49f2`;
- workflow artifact ID: `9570450926`;
- artifact ZIP digest: `sha256:d5f1e0d67f1e0e0ae7381ccd22edc1579da07f66a73d9a9a83acf80dd45e769d`.

Do not substitute or rebuild before the next physical run. Any different APK SHA is a different candidate.

## Next physical gate

The next action is a clean customer-like S22 retest using the exact APK above.

Critical expectations:

- restricted-settings step is understandable without prior Android knowledge;
- About phone / Software information states are recognized correctly on One UI 8;
- build-number copy appears once;
- credential/PIN coach stays clear of the keyboard;
- Developer mode already enabled is detected and the build-number route is skipped;
- `Esperá…` is shown while state changes are being verified;
- Developer→Wireless reopen loop = 0;
- if Samsung cannot deep-link Wireless debugging, a stable visual instruction remains and customer taps the row manually;
- automatic Settings clicks = 0;
- programmatic Settings scrolls = 0;
- pairing code auto-read/fallback remains functional;
- local ADB and secure Mac relay remain functional;
- residual overlay = 0 on cancel/finish.

A23 remains available only as controlled regression hardware if a new S22 defect needs instrumentation.

## Coordination

- connection base: PASS FINAL DEV / CLOSED;
- old physical APK `14ed9879…ecd10`: FAILED/SUPERSEDED;
- full S22 UX fix batch: complete at `1c25d36…`;
- automated gate: PASS;
- new APK `d63ea7af…c49f2`: frozen for S22 retest;
- no active Remote writer after this cycle;
- no merge, PR, deploy, Production or Supabase mutation performed.
