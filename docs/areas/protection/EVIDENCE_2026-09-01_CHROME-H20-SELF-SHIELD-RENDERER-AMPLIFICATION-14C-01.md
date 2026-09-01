# CHROME-H20-SELF-SHIELD-RENDERER-AMPLIFICATION-14C-01

Date: 2026-09-01  
Status: PASS TECHNICAL/PHYSICAL — PENDING CHATGPT FINAL REVIEW

## Scope and immutable inputs

- Base: `c7d40a11f8f198a767514b48a538272296f3bfa9`.
- Functional HEAD: `65b27d2ea2f9cb988b127518cd34d5217e5a038d`.
- A23 final APK: DEV416, SHA-256
  `462500ae255e73394f726a73a4558322a9f5e0e716c3e094ec103cb79081bdc2`.
- R3.1, model, thresholds, Byte Gate and selective configuration remained
  unchanged (`cache=64`, `concurrency=1`, `queue=2`, `timeout=5000 ms`).
- Chrome data was not cleared. Trusted bootstrap `resetCount` remained `3`.

## Causal diagnosis

The global and per-ShadowRoot `MutationObserver` paths handled an `attributes`
record by calling both `sanitizeContainer(target)` and `scan(target)`. The latter
rescanned the complete descendant subtree even though the record referred to a
single target. H20 DOM guards and the observer then duplicated work; protected
SVG/style writes could feed more watched records into the same expensive path.

The controlled ATTR run on the old path produced, for one document:

- callbacks/records: `9 / 2172`;
- child/attribute records observed before callback abort: `16 / 2`;
- max records in one callback: `839`;
- completed attribute subtree scans: `1`;
- sanitize element/container: `175 / 162`.

A pre-fix diagnostic renderer was also recorded by Android as LOW_MEMORY at
`01:23:42`, `pss/rss=1.6 GB`, consistent with the 14B amplification and before
the final DEV416 stability gate.

## Fix

Attribute records are now target-only in both observers:

1. sanitize the exact target;
2. revalidate its closest SVG root when applicable;
3. do not traverse its descendants.

`childList` behavior is unchanged: every newly added node/subtree is scanned.
The existing DOM guards, data/blob restrictions, iframe/SVG protection,
protected-style integrity and fail-close behavior remain in force. Scan metrics
now count attempts at entry, including work that does not complete.

## Controlled final result

DEV416, same ATTR workload:

- callbacks/records: `9 / 2172`;
- child/attribute records: `16 / 161` (all site attribute changes processed);
- max records in one callback: `1233`;
- scan calls/root scans: `14 / 1`;
- observer child/attribute scans: `13 / 0`;
- scanned nodes/max per scan: `9 / 1`;
- scan total/max: `1694 / 799 us`;
- sanitize element/container: `330 / 321`;
- SELF_READY/release/parser/original script: `1 / 1 / 1 / 1`;
- health failures/rejects/timeouts/protect failure: `0 / 0 / 0 / 0`;
- raw BLOCK/UNKNOWN: `0 / 0`.

This isolates the reduction to removal of attribute subtree scans rather than
to model, cache, concurrency or timeout tuning.

## Physical stability and normality

The final A23 session exercised the controlled fixture, Fravega, Mimo, Google
Images without a blocked query, scroll, back/forward, multiple tabs and
portrait/landscape/portrait. Sampling ran from `01:33:54` through `01:51:53`
(18 minutes, exceeding the required 15 minutes).

- new Chrome ANR: `0` (`lastanr`: none since boot);
- new LOW_MEMORY renderer exits during the final interval: `0`;
- maximum renderer RSS sampled: approximately `344 MiB`;
- terminal renderer PSS/RSS: approximately `30–47 MiB / 120–144 MiB`;
- minimum `MemAvailable`: approximately `806 MiB`;
- terminal `MemAvailable`: approximately `978 MiB`;
- sustained CPU runaway: not observed;
- thermal status: `0`, battery `25.6 C`, skin `26.3 C`;
- proxy queue rejects/timeouts: `0 / 0`;
- protect failures: `0`;
- QUIC/direct-TCP bypass: `0 / 0`;
- raw BLOCK/UNKNOWN: `0 / 0`.

Reference comparison:

| Run | Renderer maximum | Minimum MemAvailable | ANR |
|---|---:|---:|---:|
| H20 OFF (14B) | ~323 MiB | ~700 MiB | 0 |
| H20 old (14B) | ~859 MiB | ~127 MiB | 1 |
| H20 fixed DEV416 | ~344 MiB | ~806 MiB | 0 |

Renderer exits observed during navigation were `ISOLATED NOT NEEDED`, not
LOW_MEMORY. Fravega, Mimo and Google navigation remained usable; SAFE resources
were delivered, while BLOCK/UNKNOWN remained replaced. A clean terminal
controlled session also confirmed `failures=0`, current SELF_READY/parser state
and target-only counters.

## Automated validation

PASS:

- renderer metrics and amplification fixture tests;
- H20 bootstrap/transformer/SELF_READY/parser/curtain and tamper tests;
- DOM mutation, SVG and Shadow DOM guards;
- Service Worker gates;
- NetworkVisualDeliveryGate;
- Chrome decision session/engine;
- GloshiaVisualParity;
- focused DEV/testDEV ktlint;
- `compileDevDebugKotlin`, `lintDevDebug`, `assembleDevDebug`;
- `git diff --check`.

The root aggregate ktlint task still reports unrelated pre-existing source-set
violations outside this ticket; the affected app-user DEV/testDEV source sets
pass and no unrelated file was changed.

## Ownership, rollback and residuals

- Device Owner, Affiliated state and Accessibility service remained present.
- Glosh CE inode `1239519` and Chrome CE inode `6090` were preserved.
- No data reset or deletion occurred; `resetCount=3`.
- STOP completed: proxy cleared, CA removed, cache cleared, VPN routes restored,
  attestation revoked and Chrome returned to the protected/fail-close state.
- No R3.1 verdict semantics changed.

Residual: this closes the pathological renderer amplification and re-enables
the separate perf/normality closure. It does not itself declare Product Ready.
