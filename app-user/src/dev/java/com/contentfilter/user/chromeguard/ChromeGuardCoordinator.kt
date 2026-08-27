package com.contentfilter.user.chromeguard

internal interface ChromeGuardGenerationStore {
    fun nextGeneration(): Long

    fun recordState(
        suspended: Boolean,
        reason: String,
        bootMarker: Long,
    )

    fun incrementRestartCount(): Long
}

internal interface ChromeSuspensionPort {
    fun ensureSuspended(reason: String): Boolean

    fun ensureReleased(): Boolean
}

internal class ChromeGuardCoordinator(
    private val generationStore: ChromeGuardGenerationStore,
    private val suspension: ChromeSuspensionPort,
    private val currentBootMarker: () -> Long,
) {
    private var state = ChromeGuardState.Suspended
    private var expectedSession: ChromeGuardExpectedSession? = null
    private var leaseExpiresAtElapsed = 0L
    private var lastReason = "guard_start"
    private var acceptedHeartbeats = 0L
    private var staleRejects = 0L
    private var wrongCallerRejects = 0L
    private val guardRestarts = generationStore.incrementRestartCount()

    @Synchronized
    fun initialize(reason: String = "guard_start"): Boolean = suspendAndInvalidate(reason)

    @Synchronized
    fun beginSession(
        request: ChromeGuardSessionRequest,
        callerAuthorized: Boolean,
    ): Long? {
        if (!callerAuthorized) {
            wrongCallerRejects++
            suspendAndInvalidate("wrong_caller")
            return null
        }
        if (
            request.sessionId.isBlank() ||
            request.mainProcessNonce.isBlank() ||
            request.bootMarker != currentBootMarker() ||
            request.bootstrapGeneration <= 0
        ) {
            staleRejects++
            suspendAndInvalidate("session_stale")
            return null
        }
        if (!suspendAndInvalidate("session_pending_health")) return null
        val generation = generationStore.nextGeneration()
        expectedSession =
            ChromeGuardExpectedSession(
                protectionGeneration = generation,
                sessionId = request.sessionId,
                mainProcessNonce = request.mainProcessNonce,
                bootMarker = request.bootMarker,
                bootstrapGeneration = request.bootstrapGeneration,
            )
        return generation
    }

    @Synchronized
    fun heartbeat(
        lease: ChromeGuardLease,
        nowElapsed: Long,
        callerAuthorized: Boolean,
    ): ChromeGuardLeaseVerification {
        val verification =
            ChromeGuardLeaseVerifier.verify(
                lease = lease,
                expected = expectedSession,
                currentBootMarker = currentBootMarker(),
                nowElapsed = nowElapsed,
                callerAuthorized = callerAuthorized,
            )
        if (verification is ChromeGuardLeaseVerification.Rejected) {
            if (verification.reason == "wrong_caller") wrongCallerRejects++ else staleRejects++
            suspendAndInvalidate(verification.reason)
            return verification
        }
        val released = suspension.ensureReleased()
        if (!released) {
            lastReason = "suspension_unverified"
            expectedSession = null
            leaseExpiresAtElapsed = 0L
            val resuspended = suspension.ensureSuspended(lastReason)
            state = if (resuspended) ChromeGuardState.Suspended else ChromeGuardState.Unverified
            generationStore.recordState(resuspended, lastReason, currentBootMarker())
            return ChromeGuardLeaseVerification.Rejected(lastReason)
        }
        expectedSession = expectedSession?.copy(lastHeartbeatSequence = lease.heartbeatSequence)
        leaseExpiresAtElapsed = lease.expiresAtElapsedRealtime
        acceptedHeartbeats++
        state = ChromeGuardState.Released
        lastReason = "lease_current"
        generationStore.recordState(false, lastReason, currentBootMarker())
        return ChromeGuardLeaseVerification.Accepted
    }

    @Synchronized
    fun expireIfNeeded(nowElapsed: Long): Boolean {
        if (state != ChromeGuardState.Released || nowElapsed < leaseExpiresAtElapsed) return false
        return suspendAndInvalidate("main_process_lost")
    }

    @Synchronized
    fun revoke(reason: String): Boolean = suspendAndInvalidate(reason)

    @Synchronized
    fun snapshot(): ChromeGuardSnapshot =
        ChromeGuardSnapshot(
            state = state,
            protectionGeneration = expectedSession?.protectionGeneration ?: 0L,
            sessionId = expectedSession?.sessionId.orEmpty(),
            leaseExpiresAtElapsed = leaseExpiresAtElapsed,
            lastReason = lastReason,
            acceptedHeartbeats = acceptedHeartbeats,
            staleRejects = staleRejects,
            wrongCallerRejects = wrongCallerRejects,
            guardRestarts = guardRestarts,
        )

    private fun suspendAndInvalidate(reason: String): Boolean {
        expectedSession = null
        leaseExpiresAtElapsed = 0L
        lastReason = reason
        val suspended = suspension.ensureSuspended(reason)
        state = if (suspended) ChromeGuardState.Suspended else ChromeGuardState.Unverified
        generationStore.recordState(true, if (suspended) reason else "suspension_unverified", currentBootMarker())
        return suspended
    }
}
