#!/usr/bin/env python3
"""Reproducible, fail-closed, non-product DAG v2 model benchmark."""

from __future__ import annotations

import argparse
import concurrent.futures
import hashlib
import html
import json
import math
import os
import re
import resource
import shutil
import socket
import statistics
import subprocess
import sys
import tempfile
import time
import urllib.parse
import urllib.request
import urllib.error
from collections import Counter
from pathlib import Path
from typing import Any, Iterable

ROOT = Path(__file__).resolve().parents[2]
TOOL_DIR = Path(__file__).resolve().parent
LOCK_PATH = TOOL_DIR / "models.lock.json"
SPEC_PATH = TOOL_DIR / "corpus_spec.json"
DEFAULT_CACHE = Path(os.environ.get("DAG_V2_BENCHMARK_CACHE", Path.home() / ".cache/dag-v2-benchmark"))
USER_AGENT = "Glosh-DAG-v2-benchmark/04A (bounded research; no product inference)"
SAFE_ID = re.compile(r"^[a-z0-9][a-z0-9._-]{0,127}$")
MAX_DOWNLOAD_WORKERS = 4
INFERENCE_BATCH_SIZE = 1


def read_json(path: Path) -> dict[str, Any]:
    with path.open("r", encoding="utf-8") as source:
        return json.load(source)


def sha256_file(path: Path) -> tuple[str, int]:
    digest = hashlib.sha256()
    size = 0
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
            size += len(chunk)
    return digest.hexdigest(), size


def cache_path(cache: Path, group: str, filename: str) -> Path:
    if not SAFE_ID.fullmatch(filename):
        raise ValueError(f"unsafe filename: {filename!r}")
    base = (cache / group).resolve()
    result = (base / filename).resolve()
    if base != result.parent:
        raise ValueError("path escapes cache")
    return result


def atomic_verified_write(
    destination: Path,
    expected_sha: str,
    expected_size: int,
    chunks: Iterable[bytes],
) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    digest = hashlib.sha256()
    size = 0
    temporary = destination.with_suffix(destination.suffix + ".partial")
    try:
        with temporary.open("wb") as output:
            for chunk in chunks:
                if not chunk:
                    continue
                size += len(chunk)
                if size > expected_size:
                    raise ValueError(f"{destination.name}: source exceeds locked size")
                digest.update(chunk)
                output.write(chunk)
        if size != expected_size or digest.hexdigest() != expected_sha:
            raise ValueError(
                f"{destination.name}: integrity mismatch "
                f"(size={size}, sha256={digest.hexdigest()})"
            )
        os.replace(temporary, destination)
    finally:
        temporary.unlink(missing_ok=True)


def stream_git_object(revision: str, repository_path: str) -> Iterable[bytes]:
    if repository_path.startswith("/") or ".." in Path(repository_path).parts:
        raise ValueError("repository path escapes root")
    process = subprocess.Popen(
        ["git", "show", f"{revision}:{repository_path}"],
        cwd=ROOT,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    assert process.stdout is not None
    try:
        for chunk in iter(lambda: process.stdout.read(1024 * 1024), b""):
            yield chunk
    finally:
        stderr = process.stderr.read().decode("utf-8", "replace") if process.stderr else ""
        return_code = process.wait()
        if return_code:
            raise RuntimeError(f"git object unavailable: {stderr.strip()}")


def stream_https(url: str) -> Iterable[bytes]:
    parsed = urllib.parse.urlsplit(url)
    if parsed.scheme != "https" or parsed.username or parsed.password:
        raise ValueError("only credential-free HTTPS sources are accepted")
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=30) as response:
        final = urllib.parse.urlsplit(response.geturl())
        if final.scheme != "https":
            raise ValueError("redirect left HTTPS")
        for chunk in iter(lambda: response.read(1024 * 1024), b""):
            yield chunk


def download_models(cache: Path) -> None:
    lock = read_json(LOCK_PATH)
    total = sum(int(model["size_bytes"]) for model in lock["models"] if model["selected_for_benchmark"])
    if total > int(lock["maximum_total_bytes"]):
        raise ValueError("locked models exceed total byte limit")
    count = 0
    for model in lock["models"]:
        if not model["selected_for_benchmark"]:
            continue
        size = int(model["size_bytes"])
        if size > int(lock["maximum_model_bytes"]):
            raise ValueError(f"{model['id']}: exceeds per-model limit")
        destination = cache_path(cache, "models", model["filename"])
        if destination.exists() and sha256_file(destination) == (model["sha256"], size):
            count += 1
            continue
        chunks = (
            stream_git_object(model["revision"], model["repository_path"])
            if model["source_kind"] == "git_object"
            else stream_https(model["url"])
        )
        atomic_verified_write(destination, model["sha256"], size, chunks)
        count += 1
    print(f"verified_models={count} bytes={total} cache={cache.resolve()}")


