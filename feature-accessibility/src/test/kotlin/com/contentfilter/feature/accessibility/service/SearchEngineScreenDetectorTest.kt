package com.contentfilter.feature.accessibility.service

import com.contentfilter.core.domain.model.PolicyLevel
import com.contentfilter.core.domain.model.PolicyRule
import com.contentfilter.core.domain.model.PolicySnapshot
import com.contentfilter.core.domain.model.RuleAction
import com.contentfilter.core.domain.model.RuleScope
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchEngineScreenDetectorTest {
    private val detector = SearchEngineScreenDetector()

    @Test
    fun `leaves browser search screen when search engines are blocked`() {
        assertTrue(
            detector.shouldLeaveSearchEngine(
                packageName = "com.android.chrome",
                snapshot = snapshot(rule("google.com", RuleAction.Block)),
                visibleText = "Google Search resultados de busqueda",
            ),
        )
    }

    @Test
    fun `does not leave browser when search engines are allowed`() {
        assertFalse(
            detector.shouldLeaveSearchEngine(
                packageName = "com.android.chrome",
                snapshot = snapshot(rule("google.com", RuleAction.Allow)),
                visibleText = "Google Search resultados de busqueda",
            ),
        )
    }

    @Test
    fun `does not leave non browser app`() {
        assertFalse(
            detector.shouldLeaveSearchEngine(
                packageName = "com.example.app",
                snapshot = snapshot(rule("google.com", RuleAction.Block)),
                visibleText = "Google Search resultados de busqueda",
            ),
        )
    }

    private fun snapshot(vararg rules: PolicyRule): PolicySnapshot =
        PolicySnapshot(
            id = "test",
            version = 1L,
            rules = rules.toList(),
        )

    private fun rule(
        target: String,
        action: RuleAction,
    ): PolicyRule =
        PolicyRule(
            id = "rule-$target-$action",
            level = PolicyLevel.Device,
            scope = RuleScope.Domain,
            target = target,
            action = action,
            priority = 0,
            enabled = true,
        )
}
