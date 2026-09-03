# CHROME-H20-BATTERY-BASELINE-CONTROL-06B

## Result

`PASS TECHNICAL / PENDING CHATGPT REVIEW` — `CHROME_H20_GLOSHIA_PHOTOS_BATTERY_GATE_PASS`.

- Base and final functional SHA: `76f285be92945857e5de89423f5df7edf54fc7a9`.
- Governance SHA: `9323b126dde3b91e21e2136a11d087fa3c1f0ca1`.
- Device: Samsung A23 `SM-A235M`, Android 14, official Chrome `152.0.7977.64`.
- Measurement APK: DEV424, SHA-256 `4674ee30aa902f358f39a35dda83dcbfb5dec028da45c8cb92a28124e2bd8d6d`.
- The APK was installed update-in-place. Chrome data/cache were not cleared and its data inode remained `6090`.

## DEV-only baseline lease

The measurement branch adds a temporary `DEV_ONLY_BATTERY_BASELINE_LEASE`; it is test harness and is excluded from the final functional review history.

- It is compiled only in the DEV source set, defaults OFF, and is reachable only through a receiver protected by `android.permission.DUMP`.
- Activation requires the DEV package to be Device Owner and validates the exact Chrome package, suspended Chrome, inactive lab/presentation/real-web authority, absent lab proxy/global proxy/ephemeral CA/full-tunnel, zero outstanding document/ready/parser/active-document state, closed SVG registry, and bootstrap reset count `3`.
- The lease is memory-only, bound to monotonic elapsed time plus boot identity, limited to 1–45 minutes, shown in guard diagnostics, and revoked by normal START, explicit STOP, process/boot loss, or expiry. Invalid/expired state restores the existing fail-closed Chrome suspension.
- A one-minute physical expiry smoke automatically resuspended Chrome. Explicit revocation before ARM B also resuspended Chrome. An activation attempt while the lab was active was rejected for `lab_active,real_web_authority,lab_proxy,ephemeral_ca,full_tunnel,svg_registry`.

Automated validation on the measurement implementation:

- focused baseline/guard tests: PASS;
- `:app-user:testDevDebugUnitTest`: PASS;
- `:app-user:compileDevDebugKotlin`: PASS;
- `:app-user:lintDevDebug`: PASS;
- `:app-user:assembleDevDebug`: PASS;
- changed DEV Kotlin formatting: PASS; the aggregate test-source formatting task reports only pre-existing violations in untouched tests.

## Foreground A/B

Both arms ran unplugged, on the same A23 and Chrome build, at fixed brightness 120, portrait, Wi-Fi, screen on, battery saver off, and the same deterministic ADB workload. Each completed all 30 cycles: six Fravega launches, 170 swipes, 10 reloads, and identical dwell cadence. The normal Content Filter DNS VPN state was retained in both arms.

### ARM A — Chrome normal / Photos lab OFF

- Preconditions verified: H20/Photos lab OFF, lab proxy OFF, ephemeral lab CA absent, full-tunnel lab absent, inference activity absent, Chrome released only by the DEV lease.
- Duration: 1,928 seconds (`32m08s`).
- Charge counter: 2,150,000 to 1,925,000 microampere-hours; drain `225 mAh`, normalized `420.1 mAh/h`.
- Battery: 43% to 38%; temperature 23.0 to 27.7 degrees C.
- MemAvailable: observed minimum 543,096 KiB; final 625,724 KiB.
- Final Chrome TOTAL PSS/RSS: 218,687 / 234,444 KiB. Checkpoint RSS did not grow (about 269, 249, 237, 214, 212, and 234 MiB).

### ARM B — H20 + Byte Gate + GloshIA R3.1 selective ON

