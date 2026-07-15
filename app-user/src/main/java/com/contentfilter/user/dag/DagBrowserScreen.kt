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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.contentfilter.core.ui.PremiumFishMascot
import com.contentfilter.core.ui.ProductCard
import com.contentfilter.core.ui.ProductLargeFeatureCard
import com.contentfilter.core.ui.ProductViolet
import com.contentfilter.core.ui.ProductVisualPage
import org.json.JSONArray
import java.text.DateFormat
import java.util.Date
import java.util.UUID

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun DagBrowserRoute(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    standalone: Boolean = false,
    viewModel: DagBrowserViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(state.view) {
        if (state.view == DagView.Results || state.view == DagView.Browser) {
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }
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
    var tabsExpanded by remember { mutableStateOf(false) }
    var addressFocused by remember { mutableStateOf(false) }
    var activeWebView by remember { mutableStateOf<WebView?>(null) }
    var browserCanGoForward by remember { mutableStateOf(false) }
    var tabs by remember { mutableStateOf(listOf(DagTab(snapshot = viewModel.captureTab()))) }
    var activeTabId by remember { mutableStateOf(tabs.first().id) }

    fun persistActiveTab() {
        tabs = tabs.map { tab -> if (tab.id == activeTabId) tab.copy(snapshot = viewModel.captureTab()) else tab }
    }

    fun openTab(tab: DagTab) {
        persistActiveTab()
        activeTabId = tab.id
        viewModel.restoreTab(tab.snapshot)
        tabsExpanded = false
    }

    fun newTab() {
        persistActiveTab()
        viewModel.openNewTab()
        val tab = DagTab(snapshot = viewModel.captureTab())
        tabs = tabs + tab
        activeTabId = tab.id
        tabsExpanded = false
    }

    fun closeTab(tab: DagTab) {
        persistActiveTab()
        val remaining = tabs.filterNot { it.id == tab.id }
        if (remaining.isEmpty()) {
            viewModel.openNewTab()
            val replacement = DagTab(snapshot = viewModel.captureTab())
            tabs = listOf(replacement)
            activeTabId = replacement.id
        } else {
            tabs = remaining
            if (activeTabId == tab.id) {
                val replacement = remaining.last()
                activeTabId = replacement.id
                viewModel.restoreTab(replacement.snapshot)
            }
        }
    }

    Column(
        modifier =
            modifier
                .background(MaterialTheme.colorScheme.background)
                .then(if (standalone) Modifier.statusBarsPadding() else Modifier),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.view == DagView.Browser) {
                TextButton(
                    onClick = {
                        if (activeWebView?.canGoBack() == true) activeWebView?.goBack() else viewModel.showStart()
                    },
                    modifier = Modifier.width(40.dp),
                ) {
                    Text("‹", style = MaterialTheme.typography.headlineMedium)
                }
                TextButton(
                    onClick = { activeWebView?.goForward() },
                    enabled = browserCanGoForward,
                    modifier = Modifier.width(40.dp),
                ) {
                    Text("›", style = MaterialTheme.typography.headlineMedium)
                }
            } else if (!standalone || state.view != DagView.Start) {
                TextButton(
                    onClick = { if (state.view == DagView.Start) onBack() else viewModel.showStart() },
                    modifier = Modifier.width(40.dp),
                ) { Text("‹", style = MaterialTheme.typography.headlineMedium) }
            }
            OutlinedTextField(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(56.dp)
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused && !addressFocused) viewModel.onAddressChanged("")
                            addressFocused = focusState.isFocused
                        },
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
            TextButton(
                onClick = {
                    if (state.view == DagView.Browser) activeWebView?.reload() else viewModel.submitAddress()
                },
                enabled = !state.loading,
                modifier = Modifier.width(44.dp),
            ) { Text(if (state.view == DagView.Browser) "↻" else "Ir") }
            Box {
                TextButton(
                    onClick = {
                        persistActiveTab()
                        tabsExpanded = true
                    },
                    modifier = Modifier.width(40.dp),
                ) { Text(tabs.size.toString()) }
                DropdownMenu(expanded = tabsExpanded, onDismissRequest = { tabsExpanded = false }) {
                    tabs.forEachIndexed { index, tab ->
                        val snapshot = if (tab.id == activeTabId) viewModel.captureTab() else tab.snapshot
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        snapshot.tabLabel(index + 1),
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    TextButton(onClick = { closeTab(tab) }) { Text("×") }
                                }
                            },
                            onClick = { openTab(tab.copy(snapshot = snapshot)) },
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("＋ Nueva pestaña") }, onClick = ::newTab)
                }
            }
            Box {
                TextButton(onClick = { menuExpanded = true }, modifier = Modifier.width(40.dp)) {
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
        HorizontalDivider()

        if (state.loading) CircularProgressIndicator(modifier = Modifier.height(24.dp).padding(start = 8.dp))

        if (state.message.isNotBlank()) {
            Surface(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(state.message, style = MaterialTheme.typography.bodyMedium)
                    state.reviewCandidate?.let { candidate ->
                        Spacer(Modifier.height(4.dp))
                        TextButton(onClick = { viewModel.requestReview(candidate) }) {
                            Text("Pedir revisión de ${candidate.domain}")
                        }
                    }
                }
            }
        }

        when (state.view) {
            DagView.Start -> DagStartContent()
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
                    onWebViewChanged = { activeWebView = it },
                    onNavigationStateChanged = { _, canGoForward -> browserCanGoForward = canGoForward },
                )
        }
    }

    BackHandler(enabled = WindowInsets.isImeVisible) {
        focusManager.clearFocus()
        keyboardController?.hide()
    }
}

