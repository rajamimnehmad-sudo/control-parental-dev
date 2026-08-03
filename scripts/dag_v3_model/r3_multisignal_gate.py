#!/usr/bin/env python3
"""Convert historical DAG policy reviews into safe partial R3 signal labels."""

from __future__ import annotations

import argparse
import json
from collections import Counter
from pathlib import Path
from typing import Any


def load_policy(path: Path) -> dict[str, Any]:
    policy = json.loads(path.read_text(encoding="utf-8"))
    signals = policy.get("signals")
    if not isinstance(signals, list) or not signals or len(signals) != len(set(signals)):
        raise ValueError("policy signals must be a non-empty unique list")
    reason_map = policy.get("reason_map")
    if not isinstance(reason_map, dict) or any(value not in signals for value in reason_map.values()):
        raise ValueError("reason_map must target known signals")
    return policy


def _source_resolution(sample_id: str) -> str:
    if sample_id.startswith("wikimedia:"):
        return "wikimedia_api_resolvable"
    if sample_id.startswith("pilot:"):
        return "content_hash_needs_local_resolution"
    return "unknown_source_needs_resolution"


def _normalize_action(raw_action: Any, actions: dict[str, str]) -> str:
    if not isinstance(raw_action, str) or raw_action not in actions:
        raise ValueError(f"unsupported action: {raw_action!r}")
    return actions[raw_action]


def convert_exports(
    export_paths: list[Path],
    policy: dict[str, Any],
    *,
    min_positive: int = 25,
    min_negative: int = 50,
) -> dict[str, Any]:
    signals: list[str] = policy["signals"]
    actions: dict[str, str] = policy["actions"]
    reason_map: dict[str, str] = policy["reason_map"]
    records_by_id: dict[str, dict[str, Any]] = {}
    excluded = 0
    unmapped_reasons: Counter[str] = Counter()

    for export_path in export_paths:
        payload = json.loads(export_path.read_text(encoding="utf-8"))
        rows = payload.get("rows")
        if not isinstance(rows, list):
            raise ValueError(f"review export has no rows list: {export_path}")
        reviewer_id = payload.get("reviewer_id")
        for row in rows:
            sample_id = row.get("sample_id")
            decision = row.get("human_decision")
            if not isinstance(sample_id, str) or not isinstance(decision, dict):
                raise ValueError(f"invalid review row in {export_path}")
            action = _normalize_action(decision.get("action"), actions)
            if action == "exclude":
                excluded += 1
                continue
            raw_reasons = decision.get("reasons", [])
            if not isinstance(raw_reasons, list) or any(not isinstance(item, str) for item in raw_reasons):
                raise ValueError(f"invalid reasons for {sample_id}")
            reasons = sorted(set(raw_reasons))

            labels = {signal: "negative" if action == "allow" else "unknown" for signal in signals}
            for reason in reasons:
                signal = reason_map.get(reason)
                if signal is None:
                    unmapped_reasons[reason] += 1
                else:
                    labels[signal] = "positive"

            record = {
                "sample_id": sample_id,
                "policy_action": action,
                "reported_reasons": reasons,
                "labels": labels,
                "label_semantics": "owner_review_partial",
                "reviewer_id": reviewer_id,
                "reviewed_at": decision.get("reviewedAt"),
                "source_resolution": _source_resolution(sample_id),
                "training_authorized": True,
                "training_authorization": "owner_authorized_private_experiment",
                "source_rights_clear": False,
                "publication_reuse_authorized": False,
            }
            previous = records_by_id.get(sample_id)
            if previous is not None and (
                previous["policy_action"] != action or previous["reported_reasons"] != reasons
            ):
                raise ValueError(f"conflicting duplicate review: {sample_id}")
            records_by_id[sample_id] = record

    records = sorted(records_by_id.values(), key=lambda item: item["sample_id"])
    coverage: dict[str, dict[str, Any]] = {}
    ready_signals: list[str] = []
    for signal in signals:
        counts = Counter(record["labels"][signal] for record in records)
        ready = counts["positive"] >= min_positive and counts["negative"] >= min_negative
        if ready:
            ready_signals.append(signal)
        coverage[signal] = {
            "positive": counts["positive"],
            "negative": counts["negative"],
            "unknown": counts["unknown"],
            "pilot_training_floor_met": ready,
        }

    action_counts = Counter(record["policy_action"] for record in records)
    resolution_counts = Counter(record["source_resolution"] for record in records)
    return {
        "schema_version": "gloshia-r3-partial-label-audit-v1",
        "policy_version": policy["schema_version"],
        "inputs": [str(path.resolve()) for path in export_paths],
        "summary": {
            "records": len(records),
            "excluded": excluded,
            "actions": dict(sorted(action_counts.items())),
            "source_resolution": dict(sorted(resolution_counts.items())),
            "unmapped_reasons": dict(sorted(unmapped_reasons.items())),
            "readiness_floor": {
                "minimum_positive": min_positive,
                "minimum_negative": min_negative,
            },
            "signals_ready_for_pilot": ready_signals,
            "all_signals_ready": len(ready_signals) == len(signals),
            "training_authorized": True,
            "training_authorization": "owner_authorized_private_experiment",
            "source_rights_clear": False,
            "publication_reuse_authorized": False,
        },
        "coverage": coverage,
        "records": records,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--policy", required=True, type=Path)
    parser.add_argument("--review-export", required=True, action="append", type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--min-positive", type=int, default=25)
    parser.add_argument("--min-negative", type=int, default=50)
    args = parser.parse_args()

    result = convert_exports(
        args.review_export,
        load_policy(args.policy),
        min_positive=args.min_positive,
        min_negative=args.min_negative,
    )
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(json.dumps(result["summary"], indent=2, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
