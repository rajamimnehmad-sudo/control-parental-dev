package com.contentfilter.core.security

data class AuthSession(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAtEpochMillis: Long,
)
