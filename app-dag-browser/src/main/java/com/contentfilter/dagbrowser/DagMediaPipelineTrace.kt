package com.contentfilter.dagbrowser

internal enum class DagMediaPipelineStage {
    Base64Decode,
    SafeVectorCheck,
    BoundsRead,
    SafeSpriteCheck,
    Preprocess,
    Inference,
}

internal class DagMediaPipelineTrace(
    private val nanoTime: () -> Long = System::nanoTime,
) {
    private val elapsedNanos = mutableMapOf<DagMediaPipelineStage, Long>()

    var inferenceCount: Int = 0
        private set
    var preparedImageCount: Int = 0
    var regionalImageCount: Int = 0

    fun <T> measure(
        stage: DagMediaPipelineStage,
        operation: () -> T,
    ): T {
        val startedAt = nanoTime()
        return try {
            operation()
        } finally {
            val elapsed = (nanoTime() - startedAt).coerceAtLeast(0L)
            elapsedNanos[stage] = elapsedNanos.getOrDefault(stage, 0L) + elapsed
        }
    }

    fun <T> measureInference(operation: () -> T): T {
        inferenceCount += 1
        return measure(DagMediaPipelineStage.Inference, operation)
    }

    fun elapsedMillis(stage: DagMediaPipelineStage): Double = elapsedNanos.getOrDefault(stage, 0L) / NanosPerMillisecond

    private companion object {
        const val NanosPerMillisecond = 1_000_000.0
    }
}
