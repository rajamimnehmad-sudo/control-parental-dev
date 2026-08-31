"""Concrete, fail-closed physical cases for the H19 active-document A23 gate."""

from __future__ import annotations

from typing import Any, Mapping

from h19_active_document_runner_support import (
    ActiveDocumentGateError,
    COLD_CASE,
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
    _event_sequence,
    _last_event,
    _metrics,
    _status,
    dump_hold_command,
    sha256_text,
    verify_active_document_case,
)


def _document_generation_key(event: Mapping[str, Any]) -> tuple[Any, ...]:
    """Stable document identity; lifecycle is intentionally a separate axis."""

    return (
        int(event.get("policyEpoch", 0)),
        int(event.get("documentSequence", 0)),
        str(event.get("sessionDigestPrefix", "")),
        str(event.get("tokenDigestPrefix", "")),
    )


def _assert_bfcache_restore(
    original: Mapping[str, Any],
    restored: Mapping[str, Any],
    *,
    failure_code: str,
) -> None:
    if _document_generation_key(restored) != _document_generation_key(original):
        raise FocusedActiveDocumentGateError(failure_code)
    if int(restored.get("lifecycle", 0)) <= int(original.get("lifecycle", 0)):
        raise FocusedActiveDocumentGateError("bfcache_lifecycle_not_advanced")


