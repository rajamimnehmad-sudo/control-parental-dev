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
    val activeWindowStartMinute: Int? = null,
    val activeWindowEndMinute: Int? = null,
    val activeDaysMask: Int = 0b1111111,
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
            .put("active_window_start_minute", activeWindowStartMinute ?: JSONObject.NULL)
            .put("active_window_end_minute", activeWindowEndMinute ?: JSONObject.NULL)
            .put("active_days_mask", activeDaysMask)
            .put("updated_at", updatedAt)
            .apply {
                if (deletedAt != null) {
                    put("deleted_at", deletedAt)
                }
            }

    companion object {
        fun fromJson(json: JSONObject): RemotePolicyRuleDto =
            RemotePolicyRuleDto(
                id = json.getString("id"),
                accountId = json.optNullableString("account_id"),
                policyId = json.getString("policy_id"),
                scope = json.getString("scope"),
                target = json.getString("target"),
                action = json.getString("action"),
                priority = json.getInt("priority"),
                enabled = json.getBoolean("enabled"),
                activeWindowStartMinute = json.optNullableInt("active_window_start_minute"),
                activeWindowEndMinute = json.optNullableInt("active_window_end_minute"),
                activeDaysMask = json.optInt("active_days_mask", 0b1111111),
                updatedAt = json.getString("updated_at"),
                deletedAt = json.optNullableString("deleted_at"),
            )
    }
}
