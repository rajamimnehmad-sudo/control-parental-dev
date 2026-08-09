#!/usr/bin/env python3
"""Score a fixed binary split with one ONNX model using the DAG contract."""

from __future__ import annotations

import argparse
import hashlib
import json
import statistics
import time
from collections import defaultdict
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


def _augmentation_report(
    records: list[dict[str, object]],
    predictions: dict[str, dict[str, object]],
    threshold: float,
) -> dict[str, object]:
    slices: dict[str, list[dict[str, object]]] = defaultdict(list)
    originals: dict[str, dict[str, object]] = {}
    children: list[dict[str, object]] = []
    for row in records:
        variant = str(row.get("augmentation_variant") or "original")
        slices[variant].append(row)
        if variant == "original":
            originals[str(row["sample_id"])] = row
        else:
            children.append(row)
    slice_metrics = {}
    for variant, rows in sorted(slices.items()):
        ids = {str(row["sample_id"]) for row in rows}
        slice_metrics[variant] = evaluate_records(
            rows,
            {sample_id: prediction for sample_id, prediction in predictions.items() if sample_id in ids},
            threshold,
        )["overall"]

    flips = unsafe_degradations = safe_degradations = 0
    paired = 0
    for child in children:
        parent_id = str(child.get("parent_sample_id") or "")
        parent = originals.get(parent_id)
        if parent is None:
            continue
        parent_action = str(predictions[parent_id]["predicted_action"])
        child_action = str(predictions[str(child["sample_id"])]["predicted_action"])
        paired += 1
        if parent_action != child_action:
            flips += 1
        target = int(child["target"])
        if target == 1 and parent_action == "filter" and child_action == "allow":
            unsafe_degradations += 1
        if target == 0 and parent_action == "allow" and child_action == "filter":
            safe_degradations += 1
    return {
        "by_variant": slice_metrics,
        "paired_stability": {
            "pairs": paired,
            "decision_flips": flips,
            "decision_flip_rate": round(flips / paired, 6) if paired else 0.0,
            "unsafe_filter_to_allow_degradations": unsafe_degradations,
            "safe_allow_to_filter_degradations": safe_degradations,
        },
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model", required=True, type=Path)
    parser.add_argument("--split", required=True, type=Path)
    parser.add_argument("--image-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--model-label", required=True)
    parser.add_argument("--threshold", type=float, default=0.4)
    parser.add_argument("--runs", type=int, default=20)
    parser.add_argument(
        "--split-name",
        action="append",
        choices=("validation", "frozen_test", "reviewed_pool"),
        dest="split_names",
        help="Score only the named split; repeat to score both. Defaults to both.",
    )
    args = parser.parse_args()
    if args.runs < 5:
        raise ValueError("--runs must be at least 5")

    import onnx
    import onnxruntime as ort
    from transformers import AutoProcessor

    onnx.checker.check_model(onnx.load(args.model))
    payload = json.loads(args.split.read_text(encoding="utf-8"))
    split_names = args.split_names or ["validation", "frozen_test"]
    records = [row for row in payload["records"] if row["split"] in split_names]
    processor = AutoProcessor.from_pretrained(MODEL_ID, local_files_only=True)
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
    augmentation_reports = {}
    prediction_index = {str(row["sample_id"]): row for row in predictions}
    for split_name in split_names:
        split_records = [row for row in records if row["split"] == split_name]
        split_ids = {row["sample_id"] for row in split_records}
        split_predictions = {row["sample_id"]: row for row in predictions if row["sample_id"] in split_ids}
        split_reports[split_name] = evaluate_records(split_records, split_predictions, args.threshold)
        augmentation_reports[split_name] = _augmentation_report(split_records, prediction_index, args.threshold)
    timings_sorted = sorted(timings)
    p95_index = max(0, min(len(timings_sorted) - 1, int(len(timings_sorted) * 0.95) - 1))
    report = {
        "schema_version": "gloshia-onnx-split-score-v2",
        "model_label": args.model_label,
        "model_path": str(args.model),
        "model_bytes": args.model.stat().st_size,
        "model_sha256": _sha256(args.model),
        "threshold": args.threshold,
        "split_schema": payload.get("schema_version"),
        "input_contract": {"shape": [1, 3, 224, 224], "type": "float32", "preprocessing": "dag-letterbox"},
        "runtime": "onnxruntime CPUExecutionProvider",
        "metrics": split_reports,
        "augmentation_metrics": augmentation_reports,
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
    print(json.dumps({
        "model": args.model_label,
        "splits": {name: report["metrics"][name]["overall"] for name in split_names},
        "latency_ms": report["latency_ms"],
    }, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
