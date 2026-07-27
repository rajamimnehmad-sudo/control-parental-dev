#!/usr/bin/env python3
"""Evaluate DAG V3 multi-label predictions without network or third-party packages."""

from __future__ import annotations

import argparse
import json
import math
import re
import sys
from collections import Counter, defaultdict
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable, Sequence

from manifest_validator import SignalContract, load_signal_contract


REPORT_SCHEMA_VERSION = "dag-v3-evaluation-report-v1"
PREDICTION_SCHEMA_VERSION = "dag-v3-prediction-v1"
POLICY_SCHEMA_VERSION = "dag-v3-evaluation-policy-v1"
MAX_INPUT_BYTES = 128 * 1024 * 1024
MAX_POLICY_BYTES = 1024 * 1024
MAX_LINE_CHARACTERS = 1024 * 1024
MAX_RECORDS = 100_000
MAX_ERRORS = 200
MAX_VARIANTS = 8
MAX_SLICE_KEYS = 4
MAX_SLICE_VALUES = 32
MAX_SLICE_GROUPS_TOTAL = 48
MAX_CASE_IDS = 50
DEFAULT_ECE_BINS = 10
DEFAULT_CURVE_POINTS = 21
KNOWN_STATES = frozenset({"positive", "negative"})
MASKED_STATES = frozenset({"unknown", "not_applicable", "unreviewed"})
POLICY_ACTIONS = frozenset({"block", "observe"})
SAFE_NAME = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._:-]{0,159}$")
WILSON_Z_95 = 1.959963984540054


class EvaluationInputError(ValueError):
    """Raised when evaluation inputs violate the versioned contract."""

    def __init__(self, errors: Sequence[str]):
        super().__init__("; ".join(errors))
        self.errors = tuple(errors)


@dataclass(frozen=True)
class PolicyLabel:
    uncertain_threshold: float
    positive_threshold: float
    policy_action: str


@dataclass(frozen=True)
class EvaluationPolicy:
    version: str
    signal_contract_version: str
    labels: dict[str, PolicyLabel]

    @property
    def blocking_labels(self) -> tuple[str, ...]:
        return tuple(
            name for name, settings in self.labels.items() if settings.policy_action == "block"
        )


@dataclass(frozen=True)
class PredictionRecord:
    sample_id: str
    labels: tuple[str, ...]
    predictions: dict[str, tuple[float, ...]]
    slices: dict[str, str]


@dataclass(frozen=True)
class PredictionSet:
    records: tuple[PredictionRecord, ...]
    variants: tuple[str, ...]


def _rounded(value: float | None) -> float | None:
    if value is None:
        return None
    return round(value, 12)


def _ratio(numerator: int, denominator: int) -> float | None:
    if denominator == 0:
        return None
    return _rounded(numerator / denominator)


def _is_probability(value: Any) -> bool:
    return (
        isinstance(value, (int, float))
        and not isinstance(value, bool)
        and math.isfinite(float(value))
        and 0.0 <= float(value) <= 1.0
    )


def _read_bounded_json(path: Path, maximum_bytes: int) -> dict[str, Any]:
    size = path.stat().st_size
    if size > maximum_bytes:
        raise EvaluationInputError([f"{path}: exceeds {maximum_bytes} bytes"])
    payload = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(payload, dict):
        raise EvaluationInputError([f"{path}: root must be an object"])
    return payload


