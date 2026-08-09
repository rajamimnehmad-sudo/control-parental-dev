#!/usr/bin/env python3
"""Merge owner-reviewed private pools without duplicating samples or groups."""

from __future__ import annotations

import argparse
import json
from collections import Counter
from pathlib import Path
from typing import Any


def merge_reviewed_pools(payloads: list[dict[str, Any]]) -> dict[str, Any]:
    if len(payloads) < 2:
        raise ValueError("at least two reviewed pools are required")
    policy_versions = {payload.get("policy_schema_version") for payload in payloads}
    if len(policy_versions) != 1 or None in policy_versions:
        raise ValueError("reviewed pools must share one policy version")
    if any(not payload.get("training_authorized") for payload in payloads):
        raise ValueError("every reviewed pool must authorize private training")
    records = [dict(row) for payload in payloads for row in payload.get("records", [])]
    sample_ids = [str(row["sample_id"]) for row in records]
    groups = [str(row.get("group_key") or row.get("source_cluster")) for row in records]
    if len(set(sample_ids)) != len(sample_ids):
        raise ValueError("reviewed pools contain duplicate sample ids")
    if len(set(groups)) != len(groups):
        raise ValueError("reviewed pools contain duplicate groups")
    counts = Counter(int(row["target"]) for row in records)
    doubts = sum(int(payload.get("counts", {}).get("doubt_excluded", 0)) for payload in payloads)
    return {
        "schema_version": "gloshia-r4-merged-reviewed-circle-pool-v1",
        "status": "private_merged_reviewed_pool_not_split_not_approved_for_apk",
        "policy_schema_version": policy_versions.pop(),
        "source_pool_count": len(payloads),
        "counts": {"allow": counts[0], "filter": counts[1], "doubt_excluded": doubts},
        "training_authorized": True,
        "group_contamination": {"passed": True, "duplicate_groups": []},
        "frozen_test_opened": False,
        "final_sealed_opened": False,
        "records": sorted(records, key=lambda row: str(row["sample_id"])),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pool", action="append", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    result = merge_reviewed_pools([json.loads(path.read_text(encoding="utf-8")) for path in args.pool])
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(json.dumps({"counts": result["counts"], "source_pool_count": result["source_pool_count"]}, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
