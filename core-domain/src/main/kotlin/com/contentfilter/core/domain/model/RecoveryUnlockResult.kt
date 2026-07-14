package com.contentfilter.core.domain.model

sealed interface RecoveryUnlockResult {
    data class Unlocked(val validUntilEpochMillis: Long) : RecoveryUnlockResult

    data class Invalid(val attemptsRemaining: Int) : RecoveryUnlockResult

    data class Locked(val retryAtEpochMillis: Long) : RecoveryUnlockResult

    data object Unavailable : RecoveryUnlockResult
}
