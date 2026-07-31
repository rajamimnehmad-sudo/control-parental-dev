#!/usr/bin/env python3
"""Export the TinyCLIP policy candidate as a single mobile ONNX model."""

from __future__ import annotations

import argparse
import hashlib
import json
import statistics
import time
from pathlib import Path
from typing import Any, Sequence

import numpy as np
import torch
from PIL import Image, ImageOps
from torch import nn
from torch.nn import functional as F
from transformers import AutoModel, AutoProcessor

from pilot_binary_baseline import load_samples
from pilot_tinyclip_candidate import DAG_LETTERBOX, _dag_letterbox_image


MODEL_ID = "wkcn/TinyCLIP-ViT-8M-16-Text-3M-YFCC15M"
SCHEMA_VERSION = "dag-v3-tinyclip-mobile-export-v1"


class TinyClipPolicy(nn.Module):
    """Vision encoder, projection and binary policy head in one graph."""

    def __init__(
        self,
        vision_model: nn.Module,
        visual_projection: nn.Module,
        coefficient: np.ndarray,
        intercept: np.ndarray,
    ) -> None:
        super().__init__()
        self.vision_model = vision_model
        self.visual_projection = visual_projection
        self.register_buffer(
            "classifier_coefficient",
            torch.as_tensor(coefficient, dtype=torch.float32),
        )
        self.register_buffer(
            "classifier_intercept",
            torch.as_tensor(intercept, dtype=torch.float32),
        )

    def forward(self, pixel_values: torch.Tensor) -> torch.Tensor:
        vision_output = self.vision_model(
            pixel_values=pixel_values,
            return_dict=False,
        )
        projected = self.visual_projection(vision_output[1])
        normalized = F.normalize(projected, dim=1)
        logits = normalized @ self.classifier_coefficient.t()
        return torch.sigmoid(logits + self.classifier_intercept)


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--checkpoint", required=True, type=Path)
    parser.add_argument("--validation-review", required=True, type=Path)
    parser.add_argument("--validation-items", required=True, type=Path)
    parser.add_argument("--validation-public-dir", required=True, type=Path)
    parser.add_argument("--current-holdout", required=True, type=Path)
    parser.add_argument("--onnx-output", required=True, type=Path)
    parser.add_argument("--quantized-output", required=True, type=Path)
    parser.add_argument("--report", required=True, type=Path)
    parser.add_argument("--runs", type=int, default=30)
    parser.add_argument(
        "--per-channel",
        action=argparse.BooleanOptionalAction,
        default=True,
    )
    return parser


def _checkpoint_components(
    checkpoint: dict[str, Any],
) -> tuple[dict[str, Any], dict[str, Any], np.ndarray, np.ndarray]:
    if "state_dict" not in checkpoint:
        return (
            checkpoint["vision_model"],
            checkpoint["visual_projection"],
            checkpoint["classifier_coef"],
            checkpoint["classifier_intercept"],
        )

    state = checkpoint["state_dict"]
    if not isinstance(state, dict):
        raise ValueError("fine-tuned checkpoint state_dict must be an object")
    vision = {
        key.removeprefix("vision_model."): value
        for key, value in state.items()
        if key.startswith("vision_model.")
    }
    projection = {
        key.removeprefix("visual_projection."): value
        for key, value in state.items()
        if key.startswith("visual_projection.")
    }
    coefficient = state.get("classifier.weight")
    intercept = state.get("classifier.bias")
    if not vision or not projection or coefficient is None or intercept is None:
        raise ValueError("fine-tuned checkpoint is missing model components")
    return vision, projection, coefficient.numpy(), intercept.numpy()


def _preprocess(
    processor: Any,
    image_path: Path,
    *,
    preprocessing: str,
) -> np.ndarray:
    with Image.open(image_path) as opened:
        image = ImageOps.exif_transpose(opened).convert("RGB")
    if preprocessing == DAG_LETTERBOX:
        image = _dag_letterbox_image(image)
    return processor(images=[image], return_tensors="np")["pixel_values"].astype(
        np.float32
    )


def _evaluate_session(
    session: Any,
    cases: Sequence[dict[str, Any]],
    processor: Any,
    *,
    threshold: float,
    runs: int,
    preprocessing: str,
) -> tuple[list[dict[str, Any]], dict[str, float]]:
    predictions: list[dict[str, Any]] = []
    prepared: list[np.ndarray] = []
    for case in cases:
        pixel_values = _preprocess(
            processor,
            Path(case["image_path"]),
            preprocessing=preprocessing,
        )
        prepared.append(pixel_values)
        probability = float(
            session.run(
                ["filter_probability"],
                {"pixel_values": pixel_values},
            )[0][0, 0]
        )
        predictions.append(
            {
                "sample_id": case["sample_id"],
                "expected_action": case["expected_action"],
                "filter_probability": round(probability, 8),
                "predicted_action": (
                    "filter" if probability >= threshold else "allow"
                ),
            }
        )

    timings: list[float] = []
    benchmark_input = prepared[0]
    for _ in range(5):
        session.run(["filter_probability"], {"pixel_values": benchmark_input})
    for _ in range(runs):
        started = time.perf_counter()
        session.run(["filter_probability"], {"pixel_values": benchmark_input})
        timings.append((time.perf_counter() - started) * 1000)
    timings.sort()
    p95_index = max(0, min(len(timings) - 1, int(len(timings) * 0.95) - 1))
    latency = {
        "median_ms": round(statistics.median(timings), 3),
        "p95_ms": round(timings[p95_index], 3),
    }
    return predictions, latency


