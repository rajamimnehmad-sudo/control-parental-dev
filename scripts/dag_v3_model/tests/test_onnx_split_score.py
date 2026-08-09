import unittest
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parents[1]
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from onnx_split_score import _augmentation_report


class OnnxSplitScoreTest(unittest.TestCase):
    def test_augmentation_report_counts_paired_unsafe_degradation(self):
        records = [
            {"sample_id": "safe", "target": 0},
            {"sample_id": "unsafe", "target": 1},
            {
                "sample_id": "safe:small",
                "parent_sample_id": "safe",
                "augmentation_variant": "small",
                "target": 0,
            },
            {
                "sample_id": "unsafe:small",
                "parent_sample_id": "unsafe",
                "augmentation_variant": "small",
                "target": 1,
            },
        ]
        for row in records:
            row["split"] = "validation"
            row["human_action"] = "filter" if row["target"] else "allow"
        predictions = {
            "safe": {"predicted_action": "allow", "filter_probability": 0.1},
            "unsafe": {"predicted_action": "filter", "filter_probability": 0.9},
            "safe:small": {"predicted_action": "allow", "filter_probability": 0.2},
            "unsafe:small": {"predicted_action": "allow", "filter_probability": 0.3},
        }

        report = _augmentation_report(records, predictions, 0.4)

        self.assertEqual(1, report["by_variant"]["small"]["false_permissions"]["count"])
        self.assertEqual(2, report["paired_stability"]["pairs"])
        self.assertEqual(1, report["paired_stability"]["decision_flips"])
        self.assertEqual(1, report["paired_stability"]["unsafe_filter_to_allow_degradations"])


if __name__ == "__main__":
    unittest.main()
