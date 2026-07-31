from __future__ import annotations

import json
import tempfile
import threading
import unittest
from pathlib import Path
from urllib.error import HTTPError
from urllib.request import Request, urlopen

import numpy as np
from PIL import Image

from tools.gloshia_lab.corpus import (
    _assign_splits,
    _current_content_evidence,
    _dhash,
    _has_old_primary_date,
    _near_duplicate,
    _phash,
    repair_currentness,
    _source_cluster,
)
from tools.gloshia_lab.model import (
    PADDING_COLOR,
    TARGET_SIZE,
    _fit_plan,
    _uncertain_quadrants,
    action_from_scores,
)
from tools.gloshia_lab.metrics import evaluation_report
from tools.gloshia_lab.server import ReviewServer


class ModelPolicyTest(unittest.TestCase):
    def test_reference_policy_is_pinned_to_android_constants(self) -> None:
        root = Path(__file__).resolve().parents[3]
        analyzer = (
            root
            / "app-dag-browser/src/main/java/com/contentfilter/dagbrowser/"
            "DagOnDeviceImageAnalyzer.kt"
        ).read_text(encoding="utf-8")
        preprocessor = (
            root
            / "app-dag-browser/src/main/java/com/contentfilter/dagbrowser/"
            "DagImagePreprocessor.kt"
        ).read_text(encoding="utf-8")
        for expected in (
            "FilterThreshold = 0.4f",
            "UncertainRegionalReviewFloor = 0.3f",
            "UncertainRegionalFilterThreshold = 0.45f",
            "RegionalFilterThreshold = 0.5f",
            "RegionalStrongFilterThreshold = 0.7f",
            "RegionalConsensusMinimum = 2",
        ):
            self.assertIn(expected, analyzer)
        self.assertIn("private const val CropFraction = 0.56", preprocessor)
        self.assertIn("private const val CropFraction = 0.42", preprocessor)

    def test_fit_plan_matches_android_letterbox_examples(self) -> None:
        self.assertEqual((224, 112, 0, 56), _fit_plan(400, 200))
        self.assertEqual((56, 224, 84, 0), _fit_plan(100, 400))

    def test_uncertain_quadrants_are_four_valid_model_inputs(self) -> None:
        source = np.arange(TARGET_SIZE * TARGET_SIZE * 3, dtype=np.uint8).reshape(
            TARGET_SIZE,
            TARGET_SIZE,
            3,
        )
        views = _uncertain_quadrants(source)
        self.assertEqual(4, len(views))
        self.assertTrue(all(view.shape == source.shape for view in views))
        self.assertTrue(np.array_equal(source[0, 0], views[0][0, 0]))
        self.assertTrue(np.array_equal(source[99, 99], views[3][0, 0]))

    def test_dag_36_threshold_policy(self) -> None:
        self.assertEqual("allow", action_from_scores(0.299, [0.9]))
        self.assertEqual("filter", action_from_scores(0.4))
        self.assertEqual("filter", action_from_scores(0.336, [0.45]))
        self.assertEqual("allow", action_from_scores(0.336, [0.44, 0.2, 0.1, 0.2]))
        self.assertEqual(
            "allow",
            action_from_scores(0.2, [0.51, 0.2, 0.1], panoramic=True),
        )
        self.assertEqual(
            "filter",
            action_from_scores(0.2, [0.51, 0.52], panoramic=True),
        )


