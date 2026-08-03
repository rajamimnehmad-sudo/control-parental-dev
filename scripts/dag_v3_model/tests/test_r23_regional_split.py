from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from r23_regional_split import assign_regional_holdout, verify_no_contamination  # noqa: E402


class R23RegionalSplitTest(unittest.TestCase):
    def test_holdout_is_deterministic_and_contains_both_actions(self) -> None:
        records = []
        for index in range(24):
            action = "filter" if index < 8 else "allow"
            records.append(
                {
                    "sample_id": f"sample:{index}",
                    "sha256": f"sha:{index}",
                    "phash64": f"phash:{index}",
                    "group_key": f"group:{index}",
                    "source_url": f"https://example.invalid/{index}",
                    "human_action": action,
                    "target": 1 if action == "filter" else 0,
                    "category": "regional",
                    "split": "train",
                }
            )

        assign_regional_holdout(records, 20260803)
        holdout = [row for row in records if row["split"] == "regional_holdout"]

        self.assertEqual(sum(row["human_action"] == "filter" for row in holdout), 2)
        self.assertEqual(sum(row["human_action"] == "allow" for row in holdout), 4)
        self.assertTrue(verify_no_contamination(records)["passed"])


if __name__ == "__main__":
    unittest.main()
