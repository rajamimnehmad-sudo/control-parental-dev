# CHROME-VISUAL-REGION-SET-AUTHORITY-13B-R2B-CLOSURE-01

## Status

`BLOCKED` — the atomic R2B authority and its deterministic matrix pass, but the
final A23 matrix exposed a causally distinct R2A presentation-ordering failure.
The shield remained fail-close. R2B is not eligible for final review or real-web
enablement.

## Refs and artifact

- Functional base: `999a5cc2a3982bad0fad3f34baeba54b30d4fd8f`
- R2A canonical review: `892154613db1888046472c450ad8281a512213c3`
- Atomic authority commit: `342ff3b2af485a4bc70d7243615a8be97b432ffa`
- Oracle multiplicity correction: `f7d5357bbd0a62bd24c53d6761bada100b7a46a1`
- Version: DEV376 (`versionCode=376`)
- APK: `app-user/build/outputs/apk/dev/debug/app-user-dev-debug.apk`
- APK bytes: `159106897`
- APK SHA-256: `3f049816a0431f02a93aefb8dead7347d5fca06433c14e1f907b66824ae66fb0`
- GloshIA Visual R3.1 SHA-256:
  `c8b64af8092d3718c58736a511c996d0d443dacf3eaa74620b1e5af439a3cd48`

## Authority contract implemented

`ChromeVisualShieldRegionSetAuthority` accepts only a non-empty `Complete`
batch whose full identity is current: protection session, window, content and
viewport epochs, capture and region sequences, discovery sequence, and exact
region-set digest. It recomputes the digest, structurally compares the ordered
region set, and requires exactly one correctly stamped decision per candidate.

Release is possible only when every decision is `Allow/model_allow`. Partial
SAFE, BLOCK, Unknown, missing/extra/duplicate decisions, candidate mismatch,
digest/sequence/identity drift, cancellation, STOP, internal failure, replay,
or surface mismatch remains protected. The authority enters a
`RegionSetReleasePending` identity phase and rechecks both current identity and
surface immediately before the one-shot whole-surface release. Its replay state
is bounded to the last released batch. The DEV oracle is evaluated only after
the authority outcome and is not an input to release.

## Files changed

- New cohesive authority and tests:
  `ChromeVisualShieldRegionSetAuthority.kt`,
  `ChromeVisualShieldRegionSetAuthorityTest.kt`.
- Minimal identity, processor, lab, controller, DEV receiver/fixture, manifest,
  and version wiring.
- A localized evidence correction preserves repeated source multiplicity in
  oracle comparison. This is required for the two-region `multi-all-safe`
  fixture and does not participate in authority.

## Automated matrix

PASS:

- Complete + one current SAFE releases exactly once.
- Multiple ALL SAFE releases only after the final decision and exactly once.
- SAFE+BLOCK, all BLOCK, and Unknown never release.
- Missing, extra, duplicate, mismatched candidate, region, digest, or discovery
  sequence never release.
- Every stale identity field, new epoch/viewport/window, cancellation, STOP,
  late delivery, surface race, and exception fails closed.
- Replay of the same batch cannot release twice; a new batch first returns to
  protection and requires a new exact Complete + ALL SAFE decision set.
- Old-batch history is bounded.
- Repeated oracle source SHA multiplicity is preserved and has a deterministic
  regression.

Commands/gates that passed across the final code delta:

- focused R2B authority and DEV fixture tests;
- full `:feature-accessibility:testDebugUnitTest`;
- `:gloshia-visual-core:testDebugUnitTest`;
- focused Chrome Visual Shield, GloshIA parity, R2A and 11B app tests;
- `:feature-accessibility:compileDebugKotlin`;
- `:app-user:compileDevDebugKotlin`;
- `:app-user:lintDevDebug`;
- `:app-user:assembleDevDebug`;
- `git diff --check`.

The delta test source passes ktlint. The repository-wide feature main ktlint
task still reports only the already-recorded pre-existing lines in
`ChromeVisualShieldController.kt` and `ChromeVisualShieldRegionDiscoveryLab.kt`;
the new authority, oracle change, and tests add no ktlint violation.

## A23 preflight and preservation

- Device: SM-A235M, Android 14 / API 34.
- Chrome: `152.0.7977.64` (`versionCode=797706404`).
- Update in-place: PASS; package data inode remained `1239519`.
- Device Owner: preserved; owner is reported `DeviceOwner,Affiliated`.
- Affiliation state remained the baseline `mAffiliationIds={}`.
- Accessibility service remained enabled and bound.
- SAFE source: 8090 bytes,
  `541a1ef5373be3dc49fc542fd9a65177b664aec01c8d8608f99e6ec95577d8c1`.
