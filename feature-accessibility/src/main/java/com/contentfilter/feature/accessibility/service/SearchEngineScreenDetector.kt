package com.contentfilter.feature.accessibility.service

import com.contentfilter.core.domain.model.PolicySnapshot
import com.contentfilter.core.domain.model.RuleAction
import com.contentfilter.core.domain.model.RuleScope
import com.contentfilter.core.domain.model.SearchEngineCatalog

class SearchEngineScreenDetector {
    fun shouldLeaveSearchEngine(
        packageName: String,
        snapshot: PolicySnapshot,
        visibleText: String,
    ): Boolean = diagnose(packageName, snapshot, visibleText).shouldLeave

    fun diagnose(
        packageName: String,
        snapshot: PolicySnapshot,
        visibleText: String,
    ): SearchEngineScreenDiagnosis {
        if (packageName !in BrowserPackageNames) {
            return SearchEngineScreenDiagnosis(
                shouldLeave = false,
                reason = "non-browser",
                searchBlockRules = 0,
                visibleTextLength = visibleText.length,
            )
        }
        val blockRuleCount = snapshot.searchEngineBlockRuleCount()
        if (blockRuleCount == 0) {
            return SearchEngineScreenDiagnosis(
                shouldLeave = false,
                reason = "search-rules-not-blocked",
                searchBlockRules = 0,
                visibleTextLength = visibleText.length,
            )
        }
        if (!visibleText.indicatesSearchEngineScreen()) {
            return SearchEngineScreenDiagnosis(
                shouldLeave = false,
                reason = "no-search-signal",
                searchBlockRules = blockRuleCount,
                visibleTextLength = visibleText.length,
            )
        }
        return SearchEngineScreenDiagnosis(
            shouldLeave = true,
            reason = "blocked-search-screen",
            searchBlockRules = blockRuleCount,
            visibleTextLength = visibleText.length,
        )
    }

    private fun PolicySnapshot.searchEngineBlockRuleCount(): Int =
        rules.count {
            it.enabled &&
                it.scope == RuleScope.Domain &&
                it.action == RuleAction.Block &&
                it.target in SearchEngineCatalog.searchEngineDomains
        }

    private fun String.indicatesSearchEngineScreen(): Boolean {
        val normalized = lowercase()
        if (SearchEngineCatalog.searchEngineDomains.any { it in normalized }) return true
        return SearchEngineSignals.any { it in normalized }
    }

    private companion object {
        val BrowserPackageNames =
            setOf(
                "com.android.chrome",
                "com.sec.android.app.sbrowser",
                "org.mozilla.firefox",
                "org.mozilla.firefox_beta",
                "com.microsoft.emmx",
                "com.brave.browser",
                "com.opera.browser",
                "com.opera.mini.native",
                "com.duckduckgo.mobile.android",
                "com.vivaldi.browser",
                "com.kiwibrowser.browser",
                "com.UCMobile.intl",
                "mark.via.gp",
            )

        val SearchEngineSignals =
            setOf(
                "google search",
                "buscar con google",
                "bing search",
                "yahoo search",
                "duckduckgo search",
                "search results",
                "resultados de búsqueda",
                "resultados de busqueda",
            )
    }
}

data class SearchEngineScreenDiagnosis(
    val shouldLeave: Boolean,
    val reason: String,
    val searchBlockRules: Int,
    val visibleTextLength: Int,
)
