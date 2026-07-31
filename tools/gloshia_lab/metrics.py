"""Reports and review queues for GloshIA Lab."""

from __future__ import annotations

import json
import random
import statistics
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any, Iterable

from PIL import Image, ImageDraw, ImageFont, ImageOps


CALIBRATION_REVIEW_FLOOR = 0.30
CALIBRATION_FULL_THRESHOLDS = tuple(value / 100 for value in range(40, 61))
CALIBRATION_REGION_THRESHOLDS = tuple(value / 100 for value in range(35, 71))
CALIBRATION_REGION_VOTES = (1, 2, 3, 4)


def read_jsonl(path: Path) -> list[dict[str, Any]]:
    if not path.exists():
        return []
    return [
        json.loads(line)
        for line in path.read_text(encoding="utf-8").splitlines()
        if line.strip()
    ]


def read_reviews(path: Path) -> dict[str, dict[str, Any]]:
    if not path.exists():
        return {}
    payload = json.loads(path.read_text(encoding="utf-8"))
    reviews = payload.get("reviews", {})
    return reviews if isinstance(reviews, dict) else {}


def joined_rows(corpus_dir: Path, include_sealed: bool = False) -> list[dict[str, Any]]:
    manifest = read_jsonl(corpus_dir / "manifest.jsonl")
    predictions = {
        row["sample_id"]: row for row in read_jsonl(corpus_dir / "predictions.jsonl")
    }
    reviews = read_reviews(corpus_dir / "reviews.json")
    rows = []
    for item in manifest:
        if item.get("split") == "final_sealed" and not include_sealed:
            continue
        rows.append(
            {
                **item,
                "model_prediction": predictions.get(item["sample_id"]),
                "human_decision": reviews.get(item["sample_id"]),
            }
        )
    return rows


def build_review_queue(
    rows: Iterable[dict[str, Any]],
    maximum: int = 200,
    seed: int = 20260730,
) -> list[dict[str, Any]]:
    eligible = [
        row
        for row in rows
        if row.get("model_prediction")
        and not row["model_prediction"].get("error")
        and not row.get("human_decision")
    ]
    uncertain = sorted(
        (
            row
            for row in eligible
            if 0.24 <= row["model_prediction"]["maximum_probability"] <= 0.62
        ),
        key=lambda row: abs(row["model_prediction"]["maximum_probability"] - 0.4),
    )
    selected: list[dict[str, Any]] = []
    seen: set[str] = set()

    def add(row: dict[str, Any]) -> None:
        if row["sample_id"] not in seen and len(selected) < maximum:
            selected.append(row)
            seen.add(row["sample_id"])

    for row in uncertain[: int(maximum * 0.70)]:
        add(row)

    randomizer = random.Random(seed)
    by_category: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for row in eligible:
        by_category[row["category"]].append(row)
    while len(selected) < maximum and any(by_category.values()):
        for category in sorted(by_category):
            bucket = by_category[category]
            if not bucket:
                continue
            add(bucket.pop(randomizer.randrange(len(bucket))))
            if len(selected) >= maximum:
                break
    return selected


