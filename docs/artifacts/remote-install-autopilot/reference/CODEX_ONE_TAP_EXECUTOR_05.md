# Codex executor — REMOTE-INSTALL-ONE-TAP-HARDENING-05

This is an environment execution ticket. Product/architecture decisions are closed by ChatGPT.

## Exact base

Remote handoff branch exists only as shared source reference:
`handoff/remote-autopilot-eaa44`

Base HEAD:
`eaa44f1ba5da204d66a720ae6f5f805699ee22ee`

Local work must start from the existing clean Remote Installer worktree at this exact commit or an isolated worktree created from it. Do not touch unrelated dirty worktrees.

Read first from `coordination/remote-install-live-guide-v2` without merging it:

- `docs/REMOTE_INSTALL_ONE_TAP_HARDENING_05.md`
- `docs/REMOTE_INSTALL_LIVE_GUIDE_STATUS.md`

## Scope

Write owner: Codex Remote Installer executor.

Allowed paths only:
`tools/glosh-remote-spike/**`

Do not touch Chrome, GloshIA, DAG, app-user, app-admin, Supabase, Device Owner production logic or any other module.

No push, PR, merge, deploy, Production or spending.

## Frozen implementation

### 1. Product One-Tap UI

Modify `MainActivity.java` / `WizardLayout.java` only as needed so normal product flow is:

`CONECTAR CON SOPORTE` → passive automatic progress → `Conectado con soporte`.

HOME:
- primary CTA text exactly `CONECTAR CON SOPORTE`;
- copy says Glosh prepares the phone automatically and asks for help only if Android requires it;
- remove normal promise that the customer must follow guide taps or type six numbers.

Normal in-progress screens:
- no `MOSTRARME`;
- no `MOSTRARME DE NUEVO`;
- no `ME PERDÍ`;
- no `VOLVER AL CÓDIGO`;
- no normal-path `ABRIR AJUSTES` / `ABRIR DEPURACIÓN INALÁMBRICA` progression controls;
- no `1 de 3 / 2 de 3 / 3 de 3` progression model;
- one passive progress surface plus `CANCELAR` at most.

GUIDE_PERMISSION when Accessibility is not enabled:
- one required CTA `ACTIVAR AUTOMATIZACIÓN`;
- remove `CONTINUAR SIN GUÍA` from product path.

Credential interruption:
- passive copy asking user to enter Android PIN/pattern/password;
- only cancel remains; never capture credential.

Automatic pairing path:
- while code capture/pairing is automatic, show `Completando conexión…` rather than six-digit instructions.

Manual pairing fallback:
- only when `PairingUiState.WAITING_FOR_CODE` / `CODE_FAILED` remains after automatic path cannot supply a trusted code;
- show the existing six-box input;
- concise instruction + cancel only; no guide loop.

UNAVAILABLE:
- title/copy `No pudimos contactar al técnico`;
- primary `REINTENTAR` directly starts a new support attempt; no `VOLVER` intermediary.

CONNECTED:
- `Conectado con soporte`;
- secondary `FINALIZAR CONEXIÓN`.

Old guide helper methods may remain for debug/fallback, but normal product rendering must not expose them.

Do not change the safe automatic click/state-machine implementation except for a bug required by tests.

### 2. Phone broker renewal

Current `BrokerWaitPolicy` limits renewal to 5 short-lived requests. Replace count-as-lifetime behavior with a bounded overall active attempt window.

Target:
- overall active support attempt: 30 minutes;
- each expired broker request still generates a fresh request ID, nonce and fresh ephemeral RSA identity;
- no identity/nonce reuse;
- expiration during active flow is transparent;
- cancel/reset immediately prevents further renewals and revokes current request best-effort.

Keep a defensive high upper bound on individual request creations if useful, but the normal limit must be wall-clock based rather than five broker TTLs.

Polling transient network failures:
- retry with bounded exponential/backoff (e.g. 500 ms → 1 s → 2 s → max 5 s);
- cap consecutive transient failures (target 6) before surfacing error;
- any successful poll resets transient-failure count;
- do not treat a single temporary poll failure as `UNAVAILABLE`.

Do not retry ambiguous create POSTs with the same request identity after an unknown network outcome. Preserve fail-closed semantics.

### 3. Mac broker presence heartbeat

`BrokerOperatorClient.register()` already maps to `operator_open` and may be called repeatedly as lease renewal.

