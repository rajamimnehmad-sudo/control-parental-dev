#!/usr/bin/env python3
"""Evaluate TinyCLIP's compact semantic image encoder on the frozen policy round."""

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


MODEL_ID = "wkcn/TinyCLIP-ViT-8M-16-Text-3M-YFCC15M"
REPORT_SCHEMA_VERSION = "dag-v3-tinyclip-semantic-candidate-v1"
TINYCLIP_CROP = "tinyclip-crop"
DAG_LETTERBOX = "dag-letterbox"


def _dag_letterbox_image(image: Any, *, target_size: int = 224) -> Any:
    """Mirror DAG's full-image fit with centered #7F7F7F padding."""
    from PIL import Image

    width, height = image.size
    scale = min(target_size / width, target_size / height)
    content_size = (
        max(1, min(target_size, round(width * scale))),
        max(1, min(target_size, round(height * scale))),
    )
    resized = image.resize(content_size, resample=Image.Resampling.BILINEAR)
    output = Image.new("RGB", (target_size, target_size), color=(127, 127, 127))
    output.paste(
        resized,
        (
            (target_size - content_size[0]) // 2,
            (target_size - content_size[1]) // 2,
        ),
    )
    return output


def extract_features(
    samples: Sequence[PilotSample],
    *,
    device_name: str,
    batch_size: int,
    preprocessing: str,
) -> tuple[Any, dict[str, Any], dict[str, Any]]:
    import numpy as np
    import torch
    from huggingface_hub import hf_hub_download
    from PIL import Image, ImageOps
    from transformers import AutoModel, AutoProcessor

    if device_name == "mps" and not torch.backends.mps.is_available():
        raise RuntimeError("MPS was requested but is unavailable")
    device = torch.device(device_name)
    model = AutoModel.from_pretrained(MODEL_ID).eval().to(device)
    processor = AutoProcessor.from_pretrained(MODEL_ID)
    matrices: list[Any] = []
    with torch.inference_mode():
        for start in range(0, len(samples), batch_size):
            images: list[Any] = []
            for sample in samples[start : start + batch_size]:
                with Image.open(sample.image_path) as opened:
                    image = ImageOps.exif_transpose(opened).convert("RGB")
                    if preprocessing == DAG_LETTERBOX:
                        image = _dag_letterbox_image(image)
                    images.append(image)
            inputs = processor(images=images, return_tensors="pt")
            feature_output = model.get_image_features(
                pixel_values=inputs["pixel_values"].to(device)
            )
            features = feature_output.pooler_output
            features = torch.nn.functional.normalize(features, dim=1)
            matrices.append(features.cpu().numpy())
    weights_path = Path(hf_hub_download(MODEL_ID, "model.safetensors"))
    vision_parameters = sum(
        parameter.numel() for parameter in model.vision_model.parameters()
    ) + sum(parameter.numel() for parameter in model.visual_projection.parameters())
    provenance = {
        "model_id": MODEL_ID,
        "weights_sha256": _sha256(weights_path),
        "full_checkpoint_bytes": weights_path.stat().st_size,
        "vision_parameters": vision_parameters,
        "declared_license": "mit",
        "candidate_role": "single_semantic_image_encoder_plus_binary_head",
        "text_encoder_required_at_inference": False,
        "device": device.type,
        "preprocessing": preprocessing,
    }
    visual_state = {
        "vision_model": {
            key: value.detach().cpu()
            for key, value in model.vision_model.state_dict().items()
        },
        "visual_projection": {
            key: value.detach().cpu()
            for key, value in model.visual_projection.state_dict().items()
        },
    }
    return np.concatenate(matrices), provenance, visual_state


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
    parser.add_argument("--device", choices=("mps", "cpu"), default="mps")
    parser.add_argument("--batch-size", type=int, default=8)
    parser.add_argument(
        "--preprocessing",
        choices=(TINYCLIP_CROP, DAG_LETTERBOX),
        default=TINYCLIP_CROP,
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
        features, provenance, visual_state = extract_features(
            all_samples,
            device_name=arguments.device,
            batch_size=arguments.batch_size,
            preprocessing=arguments.preprocessing,
        )
        import numpy as np
        from sklearn.linear_model import LogisticRegression

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
        classifier.fit(features[:training_count], training_targets)
        probabilities = classifier.predict_proba(features[training_count:])[:, 1]
        import torch

        arguments.checkpoint.parent.mkdir(parents=True, exist_ok=True)
        torch.save(
            {
                "schema_version": REPORT_SCHEMA_VERSION,
                "vision_model": visual_state["vision_model"],
                "visual_projection": visual_state["visual_projection"],
                "classifier_coef": classifier.coef_,
                "classifier_intercept": classifier.intercept_,
                "labels": ["allow", "filter"],
                "threshold": 0.4,
                "pretrained_model_id": MODEL_ID,
                "text_encoder_included": False,
                "preprocessing": arguments.preprocessing,
            },
            arguments.checkpoint,
        )
        provenance["exported_candidate_bytes"] = arguments.checkpoint.stat().st_size
        provenance["exported_candidate_sha256"] = _sha256(arguments.checkpoint)
        report = {
            "schema_version": REPORT_SCHEMA_VERSION,
            "created_at": datetime.now(timezone.utc).isoformat(),
            "status": "research_candidate_not_approved_for_apk",
            "training_samples": training_count,
            "validation_samples": len(validation_samples),
            "model": provenance,
            "checkpoint": {
                "path": str(arguments.checkpoint),
                "approved_for_apk": False,
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
                "model": provenance["model_id"],
                "threshold_0_40": report["thresholds"]["0.4"]["metrics"],
            },
            indent=2,
            ensure_ascii=False,
        )
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