def sanitized_plain(value: Any, limit: int = 240) -> str:
    text = html.unescape(re.sub(r"<[^>]*>", "", str(value or "")))
    return " ".join(text.split())[:limit]


def license_allowed(short_name: str, allowlist: list[str]) -> bool:
    normalized = re.sub(r"\s+", " ", short_name.lower()).strip()
    return any(item in normalized for item in allowlist)


def api_json(params: dict[str, Any]) -> dict[str, Any]:
    encoded = urllib.parse.urlencode(params)
    request = urllib.request.Request(
        f"https://commons.wikimedia.org/w/api.php?{encoded}",
        headers={"User-Agent": USER_AGENT},
    )
    for attempt in range(5):
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                payload = json.load(response)
            time.sleep(0.5)
            return payload
        except urllib.error.HTTPError as error:
            if error.code != 429 or attempt == 4:
                raise
            retry_after = error.headers.get("Retry-After")
            delay = min(30, int(retry_after)) if retry_after and retry_after.isdigit() else 2 ** (attempt + 1)
            time.sleep(delay)
        except (urllib.error.URLError, TimeoutError, socket.timeout):
            if attempt == 4:
                raise
            time.sleep(2 ** attempt)
    raise RuntimeError("unreachable API retry state")


def dhash64(image: Any) -> str:
    from PIL import Image

    gray = image.convert("L").resize((9, 8), Image.Resampling.LANCZOS)
    pixels = list(gray.getdata())
    value = 0
    for y in range(8):
        for x in range(8):
            value = (value << 1) | int(pixels[y * 9 + x] > pixels[y * 9 + x + 1])
    return f"{value:016x}"


def corpus_candidates(
    category: str,
    continuation: str | None,
    cache: Path,
) -> tuple[list[dict[str, Any]], str | None]:
    params: dict[str, Any] = {
        "action": "query",
        "format": "json",
        "formatversion": 2,
        "generator": "categorymembers",
        "gcmtitle": f"Category:{category}",
        "gcmtype": "file",
        "gcmlimit": 50,
        "prop": "imageinfo",
        "iiprop": "url|mime|size|extmetadata",
        "iiurlwidth": 1024,
    }
    if continuation is not None:
        params["gcmcontinue"] = continuation
    metadata_cache = cache / "metadata"
    metadata_cache.mkdir(parents=True, exist_ok=True)
    key = hashlib.sha256(json.dumps(params, sort_keys=True).encode("utf-8")).hexdigest()
    cached = metadata_cache / f"{key}.json"
    if cached.exists():
        payload = read_json(cached)
    else:
        payload = api_json(params)
        temporary = cached.with_suffix(".partial")
        temporary.write_text(json.dumps(payload, sort_keys=True) + "\n", encoding="utf-8")
        os.replace(temporary, cached)
    return payload.get("query", {}).get("pages", []), payload.get("continue", {}).get("gcmcontinue")


def corpus_search_candidates(
    search: str,
    offset: int | None,
    cache: Path,
) -> tuple[list[dict[str, Any]], int | None]:
    params: dict[str, Any] = {
        "action": "query",
        "format": "json",
        "formatversion": 2,
        "generator": "search",
        "gsrnamespace": 6,
        "gsrlimit": 50,
        "gsrsearch": search,
        "prop": "imageinfo",
        "iiprop": "url|mime|size|extmetadata",
        "iiurlwidth": 1024,
    }
    if offset is not None:
        params["gsroffset"] = offset
    metadata_cache = cache / "metadata"
    metadata_cache.mkdir(parents=True, exist_ok=True)
    key = hashlib.sha256(json.dumps(params, sort_keys=True).encode("utf-8")).hexdigest()
    cached = metadata_cache / f"{key}.json"
    if cached.exists():
        payload = read_json(cached)
    else:
        payload = api_json(params)
        temporary = cached.with_suffix(".partial")
        temporary.write_text(json.dumps(payload, sort_keys=True) + "\n", encoding="utf-8")
        os.replace(temporary, cached)
    return payload.get("query", {}).get("pages", []), payload.get("continue", {}).get("gsroffset")


