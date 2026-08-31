# CHROME-STOCK-MEDIA-AUTHORITY-19 — active-document handshake

## Status

`BLOCKED / FOREGROUND_ACTIVE_DOCUMENT_FOCUS_UNAVAILABLE`

The cryptographic HELLO → CHALLENGE → PROVE → PRESENT implementation remains
fail-close, but stock Chrome 152 on the A23 did not grant
`Document.prototype.hasFocus()` at the parser-first acquisition boundary. The
controlled cold document therefore emitted no HELLO and the external protected
surface was never released.

This result does not reopen screenshot/compositor authority and does not change
GloshIA R3.1, its model, policy, labels, or thresholds.

## Refs

- Functional base: `3e2a4540fa55e00cc831c81144bfb532a953afb1`
- Functional diagnostic head: `1ddc69fb6c1e5c538de9c268ee31ceb26e7e7f9d`
- Work branch: `work/chrome-stock-media-authority-19-active-document-handshake-01`
- APK: DEV403, versionCode `403`, versionName `1.0.1-dev`
- APK SHA-256: `ace2e5f6af0ef2a11b3a49443b21ef362f9e11abe01eafba66d09268af1b2a14`
- APK signer SHA-256: `d51bc0dabd280ce1b0f098ae168eb57758faeba301156cde835737835f8a8832`
- GloshIA: Visual R3.1 / policy `dag-36`
- Model SHA-256: `c8b64af8092d3718c58736a511c996d0d443dacf3eaa74620b1e5af439a3cd48`

## Device

- A23: `SM-A235M`
- Android: 14 / API 34
- Chrome: `152.0.7977.64`
- Device Owner: preserved
- Affiliated: preserved
- Accessibility: bound and preserved
- `ceDataInode`: `1239519` before install, after update, and after gate
- Install: `adb install -r`, same signer, no clear-data

The DEV gate depends on Device Owner, Accessibility, and the local data-plane;
opening the Glosh user UI or signing into an end-user account is not required
for this controlled laboratory authority test.

## Implemented authority

The branch implements a current-only cryptographic protocol whose document
claim includes protection session, policy epoch, navigation sequence, document
sequence, lifecycle, and a closure-only token. Native code retains the current
Chrome window, root, protected-surface identity, and a one-shot challenge.

Release requires:

1. top-level document;
2. captured native `visibilityState === visible`;
3. captured native `document.hasFocus() === true`;
4. exact current claim and one-shot challenge;
5. current Chrome window/root/surface and healthy data-plane;
6. successful PRESENT followed by the native transparent commit.

Hidden/background/stale/replay claims remain fail-close. The token and
challenge are not placed in DOM, ARIA, URL, or raw logs.

## DEV403 focus acquisition delta

The controlled bootstrap captures the native `HTMLElement.prototype.focus`
before original site code can execute. At the parser barrier it performs, once:

`opaque internal curtain → focus(curtainLayer, preventScroll) → native
activeDocument recheck → arm authority → HELLO`

The focus call runs while `authorityArmed=false`, preventing a synchronous
trusted focus event from entering HELLO reentrantly. If focus throws, is a
no-op, or the native recheck stays false, the existing parser guard replaces
the document fail-close. No timer, sleep, debounce, polling, or retry is used.

## Automated validation

- H19 Python harness: `131` tests PASS.
- Parser/bootstrap focal test: PASS on DEV403.
- Full module unit aggregate from the same H19 implementation before the
  focus-only delta: PASS (`362` tasks).
- `compileDevDebugKotlin`: PASS.
- `lintDevDebug`: PASS.
- `assembleDevDebug`: PASS (`821` tasks, 4m50s).
- `git diff --check`: PASS.
- DEV and testDev ktlint reports contain no finding in either file changed by
  the focus delta. The aggregate tasks still report pre-existing findings in
  unrelated DEV/testDev files, including `ChromeHttp1ResponseWriterTest.kt`.

## Physical cold-foreground timeline

One valid DEV403 Replace-All session was executed on the connected A23.

| Time (device) | Evidence |
| --- | --- |
| 06:08:23 | Replace-All session active, stock media authority true, full-tunnel DEV, R3.1 model hash exact. |
| 06:08:50.483 | Controlled HTML transformed, documentSequence=1, navigationSequence=1. |
| 06:08:50.544 | Native parser barrier ready for Chrome windowId=1789. |
| 06:08:50.546 | Parser-barrier response delivered. |
| 06:10:06.524 | Terminal snapshot: parserBarrierRequests=1, parserBarrierReady=1, readyRequests=0, claims=0. |
| 06:10:06.511 | Protocol snapshot: activeHello=0, challengeIssued=0, proofAccepted=0, presentAccepted=0, releaseCurrent=0. |
| 06:13:16 | Bounded STOP; proxy, CA, cache, and VPN lease rolled back. |

