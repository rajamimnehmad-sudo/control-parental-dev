package com.contentfilter.core.network.dto

import org.json.JSONObject

data class RemoteAppGroupDto(
    val id: String,
    val accountId: String?,
    val deviceId: String,
    val name: String,
    val color: String,
    val limitMinutes: Int,
    val resetMinuteOfDay: Int,
    val enabled: Boolean,
    val updatedAt: String,
    val deletedAt: String?,
) {
    fun toJson(): JSONObject =
        JSONObject()
            .put("id", id)
            .put("account_id", accountId)
            .put("device_id", deviceId)
            .put("name", name)
            .put("color", color)
            .put("limit_minutes", limitMinutes)
            .put("reset_minute_of_day", resetMinuteOfDay)
            .put("enabled", enabled)
            .put("updated_at", updatedAt)
            .apply {
                deletedAt?.let { put("deleted_at", it) }
            }

    companion object {
        fun fromJson(json: JSONObject): RemoteAppGroupDto =
            RemoteAppGroupDto(
                id = json.getString("id"),
                accountId = json.optNullableString("account_id"),
                deviceId = json.getString("device_id"),
                name = json.getString("name"),
                color = json.optString("color", "teal"),
                limitMinutes = json.getInt("limit_minutes"),
                resetMinuteOfDay = json.optInt("reset_minute_of_day", 720),
                enabled = json.getBoolean("enabled"),
                updatedAt = json.getString("updated_at"),
                deletedAt = json.optNullableString("deleted_at"),
            )
    }
}

data class RemoteAppGroupAppDto(
    val id: String,
    val accountId: String?,
    val deviceId: String,
    val groupId: String,
    val packageName: String,
    val enabled: Boolean,
    val updatedAt: String,
    val deletedAt: String?,
) {
    fun toJson(): JSONObject =
        JSONObject()
            .put("id", id)
            .put("account_id", accountId)
            .put("device_id", deviceId)
            .put("group_id", groupId)
            .put("package_name", packageName)
            .put("enabled", enabled)
            .put("updated_at", updatedAt)
            .apply {
                deletedAt?.let { put("deleted_at", it) }
            }

    companion object {
        fun fromJson(json: JSONObject): RemoteAppGroupAppDto =
            RemoteAppGroupAppDto(
                id = json.getString("id"),
                accountId = json.optNullableString("account_id"),
                deviceId = json.getString("device_id"),
                groupId = json.getString("group_id"),
                packageName = json.getString("package_name"),
                enabled = json.getBoolean("enabled"),
                updatedAt = json.getString("updated_at"),
                deletedAt = json.optNullableString("deleted_at"),
            )
    }
}
