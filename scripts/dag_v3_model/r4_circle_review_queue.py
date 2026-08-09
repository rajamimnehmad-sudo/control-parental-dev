#!/usr/bin/env python3
"""Build a small human-review queue of real center-cropped circle thumbnails.

The parent decision is retained only as diagnostic context. It is deliberately
not copied into a training target because the crop may remove the visual fact
that justified the original label.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
from collections import defaultdict
from pathlib import Path
from typing import Any

from PIL import Image, ImageDraw, ImageFont, ImageOps


SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from onnx_split_score import _preprocess  # noqa: E402


def circle_center_crop(source: Image.Image, size: int = 128) -> Image.Image:
    image = ImageOps.exif_transpose(source).convert("RGB")
    square = ImageOps.fit(image, (size, size), method=Image.Resampling.LANCZOS, centering=(0.5, 0.5))
    canvas = Image.new("RGB", (size, size), (128, 128, 128))
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, size - 1, size - 1), fill=255)
    canvas.paste(square, (0, 0), mask)
    return canvas


def select_review_rows(rows: list[dict[str, Any]], per_parent_label: int) -> list[dict[str, Any]]:
    by_label: dict[int, list[dict[str, Any]]] = defaultdict(list)
    for row in rows:
        by_label[int(row["parent_target"])].append(row)

    def priority(row: dict[str, Any]) -> tuple[Any, ...]:
        probability = float(row["r3_probability"])
        target = int(row["parent_target"])
        predicted = int(probability >= 0.4)
        mismatch = predicted != target
        wrong_margin = (0.4 - probability) if target else (probability - 0.4)
        return (not mismatch, -wrong_margin, abs(probability - 0.4), str(row["sample_id"]))

    selected = []
    for label in sorted(by_label):
        selected.extend(sorted(by_label[label], key=priority)[:per_parent_label])
    return sorted(selected, key=lambda row: (int(row["parent_target"]), str(row["sample_id"])))


def excluded_groups(payloads: list[dict[str, Any]]) -> set[str]:
    return {
        str(row.get("group_key") or row.get("source_cluster"))
        for payload in payloads
        for row in payload.get("records", [])
        if row.get("group_key") or row.get("source_cluster")
    }


def render_contact_sheet(rows: list[dict[str, Any]], output: Path, columns: int = 4) -> None:
    if columns < 1 or not rows:
        raise ValueError("contact sheet needs rows and positive columns")
    tile_width, tile_height = 224, 244
    sheet_rows = (len(rows) + columns - 1) // columns
    sheet = Image.new("RGB", (columns * tile_width, sheet_rows * tile_height), (245, 245, 245))
    draw = ImageDraw.Draw(sheet)
    try:
        font = ImageFont.load_default(size=24)
    except TypeError:
        font = ImageFont.load_default()
    for index, row in enumerate(rows):
        number = int(row.get("review_number") or index + 1)
        left = (index % columns) * tile_width
        top = (index // columns) * tile_height
        with Image.open(row["image_path"]) as opened:
            image = ImageOps.contain(opened.convert("RGB"), (208, 208), method=Image.Resampling.NEAREST)
        image_left = left + (tile_width - image.width) // 2
        sheet.paste(image, (image_left, top + 32))
        draw.text((left + 8, top + 3), str(number), fill=(10, 10, 10), font=font)
        draw.rectangle((left, top, left + tile_width - 1, top + tile_height - 1), outline=(170, 170, 170), width=1)
    output.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(output, format="PNG", optimize=True)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--split", required=True, type=Path)
    parser.add_argument("--model", required=True, type=Path)
    parser.add_argument("--output-dir", required=True, type=Path)
    parser.add_argument("--manifest", required=True, type=Path)
    parser.add_argument("--per-parent-label", type=int, default=12)
    parser.add_argument("--exclude-manifest", action="append", type=Path, default=[])
    parser.add_argument("--contact-sheet", type=Path)
    args = parser.parse_args()
    if args.per_parent_label < 1:
        raise ValueError("--per-parent-label must be positive")
    if args.output_dir.exists():
        raise FileExistsError(args.output_dir)

    import onnxruntime as ort
    from transformers import AutoProcessor
    from pilot_tinyclip_candidate import MODEL_ID

    payload = json.loads(args.split.read_text(encoding="utf-8"))
    excluded = excluded_groups(
        [json.loads(path.read_text(encoding="utf-8")) for path in args.exclude_manifest]
    )
    originals = [
        row
        for row in payload["records"]
        if row.get("split") == "train"
        and not row.get("parent_sample_id")
        and str(row.get("group_key") or row.get("source_cluster")) not in excluded
    ]
    processor = AutoProcessor.from_pretrained(MODEL_ID, local_files_only=True)
    session = ort.InferenceSession(str(args.model), providers=["CPUExecutionProvider"])
    args.output_dir.mkdir(parents=True, exist_ok=False)
    candidates = []
    for row in originals:
        source = Path(row["image_path"])
        if not source.is_file():
            raise FileNotFoundError(source)
        with Image.open(source) as opened:
            crop = circle_center_crop(opened)
        name = hashlib.sha256(f"{row['sample_id']}:circle-center-cover128-q45".encode()).hexdigest()[:24] + ".jpg"
        destination = args.output_dir / name
        crop.save(destination, format="JPEG", quality=45, optimize=True)
        probability = float(session.run(None, {"pixel_values": _preprocess(processor, destination)})[0][0, 0])
        candidates.append(
            {
                "sample_id": f"{row['sample_id']}:review:circle-center-cover128-q45",
                "parent_sample_id": row["sample_id"],
                "image_path": str(destination.resolve()),
                "parent_image_path": row["image_path"],
                "parent_target": int(row["target"]),
                "parent_human_action": row.get("human_action"),
                "category": row.get("category"),
                "group_key": row.get("group_key") or row.get("source_cluster") or row.get("series"),
                "r3_probability": round(probability, 8),
                "r3_action": "filter" if probability >= 0.4 else "allow",
                "review_status": "pending",
                "review_action": None,
                "training_authorized": False,
            }
        )
    selected = select_review_rows(candidates, args.per_parent_label)
    for review_number, row in enumerate(selected, start=1):
        row["review_number"] = review_number
    selected_paths = {row["image_path"] for row in selected}
    for path in args.output_dir.iterdir():
        if str(path.resolve()) not in selected_paths:
            path.unlink()
    report = {
        "schema_version": "gloshia-r4-circle-review-queue-v1",
        "status": "review_only_not_training_data",
        "geometry": "center square cover crop, 128px circular mask, JPEG q45",
        "selection": "balanced by parent label; R3/parent disagreements first, then margin severity",
        "candidate_count": len(candidates),
        "excluded_reviewed_groups": len(excluded),
        "selected_count": len(selected),
        "selected_parent_allow": sum(int(row["parent_target"]) == 0 for row in selected),
        "selected_parent_filter": sum(int(row["parent_target"]) == 1 for row in selected),
        "frozen_test_opened": False,
        "final_sealed_opened": False,
        "records": selected,
    }
    args.manifest.parent.mkdir(parents=True, exist_ok=True)
    args.manifest.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    if args.contact_sheet is not None:
        render_contact_sheet(selected, args.contact_sheet)
    print(json.dumps({key: report[key] for key in ("candidate_count", "selected_count", "selected_parent_allow", "selected_parent_filter")}, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