def fetch_corpus_image(url: str, destination: Path, maximum: int) -> tuple[str, int]:
    parsed = urllib.parse.urlsplit(url)
    if parsed.scheme != "https" or parsed.query:
        raise ValueError("corpus URL must be plain HTTPS without query")
    for attempt in range(4):
        request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
        digest = hashlib.sha256()
        size = 0
        temporary = destination.with_suffix(".partial")
        try:
            with urllib.request.urlopen(request, timeout=30) as response, temporary.open("wb") as output:
                if urllib.parse.urlsplit(response.geturl()).scheme != "https":
                    raise ValueError("corpus redirect left HTTPS")
                for chunk in iter(lambda: response.read(256 * 1024), b""):
                    size += len(chunk)
                    if size > maximum:
                        raise ValueError("corpus image exceeds per-file limit")
                    digest.update(chunk)
                    output.write(chunk)
            os.replace(temporary, destination)
            return digest.hexdigest(), size
        except urllib.error.HTTPError as error:
            if error.code not in {429, 500, 502, 503, 504} or attempt == 3:
                raise
            time.sleep(2 ** attempt)
        except (urllib.error.URLError, TimeoutError, socket.timeout):
            if attempt == 3:
                raise
            time.sleep(2 ** attempt)
        finally:
            temporary.unlink(missing_ok=True)
    raise RuntimeError("unreachable image retry state")


