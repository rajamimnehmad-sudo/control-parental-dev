from __future__ import annotations

import argparse
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from r23_regional_evaluate import parse_model_spec  # noqa: E402


class R23RegionalEvaluateTest(unittest.TestCase):
    def test_model_spec_requires_label_and_path(self) -> None:
        self.assertEqual(parse_model_spec("r23=/tmp/model.onnx"), ("r23", Path("/tmp/model.onnx")))
        with self.assertRaises(argparse.ArgumentTypeError):
            parse_model_spec("/tmp/model.onnx")


if __name__ == "__main__":
    unittest.main()
