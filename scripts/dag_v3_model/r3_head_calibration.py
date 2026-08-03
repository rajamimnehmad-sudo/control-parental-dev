#!/usr/bin/env python3
"""Fit a bounded R3 binary head on frozen TinyCLIP visual features."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any

import numpy as np

SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from pilot_tinyclip_candidate import MODEL_ID, _dag_letterbox_image  # noqa: E402
from r2_candidate_evaluate import classification_metrics  # noqa: E402
from r2_candidate_train import _checkpoint_components  # noqa: E402


def safe_threshold(targets: list[int], probabilities: list[float], *, minimum: float = 0.05) -> tuple[float, dict[str, Any]]:
    candidates = sorted({minimum, *[round(value, 6) for value in probabilities if value >= minimum]})
    eligible = []
    for threshold in candidates:
        metrics = classification_metrics(targets, probabilities, threshold)
        if metrics["false_permissions"]["count"] == 0:
            eligible.append((metrics["false_filters"]["count"], -threshold, threshold, metrics))
    if not eligible:
        raise ValueError("no threshold preserves validation safety")
    _, _, threshold, metrics = min(eligible)
    return threshold, metrics


def _features(checkpoint_path: Path, records: list[dict[str, Any]]) -> np.ndarray:
    import torch
    from PIL import Image, ImageOps
    from transformers import AutoModel, AutoProcessor

    checkpoint = torch.load(checkpoint_path, map_location="cpu", weights_only=False)
    vision_state, projection_state, _, _ = _checkpoint_components(checkpoint)
    base = AutoModel.from_pretrained(MODEL_ID, local_files_only=True)
    base.vision_model.load_state_dict(vision_state)
    base.visual_projection.load_state_dict(projection_state)
    base.eval()
    processor = AutoProcessor.from_pretrained(MODEL_ID, local_files_only=True)
    result = []
    with torch.inference_mode():
        for start in range(0, len(records), 16):
            images = []
            for row in records[start : start + 16]:
                with Image.open(row["image_path"]) as opened:
                    image = ImageOps.exif_transpose(opened).convert("RGB")
                images.append(_dag_letterbox_image(image))
            pixels = processor(images=images, return_tensors="pt")["pixel_values"]
            pooled = base.vision_model(pixel_values=pixels).pooler_output
            projected = base.visual_projection(pooled)
            normalized = torch.nn.functional.normalize(projected, dim=1)
            result.append(normalized.numpy())
    return np.concatenate(result)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--split", required=True, type=Path)
    parser.add_argument("--initial-checkpoint", required=True, type=Path)
    parser.add_argument("--checkpoint", required=True, type=Path)
    parser.add_argument("--report", required=True, type=Path)
    args = parser.parse_args()

    import torch
    from sklearn.linear_model import LogisticRegression

    payload = json.loads(args.split.read_text())
    train = [row for row in payload["records"] if row["split"] == "train"]
    validation = [row for row in payload["records"] if row["split"] == "validation"]
    # frozen_test is deliberately not loaded or embedded during selection.
    records = [*train, *validation]
    features = _features(args.initial_checkpoint, records)
    train_features = features[: len(train)]
    validation_features = features[len(train) :]
    train_targets = np.asarray([row["target"] for row in train], dtype=np.int64)
    validation_targets = [row["target"] for row in validation]
    sample_weights = np.asarray([float(row.get("sample_weight", 1.0)) for row in train])
    owner_allow_ids = {
        row["sample_id"]
        for row in train
        if row.get("sample_weight", 1.0) > 1 and row["target"] == 0
    }

    trials = []
    selected = None
    selected_key = None
    for c_value in (0.01, 0.03, 0.1, 0.3, 1.0, 3.0, 10.0):
        model = LogisticRegression(C=c_value, max_iter=2000, solver="lbfgs", random_state=7301)
        model.fit(train_features, train_targets, sample_weight=sample_weights)
        validation_probabilities = model.predict_proba(validation_features)[:, 1].tolist()
        threshold, metrics = safe_threshold(validation_targets, validation_probabilities)
        train_probabilities = model.predict_proba(train_features)[:, 1]
        owner_allow_failures = [
            row["sample_id"]
            for row, probability in zip(train, train_probabilities)
            if row["sample_id"] in owner_allow_ids and probability >= threshold
        ]
        trial = {
            "c": c_value,
            "threshold": threshold,
            "validation": metrics,
            "owner_allow_failures": owner_allow_failures,
        }
        trials.append(trial)
        key = (
            len(owner_allow_failures),
            metrics["false_permissions"]["count"],
            metrics["false_filters"]["count"],
            -(metrics["balanced_accuracy"] or 0),
            c_value,
        )
        if selected_key is None or key < selected_key:
            selected_key = key
            selected = (model, trial, validation_probabilities, train_probabilities)

    if selected is None:
        raise RuntimeError("no R3 head candidate was selected")
    model, trial, validation_probabilities, train_probabilities = selected
    initial = torch.load(args.initial_checkpoint, map_location="cpu", weights_only=False)
    state = dict(initial["state_dict"])
    state["classifier.weight"] = torch.as_tensor(model.coef_, dtype=torch.float32)
    state["classifier.bias"] = torch.as_tensor(model.intercept_, dtype=torch.float32)
    checkpoint = {
        "schema_version": "gloshia-r3-bounded-head-v1",
        "state_dict": state,
        "threshold": trial["threshold"],
        "pretrained_model_id": MODEL_ID,
        "preprocessing": "dag-letterbox",
        "training_config": {
            "vision": "frozen R2.1 TinyCLIP",
            "classifier": "weighted logistic regression",
            "selection": "validation only; frozen_test unopened",
            "c": trial["c"],
        },
    }
    args.checkpoint.parent.mkdir(parents=True, exist_ok=True)
    torch.save(checkpoint, args.checkpoint)
    report = {
        "schema_version": "gloshia-r3-bounded-head-selection-v1",
        "train_samples": len(train),
        "validation_samples": len(validation),
        "frozen_test_loaded": False,
        "owner_allow_ids": sorted(owner_allow_ids),
        "trials": trials,
        "selected": trial,
        "validation_predictions": [
            {"sample_id": row["sample_id"], "target": row["target"], "filter_probability": probability}
            for row, probability in zip(validation, validation_probabilities)
        ],
        "owner_allow_predictions": [
            {"sample_id": row["sample_id"], "filter_probability": float(probability)}
            for row, probability in zip(train, train_probabilities)
            if row["sample_id"] in owner_allow_ids
        ],
        "final_sealed_opened": False,
    }
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n")
    print(json.dumps({"selected": trial, "owner_allow_predictions": report["owner_allow_predictions"]}, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
