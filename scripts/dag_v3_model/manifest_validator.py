#!/usr/bin/env python3
"""Validate DAG V3 dataset manifests without network or third-party packages."""

from __future__ import annotations

import argparse
import json
import re
import sys
from collections import Counter
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Any
from urllib.parse import urlparse


HEX_64 = re.compile(r"^[0-9a-f]{64}$")
HEX_16 = re.compile(r"^[0-9a-f]{16}$")
SAFE_ID = re.compile(r"^[A-Za-z0-9._:-]{1,160}$")
GLOSH_URN = re.compile(r"^urn:glosh:[A-Za-z0-9][A-Za-z0-9._:-]{0,240}$")
ALLOWED_SOURCE_KINDS = {
    "owned",
    "commissioned",
    "commons",
    "openverse",
    "openimages",
    "other_approved",
}
ALLOWED_MIME_TYPES = {"image/jpeg", "image/png", "image/webp"}
ALLOWED_ELIGIBILITY = {"eligible", "excluded"}
ALLOWED_SPLITS = {"unassigned", "train", "validation", "test", "excluded"}
ALLOWED_ML_REVIEWS = {"approved", "needs_review", "rejected"}
ALLOWED_RIGHTS_STATUS = {"approved", "not_applicable", "needs_review", "rejected"}
ASSIGNED_SPLITS = {"train", "validation", "test"}


@dataclass(frozen=True)
class SignalContract:
    version: str
    labels: tuple[str, ...]
    annotation_states: frozenset[str]


@dataclass
class ValidationReport:
    errors: list[str]
    records: int
    eligibility: Counter[str]
    splits: Counter[str]

    @property
    def ok(self) -> bool:
        return not self.errors

    def summary(self) -> dict[str, Any]:
        return {
            "ok": self.ok,
            "records": self.records,
            "errors": len(self.errors),
            "eligibility": dict(sorted(self.eligibility.items())),
            "splits": dict(sorted(self.splits.items())),
        }


def load_signal_contract(path: Path) -> SignalContract:
    payload = json.loads(path.read_text(encoding="utf-8"))
    raw_labels = payload.get("labels")
    states = payload.get("annotationStates")
    version = payload.get("contractVersion")
    if not isinstance(version, str) or not version:
        raise ValueError("signal contract requires contractVersion")
    if not isinstance(raw_labels, list) or not raw_labels:
        raise ValueError("signal contract requires labels")
    if not isinstance(states, list) or not states:
        raise ValueError("signal contract requires annotationStates")

    indices = [label.get("index") for label in raw_labels if isinstance(label, dict)]
    names = [label.get("name") for label in raw_labels if isinstance(label, dict)]
    if indices != list(range(len(raw_labels))):
        raise ValueError("signal contract indices must be consecutive")
    if any(not isinstance(name, str) or not name for name in names):
        raise ValueError("signal contract label names must be non-empty")
    if len(set(names)) != len(names):
        raise ValueError("signal contract label names must be unique")
    if any(not isinstance(state, str) or not state for state in states):
        raise ValueError("annotation states must be non-empty strings")
    return SignalContract(version, tuple(names), frozenset(states))


def load_jsonl(path: Path) -> tuple[list[tuple[int, dict[str, Any]]], list[str]]:
    records: list[tuple[int, dict[str, Any]]] = []
    errors: list[str] = []
    with path.open(encoding="utf-8") as handle:
        for line_number, raw_line in enumerate(handle, start=1):
            line = raw_line.strip()
            if not line:
                continue
            try:
                value = json.loads(line)
            except json.JSONDecodeError as error:
                errors.append(f"line {line_number}: invalid JSON: {error.msg}")
                continue
            if not isinstance(value, dict):
                errors.append(f"line {line_number}: record must be an object")
                continue
            records.append((line_number, value))
    return records, errors


def _error(errors: list[str], line: int, field: str, message: str) -> None:
    errors.append(f"line {line} {field}: {message}")


def _required_string(
    payload: dict[str, Any],
    key: str,
    errors: list[str],
    line: int,
    prefix: str = "",
) -> str | None:
    value = payload.get(key)
    field = f"{prefix}{key}"
    if not isinstance(value, str) or not value.strip():
        _error(errors, line, field, "must be a non-empty string")
        return None
    return value


