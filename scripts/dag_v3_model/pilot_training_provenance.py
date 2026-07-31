#!/usr/bin/env python3
"""Audit binary pilot provenance and split isolation without training a model."""

from __future__ import annotations

import argparse
import json
import sys
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Sequence

from pilot_binary_baseline import BaselineInputError, PilotSample, _sha256, load_samples


REPORT_SCHEMA_VERSION = "dag-v3-pilot-training-provenance-v1"
MAX_MANIFEST_BYTES = 8 * 1024 * 1024
MAX_MANIFEST_ROWS = 10_000
ROLES = ("train", "validation", "holdout")


def _read_jsonl(path: Path) -> list[dict[str, Any]]:
    if not path.is_file():
        raise ValueError(f"missing download manifest: {path}")
    if path.stat().st_size > MAX_MANIFEST_BYTES:
        raise ValueError(f"download manifest exceeds {MAX_MANIFEST_BYTES} bytes: {path}")
    rows: list[dict[str, Any]] = []
    with path.open(encoding="utf-8") as handle:
        for line_number, raw_line in enumerate(handle, start=1):
            line = raw_line.strip()
            if not line:
                continue
            if len(rows) >= MAX_MANIFEST_ROWS:
                raise ValueError(
                    f"download manifest exceeds {MAX_MANIFEST_ROWS} rows: {path}"
                )
            try:
                value = json.loads(line)
            except json.JSONDecodeError as error:
                raise ValueError(
                    f"invalid JSON in {path} line {line_number}: {error.msg}"
                ) from error
            if not isinstance(value, dict):
                raise ValueError(f"manifest row must be an object: {path}:{line_number}")
            rows.append(value)
    return rows


def _authorized_for_training(record: dict[str, Any]) -> bool:
    """Require explicit evidence; a permissive copyright label is not sufficient."""
    if record.get("training_authorized") is not True:
        return False
    rights = record.get("rights")
    license_record = record.get("license")
    return (
        isinstance(rights, dict)
        and rights.get("status") == "approved"
        and isinstance(license_record, dict)
        and license_record.get("ml_use_review") == "approved"
        and license_record.get("commercial_use_allowed") is True
        and license_record.get("derivatives_allowed") is True
    )


def _manifest_index(
    paths: Sequence[Path],
) -> tuple[dict[str, list[dict[str, Any]]], dict[str, Any]]:
    by_hash: dict[str, list[dict[str, Any]]] = defaultdict(list)
    status_counts: Counter[str] = Counter()
    license_counts: Counter[str] = Counter()
    source_counts: Counter[str] = Counter()
    usable_rows = 0
    for path in paths:
        for row in _read_jsonl(path):
            if row.get("status") not in (None, "downloaded"):
                continue
            digest = row.get("sha256")
            if not isinstance(digest, str) or len(digest) != 64:
                continue
            usable_rows += 1
            by_hash[digest].append(row)
            status_counts[str(row.get("review_status", "undeclared"))] += 1
            license_counts[str(row.get("license_id", row.get("license", "undeclared")))] += 1
            source_counts[str(row.get("source", row.get("catalog", "undeclared")))] += 1
    return by_hash, {
        "manifests": len(paths),
        "usable_rows": usable_rows,
        "unique_content_sha256": len(by_hash),
        "review_status": dict(sorted(status_counts.items())),
        "declared_license": dict(sorted(license_counts.items())),
        "source": dict(sorted(source_counts.items())),
    }


def _load_role(
    specs: Sequence[Sequence[str]],
) -> list[PilotSample]:
    samples: list[PilotSample] = []
    for review, items, public_dir in specs:
        samples.extend(
            load_samples(
                Path(review),
                Path(items),
                Path(public_dir),
                skip_excluded=True,
                require_both_classes=False,
            )
        )
    return samples


