from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from scripts.dag_v3_model.r3_multisignal_gate import convert_exports, load_policy


ROOT = Path(__file__).resolve().parents[3]
POLICY = ROOT / "scripts/dag_v3_model/r3_multisignal_policy_v1.json"


class R3MultisignalGateTest(unittest.TestCase):
    def _export(self, root: Path, rows: list[dict]) -> Path:
        path = root / "review.json"
        path.write_text(
            json.dumps(
                {
                    "schema_version": "dag-v3-human-policy-review-v1",
                    "reviewer_id": "owner",
                    "rows": rows,
                }
            ),
            encoding="utf-8",
        )
        return path

    @staticmethod
    def _row(sample_id: str, action: str, reasons: list[str]) -> dict:
        return {
            "sample_id": sample_id,
            "human_decision": {"action": action, "reasons": reasons},
        }

    def test_allow_is_full_negative_and_filter_is_partial(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            export = self._export(
                root,
                [
                    self._row("wikimedia:1", "allow", []),
                    self._row("wikimedia:2", "blur", ["shoulder_or_armpit"]),
                ],
            )
            result = convert_exports([export], load_policy(POLICY), min_positive=1, min_negative=1)
            allow, filtered = result["records"]
            self.assertTrue(all(value == "negative" for value in allow["labels"].values()))
            self.assertEqual(filtered["labels"]["shoulder_or_armpit"], "positive")
            self.assertEqual(filtered["labels"]["knee_uncovered"], "unknown")
            self.assertTrue(filtered["training_authorized"])
            self.assertFalse(filtered["publication_reuse_authorized"])

    def test_unmapped_reason_is_preserved_for_review(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            export = self._export(root, [self._row("pilot:abc", "block", ["other"])])
            result = convert_exports([export], load_policy(POLICY))
            self.assertEqual(result["summary"]["unmapped_reasons"], {"other": 1})
            self.assertEqual(
                result["records"][0]["source_resolution"],
                "content_hash_needs_local_resolution",
            )

    def test_conflicting_duplicate_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            first = self._export(root, [self._row("wikimedia:1", "allow", [])])
            second = root / "second.json"
            second.write_text(
                json.dumps(
                    {
                        "reviewer_id": "owner",
                        "rows": [self._row("wikimedia:1", "blur", ["knee_uncovered"])],
                    }
                ),
                encoding="utf-8",
            )
            with self.assertRaisesRegex(ValueError, "conflicting duplicate"):
                convert_exports([first, second], load_policy(POLICY))


if __name__ == "__main__":
    unittest.main()