def _required_object(
    payload: dict[str, Any],
    key: str,
    errors: list[str],
    line: int,
) -> dict[str, Any] | None:
    value = payload.get(key)
    if not isinstance(value, dict):
        _error(errors, line, key, "must be an object")
        return None
    return value


def _valid_time(value: str | None) -> bool:
    if value is None or not value.endswith("Z"):
        return False
    try:
        datetime.fromisoformat(value[:-1] + "+00:00")
    except ValueError:
        return False
    return True


def _valid_reference(value: str | None, allow_internal: bool) -> bool:
    if value is None or len(value) > 2048:
        return False
    if allow_internal and value.startswith("urn:glosh:"):
        return bool(GLOSH_URN.fullmatch(value))
    parsed = urlparse(value)
    return (
        parsed.scheme == "https"
        and bool(parsed.netloc)
        and parsed.username is None
        and parsed.password is None
    )


def _validate_source(
    record: dict[str, Any],
    errors: list[str],
    line: int,
) -> dict[str, Any] | None:
    source = _required_object(record, "source", errors, line)
    if source is None:
        return None
    kind = _required_string(source, "kind", errors, line, "source.")
    if kind is not None and kind not in ALLOWED_SOURCE_KINDS:
        _error(errors, line, "source.kind", "is not an approved source kind")
    for key in ("provider", "creator", "creator_group_id", "source_cluster_id"):
        value = _required_string(source, key, errors, line, "source.")
        if value is not None and key.endswith("_id") and not SAFE_ID.fullmatch(value):
            _error(errors, line, f"source.{key}", "contains unsupported characters")

    allow_internal = kind in {"owned", "commissioned"}
    for key in ("landing_url", "asset_url"):
        value = _required_string(source, key, errors, line, "source.")
        if not _valid_reference(value, allow_internal):
            _error(errors, line, f"source.{key}", "must be HTTPS or an approved Glosh URN")
    retrieved_at = _required_string(source, "retrieved_at", errors, line, "source.")
    if not _valid_time(retrieved_at):
        _error(errors, line, "source.retrieved_at", "must be an ISO-8601 UTC timestamp")
    return source


def _validate_license(
    record: dict[str, Any],
    eligible: bool,
    errors: list[str],
    line: int,
) -> None:
    license_info = _required_object(record, "license", errors, line)
    if license_info is None:
        return
    for key in ("id", "version", "attribution"):
        _required_string(license_info, key, errors, line, "license.")
    for key in ("url", "evidence_url"):
        value = _required_string(license_info, key, errors, line, "license.")
        if not _valid_reference(value, allow_internal=True):
            _error(errors, line, f"license.{key}", "must be HTTPS or an approved Glosh URN")
    verified_at = _required_string(license_info, "verified_at", errors, line, "license.")
    if not _valid_time(verified_at):
        _error(errors, line, "license.verified_at", "must be an ISO-8601 UTC timestamp")

    for key in ("commercial_use_allowed", "derivatives_allowed"):
        value = license_info.get(key)
        if not isinstance(value, bool):
            _error(errors, line, f"license.{key}", "must be a boolean")
        elif eligible and not value:
            _error(errors, line, f"license.{key}", "must be true for an eligible sample")
    ml_review = license_info.get("ml_use_review")
    if ml_review not in ALLOWED_ML_REVIEWS:
        _error(errors, line, "license.ml_use_review", "has an invalid state")
    elif eligible and ml_review != "approved":
        _error(errors, line, "license.ml_use_review", "must be approved for an eligible sample")


def _validate_rights(
    record: dict[str, Any],
    eligible: bool,
    errors: list[str],
    line: int,
) -> None:
    rights = _required_object(record, "rights", errors, line)
    if rights is None:
        return
    status = rights.get("status")
    if status not in ALLOWED_RIGHTS_STATUS:
        _error(errors, line, "rights.status", "has an invalid state")
    elif eligible and status not in {"approved", "not_applicable"}:
        _error(errors, line, "rights.status", "must be approved or not_applicable")
    evidence = _required_string(rights, "evidence_url", errors, line, "rights.")
    if not _valid_reference(evidence, allow_internal=True):
        _error(errors, line, "rights.evidence_url", "must be HTTPS or an approved Glosh URN")


