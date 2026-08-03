from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from r2_hard_negative_repair import (  # noqa: E402
    _cluster_id,
    _sanitized_row,
    _validated_plan,
)


class HardNegativeRepairTest(unittest.TestCase):
    def test_r22_query_plan_is_bounded_and_balanced(self) -> None:
        plan = Path(__file__).resolve().parents[1] / "r22_targeted_query_plan.json"
        targets, queries = _validated_plan(plan)
        self.assertEqual(targets, {"hard_negative_filter_like": 25, "hard_negative_allow_like": 25})
        self.assertEqual(set(targets), set(queries))
        self.assertTrue(all(len(bucket) <= 24 for bucket in queries.values()))

        holdout = Path(__file__).resolve().parents[1] / "r22_holdout_query_plan.json"
        holdout_targets, holdout_queries = _validated_plan(holdout)
        self.assertEqual(sum(holdout_targets.values()), 40)
        self.assertEqual(set(holdout_targets), set(holdout_queries))

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
