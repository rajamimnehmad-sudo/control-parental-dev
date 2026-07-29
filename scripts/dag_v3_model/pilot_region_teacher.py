#!/usr/bin/env python3
"""Evaluate body-region teacher features without producing an Android model."""

from __future__ import annotations

import argparse
import json
import sys
import time
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


REPORT_SCHEMA_VERSION = "dag-v3-region-teacher-pilot-v1"
DEFAULT_DETECTION_THRESHOLD = 0.35
DEFAULT_POLICY_THRESHOLD = 0.40
MAX_PEOPLE_LIMIT = 8


def _safe_crop(
    image: Any,
    box: Sequence[float],
    top_fraction: float,
    bottom_fraction: float,
    *,
    horizontal_padding: float = 0.05,
) -> Any:
    if not 0.0 <= top_fraction < bottom_fraction <= 1.0:
        raise ValueError("crop fractions must satisfy 0 <= top < bottom <= 1")
    width, height = image.size
    x1, y1, x2, y2 = (float(value) for value in box)
    box_width = max(1.0, x2 - x1)
    box_height = max(1.0, y2 - y1)
    left = max(0, int(x1 - horizontal_padding * box_width))
    right = min(width, int(x2 + horizontal_padding * box_width))
    top = max(0, int(y1 + top_fraction * box_height))
    bottom = min(height, int(y1 + bottom_fraction * box_height))
    if right <= left or bottom <= top:
        return image.copy()
    return image.crop((left, top, right, bottom))


