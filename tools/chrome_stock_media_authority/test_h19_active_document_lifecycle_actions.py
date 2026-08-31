import unittest

from h19_active_document_physical_cases import _assert_bfcache_restore
from run_active_document_a23_gate import (
    ActiveDocumentA23GateRunner,
    CASE_SPECS,
    FocusedActiveDocumentGateError,
    RunnerConfig,
)
from test_h19_active_document_runner import (
    HOLD_NONCE,
    RUN_NONCE,
    SESSION,
    TOKEN_A,
    TOKEN_B,
    FakeActiveDocumentDevice,
    FakeClock,
    event,
)


class ActiveDocumentLifecycleActionsTest(unittest.TestCase):
    def test_bfcache_restore_requires_same_capability_and_new_lifecycle(self):
        original = {
            "policyEpoch": 9,
            "documentSequence": 10,
            "sessionDigestPrefix": SESSION[:12],
            "tokenDigestPrefix": TOKEN_A[:12],
            "lifecycle": 1,
        }
        restored = {**original, "lifecycle": 2}

        _assert_bfcache_restore(
            original,
            restored,
            failure_code="history_back_did_not_restore_a",
        )

        with self.assertRaisesRegex(
            FocusedActiveDocumentGateError,
            "history_back_did_not_restore_a",
        ):
            _assert_bfcache_restore(
                original,
                {**restored, "tokenDigestPrefix": TOKEN_B[:12]},
                failure_code="history_back_did_not_restore_a",
            )
        with self.assertRaisesRegex(
            FocusedActiveDocumentGateError,
            "bfcache_lifecycle_not_advanced",
        ):
            _assert_bfcache_restore(
                original,
                original,
                failure_code="history_back_did_not_restore_a",
            )

    def test_rotation_returns_to_original_orientation_and_document_binding(self):
        class RotationDevice(FakeActiveDocumentDevice):
            def __init__(self):
                super().__init__()
                self.rotation = 0

            def supported_case_actions(self):
                return frozenset({"controlled_navigation", "portrait_landscape"})

            def perform_action(self, action, target=""):
                if action == "toggle_orientation":
                    self.actions.append(action)
                    before = self.rotation
                    self.rotation = 1
                    return {"action": action, "before": before, "observedRotation": self.rotation}
                if action == "restore_orientation":
                    self.actions.append(action)
                    self.rotation = 0
                    return {
                        "action": action,
                        "restored": True,
                        "expectedRotation": 0,
                        "observedRotation": self.rotation,
                    }
                if action == "observe_orientation":
                    self.actions.append(action)
                    return {"action": action, "observedRotation": self.rotation}
                return super().perform_action(action, target)

        device = RotationDevice()
        evidence = self._run_selected(
            device,
            "cold_foreground_release",
            "portrait_landscape",
        )

        rotation = evidence["cases"][1]
        self.assertEqual(
            {"before": 0, "after": 1, "restored": 0, "settingsRestored": True},
            rotation["orientation"],
        )
        self.assertTrue(rotation["sameDocument"])
        self.assertTrue(rotation["sameRootWindow"])
        self.assertLess(
            device.actions.index("toggle_orientation"),
            device.actions.index("restore_orientation"),
        )
        self.assertIn("observe_orientation", device.actions)

    def test_background_foreground_requires_same_document_and_root_window(self):
        passed = self._run_selected(
            self.BackgroundDevice(),
            "cold_foreground_release",
            "app_background_foreground",
        )
        self.assertTrue(passed["cases"][1]["sameDocumentRestored"])
        self.assertTrue(passed["cases"][1]["sameRootWindowRestored"])

        with self.assertRaisesRegex(
            FocusedActiveDocumentGateError,
            "foreground_document_identity_changed",
        ):
            self._run_selected(
                self.BackgroundDevice(foreground_document=11),
                "cold_foreground_release",
                "app_background_foreground",
            )

    @staticmethod
    def _run_selected(device, *case_ids):
        clock = FakeClock()
        selected = tuple(spec for spec in CASE_SPECS if spec.case_id in case_ids)
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
            case_specs=selected,
        ).run(RUN_NONCE)

    class BackgroundDevice(FakeActiveDocumentDevice):
        def __init__(self, *, foreground_document=10):
            super().__init__()
            self.foreground_document = foreground_document

        def supported_case_actions(self):
            return frozenset({"controlled_navigation", "app_background_foreground"})

        def perform_action(self, action, target=""):
            self.actions.append(action)
            if action == "background":
                self.sequence += 1
                self.lines.append(
                    event(
                        "active_document_invalidated",
                        self.current_case,
                        self.sequence,
                        10,
                        reason="invalidated_hidden",
                    )
                )
                self.metrics["staleRejected"] += 1
                self.metrics["releaseCurrent"] = 0
                return {"action": action}
            if action == "foreground":
                for metric, phase in (
                    ("activeHello", "active_hello_accepted"),
                    ("challengeIssued", "challenge_issued"),
                    ("proofAccepted", "proof_accepted"),
                    ("presentAccepted", "present_accepted"),
                ):
                    self.metrics[metric] += 1
                    self.sequence += 1
                    self.lines.append(
                        event(
                            phase,
                            self.current_case,
                            self.sequence,
                            self.foreground_document,
                            lifecycle=2,
                        )
                    )
                self.sequence += 1
                self.lines.append(
                    event(
                        "active_document_released",
                        self.current_case,
                        self.sequence,
                        self.foreground_document,
                        lifecycle=2,
                    )
                )
                self.metrics["releaseCurrent"] = 1
                self.metrics["replayCandidate"] = 1
                return {"action": action}
            return {"action": action}


if __name__ == "__main__":
    unittest.main()
