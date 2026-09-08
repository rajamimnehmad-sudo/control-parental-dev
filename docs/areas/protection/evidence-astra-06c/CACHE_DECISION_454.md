# Bounded decision cache — DEV454 candidate

Base76f285; governance9323b126; single06C writer. Runtime comparison uses exact DEV4538e39ba03 with supported64/256 configuration. No browser data/cache clearing. Same1 inference worker,2 body admissions,2 queue entries,5s decision timeout and unchanged model/authority.

Cause:64-entry decision LRU repeatedly evicts content during ordinary image-rich browsing. Cache keys bind content hash, canonical MIME, model/policy identity and session generation. Entries retain decisions/metadata, not image byte arrays. Existing LRU, clear, stale-generation, MIME and identity tests remain relevant.

Alternating physical Fravega runs: each configuration starts a fresh protection session, reopens official Chrome, then navigates the same home page three times. Each page sample occurs after12s; all full-page load events were still pending, not zero-duration loads. Browser cache/data preserved. Public page content/network may vary; this is not an ON/OFF benchmark or a first-paint measurement.

| Capacity | Final engine calls | Cache hits | Evictions | Decoded images on repeated visits |
|---|---:|---:|---:|---|
|64|241|7|176|381,378|
|256|114|198|0|466,464|

First visits:70–71 engine calls and405 decoded images in either configuration. Thus the cache improves repeated navigation, not cold inference cost. Warm DCL minus response-start was4107.7/4264.1ms at64,3599.0/3822.2ms at256; this interval includes script/layout and is not an isolated image delay. Earlier exploratory256 sequence also reached310 cache hits,113 engine calls,zero evictions. Decision queue rejects/timeouts and raw BLOCK/UNKNOWN remained0/0.

App PSS snapshots during the alternating work were173264KiB at64 and168996KiB at256. These noisy snapshots show no observed app-memory increase but do not quantify the small additional metadata allocation; final health validation is still required. No extra image buffers/workers/concurrency introduced. Keep the supported64 option for controlled comparison; change only the DEV service default to256 and versionCode454. The generic session constructor remains unchanged, because the service explicitly supplies the benchmark configuration.

Evidence: cache64-453-repeat.json, cache256-453-repeat.json, cache-453-alternating.json and runtime-453.log. Pending: exact454 build/tests/APK and physical final health. Local data: image transport remains an unimplemented architecture decision; GIF/video excluded.
