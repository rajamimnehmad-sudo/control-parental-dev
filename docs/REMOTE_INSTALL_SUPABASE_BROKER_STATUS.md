# Glosh Remote — Supabase broker status

Updated: 2026-08-24 09:49 ART

## REMOTE-INSTALL-NOLINK-GUIDED-03A

- UX/wizard local candidate PASS FINAL ChatGPT at local HEAD `5af84ca4aa3701f91606ef61957f6494d90b3b94`.
- Superseded for live no-link testing by the Supabase-integrated candidate below.

## REMOTE-SESSION-BROKER-SUPABASE-01

Status: PASS FINAL DEV — técnico + físico no-link + cross-network.

Supabase project: `syeycayasyufedwoprea`.

Backend deployed:
- Edge Function `glosh-remote-broker` v2 ACTIVE.
- Endpoint: `https://syeycayasyufedwoprea.supabase.co/functions/v1/glosh-remote-broker`.
- Database objects: `public.glosh_remote_broker_config`, `public.glosh_remote_support_windows`, `public.glosh_remote_support_requests`.
- RLS enabled; anon/authenticated have no direct table access; service_role only for broker CRUD.
- Requests use nonce hashing, short TTL, explicit acceptance, single-use claim, revocation and anti-replay.
- Broker stores ciphertext only, never join/session key plaintext.
- `seal_context_sha256 = SHA-256(request_id + ':' + nonce)` preserves request+nonce binding without exposing raw nonce to operator.

Local integration PASS and ChatGPT diff review PASS:
- Base local `5af84ca4aa3701f91606ef61957f6494d90b3b94`.
- Supabase integration commit `a2ff10744d9c867087f3747ceb9f587b42a96861` (`feat(remote): connect no-link flow to Supabase broker`).
- Android client POST `discover/request/poll/claim/revoke` and Mac client `operator_open/list/accept/revoke/close`.
- RSA-3072 / OAEP-SHA256 V2 bound to `SHA-256(request_id + ':' + nonce)`.
- HTTP live gate from Mac PASS end-to-end, including 401 without operator key, explicit accept, ciphertext-only broker, local decrypt, second claim 409, revoke and close.

Physical no-link + cross-network gate PASS:
- Final local HEAD `475bd35b2934f9dca1a54f0b29dc4c320eacd223`.
- Local commit `fix(remote): renew expired support requests` reviewed by ChatGPT: PASS.
- Worktree clean.
- APK `GloshRemote-NoLink-Retry-DEV.apk`.
- Size `19,061,222` bytes.
- SHA-256 `5448e97dc458e3770a0ca82fe18e3124a7fcbad0034a1a72dbd5b74c537fbc3b`.
- Device Samsung SM-S908E / Android 16 / SDK 36.
- Physical run was performed with Mac and S22 on different Wi-Fi networks; cross-network requirement is satisfied.
- No-link flow PASS: no link/descriptor exposed, automatic broker request PASS, explicit operator acceptance PASS, descriptor hidden from user/broker PASS.
- Guided wizard PASS; Wireless Debugging and 6-digit pairing PASS.
- WSS/HMAC/AES authentication PASS.
- `ping=pong`; `whoami` contains `uid=2000(shell)`; `device` reports Samsung SM-S908E / Android 16 / SDK 36; `status` authenticated.
- Non-allowlisted `uname` rejected.
- UX CONNECTED PASS; Connect hidden, Cancel visible; rotation/recreation preserves CONNECTED.
- Cancel/revoke PASS; post-cancel status has no agent; broker closed with `available=false`; relay and Quick Tunnel closed; no crash/ANR observed.
- Glosh/Device Owner/Chrome/GloshIA were not modified.
- Android tests 19/19 PASS; Python 6/6 PASS; lintDebug PASS; assembleDebug PASS.

Timeout retry fix:
- Expired broker requests renew with fresh identity, nonce and request ID.
- Maximum five requests / roughly ten minutes.
- Renewal only for `expired`; cancellation, revocation and errors remain fail-closed.
- Unit tests PASS.
- Full physical TTL-expiry renewal was not exercised because the accepted physical request completed immediately; this remains a narrow residual validation, not a blocker for connection PASS.

## REMOTE-INSTALL-OEM-GUIDANCE-02

Status: NEXT / IN PROGRESS DESIGN — completar antes de iniciar pilotos adaptativos reales.

Objetivo aprobado por usuario:
- Glosh Remote detecta automáticamente fabricante/modelo/Android/SDK.
- Animación nativa en loop dentro de Glosh, no GIF pesado ni copia exacta de Ajustes.
- Tres recetas iniciales: Samsung, Motorola y Xiaomi/Redmi/POCO; fallback Android genérico para otros OEM.
- Samsung: `Ajustes → Acerca del teléfono → Información de software → Número de compilación ×7`.
- Motorola: `Ajustes → Acerca del teléfono` o `Ajustes → Sistema → Acerca del teléfono → Número de compilación ×7`.
- Xiaomi/Redmi/POCO: `Ajustes → Acerca del teléfono → Información detallada y especificaciones → versión OS/MIUI varias veces`; luego `Ajustes adicionales → Opciones de desarrollador`.
- Después de Developer Options, intentar apertura directa de Wireless Debugging cuando el intent/resolución del OEM lo permita; fallback a Developer Settings.
- Segunda animación específica: `Opciones de desarrollador → Depuración inalámbrica → Activar → Emparejar dispositivo con código`.
- El código de 6 dígitos debe poder ingresarse tanto dentro de Glosh Remote como desde la notificación; ambos caminos comparten el mismo estado y al completar 6 dígitos se intenta pairing automáticamente.
- La animación resalta secuencialmente sólo el elemento que el usuario debe tocar, con flecha/pulso, y se detiene/respeta `ValueAnimator.areAnimatorsEnabled()`.
- No overlays sobre Ajustes, no Accessibility adicional para controlar Settings, no taps automatizados dentro de Settings.
- UX actual, broker, crypto, relay, allowlist y estados IDLE/PREPARING/CONNECTED deben permanecer intactos.

Fuentes oficiales verificadas 2026-08-24: Samsung Developer/Samsung Support, Motorola Support, Xiaomi Global Support y Android Developers.

## Next route

- `REMOTE-INSTALL-CONNECTION-00`: PASS FINAL DEV / CLOSED.
- `REMOTE-INSTALL-OEM-GUIDANCE-02`: prioridad inmediata antes de pilotos.
- `REMOTE-ADAPTIVE-INSTALL-PILOT-01`: queda en espera hasta terminar y validar físicamente el nuevo guiado OEM.

Security debt before product:
- Rotate the operator credential before product/general release.
- Add install-scoped/user-verifiable binding for anonymous rendezvous before general release.

No Chrome, GloshIA, DAG, App Usuario/Admin or pre-existing Edge Function should be modified by this UX task.
