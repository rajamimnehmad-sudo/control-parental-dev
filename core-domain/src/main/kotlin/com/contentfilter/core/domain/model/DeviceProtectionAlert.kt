package com.contentfilter.core.domain.model

object DeviceProtectionAlert {
    const val WebDisabled = "Protección web desactivada en este dispositivo."
    const val AppsDisabled = "Protección de apps desactivada en este dispositivo."
    const val AdminDisabled = "Protección contra desinstalación desactivada en este dispositivo."

    fun fromStates(
        vpnState: ComponentState,
        accessibilityState: ComponentState,
        deviceAdminState: ComponentState = ComponentState.Unknown,
    ): String? =
        when {
            vpnState == ComponentState.Disabled -> WebDisabled
            accessibilityState == ComponentState.Disabled -> AppsDisabled
            deviceAdminState == ComponentState.Disabled -> AdminDisabled
            else -> null
        }
}
