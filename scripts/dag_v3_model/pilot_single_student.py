#!/usr/bin/env python3
"""Train one region-supervised MobileNet student and test it on a frozen round."""

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
    DEFAULT_SEED,
    PilotSample,
    _case_list,
    _classification_metrics,
    _sha256,
    load_samples,
)


REPORT_SCHEMA_VERSION = "dag-v3-single-student-pilot-v1"
INPUT_SIZE = 224
ATTENTION_SIZE = 7
MAX_PEOPLE_LIMIT = 8


def upper_region(
    box: Sequence[float],
    image_width: int,
    image_height: int,
) -> tuple[float, float, float, float]:
    x1, y1, x2, y2 = (float(value) for value in box)
    width = max(1.0, x2 - x1)
    height = max(1.0, y2 - y1)
    return (
        max(0.0, x1 - 0.05 * width),
        max(0.0, y1 + 0.05 * height),
        min(float(image_width), x2 + 0.05 * width),
        min(float(image_height), y1 + 0.68 * height),
    )


def letterboxed_attention_cells(
    regions: Sequence[Sequence[float]],
    image_width: int,
    image_height: int,
    *,
    input_size: int = INPUT_SIZE,
    attention_size: int = ATTENTION_SIZE,
) -> list[list[float]]:
    if image_width <= 0 or image_height <= 0:
        raise ValueError("image dimensions must be positive")
    if input_size <= 0 or attention_size <= 0:
        raise ValueError("mask dimensions must be positive")
    scale = min(input_size / image_width, input_size / image_height)
    offset_x = (input_size - image_width * scale) / 2.0
    offset_y = (input_size - image_height * scale) / 2.0
    transformed = [
        (
            float(region[0]) * scale + offset_x,
            float(region[1]) * scale + offset_y,
            float(region[2]) * scale + offset_x,
            float(region[3]) * scale + offset_y,
        )
        for region in regions
    ]
    cell_size = input_size / attention_size
    mask: list[list[float]] = []
    for row in range(attention_size):
        values: list[float] = []
        center_y = (row + 0.5) * cell_size
        for column in range(attention_size):
            center_x = (column + 0.5) * cell_size
            active = any(
                left <= center_x <= right and top <= center_y <= bottom
                for left, top, right, bottom in transformed
            )
            values.append(1.0 if active else 0.0)
        mask.append(values)
    return mask


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


def detect_training_regions(
    samples: Sequence[PilotSample],
    weights_cache: Path,
    *,
    score_threshold: float,
    max_people: int,
    batch_size: int,
) -> tuple[dict[str, list[tuple[float, float, float, float]]], dict[str, Any]]:
    import torch
    from PIL import Image, ImageOps
    from torchvision.models.detection import (
        SSDLite320_MobileNet_V3_Large_Weights,
        ssdlite320_mobilenet_v3_large,
    )
    from torchvision.transforms.functional import pil_to_tensor

    torch.hub.set_dir(str(weights_cache))
    weights = SSDLite320_MobileNet_V3_Large_Weights.DEFAULT
    detector = ssdlite320_mobilenet_v3_large(weights=weights).eval()
    person_label = weights.meta["categories"].index("person")
    regions_by_id: dict[str, list[tuple[float, float, float, float]]] = {}
    detection_counts: list[int] = []

    for start in range(0, len(samples), batch_size):
        opened_images: list[Any] = []
        for sample in samples[start : start + batch_size]:
            with Image.open(sample.image_path) as opened:
                opened_images.append(ImageOps.exif_transpose(opened).convert("RGB"))
        tensors = [pil_to_tensor(image).float() / 255.0 for image in opened_images]
        with torch.inference_mode():
            detections = detector(tensors)
        for sample, image, detection in zip(
            samples[start : start + batch_size],
            opened_images,
            detections,
        ):
            keep = (detection["labels"] == person_label) & (
                detection["scores"] >= score_threshold
            )
            boxes = detection["boxes"][keep]
            scores = detection["scores"][keep]
            if len(boxes):
                areas = (boxes[:, 2] - boxes[:, 0]) * (boxes[:, 3] - boxes[:, 1])
                order = torch.argsort(areas * scores, descending=True)[:max_people]
                boxes = boxes[order]
            detected_regions = [
                upper_region(box.tolist(), image.width, image.height) for box in boxes
            ]
            regions_by_id[sample.sample_id] = detected_regions
            detection_counts.append(len(detected_regions))

    weights_path = weights_cache / "checkpoints" / Path(urlparse(weights.url).path).name
    return regions_by_id, {
        "role": "training_only_attention_supervision",
        "weights_url": weights.url,
        "weights_sha256": _sha256(weights_path),
        "images": len(samples),
        "images_with_person": sum(count > 0 for count in detection_counts),
        "images_without_person": sum(count == 0 for count in detection_counts),
        "mean_people": round(sum(detection_counts) / max(len(detection_counts), 1), 3),
        "max_people_after_cap": max(detection_counts, default=0),
    }


