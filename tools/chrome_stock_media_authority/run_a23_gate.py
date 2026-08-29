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
    package_info,
    restore_setting,
    set_and_verify_orientation,
    setting,
    sha256_text,
    swipe_up,
    tap_normalized,
)
from h19_plan import (
    HarnessError,
    navigation_requires_new_release,
    new_navigation_for,
    post_gesture_ready_required_for,
    ready_required_for,
    safe_id,
    validate_plan,
    visual_review_required_for,
)


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
        "ChromeProcessGuard:I", "FilterVpnService:I", "VpnTransport09A:I",
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


def restart_glosh_phase(
    adb: Adb,
    mode: str,
    since: str,
    timeout_seconds: int = 90,
) -> tuple[dict[str, Any], dict[str, Any]]:
    """Exercise the existing DUMP-only main-process kill and restore current lab mode."""

    overall_deadline = time.monotonic() + timeout_seconds
    _, before = request_status(adb, since)
    previous_session = str(before.get("activeMode", {}).get("session", ""))
    before_pids = set(adb.shell("pidof", APP_PACKAGE, check=False).split())
    if not previous_session or not before_pids:
        raise HarnessError("cannot prove the current H19 session/main process before restart")
    try:
        adb.broadcast(ACTION_PREFIX + "MAIN_PROCESS_KILL")
    except subprocess.CalledProcessError:
        # The receiver intentionally kills its own process before `am broadcast`
        # is guaranteed to observe a normal completion.
        pass
    deadline = min(overall_deadline, time.monotonic() + 10)
    after_kill_pids: set[str] = set()
    while time.monotonic() < deadline:
        after_kill_pids = set(adb.shell("pidof", APP_PACKAGE, check=False).split())
        services = adb.shell("dumpsys", "activity", "services", APP_PACKAGE, check=False)
        if before_pids.isdisjoint(after_kill_pids) and "ChromePhotosDataPlaneLabService" not in services:
            break
        time.sleep(0.1)
    else:
        raise HarnessError("Glosh main process/service did not cross the bounded restart boundary")
    remaining = max(1, int(overall_deadline - time.monotonic()))
    active = start_phase(adb, mode, since, remaining, previous_session=previous_session)
    after_pids = set(adb.shell("pidof", APP_PACKAGE, check=False).split())
    current_session = str(active.get("activeMode", {}).get("session", ""))
    if not after_pids or not before_pids.isdisjoint(after_pids) or current_session == previous_session:
        raise HarnessError("Glosh restart did not produce a fresh process and protection session")
    return (
        {
            "performed": True,
            "oldPidCount": len(before_pids),
            "newPidCount": len(after_pids),
            "oldSessionSha256": sha256_text(previous_session),
            "newSessionSha256": sha256_text(current_session),
            "labServiceObservedStopped": True,
            "modeRestored": mode,
        },
        active,
    )


