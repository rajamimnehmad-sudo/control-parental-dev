"""Pure evidence contract for the H19 active-document physical gate.

The module deliberately owns no ADB process and performs no device mutation.  It
does three small jobs for the eventual transactional runner integration:

* parse only allow-listed, typed HELLO/CHALLENGE/PROVE/PRESENT log events while
  retaining digest prefixes instead of raw capabilities;
* verify ordering, current-only release, one-shot/replay and per-case metric
  contracts for the sixteen reviewed physical cases; and
* describe the DUMP-only one-shot hold commands needed to make tab-switch races
  deterministic without sleeps, polling or synthetic authority.

Screen recordings remain evidence only.  Nothing in this module can grant a
presentation release.
"""

from __future__ import annotations

import re
from dataclasses import dataclass
from typing import Any, Iterable, Mapping, Sequence


PROTOCOL = "active_document_v3"
LOG_TAGS = ("ChromeMediaShieldReady", "ChromeMediaShieldActiveDocument")
TIMELINE_LIMIT = 256

CASE_IDS = (
    "cold_foreground_release",
    "background_tab_no_release",
    "foreground_a_background_b",
    "switch_during_hello",
    "switch_during_challenge",
    "switch_during_prove_present",
    "rapid_tab_switching",
    "reload",
    "back_forward_bfcache",
    "app_background_foreground",
    "omnibox_focus",
    "form_focus",
    "portrait_landscape",
    "process_restart",
    "stale_replay_token_reuse",
    "root_window_replacement",
)

HOLD_STAGES = (
    "hello_accepted",
    "challenge_issued",
    "proof_accepted",
    "present_precommit",
    "present_postcommit",
)

PHASES = {
    "active_hello_accepted",
    "active_hello_waiting_foreground",
    "active_hello_rejected",
    "challenge_issued",
    "proof_accepted",
    "proof_rejected",
    "present_accepted",
    "present_rejected",
    "active_document_released",
    "active_document_revoked",
    "active_document_revoke_rejected",
    "active_document_invalidated",
    "active_document_hold_armed",
    "active_document_hold_reached",
    "active_document_hold_released",
    "active_document_hold_cancelled",
    "active_document_status",
}

REASONS = {
    "hello_claim_invalid",
    "hello_claim_stale",
    "hello_superseded",
    "hello_foreground_ambiguous",
    "hello_context_stale",
    "hello_surface_failed",
    "prove_challenge_invalid",
    "prove_replay",
    "prove_context_changed",
    "prove_health_stale",
    "present_not_proved",
    "present_replay",
    "present_context_changed",
    "present_surface_not_opaque",
    "present_commit_failed",
    "present_postcommit_context_changed",
    "invalidated_hidden",
    "invalidated_pagehide",
    "invalidated_navigation",
    "invalidated_root",
    "invalidated_window",
    "invalidated_surface",
    "invalidated_session",
    "invalidated_stop",
    "invalidated_health",
    "hold_timeout",
    "hold_cancelled",
    "handshake_transport_cancelled",
    "handshake_closed",
    "foreground_window_unavailable",
    "foreground_root_unavailable",
    "foreground_root_package_mismatch",
    "foreground_root_window_mismatch",
    "foreground_root_identity_unavailable",
    "foreground_viewport_invalid",
    "context_read_exception",
}

COUNTER_FIELDS = (
    "activeHello",
    "challengeIssued",
    "proofAccepted",
    "presentAccepted",
    "backgroundRejected",
    "staleRejected",
    "staleReplayRejected",
    "crossTabRelease",
    "rejectedTransparentCommits",
    "opaqueRestoreFailures",
    "alphaSubmitFailures",
)
GAUGE_FIELDS = ("releaseCurrent", "replayCandidate", "alphaTransitionsOutstanding")
METRIC_FIELDS = COUNTER_FIELDS + GAUGE_FIELDS
STATUS_INTEGER_FIELDS = (
    "foregroundWindowId",
    "attemptWindowId",
    "pendingHandshake",
)
HOLD_PHASES = ("idle", "armed", "reached")

