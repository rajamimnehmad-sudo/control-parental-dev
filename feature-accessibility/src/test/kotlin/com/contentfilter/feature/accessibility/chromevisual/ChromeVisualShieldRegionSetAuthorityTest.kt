package com.contentfilter.feature.accessibility.chromevisual

import com.glosh.visual.GloshiaVisualAction
import com.glosh.visual.GloshiaVisualDecision
import com.glosh.visual.GloshiaVisualPolicyContract
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private typealias BatchMutator =
    (ChromeVisualShieldRegionSetBatchIdentity) -> ChromeVisualShieldRegionSetBatchIdentity

class ChromeVisualShieldRegionSetAuthorityTest {
    @Test
    fun `DEV region set request permits repeated source for distinct rendered regions`() {
        assertTrue(
            ChromeVisualShieldRegionDiscoveryProbeRequest(
                scenarioId = "multi-all-safe",
                sourceSha256s = listOf("1".repeat(64), "1".repeat(64)),
                renderContract = "canvas-content-islands-v3",
                gateMode = ChromeVisualShieldRegionDiscoveryGateMode.RegionSetAuthority,
            ).isValid(),
        )
    }

    @Test
    fun `single current model allow releases exactly once`() {
        val harness = Harness()
        val identity = harness.processingIdentity()

        val outcome = harness.authority.apply(harness.delivery(identity, listOf(allow("region-1"))))

        assertEquals(ChromeVisualShieldRegionSetAuthorityResult.Released, outcome.result)
        assertEquals(1, harness.releases)
        assertEquals(1, harness.gate.snapshot().labReleaseCount)
        assertEquals(1, harness.r1Metrics.snapshot().releaseCurrent)
        assertEquals(outcome.batchIdentity?.regionSetDigest, harness.authority.snapshot().releaseBatchDigest)
    }

    @Test
    fun `multiple current allows release only after exact complete decision set`() {
        val partial = Harness()
        val partialIdentity = partial.processingIdentity()
        val partialOutcome =
            partial.authority.apply(
                partial.delivery(
                    partialIdentity,
                    decisions = listOf(allow("region-1")),
                    regionCount = 2,
                ),
            )
        assertEquals(ChromeVisualShieldRegionSetAuthorityResult.MalformedProtected, partialOutcome.result)
        assertEquals(0, partial.releases)

        val complete = Harness()
        val completeIdentity = complete.processingIdentity()
        val outcome =
            complete.authority.apply(
                complete.delivery(
                    completeIdentity,
                    decisions = listOf(allow("region-1"), allow("region-2")),
                    regionCount = 2,
                ),
            )
        assertEquals(ChromeVisualShieldRegionSetAuthorityResult.Released, outcome.result)
        assertEquals(1, complete.releases)
    }

    @Test
    fun `any model filter keeps complete batch protected`() {
        listOf(
            listOf(allow("region-1"), block("region-2")),
            listOf(block("region-1"), block("region-2")),
        ).forEach { decisions ->
            val harness = Harness()
            val identity = harness.processingIdentity()

            val outcome = harness.authority.apply(harness.delivery(identity, decisions, regionCount = 2))

            assertEquals(ChromeVisualShieldRegionSetAuthorityResult.BlockProtected, outcome.result)
            assertEquals(0, harness.releases)
            assertEquals(ChromeVisualShieldPhase.Protected, harness.gate.snapshot().phase)
        }
    }

    @Test
    fun `unknown never releases with no decisions or forged safe decisions`() {
        listOf(emptyList(), listOf(allow("region-1"))).forEach { decisions ->
            val harness = Harness()
            val identity = harness.processingIdentity()
            val delivery = harness.unknownDelivery(identity, decisions)

            val outcome = harness.authority.apply(delivery)

            assertTrue(
                outcome.result == ChromeVisualShieldRegionSetAuthorityResult.UnknownProtected ||
                    outcome.result == ChromeVisualShieldRegionSetAuthorityResult.MalformedProtected,
            )
            assertEquals(0, harness.releases)
            assertEquals(ChromeVisualShieldPhase.Protected, harness.gate.snapshot().phase)
        }
    }