- Normal 06A START verified `active=true`, `ready=true`, full-tunnel DEV transport, self-shield and stock-media authority enabled, and reset count `3`.
- Duration: 1,929 seconds (`32m09s`).
- Charge counter: 1,910,000 to 1,690,000 microampere-hours; drain `220 mAh`, normalized `410.6 mAh/h`.
- Battery: 38% to 33%; temperature 27.9 to 28.6 degrees C.
- Final MemAvailable: 509,776 KiB.
- Final Chrome TOTAL PSS/RSS: 201,887 / 251,864 KiB. Checkpoint RSS did not grow (about 331, 328, 299, 290, 248, and 252 MiB).
- Final workload counters: 1,130 requests; 321 visual candidates; 77 SAFE originals; 244 unsupported replacements; 65 cache hits; 12 engine calls/inferences; queue peak 1; in-flight peak 2.

Per-process CPU and isolated wakelock/network byte totals were not reliable enough on this stock device to state normalized numeric deltas. Representative behavior, declining RSS, charge-counter result, and temperature showed no sustained CPU, wake/reconnect, memory, or thermal runaway.

### Relative result

Normalized foreground delta `(B - A) / A` was approximately `-2.3%`, inside the `<=10%` healthy band and in the direction of measurement noise rather than added drain. Both arms also lost exactly five battery percentage points. No counter-check was required.

## Background/screen-off A/B

Each arm used the same Fravega launch, HOME transition, and 15-minute screen-off window. Charge-counter resolution was 5 mAh, so this is supporting evidence rather than a commercial battery claim.

- ARM A: 907 seconds, 1,625,000 to 1,570,000 microampere-hours (`55 mAh`, about `218.3 mAh/h`), 32% to 31%, 25.9 to 24.5 degrees C.
- ARM B: 907 seconds, 1,675,000 to 1,625,000 microampere-hours (`50 mAh`, about `198.5 mAh/h`), 33% to 32%, 28.6 to 25.5 degrees C.
- Relative background delta: approximately `-9.1%`. Requests/inferences did not exhibit a sustained loop and both arms cooled throughout the window.

## Post-measurement security and health

- Controlled Original UI SVG fixture: 12/12 PASS (`INLINE_ORIGINAL`, `INLINE_CLICK`, `STATIC_CSS`, `EXTERNAL_CSS`, `STYLE_ATTRIBUTE`, `DATA_IMAGE`, `FAVICON`, `NETWORK_SVG`, `UNSAFE_NETWORK_FAIL_CLOSED`, `DYNAMIC_CSS`, `RASTER_FAIL_CLOSED`, `UNSAFE_INLINE_FAIL_CLOSED`).
- Fravega was the real public workload for both complete foreground arms; ARM B retained the previously reviewed generic 06A compatibility path.
- raw BLOCK/UNKNOWN: `0/0`; decision queue rejects/timeouts: `0/0`; proxy queue rejects: `0`; protect failure: `0`; QUIC/direct-TCP bypass: `0/0`.
- Document transform outstanding was `0`; SELF_READY/release/parser completed during the measured arm; reset count remained `3`.
- No new crash, ANR, OOM, or LOW_MEMORY appeared. ApplicationExitInfo contains only intentional Chrome force-stops and normal isolated-renderer recycling during this measurement; its sole listed LOW_MEMORY entry predates the batch (2026-08-31).
- A deliberate post-measurement main-app force-stop triggered the existing `accessibility_lost` guard and suspended Chrome fail-closed. It occurred after the valid A/B and 12/12 fixture and introduced no product-code delta.

## Rollback and functional-history isolation

The final lab state is STOPPED: baseline lease inactive, Chrome suspended fail-closed, proxy cleared, ephemeral CA removed, full-tunnel lab absent, SVG registry closed, global proxy null, always-on/lockdown VPN null, and bootstrap reset count `3`. Brightness `99`, screen timeout `600000`, automatic rotation, portrait, and stay-awake OFF were restored. The normal narrow Content Filter DNS VPN remains the device baseline.

The DEV lease implementation is preserved only on the measurement branch. The final review branch is based directly on `76f285be92945857e5de89423f5df7edf54fc7a9` and contains this evidence document only, so the final functional SHA remains unchanged.
