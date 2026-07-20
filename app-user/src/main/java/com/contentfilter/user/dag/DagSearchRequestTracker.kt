package com.contentfilter.user.dag

internal data class DagSearchRequest(
    val query: String,
    val page: Int,
    val append: Boolean,
)

internal class DagSearchRequestTracker {
    private var revision = 0L
    private var activeRequest: DagSearchRequest? = null

    fun begin(request: DagSearchRequest): Long? {
        if (request == activeRequest) return null
        activeRequest = request
        return ++revision
    }

    fun isCurrent(requestId: Long): Boolean = requestId == revision && activeRequest != null

    fun complete(requestId: Long) {
        if (isCurrent(requestId)) activeRequest = null
    }

    fun cancel() {
        revision += 1
        activeRequest = null
    }
}
