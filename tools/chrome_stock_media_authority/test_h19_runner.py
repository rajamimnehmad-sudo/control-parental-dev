import tempfile
import unittest
from pathlib import Path
from unittest.mock import Mock, patch

from h19_plan import HarnessError
from h19_ready import current_ready_result
from h19_device import (
    ce_data_inode_from_package_dump,
    controlled_navigation_url,
    navigate,
    prepare_interactive_display,
    restore_interactive_display,
)
from h19_navigation_admission import (
    materialize_controlled_navigation,
    materialize_restart_target,
    wait_for_controlled_document_admission,
)
from h19_proxy_policy import CHROME_PROXY_POLICY, chrome_proxy_policy_transition
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
    wait_for_web_root_continuity,
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


class FakeNavigationAdb:
    def __init__(self) -> None:
        self.calls: list[tuple[str, ...]] = []

    def shell(self, *args, **_kwargs):
        self.calls.append(tuple(args))
        return "Status: ok"


class FakeInteractiveAdb:
    def __init__(self, initially_awake: bool = False) -> None:
        self.awake = initially_awake
        self.stay_awake = "0"
        self.calls: list[tuple[str, ...]] = []

    def shell(self, *args, **_kwargs):
        self.calls.append(tuple(args))
        if args[:2] == ("dumpsys", "power"):
            return f"mWakefulness={'Awake' if self.awake else 'Dozing'}\n"
        if args[:3] == ("dumpsys", "window", "policy"):
            state = "INTERACTIVE_STATE_AWAKE" if self.awake else "INTERACTIVE_STATE_SLEEP"
            return f"interactiveState={state}\nmIsShowing=false\n"
        if args[:4] == ("settings", "get", "global", "stay_on_while_plugged_in"):
            return self.stay_awake
        if args[:4] == ("settings", "put", "global", "stay_on_while_plugged_in"):
            self.stay_awake = args[4]
        if args[:3] == ("input", "keyevent", "224"):
            self.awake = True
        if args[:3] == ("input", "keyevent", "223"):
            self.awake = False
        return ""


