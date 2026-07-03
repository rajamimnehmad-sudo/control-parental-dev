package com.contentfilter.core.network.dto

import org.json.JSONObject

data class RemoteExtraTimeGrantDto(
    val id: String,
    val accountId: String?,
    val requestId: String?,
    val targetType: String,
    val target: String,
    val grantedMinutes: Int,
    val validUntil: String,
    val updatedAt: String,
    val deletedAt: String?,
) {
    fun toJson(): JSONObject =
        JSONObject()
            .put("id", id)
            .put("account_id", accountId)
            .put("request_id", requestId)
            .put("target_type", targetType)
            .put("target", target)
            .put("granted_minutes", grantedMinutes)
            .put("valid_until", validUntil)
            .put("updated_at", updatedAt)

    companion object {
        fun fromJson(json: JSONObject): RemoteExtraTimeGrantDto =
            RemoteExtraTimeGrantDto(
                id = json.getString("id"),
                accountId = json.optNullableString("account_id"),
                requestId = json.optNullableString("request_id"),
                targetType = json.getString("target_type"),
                target = json.getString("target"),
                grantedMinutes = json.getInt("granted_minutes"),
                validUntil = json.getString("valid_until"),
                updatedAt = json.getString("updated_at"),
                deletedAt = json.optNullableString("deleted_at"),
            )
    }
}
