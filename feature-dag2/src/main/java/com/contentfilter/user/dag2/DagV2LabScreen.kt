package com.contentfilter.user.dag2

import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.net.Uri
import android.net.http.SslError
import android.webkit.ClientCertRequest
import android.webkit.ConsoleMessage
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
import androidx.compose.foundation.Image
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.ScriptHandler
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject

@Composable
internal fun DagV2LabScreen(
    coordinator: DagV2BrowserCoordinator,
    metrics: DagV2Metrics,
    resourceInterceptor: DagV2ResourceInterceptor,
    serviceWorkerRouter: DagV2ServiceWorkerRouter,
    pageAnalyzer: DagV2PageAnalyzer,
    webViewLifecycle: DagV2WebViewLifecycle,
    webViewHost: DagV2WebViewHost<WebView>,
    calibrationController: DagV2CalibrationController,
) {
    val state by coordinator.state.collectAsStateWithLifecycle()
    val metricSnapshot by metrics.snapshot.collectAsStateWithLifecycle()
    val calibrationState by calibrationController.state.collectAsStateWithLifecycle()
    var webView by remember { mutableStateOf<WebView?>(null) }
    var showCalibrationConfirmation by remember { mutableStateOf(false) }

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
                    onClick = {
                        webView
                            ?.takeIf(WebView::canGoBack)
                            ?.goBack()
                            ?: coordinator.goBack()
                    },
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                }
                IconButton(
                    enabled = state.canGoForward,
                    onClick = {
                        webView
                            ?.takeIf(WebView::canGoForward)
                            ?.goForward()
                            ?: coordinator.goForward()
                    },
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Adelante")
                }
                IconButton(
                    enabled = state.requestedUrl != null,
                    onClick = coordinator::refresh,
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
        TextButton(
            onClick = {
                val enabled = !state.noCacheMode
                val currentUrl = state.requestedUrl
                coordinator.setNoCacheMode(enabled)
                serviceWorkerRouter.setNoCacheMode(enabled)
                webView?.apply {
                    settings.cacheMode = if (enabled) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
                    if (enabled) clearCache(true)
                }
                if (enabled && currentUrl != null) coordinator.navigate(currentUrl)
            },
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
            Text(if (state.noCacheMode) "Sin caché DEV: activo" else "Sin caché DEV: inactivo")
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.fillMaxWidth(0.8f)) {
                Text("Calibración DAG v2", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Las páginas conservan placeholders neutros.",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Switch(
                checked = calibrationState.enabled,
                onCheckedChange = { enabled ->
                    if (enabled) showCalibrationConfirmation = true else calibrationController.setEnabled(false)
                },
            )
        }
        if (calibrationState.enabled) {
            TextButton(
                onClick = calibrationController::openReview,
                enabled = calibrationState.candidateCount > 0,
                modifier = Modifier.padding(horizontal = 8.dp),
            ) {
                Text("Revisar imágenes (${calibrationState.candidateCount})")
            }
            calibrationState.statusMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                )
            }
        }
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
                        metrics = metrics,
                        resourceInterceptor = resourceInterceptor,
                        pageAnalyzer = pageAnalyzer,
                        webViewLifecycle = webViewLifecycle,
                        webViewHost = webViewHost,
                        onWebViewCreated = { webView = it },
                        onWebViewReleased = { released ->
                            if (webView === released) webView = null
                        },
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
    if (showCalibrationConfirmation) {
        AlertDialog(
            onDismissRequest = { showCalibrationConfirmation = false },
            title = { Text("Activar Calibración DAG v2") },
            text = {
                Text(
                    "Las imágenes seguirán ocultas en las páginas.\n\n" +
                        "Sólo se mostrarán dentro del visor de revisión cuando las abras expresamente.\n\n" +
                        "Las etiquetas se enviarán al conjunto de evidencia global de DAG v2.\n\n" +
                        "No cambiarán el filtro actual.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCalibrationConfirmation = false
                        calibrationController.setEnabled(true)
                    },
                ) {
                    Text("Activar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCalibrationConfirmation = false }) {
                    Text("Cancelar")
                }
            },
        )
    }
    if (calibrationState.reviewOpen) {
        DagV2CalibrationReviewDialog(
            state = calibrationState,
            onOpenCandidate = calibrationController::openCandidate,
            onLabel = calibrationController::label,
            onClose = calibrationController::closeReview,
        )
    }
}