def _letterbox(image: Any) -> Any:
    from PIL import Image

    resized = image.copy()
    resized.thumbnail((INPUT_SIZE, INPUT_SIZE), Image.Resampling.BILINEAR)
    canvas = Image.new("RGB", (INPUT_SIZE, INPUT_SIZE), (128, 128, 128))
    canvas.paste(
        resized,
        ((INPUT_SIZE - resized.width) // 2, (INPUT_SIZE - resized.height) // 2),
    )
    return canvas


class StudentDataset:
    def __init__(
        self,
        samples: Sequence[PilotSample],
        regions_by_id: dict[str, list[tuple[float, float, float, float]]],
        *,
        augment: bool,
        teacher_probabilities_by_id: dict[str, float] | None = None,
    ) -> None:
        from torchvision.transforms import ColorJitter, Compose, Normalize, ToTensor

        self.samples = list(samples)
        self.regions_by_id = regions_by_id
        self.augment = augment
        self.teacher_probabilities_by_id = teacher_probabilities_by_id or {}
        self.color_jitter = ColorJitter(brightness=0.12, contrast=0.12, saturation=0.08)
        self.transform = Compose(
            [
                ToTensor(),
                Normalize(mean=(0.485, 0.456, 0.406), std=(0.229, 0.224, 0.225)),
            ]
        )

    def __len__(self) -> int:
        return len(self.samples)

    def __getitem__(self, index: int) -> tuple[Any, int, Any, float, float]:
        import torch
        from PIL import Image, ImageOps

        sample = self.samples[index]
        with Image.open(sample.image_path) as opened:
            image = ImageOps.exif_transpose(opened).convert("RGB")
            regions = self.regions_by_id.get(sample.sample_id, [])
            mask = letterboxed_attention_cells(regions, image.width, image.height)
            image = _letterbox(image)
        mask_tensor = torch.tensor(mask, dtype=torch.float32)
        if self.augment:
            image = self.color_jitter(image)
            if torch.rand(()) < 0.5:
                image = ImageOps.mirror(image)
                mask_tensor = torch.flip(mask_tensor, dims=(1,))
        return (
            self.transform(image),
            sample.target,
            mask_tensor,
            1.0 if regions and mask_tensor.sum() > 0 else 0.0,
            self.teacher_probabilities_by_id.get(
                sample.sample_id,
                float(sample.target),
            ),
        )


def build_student(weights_cache: Path) -> tuple[Any, dict[str, str]]:
    import torch
    from torch import nn
    from torchvision.models import MobileNet_V3_Small_Weights, mobilenet_v3_small

    torch.hub.set_dir(str(weights_cache))
    weights = MobileNet_V3_Small_Weights.DEFAULT
    base = mobilenet_v3_small(weights=weights)

    class RegionSupervisedStudent(nn.Module):
        def __init__(self) -> None:
            super().__init__()
            self.features = base.features
            self.attention = nn.Conv2d(576, 1, kernel_size=1)
            self.dropout = nn.Dropout(p=0.2)
            self.classifier = nn.Linear(576 * 2, 2)

        def forward(self, inputs: Any) -> tuple[Any, Any]:
            maps = self.features(inputs)
            attention_logits = self.attention(maps).flatten(1)
            attention_weights = torch.softmax(attention_logits, dim=1).unsqueeze(1)
            flattened_maps = maps.flatten(2)
            attended = (flattened_maps * attention_weights).sum(dim=2)
            maximum = nn.functional.adaptive_max_pool2d(maps, 1).flatten(1)
            logits = self.classifier(self.dropout(torch.cat((attended, maximum), dim=1)))
            return logits, attention_logits

    weights_path = weights_cache / "checkpoints" / Path(urlparse(weights.url).path).name
    return RegionSupervisedStudent(), {
        "weights_url": weights.url,
        "weights_sha256": _sha256(weights_path),
    }


def _freeze_for_head(model: Any) -> None:
    for parameter in model.features.parameters():
        parameter.requires_grad = False
    for parameter in model.attention.parameters():
        parameter.requires_grad = True
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
    classification_loss: Any,
    device: Any,
    *,
    epochs: int,
    attention_weight: float,
    distillation_weight: float,
) -> list[dict[str, float]]:
    import torch

    summaries: list[dict[str, float]] = []
    for _ in range(epochs):
        model.train()
        _keep_batch_norm_frozen(model)
        classification_total = 0.0
        distillation_total = 0.0
        attention_total = 0.0
        batches = 0
        for inputs, targets, masks, has_masks, teacher_probabilities in loader:
            inputs = inputs.to(device)
            targets = targets.to(device)
            masks = masks.to(device).flatten(1)
            has_masks = has_masks.to(device=device, dtype=torch.float32)
            teacher_probabilities = teacher_probabilities.to(
                device=device,
                dtype=torch.float32,
            )
            optimizer.zero_grad(set_to_none=True)
            logits, attention_logits = model(inputs)
            class_loss = classification_loss(logits, targets)
            distillation_loss = torch.nn.functional.binary_cross_entropy_with_logits(
                logits[:, 1] - logits[:, 0],
                teacher_probabilities,
            )
            active = has_masks > 0
            if bool(active.any()):
                active_masks = masks[active]
                target_distribution = active_masks / active_masks.sum(
                    dim=1,
                    keepdim=True,
                ).clamp_min(1.0)
                log_attention = torch.log_softmax(attention_logits[active], dim=1)
                region_loss = -(target_distribution * log_attention).sum(dim=1).mean()
            else:
                region_loss = torch.zeros((), device=device)
            loss = (
                (1.0 - distillation_weight) * class_loss
                + distillation_weight * distillation_loss
                + attention_weight * region_loss
            )
            loss.backward()
            optimizer.step()
            classification_total += float(class_loss.detach().cpu())
            distillation_total += float(distillation_loss.detach().cpu())
            attention_total += float(region_loss.detach().cpu())
            batches += 1
        summaries.append(
            {
                "classification": round(classification_total / max(batches, 1), 6),
                "distillation": round(distillation_total / max(batches, 1), 6),
                "attention": round(attention_total / max(batches, 1), 6),
            }
        )
    return summaries