_DIGEST_FIELDS = {
    "sessionDigest": "sessionDigestPrefix",
    "tokenDigest": "tokenDigestPrefix",
    "challengeDigest": "challengeDigestPrefix",
    "rootDigest": "rootDigestPrefix",
}
_POSITIVE_INTEGER_FIELDS = (
    "eventSequence",
    "policyEpoch",
    "navigationSequence",
    "documentSequence",
    "lifecycle",
    "surfaceEpoch",
)
_DIGEST = re.compile(r"[0-9a-f]{12,64}")
_HOLD_NONCE = re.compile(r"[0-9a-f]{32}")
_KEY_VALUE = re.compile(r"(?:^|\s)([A-Za-z][A-Za-z0-9]*)=([^\s]+)")


class ActiveDocumentGateError(RuntimeError):
    """Fail-closed evidence or harness contract violation."""


@dataclass(frozen=True)
class ActiveDocumentCaseContract:
    case_id: str
    minimum_deltas: tuple[tuple[str, int], ...] = ()
    maximum_deltas: tuple[tuple[str, int], ...] = ()
    minimum_rejections: int = 0
    minimum_releases: int = 0
    maximum_releases: int = 0
    final_release_current: int | None = None
    allows_counter_epoch_reset: bool = False

    @property
    def minimum_delta_map(self) -> dict[str, int]:
        return dict(self.minimum_deltas)

    @property
    def maximum_delta_map(self) -> dict[str, int]:
        return dict(self.maximum_deltas)


def _contract(
    case_id: str,
    *,
    minimum: Mapping[str, int] | None = None,
    maximum: Mapping[str, int] | None = None,
    rejections: int = 0,
    releases: tuple[int, int] = (0, 0),
    current: int | None = None,
    reset: bool = False,
) -> ActiveDocumentCaseContract:
    return ActiveDocumentCaseContract(
        case_id=case_id,
        minimum_deltas=tuple(sorted((minimum or {}).items())),
        maximum_deltas=tuple(sorted((maximum or {}).items())),
        minimum_rejections=rejections,
        minimum_releases=releases[0],
        maximum_releases=releases[1],
        final_release_current=current,
        allows_counter_epoch_reset=reset,
    )


CASE_CONTRACTS = {
    "cold_foreground_release": _contract(
        "cold_foreground_release",
        minimum={"activeHello": 1, "challengeIssued": 1, "proofAccepted": 1, "presentAccepted": 1},
        releases=(1, 1),
        current=1,
    ),
    "background_tab_no_release": _contract(
        "background_tab_no_release",
        minimum={"backgroundRejected": 1},
        rejections=1,
        releases=(0, 0),
    ),
    "foreground_a_background_b": _contract(
        "foreground_a_background_b",
        minimum={"backgroundRejected": 1},
        rejections=1,
        releases=(0, 1),
        current=1,
    ),
    "switch_during_hello": _contract(
        "switch_during_hello",
        minimum={"activeHello": 1},
        rejections=1,
        releases=(0, 0),
        current=0,
    ),
    "switch_during_challenge": _contract(
        "switch_during_challenge",
        minimum={"activeHello": 1, "challengeIssued": 1},
        rejections=1,
        releases=(0, 0),
        current=0,
    ),
    "switch_during_prove_present": _contract(
        "switch_during_prove_present",
        minimum={"activeHello": 1, "challengeIssued": 1, "proofAccepted": 1},
        rejections=1,
        releases=(0, 0),
        current=0,
    ),
    "rapid_tab_switching": _contract(
        "rapid_tab_switching",
        rejections=1,
        releases=(0, 1),
        current=1,
    ),
    "reload": _contract(
        "reload",
        minimum={"activeHello": 1, "challengeIssued": 1, "proofAccepted": 1, "presentAccepted": 1},
        releases=(1, 1),
        current=1,
    ),
    "back_forward_bfcache": _contract(
        "back_forward_bfcache",
        minimum={"activeHello": 2, "challengeIssued": 2, "proofAccepted": 2, "presentAccepted": 2},
        releases=(2, 3),
        current=1,
    ),
    "app_background_foreground": _contract(
        "app_background_foreground",
        minimum={"activeHello": 1, "challengeIssued": 1, "proofAccepted": 1, "presentAccepted": 1},
        rejections=1,
        releases=(1, 1),
        current=1,
    ),
    "omnibox_focus": _contract(
        "omnibox_focus",
        releases=(0, 0),
        current=1,
    ),
    "form_focus": _contract(
        "form_focus",
        releases=(0, 0),
        current=1,
    ),
    "portrait_landscape": _contract(
        "portrait_landscape",
        releases=(0, 2),
        current=1,
    ),
    "process_restart": _contract(
        "process_restart",
        minimum={"activeHello": 1, "challengeIssued": 1, "proofAccepted": 1, "presentAccepted": 1},
        releases=(1, 1),
        current=1,
        reset=True,
    ),
    "stale_replay_token_reuse": _contract(
        "stale_replay_token_reuse",
        minimum={"staleRejected": 1, "staleReplayRejected": 1},
        rejections=1,
        releases=(0, 0),
        current=1,
    ),
    "root_window_replacement": _contract(
        "root_window_replacement",
        minimum={"activeHello": 1, "challengeIssued": 1, "proofAccepted": 1, "presentAccepted": 1},
        rejections=1,
        releases=(1, 1),
        current=1,
    ),
}


