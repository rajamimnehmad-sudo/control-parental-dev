# Glosh Remote — Professional Guided Assistant

Updated: 2026-08-25 09:45 ART

## Connection base

`REMOTE-INSTALL-CONNECTION-00`: **PASS FINAL DEV / CLOSED**.

The proven no-link stack remains frozen:

`Supabase broker → temporary Mac relay/WSS → Glosh Remote → local Wireless ADB`

Preserved unchanged:
- RSA-OAEP rendezvous sealing;
- mutual HMAC authentication;
- AES-256-GCM command/results;
- fixed read-only allowlist;
- ephemeral ADB identity;
- no public ADB and no `adb tcpip 5555`;
- operator heartbeat, bounded phone request renewal and single-customer standby slot.

This cycle changes only the customer preparation experience.

## Product decision

The previous auto-click One-Tap route is **SUPERSEDED AS PRODUCT UX**.

Physical A23 evidence showed that trying to automate Samsung Settings rows and switches created brittle behavior:
- an internal Switch was previously selected instead of the navigable preference row;
- the row-only repair then failed on deep Samsung accessibility ancestry and performed three unwanted scrolls;
- a rescue overlay remained visible until cleanup.

The correct product is now:

**Glosh opens → customer confirms → Glosh observes → Glosh opens the next screen.**

The user may make simple Android taps. Reliability and clarity take priority over eliminating every tap.

## Active implementation

Task:
`REMOTE-INSTALL-GUIDED-ASSISTANT-08`

Implementation branch:
`work/remote-install-guided-assistant-08-chatgpt`

Exact current HEAD:
`6945d9728dc72a78c87fee334a078db5145eb080`

Base:
`b4a559b2707bd3040642208be643a8eefc6922ec`

Branch relation:
- ahead-only;
- 35 commits ahead;
- 0 behind;
- all changes remain under `tools/glosh-remote-spike/**`.

Status:
**CODE COMPLETE BY CHATGPT / REAL ANDROID TEST-LINT-ASSEMBLE GATE PENDING**.

No APK from this route is authorized yet.

## Four-step customer experience

### Preparing — support availability

Before step 1, Glosh checks whether a Mac support console is available.

UI label:
`Preparando`

This does not consume a fake numbered step.

### Step 1 of 4 — Activate Glosh Remote

Glosh attempts the exact accessibility-service details destination:

`android.settings.ACCESSIBILITY_DETAILS_SETTINGS`

with the Glosh service `ComponentName`, then safely falls back to:
- general Accessibility settings;
- general Settings.

Customer action:
`Activá Glosh Remote`.

As soon as the service becomes active, Glosh continues automatically. There is no Continue button.

### Step 2 of 4 — Developer options

Glosh opens Developer options directly.

If Developer options already exist:
- Glosh immediately probes the direct Wireless Debugging destination.

If they do not exist:
- Samsung fallback opens About phone;
- compact coach + highlight guide the customer through:
  - Information of software;
  - Build number ×7;
- customer performs the taps;
- Glosh observes the real resulting screen/state;
- device PIN/pattern/password, if requested, is entered only by the customer and is never read by Glosh.

### Step 3 of 4 — Wireless debugging

Glosh prioritizes the exact route:

`android.settings.WIRELESS_DEBUGGING_SETTINGS`

Customer actions:
- activate Wireless debugging;
- tap `Allow` if Android asks to trust the Wi-Fi network.

Glosh detects the state change and advances.

The guided coordinator owns:
- no Settings click executor;
- no Settings scroll executor;
- no coordinate gestures.

If the direct route is unavailable, Accessibility remains only a visual/observational fallback.

### Step 4 of 4 — Pairing code

Glosh highlights:
`Pair device with pairing code`.

The customer taps it.

Then:
- a unique contextual six-digit code is read locally and submitted automatically;
- if the broker descriptor or mDNS pairing endpoint is still arriving, the code is retained in memory until both are ready;
- the open code step is preserved when broker acceptance completes, so Glosh does not regress to Wireless Debugging or close the dialog;
- if automatic reading is ambiguous, the existing six-box app input and notification `RemoteInput` remain available.

Local ADB pairing and the already-proven secure Mac session then continue unchanged.

## Visual system

