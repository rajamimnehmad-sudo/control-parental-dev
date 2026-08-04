"""Traceable Wikimedia corpus acquisition, cleanup and sealed splitting."""

from __future__ import annotations

import hashlib
import html
import ipaddress
import json
import re
import shutil
import socket
import time
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterable
from urllib.parse import urlencode, urlparse
from urllib.error import HTTPError
from urllib.request import Request, urlopen

import numpy as np
from PIL import Image, ImageOps


API_URL = "https://commons.wikimedia.org/w/api.php"
USER_AGENT = (
    "GloshIALab/1.0 "
    "(https://github.com/rajamimnehmad-sudo/control-parental-dev; local evaluation corpus)"
)
ALLOWED_LICENSES = {"by", "by-sa", "cc0", "pdm"}
ALLOWED_MIME = {"image/jpeg", "image/png", "image/webp"}
MAX_RESPONSE_BYTES = 12 * 1024 * 1024
MAX_FILE_BYTES = 5 * 1024 * 1024
MAX_TOTAL_BYTES = 600 * 1024 * 1024
MIN_EDGE = 160
MAX_SOURCE_DIMENSION = 4096
THUMBNAIL_WIDTH = 768
REQUEST_TIMEOUT = 8
SPLIT_ORDER = ("main_eval", "difficult", "final_sealed")
CURRENT_YEARS = {"2023", "2024", "2025", "2026"}
CURRENT_YEAR_PATTERN = re.compile(r"(?<!\d)(?:2023|2024|2025|2026)(?!\d)")
YEAR_PATTERN = re.compile(r"(?<!\d)(?:18|19|20)\d{2}(?!\d)")
UPLOAD_DATE_MARKERS = ("upload date", "fecha de subida", "uploaded")
PHASH_SIZE = 32
PHASH_LOW_FREQUENCY = 8
_PHASH_AXIS = np.arange(PHASH_SIZE)
_PHASH_BASIS = np.cos(
    np.pi
    * (2 * _PHASH_AXIS + 1)
    * np.arange(PHASH_LOW_FREQUENCY)[:, np.newaxis]
    / (2 * PHASH_SIZE)
)
_PHASH_BASIS[0] *= 1 / np.sqrt(2)
_PHASH_BASIS *= np.sqrt(2 / PHASH_SIZE)


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


def _metadata_value(metadata: Any, key: str) -> str | None:
    if not isinstance(metadata, dict):
        return None
    raw = metadata.get(key)
    if not isinstance(raw, dict) or not isinstance(raw.get("value"), str):
        return None
    text = re.sub(r"<[^>]+>", " ", raw["value"])
    text = " ".join(html.unescape(text).split())
    return text or None


def _license_id(value: str | None) -> str | None:
    if not value:
        return None
    normalized = value.upper().replace("_", " ").strip()
    if normalized.startswith("CC BY-SA"):
        return "by-sa"
    if normalized.startswith("CC BY"):
        return "by"
    if normalized.startswith("CC0"):
        return "cc0"
    if normalized in {"PUBLIC DOMAIN", "PDM"}:
        return "pdm"
    return None


def _source_cluster(title: Any) -> str:
    if not isinstance(title, str):
        return "untitled"
    normalized = title.lower().removeprefix("file:")
    normalized = re.sub(r"\.(jpe?g|png|webp)$", "", normalized)
    normalized = re.sub(r"\b(19|20)\d{2}\b", " year ", normalized)
    normalized = re.sub(r"\b\d+\b", " number ", normalized)
    normalized = re.sub(r"[_\W]+", " ", normalized)
    tokens = [token for token in normalized.split() if token not in {"number"}]
    return " ".join(tokens[:8]) or "untitled"


def _current_content_evidence(
    title: Any,
    description: str | None,
    categories: str | None,
    captured_at: str | None,
) -> str | None:
    """Identify current media without mistaking a recent archive upload for a new photo."""
    title_text = title if isinstance(title, str) else ""
    description_text = description or ""
    categories_text = categories or ""
    captured_years = set(YEAR_PATTERN.findall(captured_at or ""))
    title_years = set(YEAR_PATTERN.findall(title_text))
    description_years = set(YEAR_PATTERN.findall(description_text))
    semantic_text = " ".join((title_text, description_text, categories_text))
    semantic_years = set(YEAR_PATTERN.findall(semantic_text))
    current_semantic_years = semantic_years & CURRENT_YEARS

    if captured_years and not captured_years.intersection(CURRENT_YEARS):
        return None
    if title_years - CURRENT_YEARS:
        return None
    if description_years - CURRENT_YEARS and not description_years.intersection(
        CURRENT_YEARS
    ):
        return None
    if current_semantic_years:
        return "current_year_in_title_description_or_categories"
    if not captured_at or not CURRENT_YEAR_PATTERN.search(captured_at):
        return None
    lowered_capture = captured_at.casefold()
    if any(marker in lowered_capture for marker in UPLOAD_DATE_MARKERS):
        return None
    return "current_capture_date"


