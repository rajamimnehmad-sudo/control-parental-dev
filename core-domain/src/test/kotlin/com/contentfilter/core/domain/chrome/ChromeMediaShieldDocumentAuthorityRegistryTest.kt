package com.contentfilter.core.domain.chrome

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChromeMediaShieldDocumentAuthorityRegistryTest {
    @After
    fun tearDown() = ChromeMediaShieldDocumentAuthorityRegistry.clear()

    @Test
    fun `issued top-level token claims strictly newer lifecycle while replay is fail closed`() {
        ChromeMediaShieldDocumentAuthorityRegistry.beginSession(Session, PolicyEpoch)
        val issued =
            requireNotNull(ChromeMediaShieldDocumentAuthorityRegistry.issue(Session, PolicyEpoch, TopToken, true))

        assertEquals(
            ChromeMediaShieldReadyClaimResult.Claimed(ChromeMediaShieldReadyClaim(issued, 1L)),
            ChromeMediaShieldDocumentAuthorityRegistry.claimReady(TopToken, 1L),
        )
        assertInvalid(ChromeMediaShieldDocumentAuthorityRegistry.claimReady(TopToken, 1L))
        assertEquals(
            ChromeMediaShieldReadyClaimResult.Claimed(ChromeMediaShieldReadyClaim(issued, 3L)),
            ChromeMediaShieldDocumentAuthorityRegistry.claimReady(TopToken, 3L),
        )
        assertInvalid(ChromeMediaShieldDocumentAuthorityRegistry.claimReady(TopToken, 2L))
    }

    @Test
    fun `subdocument can claim its boot but can never resolve foreground authority`() {
        ChromeMediaShieldDocumentAuthorityRegistry.beginSession(Session, PolicyEpoch)
        val frame =
            requireNotNull(ChromeMediaShieldDocumentAuthorityRegistry.issue(Session, PolicyEpoch, FrameToken, false))
        assertEquals(
            ChromeMediaShieldReadyClaimResult.Claimed(ChromeMediaShieldReadyClaim(frame, 1L)),
            ChromeMediaShieldDocumentAuthorityRegistry.claimReady(FrameToken, 1L),
        )

        assertNull(
            ChromeMediaShieldDocumentAuthorityRegistry.resolveClaimedForeground(
                Session,
                PolicyEpoch,
                frame.tokenDigest,
                1L,
                accessibilityContext(windowId = 17),
            ),
        )
    }

    @Test
    fun `top-level claim rejects a subdocument without consuming its lifecycle`() {
        ChromeMediaShieldDocumentAuthorityRegistry.beginSession(Session, PolicyEpoch)
        val frame =
            requireNotNull(ChromeMediaShieldDocumentAuthorityRegistry.issue(Session, PolicyEpoch, FrameToken, false))

        assertInvalid(ChromeMediaShieldDocumentAuthorityRegistry.claimTopLevelReady(FrameToken, 1L))
        assertEquals(
            ChromeMediaShieldReadyClaimResult.Claimed(ChromeMediaShieldReadyClaim(frame, 1L)),
            ChromeMediaShieldDocumentAuthorityRegistry.claimReady(FrameToken, 1L),
        )
    }

    @Test
    fun `claimed foreground resolves only exact session epoch digest lifecycle and valid AX context`() {
        ChromeMediaShieldDocumentAuthorityRegistry.beginSession(Session, PolicyEpoch)
        val issued =
            requireNotNull(ChromeMediaShieldDocumentAuthorityRegistry.issue(Session, PolicyEpoch, TopToken, true))
        assertTrue(
            ChromeMediaShieldDocumentAuthorityRegistry.claimReady(
                TopToken,
                1L,
            ) is ChromeMediaShieldReadyClaimResult.Claimed,
        )
        val context = accessibilityContext(windowId = 17)

        assertEquals(
            issued,
            ChromeMediaShieldDocumentAuthorityRegistry.resolveClaimedForeground(
                Session,
                PolicyEpoch,
                issued.tokenDigest,
                1L,
                context,
            ),
        )
        assertNull(
            ChromeMediaShieldDocumentAuthorityRegistry.resolveClaimedForeground(
                "other",
                PolicyEpoch,
                issued.tokenDigest,
                1L,
                context,
            ),
        )
        assertNull(
            ChromeMediaShieldDocumentAuthorityRegistry.resolveClaimedForeground(
                Session,
                PolicyEpoch + 1L,
                issued.tokenDigest,
                1L,
                context,
            ),
        )
        assertNull(
            ChromeMediaShieldDocumentAuthorityRegistry.resolveClaimedForeground(
                Session,
                PolicyEpoch,
                issued.tokenDigest,
                2L,
                context,
            ),
        )
        assertNull(
            ChromeMediaShieldDocumentAuthorityRegistry.resolveClaimedForeground(
                Session,
                PolicyEpoch,
                "bad",
                1L,
                context,
            ),
        )
        assertNull(
            ChromeMediaShieldDocumentAuthorityRegistry.resolveClaimedForeground(
                Session,
                PolicyEpoch,
                issued.tokenDigest,
                1L,
                context.copy(windowId = -1),
            ),
        )
    }

    @Test
    fun `multiple transformed tabs require explicit independent claims rather than latest wins`() {
        ChromeMediaShieldDocumentAuthorityRegistry.beginSession(Session, PolicyEpoch)
        val first =
            requireNotNull(ChromeMediaShieldDocumentAuthorityRegistry.issue(Session, PolicyEpoch, TopToken, true))
        val second =
            requireNotNull(ChromeMediaShieldDocumentAuthorityRegistry.issue(Session, PolicyEpoch, NextToken, true))

        assertEquals(first.navigationSequence + 1L, second.navigationSequence)
        assertEquals(
            ChromeMediaShieldReadyClaimResult.Claimed(ChromeMediaShieldReadyClaim(first, 1L)),
            ChromeMediaShieldDocumentAuthorityRegistry.claimReady(TopToken, 1L),
        )
        assertEquals(
            ChromeMediaShieldReadyClaimResult.Claimed(ChromeMediaShieldReadyClaim(second, 1L)),
            ChromeMediaShieldDocumentAuthorityRegistry.claimReady(NextToken, 1L),
        )
    }

    @Test
    fun `malformed unissued and stale session claims fail closed`() {
        ChromeMediaShieldDocumentAuthorityRegistry.beginSession(Session, PolicyEpoch)
        ChromeMediaShieldDocumentAuthorityRegistry.issue(Session, PolicyEpoch, TopToken, true)

        assertInvalid(ChromeMediaShieldDocumentAuthorityRegistry.claimReady("short", 1L))
        assertInvalid(ChromeMediaShieldDocumentAuthorityRegistry.claimReady(NextToken, 1L))
        assertInvalid(ChromeMediaShieldDocumentAuthorityRegistry.claimReady(TopToken, 0L))
        ChromeMediaShieldDocumentAuthorityRegistry.beginSession("replacement", PolicyEpoch + 1L)
        assertInvalid(ChromeMediaShieldDocumentAuthorityRegistry.claimReady(TopToken, 1L))
    }

    @Test
    fun `registry is bounded and clear is process-death fail-close`() {
        ChromeMediaShieldDocumentAuthorityRegistry.beginSession(Session, PolicyEpoch)
        repeat(240) { index ->
            ChromeMediaShieldDocumentAuthorityRegistry.issue(
                Session,
                PolicyEpoch,
                token(index),
                topLevel = index % 3 == 0,
            )
        }
        assertTrue(ChromeMediaShieldDocumentAuthorityRegistry.snapshot().issuedDocuments <= 128)

        ChromeMediaShieldDocumentAuthorityRegistry.clear()

        assertEquals(
            ChromeMediaShieldDocumentAuthoritySnapshot(),
            ChromeMediaShieldDocumentAuthorityRegistry.snapshot(),
        )
        assertInvalid(ChromeMediaShieldDocumentAuthorityRegistry.claimReady(token(239), 1L))
    }

    private fun assertInvalid(result: ChromeMediaShieldReadyClaimResult) {
        assertTrue(result is ChromeMediaShieldReadyClaimResult.Invalid)
    }

    private fun token(index: Int): String = "AAAAAAAAAAAAAAAAAAAAAA${index.toString().padStart(4, '0')}"

    private fun accessibilityContext(windowId: Int) =
        ChromeMediaShieldAccessibilityContext(
            windowId = windowId,
            rootIdentityDigest = ChromeMediaShieldDocumentAuthorityRegistry.digestReadyToken("root"),
            markerIdentityDigest = ChromeMediaShieldDocumentAuthorityRegistry.digestReadyToken("marker"),
        )

    private companion object {
        const val Session = "session-h19"
        const val PolicyEpoch = 19L
        const val FrameToken = "BBBBBBBBBBBBBBBBBBBBBB"
        const val TopToken = "CCCCCCCCCCCCCCCCCCCCCC"
        const val NextToken = "DDDDDDDDDDDDDDDDDDDDDD"
    }
}
