package com.contentfilter.feature.accessibility.chromevisual

/** Shell-accessible only through the DUMP-protected DEV receiver. */
object ChromeMediaShieldActiveDocumentLabControl {
    @Volatile
    private var endpoint: Endpoint? = null

    fun arm(
        caseId: String?,
        stage: String?,
        nonce: String?,
    ): String = endpoint?.arm(caseId, stage, nonce) ?: Unavailable

    fun release(
        caseId: String?,
        stage: String?,
        nonce: String?,
    ): String = endpoint?.release(caseId, stage, nonce) ?: Unavailable

    fun cancel(
        caseId: String?,
        stage: String?,
        nonce: String?,
    ): String = endpoint?.cancel(caseId, stage, nonce) ?: Unavailable

    fun status(): String = endpoint?.status() ?: Unavailable

    fun replayConsumedPresent(): String = endpoint?.replayConsumedPresent() ?: Unavailable

    internal fun bind(value: Endpoint) {
        endpoint = value
    }

    internal fun unbind(value: Endpoint) {
        if (endpoint === value) endpoint = null
    }

    internal interface Endpoint {
        fun arm(
            caseId: String?,
            stage: String?,
            nonce: String?,
        ): String

        fun release(
            caseId: String?,
            stage: String?,
            nonce: String?,
        ): String

        fun cancel(
            caseId: String?,
            stage: String?,
            nonce: String?,
        ): String

        fun status(): String

        fun replayConsumedPresent(): String
    }

    private const val Unavailable = "result=active_document_unavailable"
}
