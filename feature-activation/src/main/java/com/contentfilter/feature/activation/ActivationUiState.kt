package com.contentfilter.feature.activation

data class ActivationUiState(
    val activationCode: String = "",
    val deviceName: String = "",
    val activated: Boolean = false,
    val isLoading: Boolean = false,
    val message: String = "Pedile un código al administrador.",
)
