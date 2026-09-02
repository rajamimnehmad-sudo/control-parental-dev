package com.contentfilter.user.chromeguard

internal data class ChromeBatteryBaselinePreconditionSnapshot(
    val devPackage: Boolean,
    val deviceOwner: Boolean,
    val chromePackageExact: Boolean,
    val chromeSuspended: Boolean,
    val labInactive: Boolean,
    val presentationNotReady: Boolean,
    val realWebAuthorityClosed: Boolean,
    val labProxyAbsent: Boolean,
    val globalProxyAbsent: Boolean,
    val ephemeralCaAbsent: Boolean,
    val fullTunnelAbsent: Boolean,
    val outstandingAuthorityTokensZero: Boolean,
    val svgRegistryClosed: Boolean,
    val resetCount: Int,
) {
    fun rejectionReasons(): List<String> =
        buildList {
            if (!devPackage) add("not_dev")
            if (!deviceOwner) add("not_device_owner")
            if (!chromePackageExact) add("chrome_package")
            if (!chromeSuspended) add("chrome_not_suspended")
            if (!labInactive) add("lab_active")
            if (!presentationNotReady) add("presentation_ready")
            if (!realWebAuthorityClosed) add("real_web_authority")
            if (!labProxyAbsent) add("lab_proxy")
            if (!globalProxyAbsent) add("global_proxy")
            if (!ephemeralCaAbsent) add("ephemeral_ca")
            if (!fullTunnelAbsent) add("full_tunnel")
            if (!outstandingAuthorityTokensZero) add("outstanding_tokens")
            if (!svgRegistryClosed) add("svg_registry")
            if (resetCount != RequiredResetCount) add("reset_count")
        }

    val valid: Boolean
        get() = rejectionReasons().isEmpty()

    companion object {
        const val RequiredResetCount = 3
    }
}

internal data class ChromeBatteryBaselineLease(
    val bootMarker: Long,
    val issuedAtElapsed: Long,
    val expiresAtElapsed: Long,
) {
    fun isCurrent(
        currentBootMarker: Long,
        nowElapsed: Long,
    ): Boolean =
        bootMarker == currentBootMarker &&
            issuedAtElapsed >= 0L &&
            expiresAtElapsed > issuedAtElapsed &&
            expiresAtElapsed - issuedAtElapsed <= MaximumDurationMillis &&
            nowElapsed in issuedAtElapsed until expiresAtElapsed

    companion object {
        const val MaximumDurationMillis = 45L * 60L * 1_000L
    }
}

internal fun chromeBatteryBaselineDurationMillis(requestedMinutes: Int): Long? =
    requestedMinutes.takeIf { it in 1..45 }?.times(60_000L)
