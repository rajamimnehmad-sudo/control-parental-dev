package com.contentfilter.feature.vpn.transport

import androidx.annotation.Keep

internal data class HevNativeStats(
    val txPackets: Long,
    val txBytes: Long,
    val rxPackets: Long,
    val rxBytes: Long,
)

internal interface HevNativeApi {
    fun run(
        config: ByteArray,
        tunFd: Int,
    ): Int

    fun quit()

    fun stats(): HevNativeStats
}

@Keep
internal object HevNativeBridge : HevNativeApi {
    init {
        System.loadLibrary("hev-socks5-tunnel")
        System.loadLibrary("glosh-hev-bridge")
    }

    override fun run(
        config: ByteArray,
        tunFd: Int,
    ): Int = nativeRun(config, tunFd)

    override fun quit() = nativeQuit()

    override fun stats(): HevNativeStats {
        val values = nativeStats()
        return HevNativeStats(
            txPackets = values.getOrElse(0) { 0L },
            txBytes = values.getOrElse(1) { 0L },
            rxPackets = values.getOrElse(2) { 0L },
            rxBytes = values.getOrElse(3) { 0L },
        )
    }

    private external fun nativeRun(
        config: ByteArray,
        tunFd: Int,
    ): Int

    private external fun nativeQuit()

    private external fun nativeStats(): LongArray
}
