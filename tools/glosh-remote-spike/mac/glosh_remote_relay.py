#!/usr/bin/env python3
"""Temporary Mac-side relay for REMOTE-INSTALL-CONNECTION-00.

This is intentionally a lab tool. It binds only to localhost and asks cloudflared
for an outbound Quick Tunnel. Commands are allowlisted action names and their
payloads/results are encrypted end-to-end with a one-time 256-bit session key.
"""
from __future__ import annotations

import argparse
import asyncio
import base64
import hashlib
import hmac
import json
import os
import re
import shutil
import sys
import time
import urllib.parse
import uuid
from dataclasses import dataclass
from typing import Dict, Optional, Tuple, Union
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from websockets.legacy.server import WebSocketServerProtocol, serve
from broker_client import BrokerOperatorClient
from broker_console import (
    accept_request,
    announce_pending_requests,
    maintain_operator_presence,
    print_pending_requests,
)
PROTOCOL_VERSION = 1
MAX_MESSAGE_BYTES = 256 * 1024
DEFAULT_PORT = 8765
DEFAULT_SESSION_MINUTES = 30
TUNNEL_PATTERN = re.compile(r"https://[a-z0-9-]+\.trycloudflare\.com")

ACTIONS = {
    "ping": "Round-trip de la sesión, sin ADB",
    "whoami": "Identidad shell (id)",
    "device": "Fabricante, modelo y Android",
    "owners": "Device/Profile Owner",
    "users": "Usuarios Android",
    "battery": "Estado de batería",
}


def b64u(value: bytes) -> str:
    return base64.urlsafe_b64encode(value).decode("ascii").rstrip("=")


def b64u_decode(value: str) -> bytes:
    return base64.urlsafe_b64decode(value + "=" * (-len(value) % 4))


def hmac_b64(key: bytes, value: str) -> str:
    return b64u(hmac.new(key, value.encode("utf-8"), hashlib.sha256).digest())


def encrypt_box(key: bytes, sid: str, direction: str, seq: int, payload: dict) -> dict:
    nonce = os.urandom(12)
    aad = f"{sid}:{direction}:{seq}".encode("utf-8")
    plaintext = json.dumps(payload, separators=(",", ":"), ensure_ascii=False).encode("utf-8")
    ciphertext = AESGCM(key).encrypt(nonce, plaintext, aad)
    return {
        "v": PROTOCOL_VERSION,
        "type": "box",
        "seq": seq,
        "nonce": b64u(nonce),
        "ciphertext": b64u(ciphertext),
    }


def decrypt_box(key: bytes, sid: str, direction: str, envelope: dict) -> dict:
    seq = int(envelope["seq"])
    aad = f"{sid}:{direction}:{seq}".encode("utf-8")
    plaintext = AESGCM(key).decrypt(
        b64u_decode(envelope["nonce"]),
        b64u_decode(envelope["ciphertext"]),
        aad,
    )
    return json.loads(plaintext.decode("utf-8"))


@dataclass
class AgentInfo:
    manufacturer: str = "?"
    model: str = "?"
    device: str = "?"
    android: str = "?"
    sdk: Union[int, str] = "?"

    @classmethod
    def from_dict(cls, value: dict) -> "AgentInfo":
        return cls(
            manufacturer=str(value.get("manufacturer", "?")),
            model=str(value.get("model", "?")),
            device=str(value.get("device", "?")),
            android=str(value.get("android", "?")),
            sdk=value.get("sdk", "?"),
        )


