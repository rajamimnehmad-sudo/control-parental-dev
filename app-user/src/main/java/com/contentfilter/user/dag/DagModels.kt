package com.contentfilter.user.dag

import com.contentfilter.core.domain.model.AccessRequest

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

internal data class DagPageApproval(
    val url: String,
    val fingerprint: String,
    val policyVersion: Long,
    val modelVersion: String,
    val approvedAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
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
    Reviews,
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
    val dagExtraKosherEnabled: Boolean = false,
    val address: String = "",
    val view: DagView = DagView.Start,
    val pageStatus: DagPageStatus = DagPageStatus.Idle,
    val pageAnalysisReady: Boolean = false,
    val viewportImagesReady: Boolean = false,
    val analysisProgress: Float = 0f,
    val results: List<DagSearchResult> = emptyList(),
    val searchQuery: String = "",
    val searchPage: Int = 0,
    val canLoadMoreResults: Boolean = false,
    val suggestions: List<String> = emptyList(),
    val history: List<DagHistoryEntry> = emptyList(),
    val reviewRequests: List<AccessRequest> = emptyList(),
    val requestedUrl: String? = null,
    val navigationRevision: Long = 0L,
    val loading: Boolean = false,
    val message: String = "",
    val reviewCandidate: DagReviewCandidate? = null,
    val calibrationVersion: Long = 0,
)

data class DagTabSnapshot(
    val address: String = "",
    val view: DagView = DagView.Start,
    val pageStatus: DagPageStatus = DagPageStatus.Idle,
    val results: List<DagSearchResult> = emptyList(),
    val searchQuery: String = "",
    val searchPage: Int = 0,
    val canLoadMoreResults: Boolean = false,
    val requestedUrl: String? = null,
    val message: String = "",
    val reviewCandidate: DagReviewCandidate? = null,
)

internal data class DagSavedTab(
    val id: String,
    val snapshot: DagTabSnapshot,
    val lastUsedAtEpochMillis: Long = 0L,
)

internal fun DagTabSnapshot.isEmptyTab(): Boolean =
    view == DagView.Start &&
        address.isBlank() &&
        searchQuery.isBlank() &&
        results.isEmpty() &&
        requestedUrl == null

internal data class DagSavedTabSession(
    val activeTabId: String,
    val tabs: List<DagSavedTab>,
)
