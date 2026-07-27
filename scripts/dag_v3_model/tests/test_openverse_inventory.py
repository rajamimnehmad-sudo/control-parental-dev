from __future__ import annotations

import io
import json
import sys
import unittest
from pathlib import Path
from unittest.mock import patch


REPO_ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(REPO_ROOT / "scripts/dag_v3_model"))

import openverse_inventory  # noqa: E402


class FakeResponse:
    def __init__(self, payload: dict | bytes):
        self.body = payload if isinstance(payload, bytes) else json.dumps(payload).encode()

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_value, traceback):
        return False

    def read(self, amount: int) -> bytes:
        return self.body[:amount]


def result(
    identifier: str,
    *,
    license_id: str = "by",
    landing: str | None = None,
    mature: bool = False,
) -> dict:
    return {
        "id": identifier,
        "title": f"Title {identifier}",
        "creator": "Example Author",
        "creator_url": "https://example.org/author",
        "license": license_id,
        "license_version": "4.0",
        "license_url": "https://creativecommons.org/licenses/by/4.0/",
        "attribution": "Example Author / CC BY 4.0",
        "provider": "wikimedia",
        "source": "wikimedia",
        "foreign_landing_url": landing or f"https://example.org/work/{identifier}",
        "url": f"https://example.org/media/{identifier}.jpg",
        "width": 640,
        "height": 480,
        "filetype": "jpg",
        "mature": mature,
    }


class OpenverseInventoryTest(unittest.TestCase):
    def test_collects_metadata_without_requesting_asset_urls(self) -> None:
        requests = []

        def fake_urlopen(request, timeout):
            requests.append((request.full_url, timeout))
            return FakeResponse({"page_count": 1, "results": [result("one")]})

        with patch.object(openverse_inventory, "urlopen", side_effect=fake_urlopen):
            inventory = openverse_inventory.collect_inventory(["modest fashion"], page_size=5)

        self.assertEqual(1, len(inventory.candidates))
        self.assertEqual("needs_review", inventory.candidates[0]["review_status"])
        self.assertEqual(1, len(requests))
        self.assertTrue(requests[0][0].startswith(openverse_inventory.API_URL))
        self.assertIn("license=by%2Cby-sa%2Ccc0%2Cpdm", requests[0][0])
        self.assertIn("mature=false", requests[0][0])
        self.assertNotIn("/media/", requests[0][0])

    def test_deduplicates_ids_and_landing_pages_across_queries(self) -> None:
        pages = [
            {"page_count": 1, "results": [result("one"), result("two")]},
            {
                "page_count": 1,
                "results": [
                    result("one"),
                    result("three", landing="https://example.org/work/two"),
                ],
            },
        ]
        with patch.object(
            openverse_inventory,
            "urlopen",
            side_effect=[FakeResponse(page) for page in pages],
        ):
            inventory = openverse_inventory.collect_inventory(["query one", "query two"])

        self.assertEqual(2, len(inventory.candidates))
        self.assertEqual(2, inventory.skipped_duplicates)

    def test_skips_disallowed_license_and_mature_result(self) -> None:
        payload = {
            "page_count": 1,
            "results": [
                result("allowed"),
                result("noncommercial", license_id="by-nc"),
                result("mature", mature=True),
            ],
        }
        with patch.object(openverse_inventory, "urlopen", return_value=FakeResponse(payload)):
            inventory = openverse_inventory.collect_inventory(["fashion"])

        self.assertEqual(["allowed"], [item["openverse_id"] for item in inventory.candidates])
        self.assertEqual(2, inventory.skipped_ineligible)

    def test_missing_metadata_becomes_review_flags(self) -> None:
        item = result("missing")
        item["creator"] = None
        item["foreign_landing_url"] = None
        item["license_url"] = "http://insecure.example/license"
        with patch.object(
            openverse_inventory,
            "urlopen",
            return_value=FakeResponse({"page_count": 1, "results": [item]}),
        ):
            inventory = openverse_inventory.collect_inventory(["fashion"])

        flags = inventory.candidates[0]["review_flags"]
        self.assertIn("missing_creator", flags)
        self.assertIn("missing_landing_url", flags)
        self.assertIn("missing_license_url", flags)

    def test_enforces_query_and_pagination_limits(self) -> None:
        with self.assertRaises(ValueError):
            openverse_inventory.collect_inventory([""])
        with self.assertRaises(ValueError):
            openverse_inventory.collect_inventory(["fashion"], page_size=21)
        with self.assertRaises(ValueError):
            openverse_inventory.collect_inventory(["fashion"], max_pages=4)

    def test_rejects_oversized_metadata_response(self) -> None:
        oversized = b"x" * (openverse_inventory.MAX_RESPONSE_BYTES + 1)
        with patch.object(openverse_inventory, "urlopen", return_value=FakeResponse(oversized)):
            with self.assertRaisesRegex(ValueError, "size limit"):
                openverse_inventory.collect_inventory(["fashion"])

    def test_main_writes_jsonl_and_summary_to_separate_streams(self) -> None:
        inventory = openverse_inventory.InventoryResult(({"openverse_id": "one"},), 1, 0, 0)
        stdout = io.StringIO()
        stderr = io.StringIO()
        with (
            patch.object(openverse_inventory, "collect_inventory", return_value=inventory),
            patch.object(sys, "stdout", stdout),
            patch.object(sys, "stderr", stderr),
        ):
            exit_code = openverse_inventory.main(["--query", "fashion"])

        self.assertEqual(0, exit_code)
        self.assertEqual("one", json.loads(stdout.getvalue())["openverse_id"])
        self.assertEqual(1, json.loads(stderr.getvalue())["candidates"])


if __name__ == "__main__":
    unittest.main()
