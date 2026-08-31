#!/usr/bin/env python3
"""Focused A23 gate for H19 active-document presentation authority.

The reviewed gate consists of sixteen typed cases.  Every case is described by
``CaseSpec`` below.  A physical adapter must explicitly advertise a safe action
for every selected case before the runner mutates the device.  Missing gestures
therefore fail in preflight instead of being silently skipped or simulated.

Only typed, allow-listed H19 events are parsed.  Logcat text, URLs and runner
capabilities remain process-local and are never written to the JSON artifact.
This runner expects an already-active H19 DEV session; it does not start, stop,
or reconfigure the data plane.
"""

from __future__ import annotations

import argparse
import json
import re
import secrets
import time
from pathlib import Path
from typing import Any, Callable, Mapping

from h19_active_document_physical_cases import ActiveDocumentPhysicalCases
from h19_active_document_tab_cases import ActiveDocumentTabCases
from h19_active_document_video_evidence import ActiveDocumentCaseVideoRecorder
from h19_active_document_runner_support import (
    SCHEMA,
    CASE_SPECS,
    COLD_CASE,
    RACE_CASE,
    ActiveDocumentGateError,
    ActiveDocumentDevicePort,
    Adb,
    AdbActiveDocumentDevice,
    CaseSpec,
    DumpHoldCommand,
    FocusedActiveDocumentGateError,
    RunnerConfig,
    _binding,
    _case_events,
    claim_supersession,
    _event_binding,
    _event_claim,
    _event_sequence,
    _last_event,
    _metrics,
    _status,
    controlled_navigation_url,
    dump_hold_command,
    locate_adb,
    sha256_text,
    summarize_active_document_logs,
    top_resumed_package,
    verify_active_document_case,
)


