#!/usr/bin/env python3
"""Export the frozen R2.2 candidate with the Android-equivalent INT8 contract."""

from __future__ import annotations

import argparse
import hashlib
import json
from collections import Counter
from pathlib import Path
from typing import Any


FP32_NODE_EXCLUSION = "/vision_model/encoder/layers.0/self_attn/k_proj/MatMul"
EXPECTED_FROZEN_SAMPLES = 119
QUANTIZED_OPERATOR_TYPES = (
    "Conv",
    "MatMul",
    "Attention",
    "LSTM",
    "Gather",
    "Transpose",
    "EmbedLayerNormalization",
)


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def quantization_contract() -> dict[str, Any]:
    return {
        "format": "dynamic_qint8_per_channel_selective",
        "quantized_operator_types": list(QUANTIZED_OPERATOR_TYPES),
        "fp32_node_exclusions": [FP32_NODE_EXCLUSION],
        "threshold": 0.4,
        "input": "pixel_values float32 [1,3,224,224]",
        "output": "filter_probability float32 [1,1]",
    }


def android_report_passes(report: dict[str, Any], expected_sha256: str) -> bool:
    candidate = report.get("candidate", {})
    evaluation = report.get("candidate_evaluation", {})
    return bool(
        candidate.get("sha256") == expected_sha256
        and candidate.get("hash_matches") is True
        and evaluation.get("samples") == EXPECTED_FROZEN_SAMPLES
        and evaluation.get("finite_outputs") is True
        and evaluation.get("decision_mismatches_vs_fp32") == 0
        and evaluation.get("false_permissions") == 0
        and evaluation.get("session_closed") is True
    )


def _graph_summary(path: Path) -> dict[str, Any]:
    import onnx

    model = onnx.load(path)
    return {
        "nodes": len(model.graph.node),
        "operators": dict(sorted(Counter(node.op_type for node in model.graph.node).items())),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--fp32", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--report", required=True, type=Path)
    parser.add_argument("--android-report", action="append", default=[], type=Path)
    args = parser.parse_args()

    import onnx
    from onnxruntime.quantization import QuantType, quantize_dynamic

    args.output.parent.mkdir(parents=True, exist_ok=True)
    quantize_dynamic(
        args.fp32,
        args.output,
        weight_type=QuantType.QInt8,
        per_channel=True,
        op_types_to_quantize=list(QUANTIZED_OPERATOR_TYPES),
        nodes_to_exclude=[FP32_NODE_EXCLUSION],
    )
    onnx.checker.check_model(onnx.load(args.output))

    candidate_sha256 = _sha256(args.output)
    android_results = []
    for path in args.android_report:
        payload = json.loads(path.read_text(encoding="utf-8"))
        android_results.append(
            {
                "path": str(path),
                "device": payload.get("device"),
                "passed": android_report_passes(payload, candidate_sha256),
                "evaluation": payload.get("candidate_evaluation"),
            }
        )

    report = {
        "schema_version": "gloshia-r2.2-selective-export-v1",
        "ticket": "GLOSHIA-R2.2-EXPORT-EQUIVALENCE-17",
        "source_fp32": {
            "path": str(args.fp32),
            "bytes": args.fp32.stat().st_size,
            "sha256": _sha256(args.fp32),
        },
        "candidate": {
            "path": str(args.output),
            "bytes": args.output.stat().st_size,
            "sha256": candidate_sha256,
            "graph": _graph_summary(args.output),
        },
        "contract": quantization_contract(),
        "android_results": android_results,
        "status": (
            "GO_EXPORT_EQUIVALENCE"
            if len(android_results) >= 2 and all(item["passed"] for item in android_results)
            else "NOT_ENOUGH_ANDROID_EVIDENCE"
        ),
        "approved_for_productive_dag": False,
    }
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(json.dumps({"status": report["status"], "candidate": report["candidate"]}, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
