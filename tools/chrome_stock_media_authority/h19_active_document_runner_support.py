"""Shared contracts, typed observations, and ADB actions for the H19 A23 gate."""

from __future__ import annotations

import re
import time
from dataclasses import dataclass
from typing import Any, Mapping, Protocol

from h19_active_document_gates import (
    ActiveDocumentGateError,
    CASE_IDS,
    DumpHoldCommand,
    dump_hold_command,
    claim_supersession,
    summarize_active_document_logs,
    verify_active_document_case,
)
from h19_device import (
    CHROME_PACKAGE,
    Adb,
    controlled_navigation_url,
    locate_adb,
    navigate,
    observed_display_rotation,
    restore_setting,
    set_and_verify_orientation,
    setting,
    sha256_text,
    tap_normalized,
)
from h19_lifecycle_gates import open_controlled_new_tab


SCHEMA = "glosh-h19-active-document-a23-gate-v3"
STATUS_ACTION = "com.contentfilter.user.chromedataplane.command.STATUS"
REPLAY_ACTION = "com.contentfilter.user.chromedataplane.command.ACTIVE_DOCUMENT_REPLAY"
COLD_CASE = "cold_foreground_release"
RACE_CASE = "switch_during_prove_present"
RACE_STAGE = "present_postcommit"
EXPECTED_MODEL = "SM-A235M"
EXPECTED_SDK = 34
RACE_CAUSAL_REASONS = frozenset(
    {
        "present_context_changed",
        "present_postcommit_context_changed",
        "invalidated_hidden",
        "invalidated_pagehide",
        "invalidated_navigation",
        "invalidated_root",
        "invalidated_surface",
        "invalidated_window",
    }
)
RACE_PRESENT_REJECTION_REASONS = frozenset(
    {
        "present_context_changed",
        "present_postcommit_context_changed",
    }
)
RACE_NON_CAUSAL_REASONS = frozenset(
    {
        "hold_timeout",
        "hold_cancelled",
        "handshake_transport_cancelled",
        "invalidated_health",
        "invalidated_stop",
    }
)
LOG_FILTERS = (
    "ChromeMediaShieldActiveDocument:I",
    "ChromeMediaShieldReady:I",
    "*:S",
)
_SAFE_FAILURE_CODE = re.compile(r"[a-z][a-z0-9_]{0,63}")
_TOP_RESUMED_ACTIVITY = re.compile(
    r"^\s*topResumedActivity=ActivityRecord\{[^\n}]*\su\d+\s+([^/\s}]+)/[^\s}]+",
    re.MULTILINE,
)


@dataclass(frozen=True)
class CaseSpec:
    """One reviewed physical case and its required concrete device action."""

    case_id: str
    action: str
    hold_stage: str | None = None
    runner_method: str | None = None


CASE_SPECS = (
    CaseSpec(CASE_IDS[0], "controlled_navigation", runner_method="_run_cold"),
    CaseSpec(CASE_IDS[1], "background_tab_rejection", "hello_accepted", "_run_switch_hold"),
    CaseSpec(CASE_IDS[2], "foreground_background_binding", "hello_accepted", "_run_background_b"),
    CaseSpec(CASE_IDS[3], "switch_during_hold", "hello_accepted", "_run_switch_hold"),
    CaseSpec(CASE_IDS[4], "switch_during_hold", "challenge_issued", "_run_switch_hold"),
    CaseSpec(
        CASE_IDS[5],
        "switch_during_hold",
        RACE_STAGE,
        runner_method="_run_present_postcommit_race",
    ),
    CaseSpec(CASE_IDS[6], "rapid_tab_switching", "hello_accepted", "_run_rapid_switching"),
    CaseSpec(CASE_IDS[7], "reload", runner_method="_run_reload"),
    CaseSpec(CASE_IDS[8], "back_forward_bfcache", runner_method="_run_back_forward"),
    CaseSpec(CASE_IDS[9], "app_background_foreground", runner_method="_run_background_foreground"),
    CaseSpec(CASE_IDS[10], "omnibox_focus", runner_method="_run_focus_continuity"),
    CaseSpec(CASE_IDS[11], "fixture_form_focus", runner_method="_run_focus_continuity"),
    CaseSpec(CASE_IDS[12], "portrait_landscape", runner_method="_run_rotation"),
    CaseSpec(CASE_IDS[13], "process_restart", runner_method="_run_process_restart"),
    CaseSpec(CASE_IDS[14], "stale_replay", runner_method="_run_stale_replay"),
    CaseSpec(CASE_IDS[15], "root_window_replacement", runner_method="_run_root_replacement"),
)