    @Test
    fun `missing extra duplicate and mismatched decisions fail closed`() {
        val structuralCases =
            listOf<(Harness, ChromeVisualShieldIdentity) -> ChromeVisualShieldRegionDiscoveryDelivery>(
                { h, id -> h.delivery(id, listOf(allow("region-1")), regionCount = 2) },
                { h, id ->
                    h.delivery(
                        id,
                        listOf(allow("region-1"), allow("region-2"), allow("region-extra")),
                        regionCount = 2,
                    )
                },
                { h, id ->
                    h.delivery(
                        id,
                        listOf(allow("region-1"), allow("region-1")),
                        regionCount = 2,
                    )
                },
                { h, id ->
                    h.delivery(id, listOf(allow("wrong")), regionCount = 1, forceRegionOnDecision = true)
                },
                { h, id ->
                    h.delivery(id, listOf(allow("region-1")), mutateDigest = { "e".repeat(64) })
                },
            )
        val batchMutators =
            listOf<BatchMutator>(
                { it.copy(regionSetDigest = "f".repeat(64)) },
                { it.copy(discoverySequence = 99) },
                { it.copy(captureSequence = 99) },
                { it.copy(regionSequence = 99) },
                { it.copy(viewportEpoch = 99) },
                { it.copy(contentEpoch = 99) },
                { it.copy(windowId = 99) },
                { it.copy(protectionSessionId = 99) },
            )
        val batchCases =
            batchMutators.map { mutate ->
                { h: Harness, id: ChromeVisualShieldIdentity ->
                    h.delivery(id, listOf(allow("region-1")), mutateDecisionBatch = mutate)
                }
            }
        val cases = structuralCases + batchCases
        cases.forEach { create ->
            val harness = Harness()
            val identity = harness.processingIdentity()

            val outcome = harness.authority.apply(create(harness, identity))

            assertEquals(ChromeVisualShieldRegionSetAuthorityResult.MalformedProtected, outcome.result)
            assertEquals(0, harness.releases)
            assertTrue(harness.gate.snapshot().isFailClosed)
        }
    }

    @Test
    fun `empty complete batch and invalid region are rejected`() {
        listOf(true, false).forEach { empty ->
            val harness = Harness()
            val identity = harness.processingIdentity()
            val delivery =
                if (empty) {
                    harness.delivery(identity, emptyList(), regionCount = 0)
                } else {
                    harness.delivery(identity, listOf(allow("region-1")), invalidRegion = true)
                }

            val outcome = harness.authority.apply(delivery)

            assertEquals(ChromeVisualShieldRegionSetAuthorityResult.MalformedProtected, outcome.result)
            assertEquals(0, harness.releases)
        }
    }

    @Test
    fun `stale identity epoch viewport capture and region sequences never release`() {
        val invalidations =
            listOf(
                ChromeVisualShieldInvalidation.Navigation,
                ChromeVisualShieldInvalidation.Viewport,
                ChromeVisualShieldInvalidation.Rotation,
                ChromeVisualShieldInvalidation.Scroll,
            )
        invalidations.forEach { invalidation ->
            val harness = Harness()
            val identity = harness.processingIdentity()
            val viewport =
                if (invalidation == ChromeVisualShieldInvalidation.Viewport ||
                    invalidation == ChromeVisualShieldInvalidation.Rotation
                ) {
                    ChromeVisualViewport(0, 0, 200, 100)
                } else {
                    Viewport
                }
            harness.gate.invalidate(7, viewport, Contract, invalidation)

            val outcome = harness.authority.apply(harness.delivery(identity, listOf(allow("region-1"))))

            assertEquals(ChromeVisualShieldRegionSetAuthorityResult.StaleDropped, outcome.result)
            assertEquals(0, harness.releases)
        }
    }

    @Test
    fun `window replacement cancellation stop and late delivery remain fail closed`() {
        val actions: List<(Harness) -> Unit> =
            listOf(
                { h -> h.gate.invalidate(9, Viewport, Contract, ChromeVisualShieldInvalidation.WindowReplaced) },
                { h -> h.gate.failClosed(null) },
                { h -> h.gate.stop() },
            )
        actions.forEach { invalidate ->
            val harness = Harness()
            val identity = harness.processingIdentity()
            invalidate(harness)

            val outcome = harness.authority.apply(harness.delivery(identity, listOf(allow("region-1"))))

            assertEquals(ChromeVisualShieldRegionSetAuthorityResult.StaleDropped, outcome.result)
            assertEquals(0, harness.releases)
        }
    }

    @Test
    fun `invalidation at final authority boundary rejects release`() {
        lateinit var harness: Harness
        harness =
            Harness(
                beforeRelease = {
                    harness.gate.invalidate(7, Viewport, Contract, ChromeVisualShieldInvalidation.Navigation)
                },
            )
        val identity = harness.processingIdentity()

        val outcome = harness.authority.apply(harness.delivery(identity, listOf(allow("region-1"))))

        assertEquals(ChromeVisualShieldRegionSetAuthorityResult.StaleDropped, outcome.result)
        assertEquals(0, harness.releases)
        assertEquals(ChromeVisualShieldPhase.Protected, harness.gate.snapshot().phase)
    }