def evaluation_report(corpus_dir: Path, include_sealed: bool = False) -> dict[str, Any]:
    full_manifest = read_jsonl(corpus_dir / "manifest.jsonl")
    rows = joined_rows(corpus_dir, include_sealed=include_sealed)
    predicted = [row for row in rows if row.get("model_prediction")]
    valid = [row for row in predicted if not row["model_prediction"].get("error")]
    reviewed = [
        row
        for row in valid
        if (row.get("human_decision") or {}).get("action") in {"allow", "filter"}
    ]
    matrix = Counter()
    false_allows: list[str] = []
    false_filters: list[str] = []
    for row in reviewed:
        expected = row["human_decision"]["action"]
        actual = row["model_prediction"]["action"]
        matrix[f"{expected}_as_{actual}"] += 1
        if expected == "filter" and actual == "allow":
            false_allows.append(row["sample_id"])
        if expected == "allow" and actual == "filter":
            false_filters.append(row["sample_id"])

    latencies = sorted(row["model_prediction"]["elapsed_ms"] for row in valid)

    def percentile(fraction: float) -> float | None:
        if not latencies:
            return None
        return latencies[min(len(latencies) - 1, int(len(latencies) * fraction))]

    by_category: dict[str, dict[str, int]] = {}
    for category in sorted({row["category"] for row in valid}):
        category_rows = [row for row in valid if row["category"] == category]
        by_category[category] = dict(
            Counter(row["model_prediction"]["action"] for row in category_rows)
        )

    allows = matrix["allow_as_allow"]
    filters = matrix["filter_as_filter"]
    false_allow_count = matrix["filter_as_allow"]
    false_filter_count = matrix["allow_as_filter"]
    expected_filter = filters + false_allow_count
    expected_allow = allows + false_filter_count
    hashes = [row.get("sha256") for row in full_manifest if row.get("sha256")]
    source_clusters = Counter(
        row.get("source_cluster") or "unknown" for row in full_manifest
    )
    creators = Counter(row.get("creator") or "unknown" for row in full_manifest)
    return {
        "schema_version": "gloshia-lab-evaluation-report-v1",
        "scope": "sealed_opened" if include_sealed else "sealed_excluded",
        "corpus_rows": len(rows),
        "predicted": len(predicted),
        "valid_predictions": len(valid),
        "reviewed_reference": len(reviewed),
        "pending_review": len(valid) - len(reviewed),
        "decisions": dict(Counter(row["model_prediction"]["action"] for row in valid)),
        "by_category": by_category,
        "latency_ms": {
            "median": round(statistics.median(latencies), 3) if latencies else None,
            "p95": percentile(0.95),
            "maximum": max(latencies) if latencies else None,
        },
        "confusion_matrix": dict(matrix),
        "filter_recall": round(filters / expected_filter, 6) if expected_filter else None,
        "allow_recall": round(allows / expected_allow, 6) if expected_allow else None,
        "false_allow_rate": (
            round(false_allow_count / expected_filter, 6) if expected_filter else None
        ),
        "false_filter_rate": (
            round(false_filter_count / expected_allow, 6) if expected_allow else None
        ),
        "false_allow_ids": false_allows[:100],
        "false_filter_ids": false_filters[:100],
        "corpus_quality": {
            "total_rows": len(full_manifest),
            "unique_sha256": len(set(hashes)),
            "duplicate_sha256": len(hashes) - len(set(hashes)),
            "categories": dict(
                Counter(row.get("category") or "unknown" for row in full_manifest)
            ),
            "splits": dict(
                Counter(row.get("split") or "unknown" for row in full_manifest)
            ),
            "licenses": dict(
                Counter(row.get("license_id") or "unknown" for row in full_manifest)
            ),
            "current_evidence": dict(
                Counter(row.get("current_evidence") or "unknown" for row in full_manifest)
            ),
            "missing_creator": sum(not row.get("creator") for row in full_manifest),
            "missing_license_url": sum(
                not row.get("license_url") for row in full_manifest
            ),
            "largest_source_cluster": max(source_clusters.values(), default=0),
            "largest_creator_group": max(creators.values(), default=0),
            "training_authorized": False,
        },
        "important_limit": (
            "Metrics against human truth are provisional until the requested review queue "
            "is completed. The final_sealed split remains excluded unless explicitly opened."
        ),
    }


def _decision_metrics(rows: list[dict[str, Any]], key: str) -> dict[str, Any]:
    matrix = Counter((row["human_action"], row[key]) for row in rows)
    true_filter = matrix[("filter", "filter")]
    false_allow = matrix[("filter", "allow")]
    false_filter = matrix[("allow", "filter")]
    true_allow = matrix[("allow", "allow")]
    expected_filter = true_filter + false_allow
    expected_allow = true_allow + false_filter
    total = expected_filter + expected_allow
    return {
        "reviewed": total,
        "true_filter": true_filter,
        "false_allow": false_allow,
        "false_filter": false_filter,
        "true_allow": true_allow,
        "accuracy": round((true_filter + true_allow) / total, 6) if total else None,
        "filter_recall": round(true_filter / expected_filter, 6)
        if expected_filter
        else None,
        "allow_recall": round(true_allow / expected_allow, 6)
        if expected_allow
        else None,
    }


