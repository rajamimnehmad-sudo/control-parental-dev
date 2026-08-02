#!/usr/bin/env python3
"""Acquire a private, blind R2.1 repair batch without human labels.

The output deliberately omits creator, title, comments and profile data. The
two categories are sampling strata only; they are not model labels.
"""

from __future__ import annotations

import argparse
import hashlib
import io
import json
import mimetypes
import time
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterable

from PIL import Image, ImageOps

from tools.gloshia_lab.corpus import (
    MAX_FILE_BYTES,
    _dhash,
    _download,
    _phash,
    inventory_query,
)


SCHEMA_VERSION = "gloshia-r2-hard-negative-repair-batch-v1"
REVIEW_SCHEMA_VERSION = "gloshia-lab-human-review-v1"
TARGETS = {"hard_negative_filter_like": 25, "hard_negative_allow_like": 25}
MAX_TOTAL_BYTES = 120 * 1024 * 1024
MAX_EDGE = 4096
MIN_EDGE = 160
QUERIES = {
    "hard_negative_filter_like": (
        "women cycling team podium 2025",
        "women cycling team race 2024",
        "female cyclist team podium 2025",
        "women sports team uniform 2024",
        "women athletes podium 2025",
        "women running team event 2024",
    ),
    "hard_negative_allow_like": (
        "cycling team long sleeve podium 2025",
        "women sports team tracksuit 2024",
        "athletes team jacket podium 2025",
        "public event group adults 2024",
        "women cycling team jacket 2025",
        "sports team group fully covered 2024",
        "cycling team podium 2024",
        "women cycling team race 2023",
        "athletes group ceremony 2025",
        "sports team ceremony 2024",
    ),
}


def _utc_now() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


def _sha256_bytes(body: bytes) -> str:
    return hashlib.sha256(body).hexdigest()


def _cluster_id(value: Any) -> str:
    normalized = " ".join(str(value or "untitled").casefold().split())
    return "cluster-" + hashlib.sha256(normalized.encode("utf-8")).hexdigest()[:20]


def _hamming(left: int, right: int) -> int:
    return bin(int(left) ^ int(right)).count("1")


def _near_duplicate_compatible(
    dhash: int,
    phash: int,
    known_dhashes: Iterable[int],
    known_phashes: Iterable[int],
) -> bool:
    return any(_hamming(dhash, item) <= 8 for item in known_dhashes) or any(
        _hamming(phash, item) <= 12 for item in known_phashes
    )


def _read_manifest(path: Path) -> Iterable[dict[str, Any]]:
    if not path.is_file():
        return ()
    return (
        json.loads(line)
        for line in path.read_text(encoding="utf-8").splitlines()
        if line.strip()
    )


def known_content(manifests: Iterable[Path]) -> tuple[set[str], list[str], list[int], list[int]]:
    sample_ids: set[str] = set()
    hashes: set[str] = set()
    dhashes: list[int] = []
    phashes: list[int] = []
    for manifest in manifests:
        for row in _read_manifest(manifest):
            if row.get("sample_id"):
                sample_ids.add(str(row["sample_id"]))
            if row.get("sha256"):
                hashes.add(str(row["sha256"]))
            if row.get("dhash64"):
                dhashes.append(int(str(row["dhash64"]), 16))
            if row.get("phash64"):
                phashes.append(int(str(row["phash64"]), 16))
    return sample_ids, list(hashes), dhashes, phashes


def _candidate_order(candidates: Iterable[dict[str, Any]]) -> list[dict[str, Any]]:
    by_query: dict[str, list[dict[str, Any]]] = {}
    for candidate in candidates:
        by_query.setdefault(str(candidate.get("query") or "unknown"), []).append(candidate)
    ordered: list[dict[str, Any]] = []
    index = 0
    while True:
        added = False
        for query in sorted(by_query):
            bucket = by_query[query]
            if index < len(bucket):
                ordered.append(bucket[index])
                added = True
        if not added:
            return ordered
        index += 1


