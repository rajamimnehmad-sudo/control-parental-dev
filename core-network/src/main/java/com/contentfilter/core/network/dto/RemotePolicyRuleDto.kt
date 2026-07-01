package com.contentfilter.core.network.dto

import org.json.JSONObject

data class RemotePolicyRuleDto(
    val id: String,
    val accountId: String?,
    val policyId: String,
    val scope: String,
    val target: String,
    val action: String,
    val priority: Int,
    val enabled: Boolean,
    val updatedAt: String,
    val deletedAt: String?,
) {
    fun toJson(): JSONObject =
        JSONObject()
            .put("id", id)
            .put("account_id", accountId)
            .put("policy_id", policyId)
            .put("scope", scope)
            .put("target", target)
            .put("action", action)
            .put("priority", priority)
            .put("enabled", enabled)
            .put("updated_at", updatedAt)

    companion object {
        fun fromJson(json: JSONObject): RemotePolicyRuleDto =
            RemotePolicyRuleDto(
                id = json.getString("id"),
                accountId = json.optString("account_id").ifBlank { null },
                policyId = json.getString("policy_id"),
                scope = json.getString("scope"),
                target = json.getString("target"),
                action = json.getString("action"),
                priority = json.getInt("priority"),
                enabled = json.getBoolean("enabled"),
                updatedAt = json.getString("updated_at"),
                deletedAt = json.optString("deleted_at").ifBlank { null },
            )
    }
}
