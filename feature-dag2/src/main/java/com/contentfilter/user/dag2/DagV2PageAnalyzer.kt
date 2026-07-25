package com.contentfilter.user.dag2

import android.webkit.WebView
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DagV2PageAnalyzer
    @Inject
    constructor() {
        fun analyze(
            view: WebView,
            session: DagV2DocumentSessionState,
            onSuccess: (DagV2PageEvidence) -> Unit,
            onFailure: () -> Unit,
        ) {
            var completed = false
            val timeout =
                Runnable {
                    if (!completed) {
                        completed = true
                        onFailure()
                    }
                }
            view.postDelayed(timeout, DocumentAnalysisTimeoutMillis)
            view.evaluateJavascript(ExtractionScript) { encoded ->
                if (completed) return@evaluateJavascript
                completed = true
                view.removeCallbacks(timeout)
                val evidence = parseEvidence(encoded, session.mainDocumentUrl)
                if (evidence == null) onFailure() else onSuccess(evidence)
            }
        }

        internal fun parseEvidence(
            encoded: String?,
            mainDocumentUrl: String,
        ): DagV2PageEvidence? =
            runCatching {
                val payload = JSONArray(encoded)
                DagV2PageEvidence(
                    url = mainDocumentUrl,
                    title = payload.getString(0),
                    visibleText = payload.getString(1),
                )
            }.getOrNull()

        private companion object {
            const val DocumentAnalysisTimeoutMillis = 8_000L
            val ExtractionScript =
                """
                (function() {
                  return [
                    String(document.title || '').substring(0, 500),
                    String(document.body && document.body.innerText || '').substring(0, 24000)
                  ];
                })();
                """.trimIndent()
        }
    }

data class DagV2PageEvidence(
    val url: String,
    val title: String,
    val visibleText: String,
)
