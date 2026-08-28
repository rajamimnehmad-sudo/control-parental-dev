#!/usr/bin/env python3
"""Validate an audit-17 visible-media manifest and compute conservative coverage bounds."""

from __future__ import annotations

import argparse
import json
from collections import Counter
from pathlib import Path
from typing import Any


CATEGORIES = {
    "AUTHORITATIVE_PRE_RENDER",
    "DEFINITE_NON_INTERCEPTABLE",
    "ATTRIBUTION_UNKNOWN",
}


def summarize(items: list[dict[str, Any]]) -> dict[str, Any]:
    included = [item for item in items if item.get("denominator", True)]
    counts = Counter(item["coverage"] for item in included)
    total = len(included)
    area_total = sum(int(item["visibleAreaPx"]) for item in included)
    area = Counter()
    for item in included:
        area[item["coverage"]] += int(item["visibleAreaPx"])
    authoritative = counts["AUTHORITATIVE_PRE_RENDER"]
    unknown = counts["ATTRIBUTION_UNKNOWN"]
    authoritative_area = area["AUTHORITATIVE_PRE_RENDER"]
    unknown_area = area["ATTRIBUTION_UNKNOWN"]
    return {
        "totalVisibleStaticMedia": total,
        "counts": {category: counts[category] for category in sorted(CATEGORIES)},
        "coverageLowerBound": ratio(authoritative, total),
        "coverageUpperBound": ratio(authoritative + unknown, total),
        "visibleAreaTotalPx": area_total,
        "visibleArea": {category: area[category] for category in sorted(CATEGORIES)},
        "visibleAreaLowerBound": ratio(authoritative_area, area_total),
        "visibleAreaUpperBound": ratio(authoritative_area + unknown_area, area_total),
    }


def ratio(numerator: int, denominator: int) -> float:
    return round(numerator / denominator, 6) if denominator else 0.0


def validate(manifest: dict[str, Any]) -> list[dict[str, Any]]:
    states = manifest.get("states")
    if not isinstance(states, list) or not states:
        raise ValueError("states must be a non-empty list")
    ids: set[str] = set()
    for state in states:
        state_id = state.get("id")
        if not isinstance(state_id, str) or not state_id or state_id in ids:
            raise ValueError("every state needs a unique non-empty id")
        ids.add(state_id)
        visible = state.get("visible")
        if not isinstance(visible, list):
            raise ValueError(f"state {state_id} visible must be a list")
        instance_ids: set[str] = set()
        for item in visible:
            instance_id = item.get("id")
            if not isinstance(instance_id, str) or not instance_id or instance_id in instance_ids:
                raise ValueError(f"state {state_id} has invalid/duplicate visible id")
            instance_ids.add(instance_id)
            if item.get("coverage") not in CATEGORIES:
                raise ValueError(f"state {state_id}/{instance_id} has invalid coverage")
            if not isinstance(item.get("reason"), str) or not item["reason"]:
                raise ValueError(f"state {state_id}/{instance_id} requires a reason")
            area = item.get("visibleAreaPx")
            if not isinstance(area, int) or area <= 0:
                raise ValueError(f"state {state_id}/{instance_id} requires positive visibleAreaPx")
            if item["coverage"] == "AUTHORITATIVE_PRE_RENDER":
                required = ("requestUrlHash", "bodyDigest", "correlationId")
                if any(not item.get(field) for field in required):
                    raise ValueError(
                        f"state {state_id}/{instance_id} authoritative claim lacks exact identity"
                    )
    return states


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("manifest", type=Path)
    args = parser.parse_args()
    manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
    states = validate(manifest)
    per_state = {state["id"]: summarize(state["visible"]) for state in states}
    aggregate = summarize([item for state in states for item in state["visible"]])
    result = {"stateCount": len(states), "perState": per_state, "aggregate": aggregate}
    rendered = json.dumps(result, indent=2, sort_keys=True) + "\n"
    print(rendered, end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
