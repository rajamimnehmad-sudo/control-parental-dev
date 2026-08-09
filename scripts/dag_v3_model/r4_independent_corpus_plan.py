#!/usr/bin/env python3
"""Freeze an independent owner-reviewed R4 train/holdout corpus."""

from __future__ import annotations

import argparse
import hashlib
import json
import random
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any


def _group(row: dict[str, Any]) -> str:
    return str(row.get("group_key") or row.get("source_cluster") or row.get("group_or_series") or row["sample_id"])


def _hamming(left: str, right: str) -> int:
    return bin(int(left, 16) ^ int(right, 16)).count("1")


def reviewed_rows(manifest: list[dict[str, Any]], reviews: dict[str, Any], root: Path) -> tuple[list[dict[str, Any]], int]:
    decisions = reviews.get("reviews", {})
    rows = []
    doubts = 0
    for source in manifest:
        sample_id = str(source["sample_id"])
        review = decisions.get(sample_id)
        if review is None:
            raise ValueError(f"missing owner review: {sample_id}")
        action = review.get("action")
        if action == "doubt":
            doubts += 1
            continue
        if action not in {"allow", "filter"}:
            raise ValueError(f"invalid owner action: {sample_id}")
        image_path = root / str(source["local_path"])
        if not image_path.is_file():
            raise ValueError(f"missing image: {sample_id}")
        artifact_sha256 = hashlib.sha256(image_path.read_bytes()).hexdigest()
        row = dict(source)
        row.update(
            {
                "image_path": str(image_path.resolve()),
                "source_declared_sha256": source.get("sha256"),
                "sha256": artifact_sha256,
                "group_key": _group(source),
                "human_action": action,
                "target": int(action == "filter"),
                "training_authorization": "owner_authorized_private_experiment",
                "training_rights_clear": False,
            }
        )
        rows.append(row)
    if len(decisions) != len(manifest):
        raise ValueError("review and manifest sample counts differ")
    return rows, doubts


def build_plan(
    sources: list[tuple[list[dict[str, Any]], dict[str, Any], Path]],
    base: dict[str, Any],
    directed_gate: dict[str, Any],
    *,
    seed: int,
    holdout_fraction: float,
) -> dict[str, Any]:
    if not 0.1 <= holdout_fraction <= 0.4:
        raise ValueError("holdout fraction must be between 0.1 and 0.4")
    rows: list[dict[str, Any]] = []
    doubts = 0
    for manifest, reviews, root in sources:
        source_rows, source_doubts = reviewed_rows(manifest, reviews, root)
        rows.extend(source_rows)
        doubts += source_doubts

    unique: dict[str, dict[str, Any]] = {}
    duplicates_excluded = 0
    for row in rows:
        digest = str(row["sha256"])
        previous = unique.get(digest)
        if previous is None:
            unique[digest] = row
            continue
        if (
            str(previous["sample_id"]) != str(row["sample_id"])
            or int(previous["target"]) != int(row["target"])
            or _group(previous) != _group(row)
        ):
            raise ValueError(f"conflicting duplicate: {row['sample_id']}")
        duplicates_excluded += 1
    rows = list(unique.values())
    ids = [str(row["sample_id"]) for row in rows]
    if len(set(ids)) != len(ids):
        raise ValueError("independent corpus contains duplicate ids with different artifacts")

    protected = [row for row in base.get("records", []) if row.get("split") in {"train", "validation"}]
    protected += list(directed_gate.get("records", []))
    protected_ids = {str(row.get("sample_id")) for row in protected}
    protected_hashes = {str(row.get("sha256")) for row in protected if row.get("sha256")}
    protected_urls = {str(row.get("source_url")) for row in protected if row.get("source_url")}
    for row in protected:
        image_path = row.get("image_path")
        if image_path and Path(str(image_path)).is_file():
            protected_hashes.add(hashlib.sha256(Path(str(image_path)).read_bytes()).hexdigest())
    protected_groups = {_group(row) for row in protected}
    protected_phashes = [str(row["phash64"]) for row in protected if row.get("phash64")]
    contamination_excluded = Counter()
    clean_rows = []
    for row in rows:
        if str(row["sample_id"]) in protected_ids or str(row["sha256"]) in protected_hashes:
            contamination_excluded["exact"] += 1
            continue
        if row.get("source_url") and str(row["source_url"]) in protected_urls:
            contamination_excluded["source_url"] += 1
            continue
        if _group(row) in protected_groups:
            contamination_excluded["group"] += 1
            continue
        phash = row.get("phash64")
        if phash and any(_hamming(str(phash), known) <= 4 for known in protected_phashes):
            contamination_excluded["perceptual"] += 1
            continue
        clean_rows.append(row)
    rows = clean_rows

    groups: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for row in rows:
        groups[_group(row)].append(row)
    mixed_groups = sum(len({int(row["target"]) for row in members}) > 1 for members in groups.values())
    strata: dict[tuple[str, str, int, int], list[str]] = defaultdict(list)
    for group, members in groups.items():
        first = members[0]
        key = (
            str(first.get("catalog") or first.get("origin")),
            str(first.get("category")),
            sum(int(row["target"]) == 0 for row in members),
            sum(int(row["target"]) == 1 for row in members),
        )
        strata[key].append(group)
    held_groups: set[str] = set()
    for index, (key, candidates) in enumerate(sorted(strata.items())):
        ordered = sorted(candidates)
        random.Random(seed + index).shuffle(ordered)
        count = round(len(ordered) * holdout_fraction)
        if len(ordered) >= 3:
            count = max(1, count)
        count = min(count, max(0, len(ordered) - 1))
        held_groups.update(ordered[:count])

    planned = []
    for row in rows:
        item = dict(row)
        item["split"] = "independent_holdout" if _group(row) in held_groups else "train"
        planned.append(item)
    counts = Counter((row["split"], "filter" if int(row["target"]) else "allow") for row in planned)
    return {
        "schema_version": "gloshia-r4-independent-corpus-plan-v1",
        "status": "private_experiment_only_not_approved_for_onnx_or_apk",
        "seed": seed,
        "holdout_fraction": holdout_fraction,
        "method": "owner-reviewed groups stratified by catalog, category and binary target",
        "counts": {
            "train": {"allow": counts[("train", "allow")], "filter": counts[("train", "filter")]},
            "independent_holdout": {
                "allow": counts[("independent_holdout", "allow")],
                "filter": counts[("independent_holdout", "filter")],
            },
            "doubt_excluded": doubts,
            "exact_duplicates_excluded": duplicates_excluded,
            "mixed_label_groups": mixed_groups,
        },
        "acceptance_gate": {
            "directed_false_permissions_max": 15,
            "directed_false_filters_max": 54,
            "fixed_original_false_permissions_max": 1,
            "fixed_original_false_filters_max": 2,
            "fixed_all_false_permissions_max": 12,
            "fixed_all_false_filters_max": 5,
            "independent_holdout_must_improve_both_over_r31": True,
            "every_fold_must_pass_fixed_validation": True,
        },
        "contamination": {
            "passed": True,
            "against": ["base_train", "base_validation", "directed_162_gate"],
            "phash_hamming_minimum": 5,
            "excluded": dict(contamination_excluded),
        },
        "training_rights_clear": False,
        "authorization_mode": "owner_authorized_private_experiment",
        "frozen_test_loaded": False,
        "final_sealed_opened": False,
        "records": sorted(planned, key=lambda row: (str(row["split"]), str(row["sample_id"]))),
    }