@dataclass(frozen=True)
class DumpHoldCommand:
    """Description only; the caller may pass this to the existing DUMP receiver."""

    action: str
    extras: tuple[tuple[str, str, str], ...]


_HOLD_ACTIONS = {
    "arm": "com.contentfilter.user.chromedataplane.command.ACTIVE_DOCUMENT_HOLD_ARM",
    "release": "com.contentfilter.user.chromedataplane.command.ACTIVE_DOCUMENT_HOLD_RELEASE",
    "cancel": "com.contentfilter.user.chromedataplane.command.ACTIVE_DOCUMENT_HOLD_CANCEL",
}


def dump_hold_command(
    operation: str,
    case_id: str,
    stage: str,
    nonce: str,
) -> DumpHoldCommand:
    """Return an exact one-shot hold command without executing it.

    The nonce is a runner-owned correlation capability for the DEV hold only.  It
    is never a READY token or model/presentation authority.
    """

    if operation not in _HOLD_ACTIONS:
        raise ActiveDocumentGateError(f"unsupported hold operation: {operation}")
    if case_id not in CASE_CONTRACTS:
        raise ActiveDocumentGateError(f"unsupported active-document case: {case_id}")
    if stage not in HOLD_STAGES:
        raise ActiveDocumentGateError(f"unsupported active-document hold stage: {stage}")
    if not _HOLD_NONCE.fullmatch(nonce):
        raise ActiveDocumentGateError("hold nonce must be 128-bit lowercase hex")
    return DumpHoldCommand(
        action=_HOLD_ACTIONS[operation],
        extras=(
            ("--es", "active_document_case_id", case_id),
            ("--es", "active_document_hold_stage", stage),
            ("--es", "active_document_hold_nonce", nonce),
        ),
    )


def _fields(line: str) -> dict[str, str]:
    return {match.group(1): match.group(2) for match in _KEY_VALUE.finditer(line)}


def _positive_integer(value: str) -> int | None:
    if not value.isdigit():
        return None
    parsed = int(value)
    return parsed if parsed > 0 else None


def _nonnegative_integer(value: str) -> int | None:
    return int(value) if value.isdigit() else None


def _boolean(value: str) -> bool | None:
    if value == "true":
        return True
    if value == "false":
        return False
    return None


def _digest_prefix(value: str) -> str:
    return value[:12] if _DIGEST.fullmatch(value) else ""


def _parse_metrics(fields: Mapping[str, str]) -> dict[str, int] | None:
    result: dict[str, int] = {}
    for name in METRIC_FIELDS:
        parsed = _nonnegative_integer(fields.get(name, ""))
        if parsed is None:
            return None
        result[name] = parsed
    if (
        result["releaseCurrent"] not in {0, 1}
        or result["replayCandidate"] not in {0, 1}
        or result["alphaTransitionsOutstanding"] < 0
    ):
        return None
    return result


def _signed_integer(value: str) -> int | None:
    return int(value) if value.lstrip("-").isdigit() else None


def _parse_status(fields: Mapping[str, str]) -> dict[str, Any] | None:
    metrics = _parse_metrics(fields)
    if metrics is None:
        return None
    integers = {name: _signed_integer(fields.get(name, "")) for name in STATUS_INTEGER_FIELDS}
    if any(value is None for value in integers.values()):
        return None
    pending = int(integers["pendingHandshake"])
    hold_phase = fields.get("holdPhase", "")
    if pending not in {0, 1} or hold_phase not in HOLD_PHASES:
        return None
    status: dict[str, Any] = {
        "metrics": metrics,
        "pendingHandshake": pending,
        "holdPhase": hold_phase,
    }
    for prefix in ("foreground", "attempt"):
        window_id = int(integers[f"{prefix}WindowId"])
        root = _digest_prefix(fields.get(f"{prefix}RootDigest", ""))
        if window_id >= 0:
            if not root or set(root) == {"0"}:
                return None
            binding: dict[str, Any] | None = {
                "windowId": window_id,
                "rootDigestPrefix": root,
            }
        else:
            binding = None
        status[f"{prefix}Binding"] = binding
    return status


