#!/usr/bin/env python3
"""Train one bounded original/thumbnail consistency candidate from R3.1.

Every source family has equal classification weight regardless of how many
variants it owns. The official R3.1 ONNX output anchors each original while a
separate consistency loss pulls variants toward their current parent decision.
The process reads validation only and never opens frozen_test.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import random
import sys
import time
from collections import defaultdict
from pathlib import Path
from typing import Any


SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from onnx_split_score import _augmentation_report, _preprocess  # noqa: E402
from pilot_tinyclip_candidate import MODEL_ID, _dag_letterbox_image  # noqa: E402
from r2_candidate_train import TinyDataset, _checkpoint_components, _load_split, _predict  # noqa: E402


THRESHOLD = 0.4


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def build_families(records: list[dict[str, Any]]) -> list[list[dict[str, Any]]]:
    originals = {str(row["sample_id"]): row for row in records if not row.get("parent_sample_id")}
    children: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for row in records:
        parent_id = row.get("parent_sample_id")
        if not parent_id:
            continue
        parent_id = str(parent_id)
        parent = originals.get(parent_id)
        if parent is None:
            raise ValueError(f"missing parent record: {parent_id}")
        if row["split"] != parent["split"] or int(row["target"]) != int(parent["target"]):
            raise ValueError(f"parent label or split mismatch: {row['sample_id']}")
        children[parent_id].append(row)
    return [
        [parent, *sorted(children.get(sample_id, []), key=lambda row: str(row["sample_id"]))]
        for sample_id, parent in sorted(originals.items())
    ]


def gate_evaluation(candidate: dict[str, Any], baseline: dict[str, Any]) -> dict[str, Any]:
    def confusion(report: dict[str, Any], variant: str) -> dict[str, int]:
        return report["by_variant"][variant]["confusion_matrix"]

    variants = sorted(set(baseline["by_variant"]) - {"original"})
    candidate_original = confusion(candidate, "original")
    baseline_original = confusion(baseline, "original")
    candidate_augmented_false_permissions = sum(confusion(candidate, variant)["filter_as_allow"] for variant in variants)
    baseline_augmented_false_permissions = sum(confusion(baseline, variant)["filter_as_allow"] for variant in variants)
    candidate_augmented_false_filters = sum(confusion(candidate, variant)["allow_as_filter"] for variant in variants)
    baseline_augmented_false_filters = sum(confusion(baseline, variant)["allow_as_filter"] for variant in variants)
    candidate_circle = confusion(candidate, "circle128_q45")
    baseline_circle = confusion(baseline, "circle128_q45")
    candidate_stability = candidate["paired_stability"]
    baseline_stability = baseline["paired_stability"]
    checks = {
        "original_false_permissions_non_regression": candidate_original["filter_as_allow"] <= baseline_original["filter_as_allow"],
        "original_false_filters_non_regression": candidate_original["allow_as_filter"] <= baseline_original["allow_as_filter"],
        "augmented_false_permissions_improve": candidate_augmented_false_permissions < baseline_augmented_false_permissions,
        "augmented_false_filters_non_regression": candidate_augmented_false_filters <= baseline_augmented_false_filters,
        "circle_false_permissions_improve": candidate_circle["filter_as_allow"] < baseline_circle["filter_as_allow"],
        "circle_false_filters_non_regression": candidate_circle["allow_as_filter"] <= baseline_circle["allow_as_filter"],
        "unsafe_pair_degradations_improve": candidate_stability["unsafe_filter_to_allow_degradations"] < baseline_stability["unsafe_filter_to_allow_degradations"],
        "safe_pair_degradations_non_regression": candidate_stability["safe_allow_to_filter_degradations"] <= baseline_stability["safe_allow_to_filter_degradations"],
    }
    return {
        "passed": all(checks.values()),
        "checks": checks,
        "summary": {
            "original_false_permissions": [candidate_original["filter_as_allow"], baseline_original["filter_as_allow"]],
            "original_false_filters": [candidate_original["allow_as_filter"], baseline_original["allow_as_filter"]],
            "augmented_false_permissions": [candidate_augmented_false_permissions, baseline_augmented_false_permissions],
            "augmented_false_filters": [candidate_augmented_false_filters, baseline_augmented_false_filters],
            "circle_false_permissions": [candidate_circle["filter_as_allow"], baseline_circle["filter_as_allow"]],
            "circle_false_filters": [candidate_circle["allow_as_filter"], baseline_circle["allow_as_filter"]],
            "unsafe_pair_degradations": [candidate_stability["unsafe_filter_to_allow_degradations"], baseline_stability["unsafe_filter_to_allow_degradations"]],
            "safe_pair_degradations": [candidate_stability["safe_allow_to_filter_degradations"], baseline_stability["safe_allow_to_filter_degradations"]],
        },
    }


def _selection_key(gate: dict[str, Any], report: dict[str, Any]) -> tuple[Any, ...]:
    failed = sum(not passed for passed in gate["checks"].values())
    summary = gate["summary"]
    overall = report["by_variant"]["original"]
    return (
        failed,
        summary["unsafe_pair_degradations"][0],
        summary["circle_false_permissions"][0],
        summary["augmented_false_permissions"][0],
        summary["safe_pair_degradations"][0],
        summary["augmented_false_filters"][0],
        summary["original_false_permissions"][0],
        summary["original_false_filters"][0],
        -(overall["balanced_accuracy"] or -1),
    )


class FamilyDataset:
    def __init__(self, families: list[list[dict[str, Any]]], processor: Any, teacher: dict[str, float]) -> None:
        self.families = families
        self.processor = processor
        self.teacher = teacher

    def __len__(self) -> int:
        return len(self.families)

    def __getitem__(self, index: int) -> tuple[Any, Any, Any]:
        import torch
        from PIL import Image, ImageOps

        family = self.families[index]
        mirror = random.random() < 0.5
        tensors = []
        for record in family:
            with Image.open(record["resolved_image_path"]) as opened:
                image = ImageOps.exif_transpose(opened).convert("RGB")
            if mirror:
                image = ImageOps.mirror(image)
            image = _dag_letterbox_image(image)
            tensors.append(self.processor(images=[image], return_tensors="pt")["pixel_values"][0])
        return (
            torch.stack(tensors),
            torch.tensor(float(family[0]["target"]), dtype=torch.float32),
            torch.tensor(float(self.teacher[family[0]["sample_id"]]), dtype=torch.float32),
        )


def family_collate(rows: list[tuple[Any, Any, Any]]) -> dict[str, Any]:
    import torch

    pixels = []
    targets = []
    family_indices = []
    parent_indices = []
    child_indices = []
    child_parent_indices = []
    teacher_probabilities = []
    cursor = 0
    for family_index, (family_pixels, target, teacher_probability) in enumerate(rows):
        views = int(family_pixels.shape[0])
        pixels.append(family_pixels)
        targets.extend([target] * views)
        family_indices.extend([family_index] * views)
        parent_indices.append(cursor)
        teacher_probabilities.append(teacher_probability)
        for child_index in range(cursor + 1, cursor + views):
            child_indices.append(child_index)
            child_parent_indices.append(cursor)
        cursor += views
    return {
        "pixels": torch.cat(pixels),
        "targets": torch.stack(targets),
        "family_indices": torch.tensor(family_indices, dtype=torch.long),
        "parent_indices": torch.tensor(parent_indices, dtype=torch.long),
        "child_indices": torch.tensor(child_indices, dtype=torch.long),
        "child_parent_indices": torch.tensor(child_parent_indices, dtype=torch.long),
        "teacher_probabilities": torch.stack(teacher_probabilities),
        "family_count": len(rows),
    }


def family_losses(
    logits: Any,
    batch: dict[str, Any],
    *,
    pos_weight: Any,
    consistency_weight: float,
    anchor_weight: float,
) -> tuple[Any, dict[str, float]]:
    import torch
    from torch.nn import functional as functional

    device = logits.device
    targets = batch["targets"].to(device)
    family_indices = batch["family_indices"].to(device)
    per_view = functional.binary_cross_entropy_with_logits(logits, targets, pos_weight=pos_weight, reduction="none")
    family_sums = torch.zeros(batch["family_count"], dtype=logits.dtype, device=device)
    family_counts = torch.zeros(batch["family_count"], dtype=logits.dtype, device=device)
    family_sums.scatter_add_(0, family_indices, per_view)
    family_counts.scatter_add_(0, family_indices, torch.ones_like(per_view))
    classification = (family_sums / family_counts).mean()

    parent_indices = batch["parent_indices"].to(device)
    child_indices = batch["child_indices"].to(device)
    child_parent_indices = batch["child_parent_indices"].to(device)
    probabilities = torch.sigmoid(logits)
    if int(child_indices.numel()):
        consistency = functional.mse_loss(
            probabilities[child_indices],
            probabilities[child_parent_indices].detach(),
        )
    else:
        consistency = logits.sum() * 0.0
    anchor = functional.mse_loss(
        probabilities[parent_indices],
        batch["teacher_probabilities"].to(device),
    )
    total = classification + consistency_weight * consistency + anchor_weight * anchor
    return total, {
        "classification": float(classification.detach().cpu()),
        "consistency": float(consistency.detach().cpu()),
        "anchor": float(anchor.detach().cpu()),
        "total": float(total.detach().cpu()),
    }


def _teacher_predictions(records: list[dict[str, Any]], processor: Any, model_path: Path) -> dict[str, float]:
    import onnxruntime as ort

    session = ort.InferenceSession(str(model_path), providers=["CPUExecutionProvider"])
    return {
        str(record["sample_id"]): float(
            session.run(None, {"pixel_values": _preprocess(processor, Path(record["resolved_image_path"]))})[0][0, 0]
        )
        for record in records
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--split", required=True, type=Path)
    parser.add_argument("--image-root", required=True, type=Path)
    parser.add_argument("--initial-checkpoint", required=True, type=Path)
    parser.add_argument("--teacher-onnx", required=True, type=Path)
    parser.add_argument("--baseline-report", required=True, type=Path)
    parser.add_argument("--checkpoint", required=True, type=Path)
    parser.add_argument("--report", required=True, type=Path)
    parser.add_argument("--run-label", required=True)
    parser.add_argument("--seed", required=True, type=int)
    parser.add_argument("--epochs", type=int, default=2)
    parser.add_argument("--learning-rate-multiplier", type=float, default=0.25)
    parser.add_argument("--class-weight-multiplier", type=float, default=0.625)
    parser.add_argument("--consistency-weight", type=float, default=1.0)
    parser.add_argument("--anchor-weight", type=float, default=0.25)
    parser.add_argument("--weight-decay", type=float, default=0.02)
    parser.add_argument("--device", choices=("auto", "cpu", "mps"), default="auto")
    parser.add_argument("--time-limit-seconds", type=int, default=300)
    args = parser.parse_args()
    if min(args.epochs, args.learning_rate_multiplier, args.class_weight_multiplier) <= 0:
        raise ValueError("invalid training configuration")
    if args.consistency_weight < 0 or args.anchor_weight < 0:
        raise ValueError("loss weights cannot be negative")

    started = time.monotonic()
    import numpy as np
    import torch
    from torch import nn
    from torch.utils.data import DataLoader
    from transformers import AutoModel, AutoProcessor

    random.seed(args.seed)
    np.random.seed(args.seed)
    torch.manual_seed(args.seed)
    if args.device == "mps":
        if not torch.backends.mps.is_available():
            raise RuntimeError("MPS requested but unavailable")
        device = torch.device("mps")
    elif args.device == "cpu":
        device = torch.device("cpu")
    else:
        device = torch.device("mps" if torch.backends.mps.is_available() else "cpu")

    train, validation = _load_split(args.split, args.image_root)
    families = build_families(train)
    validation_ids = {str(row["sample_id"]) for row in validation}
    baseline_payload = json.loads(args.baseline_report.read_text(encoding="utf-8"))
    baseline_prediction_ids = {str(row["sample_id"]) for row in baseline_payload["predictions"]}
    if validation_ids != baseline_prediction_ids:
        raise ValueError("baseline report does not match validation membership")
    baseline_augmentation = baseline_payload["augmentation_metrics"]["validation"]

    initial = torch.load(args.initial_checkpoint, map_location="cpu", weights_only=False)
    vision_state, projection_state, classifier_coef, classifier_intercept = _checkpoint_components(initial)
    base = AutoModel.from_pretrained(MODEL_ID, local_files_only=True)
    base.vision_model.load_state_dict(vision_state)
    base.visual_projection.load_state_dict(projection_state)

    class Policy(nn.Module):
        def __init__(self) -> None:
            super().__init__()
            self.vision_model = base.vision_model
            self.visual_projection = base.visual_projection
            self.classifier = nn.Linear(int(classifier_coef.shape[1]), 1)
            self.classifier.weight.data.copy_(torch.as_tensor(classifier_coef, dtype=torch.float32))
            self.classifier.bias.data.copy_(torch.as_tensor(classifier_intercept, dtype=torch.float32))

        def forward(self, pixels: Any) -> Any:
            pooled = self.vision_model(pixel_values=pixels).pooler_output
            features = nn.functional.normalize(self.visual_projection(pooled), dim=1)
            return self.classifier(features).squeeze(1)

    model = Policy().to(device)
    for parameter in model.vision_model.parameters():
        parameter.requires_grad = False
    for parameter in model.vision_model.encoder.layers[-1].parameters():
        parameter.requires_grad = True
    for parameter in model.vision_model.post_layernorm.parameters():
        parameter.requires_grad = True

    processor = AutoProcessor.from_pretrained(MODEL_ID, local_files_only=True)
    originals = [family[0] for family in families]
    teacher = _teacher_predictions(originals, processor, args.teacher_onnx)
    train_loader = DataLoader(
        FamilyDataset(families, processor, teacher),
        batch_size=8,
        shuffle=True,
        num_workers=0,
        collate_fn=family_collate,
    )
    validation_loader = DataLoader(TinyDataset(validation, processor, augment=False), batch_size=8, shuffle=False, num_workers=0)
    family_targets = [int(family[0]["target"]) for family in families]
    counts = np.bincount(family_targets, minlength=2)
    if not counts[1]:
        raise ValueError("training split has no filter families")
    pos_weight = torch.tensor(
        float(counts[0] / counts[1] * args.class_weight_multiplier),
        dtype=torch.float32,
        device=device,
    )
    lr = args.learning_rate_multiplier
    optimizer = torch.optim.AdamW(
        [
            {"params": model.vision_model.encoder.layers[-1].parameters(), "lr": 2e-6 * lr},
            {"params": model.vision_model.post_layernorm.parameters(), "lr": 4e-6 * lr},
            {"params": model.visual_projection.parameters(), "lr": 8e-6 * lr},
            {"params": model.classifier.parameters(), "lr": 5e-5 * lr},
        ],
        weight_decay=args.weight_decay,
    )

    history = []
    best_state = None
    best_epoch_report = None
    best_key = None
    for epoch in range(1, args.epochs + 1):
        if time.monotonic() - started >= args.time_limit_seconds:
            break
        model.train()
        totals = defaultdict(float)
        batches = 0
        for batch in train_loader:
            optimizer.zero_grad(set_to_none=True)
            logits = model(batch["pixels"].to(device))
            loss, components = family_losses(
                logits,
                batch,
                pos_weight=pos_weight,
                consistency_weight=args.consistency_weight,
                anchor_weight=args.anchor_weight,
            )
            loss.backward()
            torch.nn.utils.clip_grad_norm_([p for p in model.parameters() if p.requires_grad], 1.0)
            optimizer.step()
            for name, value in components.items():
                totals[name] += value
            batches += 1

        probabilities = _predict(model, validation_loader, device)
        predictions = {
            str(record["sample_id"]): {
                "sample_id": record["sample_id"],
                "filter_probability": float(probability),
                "predicted_action": "filter" if probability >= THRESHOLD else "allow",
            }
            for record, probability in zip(validation, probabilities)
        }
        augmentation = _augmentation_report(validation, predictions, THRESHOLD)
        gate = gate_evaluation(augmentation, baseline_augmentation)
        epoch_report = {
            "epoch": epoch,
            "loss": {name: round(value / max(1, batches), 6) for name, value in totals.items()},
            "augmentation_metrics": augmentation,
            "gate": gate,
        }
        key = _selection_key(gate, augmentation)
        if best_key is None or key < best_key:
            best_key = key
            best_epoch_report = epoch_report
            best_state = {name: value.detach().cpu().clone() for name, value in model.state_dict().items()}
        history.append(epoch_report)

    if best_state is None or best_epoch_report is None:
        raise RuntimeError("time limit reached before a complete epoch")
    model.load_state_dict(best_state)
    selected_probabilities = _predict(model, validation_loader, device)
    selected_predictions = [
        {
            "sample_id": record["sample_id"],
            "filter_probability": round(float(probability), 8),
            "predicted_action": "filter" if probability >= THRESHOLD else "allow",
        }
        for record, probability in zip(validation, selected_probabilities)
    ]
    report = {
        "schema_version": "gloshia-r4-consistency-training-v1",
        "status": "research_only_not_approved_for_apk",
        "run_label": args.run_label,
        "seed": args.seed,
        "device": device.type,
        "threshold": THRESHOLD,
        "model_id": MODEL_ID,
        "teacher_onnx": {
            "path": str(args.teacher_onnx),
            "sha256": _sha256(args.teacher_onnx),
            "bytes": args.teacher_onnx.stat().st_size,
        },
        "train_families": len(families),
        "train_views": sum(len(family) for family in families),
        "train_filter_families": int(counts[1]),
        "validation_samples": len(validation),
        "frozen_test_loaded": False,
        "final_sealed_opened": False,
        "configuration": {
            "epochs_requested": args.epochs,
            "epochs_completed": len(history),
            "learning_rate_multiplier": args.learning_rate_multiplier,
            "class_weight_multiplier": args.class_weight_multiplier,
            "effective_pos_weight": round(float(pos_weight.cpu()), 8),
            "consistency_weight": args.consistency_weight,
            "anchor_weight": args.anchor_weight,
            "weight_decay": args.weight_decay,
            "batch_families": 8,
            "family_weighting": "mean BCE per family, then mean families",
            "consistency": "MSE child probability to detached current parent probability",
            "anchor": "MSE parent probability to official R3.1 ONNX probability",
        },
        "baseline_gate": baseline_augmentation,
        "history": history,
        "selected_epoch": best_epoch_report["epoch"],
        "selected_gate": best_epoch_report["gate"],
        "selected_augmentation_metrics": best_epoch_report["augmentation_metrics"],
        "validation_predictions": selected_predictions,
        "elapsed_seconds": round(time.monotonic() - started, 3),
    }
    args.checkpoint.parent.mkdir(parents=True, exist_ok=True)
    torch.save(
        {
            "schema_version": report["schema_version"],
            "state_dict": best_state,
            "threshold": THRESHOLD,
            "pretrained_model_id": MODEL_ID,
            "preprocessing": "dag-letterbox",
            "training_config": report["configuration"],
        },
        args.checkpoint,
    )
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(json.dumps({"run_label": args.run_label, "selected_epoch": report["selected_epoch"], "gate": report["selected_gate"]}, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
