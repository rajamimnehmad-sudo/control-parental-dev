#!/usr/bin/env python3
"""Cross-validate one frozen-encoder head repair on owner-reviewed crops.

The TinyCLIP visual encoder and projection stay frozen. Each fold learns only a
binary head from owner-reviewed crops while distilling the original head on all
non-held-out train groups. This is a bounded diagnostic, not an export step.
"""

from __future__ import annotations

import argparse
import copy
import hashlib
import json
import sys
import time
from collections import defaultdict
from pathlib import Path
from typing import Any


SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from pilot_tinyclip_candidate import MODEL_ID  # noqa: E402
from r2_candidate_train import TinyDataset, _checkpoint_components, _load_split  # noqa: E402


THRESHOLD = 0.4


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def _group(row: dict[str, Any]) -> str:
    return str(row.get("group_key") or row.get("source_cluster") or row.get("series") or row.get("sha256"))


def binary_metrics(records: list[dict[str, Any]], probabilities: list[float]) -> dict[str, Any]:
    if len(records) != len(probabilities):
        raise ValueError("record and prediction counts differ")
    aa = af = fa = ff = 0
    for row, probability in zip(records, probabilities):
        target = int(row["target"])
        predicted = int(float(probability) >= THRESHOLD)
        if target == 0 and predicted == 0:
            aa += 1
        elif target == 0:
            af += 1
        elif predicted == 0:
            fa += 1
        else:
            ff += 1
    return {
        "samples": len(records),
        "confusion_matrix": {
            "allow_as_allow": aa,
            "allow_as_filter": af,
            "filter_as_allow": fa,
            "filter_as_filter": ff,
        },
        "accuracy": round((aa + ff) / len(records), 6) if records else None,
        "false_permissions": fa,
        "false_filters": af,
    }


def metrics_by_variant(records: list[dict[str, Any]], probabilities: list[float]) -> dict[str, Any]:
    grouped: dict[str, list[tuple[dict[str, Any], float]]] = defaultdict(list)
    for row, probability in zip(records, probabilities):
        grouped[str(row.get("augmentation_variant") or "original")].append((row, probability))
    return {
        name: binary_metrics([row for row, _ in values], [probability for _, probability in values])
        for name, values in sorted(grouped.items())
    }


def fit_repair_head(
    anchor_features: Any,
    anchor_teacher_probabilities: Any,
    repair_features: Any,
    repair_targets: Any,
    initial_weight: Any,
    initial_bias: Any,
    *,
    repair_weight: float,
    delta_weight: float,
    max_iterations: int,
) -> tuple[Any, Any, dict[str, float]]:
    import torch
    from torch.nn import functional

    if repair_weight <= 0 or delta_weight < 0 or max_iterations < 1:
        raise ValueError("invalid head repair configuration")
    weight = initial_weight.detach().clone().reshape(-1).requires_grad_(True)
    bias = initial_bias.detach().clone().reshape(()).requires_grad_(True)
    reference_weight = initial_weight.detach().clone().reshape(-1)
    reference_bias = initial_bias.detach().clone().reshape(())
    optimizer = torch.optim.LBFGS(
        [weight, bias],
        lr=1.0,
        max_iter=max_iterations,
        tolerance_grad=1e-9,
        tolerance_change=1e-12,
        line_search_fn="strong_wolfe",
    )
    latest: dict[str, float] = {}

    def closure():
        optimizer.zero_grad(set_to_none=True)
        anchor_logits = anchor_features @ weight + bias
        repair_logits = repair_features @ weight + bias
        anchor = functional.binary_cross_entropy_with_logits(anchor_logits, anchor_teacher_probabilities)
        repair = functional.binary_cross_entropy_with_logits(repair_logits, repair_targets)
        delta = (weight - reference_weight).square().mean() + (bias - reference_bias).square()
        loss = anchor + repair_weight * repair + delta_weight * delta
        loss.backward()
        latest.update(anchor=float(anchor.detach()), repair=float(repair.detach()), delta=float(delta.detach()), total=float(loss.detach()))
        return loss

    optimizer.step(closure)
    closure()
    return weight.detach(), bias.detach(), latest


def gate_result(
    oof: dict[str, Any],
    fold_fixed: list[dict[str, Any]],
    acceptance: dict[str, Any],
) -> dict[str, Any]:
    checks = {
        "oof_false_permissions": oof["false_permissions"] <= int(acceptance["oof_false_permissions_max"]),
        "oof_false_filters": oof["false_filters"] <= int(acceptance["oof_false_filters_max"]),
    }
    for fold in fold_fixed:
        number = int(fold["fold"])
        original = fold["by_variant"]["original"]
        overall = fold["overall"]
        checks[f"fold_{number}_original_false_permissions"] = original["false_permissions"] <= int(
            acceptance["fixed_validation_original_false_permissions_max"]
        )
        checks[f"fold_{number}_original_false_filters"] = original["false_filters"] <= int(
            acceptance["fixed_validation_original_false_filters_max"]
        )
        checks[f"fold_{number}_all_false_permissions"] = overall["false_permissions"] <= int(
            acceptance["fixed_validation_all_false_permissions_max"]
        )
        checks[f"fold_{number}_all_false_filters"] = overall["false_filters"] <= int(
            acceptance["fixed_validation_all_false_filters_max"]
        )
    return {"passed": all(checks.values()), "checks": checks}