def _validate_labels(
    record: dict[str, Any],
    contract: SignalContract,
    split: str | None,
    errors: list[str],
    line: int,
) -> None:
    labels = _required_object(record, "labels", errors, line)
    if labels is None:
        return
    expected = set(contract.labels)
    actual = set(labels)
    missing = sorted(expected - actual)
    extra = sorted(actual - expected)
    if missing:
        _error(errors, line, "labels", f"missing labels: {', '.join(missing)}")
    if extra:
        _error(errors, line, "labels", f"unknown labels: {', '.join(extra)}")
    for name, state in labels.items():
        if name in expected and state not in contract.annotation_states:
            _error(errors, line, f"labels.{name}", "has an invalid annotation state")

    reviewed_values = [state for state in labels.values() if state != "unreviewed"]
    unreviewed_values = [state for state in labels.values() if state == "unreviewed"]
    review = _required_object(record, "review", errors, line)
    if review is None:
        return
    reviewers = review.get("reviewer_keys")
    if not isinstance(reviewers, list) or any(
        not isinstance(value, str) or not SAFE_ID.fullmatch(value) for value in reviewers
    ):
        _error(errors, line, "review.reviewer_keys", "must be a list of non-empty pseudonymous keys")
        reviewers = []
    adjudicator = review.get("adjudicator_key")
    if adjudicator is not None and (
        not isinstance(adjudicator, str) or not SAFE_ID.fullmatch(adjudicator)
    ):
        _error(errors, line, "review.adjudicator_key", "must be null or a pseudonymous key")
    if reviewed_values:
        _required_string(review, "guide_version", errors, line, "review.")
        if not reviewers:
            _error(errors, line, "review.reviewer_keys", "requires a reviewer for reviewed labels")
    if split in ASSIGNED_SPLITS and unreviewed_values:
        _error(errors, line, "labels", "assigned splits cannot contain unreviewed labels")
    if split in {"validation", "test"} and len(set(reviewers)) < 2:
        _error(errors, line, "review.reviewer_keys", "validation and test require two reviewers")


def _validate_prelabels(
    record: dict[str, Any],
    contract: SignalContract,
    errors: list[str],
    line: int,
) -> None:
    prelabels = record.get("prelabels")
    if not isinstance(prelabels, list):
        _error(errors, line, "prelabels", "must be a list")
        return
    expected = set(contract.labels)
    for index, prelabel in enumerate(prelabels):
        prefix = f"prelabels[{index}]"
        if not isinstance(prelabel, dict):
            _error(errors, line, prefix, "must be an object")
            continue
        _required_string(prelabel, "model_version", errors, line, f"{prefix}.")
        version = _required_string(prelabel, "signal_contract_version", errors, line, f"{prefix}.")
        if version is not None and version != contract.version:
            _error(errors, line, f"{prefix}.signal_contract_version", "does not match the contract")
        scores = prelabel.get("scores")
        if not isinstance(scores, dict) or set(scores) != expected:
            _error(errors, line, f"{prefix}.scores", "must contain exactly the contract labels")
            continue
        for name, score in scores.items():
            if isinstance(score, bool) or not isinstance(score, (int, float)) or not 0.0 <= score <= 1.0:
                _error(errors, line, f"{prefix}.scores.{name}", "must be between 0 and 1")


