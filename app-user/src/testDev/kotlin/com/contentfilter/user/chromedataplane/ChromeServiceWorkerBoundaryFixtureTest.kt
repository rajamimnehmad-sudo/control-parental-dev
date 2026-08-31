package com.contentfilter.user.chromedataplane

import com.contentfilter.core.domain.chrome.ChromeMediaShieldDocumentAuthorityRegistry
import com.contentfilter.core.domain.chrome.ChromePhotosDataPlaneLabContract
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ChromeServiceWorkerBoundaryFixtureTest {
    private val fixture = ChromeServiceWorkerBoundaryFixture()

    @Test
    fun `worker is root scope capable and covers pass through synthetic ready and synthetic navigation`() {
        val response = fixture.responseFor(request("GET", "/web20sw/sw.js"))
        val script = assertNotNull(response).text()

        assertEquals("/", response.headers.first { it.name == "Service-Worker-Allowed" }.value)
        assertContains(script, ChromePhotosDataPlaneLabContract.MediaShieldSelfReadyPath)
        assertContains(script, "PASS_THROUGH")
        assertContains(script, "'PASSTHROUGH'")
        assertContains(script, "SYNTHETIC_SELF_READY")
        assertContains(script, "SYNTHETIC_NAVIGATION")
        assertContains(script, "new Response(null,{status:204")
        assertContains(script, "SW SYNTHETIC DOCUMENT SENTINEL")
    }

    @Test
    fun `install proves real controller and probe keeps original-script trace`() {
        val install = assertNotNull(fixture.responseFor(request("GET", "/web20sw/install"))).text()
        val probe = assertNotNull(fixture.responseFor(request("GET", "/web20sw/probe"))).text()

        assertContains(install, "scope:'/'")
        assertContains(install, "navigator.serviceWorker.controller")
        assertContains(install, "navigator.serviceWorker.getRegistrations()")
        assertContains(install, "RESET_BASELINE")
        assertContains(install, "REGISTER_RESULT")
        assertContains(install, "SW_REGISTER_BLOCKED")
        assertContains(install, "SW_CONTROLLER=YES")
        assertContains(probe, "/web20sw/first-original.js")
        assertContains(probe, "CLIENT_CONTROLLER")
        assertFalse(probe.contains("SW SYNTHETIC DOCUMENT SENTINEL"))
    }

    @Test
    fun `event contract is bounded strict and updates evidence only`() {
        ChromeMediaShieldDocumentAuthorityRegistry.beginSession("sw-boundary-test", 20L)
        try {
            assertEquals(
                "SYNTHETIC_SELF_READY|FETCH_SELF_READY|SYNTHETIC",
                fixture.canonicalEventOrNull("v1|SYNTHETIC_SELF_READY|FETCH_SELF_READY|SYNTHETIC".toByteArray()),
            )
            assertNull(fixture.canonicalEventOrNull("v1|FORGED|FETCH_SELF_READY|SYNTHETIC".toByteArray()))
            assertNull(fixture.canonicalEventOrNull("v1|PASS_THROUGH|FETCH_SELF_READY|ALLOW".toByteArray()))

            val accepted =
                assertNotNull(
                    fixture.responseFor(
                        request("POST", "/web20sw/event", "v1|PASS_THROUGH|CLIENT_CONTROLLER|YES"),
                    ),
                )
            assertEquals(204, accepted.statusCode)
            assertContains(fixture.state(), "CONTROLLER_PRESENT=1")
            assertContains(fixture.state(), "REPORTS_ACCEPTED=1")

            val rejected = assertNotNull(fixture.responseFor(request("POST", "/web20sw/event", "forged")))
            assertEquals(400, rejected.statusCode)
            assertContains(fixture.state(), "REPORTS_REJECTED=1")
            assertEquals(0, ChromeMediaShieldDocumentAuthorityRegistry.snapshot().readyClaims)
        } finally {
            ChromeMediaShieldDocumentAuthorityRegistry.clear()
        }
    }

    @Test
    fun `state distinguishes proxy document and service worker fetch evidence`() {
        fixture.responseFor(request("GET", "/web20sw/probe?sw_case=PASS_THROUGH"))
        fixture.responseFor(request("POST", "/web20sw/event", "v1|PASS_THROUGH|FETCH_NAVIGATION|PASSTHROUGH"))
        fixture.responseFor(request("POST", "/web20sw/event", "v1|PASS_THROUGH|FETCH_SELF_READY|PASSTHROUGH"))
        fixture.responseFor(request("POST", "/web20sw/event", "v1|SYNTHETIC_NAVIGATION|FETCH_NAVIGATION|SYNTHETIC"))

        val state = fixture.state()
        assertContains(state, "PROBE_DOCS=1")
        assertContains(state, "NAV_FETCHES=2")
        assertContains(state, "SELF_READY_FETCHES=1")
        assertContains(state, "SELF_READY_PASS_THROUGH=1")
        assertContains(state, "NAV_SYNTHETIC=1")
    }

    @Test
    fun `reset verification distinguishes clean baseline and blocked future registration`() {
        fixture.responseFor(request("POST", "/web20sw/event", "v1|RESET_VERIFY|RESET_BASELINE|CLEAN"))
        fixture.responseFor(request("POST", "/web20sw/event", "v1|RESET_VERIFY|REGISTER_RESULT|BLOCKED"))

        val state = fixture.state()
        assertContains(state, "RESET_BASELINE_CLEAN=1")
        assertContains(state, "RESET_BASELINE_DIRTY=0")
        assertContains(state, "REGISTER_BLOCKED=1")
        assertContains(state, "REGISTER_SUCCEEDED=0")
    }

    @Test
    fun `unknown paths do not change fixture state`() {
        val before = fixture.state()
        assertNull(fixture.responseFor(request("GET", "/outside")))
        assertEquals(before, fixture.state())
    }

    private fun request(
        method: String,
        target: String,
        body: String = "",
    ) = ChromePhotosProxyRequest(method = method, target = target, body = body.toByteArray())

    private fun ChromePhotosFixtureResponse.text(): String = originalBytes.toString(Charsets.UTF_8)
}
