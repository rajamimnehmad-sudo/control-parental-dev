package com.contentfilter.feature.accessibility.chromevisual

import android.os.SystemClock
import com.glosh.visual.GloshiaVisualAction
import com.glosh.visual.GloshiaVisualPolicyContract
import java.security.MessageDigest

internal data class ChromeVisualShieldRegionSetBatchIdentity(
    val protectionSessionId: Long,
    val windowId: Int,
    val contentEpoch: Long,
    val viewportEpoch: Long,
    val captureSequence: Long,
    val regionSequence: Long,
    val discoverySequence: Long,
    val regionSetDigest: String,
) {
    companion object {
        fun from(
            identity: ChromeVisualShieldIdentity,
            complete: ChromeVisualShieldRegionDiscoveryResult.Complete,
        ) = ChromeVisualShieldRegionSetBatchIdentity(
            protectionSessionId = identity.protectionSessionId,
            windowId = identity.windowId,
            contentEpoch = identity.contentEpoch,
            viewportEpoch = identity.viewportEpoch,
            captureSequence = identity.captureSequence,
            regionSequence = identity.regionSequence,
            discoverySequence = complete.discoverySequence,
            regionSetDigest = complete.regionSetDigest,
        )
    }
}

internal enum class ChromeVisualShieldRegionSetAuthorityResult {
    Released,
    BlockProtected,
    UnknownProtected,
    MalformedProtected,
    StaleDropped,
    ReplayRejected,
    SurfaceRejected,
    ErrorProtected,
}

internal data class ChromeVisualShieldRegionSetAuthorityOutcome(
    val result: ChromeVisualShieldRegionSetAuthorityResult,
    val reason: String,
    val batchIdentity: ChromeVisualShieldRegionSetBatchIdentity?,
    val allSafe: Boolean,
    val batchCurrent: Boolean,
)

internal data class ChromeVisualShieldRegionSetMetricsSnapshot(
    val batchesEvaluated: Long,
    val releaseAccepted: Long,
    val releaseRejected: Long,
    val blockProtected: Long,
    val unknownProtected: Long,
    val malformedProtected: Long,
    val staleDropped: Long,
    val replayRejected: Long,
    val surfaceRejected: Long,
    val errorProtected: Long,
    val allSafe: Boolean,
    val batchCurrent: Boolean,
    val lastBatchDigest: String?,
    val releaseBatchDigest: String?,
    val authorityAcceptedAtNanos: Long,
    val releaseAtNanos: Long,
    val retainedReplayKeys: Int,
)

internal class ChromeVisualShieldRegionSetMetrics {
    private var batchesEvaluated = 0L
    private var releaseAccepted = 0L
    private var releaseRejected = 0L
    private var blockProtected = 0L
    private var unknownProtected = 0L
    private var malformedProtected = 0L
    private var staleDropped = 0L
    private var replayRejected = 0L
    private var surfaceRejected = 0L
    private var errorProtected = 0L
    private var allSafe = false
    private var batchCurrent = false
    private var lastBatchDigest: String? = null
    private var releaseBatchDigest: String? = null
    private var authorityAcceptedAtNanos = 0L
    private var releaseAtNanos = 0L

    @Synchronized
    fun onEvaluated(
        batch: ChromeVisualShieldRegionSetBatchIdentity?,
        allSafe: Boolean,
        batchCurrent: Boolean,
    ) {
        batchesEvaluated += 1
        this.allSafe = allSafe
        this.batchCurrent = batchCurrent
        lastBatchDigest = batch?.regionSetDigest
    }

    @Synchronized
    fun onAuthorityAccepted(atNanos: Long) {
        authorityAcceptedAtNanos = atNanos
    }

    @Synchronized
    fun onReleased(
        batch: ChromeVisualShieldRegionSetBatchIdentity,
        atNanos: Long,
    ) {
        releaseAccepted += 1
        releaseBatchDigest = batch.regionSetDigest
        releaseAtNanos = atNanos
        batchCurrent = true
        allSafe = true
    }