class ActiveDocumentPhysicalCases:
    """Scenario layer; the runner supplies typed waits and evidence helpers."""
    def _run_cold(self, spec: CaseSpec, run_nonce: str) -> dict[str, Any]:
        since = self._device.mark_logs()
        _, baseline = self._baseline(since)
        target = self._target(run_nonce)
        navigation = dict(self._device.navigate_controlled(target))
        observed = self._wait_for(
            since,
            lambda summary: _last_event(summary, COLD_CASE, "active_document_released") is not None,
            self._config.case_timeout_seconds,
            "cold_release_timeout",
        )
        released = _last_event(observed, COLD_CASE, "active_document_released")
        document_sequence = int(released.get("documentSequence", 0)) if released is not None else 0
        if document_sequence <= 0:
            raise FocusedActiveDocumentGateError("cold_release_identity_missing")
        summary = self._wait_for_stable_terminal(
            since,
            expected_release_current=1,
            timeout_code="cold_terminal_state_timeout",
        )
        summary = self._summary_with_current_release(summary)
        try:
            verification = verify_active_document_case(
                COLD_CASE,
                summary,
                baseline,
                expected_current_document_sequence=document_sequence,
            )
        except ActiveDocumentGateError as error:
            raise FocusedActiveDocumentGateError("cold_release_verification_failed") from error
        self._remember_current_release(summary)
        return {
            "caseId": COLD_CASE,
            "pass": True,
            "navigation": {"targetSha256": str(navigation.get("targetSha256", ""))},
            "documentSequence": document_sequence,
            "verification": verification,
        }

    def _run_reload(self, spec: CaseSpec, run_nonce: str) -> dict[str, Any]:
        since = self._device.mark_logs()
        _, baseline = self._baseline(since)
        _, previous_document, _ = self._current_identity(since)
        self._label_case(spec)
        action = dict(self._device.perform_action("reload"))
        observed = self._wait_for_case_release_count(since, spec.case_id, 1, "reload_release_timeout")
        document, _ = self._release_event_identity(observed, spec.case_id)
        if document == previous_document:
            raise FocusedActiveDocumentGateError("reload_document_identity_reused")
        summary = self._wait_for_stable_terminal(
            since,
            expected_release_current=1,
            timeout_code="reload_terminal_timeout",
        )
        verification = self._verify(
            spec,
            summary,
            baseline,
            expected_document_sequence=document,
            error_code="reload_verification_failed",
        )
        return {"caseId": spec.case_id, "pass": True, "action": action, "verification": verification}

    def _run_back_forward(self, spec: CaseSpec, run_nonce: str) -> dict[str, Any]:
        since = self._device.mark_logs()
        _, baseline = self._baseline(since)
        current, a_document, _ = self._current_identity(since)
        a_generation = current.get("currentBinding")
        if not isinstance(a_generation, Mapping):
            raise FocusedActiveDocumentGateError("history_a_identity_missing")
        self._label_case(spec)
        self._device.navigate_controlled(self._target(run_nonce))
        navigated = self._wait_for_case_release_count(
            since,
            spec.case_id,
            1,
            "history_navigation_release_timeout",
        )
        releases = [
            event
            for event in _case_events(navigated, spec.case_id)
            if event.get("phase") == "active_document_released"
        ]
        b_generation = releases[0]
        b_document = int(b_generation.get("documentSequence", 0))
        if b_document <= 0 or b_document == a_document:
            raise FocusedActiveDocumentGateError("history_b_identity_not_distinct")
        self._device.perform_action("back")
        backed = self._wait_for_case_release_count(
            since,
            spec.case_id,
            2,
            "history_back_release_timeout",
        )
        releases = [
            event
            for event in _case_events(backed, spec.case_id)
            if event.get("phase") == "active_document_released"
        ]
        back_generation = releases[1]
        _assert_bfcache_restore(
            a_generation,
            back_generation,
            failure_code="history_back_did_not_restore_a",
        )
        self._device.perform_action("forward")
        observed = self._wait_for_case_release_count(
            since,
            spec.case_id,
            3,
            "history_forward_release_timeout",
        )
        releases = [
            event
            for event in _case_events(observed, spec.case_id)
            if event.get("phase") == "active_document_released"
        ]
        forward_generation = releases[2]
        _assert_bfcache_restore(
            b_generation,
            forward_generation,
            failure_code="history_forward_did_not_restore_b",
        )
        document = int(forward_generation.get("documentSequence", 0))
        summary = self._wait_for_stable_terminal(
            since,
            expected_release_current=1,
            timeout_code="history_terminal_timeout",
        )
        verification = self._verify(
            spec,
            summary,
            baseline,
            expected_document_sequence=document,
            error_code="history_verification_failed",
        )
        return {
            "caseId": spec.case_id,
            "pass": True,
            "releaseSteps": 3,
            "history": {
                "aDocumentSequence": a_document,
                "bDocumentSequence": b_document,
                "backRestoredA": True,
                "forwardRestoredB": True,
                "bfcacheObserved": True,
                "evidence": "same_document_capability_with_advanced_lifecycle",
            },
            "verification": verification,
        }

    def _run_background_foreground(self, spec: CaseSpec, run_nonce: str) -> dict[str, Any]:
        since = self._device.mark_logs()
        _, baseline = self._baseline(since)
        _, old_document, old_binding = self._current_identity(since)
        self._label_case(spec)
        self._device.perform_action("background")
        self._wait_for(
            since,
            lambda value: any(
                event.get("phase") in {"active_document_invalidated", "active_document_revoked"}
                and int(event.get("documentSequence", 0)) == old_document
                for event in _case_events(value, spec.case_id)
            ),
            self._config.case_timeout_seconds,
            "background_invalidation_timeout",
        )
        self._wait_for_stable_terminal(
            since,
            expected_release_current=0,
            timeout_code="background_terminal_timeout",
        )
        self._device.perform_action("foreground")
        observed = self._wait_for_case_release_count(
            since,
            spec.case_id,
            1,
            "foreground_release_timeout",
        )
        document, foreground_binding = self._release_event_identity(observed, spec.case_id)
        if document != old_document or foreground_binding != old_binding:
            raise FocusedActiveDocumentGateError("foreground_document_identity_changed")
        summary = self._wait_for_stable_terminal(
            since,
            expected_release_current=1,
            timeout_code="foreground_terminal_timeout",
        )
        verification = self._verify(
            spec,
            summary,
            baseline,
            expected_document_sequence=document,
            error_code="background_foreground_verification_failed",
        )
        return {
            "caseId": spec.case_id,
            "pass": True,
            "sameDocumentRestored": True,
            "sameRootWindowRestored": True,
            "verification": verification,
        }

    def _run_focus_continuity(self, spec: CaseSpec, run_nonce: str) -> dict[str, Any]:
        if spec.case_id == "form_focus":
            preparation_since = self._device.mark_logs()
            self._label_case(spec)
            self._device.navigate_controlled(self._target(run_nonce) + "#normal-form")
            prepared = self._wait_for_case_release_count(
                preparation_since,
                spec.case_id,
                1,
                "form_fixture_release_timeout",
            )
            prepared_document, _ = self._release_event_identity(prepared, spec.case_id)
            prepared = self._summary_with_current_release(
                self._wait_for_stable_terminal(
                    preparation_since,
                    expected_release_current=1,
                    timeout_code="form_fixture_terminal_timeout",
                )
            )
            if int(prepared["currentBinding"]["documentSequence"]) != prepared_document:
                raise FocusedActiveDocumentGateError("form_fixture_identity_mismatch")
            self._remember_current_release(prepared)
        since = self._device.mark_logs()
        _, baseline = self._baseline(since)
        _, document, before_binding = self._current_identity(since)
        self._label_case(spec)
        action_name = "omnibox_focus" if spec.case_id == "omnibox_focus" else "fixture_form_focus"
        action = dict(self._device.perform_action(action_name))
        observation = action.get("inputObservation")
        expected_kind = "native_uri" if spec.case_id == "omnibox_focus" else "web_form"
        if (
            not isinstance(observation, Mapping)
            or observation.get("observed") is not True
            or observation.get("chromeEditorOwned") is not True
            or observation.get("editorKind") != expected_kind
        ):
            raise FocusedActiveDocumentGateError("chrome_input_focus_not_observed")
        try:
            summary, status = self._wait_for_structural_status(
                since,
                lambda value: _binding(value, "foregroundBinding") == before_binding,
                "focus_binding_continuity_timeout",
            )
            summary = self._wait_for_stable_terminal(
                since,
                expected_release_current=1,
                timeout_code="focus_terminal_timeout",
            )
            if _binding(_status(summary), "foregroundBinding") != before_binding:
                raise FocusedActiveDocumentGateError("focus_binding_changed")
            verification = self._verify(
                spec,
                summary,
                baseline,
                expected_document_sequence=document,
                error_code="focus_continuity_verification_failed",
            )
            return {
                "caseId": spec.case_id,
                "pass": True,
                "inputObservation": dict(observation),
                "bindingContinuous": _binding(status, "foregroundBinding") == before_binding,
                "verification": verification,
            }
        finally:
            self._device.perform_action("dismiss_input")

    def _run_rotation(self, spec: CaseSpec, run_nonce: str) -> dict[str, Any]:
        since = self._device.mark_logs()
        _, baseline = self._baseline(since)
        _, old_document, old_binding = self._current_identity(since)
        self._label_case(spec)
        orientation = dict(self._device.perform_action("toggle_orientation"))
        before_rotation = orientation.get("before")
        after_rotation = orientation.get("observedRotation")
        if not isinstance(before_rotation, int) or not isinstance(after_rotation, int):
            raise FocusedActiveDocumentGateError("rotation_observation_missing")
        if after_rotation == before_rotation:
            raise FocusedActiveDocumentGateError("rotation_transition_not_observed")
        restore: dict[str, Any] = {}
        try:
            rotated = self._wait_for_stable_terminal(
                since,
                expected_release_current=1,
                timeout_code="rotation_terminal_timeout",
            )
            rotated = self._summary_with_current_release(rotated)
            if _binding(_status(rotated), "foregroundBinding") != old_binding:
                raise FocusedActiveDocumentGateError("rotation_root_window_changed")
        finally:
            restore = dict(self._device.perform_action("restore_orientation"))
            self._wait_for_rotation(before_rotation)

        summary = self._wait_for_stable_terminal(
            since,
            expected_release_current=1,
            timeout_code="rotation_restore_terminal_timeout",
        )
        summary = self._summary_with_current_release(summary)
        if _binding(_status(summary), "foregroundBinding") != old_binding:
            raise FocusedActiveDocumentGateError("rotation_restore_root_window_changed")
        released = _last_event(summary, spec.case_id, "active_document_released")
        if released is not None and (
            int(released.get("documentSequence", 0)) != old_document
            or _event_binding(released) != old_binding
        ):
            raise FocusedActiveDocumentGateError("rotation_document_identity_changed")
        verification = self._verify(
            spec,
            summary,
            baseline,
            expected_document_sequence=old_document,
            error_code="rotation_verification_failed",
        )
        return {
            "caseId": spec.case_id,
            "pass": True,
            "orientation": {
                "before": before_rotation,
                "after": after_rotation,
                "restored": before_rotation,
                "settingsRestored": restore.get("restored") is True,
            },
            "sameDocument": True,
            "sameRootWindow": True,
            "verification": verification,
        }

    def _wait_for_rotation(self, expected: int) -> None:
        deadline = self._monotonic() + self._config.case_timeout_seconds
        while self._monotonic() < deadline:
            observed = self._device.perform_action("observe_orientation").get("observedRotation")
            if observed == expected:
                return
            self._sleep(self._config.poll_interval_seconds)
        raise FocusedActiveDocumentGateError("rotation_restore_not_observed")

    def _run_process_restart(self, spec: CaseSpec, run_nonce: str) -> dict[str, Any]:
        return self._run_restart_case(spec, run_nonce, require_binding_change=False)

    def _run_stale_replay(self, spec: CaseSpec, run_nonce: str) -> dict[str, Any]:
        post_navigation = self._probe_post_navigation_replay_absent(spec, run_nonce)
        since = self._device.mark_logs()
        baseline_summary, baseline = self._baseline(since)
        baseline_summary, document, _ = self._current_identity(since)
        if _metrics(baseline_summary)["replayCandidate"] != 1:
            raise FocusedActiveDocumentGateError("replay_candidate_unavailable")
        self._label_case(spec)
        first = dict(self._device.perform_action("replay_consumed_present"))
        if first.get("outcome") != "rejected":
            raise FocusedActiveDocumentGateError("replay_was_not_rejected")
        after_first, _ = self._wait_for_structural_status(
            since,
            lambda status: (
                int(status["metrics"]["staleRejected"]) >= baseline["staleRejected"] + 1
                and int(status["metrics"]["staleReplayRejected"])
                >= baseline["staleReplayRejected"] + 1
                and int(status["metrics"]["replayCandidate"]) == 0
            ),
            "replay_rejection_evidence_timeout",
        )
        first_metrics = _metrics(after_first)
        release_count = sum(
            event.get("phase") == "active_document_released"
            for event in _case_events(after_first, spec.case_id)
        )
        if release_count != 0:
            raise FocusedActiveDocumentGateError("replay_created_release")
        second = dict(self._device.perform_action("replay_consumed_present"))
        if second.get("outcome") != "absent":
            raise FocusedActiveDocumentGateError("replay_candidate_not_one_shot")
        summary = self._wait_for_stable_terminal(
            since,
            expected_release_current=1,
            timeout_code="replay_terminal_timeout",
        )
        terminal_metrics = _metrics(summary)
        if (
            terminal_metrics["staleRejected"] != first_metrics["staleRejected"]
            or terminal_metrics["staleReplayRejected"] != first_metrics["staleReplayRejected"]
            or terminal_metrics["replayCandidate"] != 0
        ):
            raise FocusedActiveDocumentGateError("replay_second_signal_observed")
        verification = self._verify(
            spec,
            summary,
            baseline,
            expected_document_sequence=document,
            error_code="replay_verification_failed",
        )
        return {
            "caseId": spec.case_id,
            "pass": True,
            "firstOutcome": "rejected",
            "secondOutcome": "absent",
            "postNavigationOutcome": post_navigation,
            "oneShot": True,
            "verification": verification,
        }

    def _probe_post_navigation_replay_absent(
        self,
        spec: CaseSpec,
        run_nonce: str,
    ) -> str:
        """Prove navigation clears the prior consumed PRESENT candidate.

        The next document is held at HELLO, before it can consume a new
        PRESENT.  Therefore an ``absent`` outcome can only describe the old
        candidate being cleared by the generation change.
        """

        since = self._device.mark_logs()
        current, _, _ = self._current_identity(since)
        if _metrics(current)["replayCandidate"] != 1:
            raise FocusedActiveDocumentGateError("post_navigation_replay_candidate_unavailable")
        held_spec = CaseSpec(spec.case_id, spec.action, "hello_accepted", spec.runner_method)
        arm, _, cancel, hold_digest = self._arm_hold(held_spec)
        armed = False
        cancelled = False
        try:
            if "result=active_document_hold_armed" not in self._device.send_hold(arm):
                raise FocusedActiveDocumentGateError("post_navigation_hold_arm_rejected")
            armed = True
            self._device.navigate_controlled(self._target(run_nonce))
            held, _, _, _, _ = self._wait_for_hold(since, held_spec, hold_digest)
            if _metrics(held)["replayCandidate"] != 0:
                raise FocusedActiveDocumentGateError("navigation_did_not_clear_replay_candidate")
            replay = dict(self._device.perform_action("replay_consumed_present"))
            if replay.get("outcome") != "absent":
                raise FocusedActiveDocumentGateError("post_navigation_replay_not_absent")
            if "result=active_document_hold_cancelled" not in self._device.send_hold(cancel):
                raise FocusedActiveDocumentGateError("post_navigation_hold_cancel_rejected")
            cancelled = True
            self._wait_for_stable_terminal(
                since,
                expected_release_current=0,
                timeout_code="post_navigation_hold_terminal_timeout",
            )
        finally:
            if armed and not cancelled:
                self._device.send_hold(cancel)

        restore_spec = CaseSpec(COLD_CASE, "controlled_navigation", "hello_accepted", "_run_cold")
        self._label_case(restore_spec)
        self._device.navigate_controlled(self._target(run_nonce))
        restored = self._wait_for_case_release_count(
            since,
            COLD_CASE,
            1,
            "post_navigation_restore_release_timeout",
        )
        restored_document, _ = self._release_event_identity(restored, COLD_CASE)
        restored = self._summary_with_current_release(
            self._wait_for_stable_terminal(
                since,
                expected_release_current=1,
                timeout_code="post_navigation_restore_terminal_timeout",
            )
        )
        if int(restored["currentBinding"]["documentSequence"]) != restored_document:
            raise FocusedActiveDocumentGateError("post_navigation_restore_identity_mismatch")
        self._remember_current_release(restored)
        return "absent"

    def _run_root_replacement(self, spec: CaseSpec, run_nonce: str) -> dict[str, Any]:
        return self._run_restart_case(spec, run_nonce, require_binding_change=True)

    def _run_restart_case(
        self,
        spec: CaseSpec,
        run_nonce: str,
        *,
        require_binding_change: bool,
    ) -> dict[str, Any]:
        since = self._device.mark_logs()
        _, baseline = self._baseline(since)
        _, old_document, old_binding = self._current_identity(since)
        self._label_case(spec)
        action = dict(self._device.perform_action("restart_chrome", self._target(run_nonce)))
        observed = self._wait_for_case_release_count(
            since,
            spec.case_id,
            1,
            "restart_release_timeout",
        )
        document, new_binding = self._release_event_identity(observed, spec.case_id)
        if document == old_document:
            raise FocusedActiveDocumentGateError("restart_document_identity_reused")
        if require_binding_change and new_binding == old_binding:
            raise FocusedActiveDocumentGateError("root_window_replacement_not_observed")
        if require_binding_change and not any(
            event.get("phase") in {"active_document_invalidated", "active_document_revoked"}
            and int(event.get("documentSequence", 0)) == old_document
            for event in _case_events(observed, spec.case_id)
        ):
            raise FocusedActiveDocumentGateError("root_window_invalidation_missing")
        summary = self._wait_for_stable_terminal(
            since,
            expected_release_current=1,
            timeout_code="restart_terminal_timeout",
        )
        verification = self._verify(
            spec,
            summary,
            baseline,
            expected_document_sequence=document,
            forbidden_document_sequences=(old_document,),
            error_code="restart_verification_failed",
        )
        return {
            "caseId": spec.case_id,
            "pass": True,
            "action": action,
            "bindingChanged": new_binding != old_binding,
            "verification": verification,
        }
