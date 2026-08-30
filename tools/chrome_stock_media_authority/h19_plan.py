"""Bounded, data-only execution plan contract for the H19 physical harness."""

from __future__ import annotations

import re
from typing import Any
from urllib.parse import urlsplit


ALLOWED_MODES = {"replace-all", "selective"}
ALLOWED_NAVIGATION = {
    "url",
    "controlled",
    "back",
    "forward",
    "reload",
    "foreground",
    "background-foreground",
    "restart-chrome",
    "restart-glosh",
    "two-tab-binding",
    "chrome-policy",
}
ALLOWED_ORIENTATIONS = {"current", "portrait", "landscape"}
EXPECTED_GLOSHIA_MODEL_VERSION = "R3.1"
EXPECTED_GLOSHIA_MODEL_SHA256 = "c8b64af8092d3718c58736a511c996d0d443dacf3eaa74620b1e5af439a3cd48"
EXPECTED_GLOSHIA_POLICY_VERSION = "dag-36"
READY_OPTIONAL_NAVIGATION = {"chrome-policy"}
NAVIGATION_DEFAULTS_TO_NEW_DOCUMENT = {
    "url",
    "controlled",
    "back",
    "forward",
    "reload",
    "restart-chrome",
    "restart-glosh",
    "two-tab-binding",
    "chrome-policy",
}
AUTOMATIC_AUTHORITY_EVIDENCE_NAVIGATION = {"controlled", "two-tab-binding"}


class HarnessError(RuntimeError):
    pass


def safe_id(value: Any) -> str:
    text = str(value)
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]{0,63}", text):
        raise HarnessError(f"invalid state/phase id: {text!r}")
    return text


def new_navigation_for(state: dict[str, Any]) -> bool:
    explicit = state.get("newNavigation")
    if explicit is not None:
        if not isinstance(explicit, bool):
            raise HarnessError("newNavigation must be boolean")
        return explicit
    return state.get("navigation", "url") in NAVIGATION_DEFAULTS_TO_NEW_DOCUMENT


def navigation_requires_new_release(state: dict[str, Any]) -> bool:
    return state.get("navigation", "url") in NAVIGATION_DEFAULTS_TO_NEW_DOCUMENT


def ready_required_for(state: dict[str, Any]) -> bool:
    explicit = state.get("readyRequired")
    if explicit is not None:
        if not isinstance(explicit, bool):
            raise HarnessError("readyRequired must be boolean")
        return explicit
    return state.get("navigation", "url") not in READY_OPTIONAL_NAVIGATION


def visual_review_required_for(state: dict[str, Any]) -> bool:
    explicit = state.get("visualReviewRequired")
    if explicit is not None:
        if not isinstance(explicit, bool):
            raise HarnessError("visualReviewRequired must be boolean")
        return explicit
    return state.get("navigation", "url") not in AUTOMATIC_AUTHORITY_EVIDENCE_NAVIGATION


def post_gesture_ready_required_for(state: dict[str, Any]) -> bool:
    explicit = state.get("postGestureReadyRequired")
    if explicit is not None:
        if not isinstance(explicit, bool):
            raise HarnessError("postGestureReadyRequired must be boolean")
        return explicit
    return False


def web_root_continuity_required_for(state: dict[str, Any]) -> bool:
    explicit = state.get("webRootContinuityAfterPruneRequired", False)
    if not isinstance(explicit, bool):
        raise HarnessError("webRootContinuityAfterPruneRequired must be boolean")
    return explicit