    @Synchronized
    fun onRejected(result: ChromeVisualShieldRegionSetAuthorityResult) {
        releaseRejected += 1
        when (result) {
            ChromeVisualShieldRegionSetAuthorityResult.BlockProtected -> blockProtected += 1
            ChromeVisualShieldRegionSetAuthorityResult.UnknownProtected -> unknownProtected += 1
            ChromeVisualShieldRegionSetAuthorityResult.MalformedProtected -> malformedProtected += 1
            ChromeVisualShieldRegionSetAuthorityResult.StaleDropped -> staleDropped += 1
            ChromeVisualShieldRegionSetAuthorityResult.ReplayRejected -> replayRejected += 1
            ChromeVisualShieldRegionSetAuthorityResult.SurfaceRejected -> surfaceRejected += 1
            ChromeVisualShieldRegionSetAuthorityResult.ErrorProtected -> errorProtected += 1
            ChromeVisualShieldRegionSetAuthorityResult.Released -> Unit
        }
    }

    @Synchronized
    fun snapshot(retainedReplayKeys: Int): ChromeVisualShieldRegionSetMetricsSnapshot =
        ChromeVisualShieldRegionSetMetricsSnapshot(
            batchesEvaluated = batchesEvaluated,
            releaseAccepted = releaseAccepted,
            releaseRejected = releaseRejected,
            blockProtected = blockProtected,
            unknownProtected = unknownProtected,
            malformedProtected = malformedProtected,
            staleDropped = staleDropped,
            replayRejected = replayRejected,
            surfaceRejected = surfaceRejected,
            errorProtected = errorProtected,
            allSafe = allSafe,
            batchCurrent = batchCurrent,
            lastBatchDigest = lastBatchDigest,
            releaseBatchDigest = releaseBatchDigest,
            authorityAcceptedAtNanos = authorityAcceptedAtNanos,
            releaseAtNanos = releaseAtNanos,
            retainedReplayKeys = retainedReplayKeys,
        )
}

