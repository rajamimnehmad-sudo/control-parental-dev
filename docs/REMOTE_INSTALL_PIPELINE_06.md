# Glosh Remote — Bounded Install Pipeline

Updated: 2026-08-24 12:43 ART

## REMOTE-INSTALL-PIPELINE-06

Status: DESIGN FINAL / IMPLEMENTATION AFTER PRECHECK.

### Goal
After an authenticated Glosh Remote session and a clean PRECHECK, install the selected Glosh APK remotely with explicit, bounded operations. Do not expose arbitrary shell, do not publish the APK to an unauthenticated URL, and do not mix this transport with Supabase rendezvous.

### Core decision
Prefer encrypted chunk transfer over the already authenticated WSS session, followed by same-device local ADB installation. The broker remains rendezvous/ciphertext only.

Do not send an entire APK as a single JSON/base64 command. Add a dedicated bounded file-transfer path to the existing encrypted protocol.

### Mac-side artifact validation
Before transfer, Operator validates the selected APK:
- regular file
- configured maximum size
- SHA-256
- package name from APK metadata
- version code/name
- signing certificate digest
- expected build channel/flavor

The normal Operator UI should only allow a pre-approved Glosh package/signature set. A DEV override can exist behind an explicit developer mode, but must still show package/signature before transfer.

### Transfer protocol
Reuse the existing authenticated session, AES-GCM framing, sequence/replay protection and expiry.

Introduce explicit message kinds, separate from shell commands:
- `file_begin`
  - file_id random
  - purpose=`glosh_apk_install`
  - filename logical only
  - size
  - sha256
  - expected package
  - expected signing digest
- `file_chunk`
  - file_id
  - offset
  - bounded binary/base64 payload within existing message-size limits
- `file_commit`
  - file_id
- `file_abort`
  - file_id

Suggested payload chunk around 96–128 KiB after accounting for encrypted envelope/base64 overhead. Final value must be chosen against `MAX_MESSAGE_BYTES` and tested.

Only one active upload per remote session initially.

### Android receiving boundary
The Glosh Remote app must enforce:
- purpose allowlist
- maximum file size
- monotonically expected offsets
- no sparse/out-of-order writes
- session-bound file_id
- disk-space check
- exact final byte count
- exact SHA-256
- timeout/abort cleanup

Never honor arbitrary destination paths sent by Mac.

### Same-device ADB staging
The remote agent already owns a local authenticated ADB connection to the phone's `adbd`. The professional install path should use that authority to stage the verified APK into a shell-accessible temporary location, preferably through the ADB sync/file-transfer capability already available or added to the local libadb layer.

Target pattern:
`/data/local/tmp/glosh-remote-<random>.apk`

Requirements:
- random bounded basename generated locally, not supplied as a path by operator;
- no path traversal;
- remove old temp file before/after use as appropriate;
- verify remote staged size/SHA when practical before install;
- always delete temporary APK after success/failure.

If the current local ADB library cannot safely push a file, Codex must report that capability gap before selecting an alternative. Do not silently fall back to unauthenticated public hosting.

### Install action
Expose one high-level action only, e.g. `install_glosh_apk`, referencing the verified transferred file_id. It may internally invoke the appropriate Package Manager/ADB install flow.

Parameters are bounded:
- expected package
- replace/update allowed boolean determined by precheck
- downgrade never allowed unless a separate DEV-only gate explicitly authorizes it

Normal product path:
- no `pm install <arbitrary path>` text from UI
- no extra shell flags supplied by operator
- no arbitrary package install capability

### Install decision matrix
Before installation:
- if no Glosh installed: clean install path
- if same package + trusted signing certificate: in-place update allowed
- if package exists with different signing certificate: BLOCK
- if installed version is newer than candidate: BLOCK downgrade
- if another Glosh flavor/package is present: classify explicitly; do not delete automatically in first pilots

After install, verify:
- package exists
- installed version matches candidate
- installed signing digest matches expected
- Device Admin receiver component exists
- app can launch/respond as expected

Only then mark `APK_INSTALLED`.

### Device Owner is a separate write step
Do not combine APK installation and Device Owner into one opaque button initially.

Operator sequence:
1. PRECHECK
2. resolve required customer actions
3. PRECHECK again
4. `INSTALAR GLOSH`
5. verify APK
6. `PREPARAR DEVICE OWNER` / show readiness
7. explicit operator action `ACTIVAR DEVICE OWNER`
8. verify result

This separation makes failure diagnosis and rollback much safer.

### Device Owner attempt
The exact component must derive from the target package/flavor plus the declared receiver `com.contentfilter.feature.accessibility.service.ProtectionDeviceAdminReceiver`; do not hardcode only DEV.

Before issuing the write:
- fresh Device Policy/users/accounts checks
- target package/signature verified
- no foreign Device Owner
- current user/topology compatible with the learned OEM recipe

Capture the command outcome as a sanitized result code. Never parse a failure as success from exit status alone; verify actual Device Owner state afterward.

If Device Owner activation fails:
- do not retry blindly;
- classify the exact sanitized reason/evidence;
- return to ACTION_REQUIRED/BLOCKED;
- leave APK installed unless the pilot explicitly authorizes removal.

### Post-DO boundary
Once Device Owner is verified, future onboarding can move to official DevicePolicyManager-based setup. Do not add Chrome reset, VPN policy changes or App Usuario/Admin product behavior into this ticket; those remain separate coordinated fronts.

### Operator UX
Connected + PRECHECK clean:

`INSTALAR GLOSH`

During transfer:
`Enviando Glosh… 42%`

During install:
`Instalando…`

Success:
`Glosh instalado ✓`

Then:
`ACTIVAR DEVICE OWNER`

If a customer action is needed, disable the write button and show the action instead.

### Progress and cancellation
Transfer is cancellable before install starts.

On cancel:
- stop chunks
- send file_abort best-effort
- delete partial local/Android temp state
- preserve remote support session if healthy

Once Package Manager install is actively executing, do not fake a cancel. Show `Terminando instalación…` and wait for definitive result/timeout.

### Security
Preserve:
- one active authenticated agent
- E2E encryption
- HMAC/AES session authority
- monotonic anti-replay sequencing
- session expiry/revocation
- strict operation allowlist
- size/hash/signature validation
- fail closed

Do not send APK bytes through Supabase broker.
Do not persist session secrets in the Mac Operator.
Do not allow remote arbitrary filesystem paths.

### Required gates
When implemented:
- unit: artifact allowlist/package/signature/downgrade matrix
- unit: chunk ordering, duplicate/replay, size limit, wrong hash, abort/timeout
- unit: path traversal impossible by construction
- integration: transfer a small synthetic artifact end-to-end through encrypted relay
- integration: APK transfer with exact SHA match
- install dry fixture/mock before physical
- physical S22: transfer exact known Glosh DEV APK, install/update, verify package/version/signature, clean temp file
- Device Owner activation remains a separate explicitly authorized physical gate

### Coordination
- `REMOTE-INSTALL-MAC-OPERATOR-04`: desktop shell/orchestration.
- `REMOTE-INSTALL-PRECHECK-05`: mandatory read-only gate before writes.
- `REMOTE-INSTALL-PIPELINE-06`: bounded APK transfer/install design.
- `REMOTE-ADAPTIVE-INSTALL-PILOT-01`: first real devices teach account/OEM recipes and Device Owner blockers.

No Chrome, GloshIA, DAG, App Usuario/Admin or Supabase change is part of this pipeline ticket.