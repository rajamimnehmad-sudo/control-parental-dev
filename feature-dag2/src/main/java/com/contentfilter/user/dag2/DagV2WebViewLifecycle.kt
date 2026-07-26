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

internal class DagV2WebViewHost<T : Any> {
    private var activeView: T? = null
    private var activeRelease: (() -> Unit)? = null

    fun attach(
        view: T,
        release: () -> Unit,
    ) {
        if (activeView === view) return
        close()
        activeView = view
        activeRelease = release
    }

    fun detach(view: T) {
        if (activeView !== view) return
        val release = activeRelease
        activeView = null
        activeRelease = null
        release?.invoke()
    }

    fun close() {
        val release = activeRelease
        activeView = null
        activeRelease = null
        release?.invoke()
    }
}

@Singleton
class DagV2LabLifecycleGate
    @Inject
    constructor() {
        private var generation = 0L
        private var activeGeneration: Long? = null

        @Synchronized
        fun acquire(): Long {
            generation += 1
            activeGeneration = generation
            return generation
        }

        @Synchronized
        fun release(candidate: Long): Boolean {
            if (activeGeneration != candidate) return false
            activeGeneration = null
            return true
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