@Composable
private fun DagV2CalibrationReviewDialog(
    state: DagV2CalibrationReviewState,
    onOpenCandidate: (String) -> Unit,
    onLabel: (DagV2CalibrationDecision) -> Unit,
    onClose: () -> Unit,
) {
    Dialog(onDismissRequest = onClose) {
        Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Revisión aislada", style = MaterialTheme.typography.titleLarge)
                Text(
                    "La imagen se muestra sólo en este visor nativo.",
                    style = MaterialTheme.typography.bodySmall,
                )
                when {
                    state.loadingCandidateId != null -> {
                        CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(24.dp))
                        Text("Validando y normalizando…", Modifier.align(Alignment.CenterHorizontally))
                    }
                    state.preview != null && state.previewCandidate != null -> {
                        val bytes = state.preview.jpegBytes
                        val bitmap =
                            remember(bytes) {
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            }
                        DisposableEffect(bitmap) {
                            onDispose { bitmap?.takeUnless(android.graphics.Bitmap::isRecycled)?.recycle() }
                        }
                        bitmap?.let {
                            Image(
                                bitmap = it.asImageBitmap(),
                                contentDescription = "Preview de calibración",
                                modifier = Modifier.fillMaxWidth().height(360.dp).padding(vertical = 12.dp),
                            )
                        }
                        Text(
                            "Origen: ${state.previewCandidate.sanitizedResourceHost}",
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Text(
                            "Posición: 1 de ${state.candidateCount.coerceAtLeast(1)}",
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            Button(onClick = { onLabel(DagV2CalibrationDecision.Show) }) {
                                Text("✓ Mostrar")
                            }
                            Button(onClick = { onLabel(DagV2CalibrationDecision.Hide) }) {
                                Text("× Ocultar")
                            }
                        }
                        Button(
                            onClick = { onLabel(DagV2CalibrationDecision.Unsure) },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) {
                            Text("? No estoy seguro")
                        }
                    }
                    else -> {
                        Text(
                            "Elegí expresamente una imagen para descargar su preview.",
                            modifier = Modifier.padding(vertical = 10.dp),
                        )
                        LazyColumn(Modifier.height(360.dp)) {
                            items(state.candidates, key = DagV2CalibrationCandidate::candidateId) { candidate ->
                                Column(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable { onOpenCandidate(candidate.candidateId) }
                                            .padding(vertical = 12.dp),
                                ) {
                                    Text(candidate.sanitizedResourceHost)
                                    Text(
                                        "Candidato raster · ${candidate.observedAt}",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
                state.statusMessage?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp))
                }
                TextButton(onClick = onClose, modifier = Modifier.align(Alignment.End)) {
                    Text("Cerrar")
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun DagV2WebContent(
    state: DagV2BrowserState,
    coordinator: DagV2BrowserCoordinator,
    metrics: DagV2Metrics,
    resourceInterceptor: DagV2ResourceInterceptor,
    pageAnalyzer: DagV2PageAnalyzer,
    webViewLifecycle: DagV2WebViewLifecycle,
    webViewHost: DagV2WebViewHost<WebView>,
    onWebViewCreated: (WebView) -> Unit,
    onWebViewReleased: (WebView) -> Unit,
) {
    val context = LocalContext.current
    val requestContext = state.requestContext ?: return
    val requestedUrl = state.requestedUrl ?: return

    Box(Modifier.fillMaxSize()) {
        key(requestContext.navigationToken) {
            val runtimeScripts = remember { DagV2RuntimeScriptHandle() }
            AndroidView(
                modifier = Modifier.fillMaxSize().alpha(if (state.documentVisible) 1f else 0f),
                factory = {
                    WebView(context).apply {
                        onWebViewCreated(this)
                        webViewHost.attach(this) {
                            webViewLifecycle.release(this, runtimeScripts::clear)
                        }
                        configureDagV2Settings(state.noCacheMode)
                        if (state.noCacheMode) clearCache(true)
                        val runtimeReady =
                            WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) &&
                                WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)
                        if (!runtimeReady) {
                            coordinator.onSecurityFailure(
                                requestContext,
                                "WebView no admite el script seguro de inicio requerido.",
                            )
                            return@apply
                        }
                        WebViewCompat.addWebMessageListener(
                            this,
                            DagV2BridgeName,
                            setOf("*"),
                            DagV2WebMessageBridge(requestContext, coordinator, pageAnalyzer),
                        )
                        runtimeScripts.replace(
                            WebViewCompat.addDocumentStartJavaScript(
                                this,
                                dagV2DocumentStartScript(requestContext),
                                setOf("*"),
                            ),
                        )
                        webChromeClient = DagV2ChromeClient(metrics, coordinator, requestContext)
                        webViewClient = DagV2WebViewClient(coordinator, resourceInterceptor, requestContext)
                        setDownloadListener { _, _, _, _, _ -> Unit }
                        setBackgroundColor(android.graphics.Color.WHITE)
                        coordinator.onDocumentStarted(requestContext)
                        loadUrl(
                            requestedUrl,
                            mapOf(DagV2NavigationTokenHeader to requestContext.navigationToken),
                        )
                    }
                },
                update = { view ->
                    view.settings.cacheMode =
                        if (state.noCacheMode) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
                },
                onRelease = { view ->
                    webViewHost.detach(view)
                    onWebViewReleased(view)
                },
            )
        }
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
private fun WebView.configureDagV2Settings(noCacheMode: Boolean) {
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
    settings.cacheMode = if (noCacheMode) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
    settings.safeBrowsingEnabled = true
    CookieManager.getInstance().apply {
        setAcceptCookie(true)
        setAcceptThirdPartyCookies(this@configureDagV2Settings, true)
    }
    WebView.startSafeBrowsing(context, null)
}

private class DagV2RuntimeScriptHandle {
    private var handler: ScriptHandler? = null

    fun replace(next: ScriptHandler) {
        clear()
        handler = next
    }

    fun clear() {
        handler?.remove()
        handler = null
    }
}

private class DagV2ChromeClient(
    private val metrics: DagV2Metrics,
    private val coordinator: DagV2BrowserCoordinator,
    private val context: DagV2DocumentRequestContext,
) : WebChromeClient() {
    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
        if (
            coordinator.accepts(context) &&
            consoleMessage?.messageLevel() == ConsoleMessage.MessageLevel.ERROR
        ) {
            metrics.event(DagV2MetricNames.ConsoleError)
        }
        return super.onConsoleMessage(consoleMessage)
    }

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
        return true
    }
}

private class DagV2WebViewClient(
    private val coordinator: DagV2BrowserCoordinator,
    private val resourceInterceptor: DagV2ResourceInterceptor,
    private val context: DagV2DocumentRequestContext,
) : WebViewClient() {
    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest,
    ): Boolean {
        if (!request.isForMainFrame) return false
        val attributed = resourceInterceptor.attribute(request, DagV2ResourceSource.WebView, context)
        if (attributed.attribution != DagV2RequestAttribution.Current) return true
        val url = request.url.toString()
        if (!url.isHttpsDagV2Url()) {
            coordinator.onSecurityFailure(context, "DAG v2 bloqueó una navegación no HTTPS.")
            return true
        }
        if (coordinator.isHashOnlyNavigation(context, url)) {
            coordinator.onSpaUrlChanged(context, url)
            return false
        }
        coordinator.navigate(url)
        return true
    }

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest,
    ): WebResourceResponse? = resourceInterceptor.intercept(request, DagV2ResourceSource.WebView, context)

    override fun onPageStarted(
        view: WebView,
        url: String,
        favicon: android.graphics.Bitmap?,
    ) {
        if (!coordinator.onMainFrameStarted(context, url)) view.stopLoading()
    }

    override fun onReceivedSslError(
        view: WebView?,
        handler: SslErrorHandler,
        error: SslError?,
    ) {
        handler.cancel()
    }

    override fun onReceivedHttpAuthRequest(
        view: WebView?,
        handler: HttpAuthHandler,
        host: String?,
        realm: String?,
    ) {
        handler.cancel()
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
        if (request != null) {
            val attributed =
                resourceInterceptor.attribute(request, DagV2ResourceSource.WebView, context)
            if (attributed.attribution == DagV2RequestAttribution.Current) {
                coordinator.onSecurityFailure(context, "Android Safe Browsing bloqueó la navegación.")
            }
        }
    }

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?,
    ) {
        if (request?.isForMainFrame == true) {
            val attributed =
                resourceInterceptor.attribute(request, DagV2ResourceSource.WebView, context)
            if (attributed.attribution == DagV2RequestAttribution.Current) {
                coordinator.onSecurityFailure(context, "No se pudo cargar el documento de forma segura.")
            }
        }
    }

    override fun onRenderProcessGone(
        view: WebView,
        detail: RenderProcessGoneDetail,
    ): Boolean {
        coordinator.onRendererGone(context)
        return true
    }
}

private class DagV2WebMessageBridge(
    private val boundContext: DagV2DocumentRequestContext,
    private val coordinator: DagV2BrowserCoordinator,
    private val pageAnalyzer: DagV2PageAnalyzer,
) : WebViewCompat.WebMessageListener {
    override fun onPostMessage(
        view: WebView,
        message: WebMessageCompat,
        sourceOrigin: Uri,
        isMainFrame: Boolean,
        replyProxy: JavaScriptReplyProxy,
    ) {
        val payload = message.data?.let { runCatching { JSONObject(it) }.getOrNull() }
        val sessionId = payload?.optString("sessionId").orEmpty()
        val navigationToken = payload?.optString("navigationToken").orEmpty()
        if (
            sessionId != boundContext.sessionId ||
            navigationToken != boundContext.navigationToken
        ) {
            coordinator.onRejectedDocumentCallback()
            return
        }
        val context =
            coordinator.authorizeBridgeMessage(
                sessionId = sessionId,
                navigationToken = navigationToken,
                sourceOrigin = sourceOrigin.toString(),
                isMainFrame = isMainFrame,
            )
        if (context == null) {
            coordinator.onRejectedDocumentCallback()
            return
        }
        val values = payload?.optJSONObject("payload") ?: JSONObject()
        when (payload?.optString("type")) {
            "document_ready" -> onDocumentReady(view, context)
            "spa_location" -> {
                val url = values.optString("url")
                val interaction =
                    when (values.optString("kind")) {
                        "push" -> DagV2InternalInteraction.PushState
                        "replace" -> DagV2InternalInteraction.ReplaceState
                        "hash" -> DagV2InternalInteraction.Hash
                        else -> DagV2InternalInteraction.PushState
                    }
                coordinator.onInternalInteraction(context, interaction)
                coordinator.onSpaUrlChanged(context, url)
                coordinator.onWebViewHistoryChanged(context, view.canGoBack(), view.canGoForward())
            }
            "internal_interaction" ->
                coordinator.onInternalInteraction(
                    context,
                    when (values.optString("kind")) {
                        "accordion" -> DagV2InternalInteraction.Accordion
                        "filter" -> DagV2InternalInteraction.Filter
                        else -> DagV2InternalInteraction.Button
                    },
                )
            else -> coordinator.onRejectedDocumentCallback()
        }
    }

    private fun onDocumentReady(
        view: WebView,
        context: DagV2DocumentRequestContext,
    ) {
        if (coordinator.onDocumentCommitted(context) == null) return
        pageAnalyzer.analyze(
            view = view,
            context = context,
            onSuccess = { evidence ->
                coordinator.onDocumentAnalysis(
                    context = evidence.context,
                    url = evidence.url,
                    title = evidence.title,
                    visibleText = evidence.visibleText,
                )
            },
            onFailure = { coordinator.onDocumentAnalysisFailed(context) },
            onDiscarded = coordinator::onRejectedDocumentCallback,
        )
    }
}
