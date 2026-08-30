import tempfile
import unittest
from pathlib import Path
from unittest.mock import Mock, patch

from h19_plan import HarnessError
from h19_device import ce_data_inode_from_package_dump
from run_a23_gate import (
    HARNESS_CAPABILITIES,
    baseline_then_set_orientation,
    enforce_counter_gate,
    exit_info_delta,
    ready_baseline,
    restart_glosh_phase,
    set_and_verify_orientation,
    start_phase,
    wait_for_fixture_report,
    wait_for_ready,
    write_logcat_summary,
)


class FakeRotationAdb:
    def __init__(self) -> None:
        self.rotation = 0
        self.calls: list[tuple[str, ...]] = []

    def shell(self, *args, **_kwargs):
        self.calls.append(tuple(args))
        if args[:4] == ("settings", "put", "system", "user_rotation"):
            self.rotation = int(args[4])
        if args[:2] == ("dumpsys", "input"):
            return f"SurfaceOrientation: {self.rotation}\n"
        return ""


class FakeProcessRestartAdb:
    def __init__(self) -> None:
        self.pid_calls = 0
        self.broadcasts: list[str] = []

    def shell(self, *args, **_kwargs):
        if args[:1] == ("date",):
            return "08-29 12:00:00.000"
        if args[:2] == ("pidof", "com.contentfilter.user.dev"):
            self.pid_calls += 1
            return "101" if self.pid_calls == 1 else "202"
        if args[:3] == ("dumpsys", "activity", "services"):
            return ""
        return ""

    def broadcast(self, action, _extras=None):
        self.broadcasts.append(action)
        return ""


class FakeStartAdb:
    def __init__(self) -> None:
        self.broadcasts: list[str] = []

    def broadcast(self, action, _extras=None):
        self.broadcasts.append(action)
        return ""