class ActiveDocumentA23GateRunner(ActiveDocumentPhysicalCases, ActiveDocumentTabCases):
    def __init__(
        self,
        device: ActiveDocumentDevicePort,
        *,
        config: RunnerConfig = RunnerConfig(),
        monotonic: Callable[[], float] = time.monotonic,
        sleep: Callable[[float], None] = time.sleep,
        nonce_factory: Callable[[], str] = lambda: secrets.token_hex(16),
        case_specs: tuple[CaseSpec, ...] = CASE_SPECS,
        visual_recorder: ActiveDocumentCaseVideoRecorder | None = None,
    ) -> None:
        config.validate()
        self._device = device
        self._config = config
        self._monotonic = monotonic
        self._sleep = sleep
        self._nonce_factory = nonce_factory
        self._case_specs = case_specs
        self._visual_recorder = visual_recorder
        self._navigation_sequence = 0
        self._current_release: dict[str, Any] | None = None

    def run(self, run_nonce: str) -> dict[str, Any]:
        if not re.fullmatch(r"[0-9a-f]{32}", run_nonce):
            raise FocusedActiveDocumentGateError("invalid_run_nonce")
        device_summary = dict(self._device.device_summary())
        self._preflight_case_actions()
        cases = [self._run_case(spec, run_nonce) for spec in self._case_specs]
        evidence = {
            "schema": SCHEMA,
            "status": "PASS",
            "device": device_summary,
            "cases": cases,
            "crossTabRelease": 0,
            "rawLogsPersisted": False,
            "rawUrlsPersisted": False,
            "capabilitiesPersisted": False,
            "perCaseVisualEvidence": self._visual_recorder is not None,
            "screenrecordAttempted": self._visual_recorder is not None,
        }
        assert_aggregate_evidence(evidence)
        return evidence

    def _preflight_case_actions(self) -> None:
        supported = self._device.supported_case_actions()
        for spec in self._case_specs:
            if spec.action not in supported or spec.runner_method is None:
                raise FocusedActiveDocumentGateError("physical_case_action_unavailable")

    def _run_case(self, spec: CaseSpec, run_nonce: str) -> dict[str, Any]:
        method = getattr(self, str(spec.runner_method), None)
        if method is None or not callable(method):
            raise FocusedActiveDocumentGateError("physical_case_action_unavailable")
        self._device.assert_chrome_top_resumed()
        if self._visual_recorder is None:
            result = method(spec, run_nonce)
        else:
            result = self._visual_recorder.run_case(
                spec.case_id,
                lambda: method(spec, run_nonce),
            )
        self._device.assert_chrome_top_resumed()
        return result

    def _begin_critical_exposure(self, case_id: str) -> None:
        if self._visual_recorder is not None:
            self._visual_recorder.begin_critical_window(case_id)

    def _end_critical_exposure(self, case_id: str) -> None:
        if self._visual_recorder is not None:
            self._visual_recorder.end_critical_window(case_id)

    def _target(self, run_nonce: str) -> str:
        self._navigation_sequence += 1
        return controlled_navigation_url(run_nonce, self._navigation_sequence)

    def _baseline(self, since: str) -> tuple[dict[str, Any], dict[str, int]]:
        self._device.request_status()
        summary = summarize_active_document_logs(self._device.read_typed_logs(since))
        return summary, _metrics(summary)

    def _wait_for(
        self,
        since: str,
        predicate: Callable[[Mapping[str, Any]], bool],
        timeout_seconds: float,
        timeout_code: str,
    ) -> dict[str, Any]:
        deadline = self._monotonic() + timeout_seconds
        last_summary: dict[str, Any] = summarize_active_document_logs("")
        while self._monotonic() < deadline:
            last_summary = summarize_active_document_logs(self._device.read_typed_logs(since))
            if predicate(last_summary):
                return last_summary
            self._sleep(self._config.poll_interval_seconds)
        raise FocusedActiveDocumentGateError(timeout_code)

    def _terminal_summary(self, since: str) -> dict[str, Any]:
        self._device.request_status()
        return summarize_active_document_logs(self._device.read_typed_logs(since))

    def _wait_for_structural_status(
        self,
        since: str,
        predicate: Callable[[Mapping[str, Any]], bool],
        timeout_code: str,
    ) -> tuple[dict[str, Any], Mapping[str, Any]]:
        deadline = self._monotonic() + self._config.case_timeout_seconds
        while self._monotonic() < deadline:
            summary = self._terminal_summary(since)
            status = _status(summary)
            if predicate(status):
                return summary, status
            self._sleep(self._config.poll_interval_seconds)
        raise FocusedActiveDocumentGateError(timeout_code)

    def _wait_for_stable_terminal(
        self,
        since: str,
        *,
        expected_release_current: int,
        timeout_code: str,
    ) -> dict[str, Any]:
        deadline = self._monotonic() + self._config.case_timeout_seconds
        previous: Mapping[str, Any] | None = None
        while self._monotonic() < deadline:
            summary = self._terminal_summary(since)
            status = _status(summary)
            metrics = _metrics(summary)
            terminal = (
                metrics["releaseCurrent"] == expected_release_current
                and metrics["alphaTransitionsOutstanding"] == 0
                and int(status.get("pendingHandshake", -1)) == 0
                and status.get("holdPhase") == "idle"
            )
            if terminal and previous == status:
                return summary
            previous = dict(status) if terminal else None
            self._sleep(self._config.poll_interval_seconds)
        raise FocusedActiveDocumentGateError(timeout_code)

    def _summary_with_current_release(self, summary: Mapping[str, Any]) -> dict[str, Any]:
        """Bind releaseCurrent to an exactly observed cryptographic claim and native root.

        A log marker starts a fresh evidence window, so focus/rotation cases may
        legitimately contain no new release event.  The runner may carry the
        last physically observed release across cases, but only while its
        current claim still matches the typed native window/root status.
        """

        result = dict(summary)
        metrics = _metrics(result)
        status = _status(result)
        candidate = result.get("currentBinding")
        if not isinstance(candidate, Mapping):
            candidate = self._current_release
        if metrics["releaseCurrent"] == 0:
            result["currentBinding"] = None
            return result
        foreground = _binding(status, "foregroundBinding")
        if foreground is None or _event_binding(candidate) != foreground:
            raise FocusedActiveDocumentGateError("current_release_binding_unproven")
        document_sequence = int(candidate.get("documentSequence", 0))
        if document_sequence <= 0:
            raise FocusedActiveDocumentGateError("current_release_document_unproven")
        result["currentBinding"] = dict(candidate)
        return result

    def _remember_current_release(self, summary: Mapping[str, Any]) -> None:
        current = summary.get("currentBinding")
        if not isinstance(current, Mapping) or _event_binding(current) is None:
            return
        if int(current.get("documentSequence", 0)) <= 0:
            return
        self._current_release = dict(current)

    def _current_identity(self, since: str) -> tuple[dict[str, Any], int, tuple[int, str]]:
        summary = self._summary_with_current_release(
            self._wait_for_stable_terminal(
                since,
                expected_release_current=1,
                timeout_code="current_document_terminal_timeout",
            )
        )
        current = summary.get("currentBinding")
        document_sequence = int(current.get("documentSequence", 0)) if isinstance(current, Mapping) else 0
        binding = _binding(_status(summary), "foregroundBinding")
        if document_sequence <= 0 or binding is None:
            raise FocusedActiveDocumentGateError("current_document_identity_missing")
        self._remember_current_release(summary)
        return summary, document_sequence, binding

    def _label_case(self, spec: CaseSpec) -> None:
        """Select the DEV case id without creating or delaying authority."""

        stage = spec.hold_stage or "hello_accepted"
        nonce = self._nonce_factory()
        arm = dump_hold_command("arm", spec.case_id, stage, nonce)
        cancel = dump_hold_command("cancel", spec.case_id, stage, nonce)
        if "result=active_document_hold_armed" not in self._device.send_hold(arm):
            raise FocusedActiveDocumentGateError("case_label_arm_rejected")
        if "result=active_document_hold_cancelled" not in self._device.send_hold(cancel):
            raise FocusedActiveDocumentGateError("case_label_cancel_rejected")

    def _wait_for_case_release_count(
        self,
        since: str,
        case_id: str,
        minimum: int,
        timeout_code: str,
    ) -> dict[str, Any]:
        return self._wait_for(
            since,
            lambda summary: sum(
                event.get("phase") == "active_document_released"
                for event in _case_events(summary, case_id)
            )
            >= minimum,
            self._config.case_timeout_seconds,
            timeout_code,
        )

    def _verify(
        self,
        spec: CaseSpec,
        summary: Mapping[str, Any],
        baseline: Mapping[str, int],
        *,
        expected_document_sequence: int | None = None,
        forbidden_document_sequences: tuple[int, ...] = (),
        expected_claim: tuple[str, int, int, int, int, str] | None = None,
        forbidden_claims: tuple[tuple[str, int, int, int, int, str], ...] = (),
        error_code: str,
    ) -> dict[str, Any]:
        prepared = self._summary_with_current_release(summary)
        try:
            verification = verify_active_document_case(
                spec.case_id,
                prepared,
                baseline,
                expected_current_document_sequence=expected_document_sequence,
                forbidden_release_document_sequences=forbidden_document_sequences,
                expected_current_claim=expected_claim,
                forbidden_release_claims=forbidden_claims,
            )
        except ActiveDocumentGateError as error:
            raise FocusedActiveDocumentGateError(error_code) from error
        self._remember_current_release(prepared)
        return verification

    def _wait_for_claim_supersession(
        self,
        since: str,
        spec: CaseSpec,
        held_claim: tuple[str, int, int, int, int, str],
        after_event_sequence: int,
        timeout_code: str,
    ) -> tuple[
        dict[str, Any],
        Mapping[str, Any],
        Mapping[str, Any],
        tuple[str, int, int, int, int, str],
    ]:
        summary = self._wait_for(
            since,
            lambda value: claim_supersession(
                value,
                spec.case_id,
                held_claim,
                after_event_sequence,
            )
            is not None,
            self._config.case_timeout_seconds,
            timeout_code,
        )
        pair = claim_supersession(summary, spec.case_id, held_claim, after_event_sequence)
        if pair is None:
            raise FocusedActiveDocumentGateError(timeout_code)
        terminal, hello = pair
        next_claim = _event_claim(hello)
        if next_claim is None:
            raise FocusedActiveDocumentGateError("superseding_claim_incomplete")
        return summary, terminal, hello, next_claim

    def _wait_for_claim_release(
        self,
        since: str,
        case_id: str,
        claim: tuple[str, int, int, int, int, str],
        after_event_sequence: int,
        timeout_code: str,
    ) -> tuple[dict[str, Any], Mapping[str, Any]]:
        summary = self._wait_for(
            since,
            lambda value: any(
                event.get("phase") == "active_document_released"
                and int(event.get("eventSequence", 0)) > after_event_sequence
                and _event_claim(event) == claim
                for event in _case_events(value, case_id)
            ),
            self._config.case_timeout_seconds,
            timeout_code,
        )
        releases = [
            event
            for event in _case_events(summary, case_id)
            if event.get("phase") == "active_document_released"
            and int(event.get("eventSequence", 0)) > after_event_sequence
            and _event_claim(event) == claim
        ]
        if len(releases) != 1:
            raise FocusedActiveDocumentGateError("claim_release_not_one_shot")
        return summary, releases[0]

    def _wait_for_transferred_hold(
        self,
        since: str,
        spec: CaseSpec,
        hold_digest_prefix: str,
        after_event_sequence: int,
        timeout_code: str,
    ) -> tuple[dict[str, Any], int]:
        summary = self._wait_for(
            since,
            lambda value: any(
                event.get("phase") == "active_document_hold_reached"
                and event.get("holdStage") == spec.hold_stage
                and event.get("holdDigestPrefix") == hold_digest_prefix
                and int(event.get("eventSequence", 0)) > after_event_sequence
                for event in _case_events(value, spec.case_id)
            ),
            self._config.case_timeout_seconds,
            timeout_code,
        )
        sequences = [
            int(event.get("eventSequence", 0))
            for event in _case_events(summary, spec.case_id)
            if event.get("phase") == "active_document_hold_reached"
            and event.get("holdStage") == spec.hold_stage
            and event.get("holdDigestPrefix") == hold_digest_prefix
            and int(event.get("eventSequence", 0)) > after_event_sequence
        ]
        if len(sequences) != 1:
            raise FocusedActiveDocumentGateError("transferred_hold_not_one_shot")
        return summary, sequences[0]

    def _wait_for_reactivated_document_release(
        self,
        since: str,
        case_id: str,
        document_sequence: int,
        previous_claim: tuple[str, int, int, int, int, str],
        after_event_sequence: int,
        timeout_code: str,
    ) -> tuple[dict[str, Any], tuple[str, int, int, int, int, str]]:
        def is_reactivation(event: Mapping[str, Any]) -> bool:
            claim = _event_claim(event)
            return (
                claim is not None
                and claim[:4] == previous_claim[:4]
                and claim[5] == previous_claim[5]
                and claim[4] > previous_claim[4]
            )

        summary = self._wait_for(
            since,
            lambda value: any(
                event.get("phase") == "active_document_released"
                and int(event.get("documentSequence", 0)) == document_sequence
                and int(event.get("eventSequence", 0)) > after_event_sequence
                and is_reactivation(event)
                for event in _case_events(value, case_id)
            ),
            self._config.case_timeout_seconds,
            timeout_code,
        )
        releases = [
            event
            for event in _case_events(summary, case_id)
            if event.get("phase") == "active_document_released"
            and int(event.get("documentSequence", 0)) == document_sequence
            and int(event.get("eventSequence", 0)) > after_event_sequence
            and is_reactivation(event)
        ]
        if len(releases) != 1:
            raise FocusedActiveDocumentGateError("reactivated_claim_release_not_one_shot")
        claim = _event_claim(releases[0])
        if claim is None:
            raise FocusedActiveDocumentGateError("reactivated_claim_incomplete")
        return summary, claim

    def _release_event_identity(
        self,
        summary: Mapping[str, Any],
        case_id: str,
    ) -> tuple[int, tuple[int, str]]:
        released = _last_event(summary, case_id, "active_document_released")
        document_sequence = int(released.get("documentSequence", 0)) if released else 0
        binding = _event_binding(released)
        if document_sequence <= 0 or binding is None:
            raise FocusedActiveDocumentGateError("released_document_identity_missing")
        return document_sequence, binding

    def _arm_hold(
        self,
        spec: CaseSpec,
    ) -> tuple[DumpHoldCommand, DumpHoldCommand, DumpHoldCommand, str]:
        stage = spec.hold_stage or "hello_accepted"
        nonce = self._nonce_factory()
        return (
            dump_hold_command("arm", spec.case_id, stage, nonce),
            dump_hold_command("release", spec.case_id, stage, nonce),
            dump_hold_command("cancel", spec.case_id, stage, nonce),
            sha256_text(nonce)[:12],
        )

    def _wait_for_hold(
        self,
        since: str,
        spec: CaseSpec,
        hold_digest_prefix: str,
    ) -> tuple[dict[str, Any], int, tuple[int, str], tuple[str, int, int, int, int, str], int]:
        summary = self._wait_for(
            since,
            lambda value: any(
                event.get("phase") == "active_document_hold_reached"
                and event.get("holdStage") == spec.hold_stage
                and event.get("holdDigestPrefix") == hold_digest_prefix
                for event in _case_events(value, spec.case_id)
            ),
            self._config.hold_timeout_seconds,
            "active_document_hold_timeout",
        )
        accepted_phase = {
            "hello_accepted": "active_hello_accepted",
            "challenge_issued": "challenge_issued",
            "proof_accepted": "proof_accepted",
            "present_precommit": "proof_accepted",
            "present_postcommit": "proof_accepted",
        }.get(str(spec.hold_stage))
        accepted = _last_event(summary, spec.case_id, str(accepted_phase))
        document_sequence = int(accepted.get("documentSequence", 0)) if accepted else 0
        event_binding = _event_binding(accepted)
        event_claim = _event_claim(accepted)
        if document_sequence <= 0 or event_binding is None or event_claim is None:
            raise FocusedActiveDocumentGateError("held_document_identity_missing")
        summary, status = self._wait_for_structural_status(
            since,
            lambda value: value.get("holdPhase") == "reached"
            and int(value.get("pendingHandshake", -1)) == 1,
            "held_structural_status_timeout",
        )
        foreground = _binding(status, "foregroundBinding")
        attempt = _binding(status, "attemptBinding")
        if foreground != event_binding or attempt != event_binding:
            raise FocusedActiveDocumentGateError("held_structural_binding_mismatch")
        event_sequence = _event_sequence(summary)
        if event_sequence <= 0:
            raise FocusedActiveDocumentGateError("held_event_sequence_missing")
        return summary, document_sequence, event_binding, event_claim, event_sequence

