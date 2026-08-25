# Glosh Remote — Professional Guided Assistant

Updated: 2026-08-25 11:20 ART

## Executive status

`REMOTE-INSTALL-GUIDED-ASSISTANT-08`: **PASS AUTOMATED / PENDING DIRECT S22 PHYSICAL GATE**.

The connection base remains separately closed:

`REMOTE-INSTALL-CONNECTION-00`: **PASS FINAL DEV / CLOSED**.

The previous auto-click One-Tap route remains superseded. The active product is:

**Glosh opens the exact destination → the customer performs the protected Android tap → Glosh observes the trusted result → Glosh opens the next destination.**

## Frozen source

Implementation branch:

`work/remote-install-guided-assistant-08-chatgpt`

Immutable gate branch:

`gate/remote-guided-1313eea`

Exact HEAD:

`1313eea1324903348c6e375b3ce9327120b31ff9`

Base:

`b4a559b2707bd3040642208be643a8eefc6922ec`

All Guided Assistant code changes remain under:

`tools/glosh-remote-spike/**`

No Chrome, GloshIA, DAG, App Usuario/Admin, Supabase, Production or Device Owner code was modified.

## Automated gate — PASS

Command executed from a new detached worktree on the immutable gate branch:

```bash
ANDROID_HOME=/Users/yejielnehmad/Library/Android/sdk \
  bash tools/glosh-remote-spike/verify_guided_assistant.sh
```

Result:

- source architecture guard: **PASS**;
- Python protocol/broker/standby: **14/14 PASS**;
- Android JVM tests: **96/96 PASS**;
- Android lint: **PASS**;
- Android assemble: **PASS**;
- physical gate: **not run**;
- worktree: **clean**.

The architecture guard confirms that the active guided coordinator owns no Settings click executor and no Settings scroll executor.

## Frozen APK candidate

This is now the **only authorized Guided Assistant physical candidate**:

- filename: `GloshRemote-Guided-DEV.apk`;
- path at gate: `/private/tmp/glosh-guided-gate-1313/tools/glosh-remote-spike/app/build/outputs/apk/debug/GloshRemote-Guided-DEV.apk`;
- size: `19,287,538` bytes;
- SHA-256: `14ed9879f2b18559fdbef914fa2a89e572bef2b98082c1c10e935d3e0a6ecd10`;
- report: `/private/tmp/glosh-guided-gate-1313/tools/glosh-remote-spike/app/build/outputs/apk/debug/REMOTE-INSTALL-GUIDED-ASSISTANT-08-report.txt`.

Do not rebuild, rename by rebuilding, substitute another APK or change code before the physical gate. A different byte size or SHA is a different candidate and requires a new automated gate.

## Product flow under test

### Preparing

Glosh checks whether a Mac support console is available. This is displayed as `Preparando`, not as a fake numbered step.

### Step 1 of 4 — Accessibility

Glosh attempts the exact accessibility-service detail destination and safely falls back to general Accessibility settings.

Customer action:

`Activá Glosh Remote`.

Glosh must continue automatically when the service becomes active; no Continue button.

### Step 2 of 4 — Developer options

Glosh opens Developer options directly.

If they are unavailable, Samsung visual fallback guides the customer through:

`Acerca del teléfono → Información de software → Número de compilación ×7`.

The customer performs the taps. Glosh observes only. Device PIN/pattern/password is never read.

### Step 3 of 4 — Wireless debugging

Glosh prioritizes:

`android.settings.WIRELESS_DEBUGGING_SETTINGS`

The customer activates Wireless debugging and confirms `Permitir` if Android asks to trust the Wi-Fi network.

Normal guided mode performs:

- zero automatic Settings clicks;
- zero programmatic Settings scrolls;
- zero coordinate gestures.

### Step 4 of 4 — Pairing code

Glosh highlights `Vincular dispositivo con código` and the customer taps it.

A unique contextual six-digit code is read locally and submitted automatically. If descriptor or mDNS endpoint arrives later, the code remains only in memory until pairing can start. If automatic reading is ambiguous, the six-box app input and notification `RemoteInput` remain available.

The already-proven local ADB and encrypted Mac relay continue unchanged.

## Visual contract

App, floating coach and notification share `GuidePresentation`:

- four stable steps;
- one instruction at a time;
- compact graphite/lime card;
- microanimations for tap, switch, seven taps, code, wait, success and attention;
- reduced-motion support;
- floating `×` hides the card for the current instruction;
- repeated events from the same instruction cannot force it to reappear;
- notification remains as persistent fallback;
- no `ME PERDÍ`, `MOSTRARME`, `MOSTRARME DE NUEVO` or `VOLVER AL CÓDIGO`.

## Physical route decision

By explicit product-owner decision, the first physical UX gate moves directly to the personal Samsung S22 Ultra, delivered cable-free through Taildrop.

Rationale:

- all automated gates are already green;
- the remote connection base already passed with S22 and Mac on different networks;
- the remaining unknown is the real customer guidance experience;
- the product owner will perform the Android taps personally without technical coaching;
- this gives the most representative first UX signal.

The A23 laboratory gate is **deferred, not cancelled**. It remains available for controlled reproduction, logs and regression if the S22 gate finds a defect.

## Next gate — S22 cable-free customer run

Use the exact APK SHA above. Codex may only verify the artifact, send it by Taildrop and prepare the waiting Mac operator session. Codex must not install the APK on the phone, simulate taps, change phone settings remotely or rebuild the candidate.

Customer-run requirements:

- install the exact Taildrop APK;
- Mac operator remains available while the flow is performed;
- tap `CONECTAR CON SOPORTE` once;
- follow only the app, floating coach and notification;
- no verbal technical coaching unless the app becomes blocked;
- record where the wording, target, animation or transition is unclear;
- verify automatic code detection when the six-digit dialog appears;
- verify local ADB and Mac support authenticate;
- verify cancel/finish leaves no residual overlay or connection.

Required outcomes:

- `AUTOMATIC_SETTINGS_CLICKS=0`;
- `PROGRAMMATIC_SETTINGS_SCROLLS=0`;
- `COORDINATE_GESTURES=0`;
- exact Accessibility route or understandable fallback;
- direct Developer options probe;
- direct Wireless Debugging route;
- customer switch/confirmation detected;
- compact coach does not obscure the target;
- notification mirrors every step;
- pairing row guidance is clear;
- unique code submits automatically or the manual fallback is clear;
- local ADB connects;
- Mac support authenticates;
- crash/ANR = 0;
- residual overlays = 0;
- closing the session removes remote access.

## Superseded evidence

- APK SHA `23c26d…`: failed physical auto-click route; never use again.
- HEAD `54df3995…`: blocked physical auto-click repair; superseded.
- HEAD `b4a559b…`: historical deep-row repair/base; auto-click product route superseded.
- HEAD `6945d972…`: first Guided Assistant gate attempt; four narrow JVM failures corrected in `1313eea…`.

## Coordination

- connection base: PASS FINAL DEV / CLOSED;
- Guided Assistant automated gate: PASS at `1313eea…`;
- exact APK: frozen at SHA-256 `14ed9879…ecd10`;
- direct S22 Taildrop/customer gate: next;
- A23 laboratory gate: deferred and reserved for defects/regression;
- Motorola/Xiaomi adapters: later, based on physical evidence without rewriting the shared guide engine;
- no writer is active in Remote after this coordination update;
- no merge, PR, deploy, Production or Supabase mutation is authorized.
