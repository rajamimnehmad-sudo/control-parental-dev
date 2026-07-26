package com.contentfilter.user.dag2

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class DagV2DocumentSessionState(
    val sessionId: String,
    val navigationToken: String,
    val mainDocumentUrl: String,
    val origin: String,
    val fullAnalysisCompleted: Boolean,
    val fullPageAnalysisCount: Int,
    val startedAt: Long,
    val fullAnalysisStarted: Boolean = false,
    val cancelled: Boolean = false,
    val requestContext: DagV2DocumentRequestContext,
)

enum class DagV2InternalInteraction {
    Menu,
    Button,
    Accordion,
    Filter,
    Modal,
    Carousel,
    Hash,
    PushState,
    ReplaceState,
    Scroll,
    LazyLoad,
}

@Singleton
class DagV2DocumentSession
    @Inject
    constructor() {
        @Volatile
        private var current: DagV2DocumentSessionState? = null

        @Synchronized
        fun start(mainDocumentUrl: String): DagV2DocumentSessionState {
            val sessionId = UUID.randomUUID().toString()
            val navigationToken = UUID.randomUUID().toString()
            val startedAt = System.nanoTime() / 1_000_000L
            val origin = mainDocumentUrl.dagV2Origin()
            val created =
                DagV2DocumentSessionState(
                    sessionId = sessionId,
                    navigationToken = navigationToken,
                    mainDocumentUrl = mainDocumentUrl,
                    origin = origin,
                    fullAnalysisCompleted = false,
                    fullPageAnalysisCount = 0,
                    startedAt = startedAt,
                    requestContext =
                        DagV2DocumentRequestContext(
                            sessionId = sessionId,
                            navigationToken = navigationToken,
                            documentUrl = mainDocumentUrl,
                            documentOrigin = origin,
                            createdAt = startedAt,
                        ),
                )
            current = created
            return created
        }

        fun snapshot(): DagV2DocumentSessionState? = current

        fun isCurrent(
            sessionId: String,
            navigationToken: String,
        ): Boolean =
            current?.let {
                !it.cancelled && it.sessionId == sessionId && it.navigationToken == navigationToken
            } == true

        @Synchronized
        fun beginFullAnalysis(
            sessionId: String,
            navigationToken: String,
        ): DagV2DocumentSessionState? {
            val active = current ?: return null
            if (!isCurrent(sessionId, navigationToken)) return null
            if (active.fullAnalysisStarted) return null
            return active
                .copy(
                    fullAnalysisStarted = true,
                    fullPageAnalysisCount = 1,
                ).also { current = it }
        }

        @Synchronized
        fun completeFullAnalysis(
            sessionId: String,
            navigationToken: String,
        ): DagV2DocumentSessionState? {
            val active = current ?: return null
            if (!isCurrent(sessionId, navigationToken) || !active.fullAnalysisStarted) return null
            return active.copy(fullAnalysisCompleted = true).also { current = it }
        }

        fun recordInternalInteraction(interaction: DagV2InternalInteraction): DagV2DocumentSessionState? {
            @Suppress("UNUSED_VARIABLE")
            val recordedForDiagnostics = interaction
            return current
        }

        @Synchronized
        fun cancelActive(): DagV2DocumentSessionState? {
            val cancelled = current?.copy(cancelled = true)
            current = null
            return cancelled
        }
    }