def parse_active_document_line(line: str) -> tuple[str, dict[str, Any] | None]:
    """Parse one typed line.

    Returns ``("ignored", None)`` for unrelated logs and ``("invalid", None)``
    for malformed protocol-v2 logs.  Raw token/challenge fields are never copied.
    """

    if not any(tag in line for tag in LOG_TAGS):
        return "ignored", None
    fields = _fields(line)
    if fields.get("protocol") != PROTOCOL:
        return "ignored", None
    phase = fields.get("phase", "")
    if phase not in PHASES:
        return "invalid", None
    if phase == "active_document_status":
        status = _parse_status(fields)
        return ("status", status) if status is not None else ("invalid", None)

    case_id = fields.get("caseId", "")
    event_sequence = _positive_integer(fields.get("eventSequence", ""))
    if case_id not in CASE_CONTRACTS or event_sequence is None:
        return "invalid", None
    reason = fields.get("reason", "")
    if reason and reason not in REASONS:
        return "invalid", None
    event: dict[str, Any] = {
        "phase": phase,
        "caseId": case_id,
        "eventSequence": event_sequence,
        "reason": reason,
    }
    for source, destination in _DIGEST_FIELDS.items():
        event[destination] = _digest_prefix(fields.get(source, ""))
    for name in _POSITIVE_INTEGER_FIELDS[1:]:
        parsed = _positive_integer(fields.get(name, ""))
        event[name] = parsed if parsed is not None else 0
    window_id = fields.get("windowId", "")
    event["windowId"] = int(window_id) if window_id.lstrip("-").isdigit() else None
    event["current"] = _boolean(fields.get("current", ""))
    event["rawPresented"] = _boolean(fields.get("rawPresented", ""))
    hold_stage = fields.get("holdStage", "")
    event["holdStage"] = hold_stage if hold_stage in HOLD_STAGES else ""
    event["holdDigestPrefix"] = _digest_prefix(fields.get("holdDigest", ""))
    return "event", event


def summarize_active_document_logs(logcat: str) -> dict[str, Any]:
    events: list[dict[str, Any]] = []
    metrics: dict[str, int] | None = None
    status: dict[str, Any] | None = None
    invalid_typed_lines = 0
    current_binding: dict[str, Any] | None = None
    for line in logcat.splitlines():
        kind, value = parse_active_document_line(line)
        if kind == "invalid":
            invalid_typed_lines += 1
        elif kind == "status" and value is not None:
            status = value
            metrics = value["metrics"]
        elif kind == "event" and value is not None:
            event = value
            events.append(event)
            if event["phase"] == "active_document_released":
                current_binding = event
            elif event["phase"] in {"active_document_revoked", "active_document_invalidated"}:
                if current_binding is None or active_document_claim_key(current_binding) == active_document_claim_key(event):
                    current_binding = None
    return {
        "protocol": PROTOCOL,
        "events": events[-TIMELINE_LIMIT:],
        "eventsDropped": max(0, len(events) - TIMELINE_LIMIT),
        "invalidTypedLines": invalid_typed_lines,
        "metrics": metrics,
        "status": status,
        "currentBinding": current_binding,
    }


def metric_deltas(
    before: Mapping[str, int],
    after: Mapping[str, int],
    *,
    counter_epoch_reset: bool = False,
) -> dict[str, int]:
    missing = [name for name in METRIC_FIELDS if name not in before or name not in after]
    if missing:
        raise ActiveDocumentGateError(f"active-document metrics unavailable: {missing}")
    deltas: dict[str, int] = {}
    for name in COUNTER_FIELDS:
        previous, current = int(before[name]), int(after[name])
        if previous < 0 or current < 0:
            raise ActiveDocumentGateError(f"negative active-document metric: {name}")
        if not counter_epoch_reset and current < previous:
            raise ActiveDocumentGateError(f"active-document counter regressed: {name}")
        deltas[name] = current if counter_epoch_reset else current - previous
    before_current, after_current = int(before["releaseCurrent"]), int(after["releaseCurrent"])
    if before_current not in {0, 1} or after_current not in {0, 1}:
        raise ActiveDocumentGateError("releaseCurrent must be a zero/one gauge")
    deltas["releaseCurrent"] = after_current - before_current
    return deltas


