import unittest

from glosh_remote_relay import b64u_decode, decrypt_box


class ProtocolCryptoTest(unittest.TestCase):
    def test_android_python_known_vector(self):
        key = bytes(range(32))
        envelope = {
            "seq": 1,
            "nonce": "AAECAwQFBgcICQoL",
            "ciphertext": "PCC9cquB4CGvIvjm3IgWCaH6pUaVCioZSxOs4T9TIsZkY9reg-Nz-wDNEIOqvQpIhzcHryfcBoE3NKb6D0_mKRJLmgLt",
        }
        payload = decrypt_box(
            key,
            "abcdefghijklmnopqrstuvwx",
            "server",
            envelope,
        )
        self.assertEqual("command", payload["kind"])
        self.assertEqual("test", payload["requestId"])
        self.assertEqual("ping", payload["action"])


if __name__ == "__main__":
    unittest.main()
