import hashlib
import json
import tempfile
import unittest
from pathlib import Path

from run_active_document_a23_gate import (
    AdbActiveDocumentDevice,
    ActiveDocumentA23GateRunner,
    CASE_SPECS,
    COLD_CASE,
    RACE_CASE,
    FocusedActiveDocumentGateError,
    RunnerConfig,
    assert_aggregate_evidence,
    top_resumed_package,
    write_aggregate_evidence,
)
from h19_active_document_gates import CASE_IDS
from h19_active_document_video_evidence import bounded_visual_summary


SESSION = "1" * 64
TOKEN_A = "2" * 64
TOKEN_B = "3" * 64
TOKEN_C = "7" * 64
CHALLENGE_A = "4" * 64
CHALLENGE_B = "5" * 64
ROOT = "6" * 64
ROOT_B = "a" * 64
RUN_NONCE = "8" * 32
HOLD_NONCE = "9" * 32
HOLD_DIGEST = hashlib.sha256(HOLD_NONCE.encode()).hexdigest()


def binding(window_id=22, root=ROOT):
    return (window_id, root)


def status(metrics, foreground, attempt, pending_handshake, hold_phase):
    def fields(prefix, value):
        if value is None:
            return f"{prefix}WindowId=-1 {prefix}RootDigest={'0' * 64}"
        window_id, root = value
        return f"{prefix}WindowId={window_id} {prefix}RootDigest={root}"

    return (
        "I/ChromeMediaShieldActiveDocument: "
        "protocol=active_document_v3 phase=active_document_status "
        + " ".join(f"{name}={value}" for name, value in metrics.items())
        + " "
        + fields("foreground", foreground)
        + " "
        + fields("attempt", attempt)
        + f" pendingHandshake={pending_handshake} holdPhase={hold_phase}"
    )


def event(
    phase,
    case_id,
    sequence,
    document_sequence,
    *,
    token=TOKEN_A,
    challenge=CHALLENGE_A,
    reason="",
    lifecycle=1,
):
    reason_field = f" reason={reason}" if reason else ""
    return (
        "I/ChromeMediaShieldActiveDocument: "
        f"protocol=active_document_v3 phase={phase} caseId={case_id} "
        f"eventSequence={sequence}{reason_field} policyEpoch=9 navigationSequence={document_sequence} "
        f"documentSequence={document_sequence} lifecycle={lifecycle} windowId=22 surfaceEpoch=31 "
        f"sessionDigest={SESSION} tokenDigest={token} challengeDigest={challenge} "
        f"rootDigest={ROOT} current=true rawPresented=false"
    )


def hold_event(phase, sequence, case_id="switch_during_prove_present", stage="present_postcommit"):
    return (
        "I/ChromeMediaShieldActiveDocument: "
        f"protocol=active_document_v3 phase={phase} caseId={case_id} "
        f"eventSequence={sequence} holdStage={stage} holdDigest={HOLD_DIGEST}"
    )


class FakeClock:
    def __init__(self):
        self.value = 0.0

    def monotonic(self):
        return self.value

    def sleep(self, seconds):
        self.value += seconds


