from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from r22_targeted_split import _new_records  # noqa: E402


class R22TargetedSplitTest(unittest.TestCase):
    def test_doubt_is_excluded_and_prelabel_provenance_is_explicit(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "manifest.jsonl").write_text(
                "\n".join(
                    json.dumps(
                        {
                            "sample_id": sample_id,
                            "local_path": f"images/{sample_id}.jpg",
                            "sha256": digest * 64,
                            "phash64": phash,
                            "dhash64": phash,
                            "cluster_id": sample_id,
                        }
                    )
                    for sample_id, digest, phash in (("a", "a", "1" * 16), ("b", "b", "2" * 16))
                )
                + "\n",
                encoding="utf-8",
            )
            (root / "prelabels.json").write_text(
                json.dumps({"reviews": {"a": {"action": "filter"}, "b": {"action": "doubt"}}}),
                encoding="utf-8",
            )
            records = _new_records(root / "manifest.jsonl", root / "prelabels.json")
            self.assertEqual([record["sample_id"] for record in records], ["a"])
            self.assertEqual(records[0]["label_source"], "codex_visual_prelabel")
            self.assertEqual(records[0]["split"], "train")


if __name__ == "__main__":
    unittest.main()
