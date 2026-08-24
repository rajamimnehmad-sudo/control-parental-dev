# Glosh Remote — Device Owner professional commit flow

Updated: 2026-08-24

## REMOTE-INSTALL-DEVICE-OWNER-COMMIT-07

Status: DESIGN FINAL + ISOLATED OPERATOR PROTOTYPE PASS / LOCAL-HEAD INTEGRATION PENDING.

This task belongs only to Glosh Remote / remote Android installation. It must not modify Chrome, GloshIA, DAG, App Admin/User product behavior, Supabase production functions or the current local Glosh Remote worktree until the real Mac HEAD is inspected.

## Platform facts used by the design

- Android's documented ADB development path for setting a DPC as Device Owner requires the DPC to be installed and no accounts to remain on the device.
- Android Enterprise guidance also requires no other users or work profile for a fully-managed-device ADB setup.
- AOSP DevicePolicyManagerService explicitly allows the ADB/shell path after normal setup only when there is no existing Device Owner/Profile Owner and, on normal non-split-system-user phones, no extra users and no incompatible accounts.
- Therefore Glosh Remote must treat *all* Android accounts and extra users/profiles as preconditions, not just Google accounts.
- `clearDeviceOwnerApp()` is deprecated and documented as testing-only/best-effort; product recovery must not depend on automatic Device Owner removal.

## Exact Glosh admin component currently visible in main

Main App Usuario manifest declares:

- package base: `com.contentfilter.user`
- DEV package from Gradle flavor: `com.contentfilter.user.dev`
- DeviceAdminReceiver class: `com.contentfilter.feature.accessibility.service.ProtectionDeviceAdminReceiver`

Expected DEV component at integration time, subject to verification against the actual local build:

`com.contentfilter.user.dev/com.contentfilter.feature.accessibility.service.ProtectionDeviceAdminReceiver`

Do not hardcode this into the remote protocol until the current local APK manifest/package/signing identity has been verified.

## Core product rule — Device Owner is the commit point

Everything reversible is completed before Device Owner activation:

1. remote session authenticated;
2. PRECHECK read-only PASS;
3. APK transferred through the encrypted session;
4. full APK SHA-256 verified;
5. package name verified;
6. signing certificate verified against the expected release/dev identity;
7. APK installed/update-in-place;
8. installed package/version/signature re-verified;
9. expected DeviceAdminReceiver present;
10. Glosh self-test PASS;
11. no foreign Device Owner;
12. no Profile Owner/work profile;
13. exactly one relevant Android user for the ADB route;
14. zero Android accounts;
15. ADB shell session still authenticated and healthy.

Only after all of the above may the UI offer Device Owner activation.

## Account/user cleanup

PRECHECK returns only coarse counts/state. It must never return account emails/usernames.

If `account_count > 0`:
- result = USER ACTION;
- Android Live Guide takes the user to the OEM account-management surface;
- Glosh highlights the next account entry/removal control but the person performs the removal/confirmation;
- re-run PRECHECK until total account count reaches zero.

Removal means removing the account from the phone, not deleting the cloud/service account.

OEM guidance baseline:
- Samsung: `Ajustes → Cuentas y respaldo → Administrar cuentas`; Samsung officially documents temporary removal of Samsung and Google accounts from the phone through this surface.
- Motorola: `Ajustes → Contraseñas y cuentas` / `Usuarios y cuentas` variants; select account → `Quitar/Remove account`.
- Xiaomi/Redmi/POCO: use dynamically matched HyperOS/MIUI account surfaces (`Account & sync`, Xiaomi Account and Android account management variants); do not hardcode one label across versions.

If `user_count > 1` or a Profile Owner/work/private/secondary profile is present:
- result = USER ACTION unless a foreign management owner makes it BLOCKED;
- never delete another user's/profile's data automatically;
- guide the person through the appropriate Android/OEM removal flow, then re-run PRECHECK.

## Double-consent commit

Device Owner activation requires two independent, short-lived confirmations:

### Phone grant

Glosh Remote shows a plain-language final screen on the Android device explaining that Glosh will become the principal protection administrator and cannot be casually disabled/uninstalled afterward.

User taps an explicit action such as:

`ACTIVAR PROTECCIÓN`

The Android side mints a short-lived `user_commit_grant_id` bound to the current PRECHECK fingerprint. Recommended TTL: 120 seconds, maximum 300 seconds.

### Operator grant

