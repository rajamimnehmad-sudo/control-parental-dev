#!/usr/bin/env python3
"""Try Android-oriented ONNX export formats without changing R2.1 weights."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import statistics
import time
from pathlib import Path
from typing import Any

import numpy as np

from r2_candidate_evaluate import evaluate_records
from r2_candidate_export import _preprocess


class _CalibrationReader:
    def __init__(self, records: list[dict[str, Any]], image_root: Path, processor: Any) -> None:
        self._records = records
        self._image_root = image_root
        self._processor = processor
        self._index = 0

    def get_next(self) -> dict[str, np.ndarray] | None:
        if self._index >= len(self._records):
            return None
        record = self._records[self._index]
        self._index += 1
        return {"pixel_values": _preprocess(self._processor, self._image_root / record["image_path"])}


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _graph(path: Path) -> dict[str, Any]:
    import onnx
    from collections import Counter

    model = onnx.load(path)
    return {
        "ir_version": model.ir_version,
        "opset": [{"domain": item.domain, "version": item.version} for item in model.opset_import],
        "nodes": len(model.graph.node),
        "operators": dict(sorted(Counter(node.op_type for node in model.graph.node).items())),
    }


def _finite(value: Any) -> bool:
    return bool(np.all(np.isfinite(np.asarray(value))))


def _benchmark(session: Any, pixels: np.ndarray, runs: int) -> dict[str, Any]:
    output_name = session.get_outputs()[0].name
    for _ in range(5):
        session.run([output_name], {"pixel_values": pixels})
    timings: list[float] = []
    for _ in range(runs):
        started = time.perf_counter()
        session.run([output_name], {"pixel_values": pixels})
        timings.append((time.perf_counter() - started) * 1000)
    timings.sort()
    percentile = lambda fraction: timings[max(0, min(len(timings) - 1, int(len(timings) * fraction) - 1))]
    return {
        "runs": runs,
        "mean_ms": round(statistics.mean(timings), 3),
        "p50_ms": round(statistics.median(timings), 3),
        "p90_ms": round(percentile(0.90), 3),
        "p95_ms": round(percentile(0.95), 3),
        "max_ms": round(max(timings), 3),
        "environment": "Mac CPU laboratory benchmark, not Android",
    }


def _evaluate(
    path: Path,
    records: list[dict[str, Any]],
    image_root: Path,
    processor: Any,
    runs: int,
) -> dict[str, Any]:
    import onnxruntime as ort

    result: dict[str, Any] = {"path": str(path), "bytes": path.stat().st_size, "sha256": _sha256(path)}
    try:
        session = ort.InferenceSession(str(path), providers=["CPUExecutionProvider"])
        output_name = session.get_outputs()[0].name
        first_pixels: np.ndarray | None = None
        predictions: dict[str, dict[str, Any]] = {}
        finite = True
        nonfinite_sample_ids: list[str] = []
        for record in records:
            pixels = _preprocess(processor, image_root / record["image_path"])
            if first_pixels is None:
                first_pixels = pixels
            output = session.run([output_name], {"pixel_values": pixels})[0]
            finite = finite and _finite(output)
            if not _finite(output):
                nonfinite_sample_ids.append(record["sample_id"])
            probability = float(np.asarray(output).reshape(-1)[0])
            predictions[record["sample_id"]] = {
                "sample_id": record["sample_id"],
                "filter_probability": probability if math.isfinite(probability) else None,
                "predicted_action": (
                    "filter" if probability >= 0.4 else "allow"
                ) if math.isfinite(probability) else None,
            }
        if first_pixels is None:
            raise ValueError("no records available for inference")
        result.update(
            {
                "runtime": "local_onnxruntime_cpu_passed",
                "input_names": [item.name for item in session.get_inputs()],
                "output_names": [item.name for item in session.get_outputs()],
                "finite_outputs": finite,
                "predictions": predictions,
                "latency": _benchmark(session, first_pixels, runs),
                "by_split": {},
                "metrics_available": finite,
                "nonfinite_sample_ids": nonfinite_sample_ids,
            }
        )
        if finite:
            for split_name in ("validation", "frozen_test"):
                split_records = [row for row in records if row["split"] == split_name]
                result["by_split"][split_name] = evaluate_records(split_records, predictions)["overall"]
    except Exception as error:
        result.update(
            {
                "runtime": "local_onnxruntime_cpu_failed",
                "error_type": type(error).__name__,
                "error": str(error),
                "finite_outputs": False,
            }
        )
    return result


def _make_static(input_path: Path, output_path: Path, records: list[dict[str, Any]], image_root: Path, processor: Any, quant_format: Any) -> None:
    from onnxruntime.quantization import CalibrationMethod, QuantType, quantize_static

    quantize_static(
        model_input=str(input_path),
        model_output=str(output_path),
        calibration_data_reader=_CalibrationReader(records, image_root, processor),
        quant_format=quant_format,
        per_channel=True,
        activation_type=QuantType.QInt8,
        weight_type=QuantType.QInt8,
        calibrate_method=CalibrationMethod.MinMax,
        extra_options={"ActivationSymmetric": True, "WeightSymmetric": True},
    )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--fp32", required=True, type=Path)
    parser.add_argument("--split", required=True, type=Path)
    parser.add_argument("--image-root", required=True, type=Path)
    parser.add_argument("--output-dir", required=True, type=Path)
    parser.add_argument("--report", required=True, type=Path)
    parser.add_argument("--runs", type=int, default=30)
    args = parser.parse_args()
    if args.runs < 5:
        raise ValueError("--runs must be at least 5")

    import onnx
    import onnxruntime as ort
    from onnxruntime.quantization import QuantFormat
    from onnxruntime.transformers.onnx_model import OnnxModel
    from onnxruntime.transformers.float16 import convert_float_to_float16
    from transformers import AutoProcessor
    from pilot_tinyclip_candidate import MODEL_ID

    split = json.loads(args.split.read_text(encoding="utf-8"))
    records = [row for row in split["records"] if row["split"] in ("train", "validation", "frozen_test")]
    calibration_records = [row for row in records if row["split"] == "train"]
    evaluation_records = [row for row in records if row["split"] in ("validation", "frozen_test")]
    processor = AutoProcessor.from_pretrained(MODEL_ID)
    args.output_dir.mkdir(parents=True, exist_ok=True)
    candidates = {
        "qdq_int8": args.output_dir / "r2.1-qdq-int8.onnx",
        "qlinearops_int8": args.output_dir / "r2.1-qlinearops-int8.onnx",
        "fp16": args.output_dir / "r2.1-fp16.onnx",
        "fp32_optimized": args.output_dir / "r2.1-fp32-optimized.onnx",
    }
    _make_static(args.fp32, candidates["qdq_int8"], calibration_records, args.image_root, processor, QuantFormat.QDQ)
    _make_static(args.fp32, candidates["qlinearops_int8"], calibration_records, args.image_root, processor, QuantFormat.QOperator)
    fp16_model = convert_float_to_float16(onnx.load(str(args.fp32)), keep_io_types=True)
    OnnxModel.graph_topological_sort(fp16_model.graph, is_deterministic=True)
    onnx.save(fp16_model, str(candidates["fp16"]))
    options = ort.SessionOptions()
    options.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_EXTENDED
    options.optimized_model_filepath = str(candidates["fp32_optimized"])
    ort.InferenceSession(str(args.fp32), sess_options=options, providers=["CPUExecutionProvider"])
    onnx.checker.check_model(onnx.load(str(args.fp32)))

    output: dict[str, Any] = {
        "schema_version": "gloshia-r2.1-android-export-gate-v1",
        "ticket": "GLOSHIA-VISUAL-R2.1-ANDROID-EXPORT-GATE-11",
        "status": "research_only_not_approved_for_apk",
        "runtime_python": {"version": ort.__version__, "providers": ort.get_available_providers()},
        "runtime_android_dependency": "com.microsoft.onnxruntime:onnxruntime-android:1.27.0",
        "contract": {"input": "pixel_values float32 [1,3,224,224]", "output": "filter_probability float32 [1,1]", "threshold": 0.4, "preprocessing": "dag-letterbox"},
        "calibration": {"split": "train", "samples": len(calibration_records), "frozen_test_used": False},
        "source_fp32": {"path": str(args.fp32), "sha256": _sha256(args.fp32), "bytes": args.fp32.stat().st_size},
        "candidates": {},
        "final_sealed_opened": False,
        "approved_for_apk": False,
    }
    for name, path in candidates.items():
        onnx.checker.check_model(onnx.load(str(path)))
        item = {"format": name, "checker": "passed", "graph": _graph(path)}
        item.update(_evaluate(path, evaluation_records, args.image_root, processor, args.runs))
        output["candidates"][name] = item
    reference = _evaluate(args.fp32, evaluation_records, args.image_root, processor, args.runs)
    output["fp32_reference_evaluation"] = reference
    for item in output["candidates"].values():
        candidate_predictions = item.get("predictions", {})
        reference_predictions = reference.get("predictions", {})
        if not item.get("metrics_available") or not reference.get("metrics_available"):
            item["equivalence_to_fp32"] = {"status": "not_available"}
            continue
        comparisons = []
        for split_name in ("validation", "frozen_test"):
            ids = [row["sample_id"] for row in evaluation_records if row["split"] == split_name]
            same_decision = sum(
                candidate_predictions[sample_id]["predicted_action"]
                == reference_predictions[sample_id]["predicted_action"]
                for sample_id in ids
            )
            deltas = [
                abs(
                    candidate_predictions[sample_id]["filter_probability"]
                    - reference_predictions[sample_id]["filter_probability"]
                )
                for sample_id in ids
            ]
            comparisons.append(
                {
                    "split": split_name,
                    "same_decisions": same_decision,
                    "samples": len(ids),
                    "rate": round(same_decision / len(ids), 6),
                    "max_abs_probability_delta": round(max(deltas), 8),
                    "mean_abs_probability_delta": round(float(np.mean(deltas)), 8),
                }
            )
        item["equivalence_to_fp32"] = {"status": "measured", "splits": comparisons}
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(output, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(json.dumps({name: {"runtime": item["runtime"], "bytes": item["bytes"], "sha256": item["sha256"]} for name, item in output["candidates"].items()}, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
