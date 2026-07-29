#!/usr/bin/env python3
"""Fine-tune a bounded MobileNetV3 pilot and report out-of-fold errors."""

from __future__ import annotations

import argparse
import json
import random
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Sequence
from urllib.parse import urlparse

from pilot_binary_baseline import (
    PilotSample,
    _case_list,
    _classification_metrics,
    _sha256,
    load_samples,
)


REPORT_SCHEMA_VERSION = "dag-v3-pilot-finetune-report-v1"
DEFAULT_SEED = 20260728


def _seed_everything(seed: int) -> None:
    import numpy as np
    import torch

    random.seed(seed)
    np.random.seed(seed)
    torch.manual_seed(seed)


def _device(requested: str) -> Any:
    import torch

    if requested == "mps":
        if not torch.backends.mps.is_available():
            raise RuntimeError("MPS was requested but is unavailable")
        return torch.device("mps")
    if requested == "cpu":
        return torch.device("cpu")
    return torch.device("mps" if torch.backends.mps.is_available() else "cpu")


def _letterbox(image: Any) -> Any:
    from PIL import Image

    image.thumbnail((224, 224), Image.Resampling.BILINEAR)
    canvas = Image.new("RGB", (224, 224), (128, 128, 128))
    offset = ((224 - image.width) // 2, (224 - image.height) // 2)
    canvas.paste(image, offset)
    return canvas


class PilotDataset:
    def __init__(self, samples: Sequence[PilotSample], augment: bool):
        from torchvision.transforms import (
            ColorJitter,
            Compose,
            Normalize,
            RandomHorizontalFlip,
            ToTensor,
        )

        self.samples = list(samples)
        transforms: list[Any] = []
        if augment:
            transforms.extend(
                [
                    RandomHorizontalFlip(p=0.5),
                    ColorJitter(brightness=0.12, contrast=0.12, saturation=0.08),
                ]
            )
        transforms.extend(
            [
                ToTensor(),
                Normalize(mean=(0.485, 0.456, 0.406), std=(0.229, 0.224, 0.225)),
            ]
        )
        self.transform = Compose(transforms)

    def __len__(self) -> int:
        return len(self.samples)

    def __getitem__(self, index: int) -> tuple[Any, int]:
        from PIL import Image, ImageOps

        sample = self.samples[index]
        with Image.open(sample.image_path) as opened:
            image = ImageOps.exif_transpose(opened).convert("RGB")
            image = _letterbox(image)
            return self.transform(image), sample.target


def _build_model(weights_cache: Path) -> tuple[Any, dict[str, str]]:
    import torch
    from torch import nn
    from torchvision.models import MobileNet_V3_Small_Weights, mobilenet_v3_small

    torch.hub.set_dir(str(weights_cache))
    weights = MobileNet_V3_Small_Weights.DEFAULT
    base = mobilenet_v3_small(weights=weights)

    class MobileNetBinary(nn.Module):
        def __init__(self) -> None:
            super().__init__()
            self.features = base.features
            self.dropout = nn.Dropout(p=0.2)
            self.classifier = nn.Linear(576 * 2, 2)

        def forward(self, inputs: Any) -> Any:
            feature_maps = self.features(inputs)
            average = nn.functional.adaptive_avg_pool2d(feature_maps, 1).flatten(1)
            maximum = nn.functional.adaptive_max_pool2d(feature_maps, 1).flatten(1)
            return self.classifier(self.dropout(torch.cat((average, maximum), dim=1)))

    weights_path = weights_cache / "checkpoints" / Path(urlparse(weights.url).path).name
    if not weights_path.is_file():
        raise RuntimeError(f"downloaded weights are missing: {weights_path}")
    return MobileNetBinary(), {
        "weights_url": weights.url,
        "weights_sha256": _sha256(weights_path),
    }


def _freeze_for_head(model: Any) -> None:
    for parameter in model.features.parameters():
        parameter.requires_grad = False
    for parameter in model.classifier.parameters():
        parameter.requires_grad = True


def _unfreeze_last_layers(model: Any) -> None:
    import torch

    for parameter in model.features.parameters():
        parameter.requires_grad = False
    for layer in model.features[10:]:
        for parameter in layer.parameters():
            parameter.requires_grad = True
    for module in model.features.modules():
        if isinstance(module, torch.nn.BatchNorm2d):
            module.eval()
            for parameter in module.parameters():
                parameter.requires_grad = False


def _keep_batch_norm_frozen(model: Any) -> None:
    import torch

    for module in model.features.modules():
        if isinstance(module, torch.nn.BatchNorm2d):
            module.eval()


def _train_epochs(
    model: Any,
    loader: Any,
    optimizer: Any,
    loss_function: Any,
    device: Any,
    epochs: int,
) -> list[float]:
    losses: list[float] = []
    for _ in range(epochs):
        model.train()
        _keep_batch_norm_frozen(model)
        total_loss = 0.0
        batches = 0
        for inputs, targets in loader:
            inputs = inputs.to(device)
            targets = targets.to(device)
            optimizer.zero_grad(set_to_none=True)
            loss = loss_function(model(inputs), targets)
            loss.backward()
            optimizer.step()
            total_loss += float(loss.detach().cpu())
            batches += 1
        losses.append(round(total_loss / max(batches, 1), 6))
    return losses


def _predict(model: Any, loader: Any, device: Any) -> Any:
    import numpy as np
    import torch

    probabilities: list[Any] = []
    model.eval()
    with torch.inference_mode():
        for inputs, _ in loader:
            logits = model(inputs.to(device))
            probabilities.append(torch.softmax(logits, dim=1)[:, 1].cpu().numpy())
    return np.concatenate(probabilities)


def run_finetune(
    samples: Sequence[PilotSample],
    weights_cache: Path,
    *,
    seed: int,
    batch_size: int,
    head_epochs: int,
    finetune_epochs: int,
    requested_device: str,
) -> dict[str, Any]:
    import numpy as np
    import torch
    from sklearn.model_selection import StratifiedKFold
    from torch.utils.data import DataLoader

    targets = np.asarray([sample.target for sample in samples], dtype=np.int64)
    splitter = StratifiedKFold(n_splits=5, shuffle=True, random_state=seed)
    probabilities = np.zeros(len(samples), dtype=np.float64)
    fold_summaries: list[dict[str, Any]] = []
    device = _device(requested_device)
    provenance: dict[str, str] | None = None

    for fold, (train_indices, test_indices) in enumerate(
        splitter.split(np.zeros(len(samples)), targets),
        start=1,
    ):
        fold_seed = seed + fold
        _seed_everything(fold_seed)
        model, fold_provenance = _build_model(weights_cache)
        provenance = provenance or fold_provenance
        if provenance != fold_provenance:
            raise RuntimeError("model weights changed between folds")
        model.to(device)

        training_samples = [samples[index] for index in train_indices]
        test_samples = [samples[index] for index in test_indices]
        generator = torch.Generator().manual_seed(fold_seed)
        training_loader = DataLoader(
            PilotDataset(training_samples, augment=True),
            batch_size=batch_size,
            shuffle=True,
            num_workers=0,
            generator=generator,
        )
        test_loader = DataLoader(
            PilotDataset(test_samples, augment=False),
            batch_size=batch_size,
            shuffle=False,
            num_workers=0,
        )

        train_counts = np.bincount(targets[train_indices], minlength=2)
        class_weights = len(train_indices) / (2.0 * train_counts)
        loss_function = torch.nn.CrossEntropyLoss(
            weight=torch.tensor(class_weights, dtype=torch.float32, device=device),
            label_smoothing=0.03,
        )

        _freeze_for_head(model)
        head_optimizer = torch.optim.AdamW(
            model.classifier.parameters(),
            lr=8e-4,
            weight_decay=1e-3,
        )
        head_losses = _train_epochs(
            model,
            training_loader,
            head_optimizer,
            loss_function,
            device,
            head_epochs,
        )

        _unfreeze_last_layers(model)
        visual_parameters = [
            parameter for parameter in model.features.parameters() if parameter.requires_grad
        ]
        finetune_optimizer = torch.optim.AdamW(
            [
                {"params": visual_parameters, "lr": 1e-5},
                {"params": model.classifier.parameters(), "lr": 1.5e-4},
            ],
            weight_decay=1e-3,
        )
        finetune_losses = _train_epochs(
            model,
            training_loader,
            finetune_optimizer,
            loss_function,
            device,
            finetune_epochs,
        )

        probabilities[test_indices] = _predict(model, test_loader, device)
        fold_summaries.append(
            {
                "fold": fold,
                "train": len(train_indices),
                "test": len(test_indices),
                "head_losses": head_losses,
                "finetune_losses": finetune_losses,
            }
        )
        del model
        if device.type == "mps":
            torch.mps.empty_cache()

    return {
        "device": device.type,
        "provenance": provenance,
        "probabilities": probabilities,
        "folds": fold_summaries,
    }


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--review", action="append", required=True, type=Path)
    parser.add_argument("--items", action="append", required=True, type=Path)
    parser.add_argument("--public-dir", action="append", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--weights-cache", required=True, type=Path)
    parser.add_argument("--device", choices=("auto", "mps", "cpu"), default="auto")
    parser.add_argument("--batch-size", type=int, default=16)
    parser.add_argument("--head-epochs", type=int, default=4)
    parser.add_argument("--finetune-epochs", type=int, default=6)
    parser.add_argument("--seed", type=int, default=DEFAULT_SEED)
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    arguments = _parser().parse_args(argv)
    if not (
        len(arguments.review)
        == len(arguments.items)
        == len(arguments.public_dir)
    ):
        print("error: review, items, and public-dir counts must match", file=sys.stderr)
        return 2
    if not 1 <= arguments.batch_size <= 64:
        print("error: batch-size must be between 1 and 64", file=sys.stderr)
        return 2
    if not 1 <= arguments.head_epochs <= 20:
        print("error: head-epochs must be between 1 and 20", file=sys.stderr)
        return 2
    if not 1 <= arguments.finetune_epochs <= 20:
        print("error: finetune-epochs must be between 1 and 20", file=sys.stderr)
        return 2

    try:
        sample_sets = [
            load_samples(review, items, public_dir)
            for review, items, public_dir in zip(
                arguments.review,
                arguments.items,
                arguments.public_dir,
            )
        ]
        samples = [sample for sample_set in sample_sets for sample in sample_set]
        sample_ids = [sample.sample_id for sample in samples]
        if len(set(sample_ids)) != len(sample_ids):
            raise RuntimeError("sample IDs overlap between review rounds")

        result = run_finetune(
            samples,
            arguments.weights_cache,
            seed=arguments.seed,
            batch_size=arguments.batch_size,
            head_epochs=arguments.head_epochs,
            finetune_epochs=arguments.finetune_epochs,
            requested_device=arguments.device,
        )
        import numpy as np

        targets = np.asarray([sample.target for sample in samples], dtype=np.int64)
        probabilities = result["probabilities"]
        report = {
            "schema_version": REPORT_SCHEMA_VERSION,
            "created_at": datetime.now(timezone.utc).isoformat(),
            "status": "research_only_not_approved_for_apk",
            "samples": len(samples),
            "allow": int((targets == 0).sum()),
            "filter": int((targets == 1).sum()),
            "model": {
                "name": "mobilenet-v3-small-bounded-last-layer-finetune",
                "device": result["device"],
                "input": "224x224 RGB complete-image gray letterbox",
                "pooling": "average-max",
                "head_epochs": arguments.head_epochs,
                "finetune_epochs": arguments.finetune_epochs,
                "unfrozen_feature_layers": "10..12",
                **(result["provenance"] or {}),
            },
            "evaluation": {
                "protocol": "stratified-5-fold-out-of-fold",
                "seed": arguments.seed,
                "folds": result["folds"],
                "important_limitation": (
                    "The 140-image pilot remains too small and lacks an independent third "
                    "round; these metrics cannot approve APK enforcement."
                ),
            },
            "thresholds": {
                "fixed_0_50": {
                    "metrics": _classification_metrics(targets, probabilities, 0.5),
                    "cases": _case_list(samples, targets, probabilities, 0.5),
                },
                "conservative_0_35": {
                    "metrics": _classification_metrics(targets, probabilities, 0.35),
                    "cases": _case_list(samples, targets, probabilities, 0.35),
                },
            },
            "predictions": [
                {
                    "sample_id": sample.sample_id,
                    "source": sample.source,
                    "human_action": sample.action,
                    "binary_target": "allow" if sample.target == 0 else "filter",
                    "filter_probability": round(float(probability), 6),
                }
                for sample, probability in zip(samples, probabilities)
            ],
        }
        arguments.output.parent.mkdir(parents=True, exist_ok=True)
        arguments.output.write_text(
            json.dumps(report, indent=2, ensure_ascii=False) + "\n",
            encoding="utf-8",
        )
    except (OSError, RuntimeError, ValueError) as error:
        print(f"error: {error}", file=sys.stderr)
        return 1

    print(
        json.dumps(
            {
                "output": str(arguments.output),
                "device": result["device"],
                "fixed_0_50": report["thresholds"]["fixed_0_50"]["metrics"],
                "conservative_0_35": report["thresholds"]["conservative_0_35"]["metrics"],
            },
            indent=2,
        )
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
