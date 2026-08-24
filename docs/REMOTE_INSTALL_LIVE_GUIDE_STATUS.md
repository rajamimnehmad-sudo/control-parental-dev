# Glosh Remote — Live Settings Guide

Updated: 2026-08-24 11:15 ART

## REMOTE-INSTALL-LIVE-GUIDE-03

Status: DESIGN FINAL / IMPLEMENTATION LOCAL PENDING.

This supersedes the static animation-only onboarding direction before implementation. The validated no-link broker/relay/ADB connection remains PASS FINAL DEV and must not be changed by this task.

### Product goal
A totally non-technical Android user should never feel abandoned inside Settings. Glosh Remote accompanies the user in real time while Settings is open, detects the current Settings screen, automatically scrolls until the next required row is visible, draws a lime highlight/arrow over the real target, and advances guidance when the user moves to the next screen.

### Final flow
1. User taps `CONECTAR CON SOPORTE`.
2. Glosh performs broker `discover` only. No request/RSA identity yet.
3. If support is available, Glosh explains once that it needs temporary guidance access and opens Accessibility settings.
4. User enables the dedicated Glosh Remote guide service.
5. Glosh starts `1 de 3 · Preparar el teléfono` and opens the closest Settings screen.
6. Live guide identifies the active Settings page, scrolls to the real target, highlights it, and tells the user exactly what to tap.
7. Developer-options guidance completes without consuming broker request TTL.
8. Only after that, Glosh creates the real ephemeral support request and waits for explicit operator acceptance.
9. `2 de 3 · Abrir conexión`: guide opens/locates Wireless Debugging, auto-scrolls to it, highlights it, then guides `Emparejar dispositivo con código`.
10. `3 de 3 · Ingresar código`: best-effort read the six-digit pairing code locally from the expected pairing dialog; if high-confidence detection is unavailable, fall back automatically to six large in-app boxes and RemoteInput notification.
11. All code-entry paths call the same pairing entrypoint with single-submit authority.
12. On `CONNECTED`, overlay disappears, temporary guide state is cleared, and the guide AccessibilityService calls `disableSelf()` when supported.

### Accessibility scope and privacy
- Dedicated `AccessibilityService` used only for temporary installer guidance.
- Declare `BIND_ACCESSIBILITY_SERVICE` and `canRetrieveWindowContent=true`.
- Use `FLAG_REPORT_VIEW_IDS` and `FLAG_RETRIEVE_INTERACTIVE_WINDOWS` only if required by the matching engine.
- Do not statically observe the whole phone. At runtime, resolve the actual package(s) handling Android Settings intents on that device and set `AccessibilityServiceInfo.packageNames` dynamically to only those trusted Settings package(s) while onboarding is active.
- Outside an active onboarding, do no tree scanning and render no overlay.
- Ignore Chrome, Gmail, WhatsApp, keyboards, notifications and all unrelated apps/windows.
- Never take screenshots for this guide.
- Never collect, upload, persist or log Accessibility-tree dumps, Settings text, pairing codes, nonces or cryptographic material.

### Live target engine
For every guide step:
1. Determine current OEM recipe + expected screen + expected target.
2. Traverse the active Settings tree only.
3. Score candidates using bounded signals: exact label, localized aliases, stable view/resource id when present, content description, parent/child context, screen title/context and clickability/role.
4. Require a confidence threshold. If two candidates are similarly plausible, do not highlight either; enter rescue mode.
5. If target is already visible, obtain bounds in screen coordinates and highlight the real row.
6. If target exists but is off-screen, call `ACTION_SHOW_ON_SCREEN` first.
7. Otherwise find the nearest scrollable ancestor/container and perform bounded `ACTION_SCROLL_DOWN` or `ACTION_SCROLL_FORWARD`.
8. Wait for `TYPE_VIEW_SCROLLED`, `TYPE_WINDOW_CONTENT_CHANGED`, `TYPE_WINDOW_STATE_CHANGED` or `TYPE_WINDOWS_CHANGED`, debounce, then re-scan.
9. Stop immediately when target becomes visible, the screen changes, no movement occurs, scrolling is unsupported, service is disabled or the bounded attempt limit is reached.
10. Never `ACTION_CLICK` Settings targets. User taps rows, switches and system confirmations.

Auto-scroll is a required feature, not optional best effort. Use strict limits (for example maximum 6 scroll actions per target, plus no-progress detection) so the guide cannot loop.

