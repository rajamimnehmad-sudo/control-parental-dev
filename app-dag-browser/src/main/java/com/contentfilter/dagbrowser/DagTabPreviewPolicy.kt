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
        restricted: Boolean,
    ): Boolean =
        viewVisible &&
            sessionOpen &&
            pageVisible &&
            eligibilityConfirmed &&
            !restricted

    fun acceptsResult(
        request: DagTabPreviewRequest,
        currentTabId: Long,
        currentRevision: Long,
        pageVisible: Boolean,
        restricted: Boolean,
    ): Boolean =
        pageVisible &&
            !restricted &&
            request.tabId == currentTabId &&
            request.revision == currentRevision
}
