#!/usr/bin/env python3
"""DAG v2 04B: blind review, targeted CPU signals, and sealed policy evaluation."""

from __future__ import annotations

import argparse
import base64
import concurrent.futures
import datetime as dt
import hashlib
import json
import math
import os
import random
import shutil
import statistics
import sys
import tempfile
import time
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any, Iterable

TOOL_DIR = Path(__file__).resolve().parent
ROOT = TOOL_DIR.parents[1]
EVIDENCE_04A = TOOL_DIR / "evidence/04a"
EVIDENCE_04B = TOOL_DIR / "evidence/04b"
CORPUS_LOCK = EVIDENCE_04A / "corpus.lock.jsonl"
REVIEW_ORDER_LOCK = EVIDENCE_04B / "review-order.lock.jsonl"
SPLIT_LOCK = EVIDENCE_04B / "split.lock.jsonl"
DIAGNOSTIC_LOCK = EVIDENCE_04B / "diagnostic-subset.lock.jsonl"
LABEL_SCHEMA = EVIDENCE_04B / "label-schema.json"
PLAN_LOCK = EVIDENCE_04B / "plan.lock.json"
RESULT_CHECKSUMS = EVIDENCE_04B / "results.checksums.json"
RAW_LABELS = EVIDENCE_04B / "human-labels.raw.jsonl"
NORMALIZED_LABELS = EVIDENCE_04B / "human-labels.normalized.jsonl"
MAC_SIGNALS = EVIDENCE_04B / "signals.mac.jsonl"
MAC_SIGNAL_SUMMARY = EVIDENCE_04B / "signals.mac.summary.json"
TEACHER_EVIDENCE = EVIDENCE_04B / "teacher.jsonl"
TEACHER_SUMMARY = EVIDENCE_04B / "teacher.summary.json"
POLICY_SEAL = EVIDENCE_04B / "policy-seal.json"
FROZEN_TEST = EVIDENCE_04B / "frozen-test.json"
ANDROID_EVIDENCE = EVIDENCE_04B / "android-sm-a235m.json"
RESULT_SUMMARY = EVIDENCE_04B / "result-summary.json"
POLICY_VERSION = "DAG_STRICT_MODESTY_V1"
REVIEWER_VERSION = "dag-v2-policy-reviewer-04b-1"
PLAN_VERSION = "dag-v2-04b-plan-1"
SPLIT_SEED = "dag-v2-04b-split-v1"
ORDER_SEED = "dag-v2-04b-blind-order-v1"
DIAGNOSTIC_SEED = "dag-v2-04b-diagnostic-v1"
MAX_NEAR_DUPLICATE_DISTANCE = 5
SPLIT_TARGETS = {"exploratory": 122, "validation": 41, "test": 40}
DECISIONS = {"show", "hide", "unsure"}
REASONS = (
    "adult_or_explicit",
    "underwear_or_swimwear",
    "deep_neckline_or_chest",
    "abdomen",
    "shoulder_or_armpit",
    "elbow",
    "knee",
    "tight_clothing",
    "transparency",
    "age_uncertain",
    "groups",
    "other",
)
DIAGNOSTIC_CATEGORY_PRIORITY = {
    "adult_or_explicit": ("nudity_art", "underwear", "drawings"),
    "underwear_or_swimwear": ("underwear", "female_fashion_broad", "store"),
    "deep_neckline_or_chest": ("neckline", "women_dresses_broad", "female_fashion_broad"),
    "abdomen": ("abdomen", "tight", "female_sports", "women_unspecified"),
    "shoulder_or_armpit": ("shoulders", "female_fashion_broad"),
    "elbow": ("elbows", "women_shirts", "women_permitted", "women_unspecified"),
    "knee": ("knees", "female_sports", "children_playing"),
    "tight_clothing": ("tight", "female_sports", "female_fashion_broad"),
    "transparency": ("transparent", "female_fashion_broad", "store"),
    "age_uncertain": ("children_ordinary", "children_playing", "women_unspecified"),
    "groups": ("groups", "groups_general", "women_events"),
    "other": ("no_person", "drawings", "generated", "clothing_products"),
}
EXPORT_ALLOWED_KEYS = {
    "sample_id",
    "decision",
    "reasons",
    "review_number",
    "reviewed_at",
    "policy_version",
    "reviewer_version",
}
SIGNAL_FEATURES = (
    "adult_score",
    "person_count",
    "pose_confidence",
    "shoulder_skin_ycbcr",
    "shoulder_skin_hsv",
    "shoulder_skin_lab",
    "elbow_skin_ycbcr",
    "elbow_skin_hsv",
    "elbow_skin_lab",
    "knee_skin_ycbcr",
    "knee_skin_hsv",
    "knee_skin_lab",
    "torso_skin_ycbcr",
    "torso_skin_hsv",
    "torso_skin_lab",
    "face_body_skin_ratio",
    "relative_face_skin_ratio",
    "blur_score",
    "quality_score",
    "signal_uncertainty",
)


def read_json(path: Path) -> dict[str, Any]:
    with path.open("r", encoding="utf-8") as source:
        return json.load(source)


def read_jsonl(path: Path) -> list[dict[str, Any]]:
    with path.open("r", encoding="utf-8") as source:
        return [json.loads(line) for line in source if line.strip()]


