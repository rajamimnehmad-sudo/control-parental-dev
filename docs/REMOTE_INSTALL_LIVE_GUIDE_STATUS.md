# Glosh Remote — Adaptive Remote Installer

Updated: 2026-08-24 23:10 ART

## Connection base

`REMOTE-INSTALL-CONNECTION-00`: **PASS FINAL DEV / CLOSED**.

The proven no-link stack remains frozen: broker → relay/WSS → Glosh Remote → local Wireless ADB, with HMAC/AES, fixed allowlist and no direct ADB exposure. Adaptive Autopilot reuses this stack and does not redesign it.

## Superseded guide

`REMOTE-INSTALL-LIVE-GUIDE-03`: **FAILED UX PHYSICAL / SUPERSEDED**.

The old guide-first model with large/duplicated controls is not the product UX.

## Current Android checkpoint

Local-only Codex checkpoint:
- HEAD `eaa44f1ba5da204d66a720ae6f5f805699ee22ee`;
- commit `feat(remote): add adaptive Samsung install autopilot`;
- Android tests 83/83 PASS;
- Python/broker 6/6 PASS;
- lint 0 errors / 11 pre-existing-external warnings;
- assemble PASS;
- APK `GloshRemote-LiveGuide-V2-DEV.apk`;
- 19,287,534 bytes;
- SHA-256 `ca222dbfabaa776a9e304c0e643923caf5ce394ad785a72481c8a58654b14920`;
- clean worktree;
- no push/PR/merge/deploy.

This checkpoint remains technically valuable, but **is not the final UX candidate** after real-user feedback exposed two product defects:
1. Mac/operator availability expires too quickly and repeatedly produces `soporte no disponible`;
2. customer UI still exposes laboratory/wizard controls and repeated states (`Volver`, `Me perdí`, guide/retry/state buttons) that contradict the one-tap product goal.

## Frozen final UX — One Tap

Authoritative UX contract:
`docs/REMOTE_INSTALL_ONE_TAP_AUTOPILOT_UX.md`.

Normal compatible-Samsung flow after one-time Accessibility bootstrap:

`CONECTAR CON SOPORTE` once → operator/request discovery → shortest-path Autopilot → missing Developer Options only if needed → Wireless Debugging only if needed → pairing-code dialog → exactly one contextual six-digit code consumed locally → local ADB pairing → frozen secure support stack → `Conectado con soporte`.

The customer does not manually advance internal technical states. Exceptions are only Android-owned protected prerequisites such as credential entry, first-time Accessibility/Restricted Settings, no Wi-Fi, or policy blocks; after resolving an exception, Autopilot resumes automatically from a fresh probe.

## Support availability contract

`soporte no disponible` must not appear merely because a short fixed timer expired while the Mac technician is actively waiting.

Use renewable internal leases:
- operator presence: short lease (target 120 s) renewed automatically about every 30 s while Mac is healthy and support-ready;
- customer request: target 10-minute lease renewed automatically about every 60 s while connect flow is active;
- accepted active install/support session: sliding/activity renewal rather than a fixed wall-clock expiry mid-flow;
- explicit cancel/revoke remains immediate.

Preferred current single-technician workflow: Mac explicitly enters `Esperar próximo cliente`; first valid request for that one-use slot is auto-accepted, removing a redundant approval click while preserving technician intent.

## Customer UI contract

Idle:
- one primary CTA: `CONECTAR CON SOPORTE`.

Running:
- one progress surface (`Conectando…`, `Preparando el teléfono…`, `Activando conexión segura…`);
- only normal secondary action: `Cancelar`.

Remove from normal path:
- `Volver`;
- `Me perdí`;
- `Mostrame`;
- duplicated status/retry/pairing buttons;
- manual next/continue controls for internal states.

Connected:
- one terminal state: `Conectado con soporte`.

## Current source-access blocker

The authoritative Android implementation `eaa44f1b…` is currently local-only on the Mac and is **not present in GitHub**. The remote `work/remote-install-connection-00` branch is older and does not contain the integrated Adaptive Autopilot checkpoint.

Therefore ChatGPT cannot safely finish the One-Tap hardening or build the final APK from the exact current source until that local checkpoint is exposed as an isolated GitHub handoff branch. This is a source-transfer blocker only, not a design blocker.

Required handoff is minimal: publish exact local HEAD `eaa44f1b…` to an isolated remote branch, without merge/PR/rebase/reset and without additional development. After that, ChatGPT resumes implementation/review from the exact checkpoint.

## Final closure

`REMOTE-INSTALL-LIVE-GUIDE-V2-04`: **AUTOMATED CHECKPOINT PASS / ONE-TAP HARDENING REQUIRED / SOURCE HANDOFF PENDING**.

Do not run the final S22 physical gate against the old `ca222d…` APK. Final gate must use the post-One-Tap APK built from the handed-off current source.

No Production/Supabase/merge/deploy.
