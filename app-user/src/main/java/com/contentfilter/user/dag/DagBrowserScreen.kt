package com.contentfilter.user.dag

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
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
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contentfilter.core.ui.ProductCard
import com.contentfilter.core.ui.ProductLargeFeatureCard
import com.contentfilter.core.ui.ProductViolet
import com.contentfilter.core.ui.ProductVisualPage
import org.json.JSONArray
import java.text.DateFormat
import java.util.Date

@Composable
fun DagBrowserRoute(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    standalone: Boolean = false,
    viewModel: DagBrowserViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (!state.dagAvailabilityKnown) {
        Box(modifier = modifier.background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    if (!state.dagEnabled) {
        ProductVisualPage(title = "DAG", subtitle = "Buscador protegido", onBack = onBack) {
            ProductLargeFeatureCard(
                title = "DAG está cerrado",
                subtitle = "El administrador debe habilitar DAG para este dispositivo.",
                accent = ProductViolet,
            )
        }
        return
    }

    BackHandler(enabled = state.view != DagView.Browser) {
        if (state.view == DagView.Start) onBack() else viewModel.showStart()
    }
    var menuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.background(MaterialTheme.colorScheme.background).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!standalone || state.view != DagView.Start) {
                TextButton(onClick = { if (state.view == DagView.Start) onBack() else viewModel.showStart() }) {
                    Text("‹", style = MaterialTheme.typography.headlineMedium)
                }
            }
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = state.address,
                onValueChange = viewModel::onAddressChanged,
                placeholder = { Text("Buscar en DAG o escribir dirección") },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions =
                    androidx.compose.foundation.text.KeyboardActions(
                        onGo = { viewModel.submitAddress() },
                    ),
            )
            Button(
                onClick = viewModel::submitAddress,
                enabled = !state.loading,
                shape = RoundedCornerShape(24.dp),
            ) { Text("Ir") }
            Box {
                TextButton(onClick = { menuExpanded = true }) {
                    Text("⋮", style = MaterialTheme.typography.headlineSmall)
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Inicio") },
                        onClick = {
                            menuExpanded = false
                            viewModel.showStart()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Historial") },
                        onClick = {
                            menuExpanded = false
                            viewModel.showHistory()
                        },
                    )
                }
            }
        }

        if (state.loading) CircularProgressIndicator(modifier = Modifier.height(28.dp))

        if (state.message.isNotBlank()) {
            ProductCard {
                Text(state.message, color = MaterialTheme.colorScheme.onSurface)
                state.reviewCandidate?.let { candidate ->
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { viewModel.requestReview(candidate) }) {
                        Text("Pedir revisión de ${candidate.domain}")
                    }
                }
            }
        }

        when (state.view) {
            DagView.Start -> Unit
            DagView.Results -> DagResultsContent(state.results, viewModel::openResult, viewModel::requestReview)
            DagView.History ->
                DagHistoryContent(
                    history = state.history,
                    onOpen = viewModel::openHistory,
                    onDelete = viewModel::deleteHistory,
                    onClear = viewModel::clearHistory,
                )
            DagView.Browser ->
                DagWebContent(
                    state = state,
                    onBackFromBrowser = viewModel::showStart,
                    onNavigate = viewModel::requestNavigation,
                    onPageStarted = viewModel::onPageStarted,
                    onPageTextReady = viewModel::onPageTextReady,
                    onBlockedAction = viewModel::onBrowserBlockedAction,
                    onPageBlocked = viewModel::onPageBlocked,
                    onHome = viewModel::showStart,
                )
        }
    }
}

