import unittest
from unittest.mock import Mock, patch

from h19_lifecycle_gates import (
    open_controlled_new_tab,
    ready_document_key,
    run_two_tab_binding_gate,
    wait_for_existing_document_fail_close,
)
from h19_plan import HarnessError


def marker(token: str, source: str, lifecycle: int = 1) -> dict[str, object]:
    return {
        "package": "com.android.chrome",
        "binding": "event_source",
        "axBound": True,
        "rawPresented": False,
        "windowId": "17",
        "documentSequence": "1" if token.startswith("a") else "2",
        "lifecycle": str(lifecycle),
        "tokenDigestPrefix": token,
        "rootDigestPrefix": "root" + token,
        "sourceDigestPrefix": source,
    }


class FakeTabAdb:
    def __init__(self) -> None:
        self.calls: list[tuple[tuple[str, ...], dict]] = []

    def shell(self, *args, **kwargs):
        self.calls.append((tuple(args), kwargs))
        return "Status: ok"


class H19LifecycleGatesTest(unittest.TestCase):
    def test_new_tab_uses_public_android_browser_extra_without_ui_coordinates(self) -> None:
        adb = FakeTabAdb()

        evidence = open_controlled_new_tab(adb)

        args, kwargs = adb.calls[0]
        self.assertEqual("android.provider.Browser.EXTRA_CREATE_NEW_TAB", evidence["mechanism"])
        self.assertIn(("--ez", "create_new_tab", "true"), tuple(zip(args, args[1:], args[2:])))
        self.assertNotIn("input", args)
        self.assertEqual(45, kwargs["timeout"])

    def test_two_tab_gate_requires_distinct_b_then_exact_a_binding(self) -> None:
        adb = FakeTabAdb()
        tab_a = marker("aaaaaaaaaaaa", "source-a")
        tab_b = marker("bbbbbbbbbbbb", "source-b")
        tab_a_return = marker("aaaaaaaaaaaa", "source-a", lifecycle=2)
        waits = Mock(
            side_effect=[
                {"marker": tab_b, "releaseCountInPhase": 5},
                {"marker": tab_a_return, "releaseCountInPhase": 6},
            ]
        )

        evidence = run_two_tab_binding_gate(adb, "since", 8, 4, tab_a, waits)

        self.assertTrue(evidence["pass"])
        self.assertTrue(evidence["tabBDistinct"])
        self.assertTrue(evidence["tabADocumentRestored"])
        self.assertTrue(evidence["tabALifecycleAdvanced"])
        self.assertFalse(evidence["crossTabRelease"])
        self.assertEqual(ready_document_key(tab_a), ready_document_key(evidence["tabAReturn"]))
        self.assertEqual(6, waits.call_args_list[1].kwargs["minimum_release_count"])
        self.assertIn(("input", "keyevent", "4"), [call[0] for call in adb.calls])

    def test_two_tab_gate_fails_closed_if_b_binding_is_replayed_on_a(self) -> None:
        adb = FakeTabAdb()
        tab_a = marker("aaaaaaaaaaaa", "source-a")
        tab_b = marker("bbbbbbbbbbbb", "source-b")
        waits = Mock(
            side_effect=[
                {"marker": tab_b, "releaseCountInPhase": 5},
                {"marker": tab_b, "releaseCountInPhase": 6},
            ]
        )

        with self.assertRaises(HarnessError):
            run_two_tab_binding_gate(adb, "since", 8, 4, tab_a, waits)

    def test_two_tab_gate_requires_the_complete_original_a_binding(self) -> None:
        adb = FakeTabAdb()
        tab_a = marker("aaaaaaaaaaaa", "source-a")
        tab_b = marker("bbbbbbbbbbbb", "source-b")
        changed_source_a = {
            **marker("aaaaaaaaaaaa", "source-a", lifecycle=2),
            "sourceDigestPrefix": "different-source",
        }
        waits = Mock(
            side_effect=[
                {"marker": tab_b, "releaseCountInPhase": 5},
                {"marker": changed_source_a, "releaseCountInPhase": 6},
            ]
        )

        with self.assertRaises(HarnessError):
            run_two_tab_binding_gate(adb, "since", 8, 4, tab_a, waits)

    def test_restart_fail_close_requires_no_binding_and_attached_opaque_surface(self) -> None:
        request = Mock(
            side_effect=[
                (
                    "",
                    {
                        "currentReadyBinding": None,
                        "readyPhases": {"ready_foreground_released": 1},
                        "currentSurfaceState": {
                            "phase": "data_plane_lease",
                            "action": "waiting",
                            "reason": "foreground_ready_absent",
                            "transparent": False,
                            "rawPresented": False,
                            "attachmentCount": 1,
                        },
                    },
                ),
                (
                    "",
                    {
                        "currentReadyBinding": {"tokenDigestPrefix": "stale"},
                        "currentSurfaceState": {
                            "phase": "data_plane_lease",
                            "action": "waiting",
                            "reason": "foreground_ready_absent",
                            "transparent": False,
                            "rawPresented": False,
                            "attachmentCount": 1,
                        },
                    },
                ),
                (
                    "",
                    {
                        "currentReadyBinding": None,
                        "currentSurfaceState": {
                            "phase": "data_plane_lease",
                            "action": "waiting",
                            "reason": "foreground_ready_absent",
                            "transparent": False,
                            "rawPresented": False,
                            "attachmentCount": 1,
                        },
                    },
                ),
            ]
        )
        with patch("h19_lifecycle_gates.time.sleep"):
            evidence = wait_for_existing_document_fail_close(object(), "since", 1, request)

        self.assertTrue(evidence["pass"])
        self.assertFalse(evidence["existingDocumentReboundBeforeReload"])
        self.assertTrue(evidence["explicitNavigationOrReloadRequired"])
        self.assertEqual(3, request.call_count)


if __name__ == "__main__":
    unittest.main()
