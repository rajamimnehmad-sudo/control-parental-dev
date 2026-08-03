#!/usr/bin/env python3
"""Reconstruct filtered R3 review images and build a missing-signal queue."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import time
from pathlib import Path
from typing import Any, Iterable
from urllib.error import HTTPError
from urllib.parse import urlencode, urlparse
from urllib.request import Request, urlopen


API_URL = "https://commons.wikimedia.org/w/api.php"
USER_AGENT = "GloshDAGPrivateLab/1.0 Python-urllib/3"
ALLOWED_MIME = {"image/jpeg", "image/png", "image/webp"}
ALLOWED_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp"}
MAX_DOWNLOAD_BYTES = 12 * 1024 * 1024


def page_id_batches(page_ids: Iterable[int], size: int = 50) -> list[list[int]]:
    values = sorted(set(page_ids))
    if size < 1 or size > 50:
        raise ValueError("Wikimedia page-id batch size must be between 1 and 50")
    return [values[index : index + size] for index in range(0, len(values), size)]


def _safe_url(value: Any, allowed_hosts: set[str]) -> str | None:
    if not isinstance(value, str) or len(value) > 4096:
        return None
    parsed = urlparse(value)
    if (
        parsed.scheme != "https"
        or parsed.hostname not in allowed_hosts
        or parsed.username is not None
        or parsed.password is not None
    ):
        return None
    return value


def parse_wikimedia_pages(payload: dict[str, Any]) -> dict[int, dict[str, Any]]:
    result: dict[int, dict[str, Any]] = {}
    pages = payload.get("query", {}).get("pages", [])
    if not isinstance(pages, list):
        return result
    for page in pages:
        if not isinstance(page, dict):
            continue
        page_id = page.get("pageid")
        image_info = page.get("imageinfo")
        if (
            not isinstance(page_id, int)
            or not isinstance(image_info, list)
            or not image_info
            or not isinstance(image_info[0], dict)
        ):
            continue
        info = image_info[0]
        mime = info.get("mime")
        upstream_thumb = _safe_url(info.get("thumburl"), {"upload.wikimedia.org"})
        source_url = _safe_url(info.get("descriptionurl"), {"commons.wikimedia.org"})
        title = page.get("title")
        if (
            mime not in ALLOWED_MIME
            or upstream_thumb is None
            or not isinstance(title, str)
            or not title.startswith("File:")
        ):
            continue
        filename = title.removeprefix("File:")
        asset_url = f"https://commons.wikimedia.org/w/thumb.php?{urlencode({'f': filename, 'width': '640'})}"
        result[page_id] = {
            "title": title,
            "mime": mime,
            "asset_url": asset_url,
            "source_url": source_url,
            "width": info.get("thumbwidth"),
            "height": info.get("thumbheight"),
        }
    return result


def fetch_wikimedia(page_ids: list[int]) -> dict[int, dict[str, Any]]:
    parameters = {
        "action": "query",
        "format": "json",
        "formatversion": "2",
        "pageids": "|".join(str(value) for value in page_ids),
        "prop": "imageinfo",
        "iiprop": "url|mime|size",
        "iiurlwidth": "640",
        "maxlag": "5",
    }
    request = Request(
        f"{API_URL}?{urlencode(parameters)}",
        headers={"Accept": "application/json", "User-Agent": USER_AGENT},
    )
    body: bytes | None = None
    for attempt in range(5):
        try:
            with urlopen(request, timeout=20) as response:
                body = response.read(8 * 1024 * 1024 + 1)
            break
        except OSError:
            if attempt == 4:
                raise
            time.sleep(2.0 * (attempt + 1))
    if body is None:
        raise OSError("Wikimedia metadata request failed after retries")
    if len(body) > 8 * 1024 * 1024:
        raise ValueError("Wikimedia metadata response exceeded limit")
    payload = json.loads(body)
    if not isinstance(payload, dict) or isinstance(payload.get("error"), dict):
        raise ValueError("Wikimedia returned invalid metadata")
    return parse_wikimedia_pages(payload)


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def resolve_local_hashes(search_roots: list[Path], targets: set[str]) -> dict[str, Path]:
    found: dict[str, Path] = {}
    for root in search_roots:
        if not root.exists():
            continue
        for directory, _, filenames in os.walk(root):
            for filename in filenames:
                if len(found) == len(targets):
                    return found
                path = Path(directory) / filename
                if path.suffix.lower() not in ALLOWED_EXTENSIONS:
                    continue
                try:
                    digest = _sha256(path)
                except OSError:
                    continue
                if digest in targets and digest not in found:
                    found[digest] = path.resolve()
    return found


def _download(url: str, destination: Path) -> None:
    request = Request(url, headers={"User-Agent": USER_AGENT, "Accept": "image/*"})
    body: bytes | None = None
    for attempt in range(5):
        try:
            with urlopen(request, timeout=30) as response:
                body = response.read(MAX_DOWNLOAD_BYTES + 1)
            break
        except HTTPError as error:
            if error.code not in {429, 500, 502, 503, 504} or attempt == 4:
                raise
            retry_after = error.headers.get("Retry-After") if error.headers else None
            delay = float(retry_after) if retry_after and retry_after.isdigit() else 5.0 * (attempt + 1)
            time.sleep(min(delay, 30.0))
        except OSError:
            if attempt == 4:
                raise
            time.sleep(2.0 * (attempt + 1))
    if body is None:
        raise OSError("image download failed after retries")
    if len(body) > MAX_DOWNLOAD_BYTES:
        raise ValueError("image exceeded download limit")
    destination.parent.mkdir(parents=True, exist_ok=True)
    partial = destination.with_suffix(destination.suffix + ".part")
    partial.write_bytes(body)
    from PIL import Image

    try:
        with Image.open(partial) as image:
            image.verify()
    except Exception:
        partial.unlink(missing_ok=True)
        raise
    partial.replace(destination)


def build_queue(audit: dict[str, Any], resolved: dict[str, dict[str, Any]]) -> dict[str, Any]:
    queue: list[dict[str, Any]] = []
    unresolved: list[str] = []
    for record in audit.get("records", []):
        if record.get("policy_action") != "filter":
            continue
        sample_id = record["sample_id"]
        asset = resolved.get(sample_id)
        if asset is None:
            unresolved.append(sample_id)
            continue
        unknown = [name for name, state in record["labels"].items() if state == "unknown"]
        queue.append(
            {
                "sample_id": sample_id,
                "image_path": asset["image_path"],
                "source_url": asset.get("source_url"),
                "existing_positive_signals": sorted(
                    name for name, state in record["labels"].items() if state == "positive"
                ),
                "signals_to_review": unknown,
                "labels": record["labels"],
                "owner_policy_action": "filter",
                "training_authorization": record["training_authorization"],
                "publication_reuse_authorized": False,
            }
        )
    return {
        "schema_version": "gloshia-r3-focused-relabel-queue-v1",
        "summary": {
            "filtered_records": sum(
                1 for record in audit.get("records", []) if record.get("policy_action") == "filter"
            ),
            "resolved": len(queue),
            "unresolved": len(unresolved),
        },
        "unresolved_sample_ids": sorted(unresolved),
        "queue": sorted(queue, key=lambda item: item["sample_id"]),
    }


def reconstruct(audit_path: Path, output_dir: Path, search_roots: list[Path]) -> dict[str, Any]:
    audit = json.loads(audit_path.read_text(encoding="utf-8"))
    filtered = [record for record in audit.get("records", []) if record.get("policy_action") == "filter"]
    wikimedia_ids = [
        int(record["sample_id"].split(":", 1)[1])
        for record in filtered
        if record["sample_id"].startswith("wikimedia:")
    ]
    pilot_hashes = {
        record["sample_id"].split(":", 1)[1]
        for record in filtered
        if record["sample_id"].startswith("pilot:")
    }
    resolved: dict[str, dict[str, Any]] = {}

    output_dir.mkdir(parents=True, exist_ok=True)
    metadata_cache = output_dir / "wikimedia-metadata.json"
    metadata: dict[int, dict[str, Any]] = {}
    if metadata_cache.exists():
        cached = json.loads(metadata_cache.read_text(encoding="utf-8"))
        metadata = {int(key): value for key, value in cached.items()}
    missing_metadata = sorted(set(wikimedia_ids) - set(metadata))
    for batch in page_id_batches(missing_metadata):
        metadata.update(fetch_wikimedia(batch))
        metadata_cache.write_text(
            json.dumps(metadata, indent=2, ensure_ascii=False) + "\n",
            encoding="utf-8",
        )
    for page_id, item in metadata.items():
        extension = ".png" if item["mime"] == "image/png" else ".webp" if item["mime"] == "image/webp" else ".jpg"
        destination = output_dir / "images" / f"wikimedia-{page_id}{extension}"
        if not destination.exists():
            _download(item["asset_url"], destination)
            time.sleep(0.5)
        resolved[f"wikimedia:{page_id}"] = {
            "image_path": str(destination.resolve()),
            "source_url": item.get("source_url"),
        }

    local = resolve_local_hashes(search_roots, pilot_hashes)
    for digest, path in local.items():
        resolved[f"pilot:{digest}"] = {"image_path": str(path), "source_url": None}

    queue = build_queue(audit, resolved)
    (output_dir / "relabel-queue.json").write_text(
        json.dumps(queue, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )
    return queue


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--audit", required=True, type=Path)
    parser.add_argument("--output-dir", required=True, type=Path)
    parser.add_argument("--search-root", action="append", default=[], type=Path)
    args = parser.parse_args()
    queue = reconstruct(args.audit, args.output_dir, args.search_root)
    print(json.dumps(queue["summary"], indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
