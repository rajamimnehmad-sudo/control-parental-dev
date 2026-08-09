import sys
import unittest
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parents[1]
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from r4_consistency_train import build_families, family_losses, gate_evaluation


def metric(*, false_permissions: int, false_filters: int) -> dict:
    return {
        "confusion_matrix": {
            "allow_as_allow": 21 - false_filters,
            "allow_as_filter": false_filters,
            "filter_as_allow": false_permissions,
            "filter_as_filter": 7 - false_permissions,
        },
        "balanced_accuracy": 0.8,
    }


def report(*, original=(1, 2), small=(3, 3), circle=(5, 0), unsafe=8, safe=1) -> dict:
    return {
        "by_variant": {
            "original": metric(false_permissions=original[0], false_filters=original[1]),
            "thumb160_q45": metric(false_permissions=small[0], false_filters=small[1]),
            "circle128_q45": metric(false_permissions=circle[0], false_filters=circle[1]),
        },
        "paired_stability": {
            "unsafe_filter_to_allow_degradations": unsafe,
            "safe_allow_to_filter_degradations": safe,
        },
    }


class R4ConsistencyTrainTest(unittest.TestCase):
    def test_build_families_preserves_one_parent_weight(self):
        records = [
            {"sample_id": "a", "split": "train", "target": 0},
            {"sample_id": "b", "split": "train", "target": 1},
            {"sample_id": "a:small", "parent_sample_id": "a", "split": "train", "target": 0},
            {"sample_id": "a:circle", "parent_sample_id": "a", "split": "train", "target": 0},
        ]
        families = build_families(records)
        self.assertEqual(2, len(families))
        self.assertEqual([3, 1], [len(family) for family in families])
        self.assertEqual([0, 1], [family[0]["target"] for family in families])

    def test_build_families_rejects_label_drift(self):
        records = [
            {"sample_id": "a", "split": "train", "target": 0},
            {"sample_id": "a:small", "parent_sample_id": "a", "split": "train", "target": 1},
        ]
        with self.assertRaisesRegex(ValueError, "label or split mismatch"):
            build_families(records)

    def test_gate_requires_safety_improvement_without_regressions(self):
        baseline = report()
        passing = report(original=(1, 2), small=(2, 3), circle=(4, 0), unsafe=7, safe=1)
        rejected = report(original=(1, 2), small=(2, 3), circle=(4, 1), unsafe=7, safe=1)
        self.assertTrue(gate_evaluation(passing, baseline)["passed"])
        self.assertFalse(gate_evaluation(rejected, baseline)["passed"])
        self.assertFalse(gate_evaluation(rejected, baseline)["checks"]["circle_false_filters_non_regression"])

    def test_loss_averages_views_before_families(self):
        import torch
        from torch.nn import functional as functional

        logits = torch.tensor([2.0, 2.0, 2.0, 0.0])
        batch = {
            "targets": torch.tensor([0.0, 0.0, 0.0, 1.0]),
            "family_indices": torch.tensor([0, 0, 0, 1]),
            "parent_indices": torch.tensor([0, 3]),
            "child_indices": torch.tensor([1, 2]),
            "child_parent_indices": torch.tensor([0, 0]),
            "teacher_probabilities": torch.tensor([0.5, 0.5]),
            "family_count": 2,
        }
        total, components = family_losses(
            logits,
            batch,
            pos_weight=torch.tensor(1.0),
            consistency_weight=0.0,
            anchor_weight=0.0,
        )
        safe_loss = functional.binary_cross_entropy_with_logits(torch.tensor(2.0), torch.tensor(0.0))
        filter_loss = functional.binary_cross_entropy_with_logits(torch.tensor(0.0), torch.tensor(1.0))
        expected = (safe_loss + filter_loss) / 2
        self.assertAlmostEqual(float(expected), float(total), places=6)
        self.assertAlmostEqual(float(expected), components["classification"], places=6)

    def test_loss_honors_family_weights_and_anchor_mask(self):
        import torch
        from torch.nn import functional as functional

        logits = torch.tensor([2.0, 0.0])
        batch = {
            "targets": torch.tensor([0.0, 1.0]),
            "family_indices": torch.tensor([0, 1]),
            "parent_indices": torch.tensor([0, 1]),
            "child_indices": torch.tensor([], dtype=torch.long),
            "child_parent_indices": torch.tensor([], dtype=torch.long),
            "teacher_probabilities": torch.tensor([0.5, 0.5]),
            "family_weights": torch.tensor([1.0, 3.0]),
            "teacher_anchor_mask": torch.tensor([True, False]),
            "family_count": 2,
        }
        total, components = family_losses(
            logits,
            batch,
            pos_weight=torch.tensor(1.0),
            consistency_weight=0.0,
            anchor_weight=1.0,
        )
        first = functional.binary_cross_entropy_with_logits(torch.tensor(2.0), torch.tensor(0.0))
        second = functional.binary_cross_entropy_with_logits(torch.tensor(0.0), torch.tensor(1.0))
        classification = (first + 3 * second) / 4
        anchor = functional.mse_loss(torch.sigmoid(torch.tensor([2.0])), torch.tensor([0.5]))
        self.assertAlmostEqual(float(classification), components["classification"], places=6)
        self.assertAlmostEqual(float(anchor), components["anchor"], places=6)
        self.assertAlmostEqual(float(classification + anchor), float(total), places=6)


if __name__ == "__main__":
    unittest.main()
