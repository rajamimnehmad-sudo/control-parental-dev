# CHROME-VISUAL-REGION-DISCOVERY-13B-R2A-CAPTURE-ADMISSION-01

## Status

`BLOCKED` on a causally distinct Raster Provenance/oracle mismatch after the
capture-admission defect was corrected.

DEV368 admitted an Android window capture without
`ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT`, with an exact current
attestation/capture generation, and reached the Region Discovery planner and
R3.1 inference. The resulting centered-safe observation was `Complete` but did
not match the oracle, while the diagnostic Raster Provenance classifier
returned `UNKNOWN/no_unique_signature`. Per ticket contract, the remaining R2A
matrix was not run and no planner, mapping, provenance, GloshIA, threshold, or
release-authority change was attempted.

R2A remains fail-closed and this evidence does not authorize R2B.

## Refs and artifact

- Base: `4b964b4566fdb47f11d7c84ffc24b4caecfc7423`
- Functional SHA: `8e61de77f5e1f3794fe73ecae99f5ab098915ffc`
- Work branch:
  `work/chrome-visual-region-discovery-13b-r2a-capture-admission-01`
- Review branch:
  `review/chrome-visual-region-discovery-13b-r2a-capture-admission-01-triage`
- Version: DEV368 (`versionCode=368`)
- APK bytes: `159090485`
- APK SHA-256:
  `c3285286af81954ec4f6992696ce37bd13c9504bce46c655991e22450a2df165`
- GloshIA Visual R3.1 model SHA-256:
  `c8b64af8092d3718c58736a511c996d0d443dacf3eaa74620b1e5af439a3cd48`

The functional delta is limited to:

- `ChromeWindowCapture.kt`;
- new `ChromeWindowCaptureAdmission.kt`;
- new focused `ChromeWindowCaptureAdmissionTest.kt`;
- DEV `versionCode`.

It does not change the R2A handshake/binding, planner, Complete/Unknown
contract, Raster Provenance observer, GloshIA/model/policy, release authority,
11B, or VPN.

## Admission contract

`ChromeWindowCaptureAdmission.Shared` serializes real platform calls per
`accessibilityWindowId` across capture-controller instances.

- The first call is immediately eligible.
- The next call for that window is eligible at the previous real platform call
  plus 334 ms.
- A wait is cancellable and performs no platform call if the work becomes
  stale, superseded, or stopped.
- Only the latest pending request for a window can reach Android.
- Different windows are independent.
- The timestamp is recorded immediately before the Android call and is not
  rolled back by cancellation, completion, callback failure, or exception.
- Android `errorCode=3` is fail-closed and is never retried.

The first implementation used `SystemClock.elapsedRealtime()`. A physical
request pair logged 335 elapsed milliseconds but Android still returned error
3. Android's accessibility service enforces this interval with
`SystemClock.uptimeMillis()`, so elapsed time can overstate the interval seen by
the framework. The final delta uses the same uptime clock and logs
`requestedAtUptimeMs`.

Framework reference:
`https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/accessibility/java/com/android/server/accessibility/AbstractAccessibilityServiceConnection.java`

## Deterministic admission validation

Focused tests cover:

1. first capture immediate;
2. same window at +100 ms waits;
3. +333 ms remains ineligible;
4. +334 ms invokes exactly once;
5. cancellation during wait makes no call;
6. cancellation after the call preserves the timestamp;
7. different windows are independent;
8. among multiple pending requests only the latest reaches Android;
9. completion does not move eligibility;
10. unexpected error 3 produces one call and zero retries;
11. STOP-like cancellation during wait makes no later call.

`ChromeWindowCaptureOwnershipTest` additionally remained PASS for late
callback/resource close, cancel-before-callback, normal ownership transfer,
failure without a frame, and idempotent close.

Automated gates before the final APK:

- admission and capture ownership tests: PASS;
- inherited Region Discovery, epoch-binding, stale-generation liveness,
  anti-feedback, Raster Provenance, and Visual Shield tests: PASS;
- GloshiaVisualParityTest: PASS;
- focused 11A/11B and DEV fixture/provenance regressions: PASS;
- `:app-user:compileDevDebugKotlin`: PASS;
- `:app-user:lintDevDebug`: PASS;
- `:app-user:assembleDevDebug`: PASS;
- touched test-source ktlint task: PASS;
- touched production files: no ktlint delta violations;
- `git diff --check`: PASS.

The aggregate feature main-source ktlint task still reports only the known
baseline violations in untouched Visual Shield controller/lab files. No global
reformat was performed.

## Physical preflight

- Device: Samsung SM-A235M (A23), Android 14 / API 34.
- Chrome: `152.0.7977.64`, versionCode `797706404`.
- DEV368 installed update-in-place; signature and data preserved.
- `ceDataInode` before/after: `1239519`.
- Device Owner/Affiliated preserved.
- Accessibility enabled and bound, with no crashed service in the gate window.
- SAFE sample: 8090 bytes,
  SHA-256 `541a1ef5373be3dc49fc542fd9a65177b664aec01c8d8608f99e6ec95577d8c1`.
