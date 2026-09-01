package com.contentfilter.user.chromedataplane

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class ChromeH20RendererAmplificationFixtureTest {
    private val fixture = ChromeH20RendererAmplificationFixture()

    @Test
    fun `fixture exposes isolated bounded renderer stress families`() {
        ChromeH20RendererAmplificationMode.entries.forEach { mode ->
            val html = fixture.document(mode)
            val script = fixture.script(mode)
            assertContains(html, "mode=${mode.name.replace(Regex("([a-z])([A-Z])"), "${'$'}1_${'$'}2").uppercase()}")
            assertContains(html, "<script defer src=")
            assertContains(script, ChromeMediaShieldBootstrap.SelfShieldOriginalScriptStartedName)
            assertContains(script, ChromeMediaShieldRendererMetricsScript.SnapshotEvent)
            assertContains(script, "SITE_MUTATIONS=")
        }
    }

    @Test
    fun `fixture report is bounded and canonical`() {
        val response =
            fixture.responseFor(
                request(
                    ChromeH20RendererAmplificationFixture.ReportPath,
                    "POST",
                    "MODE=ATTR_STRESS,SITE_MUTATIONS=160".toByteArray(),
                ),
            )
        assertEquals(204, response?.statusCode)
        assertContains(fixture.state(), "REPORTS=1")
        assertEquals(
            400,
            fixture.responseFor(
                request(ChromeH20RendererAmplificationFixture.ReportPath, "POST", "invalid".toByteArray()),
            )?.statusCode,
        )
    }

    private fun request(
        target: String,
        method: String = "GET",
        body: ByteArray = ByteArray(0),
    ) = ChromePhotosProxyRequest(method, target, "HTTP/1.1", emptyList(), body)
}