if tuple(spec.case_id for spec in CASE_SPECS) != CASE_IDS:
    raise RuntimeError("active-document physical case table is incomplete")


class FocusedActiveDocumentGateError(RuntimeError):
    """A safe, stable runner failure suitable for aggregate evidence."""

    def __init__(self, code: str) -> None:
        if not _SAFE_FAILURE_CODE.fullmatch(code):
            raise ValueError("invalid focused-gate failure code")
        super().__init__(code)
        self.code = code


class ActiveDocumentDevicePort(Protocol):
    """Narrow device seam used by the deterministic pure tests."""

    def device_summary(self) -> Mapping[str, Any]: ...

    def assert_chrome_top_resumed(self) -> None: ...

    def mark_logs(self) -> str: ...

    def request_status(self) -> None: ...

    def read_typed_logs(self, since: str) -> str: ...

    def navigate_controlled(self, target: str) -> Mapping[str, Any]: ...

    def open_switch_tab(self, target: str) -> Mapping[str, Any]: ...

    def send_hold(self, command: DumpHoldCommand) -> str: ...

    def restore_previous_tab(self) -> bool: ...

    def perform_action(self, action: str, target: str = "") -> Mapping[str, Any]: ...

    def supported_case_actions(self) -> frozenset[str]: ...


@dataclass(frozen=True)
class RunnerConfig:
    case_timeout_seconds: float = 15.0
    hold_timeout_seconds: float = 2.5
    poll_interval_seconds: float = 0.1

    def validate(self) -> None:
        if self.case_timeout_seconds <= 0:
            raise FocusedActiveDocumentGateError("invalid_case_timeout")
        if not 0 < self.hold_timeout_seconds < 4.0:
            raise FocusedActiveDocumentGateError("invalid_hold_timeout")
        if not 0 < self.poll_interval_seconds <= 0.5:
            raise FocusedActiveDocumentGateError("invalid_poll_interval")


