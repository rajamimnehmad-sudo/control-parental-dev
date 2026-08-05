#!/usr/bin/env python3
"""Export and evaluate the frozen R2 candidate without changing R1."""

from __future__ import annotations

import argparse
import hashlib
import json
import statistics
import time
from collections import Counter
from pathlib import Path
from typing import Any

import numpy as np
import torch
from PIL import Image, ImageOps
from torch import nn
from torch.nn import functional as F
from transformers import AutoModel, AutoProcessor

from export_tinyclip_mobile import _checkpoint_components
from pilot_tinyclip_candidate import DAG_LETTERBOX, MODEL_ID, _dag_letterbox_image
from r2_candidate_evaluate import evaluate_records


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


class TinyClipPolicy(nn.Module):
    def __init__(self, vision_model: nn.Module, visual_projection: nn.Module, coefficient: np.ndarray, intercept: np.ndarray) -> None:
        super().__init__()
        self.vision_model = vision_model
        self.visual_projection = visual_projection
        self.register_buffer("classifier_coefficient", torch.as_tensor(coefficient, dtype=torch.float32))
        self.register_buffer("classifier_intercept", torch.as_tensor(intercept, dtype=torch.float32))

    def forward(self, pixel_values: torch.Tensor) -> torch.Tensor:
        try:
            output = self.vision_model(pixel_values=pixel_values, return_dict=False)
        except TypeError:
            output = self.vision_model(pixel_values=pixel_values)
        pooled = output.pooler_output if hasattr(output, "pooler_output") else output[1]
        projected = self.visual_projection(pooled)
        normalized = F.normalize(projected, dim=1)
        logits = normalized @ self.classifier_coefficient.t()
        return torch.sigmoid(logits + self.classifier_intercept)


def _preprocess(processor: Any, path: Path) -> np.ndarray:
    with Image.open(path) as opened:
        image = ImageOps.exif_transpose(opened).convert("RGB")
    image = _dag_letterbox_image(image)
    return processor(images=[image], return_tensors="np")["pixel_values"].astype(np.float32)


def _benchmark(session: Any, pixels: np.ndarray, runs: int) -> dict[str, Any]:
    for _ in range(5):
        session.run(["filter_probability"], {"pixel_values": pixels})
    timings = []
    for _ in range(runs):
        started = time.perf_counter()
        session.run(["filter_probability"], {"pixel_values": pixels})
        timings.append((time.perf_counter() - started) * 1000)
    timings.sort()
    p95_index = max(0, min(len(timings) - 1, int(len(timings) * 0.95) - 1))
    return {
        "runs": runs,
        "mean_ms": round(statistics.mean(timings), 3),
        "p50_ms": round(statistics.median(timings), 3),
        "p95_ms": round(timings[p95_index], 3),
        "max_ms": round(max(timings), 3),
        "environment": "Mac CPU laboratory benchmark, not Android",
    }


def _evaluate_onnx(session: Any, records: list[dict[str, Any]], image_root: Path, processor: Any, runs: int) -> tuple[dict[str, dict[str, Any]], dict[str, Any]]:
    predictions: dict[str, dict[str, Any]] = {}
    first_pixels = None
    for record in records:
        pixels = _preprocess(processor, image_root / record["image_path"])
        if first_pixels is None:
            first_pixels = pixels
        probability = float(session.run(["filter_probability"], {"pixel_values": pixels})[0][0, 0])
        predictions[record["sample_id"]] = {
            "sample_id": record["sample_id"],
            "filter_probability": probability,
            "predicted_action": "filter" if probability >= 0.4 else "allow",
        }
    if first_pixels is None:
        raise ValueError("no records to evaluate")
    return predictions, _benchmark(session, first_pixels, runs)


