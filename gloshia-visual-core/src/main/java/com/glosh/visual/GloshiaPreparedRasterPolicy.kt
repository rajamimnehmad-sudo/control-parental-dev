package com.glosh.visual

object GloshiaPreparedRasterPolicy {
    fun decide(
        candidateId: String,
        preparedImages: List<GloshiaPreparedImage>,
        analyzer: GloshiaVisualAnalyzer,
        canContinue: () -> Boolean = { true },
        analyze: (GloshiaVisualAnalyzer, GloshiaPreparedImage) -> GloshiaVisualAnalysisResult =
            { currentAnalyzer, image -> currentAnalyzer.analyze(image) },
    ): GloshiaVisualDecision {
        if (preparedImages.isEmpty() || preparedImages.any { !GloshiaImageContract.isValid(it) }) {
            return blocked(candidateId, GloshiaVisualPolicyContract.DecodeFailedReason)
        }
        if (!canContinue()) {
            return blocked(candidateId, GloshiaVisualPolicyContract.AnalysisExpiredReason)
        }
        val fullProbability =
            probabilityOrDecision(candidateId, analyze(analyzer, preparedImages.first()))
        if (fullProbability is ProbabilityResult.Decision) return fullProbability.value
        val full = (fullProbability as ProbabilityResult.Value).probability
        if (!canContinue()) {
            return blocked(candidateId, GloshiaVisualPolicyContract.AnalysisExpiredReason)
        }
        if (full >= GloshiaVisualPolicyContract.FilterThreshold) {
            val basis =
                if (full >= GloshiaVisualPolicyContract.FullStrongFilterThreshold) {
                    GloshiaVisualDecisionBasis.FullStrong
                } else {
                    GloshiaVisualDecisionBasis.FullThreshold
                }
            return blocked(
                candidateId = candidateId,
                reason = GloshiaVisualPolicyContract.ModelFilterReason,
                filterProbability = full,
                basis = basis,
                preparedImageCount = preparedImages.size,
                regionalImageCount = preparedImages.size - 1,
                fullImageProbability = full,
            )
        }

        val preparedRegionalImages = preparedImages.drop(1)
        val generatedRegions =
            if (
                preparedRegionalImages.isEmpty() &&
                full >= GloshiaVisualPolicyContract.UncertainRegionalReviewFloor
            ) {
                GloshiaUncertainRegionalCropper.quadrantViews(preparedImages.first())
            } else {
                emptyList()
            }
        val regionalImages = preparedRegionalImages.ifEmpty { generatedRegions }
        val totalPrepared = preparedImages.size + generatedRegions.size
        val regionalCount = regionalImages.size
        return try {
            var maximum = full
            var votes = 0
            for (regionalImage in regionalImages) {
                if (!canContinue()) {
                    return blocked(candidateId, GloshiaVisualPolicyContract.AnalysisExpiredReason)
                }
                val regionalResult = probabilityOrDecision(candidateId, analyze(analyzer, regionalImage))
                if (regionalResult is ProbabilityResult.Decision) return regionalResult.value
                val regional = (regionalResult as ProbabilityResult.Value).probability
                maximum = maxOf(maximum, regional)
                if (regional >= GloshiaVisualPolicyContract.RegionalFilterThreshold) votes += 1
                val uncertainUnsafe =
                    generatedRegions.isNotEmpty() &&
                        regional >= GloshiaVisualPolicyContract.UncertainRegionalFilterThreshold
                val basis =
                    when {
                        uncertainUnsafe -> GloshiaVisualDecisionBasis.UncertainRegional
                        regional >= GloshiaVisualPolicyContract.RegionalStrongFilterThreshold ->
                            GloshiaVisualDecisionBasis.RegionalStrong
                        votes >= GloshiaVisualPolicyContract.RegionalConsensusMinimum ->
                            GloshiaVisualDecisionBasis.RegionalConsensus
                        else -> null
                    }
                if (basis != null) {
                    return blocked(
                        candidateId,
                        GloshiaVisualPolicyContract.ModelFilterReason,
                        maximum,
                        basis,
                        totalPrepared,
                        regionalCount,
                        full,
                    )
                }
            }
            if (!canContinue()) {
                blocked(candidateId, GloshiaVisualPolicyContract.AnalysisExpiredReason)
            } else {
                GloshiaVisualDecision(
                    candidateId = candidateId,
                    action = GloshiaVisualAction.Allow,
                    reason = GloshiaVisualPolicyContract.ModelAllowReason,
                    filterProbability = maximum,
                    preparedImageCount = totalPrepared,
                    regionalImageCount = regionalCount,
                    fullImageProbability = full,
                )
            }
        } finally {
            generatedRegions.forEach { it.rgb888.fill(0) }
        }
    }

    private fun probabilityOrDecision(
        candidateId: String,
        result: GloshiaVisualAnalysisResult,
    ): ProbabilityResult =
        when (result) {
            is GloshiaVisualAnalysisResult.Classified ->
                if (result.filterProbability.isFinite() && result.filterProbability in 0f..1f) {
                    ProbabilityResult.Value(result.filterProbability)
                } else {
                    ProbabilityResult.Decision(
                        blocked(candidateId, GloshiaVisualPolicyContract.InvalidModelOutputReason),
                    )
                }
            is GloshiaVisualAnalysisResult.Unavailable ->
                ProbabilityResult.Decision(blocked(candidateId, result.reason))
        }

    private fun blocked(
        candidateId: String,
        reason: String,
        filterProbability: Float? = null,
        basis: GloshiaVisualDecisionBasis = GloshiaVisualDecisionBasis.None,
        preparedImageCount: Int = 0,
        regionalImageCount: Int = 0,
        fullImageProbability: Float? = null,
    ) = GloshiaVisualDecision(
        candidateId = candidateId.take(MaxCandidateIdLength),
        action = GloshiaVisualAction.Block,
        reason = reason,
        filterProbability = filterProbability,
        basis = basis,
        preparedImageCount = preparedImageCount,
        regionalImageCount = regionalImageCount,
        fullImageProbability = fullImageProbability,
    )

    private sealed interface ProbabilityResult {
        data class Value(val probability: Float) : ProbabilityResult

        data class Decision(val value: GloshiaVisualDecision) : ProbabilityResult
    }

    private const val MaxCandidateIdLength = 80
}