def build_corpus(cache: Path, limit: int, group_ids: set[str] | None = None) -> None:
    from PIL import Image, ImageOps

    spec = read_json(SPEC_PATH)
    corpus = (cache / "corpus").resolve()
    corpus.mkdir(parents=True, exist_ok=True)
    manifest_path = corpus / "manifest.jsonl"
    existing: list[dict[str, Any]] = []
    if manifest_path.exists():
        with manifest_path.open("r", encoding="utf-8") as source:
            existing = [json.loads(line) for line in source if line.strip()]
    hashes = {item["sha256"] for item in existing}
    titles = {item["source_page"] for item in existing}
    total_bytes = sum(int(item["size_bytes"]) for item in existing)
    records = list(existing)
    def persist_manifest() -> None:
        temporary_manifest = manifest_path.with_suffix(".partial")
        with temporary_manifest.open("w", encoding="utf-8") as output:
            for item in records:
                output.write(json.dumps(item, sort_keys=True, ensure_ascii=False) + "\n")
        os.replace(temporary_manifest, manifest_path)

    def download_candidate(candidate: dict[str, Any]) -> dict[str, Any] | None:
        destination = Path(candidate["temporary_file"])
        try:
            digest, actual_size = fetch_corpus_image(
                candidate["url"],
                destination,
                int(spec["per_file_max_bytes"]),
            )
            with Image.open(destination) as opened:
                image = ImageOps.exif_transpose(opened)
                image.load()
                width, height = image.size
                if width < 24 or height < 24 or width * height > 80_000_000:
                    raise ValueError("unsupported dimensions")
                perceptual = dhash64(image)
            return {
                **candidate,
                "sha256": digest,
                "size_bytes": actual_size,
                "width": width,
                "height": height,
                "perceptual_hash64": perceptual,
            }
        except Exception:
            destination.unlink(missing_ok=True)
            return None

    for group in spec["queries"]:
        if group_ids is not None and group["id"] not in group_ids:
            continue
        if len(records) >= limit:
            break
        have = sum(item["category"] == group["id"] for item in records)
        sources = [("category", category) for category in group["categories"]]
        sources.append(("search", group["search"]))
        for source_kind, source_value in sources:
            continuation: str | int | None = None
            pages_read = 0
            while have < int(group["target"]) and len(records) < limit and pages_read < 3:
                if source_kind == "category":
                    pages, next_continuation = corpus_candidates(
                        source_value,
                        continuation if isinstance(continuation, str) else None,
                        cache,
                    )
                else:
                    pages, next_continuation = corpus_search_candidates(
                        source_value,
                        continuation if isinstance(continuation, int) else None,
                        cache,
                    )
                pages_read += 1
                if not pages:
                    break
                candidates: list[dict[str, Any]] = []
                for page in pages:
                    infos = page.get("imageinfo") or []
                    if not infos:
                        continue
                    info = infos[0]
                    mime = str(info.get("mime", ""))
                    if mime not in {"image/jpeg", "image/png", "image/webp"}:
                        continue
                    metadata = info.get("extmetadata") or {}
                    license_name = sanitized_plain((metadata.get("LicenseShortName") or {}).get("value"))
                    if not license_allowed(license_name, spec["license_allowlist"]):
                        continue
                    source_page = str(info.get("descriptionurl") or "")
                    url = str(info.get("thumburl") or info.get("url") or "")
                    if not source_page.startswith("https://commons.wikimedia.org/") or source_page in titles:
                        continue
                    declared_size = int(info.get("size") or 0)
                    if declared_size <= 0:
                        continue
                    if total_bytes >= int(spec["total_max_bytes"]):
                        raise ValueError("corpus byte limit reached")
                    suffix = { "image/jpeg": ".jpg", "image/png": ".png", "image/webp": ".webp" }[mime]
                    temporary_id = hashlib.sha256(source_page.encode("utf-8")).hexdigest()[:20]
                    candidates.append(
                        {
                            "source_page": source_page,
                            "url": url,
                            "suffix": suffix,
                            "temporary_file": str(corpus / f"{temporary_id}{suffix}"),
                            "license": license_name,
                            "license_url": sanitized_plain((metadata.get("LicenseUrl") or {}).get("value")),
                            "author": sanitized_plain((metadata.get("Artist") or {}).get("value")),
                        }
                    )
                remaining = min(int(group["target"]) - have, limit - len(records))
                candidates = candidates[: max(remaining * 2, 4)]
                with concurrent.futures.ThreadPoolExecutor(max_workers=MAX_DOWNLOAD_WORKERS) as executor:
                    results = list(executor.map(download_candidate, candidates))
                for downloaded in results:
                    if downloaded is None:
                        continue
                    destination = Path(downloaded["temporary_file"])
                    if have >= int(group["target"]) or len(records) >= limit:
                        destination.unlink(missing_ok=True)
                        continue
                    digest = downloaded["sha256"]
                    actual_size = int(downloaded["size_bytes"])
                    if total_bytes + actual_size > int(spec["total_max_bytes"]):
                        destination.unlink(missing_ok=True)
                        raise ValueError("corpus byte limit reached")
                    if digest in hashes:
                        destination.unlink(missing_ok=True)
                        continue
                    sample_id = f"wmc-{digest[:20]}"
                    final_destination = corpus / f"{sample_id}{downloaded['suffix']}"
                    os.replace(destination, final_destination)
                    record = {
                        "sample_id": sample_id,
                        "relative_file": final_destination.name,
                        "source": "Wikimedia Commons",
                        "source_page": downloaded["source_page"],
                        "license": downloaded["license"],
                        "license_url": downloaded["license_url"],
                        "author": downloaded["author"],
                        "sha256": digest,
                        "perceptual_hash64": downloaded["perceptual_hash64"],
                        "width": downloaded["width"],
                        "height": downloaded["height"],
                        "size_bytes": actual_size,
                        "category": group["id"],
                        "source_label": group["source_label"],
                        "transformation": "Wikimedia thumbnail, maximum width 1024 px; decode validation",
                        "review_status": "source_category_unreviewed",
                        "cluster_visual": f"dhash-prefix-{downloaded['perceptual_hash64'][:4]}",
                    }
                    records.append(record)
                    hashes.add(digest)
                    titles.add(downloaded["source_page"])
                    total_bytes += actual_size
                    have += 1
                    persist_manifest()
                if next_continuation is None or next_continuation == continuation:
                    break
                continuation = next_continuation
            if have >= int(group["target"]) or len(records) >= limit:
                break
    persist_manifest()
    print(f"corpus_samples={len(records)} bytes={total_bytes} manifest={manifest_path}")


def load_manifest(cache: Path) -> list[dict[str, Any]]:
    path = cache / "corpus/manifest.jsonl"
    with path.open("r", encoding="utf-8") as source:
        records = [json.loads(line) for line in source if line.strip()]
    if len({item["sample_id"] for item in records}) != len(records):
        raise ValueError("duplicate sample_id")
    if len({item["sha256"] for item in records}) != len(records):
        raise ValueError("duplicate image SHA-256")
    return records