Mac Operator independently shows the exact device/model and a final confirmation:

`ACTIVAR DEVICE OWNER`

The operator must click it explicitly.

No one-sided remote activation is allowed.

## Fresh-precheck binding

Immediately before running Device Owner activation, Android re-runs the fixed precheck locally and recomputes a SHA-256 fingerprint over only the relevant non-sensitive state:

- expected package;
- expected admin component;
- current Device Owner/Profile Owner;
- user count;
- account count;
- installed/signature/admin-receiver/self-test state;
- authenticated ADB readiness.

If the fresh fingerprint differs from the fingerprint bound to the user's grant, the commit is invalidated and PRECHECK must run again.

This closes the TOCTOU gap where an account/profile/package could change between the first green screen and the `dpm` command.

## Fixed remote action — never arbitrary shell

Normal Operator UI must not expose `adb shell`.

The remote contract gets one bounded action, e.g.:

`device_owner_activate_v1`

Bound inputs only:
- expected verified package;
- expected verified DeviceAdminReceiver component;
- fresh-precheck fingerprint;
- live user grant ID.

The Android agent validates all bindings and then invokes the exact equivalent of:

`dpm set-device-owner <verified-component>`

No arbitrary command string is accepted from the Mac.

## Verification after commit

After the command returns success, state is still `VERIFYING`, not `DONE`.

Required evidence:
- system Device Owner is exactly the expected Glosh package/component;
- Glosh itself reports `DevicePolicyManager.isDeviceOwnerApp()` true;
- installed package/signing identity/version unchanged;
- DeviceAdminReceiver active;
- remote session still alive long enough to report final evidence;
- no crash/ANR during the transition.

Only then state becomes `DEVICE_OWNER_ACTIVE`.

## Recovery rule

If activation may have succeeded but verification is inconsistent:

- state = `RECOVERY`;
- do not call deprecated/best-effort owner-clearing APIs automatically;
- do not run `dpm remove-active-admin` as a product rollback;
- preserve the connection if possible;
- diagnose and repair Glosh/policy state in place;
- if ownership is unrecoverably inconsistent, escalate to explicit manual recovery/factory-reset planning with the user rather than pretending rollback is safe.

This is intentionally asymmetric: before Device Owner, rollback is easy; after Device Owner, correctness must come from strong precommit gates and repair-in-place.

## Isolated prototype evidence

The external Mac Operator prototype now contains a Device Owner commit state machine and tests for:
- account-present → USER ACTION;
- foreign Device Owner → BLOCKED;
- dual user/operator confirmation;
- fresh-precheck fingerprint change invalidates commit;
- post-commit verification failure enters RECOVERY rather than auto-rollback;
- fixed `device_owner_activate_v1` payload contains no arbitrary shell field.

Current isolated prototype suite: 18/18 Python unit tests PASS.

## Integration gates when the Mac/local HEAD is available

1. inspect actual Glosh Remote local HEAD/worktree and owner;
2. preserve any current Live Guide implementation;
3. integrate PRECHECK fields as structured/redacted data, not raw dumps;
4. integrate Android-minted user grant + TTL;
5. integrate fixed Device Owner action;
6. verify exact package/component/signing identity from the APK used in the gate;
7. run Samsung S22 physical flow first;
8. verify account cleanup guidance without exposing account identities to Mac;
9. verify TOCTOU invalidation by adding an account/profile between precheck and commit in a controlled test;
10. verify exact Device Owner state via system + app;
11. no automatic Device Owner rollback;
12. ChatGPT reviews exact diff/tests/evidence before PASS FINAL.

## Coordination

- `REMOTE-INSTALL-CONNECTION-00`: PASS FINAL DEV / CLOSED.
- `REMOTE-INSTALL-LIVE-GUIDE-03`: design final; local implementation/gate pending.
- `REMOTE-INSTALL-MAC-OPERATOR-04`: isolated prototype complete; local adapter pending.
- `REMOTE-INSTALL-PRECHECK-05`: structured precheck contract defined; local action pending.
- `REMOTE-INSTALL-PIPELINE-06`: secure staged APK pipeline defined; local action pending.
- `REMOTE-INSTALL-DEVICE-OWNER-COMMIT-07`: DESIGN FINAL + prototype state machine PASS; local integration pending.
- `REMOTE-ADAPTIVE-INSTALL-PILOT-01`: waits for these gates.

No push/PR/merge/deploy/Production change is authorized by this document.
