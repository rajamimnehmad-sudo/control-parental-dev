from __future__ import annotations

import json
import sys
import unittest
from pathlib import Path
from unittest.mock import patch


REPO_ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(REPO_ROOT / "scripts/dag_v3_model"))

import wikimedia_inventory  # noqa: E402


class FakeResponse:
    def __init__(self, payload: dict | bytes):
        self.body = payload if isinstance(payload, bytes) else json.dumps(payload).encode()

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_value, traceback):
        return False

    def read(self, amount: int) -> bytes:
        return self.body[:amount]


def page(page_id: int, license_name: str = "CC BY 4.0") -> dict:
    return {
        "pageid": page_id,
        "title": f"File:Example {page_id}.jpg",
        "imageinfo": [
            {
                "mime": "image/jpeg",
                "width": 1200,
                "height": 1800,
                "sha1": "a" * 40,
                "thumburl": f"https://upload.wikimedia.org/{page_id}.jpg",
                "descriptionurl": f"https://commons.wikimedia.org/wiki/File:{page_id}",
                "extmetadata": {
                    "Artist": {"value": "<b>Example Author</b>"},
                    "LicenseShortName": {"value": license_name},
                    "LicenseUrl": {
                        "value": "https://creativecommons.org/licenses/by/4.0/"
                    },
                    "Credit": {"value": "Example Author / Wikimedia Commons"},
                },
            }
        ],
    }


class WikimediaInventoryTest(unittest.TestCase):
    def test_collects_eligible_candidate_and_strips_metadata_html(self) -> None:
        payload = {"batchcomplete": True, "query": {"pages": [page(1)]}}
        with patch.object(
            wikimedia_inventory, "urlopen", return_value=FakeResponse(payload)
        ):
            result = wikimedia_inventory.collect_inventory(["crop top"], page_size=5)
        self.assertEqual(1, len(result.candidates))
        item = result.candidates[0]
        self.assertEqual("wikimedia:1", item["candidate_id"])
        self.assertEqual("Example Author", item["creator"])
        self.assertEqual("by", item["license_id"])
        self.assertEqual("needs_review", item["review_status"])

    def test_follows_bounded_continuation_and_deduplicates(self) -> None:
        payloads = [
            {
                "continue": {"gsroffset": 5, "continue": "gsroffset||"},
                "query": {"pages": [page(1)]},
            },
            {"batchcomplete": True, "query": {"pages": [page(1), page(2)]}},
        ]
        with patch.object(
            wikimedia_inventory,
            "urlopen",
            side_effect=[FakeResponse(payload) for payload in payloads],
        ):
            result = wikimedia_inventory.collect_inventory(["dress"], max_pages=2)
        self.assertEqual(2, len(result.candidates))
        self.assertEqual(1, result.skipped_duplicates)
        self.assertEqual(2, result.requested_pages)

    def test_skips_unsupported_license_and_media(self) -> None:
        unsupported = page(1, "NonCommercial")
        video = page(2)
        video["imageinfo"][0]["mime"] = "video/webm"
        payload = {"batchcomplete": True, "query": {"pages": [unsupported, video]}}
        with patch.object(
            wikimedia_inventory, "urlopen", return_value=FakeResponse(payload)
        ):
            result = wikimedia_inventory.collect_inventory(["fashion"])
        self.assertEqual(0, len(result.candidates))
        self.assertEqual(2, result.skipped_ineligible)

    def test_enforces_query_and_pagination_limits(self) -> None:
        with self.assertRaises(ValueError):
            wikimedia_inventory.collect_inventory([""])
        with self.assertRaises(ValueError):
            wikimedia_inventory.collect_inventory(["fashion"], page_size=21)
        with self.assertRaises(ValueError):
            wikimedia_inventory.collect_inventory(["fashion"], max_pages=4)

    def test_surfaces_api_error_without_treating_it_as_empty_results(self) -> None:
        with patch.object(
            wikimedia_inventory,
            "urlopen",
            return_value=FakeResponse({"error": {"code": "maxlag"}}),
        ):
            with self.assertRaisesRegex(ValueError, "maxlag"):
                wikimedia_inventory.collect_inventory(["fashion"])


if __name__ == "__main__":
    unittest.main()
