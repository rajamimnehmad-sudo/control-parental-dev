# DEV461: cached photos under mixed cold load

Functional source: `7046808b10b0426a9389e900412d2f74d8d546c0`.
Base: `76f285be92945857e5de89423f5df7edf54fc7a9`; governance: `9323b126dde3b91e21e2136a11d087fa3c1f0ca1`.
423 unit tests, assembly and lint passed. Local and installed APK identities are in `apk-461.json`.

## Cause and change

DEV460 held both body permits through hashing, decoding and inference. A cached image therefore waited behind cold inference before its cache lookup. DEV461 reserves bounded encoded payload bytes, reads and hashes the candidate, then checks the same engine-identity/generation/MIME/hash cache. Only misses acquire the existing two processing permits. SAFE remains byte-identical; BLOCK and UNKNOWN remain replacements. Inference concurrency1 and queue2 are unchanged.

The shared encoded-payload reservation ceiling is24MiB (the previous two12MiB maximum inputs). Unknown lengths reserve12MiB. This is NOT a total Java-heap bound; copies and response buffers also exist. Physical memory and prolonged health remain acceptance gates. `bodyAdmissionMs` now measures byte-reservation waiting; `processingAdmissionMs` measures miss processing admission. Compare their sum where relevant; neither may be silently dropped from latency accounting. Cold misses hash twice; both costs are included.

## Controlled physical comparison

Same A23, exact DEV460/461 APKs, controlled multi-origin page. After the eager SAFE PNG is primed, activate15 remaining cold images, wait400ms, and request10 distinct URLs containing the same8090-byte PNG. Every probe was a SAFE cache hit with8090 original output bytes and zero inference. See `mixed-cache-burst.js`, raw JSONs and `cache-admission-comparison-461.json`.

| DOM image load, milliseconds | DEV460 | DEV461 |
| --- | ---: | ---: |
| count |10|10|
| median |3092.05|1069.55|
| p95, nearest rank |3116.10|1622.70|
| p99, nearest rank |3116.10|1622.70|

Native cache-hit admission was near zero on461 (maximum25.701ms); the first four460 hits waited1608–1823ms. Upstream and Chrome scheduling also varied. This is one mixed-burst run per version, not an ON/OFF or paint benchmark. At count10, tail percentiles are the sample maximum, not a well-estimated population tail.

## Focused physical validation

DEV461: SVG16/16, local raster11/11 including byte identity, Shadow12/12 and forms13/13 passed. Google Images loaded normally in one fresh query: TTFB860.6ms, DCL1567.1ms, load3075.8ms; visible main image resource completions2557.9–3071.2ms. These resource timings are not screenshots or paint timestamps. Health sampling46min is in progress; no final normality PASS.

H&M remains unresolved: phone460 returned403 whereas an independent desktop browser opened the store. Frávega's application404 remains unattributed. Neither is fixed by the local textual HTTP error page.

## User request: default ad reduction

User authorized evaluating default removal of advertisements/sponsored results. No ad-blocking behavior has been added to461, and no performance gain above is attributed to ad removal.

A read of the current Chrome DEV path found no existing ad-filter engine. Chrome's own filter is selective, not universal: [Chromium subresource filter](https://chromium.googlesource.com/chromium/src/+/lkgr/components/subresource_filter/). The standard `rel=sponsored` identifies paid links, including affiliate links, but does not identify every ad or its containing card: [Google's link qualification documentation](https://developers.google.com/search/docs/crawling-indexing/qualify-outbound-links). Hiding text that says promotion, or arbitrary ancestors of links, risks deleting legitimate ecommerce UI. A robust global policy needs its own bounded classification/filter rules and compatibility validation; no per-domain workaround or blanket third-party block was introduced.
