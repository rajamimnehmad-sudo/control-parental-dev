# Glosh Remote — One-Tap Adaptive Autopilot UX

Updated: 2026-08-24

## Product contract

The customer-facing product is **one primary action**:

`Instalar Glosh Remote → abrir → CONECTAR CON SOPORTE`

After that tap, Glosh Remote owns the flow. Internal technical states are not exposed as separate customer controls.

Target end-to-end behavior:

`CONNECT → discover operator → request/accept support → detect phone state → shortest safe Samsung route → enable Developer Options only if needed → enable Wireless Debugging only if needed → open pairing-code dialog → read exactly one contextual six-digit code when safe → pair locally on the phone → local ADB ready → existing secure relay session becomes operational on Mac → CONNECTED`.

The pairing code should normally stay local to the phone: the phone uses it to pair its local ADB client with adbd, then reports ADB-ready over the already authenticated secure channel. The Mac does not need the six-digit secret to be displayed or manually re-entered.

## Customer UI

### Idle
One primary button only:
- `CONECTAR CON SOPORTE`

Optional secondary surface:
- a small help/about entry, not part of the normal flow.

### Running
No technical buttons such as:
- `Volver`
- `Me perdí`
- `Mostrame`
- `Reintentar pairing`
- `Abrir opciones`
- duplicated status buttons

Show a single progress surface such as:
- `Conectando con soporte…`
- `Preparando el teléfono…`
- `Activando conexión segura…`

Only a small `Cancelar` action is normally visible.

### User action required
Expose exactly one instruction/CTA at a time, only for actions Android does not let Glosh complete safely itself, for example:
- enable Restricted Settings / Accessibility bootstrap;
- enter device PIN/password/pattern in the Android-owned credential UI;
- connect to Wi-Fi if none is usable;
- resolve an admin/policy block.

After the user completes that external action, Autopilot resumes automatically from a fresh state probe. Do not make the user press `Volver` or manually advance the wizard.

### Connected
One clear terminal state:
- `Conectado con soporte`

No duplicate connection/request/pairing states.

## Shortest-path rules

At every launch/run, inspect real state and take the shortest safe branch:

1. secure support session already connected → DONE;
2. local ADB already usable → skip Settings/pairing and attach to support;
3. prior pairing can reconnect → reconnect before opening Settings;
4. pairing dialog already open → consume it immediately;
5. Developer Options already reachable → skip Build Number;
6. Wireless Debugging already ON → skip toggle/network confirmation;
7. otherwise use Samsung recipe to enable only the missing prerequisites.

No fixed linear wizard is allowed as the normal path.

## Automatic Samsung route

When prerequisites are missing, Autopilot may perform exact verified Accessibility actions:

- open Developer Settings directly first;
- if unavailable, open About phone;
- click `Información de software`;
- click `Número de compilación` up to seven times, revalidating before every tap;
- pause for Android credential UI if required;
- reopen Developer Settings;
- open/enable `Depuración inalámbrica` only when needed;
- accept only the exact expected Wi-Fi/network confirmation;
- open `Vincular dispositivo con código de vinculación`;
- detect exactly one contextual six-digit pairing code;
- submit that code automatically to the existing local pairing service;
- verify local ADB;
- hand off to the frozen secure support stack.

Any ambiguous screen/candidate fails closed into a minimal assistance state; never blind-click coordinates.

## Mac/operator presence — no visible short expiry

The current UX defect where the customer frequently sees `soporte no disponible` because the Mac waiting window expires is not acceptable.

Separate security leases from user-visible availability:

### Operator presence lease
- Mac operator explicitly enters `Esperar cliente` / support-ready mode.
- Presence is represented internally by a short renewable lease.
- Recommended default: lease 120 seconds, heartbeat/renew every 30 seconds.
- While the Mac operator process remains healthy and support-ready, renewal is automatic and continuous; from the customer's perspective support remains available indefinitely.
- If Mac crashes, sleeps or loses network, the short lease naturally expires, limiting stale false availability.

### Phone support request
- Once the phone creates a request, request expiry is an internal lease, not a customer-visible failure.
- Recommended default: 10-minute request lease, renewed automatically every 60 seconds while the connect flow remains active and the user has not cancelled.
- If a request expires/revokes transiently, phone should recreate/renew it automatically while preserving the single `Conectando con soporte…` UX.

### Accepted secure session
- Do not expire in the middle of an active preparation/install merely because a fixed wall-clock TTL elapsed.
- Use activity/sliding renewal or an install-session lifetime with a bounded maximum; explicit cancel/revoke still closes immediately.

## Operator acceptance modes

For the current single-technician workflow, optimize for zero customer friction:

### Preferred: one-use `Esperar próximo cliente`
The Mac operator intentionally opens one support slot. The first valid request for that slot is auto-accepted, then the slot closes to additional customers. This preserves explicit technician intent but removes the extra approval click during the customer's one-tap flow.

### Future multi-customer queue
Manual accept may remain for a later multi-technician/multi-customer console. The phone still sees only `Conectando con soporte…` and keeps its request renewed automatically until accepted or cancelled.

## Normal customer interventions

The final one-tap goal still has a few platform-imposed exceptions:

- first-time Accessibility/Restricted Settings bootstrap may require explicit user action;
- device credential/PIN/password/pattern must always be entered by the user in Android's own UI;
- no usable Wi-Fi requires the user to connect Wi-Fi;
- policy/admin restrictions can require a human resolution.

These are exceptions, not wizard steps. Once resolved, Autopilot resumes automatically.

## Acceptance criteria

A normal compatible Samsung should require:

1. install/open Glosh Remote;
2. complete the one-time Accessibility bootstrap if not already done;
3. tap `CONECTAR CON SOPORTE` once;
4. no further Glosh buttons through local ADB + secure Mac connection.

Pass requires:
- no repeated/duplicated states;
- no normal-path `Volver`, `Me perdí`, `Mostrame` buttons;
- operator availability does not visibly expire while Mac remains support-ready;
- request automatically survives/renews while connecting;
- automatic route reaches pairing dialog;
- unique six-digit code is consumed automatically;
- local ADB pairing happens automatically;
- Mac session becomes operational automatically after local ADB readiness;
- zero wrong automatic clicks;
- explicit user intervention only for Android-owned protected prerequisites.

## Current candidate impact

`eaa44f1ba5da204d66a720ae6f5f805699ee22ee` / APK SHA `ca222dbfabaa776a9e304c0e643923caf5ce394ad785a72481c8a58654b14920` remains a valuable automated technical checkpoint, but it must not be declared final product UX if it still exposes duplicated guide controls or short-lived support availability. A One-Tap hardening pass is required before final closure if those defects reproduce on this exact candidate.
