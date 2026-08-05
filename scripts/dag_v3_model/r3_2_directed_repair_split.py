#!/usr/bin/env python3
"""Build the private R3.2 repair split without consuming the external exam.

The 550-image R3.1 review remains evaluation-only. This tool adds only reviewed
commercial hard negatives from the earlier private pilot, collapses each source
cluster to one representative, and checks exact/perceptual contamination against
the R3.1 training split and the external review manifests.
"""

from __future__ import annotations

import argparse
import json
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any


BINARY_ACTIONS = {"allow", "filter"}
CHECK_FIELDS = ("sample_id", "sha256", "phash64", "group_key", "source_url")
AUTHORIZATION = "owner_authorized_private_experiment"


def _read_jsonl(path: Path) -> list[dict[str, Any]]:
    return [
        json.loads(line)
        for line in path.read_text(encoding="utf-8").splitlines()
        if line.strip()
    ]


def _phash_distance(left: str | None, right: str | None) -> int | None:
    if not left or not right:
        return None
    try:
        return bin(int(left, 16) ^ int(right, 16)).count("1")
    except (TypeError, ValueError):
        return None


def _group_key(row: dict[str, Any]) -> str:
    return str(
        row.get("source_cluster")
        or row.get("group_or_series")
        or row.get("series")
        or row.get("session")
        or row.get("sha256")
    )


def _probabilities(path: Path | None) -> dict[str, float]:
    if path is None:
        return {}
    result: dict[str, float] = {}
    for row in _read_jsonl(path):
        value = row.get("maximum_probability", row.get("full_probability"))
        if value is not None:
            result[row["sample_id"]] = float(value)
    return result


def _reviewed_candidates(
    manifest_path: Path,
    reviews_path: Path,
    image_root: Path,
    predictions_path: Path | None,
) -> tuple[list[dict[str, Any]], dict[str, int]]:
    manifest = {row["sample_id"]: row for row in _read_jsonl(manifest_path)}
    reviews = json.loads(reviews_path.read_text(encoding="utf-8")).get("reviews", {})
    probabilities = _probabilities(predictions_path)
    excluded = Counter()
    candidates: list[dict[str, Any]] = []
    for sample_id, row in sorted(manifest.items()):
        review = reviews.get(sample_id) or {}
        action = review.get("action")
        if action not in BINARY_ACTIONS:
            excluded[action or "unreviewed"] += 1
            continue
        if row.get("sealed") or row.get("usage_state") != "internal_evaluation_ok":
            excluded["excluded_or_sealed"] += 1
            continue
        image_path = (image_root / row["local_path"]).resolve()
        if not image_path.is_file():
            raise FileNotFoundError(image_path)
        candidates.append(
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
                "group_key": _group_key(row),
                "human_action": action,
                "target": int(action == "filter"),
                "source_kind": "r3_commercial_hard_negative_pilot",
                "training_authorization": AUTHORIZATION,
                "training_rights_clear": False,
                "training_rights_status": row.get(
                    "training_rights_status", "training_rights_uncertain"
                ),
                "reviewed_at": review.get("reviewed_at"),
                "reviewer_id": review.get("reviewer_id"),
                "selection_probability": probabilities.get(sample_id),
            }
        )
    return candidates, dict(excluded)


def _select_one_per_cluster(candidates: list[dict[str, Any]]) -> list[dict[str, Any]]:
    by_cluster: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for row in candidates:
        by_cluster[row["group_key"]].append(row)

    selected = []
    for cluster, rows in sorted(by_cluster.items()):
        actions = {row["human_action"] for row in rows}
        if len(actions) > 1:
            raise ValueError(f"mixed human actions within source cluster {cluster}")
        action = rows[0]["human_action"]
        if action == "allow":
            key = lambda row: (row["selection_probability"] is not None, row["selection_probability"] or -1.0, row["sample_id"])
            chosen = max(rows, key=key)
        else:
            key = lambda row: (row["selection_probability"] is None, row["selection_probability"] if row["selection_probability"] is not None else 1.0, row["sample_id"])
            chosen = min(rows, key=key)
        selected.append({key: value for key, value in chosen.items() if key != "selection_probability"})
    return selected


def _field_values(rows: list[dict[str, Any]], field: str) -> dict[str, set[str]]:
    result: dict[str, set[str]] = defaultdict(set)
    for row in rows:
        value = row.get(field)
        if value:
            result[str(value)].add(row["sample_id"])
    return result


def _evaluation_conflicts(
    additions: list[dict[str, Any]], evaluation: list[dict[str, Any]]
) -> dict[str, list[str]]:
    conflicts: dict[str, list[str]] = defaultdict(list)
    evaluation_values = {
        field: _field_values(evaluation, field) for field in CHECK_FIELDS
    }
    for addition in additions:
        for field in CHECK_FIELDS:
            value = addition.get(field)
            if value and str(value) in evaluation_values[field]:
                conflicts[addition["group_key"]].append(
                    f"{field}:{value}"
                )
        for eval_row in evaluation:
            distance = _phash_distance(addition.get("phash64"), eval_row.get("phash64"))
            if distance is not None and distance <= 4:
                conflicts[addition["group_key"]].append(
                    f"phash_distance:{distance}:{eval_row['sample_id']}"
                )
    return {key: sorted(set(values)) for key, values in conflicts.items()}


