"""Per-case screenrecord evidence for H19; never presentation authority."""

from __future__ import annotations

import hashlib
import shlex
import signal
import subprocess
import sys
import tempfile
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Callable, Mapping

from h19_active_document_gates import CASE_CONTRACTS
from h19_device import Adb
from h19_evidence import summarize_frames
from h19_plan import HarnessError
from run_a23_gate import create_contact_sheet, extract_frames


RECORD_SECONDS = 45
FRAME_FPS = 4
POSITIVE_CONTROL_CASE = "cold_foreground_release"


@dataclass(frozen=True)
class ExposureContract:
    """Observational video expectation; never a release condition."""

    mode: str


EXPOSURE_CONTRACTS = {
    "cold_foreground_release": ExposureContract("positive_control"),
    "background_tab_no_release": ExposureContract("critical_zero_sentinel"),
    "foreground_a_background_b": ExposureContract("observation_only"),
    "switch_during_hello": ExposureContract("critical_zero_sentinel"),
    "switch_during_challenge": ExposureContract("critical_zero_sentinel"),
    "switch_during_prove_present": ExposureContract("critical_zero_sentinel"),
    "rapid_tab_switching": ExposureContract("critical_zero_sentinel"),
    "reload": ExposureContract("observation_only"),
    "back_forward_bfcache": ExposureContract("observation_only"),
    "app_background_foreground": ExposureContract("observation_only"),
    "omnibox_focus": ExposureContract("observation_only"),
    "form_focus": ExposureContract("observation_only"),
    "portrait_landscape": ExposureContract("observation_only"),
    "process_restart": ExposureContract("observation_only"),
    "stale_replay_token_reuse": ExposureContract("observation_only"),
    "root_window_replacement": ExposureContract("observation_only"),
}

if set(EXPOSURE_CONTRACTS) != set(CASE_CONTRACTS):
    raise RuntimeError("active-document exposure contract table is incomplete")