    @Test
    fun `surface mismatch and release exception remain protected`() {
        val mismatched = Harness(surfaceCurrent = false)
        val mismatchIdentity = mismatched.processingIdentity()
        assertEquals(
            ChromeVisualShieldRegionSetAuthorityResult.SurfaceRejected,
            mismatched.authority.apply(mismatched.delivery(mismatchIdentity, listOf(allow("region-1")))).result,
        )
        assertEquals(0, mismatched.releases)

        val failing = Harness(throwOnRelease = true)
        val failingIdentity = failing.processingIdentity()
        assertEquals(
            ChromeVisualShieldRegionSetAuthorityResult.ErrorProtected,
            failing.authority.apply(failing.delivery(failingIdentity, listOf(allow("region-1")))).result,
        )
        assertTrue(failing.reprotections > 0)
        assertEquals(ChromeVisualShieldPhase.Protected, failing.gate.snapshot().phase)
    }

    @Test
    fun `same batch is one shot and replay history is bounded`() {
        val harness = Harness()
        val firstIdentity = harness.processingIdentity()
        val first = harness.delivery(firstIdentity, listOf(allow("region-1")))
        assertEquals(ChromeVisualShieldRegionSetAuthorityResult.Released, harness.authority.apply(first).result)
        assertEquals(ChromeVisualShieldRegionSetAuthorityResult.ReplayRejected, harness.authority.apply(first).result)
        assertEquals(1, harness.releases)
        assertEquals(1, harness.authority.snapshot().retainedReplayKeys)

        harness.gate.invalidate(7, Viewport, Contract, ChromeVisualShieldInvalidation.Navigation)
        val secondIdentity = checkNotNull(harness.gate.beginCapture())
        assertEquals(ChromeVisualShieldResult.Current, harness.gate.beginProcessing(secondIdentity))
        val second = harness.delivery(secondIdentity, listOf(allow("region-1")), discoverySequence = 2)
        assertEquals(ChromeVisualShieldRegionSetAuthorityResult.Released, harness.authority.apply(second).result)
        assertEquals(2, harness.releases)
        assertEquals(1, harness.authority.snapshot().retainedReplayKeys)
    }

    @Test
    fun `non model allow reason cannot masquerade as safe`() {
        val harness = Harness()
        val identity = harness.processingIdentity()
        val suspiciousAllow =
            GloshiaVisualDecision(
                candidateId = "region-1",
                action = GloshiaVisualAction.Allow,
                reason = GloshiaVisualPolicyContract.AnalysisExpiredReason,
            )

        val outcome = harness.authority.apply(harness.delivery(identity, listOf(suspiciousAllow)))

        assertEquals(ChromeVisualShieldRegionSetAuthorityResult.BlockProtected, outcome.result)
        assertEquals(0, harness.releases)
    }

