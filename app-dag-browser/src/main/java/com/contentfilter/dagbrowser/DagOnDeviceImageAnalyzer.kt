package com.contentfilter.dagbrowser

import android.content.Context
import com.glosh.visual.GloshiaVisualPolicyContract
import com.glosh.visual.OnDeviceGloshiaVisualAnalyzer
import java.io.Closeable

internal fun interface DagImageAnalyzer {
    fun analyze(image: DagPreparedImage): DagImageAnalysisResult
}

internal sealed interface DagImageAnalysisResult {
    data class Classified(val filterProbability: Float) : DagImageAnalysisResult

    data class Unavailable(val reason: String) : DagImageAnalysisResult
}

internal object UnavailableDagImageAnalyzer : DagImageAnalyzer {
    override fun analyze(image: DagPreparedImage) = DagImageAnalysisResult.Unavailable(AnalyzerUnavailableReason)

    const val AnalyzerUnavailableReason = GloshiaVisualPolicyContract.AnalyzerUnavailableReason
}

internal class DagLifecycleImageAnalyzer(
    private val delegate: DagImageAnalyzer,
) : DagImageAnalyzer, Closeable {
    private val lock = Any()
    private var activeAnalyses = 0
    private var closeRequested = false
    private var delegateClosed = false

    override fun analyze(image: DagPreparedImage): DagImageAnalysisResult {
        val accepted =
            synchronized(lock) {
                if (closeRequested) false else true.also { activeAnalyses += 1 }
            }
        if (!accepted) return DagImageAnalysisResult.Unavailable(AnalyzerClosedReason)
        return try {
            delegate.analyze(image)
        } finally {
            closeDelegate(
                synchronized(lock) {
                    activeAnalyses -= 1
                    closeableIfReady()
                },
            )
        }
    }

    override fun close() {
        closeDelegate(
            synchronized(lock) {
                closeRequested = true
                closeableIfReady()
            },
        )
    }

    private fun closeableIfReady(): Closeable? {
        if (!closeRequested || activeAnalyses != 0 || delegateClosed) return null
        delegateClosed = true
        return delegate as? Closeable
    }

    private fun closeDelegate(closeable: Closeable?) {
        if (closeable != null) runCatching(closeable::close)
    }

    internal companion object {
        const val AnalyzerClosedReason = GloshiaVisualPolicyContract.AnalyzerClosedReason
    }
}

internal object DagOnDeviceImageAnalyzer {
    fun create(context: Context): DagImageAnalyzer {
        val shared = OnDeviceGloshiaVisualAnalyzer.create(context)
        return object : DagImageAnalyzer, Closeable {
            override fun analyze(image: DagPreparedImage): DagImageAnalysisResult =
                when (val result = shared.analyze(image)) {
                    is com.glosh.visual.GloshiaVisualAnalysisResult.Classified ->
                        DagImageAnalysisResult.Classified(result.filterProbability)
                    is com.glosh.visual.GloshiaVisualAnalysisResult.Unavailable ->
                        DagImageAnalysisResult.Unavailable(result.reason)
                }

            override fun close() {
                (shared as? Closeable)?.close()
            }
        }
    }

    const val ModelAssetPath = DagVisualModelInfo.ModelAssetPath
    const val ModelInputName = OnDeviceGloshiaVisualAnalyzer.ModelInputName
    const val FilterThreshold = GloshiaVisualPolicyContract.FilterThreshold
    const val FullStrongFilterThreshold = GloshiaVisualPolicyContract.FullStrongFilterThreshold
    const val UncertainRegionalReviewFloor = GloshiaVisualPolicyContract.UncertainRegionalReviewFloor
    const val UncertainRegionalFilterThreshold =
        GloshiaVisualPolicyContract.UncertainRegionalFilterThreshold
    const val RegionalFilterThreshold = GloshiaVisualPolicyContract.RegionalFilterThreshold
    const val RegionalStrongFilterThreshold = GloshiaVisualPolicyContract.RegionalStrongFilterThreshold
    const val RegionalConsensusMinimum = GloshiaVisualPolicyContract.RegionalConsensusMinimum
    const val ModelAllowReason = GloshiaVisualPolicyContract.ModelAllowReason
    const val ModelFilterReason = GloshiaVisualPolicyContract.ModelFilterReason
    const val InvalidModelInputReason = GloshiaVisualPolicyContract.InvalidModelInputReason
    const val InvalidModelOutputReason = GloshiaVisualPolicyContract.InvalidModelOutputReason
    const val ModelExecutionFailedReason = GloshiaVisualPolicyContract.ModelExecutionFailedReason
}
