import unittest

from scripts.dag_v3_model.r4_reviewed_representation_cv import ordered_probabilities


class R4ReviewedRepresentationCvTest(unittest.TestCase):
    def test_predictions_are_reordered_to_manifest_membership(self):
        records = [{"sample_id": "a"}, {"sample_id": "b"}]
        predictions = [
            {"sample_id": "b", "filter_probability": 0.8},
            {"sample_id": "a", "filter_probability": 0.2},
        ]
        self.assertEqual([0.2, 0.8], ordered_probabilities(records, predictions))

    def test_missing_or_duplicate_predictions_are_rejected(self):
        records = [{"sample_id": "a"}, {"sample_id": "b"}]
        with self.assertRaisesRegex(ValueError, "membership mismatch"):
            ordered_probabilities(records, [{"sample_id": "a", "filter_probability": 0.2}])
        with self.assertRaisesRegex(ValueError, "membership mismatch"):
            ordered_probabilities(
                records,
                [
                    {"sample_id": "a", "filter_probability": 0.2},
                    {"sample_id": "a", "filter_probability": 0.3},
                ],
            )


if __name__ == "__main__":
    unittest.main()
