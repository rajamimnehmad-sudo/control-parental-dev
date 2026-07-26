import hashlib
import importlib.util
import inspect
import io
import json
import os
import tempfile
import unittest
import urllib.error
from pathlib import Path
from unittest import mock

MODULE_PATH = Path(__file__).resolve().parents[1] / "dag_v2_benchmark.py"
SPEC = importlib.util.spec_from_file_location("dag_v2_benchmark", MODULE_PATH)
benchmark = importlib.util.module_from_spec(SPEC)
assert SPEC.loader
SPEC.loader.exec_module(benchmark)


class BenchmarkContractTest(unittest.TestCase):
    def test_locked_hashes_and_licenses_are_complete(self):
        lock = benchmark.read_json(benchmark.LOCK_PATH)
        for model in lock["models"]:
            self.assertRegex(model["sha256"], r"^[0-9a-f]{64}$")
            self.assertGreater(model["size_bytes"], 0)
            self.assertTrue(model["weights_license"])
            self.assertTrue(model["commercial_use"])

    def test_atomic_write_rejects_mismatch_and_cleans_partial(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "model.bin"
            with self.assertRaises(ValueError):
                benchmark.atomic_verified_write(path, "0" * 64, 3, [b"abc"])
            self.assertFalse(path.exists())
            self.assertFalse(path.with_suffix(".bin.partial").exists())

    def test_atomic_write_respects_locked_size(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "model.bin"
            digest = hashlib.sha256(b"abc").hexdigest()
            benchmark.atomic_verified_write(path, digest, 3, [b"a", b"bc"])
            self.assertEqual(path.read_bytes(), b"abc")

    def test_corpus_download_retries_transient_rate_limit(self):
        class Response(io.BytesIO):
            def __enter__(self):
                return self

            def __exit__(self, *_):
                self.close()

            def geturl(self):
                return "https://example.test/image.jpg"

        with tempfile.TemporaryDirectory() as directory:
            destination = Path(directory) / "image.jpg"
            rate_limit = urllib.error.HTTPError(
                "https://example.test/image.jpg",
                429,
                "rate limited",
                {},
                None,
            )
            with mock.patch.object(
                benchmark.urllib.request,
                "urlopen",
                side_effect=[rate_limit, Response(b"jpeg")],
            ):
                with mock.patch.object(benchmark.time, "sleep"):
                    digest, size = benchmark.fetch_corpus_image(
                        "https://example.test/image.jpg",
                        destination,
                        32,
                    )
            self.assertEqual(digest, hashlib.sha256(b"jpeg").hexdigest())
            self.assertEqual(size, 4)
            self.assertEqual(destination.read_bytes(), b"jpeg")

    def test_cache_path_rejects_traversal(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaises(ValueError):
                benchmark.cache_path(Path(directory), "models", "../escape")

    def test_dhash_is_deterministic(self):
        from PIL import Image

        image = Image.new("RGB", (20, 20), (10, 20, 30))
        self.assertEqual(benchmark.dhash64(image), benchmark.dhash64(image))
        self.assertRegex(benchmark.dhash64(image), r"^[0-9a-f]{16}$")

    def test_license_allowlist_is_closed(self):
        allowed = ["cc0", "public domain", "cc by", "cc-by"]
        self.assertTrue(benchmark.license_allowed("CC BY-SA 4.0", allowed))
        self.assertFalse(benchmark.license_allowed("Fair use", allowed))
        self.assertFalse(benchmark.license_allowed("", allowed))

    def test_simulation_does_not_emit_product_allow_decision(self):
        records = [{
            "adult_score": 0.1,
            "person_count": 1,
            "latency": {"adult_ms": 1.0, "pose_ms": 2.0, "segment_ms": 3.0},
        }]
        result = benchmark.simulate(records)
        self.assertNotIn("decision", json.dumps(result).lower())
        self.assertIsNone(result["known_false_allows"])

    def test_resume_identity_is_sample_id(self):
        first = {"sample_id": "a", "adult_score": 0.1}
        second = {"sample_id": "b", "adult_score": 0.2}
        resumed = {item["sample_id"]: item for item in (first, second)}
        self.assertEqual(set(resumed), {"a", "b"})

    def test_manifest_rejects_duplicate_sha_across_groups(self):
        with tempfile.TemporaryDirectory() as directory:
            cache = Path(directory)
            manifest = cache / "corpus/manifest.jsonl"
            manifest.parent.mkdir(parents=True)
            rows = [
                {"sample_id": "a", "sha256": "1" * 64},
                {"sample_id": "b", "sha256": "1" * 64},
            ]
            manifest.write_text("\n".join(json.dumps(row) for row in rows) + "\n", encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "duplicate image"):
                benchmark.load_manifest(cache)

    def test_concurrency_and_inference_batch_are_bounded(self):
        self.assertEqual(benchmark.INFERENCE_BATCH_SIZE, 1)
        self.assertGreaterEqual(benchmark.MAX_DOWNLOAD_WORKERS, 1)
        self.assertLessEqual(benchmark.MAX_DOWNLOAD_WORKERS, 4)

    def test_evidence_contract_has_no_url_or_private_request_fields(self):
        source = inspect.getsource(benchmark.process_sample).lower()
        forbidden_keys = [
            '"url"',
            '"cookies"',
            '"headers"',
            '"query"',
            '"referer"',
        ]
        for key in forbidden_keys:
            self.assertNotIn(key, source)

    def test_android_runner_is_not_in_product_gradle_and_has_no_release_variant(self):
        root_settings = (benchmark.ROOT / "settings.gradle.kts").read_text(encoding="utf-8")
        runner_build = (
            benchmark.TOOL_DIR / "android-runner/app/build.gradle.kts"
        ).read_text(encoding="utf-8")
        self.assertNotIn("android-runner", root_settings)
        self.assertIn('withBuildType("release")', runner_build)
        self.assertIn("variant.enable = false", runner_build)
        self.assertIn("validateBenchmarkCache", runner_build)
        self.assertIn("dependsOn(validateBenchmarkCache)", runner_build)

    def test_android_export_is_bounded_and_round_robin(self):
        with tempfile.TemporaryDirectory() as directory:
            cache = Path(directory)
            corpus = cache / "corpus"
            corpus.mkdir()
            rows = []
            for index in range(60):
                name = f"sample-{index}.jpg"
                (corpus / name).write_bytes(str(index).encode("ascii"))
                rows.append(
                    {
                        "sample_id": f"sample-{index}",
                        "sha256": hashlib.sha256(str(index).encode("ascii")).hexdigest(),
                        "relative_file": name,
                        "category": "a" if index < 30 else "b",
                    }
                )
            (corpus / "manifest.jsonl").write_text(
                "\n".join(json.dumps(row) for row in rows) + "\n",
                encoding="utf-8",
            )
            results = cache / "results"
            results.mkdir()
            (results / "evidence.jsonl").write_text(
                "\n".join(
                    json.dumps(
                        {
                            "sample_id": row["sample_id"],
                            "adult_score": 0.25,
                            "person_count": 1,
                        }
                    )
                    for row in rows
                )
                + "\n",
                encoding="utf-8",
            )
            benchmark.export_android(cache, 50)
            exported = json.loads((cache / "android-subset/manifest.json").read_text())
            categories = [item["category"] for item in exported["samples"]]
            self.assertEqual(len(categories), 50)
            self.assertEqual(categories.count("a"), 25)
            self.assertEqual(categories.count("b"), 25)

    def test_cleanup_removes_only_partial_files(self):
        with tempfile.TemporaryDirectory() as directory:
            cache = Path(directory)
            partial = cache / "x.partial"
            retained = cache / "x.jsonl"
            partial.write_bytes(b"private")
            retained.write_text("{}\n", encoding="utf-8")
            benchmark.cleanup(cache)
            self.assertFalse(partial.exists())
            self.assertTrue(retained.exists())

    def test_environment_cache_is_outside_repository_by_default(self):
        self.assertFalse(
            benchmark.DEFAULT_CACHE == benchmark.ROOT
            or benchmark.ROOT in benchmark.DEFAULT_CACHE.parents
        )


if __name__ == "__main__":
    unittest.main()
