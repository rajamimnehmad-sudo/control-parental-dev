# Chrome 06C — checkpoint DEV462

STATUS: **BLOCKED — A23 disconnected after Mac restart.** No technical normality PASS or product closure. Normal bugs remain in the same task; continuation needs the exact handset back on ADB.

- BASE: `76f285be92945857e5de89423f5df7edf54fc7a9`.
- GOVERNANCE: `main@9323b126dde3b91e21e2136a11d087fa3c1f0ca1`.
- FUNCTIONAL SHA: `03aff2a68e08dd17894edc75bbea0c44a4ea027d`.
- REVIEW: `review/chrome-h20-astra-rebaseline-06c-triage`; final remote SHA and Central HEAD are reported after publication/verification. Luna refs and historical Astra final remain untouched.
- APK:462 /1.0.1-dev /`com.contentfilter.user.dev`; signer `d51bc0dabd280ce1b0f098ae168eb57758faeba301156cde835737835f8a8832`; local SHA256 `831099936c16a86579ea4a0753c4eba1b52c8591c8a9b60d333a348882147096`.
- SOURCE–APK: local candidate matches committed application source; **462 not installed**. Last verified installed461 SHA `b87b01f951129364237f280323f13983cb5cacb718055fc354913f023753ac74`.
- ADOPTED: queued exact-byte decisions are rechecked on processing completion; unchanged capacity2, inference1, bounded bytes24MiB. SAFE original, BLOCK/UNKNOWN replacements. No polling, host fixes, URL-only SAFE cache or model/threshold/release change. Details in `LATE_CACHE_ADMISSION_462.md`.
- NORMAL CHROME: user rejects Google camisa mujer fluency. H&M403 and Frávega application404 unresolved. Chrome normality is not closed.
- PHOTOS/GLOSHIA:461 SVG16/16, local11/11, Shadow12/12 and forms13/13 passed;462 physical revalidation pending.
- LATENCY:461 controlled mixed burst (10 cached images alongside15 cold): p50/p95/p99 DOMload1069.55/1622.70/1622.70ms versus4603092.05/3116.10/3116.10ms. One run each; n10 tails are sample maxima, no ON/OFF or paint claim. User case Google web load9136.5→3400.7ms reload; Images4133.1→3410.2ms, single runs. A late cache hit waited3790.69ms for processing;462 fixes that demonstrated scheduling case, not yet measured physically.
- AUTOMATED:427 tests, assembly and lint PASS. First assembly interrupted by Mac reboot14:20:44 ART; recovered build verified.
- PHYSICAL: **462 NOT RUN**. USB/ADB remains unavailable despite repeated checks; current device state cannot be asserted.
- HEALTH:46146min/47 samples; thermal0, RSS median147336KiB/last144580KiB/peak234528KiB, MemAvailable minimum474992KiB. Ten upstream failures (5 InterruptedIOException,1 UnknownServiceException,4 IOException) are not reclassified as expected disconnects.462 needs its own prolonged gate.
- BATTERY:06B historical reference; no new discharge test on USB. Final proportional gate/inheritance decision remains pending after462 runtime validation.
- SECURITY:461 last counters raw BLOCK/UNKNOWN0/0, protectFailure0, normal queue rejects/timeouts0/0, QUIC/direct-TCP bypass0/0.462 static change preserves Byte Gate/model/authority; physical security gate pending.
- RESET COUNT:3 last verified; no cache/data deletion or new application-data reset.
- FINAL DEVICE STATE: last physically observed official Chrome open on461; now disconnected, unable to verify or leave it in a chosen final screen.
- RESIDUALS: user acceptance, revisit/cold latency, H&M/Frávega, final462 health/security. Ad reduction requested but paused and not implemented. **No GIF/video.**

Next: reconnect SM-A235M/R58T34V31AE, confirm normal physical prerequisites, install462 using the prepared-update path, verify installed hash, then repeat the user case and focused/prolonged gates in this same task. No duplicate Chrome task or intermediate product release.
