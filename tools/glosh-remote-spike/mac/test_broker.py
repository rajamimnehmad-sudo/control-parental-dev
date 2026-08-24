import base64
import json
import threading
import unittest
import urllib.request

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding, rsa

from broker_client import BrokerOperatorClient, SEALED_PREFIX, seal_descriptor
from support_session_broker import BrokerError, BrokerHttpServer, BrokerStore


REQUEST_ID = "request_abcdefghijklmnop"
NONCE = "nonce_abcdefghijklmnopqr"
SESSION_ID = "session_abcdefghijklmnop"
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


def request_value(public_key):
    return {
        "requestId": REQUEST_ID,
        "publicKey": public_key,
        "nonce": NONCE,
        "manufacturer": "Samsung",
        "model": "SM-S908E",
        "android": "16",
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

    def register_and_request(self):
        self.store.register_session(SESSION_ID, 120)
        self.store.create_request("phone", request_value(self.public_key))

    def test_explicit_accept_single_use_and_ciphertext_only(self):
        self.register_and_request()
        status, value = self.store.claim(REQUEST_ID, NONCE)
        self.assertEqual(("pending", None), (status, value))

        pending = self.store.list_pending(SESSION_ID)[0]
        ciphertext = seal_descriptor(
            pending["publicKey"], pending["requestId"], pending["nonce"], DESCRIPTOR
        )
        self.store.accept(SESSION_ID, REQUEST_ID, ciphertext)

        snapshot = self.store.broker_snapshot(REQUEST_ID)
        self.assertNotIn("descriptor", snapshot)
        self.assertNotIn("sessionKey", snapshot)
        self.assertNotIn(DESCRIPTOR, json.dumps(snapshot))
        self.assertNotIn("AAECAwQF", json.dumps(snapshot))

        with self.assertRaises(BrokerError) as duplicate:
            self.store.accept(SESSION_ID, REQUEST_ID, ciphertext)
        self.assertEqual(409, duplicate.exception.status)

        status, delivered = self.store.claim(REQUEST_ID, NONCE)
        self.assertEqual("delivered", status)
        plaintext = decrypt(self.private_key, delivered)
        self.assertEqual(
            f"{SEALED_PREFIX}\n{REQUEST_ID}\n{NONCE}\n{DESCRIPTOR}", plaintext
        )
        self.assertEqual(("consumed", None), self.store.claim(REQUEST_ID, NONCE))
        with self.assertRaises(BrokerError) as wrong_nonce:
            self.store.claim(REQUEST_ID, "nonce_wrong_abcdefghijkl")
        self.assertEqual(404, wrong_nonce.exception.status)

    def test_duplicate_request_ttl_cancel_and_session_revocation(self):
        self.register_and_request()
        with self.assertRaises(BrokerError) as duplicate:
            self.store.create_request("phone", request_value(self.public_key))
        self.assertEqual(409, duplicate.exception.status)

        self.store.cancel(REQUEST_ID, NONCE)
        self.assertEqual(("revoked", None), self.store.claim(REQUEST_ID, NONCE))

        second = request_value(self.public_key)
        second["requestId"] = "request_second_abcdefghij"
        second["nonce"] = "nonce_second_abcdefghijkl"
        self.store.create_request("phone", second)
        self.store.revoke_session(SESSION_ID)
        self.assertEqual(
            ("revoked", None),
            self.store.claim(second["requestId"], second["nonce"]),
        )

        self.store.register_session(SESSION_ID, 120)
        third = request_value(self.public_key)
        third["requestId"] = "request_third_abcdefghijk"
        third["nonce"] = "nonce_third_abcdefghijklmn"
        self.store.create_request("phone", third)
        self.clock.now += 31
        self.assertEqual(
            ("expired", None),
            self.store.claim(third["requestId"], third["nonce"]),
        )

    def test_request_requires_exactly_one_waiting_operator(self):
        with self.assertRaises(BrokerError) as unavailable:
            self.store.create_request("phone", request_value(self.public_key))
        self.assertEqual(503, unavailable.exception.status)

        self.store.register_session(SESSION_ID, 120)
        self.store.register_session("session_second_abcdefgh", 120)
        with self.assertRaises(BrokerError) as ambiguous:
            self.store.create_request("phone", request_value(self.public_key))
        self.assertEqual(503, ambiguous.exception.status)

    def test_per_source_rate_limit_is_fail_closed(self):
        self.store.register_session(SESSION_ID, 120)
        for index in range(5):
            value = request_value(self.public_key)
            value["requestId"] = f"request_rate_{index}_abcdefghijk"
            value["nonce"] = f"nonce_rate_{index}_abcdefghijklmn"
            self.store.create_request("same-phone", value)
        rejected = request_value(self.public_key)
        rejected["requestId"] = "request_rate_6_abcdefghijk"
        rejected["nonce"] = "nonce_rate_6_abcdefghijklmn"
        with self.assertRaises(BrokerError) as limit:
            self.store.create_request("same-phone", rejected)
        self.assertEqual(429, limit.exception.status)


class BrokerHttpIntegrationTest(unittest.TestCase):
    def test_operator_accepts_and_test_client_decrypts(self):
        store = BrokerStore(request_ttl=30)
        server = BrokerHttpServer(("127.0.0.1", 0), store, TOKEN)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        base_url = f"http://127.0.0.1:{server.server_port}"
        operator = BrokerOperatorClient(base_url, TOKEN, SESSION_ID)
        private_key, public_key = identity()
        try:
            operator.register(120)
            body = json.dumps(request_value(public_key)).encode("utf-8")
            create = urllib.request.Request(
                base_url + "/v1/requests",
                data=body,
                method="POST",
                headers={"Content-Type": "application/json"},
            )
            with urllib.request.urlopen(create) as response:
                self.assertEqual(201, response.status)

            pending = operator.pending()
            self.assertEqual(1, len(pending))
            ciphertext = operator.accept(pending[0], DESCRIPTOR)
            self.assertNotIn(DESCRIPTOR, ciphertext)

            claim = urllib.request.urlopen(
                base_url + f"/v1/requests/{REQUEST_ID}?nonce={NONCE}"
            )
            delivered = json.loads(claim.read().decode("utf-8"))
            plaintext = decrypt(private_key, delivered["ciphertext"])
            self.assertTrue(plaintext.endswith(DESCRIPTOR))
        finally:
            operator.close()
            server.shutdown()
            server.server_close()
            thread.join(timeout=2)


if __name__ == "__main__":
    unittest.main()