- BLOCK source: 146249 bytes,
  `9f0d22f322d06dd08a8a349b628de5136c66ee6ef601d8c9492e0e286120ff94`.

## Physical matrix on final APK

| Case | Discovery / oracle | R3.1 | Authority | Evidence |
|---|---|---|---|---|
| SAFE portrait | Complete / true | Allow, `model_allow`, 0.024400145 | Released, exact digest `4ce8b98a…`, raw-before-authority=false | PASS |
| SAFE landscape | Complete / true | Allow, `model_allow`, 0.025322706 | Released, exact digest `9c97229f…`, raw-before-authority=false | PASS |
| BLOCK portrait | Complete / true | Block, `model_filter`, 0.93479085 | BlockProtected, release unchanged, raw-before-authority=false | PASS |
| BLOCK landscape, rotation generation | Complete / true | Block, `model_filter`, 0.94701016 | BlockProtected, release unchanged | PASS |
| BLOCK landscape, fresh session | Unknown / false | no inference | UnknownProtected, release unchanged | **BLOCKER** |

The fresh landscape failure is not an R2B release error. Its accepted
attestation and capture match exactly at `contentEpoch=132`,
`viewportEpoch=13`, `regionSequence=132`, `captureSequence=7`. The full-frame
fingerprint and the 1631x316 crop show the expected search envelope, but the
crop is 515396/515396 Canvas-neutral pixels (`#202428`), with no card cluster,
no draw content, and no protected-surface lattice. Crop SHA-256:
`a166a2e6265ad35acf2949c2d4511f530f28e6dca4e91ca5a00f765180f43d37`.

This is consistent with `CANVAS_PRE_DRAW`: the attested generation was current,
but the renderer-local draw had not reached the captured raster. The diagnostic
classifier emitted `UNKNOWN/no_unique_signature` because it did not assign a
unique signature in that frame. Fixing this requires reopening R2A
presentation/commit ordering, which is outside this R2B ticket and is an
explicit STOP condition.

The final matrix stopped at this first distinct blocker. Multi ALL SAFE,
SAFE+BLOCK, ambiguous, replay, and STOP remain covered deterministically, but
were not claimed as final physical PASS after this failure. An earlier DEV376
artifact before the evidence-only multiplicity correction physically showed
multi SAFE+BLOCK correctly protected and multi ALL SAFE released after both
allows; those observations are diagnostic only, not substituted for a final
APK gate.

## Exposure evidence

The completed final cases reported `rawPresentedBeforeAuthority=false`.
SAFE batches released only after `authorityAcceptedAtNanos`, followed by
`regionSetReleaseAtNanos`. BLOCK and Unknown remained protected. Physical
recordings were held only long enough to extract bounded metadata/hashes and
were not committed or uploaded:

- SAFE portrait: 720x1280, 66 frames, SHA-256 `fd0205ca…`.
- SAFE landscape: 1280x720, 64 frames, SHA-256 `1afe3641…`.
- BLOCK portrait: 720x1280, 55 frames, SHA-256 `14b1781a…`.
- BLOCK landscape blocker: 1280x720, 63 frames, SHA-256 `a7ec1681…`.

No zero-exposure physical PASS is claimed for the incomplete final matrix.

## Ownership, health, and rollback

At the blocker terminal and after STOP:

- `fullFrameAcquired=7`, `fullFrameClosed=7`, `fullFrameOutstanding=0`;
- `cropCreated=7`, `cropClosed=7`, `cropOutstanding=0`;
- `inferenceStarted=6`, `inferenceCompleted=6`, `inferenceOutstanding=0`;
- `workIdle=true`;
- crash / ANR / OOM = `0/0/0` in the final physical window;
- data-plane failures/proxyQueueRejects/protectFailure = `0/0/0`;
- QUIC/direct-TCP attempts = `0/0`;
- rollback transport: `inactive`, `ownedFdResources=0`,
  `activeProtectedUdpSockets=0`, `transportRuntime=ready`;
- Chrome sandbox exits at rollback were Android `ISOLATED NOT NEEDED`, not
  crash/ANR/OOM;
- Device Owner, affiliation status, Accessibility, signature/data inode were
  preserved.

## Residual / next route

R2B authority code is reviewable, but the ticket is `BLOCKED` because R2A does
not yet provide a stable rendered-raster barrier in a fresh landscape session.
The next delta must be an R2A presentation/commit-ordering diagnosis and fix,
state-driven and generation-bound; it must not use sleep, debounce, blind retry,
planner relaxation, oracle authority, or GloshIA/model changes. After that fix,
the complete R2B physical matrix must be rerun on one final APK.
