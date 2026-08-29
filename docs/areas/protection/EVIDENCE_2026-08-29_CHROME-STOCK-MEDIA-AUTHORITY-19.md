# CHROME-STOCK-MEDIA-AUTHORITY-19 — physical closure evidence

STATUS: BLOCKED / FOREGROUND_READY_BINDING_UNAVAILABLE

Base: `17cac82e581e69c81fe5f435498269b313daa059`

Functional diagnostic head: `04f11cd5`

Device: Samsung SM-A235M, Android 14/API 34, stock Chrome 152.0.7977.64.

Build: DEV383. Final diagnostic APK SHA-256:
`d7551c07c0f357560d936b228b593645a00904655a65a97c55ba999ee075ec80`.

## What passed

- Network Replace-All remained fail-close on every focused run.
- Canonical focused result: `networkVisualCandidates=13`,
  `networkVisualReplaced=13`, `networkVisualRawDelivered=0`.
- `networkVisualRawBlockedDelivered=0` and
  `networkVisualRawUnknownDelivered=0`.
- `failures=0`, `proxyQueueRejects=0`, `protectFailure=0`, QUIC/direct TCP
  attempts `0/0`.
- The document transformer completed and the READY claim was accepted only
  after the opaque surface commit.
- Device Owner, Affiliated state, Accessibility enablement and app data inode
  `1239519` were preserved. The lab service stopped cleanly and the temporary
  stay-awake setting was restored to `0`.

## Blocking result

The foreground document authority could not bind the transformed document to
the current stock-Chrome Accessibility window. Physical runs consistently
reported:

- `phase=ready_ack_accepted`;
- `axBound=false`;
- no `ready_foreground_released` event;
- `trustedReadyMarkers=[]`;
- protected surface retained and raw presentation denied.

The focused audit corrected the original circular ordering (the synchronous
READY POST previously preceded publication of the marker), then tested a
current-only protected host with an Accessibility role, explicit 1x1 layout,
and finally text plus aria identity published before the claim. Chrome 152 on
this A23 still did not expose a trustworthy marker to the Accessibility
scanner.

This is not a Byte Gate escape: the raw-network counters remained zero. It is
the explicit H19 STOP condition that a non-raster foreground binding was not
demonstrable. Screenshot/raster authority was not reintroduced.

## Automated validation

- focused bootstrap contract tests: PASS;
- READY endpoint and Accessibility scanner tests from the preceding diagnostic
  build: PASS;
- DEV assemble: PASS;
- DEV lint on the preceding same-version build: PASS;
- `git diff --check`: PASS.

## Residual / next decision

Stock Chrome can be governed pre-render per network body, but this ticket did
not prove a secure and usable release of the navigation curtain for the exact
foreground document. A different browser-side, non-raster document/window
identity would be required. The approved constraints prohibit using
screenshots, CDP/extensions, or owning/modifying Chrome, so H19 cannot claim a
technical PASS.