def _sanitized_row(
    candidate: dict[str, Any],
    *,
    category: str,
    digest: str,
    dhash: int,
    phash: int,
    width: int,
    height: int,
    bytes_count: int,
    local_path: str,
) -> dict[str, Any]:
    return {
        "schema_version": SCHEMA_VERSION,
        "sample_id": candidate["sample_id"],
        "source_url": candidate["source_url"],
        "asset_url": candidate["asset_url"],
        "catalog": "wikimedia_commons",
        "origin": "wikimedia_commons",
        "sha256": digest,
        "dhash64": f"{dhash:016x}",
        "phash64": f"{phash:016x}",
        "dimensions": {"width": width, "height": height},
        "width": width,
        "height": height,
        "mime": candidate.get("mime"),
        "bytes": bytes_count,
        "acquired_at": _utc_now(),
        "category": category,
        "cluster_id": _cluster_id(candidate.get("source_cluster")),
        "source_cluster_hash": _cluster_id(candidate.get("source_cluster")),
        "query_stratum": category,
        "human_decision": None,
        "human_review_status": "pending",
        "model_prediction": None,
        "usage_state": "internal_evaluation_ok",
        "training_rights_status": "training_rights_uncertain",
        "training_authorized": False,
        "sealed": False,
        "local_path": local_path,
    }


