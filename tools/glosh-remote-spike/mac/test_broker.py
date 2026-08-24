import base64
import hashlib
import json
import threading
import unittest
import urllib.error
import urllib.request

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding, rsa

from broker_client import BrokerOperatorClient, SEALED_PREFIX, seal_descriptor
from support_session_broker import BrokerError, BrokerHttpServer, BrokerStore


REQUEST_ID = "request_abcdefghijklmnop"
NONCE = "nonce_abcdefghijklmnopqr"
TOKEN = "operator_abcdefghijklmnop"
DESCRIPTOR = (
    "gloshremote://join?v=1&url=wss%3A%2F%2Frelay.example.test"
    "&sid=abcdefghijklmnopqrstuvwx"
    "&k=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8"
)


class FakeClock:
    def __init__(self):
        self.now = 100.0

    def __call__(self):
        return self.now


def identity():
    private_key = rsa.generate_private_key(public_exponent=65537, key_size=3072)
    encoded = private_key.public_key().public_bytes(
        serialization.Encoding.DER,
        serialization.PublicFormat.SubjectPublicKeyInfo,
    )
    public_key = base64.urlsafe_b64encode(encoded).decode("ascii").rstrip("=")
    return private_key, public_key


def request_value(public_key, request_id=REQUEST_ID, nonce=NONCE):
    return {
        "request_id": request_id,
        "public_key": public_key,
        "nonce": nonce,
        "manufacturer": "Samsung",
        "model": "SM-S908E",
        "android_version": "16",
    }


def decrypt(private_key, ciphertext):
    raw = base64.urlsafe_b64decode(ciphertext + "=" * (-len(ciphertext) % 4))
    return private_key.decrypt(
        raw,
        padding.OAEP(
            mgf=padding.MGF1(algorithm=hashes.SHA256()),
            algorithm=hashes.SHA256(),
            label=None,
        ),
    ).decode("utf-8")


class BrokerLifecycleTest(unittest.TestCase):
    def setUp(self):
        self.clock = FakeClock()
        self.store = BrokerStore(clock=self.clock, request_ttl=30)
        self.private_key, self.public_key = identity()

    def open_and_request(self):
        self.store.operator_open()
        self.store.create_request("phone", request_value(self.public_key))

    def test_explicit_accept_single_use_and_ciphertext_only(self):
        self.open_and_request()
        self.assertEqual("pending", self.store.poll(REQUEST_ID, NONCE))

        pending = self.store.list_pending()[0]
        expected_context = hashlib.sha256(
            f"{REQUEST_ID}:{NONCE}".encode()
        ).hexdigest()
        self.assertEqual(expected_context, pending["seal_context_sha256"])
        self.assertNotIn("nonce", pending)
        ciphertext = seal_descriptor(
            pending["client_public_key"],
            pending["request_id"],
            pending["seal_context_sha256"],
            DESCRIPTOR,
        )
        self.store.accept(REQUEST_ID, ciphertext, "RSA-OAEP-SHA256")

        snapshot = self.store.broker_snapshot(REQUEST_ID)
        self.assertNotIn("descriptor", snapshot)
        self.assertNotIn("session_key", snapshot)
        self.assertNotIn("nonce", snapshot)
        self.assertNotIn(DESCRIPTOR, json.dumps(snapshot))
        self.assertNotIn("AAECAwQF", json.dumps(snapshot))

        with self.assertRaises(BrokerError) as duplicate:
            self.store.accept(REQUEST_ID, ciphertext, "RSA-OAEP-SHA256")
        self.assertEqual(409, duplicate.exception.status)

        self.assertEqual("accepted", self.store.poll(REQUEST_ID, NONCE))
        delivered = self.store.claim(REQUEST_ID, NONCE)
        plaintext = decrypt(self.private_key, delivered)
        self.assertEqual(
            f"{SEALED_PREFIX}\n{REQUEST_ID}\n{expected_context}\n{DESCRIPTOR}",
            plaintext,
        )
        with self.assertRaises(BrokerError) as consumed:
            self.store.claim(REQUEST_ID, NONCE)
        self.assertEqual("already_claimed", consumed.exception.code)

    def test_duplicate_ttl_cancel_and_operator_revocation(self):
        self.open_and_request()
        with self.assertRaises(BrokerError) as duplicate:
            self.store.create_request("phone", request_value(self.public_key))
        self.assertEqual(409, duplicate.exception.status)

        self.store.revoke(REQUEST_ID, NONCE)
        self.assertEqual("revoked", self.store.poll(REQUEST_ID, NONCE))

        second_id = "request_second_abcdefghij"
        second_nonce = "nonce_second_abcdefghijkl"
        self.store.create_request(
            "phone", request_value(self.public_key, second_id, second_nonce)
        )
        self.store.operator_revoke(second_id)
        self.assertEqual("revoked", self.store.poll(second_id, second_nonce))

        third_id = "request_third_abcdefghijk"
        third_nonce = "nonce_third_abcdefghijklmn"
        self.store.create_request(
            "phone", request_value(self.public_key, third_id, third_nonce)
        )
        self.clock.now += 31
        self.assertEqual("expired", self.store.poll(third_id, third_nonce))

    def test_request_requires_open_operator_window(self):
        with self.assertRaises(BrokerError) as unavailable:
            self.store.create_request("phone", request_value(self.public_key))
        self.assertEqual(503, unavailable.exception.status)
        self.store.operator_open()
        self.assertTrue(self.store.discover())
        self.store.operator_close()
        self.assertFalse(self.store.discover())

    def test_per_source_rate_limit_is_fail_closed(self):
        self.store.operator_open()
        for index in range(5):
            value = request_value(
                self.public_key,
                f"request_rate_{index}_abcdefghijk",
                f"nonce_rate_{index}_abcdefghijklmn",
            )
            self.store.create_request("same-phone", value)
        rejected = request_value(
            self.public_key,
            "request_rate_6_abcdefghijk",
            "nonce_rate_6_abcdefghijklmn",
        )
        with self.assertRaises(BrokerError) as limit:
            self.store.create_request("same-phone", rejected)
        self.assertEqual(429, limit.exception.status)


