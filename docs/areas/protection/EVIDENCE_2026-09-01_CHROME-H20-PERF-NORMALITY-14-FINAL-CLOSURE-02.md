# CHROME-H20-PERF-NORMALITY-14-FINAL-CLOSURE-02

Date: 2026-09-01
Status: BLOCKED PHYSICAL — PENDING TRIAGE

## Scope and immutable inputs

- Functional base/HEAD: `65b27d2ea2f9cb988b127518cd34d5217e5a038d`.
- A23 installed APK: DEV416 (`versionCode=416`), SHA-256
  `462500ae255e73394f726a73a4558322a9f5e0e716c3e094ec103cb79081bdc2`.
- The installed APK matched the reviewed 14C artifact and was reused. There was
  no rebuild, reinstall, data deletion or bootstrap reset.
- R3.1, model, thresholds, Byte Gate, H20 and selective configuration remained
  unchanged (`cache=64`, `concurrency=1`, `queue=2`, `timeout=5000 ms`).
- Chrome bootstrap `resetCount` remained `3`.

## Automated validation

PASS on the exact functional HEAD:

- H20 renderer-amplification and media-shield fixtures;
- bootstrap, transformer, SELF_READY, parser, curtain and tamper coverage;
- global/Shadow DOM observers, SVG and DOM mutation guards;
- Service Worker gates;
- `ChromeNetworkVisualDeliveryGateTest`;
- Chrome decision session/engine and resource transformer tests;
- `GloshiaVisualParityTest`;
- `git diff --check`.

The unit suites completed successfully after supplying the Android SDK path to
the isolated worktree. No source changed, so no artificial rebuild or lint/build
cycle was performed.

## Controlled selective semantics

The controlled delivery checks preserved the approved contract:

- SAFE: `model_allow`, original bytes, `safeRawDelivered > 0`;
- BLOCK: `model_filter`, placeholder, `blockedReplaced > 0`;
- UNKNOWN and unsupported: placeholder/fail-close;
- raw BLOCK/UNKNOWN: `0 / 0` throughout;
- queue rejects/timeouts: `0 / 0`;
- protect failures: `0`;
- QUIC/direct-TCP bypass: `0 / 0`.

Cache reuse was observed for identical SAFE and BLOCK digests: verdict and
probability were unchanged, source changed from `engine` to `cache`, cached
`inferenceMs=0`, and cache hits increased. The H19 `same-url` asset intentionally
alternates its body and therefore was not treated as an identical-digest cache
assertion.

The later normal selective fixture snapshot reported:

- candidates: `15`;
- SAFE raw / BLOCK replaced / UNKNOWN replaced / unsupported replaced:
  `8 / 1 / 1 / 5`;
- raw BLOCK/UNKNOWN: `0 / 0`;
- engine calls/cache hits/evictions: `8 / 2 / 0`;
- inference/in-flight/queue peaks: `1 / 1 / 1`;
- queue rejects/timeouts: `0 / 0`;
- model load: `419.144 ms`;
- preprocess p50/p95/p99: `13.596 / 64.443 / 64.443 ms`;
- inference p50/p95/p99: `132.237 / 163.803 / 163.803 ms`;
- decision p50/p95/p99: `155.719 / 228.694 / 228.694 ms`;
- proxy p50/p95/p99: `0.610 / 74.999 / 752.915 ms`;
- cache-hit p50/p95: `0.010 / 0.010 ms`.

## Physical blocker and early stop

The first physical navigation mistakenly used
`/web19/controlled`. That page is the hostile H19 architecture/security matrix,
not the approved selective performance fixture. It concurrently exercises
prototype tampering, DOM/iframe/Shadow/graphics probes and never completed its
H19 report in this run.

The required early-stop signature then appeared:

- renderer PSS maxima sampled: approximately `593 MiB` and `576 MiB`;
- corresponding renderer RSS: approximately `460 MiB` and `468 MiB`;
- Chrome browser CPU: approximately `221%`;
- renderer CPU: approximately `181% / 160% / 154%`;
- `kswapd` CPU: approximately `36%`;
- `MemAvailable` minimum: approximately `139–152 MiB`;
- LMKD reclaimed unrelated cached applications at the low watermark.

The proxy was stopped immediately rather than waiting for an ANR. Android's
subsequent `ApplicationExitInfo` confirmed two new LOW_MEMORY events in that
interval:

- Chrome browser at `08:15:22`, `pss/rss=546/584 MiB`;
- renderer at `08:15:17`, `pss/rss=119/134 MiB`.

There was no ANR since boot, but the new renderer LOW_MEMORY event alone violates
the closure gate.

After recovery, the correct root selective fixture remained within a healthy
memory envelope (renderer RSS maximum approximately `213 MiB`,
`MemAvailable` approximately `1.13 GiB`). CPU and request activity nevertheless
remained high: at about 90 seconds, renderer/browser CPU was approximately
`141% / 90%`, while requests rose to `414` with no user interaction. Logs showed
an H19 controlled document/frame restored alongside the correct fixture, so this
probe was not an uncontaminated normality run.

Chrome contained 15 persisted tabs. The H19 tab created during this ticket could
not be distinguished with enough certainty from existing browser state while
Chrome was being suspended/fail-closed. No tabs or Chrome data were deleted.
Because the contamination could not be removed safely without risking unknown
state, no 30-minute gate or real-web Google/Fravega/Mimo interaction sequence was
claimed.

This result does not demonstrate a regression in the reviewed 14C target-only
fix under the approved normal workload. It does demonstrate that this execution
cannot certify final performance/normality and must not be marked PASS.

## Rollback and preserved health

- Manual STOP completed; proxy and cache cleared and CA removed.
- Lab transport: `inactive`, `ownedFdResources=0`,
  `activeProtectedUdpSockets=0`, runtime `ready`.
- VPN attestation was revoked and normal VPN routes were restored.
- `documentTransformOutstanding=0`, `readyTokensOutstanding=0`.
- Device Owner and the Glosh Accessibility service remained present.
- Chrome CE inode `6090` and Glosh CE inode `1239519` were preserved.
- `resetCount=3`; no application data was cleared.
- Terminal thermal status: `0`; battery remained `100%`, about `24.8 C`. This
  abbreviated/contaminated run provides no battery certification.

## Residual and required rerun condition

The 30-minute closure remains pending. A rerun needs an uncontaminated Chrome
session in which only test-owned H19 tabs are removed through an explicitly safe
and auditable method, without clearing Chrome data or altering `resetCount`.
Then the approved root selective fixture and real-web sequence must run for the
full 30 minutes on DEV416 (or a later fully revalidated artifact).
