"""Operator client and RSA-OAEP sealing for the Glosh Remote broker."""

from __future__ import annotations

import base64
import json
import re
import urllib.request
from dataclasses import dataclass
from typing import List, Optional

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding, rsa


SEALED_PREFIX = "GLOSH-RENDEZVOUS-2"
CONTEXT_PATTERN = re.compile(r"[0-9a-f]{64}")


@dataclass
class PendingRequest:
    request_id: str
    public_key: str
    seal_context_sha256: str
    manufacturer: str
    model: str
    android: str
    expires_in_seconds: int = 0


class BrokerOperatorClient:
    def __init__(self, base_url: str, operator_key: str) -> None:
        self.base_url = base_url.rstrip("/")
        self.operator_key = operator_key

    def register(self, ttl_seconds: int = 0) -> None:
        del ttl_seconds
        self._request("operator_open")

    def pending(self) -> List[PendingRequest]:
        value = self._request("operator_list")
        return [
            PendingRequest(
                request_id=item["request_id"],
                public_key=item["client_public_key"],
                seal_context_sha256=item["seal_context_sha256"],
                manufacturer=item.get("device_manufacturer", "?"),
                model=item.get("device_model", "?"),
                android=item.get("android_version", "?"),
            )
            for item in value.get("requests", [])
            if item.get("state", "pending") == "pending"
        ]

    def accept(self, request: PendingRequest, descriptor: str) -> str:
        ciphertext = seal_descriptor(
            request.public_key,
            request.request_id,
            request.seal_context_sha256,
            descriptor,
        )
        self.accept_sealed(request, ciphertext)
        return ciphertext

    def accept_sealed(self, request: PendingRequest, ciphertext: str) -> None:
        self._request(
            "operator_accept",
            {
                "request_id": request.request_id,
                "ciphertext": ciphertext,
                "ciphertext_alg": "RSA-OAEP-SHA256",
            },
        )

    def revoke(self, request_id: str) -> None:
        self._request("operator_revoke", {"request_id": request_id})

    def close(self) -> None:
        try:
            self._request("operator_close")
        except Exception:
            pass
        finally:
            self.operator_key = ""

    def _request(self, action: str, value: Optional[dict] = None) -> dict:
        body = {"action": action}
        body.update(value or {})
        request = urllib.request.Request(
            self.base_url,
            data=json.dumps(body, separators=(",", ":")).encode("utf-8"),
            method="POST",
            headers={
                "x-glosh-operator-key": self.operator_key,
                "Content-Type": "application/json",
                "Cache-Control": "no-store",
            },
        )
        with urllib.request.urlopen(request, timeout=10) as response:
            raw = response.read().decode("utf-8")
            return json.loads(raw) if raw else {}


def seal_descriptor(
    public_key_b64: str,
    request_id: str,
    seal_context_sha256: str,
    descriptor: str,
) -> str:
    if not CONTEXT_PATTERN.fullmatch(seal_context_sha256):
        raise ValueError("broker request did not provide a valid seal context")
    encoded_key = base64.urlsafe_b64decode(
        public_key_b64 + "=" * (-len(public_key_b64) % 4)
    )
    public_key = serialization.load_der_public_key(encoded_key)
    if not isinstance(public_key, rsa.RSAPublicKey) or public_key.key_size < 3072:
        raise ValueError("broker request did not provide a supported RSA key")
    plaintext = (
        f"{SEALED_PREFIX}\n{request_id}\n{seal_context_sha256}\n{descriptor}"
    ).encode("utf-8")
    ciphertext = public_key.encrypt(
        plaintext,
        padding.OAEP(
            mgf=padding.MGF1(algorithm=hashes.SHA256()),
            algorithm=hashes.SHA256(),
            label=None,
        ),
    )
    return base64.urlsafe_b64encode(ciphertext).decode("ascii").rstrip("=")
