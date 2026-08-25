# Glosh Remote — Adaptive Remote Installer

Updated: 2026-08-25 00:25 ART

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

## Active implementation

`REMOTE-INSTALL-ONE-TAP-HARDENING-05`: **CODE COMPLETE BY CHATGPT / REAL GRADLE GATE PENDING**.

Isolated implementation branch:
`work/remote-install-one-tap-05-chatgpt`

Current implementation HEAD:
`7258b131d6e9079b9bb8cfb552024d1d82526438`

Base is exactly `eaa44f1ba5da204d66a720ae6f5f805699ee22ee`; branch is ahead-only and does not touch `main`.

Changed runtime/test scope remains limited to `tools/glosh-remote-spike/**`:
- customer One-Tap UI;
- Mac standby/heartbeat;
- one-client safe autoaccept;
- session TTL start on first authenticated Android;
- 30-minute phone request-renewal window;
- bounded request/poll retry;
- explicit manual pairing fallback state;
- narrow Java/Python tests;
- one-command verification/build script.

Implementation handoff/evidence:
`tools/glosh-remote-spike/ONE_TAP_HARDENING_05.md`.

Automated local-environment gate:
`tools/glosh-remote-spike/verify_one_tap.sh`.

## Frozen customer UX now implemented

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
- `1 de 3 / 2 de 3 / 3 de 3` mental model.

Protected exceptions remain explicit:
- first-time Accessibility bootstrap: `ACTIVAR AUTOMATIZACIÓN`;
- Android PIN/pattern/password;
- real fail-closed ambiguous Settings fallback;
- six-box pairing only when automatic code capture is not safe/unique.

## Availability implementation now present

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

The shared customer slot closes a race discovered during ChatGPT review: with two pending phones, a manual accept of A must never make remaining B eligible for automatic acceptance.

Phone:
- old five-request limit removed;
- request renewal uses a 30-minute overall bounded window;
- expired request creates fresh RSA identity/request id/nonce;
- transient request/poll failures use bounded backoff (500 ms → 1 s → 2 s → 4 s → 5 s, max six consecutive failures);
- a successful broker response resets retry budget;
- cancel/revoke stops renewal and destroys local rendezvous identity;
- claim remains intentionally fail-closed on ambiguous transport failure because it consumes one-time ciphertext.

No Supabase/schema change was made.

## ChatGPT validation completed

Independent smoke validation outside the Android build environment:
- `BrokerWaitPolicy` compiled with JDK and passed >5 renewals, 30-minute cutoff and retry reset/backoff cases;
- Mac heartbeat continued after a simulated transient failure;
- 0/1/2+ request autoaccept policy behaved fail-closed as designed;
- manual acceptance and autoaccept share the one-customer slot in dedicated tests;
- standby/session TTL logic did not consume lifetime before client authentication.

Repository compare from `eaa44f1b…` confirms all runtime/test/document/build-script changes remain under `tools/glosh-remote-spike/**`.

These smokes are useful evidence but **do not replace the real Gradle gate**.

## Remaining technical gate

Single command on the Mac/local Android build environment:

```bash
bash tools/glosh-remote-spike/verify_one_tap.sh
```

The script runs:
- `python3 -m unittest test_protocol.py test_broker.py test_one_tap_standby.py`;
- `:app:testDebugUnitTest`;
- `:app:lintDebug`;
- `:app:assembleDebug`;
- copies the result as `GloshRemote-OneTap-DEV.apk`;
- calculates byte size + SHA-256;
- writes `REMOTE-INSTALL-ONE-TAP-HARDENING-05-report.txt`.

No A23/S22 is required for this build gate.

## Final physical gate

After Gradle/Python PASS and ChatGPT review, only the new post-hardening APK goes to S22 cable-free for the real One-Tap UX gate.

## Coordination

- `REMOTE-INSTALL-CONNECTION-00`: PASS FINAL DEV / CLOSED.
- `REMOTE-INSTALL-LIVE-GUIDE-03`: FAILED UX / SUPERSEDED.
- `REMOTE-INSTALL-LIVE-GUIDE-V2-04`: automated checkpoint preserved at `eaa44f1b…`; superseded as final candidate by One-Tap hardening.
- `REMOTE-INSTALL-ONE-TAP-HARDENING-05`: CODE COMPLETE at `7258b131…` on isolated ChatGPT branch; pending real Gradle/Python gate, therefore not PASS yet.
- `REMOTE-INSTALL-MAC-OPERATOR-04`: standby/heartbeat portion absorbed into One-Tap hardening; richer operator product remains later.
- `REMOTE-INSTALL-PRECHECK-05`, `REMOTE-INSTALL-PIPELINE-06`, `REMOTE-INSTALL-DEVICE-OWNER-COMMIT-07`: preserved for later install pipeline.
- `REMOTE-ADAPTIVE-INSTALL-PILOT-01`: waits post-hardening technical PASS + S22 cable-free UX gate.

Do not touch Chrome, GloshIA, DAG, App Usuario/Admin, Supabase or production Device Owner logic.
No merge/deploy/Production.
