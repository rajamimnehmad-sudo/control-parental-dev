import unittest

from r3_candidate_split import build_split, merge_labels


class R3CandidateSplitTest(unittest.TestCase):
    def test_focused_action_and_labels_override_partial_record(self):
        partial = {
            "records": [{
                "sample_id": "wikimedia:1",
                "policy_action": "filter",
                "labels": {"signal": "unknown"},
                "label_semantics": "owner_review_partial",
                "reviewed_at": None,
                "training_authorization": "owner_authorized_private_experiment",
            }]
        }
        focused = {"rows": [{
            "sample_id": "wikimedia:1",
            "owner_policy_action": "allow",
            "labels": {"signal": "positive"},
            "reviewed_at": "now",
        }]}
        record = merge_labels(partial, focused)[0]
        self.assertEqual("allow", record["policy_action"])
        self.assertEqual("positive", record["labels"]["signal"])
        self.assertEqual("owner_review_complete", record["label_semantics"])

    def test_build_split_keeps_evaluation_and_adds_train_only(self):
        base = {"schema_version": "base", "records": [
            {"sample_id": "base:1", "split": "validation", "target": 0},
            {"sample_id": "base:2", "split": "frozen_test", "target": 1},
        ]}
        rows = [{
            "sample_id": "wikimedia:1",
            "policy_action": "allow",
            "labels": {"signal": "positive"},
            "label_semantics": "owner_review_complete",
            "training_authorization": "owner_authorized_private_experiment",
        }]
        result = build_split(base, rows, {"wikimedia:1": __import__("pathlib").Path("/tmp/1.jpg")})
        self.assertEqual(1, result["summary"]["r3_allow"])
        self.assertEqual("train", result["records"][-1]["split"])
        self.assertFalse(result["summary"]["final_sealed_opened"])


if __name__ == "__main__":
    unittest.main()
