package com.contentfilter.admin.devices

data class DevicesUiState(
    val devices: List<AdminDeviceItem> = emptyList(),
    val pairingCode: String = "",
    val pairingExpiresAt: String = "",
    val loading: Boolean = false,
    val offlineMode: Boolean = true,
    val message: String = "",
)
