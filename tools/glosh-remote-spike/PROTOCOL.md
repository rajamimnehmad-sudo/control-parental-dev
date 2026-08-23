# Glosh Remote Spike Protocol v1

Purpose: prove temporary remote technical access to an Android 11+ device without a PC on the client side. This protocol is **not** the final Glosh remote-support platform.

## Transport

- Android pairs locally with its own Wireless Debugging ADB endpoint.
- Android opens an outbound WebSocket to a temporary HTTPS/WSS relay.
- Lab relay: a WebSocket server bound to `127.0.0.1` on the technician Mac and exposed by Cloudflare Quick Tunnel.
- No inbound port is opened on the Mac or Android device.
- ADB itself is never exposed to the Internet.

## Join descriptor

The Mac generates a one-time URI:

```text
gloshremote://join?v=1&url=<WSS_URL>&sid=<SESSION_ID>&k=<BASE64URL_32_BYTE_KEY>
```

The 256-bit session key is delivered out-of-band in that URI and is never sent to the relay as plaintext protocol data.

## Authentication

1. Agent connects to `/agent?sid=<SESSION_ID>`.
2. Server sends a random challenge nonce.
3. Agent proves the session key with `HMAC-SHA256(key, "agent-auth:<sid>:<nonce>")`.
4. Server proves the same key with `HMAC-SHA256(key, "server-ready:<sid>:<nonce>")`.
5. Only one authenticated agent is accepted per session.

## Encrypted frames

Commands and results use AES-256-GCM.

Envelope:

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

Any other action is rejected on Android even if the relay is compromised.

## Revocation

- Closing the remote session closes WebSocket and local ADB connection.
- "Revoke temporary ADB identity" also deletes the app-private ADB private key and certificate.
- The lab session expires automatically (30 minutes by default).
- For the first spike, the user should turn off Wireless Debugging after the test. A later iteration may start a short-lived shell-side bridge and then disable Wireless Debugging earlier.

## Explicit non-goals for v1

- no arbitrary remote shell;
- no Glosh APK installation yet;
- no Device Owner mutation yet;
- no screen control;
- no persistence across reboot;
- no production relay/backend;
- no root;
- no exposed `adb tcpip 5555`.