def validate_manifest(manifest_path: Path, contract: SignalContract) -> ValidationReport:
    records, errors = load_jsonl(manifest_path)
    eligibility_counts: Counter[str] = Counter()
    split_counts: Counter[str] = Counter()
    seen_ids: dict[str, int] = {}
    seen_hashes: dict[str, int] = {}
    perceptual_groups: dict[str, tuple[str, int]] = {}
    split_groups: dict[str, tuple[str, int]] = {}
    source_clusters: dict[str, tuple[str, int]] = {}

    for line, record in records:
        sample_id = _required_string(record, "sample_id", errors, line)
        if sample_id is not None:
            if not SAFE_ID.fullmatch(sample_id):
                _error(errors, line, "sample_id", "contains unsupported characters")
            if sample_id in seen_ids:
                _error(errors, line, "sample_id", f"duplicates line {seen_ids[sample_id]}")
            else:
                seen_ids[sample_id] = line

        content_hash = _required_string(record, "content_sha256", errors, line)
        if content_hash is not None:
            if not HEX_64.fullmatch(content_hash):
                _error(errors, line, "content_sha256", "must be 64 lowercase hexadecimal characters")
            elif content_hash in seen_hashes:
                _error(errors, line, "content_sha256", f"duplicates line {seen_hashes[content_hash]}")
            else:
                seen_hashes[content_hash] = line

        perceptual_hash = _required_string(record, "perceptual_hash", errors, line)
        if perceptual_hash is not None and not HEX_16.fullmatch(perceptual_hash):
            _error(errors, line, "perceptual_hash", "must be 16 lowercase hexadecimal characters")

        for dimension in ("width", "height"):
            value = record.get(dimension)
            if isinstance(value, bool) or not isinstance(value, int) or value <= 0:
                _error(errors, line, dimension, "must be a positive integer")
        mime_type = record.get("mime_type")
        if mime_type not in ALLOWED_MIME_TYPES:
            _error(errors, line, "mime_type", "is not an approved raster MIME type")
        _required_string(record, "dataset_version", errors, line)
        version = _required_string(record, "signal_contract_version", errors, line)
        if version is not None and version != contract.version:
            _error(errors, line, "signal_contract_version", "does not match the selected contract")

        eligibility = record.get("eligibility")
        if eligibility not in ALLOWED_ELIGIBILITY:
            _error(errors, line, "eligibility", "must be eligible or excluded")
            eligible = False
        else:
            eligibility_counts[eligibility] += 1
            eligible = eligibility == "eligible"
        split = record.get("split")
        if split not in ALLOWED_SPLITS:
            _error(errors, line, "split", "has an invalid value")
            split = None
        else:
            split_counts[split] += 1
        if eligible and split == "excluded":
            _error(errors, line, "split", "eligible samples cannot use excluded")
        if eligibility == "excluded":
            if split != "excluded":
                _error(errors, line, "split", "excluded samples must use excluded")
            _required_string(record, "exclusion_reason", errors, line)

        split_group = _required_string(record, "split_group_id", errors, line)
        if split_group is not None and not SAFE_ID.fullmatch(split_group):
            _error(errors, line, "split_group_id", "contains unsupported characters")

        source = _validate_source(record, errors, line)
        _validate_license(record, eligible, errors, line)
        _validate_rights(record, eligible, errors, line)
        _validate_labels(record, contract, split, errors, line)
        _validate_prelabels(record, contract, errors, line)

        if perceptual_hash and split_group:
            previous = perceptual_groups.get(perceptual_hash)
            if previous is not None and previous[0] != split_group:
                _error(
                    errors,
                    line,
                    "perceptual_hash",
                    f"uses a different split_group_id than line {previous[1]}",
                )
            else:
                perceptual_groups[perceptual_hash] = (split_group, line)

        if split in ASSIGNED_SPLITS and split_group:
            previous = split_groups.get(split_group)
            if previous is not None and previous[0] != split:
                _error(errors, line, "split_group_id", f"crosses splits with line {previous[1]}")
            else:
                split_groups[split_group] = (split, line)
        if split in ASSIGNED_SPLITS and source is not None:
            cluster = source.get("source_cluster_id")
            if isinstance(cluster, str) and cluster:
                previous = source_clusters.get(cluster)
                if previous is not None and previous[0] != split:
                    _error(errors, line, "source.source_cluster_id", f"crosses splits with line {previous[1]}")
                else:
                    source_clusters[cluster] = (split, line)

    return ValidationReport(errors, len(records), eligibility_counts, split_counts)


def _default_contract_path() -> Path:
    return Path(__file__).resolve().parents[2] / "docs/dag/v3/glosh-visual-signals-v1.json"


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("manifest", type=Path, help="JSONL manifest to validate")
    parser.add_argument(
        "--signals",
        type=Path,
        default=_default_contract_path(),
        help="versioned visual signal contract",
    )
    args = parser.parse_args(argv)
    try:
        contract = load_signal_contract(args.signals)
        report = validate_manifest(args.manifest, contract)
    except (OSError, ValueError, json.JSONDecodeError) as error:
        print(f"fatal: {error}", file=sys.stderr)
        return 2

    print(json.dumps(report.summary(), sort_keys=True))
    for error in report.errors:
        print(error, file=sys.stderr)
    return 0 if report.ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
