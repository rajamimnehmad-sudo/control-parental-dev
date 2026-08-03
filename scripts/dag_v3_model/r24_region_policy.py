#!/usr/bin/env python3
"""Mirror DAG's regional image views and binary decision policy in Python."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any

from pilot_tinyclip_candidate import _dag_letterbox_image


TARGET_SIZE = 224
EXTREME_ASPECT_RATIO = 2.0
LONG_EDGE_CROP_FRACTION = 0.42
QUADRANT_CROP_FRACTION = 0.56
FULL_FILTER_THRESHOLD = 0.40
UNCERTAIN_REVIEW_FLOOR = 0.30
UNCERTAIN_REGION_THRESHOLD = 0.45
REGION_FILTER_THRESHOLD = 0.50
REGION_STRONG_THRESHOLD = 0.70


@dataclass(frozen=True)
class RegionViews:
    images: tuple[Any, ...]
    kind: str


def _crop_starts(length: int, crop_length: int) -> tuple[int, ...]:
    return tuple(dict.fromkeys((0, (length - crop_length) // 2, length - crop_length)))


def dag_region_views(image: Any) -> RegionViews:
    """Return the full image plus the exact bounded views used by DAG."""
    from PIL import Image

    width, height = image.size
    if width <= 0 or height <= 0:
        raise ValueError("image dimensions must be positive")
    long_edge = max(width, height)
    short_edge = min(width, height)
    if long_edge / short_edge >= EXTREME_ASPECT_RATIO:
        scale = min(1.0, (TARGET_SIZE * 3) / long_edge)
        decoded_size = (max(1, round(width * scale)), max(1, round(height * scale)))
        decoded = image.resize(decoded_size, resample=Image.Resampling.BILINEAR)
        decoded_width, decoded_height = decoded.size
        full = _dag_letterbox_image(decoded)
        if decoded_width >= decoded_height:
            crop_width = max(1, min(decoded_width, round(decoded_width * LONG_EDGE_CROP_FRACTION)))
            crops = (
                decoded.crop((left, 0, left + crop_width, decoded_height))
                for left in _crop_starts(decoded_width, crop_width)
            )
        else:
            crop_height = max(1, min(decoded_height, round(decoded_height * LONG_EDGE_CROP_FRACTION)))
            crops = (
                decoded.crop((0, top, decoded_width, top + crop_height))
                for top in _crop_starts(decoded_height, crop_height)
            )
        return RegionViews((full, *(_dag_letterbox_image(crop) for crop in crops)), "extreme")

    import numpy as np

    full = _dag_letterbox_image(image)
    full_pixels = np.asarray(full)
    crop_size = max(1, min(TARGET_SIZE, round(TARGET_SIZE * QUADRANT_CROP_FRACTION)))
    last_start = TARGET_SIZE - crop_size
    indices = np.minimum(np.arange(TARGET_SIZE) * crop_size // TARGET_SIZE, crop_size - 1)
    quadrants = tuple(
        Image.fromarray(
            full_pixels[
                top + indices[:, np.newaxis],
                left + indices[np.newaxis, :],
            ]
        )
        for left, top in ((0, 0), (last_start, 0), (0, last_start), (last_start, last_start))
    )
    return RegionViews((full, *quadrants), "uncertain")


def policy_margin(probabilities: Any, kind: str) -> Any:
    """Differentiable distance from DAG's filter boundary.

    Positive values mean filter and negative values mean allow. ``probabilities``
    can be a Python sequence or a one-dimensional torch tensor.
    """
    full = probabilities[0]
    regions = probabilities[1:]
    if not len(regions):
        return full - FULL_FILTER_THRESHOLD
    if kind == "uncertain":
        region_max = max(regions) if isinstance(regions, (list, tuple)) else regions.max()
        regional_path = min(full - UNCERTAIN_REVIEW_FLOOR, region_max - UNCERTAIN_REGION_THRESHOLD)
        return max(full - FULL_FILTER_THRESHOLD, regional_path)
    if kind != "extreme":
        raise ValueError(f"unknown regional view kind: {kind}")
    ordered = sorted(regions, reverse=True) if isinstance(regions, (list, tuple)) else regions.topk(2).values
    strongest = ordered[0]
    second = ordered[1]
    regional_path = max(strongest - REGION_STRONG_THRESHOLD, second - REGION_FILTER_THRESHOLD)
    return max(full - FULL_FILTER_THRESHOLD, regional_path)


def exact_policy_decision(probabilities: list[float], kind: str) -> dict[str, Any]:
    if not probabilities or any(not 0.0 <= value <= 1.0 for value in probabilities):
        raise ValueError("probabilities must be finite values between zero and one")
    full = probabilities[0]
    regions = probabilities[1:]
    if full >= FULL_FILTER_THRESHOLD:
        return {"action": "filter", "maximum_probability": max(probabilities), "inferences": 1}
    if kind == "uncertain" and full < UNCERTAIN_REVIEW_FLOOR:
        return {"action": "allow", "maximum_probability": full, "inferences": 1}
    votes = 0
    maximum = full
    for index, probability in enumerate(regions, start=2):
        maximum = max(maximum, probability)
        if probability >= REGION_FILTER_THRESHOLD:
            votes += 1
        if kind == "uncertain" and probability >= UNCERTAIN_REGION_THRESHOLD:
            return {"action": "filter", "maximum_probability": maximum, "inferences": index}
        if kind == "extreme" and (probability >= REGION_STRONG_THRESHOLD or votes >= 2):
            return {"action": "filter", "maximum_probability": maximum, "inferences": index}
    return {"action": "allow", "maximum_probability": maximum, "inferences": 1 + len(regions)}
