#!/usr/bin/env python3
"""In-memory DEV rendezvous broker for Glosh Remote.

The broker never receives the plaintext join descriptor. It holds short-lived
request metadata, an Android ephemeral public key and, after explicit operator
acceptance, one RSA-OAEP ciphertext that can be consumed exactly once.
"""

from __future__ import annotations

import argparse
import base64
import hashlib
import hmac
import json
import os
import re
import threading
import time
import urllib.parse
from dataclasses import dataclass
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Callable, Dict, List, Optional, Tuple


TOKEN_PATTERN = re.compile(r"[A-Za-z0-9_-]{20,100}")
MAX_BODY_BYTES = 32 * 1024
DEFAULT_REQUEST_TTL_SECONDS = 120
DEFAULT_SESSION_TTL_SECONDS = 120 * 60
MAX_ACTIVE_REQUESTS_PER_SOURCE = 5
MAX_ACTIVE_REQUESTS = 100


class BrokerError(Exception):
    def __init__(self, status: int, code: str) -> None:
        super().__init__(code)
        self.status = status
        self.code = code


@dataclass
class OperatorSession:
    session_id: str
    expires_at: float


@dataclass
class SupportRequest:
    request_id: str
    session_id: str
    public_key: str
    nonce: str
    nonce_hash: bytes
    manufacturer: str
    model: str
    android: str
    source: str
    expires_at: float
    ciphertext: Optional[str] = None

    def operator_view(self, now: float) -> dict:
        return {
            "requestId": self.request_id,
            "publicKey": self.public_key,
            "nonce": self.nonce,
            "manufacturer": self.manufacturer,
            "model": self.model,
            "android": self.android,
            "expiresInSeconds": max(0, int(self.expires_at - now)),
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
        self._sessions: Dict[str, OperatorSession] = {}
        self._requests: Dict[str, SupportRequest] = {}
        self._tombstones: Dict[str, Tuple[str, float, bytes]] = {}

    def register_session(self, session_id: str, ttl_seconds: int) -> None:
        self._require_token(session_id, "session_id")
        ttl = min(max(int(ttl_seconds), 1), DEFAULT_SESSION_TTL_SECONDS)
        with self._lock:
            self._prune()
            self._sessions[session_id] = OperatorSession(session_id, self._clock() + ttl)

    def revoke_session(self, session_id: str) -> None:
        with self._lock:
            self._sessions.pop(session_id, None)
            for request_id, request in list(self._requests.items()):
                if request.session_id == session_id:
                    self._finish(request_id, "revoked")

    def create_request(self, source: str, value: dict) -> int:
        request_id = self._clean_token(value.get("requestId"), "request_id")
        nonce = self._clean_token(value.get("nonce"), "nonce")
        public_key = self._clean_text(value.get("publicKey"), 8_192, "public_key")
        manufacturer = self._clean_text(value.get("manufacturer"), 40, "manufacturer", allow_empty=True)
        model = self._clean_text(value.get("model"), 80, "model", allow_empty=True)
        android = self._clean_text(value.get("android"), 30, "android", allow_empty=True)
        try:
            decoded_key = base64.urlsafe_b64decode(public_key + "=" * (-len(public_key) % 4))
        except Exception as error:
            raise BrokerError(400, "invalid_public_key") from error
        if len(decoded_key) < 384 or len(decoded_key) > 1_024:
            raise BrokerError(400, "invalid_public_key")

        with self._lock:
            self._prune()
            sessions = list(self._sessions.values())
            if len(sessions) != 1:
                raise BrokerError(503, "support_unavailable")
            if request_id in self._requests or request_id in self._tombstones:
                raise BrokerError(409, "duplicate_request")
            if len(self._requests) >= MAX_ACTIVE_REQUESTS:
                raise BrokerError(429, "request_limit")
            source_count = sum(1 for request in self._requests.values() if request.source == source)
            if source_count >= MAX_ACTIVE_REQUESTS_PER_SOURCE:
                raise BrokerError(429, "request_limit")
            session = sessions[0]
            self._requests[request_id] = SupportRequest(
                request_id=request_id,
                session_id=session.session_id,
                public_key=public_key,
                nonce=nonce,
                nonce_hash=hashlib.sha256(nonce.encode("ascii")).digest(),
                manufacturer=manufacturer,
                model=model,
                android=android,
                source=source,
                expires_at=self._clock() + self._request_ttl,
            )
            return self._request_ttl

    def list_pending(self, session_id: str) -> List[dict]:
        with self._lock:
            self._prune()
            if session_id not in self._sessions:
                raise BrokerError(404, "unknown_session")
            return [
                request.operator_view(self._clock())
                for request in self._requests.values()
                if request.session_id == session_id and request.ciphertext is None
            ]

    def accept(self, session_id: str, request_id: str, ciphertext: str) -> None:
        self._require_token(session_id, "session_id")
        self._require_token(request_id, "request_id")
        encoded = self._clean_text(ciphertext, 8_192, "ciphertext")
        try:
            raw = base64.urlsafe_b64decode(encoded + "=" * (-len(encoded) % 4))
        except Exception as error:
            raise BrokerError(400, "invalid_ciphertext") from error
        if len(raw) < 256 or len(raw) > 1_024:
            raise BrokerError(400, "invalid_ciphertext")
        with self._lock:
            self._prune()
            request = self._requests.get(request_id)
            if request is None:
                raise BrokerError(404, "unknown_request")
            if request.session_id != session_id:
                raise BrokerError(403, "wrong_session")
            if request.ciphertext is not None:
                raise BrokerError(409, "already_accepted")
            request.ciphertext = encoded

    def claim(self, request_id: str, nonce: str) -> Tuple[str, Optional[str]]:
        self._require_token(request_id, "request_id")
        self._require_token(nonce, "nonce")
        with self._lock:
            self._prune()
            tombstone = self._tombstones.get(request_id)
            if tombstone is not None:
                supplied = hashlib.sha256(nonce.encode("ascii")).digest()
                if not hmac.compare_digest(tombstone[2], supplied):
                    raise BrokerError(404, "unknown_request")
                return tombstone[0], None
            request = self._requests.get(request_id)
            if request is None:
                raise BrokerError(404, "unknown_request")
            supplied = hashlib.sha256(nonce.encode("ascii")).digest()
            if not hmac.compare_digest(request.nonce_hash, supplied):
                raise BrokerError(404, "unknown_request")
            if request.ciphertext is None:
                return "pending", None
            ciphertext = request.ciphertext
            self._finish(request_id, "consumed")
            return "delivered", ciphertext

    def cancel(self, request_id: str, nonce: str) -> None:
        self._require_token(request_id, "request_id")
        self._require_token(nonce, "nonce")
        with self._lock:
            self._prune()
            request = self._requests.get(request_id)
            if request is None:
                return
            supplied = hashlib.sha256(nonce.encode("ascii")).digest()
            if not hmac.compare_digest(request.nonce_hash, supplied):
                raise BrokerError(404, "unknown_request")
            self._finish(request_id, "revoked")

    def broker_snapshot(self, request_id: str) -> dict:
        """Test-only observability: intentionally has no descriptor/session key field."""
        with self._lock:
            request = self._requests[request_id]
            return {
                "requestId": request.request_id,
                "publicKey": request.public_key,
                "ciphertext": request.ciphertext,
                "manufacturer": request.manufacturer,
                "model": request.model,
                "android": request.android,
            }

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
        for session_id, session in list(self._sessions.items()):
            if session.expires_at <= now:
                self.revoke_session(session_id)
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
    def _clean_text(value: object, limit: int, name: str, allow_empty: bool = False) -> str:
        text = str(value or "").replace("\r", " ").replace("\n", " ").replace("\t", " ").strip()
        if (not allow_empty and not text) or len(text) > limit:
            raise BrokerError(400, f"invalid_{name}")
        return text


class BrokerHandler(BaseHTTPRequestHandler):
    server: "BrokerHttpServer"

    def do_POST(self) -> None:
        try:
            path = urllib.parse.urlsplit(self.path).path
            body = self._read_json()
            if path == "/v1/requests":
                ttl = self.server.store.create_request(self.client_address[0], body)
                self._json(201, {"status": "pending", "expiresInSeconds": ttl})
                return
            if path == "/v1/operator/sessions":
                self._authorize()
                self.server.store.register_session(body.get("sessionId", ""), body.get("ttlSeconds", 1))
                self._json(201, {"status": "waiting"})
                return
            match = re.fullmatch(r"/v1/operator/requests/([A-Za-z0-9_-]{20,100})/accept", path)
            if match:
                self._authorize()
                self.server.store.accept(body.get("sessionId", ""), match.group(1), body.get("ciphertext", ""))
                self._json(200, {"status": "accepted"})
                return
            raise BrokerError(404, "not_found")
        except BrokerError as error:
            self._json(error.status, {"error": error.code})
        except Exception:
            self._json(400, {"error": "invalid_request"})

    def do_GET(self) -> None:
        try:
            parsed = urllib.parse.urlsplit(self.path)
            query = urllib.parse.parse_qs(parsed.query)
            if parsed.path == "/v1/operator/requests":
                self._authorize()
                session_id = query.get("sessionId", [""])[0]
                self._json(200, {"requests": self.server.store.list_pending(session_id)})
                return
            match = re.fullmatch(r"/v1/requests/([A-Za-z0-9_-]{20,100})", parsed.path)
            if match:
                nonce = query.get("nonce", [""])[0]
                status, ciphertext = self.server.store.claim(match.group(1), nonce)
                if status == "pending":
                    self._json(202, {"status": status})
                elif status == "delivered":
                    self._json(200, {"status": status, "ciphertext": ciphertext})
                else:
                    self._json(410, {"status": status})
                return
            raise BrokerError(404, "not_found")
        except BrokerError as error:
            self._json(error.status, {"error": error.code})
        except Exception:
            self._json(400, {"error": "invalid_request"})

    def do_DELETE(self) -> None:
        try:
            parsed = urllib.parse.urlsplit(self.path)
            match = re.fullmatch(r"/v1/requests/([A-Za-z0-9_-]{20,100})", parsed.path)
            if match:
                nonce = urllib.parse.parse_qs(parsed.query).get("nonce", [""])[0]
                self.server.store.cancel(match.group(1), nonce)
                self._json(200, {"status": "revoked"})
                return
            session_match = re.fullmatch(r"/v1/operator/sessions/([A-Za-z0-9_-]{20,100})", parsed.path)
            if session_match:
                self._authorize()
                self.server.store.revoke_session(session_match.group(1))
                self._json(200, {"status": "revoked"})
                return
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
        raw = self.rfile.read(length)
        try:
            value = json.loads(raw.decode("utf-8"))
        except Exception as error:
            raise BrokerError(400, "invalid_json") from error
        if not isinstance(value, dict):
            raise BrokerError(400, "invalid_json")
        return value

    def _authorize(self) -> None:
        expected = f"Bearer {self.server.operator_token}"
        supplied = self.headers.get("Authorization", "")
        if not hmac.compare_digest(expected, supplied):
            raise BrokerError(401, "unauthorized")

    def _json(self, status: int, value: dict) -> None:
        raw = json.dumps(value, separators=(",", ":")).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(raw)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(raw)

    def log_message(self, format: str, *args: object) -> None:
        # Paths can contain a rendezvous nonce, so the DEV broker does not log requests.
        return


class BrokerHttpServer(ThreadingHTTPServer):
    def __init__(self, address: Tuple[str, int], store: BrokerStore, operator_token: str) -> None:
        super().__init__(address, BrokerHandler)
        self.store = store
        self.operator_token = operator_token


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Glosh Remote in-memory DEV broker")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8787)
    parser.add_argument("--operator-token", default=os.environ.get("BROKER_OPERATOR_TOKEN", ""))
    args = parser.parse_args()
    if not TOKEN_PATTERN.fullmatch(args.operator_token):
        parser.error("--operator-token must be a random base64url token (20-100 chars)")
    return args


def main() -> int:
    args = parse_args()
    server = BrokerHttpServer((args.host, args.port), BrokerStore(), args.operator_token)
    print(f"Glosh Remote DEV broker: http://{args.host}:{args.port}", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        return 0
    finally:
        server.server_close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
