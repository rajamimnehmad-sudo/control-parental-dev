# Glosh Remote — Adaptive Remote Installer

Updated: 2026-08-25 07:55 ART

## Connection base

`REMOTE-INSTALL-CONNECTION-00`: **PASS FINAL DEV / CLOSED**.

The proven no-link stack remains frozen: broker → relay/WSS → Glosh Remote → local Wireless ADB, with HMAC/AES, fixed allowlist and no direct ADB exposure.

## Superseded physical candidates

### APK from HEAD `59f6b39b…`

APK SHA-256:
`23c26d864d8ad9d3d6b3e00ae2149307a520f20b5172ccba02ce5df30f1e6390`

Status: **FAILED PHYSICAL A23 / SUPERSEDED**.

Root cause: the generic `WIRELESS_DEBUGGING` matcher scored Samsung's internal clickable Switch above the navigable preference row. `ACTION_CLICK` returned true while `DevelopmentSettingsDashboardActivity` remained on screen, causing a false-success loop.

### First row-only fix — HEAD `54df3995b2070380f72cbf9c15548630eac07347`

Status: **BLOCKED PHYSICAL A23 / SUPERSEDED BY DEEP-ROW FIX**.

Positive result:
- the old false `CLICKED` success disappeared;
- no internal Switch was clicked.

New blocker confirmed physically:
- the real Samsung `Depuración inalámbrica` label was visible;
- the dedicated row matcher still failed to resolve its clickable row;
- Autopilot therefore performed three reveal scrolls and exposed the rescue overlay;
- no `ACTION_DISPATCHED` occurred.

Root cause in source:
`SettingsTreeScanner.descendantTexts()` only records descendant text through depth 3. Samsung can place the visible title deeper inside the clickable `Preference`, so the clickable row and label were not associated even though both existed in the same accessibility tree.

Cleanup after the blocked A23 run:
- rescue overlay removed;
- Glosh Accessibility disabled;
- Content Filter restored and active;
- Wireless Debugging disabled;
- relay/Quick Tunnel closed;
- Device Owner intact;
- no code modified by the physical-test worktree.

## Active fix — deep Samsung preference ancestry

Branch:
`work/remote-install-one-tap-05-chatgpt`

Current HEAD:
`b4a559b2707bd3040642208be643a8eefc6922ec`

Status:
**FIX IMPLEMENTED BY CHATGPT / BUILD + TARGETED A23 REGRESSION PENDING**.

Changes remain isolated to `tools/glosh-remote-spike/**`.

### 1. Resolve the row by tree ancestry, not descendant-depth guessing

For `DEVELOPER_OPTIONS → WIRELESS_DEBUGGING`:

1. find a visible node with exact `Depuración inalámbrica` / `Wireless debugging` text or content description;
2. use its immutable scanner `path`;
3. walk path prefixes upward;
4. select the nearest ancestor that is:
   - visible;
   - clickable;
   - enabled;
   - non-checkable;
   - not a Switch class;
   - not `switch_widget` / `switch_background`;
   - with non-empty bounds;
5. deduplicate candidates reached from the title and switch branches;
6. require the resulting row to remain unique/HIGH confidence before authorization.

This works even if the title is nested more than three levels below the real clickable preference row.

### 2. Visible label means no reveal scroll

If the screen is `DEVELOPER_OPTIONS`, the exact Wireless Debugging label is already visible, but no safe navigable row can be resolved, Autopilot now **refuses to scroll** and fails closed immediately.

Reveal scrolling is reserved for cases where the expected label is actually absent from the visible snapshot.

This prevents the previous three-scroll overshoot/rescue-overlay behavior.

### 3. Dispatch semantics made explicit

`FreshNodeClickExecutor.Result` now uses `ACTION_DISPATCHED` as the actual enum value. A successful `AccessibilityNodeInfo.performAction(ACTION_CLICK)` means only that Android accepted the action dispatch.

Navigation success remains controlled by the subsequent trusted snapshot and `AutopilotTransitionGuard`:
- still `DEVELOPER_OPTIONS` → WAIT, no second click;
- timeout without transition → REJECT/fail closed;
- actual `WIRELESS_DEBUGGING` snapshot → ACCEPT and continue.

### 4. Regression fixtures

`AutopilotUiPureTest` now covers:
- row vs internal Switch;
- a Samsung-style deep tree where the visible title is >3 levels below the clickable preference row;
- ancestry resolution to the exact clickable row path;
- visible label with no safe ancestor as a fail-closed/no-scroll condition;
- post-dispatch transition confirmation.

## Next gate

Before physical testing:
1. run the full one-command verification gate on exact HEAD `b4a559b…`;
2. freeze new APK path/size/SHA-256;
3. install only that APK on A23;
4. targeted regression requirements:
   - exact visible label recognized;
   - resolved target is the clickable preference ancestor, never a Switch;
   - zero reveal scrolls when label is already visible;
   - immediate result `ACTION_DISPATCHED`;
   - subsequent trusted screen becomes `WIRELESS_DEBUGGING`;
   - no repeated click;
5. continue through automatic 6-digit pairing/local ADB/Mac only if the row-navigation gate passes.

S22 remains paused until A23 passes with the new exact APK.

## Coordination

- connection base: PASS FINAL DEV / CLOSED;
- APK `23c26d…`: FAILED PHYSICAL / SUPERSEDED;
- HEAD `54df3995…`: BLOCKED PHYSICAL due deep-row recognition / SUPERSEDED;
- HEAD `b4a559b…`: current fix, build + A23 regression pending;
- no changes to Chrome, GloshIA, DAG, App Usuario/Admin, Supabase or production Device Owner logic;
- no merge/deploy/Production.