def experimental_calibration_action(
    full_probability: float,
    regional_probabilities: list[float],
    *,
    full_threshold: float,
    region_threshold: float,
    region_votes: int,
) -> str:
    if full_probability >= full_threshold:
        return "filter"
    if full_probability < CALIBRATION_REVIEW_FLOOR:
        return "allow"
    return (
        "filter"
        if sum(value >= region_threshold for value in regional_probabilities)
        >= region_votes
        else "allow"
    )


def calibration_experiment_report(
    corpus_dir: Path,
    diagnostic_predictions_path: Path,
) -> dict[str, Any]:
    """Select on main_eval and report once on difficult without opening sealed."""
    manifest = {row["sample_id"]: row for row in read_jsonl(corpus_dir / "manifest.jsonl")}
    baseline = {
        row["sample_id"]: row for row in read_jsonl(corpus_dir / "predictions.jsonl")
    }
    diagnostic = {
        row["sample_id"]: row for row in read_jsonl(diagnostic_predictions_path)
    }
    reviews = read_reviews(corpus_dir / "reviews.json")
    rows: list[dict[str, Any]] = []
    doubt_count = 0
    for sample_id, review in reviews.items():
        if review.get("action") == "doubt":
            doubt_count += 1
            continue
        if review.get("action") not in {"allow", "filter"}:
            continue
        item = manifest.get(sample_id)
        current = baseline.get(sample_id)
        sweep = diagnostic.get(sample_id)
        if not item or not current or not sweep:
            raise ValueError(f"missing calibration evidence for {sample_id}")
        if item.get("split") == "final_sealed":
            raise ValueError("refusing calibration with final_sealed evidence")
        if not sweep.get("diagnostic_region_sweep"):
            raise ValueError(f"diagnostic region sweep missing for {sample_id}")
        width = int(sweep["source_width"])
        height = int(sweep["source_height"])
        panoramic = max(width, height) / min(width, height) >= 2.0
        rows.append(
            {
                "sample_id": sample_id,
                "split": item["split"],
                "source_cluster": item.get("source_cluster") or "unknown",
                "human_action": review["action"],
                "baseline_action": current["action"],
                "full_probability": float(sweep["full_probability"]),
                "regional_probabilities": [
                    float(value) for value in sweep["regional_probabilities"]
                ],
                "panoramic": panoramic,
            }
        )

    development = [row for row in rows if row["split"] == "main_eval"]
    validation = [row for row in rows if row["split"] == "difficult"]
    if not development or not validation:
        raise ValueError("main_eval and difficult reviewed evidence are both required")
    baseline_development = _decision_metrics(development, "baseline_action")
    recall_floor = baseline_development["filter_recall"]
    if recall_floor is None:
        raise ValueError("development evidence has no human filter decisions")

    candidates: list[
        tuple[tuple[float, float, float, float, float, int], dict[str, Any]]
    ] = []
    for full_threshold in CALIBRATION_FULL_THRESHOLDS:
        for region_threshold in CALIBRATION_REGION_THRESHOLDS:
            for region_votes in CALIBRATION_REGION_VOTES:
                candidate_rows = []
                for row in development:
                    action = row["baseline_action"]
                    if not row["panoramic"]:
                        action = experimental_calibration_action(
                            row["full_probability"],
                            row["regional_probabilities"],
                            full_threshold=full_threshold,
                            region_threshold=region_threshold,
                            region_votes=region_votes,
                        )
                    candidate_rows.append({**row, "candidate_action": action})
                metrics = _decision_metrics(candidate_rows, "candidate_action")
                if (metrics["filter_recall"] or 0.0) < recall_floor:
                    continue
                rank = (
                    metrics["allow_recall"] or 0.0,
                    metrics["accuracy"] or 0.0,
                    metrics["filter_recall"] or 0.0,
                    -full_threshold,
                    -region_threshold,
                    -region_votes,
                )
                candidates.append(
                    (
                        rank,
                        {
                            "full_threshold": full_threshold,
                            "region_threshold": region_threshold,
                            "region_votes": region_votes,
                            "development_metrics": metrics,
                        },
                    )
                )
    if not candidates:
        raise ValueError("no calibration candidate preserves development filter recall")
    selected = max(candidates, key=lambda item: item[0])[1]

    evaluated = []
    for row in rows:
        action = row["baseline_action"]
        if not row["panoramic"]:
            action = experimental_calibration_action(
                row["full_probability"],
                row["regional_probabilities"],
                full_threshold=selected["full_threshold"],
                region_threshold=selected["region_threshold"],
                region_votes=selected["region_votes"],
            )
        evaluated.append({**row, "candidate_action": action})

    def metrics_for(split: str | None, key: str) -> dict[str, Any]:
        selected_rows = [
            row for row in evaluated if split is None or row["split"] == split
        ]
        return _decision_metrics(selected_rows, key)

    changed = [
        row
        for row in evaluated
        if row["baseline_action"] != row["candidate_action"]
    ]
    return {
        "schema_version": "gloshia-lab-calibration-experiment-v1",
        "sealed_split_opened": False,
        "reviewed_decisive": len(rows),
        "reviewed_doubt_excluded": doubt_count,
        "unique_source_clusters": len({row["source_cluster"] for row in rows}),
        "selection": {
            "development_split": "main_eval",
            "validation_split": "difficult",
            "recall_floor": recall_floor,
            "grid_candidates": len(candidates),
            "panoramic_policy": "unchanged",
            **selected,
        },
        "baseline": {
            "development": metrics_for("main_eval", "baseline_action"),
            "validation": metrics_for("difficult", "baseline_action"),
            "all_reviewed": metrics_for(None, "baseline_action"),
        },
        "candidate": {
            "development": metrics_for("main_eval", "candidate_action"),
            "validation": metrics_for("difficult", "candidate_action"),
            "all_reviewed": metrics_for(None, "candidate_action"),
        },
        "changed_decisions": {
            "total": len(changed),
            "filter_to_allow": sum(
                row["baseline_action"] == "filter"
                and row["candidate_action"] == "allow"
                for row in changed
            ),
            "allow_to_filter": sum(
                row["baseline_action"] == "allow"
                and row["candidate_action"] == "filter"
                for row in changed
            ),
            "sample_ids": [row["sample_id"] for row in changed],
        },
        "decision": "LAB_CANDIDATE_ONLY",
        "important_limit": (
            "The difficult validation contains only two human filter decisions. This result "
            "does not authorize Android integration or opening final_sealed."
        ),
    }


