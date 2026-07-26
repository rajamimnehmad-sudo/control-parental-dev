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


def create_android_asset_fixture(root: Path):
    cache = root / "cache"
    model_dir = cache / "models"
    image_dir = cache / "android-subset"
    model_dir.mkdir(parents=True)
    image_dir.mkdir(parents=True)
    models = []
    for index in range(3):
        content = f"model-{index}".encode("ascii")
        filename = f"model-{index}.bin"
        (model_dir / filename).write_bytes(content)
        models.append(
            {
                "id": f"model-{index}",
                "filename": filename,
                "selected_for_benchmark": True,
                "sha256": hashlib.sha256(content).hexdigest(),
                "size_bytes": len(content),
            }
        )
    samples = []
    for index in range(50):
        content = f"image-{index}".encode("ascii")
        filename = f"image-{index}.jpg"
        (image_dir / filename).write_bytes(content)
        samples.append(
            {
                "sample_id": f"sample-{index}",
                "file": filename,
                "sha256": hashlib.sha256(content).hexdigest(),
                "size_bytes": len(content),
            }
        )
    model_lock_path = root / "models.lock.json"
    subset_lock_path = root / "android-subset.lock.json"
    benchmark.write_json(model_lock_path, {"models": models})
    subset = {"manifest_version": 1, "sample_count": len(samples), "samples": samples}
    benchmark.write_json(subset_lock_path, subset)
    benchmark.write_json(image_dir / "manifest.json", subset)
    return cache, model_lock_path, subset_lock_path


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

    def test_license_allowlist_accepts_only_explicit_canonical_variants(self):
        accepted = {
            "CC0": "CC0",
            "CC0 1.0": "CC0",
            "Public domain": "Public Domain",
            "Public Domain Mark": "Public Domain Mark 1.0",
            "Public Domain Mark 1.0": "Public Domain Mark 1.0",
            "CC BY 1.0": "CC BY 1.0",
            "CC BY 2.0": "CC BY 2.0",
            "CC BY 2.5": "CC BY 2.5",
            "CC BY 3.0": "CC BY 3.0",
            "CC BY 4.0": "CC BY 4.0",
            "CC BY-SA 1.0": "CC BY-SA 1.0",
            "CC BY-SA 2.0": "CC BY-SA 2.0",
            "CC BY-SA 2.5": "CC BY-SA 2.5",
            "CC BY-SA 3.0": "CC BY-SA 3.0",
            "CC BY-SA 4.0": "CC BY-SA 4.0",
            "CC BY-SA 2.0 de": "CC BY-SA 2.0 DE",
            "CC BY-SA 2.0 fr": "CC BY-SA 2.0 FR",
            "CC BY-SA 3.0 de": "CC BY-SA 3.0 DE",
            "CC BY-SA 3.0 it": "CC BY-SA 3.0 IT",
        }
        for raw, canonical in accepted.items():
            with self.subTest(raw=raw):
                self.assertTrue(benchmark.license_allowed(raw))
                self.assertEqual(benchmark.canonical_license(raw), canonical)

    def test_license_allowlist_rejects_restricted_and_ambiguous_names(self):
        rejected = [
            "CC BY-NC 4.0",
            "CC BY-NC-SA 4.0",
            "CC BY-ND 4.0",
            "Fair use",
            "Copyrighted",
            "",
            "CC BY",
            "CC BY-SA",
            "CC BY 5.0",
            "CC BY-SA 4.0 or later",
            "Possibly CC BY 4.0",
            "Custom permissive license",
        ]
        for raw in rejected:
            with self.subTest(raw=raw):
                self.assertFalse(benchmark.license_allowed(raw))
                self.assertIsNone(benchmark.canonical_license(raw))

    def test_all_203_locked_samples_use_canonical_licenses(self):
        records = benchmark.read_jsonl(benchmark.CORPUS_LOCK_PATH)
        self.assertEqual(len(records), 203)
        canonical = set(benchmark.CANONICAL_LICENSES.values())
        self.assertTrue(all(item["canonical_license"] in canonical for item in records))

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

    def test_fetch_locked_corpus_reconstructs_exact_bytes_and_rejects_changes(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            cache = root / "cache"
            lock_path = root / "corpus.lock.jsonl"
            records = []
            payloads = {}
            for index in range(203):
                content = f"locked-image-{index}".encode("ascii")
                url = f"https://upload.wikimedia.org/wikipedia/commons/a/a{index}/image-{index}.jpg"
                payloads[url] = content
                records.append(
                    {
                        "sample_id": f"sample-{index}",
                        "relative_file": f"sample-{index}.jpg",
                        "source_page": f"https://commons.wikimedia.org/wiki/File:Image-{index}.jpg",
                        "download_url": url,
                        "wikimedia_title": f"File:Image-{index}.jpg",
                        "page_id": index + 1,
                        "canonical_license": "CC BY 4.0",
                        "license_url": "https://creativecommons.org/licenses/by/4.0/",
                        "author": "fixture",
                        "sha256": hashlib.sha256(content).hexdigest(),
                        "perceptual_hash64": f"{index:016x}",
                        "width": 10,
                        "height": 10,
                        "size_bytes": len(content),
                        "category": "fixture",
                        "source_label": "fixture",
                        "transformation": "none; fixture",
                        "review_status": "source_category_unreviewed",
                        "cluster_visual": "fixture",
                    }
                )
            benchmark.write_jsonl(lock_path, records)
            with mock.patch.object(benchmark, "CORPUS_LOCK_PATH", lock_path):
                with mock.patch.object(
                    benchmark,
                    "stream_https",
                    side_effect=lambda url: [payloads[url]],
                ):
                    benchmark.fetch_locked_corpus(cache)
                self.assertEqual(
                    (cache / "corpus/sample-0.jpg").read_bytes(),
                    payloads[records[0]["download_url"]],
                )
                (cache / "corpus/sample-0.jpg").write_bytes(b"tampered")
                with mock.patch.object(benchmark, "stream_https", return_value=[b"changed"]):
                    with self.assertRaisesRegex(ValueError, "integrity mismatch"):
                        benchmark.fetch_locked_corpus(cache)

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
        self.assertIn('"verify-android-assets"', runner_build)

    def test_android_asset_validation_rejects_modified_model(self):
        with tempfile.TemporaryDirectory() as directory:
            cache, model_lock, subset_lock = create_android_asset_fixture(Path(directory))
            benchmark.verify_android_assets(cache, model_lock, subset_lock)
            (cache / "models/model-0.bin").write_bytes(b"tampered")
            with self.assertRaisesRegex(ValueError, "model integrity"):
                benchmark.verify_android_assets(cache, model_lock, subset_lock)

    def test_android_asset_validation_rejects_modified_image(self):
        with tempfile.TemporaryDirectory() as directory:
            cache, model_lock, subset_lock = create_android_asset_fixture(Path(directory))
            benchmark.verify_android_assets(cache, model_lock, subset_lock)
            (cache / "android-subset/image-0.jpg").write_bytes(b"tampered")
            with self.assertRaisesRegex(ValueError, "image integrity"):
                benchmark.verify_android_assets(cache, model_lock, subset_lock)

    def test_android_asset_validation_rejects_modified_manifest(self):
        with tempfile.TemporaryDirectory() as directory:
            cache, model_lock, subset_lock = create_android_asset_fixture(Path(directory))
            benchmark.verify_android_assets(cache, model_lock, subset_lock)
            manifest_path = cache / "android-subset/manifest.json"
            manifest = benchmark.read_json(manifest_path)
            manifest["sample_count"] = 49
            benchmark.write_json(manifest_path, manifest)
            with self.assertRaisesRegex(ValueError, "manifest integrity"):
                benchmark.verify_android_assets(cache, model_lock, subset_lock)

    def test_committed_evidence_bundle_recomputes(self):
        benchmark.verify_evidence()

    def test_evidence_record_verifier_rejects_missing_and_duplicate_samples(self):
        corpus = benchmark.read_jsonl(benchmark.CORPUS_LOCK_PATH)
        evidence = benchmark.read_jsonl(benchmark.EVIDENCE_PATH)
        run = benchmark.read_json(benchmark.RUN_PATH)
        summary = benchmark.read_json(benchmark.SUMMARY_PATH)
        model_lock = benchmark.read_json(benchmark.LOCK_PATH)
        with self.assertRaisesRegex(ValueError, "exactly 203"):
            benchmark.verify_evidence_records(corpus, evidence[:-1], run, summary, model_lock)
        duplicate = evidence[:-1] + [evidence[0]]
        with self.assertRaisesRegex(ValueError, "duplicate sample_id"):
            benchmark.verify_evidence_records(corpus, duplicate, run, summary, model_lock)

    def test_evidence_record_verifier_rejects_recomputed_metric_difference(self):
        corpus = benchmark.read_jsonl(benchmark.CORPUS_LOCK_PATH)
        evidence = benchmark.read_jsonl(benchmark.EVIDENCE_PATH)
        run = benchmark.read_json(benchmark.RUN_PATH)
        summary = json.loads(json.dumps(benchmark.read_json(benchmark.SUMMARY_PATH)))
        model_lock = benchmark.read_json(benchmark.LOCK_PATH)
        summary["stages"]["adult_ms"]["p50_ms"] += 1.0
        with self.assertRaisesRegex(ValueError, "latency percentiles"):
            benchmark.verify_evidence_records(corpus, evidence, run, summary, model_lock)

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
                        "size_bytes": len(str(index).encode("ascii")),
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
