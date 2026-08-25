# REMOTE-INSTALL-ONE-TAP-HARDENING-05

Updated: 2026-08-24 ART

## Base reviewed

Exact code checkpoint reviewed by ChatGPT:

- remote branch: `handoff/remote-autopilot-eaa44`
- HEAD: `eaa44f1ba5da204d66a720ae6f5f805699ee22ee`
- commit: `feat(remote): add adaptive Samsung install autopilot`
- prior automated result reported from this exact checkpoint: Android 83/83 PASS; Python/broker 6/6 PASS; lint 0 errors; assemble PASS.

The previously built APK `GloshRemote-LiveGuide-V2-DEV.apk` SHA-256 `ca222dbfabaa776a9e304c0e643923caf5ce394ad785a72481c8a58654b14920` remains a valid automated checkpoint but is **not the final physical candidate** after the product UX findings below.

## Findings from exact source review

The Samsung Autopilot core is present and should be preserved:

- `AdaptiveInstallCoordinator` serializes the Samsung state machine;
- exact Settings window/snapshot/generation safety is present;
- safe automatic `ACTION_CLICK` flow is present;
- `SupportSessionCoordinator.confirmDeveloperOptions()` is already called automatically when Developer Options is recognized;
- once the broker descriptor is ready, `RemotePairingService` is started automatically;
- Wireless Debugging is opened automatically;
- `ContextualPairingCodeDetector` can produce exactly one contextual six-digit code;
- that code is passed to `RemotePairingService.ACTION_SUBMIT_CODE` automatically;
- the existing secure WSS/HMAC/AES/allowlist connection stack takes over after local ADB is ready.

Therefore this cycle MUST NOT redesign Autopilot, pairing, crypto, relay protocol or broker contract.

Two product defects remain:

1. `MainActivity` still exposes the superseded guide-first UX (`MOSTRARME`, `ME PERDÍ`, `VOLVER`, `ABRIR AJUSTES`, step counters and duplicated technical states) during paths that should now be automatic.
2. Mac standby has premature expiry semantics: the broker window is opened once, and `RemoteSession.expires_at` starts when the console starts instead of when an agent actually authenticates. This makes a technician who is simply waiting appear unavailable later.

## Frozen product UX

Normal user experience:

`install → open → CONECTAR CON SOPORTE → automatic preparation → automatic Wireless Debugging → automatic six-digit capture → automatic local pairing/ADB → secure Mac session → CONECTADO`

The user should not manage the workflow.

Allowed user interventions only:

- one-time Accessibility / Restricted Settings bootstrap when Android requires it;
- device PIN / pattern / password when Android requires it;
- a truly ambiguous/unsupported UI fallback;
- manual six-box pairing only if automatic contextual six-digit capture cannot be trusted.

## Android UI hardening

### MainActivity

Keep one normal primary action on HOME:

`CONECTAR CON SOPORTE`

The normal path must not expose:

- `MOSTRARME` / `MOSTRARME DE NUEVO`;
- `ME PERDÍ`;
- `VOLVER AL CÓDIGO`;
- normal `ABRIR AJUSTES` / `ABRIR DEPURACIÓN INALÁMBRICA` controls;
- guide-first `1 de 3`, `2 de 3`, `3 de 3` mental model;
- instructions telling the user to navigate Settings manually when Autopilot is active.

Normal in-progress screens are passive progress + one `CANCELAR` action at most.

Recommended product states/copy:

- CHECKING_SUPPORT: `Conectando con soporte…` / `Estamos preparando una conexión segura.`
- GUIDE_PERMISSION when Accessibility is not yet enabled: one required CTA `ACTIVAR AUTOMATIZACIÓN`; do not offer `CONTINUAR SIN GUÍA`, because Autopilot depends on the service.
- AUTOPILOT / DEVELOPER_OPTIONS / REQUESTING_SUPPORT / WIRELESS_DEBUGGING: `Preparando tu teléfono…` / `Glosh continúa automáticamente.`
- AUTOPILOT_CREDENTIAL: `Confirmá el bloqueo de pantalla` / user enters credential; no other workflow button except cancel.
- PREPARING pairing: `Completando conexión…`; do not expose pairing internals during automatic capture.
- manual pairing fallback only: show six boxes and a concise instruction; no `ME PERDÍ`/`VOLVER` loop.
- CONNECTED: `Conectado con soporte` + `FINALIZAR CONEXIÓN`.
- true unavailable after retry policy: `No pudimos contactar al técnico` + `REINTENTAR`, not `VOLVER`.

Update `WizardLayout.showHome()` copy so it no longer says the user will be shown exactly what to tap or that the normal third step is entering six numbers. Product promise should say Glosh prepares the phone automatically and only asks for intervention when Android requires it.

The old guide rendering helpers may remain for DEV/debug/fallback if useful, but they must not be normal-path product UI.

## Phone broker wait hardening

