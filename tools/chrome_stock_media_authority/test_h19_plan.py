import json
import unittest
from pathlib import Path

from h19_plan import (
    HarnessError,
    navigation_requires_new_release,
    new_navigation_for,
    post_gesture_ready_required_for,
    ready_required_for,
    validate_plan,
)


def plan_with_taps(taps):
    return {
        "schema": "glosh-h19-a23-plan-v1",
        "phases": [
            {
                "id": "phase",
                "mode": "replace-all",
                "states": [
                    {
                        "id": "state",
                        "url": "https://example.test/",
                        "recordSeconds": 12,
                        "taps": taps,
                    }
                ],
            }
        ],
    }


class H19PlanTest(unittest.TestCase):
    def test_accepts_bounded_normalized_taps(self):
        value = plan_with_taps([{"xPermille": 0, "yPermille": 1000}])

        self.assertEqual(value, validate_plan(value))

    def test_rejects_out_of_range_or_unbounded_taps(self):
        for taps in (
            [{"xPermille": -1, "yPermille": 500}],
            [{"xPermille": 500, "yPermille": 1001}],
            [{"xPermille": 500, "yPermille": 500}] * 5,
        ):
            with self.subTest(taps=taps), self.assertRaises(HarnessError):
                validate_plan(plan_with_taps(taps))

    def test_navigation_semantics_are_explicit_and_safe_by_default(self):
        self.assertTrue(new_navigation_for({"navigation": "reload"}))
        self.assertTrue(new_navigation_for({"navigation": "back"}))
        self.assertFalse(new_navigation_for({"navigation": "foreground"}))
        self.assertFalse(new_navigation_for({"navigation": "background-foreground"}))
        self.assertTrue(new_navigation_for({"navigation": "foreground", "newNavigation": True}))
        self.assertFalse(navigation_requires_new_release({"navigation": "foreground", "newNavigation": True}))
        self.assertTrue(navigation_requires_new_release({"navigation": "reload"}))
        self.assertTrue(navigation_requires_new_release({"navigation": "restart-glosh"}))
        self.assertTrue(navigation_requires_new_release({"navigation": "two-tab-binding"}))
        self.assertTrue(
            post_gesture_ready_required_for(
                {"navigation": "foreground", "newNavigation": True, "postGestureReadyRequired": True, "taps": [{}]}
            )
        )
        self.assertFalse(ready_required_for({"navigation": "chrome-policy"}))
        self.assertTrue(ready_required_for({"navigation": "restart-chrome"}))
        self.assertTrue(ready_required_for({"navigation": "restart-glosh"}))

        invalid = plan_with_taps([])
        invalid["phases"][0]["states"][0].update({"navigation": "reload", "newNavigation": False})
        invalid["phases"][0]["states"][0].pop("url")
        with self.assertRaises(HarnessError):
            validate_plan(invalid)

    def test_exact_actions_cannot_smuggle_arbitrary_urls(self):
        value = plan_with_taps([])
        state = value["phases"][0]["states"][0]
        state.pop("url")
        state.update({"navigation": "chrome-policy", "readyRequired": False, "readyTimeoutSeconds": 0})
        self.assertEqual(value, validate_plan(value))

        state["url"] = "javascript:alert(1)"
        with self.assertRaises(HarnessError):
            validate_plan(value)

    def test_real_web_cannot_disable_digest_bound_visual_review(self):
        value = plan_with_taps([])
        value["phases"][0]["states"][0]["visualReviewRequired"] = False

        with self.assertRaises(HarnessError):
            validate_plan(value)

    def test_document_state_cannot_disable_foreground_ready(self):
        value = plan_with_taps([])
        value["phases"][0]["states"][0].update({"readyRequired": False, "readyTimeoutSeconds": 0})

        with self.assertRaises(HarnessError):
            validate_plan(value)

    def test_glosh_restart_has_a_bounded_recorded_window(self):
        value = plan_with_taps([])
        state = value["phases"][0]["states"][0]
        state.pop("url")
        state.update(
            {
                "navigation": "restart-glosh",
                "recordSeconds": 45,
                "readyTimeoutSeconds": 18,
                "processRestartTimeoutSeconds": 25,
            }
        )
        self.assertEqual(value, validate_plan(value))

        state["recordSeconds"] = 43
        with self.assertRaises(HarnessError):
            validate_plan(value)

    def test_two_tab_binding_has_three_bounded_ready_transitions(self):
        value = plan_with_taps([])
        state = value["phases"][0]["states"][0]
        state.pop("url")
        state.update(
            {
                "navigation": "two-tab-binding",
                "newNavigation": True,
                "visualReviewRequired": False,
                "recordSeconds": 25,
                "readyTimeoutSeconds": 8,
            }
        )
        self.assertEqual(value, validate_plan(value))

        state["recordSeconds"] = 24
        with self.assertRaises(HarnessError):
            validate_plan(value)

    def test_final_plan_is_bounded_and_valid(self):
        plan = json.loads((Path(__file__).with_name("final_plan.json")).read_text())

        self.assertEqual(plan, validate_plan(plan))
        self.assertEqual(385, plan["expectedAppVersionCode"])
        self.assertTrue(all(len(phase["states"]) <= 25 for phase in plan["phases"]))


if __name__ == "__main__":
    unittest.main()
