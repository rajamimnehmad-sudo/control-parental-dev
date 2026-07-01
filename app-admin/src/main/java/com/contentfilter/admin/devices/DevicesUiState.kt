package com.contentfilter.admin.devices

data class DevicesUiState(
    val devices: List<AdminDeviceItem> = emptyList(),
    val offlineMode: Boolean = true,
)
