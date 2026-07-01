package com.contentfilter.core.network.dto

import org.json.JSONObject

data class RemoteDailyLimitDto(
    val id: String,
    val policyId: String,
    val targetType: String,
    val target: String,
    val limitMinutes: Int,
    val enabled: Boolean,
    val updatedAt: String,
    val deletedAt: String?,
) {
    companion object {
        fun fromJson(json: JSONObject): RemoteDailyLimitDto =
            RemoteDailyLimitDto(
                id = json.getString("id"),
                policyId = json.getString("policy_id"),
                targetType = json.getString("target_type"),
                target = json.getString("target"),
                limitMinutes = json.getInt("limit_minutes"),
                enabled = json.getBoolean("enabled"),
                updatedAt = json.getString("updated_at"),
                deletedAt = json.optString("deleted_at").ifBlank { null },
            )
    }
}
