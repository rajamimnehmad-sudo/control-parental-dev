# CHROME-STOCK-DOCUMENT-SELF-SHIELD-20 — LIVENESS CLOSURE

## Status

`BLOCKED` — `REAL_WEB_BOOTSTRAP_FAIL_CLOSED_PRE_SELF_READY`.

The reviewed `DOCUMENT_SELF_SHIELD_RELEASE_LIVENESS_FAILURE` is closed on the
controlled A23 fixture. H20 no longer creates a `<dialog>` or uses the top layer
for its document-owned curtain. The accepted `SELF_READY` is followed by an
exact one-shot sequence proving release completion, parser continuation, and
execution of the first original script.

The same final APK exposed a causally distinct real-web blocker. Frávega and
Mimo received transformed documents but their bootstraps failed closed before
`SELF_READY`; Chrome displayed `Glosh protected this document.` instead of a
usable page. No raw visual escaped. Per the authorized STOP condition, no
second APK or unrelated architecture change was attempted.

## Refs and artifact

- Base: `f4214146838abf10b7f4b290f2bff021bad80c96`.
- Functional: `e1b773db0f1020b69784e1ad9e3d9d2c71c6ee5b`.
- Work branch: `work/chrome-stock-document-self-shield-20-liveness-01`.
- APK: DEV408, versionCode `408`, versionName `1.0.1-dev`.
- APK SHA-256: `16fcfb0ee07170951f6fdbf77a887321feafb697def6db85ca5daf79aa34dd28`.
- Signing certificate SHA-256:
  `d51bc0dabd280ce1b0f098ae168eb57758faeba301156cde835737835f8a8832`.

## Liveness root cause and fix

The failing DEV407 path created a modal `<dialog>` while its parser-blocking
bootstrap was running in `<head>`, then used `showModal()` / `close()` even
though H20 release authority was already the document-owned CSS attribute.
DEV407 accepted `SELF_READY` twice but stayed blank with `SCRIPTS=0`.

DEV408 makes the H20 curtain purely parser-first CSS:

1. H20 requires the immutable curtain stylesheet and exact root attribute.
2. H20 never creates a dialog, enters the top layer, or invokes modal APIs.
3. H19 retains its historical modal path unchanged.
4. A bounded DEV-only tracker observes, but never authorizes, the exact
   generation-bound sequence:
   `RELEASE_COMPLETED -> PARSER_CONTINUED -> ORIGINAL_SCRIPT_STARTED`.
5. The parser-continuation sentinel is inserted immediately after the
   bootstrap and before any original source token.
6. The controlled document's first original executable invokes the final
   one-shot callback. Resource requests or preload-scanner traffic do not count.

Trace requests use a fixed local path, exact origin/fetch metadata, the current
document identity and the closure-only ready token. Replays, wrong ordering,
and cross-document identities are rejected. The tracker is bounded to 64
documents and grants no presentation authority.

`ChromeMediaShieldReadyEndpoint.kt` remains a cohesive fixed-origin protocol
handler; the new bounded transition state is extracted into
`ChromeMediaShieldSelfShieldLiveness.kt`. The generated bootstrap remains a
single security prelude because splitting the JavaScript template would weaken
the ordering review boundary; it is 766 lines after this delta.

## Automated validation

PASS:

- `ChromeMediaShieldSelfShieldLivenessTest`.
- `ChromeMediaShieldReadyEndpointTest`.
- `ChromeMediaShieldBootstrapContractTest`.
- `ChromeMediaShieldDocumentTransformerTest`.
- `ChromeStockMediaAuthorityFixtureTest`.
- `ChromeNetworkVisualDeliveryGateTest`.
- `ChromeMediaShieldStaticMarkupNeutralizerTest`.
- `ChromeMediaShieldDocumentAdmissionTest`.
- `ChromeMediaShieldCspPolicyTest`.
- `ChromePhotosDataPlaneLifecycleTest`.
- `ChromeImageContentAuthorityTest`.
- `ChromePhotosProxyRequestTest`.
- `ChromePreRenderDocumentTransformerTest`.
- `ChromeMediaShieldActiveDocumentReplayContractTest`.
- `GloshiaVisualParityTest`.
- `compileDevDebugKotlin`, `lintDevDebug`, and `assembleDevDebug`.
- `git diff --check`.

The production-source and test-source ktlint tasks remain red only for existing
debt in unrelated, untouched files. No changed H20 file appears in either
report. The known test debt is
`ChromeHttp1ResponseWriterTest.kt:133:59`; production debt is limited to the
pre-existing fixture/provenance/Chrome Guard files reported by the task.

## A23 preflight

- Device: `SM-A235M`, serial `R58T34V31AE`.
- Android: 14 / API 34.
- Chrome: `152.0.7977.64`, versionCode `797706404`.
- DEV408 installed update-in-place; data inode stayed `1239519`.
- Device Owner: `com.contentfilter.user.dev`, `Affiliated`.
- `ProtectorAccessibilityService`: enabled and bound after update.
- Runtime: model, policy, proxy, VPN and trusted bootstrap ready.
- Valid lab mode was explicitly confirmed before navigation:
  `mode=replace_all`, `selfShield=true`, `active=true`, `ready=true`.

Android initially resumed the persisted pre-update lab mode
(`selective/selfShield=false`). That preflight-invalid run was stopped and was
not counted. The valid H20 session began only after the requested mode was
confirmed.

