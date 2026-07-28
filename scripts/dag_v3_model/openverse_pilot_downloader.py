#!/usr/bin/env python3
"""Download a small, bounded Openverse pilot from a reviewed metadata inventory."""

from __future__ import annotations

import argparse
import hashlib
import ipaddress
import json
import socket
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, BinaryIO, Iterable
from urllib.parse import urlparse
from urllib.request import Request, urlopen


DOWNLOAD_VERSION = "dag-v3-openverse-pilot-download-1"
MAX_ITEMS = 100
DEFAULT_ITEMS = 20
MAX_FILE_BYTES = 8 * 1024 * 1024
MAX_TOTAL_BYTES = 100 * 1024 * 1024
REQUEST_TIMEOUT_SECONDS = 15
USER_AGENT = (
    "GloshDAGBot/1.0 "
    "(https://github.com/rajamimnehmad-sudo/control-parental-dev) Python-urllib/3"
)
ALLOWED_LICENSES = {"by", "by-sa", "cc0", "pdm"}


def _public_https_url(value: Any) -> str:
    if not isinstance(value, str) or not value or len(value) > 2048:
        raise ValueError("asset URL is missing or too long")
    parsed = urlparse(value)
    if (
        parsed.scheme != "https"
        or not parsed.hostname
        or parsed.username is not None
        or parsed.password is not None
    ):
        raise ValueError("asset URL must be public HTTPS without credentials")
    try:
        addresses = {
            address[4][0]
            for address in socket.getaddrinfo(parsed.hostname, parsed.port or 443)
        }
    except socket.gaierror as error:
        raise ValueError("asset hostname cannot be resolved") from error
    if not addresses:
        raise ValueError("asset hostname has no addresses")
    for address in addresses:
        ip = ipaddress.ip_address(address)
        if not ip.is_global:
            raise ValueError("asset hostname resolves to a non-public address")
    return value


def _detect_image_type(header: bytes) -> tuple[str, str] | None:
    if header.startswith(b"\xff\xd8\xff"):
        return "jpg", "image/jpeg"
    if header.startswith(b"\x89PNG\r\n\x1a\n"):
        return "png", "image/png"
    if header.startswith((b"GIF87a", b"GIF89a")):
        return "gif", "image/gif"
    if len(header) >= 12 and header[:4] == b"RIFF" and header[8:12] == b"WEBP":
        return "webp", "image/webp"
    return None


def _download_body(response: BinaryIO, remaining_total: int) -> bytes:
    allowed = min(MAX_FILE_BYTES, remaining_total)
    body = response.read(allowed + 1)
    if len(body) > allowed:
        raise ValueError("image exceeds the configured size limit")
    if not body:
        raise ValueError("image response is empty")
    return body


def _candidate_rows(path: Path) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    with path.open("r", encoding="utf-8") as source:
        for line_number, line in enumerate(source, 1):
            if not line.strip():
                continue
            try:
                row = json.loads(line)
            except json.JSONDecodeError as error:
                raise ValueError(f"invalid JSON on inventory line {line_number}") from error
            if not isinstance(row, dict):
                raise ValueError(f"inventory line {line_number} is not an object")
            rows.append(row)
    return rows


def _previous_hashes(manifests: Iterable[Path]) -> set[str]:
    hashes: set[str] = set()
    for manifest in manifests:
        for row in _candidate_rows(manifest):
            digest = row.get("sha256")
            if (
                row.get("status") in ("downloaded", "duplicate")
                and isinstance(digest, str)
                and len(digest) == 64
                and all(character in "0123456789abcdef" for character in digest)
            ):
                hashes.add(digest)
    return hashes


