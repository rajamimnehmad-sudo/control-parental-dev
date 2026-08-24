# Glosh Remote — Mac Operator

Updated: 2026-08-24 12:43 ART

## REMOTE-INSTALL-MAC-OPERATOR-04

Status: DESIGN FINAL / IMPLEMENTATION LOCAL PENDING.

### Audit conclusion
The Mac side already has a solid technical engine but is still a lab console, not a product UI. The shared GitHub branch `work/remote-install-connection-00` is stale relative to the validated local worktree, but it demonstrates the original shape clearly: one localhost relay, one Cloudflare tunnel, one active agent, an allowlisted action map, terminal stdin, textual status and manual session revocation. Newer broker/no-link/operator acceptance code exists only in the local worktree and must be used as the actual implementation base when Codex returns.

Do not implement this operator UI from the stale GitHub branch. Use the current local Glosh Remote HEAD after re-validating owner/worktree.

### Product goal
The operator should never need to type relay commands during normal installs. One Mac app should own the support window, pending requests, relay/tunnel lifecycle, explicit acceptance, connected-device status, safe precheck, installation workflow and final disconnect.

### Recommended implementation shape
Keep the proven Python transport/broker logic as a UI-independent engine and add a desktop shell around it.

For the first professional Mac build, prefer a small PySide6 desktop app over rewriting the transport in Swift. Reasons:
- reuses the validated Python relay/broker clients;
- keeps crypto/session behavior unchanged;
- fastest path to a signed local Mac app for controlled support use;
- UI and engine remain separated so a future native SwiftUI shell can replace only presentation.

Structure proposal:

`tools/glosh-remote-spike/mac/operator/`
- `operator_app.py` — composition/root only
- `operator_controller.py` — state/orchestration
- `operator_state.py` — explicit state machine
- `broker_service.py` — wrapper around current BrokerOperatorClient
- `relay_service.py` — wrapper around current RemoteSession/tunnel lifecycle
- `precheck_service.py` — read-only high-level diagnostics
- `install_service.py` — future explicit install actions, not arbitrary shell
- `audit_log.py` — safe local action/outcome log
- `secrets.py` — macOS Keychain access
- `ui/` — PySide6 views/widgets

Do not turn the current relay file into a GUI god-class.

### Operator state machine
Single active support session per operator for the first product version.

States:
- `CLOSED` — support window closed
- `WAITING` — window open, no request accepted
- `REQUEST_SELECTED` — pending phone selected, awaiting explicit accept
- `PREPARING` — descriptor sealed/accepted, waiting for Android pairing
- `CONNECTED` — authenticated remote agent available
- `PRECHECKING` — read-only inspection running
- `READY_TO_INSTALL` — precheck complete, no unresolved hard blocker
- `ACTION_REQUIRED` — customer must change something on phone
- `INSTALLING` — future bounded install workflow active
- `VERIFYING` — future post-install checks
- `DISCONNECTING` — revoke/close in progress
- `ERROR` — safe recoverable error

No second active connected phone until this is proven operationally necessary. Pending requests may queue, but only one can be accepted at a time.

### Main window UX
Header:
- `Glosh Remote Operator`
- status pill: `Soporte cerrado`, `Esperando`, `Conectado`, etc.
- primary control: `ABRIR SOPORTE` / `CERRAR SOPORTE`

Pending area:
Each request card shows only useful metadata:
- manufacturer + model
- Android version
- request age
- state
- buttons `ACEPTAR` and `RECHAZAR`

Do not expose nonce, descriptor, RSA material, broker endpoint or session key.

Connected-device area:
- `Samsung SM-S908E`
- `Android 16 · SDK 36`
- connection health
- elapsed session time
- safe actions:
  - `PRECHECK`
  - later `INSTALAR GLOSH`
  - `DIAGNÓSTICO`
  - `DESCONECTAR`

Developer-only console goes behind an explicit DEV toggle and still routes through allowlisted actions. No arbitrary shell text box in normal mode.

### Broker/window lifecycle
`ABRIR SOPORTE` should:
1. verify operator credential exists in macOS Keychain;
2. verify broker endpoint reachable;
3. open broker support window;
4. start polling/listening for pending requests;
5. show `Esperando un teléfono…`.

`ACEPTAR` should:
1. lock selection to one request;
2. create/start the local relay session;
3. establish/verify Quick Tunnel DEV while that remains the transport;
4. seal the join descriptor using the existing RSA-OAEP flow;
5. send operator_accept;
6. wait for authenticated agent;
7. transition to CONNECTED.

Any failure must revoke the request/session best-effort and return to a safe WAITING/CLOSED state. No stale accepted request left behind.

`CERRAR SOPORTE` should revoke active request/agent, stop relay, terminate tunnel, close broker window and clear ephemeral state.

### Secrets
The raw operator credential must not live in source, APK, repo, plaintext preferences, shell history or logs.

For the Mac app:
- store/retrieve the operator key from macOS Keychain;
- keep it in memory only while needed;
- rotate the credential used during DEV coordination before product/general use;
- never display the raw key in UI.

Session keys remain ephemeral and memory-only as already designed.

### Safe local audit log
Keep a small local log for operator accountability, but never log secrets or raw sensitive command output.

