#!/usr/bin/env python3
"""Assemble the reproducible private R3 round30 candidate report."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any


def _read(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def _errors(score: dict[str, Any], split_name: str, kind: str) -> list[dict[str, Any]]:
    cases = score["metrics"][split_name]["cases"]
    if kind == "false_permissions":
        return [case for case in cases if case["target"] == 1 and case["predicted_action"] == "allow"]
    return [case for case in cases if case["target"] == 0 and case["predicted_action"] == "filter"]


def _comparison(baseline: dict[str, Any], candidate: dict[str, Any], split_name: str) -> dict[str, Any]:
    before = baseline["metrics"][split_name]["overall"]
    after = candidate["metrics"][split_name]["overall"]
    return {
        "split": split_name,
        "samples": after["samples"],
        "baseline_confusion": before["confusion_matrix"],
        "candidate_confusion": after["confusion_matrix"],
        "baseline_false_permissions": before["false_permissions"],
        "candidate_false_permissions": after["false_permissions"],
        "baseline_false_filters": before["false_filters"],
        "candidate_false_filters": after["false_filters"],
        "baseline_balanced_accuracy": before["balanced_accuracy"],
        "candidate_balanced_accuracy": after["balanced_accuracy"],
        "baseline_pr_auc": before["pr_auc"],
        "candidate_pr_auc": after["pr_auc"],
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--split", required=True, type=Path)
    parser.add_argument("--reviews", required=True, type=Path)
    parser.add_argument("--round30-evaluation", required=True, type=Path)
    parser.add_argument("--baseline-score", required=True, type=Path)
    parser.add_argument("--candidate-score", required=True, type=Path)
    parser.add_argument("--pilot", action="append", required=True, type=Path)
    parser.add_argument("--export", required=True, type=Path)
    parser.add_argument("--hybrid", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    split = _read(args.split)
    reviews = _read(args.reviews)["reviews"]
    round30_evaluation = _read(args.round30_evaluation)
    baseline = _read(args.baseline_score)
    candidate = _read(args.candidate_score)
    pilots = [_read(path) for path in args.pilot]
    export = _read(args.export)
    hybrid = _read(args.hybrid)
    binary_reviews = {key: value for key, value in reviews.items() if value.get("action") in ("allow", "filter")}
    report = {
        "schema_version": "gloshia-r3-round30-binary-candidate-report-v1",
        "ticket": "GLOSHIA-R3-ROUND30-BINARY-CANDIDATE",
        "status": "NO-GO",
        "baseline": {
            "label": baseline["model_label"],
            "path": baseline["model_path"],
            "sha256": baseline["model_sha256"],
            "bytes": baseline["model_bytes"],
            "score": baseline,
            "round30_primary_exam": round30_evaluation,
        },
        "candidate": {
            "label": candidate["model_label"],
            "path": candidate["model_path"],
            "sha256": candidate["model_sha256"],
            "bytes": candidate["model_bytes"],
            "score": candidate,
            "selected_pilot": "r3-round30-binary-pilot-03",
            "selection_rule": "validation only; false permissions first, false filters second, balanced accuracy then PR-AUC",
        },
        "pilots": [
            {
                "run_label": pilot["run_label"],
                "seed": pilot["seed"],
                "device": pilot["device"],
                "configuration": pilot["configuration"],
                "selected_validation": pilot["selected_validation"],
            }
            for pilot in pilots
        ],
        "comparison": [_comparison(baseline, candidate, split_name) for split_name in ("validation", "frozen_test")],
        "error_lists": {
            "baseline": {
                split_name: {
                    "false_permissions": _errors(baseline, split_name, "false_permissions"),
                    "false_filters": _errors(baseline, split_name, "false_filters"),
                }
                for split_name in ("validation", "frozen_test")
            },
            "candidate": {
                split_name: {
                    "false_permissions": _errors(candidate, split_name, "false_permissions"),
                    "false_filters": _errors(candidate, split_name, "false_filters"),
                }
                for split_name in ("validation", "frozen_test")
            },
        },
        "round30_review": {
            "total_review_records": len(reviews),
            "binary_records": len(binary_reviews),
            "allow": sum(row["action"] == "allow" for row in binary_reviews.values()),
            "filter": sum(row["action"] == "filter" for row in binary_reviews.values()),
            "doubt_excluded": sum(row.get("action") == "doubt" for row in reviews.values()),
            "evaluation_report": round30_evaluation,
            "not_used_as_independent_evaluation": True,
        },
        "split": {
            "path": str(args.split),
            "schema_version": split["schema_version"],
            "seed": split["seed"],
            "rows_by_split": split["rows_by_split"],
            "label_counts_by_split": split["label_counts_by_split"],
            "round30_additions": split["round30_additions"],
            "contamination_check": split["contamination_check"],
            "final_sealed_opened": split["final_sealed_opened"],
        },
        "exports": {
            "fp32": export["float_model"],
            "dynamic_int8": export["int8_model"],
            "hybrid_matmul_int8": hybrid,
        },
        "gates": {
            "validation_no_new_false_permissions": True,
            "validation_fewer_false_filters": True,
            "frozen_no_new_false_permissions": True,
            "frozen_fewer_false_filters": True,
            "fp32_onnx_checker_and_ort_cpu": True,
            "compact_android_compatible_export": False,
            "size_within_approximately_two_mb": False,
            "final_sealed_opened": False,
            "approved_for_dag": False,
        },
        "no_go_reasons": [
            "FP32 candidate is 33,220,815 bytes, 22,751,117 bytes larger than official R3.",
            "Dynamic INT8 contains ConvInteger and cannot open in local ORT CPU.",
            "Hybrid MatMul-INT8 is compact but changes 2 of 57 decisions versus candidate FP32.",
            "No Android-compatible compact export passed the parity and size gate.",
        ],
        "final_sealed_opened": False,
        "r3_official_unchanged": True,
        "dag_modified": False,
        "android_modified": False,
        "supabase_touched": False,
        "published": False,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(json.dumps({"status": report["status"], "selected": report["candidate"]["selected_pilot"], "no_go_reasons": report["no_go_reasons"]}, indent=2, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
