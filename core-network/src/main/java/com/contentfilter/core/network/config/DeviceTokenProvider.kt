package com.contentfilter.core.network.config

interface DeviceTokenProvider {
    fun currentDeviceToken(): String?

    fun saveDeviceToken(token: String)

    fun isDeviceRelinkPending(): Boolean

    fun markDeviceRelinkPending()

    fun clearDeviceRelinkPending()

    fun clearDeviceToken()
}