def _has_old_primary_date(row: dict[str, Any]) -> bool:
    title_years = set(YEAR_PATTERN.findall(str(row.get("title") or "")))
    capture_years = set(YEAR_PATTERN.findall(str(row.get("source_date") or "")))
    return bool(
        (title_years and title_years - CURRENT_YEARS)
        or (capture_years and not capture_years.intersection(CURRENT_YEARS))
    )


def _public_wikimedia_https(value: Any, allowed_hosts: set[str]) -> str:
    if not isinstance(value, str) or len(value) > 4096:
        raise ValueError("missing or oversized URL")
    parsed = urlparse(value)
    if (
        parsed.scheme != "https"
        or parsed.hostname not in allowed_hosts
        or parsed.username is not None
        or parsed.password is not None
    ):
        raise ValueError("URL is outside the approved Wikimedia hosts")
    addresses = {
        result[4][0]
        for result in socket.getaddrinfo(parsed.hostname, parsed.port or 443)
    }
    if not addresses or any(not ipaddress.ip_address(address).is_global for address in addresses):
        raise ValueError("URL did not resolve only to public addresses")
    return value


def _fetch_json(parameters: dict[str, Any]) -> dict[str, Any]:
    body: bytes | None = None
    last_error: OSError | None = None
    for attempt in range(2):
        request = Request(
            f"{API_URL}?{urlencode(parameters)}",
            headers={"Accept": "application/json", "User-Agent": USER_AGENT},
        )
        try:
            with urlopen(request, timeout=REQUEST_TIMEOUT) as response:
                body = response.read(MAX_RESPONSE_BYTES + 1)
            break
        except OSError as error:
            last_error = error
            time.sleep(0.5 * (attempt + 1))
    if body is None:
        raise OSError("Wikimedia request failed after retries") from last_error
    if len(body) > MAX_RESPONSE_BYTES:
        raise ValueError("Wikimedia response exceeds limit")
    payload = json.loads(body)
    if not isinstance(payload, dict) or isinstance(payload.get("error"), dict):
        raise ValueError("Wikimedia returned an invalid response")
    return payload


