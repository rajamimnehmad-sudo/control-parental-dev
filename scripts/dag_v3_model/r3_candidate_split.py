#!/usr/bin/env python3
"""Merge completed R3 owner labels into the frozen R2.1 development split."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

from r3_focused_relabel_prepare import (
    _download,
    fetch_wikimedia,
    page_id_batches,
    resolve_local_hashes,
)


def merge_labels(partial: dict[str, Any], focused: dict[str, Any]) -> list[dict[str, Any]]:
    records = {row["sample_id"]: dict(row) for row in partial["records"]}
    for row in focused["rows"]:
        sample_id = row["sample_id"]
        if sample_id not in records:
            raise ValueError(f"focused review is absent from partial audit: {sample_id}")
        records[sample_id]["policy_action"] = row["owner_policy_action"]
        records[sample_id]["labels"] = row["labels"]
        records[sample_id]["label_semantics"] = "owner_review_complete"
        records[sample_id]["reviewed_at"] = row["reviewed_at"]
    return sorted(records.values(), key=lambda row: row["sample_id"])


def resolve_images(records: list[dict[str, Any]], output_dir: Path, search_roots: list[Path]) -> dict[str, Path]:
    output_dir.mkdir(parents=True, exist_ok=True)
    pilot_hashes = {
        row["sample_id"].split(":", 1)[1]
        for row in records
        if row["sample_id"].startswith("pilot:")
    }
    local = resolve_local_hashes(search_roots, pilot_hashes)
    resolved = {f"pilot:{digest}": path for digest, path in local.items()}

    page_ids = sorted(
        int(row["sample_id"].split(":", 1)[1])
        for row in records
        if row["sample_id"].startswith("wikimedia:")
    )
    cache_path = output_dir / "wikimedia-metadata.json"
    metadata: dict[int, dict[str, Any]] = {}
    if cache_path.exists():
        metadata = {int(key): value for key, value in json.loads(cache_path.read_text()).items()}
    for batch in page_id_batches(set(page_ids) - set(metadata)):
        metadata.update(fetch_wikimedia(batch))
        cache_path.write_text(json.dumps(metadata, indent=2, ensure_ascii=False) + "\n")
    for page_id in page_ids:
        item = metadata.get(page_id)
        if item is None:
            continue
        suffix = ".png" if item["mime"] == "image/png" else ".webp" if item["mime"] == "image/webp" else ".jpg"
        destination = output_dir / "images" / f"wikimedia-{page_id}{suffix}"
        if not destination.exists():
            _download(item["asset_url"], destination)
        resolved[f"wikimedia:{page_id}"] = destination.resolve()
    return resolved


def build_split(base: dict[str, Any], records: list[dict[str, Any]], resolved: dict[str, Path]) -> dict[str, Any]:
    base_records = [dict(row) for row in base["records"]]
    base_ids = {row["sample_id"] for row in base_records}
    missing = sorted(row["sample_id"] for row in records if row["sample_id"] not in resolved)
    overlap = sorted(row["sample_id"] for row in records if row["sample_id"] in base_ids)
    if overlap:
        raise ValueError(f"R3 IDs overlap frozen R2.1 split: {len(overlap)}")
    signals = sorted(records[0]["labels"])
    for row in base_records:
        row["signal_labels"] = {signal: "unknown" for signal in signals}
        row["source_kind"] = row.get("source_kind", "legacy_r2_development")
    additions = []
    for row in records:
        if row["sample_id"] not in resolved:
            continue
        action = row["policy_action"]
        additions.append(
            {
                "sample_id": row["sample_id"],
                "image_path": str(resolved[row["sample_id"]]),
                "human_action": action,
                "target": int(action == "filter"),
                "split": "train",
                "category": "r3_owner_multisignal",
                "source_kind": "r3_owner_review",
                "signal_labels": row["labels"],
                "label_semantics": row["label_semantics"],
                "sample_weight": 8.0 if action == "allow" and row["label_semantics"] == "owner_review_complete" else 1.0,
                "training_authorization": row["training_authorization"],
            }
        )
    return {
        "schema_version": "gloshia-r3-candidate-split-v1",
        "source_split": base.get("schema_version"),
        "signals": signals,
        "summary": {
            "base_records": len(base_records),
            "r3_additions": len(additions),
            "r3_unresolved": len(missing),
            "r3_unresolved_sample_ids": missing,
            "r3_allow": sum(row["target"] == 0 for row in additions),
            "r3_filter": sum(row["target"] == 1 for row in additions),
            "train": sum(row["split"] == "train" for row in base_records) + len(additions),
            "validation": sum(row["split"] == "validation" for row in base_records),
            "frozen_test": sum(row["split"] == "frozen_test" for row in base_records),
            "final_sealed_opened": False,
        },
        "records": [*base_records, *additions],
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-split", required=True, type=Path)
    parser.add_argument("--partial-audit", required=True, type=Path)
    parser.add_argument("--focused-export", required=True, type=Path)
    parser.add_argument("--output-dir", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--search-root", action="append", default=[], type=Path)
    args = parser.parse_args()
    partial = json.loads(args.partial_audit.read_text())
    focused = json.loads(args.focused_export.read_text())
    records = merge_labels(partial, focused)
    resolved = resolve_images(records, args.output_dir, args.search_root)
    result = build_split(json.loads(args.base_split.read_text()), records, resolved)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2, ensure_ascii=False) + "\n")
    print(json.dumps(result["summary"], indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