class AdbActiveDocumentDevice:
    """Real ADB adapter; all returned evidence is already aggregated."""

    def __init__(self, adb: Adb, serial: str) -> None:
        self._adb = adb
        self._serial_digest = sha256_text(serial)
        self._orientation_restore: tuple[str | None, str | None, int] | None = None

    def device_summary(self) -> Mapping[str, Any]:
        if self._adb.run("get-state").stdout.strip() != "device":
            raise FocusedActiveDocumentGateError("adb_target_unavailable")
        model = self._adb.shell("getprop", "ro.product.model").strip()
        sdk = self._adb.shell("getprop", "ro.build.version.sdk").strip()
        if model != EXPECTED_MODEL or not sdk.isdigit() or int(sdk) != EXPECTED_SDK:
            raise FocusedActiveDocumentGateError("unexpected_physical_target")
        return {
            "serialSha256": self._serial_digest,
            "model": model,
            "sdk": int(sdk),
        }

    def assert_chrome_top_resumed(self) -> None:
        output = self._adb.shell("dumpsys", "activity", "activities", check=False, timeout=30)
        if top_resumed_package(output) != CHROME_PACKAGE:
            raise FocusedActiveDocumentGateError("chrome_not_top_resumed")

    def mark_logs(self) -> str:
        marker = self._adb.shell("date", "+%m-%d %H:%M:%S.000").strip()
        if not marker:
            raise FocusedActiveDocumentGateError("device_log_marker_unavailable")
        return marker

    def request_status(self) -> None:
        self._adb.broadcast(STATUS_ACTION)

    def read_typed_logs(self, since: str) -> str:
        return self._adb.run(
            "logcat",
            "-d",
            "-T",
            since,
            "-v",
            "brief",
            *LOG_FILTERS,
            timeout=30,
        ).stdout

    def navigate_controlled(self, target: str) -> Mapping[str, Any]:
        navigate(
            self._adb,
            {
                "navigation": "controlled",
                "controlledUrl": target,
            },
        )
        return {"targetSha256": sha256_text(target)}

    def open_switch_tab(self, target: str) -> Mapping[str, Any]:
        if not target:
            raise FocusedActiveDocumentGateError("controlled_tab_target_missing")
        return open_controlled_new_tab(self._adb, target)

    def send_hold(self, command: DumpHoldCommand) -> str:
        return self._adb.broadcast(command.action, list(command.extras))

    def restore_previous_tab(self) -> bool:
        output = self._adb.shell("input", "keyevent", "4", check=False)
        return "Error:" not in output and "Exception" not in output

    def perform_action(self, action: str, target: str = "") -> Mapping[str, Any]:
        if action == "open_controlled_tab":
            if not target:
                raise FocusedActiveDocumentGateError("controlled_tab_target_missing")
            return open_controlled_new_tab(self._adb, target)
        if action == "open_native_tab":
            if not target:
                raise FocusedActiveDocumentGateError("controlled_tab_target_missing")
            return self.open_switch_tab(target)
        if action in {"back", "forward", "reload"}:
            navigate(self._adb, {"navigation": action})
            return {"action": action}
        if action == "background":
            self._adb.shell("input", "keyevent", "3")
            return {"action": action}
        if action == "foreground":
            navigate(self._adb, {"navigation": "foreground"})
            return {"action": action}
        if action == "omnibox_focus":
            self._adb.shell("input", "keyevent", "84")
            return {"action": action, "inputObservation": self._wait_for_chrome_input("native_uri")}
        if action == "fixture_form_focus":
            # The runner first navigates to #normal-form, placing the controlled
            # input immediately below Chrome's toolbar.  No tree text/content
            # is read and no coordinate participates in release authority.
            tap_normalized(self._adb, 100, 90)
            return {"action": action, "inputObservation": self._wait_for_chrome_input("web_form")}
        if action == "dismiss_input":
            self._adb.shell("input", "keyevent", "4", check=False)
            return {"action": action}
        if action == "toggle_orientation":
            before = observed_display_rotation(self._adb)
            if before is None:
                raise FocusedActiveDocumentGateError("orientation_baseline_unavailable")
            self._orientation_restore = (
                setting(self._adb, "accelerometer_rotation"),
                setting(self._adb, "user_rotation"),
                before,
            )
            requested = "landscape" if before in {0, 2} else "portrait"
            result = set_and_verify_orientation(self._adb, requested)
            return {"action": action, "before": before, **result}
        if action == "restore_orientation":
            if self._orientation_restore is None:
                return {"action": action, "restored": False}
            accelerometer, rotation, expected_rotation = self._orientation_restore
            restore_setting(self._adb, "accelerometer_rotation", accelerometer)
            restore_setting(self._adb, "user_rotation", rotation)
            self._orientation_restore = None
            return {
                "action": action,
                "restored": True,
                "expectedRotation": expected_rotation,
                "observedRotation": observed_display_rotation(self._adb),
            }
        if action == "observe_orientation":
            return {
                "action": action,
                "observedRotation": observed_display_rotation(self._adb),
            }
        if action == "restart_chrome":
            if not target:
                raise FocusedActiveDocumentGateError("restart_target_missing")
            self._adb.shell("am", "force-stop", CHROME_PACKAGE)
            self.navigate_controlled(target)
            return {"action": action, "targetSha256": sha256_text(target)}
        if action == "replay_consumed_present":
            output = self._adb.broadcast(REPLAY_ACTION)
            outcomes = {
                "result=active_document_replay_rejected": "rejected",
                "result=active_document_replay_absent": "absent",
                "result=active_document_replay_stale": "stale",
                "result=active_document_replay_fail_closed": "fail_closed",
            }
            matches = [canonical for marker, canonical in outcomes.items() if marker in output]
            if len(matches) != 1:
                raise FocusedActiveDocumentGateError("replay_result_invalid")
            return {"action": action, "outcome": matches[0]}
        raise FocusedActiveDocumentGateError("unsupported_physical_action")

    def _chrome_input_observation(self) -> dict[str, Any]:
        value = self._adb.shell("dumpsys", "input_method", check=False, timeout=30)
        editor_payloads = re.findall(
            r"(?:mCurAttribute|mCurrentTextBoxAttribute)=EditorInfo\{([^\n]+)",
            value,
        )
        for payload in editor_payloads:
            if f"packageName={CHROME_PACKAGE}" not in payload:
                continue
            input_type_match = re.search(r"\binputType=(0x[0-9a-fA-F]+|\d+)", payload)
            if input_type_match is None:
                continue
            input_type = int(input_type_match.group(1), 0)
            variation = input_type & 0xFF0
            editor_kind = (
                "web_form"
                if variation == 0xA0
                else "native_uri"
                if variation == 0x10
                else "other_chrome_editor"
            )
            return {
                "observed": "mInputShown=true" in value,
                "chromeEditorOwned": True,
                "inputShown": "mInputShown=true" in value,
                "editorKind": editor_kind,
                "inputClass": input_type & 0xF,
                "inputVariation": variation,
            }
        return {
            "observed": False,
            "chromeEditorOwned": False,
            "inputShown": "mInputShown=true" in value,
            "editorKind": "none",
            "inputClass": 0,
            "inputVariation": 0,
        }

    def _wait_for_chrome_input(
        self,
        expected_kind: str,
        timeout_seconds: float = 2.0,
    ) -> dict[str, Any]:
        deadline = time.monotonic() + timeout_seconds
        last = self._chrome_input_observation()
        while time.monotonic() < deadline:
            last = self._chrome_input_observation()
            if last["observed"] is True and last["editorKind"] == expected_kind:
                return last
            time.sleep(0.1)
        return last

    def supported_case_actions(self) -> frozenset[str]:
        return frozenset(spec.action for spec in CASE_SPECS)


