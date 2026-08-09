#!/usr/bin/env python3
"""Build owner-review batches for the directed GloshIA R4 crop corpus."""

from __future__ import annotations

import argparse
import hashlib
import json
import shutil
import sys
from collections import defaultdict
from pathlib import Path
from typing import Any


SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from onnx_split_score import _preprocess  # noqa: E402
from pilot_tinyclip_candidate import MODEL_ID  # noqa: E402
from r4_circle_review_queue import circle_center_crop, excluded_groups, render_contact_sheet  # noqa: E402


def _group(row: dict[str, Any]) -> str:
    return str(row.get("group_key") or row.get("source_cluster") or "")


def hard_priority(row: dict[str, Any]) -> tuple[Any, ...]:
    probability = float(row["r3_probability"])
    parent_target = int(row["parent_target"])
    predicted = int(probability >= 0.4)
    mismatch = predicted != parent_target
    wrong_margin = (0.4 - probability) if parent_target else (probability - 0.4)
    return (not mismatch, -wrong_margin, abs(probability - 0.4), str(row["sample_id"]))


def select_stratified(candidates: list[dict[str, Any]], plan: dict[str, Any]) -> list[dict[str, Any]]:
    indexed: dict[tuple[str, int], list[dict[str, Any]]] = defaultdict(list)
    for row in candidates:
        indexed[(str(row["category"]), int(row["parent_target"]))].append(row)
    selected = []
    for stratum in plan["strata"]:
        category = str(stratum["category"])
        for label, key in ((0, "parent_allow"), (1, "parent_filter")):
            required = int(stratum[key])
            available = sorted(indexed[(category, label)], key=hard_priority)
            if len(available) < required:
                raise ValueError(f"insufficient candidates for {category}/{label}: {len(available)} < {required}")
            selected.extend(available[:required])
    expected = int(plan["authorized_total"])
    if len(selected) != expected or len({_group(row) for row in selected}) != expected:
        raise ValueError("targeted selection count or group uniqueness failed")
    return selected


def assign_batches(rows: list[dict[str, Any]], batch_size: int) -> list[list[dict[str, Any]]]:
    if batch_size < 1 or len(rows) % batch_size:
        raise ValueError("rows must divide exactly into positive batch size")
    batches: list[list[dict[str, Any]]] = [[] for _ in range(len(rows) // batch_size)]
    strata: dict[tuple[str, int], list[dict[str, Any]]] = defaultdict(list)
    for row in rows:
        strata[(str(row["category"]), int(row["parent_target"]))].append(row)
    for key in sorted(strata):
        for row in sorted(strata[key], key=hard_priority):
            eligible = [index for index, batch in enumerate(batches) if len(batch) < batch_size]
            target = min(eligible, key=lambda index: (len(batches[index]), index))
            batches[target].append(row)
    if any(len(batch) != batch_size for batch in batches):
        raise ValueError("batch assignment is incomplete")
    return batches


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--split", required=True, type=Path)
    parser.add_argument("--model", required=True, type=Path)
    parser.add_argument("--plan", required=True, type=Path)
    parser.add_argument("--exclude-manifest", action="append", required=True, type=Path)
    parser.add_argument("--output-root", required=True, type=Path)
    args = parser.parse_args()
    if args.output_root.exists():
        raise FileExistsError(args.output_root)

    import onnxruntime as ort
    from PIL import Image
    from transformers import AutoProcessor

    split = json.loads(args.split.read_text(encoding="utf-8"))
    plan = json.loads(args.plan.read_text(encoding="utf-8"))
    excluded = excluded_groups([json.loads(path.read_text(encoding="utf-8")) for path in args.exclude_manifest])
    originals = [
        row
        for row in split["records"]
        if row.get("split") == "train"
        and not row.get("parent_sample_id")
        and _group(row) not in excluded
    ]
    processor = AutoProcessor.from_pretrained(MODEL_ID, local_files_only=True)
    session = ort.InferenceSession(str(args.model), providers=["CPUExecutionProvider"])
    candidate_dir = args.output_root / "candidates"
    candidate_dir.mkdir(parents=True, exist_ok=False)
    candidates = []
    for row in originals:
        source = Path(row["image_path"])
        if not source.is_file():
            raise FileNotFoundError(source)
        with Image.open(source) as opened:
            crop = circle_center_crop(opened)
        name = hashlib.sha256(f"{row['sample_id']}:targeted-circle-v1".encode()).hexdigest()[:24] + ".jpg"
        destination = candidate_dir / name
        crop.save(destination, format="JPEG", quality=45, optimize=True)
        probability = float(session.run(None, {"pixel_values": _preprocess(processor, destination)})[0][0, 0])
        candidates.append(
            {
                "sample_id": f"{row['sample_id']}:targeted:circle-center-cover128-q45",
                "parent_sample_id": row["sample_id"],
                "image_path": str(destination.resolve()),
                "parent_image_path": row["image_path"],
                "parent_target": int(row["target"]),
                "parent_human_action": row.get("human_action"),
                "category": row.get("category"),
                "group_key": _group(row),
                "r3_probability": round(probability, 8),
                "r3_action": "filter" if probability >= 0.4 else "allow",
                "review_status": "pending",
                "review_action": None,
                "training_authorized": False,
            }
        )

    selected = select_stratified(candidates, plan)
    batches = assign_batches(selected, int(plan["batch_size"]))
    selected_names = {Path(row["image_path"]).name for row in selected}
    for path in candidate_dir.iterdir():
        if path.name not in selected_names:
            path.unlink()

    all_records = []
    for batch_index, batch in enumerate(batches, start=1):
        batch_dir = args.output_root / f"batch-{batch_index:02d}"
        image_dir = batch_dir / "images"
        image_dir.mkdir(parents=True, exist_ok=False)
        batch_records = []
        for review_number, source_row in enumerate(batch, start=1):
            row = dict(source_row)
            source = Path(row["image_path"])
            destination = image_dir / source.name
            shutil.move(str(source), destination)
            row.update(
                {
                    "image_path": str(destination.resolve()),
                    "batch_number": batch_index,
                    "review_number": review_number,
                    "global_review_number": (batch_index - 1) * int(plan["batch_size"]) + review_number,
                }
            )
            batch_records.append(row)
            all_records.append(row)
        batch_manifest = {
            "schema_version": "gloshia-r4-targeted-review-batch-v1",
            "status": "review_only_not_training_data",
            "batch_number": batch_index,
            "selected_count": len(batch_records),
            "policy_schema_version": "gloshia-r4-owner-visual-policy-v1",
            "training_authorized": False,
            "frozen_test_opened": False,
            "final_sealed_opened": False,
            "records": batch_records,
        }
        (batch_dir / "manifest.json").write_text(json.dumps(batch_manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        render_contact_sheet(batch_records, batch_dir / "review-sheet.png")
    candidate_dir.rmdir()

    report = {
        "schema_version": "gloshia-r4-targeted-review-queue-v1",
        "status": "review_only_not_training_data",
        "plan_schema_version": plan.get("schema_version"),
        "candidate_count": len(candidates),
        "excluded_reviewed_groups": len(excluded),
        "selected_count": len(all_records),
        "batch_count": len(batches),
        "training_authorized": False,
        "frozen_test_opened": False,
        "final_sealed_opened": False,
        "records": sorted(all_records, key=lambda row: int(row["global_review_number"])),
    }
    (args.output_root / "manifest.json").write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(json.dumps({key: report[key] for key in ("candidate_count", "excluded_reviewed_groups", "selected_count", "batch_count")}, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
