package com.contentfilter.admin.dashboard

data class DashboardUiState(
    val deviceCount: Int = 0,
    val pendingRequests: Int = 0,
    val syncState: String = "Unknown",
    val systemState: String = "Unknown",
    val lastSync: String = "Sin datos",
    val offlineMode: Boolean = true,
    val showDevTools: Boolean = false,
    val devToolsBusy: Boolean = false,
    val devToolsMessage: String = "",
)
