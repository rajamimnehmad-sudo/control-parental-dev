import unittest
from unittest import mock

from h19_active_document_gates import CASE_IDS
from h19_active_document_video_evidence import (
    EXPOSURE_CONTRACTS,
    FRAME_FPS,
    ActiveDocumentCaseVideoRecorder,
    _wait_recording_ready,
    assert_visual_evidence,
    bounded_visual_summary,
)
from h19_plan import HarnessError


class FakeClock:
    def __init__(self):
        self.value = 0.0

    def monotonic(self):
        return self.value

    def sleep(self, seconds):
        self.value += seconds


class FakeProcess:
    def __init__(self):
        self.running = True
        self.signals = []

    def poll(self):
        return None if self.running else 0

    def send_signal(self, value):
        self.signals.append(value)
        self.running = False

    def wait(self, timeout):
        self.running = False
        return 0

    def terminate(self):
        self.running = False

    def kill(self):
        self.running = False


class FakeAdb:
    prefix = ("adb", "-s", "test")

    def __init__(self, *, listing=True):
        self.listing = listing
        self.shell_calls = []

    def shell(self, *args, **kwargs):
        self.shell_calls.append((args, kwargs))
        if args[:2] == ("ls", "-l") and self.listing:
            return f"-rw------- 1 shell shell 16 {args[-1]}"
        return ""

    def run(self, *args, **kwargs):
        raise AssertionError("unexpected pull in fake recorder")


def frame_summary(*, sentinel=(), safe=(), frame_count=12):
    return {
        "frameCount": frame_count,
        "controlledSentinelLikeVisibleFrames": list(sentinel),
        "opaqueSurfaceVisibleFrames": [0],
        "auditPlaceholderVisibleFrames": [],
        "safeFixtureVisibleFrames": list(safe),
        "blockedPlaceholderVisibleFrames": [],
    }


def bounded(*, case_id, sentinel=(), safe=(), windows=()):
    return bounded_visual_summary(
        frame_summary(sentinel=sentinel, safe=safe),
        video_sha256="a" * 64,
        contact_sha256="b" * 64,
        critical_windows=tuple(windows),
        critical_all_frames=(
            EXPOSURE_CONTRACTS[case_id].mode == "critical_zero_sentinel"
        ),
    )


