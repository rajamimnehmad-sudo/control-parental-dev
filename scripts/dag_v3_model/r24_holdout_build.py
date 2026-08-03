#!/usr/bin/env python3
"""Build the new evaluation-only R2.4 holdout after visual review."""

from __future__ import annotations

import argparse
import json
from collections import Counter
from pathlib import Path
from typing import Any


BINARY = frozenset(("allow", "filter"))
ALLOWED = frozenset((*BINARY, "doubt", "exclude"))


def _read_jsonl(path: Path) -> list[dict[str, Any]]:
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


def build_holdout(manifest: Path, reviews_path: Path, development_split: Path, output: Path) -> dict[str, Any]:
    raw_rows = _read_jsonl(manifest)
    reviews = json.loads(reviews_path.read_text(encoding="utf-8")).get("reviews", {})
    sample_ids = {row["sample_id"] for row in raw_rows}
    if set(reviews) != sample_ids:
        raise ValueError("every holdout sample must have exactly one review")
    if any(review.get("action") not in ALLOWED for review in reviews.values()):
        raise ValueError("holdout contains an invalid review action")

    development = json.loads(development_split.read_text(encoding="utf-8")).get("records", [])
    fields = ("sample_id", "sha256", "phash64", "source_url")
    overlaps: dict[str, list[str]] = {}
    for field in fields:
        existing = {row.get(field) for row in development if row.get(field)}
        common = sorted({row.get(field) for row in raw_rows if row.get(field)} & existing)
        if common:
            overlaps[field] = common
    if overlaps:
        raise ValueError(f"holdout overlaps development data: {overlaps}")

    records: list[dict[str, Any]] = []
    for row in raw_rows:
        review = reviews[row["sample_id"]]
        action = review["action"]
        if action not in BINARY:
            continue
        records.append(
            {
                "sample_id": row["sample_id"],
                "image_path": str((manifest.parent / row["local_path"]).resolve()),
                "source_url": row.get("source_url"),
                "sha256": row["sha256"],
                "phash64": row["phash64"],
                "group_key": review.get("group_key", row.get("source_cluster_hash") or row["sample_id"]),
                "category": row.get("category"),
                "human_action": action,
                "target": int(action == "filter"),
                "split": "r24_holdout",
                "label_source": "codex_visual_prelabel_pending_owner_audit",
                "usage_state": "internal_evaluation_only",
                "training_authorized": False,
            }
        )
    counts = Counter(review["action"] for review in reviews.values())
    if len(records) < 30 or sum(row["target"] for row in records) < 10:
        raise ValueError("holdout is too small or lacks filter coverage")
    payload = {
        "schema_version": "gloshia-r24-region-holdout-v1",
        "ticket": "GLOSHIA-R2.4-REGION-AWARE-TRAINING-GATE-20",
        "status": "evaluation_only_owner_audit_pending",
        "review_counts": dict(sorted(counts.items())),
        "records": sorted(records, key=lambda row: row["sample_id"]),
        "contamination_check": {"passed": True, "checked_fields": list(fields), "overlaps": {}},
        "final_sealed_opened": False,
        "training_authorized": False,
    }
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(payload, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    return payload


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", required=True, type=Path)
    parser.add_argument("--reviews", required=True, type=Path)
    parser.add_argument("--development-split", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    payload = build_holdout(args.manifest, args.reviews, args.development_split, args.output)
    print(json.dumps({"review_counts": payload["review_counts"], "binary": len(payload["records"])}, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
