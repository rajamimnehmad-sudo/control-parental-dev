# Glosh Remote — Adaptive Remote Installer

Updated: 2026-08-25 03:00 ART

## Connection base

`REMOTE-INSTALL-CONNECTION-00`: **PASS FINAL DEV / CLOSED**.

The proven no-link stack remains frozen: broker → relay/WSS → Glosh Remote → local Wireless ADB, with HMAC/AES, fixed allowlist and no direct ADB exposure.

## Superseded guide

`REMOTE-INSTALL-LIVE-GUIDE-03`: **FAILED UX PHYSICAL / SUPERSEDED**.

The old guide-first UX is not the product route.

## One-Tap checkpoint that just failed physically

Previous gated HEAD:
`59f6b39be1b39005bacdc848ff0717240b43ed67`

Previous APK:
- `GloshRemote-OneTap-DEV.apk`;
- 19,287,534 bytes;
- SHA-256 `23c26d864d8ad9d3d6b3e00ae2149307a520f20b5172ccba02ce5df30f1e6390`.

Automated gate for that APK had passed Python, Android unit tests, lint and assemble, but the physical A23 run is now **FAILED reproducibly**. Therefore that APK is superseded and must not be used for S22 final validation.

### Physical A23 failure — confirmed root cause

On Samsung Developer Options, Glosh correctly found the label `Depuración inalámbrica`, but the generic matcher scored the internal clickable Switch higher than the navigable preference row:

- navigable row via descendant label: score 88;
- internal `switch_background`: score 90.

Observed evidence:

`decision screen=DEVELOPER_OPTIONS action=CLICK_WIRELESS_DEBUGGING`

`click target=WIRELESS_DEBUGGING result=CLICKED`

Android returned success from `ACTION_CLICK`, but `DevelopmentSettingsDashboardActivity` remained on screen and Wireless Debugging did not open. Treating `performAction()` as a completed navigation was therefore incorrect.

A23 cleanup after the failed reproduction:
- restored to Content Filter;
- animations restored to 1.0;
- relay closed;
- broker `available=false`;
- Device Owner intact;
- APK worktree clean.

## Active fix

`REMOTE-INSTALL-ONE-TAP-HARDENING-05`: **PHYSICAL FAIL CONFIRMED / FIX IMPLEMENTED BY CHATGPT / BUILD + A23 REGRESSION GATE PENDING**.

Implementation branch:
`work/remote-install-one-tap-05-chatgpt`

Current fix HEAD:
`54df3995b2070380f72cbf9c15548630eac07347`

Changes are isolated to `tools/glosh-remote-spike/**`.

### Fix 1 — target the navigable row

`SamsungSettingsClassifier` now uses a dedicated navigable-row matcher for `CLICK_WIRELESS_DEBUGGING` on `DEVELOPER_OPTIONS`.

The navigation target must:
- be clickable;
- be enabled;
- be non-checkable;
- not be a Switch class;
- not be `switch_widget` or `switch_background`;
- contain the exact Wireless Debugging label directly or as descendant text;
- remain HIGH-confidence / unique before authorization.

If no unique safe navigable row exists, Autopilot fails closed instead of touching the switch.

### Fix 2 — dispatch is not navigation success

`FreshNodeClickExecutor` no longer logs a successful `performAction()` as if the screen transition had completed. A dispatched click is surfaced as `ACTION_DISPATCHED`.

The existing transition guard remains authoritative after dispatch:
- for `CLICK_WIRELESS_DEBUGGING`, expected postcondition is screen `WIRELESS_DEBUGGING`;
- remaining on `DEVELOPER_OPTIONS` returns WAIT and authorizes no second click;
- if the expected screen is still not reached by timeout, the action is rejected/fail-closed;
- only an actual subsequent trusted snapshot of the expected screen permits Autopilot to continue.

### Regression tests added

`AutopilotUiPureTest` now includes:
1. Samsung fixture with both the navigable Wireless Debugging row and internal switch carrying the same label; the row must win and the switch must never be selected.
2. Transition test proving a dispatch while still on `DEVELOPER_OPTIONS` is WAIT, then REJECT at timeout; only real `WIRELESS_DEBUGGING` screen is ACCEPT.

## Next gate

Before another physical run:
1. run Python tests + Android unit tests + lint + assemble on exact HEAD `54df3995b2070380f72cbf9c15548630eac07347`;
2. freeze new APK path/size/SHA-256;
3. perform a targeted A23 regression run first, because A23 reproduced the bug;
4. require zero click on internal Switch, one navigation-row dispatch, and actual transition to Wireless Debugging;
5. continue the same run through automatic pairing/support if the regression passes;
6. only after A23 PASS, use that exact APK on S22 cable-free.

No push/PR/merge/deploy/Production/Supabase mutation is required for the gate. Do not alter Device Owner to make the test pass.

## Coordination

- `REMOTE-INSTALL-CONNECTION-00`: PASS FINAL DEV / CLOSED.
- `REMOTE-INSTALL-LIVE-GUIDE-03`: FAILED UX / SUPERSEDED.
- One-Tap APK SHA `23c26d…`: **FAILED PHYSICAL A23 / SUPERSEDED**.
- `REMOTE-INSTALL-ONE-TAP-HARDENING-05`: fix implemented at `54df3995…`; build + targeted A23 regression pending.
- `REMOTE-ADAPTIVE-INSTALL-PILOT-01`: S22 final gate is paused until the A23 regression passes with the new exact APK.

Do not touch Chrome, GloshIA, DAG, App Usuario/Admin, Supabase or production Device Owner logic.