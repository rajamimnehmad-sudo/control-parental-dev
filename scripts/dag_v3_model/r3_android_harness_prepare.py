#!/usr/bin/env python3
"""Prepare reproducible R3 tensors and configuration for the Android ORT harness."""

from __future__ import annotations

import argparse
import hashlib
import json
import shutil
from pathlib import Path
from typing import Any

import numpy as np

from pilot_tinyclip_candidate import MODEL_ID
from r2_candidate_export import _preprocess


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def sample_metadata(record: dict[str, Any], probability: float, threshold: float) -> dict[str, Any]:
    return {
        "sample_id": record["sample_id"],
        "split": record["split"],
        "human_action": record["human_action"],
        "fp32_probability": probability,
        "fp32_action": "filter" if probability >= threshold else "allow",
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--split", required=True, type=Path)
    parser.add_argument("--fp32", required=True, type=Path)
    parser.add_argument("--int8", required=True, type=Path)
    parser.add_argument("--r1", required=True, type=Path)
    parser.add_argument("--threshold", required=True, type=float)
    parser.add_argument("--output-dir", required=True, type=Path)
    args = parser.parse_args()

    import onnxruntime as ort
    from transformers import AutoProcessor

    payload = json.loads(args.split.read_text())
    records = [row for row in payload["records"] if row["split"] in {"validation", "frozen_test"}]
    if len(records) != 119:
        raise ValueError(f"expected 119 evaluation samples, got {len(records)}")
    processor = AutoProcessor.from_pretrained(MODEL_ID, local_files_only=True)
    session = ort.InferenceSession(str(args.fp32), providers=["CPUExecutionProvider"])
    args.output_dir.mkdir(parents=True, exist_ok=True)
    tensor_path = args.output_dir / "evaluation-inputs.bin"
    samples = []
    with tensor_path.open("wb") as tensor_file:
        for record in records:
            pixels = _preprocess(processor, Path(record["image_path"]))
            if pixels.shape != (1, 3, 224, 224) or pixels.dtype != np.float32:
                raise ValueError("unexpected preprocessed tensor contract")
            tensor_file.write(pixels.astype("<f4", copy=False).tobytes())
            probability = float(session.run(["filter_probability"], {"pixel_values": pixels})[0][0, 0])
            samples.append(sample_metadata(record, probability, args.threshold))

    candidate_name = "r3-head-01-selective-int8.onnx"
    shutil.copyfile(args.int8, args.output_dir / candidate_name)
    shutil.copyfile(args.r1, args.output_dir / "r1-official.onnx")
    metadata = {
        "schema_version": "gloshia-r3-head-01-android-evaluation-v1",
        "threshold": args.threshold,
        "samples": samples,
        "final_sealed_opened": False,
    }
    metadata_path = args.output_dir / "evaluation-metadata.json"
    metadata_path.write_text(json.dumps(metadata, indent=2, ensure_ascii=False) + "\n")
    config = {
        "schema_version": "gloshia-r3-head-01-android-gate-v1",
        "ticket": "GLOSHIA-R3-ANDROID-EQUIVALENCE-24",
        "candidate_name": candidate_name,
        "candidate_sha256": sha256(args.output_dir / candidate_name),
        "threshold": args.threshold,
    }
    config_path = args.output_dir / "harness-config.json"
    config_path.write_text(json.dumps(config, indent=2) + "\n")
    report = {
        "config": config,
        "samples": len(samples),
        "by_split": {
            name: sum(row["split"] == name for row in samples)
            for name in ("validation", "frozen_test")
        },
        "files": {
            path.name: {"bytes": path.stat().st_size, "sha256": sha256(path)}
            for path in (tensor_path, metadata_path, config_path, args.output_dir / candidate_name)
        },
        "final_sealed_opened": False,
    }
    (args.output_dir / "prepare-report.json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

