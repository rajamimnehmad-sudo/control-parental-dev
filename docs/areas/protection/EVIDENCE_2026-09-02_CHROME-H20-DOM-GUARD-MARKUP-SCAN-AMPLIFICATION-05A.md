# CHROME H20 DOM wrapper amplification 05A

## Result

- Task: `CHROME-H20-DOM-GUARD-MARKUP-SCAN-AMPLIFICATION-05A`
- Status: `H20_DOM_WRAPPER_AMPLIFICATION_FIXED`
- Functional base: `9cd29a7ddb141874d0ae45a49fc920e73a18a348`
- Device: Samsung A23 `R58T34V31AE`
- Final DEV APK: versionCode `420`
- Final APK SHA-256: `3273aeed0579ac7fe20fdad05041573843f6d50beeeb609a00800b16abd6bac1`

The causal fixture proves that the H20 overhead was not the measured body of every scan alone. The dominant amplification was a DOM convenience-wrapper post-scan of the complete receiver after each insertion. As the receiver grew, one logical insertion scanned the inserted node and then repeatedly traversed the growing container.

## Causal comparison

`CHILD_STRESS` executes the same 240 logical append/remove cycles in both modes.

| Metric | Base H20 ON | Final H20 ON | Final H20 OFF |
| --- | ---: | ---: | ---: |
| Duration (microseconds) | 1,139,100 | 41,600 | 29,800 |
| scanCalls | 735 | 493 | n/a |
| scanNodes | 42,514 | 488 | n/a |
| scanMicros | 997,683 | 7,668 | n/a |
| guardedScans | 480 | 240 | n/a |
| sanitizeElement | 44,315 | 969 | n/a |
| append calls / scans | 240 / 480 | 240 / 240 | n/a |
| PSS / RSS after workload | not isolated | 214,520 / 331,992 KiB | 216,205 / 332,136 KiB |
| MemAvailable after workload | not isolated | 1,036,580 KiB | 1,005,016 KiB |

The final identical-workload overhead is 11.8 ms. `scanNodes` fell 98.9%, `scanMicros` fell 99.2%, and `sanitizeElement` fell 97.8%. The global 254% CPU observation from gate 05 is not used as proof of causality; it is only the event that triggered this bounded A/B.

## Markup distribution

For 240 `MARKUP_STRESS` operations the focused counters show exactly:

- `createElement`: 240 calls, 240 direct factory sanitizations;
- `appendChild`: 240 calls, 240 guarded scans, 240 direct DOM sanitizations;
- `safeMarkup`: 240 calls and scans;
- `innerHTML`: 240 calls and post-scans;
- guarded scans: 240;
- markup scans: 480;
- markup scanned nodes: 240;
- markup scan time: 5,471 microseconds.

This explains the observed approximate `1 guarded + 2 markup + 2 direct sanitizations` per logical operation. That proportional markup work is cheap in the deterministic fixture, so it was measured but not optimized speculatively.

## Fix

- DOM convenience wrappers no longer post-scan their complete receiver after already scanning each inserted node. External mutations remain covered by the MutationObserver.
- DEV-only renderer telemetry attributes calls, scans, direct sanitization, root types, nodes, total/max time, and factory/DOM/markup families without per-event IPC.
- Idempotence fixes prevent H20-owned iframe and SVG icon writes from feeding repeated observer work.
- Focused guards keep data/blob media and CSS setter mutations fail-closed; the final DOMContentLoaded pass covers static parser additions.

No R3.1 model/threshold, Byte Gate, Service Worker invariant, host trust, tamper authority, or release authority changed.

## Public dynamic pages

The public pages are normality checks only, not causal proof.

- Mimo: renderer CPU max 15%, renderer RSS max about 198 MiB, aggregate Chrome PSS/RSS 180,896/267,764 KiB, MemAvailable 1,003,804 KiB. Snapshot: 224 scans, 252 nodes, 26,384 microseconds.
- Fravega: renderer CPU max 30%, renderer RSS max about 232 MiB, aggregate Chrome PSS/RSS 188,071/277,136 KiB, MemAvailable 973,836 KiB. Snapshot: 164 scans; the single 1,260-node initial traversal accounted for 114,799 of 120,784 scan microseconds.

Neither page showed sustained CPU or memory growth.

## Security and health

- Controlled local media/CSS/Canvas/ImageBitmap/OffscreenCanvas/WebGL/WebGPU/SVG/iframe/srcdoc/Shadow DOM/tamper/protected primordial/SW/SPA scenarios remained hidden, blocked, or safe as expected.
- SAFE network delivery, BLOCK replacement, UNKNOWN/unsupported fail-close, and digest cache were exercised. Raw BLOCK/UNKNOWN delivery remained `0/0`.
- Queue rejects/timeouts: `0/0`; protectFailure: `0`; QUIC/direct TCP bypass: `0/0`.
- SELF_READY accepted, parser continued, original script started, and renderer reports/rejected remained healthy.
- Final-run ANR/crash/OOM/new LOW_MEMORY: `0/0/0/0`; thermal status: `0`.
- Chrome resetCount remained `3`; no Chrome data was cleared.
- Stop completed with proxy cleared, CA removed, VPN restored, and cache cleared.

The final controlled rerun inherited browser negative decode entries from earlier stress attempts, so several network DOM probes rendered `ERROR`; proxy counters still showed SAFE raw delivery and UNKNOWN/unsupported replacement with raw `0/0`. A fresh controlled run on the same functional shield code had all scenarios pass except the known AVIF negative decode entry. This browser-cache artifact is not treated as evidence of an H20 security bypass.

## Validation

- Focused unit tests for bootstrap contract, amplification fixture, and controlled fixture: PASS.
- `compileDevDebugKotlin`: PASS through build.
- `lintDevDebug`: PASS.
- `assembleDevDebug`: PASS.
- Changed Kotlin sources and tests are ktlint-clean. The aggregate DEV source-set tasks still report pre-existing violations in unrelated files.