class FakeActiveDocumentDevice:
    def __init__(
        self,
        *,
        hold_reaches=True,
        cross_tab_release=0,
        switch_changes_binding=False,
        emit_b_hello=True,
        release_reason="invalidated_navigation",
        release_disposition="released",
        causal_event_after_snapshot=True,
        terminal_alpha_samples=(),
        restore_changes_binding=True,
    ):
        self.hold_reaches = hold_reaches
        self.cross_tab_release = cross_tab_release
        self.switch_changes_binding = switch_changes_binding
        self.emit_b_hello = emit_b_hello
        self.release_reason = release_reason
        self.release_disposition = release_disposition
        self.causal_event_after_snapshot = causal_event_after_snapshot
        self.terminal_alpha_samples = list(terminal_alpha_samples)
        self.restore_changes_binding = restore_changes_binding
        self.lines = []
        self.actions = []
        self.sequence = 0
        self.navigation_count = 0
        self.current_case = "cold_foreground_release"
        self.current_stage = "present_postcommit"
        self.race_terminal = False
        self.race_a_binding = binding()
        self.race_a_document = 20
        self.race_a_lifecycle = 1
        self.race_b_document = 30
        self.foreground_binding = binding()
        self.attempt_binding = None
        self.pending_handshake = 0
        self.hold_phase = "idle"
        self.metrics = {
            "activeHello": 0,
            "challengeIssued": 0,
            "proofAccepted": 0,
            "presentAccepted": 0,
            "backgroundRejected": 0,
            "staleRejected": 0,
            "staleReplayRejected": 0,
            "replayCandidate": 0,
            "crossTabRelease": 0,
            "releaseCurrent": 0,
            "rejectedTransparentCommits": 0,
            "opaqueRestoreFailures": 0,
            "alphaSubmitFailures": 0,
            "alphaTransitionsOutstanding": 0,
        }

    def assert_chrome_top_resumed(self):
        self.actions.append("assert_chrome_top_resumed")

    def device_summary(self):
        return {"serialSha256": "b" * 64, "model": "SM-A235M", "sdk": 34}

    def mark_logs(self):
        self.lines = []
        return f"marker-{self.navigation_count}"

    def request_status(self):
        self.actions.append("status")
        if self.race_terminal and self.terminal_alpha_samples:
            self.metrics["alphaTransitionsOutstanding"] = self.terminal_alpha_samples.pop(0)
        self.lines.append(
            status(
                self.metrics,
                self.foreground_binding,
                self.attempt_binding,
                self.pending_handshake,
                self.hold_phase,
            )
        )

    def read_typed_logs(self, since):
        return "\n".join(self.lines)

    def navigate_controlled(self, target):
        self.navigation_count += 1
        self.actions.append("navigate")
        if self.navigation_count == 1 or self.hold_phase == "idle":
            self._complete_cold()
        else:
            self._reach_race_hold()
        return {"targetSha256": "c" * 64}

    def open_switch_tab(self, target):
        self.actions.append("open_tab_b")
        if self.switch_changes_binding:
            self.foreground_binding = binding(root=ROOT_B)
        if self.emit_b_hello:
            if self.causal_event_after_snapshot:
                self.sequence += 1
                self.lines.append(
                    event(
                        "active_document_invalidated",
                        self.current_case,
                        self.sequence,
                        self.race_a_document,
                        token=TOKEN_B,
                        challenge=CHALLENGE_B,
                        reason=self.release_reason,
                        lifecycle=self.race_a_lifecycle,
                    )
                )
                self.metrics["staleRejected"] += 1
            self.metrics["activeHello"] += 1
            self.sequence += 1
            self.lines.append(
                event(
                    "active_hello_accepted",
                    self.current_case,
                    self.sequence,
                    self.race_b_document,
                    token=TOKEN_C,
                    challenge="",
                )
            )
            self.metrics["challengeIssued"] += 1
            self.sequence += 1
            self.lines.append(
                event(
                    "challenge_issued",
                    self.current_case,
                    self.sequence,
                    self.race_b_document,
                    token=TOKEN_C,
                )
            )
            self.metrics["proofAccepted"] += 1
            self.sequence += 1
            self.lines.append(
                event(
                    "proof_accepted",
                    self.current_case,
                    self.sequence,
                    self.race_b_document,
                    token=TOKEN_C,
                )
            )
            self.pending_handshake = 1
            self.attempt_binding = self.foreground_binding
            self.hold_phase = "reached"
            self._append_hold("active_document_hold_reached")
        return {
            "mechanism": "android.provider.Browser.EXTRA_CREATE_NEW_TAB",
            "targetSha256": "d" * 64,
            "intentResultSha256": "e" * 64,
        }

    def send_hold(self, command):
        operation = command.action.rsplit("_", 1)[-1].lower()
        self.actions.append(operation)
        if operation == "arm":
            extras = {name: value for _, name, value in command.extras}
            self.current_case = extras["active_document_case_id"]
            self.current_stage = extras["active_document_hold_stage"]
            self.hold_phase = "armed"
            self._append_hold("active_document_hold_armed")
            return 'Broadcast completed: data="result=active_document_hold_armed"'
        if operation == "release":
            pre_release_sequence = self.sequence
            self._append_hold("active_document_hold_released")
            if self.causal_event_after_snapshot:
                self.sequence += 1
                invalidation_sequence = self.sequence
            else:
                invalidation_sequence = pre_release_sequence
            self.lines.append(
                event(
                    "active_document_invalidated",
                    self.current_case,
                    invalidation_sequence,
                    20,
                    token=TOKEN_B,
                    challenge=CHALLENGE_B,
                    reason=self.release_reason,
                )
            )
            self.metrics["staleRejected"] += 1
            self.metrics["crossTabRelease"] = self.cross_tab_release
            self.metrics["releaseCurrent"] = 0
            self.pending_handshake = 0
            self.hold_phase = "idle"
            self.attempt_binding = None
            self.race_terminal = True
            return (
                'Broadcast completed: data="result=active_document_hold_released"'
                if self.release_disposition == "released"
                else 'Broadcast completed: data="result=active_document_hold_rejected"'
            )
        if operation == "cancel" and self.hold_phase == "reached":
            self._append_hold("active_document_hold_cancelled")
            self.sequence += 1
            self.lines.append(
                event(
                    "active_document_invalidated",
                    self.current_case,
                    self.sequence,
                    self.race_b_document,
                    token=TOKEN_C,
                    reason="hold_cancelled",
                )
            )
            self.metrics["staleRejected"] += 1
            self.metrics["crossTabRelease"] = self.cross_tab_release
            self.metrics["releaseCurrent"] = 0
            self.pending_handshake = 0
            self.hold_phase = "idle"
            self.attempt_binding = None
            self.race_terminal = True
            return 'Broadcast completed: data="result=active_document_hold_cancelled"'
        self.pending_handshake = 0
        self.hold_phase = "idle"
        self._append_hold("active_document_hold_cancelled")
        return 'Broadcast completed: data="result=active_document_hold_cancelled"'

    def perform_action(self, action, target=""):
        self.actions.append(action)
        if action != "replay_consumed_present":
            return {"action": action}
        if self.metrics["replayCandidate"] == 1:
            self.metrics["replayCandidate"] = 0
            self.metrics["staleRejected"] += 1
            self.metrics["staleReplayRejected"] += 1
            self.sequence += 1
            self.lines.append(
                event(
                    "present_rejected",
                    self.current_case,
                    self.sequence,
                    10,
                    reason="present_replay",
                )
            )
            return {"action": action, "outcome": "rejected"}
        return {"action": action, "outcome": "absent"}

    def restore_previous_tab(self):
        self.actions.append("restore_tab_a")
        if self.restore_changes_binding:
            self.foreground_binding = self.race_a_binding
            self.attempt_binding = self.race_a_binding
            self.race_a_lifecycle += 1
            self.metrics["activeHello"] += 1
            self.sequence += 1
            self.lines.append(
                event(
                    "active_hello_accepted",
                    self.current_case,
                    self.sequence,
                    self.race_a_document,
                    token=TOKEN_B,
                    challenge="",
                    lifecycle=self.race_a_lifecycle,
                )
            )
            self.metrics["challengeIssued"] += 1
            self.sequence += 1
            self.lines.append(
                event(
                    "challenge_issued",
                    self.current_case,
                    self.sequence,
                    self.race_a_document,
                    token=TOKEN_B,
                    challenge=CHALLENGE_B,
                    lifecycle=self.race_a_lifecycle,
                )
            )
            self.metrics["proofAccepted"] += 1
            self.sequence += 1
            self.lines.append(
                event(
                    "proof_accepted",
                    self.current_case,
                    self.sequence,
                    self.race_a_document,
                    token=TOKEN_B,
                    challenge=CHALLENGE_B,
                    lifecycle=self.race_a_lifecycle,
                )
            )
            self.metrics["presentAccepted"] += 1
            self.sequence += 1
            self.lines.append(
                event(
                    "present_accepted",
                    self.current_case,
                    self.sequence,
                    self.race_a_document,
                    token=TOKEN_B,
                    challenge=CHALLENGE_B,
                    lifecycle=self.race_a_lifecycle,
                )
            )
            self.sequence += 1
            self.lines.append(
                event(
                    "active_document_released",
                    self.current_case,
                    self.sequence,
                    self.race_a_document,
                    token=TOKEN_B,
                    challenge=CHALLENGE_B,
                    lifecycle=self.race_a_lifecycle,
                )
            )
            self.metrics["releaseCurrent"] = 1
            self.pending_handshake = 0
            self.hold_phase = "idle"
        return True

    def supported_case_actions(self):
        return frozenset({"controlled_navigation", "switch_during_hold"})

    def _append_hold(self, phase):
        self.sequence += 1
        self.lines.append(
            hold_event(
                phase,
                self.sequence,
                case_id=self.current_case,
                stage=self.current_stage,
            )
        )

    def _complete_cold(self):
        case = "cold_foreground_release"
        self.metrics["activeHello"] += 1
        self.sequence += 1
        self.lines.append(event("active_hello_accepted", case, self.sequence, 10, challenge=""))
        self.metrics["challengeIssued"] += 1
        self.sequence += 1
        self.lines.append(event("challenge_issued", case, self.sequence, 10))
        self.metrics["proofAccepted"] += 1
        self.sequence += 1
        self.lines.append(event("proof_accepted", case, self.sequence, 10))
        self.metrics["presentAccepted"] += 1
        self.sequence += 1
        self.lines.append(event("present_accepted", case, self.sequence, 10))
        self.sequence += 1
        self.lines.append(event("active_document_released", case, self.sequence, 10))
        self.metrics["releaseCurrent"] = 1
        self.metrics["replayCandidate"] = 1
        self.foreground_binding = binding()
        self.attempt_binding = self.foreground_binding
        self.pending_handshake = 0
        self.hold_phase = "idle"

    def _reach_race_hold(self):
        case = self.current_case
        self.metrics["releaseCurrent"] = 0
        self.metrics["replayCandidate"] = 0
        self.race_a_binding = binding()
        self.foreground_binding = self.race_a_binding
        self.attempt_binding = self.race_a_binding
        self.pending_handshake = 1
        self.metrics["activeHello"] += 1
        self.sequence += 1
        self.lines.append(
            event(
                "active_hello_accepted",
                case,
                self.sequence,
                20,
                token=TOKEN_B,
                challenge="",
            )
        )
        self.metrics["challengeIssued"] += 1
        self.sequence += 1
        self.lines.append(
            event(
                "challenge_issued",
                case,
                self.sequence,
                20,
                token=TOKEN_B,
                challenge=CHALLENGE_B,
            )
        )
        self.metrics["proofAccepted"] += 1
        self.sequence += 1
        self.lines.append(
            event(
                "proof_accepted",
                case,
                self.sequence,
                20,
                token=TOKEN_B,
                challenge=CHALLENGE_B,
            )
        )
        if self.hold_reaches:
            self.hold_phase = "reached"
            self._append_hold("active_document_hold_reached")


