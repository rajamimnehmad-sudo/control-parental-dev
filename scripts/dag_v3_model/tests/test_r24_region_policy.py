from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from r24_region_policy import dag_region_views, exact_policy_decision  # noqa: E402
from r24_region_evaluate import parse_model_spec  # noqa: E402


class FakeImage:
    def __init__(self, width: int, height: int) -> None:
        self.size = (width, height)


class R24RegionPolicyTest(unittest.TestCase):
    def test_exact_uncertain_policy_respects_full_gate(self) -> None:
        self.assertEqual("allow", exact_policy_decision([0.29, 0.9, 0.9, 0.9, 0.9], "uncertain")["action"])
        self.assertEqual("filter", exact_policy_decision([0.31, 0.46, 0.1, 0.1, 0.1], "uncertain")["action"])

    def test_exact_extreme_policy_requires_strong_or_consensus(self) -> None:
        self.assertEqual("allow", exact_policy_decision([0.2, 0.69, 0.49, 0.1], "extreme")["action"])
        self.assertEqual("filter", exact_policy_decision([0.2, 0.51, 0.5, 0.1], "extreme")["action"])
        self.assertEqual("filter", exact_policy_decision([0.2, 0.71, 0.1, 0.1], "extreme")["action"])

    def test_region_views_match_runtime_counts(self) -> None:
        from PIL import Image

        regular = dag_region_views(Image.new("RGB", (400, 300)))
        extreme = dag_region_views(Image.new("RGB", (900, 300)))
        self.assertEqual(("uncertain", 5), (regular.kind, len(regular.images)))
        self.assertEqual(("extreme", 4), (extreme.kind, len(extreme.images)))
        self.assertTrue(all(image.size == (224, 224) for image in (*regular.images, *extreme.images)))

    def test_model_spec_requires_label_and_path(self) -> None:
        import argparse

        self.assertEqual(("r1", Path("/tmp/r1.onnx")), parse_model_spec("r1=/tmp/r1.onnx"))
        with self.assertRaises(argparse.ArgumentTypeError):
            parse_model_spec("/tmp/r1.onnx")


if __name__ == "__main__":
    unittest.main()