def top_resumed_package(output: str) -> str | None:
    matches = _TOP_RESUMED_ACTIVITY.findall(output)
    return matches[0] if len(matches) == 1 else None


def _metrics(summary: Mapping[str, Any]) -> dict[str, int]:
    value = summary.get("metrics")
    if not isinstance(value, Mapping):
        raise FocusedActiveDocumentGateError("active_document_status_unavailable")
    try:
        return {str(name): int(metric) for name, metric in value.items()}
    except (TypeError, ValueError) as error:
        raise FocusedActiveDocumentGateError("active_document_status_invalid") from error


def _status(summary: Mapping[str, Any]) -> Mapping[str, Any]:
    value = summary.get("status")
    if not isinstance(value, Mapping):
        raise FocusedActiveDocumentGateError("active_document_structural_status_unavailable")
    return value


def _binding(status: Mapping[str, Any], name: str) -> tuple[int, str] | None:
    value = status.get(name)
    if not isinstance(value, Mapping):
        return None
    window_id = value.get("windowId")
    root = value.get("rootDigestPrefix")
    if (
        not isinstance(window_id, int)
        or window_id < 0
        or not isinstance(root, str)
    ):
        return None
    if not root:
        return None
    return window_id, root


def _event_sequence(summary: Mapping[str, Any]) -> int:
    events = summary.get("events")
    if not isinstance(events, list):
        return 0
    return max(
        (int(event.get("eventSequence", 0)) for event in events if isinstance(event, Mapping)),
        default=0,
    )