The runner returned `cold_release_timeout`. The later transformed navigation
showed the same shape; terminal STOP totals were:

- documents transformed: `2`;
- parser barrier requests/ready: `2/2`;
- ready requests/preflights/accepted/rejected: `0/0/0/0`;
- activeHello/challenge/proof/present: `0/0/0/0`;
- releaseCurrent/crossTabRelease: `0/0`.

This locates the blocker before the native READY endpoint. The parser barrier,
document transformer, native Chrome window/root observation, and network Byte
Gate all ran. The bootstrap could not satisfy the required active-document
predicate even after the protected curtain received the native focus call.

## Byte Gate preservation

At terminal STOP:

- network visual candidates/replaced/raw: `4/4/0`;
- raw BLOCK delivered: `0`;
- raw UNKNOWN delivered: `0`;
- failures: `0`;
- proxyQueueRejects: `0`;
- protectFailure: `0`;
- QUIC/direct TCP bypass: `0/0`.

The previously reviewed wider H19 Byte Gate remains preserved; this ticket did
not change its decision semantics.

## Why visibility alone is not sufficient

`visibilityState === visible` can prevent an already hidden document from
starting HELLO, but native Android still sees the same Chrome package,
windowId, and Accessibility root across tabs. A tab switch can occur after the
last JavaScript visibility check and before native PRESENT releases the external
overlay. Without a browser-side tab/document identifier or a presentation
fence, native code cannot prove which tab owns the claim at that final boundary.

Promoting visibility, timing, URL text, title, Accessibility text/viewId scans,
or network-process identity would therefore weaken the exact-current contract.
Those signals are intentionally not promoted.

The following non-raster routes are exhausted or prohibited by prior physical
evidence and the current contract:

- Accessibility marker/focus event source and passive tree scans;
- page title or address-bar text identity;
- package/window/root identity without a tab/document identity;
- URL/body/network-service correlation;
- screenshot/compositor/presentation-marker authority;
- CDP/DevTools or Chrome extension dependency.

## Health and rollback

- app crash/ANR/OOM: `0/0/0`;
- Chrome crash/ANR/OOM: `0/0/0`;
- `documentTransformOutstanding=0` at terminal STOP;
- `readyTokensOutstanding=0` at terminal STOP;
- `ownedFdResources=0`;
- `activeProtectedUdpSockets=0`;
- `transportRuntime=ready`;
- Device Owner/Affiliated/Accessibility/inode preserved;
- H19 service absent after cleanup.

One diagnostic `alphaSubmitFailures=1` was observed after the cold timeout. It
cannot explain `readyRequests=0`, because no HELLO reached native authority; it
was not expanded after the binding gate had already failed.

Temporary evidence hashes:

- preinstall JSON: `07375f15090ac477b251ac027b97a559ac34c454e9b0b08859ba132cad18feac`
- postinstall JSON: `5573a0a011d9027a907af3df21b60451bf24e1e28791815f93fa4ae9bfc779f1`
- postgate JSON: `ba3dfe27cb4e488247afe8c2d57cdf5b9c511265bdcc76dbef37a31c93abeaf3`
- cold log window: `027a1acdaec382d523da59f9e3b89c0f68cd0efec17bf3d51905e64c6cb8cee8`

No screenshot, crop, page body, cookie, raw URL, token, or challenge is stored.

## Not run

The remaining 15 active-document cases and all real-web/selective gates were
not run, as required after the cold authority gate failed:

- background/cross-tab/reload/BFCache/rotation/replay stress;
- controlled selective R3.1;
- Google Images, Frávega, Mimo;
- scroll/rotation cache and Chrome normality.

## Residual / next route

Stock Chrome currently supplies no demonstrated non-raster, browser-side
document/tab identifier that native Android can bind to the exact foreground
window at PRESENT while keeping the parser blocked and Chrome UX normal.

Continuing this architecture requires one of:

1. a newly demonstrated official Chrome/Android API that exposes exact
   tab/document identity to the Device Owner/accessibility boundary; or
2. control of the browser integration (owned renderer, supported managed
   extension/API, or equivalent product decision).

This evidence does not authorize Route B and does not declare Product Ready.
