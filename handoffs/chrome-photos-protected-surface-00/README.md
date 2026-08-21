# CHROME-PHOTOS-PROTECTED-SURFACE-00 v2

Temporary Codex handoff branch. It is isolated from `main` and does NOT authorize PR, merge, publication, Production, destructive actions, or changes outside the ticket.

Prepared base SHA: `36b7c004f0f19a77439cd90c819b1195ee02cb49`

## IMPORTANT — canonical artifacts

Ignore these older artifacts in this branch:

- `CHROME-PHOTOS-PROTECTED-SURFACE-00-v2.zip`: its first GitHub upload was truncated.
- `patch-parts/**`: intermediate transport attempt; non-canonical and MUST NOT be used.

Use only:

1. `CHROME-PHOTOS-PROTECTED-SURFACE-00-v2.patch.gz.b64`
2. `physical-gate/**`

## Reconstruct the canonical patch

From the repository root, use Python so the command behaves the same on macOS and Linux:

```bash
python3 - <<'PY'
from pathlib import Path
import base64, gzip, hashlib

src = Path('handoffs/chrome-photos-protected-surface-00/CHROME-PHOTOS-PROTECTED-SURFACE-00-v2.patch.gz.b64')
dst = Path('/tmp/CHROME-PHOTOS-PROTECTED-SURFACE-00-v2.patch')
encoded = src.read_bytes()
print('transport_sha256=', hashlib.sha256(encoded).hexdigest())
patch = gzip.decompress(base64.b64decode(encoded))
dst.write_bytes(patch)
print('patch_sha256=', hashlib.sha256(patch).hexdigest())
print('patch_bytes=', len(patch))
print('written=', dst)
PY
```

Expected SHA-256 values:

- canonical base64 transport file: `e21526c7c6d371b31b3e5ea2733cc4b62d406cc90ec0a0adb489f9044c639732`
- reconstructed patch: `b497f9a0e84b5362d2d13bf529e90ecf5a1ca77e73dfc69e7416373935b08ef6`
- reconstructed patch size: `62400` bytes

If any value differs, STOP without modifying code.

## Physical gate

Use the plain files under:

`handoffs/chrome-photos-protected-surface-00/physical-gate/`

Expected SHA-256:

- `README.md`: `691b216d4796cfb3ebda33267d840bbe3b69bbc8a42c21f5e3b6900fba250d03`
- `detect_sentinel_frames.py`: `dd9147e1ab5a5a92b63a0ba27bce0a865ad1117195fad12eb7c76974ae3a2c6e`
- `sentinel-fixture.html`: `64838d8a763da6633d6eefeed9401c2e1b675d4295f28e3cc23e324d833c17ca`
- `synthetic-leak.json`: `81ee1c3a4292a38dd46f4fe346a001d5557f3386d5f1d72cbc71311f41d37cb5`
- `synthetic-pass.json`: `68eb61897a09e2797644696a42d9ae401071cdb0576baad156aed4f83e4aca95`

## Codex execution rule

Before applying anything, compare the reconstructed patch with the CURRENT canonical local repository and the CURRENT Glosh Central / Control Center. The prepared SHA is context, not permission to overwrite newer local work.

Port only the safe intent when the local base has moved. Respect the current owner, allowed paths, dependencies, and concurrent work. If there is a real collision, stop and report it.

This ticket remains photos/protected-surface only: no DAG, video, DRM, model/threshold changes, PR, push, merge, publication or Production.
