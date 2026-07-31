package com.contentfilter.core.domain.repository

interface AppFeedbackRepository {
    data class AdminContact(
        val contactEmail: String,
        val phoneE164: String,
    )

    data class UserContact(
        val contactEmail: String,
        val phoneE164: String,
    )

    data class RatingAvailability(
        val nextAvailableAtEpochMillis: Long?,
    )

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

    suspend fun getAdminContact(deviceId: String): Result<AdminContact>

    suspend fun updateAdminContact(
        deviceId: String,
        contactEmail: String,
        phoneE164: String,
    ): Result<Unit>

    suspend fun getUserContact(deviceId: String): Result<UserContact>

    suspend fun updateUserContact(
        deviceId: String,
        contactEmail: String,
        phoneE164: String,
    ): Result<Unit>

    suspend fun getRatingAvailability(deviceId: String): Result<RatingAvailability>

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
