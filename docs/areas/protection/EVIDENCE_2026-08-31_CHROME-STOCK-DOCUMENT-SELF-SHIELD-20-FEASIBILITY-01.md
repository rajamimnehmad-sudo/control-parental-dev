# CHROME-STOCK-DOCUMENT-SELF-SHIELD-20-FEASIBILITY-01

## Result

`BLOCKED` — `DOCUMENT_SELF_SHIELD_RELEASE_LIVENESS_FAILURE`.

H20 retained the security-side invariants in the controlled A23 run: the document was transformed, `SELF_READY` was accepted, Replace-All replaced every admitted network visual, and raw delivery remained zero. The controlled document nevertheless never became usable: Chrome showed a blank white content area, the original fixture script was never requested, and text/layout/photos never appeared. Per the ticket, Google Images, Frávega, and Mimo were not run after the controlled gate failed.

## Refs

- Base: `1ddc69fb6c1e5c538de9c268ee31ceb26e7e7f9d`
- Functional: `f4214146838abf10b7f4b290f2bff021bad80c96`
- Branch: `work/chrome-stock-document-self-shield-20-feasibility-01`
- APK: DEV407, `app-user/build/outputs/apk/dev/debug/app-user-dev-debug.apk`
- APK SHA-256: `29600893b53d429d040c41442c26b43bc6d68398df737fccfd379f1e4ac147e7`
- Signing certificate SHA-256: `d51bc0dabd280ce1b0f098ae168eb57758faeba301156cde835737835f8a8832`

## Implemented delta

The H20 curtain stylesheet is now immutable after nonce-authorized installation. Release uses only the exact document-owned `data-glosh-h20-curtain-released=1` state. Author markup is stripped of that attribute, author mutation APIs cannot set/remove it, and the observer restores current state. This avoids mutating the nonce-cleared protected stylesheet or toggling its media after `SELF_READY`.

## Automated validation

PASS:

- H20/bootstrap/static-markup/document transformer/READY endpoint/document admission/CSP tests.
- Byte Gate and controlled stock-media fixture tests.
- `ChromeImageContentAuthorityTest`, `ChromePhotosProxyRequestTest`, `ChromePreRenderDocumentTransformerTest`, active-document replay regressions, and `GloshiaVisualParityTest`.
- `ktlintDevSourceSetCheck`.
- `compileDevDebugKotlin`, `lintDevDebug`, and `assembleDevDebug`.
- `git diff --check`.

`ktlintTestDevSourceSetCheck` remains red only for the pre-existing, unrelated `ChromeHttp1ResponseWriterTest.kt:133:59` body-expression formatting debt. No touched file appears in that report.

## A23 preflight

- Device: `SM-A235M`.
- Android: 14 / API 34.
- Chrome: `152.0.7977.64` (`versionCode=797706404`).
- Glosh: DEV407 update-in-place; data inode remained `1239519`.
- Device Owner: `com.contentfilter.user.dev`, Affiliated.
- Accessibility: `ProtectorAccessibilityService` enabled and bound.
- Model/policy/data-plane ready before navigation.

## Controlled physical gate

First controlled generation:

- transformed document: `1` (`documentSequence=1`, `navigationSequence=1`);
- transform fail-closed: `0`;
- `SELF_READY`: requests/accepted/rejected = `1/1/0`;
- network visuals: candidates/replaced/raw = `2/2/0`;
- raw BLOCK/UNKNOWN = `0/0`;
- failures/proxyQueueRejects/protectFailure = `0/0/0`;
- QUIC/direct TCP attempts = `0/0`;
- original fixture requests: documents/styles/scripts/reports = `1/1/0/0`.

A second current document generation observed during the same bounded diagnostic also reached transformed + accepted `SELF_READY`, while the fixture script count remained `0`. Thus the result is repeatable inside the single physical session and is not an isolated endpoint rejection.

Visible result:

- no global gray overlay remained;
- Chrome chrome/address bar stayed normal;
- content viewport stayed blank white;
- text/layout were not navigable;
- photos were not progressively presented;
- the controlled gate is FAIL.

The external-only recording was 40.960 seconds at 720x1280. Sampling at 1 fps produced 41 frames: controlled unsafe-sentinel-like frames = `0`; safe-fixture frames = `0`; opaque-surface frames = `0`. This establishes zero observable unsafe sentinel at that sampling resolution, but does not compensate for the usability failure.

Artifact hashes before cleanup:

- recording: `309e698015eeb571b2f1556861f96848b9d1f3c93be977b4b6183bb1d79bc610`;
- initial screenshot: `0208ded336b46bf317596dae7e769876ce013222c385cd1fb9bc07b4dbb5a674`;
- late screenshot: `6af1ff7ebfcc50088fb04bded4c306f7f4ee94bc53d33cd2aec2e8fb3faa4073`.

Raw recordings/screenshots were deleted after hashes and metrics were extracted.

## Gate decision

The native acceptance event proves that the issued document identity and runtime health were current. It does not prove that the document-owned curtain was effectively retired and the original parser/render path reached usable completion. In both observed generations, the terminal original script was never requested (`SCRIPTS=0`), while the page stayed blank.

This is a new causal blocker after `SELF_READY`, not an H19 foreground-binding failure and not a Byte Gate/model failure. No H19 active-tab handshake, Accessibility marker, external-overlay release authority, screenshot authority, model, or threshold change was introduced.

## Deferred real-web gates

- Google Images `mujer`: NOT RUN — controlled gate prerequisite failed.
- Frávega: NOT RUN — controlled gate prerequisite failed.
- Mimo: NOT RUN — controlled gate prerequisite failed.
- Physical cross-tab release gate: NOT RUN — controlled usability prerequisite failed; deterministic document-ownership tests remain PASS.

## Health and rollback

- crash/ANR/OOM observed during the session: `0/0/0`;
- `readyTokensOutstanding=0` after STOP;
- lab service stopped;
- proxy stopped and cache cleared;
- CA removed;
- `ownedFdResources=0`;
- `activeProtectedUdpSockets=0`;
- `transportRuntime=ready`;
- Device Owner/Affiliated/Accessibility/data inode preserved;
- `stay_on_while_plugged_in` restored to `7`;
- display returned to the initial dozing state;
- no Chrome profile/cache/history reset was performed.

## Residual / next route

Instrument a generation-bound client completion sequence after the accepted 204 to distinguish: (a) XHR return, (b) installer retirement, (c) release-attribute application, (d) dialog close, (e) parser continuation, and (f) first original token/script execution. Do not infer completion from native `SELF_READY` acceptance alone, and do not proceed to real web until the controlled page visibly presents text/layout and its original script executes.
