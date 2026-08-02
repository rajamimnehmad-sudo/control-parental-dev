from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from r2_hard_negative_repair import _cluster_id, _sanitized_row  # noqa: E402


class HardNegativeRepairTest(unittest.TestCase):
    def test_sanitized_row_has_no_personal_metadata_fields(self) -> None:
        row = _sanitized_row(
            {
                "sample_id": "wikimedia:1",
                "source_url": "https://commons.wikimedia.org/wiki/File:X.jpg",
                "asset_url": "https://upload.wikimedia.org/x.jpg",
                "mime": "image/jpeg",
                "source_cluster": "private creator name",
            },
            category="hard_negative_filter_like",
            digest="a" * 64,
            dhash=1,
            phash=2,
            width=960,
            height=640,
            bytes_count=100,
            local_path="images/a.jpg",
        )
        self.assertNotIn("creator", row)
        self.assertNotIn("title", row)
        self.assertNotIn("comments", row)
        self.assertEqual(row["cluster_id"], _cluster_id("private creator name"))
        self.assertIsNone(row["human_decision"])
        self.assertFalse(row["training_authorized"])


if __name__ == "__main__":
    unittest.main()
