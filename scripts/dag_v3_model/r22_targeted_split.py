#!/usr/bin/env python3
"""Append a Codex-reviewed R2.2 repair batch without touching frozen evaluation splits."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

from r2_hard_negative_repair import _near_duplicate_compatible, known_content
from r2_hard_negative_split import (
    _absolute_image_path,
    _check_unique,
    _read_json,
    _read_jsonl,
    _target,
    verify_no_contamination,
)


BINARY = frozenset(("allow", "filter"))
SCHEMA = "gloshia-r22-targeted-repair-split-v1"


def _new_records(manifest_path: Path, prelabels_path: Path) -> list[dict[str, Any]]:
    labels = _read_json(prelabels_path).get("reviews", {})
    records: list[dict[str, Any]] = []
    for raw in _read_jsonl(manifest_path):
        action = (labels.get(raw.get("sample_id")) or {}).get("action")
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
                "group_key": f"r22:{group}",
                "human_action": action,
                "target": _target(action),
                "split": "train",
                "source_kind": "r22_targeted_repair",
                "label_source": "codex_visual_prelabel",
                "training_authorization": "owner_authorized_private_experiment",
            }
        )
    return records


def _verify_excluded(manifest_path: Path, excluded_manifests: list[Path]) -> dict[str, Any]:
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
        raise ValueError(f"R2.2 batch overlaps excluded evaluation data: {overlaps}")
    return {
        "passed": True,
        "excluded_manifests": [str(path) for path in excluded_manifests],
        "checked": ["sample_id", "sha256", "dhash64<=8", "phash64<=12"],
        "overlaps": {},
    }


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
        raise ValueError("R2.2 requires the normalized R2.1 split as its baseline")
    new = _new_records(new_manifest, prelabels)
    if len(new) < 40:
        raise ValueError(f"expected at least 40 decisive R2.2 rows, found {len(new)}")
    _check_unique([*legacy, *new])
    external = _verify_excluded(new_manifest, excluded_manifests)
    records = sorted([*legacy, *new], key=lambda row: row["sample_id"])
    contamination = verify_no_contamination(records)
    payload = {
        "schema_version": SCHEMA,
        "ticket": "GLOSHIA-VISUAL-R2.2-TARGETED-REPAIR-15",
        "status": "private_experimental_only",
        "seed": seed,
        "assignment_policy": "R2.1 splits preserved; new decisive Codex prelabels train-only",
        "excluded": ["doubt", "unreviewed", "duplicates", "consumed_final_exam"],
        "authorization_mode": "owner_authorized_private_experiment",
        "label_source": "codex_visual_prelabel_pending_owner_audit",
        "new_batch_binary_rows": len(new),
        "new_batch_doubts_excluded": len(_read_jsonl(new_manifest)) - len(new),
        "contamination_check": contamination,
        "external_evaluation_exclusion": external,
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
    print(json.dumps({
        "new_rows": payload["new_batch_binary_rows"],
        "doubts_excluded": payload["new_batch_doubts_excluded"],
        "contamination": payload["contamination_check"],
    }, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
