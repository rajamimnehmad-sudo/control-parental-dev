#!/usr/bin/env python3
"""Run one bounded R2 TinyCLIP fine-tune trial from the fixed split.

The model definition, preprocessing, trainable layers and optimizer defaults
match ``pilot_tinyclip_finetune.py``.  This wrapper only changes data loading
and makes the allowed experiment knobs explicit; it never loads frozen_test.
"""

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

from pilot_tinyclip_candidate import MODEL_ID, _dag_letterbox_image  # noqa: E402
from r2_candidate_evaluate import classification_metrics  # noqa: E402


def _load_split(path: Path, image_root: Path) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    records = payload.get("records", [])
    train = [record for record in records if record["split"] == "train"]
    validation = [record for record in records if record["split"] == "validation"]
    if not train or not validation:
        raise ValueError("train and validation must be non-empty")
    if any(record["split"] == "frozen_test" for record in records):
        # The rows are present in the manifest for reproducibility, but this
        # training process must not read or score them.
        frozen_ids = {record["sample_id"] for record in records if record["split"] == "frozen_test"}
        if not frozen_ids:
            raise ValueError("frozen_test metadata is malformed")
    for record in [*train, *validation]:
        image_path = image_root / record["image_path"]
        if not image_path.is_file():
            raise FileNotFoundError(image_path)
        record["resolved_image_path"] = str(image_path)
    return train, validation


class TinyDataset:
    def __init__(self, records: list[dict[str, Any]], processor: Any, augment: bool) -> None:
        from torchvision.transforms import ColorJitter, RandomHorizontalFlip

        self.records = records
        self.processor = processor
        self.flip = RandomHorizontalFlip(p=0.5) if augment else None
        self.jitter = ColorJitter(brightness=0.08, contrast=0.08, saturation=0.05) if augment else None

    def __len__(self) -> int:
        return len(self.records)

    def __getitem__(self, index: int) -> tuple[Any, Any]:
        import torch
        from PIL import Image, ImageOps

        record = self.records[index]
        with Image.open(record["resolved_image_path"]) as opened:
            image = ImageOps.exif_transpose(opened).convert("RGB")
        if self.flip:
            image = self.jitter(self.flip(image))
        image = _dag_letterbox_image(image)
        pixels = self.processor(images=[image], return_tensors="pt")["pixel_values"][0]
        return pixels, torch.tensor(float(record["target"]), dtype=torch.float32)


def _predict(model: Any, loader: Any, device: Any) -> list[float]:
    import torch

    model.eval()
    output: list[float] = []
    with torch.inference_mode():
        for pixels, _ in loader:
            output.extend(torch.sigmoid(model(pixels.to(device))).cpu().tolist())
    return output


