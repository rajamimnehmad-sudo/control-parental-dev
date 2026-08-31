import unittest

from h19_active_document_gates import (
    CASE_CONTRACTS,
    CASE_IDS,
    HOLD_STAGES,
    METRIC_FIELDS,
    ActiveDocumentGateError,
    active_document_claim_key,
    claim_supersession,
    dump_hold_command,
    metric_deltas,
    ordering_violations,
    parse_active_document_line,
    summarize_active_document_logs,
    verify_active_document_case,
)


SESSION = "a" * 64
TOKEN = "b" * 64
CHALLENGE = "c" * 64
ROOT = "d" * 64


def metrics(**overrides):
    value = {name: 0 for name in METRIC_FIELDS}
    value.update(overrides)
    return value


def event_line(phase, case_id="cold_foreground_release", sequence=1, **overrides):
    fields = {
        "protocol": "active_document_v3",
        "phase": phase,
        "caseId": case_id,
        "eventSequence": str(sequence),
        "sessionDigest": SESSION,
        "policyEpoch": "19",
        "navigationSequence": "7",
        "documentSequence": "11",
        "lifecycle": "3",
        "tokenDigest": TOKEN,
        "challengeDigest": CHALLENGE,
        "windowId": "17",
        "rootDigest": ROOT,
        "surfaceEpoch": "23",
        "current": "true",
        "rawPresented": "false",
    }
    fields.update({name: str(value) for name, value in overrides.items()})
    payload = " ".join(f"{name}={value}" for name, value in fields.items())
    return f"I ChromeMediaShieldReady: {payload}"


def status_line(
    *,
    foreground_window=17,
    foreground_root=ROOT,
    attempt_window=17,
    attempt_root=ROOT,
    pending_handshake=0,
    hold_phase="idle",
    **values,
):
    current = metrics(**values)
    payload = " ".join(f"{name}={current[name]}" for name in METRIC_FIELDS)
    structural = (
        f"foregroundWindowId={foreground_window} foregroundRootDigest={foreground_root} "
        f"attemptWindowId={attempt_window} attemptRootDigest={attempt_root} "
        f"pendingHandshake={pending_handshake} holdPhase={hold_phase}"
    )
    return (
        "I ChromeMediaShieldReady: protocol=active_document_v3 phase=active_document_status "
        f"{payload} {structural}"
    )


def accepted_trace(case_id, start=1, document=11):
    return [
        event_line("active_hello_accepted", case_id, start, documentSequence=document, challengeDigest=""),
        event_line("challenge_issued", case_id, start + 1, documentSequence=document),
        event_line("proof_accepted", case_id, start + 2, documentSequence=document),
        event_line("present_accepted", case_id, start + 3, documentSequence=document),
        event_line("active_document_released", case_id, start + 4, documentSequence=document),
    ]


