package com.contentfilter.dagbrowser

import com.glosh.visual.GloshiaPreparedRasterPolicy
import com.glosh.visual.GloshiaVisualAction
import com.glosh.visual.GloshiaVisualAnalysisResult
import com.glosh.visual.GloshiaVisualAnalyzer
import com.glosh.visual.GloshiaVisualDecisionBasis

/** DAG adapter; all thresholds and decision policy live in the shared R3.1 engine. */
internal object DagPreparedRasterPolicy {
    fun decide(
        candidateId: String,
        preparedImages: List<DagPreparedImage>,
        analyzer: DagImageAnalyzer,
        trace: DagMediaPipelineTrace? = null,
        workGuard: DagMediaWorkGuard = AlwaysCurrentDagMediaWork,
    ): DagMediaDecision {
        val sharedAnalyzer =
            GloshiaVisualAnalyzer { image ->
                when (
                    val result =
                        trace?.measureInference { analyzer.analyze(image) }
                            ?: analyzer.analyze(image)
                ) {
                    is DagImageAnalysisResult.Classified ->
                        GloshiaVisualAnalysisResult.Classified(result.filterProbability)
                    is DagImageAnalysisResult.Unavailable ->
                        GloshiaVisualAnalysisResult.Unavailable(result.reason)
                }
            }
        val shared =
            GloshiaPreparedRasterPolicy.decide(
                candidateId = candidateId,
                preparedImages = preparedImages,
                analyzer = sharedAnalyzer,
                canContinue = workGuard::canContinue,
            )
        trace?.preparedImageCount = shared.preparedImageCount
        trace?.regionalImageCount = shared.regionalImageCount
        trace?.fullImageProbability = shared.fullImageProbability
        trace?.decisionBasis = shared.basis.toDagBasis()
        return DagMediaDecision(
            candidateId = shared.candidateId,
            action =
                when (shared.action) {
                    GloshiaVisualAction.Allow -> DagMediaAction.Allow
                    GloshiaVisualAction.Block -> DagMediaAction.Block
                },
            reason = shared.reason,
            filterProbability = shared.filterProbability,
        )
    }

    private fun GloshiaVisualDecisionBasis.toDagBasis(): DagMediaDecisionBasis =
        when (this) {
            GloshiaVisualDecisionBasis.None -> DagMediaDecisionBasis.None
            GloshiaVisualDecisionBasis.FullThreshold -> DagMediaDecisionBasis.FullThreshold
            GloshiaVisualDecisionBasis.FullStrong -> DagMediaDecisionBasis.FullStrong
            GloshiaVisualDecisionBasis.UncertainRegional -> DagMediaDecisionBasis.UncertainRegional
            GloshiaVisualDecisionBasis.RegionalStrong -> DagMediaDecisionBasis.RegionalStrong
            GloshiaVisualDecisionBasis.RegionalConsensus -> DagMediaDecisionBasis.RegionalConsensus
        }
}