def validate_plan(raw: Any) -> dict[str, Any]:
    if not isinstance(raw, dict) or raw.get("schema") != "glosh-h19-a23-plan-v1":
        raise HarnessError("plan schema must be glosh-h19-a23-plan-v1")
    phases = raw.get("phases")
    sampling_fps = raw.get("samplingFps", 4)
    startup_timeout = raw.get("startupTimeoutSeconds", 90)
    expected_version = raw.get("expectedAppVersionCode")
    minimum_chrome = raw.get("minimumChromeMajor", 146)
    if raw.get("keepExtractedFrames", False) is not False:
        raise HarnessError("raw extracted frames may not be retained")
    if not isinstance(sampling_fps, int) or not 1 <= sampling_fps <= 8:
        raise HarnessError("samplingFps must be in 1..8")
    if not isinstance(startup_timeout, int) or not 30 <= startup_timeout <= 180:
        raise HarnessError("startupTimeoutSeconds must be in 30..180")
    if expected_version is not None and (not isinstance(expected_version, int) or expected_version <= 0):
        raise HarnessError("expectedAppVersionCode must be a positive integer")
    if not isinstance(minimum_chrome, int) or not 100 <= minimum_chrome <= 999:
        raise HarnessError("minimumChromeMajor must be in 100..999")
    if raw.get("expectedGloshiaModelVersion") != EXPECTED_GLOSHIA_MODEL_VERSION:
        raise HarnessError(f"expectedGloshiaModelVersion must be {EXPECTED_GLOSHIA_MODEL_VERSION}")
    if raw.get("expectedGloshiaModelSha256") != EXPECTED_GLOSHIA_MODEL_SHA256:
        raise HarnessError("expectedGloshiaModelSha256 does not match the reviewed R3.1 model")
    if raw.get("expectedGloshiaPolicyVersion") != EXPECTED_GLOSHIA_POLICY_VERSION:
        raise HarnessError(f"expectedGloshiaPolicyVersion must be {EXPECTED_GLOSHIA_POLICY_VERSION}")
    if not isinstance(phases, list) or not 1 <= len(phases) <= 4:
        raise HarnessError("plan phases must contain 1..4 entries")
    seen: set[str] = set()
    for phase in phases:
        if not isinstance(phase, dict) or phase.get("mode") not in ALLOWED_MODES:
            raise HarnessError("every phase requires mode replace-all or selective")
        safe_id(phase.get("id"))
        states = phase.get("states")
        if not isinstance(states, list) or not 1 <= len(states) <= 25:
            raise HarnessError("every phase requires 1..25 states")
        for state in states:
            if not isinstance(state, dict):
                raise HarnessError("state must be an object")
            state_id = safe_id(state.get("id"))
            if state_id in seen:
                raise HarnessError(f"duplicate state id: {state_id}")
            seen.add(state_id)
            navigation = state.get("navigation", "url")
            if navigation not in ALLOWED_NAVIGATION:
                raise HarnessError(f"invalid navigation: {navigation}")
            url = state.get("url")
            if navigation == "url":
                parsed = urlsplit(url if isinstance(url, str) else "")
                if parsed.scheme not in {"http", "https"} or not parsed.hostname:
                    raise HarnessError(f"state {state_id} requires an http(s) URL")
            elif url is not None:
                raise HarnessError(f"state {state_id} may only provide url with navigation=url")
            new_navigation = new_navigation_for(state)
            if navigation in NAVIGATION_DEFAULTS_TO_NEW_DOCUMENT and not new_navigation:
                raise HarnessError(f"{navigation} must be recorded as a new navigation")
            ready_required = ready_required_for(state)
            if navigation not in READY_OPTIONAL_NAVIGATION and not ready_required:
                raise HarnessError("document/interaction states cannot disable foreground READY authority")
            visual_review_required = visual_review_required_for(state)
            if navigation not in AUTOMATIC_AUTHORITY_EVIDENCE_NAVIGATION and not visual_review_required:
                raise HarnessError("every real-web/non-authority state requires digest-bound visual review")
            duration = state.get("recordSeconds", 12)
            ready_timeout = state.get("readyTimeoutSeconds", 8 if ready_required else 0)
            fixture_timeout = state.get("fixtureTimeoutSeconds", 10)
            process_restart_timeout = state.get("processRestartTimeoutSeconds", 25)
            swipes = state.get("swipes", 0)
            taps = state.get("taps", [])
            if not isinstance(duration, int) or not 5 <= duration <= 45:
                raise HarnessError("recordSeconds must be in 5..45")
            if not isinstance(ready_timeout, int) or not 0 <= ready_timeout <= 20:
                raise HarnessError("readyTimeoutSeconds must be in 0..20")
            if ready_required and ready_timeout < 1:
                raise HarnessError("a READY-bound state requires a positive readyTimeoutSeconds")
            if not ready_required and ready_timeout != 0:
                raise HarnessError("a state without READY authority must use readyTimeoutSeconds=0")
            if not isinstance(fixture_timeout, int) or not 1 <= fixture_timeout <= 20:
                raise HarnessError("fixtureTimeoutSeconds must be in 1..20")
            if navigation == "restart-glosh":
                if not isinstance(process_restart_timeout, int) or not 10 <= process_restart_timeout <= 30:
                    raise HarnessError("processRestartTimeoutSeconds must be in 10..30")
            elif "processRestartTimeoutSeconds" in state:
                raise HarnessError("processRestartTimeoutSeconds is only valid for restart-glosh")
            if not isinstance(swipes, int) or not 0 <= swipes <= 12:
                raise HarnessError("swipes must be in 0..12")
            if not isinstance(taps, list) or len(taps) > 4:
                raise HarnessError("taps must contain at most four normalized points")
            for tap in taps:
                if not isinstance(tap, dict):
                    raise HarnessError("every tap must be an object")
                x, y = tap.get("xPermille"), tap.get("yPermille")
                if not isinstance(x, int) or not isinstance(y, int) or not 0 <= x <= 1000 or not 0 <= y <= 1000:
                    raise HarnessError("tap coordinates must be integer permille values in 0..1000")
            post_gesture_ready = post_gesture_ready_required_for(state)
            if post_gesture_ready and (not ready_required or not taps):
                raise HarnessError("post-gesture READY requires a READY-bound state with at least one tap")
            continuity_required = web_root_continuity_required_for(state)
            if continuity_required and not ready_required:
                raise HarnessError("web-root continuity requires foreground READY authority")
            ready_wait_count = 3 if navigation == "two-tab-binding" else 2 if post_gesture_ready else 1
            if continuity_required:
                ready_wait_count += 1
            total_ready_wait = ready_timeout * ready_wait_count
            controlled = navigation == "controlled" or (
                navigation == "url" and urlsplit(str(url)).path == "/web19/controlled"
            )
            total_fixture_wait = fixture_timeout if controlled else 0
            total_process_restart_wait = process_restart_timeout if navigation == "restart-glosh" else 0
            if duration < 1 + total_ready_wait + total_fixture_wait + total_process_restart_wait + swipes + len(taps):
                raise HarnessError("recordSeconds must cover READY plus every bounded gesture")
            if state.get("orientation", "current") not in ALLOWED_ORIENTATIONS:
                raise HarnessError("invalid orientation")
    return raw
