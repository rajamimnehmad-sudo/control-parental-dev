package com.contentfilter.user.dag

import java.net.URI
import java.util.Locale

internal fun dagShouldBlockAdRequest(
    url: String,
    isMainFrame: Boolean,
): Boolean {
    if (isMainFrame) return false
    val uri = runCatching { URI(url) }.getOrNull() ?: return false
    if (!uri.scheme.equals("https", ignoreCase = true)) return false
    val host = uri.host?.lowercase(Locale.ROOT)?.trimEnd('.') ?: return false
    return DagBlockedAdHosts.any { blocked -> host == blocked || host.endsWith(".$blocked") }
}

private val DagBlockedAdHosts =
    setOf(
        "2mdn.net",
        "adnxs.com",
        "adsrvr.org",
        "criteo.com",
        "criteo.net",
        "doubleclick.net",
        "googleadservices.com",
        "googlesyndication.com",
        "outbrain.com",
        "scorecardresearch.com",
        "taboola.com",
    )
