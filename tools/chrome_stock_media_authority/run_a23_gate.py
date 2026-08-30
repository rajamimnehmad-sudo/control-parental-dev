#!/usr/bin/env python3
"""Bounded A23 evidence runner for the H19 stock-Chrome media gate.

This runner uses only the existing DUMP-protected DEV receiver. Screen evidence
is sampled for audit; it is never used as presentation or filtering authority.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import shlex
import shutil
import signal
import subprocess
import sys
import time
from pathlib import Path
from typing import Any
from urllib.parse import urlsplit

from h19_evidence import (
    analyze_image,
    counter_deltas,
    status_counter_snapshot,
    summarize_accessibility_xml,
    summarize_frames,
    summarize_logcat,
)
from h19_device import (
    APP_PACKAGE,
    CHROME_PACKAGE,
    CHROME_POLICY_URL,
    CONTROLLED_URL,
    Adb,
    collect_preflight,
    ce_data_inode,
    exit_info,
    exit_info_delta,
    filtered_device_policy,
    locate_adb,
    navigate,
    observed_display_rotation,
    package_info,
    prepare_interactive_display,
    restore_setting,
    restore_interactive_display,
    set_and_verify_orientation,
    setting,
    sha256_text,
    swipe_up,
    tap_normalized,
)
from h19_plan import (
    EXPECTED_GLOSHIA_MODEL_SHA256,
    EXPECTED_GLOSHIA_MODEL_VERSION,
    EXPECTED_GLOSHIA_POLICY_VERSION,
    HarnessError,
    navigation_requires_new_release,
    new_navigation_for,
    post_gesture_ready_required_for,
    ready_required_for,
    safe_id,
    validate_plan,
    visual_review_required_for,
    web_root_continuity_required_for,
)
from h19_ready import current_ready_result, ready_baseline
from h19_lifecycle_gates import ready_document_key, restart_glosh_phase, run_two_tab_binding_gate


ACTION_PREFIX = "com.contentfilter.user.chromedataplane.command."
REMOTE_ROOT = "/data/local/tmp/glosh-h19-evidence"
SECURITY_ZERO_COUNTERS = (
    "networkVisualRawBlockedDelivered",
    "networkVisualRawUnknownDelivered",
    "proxyQueueRejects",
    "protectFailure",
    "quicAttempts",
    "directTcpAttempts",
)
HARNESS_CAPABILITIES = {
    "stockChromeTabSwitch": {
        "status": "SUPPORTED",
        "mechanism": "android.provider.Browser.EXTRA_CREATE_NEW_TAB_then_android_back",
        "authority": "event_source_one_shot_plus_web_root_continuity",
        "coordinateUiAutomation": False,
        "countsAsPass": True,
    },
    "backgroundForeground": {
        "status": "SUPPORTED",
        "meaning": "android_home_then_same_chrome_foreground",
        "countsAsTabSwitch": False,
    },
}


def write_json(path: Path, value: Any) -> None:
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def write_visual_review_manifest(output: Path, entries: list[dict[str, Any]]) -> None:
    write_json(
        output / "visual-review-manifest.json",
        {
            "schema": "glosh-h19-visual-review-manifest-v1",
            "status": "PENDING_MODEL_OR_HUMAN_REVIEW" if entries else "NOT_REQUIRED",
            "automaticPassEligible": False if entries else True,
            "entries": entries,
        },
    )


def write_logcat_summary(path: Path, summary: dict[str, Any]) -> None:
    """Persist aggregates only; raw Logcat and full network locations stay in RAM."""

    serialized = json.dumps(summary, indent=2, sort_keys=True) + "\n"
    if re.search(r"(?:dat=)?https?://", serialized, flags=re.IGNORECASE):
        raise HarnessError("refuse to persist a Logcat summary containing a full URL")
    path.write_text(serialized, encoding="utf-8")


def logcat(adb: Adb, since: str) -> str:
    filters = [
        "ChromePhotosDataPlane:I", "ChromeMediaShieldStatus:I", "ChromeCoverageAudit17:I", "ChromeMediaShieldReady:I",
        "ChromePhotosSurfaceProbe:I", "ChromeProcessGuard:I", "FilterVpnService:I", "VpnTransport09A:I",
        "AndroidRuntime:E", "libc:F", "DEBUG:F", "*:S",
    ]
    return adb.run("logcat", "-d", "-T", since, "-v", "threadtime", *filters, timeout=90).stdout


def request_status(adb: Adb, since: str) -> tuple[str, dict[str, Any]]:
    adb.broadcast(ACTION_PREFIX + "STATUS")
    adb.broadcast(ACTION_PREFIX + "TRANSPORT_STATUS")
    time.sleep(0.25)
    value = logcat(adb, since)
    return value, summarize_logcat(value)


def start_phase(
    adb: Adb,
    mode: str,
    since: str,
    timeout_seconds: int,
    previous_session: str = "",
) -> dict[str, Any]:
    adb.broadcast(
        ACTION_PREFIX + "START",
        [
            ("--ez", "stock_media_authority_enabled", "true"),
            ("--ez", "full_tunnel_dev_gate_enabled", "true"),
            ("--ez", "replace_all_network_visuals", "true" if mode == "replace-all" else "false"),
        ],
    )
    deadline = time.monotonic() + timeout_seconds
    last: dict[str, Any] = {}
    while time.monotonic() < deadline:
        _, last = request_status(adb, since)
        status = last.get("status", {}).get("fields", {})
        active = last.get("activeMode", {})
        expected_mode = "replace_all" if mode == "replace-all" else "selective"
        if (
            status.get("active") == "true"
            and status.get("lifecycle") == "PresentationReady"
            and status.get("ready") == "true"
            and status.get("chromeSuspended") == "false"
            and active.get("networkVisualMode") == expected_mode
            and active.get("stockMediaAuthority") == "true"
            and active.get("transport") == "full_tunnel_dev"
            and active.get("model") == EXPECTED_GLOSHIA_MODEL_VERSION
            and active.get("modelSha") == EXPECTED_GLOSHIA_MODEL_SHA256
            and active.get("policy") == EXPECTED_GLOSHIA_POLICY_VERSION
            and (not previous_session or active.get("session") != previous_session)
        ):
            return last
        time.sleep(0.5)
    raise HarnessError(f"H19 phase {mode} did not become active: {last}")


def stop_phase(adb: Adb, timeout_seconds: int = 20) -> None:
    adb.broadcast(ACTION_PREFIX + "STOP")
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        service = adb.shell("dumpsys", "activity", "services", APP_PACKAGE, check=False)
        if "ChromePhotosDataPlaneLabService" not in service:
            return
        time.sleep(0.25)
    raise HarnessError("H19 service did not stop and roll back within the bounded cleanup window")


def wait_for_ready(
    adb: Adb,
    since: str,
    timeout_seconds: int,
    minimum_release_count: int,
    previous_marker: dict[str, Any] | None = None,
    require_advance: bool = False,
) -> dict[str, Any]:
    deadline = time.monotonic() + timeout_seconds
    attempts = 0
    last_status: dict[str, Any] = {}
    while time.monotonic() < deadline:
        attempts += 1
        _, last_status = request_status(adb, since)
        result = current_ready_result(
            last_status,
            expected_package=CHROME_PACKAGE,
            minimum_release_count=minimum_release_count,
            previous_marker=previous_marker,
            require_advance=require_advance,
        )
        if result is not None:
            return {**result, "attempts": attempts}
        time.sleep(0.25)
    raise HarnessError(
        "foreground READY authority did not become current within the bounded wait: "
        f"currentReadyBinding={last_status.get('currentReadyBinding')} status={last_status.get('status', {})}"
    )


def wait_for_web_root_continuity(
    adb: Adb,
    since: str,
    timeout_seconds: int,
    minimum_verified_count: int,
    expected_marker: dict[str, Any],
) -> dict[str, Any]:
    deadline = time.monotonic() + timeout_seconds
    last_status: dict[str, Any] = {}
    while time.monotonic() < deadline:
        _, last_status = request_status(adb, since)
        continuity = last_status.get("readyWebRootContinuity", {})
        current = last_status.get("currentReadyBinding")
        if (
            int(continuity.get("verified", 0)) >= minimum_verified_count
            and int(continuity.get("violations", 0)) == 0
            and continuity.get("currentDocumentVerified") is True
            and isinstance(current, dict)
            and ready_document_key(current) == ready_document_key(expected_marker)
            and current.get("rawPresented") is False
        ):
            return {
                "pass": True,
                "verifiedCount": int(continuity["verified"]),
                "documentKeyMatched": True,
                "sourceCurrent": False,
                "rawPresented": False,
            }
        time.sleep(0.25)
    raise HarnessError(
        "exact web-root continuity after source pruning was not observed: "
        f"continuity={last_status.get('readyWebRootContinuity')} "
        f"currentReadyBinding={last_status.get('currentReadyBinding')}"
    )


def baseline_then_set_orientation(
    adb: Adb,
    phase_since: str,
    orientation: str,
) -> tuple[dict[str, Any], dict[str, Any], bool]:
    """Snapshot READY before a rotation can invalidate it, then apply orientation."""

    _, summary = request_status(adb, phase_since)
    baseline = ready_baseline(summary)
    before = observed_display_rotation(adb)
    evidence = set_and_verify_orientation(adb, orientation)
    after = evidence.get("observedRotation")
    changed = orientation != "current" and before is not None and after is not None and before != after
    return baseline, {**evidence, "baselineRotation": before, "changed": changed}, changed


def wait_for_fixture_report(
    adb: Adb,
    since: str,
    timeout_seconds: int,
    minimum_reports: int,
    minimum_frame_reports: int,
) -> tuple[str, dict[str, Any]]:
    deadline = time.monotonic() + timeout_seconds
    last_log = ""
    last_summary: dict[str, Any] = {}
    while time.monotonic() < deadline:
        last_log, last_summary = request_status(adb, since)
        fixture = last_summary.get("fixtureReport", {})
        counts = fixture.get("counts", {})
        current_report_observed = (
            int(counts.get("reports", 0)) > minimum_reports
            and int(counts.get("frame_reports", 0)) > minimum_frame_reports
        )
        if fixture.get("pass") is True and current_report_observed:
            return last_log, last_summary
        reasons = set(fixture.get("reasons", []))
        terminal = {
            "main_report_contains_error",
            "frame_report_scenario_set_mismatch",
            "frame_report_not_all_blocked",
            "frame_report_rejected",
        }
        if reasons & terminal:
            raise HarnessError(f"controlled fixture fail-closed: {fixture}")
        time.sleep(0.25)
    raise HarnessError(f"controlled fixture did not produce a passing report: {last_summary.get('fixtureReport', {})}")


def enforce_counter_gate(mode: str, current: dict[str, int], deltas: dict[str, int | None]) -> None:
    missing = [field for field in SECURITY_ZERO_COUNTERS if field not in current]
    if missing:
        raise HarnessError(f"security counters unavailable: {missing}")
    nonzero = {field: current[field] for field in SECURITY_ZERO_COUNTERS if current[field] != 0}
    if nonzero:
        raise HarnessError(f"security counter invariant failed: {nonzero}")
    negative = {field: value for field, value in deltas.items() if value is not None and value < 0}
    if negative:
        raise HarnessError(f"monotonic counter regression within phase: {negative}")
    if mode == "replace-all" and current.get("networkVisualRawDelivered") != 0:
        raise HarnessError(
            "replace-all delivered raw network visual bytes: "
            f"{current.get('networkVisualRawDelivered')}"
        )


def accessibility_summary(adb: Adb) -> dict[str, Any]:
    xml = adb.shell("uiautomator", "dump", "/dev/tty", check=False, timeout=30)
    try:
        return summarize_accessibility_xml(xml)
    except Exception as error:
        return {"parsed": False, "error": type(error).__name__}


def extract_frames(video: Path, destination: Path, fps: int) -> list[Path]:
    ffmpeg = shutil.which("ffmpeg")
    if not ffmpeg:
        raise HarnessError("ffmpeg is required to sample screenrecord evidence")
    destination.mkdir(parents=True)
    subprocess.run(
        [ffmpeg, "-hide_banner", "-loglevel", "error", "-i", str(video), "-vf", f"fps={fps}", str(destination / "%06d.png")],
        check=True,
        timeout=120,
    )
    return sorted(destination.glob("*.png"))


def create_contact_sheet(frame_paths: list[Path], destination: Path) -> dict[str, Any]:
    if not frame_paths:
        raise HarnessError("cannot build a visual-review contact sheet without frames")
    columns = 8
    rows = (len(frame_paths) + columns - 1) // columns
    subprocess.run(
        [
            "ffmpeg",
            "-hide_banner",
            "-loglevel",
            "error",
            "-i",
            str(frame_paths[0].parent / "%06d.png"),
            "-vf",
            f"scale=180:-1:flags=area,tile={columns}x{rows}:padding=2:margin=2",
            "-frames:v",
            "1",
            str(destination),
        ],
        check=True,
        timeout=120,
    )
    raw = destination.read_bytes()
    return {
        "path": str(destination),
        "sha256": hashlib.sha256(raw).hexdigest(),
        "sourceFrameCount": len(frame_paths),
        "columns": columns,
        "rows": rows,
        "reviewStatus": "PENDING_MODEL_OR_HUMAN_REVIEW",
    }


def run_state(
    adb: Adb,
    state: dict[str, Any],
    output: Path,
    fps: int,
    phase_id: str,
    mode: str,
    previous_counters: dict[str, int],
    previous_fixture_counts: dict[str, int],
    phase_since: str,
) -> dict[str, Any]:
    state_id = safe_id(state["id"])
    directory = output / "states" / state_id
    directory.mkdir(parents=True)
    state_since = adb.shell("date", "+%m-%d %H:%M:%S.000").strip()
    ready_required = ready_required_for(state)
    if ready_required:
        ready_state_baseline, orientation_evidence, orientation_changed = baseline_then_set_orientation(
            adb,
            phase_since,
            state.get("orientation", "current"),
        )
    else:
        ready_state_baseline = {"releaseCount": 0, "continuityCount": 0, "marker": None}
        orientation_evidence = set_and_verify_orientation(adb, state.get("orientation", "current"))
        orientation_changed = False
    new_navigation = new_navigation_for(state)
    adb.broadcast(
        ACTION_PREFIX + "AUDIT_MARK",
        [
            ("--es", "chrome_coverage_audit_state_label", state_id),
            ("--ez", "chrome_coverage_audit_new_navigation", "true" if new_navigation else "false"),
        ],
    )
    duration = int(state.get("recordSeconds", 12))
    remote = f"{REMOTE_ROOT}/{state_id}.mp4"
    adb.shell("mkdir", "-p", REMOTE_ROOT)
    record_arguments = ["screenrecord"]
    if state.get("orientation") == "portrait":
        record_arguments.extend(["--size", "720x1600"])
    elif state.get("orientation") == "landscape":
        record_arguments.extend(["--size", "1600x720"])
    record_arguments.extend(["--bit-rate", "4000000", "--time-limit", str(duration), remote])
    screenrecord = subprocess.Popen(
        [*adb.prefix, "shell", " ".join(shlex.quote(value) for value in record_arguments)],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    ready_evidence: dict[str, Any] = {"required": ready_required, "pass": False}
    process_restart: dict[str, Any] = {"performed": False}
    counter_baseline = previous_counters
    try:
        time.sleep(0.4)
        baseline_release_count = int(ready_state_baseline["releaseCount"])
        baseline_marker = ready_state_baseline["marker"]
        transition_required = (
            navigation_requires_new_release(state)
            or orientation_changed
            or state.get("navigation", "url") == "background-foreground"
        )
        ready_completed_by_action = False
        if state.get("navigation") == "two-tab-binding":
            ready_evidence = {
                "required": True,
                **run_two_tab_binding_gate(
                    adb,
                    phase_since,
                    int(state.get("readyTimeoutSeconds", 8)),
                    baseline_release_count,
                    baseline_marker,
                    wait_for_ready,
                ),
            }
            ready_completed_by_action = True
        elif state.get("navigation") == "restart-glosh":
            process_restart, restarted = restart_glosh_phase(
                adb,
                mode,
                state_since,
                timeout_seconds=int(state.get("processRestartTimeoutSeconds", 25)),
                request_status=request_status,
                start_phase=start_phase,
                foreground_current=lambda device: navigate(device, {"navigation": "foreground"}),
            )
            counter_baseline = status_counter_snapshot(restarted)
            navigate(adb, {"navigation": "reload"})
            process_restart["explicitReloadPerformedAfterFailClose"] = True
        else:
            navigate(adb, state)
        if ready_evidence["required"] and not ready_completed_by_action:
            ready_evidence = {
                "required": True,
                **wait_for_ready(
                    adb,
                    phase_since,
                    int(state.get("readyTimeoutSeconds", 8)),
                    minimum_release_count=(
                        baseline_release_count + 1 if transition_required else baseline_release_count
                    ),
                    previous_marker=baseline_marker,
                    require_advance=transition_required,
                ),
            }
        else:
            ready_evidence = {"required": False, "pass": True, "reason": "bounded_non_document_probe"}
        if web_root_continuity_required_for(state):
            marker = ready_evidence.get("marker")
            if not isinstance(marker, dict):
                raise HarnessError("web-root continuity gate requires an exact released marker")
            ready_evidence["webRootContinuity"] = wait_for_web_root_continuity(
                adb,
                phase_since,
                int(state.get("readyTimeoutSeconds", 8)),
                minimum_verified_count=int(ready_state_baseline["continuityCount"]) + 1,
                expected_marker=marker,
            )
        for _ in range(int(state.get("swipes", 0))):
            if screenrecord.poll() is not None:
                raise HarnessError(f"screenrecord ended before all gestures for {state_id}")
            swipe_up(adb)
            time.sleep(0.8)
        for tap in state.get("taps", []):
            if screenrecord.poll() is not None:
                raise HarnessError(f"screenrecord ended before all taps for {state_id}")
            tap_normalized(adb, int(tap["xPermille"]), int(tap["yPermille"]))
            time.sleep(1.0)
        if post_gesture_ready_required_for(state):
            prior_marker = ready_evidence.get("marker")
            prior_release_count = int(ready_evidence.get("releaseCountInPhase", 0))
            ready_evidence["postGesture"] = wait_for_ready(
                adb,
                phase_since,
                int(state.get("readyTimeoutSeconds", 8)),
                minimum_release_count=prior_release_count + 1,
                previous_marker=prior_marker if isinstance(prior_marker, dict) else None,
                require_advance=True,
            )
        controlled = state.get("navigation", "url") == "controlled" or urlsplit(state.get("url", "")).path == "/web19/controlled"
        if controlled:
            wait_for_fixture_report(
                adb,
                state_since,
                int(state.get("fixtureTimeoutSeconds", 10)),
                minimum_reports=int(previous_fixture_counts.get("reports", 0)),
                minimum_frame_reports=int(previous_fixture_counts.get("frame_reports", 0)),
            )
        try:
            screenrecord.wait(timeout=duration + 8)
        except subprocess.TimeoutExpired:
            raise HarnessError(f"screenrecord did not terminate for {state_id}")
    finally:
        state_exception_active = sys.exc_info()[0] is not None
        if screenrecord.poll() is None:
            screenrecord.terminate()
            try:
                screenrecord.wait(timeout=5)
            except subprocess.TimeoutExpired:
                screenrecord.kill()
        if screenrecord.returncode not in {0, None} and not state_exception_active:
            adb.shell("rm", "-f", remote, check=False)
            raise HarnessError(f"screenrecord failed for {state_id} with {screenrecord.returncode}")
    video = directory / "screenrecord.mp4"
    screenshot = directory / "screen.png"
    frame_directory = directory / "sampled-frames"
    contact_sheet: dict[str, Any] = {"required": False, "reviewStatus": "NOT_APPLICABLE"}
    try:
        adb.run("pull", remote, str(video), timeout=90)
        captured = adb.run("exec-out", "screencap", "-p", timeout=30, text=False).stdout
        screenshot.write_bytes(captured)
        _state_log, log_summary = request_status(adb, state_since)
        ui = {
            "source": "trusted_accessibility_service_log",
            "readyMarkers": log_summary.get("readyMarkers", []),
            "externalUiAutomationUsed": False,
        }
        write_logcat_summary(directory / "logcat-summary.json", log_summary)
        current_counters = status_counter_snapshot(log_summary)
        deltas = counter_deltas(counter_baseline, current_counters)
        enforce_counter_gate(mode, current_counters, deltas)
        fixture_gate = log_summary.get("fixtureReport", {})
        if controlled and fixture_gate.get("pass") is not True:
            raise HarnessError(f"controlled fixture report was not current at state close: {fixture_gate}")
        frame_paths = extract_frames(video, frame_directory, fps)
        if not frame_paths:
            raise HarnessError(f"screenrecord produced no analyzable frames for {state_id}")
        video_evidence = summarize_frames(frame_paths)
        screen_evidence = analyze_image(screenshot, sample_scale=2)
        video_sha256 = hashlib.sha256(video.read_bytes()).hexdigest()
        if visual_review_required_for(state):
            contact_path = directory / "contact-sheet.png"
            review_purpose = (
                "MANAGED_POLICY_SUPPORT"
                if state.get("navigation") == "chrome-policy"
                else "REPLACE_ALL_MEDIA_ABSENCE"
                if mode == "replace-all"
                else "SELECTIVE_MEDIA_BEHAVIOR"
            )
            required_verdict = {
                "MANAGED_POLICY_SUPPORT": "MANAGED_POLICY_EVIDENCE_REVIEWED",
                "REPLACE_ALL_MEDIA_ABSENCE": "NO_UNEXPECTED_IN_SCOPE_RAW_MEDIA",
                "SELECTIVE_MEDIA_BEHAVIOR": "SELECTIVE_BEHAVIOR_REVIEWED_NO_ARCHITECTURE_ESCAPE",
            }[review_purpose]
            contact_sheet = {
                "required": True,
                **create_contact_sheet(frame_paths, contact_path),
                "path": f"states/{state_id}/contact-sheet.png",
                "reviewPurpose": review_purpose,
                "requiredVerdict": required_verdict,
            }
    finally:
        adb.shell("rm", "-f", remote, check=False)
        if frame_directory.exists():
            shutil.rmtree(frame_directory)
        video.unlink(missing_ok=True)
        screenshot.unlink(missing_ok=True)
    target = state.get("url", "")
    if state.get("navigation") == "controlled":
        target = CONTROLLED_URL
    elif state.get("navigation") == "two-tab-binding":
        target = CONTROLLED_URL
    elif state.get("navigation") == "chrome-policy":
        target = CHROME_POLICY_URL
    parsed = urlsplit(target)
    result = {
        "id": state_id,
        "phaseId": phase_id,
        "mode": mode,
        "navigation": state.get("navigation", "url"),
        "newNavigation": new_navigation,
        "orientation": state.get("orientation", "current"),
        "recordSeconds": duration,
        "swipes": int(state.get("swipes", 0)),
        "tapCount": len(state.get("taps", [])),
        "urlHost": parsed.hostname or "",
        "urlSha256": sha256_text(target) if target else "",
        "orientationVerification": orientation_evidence,
        "readyWait": ready_evidence,
        "stockChromeTabSwitch": (
            ready_evidence if state.get("navigation") == "two-tab-binding" else {"applicable": False}
        ),
        "accessibility": ui,
        "logcat": log_summary,
        "counters": {
            "previous": counter_baseline,
            "current": current_counters,
            "delta": deltas,
            "epochReset": process_restart.get("performed") is True,
        },
        "processRestart": process_restart,
        "controlledFixtureGate": fixture_gate if controlled else {"applicable": False},
        "screenEvidence": screen_evidence,
        "videoEvidence": {**video_evidence, "samplingFps": fps, "physicalClaim": "observable_at_sampling_resolution"},
        "videoSha256": video_sha256,
        "visualReview": contact_sheet,
        "rawArtifactsRetained": contact_sheet.get("required") is True,
    }
    write_json(directory / "snapshot.json", result)
    return result


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--serial", required=True)
    parser.add_argument("--plan", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    if not re.fullmatch(r"[A-Za-z0-9._:-]+", args.serial):
        raise HarnessError("invalid adb serial")
    plan = validate_plan(json.loads(args.plan.read_text(encoding="utf-8")))
    for dependency in ("ffmpeg", "ffprobe"):
        if not shutil.which(dependency):
            raise HarnessError(f"{dependency} is required before the device gate starts")
    if args.output.exists() and any(args.output.iterdir()):
        raise HarnessError(f"refuse to overwrite non-empty output: {args.output}")
    args.output.mkdir(parents=True, exist_ok=True)
    adb = Adb(locate_adb(), args.serial)
    rotation = {name: setting(adb, name) for name in ("accelerometer_rotation", "user_rotation")}
    run_since = adb.shell("date", "+%m-%d %H:%M:%S.000").strip()
    started = time.time()
    preflight = collect_preflight(adb, plan.get("expectedModel", "SM-A235M"), int(plan.get("expectedSdk", 34)))
    preflight["harnessCapabilities"] = HARNESS_CAPABILITIES
    expected_version = plan.get("expectedAppVersionCode")
    if expected_version is not None and preflight["app"]["versionCode"] != str(expected_version):
        raise HarnessError(f"installed app versionCode is {preflight['app']['versionCode']}, expected {expected_version}")
    chrome_major = preflight["chrome"]["versionName"].split(".", 1)[0]
    if not chrome_major.isdigit() or int(chrome_major) < int(plan.get("minimumChromeMajor", 146)):
        raise HarnessError(f"Chrome version is below required major: {preflight['chrome']['versionName']}")
    if adb.path_exists(REMOTE_ROOT):
        raise HarnessError(f"refuse to reuse existing device evidence path: {REMOTE_ROOT}")
    state_results: list[dict[str, Any]] = []
    visual_review_entries: list[dict[str, Any]] = []
    active = False
    last_phase_since = run_since
    display_lease: dict[str, Any] | None = None

    def stop_for_signal(_signal: int, _frame: Any) -> None:
        raise KeyboardInterrupt

    signal.signal(signal.SIGINT, stop_for_signal)
    signal.signal(signal.SIGTERM, stop_for_signal)
    try:
        display_lease = prepare_interactive_display(adb)
        preflight["interactiveDisplayLease"] = display_lease
        write_json(args.output / "preflight.json", preflight)
        adb.shell("mkdir", "-p", REMOTE_ROOT)
        for phase in plan["phases"]:
            if active:
                stop_phase(adb)
                active = False
            phase_id = safe_id(phase["id"])
            mode = phase["mode"]
            last_phase_since = adb.shell("date", "+%m-%d %H:%M:%S.000").strip()
            active = True
            active_summary = start_phase(adb, mode, last_phase_since, int(plan.get("startupTimeoutSeconds", 90)))
            previous_counters = status_counter_snapshot(active_summary)
            enforce_counter_gate(mode, previous_counters, {field: 0 for field in previous_counters})
            previous_fixture_counts: dict[str, int] = {}
            write_json(
                args.output / f"phase-{phase_id}.json",
                {"id": phase_id, "mode": mode, "active": active_summary, "initialCounters": previous_counters},
            )
            for state in phase["states"]:
                result = run_state(
                    adb, state, args.output, int(plan.get("samplingFps", 4)),
                    phase_id, mode, previous_counters, previous_fixture_counts, last_phase_since,
                )
                previous_counters = result["counters"]["current"]
                fixture_counts = result["controlledFixtureGate"].get("counts", {})
                if fixture_counts:
                    previous_fixture_counts = {
                        key: int(value)
                        for key, value in fixture_counts.items()
                        if isinstance(value, int)
                    }
                video = result["videoEvidence"]
                if result["visualReview"].get("required") is True:
                    visual_review_entries.append(
                        {
                            "stateId": result["id"],
                            "phaseId": phase_id,
                            "mode": mode,
                            **result["visualReview"],
                        }
                    )
                    write_visual_review_manifest(args.output, visual_review_entries)
                state_results.append(
                    {
                        "id": result["id"], "phaseId": phase_id, "mode": mode,
                        "snapshot": f"states/{result['id']}/snapshot.json",
                        "sampledFrames": video["frameCount"],
                        "auditPlaceholderFrames": len(video["auditPlaceholderVisibleFrames"]),
                        "controlledSentinelLikeFrames": len(video["controlledSentinelLikeVisibleFrames"]),
                        "readyMarkerCount": len(result["accessibility"].get("readyMarkers", [])),
                        "stockChromeTabSwitchPass": result["stockChromeTabSwitch"].get("pass"),
                        "counterDelta": result["counters"]["delta"],
                        "controlledFixturePass": result["controlledFixtureGate"].get("pass"),
                        "visualReviewStatus": result["visualReview"].get("reviewStatus"),
                    }
                )
        _final_log, final_summary = request_status(adb, last_phase_since)
        write_logcat_summary(args.output / "logcat-summary.json", final_summary)
        write_visual_review_manifest(args.output, visual_review_entries)
        write_json(
            args.output / "summary.json",
            {
                "schema": "glosh-h19-a23-evidence-v1",
                "durationSeconds": round(time.time() - started, 3),
                "states": state_results,
                "finalLogcat": final_summary,
                "samplingResolution": f"{int(plan.get('samplingFps', 4))}_fps",
                "screensAreEvidenceOnly": True,
                "harnessCapabilities": HARNESS_CAPABILITIES,
                "visualReviewGate": {
                    "status": "PENDING_MODEL_OR_HUMAN_REVIEW" if visual_review_entries else "NOT_REQUIRED",
                    "automaticPassEligible": False if visual_review_entries else True,
                    "manifest": "visual-review-manifest.json",
                },
            },
        )
    finally:
        active_exception = sys.exc_info()[0] is not None
        cleanup_error: Exception | None = None
        if active:
            try:
                stop_phase(adb)
            except Exception as error:
                cleanup_error = error
        restore_setting(adb, "accelerometer_rotation", rotation["accelerometer_rotation"])
        restore_setting(adb, "user_rotation", rotation["user_rotation"])
        display_restore = (
            restore_interactive_display(adb, display_lease)
            if display_lease is not None
            else {"display": "lease_not_acquired", "stayOnWhilePluggedIn": "unchanged"}
        )
        adb.shell("rm", "-rf", REMOTE_ROOT, check=False)
        adb.broadcast(ACTION_PREFIX + "TRANSPORT_STATUS")
        time.sleep(0.25)
        post_log_summary = summarize_logcat(logcat(adb, run_since))
        post_policy = adb.shell("dumpsys", "device_policy", check=False, timeout=90)
        post_exit = {"app": exit_info(adb, APP_PACKAGE), "chrome": exit_info(adb, CHROME_PACKAGE)}
        exit_deltas = {
            package: exit_info_delta(preflight["exitInfo"][package], post_exit[package])
            for package in ("app", "chrome")
        }
        post_inode = ce_data_inode(adb)
        post_policy_summary = filtered_device_policy(post_policy)
        rollback_invariants = {
            "ceDataInodePreserved": post_inode == preflight["ceDataInode"],
            "devicePolicySelectedLinesPreserved":
                post_policy_summary["selectedLines"] == preflight["devicePolicy"]["selectedLines"],
            "labServiceStopped": "ChromePhotosDataPlaneLabService" not in adb.shell(
                "dumpsys", "activity", "services", APP_PACKAGE, check=False
            ),
            "exitInfoDeltaZero": all(value == 0 for package in exit_deltas.values() for value in package.values()),
            "ownedFdResourcesZero": post_log_summary.get("transportStatus", {}).get("ownedFdResources") == "0",
            "protectedUdpSocketsZero": post_log_summary.get("transportStatus", {}).get("activeProtectedUdpSockets") == "0",
            "transportRuntimeReady": post_log_summary.get("transportStatus", {}).get("transportRuntime") == "ready",
        }
        write_json(
            args.output / "postflight.json",
            {
                "app": package_info(adb, APP_PACKAGE),
                "chrome": package_info(adb, CHROME_PACKAGE),
                "ceDataInode": post_inode,
                "devicePolicy": post_policy_summary,
                "devicePolicyFullDumpStable": post_policy_summary["sha256"] == preflight["devicePolicy"]["sha256"],
                "exitInfo": post_exit,
                "exitInfoDelta": exit_deltas,
                "rotationRestored": {name: setting(adb, name) for name in rotation},
                "interactiveDisplayRestored": display_restore,
                "labServicePresent": not rollback_invariants["labServiceStopped"],
                "rollbackInvariants": rollback_invariants,
                "terminalLogcat": post_log_summary,
            },
        )
        rollback_failures = [name for name, passed in rollback_invariants.items() if not passed]
        if rollback_failures and not active_exception:
            raise HarnessError(f"postflight invariant failed: {rollback_failures}")
        if cleanup_error is not None and not active_exception:
            raise cleanup_error
    print(args.output / "summary.json")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except HarnessError as error:
        print(f"H19 harness error: {error}", file=sys.stderr)
        raise SystemExit(2)