def load_evaluation_policy(path: Path, contract: SignalContract) -> EvaluationPolicy:
    payload = _read_bounded_json(path, MAX_POLICY_BYTES)
    errors: list[str] = []

    if payload.get("schema_version") != POLICY_SCHEMA_VERSION:
        errors.append(f"schema_version must be {POLICY_SCHEMA_VERSION}")
    version = payload.get("policy_version")
    if not isinstance(version, str) or not SAFE_NAME.fullmatch(version):
        errors.append("policy_version must be a safe non-empty identifier")
    signal_version = payload.get("signal_contract_version")
    if signal_version != contract.version:
        errors.append(f"signal_contract_version must be {contract.version}")

    raw_labels = payload.get("labels")
    settings: dict[str, PolicyLabel] = {}
    if not isinstance(raw_labels, dict):
        errors.append("labels must be an object")
        raw_labels = {}

    expected = set(contract.labels)
    actual = set(raw_labels)
    missing = sorted(expected - actual)
    extra = sorted(actual - expected)
    if missing:
        errors.append(f"labels missing contract entries: {', '.join(missing)}")
    if extra:
        errors.append(f"labels contain unknown entries: {', '.join(extra)}")

    for name in contract.labels:
        raw = raw_labels.get(name)
        if not isinstance(raw, dict):
            continue
        uncertain = raw.get("uncertain_threshold")
        positive = raw.get("positive_threshold")
        action = raw.get("policy_action")
        if not _is_probability(uncertain):
            errors.append(f"labels.{name}.uncertain_threshold must be between 0 and 1")
            continue
        if not _is_probability(positive):
            errors.append(f"labels.{name}.positive_threshold must be between 0 and 1")
            continue
        uncertain_value = float(uncertain)
        positive_value = float(positive)
        if uncertain_value > positive_value:
            errors.append(
                f"labels.{name}.uncertain_threshold must not exceed positive_threshold"
            )
            continue
        if action not in POLICY_ACTIONS:
            errors.append(f"labels.{name}.policy_action must be block or observe")
            continue
        settings[name] = PolicyLabel(uncertain_value, positive_value, action)

    if settings and not any(item.policy_action == "block" for item in settings.values()):
        errors.append("at least one label must have policy_action block")
    if errors:
        raise EvaluationInputError(errors)
    return EvaluationPolicy(str(version), str(signal_version), settings)


def _record_error(errors: list[str], line_number: int, field: str, message: str) -> None:
    errors.append(f"line {line_number} {field}: {message}")


def _validate_record(
    payload: dict[str, Any],
    line_number: int,
    contract: SignalContract,
    errors: list[str],
) -> PredictionRecord | None:
    initial_error_count = len(errors)

    if payload.get("schema_version") != PREDICTION_SCHEMA_VERSION:
        _record_error(
            errors,
            line_number,
            "schema_version",
            f"must be {PREDICTION_SCHEMA_VERSION}",
        )
    if payload.get("signal_contract_version") != contract.version:
        _record_error(
            errors,
            line_number,
            "signal_contract_version",
            f"must be {contract.version}",
        )

    sample_id = payload.get("sample_id")
    if not isinstance(sample_id, str) or not SAFE_NAME.fullmatch(sample_id):
        _record_error(errors, line_number, "sample_id", "must be a safe non-empty identifier")

    raw_labels = payload.get("labels")
    labels: tuple[str, ...] = ()
    if not isinstance(raw_labels, dict):
        _record_error(errors, line_number, "labels", "must be an object")
    else:
        expected = set(contract.labels)
        actual = set(raw_labels)
        missing = sorted(expected - actual)
        extra = sorted(actual - expected)
        if missing:
            _record_error(errors, line_number, "labels", f"missing: {', '.join(missing)}")
        if extra:
            _record_error(errors, line_number, "labels", f"unknown: {', '.join(extra)}")
        for name in contract.labels:
            state = raw_labels.get(name)
            if state not in contract.annotation_states:
                _record_error(errors, line_number, f"labels.{name}", "invalid annotation state")
        if not missing and not extra and all(
            raw_labels.get(name) in contract.annotation_states for name in contract.labels
        ):
            labels = tuple(raw_labels[name] for name in contract.labels)

    raw_predictions = payload.get("predictions")
    predictions: dict[str, tuple[float, ...]] = {}
    if not isinstance(raw_predictions, dict) or not raw_predictions:
        _record_error(errors, line_number, "predictions", "must be a non-empty object")
    elif len(raw_predictions) > MAX_VARIANTS:
        _record_error(
            errors,
            line_number,
            "predictions",
            f"must contain at most {MAX_VARIANTS} variants",
        )
    else:
        for variant, raw_scores in raw_predictions.items():
            if not isinstance(variant, str) or not SAFE_NAME.fullmatch(variant):
                _record_error(
                    errors,
                    line_number,
                    "predictions",
                    "variant names must be safe non-empty identifiers",
                )
                continue
            if not isinstance(raw_scores, list) or len(raw_scores) != len(contract.labels):
                _record_error(
                    errors,
                    line_number,
                    f"predictions.{variant}",
                    f"must contain {len(contract.labels)} ordered probabilities",
                )
                continue
            invalid_indices = [
                str(index) for index, value in enumerate(raw_scores) if not _is_probability(value)
            ]
            if invalid_indices:
                _record_error(
                    errors,
                    line_number,
                    f"predictions.{variant}",
                    f"invalid probabilities at indices: {', '.join(invalid_indices)}",
                )
                continue
            predictions[variant] = tuple(float(value) for value in raw_scores)

    raw_slices = payload.get("slices", {})
    slices: dict[str, str] = {}
    if not isinstance(raw_slices, dict):
        _record_error(errors, line_number, "slices", "must be an object when present")
    elif len(raw_slices) > 16:
        _record_error(errors, line_number, "slices", "must contain at most 16 fields")
    else:
        for key, value in raw_slices.items():
            if not isinstance(key, str) or not SAFE_NAME.fullmatch(key):
                _record_error(errors, line_number, "slices", "contains an invalid key")
                continue
            if not isinstance(value, str) or not SAFE_NAME.fullmatch(value):
                _record_error(
                    errors,
                    line_number,
                    f"slices.{key}",
                    "must be a safe non-empty identifier",
                )
                continue
            slices[key] = value

    if len(errors) != initial_error_count:
        return None
    return PredictionRecord(str(sample_id), labels, predictions, slices)


