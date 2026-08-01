#!/usr/bin/env python3
"""Build a compact JSON summary from one DAG controlled-fixture run."""

from __future__ import annotations

import argparse
import json
import math
import re
import statistics
from collections import Counter, defaultdict
from pathlib import Path


KEY_VALUE = re.compile(r"([a-z_]+)=([^\s]+)")
PERFORMANCE = re.compile(r"navigation=(\d+) metric=([a-z_]+) elapsed_ms=(\d+)")
EXIT_RECORD_START = re.compile(r"Historical Process Exit|ApplicationExitInfo", re.IGNORECASE)
EXIT_REASON = re.compile(r"\breason\s*[=:]\s*(\d+)(?:\s*\(([^)]+)\))?", re.IGNORECASE)
EXIT_TIMESTAMP = re.compile(r"\btimestamp\s*[=:]\s*(\d{10,13})(?!\d)", re.IGNORECASE)


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="replace") if path.is_file() else ""


def percentile(values: list[float], fraction: float) -> float | None:
    if not values:
        return None
    ordered = sorted(values)
    index = max(0, min(len(ordered) - 1, math.ceil(fraction * len(ordered)) - 1))
    return round(ordered[index], 2)


def distribution(values: list[float]) -> dict[str, float | int | None]:
    return {
        "count": len(values),
        "p50": round(statistics.median(values), 2) if values else None,
        "p90": percentile(values, 0.90),
        "p95": percentile(values, 0.95),
        "max": round(max(values), 2) if values else None,
    }


def parse_key_values(line: str) -> dict[str, str]:
    return dict(KEY_VALUE.findall(line))


def metadata_value(text: str, name: str) -> str | None:
    match = re.search(rf"^{re.escape(name)}=(.*)$", text, re.MULTILINE)
    return match.group(1).strip() if match else None


def trim_logcat_to_run(text: str, run_id: str | None) -> tuple[str, bool]:
    if not run_id:
        return text, False
    lines = text.splitlines()
    marker = f"run_start={run_id}"
    marker_indexes = [
        index
        for index, line in enumerate(lines)
        if "DagPerfHarness" in line and marker in line
    ]
    if not marker_indexes:
        return text, False
    return "\n".join(lines[marker_indexes[-1] + 1 :]), True


def exit_records(text: str) -> list[str]:
    lines = text.splitlines()
    starts = [index for index, line in enumerate(lines) if EXIT_RECORD_START.search(line)]
    if starts:
        records = []
        for position, start in enumerate(starts):
            end = starts[position + 1] if position + 1 < len(starts) else len(lines)
            record = " ".join(part.strip() for part in lines[start:end] if part.strip())
            if record:
                records.append(record)
        return records
    return [line.strip() for line in lines if EXIT_REASON.search(line)]


def exit_timestamp_millis(record: str) -> int | None:
    match = EXIT_TIMESTAMP.search(record)
    if not match:
        return None
    value = int(match.group(1))
    return value * 1_000 if value < 10_000_000_000 else value


def exit_cause(record: str) -> str:
    reason = EXIT_REASON.search(record)
    code = int(reason.group(1)) if reason else None
    label = reason.group(2).lower() if reason and reason.group(2) else ""
    lowered = record.lower()
    if code == 6 or "anr" in label or re.search(r"\banr\b", lowered):
        return "anr"
    if code == 5 or "native" in label and "crash" in label:
        return "native_crash"
    if code == 4 or "crash" in label or re.search(r"\bcrash\b", lowered):
        return "crash"
    return "other"


def summarize_exit_info(before: str, after: str, run_start_seconds: int | None) -> dict[str, object]:
    before_records = exit_records(before)
    after_records = exit_records(after)
    remaining_before = Counter(before_records)
    new_records = []
    for record in after_records:
        if remaining_before[record] > 0:
            remaining_before[record] -= 1
        else:
            new_records.append(record)
    run_start_millis = run_start_seconds * 1_000 if run_start_seconds is not None else None
    post_start_records = []
    for record in new_records:
        timestamp = exit_timestamp_millis(record)
        if run_start_millis is None or timestamp is None or timestamp >= run_start_millis:
            post_start_records.append(record)
    causes = Counter(exit_cause(record) for record in post_start_records)
    recognized = bool(
        after_records
        or re.search(r"PROCESS EXIT INFO|No historical process exit", after, re.IGNORECASE)
    )
    return {
        "format_recognized": recognized,
        "before_entries": len(before_records),
        "after_entries": len(after_records),
        "new_entries_by_diff": len(new_records),
        "post_start_new_entries": len(post_start_records),
        "causes": {
            "crash": causes["crash"],
            "native_crash": causes["native_crash"],
            "anr": causes["anr"],
            "other": causes["other"],
        },
        "crash_or_anr_entries": causes["crash"] + causes["native_crash"] + causes["anr"],
    }


def parse_int(pattern: str, text: str) -> int | None:
    match = re.search(pattern, text, re.MULTILINE)
    return int(match.group(1).replace(",", "")) if match else None


