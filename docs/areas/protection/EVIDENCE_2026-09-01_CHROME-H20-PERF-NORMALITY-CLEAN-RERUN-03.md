# CHROME-H20-PERF-NORMALITY-CLEAN-RERUN-03

Date: 2026-09-01
Status: BLOCKED PHYSICAL — PRODUCT BLOCKER
Blocker: `FRAVEGA_TAB_SUSTAINED_CPU_RUNAWAY_UNDER_H20`

## Immutable inputs

- Functional base/HEAD: `65b27d2ea2f9cb988b127518cd34d5217e5a038d`.
- Installed APK: DEV416 (`versionCode=416`), SHA-256
  `462500ae255e73394f726a73a4558322a9f5e0e716c3e094ec103cb79081bdc2`.
- H20 + Byte Gate + GloshIA Visual R3.1 remained unchanged.
- Selective config remained `cache=64`, `concurrency=1`, `queue=2`,
  `timeout=5000 ms`.
- No build, reinstall, data deletion, history deletion or bootstrap reset.
  `resetCount` remained `3`.

## Clean-session pre-gate

The previous H19 contamination was removed without deleting Chrome data:

1. lab STOP;
2. normal `force-stop` of Chrome;
3. visual inspection through Chrome's normal tab switcher;
4. closure of the positively identified `GLOSH H19 CONTROLLED` card only;
5. another STOP + force-stop + selective restart;
6. cold launch of the correct root fixture `/`.

Chrome confirmed `Se cerró GLOSH H19 CONTROLLED`; the tab count changed from
`14` to `13`. No unknown tab was closed.

The 3-minute pre-gate passed:

- H19 documents/frames/scripts/styles/workers/service workers: all `0`;
- browser CPU settled around `6–7%` after initial load;
- renderer CPU settled around `0–6%`;
- renderer PSS/RSS approximately `29–57 / 117–155 MiB`;
- `MemAvailable` approximately `1.09–1.11 GiB`;
- no new LOW_MEMORY, ANR or crash.

Request growth was expected fixture activity, not churn: the root fixture's
lease script calls `/__glosh_lease` every `250 ms`. The proxy counts those
heartbeats as passthrough while engine calls, candidates and memory remain
stable. Observed growth was approximately four requests per second.

## Controlled selective contract

The root fixture showed SAFE original content and BLOCK placeholder content.
UNKNOWN and unsupported resources remained fail-closed.

- final SAFE raw / BLOCK replaced / UNKNOWN replaced / unsupported replaced:
  `67 / 6 / 46 / 46`;
- raw BLOCK/UNKNOWN: `0 / 0`;
- engine calls/cache hits/misses/evictions: `57 / 19 / 57 / 0`;
- inference/in-flight/queue peaks: `1 / 2 / 1`;
- queue rejects/timeouts: `0 / 0`;
- protect failures: `0`;
- QUIC/direct-TCP bypass: `0 / 0`.

The aggregate proxy `failures` counter reached `152` during real-web navigation.
These were distinct from queue rejects/timeouts and were not causally classified
before the required CPU stop; they remain part of the follow-up evidence.

Cache parity was explicit for both verdicts. Repeated bodies retained verdict,
probability and basis, changed to `source=cache`, and reported
`decodeMs=0`, `inferenceMs=0`. Examples:

- SAFE probability `0.018158317`, `model_allow`;
- BLOCK probability `0.50194615`, `model_filter`, `UncertainRegional`.

Final latency snapshot:

- model load: `273.597 ms`;
- preprocess p50/p95/p99: `9.746 / 90.302 / 107.158 ms`;
- inference p50/p95/p99: `145.670 / 571.185 / 597.746 ms`;
- decision p50/p95/p99: `182.564 / 882.641 / 1093.208 ms`;
- proxy p50/p95/p99: `0.319 / 22.415 / 179.653 ms`;
- cache-hit p50/p95: `0.029 / 0.057 ms`.

## Real web and interaction result

- Google Images without a query: usable.
- Google Images `mujer`: the single normal attempt returned `/sorry/` and was
  classified `BLOCKED_BY_SITE`; no evasion was attempted.
- Fravega: layout/text remained usable and long scroll reached the footer.
- Mimo: layout remained usable; one SAFE hero was original and BLOCK resources
  showed the Glosh placeholder; long scroll/lazy loading worked.
- Mimo form: accepted typed text; it was not submitted, avoiding an external
  subscription action.
- Reload, multiple tabs, scroll/lazy and background/foreground were exercised.
- Back/forward did not complete as a valid pair: Android back moved to the home
  surface and forward did not restore navigation.
- Rotation was not run because the CPU stop condition had already been met.

Only test-owned cards with visually unambiguous titles were closed during causal
triage: `GLOSH H19 CONTROLLED`, the current Glosh fixture, `Mimo & Co` and
`Frávega`. All unknown persisted tabs remained untouched.

## Physical blocker and causal isolation

The sampled 30-minute gate started at `11:38:56Z`. It was stopped after
`11m28s` of scheduled samples; the active diagnostic session continued to about
`16m04s` before lab STOP.

CPU became continuously high after Fravega loaded:

- repeated monitor samples: browser approximately `138–159%`, hot renderer
  approximately `140–150%`;
- independent 3-second `/proc` delta: browser approximately `153–155%`, hot
  renderer approximately `190–194%`;
- the high state persisted for more than seven minutes, including while Chrome
  was backgrounded and after the root fixture was foregrounded again.

The hot renderer remained after closing the test-owned current Glosh tab and
after closing the positively identified Mimo tab. Closing the positively
identified Fravega tab terminated renderer PID `22623`; Android recorded that
exit as `ISOLATED NOT NEEDED`, `pss/rss=226/336 MiB`. Within about 35 seconds:

- browser CPU fell to approximately `16%`;
- renderer CPU fell to `0%`;
- `MemAvailable` recovered to approximately `1.10 GiB`.

This provides strong tab-level attribution to the Fravega document. It does not
yet distinguish ordinary Fravega site behavior from an H20/document-shield
interaction, so it is a real product-normality blocker rather than a demonstrated
regression of the 14C target-only MutationObserver fix.

Memory and safety did not reproduce the old 14B runaway:

- hot renderer maximum measured PSS/RSS: approximately `235/340 MiB`;
- minimum `MemAvailable`: approximately `662 MiB`;
- new LOW_MEMORY exits: `0`;
- ANR/crash/OOM: `0 / 0 / 0`;
- thermal status: `0`;
- battery: `100%`, approximately `24.9 C` to `25.7 C` during sampling.

## H20 health and rollback

Before STOP:

- documents transformed/fail-closed: `10 / 1`;
- SELF_READY accepted/rejected: `10 / 0`;
- self-shield release/parser continuation: `10 / 10`;
- document transform outstanding: `0`;
- H19 activity: all `0`.

After STOP:

- proxy/cache cleared and lab CA removed;
- VPN attestation revoked and normal VPN routes restored;
- transport `inactive/ready`;
- owned FD resources / active protected UDP sockets: `0 / 0`;
- document/ready-token outstanding: `0 / 0`;
- Device Owner and Glosh Accessibility remained present;
- Chrome/Glosh CE inodes remained `6090 / 1239519`;
- `resetCount=3`;
- terminal `MemAvailable` approximately `1.27 GiB`, thermal status `0`.

## Required follow-up

Do not tune R3.1 or H20 speculatively. A focused Fravega CPU attribution should
compare the same page and interaction window with H20 ON versus an authorized
H20 OFF control, while mapping renderer PID/lifecycle and document-shield work.
The final 30-minute performance/normality closure remains pending.