The app, notification and floating coach share one `GuidePresentation` model:
- exactly four stable steps;
- one action sentence at a time;
- graphite compact card with lime progress segments;
- small native microanimations:
  - tap;
  - switch;
  - seven taps;
  - six code boxes;
  - waiting spinner;
  - success check;
  - attention state;
- reduced-motion support via `ValueAnimator.areAnimatorsEnabled()`;
- floating `×` hides only the card for the current instruction;
- repeated Settings events cannot force the hidden card to reappear;
- a new instruction may show the card again;
- persistent notification remains as fallback;
- notification and overlay always mirror the same step.

Removed from normal UX:
- `ME PERDÍ`;
- `MOSTRARME`;
- `MOSTRARME DE NUEVO`;
- `VOLVER AL CÓDIGO`;
- automatic reveal scroll;
- automatic row/switch clicks;
- repeated technical states.

## Code and tests added

Runtime:
- `GuidePresentation`;
- `GuideCueView`;
- compact `CoachBarController`;
- persistent step-aware `GuideNotification`;
- exact Accessibility and Wireless Debugging routes;
- observer-only `AdaptiveInstallCoordinator`;
- observer/highlight-only `LiveGuideAccessibilityService`;
- guided four-step `MainActivity` / `WizardLayout`;
- pending pairing-code buffering inside `RemotePairingService`;
- broker-ready stage preservation inside `SupportSessionCoordinator`.

Tests:
- `GuidePresentationTest` locks step numbers, copy and cue selection;
- `GuidedAssistantArchitectureTest` forbids click/scroll authority in the guided coordinator;
- `SettingsRouteTest` requires exact Accessibility and Wireless Debugging routes first;
- all previous protocol, broker, crypto, pairing and Android tests remain.

Documentation/gate:
- `tools/glosh-remote-spike/GUIDED_ASSISTANT_08.md`;
- `tools/glosh-remote-spike/verify_guided_assistant.sh`;
- canonical README updated.

## Required automated gate

Run on exact HEAD `6945d972…`:

```bash
ANDROID_HOME=/Users/yejielnehmad/Library/Android/sdk \
  bash tools/glosh-remote-spike/verify_guided_assistant.sh
```

The gate must pass:
- source architecture guard: no click/scroll authority;
- Python protocol/broker/standby tests;
- Android JVM unit tests;
- Android lint;
- Android assemble;
- exact APK byte size and SHA-256 report.

Expected artifact name:
`GloshRemote-Guided-DEV.apk`.

If the build fails, Codex must stop and report; ChatGPT owns corrections.

## Required physical order

Only after automated PASS:

### A23 Samsung laboratory

Requirements:
- zero automatic Settings clicks;
- zero programmatic Settings scrolls;
- exact Accessibility route or safe fallback;
- direct Developer options probe;
- direct Wireless Debugging route;
- user switch/confirmation detected;
- compact coach does not obstruct target;
- hiding the coach persists for the current instruction;
- notification mirrors all four steps;
- pair-code row highlighted clearly;
- unique six-digit code submitted automatically;
- notification/manual code fallback works;
- local ADB and Mac support connect;
- overlay clears on cancel/finish;
- Content Filter, animations and Device Owner restored.

### S22 cable-free customer gate

Use the exact same gated A23 APK, without USB, and evaluate:
- instructions understood without verbal technical coaching;
- no confusing/repeated buttons;
- no residual overlay;
- no wrong automatic action;
- pairing/code experience clear;
- secure Mac connection successful.

## Superseded checkpoints

- APK SHA `23c26d…`: FAILED physical A23; never use again.
- HEAD `54df3995…`: blocked physical A23; superseded.
- HEAD `b4a559b…`: deep-row auto-click repair retained only as historical base; product route superseded by Guided Assistant.

## Coordination

- connection base: PASS FINAL DEV / CLOSED;
- Guided Assistant code: complete at `6945d972…`;
- automated build gate: pending;
- A23 guided physical gate: pending after automated PASS;
- S22 cable-free gate: pending after A23 PASS;
- Motorola/Xiaomi adapters: later, based on real evidence and without rewriting the shared guide engine;
- no Chrome, GloshIA, DAG, App Usuario/Admin, Supabase or production Device Owner changes;
- no merge, PR, deploy or Production action.
