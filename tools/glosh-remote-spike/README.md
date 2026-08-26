# Glosh Remote

Glosh Remote proves secure support access to an **already-used Android phone without a PC on the client side**.

It is deliberately isolated under `tools/glosh-remote-spike/`. It does not modify App Usuario, App Admin, Chrome/GloshIA, DAG, the root Android product or Production backend code.

## Current product direction

The connection stack already passed the no-link/cross-network DEV gate. The current work is the customer experience that prepares Wireless ADB safely.

The current Samsung DEV candidate is a **notification-PIN flow**. It keeps Android
Settings under the customer's control and accepts the six-digit pairing code only
after local mDNS has identified the matching pairing endpoint.

### Customer flow

1. Open Glosh Remote and tap **CONECTAR CON SOPORTE**.
2. Grant notification permission when Android asks; the broker session then opens.
3. In Wireless Debugging, tap **Pair device with pairing code**.
4. After Glosh detects that endpoint, expand its notification, tap
   **Ingresar código**, and submit the six digits through Android `RemoteInput`.
5. Local ADB pairing, TLS connection and the encrypted Mac relay continue automatically.

The app does not accept or capture the pairing PIN. This prevents an early code
from being queued before the pairing endpoint exists and makes the notification
the single authority for manual code entry.

Broker delivery and foreground-service startup use a two-phase handoff: the
descriptor remains owned by the coordinator until `RemotePairingService` reports
`PREPARING` or `CONNECTED`. There is no elapsed-time reset between those states.

The customer never sees or enters a link, IP address, TCP port, relay descriptor, session key, shell command or terminal.

The earlier guided-assistant design remains historical context in
`GUIDED_ASSISTANT_08.md`; it is not the active Samsung route.

## Interaction authority

The active Samsung route uses no Accessibility service, overlay, Bubble, automatic
Settings click, automatic scroll, coordinate gesture or contextual code capture.
The customer owns every Android Settings action. Glosh owns broker rendezvous,
endpoint-bound notification input, local ADB pairing and the encrypted relay.

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

Canonical notification-PIN verification:

```bash
ANDROID_HOME=/Users/yejielnehmad/Library/Android/sdk \
  bash tools/glosh-remote-spike/verify_guided_assistant.sh
```

The gate runs:

- source architecture guard: no app/capture PIN path, explicit notification
  permission and endpoint-bound `RemoteInput`;
- Python protocol, broker and standby tests;
- Android JVM tests;
- Android lint;
- Android assemble;
- exact APK size/SHA report.

Expected artifact:

```text
tools/glosh-remote-spike/app/build/outputs/apk/debug/GloshRemote-Notification-PIN-DEV.apk
```

## Mac relay

Requires Python 3.9+, dependencies from `mac/requirements.txt`, and `cloudflared` for the temporary DEV tunnel.

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

1. Build and automated gate on the exact notification-PIN HEAD.
2. One cable-free S22 run:
   - one **CONECTAR CON SOPORTE** action;
   - notification permission granted explicitly;
   - no PIN input or capture inside the app;
   - `RemoteInput` appears only after the pairing endpoint is detected;
   - local ADB + authenticated Mac support;
   - complete cleanup/restoration.
3. Add other Samsung/OEM coverage only from real evidence.

No Device Owner mutation is performed by this task.
