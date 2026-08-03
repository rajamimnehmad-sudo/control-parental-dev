#!/usr/bin/env python3
"""Build a grouped R2.1 repair split without opening the sealed examination.

The previous R2 train/validation rows remain usable for development.  The
previous R2 frozen test stays outside the new split, except for the explicitly
authorized original false-permission case, which is included in training as a
diagnostic repair example.  A deterministic subset of the newly reviewed
binary batch becomes the new frozen_test; doubt never enters a split.
"""

from __future__ import annotations

import argparse
import hashlib
import json
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any, Iterable


SPLITS = ("train", "validation", "frozen_test")
BINARY = frozenset(("allow", "filter"))
SCHEMA = "gloshia-r2-hard-negative-repair-split-v1"


def _read_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def _read_jsonl(path: Path) -> list[dict[str, Any]]:
    return [
        json.loads(line)
        for line in path.read_text(encoding="utf-8").splitlines()
        if line.strip()
    ]


def _absolute_image_path(manifest_path: Path, image_path: str) -> str:
    return str((manifest_path.parent / image_path).resolve())


def _target(action: str) -> int:
    return 1 if action == "filter" else 0


def _legacy_records(split_path: Path) -> tuple[list[dict[str, Any]], list[str]]:
    payload = _read_json(split_path)
    records: list[dict[str, Any]] = []
    legacy_holdout: list[str] = []
    for raw in payload.get("records", []):
        action = raw.get("human_action")
        if action not in BINARY:
            continue
        sample_id = raw["sample_id"]
        if raw.get("split") == "frozen_test":
            legacy_holdout.append(sample_id)
            continue
        historical_split = raw.get("split")
        split = "train" if historical_split in {"train", "frozen_test"} else "validation"
        records.append(
            {
                "sample_id": sample_id,
                "image_path": _absolute_image_path(Path(raw["manifest_path"]), raw["image_path"]),
                "source_url": raw.get("source_url"),
                "catalog": raw.get("catalog"),
                "sha256": raw["sha256"],
                "phash64": raw["phash64"],
                "dhash64": raw.get("dhash64"),
                "dimensions": raw.get("dimensions"),
                "category": raw.get("category"),
                "origin": raw.get("origin"),
                "group_key": f"legacy:{raw.get('group_key') or sample_id}",
                "human_action": action,
                "target": _target(action),
                "split": split,
                "source_kind": "legacy_r2_development",
            }
        )
    return records, sorted(legacy_holdout)


def _new_records(manifest_path: Path, reviews_path: Path) -> list[dict[str, Any]]:
    reviews = _read_json(reviews_path).get("reviews", {})
    records: list[dict[str, Any]] = []
    for raw in _read_jsonl(manifest_path):
        action = reviews.get(raw.get("sample_id"), {}).get("action")
        if action not in BINARY:
            continue
        if raw.get("training_authorized") is not True or raw.get("training_rights_status") != "training_rights_clear":
            raise ValueError(
                f"training authorization is not clear for new sample: {raw.get('sample_id')}"
            )
        group = raw.get("source_cluster_hash") or raw.get("cluster_id") or raw["sample_id"]
        records.append(
            {
                "sample_id": raw["sample_id"],
                "image_path": _absolute_image_path(manifest_path, raw["local_path"]),
                "source_url": raw.get("source_url"),
                "catalog": raw.get("catalog"),
                "sha256": raw["sha256"],
                "phash64": raw["phash64"],
                "dhash64": raw.get("dhash64"),
                "dimensions": raw.get("dimensions"),
                "category": raw.get("category"),
                "origin": raw.get("origin"),
                "group_key": f"new:{group}",
                "human_action": action,
                "target": _target(action),
                "split": None,
                "source_kind": "new_hard_negative_repair",
            }
        )
    return records


def _check_unique(records: Iterable[dict[str, Any]]) -> None:
    for field in ("sample_id", "sha256", "phash64"):
        values = [row[field] for row in records]
        if len(values) != len(set(values)):
            raise ValueError(f"duplicate {field}")


