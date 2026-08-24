# Glosh Remote — Adaptive Autopilot Architecture

Updated: 2026-08-24
Status: DESIGN FROZEN / SAMSUNG-FIRST / UNIVERSAL CORE

## Product goal

After the one-time Accessibility bootstrap, the user taps **INICIAR** once. Glosh Remote detects the real device state and takes the shortest safe path to an authenticated local Wireless ADB connection. Known Samsung screens are operated automatically with deterministic Accessibility `ACTION_CLICK` actions. Unknown or ambiguous states stop immediately and fall back to a minimal guide.

The existing secure remote connection stack is not redesigned here. Broker/relay/WSS/HMAC/AES/allowlist/no-link remain frozen and are entered only after local ADB is ready.

## Design choice

**Samsung-first implementation, universal engine.**

We implement only the Samsung OEM recipe now, but the core knows nothing about Samsung-specific menu ordering. OEM differences are provided by adapters/recipes. Motorola/Xiaomi later add recipes without rewriting the state machine, safety gate, pairing or remote connection.

## Absolute shortest-path order

The engine must always try states in this order:

1. Existing authenticated support session → DONE.
2. Existing valid local ADB transport → skip Settings and pairing; connect support.
3. If Android < 11/API 30 → standard Wireless Debugging route is unavailable; explicit fallback.
4. If Accessibility is not enabled → bootstrap user once; do not pretend automation is possible before it.
5. If already on pairing dialog → pair immediately.
6. Try public `Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS` directly.
7. If Developer Options is recognized → go straight to Wireless Debugging.
8. If Developer Options is not available and OEM=Samsung → public `Settings.ACTION_DEVICE_INFO_SETTINGS` → Software information → Build number.
9. After enabling Developer Options → re-open Developer Settings directly. Never navigate back through menus if an intent can jump there.
10. Enable Wireless Debugging only if OFF.
11. Accept only the exact expected Wi-Fi/network confirmation if it appears.
12. Open Pair device with pairing code.
13. If exactly one contextual six-digit code is visible → feed existing pairing service automatically.
14. Otherwise show six-box manual code input; connection architecture remains unchanged.
15. Once local ADB is valid → hand off to the already proven secure support connection stack.

## Android state detection rule

Do not use `Settings.Global.DEVELOPMENT_SETTINGS_ENABLED` or `ADB_ENABLED` to infer state from a normal third-party app. Current Android documentation says those values always read 0 for third-party apps. State detection must therefore use direct Settings intents + trusted UI classification, or the existing ADB transport if already established.

Official shortcuts used:
- `Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS`
- `Settings.ACTION_DEVICE_INFO_SETTINGS`

## One manual bootstrap

Before Accessibility is enabled, Glosh cannot press Settings controls on the user's behalf. The bootstrap is intentionally small:

1. User installs/opens Glosh Remote.
2. App checks whether its Accessibility service is enabled.
3. If not, app opens the appropriate Accessibility/app-settings screen and shows only the manual steps actually required by that Android build, including Restricted Settings if Samsung/Android requires it for that sideload source.
4. As soon as the service becomes enabled, app returns to Autopilot automatically.

After this bootstrap, **INICIAR** is intended to perform all normal Settings clicks automatically until local ADB is connected, except protected device credentials.

## Universal state engine

Core states:

- `BOOT`
- `NEED_ACCESSIBILITY`
- `PROBE_EXISTING_ADB`
- `PROBE_DEVELOPER_SETTINGS`
- `OEM_ENABLE_DEVELOPMENT`
- `WAIT_USER_CREDENTIAL`
- `DEVELOPER_OPTIONS`
- `WIRELESS_DEBUGGING`
- `WAIT_NETWORK_CONFIRMATION`
- `PAIRING_DIALOG`
- `LOCAL_PAIRING`
- `LOCAL_ADB_READY`
- `CONNECT_SUPPORT`
- `DONE`
- `GUIDED_FALLBACK`
- `BLOCKED`

Every automatic step is one transaction:

`observe → stable snapshot → classify → authorize exactly one action → fresh node reacquisition → ACTION_CLICK → invalidate generation → observe expected next state`

Never chain blind clicks.

## Click safety contract

An automatic click is allowed only when ALL are true:

1. Exactly one trusted Settings `TYPE_APPLICATION` window is authoritative.
2. Overlay, IME and unrelated apps are excluded.
3. At least two stable snapshots/fingerprints agree.
4. `(windowId, generation, fingerprint)` is still current.
5. OEM recipe allows the target on the classified screen.
6. Candidate confidence is HIGH.
7. Candidate is unique and has an adequate margin over #2.
8. Candidate exposes click semantics or an explicitly supported toggle action.
9. The live node is freshly re-acquired immediately before action.
10. The action's expected next-state set is known.

Any false condition → **no click**.

## No coordinate automation as normal path

Do not use x/y coordinates or generic gesture taps for the main Samsung path. Accessibility node semantics are the authority. Coordinate gestures may be considered only as a future explicitly scoped compatibility escape hatch, never as a silent fallback.

## Samsung adapter v1

### Developer Options already enabled

`ACTION_APPLICATION_DEVELOPMENT_SETTINGS` → recognize Developer Options → click `Depuración inalámbrica`.

