#!/usr/bin/env python3
"""Measure independent DAG V3 annotation agreement without opening image assets."""

from __future__ import annotations

import argparse
import json
import math
import sys
from collections import Counter, defaultdict
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Sequence

from manifest_validator import (
    MANIFEST_SCHEMA_VERSION,
    SignalContract,
    load_jsonl,
    load_signal_contract,
    validate_manifest,
)


REPORT_SCHEMA_VERSION = "dag-v3-annotation-agreement-report-v1"
MAX_INPUT_BYTES = 128 * 1024 * 1024
MAX_RECORDS = 100_000
MAX_GUIDE_VERSIONS = 16
MAX_CASES = 50
BINARY_STATES = frozenset({"positive", "negative"})


class AgreementInputError(ValueError):
    """Raised when a manifest cannot be used for agreement measurement."""

    def __init__(self, errors: Sequence[str]):
        super().__init__("; ".join(errors))
        self.errors = tuple(errors)


@dataclass(frozen=True)
class AnnotationPair:
    sample_id: str
    split: str
    guide_version: str
    first: dict[str, str]
    second: dict[str, str]
    adjudicated_labels: frozenset[str]


@dataclass(frozen=True)
class AgreementDataset:
    records: int
    review_counts: dict[int, int]
    pairs: tuple[AnnotationPair, ...]


def _rounded(value: float | None) -> float | None:
    if value is None:
        return None
    return round(value, 12)


def _ratio(numerator: int, denominator: int) -> float | None:
    if denominator == 0:
        return None
    return _rounded(numerator / denominator)


def _bounded_ids(values: Sequence[str]) -> dict[str, Any]:
    identifiers = sorted(values)
    return {
        "sample_ids": identifiers[:MAX_CASES],
        "truncated": len(identifiers) > MAX_CASES,
    }


def load_agreement_dataset(path: Path, contract: SignalContract) -> AgreementDataset:
    if path.stat().st_size > MAX_INPUT_BYTES:
        raise AgreementInputError([f"{path}: exceeds {MAX_INPUT_BYTES} bytes"])

    validation = validate_manifest(path, contract)
    if not validation.ok:
        raise AgreementInputError(validation.errors)
    records, read_errors = load_jsonl(path)
    if read_errors:
        raise AgreementInputError(read_errors)
    if len(records) > MAX_RECORDS:
        raise AgreementInputError([f"record limit of {MAX_RECORDS} exceeded"])

    review_counts: Counter[int] = Counter()
    pairs: list[AnnotationPair] = []
    guide_versions: set[str] = set()
    for _, record in records:
        review = record["review"]
        annotations = review["annotations"]
        review_counts[len(annotations)] += 1
        if len(annotations) != 2:
            continue
        guide_version = review["guide_version"]
        guide_versions.add(guide_version)
        adjudication = review["adjudication"]
        adjudicated_labels = (
            frozenset(adjudication["labels"]) if adjudication is not None else frozenset()
        )
        pairs.append(
            AnnotationPair(
                sample_id=record["sample_id"],
                split=record["split"],
                guide_version=guide_version,
                first=annotations[0]["labels"],
                second=annotations[1]["labels"],
                adjudicated_labels=adjudicated_labels,
            )
        )

    if len(guide_versions) > MAX_GUIDE_VERSIONS:
        raise AgreementInputError(
            [f"manifest contains more than {MAX_GUIDE_VERSIONS} guide versions"]
        )
    return AgreementDataset(
        records=len(records),
        review_counts={count: review_counts[count] for count in (0, 1, 2)},
        pairs=tuple(pairs),
    )


def _cohen_kappa(
    pairs: Sequence[tuple[str, str]],
    states: Sequence[str],
) -> float | None:
    if not pairs:
        return None
    first_counts = Counter(first for first, _ in pairs)
    second_counts = Counter(second for _, second in pairs)
    observed = sum(first == second for first, second in pairs) / len(pairs)
    expected = sum(
        first_counts[state] / len(pairs) * second_counts[state] / len(pairs)
        for state in states
    )
    if math.isclose(expected, 1.0):
        return None
    return _rounded((observed - expected) / (1.0 - expected))


def _state_pair_key(first: str, second: str) -> str:
    return "|".join(sorted((first, second)))


