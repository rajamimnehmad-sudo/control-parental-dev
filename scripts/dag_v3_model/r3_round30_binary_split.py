#!/usr/bin/env python3
"""Build the private R3 binary repair split from reviewed round30 images.

Round30 is training-only.  The historical validation and frozen_test rows are
copied byte-for-byte in their split assignment and are never relabelled.  A
failure in any exact or perceptual contamination check aborts the build.
"""

from __future__ import annotations

import argparse
import json
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any


BINARY_ACTIONS = {"allow": 0, "filter": 1}
CHECK_FIELDS = ("sample_id", "sha256", "phash64", "group_key", "source_url")


def _read_jsonl(path: Path) -> list[dict[str, Any]]:
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


def _phash_distance(left: str | None, right: str | None) -> int | None:
    if not left or not right:
        return None
    try:
        return bin(int(left, 16) ^ int(right, 16)).count("1")
    except (TypeError, ValueError):
        return None


def _round30_records(
    manifest_path: Path,
    reviews_path: Path,
    image_root: Path,
) -> tuple[list[dict[str, Any]], dict[str, int]]:
    manifest = {row["sample_id"]: row for row in _read_jsonl(manifest_path)}
    reviews_payload = json.loads(reviews_path.read_text(encoding="utf-8"))
    reviews = reviews_payload.get("reviews", {})
    if not manifest:
        raise ValueError("round30 manifest is empty")

    rows: list[dict[str, Any]] = []
    excluded = Counter()
    for sample_id, row in sorted(manifest.items()):
        review = reviews.get(sample_id)
        if review is None:
            excluded["unreviewed"] += 1
            continue
        action = review.get("action")
        if action not in BINARY_ACTIONS:
            excluded[action or "invalid_action"] += 1
            continue
        if row.get("sealed") or row.get("usage_state") != "internal_evaluation_ok":
            excluded["excluded_or_sealed"] += 1
            continue
        if not row.get("training_authorized"):
            excluded["not_training_authorized"] += 1
            continue
        image_path = (image_root / row["local_path"]).resolve()
        if not image_path.is_file():
            raise FileNotFoundError(image_path)
        rows.append(
            {
                "sample_id": sample_id,
                "image_path": str(image_path),
                "source_url": row.get("source_url"),
                "catalog": row.get("catalog"),
                "origin": row.get("origin"),
                "sha256": row["sha256"],
                "phash64": row.get("phash64"),
                "dhash64": row.get("dhash64"),
                "dimensions": [row.get("width"), row.get("height")],
                "category": row.get("category"),
                "campaign": row.get("campaign"),
                "product": row.get("product"),
                "session": row.get("session"),
                "series": row.get("series"),
                "group_key": row.get("source_cluster") or row.get("series") or row.get("session"),
                "human_action": action,
                "target": BINARY_ACTIONS[action],
                "split": "train",
                "source_kind": "r3_round30_owner_review",
                "training_authorization": "owner_authorized_private_experiment",
                "training_rights_clear": False,
                "training_rights_status": row.get("training_rights_status", "training_rights_uncertain"),
                "reviewed_at": review.get("reviewed_at"),
                "reviewer_id": review.get("reviewer_id"),
            }
        )
    return rows, dict(excluded)


def _overlaps(records: list[dict[str, Any]]) -> dict[str, dict[str, list[str]]]:
    by_split: dict[str, dict[str, dict[str, list[str]]]] = defaultdict(lambda: defaultdict(lambda: defaultdict(list)))
    for row in records:
        split = row["split"]
        for field in CHECK_FIELDS:
            value = row.get(field)
            if value:
                by_split[split][field][str(value)].append(row["sample_id"])
    result: dict[str, dict[str, list[str]]] = {}
    splits = sorted(by_split)
    for left_index, left in enumerate(splits):
        for right in splits[left_index + 1 :]:
            for field in CHECK_FIELDS:
                shared = set(by_split[left][field]) & set(by_split[right][field])
                if shared:
                    result[f"{left}__{right}__{field}"] = sorted(shared)
    return result