def load_prediction_set(path: Path, contract: SignalContract) -> PredictionSet:
    if path.stat().st_size > MAX_INPUT_BYTES:
        raise EvaluationInputError([f"{path}: exceeds {MAX_INPUT_BYTES} bytes"])

    errors: list[str] = []
    records: list[PredictionRecord] = []
    sample_ids: set[str] = set()
    expected_variants: tuple[str, ...] | None = None

    with path.open(encoding="utf-8") as handle:
        for line_number, raw_line in enumerate(handle, start=1):
            if len(errors) >= MAX_ERRORS:
                errors.append(f"validation stopped after at least {MAX_ERRORS} errors")
                break
            if len(raw_line) > MAX_LINE_CHARACTERS:
                _record_error(
                    errors,
                    line_number,
                    "record",
                    f"exceeds {MAX_LINE_CHARACTERS} characters",
                )
                continue
            line = raw_line.strip()
            if not line:
                continue
            if len(records) >= MAX_RECORDS:
                errors.append(f"record limit of {MAX_RECORDS} exceeded")
                break
            try:
                payload = json.loads(line)
            except json.JSONDecodeError as error:
                _record_error(errors, line_number, "record", f"invalid JSON: {error.msg}")
                continue
            if not isinstance(payload, dict):
                _record_error(errors, line_number, "record", "must be an object")
                continue
            record = _validate_record(payload, line_number, contract, errors)
            if record is None:
                continue
            variants = tuple(sorted(record.predictions))
            if expected_variants is None:
                expected_variants = variants
            elif variants != expected_variants:
                _record_error(
                    errors,
                    line_number,
                    "predictions",
                    "variant set differs from previous records",
                )
                continue
            if record.sample_id in sample_ids:
                _record_error(errors, line_number, "sample_id", "duplicates an earlier record")
                continue
            sample_ids.add(record.sample_id)
            records.append(record)

    if not records and not errors:
        errors.append("prediction file contains no records")
    if errors:
        raise EvaluationInputError(errors)
    return PredictionSet(tuple(records), expected_variants or ())


