#!/usr/bin/env python3
"""Build a bounded, private commercial-adjacent GloshIA review pilot.

This tool is evaluation-only. It never assigns a human label, opens sealed data,
or declares training rights. It keeps source URLs and opaque series clusters but
does not copy creator names, titles, comments, or profiles into the pilot.
"""

from __future__ import annotations

import argparse
import hashlib
import io
import json
import re
import subprocess
import tempfile
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from PIL import Image, ImageOps

from tools.gloshia_lab.corpus import _dhash as canonical_dhash
from tools.gloshia_lab.corpus import _phash as canonical_phash

CURRENT_YEARS = {"2023", "2024", "2025", "2026"}
HISTORICAL_MARKERS = (
    "dpla",
    "postcard",
    "circa",
    "19th century",
    "18th century",
    "1918",
    "1919",
    "1920",
    "1930",
    "1940",
    "1950",
    "1960",
    "1970",
    "1980",
    "1990",
)
COMMERCIAL_MARKERS = (
    "advert",
    "banner",
    "billboard",
    "campaign",
    "catalog",
    "clothing",
    "commercial",
    "fashion",
    "lottery",
    "mall",
    "mannequin",
    "market",
    "payment",
    "poster",
    "promotion",
    "retail",
    "shop",
    "store",
)
ALLOWED_LICENSES = {"by", "by-sa", "cc0", "pdm"}
MAX_BYTES = 5 * 1024 * 1024


def hamming_distance(left: int, right: int) -> int:
    return bin(left ^ right).count("1")


def now() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


def digest_bytes(body: bytes) -> str:
    return hashlib.sha256(body).hexdigest()


def dhash(image: Image.Image) -> int:
    return canonical_dhash(image)


def phash(image: Image.Image) -> int:
    return canonical_phash(image)


def rows(path: Path) -> list[dict[str, Any]]:
    result = []
    for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if not line.strip():
            continue
        value = json.loads(line)
        if not isinstance(value, dict) or not value.get("candidate_id"):
            raise ValueError(f"invalid candidate at line {line_number}")
        result.append(value)
    return result


def known_hashes(manifest: Path | None) -> tuple[set[str], list[int], list[int]]:
    exact: set[str] = set()
    dhashes: list[int] = []
    phashes: list[int] = []
    if manifest is None or not manifest.exists():
        return exact, dhashes, phashes
    for line in manifest.read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        row = json.loads(line)
        if isinstance(row.get("sha256"), str):
            exact.add(row["sha256"])
        for key, target in (("dhash64", dhashes), ("phash64", phashes)):
            value = row.get(key)
            if isinstance(value, str):
                try:
                    target.append(int(value, 16))
                except ValueError:
                    pass
    return exact, dhashes, phashes


def current_and_relevant(candidate: dict[str, Any]) -> bool:
    title = str(candidate.get("title") or "")
    title_text = title.casefold()
    text = " ".join(
        str(candidate.get(key) or "")
        for key in ("title", "query", "source_date", "source_uploaded_at")
    ).casefold()
    if any(marker in text for marker in HISTORICAL_MARKERS):
        return False
    years = set(re.findall(r"(?<!\d)(?:18|19|20)\d{2}(?!\d)", title_text))
    current_years = years.intersection(CURRENT_YEARS)
    compact_dates = set(re.findall(r"(?<!\d)(?:2023|2024|2025|2026)\d{4}(?!\d)", title_text))
    if years and not current_years:
        return False
    # A query containing a recent year is not sufficient: Commons often returns
    # historical scans for modern search terms. Require a current year/date in
    # the file title or primary capture metadata.
    if not current_years and not compact_dates:
        return False
    return any(marker in text for marker in COMMERCIAL_MARKERS)


def category(candidate: dict[str, Any]) -> str:
    text = " ".join(
        str(candidate.get(key) or "")
        for key in ("title", "query")
    ).casefold()
    if any(marker in text for marker in ("payment", "checkout", "market")):
        return "payment_commerce_control"
    if any(marker in text for marker in ("mannequin", "catalog", "clothing", "fashion")):
        return "retail_catalog_fashion"
    if any(marker in text for marker in ("poster", "billboard", "banner", "advertising")):
        return "promotional_text_graphic"
    if any(marker in text for marker in ("family", "people", "mall", "store", "shop")):
        return "commercial_banner_people"
    return "safe_graphic_control"


def opaque_cluster(candidate: dict[str, Any]) -> str:
    source = str(candidate.get("title") or candidate.get("candidate_id"))
    normalized = re.sub(r"\b(?:19|20)\d{2}\b|\b\d+\b", " ", source.casefold())
    normalized = re.sub(r"[^a-z0-9]+", " ", normalized).strip()
    return "cluster:" + hashlib.sha256(normalized.encode("utf-8")).hexdigest()[:16]


def fetch(url: str) -> bytes:
    with tempfile.NamedTemporaryFile(suffix=".img") as temporary:
        command = [
            "curl", "--fail", "--location", "--silent", "--show-error",
            "--connect-timeout", "8", "--max-time", "25", "--retry", "1",
            "--retry-delay", "1", "--user-agent", "GloshIALab/1.0 local evaluation",
            "-o", temporary.name, url,
        ]
        completed = subprocess.run(command, capture_output=True, text=True, check=False)
        if completed.returncode:
            detail = (completed.stderr or "curl_failed").strip()[-240:]
            raise RuntimeError(detail)
        body = Path(temporary.name).read_bytes()
    if not body or len(body) > MAX_BYTES:
        raise ValueError("empty_or_oversized_image")
    return body


