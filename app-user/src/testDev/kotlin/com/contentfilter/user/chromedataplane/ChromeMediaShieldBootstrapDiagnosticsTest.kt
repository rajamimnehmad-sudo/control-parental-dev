package com.contentfilter.user.chromedataplane

import com.contentfilter.core.domain.chrome.ChromeMediaShieldDocumentAuthorityRegistry
import com.contentfilter.core.domain.chrome.ChromeMediaShieldSelfReadyIdentity
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChromeMediaShieldBootstrapDiagnosticsTest {
    @AfterTest
    fun tearDown() = ChromeMediaShieldDocumentAuthorityRegistry.clear()

    @Test
    fun `records exact issued generation once without claiming SELF_READY`() {
        ChromeMediaShieldDocumentAuthorityRegistry.beginSession(Session, Epoch)
        val issued = requireNotNull(ChromeMediaShieldDocumentAuthorityRegistry.issue(Session, Epoch, Token, true))
        val identity = issued.selfReady()
        val diagnostics = ChromeMediaShieldBootstrapDiagnostics()

        assertTrue(diagnostics.record(Token, identity, "INSTALL", "STYLESHEET_CAPABILITIES"))
        assertFalse(diagnostics.record(Token, identity, "INSTALL", "CSSOM_GUARDS"))
        assertFalse(diagnostics.record(Token, identity.copy(documentSequence = 99L), "INSTALL", "CSSOM_GUARDS"))
        assertFalse(diagnostics.record(Token, identity, "bad", "CSSOM_GUARDS"))
        assertEquals(0, ChromeMediaShieldDocumentAuthorityRegistry.snapshot().readyClaims)
        assertEquals(
            ChromeMediaShieldBootstrapDiagnosticMetrics(
                accepted = 1L,
                rejected = 3L,
                lastStage = "INSTALL",
                lastReason = "STYLESHEET_CAPABILITIES",
                outstanding = 1,
            ),
            diagnostics.metrics(),
        )
    }

    private fun com.contentfilter.core.domain.chrome.ChromeMediaShieldDocumentIdentity.selfReady() =
        ChromeMediaShieldSelfReadyIdentity(
            protectionSessionId,
            policyEpoch,
            navigationSequence,
            documentSequence,
            1L,
            topLevel,
        )

    private companion object {
        const val Session = "h20-bootstrap-diagnostic"
        const val Epoch = 20L
        const val Token = "AAAAAAAAAAAAAAAAAAAAAA"
    }
}