def assert_aggregate_evidence(value: Mapping[str, Any]) -> None:
    """Reject accidental raw device/log/URL/capability persistence."""

    encoded = json.dumps(value, sort_keys=True, separators=(",", ":"))
    lowered = encoded.lower()
    forbidden = (
        "http://",
        "https://",
        "chromemediashieldactivedocument",
        "protocol=active_document_v3",
        "active_document_hold_nonce",
        "readytoken",
        "challengedigest=",
    )
    if any(item in lowered for item in forbidden):
        raise FocusedActiveDocumentGateError("aggregate_evidence_contains_sensitive_data")
    if value.get("rawLogsPersisted") is not False or value.get("rawUrlsPersisted") is not False:
        raise FocusedActiveDocumentGateError("aggregate_evidence_retention_contract_missing")
    if value.get("capabilitiesPersisted") is not False:
        raise FocusedActiveDocumentGateError("aggregate_capability_retention_contract_missing")


def write_aggregate_evidence(path: Path, evidence: Mapping[str, Any]) -> None:
    assert_aggregate_evidence(evidence)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(evidence, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def _arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True, help="exact ADB serial; never persisted raw")
    parser.add_argument("--output", type=Path, required=True, help="aggregate JSON output")
    parser.add_argument("--case-timeout-seconds", type=float, default=15.0)
    parser.add_argument("--hold-timeout-seconds", type=float, default=2.5)
    parser.add_argument(
        "--screenrecord",
        action="store_true",
        help="opt in to on-device video; managed devices may prohibit it",
    )
    return parser.parse_args()


