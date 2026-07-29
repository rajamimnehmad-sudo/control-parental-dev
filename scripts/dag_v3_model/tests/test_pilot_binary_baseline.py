from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(REPO_ROOT / "scripts/dag_v3_model"))

import pilot_binary_baseline  # noqa: E402


class PilotBinaryBaselineTest(unittest.TestCase):
    def write_inputs(self, root: Path, action: str = "allow") -> tuple[Path, Path, Path]:
        public_dir = root / "public"
        image_dir = public_dir / "review-images"
        image_dir.mkdir(parents=True)
        (image_dir / "001.jpg").write_bytes(b"image")
        review = {
            "schema_version": pilot_binary_baseline.REVIEW_SCHEMA_VERSION,
            "completed": True,
            "reviewed": 1,
            "total": 1,
            "rows": [
                {
                    "sample_id": "pilot:one",
                    "human_decision": {"action": action, "reasons": []},
                }
            ],
        }
        items = {
            "schemaVersion": pilot_binary_baseline.ITEMS_SCHEMA_VERSION,
            "total": 1,
            "items": [
                {
                    "id": "pilot:one",
                    "image": "/review-images/001.jpg",
                    "source": "test",
                }
            ],
        }
        review_path = root / "review.json"
        items_path = public_dir / "items.json"
        review_path.write_text(json.dumps(review), encoding="utf-8")
        items_path.write_text(json.dumps(items), encoding="utf-8")
        return review_path, items_path, public_dir

    def test_combines_blur_and_block_into_filter_target(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            review_path, items_path, public_dir = self.write_inputs(root)
            review = json.loads(review_path.read_text(encoding="utf-8"))
            review["reviewed"] = 3
            review["total"] = 3
            review["rows"] = [
                {
                    "sample_id": f"pilot:{index}",
                    "human_decision": {"action": action, "reasons": []},
                }
                for index, action in enumerate(("allow", "blur", "block"), start=1)
            ]
            items = json.loads(items_path.read_text(encoding="utf-8"))
            items["total"] = 3
            items["items"] = []
            for index in range(1, 4):
                image_name = f"{index:03}.jpg"
                (public_dir / "review-images" / image_name).write_bytes(b"image")
                items["items"].append(
                    {
                        "id": f"pilot:{index}",
                        "image": f"/review-images/{image_name}",
                        "source": "test",
                    }
                )
            review_path.write_text(json.dumps(review), encoding="utf-8")
            items_path.write_text(json.dumps(items), encoding="utf-8")

            samples = pilot_binary_baseline.load_samples(
                review_path,
                items_path,
                public_dir,
            )

            self.assertEqual([0, 1, 1], [sample.target for sample in samples])

    def test_rejects_incomplete_or_unsure_review(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            review_path, items_path, public_dir = self.write_inputs(root, action="unsure")
            review = json.loads(review_path.read_text(encoding="utf-8"))
            review["completed"] = False
            review_path.write_text(json.dumps(review), encoding="utf-8")

            with self.assertRaises(pilot_binary_baseline.BaselineInputError):
                pilot_binary_baseline.load_samples(review_path, items_path, public_dir)

    def test_rejects_image_path_escape(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            review_path, items_path, public_dir = self.write_inputs(root)
            items = json.loads(items_path.read_text(encoding="utf-8"))
            items["items"][0]["image"] = "/review-images/../secret.jpg"
            items_path.write_text(json.dumps(items), encoding="utf-8")

            with self.assertRaises(pilot_binary_baseline.BaselineInputError):
                pilot_binary_baseline.load_samples(review_path, items_path, public_dir)

    def test_can_skip_a_human_excluded_sample(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            review_path, items_path, public_dir = self.write_inputs(root)
            review = json.loads(review_path.read_text(encoding="utf-8"))
            review["reviewed"] = 3
            review["total"] = 3
            review["rows"] = [
                {
                    "sample_id": f"pilot:{index}",
                    "human_decision": {"action": action, "reasons": []},
                }
                for index, action in enumerate(("allow", "blur", "exclude"), start=1)
            ]
            items = json.loads(items_path.read_text(encoding="utf-8"))
            items["total"] = 3
            items["items"] = []
            for index in range(1, 4):
                image_name = f"{index:03}.jpg"
                (public_dir / "review-images" / image_name).write_bytes(b"image")
                items["items"].append(
                    {
                        "id": f"pilot:{index}",
                        "image": f"/review-images/{image_name}",
                        "source": "test",
                    }
                )
            review_path.write_text(json.dumps(review), encoding="utf-8")
            items_path.write_text(json.dumps(items), encoding="utf-8")

            samples = pilot_binary_baseline.load_samples(
                review_path,
                items_path,
                public_dir,
                skip_excluded=True,
            )

            self.assertEqual(["pilot:1", "pilot:2"], [sample.sample_id for sample in samples])


if __name__ == "__main__":
    unittest.main()
