package com.contentfilter.core.network.dto

import org.json.JSONObject

data class AuthSessionDto(
    val accessToken: String,
    val refreshToken: String?,
    val expiresInSeconds: Long,
) {
    companion object {
        fun fromJson(json: JSONObject): AuthSessionDto =
            AuthSessionDto(
                accessToken = json.getString("access_token"),
                refreshToken = json.optNullableString("refresh_token"),
                expiresInSeconds = json.optLong("expires_in", 0L),
            )
    }
}