class H19RunnerTest(unittest.TestCase):
    def test_tab_switch_uses_exact_binding_and_is_not_aliased_to_app_foregrounding(self):
        self.assertEqual("SUPPORTED", HARNESS_CAPABILITIES["stockChromeTabSwitch"]["status"])
        self.assertEqual(
            "exact_event_source_ready_binding",
            HARNESS_CAPABILITIES["stockChromeTabSwitch"]["authority"],
        )
        self.assertFalse(HARNESS_CAPABILITIES["stockChromeTabSwitch"]["coordinateUiAutomation"])
        self.assertTrue(HARNESS_CAPABILITIES["stockChromeTabSwitch"]["countsAsPass"])
        self.assertFalse(HARNESS_CAPABILITIES["backgroundForeground"]["countsAsTabSwitch"])

    def test_phase_start_waits_for_guard_release_not_merely_proxy_ready(self):
        common = {
            "activeMode": {
                "networkVisualMode": "replace_all",
                "stockMediaAuthority": "true",
                "transport": "full_tunnel_dev",
                "session": "current",
            },
        }
        suspended = {
            **common,
            "status": {
                "fields": {
                    "active": "true",
                    "lifecycle": "ProxyReady",
                    "ready": "false",
                    "chromeSuspended": "true",
                },
            },
        }
        released = {
            **common,
            "status": {
                "fields": {
                    "active": "true",
                    "lifecycle": "PresentationReady",
                    "ready": "true",
                    "chromeSuspended": "false",
                },
            },
        }
        with (
            patch("run_a23_gate.request_status", side_effect=[("", suspended), ("", released)]) as status,
            patch("run_a23_gate.time.sleep"),
        ):
            result = start_phase(FakeStartAdb(), "replace-all", "since", timeout_seconds=1)

        self.assertEqual(released, result)
        self.assertEqual(2, status.call_count)

    def test_ce_data_inode_uses_package_manager_for_non_debuggable_dev(self):
        package_dump = (
            "Packages:\n"
            "  Package [com.contentfilter.user.dev]\n"
            "    User 0: ceDataInode=1239519 installed=true hidden=false\n"
        )

        self.assertEqual("1239519", ce_data_inode_from_package_dump(package_dump))
        with self.assertRaises(HarnessError):
            ce_data_inode_from_package_dump("User 0: installed=true")

    def test_only_aggregate_logcat_summary_is_persisted(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            output = root / "logcat-summary.json"
            write_logcat_summary(output, {"lineCount": 1, "sha256": "a" * 64})

            self.assertTrue(output.is_file())
            self.assertFalse((root / "logcat.txt").exists())
            self.assertNotIn("http", output.read_text())

            with self.assertRaises(HarnessError):
                write_logcat_summary(
                    output,
                    {"unsafe": "ActivityTaskManager START dat=https://shop.example/private"},
                )

    def test_glosh_restart_uses_exact_kill_and_requires_fresh_session(self):
        adb = FakeProcessRestartAdb()
        before = {"activeMode": {"session": "old-session"}}
        after = {"activeMode": {"session": "new-session"}}
        fail_closed = {
            "currentReadyBinding": None,
            "currentSurfaceState": {
                "phase": "data_plane_lease",
                "action": "waiting",
                "reason": "foreground_ready_absent",
                "transparent": False,
                "rawPresented": False,
                "attachmentCount": 1,
            },
        }
        request = Mock(side_effect=[("", before), ("", fail_closed)])
        start = Mock(return_value=after)
        foreground = Mock()
        evidence, active = restart_glosh_phase(
            adb,
            "replace-all",
            "since",
            timeout_seconds=5,
            request_status=request,
            start_phase=start,
            foreground_current=foreground,
        )

        self.assertEqual(after, active)
        self.assertTrue(evidence["performed"])
        self.assertTrue(evidence["preReloadFailClose"]["pass"])
        self.assertFalse(evidence["reloadPerformedByRestartHelper"])
        self.assertEqual(
            ["com.contentfilter.user.chromedataplane.command.MAIN_PROCESS_KILL"],
            adb.broadcasts,
        )
        self.assertEqual("old-session", start.call_args.kwargs["previous_session"])
        self.assertEqual("08-29 12:00:00.000", start.call_args.args[2])
        foreground.assert_called_once_with(adb)

    def test_orientation_is_verified_before_state_recording(self):
        adb = FakeRotationAdb()

        result = set_and_verify_orientation(adb, "landscape", timeout_seconds=1)

        self.assertTrue(result["verified"])
        self.assertEqual(1, result["observedRotation"])

    def test_exit_info_is_gated_as_pre_post_delta(self):
        before = {"reasons": {"crash": 4, "anr": 2, "lowMemory": 1}}
        after = {"reasons": {"crash": 4, "anr": 3, "lowMemory": 1}}

        self.assertEqual(
            {"crash": 0, "anr": 1, "lowMemory": 0},
            exit_info_delta(before, after),
        )

    def test_counter_gate_rejects_raw_replace_all_and_security_failures(self):
        healthy = {
            "networkVisualRawBlockedDelivered": 0,
            "networkVisualRawUnknownDelivered": 0,
            "proxyQueueRejects": 0,
            "protectFailure": 0,
            "quicAttempts": 0,
            "directTcpAttempts": 0,
            "networkVisualRawDelivered": 0,
        }
        enforce_counter_gate("replace-all", healthy, {key: 0 for key in healthy})

        with self.assertRaises(HarnessError):
            enforce_counter_gate("replace-all", {**healthy, "networkVisualRawDelivered": 1}, {})
        with self.assertRaises(HarnessError):
            enforce_counter_gate("selective", {**healthy, "protectFailure": 1}, {})

    def test_post_gesture_ready_requires_new_marker_not_prior_release(self):
        previous = {
            "package": "com.android.chrome",
            "lifecycle": "7",
            "tokenDigestPrefix": "old",
            "windowId": "4",
        }
        fresh = {**previous, "lifecycle": "8", "tokenDigestPrefix": "fresh"}
        status = {
            "status": {"fields": {"active": "true", "lifecycle": "PresentationReady"}},
            "readyPhases": {"ready_foreground_released": 1},
        }
        with (
            patch(
                "run_a23_gate.request_status",
                side_effect=[
                    ("", {**status, "currentReadyBinding": previous}),
                    ("", {**status, "currentReadyBinding": fresh, "readyPhases": {"ready_foreground_released": 2}}),
                ],
            ),
            patch("run_a23_gate.time.sleep"),
        ):
            result = wait_for_ready(
                object(),
                "since",
                timeout_seconds=1,
                minimum_release_count=1,
                previous_marker=previous,
                require_advance=True,
            )

        self.assertEqual("fresh", result["marker"]["tokenDigestPrefix"])
        self.assertEqual(2, result["attempts"])

    def test_ready_wait_accepts_the_current_verified_binding_without_a_new_release(self):
        current = {
            "package": "com.android.chrome",
            "lifecycle": "7",
            "tokenDigestPrefix": "current",
            "windowId": "4",
            "rootDigestPrefix": "root",
            "sourceDigestPrefix": "source",
        }
        summary = {
            "status": {"fields": {"active": "true", "lifecycle": "PresentationReady"}},
            "readyPhases": {"ready_foreground_released": 3},
            "currentReadyBinding": current,
        }
        with patch("run_a23_gate.request_status", return_value=("", summary)):
            result = wait_for_ready(
                object(),
                "phase-since",
                timeout_seconds=1,
                minimum_release_count=3,
                previous_marker=current,
                require_advance=False,
            )

        self.assertEqual(current, result["marker"])
        self.assertTrue(result["acceptedCurrentBinding"])
        self.assertFalse(result["markerAdvanced"])

    def test_rotation_baseline_is_captured_before_orientation_mutation(self):
        calls: list[str] = []
        current = {"package": "com.android.chrome", "lifecycle": "2"}

        def status(*_args, **_kwargs):
            calls.append("status")
            return "", {
                "readyPhases": {"ready_foreground_released": 4},
                "currentReadyBinding": current,
            }

        def observed(*_args, **_kwargs):
            calls.append("observed")
            return 0

        def rotate(*_args, **_kwargs):
            calls.append("rotate")
            return {"requested": "landscape", "observedRotation": 1, "verified": True}

        with (
            patch("run_a23_gate.request_status", side_effect=status),
            patch("run_a23_gate.observed_display_rotation", side_effect=observed),
            patch("run_a23_gate.set_and_verify_orientation", side_effect=rotate),
        ):
            baseline, orientation, changed = baseline_then_set_orientation(object(), "phase", "landscape")

        self.assertEqual(["status", "observed", "rotate"], calls)
        self.assertEqual({"releaseCount": 4, "marker": current}, baseline)
        self.assertEqual(0, orientation["baselineRotation"])
        self.assertTrue(changed)

    def test_ready_baseline_has_no_passive_marker_fallback(self):
        self.assertEqual(
            {"releaseCount": 2, "marker": None},
            ready_baseline(
                {
                    "readyPhases": {"ready_foreground_released": 2},
                    "readyMarkers": [{"tokenDigestPrefix": "historical-only"}],
                    "currentReadyBinding": None,
                }
            ),
        )

    def test_controlled_gate_requires_report_counts_to_advance(self):
        stale = {"fixtureReport": {"pass": True, "counts": {"reports": 1, "frame_reports": 1}}}
        fresh = {"fixtureReport": {"pass": True, "counts": {"reports": 2, "frame_reports": 2}}}
        with (
            patch("run_a23_gate.request_status", side_effect=[("stale", stale), ("fresh", fresh)]),
            patch("run_a23_gate.time.sleep"),
        ):
            log, summary = wait_for_fixture_report(
                object(),
                "since",
                timeout_seconds=1,
                minimum_reports=1,
                minimum_frame_reports=1,
            )

        self.assertEqual("fresh", log)
        self.assertEqual(2, summary["fixtureReport"]["counts"]["reports"])


if __name__ == "__main__":
    unittest.main()
