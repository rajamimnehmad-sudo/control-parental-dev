#!/usr/bin/env python3
"""Detect raw sentinel exposure and protected-surface marker continuity in a recording."""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import sys

import cv2
import numpy as np


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser()
    p.add_argument("video", type=Path)
    p.add_argument("--skip-seconds", type=float, default=0.0)
    p.add_argument("--min-sentinel-pixels", type=int, default=150)
    p.add_argument("--min-marker-pixels", type=int, default=120)
    p.add_argument("--samples-dir", type=Path, default=None)
    return p.parse_args()


def masks(frame: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
    # OpenCV uses BGR. Thresholds intentionally tolerate H.264 compression.
    b, g, r = cv2.split(frame)
    magenta = (r > 185) & (b > 185) & (g < 105)
    lime = (g > 185) & (r < 105) & (b < 105)
    sentinel = magenta | lime
    # DEV compositor marker is #00C8FF (RGB), i.e. BGR ~= [255, 200, 0].
    marker = (b > 185) & (g > 120) & (r < 105)
    return sentinel, marker


def main() -> int:
    args = parse_args()
    cap = cv2.VideoCapture(str(args.video))
    if not cap.isOpened():
        print(f"ERROR: cannot open {args.video}", file=sys.stderr)
        return 2
    fps = cap.get(cv2.CAP_PROP_FPS)
    if not fps or not np.isfinite(fps) or fps <= 0:
        fps = 30.0
    skip_frames = int(round(args.skip_seconds * fps))
    samples_dir = args.samples_dir
    if samples_dir:
        samples_dir.mkdir(parents=True, exist_ok=True)

    checked = 0
    sentinel_failures: list[dict] = []
    marker_failures: list[dict] = []
    frame_index = -1
    while True:
        ok, frame = cap.read()
        if not ok:
            break
        frame_index += 1
        if frame_index < skip_frames:
            continue
        checked += 1
        sentinel, marker = masks(frame)
        sentinel_pixels = int(np.count_nonzero(sentinel))
        marker_pixels = int(np.count_nonzero(marker))
        timestamp = frame_index / fps
        failed = False
        if sentinel_pixels >= args.min_sentinel_pixels:
            sentinel_failures.append({"frame": frame_index, "time_s": round(timestamp, 4), "pixels": sentinel_pixels})
            failed = True
        if marker_pixels < args.min_marker_pixels:
            marker_failures.append({"frame": frame_index, "time_s": round(timestamp, 4), "pixels": marker_pixels})
            failed = True
        if failed and samples_dir and len(list(samples_dir.glob("failure-*.png"))) < 8:
            cv2.imwrite(str(samples_dir / f"failure-{frame_index:06d}.png"), frame)

    cap.release()
    summary = {
        "video": str(args.video),
        "fps": round(float(fps), 3),
        "checked_frames": checked,
        "skip_seconds": args.skip_seconds,
        "sentinel_exposure_frames": len(sentinel_failures),
        "surface_marker_missing_frames": len(marker_failures),
        "first_sentinel_failures": sentinel_failures[:10],
        "first_marker_failures": marker_failures[:10],
    }
    passed = checked > 0 and not sentinel_failures and not marker_failures
    summary["result"] = "PASS" if passed else "FAIL"
    print(json.dumps(summary, indent=2))
    return 0 if passed else 1


if __name__ == "__main__":
    raise SystemExit(main())