Add an async heartbeat while the console is intentionally open:
- target interval 60 seconds;
- execute `broker.register` in executor;
- transient heartbeat failure is logged without closing the console;
- next heartbeat retries normally;
- task stops on `session.stop_event`;
- final `broker.close()` remains best-effort in shutdown.

Do not change broker schema or Edge Function.

### 4. Secure-session TTL begins on authenticated agent

In `RemoteSession`:
- replace startup `expires_at = now + duration` with `duration_seconds` and `expires_at = None`;
- waiting for a phone does not consume authenticated-session lifetime;
- handler accepts waiting connections while `expires_at is None`;
- after successful agent authentication/claim, set `expires_at = monotonic() + duration_seconds` exactly once;
- command expiry checks only when `expires_at is not None`;
- `expire_session()` first waits for `agent_ready`, then enforces the existing authenticated-session deadline;
- reconnect within the same already-started session does not reset/extend expiry unless existing semantics explicitly require a new RemoteSession.

Update console copy to say the configured minutes are the duration **once connected**, not time remaining while waiting.

### 5. Safe one-client auto-accept

Technician starting this relay+broker console is explicit intent to wait for one client.

Add an auto-accept coroutine:
- poll `broker.pending()` about every 1–2 seconds;
- if 0 pending: no action;
- if exactly 1 pending AND no connected agent AND this RemoteSession has not already auto-accepted a request: seal current descriptor and call `broker.accept` automatically;
- remember accepted request ID locally and never auto-accept a second request in the same RemoteSession;
- if 2+ pending: do not choose; print one clear fail-closed message and leave explicit `requests` / `accept <id>` fallback available;
- transient broker list failure does not terminate the relay;
- do not print descriptor, session key, nonce or ciphertext.

`announce_pending_requests` may be simplified/combined with this worker, but avoid two competing automatic acceptors.

## Preserve

Do not redesign or weaken:
- SupportSession broker sealing;
- HMAC/AES/seq/replay;
- relay protocol;
- allowlist;
- ephemeral ADB identity;
- Android trusted-window/snapshot/generation click guards;
- contextual six-digit detector;
- no public ADB / no tcpip 5555.

## Tests

Run existing suite first/after.

Add narrow tests proving:

Android/JVM:
- active request can renew past historical five-attempt boundary while overall 30-minute window remains open;
- overall wait window eventually stops renewal;
- reset/cancel resets policy;
- existing Autopilot/pairing tests remain green.

Python:
- operator heartbeat calls register repeatedly while waiting;
- heartbeat exception is non-terminal;
- `RemoteSession.expires_at is None` before authenticated agent;
- session expiry starts only after authentication;
- 0 pending => auto-accept none;
- exactly 1 pending => one accept;
- 2+ pending => zero auto-accept;
- same RemoteSession never auto-accepts a second request.

Then mandatory gates:

1. Android/JVM tests;
2. Python/broker tests;
3. `lintDebug` — zero errors;
4. `assembleDebug` — PASS.

No phone required.

## Local commit / evidence

After PASS:
- one clean local commit, suggested message: `fix(remote): make autopilot one-tap and keep support standby alive`;
- report final HEAD;
- report changed files;
- report exact test totals;
- report lint/assemble;
- worktree clean.

Create exact candidate:
`GloshRemote-OneTap-DEV.apk`

Report:
- absolute path;
- size bytes;
- SHA-256.

Also generate:
- `/tmp/REMOTE-INSTALL-ONE-TAP-HARDENING-05.patch` = diff from `eaa44f1ba5da204d66a720ae6f5f805699ee22ee` to final HEAD;
- `/tmp/REMOTE-INSTALL-ONE-TAP-HARDENING-05-report.txt` with the evidence above and no secrets.

## Deliver artifacts to ChatGPT without Git push

Serve only these three exact files through a localhost-only temporary HTTP server + Cloudflare Quick Tunnel using an unguessable random path token:

- final APK;
- patch;
- report.

No directory listing. No `.git`. No credentials.

Return three complete HTTPS URLs plus SHA-256 + sizes.

Keep tunnel alive for handoff, then ChatGPT/user will instruct closure. Do not push any code.

## Result

Return `PASS`, `BLOCKED` or `FAILED`.

PASS requires all automated gates, clean commit/worktree, exact APK, patch/report and handoff URLs.
Physical S22 UX is intentionally later and is not part of this execution ticket.
