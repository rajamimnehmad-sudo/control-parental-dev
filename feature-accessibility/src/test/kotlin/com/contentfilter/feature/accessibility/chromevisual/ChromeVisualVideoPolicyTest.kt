package com.contentfilter.feature.accessibility.chromevisual

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChromeVisualVideoPolicyTest {
    private val region = ChromeVisualRegion("tile_1_0", 0, 500, 500, 1_000)
    private val key = ChromeVisualVideoRegionKey(windowId = 7, pageIdentity = 10L, regionId = region.id)

    @Test
    fun `two consecutive safe samples are required before revealing`() {
        val policy = ChromeVisualVideoPolicy()
        policy.beginPage(10L)

        assertEquals(ChromeVisualPresentation.Covered, policy.beforeSample(key, region, observedChange = false))
        assertEquals(ChromeVisualPresentation.Covered, policy.record(key, ChromeVisualSampleDecision.Allow))
        assertEquals(listOf(region), policy.regionsNeedingConfirmation(7, 10L, listOf(region)))
        assertEquals(ChromeVisualPresentation.Visible, policy.record(key, ChromeVisualSampleDecision.Allow))
        assertTrue(policy.regionsNeedingConfirmation(7, 10L, listOf(region)).isEmpty())
    }

    @Test
    fun `block and unavailable cover immediately and reset recovery`() {
        val policy = ChromeVisualVideoPolicy()
        policy.beginPage(10L)
        policy.beforeSample(key, region, observedChange = true)
        policy.record(key, ChromeVisualSampleDecision.Allow)
        policy.record(key, ChromeVisualSampleDecision.Allow)

        assertEquals(ChromeVisualPresentation.Covered, policy.record(key, ChromeVisualSampleDecision.Block))
        assertEquals(ChromeVisualPresentation.Covered, policy.record(key, ChromeVisualSampleDecision.Allow))
        assertEquals(ChromeVisualPresentation.Covered, policy.record(key, ChromeVisualSampleDecision.Unavailable))
        assertTrue(policy.hasDynamicRegions())
    }

    @Test
    fun `geometry change invalidates a previously visible region`() {
        val policy = ChromeVisualVideoPolicy()
        policy.beginPage(10L)
        policy.beforeSample(key, region, observedChange = true)
        policy.record(key, ChromeVisualSampleDecision.Allow)
        policy.record(key, ChromeVisualSampleDecision.Allow)

        val moved = region.copy(top = 600, bottom = 1_100)
        assertEquals(ChromeVisualPresentation.Covered, policy.beforeSample(key, moved, observedChange = true))
        assertEquals(listOf(moved), policy.failActiveRegions())
    }

    @Test
    fun `new page clears prior authority`() {
        val policy = ChromeVisualVideoPolicy()
        assertTrue(policy.beginPage(10L))
        policy.beforeSample(key, region, observedChange = true)
        policy.record(key, ChromeVisualSampleDecision.Allow)
        assertTrue(policy.beginPage(11L))
        assertFalse(policy.hasDynamicRegions())
        assertTrue(policy.regionsNeedingConfirmation(7, 11L, listOf(region)).isEmpty())
    }

    @Test
    fun `rapid seek covers immediately and requires a fresh recovery`() {
        val policy = ChromeVisualVideoPolicy()
        policy.beginPage(10L)
        policy.beforeSample(key, region, observedChange = true)
        policy.record(key, ChromeVisualSampleDecision.Allow)
        assertEquals(ChromeVisualPresentation.Visible, policy.record(key, ChromeVisualSampleDecision.Allow))

        assertEquals(ChromeVisualPresentation.Covered, policy.beforeSample(key, region, observedChange = true))
        assertEquals(ChromeVisualPresentation.Covered, policy.record(key, ChromeVisualSampleDecision.Allow))
        assertEquals(ChromeVisualPresentation.Visible, policy.record(key, ChromeVisualSampleDecision.Allow))
    }

    @Test
    fun `second video in the same window never inherits the first video decision`() {
        val policy = ChromeVisualVideoPolicy()
        policy.beginPage(10L)
        policy.beforeSample(key, region, observedChange = true)
        policy.record(key, ChromeVisualSampleDecision.Allow)
        policy.record(key, ChromeVisualSampleDecision.Allow)

        val nextKey = key.copy(pageIdentity = 11L)
        assertTrue(policy.beginPage(11L))
        assertEquals(ChromeVisualPresentation.Covered, policy.beforeSample(nextKey, region, observedChange = true))
        assertEquals(ChromeVisualPresentation.Covered, policy.record(nextKey, ChromeVisualSampleDecision.Allow))
    }
}
