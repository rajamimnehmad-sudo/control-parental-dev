package com.contentfilter.core.domain.model

object DeviceProtectionAlert {
    const val WebDisabled = "Protección web desactivada en este dispositivo."
    const val AppsDisabled = "Protección de apps desactivada en este dispositivo."

    fun fromStates(
        vpnState: ComponentState,
        accessibilityState: ComponentState,
    ): String? =
        when {
            vpnState == ComponentState.Disabled -> WebDisabled
            accessibilityState == ComponentState.Disabled -> AppsDisabled
            else -> null
        }
}