    private class Harness(
        surfaceCurrent: Boolean = true,
        private val throwOnRelease: Boolean = false,
        beforeRelease: () -> Unit = {},
    ) {
        val gate = ChromeVisualShieldIdentityGate()
        val r1Metrics = ChromeVisualShieldR1Metrics()
        var releases = 0
        var reprotections = 0
        val authority =
            ChromeVisualShieldRegionSetAuthority(
                identityGate = gate,
                metrics = ChromeVisualShieldRegionSetMetrics(),
                r1Metrics = r1Metrics,
                isSurfaceCurrent = { surfaceCurrent },
                releaseSurface = {
                    if (throwOnRelease) error("release failed")
                    releases += 1
                },
                reprotectSurface = { reprotections += 1 },
                beforeReleaseBoundary = beforeRelease,
                monotonicNanos = { 10L + releases },
            )

        fun processingIdentity(): ChromeVisualShieldIdentity {
            gate.start(7, Viewport, Contract)
            return checkNotNull(gate.beginCapture()).also {
                assertEquals(ChromeVisualShieldResult.Current, gate.beginProcessing(it))
            }
        }

        fun delivery(
            identity: ChromeVisualShieldIdentity,
            decisions: List<GloshiaVisualDecision>,
            regionCount: Int = decisions.size,
            discoverySequence: Long = 1,
            mutateDigest: (String) -> String = { it },
            mutateDecisionBatch: BatchMutator = { it },
            forceRegionOnDecision: Boolean = false,
            invalidRegion: Boolean = false,
        ): ChromeVisualShieldRegionDiscoveryDelivery {
            val regions =
                (1..regionCount).map { index ->
                    ChromeVisualShieldDiscoveredRegion(
                        id = "region-$index",
                        bounds =
                            if (invalidRegion) {
                                ChromeVisualRegion("region-$index", 10, 10, 10, 20)
                            } else {
                                ChromeVisualRegion("region-$index", index * 10, 10, index * 10 + 8, 20)
                            },
                        visualSignature = index.toString(16).repeat(64),
                        assignedPixels = 80,
                    )
                }
            val unsigned =
                ChromeVisualShieldRegionDiscoveryResult.Complete(
                    regions = regions,
                    discoverySequence = discoverySequence,
                    regionSetDigest = "0".repeat(64),
                    coverageEvidence = ChromeVisualShieldCoverageEvidence(100, 20, 0, 80, 0, 0, "test"),
                )
            val complete =
                unsigned.copy(
                    regionSetDigest = mutateDigest(ChromeVisualShieldRegionSetDigest.compute(identity, unsigned)),
                )
            val batch = mutateDecisionBatch(ChromeVisualShieldRegionSetBatchIdentity.from(identity, complete))
            val regionDecisions =
                decisions.mapIndexed { index, decision ->
                    val region =
                        if (forceRegionOnDecision) {
                            regions.first().copy(id = decision.candidateId)
                        } else {
                            regions.getOrElse(index) { regions.first() }
                        }
                    ChromeVisualShieldRegionDecision(region, decision, batch)
                }
            return baseDelivery(identity, complete, regionDecisions)
        }

        fun unknownDelivery(
            identity: ChromeVisualShieldIdentity,
            decisions: List<GloshiaVisualDecision>,
        ): ChromeVisualShieldRegionDiscoveryDelivery {
            val unknown =
                ChromeVisualShieldRegionDiscoveryResult.Unknown(
                    ChromeVisualShieldDiscoveryUnknownReason.BackgroundAmbiguous,
                    ChromeVisualShieldResidualEvidence(100, 40, 40, 2, 0, "ambiguous"),
                )
            val region =
                ChromeVisualShieldDiscoveredRegion(
                    "region-1",
                    ChromeVisualRegion("region-1", 10, 10, 20, 20),
                    "1".repeat(64),
                    100,
                )
            return baseDelivery(
                identity,
                unknown,
                decisions.map { ChromeVisualShieldRegionDecision(region, it, null) },
            )
        }

        private fun baseDelivery(
            identity: ChromeVisualShieldIdentity,
            discovery: ChromeVisualShieldRegionDiscoveryResult,
            decisions: List<ChromeVisualShieldRegionDecision>,
        ) = ChromeVisualShieldRegionDiscoveryDelivery(
            work =
                ChromeVisualShieldWork(
                    identity,
                    "test",
                    ChromeVisualShieldWorkMode.RegionDiscoveryProbe(
                        ChromeVisualShieldRegionDiscoveryProbeRequest(
                            "centered-safe",
                            listOf("1".repeat(64)),
                            "canvas-content-islands-v3",
                            ChromeVisualShieldRegionDiscoveryGateMode.RegionSetAuthority,
                        ),
                        ChromeVisualShieldRegionDiscoveryRenderBinding(
                            identity.protectionSessionId,
                            identity.windowId,
                            identity.contentEpoch,
                            identity.viewportEpoch,
                            identity.regionSequence,
                            identity.renderIdentityToken(),
                            "3".repeat(64),
                        ),
                    ),
                ),
            searchEnvelope = identity.region,
            cropEvidence = ChromeVisualShieldCropEvidence(100, 100, "2".repeat(64)),
            discovery = discovery,
            decisions = decisions,
        )
    }

    private companion object {
        val Viewport = ChromeVisualViewport(0, 0, 100, 200)
        val Contract = ChromeVisualShieldRegionContract("fixture", 1000, 1000, 9000, 9000, "signed")

        fun allow(id: String) =
            GloshiaVisualDecision(
                candidateId = id,
                action = GloshiaVisualAction.Allow,
                reason = GloshiaVisualPolicyContract.ModelAllowReason,
                filterProbability = 0.1f,
            )

        fun block(id: String) =
            GloshiaVisualDecision(
                candidateId = id,
                action = GloshiaVisualAction.Block,
                reason = GloshiaVisualPolicyContract.ModelFilterReason,
                filterProbability = 0.9f,
            )
    }
}
