# Chrome Astra 06C — DEV467 physical candidate

- Governance: `9323b126dde3b91e21e2136a11d087fa3c1f0ca1`
- Functional base: `76f285be92945857e5de89423f5df7edf54fc7a9`
- Functional SHA: `83b9b13abb286c30f23e0937bd53e37b10b65d1a`
- Device: SM-A235M / R58T34V31AE
- Package/version: `com.contentfilter.user.dev` / 467 / `1.0.1-dev`
- APK SHA-256, local and installed: `d6366273d8247e50e9d342e16dd267ca550cafb23a16c42c35149b03c48ba698`
- Signer SHA-256: `d51bc0dabd280ce1b0f098ae168eb57758faeba301156cde835737835f8a8832`

## Adopted deltas

- Long-running TLS sessions use a ten-year session CA and renewable 24-hour leaf certificates. Cached leaves refresh 30 minutes before expiry. This removes the observed twelve-hour `ERR_CERT_DATE_INVALID` failure without persisting or reusing leaf keys across sessions.
- Default image decision concurrency is two and decision metadata cache capacity is 512, matching DAG v230's bounded native concurrency and cache. Queue capacity remains two and the image byte budget remains 24 MiB.
- A single upstream timeout can be retried only for empty `GET`/`HEAD` requests. Mutation requests are never retried. The recovery is counted as `upstreamIdempotentRetries`.

## Automated validation

- `:app-user:testDevDebugUnitTest`: 474 tests, 0 failures, 0 errors, 0 skipped.
- `:app-user:lintDevDebug`: PASS.
- `chrome-local-photo-priority.test.mjs`: PASS for visible priority, bounded overtaking, FIFO fallback, cancellation, picture geometry and geometry failure fallback.
- R8 DEV build: PASS.

## Physical validation

- Fresh default runtime: decision cache 512, decision concurrency 2, queue 2, timeout 5000 ms; active/ready; `resetCount=3`.
- Controlled local-photo contract: 11/11 PASS including static, dynamic, srcset, picture, input, inserted markup, clone, shadow, removed, stale and byte identity.
- SVG 06A: 16/16 PASS. Form target: 13/13 PASS. Detached shadow lifecycle: 12/12 PASS.
- Visible-priority burst on DEV467: 12/12 decoded with original dimensions; above-fold completion 773.7 ms.
- DEV466 three-run precursor on identical concurrency/cache: above-fold 812.2, 900.9 and 790.3 ms; median 812.2 ms.
- Google Images cold runs showed every viewport image decoded. A measured new-query run loaded in 3521.1 ms with 9/9 viewport images. Its 17 image responses had request-to-delivery p50 223.4 ms and p95/p99 637.6 ms; processing admission p50 89.5 ms and p95/p99 301.3 ms.
- DEV467 Google Images: cold 4390.7 ms; three warm reloads 2045.3, 1979.4 and 2540.3 ms; no blank page or upstream timeout.
- Mimo: original logo and SVG controls visible; menu icon 20x20 with `data-glosh-icon-safe=1`; drawer opens and exposes all categories.
- Mercado Libre: home complete, 12/12 viewport images decoded, long scroll available; back navigation preserved it. A repeated automated search reached Mercado Libre's own CAPTCHA and was classified as anti-bot, not a filter failure.
- Frávega: home complete, 19/19 viewport images decoded; listing, product detail, visible forms and add-to-cart passed without purchase or personal data submission; forward navigation and portrait-landscape-portrait passed.
- During a later high-load Frávega run, one idempotent timeout was retried and recovered (`upstreamIdempotentRetries=1`); the page completed with 22/22 viewport images. No mutation request was retried.
- H&M returned HTTP 403 Access Denied from Akamai both on the candidate path and an independent direct fetch. Its doctype-less denial document was fail-closed by the conservative global parser. No domain exception was added.
- Screen sleep/wake restored Chrome in foreground with protection active/ready and `chromeSuspended=false`.

## Security

- No R3.1, weights, labels, thresholds, Byte Gate, release authority, Device Owner, activation or Accessibility changes.
- Observed protected delivery retained raw BLOCK/UNKNOWN at 0/0, QUIC/direct TCP at 0/0, `protectFailure=0`, queue rejects/timeouts at 0/0 and `resetCount=3`.
- GIF and video paths remain excluded.

## Health

- Final DEV467 run completed 2700 seconds with 46 one-minute samples and the same Glosh PID throughout.
- `MemAvailable`: minimum 575932 KiB, maximum 829168 KiB, final 666772 KiB.
- Glosh process RSS: 217772 KiB at start, maximum 235320 KiB, final 155380 KiB. Threads: 135 at start, maximum 149, final 131.
- Detailed PSS: Glosh 173466 -> 168323 KiB; Chrome main 182017 -> 181950 KiB. The final foreground Chrome renderer was 146014 KiB PSS.
- Thermal status was 0 in every sample; battery temperature changed from 26.4 to 26.2 C. The ADB cable was not supplying power, so battery changed from 32% to 27% during the mixed navigation/idle run.
- The 40-minute idle sample showed 728% idle across eight cores, Glosh at 6.2% and its guard at 3.1% while health collection was active.
- No crash, ANR, OOM, low-memory event, proxy fatal, process restart or protection loss occurred. Android exit history contains only the deliberate package-update exits.
- Final Chrome state: foreground, awake, Google Images `camisa mujer`, document complete, 9/9 viewport images decoded.
