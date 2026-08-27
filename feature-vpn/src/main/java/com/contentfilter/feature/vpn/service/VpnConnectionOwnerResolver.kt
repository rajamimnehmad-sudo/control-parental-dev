package com.contentfilter.feature.vpn.service

import android.content.Context
import android.net.ConnectivityManager
import java.net.InetSocketAddress
import java.util.LinkedHashMap

internal enum class VpnTransportProtocol(
    val number: Int,
) {
    Tcp(6),
    Udp(17),
}

internal data class VpnFlowTuple(
    val protocol: VpnTransportProtocol,
    val localAddress: InetSocketAddress,
    val remoteAddress: InetSocketAddress,
)

internal sealed interface VpnConnectionOwnerResult {
    data class Resolved(
        val uid: Int,
        val packages: List<String>,
    ) : VpnConnectionOwnerResult

    data object Unknown : VpnConnectionOwnerResult

    data object PermissionDenied : VpnConnectionOwnerResult
}

internal fun interface ConnectionOwnerLookup {
    fun ownerUid(flow: VpnFlowTuple): Int
}

internal fun interface UidPackageLookup {
    fun packagesForUid(uid: Int): List<String>
}

/**
 * Resolves the owner of a TUN flow without retaining flow addresses or payloads.
 *
 * Only the UID-to-package diagnostic mapping is cached. Owner lookup remains per
 * flow because Android's connection table is the authority for every 5-tuple.
 */
internal class VpnConnectionOwnerResolver(
    private val ownerLookup: ConnectionOwnerLookup,
    private val packageLookup: UidPackageLookup,
    private val packageCacheCapacity: Int = DefaultPackageCacheCapacity,
) {
    init {
        require(packageCacheCapacity > 0)
    }

    private val packageCache =
        object : LinkedHashMap<Int, List<String>>(packageCacheCapacity, LoadFactor, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, List<String>>?): Boolean =
                size > packageCacheCapacity
        }

    fun resolve(flow: VpnFlowTuple): VpnConnectionOwnerResult {
        val uid =
            try {
                ownerLookup.ownerUid(flow)
            } catch (_: SecurityException) {
                return VpnConnectionOwnerResult.PermissionDenied
            } catch (_: IllegalArgumentException) {
                return VpnConnectionOwnerResult.Unknown
            }
        if (uid == InvalidUid) return VpnConnectionOwnerResult.Unknown

        val packages =
            synchronized(packageCache) {
                packageCache[uid]
                    ?: runCatching { packageLookup.packagesForUid(uid).distinct().sorted() }
                        .getOrDefault(emptyList())
                        .also { packageCache[uid] = it }
            }
        return VpnConnectionOwnerResult.Resolved(uid = uid, packages = packages)
    }

    fun clear() {
        synchronized(packageCache) { packageCache.clear() }
    }

    internal fun cachedUidCount(): Int = synchronized(packageCache) { packageCache.size }

    companion object {
        private const val InvalidUid = -1
        private const val DefaultPackageCacheCapacity = 32
        private const val LoadFactor = 0.75f

        fun create(context: Context): VpnConnectionOwnerResolver {
            val connectivityManager = requireNotNull(context.getSystemService(ConnectivityManager::class.java))
            return VpnConnectionOwnerResolver(
                ownerLookup =
                    ConnectionOwnerLookup { flow ->
                        connectivityManager.getConnectionOwnerUid(
                            flow.protocol.number,
                            flow.localAddress,
                            flow.remoteAddress,
                        )
                    },
                packageLookup =
                    UidPackageLookup { uid ->
                        context.packageManager.getPackagesForUid(uid)?.toList().orEmpty()
                    },
            )
        }
    }
}
