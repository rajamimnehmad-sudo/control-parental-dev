# Glosh Remote — Live Settings Guide

Updated: 2026-08-24 11:06 ART

## REMOTE-INSTALL-LIVE-GUIDE-03

Status: DESIGN APPROVED / IMPLEMENTATION LOCAL PENDING.

Supersedes the simpler static OEM animation-only direction of `REMOTE-INSTALL-OEM-GUIDANCE-02` before implementation. The already validated no-link connection/broker path remains untouched and PASS FINAL DEV.

### Product goal
A totally non-technical Android user should never feel abandoned inside Settings. Glosh Remote should accompany the user in real time while Settings is open, detect the current Settings screen, automatically scroll until the next required row is visible, draw a lime highlight/arrow over the real target, and advance guidance when the user moves to the next screen.

### Authority / implementation
- Use a dedicated `AccessibilityService` only for the temporary Glosh Remote installer guide.
- Enable window-content retrieval and listen only while an explicit Glosh Remote onboarding session is active.
- Scope observation to Android/OEM Settings surfaces; ignore all unrelated apps/windows.
- Render guidance with `TYPE_ACCESSIBILITY_OVERLAY`; do not require a generic `SYSTEM_ALERT_WINDOW` overlay if the accessibility overlay is sufficient.
- Highlight layer must be pass-through/non-touchable so Settings remains fully interactive.
- Small Glosh assistant card may be touchable for `ME PERDÍ`, `MOSTRAR DE NUEVO`, `VOLVER A GLOSH` and minimize/expand.
- Never collect, upload or persist arbitrary Accessibility tree contents.
- Never log Settings text dumps.
- Never persist sensitive nodes, pairing codes, nonces or cryptographic material.
- Shut down/remove the overlay when guidance ends or the user leaves Settings.

### Live target engine
For each OEM recipe step:
1. Inspect the active Settings accessibility tree.
2. Match the expected target using bounded OEM-specific labels/synonyms plus structural hints.
3. If target is visible, obtain its screen bounds and draw a lime pulse/outline + arrow on the real row.
4. If target exists but is off-screen, first try `ACTION_SHOW_ON_SCREEN`.
5. Otherwise locate the nearest scrollable container and use bounded `ACTION_SCROLL_FORWARD` / `ACTION_SCROLL_DOWN` until the target appears.
6. Re-scan after each `TYPE_VIEW_SCROLLED` / content/window event.
7. Stop immediately when the target is visible, the screen changes, no further scroll is possible, or a bounded attempt limit is reached.
8. Never perform `ACTION_CLICK` on Settings targets. User taps switches/rows/system confirmations themselves.

Auto-scroll is an explicitly approved requirement.

### OEM recipes
Initial live recipes:
- Samsung: `Acerca del teléfono → Información de software → Número de compilación ×7`.
- Motorola: `Acerca del teléfono` or `Sistema → Acerca del teléfono → Número de compilación ×7`.
- Xiaomi/Redmi/POCO: `Acerca del teléfono → Información detallada y especificaciones → versión OS/MIUI repetidamente`, then `Ajustes adicionales → Opciones de desarrollador`.
- Generic Android fallback when OEM recipe does not match reliably.

Second-stage universal recipe:
`Opciones de desarrollador → Depuración inalámbrica → activar → Emparejar dispositivo con código`.

### Six-digit pairing code
When the Android pairing-code dialog is visible:
- Best-effort detect the six-digit code locally from the Accessibility tree only when the current expected step is the Wireless Debugging pairing dialog and surrounding semantics identify it as a pairing code.
- If confidently detected, feed it directly to the same `RemotePairingService` entrypoint used by the app/notificaton and auto-start pairing.
- Do not log, persist, upload or expose the code to Supabase/broker.
- If code cannot be read confidently, fall back to the existing six large in-app boxes and RemoteInput notification.
- Keep anti-double-submit authority: at most one pairing attempt active.

### UX
- First-time requirement: user must explicitly enable the Glosh Remote guidance AccessibilityService. Open the most specific accessibility settings page available; explain in plain language that it is temporary and used only to guide setup.
- Once enabled, the service should immediately recognize Settings and start the live guide.
- Floating card examples: `Tocá Información de software`, then `Ahora tocá Número de compilación 7 veces`.
- Overlay moves away from the highlighted row and can minimize to a small Glosh bubble.
- Progress remains `1 de 3`, `2 de 3`, `3 de 3`.
- `ME PERDÍ` triggers a fresh tree scan, bounded auto-scroll and OEM-specific rescue copy without resetting the secure session.
- If Accessibility is turned off mid-guide, fail gracefully to the already-designed animation/static OEM guide; do not block the user permanently.

### Security / policy boundaries
- Accessibility guide is temporary installer functionality, not a permanent remote-control backdoor.
- No arbitrary screen scraping and no observation outside the Settings packages/surfaces needed for onboarding.
- No automatic taps on permission toggles, Developer Options, Wireless Debugging or pairing confirmation.
- Auto-scroll and highlight are allowed; user remains responsible for explicit Android confirmations.
- Existing broker Supabase, RSA/OAEP, HMAC/AES, relay, allowlist, retry-expired logic, `FLAG_SECURE`, IDLE/PREPARING/CONNECTED state authority and no-link architecture must not change.

### Required gates
- Unit tests: OEM detector/recipes, target matching, bounds/highlight mapping, bounded scroll, no-scroll termination, no click actions, reduced motion, Accessibility-off fallback, six-digit confident detection/fallback, anti-double-submit, privacy/no-persistence invariants.
- Existing Android + Python/broker tests no regression.
- lintDebug 0 errors; assembleDebug PASS.
- Physical Samsung S22 gate first: enable guide service, live detect Settings screen, auto-scroll to targets, highlight correct rows, guide Build Number x7, guide Wireless Debugging, highlight Pair with code, best-effort auto-read 6-digit code, connect, cancel, no crash/ANR.
- Motorola/Xiaomi recipes implemented + unit-tested only until physical hardware exists.

### Coordination
- `REMOTE-INSTALL-CONNECTION-00` remains PASS FINAL DEV / CLOSED.
- `REMOTE-INSTALL-LIVE-GUIDE-03` becomes the immediate UX task before adaptive install pilots.
- `REMOTE-ADAPTIVE-INSTALL-PILOT-01` waits for the Samsung physical live-guide gate.
- Work only under `tools/glosh-remote-spike/**`; do not touch Chrome, GloshIA, DAG, App Usuario/Admin, Device Owner production logic or existing Supabase functions.
- No push/PR/merge/deploy without explicit authorization.
