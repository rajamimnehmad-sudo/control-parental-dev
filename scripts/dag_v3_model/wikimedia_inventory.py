#!/usr/bin/env python3
"""Collect bounded Wikimedia Commons image candidates with license metadata."""

from __future__ import annotations

import argparse
import html
import json
import re
import sys
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Any, Iterable
from urllib.parse import urlencode, urlparse
from urllib.request import Request, urlopen


API_URL = "https://commons.wikimedia.org/w/api.php"
INVENTORY_VERSION = "dag-v3-wikimedia-inventory-1"
MAX_QUERIES = 12
MAX_PAGE_SIZE = 20
MAX_PAGES = 3
MAX_RESPONSE_BYTES = 8 * 1024 * 1024
REQUEST_TIMEOUT_SECONDS = 15
USER_AGENT = (
    "GloshDAGBot/1.0 "
    "(https://github.com/rajamimnehmad-sudo/control-parental-dev) Python-urllib/3"
)
ALLOWED_MIME = {"image/jpeg", "image/png", "image/gif", "image/webp"}


@dataclass(frozen=True)
class InventoryResult:
    candidates: tuple[dict[str, Any], ...]
    requested_pages: int
    skipped_duplicates: int
    skipped_ineligible: int

    def summary(self) -> dict[str, int]:
        return {
            "candidates": len(self.candidates),
            "requested_pages": self.requested_pages,
            "skipped_duplicates": self.skipped_duplicates,
            "skipped_ineligible": self.skipped_ineligible,
        }


def _validate_queries(queries: Iterable[str]) -> tuple[str, ...]:
    normalized: list[str] = []
    for query in queries:
        value = query.strip()
        if not value or len(value) > 200 or any(ord(character) < 32 for character in value):
            raise ValueError("queries must be non-blank, printable, and at most 200 characters")
        if value not in normalized:
            normalized.append(value)
    if not normalized:
        raise ValueError("at least one query is required")
    if len(normalized) > MAX_QUERIES:
        raise ValueError(f"at most {MAX_QUERIES} unique queries are allowed")
    return tuple(normalized)


def _safe_https(value: Any) -> str | None:
    if not isinstance(value, str) or not value or len(value) > 4096:
        return None
    parsed = urlparse(value)
    if (
        parsed.scheme != "https"
        or not parsed.netloc
        or parsed.username is not None
        or parsed.password is not None
    ):
        return None
    return value


def _metadata_value(metadata: Any, key: str) -> str | None:
    if not isinstance(metadata, dict):
        return None
    item = metadata.get(key)
    if not isinstance(item, dict) or not isinstance(item.get("value"), str):
        return None
    text = re.sub(r"<[^>]+>", " ", item["value"])
    text = " ".join(html.unescape(text).split())
    return text or None


def _license_id(short_name: str | None) -> str | None:
    if not short_name:
        return None
    normalized = short_name.upper().replace("_", " ").strip()
    if normalized.startswith("CC BY-SA"):
        return "by-sa"
    if normalized.startswith("CC BY"):
        return "by"
    if normalized.startswith("CC0"):
        return "cc0"
    if normalized in {"PUBLIC DOMAIN", "PDM"}:
        return "pdm"
    return None


def _fetch_page(
    query: str,
    page_size: int,
    continuation: str | None,
) -> dict[str, Any]:
    parameters = {
        "action": "query",
        "format": "json",
        "formatversion": "2",
        "generator": "search",
        "gsrnamespace": "6",
        "gsrsearch": f"{query} filetype:bitmap",
        "gsrlimit": page_size,
        "prop": "imageinfo",
        "iiprop": "url|mime|size|sha1|extmetadata",
        "iiurlwidth": "1024",
        "maxlag": "2",
    }
    if continuation is not None:
        parameters["gsroffset"] = continuation
    request = Request(
        f"{API_URL}?{urlencode(parameters)}",
        headers={"Accept": "application/json", "User-Agent": USER_AGENT},
    )
    with urlopen(request, timeout=REQUEST_TIMEOUT_SECONDS) as response:
        body = response.read(MAX_RESPONSE_BYTES + 1)
    if len(body) > MAX_RESPONSE_BYTES:
        raise ValueError("Wikimedia response exceeds the metadata size limit")
    payload = json.loads(body)
    if not isinstance(payload, dict):
        raise ValueError("Wikimedia returned an unexpected response")
    error = payload.get("error")
    if isinstance(error, dict):
        code = error.get("code") if isinstance(error.get("code"), str) else "unknown"
        raise ValueError(f"Wikimedia API error: {code}")
    return payload


