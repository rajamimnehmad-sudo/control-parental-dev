from __future__ import annotations

import io
import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


REPO_ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(REPO_ROOT / "scripts/dag_v3_model"))

import evaluation_harness  # noqa: E402
from manifest_validator import load_signal_contract  # noqa: E402


CONTRACT_PATH = REPO_ROOT / "docs/dag/v3/glosh-visual-signals-v1.json"


class EvaluationHarnessTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.contract = load_signal_contract(CONTRACT_PATH)
        cls.label_indices = {
            name: index for index, name in enumerate(cls.contract.labels)
        }
        cls.risk_label = "adult_or_explicit"

    def policy_payload(self) -> dict:
        return {
            "schema_version": evaluation_harness.POLICY_SCHEMA_VERSION,
            "signal_contract_version": self.contract.version,
            "policy_version": "pilot-unapproved-1",
            "labels": {
                name: {
                    "uncertain_threshold": 0.4,
                    "positive_threshold": 0.8,
                    "policy_action": (
                        "block" if name == self.risk_label else "observe"
                    ),
                }
                for name in self.contract.labels
            },
        }

    def load_policy(self, payload: dict | None = None):
        with tempfile.NamedTemporaryFile(
            mode="w", encoding="utf-8", suffix=".json"
        ) as handle:
            json.dump(payload or self.policy_payload(), handle)
            handle.flush()
            return evaluation_harness.load_evaluation_policy(
                Path(handle.name), self.contract
            )

    def record(
        self,
        sample_id: str,
        truth: str,
        float_score: float,
        int8_score: float | None = None,
        *,
        masked_label: str | None = None,
        slice_value: str = "owned",
    ) -> dict:
        labels = {name: "negative" for name in self.contract.labels}
        labels[self.risk_label] = truth
        if masked_label is not None:
            labels[masked_label] = "unknown"
        float_scores = [0.0] * len(self.contract.labels)
        float_scores[self.label_indices[self.risk_label]] = float_score
        predictions = {"float": float_scores}
        if int8_score is not None:
            int8_scores = list(float_scores)
            int8_scores[self.label_indices[self.risk_label]] = int8_score
            predictions["int8"] = int8_scores
        return {
            "schema_version": evaluation_harness.PREDICTION_SCHEMA_VERSION,
            "signal_contract_version": self.contract.version,
            "sample_id": sample_id,
            "labels": labels,
            "predictions": predictions,
            "slices": {"source": slice_value},
        }

    def load_predictions(self, records: list[dict]):
        with tempfile.NamedTemporaryFile(
            mode="w", encoding="utf-8", suffix=".jsonl"
        ) as handle:
            for record in records:
                handle.write(json.dumps(record) + "\n")
            handle.flush()
            return evaluation_harness.load_prediction_set(
                Path(handle.name), self.contract
            )

    def test_perfect_predictions_and_masked_states(self) -> None:
        masked = "female_underwear_or_swimwear"
        predictions = self.load_predictions(
            [
                self.record("positive", "positive", 0.95, masked_label=masked),
                self.record("negative", "negative", 0.05),
            ]
        )
        report = evaluation_harness.build_evaluation_report(
            predictions, self.contract, self.load_policy(), curve_points=3
        )

        metrics = report["overall"]["variants"]["float"]["labels"][self.risk_label]
        self.assertEqual(1.0, metrics["precision"])
        self.assertEqual(1.0, metrics["recall"])
        self.assertEqual(1.0, metrics["pr_auc"])
        self.assertEqual(
            0,
            metrics["positive_truth_threshold_bands"]["below_uncertain"],
        )
        self.assertEqual(
            1,
            report["overall"]["variants"]["float"]["labels"][masked]["masked"][
                "unknown"
            ],
        )
        policy = report["overall"]["variants"]["float"]["policy"]
        self.assertEqual(0, policy["false_allow_count"])
        self.assertEqual(1, policy["evaluable_matrix"]["block"]["block"])
        self.assertEqual(1, policy["evaluable_matrix"]["allow"]["allow"])

    def test_uncertain_stays_hidden_and_below_low_threshold_is_false_allow(self) -> None:
        predictions = self.load_predictions(
            [
                self.record("hidden", "positive", 0.5),
                self.record("leak", "positive", 0.2),
            ]
        )
        report = evaluation_harness.build_evaluation_report(
            predictions, self.contract, self.load_policy(), curve_points=3
        )
        policy = report["overall"]["variants"]["float"]["policy"]
        bands = report["overall"]["variants"]["float"]["labels"][
            self.risk_label
        ]["positive_truth_threshold_bands"]

        self.assertEqual(1, policy["evaluable_matrix"]["block"]["uncertain"])
        self.assertEqual(1, policy["false_allow_count"])
        self.assertEqual(0.5, policy["false_allow_rate"])
        self.assertEqual(["leak"], policy["false_allow_cases"]["sample_ids"])
        self.assertEqual(1, bands["uncertain_to_positive"])
        self.assertEqual(1, bands["below_uncertain"])

    def test_unknown_blocking_truth_is_unresolved_but_not_applicable_is_allow(self) -> None:
        unknown = self.record("unknown", "unknown", 0.0)
        not_applicable = self.record("not-applicable", "not_applicable", 0.0)
        predictions = self.load_predictions([unknown, not_applicable])

        report = evaluation_harness.build_evaluation_report(
            predictions, self.contract, self.load_policy(), curve_points=3
        )
        truth = report["overall"]["variants"]["float"]["policy"]["truth_counts"]

        self.assertEqual(1, truth["unresolved"])
        self.assertEqual(1, truth["allow"])
        self.assertEqual(0, truth["block"])

    def test_quantized_comparison_detects_new_false_allow(self) -> None:
        predictions = self.load_predictions(
            [
                self.record("regression", "positive", 0.5, 0.2),
                self.record("stable", "negative", 0.1, 0.1),
            ]
        )
        report = evaluation_harness.build_evaluation_report(
            predictions,
            self.contract,
            self.load_policy(),
            curve_points=3,
            reference_variant="float",
            candidate_variant="int8",
        )
        comparison = report["overall"]["comparison"]
        delta = comparison["label_probability_delta"][self.risk_label]

        self.assertEqual(1, comparison["policy_disagreement_count"])
        self.assertEqual(1, comparison["candidate_more_permissive_count"])
        self.assertEqual(1, comparison["candidate_new_false_allow_count"])
        self.assertEqual(
            ["regression"],
            comparison["candidate_new_false_allow_cases"]["sample_ids"],
        )
        self.assertEqual(1, delta["uncertain_threshold_crossings"])
        self.assertEqual(0, delta["positive_threshold_crossings"])

    def test_slice_reports_are_separate_and_include_missing_value(self) -> None:
        first = self.record("owned", "positive", 0.9, slice_value="owned")
        second = self.record("missing", "negative", 0.1)
        second["slices"] = {}
        predictions = self.load_predictions([first, second])

        report = evaluation_harness.build_evaluation_report(
            predictions,
            self.contract,
            self.load_policy(),
            curve_points=3,
            slice_keys=["source"],
        )

        self.assertEqual(
            {"__missing__", "owned"},
            set(report["slices"]["source"]),
        )
        self.assertEqual(1, report["slices"]["source"]["owned"]["records"])

    def test_rejects_duplicate_ids_and_invalid_probability(self) -> None:
        first = self.record("duplicate", "negative", 0.1)
        second = self.record("duplicate", "negative", 1.2)
        with self.assertRaises(evaluation_harness.EvaluationInputError) as raised:
            self.load_predictions([first, second])

        self.assertIn("invalid probabilities", str(raised.exception))

        second = self.record("duplicate", "negative", 0.1)
        with self.assertRaises(evaluation_harness.EvaluationInputError) as raised:
            self.load_predictions([first, second])
        self.assertIn("duplicates an earlier record", str(raised.exception))

    def test_rejects_incomplete_policy_and_inverted_thresholds(self) -> None:
        payload = self.policy_payload()
        del payload["labels"][self.risk_label]
        with self.assertRaises(evaluation_harness.EvaluationInputError) as raised:
            self.load_policy(payload)
        self.assertIn("missing contract entries", str(raised.exception))

        payload = self.policy_payload()
        payload["labels"][self.risk_label]["uncertain_threshold"] = 0.9
        payload["labels"][self.risk_label]["positive_threshold"] = 0.8
        with self.assertRaises(evaluation_harness.EvaluationInputError) as raised:
            self.load_policy(payload)
        self.assertIn("must not exceed", str(raised.exception))

    def test_metric_calculations_are_deterministic(self) -> None:
        predictions = self.load_predictions(
            [
                self.record("positive-high", "positive", 0.9),
                self.record("positive-low", "positive", 0.3),
                self.record("negative-mid", "negative", 0.6),
                self.record("negative-low", "negative", 0.1),
            ]
        )
        report = evaluation_harness.build_evaluation_report(
            predictions,
            self.contract,
            self.load_policy(),
            ece_bins=2,
            curve_points=3,
        )
        metrics = report["overall"]["variants"]["float"]["labels"][self.risk_label]

        self.assertAlmostEqual(0.833333333333, metrics["pr_auc"])
        self.assertAlmostEqual(0.2175, metrics["brier_score"])
        self.assertAlmostEqual(0.275, metrics["expected_calibration_error"])
        self.assertEqual(3, len(metrics["precision_recall_curve"]))

    def test_main_keeps_report_and_errors_on_separate_streams(self) -> None:
        predictions = [self.record("one", "negative", 0.1)]
        with (
            tempfile.NamedTemporaryFile(
                mode="w", encoding="utf-8", suffix=".jsonl"
            ) as prediction_file,
            tempfile.NamedTemporaryFile(
                mode="w", encoding="utf-8", suffix=".json"
            ) as policy_file,
        ):
            prediction_file.write(json.dumps(predictions[0]) + "\n")
            prediction_file.flush()
            json.dump(self.policy_payload(), policy_file)
            policy_file.flush()
            stdout = io.StringIO()
            stderr = io.StringIO()
            with patch.object(sys, "stdout", stdout), patch.object(
                sys, "stderr", stderr
            ):
                exit_code = evaluation_harness.main(
                    [
                        prediction_file.name,
                        policy_file.name,
                        "--curve-points",
                        "3",
                    ]
                )

        self.assertEqual(0, exit_code)
        self.assertTrue(json.loads(stdout.getvalue())["ok"])
        self.assertEqual("", stderr.getvalue())


if __name__ == "__main__":
    unittest.main()
