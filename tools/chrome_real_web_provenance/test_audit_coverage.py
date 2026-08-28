#!/usr/bin/env python3

import unittest

from audit_coverage import summarize, validate


class AuditCoverageTest(unittest.TestCase):
    def test_count_and_visible_area_bounds_remain_conservative(self) -> None:
        items = [
            visible("a", "AUTHORITATIVE_PRE_RENDER", 100),
            visible("b", "DEFINITE_NON_INTERCEPTABLE", 300),
            visible("c", "ATTRIBUTION_UNKNOWN", 600),
        ]
        result = summarize(items)

        self.assertEqual(3, result["totalVisibleStaticMedia"])
        self.assertEqual(0.333333, result["coverageLowerBound"])
        self.assertEqual(0.666667, result["coverageUpperBound"])
        self.assertEqual(0.1, result["visibleAreaLowerBound"])
        self.assertEqual(0.7, result["visibleAreaUpperBound"])

    def test_authoritative_claim_requires_exact_identity(self) -> None:
        manifest = {"states": [{"id": "s1", "visible": [visible("a", "AUTHORITATIVE_PRE_RENDER", 1)]}]}

        with self.assertRaisesRegex(ValueError, "lacks exact identity"):
            validate(manifest)

    def test_video_can_be_excluded_from_static_media_denominator(self) -> None:
        item = visible("video", "ATTRIBUTION_UNKNOWN", 500)
        item["denominator"] = False

        self.assertEqual(0, summarize([item])["totalVisibleStaticMedia"])


def visible(instance_id: str, coverage: str, area: int) -> dict[str, object]:
    return {
        "id": instance_id,
        "coverage": coverage,
        "reason": "test",
        "visibleAreaPx": area,
    }


if __name__ == "__main__":
    unittest.main()
