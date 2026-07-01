package com.contentfilter.core.domain.model

data class ActivationCredentials(
    val email: String,
    val password: String,
    val activationCode: String,
    val deviceDisplayName: String,
    val appVersionCode: Int,
    val appRole: String = "user",
)
