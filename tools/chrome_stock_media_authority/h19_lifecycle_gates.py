"""Bounded stock-Chrome tab and Glosh restart gates for the H19 harness."""

from __future__ import annotations

import subprocess
import time
from typing import Any, Callable

from h19_device import APP_PACKAGE, CHROME_PACKAGE, CONTROLLED_URL, sha256_text
from h19_plan import HarnessError


ReadyWait = Callable[..., dict[str, Any]]
StatusRequest = Callable[[Any, str], tuple[str, dict[str, Any]]]
PhaseStart = Callable[..., dict[str, Any]]

READY_BINDING_FIELDS = (
    "windowId",
    "documentSequence",
    "lifecycle",
    "tokenDigestPrefix",
    "rootDigestPrefix",
    "sourceDigestPrefix",
)
READY_DOCUMENT_FIELDS = tuple(field for field in READY_BINDING_FIELDS if field != "lifecycle")


def ready_binding_key(marker: dict[str, Any] | None) -> tuple[str, ...] | None:
    if not isinstance(marker, dict):
        return None
    if (
        marker.get("package") != CHROME_PACKAGE
        or marker.get("binding") != "event_source"
        or marker.get("axBound") is not True
        or marker.get("rawPresented") is not False
        or not str(marker.get("windowId", "")).isdigit()
        or not str(marker.get("documentSequence", "")).isdigit()
        or not str(marker.get("lifecycle", "")).isdigit()
    ):
        return None
    values = tuple(str(marker.get(field, "")) for field in READY_BINDING_FIELDS)
    return values if all(values) else None


def ready_document_key(marker: dict[str, Any] | None) -> tuple[str, ...] | None:
    if ready_binding_key(marker) is None:
        return None
    return tuple(str(marker.get(field, "")) for field in READY_DOCUMENT_FIELDS)


def open_controlled_new_tab(adb: Any) -> dict[str, Any]:
    """Use Android's public Browser.EXTRA_CREATE_NEW_TAB contract."""

    output = adb.shell(
        "am",
        "start",
        "-W",
        "-a",
        "android.intent.action.VIEW",
        "-d",
        CONTROLLED_URL,
        "-p",
        CHROME_PACKAGE,
        "--ez",
        "create_new_tab",
        "true",
        timeout=45,
    )
    if "Error:" in output or "Exception" in output:
        raise HarnessError("stock Chrome rejected the bounded create-new-tab intent")
    return {
        "mechanism": "android.provider.Browser.EXTRA_CREATE_NEW_TAB",
        "targetSha256": sha256_text(CONTROLLED_URL),
        "intentResultSha256": sha256_text(output),
    }


def run_two_tab_binding_gate(
    adb: Any,
    since: str,
    timeout_seconds: int,
    baseline_release_count: int,
    tab_a_marker: dict[str, Any] | None,
    wait_ready: ReadyWait,
) -> dict[str, Any]:
    """Open controlled tab B, then Back to A, proving exact READY identities."""

    tab_a_key = ready_binding_key(tab_a_marker)
    tab_a_document = ready_document_key(tab_a_marker)
    if tab_a_key is None or tab_a_document is None:
        raise HarnessError("two-tab gate requires an exact current tab-A READY binding")
    opened = False
    returned = False
    tab_b: dict[str, Any] | None = None
    try:
        intent_evidence = open_controlled_new_tab(adb)
        opened = True
        tab_b = wait_ready(
            adb,
            since,
            timeout_seconds,
            minimum_release_count=baseline_release_count + 1,
            previous_marker=tab_a_marker,
            require_advance=True,
        )
        tab_b_marker = tab_b.get("marker")
        tab_b_key = ready_binding_key(tab_b_marker if isinstance(tab_b_marker, dict) else None)
        tab_b_document = ready_document_key(tab_b_marker if isinstance(tab_b_marker, dict) else None)
        if tab_b_key is None or tab_b_document is None or tab_b_document == tab_a_document:
            raise HarnessError("new Chrome tab did not acquire a distinct exact READY binding")
        adb.shell("input", "keyevent", "4")
        returned = True
        tab_a_return = wait_ready(
            adb,
            since,
            timeout_seconds,
            minimum_release_count=int(tab_b.get("releaseCountInPhase", 0)) + 1,
            previous_marker=tab_b_marker,
            require_advance=True,
        )
        return_marker = tab_a_return.get("marker")
        return_key = ready_binding_key(return_marker if isinstance(return_marker, dict) else None)
        return_document = ready_document_key(return_marker if isinstance(return_marker, dict) else None)
        old_lifecycle = int(str(tab_a_marker.get("lifecycle", "0")))
        return_lifecycle = int(str(return_marker.get("lifecycle", "0"))) if isinstance(return_marker, dict) else 0
        if (
            return_key is None
            or return_document != tab_a_document
            or return_key == tab_b_key
            or return_lifecycle <= old_lifecycle
        ):
            raise HarnessError("tab-B READY binding crossed the return boundary to tab A")
        return {
            "pass": True,
            "kind": "stock_chrome_two_tab_foreground_binding",
            "intent": intent_evidence,
            "tabA": tab_a_marker,
            "tabB": tab_b_marker,
            "tabAReturn": return_marker,
            "tabBDistinct": True,
            "tabADocumentRestored": True,
            "tabALifecycleAdvanced": True,
            "crossTabRelease": False,
            "releaseCountInPhase": int(tab_a_return.get("releaseCountInPhase", 0)),
            "marker": return_marker,
        }
    finally:
        if opened and not returned:
            adb.shell("input", "keyevent", "4", check=False)


