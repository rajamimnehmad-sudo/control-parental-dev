package com.contentfilter.user.dag

enum class DagClassification {
    Allowed,
    Blocked,
    Uncertain,
}

data class DagClassificationResult(
    val decision: DagClassification,
    val category: String,
    val confidence: Float,
    val modelVersion: String,
)

data class DagSearchResult(
    val title: String,
    val url: String,
    val domain: String,
    val description: String,
    val classification: DagClassificationResult,
)

enum class DagHistoryType {
    Search,
    Page,
}

data class DagHistoryEntry(
    val id: String,
    val type: DagHistoryType,
    val value: String,
    val url: String?,
    val title: String?,
    val visitedAtEpochMillis: Long,
)

data class DagReviewCandidate(
    val url: String,
    val domain: String,
    val title: String,
    val category: String,
    val modelVersion: String,
)

enum class DagView {
    Start,
    Results,
    Browser,
    History,
}

enum class DagPageStatus {
    Idle,
    Loading,
    Visible,
    Blocked,
    Uncertain,
}

data class DagBrowserUiState(
    val dagAvailabilityKnown: Boolean = false,
    val dagEnabled: Boolean = false,
    val address: String = "",
    val view: DagView = DagView.Start,
    val pageStatus: DagPageStatus = DagPageStatus.Idle,
    val results: List<DagSearchResult> = emptyList(),
    val history: List<DagHistoryEntry> = emptyList(),
    val requestedUrl: String? = null,
    val navigationRevision: Long = 0L,
    val loading: Boolean = false,
    val message: String = "",
    val reviewCandidate: DagReviewCandidate? = null,
)

data class DagTabSnapshot(
    val address: String = "",
    val view: DagView = DagView.Start,
    val pageStatus: DagPageStatus = DagPageStatus.Idle,
    val results: List<DagSearchResult> = emptyList(),
    val requestedUrl: String? = null,
    val message: String = "",
    val reviewCandidate: DagReviewCandidate? = null,
)