class RemoteSession:
    def __init__(self, minutes: int) -> None:
        self.sid = b64u(os.urandom(18))
        self.key = os.urandom(32)
        self.duration_seconds = minutes * 60
        self.expires_at: Optional[float] = None
        self.agent: Optional[WebSocketServerProtocol] = None
        self.agent_info: Optional[AgentInfo] = None
        self.agent_ready = asyncio.Event()
        self.stop_event = asyncio.Event()
        self.claim_lock = asyncio.Lock()
        self.send_lock = asyncio.Lock()
        self.pending: Dict[str, asyncio.Future] = {}
        self.support_slot_claimed = False
        self.accepted_request_id: Optional[str] = None
        self.server_seq = 0
        self.agent_seq = 0

    def join_uri(self, public_https_url: str) -> str:
        wss_url = public_https_url.replace("https://", "wss://", 1)
        query = urllib.parse.urlencode(
            {
                "v": str(PROTOCOL_VERSION),
                "url": wss_url,
                "sid": self.sid,
                "k": b64u(self.key),
            }
        )
        return f"gloshremote://join?{query}"

    async def handler(self, websocket: WebSocketServerProtocol, path: str) -> None:
        parsed = urllib.parse.urlparse(path)
        query = urllib.parse.parse_qs(parsed.query)
        if parsed.path != "/agent" or query.get("sid", [None])[0] != self.sid:
            await websocket.close(4001, "unknown session")
            return
        if self.expires_at is not None and time.monotonic() >= self.expires_at:
            await websocket.close(4002, "session expired")
            return

        challenge = b64u(os.urandom(32))
        await websocket.send(json.dumps({"v": 1, "type": "challenge", "nonce": challenge}))
        try:
            raw_auth = await asyncio.wait_for(websocket.recv(), timeout=15)
            auth = json.loads(raw_auth)
        except Exception:
            await websocket.close(4003, "authentication timeout")
            return

        expected = hmac_b64(self.key, f"agent-auth:{self.sid}:{challenge}")
        if auth.get("type") != "auth" or not hmac.compare_digest(str(auth.get("proof", "")), expected):
            await websocket.close(4004, "authentication failed")
            return

        async with self.claim_lock:
            if self.agent is not None:
                await websocket.close(4005, "agent already connected")
                return
            if self.expires_at is None:
                self.expires_at = time.monotonic() + self.duration_seconds
            elif time.monotonic() >= self.expires_at:
                await websocket.close(4002, "session expired")
                return
            self.agent = websocket
            self.agent_info = AgentInfo.from_dict(auth.get("device") or {})
            self.agent_seq = 0
            self.server_seq = 0
            self.agent_ready.set()

        server_proof = hmac_b64(self.key, f"server-ready:{self.sid}:{challenge}")
        await websocket.send(json.dumps({"v": 1, "type": "ready", "serverProof": server_proof}))
        print(
            f"\n[agent] conectado: {self.agent_info.manufacturer} {self.agent_info.model} "
            f"· Android {self.agent_info.android} · SDK {self.agent_info.sdk}\n",
            flush=True,
        )

        try:
            async for raw in websocket:
                await self._handle_agent_message(raw)
        except Exception as exc:
            if not self.stop_event.is_set():
                print(f"[agent] conexión cerrada: {exc}", file=sys.stderr)
        finally:
            async with self.claim_lock:
                if self.agent is websocket:
                    self.agent = None
                    self.agent_info = None
                    self.agent_ready.clear()
                    for future in self.pending.values():
                        if not future.done():
                            future.set_exception(ConnectionError("agent disconnected"))
                    self.pending.clear()

    async def _handle_agent_message(self, raw: str) -> None:
        envelope = json.loads(raw)
        if envelope.get("type") != "box":
            raise ValueError("unexpected agent message")
        seq = int(envelope["seq"])
        if seq <= self.agent_seq:
            raise ValueError("replayed/out-of-order agent frame")
        self.agent_seq = seq
        payload = decrypt_box(self.key, self.sid, "agent", envelope)
        if payload.get("kind") != "result":
            raise ValueError("unexpected encrypted payload")
        request_id = str(payload.get("requestId", ""))
        future = self.pending.pop(request_id, None)
        if future is not None and not future.done():
            future.set_result(payload)

    async def command(self, action: str, timeout: float = 20.0) -> dict:
        if action not in ACTIONS:
            raise ValueError(f"action not allowlisted: {action}")
        websocket = self.agent
        if websocket is None:
            raise ConnectionError("no agent connected")
        if self.expires_at is not None and time.monotonic() >= self.expires_at:
            raise TimeoutError("session expired")

        request_id = str(uuid.uuid4())
        loop = asyncio.get_running_loop()
        future = loop.create_future()
        self.pending[request_id] = future
        payload = {
            "kind": "command",
            "requestId": request_id,
            "action": action,
        }

        async with self.send_lock:
            self.server_seq += 1
            envelope = encrypt_box(self.key, self.sid, "server", self.server_seq, payload)
            await websocket.send(json.dumps(envelope, separators=(",", ":")))

        try:
            return await asyncio.wait_for(future, timeout=timeout)
        finally:
            self.pending.pop(request_id, None)

    async def close(self) -> None:
        self.stop_event.set()
        websocket = self.agent
        if websocket is not None:
            await websocket.close(1000, "session revoked")
        self.agent_ready.clear()
        self.key = b"\x00" * len(self.key)


