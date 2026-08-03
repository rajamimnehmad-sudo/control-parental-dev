from __future__ import annotations

import hashlib
import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from r22_canary_manifest import build_manifest  # noqa: E402


class R22CanaryManifestTest(unittest.TestCase):
    def test_requires_exactly_forty_binary_hash_bound_samples(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            images = root / "images"
            images.mkdir()
            rows = []
            reviews = {}
            for index in range(40):
                image = images / f"{index}.jpg"
                image.write_bytes(f"image-{index}".encode())
                sample_id = f"sample:{index}"
                rows.append(
                    {
                        "sample_id": sample_id,
                        "local_path": f"images/{image.name}",
                        "sha256": hashlib.sha256(image.read_bytes()).hexdigest(),
                        "mime": "image/jpeg",
                    }
                )
                reviews[sample_id] = {"action": "filter" if index < 12 else "allow"}
            manifest = root / "manifest.jsonl"
            manifest.write_text("\n".join(json.dumps(row) for row in rows) + "\n", encoding="utf-8")
            review_path = root / "reviews.json"
            review_path.write_text(json.dumps({"reviews": reviews}), encoding="utf-8")

            result = build_manifest(manifest, review_path, images)

            self.assertEqual(len(result["samples"]), 40)
            self.assertEqual(result["samples"][0]["human_action"], "filter")
            self.assertFalse(result["final_sealed_opened"])


if __name__ == "__main__":
    unittest.main()
