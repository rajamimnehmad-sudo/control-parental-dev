package com.contentfilter.core.domain.chrome

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

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
    fun `self ready accepts only exact owning document and rejects replay`() {
        ChromeMediaShieldDocumentAuthorityRegistry.beginSession(Session, PolicyEpoch)
        val issued =
            requireNotNull(ChromeMediaShieldDocumentAuthorityRegistry.issue(Session, PolicyEpoch, TopToken, true))
        val expected = issued.selfReady(lifecycle = 1L)

        assertEquals(
            ChromeMediaShieldReadyClaimResult.Claimed(ChromeMediaShieldReadyClaim(issued, 1L)),
            ChromeMediaShieldDocumentAuthorityRegistry.claimSelfReady(TopToken, expected),
        )
        assertInvalid(ChromeMediaShieldDocumentAuthorityRegistry.claimSelfReady(TopToken, expected))
        assertInvalid(
            ChromeMediaShieldDocumentAuthorityRegistry.claimSelfReady(
                TopToken,
                expected.copy(documentSequence = expected.documentSequence + 1L, lifecycleSequence = 2L),
            ),
        )
        assertEquals(
            ChromeMediaShieldReadyClaimResult.Claimed(ChromeMediaShieldReadyClaim(issued, 2L)),
            ChromeMediaShieldDocumentAuthorityRegistry.claimSelfReady(
                TopToken,
                expected.copy(lifecycleSequence = 2L),
            ),
        )
    }

    @Test
    fun `bootstrap diagnostic validates exact unclaimed document without consuming authority`() {
        ChromeMediaShieldDocumentAuthorityRegistry.beginSession(Session, PolicyEpoch)
        val issued = requireNotNull(ChromeMediaShieldDocumentAuthorityRegistry.issue(Session, PolicyEpoch, TopToken, true))
        val expected = issued.selfReady()

        assertTrue(ChromeMediaShieldDocumentAuthorityRegistry.validatesUnclaimedSelfReady(TopToken, expected))
        assertFalse(
            ChromeMediaShieldDocumentAuthorityRegistry.validatesUnclaimedSelfReady(
                TopToken,
                expected.copy(documentSequence = expected.documentSequence + 1L),
            ),
        )
        assertEquals(0, ChromeMediaShieldDocumentAuthorityRegistry.snapshot().readyClaims)
        assertEquals(
            ChromeMediaShieldReadyClaimResult.Claimed(ChromeMediaShieldReadyClaim(issued, 1L)),
            ChromeMediaShieldDocumentAuthorityRegistry.claimSelfReady(TopToken, expected),
        )
        assertFalse(ChromeMediaShieldDocumentAuthorityRegistry.validatesUnclaimedSelfReady(TopToken, expected))
    }

    @Test
    fun `independent documents can self ready but neither token can claim the other`() {
        ChromeMediaShieldDocumentAuthorityRegistry.beginSession(Session, PolicyEpoch)
        val first =
            requireNotNull(ChromeMediaShieldDocumentAuthorityRegistry.issue(Session, PolicyEpoch, TopToken, true))
        val second =
            requireNotNull(ChromeMediaShieldDocumentAuthorityRegistry.issue(Session, PolicyEpoch, NextToken, true))

        assertInvalid(ChromeMediaShieldDocumentAuthorityRegistry.claimSelfReady(TopToken, second.selfReady()))
        assertInvalid(ChromeMediaShieldDocumentAuthorityRegistry.claimSelfReady(NextToken, first.selfReady()))
        assertEquals(
            ChromeMediaShieldReadyClaimResult.Claimed(ChromeMediaShieldReadyClaim(first, 1L)),
            ChromeMediaShieldDocumentAuthorityRegistry.claimSelfReady(TopToken, first.selfReady()),
        )
        assertEquals(
            ChromeMediaShieldReadyClaimResult.Claimed(ChromeMediaShieldReadyClaim(second, 1L)),
            ChromeMediaShieldDocumentAuthorityRegistry.claimSelfReady(NextToken, second.selfReady()),
        )
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
    fun `prove and present resolve only the exact already claimed top-level lifecycle`() {
        ChromeMediaShieldDocumentAuthorityRegistry.beginSession(Session, PolicyEpoch)
        val issued =
            requireNotNull(ChromeMediaShieldDocumentAuthorityRegistry.issue(Session, PolicyEpoch, TopToken, true))
        val first = claimTopLevel(TopToken, 1L)

        assertEquals(first, ChromeMediaShieldDocumentAuthorityRegistry.resolveTopLevelReady(TopToken, 1L))
        assertNull(ChromeMediaShieldDocumentAuthorityRegistry.resolveTopLevelReady(TopToken, 2L))
        assertNull(ChromeMediaShieldDocumentAuthorityRegistry.resolveTopLevelReady(FrameToken, 1L))

        val second = claimTopLevel(TopToken, 2L)
        assertEquals(issued, second.identity)
        assertNull(ChromeMediaShieldDocumentAuthorityRegistry.resolveTopLevelReady(TopToken, 1L))
        assertEquals(second, ChromeMediaShieldDocumentAuthorityRegistry.resolveTopLevelReady(TopToken, 2L))
    }

    @Test
    fun `active document commit is atomic with lifecycle replacement and session invalidation`() {
        ChromeMediaShieldDocumentAuthorityRegistry.beginSession(Session, PolicyEpoch)
        ChromeMediaShieldDocumentAuthorityRegistry.issue(Session, PolicyEpoch, TopToken, true)
        val claim = claimTopLevel(TopToken, 1L)
        var commits = 0

        assertTrue(
            ChromeMediaShieldDocumentAuthorityRegistry.commitIfTopLevelReadyCurrent(claim) {
                commits += 1
                true
            },
        )
        claimTopLevel(TopToken, 2L)
        assertFalse(
            ChromeMediaShieldDocumentAuthorityRegistry.commitIfTopLevelReadyCurrent(claim) {
                commits += 1
                true
            },
        )
        ChromeMediaShieldDocumentAuthorityRegistry.beginSession("replacement", PolicyEpoch + 1L)
        assertFalse(
            ChromeMediaShieldDocumentAuthorityRegistry.commitIfTopLevelReadyCurrent(claim) {
                commits += 1
                true
            },
        )
        assertEquals(1, commits)
    }

    @Test
    fun `network claim alone has no foreground authority and exact AX activation is required`() {
        ChromeMediaShieldDocumentAuthorityRegistry.beginSession(Session, PolicyEpoch)
        val issued =
            requireNotNull(ChromeMediaShieldDocumentAuthorityRegistry.issue(Session, PolicyEpoch, TopToken, true))
        val claim = claimTopLevel(TopToken, 1L)
        val context = accessibilityContext(windowId = 17)

        assertNull(resolve(claim, context))
        val activation =
            requireNotNull(
                ChromeMediaShieldDocumentAuthorityRegistry.activateClaimedForeground(
                    claim,
                    context,
                ),
            )
        assertEquals(claim, activation.claim)
        assertEquals(context, activation.accessibilityContext)
        assertEquals(
            issued,
            resolve(claim, context),
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
        assertNull(resolve(claim, context.copy(markerIdentityDigest = digest("replacement"))))
    }

    @Test
    fun `multiple transformed tabs require explicit independent claims rather than latest wins`() {
        ChromeMediaShieldDocumentAuthorityRegistry.beginSession(Session, PolicyEpoch)
        val first =
            requireNotNull(ChromeMediaShieldDocumentAuthorityRegistry.issue(Session, PolicyEpoch, TopToken, true))
        val second =
            requireNotNull(ChromeMediaShieldDocumentAuthorityRegistry.issue(Session, PolicyEpoch, NextToken, true))

        assertEquals(first.navigationSequence + 1L, second.navigationSequence)
        val firstClaim = claimTopLevel(TopToken, 1L)
        val secondClaim = claimTopLevel(NextToken, 1L)
        val firstContext = accessibilityContext(windowId = 17)
        val secondContext = accessibilityContext(windowId = 18)

        assertNull(resolve(firstClaim, firstContext))
        assertNull(resolve(secondClaim, secondContext))
        assertTrue(
            ChromeMediaShieldDocumentAuthorityRegistry.activateClaimedForeground(
                firstClaim,
                firstContext,
            ) != null,
        )
        assertEquals(first, resolve(firstClaim, firstContext))
        assertTrue(
            ChromeMediaShieldDocumentAuthorityRegistry.activateClaimedForeground(
                secondClaim,
                secondContext,
            ) != null,
        )
        assertNull(
            resolve(firstClaim, firstContext),
        )
        assertEquals(second, resolve(secondClaim, secondContext))
    }

    @Test
    fun `foreground claim can reactivate an issued bfcache document while older claim loses authority`() {
        ChromeMediaShieldDocumentAuthorityRegistry.beginSession(Session, PolicyEpoch)
        val first =
            requireNotNull(ChromeMediaShieldDocumentAuthorityRegistry.issue(Session, PolicyEpoch, TopToken, true))
        val second =
            requireNotNull(ChromeMediaShieldDocumentAuthorityRegistry.issue(Session, PolicyEpoch, NextToken, true))
        val context = accessibilityContext(windowId = 17)

        val firstClaim = claimTopLevel(TopToken, 1L)
        requireNotNull(ChromeMediaShieldDocumentAuthorityRegistry.activateClaimedForeground(firstClaim, context))
        assertEquals(
            first,
            resolve(firstClaim, context),
        )

        val secondClaim = claimTopLevel(NextToken, 1L)
        assertEquals(first, resolve(firstClaim, context))
        requireNotNull(ChromeMediaShieldDocumentAuthorityRegistry.activateClaimedForeground(secondClaim, context))
        assertNull(
            resolve(firstClaim, context),
        )
        assertEquals(
            second,
            resolve(secondClaim, context),
        )

        val restoredClaim = claimTopLevel(TopToken, 2L)
        assertNull(resolve(restoredClaim, context))
        requireNotNull(ChromeMediaShieldDocumentAuthorityRegistry.activateClaimedForeground(restoredClaim, context))
        assertEquals(
            first,
            resolve(restoredClaim, context),
        )
        assertNull(
            resolve(secondClaim, context),
        )
    }

    @Test
    fun `final foreground commit is atomic with activation replacement`() {
        ChromeMediaShieldDocumentAuthorityRegistry.beginSession(Session, PolicyEpoch)
        ChromeMediaShieldDocumentAuthorityRegistry.issue(Session, PolicyEpoch, TopToken, true)
        ChromeMediaShieldDocumentAuthorityRegistry.issue(Session, PolicyEpoch, NextToken, true)
        val first = claimTopLevel(TopToken, 1L)
        val second = claimTopLevel(NextToken, 1L)
        val firstContext = accessibilityContext(17)
        val secondContext = accessibilityContext(18)
        requireNotNull(ChromeMediaShieldDocumentAuthorityRegistry.activateClaimedForeground(first, firstContext))
        val commitEntered = CountDownLatch(1)
        val allowCommitToFinish = CountDownLatch(1)
        val replacementFinished = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val committed =
                executor.submit<Boolean> {
                    ChromeMediaShieldDocumentAuthorityRegistry.commitIfClaimedForegroundCurrent(
                        first,
                        firstContext,
                    ) {
                        commitEntered.countDown()
                        allowCommitToFinish.await(TestWaitMillis, TimeUnit.MILLISECONDS)
                    }
                }
            assertTrue(commitEntered.await(TestWaitMillis, TimeUnit.MILLISECONDS))
            executor.execute {
                ChromeMediaShieldDocumentAuthorityRegistry.activateClaimedForeground(second, secondContext)
                replacementFinished.countDown()
            }
            assertFalse(replacementFinished.await(ShortWaitMillis, TimeUnit.MILLISECONDS))
            allowCommitToFinish.countDown()
            assertTrue(committed.get(TestWaitMillis, TimeUnit.MILLISECONDS))
            assertTrue(replacementFinished.await(TestWaitMillis, TimeUnit.MILLISECONDS))
            assertNull(resolve(first, firstContext))
            assertEquals(second.identity, resolve(second, secondContext))
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `invalid stale and deactivated foreground never execute presentation commit`() {
        ChromeMediaShieldDocumentAuthorityRegistry.beginSession(Session, PolicyEpoch)
        ChromeMediaShieldDocumentAuthorityRegistry.issue(Session, PolicyEpoch, TopToken, true)
        val claim = claimTopLevel(TopToken, 1L)
        val context = accessibilityContext(17)
        var commits = 0

        assertFalse(
            ChromeMediaShieldDocumentAuthorityRegistry.commitIfClaimedForegroundCurrent(claim, context) {
                commits += 1
                true
            },
        )
        requireNotNull(ChromeMediaShieldDocumentAuthorityRegistry.activateClaimedForeground(claim, context))
        assertFalse(
            ChromeMediaShieldDocumentAuthorityRegistry.commitIfClaimedForegroundCurrent(
                claim,
                context.copy(windowId = 18),
            ) {
                commits += 1
                true
            },
        )
        assertTrue(ChromeMediaShieldDocumentAuthorityRegistry.deactivateClaimedForeground(claim))
        assertFalse(ChromeMediaShieldDocumentAuthorityRegistry.deactivateClaimedForeground(claim))
        assertFalse(
            ChromeMediaShieldDocumentAuthorityRegistry.commitIfClaimedForegroundCurrent(claim, context) {
                commits += 1
                true
            },
        )
        assertEquals(0, commits)
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

    private fun claimTopLevel(
        token: String,
        lifecycle: Long,
    ): ChromeMediaShieldReadyClaim =
        (
            ChromeMediaShieldDocumentAuthorityRegistry.claimTopLevelReady(token, lifecycle) as
                ChromeMediaShieldReadyClaimResult.Claimed
        ).claim

    private fun resolve(
        claim: ChromeMediaShieldReadyClaim,
        context: ChromeMediaShieldAccessibilityContext,
    ): ChromeMediaShieldDocumentIdentity? =
        ChromeMediaShieldDocumentAuthorityRegistry.resolveClaimedForeground(
            claim.identity.protectionSessionId,
            claim.identity.policyEpoch,
            claim.identity.tokenDigest,
            claim.lifecycleSequence,
            context,
        )

    private fun digest(value: String): String = ChromeMediaShieldDocumentAuthorityRegistry.digestReadyToken(value)

    private fun ChromeMediaShieldDocumentIdentity.selfReady(lifecycle: Long = 1L) =
        ChromeMediaShieldSelfReadyIdentity(
            protectionSessionId = protectionSessionId,
            policyEpoch = policyEpoch,
            navigationSequence = navigationSequence,
            documentSequence = documentSequence,
            lifecycleSequence = lifecycle,
            topLevel = topLevel,
        )

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
        const val TestWaitMillis = 2_000L
        const val ShortWaitMillis = 50L
    }
}
