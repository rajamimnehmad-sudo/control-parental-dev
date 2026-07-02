package com.contentfilter.core.domain.model

sealed interface ActivationResult {
    data class Activated(val activation: DeviceActivation) : ActivationResult

    data class Failed(val reason: String) : ActivationResult
}