def _graph_summary(path: Path) -> dict[str, Any]:
    import onnx

    model = onnx.load(path)
    return {
        "ir_version": model.ir_version,
        "opset_import": [{"domain": item.domain, "version": item.version} for item in model.opset_import],
        "node_count": len(model.graph.node),
        "operator_counts": dict(sorted(Counter(node.op_type for node in model.graph.node).items())),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--checkpoint", required=True, type=Path)
    parser.add_argument("--split", required=True, type=Path)
    parser.add_argument("--image-root", required=True, type=Path)
    parser.add_argument("--r1-onnx", required=True, type=Path)
    parser.add_argument("--float-output", required=True, type=Path)
    parser.add_argument("--int8-output", required=True, type=Path)
    parser.add_argument("--predictions-output", required=True, type=Path)
    parser.add_argument("--report", required=True, type=Path)
    parser.add_argument("--runs", type=int, default=30)
    args = parser.parse_args()
    if args.runs < 5:
        raise ValueError("--runs must be at least 5")

    import onnx
    import onnxruntime as ort
    from onnxruntime.quantization import QuantType, quantize_dynamic

    split_payload = json.loads(args.split.read_text(encoding="utf-8"))
    records = [record for record in split_payload["records"] if record["split"] in ("validation", "frozen_test")]
    checkpoint = torch.load(args.checkpoint, map_location="cpu", weights_only=False)
    vision_state, projection_state, coefficient, intercept = _checkpoint_components(checkpoint)
    # PyTorch 2.2's legacy exporter cannot lower SDPA's scalar scale.  Eager
    # attention is the same TinyCLIP architecture and is used only for export.
    base = AutoModel.from_pretrained(MODEL_ID, attn_implementation="eager")
    base.vision_model.load_state_dict(vision_state)
    base.visual_projection.load_state_dict(projection_state)
    policy = TinyClipPolicy(base.vision_model, base.visual_projection, coefficient, intercept).eval()
    processor = AutoProcessor.from_pretrained(MODEL_ID)

    args.float_output.parent.mkdir(parents=True, exist_ok=True)
    example = torch.zeros((1, 3, 224, 224), dtype=torch.float32)
    with torch.inference_mode():
        torch.onnx.export(
            policy,
            (example,),
            args.float_output,
            input_names=["pixel_values"],
            output_names=["filter_probability"],
            opset_version=17,
            do_constant_folding=True,
        )
    onnx.checker.check_model(onnx.load(args.float_output))
    quantize_dynamic(args.float_output, args.int8_output, weight_type=QuantType.QInt8, per_channel=True)
    onnx.checker.check_model(onnx.load(args.int8_output))

    providers = ["CPUExecutionProvider"]
    float_session = ort.InferenceSession(str(args.float_output), providers=providers)
    by_split: dict[str, Any] = {}
    all_float_predictions: dict[str, dict[str, Any]] = {}
    for split_name in ("validation", "frozen_test"):
        split_records = [record for record in records if record["split"] == split_name]
        predictions, latency = _evaluate_onnx(float_session, split_records, args.image_root, processor, args.runs)
        all_float_predictions.update(predictions)
        by_split[split_name] = {"metrics": evaluate_records(split_records, predictions)["overall"], "latency": latency}

    quantized_result: dict[str, Any]
    all_quantized_predictions: dict[str, dict[str, Any]] = {}
    try:
        quantized_session = ort.InferenceSession(str(args.int8_output), providers=providers)
        for split_name in ("validation", "frozen_test"):
            split_records = [record for record in records if record["split"] == split_name]
            predictions, latency = _evaluate_onnx(quantized_session, split_records, args.image_root, processor, args.runs)
            all_quantized_predictions.update(predictions)
            by_split[split_name]["quantized_metrics"] = evaluate_records(split_records, predictions)["overall"]
            by_split[split_name]["quantized_latency"] = latency
        quantized_result = {"status": "local_ort_passed", "predictions": all_quantized_predictions}
    except Exception as error:  # local ORT may lack ConvInteger while Android ORT is the target runtime
        quantized_result = {"status": "local_ort_failed", "error_type": type(error).__name__, "error": str(error)}

    prediction_rows = list(all_float_predictions.values())
    args.predictions_output.write_text("\n".join(json.dumps(row) for row in prediction_rows) + "\n", encoding="utf-8")
    report = {
        "schema_version": "gloshia-r2-candidate-export-v1",
        "status": "research_only_not_approved_for_apk",
        "model_id": MODEL_ID,
        "threshold": 0.4,
        "preprocessing": DAG_LETTERBOX,
        "input": {"name": "pixel_values", "shape": [1, 3, 224, 224], "type": "float32"},
        "output": {"name": "filter_probability", "shape": [1, 1], "type": "float32"},
        "float_model": {
            "path": str(args.float_output),
            "bytes": args.float_output.stat().st_size,
            "sha256": _sha256(args.float_output),
            "graph": _graph_summary(args.float_output),
        },
        "int8_model": {
            "path": str(args.int8_output),
            "bytes": args.int8_output.stat().st_size,
            "sha256": _sha256(args.int8_output),
            "graph": _graph_summary(args.int8_output),
            "quantization": "dynamic_qint8_per_channel",
            "validation": "onnx_checker_passed",
            "runtime": quantized_result,
        },
        "r1_reference": {"path": str(args.r1_onnx), "bytes": args.r1_onnx.stat().st_size, "sha256": _sha256(args.r1_onnx)},
        "evaluation": by_split,
        "float_predictions": all_float_predictions,
        "quantized_predictions": all_quantized_predictions,
        "frozen_test_read_once": True,
        "final_sealed_opened": False,
        "approved_for_apk": False,
    }
    args.report.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(json.dumps({"validation": by_split["validation"], "frozen_test": by_split["frozen_test"], "quantized": quantized_result["status"]}, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
