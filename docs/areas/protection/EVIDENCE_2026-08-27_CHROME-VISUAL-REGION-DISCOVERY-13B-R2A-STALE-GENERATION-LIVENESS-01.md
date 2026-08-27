# CHROME-VISUAL-REGION-DISCOVERY-13B-R2A-STALE-GENERATION-LIVENESS-01

## Status

`BLOCKED`.

The stale-generation liveness delta is automated-test complete and the A23
demonstrated a one-shot state-driven invalidation followed by a fresh, exactly
matched attestation/capture generation. Gate H did not complete: the fresh
capture failed closed with Android Accessibility screenshot `errorCode=3`
(`ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT`). Per ticket contract, no retry,
sleep, debounce, planner work, inference, or matrix continuation was attempted.

R2A remains blocked. This evidence does not authorize R2B or release authority.

## Refs and artifact

- Base: `f369f1236901e50ef9e6c19503d7d6c16b916591`
- Functional SHA: `4b964b4566fdb47f11d7c84ffc24b4caecfc7423`
- Work branch: `work/chrome-visual-region-discovery-13b-r2a-stale-generation-liveness-01`
- Review branch: `review/chrome-visual-region-discovery-13b-r2a-stale-generation-liveness-01-triage`
- Excluded evidence-only base: `34f2f00bbc51c9dcb57c81eb13a0ceb8d5f3a71c`
- Version: DEV367 (`versionCode=367`)
- APK: `app-user/build/outputs/apk/dev/debug/app-user-dev-debug.apk`
- APK bytes: `159090485`
- APK SHA-256: `b351914907498bfa704684112f9b7c855ba6447dc7b0e4eae6b8d50ebc5921c5`
- GloshIA Visual R3.1 model SHA-256:
  `c8b64af8092d3718c58736a511c996d0d443dacf3eaa74620b1e5af439a3cd48`

The functional delta changes only the two DEV handshake/fixture files, their
two focused tests, and DEV versionCode. It does not touch the render binding,
Region Discovery planner, raster provenance observer, Visual Shield
controller, GloshIA, model/policy, release authority, 11B, or VPN.

`ChromeVisualShieldRegionDiscoveryFixture.kt` remains a single 551-line DEV
fixture protocol. Its HTTP endpoints and generated runner share one state
machine; splitting them in this narrow fix would scatter that contract. It is
below the repository's 800-line mandatory split-ticket threshold.

## Typed stale transitions

`claimAttestation()` now returns one of:

- `Claimed`
- `GenerationInvalidated`
- `Invalid`

`GenerationInvalidated` is emitted only for the exact active, structurally
valid, in-flight binding in the expected session/window when the native
generation has advanced. The active phase becomes `Invalidated`, making a
repeat POST invalid without emitting a second signal. Random, malformed,
foreign, missing-active, phase-mismatched, and binding-mismatched requests fail
closed as `Invalid`.

`executeAttestation()` now returns one of:

- `Accepted`
- `Rejected`
- `GenerationInvalidated`
- `StaleClaim`

A native-generation change after claim and before callback execution returns
`GenerationInvalidated` without invoking the native attestation callback.
Foreign or otherwise stale claims return `StaleClaim` and cannot request a new
render. Only the two legitimate invalidation results map to
`region_generation_invalidated`; the existing fixture transition then requests
one fresh generation. No timer is used as authority.

## Automated validation

All relevant automated gates passed before installing DEV367:

- focused DEV handshake, fixture, and attestation tests: PASS;
- exact pre-claim and post-claim generation transition regressions: PASS;
- repeated stale POST/claim produces no second invalidation: PASS;
- malformed/random and foreign session/window claims do not rerender: PASS;
- same generation/key duplicate x100 starts one render: PASS;
- fresh generation and normal claim/execute paths: PASS;
- stale binding cannot enable capture/planner/inference: PASS;
- STOP clears handshake state/signals: PASS;
- `:feature-accessibility:testDebugUnitTest`: PASS, including epoch binding,
  Region Discovery, raster provenance, Visual Shield, and ownership;
- `GloshiaVisualParityTest`: PASS with the model SHA above;
- focused app DEV regressions for Visual Shield, 11B authority/proxy,
  11A response writing, provenance fixture/routing, and resource transform:
  PASS;
- `:app-user:compileDevDebugKotlin`: PASS;
- `:app-user:lintDevDebug`: PASS;
- `:app-user:assembleDevDebug`: PASS;
- `git diff --check`: PASS.

The aggregate DEV/testDev ktlint source-set tasks still report only existing
violations in untouched Chrome authority/proxy/guard test files. None of the
five changed files appears in either report; delta ktlint is PASS. No unrelated
formatting was performed.

## Physical preflight

- Device: Samsung SM-A235M (A23), Android 14 / API 34.
- Chrome: `152.0.7977.64`, versionCode `797706404`.
- DEV367 installed update-in-place; signature/data preserved.
- `ceDataInode` before and after: `1239519`.
- Device Owner preserved: `com.contentfilter.user.dev`.
- Affiliation state preserved exactly: `mAffiliationIds={}`.
- Accessibility enabled and bound; crashed-services set empty before Gate H.
- Data plane: `PresentationReady`, Chrome unsuspended, failures=0,
  proxyQueueRejects=0, protectFailure=0, QUIC/direct TCP bypass=0/0.
