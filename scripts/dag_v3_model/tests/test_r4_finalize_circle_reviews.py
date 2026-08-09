import tempfile
import unittest
from pathlib import Path

from scripts.dag_v3_model.r4_finalize_circle_reviews import finalize_reviews


class R4FinalizeCircleReviewsTest(unittest.TestCase):
    def test_clear_reviews_are_finalized_and_doubts_excluded(self):
        with tempfile.TemporaryDirectory() as temporary:
            image = Path(temporary) / "crop.jpg"
            image.write_bytes(b"crop")
            payload = {
                "schema_version": "queue-v1",
                "records": [
                    {
                        "sample_id": "allow",
                        "parent_sample_id": "a",
                        "image_path": str(image),
                        "group_key": "a",
                        "review_status": "complete",
                        "review_action": "allow",
                    },
                    {
                        "sample_id": "doubt",
                        "parent_sample_id": "b",
                        "image_path": str(image),
                        "group_key": "b",
                        "review_status": "doubt",
                        "review_action": "doubt",
                    },
                ],
            }
            result = finalize_reviews(payload, authorize_private_training=True)
            self.assertEqual({"allow": 1, "filter": 0, "doubt_excluded": 1}, result["counts"])
            self.assertEqual(1, len(result["records"]))
            self.assertTrue(result["records"][0]["training_authorized"])

    def test_pending_review_is_rejected(self):
        payload = {"records": [{"sample_id": "pending", "review_status": "pending", "review_action": None}]}
        with self.assertRaisesRegex(ValueError, "pending"):
            finalize_reviews(payload, authorize_private_training=False)


if __name__ == "__main__":
    unittest.main()
