#!/usr/bin/env python3
"""Score grouped OOF checkpoints from one bounded R4 representation trial.

Training stays in ``r4_consistency_train.py``. This evaluator only opens the
five already-trained fold checkpoints, scores each checkpoint on its unseen
owner-reviewed groups, and applies the acceptance gate frozen in the folds
manifest. It never reads frozen_test or final_sealed.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
import time
from pathlib import Path
from typing import Any

SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from pilot_tinyclip_candidate import MODEL_ID
from r2_candidate_train import TinyDataset, _checkpoint_components, _predict
from r4_reviewed_head_cv import binary_metrics, gate_result, metrics_by_variant


THRESHOLD = 0.4


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def ordered_probabilities(records: list[dict[str, Any]], predictions: list[dict[str, Any]]) -> list[float]:
    indexed = {str(row["sample_id"]): row for row in predictions}
    expected = {str(row["sample_id"]) for row in records}
    if len(indexed) != len(predictions) or set(indexed) != expected:
        missing = sorted(expected - set(indexed))
        extra = sorted(set(indexed) - expected)
        raise ValueError(f"prediction membership mismatch; missing={missing}, extra={extra}")
    return [float(indexed[str(row["sample_id"])]["filter_probability"]) for row in records]


def _official_metric(report: dict[str, Any], split_name: str) -> dict[str, Any]:
    overall = report["metrics"][split_name]["overall"]
    return {
        "samples": overall["samples"],
        "false_permissions": overall["false_permissions"]["count"],
        "false_filters": overall["false_filters"]["count"],
        "accuracy": overall["accuracy"],
    }


def _policy(checkpoint_path: Path, device: Any) -> Any:
    import torch
    from torch import nn
    from transformers import AutoModel

    checkpoint = torch.load(checkpoint_path, map_location="cpu", weights_only=False)
    vision_state, projection_state, coefficient, intercept = _checkpoint_components(checkpoint)
    base = AutoModel.from_pretrained(MODEL_ID, local_files_only=True)
    base.vision_model.load_state_dict(vision_state)
    base.visual_projection.load_state_dict(projection_state)

    class Policy(nn.Module):
        def __init__(self) -> None:
            super().__init__()
            self.vision_model = base.vision_model
            self.visual_projection = base.visual_projection
            self.classifier = nn.Linear(int(coefficient.shape[-1]), 1)
            self.classifier.weight.data.copy_(torch.as_tensor(coefficient, dtype=torch.float32).reshape(1, -1))
            self.classifier.bias.data.copy_(torch.as_tensor(intercept, dtype=torch.float32).reshape(1))

        def forward(self, pixels: Any) -> Any:
            pooled = self.vision_model(pixel_values=pixels).pooler_output
            features = nn.functional.normalize(self.visual_projection(pooled), dim=1)
            return self.classifier(features).squeeze(1)

    return Policy().to(device)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--folds", required=True, type=Path)
    parser.add_argument("--base-split", required=True, type=Path)
    parser.add_argument("--fold-run-dir", required=True, type=Path)
    parser.add_argument("--official-reviewed-report", required=True, type=Path)
    parser.add_argument("--official-validation-report", required=True, type=Path)
    parser.add_argument("--report", required=True, type=Path)
    parser.add_argument("--device", choices=("cpu", "mps", "auto"), default="auto")
    args = parser.parse_args()
    started = time.monotonic()

    import torch
    from torch.utils.data import DataLoader
    from transformers import AutoProcessor

    if args.device == "mps":
        if not torch.backends.mps.is_available():
            raise RuntimeError("MPS requested but unavailable")
        device = torch.device("mps")
    elif args.device == "cpu":
        device = torch.device("cpu")
    else:
        device = torch.device("mps" if torch.backends.mps.is_available() else "cpu")

    folds_payload = json.loads(args.folds.read_text(encoding="utf-8"))
    reviewed = [dict(row) for row in folds_payload["records"]]
    base = json.loads(args.base_split.read_text(encoding="utf-8"))
    validation = [dict(row) for row in base["records"] if row.get("split") == "validation"]
    if not reviewed or not validation:
        raise ValueError("reviewed folds and fixed validation must be non-empty")
    processor = AutoProcessor.from_pretrained(MODEL_ID, local_files_only=True)

    oof_by_id: dict[str, float] = {}
    fold_reports = []
    fold_fixed = []
    artifacts = []
    for fold in range(int(folds_payload["folds"])):
        checkpoint_path = args.fold_run_dir / f"candidate-{fold}.pt"
        training_report_path = args.fold_run_dir / f"train-{fold}.json"
        if not checkpoint_path.is_file() or not training_report_path.is_file():
            raise FileNotFoundError(f"missing fold artifacts for {fold}")
        training_report = json.loads(training_report_path.read_text(encoding="utf-8"))
        if training_report.get("frozen_test_loaded") or training_report.get("final_sealed_opened"):
            raise ValueError(f"fold {fold} opened a sealed evaluation")

        held = [dict(row) for row in reviewed if int(row["cv_fold"]) == fold]
        for row in held:
            path = Path(row["image_path"])
            if not path.is_file():
                raise FileNotFoundError(path)
            row["resolved_image_path"] = str(path)
        model = _policy(checkpoint_path, device)
        loader = DataLoader(TinyDataset(held, processor, augment=False), batch_size=16, shuffle=False, num_workers=0)
        probabilities = _predict(model, loader, device)
        for row, probability in zip(held, probabilities):
            sample_id = str(row["sample_id"])
            if sample_id in oof_by_id:
                raise ValueError(f"duplicate OOF sample: {sample_id}")
            oof_by_id[sample_id] = float(probability)

        fixed_probabilities = ordered_probabilities(validation, training_report["validation_predictions"])
        fixed = {
            "fold": fold,
            "overall": binary_metrics(validation, fixed_probabilities),
            "by_variant": metrics_by_variant(validation, fixed_probabilities),
        }
        fold_fixed.append(fixed)
        held_metrics = binary_metrics(held, probabilities)
        fold_reports.append(
            {
                "fold": fold,
                "held_out": held_metrics,
                "held_out_groups": sorted(str(row["group_key"]) for row in held),
                "selected_epoch": training_report["selected_epoch"],
                "fixed_validation": fixed,
                "training_report": {"path": str(training_report_path), "sha256": _sha256(training_report_path)},
                "checkpoint": {"path": str(checkpoint_path), "sha256": _sha256(checkpoint_path)},
            }
        )
        artifacts.extend([str(checkpoint_path), str(training_report_path)])
        del model

    expected_ids = {str(row["sample_id"]) for row in reviewed}
    if set(oof_by_id) != expected_ids:
        raise RuntimeError("cross-validation did not produce every OOF prediction")
    oof_probabilities = [oof_by_id[str(row["sample_id"])] for row in reviewed]
    oof = binary_metrics(reviewed, oof_probabilities)
    gate = gate_result(oof, fold_fixed, folds_payload["acceptance_gate"])
    official_reviewed = json.loads(args.official_reviewed_report.read_text(encoding="utf-8"))
    official_validation = json.loads(args.official_validation_report.read_text(encoding="utf-8"))
    report = {
        "schema_version": "gloshia-r4-reviewed-representation-cv-v1",
        "status": "cross_validation_go_not_exported" if gate["passed"] else "cross_validation_no_go",
        "method": "last TinyCLIP visual layer, projection and binary head; grouped OOF evaluation",
        "device": device.type,
        "threshold": THRESHOLD,
        "sources": {
            "folds": {"path": str(args.folds), "sha256": _sha256(args.folds)},
            "base_split": {"path": str(args.base_split), "sha256": _sha256(args.base_split)},
        },
        "baseline": {
            "r31_official_reviewed": _official_metric(official_reviewed, "reviewed_pool"),
            "r31_official_fixed_validation": _official_metric(official_validation, "validation"),
        },
        "oof": oof,
        "oof_predictions": [
            {
                "sample_id": row["sample_id"],
                "cv_fold": row["cv_fold"],
                "target": row["target"],
                "filter_probability": round(probability, 8),
                "predicted_action": "filter" if probability >= THRESHOLD else "allow",
            }
            for row, probability in zip(reviewed, oof_probabilities)
        ],
        "folds": fold_reports,
        "acceptance_gate": folds_payload["acceptance_gate"],
        "gate": gate,
        "candidate_artifacts": artifacts,
        "frozen_test_loaded": False,
        "final_sealed_opened": False,
        "elapsed_seconds": round(time.monotonic() - started, 3),
    }
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(json.dumps({"status": report["status"], "oof": oof, "gate": gate}, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