def wait_for_existing_document_fail_close(
    adb: Any,
    since: str,
    timeout_seconds: int,
    request_status: StatusRequest,
) -> dict[str, Any]:
    """Observe a new-session opaque surface before any explicit reload."""

    deadline = time.monotonic() + timeout_seconds
    last: dict[str, Any] = {}
    while time.monotonic() < deadline:
        _, last = request_status(adb, since)
        surface = last.get("currentSurfaceState")
        release_count = int(last.get("readyPhases", {}).get("ready_foreground_released", 0))
        if (
            last.get("currentReadyBinding") is None
            and release_count == 0
            and isinstance(surface, dict)
            and surface.get("phase") == "data_plane_lease"
            and surface.get("action") == "waiting"
            and surface.get("reason") == "foreground_ready_absent"
            and surface.get("transparent") is False
            and surface.get("rawPresented") is False
            and int(surface.get("attachmentCount", 0)) >= 1
        ):
            return {
                "pass": True,
                "currentReadyBindingAbsent": True,
                "surface": surface,
                "releaseCountSinceRestart": 0,
                "existingDocumentReboundBeforeReload": False,
                "explicitNavigationOrReloadRequired": True,
            }
        time.sleep(0.25)
    raise HarnessError(
        "existing Chrome document was not demonstrably fail-closed before explicit reload: "
        f"currentReadyBinding={last.get('currentReadyBinding')} "
        f"surface={last.get('currentSurfaceState')}"
    )


def restart_glosh_phase(
    adb: Any,
    mode: str,
    since: str,
    timeout_seconds: int,
    request_status: StatusRequest,
    start_phase: PhaseStart,
    foreground_current: Callable[[Any], None],
) -> tuple[dict[str, Any], dict[str, Any]]:
    """Restart Glosh, prove the existing document opaque, and leave reload to the caller."""

    overall_deadline = time.monotonic() + timeout_seconds
    _, before = request_status(adb, since)
    previous_session = str(before.get("activeMode", {}).get("session", ""))
    before_pids = set(adb.shell("pidof", APP_PACKAGE, check=False).split())
    if not previous_session or not before_pids:
        raise HarnessError("cannot prove the current H19 session/main process before restart")
    try:
        adb.broadcast("com.contentfilter.user.chromedataplane.command.MAIN_PROCESS_KILL")
    except subprocess.CalledProcessError:
        # The receiver intentionally kills its own process before `am broadcast`
        # is guaranteed to observe a normal completion.
        pass
    deadline = min(overall_deadline, time.monotonic() + 10)
    after_kill_pids: set[str] = set()
    while time.monotonic() < deadline:
        after_kill_pids = set(adb.shell("pidof", APP_PACKAGE, check=False).split())
        services = adb.shell("dumpsys", "activity", "services", APP_PACKAGE, check=False)
        if before_pids.isdisjoint(after_kill_pids) and "ChromePhotosDataPlaneLabService" not in services:
            break
        time.sleep(0.1)
    else:
        raise HarnessError("Glosh main process/service did not cross the bounded restart boundary")
    restarted_since = adb.shell("date", "+%m-%d %H:%M:%S.000").strip()
    remaining = max(1, int(overall_deadline - time.monotonic()))
    active = start_phase(
        adb,
        mode,
        restarted_since,
        remaining,
        previous_session=previous_session,
    )
    after_pids = set(adb.shell("pidof", APP_PACKAGE, check=False).split())
    current_session = str(active.get("activeMode", {}).get("session", ""))
    if not after_pids or not before_pids.isdisjoint(after_pids) or current_session == previous_session:
        raise HarnessError("Glosh restart did not produce a fresh process and protection session")
    foreground_current(adb)
    fail_close = wait_for_existing_document_fail_close(
        adb,
        restarted_since,
        max(1, int(overall_deadline - time.monotonic())),
        request_status,
    )
    return (
        {
            "performed": True,
            "oldPidCount": len(before_pids),
            "newPidCount": len(after_pids),
            "oldSessionSha256": sha256_text(previous_session),
            "newSessionSha256": sha256_text(current_session),
            "labServiceObservedStopped": True,
            "modeRestored": mode,
            "preReloadFailClose": fail_close,
            "reloadPerformedByRestartHelper": False,
        },
        active,
    )
