import importlib.util
import json
import pathlib
import tempfile
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[3]
TOOL_DIR = ROOT / "tools/dag-v2-benchmark"
SPEC = importlib.util.spec_from_file_location(
    "dag_v2_policy_eval",
    TOOL_DIR / "dag_v2_policy_eval.py",
)
POLICY = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(POLICY)


class PolicyEvaluationContractTest(unittest.TestCase):
    def test_review_plan_is_deterministic_blind_and_complete(self):
        POLICY.verify_review_plan()
        order = POLICY.read_jsonl(POLICY.REVIEW_ORDER_LOCK)
        self.assertEqual(203, len(order))
        self.assertEqual(list(range(1, 204)), [item["position"] for item in order])
        for item in order:
            self.assertEqual(
                {"position", "sample_id", "sha256", "relative_file", "diagnostic"},
                set(item),
            )

    def test_split_has_no_exact_or_perceptual_leakage(self):
        rows = POLICY.read_jsonl(POLICY.SPLIT_LOCK)
        by_sha = {}
        by_cluster = {}
        for item in rows:
            by_sha.setdefault(item["sha256"], set()).add(item["split"])
            by_cluster.setdefault(item["cluster_id"], set()).add(item["split"])
        self.assertTrue(all(len(values) == 1 for values in by_sha.values()))
        self.assertTrue(all(len(values) == 1 for values in by_cluster.values()))
        self.assertEqual(POLICY.SPLIT_TARGETS, dict(__import__("collections").Counter(
            item["split"] for item in rows
        )))

    def test_diagnostic_subset_has_five_per_reason_and_unique_clusters(self):
        rows = POLICY.read_jsonl(POLICY.DIAGNOSTIC_LOCK)
        counts = __import__("collections").Counter(item["diagnostic_stratum"] for item in rows)
        self.assertEqual({reason: 5 for reason in POLICY.REASONS}, dict(counts))
        self.assertEqual(60, len({item["cluster_id"] for item in rows}))

    def test_fixture_assets_are_generated_without_network_or_corpus(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = pathlib.Path(temporary) / "assets"
            POLICY.prepare_reviewer_assets(pathlib.Path("/does/not/exist"), output, fixture=True)
            manifest = POLICY.read_json(output / "manifest.json")
            self.assertEqual(3, manifest["sample_count"])
            self.assertTrue(manifest["fixture"])
            for item in manifest["samples"]:
                image = output / "images" / item["file"]
                self.assertEqual(item["sha256"], POLICY.sha256_file(image)[0])

    def test_label_export_schema_rejects_private_or_model_fields(self):
        order = POLICY.read_jsonl(POLICY.REVIEW_ORDER_LOCK)
        valid = {
            "sample_id": order[0]["sample_id"],
            "decision": "unsure",
            "reasons": [],
            "review_number": 1,
            "reviewed_at": "2026-07-26T12:00:00Z",
            "policy_version": POLICY.POLICY_VERSION,
            "reviewer_version": POLICY.REVIEWER_VERSION,
        }
        with tempfile.TemporaryDirectory() as temporary:
            path = pathlib.Path(temporary) / "labels.jsonl"
            path.write_text(json.dumps(valid) + "\n", encoding="utf-8")
            labels = POLICY.validate_label_export(path, require_complete=False)
            self.assertEqual("unsure", labels[0]["decision"])
            for forbidden in ("url", "model", "cookie", "author"):
                invalid = dict(valid)
                invalid[forbidden] = "forbidden"
                path.write_text(json.dumps(invalid) + "\n", encoding="utf-8")
                with self.assertRaises(ValueError):
                    POLICY.validate_label_export(path, require_complete=False)

    def test_legacy_reasons_are_normalized_without_ambiguity(self):
        order = POLICY.read_jsonl(POLICY.REVIEW_ORDER_LOCK)
        legacy = {
            "sample_id": order[0]["sample_id"],
            "decision": "hide",
            "reasons": "[abdomen, knee]",
            "review_number": 1,
            "reviewed_at": "2026-07-26T12:00:00Z",
            "policy_version": POLICY.POLICY_VERSION,
            "reviewer_version": POLICY.REVIEWER_VERSION,
        }
        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary)
            source = root / "legacy.jsonl"
            output = root / "normalized.jsonl"
            source.write_text(json.dumps(legacy) + "\n", encoding="utf-8")
            labels = POLICY.validate_label_export(source, require_complete=False)
            self.assertEqual(["abdomen", "knee"], labels[0]["reasons"])
            with self.assertRaises(ValueError):
                POLICY.normalize_label_export(source, output)

            remaining = []
            for item in order[1:]:
                remaining.append(
                    {
                        **legacy,
                        "sample_id": item["sample_id"],
                        "decision": "show",
                        "reasons": "[]",
                    }
                )
            POLICY.write_jsonl(source, [legacy, *remaining])
            POLICY.normalize_label_export(source, output)
            normalized = POLICY.read_jsonl(output)
            self.assertEqual(["abdomen", "knee"], normalized[0]["reasons"])
            self.assertEqual([], normalized[1]["reasons"])
            self.assertNotEqual(POLICY.sha256_file(source)[0], POLICY.sha256_file(output)[0])

    def test_legacy_reasons_reject_unknown_or_ambiguous_values(self):
        for value in ("abdomen", "[abdomen,,knee]", "[unknown_reason]", "[abdomen, abdomen]"):
            with self.subTest(value=value), self.assertRaises(ValueError):
                POLICY._normalize_export_reasons(value)

    def test_versioned_results_bundle_recomputes(self):
        POLICY.verify_04b_results()

    def test_heavy_teacher_is_hard_limited_below_ten_percent(self):
        with self.assertRaises(ValueError):
            POLICY.compare_segmentation_teacher(
                pathlib.Path("/unused"),
                pathlib.Path("/unused"),
                pathlib.Path("/unused"),
                21,
            )
        self.assertLessEqual(20 * 100.0 / 203.0, 10.0)

    def test_policy_selection_excludes_unsure_and_test_opens_once(self):
        order = POLICY.read_jsonl(POLICY.REVIEW_ORDER_LOCK)
        split = {item["sample_id"]: item for item in POLICY.read_jsonl(POLICY.SPLIT_LOCK)}
        labels = []
        signals = []
        unsure_count = 0
        for index, item in enumerate(order):
            decision = "unsure" if index % 11 == 0 else ("hide" if index % 2 == 0 else "show")
            unsure_count += decision == "unsure"
            labels.append(
                {
                    "sample_id": item["sample_id"],
                    "decision": decision,
                    "reasons": ["knee"] if decision == "hide" else [],
                    "review_number": 1,
                    "reviewed_at": "2026-07-26T12:00:00Z",
                    "policy_version": POLICY.POLICY_VERSION,
                    "reviewer_version": POLICY.REVIEWER_VERSION,
                }
            )
            hidden = 1.0 if decision == "hide" else 0.0
            record = {
                "sample_id": item["sample_id"],
                "latency_ms": {
                    "adult": 1.0,
                    "pose": 1.0,
                    "local_signals": 0.2,
                    "policy": 0.01,
                    "sequential": 2.2,
                    "adult_pose_parallel": 1.2,
                },
            }
            for feature in POLICY.SIGNAL_FEATURES:
                record[feature] = hidden
            signals.append(record)
        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary)
            labels_path = root / "labels.jsonl"
            signals_path = root / "signals.jsonl"
            seal_path = root / "seal.json"
            test_path = root / "test.json"
            POLICY.write_jsonl(labels_path, labels)
            POLICY.write_jsonl(signals_path, signals)
            POLICY.select_policy(labels_path, signals_path, seal_path)
            seal = POLICY.read_json(seal_path)
            self.assertEqual(unsure_count, seal["unsure_excluded"])
            self.assertFalse(seal["test_opened"])
            self.assertIn(
                seal["selected"]["name"],
                {
                    "deterministic_rules",
                    "logistic_regression",
                    "small_tree_depth_3",
                    "bounded_stump_boost",
                },
            )
            self.assertLessEqual(len(seal["selected"]["model"].get("stumps", [])), 12)
            POLICY.open_frozen_test(labels_path, signals_path, seal_path, test_path)
            result = POLICY.read_json(test_path)
            self.assertTrue(result["opened_once"])
            self.assertFalse(result["production_claim"])
            with self.assertRaises(ValueError):
                POLICY.open_frozen_test(labels_path, signals_path, seal_path, test_path)

    def test_no_product_integration_or_tracked_binary_images(self):
        product = ROOT / "feature-dag2"
        policy_text = (TOOL_DIR / "dag_v2_policy_eval.py").read_text(encoding="utf-8")
        self.assertNotIn("feature-dag2/src", policy_text)
        provider = next(product.rglob("DagV2ImagePipeline.kt"))
        self.assertIn("Hide", provider.read_text(encoding="utf-8"))
        binary_suffixes = {".jpg", ".jpeg", ".png", ".webp", ".onnx", ".tflite", ".task", ".apk"}
        tracked_tool_files = __import__("subprocess").check_output(
            ["git", "ls-files", "tools/dag-v2-benchmark"],
            cwd=ROOT,
            text=True,
        ).splitlines()
        self.assertFalse(
            [path for path in tracked_tool_files if pathlib.Path(path).suffix.lower() in binary_suffixes]
        )
        source_binaries = [
            path
            for path in TOOL_DIR.rglob("*")
            if path.is_file()
            and path.suffix.lower() in binary_suffixes
            and "build" not in path.parts
            and ".gradle" not in path.parts
        ]
        self.assertFalse(source_binaries)


if __name__ == "__main__":
    unittest.main()
