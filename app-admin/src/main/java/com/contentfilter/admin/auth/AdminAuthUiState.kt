package com.contentfilter.admin.auth

data class AdminAuthUiState(
    val email: String = "",
    val password: String = "",
    val activationCode: String = "",
    val deviceName: String = "Administrador",
    val activated: Boolean = false,
    val offlineMode: Boolean = true,
    val loading: Boolean = false,
    val message: String = "Modo Offline / Desarrollo",
)