### Developer Options disabled

Developer Settings probe does not yield a recognized Developer Options screen → `ACTION_DEVICE_INFO_SETTINGS` → `Información de software` → `Número de compilación`.

Build number is clicked up to seven times, but EACH tap is separately re-authorized against a fresh stable snapshot. If a credential prompt appears before/after any tap, automation stops.

After the user enters the credential, the engine re-probes Developer Settings directly; it does not assume the previous sequence succeeded.

### Wireless Debugging OFF

Recognize Wireless Debugging screen/toggle → enable exact toggle → if exact Samsung/Android network confirmation appears, press the positive action automatically → verify Wireless Debugging is ON.

### Wireless Debugging already ON

Skip the toggle/confirmation entirely → `Vincular dispositivo con código de vinculación`.

### Pairing dialog already open

Skip all prior navigation and pair.

## Credential rule

Never enter/read/store screen lock PIN, password or pattern. `CREDENTIAL_PROMPT` is a first-class state:

- pause clicks;
- tell user `Confirmá el PIN/patrón del teléfono para continuar`;
- observe only for the protected prompt to disappear;
- resume from a fresh state probe.

## Pairing code rule

Automatic PIN extraction is allowed only when:

- pairing request is active;
- current screen is confidently the Wireless Debugging pairing dialog;
- exactly one six-digit numeric candidate exists;
- the candidate is inside/near the expected pairing semantics;
- snapshot/token remains current.

Otherwise show the six-box manual fallback. Never guess a code.

## Existing ADB shortcut

Before opening Settings, Glosh should ask the existing local ADB transport/session component whether a valid transport is already available. If yes:

- do not touch Developer Options;
- do not toggle Wireless Debugging;
- do not open pairing;
- transition directly to the existing secure support connection.

If a previous pairing exists but the transport is not currently connected, the Android implementation should attempt the existing reconnect mechanism first; only open a new pairing dialog when reconnect fails deterministically.

## Scenario coverage

The architecture explicitly handles:

- Accessibility OFF / ON;
- Android <11;
- no Wi-Fi / Wireless Debugging unavailable or policy-disabled;
- Developer Options OFF / ON;
- Wireless Debugging OFF / ON;
- network confirmation present/absent;
- credential prompt present/absent;
- pairing dialog already open;
- unique six-digit code / ambiguous code / unreadable code;
- existing valid ADB;
- existing support connection;
- rotation/window change/stale node;
- IME/overlay becoming active;
- unsupported OEM;
- unexpected screen;
- Accessibility revoked mid-run;
- user leaves Settings;
- connection/pairing expiry.

Not every OEM is automatically navigated in v1. The **engine is universal; Samsung is the only fully automated recipe now**. Unsupported OEMs safely fall back rather than receiving guessed clicks.

## Failure/fallback hierarchy

1. `RETRY OBSERVE` — transient unstable snapshot.
2. `REOPEN EXACT INTENT` — expected Settings screen was lost.
3. `GUIDED FALLBACK` — known user-resolvable screen but autopilot confidence is insufficient.
4. `BLOCKED` — unsupported Android, Wi-Fi/policy restriction, another hard prerequisite.

Fallback never silently changes OEM recipe.

## UX

Normal success should look like:

`Preparando teléfono…`

- Revisando estado
- Activando opciones necesarias
- Activando depuración inalámbrica
- Vinculando conexión segura
- Conectando con soporte

`Conectado`

Do not show Settings implementation detail unless manual intervention is required.

Manual interruptions should be explicit and singular:

- `Activá Accesibilidad para continuar.`
- `Confirmá el PIN/patrón del teléfono.`
- `Conectate a una red Wi-Fi.`
- `No pude reconocer esta pantalla. Tocá Continuar para usar la guía.`

## Android implementation modules

Recommended production responsibilities:

- `AdaptiveInstallCoordinator` — single serialized state owner.
- `InstallCapabilityProbe` — API/OEM/accessibility/ADB/Wi-Fi/reconnect probes.
- `SettingsWindowSelector` — exactly one trusted Settings application window.
- `SettingsSnapshotFactory` — immutable tree snapshot; no live nodes in matcher.
- `SettingsScreenClassifier` — screen identity + confidence.
- `OemSettingsAdapter` — recipe interface.
- `SamsungSettingsAdapter` — Samsung aliases/context/expected transitions.
- `AutopilotActionGate` — the ten click-authority conditions.
- `FreshNodeResolver` — re-acquire exact live node immediately before action.
- `PairingCodeDetector` — contextual unique six-digit detector.
- `GuidedFallbackController` — minimal guide only when automation stops.

Keep pairing/relay/broker/crypto in their existing components.

## What remains for Codex

Only environment-bound work:

1. Inspect exact local code at the current Remote Installer HEAD.
2. Port this already-defined state engine/safety contract into the existing Android module without touching broker/relay/crypto.
3. Add Android/JVM tests mapping the reference cases.
4. Compile/lint.
5. Run A23 physical Samsung Autopilot gate.
6. Generate APK and send identical candidate to S22 for cable-free user UX check.

No product/architecture decisions should be required from Codex unless the real Samsung Accessibility tree contradicts this contract.