## Controlled A23 gate

PASS for the liveness question:

- documents transformed/fail-closed/outstanding: `2/0/0`;
- `SELF_READY` requests/accepted/rejected: `1/1/0`;
- `selfShieldReleaseCompleted=1`;
- `selfShieldParserContinued=1`;
- `selfShieldOriginalScriptStarted=1`;
- `selfShieldTraceRejected=0`;
- `selfShieldTraceOutstanding=0`;
- fixture `SCRIPTS=1` and `FIRST_ORIGINAL_SCRIPTS=1`;
- page text/layout and audit placeholders visibly presented;
- no global gray overlay remained;
- network visual candidates/replaced/raw: `14/14/0`;
- raw BLOCK/UNKNOWN: `0/0`;
- proxy queue rejects/protect failures/QUIC/direct TCP bypass: `0/0/0/0`.

This directly closes the reviewed post-204 parser-liveness blocker. The first
original script ran; CSS/image requests were not used as a substitute proof.

## No-flash evidence

External recordings were used only as evidence and never as runtime authority.
At 2 fps, 533 sampled frames across the bounded controlled/real-web recordings
contained `0` frames with the controlled raw red/black sentinel signature.
The real-web recording contained 194 frames with the cyan audit placeholder,
providing a positive detector control. The controlled release transition itself
was not fully covered because Chrome restored a non-navigating tab until a
physical refresh; therefore this result is reported only at the actual sampling
coverage and is not overstated as a continuous-time guarantee.

Evidence hashes before deletion:

- controlled recording: `fd83c98e387503075264b8f88c296fe547f88e5bd193fdeb3bffe2c14ab55c6a`;
- real-web recording: `dad443fc06f0785749579f129a7f9a5e2f9db474fd18d9a62e5ae156deabc392`;
- controlled terminal screenshot: `3cb21be6141b1512b677c37eb32cb3930192124f303c3b5089dfd0a1299aa8a2`;
- Google Images no-query screenshot: `2bec2a0bf05b39cc6793a3b14f35510400dc8c2265f2e1d00f200acc038e065d`;
- Google `mujer` terminal screenshot: `ad626424f316b61142cdd15ed7e6644e8df4b6beddda81a6e7a12f985d7bc552`;
- Frávega screenshot: `4680b06599bf8e4f1d1da41bf9b92b059966a2a13fd59f047ca01c9ced9a4d3b`;
- Mimo screenshot: `f2b9cdccc3d12e71f12a8b7547fd4e709edf2149a1ff4f235fe0cd5f3987d2e9`.

All raw recordings and screenshots were deleted locally and from the A23 after
hashes/metrics were extracted.

## Real-web smoke

### Google Images

The Google Images page without a query transformed, reached `SELF_READY`,
released its own curtain, displayed normal text/layout, and showed only audit
placeholders. Raw network visual delivery stayed zero.

The exact authorized search `mujer` was attempted. Google redirected the lab
session to `google.com/sorry`; this is recorded as `BLOCKED_BY_SITE`. No CAPTCHA
or site control was bypassed. The redirected document was fail-closed.

### Frávega

FAIL usability / PASS fail-close:

- document transform count advanced `4 -> 5`;
- `SELF_READY`, release, and parser-continuation counters did not advance;
- Chrome displayed `Glosh protected this document.`;
- Replace-All candidates/replaced/raw reached `26/26/0`;
- no raw photo was visible;
- the page was not navigable as a normal storefront.

### Mimo

FAIL usability / PASS fail-close:

- document transform count advanced `5 -> 6`;
- `SELF_READY`, release, and parser-continuation counters did not advance;
- Chrome displayed `Glosh protected this document.`;
- Replace-All candidates/replaced/raw reached `31/31/0`;
- no raw photo was visible;
- the page was not navigable as a normal storefront.

The two sites reproduce the same boundary: transformation succeeds, but the
real-web bootstrap fails closed before `SELF_READY`. This is distinct from the
controlled post-204 liveness defect fixed by DEV408.

## Health and rollback

- crash/ANR/OOM observed: `0/0/0`;
- network visual candidates/replaced/raw: `31/31/0`;
- raw BLOCK/UNKNOWN: `0/0`;
- proxy queue rejects/protect failures: `0/0`;
- QUIC/direct TCP bypass: `0/0`;
- `selfShieldTraceOutstanding=0` before STOP;
- terminal transport:
  `ownedFdResources=0`, `activeProtectedUdpSockets=0`,
  `transportRuntime=ready`;
- lab service removed after final STOP;
- Device Owner/Affiliated/Accessibility/data inode preserved.

The proxy recorded 22 client-side TLS handshake EOFs before SNI. These are
speculative Chrome connection disconnects, not crashes, raw delivery, queue
rejection, protect failure, QUIC bypass, or direct-TCP bypass. They are retained
as a health residual rather than hidden.

## Residual / next route

`REAL_WEB_BOOTSTRAP_FAIL_CLOSED_PRE_SELF_READY` is the sole closure blocker.
The next diagnostic must add a privacy-safe, one-shot bootstrap installation
reason before `SELF_READY` and compare the first failed invariant on Frávega and
Mimo against the passing controlled/Google document. It must not return to H19,
Accessibility/tab binding, global overlay release, screenshots, polling, model
changes, or threshold changes.

