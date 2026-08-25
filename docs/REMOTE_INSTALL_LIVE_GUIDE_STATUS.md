# Glosh Remote — Adaptive Remote Installer

Updated: 2026-08-25 01:08 ART

## Connection base

`REMOTE-INSTALL-CONNECTION-00`: **PASS FINAL DEV / CLOSED**.

The proven no-link stack remains frozen: broker → relay/WSS → Glosh Remote → local Wireless ADB, with HMAC/AES, fixed allowlist and no direct ADB exposure. One-Tap hardening reuses this stack and does not redesign it.

## Superseded guide

`REMOTE-INSTALL-LIVE-GUIDE-03`: **FAILED UX PHYSICAL / SUPERSEDED**.

The old guide-first UX is not the product route.

## Preserved automated checkpoint

Handoff branch:
`handoff/remote-autopilot-eaa44`

Exact base HEAD:
`eaa44f1ba5da204d66a720ae6f5f805699ee22ee`

Reported gates on that checkpoint:
- Android 83/83 PASS;
- Python/broker 6/6 PASS;
- lint 0 errors / 11 pre-existing-external warnings;
- assemble PASS;
- clean worktree.

Historical checkpoint APK SHA-256:
`ca222dbfabaa776a9e304c0e643923caf5ce394ad785a72481c8a58654b14920`.

That APK is preserved for evidence only and is **not the final S22 candidate**.

## One-Tap implementation

`REMOTE-INSTALL-ONE-TAP-HARDENING-05`: **PASS AUTOMATED / PENDING S22 PHYSICAL GATE**.

Isolated implementation branch:
`work/remote-install-one-tap-05-chatgpt`

Exact gated HEAD:
`59f6b39be1b39005bacdc848ff0717240b43ed67`

Base is exactly `eaa44f1b…`; branch is ahead-only and does not touch `main`.

Changed runtime/test scope remains limited to `tools/glosh-remote-spike/**`:
- customer One-Tap UI;
- Mac standby/heartbeat;
- one-client safe autoaccept;
- session TTL start on first authenticated Android;
- 30-minute phone request-renewal window;
- bounded request/poll retry;
- explicit manual pairing fallback state;
- narrow Java/Python tests;
- one-command verification/build script with isolated Python dependency bootstrap.

Implementation handoff/evidence:
`tools/glosh-remote-spike/ONE_TAP_HARDENING_05.md`.

Automated local-environment gate:
`tools/glosh-remote-spike/verify_one_tap.sh`.

## Frozen customer UX implemented

Normal compatible Samsung experience:

`install → open → CONECTAR CON SOPORTE → automatic preparation → automatic Wireless Debugging → automatic six-digit capture → automatic local pairing/ADB → secure Mac session → Conectado con soporte`.

Normal screen:
- one initial CTA: `CONECTAR CON SOPORTE`;
- during work: passive progress + `CANCELAR`;
- terminal: `Conectado con soporte` + `FINALIZAR CONEXIÓN`.

Normal path no longer exposes:
- `MOSTRARME` / `MOSTRARME DE NUEVO`;
- `ME PERDÍ`;
- `VOLVER AL CÓDIGO`;
- internal manual progression buttons;
- `1 de 3 / 2 de 3 / 3 de 3` guide-first mental model.

Protected exceptions remain explicit:
- first-time Accessibility bootstrap: `ACTIVAR AUTOMATIZACIÓN`;
- Android PIN/pattern/password;
- real fail-closed ambiguous Settings fallback;
- six-box pairing only when automatic code capture is not safe/unique.

## Availability implementation

Mac/operator:
- `operator_open` renews as heartbeat every 60 s while the console is intentionally waiting;
- temporary heartbeat errors do not terminate standby;
- exactly one pending request is autoaccepted once for that RemoteSession;
- 0 requests do nothing;
- 2+ requests fail closed to manual technician selection;
- manual and automatic acceptance share one session-level customer slot;
- once either path accepts one request, that RemoteSession cannot deliver its descriptor to a second phone;
- standby does not consume authenticated-session TTL;
- session TTL starts only on first authenticated Android and is not extended on reconnect.