def write_json(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    os.replace(temporary, path)


def write_jsonl(path: Path, values: Iterable[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(
        "".join(json.dumps(item, sort_keys=True) + "\n" for item in values),
        encoding="utf-8",
    )
    os.replace(temporary, path)


def sha256_file(path: Path) -> tuple[str, int]:
    digest = hashlib.sha256()
    size = 0
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
            size += len(chunk)
    return digest.hexdigest(), size


def stable_key(seed: str, value: str) -> str:
    return hashlib.sha256(f"{seed}:{value}".encode("utf-8")).hexdigest()


def hamming64(first: str, second: str) -> int:
    return bin(int(first, 16) ^ int(second, 16)).count("1")


class UnionFind:
    def __init__(self, values: Iterable[str]) -> None:
        self.parent = {value: value for value in values}

    def find(self, value: str) -> str:
        parent = self.parent[value]
        if parent != value:
            self.parent[value] = self.find(parent)
        return self.parent[value]

    def union(self, first: str, second: str) -> None:
        left = self.find(first)
        right = self.find(second)
        if left != right:
            self.parent[max(left, right)] = min(left, right)


def perceptual_clusters(corpus: list[dict[str, Any]]) -> dict[str, str]:
    union = UnionFind(item["sample_id"] for item in corpus)
    for index, first in enumerate(corpus):
        for second in corpus[index + 1 :]:
            if (
                first["sha256"] == second["sha256"]
                or hamming64(first["perceptual_hash64"], second["perceptual_hash64"])
                <= MAX_NEAR_DUPLICATE_DISTANCE
            ):
                union.union(first["sample_id"], second["sample_id"])
    grouped: dict[str, list[str]] = defaultdict(list)
    for item in corpus:
        grouped[union.find(item["sample_id"])].append(item["sample_id"])
    result: dict[str, str] = {}
    for members in grouped.values():
        cluster_id = "cluster-" + hashlib.sha256("|".join(sorted(members)).encode()).hexdigest()[:16]
        for sample_id in members:
            result[sample_id] = cluster_id
    return result


def assign_splits(
    corpus: list[dict[str, Any]],
    cluster_by_sample: dict[str, str],
) -> dict[str, str]:
    by_cluster: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for item in corpus:
        by_cluster[cluster_by_sample[item["sample_id"]]].append(item)
    assignments: dict[str, str] = {}
    split_counts = Counter()
    category_counts: dict[str, Counter[str]] = defaultdict(Counter)
    clusters = sorted(
        by_cluster.items(),
        key=lambda pair: (-len(pair[1]), stable_key(SPLIT_SEED, pair[0])),
    )
    ratios = {name: target / len(corpus) for name, target in SPLIT_TARGETS.items()}
    for cluster_id, members in clusters:
        categories = Counter(item["category"] for item in members)
        choices = []
        for split, target in SPLIT_TARGETS.items():
            overflow = max(0, split_counts[split] + len(members) - target)
            global_pressure = (split_counts[split] + len(members)) / target
            category_pressure = statistics.fmean(
                (
                    category_counts[split][category] + count
                )
                / max(1.0, sum(1 for item in corpus if item["category"] == category) * ratios[split])
                for category, count in categories.items()
            )
            choices.append(
                (
                    overflow > 0,
                    overflow,
                    global_pressure + category_pressure,
                    stable_key(SPLIT_SEED, f"{cluster_id}:{split}"),
                    split,
                )
            )
        split = min(choices)[-1]
        assignments[cluster_id] = split
        split_counts[split] += len(members)
        category_counts[split].update(categories)
    return {
        item["sample_id"]: assignments[cluster_by_sample[item["sample_id"]]]
        for item in corpus
    }


def choose_diagnostic_subset(
    corpus: list[dict[str, Any]],
    cluster_by_sample: dict[str, str],
) -> list[dict[str, Any]]:
    chosen_ids: set[str] = set()
    chosen_clusters: set[str] = set()
    output: list[dict[str, Any]] = []
    for reason in REASONS:
        priorities = DIAGNOSTIC_CATEGORY_PRIORITY[reason]
        candidates = sorted(
            (
                item
                for item in corpus
                if item["category"] in priorities
                and item["sample_id"] not in chosen_ids
                and cluster_by_sample[item["sample_id"]] not in chosen_clusters
            ),
            key=lambda item: (
                priorities.index(item["category"]),
                stable_key(DIAGNOSTIC_SEED, f"{reason}:{item['sample_id']}"),
            ),
        )
        if len(candidates) < 5:
            fallback = sorted(
                (
                    item
                    for item in corpus
                    if item["sample_id"] not in chosen_ids
                    and cluster_by_sample[item["sample_id"]] not in chosen_clusters
                    and item not in candidates
                ),
                key=lambda item: stable_key(DIAGNOSTIC_SEED, f"fallback:{reason}:{item['sample_id']}"),
            )
            candidates.extend(fallback)
        for item in candidates[:5]:
            chosen_ids.add(item["sample_id"])
            chosen_clusters.add(cluster_by_sample[item["sample_id"]])
            output.append(
                {
                    "sample_id": item["sample_id"],
                    "diagnostic_stratum": reason,
                    "cluster_id": cluster_by_sample[item["sample_id"]],
                    "sha256": item["sha256"],
                }
            )
    if len(output) != 60:
        raise ValueError(f"diagnostic subset must contain 60 samples, got {len(output)}")
    return output


def create_review_plan() -> None:
    corpus = read_jsonl(CORPUS_LOCK)
    if len(corpus) != 203:
        raise ValueError("04B requires the exact 203-sample 04A corpus")
    cluster_by_sample = perceptual_clusters(corpus)
    split_by_sample = assign_splits(corpus, cluster_by_sample)
    diagnostic = choose_diagnostic_subset(corpus, cluster_by_sample)
    diagnostic_ids = {item["sample_id"] for item in diagnostic}
    order = sorted(corpus, key=lambda item: stable_key(ORDER_SEED, item["sample_id"]))
    review_rows = [
        {
            "position": index + 1,
            "sample_id": item["sample_id"],
            "sha256": item["sha256"],
            "relative_file": item["relative_file"],
            "diagnostic": item["sample_id"] in diagnostic_ids,
        }
        for index, item in enumerate(order)
    ]
    split_rows = [
        {
            "sample_id": item["sample_id"],
            "sha256": item["sha256"],
            "perceptual_hash64": item["perceptual_hash64"],
            "cluster_id": cluster_by_sample[item["sample_id"]],
            "source_category": item["category"],
            "split": split_by_sample[item["sample_id"]],
        }
        for item in sorted(corpus, key=lambda value: value["sample_id"])
    ]
    schema = {
        "schema_version": 1,
        "policy_version": POLICY_VERSION,
        "reviewer_version": REVIEWER_VERSION,
        "decisions": sorted(DECISIONS),
        "reasons": list(REASONS),
        "required_fields": sorted(EXPORT_ALLOWED_KEYS),
        "additional_fields_allowed": False,
        "unsure_training_role": "excluded",
    }
    EVIDENCE_04B.mkdir(parents=True, exist_ok=True)
    write_jsonl(REVIEW_ORDER_LOCK, review_rows)
    write_jsonl(SPLIT_LOCK, split_rows)
    write_jsonl(DIAGNOSTIC_LOCK, diagnostic)
    write_json(LABEL_SCHEMA, schema)
    files = [REVIEW_ORDER_LOCK, SPLIT_LOCK, DIAGNOSTIC_LOCK, LABEL_SCHEMA]
    plan = {
        "plan_version": PLAN_VERSION,
        "created_before_human_labels": True,
        "corpus_sha256": sha256_file(CORPUS_LOCK)[0],
        "sample_count": len(corpus),
        "diagnostic_count": len(diagnostic),
        "split_targets": SPLIT_TARGETS,
        "near_duplicate_hamming_max": MAX_NEAR_DUPLICATE_DISTANCE,
        "seeds": {
            "order": ORDER_SEED,
            "split": SPLIT_SEED,
            "diagnostic": DIAGNOSTIC_SEED,
        },
        "files": {
            path.name: {"sha256": sha256_file(path)[0], "size_bytes": path.stat().st_size}
            for path in files
        },
    }
    write_json(PLAN_LOCK, plan)
    print(
        f"review_plan=created samples={len(corpus)} diagnostic={len(diagnostic)} "
        f"splits={dict(Counter(split_by_sample.values()))}"
    )


def verify_review_plan() -> None:
    corpus = read_jsonl(CORPUS_LOCK)
    order = read_jsonl(REVIEW_ORDER_LOCK)
    split = read_jsonl(SPLIT_LOCK)
    diagnostic = read_jsonl(DIAGNOSTIC_LOCK)
    plan = read_json(PLAN_LOCK)
    ids = {item["sample_id"] for item in corpus}
    if len(corpus) != 203 or len(order) != 203 or len(split) != 203:
        raise ValueError("review plan must cover exactly 203 samples")
    if {item["sample_id"] for item in order} != ids:
        raise ValueError("blind order sample mismatch")
    if {item["sample_id"] for item in split} != ids:
        raise ValueError("split sample mismatch")
    if len({item["sample_id"] for item in order}) != 203:
        raise ValueError("duplicate sample in blind order")
    if [item["position"] for item in order] != list(range(1, 204)):
        raise ValueError("blind order positions are not contiguous")
    if any(key in item for item in order for key in ("category", "adult_score", "prediction")):
        raise ValueError("blind order leaks source or model context")
    split_counts = Counter(item["split"] for item in split)
    if split_counts != Counter(SPLIT_TARGETS):
        raise ValueError(f"unexpected split counts: {dict(split_counts)}")
    cluster_splits: dict[str, set[str]] = defaultdict(set)
    sha_splits: dict[str, set[str]] = defaultdict(set)
    for item in split:
        cluster_splits[item["cluster_id"]].add(item["split"])
        sha_splits[item["sha256"]].add(item["split"])
    if any(len(values) != 1 for values in cluster_splits.values()):
        raise ValueError("perceptual cluster leakage across splits")
    if any(len(values) != 1 for values in sha_splits.values()):
        raise ValueError("exact duplicate leakage across splits")
    if len(diagnostic) != 60 or len({item["sample_id"] for item in diagnostic}) != 60:
        raise ValueError("diagnostic subset must contain 60 unique samples")
    if Counter(item["diagnostic_stratum"] for item in diagnostic) != Counter({reason: 5 for reason in REASONS}):
        raise ValueError("diagnostic strata are not balanced")
    if len({item["cluster_id"] for item in diagnostic}) != 60:
        raise ValueError("diagnostic subset contains perceptual cluster overlap")
    if plan["corpus_sha256"] != sha256_file(CORPUS_LOCK)[0]:
        raise ValueError("04A corpus lock changed after 04B plan creation")
    for name, expected in plan["files"].items():
        actual_hash, actual_size = sha256_file(EVIDENCE_04B / name)
        if actual_hash != expected["sha256"] or actual_size != expected["size_bytes"]:
            raise ValueError(f"{name}: review plan integrity mismatch")
    regenerated_dir = Path(tempfile.mkdtemp(prefix="dag-v2-review-plan-"))
    try:
        original = EVIDENCE_04B
        globals()["EVIDENCE_04B"] = regenerated_dir
        globals()["REVIEW_ORDER_LOCK"] = regenerated_dir / "review-order.lock.jsonl"
        globals()["SPLIT_LOCK"] = regenerated_dir / "split.lock.jsonl"
        globals()["DIAGNOSTIC_LOCK"] = regenerated_dir / "diagnostic-subset.lock.jsonl"
        globals()["LABEL_SCHEMA"] = regenerated_dir / "label-schema.json"
        globals()["PLAN_LOCK"] = regenerated_dir / "plan.lock.json"
        create_review_plan()
        for name in ("review-order.lock.jsonl", "split.lock.jsonl", "diagnostic-subset.lock.jsonl", "label-schema.json"):
            if (regenerated_dir / name).read_bytes() != (original / name).read_bytes():
                raise ValueError(f"{name}: deterministic regeneration mismatch")
    finally:
        globals()["EVIDENCE_04B"] = original
        globals()["REVIEW_ORDER_LOCK"] = original / "review-order.lock.jsonl"
        globals()["SPLIT_LOCK"] = original / "split.lock.jsonl"
        globals()["DIAGNOSTIC_LOCK"] = original / "diagnostic-subset.lock.jsonl"
        globals()["LABEL_SCHEMA"] = original / "label-schema.json"
        globals()["PLAN_LOCK"] = original / "plan.lock.json"
        shutil.rmtree(regenerated_dir)
    print(
        f"review_plan=ok samples=203 diagnostic=60 splits={dict(split_counts)} "
        f"clusters={len(cluster_splits)}"
    )


FIXTURE_PNGS = (
    "iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAIAAAD91JpzAAAAFElEQVR42mP4z8DAwMDAxMDAwMAAAAYAAX4BpoAAAAAASUVORK5CYII=",
    "iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAIAAAD91JpzAAAAFUlEQVR42mNk+M/AAAQYGBgYGMAAAAwAAf4BpoAAAAAASUVORK5CYII=",
    "iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAIAAAD91JpzAAAAFUlEQVR42mP4zwAEGBgYGBjAAAAMAAH+AaYAAAAASUVORK5CYII=",
)


def prepare_reviewer_assets(cache: Path, output: Path, fixture: bool = False) -> None:
    if output.exists():
        shutil.rmtree(output)
    images = output / "images"
    images.mkdir(parents=True)
    if fixture:
        samples = []
        for index, encoded in enumerate(FIXTURE_PNGS):
            data = base64.b64decode(encoded)
            filename = f"fixture-{index + 1}.png"
            (images / filename).write_bytes(data)
            samples.append(
                {
                    "position": index + 1,
                    "sample_id": f"fixture-{index + 1}",
                    "file": filename,
                    "sha256": hashlib.sha256(data).hexdigest(),
                    "diagnostic": index == 0,
                }
            )
    else:
        verify_review_plan()
        corpus = {item["sample_id"]: item for item in read_jsonl(CORPUS_LOCK)}
        samples = []
        for item in read_jsonl(REVIEW_ORDER_LOCK):
            locked = corpus[item["sample_id"]]
            source = cache / "corpus" / locked["relative_file"]
            actual_hash, actual_size = sha256_file(source)
            if actual_hash != locked["sha256"] or actual_size != int(locked["size_bytes"]):
                raise ValueError(f"{item['sample_id']}: corpus cache integrity mismatch")
            target = images / locked["relative_file"]
            try:
                os.link(source, target)
            except OSError:
                shutil.copyfile(source, target)
            samples.append(
                {
                    "position": item["position"],
                    "sample_id": item["sample_id"],
                    "file": locked["relative_file"],
                    "sha256": locked["sha256"],
                    "diagnostic": item["diagnostic"],
                }
            )
    manifest = {
        "manifest_version": 1,
        "policy_version": POLICY_VERSION,
        "reviewer_version": REVIEWER_VERSION,
        "fixture": fixture,
        "sample_count": len(samples),
        "reasons": list(REASONS),
        "samples": samples,
    }
    write_json(output / "manifest.json", manifest)
    print(f"reviewer_assets=ok samples={len(samples)} fixture={str(fixture).lower()} output={output}")


def _normalize_export_reasons(value: Any) -> tuple[list[str], bool]:
    if isinstance(value, list):
        reasons = value
        legacy = False
    elif isinstance(value, str) and value.startswith("[") and value.endswith("]"):
        content = value[1:-1].strip()
        reasons = [] if not content else [item.strip() for item in content.split(",")]
        legacy = True
    else:
        raise ValueError("invalid reasons")
    if (
        any(not isinstance(reason, str) or reason not in REASONS for reason in reasons)
        or len(set(reasons)) != len(reasons)
    ):
        raise ValueError("invalid reasons")
    return reasons, legacy


def validate_label_export(path: Path, require_complete: bool = True) -> list[dict[str, Any]]:
    raw_labels = read_jsonl(path)
    labels: list[dict[str, Any]] = []
    current: dict[str, dict[str, Any]] = {}
    expected_ids = {item["sample_id"] for item in read_jsonl(REVIEW_ORDER_LOCK)}
    legacy_reason_strings = 0
    for raw_item in raw_labels:
        item = dict(raw_item)
        unexpected = set(item) - EXPORT_ALLOWED_KEYS
        missing = EXPORT_ALLOWED_KEYS - set(item)
        if unexpected or missing:
            raise ValueError(f"label schema mismatch: unexpected={sorted(unexpected)} missing={sorted(missing)}")
        if item["sample_id"] not in expected_ids:
            raise ValueError("unknown sample_id in label export")
        if item["decision"] not in DECISIONS:
            raise ValueError("invalid human decision")
        item["reasons"], legacy = _normalize_export_reasons(item["reasons"])
        legacy_reason_strings += int(legacy)
        if not isinstance(item["review_number"], int) or item["review_number"] < 1:
            raise ValueError("invalid review number")
        if item["policy_version"] != POLICY_VERSION or item["reviewer_version"] != REVIEWER_VERSION:
            raise ValueError("label version mismatch")
        if item["sample_id"] in current:
            raise ValueError("export contains more than one current label per sample")
        current[item["sample_id"]] = item
        labels.append(item)
    if require_complete and set(current) != expected_ids:
        raise ValueError(f"human review incomplete: {len(expected_ids) - len(current)} decisions pending")
    if any(item["decision"] == "unsure" and item.get("training_target") for item in labels):
        raise ValueError("unsure cannot become a training target")
    digest, size = sha256_file(path)
    print(
        f"label_export=ok labels={len(labels)} complete={set(current) == expected_ids} "
        f"sha256={digest} size={size} legacy_reason_strings={legacy_reason_strings}"
    )
    return labels


def normalize_label_export(source: Path, output: Path) -> None:
    if output.exists():
        raise ValueError("normalized label export already exists")
    labels = validate_label_export(source)
    write_jsonl(output, labels)
    digest, size = sha256_file(output)
    print(
        f"label_export_normalized=ok labels={len(labels)} sha256={digest} size={size} "
        f"source_sha256={sha256_file(source)[0]}"
    )


def _selected_scorer(selected: dict[str, Any]) -> Any:
    model = selected["model"]
    if selected["name"] == "deterministic_rules":
        return lambda values: max(values[0], values[3], values[6], values[9], values[12])
    if selected["name"] == "logistic_regression":
        return lambda values: _logistic_score(model, values)
    if selected["name"] == "small_tree_depth_3":
        return lambda values: _tree_score(model, values)
    if selected["name"] == "bounded_stump_boost":
        return lambda values: _boost_score(model, values)
    raise ValueError("unknown selected policy")


def _frozen_test_rows(
    labels: list[dict[str, Any]],
    signals: dict[str, dict[str, Any]],
    splits: dict[str, dict[str, Any]],
) -> list[dict[str, Any]]:
    rows = []
    for label in labels:
        if label["decision"] == "unsure" or splits[label["sample_id"]]["split"] != "test":
            continue
        rows.append(
            {
                **signals[label["sample_id"]],
                "decision": label["decision"],
                "reasons": label["reasons"],
                "category": splits[label["sample_id"]]["source_category"],
                "cluster_id": splits[label["sample_id"]]["cluster_id"],
            }
        )
    return rows


def verify_04b_results() -> None:
    checksums = read_json(RESULT_CHECKSUMS)
    if checksums["bundle_version"] != "dag-v2-04b-results-1":
        raise ValueError("unsupported 04B results bundle")
    expected_names = set(checksums["files"])
    actual_names = {
        path.name
        for path in EVIDENCE_04B.iterdir()
        if path.is_file() and path.name not in {
            "diagnostic-subset.lock.jsonl",
            "label-schema.json",
            "plan.lock.json",
            "review-order.lock.jsonl",
            "split.lock.jsonl",
            RESULT_CHECKSUMS.name,
        }
    }
    if actual_names != expected_names:
        raise ValueError(
            f"04B result file set mismatch: missing={sorted(expected_names - actual_names)} "
            f"additional={sorted(actual_names - expected_names)}"
        )
    for name, expected in checksums["files"].items():
        actual_hash, actual_size = sha256_file(EVIDENCE_04B / name)
        if actual_hash != expected["sha256"] or actual_size != expected["size_bytes"]:
            raise ValueError(f"{name}: result integrity mismatch")

    raw = validate_label_export(RAW_LABELS)
    normalized = validate_label_export(NORMALIZED_LABELS)
    if raw != normalized:
        raise ValueError("normalized labels do not preserve the human export")
    ids = {item["sample_id"] for item in normalized}
    if len(ids) != 203:
        raise ValueError("04B result labels must contain 203 unique samples")
    signals_list = read_jsonl(MAC_SIGNALS)
    if len(signals_list) != 203 or {item["sample_id"] for item in signals_list} != ids:
        raise ValueError("04B signal sample set does not match human labels")
    signal_summary = read_json(MAC_SIGNAL_SUMMARY)
    if signal_summary["sample_count"] != 203:
        raise ValueError("unexpected Mac signal count")
    for stage in (
        "adult",
        "pose",
        "local_signals",
        "policy",
        "sequential",
        "adult_pose_parallel",
    ):
        if signal_stage_stats(signals_list, stage) != signal_summary["stages"][stage]:
            raise ValueError(f"{stage}: Mac latency summary mismatch")

    seal = read_json(POLICY_SEAL)
    if (
        seal["labels_sha256"] != sha256_file(NORMALIZED_LABELS)[0]
        or seal["signals_sha256"] != sha256_file(MAC_SIGNALS)[0]
        or seal["split_sha256"] != sha256_file(SPLIT_LOCK)[0]
    ):
        raise ValueError("sealed policy inputs do not match result bundle")
    selected = seal["selected"]
    frozen = read_json(FROZEN_TEST)
    expected_parameters = hashlib.sha256(
        json.dumps(selected, sort_keys=True).encode("utf-8")
    ).hexdigest()
    if frozen["parameters_sha256"] != expected_parameters or not frozen["opened_once"]:
        raise ValueError("frozen test does not match sealed parameters")
    signals = {item["sample_id"]: item for item in signals_list}
    splits = {item["sample_id"]: item for item in read_jsonl(SPLIT_LOCK)}
    test_rows = _frozen_test_rows(normalized, signals, splits)
    thresholds = selected["thresholds"]
    recomputed = _metrics(
        test_rows,
        _selected_scorer(selected),
        thresholds["show_max"],
        thresholds["hide_min"],
    )
    if recomputed != frozen["test"]:
        raise ValueError("frozen test metrics are not reproducible")

    result = read_json(RESULT_SUMMARY)
    counts = Counter(item["decision"] for item in normalized)
    reasons = Counter(reason for item in normalized for reason in item["reasons"])
    if result["human_labels"]["counts"] != dict(sorted(counts.items())):
        raise ValueError("human decision counts mismatch")
    if result["human_labels"]["reason_counts"] != dict(sorted(reasons.items())):
        raise ValueError("human reason counts mismatch")
    if result["decision"] != "NO-GO":
        raise ValueError("04B result must remain NO-GO")
    expected_test_summary = {
        key: frozen["test"][key]
        for key in (
            "sample_count",
            "show_precision",
            "hide_recall",
            "hide_recall_95ci",
            "critical_false_allows",
            "false_blocks",
            "coverage",
            "uncertainty_percent",
            "heavy_segmentation_needed_percent",
        )
    }
    if result["frozen_test"] != expected_test_summary:
        raise ValueError("result summary does not match frozen test")
    android = read_json(ANDROID_EVIDENCE)
    parallel = android["stages"]["adult_pose_parallel"]
    performance_pass = parallel["p50_ms"] <= 350.0 and parallel["p95_ms"] <= 600.0
    if performance_pass != android["performance_gate_passed"]:
        raise ValueError("Android performance gate calculation mismatch")
    if android["samples"] != 72 or android["failures"] != 0:
        raise ValueError("unexpected Android physical result")
    print(
        "04b_results=ok samples=203 decisions="
        f"{dict(sorted(counts.items()))} test_opened_once=true decision=NO-GO "
        f"files={len(expected_names)}"
    )


def percentile(values: list[float], fraction: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    index = max(0, min(len(ordered) - 1, math.ceil(len(ordered) * fraction) - 1))
    return ordered[index]


def signal_stage_stats(records: list[dict[str, Any]], name: str) -> dict[str, float]:
    values = [float(item["latency_ms"][name]) for item in records]
    return {
        "p50_ms": percentile(values, 0.50),
        "p95_ms": percentile(values, 0.95),
        "max_ms": max(values, default=0.0),
    }


def _skin_masks(rgb: Any, np: Any) -> dict[str, Any]:
    pixels = rgb.astype(np.float32)
    red, green, blue = pixels[..., 0], pixels[..., 1], pixels[..., 2]
    y = 0.299 * red + 0.587 * green + 0.114 * blue
    cb = 128.0 - 0.168736 * red - 0.331264 * green + 0.5 * blue
    cr = 128.0 + 0.5 * red - 0.418688 * green - 0.081312 * blue
    ycbcr = (y > 35) & (cb >= 77) & (cb <= 127) & (cr >= 133) & (cr <= 173)
    maximum = pixels.max(axis=2)
    minimum = pixels.min(axis=2)
    delta = maximum - minimum
    saturation = np.zeros_like(maximum)
    np.divide(delta, maximum, out=saturation, where=maximum > 0)
    hue = np.zeros_like(maximum)
    mask = delta > 0
    red_max = mask & (maximum == red)
    green_max = mask & (maximum == green)
    blue_max = mask & (maximum == blue)
    hue[red_max] = ((green[red_max] - blue[red_max]) / delta[red_max]) % 6
    hue[green_max] = (blue[green_max] - red[green_max]) / delta[green_max] + 2
    hue[blue_max] = (red[blue_max] - green[blue_max]) / delta[blue_max] + 4
    hue *= 60
    hsv = (((hue <= 50) | (hue >= 340)) & (saturation >= 0.12) & (saturation <= 0.75) & (maximum >= 50))
    normalized = pixels / 255.0
    linear = np.where(
        normalized <= 0.04045,
        normalized / 12.92,
        ((normalized + 0.055) / 1.055) ** 2.4,
    )
    x = linear[..., 0] * 0.4124 + linear[..., 1] * 0.3576 + linear[..., 2] * 0.1805
    y_lab = linear[..., 0] * 0.2126 + linear[..., 1] * 0.7152 + linear[..., 2] * 0.0722
    z = linear[..., 0] * 0.0193 + linear[..., 1] * 0.1192 + linear[..., 2] * 0.9505
    xyz = np.stack((x / 0.95047, y_lab, z / 1.08883), axis=2)
    fxyz = np.where(xyz > 0.008856, np.cbrt(xyz), (7.787 * xyz) + (16 / 116))
    light = (116 * fxyz[..., 1]) - 16
    a = 500 * (fxyz[..., 0] - fxyz[..., 1])
    b = 200 * (fxyz[..., 1] - fxyz[..., 2])
    lab = (light > 20) & (light < 95) & (a > 5) & (a < 35) & (b > 5) & (b < 45)
    return {"ycbcr": ycbcr, "hsv": hsv, "lab": lab, "lab_values": (light, a, b)}


def _patch_ratio(mask: Any, x: float, y: float, radius: int) -> float:
    height, width = mask.shape
    center_x = int(max(0, min(width - 1, x * width)))
    center_y = int(max(0, min(height - 1, y * height)))
    left, right = max(0, center_x - radius), min(width, center_x + radius + 1)
    top, bottom = max(0, center_y - radius), min(height, center_y + radius + 1)
    patch = mask[top:bottom, left:right]
    return float(patch.mean()) if patch.size else 0.0


def _region_ratio(mask: Any, points: list[tuple[float, float]]) -> float:
    if not points:
        return 0.0
    height, width = mask.shape
    xs = [point[0] for point in points]
    ys = [point[1] for point in points]
    left = max(0, min(width - 1, int(min(xs) * width)))
    right = max(left + 1, min(width, int(max(xs) * width) + 1))
    top = max(0, min(height - 1, int(min(ys) * height)))
    bottom = max(top + 1, min(height, int(max(ys) * height) + 1))
    region = mask[top:bottom, left:right]
    return float(region.mean()) if region.size else 0.0


def extract_targeted_signals(cache: Path, output: Path, limit: int | None = None) -> None:
    from PIL import Image, ImageOps
    import mediapipe as mp
    import numpy as np
    import onnxruntime as ort
    import psutil
    from mediapipe.tasks import python
    from mediapipe.tasks.python import vision

    corpus = read_jsonl(CORPUS_LOCK)
    if limit is not None:
        corpus = corpus[:limit]
    models = read_json(TOOL_DIR / "models.lock.json")["models"]
    by_id = {item["id"]: item for item in models}
    adult_model = cache / "models" / by_id["marqo-nsfw-vit-tiny-384-dag-v1-reference"]["filename"]
    pose_model = cache / "models" / by_id["mediapipe-pose-landmarker-lite-float16-1"]["filename"]
    for model, item in (
        (adult_model, by_id["marqo-nsfw-vit-tiny-384-dag-v1-reference"]),
        (pose_model, by_id["mediapipe-pose-landmarker-lite-float16-1"]),
    ):
        if sha256_file(model) != (item["sha256"], int(item["size_bytes"])):
            raise ValueError(f"{item['id']}: model integrity mismatch")
    session = ort.InferenceSession(str(adult_model), providers=["CPUExecutionProvider"])
    pose = vision.PoseLandmarker.create_from_options(
        vision.PoseLandmarkerOptions(
            base_options=python.BaseOptions(model_asset_path=str(pose_model)),
            running_mode=vision.RunningMode.IMAGE,
            num_poses=4,
            min_pose_detection_confidence=0.25,
            min_pose_presence_confidence=0.25,
        )
    )
    process = psutil.Process()
    records: list[dict[str, Any]] = []

    def adult_score(rgb: Any) -> float:
        height, width = rgb.shape[:2]
        side = min(height, width)
        top, left = (height - side) // 2, (width - side) // 2
        image = Image.fromarray(rgb[top : top + side, left : left + side]).resize((384, 384))
        values = np.asarray(image, dtype=np.float32) / 127.5 - 1.0
        logits = session.run(None, {"pixel_values": np.transpose(values, (2, 0, 1))[None, ...]})[0][0]
        shifted = np.exp(logits - np.max(logits))
        return float(shifted[0] / shifted.sum())

    def pose_result(rgb: Any) -> Any:
        return pose.detect(mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb))

    started_total = time.perf_counter()
    for item in corpus:
        image_path = cache / "corpus" / item["relative_file"]
        if sha256_file(image_path)[0] != item["sha256"]:
            raise ValueError(f"{item['sample_id']}: corpus hash mismatch")
        with Image.open(image_path) as opened:
            rgb = np.asarray(ImageOps.exif_transpose(opened).convert("RGB"), dtype=np.uint8)
        started = time.perf_counter()
        adult = adult_score(rgb)
        adult_ms = (time.perf_counter() - started) * 1000
        started = time.perf_counter()
        poses = pose_result(rgb)
        pose_ms = (time.perf_counter() - started) * 1000
        started = time.perf_counter()
        masks = _skin_masks(rgb, np)
        landmarks = poses.pose_landmarks
        height, width = rgb.shape[:2]
        radius = max(3, int(min(width, height) * 0.035))
        values: dict[str, list[float]] = defaultdict(list)
        confidence_values: list[float] = []
        coordinate_output: list[dict[str, Any]] = []
        index_groups = {
            "shoulder": (11, 12),
            "elbow": (13, 14),
            "hip": (23, 24),
            "knee": (25, 26),
        }
        for person_index, person in enumerate(landmarks):
            coords: dict[str, list[dict[str, float]]] = {}
            for name, indexes in index_groups.items():
                coords[name] = []
                for landmark_index in indexes:
                    landmark = person[landmark_index]
                    confidence = min(
                        float(getattr(landmark, "visibility", 0.0)),
                        float(getattr(landmark, "presence", 0.0)),
                    )
                    confidence_values.append(confidence)
                    coords[name].append(
                        {
                            "x": round(float(landmark.x), 6),
                            "y": round(float(landmark.y), 6),
                            "confidence": round(confidence, 6),
                        }
                    )
                    if name in ("shoulder", "elbow", "knee") and confidence >= 0.25:
                        for method in ("ycbcr", "hsv", "lab"):
                            values[f"{name}_{method}"].append(
                                _patch_ratio(masks[method], float(landmark.x), float(landmark.y), radius)
                            )
            torso_points = [
                (float(person[index].x), float(person[index].y))
                for index in (11, 12, 23, 24)
                if min(
                    float(getattr(person[index], "visibility", 0.0)),
                    float(getattr(person[index], "presence", 0.0)),
                )
                >= 0.25
            ]
            for method in ("ycbcr", "hsv", "lab"):
                values[f"torso_{method}"].append(_region_ratio(masks[method], torso_points))
            coordinate_output.append({"pose_index": person_index, "landmarks": coords})
        face_points = []
        if landmarks:
            person = landmarks[0]
            for index in (0, 2, 5, 7, 8):
                confidence = min(
                    float(getattr(person[index], "visibility", 0.0)),
                    float(getattr(person[index], "presence", 0.0)),
                )
                if confidence >= 0.25:
                    face_points.append((float(person[index].x), float(person[index].y)))
        face_skin = statistics.fmean(
            [_patch_ratio(masks["ycbcr"], x, y, radius) for x, y in face_points]
        ) if face_points else 0.0
        body_skin = statistics.fmean(
            values["torso_ycbcr"]
            + values["shoulder_ycbcr"]
            + values["elbow_ycbcr"]
            + values["knee_ycbcr"]
        ) if any(values[key] for key in (
            "torso_ycbcr", "shoulder_ycbcr", "elbow_ycbcr", "knee_ycbcr"
        )) else 0.0
        detector_values = [
            statistics.fmean(values[key]) if values[key] else 0.0
            for key in (
                "shoulder_ycbcr", "shoulder_hsv", "shoulder_lab",
                "elbow_ycbcr", "elbow_hsv", "elbow_lab",
                "knee_ycbcr", "knee_hsv", "knee_lab",
                "torso_ycbcr", "torso_hsv", "torso_lab",
            )
        ]
        relative_face = max(0.0, min(1.0, body_skin / max(0.05, face_skin))) if face_skin else 0.0
        gray = rgb.astype(np.float32).mean(axis=2)
        laplacian = (
            -4 * gray[1:-1, 1:-1]
            + gray[:-2, 1:-1]
            + gray[2:, 1:-1]
            + gray[1:-1, :-2]
            + gray[1:-1, 2:]
        )
        blur_score = float(laplacian.var()) if laplacian.size else 0.0
        quality_score = min(1.0, min(width, height) / 512.0) * min(1.0, math.log1p(blur_score) / 6.0)
        pose_confidence = statistics.fmean(confidence_values) if confidence_values else 0.0
        disagreement = statistics.pstdev(detector_values) if len(detector_values) > 1 else 0.0
        local_ms = (time.perf_counter() - started) * 1000
        started = time.perf_counter()
        policy_values = [
            adult,
            min(1.0, len(landmarks) / 4.0),
            pose_confidence,
            *detector_values,
            relative_face,
            quality_score,
            disagreement,
        ]
        policy_upper_bound_score = -0.4
        for index in range(12):
            value = policy_values[index % len(policy_values)]
            policy_upper_bound_score += 0.08 if value > (0.1 + (index % 5) * 0.15) else -0.02
        policy_upper_bound_score = _sigmoid(policy_upper_bound_score)
        policy_ms = (time.perf_counter() - started) * 1000
        started = time.perf_counter()
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as executor:
            adult_future = executor.submit(adult_score, rgb)
            pose_future = executor.submit(pose_result, rgb)
            adult_future.result()
            pose_future.result()
        parallel_ms = (time.perf_counter() - started) * 1000
        record: dict[str, Any] = {
            "sample_id": item["sample_id"],
            "adult_score": adult,
            "person_count": len(landmarks),
            "pose_confidence": pose_confidence,
            "pose_landmarks": coordinate_output,
            "face_body_skin_ratio": body_skin / max(0.05, face_skin) if face_skin else 0.0,
            "relative_face_skin_ratio": relative_face,
            "blur_score": blur_score,
            "quality_score": quality_score,
            "signal_uncertainty": max(0.0, min(1.0, (1.0 - pose_confidence) * 0.6 + disagreement * 0.4)),
            "policy_upper_bound_score": policy_upper_bound_score,
            "latency_ms": {
                "adult": adult_ms,
                "pose": pose_ms,
                "local_signals": local_ms,
                "policy": policy_ms,
                "sequential": adult_ms + pose_ms + local_ms + policy_ms,
                "adult_pose_parallel": parallel_ms + local_ms + policy_ms,
            },
            "runtime": "CPU",
            "model_versions": [
                "marqo-nsfw-vit-tiny-384-dag-v1-reference",
                "mediapipe-pose-landmarker-lite-float16-1",
            ],
        }
        for name in ("shoulder", "elbow", "knee", "torso"):
            for method in ("ycbcr", "hsv", "lab"):
                key = f"{name}_skin_{method}" if name != "torso" else f"torso_skin_{method}"
                source_key = f"{name}_{method}"
                record[key] = statistics.fmean(values[source_key]) if values[source_key] else 0.0
        records.append(record)
    pose.close()
    write_jsonl(output, records)
    summary = {
        "sample_count": len(records),
        "stages": {
            name: signal_stage_stats(records, name)
            for name in ("adult", "pose", "local_signals", "policy", "sequential", "adult_pose_parallel")
        },
        "wall_ms": (time.perf_counter() - started_total) * 1000,
        "peak_rss_bytes": process.memory_info().rss,
        "backend": "CPU",
        "heavy_segmentation_percent": 0.0,
    }
    write_json(output.with_suffix(".summary.json"), summary)
    print(json.dumps(summary, sort_keys=True))


def compare_segmentation_teacher(
    cache: Path,
    signals_path: Path,
    output: Path,
    limit: int,
) -> None:
    if limit < 1 or limit > 20:
        raise ValueError("offline teacher is limited to at most 20/203 samples")
    from PIL import Image, ImageOps
    import mediapipe as mp
    import numpy as np
    from mediapipe.tasks import python
    from mediapipe.tasks.python import vision

    corpus = {item["sample_id"]: item for item in read_jsonl(CORPUS_LOCK)}
    signals = {item["sample_id"]: item for item in read_jsonl(signals_path)}
    diagnostic = sorted(
        read_jsonl(DIAGNOSTIC_LOCK),
        key=lambda item: stable_key("dag-v2-04b-teacher-v1", item["sample_id"]),
    )[:limit]
    model = next(
        item
        for item in read_json(TOOL_DIR / "models.lock.json")["models"]
        if item["id"] == "mediapipe-selfie-multiclass-256-float32-1"
    )
    model_path = cache / "models" / model["filename"]
    if sha256_file(model_path) != (model["sha256"], int(model["size_bytes"])):
        raise ValueError("offline teacher model integrity mismatch")
    segmenter = vision.ImageSegmenter.create_from_options(
        vision.ImageSegmenterOptions(
            base_options=python.BaseOptions(model_asset_path=str(model_path)),
            running_mode=vision.RunningMode.IMAGE,
            output_category_mask=True,
            output_confidence_masks=False,
        )
    )
    records = []
    for selected in diagnostic:
        sample_id = selected["sample_id"]
        if sample_id not in signals:
            raise ValueError("targeted signals must exist before teacher comparison")
        locked = corpus[sample_id]
        image_path = cache / "corpus" / locked["relative_file"]
        if sha256_file(image_path)[0] != locked["sha256"]:
            raise ValueError("teacher corpus integrity mismatch")
        with Image.open(image_path) as opened:
            rgb = np.asarray(ImageOps.exif_transpose(opened).convert("RGB"), dtype=np.uint8)
        started = time.perf_counter()
        result = segmenter.segment(mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb))
        elapsed = (time.perf_counter() - started) * 1000
        mask = result.category_mask.numpy_view()
        teacher_skin = float(np.count_nonzero((mask == 2) | (mask == 3))) / float(mask.size)
        local = statistics.fmean(
            float(signals[sample_id].get(name, 0.0))
            for name in (
                "shoulder_skin_ycbcr",
                "elbow_skin_ycbcr",
                "knee_skin_ycbcr",
                "torso_skin_ycbcr",
            )
        )
        records.append(
            {
                "sample_id": sample_id,
                "teacher_skin_ratio": teacher_skin,
                "local_skin_ratio": local,
                "absolute_difference": abs(teacher_skin - local),
                "teacher_latency_ms": elapsed,
            }
        )
    segmenter.close()
    write_jsonl(output, records)
    summary = {
        "sample_count": len(records),
        "corpus_percent": len(records) * 100.0 / 203.0,
        "role": "offline_teacher_comparison_only",
        "runtime_candidate": False,
        "absolute_difference_mean": statistics.fmean(
            item["absolute_difference"] for item in records
        ),
        "teacher_latency": {
            "p50_ms": percentile([item["teacher_latency_ms"] for item in records], 0.50),
            "p95_ms": percentile([item["teacher_latency_ms"] for item in records], 0.95),
            "max_ms": max(item["teacher_latency_ms"] for item in records),
        },
    }
    write_json(output.with_suffix(".summary.json"), summary)
    print(json.dumps(summary, sort_keys=True))


def _vector(record: dict[str, Any]) -> list[float]:
    return [float(record.get(name, 0.0)) for name in SIGNAL_FEATURES]


def _sigmoid(value: float) -> float:
    return 1.0 / (1.0 + math.exp(-max(-30.0, min(30.0, value))))


def _fit_logistic(rows: list[tuple[list[float], int]]) -> dict[str, Any]:
    width = len(SIGNAL_FEATURES)
    means = [statistics.fmean(row[0][index] for row in rows) for index in range(width)]
    scales = [
        max(1e-6, statistics.pstdev(row[0][index] for row in rows))
        for index in range(width)
    ]
    weights = [0.0] * width
    bias = 0.0
    for _ in range(400):
        gradients = [0.0] * width
        bias_gradient = 0.0
        for features, target in rows:
            normalized = [(value - means[index]) / scales[index] for index, value in enumerate(features)]
            prediction = _sigmoid(bias + sum(weight * value for weight, value in zip(weights, normalized)))
            error = prediction - target
            bias_gradient += error
            for index, value in enumerate(normalized):
                gradients[index] += error * value
        count = max(1, len(rows))
        bias -= 0.05 * bias_gradient / count
        for index in range(width):
            weights[index] -= 0.05 * (gradients[index] / count + 0.01 * weights[index])
    return {"weights": weights, "bias": bias, "means": means, "scales": scales}


def _logistic_score(model: dict[str, Any], features: list[float]) -> float:
    normalized = [
        (value - model["means"][index]) / model["scales"][index]
        for index, value in enumerate(features)
    ]
    return _sigmoid(model["bias"] + sum(
        weight * value for weight, value in zip(model["weights"], normalized)
    ))


def _fit_stump_boost(rows: list[tuple[list[float], int]], rounds: int = 12) -> dict[str, Any]:
    scores = [0.0] * len(rows)
    stumps = []
    for _ in range(rounds):
        residuals = [target - _sigmoid(scores[index]) for index, (_, target) in enumerate(rows)]
        best: tuple[float, int, float, float, float] | None = None
        for feature_index in range(len(SIGNAL_FEATURES)):
            values = sorted({features[feature_index] for features, _ in rows})
            thresholds = values[1::max(1, len(values) // 12)] or values
            for threshold in thresholds[:12]:
                left_values = [
                    residuals[index]
                    for index, (features, _) in enumerate(rows)
                    if features[feature_index] <= threshold
                ]
                right_values = [
                    residuals[index]
                    for index, (features, _) in enumerate(rows)
                    if features[feature_index] > threshold
                ]
                if not left_values or not right_values:
                    continue
                left = statistics.fmean(left_values)
                right = statistics.fmean(right_values)
                error = sum(
                    (
                        residuals[index]
                        - (left if features[feature_index] <= threshold else right)
                    )
                    ** 2
                    for index, (features, _) in enumerate(rows)
                )
                candidate = (error, feature_index, threshold, left, right)
                if best is None or candidate < best:
                    best = candidate
        if best is None:
            break
        _, feature_index, threshold, left, right = best
        stump = {
            "feature": SIGNAL_FEATURES[feature_index],
            "feature_index": feature_index,
            "threshold": threshold,
            "left": left * 0.3,
            "right": right * 0.3,
        }
        stumps.append(stump)
        for index, (features, _) in enumerate(rows):
            scores[index] += stump["left"] if features[feature_index] <= threshold else stump["right"]
    return {"stumps": stumps}


def _boost_score(model: dict[str, Any], features: list[float]) -> float:
    score = 0.0
    for stump in model["stumps"]:
        score += stump["left"] if features[stump["feature_index"]] <= stump["threshold"] else stump["right"]
    return _sigmoid(score)


def _fit_small_tree(
    rows: list[tuple[list[float], int]],
    depth: int = 0,
    max_depth: int = 3,
) -> dict[str, Any]:
    positives = sum(target for _, target in rows)
    probability = positives / max(1, len(rows))
    if depth >= max_depth or len(rows) < 6 or positives in (0, len(rows)):
        return {"leaf": probability, "samples": len(rows)}
    parent_error = sum((target - probability) ** 2 for _, target in rows)
    best: tuple[float, int, float, list[Any], list[Any]] | None = None
    for feature_index in range(len(SIGNAL_FEATURES)):
        values = sorted({features[feature_index] for features, _ in rows})
        if len(values) < 2:
            continue
        thresholds = [
            (values[index - 1] + values[index]) / 2.0
            for index in range(1, len(values))
        ]
        stride = max(1, len(thresholds) // 16)
        for threshold in thresholds[::stride][:16]:
            left = [row for row in rows if row[0][feature_index] <= threshold]
            right = [row for row in rows if row[0][feature_index] > threshold]
            if len(left) < 2 or len(right) < 2:
                continue
            error = 0.0
            for group in (left, right):
                mean = statistics.fmean(target for _, target in group)
                error += sum((target - mean) ** 2 for _, target in group)
            candidate = (error, feature_index, threshold, left, right)
            if best is None or candidate[:3] < best[:3]:
                best = candidate
    if best is None or best[0] >= parent_error:
        return {"leaf": probability, "samples": len(rows)}
    _, feature_index, threshold, left, right = best
    return {
        "feature": SIGNAL_FEATURES[feature_index],
        "feature_index": feature_index,
        "threshold": threshold,
        "left": _fit_small_tree(left, depth + 1, max_depth),
        "right": _fit_small_tree(right, depth + 1, max_depth),
        "samples": len(rows),
    }


def _tree_score(model: dict[str, Any], features: list[float]) -> float:
    node = model
    while "leaf" not in node:
        node = (
            node["left"]
            if features[node["feature_index"]] <= node["threshold"]
            else node["right"]
        )
    return float(node["leaf"])


def _threshold_decision(score: float, low: float, high: float) -> str:
    if score <= low:
        return "show"
    if score >= high:
        return "hide"
    return "unsure"


def _wilson(successes: int, total: int) -> dict[str, float]:
    if total <= 0:
        return {"low": 0.0, "high": 0.0}
    z = 1.959963984540054
    proportion = successes / total
    denominator = 1.0 + z * z / total
    center = (proportion + z * z / (2 * total)) / denominator
    margin = (
        z
        * math.sqrt(
            proportion * (1.0 - proportion) / total
            + z * z / (4 * total * total)
        )
        / denominator
    )
    return {"low": max(0.0, center - margin), "high": min(1.0, center + margin)}


def _outcome_metrics(outcomes: list[tuple[dict[str, Any], str]]) -> dict[str, Any]:
    predicted_show = [item for item in outcomes if item[1] == "show"]
    actual_hide = [item for item in outcomes if item[0]["decision"] == "hide"]
    show_successes = sum(1 for row, _ in predicted_show if row["decision"] == "show")
    hide_successes = sum(1 for row, predicted in actual_hide if predicted == "hide")
    critical_false_allows = sum(
        1 for row, predicted in outcomes if predicted == "show" and row["decision"] == "hide"
    )
    false_blocks = sum(
        1 for row, predicted in outcomes if predicted == "hide" and row["decision"] == "show"
    )
    uncertain = sum(1 for _, predicted in outcomes if predicted == "unsure")
    return {
        "sample_count": len(outcomes),
        "show_precision": show_successes / len(predicted_show) if predicted_show else 0.0,
        "show_precision_95ci": _wilson(show_successes, len(predicted_show)),
        "hide_recall": hide_successes / len(actual_hide) if actual_hide else 0.0,
        "hide_recall_95ci": _wilson(hide_successes, len(actual_hide)),
        "critical_false_allows": critical_false_allows,
        "false_blocks": false_blocks,
        "coverage": (len(outcomes) - uncertain) / max(1, len(outcomes)),
        "uncertainty_percent": uncertain * 100.0 / max(1, len(outcomes)),
        "heavy_segmentation_needed_percent": uncertain * 100.0 / max(1, len(outcomes)),
    }


def _metrics(
    rows: list[dict[str, Any]],
    scorer: Any,
    low: float,
    high: float,
) -> dict[str, Any]:
    outcomes = []
    for row in rows:
        predicted = _threshold_decision(float(scorer(_vector(row))), low, high)
        outcomes.append((row, predicted))
    result = _outcome_metrics(outcomes)
    by_reason: dict[str, Any] = {}
    for reason in REASONS:
        subset = [item for item in outcomes if reason in item[0].get("reasons", [])]
        if subset:
            by_reason[reason] = _outcome_metrics(subset)
    by_category = {}
    for category in sorted({item[0].get("category") for item in outcomes if item[0].get("category")}):
        subset = [item for item in outcomes if item[0].get("category") == category]
        by_category[category] = _outcome_metrics(subset)
    cluster_groups: dict[str, list[tuple[dict[str, Any], str]]] = defaultdict(list)
    for item in outcomes:
        cluster_groups[item[0].get("cluster_id", "")].append(item)
    comparable_clusters = [items for items in cluster_groups.values() if len(items) > 1]
    stable_clusters = sum(
        1 for items in comparable_clusters if len({predicted for _, predicted in items}) == 1
    )
    result["by_reason"] = by_reason
    result["by_category"] = by_category
    result["cluster_stability"] = {
        "comparable_cluster_count": len(comparable_clusters),
        "stable_cluster_count": stable_clusters,
        "stable_fraction": stable_clusters / len(comparable_clusters) if comparable_clusters else None,
    }
    return result


def _choose_thresholds(rows: list[dict[str, Any]], scorer: Any) -> tuple[float, float, dict[str, Any]]:
    best: tuple[Any, ...] | None = None
    result: tuple[float, float, dict[str, Any]] | None = None
    for low_index in range(1, 10):
        low = low_index / 20.0
        for high_index in range(11, 20):
            high = high_index / 20.0
            metrics = _metrics(rows, scorer, low, high)
            rank = (
                metrics["critical_false_allows"],
                -metrics["hide_recall"],
                -metrics["show_precision"],
                metrics["uncertainty_percent"],
                low,
                -high,
            )
            if best is None or rank < best:
                best = rank
                result = (low, high, metrics)
    assert result is not None
    return result


def select_policy(labels_path: Path, signals_path: Path, output: Path) -> None:
    labels = validate_label_export(labels_path)
    signals = {item["sample_id"]: item for item in read_jsonl(signals_path)}
    splits = {item["sample_id"]: item for item in read_jsonl(SPLIT_LOCK)}
    labels_by_id = {item["sample_id"]: item for item in labels}
    rows = []
    for sample_id, label in labels_by_id.items():
        if label["decision"] == "unsure":
            continue
        if sample_id not in signals:
            raise ValueError("missing targeted signals")
        row = {
            **signals[sample_id],
            "decision": label["decision"],
            "reasons": label["reasons"],
            "split": splits[sample_id]["split"],
            "category": splits[sample_id]["source_category"],
            "cluster_id": splits[sample_id]["cluster_id"],
        }
        rows.append(row)
    train = [row for row in rows if row["split"] == "exploratory"]
    validation = [row for row in rows if row["split"] == "validation"]
    if not train or not validation:
        raise ValueError("insufficient conclusive train/validation labels")
    train_pairs = [(_vector(row), int(row["decision"] == "hide")) for row in train]
    logistic = _fit_logistic(train_pairs)
    tree = _fit_small_tree(train_pairs)
    boost = _fit_stump_boost(train_pairs)
    rule_scorer = lambda values: max(values[0], values[3], values[6], values[9], values[12])
    candidates = []
    for name, model, scorer in (
        ("deterministic_rules", {"formula": "max(adult, shoulder, elbow, knee, torso)"}, rule_scorer),
        ("logistic_regression", logistic, lambda values, model=logistic: _logistic_score(model, values)),
        ("small_tree_depth_3", tree, lambda values, model=tree: _tree_score(model, values)),
        ("bounded_stump_boost", boost, lambda values, model=boost: _boost_score(model, values)),
    ):
        low, high, metrics = _choose_thresholds(validation, scorer)
        candidates.append(
            {
                "name": name,
                "model": model,
                "thresholds": {"show_max": low, "hide_min": high},
                "validation": metrics,
            }
        )
    chosen = min(
        candidates,
        key=lambda item: (
            item["validation"]["critical_false_allows"],
            -item["validation"]["hide_recall"],
            -item["validation"]["show_precision"],
            item["validation"]["uncertainty_percent"],
            item["name"],
        ),
    )
    seal = {
        "seal_version": 1,
        "test_opened": False,
        "feature_names": list(SIGNAL_FEATURES),
        "labels_sha256": sha256_file(labels_path)[0],
        "signals_sha256": sha256_file(signals_path)[0],
        "split_sha256": sha256_file(SPLIT_LOCK)[0],
        "training_count": len(train),
        "validation_count": len(validation),
        "unsure_excluded": sum(1 for item in labels if item["decision"] == "unsure"),
        "candidates": candidates,
        "selected": chosen,
    }
    write_json(output, seal)
    print(f"policy_selection=sealed selected={chosen['name']} test_opened=false")


def open_frozen_test(
    labels_path: Path,
    signals_path: Path,
    seal_path: Path,
    output: Path,
) -> None:
    if output.exists():
        raise ValueError("frozen test was already opened")
    labels = validate_label_export(labels_path)
    signals = {item["sample_id"]: item for item in read_jsonl(signals_path)}
    splits = {item["sample_id"]: item for item in read_jsonl(SPLIT_LOCK)}
    seal = read_json(seal_path)
    if (
        seal["test_opened"]
        or seal["labels_sha256"] != sha256_file(labels_path)[0]
        or seal["signals_sha256"] != sha256_file(signals_path)[0]
        or seal["split_sha256"] != sha256_file(SPLIT_LOCK)[0]
    ):
        raise ValueError("policy seal does not match frozen inputs")
    selected = seal["selected"]
    scorer = _selected_scorer(selected)
    rows = _frozen_test_rows(labels, signals, splits)
    thresholds = selected["thresholds"]
    metrics = _metrics(rows, scorer, thresholds["show_max"], thresholds["hide_min"])
    result = {
        "opened_once": True,
        "opened_at": dt.datetime.now(dt.timezone.utc).isoformat(),
        "selected_policy": selected["name"],
        "parameters_sha256": hashlib.sha256(
            json.dumps(selected, sort_keys=True).encode("utf-8")
        ).hexdigest(),
        "test": metrics,
        "gate": {
            "zero_critical_false_allows": metrics["critical_false_allows"] == 0,
            "show_precision_at_least_95": metrics["show_precision"] >= 0.95,
            "hide_recall_at_least_95": metrics["hide_recall"] >= 0.95,
            "uncertainty_at_most_40": metrics["uncertainty_percent"] <= 40.0,
            "heavy_segmentation_at_most_10": metrics["heavy_segmentation_needed_percent"] <= 10.0,
        },
        "production_claim": False,
    }
    write_json(output, result)
    print(json.dumps(result, sort_keys=True))


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--cache",
        type=Path,
        default=Path(os.environ.get("DAG_V2_BENCHMARK_CACHE", Path.home() / ".cache/dag-v2-benchmark")),
    )
    subparsers = parser.add_subparsers(dest="command", required=True)
    subparsers.add_parser("create-review-plan")
    subparsers.add_parser("verify-review-plan")
    subparsers.add_parser("verify-results")
    assets = subparsers.add_parser("prepare-reviewer-assets")
    assets.add_argument("--output", type=Path, required=True)
    assets.add_argument("--fixture", action="store_true")
    labels = subparsers.add_parser("validate-label-export")
    labels.add_argument("path", type=Path)
    labels.add_argument("--allow-incomplete", action="store_true")
    normalize = subparsers.add_parser("normalize-label-export")
    normalize.add_argument("--source", type=Path, required=True)
    normalize.add_argument("--output", type=Path, required=True)
    signals = subparsers.add_parser("extract-signals")
    signals.add_argument("--output", type=Path, required=True)
    signals.add_argument("--limit", type=int)
    teacher = subparsers.add_parser("compare-teacher")
    teacher.add_argument("--signals", type=Path, required=True)
    teacher.add_argument("--output", type=Path, required=True)
    teacher.add_argument("--limit", type=int, default=20)
    select = subparsers.add_parser("select-policy")
    select.add_argument("--labels", type=Path, required=True)
    select.add_argument("--signals", type=Path, required=True)
    select.add_argument("--output", type=Path, required=True)
    frozen = subparsers.add_parser("open-test")
    frozen.add_argument("--labels", type=Path, required=True)
    frozen.add_argument("--signals", type=Path, required=True)
    frozen.add_argument("--seal", type=Path, required=True)
    frozen.add_argument("--output", type=Path, required=True)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    if args.command == "create-review-plan":
        create_review_plan()
    elif args.command == "verify-review-plan":
        verify_review_plan()
    elif args.command == "verify-results":
        verify_04b_results()
    elif args.command == "prepare-reviewer-assets":
        prepare_reviewer_assets(args.cache, args.output, args.fixture)
    elif args.command == "validate-label-export":
        validate_label_export(args.path, not args.allow_incomplete)
    elif args.command == "normalize-label-export":
        normalize_label_export(args.source, args.output)
    elif args.command == "extract-signals":
        extract_targeted_signals(args.cache, args.output, args.limit)
    elif args.command == "compare-teacher":
        compare_segmentation_teacher(args.cache, args.signals, args.output, args.limit)
    elif args.command == "select-policy":
        select_policy(args.labels, args.signals, args.output)
    elif args.command == "open-test":
        open_frozen_test(args.labels, args.signals, args.seal, args.output)


if __name__ == "__main__":
    main()
