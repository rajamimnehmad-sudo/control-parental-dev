# Glosh Remote Spike Protocol v1

Purpose: prove temporary remote technical access to an Android 11+ device without a PC on the client side. This protocol is **not** the final Glosh remote-support platform.

## Bootstrap transport

- Android pairs locally with its own Wireless Debugging ADB endpoint.
- Pairing endpoint and TLS connection endpoint are discovered automatically with local mDNS.
- The client types only Android's six-digit pairing code; host and port are never user input.
- The ADB RSA identity exists only in process memory. It is not persisted.
- After local ADB is ready, Android opens an outbound WebSocket to a temporary HTTPS/WSS relay.
- Lab relay: WebSocket server bound to `127.0.0.1` on the technician Mac and exposed by Cloudflare Quick Tunnel.
- No inbound port is opened on the Mac or Android device.
- ADB itself is never exposed to the Internet.

## Join descriptor

The Mac generates a one-time URI:

```text
gloshremote://join?v=1&url=<WSS_URL>&sid=<SESSION_ID>&k=<BASE64URL_32_BYTE_KEY>
```

The 256-bit session key is delivered out-of-band in that URI and is never sent as plaintext command-protocol data.

## Authentication

1. Agent connects to `/agent?sid=<SESSION_ID>`.
2. Server sends a random challenge nonce.
3. Agent proves the session key with `HMAC-SHA256(key, "agent-auth:<sid>:<nonce>")`.
4. Server proves the same key with `HMAC-SHA256(key, "server-ready:<sid>:<nonce>")`.
5. Only one authenticated agent is accepted per session.

## Encrypted frames

Commands and results use AES-256-GCM.

```json
{
  "v": 1,
  "type": "box",
  "seq": 1,
  "nonce": "base64url-12-byte-nonce",
  "ciphertext": "base64url-ciphertext-and-tag"
}
```

AAD is directional and monotonic:

- Mac → Android: `<sid>:server:<seq>`
- Android → Mac: `<sid>:agent:<seq>`

Frames with a repeated or decreasing sequence are rejected.

## Command model

The Mac sends **action names only**. It cannot supply raw shell strings.

Current allowlist:

- `ping` — app-local round trip
- `whoami` — `id`
- `device` — selected read-only `getprop` values
- `owners` — `dpm list-owners`
- `users` — `pm list users`
- `battery` — `dumpsys battery`

Any other action is rejected on Android even if the transport relay is compromised.

## Revocation

- Closing the remote session closes WebSocket and local ADB connection.
- Closing the service drops the in-memory ADB private key/certificate; another session must pair again.
- Process death or reboot also destroys that identity.
- The lab relay session expires automatically (30 minutes by default).
- For V0, Wireless debugging remains enabled until the user turns it off after the test. A later iteration may start a short-lived shell-side bridge and disable Wireless debugging earlier.

## Explicit non-goals for v1

- no arbitrary remote shell;
- no Glosh APK installation yet;
- no Device Owner mutation yet;
- no screen control;
- no persistence across reboot;
- no production relay/backend;
- no root;
- no exposed `adb tcpip 5555`.
