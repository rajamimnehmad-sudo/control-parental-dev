from __future__ import annotations

import unittest

from scripts.dag_v3_model.r3_focused_relabel_prepare import (
    build_queue,
    page_id_batches,
    parse_wikimedia_pages,
)


class R3FocusedRelabelPrepareTest(unittest.TestCase):
    def test_page_ids_are_unique_sorted_and_bounded(self) -> None:
        self.assertEqual(page_id_batches([3, 1, 3, 2], size=2), [[1, 2], [3]])
        with self.assertRaises(ValueError):
            page_id_batches([1], size=51)

    def test_wikimedia_parser_rejects_untrusted_asset_host(self) -> None:
        payload = {
            "query": {
                "pages": [
                    {
                        "pageid": 1,
                        "title": "File:one.jpg",
                        "imageinfo": [
                            {
                                "mime": "image/jpeg",
                                "thumburl": "https://evil.example/one.jpg",
                                "descriptionurl": "https://commons.wikimedia.org/wiki/File:one.jpg",
                            }
                        ],
                    },
                    {
                        "pageid": 2,
                        "title": "File:two.jpg",
                        "imageinfo": [
                            {
                                "mime": "image/jpeg",
                                "thumburl": "https://upload.wikimedia.org/two.jpg",
                                "descriptionurl": "https://commons.wikimedia.org/wiki/File:two.jpg",
                            }
                        ],
                    },
                ]
            }
        }
        self.assertEqual(set(parse_wikimedia_pages(payload)), {2})

    def test_queue_reviews_only_unknown_signals_from_filtered_rows(self) -> None:
        audit = {
            "records": [
                {
                    "sample_id": "wikimedia:1",
                    "policy_action": "filter",
                    "labels": {"shoulder": "positive", "knee": "unknown"},
                    "training_authorization": "owner_authorized_private_experiment",
                },
                {
                    "sample_id": "wikimedia:2",
                    "policy_action": "allow",
                    "labels": {"shoulder": "negative", "knee": "negative"},
                    "training_authorization": "owner_authorized_private_experiment",
                },
            ]
        }
        queue = build_queue(
            audit,
            {"wikimedia:1": {"image_path": "/tmp/one.jpg", "source_url": None}},
        )
        self.assertEqual(queue["summary"], {"filtered_records": 1, "resolved": 1, "unresolved": 0})
        self.assertEqual(queue["queue"][0]["existing_positive_signals"], ["shoulder"])
        self.assertEqual(queue["queue"][0]["signals_to_review"], ["knee"])


if __name__ == "__main__":
    unittest.main()
