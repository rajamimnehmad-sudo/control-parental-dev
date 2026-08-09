import tempfile
import unittest
import hashlib
from pathlib import Path

from PIL import Image

from scripts.dag_v3_model.r4_thumbnail_repair_split import VARIANTS, build_thumbnail_split


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


class R4ThumbnailRepairSplitTest(unittest.TestCase):
    def test_variants_keep_parent_label_group_and_split(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            image_path = root / "source.jpg"
            Image.new("RGB", (640, 360), (180, 90, 30)).save(image_path)
            payload = {
                "schema_version": "source-v1",
                "records": [
                    {
                        "sample_id": "sample-1",
                        "image_path": str(image_path),
                        "sha256": sha256(image_path),
                        "source_cluster": "group-1",
                        "split": "train",
                        "target": 1,
                        "human_action": "filter",
                    }
                ],
            }
            result = build_thumbnail_split(payload, root / "generated", seed=7, max_train_groups_per_label=1)
            variants = [row for row in result["records"] if row.get("parent_sample_id") == "sample-1"]
            self.assertEqual(len(VARIANTS), len(variants))
            self.assertTrue(all(row["target"] == 1 for row in variants))
            self.assertTrue(all(row["split"] == "train" for row in variants))
            self.assertTrue(all(row["group_key"] == "group-1" for row in variants))
            self.assertTrue(all(row["parent_manifest_sha256"] == sha256(image_path) for row in variants))
            self.assertTrue(all(row["parent_file_sha256"] == sha256(image_path) for row in variants))
            self.assertTrue(all("phash64" not in row and "dhash64" not in row for row in variants))
            self.assertTrue(result["group_contamination"]["passed"])
            self.assertFalse(result["frozen_test_augmented"])

    def test_validation_is_augmented_but_frozen_test_remains_untouched(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            image_path = root / "source.jpg"
            Image.new("RGB", (320, 240), (20, 40, 60)).save(image_path)
            records = []
            for split in ("validation", "frozen_test"):
                records.append(
                    {
                        "sample_id": split,
                        "image_path": str(image_path),
                        "sha256": sha256(image_path),
                        "source_cluster": split,
                        "split": split,
                        "target": 0,
                        "human_action": "allow",
                    }
                )
            result = build_thumbnail_split(
                {"schema_version": "source-v1", "records": records},
                root / "generated",
                seed=7,
                max_train_groups_per_label=1,
            )
            self.assertEqual(len(VARIANTS), sum("parent_sample_id" in row for row in result["records"]))
            self.assertFalse(any(row.get("parent_sample_id") == "frozen_test" for row in result["records"]))

    def test_missing_source_can_be_resolved_from_archive_before_output_creation(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            archive = root / "archive"
            archive.mkdir()
            archived = archive / "known.jpg"
            Image.new("RGB", (200, 100), (1, 2, 3)).save(archived)
            payload = {
                "schema_version": "source-v1",
                "records": [
                    {
                        "sample_id": "archived",
                        "image_path": str(root / "missing" / "known.jpg"),
                        "sha256": sha256(archived),
                        "source_cluster": "archived-group",
                        "split": "train",
                        "target": 0,
                        "human_action": "allow",
                    }
                ],
            }
            result = build_thumbnail_split(
                payload,
                root / "generated",
                seed=3,
                max_train_groups_per_label=1,
                fallback_roots=[archive],
            )
            self.assertEqual(len(VARIANTS), result["generated_rows"])
            original = next(row for row in result["records"] if row["sample_id"] == "archived")
            self.assertEqual(str(archived.resolve()), original["image_path"])
            self.assertTrue(original["manifest_image_path"].endswith("missing/known.jpg"))

    def test_source_identity_mismatch_fails_before_creating_output(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            source = root / "source.jpg"
            Image.new("RGB", (100, 100), (3, 2, 1)).save(source)
            payload = {
                "records": [
                    {
                        "sample_id": "mismatch",
                        "image_path": str(source),
                        "sha256": "a" * 64,
                        "source_cluster": "mismatch",
                        "split": "validation",
                        "target": 0,
                    }
                ]
            }
            output = root / "generated"
            with self.assertRaisesRegex(ValueError, "source identity mismatch"):
                build_thumbnail_split(payload, output, seed=1, max_train_groups_per_label=1)
            self.assertFalse(output.exists())


if __name__ == "__main__":
    unittest.main()
