# Glosh Remote — Live Settings Guide

Updated: 2026-08-24 20:15 ART

## REMOTE-INSTALL-LIVE-GUIDE-03

Status: **FAILED UX PHYSICAL / SUPERSEDED BY V2**.

The no-link broker/relay/ADB connection remains PASS FINAL DEV. The physical failure is isolated to the temporary Settings guidance layer.

Authoritative local implementation chain before V2:
`475bd35b... → 1a537f7a... → 99fcf2bb... → 122c45b9... → 93735b1c...`

## REMOTE-INSTALL-LIVE-GUIDE-V2-04

Status: **AUTOPILOT-FIRST ROUTE APPROVED / IMPLEMENTATION PENDING**.

Codex already completed the read-only audit of the real Android code at `93735b1c2a9f493dcd491556b23920c50c3d66f2`. The structural findings remain authoritative: explicit Settings window selection, immutable snapshots, anti-stale generation/window/fingerprint guards and fail-closed matching are required.

### Route change — 2026-08-24

Guide-first is no longer the primary UX. The active design is **Autopilot first + thin guided fallback**. See `docs/REMOTE_INSTALL_AUTOPILOT_DECISION.md`.

For known OEM routes, Glosh Remote should automatically activate only exact HIGH-confidence clickable Settings nodes until Android reaches the Wireless Debugging six-digit pairing-code screen. The user intervenes only for protected credential/security confirmations or when confidence is insufficient.

The V2 safety core is retained because automatic clicks require stronger guarantees than highlights:
- exactly one trusted Settings `TYPE_APPLICATION` window;
- overlay/IME/apps ajenas excluded;
- stable immutable snapshot;
- generation/window/fingerprint anti-stale token;
- OEM recipe + exact alias/context;
- a single HIGH-confidence candidate with clear margin;
- fresh node re-acquisition immediately before `ACTION_CLICK`;
- expected-screen transition after each click;
- any ambiguity/unexpected screen => stop automation and fall back to guide.

### Samsung pilot

Preferred route:
1. Fast-path Developer Options / Wireless Debugging if already available.
2. Otherwise `Acerca del teléfono` → `Información de software`.
3. `Número de compilación` ×7 automatically when safe.
4. If Android requests credential/protected confirmation, stop and ask the user to complete it.
5. Open `Opciones de desarrollador`.
6. Reach/enable `Depuración inalámbrica` using exact clickable/toggle semantics.
7. Open `Vincular dispositivo con código de vinculación`.
8. Reach the six-digit pairing-code dialog.
9. Minimum closure for this pilot: stop there reliably. Optional enhancement: read exactly one contextual six-digit code and feed the existing guarded pairing path.

The guide (`MOSTRARME`, coach/highlight) remains a fallback, not the primary route. Do not spend the current cycle perfecting autonomous guide scrolling or broad human-scroll heuristics unless needed for fallback safety.

### Fast validation route

A23 is the instrumented lab device:
- Samsung SM-A235M / Android 14 / API 34;
- USB/ADB allowed only for DEV install/log/evidence;
- implement Autopilot + safety core;
- technical tests/lint/assemble;
- pilot gate: reach six-digit pairing screen safely on repeated runs;
- one wrong automatic click = FAIL;
- protected/ambiguous state stopping safely = expected behavior, not failure.

Do **not** run full broker/relay Gate B/C in this cycle merely to validate the navigation change. The no-link connection stack is already PASS FINAL DEV and should be preserved untouched.

After A23 autopilot pilot passes, send the exact APK to S22. S22 remains cable-free and is used as the real customer UX check.

### GitHub handoff

Branch: `coordination/remote-install-live-guide-v2`.

Do not use the deprecated binary ZIP. Use the text references plus `docs/REMOTE_INSTALL_AUTOPILOT_DECISION.md`.

## Coordination

- `REMOTE-INSTALL-CONNECTION-00`: PASS FINAL DEV / CLOSED.
- `REMOTE-INSTALL-LIVE-GUIDE-03`: FAILED UX / SUPERSEDED.
- `REMOTE-INSTALL-LIVE-GUIDE-V2-04`: AUTOPILOT-FIRST is the active route; A23 pilot to six-digit code is next.
- `REMOTE-INSTALL-MAC-OPERATOR-04`: preserved; waits for remote installer UX stability.
- `REMOTE-INSTALL-PRECHECK-05`, `REMOTE-INSTALL-PIPELINE-06`, `REMOTE-INSTALL-DEVICE-OWNER-COMMIT-07`: preserved.
- `REMOTE-ADAPTIVE-INSTALL-PILOT-01`: waits for A23 Autopilot pilot + S22 cable-free UX confirmation.
- Do not touch Chrome, GloshIA, DAG, App Usuario/Admin, Supabase or production Device Owner logic.
