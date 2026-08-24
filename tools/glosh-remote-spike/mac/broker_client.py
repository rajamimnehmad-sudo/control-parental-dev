"""Operator-side client and RSA-OAEP sealing for the Glosh Remote DEV broker."""

from __future__ import annotations

import base64
import json
import urllib.parse
import urllib.request
from dataclasses import dataclass
from typing import List, Optional

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding, rsa


SEALED_PREFIX = "GLOSH-RENDEZVOUS-1"


@dataclass
class PendingRequest:
    request_id: str
    public_key: str
    nonce: str
    manufacturer: str
    model: str
    android: str
    expires_in_seconds: int


class BrokerOperatorClient:
    def __init__(self, base_url: str, token: str, session_id: str) -> None:
        self.base_url = base_url.rstrip("/")
        self.token = token
        self.session_id = session_id

    def register(self, ttl_seconds: int) -> None:
        self._request(
            "POST",
            "/v1/operator/sessions",
            {"sessionId": self.session_id, "ttlSeconds": ttl_seconds},
        )

    def pending(self) -> List[PendingRequest]:
        value = self._request(
            "GET",
            "/v1/operator/requests?"
            + urllib.parse.urlencode({"sessionId": self.session_id}),
        )
        return [
            PendingRequest(
                request_id=item["requestId"],
                public_key=item["publicKey"],
                nonce=item["nonce"],
                manufacturer=item.get("manufacturer", "?"),
                model=item.get("model", "?"),
                android=item.get("android", "?"),
                expires_in_seconds=int(item.get("expiresInSeconds", 0)),
            )
            for item in value.get("requests", [])
        ]

    def accept(self, request: PendingRequest, descriptor: str) -> str:
        ciphertext = seal_descriptor(
            request.public_key,
            request.request_id,
            request.nonce,
            descriptor,
        )
        self._request(
            "POST",
            f"/v1/operator/requests/{request.request_id}/accept",
            {"sessionId": self.session_id, "ciphertext": ciphertext},
        )
        return ciphertext

    def accept_sealed(self, request: PendingRequest, ciphertext: str) -> None:
        self._request(
            "POST",
            f"/v1/operator/requests/{request.request_id}/accept",
            {"sessionId": self.session_id, "ciphertext": ciphertext},
        )

    def close(self) -> None:
        try:
            self._request("DELETE", f"/v1/operator/sessions/{self.session_id}")
        except Exception:
            pass

    def _request(self, method: str, path: str, value: Optional[dict] = None) -> dict:
        raw = None if value is None else json.dumps(value, separators=(",", ":")).encode("utf-8")
        request = urllib.request.Request(
            self.base_url + path,
            data=raw,
            method=method,
            headers={
                "Authorization": f"Bearer {self.token}",
                "Content-Type": "application/json",
                "Cache-Control": "no-store",
            },
        )
        with urllib.request.urlopen(request, timeout=10) as response:
            return json.loads(response.read().decode("utf-8"))


def seal_descriptor(public_key_b64: str, request_id: str, nonce: str, descriptor: str) -> str:
    encoded_key = base64.urlsafe_b64decode(public_key_b64 + "=" * (-len(public_key_b64) % 4))
    public_key = serialization.load_der_public_key(encoded_key)
    if not isinstance(public_key, rsa.RSAPublicKey) or public_key.key_size < 3072:
        raise ValueError("broker request did not provide a supported RSA key")
    plaintext = f"{SEALED_PREFIX}\n{request_id}\n{nonce}\n{descriptor}".encode("utf-8")
    ciphertext = public_key.encrypt(
        plaintext,
        padding.OAEP(
            mgf=padding.MGF1(algorithm=hashes.SHA256()),
            algorithm=hashes.SHA256(),
            label=None,
        ),
    )
    return base64.urlsafe_b64encode(ciphertext).decode("ascii").rstrip("=")
