package com.contentfilter.feature.accessibility.chromevisual

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChromeVisualWindowSelectionPolicyTest {
    @Test
    fun `inactive chrome window cannot survive an unrelated app event`() {
        assertFalse(
            ChromeVisualWindowSelectionPolicy.canUseExactCandidate(
                isActive = false,
                isFocused = false,
                allowBehindInputMethod = false,
            ),
        )
    }

    @Test
    fun `active or focused chrome window remains eligible`() {
        assertTrue(
            ChromeVisualWindowSelectionPolicy.canUseExactCandidate(
                isActive = true,
                isFocused = false,
                allowBehindInputMethod = false,
            ),
        )
        assertTrue(
            ChromeVisualWindowSelectionPolicy.canUseExactCandidate(
                isActive = false,
                isFocused = true,
                allowBehindInputMethod = false,
            ),
        )
    }

    @Test
    fun `input method may preserve exact chrome window behind it`() {
        assertTrue(
            ChromeVisualWindowSelectionPolicy.canUseExactCandidate(
                isActive = false,
                isFocused = false,
                allowBehindInputMethod = true,
            ),
        )
    }
}
