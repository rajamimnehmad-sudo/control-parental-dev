#!/usr/bin/env python3
"""Build a private, hash-bound manifest for the R2.2 Android real-image canary."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
from typing import Any


def _sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def build_manifest(manifest_path: Path, reviews_path: Path, image_root: Path) -> dict[str, Any]:
    source_rows = [json.loads(line) for line in manifest_path.read_text(encoding="utf-8").splitlines() if line.strip()]
    reviews = json.loads(reviews_path.read_text(encoding="utf-8")).get("reviews", {})
    samples = []
    for row in source_rows:
        sample_id = row["sample_id"]
        action = reviews.get(sample_id, {}).get("action")
        if action not in {"allow", "filter"}:
            continue
        source = image_root / Path(row["local_path"]).name
        if not source.is_file():
            raise FileNotFoundError(source)
        digest = _sha256(source)
        if digest != row["sha256"]:
            raise ValueError(f"sha256 mismatch for {sample_id}")
        samples.append(
            {
                "sample_id": sample_id,
                "image_name": source.name,
                "sha256": digest,
                "human_action": action,
                "mime": row["mime"],
            }
        )
    if len(samples) != 40:
        raise ValueError(f"expected 40 binary reviewed samples, found {len(samples)}")
    return {
        "schema_version": "gloshia-r2.2-real-image-canary-manifest-v1",
        "ticket": "GLOSHIA-R2.2-REVERSIBLE-CANARY-18",
        "samples": samples,
        "final_sealed_opened": False,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", required=True, type=Path)
    parser.add_argument("--reviews", required=True, type=Path)
    parser.add_argument("--image-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    payload = build_manifest(args.manifest, args.reviews, args.image_root)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(payload, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(json.dumps({"samples": len(payload["samples"]), "output": str(args.output)}, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