def _evaluate_pairs(
    pairs: Sequence[AnnotationPair],
    contract: SignalContract,
) -> dict[str, Any]:
    states = tuple(sorted(state for state in contract.annotation_states if state != "unreviewed"))
    per_label: dict[str, Any] = {}
    total_decisions = 0
    total_agreements = 0
    total_adjudicated = 0
    samples_with_disagreement: list[dict[str, Any]] = []

    for label in contract.labels:
        decisions = [(pair.first[label], pair.second[label]) for pair in pairs]
        exact_agreements = sum(first == second for first, second in decisions)
        state_pairs = Counter(_state_pair_key(first, second) for first, second in decisions)
        binary_pairs = [
            (first, second)
            for first, second in decisions
            if first in BINARY_STATES and second in BINARY_STATES
        ]
        binary_agreements = sum(first == second for first, second in binary_pairs)
        positive_negative_disagreements = sum(
            {first, second} == BINARY_STATES for first, second in decisions
        )
        uncertainty_disagreements = sum(
            first != second
            and (
                first in {"unknown", "not_applicable"}
                or second in {"unknown", "not_applicable"}
            )
            for first, second in decisions
        )
        disagreement_ids = [
            pair.sample_id
            for pair in pairs
            if pair.first[label] != pair.second[label]
        ]
        adjudicated = sum(label in pair.adjudicated_labels for pair in pairs)
        total_decisions += len(decisions)
        total_agreements += exact_agreements
        total_adjudicated += adjudicated
        per_label[label] = {
            "support": len(decisions),
            "exact_agreement_count": exact_agreements,
            "exact_agreement_rate": _ratio(exact_agreements, len(decisions)),
            "cohen_kappa": _cohen_kappa(decisions, states),
            "state_pair_counts_unordered": dict(sorted(state_pairs.items())),
            "binary_known_support": len(binary_pairs),
            "binary_known_agreement_rate": _ratio(binary_agreements, len(binary_pairs)),
            "positive_negative_disagreement_count": positive_negative_disagreements,
            "uncertainty_disagreement_count": uncertainty_disagreements,
            "adjudicated_count": adjudicated,
            "disagreement_cases": _bounded_ids(disagreement_ids),
        }

    for pair in pairs:
        disagreements = [
            label for label in contract.labels if pair.first[label] != pair.second[label]
        ]
        if disagreements:
            samples_with_disagreement.append(
                {
                    "sample_id": pair.sample_id,
                    "labels": disagreements,
                }
            )
    samples_with_disagreement.sort(key=lambda item: item["sample_id"])

    return {
        "double_reviewed_samples": len(pairs),
        "decisions_compared": total_decisions,
        "exact_agreement_count": total_agreements,
        "micro_exact_agreement_rate": _ratio(total_agreements, total_decisions),
        "samples_with_any_disagreement": len(samples_with_disagreement),
        "sample_agreement_rate": _ratio(
            len(pairs) - len(samples_with_disagreement), len(pairs)
        ),
        "adjudicated_decisions": total_adjudicated,
        "disagreement_samples": {
            "cases": samples_with_disagreement[:MAX_CASES],
            "truncated": len(samples_with_disagreement) > MAX_CASES,
        },
        "labels": per_label,
    }


def build_agreement_report(
    dataset: AgreementDataset,
    contract: SignalContract,
) -> dict[str, Any]:
    by_split: dict[str, list[AnnotationPair]] = defaultdict(list)
    by_guide: dict[str, list[AnnotationPair]] = defaultdict(list)
    for pair in dataset.pairs:
        by_split[pair.split].append(pair)
        by_guide[pair.guide_version].append(pair)

    return {
        "ok": True,
        "schema_version": REPORT_SCHEMA_VERSION,
        "manifest_schema_version": MANIFEST_SCHEMA_VERSION,
        "signal_contract_version": contract.version,
        "annotation_consistency_version": contract.annotation_consistency_version,
        "records": dataset.records,
        "review_counts": {
            "zero": dataset.review_counts.get(0, 0),
            "one": dataset.review_counts.get(1, 0),
            "two": dataset.review_counts.get(2, 0),
        },
        "metric_definitions": {
            "exact_agreement": "both independent reviewers selected the same annotation state",
            "cohen_kappa": (
                "categorical Cohen kappa over positive, negative, unknown and not_applicable; "
                "null when expected agreement is one"
            ),
            "binary_known": "only decisions where both reviewers selected positive or negative",
            "unordered_pairs": "state pairs are sorted so reviewer order cannot change counts",
        },
        "overall": _evaluate_pairs(dataset.pairs, contract),
        "by_split": {
            name: _evaluate_pairs(pairs, contract)
            for name, pairs in sorted(by_split.items())
        },
        "by_guide_version": {
            name: _evaluate_pairs(pairs, contract)
            for name, pairs in sorted(by_guide.items())
        },
    }


def _default_contract_path() -> Path:
    return (
        Path(__file__).resolve().parents[2]
        / "docs/dag/v3/glosh-visual-signals-v1.json"
    )


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("manifest", type=Path, help="validated dataset manifest JSONL")
    parser.add_argument(
        "--signals",
        type=Path,
        default=_default_contract_path(),
        help="versioned visual signal contract",
    )
    parser.add_argument("--pretty", action="store_true")
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = _parser().parse_args(argv)
    try:
        contract = load_signal_contract(args.signals)
        dataset = load_agreement_dataset(args.manifest, contract)
        report = build_agreement_report(dataset, contract)
    except AgreementInputError as error:
        for message in error.errors:
            print(message, file=sys.stderr)
        return 1
    except (OSError, UnicodeError, json.JSONDecodeError, ValueError) as error:
        print(f"could not measure annotation agreement: {error}", file=sys.stderr)
        return 2

    indent = 2 if args.pretty else None
    print(json.dumps(report, ensure_ascii=True, sort_keys=True, indent=indent))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