def _letterbox(image: Any, transform: Any) -> Any:
    from PIL import Image

    resized = image.copy()
    resized.thumbnail((224, 224), Image.Resampling.BILINEAR)
    canvas = Image.new("RGB", (224, 224), (128, 128, 128))
    canvas.paste(resized, ((224 - resized.width) // 2, (224 - resized.height) // 2))
    return transform(canvas)


def _feature_batch(images: Sequence[Any], feature_model: Any, transform: Any) -> Any:
    import numpy as np
    import torch
    from torch import nn

    with torch.inference_mode():
        batch = torch.stack([_letterbox(image, transform) for image in images])
        maps = feature_model(batch)
        average = nn.functional.adaptive_avg_pool2d(maps, 1).flatten(1)
        maximum = nn.functional.adaptive_max_pool2d(maps, 1).flatten(1)
        features = torch.cat((average, maximum), dim=1).cpu().numpy()
    norms = np.linalg.norm(features, axis=1, keepdims=True)
    return features / np.clip(norms, 1e-12, None)


def extract_region_features(
    samples: Sequence[PilotSample],
    weights_cache: Path,
    *,
    detection_threshold: float,
    max_people: int,
    batch_size: int,
) -> tuple[dict[str, Any], dict[str, Any]]:
    import numpy as np
    import torch
    from PIL import Image, ImageOps
    from torchvision.models import MobileNet_V3_Small_Weights, mobilenet_v3_small
    from torchvision.models.detection import (
        SSDLite320_MobileNet_V3_Large_Weights,
        ssdlite320_mobilenet_v3_large,
    )
    from torchvision.transforms import Compose, Normalize, ToTensor
    from torchvision.transforms.functional import pil_to_tensor

    torch.hub.set_dir(str(weights_cache))
    feature_weights = MobileNet_V3_Small_Weights.DEFAULT
    feature_model = mobilenet_v3_small(weights=feature_weights).features.eval()
    detector_weights = SSDLite320_MobileNet_V3_Large_Weights.DEFAULT
    detector = ssdlite320_mobilenet_v3_large(weights=detector_weights).eval()
    person_label = detector_weights.meta["categories"].index("person")
    transform = Compose(
        [
            ToTensor(),
            Normalize(mean=(0.485, 0.456, 0.406), std=(0.229, 0.224, 0.225)),
        ]
    )

    rows: dict[str, list[Any]] = {
        "whole": [],
        "person": [],
        "upper": [],
        "lower": [],
    }
    detection_counts: list[int] = []
    for start in range(0, len(samples), batch_size):
        opened_images: list[Any] = []
        for sample in samples[start : start + batch_size]:
            with Image.open(sample.image_path) as opened:
                opened_images.append(ImageOps.exif_transpose(opened).convert("RGB"))
        detector_inputs = [pil_to_tensor(image).float() / 255.0 for image in opened_images]
        with torch.inference_mode():
            detections = detector(detector_inputs)
        whole_features = _feature_batch(opened_images, feature_model, transform)

        for image, detection, whole_feature in zip(
            opened_images,
            detections,
            whole_features,
        ):
            keep = (detection["labels"] == person_label) & (
                detection["scores"] >= detection_threshold
            )
            boxes = detection["boxes"][keep]
            scores = detection["scores"][keep]
            if len(boxes):
                areas = (boxes[:, 2] - boxes[:, 0]) * (boxes[:, 3] - boxes[:, 1])
                order = torch.argsort(areas * scores, descending=True)[:max_people]
                boxes = boxes[order]
            detection_counts.append(int(len(boxes)))
            rows["whole"].append(whole_feature)

            views = {
                "person": [_safe_crop(image, box, 0.0, 1.0) for box in boxes],
                "upper": [_safe_crop(image, box, 0.05, 0.68) for box in boxes],
                "lower": [_safe_crop(image, box, 0.45, 1.0) for box in boxes],
            }
            for name, crops in views.items():
                if not crops:
                    crops = [image]
                crop_features = _feature_batch(crops, feature_model, transform)
                rows[name].append(crop_features.max(axis=0))

    feature_path = weights_cache / "checkpoints" / Path(
        urlparse(feature_weights.url).path
    ).name
    detector_path = weights_cache / "checkpoints" / Path(
        urlparse(detector_weights.url).path
    ).name
    return {name: np.stack(values) for name, values in rows.items()}, {
        "images_with_person": sum(count > 0 for count in detection_counts),
        "images_without_person": sum(count == 0 for count in detection_counts),
        "mean_people": round(float(np.mean(detection_counts)), 3),
        "max_people_detected_after_cap": max(detection_counts, default=0),
        "feature_weights_url": feature_weights.url,
        "feature_weights_sha256": _sha256(feature_path),
        "detector_weights_url": detector_weights.url,
        "detector_weights_sha256": _sha256(detector_path),
    }


def select_candidate(
    samples: Sequence[PilotSample],
    feature_blocks: dict[str, Any],
    *,
    seed: int,
) -> tuple[dict[str, Any], list[dict[str, Any]]]:
    import numpy as np
    from sklearn.linear_model import LogisticRegression
    from sklearn.model_selection import StratifiedKFold, cross_val_predict
    from sklearn.pipeline import make_pipeline
    from sklearn.preprocessing import StandardScaler

    variants = {
        "whole": ("whole",),
        "whole_person": ("whole", "person"),
        "whole_upper": ("whole", "upper"),
        "whole_regions": ("whole", "upper", "lower"),
        "whole_all": ("whole", "person", "upper", "lower"),
        "regions_only": ("upper", "lower"),
    }
    targets = np.asarray([sample.target for sample in samples], dtype=np.int64)
    splitter = StratifiedKFold(n_splits=5, shuffle=True, random_state=seed)
    candidates: list[dict[str, Any]] = []
    for variant, names in variants.items():
        matrix = np.concatenate([feature_blocks[name] for name in names], axis=1)
        for regularization in (0.003, 0.01, 0.03, 0.1):
            classifier = make_pipeline(
                StandardScaler(),
                LogisticRegression(
                    C=regularization,
                    class_weight="balanced",
                    max_iter=3_000,
                    random_state=seed,
                ),
            )
            probabilities = cross_val_predict(
                classifier,
                matrix,
                targets,
                cv=splitter,
                method="predict_proba",
            )[:, 1]
            metrics = _classification_metrics(
                targets,
                probabilities,
                DEFAULT_POLICY_THRESHOLD,
            )
            confusion = metrics["confusion_matrix"]
            candidates.append(
                {
                    "variant": variant,
                    "blocks": names,
                    "C": regularization,
                    "false_allow": confusion["filter_as_allow"],
                    "false_filter": confusion["allow_as_filter"],
                    "roc_auc": metrics["roc_auc"],
                    "average_precision": metrics["average_precision"],
                }
            )
    candidates.sort(
        key=lambda item: (
            item["false_allow"],
            item["false_filter"],
            -item["average_precision"],
        )
    )
    return candidates[0], candidates


def evaluate_validation(
    training_samples: Sequence[PilotSample],
    validation_samples: Sequence[PilotSample],
    all_blocks: dict[str, Any],
    selected: dict[str, Any],
    *,
    seed: int,
) -> dict[str, Any]:
    import numpy as np
    from sklearn.linear_model import LogisticRegression
    from sklearn.pipeline import make_pipeline
    from sklearn.preprocessing import StandardScaler

    training_count = len(training_samples)
    matrix = np.concatenate(
        [all_blocks[name] for name in selected["blocks"]],
        axis=1,
    )
    training_targets = np.asarray(
        [sample.target for sample in training_samples],
        dtype=np.int64,
    )
    validation_targets = np.asarray(
        [sample.target for sample in validation_samples],
        dtype=np.int64,
    )
    classifier = make_pipeline(
        StandardScaler(),
        LogisticRegression(
            C=selected["C"],
            class_weight="balanced",
            max_iter=3_000,
            random_state=seed,
        ),
    )
    classifier.fit(matrix[:training_count], training_targets)
    probabilities = classifier.predict_proba(matrix[training_count:])[:, 1]
    thresholds = {}
    for threshold in (0.50, 0.40, 0.35, 0.25):
        thresholds[str(threshold)] = {
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
    return {
        "thresholds": thresholds,
        "predictions": [
            {
                "sample_id": sample.sample_id,
                "human_action": sample.action,
                "filter_probability": round(float(probability), 6),
            }
            for sample, probability in zip(validation_samples, probabilities)
        ],
    }


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--review", type=Path, action="append", required=True)
    parser.add_argument("--items", type=Path, action="append", required=True)
    parser.add_argument("--public-dir", type=Path, action="append", required=True)
    parser.add_argument("--validation-review", type=Path, required=True)
    parser.add_argument("--validation-items", type=Path, required=True)
    parser.add_argument("--validation-public-dir", type=Path, required=True)
    parser.add_argument("--weights-cache", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--batch-size", type=int, default=8)
    parser.add_argument("--max-people", type=int, default=4)
    parser.add_argument(
        "--detection-threshold",
        type=float,
        default=DEFAULT_DETECTION_THRESHOLD,
    )
    parser.add_argument("--seed", type=int, default=DEFAULT_SEED)
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    arguments = _parser().parse_args(argv)
    if not (
        len(arguments.review)
        == len(arguments.items)
        == len(arguments.public_dir)
    ):
        print("error: each training round requires review, items, and public-dir", file=sys.stderr)
        return 2
    if not 1 <= arguments.batch_size <= 32:
        print("error: --batch-size must be between 1 and 32", file=sys.stderr)
        return 2
    if not 1 <= arguments.max_people <= MAX_PEOPLE_LIMIT:
        print(f"error: --max-people must be between 1 and {MAX_PEOPLE_LIMIT}", file=sys.stderr)
        return 2
    if not 0.05 <= arguments.detection_threshold <= 0.95:
        print("error: --detection-threshold must be between 0.05 and 0.95", file=sys.stderr)
        return 2

    started = time.perf_counter()
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
        all_samples = [*training_samples, *validation_samples]
        blocks, detector_report = extract_region_features(
            all_samples,
            arguments.weights_cache,
            detection_threshold=arguments.detection_threshold,
            max_people=arguments.max_people,
            batch_size=arguments.batch_size,
        )
        training_blocks = {
            name: values[: len(training_samples)] for name, values in blocks.items()
        }
        selected, candidates = select_candidate(
            training_samples,
            training_blocks,
            seed=arguments.seed,
        )
        validation = evaluate_validation(
            training_samples,
            validation_samples,
            blocks,
            selected,
            seed=arguments.seed,
        )
        report = {
            "schema_version": REPORT_SCHEMA_VERSION,
            "status": "research_teacher_only_not_approved_for_android",
            "single_model_goal": True,
            "training_samples": len(training_samples),
            "validation_samples": len(validation_samples),
            "method": {
                "detector_role": "training-time region teacher only",
                "detector_score_threshold": arguments.detection_threshold,
                "max_people_per_image": arguments.max_people,
                "policy_threshold_candidate": DEFAULT_POLICY_THRESHOLD,
                "candidate_selection": (
                    "training-only five-fold cross-validation; minimize false allows, "
                    "then false filters, then maximize average precision"
                ),
            },
            "detector": detector_report,
            "selected_candidate": selected,
            "internal_candidates": candidates,
            "validation": validation,
            "elapsed_seconds": round(time.perf_counter() - started, 2),
        }
        arguments.output.parent.mkdir(parents=True, exist_ok=True)
        arguments.output.write_text(
            json.dumps(report, indent=2, ensure_ascii=False) + "\n",
            encoding="utf-8",
        )
    except (OSError, RuntimeError, ValueError) as error:
        print(f"error: {error}", file=sys.stderr)
        return 1

    summary = {
        "output": str(arguments.output),
        "selected_candidate": selected,
        "validation_0_40": validation["thresholds"]["0.4"]["metrics"],
        "elapsed_seconds": report["elapsed_seconds"],
    }
    print(json.dumps(summary, indent=2, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