def _near_phash(records: list[dict[str, Any]], max_distance: int = 4) -> list[dict[str, Any]]:
    result = []
    for index, left in enumerate(records):
        for right in records[index + 1 :]:
            distance = _phash_distance(left.get("phash64"), right.get("phash64"))
            if distance is not None and distance <= max_distance:
                result.append(
                    {
                        "left": left["sample_id"],
                        "right": right["sample_id"],
                        "distance": distance,
                        "left_split": left["split"],
                        "right_split": right["split"],
                    }
                )
    return result


def build_split(base_payload: dict[str, Any], additions: list[dict[str, Any]], *, seed: int) -> dict[str, Any]:
    base_records = [dict(row) for row in base_payload["records"]]
    if any(row.get("split") not in {"train", "validation", "frozen_test"} for row in base_records):
        raise ValueError("base split contains an unsupported split")
    base_ids = {row["sample_id"] for row in base_records}
    addition_ids = {row["sample_id"] for row in additions}
    if base_ids & addition_ids:
        raise ValueError("round30 sample IDs overlap the historical split")
    records = base_records + additions
    exact_overlaps = _overlaps(records)
    near_phash = _near_phash(records)
    cross_split_near = [row for row in near_phash if row["left_split"] != row["right_split"]]
    if exact_overlaps:
        raise ValueError(f"contamination detected: {exact_overlaps}")
    if cross_split_near:
        raise ValueError(f"perceptual contamination detected: {cross_split_near[:5]}")

    counts = Counter((row["split"], row["human_action"]) for row in records)
    return {
        "schema_version": "gloshia-r3-round30-binary-candidate-split-v1",
        "ticket": "GLOSHIA-R3-ROUND30-BINARY-CANDIDATE",
        "status": "private_experimental_only",
        "seed": seed,
        "assignment_policy": "historical_validation_and_frozen_test_preserved; reviewed_round30_train_only",
        "authorization_mode": "owner_authorized_private_experiment",
        "training_rights_clear_declared": False,
        "excluded": ["doubt", "unreviewed", "excluded", "duplicates", "final_sealed"],
        "source_splits": [base_payload.get("schema_version")],
        "rows_by_split": {split: sum(row["split"] == split for row in records) for split in ("train", "validation", "frozen_test")},
        "label_counts_by_split": {
            split: {action: counts[(split, action)] for action in ("allow", "filter")}
            for split in ("train", "validation", "frozen_test")
        },
        "round30_additions": len(additions),
        "round30_excluded": {},
        "contamination_check": {
            "passed": True,
            "checked_fields": list(CHECK_FIELDS),
            "exact_overlaps": exact_overlaps,
            "perceptual_distance_threshold": 4,
            "perceptual_near_pairs_same_split": [row for row in near_phash if row["left_split"] == row["right_split"]],
            "perceptual_near_pairs_cross_split": cross_split_near,
        },
        "final_sealed_opened": False,
        "records": sorted(records, key=lambda row: row["sample_id"]),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-split", required=True, type=Path)
    parser.add_argument("--manifest", required=True, type=Path)
    parser.add_argument("--reviews", required=True, type=Path)
    parser.add_argument("--image-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--seed", type=int, default=3005)
    args = parser.parse_args()
    additions, excluded = _round30_records(args.manifest, args.reviews, args.image_root)
    payload = build_split(json.loads(args.base_split.read_text(encoding="utf-8")), additions, seed=args.seed)
    payload["round30_excluded"] = excluded
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(payload, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(json.dumps({"rows": len(payload["records"]), "round30_additions": len(additions), "excluded": excluded, "rows_by_split": payload["rows_by_split"], "label_counts_by_split": payload["label_counts_by_split"], "contamination_passed": True}, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
