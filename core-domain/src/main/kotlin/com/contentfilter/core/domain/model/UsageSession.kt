package com.contentfilter.core.domain.model

/**
 * Local usage session captured from official platform signals.
 */
data class UsageSession(
    val id: String,
    val deviceId: String,
    val packageName: String,
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long?,
) {
    companion object {
        const val LOCAL_DEVICE_ID = "local-device"
    }
}
