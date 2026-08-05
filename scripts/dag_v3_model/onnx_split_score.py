#!/usr/bin/env python3
"""Score a fixed binary split with one ONNX model using the DAG contract."""

from __future__ import annotations

import argparse
import hashlib
import json
import statistics
import time
from pathlib import Path

import numpy as np

from pilot_tinyclip_candidate import MODEL_ID, _dag_letterbox_image
from r2_candidate_evaluate import evaluate_records


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def _preprocess(processor: object, path: Path) -> np.ndarray:
    from PIL import Image, ImageOps

    with Image.open(path) as opened:
        image = ImageOps.exif_transpose(opened).convert("RGB")
    image = _dag_letterbox_image(image)
    return processor(images=[image], return_tensors="np")["pixel_values"].astype(np.float32)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model", required=True, type=Path)
    parser.add_argument("--split", required=True, type=Path)
    parser.add_argument("--image-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--model-label", required=True)
    parser.add_argument("--threshold", type=float, default=0.4)
    parser.add_argument("--runs", type=int, default=20)
    args = parser.parse_args()
    if args.runs < 5:
        raise ValueError("--runs must be at least 5")

    import onnx
    import onnxruntime as ort
    from transformers import AutoProcessor

    onnx.checker.check_model(onnx.load(args.model))
    payload = json.loads(args.split.read_text(encoding="utf-8"))
    records = [row for row in payload["records"] if row["split"] in ("validation", "frozen_test")]
    processor = AutoProcessor.from_pretrained(MODEL_ID)
    session = ort.InferenceSession(str(args.model), providers=["CPUExecutionProvider"])
    predictions: list[dict[str, object]] = []
    timings: list[float] = []
    for record in records:
        pixels = _preprocess(processor, args.image_root / record["image_path"])
        started = time.perf_counter()
        probability = float(session.run(None, {"pixel_values": pixels})[0][0, 0])
        timings.append((time.perf_counter() - started) * 1000)
        predictions.append({
            "sample_id": record["sample_id"],
            "filter_probability": probability,
            "predicted_action": "filter" if probability >= args.threshold else "allow",
        })
    split_reports = {}
    for split_name in ("validation", "frozen_test"):
        split_records = [row for row in records if row["split"] == split_name]
        split_ids = {row["sample_id"] for row in split_records}
        split_predictions = {row["sample_id"]: row for row in predictions if row["sample_id"] in split_ids}
        split_reports[split_name] = evaluate_records(split_records, split_predictions, args.threshold)
    timings_sorted = sorted(timings)
    p95_index = max(0, min(len(timings_sorted) - 1, int(len(timings_sorted) * 0.95) - 1))
    report = {
        "schema_version": "gloshia-onnx-split-score-v1",
        "model_label": args.model_label,
        "model_path": str(args.model),
        "model_bytes": args.model.stat().st_size,
        "model_sha256": _sha256(args.model),
        "threshold": args.threshold,
        "split_schema": payload.get("schema_version"),
        "input_contract": {"shape": [1, 3, 224, 224], "type": "float32", "preprocessing": "dag-letterbox"},
        "runtime": "onnxruntime CPUExecutionProvider",
        "metrics": split_reports,
        "latency_ms": {
            "samples": len(timings),
            "mean": statistics.mean(timings),
            "p50": statistics.median(timings),
            "p95": timings_sorted[p95_index],
            "max": max(timings),
        },
        "predictions": predictions,
        "final_sealed_opened": False,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(json.dumps({"model": args.model_label, "validation": split_reports["validation"]["overall"], "frozen_test": split_reports["frozen_test"]["overall"], "latency_ms": report["latency_ms"]}, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
