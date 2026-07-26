package com.contentfilter.user.dag2

import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import javax.inject.Inject
import javax.inject.Singleton

enum class DagV2ImageDecision {
    Hide,
}

interface DagV2ImageDecisionProvider {
    fun decide(): DagV2ImageDecision
}

@Singleton
class DagV2FailClosedImageDecisionProvider
    @Inject
    constructor() : DagV2ImageDecisionProvider {
        override fun decide(): DagV2ImageDecision = DagV2ImageDecision.Hide
    }

@Singleton
class DagV2NeutralImageFactory
    @Inject
    constructor() {
        fun create(): WebResourceResponse =
            WebResourceResponse(
                "image/svg+xml",
                "utf-8",
                200,
                "OK",
                SafeImageHeaders,
                ByteArrayInputStream(NeutralSvg),
            )

        fun bytesForTest(): ByteArray = NeutralSvg.copyOf()

        private companion object {
            val SafeImageHeaders =
                mapOf(
                    "Cache-Control" to "no-store, max-age=0",
                    "Content-Security-Policy" to "default-src 'none'; style-src 'none'; sandbox",
                    "X-Content-Type-Options" to "nosniff",
                )
            val NeutralSvg =
                """
                <svg xmlns="http://www.w3.org/2000/svg" width="640" height="480" viewBox="0 0 640 480">
                  <rect width="640" height="480" fill="#E9EDF2"/>
                </svg>
                """.trimIndent().encodeToByteArray()
        }
    }

/**
 * Lote 2 has no visual model and therefore has no approved path. Every image
 * response is generated locally and is independent from the source bytes.
 */
@Singleton
class DagV2ImagePipeline
    @Inject
    constructor(
        private val decisionProvider: DagV2FailClosedImageDecisionProvider,
        private val neutralImageFactory: DagV2NeutralImageFactory,
        private val sessions: DagV2DocumentSession,
        private val metrics: DagV2Metrics,
        private val calibration: DagV2CalibrationController? = null,
    ) {
        fun intercept(
            request: DagV2ResourceRequest,
            kind: DagV2ResourceKind,
        ): WebResourceResponse {
            val context = request.documentContext
            val session =
                sessions.snapshot()?.takeIf {
                    request.attribution == DagV2RequestAttribution.Current &&
                        context?.sessionId == it.sessionId &&
                        context.navigationToken == it.navigationToken &&
                        sessions.isCurrent(it.sessionId, it.navigationToken)
                }
            if (session == null) {
                return neutralImageFactory.create()
            }
            metrics.imageRequest(request.source, session)

            check(kind == DagV2ResourceKind.RasterImage || kind == DagV2ResourceKind.SvgImage)
            check(decisionProvider.decide() == DagV2ImageDecision.Hide)
            calibration?.observeCandidate(request, kind, session)

            if (!sessions.isCurrent(session.sessionId, session.navigationToken)) {
                return neutralImageFactory.create()
            }
            return neutralPlaceholder(session)
        }

        fun cancelBefore(activeNavigationToken: String) {
            val session = sessions.snapshot()
            if (session != null && session.navigationToken != activeNavigationToken) {
                metrics.imageCancelled()
            }
        }

        fun cancelAll() = Unit

        fun shutdown() = Unit

        private fun neutralPlaceholder(session: DagV2DocumentSessionState?): WebResourceResponse {
            metrics.visualPlaceholderReady(session)
            return neutralImageFactory.create()
        }
    }
