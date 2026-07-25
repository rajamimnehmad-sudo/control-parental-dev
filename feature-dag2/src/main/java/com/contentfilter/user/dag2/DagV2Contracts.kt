package com.contentfilter.user.dag2

import java.net.URI
import java.util.Locale

enum class DagV2SiteDecision {
    Allow,
    Block,
}

data class DagV2PolicyResult(
    val decision: DagV2SiteDecision,
    val reason: String,
)

data class DagV2SearchResult(
    val title: String,
    val url: String,
    val description: String,
)

sealed interface DagV2SearchOutcome {
    data class Success(val results: List<DagV2SearchResult>) : DagV2SearchOutcome

    data class Failure(val message: String) : DagV2SearchOutcome
}

enum class DagV2ResourceKind {
    MainDocument,
    NonVisual,
    RasterImage,
    SvgImage,
    Media,
    Subframe,
    Unknown,
}

enum class DagV2ResourceSource {
    WebView,
    ServiceWorker,
}

data class DagV2ResourceRequest(
    val url: String,
    val headers: Map<String, String>,
    val isForMainFrame: Boolean,
    val source: DagV2ResourceSource,
    val sessionId: String? = null,
    val navigationToken: String? = null,
)

sealed interface DagV2ResourceRoute {
    data object Bypass : DagV2ResourceRoute

    data object VisualPipeline : DagV2ResourceRoute

    data object Block : DagV2ResourceRoute
}

internal fun String.normalizedDagV2Host(): String =
    runCatching { URI(this).host.orEmpty() }
        .getOrDefault("")
        .lowercase(Locale.ROOT)
        .removePrefix("www.")
        .removeSuffix(".")

internal fun String.isHttpsDagV2Url(): Boolean =
    runCatching {
        val uri = URI(this)
        uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
    }.getOrDefault(false)
