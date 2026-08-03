from __future__ import annotations

import unittest
from unittest import mock
from pathlib import Path

from scripts.dag_v3_model.r2_hard_negative_split import _new_records, verify_no_contamination


class HardNegativeSplitTest(unittest.TestCase):
    def test_contamination_check_accepts_grouped_disjoint_records(self) -> None:
        records = [
            {"sample_id": "a", "sha256": "a" * 64, "phash64": "1", "group_key": "g1", "source_url": "u1", "split": "train", "target": 0},
            {"sample_id": "b", "sha256": "b" * 64, "phash64": "2", "group_key": "g2", "source_url": "u2", "split": "validation", "target": 1},
            {"sample_id": "c", "sha256": "c" * 64, "phash64": "3", "group_key": "g3", "source_url": "u3", "split": "frozen_test", "target": 1},
        ]
        report = verify_no_contamination(records)
        self.assertTrue(report["passed"])
        self.assertEqual(report["rows_by_split"], {"train": 1, "validation": 1, "frozen_test": 1})

    def test_new_rows_without_training_authorization_are_rejected(self) -> None:
        with mock.patch(
            "scripts.dag_v3_model.r2_hard_negative_split._read_jsonl",
            return_value=[
                {
                    "sample_id": "new:1",
                    "local_path": "images/1.jpg",
                    "source_cluster_hash": "cluster-1",
                    "sha256": "a" * 64,
                    "phash64": "1",
                    "dhash64": "2",
                    "category": "hard_negative_filter_like",
                    "usage_state": "internal_evaluation_ok",
                    "training_authorized": False,
                    "training_rights_status": "training_rights_uncertain",
                }
            ],
        ), mock.patch(
            "scripts.dag_v3_model.r2_hard_negative_split._read_json",
            return_value={"reviews": {"new:1": {"action": "filter"}}},
        ):
            with self.assertRaisesRegex(ValueError, "authorization"):
                _new_records(Path("/tmp/manifest.jsonl"), Path("/tmp/reviews.json"))

    def test_owner_private_authorization_does_not_claim_clear_rights(self) -> None:
        with mock.patch(
            "scripts.dag_v3_model.r2_hard_negative_split._read_jsonl",
            return_value=[
                {
                    "sample_id": "new:1",
                    "local_path": "images/1.jpg",
                    "source_cluster_hash": "cluster-1",
                    "sha256": "a" * 64,
                    "phash64": "1",
                    "dhash64": "2",
                    "category": "hard_negative_filter_like",
                    "training_authorized": False,
                    "training_rights_status": "training_rights_uncertain",
                }
            ],
        ), mock.patch(
            "scripts.dag_v3_model.r2_hard_negative_split._read_json",
            return_value={"reviews": {"new:1": {"action": "filter"}}},
        ):
            rows = _new_records(
                Path("/tmp/manifest.jsonl"),
                Path("/tmp/reviews.json"),
                owner_authorized_private_experiment=True,
            )
        self.assertEqual(rows[0]["training_authorization"], "owner_authorized_private_experiment")


if __name__ == "__main__":
    unittest.main()
