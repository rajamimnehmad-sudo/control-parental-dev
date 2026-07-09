package com.contentfilter.core.domain.model

data class Device(
    val id: String,
    val accountId: String,
    val displayName: String,
    val appRole: String = "user",
    val lastSeenAtEpochMillis: Long? = null,
    val vpnState: ComponentState = ComponentState.Unknown,
    val accessibilityState: ComponentState = ComponentState.Unknown,
    val protectionAlert: String? = null,
    val protectionUpdatedAtEpochMillis: Long? = null,
)
