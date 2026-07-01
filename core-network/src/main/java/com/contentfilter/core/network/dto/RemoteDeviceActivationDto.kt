package com.contentfilter.core.network.dto

import org.json.JSONObject

data class RemoteDeviceActivationDto(
    val accountId: String,
    val deviceId: String,
    val activationId: String,
) {
    companion object {
        fun fromJson(json: JSONObject): RemoteDeviceActivationDto =
            RemoteDeviceActivationDto(
                accountId = json.getString("account_id"),
                deviceId = json.getString("device_id"),
                activationId = json.getString("activation_id"),
            )
    }
}
