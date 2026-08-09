#!/usr/bin/env python3
"""Freeze stratified, non-overlapping folds for owner-reviewed circle crops."""

from __future__ import annotations

import argparse
import hashlib
import json
import random
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any


def _sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def _group(row: dict[str, Any]) -> str:
    return str(row.get("group_key") or row.get("source_cluster") or "")


def build_group_folds(
    reviewed: dict[str, Any],
    base: dict[str, Any],
    *,
    folds: int,
    seed: int,
    policy_sha256: str,
) -> dict[str, Any]:
    if folds < 2:
        raise ValueError("folds must be at least 2")
    rows = [dict(row) for row in reviewed.get("records", [])]
    if not reviewed.get("training_authorized") or not rows:
        raise ValueError("reviewed pool is not authorized or is empty")
    if any(int(row["target"]) not in {0, 1} for row in rows):
        raise ValueError("reviewed pool must be binary")
    groups = [_group(row) for row in rows]
    if any(not group for group in groups) or len(set(groups)) != len(groups):
        raise ValueError("every reviewed crop must own one unique non-empty group")

    base_rows = {str(row["sample_id"]): row for row in base.get("records", [])}
    for row in rows:
        parent = base_rows.get(str(row["parent_sample_id"]))
        if parent is None or parent.get("split") != "train":
            raise ValueError(f"reviewed parent is not in base train: {row['sample_id']}")
        if _group(parent) != _group(row):
            raise ValueError(f"reviewed crop group differs from parent: {row['sample_id']}")

    by_label: dict[int, list[dict[str, Any]]] = defaultdict(list)
    for row in rows:
        by_label[int(row["target"])].append(row)
    if any(len(label_rows) < folds for label_rows in by_label.values()) or set(by_label) != {0, 1}:
        raise ValueError("each binary label needs at least one group per fold")

    assigned: list[dict[str, Any]] = []
    for label, label_rows in sorted(by_label.items()):
        ordered = sorted(label_rows, key=lambda row: str(row["sample_id"]))
        random.Random(seed + label).shuffle(ordered)
        for index, row in enumerate(ordered):
            row["cv_fold"] = index % folds
            assigned.append(row)

    fold_counts = {}
    for fold in range(folds):
        fold_rows = [row for row in assigned if int(row["cv_fold"]) == fold]
        fold_counts[str(fold)] = {
            "samples": len(fold_rows),
            "allow": sum(int(row["target"]) == 0 for row in fold_rows),
            "filter": sum(int(row["target"]) == 1 for row in fold_rows),
            "groups": sorted(_group(row) for row in fold_rows),
        }

    return {
        "schema_version": "gloshia-r4-reviewed-group-folds-v1",
        "status": "frozen_cross_validation_only_not_approved_for_apk",
        "seed": seed,
        "folds": folds,
        "source_schema_version": reviewed.get("schema_version"),
        "base_schema_version": base.get("schema_version"),
        "policy_schema_version": reviewed.get("policy_schema_version"),
        "policy_sha256": policy_sha256,
        "method": "deterministic stratification by human target with non-overlapping source groups",
        "counts": dict(Counter("filter" if int(row["target"]) else "allow" for row in assigned)),
        "fold_counts": fold_counts,
        "acceptance_gate": {
            "oof_false_permissions_max": 4,
            "oof_false_filters_max": 5,
            "fixed_validation_original_false_permissions_max": 1,
            "fixed_validation_original_false_filters_max": 2,
            "fixed_validation_all_false_permissions_max": 12,
            "fixed_validation_all_false_filters_max": 5,
            "every_fold_must_pass_fixed_validation": True,
            "interpretation": "improve at least two owner-reviewed false permissions without regressing R3.1 fixed validation",
        },
        "group_contamination": {"passed": True, "crossing_groups": []},
        "frozen_test_opened": False,
        "final_sealed_opened": False,
        "records": sorted(assigned, key=lambda row: (int(row["cv_fold"]), str(row["sample_id"]))),
    }


def build_training_fold(
    fold_payload: dict[str, Any],
    base: dict[str, Any],
    *,
    held_out_fold: int,
    reviewed_weight: float,
) -> dict[str, Any]:
    if reviewed_weight <= 0:
        raise ValueError("reviewed weight must be positive")
    reviewed = [dict(row) for row in fold_payload["records"]]
    if held_out_fold < 0 or held_out_fold >= int(fold_payload["folds"]):
        raise ValueError("invalid held-out fold")
    held_groups = {_group(row) for row in reviewed if int(row["cv_fold"]) == held_out_fold}
    records = []
    for source in base.get("records", []):
        if source.get("split") == "train":
            if source.get("parent_sample_id") or _group(source) in held_groups:
                continue
            row = dict(source)
            row["training_weight"] = 1.0
            row["teacher_anchor"] = True
            records.append(row)
        elif source.get("split") == "validation":
            records.append(dict(source))
    for source in reviewed:
        if int(source["cv_fold"]) == held_out_fold:
            continue
        row = dict(source)
        row["review_parent_sample_id"] = row.pop("parent_sample_id")
        row["split"] = "train"
        row["training_weight"] = reviewed_weight
        row["teacher_anchor"] = False
        records.append(row)
    train_groups = {_group(row) for row in records if row["split"] == "train"}
    if train_groups & held_groups:
        raise ValueError("held-out groups leaked into training")
    return {
        "schema_version": "gloshia-r4-reviewed-representation-fold-v1",
        "status": "cross_validation_train_only_not_approved_for_apk",
        "held_out_fold": held_out_fold,
        "reviewed_weight": reviewed_weight,
        "held_out_groups": sorted(held_groups),
        "source_folds_schema_version": fold_payload.get("schema_version"),
        "base_schema_version": base.get("schema_version"),
        "frozen_test_included": False,
        "final_sealed_opened": False,
        "records": sorted(records, key=lambda row: (str(row["split"]), str(row["sample_id"]))),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--reviewed-pool", required=True, type=Path)
    parser.add_argument("--base-split", required=True, type=Path)
    parser.add_argument("--policy", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--folds", type=int, default=5)
    parser.add_argument("--seed", type=int, default=4201)
    parser.add_argument("--training-output-dir", type=Path)
    parser.add_argument("--reviewed-weight", type=float, default=8.0)
    args = parser.parse_args()
    result = build_group_folds(
        json.loads(args.reviewed_pool.read_text(encoding="utf-8")),
        json.loads(args.base_split.read_text(encoding="utf-8")),
        folds=args.folds,
        seed=args.seed,
        policy_sha256=_sha256(args.policy),
    )
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    if args.training_output_dir is not None:
        args.training_output_dir.mkdir(parents=True, exist_ok=False)
        base_payload = json.loads(args.base_split.read_text(encoding="utf-8"))
        for fold in range(int(result["folds"])):
            training = build_training_fold(result, base_payload, held_out_fold=fold, reviewed_weight=args.reviewed_weight)
            (args.training_output_dir / f"fold-{fold}.json").write_text(
                json.dumps(training, indent=2, ensure_ascii=False) + "\n",
                encoding="utf-8",
            )
    print(json.dumps({"counts": result["counts"], "fold_counts": result["fold_counts"]}, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
