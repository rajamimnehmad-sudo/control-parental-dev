# DEV453 candidate: idle connections retaining proxy workers

Status: DEV453 installed with exact source/APK match.404 tests and lint PASS. Local deadline probe PASS; real-page and final health validation in progress, not an integral PASS.

DEV452 runtime evidence shows a second, independent source of global latency. During the Fravega sequence, all64 connection workers were occupied. New accepted connections waited6.0–7.4s even though queue rejections stayed zero. The aggregate503 worker samples at09:07 had p50/p95/p99=0.518/19.139/2537.558ms, maximum7377.178ms. These connection waits are separate from per-image request-to-delivery timings.

Same-worker causal evidence, from runtime-452.log:

- TID21056: c159 TLS ready08:44:42.898, no following request on that worker, then c206 starts08:45:02.918 after7302.281ms admission wait. Worker retained20.020s after TLS readiness.
- TID21229: final POST response on its previous connection08:44:43.031, then c207 starts08:45:03.054 after7377.178ms admission wait. Worker retained20.023s after response completion.
- TID21107: c157 TLS ready08:44:40.807, then c204 starts08:45:00.830 after6009.061ms admission wait. Worker retained20.023s.

The connection implementation used the same20s socket read deadline while waiting for another HTTP request. Raising workers earlier postponed this bottleneck but did not eliminate it. No async OkHttp Dispatcher host limit is involved.

## Narrow correction

Retain64 workers,32 queued connections,2 admitted image bodies,1 inference worker and64 cached decisions. Set a2s read deadline only while waiting for the first byte of a CONNECT/HTTP request. On its first byte, restore the20s active-message deadline before reading remaining headers or upload body. The TLS handshake explicitly retains20s. Apply the same policy to direct HTTP and tunneled HTTP/1.1. No upstream connection pool, HTTP/2, model, thresholds, byte authority or document release change.

The small stream wrapper does not buffer, discard or rewrite bytes. Only a SocketTimeoutException while awaiting the first request byte becomes an idle close. Other socket errors remain errors, and partial-request timeouts remain protocol failures. Existing idle close handling releases the worker without a fabricated error response.

This may increase downstream reconnects after idle periods; the existing bounded certificate cache and independent OkHttp upstream reuse remain intact. Retain the change only after physical repeat runs demonstrate lower admission tails without unacceptable reconnect/CPU/health cost. Do not call it proven from unit tests alone.

Required validation: first-byte/active header/body deadline separation, byte identity and pipelined requests, zero-length reads, idle vs partial timeout, ordinary SocketException accounting; existing proxy tests; exact commit/APK identity; physical Google/Fravega/burst/reload/lifecycle; final>=45min health. Static data: raster transport remains a separate pending architecture decision and is not implemented by this change.

## Physical deadline comparison

Same bounded ADB-loopback TLS/HTTP1.1 fixture probe: DEV452 idle EOF20.029s; DEV453 idle EOF2.010s. Both retained a deliberately slow6s request (partial headers and upload), returned201 and exactly echoed the5 original bytes with identical SHA256. Probe is not a Chrome latency/TLS trust gate. The first setup omitted ALPN and hit the existing non-ALPN client incompatibility; it was excluded and the probe corrected to negotiate HTTP/1.1 like Chrome. No candidate behavior was changed to accommodate that setup.

First real Fravega home→product sequence on453:227 accepted connections, worker admission p50/p95/p99=0.552/7.862/15.758ms, max25.123ms. This is an initial real-page sample, not a randomized overall-page A/B or a final result.

## Follow-up sampling

Before runtime cache experiments,615 real connection admissions had p50/p95/p99=0.475/4.700/12.333ms, maximum48.370ms. Google32 network image deliveries had p50/p95/p99=419.832/1002.765/1130.469ms, no network failures in that sample. These are independent intervals and are not added. The image samples are not an ON/OFF comparison.

The453 health recording started09:26:53 ART. From09:49:48 onward it includes intentional STOP/START transitions for supported runtime cache64/256 comparisons; it is not an uninterrupted default-configuration final health gate. Chrome was reopened after these controlled transitions. No Chrome data/cache was cleared; resetCount stays3.
