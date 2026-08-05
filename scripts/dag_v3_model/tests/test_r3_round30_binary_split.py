import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from r3_round30_binary_split import build_split


class R3Round30BinarySplitTest(unittest.TestCase):
    def test_additions_are_training_only_and_sealed_stays_closed(self):
        base = {
            "schema_version": "historical",
            "records": [
                {"sample_id": "base-v", "split": "validation", "human_action": "allow", "target": 0, "sha256": "v", "phash64": "0000000000000000", "group_key": "gv", "source_url": "uv"},
                {"sample_id": "base-f", "split": "frozen_test", "human_action": "filter", "target": 1, "sha256": "f", "phash64": "ffffffffffffffff", "group_key": "gf", "source_url": "uf"},
            ],
        }
        additions = [{
            "sample_id": "new-t",
            "split": "train",
            "human_action": "allow",
            "target": 0,
            "sha256": "t",
            "phash64": "aaaaaaaaaaaaaaaa",
            "group_key": "gt",
            "source_url": "ut",
        }]
        result = build_split(base, additions, seed=3005)
        self.assertEqual({"validation": 1, "frozen_test": 1, "train": 1}, result["rows_by_split"])
        self.assertTrue(result["contamination_check"]["passed"])
        self.assertFalse(result["final_sealed_opened"])

    def test_cross_split_exact_hash_is_rejected(self):
        base = {"schema_version": "historical", "records": [{"sample_id": "base", "split": "frozen_test", "human_action": "allow", "target": 0, "sha256": "same", "phash64": "1", "group_key": "g1", "source_url": "u1"}]}
        additions = [{"sample_id": "new", "split": "train", "human_action": "filter", "target": 1, "sha256": "same", "phash64": "2", "group_key": "g2", "source_url": "u2"}]
        with self.assertRaises(ValueError):
            build_split(base, additions, seed=3005)


if __name__ == "__main__":
    unittest.main()
