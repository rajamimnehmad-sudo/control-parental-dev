#!/usr/bin/env python3
"""Bounded TinyCLIP fine-tune with frozen validation and untouched holdout."""

from __future__ import annotations

import argparse
import json
import random
import sys
from pathlib import Path
from typing import Any

from pilot_binary_baseline import _classification_metrics, load_samples
from pilot_tinyclip_candidate import MODEL_ID, _dag_letterbox_image


def _load_sets(arguments: Any) -> tuple[list[Any], list[Any], list[Any]]:
    training = [
        sample
        for review, items, public_dir in zip(
            arguments.review, arguments.items, arguments.public_dir
        )
        for sample in load_samples(review, items, public_dir, skip_excluded=True)
    ]
    validation = load_samples(
        arguments.validation_review,
        arguments.validation_items,
        arguments.validation_public_dir,
        skip_excluded=True,
    )
    holdout = load_samples(
        arguments.holdout_review,
        arguments.holdout_items,
        arguments.holdout_public_dir,
        skip_excluded=True,
        require_both_classes=False,
    )
    ids = [
        {sample.sample_id for sample in group}
        for group in (training, validation, holdout)
    ]
    if any(ids[left] & ids[right] for left, right in ((0, 1), (0, 2), (1, 2))):
        raise RuntimeError("training, validation, and holdout IDs must not overlap")
    return training, validation, holdout


class TinyDataset:
    def __init__(self, samples: list[Any], processor: Any, augment: bool) -> None:
        from torchvision.transforms import ColorJitter, RandomHorizontalFlip

        self.samples = samples
        self.processor = processor
        self.flip = RandomHorizontalFlip(p=0.5) if augment else None
        self.jitter = (
            ColorJitter(brightness=0.08, contrast=0.08, saturation=0.05)
            if augment
            else None
        )

    def __len__(self) -> int:
        return len(self.samples)

    def __getitem__(self, index: int) -> tuple[Any, Any]:
        import torch
        from PIL import Image, ImageOps

        sample = self.samples[index]
        with Image.open(sample.image_path) as opened:
            image = ImageOps.exif_transpose(opened).convert("RGB")
        if self.flip:
            image = self.flip(image)
            image = self.jitter(image)
        image = _dag_letterbox_image(image)
        pixels = self.processor(images=[image], return_tensors="pt")[
            "pixel_values"
        ][0]
        return pixels, torch.tensor(float(sample.target), dtype=torch.float32)


def _metrics(targets: list[int], probabilities: list[float]) -> dict[str, Any]:
    import numpy as np

    return _classification_metrics(
        np.asarray(targets, dtype=np.int64),
        np.asarray(probabilities, dtype=np.float64),
        0.4,
    )


