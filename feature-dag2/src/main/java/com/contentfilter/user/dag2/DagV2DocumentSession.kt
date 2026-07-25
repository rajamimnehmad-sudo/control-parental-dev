package com.contentfilter.user.dag2

import java.net.URI
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
            val created =
                DagV2DocumentSessionState(
                    sessionId = UUID.randomUUID().toString(),
                    navigationToken = UUID.randomUUID().toString(),
                    mainDocumentUrl = mainDocumentUrl,
                    origin = mainDocumentUrl.dagV2Origin(),
                    fullAnalysisCompleted = false,
                    fullPageAnalysisCount = 0,
                    startedAt = System.nanoTime() / 1_000_000L,
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

private fun String.dagV2Origin(): String =
    runCatching {
        val uri = URI(this)
        "${uri.scheme.lowercase()}://${uri.host.lowercase()}${uri.port.takeIf { it >= 0 }?.let { ":$it" }.orEmpty()}"
    }.getOrDefault("")
