# CHROME-H20-GLOBAL-WEB-NORMALITY-05

Date: 2026-09-01
Status: BLOCKED PHYSICAL
Blocker: `H20_DYNAMIC_DOCUMENT_DOM_GUARD_SCAN_AMPLIFICATION`

## Inputs and scope

- Functional base/HEAD: `9cd29a7ddb141874d0ae45a49fc920e73a18a348`.
- Installed APK: DEV417 (`versionCode=417`), SHA-256
  `4fc837a2ba17b9c2b6c3919561c4239b6715cb93e75e88c291c36a3a14e22d0f`.
- H20, Byte Gate, GloshIA Visual R3.1, model weights, thresholds and selective
  config remained unchanged (`cache=64`, `concurrency=1`, `queue=2`,
  `timeout=5000 ms`).
- No code change, build, install, Chrome-data deletion or trusted-bootstrap
  reset was made. `resetCount` remained `3`.

## Controlled selective

The normal H20 fixture was used; `/web19/controlled` was not opened.

- SAFE original bytes were delivered (`safeRawDelivered=8`).
- BLOCK was replaced (`blockedReplaced=3`).
- UNKNOWN was replaced fail-close (`unknownReplaced=7`).
- raw BLOCK/UNKNOWN remained `0 / 0`.
- The initial document recorded `cacheHits=1`, `engineCalls=10`. Reload and
  repeated content raised cache hits to `4` while engine calls rose only to
  `11`, covering repeated SAFE/BLOCK digests without proportional reinference.
- renderer reports were live and accepted (`rejected=0`).

## Public matrix reached before STOP

The formal continuous session started at epoch `1788305248`. It exercised:

| Class | Observation |
| --- | --- |
| Search / editable local form | Google loaded; local text was entered without submission |
| Editorial/news attempt | BBC Mundo rendered blank and was replaced rather than counted as usable |
| Editorial/news / dynamic / multi-origin | Infobae loaded, remained navigable and showed progressive SAFE plus BLOCK placeholders during long scroll |
| Reference/institutional | Spanish Wikipedia loaded; scroll, back, forward and reload were exercised |
| E-commerce / responsive / lazy | Mimo loaded, scrolled and remained usable with SAFE content and BLOCK placeholders |
| SPA dynamic | React loaded and was scrolled; its hidden-document renderer report exposed the pathological signature below |
| Image-heavy/lazy attempt | Unsplash remained blank; it was not counted as a successful class |

Tabs, warm navigation, background/foreground and
portrait-to-landscape-to-portrait were exercised. The original orientation
settings (`accelerometer_rotation=1`, `user_rotation=0`) were restored.

The full eight-page/45-minute matrix was intentionally not completed. At
approximately 25 minutes the mandatory early-stop condition was reached.

## Early-stop event

Renderer PID `25775` rose through approximately `352`, `534`, `664`, `834` and
`860 MiB` RSS. Sampled CPU reached `254%` and remained above `100%` across
multiple approximately 36-second samples. `MemAvailable` fell to `331020 KiB`
(approximately `323.3 MiB`). Thermal status remained `0`.

After the active dynamic document was hidden, CPU returned to `0%`, but the
renderer retained approximately `700 MiB` RSS. A direct post-event snapshot
still measured `662935 KiB` PSS and `700696 KiB` RSS. Closing the positively
identified React test tab alone retained PID `25775`; closing the adjacent,
also positively identified Unsplash test tab terminated the shared renderer
process. No unknown persisted tab was closed.

No new ANR, crash, OOM or LOW_MEMORY exit was present in ApplicationExitInfo at
STOP. The run was stopped before waiting for one.

## Renderer attribution

Six renderer reports were accepted and none rejected. The cumulative counters
before the pathological document report (`reports=5`) were:

- callbacks/records: `225 / 14697`;
- child/attribute records: `565 / 25`;
- scan calls/nodes/micros: `1141 / 1789 / 47741`;
- guarded/markup scans: `638 / 48`;
- sanitize element/container: `5014 / 2362`;
- ensure style/curtain: `230 / 240`.

The next hidden-document snapshot (`reports=6`) raised them to:

- callbacks/records: `255 / 17300`;
- child/attribute records: `639 / 26`;
- scan calls/nodes/micros: `176262 / 60197 / 1124883`;
- guarded/markup scans: `58994 / 116752`;
- sanitize element/container: `180201 / 2406`;
- ensure style/curtain: `261 / 273`.

Therefore that single document delta was:

- only `30` callbacks, `74` child records and `1` attribute record;
- `175121` scan calls, including `58356` guarded and `116704` markup scans;
- `175187` additional element sanitizations;
- approximately `1.077 s` of recorded scan time;
- only `31 / 33` additional style/curtain checks;
- no SVG feedback, Shadow DOM activity or internal mutations.

This disproportional expansion cannot be attributed to ordinary attribute
churn, SVG feedback, Shadow observers, or unconditional style/curtain checks.
It localizes the observed H20 overhead to the general DOM guard/markup wrapper
path. The technical classification is:

`H20_DYNAMIC_DOCUMENT_DOM_GUARD_SCAN_AMPLIFICATION`

No host-specific workaround or speculative fix was made. A generic fix needs a
separate focused implementation and security revalidation before this global
normality gate is rerun.

## Security and failures

Final pre-STOP status retained:

- requests/engine calls: `1219 / 70`;
- cache hits/misses/evictions: `4 / 70 / 6`;
- inference/in-flight/queue peaks: `1 / 2 / 2`;
- queue rejects/timeouts: `0 / 0`;
- raw BLOCK/UNKNOWN: `0 / 0`;
- SAFE raw/BLOCK replaced/UNKNOWN replaced/unsupported replaced:
  `61 / 13 / 36 / 47`;
- protect failures: `0`;
- QUIC/direct-TCP bypass: `0 / 0`;
- document transform outstanding: `0`;
- bootstrap reset generation/complete generation/count: `2 / 2 / 3`.

All `131 / 131` failure records were recovered from Logcat:

| Category | Count | Evidence |
| --- | ---: | --- |
| Client TLS handshake EOF/cancel | 130 | `phase=tls_failed`, `side=client`, `stage=handshake`, `SSLHandshakeException` rooted in `EOFException` |
| Upstream TLS | 0 | No matching line |
| DNS | 0 | No matching line |
| Upstream abort before response | 1 | `phase=upstream_failed`, `responseStarted=false`, `errorResponseWritten=true`, `UnknownServiceException` |
| Upstream abort after response | 0 | No matching line |
| Protocol/transform | 0 | No matching line |
| Fatal | 0 | No matching line |

No queue rejection, timeout, protect failure, bypass or raw blocked/unknown
delivery accompanied these failures.

## STOP and rollback

Final idempotent STOP verified:

- lifecycle stopped, proxy/cache/CA removed and VPN restored;
- transport `inactive`, runtime `ready`;
- owned FD/protected UDP sockets `0 / 0`;
- document/ready outstanding `0 / 0`;
- global proxy and always-on/lockdown VPN settings `null`;
- Device Owner, empty affiliation set and Accessibility preserved;
- Glosh/Chrome data inodes preserved at `1239519 / 6090`;
- Chrome suspended under the inactive fail-close guard;
- orientation restored;
- `resetCount=3`.

The residual is the generic renderer amplification above. The result is not a
domain certification and does not authorize Product Hardening.