- BLOCK sample: 146249 bytes,
  SHA-256 `9f0d22f322d06dd08a8a349b628de5136c66ee6ef601d8c9492e0e286120ff94`.

A preflight command while the notification shade held focus returned
`chrome_absent`, `session=0`, and all work/resource counters zero. The valid
sessions started only after Chrome was the focused window.

## Platform request timeline

### Physical cycle 1: clock mismatch isolated

- C1: `requestedAtElapsedMs=166154242`, first request.
- C1 was cancelled by a real generation invalidation.
- C2: `requestedAtElapsedMs=166154577`, reported interval 335 ms.
- Attestation/capture matched at E51/R51.
- Android returned `errorCode=3`; planner/inference remained zero.
- Terminal ownership: full frame 1/1/0, crop 0/0/0, inference outstanding 0.

This remained the same capture-admission root cause and led to the clock
alignment above.

### Physical cycle 2: corrected uptime admission

The valid corrected session took the direct route after a pre-claim generation
replacement, so only the current generation issued a platform request:

- one legitimate stale-generation signal for E49/R49 while native was E50/R50;
- replacement binding E51/R51;
- attestation E51/R51;
- capture E51/R51;
- C1 platform call:
  `requestedAtUptimeMs=166510338 intervalMs=first`;
- `errorCode3=0`;
- Android returned a 1080x2408 frame;
- the pipeline derived one 756x722 crop and ran one R3.1 inference.

Handshake counters stabilized at:

- `identityRequests=3`;
- `beginFixtureRenderCount=3`;
- `generationInvalidations=1`;
- `generationReplacements=1`.

The exact security binding passed:

- attested contentEpoch = capture contentEpoch = `51`;
- attested regionSequence = capture regionSequence = `51`;
- viewportEpoch = `1`;
- render-key digest =
  `3944b709721e1f273fb4d4250a0d220c21f8b9eea1d8475e7cb91ad2f8bde42e`.

## Distinct blocker after admission

Raster Provenance observed:

- full frame: 1080x2408;
- crop: 756x722;
- crop SHA-256:
  `fc04975184be790f1a176f55cbd569624670536a621f6f7a52b7bab1e12c273e`;
- surface-marker pixels: `0`;
- crop card pixels: `5767`;
- canvas-neutral fraction: `0.3832168139647364`;
- draw-foreign fraction: `0.9267399267399268`;
- mapping delta: `none`;
- classification: `UNKNOWN`;
- basis: `no_unique_signature`.

The unchanged planner then observed:

- result: `Complete`;
- sequence: `1`;
- observed digest:
  `590c3db994cfaeeedbc1eaae8de2a616f0b59b095dffd56abaac948e492c81d8`;
- one region: `[103,42,653,593]`;
- R3.1 decision: `Allow/model_allow`;
- probability: `0.024400145`;
- `regionOracleMatch=false`;
- accepted region-set authority was not published;
- `regionDiscoveryCompleted=false`.

This is causally after capture admission and exact epoch binding. It is not an
error-3, stale-generation, ownership, or model-load failure. The ticket
requires STOP on `UNKNOWN`/oracle mismatch after a successful capture, so the
landscape, BLOCK, off-center, multi, ambiguous, rotation, and stress matrix was
not executed.

## Fail-close, ownership, and health

Throughout the corrected session:

- `NEVER RELEASE`: preserved;
- `rawPresented=false`;
- `releaseCurrent=0`;
- `labReleaseCount=0`;
- surface remained protected until explicit STOP.

Terminal ownership:

- `fullFrameAcquired=1`, `fullFrameClosed=1`, `fullFrameOutstanding=0`;
- `cropCreated=1`, `cropClosed=1`, `cropOutstanding=0`;
- `inferenceStarted=1`, `inferenceCompleted=1`,
  `inferenceOutstanding=0`;
- `workIdle=true`.

Health in the valid corrected window:

- crash/ANR/OOM = `0/0/0`;
- data-plane failures/proxyQueueRejects/protectFailure = `0/0/0`;
- QUIC/direct TCP bypass = `0/0`;
- no new abnormal Chrome or Glosh exit; Chrome renderer exits at rollback were
  `ISOLATED_NOT_NEEDED`, not crashes.

## Rollback

- Visual Shield explicit STOP: `Inactive`, all outstanding resources zero.
- Data plane explicit STOP: proxy cleared, CA removed, cache cleared.
- Chrome suspended fail-close on data-plane stop.
- `ownedFdResources=0`.
- `activeProtectedUdpSockets=0`.
- `transportRuntime=ready`.
- Device Owner, Affiliated, Accessibility, app data, signature,
  `ceDataInode=1239519`, and rotation settings were preserved.
- No adb reverse remained active.

## Residual / next route

`BLOCKED`: capture admission is corrected and physically reaches the planner,
but centered-safe ends in `UNKNOWN/no_unique_signature` plus
`regionOracleMatch=false`. The next ticket must review this new Raster
Provenance/oracle/planner-boundary evidence before any R2A matrix or R2B work.
This ticket does not select or implement that next architecture.
