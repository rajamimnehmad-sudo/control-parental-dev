package com.contentfilter.user.dag

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SafeBrowsingResponse
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.contentfilter.user.BuildConfig

/**
 * DAG's browser surface.
 *
 * Images and other functional resources are intentionally loaded by WebView itself. Keeping the
 * browser out of the resource path avoids duplicate downloads, bitmap allocations and model work.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun DagWebContent(
    state: DagBrowserUiState,
    onBackFromBrowser: () -> Unit,
    onNavigate: (String, String) -> Unit,
    onPageStarted: (String) -> Boolean,
    onPageReady: (String, String) -> Unit,
    onBlockedAction: (String) -> Unit,
    onGeolocationPrompt: (String, (Boolean) -> Unit) -> Unit,
    onFaviconChanged: (String, Bitmap) -> Unit,
    onPageBlocked: (String) -> Unit,
    onRendererGone: () -> Unit,
    onWebViewChanged: (WebView?) -> Unit,
    onNavigationStateChanged: (Boolean, Boolean) -> Unit,
) {
    val context = LocalContext.current
    val currentGeolocationPrompt by rememberUpdatedState(onGeolocationPrompt)
    val currentFaviconChanged by rememberUpdatedState(onFaviconChanged)
    var webView by remember { mutableStateOf<WebView?>(null) }
    var loadedNavigationRevision by remember { mutableStateOf(-1L) }
    var pendingFavicon by remember { mutableStateOf<Pair<String, Bitmap>?>(null) }

    BackHandler {
        if (webView?.canGoBack() == true) webView?.goBack() else onBackFromBrowser()
    }

    LaunchedEffect(state.dagEnabled) {
        if (!state.dagEnabled) {
            webView?.stopLoading()
            webView?.loadUrl("about:blank")
        }
    }

    LaunchedEffect(webView, state.navigationRevision, state.requestedUrl) {
        state.requestedUrl?.let { url ->
            val view = webView
            if (
                url.startsWith("https://", ignoreCase = true) &&
                view != null &&
                loadedNavigationRevision != state.navigationRevision
            ) {
                loadedNavigationRevision = state.navigationRevision
                val performanceProbe =
                    BuildConfig.DEBUG &&
                        runCatching { Uri.parse(url).getQueryParameter("codexperf") != null }.getOrDefault(false)
                view.settings.cacheMode =
                    if (performanceProbe) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
                if (performanceProbe) view.clearCache(true)
                if (view.url == url) view.reload() else view.loadUrl(url)
            }
        }
    }

    LaunchedEffect(state.pageStatus, state.requestedUrl, pendingFavicon) {
        val pending = pendingFavicon ?: return@LaunchedEffect
        val requestedDomain = DagContentClassifier.domainFrom(state.requestedUrl.orEmpty())
        val faviconDomain = DagContentClassifier.domainFrom(pending.first)
        if (
            state.pageStatus == DagPageStatus.Visible &&
            requestedDomain.isNotBlank() &&
            requestedDomain == faviconDomain
        ) {
            pendingFavicon = null
            currentFaviconChanged(pending.first, pending.second)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            pendingFavicon?.second?.recycle()
            pendingFavicon = null
            webView?.apply {
                stopLoading()
                loadUrl("about:blank")
                webChromeClient = null
                webViewClient = WebViewClient()
                destroy()
            }
            webView = null
            onWebViewChanged(null)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize().alpha(if (state.pageStatus == DagPageStatus.Visible) 1f else 0f),
            factory = {
                WebView(context).apply {
                    webView = this
                    onWebViewChanged(this)
                    val dagWebView = this
                    configureDagSettings()
                    setBackgroundColor(android.graphics.Color.WHITE)
                    CookieManager.getInstance().apply {
                        setAcceptCookie(true)
                        setAcceptThirdPartyCookies(dagWebView, false)
                    }
                    webChromeClient =
                        DagChromeClient(
                            onBlocked = onBlockedAction,
                            onGeolocationPrompt = { origin, decision ->
                                currentGeolocationPrompt(origin, decision)
                            },
                            onFaviconChanged = { url, icon ->
                                pendingFavicon?.second?.recycle()
                                pendingFavicon = url to icon.copy(Bitmap.Config.ARGB_8888, false)
                            },
                        )
                    webViewClient =
                        DagWebViewClient(
                            onNavigate = onNavigate,
                            onStarted = onPageStarted,
                            onReady = { view, url ->
                                onPageReady(url, view.title.orEmpty())
                                onNavigationStateChanged(view.canGoBack(), view.canGoForward())
                            },
                            onBlocked = onPageBlocked,
                            onRendererGone = { failedView ->
                                if (webView === failedView) {
                                    webView = null
                                    onWebViewChanged(null)
                                    onRendererGone()
                                }
                            },
                        )
                    setDownloadListener { _, _, _, _, _ ->
                        onBlockedAction("Las descargas están bloqueadas en DAG.")
                    }
                }
            },
        )

        if (state.pageStatus != DagPageStatus.Visible) {
            Box(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (state.pageStatus == DagPageStatus.Blocked || state.pageStatus == DagPageStatus.Uncertain) {
                    Text(
                        when (state.pageStatus) {
                            DagPageStatus.Blocked -> "Página bloqueada"
                            DagPageStatus.Uncertain -> "Página pendiente de revisión"
                            else -> ""
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun WebView.configureDagSettings() {
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.databaseEnabled = false
    settings.allowFileAccess = false
    settings.allowContentAccess = false
    settings.loadsImagesAutomatically = true
    settings.blockNetworkImage = false
    settings.mediaPlaybackRequiresUserGesture = true
    settings.javaScriptCanOpenWindowsAutomatically = false
    settings.setSupportMultipleWindows(false)
    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
    settings.cacheMode = WebSettings.LOAD_DEFAULT
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) settings.safeBrowsingEnabled = true
    settings.setGeolocationEnabled(true)
}

private class DagChromeClient(
    private val onBlocked: (String) -> Unit,
    private val onGeolocationPrompt: (String, (Boolean) -> Unit) -> Unit,
    private val onFaviconChanged: (String, Bitmap) -> Unit,
) : WebChromeClient() {
    override fun onReceivedIcon(
        view: WebView?,
        icon: Bitmap?,
    ) {
        val url = view?.url?.takeIf { it.startsWith("https://", ignoreCase = true) } ?: return
        icon?.takeIf { it.width > 0 && it.height > 0 }?.let { onFaviconChanged(url, it) }
    }

    override fun onPermissionRequest(request: PermissionRequest) {
        request.deny()
        onBlocked("Cámara y micrófono están bloqueados en DAG.")
    }

    override fun onGeolocationPermissionsShowPrompt(
        origin: String?,
        callback: GeolocationPermissions.Callback?,
    ) {
        val safeOrigin = origin?.takeIf { it.startsWith("https://", ignoreCase = true) }
        if (safeOrigin == null || callback == null) {
            callback?.invoke(origin, false, false)
            return
        }
        onGeolocationPrompt(safeOrigin) { allowed ->
            callback.invoke(safeOrigin, allowed, false)
        }
    }

    override fun onShowFileChooser(
        webView: WebView?,
        filePathCallback: ValueCallback<Array<Uri>>?,
        fileChooserParams: FileChooserParams?,
    ): Boolean {
        filePathCallback?.onReceiveValue(null)
        onBlocked("El acceso a archivos está bloqueado en DAG.")
        return true
    }
}

private class DagWebViewClient(
    private val onNavigate: (String, String) -> Unit,
    private val onStarted: (String) -> Boolean,
    private val onReady: (WebView, String) -> Unit,
    private val onBlocked: (String) -> Unit,
    private val onRendererGone: (WebView) -> Unit,
) : WebViewClient() {
    private val pageUrlTracker = DagPageUrlTracker()

    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest,
    ): Boolean {
        val target = request.url
        if (target.scheme != "https") {
            onBlocked("DAG bloqueó una navegación no segura.")
            return true
        }
        val currentHost = Uri.parse(view.url.orEmpty()).host
        if (request.isForMainFrame && currentHost != null && target.host != currentHost) {
            onNavigate(target.toString(), target.host.orEmpty())
            return true
        }
        return false
    }

    override fun onPageStarted(
        view: WebView,
        url: String,
        favicon: Bitmap?,
    ) {
        pageUrlTracker.begin(url)
        if (!onStarted(url)) view.stopLoading()
    }

    override fun onPageCommitVisible(
        view: WebView,
        url: String,
    ) {
        pageUrlTracker.current()?.takeIf { it.matches(url) }?.let {
            if (url.startsWith("https://")) onReady(view, url)
        }
    }

    override fun onPageFinished(
        view: WebView,
        url: String,
    ) {
        pageUrlTracker.current()?.takeIf { it.matches(url) }?.let {
            if (url.startsWith("https://")) onReady(view, url)
        }
    }

    override fun onReceivedSslError(
        view: WebView?,
        handler: SslErrorHandler,
        error: SslError?,
    ) {
        handler.cancel()
        onBlocked("DAG bloqueó un certificado de seguridad inválido.")
    }

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?,
    ) {
        if (request?.isForMainFrame == true) onBlocked("No se pudo cargar la página de forma segura.")
    }

    override fun onSafeBrowsingHit(
        view: WebView?,
        request: WebResourceRequest?,
        threatType: Int,
        callback: SafeBrowsingResponse,
    ) {
        callback.backToSafety(true)
        onBlocked("Navegación peligrosa bloqueada por Android.")
    }

    override fun onRenderProcessGone(
        view: WebView,
        detail: RenderProcessGoneDetail,
    ): Boolean {
        onRendererGone(view)
        return true
    }
}

internal class DagPageUrlTracker {
    private var generation = 0L
    private var document: DagPageDocument? = null

    @Synchronized
    fun begin(url: String): DagPageDocument {
        generation += 1
        return DagPageDocument(generation, url).also { document = it }
    }

    @Synchronized
    fun current(): DagPageDocument? = document
}

internal data class DagPageDocument(
    val generation: Long,
    val url: String,
) {
    fun matches(value: String?): Boolean = value?.substringBefore('#') == url.substringBefore('#')
}
