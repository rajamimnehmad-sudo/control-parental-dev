import asyncio
import json
import time
import unittest

from glosh_remote_relay import RemoteSession, b64u_decode, decrypt_box, encrypt_box


class RecordingWebSocket:
    def __init__(self):
        self.sent = []

    async def send(self, raw):
        self.sent.append(raw)


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


class ProtocolCommandTest(unittest.IsolatedAsyncioTestCase):
    async def test_shell_arguments_remain_inside_encrypted_frame(self):
        session = RemoteSession(30)
        websocket = RecordingWebSocket()
        session.agent = websocket
        session.expires_at = time.monotonic() + 60

        pending = asyncio.create_task(
            session.command("shell", {"command": "logcat -d -t 200"})
        )
        await asyncio.sleep(0)
        envelope = json.loads(websocket.sent[0])
        payload = decrypt_box(session.key, session.sid, "server", envelope)
        self.assertEqual("shell", payload["action"])
        self.assertEqual("logcat -d -t 200", payload["arguments"]["command"])
        self.assertNotIn("logcat", websocket.sent[0])

        response = encrypt_box(
            session.key,
            session.sid,
            "agent",
            1,
            {
                "kind": "result",
                "requestId": payload["requestId"],
                "action": "shell",
                "ok": True,
                "output": "done",
            },
        )
        await session._handle_agent_message(json.dumps(response))
        self.assertEqual("done", (await pending)["output"])


if __name__ == "__main__":
    unittest.main()
