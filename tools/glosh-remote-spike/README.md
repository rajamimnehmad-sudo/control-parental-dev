# Glosh Remote Spike

`REMOTE-FULL-ADB-24`

Laboratory project to prove remote technical access to an **already-used Android phone, without a PC on the client side**.

It is deliberately isolated under `tools/`: it does not modify App Usuario, App Admin, Chrome/GloshIA, the root Gradle project or Production backend code.

## Client UX target

The normal flow no longer transports session material through the UI:

1. The client opens Glosh Remote and taps **CONECTAR CON SOPORTE**.
2. The app creates an in-memory RSA rendezvous identity and requests support from the configured broker.
3. The Mac operator sees the manufacturer/model and explicitly accepts the random request ID.
4. The operator encrypts the internal join descriptor for that one Android public key.
5. Android decrypts it locally and guides the user through Developer options and Wireless debugging.
6. The client types only Android's six-digit pairing code in the Glosh notification.
7. Pairing, local ADB TLS and the existing remote relay continue automatically.

The client never sees or enters a link, IP, TCP port, relay, key, shell command or terminal. A direct
`gloshremote://join?...` intent remains available only in debug builds as a hidden laboratory fallback;
there is no paste field or descriptor UI.

## PASS for this spike

PASS requires all of the following on Android 11+:

- no PC on the client side;
- no root;
- no public ADB / no `adb tcpip 5555`;
- local Wireless Debugging pairing succeeds on the same phone;
- Mac and phone may be on different networks;
- Android opens an outbound WSS connection to the temporary Mac relay;
- mutual one-time-key authentication succeeds;
- after explicit user pairing, Mac has the practical `uid=2000(shell)` surface of a USB ADB session;
- shell diagnostics, `logcat`, `dumpsys`, ADB Sync file transfer, APK installation and `dpm` work remotely;
- every transferred file is bounded and verified by declared size plus SHA-256 before ADB Sync;
- command/results are additionally AES-256-GCM encrypted end-to-end;
- closing, losing or revoking the session closes local ADB too;
- a new process/session requires a new ADB pairing identity.

The operator can install Glosh and run the exact Device Owner provisioning command. Android still
enforces its normal prerequisites: no incompatible existing owner, eligible primary user and account
state, and any OEM/user confirmations that cannot be bypassed by ADB shell.

## Ephemeral ADB identity

The ADB RSA private key and certificate are generated **in memory only** when the foreground service starts. They are not written to SharedPreferences, files, database or Android backup.

Consequences are intentional:

- closing the session drops the identity;
- killing the process drops the identity;
- reboot drops the identity;
- another support session requires pairing again.

For a temporary installation bridge this is preferable to leaving a reusable ADB credential on a customer's phone.

## Support Session Broker

`BROKER_BASE_URL` defaults in this DEV candidate to the deployed Supabase Edge Function and can be
overridden at build time with:

```bash
./gradlew -p tools/glosh-remote-spike \
  -PbrokerBaseUrl=https://broker.example.invalid \
  :app:assembleDebug
```

With no stable HTTPS broker configured, the normal button fails cleanly with
“Soporte remoto no está disponible en este momento.” It never falls back to asking the client for a link.

The broker contract is TTL-bound, single-use and rate-limited. It stores request metadata, the
ephemeral Android public key and an RSA-OAEP ciphertext after explicit operator acceptance. The join
descriptor and its 256-bit session key never reach the broker in plaintext. Cancellation destroys the
Android private-key reference and revokes the pending request best-effort.

## Relay and rendezvous separation

The stable rendezvous endpoint is a Supabase Edge Function. The relay remains a separate, temporary
Mac process; the broker only transfers metadata and ciphertext and never sees the descriptor plaintext.

The Mac relay binds only to `127.0.0.1`; `cloudflared` creates an outbound Quick Tunnel with a random HTTPS/WSS endpoint. Neither Mac nor Android needs an inbound router port.

Quick Tunnel is **lab-only**. If the spike passes, it can later be replaced by Glosh-owned infrastructure without changing the local ADB bootstrap or encrypted command contract.

## Build

Standalone Gradle project:

```bash
./gradlew -p tools/glosh-remote-spike :app:testDebugUnitTest :app:assembleDebug
```

Expected APK:

