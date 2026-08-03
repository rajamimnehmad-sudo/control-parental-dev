#!/usr/bin/env python3
"""Fine-tune one TinyCLIP model against DAG's exact regional policy."""

from __future__ import annotations

import argparse
import json
import random
import sys
import time
from pathlib import Path
from typing import Any

SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from r2_candidate_evaluate import classification_metrics  # noqa: E402
from r2_candidate_train import _checkpoint_components  # noqa: E402
from r24_region_policy import (  # noqa: E402
    REGION_FILTER_THRESHOLD,
    REGION_STRONG_THRESHOLD,
    UNCERTAIN_REGION_THRESHOLD,
    UNCERTAIN_REVIEW_FLOOR,
    dag_region_views,
    exact_policy_decision,
)
from pilot_tinyclip_candidate import MODEL_ID  # noqa: E402


class RegionDataset:
    def __init__(self, records: list[dict[str, Any]], processor: Any, augment: bool) -> None:
        from torchvision.transforms import ColorJitter, RandomHorizontalFlip

        self.records = records
        self.processor = processor
        self.flip = RandomHorizontalFlip(p=0.5) if augment else None
        self.jitter = ColorJitter(brightness=0.08, contrast=0.08, saturation=0.05) if augment else None

    def __len__(self) -> int:
        return len(self.records)

    def __getitem__(self, index: int) -> tuple[Any, Any, str, str]:
        import torch
        from PIL import Image, ImageOps

        record = self.records[index]
        with Image.open(record["image_path"]) as opened:
            image = ImageOps.exif_transpose(opened).convert("RGB")
        if self.flip:
            image = self.jitter(self.flip(image))
        views = dag_region_views(image)
        images = list(views.images)
        if len(images) == 4:
            images.append(images[-1])
        pixels = self.processor(images=images, return_tensors="pt")["pixel_values"]
        return pixels, torch.tensor(float(record["target"]), dtype=torch.float32), views.kind, record["sample_id"]


def _collate(rows: list[tuple[Any, Any, str, str]]) -> tuple[Any, Any, list[str], list[str]]:
    import torch

    pixels, targets, kinds, sample_ids = zip(*rows)
    return torch.stack(pixels), torch.stack(targets), list(kinds), list(sample_ids)


def _load_records(path: Path) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    train = [dict(row) for row in payload.get("records", []) if row.get("split") == "train"]
    validation = [dict(row) for row in payload.get("records", []) if row.get("split") == "validation"]
    if not train or not validation:
        raise ValueError("train and validation must be non-empty")
    for row in [*train, *validation]:
        image_path = Path(row["image_path"])
        if not image_path.is_file():
            raise FileNotFoundError(image_path)
        row["image_path"] = str(image_path)
    return train, validation


def _policy_margins(probabilities: Any, kinds: list[str]) -> tuple[Any, Any, Any]:
    import torch

    full = probabilities[:, 0]
    policy: list[Any] = []
    regional: list[Any] = []
    for index, kind in enumerate(kinds):
        full_path = full[index] - 0.40
        if kind == "uncertain":
            region_values = probabilities[index, 1:5]
            region_max = region_values.max()
            regional_path = region_max - UNCERTAIN_REGION_THRESHOLD
            gated_path = torch.minimum(full[index] - UNCERTAIN_REVIEW_FLOOR, regional_path)
        elif kind == "extreme":
            region_values = probabilities[index, 1:4]
            strongest = region_values.max()
            second = region_values.topk(2).values[1]
            regional_path = torch.maximum(
                strongest - REGION_STRONG_THRESHOLD,
                second - REGION_FILTER_THRESHOLD,
            )
            gated_path = regional_path
        else:
            raise ValueError(f"unknown regional kind: {kind}")
        policy.append(torch.maximum(full_path, gated_path))
        regional.append(regional_path)
    return torch.stack(policy), torch.stack(regional), full - UNCERTAIN_REVIEW_FLOOR