class H19RunnerTest(unittest.TestCase):
    def test_chrome_proxy_policy_requires_current_pid_set_then_flush(self):
        correct_set = (
            "08-30 00:28:57.654  5729  5729 I cr_CombinedPProvider: "
            f"#setPolicy() ProxySettings -> {CHROME_PROXY_POLICY}"
        )
        wrong_pid_flush = "08-30 00:28:57.655  9999  9999 I cr_CombinedPProvider: #flushPolicies()"
        correct_flush = "08-30 00:28:57.656  5729  5729 I cr_CombinedPProvider: #flushPolicies()"

        self.assertIsNone(chrome_proxy_policy_transition([correct_set, wrong_pid_flush], 5729))
        evidence = chrome_proxy_policy_transition([correct_set, wrong_pid_flush, correct_flush], 5729)

        self.assertIsNotNone(evidence)
        self.assertTrue(evidence["pass"])
        self.assertEqual(5729, evidence["browserPid"])

        wrong_set = (
            "08-30 00:28:57.655  5729  5729 I cr_CombinedPProvider: "
            "#setPolicy() ProxySettings -> {\"ProxyMode\":\"direct\"}"
        )
        self.assertIsNone(chrome_proxy_policy_transition([correct_set, wrong_set, correct_flush], 5729))

    def test_chrome_proxy_policy_rejects_flush_before_current_set(self):
        flush = "08-30 00:28:57.600  5729  5729 I cr_CombinedPProvider: #flushPolicies()"
        set_line = (
            "08-30 00:28:57.654  5729  5729 I cr_CombinedPProvider: "
            f"#setPolicy() ProxySettings -> {CHROME_PROXY_POLICY}"
        )

        self.assertIsNone(chrome_proxy_policy_transition([flush, set_line], 5729))

    def test_controlled_navigation_url_is_fresh_but_keeps_exact_fixture_origin_and_path(self):
        first = controlled_navigation_url("a" * 32, 1)
        second = controlled_navigation_url("a" * 32, 2)

        self.assertEqual("https://glosh-photos.test/web19/controlled?h19_nav=" + "a" * 32 + "-1", first)
        self.assertNotEqual(first, second)

    def test_only_fresh_controlled_navigation_consumes_a_sequence(self):
        controlled, sequence = materialize_controlled_navigation(
            {"id": "a", "navigation": "controlled"},
            "b" * 32,
            0,
        )
        reload_state, after_reload = materialize_controlled_navigation(
            {"id": "reload", "navigation": "reload"},
            "b" * 32,
            sequence,
        )
        tab, after_tab = materialize_controlled_navigation(
            {"id": "tab", "navigation": "two-tab-binding"},
            "b" * 32,
            after_reload,
        )

        self.assertIn("controlledUrl", controlled)
        self.assertNotIn("controlledUrl", reload_state)
        self.assertEqual(1, after_reload)
        self.assertIn("h19_nav=" + "b" * 32 + "-2", tab["controlledUrl"])
        self.assertEqual(2, after_tab)

    def test_chrome_restart_reuses_only_a_known_explicit_target(self):
        explicit, target = materialize_restart_target(
            {"id": "site", "navigation": "url", "url": "https://example.test/page"},
            "",
        )
        restart, target_after_restart = materialize_restart_target(
            {"id": "restart", "navigation": "restart-chrome"},
            target,
        )

        self.assertEqual("https://example.test/page", explicit["url"])
        self.assertEqual("https://example.test/page", restart["restartUrl"])
        self.assertEqual(target, target_after_restart)

    def test_chrome_restart_rejects_unknown_target_after_back_or_tab_switch(self):
        _, target = materialize_restart_target(
            {"id": "back", "navigation": "back"},
            "https://example.test/page",
        )

        self.assertEqual("", target)
        with self.assertRaises(HarnessError):
            materialize_restart_target({"id": "restart", "navigation": "restart-chrome"}, target)

    def test_chrome_restart_opens_only_local_policy_page_before_network_target(self):
        adb = FakeNavigationAdb()

        navigate(adb, {"navigation": "restart-chrome"})

        self.assertEqual(("am", "force-stop", "com.android.chrome"), adb.calls[0])
        self.assertIn("chrome://policy", adb.calls[1])
        self.assertFalse(any("http://" in value or "https://" in value for value in adb.calls[1]))

    def test_physical_gate_acquires_and_restores_interactive_display_lease(self):
        adb = FakeInteractiveAdb(initially_awake=False)

        lease = prepare_interactive_display(adb, timeout_seconds=1)

        self.assertEqual("Awake", lease["current"]["wakefulness"])
        self.assertEqual("3", adb.stay_awake)
        self.assertTrue(lease["restoreSleep"])

        restored = restore_interactive_display(adb, lease)

        self.assertEqual("0", restored["stayOnWhilePluggedIn"])
        self.assertEqual("Dozing", restored["display"]["wakefulness"])
        self.assertIn(("wm", "dismiss-keyguard"), adb.calls)

    def test_already_awake_device_is_not_put_to_sleep_at_cleanup(self):
        adb = FakeInteractiveAdb(initially_awake=True)

        lease = prepare_interactive_display(adb, timeout_seconds=1)
        restored = restore_interactive_display(adb, lease)

        self.assertFalse(lease["restoreSleep"])
        self.assertEqual("Awake", restored["display"]["wakefulness"])
        self.assertNotIn(("input", "keyevent", "223"), adb.calls)

    def test_tab_switch_uses_exact_binding_and_is_not_aliased_to_app_foregrounding(self):
        self.assertEqual("SUPPORTED", HARNESS_CAPABILITIES["stockChromeTabSwitch"]["status"])
        self.assertEqual(
            "event_source_one_shot_plus_web_root_continuity",
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
                "model": "R3.1",
                "modelSha": "c8b64af8092d3718c58736a511c996d0d443dacf3eaa74620b1e5af439a3cd48",
                "policy": "dag-36",
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
        ordering: list[str] = []
        policy = Mock(side_effect=lambda *_args: ordering.append("policy") or {"pass": True})
        foreground = Mock(side_effect=lambda *_args: ordering.append("foreground"))
        evidence, active = restart_glosh_phase(
            adb,
            "replace-all",
            "since",
            timeout_seconds=5,
            request_status=request,
            start_phase=start,
            observe_proxy_policy=policy,
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
        self.assertEqual(["policy", "foreground"], ordering)
        self.assertTrue(evidence["chromeProxyPolicyObserved"]["pass"])
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
            "documentSequence": "2",
            "surfaceEpoch": "8",
            "rootDigestPrefix": "root-old",
            "webRootDigestPrefix": "web-root-old",
            "sourceDigestPrefix": "source-old",
            "rootBinding": "native_root",
        }
        fresh = {
            **previous,
            "lifecycle": "8",
            "documentSequence": "3",
            "surfaceEpoch": "9",
            "tokenDigestPrefix": "fresh",
            "webRootDigestPrefix": "web-root-fresh",
        }
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
            "documentSequence": "2",
            "surfaceEpoch": "8",
            "rootDigestPrefix": "root",
            "webRootDigestPrefix": "web-root",
            "sourceDigestPrefix": "source",
            "rootBinding": "native_root",
            "continuity": "none",
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
        current = {
            "package": "com.android.chrome",
            "lifecycle": "2",
            "documentSequence": "4",
            "surfaceEpoch": "8",
            "tokenDigestPrefix": "token",
            "webRootDigestPrefix": "web-root",
        }

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
        self.assertEqual({"releaseCount": 4, "continuityCount": 0, "marker": current}, baseline)
        self.assertEqual(0, orientation["baselineRotation"])
        self.assertTrue(changed)

    def test_ready_advance_ignores_recreated_source_and_native_root_but_tracks_surface_epoch(self):
        previous = {
            "package": "com.android.chrome",
            "windowId": "4",
            "documentSequence": "2",
            "surfaceEpoch": "8",
            "lifecycle": "7",
            "tokenDigestPrefix": "token",
            "rootDigestPrefix": "native-root-a",
            "webRootDigestPrefix": "web-root",
            "sourceDigestPrefix": "source-a",
        }
        status = {
            "status": {"fields": {"active": "true", "lifecycle": "PresentationReady"}},
            "readyPhases": {"ready_foreground_released": 1},
        }
        recreated_source = {
            **previous,
            "rootDigestPrefix": "native-root-b",
            "sourceDigestPrefix": "source-b",
        }
        rotated = {**recreated_source, "surfaceEpoch": "9"}

        self.assertIsNone(
            current_ready_result(
                {**status, "currentReadyBinding": recreated_source},
                "com.android.chrome",
                1,
                previous,
                True,
            )
        )
        self.assertIsNotNone(
            current_ready_result(
                {**status, "currentReadyBinding": rotated},
                "com.android.chrome",
                1,
                previous,
                True,
            )
        )

    def test_ready_baseline_has_no_passive_marker_fallback(self):
        self.assertEqual(
            {"releaseCount": 2, "continuityCount": 0, "marker": None},
            ready_baseline(
                {
                    "readyPhases": {"ready_foreground_released": 2},
                    "readyMarkers": [{"tokenDigestPrefix": "historical-only"}],
                    "currentReadyBinding": None,
                }
            ),
        )

    def test_web_root_continuity_wait_requires_same_current_document_after_pruning(self):
        marker = {
            "package": "com.android.chrome",
            "binding": "event_source",
            "axBound": True,
            "rawPresented": False,
            "windowId": "4",
            "surfaceEpoch": "8",
            "documentSequence": "2",
            "lifecycle": "7",
            "tokenDigestPrefix": "aaaaaaaaaaaa",
            "rootDigestPrefix": "bbbbbbbbbbbb",
            "webRootDigestPrefix": "cccccccccccc",
            "sourceDigestPrefix": "dddddddddddd",
            "rootBinding": "native_root",
        }
        summary = {
            "readyWebRootContinuity": {
                "verified": 1,
                "violations": 0,
                "currentDocumentVerified": True,
            },
            "currentReadyBinding": marker,
        }
        with patch("run_a23_gate.request_status", return_value=("", summary)):
            result = wait_for_web_root_continuity(
                object(),
                "since",
                timeout_seconds=1,
                minimum_verified_count=1,
                expected_marker=marker,
            )

        self.assertTrue(result["pass"])
        self.assertFalse(result["sourceCurrent"])

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

    def test_controlled_document_must_cross_fixture_and_transformer_before_ready(self):
        stale = {
            "fixtureReport": {"counts": {"documents": 4}},
            "status": {"fields": {"mediaDocumentsTransformed": "8"}},
        }
        fresh = {
            "fixtureReport": {"counts": {"documents": 5}},
            "status": {"fields": {"mediaDocumentsTransformed": "9"}},
        }
        with patch("h19_navigation_admission.time.sleep"):
            result = wait_for_controlled_document_admission(
                object(),
                "since",
                timeout_seconds=1,
                minimum_documents=4,
                minimum_transformed=8,
                request_status=Mock(side_effect=[("stale", stale), ("fresh", fresh)]),
            )

        self.assertTrue(result["pass"])
        self.assertEqual(5, result["documents"])
        self.assertEqual(9, result["mediaDocumentsTransformed"])


if __name__ == "__main__":
    unittest.main()
