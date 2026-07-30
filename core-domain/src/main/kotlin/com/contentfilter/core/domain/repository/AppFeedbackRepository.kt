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

    suspend fun submitSupportReport(
        deviceId: String,
        category: String,
        safeSummary: String,
        appVersionCode: Int,
        manufacturer: String,
        model: String,
        androidVersion: String,
        diagnosticCodes: List<String>,
    ): Result<Unit>
}
