package com.contentfilter.user.dag2

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.webkit.ClientCertRequest
import android.webkit.CookieManager
import android.webkit.HttpAuthHandler
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SafeBrowsingResponse
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONArray
import org.json.JSONObject

@Composable
internal fun DagV2LabScreen(
    coordinator: DagV2BrowserCoordinator,
    metrics: DagV2Metrics,
    resourceRouter: DagV2ResourceRouter,
) {
    val state by coordinator.state.collectAsStateWithLifecycle()
    val metricSnapshot by metrics.snapshot.collectAsStateWithLifecycle()
    var webView by remember { mutableStateOf<WebView?>(null) }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Spacer(Modifier.height(24.dp))
        Text(
            text = "DAG v2 Lab",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        OutlinedTextField(
            value = state.input,
            onValueChange = coordinator::updateInput,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            label = { Text("Buscar o abrir HTTPS") },
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = coordinator::submit) {
                    Icon(Icons.Default.Search, contentDescription = "Buscar")
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { coordinator.submit() }),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row {
                IconButton(
                    enabled = state.canGoBack,
                    onClick = { webView?.goBack() },
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                }
                IconButton(
                    enabled = state.canGoForward,
                    onClick = { webView?.goForward() },
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Adelante")
                }
                IconButton(
                    enabled = state.requestedUrl != null,
                    onClick = { state.requestedUrl?.let(coordinator::navigate) },
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Actualizar")
                }
            }
            Text(
                text = "Documento: ${state.fullPageAnalysisCount} · Fotos pendientes: ${metricSnapshot.visualPendingCount}",
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Text(
            text = state.statusMessage,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        state.blockedMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        HorizontalDivider()
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.searching -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.results.isNotEmpty() ->
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(state.results, key = DagV2SearchResult::url) { result ->
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { coordinator.openResult(result) }
                                        .padding(16.dp),
                            ) {
                                Text(result.title, style = MaterialTheme.typography.titleMedium)
                                Text(result.url, style = MaterialTheme.typography.labelSmall)
                                Text(result.description, style = MaterialTheme.typography.bodySmall)
                            }
                            HorizontalDivider()
                        }
                    }
                state.requestedUrl != null ->
                    DagV2WebContent(
                        state = state,
                        coordinator = coordinator,
                        resourceRouter = resourceRouter,
                        onWebView = { webView = it },
                    )
                else ->
                    Text(
                        text = "DAG v1 sigue siendo el navegador activo. Este acceso es un laboratorio DEV aislado.",
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun DagV2WebContent(
    state: DagV2BrowserState,
    coordinator: DagV2BrowserCoordinator,
    resourceRouter: DagV2ResourceRouter,
    onWebView: (WebView?) -> Unit,
) {
    val context = LocalContext.current
    var loadedRevision by remember { mutableLongStateOf(-1L) }
    var runtimeReady by remember { mutableStateOf(false) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    DisposableEffect(Unit) {
        onDispose { onWebView(null) }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize().alpha(if (state.documentVisible) 1f else 0f),
            factory = {
                WebView(context).apply {
                    onWebView(this)
                    configureDagV2Settings()
                    val bridge = DagV2JavaScriptBridge(mainHandler, coordinator)
                    addJavascriptInterface(bridge, "DagV2Bridge")
                    runtimeReady = installDagV2DocumentStartScript()
                    if (!runtimeReady) {
                        coordinator.onSecurityFailure("WebView no admite el script seguro de inicio requerido.")
                    }
                    webChromeClient = DagV2ChromeClient(coordinator)
                    webViewClient = DagV2WebViewClient(coordinator, resourceRouter)
                    setDownloadListener { _, _, _, _, _ ->
                        coordinator.onSecurityFailure("Las descargas están bloqueadas en DAG v2 Lab.")
                    }
                    setBackgroundColor(android.graphics.Color.WHITE)
                }
            },
            update = { view ->
                val requested = state.requestedUrl
                if (requested == null) {
                    if (view.url != "about:blank") {
                        view.stopLoading()
                        view.loadUrl("about:blank")
                    }
                } else if (runtimeReady && loadedRevision != state.navigationRevision) {
                    loadedRevision = state.navigationRevision
                    view.loadUrl(requested)
                }
            },
        )
        if (state.documentAnalyzing && !state.documentVisible) {
            Surface(
                color = Color(0xFFF6F7F9),
                modifier = Modifier.fillMaxSize(),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                    Text("Comprobando el documento…", Modifier.padding(12.dp))
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun WebView.configureDagV2Settings() {
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.databaseEnabled = true
    settings.loadsImagesAutomatically = true
    settings.blockNetworkImage = false
    settings.blockNetworkLoads = false
    settings.allowFileAccess = false
    settings.allowContentAccess = false
    settings.allowFileAccessFromFileURLs = false
    settings.allowUniversalAccessFromFileURLs = false
    settings.javaScriptCanOpenWindowsAutomatically = false
    settings.setSupportMultipleWindows(false)
    settings.mediaPlaybackRequiresUserGesture = true
    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
    settings.cacheMode = WebSettings.LOAD_DEFAULT
    settings.safeBrowsingEnabled = true
    CookieManager.getInstance().apply {
        setAcceptCookie(true)
        setAcceptThirdPartyCookies(this@configureDagV2Settings, true)
    }
    WebView.startSafeBrowsing(context, null)
}

private fun WebView.installDagV2DocumentStartScript(): Boolean {
    if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return false
    WebViewCompat.addDocumentStartJavaScript(this, DagV2DocumentStartScript, setOf("*"))
    return true
}

private class DagV2ChromeClient(
    private val coordinator: DagV2BrowserCoordinator,
) : WebChromeClient() {
    override fun onPermissionRequest(request: PermissionRequest) {
        request.deny()
    }

    override fun onGeolocationPermissionsShowPrompt(
        origin: String?,
        callback: android.webkit.GeolocationPermissions.Callback,
    ) {
        callback.invoke(origin, false, false)
    }

    override fun onShowFileChooser(
        webView: WebView?,
        filePathCallback: ValueCallback<Array<android.net.Uri>>?,
        fileChooserParams: FileChooserParams?,
    ): Boolean {
        filePathCallback?.onReceiveValue(null)
        coordinator.onSecurityFailure("La selección de archivos está bloqueada.")
        return true
    }
}

private class DagV2WebViewClient(
    private val coordinator: DagV2BrowserCoordinator,
    private val router: DagV2ResourceRouter,
) : WebViewClient() {
    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest,
    ): Boolean {
        if (!request.isForMainFrame) return false
        val url = request.url.toString()
        if (!url.isHttpsDagV2Url()) {
            coordinator.onSecurityFailure("DAG v2 bloqueó una navegación no HTTPS.")
            return true
        }
        if (coordinator.isExpectedDocument(url)) return false
        coordinator.navigate(url)
        return true
    }

    override fun onPageStarted(
        view: WebView,
        url: String,
        favicon: Bitmap?,
    ) {
        if (url.startsWith("https://") && !coordinator.isExpectedDocument(url)) {
            view.stopLoading()
            coordinator.navigate(url)
            return
        }
        if (url.startsWith("https://")) coordinator.onDocumentStarted(url)
    }

    override fun onPageCommitVisible(
        view: WebView,
        url: String,
    ) {
        val session = coordinator.onDocumentCommitted(url) ?: return
        view.postDelayed(
            { extractDocumentOnce(view, session, coordinator) },
            DocumentSettleDelayMillis,
        )
    }

    override fun onPageFinished(
        view: WebView,
        url: String,
    ) {
        coordinator.onNavigationState(view.canGoBack(), view.canGoForward())
    }

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest,
    ): WebResourceResponse? = router.intercept(request, DagV2ResourceSource.WebView)

    override fun onReceivedSslError(
        view: WebView?,
        handler: SslErrorHandler,
        error: SslError?,
    ) {
        handler.cancel()
        coordinator.onSecurityFailure("DAG v2 bloqueó un certificado inválido.")
    }

    override fun onReceivedHttpAuthRequest(
        view: WebView?,
        handler: HttpAuthHandler,
        host: String?,
        realm: String?,
    ) {
        handler.cancel()
        coordinator.onSecurityFailure("La autenticación HTTP interactiva está bloqueada.")
    }

    override fun onReceivedClientCertRequest(
        view: WebView?,
        request: ClientCertRequest,
    ) {
        request.cancel()
    }

    override fun onSafeBrowsingHit(
        view: WebView?,
        request: WebResourceRequest?,
        threatType: Int,
        callback: SafeBrowsingResponse,
    ) {
        callback.backToSafety(true)
        coordinator.onSecurityFailure("Android Safe Browsing bloqueó la navegación.")
    }

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?,
    ) {
        if (request?.isForMainFrame == true) {
            coordinator.onCurrentDocumentFailure("No se pudo cargar el documento de forma segura.")
        }
    }

    override fun onRenderProcessGone(
        view: WebView,
        detail: RenderProcessGoneDetail,
    ): Boolean {
        coordinator.onRendererGone()
        view.destroy()
        return true
    }
}

private fun extractDocumentOnce(
    view: WebView,
    session: DagV2DocumentSessionState,
    coordinator: DagV2BrowserCoordinator,
) {
    var completed = false
    val timeout =
        Runnable {
            if (!completed) {
                completed = true
                coordinator.onDocumentAnalysisFailed(session.sessionId, session.navigationToken)
            }
        }
    view.postDelayed(timeout, DocumentAnalysisTimeoutMillis)
    view.evaluateJavascript(
        """
        (function() {
          return JSON.stringify({
            title: String(document.title || '').substring(0, 500),
            text: String(document.body && document.body.innerText || '').substring(0, 24000)
          });
        })();
        """.trimIndent(),
    ) { encoded ->
        if (completed) return@evaluateJavascript
        completed = true
        view.removeCallbacks(timeout)
        val payload =
            runCatching {
                val decoded = JSONArray("[$encoded]").getString(0)
                JSONObject(decoded)
            }.getOrNull()
        if (payload == null) {
            coordinator.onDocumentAnalysisFailed(session.sessionId, session.navigationToken)
        } else {
            coordinator.onDocumentAnalysis(
                sessionId = session.sessionId,
                navigationToken = session.navigationToken,
                url = session.mainDocumentUrl,
                title = payload.optString("title"),
                visibleText = payload.optString("text"),
            )
        }
    }
}

private class DagV2JavaScriptBridge(
    private val handler: Handler,
    private val coordinator: DagV2BrowserCoordinator,
) {
    @android.webkit.JavascriptInterface
    fun onSpaLocation(
        url: String,
        kind: String,
    ) {
        handler.post {
            val interaction =
                when (kind) {
                    "pushState" -> DagV2InternalInteraction.PushState
                    "replaceState" -> DagV2InternalInteraction.ReplaceState
                    else -> DagV2InternalInteraction.Hash
                }
            coordinator.onInternalInteraction(interaction)
            coordinator.onSpaUrlChanged(url)
        }
    }

    @android.webkit.JavascriptInterface
    fun onInternalInteraction(kind: String) {
        handler.post {
            coordinator.onInternalInteraction(
                when (kind) {
                    "accordion" -> DagV2InternalInteraction.Accordion
                    "filter" -> DagV2InternalInteraction.Filter
                    else -> DagV2InternalInteraction.Button
                },
            )
        }
    }
}

private const val DocumentAnalysisTimeoutMillis = 8_000L
private const val DocumentSettleDelayMillis = 500L
