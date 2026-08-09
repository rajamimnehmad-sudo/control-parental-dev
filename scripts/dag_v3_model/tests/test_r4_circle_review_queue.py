import sys
import tempfile
import unittest
from pathlib import Path

from PIL import Image

SCRIPT_DIR = Path(__file__).resolve().parents[1]
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from r4_circle_review_queue import circle_center_crop, excluded_groups, render_contact_sheet, select_review_rows


class R4CircleReviewQueueTest(unittest.TestCase):
    def test_circle_crop_is_square_and_masks_corners(self):
        source = Image.new("RGB", (240, 120), (240, 20, 20))
        output = circle_center_crop(source, size=64)
        self.assertEqual((64, 64), output.size)
        self.assertEqual((128, 128, 128), output.getpixel((0, 0)))
        self.assertEqual((240, 20, 20), output.getpixel((32, 32)))

    def test_selection_is_balanced_and_prioritizes_disagreements(self):
        rows = [
            {"sample_id": "allow-wrong", "parent_target": 0, "r3_probability": 0.9},
            {"sample_id": "allow-right", "parent_target": 0, "r3_probability": 0.1},
            {"sample_id": "filter-wrong", "parent_target": 1, "r3_probability": 0.05},
            {"sample_id": "filter-right", "parent_target": 1, "r3_probability": 0.95},
        ]
        selected = select_review_rows(rows, per_parent_label=1)
        self.assertEqual({"allow-wrong", "filter-wrong"}, {row["sample_id"] for row in selected})

    def test_excluded_groups_reads_multiple_manifest_shapes(self):
        payloads = [
            {"records": [{"group_key": "a"}, {"source_cluster": "b"}]},
            {"records": [{"group_key": "a"}, {"sample_id": "no-group"}]},
        ]
        self.assertEqual({"a", "b"}, excluded_groups(payloads))

    def test_contact_sheet_renders_numbered_rows(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            image = root / "crop.jpg"
            Image.new("RGB", (128, 128), (20, 30, 40)).save(image)
            output = root / "sheet.png"
            render_contact_sheet([{"image_path": str(image), "review_number": 1}], output)
            with Image.open(output) as rendered:
                self.assertEqual((896, 244), rendered.size)


if __name__ == "__main__":
    unittest.main()
