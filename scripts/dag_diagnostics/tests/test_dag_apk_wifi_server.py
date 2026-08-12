from __future__ import annotations

import http.client
import json
import pathlib
import sys
import tempfile
import threading
import unittest


SCRIPT_DIR = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPT_DIR))

from dag_apk_wifi_server import DagApkServer  # noqa: E402


class DagApkWifiServerTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        directory = pathlib.Path(self.temporary_directory.name)
        self.apk_bytes = bytes(range(256)) * 8
        self.apk_path = directory / "DagBrowser-dev-debug.apk"
        self.apk_path.write_bytes(self.apk_bytes)
        self.metadata_path = directory / "output-metadata.json"
        self.metadata_path.write_text(
            json.dumps(
                {
                    "elements": [
                        {
                            "versionCode": 206,
                            "versionName": "0.70.10-dev",
                        }
                    ]
                }
            ),
            encoding="utf-8",
        )
        self.token = "A" * 32
        self.server = DagApkServer(
            ("127.0.0.1", 0),
            self.token,
            self.apk_path,
            self.metadata_path,
        )
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()

    def tearDown(self) -> None:
        self.server.shutdown()
        self.server.server_close()
        self.thread.join(timeout=2)
        self.temporary_directory.cleanup()

    def request(self, method: str, path: str, headers=None):
        connection = http.client.HTTPConnection("127.0.0.1", self.server.server_port, timeout=2)
        connection.request(method, path, headers=headers or {})
        response = connection.getresponse()
        body = response.read()
        headers_result = dict(response.getheaders())
        connection.close()
        return response.status, headers_result, body

    def test_unknown_routes_do_not_expose_the_repository(self) -> None:
        status, _, _ = self.request("GET", "/")
        self.assertEqual(404, status)
        status, _, _ = self.request("GET", "/app-dag-browser/build.gradle.kts")
        self.assertEqual(404, status)

    def test_private_index_points_to_current_version_without_cache(self) -> None:
        status, headers, body = self.request("GET", f"/t/{self.token}/")
        self.assertEqual(200, status)
        self.assertEqual("no-store", headers["Cache-Control"])
        self.assertIn(b"0.70.10-dev", body)
        self.assertIn("Última actualización disponible:".encode(), body)
        self.assertIn(b"<time datetime=\"", body)
        self.assertIn(b"dag.apk?v=", body)

    def test_full_apk_download_is_exact_and_supports_head(self) -> None:
        path = f"/t/{self.token}/dag.apk"
        status, headers, body = self.request("GET", path)
        self.assertEqual(200, status)
        self.assertEqual(self.apk_bytes, body)
        self.assertEqual(str(len(self.apk_bytes)), headers["Content-Length"])
        self.assertEqual("bytes", headers["Accept-Ranges"])
        self.assertIn("DAG-Browser-206-0.70.10-dev.apk", headers["Content-Disposition"])

        status, headers, body = self.request("HEAD", path)
        self.assertEqual(200, status)
        self.assertEqual(b"", body)
        self.assertEqual(str(len(self.apk_bytes)), headers["Content-Length"])

    def test_range_download_supports_android_resume(self) -> None:
        status, headers, body = self.request(
            "GET",
            f"/t/{self.token}/dag.apk",
            headers={"Range": "bytes=100-199"},
        )
        self.assertEqual(206, status)
        self.assertEqual(self.apk_bytes[100:200], body)
        self.assertEqual(f"bytes 100-199/{len(self.apk_bytes)}", headers["Content-Range"])

    def test_invalid_range_fails_without_sending_apk_bytes(self) -> None:
        status, headers, body = self.request(
            "GET",
            f"/t/{self.token}/dag.apk",
            headers={"Range": "bytes=99999-100000"},
        )
        self.assertEqual(416, status)
        self.assertEqual(b"", body)
        self.assertEqual(f"bytes */{len(self.apk_bytes)}", headers["Content-Range"])


if __name__ == "__main__":
    unittest.main()