def build_training_payload(plan: dict[str, Any], base: dict[str, Any]) -> dict[str, Any]:
    records = [
        dict(row)
        for row in base.get("records", [])
        if row.get("split") in {"train", "validation"}
        and str(row.get("augmentation_variant") or "original") == "original"
        and not row.get("parent_sample_id")
    ]
    for source in plan.get("records", []):
        if source.get("split") != "train":
            continue
        row = dict(source)
        row["source_split"] = "independent_corpus_train"
        records.append(row)
    return {
        "schema_version": "gloshia-r4-independent-training-split-v1",
        "status": "private_experiment_only_not_approved_for_onnx_or_apk",
        "source_plan_schema_version": plan.get("schema_version"),
        "base_schema_version": base.get("schema_version"),
        "frozen_test_included": False,
        "final_sealed_opened": False,
        "records": sorted(records, key=lambda row: (str(row["split"]), str(row["sample_id"]))),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", action="append", nargs=2, metavar=("MANIFEST", "REVIEWS"), required=True)
    parser.add_argument("--base-split", type=Path, required=True)
    parser.add_argument("--directed-gate", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--training-output", type=Path)
    parser.add_argument("--seed", type=int, default=20260809)
    parser.add_argument("--holdout-fraction", type=float, default=0.2)
    args = parser.parse_args()
    sources = []
    for manifest_name, reviews_name in args.source:
        manifest_path = Path(manifest_name)
        manifest = [json.loads(line) for line in manifest_path.read_text(encoding="utf-8").splitlines() if line.strip()]
        reviews = json.loads(Path(reviews_name).read_text(encoding="utf-8"))
        sources.append((manifest, reviews, manifest_path.parent))
    result = build_plan(
        sources,
        json.loads(args.base_split.read_text(encoding="utf-8")),
        json.loads(args.directed_gate.read_text(encoding="utf-8")),
        seed=args.seed,
        holdout_fraction=args.holdout_fraction,
    )
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    if args.training_output is not None:
        training = build_training_payload(result, json.loads(args.base_split.read_text(encoding="utf-8")))
        args.training_output.parent.mkdir(parents=True, exist_ok=True)
        args.training_output.write_text(json.dumps(training, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(json.dumps(result["counts"], indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
