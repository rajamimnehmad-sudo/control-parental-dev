package com.contentfilter.user.protection

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BatteryOptimizationPromptPolicyTest {
    @Test
    fun promptsOnceWhenExemptionIsMissing() {
        assertTrue(
            BatteryOptimizationPromptPolicy.shouldPrompt(
                exempt = false,
                promptAlreadyShown = false,
            ),
        )
    }

    @Test
    fun doesNotPromptWhenAlreadyExemptOrPreviouslyShown() {
        assertFalse(
            BatteryOptimizationPromptPolicy.shouldPrompt(
                exempt = true,
                promptAlreadyShown = false,
            ),
        )
        assertFalse(
            BatteryOptimizationPromptPolicy.shouldPrompt(
                exempt = false,
                promptAlreadyShown = true,
            ),
        )
    }
}