```text
tools/glosh-remote-spike/app/build/outputs/apk/debug/app-debug.apk
```

The actual Android SDK/JitPack build is the point where this ticket hands off to Codex on the Mac.

## Mac relay and broker

Requires Python 3.9+ and `cloudflared` for the physical internet gate.

```bash
cd tools/glosh-remote-spike/mac
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
python -m unittest test_protocol.py
python -m unittest test_broker.py
python glosh_remote_relay.py
```

For a local contract gate, start the in-memory reference broker:

```bash
export GLOSH_REMOTE_OPERATOR_KEY='<random-base64url-token>'
python support_session_broker.py --operator-key "$GLOSH_REMOTE_OPERATOR_KEY"

python glosh_remote_relay.py \
  --broker-url http://127.0.0.1:8766
```

In broker mode the relay does not print the descriptor. It prints pending requests and requires
`accept <request-id>`. Without broker flags it retains the direct descriptor as a DEV-only gate tool.

On the Mac the support commands are:

```text
ping
whoami
device
owners
users
battery
shell <command>
push <local-file> <remote-path>
install <local-apk>
owner <package/admin-component>
provision <local-apk> <package/admin-component>
status
requests
accept <request-id>
help
quit
```

`shell` is deliberately equivalent to an authorized ADB shell, not root. `provision` transfers the
APK, installs it, removes the temporary copy and invokes `dpm set-device-owner --user 0`.

## Gate 0 — relay without Android

Run `glosh_remote_relay.py` in one terminal and `mock_agent.py '<descriptor>'` in another. Verify mutual authentication, `ping`, `device`, revocation and reconnect behavior before involving a phone.

## Gate 1 — real Android DEV fallback

1. Install the APK.
2. Start the Mac relay and broker lab.
3. Tap **CONECTAR CON SOPORTE** and explicitly accept the pending request on the Mac.
4. Follow the three-step Android guide.
5. Enable **Wireless debugging**.
6. Tap **Pair device with pairing code**.
7. Pull down the Glosh Remote notification and enter the six digits.
8. Wait for **Conectado con soporte**.
9. From the Mac run diagnostics, file transfer, install or Device Owner provisioning.
10. Use `quit` / **Cancelar conexión** and prove commands no longer execute.
11. Turn Wireless debugging off after the lab gate.

## Security properties

- ADB remains on the phone/local network; it is never forwarded to the Internet.
- Session secret is random 256-bit material and is not persisted by the APK or visible to the broker.
- Rendezvous uses an in-memory Android RSA-3072 key and standard RSA-OAEP SHA-256 sealing.
- Broker requests are random, short-lived, single-use, explicitly accepted and replay-protected.
- Mutual HMAC challenge/response authenticates both ends before command traffic.
- Command/result bodies use AES-256-GCM with directional sequence-bound AAD.
- Monotonic sequence numbers reject replayed encrypted frames.
- Shell text and file chunks exist only inside authenticated AES-256-GCM frames.
- Shell output is capped to 2 MiB per command and relay frames to 4 MiB.
- A file is accepted only in order, up to 512 MiB, and must match its declared size and SHA-256.
- Staging files are deleted on success, failure, disconnect or process cleanup.
- Relay loss is fail-closed and the temporary ADB identity is discarded.
- `FLAG_SECURE` remains enabled throughout the guided flow.
- Notification permission is required on Android 13+ because the six-digit pairing input lives in the notification.

## Known limitations / next gates

- Android 11+ only for V0.
- The stable DEV broker and no-link cross-network connection have passed on the S22; full shell,
  ADB Sync and provisioning still require the v24 physical gate.
- Wireless debugging remains enabled during V0. After this path passes, the next architecture experiment can start a short-lived shell-side bridge and ask the user to turn Wireless debugging off earlier.
- Some OEMs may change Developer options or mDNS behavior; real pilots will build manufacturer/model recipes before automation.
- Android 17 local-network permission changes are deferred until the target SDK is raised to API 37.
- The `libadb-android` dependency explicitly says it has not undergone a security audit; the user must
  explicitly pair every ephemeral session and the relay expires automatically.

See [`PROTOCOL.md`](PROTOCOL.md) and [`THIRD_PARTY.md`](THIRD_PARTY.md).
