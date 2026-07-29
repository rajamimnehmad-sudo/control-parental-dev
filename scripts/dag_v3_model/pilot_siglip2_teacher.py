#!/usr/bin/env python3
"""Test a frozen SigLIP 2 image encoder as a training-only semantic teacher."""

from __future__ import annotations

import argparse
import json
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Sequence

from pilot_binary_baseline import (
    DEFAULT_SEED,
    PilotSample,
    _case_list,
    _classification_metrics,
    _sha256,
    load_samples,
)


MODEL_ID = "timm/vit_base_patch16_siglip_224.v2_webli"
REPORT_SCHEMA_VERSION = "dag-v3-siglip2-semantic-teacher-pilot-v1"


class FeatureDataset:
    def __init__(self, samples: Sequence[PilotSample], transform: Any) -> None:
        self.samples = list(samples)
        self.transform = transform

    def __len__(self) -> int:
        return len(self.samples)

    def __getitem__(self, index: int) -> Any:
        from PIL import Image, ImageOps

        with Image.open(self.samples[index].image_path) as opened:
            image = ImageOps.exif_transpose(opened).convert("RGB")
            return self.transform(image)


def extract_features(
    samples: Sequence[PilotSample],
    *,
    device_name: str,
    batch_size: int,
) -> tuple[Any, dict[str, Any]]:
    import numpy as np
    import timm
    import torch
    from huggingface_hub import hf_hub_download
    from timm.data import create_transform, resolve_model_data_config
    from torch.utils.data import DataLoader

    if device_name == "mps" and not torch.backends.mps.is_available():
        raise RuntimeError("MPS was requested but is unavailable")
    device = torch.device(device_name)
    model = timm.create_model(
        f"hf_hub:{MODEL_ID}",
        pretrained=True,
        num_classes=0,
    ).eval().to(device)
    transform = create_transform(
        **resolve_model_data_config(model),
        is_training=False,
    )
    loader = DataLoader(
        FeatureDataset(samples, transform),
        batch_size=batch_size,
        shuffle=False,
        num_workers=0,
    )
    matrices: list[Any] = []
    with torch.inference_mode():
        for inputs in loader:
            features = model(inputs.to(device))
            features = torch.nn.functional.normalize(features, dim=1)
            matrices.append(features.cpu().numpy())
    weights_path = Path(hf_hub_download(MODEL_ID, "model.safetensors"))
    return np.concatenate(matrices), {
        "model_id": MODEL_ID,
        "weights_sha256": _sha256(weights_path),
        "weights_bytes": weights_path.stat().st_size,
        "declared_license": "apache-2.0",
        "role": "training_only_semantic_teacher",
        "present_at_final_inference": False,
        "device": device.type,
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
    parser.add_argument("--device", choices=("mps", "cpu"), default="mps")
    parser.add_argument("--batch-size", type=int, default=8)
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
    if not 1 <= arguments.batch_size <= 32:
        print("error: batch-size must be between 1 and 32", file=sys.stderr)
        return 2
    try:
        training_samples = [
            sample
            for review, items, public_dir in zip(
                arguments.review,
                arguments.items,
                arguments.public_dir,
            )
            for sample in load_samples(
                review,
                items,
                public_dir,
                skip_excluded=True,
            )
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

        all_samples = [*training_samples, *validation_samples]
        features, provenance = extract_features(
            all_samples,
            device_name=arguments.device,
            batch_size=arguments.batch_size,
        )
        import numpy as np
        from sklearn.linear_model import LogisticRegression
        from sklearn.model_selection import StratifiedKFold

        training_count = len(training_samples)
        training_targets = np.asarray(
            [sample.target for sample in training_samples],
            dtype=np.int64,
        )
        validation_targets = np.asarray(
            [sample.target for sample in validation_samples],
            dtype=np.int64,
        )
        classifier = LogisticRegression(
            class_weight="balanced",
            max_iter=5000,
            random_state=arguments.seed,
        )
        folds = StratifiedKFold(
            n_splits=5,
            shuffle=True,
            random_state=arguments.seed,
        )
        out_of_fold_probabilities = np.zeros(training_count, dtype=np.float64)
        for fold_train, fold_test in folds.split(
            features[:training_count],
            training_targets,
        ):
            fold_classifier = LogisticRegression(
                class_weight="balanced",
                max_iter=5000,
                random_state=arguments.seed,
            )
            fold_classifier.fit(
                features[fold_train],
                training_targets[fold_train],
            )
            out_of_fold_probabilities[fold_test] = fold_classifier.predict_proba(
                features[fold_test]
            )[:, 1]
        classifier.fit(features[:training_count], training_targets)
        probabilities = classifier.predict_proba(features[training_count:])[:, 1]
        report = {
            "schema_version": REPORT_SCHEMA_VERSION,
            "created_at": datetime.now(timezone.utc).isoformat(),
            "status": "research_teacher_only_not_approved_for_apk",
            "training_samples": training_count,
            "validation_samples": len(validation_samples),
            "teacher": provenance,
            "training_soft_targets": {
                "protocol": "5_fold_out_of_fold_no_sample_scores_itself",
                "predictions": [
                    {
                        "sample_id": sample.sample_id,
                        "human_action": sample.action,
                        "filter_probability": round(float(probability), 6),
                    }
                    for sample, probability in zip(
                        training_samples,
                        out_of_fold_probabilities,
                    )
                ],
            },
            "evaluation": {
                "protocol": "frozen_round_3_validation_not_used_for_training",
                "important_limitation": (
                    "Round 3 already informed architecture research; a new independent "
                    "test remains mandatory before an APK decision."
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
                    "human_action": sample.action,
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
                "teacher": provenance["model_id"],
                "threshold_0_40": report["thresholds"]["0.4"]["metrics"],
            },
            indent=2,
            ensure_ascii=False,
        )
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
