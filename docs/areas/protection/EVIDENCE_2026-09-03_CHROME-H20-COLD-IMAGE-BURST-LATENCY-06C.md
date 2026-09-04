# Chrome H20 cold image burst latency 06C

## Scope and anchors

- Task: `chrome-h20-cold-image-burst-latency-06c`.
- Functional base: `76f285be92945857e5de89423f5df7edf54fc7a9`.
- Governance SHA fixed for the batch: `9323b126dde3b91e21e2136a11d087fa3c1f0ca1`.
- Last committed implementation before this batch: `15f43d628037c1158e741bcc97a4f7c3a746eba5`.
- Scope stayed inside the Chrome Photos DEV data plane, its focused tests, the DEV version, and this evidence. No model, weights, labels, thresholds, Byte Gate semantics, or release authority changed.

## Root cause and fix

The A23 cold-burst observation was a bounded scheduling problem, not a raw-media leak or model-quality change. Chrome maintains many idle HTTP/1.1 CONNECT tunnels while loading a multi-origin page. The previous eight-worker proxy could let idle tunnels occupy workers until the socket timeout, delaying fresh image origins. The DEV proxy now admits a bounded 64-worker/64-queue fan-out with a five-second idle socket timeout. Image-body admission is bounded at two bodies per inference worker (four with the default two workers), preserving backpressure.

The default decision profile remains bounded at `256 entries / 2 concurrent inferences / 4 queue entries / 5000 ms`. The generic blocked fixture is now a fixed neutral blur-style placeholder without product text; it never contains original blocked bytes. The fail-closed curtain is white and no longer displays a per-navigation “Analizando…” message, keeping the normal Chrome surface usable while authority is pending.

The follow-up DEV432 optimization bounds the OkHttp upstream dispatcher at `128` total requests and `32` per host. This removes the default five-request-per-host serialization that was visible behind the H20 HTTP/1.1 connection boundary while retaining bounded admission and normal HTTP/2 multiplexing upstream. No client-side HTTP/2 MITM, fail-close, cache, or media-authority semantics changed.

The Mimo/Coto icon report exposed a generic SVG compatibility false positive: real UI SVGs contain bounded inert `<style>` rules and framework `data-*` metadata. The original-ui validator and the dynamic `safeIcon` path now admit only those bounded class/id rules and inert metadata, while continuing to reject scripts, external URLs, nested raster/media, active CSS, and Glosh control metadata. Authorized SVG bytes are not reconstructed or restyled; rejected inputs remain fail-closed. The dynamic guard has the same conservative grammar, so this is not a host- or framework-specific workaround.

Mimo then exposed the corresponding sprite case used by its hamburger/search UI: an invisible `0×0` SVG containing ID-bearing vector groups under `<defs>`, referenced by ordinary `<use href="#...">` elements. The generic guard now admits only that bounded sprite shape and only references to a validated `<g>`/`<symbol>` inside the sprite's `<defs>`. The original `<use>`, paths, fills, geometry, and interactions remain untouched.

## Automated validation

- Focused `ChromePhotoDecisionSessionTest`, `ChromeImageContentAuthorityTest`, and `ChromePhotosHttpsProxyConnectionTest`: PASS.
- `:app-user:compileDevDebugKotlin`: PASS.
- `:app-user:lintDevDebug`: PASS.
- `:app-user:assembleDevDebug`: PASS.
- `git diff --check`: PASS.
- DEV432 focused `ChromePhotosRealUpstreamTest`: PASS (dispatcher limits asserted).
- DEV435 focused SVG authority and bootstrap contract tests: PASS (bounded inert style rules, framework metadata, and dynamic guard parity).
- DEV436 bootstrap contract tests and compile/lint/assemble validation: PASS (bounded `<defs>` sprite handling).

## APK and update-in-place

