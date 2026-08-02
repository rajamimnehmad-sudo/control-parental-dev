#!/usr/bin/env python3
"""Build and verify the private, grouped R2 candidate data split.

This module intentionally consumes the balanced-review manifest rather than
the sealed examination.  It records the exact source rows, hashes and group
assignment needed to reproduce the experiment without copying images into
Git.
"""

from __future__ import annotations

import argparse
import hashlib
import json
from collections import defaultdict
from pathlib import Path
from typing import Any


SPLITS = ("train", "validation", "frozen_test")
BINARY_ACTIONS = frozenset(("allow", "filter"))
OUTPUT_SCHEMA = "gloshia-r2-candidate-split-v1"


def _json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def load_rows(manifest_path: Path, reviews_path: Path) -> list[dict[str, Any]]:
    reviews = _json(reviews_path).get("reviews", {})
    rows = [
        json.loads(line)
        for line in manifest_path.read_text(encoding="utf-8").splitlines()
        if line.strip()
    ]
    if not rows or not isinstance(reviews, dict):
        raise ValueError("manifest and review data must be non-empty")

    selected: list[dict[str, Any]] = []
    seen_ids: set[str] = set()
    seen_hashes: set[str] = set()
    seen_phashes: set[str] = set()
    for row in rows:
        sample_id = row.get("sample_id")
        review = reviews.get(sample_id, {})
        action = review.get("action")
        if action not in BINARY_ACTIONS:
            continue
        if row.get("sealed") is True or row.get("usage_state") != "internal_evaluation_ok":
            raise ValueError(f"binary row is not eligible for private experiment: {sample_id}")
        if sample_id in seen_ids:
            raise ValueError(f"duplicate sample_id: {sample_id}")
        sha256 = row.get("sha256")
        phash64 = row.get("phash64")
        if not isinstance(sha256, str) or len(sha256) != 64:
            raise ValueError(f"missing SHA-256: {sample_id}")
        if not isinstance(phash64, str) or not phash64:
            raise ValueError(f"missing perceptual hash: {sample_id}")
        if sha256 in seen_hashes:
            raise ValueError(f"duplicate SHA-256: {sample_id}")
        if phash64 in seen_phashes:
            raise ValueError(f"duplicate perceptual hash: {sample_id}")
        seen_ids.add(sample_id)
        seen_hashes.add(sha256)
        seen_phashes.add(phash64)
        group = row.get("source_cluster") or row.get("group_or_series") or row.get("source_url")
        if not isinstance(group, str) or not group.strip():
            group = f"sample:{sample_id}"
        selected.append(
            {
                "sample_id": sample_id,
                "image_path": row.get("local_path"),
                "manifest_path": str(manifest_path),
                "source_url": row.get("source_url"),
                "catalog": row.get("catalog"),
                "sha256": sha256,
                "phash64": phash64,
                "dhash64": row.get("dhash64"),
                "dimensions": {"width": row.get("width"), "height": row.get("height")},
                "category": row.get("category"),
                "origin": row.get("source_cluster") or row.get("group_or_series"),
                "group_key": group,
                "historical_split": row.get("split"),
                "human_action": action,
                "target": 0 if action == "allow" else 1,
                "usage_state": row.get("usage_state"),
                "training_rights_status": row.get("training_rights_status"),
            }
        )
    if len(selected) != 322:
        raise ValueError(f"expected 322 binary rows, found {len(selected)}")
    return selected


def _assign_grouped(rows: list[dict[str, Any]]) -> dict[str, str]:
    """Use historical review strata, then move every cross-stratum group together."""
    base = {"main_eval": "train", "directed_review": "validation", "difficult": "frozen_test"}
    assignment: dict[str, str] = {}
    groups: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for row in rows:
        historical = row.get("historical_split")
        if historical not in base:
            raise ValueError(f"unexpected historical split: {historical}")
        assignment[row["sample_id"]] = base[historical]
        groups[row["group_key"]].append(row)
    for group_rows in groups.values():
        splits = {assignment[row["sample_id"]] for row in group_rows}
        if len(splits) > 1:
            # Validation is the least consequential development split.  Moving
            # the whole group there avoids leaking a photo series into test.
            for row in group_rows:
                assignment[row["sample_id"]] = "validation"
    return assignment


def verify_no_contamination(records: list[dict[str, Any]]) -> dict[str, Any]:
    by_split: dict[str, list[dict[str, Any]]] = {split: [] for split in SPLITS}
    for record in records:
        split = record.get("split")
        if split not in SPLITS:
            raise ValueError(f"invalid split: {split}")
        by_split[split].append(record)
    fields = ("sample_id", "sha256", "phash64", "group_key", "source_url")
    overlaps: dict[str, list[str]] = {}
    for field in fields:
        for left_index, left in enumerate(SPLITS):
            for right in SPLITS[left_index + 1 :]:
                left_values = {row[field] for row in by_split[left]}
                right_values = {row[field] for row in by_split[right]}
                common = sorted(left_values & right_values)
                if common:
                    overlaps[f"{left}:{right}:{field}"] = common
    if overlaps:
        raise ValueError(f"split contamination detected: {overlaps}")
    return {
        "passed": True,
        "checked_fields": list(fields),
        "rows_by_split": {split: len(by_split[split]) for split in SPLITS},
        "filters_by_split": {
            split: sum(row["target"] for row in by_split[split]) for split in SPLITS
        },
        "overlaps": {},
    }


def build_split(manifest_path: Path, reviews_path: Path, output_path: Path, seed: int) -> dict[str, Any]:
    rows = load_rows(manifest_path, reviews_path)
    assignment = _assign_grouped(rows)
    records = [dict(row, split=assignment[row["sample_id"]]) for row in rows]
    records.sort(key=lambda row: row["sample_id"])
    contamination = verify_no_contamination(records)
    payload = {
        "schema_version": OUTPUT_SCHEMA,
        "ticket": "GLOSHIA-VISUAL-CANDIDATE-TRAIN-08",
        "status": "private_experimental_only",
        "seed": seed,
        "source_manifest": str(manifest_path),
        "source_reviews": str(reviews_path),
        "excluded": ["doubt", "excluded", "unreviewed", "duplicates", "final_sealed"],
        "threshold_for_comparison": 0.4,
        "r1_model_sha256": "2d52bd9e5eb4cd448cb0d64a784b2ee6f761ad20e890c57b898fd7991d29a9ee",
        "contamination_check": contamination,
        "records": records,
    }
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(payload, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    return payload


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", required=True, type=Path)
    parser.add_argument("--reviews", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--seed", type=int, default=20260802)
    args = parser.parse_args()
    payload = build_split(args.manifest, args.reviews, args.output, args.seed)
    print(json.dumps(payload["contamination_check"], indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