Files:

- `SupportSessionBrokerClient.java`
- `BrokerWaitPolicy.java`
- `SupportSessionCoordinator.java` only if needed for presentation/retry semantics.

Current request renewal is bounded to 5 request attempts. The active product connection attempt should survive short broker request TTLs without showing false `UNAVAILABLE` while the user is still connecting.

Requirements:

- keep short-lived individual broker requests and fresh ephemeral RSA identities;
- when poll returns `expired`, transparently create a fresh request while the product attempt remains active;
- increase/replace the fixed 5-attempt limit with a bounded **overall active-attempt window** suitable for a real support flow (target 30 minutes) rather than a tiny count of short broker TTLs;
- transient poll/network failures get bounded retry/backoff before surfacing failure;
- cancellation immediately stops renewals and revokes the current request best-effort;
- never reuse nonce/private identity across renewed requests;
- no secret logging.

Do not change Supabase or broker schema in this cycle.

## Mac standby / availability hardening

Files:

- `mac/broker_client.py`
- `mac/broker_console.py`
- `mac/glosh_remote_relay.py`
- tests as needed.

### Broker heartbeat

`operator_open` is a renewable lease. While the operator console is intentionally open and the session has not been stopped:

- call `operator_open` periodically (target every 60 seconds; retry transient failures without killing the console);
- this is a heartbeat/lease renewal, not a new logical technician session;
- `operator_close` still runs exactly once during final shutdown/best-effort cleanup.

The user-facing phone must therefore not lose support availability merely because a broker window TTL elapsed while the technician was waiting.

### Session TTL starts on actual connection

Current `RemoteSession` starts `expires_at` in `__init__`. Change semantics:

- store `duration_seconds` at construction;
- while no authenticated Android agent has ever claimed the session, the Mac may remain in standby without consuming the secure-session lifetime;
- set `expires_at = monotonic() + duration_seconds` only when the Android agent successfully authenticates/claims the session;
- `expire_session()` waits for `agent_ready` before starting/waiting for the expiry deadline;
- command/handler expiry checks must tolerate `expires_at is None` while waiting;
- after authenticated session starts, the existing bounded expiry remains enforced.

This keeps the support desk available while preserving a temporary authenticated session.

### Safe auto-accept

The technician explicitly starting the console puts it in `waiting for one client` mode.

Automatic acceptance is allowed only when:

- there is exactly **one** pending broker request;
- no Android agent is connected;
- no request has already been accepted for this `RemoteSession`.

Then seal the current descriptor and call `operator_accept` automatically.

If there are 2+ pending requests, fail closed to explicit operator selection; never guess which client.

After one request is accepted, do not auto-accept another until a new RemoteSession is deliberately started.

Manual `requests` / `accept <id>` can remain as technician/debug fallback.

## Preserve unchanged

Do not redesign or weaken:

- broker crypto/rendezvous sealing;
- WSS relay;
- mutual HMAC;
- AES-256-GCM;
- sequence/replay checks;
- fixed command allowlist;
- ephemeral ADB/RSA identity;
- no public ADB / no tcpip 5555;
- fail-closed Settings click authority;
- contextual pairing-code requirements;
- PIN/password/pattern non-capture.

Do not touch Chrome, GloshIA, DAG, App Usuario/Admin, Supabase or production Device Owner logic.

## Required tests/gates before APK

Android/JVM:

- retain all existing 83 tests;
- add/adjust BrokerWaitPolicy coverage for overall wait-window renewal rather than 5-count premature stop;
- cancel/reset stops renewal;
- normal UI path cannot require guide-first buttons to advance;
- automatic pairing path remains unchanged.

Python/mac:

- retain existing 6 broker/protocol tests;
- test broker heartbeat renews standby lease;
- transient heartbeat failure does not close operator console;
- RemoteSession has no expiry before authenticated agent;
- expiry starts when agent authenticates;
- auto-accept exactly one pending request;
- 0 pending → no accept;
- 2+ pending → no auto-accept;
- only one request can be auto-accepted per RemoteSession.

Then run:

- Android tests;
- Python/broker tests;
- `lintDebug` (0 errors);
- `assembleDebug`;
- clean local commit;
- exact APK path/size/SHA-256.

No phone is required for this technical closure.

After those gates pass, the new APK (not SHA `ca222d…`) becomes the sole S22 cable-free physical candidate.

## Final physical gate

S22 only, no USB.

Expected product experience:

1. install exact candidate;
2. open Glosh Remote;
3. tap `CONECTAR CON SOPORTE` once;
4. complete Accessibility/PIN only if Android explicitly asks;
5. observe automatic Settings navigation, Wireless Debugging, pairing-code capture, local ADB pairing and secure Mac connection;
6. zero wrong automatic clicks;
7. no normal `ME PERDÍ` / `MOSTRARME` / manual pairing unless automatic fallback is genuinely required.