def main(argv: Sequence[str] | None = None) -> int:
    arguments = _parser().parse_args(argv)
    if arguments.runs < 5:
        raise ValueError("--runs must be at least 5")

    checkpoint = torch.load(
        arguments.checkpoint,
        map_location="cpu",
        weights_only=False,
    )
    vision_state, projection_state, coefficient, intercept = (
        _checkpoint_components(checkpoint)
    )
    base_model = AutoModel.from_pretrained(MODEL_ID)
    base_model.vision_model.load_state_dict(vision_state)
    base_model.visual_projection.load_state_dict(projection_state)
    policy = TinyClipPolicy(
        base_model.vision_model,
        base_model.visual_projection,
        coefficient,
        intercept,
    ).eval()
    processor = AutoProcessor.from_pretrained(MODEL_ID)
    threshold = float(checkpoint["threshold"])
    preprocessing = str(checkpoint.get("preprocessing", "tinyclip-crop"))

    arguments.onnx_output.parent.mkdir(parents=True, exist_ok=True)
    example = torch.zeros((1, 3, 224, 224), dtype=torch.float32)
    with torch.inference_mode():
        torch.onnx.export(
            policy,
            (example,),
            arguments.onnx_output,
            input_names=["pixel_values"],
            output_names=["filter_probability"],
            opset_version=17,
            do_constant_folding=True,
            dynamo=False,
        )

    import onnx
    import onnxruntime as ort
    from onnxruntime.quantization import QuantType, quantize_dynamic

    onnx.checker.check_model(onnx.load(arguments.onnx_output))
    quantize_dynamic(
        arguments.onnx_output,
        arguments.quantized_output,
        weight_type=QuantType.QInt8,
        per_channel=arguments.per_channel,
    )
    onnx.checker.check_model(onnx.load(arguments.quantized_output))

    validation_samples = load_samples(
        arguments.validation_review,
        arguments.validation_items,
        arguments.validation_public_dir,
        skip_excluded=True,
    )
    validation_cases = [
        {
            "sample_id": sample.sample_id,
            "image_path": str(sample.image_path),
            "expected_action": sample.action,
            "group": "frozen_validation",
        }
        for sample in validation_samples
    ]
    current = json.loads(arguments.current_holdout.read_text(encoding="utf-8"))
    current_cases = [
        {
            "sample_id": row["sample_id"],
            "image_path": row["image_path"],
            "expected_action": row["expected_action"],
            "group": "current_holdout",
        }
        for row in current["predictions"]
    ]
    cases = [*validation_cases, *current_cases]

    providers = ["CPUExecutionProvider"]
    float_session = ort.InferenceSession(
        str(arguments.onnx_output),
        providers=providers,
    )
    quantized_session = ort.InferenceSession(
        str(arguments.quantized_output),
        providers=providers,
    )
    float_predictions, float_latency = _evaluate_session(
        float_session,
        cases,
        processor,
        threshold=threshold,
        runs=arguments.runs,
        preprocessing=preprocessing,
    )
    quantized_predictions, quantized_latency = _evaluate_session(
        quantized_session,
        cases,
        processor,
        threshold=threshold,
        runs=arguments.runs,
        preprocessing=preprocessing,
    )

    probability_differences = [
        abs(
            float_row["filter_probability"]
            - quantized_row["filter_probability"]
        )
        for float_row, quantized_row in zip(
            float_predictions,
            quantized_predictions,
        )
    ]
    decision_changes = [
        {
            "sample_id": float_row["sample_id"],
            "float_action": float_row["predicted_action"],
            "quantized_action": quantized_row["predicted_action"],
        }
        for float_row, quantized_row in zip(
            float_predictions,
            quantized_predictions,
        )
        if float_row["predicted_action"] != quantized_row["predicted_action"]
    ]
    current_allow = next(
        row
        for row in quantized_predictions
        if row["sample_id"] == "openimages:3bbda944ea0d5c4f"
    )
    report = {
        "schema_version": SCHEMA_VERSION,
        "status": (
            "quantized_export_passed_research_parity"
            if not decision_changes
            else "quantized_export_failed_research_parity"
        ),
        "important_note": (
            "Export parity only compares float and quantized candidates. The known "
            "human-confirmed allow is reported separately as a model-quality check."
        ),
        "model_id": MODEL_ID,
        "threshold": threshold,
        "preprocessing": preprocessing,
        "quantization": {
            "method": "dynamic_int8",
            "per_channel": arguments.per_channel,
        },
        "cases": {
            "frozen_validation": len(validation_cases),
            "current_holdout": len(current_cases),
            "total": len(cases),
        },
        "float_model": {
            "path": str(arguments.onnx_output),
            "bytes": arguments.onnx_output.stat().st_size,
            "sha256": _sha256(arguments.onnx_output),
            "latency": float_latency,
        },
        "quantized_model": {
            "path": str(arguments.quantized_output),
            "bytes": arguments.quantized_output.stat().st_size,
            "sha256": _sha256(arguments.quantized_output),
            "latency": quantized_latency,
        },
        "parity": {
            "decision_changes": decision_changes,
            "maximum_probability_difference": round(
                max(probability_differences),
                8,
            ),
            "mean_probability_difference": round(
                statistics.mean(probability_differences),
                8,
            ),
            "known_human_confirmed_allow": current_allow,
        },
        "float_predictions": float_predictions,
        "quantized_predictions": quantized_predictions,
        "approved_for_apk": False,
    }
    arguments.report.parent.mkdir(parents=True, exist_ok=True)
    arguments.report.write_text(
        json.dumps(report, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )
    print(json.dumps(report, indent=2, ensure_ascii=False))
    return 0 if not decision_changes else 1


if __name__ == "__main__":
    raise SystemExit(main())
