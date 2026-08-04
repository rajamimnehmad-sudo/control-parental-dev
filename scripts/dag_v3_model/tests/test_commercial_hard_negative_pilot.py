from __future__ import annotations

import unittest

from scripts.dag_v3_model.commercial_hard_negative_pilot import (
    current_and_relevant,
    hamming_distance,
)


class CommercialHardNegativePilotTest(unittest.TestCase):
    def test_rejects_modern_query_returning_historical_title(self) -> None:
        self.assertFalse(
            current_and_relevant(
                {
                    "title": "File:Advertising postcard, circa 1918.jpg",
                    "query": "2025 store advertising people",
                }
            )
        )

    def test_requires_current_primary_title_evidence(self) -> None:
        self.assertFalse(
            current_and_relevant(
                {
                    "title": "File:Store interior without date.jpg",
                    "query": "2025 retail advertising people",
                }
            )
        )
        self.assertTrue(
            current_and_relevant(
                {
                    "title": "File:Retail display 2025.jpg",
                    "query": "fashion store",
                }
            )
        )

    def test_hamming_distance_is_python_39_compatible(self) -> None:
        self.assertEqual(0, hamming_distance(0x1234, 0x1234))
        self.assertEqual(4, hamming_distance(0b1111, 0))


if __name__ == "__main__":
    unittest.main()
