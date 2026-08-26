package com.contentfilter.feature.accessibility.chromevisual

/** Shell-accessible only through the DEV receiver. Production builds have no caller for this gate. */
object ChromeVisualShieldLabControl {
    const val RegionId = "fixture-sentinel-v1"
    const val RegionLeftBasisPoints = 1_500
    const val RegionTopBasisPoints = 2_500
    const val RegionRightBasisPoints = 8_500
    const val RegionBottomBasisPoints = 5_500
    const val FixtureSignature = "compiled:chrome-visual-shield-13b-r:v1"

    @Volatile
    private var endpoint: Endpoint? = null

    fun start(): String = endpoint?.start() ?: Unavailable

    fun stop(): String = endpoint?.stop() ?: Unavailable

    fun release(): String = endpoint?.release() ?: Unavailable

    fun injectStale(): String = endpoint?.injectStale() ?: Unavailable

    fun cancelStress(): String = endpoint?.cancelStress() ?: Unavailable

    fun armAnalyzerFailure(): String = endpoint?.armAnalyzerFailure() ?: Unavailable

    fun status(): String = endpoint?.status() ?: Unavailable

    internal fun bind(value: Endpoint) {
        endpoint = value
    }

    internal fun unbind(value: Endpoint) {
        if (endpoint === value) endpoint = null
    }

    internal interface Endpoint {
        fun start(): String

        fun stop(): String

        fun release(): String

        fun injectStale(): String

        fun cancelStress(): String

        fun armAnalyzerFailure(): String

        fun status(): String
    }

    private const val Unavailable = "result=unavailable"
}
