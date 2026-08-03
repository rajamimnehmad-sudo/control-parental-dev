from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from r22_selective_export import (  # noqa: E402
    FP32_NODE_EXCLUSION,
    android_report_passes,
    quantization_contract,
)


class R22SelectiveExportTest(unittest.TestCase):
    def test_contract_freezes_runtime_operator_set_and_preserves_one_sensitive_node(self) -> None:
        contract = quantization_contract()
        self.assertEqual(
            contract["quantized_operator_types"],
            [
                "Conv",
                "MatMul",
                "Attention",
                "LSTM",
                "Gather",
                "Transpose",
                "EmbedLayerNormalization",
            ],
        )
        self.assertEqual(contract["fp32_node_exclusions"], [FP32_NODE_EXCLUSION])
        self.assertEqual(contract["threshold"], 0.4)

    def test_android_gate_requires_hash_finite_equivalence_and_no_false_permissions(self) -> None:
        report = {
            "candidate": {"sha256": "abc", "hash_matches": True},
            "candidate_evaluation": {
                "samples": 119,
                "finite_outputs": True,
                "decision_mismatches_vs_fp32": 0,
                "false_permissions": 0,
                "session_closed": True,
            },
        }
        self.assertTrue(android_report_passes(report, "abc"))
        report["candidate_evaluation"]["samples"] = 1
        self.assertFalse(android_report_passes(report, "abc"))
        report["candidate_evaluation"]["samples"] = 119
        report["candidate_evaluation"]["decision_mismatches_vs_fp32"] = 1
        self.assertFalse(android_report_passes(report, "abc"))


if __name__ == "__main__":
    unittest.main()