### Overlay geometry
- Use `TYPE_ACCESSIBILITY_OVERLAY`; do not require generic `SYSTEM_ALERT_WINDOW` if accessibility overlay is sufficient.
- Highlight layer is full-screen, transparent, NOT_TOUCHABLE and NOT_FOCUSABLE.
- Draw a soft lime outline/pulse around the target bounds plus a small arrow/hand indicator.
- Account for display insets, status/navigation bars, rotation and density when mapping node bounds to overlay coordinates.
- Recompute bounds after every scroll/window/layout change.
- Floating Glosh card is a separate small touchable overlay, automatically placed away from the target; it can minimize to a small Glosh bubble.
- Respect `ValueAnimator.areAnimatorsEnabled()`; reduced-motion mode uses a static highlight.

### OEM recipes
Initial recipes:
- Samsung: `Acerca del teléfono → Información de software → Número de compilación ×7`.
- Motorola: `Acerca del teléfono` or `Sistema → Acerca del teléfono → Número de compilación ×7`.
- Xiaomi/Redmi/POCO: `Acerca del teléfono → Información detallada y especificaciones → versión OS/MIUI repetidamente`, then `Ajustes adicionales → Opciones de desarrollador`.
- Generic Android fallback when the OEM recipe cannot be matched confidently.

Recipes must support localized/variant labels through explicit bounded aliases, not broad fuzzy matching. Architecture must allow later variants by OEM/Android version without rewriting the engine.

### Developer-options stage
- Samsung/Motorola: locate/scroll/highlight `Número de compilación`.
- Xiaomi family: locate the appropriate OS/MIUI/HyperOS version item only when recipe/context confidence is high.
- Best-effort count target click events to show `1 de 7`, `2 de 7`, etc.; never depend solely on click counting to advance.
- If the OEM exposes the system confirmation/toast text indicating developer mode is enabled, advance automatically only when confidence is high; otherwise use the existing simple human confirmation fallback.

### Wireless Debugging stage
- Open the most specific resolvable Settings route available; fallback is `Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS`.
- Live scanner locates `Depuración inalámbrica`; if necessary auto-scrolls and highlights it.
- User toggles it.
- Scanner then locates/highlights `Emparejar dispositivo con código` and scrolls as needed.
- Never auto-toggle or auto-click either control.

### Six-digit pairing code
Only attempt auto-detection while the current expected state is specifically the Wireless Debugging pairing-code dialog/window.

High-confidence code detection requires:
- exactly six decimal digits;
- expected pairing-dialog state;
- nearby semantic context indicating pairing/wireless-debugging/code;
- no competing six-digit candidates.

If confidence is high:
- keep code only in memory;
- feed it into the same `RemotePairingService` entrypoint used by in-app input and notification RemoteInput;
- immediately clear references/buffers where practical;
- never log, persist, upload or send it to Supabase/broker.

If confidence is not high:
- do nothing automatic;
- show six large numeric boxes in Glosh;
- auto-submit exactly at six digits;
- keep RemoteInput notification as an alternative.

Pairing authority:
- at most one pairing attempt active;
- duplicate code/app-notification-accessibility races must not start a second pairing;
- failure releases the guard and allows a new code;
- success clears all code state.

### Rescue / correction behavior
`ME PERDÍ` never resets the secure session.

On tap:
1. re-scan current Settings window;
2. infer current recipe step only from bounded known screens/targets;
3. if recognized, auto-scroll/highlight the correct target again;
4. if unrecognized, remove highlight and show `Volvamos al punto correcto` with `ABRIR AJUSTES CORRECTOS`.

If user navigates to the wrong Settings page, the assistant should say a short corrective message, not highlight unrelated controls.

### State and recovery
Keep a dedicated UX state machine separate from crypto/session state, for example:
- GUIDE_PERMISSION
- DEV_OPTIONS_ROUTE
- DEV_OPTIONS_TARGET
- SUPPORT_PREPARING
- WIRELESS_DEBUGGING_TARGET
- PAIR_CODE_TARGET
- PAIRING
- CONNECTED

Persist only non-sensitive UX state needed for Activity recreation (OEM family, guide step, confirmation flags). Never persist RSA private key, nonce, descriptor, session key or pairing code.

If process death destroys ephemeral crypto state, fail closed and resume from the nearest safe UX checkpoint with human copy instead of pretending the secure session survived.

