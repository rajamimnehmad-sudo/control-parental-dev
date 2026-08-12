package com.contentfilter.dagbrowser

/**
 * Single visual authority for an already prepared raster.
 *
 * Network images and covered video frames enter here only after producing the exact same bounded
 * 224x224 RGB contract. The caller owns and clears [preparedImages]; temporary regional views are
 * cleared here before returning. Every stale, uncertain-invalid or unavailable result fails closed.
 */
internal object DagPreparedRasterPolicy {
    fun decide(
        candidateId: String,
        preparedImages: List<DagPreparedImage>,
        analyzer: DagImageAnalyzer,
        trace: DagMediaPipelineTrace? = null,
        workGuard: DagMediaWorkGuard = AlwaysCurrentDagMediaWork,
    ): DagMediaDecision {
        if (preparedImages.isEmpty() || preparedImages.any { !DagImageDecodeContract.isValid(it) }) {
            return blocked(candidateId, AndroidDagImagePreprocessor.DecodeFailedReason)
        }
        if (!workGuard.canContinue()) {
            return blocked(candidateId, DagMediaBytesPolicy.AnalysisExpiredReason)
        }
        val fullProbability =
            when (
                val analysis =
                    trace.measureInference { analyzer.analyze(preparedImages.first()) }
            ) {
                is DagImageAnalysisResult.Classified ->
                    analysis.filterProbability.takeIf(::isValidProbability)
                        ?: return blocked(
                            candidateId,
                            DagOnDeviceImageAnalyzer.InvalidModelOutputReason,
                        )
                is DagImageAnalysisResult.Unavailable ->
                    return blocked(candidateId, analysis.reason)
            }
        if (!workGuard.canContinue()) {
            return blocked(candidateId, DagMediaBytesPolicy.AnalysisExpiredReason)
        }
        trace?.fullImageProbability = fullProbability
        if (fullProbability >= DagOnDeviceImageAnalyzer.FilterThreshold) {
            trace?.decisionBasis =
                if (fullProbability >= DagOnDeviceImageAnalyzer.FullStrongFilterThreshold) {
                    DagMediaDecisionBasis.FullStrong
                } else {
                    DagMediaDecisionBasis.FullThreshold
                }
            return blocked(
                candidateId,
                DagOnDeviceImageAnalyzer.ModelFilterReason,
                fullProbability,
            )
        }

        val preparedRegionalImages = preparedImages.drop(1)
        val generatedUncertainRegions =
            if (
                preparedRegionalImages.isEmpty() &&
                fullProbability >= DagOnDeviceImageAnalyzer.UncertainRegionalReviewFloor
            ) {
                DagUncertainRegionalCropper.quadrantViews(preparedImages.first())
            } else {
                emptyList()
            }
        val regionalImages = preparedRegionalImages.ifEmpty { generatedUncertainRegions }
        if (generatedUncertainRegions.isNotEmpty()) {
            trace?.regionalImageCount = generatedUncertainRegions.size
            trace?.preparedImageCount = 1 + generatedUncertainRegions.size
        }
        return try {
            var maximumProbability = fullProbability
            var regionalFilterVotes = 0
            for (regionalImage in regionalImages) {
                if (!workGuard.canContinue()) {
                    return blocked(candidateId, DagMediaBytesPolicy.AnalysisExpiredReason)
                }
                val regionalProbability =
                    when (val analysis = trace.measureInference { analyzer.analyze(regionalImage) }) {
                        is DagImageAnalysisResult.Classified ->
                            analysis.filterProbability.takeIf(::isValidProbability)
                                ?: return blocked(
                                    candidateId,
                                    DagOnDeviceImageAnalyzer.InvalidModelOutputReason,
                                )
                        is DagImageAnalysisResult.Unavailable ->
                            return blocked(candidateId, analysis.reason)
                    }
                maximumProbability = maxOf(maximumProbability, regionalProbability)
                if (regionalProbability >= DagOnDeviceImageAnalyzer.RegionalFilterThreshold) {
                    regionalFilterVotes += 1
                }
                val uncertainRegionIsUnsafe =
                    generatedUncertainRegions.isNotEmpty() &&
                        regionalProbability >=
                        DagOnDeviceImageAnalyzer.UncertainRegionalFilterThreshold
                if (
                    uncertainRegionIsUnsafe ||
                    regionalProbability >= DagOnDeviceImageAnalyzer.RegionalStrongFilterThreshold ||
                    regionalFilterVotes >= DagOnDeviceImageAnalyzer.RegionalConsensusMinimum
                ) {
                    trace?.decisionBasis =
                        when {
                            uncertainRegionIsUnsafe -> DagMediaDecisionBasis.UncertainRegional
                            regionalProbability >=
                                DagOnDeviceImageAnalyzer.RegionalStrongFilterThreshold ->
                                DagMediaDecisionBasis.RegionalStrong
                            else -> DagMediaDecisionBasis.RegionalConsensus
                        }
                    return blocked(
                        candidateId,
                        DagOnDeviceImageAnalyzer.ModelFilterReason,
                        maximumProbability,
                    )
                }
            }
            if (!workGuard.canContinue()) {
                return blocked(candidateId, DagMediaBytesPolicy.AnalysisExpiredReason)
            }
            DagMediaDecision(
                candidateId = candidateId,
                action = DagMediaAction.Allow,
                reason = DagOnDeviceImageAnalyzer.ModelAllowReason,
                filterProbability = maximumProbability,
            )
        } finally {
            generatedUncertainRegions.forEach { it.rgb888.fill(0) }
        }
    }

    private fun blocked(
        candidateId: String,
        reason: String,
        filterProbability: Float? = null,
    ) = DagMediaDecision(
        candidateId = candidateId.take(MaxCandidateIdLength),
        action = DagMediaAction.Block,
        reason = reason,
        filterProbability = filterProbability,
    )

    private fun isValidProbability(probability: Float): Boolean = probability.isFinite() && probability in 0f..1f

    private const val MaxCandidateIdLength = 80
}

private fun <T> DagMediaPipelineTrace?.measureInference(operation: () -> T): T =
    this?.measureInference(operation) ?: operation()
