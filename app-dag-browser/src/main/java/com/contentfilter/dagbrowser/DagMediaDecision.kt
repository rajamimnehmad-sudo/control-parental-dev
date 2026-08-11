package com.contentfilter.dagbrowser

internal data class DagMediaDecision(
    val candidateId: String,
    val action: DagMediaAction,
    val reason: String,
    val filterProbability: Float? = null,
    val imageWidth: Int? = null,
    val imageHeight: Int? = null,
    val replacementBytesBase64: String? = null,
)

internal enum class DagMediaAction(
    val wireValue: String,
) {
    Allow("allow"),
    Block("block"),
}
