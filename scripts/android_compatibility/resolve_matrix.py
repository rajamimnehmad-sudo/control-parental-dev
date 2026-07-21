#!/usr/bin/env python3
"""Resolve a small Test Lab matrix against the current device catalog."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--catalog", required=True, type=Path)
    parser.add_argument("--config", required=True, type=Path)
    parser.add_argument("--locales", type=Path)
    parser.add_argument("--kind", required=True, choices=("virtual", "physical"))
    parser.add_argument("--min-api", required=True, type=int)
    parser.add_argument("--target-api", required=True, type=int)
    return parser.parse_args()


def model_kind(model: dict) -> str:
    form = str(model.get("form", "")).lower()
    tags = {str(tag).lower() for tag in model.get("tags", [])}
    if "virtual" in form or "virtual" in tags:
        return "virtual"
    if "physical" in form or "physical" in tags:
        return "physical"
    return "unknown"


def resolve_api(role: str, supported: list[int], minimum: int, target: int) -> int | None:
    supported_range = [api for api in supported if minimum <= api <= target]
    if not supported_range:
        return None
    desired = {
        "min": minimum,
        "mid": (minimum + target) // 2,
        "target": target,
        "latest_supported": target,
    }[role]
    return min(supported_range, key=lambda api: (abs(api - desired), -api))


def main() -> int:
    args = parse_args()
    models = json.loads(args.catalog.read_text(encoding="utf-8"))
    config = json.loads(args.config.read_text(encoding="utf-8"))
    available_locales: set[str] | None = None
    if args.locales:
        locales = json.loads(args.locales.read_text(encoding="utf-8"))
        available_locales = {
            str(locale.get("id") or locale.get("locale") or "") for locale in locales
        }
    failures: list[str] = []

    for cell in config["cells"]:
        pattern = re.compile(cell.get("model_regex", ".*"), re.IGNORECASE)
        form_factor = cell.get("form_factor", "").lower()
        matches: list[tuple[dict, int]] = []
        for model in models:
            if model_kind(model) != args.kind:
                continue
            tags = {str(tag).lower() for tag in model.get("tags", [])}
            if "deprecated" in tags or "reducedcapacity" in tags:
                continue
            haystack = " ".join(
                str(model.get(key, "")) for key in ("id", "name", "manufacturer", "brand")
            )
            if not pattern.search(haystack):
                continue
            actual_form_factor = str(model.get("formFactor", "")).lower()
            if form_factor and actual_form_factor and form_factor != actual_form_factor:
                continue
            supported = [int(api) for api in model.get("supportedVersionIds", []) if str(api).isdigit()]
            api = resolve_api(cell["api"], supported, args.min_api, args.target_api)
            if api is not None:
                matches.append((model, api))

        if not matches:
            failures.append(cell["name"])
            continue
        model, api = sorted(matches, key=lambda match: str(match[0].get("id", "")))[0]
        locale = cell.get("locale", "es_AR")
        if available_locales is not None and locale not in available_locales:
            failures.append(f'{cell["name"]} (locale {locale})')
            continue
        orientation = cell.get("orientation", "portrait")
        print(f'{cell["name"]}\tmodel={model["id"]},version={api},locale={locale},orientation={orientation}')

    if failures:
        print("Sin dispositivo estable disponible para: " + ", ".join(failures), file=sys.stderr)
        return 3
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
