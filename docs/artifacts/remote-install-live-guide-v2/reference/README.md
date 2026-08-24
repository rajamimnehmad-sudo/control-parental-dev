# REMOTE-INSTALL-LIVE-GUIDE-V2-04 — GitHub handoff

Use this directory as the authoritative Codex handoff. Do **not** use the previously published binary ZIP at `docs/artifacts/remote-install-live-guide-v2/glosh_remote_live_guide_v2_prototype.zip`; its GitHub blob failed the integrity gate and is deprecated.

Authoritative files:

- `docs/REMOTE_INSTALL_LIVE_GUIDE_PRO_AUDIT_04.md`
- `docs/artifacts/remote-install-live-guide-v2/reference/ANDROID_PORTING_CONTRACT.md`
- `docs/artifacts/remote-install-live-guide-v2/reference/ACCEPTANCE_GATES.md`
- `docs/artifacts/remote-install-live-guide-v2/reference/HANDOFF_MANIFEST.json`
- `docs/artifacts/remote-install-live-guide-v2/reference/fixtures/samsung_s22_es.json`

Expected local implementation base to verify before editing:

`93735b1c2a9f493dcd491556b23920c50c3d66f2`

Reference file integrity:

- `ANDROID_PORTING_CONTRACT.md` — SHA-256 `59675fe9ff7186169fe43119c6935a3dfcecf20b1fa6511a4cf38cfe6d732c1f`, size 4788 bytes.
- `ACCEPTANCE_GATES.md` — SHA-256 `eacd65660a01d48ad951935ffb38c49d25b2df5847dda56cf73e8831d5aea4d8`, size 2561 bytes.
- `HANDOFF_MANIFEST.json` — SHA-256 `095317612340860640b7a38f32402b0c68e3127bd8e37dc1770a49472646d7e1`, size 3309 bytes.
- `fixtures/samsung_s22_es.json` — SHA-256 `c0bc2ab22c3dff7028c9b8ab0a59809193bdf5e7db191ce6aa44abb368e38eac`, size 1788 bytes.

Codex should fetch this branch without checking it out over the Android worktree, verify the hashes above, then implement V2 under `tools/glosh-remote-spike/**` only.

Gate order is mandatory: technical tests → Gate A Samsung guide-only → Gate B pairing UX → Gate C full remote session. Stop at the first reproducible physical failure.