def download_pilot(
    inventory_path: Path,
    output_dir: Path,
    limit: int = DEFAULT_ITEMS,
    known_manifests: Iterable[Path] = (),
    delay_seconds: float = 0.0,
) -> dict[str, int]:
    if not 1 <= limit <= MAX_ITEMS:
        raise ValueError(f"limit must be between 1 and {MAX_ITEMS}")
    if not 0.0 <= delay_seconds <= 5.0:
        raise ValueError("delay_seconds must be between 0 and 5")

    rows = _candidate_rows(inventory_path)
    manifest_path = output_dir / "downloads.jsonl"
    images_dir = output_dir / "images"
    if manifest_path.exists() or (images_dir.exists() and any(images_dir.iterdir())):
        raise ValueError("output directory already contains a pilot download")
    output_dir.mkdir(parents=True, exist_ok=True)
    images_dir.mkdir(exist_ok=True)

    downloaded = 0
    failed = 0
    duplicates = 0
    total_bytes = 0
    seen_hashes = _previous_hashes(known_manifests)
    records: list[dict[str, Any]] = []

    for row in rows[:limit]:
        identifier = row.get("candidate_id") or row.get("openverse_id")
        record: dict[str, Any] = {
            "download_version": DOWNLOAD_VERSION,
            "catalog": row.get("catalog") or "openverse",
            "candidate_id": identifier,
            "openverse_id": row.get("openverse_id"),
            "wikimedia_page_id": row.get("wikimedia_page_id"),
            "query": row.get("query"),
            "title": row.get("title"),
            "creator": row.get("creator"),
            "source": row.get("source"),
            "landing_url": row.get("landing_url"),
            "asset_url": row.get("asset_url"),
            "license_id": row.get("license_id"),
            "license_name": row.get("license_name"),
            "license_version": row.get("license_version"),
            "license_url": row.get("license_url"),
            "attribution": row.get("attribution"),
            "review_status": "needs_license_and_visual_review",
        }
        try:
            if not isinstance(identifier, str) or not identifier:
                raise ValueError("candidate has no Openverse ID")
            if row.get("review_status") not in (None, "needs_review"):
                raise ValueError("candidate is not in the expected review state")
            if row.get("mature") is True:
                raise ValueError("candidate is marked as mature")
            if row.get("license_id") not in ALLOWED_LICENSES:
                raise ValueError("candidate license is not allowed for this pilot")
            asset_url = _public_https_url(row.get("asset_url"))
            remaining_total = MAX_TOTAL_BYTES - total_bytes
            if remaining_total <= 0:
                raise ValueError("pilot reached the total download size limit")
            request = Request(
                asset_url,
                headers={
                    "Accept": "image/jpeg,image/png,image/webp,image/gif",
                    "User-Agent": USER_AGENT,
                },
            )
            with urlopen(request, timeout=REQUEST_TIMEOUT_SECONDS) as response:
                final_url = _public_https_url(response.geturl())
                body = _download_body(response, remaining_total)
            detected = _detect_image_type(body[:16])
            if detected is None:
                raise ValueError("response is not a supported raster image")
            extension, mime = detected
            digest = hashlib.sha256(body).hexdigest()
            if digest in seen_hashes:
                duplicates += 1
                record.update({"status": "duplicate", "sha256": digest})
            else:
                filename = f"{downloaded + 1:03d}-{digest[:16]}.{extension}"
                (images_dir / filename).write_bytes(body)
                seen_hashes.add(digest)
                downloaded += 1
                total_bytes += len(body)
                record.update(
                    {
                        "status": "downloaded",
                        "retrieved_at": datetime.now(timezone.utc)
                        .isoformat()
                        .replace("+00:00", "Z"),
                        "final_asset_url": final_url,
                        "local_path": f"images/{filename}",
                        "sha256": digest,
                        "bytes": len(body),
                        "mime": mime,
                    }
                )
        except (OSError, ValueError) as error:
            failed += 1
            record.update({"status": "failed", "error": str(error)})
        records.append(record)
        if delay_seconds:
            time.sleep(delay_seconds)

    with manifest_path.open("w", encoding="utf-8") as manifest:
        for record in records:
            manifest.write(json.dumps(record, ensure_ascii=False, sort_keys=True) + "\n")

    return {
        "requested": min(limit, len(rows)),
        "downloaded": downloaded,
        "failed": failed,
        "duplicates": duplicates,
        "bytes": total_bytes,
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("inventory", type=Path)
    parser.add_argument("output_dir", type=Path)
    parser.add_argument("--limit", type=int, default=DEFAULT_ITEMS)
    parser.add_argument(
        "--known-downloads",
        action="append",
        default=[],
        type=Path,
        help="downloads.jsonl from an earlier batch; repeat for multiple batches",
    )
    parser.add_argument(
        "--delay-seconds",
        type=float,
        default=0.0,
        help="polite delay after each asset request; Wikimedia pilots should use 1",
    )
    args = parser.parse_args(argv)
    try:
        summary = download_pilot(
            args.inventory,
            args.output_dir,
            args.limit,
            args.known_downloads,
            args.delay_seconds,
        )
    except (OSError, ValueError) as error:
        print(f"fatal: {error}", file=sys.stderr)
        return 2
    print(json.dumps(summary, sort_keys=True))
    return 0 if summary["downloaded"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
