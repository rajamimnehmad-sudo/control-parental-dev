package com.contentfilter.core.update.model

sealed interface UpdateCheckResult {
    data object NotConfigured : UpdateCheckResult

    data class UpToDate(val manifest: UpdateManifest) : UpdateCheckResult

    data object NetworkError : UpdateCheckResult

    data class Available(val manifest: UpdateManifest) : UpdateCheckResult
}
