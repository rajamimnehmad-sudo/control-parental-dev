# Glosh Remote — Supabase broker status

Updated: 2026-08-24 09:23 ART

## REMOTE-INSTALL-NOLINK-GUIDED-03A

- UX/wizard local candidate PASS FINAL ChatGPT at local HEAD `5af84ca4aa3701f91606ef61957f6494d90b3b94`.
- Superseded for live no-link testing by the Supabase-integrated candidate below.

## REMOTE-SESSION-BROKER-SUPABASE-01

Status: PASS TÉCNICO + PASS FÍSICO NO-LINK.

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

Physical no-link gate PASS:
- Final local HEAD `475bd35b2934f9dca1a54f0b29dc4c320eacd223`.
- Local commit `fix(remote): renew expired support requests`.
- Worktree clean.
- APK `GloshRemote-NoLink-Retry-DEV.apk`.
- Size `19,061,222` bytes.
- SHA-256 `5448e97dc458e3770a0ca82fe18e3124a7fcbad0034a1a72dbd5b74c537fbc3b`.
- Device Samsung SM-S908E / Android 16 / SDK 36.
- User clarified after the handoff that the physical run was performed with Mac and S22 on different Wi-Fi networks. Treat as cross-network PASS provided they were independent networks/LANs and not merely two SSIDs of the same router.
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

Security debt before product:
- The operator credential used during DEV coordination was exposed in the private chat transcript. Rotation was explicitly deferred by the user for the DEV gate; rotate before product/general release.
- Anonymous rendezvous still lacks cryptographic customer identity before pairing; explicit operator acceptance mitigates accidental connection but install-scoped/user-verifiable binding remains product hardening.

Next route:
- `REMOTE-INSTALL-CONNECTION-00` may close PASS if the two Wi-Fi networks were independent LANs.
- Advance to `REMOTE-ADAPTIVE-INSTALL-PILOT-01`: use the now-proven no-link remote ADB path to perform real Glosh installations manually/adaptively by OEM/model, with Codex scanning accounts/profiles/restrictions and documenting recipes before further automation.

No Chrome, GloshIA, DAG, App Usuario/Admin or pre-existing Edge Function was modified by the physical gate.