def _predict(model: Any, loader: Any, device: Any) -> Any:
    import numpy as np
    import torch

    probabilities: list[Any] = []
    model.eval()
    with torch.inference_mode():
        for inputs, _, _, _, _ in loader:
            logits, _ = model(inputs.to(device))
            probabilities.append(torch.softmax(logits, dim=1)[:, 1].cpu().numpy())
    return np.concatenate(probabilities)


def modern_allow_sampling_indices(
    training_samples: Sequence[PilotSample],
    emphasized_sample_ids: set[str],
    emphasis: float,
    *,
    seed: int,
) -> list[int] | None:
    """Swap older allows for recent allows while retaining every filter sample."""
    if emphasis < 1.0:
        raise ValueError("modern allow emphasis must be at least 1")
    emphasized_indices = [
        index
        for index, sample in enumerate(training_samples)
        if sample.target == 0 and sample.sample_id in emphasized_sample_ids
    ]
    older_allow_indices = [
        index
        for index, sample in enumerate(training_samples)
        if sample.target == 0 and sample.sample_id not in emphasized_sample_ids
    ]
    if not emphasized_indices or emphasis == 1.0:
        return None
    if not older_allow_indices:
        raise ValueError("modern allow emphasis requires older allow examples")
    replacement_count = round((emphasis - 1.0) * len(emphasized_indices))
    if replacement_count > len(older_allow_indices):
        maximum = 1.0 + len(older_allow_indices) / len(emphasized_indices)
        raise ValueError(
            f"modern allow emphasis must not exceed {maximum:.3f} for this dataset"
        )
    generator = random.Random(seed)
    removed = set(generator.sample(older_allow_indices, replacement_count))
    repeated = [
        emphasized_indices[index % len(emphasized_indices)]
        for index in range(replacement_count)
    ]
    indices = [
        index for index in range(len(training_samples)) if index not in removed
    ] + repeated
    generator.shuffle(indices)
    return indices


