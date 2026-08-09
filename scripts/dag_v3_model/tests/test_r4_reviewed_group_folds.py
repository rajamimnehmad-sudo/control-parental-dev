import unittest

from scripts.dag_v3_model.r4_reviewed_group_folds import build_group_folds, build_training_fold


def fixtures():
    reviewed = {"training_authorized": True, "records": []}
    base = {"records": []}
    for label in (0, 1):
        for index in range(6):
            group = f"g-{label}-{index}"
            parent = f"p-{label}-{index}"
            base["records"].append({"sample_id": parent, "split": "train", "group_key": group})
            reviewed["records"].append(
                {"sample_id": f"r-{label}-{index}", "parent_sample_id": parent, "group_key": group, "target": label}
            )
    return reviewed, base


class R4ReviewedGroupFoldsTest(unittest.TestCase):
    def test_folds_are_stratified_unique_and_reproducible(self):
        reviewed, base = fixtures()
        first = build_group_folds(reviewed, base, folds=3, seed=7, policy_sha256="abc")
        second = build_group_folds(reviewed, base, folds=3, seed=7, policy_sha256="abc")
        self.assertEqual(first["records"], second["records"])
        self.assertTrue(first["group_contamination"]["passed"])
        for counts in first["fold_counts"].values():
            self.assertEqual(2, counts["allow"])
            self.assertEqual(2, counts["filter"])

    def test_parent_group_mismatch_is_rejected(self):
        reviewed, base = fixtures()
        reviewed["records"][0]["group_key"] = "wrong"
        with self.assertRaisesRegex(ValueError, "differs"):
            build_group_folds(reviewed, base, folds=3, seed=7, policy_sha256="abc")

    def test_training_fold_excludes_held_groups_and_adds_review_weights(self):
        reviewed, base = fixtures()
        folds = build_group_folds(reviewed, base, folds=3, seed=7, policy_sha256="abc")
        training = build_training_fold(folds, base, held_out_fold=0, reviewed_weight=8.0)
        held = set(training["held_out_groups"])
        train = [row for row in training["records"] if row["split"] == "train"]
        self.assertFalse(held & {row["group_key"] for row in train})
        repairs = [row for row in train if "review_parent_sample_id" in row]
        self.assertEqual(8, len(repairs))
        self.assertTrue(all(row["training_weight"] == 8.0 and not row["teacher_anchor"] for row in repairs))

    def test_gate_requires_twenty_percent_reduction_in_both_error_types(self):
        reviewed, base = fixtures()
        result = build_group_folds(
            reviewed,
            base,
            folds=3,
            seed=7,
            policy_sha256="abc",
            baseline_false_permissions=16,
            baseline_false_filters=11,
        )
        gate = result["acceptance_gate"]
        self.assertEqual(12, gate["oof_false_permissions_max"])
        self.assertEqual(8, gate["oof_false_filters_max"])
        self.assertEqual(3, gate["required_false_filter_reduction"])


if __name__ == "__main__":
    unittest.main()