- SAFE sample: 8090 bytes,
  SHA-256 `541a1ef5373be3dc49fc542fd9a65177b664aec01c8d8608f99e6ec95577d8c1`.
- BLOCK sample: 146249 bytes,
  SHA-256 `9f0d22f322d06dd08a8a349b628de5136c66ee6ef601d8c9492e0e286120ff94`.

An initial DEV command returned `result=chrome_absent` before any Visual Shield
session was created (`session=0`, all work/resource counters zero). Chrome was
then brought foreground. The single valid Gate H session began only when
`REGION_DISCOVERY_PROBE` returned `probe_started session=1`.

## Gate H generation timeline

The relevant state-driven route was:

1. Render/attestation/capture C1: E20/R20.
2. The native context advanced to E21/R21 while C1 was pending; C1 was
   cancelled and closed.
3. Exactly one endpoint result was logged:
   `region_generation_invalidated ... contentEpoch=20 regionSequence=20`.
4. The fixture requested one replacement for the unchanged render key.
5. Fresh render binding G2: E22/R22, `generationReplacements=1`.
6. Fresh attestation: E22/R22, oracle present.
7. Fresh capture C2: E22/R22.

The security match therefore passed:

- attested contentEpoch = capture contentEpoch = `22`;
- attested regionSequence = capture regionSequence = `22`;
- viewportEpoch = `1`;
- render key digest =
  `3944b709721e1f273fb4d4250a0d220c21f8b9eea1d8475e7cb91ad2f8bde42e`.

Handshake counters stabilized at `identityRequests=3`,
`beginFixtureRenderCount=3`, `generationReplacements=1`; the first two renders
correspond to distinct observed native/geometry states and the third to the
single stale-generation replacement. Two later status snapshots did not add
identity or render requests.

Coverage precision: the physical `region_generation_invalidated` above came
from the already accepted generation's native completion outcome. The new
pre-claim/post-claim classifier metric remained `generationInvalidations=0` in
this session, so those two typed branches are proven by deterministic tests but
were not themselves exercised on the A23. The ticket's allowed physical route
B was still observed end-to-end (one invalidation, one replacement, matching
fresh attestation/capture), but this run must not be cited as physical coverage
of both typed classifier branches.

## Physical blocker

Immediately after the exact E22/R22 capture binding was accepted, Android
returned:

`phase=region_discovery_capture errorCode=3 result=fail_close`

Consequences:

- `regionDiscoveryResult=none`;
- `regionDiscoveryCompleted=false`;
- `regionOracleMatch=null`;
- planner/inference never started (`inferenceStarted=0`);
- no Raster Provenance content classification was possible;
- the centered-safe Gate H did not reach `Complete/model_allow`;
- the remaining R2A matrix was not run.

This is not an epoch mismatch: the failed C2 was bound exactly to the current
attestation. The evidence is insufficient to prescribe a safe implementation
change. The next route must audit capture admission/serialization after a
cancelled generation and the platform's screenshot interval contract without
using a blind retry, sleep, debounce, or weakening invalidation.

## Fail-close, ownership, and health

Throughout the valid session:

- `NEVER RELEASE`: preserved;
- `rawPresented=false`;
- `releaseCurrent=0`;
- `labReleaseCount=0`;
- surface remained `Protected` until explicit STOP.

Terminal Visual Shield ownership:

- `fullFrameAcquired=1`, `fullFrameClosed=1`, `fullFrameOutstanding=0`;
- `cropCreated=0`, `cropClosed=0`, `cropOutstanding=0`;
- `inferenceOutstanding=0`;
- `workIdle=true`;
- `captureCancelled=1`.

Physical health:

- crash/ANR/OOM = `0/0/0` during the gate window;
- data-plane failures/proxyQueueRejects/protectFailure = `0/0/0`;
- QUIC/direct TCP bypass = `0/0`;
- no new abnormal Chrome or Glosh process exit was recorded.

## Rollback

- Visual Shield explicit STOP: `Inactive`, all outstanding resources zero.
- Data plane explicit STOP: proxy cleared, CA removed, cache cleared.
- Chrome suspended fail-close on data-plane stop.
- `ownedFdResources=0`.
- `activeProtectedUdpSockets=0`.
- `transportRuntime=ready`.
- Device Owner, affiliation state, Accessibility binding, app data, signature,
  and `ceDataInode=1239519` preserved.
- Device rotation settings restored to their preflight values.

## Residual / next route

`BLOCKED`: state-driven stale-generation recovery and exact generation binding
are demonstrated, but Gate H is blocked by a fresh-capture
`ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT` immediately after the cancelled
generation. Do not proceed to planner changes, the R2A matrix, R2B,
Fravega/Mimo, scheduler tuning, or video/GIF until ChatGPT reviews this evidence
and defines a narrowly scoped capture-admission ticket.
