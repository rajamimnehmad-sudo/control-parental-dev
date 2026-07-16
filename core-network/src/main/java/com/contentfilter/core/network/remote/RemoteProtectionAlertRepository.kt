package com.contentfilter.core.network.remote

data class RemoteProtectionAlert(
    val id: String,
    val deviceId: String,
    val alertType: String,
    val title: String,
    val body: String,
    val createdAt: String,
)

interface RemoteProtectionAlertRepository {
    suspend fun pullAlerts(): RemoteResult<List<RemoteProtectionAlert>>
}
