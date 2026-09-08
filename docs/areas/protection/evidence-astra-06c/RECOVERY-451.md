# DEV451 — expired-session recovery follow-up

Status: DEV451 installed with exact source/APK match; automatic expiry recovery physically demonstrated once. **Not integral PASS**; final timeout follow-up and final-candidate health remain pending.

Functional SHA: `e971662c9f9d77c2605bfb5aad70bc04c7fe557b`.
Base integral: `76f285be92945857e5de89423f5df7edf54fc7a9`; governance main: `9323b126dde3b91e21e2136a11d087fa3c1f0ca1`.
Same owner, task06C and isolated worktree. Central resumed at `065cd199705c60b18a74006937ecace488e09ba5`. Only authorized app-user DEV sources/tests and versionCode change.

## Observed failure

After the DEV45046-minute gate, at04:14:07 ART the guard expired the lease and suspended Chrome with reason main_process_lost. The app process24072 was still alive at07:25 and STATUS reported PresentationReady/active/ready while Chrome was suspended. Rejected no_current_session heartbeats then persisted. The reason text alone does not prove process death; the initial scheduling/IPC delay is not fully attributed.

Existing STOP/START recovered generation538 at07:26:06 with lease_current. Mimo original menu/logo/photos were confirmed in the compositor at07:28 after direct URL navigation; the initial restored intent stalled and was not called a page-render PASS. This was an operational recovery, not a permanent fix. The prior integral PASS is suspended in Central.

## Correction

The existing health loop now requests a fresh session when its previous heartbeat has expired, or Chrome remains suspended for one lease interval despite timely sends. The second case covers rejected/asynchronously delayed heartbeats, for which successful Messenger.send is not an acknowledgment.

The loop reaches this code only after the existing full runtime/bootstrap health checks. A new nonce/generation is obtained through the existing guard protocol. Opening the session keeps Chrome suspended. The function returns without sending a lease, so a subsequent iteration must freshly revalidate health before publishing. Display readiness is cleared while renewing and restored only after verified release.

Recovery is disabled before explicit stop, update preparation, direct-boot handling and fail-close. An update/manual stop cannot be automatically undone by this path. Cancellation propagates; an IPC renewal error remains fail-closed. No guard verifier/TTL/suspension authority, model, weights, thresholds, proxy/cache or reset behavior changed. This does not promise Chrome will stay open when protection actually fails.

A bounded explicit DEV command GUARD_HEARTBEAT_DELAY queues one3000ms health-loop delay, once, without changing ordinary browsing or security deadlines. It exists to reproduce lease expiry on the physical device. Unit tests exercise the bound, one-shot consumption, clearing on stop/start, current and expired sessions, persistent suspension with timely sends, disabled recovery, and actual coordinator expiry/new-generation/release sequencing.

## Validation

395 Chrome/guard tests pass with zero failures/errors. Changed-file formatting passes. Build/lint and APK identity are recorded after completion in the accompanying451 JSON. A23 reconnected and DEV451 was installed. Deliberate3000ms heartbeat delay caused suspension at08:01:04.854, fresh generation540 at08:01:06.379, verified release at08:01:06.536 and readiness at08:01:06.599 without manual STOP/START. See expiry-recovery-451-run1.json and guard/runtime logs. The running451 health observation includes deliberate configuration STOP/START and does not establish a final single-config gate.

Required physical continuation: verify exact451 APK/signer/source, active/ready/DO/A11y/reset3; inject delayed heartbeat and observe expiry→new generation→verified release automatically; verify repeated recovery, ordinary browsing, screen-off/resume, update-preparation remains suspended and explicit STOP remains stopped; complete proportional>=45min health observation on the final candidate. Preserve final Chrome available/protected for the user.

No GIF/video integration. No Chrome data/cache clearing, PR, merge, main, Production, deploy or product publication.
