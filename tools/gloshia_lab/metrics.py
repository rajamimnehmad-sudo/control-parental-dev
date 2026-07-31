"""Reports and review queues for GloshIA Lab."""

from __future__ import annotations

import json
import random
import statistics
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any, Iterable

from PIL import Image, ImageDraw, ImageFont, ImageOps


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
