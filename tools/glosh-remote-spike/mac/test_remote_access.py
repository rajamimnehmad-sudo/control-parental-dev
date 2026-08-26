from __future__ import annotations

import base64
import hashlib
import os
import tempfile
import unittest

from remote_access import FILE_CHUNK_BYTES, push_file, validate_remote_path


class RecordingSession:
    def __init__(self) -> None:
        self.calls = []

    async def command(self, action, arguments=None, timeout=20.0):
        self.calls.append((action, arguments, timeout))
        return {"ok": True, "output": "ok"}


class RemoteAccessTest(unittest.IsolatedAsyncioTestCase):
    async def test_push_streams_ordered_chunks_with_declared_sha256(self) -> None:
        payload = os.urandom(FILE_CHUNK_BYTES * 2 + 17)
        with tempfile.NamedTemporaryFile() as source:
            source.write(payload)
            source.flush()
            session = RecordingSession()
            result = await push_file(
                session,
                source.name,
                "/data/local/tmp/glosh-test.apk",
            )

        self.assertTrue(result["ok"])
        self.assertEqual("push-start", session.calls[0][0])
        start = session.calls[0][1]
        self.assertEqual(len(payload), start["size"])
        self.assertEqual(hashlib.sha256(payload).hexdigest(), start["sha256"])
        chunks = [call for call in session.calls if call[0] == "push-chunk"]
        self.assertEqual([0, FILE_CHUNK_BYTES, FILE_CHUNK_BYTES * 2], [c[1]["offset"] for c in chunks])
        rebuilt = b"".join(
            base64.urlsafe_b64decode(c[1]["data"] + "=" * (-len(c[1]["data"]) % 4))
            for c in chunks
        )
        self.assertEqual(payload, rebuilt)
        self.assertEqual("push-finish", session.calls[-1][0])

    async def test_push_rejects_invalid_remote_path_before_sending(self) -> None:
        with tempfile.NamedTemporaryFile() as source:
            source.write(b"apk")
            source.flush()
            session = RecordingSession()
            with self.assertRaises(ValueError):
                await push_file(session, source.name, "relative/path")
        self.assertEqual([], session.calls)

    def test_remote_path_rejects_sync_delimiter_and_nul(self) -> None:
        for value in ("/data/local/tmp/a,b", "/data/local/tmp/a\0b"):
            with self.assertRaises(ValueError):
                validate_remote_path(value)


if __name__ == "__main__":
    unittest.main()
