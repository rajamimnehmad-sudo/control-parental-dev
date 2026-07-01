package com.contentfilter.admin.devices

data class AdminDeviceItem(
    val id: String,
    val name: String,
    val user: String,
    val version: String,
    val vpnState: String,
    val accessibilityState: String,
    val lastSync: String,
    val systemState: String,
)
