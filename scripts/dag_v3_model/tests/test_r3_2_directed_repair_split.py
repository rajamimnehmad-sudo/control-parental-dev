from __future__ import annotations

import unittest

from scripts.dag_v3_model.r3_2_directed_repair_split import (
    _phash_distance,
    _select_one_per_cluster,
)


class R32DirectedRepairSplitTest(unittest.TestCase):
    def test_phash_distance_is_bitwise(self) -> None:
        self.assertEqual(0, _phash_distance("ff", "ff"))
        self.assertEqual(1, _phash_distance("ff", "fe"))

    def test_selection_keeps_one_hard_case_per_cluster(self) -> None:
        rows = [
            {"group_key": "series-a", "human_action": "allow", "selection_probability": 0.2, "sample_id": "a1"},
            {"group_key": "series-a", "human_action": "allow", "selection_probability": 0.8, "sample_id": "a2"},
            {"group_key": "series-b", "human_action": "filter", "selection_probability": 0.1, "sample_id": "b1"},
            {"group_key": "series-b", "human_action": "filter", "selection_probability": 0.7, "sample_id": "b2"},
        ]
        selected = _select_one_per_cluster(rows)
        self.assertEqual({"a2", "b1"}, {row["sample_id"] for row in selected})

    def test_mixed_action_cluster_is_rejected(self) -> None:
        with self.assertRaises(ValueError):
            _select_one_per_cluster(
                [
                    {"group_key": "series-a", "human_action": "allow", "selection_probability": 0.2, "sample_id": "a1"},
                    {"group_key": "series-a", "human_action": "filter", "selection_probability": 0.8, "sample_id": "a2"},
                ]
            )


if __name__ == "__main__":
    unittest.main()
