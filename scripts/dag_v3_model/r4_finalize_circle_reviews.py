#!/usr/bin/env python3
"""Finalize clear owner-reviewed circle crops into a private review pool."""

from __future__ import annotations

import argparse
import hashlib
import json
from collections import Counter
from pathlib import Path
from typing import Any


def _sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def apply_numbered_decisions(payload: dict[str, Any], decisions: dict[str, Any]) -> dict[str, Any]:
    result = {**payload, "records": [dict(row) for row in payload.get("records", [])]}
    by_number = {int(row["review_number"]): row for row in result["records"]}
    if len(by_number) != len(result["records"]):
        raise ValueError("review numbers must be present and unique")
    normalized = {int(number): str(action) for number, action in decisions.get("decisions", {}).items()}
    if set(normalized) != set(by_number):
        missing = sorted(set(by_number) - set(normalized))
        extra = sorted(set(normalized) - set(by_number))
        raise ValueError(f"numbered decisions do not match queue; missing={missing}, extra={extra}")
    invalid = {number: action for number, action in normalized.items() if action not in {"allow", "filter", "doubt"}}
    if invalid:
        raise ValueError(f"invalid numbered decisions: {invalid}")
    for number, action in normalized.items():
        row = by_number[number]
        row["review_status"] = "doubt" if action == "doubt" else "complete"
        row["review_action"] = action
        row["training_authorized"] = False
    result["status"] = "owner_review_complete_with_doubts_not_training_data"
    result["decision_source"] = decisions.get("decision_source")
    return result


def finalize_reviews(payload: dict[str, Any], *, authorize_private_training: bool) -> dict[str, Any]:
    records = payload.get("records", [])
    pending = [row["sample_id"] for row in records if row.get("review_status") == "pending"]
    if pending:
        raise ValueError(f"review queue still has {len(pending)} pending rows")
    invalid = [row["sample_id"] for row in records if row.get("review_action") not in {"allow", "filter", "doubt"}]
    if invalid:
        raise ValueError(f"invalid review actions: {invalid}")

    finalized = []
    excluded = Counter()
    for row in records:
        action = str(row["review_action"])
        if action == "doubt":
            excluded["doubt"] += 1
            continue
        path = Path(row["image_path"])
        if not path.is_file():
            raise FileNotFoundError(path)
        finalized.append(
            {
                "sample_id": row["sample_id"],
                "image_path": str(path.resolve()),
                "sha256": _sha256(path),
                "parent_sample_id": row["parent_sample_id"],
                "group_key": row["group_key"],
                "source_cluster": row["group_key"],
                "category": row.get("category"),
                "human_action": action,
                "target": 1 if action == "filter" else 0,
                "split": "reviewed_pool",
                "source_kind": "owner_reviewed_circle_center_cover128_q45",
                "augmentation_variant": "circle_center_cover128_q45",
                "training_authorized": authorize_private_training,
                "training_authorization": "owner_authorized_private_experiment" if authorize_private_training else None,
            }
        )
    counts = Counter(int(row["target"]) for row in finalized)
    return {
        "schema_version": "gloshia-r4-reviewed-circle-pool-v1",
        "status": "private_reviewed_pool_not_split_not_approved_for_apk",
        "source_schema_version": payload.get("schema_version"),
        "policy_schema_version": "gloshia-r4-owner-visual-policy-v1",
        "records": sorted(finalized, key=lambda row: row["sample_id"]),
        "counts": {"allow": counts[0], "filter": counts[1], "doubt_excluded": excluded["doubt"]},
        "training_authorized": authorize_private_training,
        "frozen_test_opened": False,
        "final_sealed_opened": False,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--review-manifest", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--owner-authorized-private-training", action="store_true")
    parser.add_argument("--numbered-decisions", type=Path)
    args = parser.parse_args()
    payload = json.loads(args.review_manifest.read_text(encoding="utf-8"))
    if args.numbered_decisions is not None:
        payload = apply_numbered_decisions(
            payload,
            json.loads(args.numbered_decisions.read_text(encoding="utf-8")),
        )
        args.review_manifest.write_text(json.dumps(payload, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    result = finalize_reviews(payload, authorize_private_training=args.owner_authorized_private_training)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(json.dumps({"counts": result["counts"], "training_authorized": result["training_authorized"]}, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