def load_teacher_probabilities(
    report_path: Path,
    expected_sample_ids: set[str],
) -> tuple[dict[str, float], dict[str, Any]]:
    payload = json.loads(report_path.read_text(encoding="utf-8"))
    if payload.get("schema_version") != "dag-v3-siglip2-semantic-teacher-pilot-v1":
        raise ValueError("unsupported semantic teacher report schema")
    predictions = payload.get("training_soft_targets", {}).get("predictions")
    if not isinstance(predictions, list):
        raise ValueError("semantic teacher report has no training soft targets")
    probabilities: dict[str, float] = {}
    for prediction in predictions:
        sample_id = prediction.get("sample_id")
        probability = prediction.get("filter_probability")
        if (
            not isinstance(sample_id, str)
            or not isinstance(probability, (int, float))
            or not 0.0 <= float(probability) <= 1.0
        ):
            raise ValueError("invalid semantic teacher probability")
        if sample_id in probabilities:
            raise ValueError(f"duplicate semantic teacher sample: {sample_id}")
        probabilities[sample_id] = float(probability)
    if set(probabilities) != expected_sample_ids:
        raise ValueError("semantic teacher samples do not exactly match training")
    teacher = payload.get("teacher", {})
    return probabilities, {
        "report_sha256": _sha256(report_path),
        "model_id": teacher.get("model_id"),
        "weights_sha256": teacher.get("weights_sha256"),
        "declared_license": teacher.get("declared_license"),
        "soft_target_protocol": payload["training_soft_targets"].get("protocol"),
    }


