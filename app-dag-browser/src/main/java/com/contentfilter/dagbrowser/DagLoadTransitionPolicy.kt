package com.contentfilter.dagbrowser

internal object DagLoadTransitionPolicy {
    fun shouldCover(
        currentUrl: String,
        targetUrl: String,
        targetsCurrentWindow: Boolean,
        pageVisible: Boolean,
        barrierAlreadyWaiting: Boolean,
    ): Boolean {
        if (!targetsCurrentWindow || !pageVisible || barrierAlreadyWaiting) return false
        return currentUrl.substringBefore('#') != targetUrl.substringBefore('#')
    }
}
