package com.contentfilter.user.chromedataplane

import com.contentfilter.core.domain.chrome.ChromeMediaShieldDocumentAuthorityRegistry
import com.contentfilter.core.domain.chrome.ChromePhotosDataPlaneRuntimeAttestation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChromePhotosDataPlaneLifecycleTest {
    @Test
    fun `presentation requires listening proxy`() {
        val lifecycle = ChromePhotosDataPlaneLifecycle()

        lifecycle.begin()
        assertFailsWith<IllegalStateException> { lifecycle.presentationReady() }
        lifecycle.proxyReady()
        assertEquals(ChromePhotosDataPlanePhase.PresentationReady, lifecycle.presentationReady())
    }

    @Test
    fun `failure can restart but stopped cannot become ready`() {
        val lifecycle = ChromePhotosDataPlaneLifecycle()

        lifecycle.begin()
        lifecycle.fail()
        lifecycle.begin()
        lifecycle.proxyReady()
        lifecycle.stop()

        assertEquals(ChromePhotosDataPlanePhase.Stopped, lifecycle.current())
        assertFailsWith<IllegalStateException> { lifecycle.presentationReady() }
    }

    @Test
    fun `fixture expiry preserves independently healthy real web presentation`() {
        assertFalse(shouldClearPresentationReadyAfterFixtureScopeLoss(realWebReady = true))
        assertTrue(shouldClearPresentationReadyAfterFixtureScopeLoss(realWebReady = false))
    }

    @Test
    fun `Chrome policy is scoped to fixed loopback proxy`() {
        assertEquals(
            """{"ProxyMode":"fixed_servers","ProxyServer":"127.0.0.1:8877","ProxyBypassList":"<-loopback>"}""",
            ChromePhotosChromePolicy.proxySettingsJson(),
        )
    }

    @Test
    fun `terminal startup cleanup removes document and runtime authority`() {
        ChromePhotosDataPlaneRuntimeAttestation.beginSession(
            sessionId = "h19-cleanup-session",
            mediaAuthorityEnabled = true,
            mediaPolicyEpoch = 19L,
        )
        ChromeMediaShieldDocumentAuthorityRegistry.beginSession("h19-cleanup-session", 19L)
        ChromeMediaShieldDocumentAuthorityRegistry.issue(
            sessionId = "h19-cleanup-session",
            epoch = 19L,
            readyToken = "abcdefghijklmnopqrstuv",
            topLevel = true,
        )

        clearChromePhotosDataPlaneAuthorityState()

        assertEquals("", ChromePhotosDataPlaneRuntimeAttestation.snapshot().sessionId)
        assertEquals(0, ChromeMediaShieldDocumentAuthorityRegistry.snapshot().issuedDocuments)
        assertEquals("", ChromeMediaShieldDocumentAuthorityRegistry.snapshot().protectionSessionId)
    }
}
