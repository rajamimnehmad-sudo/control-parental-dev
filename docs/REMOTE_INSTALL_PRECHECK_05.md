# Glosh Remote — Adaptive Install PRECHECK

Updated: 2026-08-24 12:43 ART

## REMOTE-INSTALL-PRECHECK-05

Status: DESIGN FINAL / IMPLEMENTATION LOCAL PENDING.

### Purpose
After Glosh Remote reaches an authenticated ADB-shell session, the first operator action is always a read-only PRECHECK. PRECHECK determines whether the phone can proceed toward Glosh installation/Device Owner, what the customer must change manually, and what is a hard blocker. It must classify evidence; it must not mutate the phone.

### Output model
Return a structured object, not raw shell text:

- `device`
  - manufacturer
  - model
  - Android version
  - SDK
  - build fingerprint hash/prefix if useful, never a persistent advertising identity
- `ready[]`
- `user_actions[]`
- `blockers[]`
- `warnings[]`
- `evidence_codes[]` — sanitized machine reason codes for DEV troubleshooting

Operator UI renders three main sections:

`LISTO`
`REQUIERE ACCIÓN DEL USUARIO`
`BLOQUEO`

Warnings stay secondary and never hide a blocker.

### Read-only checks
PRECHECK should gather the minimum evidence needed for installation decisions:

1. ADB/session authority
- authenticated agent
- `uid=2000(shell)` expected
- local same-device adbd reachable

2. Android/device
- manufacturer/model
- Android/SDK
- primary user id
- setup/provisioning state when observable

3. Device Policy
- existing Device Owner
- Profile Owner(s)
- active Device Admins
- managed/work profile presence

4. Users/profiles
- Android user list
- secondary users
- guest users
- managed profiles
- current/foreground user

5. Accounts summary
- account provider/type counts visible through the shell-side evidence available on that OEM/version
- classify Google, Samsung, Xiaomi/MI, WhatsApp/other providers when visible
- do not return account email/name to normal UI

6. Glosh package state
- installed package/flavor/version
- current signature digest when inspectable through package metadata
- Device Admin receiver availability
- whether another Glosh build/flavor could conflict with install/update

Known main application namespace is `com.contentfilter.user`; DEV uses `.dev`. Device Admin receiver declared by the app is `com.contentfilter.feature.accessibility.service.ProtectionDeviceAdminReceiver`. Do not hardcode only DEV; derive candidate package from the APK/install target and inspect installed packages.

7. Accessibility/VPN
- current Glosh accessibility service state if Glosh is installed
- current always-on/active VPN state where observable
- competing VPN indicator if relevant

8. Package/install environment
- Play Store presence/enabled state
- unknown-sources/restriction signals only where relevant
- package installer availability
- available storage sanity check before APK transfer/install

9. OEM restrictions
- Samsung/Xiaomi/Motorola family classification
- known user-visible prerequisites should become recipe hints, not speculative blockers

### Privacy boundary
Raw account dumps and full `dumpsys` output may be used transiently inside the local diagnostic parser when required, but must not be returned wholesale to the Mac UI, broker or logs.

Normal UI account result examples:
- `Cuenta Google detectada · 1`
- `Cuenta Samsung detectada · 1`
- `Otro proveedor de cuenta detectado · 2`

Do not show email addresses by default.

No PRECHECK data is sent to Supabase. Broker remains rendezvous/ciphertext only.

### Classification rules
Use explicit reason codes and conservative logic.

#### LISTO examples
- `ADB_SHELL_OK`
- `PRIMARY_USER_OK`
- `NO_EXISTING_DEVICE_OWNER`
- `NO_MANAGED_PROFILE`
- `STORAGE_OK`
- `TARGET_APK_COMPATIBLE`

#### REQUIERE ACCIÓN DEL USUARIO examples
These are not automatically mutated during PRECHECK:
- `GOOGLE_ACCOUNT_PRESENT`
- `SAMSUNG_ACCOUNT_PRESENT`
- `XIAOMI_ACCOUNT_PRESENT`
- `OTHER_ANDROID_ACCOUNTS_PRESENT`
- `WORK_PROFILE_REMOVAL_REQUIRED`
- `SECONDARY_USER_REVIEW_REQUIRED`
- `OEM_CONFIRMATION_REQUIRED`