async def start_cloudflared(port: int) -> Tuple[asyncio.subprocess.Process, str]:
    executable = shutil.which("cloudflared")
    if not executable:
        raise RuntimeError(
            "cloudflared no está instalado. Codex deberá instalarlo en la Mac antes del gate físico."
        )

    process = await asyncio.create_subprocess_exec(
        executable,
        "tunnel",
        "--url",
        f"http://127.0.0.1:{port}",
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.STDOUT,
    )
    assert process.stdout is not None

    async def read_url() -> str:
        while True:
            line_bytes = await process.stdout.readline()
            if not line_bytes:
                raise RuntimeError("cloudflared terminó antes de entregar una URL")
            line = line_bytes.decode("utf-8", errors="replace")
            match = TUNNEL_PATTERN.search(line)
            if match:
                return match.group(0)

    try:
        public_url = await asyncio.wait_for(read_url(), timeout=35)
    except Exception:
        process.terminate()
        await process.wait()
        raise

    async def drain_logs() -> None:
        assert process.stdout is not None
        while True:
            line = await process.stdout.readline()
            if not line:
                return

    asyncio.create_task(drain_logs())
    return process, public_url


async def async_input(prompt: str) -> str:
    """Cancelable stdin reader for the Mac event loop (no stuck executor thread on expiry)."""
    loop = asyncio.get_running_loop()
    future = loop.create_future()
    fd = sys.stdin.fileno()
    print(prompt, end="", flush=True)

    def on_readable() -> None:
        try:
            line = sys.stdin.readline()
            if not future.done():
                future.set_result(line)
        except Exception as exc:
            if not future.done():
                future.set_exception(exc)
        finally:
            try:
                loop.remove_reader(fd)
            except Exception:
                pass

    loop.add_reader(fd, on_readable)
    try:
        return await future
    finally:
        try:
            loop.remove_reader(fd)
        except Exception:
            pass


async def interactive_cli(
    session: RemoteSession,
    descriptor: str,
    broker: Optional[BrokerOperatorClient],
) -> None:
    print("\nEsperando al Android…")
    print("Comandos: " + ", ".join(ACTIONS) + ", status, requests, accept <request-id>, help, quit")
    while not session.stop_event.is_set():
        try:
            raw = (await async_input("glosh-remote> ")).strip()
        except (EOFError, KeyboardInterrupt):
            raw = "quit"

        command = raw.lower()
        if not command:
            continue
        if command == "quit":
            session.stop_event.set()
            return
        if command == "help":
            for name, description in ACTIONS.items():
                print(f"  {name:8} {description}")
            print("  status                 Muestra el agente actual")
            print("  requests               Muestra solicitudes pendientes del broker")
            print("  accept <request-id>     Acepta explícitamente un teléfono")
            print("  quit                    Revoca la sesión")
            continue
        if command == "status":
            print(session.agent_info or "sin agente")
            continue
        if command == "requests":
            if broker is None:
                print("Broker no configurado; esta sesión usa el fallback DEV por descriptor.")
            else:
                await print_pending_requests(broker)
            continue
        if command.startswith("accept "):
            if broker is None:
                print("Broker no configurado.")
                continue
            request_id = raw.split(maxsplit=1)[1].strip()
            try:
                print(await accept_request(broker, request_id, descriptor, session))
            except Exception as exc:
                print(f"[broker] no se pudo aceptar la solicitud: {exc}")
            continue
        if command not in ACTIONS:
            print("Comando no permitido. Usá help.")
            continue

        try:
            result = await session.command(command)
            marker = "PASS" if result.get("ok") else "ERROR"
            print(f"[{marker}] {command}\n{result.get('output', '')}".rstrip())
        except Exception as exc:
            print(f"[ERROR] {exc}")


async def expire_session(session: RemoteSession) -> None:
    await session.agent_ready.wait()
    while not session.stop_event.is_set():
        expires_at = session.expires_at
        if expires_at is None:
            await asyncio.sleep(0.1)
            continue
        delay = max(0.0, expires_at - time.monotonic())
        if delay:
            try:
                await asyncio.wait_for(session.stop_event.wait(), timeout=delay)
                return
            except asyncio.TimeoutError:
                pass
        if not session.stop_event.is_set():
            print("\n[session] expiró automáticamente.")
            session.stop_event.set()
        return


