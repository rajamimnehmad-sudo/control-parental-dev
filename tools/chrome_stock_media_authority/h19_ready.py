"""Pure current-binding policy for the bounded H19 physical harness."""

from __future__ import annotations

from typing import Any


def ready_baseline(summary: dict[str, Any]) -> dict[str, Any]:
    marker = summary.get("currentReadyBinding")
    return {
        "releaseCount": int(summary.get("readyPhases", {}).get("ready_foreground_released", 0)),
        "anchorRebindCount": int(summary.get("readyExactAnchorRebind", {}).get("maxCount", 0)),
        "marker": marker if isinstance(marker, dict) else None,
    }


def current_ready_result(
    summary: dict[str, Any],
    expected_package: str,
    minimum_release_count: int,
    previous_marker: dict[str, Any] | None,
    require_advance: bool,
) -> dict[str, Any] | None:
    marker = summary.get("currentReadyBinding")
    if not isinstance(marker, dict) or marker.get("package") != expected_package:
        return None
    fields = summary.get("status", {}).get("fields", {})
    release_count = int(summary.get("readyPhases", {}).get("ready_foreground_released", 0))
    marker_advanced = previous_marker is None or any(
        marker.get(field) != previous_marker.get(field)
        for field in (
            "documentSequence",
            "tokenDigestPrefix",
            "lifecycle",
            "windowId",
            "webRootDigestPrefix",
            "surfaceEpoch",
        )
    )
    if (
        fields.get("active") != "true"
        or fields.get("lifecycle") != "PresentationReady"
        or release_count < minimum_release_count
        or (require_advance and not marker_advanced)
    ):
        return None
    return {
        "pass": True,
        "marker": marker,
        "lifecycle": fields.get("lifecycle"),
        "releaseCountInPhase": release_count,
        "markerAdvanced": marker_advanced,
        "acceptedCurrentBinding": not require_advance,
    }
