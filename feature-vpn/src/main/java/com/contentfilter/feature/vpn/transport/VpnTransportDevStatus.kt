package com.contentfilter.feature.vpn.transport

internal fun VpnTransportGateMetrics.toDevStatusLog(): String =
    "status=active generation=$generation scope=${scope.name.lowercase()} " +
        "bridge=${packetSocketType.name.lowercase()} queuePeak=$queuePeak queueDrops=$queueDrops " +
        "forwarded=$forwardedPackets returned=$returnedPackets " +
        "chromeTcpDrops=$chromeTcpDrops chromeUdpDrops=$chromeUdpDrops " +
        "unknownDrops=$unknownOwnerDrops recursion=$recursionPackets " +
        "dnsTcpDrops=$dnsTcpDrops proxyPackets=$authorizedProxyPackets " +
        "ownerTimeouts=${ownerCache.lookupTimeouts} ownerQueueDrops=${ownerCache.queueRejections} " +
        "networkInvalidations=$networkInvalidations transportRuntime=${runtime.state.name.lowercase()} " +
        "hevTxPackets=${hev.txPackets} hevRxPackets=${hev.rxPackets} " +
        "hevLifecycle=${hevLifecycle.state.name.lowercase()} hevJoinTimeouts=${hevLifecycle.joinTimeouts} " +
        "hevCleanupCount=${hevLifecycle.cleanupCount} socksTcp=${socks.tcpConnects} " +
        "socksUdpAssociations=${socks.udpAssociations} socksUdpOut=${socks.udpDatagramsOut} " +
        "socksUdpIn=${socks.udpDatagramsIn} socksUdpInvalid=${socks.malformedUdpDatagrams} " +
        "socksUdpFragmentsDropped=${socks.udpFragmentsDropped} " +
        "activeUdpAssociations=${socks.activeUdpAssociations} " +
        "activeUdpAssociationsPeak=${socks.activeUdpAssociationsPeak} " +
        "protectFailures=${protectedSockets.protectFailures} " +
        "protectedUdpSocketsCreated=${protectedSockets.protectedUdpSocketsCreated} " +
        "protectUdpSuccess=${protectedSockets.protectUdpSuccess} " +
        "protectUdpFailure=${protectedSockets.protectUdpFailure} " +
        "ownedFdResources=${resources.ownedFdResources} ownedFdPeak=${resources.ownedFdResourcesPeak} " +
        "activeProtectedUdpSockets=${resources.activeProtectedUdpSockets} " +
        "protectedUdpPeak=${resources.activeProtectedUdpSocketsPeak} " +
        "malformedEmpty=${socks.malformedProbeEmptySent} " +
        "malformedTruncated=${socks.malformedProbeTruncatedSent} " +
        "malformedInvalidHeader=${socks.malformedProbeInvalidHeaderSent} " +
        "socksSessionIoFailures=${socks.sessionIoFailures} " +
        "socksShutdownTimeouts=${socks.executorShutdownTimeouts}"

internal fun VpnOwnedResourceSnapshot.toDevInactiveStatusLog(runtime: VpnTransportRuntimeSnapshot): String =
    "status=inactive ownedFdResources=$ownedFdResources ownedFdPeak=$ownedFdResourcesPeak " +
        "activeProtectedUdpSockets=$activeProtectedUdpSockets " +
        "protectedUdpPeak=$activeProtectedUdpSocketsPeak " +
        "transportRuntime=${runtime.state.name.lowercase()}"
