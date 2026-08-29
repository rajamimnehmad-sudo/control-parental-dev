#!/usr/bin/env python3

from __future__ import annotations

import hashlib
import tempfile
import unittest
from pathlib import Path

from h19_evidence import (
    analyze_image,
    counter_deltas,
    latest_status,
    latest_structured_status,
    parse_fixture_report,
    status_counter_snapshot,
    summarize_accessibility_xml,
    summarize_logcat,
)
from h19_plan import HarnessError, validate_plan


class H19EvidenceTest(unittest.TestCase):
    def test_ready_marker_evidence_comes_from_the_bound_accessibility_service(self) -> None:
        line = (
            "I ChromeMediaShieldReady: phase=ready_foreground_released windowId=17 surfaceEpoch=8 "
            "documentSequence=3 lifecycle=4 token=abcdef012345 axBound=true reason= rawPresented=false"
        )

        summary = summarize_logcat(line)

        self.assertEqual(
            {
                "package": "com.android.chrome",
                "windowId": "17",
                "documentSequence": "3",
                "lifecycle": "4",
                "tokenDigestPrefix": "abcdef012345",
                "axBound": True,
            },
            summary["readyMarkers"][0],
        )
        self.assertNotIn("glosh-shield-ready", str(summary))

    def test_structured_status_reconstructs_untruncated_security_counters(self) -> None:
        logcat = "\n".join(
            [
                "I ChromeMediaShieldStatus: v=1 seq=7 kind=network mode=replace_all candidates=6 replaced=6 rawDelivered=0 safeRaw=0 blockedReplaced=0 unknownReplaced=0 unsupportedReplaced=0 rawBlocked=0 rawUnknown=0 cacheHit=0 decisionEngine=6",
                "I ChromeMediaShieldStatus: v=1 seq=7 kind=document transformed=1 failClosed=0 outstanding=0 issued=1 claims=1 documentSequence=1 navigationSequence=1 readyRequests=1 readyPreflights=0 readyAccepted=1 readyRejected=0",
                "I ChromeMediaShieldStatus: v=1 seq=7 kind=health failures=0 proxyQueueRejects=0 protectFailure=0 quicAttempts=0 directTcpAttempts=0 active=true ready=true",
                "I ChromeMediaShieldStatus: v=1 seq=7 kind=fixture report=PASS",
            ]
        )

        status = latest_structured_status(logcat)
        summary = summarize_logcat(logcat)

        self.assertTrue(status["complete"])
        self.assertEqual("0", status["fields"]["networkVisualRawBlockedDelivered"])
        self.assertTrue(summary["counterInvariants"]["allAvailable"])
        self.assertTrue(all(summary["counterInvariants"]["zero"].values()))

    def test_latest_structured_status_uses_latest_record_after_sequence_reset(self) -> None:
        def record(sequence: int, candidates: int) -> list[str]:
            return [
                f"I ChromeMediaShieldStatus: v=1 seq={sequence} kind=network mode=replace_all candidates={candidates} replaced={candidates} rawDelivered=0 safeRaw=0 blockedReplaced=0 unknownReplaced=0 unsupportedReplaced=0 rawBlocked=0 rawUnknown=0 cacheHit=0 decisionEngine={candidates} engineCalls={candidates}",
                f"I ChromeMediaShieldStatus: v=1 seq={sequence} kind=document transformed=1 failClosed=0 outstanding=0 issued=1",
                f"I ChromeMediaShieldStatus: v=1 seq={sequence} kind=health failures=0 proxyQueueRejects=0 protectFailure=0 quicAttempts=0 directTcpAttempts=0",
                f"I ChromeMediaShieldStatus: v=1 seq={sequence} kind=fixture report=not_run",
            ]

        status = latest_structured_status("\n".join(record(9, 9) + record(1, 2)))

        self.assertEqual(1, status["sequence"])
        self.assertEqual("2", status["fields"]["networkVisualCandidates"])

    def test_fixture_report_requires_every_subdocument_sink_blocked(self) -> None:
        frame = ",".join(
            f"{name}=BLOCKED"
            for name in sorted(
                {
                    "frame-data-img",
                    "frame-blob-img",
                    "frame-canvas",
                    "frame-service-worker",
                    "frame-closed-shadow",
                }
            )
        )
        value = (
            "REPORT=local-data-img=BLOCKED,normal-css=SAFE,out-of-scope-css-synthesis=OUT_OF_SCOPE_VISIBLE,"
            "DOCUMENTS=1,FRAMES=1,SCRIPTS=1,STYLES=1,WORKERS=1,SERVICE_WORKERS=0,"
            f"FRAME_REPORTS=1,FRAME_REPORT_REJECTS=0,FRAME_REPORT={frame},"
            f"FRAME_REPORT_SHA={'a' * 64},FRAME_CHALLENGE_SHA={'b' * 64},"
            "SAME_URL_BODIES=2,REPORTS=1"
        )

        parsed = parse_fixture_report(value)

        self.assertTrue(parsed["pass"])
        self.assertEqual(5, len(parsed["frameOutcomes"]))

        escaped = parse_fixture_report(value.replace("frame-canvas=BLOCKED", "frame-canvas=ESCAPED"))
        errored = parse_fixture_report(value.replace("normal-css=SAFE", "normal-css=ERROR"))
        self.assertFalse(escaped["pass"])
        self.assertIn("frame_report_not_all_blocked", escaped["reasons"])
        self.assertFalse(errored["pass"])
        self.assertIn("main_report_contains_error", errored["reasons"])

    def test_counter_snapshots_produce_current_vs_previous_deltas(self) -> None:
        previous = {"networkVisualInference": 3, "networkVisualCacheHit": 2, "proxyQueueRejects": 0}
        summary = {
            "status": {
                "fields": {
                    "networkVisualInference": "5",
                    "networkVisualCacheHit": "7",
                    "proxyQueueRejects": "0",
                }
            }
        }

        current = status_counter_snapshot(summary)
        delta = counter_deltas(previous, current)

        self.assertEqual(2, delta["networkVisualInference"])
        self.assertEqual(5, delta["networkVisualCacheHit"])
        self.assertEqual(0, delta["proxyQueueRejects"])

    def test_audit_placeholder_and_sentinel_are_reported_separately(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            audit = Path(directory) / "audit.png"
            sentinel = Path(directory) / "sentinel.png"
            striped(audit, (55, 65, 81), (0, 200, 255))
            striped(sentinel, (0, 0, 0), (220, 20, 48))

            audit_result = analyze_image(audit, sample_scale=1)
            sentinel_result = analyze_image(sentinel, sample_scale=1)

            self.assertTrue(audit_result["auditPlaceholderObserved"])
            self.assertFalse(audit_result["controlledSentinelLikeObserved"])
            self.assertTrue(sentinel_result["controlledSentinelLikeObserved"])
            self.assertFalse(sentinel_result["auditPlaceholderObserved"])

    def test_safe_and_blocked_fixture_palettes_are_aggregate_only(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            safe = Path(directory) / "safe.png"
            blocked = Path(directory) / "blocked.png"
            safe_pixels = [(70, 155, 210)] * (320 * 180)
            fill_rect(safe_pixels, 320, 0, 110, 319, 179, (37, 120, 64))
            fill_rect(safe_pixels, 320, 230, 25, 310, 105, (235, 210, 96))
            write_ppm(safe, 320, 180, safe_pixels)
            write_ppm(blocked, 320, 180, [(92, 100, 108)] * (320 * 180))

            self.assertTrue(analyze_image(safe, 1)["safeFixturePaletteObserved"])
            self.assertTrue(analyze_image(blocked, 1)["blockedPlaceholderObserved"])

    def test_status_truncation_is_explicit_and_transport_is_independent(self) -> None:
        complete = (
            "I ChromePhotosDataPlane: phase=status lifecycle=PresentationReady active=true "
            "networkVisualRawBlockedDelivered=0 audit17Dropped=0\n"
            "I VpnTransport09A: status=active ownedFdResources=0 transportRuntime=ready\n"
        )
        truncated = "I ChromePhotosDataPlane: phase=status lifecycle=ProxyReady active=true h19=REPORT=x"

        self.assertTrue(latest_status(complete)["complete"])
        self.assertFalse(latest_status(truncated)["complete"])
        summary = summarize_logcat(complete)
        self.assertEqual("0", summary["transportStatus"]["ownedFdResources"])
        self.assertFalse(summary["counterInvariants"]["allAvailable"])

    def test_accessibility_summary_hashes_token_and_discards_other_text(self) -> None:
        token = "this-token-must-never-be-written-to-evidence"
        xml = (
            "UI hierchary dumped to: /dev/tty\n<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>"
            "<hierarchy><node package='com.android.chrome' text='private page text' "
            f"content-desc='glosh-shield-ready:{token}:42' bounds='[0,0][1,1]' /></hierarchy>\nDone"
        )

        summary = summarize_accessibility_xml(xml)

        self.assertTrue(summary["parsed"])
        self.assertEqual(1, len(summary["readyMarkers"]))
        self.assertEqual(hashlib.sha256(token.encode()).hexdigest(), summary["readyMarkers"][0]["tokenSha256"])
        self.assertNotIn("private page text", str(summary))
        self.assertNotIn(token, str(summary))

    def test_accessibility_summary_only_records_whitelisted_policy_names(self) -> None:
        xml = (
            "<?xml version='1.0'?><hierarchy>"
            "<node package='com.android.chrome' text='ForceGoogleSafeSearch private-value' "
            "content-desc='URLBlocklist' bounds='[0,0][1,1]' />"
            "</hierarchy>"
        )

        summary = summarize_accessibility_xml(xml)

        self.assertEqual(["ForceGoogleSafeSearch", "URLBlocklist"], summary["managedPolicyNamesObserved"])
        self.assertNotIn("private-value", str(summary))

    def test_plan_is_bounded_and_rejects_non_web_navigation(self) -> None:
        plan = {
            "schema": "glosh-h19-a23-plan-v1",
            "phases": [
                {
                    "id": "replace",
                    "mode": "replace-all",
                    "states": [
                        {
                            "id": "controlled",
                            "url": "https://glosh-photos.test/web19/controlled",
                            "recordSeconds": 20,
                        }
                    ],
                }
            ],
        }
        self.assertIs(plan, validate_plan(plan))
        plan["phases"][0]["states"][0]["url"] = "data:image/png;base64,AAAA"
        with self.assertRaises(HarnessError):
            validate_plan(plan)


def striped(path: Path, first: tuple[int, int, int], second: tuple[int, int, int]) -> None:
    pixels = [first] * (320 * 180)
    for index in range(8):
        if index % 2 == 0:
            fill_rect(pixels, 320, index * 40, 0, (index + 1) * 40 - 1, 179, second)
    write_ppm(path, 320, 180, pixels)


def fill_rect(
    pixels: list[tuple[int, int, int]],
    width: int,
    left: int,
    top: int,
    right: int,
    bottom: int,
    color: tuple[int, int, int],
) -> None:
    for y in range(top, bottom + 1):
        for x in range(left, right + 1):
            pixels[y * width + x] = color


def write_ppm(path: Path, width: int, height: int, pixels: list[tuple[int, int, int]]) -> None:
    body = bytes(channel for pixel in pixels for channel in pixel)
    path.write_bytes(f"P6\n{width} {height}\n255\n".encode() + body)


if __name__ == "__main__":
    unittest.main()
