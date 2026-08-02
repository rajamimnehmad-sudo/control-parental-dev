from __future__ import annotations

import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from r2_candidate_data import build_split, verify_no_contamination


class CandidateDataTest(unittest.TestCase):
    def test_contamination_gate_rejects_shared_group(self) -> None:
        records = [
            {"split": "train", "sample_id": "a", "sha256": "a", "phash64": "1", "group_key": "same", "source_url": "u1"},
            {"split": "frozen_test", "sample_id": "b", "sha256": "b", "phash64": "2", "group_key": "same", "source_url": "u2"},
        ]
        with self.assertRaisesRegex(ValueError, "contamination"):
            verify_no_contamination(records)

    def test_authorized_binary_split_is_grouped(self) -> None:
        base = Path(".codex-tmp/gloshia-balanced-review-20260802")
        output = Path(".codex-tmp/gloshia-r2-candidate-test-split.json")
        payload = build_split(base / "manifest.jsonl", base / "reviews.json", output, 20260802)
        self.assertTrue(payload["contamination_check"]["passed"])
        self.assertEqual({row["split"] for row in payload["records"]}, {"train", "validation", "frozen_test"})
        self.assertEqual(len(payload["records"]), 322)
        self.assertEqual(sum(row["target"] for row in payload["records"]), 43)
        if output.exists():
            output.unlink()
