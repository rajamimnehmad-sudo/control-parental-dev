package com.contentfilter.core.update.model

sealed interface UpdateCheckResult {
    data object NotConfigured : UpdateCheckResult

    data object UpToDate : UpdateCheckResult

    data object NetworkError : UpdateCheckResult

    data class Available(val manifest: UpdateManifest) : UpdateCheckResult
}
