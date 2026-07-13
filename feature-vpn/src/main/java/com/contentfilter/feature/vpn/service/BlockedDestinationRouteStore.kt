package com.contentfilter.feature.vpn.service

import com.contentfilter.feature.vpn.dns.DnsAnswerAddresses
import java.net.InetAddress
import java.security.MessageDigest
import java.util.LinkedHashMap

internal class BlockedDestinationRouteStore(
    private val maxRoutes: Int = DefaultMaxRoutes,
) {
    private val routes = LinkedHashMap<String, InetAddress>()
    private val domainRoutes = mutableMapOf<List<Byte>, Set<String>>()

    init {
        require(maxRoutes > 0)
    }

    @Synchronized
    fun beginPreparation(domain: String): Boolean {
        val fingerprint = domain.fingerprint()
        if (domainRoutes.containsKey(fingerprint)) return false
        domainRoutes[fingerprint] = emptySet()
        return true
    }

    @Synchronized
    fun add(
        domain: String,
        answers: DnsAnswerAddresses,
    ): Boolean {
        var routesChanged = false
        val fingerprint = domain.fingerprint()
        val addressKeys = domainRoutes[fingerprint].orEmpty().toMutableSet()
        answers.addresses.forEach { address ->
            val key = address.hostAddress.orEmpty()
            val previous = routes.remove(key)
            routes[key] = address
            addressKeys += key
            routesChanged = routesChanged || previous == null
        }
        while (routes.size > maxRoutes) {
            val removedKey = routes.keys.first()
            routes.remove(removedKey)
            domainRoutes.replaceAll { _, keys -> keys - removedKey }
            domainRoutes.entries.removeAll { it.value.isEmpty() }
            routesChanged = true
        }
        if (addressKeys.any(routes::containsKey)) {
            domainRoutes[fingerprint] = addressKeys.filterTo(mutableSetOf(), routes::containsKey)
        } else {
            domainRoutes.remove(fingerprint)
        }
        return routesChanged
    }

    @Synchronized
    fun activeRoutes(): List<InetAddress> = routes.values.toList()

    @Synchronized
    fun forgetDomain(domain: String) {
        domainRoutes.remove(domain.fingerprint())
    }

    @Synchronized
    fun clear() {
        routes.clear()
        domainRoutes.clear()
    }

    private fun String.fingerprint(): List<Byte> =
        MessageDigest.getInstance(DomainFingerprintAlgorithm).digest(encodeToByteArray()).asList()

    private companion object {
        const val DefaultMaxRoutes = 128
        const val DomainFingerprintAlgorithm = "SHA-256"
    }
}
