import hashlib
import tempfile
import unittest
from pathlib import Path

from scripts.dag_v3_model.r4_independent_corpus_plan import build_plan, build_training_payload


def source(root: Path, prefix: str):
    manifest = []
    reviews = {"reviews": {}}
    for target in (0, 1):
        for index in range(6):
            sample_id = f"{prefix}-{target}-{index}"
            path = root / f"{sample_id}.jpg"
            path.write_bytes(sample_id.encode())
            manifest.append(
                {
                    "sample_id": sample_id,
                    "local_path": path.name,
                    "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
                    "phash64": f"{target + 8:x}{index + 8:x}".ljust(16, "0"),
                    "source_cluster": sample_id,
                    "catalog": prefix,
                    "category": "people",
                }
            )
            reviews["reviews"][sample_id] = {"action": "filter" if target else "allow"}
    return manifest, reviews, root


class R4IndependentCorpusPlanTest(unittest.TestCase):
    def test_freezes_reproducible_stratified_holdout(self):
        with tempfile.TemporaryDirectory() as directory:
            item = source(Path(directory), "catalog")
            first = build_plan([item], {"records": []}, {"records": []}, seed=4, holdout_fraction=0.2)
            second = build_plan([item], {"records": []}, {"records": []}, seed=4, holdout_fraction=0.2)
            self.assertEqual(first["records"], second["records"])
            self.assertEqual({"allow": 5, "filter": 5}, first["counts"]["train"])
            self.assertEqual({"allow": 1, "filter": 1}, first["counts"]["independent_holdout"])
            self.assertFalse(first["frozen_test_loaded"])

    def test_excludes_exact_protected_contamination(self):
        with tempfile.TemporaryDirectory() as directory:
            item = source(Path(directory), "catalog")
            protected = {"records": [{"sample_id": "other", "sha256": item[0][0]["sha256"], "split": "train"}]}
            result = build_plan([item], protected, {"records": []}, seed=4, holdout_fraction=0.2)
            self.assertEqual(11, len(result["records"]))
            self.assertEqual(1, result["contamination"]["excluded"]["exact"])

    def test_excludes_doubts(self):
        with tempfile.TemporaryDirectory() as directory:
            manifest, reviews, root = source(Path(directory), "catalog")
            reviews["reviews"][manifest[0]["sample_id"]]["action"] = "doubt"
            result = build_plan([(manifest, reviews, root)], {"records": []}, {"records": []}, seed=4, holdout_fraction=0.2)
            self.assertEqual(1, result["counts"]["doubt_excluded"])
            self.assertEqual(11, len(result["records"]))

    def test_preserves_stale_declared_hash_and_records_real_artifact_hash(self):
        with tempfile.TemporaryDirectory() as directory:
            manifest, reviews, root = source(Path(directory), "catalog")
            manifest[0]["sha256"] = "0" * 64
            result = build_plan([(manifest, reviews, root)], {"records": []}, {"records": []}, seed=4, holdout_fraction=0.2)
            row = next(item for item in result["records"] if item["sample_id"] == manifest[0]["sample_id"])
            self.assertEqual("0" * 64, row["source_declared_sha256"])
            self.assertNotEqual(row["source_declared_sha256"], row["sha256"])

    def test_excludes_identical_agreed_duplicate_once(self):
        with tempfile.TemporaryDirectory() as directory:
            item = source(Path(directory), "catalog")
            result = build_plan([item, item], {"records": []}, {"records": []}, seed=4, holdout_fraction=0.2)
            self.assertEqual(12, len(result["records"]))
            self.assertEqual(12, result["counts"]["exact_duplicates_excluded"])

    def test_training_payload_never_includes_base_frozen_test_or_new_holdout(self):
        plan = {"records": [{"sample_id": "new-train", "split": "train"}, {"sample_id": "new-held", "split": "independent_holdout"}]}
        base = {"records": [{"sample_id": "base", "split": "train"}, {"sample_id": "validation", "split": "validation"}, {"sample_id": "sealed", "split": "frozen_test"}, {"sample_id": "synthetic", "split": "train", "augmentation_variant": "thumb96_q35", "parent_sample_id": "base"}]}
        result = build_training_payload(plan, base)
        self.assertEqual({"base", "validation", "new-train"}, {row["sample_id"] for row in result["records"]})
        self.assertFalse(result["frozen_test_included"])


if __name__ == "__main__":
    unittest.main()
