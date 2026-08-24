#!/usr/bin/env python3
"""In-memory reference implementation of the Glosh Remote broker contract."""

from __future__ import annotations

import argparse
import base64
import hashlib
import hmac
import json
import re
import threading
import time
from dataclasses import dataclass
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Callable, Dict, List, Optional, Tuple


TOKEN_PATTERN = re.compile(r"[A-Za-z0-9_-]{20,100}")
CONTEXT_PATTERN = re.compile(r"[0-9a-f]{64}")
MAX_BODY_BYTES = 32 * 1024
DEFAULT_REQUEST_TTL_SECONDS = 120
DEFAULT_WINDOW_TTL_SECONDS = 120 * 60
MAX_ACTIVE_REQUESTS_PER_SOURCE = 5
MAX_ACTIVE_REQUESTS = 100


class BrokerError(Exception):
    def __init__(self, status: int, code: str) -> None:
        super().__init__(code)
        self.status = status
        self.code = code


@dataclass
class SupportRequest:
    request_id: str
    public_key: str
    nonce_hash: bytes
    seal_context_sha256: str
    manufacturer: str
    model: str
    android_version: str
    source: str
    expires_at: float
    ciphertext: Optional[str] = None

    def operator_view(self, now: float) -> dict:
        return {
            "request_id": self.request_id,
            "client_public_key": self.public_key,
            "device_manufacturer": self.manufacturer,
            "device_model": self.model,
            "android_version": self.android_version,
            "seal_context_sha256": self.seal_context_sha256,
            "state": "accepted" if self.ciphertext is not None else "pending",
            "expires_in_seconds": max(0, int(self.expires_at - now)),
        }


