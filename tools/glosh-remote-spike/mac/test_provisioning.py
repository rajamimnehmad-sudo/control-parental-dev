import base64
import hashlib
import tempfile
import unittest
from pathlib import Path

from provisioning_console import maintenance_shell, provision_apk


class FakeSession:
    def __init__(self, eligible=True):
        self.eligible = eligible
        self.calls = []
        self.received = bytearray()
        self.transfer_id = None
        self.expected_sha = None

    async def command(self, action, params=None, timeout=20.0):
        self.calls.append((action, params, timeout))
        if action == "owner-preflight":
            return self._ok({
                "eligible": self.eligible,
                "ownerState": "NONE",
                "accountCount": 0,
                "blockReason": "" if self.eligible else "bloqueado",
            })
        if action == "artifact-begin":
            self.transfer_id = params["transferId"]
            self.expected_sha = params["sha256"]
            return self._ok({"transferId": self.transfer_id, "nextOffset": 0})
        if action == "artifact-chunk":
            self.assert_offset(params["offset"])
            encoded = params["data"]
            block = base64.urlsafe_b64decode(encoded + "=" * (-len(encoded) % 4))
            self.received.extend(block)
            return self._ok({"transferId": self.transfer_id, "nextOffset": len(self.received)})
        if action == "artifact-stage":
            return self._ok({
                "transferId": self.transfer_id,
                "artifactSha256": self.expected_sha,
                "packageName": "com.contentfilter.user.dev",
                "versionCode": 400,
                "signerSha256": "b" * 64,
            })
        if action == "owner-commit":
            return self._ok({"installed": True, "deviceOwner": True})
        if action == "maintenance-shell":
            return self._ok({"output": "uid=2000(shell)"})
        raise AssertionError(action)

    def assert_offset(self, offset):
        if offset != len(self.received):
            raise AssertionError(f"offset {offset} != {len(self.received)}")

    @staticmethod
    def _ok(value):
        import json
        return {"ok": True, "output": json.dumps(value)}


class ProvisioningConsoleTest(unittest.IsolatedAsyncioTestCase):
    async def test_blocked_preflight_never_transfers(self):
        session = FakeSession(eligible=False)
        with self.assertRaisesRegex(RuntimeError, "bloqueado"):
            await provision_apk(session, "/not/read.apk", self._confirm)
        self.assertEqual(["owner-preflight"], [call[0] for call in session.calls])

    async def test_transfer_reassembles_and_commit_is_artifact_bound(self):
        payload = bytes(range(256)) * 1000
        session = FakeSession()
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory, "glosh.apk")
            apk.write_bytes(payload)

            async def confirm(_prompt):
                return "DEVICE OWNER " + hashlib.sha256(payload).hexdigest()[:12]

            result = await provision_apk(session, str(apk), confirm)

        self.assertEqual(payload, bytes(session.received))
        self.assertTrue(result["deviceOwner"])
        commit = next(call for call in session.calls if call[0] == "owner-commit")
        self.assertEqual(session.transfer_id, commit[1]["transferId"])
        self.assertEqual("b" * 64, commit[1]["signerSha256"])
        self.assertNotIn(str(apk), repr(session.calls))

    async def test_wrong_confirmation_never_commits(self):
        session = FakeSession()
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory, "glosh.apk")
            apk.write_bytes(b"apk")

            async def decline(_prompt):
                return "no"

            with self.assertRaisesRegex(RuntimeError, "cancelada"):
                await provision_apk(session, str(apk), decline)
        self.assertNotIn("owner-commit", [call[0] for call in session.calls])

    async def test_maintenance_shell_uses_dedicated_encrypted_action(self):
        session = FakeSession()
        output = await maintenance_shell(session, "id")
        self.assertEqual("uid=2000(shell)", output)
        self.assertEqual("maintenance-shell", session.calls[0][0])
        self.assertEqual({"command": "id"}, session.calls[0][1])

    @staticmethod
    async def _confirm(_prompt):
        return "unused"


if __name__ == "__main__":
    unittest.main()
