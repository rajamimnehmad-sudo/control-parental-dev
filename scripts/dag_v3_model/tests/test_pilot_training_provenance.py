from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(REPO_ROOT / "scripts/dag_v3_model"))

import pilot_training_provenance as provenance  # noqa: E402


class PilotTrainingProvenanceTest(unittest.TestCase):
    def _set(
        self,
        root: Path,
        name: str,
        sample_id: str,
        content: bytes,
        action: str,
    ) -> tuple[list[str], str]:
        public_dir = root / name / "public"
        image_dir = public_dir / "review-images"
        image_dir.mkdir(parents=True)
        image_path = image_dir / f"{name}.jpg"
        image_path.write_bytes(content)
        review = root / name / "review.json"
        items = root / name / "items.json"
        review.write_text(
            json.dumps(
                {
                    "schema_version": "dag-v3-human-policy-review-v1",
                    "completed": True,
                    "reviewed": 1,
                    "total": 1,
                    "rows": [
                        {
                            "sample_id": sample_id,
                            "human_decision": {"action": action, "reasons": []},
                        }
                    ],
                }
            ),
            encoding="utf-8",
        )
        items.write_text(
            json.dumps(
                {
                    "schemaVersion": "dag-v3-blind-policy-review-items-v1",
                    "total": 1,
                    "items": [
                        {
                            "id": sample_id,
                            "image": f"/review-images/{name}.jpg",
                            "source": "owned",
                        }
                    ],
                }
            ),
            encoding="utf-8",
        )
        return [str(review), str(items), str(public_dir)], provenance._sha256(image_path)

    def _manifest(self, root: Path, records: list[dict]) -> Path:
        path = root / "downloads.jsonl"
        path.write_text(
            "".join(json.dumps(record) + "\n" for record in records),
            encoding="utf-8",
        )
        return path

    @staticmethod
    def _authorized(digest: str) -> dict:
        return {
            "status": "downloaded",
            "sha256": digest,
            "training_authorized": True,
            "review_status": "approved_for_training",
            "license": {
                "ml_use_review": "approved",
                "commercial_use_allowed": True,
                "derivatives_allowed": True,
            },
            "rights": {"status": "approved"},
        }

    def test_ready_when_provenance_rights_and_splits_are_complete(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            train, train_hash = self._set(root, "train", "sample-train", b"train", "allow")
            validation, validation_hash = self._set(
                root, "validation", "sample-validation", b"validation", "block"
            )
            manifest = self._manifest(
                root,
                [self._authorized(train_hash), self._authorized(validation_hash)],
            )

            report = provenance.audit(
                {"train": [train], "validation": [validation], "holdout": []},
                [manifest],
            )

            self.assertTrue(report["gate"]["ready_for_retraining"])
            self.assertEqual(1, report["roles"]["train"]["provenance_matched"])

    def test_open_license_style_metadata_does_not_imply_authorization(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            train, train_hash = self._set(root, "train", "sample-train", b"train", "allow")
            validation, _ = self._set(
                root, "validation", "sample-validation", b"validation", "block"
            )
            manifest = self._manifest(
                root,
                [
                    {
                        "status": "downloaded",
                        "sha256": train_hash,
                        "license_id": "cc0",
                        "review_status": "needs_license_and_visual_review",
                    }
                ],
            )

            report = provenance.audit(
                {"train": [train], "validation": [validation], "holdout": []},
                [manifest],
            )

            self.assertFalse(report["gate"]["ready_for_retraining"])
            self.assertIn(
                "explicit_training_rights_incomplete", report["gate"]["blockers"]
            )

    def test_detects_content_overlap_across_roles(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            train, digest = self._set(root, "train", "sample-train", b"same", "allow")
            validation, _ = self._set(
                root, "validation", "sample-validation", b"same", "block"
            )
            manifest = self._manifest(root, [self._authorized(digest)])

            report = provenance.audit(
                {"train": [train], "validation": [validation], "holdout": []},
                [manifest],
            )

            self.assertFalse(report["gate"]["ready_for_retraining"])
            self.assertIn("cross_split_overlap", report["gate"]["blockers"])


if __name__ == "__main__":
    unittest.main()