class BrokerStore:
    def __init__(
        self,
        clock: Callable[[], float] = time.monotonic,
        request_ttl: int = DEFAULT_REQUEST_TTL_SECONDS,
    ) -> None:
        self._clock = clock
        self._request_ttl = request_ttl
        self._lock = threading.RLock()
        self._window_expires_at = 0.0
        self._requests: Dict[str, SupportRequest] = {}
        self._tombstones: Dict[str, Tuple[str, float, bytes]] = {}

    def operator_open(self) -> None:
        with self._lock:
            self._prune()
            self._window_expires_at = self._clock() + DEFAULT_WINDOW_TTL_SECONDS

    def operator_close(self) -> None:
        with self._lock:
            self._window_expires_at = 0.0
            for request_id in list(self._requests):
                self._finish(request_id, "revoked")

    def discover(self) -> bool:
        with self._lock:
            self._prune()
            return self._window_expires_at > self._clock()

    def create_request(self, source: str, value: dict) -> int:
        request_id = self._clean_token(value.get("request_id"), "request_id")
        nonce = self._clean_token(value.get("nonce"), "nonce")
        public_key = self._clean_text(value.get("public_key"), 8_192, "public_key")
        manufacturer = self._clean_text(
            value.get("manufacturer"), 40, "manufacturer", allow_empty=True
        )
        model = self._clean_text(value.get("model"), 80, "model", allow_empty=True)
        android_version = self._clean_text(
            value.get("android_version"), 30, "android_version", allow_empty=True
        )
        try:
            decoded_key = base64.urlsafe_b64decode(
                public_key + "=" * (-len(public_key) % 4)
            )
        except Exception as error:
            raise BrokerError(400, "invalid_public_key") from error
        if len(decoded_key) < 384 or len(decoded_key) > 1_024:
            raise BrokerError(400, "invalid_public_key")

        with self._lock:
            self._prune()
            if not self.discover():
                raise BrokerError(503, "support_unavailable")
            if request_id in self._requests or request_id in self._tombstones:
                raise BrokerError(409, "duplicate_request")
            if len(self._requests) >= MAX_ACTIVE_REQUESTS:
                raise BrokerError(429, "request_limit")
            source_count = sum(
                1 for request in self._requests.values() if request.source == source
            )
            if source_count >= MAX_ACTIVE_REQUESTS_PER_SOURCE:
                raise BrokerError(429, "request_limit")
            context = hashlib.sha256(f"{request_id}:{nonce}".encode()).hexdigest()
            self._requests[request_id] = SupportRequest(
                request_id=request_id,
                public_key=public_key,
                nonce_hash=hashlib.sha256(nonce.encode("ascii")).digest(),
                seal_context_sha256=context,
                manufacturer=manufacturer,
                model=model,
                android_version=android_version,
                source=source,
                expires_at=self._clock() + self._request_ttl,
            )
            return self._request_ttl

    def list_pending(self) -> List[dict]:
        with self._lock:
            self._prune()
            if not self.discover():
                raise BrokerError(409, "operator_closed")
            return [
                request.operator_view(self._clock())
                for request in self._requests.values()
                if request.ciphertext is None
            ]

    def accept(
        self,
        request_id: str,
        ciphertext: str,
        ciphertext_alg: str,
    ) -> None:
        self._require_token(request_id, "request_id")
        if ciphertext_alg != "RSA-OAEP-SHA256":
            raise BrokerError(400, "invalid_ciphertext_alg")
        encoded = self._clean_text(ciphertext, 8_192, "ciphertext")
        try:
            raw = base64.urlsafe_b64decode(encoded + "=" * (-len(encoded) % 4))
        except Exception as error:
            raise BrokerError(400, "invalid_ciphertext") from error
        if len(raw) < 384 or len(raw) > 1_024:
            raise BrokerError(400, "invalid_ciphertext")
        with self._lock:
            self._prune()
            request = self._requests.get(request_id)
            if request is None:
                raise BrokerError(404, "unknown_request")
            if request.ciphertext is not None:
                raise BrokerError(409, "already_accepted")
            request.ciphertext = encoded

    def poll(self, request_id: str, nonce: str) -> str:
        with self._lock:
            request = self._authorized_request(request_id, nonce)
            if request is None:
                return self._tombstones[request_id][0]
            return "accepted" if request.ciphertext is not None else "pending"

    def claim(self, request_id: str, nonce: str) -> str:
        with self._lock:
            request = self._authorized_request(request_id, nonce)
            if request is None:
                state = self._tombstones[request_id][0]
                raise BrokerError(409, "already_claimed" if state == "claimed" else state)
            if request.ciphertext is None:
                raise BrokerError(409, "not_ready")
            ciphertext = request.ciphertext
            self._finish(request_id, "claimed")
            return ciphertext

    def revoke(self, request_id: str, nonce: str) -> None:
        with self._lock:
            request = self._authorized_request(request_id, nonce)
            if request is not None:
                self._finish(request_id, "revoked")

    def operator_revoke(self, request_id: str) -> None:
        self._require_token(request_id, "request_id")
        with self._lock:
            self._prune()
            if request_id in self._requests:
                self._finish(request_id, "revoked")

    def broker_snapshot(self, request_id: str) -> dict:
        """Test-only observability with no nonce, descriptor or session key."""
        with self._lock:
            request = self._requests[request_id]
            return request.operator_view(self._clock()) | {
                "ciphertext": request.ciphertext
            }

    def _authorized_request(
        self, request_id: str, nonce: str
    ) -> Optional[SupportRequest]:
        self._require_token(request_id, "request_id")
        self._require_token(nonce, "nonce")
        self._prune()
        supplied = hashlib.sha256(nonce.encode("ascii")).digest()
        request = self._requests.get(request_id)
        if request is not None:
            if not hmac.compare_digest(request.nonce_hash, supplied):
                raise BrokerError(404, "unknown_request")
            return request
        tombstone = self._tombstones.get(request_id)
        if tombstone is None or not hmac.compare_digest(tombstone[2], supplied):
            raise BrokerError(404, "unknown_request")
        return None

    def _finish(self, request_id: str, outcome: str) -> None:
        request = self._requests.pop(request_id, None)
        if request is not None:
            self._tombstones[request_id] = (
                outcome,
                self._clock() + self._request_ttl,
                request.nonce_hash,
            )

    def _prune(self) -> None:
        now = self._clock()
        if self._window_expires_at and self._window_expires_at <= now:
            self.operator_close()
        for request_id, request in list(self._requests.items()):
            if request.expires_at <= now:
                self._finish(request_id, "expired")
        for request_id, (_, expires_at, _) in list(self._tombstones.items()):
            if expires_at <= now:
                self._tombstones.pop(request_id, None)

    @staticmethod
    def _clean_token(value: object, name: str) -> str:
        text = str(value or "")
        if not TOKEN_PATTERN.fullmatch(text):
            raise BrokerError(400, f"invalid_{name}")
        return text

    @staticmethod
    def _require_token(value: str, name: str) -> None:
        if not TOKEN_PATTERN.fullmatch(value or ""):
            raise BrokerError(400, f"invalid_{name}")

    @staticmethod
    def _clean_text(
        value: object, limit: int, name: str, allow_empty: bool = False
    ) -> str:
        text = (
            str(value or "")
            .replace("\r", " ")
            .replace("\n", " ")
            .replace("\t", " ")
            .strip()
        )
        if (not allow_empty and not text) or len(text) > limit:
            raise BrokerError(400, f"invalid_{name}")
        return text


