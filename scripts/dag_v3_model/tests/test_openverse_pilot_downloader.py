from __future__ import annotations

import json
import socket
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


REPO_ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(REPO_ROOT / "scripts/dag_v3_model"))

import openverse_pilot_downloader as downloader  # noqa: E402


class FakeResponse:
    def __init__(self, body: bytes, url: str = "https://media.example/image.jpg"):
        self.body = body
        self.url = url

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_value, traceback):
        return False

    def read(self, amount: int) -> bytes:
        return self.body[:amount]

    def geturl(self) -> str:
        return self.url


def candidate(identifier: str = "one") -> dict:
    return {
        "openverse_id": identifier,
        "query": "modest fashion woman",
        "source": "wikimedia",
        "landing_url": f"https://example.org/work/{identifier}",
        "asset_url": f"https://media.example/{identifier}.jpg",
        "license_id": "by",
        "license_url": "https://creativecommons.org/licenses/by/4.0/",
        "attribution": "Example Author / CC BY 4.0",
    }


PUBLIC_ADDRESS = [
    (socket.AF_INET, socket.SOCK_STREAM, 6, "", ("93.184.216.34", 443))
]


class OpenversePilotDownloaderTest(unittest.TestCase):
    def _write_inventory(self, directory: Path, rows: list[dict]) -> Path:
        path = directory / "inventory.jsonl"
        path.write_text(
            "".join(json.dumps(row) + "\n" for row in rows),
            encoding="utf-8",
        )
        return path

    def test_downloads_supported_image_and_writes_provenance(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            inventory = self._write_inventory(root, [candidate()])
            jpeg = b"\xff\xd8\xff" + b"pilot-image"
            with (
                patch.object(downloader.socket, "getaddrinfo", return_value=PUBLIC_ADDRESS),
                patch.object(downloader, "urlopen", return_value=FakeResponse(jpeg)),
            ):
                summary = downloader.download_pilot(inventory, root / "output")

            self.assertEqual(1, summary["downloaded"])
            record = json.loads((root / "output/downloads.jsonl").read_text())
            self.assertEqual("downloaded", record["status"])
            self.assertEqual("needs_license_and_visual_review", record["review_status"])
            self.assertTrue((root / "output" / record["local_path"]).is_file())

    def test_rejects_private_destination(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            inventory = self._write_inventory(root, [candidate()])
            private_address = [
                (socket.AF_INET, socket.SOCK_STREAM, 6, "", ("127.0.0.1", 443))
            ]
            with patch.object(
                downloader.socket, "getaddrinfo", return_value=private_address
            ):
                summary = downloader.download_pilot(inventory, root / "output")

            self.assertEqual(0, summary["downloaded"])
            self.assertEqual(1, summary["failed"])

    def test_rejects_non_image_and_oversized_body(self) -> None:
        cases = [
            b"<html>not an image</html>",
            b"\xff\xd8\xff" + b"x" * downloader.MAX_FILE_BYTES,
        ]
        for body in cases:
            with self.subTest(size=len(body)):
                with tempfile.TemporaryDirectory() as temporary:
                    root = Path(temporary)
                    inventory = self._write_inventory(root, [candidate()])
                    with (
                        patch.object(
                            downloader.socket,
                            "getaddrinfo",
                            return_value=PUBLIC_ADDRESS,
                        ),
                        patch.object(
                            downloader, "urlopen", return_value=FakeResponse(body)
                        ),
                    ):
                        summary = downloader.download_pilot(inventory, root / "output")
                    self.assertEqual(0, summary["downloaded"])
                    self.assertEqual(1, summary["failed"])

    def test_deduplicates_exact_content(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            inventory = self._write_inventory(
                root, [candidate("one"), candidate("two")]
            )
            jpeg = b"\xff\xd8\xff" + b"same-image"
            with (
                patch.object(downloader.socket, "getaddrinfo", return_value=PUBLIC_ADDRESS),
                patch.object(downloader, "urlopen", return_value=FakeResponse(jpeg)),
            ):
                summary = downloader.download_pilot(inventory, root / "output")
            self.assertEqual(1, summary["downloaded"])
            self.assertEqual(1, summary["duplicates"])

    def test_deduplicates_against_previous_batch_manifest(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            inventory = self._write_inventory(root, [candidate()])
            jpeg = b"\xff\xd8\xff" + b"previous-image"
            digest = downloader.hashlib.sha256(jpeg).hexdigest()
            previous = root / "previous.jsonl"
            previous.write_text(
                json.dumps({"status": "downloaded", "sha256": digest}) + "\n",
                encoding="utf-8",
            )
            with (
                patch.object(downloader.socket, "getaddrinfo", return_value=PUBLIC_ADDRESS),
                patch.object(downloader, "urlopen", return_value=FakeResponse(jpeg)),
            ):
                summary = downloader.download_pilot(
                    inventory,
                    root / "output",
                    known_manifests=[previous],
                )
            self.assertEqual(0, summary["downloaded"])
            self.assertEqual(1, summary["duplicates"])
            self.assertEqual([], list((root / "output/images").iterdir()))

    def test_enforces_item_limit(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            inventory = self._write_inventory(root, [candidate()])
            with self.assertRaises(ValueError):
                downloader.download_pilot(inventory, root / "output", limit=101)

    def test_refuses_to_mix_with_an_existing_download(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            inventory = self._write_inventory(root, [candidate()])
            output = root / "output"
            output.mkdir()
            (output / "downloads.jsonl").write_text("{}\n", encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "already contains"):
                downloader.download_pilot(inventory, output)


if __name__ == "__main__":
    unittest.main()
