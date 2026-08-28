#!/usr/bin/env python3
"""Aggregate-only detector for the H18 laboratory lattice and audit placeholder."""

from __future__ import annotations

import hashlib
import json
import statistics
import sys
from pathlib import Path

from PIL import Image


RASTER_MASK = 0xD3A5C69E5A3C96E1
TARGETS = {
    "red": (220, 20, 48),
    "black": (0, 0, 0),
    "yellow": (255, 238, 0),
    "cyan": (0, 200, 255),
    "audit_gray": (55, 65, 81),
}
TOLERANCE = 12


def near(pixel: tuple[int, int, int], target: tuple[int, int, int], tolerance: int = TOLERANCE) -> bool:
    return all(abs(channel - expected) <= tolerance for channel, expected in zip(pixel, target))


def median_patch(image: Image.Image, x: int, y: int, radius: int = 2) -> tuple[int, int, int]:
    values = [[], [], []]
    for sample_y in range(max(0, y - radius), min(image.height, y + radius + 1)):
        for sample_x in range(max(0, x - radius), min(image.width, x + radius + 1)):
            pixel = image.getpixel((sample_x, sample_y))
            for channel in range(3):
                values[channel].append(pixel[channel])
    return tuple(int(statistics.median(channel)) for channel in values)


def raster_result(image: Image.Image, yellow_points: list[tuple[int, int]]) -> dict[str, object]:
    if not yellow_points:
        return {"present": False, "correctCells": 0, "bbox": None}
    left = min(point[0] for point in yellow_points)
    top = min(point[1] for point in yellow_points)
    right = max(point[0] for point in yellow_points)
    bottom = max(point[1] for point in yellow_points)
    width = right - left + 1
    height = bottom - top + 1
    if width < 128 or height < 128:
        return {"present": False, "correctCells": 0, "bbox": [left, top, right, bottom]}

    border = max(1, round(min(width, height) * 8 / 272))
    inner_left = left + border
    inner_top = top + border
    inner_width = max(8, width - 2 * border)
    inner_height = max(8, height - 2 * border)
    correct = 0
    observed = []
    for index in range(64):
        row, column = divmod(index, 8)
        x = round(inner_left + (column + 0.5) * inner_width / 8)
        y = round(inner_top + (row + 0.5) * inner_height / 8)
        pixel = median_patch(image, x, y)
        expected = TARGETS["red"] if (RASTER_MASK >> (63 - index)) & 1 else TARGETS["black"]
        matches = near(pixel, expected)
        correct += int(matches)
        observed.append("1" if near(pixel, TARGETS["red"]) else "0" if near(pixel, TARGETS["black"]) else "?")
    return {
        "present": correct >= 60,
        "correctCells": correct,
        "bbox": [left, top, right, bottom],
        "observedBits": "".join(observed),
    }


def main(path_value: str) -> None:
    path = Path(path_value)
    raw = path.read_bytes()
    image = Image.open(path).convert("RGB")
    counts = {name: 0 for name in TARGETS}
    yellow_points: list[tuple[int, int]] = []
    for y in range(image.height):
        for x in range(image.width):
            pixel = image.getpixel((x, y))
            for name, target in TARGETS.items():
                if near(pixel, target):
                    counts[name] += 1
                    if name == "yellow":
                        yellow_points.append((x, y))
    result = {
        "fileSha256": hashlib.sha256(raw).hexdigest(),
        "width": image.width,
        "height": image.height,
        "colorCounts": counts,
        "auditPlaceholderObserved": counts["cyan"] >= 500 and counts["audit_gray"] >= 500,
        "domCssRaster": raster_result(image, yellow_points),
    }
    print(json.dumps(result, sort_keys=True, separators=(",", ":")))


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: analyze_h18_frame.py SCREENSHOT.png")
    main(sys.argv[1])
