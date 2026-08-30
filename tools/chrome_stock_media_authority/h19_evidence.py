#!/usr/bin/env python3
"""Aggregate-only H19 frame, Logcat and Accessibility evidence helpers."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import shutil
import subprocess
import xml.etree.ElementTree as ET
from collections import Counter
from pathlib import Path
from typing import Any, Iterable

TARGETS = {
    "sentinel_red": (220, 20, 48),
    "sentinel_black": (0, 0, 0),
    "audit_cyan": (0, 200, 255),
    "audit_gray": (55, 65, 81),
    "blocked_gray": (92, 100, 108),
    "safe_blue": (70, 155, 210),
    "safe_green": (37, 120, 64),
    "safe_yellow": (235, 210, 96),
    "opaque_surface": (32, 33, 36),
}
TOLERANCE = 12
READY_PREFIX = "glosh-shield-ready:"
STATUS_FINAL_FIELD = "audit17Dropped"
STRUCTURED_STATUS_KINDS = {"network", "document", "health", "fixture"}
COUNTER_FIELDS = (
    "networkVisualCandidates",
    "networkVisualReplaced",
    "networkVisualRawDelivered",
    "networkVisualSafeRawDelivered",
    "networkVisualBlockedReplaced",
    "networkVisualUnknownReplaced",
    "networkVisualUnsupportedReplaced",
    "networkVisualRawBlockedDelivered",
    "networkVisualRawUnknownDelivered",
    "networkVisualCacheHit",
    "networkVisualInference",
    "networkVisualEngineCalls",
    "mediaDocumentsTransformed",
    "mediaDocumentsFailClosed",
    "proxyQueueRejects",
    "protectFailure",
    "quicAttempts",
    "directTcpAttempts",
)
FRAME_SCENARIOS = {
    "frame-data-img",
    "frame-blob-img",
    "frame-canvas",
    "frame-service-worker",
    "frame-closed-shadow",
}
READY_BINDING_KIND = "event_source"
READY_TIMELINE_LIMIT = 128
SURFACE_TIMELINE_LIMIT = 128
MANAGED_POLICY_NAMES = {
    "AIModeSettings",
    "AllowBackForwardCacheForCacheControlNoStorePageEnabled",
    "BackForwardCacheEnabled",
    "DataUrlInSvgUseEnabled",
    "FindsSettings",
    "ForceGoogleSafeSearch",
    "IncognitoModeAvailability",
    "NTPContentSuggestionsEnabled",
    "SearchContentSharingSettings",
    "SearchSuggestEnabled",
    "URLAllowlist",
    "URLBlocklist",
}


def near(pixel: tuple[int, int, int], target: tuple[int, int, int]) -> bool:
    return all(abs(channel - expected) <= TOLERANCE for channel, expected in zip(pixel, target))


def _bbox(points: list[tuple[int, int]]) -> list[int] | None:
    if not points:
        return None
    return [
        min(point[0] for point in points),
        min(point[1] for point in points),
        max(point[0] for point in points),
        max(point[1] for point in points),
    ]


def _pixels_in_box(
    pixels: bytes,
    width: int,
    box: list[int],
    target: tuple[int, int, int],
) -> int:
    left, top, right, bottom = box
    count = 0
    for y in range(top, bottom + 1):
        for x in range(left, right + 1):
            offset = (y * width + x) * 3
            if near((pixels[offset], pixels[offset + 1], pixels[offset + 2]), target):
                count += 1
    return count


def _decode_rgb(path: Path, sample_scale: int) -> tuple[int, int, int, int, bytes]:
    ffmpeg, ffprobe = shutil.which("ffmpeg"), shutil.which("ffprobe")
    if not ffmpeg or not ffprobe:
        raise RuntimeError("ffmpeg and ffprobe are required for aggregate frame analysis")
    probe = subprocess.run(
        [ffprobe, "-v", "error", "-select_streams", "v:0", "-show_entries", "stream=width,height", "-of", "json", str(path)],
        check=True,
        capture_output=True,
        text=True,
        timeout=30,
    )
    stream = json.loads(probe.stdout)["streams"][0]
    original_width, original_height = int(stream["width"]), int(stream["height"])
    width, height = max(1, original_width // sample_scale), max(1, original_height // sample_scale)
    decoded = subprocess.run(
        [
            ffmpeg, "-v", "error", "-i", str(path), "-frames:v", "1", "-vf", f"scale={width}:{height}:flags=neighbor",
            "-f", "rawvideo", "-pix_fmt", "rgb24", "pipe:1",
        ],
        check=True,
        capture_output=True,
        timeout=30,
    ).stdout
    if len(decoded) != width * height * 3:
        raise RuntimeError("decoded RGB byte length does not match probed dimensions")
    return original_width, original_height, width, height, decoded


def analyze_image(path: Path, sample_scale: int = 2) -> dict[str, Any]:
    """Analyze known fixture signatures without retaining or emitting raw pixels."""
    if sample_scale < 1 or sample_scale > 8:
        raise ValueError("sample_scale must be in 1..8")
    raw = path.read_bytes()
    original_width, original_height, width, height, pixels = _decode_rgb(path, sample_scale)

    counts = Counter({name: 0 for name in TARGETS})
    points: dict[str, list[tuple[int, int]]] = {name: [] for name in TARGETS}
    for y in range(height):
        for x in range(width):
            offset = (y * width + x) * 3
            pixel = (pixels[offset], pixels[offset + 1], pixels[offset + 2])
            for name, target in TARGETS.items():
                if near(pixel, target):
                    counts[name] += 1
                    points[name].append((x, y))

    red_box = _bbox(points["sentinel_red"])
    sentinel_black_inside = 0
    sentinel_span = [0, 0]
    if red_box:
        sentinel_black_inside = _pixels_in_box(pixels, width, red_box, TARGETS["sentinel_black"])
        sentinel_span = [red_box[2] - red_box[0] + 1, red_box[3] - red_box[1] + 1]
    sentinel_like = bool(
        red_box
        and counts["sentinel_red"] >= 150
        and sentinel_black_inside >= 100
        and sentinel_span[0] >= 40
        and sentinel_span[1] >= 25
    )
    audit_box = _bbox(points["audit_cyan"])
    audit_gray_inside = _pixels_in_box(pixels, width, audit_box, TARGETS["audit_gray"]) if audit_box else 0
    audit_placeholder = bool(
        audit_box
        and counts["audit_cyan"] >= 150
        and audit_gray_inside >= 100
        and audit_box[2] - audit_box[0] + 1 >= 40
        and audit_box[3] - audit_box[1] + 1 >= 25
    )
    safe_fixture = (
        counts["safe_blue"] >= 150
        and counts["safe_green"] >= 80
        and counts["safe_yellow"] >= 20
    )
    blocked_placeholder = counts["blocked_gray"] >= 300
    opaque_surface = counts["opaque_surface"] >= width * height // 5
    return {
        "fileSha256": hashlib.sha256(raw).hexdigest(),
        "width": original_width,
        "height": original_height,
        "analysisWidth": width,
        "analysisHeight": height,
        "sampleScale": sample_scale,
        "colorSamples": dict(sorted(counts.items())),
        "colorBboxes": {name: _bbox(value) for name, value in sorted(points.items())},
        "auditPlaceholderObserved": audit_placeholder,
        "controlledSentinelLikeObserved": sentinel_like,
        "sentinelBlackInsideRedBbox": sentinel_black_inside,
        "safeFixturePaletteObserved": safe_fixture,
        "blockedPlaceholderObserved": blocked_placeholder,
        "opaqueSurfaceObserved": opaque_surface,
    }


def summarize_frames(paths: Iterable[Path], sample_scale: int = 2) -> dict[str, Any]:
    frames = []
    for index, path in enumerate(sorted(paths)):
        result = analyze_image(path, sample_scale=sample_scale)
        result["index"] = index
        result["name"] = path.name
        frames.append(result)

    def indexes(field: str) -> list[int]:
        return [int(frame["index"]) for frame in frames if frame[field]]

    return {
        "frameCount": len(frames),
        "auditPlaceholderVisibleFrames": indexes("auditPlaceholderObserved"),
        "controlledSentinelLikeVisibleFrames": indexes("controlledSentinelLikeObserved"),
        "safeFixtureVisibleFrames": indexes("safeFixturePaletteObserved"),
        "blockedPlaceholderVisibleFrames": indexes("blockedPlaceholderObserved"),
        "opaqueSurfaceVisibleFrames": indexes("opaqueSurfaceObserved"),
        "frames": frames,
    }


def _key_values(value: str) -> dict[str, str]:
    return {key: item for key, item in re.findall(r"(?:^|\s)([A-Za-z][A-Za-z0-9]*)=([^\s]+)", value)}


def _canonical_outcomes(value: str) -> dict[str, str] | None:
    if not value or value in {"not_run", "invalid"}:
        return None
    outcomes: dict[str, str] = {}
    for item in value.split(","):
        key, separator, verdict = item.partition("=")
        if not separator or not key or not verdict or key in outcomes:
            return None
        outcomes[key] = verdict
    return outcomes


def parse_fixture_report(value: str) -> dict[str, Any]:
    """Parse the bounded H19 state string without retaining any media bytes."""
    pattern = re.compile(
        r"^REPORT=(?P<report>.*?),DOCUMENTS=(?P<documents>\d+),FRAMES=(?P<frames>\d+),"
        r"SCRIPTS=(?P<scripts>\d+),STYLES=(?P<styles>\d+),WORKERS=(?P<workers>\d+),"
        r"SERVICE_WORKERS=(?P<service_workers>\d+),FRAME_REPORTS=(?P<frame_reports>\d+),"
        r"FRAME_REPORT_REJECTS=(?P<frame_report_rejects>\d+),FRAME_REPORT=(?P<frame_report>.*?),"
        r"FRAME_REPORT_SHA=(?P<frame_report_sha>[0-9a-f]{64}|not_run),"
        r"FRAME_CHALLENGE_SHA=(?P<frame_challenge_sha>[0-9a-f]{64}|not_run),"
        r"FRAME_GENERATION=(?P<frame_generation>\d+),"
        r"FRAME_ACCEPTED_CHALLENGE_SHA=(?P<frame_accepted_challenge_sha>[0-9a-f]{64}|not_run),"
        r"FRAME_REPORT_BINDING_SHA=(?P<frame_report_binding_sha>[0-9a-f]{64}|not_run),"
        r"SAME_URL_BODIES=(?P<same_url_bodies>\d+),REPORTS=(?P<reports>\d+)$"
    )
    match = pattern.fullmatch(value)
    if not match:
        return {"parsed": False, "pass": False, "reasons": ["malformed_fixture_report"]}
    fields = match.groupdict()
    report = _canonical_outcomes(fields["report"])
    frame_report = _canonical_outcomes(fields["frame_report"])
    reasons: list[str] = []
    if report is None:
        reasons.append("main_report_missing_or_invalid")
    elif any(verdict == "ERROR" for verdict in report.values()):
        reasons.append("main_report_contains_error")
    if frame_report is None:
        reasons.append("frame_report_missing_or_invalid")
    else:
        if set(frame_report) != FRAME_SCENARIOS:
            reasons.append("frame_report_scenario_set_mismatch")
        if any(verdict != "BLOCKED" for verdict in frame_report.values()):
            reasons.append("frame_report_not_all_blocked")
    numeric = {
        key: int(fields[key])
        for key in (
            "documents",
            "frames",
            "scripts",
            "styles",
            "workers",
            "service_workers",
            "frame_reports",
            "frame_report_rejects",
            "frame_generation",
            "same_url_bodies",
            "reports",
        )
    }
    if numeric["frame_reports"] < 1:
        reasons.append("no_accepted_frame_report")
    elif fields["frame_report_sha"] == "not_run":
        reasons.append("frame_report_digest_missing")
    if numeric["frame_report_rejects"] != 0:
        reasons.append("frame_report_rejected")
    if numeric["frame_generation"] < 1:
        reasons.append("frame_generation_missing")
    if fields["frame_challenge_sha"] == "not_run":
        reasons.append("frame_challenge_missing")
    if fields["frame_accepted_challenge_sha"] != fields["frame_challenge_sha"]:
        reasons.append("frame_accepted_challenge_mismatch")
    if fields["frame_report_binding_sha"] == "not_run":
        reasons.append("frame_report_binding_missing")
    if numeric["reports"] < 1:
        reasons.append("no_accepted_main_report")
    return {
        "parsed": True,
        "pass": not reasons,
        "reasons": reasons,
        "counts": numeric,
        "mainOutcomeCount": len(report or {}),
        "mainErrorCount": sum(verdict == "ERROR" for verdict in (report or {}).values()),
        "frameOutcomes": dict(sorted((frame_report or {}).items())),
        "frameReportSha256": fields["frame_report_sha"],
        "frameChallengeSha256": fields["frame_challenge_sha"],
        "frameGeneration": numeric["frame_generation"],
        "frameAcceptedChallengeSha256": fields["frame_accepted_challenge_sha"],
        "frameReportBindingSha256": fields["frame_report_binding_sha"],
    }


def status_counter_snapshot(summary: dict[str, Any]) -> dict[str, int]:
    fields = summary.get("status", {}).get("fields", {})
    return {
        field: int(fields[field])
        for field in COUNTER_FIELDS
        if isinstance(fields.get(field), str) and fields[field].isdigit()
    }


def counter_deltas(previous: dict[str, int], current: dict[str, int]) -> dict[str, int | None]:
    return {
        field: current[field] - previous[field] if field in previous and field in current else None
        for field in COUNTER_FIELDS
    }


def latest_status(logcat: str) -> dict[str, Any]:
    lines = [line for line in logcat.splitlines() if "phase=status " in line]
    if not lines:
        return {"present": False, "complete": False, "fields": {}}
    line = lines[-1].split("phase=status ", 1)[1]
    fields = _key_values("phase=status " + line)
    return {
        "present": True,
        "complete": STATUS_FINAL_FIELD in fields,
        "fieldCount": len(fields),
        "fields": fields,
        "lineSha256": hashlib.sha256(line.encode()).hexdigest(),
    }


def latest_structured_status(logcat: str) -> dict[str, Any]:
    records: list[tuple[int, dict[str, dict[str, str]]]] = []
    current_sequence: int | None = None
    current_kinds: dict[str, dict[str, str]] = {}
    for line in logcat.splitlines():
        if "ChromeMediaShieldStatus" not in line or "v=1 " not in line:
            continue
        fields = _key_values(line)
        sequence, kind = fields.get("seq", ""), fields.get("kind", "")
        if not sequence.isdigit() or kind not in STRUCTURED_STATUS_KINDS:
            continue
        parsed_sequence = int(sequence)
        starts_new_record = current_sequence != parsed_sequence or (kind == "network" and kind in current_kinds)
        if starts_new_record:
            if current_sequence is not None:
                records.append((current_sequence, current_kinds))
            current_sequence = parsed_sequence
            current_kinds = {}
        current_kinds[kind] = fields
    if current_sequence is not None:
        records.append((current_sequence, current_kinds))
    if not records:
        return {"present": False, "complete": False, "sequence": 0, "kinds": {}, "fields": {}}
    complete_records = [record for record in records if STRUCTURED_STATUS_KINDS.issubset(record[1])]
    sequence, kinds = (complete_records or records)[-1]
    network = kinds.get("network", {})
    health = kinds.get("health", {})
    document = kinds.get("document", {})
    canonical = {
        "networkVisualCandidates": network.get("candidates", ""),
        "networkVisualReplaced": network.get("replaced", ""),
        "networkVisualRawDelivered": network.get("rawDelivered", ""),
        "networkVisualSafeRawDelivered": network.get("safeRaw", ""),
        "networkVisualBlockedReplaced": network.get("blockedReplaced", ""),
        "networkVisualUnknownReplaced": network.get("unknownReplaced", ""),
        "networkVisualUnsupportedReplaced": network.get("unsupportedReplaced", ""),
        "networkVisualRawBlockedDelivered": network.get("rawBlocked", ""),
        "networkVisualRawUnknownDelivered": network.get("rawUnknown", ""),
        "networkVisualCacheHit": network.get("cacheHit", ""),
        "networkVisualInference": network.get("decisionEngine", ""),
        "networkVisualEngineCalls": network.get("engineCalls", ""),
        "proxyQueueRejects": health.get("proxyQueueRejects", ""),
        "protectFailure": health.get("protectFailure", ""),
        "quicAttempts": health.get("quicAttempts", ""),
        "directTcpAttempts": health.get("directTcpAttempts", ""),
        "mediaDocumentsTransformed": document.get("transformed", ""),
        "mediaDocumentsFailClosed": document.get("failClosed", ""),
        "documentTransformOutstanding": document.get("outstanding", ""),
        "readyTokensOutstanding": document.get("issued", ""),
    }
    return {
        "present": True,
        "complete": STRUCTURED_STATUS_KINDS.issubset(kinds),
        "sequence": sequence,
        "kinds": {kind: dict(sorted(fields.items())) for kind, fields in sorted(kinds.items())},
        "fields": canonical,
    }


def summarize_logcat(logcat: str) -> dict[str, Any]:
    ready_phases: Counter[str] = Counter()
    ready_reasons: Counter[str] = Counter()
    ready_markers: list[dict[str, Any]] = []
    ready_timeline: list[dict[str, Any]] = []
    current_ready_binding: dict[str, Any] | None = None
    ready_claim_progress: dict[tuple[str, str, str, str], set[str]] = {}
    ready_order_violations = 0
    ready_exact_anchor_rebind_max = 0
    surface_timeline: list[dict[str, Any]] = []
    coverage_events: Counter[str] = Counter()
    for line in logcat.splitlines():
        if "ChromeMediaShieldReady" in line and "phase=" in line:
            fields = _key_values(line)
            phase = _safe_reason(fields.get("phase", ""), fallback="unknown")
            reason = _safe_reason(fields.get("reason", ""), fallback="")
            ready_phases[phase] += 1
            if reason:
                ready_reasons[reason] += 1
            event = _ready_event(phase, reason, fields)
            ready_exact_anchor_rebind_max = max(
                ready_exact_anchor_rebind_max,
                int(event.get("exactAnchorRebindCount", 0)),
            )
            claim_key = _ready_claim_key(event)
            progress = ready_claim_progress.setdefault(claim_key, set())
            if phase == "ready_ack_accepted":
                progress.add("ack")
            elif phase in {"ready_focus_bound", "ready_ax_bound"} and "ack" in progress:
                progress.add("focus")
            ready_timeline.append(event)
            if phase == "ready_foreground_released":
                event["orderingVerified"] = {"ack", "focus"}.issubset(progress)
                if not event["orderingVerified"]:
                    ready_order_violations += 1
                ready_markers.append(event)
                current_ready_binding = event if _is_verified_ready_binding(event) else None
            elif phase in {"ready_foreground_revoked", "ready_fail_closed"}:
                current_ready_binding = None
        if "ChromePhotosSurfaceProbe" in line and "phase=" in line:
            surface_timeline.append(_surface_event(_key_values(line)))
        if "audit17 event=" in line:
            match = re.search(r"audit17 event=([^\s]+)", line)
            coverage_events[match.group(1) if match else "unknown"] += 1
    crash_patterns = {
        "fatalException": r"FATAL EXCEPTION",
        "nativeFatalSignal": r"Fatal signal|SIGABRT|SIGSEGV|SIGTRAP",
        "anr": r"\bANR in\b|am_anr",
        "oom": r"OutOfMemoryError|lowmemorykiller|lmkd.*Kill",
    }
    crashes = {
        name: len(re.findall(pattern, logcat, flags=re.IGNORECASE))
        for name, pattern in crash_patterns.items()
    }
    active_lines = [line for line in logcat.splitlines() if "phase=active " in line]
    active_mode = _key_values(active_lines[-1]) if active_lines else {}
    transport_lines = [line for line in logcat.splitlines() if "VpnTransport09A" in line and "status=" in line]
    transport_status = _key_values(transport_lines[-1]) if transport_lines else {}
    legacy_status = latest_status(logcat)
    structured_status = latest_structured_status(logcat)
    status_fields = dict(legacy_status.get("fields", {}))
    status_fields.update(
        {key: value for key, value in structured_status.get("fields", {}).items() if value != ""}
    )
    status = {**legacy_status, "fields": status_fields}
    fixture_value = structured_status.get("kinds", {}).get("fixture", {}).get("report", "")
    fixture_report = parse_fixture_report(fixture_value) if fixture_value else {
        "parsed": False,
        "pass": False,
        "reasons": ["fixture_report_absent"],
    }
    zero_fields = (
        "networkVisualRawBlockedDelivered", "networkVisualRawUnknownDelivered",
        "proxyQueueRejects", "protectFailure", "quicAttempts", "directTcpAttempts",
    )
    return {
        "status": status,
        "structuredStatus": structured_status,
        "activeMode": active_mode,
        "transportStatus": transport_status,
        "counterInvariants": {
            "allAvailable": all(field in status_fields for field in zero_fields),
            "zero": {field: status_fields.get(field) == "0" for field in zero_fields},
        },
        "readyPhases": dict(sorted(ready_phases.items())),
        "readyReasons": dict(sorted(ready_reasons.items())),
        "readyMarkers": ready_markers[-READY_TIMELINE_LIMIT:],
        "readyMarkersDropped": max(0, len(ready_markers) - READY_TIMELINE_LIMIT),
        "readyTimeline": ready_timeline[-READY_TIMELINE_LIMIT:],
        "readyTimelineDropped": max(0, len(ready_timeline) - READY_TIMELINE_LIMIT),
        "currentReadyBinding": current_ready_binding,
        "readyBindingOrder": {
            "releaseCount": len(ready_markers),
            "verifiedReleaseCount": sum(
                1 for marker in ready_markers if _is_verified_ready_binding(marker)
            ),
            "violations": ready_order_violations,
        },
        "readyExactAnchorRebind": {
            "maxCount": ready_exact_anchor_rebind_max,
        },
        "surfaceTimeline": surface_timeline[-SURFACE_TIMELINE_LIMIT:],
        "surfaceTimelineDropped": max(0, len(surface_timeline) - SURFACE_TIMELINE_LIMIT),
        "currentSurfaceState": surface_timeline[-1] if surface_timeline else None,
        "fixtureReport": fixture_report,
        "coverageEvents": dict(sorted(coverage_events.items())),
        "healthSignals": crashes,
        "lineCount": len(logcat.splitlines()),
        "sha256": hashlib.sha256(logcat.encode()).hexdigest(),
    }


def _safe_reason(value: str, fallback: str) -> str:
    return value if re.fullmatch(r"[a-z0-9_]{1,80}", value) else fallback


def _safe_digest_prefix(value: str) -> str:
    return value if re.fullmatch(r"[0-9a-f]{12,64}", value) else ""


def _ready_event(phase: str, reason: str, fields: dict[str, str]) -> dict[str, Any]:
    binding = fields.get("binding", "")
    root_binding = fields.get("rootBinding", "")
    continuity = fields.get("continuity", "")
    raw_presented = fields.get("rawPresented")
    source_current = fields.get("sourceCurrent")
    source_scope = fields.get("sourceEvidenceScope", "")
    exact_anchor_rebinds = fields.get("exactAnchorRebinds", "")
    return {
        "package": "com.android.chrome",
        "phase": phase,
        "windowId": (
            fields.get("windowId", "") if fields.get("windowId", "").lstrip("-").isdigit() else ""
        ),
        "documentSequence": (
            fields.get("documentSequence", "") if fields.get("documentSequence", "").isdigit() else ""
        ),
        "surfaceEpoch": fields.get("surfaceEpoch", "") if fields.get("surfaceEpoch", "").isdigit() else "",
        "lifecycle": fields.get("lifecycle", "") if fields.get("lifecycle", "").isdigit() else "",
        "tokenDigestPrefix": _safe_digest_prefix(fields.get("token", "")),
        "rootDigestPrefix": _safe_digest_prefix(fields.get("root", "")),
        "webRootDigestPrefix": _safe_digest_prefix(fields.get("webRoot", "")),
        "sourceDigestPrefix": _safe_digest_prefix(fields.get("source", "")),
        "sourceCurrent": source_current == "true" if source_current in {"true", "false"} else None,
        "sourceEvidenceScope": (
            source_scope if source_scope in {"current_boundary", "initial_only", "none"} else ""
        ),
        "exactAnchorRebindCount": int(exact_anchor_rebinds) if exact_anchor_rebinds.isdigit() else 0,
        "binding": binding if binding == READY_BINDING_KIND else "",
        "rootBinding": root_binding if root_binding in {"native_root", "web_root"} else "",
        "continuity": continuity if continuity in {"none", "web_root"} else "",
        "axBound": fields.get("axBound") == "true",
        "reason": reason,
        "rawPresented": (
            raw_presented == "true" if raw_presented in {"true", "false"} else None
        ),
    }


def _is_verified_ready_binding(event: dict[str, Any]) -> bool:
    return (
        event.get("phase") == "ready_foreground_released"
        and event.get("package") == "com.android.chrome"
        and event.get("binding") == READY_BINDING_KIND
        and event.get("axBound") is True
        and event.get("rawPresented") is False
        and event.get("orderingVerified") is True
        and bool(event.get("tokenDigestPrefix"))
        and bool(event.get("rootDigestPrefix"))
        and bool(event.get("webRootDigestPrefix"))
        and event.get("rootBinding") in {"native_root", "web_root"}
        and str(event.get("windowId", "")).isdigit()
        and str(event.get("documentSequence", "")).isdigit()
        and str(event.get("surfaceEpoch", "")).isdigit()
        and str(event.get("lifecycle", "")).isdigit()
        and int(str(event.get("lifecycle", "0"))) > 0
        and event.get("sourceCurrent") is True
        and event.get("sourceEvidenceScope") == "current_boundary"
        and bool(event.get("sourceDigestPrefix"))
    )


def _ready_claim_key(event: dict[str, Any]) -> tuple[str, str, str, str]:
    return (
        str(event.get("windowId", "")),
        str(event.get("documentSequence", "")),
        str(event.get("lifecycle", "")),
        str(event.get("tokenDigestPrefix", "")),
    )


def _surface_event(fields: dict[str, str]) -> dict[str, Any]:
    transparent = fields.get("transparent")
    raw_presented = fields.get("rawPresented")
    attachment_count = fields.get("attachmentCount", "")
    return {
        "phase": _safe_reason(fields.get("phase", ""), fallback="unknown"),
        "action": _safe_reason(fields.get("action", ""), fallback=""),
        "reason": _safe_reason(fields.get("reason", ""), fallback=""),
        "windowId": (
            fields.get("windowId", "") if fields.get("windowId", "").lstrip("-").isdigit() else ""
        ),
        "epoch": fields.get("epoch", "") if fields.get("epoch", "").isdigit() else "",
        "transparent": transparent == "true" if transparent in {"true", "false"} else None,
        "attachmentCount": int(attachment_count) if attachment_count.isdigit() else 0,
        "rawPresented": (
            raw_presented == "true" if raw_presented in {"true", "false"} else None
        ),
    }


def summarize_accessibility_xml(xml: str) -> dict[str, Any]:
    """Hash H19 marker tokens and discard all unrelated visible text."""
    start = xml.find("<?xml")
    if start < 0:
        return {"parsed": False, "nodeCount": 0, "packages": {}, "readyMarkers": []}
    end = xml.rfind("</hierarchy>")
    if end < start:
        return {"parsed": False, "nodeCount": 0, "packages": {}, "readyMarkers": []}
    root = ET.fromstring(xml[start : end + len("</hierarchy>")])
    packages: Counter[str] = Counter()
    markers: list[dict[str, Any]] = []
    managed_policies: set[str] = set()
    node_count = 0
    for node in root.iter("node"):
        node_count += 1
        package_name = node.attrib.get("package", "")
        if package_name:
            packages[package_name] += 1
        for attribute in ("content-desc", "text"):
            value = node.attrib.get(attribute, "")
            managed_policies.update(name for name in MANAGED_POLICY_NAMES if name in value)
            marker_at = value.find(READY_PREFIX)
            if marker_at < 0:
                continue
            payload = value[marker_at + len(READY_PREFIX) :].split()[0]
            token, separator, lifecycle = payload.partition(":")
            markers.append(
                {
                    "source": attribute,
                    "tokenSha256": hashlib.sha256(token.encode()).hexdigest(),
                    "lifecycle": lifecycle if separator and lifecycle.isdigit() else "",
                    "package": package_name,
                    "bounds": node.attrib.get("bounds", ""),
                }
            )
    return {
        "parsed": True,
        "nodeCount": node_count,
        "packages": dict(sorted(packages.items())),
        "readyMarkers": markers,
        "managedPolicyNamesObserved": sorted(managed_policies),
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers(dest="command", required=True)
    frame_parser = subparsers.add_parser("frame")
    frame_parser.add_argument("path", type=Path)
    frames_parser = subparsers.add_parser("frames")
    frames_parser.add_argument("directory", type=Path)
    log_parser = subparsers.add_parser("logcat")
    log_parser.add_argument("path", type=Path)
    ui_parser = subparsers.add_parser("accessibility")
    ui_parser.add_argument("path", type=Path)
    args = parser.parse_args()

    if args.command == "frame":
        result = analyze_image(args.path)
    elif args.command == "frames":
        result = summarize_frames(args.directory.glob("*.png"))
    elif args.command == "logcat":
        result = summarize_logcat(args.path.read_text(encoding="utf-8", errors="replace"))
    else:
        result = summarize_accessibility_xml(args.path.read_text(encoding="utf-8", errors="replace"))
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