def wait_for_ready(
    adb: Adb,
    since: str,
    timeout_seconds: int,
    minimum_release_count: int,
    previous_marker: dict[str, Any] | None = None,
) -> dict[str, Any]:
    deadline = time.monotonic() + timeout_seconds
    attempts = 0
    last_status: dict[str, Any] = {}
    while time.monotonic() < deadline:
        attempts += 1
        _, last_status = request_status(adb, since)
        markers = [
            marker
            for marker in last_status.get("readyMarkers", [])
            if marker.get("package") == CHROME_PACKAGE and marker.get("lifecycle", "").isdigit()
        ]
        fields = last_status.get("status", {}).get("fields", {})
        release_count = int(last_status.get("readyPhases", {}).get("ready_foreground_released", 0))
        marker_advanced = False
        if len(markers) == 1:
            marker_advanced = previous_marker is None or any(
                markers[0].get(field) != previous_marker.get(field)
                for field in ("tokenDigestPrefix", "lifecycle", "windowId")
            )
        if (
            len(markers) == 1
            and fields.get("active") == "true"
            and fields.get("lifecycle") == "PresentationReady"
            and release_count >= minimum_release_count
            and marker_advanced
        ):
            return {
                "pass": True,
                "attempts": attempts,
                "marker": markers[0],
                "lifecycle": fields.get("lifecycle"),
                "releaseCountSinceStateStart": release_count,
                "markerAdvanced": marker_advanced,
            }
        time.sleep(0.25)
    raise HarnessError(
        "foreground READY authority did not become current within the bounded wait: "
        f"trustedReadyMarkers={last_status.get('readyMarkers', [])} status={last_status.get('status', {})}"
    )


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
) -> dict[str, Any]:
    state_id = safe_id(state["id"])
    directory = output / "states" / state_id
    directory.mkdir(parents=True)
    orientation_evidence = set_and_verify_orientation(adb, state.get("orientation", "current"))
    state_since = adb.shell("date", "+%m-%d %H:%M:%S.000").strip()
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
    ready_evidence: dict[str, Any] = {"required": ready_required_for(state), "pass": False}
    process_restart: dict[str, Any] = {"performed": False}
    counter_baseline = previous_counters
    try:
        time.sleep(0.4)
        baseline_release_count = 0
        baseline_marker: dict[str, Any] | None = None
        if ready_evidence["required"] and navigation_requires_new_release(state):
            _, baseline_status_summary = request_status(adb, state_since)
            baseline_markers = [
                marker
                for marker in baseline_status_summary.get("readyMarkers", [])
                if marker.get("package") == CHROME_PACKAGE and marker.get("lifecycle", "").isdigit()
            ]
            _, baseline_status = request_status(adb, state_since)
            baseline_release_count = int(
                baseline_status.get("readyPhases", {}).get("ready_foreground_released", 0)
            )
            if len(baseline_markers) == 1:
                baseline_marker = baseline_markers[0]
        if state.get("navigation") == "restart-glosh":
            process_restart, restarted = restart_glosh_phase(
                adb,
                mode,
                state_since,
                timeout_seconds=int(state.get("processRestartTimeoutSeconds", 25)),
            )
            counter_baseline = status_counter_snapshot(restarted)
            navigate(adb, {"navigation": "reload"})
        else:
            navigate(adb, state)
        if ready_evidence["required"]:
            ready_evidence = {
                "required": True,
                **wait_for_ready(
                    adb,
                    state_since,
                    int(state.get("readyTimeoutSeconds", 8)),
                    minimum_release_count=(
                        baseline_release_count + 1 if navigation_requires_new_release(state) else 0
                    ),
                    previous_marker=baseline_marker,
                ),
            }
        else:
            ready_evidence = {"required": False, "pass": True, "reason": "bounded_non_document_probe"}
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
            prior_release_count = int(ready_evidence.get("releaseCountSinceStateStart", 0))
            ready_evidence["postGesture"] = wait_for_ready(
                adb,
                state_since,
                int(state.get("readyTimeoutSeconds", 8)),
                minimum_release_count=prior_release_count + 1,
                previous_marker=prior_marker if isinstance(prior_marker, dict) else None,
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
    expected_version = plan.get("expectedAppVersionCode")
    if expected_version is not None and preflight["app"]["versionCode"] != str(expected_version):
        raise HarnessError(f"installed app versionCode is {preflight['app']['versionCode']}, expected {expected_version}")
    chrome_major = preflight["chrome"]["versionName"].split(".", 1)[0]
    if not chrome_major.isdigit() or int(chrome_major) < int(plan.get("minimumChromeMajor", 146)):
        raise HarnessError(f"Chrome version is below required major: {preflight['chrome']['versionName']}")
    write_json(args.output / "preflight.json", preflight)
    if adb.path_exists(REMOTE_ROOT):
        raise HarnessError(f"refuse to reuse existing device evidence path: {REMOTE_ROOT}")
    adb.shell("mkdir", "-p", REMOTE_ROOT)
    state_results: list[dict[str, Any]] = []
    visual_review_entries: list[dict[str, Any]] = []
    active = False
    last_phase_since = run_since

    def stop_for_signal(_signal: int, _frame: Any) -> None:
        raise KeyboardInterrupt

    signal.signal(signal.SIGINT, stop_for_signal)
    signal.signal(signal.SIGTERM, stop_for_signal)
    try:
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
            previous_fixture_counts: dict[str, int] = {}
            write_json(
                args.output / f"phase-{phase_id}.json",
                {"id": phase_id, "mode": mode, "active": active_summary, "initialCounters": previous_counters},
            )
            for state in phase["states"]:
                result = run_state(
                    adb, state, args.output, int(plan.get("samplingFps", 4)),
                    phase_id, mode, previous_counters, previous_fixture_counts,
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