def _predict(model: Any, loader: Any, device: Any) -> list[float]:
    import torch

    output: list[float] = []
    model.eval()
    with torch.inference_mode():
        for pixels, _ in loader:
            output.extend(torch.sigmoid(model(pixels.to(device))).cpu().tolist())
    return output


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--review", action="append", required=True, type=Path)
    parser.add_argument("--items", action="append", required=True, type=Path)
    parser.add_argument("--public-dir", action="append", required=True, type=Path)
    parser.add_argument("--validation-review", required=True, type=Path)
    parser.add_argument("--validation-items", required=True, type=Path)
    parser.add_argument("--validation-public-dir", required=True, type=Path)
    parser.add_argument("--holdout-review", required=True, type=Path)
    parser.add_argument("--holdout-items", required=True, type=Path)
    parser.add_argument("--holdout-public-dir", required=True, type=Path)
    parser.add_argument("--initial-checkpoint", required=True, type=Path)
    parser.add_argument("--checkpoint", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--epochs", type=int, default=8)
    args = parser.parse_args()

    if not (len(args.review) == len(args.items) == len(args.public_dir)):
        print("error: review, items, and public-dir counts must match", file=sys.stderr)
        return 2

    import numpy as np
    import torch
    from torch import nn
    from torch.utils.data import DataLoader
    from transformers import AutoModel, AutoProcessor

    random.seed(20260728)
    np.random.seed(20260728)
    torch.manual_seed(20260728)
    device = torch.device("mps" if torch.backends.mps.is_available() else "cpu")
    training, validation, holdout = _load_sets(args)
    initial = torch.load(args.initial_checkpoint, map_location="cpu", weights_only=False)
    base = AutoModel.from_pretrained(MODEL_ID)
    base.vision_model.load_state_dict(initial["vision_model"])
    base.visual_projection.load_state_dict(initial["visual_projection"])

    class Policy(nn.Module):
        def __init__(self) -> None:
            super().__init__()
            self.vision_model = base.vision_model
            self.visual_projection = base.visual_projection
            self.classifier = nn.Linear(
                int(initial["classifier_coef"].shape[1]), 1
            )
            self.classifier.weight.data.copy_(
                torch.as_tensor(initial["classifier_coef"], dtype=torch.float32)
            )
            self.classifier.bias.data.copy_(
                torch.as_tensor(initial["classifier_intercept"], dtype=torch.float32)
            )

        def forward(self, pixels: Any) -> Any:
            pooled = self.vision_model(pixel_values=pixels).pooler_output
            features = nn.functional.normalize(
                self.visual_projection(pooled), dim=1
            )
            return self.classifier(features).squeeze(1)

    model = Policy().to(device)
    for parameter in model.vision_model.parameters():
        parameter.requires_grad = False
    for parameter in model.vision_model.encoder.layers[-1].parameters():
        parameter.requires_grad = True
    for parameter in model.vision_model.post_layernorm.parameters():
        parameter.requires_grad = True

    processor = AutoProcessor.from_pretrained(MODEL_ID)
    train_loader = DataLoader(
        TinyDataset(training, processor, augment=True),
        batch_size=8,
        shuffle=True,
        num_workers=0,
    )
    validation_loader = DataLoader(
        TinyDataset(validation, processor, augment=False),
        batch_size=8,
        shuffle=False,
        num_workers=0,
    )
    holdout_loader = DataLoader(
        TinyDataset(holdout, processor, augment=False),
        batch_size=4,
        shuffle=False,
        num_workers=0,
    )
    counts = np.bincount([sample.target for sample in training], minlength=2)
    loss_function = nn.BCEWithLogitsLoss(
        pos_weight=torch.tensor(
            float(counts[0] / counts[1]),
            dtype=torch.float32,
            device=device,
        )
    )
    optimizer = torch.optim.AdamW(
        [
            {
                "params": model.vision_model.encoder.layers[-1].parameters(),
                "lr": 2e-6,
            },
            {
                "params": model.vision_model.post_layernorm.parameters(),
                "lr": 4e-6,
            },
            {"params": model.visual_projection.parameters(), "lr": 8e-6},
            {"params": model.classifier.parameters(), "lr": 5e-5},
        ],
        weight_decay=1e-3,
    )

    validation_targets = [sample.target for sample in validation]
    history = []
    best_state = None
    best_key = None
    for epoch in range(1, args.epochs + 1):
        model.train()
        total = 0.0
        batches = 0
        for pixels, targets in train_loader:
            optimizer.zero_grad(set_to_none=True)
            loss = loss_function(model(pixels.to(device)), targets.to(device))
            loss.backward()
            torch.nn.utils.clip_grad_norm_(
                [parameter for parameter in model.parameters() if parameter.requires_grad],
                1.0,
            )
            optimizer.step()
            total += float(loss.detach().cpu())
            batches += 1
        probabilities = _predict(model, validation_loader, device)
        metrics = _metrics(validation_targets, probabilities)
        false_allow = len(metrics["confusion_matrix"]) and metrics[
            "confusion_matrix"
        ]["filter_as_allow"]
        false_filter = metrics["confusion_matrix"]["allow_as_filter"]
        key = (false_allow, false_filter, total / max(1, batches))
        if best_key is None or key < best_key:
            best_key = key
            best_state = {
                key: value.detach().cpu().clone()
                for key, value in model.state_dict().items()
            }
        history.append(
            {
                "epoch": epoch,
                "loss": round(total / max(1, batches), 6),
                "validation": metrics,
            }
        )

    model.load_state_dict(best_state)
    validation_probabilities = _predict(model, validation_loader, device)
    holdout_probabilities = _predict(model, holdout_loader, device)
    report = {
        "schema_version": "dag-v3-tinyclip-bounded-finetune-v1",
        "status": "research_only_not_approved_for_apk",
        "device": device.type,
        "training_samples": len(training),
        "validation_samples": len(validation),
        "holdout_samples": len(holdout),
        "history": history,
        "selected_validation": _metrics(
            validation_targets, validation_probabilities
        ),
        "untouched_holdout": _metrics(
            [sample.target for sample in holdout], holdout_probabilities
        ),
        "holdout_predictions": [
            {
                "sample_id": sample.sample_id,
                "human_action": sample.action,
                "filter_probability": round(probability, 6),
                "predicted_action": "filter" if probability >= 0.4 else "allow",
            }
            for sample, probability in zip(holdout, holdout_probabilities)
        ],
    }
    args.checkpoint.parent.mkdir(parents=True, exist_ok=True)
    torch.save(
        {
            "schema_version": report["schema_version"],
            "state_dict": best_state,
            "threshold": 0.4,
            "pretrained_model_id": MODEL_ID,
            "preprocessing": "dag-letterbox",
        },
        args.checkpoint,
    )
    args.output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({
        "validation": report["selected_validation"],
        "holdout": report["untouched_holdout"],
    }, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