def _wilson_interval(events: int, trials: int) -> dict[str, float] | None:
    if trials == 0:
        return None
    proportion = events / trials
    z_squared = WILSON_Z_95 * WILSON_Z_95
    denominator = 1.0 + z_squared / trials
    center = (proportion + z_squared / (2.0 * trials)) / denominator
    half_width = (
        WILSON_Z_95
        * math.sqrt(
            proportion * (1.0 - proportion) / trials
            + z_squared / (4.0 * trials * trials)
        )
        / denominator
    )
    return {
        "confidence": 0.95,
        "estimate": _rounded(proportion),
        "lower": _rounded(max(0.0, center - half_width)),
        "upper": _rounded(min(1.0, center + half_width)),
    }


def _average_precision(pairs: Sequence[tuple[float, int]]) -> float | None:
    positive_count = sum(target for _, target in pairs)
    if positive_count == 0:
        return None
    ordered = sorted(pairs, key=lambda item: item[0], reverse=True)
    true_positives = 0
    false_positives = 0
    previous_recall = 0.0
    area = 0.0
    index = 0
    while index < len(ordered):
        score = ordered[index][0]
        while index < len(ordered) and ordered[index][0] == score:
            if ordered[index][1] == 1:
                true_positives += 1
            else:
                false_positives += 1
            index += 1
        recall = true_positives / positive_count
        precision = true_positives / (true_positives + false_positives)
        area += (recall - previous_recall) * precision
        previous_recall = recall
    return _rounded(area)


def _calibration(
    pairs: Sequence[tuple[float, int]],
    bin_count: int,
) -> tuple[float | None, list[dict[str, Any]]]:
    if not pairs:
        return None, []
    counts = [0] * bin_count
    probability_sums = [0.0] * bin_count
    target_sums = [0] * bin_count
    for probability, target in pairs:
        index = min(int(probability * bin_count), bin_count - 1)
        counts[index] += 1
        probability_sums[index] += probability
        target_sums[index] += target

    calibration_bins: list[dict[str, Any]] = []
    weighted_error = 0.0
    for index, count in enumerate(counts):
        if count == 0:
            continue
        mean_probability = probability_sums[index] / count
        positive_rate = target_sums[index] / count
        weighted_error += count / len(pairs) * abs(mean_probability - positive_rate)
        calibration_bins.append(
            {
                "lower": _rounded(index / bin_count),
                "upper": _rounded((index + 1) / bin_count),
                "count": count,
                "mean_probability": _rounded(mean_probability),
                "positive_rate": _rounded(positive_rate),
            }
        )
    return _rounded(weighted_error), calibration_bins


def _precision_recall_curve(
    pairs: Sequence[tuple[float, int]],
    point_count: int,
) -> list[dict[str, Any]]:
    positive_count = sum(target for _, target in pairs)
    ordered = sorted(pairs, key=lambda item: item[0], reverse=True)
    true_positives = 0
    false_positives = 0
    index = 0
    curve: list[dict[str, Any]] = []
    thresholds = [1.0 - point / (point_count - 1) for point in range(point_count)]
    for threshold in thresholds:
        while index < len(ordered) and ordered[index][0] >= threshold:
            if ordered[index][1] == 1:
                true_positives += 1
            else:
                false_positives += 1
            index += 1
        predicted_positive = true_positives + false_positives
        curve.append(
            {
                "threshold": _rounded(threshold),
                "precision": _ratio(true_positives, predicted_positive),
                "recall": _ratio(true_positives, positive_count),
                "predicted_positive": predicted_positive,
            }
        )
    return curve


