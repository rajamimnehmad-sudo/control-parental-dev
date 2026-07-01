package com.contentfilter.core.domain.model

data class DeviceActivation(
    val id: String,
    val accountId: String,
    val deviceId: String,
    val activatedAtEpochMillis: Long,
)
