package com.contentfilter.user.dag2

enum class DagV2CanonicalTextDecision {
    Allowed,
    Blocked,
    Uncertain,
}

data class DagV2CanonicalTextResult(
    val decision: DagV2CanonicalTextDecision,
    val category: String,
)

interface DagV2CanonicalTextPolicy {
    fun classifyQuery(query: String): DagV2CanonicalTextResult

    fun classifyNavigation(url: String): DagV2CanonicalTextResult

    fun classifyResult(result: DagV2SearchResult): DagV2CanonicalTextResult

    fun classifyPage(
        url: String,
        title: String,
        visibleText: String,
    ): DagV2CanonicalTextResult
}