def _evaluate_label(
    records: Sequence[PredictionRecord],
    variant: str,
    label_index: int,
    settings: PolicyLabel,
    ece_bins: int,
    curve_points: int,
) -> dict[str, Any]:
    state_counts: Counter[str] = Counter()
    pairs: list[tuple[float, int]] = []
    true_positive = false_positive = true_negative = false_negative = 0
    positive_below_uncertain = positive_uncertain_band = positive_at_or_above = 0

    for record in records:
        state = record.labels[label_index]
        state_counts[state] += 1
        if state not in KNOWN_STATES:
            continue
        target = 1 if state == "positive" else 0
        score = record.predictions[variant][label_index]
        pairs.append((score, target))
        predicted_positive = score >= settings.positive_threshold
        if target and predicted_positive:
            true_positive += 1
        elif target:
            false_negative += 1
        elif predicted_positive:
            false_positive += 1
        else:
            true_negative += 1

        if target:
            if score >= settings.positive_threshold:
                positive_at_or_above += 1
            elif score >= settings.uncertain_threshold:
                positive_uncertain_band += 1
            else:
                positive_below_uncertain += 1

    positive_support = true_positive + false_negative
    negative_support = true_negative + false_positive
    predicted_positive_count = true_positive + false_positive
    brier = (
        sum((probability - target) ** 2 for probability, target in pairs) / len(pairs)
        if pairs
        else None
    )
    ece, calibration_bins = _calibration(pairs, ece_bins)
    return {
        "policy_action": settings.policy_action,
        "uncertain_threshold": settings.uncertain_threshold,
        "positive_threshold": settings.positive_threshold,
        "support": len(pairs),
        "positive_support": positive_support,
        "negative_support": negative_support,
        "masked": {
            state: state_counts.get(state, 0) for state in sorted(MASKED_STATES)
        },
        "confusion_at_positive_threshold": {
            "true_positive": true_positive,
            "false_positive": false_positive,
            "true_negative": true_negative,
            "false_negative": false_negative,
        },
        "precision": _ratio(true_positive, predicted_positive_count),
        "recall": _ratio(true_positive, positive_support),
        "false_negative_rate": _ratio(false_negative, positive_support),
        "specificity": _ratio(true_negative, negative_support),
        "positive_truth_threshold_bands": {
            "below_uncertain": positive_below_uncertain,
            "uncertain_to_positive": positive_uncertain_band,
            "at_or_above_positive": positive_at_or_above,
            "below_uncertain_rate": _ratio(positive_below_uncertain, positive_support),
            "below_uncertain_rate_wilson_95": _wilson_interval(
                positive_below_uncertain, positive_support
            ),
        },
        "pr_auc": _average_precision(pairs),
        "pr_auc_method": "average_precision_step",
        "brier_score": _rounded(brier),
        "expected_calibration_error": ece,
        "calibration_bins": calibration_bins,
        "precision_recall_curve": _precision_recall_curve(pairs, curve_points),
    }


def _policy_action(
    record: PredictionRecord,
    variant: str,
    policy: EvaluationPolicy,
    label_indices: dict[str, int],
) -> str:
    uncertain = False
    for name in policy.blocking_labels:
        score = record.predictions[variant][label_indices[name]]
        settings = policy.labels[name]
        if score >= settings.positive_threshold:
            return "block"
        if score >= settings.uncertain_threshold:
            uncertain = True
    return "uncertain" if uncertain else "allow"


def _truth_policy_action(
    record: PredictionRecord,
    policy: EvaluationPolicy,
    label_indices: dict[str, int],
) -> str:
    states = [record.labels[label_indices[name]] for name in policy.blocking_labels]
    if "positive" in states:
        return "block"
    if any(state in {"unknown", "unreviewed"} for state in states):
        return "unresolved"
    return "allow"


def _case_ids(values: Iterable[str]) -> dict[str, Any]:
    identifiers = list(values)
    return {
        "sample_ids": identifiers[:MAX_CASE_IDS],
        "truncated": len(identifiers) > MAX_CASE_IDS,
    }


