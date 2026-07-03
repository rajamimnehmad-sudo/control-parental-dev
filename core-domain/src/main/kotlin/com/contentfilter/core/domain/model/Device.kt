package com.contentfilter.core.domain.model

data class Device(
    val id: String,
    val accountId: String,
    val displayName: String,
    val appRole: String = "user",
    val lastSeenAtEpochMillis: Long? = null,
)
