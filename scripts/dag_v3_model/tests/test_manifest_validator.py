from __future__ import annotations

import copy
import json
import sys
import tempfile
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(REPO_ROOT / "scripts/dag_v3_model"))

from manifest_validator import load_signal_contract, validate_manifest  # noqa: E402


CONTRACT_PATH = REPO_ROOT / "docs/dag/v3/glosh-visual-signals-v1.json"


class ManifestValidatorTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.contract = load_signal_contract(CONTRACT_PATH)

    def sample(self, suffix: str = "1") -> dict:
        return {
            "sample_id": f"sample-{suffix}",
            "content_sha256": suffix[-1] * 64,
            "perceptual_hash": suffix[-1] * 16,
            "width": 640,
            "height": 480,
            "mime_type": "image/jpeg",
            "dataset_version": "dag-v3-pilot-1",
            "signal_contract_version": self.contract.version,
            "eligibility": "eligible",
            "exclusion_reason": None,
            "source": {
                "kind": "commons",
                "provider": "wikimedia-commons",
                "landing_url": f"https://commons.wikimedia.org/wiki/File:Example-{suffix}.jpg",
                "asset_url": f"https://upload.wikimedia.org/example-{suffix}.jpg",
                "creator": "Example Author",
                "creator_group_id": f"creator-{suffix}",
                "source_cluster_id": f"shoot-{suffix}",
                "retrieved_at": "2026-07-27T20:00:00Z",
            },
            "license": {
                "id": "CC-BY-4.0",
                "version": "4.0",
                "url": "https://creativecommons.org/licenses/by/4.0/",
                "attribution": "Example Author / CC BY 4.0",
                "verified_at": "2026-07-27T20:00:00Z",
                "evidence_url": f"https://commons.wikimedia.org/wiki/File:Example-{suffix}.jpg",
                "commercial_use_allowed": True,
                "derivatives_allowed": True,
                "ml_use_review": "approved",
            },
            "rights": {
                "status": "approved",
                "evidence_url": f"https://commons.wikimedia.org/wiki/File:Example-{suffix}.jpg",
            },
            "split": "unassigned",
            "split_group_id": f"group-{suffix}",
            "prelabels": [],
            "labels": {name: "unreviewed" for name in self.contract.labels},
            "review": {
                "guide_version": "",
                "reviewer_keys": [],
                "adjudicator_key": None,
            },
        }

    def validate(self, records: list[dict]):
        with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", suffix=".jsonl") as handle:
            for record in records:
                handle.write(json.dumps(record) + "\n")
            handle.flush()
            return validate_manifest(Path(handle.name), self.contract)

    def mark_reviewed(self, record: dict, split: str, reviewers: list[str]) -> None:
        record["split"] = split
        record["labels"] = {name: "negative" for name in self.contract.labels}
        record["review"] = {
            "guide_version": "glosh-visual-annotation-v1",
            "reviewer_keys": reviewers,
            "adjudicator_key": None,
        }

    def test_accepts_eligible_unassigned_unreviewed_sample(self) -> None:
        report = self.validate([self.sample()])
        self.assertTrue(report.ok, report.errors)
        self.assertEqual(1, report.records)

    def test_rejects_eligible_sample_without_commercial_license(self) -> None:
        sample = self.sample()
        sample["license"]["commercial_use_allowed"] = False
        sample["license"]["ml_use_review"] = "needs_review"
        report = self.validate([sample])
        self.assertFalse(report.ok)
        self.assertTrue(any("commercial_use_allowed" in error for error in report.errors))
        self.assertTrue(any("ml_use_review" in error for error in report.errors))

    def test_rejects_duplicate_hash_and_split_leakage(self) -> None:
        first = self.sample("1")
        second = self.sample("2")
        self.mark_reviewed(first, "train", ["reviewer-a"])
        self.mark_reviewed(second, "test", ["reviewer-a", "reviewer-b"])
        second["content_sha256"] = first["content_sha256"]
        second["split_group_id"] = first["split_group_id"]
        second["source"]["source_cluster_id"] = first["source"]["source_cluster_id"]
        report = self.validate([first, second])
        self.assertFalse(report.ok)
        self.assertTrue(any("content_sha256" in error and "duplicates" in error for error in report.errors))
        self.assertTrue(any("split_group_id" in error and "crosses splits" in error for error in report.errors))
        self.assertTrue(any("source.source_cluster_id" in error for error in report.errors))

    def test_rejects_missing_unknown_and_invalid_labels(self) -> None:
        sample = self.sample()
        removed = self.contract.labels[0]
        del sample["labels"][removed]
        sample["labels"]["invented_label"] = "positive"
        sample["labels"][self.contract.labels[1]] = "maybe"
        report = self.validate([sample])
        self.assertFalse(report.ok)
        self.assertTrue(any("missing labels" in error for error in report.errors))
        self.assertTrue(any("unknown labels" in error for error in report.errors))
        self.assertTrue(any("invalid annotation state" in error for error in report.errors))

    def test_rejects_private_device_source(self) -> None:
        sample = self.sample()
        sample["source"]["kind"] = "device_capture"
        sample["source"]["landing_url"] = "file:///private/device.jpg"
        sample["source"]["asset_url"] = "data:image/jpeg;base64,AA=="
        report = self.validate([sample])
        self.assertFalse(report.ok)
        self.assertTrue(any("source.kind" in error for error in report.errors))
        self.assertTrue(any("source.landing_url" in error for error in report.errors))
        self.assertTrue(any("source.asset_url" in error for error in report.errors))

    def test_rejects_url_credentials_and_non_pseudonymous_reviewer(self) -> None:
        sample = self.sample()
        self.mark_reviewed(sample, "train", ["reviewer@example.com"])
        sample["source"]["asset_url"] = "https://user:secret@example.org/image.jpg"
        report = self.validate([sample])
        self.assertFalse(report.ok)
        self.assertTrue(any("source.asset_url" in error for error in report.errors))
        self.assertTrue(any("review.reviewer_keys" in error for error in report.errors))

    def test_assigned_test_requires_complete_double_review(self) -> None:
        sample = self.sample()
        sample["split"] = "test"
        sample["review"]["reviewer_keys"] = ["reviewer-a"]
        report = self.validate([sample])
        self.assertFalse(report.ok)
        self.assertTrue(any("unreviewed labels" in error for error in report.errors))
        self.assertTrue(any("require two reviewers" in error for error in report.errors))

    def test_excluded_sample_requires_reason_and_excluded_split(self) -> None:
        sample = copy.deepcopy(self.sample())
        sample["eligibility"] = "excluded"
        sample["split"] = "unassigned"
        sample["exclusion_reason"] = ""
        report = self.validate([sample])
        self.assertFalse(report.ok)
        self.assertTrue(any("excluded samples must use excluded" in error for error in report.errors))
        self.assertTrue(any("exclusion_reason" in error for error in report.errors))


if __name__ == "__main__":
    unittest.main()
