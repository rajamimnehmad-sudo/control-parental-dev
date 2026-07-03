package com.contentfilter.core.network.dto

import org.json.JSONObject

data class RemoteDailyLimitDto(
    val id: String,
    val accountId: String?,
    val policyId: String,
    val targetType: String,
    val target: String,
    val limitMinutes: Int,
    val enabled: Boolean,
    val updatedAt: String,
    val deletedAt: String?,
) {
    fun toJson(): JSONObject =
        JSONObject()
            .put("id", id)
            .put("account_id", accountId)
            .put("policy_id", policyId)
            .put("target_type", targetType)
            .put("target", target)
            .put("limit_minutes", limitMinutes)
            .put("enabled", enabled)
            .put("updated_at", updatedAt)
            .apply {
                deletedAt?.let { put("deleted_at", it) }
            }

    companion object {
        fun fromJson(json: JSONObject): RemoteDailyLimitDto =
            RemoteDailyLimitDto(
                id = json.getString("id"),
                accountId = json.optNullableString("account_id"),
                policyId = json.getString("policy_id"),
                targetType = json.getString("target_type"),
                target = json.getString("target"),
                limitMinutes = json.getInt("limit_minutes"),
                enabled = json.getBoolean("enabled"),
                updatedAt = json.getString("updated_at"),
                deletedAt = json.optNullableString("deleted_at"),
            )
    }
}
