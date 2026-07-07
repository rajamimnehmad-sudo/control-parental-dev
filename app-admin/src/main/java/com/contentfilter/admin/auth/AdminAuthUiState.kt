package com.contentfilter.admin.auth

data class AdminAuthUiState(
    val activationCode: String = "",
    val activated: Boolean = false,
    val offlineMode: Boolean = true,
    val loading: Boolean = false,
    val message: String = "Modo Offline / Desarrollo",
)
