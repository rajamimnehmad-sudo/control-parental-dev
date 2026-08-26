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

## Support rendezvous

The normal client flow discovers a waiting operator through a stable, explicitly configured
`BROKER_BASE_URL`. Android sends only a random request ID, random nonce, ephemeral RSA-3072 public key,
manufacturer/model and Android version. It sends no account, IMEI, Android ID or persistent identity.

The operator opens one waiting support window and must explicitly run `accept <request-id>`. The Mac
then seals the internal descriptor with RSA-OAEP SHA-256 for that request's public key. The plaintext
descriptor remains on the Mac and Android only. The broker receives and transfers ciphertext.

Requests have a short TTL, are rate-limited and can be revoked. A ciphertext can be accepted once and
claimed once; consumed, expired and revoked request IDs become temporary tombstones to reject replay.
The sealed plaintext binds protocol version, request ID, the SHA-256 context
`SHA-256(request_id + ":" + nonce)` and descriptor. The broker gives the operator only that context;
Android recomputes it from its in-memory nonce before accepting the descriptor.

The included broker is an in-memory reference implementation of the deployed action-POST contract.
The physical no-link and cross-network gates remain separate from this HTTP integration gate.

## Internal join descriptor

The Mac generates a one-time URI:

```text
gloshremote://join?v=1&url=<WSS_URL>&sid=<SESSION_ID>&k=<BASE64URL_32_BYTE_KEY>
```

The 256-bit session key is delivered inside the RSA-OAEP sealed rendezvous payload and is never visible
to the normal UI or broker. Debug builds retain direct intent parsing solely for laboratory recovery.

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

## Remote ADB model

After the user explicitly pairs Wireless Debugging and the relay proves the one-time session key, the
Mac receives the practical authority of `uid=2000(shell)`, matching an ADB cable without root.

Convenience actions remain:

- `ping` — app-local round trip
- `whoami` — `id`
- `device` — selected read-only `getprop` values
- `owners` — `dpm list-owners`
- `users` — `pm list users`
- `battery` — `dumpsys battery`

The authenticated protocol also carries:

- `shell` with a command string up to 32 KiB; output is capped to 2 MiB;
- `push-start`, ordered `push-chunk` and `push-finish` for files up to 512 MiB;
- a declared size and SHA-256 which Android verifies before opening ADB Sync;
- Mac-side `install`, `owner` and `provision` transactions built from `push` plus `shell`.

File chunks are at most 96 KiB on the wire and 128 KiB at the Android boundary. A disconnect aborts
and deletes the app-private staging file. The ADB destination is still constrained by the actual
permissions Android grants to `shell`; this protocol never provides root.

## Revocation

- Closing the remote session closes WebSocket and local ADB connection.
- Closing the service drops the in-memory ADB private key/certificate; another session must pair again.
- Process death or reboot also destroys that identity.
- The lab relay session expires automatically (30 minutes by default).
- For V0, Wireless debugging remains enabled until the user turns it off after the test. A later iteration may start a short-lived shell-side bridge and disable Wireless debugging earlier.

## Explicit non-goals for v1

- no root or SELinux bypass;
- no hidden or persistent access after session close;
- no bypass of Android's Device Owner eligibility rules or protected user confirmations;
- no interactive framebuffer protocol; diagnostics and permitted `shell` tools remain available;
- no persistence across reboot;
- no production relay/backend;
- no root;
- no exposed `adb tcpip 5555`.
