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
        previewRestricted: Boolean,
    ): Boolean =
        viewVisible &&
            sessionOpen &&
            pageVisible &&
            eligibilityConfirmed &&
            !previewRestricted

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
