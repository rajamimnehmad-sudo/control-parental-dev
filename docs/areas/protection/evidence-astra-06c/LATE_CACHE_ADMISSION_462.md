# DEV462: recheck decisions when processing completes

Functional source: `03aff2a68e08dd17894edc75bbea0c44a4ea027d`.
Base: `76f285be92945857e5de89423f5df7edf54fc7a9`; governance: `9323b126dde3b91e21e2136a11d087fa3c1f0ca1`.

The user rejects revisit fluency, specifically Google “camisa mujer”. DEV461 included a late cache hit after3790.69ms processing admission. A decision absent at the first lookup can become cached while that request waits behind other processing jobs.

DEV462 replaces the processing semaphore with a fair lock/condition at the same capacity2. Completion wakes waiters; each checks its exact-byte/MIME/engine-generation decision before consuming capacity. Cache probes bind the original input and identity in a private lookup closure, without accepting another URL/body or rehashing on each notification. Cold work still waits at the same processing bound. No polling, extra worker, larger byte budget, inference parallelism, model change, downstream-cache relaxation or release change. Queue population remains bounded by the existing request workers. Admission timing records the actual wait on either completion path.

427 unit tests passed, including a cached waiter returning while an older cold waiter still occupies the sole test slot; interrupted waits preserving interruption and returning capacity; and late SAFE/BLOCK/UNKNOWN decisions preserving original/replacement bytes. These tests establish the scheduling mechanism, not whole-web latency acceptance.

The Mac rebooted at2026-09-08 14:20:44 ART, interrupting the first assembly. Temporary logs and exec sessions disappeared. Source and persisted test/evidence files survived. The output APK at recovery still matched DEV461 (`b87b01f951129364237f280323f13983cb5cacb718055fc354913f023753ac74`), not462. Assembly was restarted from the same commit with one Gradle worker and lower process priority. Logs now reside in ignored `.gradle/astra-06c-logs/` rather than system temporary storage.

ADB has no connected device after restart; the user was asked to reconnect the A23. No462 installation or physical PASS is claimed. DEV461 remains the last verified installed candidate, but current device state cannot be verified while disconnected.

DEV461 completed46min/47 health samples before the reboot. See `health-summary-461.json`: thermal0, raw BLOCK/UNKNOWN0/0, queue rejects/timeouts0/0, protectFailure0, bypass0/0, reset3, Chrome not suspended at the last sample. Ten upstream failures require separate attribution; do not reinterpret them as queue success or expected cancellation. App RSS returned to144580KiB after a234528KiB peak; the mixed workload is not a matched memory A/B. DEV462 changes scheduling and needs its own final physical and prolonged health gate.

Recovered assembly PASS in4m17s. Verified local APK462 identity is in `apk-462.json`; it has not been installed. Platform concurrency semantics checked against [Android Condition](https://developer.android.com/reference/java/util/concurrent/locks/Condition) and [Java17 ReentrantLock](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/locks/ReentrantLock.html).

Lint PASS in1m45s. ADB remains empty across repeated checks after recovery; physical installation and final gate are BLOCKED by the external connection.

USB recovered before final handoff;462 installed and installed SHA verified equal to local. Same task resumed. Active physical tab723 loaded the controlled mixed burst:10 SAFE cache-hit probes alongside15 cold images all loaded, median1016.0ms; single-run comparison is not a whole-web PASS. A fresh Google camisa mujer navigation redirected to/sorry/index and returned429; further repeated searches paused. Before updating,461 had256 cache entries and205 evictions: older visits can require fresh inference after eviction. This is distinct from the late-cache wait fixed in462.