def parse_float(pattern: str, text: str) -> float | None:
    match = re.search(pattern, text, re.MULTILINE)
    return float(match.group(1)) if match else None


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("run_dir", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    run_dir = args.run_dir.resolve()
    metadata = read(run_dir / "run-metadata.txt")
    raw_logcat = read(run_dir / "logcat.txt")
    run_id = metadata_value(metadata, "run_id")
    logcat, marker_found = trim_logcat_to_run(raw_logcat, run_id)
    am_start = read(run_dir / "am-start.txt")
    gfxinfo = read(run_dir / "gfxinfo-after.txt")
    meminfo = read(run_dir / "meminfo-after.txt")
    exit_info_before = read(run_dir / "exit-info-before.txt")
    exit_info_after = read(run_dir / "exit-info-after.txt")
    try:
        run_start_seconds = int(read(run_dir / "run-start-epoch-seconds.txt").strip())
    except ValueError:
        run_start_seconds = None

    navigation_metrics: dict[int, dict[str, int]] = defaultdict(dict)
    pipeline_values: dict[str, list[float]] = defaultdict(list)
    pipeline_paths: Counter[str] = Counter()
    pipeline_priorities: Counter[str] = Counter()
    client_outcomes: Counter[str] = Counter()
    presentation_bindings: Counter[str] = Counter()
    presentation_matches: list[float] = []
    pipeline_fields = (
        "bridge_ms",
        "queue_ms",
        "capture_ms",
        "fetch_ms",
        "hash_ms",
        "encode_ms",
        "base64_ms",
        "vector_ms",
        "bounds_ms",
        "preprocess_ms",
        "inference_ms",
        "native_ms",
    )
    for line in logcat.splitlines():
        performance = PERFORMANCE.search(line)
        if performance:
            navigation_metrics[int(performance.group(1))][performance.group(2)] = int(performance.group(3))
        if "DagMediaTransport" not in line:
            continue
        fields = parse_key_values(line)
        if " pipeline " in line:
            pipeline_paths[fields.get("path", "unknown")] += 1
            pipeline_priorities[fields.get("priority", "unknown")] += 1
            for name in pipeline_fields:
                try:
                    value = float(fields.get(name, "-1"))
                except ValueError:
                    continue
                if value >= 0:
                    pipeline_values[name].append(value)
        elif " client " in line:
            client_outcomes[fields.get("outcome", "unknown")] += 1
        elif " presentation " in line:
            presentation_bindings[fields.get("binding", "unknown")] += 1
            try:
                matched = float(fields.get("matched", "-1"))
            except ValueError:
                matched = -1
            if matched >= 0:
                presentation_matches.append(matched)

    events = []
    event_path = run_dir / "fixture-events.jsonl"
    if event_path.is_file():
        for line in event_path.read_text(encoding="utf-8", errors="replace").splitlines():
            try:
                events.append(json.loads(line))
            except json.JSONDecodeError:
                pass

    summary = {
        "schema_version": "dag-controlled-perf-run-v1",
        "run_dir": str(run_dir),
        "logcat_window": {
            "marker_found": marker_found,
            "since": read(run_dir / "logcat-since.txt").strip() or None,
        },
        "fixture_reached": bool(events),
        "fixture_events": events,
        "am_start": {
            "this_time_ms": parse_int(r"^ThisTime:\s*(\d+)", am_start),
            "total_time_ms": parse_int(r"^TotalTime:\s*(\d+)", am_start),
            "wait_time_ms": parse_int(r"^WaitTime:\s*(\d+)", am_start),
        },
        "dag_performance_by_navigation": {str(key): value for key, value in sorted(navigation_metrics.items())},
        "media_pipeline": {
            "paths": dict(pipeline_paths),
            "priorities": dict(pipeline_priorities),
            "client_outcomes": dict(client_outcomes),
            "stages_ms": {name: distribution(values) for name, values in pipeline_values.items()},
            "presentation_bindings": dict(presentation_bindings),
            "presentation_matches": distribution(presentation_matches),
        },
        "gfxinfo": {
            "total_frames": parse_int(r"Total frames rendered:\s*([\d,]+)", gfxinfo),
            "janky_frames": parse_int(r"Janky frames:\s*([\d,]+)", gfxinfo),
            "janky_percent": parse_float(r"Janky frames:\s*[\d,]+\s*\(([\d.]+)%\)", gfxinfo),
            "p50_ms": parse_int(r"50th percentile:\s*(\d+)ms", gfxinfo),
            "p90_ms": parse_int(r"90th percentile:\s*(\d+)ms", gfxinfo),
            "p95_ms": parse_int(r"95th percentile:\s*(\d+)ms", gfxinfo),
            "p99_ms": parse_int(r"99th percentile:\s*(\d+)ms", gfxinfo),
        },
        "memory_kb": {
            "total_pss": parse_int(r"TOTAL PSS:\s*([\d,]+)", meminfo),
            "total_rss": parse_int(r"TOTAL RSS:\s*([\d,]+)", meminfo),
        },
        "exit_info": summarize_exit_info(
            exit_info_before,
            exit_info_after,
            run_start_seconds,
        ),
    }
    output = args.output or (run_dir / "summary.json")
    output.write_text(json.dumps(summary, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(output)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
