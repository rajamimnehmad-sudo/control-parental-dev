# Glosh Remote — Adaptive Remote Installer

Updated: 2026-08-24 23:43 ART

## Connection base

`REMOTE-INSTALL-CONNECTION-00`: **PASS FINAL DEV / CLOSED**.

The proven no-link stack remains frozen: broker → relay/WSS → Glosh Remote → local Wireless ADB, with HMAC/AES, fixed allowlist and no direct ADB exposure. One-Tap hardening reuses this stack and does not redesign it.

## Superseded guide

`REMOTE-INSTALL-LIVE-GUIDE-03`: **FAILED UX PHYSICAL / SUPERSEDED**.

The old guide-first UX is not the product route.

## Exact Android checkpoint now accessible

Handoff branch:
`handoff/remote-autopilot-eaa44`

Exact HEAD:
`eaa44f1ba5da204d66a720ae6f5f805699ee22ee`

Commit:
`feat(remote): add adaptive Samsung install autopilot`

Reported automated gates for this exact checkpoint:
- Android 83/83 PASS;
- Python/broker 6/6 PASS;
- lint 0 errors / 11 pre-existing-external warnings;
- assemble PASS;
- clean worktree.

Historical checkpoint APK:
- `GloshRemote-LiveGuide-V2-DEV.apk`;
- 19,287,534 bytes;
- SHA-256 `ca222dbfabaa776a9e304c0e643923caf5ce394ad785a72481c8a58654b14920`.

That APK is a valid automated checkpoint but **not the final S22 candidate** after product UX review.

## Exact source review — result

ChatGPT reviewed the handed-off source. The important automatic chain is already present and must be preserved:
- Samsung adaptive state machine;
- trusted Settings window/snapshot/generation guards;
- exact automatic click transactions;
- automatic detection of Developer Options;
- automatic support request when Developer Options is ready;
- automatic start of `RemotePairingService` when the broker descriptor arrives;
- automatic Wireless Debugging navigation;
- contextual six-digit code detection;
- automatic submission of a unique safe code to local pairing;
- reuse of the already-proven secure support stack after local ADB is ready.

No redesign of Autopilot/pairing/crypto is needed.

## Active task

`REMOTE-INSTALL-ONE-TAP-HARDENING-05`: **ACTIVE / CODE CONTRACT FROZEN / ENVIRONMENT EXECUTION PENDING**.

Authoritative implementation contract:
`docs/REMOTE_INSTALL_ONE_TAP_HARDENING_05.md`.

Two defects are being closed:
1. customer UI still exposes old guide/lab controls and repeated technical states;
2. Mac standby availability/session lifetime starts too early and broker presence is not heartbeated continuously.

## Frozen final UX

Normal compatible Samsung experience:

`install → open → CONECTAR CON SOPORTE → automatic preparation → automatic Wireless Debugging → automatic six-digit capture → automatic local pairing/ADB → secure Mac session → CONECTADO`.

Normal user intervention only when Android itself requires:
- one-time Accessibility / Restricted Settings bootstrap;
- device PIN/pattern/password;
- true ambiguous/unsupported fallback;
- manual six-box pairing only if automatic contextual code capture cannot be trusted.

Remove from normal customer path:
- `MOSTRARME` / `MOSTRARME DE NUEVO`;
- `ME PERDÍ`;
- `VOLVER AL CÓDIGO`;
- internal `ABRIR AJUSTES` / `ABRIR DEPURACIÓN INALÁMBRICA` progression buttons;
- `1 de 3 / 2 de 3 / 3 de 3` guide-first mental model.

Running state: passive progress + `CANCELAR` at most.

## Availability fix

Mac/operator:
- broker `operator_open` becomes a heartbeat/renewable lease while console is intentionally open;
- standby before any authenticated Android does not consume secure-session lifetime;
- authenticated session TTL starts when the Android agent actually authenticates;
- first and only pending request may be auto-accepted in explicit single-client waiting mode;
- 2+ pending requests => fail closed to explicit technician selection.

Phone:
- short request expiry renews transparently during one active support attempt;
- overall support attempt is bounded by an appropriate wall-clock window rather than only five short request leases;
- transient poll/network failures use bounded retry before surfacing failure;
- cancel/revoke immediately stops renewal.

No Supabase/schema change is required for this cycle.

## Remaining technical execution

The remaining work requires the Mac/Android build environment:
1. apply the frozen One-Tap hardening only under `tools/glosh-remote-spike/**`;
2. add narrow tests for heartbeat/session-start/auto-accept/request renewal/UI progression;
3. run Android tests + Python/broker tests + lint + assemble;
4. make one clean local commit;
5. produce exact APK path/size/SHA-256;
6. expose that exact APK for ChatGPT delivery/review.

No phone is required for this technical closure.

## Final physical gate

After technical PASS, only the new post-hardening APK goes to S22, cable-free, with zero wrong automatic clicks and one-tap UX confirmation.

## Coordination

- `REMOTE-INSTALL-CONNECTION-00`: PASS FINAL DEV / CLOSED.
- `REMOTE-INSTALL-LIVE-GUIDE-03`: FAILED UX / SUPERSEDED.
- `REMOTE-INSTALL-LIVE-GUIDE-V2-04`: automated checkpoint preserved at `eaa44f1b…`; superseded as final candidate by One-Tap hardening.
- `REMOTE-INSTALL-ONE-TAP-HARDENING-05`: ACTIVE; exact source reviewed, contract frozen, environment execution next.
- `REMOTE-INSTALL-MAC-OPERATOR-04`: partially absorbed by One-Tap standby/heartbeat hardening.
- `REMOTE-INSTALL-PRECHECK-05`, `REMOTE-INSTALL-PIPELINE-06`, `REMOTE-INSTALL-DEVICE-OWNER-COMMIT-07`: preserved for later install pipeline.
- `REMOTE-ADAPTIVE-INSTALL-PILOT-01`: waits for post-hardening APK + S22 cable-free gate.

Do not touch Chrome, GloshIA, DAG, App Usuario/Admin, Supabase or production Device Owner logic.
No merge/deploy/Production.
