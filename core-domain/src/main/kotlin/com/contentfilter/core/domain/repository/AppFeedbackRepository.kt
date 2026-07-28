package com.contentfilter.core.domain.repository

interface AppFeedbackRepository {
    suspend fun submitRating(
        deviceId: String,
        stars: Int,
        comment: String,
        appVersionCode: Int,
    ): Result<Unit>

    suspend fun reportDeviceMetadata(
        deviceId: String,
        manufacturer: String,
        model: String,
        androidVersion: String,
        androidSdk: Int,
    ): Result<Unit>

    suspend fun updateAdminPhone(
        deviceId: String,
        phoneE164: String,
    ): Result<Unit>
}