def _evaluate_policy(
    records: Sequence[PredictionRecord],
    variant: str,
    policy: EvaluationPolicy,
    label_indices: dict[str, int],
) -> dict[str, Any]:
    predicted_counts: Counter[str] = Counter()
    truth_counts: Counter[str] = Counter()
    matrix = {
        "block": Counter(),
        "allow": Counter(),
    }
    false_allow_ids: list[str] = []

    for record in records:
        predicted = _policy_action(record, variant, policy, label_indices)
        truth = _truth_policy_action(record, policy, label_indices)
        predicted_counts[predicted] += 1
        truth_counts[truth] += 1
        if truth in matrix:
            matrix[truth][predicted] += 1
        if truth == "block" and predicted == "allow":
            false_allow_ids.append(record.sample_id)

    truth_block = truth_counts["block"]
    truth_allow = truth_counts["allow"]
    safe_hidden = matrix["allow"]["uncertain"] + matrix["allow"]["block"]
    return {
        "blocking_labels": list(policy.blocking_labels),
        "truth_counts": {
            "block": truth_block,
            "allow": truth_allow,
            "unresolved": truth_counts["unresolved"],
        },
        "predicted_counts": {
            "block": predicted_counts["block"],
            "uncertain": predicted_counts["uncertain"],
            "allow": predicted_counts["allow"],
        },
        "evaluable_matrix": {
            truth: {
                action: matrix[truth][action] for action in ("block", "uncertain", "allow")
            }
            for truth in ("block", "allow")
        },
        "false_allow_count": len(false_allow_ids),
        "false_allow_rate": _ratio(len(false_allow_ids), truth_block),
        "false_allow_rate_wilson_95": _wilson_interval(len(false_allow_ids), truth_block),
        "false_allow_cases": _case_ids(false_allow_ids),
        "safe_hidden_count": safe_hidden,
        "safe_hidden_rate": _ratio(safe_hidden, truth_allow),
        "uncertain_rate_all": _ratio(predicted_counts["uncertain"], len(records)),
        "uncertain_rate_truth_block": _ratio(matrix["block"]["uncertain"], truth_block),
        "uncertain_rate_truth_allow": _ratio(matrix["allow"]["uncertain"], truth_allow),
    }


def _evaluate_variant(
    records: Sequence[PredictionRecord],
    variant: str,
    contract: SignalContract,
    policy: EvaluationPolicy,
    ece_bins: int,
    curve_points: int,
) -> dict[str, Any]:
    label_indices = {name: index for index, name in enumerate(contract.labels)}
    return {
        "records": len(records),
        "labels": {
            name: _evaluate_label(
                records,
                variant,
                label_indices[name],
                policy.labels[name],
                ece_bins,
                curve_points,
            )
            for name in contract.labels
        },
        "policy": _evaluate_policy(records, variant, policy, label_indices),
    }


def _crosses_threshold(first: float, second: float, threshold: float) -> bool:
    return (first >= threshold) != (second >= threshold)


def _compare_variants(
    records: Sequence[PredictionRecord],
    reference: str,
    candidate: str,
    contract: SignalContract,
    policy: EvaluationPolicy,
) -> dict[str, Any]:
    label_indices = {name: index for index, name in enumerate(contract.labels)}
    label_deltas: dict[str, Any] = {}
    for name in contract.labels:
        index = label_indices[name]
        settings = policy.labels[name]
        deltas: list[float] = []
        uncertain_crossings = 0
        positive_crossings = 0
        for record in records:
            first = record.predictions[reference][index]
            second = record.predictions[candidate][index]
            deltas.append(abs(first - second))
            uncertain_crossings += int(
                _crosses_threshold(first, second, settings.uncertain_threshold)
            )
            positive_crossings += int(
                _crosses_threshold(first, second, settings.positive_threshold)
            )
        label_deltas[name] = {
            "mean_absolute_probability_delta": _rounded(sum(deltas) / len(deltas)),
            "max_absolute_probability_delta": _rounded(max(deltas)),
            "uncertain_threshold_crossings": uncertain_crossings,
            "positive_threshold_crossings": positive_crossings,
        }

    transition_matrix = {
        action: Counter() for action in ("block", "uncertain", "allow")
    }
    disagreement_ids: list[str] = []
    more_permissive_ids: list[str] = []
    more_restrictive_ids: list[str] = []
    new_false_allow_ids: list[str] = []
    resolved_false_allow_ids: list[str] = []
    truth_block_count = 0
    action_rank = {"allow": 0, "uncertain": 1, "block": 2}

    for record in records:
        first = _policy_action(record, reference, policy, label_indices)
        second = _policy_action(record, candidate, policy, label_indices)
        truth = _truth_policy_action(record, policy, label_indices)
        transition_matrix[first][second] += 1
        if first != second:
            disagreement_ids.append(record.sample_id)
        if action_rank[second] < action_rank[first]:
            more_permissive_ids.append(record.sample_id)
        elif action_rank[second] > action_rank[first]:
            more_restrictive_ids.append(record.sample_id)
        if truth == "block":
            truth_block_count += 1
            if first != "allow" and second == "allow":
                new_false_allow_ids.append(record.sample_id)
            elif first == "allow" and second != "allow":
                resolved_false_allow_ids.append(record.sample_id)

    return {
        "reference_variant": reference,
        "candidate_variant": candidate,
        "records": len(records),
        "label_probability_delta": label_deltas,
        "policy_transition_matrix": {
            first: {
                second: transition_matrix[first][second]
                for second in ("block", "uncertain", "allow")
            }
            for first in ("block", "uncertain", "allow")
        },
        "policy_disagreement_count": len(disagreement_ids),
        "policy_disagreement_rate": _ratio(len(disagreement_ids), len(records)),
        "policy_disagreement_cases": _case_ids(disagreement_ids),
        "candidate_more_permissive_count": len(more_permissive_ids),
        "candidate_more_permissive_cases": _case_ids(more_permissive_ids),
        "candidate_more_restrictive_count": len(more_restrictive_ids),
        "candidate_more_restrictive_cases": _case_ids(more_restrictive_ids),
        "candidate_new_false_allow_count": len(new_false_allow_ids),
        "candidate_new_false_allow_rate_over_truth_block": _ratio(
            len(new_false_allow_ids), truth_block_count
        ),
        "candidate_new_false_allow_rate_wilson_95": _wilson_interval(
            len(new_false_allow_ids), truth_block_count
        ),
        "candidate_new_false_allow_cases": _case_ids(new_false_allow_ids),
        "candidate_resolved_false_allow_count": len(resolved_false_allow_ids),
        "candidate_resolved_false_allow_cases": _case_ids(resolved_false_allow_ids),
    }


