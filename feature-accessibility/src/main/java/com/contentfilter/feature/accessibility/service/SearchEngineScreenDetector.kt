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
    ): Boolean {
        if (packageName !in BrowserPackageNames) return false
        if (!snapshot.searchEnginesBlocked()) return false
        return visibleText.indicatesSearchEngineScreen()
    }

    private fun PolicySnapshot.searchEnginesBlocked(): Boolean =
        rules.any {
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