def _validation_metrics(records: list[dict[str, Any]], probabilities: list[float]) -> dict[str, Any]:
    return classification_metrics(
        [record["target"] for record in records],
        probabilities,
        0.4,
    )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--split", required=True, type=Path)
    parser.add_argument("--image-root", required=True, type=Path)
    parser.add_argument("--initial-checkpoint", required=True, type=Path)
    parser.add_argument("--checkpoint", required=True, type=Path)
    parser.add_argument("--report", required=True, type=Path)
    parser.add_argument("--run-label", required=True)
    parser.add_argument("--seed", required=True, type=int)
    parser.add_argument("--epochs", type=int, default=2)
    parser.add_argument("--learning-rate-multiplier", type=float, default=1.0)
    parser.add_argument("--class-weight-multiplier", type=float, default=1.0)
    parser.add_argument("--weight-decay", type=float, default=1e-3)
    parser.add_argument("--device", choices=("auto", "cpu", "mps"), default="auto")
    parser.add_argument("--time-limit-seconds", type=int, default=1800)
    args = parser.parse_args()

    if args.epochs < 1 or args.learning_rate_multiplier <= 0 or args.class_weight_multiplier <= 0:
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
    if args.device == "mps":
        if not torch.backends.mps.is_available():
            raise RuntimeError("MPS requested but unavailable")
        device = torch.device("mps")
    elif args.device == "cpu":
        device = torch.device("cpu")
    else:
        device = torch.device("mps" if torch.backends.mps.is_available() else "cpu")

    train, validation = _load_split(args.split, args.image_root)
    initial = torch.load(args.initial_checkpoint, map_location="cpu", weights_only=False)
    base = AutoModel.from_pretrained(MODEL_ID)
    base.vision_model.load_state_dict(initial["vision_model"])
    base.visual_projection.load_state_dict(initial["visual_projection"])

    class Policy(nn.Module):
        def __init__(self) -> None:
            super().__init__()
            self.vision_model = base.vision_model
            self.visual_projection = base.visual_projection
            self.classifier = nn.Linear(int(initial["classifier_coef"].shape[1]), 1)
            self.classifier.weight.data.copy_(torch.as_tensor(initial["classifier_coef"], dtype=torch.float32))
            self.classifier.bias.data.copy_(torch.as_tensor(initial["classifier_intercept"], dtype=torch.float32))

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

    processor = AutoProcessor.from_pretrained(MODEL_ID)
    train_loader = DataLoader(TinyDataset(train, processor, augment=True), batch_size=8, shuffle=True, num_workers=0)
    validation_loader = DataLoader(TinyDataset(validation, processor, augment=False), batch_size=8, shuffle=False, num_workers=0)
    counts = np.bincount([record["target"] for record in train], minlength=2)
    if not counts[1]:
        raise ValueError("training split has no filter examples")
    loss_function = nn.BCEWithLogitsLoss(
        pos_weight=torch.tensor(float(counts[0] / counts[1] * args.class_weight_multiplier), dtype=torch.float32, device=device)
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

    history: list[dict[str, Any]] = []
    best_state: dict[str, Any] | None = None
    best_metrics: dict[str, Any] | None = None
    best_key: tuple[Any, ...] | None = None
    completed_epochs = 0
    for epoch in range(1, args.epochs + 1):
        if time.monotonic() - started >= args.time_limit_seconds:
            break
        model.train()
        total_loss = 0.0
        batches = 0
        for pixels, targets in train_loader:
            optimizer.zero_grad(set_to_none=True)
            loss = loss_function(model(pixels.to(device)), targets.to(device))
            loss.backward()
            torch.nn.utils.clip_grad_norm_([parameter for parameter in model.parameters() if parameter.requires_grad], 1.0)
            optimizer.step()
            total_loss += float(loss.detach().cpu())
            batches += 1
        probabilities = _predict(model, validation_loader, device)
        metrics = _validation_metrics(validation, probabilities)
        confusion = metrics["confusion_matrix"]
        key = (
            confusion["filter_as_allow"],
            confusion["allow_as_filter"],
            -(metrics["balanced_accuracy"] or -1),
            -(metrics["pr_auc"] or -1),
            total_loss / max(1, batches),
        )
        if best_key is None or key < best_key:
            best_key = key
            best_metrics = metrics
            best_state = {name: value.detach().cpu().clone() for name, value in model.state_dict().items()}
        history.append({"epoch": epoch, "loss": round(total_loss / max(1, batches), 6), "validation": metrics})
        completed_epochs = epoch

    if best_state is None or best_metrics is None:
        raise RuntimeError("time limit reached before a complete epoch")
    model.load_state_dict(best_state)
    selected_probabilities = _predict(model, validation_loader, device)
    report = {
        "schema_version": "gloshia-r2-candidate-training-v1",
        "status": "research_only_not_approved_for_apk",
        "run_label": args.run_label,
        "seed": args.seed,
        "device": device.type,
        "model_id": MODEL_ID,
        "preprocessing": "dag-letterbox",
        "threshold": 0.4,
        "train_samples": len(train),
        "train_filters": int(counts[1]),
        "validation_samples": len(validation),
        "validation_filters": sum(record["target"] for record in validation),
        "frozen_test_loaded": False,
        "configuration": {
            "epochs_requested": args.epochs,
            "epochs_completed": completed_epochs,
            "learning_rate_multiplier": args.learning_rate_multiplier,
            "class_weight_multiplier": args.class_weight_multiplier,
            "weight_decay": args.weight_decay,
            "batch_size": 8,
            "trainable": "last vision encoder layer + post_layernorm + projection + binary classifier",
        },
        "elapsed_seconds": round(time.monotonic() - started, 3),
        "history": history,
        "selected_validation": best_metrics,
        "validation_predictions": [
            {
                "sample_id": record["sample_id"],
                "filter_probability": round(float(probability), 8),
                "predicted_action": "filter" if probability >= 0.4 else "allow",
            }
            for record, probability in zip(validation, selected_probabilities)
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
            "training_config": report["configuration"],
        },
        args.checkpoint,
    )
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(json.dumps({"run_label": args.run_label, "device": device.type, "validation": best_metrics}, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
