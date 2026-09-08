# Chrome Astra06C — DEV454 architecture boundary

STATUS: **BLOCKED — authorization for static local-raster transport pending.** No CHROME_ASTRA_PHOTOS_NORMALITY_PASS and no final product closure. The two source-stripped sponsored Google images are exactly assigned as data:image/webp by their page scripts; existing code cannot send these local bytes to the image authority. The concrete bounded transport proposal is in LOCAL_RASTER_DECISION.md. No implementation has crossed that architecture boundary. User trial remains unanswered.

## Identity / coordination

- Task06C, same owner Codex Astra — Chrome06C Photos Rebaseline; no second Chrome writer/task.
- BASE76f285be92945857e5de89423f5df7edf54fc7a9; GOVERNANCE main9323b126dde3b91e21e2136a11d087fa3c1f0ca1, remote verified.
- FUNCTIONAL SHA c2fd21586ea20b4d404a049a87ea81ad11d90c23.
- Work branch work/chrome-h20-astra-rebaseline-06c; matching isolated worktree.
- Review destination review/chrome-h20-astra-rebaseline-06c-triage. Exact remote verification and final Central HEAD are reported after publication. Central pre-publication83583ce0a0555f7aaf817a5677ccbbdaa48ea126.
- APK454 /1.0.1-dev /com.contentfilter.user.dev. Signer SHA256 d51bc0dabd280ce1b0f098ae168eb57758faeba301156cde835737835f8a8832.
- Local and installed APK SHA256 f2076615b524054eef66c2405ffdf845fded9d3f9dabce34a26fd05f27b85e79. SOURCE–APK MATCH. Built from clean compiled source at the functional SHA; subsequent evidence-only commit does not replace that build identity.

## Adopted / discarded

Selected Photos/UI/measurement work from the integral anchor remains preserved: original legitimate SVG/UI handling, bounded image decisions, localized gray replacement, phase timing/cancellation accounting, TLS certificate-cache contention fixes, and normal form compatibility. See historical450 report for earlier evidence; its integral PASS is explicitly suspended.

451/452: recover an expired guard session through the existing protocol and a fresh health check, preserve external cancellation, retry only a bounded local IPC timeout. Never grant a lease from stale health or bypass deliberate STOP/PREPARE_UPDATE. 453:2s deadline only before first request byte;20s active headers/uploads and TLS handshake unchanged. 454:256 bounded decision metadata entries instead of64; no additional image buffers or inference concurrency.

Discarded as defaults: inference concurrency2 (inconsistent repeated timing benefit), unproven transport tuning, complete experimental06C chain. No GIF/video/media experiments imported. Local data: photo transport is proposed only, not implemented. No host-specific code. All148 changed tracked paths from anchor are within authorized scope; model core/domain have no diff.

## Normal Chrome / Photos

454 physically revalidated SVG16/16, forms13/13 and Shadow12/12. Official Chrome/Google original UI is visibly presented; the Google screenshot and exact local-image assignment evidence expose the remaining compatibility gap. Google sponsored photos and suggestion chips using local rasters remain unsupported/fail-closed. Network gray replacements and model decisions are a separate category; unsupported is not called compatible.

Last broader452 physical gate: Mimo original menu/logo/photos; Fravega product7 photos, cart add, coupon typing/clearing without submission, remove confirmation and empty cart; Mercado Libre home; rotation/history/reload/tab and wake recovery. These are prior-build observations, not a claim that every scenario passed again on454. H&M returned403 through proxy and external curl; NETWORK/SITE/ANTI-BOT observation, no Chrome-OFF equivalence or domain workaround.

## Latency

- Causal worker retention452: new connection admission reached7377ms behind idle connections held20.02s. Physical453 TLS probe: idle EOF20.029→2.010s; active6s partial headers/upload retained201 and exact5-byte body. Real453 sequence615 admissions p50/p95/p99=0.475/4.700/12.333ms, max48.370ms.
- Google45332 network image deliveries p50/p95/p99=419.832/1002.765/1130.469ms, no network failures in that sample. Not an ON/OFF comparison or first-paint measurement.
- Alternating453 runtime cache64/256, three Fravega visits: engineCalls241→114, hits7→198, evictions176→0; first visits70–71 analyses in both. Warm decoded-image counts381/378 vs466/464. This improves reuse, not cold inference.
-454 mixed initial workload75 engine deliveries p50/p95/p99=1405.598/2912.789/4044.059ms;25 cache deliveries260.376/716.640/756.938ms. Initial Chrome restored Fravega before the controlled fixture, so this is not isolated cold latency. See CONTROL_454.md and independent phase quantiles in latency-454-triage.json; overlapping intervals are not summed.
-454 two warm controlled fixture visits: first decoded resource end305.1/286.3ms, first eight completion960.2/863.7ms. No measured ON/OFF target or subjective acceptance claimed.

## Automated / health / security

404 Chrome/guard tests, build and lint PASS on454. Existing38 VPN tests inherited unchanged. First build attempt exposed the old64 default assertion; corrected before the exact successful build. No failed test hidden.

454 health is **PARTIAL,14min/15 samples**, stopped at the architecture boundary, not a>=45min final gate. Same PID8375, thermal0, MemAvailable minimum656860KiB/final716636KiB, recent median CPU6.73% of one core. No new crash/ANR in inspected exit history; last process exits were the intentional package update. FD/socket count unavailable on non-debuggable package; FDSize is capacity, not count.452 completed46min historically;453 was mixed tuning and partial. Final>=45min gate remains required after Photos architecture is settled. Battery06B is historical reference only at this boundary; no new discharge test (USB powered), final proportional consumption decision pending.

Final recorded status: active=true, ready=true, chromeSuspended=false; raw BLOCK/UNKNOWN0/0, protectFailure0, queueRejects/timeouts0/0, QUIC/direct-TCP attempts0/0, resetCount3. These are observed counters, not a claim that every generic DNS/network error was zero. DO and affiliation, enabled+bound Accessibility verified after454 installation. R3.1/weights/labels/thresholds and existing guard/release/parser/SELF_READY/SW authority remain unchanged by the follow-up. No unsafe original release, new clearApplicationUserData, Chrome data/cache wipe, main/PR/merge/Production/deploy/spending.

FINAL DEVICE: A23SM-A235M/R58T34V31AE online, awake, official Chrome unsuspended on Google Images, protection ready, Device Owner affiliated and Accessibility bound; app454 installed, reset3 and Chrome ceDataInode6090 retained. Host diagnostic recorders stopped; only task-created19227 probe forward removed. Device protection was not stopped for handoff. Chrome remains protected; deliberate STOP or a genuine failed protection precondition still fails closed.

RESIDUAL: authorization for the bounded static data: transport; then implementation/security review, final physical/health gates and user acceptance. GIF/video remain paused.
