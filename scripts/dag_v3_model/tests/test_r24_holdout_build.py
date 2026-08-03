from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from r24_holdout_build import build_holdout  # noqa: E402


class R24HoldoutBuildTest(unittest.TestCase):
    def test_rejects_overlap_with_development(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            image = root / "image.jpg"
            image.write_bytes(b"test")
            row = {
                "sample_id": "sample:1",
                "local_path": image.name,
                "sha256": "same",
                "phash64": "p1",
                "source_url": "https://example.invalid/1",
            }
            manifest = root / "manifest.jsonl"
            manifest.write_text(json.dumps(row) + "\n")
            reviews = root / "reviews.json"
            reviews.write_text(json.dumps({"reviews": {"sample:1": {"action": "allow"}}}))
            split = root / "split.json"
            split.write_text(json.dumps({"records": [{"sample_id": "old", "sha256": "same"}]}))
            with self.assertRaisesRegex(ValueError, "overlaps development"):
                build_holdout(manifest, reviews, split, root / "output.json")


if __name__ == "__main__":
    unittest.main()
