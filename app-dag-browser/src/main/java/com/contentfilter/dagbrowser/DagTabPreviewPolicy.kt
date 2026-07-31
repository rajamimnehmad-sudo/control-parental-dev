package com.contentfilter.dagbrowser

internal data class DagTabPreviewRequest(
    val tabId: Long,
    val revision: Long,
)

internal object DagTabPreviewPolicy {
    fun canCapture(
        viewVisible: Boolean,
        sessionOpen: Boolean,
        pageVisible: Boolean,
        eligibilityConfirmed: Boolean,
    ): Boolean =
        viewVisible &&
            sessionOpen &&
            pageVisible &&
            eligibilityConfirmed

    fun acceptsResult(
        request: DagTabPreviewRequest,
        currentTabId: Long,
        currentRevision: Long,
        pageVisible: Boolean,
    ): Boolean =
        pageVisible &&
            request.tabId == currentTabId &&
            request.revision == currentRevision
}
