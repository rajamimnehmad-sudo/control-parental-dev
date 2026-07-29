package com.contentfilter.core.domain.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProtectedBrowserPolicyTest {
    @Test
    fun `DAG preference requires one enabled canonical rule`() {
        val enabled =
            PolicyRule(
                id = "dag",
                scope = RuleScope.Domain,
                target = ProtectedBrowserPolicy.RuleTarget,
                action = RuleAction.Allow,
                priority = ProtectedBrowserPolicy.RulePriority,
                enabled = true,
            )

        assertTrue(listOf(enabled).protectedBrowserRequired())
        assertFalse(listOf(enabled.copy(enabled = false)).protectedBrowserRequired())
        assertFalse(listOf(enabled.copy(action = RuleAction.Block)).protectedBrowserRequired())
    }

    @Test
    fun `protected browser is never classified as an alternative`() {
        assertTrue(ProtectedBrowserPolicy.isProtectedBrowser(ProtectedBrowserPolicy.DevPackageName))
        assertFalse(
            ProtectedBrowserPolicy.isKnownAlternativeBrowser(
                ProtectedBrowserPolicy.DevPackageName,
            ),
        )
        assertTrue(ProtectedBrowserPolicy.isKnownAlternativeBrowser("com.android.chrome"))
    }
}
