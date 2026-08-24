# Glosh Remote — Live Guide physical-gate status

Updated: 2026-08-24 13:25 ART

## REMOTE-INSTALL-LIVE-GUIDE-03

Status: CODE COMPLETE LOCAL / TECH GATES PASS / PHYSICAL GATE INCOMPLETE.

This status supersedes the older `IMPLEMENTATION LOCAL PENDING` note. The no-link cross-network connection path remains PASS FINAL DEV and must not be changed while closing this gate.

### Local implementation state reported by Codex
- Branch: `work/remote-install-live-guide-03`.
- Local HEAD: `93735b1c2a9f493dcd491556b23920c50c3d66f2`.
- Commits after the previously validated no-link base `475bd35b2934f9dca1a54f0b29dc4c320eacd223`:
  - `1a537f7ac8931c7a1b5525d0ed9658f57e4948c8` — OEM-guided installation wizard.
  - `99fcf2bbf4e8bbaed77c9bf7933de4b8ddadad61` — temporary live Settings guide.
  - `122c45b97931682c8b63f9a65a1a3f4563728871` — Samsung toolbar-title recognition fix.
  - `93735b1c2a9f493dcd491556b23920c50c3d66f2` — confirmed live-guide close control.
- Approx. 40 files involved since `475bd35b`, including MainActivity/RemotePairingService integration and isolated guide accessibility/overlay/scroll/pairing/state packages.

### Technical gates
- Android unit tests: 59/59 PASS.
- Python/broker tests: 6/6 PASS.
- lintDebug: PASS, 0 errors / 8 warnings.
- assembleDebug: PASS.

### Final reported APK
- `GloshRemote-LiveGuide-DEV.apk`
- historical build path: `/private/tmp/glosh-remote-live-guide-03/tools/glosh-remote-spike/app/build/outputs/apk/debug/GloshRemote-LiveGuide-DEV.apk`
- size: 19,196,145 bytes.
- SHA-256: `c3dacc887631592f26d2078197ca6f99ad39624b9b0231eec4979457b0a00d93`.
- Sent through Taildrop to `s22-ultra-de-yejiel`.
- The temporary worktree directory later disappeared, so that historical build path no longer exists locally.

### Physical gate evidence so far
An earlier Live Guide build was exercised on the S22:
- Accessibility Guide enabled.
- Samsung path reached `Información de software`.
- Glosh accessibility overlay appeared.
- Matcher incorrectly entered rescue mode although `Número de compilación` was visible.

That failure produced the Samsung toolbar-title fix (`122c45b9`), followed by the final close-control polish (`93735b1c`).

The final APK `c3dacc...00d93` was sent to the S22, but there is no recorded confirmation that this exact final APK was installed or physically revalidated. Therefore the physical gate remains OPEN.

### Worktree recovery issue
Git still registers the Glosh Remote worktrees but their temporary directories are gone and are reported as prunable. The branch/commit refs remain. Recovery must preserve the branch and commits; do not reset/rebase/stash or run broad cleanup. Recreate only the exact missing Live Guide worktree registration/path needed for this task.

### Required next gate
1. Recover a clean worktree at exactly `work/remote-install-live-guide-03 @ 93735b1c...` without changing code.
2. Re-run 59/59 Android tests, 6/6 Python/broker tests, lintDebug and assembleDebug.
3. Rebuild or recover the exact final APK and record current size/SHA-256.
4. Install/update that exact APK on Samsung S22 without uninstalling the existing app.
5. Run one physical Live Guide session through Samsung Developer Options, Wireless Debugging, pairing, CONNECTED, overlay removal, accessibility self-disable and cancel/revoke.
6. Confirm no broker/crypto/no-link regressions and no crash/ANR.
7. Produce exact diff/review evidence for ChatGPT. Codex PASS is technical only until ChatGPT reviews the code/diff and evidence.

### Coordination
- `REMOTE-INSTALL-CONNECTION-00`: PASS FINAL DEV / CLOSED.
- `REMOTE-INSTALL-LIVE-GUIDE-03`: CODE COMPLETE LOCAL / PHYSICAL GATE OPEN — immediate priority.
- `REMOTE-INSTALL-MAC-OPERATOR-04`, `REMOTE-INSTALL-PRECHECK-05`, `REMOTE-INSTALL-PIPELINE-06`, `REMOTE-INSTALL-DEVICE-OWNER-COMMIT-07`: design/prototype work prepared by ChatGPT, integration waits until the Live Guide physical gate closes on the current local HEAD.
- `REMOTE-ADAPTIVE-INSTALL-PILOT-01`: waits for Live Guide + Operator/Precheck integration.

No Chrome, GloshIA, DAG, App Usuario/Admin, Supabase backend or unrelated worktree should be modified during this recovery/gate task.
