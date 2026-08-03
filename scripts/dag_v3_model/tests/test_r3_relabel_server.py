from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from scripts.dag_v3_model.r3_relabel_server import normalize_review, read_reviews, write_reviews


class R3RelabelServerTest(unittest.TestCase):
    def setUp(self) -> None:
        self.item = {
            "sample_id": "wikimedia:1",
            "labels": {"shoulder": "positive", "knee": "unknown"},
        }

    def test_complete_review_requires_all_final_states(self) -> None:
        review = normalize_review(
            self.item,
            {
                "sample_id": "wikimedia:1",
                "status": "complete",
                "labels": {"shoulder": "positive", "knee": "negative"},
            },
        )
        self.assertEqual(review["status"], "complete")
        with self.assertRaisesRegex(ValueError, "cannot contain unknown"):
            normalize_review(
                self.item,
                {
                    "sample_id": "wikimedia:1",
                    "status": "complete",
                    "labels": {"shoulder": "positive", "knee": "unknown"},
                },
            )

    def test_review_rejects_missing_or_extra_signal(self) -> None:
        with self.assertRaisesRegex(ValueError, "exactly"):
            normalize_review(
                self.item,
                {
                    "sample_id": "wikimedia:1",
                    "status": "complete",
                    "labels": {"shoulder": "positive"},
                },
            )

    def test_review_file_is_written_atomically(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "reviews.json"
            payload = {"schema_version": "test", "reviews": {"wikimedia:1": {"status": "doubt"}}}
            write_reviews(path, payload)
            self.assertEqual(read_reviews(path), payload)
            self.assertFalse(path.with_suffix(".json.tmp").exists())


if __name__ == "__main__":
    unittest.main()
