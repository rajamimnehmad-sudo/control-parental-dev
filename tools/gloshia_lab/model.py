"""Reference preprocessing and policy helpers mirrored by the exact Node runner."""

from __future__ import annotations

import hashlib
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any, Sequence

import numpy as np
from PIL import Image, ImageOps


TARGET_SIZE = 224
PADDING_COLOR = (127, 127, 127)
IMAGE_MEAN = np.asarray((0.48145466, 0.4578275, 0.40821073), dtype=np.float32)
IMAGE_STD = np.asarray((0.26862954, 0.26130258, 0.27577711), dtype=np.float32)
FILTER_THRESHOLD = 0.40
UNCERTAIN_REVIEW_FLOOR = 0.30
UNCERTAIN_REGION_THRESHOLD = 0.45
REGIONAL_THRESHOLD = 0.50
REGIONAL_STRONG_THRESHOLD = 0.70
REGIONAL_CONSENSUS = 2
MAX_DIMENSION = 4096
MAX_PIXELS = 16_777_216
PANORAMIC_ASPECT_RATIO = 2.0
PANORAMIC_CROP_FRACTION = 0.42
UNCERTAIN_CROP_FRACTION = 0.56
REGIONAL_DECODE_LONG_EDGE = TARGET_SIZE * 3


@dataclass(frozen=True)
class GloshPrediction:
    action: str
    reason: str
    full_probability: float
    regional_probabilities: tuple[float, ...]
    maximum_probability: float
    inference_count: int
    elapsed_ms: float
    source_width: int
    source_height: int
    policy_version: str = "dag-36"

    def to_dict(self) -> dict[str, Any]:
        payload = asdict(self)
        payload["regional_probabilities"] = list(self.regional_probabilities)
        return payload


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _fit_plan(width: int, height: int) -> tuple[int, int, int, int]:
    scale = min(TARGET_SIZE / width, TARGET_SIZE / height)
    content_width = max(1, min(TARGET_SIZE, int(width * scale + 0.5)))
    content_height = max(1, min(TARGET_SIZE, int(height * scale + 0.5)))
    return (
        content_width,
        content_height,
        (TARGET_SIZE - content_width) // 2,
        (TARGET_SIZE - content_height) // 2,
    )


def _letterbox(image: Image.Image) -> np.ndarray:
    width, height = image.size
    content_width, content_height, offset_x, offset_y = _fit_plan(width, height)
    resized = image.resize(
        (content_width, content_height),
        resample=Image.Resampling.BILINEAR,
    )
    output = Image.new("RGB", (TARGET_SIZE, TARGET_SIZE), color=PADDING_COLOR)
    output.paste(resized, (offset_x, offset_y))
    return np.asarray(output, dtype=np.uint8).copy()


def _crop_starts(long_edge: int, crop_length: int) -> tuple[int, ...]:
    return tuple(dict.fromkeys((0, (long_edge - crop_length) // 2, long_edge - crop_length)))


def _decode_for_regions(image: Image.Image) -> Image.Image:
    width, height = image.size
    long_edge = max(width, height)
    if long_edge <= REGIONAL_DECODE_LONG_EDGE:
        return image.copy()
    scale = REGIONAL_DECODE_LONG_EDGE / long_edge
    decoded_size = (
        max(1, int(width * scale + 0.5)),
        max(1, int(height * scale + 0.5)),
    )
    return image.resize(decoded_size, resample=Image.Resampling.BILINEAR)


def _panoramic_views(image: Image.Image) -> tuple[np.ndarray, list[np.ndarray]]:
    decoded = _decode_for_regions(image)
    width, height = decoded.size
    full = _letterbox(decoded)
    long_edge = max(width, height)
    short_edge = min(width, height)
    if long_edge / short_edge < PANORAMIC_ASPECT_RATIO:
        return full, []

    views: list[np.ndarray] = []
    if width >= height:
        crop_width = max(1, min(width, int(width * PANORAMIC_CROP_FRACTION + 0.5)))
        for left in _crop_starts(width, crop_width):
            views.append(_letterbox(decoded.crop((left, 0, left + crop_width, height))))
    else:
        crop_height = max(1, min(height, int(height * PANORAMIC_CROP_FRACTION + 0.5)))
        for top in _crop_starts(height, crop_height):
            views.append(_letterbox(decoded.crop((0, top, width, top + crop_height))))
    return full, views


def _uncertain_quadrants(full: np.ndarray) -> list[np.ndarray]:
    crop_size = max(1, min(TARGET_SIZE, int(TARGET_SIZE * UNCERTAIN_CROP_FRACTION + 0.5)))
    last_start = TARGET_SIZE - crop_size
    views: list[np.ndarray] = []
    source_offsets = ((0, 0), (last_start, 0), (0, last_start), (last_start, last_start))
    source_axis = np.minimum(
        (np.arange(TARGET_SIZE, dtype=np.int32) * crop_size) // TARGET_SIZE,
        crop_size - 1,
    )
    for left, top in source_offsets:
        ys = top + source_axis
        xs = left + source_axis
        views.append(full[np.ix_(ys, xs)].copy())
    return views


def _normalize(image: np.ndarray) -> np.ndarray:
    values = image.astype(np.float32) / np.float32(255.0)
    values = (values - IMAGE_MEAN) / IMAGE_STD
    return np.transpose(values, (2, 0, 1))[np.newaxis, ...].astype(np.float32)


def _load_static_rgb(path: Path) -> Image.Image:
    with Image.open(path) as opened:
        if getattr(opened, "n_frames", 1) != 1:
            raise ValueError("animated_image")
        width, height = opened.size
        if (
            width <= 0
            or height <= 0
            or width > MAX_DIMENSION
            or height > MAX_DIMENSION
            or width * height > MAX_PIXELS
        ):
            raise ValueError("unsafe_dimensions")
        return ImageOps.exif_transpose(opened).convert("RGB")


def action_from_scores(
    full_probability: float,
    regional_probabilities: Sequence[float] = (),
    *,
    panoramic: bool = False,
) -> str:
    """Evaluate already-produced scores with DAG 36's decision thresholds."""
    scores = (full_probability, *regional_probabilities)
    if any(not np.isfinite(value) or value < 0.0 or value > 1.0 for value in scores):
        raise ValueError("invalid_model_output")
    if full_probability >= FILTER_THRESHOLD:
        return "filter"
    if panoramic:
        votes = 0
        for probability in regional_probabilities:
            if probability >= REGIONAL_THRESHOLD:
                votes += 1
            if probability >= REGIONAL_STRONG_THRESHOLD or votes >= REGIONAL_CONSENSUS:
                return "filter"
        return "allow"
    if full_probability >= UNCERTAIN_REVIEW_FLOOR:
        return (
            "filter"
            if any(
                probability >= UNCERTAIN_REGION_THRESHOLD
                for probability in regional_probabilities
            )
            else "allow"
        )
    return "allow"
