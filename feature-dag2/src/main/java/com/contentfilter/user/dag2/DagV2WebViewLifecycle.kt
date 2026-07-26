package com.contentfilter.user.dag2

import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import javax.inject.Inject
import javax.inject.Singleton

internal interface DagV2WebViewReleasePort {
    fun stopLoading()

    fun loadNeutralDocument()

    fun removeBridge()

    fun neutralizeClients()

    fun clearCallbacks()

    fun destroy()
}

internal object DagV2WebViewReleaseSequence {
    fun release(port: DagV2WebViewReleasePort) {
        port.stopLoading()
        port.loadNeutralDocument()
        port.removeBridge()
        port.neutralizeClients()
        port.clearCallbacks()
        port.destroy()
    }
}

@Singleton
class DagV2WebViewLifecycle
    @Inject
    constructor(
        private val pageAnalyzer: DagV2PageAnalyzer,
    ) {
        fun release(
            view: WebView,
            removeRuntimeScript: () -> Unit,
        ) {
            DagV2WebViewReleaseSequence.release(
                object : DagV2WebViewReleasePort {
                    override fun stopLoading() {
                        view.stopLoading()
                    }

                    override fun loadNeutralDocument() {
                        runCatching { view.loadUrl("about:blank") }
                    }

                    override fun removeBridge() {
                        removeRuntimeScript()
                        view.removeJavascriptInterface(DagV2BridgeName)
                        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                            WebViewCompat.removeWebMessageListener(view, DagV2BridgeName)
                        }
                    }

                    override fun neutralizeClients() {
                        view.webChromeClient = WebChromeClient()
                        view.webViewClient = WebViewClient()
                        view.setDownloadListener(null)
                    }

                    override fun clearCallbacks() {
                        pageAnalyzer.cancel(view)
                    }

                    override fun destroy() {
                        view.removeAllViews()
                        view.destroy()
                    }
                },
            )
        }
    }

internal const val DagV2BridgeName = "DagV2Bridge"
