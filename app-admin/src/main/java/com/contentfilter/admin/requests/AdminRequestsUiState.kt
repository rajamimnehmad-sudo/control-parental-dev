package com.contentfilter.admin.requests

import com.contentfilter.core.domain.model.AccessRequest

data class AdminRequestsUiState(
    val requests: List<AccessRequest> = emptyList(),
    val users: List<AdminRequestUserUiState> = emptyList(),
    val selectedDeviceId: String? = null,
    val offlineMode: Boolean = true,
    val message: String = "",
    val lastSyncMessage: String = "",
    val isLoading: Boolean = false,
)

data class AdminRequestUserUiState(
    val deviceId: String,
    val name: String,
    val pendingCount: Int,
) {
    val needsAttention: Boolean = pendingCount > 0
}