class BrokerHttpIntegrationTest(unittest.TestCase):
    def post(self, base_url, value, key=None):
        headers = {"Content-Type": "application/json"}
        if key is not None:
            headers["x-glosh-operator-key"] = key
        request = urllib.request.Request(
            base_url,
            data=json.dumps(value).encode(),
            method="POST",
            headers=headers,
        )
        with urllib.request.urlopen(request) as response:
            return response.status, json.loads(response.read())

    def test_action_contract_operator_accepts_and_client_decrypts(self):
        store = BrokerStore(request_ttl=30)
        server = BrokerHttpServer(("127.0.0.1", 0), store, TOKEN)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        base_url = f"http://127.0.0.1:{server.server_port}"
        operator = BrokerOperatorClient(base_url, TOKEN)
        private_key, public_key = identity()
        try:
            with self.assertRaises(urllib.error.HTTPError) as unauthorized:
                self.post(base_url, {"action": "operator_open"})
            self.assertEqual(401, unauthorized.exception.code)

            operator.register()
            self.assertEqual(
                (200, {"available": True}),
                self.post(base_url, {"action": "discover"}),
            )
            value = {"action": "request"} | request_value(public_key)
            status, _ = self.post(base_url, value)
            self.assertEqual(201, status)

            pending = operator.pending()
            self.assertEqual(1, len(pending))
            ciphertext = operator.accept(pending[0], DESCRIPTOR)
            self.assertNotIn(DESCRIPTOR, ciphertext)

            status, poll = self.post(
                base_url,
                {"action": "poll", "request_id": REQUEST_ID, "nonce": NONCE},
            )
            self.assertEqual((200, "accepted"), (status, poll["state"]))
            status, delivered = self.post(
                base_url,
                {"action": "claim", "request_id": REQUEST_ID, "nonce": NONCE},
            )
            self.assertEqual(200, status)
            self.assertTrue(decrypt(private_key, delivered["ciphertext"]).endswith(DESCRIPTOR))
            with self.assertRaises(urllib.error.HTTPError) as duplicate_claim:
                self.post(
                    base_url,
                    {"action": "claim", "request_id": REQUEST_ID, "nonce": NONCE},
                )
            self.assertEqual(409, duplicate_claim.exception.code)
        finally:
            operator.close()
            server.shutdown()
            server.server_close()
            thread.join(timeout=2)


if __name__ == "__main__":
    unittest.main()
