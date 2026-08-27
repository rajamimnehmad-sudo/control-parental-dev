package com.contentfilter.user.chromedataplane

import com.contentfilter.feature.accessibility.chromevisual.ChromeVisualShieldRegionDiscoveryRenderBinding
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChromeVisualShieldRegionDiscoveryFixtureTest {
    @Test
    fun `layout matrix is deterministic and keeps separate multi regions`() {
        val centered =
            assertNotNull(
                ChromeVisualShieldRegionDiscoveryLayoutContract.geometry(
                    ChromeVisualShieldRegionDiscoveryScenario.CenteredBlock,
                    listOf(1064 to 1600),
                    700,
                    660,
                ),
            )
        val multi =
            assertNotNull(
                ChromeVisualShieldRegionDiscoveryLayoutContract.geometry(
                    ChromeVisualShieldRegionDiscoveryScenario.MultiSafeBlock,
                    listOf(100 to 100, 1064 to 1600),
                    1000,
                    600,
                ),
            )

        assertEquals(1, centered.size)
        assertEquals(2, multi.size)
        assertFalse(multi[0].left + multi[0].width >= multi[1].left)
    }

    @Test
    fun `fixture uses screenshot-only canvas islands and never release`() {
        val response =
            assertNotNull(
                ChromeVisualShieldFixture.responseFor(
                    request(
                        ChromeVisualShieldRegionDiscoveryFixture.pagePath(
                            ChromeVisualShieldRegionDiscoveryScenario.MultiSafeBlock,
                        ),
                    ),
                ),
            )
        val html = response.originalBytes.toString(Charsets.UTF_8)

        assertContains(html, "<canvas id=\"discovery-canvas\"")
        assertContains(html, "data-never-release=\"true\"")
        assertContains(html, "createImageBitmap(new Blob([bytes]")
        assertContains(html, "context.drawImage(sources[index]")
        assertContains(html, "const awaitPresentedFrame = async () => { await frame(); await frame(); };")
        assertContains(html, "await awaitPresentedFrame();")
        assertEquals(2, Regex(Regex.escape("await frame();")).findAll(html).count())
        assertContains(html, ChromeVisualShieldRegionDiscoveryLayoutContract.Version)
        assertContains(html, "multi-safe-block")
        assertContains(html, "screen.availHeight || screen.height")
        assertContains(html, "screenAvailableHeight - innerHeight")
        assertContains(html, "screen.height * 0.25 - browserTopControlsHeight")
        assertContains(html, "screen.height * 0.3")
        assertContains(html, ChromeVisualShieldRegionDiscoveryFixture.RenderIdentityPrefix)
        assertContains(html, "lastSubmittedGeometryKey")
        assertContains(html, "geometrySnapshot().keyBody !== geometry.keyBody")
        assertContains(html, "attestationResult.startsWith('result=region_generation_invalidated')")
        assertEquals(1, "region_generation_invalidated".toRegex().findAll(html).count())
        assertEquals(2, "lastSubmittedGeometryKey = null;".toRegex().findAll(html).count())
        assertFalse(html.contains("attestationResult.startsWith('result=region_attestation"))
        assertFalse(html.contains(ChromeVisualShieldFixture.RenderIdentityPath))
        assertFalse(html.contains("revision += 1"))
        assertFalse(html.contains("expectedVerdict"))
        assertFalse(html.contains("release("))
        assertFalse(html.contains("screen.height - viewportHeight"))
        assertFalse(html.contains("screen.height - innerHeight"))
        assertFalse(response.contentType.startsWith("image/"))
    }

    @Test
    fun `carrier top excludes system navigation and stays at signed available screen fraction`() {
        val screenHeight = 803.0
        val availableScreenHeight = 789.0
        val layoutViewportHeight = 724.0

        val carrierTop =
            ChromeVisualShieldRegionDiscoveryLayoutContract.carrierTopCss(
                screenHeight,
                availableScreenHeight,
                layoutViewportHeight,
            )
        val absoluteCarrierTop = (availableScreenHeight - layoutViewportHeight) + carrierTop
        val oldAbsoluteCarrierTop =
            (availableScreenHeight - layoutViewportHeight) +
                maxOf(
                    0.0,
                    screenHeight * ChromeVisualShieldRegionDiscoveryLayoutContract.CarrierTopFraction -
                        (screenHeight - layoutViewportHeight),
                )

        assertEquals(screenHeight * 0.25, absoluteCarrierTop)
        assertTrue(oldAbsoluteCarrierTop < absoluteCarrierTop)
        assertEquals(screenHeight - availableScreenHeight, absoluteCarrierTop - oldAbsoluteCarrierTop)
    }

    @Test
    fun `attestation rejects geometry not produced by fixed contract`() {
        val scenario = ChromeVisualShieldRegionDiscoveryScenario.CenteredSafe
        val body =
            "${scenario.wireName}|${ChromeVisualShieldRegionDiscoveryLayoutContract.Version}|$Token|$RenderKey|" +
                "7|11|21|13|21|700|660|" +
                "100|200|700|660|0|0|1080|2200|1|1|true|" +
                "safe,${ChromeVisualShieldFixtureSample.Safe.expectedSha256},100,100,0,0,700,660"

        ChromeVisualShieldRegionDiscoveryAttestationStore.clear()
        assertContains(
            ChromeVisualShieldRegionDiscoveryAttestationStore.record(scenario, body, Binding),
            "geometry_mismatch",
        )
        assertNull(ChromeVisualShieldRegionDiscoveryAttestationStore.peek(scenario, Binding))
    }

    @Test
    fun `attestation pins identity sources and complete expectation`() {
        val scenario = ChromeVisualShieldRegionDiscoveryScenario.CenteredSafe
        val geometry =
            assertNotNull(
                ChromeVisualShieldRegionDiscoveryLayoutContract.geometry(scenario, listOf(100 to 100), 700, 660),
            ).single()
        val body =
            "${scenario.wireName}|${ChromeVisualShieldRegionDiscoveryLayoutContract.Version}|$Token|$RenderKey|" +
                "7|11|21|13|21|700|660|" +
                "100|200|700|660|0|0|1080|2200|1|1|true|" +
                "safe,${ChromeVisualShieldFixtureSample.Safe.expectedSha256},100,100," +
                "${geometry.left},${geometry.top},${geometry.width},${geometry.height}"

        ChromeVisualShieldRegionDiscoveryAttestationStore.clear()
        assertContains(
            ChromeVisualShieldRegionDiscoveryAttestationStore.record(scenario, body, Binding),
            "result=region_render_attested",
        )
        val oracle = assertNotNull(ChromeVisualShieldRegionDiscoveryAttestationStore.peek(scenario, Binding)).oracle()
        assertEquals(Token, oracle.renderIdentityToken)
        assertEquals(
            listOf(ChromeVisualShieldFixtureSample.Safe.expectedSha256),
            oracle.regions.map { it.sourceSha256 },
        )
        assertEquals(true, oracle.expectComplete)
        ChromeVisualShieldRegionDiscoveryAttestationStore.clear()
    }

    private fun request(path: String) =
        ChromePhotosProxyRequest(
            target = path,
            method = "GET",
            version = "HTTP/1.1",
            headers = emptyList(),
            body = ByteArray(0),
        )

    private companion object {
        const val Token = "7:11:13:0:0:1080:2408:fixture:162:602:918:1324"
        const val RenderKey = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        val Binding =
            ChromeVisualShieldRegionDiscoveryRenderBinding(
                protectionSessionId = 7,
                windowId = 11,
                contentEpoch = 21,
                viewportEpoch = 13,
                regionSequence = 21,
                renderIdentityToken = Token,
                renderGeometryKeyDigest = RenderKey,
            )
    }
}