class ActiveDocumentGateTest(unittest.TestCase):
    def test_claim_supersession_accepts_same_native_root_only_after_terminal_a(self):
        a = event_line("active_hello_accepted", "switch_during_hello", 1, challengeDigest="")
        terminal = event_line(
            "active_document_invalidated",
            "switch_during_hello",
            2,
            reason="invalidated_navigation",
        )
        b = event_line(
            "active_hello_accepted",
            "switch_during_hello",
            3,
            navigationSequence=12,
            documentSequence=12,
            tokenDigest="e" * 64,
            challengeDigest="",
        )
        summary = summarize_active_document_logs("\n".join((a, terminal, b)))
        held_claim = active_document_claim_key(summary["events"][0])

        pair = claim_supersession(summary, "switch_during_hello", held_claim, 1)

        self.assertIsNotNone(pair)
        self.assertEqual("active_document_invalidated", pair[0]["phase"])
        self.assertNotEqual(held_claim, active_document_claim_key(pair[1]))
        self.assertEqual(pair[0]["rootDigestPrefix"], pair[1]["rootDigestPrefix"])

    def test_claim_supersession_rejects_missing_terminal_wrong_claim_or_late_a_release(self):
        a = event_line("active_hello_accepted", "switch_during_hello", 1, challengeDigest="")
        terminal = event_line(
            "active_document_invalidated",
            "switch_during_hello",
            2,
            reason="invalidated_navigation",
        )
        b = event_line(
            "active_hello_accepted",
            "switch_during_hello",
            3,
            navigationSequence=12,
            documentSequence=12,
            tokenDigest="e" * 64,
            challengeDigest="",
        )
        release_a = event_line("active_document_released", "switch_during_hello", 4)
        base = summarize_active_document_logs(a)
        held_claim = active_document_claim_key(base["events"][0])

        for lines in ((a, b), (a, terminal, a), (a, terminal, b, release_a)):
            with self.subTest(lines=len(lines)):
                summary = summarize_active_document_logs("\n".join(lines))
                self.assertIsNone(claim_supersession(summary, "switch_during_hello", held_claim, 1))

    def test_exact_sixteen_case_ids_have_contracts(self):
        self.assertEqual(16, len(CASE_IDS))
        self.assertEqual(set(CASE_IDS), set(CASE_CONTRACTS))

    def test_typed_parser_redacts_capabilities_and_ignores_unrelated_logs(self):
        kind, parsed = parse_active_document_line(
            event_line("challenge_issued") + " token=RAW_SECRET challenge=RAW_CHALLENGE url=https://private.test/path"
        )

        self.assertEqual("event", kind)
        self.assertEqual(SESSION[:12], parsed["sessionDigestPrefix"])
        self.assertEqual(TOKEN[:12], parsed["tokenDigestPrefix"])
        self.assertEqual(CHALLENGE[:12], parsed["challengeDigestPrefix"])
        serialized = repr(parsed)
        self.assertNotIn("RAW_SECRET", serialized)
        self.assertNotIn("private.test", serialized)
        self.assertEqual(("ignored", None), parse_active_document_line("I OtherTag: phase=challenge_issued"))

    def test_malformed_protocol_line_is_explicitly_invalid(self):
        kind, parsed = parse_active_document_line(
            "I ChromeMediaShieldReady: protocol=active_document_v3 phase=challenge_issued caseId=unknown eventSequence=1"
        )

        self.assertEqual("invalid", kind)
        self.assertIsNone(parsed)

    def test_status_parser_requires_and_redacts_structural_terminal_state(self):
        summary = summarize_active_document_logs(status_line(releaseCurrent=1))

        self.assertEqual(17, summary["status"]["foregroundBinding"]["windowId"])
        self.assertEqual(ROOT[:12], summary["status"]["foregroundBinding"]["rootDigestPrefix"])
        self.assertEqual(ROOT[:12], summary["status"]["attemptBinding"]["rootDigestPrefix"])
        self.assertEqual(0, summary["status"]["pendingHandshake"])
        self.assertEqual("idle", summary["status"]["holdPhase"])
        self.assertNotIn(ROOT, repr(summary))

        malformed = status_line().replace(" pendingHandshake=0", "")
        invalid = summarize_active_document_logs(malformed)
        self.assertEqual(1, invalid["invalidTypedLines"])
        self.assertIsNone(invalid["status"])

    def test_happy_trace_is_ordered_current_and_one_shot(self):
        summary = summarize_active_document_logs(
            "\n".join(
                accepted_trace("cold_foreground_release")
                + [status_line(activeHello=1, challengeIssued=1, proofAccepted=1, presentAccepted=1, releaseCurrent=1)]
            )
        )

        result = verify_active_document_case(
            "cold_foreground_release",
            summary,
            metrics(),
            expected_current_document_sequence=11,
        )

        self.assertTrue(result["pass"])
        self.assertEqual(1, result["releaseCount"])
        self.assertEqual([], ordering_violations(summary["events"]))

    def test_out_of_order_duplicate_and_replayed_release_fail_closed(self):
        out_of_order = [
            event_line("active_hello_accepted", sequence=1, challengeDigest=""),
            event_line("proof_accepted", sequence=2),
        ]
        self.assertIn("accepted_phase_out_of_order_or_duplicate", ordering_violations(_events(out_of_order)))

        duplicate = accepted_trace("cold_foreground_release") + [
            event_line("active_document_released", sequence=6)
        ]
        violations = ordering_violations(_events(duplicate))
        self.assertIn("release_replayed", violations)
        self.assertIn("accepted_phase_out_of_order_or_duplicate", violations)

    def test_release_must_be_current_opaque_bound_and_raw_false(self):
        for field, value, reason in (
            ("current", "false", "release_not_current"),
            ("rawPresented", "true", "release_raw_presentation_unknown_or_true"),
            ("rootDigest", "", "presentation_context_incomplete"),
            ("surfaceEpoch", "0", "presentation_context_incomplete"),
        ):
            lines = accepted_trace("cold_foreground_release")
            lines[-1] = event_line("active_document_released", sequence=5, **{field: value})
            with self.subTest(field=field):
                self.assertIn(reason, ordering_violations(_events(lines)))

    def test_challenge_token_and_presentation_context_are_generation_bound(self):
        token_reuse = accepted_trace("cold_foreground_release", document=11) + accepted_trace(
            "cold_foreground_release", start=6, document=12
        )
        self.assertIn("token_reused_across_claims", ordering_violations(_events(token_reuse)))

        challenge_reuse = accepted_trace("cold_foreground_release", document=11)
        challenge_reuse += [
            event_line(
                "active_hello_accepted",
                sequence=6,
                documentSequence=12,
                tokenDigest="e" * 64,
                challengeDigest="",
            ),
            event_line(
                "challenge_issued",
                sequence=7,
                documentSequence=12,
                tokenDigest="e" * 64,
            ),
        ]
        self.assertIn("challenge_reused_across_claims", ordering_violations(_events(challenge_reuse)))

        context_change = accepted_trace("cold_foreground_release")
        context_change[2] = event_line("proof_accepted", sequence=3, rootDigest="f" * 64)
        self.assertIn("presentation_context_changed", ordering_violations(_events(context_change)))

    def test_invalidation_makes_late_acceptance_inert(self):
        lines = [
            event_line("active_hello_accepted", sequence=1, challengeDigest=""),
            event_line("challenge_issued", sequence=2),
            event_line("active_document_invalidated", sequence=3, reason="invalidated_hidden"),
            event_line("proof_accepted", sequence=4),
        ]

        self.assertIn("accepted_after_invalidation", ordering_violations(_events(lines)))

    def test_cross_tab_release_is_terminal_even_when_trace_itself_is_ordered(self):
        summary = summarize_active_document_logs(
            "\n".join(
                accepted_trace("cold_foreground_release")
                + [
                    status_line(
                        activeHello=1,
                        challengeIssued=1,
                        proofAccepted=1,
                        presentAccepted=1,
                        crossTabRelease=1,
                        releaseCurrent=1,
                    )
                ]
            )
        )

        with self.assertRaisesRegex(ActiveDocumentGateError, "cross-tab"):
            verify_active_document_case("cold_foreground_release", summary, metrics())

    def test_runner_foreground_map_rejects_release_from_tab_b(self):
        summary = summarize_active_document_logs(
            "\n".join(
                accepted_trace("foreground_a_background_b", document=22)
                + [
                    event_line(
                        "active_hello_rejected",
                        "foreground_a_background_b",
                        6,
                        reason="hello_foreground_ambiguous",
                        documentSequence=33,
                    ),
                    status_line(
                        activeHello=1,
                        challengeIssued=1,
                        proofAccepted=1,
                        presentAccepted=1,
                        backgroundRejected=1,
                        releaseCurrent=1,
                    ),
                ]
            )
        )

        with self.assertRaisesRegex(ActiveDocumentGateError, "non-foreground"):
            verify_active_document_case(
                "foreground_a_background_b",
                summary,
                metrics(),
                forbidden_release_document_sequences={22},
            )

    def test_switch_at_each_protocol_boundary_requires_rejection_and_no_release(self):
        cases = {
            "switch_during_hello": [
                event_line("active_hello_accepted", "switch_during_hello", 1, challengeDigest=""),
                event_line(
                    "active_hello_rejected",
                    "switch_during_hello",
                    2,
                    reason="hello_context_stale",
                ),
            ],
            "switch_during_challenge": [
                event_line("active_hello_accepted", "switch_during_challenge", 1, challengeDigest=""),
                event_line("challenge_issued", "switch_during_challenge", 2),
                event_line(
                    "proof_rejected",
                    "switch_during_challenge",
                    3,
                    reason="prove_context_changed",
                ),
            ],
            "switch_during_prove_present": [
                event_line("active_hello_accepted", "switch_during_prove_present", 1, challengeDigest=""),
                event_line("challenge_issued", "switch_during_prove_present", 2),
                event_line("proof_accepted", "switch_during_prove_present", 3),
                event_line(
                    "present_rejected",
                    "switch_during_prove_present",
                    4,
                    reason="present_postcommit_context_changed",
                ),
            ],
        }
        for case_id, lines in cases.items():
            contract = CASE_CONTRACTS[case_id]
            after = metrics(**contract.minimum_delta_map, staleRejected=1, releaseCurrent=0)
            summary = summarize_active_document_logs("\n".join(lines + [_status_from_metrics(after)]))
            with self.subTest(case=case_id):
                self.assertTrue(verify_active_document_case(case_id, summary, metrics())["pass"])

    def test_background_and_stale_replay_contracts_reject_without_new_release(self):
        background = summarize_active_document_logs(
            "\n".join(
                [
                    event_line(
                        "active_hello_rejected",
                        "background_tab_no_release",
                        1,
                        reason="hello_foreground_ambiguous",
                    ),
                    status_line(backgroundRejected=1),
                ]
            )
        )
        self.assertTrue(verify_active_document_case("background_tab_no_release", background, metrics())["pass"])

        baseline = metrics(releaseCurrent=1, replayCandidate=1)
        replay = summarize_active_document_logs(
            "\n".join(
                accepted_trace("cold_foreground_release")
                + [
                    event_line(
                        "present_rejected",
                        "stale_replay_token_reuse",
                        6,
                        reason="present_replay",
                    ),
                    status_line(
                        staleRejected=1,
                        staleReplayRejected=1,
                        replayCandidate=0,
                        releaseCurrent=1,
                    ),
                ]
            )
        )
        self.assertTrue(
            verify_active_document_case(
                "stale_replay_token_reuse",
                replay,
                baseline,
                expected_current_document_sequence=11,
            )["pass"]
        )

    def test_released_surface_requires_runner_foreground_identity(self):
        summary = summarize_active_document_logs(
            "\n".join(
                accepted_trace("cold_foreground_release")
                + [status_line(activeHello=1, challengeIssued=1, proofAccepted=1, presentAccepted=1, releaseCurrent=1)]
            )
        )

        with self.assertRaisesRegex(ActiveDocumentGateError, "runner foreground"):
            verify_active_document_case("cold_foreground_release", summary, metrics())

    def test_terminal_status_rejects_pending_handshake_or_active_hold(self):
        trace = accepted_trace("cold_foreground_release")
        terminal_metrics = dict(activeHello=1, challengeIssued=1, proofAccepted=1, presentAccepted=1, releaseCurrent=1)
        for status, message in (
            (status_line(pending_handshake=1, **terminal_metrics), "handshake remained pending"),
            (status_line(hold_phase="reached", **terminal_metrics), "hold remained active"),
        ):
            with self.subTest(message=message):
                summary = summarize_active_document_logs("\n".join(trace + [status]))
                with self.assertRaisesRegex(ActiveDocumentGateError, message):
                    verify_active_document_case(
                        "cold_foreground_release",
                        summary,
                        metrics(),
                        expected_current_document_sequence=11,
                    )

    def test_metrics_are_monotonic_except_explicit_process_epoch_reset(self):
        before = metrics(activeHello=9, challengeIssued=8, releaseCurrent=1)
        after = metrics(activeHello=10, challengeIssued=9, releaseCurrent=0)
        deltas = metric_deltas(before, after)
        self.assertEqual(1, deltas["activeHello"])
        self.assertEqual(-1, deltas["releaseCurrent"])

        with self.assertRaisesRegex(ActiveDocumentGateError, "regressed"):
            metric_deltas(before, metrics(activeHello=1))
        reset = metric_deltas(before, metrics(activeHello=1), counter_epoch_reset=True)
        self.assertEqual(1, reset["activeHello"])

    def test_current_binding_is_cleared_by_matching_revoke(self):
        lines = accepted_trace("cold_foreground_release")
        lines.append(
            event_line(
                "active_document_revoked",
                "cold_foreground_release",
                6,
                reason="invalidated_navigation",
            )
        )
        summary = summarize_active_document_logs("\n".join(lines + [status_line()]))

        self.assertIsNone(summary["currentBinding"])

    def test_timeline_is_bounded_and_no_raw_line_is_retained(self):
        lines = [
            event_line(
                "active_hello_rejected",
                "background_tab_no_release",
                sequence,
                reason="hello_foreground_ambiguous",
            )
            for sequence in range(1, 300)
        ]
        summary = summarize_active_document_logs("\n".join(lines))

        self.assertEqual(256, len(summary["events"]))
        self.assertEqual(43, summary["eventsDropped"])
        self.assertNotIn("https://", repr(summary))

    def test_dump_hold_commands_are_allowlisted_exact_and_never_execute(self):
        nonce = "f" * 32
        for operation in ("arm", "release", "cancel"):
            command = dump_hold_command(
                operation,
                "switch_during_prove_present",
                "present_precommit",
                nonce,
            )
            self.assertTrue(command.action.endswith(operation.upper()))
            self.assertIn(("--es", "active_document_hold_nonce", nonce), command.extras)

        for operation, case_id, stage, invalid_nonce in (
            ("retry", "switch_during_hello", "hello_accepted", nonce),
            ("arm", "unknown", "hello_accepted", nonce),
            ("arm", "switch_during_hello", "sleep", nonce),
            ("arm", "switch_during_hello", "hello_accepted", "short"),
        ):
            with self.subTest(operation=operation, case=case_id, stage=stage):
                with self.assertRaises(ActiveDocumentGateError):
                    dump_hold_command(operation, case_id, stage, invalid_nonce)

    def test_every_hold_stage_is_representable(self):
        for stage in HOLD_STAGES:
            command = dump_hold_command("arm", "cold_foreground_release", stage, "e" * 32)
            self.assertIn(("--es", "active_document_hold_stage", stage), command.extras)


def _events(lines):
    return summarize_active_document_logs("\n".join(lines))["events"]


def _status_from_metrics(value):
    return status_line(**value)


if __name__ == "__main__":
    unittest.main()
