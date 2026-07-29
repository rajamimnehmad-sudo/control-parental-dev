#!/usr/bin/env python3
"""Measure a local allow-vs-filter pilot without changing the Android runtime."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import random
import sys
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Sequence
from urllib.parse import urlparse


REVIEW_SCHEMA_VERSION = "dag-v3-human-policy-review-v1"
ITEMS_SCHEMA_VERSION = "dag-v3-blind-policy-review-items-v1"
REPORT_SCHEMA_VERSION = "dag-v3-pilot-binary-baseline-v1"
ALLOWED_ACTIONS = frozenset({"allow", "blur", "block"})
ALLOWED_IMAGE_DIRECTORIES = frozenset(
    {
        "review-images",
        "review-images-round-2",
        "review-images-round-3",
        "review-images-round-4",
        "review-images-round-5",
        "review-images-round-6",
        "review-images-round-7",
        "review-images-round-8",
        "review-images-round-9",
    }
)
MAX_INPUT_BYTES = 4 * 1024 * 1024
MAX_SAMPLES = 1_000
DEFAULT_SEED = 20260728


class BaselineInputError(ValueError):
    """Raised when the local pilot inputs are incomplete or inconsistent."""


@dataclass(frozen=True)
class PilotSample:
    sample_id: str
    image_path: Path
    source: str
    action: str

    @property
    def target(self) -> int:
        return 0 if self.action == "allow" else 1


def _read_json(path: Path) -> dict[str, Any]:
    if not path.is_file():
        raise BaselineInputError(f"missing input: {path}")
    if path.stat().st_size > MAX_INPUT_BYTES:
        raise BaselineInputError(f"input exceeds {MAX_INPUT_BYTES} bytes: {path}")
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        raise BaselineInputError(f"cannot read JSON {path}: {error}") from error
    if not isinstance(payload, dict):
        raise BaselineInputError(f"JSON root must be an object: {path}")
    return payload


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def load_samples(
    review_path: Path,
    items_path: Path,
    public_dir: Path,
    *,
    skip_excluded: bool = False,
    require_both_classes: bool = True,
) -> list[PilotSample]:
    review = _read_json(review_path)
    items_payload = _read_json(items_path)
    errors: list[str] = []

    if review.get("schema_version") != REVIEW_SCHEMA_VERSION:
        errors.append(f"review schema_version must be {REVIEW_SCHEMA_VERSION}")
    if review.get("completed") is not True:
        errors.append("review must be completed")
    rows = review.get("rows")
    if not isinstance(rows, list) or not rows or len(rows) > MAX_SAMPLES:
        errors.append(f"review rows must contain 1..{MAX_SAMPLES} samples")
        rows = []

    if items_payload.get("schemaVersion") != ITEMS_SCHEMA_VERSION:
        errors.append(f"items schemaVersion must be {ITEMS_SCHEMA_VERSION}")
    items = items_payload.get("items")
    if not isinstance(items, list) or not items or len(items) > MAX_SAMPLES:
        errors.append(f"items must contain 1..{MAX_SAMPLES} samples")
        items = []

    item_index: dict[str, dict[str, Any]] = {}
    for index, item in enumerate(items, start=1):
        if not isinstance(item, dict):
            errors.append(f"item {index} must be an object")
            continue
        sample_id = item.get("id")
        if not isinstance(sample_id, str) or not sample_id:
            errors.append(f"item {index} id must be a non-empty string")
            continue
        if sample_id in item_index:
            errors.append(f"duplicate item id: {sample_id}")
            continue
        item_index[sample_id] = item

    samples: list[PilotSample] = []
    seen_rows: set[str] = set()
    public_root = public_dir.resolve()
    for index, row in enumerate(rows, start=1):
        if not isinstance(row, dict):
            errors.append(f"review row {index} must be an object")
            continue
        sample_id = row.get("sample_id")
        if not isinstance(sample_id, str) or not sample_id:
            errors.append(f"review row {index} sample_id must be a non-empty string")
            continue
        if sample_id in seen_rows:
            errors.append(f"duplicate review sample_id: {sample_id}")
            continue
        seen_rows.add(sample_id)

        decision = row.get("human_decision")
        action = decision.get("action") if isinstance(decision, dict) else None
        is_excluded = action == "exclude" and skip_excluded
        if action not in ALLOWED_ACTIONS and not is_excluded:
            errors.append(
                f"{sample_id}: action must be allow, blur, or block for this baseline"
            )
            continue

        item = item_index.get(sample_id)
        if item is None:
            errors.append(f"{sample_id}: missing review item")
            continue
        relative_image = item.get("image")
        relative_path = (
            Path(relative_image.removeprefix("/"))
            if isinstance(relative_image, str)
            else Path()
        )
        if (
            not isinstance(relative_image, str)
            or not relative_image.startswith("/")
            or len(relative_path.parts) != 2
            or relative_path.parts[0] not in ALLOWED_IMAGE_DIRECTORIES
            or relative_path.name != relative_path.parts[1]
        ):
            errors.append(f"{sample_id}: unsafe image path")
            continue
        image_path = (public_root / relative_path).resolve()
        if public_root not in image_path.parents or not image_path.is_file():
            errors.append(f"{sample_id}: missing image file")
            continue
        source = item.get("source")
        if not isinstance(source, str) or not source:
            errors.append(f"{sample_id}: source must be a non-empty string")
            continue
        if is_excluded:
            continue
        samples.append(PilotSample(sample_id, image_path, source, action))

    if seen_rows != set(item_index):
        missing_review = sorted(set(item_index) - seen_rows)
        extra_review = sorted(seen_rows - set(item_index))
        if missing_review:
            errors.append(f"items without review: {', '.join(missing_review[:5])}")
        if extra_review:
            errors.append(f"reviews without item: {', '.join(extra_review[:5])}")
    if review.get("reviewed") != len(rows) or review.get("total") != len(rows):
        errors.append("reviewed and total must match row count")
    if items_payload.get("total") != len(items):
        errors.append("items total must match item count")

    targets = [sample.target for sample in samples]
    if not samples:
        errors.append("baseline requires at least one usable sample")
    if (
        require_both_classes
        and targets
        and (sum(targets) == 0 or sum(targets) == len(targets))
    ):
        errors.append("baseline requires both allow and filter samples")
    if errors:
        raise BaselineInputError("; ".join(errors))
    return samples


def _seed_everything(seed: int) -> None:
    random.seed(seed)
    os.environ["PYTHONHASHSEED"] = str(seed)


def _rounded(value: float) -> float:
    return round(float(value), 6)


def _classification_metrics(targets: Any, probabilities: Any, threshold: float) -> dict[str, Any]:
    import numpy as np
    from sklearn.metrics import (
        average_precision_score,
        brier_score_loss,
        confusion_matrix,
        roc_auc_score,
    )

    predictions = (probabilities >= threshold).astype(np.int64)
    tn, fp, fn, tp = confusion_matrix(targets, predictions, labels=[0, 1]).ravel()

    def ratio(numerator: int, denominator: int) -> float | None:
        return _rounded(numerator / denominator) if denominator else None

    return {
        "threshold": _rounded(threshold),
        "confusion_matrix": {
            "allow_as_allow": int(tn),
            "allow_as_filter": int(fp),
            "filter_as_allow": int(fn),
            "filter_as_filter": int(tp),
        },
        "accuracy": ratio(int(tn + tp), int(tn + fp + fn + tp)),
        "filter_precision": ratio(int(tp), int(tp + fp)),
        "filter_recall": ratio(int(tp), int(tp + fn)),
        "false_allow_rate": ratio(int(fn), int(tp + fn)),
        "allow_recall": ratio(int(tn), int(tn + fp)),
        "roc_auc": _rounded(roc_auc_score(targets, probabilities)),
        "average_precision": _rounded(average_precision_score(targets, probabilities)),
        "brier": _rounded(brier_score_loss(targets, probabilities)),
    }


def _case_list(samples: Sequence[PilotSample], targets: Any, probabilities: Any, threshold: float) -> dict[str, Any]:
    false_allow: list[dict[str, Any]] = []
    false_filter: list[dict[str, Any]] = []
    for sample, target, probability in zip(samples, targets, probabilities):
        predicted = int(probability >= threshold)
        case = {
            "sample_id": sample.sample_id,
            "source": sample.source,
            "human_action": sample.action,
            "filter_probability": _rounded(probability),
        }
        if int(target) == 1 and predicted == 0:
            false_allow.append(case)
        elif int(target) == 0 and predicted == 1:
            false_filter.append(case)
    return {
        "false_allow": sorted(false_allow, key=lambda item: item["filter_probability"]),
        "false_filter": sorted(
            false_filter,
            key=lambda item: item["filter_probability"],
            reverse=True,
        ),
    }


def _extract_features(
    samples: Sequence[PilotSample],
    weights_cache: Path,
    batch_size: int,
    pooling: str,
) -> tuple[Any, dict[str, str]]:
    import numpy as np
    import torch
    from PIL import Image, ImageOps
    from torch import nn
    from torchvision.models import MobileNet_V3_Small_Weights, mobilenet_v3_small
    from torchvision.transforms import Compose, Normalize, ToTensor

    torch.hub.set_dir(str(weights_cache))
    weights = MobileNet_V3_Small_Weights.DEFAULT
    model = mobilenet_v3_small(weights=weights)
    weights_path = weights_cache / "checkpoints" / Path(urlparse(weights.url).path).name
    if not weights_path.is_file():
        raise RuntimeError(f"downloaded weights are missing: {weights_path}")
    feature_model = model.features
    feature_model.eval()
    transform = Compose(
        [
            ToTensor(),
            Normalize(mean=(0.485, 0.456, 0.406), std=(0.229, 0.224, 0.225)),
        ]
    )

    def letterbox(path: Path) -> Any:
        with Image.open(path) as opened:
            image = ImageOps.exif_transpose(opened).convert("RGB")
            image.thumbnail((224, 224), Image.Resampling.BILINEAR)
            canvas = Image.new("RGB", (224, 224), (128, 128, 128))
            offset = ((224 - image.width) // 2, (224 - image.height) // 2)
            canvas.paste(image, offset)
            return transform(canvas)

    rows: list[Any] = []
    with torch.inference_mode():
        for start in range(0, len(samples), batch_size):
            batch = torch.stack(
                [letterbox(sample.image_path) for sample in samples[start : start + batch_size]]
            )
            feature_maps = feature_model(batch)
            average_features = nn.functional.adaptive_avg_pool2d(feature_maps, 1).flatten(1)
            if pooling == "average-max":
                maximum_features = nn.functional.adaptive_max_pool2d(feature_maps, 1).flatten(1)
                features = torch.cat((average_features, maximum_features), dim=1)
            else:
                features = average_features
            rows.append(features.cpu().numpy())
    matrix = np.concatenate(rows, axis=0)
    norms = np.linalg.norm(matrix, axis=1, keepdims=True)
    return matrix / np.clip(norms, 1e-12, None), {
        "weights_url": weights.url,
        "weights_sha256": _sha256(weights_path),
    }


def build_report(
    samples: Sequence[PilotSample],
    features: Any,
    review_path: Path,
    items_path: Path,
    *,
    seed: int,
    repeats: int,
    pooling: str,
    model_provenance: dict[str, str],
) -> dict[str, Any]:
    import numpy as np

    targets = np.asarray([sample.target for sample in samples], dtype=np.int64)
    probabilities = _cross_validated_probabilities(
        features,
        targets,
        seed=seed,
        repeats=repeats,
    )
    fixed_threshold = 0.5
    conservative_threshold = 0.35

    return {
        "schema_version": REPORT_SCHEMA_VERSION,
        "created_at": datetime.now(timezone.utc).isoformat(),
        "status": "research_only_not_approved_for_apk",
        "inputs": {
            "review_path": str(review_path),
            "review_sha256": _sha256(review_path),
            "items_path": str(items_path),
            "items_sha256": _sha256(items_path),
            "samples": len(samples),
            "allow": int((targets == 0).sum()),
            "filter": int((targets == 1).sum()),
        },
        "feature_extractor": {
            "name": "torchvision-mobilenet-v3-small-imagenet1k-v1",
            "pretrained": True,
            "frozen": True,
            "feature_dimensions": int(features.shape[1]),
            "spatial_pooling": pooling,
            "input": "224x224 RGB complete-image gray letterbox",
            "device": "cpu",
            **model_provenance,
        },
        "classifier": {
            "name": "standard-scaler-logistic-regression",
            "primary_target": "allow-vs-filter",
            "blur_and_block_combined": True,
        },
        "evaluation": {
            "protocol": "repeated-stratified-5-fold-cross-validation",
            "repeats": repeats,
            "seed": seed,
            "important_limitation": (
                "The 100-image pilot has no approved near-duplicate/source-group split; "
                "these results are preliminary and cannot approve APK enforcement."
            ),
        },
        "thresholds": {
            "fixed_0_50": {
                "metrics": _classification_metrics(targets, probabilities, fixed_threshold),
                "cases": _case_list(samples, targets, probabilities, fixed_threshold),
            },
            "conservative_0_35": {
                "metrics": _classification_metrics(
                    targets,
                    probabilities,
                    conservative_threshold,
                ),
                "cases": _case_list(
                    samples,
                    targets,
                    probabilities,
                    conservative_threshold,
                ),
            },
        },
        "predictions": [
            {
                "sample_id": sample.sample_id,
                "source": sample.source,
                "human_action": sample.action,
                "binary_target": "allow" if sample.target == 0 else "filter",
                "filter_probability": _rounded(probability),
            }
            for sample, probability in zip(samples, probabilities)
        ],
    }


def _cross_validated_probabilities(
    features: Any,
    targets: Any,
    *,
    seed: int,
    repeats: int,
) -> Any:
    import numpy as np
    from sklearn.linear_model import LogisticRegression
    from sklearn.model_selection import RepeatedStratifiedKFold
    from sklearn.pipeline import make_pipeline
    from sklearn.preprocessing import StandardScaler

    splitter = RepeatedStratifiedKFold(
        n_splits=5,
        n_repeats=repeats,
        random_state=seed,
    )
    probability_sum = np.zeros(len(targets), dtype=np.float64)
    probability_count = np.zeros(len(targets), dtype=np.int64)

    for train_indices, test_indices in splitter.split(features, targets):
        classifier = make_pipeline(
            StandardScaler(),
            LogisticRegression(
                C=0.1,
                class_weight="balanced",
                max_iter=2_000,
                random_state=seed,
            ),
        )
        classifier.fit(features[train_indices], targets[train_indices])
        probability_sum[test_indices] += classifier.predict_proba(features[test_indices])[:, 1]
        probability_count[test_indices] += 1

    if not np.all(probability_count == repeats):
        raise RuntimeError("cross-validation did not score every sample once per repeat")
    return probability_sum / probability_count


def build_combined_cross_validation(
    first_samples: Sequence[PilotSample],
    first_features: Any,
    second_samples: Sequence[PilotSample],
    second_features: Any,
    *,
    seed: int,
    repeats: int,
) -> dict[str, Any]:
    import numpy as np

    samples = [*first_samples, *second_samples]
    features = np.concatenate((first_features, second_features), axis=0)
    targets = np.asarray([sample.target for sample in samples], dtype=np.int64)
    probabilities = _cross_validated_probabilities(
        features,
        targets,
        seed=seed,
        repeats=repeats,
    )
    return {
        "status": "combined_research_cross_validation",
        "important_limitation": (
            "Round 2 now participates in training folds, so this does not replace a new "
            "independent external test."
        ),
        "samples": len(samples),
        "allow": int((targets == 0).sum()),
        "filter": int((targets == 1).sum()),
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
                "filter_probability": _rounded(probability),
            }
            for sample, probability in zip(samples, probabilities)
        ],
    }


def build_external_evaluation(
    training_samples: Sequence[PilotSample],
    training_features: Any,
    test_samples: Sequence[PilotSample],
    test_features: Any,
    *,
    seed: int,
) -> dict[str, Any]:
    import numpy as np
    from sklearn.linear_model import LogisticRegression
    from sklearn.pipeline import make_pipeline
    from sklearn.preprocessing import StandardScaler

    training_targets = np.asarray(
        [sample.target for sample in training_samples],
        dtype=np.int64,
    )
    test_targets = np.asarray([sample.target for sample in test_samples], dtype=np.int64)
    classifier = make_pipeline(
        StandardScaler(),
        LogisticRegression(
            C=0.1,
            class_weight="balanced",
            max_iter=2_000,
            random_state=seed,
        ),
    )
    classifier.fit(training_features, training_targets)
    probabilities = classifier.predict_proba(test_features)[:, 1]

    return {
        "status": "independent_hard_case_stress_test",
        "training_samples": len(training_samples),
        "test_samples": len(test_samples),
        "test_allow": int((test_targets == 0).sum()),
        "test_filter": int((test_targets == 1).sum()),
        "thresholds": {
            "fixed_0_50": {
                "metrics": _classification_metrics(test_targets, probabilities, 0.5),
                "cases": _case_list(test_samples, test_targets, probabilities, 0.5),
            },
            "conservative_0_35": {
                "metrics": _classification_metrics(test_targets, probabilities, 0.35),
                "cases": _case_list(test_samples, test_targets, probabilities, 0.35),
            },
        },
        "predictions": [
            {
                "sample_id": sample.sample_id,
                "source": sample.source,
                "human_action": sample.action,
                "binary_target": "allow" if sample.target == 0 else "filter",
                "filter_probability": _rounded(probability),
            }
            for sample, probability in zip(test_samples, probabilities)
        ],
    }


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("review", type=Path)
    parser.add_argument("items", type=Path)
    parser.add_argument("public_dir", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--weights-cache", type=Path, required=True)
    parser.add_argument("--batch-size", type=int, default=16)
    parser.add_argument("--repeats", type=int, default=10)
    parser.add_argument("--seed", type=int, default=DEFAULT_SEED)
    parser.add_argument(
        "--pooling",
        choices=("average", "average-max"),
        default="average",
    )
    parser.add_argument("--external-review", type=Path)
    parser.add_argument("--external-items", type=Path)
    parser.add_argument("--external-public-dir", type=Path)
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    arguments = _parser().parse_args(argv)
    if not 1 <= arguments.batch_size <= 128:
        print("error: --batch-size must be between 1 and 128", file=sys.stderr)
        return 2
    if not 1 <= arguments.repeats <= 100:
        print("error: --repeats must be between 1 and 100", file=sys.stderr)
        return 2
    external_arguments = (
        arguments.external_review,
        arguments.external_items,
        arguments.external_public_dir,
    )
    if any(external_arguments) and not all(external_arguments):
        print(
            "error: external review, items, and public directory must be provided together",
            file=sys.stderr,
        )
        return 2
    try:
        samples = load_samples(arguments.review, arguments.items, arguments.public_dir)
        _seed_everything(arguments.seed)
        features, model_provenance = _extract_features(
            samples,
            arguments.weights_cache,
            arguments.batch_size,
            arguments.pooling,
        )
        report = build_report(
            samples,
            features,
            arguments.review,
            arguments.items,
            seed=arguments.seed,
            repeats=arguments.repeats,
            pooling=arguments.pooling,
            model_provenance=model_provenance,
        )
        if all(external_arguments):
            external_samples = load_samples(
                arguments.external_review,
                arguments.external_items,
                arguments.external_public_dir,
            )
            external_features, external_provenance = _extract_features(
                external_samples,
                arguments.weights_cache,
                arguments.batch_size,
                arguments.pooling,
            )
            if external_provenance != model_provenance:
                raise RuntimeError("training and external test used different model weights")
            report["external_test"] = build_external_evaluation(
                samples,
                features,
                external_samples,
                external_features,
                seed=arguments.seed,
            )
            report["combined_cross_validation"] = build_combined_cross_validation(
                samples,
                features,
                external_samples,
                external_features,
                seed=arguments.seed,
                repeats=arguments.repeats,
            )
        arguments.output.parent.mkdir(parents=True, exist_ok=True)
        arguments.output.write_text(
            json.dumps(report, indent=2, ensure_ascii=False) + "\n",
            encoding="utf-8",
        )
    except (BaselineInputError, OSError, RuntimeError, ValueError) as error:
        print(f"error: {error}", file=sys.stderr)
        return 1
    summary = {
        "output": str(arguments.output),
        "fixed_0_50": report["thresholds"]["fixed_0_50"]["metrics"],
        "conservative_0_35": report["thresholds"]["conservative_0_35"]["metrics"],
    }
    if "external_test" in report:
        summary["external_test"] = {
            name: value["metrics"]
            for name, value in report["external_test"]["thresholds"].items()
        }
        summary["combined_cross_validation"] = {
            name: value["metrics"]
            for name, value in report["combined_cross_validation"]["thresholds"].items()
        }
    print(json.dumps(summary, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
