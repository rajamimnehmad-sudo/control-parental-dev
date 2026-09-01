# CHROME-H20-RENDERER-METRICS-LIVENESS-04A

Date: 2026-09-01
Status: PASS TECHNICAL/PHYSICAL / PENDING CHATGPT FINAL REVIEW
Result: `RENDERER_METRICS_LIVENESS_PASS`

## Inputs and scope

- Functional base: `65b27d2ea2f9cb988b127518cd34d5217e5a038d`.
- Previous evidence review: `ee096a3d19dd4536f69f2bae8f33fd909c1cd682`.
- Initial installed artifact: DEV416, SHA-256
  `462500ae255e73394f726a73a4558322a9f5e0e716c3e094ec103cb79081bdc2`.
- Final functional commit: `9cd29a7ddb141874d0ae45a49fc920e73a18a348`.
- Final installed artifact: DEV417, SHA-256
  `4fc837a2ba17b9c2b6c3919561c4239b6715cb93e75e88c291c36a3a14e22d0f`.
- The signer SHA-256 remained
  `d51bc0dabd280ce1b0f098ae168eb57758faeba301156cde835737835f8a8832`.
- H20, Byte Gate, GloshIA Visual R3.1, model weights, thresholds and selective
  config remained unchanged (`cache=64`, `concurrency=1`, `queue=2`,
  `timeout=5000 ms`).
- No Chrome data deletion, trusted-bootstrap reset, PR, merge or deployment was
  performed. `resetCount` remained `3`.

## A23 recovery and pre-fix health

ADB recovered the SM-A235M as serial `R58T34V31AE`. An idempotent STOP verified:

- proxy cleared, lab CA removed and VPN restored;
- transport `inactive`, runtime `ready`;
- owned FD and protected UDP sockets `0 / 0`;
- document and ready-token outstanding `0 / 0`;
- DEV416 and its installed hash matched the expected artifact;
- Glosh/Chrome data inodes `1239519 / 6090`;
- Device Owner, empty affiliation set and the expected Accessibility service
  remained installed, enabled and bound;
- global proxy and always-on/lockdown VPN settings were `null`;
- `resetCount=3`.

## Root cause and fix

`ChromeMediaShieldRendererMetricsScript.reporting` previously set
`rendererMetricsSent=true` before synchronous XHR delivery was attempted. A
lifecycle failure therefore converted an unaccepted attempt into a permanent
one-shot state.

The minimal generic fix:

- leaves `rendererMetricsSent` false while the request is attempted;
- sets it true only after the native response URL matches and status is exactly
  `204`;
- retains false on exception or any non-accepted response, allowing retry;
- adds a `visibilitychange` listener that attempts delivery when the document
  becomes hidden;
- retains the explicit `glosh-h20-renderer-metrics-snapshot` trigger and the
  `pagehide` fallback;
- leaves server-side replay rejection and all security decisions unchanged.

DEV417 is the only new artifact and was installed update-in-place.

## Automated validation

Focused unit execution passed for:

- `ChromeMediaShieldBootstrapContractTest`;
- `ChromeMediaShieldReadyEndpointTest`;
- `ChromeMediaShieldRendererMetricsTest`.

The new coverage proves:

1. a failed first send remains retryable;
2. an accepted native `204` seals the one-shot;
3. `visibilitychange` hidden triggers a snapshot;
4. explicit snapshot and `pagehide` remain available;
5. endpoint replay remains rejected by the existing duplicate test;
6. invalid token and document identity remain rejected;
7. the existing bootstrap contract suite retains SELF_READY, release, parser
   continuation and Byte Gate behavior.

`compileDevDebugKotlin`, `lintDevDebug` and `assembleDevDebug` all passed. The
final APK was reassembled from the committed HEAD. `git diff --check` passed.
The source-set-wide ktlint tasks still report only pre-existing violations in
unmodified files; no changed file is present in the final ktlint report.

## Physical renderer-metrics gate

DEV417 started H20 selective with `stockMediaAuthority=true` and
`documentSelfShield=true`. The normal H20 fixture was used; `/web19/controlled`
was not opened.