def _candidate(query: str, page: dict[str, Any], retrieved_at: str) -> dict[str, Any] | None:
    page_id = page.get("pageid")
    title = page.get("title")
    imageinfo = page.get("imageinfo")
    if (
        not isinstance(page_id, int)
        or not isinstance(title, str)
        or not isinstance(imageinfo, list)
        or not imageinfo
        or not isinstance(imageinfo[0], dict)
    ):
        return None
    info = imageinfo[0]
    mime = info.get("mime")
    if mime not in ALLOWED_MIME:
        return None
    metadata = info.get("extmetadata")
    license_name = _metadata_value(metadata, "LicenseShortName")
    license_id = _license_id(license_name)
    if license_id is None:
        return None
    asset_url = _safe_https(info.get("thumburl"))
    landing_url = _safe_https(info.get("descriptionurl"))
    if asset_url is None or landing_url is None:
        return None

    creator = _metadata_value(metadata, "Artist")
    license_url = _safe_https(_metadata_value(metadata, "LicenseUrl"))
    credit = _metadata_value(metadata, "Credit")
    restrictions = _metadata_value(metadata, "Restrictions")
    review_flags = [
        "source_page_license_must_be_verified",
        "personality_rights_must_be_reviewed",
        "visual_relevance_must_be_reviewed",
    ]
    if creator is None:
        review_flags.append("missing_creator")
    if license_url is None:
        review_flags.append("missing_license_url")
    if restrictions:
        review_flags.append("source_reports_additional_restrictions")

    width = info.get("width") if isinstance(info.get("width"), int) else None
    height = info.get("height") if isinstance(info.get("height"), int) else None
    sha1 = info.get("sha1") if isinstance(info.get("sha1"), str) else None
    return {
        "inventory_version": INVENTORY_VERSION,
        "catalog": "wikimedia_commons",
        "candidate_id": f"wikimedia:{page_id}",
        "wikimedia_page_id": page_id,
        "retrieved_at": retrieved_at,
        "query": query,
        "review_status": "needs_review",
        "review_flags": review_flags,
        "title": title,
        "creator": creator,
        "license_id": license_id,
        "license_name": license_name,
        "license_url": license_url,
        "attribution": credit or creator,
        "source": "wikimedia",
        "landing_url": landing_url,
        "asset_url": asset_url,
        "asset_variant": "thumbnail_1024",
        "width": width,
        "height": height,
        "mime": mime,
        "source_sha1": sha1,
        "mature": False,
    }


def collect_inventory(
    queries: Iterable[str],
    page_size: int = MAX_PAGE_SIZE,
    max_pages: int = 1,
) -> InventoryResult:
    normalized_queries = _validate_queries(queries)
    if not 1 <= page_size <= MAX_PAGE_SIZE:
        raise ValueError(f"page_size must be between 1 and {MAX_PAGE_SIZE}")
    if not 1 <= max_pages <= MAX_PAGES:
        raise ValueError(f"max_pages must be between 1 and {MAX_PAGES}")

    retrieved_at = datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
    candidates: list[dict[str, Any]] = []
    seen_ids: set[int] = set()
    requested_pages = 0
    skipped_duplicates = 0
    skipped_ineligible = 0

    for query in normalized_queries:
        continuation: str | None = None
        for _ in range(max_pages):
            payload = _fetch_page(query, page_size, continuation)
            requested_pages += 1
            pages = payload.get("query", {}).get("pages", [])
            if not isinstance(pages, list):
                raise ValueError("Wikimedia returned invalid page data")
            for page in pages:
                if not isinstance(page, dict):
                    skipped_ineligible += 1
                    continue
                item = _candidate(query, page, retrieved_at)
                if item is None:
                    skipped_ineligible += 1
                    continue
                page_id = item["wikimedia_page_id"]
                if page_id in seen_ids:
                    skipped_duplicates += 1
                    continue
                seen_ids.add(page_id)
                candidates.append(item)
            next_offset = payload.get("continue", {}).get("gsroffset")
            if not pages or not isinstance(next_offset, int):
                break
            continuation = str(next_offset)

    return InventoryResult(
        candidates=tuple(candidates),
        requested_pages=requested_pages,
        skipped_duplicates=skipped_duplicates,
        skipped_ineligible=skipped_ineligible,
    )


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--query", action="append", required=True)
    parser.add_argument("--page-size", type=int, default=MAX_PAGE_SIZE)
    parser.add_argument("--max-pages", type=int, default=1)
    args = parser.parse_args(argv)
    try:
        result = collect_inventory(args.query, args.page_size, args.max_pages)
    except (OSError, ValueError, json.JSONDecodeError) as error:
        print(f"fatal: {error}", file=sys.stderr)
        return 2
    for candidate in result.candidates:
        print(json.dumps(candidate, ensure_ascii=False, sort_keys=True))
    print(json.dumps(result.summary(), sort_keys=True), file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
