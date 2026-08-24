package com.contentfilter.feature.vpn.transport

internal enum class VpnTransportRuntimeState {
    Ready,
    Running,
    Quarantined,
}

internal data class VpnTransportRuntimeSnapshot(
    val state: VpnTransportRuntimeState,
    val generation: Long,
    val reason: String?,
)

/** Process-scoped authority that prevents restart after an incomplete native/SOCKS shutdown. */
internal object VpnTransportRuntimeAuthority {
    private var state = VpnTransportRuntimeState.Ready
    private var generation = 0L
    private var reason: String? = null

    @Synchronized
    fun begin(): Long {
        check(state == VpnTransportRuntimeState.Ready) { "Transport unavailable state=$state reason=$reason" }
        generation++
        state = VpnTransportRuntimeState.Running
        reason = null
        return generation
    }

    @Synchronized
    fun finish(
        token: Long,
        clean: Boolean,
        dirtyReason: String?,
    ) {
        if (token != generation) return
        if (clean) {
            state = VpnTransportRuntimeState.Ready
            reason = null
        } else {
            state = VpnTransportRuntimeState.Quarantined
            reason = dirtyReason ?: "dirty_shutdown"
        }
    }

    @Synchronized
    fun snapshot(): VpnTransportRuntimeSnapshot = VpnTransportRuntimeSnapshot(state, generation, reason)

    @Synchronized
    internal fun resetForTest() {
        state = VpnTransportRuntimeState.Ready
        generation = 0L
        reason = null
    }
}