class ActiveDocumentVideoEvidenceTest(unittest.TestCase):
    def test_every_case_has_an_explicit_exposure_contract(self):
        self.assertEqual(set(CASE_IDS), set(EXPOSURE_CONTRACTS))
        self.assertEqual("positive_control", EXPOSURE_CONTRACTS[CASE_IDS[0]].mode)
        self.assertEqual("observation_only", EXPOSURE_CONTRACTS["omnibox_focus"].mode)
        self.assertEqual(
            "observation_only",
            EXPOSURE_CONTRACTS["stale_replay_token_reuse"].mode,
        )
        self.assertEqual(
            "critical_zero_sentinel",
            EXPOSURE_CONTRACTS["switch_during_prove_present"].mode,
        )

    def test_pre_roll_requires_live_process_remote_file_and_one_frame_interval(self):
        clock = FakeClock()
        process = FakeProcess()
        adb = FakeAdb()
        remote = "/sdcard/case.mp4"

        _wait_recording_ready(
            adb,
            process,
            remote,
            monotonic=clock.monotonic,
            sleep=clock.sleep,
        )

        self.assertGreaterEqual(clock.value, 1 / FRAME_FPS)
        self.assertTrue(any(call[0][:2] == ("ls", "-l") for call in adb.shell_calls))

    def test_pre_roll_failure_still_stops_recorder_removes_remote_and_preserves_error(self):
        clock = FakeClock()
        process = FakeProcess()
        adb = FakeAdb(listing=False)
        recorder = ActiveDocumentCaseVideoRecorder(
            adb,
            monotonic=clock.monotonic,
            sleep=clock.sleep,
        )
        executed = False

        def execute():
            nonlocal executed
            executed = True
            return {"caseId": "cold_foreground_release"}

        with mock.patch(
            "h19_active_document_video_evidence.subprocess.Popen",
            return_value=process,
        ), mock.patch.object(
            recorder,
            "_analyze",
            side_effect=HarnessError("secondary-analysis-failure"),
        ) as analyze:
            with self.assertRaisesRegex(
                HarnessError,
                "active-document screenrecord pre-roll was not observed",
            ):
                recorder.run_case("cold_foreground_release", execute)

        self.assertFalse(executed)
        self.assertFalse(process.running)
        self.assertTrue(analyze.called)
        remote_removals = [
            call
            for call in adb.shell_calls
            if call[0][:2] == ("rm", "-f")
            and call[0][-1] == "/sdcard/glosh-h19-active-document-cold_foreground_release.mp4"
        ]
        self.assertEqual(2, len(remote_removals))

    def test_execute_failure_is_not_hidden_by_analysis_failure(self):
        clock = FakeClock()
        process = FakeProcess()
        adb = FakeAdb()
        recorder = ActiveDocumentCaseVideoRecorder(
            adb,
            monotonic=clock.monotonic,
            sleep=clock.sleep,
        )

        def execute():
            raise RuntimeError("typed-case-primary")

        with mock.patch(
            "h19_active_document_video_evidence.subprocess.Popen",
            return_value=process,
        ), mock.patch.object(
            recorder,
            "_analyze",
            side_effect=HarnessError("secondary-analysis-failure"),
        ):
            with self.assertRaisesRegex(RuntimeError, "typed-case-primary"):
                recorder.run_case("cold_foreground_release", execute)

        self.assertFalse(process.running)

    def test_recorder_death_before_critical_terminal_fails_closed(self):
        clock = FakeClock()
        process = FakeProcess()
        adb = FakeAdb()
        recorder = ActiveDocumentCaseVideoRecorder(
            adb,
            monotonic=clock.monotonic,
            sleep=clock.sleep,
        )

        def execute():
            recorder.begin_critical_window("switch_during_prove_present")
            process.running = False
            recorder.end_critical_window("switch_during_prove_present")
            return {"caseId": "switch_during_prove_present"}

        with mock.patch(
            "h19_active_document_video_evidence.subprocess.Popen",
            return_value=process,
        ), mock.patch.object(
            recorder,
            "_analyze",
            side_effect=HarnessError("secondary-analysis-failure"),
        ):
            with self.assertRaisesRegex(
                HarnessError,
                "screenrecord ended before critical terminal",
            ):
                recorder.run_case("switch_during_prove_present", execute)

    def test_missing_or_invalid_visual_evidence_fails_closed(self):
        with self.assertRaisesRegex(HarnessError, "authority boundary invalid"):
            assert_visual_evidence("cold_foreground_release", {})

        value = bounded(case_id="cold_foreground_release", safe=[2])
        value["safeFixtureVisibleFrames"] = "invalid"
        with self.assertRaisesRegex(HarnessError, "analyzer result invalid"):
            assert_visual_evidence("cold_foreground_release", value)

    def test_cold_requires_a_visible_positive_control(self):
        with self.assertRaisesRegex(HarnessError, "positive control was not visible"):
            assert_visual_evidence(
                "cold_foreground_release",
                bounded(case_id="cold_foreground_release"),
            )

        checked = assert_visual_evidence(
            "cold_foreground_release",
            bounded(case_id="cold_foreground_release", safe=[2]),
        )
        self.assertEqual([2], checked["safeFixtureVisibleFrames"])

    def test_no_release_race_checks_entire_preroll_to_terminal_recording(self):
        case_id = "switch_during_prove_present"
        evidence = bounded(
            case_id=case_id,
            sentinel=[8],
            windows=((0.5, 1.0),),
        )
        self.assertEqual(
            "entire_recording_preroll_to_terminal",
            evidence["criticalFrameSelection"],
        )
        self.assertEqual([8], evidence["criticalSentinelVisibleFrames"])
        with self.assertRaisesRegex(HarnessError, "critical no-release interval"):
            assert_visual_evidence(case_id, evidence)

        clean = bounded(case_id=case_id, windows=((0.5, 1.0),))
        assert_visual_evidence(case_id, clean)

    def test_no_release_race_requires_a_sampled_critical_window(self):
        case_id = "switch_during_hello"
        with self.assertRaisesRegex(HarnessError, "critical exposure evidence missing"):
            assert_visual_evidence(case_id, bounded(case_id=case_id))


if __name__ == "__main__":
    unittest.main()
