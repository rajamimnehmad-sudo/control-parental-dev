package com.contentfilter.core.network.dto

import org.json.JSONObject

data class RemotePolicyDto(
    val id: String,
    val accountId: String?,
    val deviceId: String?,
    val version: Long,
    val active: Boolean,
    val updatedAt: String,
    val deletedAt: String?,
) {
    fun toJson(): JSONObject =
        JSONObject()
            .put("id", id)
            .put("account_id", accountId)
            .put("device_id", deviceId)
            .put("version", version)
            .put("active", active)
            .put("updated_at", updatedAt)

    companion object {
        fun fromJson(json: JSONObject): RemotePolicyDto =
            RemotePolicyDto(
                id = json.getString("id"),
                accountId = json.optNullableString("account_id"),
                deviceId = json.optNullableString("device_id"),
                version = json.getLong("version"),
                active = json.getBoolean("active"),
                updatedAt = json.getString("updated_at"),
                deletedAt = json.optNullableString("deleted_at"),
            )
    }
}
