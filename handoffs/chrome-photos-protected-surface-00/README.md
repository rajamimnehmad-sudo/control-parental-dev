# CHROME-PHOTOS-PROTECTED-SURFACE-00 v2

This branch carries a temporary handoff package for Codex. It is intentionally isolated from main and does not authorize merge, PR, publication, Production, or destructive actions.

Prepared base SHA: `36b7c004f0f19a77439cd90c819b1195ee02cb49`

IMPORTANT: ignore the old ZIP in this branch. Its GitHub upload was truncated. Use only the uncompressed text artifacts below.

## Patch

The canonical patch is stored as five plain-text parts under:

`handoffs/chrome-photos-protected-surface-00/patch-parts/`

Reassemble from repository root with:

```bash
cat handoffs/chrome-photos-protected-surface-00/patch-parts/CHROME-PHOTOS-PROTECTED-SURFACE-00-v2.patch.part{01,02,03,04,05} \
  > /tmp/CHROME-PHOTOS-PROTECTED-SURFACE-00-v2.patch
sha256sum /tmp/CHROME-PHOTOS-PROTECTED-SURFACE-00-v2.patch
```

Expected final patch SHA-256:

`b497f9a0e84b5362d2d13bf529e90ecf5a1ca77e73dfc69e7416373935b08ef6`

Expected part SHA-256 values:

- part01: `2b1ccff97f07b27440253176775221f0167d28e50fcbffa89994fbe87e5393d9`
- part02: `05b856d7c97f59ba9bba55b93b93e446358de2c2fa8d8d0cabfd8c182c6eadf9`
- part03: `2f8f9362eb9a374bb2ab05f3f244094031f6e795fd68a5edec8aa06b2357d87a`
- part04: `ae98a880db9fcc73dbfeb5b0b49e3626bb00a9d58e1f4ccc6ddf13d679974183`
- part05: `55a45f5c2ca41b28bde6912fe3ab19aef0ac6cf09a7414625373a0ada0c5db7f`

## Physical gate

Use the plain files under:

`handoffs/chrome-photos-protected-surface-00/physical-gate/`

Expected SHA-256:

- `README.md`: `691b216d4796cfb3ebda33267d840bbe3b69bbc8a42c21f5e3b6900fba250d03`
- `detect_sentinel_frames.py`: `dd9147e1ab5a5a92b63a0ba27bce0a865ad1117195fad12eb7c76974ae3a2c6e`
- `sentinel-fixture.html`: `64838d8a763da6633d6eefeed9401c2e1b675d4295f28e3cc23e324d833c17ca`
- `synthetic-leak.json`: `81ee1c3a4292a38dd46f4fe346a001d5557f3386d5f1d72cbc71311f41d37cb5`
- `synthetic-pass.json`: `68eb61897a09e2797644696a42d9ae401071cdb0576baad156aed4f83e4aca95`

Codex must verify these hashes before using the artifacts, compare the reconstructed patch with the current canonical local repo and Glosh Central/Control Center, and port only the safe intent. Do not apply blindly if the local base has moved.
