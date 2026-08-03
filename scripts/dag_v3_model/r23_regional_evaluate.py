#!/usr/bin/env python3
"""Evaluate frozen ONNX candidates on one named R2.3 split."""

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

from pilot_tinyclip_candidate import MODEL_ID, _dag_letterbox_image  # noqa: E402
from r2_candidate_evaluate import classification_metrics  # noqa: E402


def parse_model_spec(value: str) -> tuple[str, Path]:
    label, separator, raw_path = value.partition("=")
    if not separator or not label.strip() or not raw_path.strip():
        raise argparse.ArgumentTypeError("model must use label=/path/model.onnx")
    return label.strip(), Path(raw_path).expanduser()


def _sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def _pixels(processor: Any, image_path: Path) -> Any:
    import numpy as np
    from PIL import Image, ImageOps

    with Image.open(image_path) as opened:
        image = ImageOps.exif_transpose(opened).convert("RGB")
    image = _dag_letterbox_image(image)
    return processor(images=[image], return_tensors="np")["pixel_values"].astype(np.float32)


def evaluate_model(session: Any, records: list[dict[str, Any]], processor: Any) -> dict[str, Any]:
    input_name = session.get_inputs()[0].name
    output_name = session.get_outputs()[0].name
    probabilities: list[float] = []
    rows: list[dict[str, Any]] = []
    for record in records:
        probability = float(session.run([output_name], {input_name: _pixels(processor, Path(record["image_path"]))})[0][0, 0])
        probabilities.append(probability)
        rows.append(
            {
                "sample_id": record["sample_id"],
                "human_action": record["human_action"],
                "filter_probability": probability,
                "predicted_action": "filter" if probability >= 0.4 else "allow",
            }
        )
    return {
        "metrics": classification_metrics([record["target"] for record in records], probabilities, 0.4),
        "predictions": rows,
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
    records = [record for record in payload.get("records", []) if record.get("split") == args.split_name]
    if not records:
        raise ValueError(f"split is empty: {args.split_name}")
    processor = AutoProcessor.from_pretrained(MODEL_ID, local_files_only=True)
    models: dict[str, Any] = {}
    for label, model_path in args.model:
        model_path = model_path.resolve()
        session = ort.InferenceSession(str(model_path), providers=["CPUExecutionProvider"])
        models[label] = {
            "path": str(model_path),
            "sha256": _sha256(model_path),
            **evaluate_model(session, records, processor),
        }
    report = {
        "schema_version": "gloshia-r23-regional-evaluation-v1",
        "ticket": "GLOSHIA-R2.3-REGIONAL-SAFETY-REPAIR-19",
        "split": args.split_name,
        "samples": len(records),
        "threshold": 0.4,
        "models": models,
        "final_sealed_opened": False,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(json.dumps({label: result["metrics"] for label, result in models.items()}, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
