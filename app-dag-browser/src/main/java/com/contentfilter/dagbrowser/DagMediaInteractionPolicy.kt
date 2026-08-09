package com.contentfilter.dagbrowser

internal object DagMediaInteractionPolicy {
    const val ActiveAnalysisThreads = 1
    const val RestoreDelayMillis = 250L

    fun analysisThreads(
        interacting: Boolean,
        idleThreads: Int,
    ): Int = if (interacting) ActiveAnalysisThreads else idleThreads
}