/** R2B DEV-only atomic authority. It never consults the fixture oracle. */
internal class ChromeVisualShieldRegionSetAuthority(
    private val identityGate: ChromeVisualShieldIdentityGate,
    private val metrics: ChromeVisualShieldRegionSetMetrics,
    private val r1Metrics: ChromeVisualShieldR1Metrics,
    private val isSurfaceCurrent: (ChromeVisualShieldIdentity) -> Boolean,
    private val releaseSurface: () -> Unit,
    private val reprotectSurface: () -> Unit,
    private val beforeReleaseBoundary: () -> Unit = {},
    private val monotonicNanos: () -> Long = SystemClock::elapsedRealtimeNanos,
) {
    private var lastReleasedBatch: ChromeVisualShieldRegionSetBatchIdentity? = null

    fun apply(delivery: ChromeVisualShieldRegionDiscoveryDelivery): ChromeVisualShieldRegionSetAuthorityOutcome =
        try {
            applyChecked(delivery)
        } catch (_: Throwable) {
            identityGate.abortRegionSetRelease(delivery.work.identity)
            identityGate.failClosed(delivery.work.identity)
            runCatching(reprotectSurface)
            reject(
                result = ChromeVisualShieldRegionSetAuthorityResult.ErrorProtected,
                reason = "authority_exception",
                batch = null,
                allSafe = false,
                batchCurrent = false,
            )
        }

    fun snapshot(): ChromeVisualShieldRegionSetMetricsSnapshot =
        metrics.snapshot(if (lastReleasedBatch == null) 0 else 1)

    private fun applyChecked(
        delivery: ChromeVisualShieldRegionDiscoveryDelivery,
    ): ChromeVisualShieldRegionSetAuthorityOutcome {
        val identity = delivery.work.identity
        val complete =
            delivery.discovery as? ChromeVisualShieldRegionDiscoveryResult.Complete
                ?: return protectUnknown(delivery)
        val batch = ChromeVisualShieldRegionSetBatchIdentity.from(identity, complete)
        val validation = validate(delivery, complete, batch)
        if (validation != null) {
            return protectCurrent(
                identity = identity,
                result = ChromeVisualShieldRegionSetAuthorityResult.MalformedProtected,
                reason = validation,
                batch = batch,
                allSafe = false,
            )
        }
        if (lastReleasedBatch == batch) {
            return reject(
                result = ChromeVisualShieldRegionSetAuthorityResult.ReplayRejected,
                reason = "batch_already_released",
                batch = batch,
                allSafe = true,
                batchCurrent = false,
            )
        }
        if (!identityGate.isCurrentProcessing(identity)) {
            return rejectStale(batch, "identity_not_current_processing")
        }
        val allSafe = delivery.decisions.all { it.isModelAllow() }
        if (!allSafe) {
            return protectCurrent(
                identity = identity,
                result = ChromeVisualShieldRegionSetAuthorityResult.BlockProtected,
                reason = "not_all_regions_model_allow",
                batch = batch,
                allSafe = false,
            )
        }
        if (!isSurfaceCurrent(identity)) {
            return protectCurrent(
                identity = identity,
                result = ChromeVisualShieldRegionSetAuthorityResult.SurfaceRejected,
                reason = "surface_not_current_before_authority",
                batch = batch,
                allSafe = true,
            )
        }
        if (identityGate.beginRegionSetRelease(identity) is ChromeVisualShieldResult.Stale) {
            return rejectStale(batch, "release_boundary_stale")
        }
        beforeReleaseBoundary()
        if (!identityGate.isCurrentRegionSetRelease(identity) || !isSurfaceCurrent(identity)) {
            identityGate.abortRegionSetRelease(identity)
            return rejectStale(batch, "invalidated_before_release")
        }
        if (lastReleasedBatch == batch) {
            identityGate.abortRegionSetRelease(identity)
            return reject(
                result = ChromeVisualShieldRegionSetAuthorityResult.ReplayRejected,
                reason = "batch_replayed_at_release_boundary",
                batch = batch,
                allSafe = true,
                batchCurrent = false,
            )
        }

        val acceptedAt = monotonicNanos()
        metrics.onEvaluated(batch, allSafe = true, batchCurrent = true)
        metrics.onAuthorityAccepted(acceptedAt)
        r1Metrics.onSafeCurrent()
        r1Metrics.onSafeDecisionAccepted(acceptedAt)
        try {
            releaseSurface()
        } catch (failure: Throwable) {
            identityGate.abortRegionSetRelease(identity)
            identityGate.failClosed(identity)
            runCatching(reprotectSurface)
            throw failure
        }
        if (identityGate.completeRegionSetRelease(identity) is ChromeVisualShieldResult.Stale) {
            runCatching(reprotectSurface)
            return rejectStale(batch, "invalidated_during_surface_release")
        }
        lastReleasedBatch = batch
        val releasedAt = monotonicNanos()
        metrics.onReleased(batch, releasedAt)
        r1Metrics.onReleaseCurrent(releasedAt)
        return ChromeVisualShieldRegionSetAuthorityOutcome(
            result = ChromeVisualShieldRegionSetAuthorityResult.Released,
            reason = "all_regions_current_model_allow",
            batchIdentity = batch,
            allSafe = true,
            batchCurrent = true,
        )
    }

    private fun protectUnknown(
        delivery: ChromeVisualShieldRegionDiscoveryDelivery,
    ): ChromeVisualShieldRegionSetAuthorityOutcome {
        val malformed = delivery.decisions.isNotEmpty()
        return protectCurrent(
            identity = delivery.work.identity,
            result =
                if (malformed) {
                    ChromeVisualShieldRegionSetAuthorityResult.MalformedProtected
                } else {
                    ChromeVisualShieldRegionSetAuthorityResult.UnknownProtected
                },
            reason = if (malformed) "unknown_with_decisions" else "discovery_unknown",
            batch = null,
            allSafe = false,
        )
    }

    private fun protectCurrent(
        identity: ChromeVisualShieldIdentity,
        result: ChromeVisualShieldRegionSetAuthorityResult,
        reason: String,
        batch: ChromeVisualShieldRegionSetBatchIdentity?,
        allSafe: Boolean,
    ): ChromeVisualShieldRegionSetAuthorityOutcome {
        if (identityGate.completeProcessing(identity) is ChromeVisualShieldResult.Stale) {
            return rejectStale(batch, "stale_while_protecting_$reason")
        }
        return reject(result, reason, batch, allSafe, batchCurrent = true)
    }

    private fun rejectStale(
        batch: ChromeVisualShieldRegionSetBatchIdentity?,
        reason: String,
    ): ChromeVisualShieldRegionSetAuthorityOutcome {
        r1Metrics.onStaleInferenceDropped()
        return reject(
            ChromeVisualShieldRegionSetAuthorityResult.StaleDropped,
            reason,
            batch,
            allSafe = false,
            batchCurrent = false,
        )
    }

    private fun reject(
        result: ChromeVisualShieldRegionSetAuthorityResult,
        reason: String,
        batch: ChromeVisualShieldRegionSetBatchIdentity?,
        allSafe: Boolean,
        batchCurrent: Boolean,
    ): ChromeVisualShieldRegionSetAuthorityOutcome {
        metrics.onEvaluated(batch, allSafe, batchCurrent)
        metrics.onRejected(result)
        when (result) {
            ChromeVisualShieldRegionSetAuthorityResult.BlockProtected -> r1Metrics.onBlockCurrent()
            ChromeVisualShieldRegionSetAuthorityResult.UnknownProtected,
            ChromeVisualShieldRegionSetAuthorityResult.MalformedProtected,
            ChromeVisualShieldRegionSetAuthorityResult.SurfaceRejected,
            ChromeVisualShieldRegionSetAuthorityResult.ErrorProtected,
            -> r1Metrics.onFailClosedCurrent()
            ChromeVisualShieldRegionSetAuthorityResult.StaleDropped,
            ChromeVisualShieldRegionSetAuthorityResult.ReplayRejected,
            ChromeVisualShieldRegionSetAuthorityResult.Released,
            -> Unit
        }
        r1Metrics.onReleaseRejected()
        return ChromeVisualShieldRegionSetAuthorityOutcome(result, reason, batch, allSafe, batchCurrent)
    }

    private fun validate(
        delivery: ChromeVisualShieldRegionDiscoveryDelivery,
        complete: ChromeVisualShieldRegionDiscoveryResult.Complete,
        batch: ChromeVisualShieldRegionSetBatchIdentity,
    ): String? {
        val regions = complete.regions
        if (complete.discoverySequence <= 0) return "invalid_discovery_sequence"
        if (regions.isEmpty()) return "empty_region_set"
        if (regions.map { it.id }.distinct().size != regions.size) return "duplicate_region_id"
        if (
            regions.any {
                it.id.isBlank() || it.bounds.width <= 0 || it.bounds.height <= 0 ||
                    it.visualSignature.matches(Sha256Regex).not() || it.assignedPixels <= 0
            }
        ) {
            return "invalid_region"
        }
        val expectedDigest = ChromeVisualShieldRegionSetDigest.compute(delivery.work.identity, complete)
        if (complete.regionSetDigest != expectedDigest) return "digest_mismatch"
        val decisions = delivery.decisions
        if (decisions.size != regions.size) return "decision_count_mismatch"
        if (decisions.map { it.region.id }.distinct().size != decisions.size) return "duplicate_decision"
        if (decisions.any { it.batchIdentity != batch }) return "decision_batch_mismatch"
        if (decisions.any { it.decision.candidateId != it.region.id }) return "candidate_id_mismatch"
        if (decisions.map { it.region } != regions) return "region_decision_mismatch"
        return null
    }

    private fun ChromeVisualShieldRegionDecision.isModelAllow(): Boolean =
        decision.action == GloshiaVisualAction.Allow &&
            decision.reason == GloshiaVisualPolicyContract.ModelAllowReason

    private companion object {
        val Sha256Regex = Regex("[0-9a-f]{64}")
    }
}

internal object ChromeVisualShieldRegionSetDigest {
    fun compute(
        identity: ChromeVisualShieldIdentity,
        complete: ChromeVisualShieldRegionDiscoveryResult.Complete,
    ): String =
        sha256(
            buildString {
                append(identity.protectionSessionId).append('|')
                append(identity.windowId).append('|')
                append(identity.contentEpoch).append('|')
                append(identity.viewportEpoch).append('|')
                append(identity.captureSequence).append('|')
                append(complete.discoverySequence)
                complete.regions.forEach { region ->
                    append('|').append(region.id).append(':').append(region.bounds.left).append(',')
                    append(region.bounds.top).append(',').append(region.bounds.right).append(',')
                    append(region.bounds.bottom).append(':').append(region.visualSignature)
                }
            },
        )

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
