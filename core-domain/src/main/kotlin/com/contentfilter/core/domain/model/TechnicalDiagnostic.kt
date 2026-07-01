package com.contentfilter.core.domain.model

/**
 * Sanitized technical diagnostic event.
 */
data class TechnicalDiagnostic(
    val id: String,
    val type: String,
    val message: String,
    val occurredAtEpochMillis: Long,
)