def _contamination(
    base: list[dict[str, Any]],
    additions: list[dict[str, Any]],
    evaluation: list[dict[str, Any]],
) -> dict[str, Any]:
    records = [*base, *additions]
    by_split = {
        split: [row for row in records if row.get("split") == split]
        for split in ("train", "validation", "frozen_test")
    }
    exact: dict[str, list[str]] = {}
    for left_index, left_name in enumerate(by_split):
        for right_name in list(by_split)[left_index + 1 :]:
            for field in CHECK_FIELDS:
                left_values = _field_values(by_split[left_name], field)
                right_values = _field_values(by_split[right_name], field)
                for value in sorted(set(left_values) & set(right_values)):
                    exact[f"{left_name}__{right_name}__{field}__{value}"] = sorted(
                        left_values[value] | right_values[value]
                    )

    eval_exact: dict[str, list[str]] = {}
    for field in CHECK_FIELDS:
        addition_values = _field_values(additions, field)
        eval_values = _field_values(evaluation, field)
        for value in sorted(set(addition_values) & set(eval_values)):
            eval_exact[f"{field}__{value}"] = sorted(
                addition_values[value] | eval_values[value]
            )

    cross_split_near: list[dict[str, Any]] = []
    for left_name, right_name in (
        ("train", "validation"),
        ("train", "frozen_test"),
        ("validation", "frozen_test"),
    ):
            for left in by_split[left_name]:
                for right in by_split[right_name]:
                    distance = _phash_distance(left.get("phash64"), right.get("phash64"))
                    if distance is not None and distance <= 4:
                        cross_split_near.append(
                            {
                                "left": left["sample_id"],
                                "right": right["sample_id"],
                                "distance": distance,
                            }
                        )

    eval_near: list[dict[str, Any]] = []
    for addition in additions:
        for eval_row in evaluation:
            distance = _phash_distance(addition.get("phash64"), eval_row.get("phash64"))
            if distance is not None and distance <= 4:
                eval_near.append(
                    {
                        "left": addition["sample_id"],
                        "right": eval_row["sample_id"],
                        "distance": distance,
                    }
                )

    return {
        "passed": not exact and not eval_exact and not cross_split_near and not eval_near,
        "checked_fields": list(CHECK_FIELDS),
        "perceptual_distance_threshold": 4,
        "exact_split_overlaps": exact,
        "exact_evaluation_overlaps": eval_exact,
        "perceptual_cross_split_near_pairs": cross_split_near,
        "perceptual_evaluation_near_pairs": eval_near,
    }


def build_split(
    base_payload: dict[str, Any],
    selected: list[dict[str, Any]],
    evaluation: list[dict[str, Any]],
    *,
    seed: int,
    evaluation_conflicts: dict[str, list[str]] | None = None,
) -> dict[str, Any]:
    base = [dict(row) for row in base_payload["records"]]
    if any(row.get("split") not in {"train", "validation", "frozen_test"} for row in base):
        raise ValueError("base split contains unsupported split")
    for row in selected:
        row["split"] = "train"
    contamination = _contamination(base, selected, evaluation)
    if not contamination["passed"]:
        raise ValueError(f"contamination detected: {contamination}")
    records = [*base, *selected]
    counts = Counter((row["split"], row["human_action"]) for row in records)
    return {
        "schema_version": "gloshia-r3-2-directed-repair-split-v1",
        "ticket": "GLOSHIA-R3.2-DIRECTED-REPAIR-TRAIN",
        "status": "private_experimental_only",
        "seed": seed,
        "assignment_policy": "r3_round30_train_preserved; one_reviewed_hard_negative_per_source_cluster_added_to_train",
        "authorization_mode": AUTHORIZATION,
        "training_rights_clear_declared": False,
        "excluded": ["doubt", "unreviewed", "excluded", "duplicates", "final_sealed"],
        "source_splits": [base_payload.get("schema_version")],
        "rows_by_split": {
            split: sum(row["split"] == split for row in records)
            for split in ("train", "validation", "frozen_test")
        },
        "label_counts_by_split": {
            split: {action: counts[(split, action)] for action in ("allow", "filter")}
            for split in ("train", "validation", "frozen_test")
        },
        "hard_negative_candidates": len(selected),
        "hard_negative_clusters": len({row["group_key"] for row in selected}),
        "evaluation_conflicts_excluded": evaluation_conflicts or {},
        "evaluation_rows_checked": len(evaluation),
        "contamination_check": contamination,
        "final_sealed_opened": False,
        "records": sorted(records, key=lambda row: (row["split"], row["sample_id"])),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-split", required=True, type=Path)
    parser.add_argument("--hard-manifest", required=True, type=Path)
    parser.add_argument("--hard-reviews", required=True, type=Path)
    parser.add_argument("--hard-image-root", required=True, type=Path)
    parser.add_argument("--hard-predictions", type=Path)
    parser.add_argument("--evaluation-manifest", action="append", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--seed", type=int, default=3201)
    args = parser.parse_args()

    base_payload = json.loads(args.base_split.read_text(encoding="utf-8"))
    candidates, excluded = _reviewed_candidates(
        args.hard_manifest,
        args.hard_reviews,
        args.hard_image_root,
        args.hard_predictions,
    )
    selected = _select_one_per_cluster(candidates)
    evaluation = [row for path in args.evaluation_manifest for row in _read_jsonl(path)]
    evaluation_conflicts = _evaluation_conflicts(selected, evaluation)
    selected = [
        row for row in selected if row["group_key"] not in evaluation_conflicts
    ]
    result = build_split(
        base_payload,
        selected,
        evaluation,
        seed=args.seed,
        evaluation_conflicts=evaluation_conflicts,
    )
    result["hard_negative_excluded"] = excluded
    result["hard_negative_candidate_actions"] = dict(Counter(row["human_action"] for row in candidates))
    result["hard_negative_selected_actions"] = dict(Counter(row["human_action"] for row in selected))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(json.dumps({key: result[key] for key in ("rows_by_split", "label_counts_by_split", "hard_negative_candidates", "hard_negative_excluded", "contamination_check", "final_sealed_opened")}, indent=2, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