def build(inventory: Path, output: Path, limit: int, known_manifest: Path | None) -> dict[str, Any]:
    output.mkdir(parents=True, exist_ok=True)
    images = output / "images"
    images.mkdir(exist_ok=True)
    manifest = output / "manifest.jsonl"
    if manifest.exists():
        raise ValueError("output already contains a completed pilot")

    candidates = [candidate for candidate in rows(inventory) if current_and_relevant(candidate)]
    # Round-robin by query prevents one search result family from dominating.
    buckets: dict[str, list[dict[str, Any]]] = {}
    for candidate in candidates:
        buckets.setdefault(str(candidate.get("query") or "unknown"), []).append(candidate)
    ordered: list[dict[str, Any]] = []
    while len(ordered) < len(candidates):
        advanced = False
        for bucket in buckets.values():
            if bucket:
                ordered.append(bucket.pop(0))
                advanced = True
        if not advanced:
            break

    exact, known_dhashes, known_phashes = known_hashes(known_manifest)
    seen_clusters: Counter[str] = Counter()
    local_rows: list[dict[str, Any]] = []
    log_rows: list[dict[str, Any]] = []
    seen_candidates: set[str] = set()
    for candidate in ordered:
        if len(local_rows) >= limit:
            break
        candidate_id = str(candidate["candidate_id"])
        if candidate_id in seen_candidates:
            continue
        seen_candidates.add(candidate_id)
        cluster = opaque_cluster(candidate)
        if seen_clusters[cluster] >= 2:
            log_rows.append({"candidate_id": candidate_id, "status": "series_cluster_limit"})
            continue
        if candidate.get("license_id") not in ALLOWED_LICENSES:
            log_rows.append({"candidate_id": candidate_id, "status": "license_not_allowed"})
            continue
        try:
            body = fetch(str(candidate["asset_url"]))
            digest = digest_bytes(body)
            if digest in exact:
                log_rows.append({"candidate_id": candidate_id, "status": "duplicate_sha256", "sha256": digest})
                continue
            with Image.open(io.BytesIO(body)) as opened:
                if getattr(opened, "n_frames", 1) != 1:
                    raise ValueError("animated_image")
                image = ImageOps.exif_transpose(opened).convert("RGB")
                if min(image.size) < 160 or max(image.size) > 4096:
                    raise ValueError("unsafe_dimensions")
                d_hash = dhash(image)
                p_hash = phash(image)
                if any(hamming_distance(d_hash, item) <= 8 for item in known_dhashes) or any(
                    hamming_distance(p_hash, item) <= 12 for item in known_phashes
                ):
                    log_rows.append({"candidate_id": candidate_id, "status": "duplicate_perceptual", "sha256": digest})
                    continue
                filename = f"{digest[:20]}.jpg"
                image.save(images / filename, format="JPEG", quality=92, optimize=True)
                width, height = image.size
                stored_bytes = (images / filename).stat().st_size
            exact.add(digest)
            known_dhashes.append(d_hash)
            known_phashes.append(p_hash)
            seen_clusters[cluster] += 1
            local_rows.append({
                "schema_version": "gloshia-r3-commercial-sample-v1",
                "sample_id": f"r3-commercial:{candidate_id}",
                "source_url": candidate.get("landing_url"),
                "asset_url": candidate.get("asset_url"),
                "origin": "wikimedia_commons",
                "catalog": "wikimedia_commons",
                "license_id": candidate.get("license_id"),
                "license_url": candidate.get("license_url"),
                "sha256": digest,
                "dhash64": f"{d_hash:016x}",
                "phash64": f"{p_hash:016x}",
                "width": width,
                "height": height,
                "mime": "image/jpeg",
                "bytes": stored_bytes,
                "acquired_at": now(),
                "category": category(candidate),
                "source_cluster": cluster,
                "group_or_series": cluster,
                "query_family": str(candidate.get("query") or "unknown"),
                "local_path": f"images/{filename}",
                "split": "directed_review",
                "sealed": False,
                "usage_state": "internal_evaluation_ok",
                "training_rights_status": "training_rights_uncertain",
                "training_authorized": False,
                "human_review_status": "pending",
                "human_decision": None,
                "model_prediction": None,
                "codex_prelabel": None,
            })
            log_rows.append({"candidate_id": candidate_id, "status": "downloaded", "sha256": digest})
        except (OSError, RuntimeError, ValueError) as error:
            log_rows.append({"candidate_id": candidate_id, "status": "failed", "error": str(error)})

    manifest.write_text(
        "".join(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n" for row in local_rows),
        encoding="utf-8",
    )
    (output / "download-log.jsonl").write_text(
        "".join(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n" for row in log_rows),
        encoding="utf-8",
    )
    summary = {
        "schema_version": "gloshia-r3-commercial-pilot-summary-v1",
        "created_at": now(),
        "requested": limit,
        "eligible_candidates": len(candidates),
        "downloaded": len(local_rows),
        "categories": dict(Counter(row["category"] for row in local_rows)),
        "clusters": len({row["source_cluster"] for row in local_rows}),
        "duplicates_or_excluded": sum(row["status"] != "downloaded" for row in log_rows),
        "training_rights_status": "training_rights_uncertain",
        "training_authorized": False,
        "sealed_split_opened": False,
        "human_review_required": True,
    }
    (output / "summary.json").write_text(json.dumps(summary, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    return summary


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("inventory", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--limit", type=int, default=50)
    parser.add_argument("--known-manifest", type=Path)
    args = parser.parse_args()
    if not 1 <= args.limit <= 60:
        parser.error("--limit must be between 1 and 60")
    print(json.dumps(build(args.inventory, args.output, args.limit, args.known_manifest), ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
