#!/usr/bin/env python3
"""Mock Android agent for testing relay/tunnel/protocol before a physical phone.

Usage:
    python mock_agent.py 'gloshremote://join?...'

It proves WSS transport, mutual authentication, AES-GCM framing, replay counters,
and command/result correlation. It intentionally does NOT execute ADB.
"""

from __future__ import annotations

import argparse
import asyncio
import base64
import hashlib
import hmac
import json
import urllib.parse
from typing import Tuple

from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from websockets.legacy.client import connect


def b64u(value: bytes) -> str:
    return base64.urlsafe_b64encode(value).decode("ascii").rstrip("=")


def b64u_decode(value: str) -> bytes:
    return base64.urlsafe_b64decode(value + "=" * (-len(value) % 4))


def hmac_b64(key: bytes, value: str) -> str:
    return b64u(hmac.new(key, value.encode("utf-8"), hashlib.sha256).digest())


def parse_join_uri(raw: str) -> Tuple[str, str, bytes]:
    parsed = urllib.parse.urlparse(raw.strip())
    if parsed.scheme != "gloshremote" or parsed.netloc != "join":
        raise ValueError("invalid Glosh Remote descriptor")
    query = urllib.parse.parse_qs(parsed.query)
    if query.get("v", [None])[0] != "1":
        raise ValueError("unsupported protocol version")
    wss_url = query.get("url", [None])[0]
    sid = query.get("sid", [None])[0]
    key_text = query.get("k", [None])[0]
    if not wss_url or not wss_url.startswith("wss://"):
        raise ValueError("descriptor requires WSS")
    if not sid or not key_text:
        raise ValueError("descriptor missing sid/key")
    key = b64u_decode(key_text)
    if len(key) != 32:
        raise ValueError("session key must be 256-bit")
    return wss_url.rstrip("/"), sid, key


def decrypt_box(key: bytes, sid: str, direction: str, envelope: dict) -> dict:
    seq = int(envelope["seq"])
    aad = f"{sid}:{direction}:{seq}".encode("utf-8")
    plaintext = AESGCM(key).decrypt(
        b64u_decode(envelope["nonce"]),
        b64u_decode(envelope["ciphertext"]),
        aad,
    )
    return json.loads(plaintext.decode("utf-8"))


def encrypt_box(key: bytes, sid: str, direction: str, seq: int, payload: dict) -> dict:
    nonce = bytes((seq + offset) % 256 for offset in range(12))
    aad = f"{sid}:{direction}:{seq}".encode("utf-8")
    plaintext = json.dumps(payload, separators=(",", ":"), ensure_ascii=False).encode("utf-8")
    ciphertext = AESGCM(key).encrypt(nonce, plaintext, aad)
    return {
        "v": 1,
        "type": "box",
        "seq": seq,
        "nonce": b64u(nonce),
        "ciphertext": b64u(ciphertext),
    }


async def run(descriptor: str) -> None:
    base, sid, key = parse_join_uri(descriptor)
    url = f"{base}/agent?{urllib.parse.urlencode({'sid': sid})}"
    inbound_seq = 0
    outbound_seq = 0

    print(f"[mock] connecting to {base}")
    async with connect(url, max_size=256 * 1024) as websocket:
        challenge = json.loads(await websocket.recv())
        if challenge.get("type") != "challenge":
            raise RuntimeError("expected challenge")
        nonce = str(challenge["nonce"])

        proof = hmac_b64(key, f"agent-auth:{sid}:{nonce}")
        await websocket.send(
            json.dumps(
                {
                    "v": 1,
                    "type": "auth",
                    "proof": proof,
                    "device": {
                        "manufacturer": "Glosh",
                        "model": "MockAgent",
                        "device": "mock",
                        "android": "0",
                        "sdk": 0,
                    },
                },
                separators=(",", ":"),
            )
        )

        ready = json.loads(await websocket.recv())
        expected = hmac_b64(key, f"server-ready:{sid}:{nonce}")
        if ready.get("type") != "ready" or not hmac.compare_digest(str(ready.get("serverProof", "")), expected):
            raise RuntimeError("server authentication failed")
        print("[mock] mutually authenticated")

        async for raw in websocket:
            envelope = json.loads(raw)
            if envelope.get("type") != "box":
                raise RuntimeError("unexpected frame")
            seq = int(envelope["seq"])
            if seq <= inbound_seq:
                raise RuntimeError("replayed/out-of-order server frame")
            inbound_seq = seq
            payload = decrypt_box(key, sid, "server", envelope)
            if payload.get("kind") != "command":
                raise RuntimeError("unexpected encrypted payload")

            action = str(payload.get("action", ""))
            request_id = str(payload.get("requestId", ""))
            print(f"[mock] command: {action}")
            output = "pong" if action == "ping" else f"mock:{action}"

            outbound_seq += 1
            response = encrypt_box(
                key,
                sid,
                "agent",
                outbound_seq,
                {
                    "kind": "result",
                    "requestId": request_id,
                    "action": action,
                    "ok": True,
                    "output": output,
                },
            )
            await websocket.send(json.dumps(response, separators=(",", ":")))


def main() -> int:
    parser = argparse.ArgumentParser(description="Glosh Remote mock Android agent")
    parser.add_argument("join_uri")
    args = parser.parse_args()
    try:
        asyncio.run(run(args.join_uri))
        return 0
    except KeyboardInterrupt:
        return 130
    except Exception as exc:
        print(f"ERROR: {exc}")
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