private data class DagTab(
    val id: String = UUID.randomUUID().toString(),
    val snapshot: DagTabSnapshot,
)

private fun DagTabSnapshot.tabLabel(position: Int): String =
    when (view) {
        DagView.Browser -> DagContentClassifier.domainFrom(requestedUrl.orEmpty()).ifBlank { "Pestaña $position" }
        DagView.Results -> address.ifBlank { "Resultados" }
        DagView.History -> "Historial"
        DagView.Start -> "Nueva pestaña"
    }

@Composable
private fun DagStartContent() {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        PremiumFishMascot(modifier = Modifier.width(150.dp).height(120.dp))
        Spacer(Modifier.height(12.dp))
        Text("DAG", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Buscá y navegá con protección local.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DagResultsContent(
    results: List<DagSearchResult>,
    onOpen: (DagSearchResult) -> Unit,
    onReview: (DagReviewCandidate) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(results, key = { it.url }) { result ->
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = result.classification.decision == DagClassification.Allowed) {
                            onOpen(result)
                        }
                        .padding(horizontal = 12.dp, vertical = 9.dp),
            ) {
                Text(
                    result.domain,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    result.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (result.description.isNotBlank()) {
                    Text(
                        result.description,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (result.classification.decision == DagClassification.Uncertain) {
                    TextButton(
                        onClick = {
                            onReview(
                                DagReviewCandidate(
                                    url = result.url,
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
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
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
    onPageTextReady: (String, String, String?, DagImagePageSummary) -> Unit,
    onBlockedAction: (String) -> Unit,
    onPageBlocked: (String) -> Unit,
    onWebViewChanged: (WebView?) -> Unit,
    onNavigationStateChanged: (Boolean, Boolean) -> Unit,
) {
    val context = LocalContext.current
    val imageClassifier = remember(context) { DagImageClassifier(context) }
    val imageLoader = remember(imageClassifier) { DagImageResourceLoader(imageClassifier) }
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
            onWebViewChanged(null)
            imageClassifier.close()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize().alpha(if (state.pageStatus == DagPageStatus.Visible) 1f else 0f),
            factory = {
                WebView(context).apply {
                    webView = this
                    onWebViewChanged(this)
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
                                imageLoader.resetPage()
                                onPageStarted(url)
                            },
                            onFinished = { view, url ->
                                canGoBack = view.canGoBack()
                                canGoForward = view.canGoForward()
                                onNavigationStateChanged(canGoBack, canGoForward)
                                if (inspectedUrl != url) {
                                    inspectedUrl = url
                                    view.sanitizeAndExtractVisibleText { text ->
                                        onPageTextReady(url, view.title.orEmpty(), text, imageLoader.pageSummary())
                                    }
                                }
                            },
                            onBlocked = onPageBlocked,
                            imageLoader = imageLoader,
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
    val supportsDocumentStartScript = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
    settings.javaScriptEnabled = true
    if (supportsDocumentStartScript) {
        WebViewCompat.addDocumentStartJavaScript(
            this,
            DagDocumentStartScript,
            setOf("*"),
        )
    }
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
    settings.safeBrowsingEnabled = true
}

private val DagDocumentStartScript =
    """
    (function() {
      try {
        if (window.URL && window.URL.createObjectURL) {
          Object.defineProperty(window.URL, 'createObjectURL', {
            value: function() { throw new Error('DAG blocked blob content'); },
            writable: false,
            configurable: false
          });
        }
        if (navigator.serviceWorker) {
          var worker = navigator.serviceWorker;
          Object.defineProperty(worker, 'register', {
            value: function() { return Promise.reject(new Error('DAG blocked service workers')); },
            writable: false,
            configurable: false
          });
          if (worker.controller) {
            window.stop();
            worker.getRegistrations().then(function(registrations) {
              return Promise.all(registrations.map(function(registration) { return registration.unregister(); }));
            }).finally(function() { location.reload(); });
          }
        }
      } catch (_) {}
    })();
    """.trimIndent()

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
    private val imageLoader: DagImageResourceLoader,
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
    ): WebResourceResponse? = imageLoader.intercept(request, view?.url)
}

private fun WebView.sanitizeAndExtractVisibleText(callback: (String?) -> Unit) {
    evaluateJavascript(
        """
        (function() {
          function dagHttpsImageSource(image) {
            var candidates = [
              image.getAttribute('data-src'),
              image.getAttribute('data-lazy-src'),
              image.getAttribute('data-original'),
              image.getAttribute('data-lazy'),
              image.getAttribute('data-url'),
              image.getAttribute('data-image'),
              image.getAttribute('data-hi-res-src'),
              image.getAttribute('data-src-large'),
              image.getAttribute('data-flickity-lazyload'),
              image.getAttribute('data-srcset'),
              image.getAttribute('src'),
              image.currentSrc
            ];
            for (var index = 0; index < candidates.length; index++) {
              var raw = candidates[index] || '';
              var firstCandidate = raw.split(',')[0].trim().split(/\s+/)[0];
              if (!firstCandidate) continue;
              try {
                var resolved = new URL(firstCandidate, document.baseURI);
                if (resolved.protocol === 'https:') return resolved.href;
              } catch (_) {}
            }
            return '';
          }
          function dagSecureBackground(node) {
            if (!node || node.nodeType !== 1) return;
            var background = '';
            try { background = window.getComputedStyle(node).backgroundImage || ''; } catch (_) {}
            if (!background || background === 'none') return;
            var matches = background.matchAll(/url\(["']?([^"')]+)["']?\)/g);
            for (var match of matches) {
              try {
                if (new URL(match[1], document.baseURI).protocol !== 'https:') {
                  node.style.setProperty('background-image', 'none', 'important');
                  return;
                }
              } catch (_) {
                node.style.setProperty('background-image', 'none', 'important');
                return;
              }
            }
          }
          function dagSecureNode(node) {
            if (!node || node.nodeType !== 1) return;
            var tag = node.tagName ? node.tagName.toLowerCase() : '';
            if (tag === 'video' || tag === 'audio' || tag === 'canvas' || tag === 'iframe') {
              node.remove();
              return;
            }
            if (tag === 'source') {
              var parentTag = node.parentElement && node.parentElement.tagName ? node.parentElement.tagName.toLowerCase() : '';
              if (parentTag === 'video' || parentTag === 'audio') node.remove();
              return;
            }
            if (tag === 'img') {
              var source = dagHttpsImageSource(node);
              if (!source) {
                node.remove();
                return;
              }
              node.removeAttribute('srcset');
              node.removeAttribute('data-srcset');
              if (node.getAttribute('src') !== source) node.src = source;
            }
            dagSecureBackground(node);
            node.querySelectorAll && node.querySelectorAll('video,audio,video source,audio source,canvas,iframe').forEach(function(child) { child.remove(); });
            node.querySelectorAll && node.querySelectorAll('img').forEach(function(image) {
              dagSecureNode(image);
            });
            node.querySelectorAll && node.querySelectorAll('*').forEach(dagSecureBackground);
          }
          dagSecureNode(document.documentElement);
          var style = document.getElementById('dag-safe-style');
          if (!style) {
            style = document.createElement('style');
            style.id = 'dag-safe-style';
            style.textContent = 'video,audio,canvas,iframe { display:none !important; }';
            document.documentElement.appendChild(style);
          }
          if (!window.__dagSafeObserver) {
            window.__dagSafeObserver = new MutationObserver(function(records) {
              records.forEach(function(record) {
                record.addedNodes.forEach(dagSecureNode);
                if (record.type === 'attributes') dagSecureNode(record.target);
              });
            });
            window.__dagSafeObserver.observe(document.documentElement, {
              childList: true,
              subtree: true,
              attributes: true,
              attributeFilter: ['src', 'srcset', 'style', 'class']
            });
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
