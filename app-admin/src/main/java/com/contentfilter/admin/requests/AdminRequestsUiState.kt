package com.contentfilter.admin.requests

import com.contentfilter.core.domain.model.AccessRequest

data class AdminRequestsUiState(
    val requests: List<AccessRequest> = emptyList(),
    val offlineMode: Boolean = true,
    val message: String = "",
    val lastSyncMessage: String = "",
    val isLoading: Boolean = false,
)
