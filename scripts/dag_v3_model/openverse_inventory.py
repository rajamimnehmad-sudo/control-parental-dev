#!/usr/bin/env python3
"""Collect bounded Openverse metadata candidates without downloading images."""

from __future__ import annotations

import argparse
import json
import sys
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Any, Iterable
from urllib.parse import urlencode, urlparse
from urllib.request import Request, urlopen


API_URL = "https://api.openverse.org/v1/images/"
INVENTORY_VERSION = "dag-v3-openverse-inventory-1"
ALLOWED_LICENSES = ("by", "by-sa", "cc0", "pdm")
MAX_QUERIES = 12
MAX_PAGE_SIZE = 20
MAX_PAGES = 3
MAX_RESPONSE_BYTES = 5 * 1024 * 1024
REQUEST_TIMEOUT_SECONDS = 10
USER_AGENT = "Glosh-DAG-V3-metadata-inventory/1.0 (no image downloads)"


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
        if not value:
            raise ValueError("queries must not be blank")
        if len(value) > 200:
            raise ValueError("queries must not exceed 200 characters")
        if any(ord(character) < 32 for character in value):
            raise ValueError("queries must not contain control characters")
        if value not in normalized:
            normalized.append(value)
    if not normalized:
        raise ValueError("at least one query is required")
    if len(normalized) > MAX_QUERIES:
        raise ValueError(f"at most {MAX_QUERIES} unique queries are allowed")
    return tuple(normalized)


def _safe_https(value: Any) -> str | None:
    if not isinstance(value, str) or not value or len(value) > 2048:
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


def _fetch_page(query: str, page: int, page_size: int) -> dict[str, Any]:
    parameters = urlencode(
        {
            "q": query,
            "license": ",".join(ALLOWED_LICENSES),
            "mature": "false",
            "page": page,
            "page_size": page_size,
        }
    )
    request = Request(
        f"{API_URL}?{parameters}",
        headers={"Accept": "application/json", "User-Agent": USER_AGENT},
    )
    with urlopen(request, timeout=REQUEST_TIMEOUT_SECONDS) as response:
        body = response.read(MAX_RESPONSE_BYTES + 1)
    if len(body) > MAX_RESPONSE_BYTES:
        raise ValueError("Openverse response exceeds the metadata size limit")
    payload = json.loads(body)
    if not isinstance(payload, dict) or not isinstance(payload.get("results"), list):
        raise ValueError("Openverse returned an unexpected response")
    return payload


def _candidate(query: str, raw: dict[str, Any], retrieved_at: str) -> dict[str, Any] | None:
    identifier = raw.get("id")
    license_id = raw.get("license")
    if not isinstance(identifier, str) or not identifier:
        return None
    if license_id not in ALLOWED_LICENSES or raw.get("mature") is True:
        return None

    creator = raw.get("creator") if isinstance(raw.get("creator"), str) else None
    provider = raw.get("provider") if isinstance(raw.get("provider"), str) else None
    source = raw.get("source") if isinstance(raw.get("source"), str) else None
    license_version = raw.get("license_version")
    attribution = raw.get("attribution") if isinstance(raw.get("attribution"), str) else None
    width = raw.get("width") if isinstance(raw.get("width"), int) else None
    height = raw.get("height") if isinstance(raw.get("height"), int) else None
    filetype = raw.get("filetype") if isinstance(raw.get("filetype"), str) else None
    landing_url = _safe_https(raw.get("foreign_landing_url"))
    asset_url = _safe_https(raw.get("url"))
    license_url = _safe_https(raw.get("license_url"))
    review_flags = [
        "source_page_license_must_be_verified",
        "personality_rights_must_be_reviewed",
        "visual_relevance_must_be_reviewed",
    ]
    if not creator:
        review_flags.append("missing_creator")
    if landing_url is None:
        review_flags.append("missing_landing_url")
    if asset_url is None:
        review_flags.append("missing_asset_url")
    if license_url is None:
        review_flags.append("missing_license_url")
    if not isinstance(license_version, str) or not license_version:
        review_flags.append("missing_license_version")
    if not attribution:
        review_flags.append("missing_attribution")
    if not provider:
        review_flags.append("missing_provider")
    if not source:
        review_flags.append("missing_source")
    if width is None or height is None:
        review_flags.append("missing_dimensions")
    if not filetype:
        review_flags.append("missing_filetype")

    return {
        "inventory_version": INVENTORY_VERSION,
        "catalog": "openverse",
        "candidate_id": f"openverse:{identifier}",
        "retrieved_at": retrieved_at,
        "query": query,
        "openverse_id": identifier,
        "review_status": "needs_review",
        "review_flags": review_flags,
        "title": raw.get("title") if isinstance(raw.get("title"), str) else None,
        "creator": creator,
        "creator_url": _safe_https(raw.get("creator_url")),
        "license_id": license_id,
        "license_version": license_version,
        "license_url": license_url,
        "attribution": attribution,
        "provider": provider,
        "source": source,
        "landing_url": landing_url,
        "asset_url": asset_url,
        "width": width,
        "height": height,
        "filetype": filetype,
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
    seen_ids: set[str] = set()
    seen_landings: set[str] = set()
    requested_pages = 0
    skipped_duplicates = 0
    skipped_ineligible = 0

    for query in normalized_queries:
        for page in range(1, max_pages + 1):
            payload = _fetch_page(query, page, page_size)
            requested_pages += 1
            raw_results = payload["results"]
            for raw in raw_results:
                if not isinstance(raw, dict):
                    skipped_ineligible += 1
                    continue
                item = _candidate(query, raw, retrieved_at)
                if item is None:
                    skipped_ineligible += 1
                    continue
                identifier = item["openverse_id"]
                landing_url = item["landing_url"]
                if identifier in seen_ids or (landing_url is not None and landing_url in seen_landings):
                    skipped_duplicates += 1
                    continue
                seen_ids.add(identifier)
                if landing_url is not None:
                    seen_landings.add(landing_url)
                candidates.append(item)

            page_count = payload.get("page_count")
            if not raw_results or (isinstance(page_count, int) and page >= page_count):
                break

    return InventoryResult(
        candidates=tuple(candidates),
        requested_pages=requested_pages,
        skipped_duplicates=skipped_duplicates,
        skipped_ineligible=skipped_ineligible,
    )


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--query", action="append", required=True, help="metadata search query")
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
