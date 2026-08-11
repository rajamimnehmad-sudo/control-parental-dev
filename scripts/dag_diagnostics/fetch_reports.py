#!/usr/bin/env python3
"""Fetch sanitized DAG flight-recorder reports without requiring ADB."""

from __future__ import annotations

import argparse
import json
import os
import pathlib
import urllib.parse
import urllib.request


PROJECT_ROOT = pathlib.Path(__file__).resolve().parents[2]
DEFAULT_ENDPOINT = "https://syeycayasyufedwoprea.supabase.co/functions/v1/dag-diagnostic-report"


def env_value(name: str) -> str:
    value = os.environ.get(name, "").strip()
    if value:
        return value
    env_file = PROJECT_ROOT / ".env"
    if not env_file.is_file():
        return ""
    for line in env_file.read_text(encoding="utf-8").splitlines():
        if line.startswith(f"{name}="):
            return line.split("=", 1)[1].strip()
    return ""


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--code", help="DAG-XXXXXXXX report code; omit to list recent reports")
    parser.add_argument("--output", type=pathlib.Path, help="optional JSON output path")
    args = parser.parse_args()

    token = env_value("DAG_DIAGNOSTIC_READER_TOKEN")
    if len(token) < 32:
        raise SystemExit("DAG_DIAGNOSTIC_READER_TOKEN is not configured")
    endpoint = env_value("DAG_DIAGNOSTIC_UPLOAD_URL") or DEFAULT_ENDPOINT
    if args.code:
        endpoint += "?" + urllib.parse.urlencode({"code": args.code})
    request = urllib.request.Request(endpoint, headers={"X-DAG-Reader-Token": token})
    with urllib.request.urlopen(request, timeout=20) as response:
        payload = json.load(response)
    rendered = json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=True) + "\n"
    if args.output:
        args.output.write_text(rendered, encoding="utf-8")
    else:
        print(rendered, end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