class ModelSuite:
    def __init__(self, cache: Path) -> None:
        import mediapipe as mp
        import numpy as np
        import onnxruntime as ort
        from mediapipe.tasks import python
        from mediapipe.tasks.python import vision

        self.np = np
        self.mp = mp
        self.vision = vision
        started = time.perf_counter()
        self.nsfw = ort.InferenceSession(
            str(cache / "models/nsfw_marqo_vit_tiny_384.onnx"),
            providers=["CPUExecutionProvider"],
        )
        self.load_nsfw_ms = (time.perf_counter() - started) * 1000
        started = time.perf_counter()
        self.pose = vision.PoseLandmarker.create_from_options(
            vision.PoseLandmarkerOptions(
                base_options=python.BaseOptions(
                    model_asset_path=str(cache / "models/pose_landmarker_lite.task")
                ),
                running_mode=vision.RunningMode.IMAGE,
                num_poses=4,
                min_pose_detection_confidence=0.25,
                min_pose_presence_confidence=0.25,
            )
        )
        self.load_pose_ms = (time.perf_counter() - started) * 1000
        started = time.perf_counter()
        self.segmenter = vision.ImageSegmenter.create_from_options(
            vision.ImageSegmenterOptions(
                base_options=python.BaseOptions(
                    model_asset_path=str(cache / "models/selfie_multiclass_256x256.tflite")
                ),
                running_mode=vision.RunningMode.IMAGE,
                output_category_mask=True,
                output_confidence_masks=False,
            )
        )
        self.load_segment_ms = (time.perf_counter() - started) * 1000

    def close(self) -> None:
        self.pose.close()
        self.segmenter.close()

    def adult(self, image: Any) -> float:
        from PIL import Image

        rgb = image.convert("RGB")
        side = min(rgb.size)
        left = (rgb.width - side) // 2
        top = (rgb.height - side) // 2
        resized = rgb.crop((left, top, left + side, top + side)).resize((384, 384), Image.Resampling.BILINEAR)
        array = self.np.asarray(resized, dtype=self.np.float32)
        array = (array / 127.5) - 1.0
        tensor = self.np.transpose(array, (2, 0, 1))[None, ...]
        logits = self.nsfw.run(None, {"pixel_values": tensor})[0][0]
        maximum = float(max(logits))
        exponents = [math.exp(float(item) - maximum) for item in logits]
        return exponents[0] / sum(exponents)

    def pose_signals(self, image: Any) -> dict[str, Any]:
        array = self.np.asarray(image.convert("RGB"), dtype=self.np.uint8)
        result = self.pose.detect(self.mp.Image(image_format=self.mp.ImageFormat.SRGB, data=array))
        poses = result.pose_landmarks
        landmark_indexes = {"shoulder": (11, 12), "elbow": (13, 14), "knee": (25, 26)}
        signals: dict[str, Any] = {"person_count": len(poses), "pose_confidence": 0.0}
        confidences: list[float] = []
        for signal, indexes in landmark_indexes.items():
            values = [
                min(float(getattr(pose[index], "visibility", 0.0)), float(getattr(pose[index], "presence", 0.0)))
                for pose in poses
                for index in indexes
            ]
            signals[f"{signal}_evidence"] = max(values, default=0.0)
            confidences.extend(values)
        signals["pose_confidence"] = statistics.fmean(confidences) if confidences else 0.0
        return signals

    def segmentation_signals(self, image: Any) -> dict[str, float]:
        array = self.np.asarray(image.convert("RGB"), dtype=self.np.uint8)
        result = self.segmenter.segment(self.mp.Image(image_format=self.mp.ImageFormat.SRGB, data=array))
        mask = result.category_mask.numpy_view()
        total = float(mask.size)
        return {
            "body_skin_ratio": float(self.np.count_nonzero(mask == 2)) / total,
            "face_skin_ratio": float(self.np.count_nonzero(mask == 3)) / total,
            "clothing_ratio": float(self.np.count_nonzero(mask == 4)) / total,
            "accessory_ratio": float(self.np.count_nonzero(mask == 5)) / total,
        }


def process_sample(suite: ModelSuite, image: Any, record: dict[str, Any]) -> dict[str, Any]:
    evidence: dict[str, Any] = {
        "sample_id": record["sample_id"],
        "category": record["category"],
        "review_status": record["review_status"],
        "female_evidence": None,
        "source_model_versions": [
            "marqo-nsfw-vit-tiny-384-dag-v1-reference",
            "mediapipe-pose-landmarker-lite-float16-1",
            "mediapipe-selfie-multiclass-256-float32-1",
        ],
    }
    latencies: dict[str, float] = {}
    started = time.perf_counter()
    evidence["adult_score"] = suite.adult(image)
    latencies["adult_ms"] = (time.perf_counter() - started) * 1000
    started = time.perf_counter()
    evidence.update(suite.pose_signals(image))
    latencies["pose_ms"] = (time.perf_counter() - started) * 1000
    started = time.perf_counter()
    evidence.update(suite.segmentation_signals(image))
    latencies["segment_ms"] = (time.perf_counter() - started) * 1000
    evidence["uncertainty"] = 1.0 - abs(float(evidence["adult_score"]) - 0.5) * 2.0
    evidence["latency"] = latencies
    return evidence


