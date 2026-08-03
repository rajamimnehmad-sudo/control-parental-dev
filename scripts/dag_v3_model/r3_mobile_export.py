#!/usr/bin/env python3
"""Export a Conv-FP32 / MatMul-INT8 R3 model and verify frozen tensors locally."""

from __future__ import annotations

import argparse
import hashlib
import json
import statistics
import time
from pathlib import Path

import numpy as np

from r22_selective_export import FP32_NODE_EXCLUSION


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--fp32", required=True, type=Path)
    parser.add_argument("--metadata", required=True, type=Path)
    parser.add_argument("--tensors", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--report", required=True, type=Path)
    parser.add_argument("--exclude-node", action="append", default=[])
    args = parser.parse_args()

    import onnx
    import onnxruntime as ort
    from onnxruntime.quantization import QuantType, quantize_dynamic

    args.output.parent.mkdir(parents=True, exist_ok=True)
    quantize_dynamic(
        args.fp32,
        args.output,
        weight_type=QuantType.QInt8,
        per_channel=True,
        op_types_to_quantize=["MatMul"],
        nodes_to_exclude=[FP32_NODE_EXCLUSION, *args.exclude_node],
    )
    onnx.checker.check_model(onnx.load(args.output))
    metadata = json.loads(args.metadata.read_text())
    samples = metadata["samples"]
    values = np.fromfile(args.tensors, dtype="<f4")
    expected_values = len(samples) * 3 * 224 * 224
    if values.size != expected_values:
        raise ValueError("tensor file size does not match metadata")
    tensors = values.reshape(len(samples), 1, 3, 224, 224)
    session = ort.InferenceSession(str(args.output), providers=["CPUExecutionProvider"])
    mismatches = []
    deltas = []
    confusion = {"allow_as_allow": 0, "allow_as_filter": 0, "filter_as_allow": 0, "filter_as_filter": 0}
    threshold = float(metadata["threshold"])
    timings = []
    for sample, pixels in zip(samples, tensors):
        started = time.perf_counter()
        probability = float(session.run(["filter_probability"], {"pixel_values": pixels})[0][0, 0])
        timings.append((time.perf_counter() - started) * 1000)
        action = "filter" if probability >= threshold else "allow"
        expected = sample["fp32_action"]
        delta = abs(probability - float(sample["fp32_probability"]))
        deltas.append(delta)
        human = sample["human_action"]
        confusion[f"{human}_as_{action}"] += 1
        if action != expected:
            mismatches.append({
                "sample_id": sample["sample_id"],
                "split": sample["split"],
                "human_action": human,
                "fp32_action": expected,
                "hybrid_action": action,
                "fp32_probability": sample["fp32_probability"],
                "hybrid_probability": probability,
                "delta": delta,
            })
    timings.sort()
    report = {
        "schema_version": "gloshia-r3-head-01-hybrid-export-v1",
        "candidate": {"bytes": args.output.stat().st_size, "sha256": sha256(args.output)},
        "contract": {
            "quantized_ops": ["MatMul"],
            "conv": "fp32",
            "excluded_nodes": [FP32_NODE_EXCLUSION, *args.exclude_node],
            "threshold": threshold,
        },
        "evaluation": {
            "samples": len(samples),
            "finite_outputs": all(np.isfinite(deltas)),
            "decision_mismatches_vs_fp32": len(mismatches),
            "mismatches": mismatches,
            "mean_abs_probability_delta": statistics.mean(deltas),
            "max_abs_probability_delta": max(deltas),
            "confusion_matrix": confusion,
            "false_permissions": confusion["filter_as_allow"],
            "false_filters": confusion["allow_as_filter"],
            "latency_mac_cpu_ms": {
                "p50": statistics.median(timings),
                "p95": timings[max(0, int(len(timings) * 0.95) - 1)],
            },
        },
        "approved_for_dag": False,
        "final_sealed_opened": False,
    }
    args.report.write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
