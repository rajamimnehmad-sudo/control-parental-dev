package com.contentfilter.feature.accessibility.chromevisual

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChromeVisualCapabilityPolicyTest {
    @Test
    fun `only api 34 arm64 dev with engine can analyze Chrome`() {
        val available = ChromeVisualCapabilityPolicy.initial(34, true, true, true)
        assertEquals(ChromeVisualCapabilityState.AvailableStrongEnough, available.state)
        assertTrue(available.canAnalyzeChrome)
        assertFalse(available.requiresDagFallback)

        assertEquals(
            ChromeVisualCapabilityState.UnsupportedAndroid,
            ChromeVisualCapabilityPolicy.initial(33, true, true, true).state,
        )
        assertEquals(
            ChromeVisualCapabilityState.UnsupportedAbi,
            ChromeVisualCapabilityPolicy.initial(34, false, true, true).state,
        )
        assertEquals(
            ChromeVisualCapabilityState.Unavailable,
            ChromeVisualCapabilityPolicy.initial(34, true, false, true).state,
        )
    }

    @Test
    fun `secure and failed captures require DAG while preserving coverage`() {
        val secure = ChromeVisualCapabilityPolicy.captureFailure(secureWindow = true)
        assertEquals(ChromeVisualCapabilityState.SecureWindow, secure.state)
        assertTrue(secure.keepExistingCoverage)
        assertTrue(secure.requiresDagFallback)

        val failed = ChromeVisualCapabilityPolicy.captureFailure(secureWindow = false)
        assertEquals(ChromeVisualCapabilityState.Degraded, failed.state)
        assertTrue(failed.keepExistingCoverage)
        assertTrue(failed.requiresDagFallback)
    }

    @Test
    fun `ambiguous geometry and overload fail closed`() {
        assertEquals(
            ChromeVisualCapabilityState.AmbiguousGeometry,
            ChromeVisualCapabilityPolicy.runtimeUnavailable(ambiguousGeometry = true).state,
        )
        assertEquals(
            ChromeVisualCapabilityState.Overload,
            ChromeVisualCapabilityPolicy.runtimeUnavailable(overloaded = true).state,
        )
    }
}