def _causal_race_events(
    summary: Mapping[str, Any],
    document_sequence: int,
    after_event_sequence: int,
) -> list[Mapping[str, Any]]:
    matches: list[Mapping[str, Any]] = []
    for event in _case_events(summary, RACE_CASE):
        phase = event.get("phase")
        reason = event.get("reason")
        phase_reason_valid = (
            phase == "active_document_invalidated" and reason in RACE_CAUSAL_REASONS
        ) or (
            phase == "present_rejected" and reason in RACE_PRESENT_REJECTION_REASONS
        )
        if (
            phase_reason_valid
            and int(event.get("documentSequence", 0)) == document_sequence
            and int(event.get("eventSequence", 0)) > after_event_sequence
        ):
            matches.append(event)
    return matches


def _causal_case_events(
    summary: Mapping[str, Any],
    case_id: str,
    document_sequence: int,
    after_event_sequence: int,
) -> list[Mapping[str, Any]]:
    """Return only typed, post-action invalidations for one held document."""

    matches: list[Mapping[str, Any]] = []
    for event in _case_events(summary, case_id):
        phase = event.get("phase")
        reason = event.get("reason")
        if (
            (
                phase == "active_document_invalidated"
                and reason in RACE_CAUSAL_REASONS
            )
            or (
                phase in {"active_hello_rejected", "proof_rejected", "present_rejected"}
                and reason in REJECTION_REASONS
            )
        ) and int(event.get("documentSequence", 0)) == document_sequence and int(
            event.get("eventSequence", 0)
        ) > after_event_sequence:
            matches.append(event)
    return matches


def _case_events(summary: Mapping[str, Any], case_id: str) -> list[Mapping[str, Any]]:
    events = summary.get("events")
    if not isinstance(events, list):
        return []
    return [event for event in events if isinstance(event, Mapping) and event.get("caseId") == case_id]


def _last_event(
    summary: Mapping[str, Any],
    case_id: str,
    phase: str,
) -> Mapping[str, Any] | None:
    matches = [event for event in _case_events(summary, case_id) if event.get("phase") == phase]
    return matches[-1] if matches else None


REJECTION_REASONS = frozenset(
    {
        "hello_claim_invalid",
        "hello_foreground_ambiguous",
        "hello_context_stale",
        "hello_surface_failed",
        "prove_challenge_invalid",
        "prove_replay",
        "prove_context_changed",
        "prove_health_stale",
        "present_not_proved",
        "present_replay",
        "present_context_changed",
        "present_surface_not_opaque",
        "present_commit_failed",
        "present_postcommit_context_changed",
    }
)


def _event_binding(event: Mapping[str, Any] | None) -> tuple[int, str] | None:
    if not isinstance(event, Mapping):
        return None
    window_id = event.get("windowId")
    root = event.get("rootDigestPrefix")
    if not isinstance(window_id, int) or window_id < 0 or not root:
        return None
    return window_id, str(root)


def _event_claim(event: Mapping[str, Any] | None) -> tuple[str, int, int, int, int, str] | None:
    """Return the redacted cryptographic document claim carried by one typed event."""

    if not isinstance(event, Mapping):
        return None
    session = event.get("sessionDigestPrefix")
    token = event.get("tokenDigestPrefix")
    integers = (
        event.get("policyEpoch"),
        event.get("navigationSequence"),
        event.get("documentSequence"),
        event.get("lifecycle"),
    )
    if (
        not isinstance(session, str)
        or not session
        or not isinstance(token, str)
        or not token
        or any(not isinstance(value, int) or value <= 0 for value in integers)
    ):
        return None
    policy, navigation, document, lifecycle = integers
    return session, policy, navigation, document, lifecycle, token