def write_contact_sheets(
    rows: list[dict[str, Any]],
    corpus_dir: Path,
    output_dir: Path,
    columns: int = 4,
    rows_per_sheet: int = 4,
) -> list[Path]:
    output_dir.mkdir(parents=True, exist_ok=True)
    cell_width = 300
    cell_height = 360
    image_height = 280
    page_size = columns * rows_per_sheet
    font = ImageFont.load_default(size=14)
    outputs: list[Path] = []
    for page_index in range(0, len(rows), page_size):
        page_rows = rows[page_index : page_index + page_size]
        sheet = Image.new(
            "RGB",
            (columns * cell_width, rows_per_sheet * cell_height),
            "white",
        )
        draw = ImageDraw.Draw(sheet)
        for cell_index, row in enumerate(page_rows):
            x = (cell_index % columns) * cell_width
            y = (cell_index // columns) * cell_height
            with Image.open(corpus_dir / row["local_path"]) as opened:
                image = ImageOps.exif_transpose(opened).convert("RGB")
                image.thumbnail((cell_width - 16, image_height - 16))
                image_x = x + (cell_width - image.width) // 2
                image_y = y + 8 + (image_height - 16 - image.height) // 2
                sheet.paste(image, (image_x, image_y))
            prediction = row.get("model_prediction") or {}
            label = (
                f"{page_index + cell_index + 1:03d} {row['sample_id']}\n"
                f"{row['category']} · {prediction.get('action', '?')} "
                f"{prediction.get('maximum_probability', 0):.3f}"
            )
            draw.multiline_text((x + 8, y + image_height + 4), label, fill="black", font=font)
        output = output_dir / f"review-{page_index // page_size + 1:02d}.jpg"
        sheet.save(output, quality=90, optimize=True)
        outputs.append(output)
    return outputs
