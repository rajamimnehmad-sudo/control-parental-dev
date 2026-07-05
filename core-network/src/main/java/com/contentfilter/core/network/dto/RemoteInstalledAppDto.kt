package com.contentfilter.core.network.dto

import org.json.JSONObject

data class RemoteInstalledAppDto(
    val id: String,
    val accountId: String,
    val deviceId: String,
    val appName: String,
    val packageName: String,
    val versionName: String?,
    val isSystemApp: Boolean,
    val iconBase64: String?,
    val updatedAt: String,
) {
    fun toJson(): JSONObject =
        JSONObject()
            .put("id", id)
            .put("account_id", accountId)
            .put("device_id", deviceId)
            .put("app_name", appName)
            .put("package_name", packageName)
            .put("version_name", versionName)
            .put("is_system_app", isSystemApp)
            .put("icon_base64", iconBase64)
            .put("updated_at", updatedAt)

    fun toRequiredJson(): JSONObject =
        JSONObject()
            .put("id", id)
            .put("account_id", accountId)
            .put("device_id", deviceId)
            .put("app_name", appName)
            .put("package_name", packageName)
            .put("updated_at", updatedAt)

    companion object {
        fun fromJson(json: JSONObject): RemoteInstalledAppDto =
            RemoteInstalledAppDto(
                id = json.getString("id"),
                accountId = json.getString("account_id"),
                deviceId = json.getString("device_id"),
                appName = json.getString("app_name"),
                packageName = json.getString("package_name"),
                versionName = json.optNullableString("version_name"),
                isSystemApp = json.optBoolean("is_system_app", false),
                iconBase64 = json.optNullableString("icon_base64"),
                updatedAt = json.getString("updated_at"),
            )
    }
}
