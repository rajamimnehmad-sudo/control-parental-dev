package com.contentfilter.feature.activation

data class ActivationUiState(
    val email: String = "",
    val password: String = "",
    val activationCode: String = "",
    val deviceName: String = "",
    val activated: Boolean = false,
    val isLoading: Boolean = false,
    val message: String = "Modo offline/desarrollo disponible si Supabase no esta configurado.",
)