def _features(model: Any, records: list[dict[str, Any]], processor: Any, device: Any) -> Any:
    import torch
    from torch.nn import functional
    from torch.utils.data import DataLoader

    loader = DataLoader(TinyDataset(records, processor, augment=False), batch_size=16, shuffle=False, num_workers=0)
    output = []
    model.eval()
    with torch.inference_mode():
        for pixels, _ in loader:
            pooled = model.vision_model(pixel_values=pixels.to(device)).pooler_output
            output.append(functional.normalize(model.visual_projection(pooled), dim=1).cpu())
    return torch.cat(output)


def _probabilities(features: Any, weight: Any, bias: Any) -> list[float]:
    import torch

    return torch.sigmoid(features @ weight.reshape(-1) + bias.reshape(())).tolist()


def _official_metric(report: dict[str, Any], split_name: str) -> dict[str, Any]:
    overall = report["metrics"][split_name]["overall"]
    return {
        "samples": overall["samples"],
        "false_permissions": overall["false_permissions"]["count"],
        "false_filters": overall["false_filters"]["count"],
        "accuracy": overall["accuracy"],
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--folds", required=True, type=Path)
    parser.add_argument("--base-split", required=True, type=Path)
    parser.add_argument("--image-root", required=True, type=Path)
    parser.add_argument("--initial-checkpoint", required=True, type=Path)
    parser.add_argument("--official-reviewed-report", required=True, type=Path)
    parser.add_argument("--official-validation-report", required=True, type=Path)
    parser.add_argument("--report", required=True, type=Path)
    parser.add_argument("--checkpoint", type=Path)
    parser.add_argument("--repair-weight", type=float, default=0.25)
    parser.add_argument("--delta-weight", type=float, default=0.01)
    parser.add_argument("--max-iterations", type=int, default=100)
    parser.add_argument("--device", choices=("cpu", "mps", "auto"), default="auto")
    args = parser.parse_args()
    started = time.monotonic()

    import torch
    from torch import nn
    from transformers import AutoModel, AutoProcessor

    torch.manual_seed(4201)
    torch.use_deterministic_algorithms(True)
    if args.device == "mps":
        if not torch.backends.mps.is_available():
            raise RuntimeError("MPS requested but unavailable")
        device = torch.device("mps")
    elif args.device == "cpu":
        device = torch.device("cpu")
    else:
        device = torch.device("mps" if torch.backends.mps.is_available() else "cpu")

    fold_payload = json.loads(args.folds.read_text(encoding="utf-8"))
    reviewed = [dict(row, resolved_image_path=str(Path(row["image_path"]))) for row in fold_payload["records"]]
    if any(not Path(row["resolved_image_path"]).is_file() for row in reviewed):
        raise FileNotFoundError("a reviewed crop is missing")
    train, validation = _load_split(args.base_split, args.image_root)
    initial = torch.load(args.initial_checkpoint, map_location="cpu", weights_only=False)
    vision_state, projection_state, initial_weight, initial_bias = _checkpoint_components(initial)
    base = AutoModel.from_pretrained(MODEL_ID, local_files_only=True)
    base.vision_model.load_state_dict(vision_state)
    base.visual_projection.load_state_dict(projection_state)

    class FeatureModel(nn.Module):
        def __init__(self):
            super().__init__()
            self.vision_model = base.vision_model
            self.visual_projection = base.visual_projection

    feature_model = FeatureModel().to(device)
    processor = AutoProcessor.from_pretrained(MODEL_ID, local_files_only=True)
    train_features = _features(feature_model, train, processor, device)
    validation_features = _features(feature_model, validation, processor, device)
    reviewed_features = _features(feature_model, reviewed, processor, device)
    initial_weight = torch.as_tensor(initial_weight, dtype=torch.float32).reshape(-1)
    initial_bias = torch.as_tensor(initial_bias, dtype=torch.float32).reshape(())
    train_teacher = torch.sigmoid(train_features @ initial_weight + initial_bias)

    folds = int(fold_payload["folds"])
    oof_probabilities: list[float | None] = [None] * len(reviewed)
    fold_reports = []
    fold_fixed = []
    for fold in range(folds):
        held_indices = [index for index, row in enumerate(reviewed) if int(row["cv_fold"]) == fold]
        repair_indices = [index for index, row in enumerate(reviewed) if int(row["cv_fold"]) != fold]
        held_groups = {_group(reviewed[index]) for index in held_indices}
        anchor_indices = [index for index, row in enumerate(train) if _group(row) not in held_groups]
        weight, bias, losses = fit_repair_head(
            train_features[anchor_indices],
            train_teacher[anchor_indices],
            reviewed_features[repair_indices],
            torch.tensor([float(reviewed[index]["target"]) for index in repair_indices]),
            initial_weight,
            initial_bias,
            repair_weight=args.repair_weight,
            delta_weight=args.delta_weight,
            max_iterations=args.max_iterations,
        )
        held_probabilities = _probabilities(reviewed_features[held_indices], weight, bias)
        for index, probability in zip(held_indices, held_probabilities):
            oof_probabilities[index] = probability
        fixed_probabilities = _probabilities(validation_features, weight, bias)
        fixed = {
            "fold": fold,
            "overall": binary_metrics(validation, fixed_probabilities),
            "by_variant": metrics_by_variant(validation, fixed_probabilities),
        }
        fold_fixed.append(fixed)
        fold_reports.append(
            {
                "fold": fold,
                "held_out_samples": len(held_indices),
                "held_out_groups": sorted(held_groups),
                "anchor_train_samples": len(anchor_indices),
                "repair_train_samples": len(repair_indices),
                "loss": losses,
                "held_out": binary_metrics([reviewed[index] for index in held_indices], held_probabilities),
                "fixed_validation": fixed,
            }
        )

    if any(probability is None for probability in oof_probabilities):
        raise RuntimeError("cross-validation did not produce every OOF prediction")
    oof_values = [float(probability) for probability in oof_probabilities if probability is not None]
    oof = binary_metrics(reviewed, oof_values)
    gate = gate_result(oof, fold_fixed, fold_payload["acceptance_gate"])
    official_reviewed = json.loads(args.official_reviewed_report.read_text(encoding="utf-8"))
    official_validation = json.loads(args.official_validation_report.read_text(encoding="utf-8"))

    final_summary = None
    checkpoint_written = False
    if gate["passed"]:
        final_weight, final_bias, final_loss = fit_repair_head(
            train_features,
            train_teacher,
            reviewed_features,
            torch.tensor([float(row["target"]) for row in reviewed]),
            initial_weight,
            initial_bias,
            repair_weight=args.repair_weight,
            delta_weight=args.delta_weight,
            max_iterations=args.max_iterations,
        )
        final_validation_probabilities = _probabilities(validation_features, final_weight, final_bias)
        final_reviewed_probabilities = _probabilities(reviewed_features, final_weight, final_bias)
        final_summary = {
            "loss": final_loss,
            "reviewed": binary_metrics(reviewed, final_reviewed_probabilities),
            "fixed_validation": binary_metrics(validation, final_validation_probabilities),
            "fixed_validation_by_variant": metrics_by_variant(validation, final_validation_probabilities),
        }
        if args.checkpoint is not None:
            candidate = copy.deepcopy(initial)
            candidate["schema_version"] = "gloshia-r4-reviewed-head-repair-v1"
            candidate["threshold"] = THRESHOLD
            candidate["state_dict"]["classifier.weight"] = final_weight.reshape(1, -1)
            candidate["state_dict"]["classifier.bias"] = final_bias.reshape(1)
            candidate["training_config"] = {
                "method": "frozen encoder and projection; convex distilled binary head repair",
                "repair_weight": args.repair_weight,
                "delta_weight": args.delta_weight,
                "max_iterations": args.max_iterations,
                "source_folds_sha256": _sha256(args.folds),
            }
            args.checkpoint.parent.mkdir(parents=True, exist_ok=True)
            torch.save(candidate, args.checkpoint)
            checkpoint_written = True

    report = {
        "schema_version": "gloshia-r4-reviewed-head-cv-v1",
        "status": "cross_validation_go_not_exported" if gate["passed"] else "cross_validation_no_go",
        "method": "frozen TinyCLIP encoder/projection; convex head-only repair with grouped OOF evaluation",
        "configuration": {
            "repair_weight": args.repair_weight,
            "delta_weight": args.delta_weight,
            "max_iterations": args.max_iterations,
            "device_for_feature_extraction": device.type,
            "head_optimization_device": "cpu",
        },
        "sources": {
            "folds": {"path": str(args.folds), "sha256": _sha256(args.folds)},
            "base_split": {"path": str(args.base_split), "sha256": _sha256(args.base_split)},
            "initial_checkpoint": {"path": str(args.initial_checkpoint), "sha256": _sha256(args.initial_checkpoint)},
        },
        "baseline": {
            "r31_official_reviewed": _official_metric(official_reviewed, "reviewed_pool"),
            "r31_official_fixed_validation": _official_metric(official_validation, "validation"),
            "initial_fp32_reviewed": binary_metrics(reviewed, _probabilities(reviewed_features, initial_weight, initial_bias)),
            "initial_fp32_fixed_validation": binary_metrics(validation, _probabilities(validation_features, initial_weight, initial_bias)),
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
            for row, probability in zip(reviewed, oof_values)
        ],
        "folds": fold_reports,
        "acceptance_gate": fold_payload["acceptance_gate"],
        "gate": gate,
        "final_all_reviewed_candidate": final_summary,
        "checkpoint_written": checkpoint_written,
        "frozen_test_loaded": False,
        "final_sealed_opened": False,
        "elapsed_seconds": round(time.monotonic() - started, 3),
    }
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(json.dumps({"status": report["status"], "oof": oof, "gate": gate, "checkpoint_written": checkpoint_written}, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