def _predict(model: Any, loader: Any, device: Any) -> tuple[list[float], list[int], list[dict[str, Any]]]:
    import torch

    model.eval()
    scores: list[float] = []
    predicted: list[int] = []
    rows: list[dict[str, Any]] = []
    with torch.inference_mode():
        for pixels, _, kinds, sample_ids in loader:
            batch, views, channels, height, width = pixels.shape
            logits = model(pixels.reshape(batch * views, channels, height, width).to(device)).reshape(batch, views)
            probabilities = torch.sigmoid(logits).cpu()
            margins, _, _ = _policy_margins(probabilities, kinds)
            for sample_id, kind, values, margin in zip(sample_ids, kinds, probabilities.tolist(), margins.tolist()):
                decision_values = values[:4] if kind == "extreme" else values
                decision = exact_policy_decision(decision_values, kind)
                scores.append(float(torch.sigmoid(torch.tensor(margin * 12.0))))
                predicted.append(int(decision["action"] == "filter"))
                rows.append({"sample_id": sample_id, "kind": kind, "probabilities": values, **decision})
    return scores, predicted, rows


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--split", required=True, type=Path)
    parser.add_argument("--initial-checkpoint", required=True, type=Path)
    parser.add_argument("--checkpoint", required=True, type=Path)
    parser.add_argument("--report", required=True, type=Path)
    parser.add_argument("--run-label", required=True)
    parser.add_argument("--seed", required=True, type=int)
    parser.add_argument("--epochs", type=int, default=2)
    parser.add_argument("--learning-rate-multiplier", type=float, default=0.35)
    parser.add_argument("--class-weight-multiplier", type=float, default=1.0)
    parser.add_argument("--region-loss-weight", type=float, default=0.5)
    parser.add_argument("--gate-loss-weight", type=float, default=0.25)
    parser.add_argument("--device", choices=("auto", "cpu", "mps"), default="auto")
    parser.add_argument("--time-limit-seconds", type=int, default=1800)
    args = parser.parse_args()

    if args.epochs < 1 or min(args.learning_rate_multiplier, args.class_weight_multiplier) <= 0:
        raise ValueError("invalid training configuration")
    started = time.monotonic()
    import numpy as np
    import torch
    from torch import nn
    from torch.utils.data import DataLoader
    from transformers import AutoModel, AutoProcessor

    random.seed(args.seed)
    np.random.seed(args.seed)
    torch.manual_seed(args.seed)
    if args.device == "mps" and not torch.backends.mps.is_available():
        raise RuntimeError("MPS requested but unavailable")
    if args.device == "auto":
        device = torch.device("mps" if torch.backends.mps.is_available() else "cpu")
    else:
        device = torch.device(args.device)

    train, validation = _load_records(args.split)
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
    train_loader = DataLoader(RegionDataset(train, processor, True), batch_size=4, shuffle=True, num_workers=0, collate_fn=_collate)
    validation_loader = DataLoader(RegionDataset(validation, processor, False), batch_size=4, shuffle=False, num_workers=0, collate_fn=_collate)
    counts = np.bincount([row["target"] for row in train], minlength=2)
    positive_weight = float(counts[0] / counts[1] * args.class_weight_multiplier)
    loss_function = nn.BCEWithLogitsLoss(pos_weight=torch.tensor(positive_weight, device=device))
    optimizer = torch.optim.AdamW(
        [
            {"params": model.vision_model.encoder.layers[-1].parameters(), "lr": 2e-6 * args.learning_rate_multiplier},
            {"params": model.vision_model.post_layernorm.parameters(), "lr": 4e-6 * args.learning_rate_multiplier},
            {"params": model.visual_projection.parameters(), "lr": 8e-6 * args.learning_rate_multiplier},
            {"params": model.classifier.parameters(), "lr": 5e-5 * args.learning_rate_multiplier},
        ],
        weight_decay=1e-3,
    )

    history: list[dict[str, Any]] = []
    best_state: dict[str, Any] | None = None
    best_metrics: dict[str, Any] | None = None
    best_predictions: list[dict[str, Any]] = []
    best_key: tuple[Any, ...] | None = None
    completed_epochs = 0
    for epoch in range(1, args.epochs + 1):
        if time.monotonic() - started >= args.time_limit_seconds:
            break
        model.train()
        total_loss = 0.0
        batches = 0
        for pixels, targets, kinds, _ in train_loader:
            batch, views, channels, height, width = pixels.shape
            optimizer.zero_grad(set_to_none=True)
            logits = model(pixels.reshape(batch * views, channels, height, width).to(device)).reshape(batch, views)
            probabilities = torch.sigmoid(logits)
            policy, regional, gate = _policy_margins(probabilities, kinds)
            targets = targets.to(device)
            loss = (
                loss_function(policy * 12.0, targets)
                + args.region_loss_weight * loss_function(regional * 12.0, targets)
                + args.gate_loss_weight * loss_function(gate * 12.0, targets)
            )
            loss.backward()
            torch.nn.utils.clip_grad_norm_([parameter for parameter in model.parameters() if parameter.requires_grad], 1.0)
            optimizer.step()
            total_loss += float(loss.detach().cpu())
            batches += 1
        scores, predicted, predictions = _predict(model, validation_loader, device)
        metrics = classification_metrics([row["target"] for row in validation], scores, predicted=predicted)
        confusion = metrics["confusion_matrix"]
        key = (confusion["filter_as_allow"], confusion["allow_as_filter"], -(metrics["balanced_accuracy"] or -1))
        if best_key is None or key < best_key:
            best_key = key
            best_metrics = metrics
            best_predictions = predictions
            best_state = {name: value.detach().cpu().clone() for name, value in model.state_dict().items()}
        history.append({"epoch": epoch, "loss": round(total_loss / max(1, batches), 6), "validation": metrics})
        completed_epochs = epoch

    if best_state is None or best_metrics is None:
        raise RuntimeError("time limit reached before a complete epoch")
    report = {
        "schema_version": "gloshia-r24-region-policy-training-v1",
        "status": "research_only_not_approved_for_apk",
        "run_label": args.run_label,
        "seed": args.seed,
        "device": device.type,
        "threshold_contract": "dag-policy-0.40-0.30-0.45-0.50-0.70",
        "train_samples": len(train),
        "train_filters": int(counts[1]),
        "validation_samples": len(validation),
        "validation_filters": sum(row["target"] for row in validation),
        "frozen_test_loaded": False,
        "configuration": {
            "epochs_requested": args.epochs,
            "epochs_completed": completed_epochs,
            "learning_rate_multiplier": args.learning_rate_multiplier,
            "class_weight_multiplier": args.class_weight_multiplier,
            "region_loss_weight": args.region_loss_weight,
            "gate_loss_weight": args.gate_loss_weight,
            "batch_size_bags": 4,
            "views_per_bag": "4 extreme or 5 uncertain",
        },
        "elapsed_seconds": round(time.monotonic() - started, 3),
        "history": history,
        "selected_validation": best_metrics,
        "validation_predictions": best_predictions,
    }
    args.checkpoint.parent.mkdir(parents=True, exist_ok=True)
    torch.save(
        {
            "schema_version": report["schema_version"],
            "state_dict": best_state,
            "threshold": 0.4,
            "pretrained_model_id": MODEL_ID,
            "preprocessing": "dag-letterbox",
            "training_config": report["configuration"],
        },
        args.checkpoint,
    )
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(json.dumps({"run_label": args.run_label, "validation": best_metrics}, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
