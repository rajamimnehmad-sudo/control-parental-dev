package com.contentfilter.core.domain.model

/**
 * Lifecycle of a local unlock or extra-time request.
 */
enum class RequestStatus {
    PendingLocal,
    PendingRemote,
    Approved,
    Rejected,
    Expired,
}
