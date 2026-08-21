package com.contentfilter.feature.vpn.service

import android.content.Context
import android.util.Log
import com.contentfilter.core.domain.chrome.ChromePhotosDataPlaneLabContract
import com.contentfilter.core.domain.chrome.ChromePhotosDataPlaneRuntimeAttestation
import com.contentfilter.feature.vpn.dns.VpnPacketDiagnostic
import java.net.InetAddress

internal data class ChromePhotosLabVpnRoute(
    val address: String,
    val prefixLength: Int,
)

internal object ChromePhotosDataPlaneLabVpnPolicy {
    fun isActive(context: Context): Boolean =
        context.packageName.endsWith(".dev") &&
            context.getSharedPreferences(
                ChromePhotosDataPlaneLabContract.PreferencesName,
                Context.MODE_PRIVATE,
            ).getBoolean(ChromePhotosDataPlaneLabContract.KeyActive, false)

    fun routes(active: Boolean): List<ChromePhotosLabVpnRoute> =
        if (active) {
            listOf(
                ChromePhotosLabVpnRoute(
                    ChromePhotosDataPlaneLabContract.FixtureIpv4,
                    Ipv4HostPrefixLength,
                ),
            )
        } else {
            emptyList()
        }

    fun isFixtureDomain(
        active: Boolean,
        normalizedDomain: String,
    ): Boolean = active && normalizedDomain == ChromePhotosDataPlaneLabContract.FixtureHost

    fun fixtureAddresses(queryType: Int): List<ByteArray> =
        if (queryType == DnsTypeA) {
            listOf(InetAddress.getByName(ChromePhotosDataPlaneLabContract.FixtureIpv4).address)
        } else {
            emptyList()
        }

    fun isTunnelConfirmed(
        active: Boolean,
        sessionId: String,
        established: Boolean,
    ): Boolean = active && sessionId.isNotBlank() && established

    fun markTunnelState(
        context: Context,
        established: Boolean,
    ) {
        val preferences =
            context.getSharedPreferences(
                ChromePhotosDataPlaneLabContract.PreferencesName,
                Context.MODE_PRIVATE,
            )
        val sessionId = preferences.getString(ChromePhotosDataPlaneLabContract.KeySessionId, null).orEmpty()
        val confirmed = isTunnelConfirmed(isActive(context), sessionId, established)
        preferences.edit()
            .putBoolean(ChromePhotosDataPlaneLabContract.KeyVpnConfirmed, confirmed)
            .putString(
                ChromePhotosDataPlaneLabContract.KeyVpnSessionId,
                if (confirmed) sessionId else "",
            )
            .commit()
        ChromePhotosDataPlaneRuntimeAttestation.markVpnConfirmed(sessionId, confirmed)
        if (sessionId.isNotBlank()) {
            Log.i(
                LogTag,
                "vpnAttestation=${if (confirmed) "confirmed" else "revoked"} " +
                    "session=${sessionId.take(SessionLogLength)}",
            )
        }
    }

    @Synchronized
    fun recordControlledTransportAttempt(
        context: Context,
        diagnostic: VpnPacketDiagnostic,
    ): Boolean {
        if (!isActive(context) || diagnostic.destinationPort != HttpsPort) return false
        val key =
            when (diagnostic.protocol.lowercase()) {
                "udp" -> ChromePhotosDataPlaneLabContract.KeyQuicAttempts
                "tcp" -> ChromePhotosDataPlaneLabContract.KeyDirectTcpAttempts
                else -> return false
            }
        val preferences =
            context.getSharedPreferences(
                ChromePhotosDataPlaneLabContract.PreferencesName,
                Context.MODE_PRIVATE,
            )
        val count = preferences.getLong(key, 0L) + 1L
        preferences.edit().putLong(key, count).commit()
        Log.i(
            LogTag,
            "transport=${diagnostic.protocol.lowercase()} destination=fixture port=$HttpsPort action=dropped count=$count",
        )
        return true
    }

    private const val DnsTypeA = 1
    private const val HttpsPort = 443
    private const val Ipv4HostPrefixLength = 32
    private const val SessionLogLength = 8
    private const val LogTag = "ChromePhotosDataPlane"
}
