package com.contentfilter.core.network.dto

import org.json.JSONObject

data class RemoteDeviceDto(
    val id: String,
    val accountId: String,
    val displayName: String,
    val updatedAt: String,
    val deletedAt: String?,
) {
    companion object {
        fun fromJson(json: JSONObject): RemoteDeviceDto =
            RemoteDeviceDto(
                id = json.getString("id"),
                accountId = json.getString("account_id"),
                displayName = json.getString("display_name"),
                updatedAt = json.getString("updated_at"),
                deletedAt = json.optString("deleted_at").ifBlank { null },
            )
    }
}
