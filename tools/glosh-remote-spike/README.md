# Glosh Remote

Glosh Remote proves secure support access to an **already-used Android phone without a PC on the client side**.

It is deliberately isolated under `tools/glosh-remote-spike/`. It does not modify App Usuario, App Admin, Chrome/GloshIA, DAG, the root Android product or Production backend code.

## Current product direction

The connection stack already passed the no-link/cross-network DEV gate. The current work is the customer experience that prepares Wireless ADB safely.

The normal product is a **professional guided assistant**, not a bot that blindly presses Android controls.

### Customer flow

1. Open Glosh Remote and tap **CONECTAR CON SOPORTE**.
2. **Paso 1 de 4:** activate Glosh Remote Accessibility.
3. **Paso 2 de 4:** confirm Developer options; Samsung enable-development screens are highlighted only if needed.
4. **Paso 3 de 4:** Glosh opens Wireless Debugging directly; the customer activates it and confirms the Wi‑Fi network if Android asks.
5. **Paso 4 de 4:** the customer taps **Pair device with pairing code**. Glosh reads a unique contextual six-digit code locally and submits it automatically when safe.
6. Local ADB pairing, TLS connection and the encrypted Mac relay continue automatically.

The app, persistent notification and compact floating coach show the same instruction and the same four-step progress. The floating card includes a small native animation for tap, switch, seven taps, code, waiting and success.

If automatic code reading is not safe, the existing six-box input and notification `RemoteInput` remain available.

The customer never sees or enters a link, IP address, TCP port, relay descriptor, session key, shell command or terminal.

Detailed design and gate:

`GUIDED_ASSISTANT_08.md`

## Interaction authority

Normal guided mode uses Accessibility for:

- selecting one trusted Settings application window;
- stable immutable snapshots;
- observing real state changes;
- highlighting known labels;
- reading the contextual pairing code;
- a Samsung enable-development fallback.

Normal guided mode does **not**:

- automatically click Settings rows, buttons or switches;
- automatically scroll Settings;
- use screen coordinates;
- read device PIN, pattern or password.

Glosh opens the narrowest resolvable Settings destination first. The customer performs Android-protected actions; Glosh observes the result and opens the next destination.

## Connection architecture

The stable rendezvous endpoint is a Supabase Edge Function. The broker stores only short-lived request metadata, an ephemeral Android RSA public key and RSA-OAEP ciphertext after operator acceptance. It never receives the plaintext join descriptor or 256-bit session key.

The Mac relay binds to `127.0.0.1`. `cloudflared` creates an outbound temporary HTTPS/WSS endpoint, so neither the Mac nor Android requires an inbound router port.

The client-side ADB RSA identity is generated in memory and discarded when the process/session ends. A new process requires a new Wireless Debugging pairing.

## Security properties

- Android 11+ standard Wireless Debugging path.
- No root.
- No public ADB and no `adb tcpip 5555`.
- ADB remains local to the phone/network and is never forwarded publicly.
- RSA-3072/OAEP-SHA256 rendezvous sealing.
- Mutual HMAC authentication.
- AES-256-GCM command/result payload protection.
- Directional sequence numbers reject replay/out-of-order frames.
- Fixed read-only command allowlist; no arbitrary remote shell.
- Output capped per action.
- Closing/revoking the session discards relay and local ADB identity.
- `FLAG_SECURE` remains enabled.
- Device credentials and pairing code are not logged.

## Standby behavior

While the Mac operator console is intentionally open:

- broker presence is renewed by heartbeat;
- standby does not consume authenticated-session TTL;
- exactly one pending customer may be autoaccepted once;
- multiple simultaneous customers fail closed to explicit technician selection;
- phone broker requests renew within a bounded overall attempt window.

## Build and automated gate

Standalone Gradle project:

```bash
./gradlew -p tools/glosh-remote-spike :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Canonical guided verification:

```bash
ANDROID_HOME=/Users/yejielnehmad/Library/Android/sdk \
  bash tools/glosh-remote-spike/verify_guided_assistant.sh
```

The gate runs:

- source architecture guard: no click/scroll ownership in the guided coordinator;
- Python protocol, broker and standby tests;
- Android JVM tests;
- Android lint;
- Android assemble;
- exact APK size/SHA report.

Expected artifact:

```text
tools/glosh-remote-spike/app/build/outputs/apk/debug/GloshRemote-Guided-DEV.apk
```

## Mac relay

Requires Python 3.9+, dependencies from `mac/requirements.txt`, and `cloudflared` for the temporary DEV tunnel.

Codex operator procedure, failure diagnosis and safe cleanup:

`OPERATOR_RUNBOOK.md`

```bash
cd tools/glosh-remote-spike/mac
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
python -m unittest test_protocol.py test_broker.py test_one_tap_standby.py
python glosh_remote_relay.py \
  --broker-url "$BROKER_BASE_URL"
```

The operator console exposes only:

```text
ping
whoami
device
owners
users
battery
status
requests
accept <request-id>
help
quit
```

There is intentionally no arbitrary remote shell.

## Physical validation order

1. Build and automated gate on the exact guided HEAD.
2. A23 Samsung laboratory run:
   - zero automatic clicks;
   - zero programmatic scrolls;
   - exact direct routes;
   - visual fallback only when needed;
   - automatic or notification pairing code;
   - local ADB + Mac support;
   - complete cleanup/restoration.
3. Same exact APK on S22 cable-free as a real customer UX run.
4. Add Motorola/Xiaomi adapters only from real evidence; keep the common guided engine.

No Device Owner mutation is performed by this guided bootstrap task.
