import unittest

from scripts.dag_v3_model.r4_merge_reviewed_pools import merge_reviewed_pools


def pool(sample, group, target):
    return {
        "policy_schema_version": "policy-v1",
        "training_authorized": True,
        "counts": {"doubt_excluded": 1},
        "records": [{"sample_id": sample, "group_key": group, "target": target}],
    }


class R4MergeReviewedPoolsTest(unittest.TestCase):
    def test_merge_preserves_counts_and_unique_groups(self):
        result = merge_reviewed_pools([pool("a", "ga", 0), pool("b", "gb", 1)])
        self.assertEqual({"allow": 1, "filter": 1, "doubt_excluded": 2}, result["counts"])
        self.assertTrue(result["group_contamination"]["passed"])

    def test_merge_rejects_duplicate_groups(self):
        with self.assertRaisesRegex(ValueError, "duplicate groups"):
            merge_reviewed_pools([pool("a", "same", 0), pool("b", "same", 1)])


if __name__ == "__main__":
    unittest.main()