class CorpusTest(unittest.TestCase):
    def test_current_content_rejects_recent_upload_of_historical_photo(self) -> None:
        self.assertIsNone(
            _current_content_evidence(
                "File:Fashion show 1961.jpg",
                "A historic runway",
                "1961 fashion shows",
                None,
            )
        )
        self.assertIsNone(
            _current_content_evidence(
                "File:Archive scan.jpg",
                "Employee event 1963",
                "Photographic archives",
                "30 December 2024 (upload date)",
            )
        )
        self.assertIsNone(
            _current_content_evidence(
                "File:FI0003601.jpg",
                "Leather jackets, 1991",
                "Clothing in 1991",
                "2025-01-30",
            )
        )
        self.assertIsNone(
            _current_content_evidence(
                "File:Recent category, old capture.jpg",
                None,
                "Images uploaded in 2024",
                "2013-04-05",
            )
        )

    def test_current_content_accepts_capture_or_semantic_current_year(self) -> None:
        self.assertEqual(
            _current_content_evidence(
                "File:Event portrait.jpg",
                None,
                None,
                "2025-05-16 19:57",
            ),
            "current_capture_date",
        )
        self.assertEqual(
            _current_content_evidence(
                "File:Event portrait 2024.jpg",
                None,
                None,
                None,
            ),
            "current_year_in_title_description_or_categories",
        )

    def test_completed_row_rejects_old_title_or_capture_year(self) -> None:
        self.assertTrue(
            _has_old_primary_date(
                {"title": "File:Business group circa 1890s.jpg", "source_date": "2025"}
            )
        )
        self.assertTrue(
            _has_old_primary_date(
                {"title": "File:Undated event.jpg", "source_date": "2013-04-05"}
            )
        )
        self.assertFalse(
            _has_old_primary_date(
                {"title": "File:Fashion event 2025.jpg", "source_date": "2025-04-05"}
            )
        )

    def test_source_cluster_ignores_trailing_series_variants(self) -> None:
        self.assertEqual(
            _source_cluster(
                "File:Bodysuits and hosiery for a wedding and its night - model choice.jpg"
            ),
            _source_cluster(
                "File:Bodysuits and hosiery for a wedding and its night - models choice.jpg"
            ),
        )

    def test_query_plan_targets_exactly_one_thousand(self) -> None:
        plan = json.loads(
            (
                Path(__file__).resolve().parents[1] / "queries.json"
            ).read_text(encoding="utf-8")
        )
        self.assertEqual(
            1000,
            sum(category["target"] for category in plan["categories"].values()),
        )

    def test_split_is_deterministic_and_keeps_twenty_percent_sealed(self) -> None:
        rows = [
            {
                "sample_id": f"sample:{index}",
                "category": "boundary_current",
                "sha256": f"{index:064x}",
            }
            for index in range(100)
        ]
        _assign_splits(rows)
        counts = {
            split: sum(row["split"] == split for row in rows)
            for split in ("main_eval", "difficult", "final_sealed")
        }
        self.assertEqual(
            {"main_eval": 60, "difficult": 20, "final_sealed": 20},
            counts,
        )
        self.assertTrue(
            all(row["sealed"] == (row["split"] == "final_sealed") for row in rows)
        )

    def test_split_keeps_a_source_series_together(self) -> None:
        rows = [
            {
                "sample_id": f"series:{index}",
                "category": "boundary_current",
                "source_cluster": "same-event",
                "sha256": f"{index:064x}",
            }
            for index in range(8)
        ] + [
            {
                "sample_id": f"other:{index}",
                "category": "boundary_current",
                "source_cluster": f"other-event-{index}",
                "sha256": f"{index + 8:064x}",
            }
            for index in range(32)
        ]

        _assign_splits(rows)

        self.assertEqual(
            1,
            len({row["split"] for row in rows if row["source_cluster"] == "same-event"}),
        )

    def test_identical_image_is_a_near_duplicate(self) -> None:
        image = Image.new("RGB", (320, 240), PADDING_COLOR)
        dhash = _dhash(image)
        phash = _phash(image)
        self.assertTrue(_near_duplicate(dhash, phash, [dhash], [phash]))

    def test_currentness_repair_is_recoverable_and_keeps_valid_rows(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            corpus = Path(temporary)
            images = corpus / "images"
            sheets = corpus / "contact-sheets"
            images.mkdir()
            sheets.mkdir()
            rows = [
                {
                    "sample_id": "sample:old",
                    "title": "File:Portrait from the 1890s.jpg",
                    "source_date": "2025",
                    "local_path": "images/old.jpg",
                },
                {
                    "sample_id": "sample:current",
                    "title": "File:Fashion event 2025.jpg",
                    "source_date": "2025-05-16",
                    "local_path": "images/current.jpg",
                },
            ]
            (corpus / "manifest.jsonl").write_text(
                "".join(json.dumps(row) + "\n" for row in rows),
                encoding="utf-8",
            )
            for name in ("summary.json", "evaluation-report.json"):
                (corpus / name).write_text("{}\n", encoding="utf-8")
            (corpus / "predictions.jsonl").write_text("{}\n", encoding="utf-8")
            (images / "old.jpg").write_bytes(b"old")
            (images / "current.jpg").write_bytes(b"current")
            (sheets / "sheet.jpg").write_bytes(b"sheet")

            result = repair_currentness(corpus)

            self.assertEqual(1, result["repaired"])
            self.assertEqual(1, result["remaining"])
            backup = Path(result["backup"])
            self.assertTrue((backup / "manifest.jsonl").exists())
            self.assertTrue((backup / "invalid-images" / "old.jpg").exists())
            self.assertTrue((backup / "contact-sheets" / "sheet.jpg").exists())
            self.assertFalse((corpus / "manifest.jsonl").exists())
            self.assertTrue((corpus / "images" / "current.jpg").exists())
            partial = [
                json.loads(line)
                for line in (corpus / "manifest.partial.jsonl")
                .read_text(encoding="utf-8")
                .splitlines()
            ]
            self.assertEqual(["sample:current"], [row["sample_id"] for row in partial])


class MetricsTest(unittest.TestCase):
    def test_report_excludes_sealed_and_computes_human_matrix(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            corpus = Path(temporary)
            manifest = [
                {
                    "sample_id": "sample:allow",
                    "category": "safe_hard",
                    "split": "main_eval",
                },
                {
                    "sample_id": "sample:miss",
                    "category": "boundary_current",
                    "split": "difficult",
                },
                {
                    "sample_id": "sample:sealed",
                    "category": "sensitive_control",
                    "split": "final_sealed",
                },
            ]
            predictions = [
                {
                    "sample_id": "sample:allow",
                    "action": "allow",
                    "elapsed_ms": 10.0,
                },
                {
                    "sample_id": "sample:miss",
                    "action": "allow",
                    "elapsed_ms": 20.0,
                },
                {
                    "sample_id": "sample:sealed",
                    "action": "filter",
                    "elapsed_ms": 30.0,
                },
            ]
            (corpus / "manifest.jsonl").write_text(
                "".join(json.dumps(row) + "\n" for row in manifest),
                encoding="utf-8",
            )
            (corpus / "predictions.jsonl").write_text(
                "".join(json.dumps(row) + "\n" for row in predictions),
                encoding="utf-8",
            )
            (corpus / "reviews.json").write_text(
                json.dumps(
                    {
                        "reviews": {
                            "sample:allow": {"action": "allow"},
                            "sample:miss": {"action": "filter"},
                            "sample:sealed": {"action": "filter"},
                        }
                    }
                ),
                encoding="utf-8",
            )

            report = evaluation_report(corpus)

            self.assertEqual(2, report["corpus_rows"])
            self.assertEqual(2, report["reviewed_reference"])
            self.assertEqual(1, report["confusion_matrix"]["allow_as_allow"])
            self.assertEqual(1, report["confusion_matrix"]["filter_as_allow"])
            self.assertEqual(0.0, report["filter_recall"])
            self.assertEqual(1.0, report["allow_recall"])


class ReviewServerTest(unittest.TestCase):
    def test_unreviewed_items_do_not_reveal_model_and_review_reveals_it(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            corpus = Path(temporary)
            (corpus / "images").mkdir()
            (corpus / "images" / "sample.jpg").write_bytes(b"image")
            manifest = {
                "sample_id": "sample:one",
                "category": "boundary_current",
                "split": "main_eval",
                "local_path": "images/sample.jpg",
                "sha256": "1" * 64,
            }
            prediction = {
                "sample_id": "sample:one",
                "action": "filter",
                "maximum_probability": 0.61,
                "elapsed_ms": 12.0,
            }
            (corpus / "manifest.jsonl").write_text(
                json.dumps(manifest) + "\n",
                encoding="utf-8",
            )
            (corpus / "predictions.jsonl").write_text(
                json.dumps(prediction) + "\n",
                encoding="utf-8",
            )
            web = Path(__file__).resolve().parents[1] / "web"
            server = ReviewServer(("127.0.0.1", 0), corpus, web, False)
            thread = threading.Thread(target=server.serve_forever, daemon=True)
            thread.start()
            base = f"http://127.0.0.1:{server.server_port}"
            try:
                before = json.loads(
                    urlopen(f"{base}/api/items?scope=queue", timeout=2).read()
                )["items"][0]
                self.assertIsNone(before["model_prediction"])
                self.assertIsNone(before["category"])
                self.assertIsNone(before["split"])

                rejected_request = Request(
                    f"{base}/api/review",
                    data=json.dumps(
                        {
                            "sample_id": "sample:one",
                            "action": "allow",
                            "reasons": [],
                        }
                    ).encode(),
                    headers={
                        "Content-Type": "application/json",
                        "Origin": "https://untrusted.invalid",
                    },
                    method="POST",
                )
                with self.assertRaises(HTTPError) as rejected:
                    urlopen(rejected_request, timeout=2)
                self.assertEqual(403, rejected.exception.code)

                request = Request(
                    f"{base}/api/review",
                    data=json.dumps(
                        {
                            "sample_id": "sample:one",
                            "action": "filter",
                            "reasons": ["shoulder_or_armpit"],
                        }
                    ).encode(),
                    headers={
                        "Content-Type": "application/json",
                        "Origin": base,
                    },
                    method="POST",
                )
                saved = json.loads(urlopen(request, timeout=2).read())
                self.assertEqual("filter", saved["model_prediction"]["action"])
                self.assertTrue(saved["matched_model"])

                after = json.loads(
                    urlopen(f"{base}/api/items?scope=all", timeout=2).read()
                )["items"][0]
                self.assertEqual("filter", after["model_prediction"]["action"])
                self.assertEqual("boundary_current", after["category"])
                self.assertEqual("main_eval", after["split"])
                status = json.loads(urlopen(f"{base}/api/status", timeout=2).read())
                self.assertEqual(1, status["reviewed_total"])
                self.assertEqual(0, status["queue_remaining"])

                undo_request = Request(
                    f"{base}/api/review",
                    data=json.dumps({"sample_id": "sample:one"}).encode(),
                    headers={
                        "Content-Type": "application/json",
                        "Origin": base,
                    },
                    method="DELETE",
                )
                undone = json.loads(urlopen(undo_request, timeout=2).read())
                self.assertTrue(undone["removed"])
                restored = json.loads(
                    urlopen(f"{base}/api/items?scope=all", timeout=2).read()
                )["items"][0]
                self.assertIsNone(restored["human_decision"])
                self.assertIsNone(restored["model_prediction"])
            finally:
                server.shutdown()
                server.server_close()
                thread.join(timeout=2)


if __name__ == "__main__":
    unittest.main()
