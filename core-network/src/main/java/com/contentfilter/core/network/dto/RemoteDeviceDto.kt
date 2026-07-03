package com.contentfilter.core.network.dto

import org.json.JSONObject

data class RemoteDeviceDto(
    val id: String,
    val accountId: String,
    val displayName: String,
    val appRole: String,
    val lastSeenAt: String?,
    val updatedAt: String,
    val deletedAt: String?,
) {
    companion object {
        fun fromJson(json: JSONObject): RemoteDeviceDto =
            RemoteDeviceDto(
                id = json.getString("id"),
                accountId = json.getString("account_id"),
                displayName = json.getString("display_name"),
                appRole = json.optString("app_role", "user").ifBlank { "user" },
                lastSeenAt = json.optNullableString("last_seen_at"),
                updatedAt = json.getString("updated_at"),
                deletedAt = json.optNullableString("deleted_at"),
            )
    }
}