- DEV436, `versionName=1.0.1-dev`.
- APK SHA-256: `d967cd99dae8f29a43ed637dedea65150d01c258dfc097f335e9cf51eeb65ec6`.
- `adb install -r`: PASS; app data inode remained `1239519`.
- Chrome data/cache was not cleared; bootstrap `resetCount=3` remained unchanged.
- Device Owner and Chrome-only protection state were restored after measurement: `active=true`, `ready=true`, `stockMediaAuthority=true`, `documentSelfShield=true`.

## Physical observations

Device: Samsung A23 `SM-A235M`, Android 14/API 34, official Chrome.

With DEV429/DEV431 protection ON, fresh public pages remained usable and preserved original UI. Google Images showed first visible photos at approximately four seconds and a filled grid at approximately six seconds. Frávega preserved navbar, search, filters, product text and controls; product photos appeared at approximately eight seconds. Representative cumulative ON counters remained `raw BLOCK/UNKNOWN=0/0`, `queue rejects/timeouts=0/0`, `protectFailure=0`, and `bypass=0/0`; inference p95 was approximately 230–255 ms, while proxy p95 was approximately 913–924 ms with peak active tunnels 17–47/64.

With fresh DEV432 and no screenshots or UI-hierarchy instrumentation, Google Images measured proxy p50/p95/p99 `206/435/437 ms` at 13 peak connections; inference p95 was `186 ms`, decision p95 `206 ms`, and the first ten-second sample had `27` requests with `0` queue rejects/timeouts. Frávega measured proxy p50/p95/p99 `252/796/1179 ms` at 34 peak active tunnels and 60 connections; one cancelled upstream request was classified as an `InterruptedIOException` with no response started, while queue rejects/timeouts, raw BLOCK/UNKNOWN, protect failures, and bypass counters stayed zero. The dispatcher reduced the Google p95 relative to the prior ~913–924 ms observation, while Frávega remains fan-out/network-bound.

An idle 30-second device sanity sample while protection remained active showed stable app memory (PSS approximately `168 → 155 MiB`, RSS `232 → 221 MiB`), Chrome at approximately `0.7%` cumulative CPU in the sampled view, no sustained guard-process growth, battery temperature `24.8°C`, and no new proxy failures or queue/reject counters. This is a bounded sanity check, not a battery A/B result.

On DEV435, fresh no-screenshot navigations to Mimo and Coto preserved the protected Chrome session and produced `uiSvgNetworkRejected=0` with `uiSvgNetworkAccepted=7` (Mimo) and `10` cumulative (after Coto). The same snapshots showed `proxyQueueRejects=0`, `queueRejects=0`, `timeouts=0`, `protectFailure=0`, `quicAttempts=0`, and `directTcpAttempts=0`; Mimo proxy p95 was `901.793 ms`, Coto-inclusive p95 `902.268 ms`. These are timing/status observations only; no ADB screenshot or UI-hierarchy capture was used.

On DEV436, a fresh Mimo navigation after the sprite fix produced `uiSvgNetworkAccepted=8`, `uiSvgNetworkRejected=0`, proxy p50/p95/p99 `202.726/742.110/944.242 ms`, and inference p95 `255.427 ms`. The snapshot retained `raw BLOCK/UNKNOWN=0/0`, `proxyQueueRejects=0`, `queueRejects=0`, `timeouts=0`, `protectFailure=0`, `quicAttempts=0`, and `directTcpAttempts=0`. Visual confirmation is now requested from the user on the live Mimo tab; repeated screenshots remain excluded because they invoke the protected proof surface.

Fresh classification run (DEV436, no screenshots): Mimo photo responses observed in the run were HTTP `200 image/png` with `decision=safe`, `reason=model_allow`, and original bytes delivered; no new Mimo `block` or `unknown` decision was emitted. Frávega emitted many HTTP `200 image/webp`/`image/png` `safe/model_allow` responses with original bytes, plus one intentional `block/model_filter` replacement. Its other missing-looking assets were tiny `42–43` byte payloads announced as `image/png` but sniffed as unsupported GIF (`unknown/unsupported_gif`), and one empty payload (`unknown/image_format_unknown`): Gloshia correctly fail-closed these rather than treating malformed/unsupported media as safe. A separate `UnknownHostException` and an HTTP `403 application/xml` response are upstream/network failures, not Gloshia policy blocks. This distinguishes expected filter replacements from external delivery errors without weakening the contract.