def main() -> int:
    args = _arguments()
    config = RunnerConfig(
        case_timeout_seconds=args.case_timeout_seconds,
        hold_timeout_seconds=args.hold_timeout_seconds,
    )
    adb = Adb(locate_adb(), args.serial)
    runner = ActiveDocumentA23GateRunner(
        AdbActiveDocumentDevice(adb, args.serial),
        config=config,
        visual_recorder=ActiveDocumentCaseVideoRecorder(adb) if args.screenrecord else None,
    )
    try:
        evidence = runner.run(secrets.token_hex(16))
        assert_aggregate_evidence(evidence)
    except FocusedActiveDocumentGateError as error:
        failure = {
            "schema": SCHEMA,
            "status": "FAILED",
            "failureCode": error.code,
            "rawLogsPersisted": False,
            "rawUrlsPersisted": False,
            "capabilitiesPersisted": False,
        }
        write_aggregate_evidence(args.output, failure)
        return 2
    except Exception:
        failure = {
            "schema": SCHEMA,
            "status": "FAILED",
            "failureCode": "runner_operation_failed",
            "rawLogsPersisted": False,
            "rawUrlsPersisted": False,
            "capabilitiesPersisted": False,
        }
        write_aggregate_evidence(args.output, failure)
        return 2
    write_aggregate_evidence(args.output, evidence)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
