# CHROME-PHOTOS-PROTECTED-SURFACE-00 v2

Temporary Codex handoff branch. It is isolated from `main` and does NOT authorize PR, merge, publication, Production, destructive actions, or changes outside the ticket.

Prepared base SHA: `36b7c004f0f19a77439cd90c819b1195ee02cb49`

## IMPORTANT — canonical artifacts

Ignore ALL older transport attempts in this branch:

- `CHROME-PHOTOS-PROTECTED-SURFACE-00-v2.zip`
- `CHROME-PHOTOS-PROTECTED-SURFACE-00-v2.patch.gz.b64`
- `patch-parts/**`
- `canonical-v3/patch.gz.b64.part03`

Use only these verified files:

- `canonical-v3/patch.gz.b64.part01`
- `canonical-v3/patch.gz.b64.part02`
- `canonical-v3/patch.gz.b64.part03a`
- `canonical-v3/patch.gz.b64.part03b`
- `physical-gate/**`

## Remote verification

These four canonical transport files were verified against their local Git blob hashes after being committed to GitHub:

- part01 Git blob SHA-1: `a564bd61292cf0d09f9cc5135ef9855671e92a6c`
- part02 Git blob SHA-1: `983de9096f2a365130fb5e71eae5083e51ae23c4`
- part03a Git blob SHA-1: `88e8ba1c78d099c41369dba0736486ba9efd1df4`
- part03b Git blob SHA-1: `703d908f10e18c50fdd4717201be8d84ff3eb4d8`

## Reconstruct the canonical patch

From repository root:

```bash
python3 - <<'PY'
from pathlib import Path
import base64, gzip, hashlib

root = Path('handoffs/chrome-photos-protected-surface-00/canonical-v3')
parts = [
    root / 'patch.gz.b64.part01',
    root / 'patch.gz.b64.part02',
    root / 'patch.gz.b64.part03a',
    root / 'patch.gz.b64.part03b',
]
encoded = b''.join(p.read_bytes() for p in parts)
print('transport_sha256=', hashlib.sha256(encoded).hexdigest())
patch = gzip.decompress(base64.b64decode(encoded))
dst = Path('/tmp/CHROME-PHOTOS-PROTECTED-SURFACE-00-v2.patch')
dst.write_bytes(patch)
print('patch_sha256=', hashlib.sha256(patch).hexdigest())
print('patch_bytes=', len(patch))
print('written=', dst)
PY
```

Expected values:

- concatenated transport SHA-256: `860fb889a5183e67b8f32b9b2c99cb1abc2a2c7df28be57cc8534aab8ef62c45`
- reconstructed patch SHA-256: `b497f9a0e84b5362d2d13bf529e90ecf5a1ca77e73dfc69e7416373935b08ef6`
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