An initial post-update Accessibility rebind caused the documented fail-close
and Chrome suspension. A later UIAutomator inspection also temporarily detached
Accessibility on this Samsung and correctly triggered the same fail-close.
Accessibility returned enabled/bound each time, and an idempotent STOP/START
restored the lab without reset or data mutation. UIAutomator was not used again
while the lab was active; these interrupted sessions were excluded from the
physical result.

In the clean metrics session:

- fixture documents transformed: `3`;
- SELF_READY accepted/rejected: `3 / 0`;
- release/parser continuation: `3 / 3`;
- visibility-driven tab changes produced accepted reports;
- a separate positively identified test document was closed through normal
  Chrome UI, exercising the close/pagehide path;
- final renderer reports/rejected: `3 / 0`;
- counters were non-zero, including `globalCallbacks=18`,
  `globalRecords=537`, `childRecords=27`, `attributeRecords=3`,
  `scanCalls=21`, `scanNodes=9`, `sanitizeElement=12`,
  `ensureStyle=21` and `ensureCurtain=27`.

This closes the prior `reports=0` liveness residual. Renderer telemetry remains
diagnostic only and is not used for any security decision.

## Short dynamic-page CPU check

After metrics passed, a clean H20 selective session exercised Mimo for about six
minutes with three long scrolls and twelve samples approximately 30 seconds
apart.

- active renderer PID: `13447`;
- renderer RSS range: approximately `175–203 MiB`;
- renderer CPU: stable at approximately `4%` in the sampled `top` snapshots;
- browser RSS range: approximately `248–262 MiB`;
- browser CPU: approximately `0–3%`;
- minimum `MemAvailable`: `728548 KiB` (approximately `711.5 MiB`);
- thermal status: `0` throughout;
- no new ANR, crash, OOM or LOW_MEMORY exit;
- no sustained CPU or memory runaway.

Closing the positively identified Mimo test tab produced an accepted renderer
snapshot:

- reports/rejected: `1 / 0`;
- global callbacks/records: `101 / 895`;
- child/attribute records: `255 / 21`;
- scan calls/nodes/micros: `904 / 1506 / 29954`;
- sanitize element/container: `5127 / 2736`;
- style/curtain: `102 / 104`;
- SVG records/internal mutations: `0 / 0`;
- Shadow callbacks/records/scans: `0 / 0 / 0`.

The previously observed CPU outlier did not recur, so no further CPU attribution
or tuning was performed.

The final pre-STOP status retained:

- raw BLOCK/UNKNOWN delivery: `0 / 0`;
- queue rejects/timeouts: `0 / 0`;
- protect failure: `0`;
- QUIC/direct-TCP bypass: `0 / 0`;
- document/ready outstanding: `0 / 0`;
- SELF_READY accepted/rejected: `1 / 0`;
- renderer reports/rejected: `1 / 0`.

All `32 / 32` session failures were `phase=tls_failed`, `side=client`,
`stage=handshake`, with `SSLHandshakeException` rooted in `EOFException`,
produced while replacing/closing test-owned tabs. No upstream, DNS, fixture or
proxy-fatal record—and no queue reject, timeout, protect failure, bypass or raw
blocked/unknown delivery—accompanied them.

## Final STOP and rollback

Final STOP completed with:

- proxy cleanup complete, cache entries `0`;
- proxy cleared, lab CA removed and VPN restored;
- transport `inactive`, runtime `ready`;
- owned FD/protected UDP sockets `0 / 0`;
- document/ready outstanding `0 / 0`;
- global proxy and always-on/lockdown VPN settings `null`;
- Device Owner, affiliation and Accessibility preserved;
- Glosh/Chrome data inodes preserved at `1239519 / 6090`;
- Chrome suspended under the inactive fail-close guard;
- installed DEV417 hash verified as
  `4fc837a2ba17b9c2b6c3919561c4239b6715cb93e75e88c291c36a3a14e22d0f`;
- `resetCount=3`.

No physical residual remains for renderer-metrics liveness.
