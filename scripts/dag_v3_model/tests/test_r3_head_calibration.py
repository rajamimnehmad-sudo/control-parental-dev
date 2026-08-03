import unittest

from r3_head_calibration import safe_threshold


class R3HeadCalibrationTest(unittest.TestCase):
    def test_threshold_minimizes_false_filters_without_false_permissions(self):
        threshold, metrics = safe_threshold([0, 0, 1, 1], [0.2, 0.55, 0.6, 0.9])
        self.assertEqual(0, metrics["false_permissions"]["count"])
        self.assertEqual(0, metrics["false_filters"]["count"])
        self.assertEqual(0.6, threshold)


if __name__ == "__main__":
    unittest.main()