class ActiveDocumentRunnerTest(unittest.TestCase):
    def test_top_resumed_parser_is_exact_and_fail_closed(self):
        chrome = "  topResumedActivity=ActivityRecord{abc u0 com.android.chrome/com.google.Chrome t1}"
        settings = "  topResumedActivity=ActivityRecord{abc u0 com.android.settings/.Settings t1}"
        self.assertEqual("com.android.chrome", top_resumed_package(chrome))
        self.assertEqual("com.android.settings", top_resumed_package(settings))
        self.assertIsNone(top_resumed_package(""))
        self.assertIsNone(top_resumed_package(chrome + "\n" + chrome))

    def runner(self, device, clock=None):
        clock = clock or FakeClock()
        return ActiveDocumentA23GateRunner(
            device,
            config=RunnerConfig(
                case_timeout_seconds=1.0,
                hold_timeout_seconds=0.5,
                poll_interval_seconds=0.1,
            ),
            monotonic=clock.monotonic,
            sleep=clock.sleep,
            nonce_factory=lambda: HOLD_NONCE,
            case_specs=tuple(
                spec for spec in CASE_SPECS if spec.case_id in (COLD_CASE, RACE_CASE)
            ),
        )

    def test_case_spec_table_is_exactly_the_sixteen_reviewed_cases(self):
        self.assertEqual(CASE_IDS, tuple(spec.case_id for spec in CASE_SPECS))
        self.assertEqual(16, len(CASE_SPECS))
        self.assertEqual(16, len({spec.case_id for spec in CASE_SPECS}))
        self.assertEqual("hello_accepted", CASE_SPECS[3].hold_stage)
        self.assertEqual("challenge_issued", CASE_SPECS[4].hold_stage)
        self.assertEqual("present_postcommit", CASE_SPECS[5].hold_stage)

    def test_every_reviewed_case_has_a_concrete_runner_and_real_adapter_action(self):
        adapter = AdbActiveDocumentDevice(None, "test-serial")
        supported = adapter.supported_case_actions()

        for spec in CASE_SPECS:
            with self.subTest(case=spec.case_id):
                self.assertIn(spec.action, supported)
                self.assertIsNotNone(spec.runner_method)
                self.assertTrue(callable(getattr(ActiveDocumentA23GateRunner, spec.runner_method)))

    def test_chrome_window_focus_without_shown_ime_is_not_input_evidence(self):
        class DumpsysAdb:
            def __init__(self, output):
                self.output = output

            def shell(self, *args, **kwargs):
                return self.output

        focused_only = AdbActiveDocumentDevice(
            DumpsysAdb(
                "mCurFocusedWindow=com.android.chrome mInputShown=false "
                "mCurAttribute=EditorInfo{ packageName=com.android.chrome inputType=0x11 }"
            ),
            "test-serial",
        )
        shown_editor = AdbActiveDocumentDevice(
            DumpsysAdb(
                "mInputShown=true "
                "mCurAttribute=EditorInfo{ packageName=com.android.chrome inputType=0x11 }"
            ),
            "test-serial",
        )

        self.assertFalse(focused_only._chrome_input_observation()["observed"])
        observed = shown_editor._chrome_input_observation()
        self.assertTrue(observed["observed"])
        self.assertEqual("native_uri", observed["editorKind"])

    def test_chrome_form_editor_is_structurally_distinct_from_omnibox(self):
        class DumpsysAdb:
            def shell(self, *args, **kwargs):
                return (
                    "mInputShown=true "
                    "mCurrentTextBoxAttribute=EditorInfo{ "
                    "packageName=com.android.chrome inputType=0xa1 }"
                )

        observed = AdbActiveDocumentDevice(
            DumpsysAdb(),
            "test-serial",
        )._chrome_input_observation()

        self.assertTrue(observed["observed"])
        self.assertEqual("web_form", observed["editorKind"])

    def test_replay_is_one_shot_rejected_and_never_creates_release(self):
        class ReplayDevice(FakeActiveDocumentDevice):
            def supported_case_actions(self):
                return frozenset({"controlled_navigation", "stale_replay"})

        device = ReplayDevice()
        clock = FakeClock()
        selected = tuple(
            spec
            for spec in CASE_SPECS
            if spec.case_id in ("cold_foreground_release", "stale_replay_token_reuse")
        )
        runner = ActiveDocumentA23GateRunner(
            device,
            config=RunnerConfig(
                case_timeout_seconds=1.0,
                hold_timeout_seconds=0.5,
                poll_interval_seconds=0.1,
            ),
            monotonic=clock.monotonic,
            sleep=clock.sleep,
            nonce_factory=lambda: HOLD_NONCE,
            case_specs=selected,
        )

        evidence = runner.run(RUN_NONCE)

        replay = evidence["cases"][1]
        self.assertEqual("rejected", replay["firstOutcome"])
        self.assertEqual("absent", replay["secondOutcome"])
        self.assertEqual("absent", replay["postNavigationOutcome"])
        self.assertEqual(0, replay["verification"]["releaseCount"])
        self.assertEqual(1, replay["verification"]["terminal"]["releaseCurrent"])
        self.assertEqual(0, replay["verification"]["terminal"]["replayCandidate"])
        self.assertEqual(3, device.actions.count("replay_consumed_present"))

    def test_full_gate_blocks_in_preflight_before_any_device_mutation(self):
        device = FakeActiveDocumentDevice()
        clock = FakeClock()
        runner = ActiveDocumentA23GateRunner(
            device,
            config=RunnerConfig(
                case_timeout_seconds=1.0,
                hold_timeout_seconds=0.5,
                poll_interval_seconds=0.1,
            ),
            monotonic=clock.monotonic,
            sleep=clock.sleep,
            nonce_factory=lambda: HOLD_NONCE,
        )

        with self.assertRaisesRegex(
            FocusedActiveDocumentGateError,
            "physical_case_action_unavailable",
        ):
            runner.run(RUN_NONCE)

        self.assertEqual([], device.actions)
        self.assertEqual(0, device.navigation_count)

    def test_cold_and_tab_switch_race_pass_with_exact_action_order(self):
        device = FakeActiveDocumentDevice()

        evidence = self.runner(device).run(RUN_NONCE)

        self.assertEqual("PASS", evidence["status"])
        self.assertEqual(0, evidence["crossTabRelease"])
        self.assertEqual(
            ["cold_foreground_release", "switch_during_prove_present"],
            [case["caseId"] for case in evidence["cases"]],
        )
        arm = device.actions.index("arm")
        race_navigation = device.actions.index("navigate", arm)
        switch = device.actions.index("open_tab_b", race_navigation)
        cancel = device.actions.index("cancel", switch)
        self.assertLess(arm, race_navigation)
        self.assertLess(race_navigation, switch)
        self.assertLess(switch, cancel)
        race = evidence["cases"][1]
        self.assertTrue(race["hold"]["reached"])
        self.assertTrue(race["hold"]["cancelCommandSentAfterSwitch"])
        self.assertGreater(
            race["hold"]["causalInvalidationEventSequence"],
            race["hold"]["preSwitchEventSequence"],
        )
        self.assertEqual("invalidated_navigation", race["hold"]["causalInvalidationReason"])
        self.assertTrue(race["tabSwitch"]["claimSupersessionObserved"])
        self.assertFalse(race["tabSwitch"]["nativeBindingChanged"])
        self.assertTrue(race["tabSwitch"]["restoreVerified"])
        self.assertEqual(0, race["verification"]["releaseCount"])
        self.assertEqual(0, race["verification"]["crossTabRelease"])

    def test_cross_tab_release_fails_closed(self):
        device = FakeActiveDocumentDevice(cross_tab_release=1)

        with self.assertRaisesRegex(FocusedActiveDocumentGateError, "race_verification_failed"):
            self.runner(device).run(RUN_NONCE)

        self.assertIn("restore_tab_a", device.actions)

    def test_hold_timeout_cannot_masquerade_as_structural_tab_invalidation(self):
        for reason in ("hold_cancelled", "handshake_transport_cancelled", "invalidated_health"):
            device = FakeActiveDocumentDevice(
                release_reason=reason,
                release_disposition="rejected",
            )

            with self.subTest(reason=reason):
                with self.assertRaisesRegex(
                    FocusedActiveDocumentGateError,
                    "race_claim_supersession_not_observed|race_non_causal_invalidation",
                ):
                    self.runner(device).run(RUN_NONCE)

                self.assertIn("open_tab_b", device.actions)
                self.assertIn("restore_tab_a", device.actions)

    def test_hold_requires_foreground_attempt_and_proof_to_share_a_binding(self):
        class MismatchedAttemptDevice(FakeActiveDocumentDevice):
            def _reach_race_hold(self):
                super()._reach_race_hold()
                self.attempt_binding = binding(root=ROOT_B)

        device = MismatchedAttemptDevice()

        with self.assertRaisesRegex(
            FocusedActiveDocumentGateError,
            "held_structural_binding_mismatch",
        ):
            self.runner(device).run(RUN_NONCE)

        self.assertNotIn("open_tab_b", device.actions)

    def test_switch_requires_a_distinct_cryptographic_b_claim_not_a_root_change(self):
        device = FakeActiveDocumentDevice(emit_b_hello=False)

        with self.assertRaisesRegex(
            FocusedActiveDocumentGateError,
            "race_claim_supersession_not_observed",
        ):
            self.runner(device).run(RUN_NONCE)

        self.assertNotIn("release", device.actions)
        self.assertIn("cancel", device.actions)

    def test_early_switch_failure_restores_tab_without_masking_causal_error(self):
        device = FakeActiveDocumentDevice(emit_b_hello=False)
        clock = FakeClock()
        selected = tuple(
            spec
            for spec in CASE_SPECS
            if spec.case_id in ("cold_foreground_release", "switch_during_hello")
        )
        runner = ActiveDocumentA23GateRunner(
            device,
            config=RunnerConfig(
                case_timeout_seconds=1.0,
                hold_timeout_seconds=0.5,
                poll_interval_seconds=0.1,
            ),
            monotonic=clock.monotonic,
            sleep=clock.sleep,
            nonce_factory=lambda: HOLD_NONCE,
            case_specs=selected,
        )

        with self.assertRaisesRegex(
            FocusedActiveDocumentGateError,
            "foreground_claim_supersession_not_observed",
        ):
            runner.run(RUN_NONCE)

        self.assertEqual(1, device.actions.count("restore_tab_a"))

    def test_rapid_switch_failure_restores_every_opened_tab(self):
        class RapidFailureDevice(FakeActiveDocumentDevice):
            def supported_case_actions(self):
                return frozenset({"controlled_navigation", "rapid_tab_switching"})

        device = RapidFailureDevice(emit_b_hello=False)
        clock = FakeClock()
        selected = tuple(
            spec
            for spec in CASE_SPECS
            if spec.case_id in ("cold_foreground_release", "rapid_tab_switching")
        )
        runner = ActiveDocumentA23GateRunner(
            device,
            config=RunnerConfig(
                case_timeout_seconds=1.0,
                hold_timeout_seconds=0.5,
                poll_interval_seconds=0.1,
            ),
            monotonic=clock.monotonic,
            sleep=clock.sleep,
            nonce_factory=lambda: HOLD_NONCE,
            case_specs=selected,
        )

        with self.assertRaisesRegex(
            FocusedActiveDocumentGateError,
            "rapid_claim_supersession_not_observed",
        ):
            runner.run(RUN_NONCE)

        self.assertEqual(1, device.actions.count("restore_tab_a"))

    def test_causal_invalidation_must_be_newer_than_pre_switch_snapshot(self):
        device = FakeActiveDocumentDevice(causal_event_after_snapshot=False)

        with self.assertRaisesRegex(
            FocusedActiveDocumentGateError,
            "race_claim_supersession_not_observed",
        ):
            self.runner(device).run(RUN_NONCE)

    def test_terminal_poll_waits_for_zero_alpha_and_two_stable_snapshots(self):
        device = FakeActiveDocumentDevice(terminal_alpha_samples=(1, 0, 0))

        evidence = self.runner(device).run(RUN_NONCE)

        self.assertEqual("PASS", evidence["status"])
        cancel_index = device.actions.index("cancel")
        restore_index = device.actions.index("restore_tab_a", cancel_index)
        self.assertGreaterEqual(device.actions[cancel_index:restore_index].count("status"), 3)
        self.assertEqual(
            {
                "releaseCurrent": 0,
                "alphaTransitionsOutstanding": 0,
                "pendingHandshake": 0,
                "holdPhase": "idle",
                "replayCandidate": 0,
            },
            evidence["cases"][1]["verification"]["terminal"],
        )

    def test_terminal_alpha_that_never_closes_times_out(self):
        device = FakeActiveDocumentDevice(terminal_alpha_samples=(1,))

        with self.assertRaisesRegex(
            FocusedActiveDocumentGateError,
            "race_terminal_state_timeout",
        ):
            self.runner(device).run(RUN_NONCE)

    def test_restore_requires_observed_original_structural_binding(self):
        device = FakeActiveDocumentDevice(restore_changes_binding=False)

        with self.assertRaisesRegex(
            FocusedActiveDocumentGateError,
            "foreground_restore_claim_not_observed",
        ):
            self.runner(device).run(RUN_NONCE)

    def test_hold_timeout_cancels_without_switch_or_release(self):
        device = FakeActiveDocumentDevice(hold_reaches=False)

        with self.assertRaisesRegex(FocusedActiveDocumentGateError, "present_postcommit_hold_timeout"):
            self.runner(device).run(RUN_NONCE)

        self.assertIn("cancel", device.actions)
        self.assertNotIn("open_tab_b", device.actions)
        self.assertNotIn("release", device.actions)

    def test_aggregate_json_excludes_url_log_and_capability(self):
        device = FakeActiveDocumentDevice()
        evidence = self.runner(device).run(RUN_NONCE)

        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "active-document.json"
            write_aggregate_evidence(output, evidence)
            raw = output.read_text(encoding="utf-8")

        self.assertNotIn(RUN_NONCE, raw)
        self.assertNotIn(HOLD_NONCE, raw)
        self.assertNotIn("https://", raw)
        self.assertNotIn("protocol=active_document_v3", raw)
        parsed = json.loads(raw)
        self.assertFalse(parsed["rawLogsPersisted"])
        self.assertFalse(parsed["rawUrlsPersisted"])
        self.assertFalse(parsed["capabilitiesPersisted"])

    def test_aggregate_guard_rejects_raw_url(self):
        with self.assertRaisesRegex(
            FocusedActiveDocumentGateError,
            "aggregate_evidence_contains_sensitive_data",
        ):
            assert_aggregate_evidence(
                {
                    "rawLogsPersisted": False,
                    "rawUrlsPersisted": False,
                    "capabilitiesPersisted": False,
                    "diagnostic": "https://private.invalid/path",
                }
            )

    def test_visual_evidence_is_bounded_and_never_claims_authority(self):
        result = bounded_visual_summary(
            {
                "frameCount": 12,
                "controlledSentinelLikeVisibleFrames": [4, 5],
                "opaqueSurfaceVisibleFrames": [0, 1],
                "auditPlaceholderVisibleFrames": [8],
                "safeFixtureVisibleFrames": [9],
                "blockedPlaceholderVisibleFrames": [10],
                "frames": [{"decodedPixels": "must-not-survive"}],
            },
            video_sha256="a" * 64,
            contact_sha256="b" * 64,
        )

        self.assertFalse(result["usedForAuthority"])
        self.assertTrue(result["observationalOnly"])
        self.assertFalse(result["rawFramesPersisted"])
        self.assertNotIn("frames", result)
        self.assertEqual([4, 5], result["controlledSentinelVisibleFrames"])


if __name__ == "__main__":
    unittest.main()
