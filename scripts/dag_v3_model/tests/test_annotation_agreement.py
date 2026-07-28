from __future__ import annotations

import copy
import io
import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


REPO_ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(REPO_ROOT / "scripts/dag_v3_model"))

import annotation_agreement  # noqa: E402
from manifest_validator import load_signal_contract  # noqa: E402


CONTRACT_PATH = REPO_ROOT / "docs/dag/v3/glosh-visual-signals-v1.json"


class AnnotationAgreementTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.contract = load_signal_contract(CONTRACT_PATH)
        cls.risk_label = cls.contract.labels[4]

    def sample(
        self,
        suffix: str,
        first_state: str,
        second_state: str,
        *,
        split: str = "test",
        guide: str = "glosh-visual-annotation-v1",
        adjudicated_state: str | None = None,
    ) -> dict:
        first_labels = {name: "negative" for name in self.contract.labels}
        first_labels["person_present"] = "positive"
        second_labels = copy.deepcopy(first_labels)
        first_labels[self.risk_label] = first_state
        second_labels[self.risk_label] = second_state
        final_labels = copy.deepcopy(first_labels)
        disagreements = [
            name
            for name in self.contract.labels
            if first_labels[name] != second_labels[name]
        ]
        adjudication = None
        if disagreements:
            resolved = adjudicated_state or first_state
            final_labels[self.risk_label] = resolved
            adjudication = {
                "adjudicator_key": "reviewer-c",
                "adjudicated_at": "2026-07-27T22:00:00Z",
                "labels": {self.risk_label: resolved},
            }
        return {
            "manifest_schema_version": "dag-v3-dataset-manifest-v1",
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
                "landing_url": f"https://example.org/work/{suffix}",
                "asset_url": f"https://example.org/media/{suffix}.jpg",
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
                "evidence_url": f"https://example.org/work/{suffix}",
                "commercial_use_allowed": True,
                "derivatives_allowed": True,
                "ml_use_review": "approved",
            },
            "rights": {
                "status": "approved",
                "evidence_url": f"https://example.org/work/{suffix}",
            },
            "split": split,
            "split_group_id": f"group-{suffix}",
            "prelabels": [],
            "labels": final_labels,
            "review": {
                "guide_version": guide,
                "annotations": [
                    {
                        "reviewer_key": "reviewer-a",
                        "reviewed_at": "2026-07-27T21:00:00Z",
                        "labels": first_labels,
                    },
                    {
                        "reviewer_key": "reviewer-b",
                        "reviewed_at": "2026-07-27T21:01:00Z",
                        "labels": second_labels,
                    },
                ],
                "adjudication": adjudication,
            },
        }

    def load_dataset(self, records: list[dict]):
        with tempfile.NamedTemporaryFile(
            mode="w", encoding="utf-8", suffix=".jsonl"
        ) as handle:
            for record in records:
                handle.write(json.dumps(record) + "\n")
            handle.flush()
            return annotation_agreement.load_agreement_dataset(
                Path(handle.name), self.contract
            )

    def test_perfect_varied_agreement_has_kappa_one(self) -> None:
        dataset = self.load_dataset(
            [
                self.sample("1", "positive", "positive"),
                self.sample("2", "negative", "negative"),
            ]
        )
        report = annotation_agreement.build_agreement_report(dataset, self.contract)
        metrics = report["overall"]["labels"][self.risk_label]

        self.assertEqual(1.0, metrics["exact_agreement_rate"])
        self.assertEqual(1.0, metrics["cohen_kappa"])
        self.assertEqual(1.0, metrics["binary_known_agreement_rate"])
        self.assertEqual(0, report["overall"]["samples_with_any_disagreement"])

    def test_disagreement_and_adjudication_are_reported(self) -> None:
        dataset = self.load_dataset(
            [self.sample("1", "positive", "negative", adjudicated_state="unknown")]
        )
        report = annotation_agreement.build_agreement_report(dataset, self.contract)
        metrics = report["overall"]["labels"][self.risk_label]

        self.assertEqual(0.0, metrics["exact_agreement_rate"])
        self.assertEqual(1, metrics["positive_negative_disagreement_count"])
        self.assertEqual(1, metrics["adjudicated_count"])
        self.assertEqual(
            {"negative|positive": 1},
            metrics["state_pair_counts_unordered"],
        )
        self.assertEqual(
            ["sample-1"],
            metrics["disagreement_cases"]["sample_ids"],
        )
        self.assertEqual(1, report["overall"]["samples_with_any_disagreement"])

    def test_unknown_disagreement_is_masked_from_binary_known_metric(self) -> None:
        dataset = self.load_dataset(
            [self.sample("1", "unknown", "negative", adjudicated_state="unknown")]
        )
        report = annotation_agreement.build_agreement_report(dataset, self.contract)
        metrics = report["overall"]["labels"][self.risk_label]

        self.assertEqual(0, metrics["binary_known_support"])
        self.assertIsNone(metrics["binary_known_agreement_rate"])
        self.assertEqual(1, metrics["uncertainty_disagreement_count"])

    def test_groups_reports_by_split_and_guide(self) -> None:
        dataset = self.load_dataset(
            [
                self.sample(
                    "1",
                    "positive",
                    "positive",
                    split="validation",
                    guide="guide-v1",
                ),
                self.sample(
                    "2",
                    "negative",
                    "negative",
                    split="test",
                    guide="guide-v2",
                ),
            ]
        )
        report = annotation_agreement.build_agreement_report(dataset, self.contract)

        self.assertEqual({"test", "validation"}, set(report["by_split"]))
        self.assertEqual({"guide-v1", "guide-v2"}, set(report["by_guide_version"]))
        self.assertEqual(1, report["by_split"]["test"]["double_reviewed_samples"])

    def test_invalid_manifest_is_rejected_before_measurement(self) -> None:
        sample = self.sample("1", "positive", "positive")
        sample["review"]["annotations"][1]["reviewer_key"] = "reviewer-a"

        with self.assertRaises(annotation_agreement.AgreementInputError) as raised:
            self.load_dataset([sample])
        self.assertIn("reviewer keys must be distinct", str(raised.exception))

    def test_main_writes_only_report_to_stdout(self) -> None:
        sample = self.sample("1", "negative", "negative")
        with tempfile.NamedTemporaryFile(
            mode="w", encoding="utf-8", suffix=".jsonl"
        ) as handle:
            handle.write(json.dumps(sample) + "\n")
            handle.flush()
            stdout = io.StringIO()
            stderr = io.StringIO()
            with patch.object(sys, "stdout", stdout), patch.object(
                sys, "stderr", stderr
            ):
                exit_code = annotation_agreement.main([handle.name])

        self.assertEqual(0, exit_code)
        self.assertTrue(json.loads(stdout.getvalue())["ok"])
        self.assertEqual("", stderr.getvalue())


if __name__ == "__main__":
    unittest.main()