def active_document_claim_key(event: Mapping[str, Any]) -> tuple[Any, ...]:
    return (
        event.get("sessionDigestPrefix"),
        event.get("policyEpoch"),
        event.get("navigationSequence"),
        event.get("documentSequence"),
        event.get("lifecycle"),
        event.get("tokenDigestPrefix"),
    )


def _complete_claim_key(event: Mapping[str, Any]) -> bool:
    return all(active_document_claim_key(event))


def claim_supersession(
    summary: Mapping[str, Any],
    case_id: str,
    held_claim: tuple[Any, ...],
    after_event_sequence: int,
) -> tuple[Mapping[str, Any], Mapping[str, Any]] | None:
    """Prove terminal A followed by a distinct, current cryptographic HELLO B.

    Tabs may share one native window/root. The security boundary is therefore
    the proxy-issued document claim. A is required to terminate before B is
    accepted, and any post-switch release of A rejects the supersession.
    """

    events = [
        event
        for event in summary.get("events", ())
        if isinstance(event, Mapping)
        and event.get("caseId") == case_id
        and int(event.get("eventSequence", 0)) > after_event_sequence
    ]
    if any(
        event.get("phase") == "active_document_released"
        and active_document_claim_key(event) == held_claim
        for event in events
    ):
        return None
    for hello in events:
        hello_claim = active_document_claim_key(hello)
        if (
            hello.get("phase") != "active_hello_accepted"
            or not _complete_claim_key(hello)
            or hello_claim == held_claim
            or hello_claim[0] != held_claim[0]
            or hello_claim[1] != held_claim[1]
            or hello_claim[3] == held_claim[3]
            or hello_claim[5] == held_claim[5]
        ):
            continue
        hello_sequence = int(hello.get("eventSequence", 0))
        terminals = [
            event
            for event in events
            if int(event.get("eventSequence", 0)) < hello_sequence
            and active_document_claim_key(event) == held_claim
            and event.get("phase") in {"active_document_invalidated", "active_document_revoked"}
            and event.get("reason") in {"invalidated_navigation", "handshake_transport_cancelled"}
        ]
        if terminals:
            return max(terminals, key=lambda event: int(event.get("eventSequence", 0))), hello
    return None


def _presentation_context(event: Mapping[str, Any]) -> tuple[Any, ...] | None:
    window_id = event.get("windowId")
    surface_epoch = int(event.get("surfaceEpoch", 0))
    root_digest = event.get("rootDigestPrefix")
    if (
        window_id is None
        or int(window_id) < 0
        or surface_epoch <= 0
        or not root_digest
    ):
        return None
    return (int(window_id), root_digest, surface_epoch)


