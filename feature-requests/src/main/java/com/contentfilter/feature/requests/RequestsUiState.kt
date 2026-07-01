package com.contentfilter.feature.requests

import com.contentfilter.core.domain.model.AccessRequest
import com.contentfilter.core.domain.model.ExtraTimeGrant

data class RequestsUiState(
    val target: String = "",
    val reason: String = "",
    val minutes: String = "15",
    val pendingCount: Int = 0,
    val message: String = "Las solicitudes se guardan localmente y se sincronizarán cuando sea posible.",
    val requests: List<AccessRequest> = emptyList(),
    val extraTimeGrants: List<ExtraTimeGrant> = emptyList(),
)
