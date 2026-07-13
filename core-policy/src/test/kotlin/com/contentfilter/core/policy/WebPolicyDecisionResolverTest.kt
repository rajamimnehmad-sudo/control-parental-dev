package com.contentfilter.core.policy

import com.contentfilter.core.domain.model.WebPolicyDecision
import com.contentfilter.core.domain.model.WebPolicyDecisionSource
import com.contentfilter.core.domain.model.WebPolicyOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class WebPolicyDecisionResolverTest {
    private val resolver = WebPolicyDecisionResolver()

    @Test
    fun `source priorities follow the web protection epic`() {
        val sourcesInAscendingPriority =
            listOf(
                WebPolicyDecisionSource.DefaultPolicy,
                WebPolicyDecisionSource.LocalSearchClassifier,
                WebPolicyDecisionSource.LocalDomainClassifier,
                WebPolicyDecisionSource.SignedDomainList,
                WebPolicyDecisionSource.TechnicalAllowlist,
                WebPolicyDecisionSource.AdministratorRule,
                WebPolicyDecisionSource.PlatformPolicy,
            )

        sourcesInAscendingPriority.zipWithNext().forEach { (lower, higher) ->
            val selected = resolver.resolve(listOf(decision(lower), decision(higher)), Now)

            assertEquals(higher, selected?.source)
        }
    }

    @Test
    fun `older response from the same layer cannot replace a newer version`() {
        val current = decision(WebPolicyDecisionSource.SignedDomainList, version = 9)
        val stale =
            decision(
                source = WebPolicyDecisionSource.SignedDomainList,
                outcome = WebPolicyOutcome.Allow,
                version = 8,
                evaluatedAtEpochMillis = Now + 1_000,
            )

        assertSame(current, resolver.resolve(listOf(current, stale), Now))
    }

    @Test
    fun `newer response from the same layer replaces the current version`() {
        val current = decision(WebPolicyDecisionSource.SignedDomainList, version = 9)
        val updated =
            decision(
                source = WebPolicyDecisionSource.SignedDomainList,
                outcome = WebPolicyOutcome.Allow,
                version = 10,
            )

        assertSame(updated, resolver.resolve(listOf(current, updated), Now))
    }

    @Test
    fun `resolver returns the selected layer object without modifying another layer`() {
        val administrator = decision(WebPolicyDecisionSource.AdministratorRule)
        val list =
            decision(source = WebPolicyDecisionSource.SignedDomainList).copy(
                category = "list-category",
                confidence = 0.91,
            )

        val selected = resolver.resolve(listOf(list, administrator), Now)

        assertSame(administrator, selected)
        assertEquals("list-category", list.category)
        assertEquals(0.91, list.confidence)
    }

    @Test
    fun `expired response cannot override an active lower layer`() {
        val expiredAdministrator =
            decision(
                source = WebPolicyDecisionSource.AdministratorRule,
                expiresAtEpochMillis = Now,
            )
        val activeList = decision(WebPolicyDecisionSource.SignedDomainList)

        assertSame(activeList, resolver.resolve(listOf(expiredAdministrator, activeList), Now))
    }

    @Test
    fun `returns null when no active response remains`() {
        val expired = decision(WebPolicyDecisionSource.PlatformPolicy, expiresAtEpochMillis = Now)

        assertNull(resolver.resolve(listOf(expired), Now))
    }

    @Test
    fun `same source and version uses the latest evaluation`() {
        val older = decision(WebPolicyDecisionSource.LocalDomainClassifier, evaluatedAtEpochMillis = Now - 2)
        val newer =
            decision(
                source = WebPolicyDecisionSource.LocalDomainClassifier,
                outcome = WebPolicyOutcome.Uncertain,
                evaluatedAtEpochMillis = Now - 1,
            )

        assertSame(newer, resolver.resolve(listOf(older, newer), Now))
    }

    @Test
    fun `conflicting responses with identical freshness prefer review`() {
        val candidates =
            WebPolicyOutcome.entries.map { outcome ->
                decision(
                    source = WebPolicyDecisionSource.LocalDomainClassifier,
                    outcome = outcome,
                )
            }

        assertEquals(WebPolicyOutcome.RequireReview, resolver.resolve(candidates, Now)?.outcome)
    }

    private fun decision(
        source: WebPolicyDecisionSource,
        outcome: WebPolicyOutcome = WebPolicyOutcome.Block,
        version: Long = 1,
        evaluatedAtEpochMillis: Long = Now - 1,
        expiresAtEpochMillis: Long? = null,
    ): WebPolicyDecision =
        WebPolicyDecision(
            outcome = outcome,
            category = "test-category",
            confidence = 1.0,
            source = source,
            version = version,
            technicalReason = "test-decision",
            evaluatedAtEpochMillis = evaluatedAtEpochMillis,
            expiresAtEpochMillis = expiresAtEpochMillis,
        )

    private companion object {
        const val Now = 10_000L
    }
}