def ordering_violations(events: Sequence[Mapping[str, Any]]) -> list[str]:
    """Return fail-closed protocol ordering/replay violations."""

    progress: dict[tuple[Any, ...], int] = {}
    challenges: dict[tuple[Any, ...], str] = {}
    challenge_owners: dict[str, tuple[Any, ...]] = {}
    token_owners: dict[str, tuple[Any, ...]] = {}
    presentation_contexts: dict[tuple[Any, ...], tuple[Any, ...]] = {}
    terminal: set[tuple[Any, ...]] = set()
    released: set[tuple[Any, ...]] = set()
    last_sequence = 0
    violations: list[str] = []
    accepted_rank = {
        "active_hello_accepted": 1,
        "challenge_issued": 2,
        "proof_accepted": 3,
        "present_accepted": 4,
        "active_document_released": 5,
    }
    for event in events:
        sequence = int(event.get("eventSequence", 0))
        if sequence <= last_sequence:
            violations.append("event_sequence_not_monotonic")
        last_sequence = max(last_sequence, sequence)
        phase = str(event.get("phase", ""))
        if phase.startswith("active_document_hold_"):
            continue
        key = active_document_claim_key(event)
        if phase in accepted_rank and not _complete_claim_key(event):
            violations.append("accepted_identity_incomplete")
            continue
        if phase in {"active_document_revoked", "active_document_invalidated"}:
            terminal.add(key)
            continue
        if phase not in accepted_rank:
            continue
        rank = accepted_rank[phase]
        token = str(event.get("tokenDigestPrefix", ""))
        if rank == 1:
            owner = token_owners.get(token)
            if owner is not None and owner != key:
                violations.append("token_reused_across_claims")
            token_owners[token] = key
        if key in terminal:
            violations.append("accepted_after_invalidation")
        expected = progress.get(key, 0) + 1
        if rank != expected:
            violations.append("accepted_phase_out_of_order_or_duplicate")
        else:
            progress[key] = rank
        challenge = str(event.get("challengeDigestPrefix", ""))
        if rank >= 2:
            if not challenge:
                violations.append("challenge_digest_missing")
            elif rank == 2:
                owner = challenge_owners.get(challenge)
                if owner is not None and owner != key:
                    violations.append("challenge_reused_across_claims")
                challenge_owners[challenge] = key
                challenges[key] = challenge
            elif challenges.get(key) != challenge:
                violations.append("challenge_digest_changed")
        if rank >= 2:
            context = _presentation_context(event)
            if context is None:
                violations.append("presentation_context_incomplete")
            elif rank == 2:
                presentation_contexts[key] = context
            elif presentation_contexts.get(key) != context:
                violations.append("presentation_context_changed")
            if event.get("current") is not True:
                violations.append("accepted_context_not_current")
            if event.get("rawPresented") is not False:
                violations.append("accepted_raw_presentation_unknown_or_true")
        if phase == "active_document_released":
            if key in released:
                violations.append("release_replayed")
            released.add(key)
            if event.get("current") is not True:
                violations.append("release_not_current")
            if event.get("rawPresented") is not False:
                violations.append("release_raw_presentation_unknown_or_true")
    return sorted(set(violations))


