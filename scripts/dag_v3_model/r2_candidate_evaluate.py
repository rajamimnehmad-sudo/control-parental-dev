#!/usr/bin/env python3
"""Evaluate one GloshIA binary model against a fixed candidate split."""

from __future__ import annotations

import argparse
import json
from collections import defaultdict
from pathlib import Path
from typing import Any, Iterable


def _ratio(numerator: int, denominator: int) -> float | None:
    return round(numerator / denominator, 6) if denominator else None


def _average_precision(targets: list[int], probabilities: list[float]) -> float | None:
    positives = sum(targets)
    if not positives:
        return None
    order = sorted(range(len(targets)), key=lambda index: probabilities[index], reverse=True)
    found = 0
    total = 0.0
    for rank, index in enumerate(order, start=1):
        if targets[index]:
            found += 1
            total += found / rank
    return round(total / positives, 6)


def classification_metrics(
    targets: list[int],
    probabilities: list[float],
    threshold: float = 0.4,
    predicted: list[int] | None = None,
) -> dict[str, Any]:
    if len(targets) != len(probabilities) or not targets:
        raise ValueError("targets and probabilities must have equal non-zero length")
    predicted = predicted or [int(probability >= threshold) for probability in probabilities]
    tn = sum(target == 0 and prediction == 0 for target, prediction in zip(targets, predicted))
    fp = sum(target == 0 and prediction == 1 for target, prediction in zip(targets, predicted))
    fn = sum(target == 1 and prediction == 0 for target, prediction in zip(targets, predicted))
    tp = sum(target == 1 and prediction == 1 for target, prediction in zip(targets, predicted))
    allow_recall = _ratio(tn, tn + fp)
    filter_recall = _ratio(tp, tp + fn)
    balanced = None
    if allow_recall is not None and filter_recall is not None:
        balanced = round((allow_recall + filter_recall) / 2, 6)
    filter_precision = _ratio(tp, tp + fp)
    allow_precision = _ratio(tn, tn + fn)
    f1_filter = (
        round(2 * filter_precision * filter_recall / (filter_precision + filter_recall), 6)
        if filter_precision is not None and filter_recall is not None and filter_precision + filter_recall
        else None
    )
    f1_allow = (
        round(2 * allow_precision * allow_recall / (allow_precision + allow_recall), 6)
        if allow_precision is not None and allow_recall is not None and allow_precision + allow_recall
        else None
    )
    return {
        "samples": len(targets),
        "threshold": threshold,
        "confusion_matrix": {
            "allow_as_allow": tn,
            "allow_as_filter": fp,
            "filter_as_allow": fn,
            "filter_as_filter": tp,
        },
        "accuracy": _ratio(tn + tp, len(targets)),
        "balanced_accuracy": balanced,
        "filter_precision": filter_precision,
        "filter_recall": filter_recall,
        "allow_precision": allow_precision,
        "allow_recall": allow_recall,
        "filter_f1": f1_filter,
        "allow_f1": f1_allow,
        "false_permissions": {"count": fn, "denominator": tp + fn, "rate": _ratio(fn, tp + fn)},
        "false_filters": {"count": fp, "denominator": tn + fp, "rate": _ratio(fp, tn + fp)},
        "pr_auc": _average_precision(targets, probabilities),
    }


def evaluate_records(records: list[dict[str, Any]], predictions: dict[str, dict[str, Any]], threshold: float = 0.4) -> dict[str, Any]:
    missing = [row["sample_id"] for row in records if row["sample_id"] not in predictions]
    if missing:
        raise ValueError(f"missing predictions for {len(missing)} samples")
    cases = []
    for row in records:
        prediction = predictions[row["sample_id"]]
        probability = prediction.get("filter_probability", prediction.get("maximum_probability"))
        if probability is None:
            raise ValueError(f"missing filter probability: {row['sample_id']}")
        predicted_action = prediction.get("predicted_action", prediction.get("action"))
        if predicted_action not in ("allow", "filter"):
            predicted_action = "filter" if float(probability) >= threshold else "allow"
        cases.append(
            {
                "sample_id": row["sample_id"],
                "category": row.get("category"),
                "split": row["split"],
                "human_action": row["human_action"],
                "target": row["target"],
                "filter_probability": float(probability),
                "predicted_action": predicted_action,
            }
        )
    result: dict[str, Any] = {
        "overall": classification_metrics(
            [case["target"] for case in cases],
            [case["filter_probability"] for case in cases],
            threshold,
            [int(case["predicted_action"] == "filter") for case in cases],
        ),
        "by_category": {},
    }
    by_category: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for case in cases:
        by_category[str(case.get("category") or "uncategorized")].append(case)
    for category, category_cases in sorted(by_category.items()):
        result["by_category"][category] = classification_metrics(
            [case["target"] for case in category_cases],
            [case["filter_probability"] for case in category_cases],
            threshold,
            [int(case["predicted_action"] == "filter") for case in category_cases],
        )
    result["cases"] = cases
    return result


def _read_predictions(path: Path) -> dict[str, dict[str, Any]]:
    if path.suffix == ".jsonl":
        return {row["sample_id"]: row for row in (json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip())}
    payload = json.loads(path.read_text(encoding="utf-8"))
    rows: Iterable[dict[str, Any]] = payload.get("predictions", payload.get("quantized_predictions", []))
    return {row["sample_id"]: row for row in rows}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--split", required=True, type=Path)
    parser.add_argument("--predictions", required=True, type=Path)
    parser.add_argument("--model-label", required=True)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--threshold", type=float, default=0.4)
    parser.add_argument("--split-name", choices=("train", "validation", "frozen_test"))
    args = parser.parse_args()
    split_payload = json.loads(args.split.read_text(encoding="utf-8"))
    records = split_payload["records"]
    if args.split_name:
        records = [record for record in records if record["split"] == args.split_name]
    predictions = _read_predictions(args.predictions)
    report = {
        "schema_version": "gloshia-r2-candidate-evaluation-v1",
        "model": args.model_label,
        "split_schema": split_payload.get("schema_version"),
        "threshold": args.threshold,
        "split": args.split_name or "all",
        "evaluation_scope": "binary human allow/filter only; doubt and final_sealed excluded",
        **evaluate_records(records, predictions, args.threshold),
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(json.dumps(report["overall"], indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