def audit(
    role_specs: dict[str, Sequence[Sequence[str]]],
    manifest_paths: Sequence[Path],
) -> dict[str, Any]:
    provenance, manifest_summary = _manifest_index(manifest_paths)
    role_samples = {role: _load_role(role_specs.get(role, [])) for role in ROLES}

    role_reports: dict[str, Any] = {}
    role_ids: dict[str, set[str]] = {}
    role_hashes: dict[str, set[str]] = {}
    duplicate_ids: dict[str, list[str]] = {}
    duplicate_hashes: dict[str, list[str]] = {}

    for role, samples in role_samples.items():
        ids = [sample.sample_id for sample in samples]
        hashes = [_sha256(sample.image_path) for sample in samples]
        id_counts = Counter(ids)
        hash_counts = Counter(hashes)
        role_ids[role] = set(ids)
        role_hashes[role] = set(hashes)
        duplicate_ids[role] = sorted(key for key, count in id_counts.items() if count > 1)
        duplicate_hashes[role] = sorted(
            key for key, count in hash_counts.items() if count > 1
        )

        matched = sum(digest in provenance for digest in hashes)
        authorized = sum(
            any(_authorized_for_training(row) for row in provenance.get(digest, []))
            for digest in hashes
        )
        actions = Counter(sample.action for sample in samples)
        sources = Counter(sample.source for sample in samples)
        role_reports[role] = {
            "sets": len(role_specs.get(role, [])),
            "samples": len(samples),
            "actions": dict(sorted(actions.items())),
            "sources": dict(sorted(sources.items())),
            "unique_sample_ids": len(role_ids[role]),
            "unique_content_sha256": len(role_hashes[role]),
            "provenance_matched": matched,
            "provenance_unmatched": len(samples) - matched,
            "explicitly_training_authorized": authorized,
        }

    overlaps: dict[str, Any] = {}
    for left_index, left in enumerate(ROLES):
        for right in ROLES[left_index + 1 :]:
            key = f"{left}_vs_{right}"
            overlaps[key] = {
                "sample_ids": sorted(role_ids[left] & role_ids[right]),
                "content_sha256": sorted(role_hashes[left] & role_hashes[right]),
            }

    train = role_reports["train"]
    overlap_free = all(
        not values["sample_ids"] and not values["content_sha256"]
        for values in overlaps.values()
    )
    no_internal_duplicates = all(
        not duplicate_ids[role] and not duplicate_hashes[role] for role in ROLES
    )
    has_isolated_evaluation = bool(
        role_reports["validation"]["samples"] or role_reports["holdout"]["samples"]
    )
    provenance_complete = train["provenance_unmatched"] == 0
    rights_complete = (
        train["samples"] > 0
        and train["explicitly_training_authorized"] == train["samples"]
    )
    ready = all(
        (
            train["samples"] > 0,
            overlap_free,
            no_internal_duplicates,
            has_isolated_evaluation,
            provenance_complete,
            rights_complete,
        )
    )
    blockers = []
    if train["samples"] == 0:
        blockers.append("no_training_samples")
    if not overlap_free:
        blockers.append("cross_split_overlap")
    if not no_internal_duplicates:
        blockers.append("duplicate_samples_or_content")
    if not has_isolated_evaluation:
        blockers.append("no_isolated_evaluation_set")
    if not provenance_complete:
        blockers.append("training_provenance_incomplete")
    if not rights_complete:
        blockers.append("explicit_training_rights_incomplete")

    return {
        "schema_version": REPORT_SCHEMA_VERSION,
        "created_at": datetime.now(timezone.utc).isoformat(),
        "purpose": "read_only_retraining_gate",
        "roles": role_reports,
        "download_manifests": manifest_summary,
        "duplicate_sample_ids": duplicate_ids,
        "duplicate_content_sha256": duplicate_hashes,
        "cross_role_overlaps": overlaps,
        "gate": {
            "ready_for_retraining": ready,
            "blockers": blockers,
            "note": (
                "Open-license metadata alone does not establish model-training or "
                "personality-rights approval. Authorization must be explicit."
            ),
        },
    }


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    for role in ROLES:
        parser.add_argument(
            f"--{role}",
            action="append",
            nargs=3,
            metavar=("REVIEW", "ITEMS", "PUBLIC_DIR"),
            default=[],
        )
    parser.add_argument(
        "--download-manifest",
        action="append",
        type=Path,
        default=[],
    )
    parser.add_argument("--output", type=Path)
    parser.add_argument("--pretty", action="store_true")
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    arguments = _parser().parse_args(argv)
    try:
        report = audit(
            {role: getattr(arguments, role) for role in ROLES},
            arguments.download_manifest,
        )
    except (BaselineInputError, OSError, UnicodeError, ValueError) as error:
        print(f"error: {error}", file=sys.stderr)
        return 1

    encoded = json.dumps(
        report,
        ensure_ascii=False,
        indent=2 if arguments.pretty else None,
        sort_keys=arguments.pretty,
    ) + "\n"
    if arguments.output:
        arguments.output.parent.mkdir(parents=True, exist_ok=True)
        arguments.output.write_text(encoded, encoding="utf-8")
    else:
        print(encoded, end="")
    return 0 if report["gate"]["ready_for_retraining"] else 3


if __name__ == "__main__":
    raise SystemExit(main())
