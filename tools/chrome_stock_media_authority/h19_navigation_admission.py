"""Fresh controlled-navigation identities and pre-READY activity admission for H19."""

from __future__ import annotations

import re
import time
from typing import Any, Callable

from h19_device import CONTROLLED_URL, Adb, controlled_navigation_url
from h19_evidence import status_counter_snapshot
from h19_plan import HarnessError


StatusRequest = Callable[[Adb, str], tuple[str, dict[str, Any]]]
StatusRefresh = Callable[..., tuple[str, dict[str, Any]]]


def wait_for_controlled_document_admission(
    adb: Adb,
    since: str,
    timeout_seconds: int,
    minimum_documents: int,
    minimum_transformed: int,
    observe_status: StatusRequest,
    refresh_status: StatusRefresh,
) -> dict[str, Any]:
    """Require post-navigation fixture/transform activity before evaluating exact READY authority."""

    deadline = time.monotonic() + timeout_seconds
    last_documents = minimum_documents
    last_transformed = minimum_transformed
    snapshot_requested = False
    while time.monotonic() < deadline:
        value, summary = observe_status(adb, since)
        fixture_documents = len(
            re.findall(r"\bphase=media_shield_document origin=fixture\b", value)
        )
        if not snapshot_requested and fixture_documents > minimum_documents:
            _, summary = refresh_status(adb, since, include_transport=False)
            snapshot_requested = True
        last_documents = int(summary.get("fixtureReport", {}).get("counts", {}).get("documents", 0))
        last_transformed = status_counter_snapshot(summary).get("mediaDocumentsTransformed", 0)
        if last_documents > minimum_documents and last_transformed > minimum_transformed:
            return {
                "pass": True,
                "documents": last_documents,
                "mediaDocumentsTransformed": last_transformed,
                "authority": False,
                "purpose": "pre_ready_activity_gate",
            }
        time.sleep(0.25)
    raise HarnessError(
        "controlled document did not cross the transformer before READY; "
        f"classification=PROXY_POLICY_NOT_ACTIVE documents={last_documents} "
        f"mediaDocumentsTransformed={last_transformed}"
    )


def materialize_controlled_navigation(
    state: dict[str, Any],
    run_nonce: str,
    current_sequence: int,
) -> tuple[dict[str, Any], int]:
    """Attach a unique target only to fresh controlled-document navigations."""

    result = dict(state)
    if result.get("navigation", "url") not in {"controlled", "two-tab-binding"}:
        return result, current_sequence
    next_sequence = current_sequence + 1
    result["controlledUrl"] = controlled_navigation_url(run_nonce, next_sequence)
    return result, next_sequence


def materialize_restart_target(
    state: dict[str, Any],
    last_explicit_target: str,
) -> tuple[dict[str, Any], str]:
    """Carry only a known explicit URL across the Chrome policy-admission restart boundary."""

    result = dict(state)
    navigation = result.get("navigation", "url")
    if navigation == "url":
        return result, str(result["url"])
    if navigation == "controlled":
        return result, str(result.get("controlledUrl", CONTROLLED_URL))
    if navigation == "restart-chrome":
        if not last_explicit_target:
            raise HarnessError("restart-chrome requires a previously known explicit network target")
        result["restartUrl"] = last_explicit_target
        return result, last_explicit_target
    if navigation in {"back", "forward", "two-tab-binding"}:
        return result, ""
    return result, last_explicit_target
