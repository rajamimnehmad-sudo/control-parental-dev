# CHROME-H20-DYNAMIC-DOCUMENT-CPU-ATTRIBUTION-04

Date: 2026-09-01
Status: BLOCKED DIAGNOSTIC
Blocker: `H20_DYNAMIC_DOCUMENT_CPU_COMPONENT_UNRESOLVED`
Specific residual: `SUSTAINED_CPU_EVENT_NOT_REPRODUCED_AND_RENDERER_SNAPSHOT_NOT_OBSERVED`

## Immutable inputs

- Functional base/HEAD: `65b27d2ea2f9cb988b127518cd34d5217e5a038d`.
- Installed APK: DEV416 (`versionCode=416`), SHA-256
  `462500ae255e73394f726a73a4558322a9f5e0e716c3e094ec103cb79081bdc2`.
- H20, Byte Gate and GloshIA Visual R3.1 remained unchanged.
- Selective config remained `cache=64`, `concurrency=1`, `queue=2`,
  `timeout=5000 ms`.
- No source change, build, install, data deletion or bootstrap reset was made.
  Every observed status retained `resetCount=3`.

## Previous-session failure classification

The clean-rerun session ended with `failures=152`. The retained Logcat covers
149 phase records, or 98.0% of that counter. Classification used only existing
structured fields and hashed host identifiers; no clear URL or host was
persisted.

| Category | Count | Evidence |
| --- | ---: | --- |
| Client TLS handshake/EOF/cancel | 145 | `phase=tls_failed`, `side=client`, `stage=handshake`, `SSLHandshakeException` rooted in `EOFException` |
| Upstream TLS | 0 | No matching retained line |
| DNS | 1 | `phase=connection_failed`, `UnknownHostException` |
| Upstream abort before response | 3 | `phase=upstream_failed`, `responseStarted=false`, `errorResponseWritten=true`, `UnknownServiceException` |
| Upstream abort after response start | 0 | No matching retained line |
| Protocol/transform | 0 | No matching retained line |
| Fixture | 0 | No matching retained line |
| Fatal | 0 | No matching retained line |
| Not classifiable from retained Logcat | 3 | Counter-to-log coverage gap |

The classified sum is 149. The dominant client-TLS signature repeated across
multiple hashed-host groups; it was not inferred from the older COVERAGE-17
distribution.

For the new A/B sessions, the same classification yielded:

- A OFF: 63 failures = 60 client TLS, 1 DNS, 2 upstream-before-response;
- B ON: 46 failures = 42 client TLS, 1 DNS, 3 upstream-before-response.

No queue reject, timeout, proxy fatal or raw blocked/unknown delivery accompanied
these failure counters.

## Controlled same-document A/B

Both arms used the same public reproducer URL, portrait viewport, cold Chrome
process, newly created test-owned tab, two-minute stabilization and six identical
long swipes. The lab was stopped between arms. Chrome data and unknown persisted
tabs were not modified. The public page did serve visibly different live variants
between arms, so this is a same-URL and same-interaction comparison rather than a
byte-identical document replay.

### A — document self-shield OFF

- Control retained the current full tunnel/data plane and disabled only document
  self-shield (`stockMediaAuthority=false`, `documentSelfShield=false`).
- Formal window: 514 seconds after stabilization.
- Browser CPU: average `4.29%`, maximum `16.3%`.
- Active renderer CPU: average `9.75%`, maximum `20.3%`.
- Renderer maximum PSS/RSS: `221838 / 307120 KiB`
  (approximately `216.6 / 299.9 MiB`).
- Browser maximum PSS/RSS: `208249 / 328324 KiB`
  (approximately `203.4 / 320.6 MiB`).
- Minimum `MemAvailable`: `736056 KiB` (approximately `718.8 MiB`).
- Thermal status `0`; battery remained `100%`, approximately `24.4–24.7 C`.
- The active test-document renderer was PID `5387`. Closing only the positively
  identified test tab terminated it with `ISOLATED NOT NEEDED`, confirming the
  tab/PID binding.

The renderer alternated ordinary dynamic bursts with low samples; it did not
enter a sustained runaway state.

### B — H20 ON

