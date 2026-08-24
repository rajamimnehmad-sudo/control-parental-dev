# Glosh Remote — Supabase broker status

Updated: 2026-08-24 06:43 ART

## REMOTE-INSTALL-NOLINK-GUIDED-03A

- UX/wizard local candidate remains PASS FINAL ChatGPT at local HEAD `5af84ca4aa3701f91606ef61957f6494d90b3b94`.
- APK candidate: `GloshRemote-NoLink-Guided-03A.apk`, SHA-256 `a9e52ab9747e5858601e67c27f0622b680940919f4ff9f26ae7dc13c1b53f0e6`.
- Cross-network physical gate remains pending.

## REMOTE-SESSION-BROKER-SUPABASE-01

Status: IN PROGRESS / backend v2 deployed, pending live HTTP + Android/Mac integration gate.

Supabase project: `syeycayasyufedwoprea`.

New isolated database objects:
- `public.glosh_remote_broker_config`
- `public.glosh_remote_support_windows`
- `public.glosh_remote_support_requests`

Security:
- RLS enabled on all three tables.
- `anon` and `authenticated` have no direct table access.
- `service_role` is the only role granted CRUD for the broker tables.
- Operator credential is stored only as SHA-256; raw operator key is intended to live only on the operator Mac.
- Phone requests use per-request nonce hashing, short TTL, explicit operator acceptance, single-use claim and revocation.
- Broker stores only ciphertext for the sealed session material, never the join/session key in clear.
- v2 adds `seal_context_sha256 = SHA-256(request_id + ':' + nonce)` so the operator can bind RSA-OAEP sealing to request+nonce without receiving the raw phone nonce.

Edge Function deployed:
- slug: `glosh-remote-broker`
- current version: 2 / ACTIVE.
- verify_jwt: false by design because phone entry is public; operator actions require custom `x-glosh-operator-key` authentication.
- endpoint: `https://syeycayasyufedwoprea.supabase.co/functions/v1/glosh-remote-broker`
- public actions: `discover`, `request`, `poll`, `claim`, `revoke`.
- operator actions: `operator_open`, `operator_list`, `operator_accept`, `operator_revoke`, `operator_close`.

Backend checks completed:
- migration `glosh_remote_broker_v1` applied successfully.
- migration `glosh_remote_broker_seal_context_v2` applied successfully.
- RLS/privilege check PASS: anon=false, authenticated=false, service_role=true.
- transactional schema lifecycle test PASS and ROLLBACK confirmed with zero test rows.
- security advisor only reports the intentional INFO `RLS enabled, no policy` for these broker tables; no new broker-specific WARN finding.

Pending before final PASS:
1. Live HTTP smoke from the Mac because ChatGPT runtime cannot resolve the project DNS for external POST testing.
2. Adapt local Android `SupportSessionBrokerClient` and Mac broker console to this Supabase action contract.
3. Compile APK with `BROKER_BASE_URL=https://syeycayasyufedwoprea.supabase.co/functions/v1/glosh-remote-broker`.
4. Physical no-link flow: operator opens support window → Android taps Connect → request appears → explicit accept → sealed descriptor claim → Wireless Debugging wizard → 6-digit pairing → authenticated agent → revoke/cancel.
5. Cross-network physical validation remains required before closing `REMOTE-INSTALL-CONNECTION-00`.

No Chrome, GloshIA, DAG, App Usuario/Admin or pre-existing Edge Function was modified.
