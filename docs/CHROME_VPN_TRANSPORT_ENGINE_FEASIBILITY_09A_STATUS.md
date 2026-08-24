# CHROME-VPN-TRANSPORT-ENGINE-FEASIBILITY-09A

Updated: 2026-08-24 08:13 ART

## Coordination state

- `CHROME-VPN-TRANSPORT-ARCHITECTURE-08B`: **PASS FINAL ARCHITECTURE / REVIEWED BY CHATGPT** for the bounded UID-attribution spike.
- Local 08B functional commit: `05278e7f0fccb585117a3a79ce2d4a6c940ba07c`.
- Local 08B evidence/final HEAD: `ddfa0cab2e033d64a2f0968b541dad7f63be0eb3`.
- Physical A23 evidence: TCP/UDP owner attribution distinguished Chrome (`com.android.chrome`, UID 10222), Samsung Internet (UID 10262), and Google Search (UID 10214); product DNS/VPN preserved after rollback; resetCount remained 1.
- 08B parser/diagnostics are accepted only as feasibility instrumentation, not as production datapath. Required before production: direction normalization, IPv6 extension/fragment handling, async bounded owner admission/cache, UID/package invalidation and removal of production-sensitive diagnostic logging.

## 09A route

Status: **IN PROGRESS / next Codex task**.

Base local: `ddfa0cab2e033d64a2f0968b541dad7f63be0eb3`.

Goal: prove a controlled transport chain without default routes:

`Android TUN -> Glosh dispatcher -> UID/policy -> packet-oriented internal socketpair -> HEV 2.17.1 -> loopback SOCKS5 -> protect-before-connect upstream socket -> Internet -> return packets -> dispatcher -> Android TUN`.

Security invariants:

- one VpnService only;
- no `0.0.0.0/0` or `::/0` in 09A;
- controlled `/32` and `/128` fixture routes only;
- Glosh DNS path remains authoritative;
- non-Chrome fixture TCP/UDP must round-trip through transport engine;
- Chrome direct TCP/443 and UDP/443 must fail closed;
- unknown owner on a sensitive Chrome candidate must fail closed;
- every SOCKS upstream socket must pass `VpnService.protect()` before connect/send; protect failure means no connection;
- no Chrome reset; resetCount must stay 1;
- no second VPN, no Production, no push/PR/merge.

## HEV candidate pinned for feasibility

- Upstream: `heiher/hev-socks5-tunnel`.
- Release: `2.17.1`.
- Commit: `9a06bc6e7989da54e3d32ff701ef7a7ce4995d3a`.
- License: MIT; Android-required subcomponents previously reviewed as MIT/BSD-compatible engineering review; final third-party notices/SBOM required.
- External FD API exists through `hev_socks5_tunnel_main* (..., int tun_fd)` and upstream does not close an externally supplied TUN FD.
- Android build supports API 29, arm64-v8a/armeabi-v7a/x86/x86_64 and explicit 16 KiB ELF linker alignment.
- Known risk to stress in 09A: upstream issue #315 reports a rare Android 14 teardown SIGABRT in lwIP and was closed for lack of reproduction without an identified fix. Issue #323 UDP null dereference was fixed before release 2.17.1.

## 09A acceptance boundary

09A is a feasibility gate, **not** general full-tunnel/product approval. PASS requires controlled TCP+UDP round-trip for a non-Chrome app through HEV/SOCKS/protected sockets, Chrome direct TCP/UDP 443 drops, DNS preservation, no recursion, bounded backpressure, repeatable native start/stop/rollback, no crash/ANR/OOM, and exact preservation of Device Owner/Accessibility/product VPN/resetCount/data. General default routes remain a later ticket.