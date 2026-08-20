package com.contentfilter.feature.accessibility.chromevisual

internal enum class ChromeVisualCapabilityState {
    AvailableStrongEnough,
    Degraded,
    Unavailable,
    SecureWindow,
    Overload,
    UnsupportedAndroid,
    UnsupportedAbi,
    AmbiguousGeometry,
}

internal data class ChromeVisualCapabilityDecision(
    val state: ChromeVisualCapabilityState,
    val canAnalyzeChrome: Boolean,
    val keepExistingCoverage: Boolean,
    val requiresDagFallback: Boolean,
    val reason: String,
)

internal object ChromeVisualCapabilityPolicy {
    fun initial(
        sdkInt: Int,
        is64BitProcess: Boolean,
        featureEnabled: Boolean,
        engineAvailable: Boolean,
    ): ChromeVisualCapabilityDecision =
        when {
            sdkInt < MinimumSdk -> fallback(ChromeVisualCapabilityState.UnsupportedAndroid, "api_below_34")
            !is64BitProcess -> fallback(ChromeVisualCapabilityState.UnsupportedAbi, "non_arm64_process")
            !featureEnabled -> fallback(ChromeVisualCapabilityState.Unavailable, "feature_dev_only")
            !engineAvailable -> fallback(ChromeVisualCapabilityState.Unavailable, "visual_engine_unavailable")
            else ->
                ChromeVisualCapabilityDecision(
                    state = ChromeVisualCapabilityState.AvailableStrongEnough,
                    canAnalyzeChrome = true,
                    keepExistingCoverage = false,
                    requiresDagFallback = false,
                    reason = "available",
                )
        }

    fun captureFailure(secureWindow: Boolean): ChromeVisualCapabilityDecision =
        fallback(
            state =
                if (secureWindow) {
                    ChromeVisualCapabilityState.SecureWindow
                } else {
                    ChromeVisualCapabilityState.Degraded
                },
            reason = if (secureWindow) "secure_window" else "capture_unavailable",
            keepCoverage = true,
        )

    fun runtimeUnavailable(
        ambiguousGeometry: Boolean = false,
        overloaded: Boolean = false,
    ): ChromeVisualCapabilityDecision =
        fallback(
            state =
                when {
                    overloaded -> ChromeVisualCapabilityState.Overload
                    ambiguousGeometry -> ChromeVisualCapabilityState.AmbiguousGeometry
                    else -> ChromeVisualCapabilityState.Unavailable
                },
            reason =
                when {
                    overloaded -> "analysis_overload"
                    ambiguousGeometry -> "ambiguous_geometry"
                    else -> "chrome_window_unavailable"
                },
            keepCoverage = true,
        )

    private fun fallback(
        state: ChromeVisualCapabilityState,
        reason: String,
        keepCoverage: Boolean = false,
    ) = ChromeVisualCapabilityDecision(
        state = state,
        canAnalyzeChrome = false,
        keepExistingCoverage = keepCoverage,
        requiresDagFallback = true,
        reason = reason,
    )

    private const val MinimumSdk = 34
}