- DEV416 ran with H20 and R3.1 selective enabled.
- Formal window: 512 seconds after stabilization.
- Browser CPU: average `3.75%`, maximum `15.7%`.
- Active renderer CPU: average `2.16%`, maximum `9.3%`.
- Renderer maximum PSS/RSS: `151980 / 233744 KiB`
  (approximately `148.4 / 228.3 MiB`).
- Browser maximum PSS/RSS: `207386 / 325060 KiB`
  (approximately `202.5 / 317.4 MiB`).
- Minimum `MemAvailable`: `968400 KiB` (approximately `945.7 MiB`).
- Thermal status `0`; battery remained `100%`, approximately `24.7–24.8 C`.
- The active test-document renderer was PID `8080`. Closing only the positively
  identified test tab terminated it with `ISOLATED NOT NEEDED`, confirming the
  tab/PID binding.

Relative to A, B renderer CPU was `0.22x` on average and `0.46x` at maximum,
with absolute deltas of `-7.59` and `-11.0` percentage points. Browser CPU was
`0.87x` on average (`-0.54` percentage points). B therefore did not reproduce
or amplify the earlier sustained approximately 190% renderer event.

## H20 status and renderer telemetry

Immediately before closing the B tab:

- requests/engine calls: `115 / 47`;
- cache hits/misses: `1 / 47`;
- inference/in-flight/queue peaks: `1 / 2 / 1`;
- queue rejects/timeouts: `0 / 0`;
- raw BLOCK/UNKNOWN: `0 / 0`;
- protect failures: `0`;
- QUIC/direct-TCP bypass: `0 / 0`;
- candidates / SAFE raw / UNKNOWN replaced / unsupported replaced:
  `76 / 48 / 22 / 6`;
- documents transformed: `1`;
- SELF_READY accepted/rejected: `1 / 0`;
- self-shield release/parser continuation: `1 / 1`;
- document transform outstanding: `0`.

The existing one-shot renderer report did not arrive. Before the close and for
more than 80 seconds after pagehide/STATUS requests, status remained
`reports=0`, `rejected=0`, with all 38 renderer counters at zero. Consequently
the attribution cannot claim low callback, attribute, child, scan, sanitize,
style/curtain, SVG/internal-mutation or Shadow DOM rates: those counters were
not observed for this document. `readyTokensOutstanding` remained `1` until the
last observable pre-STOP status, and the page close incremented document
fail-close state.

No new ANR, crash, OOM or LOW_MEMORY exit was observed in either arm.

## Diagnostic decision

The prior pathological event did not reproduce in A or B. The observed B arm
was lower in CPU and memory than A, so there is no evidence in this run for
`H20_DYNAMIC_DOCUMENT_CPU_AMPLIFICATION` or
`H20_SITE_INTERACTION_CPU_AMPLIFICATION`. The differing live responses and
non-reproduction also prevent a final `SITE_INTRINSIC_DYNAMIC_CPU_LOAD`
classification.

The correct terminal result is therefore:

`BLOCKED: H20_DYNAMIC_DOCUMENT_CPU_COMPONENT_UNRESOLVED`

with residual:

`SUSTAINED_CPU_EVENT_NOT_REPRODUCED_AND_RENDERER_SNAPSHOT_NOT_OBSERVED`.

Because H20 amplification was not demonstrated, the conditional second-B/A'
repetition, two additional public class pages and the renderer-amplification
fixture matrix were not run. No speculative fix was made and no APK was built.

## Stop and rollback verification

A final idempotent lab STOP and health query were initiated after the B tab was
closed. During that command ADB lost the device. The ADB daemon restarted, but
three subsequent checks and macOS USB inventory found no attached A23. Therefore
final rollback properties cannot be asserted from post-STOP evidence.

Last verified before the disconnect:

- exact test tab closed and renderer PID `8080` exited;
- raw BLOCK/UNKNOWN `0 / 0`;
- queue rejects/timeouts `0 / 0`;
- protect failure `0` and bypass `0 / 0`;
- document transform outstanding `0`;
- `resetCount=3`.

Unverified after the disconnect:

- terminal proxy/CA/cache removal and VPN restoration;
- transport `inactive/ready`, owned FD/protected sockets `0 / 0`;
- ready-token outstanding `0`;
- final Device Owner/Affiliated/Accessibility and inode preservation.

No destructive recovery or Chrome data operation was attempted. Physical
rollback verification requires the same device to reconnect.
