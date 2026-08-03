import unittest

from r3_android_harness_prepare import sample_metadata


class R3AndroidHarnessPrepareTest(unittest.TestCase):
    def test_metadata_uses_candidate_threshold(self):
        row = {"sample_id": "sample:1", "split": "validation", "human_action": "allow"}
        self.assertEqual("allow", sample_metadata(row, 0.38, 0.381063)["fp32_action"])
        self.assertEqual("filter", sample_metadata(row, 0.39, 0.381063)["fp32_action"])


if __name__ == "__main__":
    unittest.main()
