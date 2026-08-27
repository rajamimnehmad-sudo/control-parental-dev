package com.contentfilter.feature.accessibility.chromevisual

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ChromeVisualShieldIdentityTest {
    private val viewport = ChromeVisualViewport(0, 100, 1080, 2200)
    private val contract =
        ChromeVisualShieldRegionContract(
            id = "fixture-sentinel-v1",
            leftBasisPoints = 1_500,
            topBasisPoints = 2_500,
            rightBasisPoints = 8_500,
            bottomBasisPoints = 5_500,
            fixtureSignature = "compiled:13b-r:v1",
        )

    @Test
    fun `late C1 after E2 is stale and cannot release current protection`() {
        var staleDropped = 0
        val gate = ChromeVisualShieldIdentityGate { staleDropped += 1 }
        gate.start(windowId = 7, viewport = viewport, regionContract = contract)
        val c1 = assertNotNull(gate.beginCapture())
        assertIs<ChromeVisualShieldResult.Current>(gate.beginProcessing(c1))

        gate.invalidate(7, viewport, contract, ChromeVisualShieldInvalidation.Scroll)
        val e2 = assertNotNull(gate.snapshot().context)

        assertIs<ChromeVisualShieldResult.Stale>(gate.completeProcessing(c1))
        assertEquals(1, staleDropped)
        assertEquals(ChromeVisualShieldPhase.Protected, gate.snapshot().phase)
        assertFalse(gate.releaseForExplicitLabGate(c1.toContext()))
        assertEquals(ChromeVisualShieldPhase.Protected, gate.snapshot().phase)
        assertTrue(gate.releaseForExplicitLabGate(e2))
    }

    @Test
    fun `sessions epochs viewport and region sequences are monotonic`() {
        val gate = ChromeVisualShieldIdentityGate()
        val first = assertNotNull(assertNotNull(gate.start(7, viewport, contract)).context)
        val scroll =
            assertNotNull(
                gate.invalidate(7, viewport, contract, ChromeVisualShieldInvalidation.Scroll)?.context,
            )
        val rotatedViewport = ChromeVisualViewport(0, 60, 2200, 1080)
        val rotation =
            assertNotNull(
                gate.invalidate(7, rotatedViewport, contract, ChromeVisualShieldInvalidation.Rotation)?.context,
            )
        val newWindow =
            assertNotNull(
                gate.invalidate(9, rotatedViewport, contract, ChromeVisualShieldInvalidation.WindowReplaced)
                    ?.context,
            )
        val secondSession =
            assertNotNull(assertNotNull(gate.start(9, rotatedViewport, contract)).context)

        assertTrue(scroll.contentEpoch > first.contentEpoch)
        assertEquals(first.viewportEpoch, scroll.viewportEpoch)
        assertTrue(scroll.regionSequence > first.regionSequence)
        assertTrue(rotation.viewportEpoch > scroll.viewportEpoch)
        assertTrue(newWindow.viewportEpoch > rotation.viewportEpoch)
        assertTrue(secondSession.protectionSessionId > first.protectionSessionId)
        assertTrue(secondSession.contentEpoch > newWindow.contentEpoch)
    }

    @Test
    fun `invalid fixture contract fails closed before a session exists`() {
        val invalid = contract.copy(fixtureSignature = "", rightBasisPoints = 20_000)
        val gate = ChromeVisualShieldIdentityGate()

        assertEquals(null, gate.start(7, viewport, invalid))
        assertEquals(ChromeVisualShieldPhase.Inactive, gate.snapshot().phase)
        assertEquals(null, gate.beginCapture())
    }

    @Test
    fun `protection is committed before capture scheduling`() {
        val gate = ChromeVisualShieldIdentityGate()
        gate.start(7, viewport, contract)
        val coordinator = ChromeVisualShieldCycleCoordinator(gate)
        val ordering = mutableListOf<String>()

        val scheduled =
            coordinator.invalidateProtectThenSchedule(
                windowId = 7,
                viewport = viewport,
                regionContract = contract,
                reason = ChromeVisualShieldInvalidation.Navigation,
                protect = {
                    assertEquals(ChromeVisualShieldPhase.Protected, it.phase)
                    ordering += "protect"
                    true
                },
                schedule = { ordering += "capture" },
            )

        assertTrue(scheduled)
        assertEquals(listOf("protect", "capture"), ordering)
    }

    @Test
    fun `capture scheduling stops when opaque protection cannot commit`() {
        val gate = ChromeVisualShieldIdentityGate()
        gate.start(7, viewport, contract)
        val coordinator = ChromeVisualShieldCycleCoordinator(gate)
        var scheduled = false

        assertFalse(
            coordinator.invalidateProtectThenSchedule(
                7,
                viewport,
                contract,
                ChromeVisualShieldInvalidation.Viewport,
                protect = { false },
                schedule = { scheduled = true },
            ),
        )
        assertFalse(scheduled)
        assertEquals(ChromeVisualShieldPhase.Protected, gate.snapshot().phase)
    }

    @Test
    fun `capture pending processing and errors all remain fail closed`() {
        val gate = ChromeVisualShieldIdentityGate()
        gate.start(7, viewport, contract)
        val capture = assertNotNull(gate.beginCapture())
        assertTrue(gate.snapshot().isFailClosed)

        gate.beginProcessing(capture)
        assertTrue(gate.snapshot().isFailClosed)

        gate.failClosed(capture)
        assertEquals(ChromeVisualShieldPhase.Protected, gate.snapshot().phase)
        assertTrue(gate.snapshot().isFailClosed)
    }

    @Test
    fun `multiple captures completed out of order cannot change current epoch`() {
        val gate = ChromeVisualShieldIdentityGate()
        gate.start(7, viewport, contract)
        val c1 = assertNotNull(gate.beginCapture())
        gate.beginProcessing(c1)
        gate.invalidate(7, viewport, contract, ChromeVisualShieldInvalidation.Scroll)
        val c2 = assertNotNull(gate.beginCapture())
        gate.beginProcessing(c2)
        gate.invalidate(7, viewport, contract, ChromeVisualShieldInvalidation.Navigation)

        assertIs<ChromeVisualShieldResult.Stale>(gate.completeProcessing(c2))
        assertIs<ChromeVisualShieldResult.Stale>(gate.completeProcessing(c1))
        assertEquals(ChromeVisualShieldPhase.Protected, gate.snapshot().phase)
        assertEquals(3, gate.snapshot().protectionTransitions)
    }

    @Test
    fun `fixture region resolves deterministically and maps to bounded frame crop`() {
        val region = assertNotNull(contract.resolve(viewport))
        val mapped = assertNotNull(ChromeVisualGeometryMapper.toFrame(region, viewport, 540, 1050))

        assertEquals(378, mapped.width)
        assertEquals(315, mapped.height)
        assertEquals("fixture-sentinel-v1", mapped.id)
    }

    private fun ChromeVisualShieldIdentity.toContext() =
        ChromeVisualShieldContext(
            protectionSessionId = protectionSessionId,
            windowId = windowId,
            contentEpoch = contentEpoch,
            viewport = viewport,
            viewportEpoch = viewportEpoch,
            regionId = regionId,
            regionSequence = regionSequence,
            region = region,
        )
}
