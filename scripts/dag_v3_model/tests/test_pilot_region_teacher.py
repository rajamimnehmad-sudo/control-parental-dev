from __future__ import annotations

import sys
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(REPO_ROOT / "scripts/dag_v3_model"))

import pilot_region_teacher  # noqa: E402


class FakeImage:
    def __init__(self, size: tuple[int, int]) -> None:
        self.size = size

    def crop(self, box: tuple[int, int, int, int]) -> "FakeImage":
        left, top, right, bottom = box
        return FakeImage((right - left, bottom - top))

    def copy(self) -> "FakeImage":
        return FakeImage(self.size)


class PilotRegionTeacherTest(unittest.TestCase):
    def test_safe_crop_uses_requested_vertical_region(self) -> None:
        image = FakeImage((100, 200))

        cropped = pilot_region_teacher._safe_crop(
            image,
            (10, 20, 90, 180),
            0.05,
            0.68,
            horizontal_padding=0.0,
        )

        self.assertEqual((80, 100), cropped.size)

    def test_safe_crop_rejects_invalid_fractions(self) -> None:
        image = FakeImage((100, 200))

        with self.assertRaises(ValueError):
            pilot_region_teacher._safe_crop(image, (10, 20, 90, 180), 0.8, 0.2)


if __name__ == "__main__":
    unittest.main()