The shared customer slot closes the race discovered during ChatGPT review: with two pending phones, a manual accept of A must never make remaining B eligible for automatic acceptance.

Phone:
- old five-request limit removed;
- request renewal uses a 30-minute overall bounded window;
- expired request creates fresh RSA identity/request id/nonce;
- transient request/poll failures use bounded backoff (500 ms → 1 s → 2 s → 4 s → 5 s, max six consecutive failures);
- a successful broker response resets retry budget;
- cancel/revoke stops renewal and destroys local rendezvous identity;
- claim remains intentionally fail-closed on ambiguous transport failure because it consumes one-time ciphertext.

No Supabase/schema change was made.

## Automated gate — PASS

The first real execution stopped before Gradle because the system Python lacked `websockets`; this was a verification-script bootstrap defect, not a product/runtime failure. HEAD `59f6b39b…` fixed the gate by creating a temporary isolated venv and installing exactly `mac/requirements.txt`.

The corrected gate then completed successfully on the Mac/local Android build environment:

- HEAD: `59f6b39be1b39005bacdc848ff0717240b43ed67`;
- Python protocol/broker/standby tests: **PASS**;
- Android JVM unit tests: **PASS**;
- lint: **PASS**;
- assemble: **PASS**;
- git status: **clean**.

Exact generated APK:
- filename: `GloshRemote-OneTap-DEV.apk`;
- path at gate: `/private/tmp/glosh-one-tap-build-59f6b39b/tools/glosh-remote-spike/app/build/outputs/apk/debug/GloshRemote-OneTap-DEV.apk`;
- size: `19,287,534` bytes;
- SHA-256: `23c26d864d8ad9d3d6b3e00ae2149307a520f20b5172ccba02ce5df30f1e6390`.

This exact APK is now the **only authorized S22 One-Tap physical-gate candidate**. Do not rebuild or substitute a different artifact before the physical gate unless a new code change is intentionally made.

## Artifact delivery note

Codex exposed the exact APK and report through a temporary Cloudflare Quick Tunnel after the PASS. ChatGPT's current execution runtime could not resolve the `trycloudflare.com` domain, so ChatGPT could not independently copy the APK into its own sandbox attachment storage in this cycle. This is an artifact-transfer limitation only; it does not change the automated gate result or APK identity above.

## Final physical gate

Only remaining product gate for this cycle:
- install the exact APK with SHA-256 `23c26d864d8ad9d3d6b3e00ae2149307a520f20b5172ccba02ce5df30f1e6390` on S22;
- no USB / cable-free customer-like run;
- user taps `CONECTAR CON SOPORTE` once;
- verify automatic shortest-path Samsung navigation, Wireless Debugging, contextual six-digit capture, local pairing/ADB and secure Mac session;
- zero wrong automatic clicks;
- protected OS credential prompts may require the user, then Autopilot resumes;
- ambiguous state must fail closed to minimal fallback, not blind click.

## Coordination

- `REMOTE-INSTALL-CONNECTION-00`: PASS FINAL DEV / CLOSED.
- `REMOTE-INSTALL-LIVE-GUIDE-03`: FAILED UX / SUPERSEDED.
- `REMOTE-INSTALL-LIVE-GUIDE-V2-04`: automated checkpoint preserved at `eaa44f1b…`; superseded as final candidate by One-Tap hardening.
- `REMOTE-INSTALL-ONE-TAP-HARDENING-05`: **PASS AUTOMATED at `59f6b39b…`; exact APK frozen; pending S22 physical gate only**.
- `REMOTE-INSTALL-MAC-OPERATOR-04`: standby/heartbeat portion absorbed into One-Tap hardening; richer operator product remains later.
- `REMOTE-INSTALL-PRECHECK-05`, `REMOTE-INSTALL-PIPELINE-06`, `REMOTE-INSTALL-DEVICE-OWNER-COMMIT-07`: preserved for later install pipeline.
- `REMOTE-ADAPTIVE-INSTALL-PILOT-01`: next action is the exact post-hardening APK on S22 cable-free.

Do not touch Chrome, GloshIA, DAG, App Usuario/Admin, Supabase or production Device Owner logic.
No merge/deploy/Production.