@Composable
private fun DagResultsContent(
    results: List<DagSearchResult>,
    onOpen: (DagSearchResult) -> Unit,
    onReview: (DagReviewCandidate) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(results, key = { it.url }) { result ->
            ProductCard(
                modifier =
                    Modifier.clickable(enabled = result.classification.decision == DagClassification.Allowed) {
                        onOpen(result)
                    },
            ) {
                Text(result.title, style = MaterialTheme.typography.titleMedium)
                Text(result.domain, color = MaterialTheme.colorScheme.primary)
                if (result.description.isNotBlank()) Text(result.description)
                if (result.classification.decision == DagClassification.Uncertain) {
                    Button(
                        onClick = {
                            onReview(
                                DagReviewCandidate(
                                    domain = result.domain,
                                    title = result.title,
                                    category = result.classification.category,
                                    modelVersion = result.classification.modelVersion,
                                ),
                            )
                        },
                    ) {
                        Text("Pedir revisión")
                    }
                }
            }
        }
    }
}

@Composable
private fun DagHistoryContent(
    history: List<DagHistoryEntry>,
    onOpen: (DagHistoryEntry) -> Unit,
    onDelete: (String) -> Unit,
    onClear: () -> Unit,
) {
    var confirmClear by remember { mutableStateOf(false) }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Historial local", style = MaterialTheme.typography.titleLarge)
        TextButton(onClick = { confirmClear = true }, enabled = history.isNotEmpty()) { Text("Borrar todo") }
    }
    if (history.isEmpty()) {
        ProductCard { Text("Todavía no hay búsquedas ni páginas visitadas.") }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(history, key = { it.id }) { entry ->
                ProductCard(modifier = Modifier.clickable { onOpen(entry) }) {
                    Text(entry.title ?: entry.value, style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (entry.type == DagHistoryType.Search) {
                            "Búsqueda"
                        } else {
                            DagContentClassifier.domainFrom(
                                entry.url.orEmpty(),
                            )
                        },
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        DateFormat.getDateTimeInstance(
                            DateFormat.SHORT,
                            DateFormat.SHORT,
                        ).format(Date(entry.visitedAtEpochMillis)),
                    )
                    TextButton(onClick = { onDelete(entry.id) }) { Text("Borrar") }
                }
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("¿Borrar todo el historial?") },
            text = { Text("Se eliminarán del teléfono todas las búsquedas y páginas guardadas en DAG.") },
            confirmButton = {
                Button(onClick = {
                    onClear()
                    confirmClear = false
                }) { Text("Borrar todo") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar") } },
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun DagWebContent(
    state: DagBrowserUiState,
    onBackFromBrowser: () -> Unit,
    onNavigate: (String, String) -> Unit,
    onPageStarted: (String) -> Boolean,
    onPageTextReady: (String, String, String?) -> Unit,
    onBlockedAction: (String) -> Unit,
    onPageBlocked: (String) -> Unit,
    onHome: () -> Unit,
) {
    val context = LocalContext.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var inspectedUrl by remember { mutableStateOf<String?>(null) }

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
            if (url.startsWith("https://", ignoreCase = true) && webView?.url != url) webView?.loadUrl(url)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            webView?.stopLoading()
            webView?.destroy()
            webView = null
        }
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { webView?.goBack() }, enabled = canGoBack) { Text("◀") }
        OutlinedButton(onClick = { webView?.goForward() }, enabled = canGoForward) { Text("▶") }
        OutlinedButton(onClick = { webView?.reload() }) { Text("↻") }
        OutlinedButton(onClick = {
            webView?.stopLoading()
            onHome()
        }) { Text("⌂") }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize().alpha(if (state.pageStatus == DagPageStatus.Visible) 1f else 0f),
            factory = {
                WebView(context).apply {
                    webView = this
                    configureDagSettings()
                    val dagWebView = this
                    CookieManager.getInstance().apply {
                        setAcceptCookie(true)
                        setAcceptThirdPartyCookies(dagWebView, false)
                    }
                    webChromeClient = DagChromeClient(onBlockedAction)
                    webViewClient =
                        DagWebViewClient(
                            onNavigate = onNavigate,
                            onStarted = { url ->
                                inspectedUrl = null
                                onPageStarted(url)
                            },
                            onFinished = { view, url ->
                                canGoBack = view.canGoBack()
                                canGoForward = view.canGoForward()
                                if (inspectedUrl != url) {
                                    inspectedUrl = url
                                    view.sanitizeAndExtractVisibleText { text ->
                                        onPageTextReady(url, view.title.orEmpty(), text)
                                    }
                                }
                            },
                            onBlocked = onPageBlocked,
                        )
                    setDownloadListener { _, _, _, _, _ -> onBlockedAction("Las descargas están bloqueadas en DAG.") }
                }
            },
        )
        if (state.pageStatus != DagPageStatus.Visible) {
            Column(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (state.pageStatus == DagPageStatus.Loading) CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text(
                    when (state.pageStatus) {
                        DagPageStatus.Loading -> "DAG está analizando la página…"
                        DagPageStatus.Blocked -> "Página bloqueada"
                        DagPageStatus.Uncertain -> "Página pendiente de revisión"
                        else -> "Preparando navegador"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
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
    settings.loadsImagesAutomatically = false
    settings.blockNetworkImage = true
    settings.mediaPlaybackRequiresUserGesture = true
    settings.javaScriptCanOpenWindowsAutomatically = false
    settings.setSupportMultipleWindows(false)
    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
    settings.cacheMode = WebSettings.LOAD_DEFAULT
    settings.safeBrowsingEnabled = true
}

private class DagChromeClient(
    private val onBlocked: (String) -> Unit,
) : WebChromeClient() {
    override fun onPermissionRequest(request: PermissionRequest) {
        request.deny()
        onBlocked("Cámara y micrófono están bloqueados en DAG.")
    }

    override fun onGeolocationPermissionsShowPrompt(
        origin: String?,
        callback: GeolocationPermissions.Callback?,
    ) {
        callback?.invoke(origin, false, false)
        onBlocked("La ubicación está bloqueada en DAG.")
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
    private val onFinished: (WebView, String) -> Unit,
    private val onBlocked: (String) -> Unit,
) : WebViewClient() {
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
        if (!onStarted(url)) view.stopLoading()
    }

    override fun onPageFinished(
        view: WebView,
        url: String,
    ) {
        if (url.startsWith("https://")) onFinished(view, url)
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

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest,
    ): WebResourceResponse? {
        val path = request.url.lastPathSegment.orEmpty().lowercase()
        if (BlockedMediaExtensions.any(path::endsWith)) {
            return WebResourceResponse("text/plain", "utf-8", 204, "Blocked", emptyMap(), null)
        }
        return null
    }

    private companion object {
        val BlockedMediaExtensions =
            setOf(
                ".jpg", ".jpeg", ".png", ".gif", ".webp", ".svg", ".bmp", ".avif",
                ".mp4", ".webm", ".m3u8", ".mp3", ".wav", ".ogg", ".mov",
            )
    }
}

private fun WebView.sanitizeAndExtractVisibleText(callback: (String?) -> Unit) {
    evaluateJavascript(
        """
        (function() {
          document.querySelectorAll('img,picture,video,audio,source,canvas,iframe').forEach(function(node) { node.remove(); });
          var style = document.getElementById('dag-safe-style');
          if (!style) {
            style = document.createElement('style');
            style.id = 'dag-safe-style';
            style.textContent = '* { background-image: none !important; } img,picture,video,audio,canvas,iframe { display:none !important; }';
            document.documentElement.appendChild(style);
          }
          return document.body ? document.body.innerText.substring(0,24000) : '';
        })();
        """.trimIndent(),
    ) { encoded -> callback(encoded.decodeJavascriptString()) }
}

private fun String?.decodeJavascriptString(): String? {
    if (this == null || this == "null") return null
    return runCatching { JSONArray("[$this]").optString(0).takeIf { it.isNotBlank() } }.getOrNull()
}