class BrokerHandler(BaseHTTPRequestHandler):
    server: "BrokerHttpServer"

    def do_POST(self) -> None:
        try:
            body = self._read_json()
            action = body.get("action", "")
            if action.startswith("operator_"):
                self._authorize()
            if action == "discover":
                self._json(200, {"available": self.server.store.discover()})
            elif action == "request":
                ttl = self.server.store.create_request(self.client_address[0], body)
                self._json(201, {"state": "pending", "expires_in_seconds": ttl})
            elif action == "poll":
                state = self.server.store.poll(body.get("request_id", ""), body.get("nonce", ""))
                self._json(200, {"state": state})
            elif action == "claim":
                ciphertext = self.server.store.claim(
                    body.get("request_id", ""), body.get("nonce", "")
                )
                self._json(200, {"state": "claimed", "ciphertext": ciphertext})
            elif action == "revoke":
                self.server.store.revoke(
                    body.get("request_id", ""), body.get("nonce", "")
                )
                self._json(200, {"state": "revoked"})
            elif action == "operator_open":
                self.server.store.operator_open()
                self._json(201, {"ok": True})
            elif action == "operator_list":
                self._json(200, {"requests": self.server.store.list_pending()})
            elif action == "operator_accept":
                self.server.store.accept(
                    body.get("request_id", ""),
                    body.get("ciphertext", ""),
                    body.get("ciphertext_alg", ""),
                )
                self._json(200, {"ok": True})
            elif action == "operator_revoke":
                self.server.store.operator_revoke(body.get("request_id", ""))
                self._json(200, {"ok": True})
            elif action == "operator_close":
                self.server.store.operator_close()
                self._json(200, {"ok": True})
            else:
                raise BrokerError(404, "not_found")
        except BrokerError as error:
            self._json(error.status, {"error": error.code})
        except Exception:
            self._json(400, {"error": "invalid_request"})

    def _read_json(self) -> dict:
        try:
            length = int(self.headers.get("Content-Length", "0"))
        except ValueError as error:
            raise BrokerError(400, "invalid_length") from error
        if length <= 0 or length > MAX_BODY_BYTES:
            raise BrokerError(413, "invalid_length")
        try:
            value = json.loads(self.rfile.read(length).decode("utf-8"))
        except Exception as error:
            raise BrokerError(400, "invalid_json") from error
        if not isinstance(value, dict):
            raise BrokerError(400, "invalid_json")
        return value

    def _authorize(self) -> None:
        supplied = self.headers.get("x-glosh-operator-key", "")
        if not hmac.compare_digest(self.server.operator_key, supplied):
            raise BrokerError(401, "unauthorized")

    def _json(self, status: int, value: dict) -> None:
        raw = json.dumps(value, separators=(",", ":")).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def log_message(self, format: str, *args: object) -> None:
        # Requests may contain rendezvous secrets; never log them.
        return


class BrokerHttpServer(ThreadingHTTPServer):
    def __init__(
        self, address: Tuple[str, int], store: BrokerStore, operator_key: str
    ) -> None:
        super().__init__(address, BrokerHandler)
        self.store = store
        self.operator_key = operator_key


def main() -> int:
    parser = argparse.ArgumentParser(description="Glosh Remote in-memory DEV broker")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8766)
    parser.add_argument("--operator-key", required=True)
    args = parser.parse_args()
    if not TOKEN_PATTERN.fullmatch(args.operator_key):
        parser.error("--operator-key must be a 20-100 character URL-safe token")
    server = BrokerHttpServer(
        (args.host, args.port), BrokerStore(), args.operator_key
    )
    print(f"Glosh Remote DEV broker: http://{args.host}:{args.port}", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