class ActiveDocumentCaseVideoRecorder:
    """Capture, analyze, hash, and delete one video for every physical case."""

    def __init__(
        self,
        adb: Adb,
        *,
        monotonic: Callable[[], float] = time.monotonic,
        sleep: Callable[[float], None] = time.sleep,
    ) -> None:
        self._adb = adb
        self._monotonic = monotonic
        self._sleep = sleep
        self._active_case_id: str | None = None
        self._recording_started_at: float | None = None
        self._critical_started_at: float | None = None
        self._critical_windows: list[tuple[float, float]] = []
        self._active_process: subprocess.Popen[Any] | None = None

    def begin_critical_window(self, case_id: str) -> None:
        """Mark a typed no-release interval inside the per-case recording."""

        if (
            self._active_case_id != case_id
            or self._recording_started_at is None
            or EXPOSURE_CONTRACTS.get(case_id) != ExposureContract("critical_zero_sentinel")
            or self._critical_started_at is not None
        ):
            raise HarnessError("active-document critical exposure start invalid")
        self._critical_started_at = self._monotonic() - self._recording_started_at

    def end_critical_window(self, case_id: str) -> None:
        """Close a no-release interval after one evidence-only frame period."""

        if self._active_case_id != case_id or self._critical_started_at is None:
            raise HarnessError("active-document critical exposure end invalid")
        # The typed case is already terminal.  This bounded wait only ensures
        # the observational recording samples that terminal protected state.
        self._sleep(1 / FRAME_FPS)
        ended_at = self._monotonic() - float(self._recording_started_at)
        if ended_at <= self._critical_started_at:
            raise HarnessError("active-document critical exposure interval empty")
        if self._active_process is None:
            raise HarnessError("active-document critical recorder missing")
        if self._active_process.poll() is not None:
            raise HarnessError(
                "active-document screenrecord ended before critical terminal"
            )
        self._critical_windows.append((self._critical_started_at, ended_at))
        self._critical_started_at = None
        # Stop the critical recording before the scenario restores a released
        # tab.  Consequently every decoded frame is from pre-roll through the
        # typed no-release terminal; no Popen-time/MP4-PTS conversion exists.
        _finish_recording(self._active_process)

    def run_case(
        self,
        case_id: str,
        execute: Callable[[], dict[str, Any]],
    ) -> dict[str, Any]:
        remote = f"/sdcard/glosh-h19-active-document-{case_id}.mp4"
        self._adb.shell("rm", "-f", remote, check=False)
        command = [
            "screenrecord",
            "--bit-rate",
            "4000000",
            "--time-limit",
            str(RECORD_SECONDS),
            remote,
        ]
        recording_started_at = self._monotonic()
        process = subprocess.Popen(
            [*self._adb.prefix, "shell", " ".join(shlex.quote(item) for item in command)],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        self._active_case_id = case_id
        self._recording_started_at = recording_started_at
        self._critical_started_at = None
        self._critical_windows = []
        self._active_process = process
        case_error: tuple[type[BaseException], BaseException, Any] | None = None
        analysis_error: tuple[type[BaseException], BaseException, Any] | None = None
        result: dict[str, Any] | None = None
        evidence: dict[str, Any] | None = None
        try:
            try:
                _wait_recording_ready(
                    self._adb,
                    process,
                    remote,
                    monotonic=self._monotonic,
                    sleep=self._sleep,
                )
                result = execute()
                critical_mode = (
                    EXPOSURE_CONTRACTS[case_id].mode == "critical_zero_sentinel"
                )
                if process.poll() is not None and not (
                    critical_mode and self._critical_windows
                ):
                    raise HarnessError(
                        "active-document screenrecord ended before typed case completion"
                    )
                # Evidence sampling only. This cannot grant or revoke authority;
                # the typed case has already reached its terminal decision.
                if not critical_mode:
                    self._sleep(2 / FRAME_FPS)
            except BaseException:
                case_error = sys.exc_info()  # type: ignore[assignment]
            finally:
                _finish_recording(process)

            # Analyze even when pre-roll or the typed case failed if Android
            # produced a recording.  That preserves useful bounded evidence,
            # while the original execution failure remains authoritative.
            try:
                if self._critical_started_at is not None:
                    raise HarnessError("active-document critical exposure interval left open")
                evidence = self._analyze(
                    case_id,
                    remote,
                    critical_windows=tuple(self._critical_windows),
                )
            except BaseException:
                analysis_error = sys.exc_info()  # type: ignore[assignment]
        finally:
            _finish_recording(process)
            self._adb.shell("rm", "-f", remote, check=False)
            self._active_case_id = None
            self._recording_started_at = None
            self._critical_started_at = None
            self._critical_windows = []
            self._active_process = None
        if case_error is not None:
            _, error, traceback = case_error
            raise error.with_traceback(traceback)
        if analysis_error is not None:
            _, error, traceback = analysis_error
            raise error.with_traceback(traceback)
        if result is None or evidence is None:
            raise HarnessError("active-document case or visual evidence missing")
        checked = assert_visual_evidence(case_id, evidence)
        combined = dict(result)
        combined["exposureEvidence"] = checked
        return combined

    def _analyze(
        self,
        case_id: str,
        remote: str,
        *,
        critical_windows: tuple[tuple[float, float], ...],
    ) -> dict[str, Any]:
        with tempfile.TemporaryDirectory(prefix=f"glosh-h19-{case_id}-") as directory:
            root = Path(directory)
            video = root / "screenrecord.mp4"
            frames_directory = root / "frames"
            contact = root / "contact-sheet.png"
            self._adb.run("pull", remote, str(video), timeout=90)
            if not video.is_file() or video.stat().st_size <= 0:
                raise HarnessError("active-document screenrecord was not pulled")
            frames = extract_frames(video, frames_directory, FRAME_FPS)
            if not frames:
                raise HarnessError("active-document screenrecord has no analyzable frames")
            summary = summarize_frames(frames)
            contact_summary = create_contact_sheet(frames, contact)
            return bounded_visual_summary(
                summary,
                video_sha256=hashlib.sha256(video.read_bytes()).hexdigest(),
                contact_sha256=str(contact_summary["sha256"]),
                critical_windows=critical_windows,
                critical_all_frames=(
                    EXPOSURE_CONTRACTS[case_id].mode == "critical_zero_sentinel"
                ),
            )


def bounded_visual_summary(
    frame_summary: Mapping[str, Any],
    *,
    video_sha256: str,
    contact_sha256: str,
    critical_windows: tuple[tuple[float, float], ...] = (),
    critical_all_frames: bool = False,
) -> dict[str, Any]:
    """Retain aggregate indexes/hashes and discard videos, sheets, and frames."""

    def indexes(name: str) -> list[int]:
        value = frame_summary.get(name, [])
        if not isinstance(value, list) or any(not isinstance(item, int) or item < 0 for item in value):
            raise HarnessError(f"invalid visual evidence field: {name}")
        return list(value)

    frame_count = int(frame_summary.get("frameCount", 0))
    if frame_count <= 0:
        raise HarnessError("visual evidence frame count is invalid")
    indexed_fields = {
        name: indexes(name)
        for name in (
            "controlledSentinelLikeVisibleFrames",
            "opaqueSurfaceVisibleFrames",
            "auditPlaceholderVisibleFrames",
            "safeFixtureVisibleFrames",
            "blockedPlaceholderVisibleFrames",
        )
    }
    if any(index >= frame_count for values in indexed_fields.values() for index in values):
        raise HarnessError("visual evidence frame index is out of range")
    critical_indexes: set[int] = set(range(frame_count)) if critical_all_frames else set()
    bounded_windows: list[dict[str, Any]] = []
    for start_seconds, end_seconds in critical_windows:
        if start_seconds < 0 or end_seconds <= start_seconds:
            raise HarnessError("invalid critical exposure window")
        bounded_windows.append(
            {
                "startMillis": round(start_seconds * 1000),
                "endMillis": round(end_seconds * 1000),
                "sampledFrameCount": frame_count if critical_all_frames else 0,
            }
        )
    critical_sorted = sorted(critical_indexes)
    sentinel = indexed_fields["controlledSentinelLikeVisibleFrames"]
    return {
        "observationalOnly": True,
        "usedForAuthority": False,
        "samplingFps": FRAME_FPS,
        "frameCount": frame_count,
        "controlledSentinelVisibleFrames": sentinel,
        "opaqueSurfaceVisibleFrames": indexed_fields["opaqueSurfaceVisibleFrames"],
        "auditPlaceholderVisibleFrames": indexed_fields["auditPlaceholderVisibleFrames"],
        "safeFixtureVisibleFrames": indexed_fields["safeFixtureVisibleFrames"],
        "blockedPlaceholderVisibleFrames": indexed_fields["blockedPlaceholderVisibleFrames"],
        "criticalWindows": bounded_windows,
        "criticalFrameSelection": (
            "entire_recording_preroll_to_terminal" if critical_all_frames else "not_applicable"
        ),
        "criticalFrameCount": len(critical_sorted),
        "criticalSentinelVisibleFrames": [index for index in sentinel if index in critical_indexes],
        "videoSha256": video_sha256,
        "contactSheetSha256": contact_sha256,
        "rawFramesPersisted": False,
        "rawVideoPersisted": False,
        "contactSheetPersisted": False,
    }


def assert_visual_evidence(case_id: str, evidence: Mapping[str, Any]) -> dict[str, Any]:
    """Fail closed on missing or invalid physical exposure evidence."""

    if case_id not in CASE_CONTRACTS:
        raise HarnessError("unknown active-document visual-evidence case")
    if evidence.get("observationalOnly") is not True or evidence.get("usedForAuthority") is not False:
        raise HarnessError("visual evidence authority boundary invalid")
    if int(evidence.get("frameCount", 0)) <= 0:
        raise HarnessError("visual evidence missing")
    for field in ("videoSha256", "contactSheetSha256"):
        value = evidence.get(field)
        if not isinstance(value, str) or len(value) != 64:
            raise HarnessError("visual evidence hash invalid")
    if any(
        evidence.get(field) is not False
        for field in ("rawFramesPersisted", "rawVideoPersisted", "contactSheetPersisted")
    ):
        raise HarnessError("raw visual evidence retention is not allowed")
    sentinel = evidence.get("controlledSentinelVisibleFrames")
    safe = evidence.get("safeFixtureVisibleFrames")
    if not isinstance(sentinel, list) or not isinstance(safe, list):
        raise HarnessError("visual analyzer result invalid")
    contract = EXPOSURE_CONTRACTS[case_id]
    if contract.mode == "critical_zero_sentinel":
        windows = evidence.get("criticalWindows")
        critical_sentinel = evidence.get("criticalSentinelVisibleFrames")
        if (
            not isinstance(windows, list)
            or not windows
            or int(evidence.get("criticalFrameCount", 0)) <= 0
            or not isinstance(critical_sentinel, list)
        ):
            raise HarnessError("critical exposure evidence missing")
        if critical_sentinel:
            raise HarnessError("sentinel visible during critical no-release interval")
    if contract.mode == "positive_control" and not safe:
        raise HarnessError("released positive control was not visible")
    return dict(evidence)


def _finish_recording(process: subprocess.Popen[Any]) -> None:
    if process.poll() is not None:
        return
    process.send_signal(signal.SIGINT)
    try:
        process.wait(timeout=8)
    except subprocess.TimeoutExpired:
        process.terminate()
        try:
            process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=5)


def _wait_recording_ready(
    adb: Adb,
    process: subprocess.Popen[Any],
    remote: str,
    *,
    timeout_seconds: float = 3.0,
    monotonic: Callable[[], float] = time.monotonic,
    sleep: Callable[[float], None] = time.sleep,
) -> None:
    """Evidence-only pre-roll: live process + remote file + one frame interval."""

    started = monotonic()
    deadline = started + timeout_seconds
    while monotonic() < deadline:
        if process.poll() is not None:
            raise HarnessError("active-document screenrecord exited during pre-roll")
        listing = adb.shell("ls", "-l", remote, check=False)
        interval_elapsed = monotonic() - started >= 1 / FRAME_FPS
        if remote in listing and interval_elapsed:
            return
        sleep(min(0.05, max(0.0, deadline - monotonic())))
    raise HarnessError("active-document screenrecord pre-roll was not observed")
