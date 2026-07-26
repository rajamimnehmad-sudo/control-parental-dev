package com.contentfilter.user.dag2

import android.webkit.WebView
import org.json.JSONArray
import java.util.WeakHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DagV2PageAnalyzer
    @Inject
    constructor() {
        private val pending = WeakHashMap<WebView, MutableSet<AnalysisOperation>>()

        fun analyze(
            view: WebView,
            context: DagV2DocumentRequestContext,
            onSuccess: (DagV2PageEvidence) -> Unit,
            onFailure: () -> Unit,
            onDiscarded: () -> Unit,
        ) {
            val operation = AnalysisOperation()
            register(view, operation)
            val timeout =
                Runnable {
                    if (operation.finish()) {
                        unregister(view, operation)
                        onFailure()
                    }
                }
            operation.timeout = timeout
            view.postDelayed(timeout, DocumentAnalysisTimeoutMillis)
            view.evaluateJavascript(ExtractionScript) { encoded ->
                if (!operation.finish()) return@evaluateJavascript
                view.removeCallbacks(timeout)
                unregister(view, operation)
                val evidence = parseEvidence(encoded, context)
                when {
                    evidence == null -> onFailure()
                    evidence.context != context -> onDiscarded()
                    else -> onSuccess(evidence)
                }
            }
        }

        @Synchronized
        fun cancel(view: WebView) {
            pending.remove(view).orEmpty().forEach { operation ->
                operation.cancel()
                operation.timeout?.let(view::removeCallbacks)
            }
        }

        internal fun parseEvidence(
            encoded: String?,
            expectedContext: DagV2DocumentRequestContext,
        ): DagV2PageEvidence? =
            runCatching {
                val payload = JSONArray(encoded)
                DagV2PageEvidence(
                    context =
                        DagV2DocumentRequestContext(
                            sessionId = payload.getString(0),
                            navigationToken = payload.getString(1),
                            documentUrl = expectedContext.documentUrl,
                            documentOrigin = expectedContext.documentOrigin,
                            createdAt = expectedContext.createdAt,
                        ),
                    url = payload.getString(2),
                    title = payload.getString(3),
                    visibleText = payload.getString(4),
                )
            }.getOrNull()

        @Synchronized
        private fun register(
            view: WebView,
            operation: AnalysisOperation,
        ) {
            pending.getOrPut(view) { linkedSetOf() } += operation
        }

        @Synchronized
        private fun unregister(
            view: WebView,
            operation: AnalysisOperation,
        ) {
            pending[view]?.let { operations ->
                operations -= operation
                if (operations.isEmpty()) pending.remove(view)
            }
        }

        private class AnalysisOperation {
            var timeout: Runnable? = null
            private var active = true

            @Synchronized
            fun finish(): Boolean {
                if (!active) return false
                active = false
                return true
            }

            @Synchronized
            fun cancel() {
                active = false
            }
        }

        private companion object {
            const val DocumentAnalysisTimeoutMillis = 8_000L
            val ExtractionScript =
                """
                (function() {
                  var context = window.__dag2Context || {};
                  return [
                    String(context.sessionId || ''),
                    String(context.navigationToken || ''),
                    String(location.href || ''),
                    String(document.title || '').substring(0, 500),
                    String(document.body && document.body.innerText || '').substring(0, 24000)
                  ];
                })();
                """.trimIndent()
        }
    }

data class DagV2PageEvidence(
    val context: DagV2DocumentRequestContext,
    val url: String,
    val title: String,
    val visibleText: String,
)