def verify_active_document_case(
    case_id: str,
    summary: Mapping[str, Any],
    baseline_metrics: Mapping[str, int],
    *,
    expected_current_document_sequence: int | None = None,
    forbidden_release_document_sequences: Iterable[int] = (),
    expected_current_claim: tuple[Any, ...] | None = None,
    forbidden_release_claims: Iterable[tuple[Any, ...]] = (),
    counter_epoch_reset: bool | None = None,
) -> dict[str, Any]:
    """Verify one of the sixteen physical case traces.

    ``expected_current_document_sequence`` and forbidden sequences come from the
    runner's observed A/B navigation map.  This is intentional: an app-side
    ``windowId`` alone cannot prove which stock-Chrome tab is foreground.
    """

    contract = CASE_CONTRACTS.get(case_id)
    if contract is None:
        raise ActiveDocumentGateError(f"unsupported active-document case: {case_id}")
    if int(summary.get("invalidTypedLines", 0)) != 0:
        raise ActiveDocumentGateError("malformed typed active-document evidence")
    if int(summary.get("eventsDropped", 0)) != 0:
        raise ActiveDocumentGateError("active-document evidence timeline truncated")
    after_metrics = summary.get("metrics")
    if not isinstance(after_metrics, Mapping):
        raise ActiveDocumentGateError("active-document terminal metrics absent")
    terminal_status = summary.get("status")
    if not isinstance(terminal_status, Mapping):
        raise ActiveDocumentGateError("active-document terminal structural status absent")
    reset = contract.allows_counter_epoch_reset if counter_epoch_reset is None else counter_epoch_reset
    deltas = metric_deltas(baseline_metrics, after_metrics, counter_epoch_reset=reset)
    if int(after_metrics["crossTabRelease"]) != 0 or deltas["crossTabRelease"] != 0:
        raise ActiveDocumentGateError("cross-tab presentation release observed")
    if deltas["rejectedTransparentCommits"] != 0:
        raise ActiveDocumentGateError("a rejected transparent transaction reached platform commit")
    if deltas["opaqueRestoreFailures"] != 0 or deltas["alphaSubmitFailures"] != 0:
        raise ActiveDocumentGateError("protected-surface alpha transaction failed")
    if int(after_metrics["alphaTransitionsOutstanding"]) != 0:
        raise ActiveDocumentGateError("protected-surface alpha transaction remained outstanding")
    if int(terminal_status.get("pendingHandshake", -1)) != 0:
        raise ActiveDocumentGateError("active-document handshake remained pending")
    if terminal_status.get("holdPhase") != "idle":
        raise ActiveDocumentGateError("active-document diagnostic hold remained active")
    if case_id == "stale_replay_token_reuse" and int(after_metrics["replayCandidate"]) != 0:
        raise ActiveDocumentGateError("consumed replay candidate remained available")
    for name, minimum in contract.minimum_delta_map.items():
        if deltas[name] < minimum:
            raise ActiveDocumentGateError(f"{case_id} requires {name}>={minimum}; observed {deltas[name]}")
    for name, maximum in contract.maximum_delta_map.items():
        if deltas[name] > maximum:
            raise ActiveDocumentGateError(f"{case_id} requires {name}<={maximum}; observed {deltas[name]}")
    if deltas["backgroundRejected"] + deltas["staleRejected"] < contract.minimum_rejections:
        raise ActiveDocumentGateError(f"{case_id} did not reject the invalidated/background generation")

    events = [event for event in summary.get("events", ()) if event.get("caseId") == case_id]
    violations = ordering_violations(events)
    if violations:
        raise ActiveDocumentGateError(f"active-document ordering violation: {violations}")
    releases = [event for event in events if event.get("phase") == "active_document_released"]
    rejection_phases = {
        "active_hello_rejected",
        "proof_rejected",
        "present_rejected",
        "active_document_revoked",
        "active_document_invalidated",
    }
    case_rejections = sum(event.get("phase") in rejection_phases for event in events)
    if case_rejections < contract.minimum_rejections:
        raise ActiveDocumentGateError(f"{case_id} lacks a typed rejection/invalidation event")
    if not contract.minimum_releases <= len(releases) <= contract.maximum_releases:
        raise ActiveDocumentGateError(
            f"{case_id} release count {len(releases)} outside "
            f"{contract.minimum_releases}..{contract.maximum_releases}"
        )
    forbidden = set(forbidden_release_document_sequences)
    escaped = sorted(
        int(event.get("documentSequence", 0))
        for event in releases
        if int(event.get("documentSequence", 0)) in forbidden
    )
    if escaped:
        raise ActiveDocumentGateError(f"release belonged to a non-foreground document: {escaped}")
    forbidden_claims = set(forbidden_release_claims)
    if any(active_document_claim_key(event) in forbidden_claims for event in releases):
        raise ActiveDocumentGateError("release belonged to a superseded cryptographic claim")

    current_binding = summary.get("currentBinding")
    if contract.final_release_current is not None:
        if int(after_metrics["releaseCurrent"]) != contract.final_release_current:
            raise ActiveDocumentGateError(
                f"{case_id} requires releaseCurrent={contract.final_release_current}"
            )
        if contract.final_release_current == 1 and not isinstance(current_binding, Mapping):
            raise ActiveDocumentGateError("releaseCurrent=1 lacks an exact current binding")
        if contract.final_release_current == 0 and current_binding is not None:
            raise ActiveDocumentGateError("releaseCurrent=0 retained a current binding")
    if contract.final_release_current == 1 and expected_current_document_sequence is None:
        raise ActiveDocumentGateError("runner foreground document sequence is required for a released surface")
    if expected_current_document_sequence is not None:
        if not isinstance(current_binding, Mapping) or int(current_binding.get("documentSequence", 0)) != int(
            expected_current_document_sequence
        ):
            raise ActiveDocumentGateError("current binding does not match the runner-observed foreground document")
    if expected_current_claim is not None:
        if not isinstance(current_binding, Mapping) or active_document_claim_key(current_binding) != expected_current_claim:
            raise ActiveDocumentGateError("current binding does not match the runner-observed foreground claim")

    return {
        "pass": True,
        "caseId": case_id,
        "protocol": PROTOCOL,
        "eventCount": len(events),
        "releaseCount": len(releases),
        "deltas": deltas,
        "crossTabRelease": 0,
        "orderingViolations": [],
        "terminal": {
            "releaseCurrent": int(after_metrics["releaseCurrent"]),
            "alphaTransitionsOutstanding": int(after_metrics["alphaTransitionsOutstanding"]),
            "pendingHandshake": int(terminal_status["pendingHandshake"]),
            "holdPhase": str(terminal_status["holdPhase"]),
            "replayCandidate": int(after_metrics["replayCandidate"]),
        },
        "currentDocumentSequence": (
            int(current_binding.get("documentSequence", 0)) if isinstance(current_binding, Mapping) else 0
        ),
    }