def inventory_query(query: str, pages: int = 3, page_size: int = 50) -> list[dict[str, Any]]:
    candidates: list[dict[str, Any]] = []
    continuation: int | None = None
    retrieved_at = utc_now()
    for _ in range(pages):
        parameters: dict[str, Any] = {
            "action": "query",
            "format": "json",
            "formatversion": "2",
            "generator": "search",
            "gsrnamespace": "6",
            "gsrsearch": f"{query} filetype:bitmap",
            "gsrlimit": page_size,
            "prop": "imageinfo",
            "iiprop": "url|mime|size|sha1|extmetadata",
            "iiurlwidth": THUMBNAIL_WIDTH,
            "iiextmetadatalanguage": "en",
            "iiextmetadatafilter": (
                "LicenseShortName|DateTimeOriginal|DateTime|ImageDescription|"
                "Categories|Artist|Credit|LicenseUrl"
            ),
            "maxlag": "5",
        }
        if continuation is not None:
            parameters["gsroffset"] = continuation
        payload = _fetch_json(parameters)
        raw_pages = payload.get("query", {}).get("pages", [])
        if not isinstance(raw_pages, list):
            break
        for page in raw_pages:
            if not isinstance(page, dict):
                continue
            info_rows = page.get("imageinfo")
            if (
                not isinstance(info_rows, list)
                or not info_rows
                or not isinstance(info_rows[0], dict)
            ):
                continue
            info = info_rows[0]
            metadata = info.get("extmetadata")
            license_name = _metadata_value(metadata, "LicenseShortName")
            license_id = _license_id(license_name)
            mime = info.get("mime")
            width = info.get("width")
            height = info.get("height")
            captured_at = _metadata_value(metadata, "DateTimeOriginal")
            uploaded_at = _metadata_value(metadata, "DateTime")
            description = _metadata_value(metadata, "ImageDescription")
            categories = _metadata_value(metadata, "Categories")
            title = page.get("title")
            current_evidence = _current_content_evidence(
                title,
                description,
                categories,
                captured_at,
            )
            if (
                license_id not in ALLOWED_LICENSES
                or mime not in ALLOWED_MIME
                or not isinstance(width, int)
                or not isinstance(height, int)
                or min(width, height) < MIN_EDGE
                or current_evidence is None
            ):
                continue
            try:
                asset_url = _public_wikimedia_https(
                    info.get("thumburl"),
                    {"upload.wikimedia.org"},
                )
                landing_url = _public_wikimedia_https(
                    info.get("descriptionurl"),
                    {"commons.wikimedia.org"},
                )
            except (OSError, ValueError):
                continue
            page_id = page.get("pageid")
            if not isinstance(page_id, int):
                continue
            candidates.append(
                {
                    "schema_version": "gloshia-lab-candidate-v1",
                    "sample_id": f"wikimedia:{page_id}",
                    "catalog": "wikimedia_commons",
                    "query": query,
                    "title": title,
                    "creator": _metadata_value(metadata, "Artist"),
                    "credit": _metadata_value(metadata, "Credit"),
                    "license_id": license_id,
                    "license_name": license_name,
                    "license_url": _metadata_value(metadata, "LicenseUrl"),
                    "mime": mime,
                    "source_url": landing_url,
                    "landing_url": landing_url,
                    "asset_url": asset_url,
                    "source_width": width,
                    "source_height": height,
                    "source_sha1": info.get("sha1"),
                    "source_cluster": _source_cluster(title),
                    "source_date": captured_at,
                    "source_uploaded_at": uploaded_at,
                    "current_evidence": current_evidence,
                    "retrieved_at": retrieved_at,
                    "provenance_status": "source_page_review_required_before_training",
                }
            )
        next_offset = payload.get("continue", {}).get("gsroffset")
        if not isinstance(next_offset, int):
            break
        continuation = next_offset
        time.sleep(0.5)
    return candidates


def _sha256_bytes(body: bytes) -> str:
    return hashlib.sha256(body).hexdigest()


def _dhash(image: Image.Image) -> int:
    gray = ImageOps.grayscale(image).resize((9, 8), resample=Image.Resampling.BILINEAR)
    pixels = list(gray.getdata())
    value = 0
    for y in range(8):
        for x in range(8):
            value = (value << 1) | int(pixels[y * 9 + x] > pixels[y * 9 + x + 1])
    return value


def _phash(image: Image.Image) -> int:
    gray = ImageOps.grayscale(image).resize(
        (PHASH_SIZE, PHASH_SIZE),
        resample=Image.Resampling.LANCZOS,
    )
    pixels = np.asarray(gray, dtype=np.float32)
    coefficients = _PHASH_BASIS @ pixels @ _PHASH_BASIS.T
    values = coefficients.flatten()[1 : PHASH_LOW_FREQUENCY**2 + 1]
    median = float(np.median(values))
    result = 0
    for value in values:
        result = (result << 1) | int(value > median)
    return result


def _near_duplicate(
    dhash: int,
    phash: int,
    known_dhashes: Iterable[int],
    known_phashes: Iterable[int],
) -> bool:
    return any(bin(dhash ^ item).count("1") <= 8 for item in known_dhashes) or any(
        bin(phash ^ item).count("1") <= 12 for item in known_phashes
    )


def _download(candidate: dict[str, Any]) -> bytes:
    url = _public_wikimedia_https(candidate["asset_url"], {"upload.wikimedia.org"})
    body: bytes | None = None
    final_url: str | None = None
    for attempt in range(3):
        request = Request(
            url,
            headers={"Accept": "image/jpeg,image/png,image/webp", "User-Agent": USER_AGENT},
        )
        try:
            with urlopen(request, timeout=REQUEST_TIMEOUT) as response:
                final_url = _public_wikimedia_https(
                    response.geturl(),
                    {"upload.wikimedia.org"},
                )
                body = response.read(MAX_FILE_BYTES + 1)
            break
        except HTTPError as error:
            if error.code != 429 or attempt == 2:
                raise
            retry_after = error.headers.get("Retry-After", "5")
            delay = float(retry_after) if retry_after.replace(".", "", 1).isdigit() else 5.0
            time.sleep(min(60.0, max(2.0, delay)))
    if body is None or final_url is None:
        raise ValueError("download failed after retries")
    if len(body) > MAX_FILE_BYTES or not body:
        raise ValueError("image exceeds limit or is empty")
    candidate["final_asset_url"] = final_url
    return body