### Broker TTL timing
Preserve the previously approved timing:
- `CONECTAR CON SOPORTE` performs `discover` only;
- no real broker request while the user is learning Developer Options;
- create request/identity/nonce only after the guide reaches the connection stage;
- keep the validated bounded expired-request renewal (max 5 attempts) unchanged.

### End of guide
When service state becomes `CONNECTED`:
- remove all accessibility overlays immediately;
- clear live-guide state and any code reference;
- dynamically clear package/event interest where appropriate;
- call `AccessibilityService.disableSelf()` on supported API levels;
- show `Guía terminada ✓` briefly in Glosh.

If Accessibility is turned off early or OEM nodes are unusable, fall back to the static OEM animation/manual guide. Accessibility improves UX but is never the only path to completion.

### Modularity
Keep new code isolated under cohesive packages, e.g.:
- `guide/accessibility/LiveGuideAccessibilityService`
- `guide/accessibility/SettingsPackageResolver`
- `guide/accessibility/SettingsTreeScanner`
- `guide/accessibility/TargetMatcher`
- `guide/accessibility/AutoScrollController`
- `guide/accessibility/HighlightOverlayController`
- `guide/accessibility/GuideBubbleController`
- `guide/oem/OemDetector`
- `guide/oem/OemRecipe*`
- `guide/state/GuideStateMachine`
- `guide/pairing/PairingCodeDetector`

Do not inflate `MainActivity` or `RemotePairingService` with guide-specific responsibilities.

### Security boundaries / non-regression
- No screenshot capture.
- No arbitrary screen scraping.
- No observation outside resolved Settings packages during active onboarding.
- No automatic taps on permission toggles, Developer Options, Wireless Debugging or pairing confirmation.
- Existing Supabase broker, RSA/OAEP, HMAC/AES, relay, allowlist, retry-expired logic, `FLAG_SECURE`, IDLE/PREPARING/CONNECTED authority and no-link architecture must not change.

### Required tests
- OEM detector: Samsung, Motorola, Xiaomi, Redmi, POCO, Generic.
- Settings-package resolver and dynamic package scoping.
- Target matcher exact/alias/context/id scoring.
- Ambiguous candidate rejection.
- Wrong-screen rejection.
- Bounds mapping with insets/rotation.
- `ACTION_SHOW_ON_SCREEN` success.
- Fallback scroll down/forward.
- target after N scrolls.
- bounded max-attempt stop.
- no-progress stop.
- screen-change stop.
- service-disabled stop.
- no `ACTION_CLICK` path anywhere.
- reduced motion.
- non-Settings package ignored.
- Accessibility-off fallback.
- guide UX state recreation with no secrets persisted.
- process/ephemeral-session loss fail-closed.
- six-digit contextual detection success.
- random six-digit rejection.
- ambiguous multiple-code rejection.
- in-app fallback input.
- one pairing attempt across Accessibility/app/notification race.
- failure allows retry; success clears code.
- `CONNECTED` removes overlay and requests `disableSelf()`.
- all existing Android + Python/broker tests remain green.

### Physical gate — Samsung S22
Validate:
1. enable temporary guide service;
2. dynamic scoping resolves Settings package(s);
3. detect Samsung;
4. open About phone;
5. auto-scroll to real `Información de software` if needed;
6. highlight bounds align with real row;
7. user taps;
8. auto-scroll/highlight real `Número de compilación`;
9. guide x7;
10. broker request still absent during learning stage;
11. request begins only at support-preparing stage;
12. open Developer Settings;
13. auto-scroll/highlight Wireless Debugging;
14. user enables it;
15. auto-scroll/highlight Pair with code;
16. user opens dialog;
17. attempt high-confidence six-digit auto-detection;
18. if detected, pair automatically; otherwise manual fallback must work;
19. CONNECTED;
20. overlay removed and guide service auto-disabled;
21. cancel/revoke still PASS;
22. no crash/ANR and no broker/crypto regression.

Motorola/Xiaomi recipes are implemented + unit-tested only until physical hardware exists.

### Coordination
- `REMOTE-INSTALL-CONNECTION-00`: PASS FINAL DEV / CLOSED.
- `REMOTE-INSTALL-LIVE-GUIDE-03`: immediate UX task.
- `REMOTE-ADAPTIVE-INSTALL-PILOT-01`: waits for Samsung physical live-guide gate.
- Work only under `tools/glosh-remote-spike/**`; do not touch Chrome, GloshIA, DAG, App Usuario/Admin, Device Owner production logic or existing Supabase functions.
- No push/PR/merge/deploy without explicit authorization.
