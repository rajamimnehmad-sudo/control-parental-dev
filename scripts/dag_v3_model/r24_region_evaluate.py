#!/usr/bin/env python3
"""Evaluate ONNX models through DAG's complete regional image policy."""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
from pathlib import Path
from typing import Any

SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from pilot_tinyclip_candidate import MODEL_ID  # noqa: E402
from r2_candidate_evaluate import classification_metrics  # noqa: E402
from r24_region_policy import dag_region_views, exact_policy_decision, policy_margin  # noqa: E402


def parse_model_spec(value: str) -> tuple[str, Path]:
    label, separator, path = value.partition("=")
    if not separator or not label.strip() or not path.strip():
        raise argparse.ArgumentTypeError("model must use label=/path/model.onnx")
    return label.strip(), Path(path).expanduser()


def _sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def evaluate(session: Any, records: list[dict[str, Any]], processor: Any) -> dict[str, Any]:
    import numpy as np
    from PIL import Image, ImageOps

    input_name = session.get_inputs()[0].name
    output_name = session.get_outputs()[0].name
    cases: list[dict[str, Any]] = []
    scores: list[float] = []
    predicted: list[int] = []
    total_inferences = 0
    for record in records:
        with Image.open(record["image_path"]) as opened:
            image = ImageOps.exif_transpose(opened).convert("RGB")
        views = dag_region_views(image)
        probabilities: list[float] = []
        for view in views.images:
            pixels = processor(images=[view], return_tensors="np")["pixel_values"].astype(np.float32)
            value = float(session.run([output_name], {input_name: pixels})[0][0, 0])
            probabilities.append(value)
        decision = exact_policy_decision(probabilities, views.kind)
        margin = float(policy_margin(probabilities, views.kind))
        score = 1.0 / (1.0 + np.exp(-12.0 * margin))
        scores.append(float(score))
        predicted.append(int(decision["action"] == "filter"))
        total_inferences += int(decision["inferences"])
        cases.append(
            {
                "sample_id": record["sample_id"],
                "human_action": record["human_action"],
                "target": record["target"],
                "kind": views.kind,
                "probabilities": probabilities,
                "policy_score": float(score),
                **decision,
            }
        )
    return {
        "metrics": classification_metrics(
            [record["target"] for record in records],
            scores,
            predicted=predicted,
        ),
        "total_inferences": total_inferences,
        "mean_inferences": round(total_inferences / len(records), 4),
        "cases": cases,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--split", required=True, type=Path)
    parser.add_argument("--split-name", required=True)
    parser.add_argument("--model", action="append", required=True, type=parse_model_spec)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    import onnxruntime as ort
    from transformers import AutoProcessor

    payload = json.loads(args.split.read_text(encoding="utf-8"))
    records = [row for row in payload.get("records", []) if row.get("split") == args.split_name]
    if not records:
        raise ValueError(f"split is empty: {args.split_name}")
    processor = AutoProcessor.from_pretrained(MODEL_ID, local_files_only=True)
    models: dict[str, Any] = {}
    for label, path in args.model:
        path = path.resolve()
        session = ort.InferenceSession(str(path), providers=["CPUExecutionProvider"])
        models[label] = {"path": str(path), "sha256": _sha256(path), **evaluate(session, records, processor)}
    report = {
        "schema_version": "gloshia-r24-region-policy-evaluation-v1",
        "ticket": "GLOSHIA-R2.4-REGION-AWARE-TRAINING-GATE-20",
        "split": args.split_name,
        "samples": len(records),
        "models": models,
        "final_sealed_opened": False,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(json.dumps({label: result["metrics"] for label, result in models.items()}, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
