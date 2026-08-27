# MAC-SAFE-CLEANUP-01

Status: **DONE / SAFE REGENERABLE CLEANUP — PENDING CHATGPT REVIEW**

Base: `review/glosh-convergence-baseline-01` at
`c2900bc5a2f28fa9d291862197f70a2e2c8e2f6c`.

## Deleted categories

| Category | Exact paths | Logical allocated bytes removed |
| --- | ---: | ---: |
| Gradle / Android `build`, project `.gradle` and isolated subproject caches | 416 | 19,170,480,128 |
| Web `node_modules` / `.next` with lockfile recipe | 2 | 930,750,464 |
| Venvs | 0 | 0 |
| Other | 0 | 0 |
| **Total** | **418** | **20,101,230,592** |

APFS free space changed from `13,073,334,272` bytes to `30,402,654,208`
bytes: **17,329,319,936 bytes actually freed**. The difference from the logical
path total is shared/COW filesystem accounting, not an omitted deletion.

The exact private allowlists and execution logs are under
`~/Glosh-Cleanup-Metadata/MAC-SAFE-CLEANUP-01/`. Both executions completed with
zero skipped and zero failed targets; all 418 deleted paths were absent after
the operation.

## Explicitly protected

- `~/Glosh-Preservation/MAC-LOCAL-PRESERVATION-03/` and every bundle/manifest.
- Every `.codex-tmp`, including the original 5,810,292 KiB tree and the
  1,386,224 KiB DAG-worktree tree discovered during the cleanup.
- The dirty historical checkout, its 8 tracked modifications, 6 untracked
  files, preserved APKs and 11 ignored sensitive/ambiguous files.
- `.git`, 59 preserve refs, 68 local branches, 59 worktree records and all 34
  existing worktree directories.
- Datasets, corpus, labels, reviews, calibrations, models, tensors, crops,
  screenshots, videos, evidence, benchmarks and unique scripts.
- Two external venvs (66,936,832 bytes) without explicit preservation
  classification.

## Post-clean validation

- Baseline remote remains `c2900bc5a2f28fa9d291862197f70a2e2c8e2f6c`.
- Preserve remote remains `f6e32dab2da6e2faf5f56f7e9bd0bb694565a592`.
- Bundle SHA-256 remains
  `75d6cddb882ce1bf45fa81e13efd54feeaa8764a4fd0ab99f98c31e0af7c612a`;
  `git bundle verify` passed.
- All 19 indexed preservation manifests match their recorded SHA-256.
- Preservation root size, inode, path count and path-list hash are unchanged.
- Original status count/hash, unstaged binary diff, empty staged diff and
  untracked-content hash are unchanged.
- Branch, preserve-ref and worktree counts/maps are unchanged.
- Original `.codex-tmp` size, inode, 49,394-path list and path-list hash are
  unchanged; 31/31 preservation samples match by content hash.
- No tracked/untracked work, worktree, branch, ref, GitHub branch or PR was
  deleted. No build/test was run because product code did not change.

## Possible second pass — not authorized here

- Existing non-original worktree directories occupy 2,402,959,360 bytes after
  this cleanup. Of that, 1,419,493,376 bytes are the protected DAG-worktree
  `.codex-tmp`; it was not covered by the original root `.codex-tmp` manifest
  and needs separate preservation/classification before any worktree removal.
- The remaining gross worktree footprint excluding that protected tree is
  983,465,984 bytes. Removal still requires a separate branch/worktree ticket.
- The dirty original retains 3,921,580,032 bytes of build/cache candidates and
  its web dependencies by explicit exclusion. It must not be cleaned without a
  later authorization tailored to the dirty checkout and preserved APKs.
- The 25 missing worktree registrations remain unpruned.

No second cleanup pass is authorized by this result.
