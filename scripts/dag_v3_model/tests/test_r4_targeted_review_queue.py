import unittest

from scripts.dag_v3_model.r4_targeted_review_queue import assign_batches, select_stratified


class R4TargetedReviewQueueTest(unittest.TestCase):
    def test_selection_respects_semantic_label_quotas_and_hardness(self):
        rows = [
            {"sample_id": "a-hard", "group_key": "a-hard", "category": "x", "parent_target": 0, "r3_probability": 0.9},
            {"sample_id": "a-easy", "group_key": "a-easy", "category": "x", "parent_target": 0, "r3_probability": 0.1},
            {"sample_id": "f-hard", "group_key": "f-hard", "category": "x", "parent_target": 1, "r3_probability": 0.1},
            {"sample_id": "f-easy", "group_key": "f-easy", "category": "x", "parent_target": 1, "r3_probability": 0.9},
        ]
        plan = {"authorized_total": 2, "strata": [{"category": "x", "parent_allow": 1, "parent_filter": 1}]}
        selected = select_stratified(rows, plan)
        self.assertEqual({"a-hard", "f-hard"}, {row["sample_id"] for row in selected})

    def test_batch_assignment_is_complete_and_deterministic(self):
        rows = [
            {"sample_id": str(index), "group_key": str(index), "category": "x", "parent_target": index % 2, "r3_probability": index / 10}
            for index in range(8)
        ]
        first = assign_batches(rows, 4)
        second = assign_batches(rows, 4)
        self.assertEqual(first, second)
        self.assertEqual([4, 4], [len(batch) for batch in first])


if __name__ == "__main__":
    unittest.main()