def run_benchmark(cache: Path, limit: int | None) -> None:
    from PIL import Image, ImageOps
    import psutil

    download_models(cache)
    records = load_manifest(cache)
    if limit is not None:
        records = records[:limit]
    result_dir = cache / "results"
    result_dir.mkdir(parents=True, exist_ok=True)
    output_path = result_dir / "evidence.jsonl"
    existing: dict[str, dict[str, Any]] = {}
    if output_path.exists():
        with output_path.open("r", encoding="utf-8") as source:
            existing = {item["sample_id"]: item for item in map(json.loads, filter(str.strip, source))}
    suite = ModelSuite(cache)
    process = psutil.Process()
    peak_rss = process.memory_info().rss
    started_total = time.perf_counter()
    newly_processed = 0
    try:
        with output_path.open("a", encoding="utf-8") as output:
            for record in records:
                if record["sample_id"] in existing:
                    continue
                image_path = cache / "corpus" / record["relative_file"]
                if sha256_file(image_path)[0] != record["sha256"]:
                    raise ValueError(f"{record['sample_id']}: corpus hash mismatch")
                with Image.open(image_path) as opened:
                    image = ImageOps.exif_transpose(opened).convert("RGB")
                    result = process_sample(suite, image, record)
                    image.close()
                output.write(json.dumps(result, sort_keys=True) + "\n")
                output.flush()
                newly_processed += 1
                peak_rss = max(peak_rss, process.memory_info().rss)
    finally:
        suite.close()
    run = {
        "runtime": {
            "python": sys.version.split()[0],
            "platform": sys.platform,
            "machine": os.uname().machine,
            "backend": "CPU",
            "mps_available": False,
            "mps_reason": "selected ONNX Runtime and MediaPipe Tasks adapters expose CPU only in this harness",
        },
        "model_load_ms": {
            "adult": suite.load_nsfw_ms,
            "pose": suite.load_pose_ms,
            "segment": suite.load_segment_ms,
        },
        "processed_or_resumed": len(records),
        "newly_processed": newly_processed,
        "resumed_count": len(records) - newly_processed,
        "wall_ms": (time.perf_counter() - started_total) * 1000,
        "peak_rss_bytes": peak_rss,
        "max_rss_ru_bytes": int(resource.getrusage(resource.RUSAGE_SELF).ru_maxrss),
    }
    (result_dir / "run.json").write_text(json.dumps(run, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps(run, sort_keys=True))


def percentile(values: list[float], fraction: float) -> float | None:
    if not values:
        return None
    ordered = sorted(values)
    index = max(0, min(len(ordered) - 1, math.ceil(fraction * len(ordered)) - 1))
    return ordered[index]


def stage_stats(records: list[dict[str, Any]], name: str) -> dict[str, Any]:
    values = [float(item["latency"][name]) for item in records]
    return {
        "count": len(values),
        "p50_ms": percentile(values, 0.50),
        "p95_ms": percentile(values, 0.95),
        "max_ms": max(values, default=None),
        "mean_ms": statistics.fmean(values) if values else None,
    }


def simulate(records: list[dict[str, Any]]) -> dict[str, Any]:
    all_totals = [sum(float(value) for value in item["latency"].values()) for item in records]
    adaptive_totals: list[float] = []
    adaptive_pose = 0
    adaptive_segment = 0
    minimal_totals: list[float] = []
    no_response = 0
    for item in records:
        adult = float(item["adult_score"])
        adult_ms = float(item["latency"]["adult_ms"])
        pose_ms = float(item["latency"]["pose_ms"])
        segment_ms = float(item["latency"]["segment_ms"])
        minimal_totals.append(adult_ms)
        if not math.isfinite(adult):
            no_response += 1
            adaptive_totals.append(adult_ms)
            continue
        total = adult_ms
        if adult < 0.80:
            total += pose_ms
            adaptive_pose += 1
            if 0.10 < adult < 0.80 or int(item["person_count"]) > 0:
                total += segment_ms
                adaptive_segment += 1
        adaptive_totals.append(total)
    def totals(values: list[float]) -> dict[str, Any]:
        return {
            "p50_ms": percentile(values, 0.50),
            "p95_ms": percentile(values, 0.95),
            "max_ms": max(values, default=None),
            "mean_ms": statistics.fmean(values) if values else None,
        }
    count = max(1, len(records))
    return {
        "all_models": {**totals(all_totals), "pose_percent": 100.0, "segment_percent": 100.0},
        "adaptive": {
            **totals(adaptive_totals),
            "pose_percent": adaptive_pose * 100.0 / count,
            "segment_percent": adaptive_segment * 100.0 / count,
            "rule": "adult always; pose when adult<0.80; segment when pose found or adult in (0.10,0.80)",
        },
        "conservative_minimal": {
            **totals(minimal_totals),
            "pose_percent": 0.0,
            "segment_percent": 0.0,
            "coverage_warning": "NSFW score alone cannot enforce modesty and uncertain inputs remain Hide",
        },
        "known_false_allows": None,
        "known_false_blocks": None,
        "accuracy_reason": "corpus source queries are not human policy labels",
        "no_response_count": no_response,
    }


def summarize(cache: Path) -> None:
    evidence_path = cache / "results/evidence.jsonl"
    with evidence_path.open("r", encoding="utf-8") as source:
        records = [json.loads(line) for line in source if line.strip()]
    lock = read_json(LOCK_PATH)
    manifest = load_manifest(cache)
    processed = {item["sample_id"] for item in records}
    covered = Counter(item["category"] for item in manifest if item["sample_id"] in processed)
    summary = {
        "schema_version": 1,
        "sample_count": len(records),
        "source_label_status": "unreviewed_source_category; not accuracy ground truth",
        "category_counts": dict(sorted(covered.items())),
        "stages": {
            "adult_ms": stage_stats(records, "adult_ms"),
            "pose_ms": stage_stats(records, "pose_ms"),
            "segment_ms": stage_stats(records, "segment_ms"),
        },
        "cascades": simulate(records),
        "model_total_bytes": sum(int(item["size_bytes"]) for item in lock["models"] if item["selected_for_benchmark"]),
        "run": read_json(cache / "results/run.json"),
        "disagreement_reference": {
            "available": False,
            "reason": "the adult candidate is itself the independently executed DAG v1 professional artifact; no separate v2 decision model exists",
        },
    }
    destination = cache / "results/summary.json"
    destination.write_text(json.dumps(summary, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps(summary, indent=2, sort_keys=True))


def export_android(cache: Path, limit: int) -> None:
    all_records = load_manifest(cache)
    with (cache / "results/evidence.jsonl").open("r", encoding="utf-8") as source:
        evidence = {item["sample_id"]: item for item in map(json.loads, filter(str.strip, source))}
    grouped: dict[str, list[dict[str, Any]]] = {}
    for record in all_records:
        grouped.setdefault(record["category"], []).append(record)
    records: list[dict[str, Any]] = []
    index = 0
    categories = sorted(grouped)
    while len(records) < limit:
        added = False
        for category in categories:
            values = grouped[category]
            if index < len(values) and len(records) < limit:
                records.append(values[index])
                added = True
        if not added:
            break
        index += 1
    if len(records) < 50:
        raise ValueError("representative Android subset requires at least 50 corpus samples")
    destination = cache / "android-subset"
    destination.mkdir(parents=True, exist_ok=True)
    selected_files = {record["relative_file"] for record in records}
    for existing in destination.iterdir():
        if existing.is_file() and existing.name != "manifest.json" and existing.name not in selected_files:
            existing.unlink()
    manifest = []
    for record in records:
        source = cache / "corpus" / record["relative_file"]
        target = destination / record["relative_file"]
        if not target.exists():
            os.link(source, target)
        manifest.append(
            {
                "sample_id": record["sample_id"],
                "file": target.name,
                "sha256": record["sha256"],
                "category": record["category"],
                "expected_adult_score": float(evidence[record["sample_id"]]["adult_score"]),
                "expected_person_count": int(evidence[record["sample_id"]]["person_count"]),
            }
        )
    (destination / "manifest.json").write_text(
        json.dumps({"samples": manifest}, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    print(f"android_subset={len(manifest)} path={destination}")


def parity_signature(cache: Path) -> None:
    manifest = read_json(cache / "android-subset/manifest.json")["samples"]
    with (cache / "results/evidence.jsonl").open("r", encoding="utf-8") as source:
        evidence = {item["sample_id"]: item for item in map(json.loads, filter(str.strip, source))}
    adult = hashlib.sha256()
    pose = hashlib.sha256()
    missing: list[str] = []
    for item in sorted(manifest, key=lambda value: value["file"]):
        result = evidence.get(item["sample_id"])
        if result is None:
            missing.append(item["sample_id"])
            continue
        adult.update(f"{item['file']}:{float(result['adult_score']):.3f}\n".encode("utf-8"))
        pose.update(f"{item['file']}:{int(result['person_count'])}\n".encode("utf-8"))
    if missing:
        raise ValueError(f"missing desktop evidence for {len(missing)} Android samples")
    output = {
        "adult_score_3dp": adult.hexdigest(),
        "pose_count": pose.hexdigest(),
        "sample_count": len(manifest),
        "segment_parity": "response-count only; category-mask parity not implemented",
    }
    print(json.dumps(output, sort_keys=True))


def verify_repository() -> None:
    lock = read_json(LOCK_PATH)
    required = {
        "author",
        "source",
        "revision",
        "filename",
        "sha256",
        "size_bytes",
        "format",
        "code_license",
        "weights_license",
        "commercial_use",
        "restrictions",
        "runtime",
    }
    for model in lock["models"]:
        missing = required - model.keys()
        if missing:
            raise ValueError(f"{model['id']}: missing {sorted(missing)}")
        if not re.fullmatch(r"[0-9a-f]{64}", model["sha256"]):
            raise ValueError(f"{model['id']}: invalid SHA-256")
        if str(model["weights_license"]).lower() in {"unknown", "not confirmed", ""} and model["selected_for_benchmark"]:
            raise ValueError(f"{model['id']}: selected with unconfirmed weights license")
    forbidden_extensions = {".onnx", ".tflite", ".task", ".jpg", ".jpeg", ".png", ".webp", ".parquet"}
    tracked = subprocess.check_output(["git", "ls-files"], cwd=ROOT, text=True).splitlines()
    offenders = [
        path for path in tracked
        if path.startswith("tools/dag-v2-benchmark/") and Path(path).suffix.lower() in forbidden_extensions
    ]
    if offenders:
        raise ValueError(f"large/binary benchmark artifacts tracked: {offenders}")
    provider = ROOT / "feature-dag2/src/main/java/com/contentfilter/user/dag2/DagV2ImagePipeline.kt"
    provider_text = provider.read_text(encoding="utf-8")
    if "DagV2ImageDecision.Hide" not in provider_text or "Allow" in provider_text:
        raise ValueError("DAG v2 provider is no longer unconditionally Hide")
    print(f"repository_verification=ok models={len(lock['models'])} tracked_binary_artifacts=0 provider=Hide")


def cleanup(cache: Path) -> None:
    removed = 0
    for temporary in cache.rglob("*.partial"):
        temporary.unlink(missing_ok=True)
        removed += 1
    manifest = cache / "corpus/manifest.jsonl"
    referenced: set[str] = set()
    if manifest.exists():
        with manifest.open("r", encoding="utf-8") as source:
            referenced = {json.loads(line)["relative_file"] for line in source if line.strip()}
    corpus = cache / "corpus"
    if corpus.exists():
        for image in corpus.iterdir():
            if image.is_file() and image.name != "manifest.jsonl" and image.suffix.lower() in {
                ".jpg", ".jpeg", ".png", ".webp"
            } and image.name not in referenced:
                image.unlink()
                removed += 1
    print(f"removed_temporary_or_orphan_files={removed}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--cache", type=Path, default=DEFAULT_CACHE)
    subparsers = parser.add_subparsers(dest="command", required=True)
    subparsers.add_parser("download-models")
    corpus = subparsers.add_parser("build-corpus")
    corpus.add_argument("--limit", type=int, default=240, choices=range(1, 501), metavar="[1-500]")
    corpus.add_argument("--group-id", action="append")
    run = subparsers.add_parser("run")
    run.add_argument("--limit", type=int, choices=range(1, 501), metavar="[1-500]")
    subparsers.add_parser("summarize")
    android = subparsers.add_parser("export-android")
    android.add_argument("--limit", type=int, default=75, choices=range(50, 101), metavar="[50-100]")
    subparsers.add_parser("verify-repository")
    subparsers.add_parser("parity-signature")
    subparsers.add_parser("cleanup")
    arguments = parser.parse_args()
    cache = arguments.cache.expanduser().resolve()
    if arguments.command == "download-models":
        download_models(cache)
    elif arguments.command == "build-corpus":
        build_corpus(
            cache,
            arguments.limit,
            set(arguments.group_id) if arguments.group_id else None,
        )
    elif arguments.command == "run":
        run_benchmark(cache, arguments.limit)
    elif arguments.command == "summarize":
        summarize(cache)
    elif arguments.command == "export-android":
        export_android(cache, arguments.limit)
    elif arguments.command == "verify-repository":
        verify_repository()
    elif arguments.command == "parity-signature":
        parity_signature(cache)
    elif arguments.command == "cleanup":
        cleanup(cache)


if __name__ == "__main__":
    main()
