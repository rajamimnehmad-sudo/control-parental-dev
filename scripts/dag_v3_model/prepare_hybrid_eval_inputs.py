#!/usr/bin/env python3
"""Prepare frozen tensor inputs for the existing selective R3 exporter."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

import numpy as np

from pilot_tinyclip_candidate import _dag_letterbox_image, MODEL_ID


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--split", required=True, type=Path)
    parser.add_argument("--predictions", required=True, type=Path)
    parser.add_argument("--image-root", required=True, type=Path)
    parser.add_argument("--metadata", required=True, type=Path)
    parser.add_argument("--tensors", required=True, type=Path)
    args = parser.parse_args()

    from PIL import Image, ImageOps
    from transformers import AutoProcessor

    split = json.loads(args.split.read_text(encoding="utf-8"))
    records = [row for row in split["records"] if row["split"] in ("validation", "frozen_test")]
    predictions = {
        row["sample_id"]: row
        for row in (
            json.loads(line)
            for line in args.predictions.read_text(encoding="utf-8").splitlines()
            if line.strip()
        )
    }
    processor = AutoProcessor.from_pretrained(MODEL_ID)
    tensor_rows: list[np.ndarray] = []
    samples: list[dict[str, object]] = []
    for record in records:
        prediction = predictions[record["sample_id"]]
        with Image.open(args.image_root / record["image_path"]) as opened:
            image = ImageOps.exif_transpose(opened).convert("RGB")
        image = _dag_letterbox_image(image)
        pixels = processor(images=[image], return_tensors="np")["pixel_values"].astype(np.float32)
        tensor_rows.append(pixels)
        samples.append(
            {
                "sample_id": record["sample_id"],
                "split": record["split"],
                "human_action": record["human_action"],
                "fp32_probability": float(prediction["filter_probability"]),
                "fp32_action": prediction["predicted_action"],
            }
        )
    args.metadata.parent.mkdir(parents=True, exist_ok=True)
    args.metadata.write_text(
        json.dumps(
            {
                "schema_version": "gloshia-r3-hybrid-export-inputs-v1",
                "threshold": 0.4,
                "model_id": MODEL_ID,
                "samples": samples,
            },
            indent=2,
            ensure_ascii=False,
        )
        + "\n",
        encoding="utf-8",
    )
    np.concatenate(tensor_rows, axis=0).astype("<f4").tofile(args.tensors)
    print(json.dumps({"samples": len(samples), "tensor_bytes": args.tensors.stat().st_size}, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
