#!/usr/bin/env python3
"""Build the private R2.3 train split and a new untouched regional holdout."""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
from pathlib import Path
from typing import Any

SCRIPT_DIR = Path(__file__).resolve().parent
REPOSITORY_ROOT = SCRIPT_DIR.parents[1]
for import_path in (SCRIPT_DIR, REPOSITORY_ROOT):
    if str(import_path) not in sys.path:
        sys.path.insert(0, str(import_path))

from r2_hard_negative_repair import _near_duplicate_compatible, known_content
from r2_hard_negative_split import (
    _absolute_image_path,
    _check_unique,
    _read_json,
    _read_jsonl,
    _target,
)


BINARY = frozenset(("allow", "filter"))
SPLITS = ("train", "validation", "frozen_test", "regional_holdout")
SCHEMA = "gloshia-r23-regional-repair-split-v1"
EXPECTED_BINARY_ROWS = 55


def _rank(seed: int, sample_id: str) -> str:
    return hashlib.sha256(f"{seed}:{sample_id}".encode("utf-8")).hexdigest()


def _new_records(manifest_path: Path, reviews_path: Path) -> list[dict[str, Any]]:
    reviews = _read_json(reviews_path).get("reviews", {})
    records: list[dict[str, Any]] = []
    for raw in _read_jsonl(manifest_path):
        action = (reviews.get(raw.get("sample_id")) or {}).get("action")
        if action not in BINARY:
            continue
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
                "group_key": f"r23:{group}",
                "human_action": action,
                "target": _target(action),
                "split": "train",
                "source_kind": "r23_regional_repair",
                "label_source": "codex_visual_prelabel",
                "training_authorization": "owner_authorized_private_experiment",
            }
        )
    return records


def assign_regional_holdout(records: list[dict[str, Any]], seed: int) -> None:
    buckets: dict[tuple[str, str], list[dict[str, Any]]] = {}
    for row in records:
        buckets.setdefault((row["human_action"], row.get("category") or "unknown"), []).append(row)
    for bucket in buckets.values():
        ordered = sorted(bucket, key=lambda row: _rank(seed, row["sample_id"]))
        holdout_count = 0 if len(ordered) < 2 else min(len(ordered) - 1, max(1, round(len(ordered) * 0.25)))
        for row in ordered[:holdout_count]:
            row["split"] = "regional_holdout"


def verify_no_contamination(records: list[dict[str, Any]]) -> dict[str, Any]:
    by_split = {split: [row for row in records if row["split"] == split] for split in SPLITS}
    fields = ("sample_id", "sha256", "phash64", "group_key", "source_url")
    overlaps: dict[str, list[str]] = {}
    for field in fields:
        for index, left in enumerate(SPLITS):
            for right in SPLITS[index + 1 :]:
                common = sorted({row[field] for row in by_split[left]} & {row[field] for row in by_split[right]})
                if common:
                    overlaps[f"{left}:{right}:{field}"] = common
    if overlaps:
        raise ValueError(f"split contamination detected: {overlaps}")
    return {
        "passed": True,
        "checked_fields": list(fields),
        "rows_by_split": {split: len(rows) for split, rows in by_split.items()},
        "filters_by_split": {split: sum(row["target"] for row in rows) for split, rows in by_split.items()},
        "overlaps": {},
    }


def _verify_external(manifest_path: Path, excluded_manifests: list[Path]) -> dict[str, Any]:
    rows = _read_jsonl(manifest_path)
    known_ids, known_hashes, known_dhashes, known_phashes = known_content(excluded_manifests)
    overlaps = {
        "sample_id": sorted({row["sample_id"] for row in rows} & known_ids),
        "sha256": sorted({row["sha256"] for row in rows} & set(known_hashes)),
        "near_duplicate": sorted(
            row["sample_id"]
            for row in rows
            if _near_duplicate_compatible(
                int(row["dhash64"], 16),
                int(row["phash64"], 16),
                known_dhashes,
                known_phashes,
            )
        ),
    }
    if any(overlaps.values()):
        raise ValueError(f"R2.3 batch overlaps excluded data: {overlaps}")
    return {"passed": True, "excluded_manifests": [str(path) for path in excluded_manifests], "overlaps": {}}


def build_split(
    legacy_path: Path,
    new_manifest: Path,
    prelabels: Path,
    excluded_manifests: list[Path],
    output: Path,
    seed: int,
) -> dict[str, Any]:
    legacy_payload = _read_json(legacy_path)
    legacy = [
        dict(record)
        for record in legacy_payload.get("records", [])
        if record.get("split") in {"train", "validation", "frozen_test"}
        and record.get("human_action") in BINARY
    ]
    if not legacy or any(not Path(record["image_path"]).is_absolute() for record in legacy):
        raise ValueError("R2.3 requires the normalized R2.2 split")
    new = _new_records(new_manifest, prelabels)
    if len(new) != EXPECTED_BINARY_ROWS:
        raise ValueError(f"expected {EXPECTED_BINARY_ROWS} decisive R2.3 rows, found {len(new)}")
    assign_regional_holdout(new, seed)
    if not any(row["split"] == "regional_holdout" for row in new):
        raise ValueError("regional holdout is empty")
    _check_unique([*legacy, *new])
    external = _verify_external(new_manifest, excluded_manifests)
    records = sorted([*legacy, *new], key=lambda row: row["sample_id"])
    contamination = verify_no_contamination(records)
    payload = {
        "schema_version": SCHEMA,
        "ticket": "GLOSHIA-R2.3-REGIONAL-SAFETY-REPAIR-19",
        "status": "private_experimental_only",
        "seed": seed,
        "assignment_policy": "R2.2 historical splits preserved; new binary rows stratified into train and untouched regional_holdout",
        "excluded": ["doubt", "unreviewed", "duplicates", "consumed_final_exam", "consumed_r22_canary"],
        "authorization_mode": "owner_authorized_private_experiment",
        "label_source": "codex_visual_prelabel_pending_owner_audit",
        "new_batch_binary_rows": len(new),
        "new_batch_doubts_excluded": len(_read_jsonl(new_manifest)) - len(new),
        "external_evaluation_exclusion": external,
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
    parser.add_argument("--prelabels", required=True, type=Path)
    parser.add_argument("--excluded-manifest", action="append", type=Path, default=[])
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--seed", type=int, default=20260803)
    args = parser.parse_args()
    payload = build_split(
        args.legacy_split,
        args.new_manifest,
        args.prelabels,
        args.excluded_manifest,
        args.output,
        args.seed,
    )
    print(json.dumps(payload["contamination_check"], indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