def train_and_evaluate(
    training_samples: Sequence[PilotSample],
    validation_samples: Sequence[PilotSample],
    regions_by_id: dict[str, list[tuple[float, float, float, float]]],
    weights_cache: Path,
    checkpoint_path: Path,
    sampling_indices: Sequence[int] | None,
    *,
    teacher_probabilities_by_id: dict[str, float] | None,
    distillation_weight: float,
    seed: int,
    batch_size: int,
    head_epochs: int,
    finetune_epochs: int,
    attention_weight: float,
    requested_device: str,
) -> dict[str, Any]:
    import numpy as np
    import torch
    from torch.utils.data import DataLoader

    _seed_everything(seed)
    device = _device(requested_device)
    model, provenance = build_student(weights_cache)
    model.to(device)
    generator = torch.Generator().manual_seed(seed)
    training_loader = DataLoader(
        StudentDataset(
            training_samples,
            regions_by_id,
            augment=True,
            teacher_probabilities_by_id=teacher_probabilities_by_id,
        ),
        batch_size=batch_size,
        shuffle=sampling_indices is None,
        sampler=sampling_indices,
        num_workers=0,
        generator=generator if sampling_indices is None else None,
    )
    validation_loader = DataLoader(
        StudentDataset(validation_samples, {}, augment=False),
        batch_size=batch_size,
        shuffle=False,
        num_workers=0,
    )
    training_targets = np.asarray(
        [sample.target for sample in training_samples],
        dtype=np.int64,
    )
    counts = np.bincount(training_targets, minlength=2)
    class_weights = len(training_targets) / (2.0 * counts)
    classification_loss = torch.nn.CrossEntropyLoss(
        weight=torch.tensor(class_weights, dtype=torch.float32, device=device),
        label_smoothing=0.03,
    )

    _freeze_for_head(model)
    head_optimizer = torch.optim.AdamW(
        [*model.attention.parameters(), *model.classifier.parameters()],
        lr=8e-4,
        weight_decay=1e-3,
    )
    head_losses = _train_epochs(
        model,
        training_loader,
        head_optimizer,
        classification_loss,
        device,
        epochs=head_epochs,
        attention_weight=attention_weight,
        distillation_weight=distillation_weight,
    )

    _unfreeze_last_layers(model)
    visual_parameters = [
        parameter for parameter in model.features.parameters() if parameter.requires_grad
    ]
    finetune_optimizer = torch.optim.AdamW(
        [
            {"params": visual_parameters, "lr": 8e-6},
            {"params": model.attention.parameters(), "lr": 8e-5},
            {"params": model.classifier.parameters(), "lr": 1.2e-4},
        ],
        weight_decay=1e-3,
    )
    finetune_losses = _train_epochs(
        model,
        training_loader,
        finetune_optimizer,
        classification_loss,
        device,
        epochs=finetune_epochs,
        attention_weight=attention_weight,
        distillation_weight=distillation_weight,
    )
    probabilities = _predict(model, validation_loader, device)

    checkpoint_path.parent.mkdir(parents=True, exist_ok=True)
    torch.save(
        {
            "schema_version": REPORT_SCHEMA_VERSION,
            "state_dict": {key: value.detach().cpu() for key, value in model.state_dict().items()},
            "input_size": INPUT_SIZE,
            "labels": ["allow", "filter"],
            "single_model": True,
            "seed": seed,
            "provenance": provenance,
            "distillation_weight": distillation_weight,
        },
        checkpoint_path,
    )
    return {
        "device": device.type,
        "provenance": provenance,
        "head_losses": head_losses,
        "finetune_losses": finetune_losses,
        "probabilities": probabilities,
        "checkpoint_sha256": _sha256(checkpoint_path),
    }


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--review", action="append", required=True, type=Path)
    parser.add_argument("--items", action="append", required=True, type=Path)
    parser.add_argument("--public-dir", action="append", required=True, type=Path)
    parser.add_argument("--validation-review", required=True, type=Path)
    parser.add_argument("--validation-items", required=True, type=Path)
    parser.add_argument("--validation-public-dir", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--checkpoint", required=True, type=Path)
    parser.add_argument("--weights-cache", required=True, type=Path)
    parser.add_argument("--device", choices=("auto", "mps", "cpu"), default="auto")
    parser.add_argument("--batch-size", type=int, default=16)
    parser.add_argument("--head-epochs", type=int, default=6)
    parser.add_argument("--finetune-epochs", type=int, default=6)
    parser.add_argument("--attention-weight", type=float, default=0.15)
    parser.add_argument("--detection-threshold", type=float, default=0.35)
    parser.add_argument("--max-people", type=int, default=4)
    parser.add_argument("--modern-allow-emphasis", type=float, default=1.0)
    parser.add_argument("--teacher-report", type=Path)
    parser.add_argument("--distillation-weight", type=float, default=0.0)
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
    if not 0.0 <= arguments.attention_weight <= 1.0:
        print("error: attention-weight must be between 0 and 1", file=sys.stderr)
        return 2
    if not 0.05 <= arguments.detection_threshold <= 0.95:
        print("error: detection-threshold must be between 0.05 and 0.95", file=sys.stderr)
        return 2
    if not 1 <= arguments.max_people <= MAX_PEOPLE_LIMIT:
        print(f"error: max-people must be between 1 and {MAX_PEOPLE_LIMIT}", file=sys.stderr)
        return 2
    if not 1.0 <= arguments.modern_allow_emphasis <= 8.0:
        print("error: modern-allow-emphasis must be between 1 and 8", file=sys.stderr)
        return 2
    if not 0.0 <= arguments.distillation_weight <= 0.75:
        print("error: distillation-weight must be between 0 and 0.75", file=sys.stderr)
        return 2
    if (arguments.teacher_report is None) != (arguments.distillation_weight == 0.0):
        print(
            "error: teacher-report and a positive distillation-weight must be used together",
            file=sys.stderr,
        )
        return 2

    try:
        training_rounds = [
            load_samples(review, items, public_dir, skip_excluded=True)
            for review, items, public_dir in zip(
                arguments.review,
                arguments.items,
                arguments.public_dir,
            )
        ]
        training_samples = [
            sample for round_samples in training_rounds for sample in round_samples
        ]
        validation_samples = load_samples(
            arguments.validation_review,
            arguments.validation_items,
            arguments.validation_public_dir,
            skip_excluded=True,
        )
        training_ids = {sample.sample_id for sample in training_samples}
        validation_ids = {sample.sample_id for sample in validation_samples}
        if len(training_ids) != len(training_samples):
            raise RuntimeError("training sample IDs overlap between rounds")
        if training_ids & validation_ids:
            raise RuntimeError("training and validation sample IDs overlap")
        emphasized_sample_ids = {
            sample.sample_id
            for sample in training_rounds[-1]
            if sample.target == 0
        }
        sampling_indices = modern_allow_sampling_indices(
            training_samples,
            emphasized_sample_ids,
            arguments.modern_allow_emphasis,
            seed=arguments.seed,
        )
        teacher_probabilities: dict[str, float] | None = None
        teacher_provenance: dict[str, Any] | None = None
        if arguments.teacher_report is not None:
            teacher_probabilities, teacher_provenance = load_teacher_probabilities(
                arguments.teacher_report,
                training_ids,
            )

        regions, teacher_report = detect_training_regions(
            training_samples,
            arguments.weights_cache,
            score_threshold=arguments.detection_threshold,
            max_people=arguments.max_people,
            batch_size=min(arguments.batch_size, 8),
        )
        result = train_and_evaluate(
            training_samples,
            validation_samples,
            regions,
            arguments.weights_cache,
            arguments.checkpoint,
            sampling_indices,
            teacher_probabilities_by_id=teacher_probabilities,
            distillation_weight=arguments.distillation_weight,
            seed=arguments.seed,
            batch_size=arguments.batch_size,
            head_epochs=arguments.head_epochs,
            finetune_epochs=arguments.finetune_epochs,
            attention_weight=arguments.attention_weight,
            requested_device=arguments.device,
        )
        import numpy as np

        validation_targets = np.asarray(
            [sample.target for sample in validation_samples],
            dtype=np.int64,
        )
        probabilities = result["probabilities"]
        report = {
            "schema_version": REPORT_SCHEMA_VERSION,
            "created_at": datetime.now(timezone.utc).isoformat(),
            "status": "research_only_not_approved_for_apk",
            "single_model_at_inference": True,
            "training_samples": len(training_samples),
            "validation_samples": len(validation_samples),
            "model": {
                "name": "mobilenet-v3-small-spatial-attention-student",
                "device": result["device"],
                "input": "224x224 RGB complete-image gray letterbox",
                "outputs": ["allow", "filter"],
                "head_epochs": arguments.head_epochs,
                "finetune_epochs": arguments.finetune_epochs,
                "attention_supervision_weight": arguments.attention_weight,
                "detector_present_at_inference": False,
                **result["provenance"],
            },
            "training_region_teacher": teacher_report,
            "training": {
                "sampling": {
                    "modern_allow_emphasis": arguments.modern_allow_emphasis,
                    "emphasized_allow_samples": len(emphasized_sample_ids),
                    "class_mass_preserved": True,
                },
                "distillation": {
                    "weight": arguments.distillation_weight,
                    "human_labels_remain_primary": arguments.distillation_weight < 0.5,
                    "teacher": teacher_provenance,
                },
                "head_losses": result["head_losses"],
                "finetune_losses": result["finetune_losses"],
            },
            "checkpoint": {
                "path": str(arguments.checkpoint),
                "sha256": result["checkpoint_sha256"],
                "approved_for_export": False,
            },
            "evaluation": {
                "protocol": "frozen_round_3_validation_not_used_for_training",
                "important_limitation": (
                    "Round 3 already informed architecture research, so a new independent "
                    "test is still required before any APK decision."
                ),
            },
            "thresholds": {
                str(threshold): {
                    "metrics": _classification_metrics(
                        validation_targets,
                        probabilities,
                        threshold,
                    ),
                    "cases": _case_list(
                        validation_samples,
                        validation_targets,
                        probabilities,
                        threshold,
                    ),
                }
                for threshold in (0.50, 0.40, 0.35, 0.25)
            },
            "predictions": [
                {
                    "sample_id": sample.sample_id,
                    "source": sample.source,
                    "human_action": sample.action,
                    "binary_target": "allow" if sample.target == 0 else "filter",
                    "filter_probability": round(float(probability), 6),
                }
                for sample, probability in zip(validation_samples, probabilities)
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
                "checkpoint": str(arguments.checkpoint),
                "device": result["device"],
                "threshold_0_40": report["thresholds"]["0.4"]["metrics"],
            },
            indent=2,
            ensure_ascii=False,
        )
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