## OFF control and instrumentation caveats

A temporary DEV-only baseline lease was used only in the separate 06B harness APK and was revoked/expired before DEV431 was installed. It was not included in this functional history. In the OFF control, Google Images showed photos around two seconds; Frávega showed normal UI around four seconds and product photos around six seconds. This bounds the remaining gap to proxy/network/render scheduling rather than R3.1 inference.

Repeated ADB screenshots are not valid latency evidence: `ChromePhotosProtectedSurface` intentionally replaces captured pixels with a protected proof frame, which can appear as a dark/blank flash. One attempted UI-hierarchy run also triggered the lab accessibility watchdog and fail-closed Chrome; the protection service was restarted and verified `active/ready` afterward. No screenshot or accessibility-instrumentation artifact is treated as a product regression.

## Residual

The remaining optimization candidate is client-side HTTP/2 multiplexing between Chrome and the MITM. It is a material architecture/security decision and was not changed in 06C. Battery A/B remains unavailable while the A23 is charging, so no battery-gate claim is made. Further work requires quantifying that boundary without weakening fail-close, no-store, capability, or media-authority invariants.

## GIF/video follow-up

DEV438 adds a bounded animated-GIF path to the existing GloshIA R3.1 photo
authority. The validator walks the complete container before authorization:
logical screen and color tables, reserved bits, frame bounds, LZW sub-block
framing, normalized frame timing, a maximum of 120 frames/60 seconds, and a
final trailer with no trailing bytes. Malformed, oversized, truncated, or
structurally ambiguous GIFs remain fail-closed. Android decodes one frame at a
time into a 224px RGB tensor, clears it after inspection, and preserves the
original GIF bytes for SAFE delivery; a BLOCK decision still uses the existing
neutral placeholder contract. No raster `data:`/`blob:` allowance was added.

The temporal selector follows the proven DAG strategy but is local to Chrome:
cheap 16x16 signatures, immediate analysis on material change, and a 500ms
model cadence with a ten-analysis cap. All frames are structurally visited, so
skipped stable frames cannot hide a malformed or unbounded container. The
candidate digest is computed once per GIF rather than once per frame to avoid
avoidable CPU work during animation.

The Chrome visual layer still retains its separate dynamic-region video path:
changed-region signatures, two consecutive safe samples and a bounded 500ms
verification schedule (~2 samples/second). This is compositor/accessibility
visual protection, not a network-video bypass. Encoded MP4/HLS/MSE streams
remain fail-closed until a transport authority can preserve the same security
monotonicity; no domain-specific or blanket raster bypass was introduced.

The latency attribution build (DEV439) adds only DEV diagnostics to each real
response: time to upstream headers, bounded sanitize/decision time, downstream
write time, and the existing end-to-end request-to-delivery time. It does not
alter scheduling, buffering, media authority, or release behavior. No
client-side HTTP/2 change was made.

Automated GIF timeline, selector, authority, engine, compile, lint and assemble
validation passed for DEV438. APK SHA-256:
`cf582d81b7f13abbb925a6ec9aa3d802762dc81e7934a302fca1c2d000c5ab46`.
The APK was installed in place on A23 (`versionCode=438`); app-data inode
`1239519`, Chrome data/cache, and `resetCount=3` were preserved. A fresh
Frávega navigation through the installed build showed real HTTP/2 upstream
connections, original safe image delivery, and the expected bounded counters;
no screenshot instrumentation was used.

DEV439 instrumentation APK SHA-256:
`28f6534e024bf265eba5e741b2019a6e5fcd101ae951b10faee5b65004d087ba`.
The A23 accepted the update in place and preserved inode `1239519`, but the
Device Owner currently reports Chrome `suspended=true`; an ADB launch is
rejected by the system admin dialog. Consequently no fresh real-browser
cold-burst sample was attributed from DEV439 yet.