def _write_json(path: Path, payload: Any) -> None:
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(
        json.dumps(payload, indent=2, ensure_ascii=False, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    temporary.replace(path)


def _write_jsonl(path: Path, rows: Iterable[dict[str, Any]]) -> None:
    temporary = path.with_suffix(path.suffix + ".tmp")
    with temporary.open("w", encoding="utf-8") as handle:
        for row in rows:
            handle.write(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n")
    temporary.replace(path)


def repair_currentness(
    output_dir: Path,
) -> dict[str, Any]:
    """Reopen a completed corpus when stricter date rules invalidate samples."""
    output_dir = output_dir.resolve()
    manifest_path = output_dir / "manifest.jsonl"
    partial_path = output_dir / "manifest.partial.jsonl"
    if not manifest_path.exists() or partial_path.exists():
        raise ValueError("repair requires one completed corpus without a partial run")
    rows = [
        json.loads(line)
        for line in manifest_path.read_text(encoding="utf-8").splitlines()
        if line.strip()
    ]
    invalid = [row for row in rows if _has_old_primary_date(row)]
    if not invalid:
        return {"repaired": 0, "remaining": len(rows), "backup": None}

    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    backup_dir = output_dir / "repair-backups" / stamp
    backup_dir.mkdir(parents=True)
    for name in (
        "manifest.jsonl",
        "summary.json",
        "predictions.jsonl",
        "evaluation-report.json",
    ):
        source = output_dir / name
        if source.exists():
            shutil.move(str(source), str(backup_dir / name))
    sheets = output_dir / "contact-sheets"
    if sheets.exists():
        shutil.move(str(sheets), str(backup_dir / "contact-sheets"))

    invalid_ids = {row["sample_id"] for row in invalid}
    valid = [row for row in rows if row["sample_id"] not in invalid_ids]
    invalid_images = backup_dir / "invalid-images"
    invalid_images.mkdir()
    for row in invalid:
        source = output_dir / row["local_path"]
        if source.exists():
            shutil.move(str(source), str(invalid_images / source.name))
    _write_jsonl(partial_path, valid)
    report = {
        "schema_version": "gloshia-lab-repair-v1",
        "repaired_at": utc_now(),
        "removed": [
            {"sample_id": row["sample_id"], "title": row.get("title")} for row in invalid
        ],
        "remaining": len(valid),
        "backup": str(backup_dir),
    }
    _write_json(output_dir / "repair-report.json", report)
    return {"repaired": len(invalid), "remaining": len(valid), "backup": str(backup_dir)}


def _assign_splits(rows: list[dict[str, Any]], include_sealed: bool = True) -> None:
    if not include_sealed:
        for row in rows:
            row["split"] = "directed_review"
            row["sealed"] = False
        return
    by_category: dict[str, list[dict[str, Any]]] = {}
    for row in rows:
        by_category.setdefault(row["category"], []).append(row)
    for category_rows in by_category.values():
        total = len(category_rows)
        targets = {
            "main_eval": int(total * 0.60),
            "difficult": int(total * 0.20),
            "final_sealed": total - int(total * 0.60) - int(total * 0.20),
        }
        grouped: dict[str, list[dict[str, Any]]] = {}
        for row in category_rows:
            group_key = row.get("source_cluster") or row["sample_id"]
            grouped.setdefault(group_key, []).append(row)
        ordered_groups = sorted(
            grouped.values(),
            key=lambda group: (
                -len(group),
                hashlib.sha256(
                    (
                        f"{group[0]['category']}:"
                        f"{group[0].get('source_cluster') or group[0]['sample_id']}"
                    ).encode()
                ).hexdigest(),
            ),
        )
        counts = Counter()
        for group in ordered_groups:
            group_size = len(group)
            fitting = [
                split
                for split in SPLIT_ORDER
                if counts[split] + group_size <= targets[split]
            ]
            if fitting:
                selected = max(
                    fitting,
                    key=lambda split: (
                        targets[split] - counts[split],
                        -SPLIT_ORDER.index(split),
                    ),
                )
            else:
                selected = min(
                    SPLIT_ORDER,
                    key=lambda split: (
                        max(0, counts[split] + group_size - targets[split]),
                        counts[split] / max(1, targets[split]),
                    ),
                )
            for row in group:
                row["split"] = selected
                row["sealed"] = selected == "final_sealed"
            counts[selected] += group_size


def build_corpus(
    query_plan_path: Path,
    output_dir: Path,
    target_override: int | None = None,
    include_sealed: bool = True,
    progress: Any = print,
) -> dict[str, Any]:
    plan = json.loads(query_plan_path.read_text(encoding="utf-8"))
    categories = plan.get("categories")
    if not isinstance(categories, dict) or not categories:
        raise ValueError("invalid query plan")
    output_dir.mkdir(parents=True, exist_ok=True)
    images_dir = output_dir / "images"
    images_dir.mkdir(exist_ok=True)
    manifest_path = output_dir / "manifest.jsonl"
    partial_path = output_dir / "manifest.partial.jsonl"
    failures_path = output_dir / "failures.partial.json"
    if manifest_path.exists():
        raise ValueError("completed corpus already exists")

    requested_total = sum(int(value["target"]) for value in categories.values())
    scale = 1.0 if target_override is None else target_override / requested_total
    targets = {
        category: max(1, int(round(int(settings["target"]) * scale)))
        for category, settings in categories.items()
    }
    delta = (target_override or requested_total) - sum(targets.values())
    if delta:
        first_category = next(iter(targets))
        targets[first_category] += delta

    rows: list[dict[str, Any]] = []
    if partial_path.exists():
        rows = [
            json.loads(line)
            for line in partial_path.read_text(encoding="utf-8").splitlines()
            if line.strip()
        ]
        progress(f"Reanudando corpus parcial con {len(rows)} imágenes")
    elif any(images_dir.iterdir()):
        raise ValueError("image directory is not empty and has no resumable manifest")
    seen_ids: set[str] = {row["sample_id"] for row in rows}
    seen_hashes: set[str] = {row["sha256"] for row in rows}
    seen_dhashes: list[int] = [int(row["dhash64"], 16) for row in rows]
    seen_phashes: list[int] = [int(row["phash64"], 16) for row in rows]
    source_clusters = Counter(row.get("source_cluster", "untitled") for row in rows)
    creators = Counter(row.get("creator") or "unknown" for row in rows)
    total_bytes = sum(int(row["bytes"]) for row in rows)
    failures = Counter()
    if failures_path.exists():
        stored_failures = json.loads(failures_path.read_text(encoding="utf-8"))
        if isinstance(stored_failures, dict):
            failures.update(
                {
                    str(key): int(value)
                    for key, value in stored_failures.items()
                    if isinstance(value, int) and value >= 0
                }
            )

    for category, settings in categories.items():
        category_count = sum(row["category"] == category for row in rows)
        if category_count >= targets[category]:
            progress(f"{category}: {category_count}/{targets[category]} ya completo")
            continue
        candidates_by_query: list[list[dict[str, Any]]] = []
        for query in settings["queries"]:
            progress(f"Inventario {category}: {query}")
            try:
                candidates_by_query.append(inventory_query(query))
            except (OSError, ValueError) as error:
                failures[f"inventory:{type(error).__name__}"] += 1
                progress(f"ADVERTENCIA inventario omitido: {query}")
                candidates_by_query.append([])
        candidates: list[dict[str, Any]] = []
        maximum_bucket = max((len(bucket) for bucket in candidates_by_query), default=0)
        for index in range(maximum_bucket):
            for bucket in candidates_by_query:
                if index < len(bucket):
                    candidates.append(bucket[index])
        unique_candidates: list[dict[str, Any]] = []
        for candidate in candidates:
            if candidate["sample_id"] not in seen_ids:
                seen_ids.add(candidate["sample_id"])
                unique_candidates.append(candidate)
        progress(
            f"{category}: {len(unique_candidates)} candidatos para {targets[category]} imágenes"
        )
        for candidate in unique_candidates:
            if category_count >= targets[category]:
                break
            cluster = candidate.get("source_cluster", "untitled")
            creator = candidate.get("creator") or "unknown"
            if source_clusters[cluster] >= 15:
                failures["source_cluster_limit"] += 1
                continue
            if creator != "unknown" and creators[creator] >= 30:
                failures["creator_limit"] += 1
                continue
            try:
                time.sleep(1.0)
                body = _download(candidate)
                digest = _sha256_bytes(body)
                if digest in seen_hashes:
                    failures["exact_duplicate"] += 1
                    continue
                with Image.open(__import__("io").BytesIO(body)) as opened:
                    if getattr(opened, "n_frames", 1) != 1:
                        raise ValueError("animated")
                    transposed = ImageOps.exif_transpose(opened)
                    if "transparency" in transposed.info or transposed.mode in {
                        "LA",
                        "RGBA",
                    }:
                        foreground = transposed.convert("RGBA")
                        background = Image.new("RGBA", foreground.size, (127, 127, 127, 255))
                        background.alpha_composite(foreground)
                        image = background.convert("RGB")
                    else:
                        image = transposed.convert("RGB")
                    if (
                        min(image.size) < MIN_EDGE
                        or max(image.size) > MAX_SOURCE_DIMENSION
                        or image.width * image.height > MAX_SOURCE_DIMENSION**2
                    ):
                        raise ValueError("unsafe_dimensions")
                    perceptual = _dhash(image)
                    perceptual_p = _phash(image)
                    if _near_duplicate(
                        perceptual,
                        perceptual_p,
                        seen_dhashes,
                        seen_phashes,
                    ):
                        failures["near_duplicate"] += 1
                        continue
                    filename = f"{digest[:20]}.jpg"
                    image.save(
                        images_dir / filename,
                        format="JPEG",
                        quality=92,
                        optimize=True,
                    )
                    normalized_bytes = (images_dir / filename).stat().st_size
                    width, height = image.size
                seen_hashes.add(digest)
                seen_dhashes.append(perceptual)
                seen_phashes.append(perceptual_p)
                total_bytes += normalized_bytes
                if total_bytes > MAX_TOTAL_BYTES:
                    raise ValueError("corpus exceeded total byte budget")
                row = {
                    **candidate,
                    "schema_version": "gloshia-lab-sample-v1",
                    "category": category,
                    "sha256": digest,
                    "dhash64": f"{perceptual:016x}",
                    "phash64": f"{perceptual_p:016x}",
                    "local_path": f"images/{filename}",
                    "width": width,
                    "height": height,
                    "bytes": normalized_bytes,
                    "downloaded_at": utc_now(),
                    "acquired_at": utc_now(),
                    "group_or_series": candidate.get("source_cluster"),
                    "usage_state": "internal_evaluation_ok",
                    "training_rights_status": "training_rights_uncertain",
                    "training_authorized": False,
                    "human_review_status": "pending",
                    "human_decision": None,
                    "codex_prelabel": None,
                    "model_prediction": None,
                }
                rows.append(row)
                source_clusters[cluster] += 1
                creators[creator] += 1
                _write_jsonl(partial_path, rows)
                _write_json(failures_path, dict(failures))
                category_count += 1
                if category_count % 25 == 0 or category_count == targets[category]:
                    progress(f"{category}: {category_count}/{targets[category]}")
            except Exception as error:
                failures[type(error).__name__ + ":" + str(error)[:80]] += 1
        if category_count < targets[category]:
            progress(
                f"ADVERTENCIA {category}: {category_count}/{targets[category]} válidas"
            )

    expected_total = target_override or requested_total
    if len(rows) != expected_total:
        incomplete_summary = {
            "schema_version": "gloshia-lab-corpus-summary-v1",
            "status": "incomplete_resumable",
            "created_at": utc_now(),
            "requested": expected_total,
            "downloaded": len(rows),
            "bytes": total_bytes,
            "categories": dict(Counter(row["category"] for row in rows)),
            "failures": dict(failures),
            "query_plan": str(query_plan_path),
            "training_authorized": False,
            "sealed_split_opened": False,
        }
        _write_json(output_dir / "summary.partial.json", incomplete_summary)
        _write_json(failures_path, dict(failures))
        raise ValueError(
            f"corpus incomplete ({len(rows)}/{expected_total}); "
            "the checkpoint was preserved for a safe resume"
        )

    _assign_splits(rows, include_sealed=include_sealed)
    _write_jsonl(manifest_path, rows)
    partial_path.unlink(missing_ok=True)
    failures_path.unlink(missing_ok=True)
    (output_dir / "summary.partial.json").unlink(missing_ok=True)
    summary = {
        "schema_version": "gloshia-lab-corpus-summary-v1",
        "created_at": utc_now(),
        "requested": expected_total,
        "downloaded": len(rows),
        "bytes": total_bytes,
        "categories": dict(Counter(row["category"] for row in rows)),
        "splits": dict(Counter(row["split"] for row in rows)),
        "licenses": dict(Counter(row["license_id"] for row in rows)),
        "failures": dict(failures),
        "query_plan": str(query_plan_path),
        "training_authorized": False,
        "sealed_split_opened": False,
        "review_only": not include_sealed,
    }
    _write_json(output_dir / "summary.json", summary)
    return summary
