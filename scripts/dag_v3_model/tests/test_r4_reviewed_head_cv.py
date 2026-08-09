import unittest

from scripts.dag_v3_model.r4_reviewed_head_cv import binary_metrics, fit_repair_head, gate_result


class R4ReviewedHeadCvTest(unittest.TestCase):
    def test_binary_metrics_counts_both_error_directions(self):
        rows = [{"target": 0}, {"target": 0}, {"target": 1}, {"target": 1}]
        metrics = binary_metrics(rows, [0.1, 0.8, 0.2, 0.9])
        self.assertEqual(1, metrics["false_permissions"])
        self.assertEqual(1, metrics["false_filters"])

    def test_head_repair_changes_review_signal_without_large_anchor_drift(self):
        import torch

        anchor = torch.tensor([[-1.0], [1.0]])
        teacher = torch.tensor([0.2, 0.8])
        repair = torch.tensor([[1.0], [1.2]])
        targets = torch.tensor([1.0, 1.0])
        initial_weight = torch.tensor([0.0])
        initial_bias = torch.tensor(0.0)
        weight, bias, _ = fit_repair_head(
            anchor,
            teacher,
            repair,
            targets,
            initial_weight,
            initial_bias,
            repair_weight=0.25,
            delta_weight=0.01,
            max_iterations=50,
        )
        repaired = torch.sigmoid(repair @ weight + bias)
        self.assertGreater(float(repaired.mean()), 0.5)
        anchored = torch.sigmoid(anchor @ weight + bias)
        self.assertLess(float((anchored - teacher).abs().max()), 0.25)

    def test_gate_requires_every_fixed_fold(self):
        acceptance = {
            "oof_false_permissions_max": 4,
            "oof_false_filters_max": 5,
            "fixed_validation_original_false_permissions_max": 1,
            "fixed_validation_original_false_filters_max": 2,
            "fixed_validation_all_false_permissions_max": 12,
            "fixed_validation_all_false_filters_max": 5,
        }
        metric = {"false_permissions": 1, "false_filters": 2}
        passing = [{"fold": 0, "overall": {"false_permissions": 10, "false_filters": 5}, "by_variant": {"original": metric}}]
        failing = [{"fold": 0, "overall": {"false_permissions": 10, "false_filters": 6}, "by_variant": {"original": metric}}]
        self.assertTrue(gate_result({"false_permissions": 4, "false_filters": 5}, passing, acceptance)["passed"])
        self.assertFalse(gate_result({"false_permissions": 4, "false_filters": 5}, failing, acceptance)["passed"])


if __name__ == "__main__":
    unittest.main()
