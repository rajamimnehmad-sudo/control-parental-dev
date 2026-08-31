"""Held tab-switch and postcommit race cases for the H19 A23 gate."""

from __future__ import annotations

from typing import Any, Mapping

from h19_active_document_runner_support import (
    RACE_CASE,
    RACE_NON_CAUSAL_REASONS,
    RACE_STAGE,
    CaseSpec,
    FocusedActiveDocumentGateError,
    _binding,
    _case_events,
    _causal_case_events,
    _causal_race_events,
    _event_binding,
    _event_claim,
    _event_sequence,
    _last_event,
    _status,
    dump_hold_command,
    sha256_text,
)
from h19_active_document_gates import ActiveDocumentGateError, verify_active_document_case


class ActiveDocumentTabCases:
    """Cases whose decisive action is a cryptographically observed tab switch."""
    def _run_switch_hold(self, spec: CaseSpec, run_nonce: str) -> dict[str, Any]:
        since = self._device.mark_logs()
        _, baseline = self._baseline(since)
        arm, _, cancel, hold_digest = self._arm_hold(spec)
        armed = False
        completed = False
        switched = False
        exposure_open = False
        try:
            if "result=active_document_hold_armed" not in self._device.send_hold(arm):
                raise FocusedActiveDocumentGateError("hold_arm_rejected")
            armed = True
            self._begin_critical_exposure(spec.case_id)
            exposure_open = True
            navigation = dict(self._device.navigate_controlled(self._target(run_nonce)))
            _, document_sequence, a_binding, a_claim, before_switch = self._wait_for_hold(
                since,
                spec,
                hold_digest,
            )
            self._current_release = None
            tab = dict(self._device.open_switch_tab(self._target(run_nonce)))
            switched = True
            superseded, causal_event, b_hello, b_claim = self._wait_for_claim_supersession(
                since,
                spec,
                a_claim,
                before_switch,
                "foreground_claim_supersession_not_observed",
            )
            b_binding = _event_binding(b_hello)
            b_document = int(b_hello.get("documentSequence", 0))
            if b_binding is None or b_document <= 0:
                raise FocusedActiveDocumentGateError("superseding_document_identity_missing")
            _, transferred_sequence = self._wait_for_transferred_hold(
                since,
                spec,
                hold_digest,
                int(b_hello.get("eventSequence", 0)),
                "superseding_claim_hold_not_reached",
            )
            disposition = self._device.send_hold(cancel)
            completed = True
            if "result=active_document_hold_cancelled" not in disposition:
                raise FocusedActiveDocumentGateError("transferred_hold_cancel_result_invalid")
            summary = self._wait_for_stable_terminal(
                since,
                expected_release_current=0,
                timeout_code="switch_terminal_state_timeout",
            )
            verification = self._verify(
                spec,
                summary,
                baseline,
                forbidden_document_sequences=(document_sequence,),
                forbidden_claims=(a_claim, b_claim),
                error_code="switch_case_verification_failed",
            )
            self._end_critical_exposure(spec.case_id)
            exposure_open = False
            return {
                "caseId": spec.case_id,
                "pass": True,
                "navigation": {"targetSha256": str(navigation.get("targetSha256", ""))},
                "hold": {
                    "stage": str(spec.hold_stage),
                    "causalReason": str(causal_event.get("reason", "")),
                    "transferredEventSequence": transferred_sequence,
                },
                "tabSwitch": {
                    "claimSupersessionObserved": True,
                    "nativeBindingChanged": b_binding != a_binding,
                    "mechanism": str(tab.get("mechanism", "")),
                },
                "verification": verification,
            }
        finally:
            if exposure_open:
                self._end_critical_exposure(spec.case_id)
            if armed and not completed:
                self._device.send_hold(cancel)
            if switched:
                self._current_release = None
                self._device.restore_previous_tab()
                try:
                    restored, restored_claim = self._wait_for_reactivated_document_release(
                        since,
                        spec.case_id,
                        document_sequence,
                        a_claim,
                        int(causal_event.get("eventSequence", 0)),
                        timeout_code="switch_restore_terminal_timeout",
                    )
                    restored = self._summary_with_current_release(
                        self._wait_for_stable_terminal(
                            since,
                            expected_release_current=1,
                            timeout_code="switch_restore_structural_timeout",
                        )
                    )
                    if _event_claim(restored.get("currentBinding")) != restored_claim:
                        raise FocusedActiveDocumentGateError("switch_restore_claim_mismatch")
                    self._remember_current_release(restored)
                except FocusedActiveDocumentGateError:
                    if completed:
                        raise

    def _run_background_b(self, spec: CaseSpec, run_nonce: str) -> dict[str, Any]:
        since = self._device.mark_logs()
        _, baseline = self._baseline(since)
        current, a_document, a_binding = self._current_identity(since)
        a_claim = _event_claim(current.get("currentBinding"))
        if a_claim is None:
            raise FocusedActiveDocumentGateError("foreground_a_claim_missing")
        arm, release, cancel, hold_digest = self._arm_hold(spec)
        armed = False
        completed = False
        tab_opened = False
        try:
            if "result=active_document_hold_armed" not in self._device.send_hold(arm):
                raise FocusedActiveDocumentGateError("hold_arm_rejected")
            armed = True
            tab = dict(self._device.perform_action("open_controlled_tab", self._target(run_nonce)))
            tab_opened = True
            _, b_document, b_binding, b_claim, before_switch = self._wait_for_hold(
                since,
                spec,
                hold_digest,
            )
            if b_claim == a_claim:
                raise FocusedActiveDocumentGateError("background_tab_claim_not_distinct")
            self._current_release = None
            if not self._device.restore_previous_tab():
                raise FocusedActiveDocumentGateError("foreground_restore_command_failed")
            _, causal_event, a_hello, restored_claim = self._wait_for_claim_supersession(
                since,
                spec,
                b_claim,
                before_switch,
                "foreground_a_claim_reactivation_not_observed",
            )
            if (
                restored_claim[:4] != a_claim[:4]
                or restored_claim[5] != a_claim[5]
                or restored_claim[4] <= a_claim[4]
            ):
                raise FocusedActiveDocumentGateError("foreground_a_reactivation_claim_invalid")
            self._wait_for_transferred_hold(
                since,
                spec,
                hold_digest,
                int(a_hello.get("eventSequence", 0)),
                "foreground_a_transferred_hold_not_reached",
            )
            disposition = self._device.send_hold(release)
            completed = True
            if not any(
                value in disposition
                for value in (
                    "result=active_document_hold_released",
                    "result=active_document_hold_rejected",
                )
            ):
                raise FocusedActiveDocumentGateError("hold_release_result_invalid")
            self._wait_for_claim_release(
                since,
                spec.case_id,
                restored_claim,
                int(a_hello.get("eventSequence", 0)),
                "foreground_a_release_timeout",
            )
            summary = self._wait_for_stable_terminal(
                since,
                expected_release_current=1,
                timeout_code="foreground_a_terminal_timeout",
            )
            verification = self._verify(
                spec,
                summary,
                baseline,
                expected_document_sequence=a_document,
                forbidden_document_sequences=(b_document,),
                expected_claim=restored_claim,
                forbidden_claims=(b_claim,),
                error_code="foreground_background_verification_failed",
            )
            return {
                "caseId": spec.case_id,
                "pass": True,
                "tab": {
                    "mechanism": str(tab.get("mechanism", "")),
                    "backgroundClaimDistinct": True,
                    "nativeBindingChanged": b_binding != a_binding,
                    "causalReason": str(causal_event.get("reason", "")),
                },
                "verification": verification,
            }
        finally:
            if armed and not completed:
                self._device.send_hold(cancel)
            if tab_opened:
                current_claim = _event_claim(self._terminal_summary(since).get("currentBinding"))
                if (
                    current_claim is None
                    or current_claim[:4] != a_claim[:4]
                    or current_claim[5] != a_claim[5]
                ):
                    self._device.restore_previous_tab()

    def _run_rapid_switching(self, spec: CaseSpec, run_nonce: str) -> dict[str, Any]:
        since = self._device.mark_logs()
        _, baseline = self._baseline(since)
        claim_supersessions = 0
        forbidden: list[int] = []
        forbidden_claims: list[tuple[str, int, int, int, int, str]] = []
        exposure_open = False
        try:
            self._begin_critical_exposure(spec.case_id)
            exposure_open = True
            for _ in range(3):
                arm, _, cancel, hold_digest = self._arm_hold(spec)
                armed = False
                completed = False
                try:
                    if "result=active_document_hold_armed" not in self._device.send_hold(arm):
                        raise FocusedActiveDocumentGateError("rapid_hold_arm_rejected")
                    armed = True
                    self._device.navigate_controlled(self._target(run_nonce))
                    _, document, _, held_claim, before_switch = self._wait_for_hold(
                        since,
                        spec,
                        hold_digest,
                    )
                    forbidden.append(document)
                    forbidden_claims.append(held_claim)
                    self._current_release = None
                    self._device.open_switch_tab(self._target(run_nonce))
                    _, _, b_hello, b_claim = self._wait_for_claim_supersession(
                        since,
                        spec,
                        held_claim,
                        before_switch,
                        "rapid_claim_supersession_not_observed",
                    )
                    forbidden_claims.append(b_claim)
                    claim_supersessions += 1
                    self._wait_for_transferred_hold(
                        since,
                        spec,
                        hold_digest,
                        int(b_hello.get("eventSequence", 0)),
                        "rapid_transferred_hold_not_reached",
                    )
                    disposition = self._device.send_hold(cancel)
                    completed = True
                    if "result=active_document_hold_cancelled" not in disposition:
                        raise FocusedActiveDocumentGateError("rapid_hold_cancel_invalid")
                    self._wait_for_stable_terminal(
                        since,
                        expected_release_current=0,
                        timeout_code="rapid_current_terminal_timeout",
                    )
                finally:
                    if armed and not completed:
                        self._device.send_hold(cancel)
            self._end_critical_exposure(spec.case_id)
            exposure_open = False
        finally:
            if exposure_open:
                self._end_critical_exposure(spec.case_id)
        self._label_case(spec)
        self._device.navigate_controlled(self._target(run_nonce))
        observed = self._wait_for_case_release_count(
            since,
            spec.case_id,
            1,
            "rapid_final_release_timeout",
        )
        final_document, _ = self._release_event_identity(observed, spec.case_id)
        final_claim = _event_claim(_last_event(observed, spec.case_id, "active_document_released"))
        if final_claim is None:
            raise FocusedActiveDocumentGateError("rapid_final_claim_missing")
        summary = self._wait_for_stable_terminal(
            since,
            expected_release_current=1,
            timeout_code="rapid_terminal_timeout",
        )
        verification = self._verify(
            spec,
            summary,
            baseline,
            expected_document_sequence=final_document,
            forbidden_document_sequences=tuple(forbidden),
            expected_claim=final_claim,
            forbidden_claims=tuple(forbidden_claims),
            error_code="rapid_switch_verification_failed",
        )
        return {
            "caseId": spec.case_id,
            "pass": True,
            "claimSupersessions": claim_supersessions,
            "verification": verification,
        }


    def _run_present_postcommit_race(self, spec: CaseSpec, run_nonce: str) -> dict[str, Any]:
        since = self._device.mark_logs()
        _, baseline = self._baseline(since)
        nonce = self._nonce_factory()
        hold_digest_prefix = sha256_text(nonce)[:12]
        arm = dump_hold_command("arm", RACE_CASE, RACE_STAGE, nonce)
        cancel = dump_hold_command("cancel", RACE_CASE, RACE_STAGE, nonce)
        hold_armed = False
        hold_reached = False
        hold_cancelled = False
        switch_completed = False
        cancel_result = "not_sent"
        tab_evidence: dict[str, Any] = {}
        a_document_sequence = 0
        a_binding: tuple[int, str] | None = None
        a_claim: tuple[str, int, int, int, int, str] | None = None
        b_binding: tuple[int, str] | None = None
        b_claim: tuple[str, int, int, int, int, str] | None = None
        pre_switch_event_sequence = 0
        restore_verified = False
        exposure_open = False
        try:
            arm_result = self._device.send_hold(arm)
            if "result=active_document_hold_armed" not in arm_result:
                raise FocusedActiveDocumentGateError("hold_arm_rejected")
            hold_armed = True
            self._begin_critical_exposure(spec.case_id)
            exposure_open = True
            navigation = dict(self._device.navigate_controlled(self._target(run_nonce)))
            held_summary = self._wait_for(
                since,
                lambda summary: any(
                    event.get("phase") == "active_document_hold_reached"
                    and event.get("holdStage") == RACE_STAGE
                    and event.get("holdDigestPrefix") == hold_digest_prefix
                    for event in _case_events(summary, RACE_CASE)
                ),
                self._config.hold_timeout_seconds,
                "present_postcommit_hold_timeout",
            )
            hold_reached = True
            proof = _last_event(held_summary, RACE_CASE, "proof_accepted")
            a_document_sequence = int(proof.get("documentSequence", 0)) if proof is not None else 0
            a_claim = _event_claim(proof)
            if a_document_sequence <= 0 or a_claim is None:
                raise FocusedActiveDocumentGateError("held_document_identity_missing")

            held_summary, held_status = self._wait_for_structural_status(
                since,
                lambda status: (
                    status.get("holdPhase") == "reached"
                    and int(status.get("pendingHandshake", -1)) == 1
                ),
                "held_structural_status_timeout",
            )
            a_binding = _binding(held_status, "foregroundBinding")
            attempt_binding = _binding(held_status, "attemptBinding")
            proof_binding = (
                (
                    int(proof.get("windowId", -1)),
                    str(proof.get("rootDigestPrefix", "")),
                )
                if proof is not None
                else None
            )
            if a_binding is None or attempt_binding != a_binding or proof_binding != a_binding:
                raise FocusedActiveDocumentGateError("held_structural_binding_mismatch")
            pre_switch_event_sequence = _event_sequence(held_summary)
            if pre_switch_event_sequence <= 0:
                raise FocusedActiveDocumentGateError("held_event_sequence_missing")

            self._current_release = None
            tab_evidence = dict(self._device.open_switch_tab(self._target(run_nonce)))
            switch_completed = True
            _, causal_event, b_hello, b_claim = self._wait_for_claim_supersession(
                since,
                spec,
                a_claim,
                pre_switch_event_sequence,
                "race_claim_supersession_not_observed",
            )
            b_binding = _event_binding(b_hello)
            b_document_sequence = int(b_hello.get("documentSequence", 0))
            if b_binding is None or b_document_sequence <= 0:
                raise FocusedActiveDocumentGateError("race_superseding_document_missing")
            _, transferred_sequence = self._wait_for_transferred_hold(
                since,
                spec,
                hold_digest_prefix,
                int(b_hello.get("eventSequence", 0)),
                "race_superseding_hold_not_reached",
            )
            cancel_output = self._device.send_hold(cancel)
            hold_cancelled = True
            if "result=active_document_hold_cancelled" in cancel_output:
                cancel_result = "cancelled"
            else:
                raise FocusedActiveDocumentGateError("hold_cancel_result_invalid")

            causal_summary = self._wait_for(
                since,
                lambda summary: any(
                    event.get("phase") == "active_document_invalidated"
                    and _event_claim(event) == b_claim
                    and event.get("reason") == "hold_cancelled"
                    for event in _case_events(summary, spec.case_id)
                ),
                self._config.case_timeout_seconds,
                "race_superseding_claim_cancel_timeout",
            )
            non_causal = [
                event
                for event in _case_events(causal_summary, RACE_CASE)
                if int(event.get("documentSequence", 0)) == a_document_sequence
                and int(event.get("eventSequence", 0)) > pre_switch_event_sequence
                and event.get("reason") in RACE_NON_CAUSAL_REASONS
            ]
            if non_causal:
                raise FocusedActiveDocumentGateError("race_non_causal_invalidation")
            summary = self._wait_for_stable_terminal(
                since,
                expected_release_current=0,
                timeout_code="race_terminal_state_timeout",
            )
            try:
                verification = verify_active_document_case(
                    RACE_CASE,
                    summary,
                    baseline,
                    forbidden_release_document_sequences=(a_document_sequence,),
                    forbidden_release_claims=(a_claim, b_claim),
                )
            except ActiveDocumentGateError as error:
                raise FocusedActiveDocumentGateError("race_verification_failed") from error
            if not (hold_armed and hold_reached and switch_completed and hold_cancelled):
                raise FocusedActiveDocumentGateError("race_action_order_incomplete")
            self._end_critical_exposure(spec.case_id)
            exposure_open = False
            self._current_release = None
            if not self._device.restore_previous_tab():
                raise FocusedActiveDocumentGateError("foreground_restore_command_failed")
            _, restored_claim = self._wait_for_reactivated_document_release(
                since,
                spec.case_id,
                a_document_sequence,
                a_claim,
                int(causal_event.get("eventSequence", 0)),
                "foreground_restore_claim_not_observed",
            )
            restored_summary = self._wait_for_stable_terminal(
                since,
                expected_release_current=1,
                timeout_code="foreground_restore_terminal_timeout",
            )
            restored_summary = self._summary_with_current_release(restored_summary)
            restore_verified = _event_claim(restored_summary.get("currentBinding")) == restored_claim
            if not restore_verified:
                raise FocusedActiveDocumentGateError("foreground_restore_binding_changed")
            return {
                "caseId": RACE_CASE,
                "pass": True,
                "navigation": {"targetSha256": str(navigation.get("targetSha256", ""))},
                "heldDocumentSequence": a_document_sequence,
                "hold": {
                    "stage": RACE_STAGE,
                    "armed": True,
                    "reached": True,
                    "cancelCommandSentAfterSwitch": True,
                    "cancelDisposition": cancel_result,
                    "transferredEventSequence": transferred_sequence,
                    "preSwitchEventSequence": pre_switch_event_sequence,
                    "causalInvalidationEventSequence": int(causal_event["eventSequence"]),
                    "causalInvalidationReason": str(causal_event["reason"]),
                },
                "tabSwitch": {
                    "completedBeforeRelease": True,
                    "claimSupersessionObserved": True,
                    "nativeBindingChanged": b_binding != a_binding,
                    "restoreVerified": restore_verified,
                    "mechanism": str(tab_evidence.get("mechanism", "")),
                    "targetSha256": str(tab_evidence.get("targetSha256", "")),
                    "intentResultSha256": str(tab_evidence.get("intentResultSha256", "")),
                },
                "verification": verification,
            }
        finally:
            if exposure_open:
                self._end_critical_exposure(spec.case_id)
            if hold_armed and not hold_cancelled:
                self._device.send_hold(cancel)
            if switch_completed and not restore_verified:
                self._device.restore_previous_tab()