def _evaluate_section(
    records: Sequence[PredictionRecord],
    variants: Sequence[str],
    contract: SignalContract,
    policy: EvaluationPolicy,
    ece_bins: int,
    curve_points: int,
    reference_variant: str | None,
    candidate_variant: str | None,
) -> dict[str, Any]:
    section = {
        "records": len(records),
        "variants": {
            variant: _evaluate_variant(
                records, variant, contract, policy, ece_bins, curve_points
            )
            for variant in variants
        },
    }
    if reference_variant is not None and candidate_variant is not None:
        section["comparison"] = _compare_variants(
            records,
            reference_variant,
            candidate_variant,
            contract,
            policy,
        )
    return section


def build_evaluation_report(
    prediction_set: PredictionSet,
    contract: SignalContract,
    policy: EvaluationPolicy,
    *,
    ece_bins: int = DEFAULT_ECE_BINS,
    curve_points: int = DEFAULT_CURVE_POINTS,
    slice_keys: Sequence[str] = (),
    reference_variant: str | None = None,
    candidate_variant: str | None = None,
) -> dict[str, Any]:
    errors: list[str] = []
    if not 2 <= ece_bins <= 100:
        errors.append("ece_bins must be between 2 and 100")
    if not 2 <= curve_points <= 101:
        errors.append("curve_points must be between 2 and 101")
    if len(slice_keys) > MAX_SLICE_KEYS:
        errors.append(f"at most {MAX_SLICE_KEYS} slice keys may be requested")
    if len(set(slice_keys)) != len(slice_keys):
        errors.append("slice keys must be unique")
    for key in slice_keys:
        if not SAFE_NAME.fullmatch(key):
            errors.append(f"invalid slice key: {key}")

    if (reference_variant is None) != (candidate_variant is None):
        errors.append("reference_variant and candidate_variant must be provided together")
    for role, variant in (
        ("reference_variant", reference_variant),
        ("candidate_variant", candidate_variant),
    ):
        if variant is not None and variant not in prediction_set.variants:
            errors.append(f"{role} {variant} is not present in predictions")
    if reference_variant is not None and reference_variant == candidate_variant:
        errors.append("reference_variant and candidate_variant must differ")
    if errors:
        raise EvaluationInputError(errors)

    overall = _evaluate_section(
        prediction_set.records,
        prediction_set.variants,
        contract,
        policy,
        ece_bins,
        curve_points,
        reference_variant,
        candidate_variant,
    )
    slices: dict[str, Any] = {}
    slice_group_count = 0
    for key in slice_keys:
        grouped: dict[str, list[PredictionRecord]] = defaultdict(list)
        for record in prediction_set.records:
            grouped[record.slices.get(key, "__missing__")].append(record)
        if len(grouped) > MAX_SLICE_VALUES:
            raise EvaluationInputError(
                [f"slice {key} has more than {MAX_SLICE_VALUES} distinct values"]
            )
        slice_group_count += len(grouped)
        if slice_group_count > MAX_SLICE_GROUPS_TOTAL:
            raise EvaluationInputError(
                [
                    "requested slices contain more than "
                    f"{MAX_SLICE_GROUPS_TOTAL} groups in total"
                ]
            )
        slices[key] = {
            value: _evaluate_section(
                records,
                prediction_set.variants,
                contract,
                policy,
                ece_bins,
                curve_points,
                reference_variant,
                candidate_variant,
            )
            for value, records in sorted(grouped.items())
        }

    return {
        "ok": True,
        "schema_version": REPORT_SCHEMA_VERSION,
        "signal_contract_version": contract.version,
        "policy_version": policy.version,
        "records": len(prediction_set.records),
        "variants": list(prediction_set.variants),
        "masked_annotation_states": sorted(MASKED_STATES),
        "policy_truth_semantics": {
            "block": "one or more blocking labels are positive",
            "unresolved": "no blocking label is positive and at least one is unknown or unreviewed",
            "allow": "all blocking labels are negative or not_applicable",
        },
        "metric_definitions": {
            "pr_auc": "exact step-wise average precision over reviewed positive/negative labels",
            "ece": f"equal-width expected calibration error with {ece_bins} bins",
            "confidence_intervals": "two-sided Wilson score interval at 95 percent",
            "positive_below_uncertain": (
                "positive truth below its uncertain threshold; it is a false allow only "
                "when the label has policy_action block and no other label keeps it hidden"
            ),
        },
        "overall": overall,
        "slices": slices,
    }


