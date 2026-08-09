#!/usr/bin/env python3
"""Create a grouped, label-preserving thumbnail repair split for GloshIA.

Only whole-image transformations are automatic. Regional crops are deliberately
excluded because a crop can remove the visual fact that justified the parent
label and therefore requires separate human review.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import random
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any

from PIL import Image, ImageDraw, ImageFilter, ImageOps


VARIANTS = ("thumb160_q45", "thumb96_q35", "circle128_q45")
AUTOMATIC_SPLITS = {"train", "validation"}


def _variant_image(source: Image.Image, variant: str) -> Image.Image:
    image = ImageOps.exif_transpose(source).convert("RGB")
    if variant == "thumb160_q45":
        image.thumbnail((160, 160), Image.Resampling.LANCZOS)
        return image.filter(ImageFilter.GaussianBlur(radius=0.35))
    if variant == "thumb96_q35":
        image.thumbnail((96, 96), Image.Resampling.LANCZOS)
        return image
    if variant == "circle128_q45":
        image.thumbnail((128, 128), Image.Resampling.LANCZOS)
        canvas = Image.new("RGB", (128, 128), (128, 128, 128))
        left = (128 - image.width) // 2
        top = (128 - image.height) // 2
        mask = Image.new("L", image.size, 0)
        ImageDraw.Draw(mask).ellipse((0, 0, image.width - 1, image.height - 1), fill=255)
        canvas.paste(image, (left, top), mask)
        return canvas
    raise ValueError(f"unsupported variant: {variant}")


def _quality(variant: str) -> int:
    return 35 if variant == "thumb96_q35" else 45


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def _select_groups(records: list[dict[str, Any]], seed: int, max_groups_per_label: int) -> set[str]:
    by_label: dict[int, dict[str, list[dict[str, Any]]]] = defaultdict(lambda: defaultdict(list))
    for row in records:
        if row.get("split") != "train":
            continue
        group = str(row.get("group_key") or row.get("source_cluster") or row.get("series") or row["sha256"])
        by_label[int(row["target"])][group].append(row)
    selected: set[str] = set()
    for label, groups in sorted(by_label.items()):
        keys = sorted(groups)
        random.Random(seed + label).shuffle(keys)
        selected.update(keys[:max_groups_per_label])
    return selected


def _fallback_index(roots: list[Path]) -> dict[str, Path]:
    index: dict[str, Path] = {}
    duplicates: set[str] = set()
    for root in roots:
        if not root.is_dir():
            raise FileNotFoundError(root)
        for path in root.rglob("*"):
            if not path.is_file():
                continue
            if path.name in index and index[path.name] != path:
                duplicates.add(path.name)
            else:
                index[path.name] = path.resolve()
    for name in duplicates:
        index.pop(name, None)
    return index


def _resolve_sources(records: list[dict[str, Any]], fallback_roots: list[Path]) -> dict[str, tuple[Path, str]]:
    fallback = _fallback_index(fallback_roots)
    resolved: dict[str, tuple[Path, str]] = {}
    missing: list[str] = []
    for row in records:
        if row.get("split") not in AUTOMATIC_SPLITS:
            continue
        source = Path(row["image_path"])
        candidate = source.resolve() if source.is_file() else fallback.get(source.name)
        if candidate is None:
            missing.append(str(source))
            continue
        actual_hash = _sha256(candidate)
        expected_hash = str(row.get("sha256", "")).lower()
        identity_by_hash = actual_hash == expected_hash
        identity_by_content_name = len(expected_hash) == 64 and candidate.stem == expected_hash[: len(candidate.stem)]
        if not identity_by_hash and not identity_by_content_name:
            raise ValueError(f"source identity mismatch for {row['sample_id']}: {candidate.name}")
        resolved[row["sample_id"]] = (candidate, actual_hash)
    if missing:
        raise FileNotFoundError(f"{len(missing)} source images are missing; first={missing[0]}")
    return resolved


def build_thumbnail_split(
    payload: dict[str, Any],
    output_dir: Path,
    *,
    seed: int,
    max_train_groups_per_label: int,
    fallback_roots: list[Path] | None = None,
) -> dict[str, Any]:
    records = [dict(row) for row in payload["records"]]
    selected_train_groups = _select_groups(records, seed, max_train_groups_per_label)
    resolved_sources = _resolve_sources(records, fallback_roots or [])
    for row in records:
        if row.get("split") not in AUTOMATIC_SPLITS:
            continue
        resolved_path, current_hash = resolved_sources[row["sample_id"]]
        manifest_path = str(row["image_path"])
        if Path(manifest_path).resolve() != resolved_path:
            row["manifest_image_path"] = manifest_path
            row["image_path"] = str(resolved_path)
        row["current_file_sha256"] = current_hash
    output_dir.mkdir(parents=True, exist_ok=False)
    generated: list[dict[str, Any]] = []
    counts: Counter[tuple[str, str, int]] = Counter()

    for row in records:
        split = row.get("split")
        if split not in AUTOMATIC_SPLITS:
            continue
        group = str(row.get("group_key") or row.get("source_cluster") or row.get("series") or row["sha256"])
        if split == "train" and group not in selected_train_groups:
            continue
        source_path, source_file_hash = resolved_sources[row["sample_id"]]
        with Image.open(source_path) as source:
            for variant in VARIANTS:
                transformed = _variant_image(source, variant)
                content_name = hashlib.sha256(f"{row['sample_id']}:{variant}".encode()).hexdigest()[:24]
                destination = output_dir / f"{content_name}.jpg"
                transformed.save(destination, format="JPEG", quality=_quality(variant), optimize=True)
                content_hash = hashlib.sha256(destination.read_bytes()).hexdigest()
                generated_row = dict(row)
                generated_row.pop("phash64", None)
                generated_row.pop("dhash64", None)
                generated_row.update(
                    {
                        "sample_id": f"{row['sample_id']}:r4:{variant}",
                        "image_path": str(destination.resolve()),
                        "sha256": content_hash,
                        "parent_manifest_sha256": row["sha256"],
                        "parent_file_sha256": source_file_hash,
                        "parent_sample_id": row["sample_id"],
                        "augmentation_family": "r4_thumbnail_repair_v1",
                        "augmentation_variant": variant,
                        "group_key": group,
                        "source_cluster": group,
                    }
                )
                generated.append(generated_row)
                counts[(split, variant, int(row["target"]))] += 1

    combined = [*records, *generated]
    group_splits: dict[str, set[str]] = defaultdict(set)
    for row in combined:
        group = str(row.get("group_key") or row.get("source_cluster") or row.get("series") or row["sha256"])
        group_splits[group].add(str(row["split"]))
    crossing = {group: sorted(splits) for group, splits in group_splits.items() if len(splits) > 1}
    if crossing:
        raise ValueError(f"group contamination detected: {crossing}")

    return {
        "schema_version": "gloshia-r4-thumbnail-repair-split-v1",
        "status": "research_only_not_approved_for_apk",
        "seed": seed,
        "source_schema_version": payload.get("schema_version"),
        "automatic_label_policy": "whole_image_transformations_only; regional_crops_require_human_review",
        "variants": list(VARIANTS),
        "selected_train_groups": len(selected_train_groups),
        "generated_rows": len(generated),
        "generated_counts": {
            f"{split}:{variant}:{label}": value
            for (split, variant, label), value in sorted(counts.items())
        },
        "source_file_hash_drift": {
            "count": sum(
                source_file_hash != str(row["sha256"]).lower()
                for row in records
                if row.get("split") in AUTOMATIC_SPLITS
                for _, source_file_hash in [resolved_sources[row["sample_id"]]]
            ),
            "interpretation": "manifest identity is preserved by the content-addressed filename; current file bytes are recorded separately",
        },
        "group_contamination": {"passed": True, "crossing_groups": {}},
        "frozen_test_augmented": False,
        "final_sealed_opened": False,
        "records": sorted(combined, key=lambda row: (row["split"], row["sample_id"])),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-split", required=True, type=Path)
    parser.add_argument("--output-dir", required=True, type=Path)
    parser.add_argument("--output-split", required=True, type=Path)
    parser.add_argument("--seed", type=int, default=4101)
    parser.add_argument("--max-train-groups-per-label", type=int, default=60)
    parser.add_argument("--fallback-root", action="append", type=Path, default=[])
    args = parser.parse_args()
    payload = json.loads(args.source_split.read_text(encoding="utf-8"))
    result = build_thumbnail_split(
        payload,
        args.output_dir,
        seed=args.seed,
        max_train_groups_per_label=args.max_train_groups_per_label,
        fallback_roots=args.fallback_root,
    )
    args.output_split.parent.mkdir(parents=True, exist_ok=True)
    args.output_split.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps({key: result[key] for key in ("generated_rows", "selected_train_groups", "frozen_test_augmented")}))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