async def async_main(args: argparse.Namespace) -> int:
    session = RemoteSession(args.session_minutes)
    tunnel_process: Optional[asyncio.subprocess.Process] = None
    broker: Optional[BrokerOperatorClient] = None

    async with serve(
        session.handler,
        "127.0.0.1",
        args.port,
        max_size=MAX_MESSAGE_BYTES,
        ping_interval=20,
        ping_timeout=20,
    ):
        try:
            if args.public_url:
                public_url = args.public_url.rstrip("/")
            else:
                tunnel_process, public_url = await start_cloudflared(args.port)

            descriptor = session.join_uri(public_url)
            if args.broker_url:
                broker = BrokerOperatorClient(args.broker_url, args.operator_key)
                await asyncio.get_running_loop().run_in_executor(None, broker.register, 0)

            print("\n=== GLOSH REMOTE SPIKE ===")
            print(f"Relay local: ws://127.0.0.1:{args.port}")
            print(f"Relay público temporal: {public_url}")
            print(
                f"Standby sin consumir sesión · al conectar: "
                f"{args.session_minutes} min de sesión segura"
            )
            if broker is None:
                print("\nFallback DEV directo (contiene una clave temporal; no lo publiques):\n")
                print(descriptor)
                print()
            else:
                print(f"Broker de soporte activo: {args.broker_url}")
                print("El descriptor no se muestra ni se entrega al broker en claro.")
                print("Esperando próximo cliente; una solicitud única se acepta automáticamente.")

            expiry_task = asyncio.create_task(expire_session(session))
            cli_task = asyncio.create_task(interactive_cli(session, descriptor, broker))
            broker_task = (
                asyncio.create_task(announce_pending_requests(session, broker, descriptor))
                if broker is not None
                else None
            )
            presence_task = (
                asyncio.create_task(maintain_operator_presence(session, broker))
                if broker is not None
                else None
            )
            await session.stop_event.wait()
            cli_task.cancel()
            expiry_task.cancel()
            if broker_task is not None:
                broker_task.cancel()
            if presence_task is not None:
                presence_task.cancel()
            await asyncio.gather(
                *[
                    task
                    for task in (cli_task, expiry_task, broker_task, presence_task)
                    if task is not None
                ],
                return_exceptions=True,
            )
            await session.close()
            return 0
        finally:
            if broker is not None:
                await asyncio.get_running_loop().run_in_executor(None, broker.close)
            if tunnel_process is not None and tunnel_process.returncode is None:
                tunnel_process.terminate()
                try:
                    await asyncio.wait_for(tunnel_process.wait(), timeout=5)
                except asyncio.TimeoutError:
                    tunnel_process.kill()
                    await tunnel_process.wait()


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Glosh Remote lab relay")
    parser.add_argument("--port", type=int, default=DEFAULT_PORT)
    parser.add_argument("--session-minutes", type=int, default=DEFAULT_SESSION_MINUTES)
    parser.add_argument(
        "--public-url",
        help="URL HTTPS de un túnel ya creado; si se omite se lanza cloudflared Quick Tunnel.",
    )
    parser.add_argument(
        "--broker-url",
        default=os.environ.get("BROKER_BASE_URL", ""),
        help="Endpoint estable HTTPS del Support Session Broker.",
    )
    parser.set_defaults(operator_key=os.environ.get("GLOSH_REMOTE_OPERATOR_KEY", ""))
    args = parser.parse_args()
    if not (1 <= args.port <= 65535):
        parser.error("--port fuera de rango")
    if not (1 <= args.session_minutes <= 120):
        parser.error("--session-minutes debe estar entre 1 y 120")
    if args.public_url and not args.public_url.startswith("https://"):
        parser.error("--public-url debe ser HTTPS")
    if bool(args.broker_url) != bool(args.operator_key):
        parser.error("--broker-url requiere GLOSH_REMOTE_OPERATOR_KEY")
    if args.broker_url:
        parsed_broker = urllib.parse.urlparse(args.broker_url)
        loopback_dev = parsed_broker.scheme == "http" and parsed_broker.hostname in {"127.0.0.1", "localhost"}
        if parsed_broker.scheme != "https" and not loopback_dev:
            parser.error("--broker-url debe ser HTTPS (HTTP sólo se admite en loopback DEV)")
    return args


def main() -> int:
    try:
        return asyncio.run(async_main(parse_args()))
    except KeyboardInterrupt:
        return 130
    except Exception as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
