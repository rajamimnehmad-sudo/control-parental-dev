# Glosh Remote — Mac Operator Console

Updated: 2026-08-24

## REMOTE-INSTALL-MAC-CONSOLE-04

Status: DESIGN / PENDING LOCAL IMPLEMENTATION.

### Why this exists
The current Glosh Remote operator side is technically functional through Python/terminal tooling, but there is no polished operator application for the Mac. The installer should not require the operator to remember broker commands, relay flags, request IDs or shell syntax.

### Product goal
Provide one simple Mac window for the complete remote-install operator flow:

1. Open/close support availability.
2. Show incoming pending requests automatically.
3. Display only safe device metadata: manufacturer, model, Android/SDK and request age.
4. Explicit Accept / Reject buttons.
5. Start and supervise the temporary relay/tunnel automatically.
6. Show connection states clearly: Waiting, Pairing, Connected, Disconnected, Expired.
7. Once connected, expose task-oriented actions rather than an arbitrary shell.
8. Keep a local session activity log suitable for troubleshooting, without secrets.
9. Disconnect/revoke from one obvious control.

### Initial Mac UX
Single-window layout:

- Header: `Glosh Remote · Soporte`.
- Status pill: `Disponible para recibir` / `No disponible`.
- Primary control: `ABRIR SOPORTE` / `CERRAR SOPORTE`.
- Incoming requests section with device card and `ACEPTAR` / `RECHAZAR`.
- Active device section when connected.
- Safe actions grouped by purpose:
  - `PRECHECK DEL TELÉFONO`
  - `INSTALAR / ACTUALIZAR GLOSH`
  - `DIAGNÓSTICO`
  - `VER ESTADO`
  - `DESCONECTAR`
- Advanced DEV section may expose the current allowlisted command console, collapsed by default and never presented as the normal product path.

### Security boundaries
- No arbitrary remote shell textbox in the normal UI.
- Reuse the existing command allowlist and fail-closed behavior.
- Operator credential must not be hardcoded, logged or committed. Product path should move it to macOS Keychain or another OS-protected secret store before general release.
- Never display or persist broker nonce, session keys, RSA private material or plaintext descriptors.
- Any destructive or provisioning-sensitive action must require an explicit operator confirmation.
- One active installation session at a time initially; add multi-session only after the single-device flow is proven operationally.

### Process supervision
The Mac application should own/supervise the operator-side runtime instead of asking the operator to manage terminals manually:

- broker operator window open/close;
- relay process;
- cloudflared Quick Tunnel DEV while that transport is still used;
- health state and process exit detection;
- bounded restart/recovery for infrastructure failures;
- clean shutdown/revocation at disconnect.

Do not silently restart a security-sensitive session after its credentials have expired; restart only by creating a fresh ephemeral session.

### Device workflow after connect
The normal operator UI should expose recipes/actions, not raw ADB concepts.

First planned flow after live-guide validation:

`PRECHECK DEL TELÉFONO`
- manufacturer/model/Android/SDK;
- users/profiles;
- Device Owner / Profile Owner;
- account providers visible through ADB/system services;
- Accessibility/VPN state;
- existing Glosh packages;
- blockers for Device Owner.

Result shown as:
- `LISTO`
- `REQUIERE ACCIÓN DEL USUARIO`
- `BLOQUEO`

Only after explicit operator approval does the next provisioning step run.

### Relationship to current validated architecture
Do not change the already validated no-link transport or security architecture:
- Supabase broker rendezvous;
- ephemeral RSA/OAEP descriptor sealing;
- relay WSS/HMAC/AES;
- same-device Wireless ADB;
- allowlisted operator commands;
- cancel/revoke/fail-closed behavior.

The Mac console is an orchestration/UI layer over those components.

### Coordination
- `REMOTE-INSTALL-CONNECTION-00`: PASS FINAL DEV / CLOSED.
- `REMOTE-INSTALL-LIVE-GUIDE-03`: current Android UX implementation/gate remains active.
- `REMOTE-INSTALL-MAC-CONSOLE-04`: can be designed/reviewed in parallel, but local implementation should use the current Glosh Remote worktree HEAD rather than the stale GitHub branch.
- `REMOTE-ADAPTIVE-INSTALL-PILOT-01`: follows once the live guide and operator workflow are usable.

No Chrome, GloshIA, DAG, App Usuario/Admin, Device Owner production logic or Supabase schema should be modified by this console task.
