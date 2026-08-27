package com.contentfilter.feature.vpn.service

import android.content.Context
import android.util.Log
import java.util.LinkedHashMap

/** DEV-only, bounded evidence collector for the 08B UID-attribution spike. */
internal class VpnConnectionOwnerDiagnostics private constructor(
    private val resolver: VpnConnectionOwnerResolver,
) {
    private val attemptsByFlow =
        object : LinkedHashMap<VpnFlowTuple, Int>(MaximumTrackedFlows, LoadFactor, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<VpnFlowTuple, Int>?): Boolean =
                size > MaximumTrackedFlows
        }

    fun observe(
        packet: ByteArray,
        length: Int,
    ) {
        val flow = VpnFlowTupleParser.parse(packet, length) ?: return
        val priorAttempts = synchronized(attemptsByFlow) { attemptsByFlow[flow] ?: 0 }
        if (priorAttempts >= MaximumLookupAttemptsPerFlow) return

        val result = resolver.resolve(flow)
        val attempts = priorAttempts + 1
        synchronized(attemptsByFlow) {
            attemptsByFlow[flow] =
                if (result is VpnConnectionOwnerResult.Unknown) attempts else MaximumLookupAttemptsPerFlow
        }
        Log.i(LogTag, result.toSanitizedLog(flow, attempts))
    }

    fun clear() {
        synchronized(attemptsByFlow) { attemptsByFlow.clear() }
        resolver.clear()
    }

    private fun VpnConnectionOwnerResult.toSanitizedLog(
        flow: VpnFlowTuple,
        attempt: Int,
    ): String {
        val base =
            "ownerLookup protocol=${flow.protocol.name.lowercase()} " +
                "local=${flow.localAddress.address.hostAddress}:${flow.localAddress.port} " +
                "remote=${flow.remoteAddress.address.hostAddress}:${flow.remoteAddress.port}"
        return when (this) {
            is VpnConnectionOwnerResult.Resolved ->
                "$base result=resolved uid=$uid packages=${packages.ifEmpty { listOf("unmapped") }.joinToString(",")}"
            VpnConnectionOwnerResult.Unknown -> "$base result=unknown attempt=$attempt"
            VpnConnectionOwnerResult.PermissionDenied -> "$base result=permission_denied"
        }
    }

    companion object {
        private const val LoadFactor = 0.75f
        private const val MaximumLookupAttemptsPerFlow = 3
        private const val MaximumTrackedFlows = 256
        private const val LogTag = "VpnOwnerSpike08B"

        fun create(context: Context): VpnConnectionOwnerDiagnostics =
            VpnConnectionOwnerDiagnostics(VpnConnectionOwnerResolver.create(context))
    }
}
