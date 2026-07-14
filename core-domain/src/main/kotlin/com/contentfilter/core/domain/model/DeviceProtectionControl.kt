package com.contentfilter.core.domain.model

enum class ProtectionAuthorizationScope(val remoteValue: String) {
    None("none"),
    Settings("settings"),
    Removal("removal"),
    ;

    companion object {
        fun fromRemote(value: String?): ProtectionAuthorizationScope =
            entries.firstOrNull { it.remoteValue == value } ?: None
    }
}

data class DeviceProtectionControl(
    val deviceId: String,
    val accountId: String,
    val armed: Boolean = false,
    val authorizationScope: ProtectionAuthorizationScope = ProtectionAuthorizationScope.None,
    val authorizationExpiresAtEpochMillis: Long? = null,
    val commandRevision: Long = 0,
    val appliedRevision: Long = 0,
    val recoverySalt: String? = null,
    val recoveryVerifier: String? = null,
    val recoveryRevision: Long = 0,
    val recoveryConsumedRevision: Long = 0,
) {
    fun hasAuthorization(
        scope: ProtectionAuthorizationScope,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): Boolean =
        authorizationScope == scope &&
            authorizationExpiresAtEpochMillis?.let { it > nowEpochMillis } == true

    val hasAvailableRecovery: Boolean
        get() =
            !recoverySalt.isNullOrBlank() &&
                !recoveryVerifier.isNullOrBlank() &&
                recoveryRevision > recoveryConsumedRevision
}
