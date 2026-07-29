from __future__ import annotations

import sys
import json
import tempfile
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(REPO_ROOT / "scripts/dag_v3_model"))

import pilot_single_student  # noqa: E402


class PilotSingleStudentTest(unittest.TestCase):
    def test_upper_region_covers_shoulders_to_lower_torso(self) -> None:
        region = pilot_single_student.upper_region((20, 10, 80, 110), 100, 120)

        self.assertEqual((17.0, 15.0, 83.0, 78.0), region)

    def test_attention_mask_respects_letterbox(self) -> None:
        mask = pilot_single_student.letterboxed_attention_cells(
            [(0, 0, 100, 50)],
            100,
            50,
            input_size=100,
            attention_size=10,
        )

        self.assertEqual(0.0, mask[1][5])
        self.assertEqual(1.0, mask[3][5])
        self.assertEqual(1.0, mask[6][5])
        self.assertEqual(0.0, mask[8][5])

    def test_attention_mask_rejects_invalid_dimensions(self) -> None:
        with self.assertRaises(ValueError):
            pilot_single_student.letterboxed_attention_cells([], 0, 100)

    def test_modern_allow_emphasis_preserves_every_filter_and_class_mass(self) -> None:
        samples = [
            self._sample("old-allow-1", 0),
            self._sample("old-allow-2", 0),
            self._sample("modern-allow", 0),
            self._sample("filter-1", 1),
            self._sample("filter-2", 1),
        ]

        indices = pilot_single_student.modern_allow_sampling_indices(
            samples,
            {"modern-allow"},
            2.0,
            seed=1729,
        )

        self.assertIsNotNone(indices)
        sampled_targets = [samples[index].target for index in indices]
        sampled_ids = [samples[index].sample_id for index in indices]
        self.assertEqual(3, sampled_targets.count(0))
        self.assertEqual(2, sampled_targets.count(1))
        self.assertEqual(2, sampled_ids.count("modern-allow"))
        self.assertEqual(1, sampled_ids.count("filter-1"))
        self.assertEqual(1, sampled_ids.count("filter-2"))

    def test_modern_allow_emphasis_rejects_impossible_weight(self) -> None:
        samples = [
            self._sample("old-allow", 0),
            self._sample("modern-allow", 0),
            self._sample("filter", 1),
        ]

        with self.assertRaises(ValueError):
            pilot_single_student.modern_allow_sampling_indices(
                samples,
                {"modern-allow"},
                3.0,
                seed=1729,
            )

    def test_teacher_probabilities_require_exact_training_ids(self) -> None:
        payload = {
            "schema_version": "dag-v3-siglip2-semantic-teacher-pilot-v1",
            "teacher": {
                "model_id": "teacher",
                "weights_sha256": "abc",
                "declared_license": "apache-2.0",
            },
            "training_soft_targets": {
                "protocol": "out_of_fold",
                "predictions": [
                    {"sample_id": "one", "filter_probability": 0.2},
                    {"sample_id": "two", "filter_probability": 0.8},
                ],
            },
        }
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "teacher.json"
            path.write_text(json.dumps(payload), encoding="utf-8")

            probabilities, provenance = (
                pilot_single_student.load_teacher_probabilities(
                    path,
                    {"one", "two"},
                )
            )

        self.assertEqual({"one": 0.2, "two": 0.8}, probabilities)
        self.assertEqual("out_of_fold", provenance["soft_target_protocol"])

    def test_teacher_probabilities_reject_missing_sample(self) -> None:
        payload = {
            "schema_version": "dag-v3-siglip2-semantic-teacher-pilot-v1",
            "teacher": {},
            "training_soft_targets": {
                "predictions": [
                    {"sample_id": "one", "filter_probability": 0.2},
                ],
            },
        }
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "teacher.json"
            path.write_text(json.dumps(payload), encoding="utf-8")

            with self.assertRaises(ValueError):
                pilot_single_student.load_teacher_probabilities(
                    path,
                    {"one", "two"},
                )

    @staticmethod
    def _sample(sample_id: str, target: int) -> object:
        return type("Sample", (), {"sample_id": sample_id, "target": target})()


if __name__ == "__main__":
    unittest.main()
