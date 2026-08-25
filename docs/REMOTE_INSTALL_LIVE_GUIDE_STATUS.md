# Glosh Remote — Professional Guided Assistant

Updated: 2026-08-25 11:58 ART

## Executive status

`REMOTE-INSTALL-GUIDED-ASSISTANT-08`: **S22 PHYSICAL FAIL AT DEVELOPER→WIRELESS TRANSITION / FIX IMPLEMENTED / NEW AUTOMATED GATE PENDING**.

`REMOTE-INSTALL-CONNECTION-00`: **PASS FINAL DEV / CLOSED**.

The secure connection stack remains unchanged. The defect is isolated to the guided Settings transition.

## Failed physical candidate

Previously frozen candidate:

- HEAD: `1313eea1324903348c6e375b3ce9327120b31ff9`;
- APK: `GloshRemote-Guided-DEV.apk`;
- size: `19,287,538` bytes;
- SHA-256: `14ed9879f2b18559fdbef914fa2a89e572bef2b98082c1c10e935d3e0a6ecd10`.

Automated gates on that candidate were all PASS:
- architecture PASS;
- Python 14/14 PASS;
- Android 96/96 PASS;
- lint PASS;
- assemble PASS.

Physical S22 result: **FAILED UX / REPRODUCIBLE LOOP**.

Observed behavior:
- user enabled Developer options;
- Glosh kept returning to Developer options instead of advancing;
- the Samsung Wireless Debugging deep-link resolves back to Developer options on this device/firmware;
- the coordinator treated the repeated Developer-options screen as a reason to relaunch the same direct route.

That APK is now **superseded and must not be reused**.

## Root cause

The prior coordinator used screen fingerprint changes to decide whether to reopen:

`DEVELOPER_OPTIONS → open WIRELESS_DEBUGGING_SETTINGS → Samsung returns DEVELOPER_OPTIONS → repeat`.

This was still an unbounded state-machine error even though Glosh performed no automatic Settings clicks or scrolls.

## Fix implemented

Implementation branch:

`work/remote-install-guided-assistant-08-chatgpt`

Current fix HEAD:

`7af909c193234a2f07c6102b8588468327c86295`

Immutable gate branch:

`gate/remote-guided-s22-loopfix-7af909c`

Changes are isolated to `tools/glosh-remote-spike/**`.

### Correct finite transition

Step 2 now ends as soon as Glosh trusts and recognizes the Developer options screen.

Then step 3 starts separately:

1. attempt `android.settings.WIRELESS_DEBUGGING_SETTINGS` once;
2. if Samsung returns to Developer options, Glosh does **not** relaunch immediately;
3. customer receives a clear instruction to activate Developer options if needed and then manually tap `Depuración inalámbrica` in the current list;
4. a second direct attempt is allowed only after a genuinely different trusted Developer-options fingerprint, representing a user-driven state change such as enabling the master Developer options switch;
5. if that second attempt also returns to Developer options, the route becomes permanent visual fallback for that run;
6. no third attempt exists;
7. no automatic click, scroll or coordinate gesture is introduced.

When the customer manually enters Wireless Debugging, the existing observer detects the actual `WIRELESS_DEBUGGING` screen and resumes the normal step-3 logic automatically.

## New pure policy

`WirelessDirectRoutePolicy` owns the bounded direct-route state:

- same Developer snapshot repeated indefinitely → `WAIT_FOR_USER`;
- one real user-driven fingerprint change → `RETRY_DIRECT_ONCE`;
- second return → `VISUAL_FALLBACK` permanently;
- reset only when a new guided run starts.

Regression tests explicitly prove:
- repeated same Developer screen cannot loop;
- exactly one user-driven retry is possible;
- after two attempts, all future Developer snapshots are fallback-only;
- reset creates a fresh bounded session.

## Required new automated gate

Run only from:

`gate/remote-guided-s22-loopfix-7af909c`

Exact HEAD:

`7af909c193234a2f07c6102b8588468327c86295`

Command:

```bash
ANDROID_HOME=/Users/yejielnehmad/Library/Android/sdk \
  bash tools/glosh-remote-spike/verify_guided_assistant.sh
```

Must pass architecture, Python, Android JVM tests including the new loop-policy tests, lint and assemble. Freeze a new APK path/size/SHA. Do not reuse `14ed9879…ecd10`.

## Next physical gate

After automated PASS, send the exact new APK to the S22 by Taildrop and repeat only the customer flow.

Critical physical requirement:

`DEVELOPER_OPTIONS_REOPEN_LOOP=0`.

If Samsung returns from the direct Wireless route to Developer options:
- no immediate relaunch;
- stable instruction remains visible;
- user manually taps Wireless Debugging;
- once inside Wireless Debugging, Glosh resumes automatically.

The A23 remains available only as controlled regression hardware if needed.

## Coordination

- connection base: PASS FINAL DEV / CLOSED;
- previous Guided APK `14ed9879…ecd10`: FAILED PHYSICAL S22 / SUPERSEDED;
- current fix: `7af909c…`;
- new automated gate: pending;
- new S22 Taildrop candidate: only after automated PASS;
- no Chrome, GloshIA, DAG, App Usuario/Admin, Supabase, Production or Device Owner changes;
- no merge, PR or deploy authorized.