def build_batch(output_dir: Path, known_manifests: Iterable[Path]) -> dict[str, Any]:
    output_dir = output_dir.resolve()
    partial_path = output_dir / "manifest.partial.jsonl"
    if output_dir.exists():
        allowed = {"images", "manifest.partial.jsonl", "summary.partial.json"}
        existing = [item for item in output_dir.iterdir() if item.name not in allowed]
        images = output_dir / "images"
        if existing or (images.exists() and any(images.iterdir()) and not partial_path.exists()):
            raise ValueError("output directory must be empty")
    images_dir = output_dir / "images"
    images_dir.mkdir(parents=True, exist_ok=True)
    sample_ids, known_hashes, known_dhashes, known_phashes = known_content(known_manifests)
    selected: list[dict[str, Any]] = []
    if partial_path.exists():
        selected.extend(_read_manifest(partial_path))
    selected_ids = set(sample_ids)
    selected_hashes = set(known_hashes)
    selected_dhashes = list(known_dhashes)
    selected_phashes = list(known_phashes)
    selected_clusters: set[str] = set()
    for row in selected:
        selected_ids.add(str(row["sample_id"]))
        selected_hashes.add(str(row["sha256"]))
        selected_dhashes.append(int(str(row["dhash64"]), 16))
        selected_phashes.append(int(str(row["phash64"]), 16))
        selected_clusters.add(str(row["cluster_id"]))
    failures: Counter[str] = Counter()
    inventory_counts: Counter[str] = Counter()

    for category, target in TARGETS.items():
        candidates: list[dict[str, Any]] = []
        for query in QUERIES[category]:
            try:
                bucket = inventory_query(query, pages=2, page_size=30)
                candidates.extend(bucket)
                inventory_counts[category] += len(bucket)
            except (OSError, ValueError) as error:
                failures[f"inventory:{type(error).__name__}"] += 1
        for candidate in _candidate_order(candidates):
            if sum(row["category"] == category for row in selected) >= target:
                break
            sample_id = str(candidate.get("sample_id") or "")
            cluster = _cluster_id(candidate.get("source_cluster"))
            if not sample_id or sample_id in selected_ids:
                failures["duplicate_sample_id"] += 1
                continue
            if cluster in selected_clusters:
                failures["same_series_or_cluster"] += 1
                continue
            try:
                time.sleep(0.8)
                body = _download(candidate)
                digest = _sha256_bytes(body)
                if digest in selected_hashes:
                    failures["duplicate_sha256"] += 1
                    continue
                with Image.open(io.BytesIO(body)) as opened:
                    if getattr(opened, "n_frames", 1) != 1:
                        raise ValueError("animated_image")
                    image = ImageOps.exif_transpose(opened).convert("RGB")
                    width, height = image.size
                    if min(width, height) < MIN_EDGE or max(width, height) > MAX_EDGE:
                        raise ValueError("unsafe_dimensions")
                    dhash = _dhash(image)
                    phash = _phash(image)
                    if _near_duplicate_compatible(dhash, phash, selected_dhashes, selected_phashes):
                        failures["near_duplicate"] += 1
                        continue
                suffix = mimetypes.guess_extension(str(candidate.get("mime") or "")) or ".jpg"
                filename = f"{digest[:20]}{suffix}"
                image_path = images_dir / filename
                image_path.write_bytes(body)
                record = _sanitized_row(
                    candidate,
                    category=category,
                    digest=digest,
                    dhash=dhash,
                    phash=phash,
                    width=width,
                    height=height,
                    bytes_count=len(body),
                    local_path=f"images/{filename}",
                )
                selected.append(record)
                selected_ids.add(sample_id)
                selected_hashes.add(digest)
                selected_dhashes.append(dhash)
                selected_phashes.append(phash)
                selected_clusters.add(cluster)
                partial_path.write_text(
                    "\n".join(json.dumps(row, ensure_ascii=False, sort_keys=True) for row in selected) + "\n",
                    encoding="utf-8",
                )
                if sum(row["bytes"] for row in selected) > MAX_TOTAL_BYTES:
                    raise ValueError("batch_size_limit")
            except Exception as error:
                failures[type(error).__name__ + ":" + str(error)[:80]] += 1

    counts = Counter(row["category"] for row in selected)
    if counts != Counter(TARGETS):
        (output_dir / "summary.partial.json").write_text(
            json.dumps(
                {
                    "schema_version": SCHEMA_VERSION,
                    "status": "incomplete_resumable",
                    "downloaded": len(selected),
                    "categories": dict(counts),
                    "target": TARGETS,
                    "failures": dict(failures),
                },
                indent=2,
            )
            + "\n",
            encoding="utf-8",
        )
        raise ValueError(f"batch incomplete: {dict(counts)} expected {TARGETS}")
    selected.sort(key=lambda row: row["sample_id"])
    (output_dir / "manifest.jsonl").write_text(
        "\n".join(json.dumps(row, ensure_ascii=False, sort_keys=True) for row in selected) + "\n",
        encoding="utf-8",
    )
    partial_path.unlink(missing_ok=True)
    (output_dir / "reviews.json").write_text(
        json.dumps({"schema_version": REVIEW_SCHEMA_VERSION, "reviews": {}}, indent=2) + "\n",
        encoding="utf-8",
    )
    summary = {
        "schema_version": SCHEMA_VERSION,
        "status": "needs_human_review",
        "created_at": _utc_now(),
        "target": sum(TARGETS.values()),
        "downloaded": len(selected),
        "categories": dict(counts),
        "bytes": sum(row["bytes"] for row in selected),
        "clusters": len({row["cluster_id"] for row in selected}),
        "deduplication": {
            "known_manifests": [str(path) for path in known_manifests],
            "known_sha256_count": len(known_hashes),
            "known_dhash_count": len(known_dhashes),
            "known_phash_count": len(known_phashes),
            "failures": dict(failures),
        },
        "inventory_counts": dict(inventory_counts),
        "human_labels": "none; pending review",
        "final_sealed_opened": False,
        "training_authorized": False,
    }
    (output_dir / "summary.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
    (output_dir / "review-instructions.json").write_text(
        json.dumps(
            {
                "status": "blind_review_required",
                "before_decision": "do_not_show_model_prediction",
                "controls": ["swipe_right_allow", "swipe_left_filter", "button_doubt", "undo", "export_json"],
                "categories_are": "sampling strata only, not labels",
                "review_url": "http://127.0.0.1:8770/",
            },
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )
    return summary


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--known-manifest", action="append", type=Path, default=[])
    args = parser.parse_args()
    summary = build_batch(args.output, args.known_manifest)
    print(json.dumps(summary, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
