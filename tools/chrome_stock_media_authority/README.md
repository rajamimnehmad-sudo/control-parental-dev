# Chrome Stock Media Authority H19 evidence harness

This DEV-only harness runs bounded H19 evidence phases on the dedicated A23. It
uses the existing `android.permission.DUMP` receiver and stock Chrome. It does
not use CDP, DevTools, MediaProjection, or screenshots as filtering/release
authority.

The runner:

- refuses automatic device selection and an already-active H19 lab;
- verifies the exact model/API, package versions, owner/accessibility evidence,
  and the Glosh data inode;
- starts either `replace-all` or `selective` using the existing receiver;
- opens no unrecorded anchor page between phase start and the first state;
- marks each state in the bounded provenance ledger;
- verifies requested rotation, begins recording before navigation, snapshots the
  prior READY count/marker, and waits boundedly for a fresh exact foreground
  READY marker before any gesture;
- records a bounded screen video and final screenshot as evidence only;
- samples the video at the declared FPS for known fixture palettes/patterns;
- records current-versus-previous inference/cache/security counter deltas;
- gates the controlled main report and requires its signed `FRAME_REPORT` to
  contain exactly the five expected `BLOCKED` outcomes with no error/escape;
- hashes and discards the raw Accessibility tree, retaining only package counts
  and hashed H19 ready markers (plus whitelisted policy names);
- emits per-state JSON plus independent aggregate transport/READY/log health
  evidence, never raw Logcat;
- deletes recordings, screenshots, and sampled frames after aggregate extraction;
- never clears Logcat, Chrome data, cache, history, or accounts;
- always stops H19, removes only its own device temporary directory, and restores
  the exact rotation settings on normal exit, failure, Ctrl-C, or SIGTERM.

READY evidence is derived from the latest exact `event_source` release and is
invalidated by a later revoke/fail-close event. A state that keeps the same
foreground document may reuse that current verified binding without requiring a
duplicate release log. The runner snapshots the current binding before changing
orientation, so a real rotation must produce a later current binding before the
state can proceed.

The bounded `two-tab-binding` action uses Android's public
`Browser.EXTRA_CREATE_NEW_TAB` contract to open a controlled tab B. It accepts B
only after a distinct exact `event_source` READY binding, then uses Android Back
and accepts tab A only when its document binding (window/document/token/root/source)
is restored under a strictly newer visibility lifecycle. Any reuse of B's
token/root/source binding across that boundary fails the gate. No Chrome UI
coordinates, CDP, DevTools, or screenshot authority are involved. Android Home
→ Chrome foregrounding remains a separate action and never counts as a tab
switch.

Run the deterministic local tests first:

```bash
python3 -m unittest discover -s tools/chrome_stock_media_authority -p 'test_*.py'
```

Then create a plan from `example_plan.json` and run it with an explicit serial:

```bash
python3 tools/chrome_stock_media_authority/run_a23_gate.py \
  --serial DEVICE_SERIAL \
  --plan tools/chrome_stock_media_authority/example_plan.json \
  --output .codex-tmp/chrome-stock-media-authority/h19-run
```

Plans can contain up to four phases and 25 states per phase. Supported phase
modes are `replace-all` and `selective`. A state can open an HTTP(S) URL or use
the bounded actions `controlled`, `back`, `forward`, `reload`, `foreground`,
`background-foreground`, `restart-chrome`, `restart-glosh`, `two-tab-binding`, and
`chrome-policy`. `restart-glosh` uses the existing DUMP-only main-process kill,
requires a fresh PID/protection session, restores the same lab mode, and first
proves the existing Chrome document has no current READY binding and remains
covered by an attached opaque surface. Only then does the runner explicitly
reload: a process/session restart cannot rebind the old document token without
new navigation/reload. The
`controlled` and `chrome-policy` targets are fixed in code, so a plan cannot
smuggle an arbitrary non-HTTP scheme. `restart-chrome` preserves the profile.
Orientation is `current`, `portrait`, or `landscape`; swipe and recording counts
are bounded by validation.

The compiled media `policyEpoch` is intentionally not mutable through this
harness. Proving an epoch transition requires a separately reviewed APK whose
compiled epoch changes; the runner will not fake that transition with a plan
flag or counter reset.

For a bounded preview/product-detail interaction, a state may contain up to four
`taps` expressed as display-relative permille coordinates, for example
`{"xPermille": 500, "yPermille": 420}`. The evidence records only the count;
coordinates are part of the explicit reviewed plan rather than inferred UI
automation.

Output layout:

```text
preflight.json
phase-*.json
states/<id>/snapshot.json
states/<id>/contact-sheet.png  # temporary, real-web review only
states/<id>/logcat-summary.json
logcat-summary.json
summary.json
postflight.json
visual-review-manifest.json
```

Full URLs are not copied into JSON evidence: the state retains only the host and
SHA-256. Raw Logcat and the raw UI hierarchy are never written.

Known fixture palettes cannot prove the absence of an arbitrary real photograph.
Every non-controlled web state therefore creates a contact sheet from **all**
sampled frames and leaves the run in `PENDING_MODEL_OR_HUMAN_REVIEW`; counters
alone never make it PASS. Review each contact sheet through a model-visible or
human-visible path, then provide an exact digest-bound verdict file:

```json
{
  "schema": "glosh-h19-visual-review-v1",
  "reviewer": "model-visible-review",
  "entries": [{
    "stateId": "ra-google-mujer-cold",
    "contactSheetSha256": "...",
    "verdict": "NO_UNEXPECTED_IN_SCOPE_RAW_MEDIA"
  }]
}
```

Every manifest state must appear exactly once. Finalize only after actual review:

```bash
python3 tools/chrome_stock_media_authority/finalize_visual_review.py \
  --output .codex-tmp/chrome-stock-media-authority/h19-run \
  --review .codex-tmp/chrome-stock-media-authority/h19-review.json
```

The finalizer verifies every SHA, persists only verdicts/hashes, deletes the
contact-sheet pixels, and marks PASS only when every verdict equals that
manifest entry's `requiredVerdict`. Replace-All requires
`NO_UNEXPECTED_IN_SCOPE_RAW_MEDIA`; selective states and `chrome://policy` use
their own explicit review purposes. Escape or inconclusive verdicts remain
fail-closed.

`controlledSentinelLikeVisibleFrames` is deliberately an observation, not an
automatic security verdict: the H19 out-of-scope CSS synthesis control can share
red/black colors. Interpret it with fixture position/state and Byte Gate
counters. Physical exposure claims are limited to the configured sampling FPS.

`run_a23_gate.py` remains one orchestration unit because its single
responsibility is the transactional device lifecycle: preflight, bounded state
capture, and guaranteed rollback share one ownership boundary. Plan validation
and evidence analysis are already split into separate modules; splitting the
transaction itself would duplicate or weaken cleanup state.

`h19_evidence.py` remains just over 500 lines because it is the single
privacy-reduction boundary: raw frame pixels, Logcat, fixture reports, and raw
Accessibility XML enter there and only aggregate counts, hashes, reason codes,
and whitelisted identifiers leave. Splitting those sanitizers would duplicate
redaction rules at the evidence boundary. Device mutation and visual-review
finalization are separate modules.