def _pick_groups(groups: list[list[dict[str, Any]]], target_count: int) -> set[str]:
    selected: set[str] = set()
    count = 0
    for group in groups:
        if count + len(group) <= target_count:
            selected.add(group[0]["group_key"])
            count += len(group)
    return selected


def _assign_new(records: list[dict[str, Any]], seed: int) -> dict[str, str]:
    grouped: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for row in records:
        grouped[row["group_key"]].append(row)
    by_target: dict[int, list[list[dict[str, Any]]]] = {0: [], 1: []}
    for group in grouped.values():
        by_target[1 if sum(row["target"] for row in group) >= len(group) / 2 else 0].append(group)
    for target_groups in by_target.values():
        target_groups.sort(
            key=lambda group: hashlib.sha256(
                f"{seed}:{group[0]['group_key']}".encode("utf-8")
            ).hexdigest()
        )
    test_groups: set[str] = set()
    validation_groups: set[str] = set()
    for target, target_groups in by_target.items():
        total = sum(len(group) for group in target_groups)
        test_groups |= _pick_groups(target_groups, round(total * 0.30))
        remaining = [group for group in target_groups if group[0]["group_key"] not in test_groups]
        validation_groups |= _pick_groups(remaining, round(total * 0.16))
    assignment: dict[str, str] = {}
    for row in records:
        if row["group_key"] in test_groups:
            assignment[row["sample_id"]] = "frozen_test"
        elif row["group_key"] in validation_groups:
            assignment[row["sample_id"]] = "validation"
        else:
            assignment[row["sample_id"]] = "train"
    return assignment


def verify_no_contamination(records: list[dict[str, Any]]) -> dict[str, Any]:
    by_split = {split: [row for row in records if row["split"] == split] for split in SPLITS}
    fields = ("sample_id", "sha256", "phash64", "group_key", "source_url")
    overlaps: dict[str, list[str]] = {}
    for field in fields:
        for index, left in enumerate(SPLITS):
            for right in SPLITS[index + 1 :]:
                common = sorted({r[field] for r in by_split[left]} & {r[field] for r in by_split[right]})
                if common:
                    overlaps[f"{left}:{right}:{field}"] = common
    if overlaps:
        raise ValueError(f"split contamination detected: {overlaps}")
    return {
        "passed": True,
        "checked_fields": list(fields),
        "rows_by_split": {split: len(by_split[split]) for split in SPLITS},
        "filters_by_split": {split: sum(r["target"] for r in by_split[split]) for split in SPLITS},
        "overlaps": {},
    }


def build_split(legacy_path: Path, new_manifest: Path, new_reviews: Path, output: Path, seed: int) -> dict[str, Any]:
    legacy, legacy_holdout = _legacy_records(legacy_path)
    new = _new_records(new_manifest, new_reviews)
    if len(new) != 49:
        raise ValueError(f"expected 49 binary new rows, found {len(new)}")
    _check_unique([*legacy, *new])
    new_assignment = _assign_new(new, seed)
    for row in new:
        row["split"] = new_assignment[row["sample_id"]]
    records = sorted([*legacy, *new], key=lambda row: row["sample_id"])
    contamination = verify_no_contamination(records)
    payload = {
        "schema_version": SCHEMA,
        "ticket": "GLOSHIA-VISUAL-R2-HARD-NEGATIVE-REPAIR-09",
        "status": "private_experimental_only",
        "seed": seed,
        "excluded": ["doubt", "unreviewed", "duplicates", "final_sealed"],
        "legacy_frozen_holdout_outside_new_split": legacy_holdout,
        "new_batch_binary_rows": len(new),
        "contamination_check": contamination,
        "records": records,
    }
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(payload, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    return payload


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--legacy-split", required=True, type=Path)
    parser.add_argument("--new-manifest", required=True, type=Path)
    parser.add_argument("--new-reviews", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--seed", type=int, default=20260803)
    args = parser.parse_args()
    payload = build_split(args.legacy_split, args.new_manifest, args.new_reviews, args.output, args.seed)
    print(json.dumps(payload["contamination_check"], indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