Allowed fields:
- timestamp
- anonymized/session request id prefix
- device manufacturer/model
- high-level action (`accept`, `precheck`, `install`, `disconnect`)
- result (`PASS`, `FAILED`, `CANCELLED`)
- non-sensitive reason code

Never log:
- operator key
- session key
- descriptor
- nonce
- pairing code
- account emails
- full dumpsys output
- Accessibility content

### PRECHECK contract
PRECHECK is read-only and should be the first action after CONNECTED.

The UI should present three human sections:

`LISTO`
- Android compatible
- Wireless ADB authenticated
- no existing Device Owner
- no work profile, etc.

`REQUIERE ACCIÓN DEL USUARIO`
- Google account present
- Samsung/Xiaomi account present
- work profile to remove
- OEM confirmation needed
- other condition known to block the chosen Device Owner path

`BLOQUEO`
- another Device Owner exists
- unsupported Android/version/path
- multi-user/profile condition that cannot be safely resolved in this pilot
- ADB authority insufficient

The operator should not need to interpret raw ADB output.

### Precheck data sources / privacy
Use explicit read-only allowlisted actions, not arbitrary shell.

Candidate high-level actions for the Android agent:
- `device`
- `owners`
- `users`
- `profiles`
- `accounts_summary`
- `admins`
- `packages_glosh`
- `accessibility_status`
- `vpn_status`
- `play_store_status`
- `restrictions`
- `battery`

Prefer normalized summaries from the agent. For accounts, show provider/type/count first; do not show email addresses unless a later real blocker proves the exact identity is operationally necessary.

The first adaptive pilots should still allow Codex/engine logic to inspect the raw underlying ADB evidence locally when needed, but the normal operator UI should show only classified results.

### Installation authority
Do not add an arbitrary remote shell to make installation easier.

Build explicit high-level operations over the proven channel, for example later:
- upload/install selected Glosh APK
- verify signature/version
- attempt Device Owner only after precheck approval
- verify Device Owner
- open required Settings screen
- verify Accessibility/VPN
- run final health check

Every write action must have a clear UI name, explicit operator trigger, bounded parameters and structured result.

### APK delivery — future design constraint
The current command channel is not yet a professional APK-transfer path. Do not quietly add huge base64 blobs to generic commands.

Preferred future options to evaluate in the adaptive-install ticket:
1. chunked encrypted file transfer over the existing authenticated WSS relay, with size/SHA-256 validation and temp-file cleanup; or
2. short-lived authenticated download URL scoped to one install, then verify SHA-256 before local ADB install.

Do not choose until the current local agent capabilities are inspected. Whatever path is chosen, Glosh APK integrity must be verified before installation.

### Error handling
Human-facing messages:
- `El teléfono dejó de responder.`
- `La solicitud venció. Pedile al cliente que toque Conectar con soporte otra vez.`
- `No pudimos preparar la conexión. No se modificó el teléfono.`
- `El precheck necesita una acción del cliente.`

Never expose stack traces or crypto/broker details in the normal UI.

Developer details may be available behind an expandable `Detalles técnicos` panel with sanitized reason codes.

### Security invariants
Preserve exactly:
- localhost-only relay bind
- outbound tunnel, no inbound router port
- explicit operator acceptance
- one active agent per session
- HMAC mutual authentication
- AES-256-GCM encrypted command channel
- monotonic anti-replay sequence
- session expiry/revocation
- ephemeral Android ADB identity
- allowlisted high-level commands
- fail-closed behavior

The Operator UI must call the existing safe engine; it must not bypass these controls.

### Product hardening before general use
Still required:
- rotate operator credential exposed during DEV coordination;
- add a customer-verifiable/install-scoped binding to prevent accepting a spoofed anonymous request during an open support window;
- replace Quick Tunnel DEV with a stable production transport or formally approve its replacement;
- sign/notarize the Mac app if distributed beyond the development Mac;
- decide audit-log retention.

### Implementation gates when Codex is available
Before editing, Codex must inspect the actual current local HEAD and newer local broker/relay files; GitHub remote branch is not a valid implementation base.

Expected gates:
- engine unit tests unchanged/green;
- UI/controller unit tests for state transitions;
- broker-open/list/accept/revoke/close integration test;
- relay/tunnel lifecycle test with mocked process failures;
- single-active-session enforcement;
- Keychain secret test/no-log scan;
- PRECHECK classification tests;
- app launch smoke on Mac;
- real S22 gate: pending request appears → accept → CONNECTED → PRECHECK → disconnect.

### Coordination
- `REMOTE-INSTALL-CONNECTION-00`: PASS FINAL DEV / CLOSED.
- `REMOTE-INSTALL-LIVE-GUIDE-03`: implementation/gate currently ahead of adaptive install pilot.
- `REMOTE-INSTALL-MAC-OPERATOR-04`: design final, implementation waits for access to current local Glosh Remote HEAD.
- `REMOTE-ADAPTIVE-INSTALL-PILOT-01`: should use the Operator + PRECHECK once available, but may begin with console only if the user explicitly chooses not to wait.

No Chrome, GloshIA, DAG, App Usuario/Admin or existing Supabase function should be modified by this ticket.