package com.contentfilter.core.domain.model

data class InstalledApp(
    val id: String,
    val accountId: String,
    val deviceId: String,
    val appName: String,
    val packageName: String,
    val versionName: String?,
    val isSystemApp: Boolean,
    val iconBase64: String?,
    val updatedAtEpochMillis: Long,
)
