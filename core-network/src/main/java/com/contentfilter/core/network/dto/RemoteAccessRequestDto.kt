package com.contentfilter.core.network.dto

import org.json.JSONObject

data class RemoteAccessRequestDto(
    val id: String,
    val accountId: String?,
    val deviceId: String?,
    val requestType: String,
    val targetType: String,
    val target: String,
    val targetPackageName: String?,
    val targetDomain: String?,
    val reason: String,
    val requestedMinutes: Int?,
    val status: String,
    val createdAt: String,
    val updatedAt: String,
    val expiresAt: String?,
    val deletedAt: String?,
) {
    fun toJson(): JSONObject =
        JSONObject()
            .put("id", id)
            .put("account_id", accountId)
            .put("device_id", deviceId)
            .put("request_type", requestType)
            .put("target_type", targetType)
            .put("target", target)
            .put("target_package_name", targetPackageName)
            .put("target_domain", targetDomain)
            .put("reason", reason)
            .put("requested_minutes", requestedMinutes)
            .put("status", status)
            .put("created_at", createdAt)
            .put("updated_at", updatedAt)
            .put("expires_at", expiresAt)

    companion object {
        fun fromJson(json: JSONObject): RemoteAccessRequestDto =
            RemoteAccessRequestDto(
                id = json.getString("id"),
                accountId = json.optNullableString("account_id"),
                deviceId = json.optNullableString("device_id"),
                requestType = json.optString("request_type").ifBlank { inferRequestType(json) },
                targetType = json.getString("target_type"),
                target = json.getString("target"),
                targetPackageName = json.optNullableString("target_package_name"),
                targetDomain = json.optNullableString("target_domain"),
                reason = json.getString("reason"),
                requestedMinutes = if (json.isNull("requested_minutes")) null else json.getInt("requested_minutes"),
                status = json.getString("status"),
                createdAt = json.getString("created_at"),
                updatedAt = json.getString("updated_at"),
                expiresAt = json.optNullableString("expires_at"),
                deletedAt = json.optNullableString("deleted_at"),
            )

        private fun inferRequestType(json: JSONObject): String =
            when {
                !json.isNull("requested_minutes") -> "EXTRA_TIME"
                json.optString("target_type") == "Domain" -> "DOMAIN_ACCESS"
                json.optString("target_type") == "App" -> "APP_ACCESS"
                else -> "OTHER"
            }
    }
}

private fun JSONObject.optNullableString(name: String): String? =
    if (isNull(name)) null else optString(name).ifBlank { null }