def _default_contract_path() -> Path:
    return (
        Path(__file__).resolve().parents[2]
        / "docs/dag/v3/glosh-visual-signals-v1.json"
    )


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Evaluate reviewed DAG V3 prediction JSONL without network access."
    )
    parser.add_argument("predictions", type=Path, help="versioned prediction JSONL")
    parser.add_argument("policy", type=Path, help="unapproved or approved threshold policy JSON")
    parser.add_argument(
        "--contract",
        type=Path,
        default=_default_contract_path(),
        help="visual signal contract JSON",
    )
    parser.add_argument("--ece-bins", type=int, default=DEFAULT_ECE_BINS)
    parser.add_argument("--curve-points", type=int, default=DEFAULT_CURVE_POINTS)
    parser.add_argument("--slice-key", action="append", default=[])
    parser.add_argument("--reference-variant")
    parser.add_argument("--candidate-variant")
    parser.add_argument("--pretty", action="store_true")
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = _parser().parse_args(argv)
    try:
        contract = load_signal_contract(args.contract)
        policy = load_evaluation_policy(args.policy, contract)
        predictions = load_prediction_set(args.predictions, contract)
        report = build_evaluation_report(
            predictions,
            contract,
            policy,
            ece_bins=args.ece_bins,
            curve_points=args.curve_points,
            slice_keys=args.slice_key,
            reference_variant=args.reference_variant,
            candidate_variant=args.candidate_variant,
        )
    except EvaluationInputError as error:
        for message in error.errors:
            print(message, file=sys.stderr)
        return 1
    except (OSError, UnicodeError, json.JSONDecodeError, ValueError) as error:
        print(f"could not evaluate predictions: {error}", file=sys.stderr)
        return 2

    indent = 2 if args.pretty else None
    print(json.dumps(report, ensure_ascii=True, sort_keys=True, indent=indent))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
