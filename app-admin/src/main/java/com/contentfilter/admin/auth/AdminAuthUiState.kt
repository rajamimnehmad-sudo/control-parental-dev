package com.contentfilter.admin.auth

data class AdminAuthUiState(
    val activationCode: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val activated: Boolean = false,
    val offlineMode: Boolean = true,
    val loading: Boolean = false,
    val showResetConfirmation: Boolean = false,
    val message: String = "Sin conexion. Mostrando datos guardados.",
)
