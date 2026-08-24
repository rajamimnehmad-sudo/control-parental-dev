# Glosh Remote — Supabase broker status

Updated: 2026-08-24 07:40 ART

## REMOTE-INSTALL-NOLINK-GUIDED-03A

- UX/wizard local candidate remains PASS FINAL ChatGPT at local HEAD `5af84ca4aa3701f91606ef61957f6494d90b3b94`.
- APK candidate historical: `GloshRemote-NoLink-Guided-03A.apk`, SHA-256 `a9e52ab9747e5858601e67c27f0622b680940919f4ff9f26ae7dc13c1b53f0e6`.
- Superseded for live no-link testing by the Supabase-integrated APK below.

## REMOTE-SESSION-BROKER-SUPABASE-01

Status: PASS TÉCNICO REVISADO POR CHATGPT / PENDIENTE ROTACIÓN DE CREDENCIAL + GATE FÍSICO NO-LINK.

Supabase project: `syeycayasyufedwoprea`.

Backend deployed:
- Edge Function `glosh-remote-broker` v2 ACTIVE.
- Endpoint: `https://syeycayasyufedwoprea.supabase.co/functions/v1/glosh-remote-broker`.
- Database objects: `public.glosh_remote_broker_config`, `public.glosh_remote_support_windows`, `public.glosh_remote_support_requests`.
- RLS enabled; anon/authenticated have no direct table access; service_role only for broker CRUD.
- Operator credential stored only as SHA-256; raw operator key is intended to remain only on operator Mac.
- Requests use nonce hashing, short TTL, explicit acceptance, single-use claim, revocation and anti-replay.
- Broker stores ciphertext only, never join/session key plaintext.
- `seal_context_sha256 = SHA-256(request_id + ':' + nonce)` preserves request+nonce binding without exposing raw nonce to operator.

Local integration PASS and ChatGPT diff review PASS:
- HEAD inicial `5af84ca4aa3701f91606ef61957f6494d90b3b94`.
- HEAD final / local commit `a2ff10744d9c867087f3747ceb9f587b42a96861` (`feat(remote): connect no-link flow to Supabase broker`).
- Worktree clean; 10 changed files all under `tools/glosh-remote-spike/**`.
- Android client migrated to POST `discover/request/poll/claim/revoke`.
- Mac client migrated to `operator_open/list/accept/revoke/close`.
- RSA-3072 / OAEP-SHA256 V2 bound to `SHA-256(request_id + ':' + nonce)`; Android recomputes binding locally before accepting descriptor.
- Diff review found no code blocker for DEV physical gate. Legacy V1 sealed-session compatibility remains only as compatibility path.
- HTTP live gate from Mac PASS: unauth operator_open=401; authenticated operator_open=201; discover available=true; synthetic request=201; operator_list returns metadata/public key/seal_context and no raw nonce; seal context local match exact; operator_accept=200; poll accepted; claim=200; local decrypt/JoinDescriptor PASS; second claim=409 already_claimed; operator_revoke=200; poll revoked; operator_close=200; discover available=false.
- Integrated Mac broker client gate PASS.
- Android tests 17/17 PASS; Python/protocol/broker 6/6 PASS; lintDebug PASS 0 errors / 18 pre-existing warnings; assembleDebug PASS.
- Operator key scan PASS: not present in Git or APK.
- Public endpoint confirmed embedded in APK.

Security action before physical gate:
- The raw operator credential used during setup was exposed in the private chat transcript while coordinating the gate. Treat it as compromised and rotate it before any further live physical session.
- Current Supabase check at 07:40 ART: `open_windows=0`, `active_requests=0`; nothing remains waiting or connected at the broker.
- Rotation procedure: Codex generates a fresh random operator key locally on the Mac, keeps raw value only in a mode-600 local temp file/environment, and returns only its SHA-256 to ChatGPT; ChatGPT updates only `glosh_remote_broker_config.value_sha256`. Do not paste the raw replacement key into chat or Git.

Current physical candidate after credential rotation:
- APK `GloshRemote-NoLink-Supabase-DEV.apk`.
- Path `/private/tmp/glosh-remote-install-connection-00-gate/tools/glosh-remote-spike/app/build/outputs/apk/debug/GloshRemote-NoLink-Supabase-DEV.apk`.
- Size `19,060,018` bytes.
- SHA-256 `29218638299e5d21312a878ce9c578758f298e63473530798616bea2ea04ca12`.

Residual product risk (not a DEV gate blocker):
- The current anonymous no-link rendezvous has no cryptographic identity for the customer before pairing. During an open support window, another Internet client could submit a spoofed request with matching manufacturer/model; explicit operator acceptance mitigates accidental connection but does not prove customer identity. This is acceptable for the controlled DEV gate, but product hardening must add an install-scoped or user-verifiable binding before general release.

Pending before final closure:
1. Rotate operator credential without exposing the new raw key outside the Mac.
2. Install the exact Supabase APK on S22 and run the physical no-link flow: open app → CONECTAR CON SOPORTE → operator sees request → explicit accept → sealed claim → guided Wireless Debugging → 6-digit pairing → authenticated agent → cancel/revoke.
3. Validate UX pulse/wizard physically and no link/descriptor exposure.
4. Then repeat the same flow cross-network (Mac and S22 on different Wi-Fi/LAN) before closing `REMOTE-INSTALL-CONNECTION-00`.

No Chrome, GloshIA, DAG, App Usuario/Admin or pre-existing Edge Function was modified by this integration. No push/PR/merge/deploy adicional from Codex.
