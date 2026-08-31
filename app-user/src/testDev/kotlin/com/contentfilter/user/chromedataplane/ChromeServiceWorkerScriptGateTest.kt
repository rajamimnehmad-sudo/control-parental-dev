package com.contentfilter.user.chromedataplane

import com.contentfilter.core.domain.chrome.ChromePhotosDataPlaneRuntimeAttestation
import okhttp3.OkHttpClient
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ChromeServiceWorkerScriptGateTest {
    @Test
    fun `browser owned service worker metadata is classified without blocking ordinary workers`() {
        assertTrue(
            request(ChromeHttpHeader("Service-Worker", " script ")).isServiceWorkerScriptRequest(),
        )
        assertTrue(
            request(ChromeHttpHeader("Sec-Fetch-Dest", "serviceworker")).isServiceWorkerScriptRequest(),
        )
        assertFalse(
            request(ChromeHttpHeader("Sec-Fetch-Dest", "worker")).isServiceWorkerScriptRequest(),
        )
        assertFalse(
            request(ChromeHttpHeader("Sec-Fetch-Dest", "script")).isServiceWorkerScriptRequest(),
        )
    }

    @Test
    fun `gate is scoped to document self shield sessions`() {
        val serviceWorker = request(ChromeHttpHeader("Service-Worker", "script"))

        assertFalse(ChromeServiceWorkerScriptGate.blocks(serviceWorker, documentSelfShieldEnabled = false))
        assertTrue(ChromeServiceWorkerScriptGate.blocks(serviceWorker, documentSelfShieldEnabled = true))
        assertFalse(
            ChromeServiceWorkerScriptGate.blocks(
                request(ChromeHttpHeader("Sec-Fetch-Dest", "worker")),
                documentSelfShieldEnabled = true,
            ),
        )
    }

    @Test
    fun `real upstream rejects service worker main script before network call`() {
        val networkCalls = AtomicInteger()
        val client =
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    networkCalls.incrementAndGet()
                    chain.proceed(chain.request())
                }
                .build()
        val upstream = ChromePhotosRealUpstream(client = client)
        ChromePhotosDataPlaneRuntimeAttestation.beginSession(
            sessionId = "sw-script-gate-test",
            mediaAuthorityEnabled = true,
            mediaPolicyEpoch = 20L,
            documentSelfShieldEnabled = true,
        )
        try {
            assertFailsWith<IOException> {
                upstream.execute(
                    host = "example.com",
                    request = request(ChromeHttpHeader("Service-Worker", "script")),
                )
            }
            assertEquals(0, networkCalls.get())
        } finally {
            upstream.close()
            ChromePhotosDataPlaneRuntimeAttestation.clear()
        }
    }

    @Test
    fun `controlled fixture refuses service worker script while ordinary worker probe remains available`() {
        val fixture = ChromeServiceWorkerBoundaryFixture()
        ChromePhotosDataPlaneRuntimeAttestation.beginSession(
            sessionId = "sw-fixture-gate-test",
            mediaAuthorityEnabled = true,
            mediaPolicyEpoch = 20L,
            documentSelfShieldEnabled = true,
        )
        try {
            val blocked =
                assertNotNull(
                    fixture.responseFor(
                        ChromePhotosProxyRequest(
                            method = "GET",
                            target = "/web20sw/sw.js",
                            headers = listOf(ChromeHttpHeader("Service-Worker", "script")),
                        ),
                    ),
                )
            val ordinaryWorker =
                assertNotNull(
                    fixture.responseFor(
                        ChromePhotosProxyRequest(
                            method = "GET",
                            target = "/web20sw/worker-register-probe.js",
                            headers = listOf(ChromeHttpHeader("Sec-Fetch-Dest", "worker")),
                        ),
                    ),
                )

            assertEquals(403, blocked.statusCode)
            assertEquals(200, ordinaryWorker.statusCode)
            assertContains(ordinaryWorker.originalBytes.toString(Charsets.UTF_8), "navigator.serviceWorker")
            assertContains(fixture.state(), "WORKER_SCRIPTS=0")
            assertContains(fixture.state(), "WORKER_PROBE_SCRIPTS=1")
        } finally {
            ChromePhotosDataPlaneRuntimeAttestation.clear()
        }
    }

    @Test
    fun `fixture records worker realm registration outcome separately from window outcome`() {
        val fixture = ChromeServiceWorkerBoundaryFixture()
        val workerEvent =
            assertNotNull(
                fixture.responseFor(
                    ChromePhotosProxyRequest(
                        method = "POST",
                        target = "/web20sw/event",
                        body = "v1|RESET_VERIFY|WORKER_REGISTER_RESULT|BLOCKED".toByteArray(),
                    ),
                ),
            )

        assertEquals(204, workerEvent.statusCode)
        assertContains(fixture.state(), "WORKER_REGISTER_BLOCKED=1")
        assertContains(fixture.state(), "WORKER_REGISTER_SUCCEEDED=0")
    }

    private fun request(vararg headers: ChromeHttpHeader): ChromePhotosProxyRequest =
        ChromePhotosProxyRequest(
            method = "GET",
            target = "/sw.js",
            headers = headers.toList(),
        )
}
