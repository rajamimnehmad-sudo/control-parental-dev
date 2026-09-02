package com.contentfilter.user.chromedataplane

import com.contentfilter.core.domain.chrome.ChromePhotosDataPlaneLabContract
import okhttp3.Dns
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException

internal fun interface ChromeHostResolver {
    @Throws(UnknownHostException::class)
    fun lookup(host: String): List<InetAddress>
}

internal class ChromePublicDestinationAuthority(
    private val resolver: ChromeHostResolver = ChromeHostResolver { host -> InetAddress.getAllByName(host).toList() },
) {
    fun admitConnect(requestLine: String): ChromePhotosConnectTarget? {
        val target = ChromePhotosConnectTarget.parseSyntax(requestLine) ?: return null
        if (target.host == ChromePhotosDataPlaneLabContract.FixtureHost || target.host == ChromePhotosDataPlaneLabContract.OriginalUiSvgHost) return target
        return target.takeIf { resolvePublic(it.host).isNotEmpty() }
    }

    @Throws(UnknownHostException::class)
    fun resolvePublic(rawHost: String): List<InetAddress> {
        val host = normalizeDnsHost(rawHost)
        val addresses = resolver.lookup(host).distinctBy(InetAddress::getHostAddress)
        if (addresses.isEmpty() || addresses.any { !it.isPublicInternetAddress() }) {
            throw UnknownHostException("Destination is not entirely public")
        }
        return addresses
    }

    fun isSyntacticallyPublicHost(rawHost: String): Boolean =
        runCatching { normalizeDnsHost(rawHost) }
            .getOrNull()
            ?.let {
                it != ChromePhotosDataPlaneLabContract.FixtureHost && it != ChromePhotosDataPlaneLabContract.OriginalUiSvgHost
            } == true
}

internal class ChromeAuthorityDns(
    private val authority: ChromePublicDestinationAuthority,
) : Dns {
    override fun lookup(hostname: String): List<InetAddress> = authority.resolvePublic(hostname)
}

private fun InetAddress.isPublicInternetAddress(): Boolean {
    if (
        isAnyLocalAddress ||
        isLoopbackAddress ||
        isLinkLocalAddress ||
        isSiteLocalAddress ||
        isMulticastAddress
    ) {
        return false
    }
    return when (this) {
        is Inet4Address -> address.isPublicIpv4()
        is Inet6Address -> !isIPv4CompatibleAddress && address.isPublicIpv6()
        else -> false
    }
}

private fun ByteArray.isPublicIpv4(): Boolean {
    if (size != Ipv4Bytes) return false
    val first = this[0].toInt() and 0xff
    val second = this[1].toInt() and 0xff
    return when {
        first == 0 -> false
        first == 10 -> false
        first == 100 && second in 64..127 -> false
        first == 127 -> false
        first == 169 && second == 254 -> false
        first == 172 && second in 16..31 -> false
        first == 192 && second == 0 -> false
        first == 192 && second == 168 -> false
        first == 198 && second in 18..19 -> false
        first == 198 && second == 51 -> false
        first == 203 && second == 0 -> false
        first >= 224 -> false
        else -> true
    }
}

private fun ByteArray.isPublicIpv6(): Boolean {
    if (size != Ipv6Bytes) return false
    val first = this[0].toInt() and 0xff
    val second = this[1].toInt() and 0xff
    val documentation =
        first == 0x20 && second == 0x01 &&
            (this[2].toInt() and 0xff) == 0x0d && (this[3].toInt() and 0xff) == 0xb8
    val uniqueLocal = first and 0xfe == 0xfc
    val linkLocal = first == 0xfe && second and 0xc0 == 0x80
    val multicast = first == 0xff
    val ipv4Mapped = take(10).all { it == 0.toByte() } && this[10] == 0xff.toByte() && this[11] == 0xff.toByte()
    val unspecifiedOrLoopback = take(Ipv6Bytes - 1).all { it == 0.toByte() }
    val nat64EmbeddedIpv4 = nat64EmbeddedIpv4()
    return !documentation &&
        !uniqueLocal &&
        !linkLocal &&
        !multicast &&
        !ipv4Mapped &&
        !unspecifiedOrLoopback &&
        (nat64EmbeddedIpv4 == null || nat64EmbeddedIpv4.isPublicIpv4())
}

private fun ByteArray.nat64EmbeddedIpv4(): ByteArray? {
    val wellKnownPrefix =
        this[0] == 0x00.toByte() &&
            this[1] == 0x64.toByte() &&
            this[2] == 0xff.toByte() &&
            this[3] == 0x9b.toByte() &&
            sliceArray(4 until 12).all { it == 0.toByte() }
    val localUsePrefix =
        this[0] == 0x00.toByte() &&
            this[1] == 0x64.toByte() &&
            this[2] == 0xff.toByte() &&
            this[3] == 0x9b.toByte() &&
            this[4] == 0x00.toByte() &&
            this[5] == 0x01.toByte() &&
            sliceArray(6 until 12).all { it == 0.toByte() }
    return takeIf { wellKnownPrefix || localUsePrefix }?.copyOfRange(12, 16)
}

private const val Ipv4Bytes = 4
private const val Ipv6Bytes = 16
