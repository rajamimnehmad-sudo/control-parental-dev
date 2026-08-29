package com.contentfilter.feature.vpn.service

internal data class BrowserVpnAdmissionResult(
    val admittedCount: Int,
    val chromeAdmitted: Boolean,
)

/** Applies the browser allow-list while making Chrome admission authoritative for H19. */
internal object BrowserVpnAdmissionPolicy {
    fun admit(
        browserPackages: List<String>,
        chromePackage: String,
        requireChrome: Boolean,
        addAllowedApplication: (String) -> Unit,
    ): BrowserVpnAdmissionResult {
        var admittedCount = 0
        var chromeAdmitted = false
        browserPackages.distinct().forEach { packageName ->
            try {
                addAllowedApplication(packageName)
                admittedCount += 1
                if (packageName == chromePackage) chromeAdmitted = true
            } catch (error: Exception) {
                if (requireChrome && packageName == chromePackage) throw error
            }
        }
        check(!requireChrome || chromeAdmitted) { "Chrome VPN admission is required for full-tunnel media authority" }
        return BrowserVpnAdmissionResult(
            admittedCount = admittedCount,
            chromeAdmitted = chromeAdmitted,
        )
    }
}
