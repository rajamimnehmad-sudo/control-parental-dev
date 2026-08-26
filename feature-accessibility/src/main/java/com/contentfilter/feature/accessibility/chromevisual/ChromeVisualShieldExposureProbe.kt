package com.contentfilter.feature.accessibility.chromevisual

internal data class ChromeVisualShieldRgb(
    val red: Int,
    val green: Int,
    val blue: Int,
)

internal object ChromeVisualShieldExposureProbe {
    fun isSentinelPair(
        first: ChromeVisualShieldRgb,
        second: ChromeVisualShieldRgb,
    ): Boolean =
        (isSentinelRed(first) && isSentinelBlack(second)) ||
            (isSentinelBlack(first) && isSentinelRed(second))

    private fun isSentinelRed(color: ChromeVisualShieldRgb): Boolean =
        color.red >= 190 && color.green <= 45 && color.blue <= 70

    private fun isSentinelBlack(color: ChromeVisualShieldRgb): Boolean =
        color.red <= 25 && color.green <= 25 && color.blue <= 25
}
