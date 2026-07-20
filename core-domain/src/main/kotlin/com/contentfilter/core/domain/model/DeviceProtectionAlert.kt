package com.contentfilter.core.domain.model

object DeviceProtectionAlert {
    const val WebDisabled = "Protección web desactivada en este dispositivo."
    const val AppsDisabled = "Protección de apps desactivada en este dispositivo."
    const val AdminDisabled = "Protección contra desinstalación desactivada en este dispositivo."
    const val PossibleUninstall = "ALERTA MÁXIMA · App Usuario posiblemente desinstalada."
    const val PossibleUninstallWindowMillis = 30L * 60L * 1000L

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

    fun isPossibleUninstall(
        deviceAdminState: ComponentState,
        lastSeenAtEpochMillis: Long?,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): Boolean =
        deviceAdminState == ComponentState.Disabled &&
            lastSeenAtEpochMillis != null &&
            nowEpochMillis - lastSeenAtEpochMillis > PossibleUninstallWindowMillis
}
