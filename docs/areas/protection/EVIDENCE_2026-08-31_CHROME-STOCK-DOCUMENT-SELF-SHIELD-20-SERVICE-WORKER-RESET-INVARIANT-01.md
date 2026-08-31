# CHROME-STOCK-DOCUMENT-SELF-SHIELD-20 — Service Worker reset invariant

## Status

PASS diagnostic. The explicitly authorized one-time Chrome application-data reset removed the pre-existing Service Worker state, and the H20 bootstrap prevented a subsequent Service Worker registration.

This result validates the proposed product invariant only. It does not by itself complete selective R3.1 or declare H20 product-ready.

## Refs

- Functional base: `07112e2d`
- Functional delta: `01e4c800`
- Branch: `work/chrome-stock-document-self-shield-20-service-worker-reset-invariant-01`
- APK: DEV412
- APK SHA-256: `c6cc40d4b5640a12b03299d4e122b694177aa32c24b6a801e77f8dc7d6f3ba63`

## Automated validation

- `ChromeServiceWorkerBoundaryFixtureTest`: PASS
- `ChromePhotosTrustedBootstrapPolicyTest`: PASS
- `compileDevDebugKotlin`: PASS
- `lintDevDebug`: PASS
- `assembleDevDebug`: PASS
- `git diff --check`: PASS

## Physical gate

- Device: Samsung SM-A235M
- Android: 14
- Chrome: 152.0.7977.64
- Glosh: DEV412, update-in-place
- Reset arming result: `chrome_reset_armed`
- Reset completion: `chrome_reset_complete generation=2 resetCount=3`
- Subsequent starts: `chrome_reset_skipped generation=2 resetCount=3`
- Authorized Chrome application-data clear calls in this gate: exactly 1
- Other application/device-data clears: 0

The Chrome first-run UI reappeared after the reset, independently confirming that application data was cleared. The fixture then reported:

- `RESET_BASELINE_CLEAN=1`
- `RESET_BASELINE_DIRTY=0`
- `REGISTER_BLOCKED=1`
- `REGISTER_SUCCEEDED=0`
- `WORKER_SCRIPTS=0`
- `CONTROLLER_PRESENT=0`
- `NAV_FETCHES=0`
- `SELF_READY_FETCHES=0`

The visible controlled result was `SW_REGISTER_BLOCKED`.

## Authority and raw delivery

- H20 document transformed: 1
- SELF_READY accepted: 1
- Own curtain release completed: 1
- Parser continuation: 1
- Network visual candidates/replaced/raw: 7/7/0
- Raw BLOCK delivered: 0
- Raw UNKNOWN delivered: 0
- Proxy queue rejects: 0
- Protect failures: 0
- QUIC/direct TCP attempts: 0/0

The native bootstrap owns the reset generation. Re-running START did not clear Chrome again. A second DEV arm for the same current generation is rejected by the controller contract.

## Preservation and health

- Device Owner: preserved
- Affiliated: preserved
- Accessibility service: preserved and enabled
- Glosh `ceDataInode`: `1239519` before and after
- Chrome package `ceDataInode`: `6090` before and after; reset completion and the Chrome first-run UI are the reset evidence
- Crash/ANR/OOM: 0/0/0
- Final lab state: `phase=stopped rollback=complete cache=cleared`
- Chrome final state: suspended fail-close after lab STOP

No Glosh data, Device Owner state, Accessibility state, or other device/application data was deleted.

## Residual/product decision

The Service Worker escape found by the prior boundary ticket is closable in stock Chrome if product provisioning adopts both requirements as one invariant:

1. exactly one managed Chrome application-data reset when entering the new policy generation; and
2. parser-first prevention of all later `navigator.serviceWorker.register()` calls.

The compatibility cost is real: Chrome onboarding returns after the reset, and sites that require Service Workers may lose functionality. This diagnostic does not choose that product policy and does not reopen selective R3.1.
