package com.contentfilter.feature.vpn.service

import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class VpnConnectionOwnerResolverTest {
    @Test
    fun `TCP flow resolves valid UID and packages`() {
        val flow = flow(VpnTransportProtocol.Tcp)
        val resolver = resolver(owner = 10_123, packages = listOf("com.android.chrome"))

        val result = assertIs<VpnConnectionOwnerResult.Resolved>(resolver.resolve(flow))

        assertEquals(10_123, result.uid)
        assertEquals(listOf("com.android.chrome"), result.packages)
    }

    @Test
    fun `UDP flow resolves valid UID`() {
        val result =
            resolver(owner = 10_456, packages = listOf("com.example.second"))
                .resolve(flow(VpnTransportProtocol.Udp))

        assertEquals(10_456, assertIs<VpnConnectionOwnerResult.Resolved>(result).uid)
    }

    @Test
    fun `invalid UID remains unknown`() {
        assertEquals(
            VpnConnectionOwnerResult.Unknown,
            resolver(owner = -1).resolve(flow(VpnTransportProtocol.Tcp)),
        )
    }

    @Test
    fun `SecurityException is reported without escaping`() {
        val resolver =
            VpnConnectionOwnerResolver(
                ownerLookup = ConnectionOwnerLookup { throw SecurityException("not active VPN") },
                packageLookup = UidPackageLookup { emptyList() },
            )

        assertEquals(
            VpnConnectionOwnerResult.PermissionDenied,
            resolver.resolve(flow(VpnTransportProtocol.Udp)),
        )
    }

    @Test
    fun `null package lookup remains unknown and is not cached`() {
        var packageLookups = 0
        val resolver =
            VpnConnectionOwnerResolver(
                ownerLookup = ConnectionOwnerLookup { 10_321 },
                packageLookup =
                    UidPackageLookup {
                        packageLookups += 1
                        null
                    },
            )

        repeat(2) {
            assertEquals(VpnConnectionOwnerResult.Unknown, resolver.resolve(flow(VpnTransportProtocol.Tcp)))
        }
        assertEquals(2, packageLookups)
        assertEquals(0, resolver.cachedUidCount())
    }

    @Test
    fun `package lookup exception remains unknown and is not cached`() {
        var packageLookups = 0
        val resolver =
            VpnConnectionOwnerResolver(
                ownerLookup = ConnectionOwnerLookup { 10_322 },
                packageLookup =
                    UidPackageLookup {
                        packageLookups += 1
                        throw IllegalStateException("package manager unavailable")
                    },
            )

        repeat(2) {
            assertEquals(VpnConnectionOwnerResult.Unknown, resolver.resolve(flow(VpnTransportProtocol.Tcp)))
        }
        assertEquals(2, packageLookups)
        assertEquals(0, resolver.cachedUidCount())
    }

    @Test
    fun `empty package lookup remains unknown and is not cached`() {
        var packageLookups = 0
        val resolver =
            VpnConnectionOwnerResolver(
                ownerLookup = ConnectionOwnerLookup { 10_323 },
                packageLookup =
                    UidPackageLookup {
                        packageLookups += 1
                        emptyList()
                    },
            )

        repeat(2) {
            assertEquals(VpnConnectionOwnerResult.Unknown, resolver.resolve(flow(VpnTransportProtocol.Tcp)))
        }
        assertEquals(2, packageLookups)
        assertEquals(0, resolver.cachedUidCount())
    }

    @Test
    fun `package cache is bounded and avoids duplicate package resolution`() {
        var packageLookups = 0
        var owner = 1
        val resolver =
            VpnConnectionOwnerResolver(
                ownerLookup = ConnectionOwnerLookup { owner },
                packageLookup =
                    UidPackageLookup { uid ->
                        packageLookups += 1
                        listOf("package.$uid")
                    },
                packageCacheCapacity = 2,
            )

        resolver.resolve(flow(VpnTransportProtocol.Tcp))
        resolver.resolve(flow(VpnTransportProtocol.Udp))
        owner = 2
        resolver.resolve(flow(VpnTransportProtocol.Tcp))
        owner = 3
        resolver.resolve(flow(VpnTransportProtocol.Tcp))

        assertEquals(3, packageLookups)
        assertEquals(2, resolver.cachedUidCount())
    }

    @Test
    fun `multiple packages for UID are normalized for verification`() {
        val result =
            resolver(owner = 20_001, packages = listOf("z.package", "a.package", "z.package"))
                .resolve(flow(VpnTransportProtocol.Tcp))

        assertEquals(
            listOf("a.package", "z.package"),
            assertIs<VpnConnectionOwnerResult.Resolved>(result).packages,
        )
    }

    private fun resolver(
        owner: Int,
        packages: List<String> = emptyList(),
    ): VpnConnectionOwnerResolver =
        VpnConnectionOwnerResolver(
            ownerLookup = ConnectionOwnerLookup { owner },
            packageLookup = UidPackageLookup { packages },
        )

    private fun flow(protocol: VpnTransportProtocol): VpnFlowTuple =
        VpnFlowTuple(
            protocol = protocol,
            localAddress = InetSocketAddress(InetAddress.getByName("10.8.0.2"), 42_000),
            remoteAddress = InetSocketAddress(InetAddress.getByName("203.0.113.8"), 443),
        )
}
