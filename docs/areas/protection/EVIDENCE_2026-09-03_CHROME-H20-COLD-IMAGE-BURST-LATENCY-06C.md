# Chrome H20 cold image burst latency 06C

## Scope and anchors

- Task: `chrome-h20-cold-image-burst-latency-06c`.
- Functional base: `76f285be92945857e5de89423f5df7edf54fc7a9`.
- Governance SHA fixed for the batch: `9323b126dde3b91e21e2136a11d087fa3c1f0ca1`.
- Last committed implementation before this batch: `46d31165be48e1405eb26e669d4eb3b40f752d15`.
- Scope stayed inside the Chrome Photos DEV data plane, its focused tests, the DEV version, and this evidence. No model, weights, labels, thresholds, Byte Gate semantics, or release authority changed.

## Root cause and fix

The A23 cold-burst observation was a bounded scheduling problem, not a raw-media leak or model-quality change. Chrome maintains many idle HTTP/1.1 CONNECT tunnels while loading a multi-origin page. The previous eight-worker proxy could let idle tunnels occupy workers until the socket timeout, delaying fresh image origins. The DEV proxy now admits a bounded 64-worker/64-queue fan-out with a five-second idle socket timeout. Image-body admission is bounded at two bodies per inference worker (four with the default two workers), preserving backpressure.

The default decision profile remains bounded at `256 entries / 2 concurrent inferences / 4 queue entries / 5000 ms`. The generic blocked fixture is now a fixed neutral blur-style placeholder without product text; it never contains original blocked bytes. The fail-closed curtain is white and no longer displays a per-navigation “Analizando…” message, keeping the normal Chrome surface usable while authority is pending.

## Automated validation

- Focused `ChromePhotoDecisionSessionTest`, `ChromeImageContentAuthorityTest`, and `ChromePhotosHttpsProxyConnectionTest`: PASS.
- `:app-user:compileDevDebugKotlin`: PASS.
- `:app-user:lintDevDebug`: PASS.
- `:app-user:assembleDevDebug`: PASS.
- `git diff --check`: PASS.

## APK and update-in-place

- DEV431, `versionName=1.0.1-dev`.
- APK SHA-256: `3a58a17ccb35f0c1ed4d4b31876149c002e43be424c9ad836052ca79a18ce0ad`.
- `adb install -r`: PASS; app data inode remained `1239519`.
- Chrome data/cache was not cleared; bootstrap `resetCount=3` remained unchanged.
- Device Owner and Chrome-only protection state were restored after measurement: `active=true`, `ready=true`, `stockMediaAuthority=true`, `documentSelfShield=true`.

## Physical observations

Device: Samsung A23 `SM-A235M`, Android 14/API 34, official Chrome.

With DEV429/DEV431 protection ON, fresh public pages remained usable and preserved original UI. Google Images showed first visible photos at approximately four seconds and a filled grid at approximately six seconds. Frávega preserved navbar, search, filters, product text and controls; product photos appeared at approximately eight seconds. Representative cumulative ON counters remained `raw BLOCK/UNKNOWN=0/0`, `queue rejects/timeouts=0/0`, `protectFailure=0`, and `bypass=0/0`; inference p95 was approximately 230–255 ms, while proxy p95 was approximately 913–924 ms with peak active tunnels 17–47/64.

## OFF control and instrumentation caveats

A temporary DEV-only baseline lease was used only in the separate 06B harness APK and was revoked/expired before DEV431 was installed. It was not included in this functional history. In the OFF control, Google Images showed photos around two seconds; Frávega showed normal UI around four seconds and product photos around six seconds. This bounds the remaining gap to proxy/network/render scheduling rather than R3.1 inference.

Repeated ADB screenshots are not valid latency evidence: `ChromePhotosProtectedSurface` intentionally replaces captured pixels with a protected proof frame, which can appear as a dark/blank flash. One attempted UI-hierarchy run also triggered the lab accessibility watchdog and fail-closed Chrome; the protection service was restarted and verified `active/ready` afterward. No screenshot or accessibility-instrumentation artifact is treated as a product regression.

## Residual

The remaining optimization candidate is client-side HTTP/2 multiplexing between Chrome and the MITM. It is a material architecture/security decision and was not changed in 06C. Further work requires quantifying that boundary without weakening fail-close, no-store, capability, or media-authority invariants.
