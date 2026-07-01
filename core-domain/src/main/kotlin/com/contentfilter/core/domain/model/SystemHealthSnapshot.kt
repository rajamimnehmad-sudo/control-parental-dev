package com.contentfilter.core.domain.model

/**
 * Snapshot of the local system health. It is intentionally platform-neutral.
 */
data class SystemHealthSnapshot(
    val vpnState: ComponentState,
    val accessibilityState: ComponentState,
    val syncState: ComponentState,
    val integrityState: ComponentState,
    val databaseState: ComponentState,
    val licenseState: LicenseState,
    val updateState: UpdateState,
    val checkedAtEpochMillis: Long,
) {
    val protectionLevel: ProtectionLevel
        get() = when {
            vpnState == ComponentState.Disabled ||
                accessibilityState == ComponentState.Disabled ||
                databaseState == ComponentState.Disabled -> ProtectionLevel.Unprotected
            hasWarningState() -> ProtectionLevel.Warning
            else -> ProtectionLevel.Protected
        }

    private fun hasWarningState(): Boolean =
        vpnState != ComponentState.Enabled ||
            accessibilityState != ComponentState.Enabled ||
            syncState == ComponentState.Warning ||
            integrityState == ComponentState.Warning ||
            databaseState == ComponentState.Warning ||
            licenseState != LicenseState.Active ||
            updateState != UpdateState.Current
}
