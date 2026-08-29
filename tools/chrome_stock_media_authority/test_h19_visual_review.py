import hashlib
import json
import tempfile
import unittest
from pathlib import Path

from finalize_visual_review import PASS_VERDICT, finalize_review
from h19_plan import HarnessError


class H19VisualReviewTest(unittest.TestCase):
    def test_exact_review_finalizes_gate_and_deletes_contact_pixels(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory)
            contact = output / "states/state/contact-sheet.png"
            contact.parent.mkdir(parents=True)
            contact.write_bytes(b"bounded-contact-sheet")
            digest = hashlib.sha256(contact.read_bytes()).hexdigest()
            write_json(
                output / "visual-review-manifest.json",
                {
                    "schema": "glosh-h19-visual-review-manifest-v1",
                    "status": "PENDING_MODEL_OR_HUMAN_REVIEW",
                    "entries": [{"stateId": "state", "path": "states/state/contact-sheet.png", "sha256": digest}],
                },
            )
            write_json(
                output / "states/state/snapshot.json",
                {
                    "rawArtifactsRetained": True,
                    "visualReview": {"sha256": digest, "reviewStatus": "PENDING_MODEL_OR_HUMAN_REVIEW"},
                },
            )
            write_json(output / "summary.json", {"visualReviewGate": {"status": "PENDING"}})
            review = output / "review.json"
            write_json(
                review,
                {
                    "schema": "glosh-h19-visual-review-v1",
                    "reviewer": "model-visible-review",
                    "entries": [
                        {"stateId": "state", "contactSheetSha256": digest, "verdict": PASS_VERDICT}
                    ],
                },
            )

            result = finalize_review(output, review)

            self.assertEqual("PASS", result["status"])
            self.assertFalse(contact.exists())
            self.assertTrue(json.loads((output / "summary.json").read_text())["visualReviewGate"]["automaticPassEligible"])
            snapshot = json.loads((output / "states/state/snapshot.json").read_text())
            self.assertFalse(snapshot["rawArtifactsRetained"])
            self.assertEqual(PASS_VERDICT, snapshot["visualReview"]["reviewStatus"])

    def test_digest_or_state_mismatch_fails_closed_without_deleting_evidence(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory)
            contact = output / "contact.png"
            contact.write_bytes(b"pixels")
            digest = hashlib.sha256(contact.read_bytes()).hexdigest()
            write_json(
                output / "visual-review-manifest.json",
                {
                    "schema": "glosh-h19-visual-review-manifest-v1",
                    "entries": [{"stateId": "state", "path": "contact.png", "sha256": digest}],
                },
            )
            write_json(output / "summary.json", {})
            review = output / "review.json"
            write_json(
                review,
                {
                    "schema": "glosh-h19-visual-review-v1",
                    "reviewer": "reviewer",
                    "entries": [
                        {"stateId": "state", "contactSheetSha256": "0" * 64, "verdict": PASS_VERDICT}
                    ],
                },
            )

            with self.assertRaises(HarnessError):
                finalize_review(output, review)

            self.assertTrue(contact.exists())


def write_json(path: Path, value) -> None:
    path.write_text(json.dumps(value), encoding="utf-8")


if __name__ == "__main__":
    unittest.main()
