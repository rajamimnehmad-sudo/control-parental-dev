package com.contentfilter.feature.accessibility.service

class SettingsProtectionPolicy {
    fun shouldLeaveSettings(
        packageName: String,
        className: String?,
        elapsedRealtimeMillis: Long,
    ): Boolean {
        if (packageName != AndroidSettingsPackage) return false
        if (elapsedRealtimeMillis - lastActionAtElapsedMillis < MinActionIntervalMillis) return false
        val normalizedClass = className.orEmpty()
        val protectedScreen = ProtectedSettingsClassHints.any { normalizedClass.contains(it, ignoreCase = true) }
        if (!protectedScreen) return false
        lastActionAtElapsedMillis = elapsedRealtimeMillis
        return true
    }

    private var lastActionAtElapsedMillis: Long = -MinActionIntervalMillis

    private companion object {
        const val AndroidSettingsPackage = "com.android.settings"
        const val MinActionIntervalMillis = 2_000L
        val ProtectedSettingsClassHints = listOf(
            "Accessibility",
            "Vpn",
            "DeviceAdmin",
            "SpecialAccess",
        )
    }
}
