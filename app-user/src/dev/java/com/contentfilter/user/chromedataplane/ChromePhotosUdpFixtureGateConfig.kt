package com.contentfilter.user.chromedataplane

import android.content.Intent
import com.contentfilter.core.domain.chrome.ChromePhotosDataPlaneLabContract
import java.net.InetAddress

internal data class ChromePhotosUdpFixtureGateConfig(
    val enabled: Boolean,
    val address: String,
    val port: Int,
    val malformedProbeEnabled: Boolean,
) {
    companion object {
        val Disabled = ChromePhotosUdpFixtureGateConfig(false, "", 0, false)

        fun fromIntent(intent: Intent?): ChromePhotosUdpFixtureGateConfig {
            if (intent?.getBooleanExtra(ChromePhotosDataPlaneLabContract.KeyUdpFixtureGateEnabled, false) != true) {
                return Disabled
            }
            val rawAddress = intent.getStringExtra(ChromePhotosDataPlaneLabContract.KeyUdpFixtureAddress).orEmpty()
            val address = parseIpv4Literal(rawAddress)
            require(
                address != null &&
                    address.isSiteLocalAddress &&
                    !address.isAnyLocalAddress &&
                    !address.isLoopbackAddress &&
                    !address.isMulticastAddress,
            ) { "UDP fixture must be an exact private IPv4 address" }
            val port = intent.getIntExtra(ChromePhotosDataPlaneLabContract.KeyUdpFixturePort, 0)
            require(port in MinimumFixturePort..MaximumFixturePort) { "UDP fixture port outside bounded range" }
            return ChromePhotosUdpFixtureGateConfig(
                enabled = true,
                address = address.hostAddress.orEmpty(),
                port = port,
                malformedProbeEnabled =
                    intent.getBooleanExtra(
                        ChromePhotosDataPlaneLabContract.KeyUdpFixtureMalformedProbeEnabled,
                        false,
                    ),
            )
        }

        private const val MinimumFixturePort = 20_000
        private const val MaximumFixturePort = 50_000

        private fun parseIpv4Literal(value: String): InetAddress? {
            val octets = value.split('.')
            if (octets.size != Ipv4OctetCount) return null
            val bytes =
                octets.map { octet ->
                    if (octet.isEmpty() || octet.any { character -> !character.isDigit() }) return null
                    val number = octet.toIntOrNull()?.takeIf { it in 0..255 } ?: return null
                    number.toByte()
                }.toByteArray()
            return InetAddress.getByAddress(bytes)
        }

        private const val Ipv4OctetCount = 4
    }
}