The first pilots must validate whether a detected account actually blocks the chosen `dpm set-device-owner` path on that OEM/version before codifying it as universally required. Do not invent a blanket rule from one phone.

#### BLOQUEO examples
- `EXISTING_FOREIGN_DEVICE_OWNER`
- `ADB_SHELL_NOT_AUTHENTICATED`
- `UNSUPPORTED_ANDROID`
- `UNSUPPORTED_USER_TOPOLOGY`
- `TARGET_RECEIVER_MISSING`
- `TARGET_APK_SIGNATURE_CONFLICT`
- `INSUFFICIENT_STORAGE`
- `DEVICE_POLICY_STATE_UNKNOWN_FAIL_CLOSED` when policy evidence is contradictory or cannot be safely interpreted

An existing Glosh Device Owner should not automatically be a blocker; classify it as already-managed/repair path after verifying package/signature.

### Evidence adapters
Do not expose arbitrary command execution through the Operator.

Implement internal high-level actions in the Android remote agent, each with bounded parser/output:
- `precheck_device`
- `precheck_policy`
- `precheck_users`
- `precheck_accounts`
- `precheck_glosh`
- `precheck_runtime`

The Mac `PRECHECK` button may execute these in sequence/parallel as appropriate and merge normalized results.

Underlying shell commands may vary across Android/OEM versions. Prefer a small adapter layer with fallbacks, e.g. Device Policy command output first and `dumpsys device_policy` fallback, rather than forcing one string format globally. Tests need fixture outputs for Samsung first and generic variants.

### No mutation guarantee
During PRECHECK, prohibit all commands/actions that can change state:
- package install/uninstall/disable/enable
- account removal
- user/profile removal
- `dpm set-device-owner`
- Device Admin activation/deactivation
- settings put/delete
- app data clear
- Chrome reset
- VPN/Accessibility changes
- reboot

A PRECHECK implementation that needs a write to learn the answer is not a PRECHECK; it must return `UNKNOWN`/blocker and defer to an explicitly authorized install step.

### Operator UX
Connected phone card:

`PRECHECK` primary action.

While running:
`Revisando el teléfono…`

Result example:

LISTO
✓ Samsung SM-S908E · Android 16
✓ Conexión de soporte autenticada
✓ No hay otro Device Owner

REQUIERE ACCIÓN DEL USUARIO
• Cuenta Google detectada
• Cuenta Samsung detectada

BLOQUEO
— Ninguno

Primary next action only if no blocker:
`PREPARAR INSTALACIÓN`

If user actions exist:
`GUIAR AL CLIENTE`

Never present `set-device-owner` as the next normal button before required user actions are resolved and re-PRECHECK passes.

### Recheck loop
After the customer removes an account/profile or makes a required change:
- operator presses `VOLVER A REVISAR`;
- run a fresh PRECHECK;
- do not trust cached blockers/actions;
- timestamp the result locally.

This is how real OEM recipes are learned during pilots.

### Pilot evidence
For each real installation record only non-sensitive recipe evidence:
- OEM/model
- Android/SDK
- PRECHECK reason codes before action
- user action performed
- PRECHECK reason codes after action
- whether Device Owner attempt later succeeded/failed and sanitized error code

Do not record account emails, pairing codes, session keys or raw dumps.

### Gates
When implemented:
- parser fixtures for Device Policy/users/accounts/package states
- explicit no-mutation test: all PRECHECK actions are read-only allowlist entries
- privacy test: no email/full dumps returned
- conflict tests: foreign DO, managed profile, multiple users, account providers, signature conflict
- UNKNOWN/fail-closed tests for malformed OEM output
- S22 physical PRECHECK with no writes
- compare PRECHECK before/after one deliberately user-performed benign condition change when appropriate

### Coordination
- `REMOTE-INSTALL-CONNECTION-00`: PASS FINAL DEV / CLOSED.
- `REMOTE-INSTALL-LIVE-GUIDE-03`: immediate Android UX implementation/gate.
- `REMOTE-INSTALL-MAC-OPERATOR-04`: Mac product shell design final.
- `REMOTE-INSTALL-PRECHECK-05`: read-only classification design final.
- `REMOTE-ADAPTIVE-INSTALL-PILOT-01`: should execute PRECHECK first; destructive/install actions remain separate and explicitly authorized.

Do not touch Chrome, GloshIA, DAG, App Usuario/Admin or Supabase for PRECHECK.