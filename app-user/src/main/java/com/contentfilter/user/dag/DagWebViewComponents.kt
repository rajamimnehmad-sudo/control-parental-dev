package com.contentfilter.user.dag

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
import android.webkit.WebResourceResponse
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.contentfilter.user.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicLong

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun DagWebContent(
    state: DagBrowserUiState,
    onBackFromBrowser: () -> Unit,
    onNavigate: (String, String) -> Unit,
    onPageStarted: (String) -> Boolean,
    onPageTextReady: (String, String, String?, DagImagePageSummary) -> Unit,
    onViewportImagesReady: (String) -> Unit,
    onViewportImageProgress: (String, Int, Int) -> Unit,
    onCalibrationCandidate: (ByteArray, DagImageClassification) -> Unit,
    visualCalibrationEnabled: Boolean,
    onManualCalibrationCandidate: (ByteArray, DagImageClassification) -> Unit,
    onManualBlurReviewCandidate: (ByteArray, DagImageClassification) -> Unit,
    onBlockedAction: (String) -> Unit,
    onGeolocationPrompt: (String, (Boolean) -> Unit) -> Unit,
    onFaviconChanged: (String, Bitmap) -> Unit,
    onPageBlocked: (String) -> Unit,
    onRendererGone: () -> Unit,
    onWebViewChanged: (WebView?) -> Unit,
    onNavigationStateChanged: (Boolean, Boolean) -> Unit,
) {
    val context = LocalContext.current
    val imageClassifiers =
        remember(context) {
            List(DagImageDeliveryPolicy.MaximumConcurrentClassifications) { DagImageClassifier(context) }
        }
    val currentCalibrationCandidate by rememberUpdatedState(onCalibrationCandidate)
    val currentManualCalibrationCandidate by rememberUpdatedState(onManualCalibrationCandidate)
    val currentManualBlurReviewCandidate by rememberUpdatedState(onManualBlurReviewCandidate)
    val currentVisualCalibrationEnabled by rememberUpdatedState(visualCalibrationEnabled)
    val currentExtraKosherEnabled by rememberUpdatedState(state.dagExtraKosherEnabled)
    val currentGeolocationPrompt by rememberUpdatedState(onGeolocationPrompt)
    val currentFaviconChanged by rememberUpdatedState(onFaviconChanged)
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    val imageLoader =
        remember(imageClassifiers) {
            DagImageResourceLoader(
                classifiers = imageClassifiers,
                onCalibrationCandidate = { image, classification ->
                    currentCalibrationCandidate(image, classification)
                },
                onManualCandidateReady = { imageUrl, decision ->
                    mainHandler.post {
                        if (currentVisualCalibrationEnabled) {
                            webView?.setDagImageCalibrationDecision(imageUrl, decision)
                        }
                    }
                },
            )
        }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var inspectedUrl by remember { mutableStateOf<String?>(null) }
    var preparedUrl by remember { mutableStateOf<String?>(null) }
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
    LaunchedEffect(imageClassifiers) {
        withContext(Dispatchers.Default) {
            imageClassifiers.map { classifier -> async { classifier.prepare() } }.awaitAll()
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
                view.settings.blockNetworkImage = true
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
    LaunchedEffect(webView, visualCalibrationEnabled) {
        val view = webView
        if (imageLoader.setDevCalibrationRevealEnabled(visualCalibrationEnabled)) {
            view?.setDagVisualCalibrationMode(visualCalibrationEnabled)
            view?.setDagImageCalibrationDecisions(imageLoader.manualCalibrationDecisions())
        } else if (state.pageStatus == DagPageStatus.Visible) {
            view?.setDagVisualCalibrationMode(visualCalibrationEnabled)
        }
    }
    LaunchedEffect(webView, state.dagExtraKosherEnabled) {
        val view = webView ?: return@LaunchedEffect
        view.url?.takeIf { it.startsWith("https://", ignoreCase = true) }?.let {
            view.settings.blockNetworkImage = true
            view.reload()
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            pendingFavicon?.second?.recycle()
            pendingFavicon = null
            webView?.stopLoading()
            webView?.destroy()
            webView = null
            onWebViewChanged(null)
            imageLoader.cancel()
            imageClassifiers.forEach(DagImageClassifier::close)
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
                    val cookieManager = CookieManager.getInstance()
                    var captchaSessionRevision = 0
                    val captchaSessionExpiresAt = AtomicLong(0L)

                    fun closeCaptchaSession() {
                        captchaSessionRevision += 1
                        captchaSessionExpiresAt.set(0L)
                        cookieManager.setAcceptThirdPartyCookies(dagWebView, false)
                    }

                    configureDagSettings(
                        calibrationDecision = imageLoader::manualCalibrationDecision,
                        onManualImageReported = { action, imageUrl ->
                            imageLoader.takeManualCalibrationCandidate(imageUrl)?.let { candidate ->
                                when (action) {
                                    DagCalibrationActionBlock ->
                                        currentManualCalibrationCandidate(candidate.thumbnail, candidate.classification)
                                    DagCalibrationActionAllow ->
                                        currentManualBlurReviewCandidate(
                                            candidate.thumbnail,
                                            candidate.classification,
                                        )
                                }
                            }
                        },
                        onCaptchaDetected = { _ ->
                            captchaSessionRevision += 1
                            val sessionRevision = captchaSessionRevision
                            captchaSessionExpiresAt.set(
                                SystemClock.elapsedRealtime() + DagCaptchaSessionDurationMillis,
                            )
                            cookieManager.setAcceptThirdPartyCookies(dagWebView, true)
                            dagWebView.restartDagCaptchaFramesForSession()
                            dagWebView.postDelayed(
                                {
                                    if (captchaSessionRevision == sessionRevision) closeCaptchaSession()
                                },
                                DagCaptchaSessionDurationMillis,
                            )
                        },
                    )
                    setBackgroundColor(android.graphics.Color.WHITE)
                    cookieManager.apply {
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

                    fun prepareViewport(
                        view: WebView,
                        url: String,
                    ) {
                        if (preparedUrl == url) return
                        preparedUrl = url
                        view.sanitizeAndExtractVisibleText(extraKosherEnabled = currentExtraKosherEnabled) {
                            view.setDagVisualCalibrationMode(currentVisualCalibrationEnabled)
                            view.setDagImageCalibrationDecisions(imageLoader.manualCalibrationDecisions())
                            view.awaitDagViewportImages(
                                onProgress = { resolved, total -> onViewportImageProgress(url, resolved, total) },
                            ) {
                                view.postDelayed(
                                    {
                                        if (preparedUrl == url) onViewportImagesReady(url)
                                    },
                                    DagViewportReadinessPolicy.VisualSettleMillis,
                                )
                            }
                        }
                    }

                    fun inspectPage(
                        view: WebView,
                        url: String,
                    ) {
                        if (inspectedUrl == url) return
                        inspectedUrl = url
                        prepareViewport(view, url)
                        view.sanitizeAndExtractVisibleText(extraKosherEnabled = currentExtraKosherEnabled) { text ->
                            onPageTextReady(url, view.title.orEmpty(), text, imageLoader.pageSummary())
                        }
                    }
                    webViewClient =
                        DagWebViewClient(
                            onNavigate = onNavigate,
                            onStarted = { url ->
                                closeCaptchaSession()
                                settings.blockNetworkImage = true
                                inspectedUrl = null
                                preparedUrl = null
                                imageLoader.resetPage()
                                onPageStarted(url)
                            },
                            onCommitted = { view, url ->
                                prepareViewport(view, url)
                                view.postDelayed(
                                    {
                                        if (view.url == url && inspectedUrl != url) inspectPage(view, url)
                                    },
                                    PageCommitInspectionDelayMillis,
                                )
                            },
                            onFinished = { view, url ->
                                canGoBack = view.canGoBack()
                                canGoForward = view.canGoForward()
                                onNavigationStateChanged(canGoBack, canGoForward)
                                inspectPage(view, url)
                                view.setDagVisualCalibrationMode(currentVisualCalibrationEnabled)
                                view.setDagImageCalibrationDecisions(imageLoader.manualCalibrationDecisions())
                            },
                            onBlocked = onPageBlocked,
                            onRendererGone = { failedView ->
                                if (webView === failedView) {
                                    webView = null
                                    onWebViewChanged(null)
                                    onRendererGone()
                                }
                            },
                            imageLoader = imageLoader,
                            captchaSessionActive = {
                                SystemClock.elapsedRealtime() < captchaSessionExpiresAt.get()
                            },
                        )
                    setDownloadListener { _, _, _, _, _ -> onBlockedAction("Las descargas están bloqueadas en DAG.") }
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
private fun WebView.configureDagSettings(
    calibrationDecision: (String) -> DagImageDecision?,
    onManualImageReported: (String, String) -> Unit,
    onCaptchaDetected: (String) -> Unit,
) {
    val supportsDocumentStartScript = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
    settings.javaScriptEnabled = true
    if (supportsDocumentStartScript) {
        WebViewCompat.addDocumentStartJavaScript(
            this,
            DagDocumentStartScript,
            setOf("*"),
        )
    }
    if (
        BuildConfig.DAG_VISUAL_CALIBRATION_AVAILABLE &&
        WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)
    ) {
        WebViewCompat.addWebMessageListener(
            this,
            DagCalibrationBridgeName,
            setOf("*"),
        ) { view, message, sourceOrigin, isMainFrame, _ ->
            if (!isMainFrame || sourceOrigin.scheme != "https") return@addWebMessageListener
            val data = message.data ?: return@addWebMessageListener
            val payload = runCatching { org.json.JSONObject(data) }.getOrNull() ?: return@addWebMessageListener
            val action = payload.optString("action")
            val imageUrl = payload.optString("url").takeIf { it.startsWith("https://") } ?: return@addWebMessageListener
            if (action == DagCalibrationActionProbe) {
                calibrationDecision(imageUrl)?.let { view.setDagImageCalibrationDecision(imageUrl, it) }
                return@addWebMessageListener
            }
            if (action == DagCalibrationActionBlock || action == DagCalibrationActionAllow) {
                onManualImageReported(action, imageUrl)
            }
        }
    }
    if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
        WebViewCompat.addWebMessageListener(
            this,
            DagCaptchaBridgeName,
            setOf("*"),
        ) { _, message, sourceOrigin, isMainFrame, _ ->
            if (!isMainFrame || sourceOrigin.scheme != "https") return@addWebMessageListener
            val payload = runCatching { org.json.JSONObject(message.data.orEmpty()) }.getOrNull()
            val captchaUrl = payload?.optString("url").orEmpty()
            if (payload?.optString("action") == DagCaptchaSessionStart && isDagCaptchaProviderUrl(captchaUrl)) {
                onCaptchaDetected(captchaUrl)
            }
        }
    }
    settings.domStorageEnabled = true
    settings.databaseEnabled = false
    settings.allowFileAccess = false
    settings.allowContentAccess = false
    settings.loadsImagesAutomatically = true
    settings.blockNetworkImage = true
    settings.mediaPlaybackRequiresUserGesture = true
    settings.javaScriptCanOpenWindowsAutomatically = false
    settings.setSupportMultipleWindows(false)
    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
    settings.cacheMode = WebSettings.LOAD_DEFAULT
    settings.safeBrowsingEnabled = true
    settings.setGeolocationEnabled(true)
}

private fun WebView.setDagImageCalibrationDecision(
    imageUrl: String,
    decision: DagImageDecision,
) {
    evaluateJavascript(
        "(function(){if(window.__dagSetImageCalibrationDecision){window.__dagSetImageCalibrationDecision(" +
            org.json.JSONObject.quote(imageUrl) + "," +
            org.json.JSONObject.quote(decision.name.lowercase()) + ");}})();",
        null,
    )
}

private fun WebView.restartDagCaptchaFramesForSession() {
    evaluateJavascript(
        """
        (function() {
          document.querySelectorAll('iframe[data-dag-safe-captcha="true"]').forEach(function(frame) {
            if (frame.getAttribute('data-dag-captcha-reloaded') === 'true') return;
            var source = frame.getAttribute('src') || '';
            if (source.indexOf('https://') !== 0) return;
            frame.setAttribute('data-dag-captcha-reloaded', 'true');
            frame.setAttribute('src', source);
          });
        })();
        """.trimIndent(),
        null,
    )
}

private fun WebView.setDagImageCalibrationDecisions(decisions: Map<String, DagImageDecision>) {
    if (decisions.isEmpty()) return
    val payload =
        JSONArray().apply {
            decisions.forEach { (imageUrl, decision) ->
                put(JSONArray().put(imageUrl).put(decision.name.lowercase()))
            }
        }
    evaluateJavascript(
        "(function(items){if(!window.__dagSetImageCalibrationDecision){return;}" +
            "items.forEach(function(item){window.__dagSetImageCalibrationDecision(item[0],item[1]);});})(" +
            payload + ");",
        null,
    )
}

private fun WebView.setDagVisualCalibrationMode(enabled: Boolean) {
    evaluateJavascript(
        "(function(){if(window.__dagSetVisualCalibrationMode){window.__dagSetVisualCalibrationMode(" +
            enabled + ");}})();",
        null,
    )
}

private val DagDocumentStartScript =
    """
    (function() {
      try {
        var dagDocumentHost = (window.location.hostname || '').toLowerCase();
        var dagDocumentPath = window.location.pathname || '';
        var dagCaptchaDocument =
          ((dagDocumentHost === 'www.google.com' || dagDocumentHost === 'www.recaptcha.net') &&
            dagDocumentPath.indexOf('/recaptcha/') === 0) ||
          (dagDocumentHost === 'challenges.cloudflare.com' &&
            dagDocumentPath.indexOf('/cdn-cgi/challenge-platform/') === 0) ||
          (dagDocumentHost === 'newassets.hcaptcha.com' &&
            dagDocumentPath.indexOf('/captcha/') === 0);
        if (dagCaptchaDocument) return;
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
    private val onCommitted: (WebView, String) -> Unit,
    private val onFinished: (WebView, String) -> Unit,
    private val onBlocked: (String) -> Unit,
    private val onRendererGone: (WebView) -> Unit,
    private val imageLoader: DagImageResourceLoader,
    private val captchaSessionActive: () -> Boolean,
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
        pageUrlTracker.update(url)
        if (!onStarted(url)) view.stopLoading()
    }

    override fun onPageFinished(
        view: WebView,
        url: String,
    ) {
        if (url.startsWith("https://")) onFinished(view, url)
    }

    override fun onPageCommitVisible(
        view: WebView,
        url: String,
    ) {
        if (url.startsWith("https://")) onCommitted(view, url)
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

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest,
    ): WebResourceResponse? {
        if (captchaSessionActive() && isDagCaptchaSessionResourceUrl(request.url.toString())) return null
        if (dagShouldBlockAdRequest(request.url.toString(), request.isForMainFrame)) {
            return WebResourceResponse(
                "text/plain",
                "utf-8",
                204,
                "No Content",
                emptyMap(),
                ByteArrayInputStream(ByteArray(0)),
            )
        }
        return imageLoader.intercept(request, pageUrlTracker.current())
    }
}

internal class DagPageUrlTracker {
    @Volatile
    private var pageUrl: String? = null

    fun update(url: String) {
        pageUrl = url
    }

    fun current(): String? = pageUrl
}

private fun WebView.sanitizeAndExtractVisibleText(
    extraKosherEnabled: Boolean,
    attempt: Int = 0,
    callback: (String?) -> Unit,
) {
    evaluateJavascript(
        """
        (function() {
          window.__dagExtraKosherEnabled = ${if (extraKosherEnabled) "true" else "false"};
          var dagIntimatePattern = /$DagIntimateImageJavaScriptPattern/i;
          function dagNormalizedText(value) {
            return (value || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();
          }
          function dagImageContext(image) {
            var container = image.closest && image.closest('[data-product],article,.product,.product-card,.product-item,[class*="product-card"],[class*="product-item"],[class*="product-summary"],[class*="productSummary"]');
            var containerText = container && (container.innerText || '');
            if (containerText && containerText.length > 800) containerText = '';
            var productLink = image.closest && image.closest('a[href]');
            return dagNormalizedText([
              image.getAttribute('alt'), image.getAttribute('title'), image.getAttribute('aria-label'),
              image.getAttribute('src'), image.getAttribute('data-src'), image.currentSrc,
              productLink && productLink.getAttribute('href'),
              (containerText || '').substring(0, 400)
            ].join(' '));
          }
          function dagFemaleIntimatePage() {
            var route = dagNormalizedText(location.pathname.replace(/[-_]+/g, ' '));
            return /\b(mujer(es)?|women|woman|dama(s)?|female)\b/.test(route) &&
              /\b(ropa\s+(interior|intima)|underwear|lingerie|lenceria|intimates|corpin(es|os)?|bombacha(s)?|bralette(s)?|pant(y|ies))\b/.test(route);
          }
          function dagIsContentImage(image) {
            if (image.closest && image.closest('header,nav,[role="navigation"]')) return false;
            if (image.closest && image.closest('button,[role="button"],input,label')) return false;
            var marker = dagNormalizedText([image.className, image.id, image.getAttribute('alt')].join(' '));
            return !/\b(logo|icon|sprite|flag|payment)\b/.test(marker);
          }
          function dagApplyExtraKosherImage(image) {
            if (!image) return false;
            var shouldBlur = window.__dagExtraKosherEnabled === true && dagIsContentImage(image);
            if (shouldBlur) {
              image.setAttribute('data-dag-extra-kosher-blurred', 'true');
            } else {
              image.removeAttribute('data-dag-extra-kosher-blurred');
            }
            return shouldBlur;
          }
          function dagBlurIntimateImage(image) {
            var sensitive = dagIntimatePattern.test(dagImageContext(image));
            if (!sensitive && dagFemaleIntimatePage() && dagIsContentImage(image)) sensitive = true;
            if (!sensitive) return false;
            image.setAttribute('data-dag-kosher-blurred', 'true');
            return true;
          }
          function dagHttpsImageSource(image) {
            var pictureSource = image.closest && image.closest('picture');
            pictureSource = pictureSource && pictureSource.querySelector('source[data-srcset],source[data-lazy-srcset],source[srcset]');
            var candidates = [
              image.getAttribute('data-dag-held-src'),
              image.getAttribute('data-src'),
              image.getAttribute('data-lazy-src'),
              image.getAttribute('data-lazy-srcset'),
              image.getAttribute('data-original'),
              image.getAttribute('data-original-set'),
              image.getAttribute('data-lazy'),
              image.getAttribute('data-url'),
              image.getAttribute('data-image'),
              image.getAttribute('data-hi-res-src'),
              image.getAttribute('data-src-large'),
              image.getAttribute('data-flickity-lazyload'),
              image.getAttribute('data-srcset'),
              pictureSource && pictureSource.getAttribute('data-srcset'),
              pictureSource && pictureSource.getAttribute('data-lazy-srcset'),
              pictureSource && pictureSource.getAttribute('srcset'),
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
          function dagLoadImage(image) {
            if (!image || !image.isConnected) return;
            dagBlurIntimateImage(image);
            dagApplyExtraKosherImage(image);
            var source = dagHttpsImageSource(image);
            if (!source) {
              image.style.setProperty('visibility', 'hidden', 'important');
              image.setAttribute('data-dag-image-terminal', 'unavailable');
              return;
            }
            image.removeAttribute('srcset');
            image.removeAttribute('data-srcset');
            image.removeAttribute('data-lazy-srcset');
            image.removeAttribute('data-dag-held-src');
            image.style.removeProperty('visibility');
            if (image.getAttribute('src') !== source) image.src = source;
            image.setAttribute('data-dag-image-ready', 'true');
            if (window.__dagVisualCalibrationEnabled) window.__dagRefreshCalibrationMarkers();
          }
          function dagQueueImage(image) {
            if (!image) return;
            dagBlurIntimateImage(image);
            dagApplyExtraKosherImage(image);
            if (window.IntersectionObserver && image.getAttribute('data-dag-image-ready') !== 'true') {
              var heldSource = dagHttpsImageSource(image);
              if (!heldSource) {
                image.style.setProperty('visibility', 'hidden', 'important');
                image.setAttribute('data-dag-image-terminal', 'unavailable');
                return;
              }
              var heldRect;
              try { heldRect = image.getBoundingClientRect(); } catch (_) {}
              var declaredWidth = parseFloat(image.getAttribute('width') || '0');
              var declaredHeight = parseFloat(image.getAttribute('height') || '0');
              if (declaredWidth > 0 && declaredHeight > 0) {
                image.style.setProperty('aspect-ratio', declaredWidth + ' / ' + declaredHeight);
              } else if (heldRect && heldRect.width >= 24 && heldRect.height >= 24) {
                image.style.setProperty('aspect-ratio', heldRect.width + ' / ' + heldRect.height);
              }
              image.setAttribute('data-dag-held-src', heldSource);
              image.removeAttribute('src');
              image.removeAttribute('srcset');
              var heldPicture = image.closest && image.closest('picture');
              heldPicture && heldPicture.querySelectorAll('source').forEach(function(source) {
                source.removeAttribute('srcset');
                source.removeAttribute('data-srcset');
                source.removeAttribute('data-lazy-srcset');
              });
              if (!window.__dagImageObserver) {
                var dagPrefetchMargin = Math.max(
                  (window.innerHeight || 0) * ${DagViewportReadinessPolicy.PrefetchViewportCount},
                  600
                );
                window.__dagImageObserver = new IntersectionObserver(function(entries) {
                  entries.forEach(function(entry) {
                    if (!entry.isIntersecting) return;
                    window.__dagImageObserver.unobserve(entry.target);
                    dagLoadImage(entry.target);
                  });
                }, { rootMargin: dagPrefetchMargin + 'px 0px' });
              }
              window.__dagImageObserver.observe(image);
              return;
            }
            dagLoadImage(image);
          }
          window.__dagPrepareViewportImages = function(hidePending) {
            var viewportHeight = Math.max(window.innerHeight || 0, 1);
            var preparationLimit = viewportHeight * ${DagViewportReadinessPolicy.PreparedViewportCount};
            var total = 0;
            var pending = 0;
            document.querySelectorAll('img').forEach(function(image) {
              if (!image || !image.isConnected) return;
              var rect;
              try { rect = image.getBoundingClientRect(); } catch (_) { return; }
              if (rect.bottom < 0 || rect.top > preparationLimit) return;
              var style;
              try { style = window.getComputedStyle(image); } catch (_) { return; }
              if (style.display === 'none' || rect.width < 24 || rect.height < 24) {
                return;
              }
              if (rect.top > preparationLimit) {
                var heldSource = dagHttpsImageSource(image);
                if (heldSource) {
                  image.setAttribute('data-dag-held-src', heldSource);
                  image.removeAttribute('src');
                  image.removeAttribute('srcset');
                  var picture = image.closest && image.closest('picture');
                  picture && picture.querySelectorAll('source').forEach(function(source) {
                    source.removeAttribute('srcset');
                    source.removeAttribute('data-srcset');
                    source.removeAttribute('data-lazy-srcset');
                  });
                  image.removeAttribute('data-dag-image-ready');
                  dagQueueImage(image);
                }
                return;
              }
              var visibleNow = rect.top <= viewportHeight && rect.bottom >= 0;
              dagLoadImage(image);
              if (!visibleNow) return;
              total += 1;
              if (image.hasAttribute('data-dag-image-terminal')) return;
              if (image.complete && image.naturalWidth > 0) return;
              if (image.complete && image.naturalWidth === 0) {
                if (!hidePending) {
                  pending += 1;
                  return;
                }
                image.style.setProperty('visibility', 'hidden', 'important');
                image.setAttribute('data-dag-image-terminal', 'unavailable');
                return;
              }
              if (hidePending) {
                image.style.setProperty('visibility', 'hidden', 'important');
                image.setAttribute('data-dag-image-terminal', 'timeout');
                return;
              }
              pending += 1;
            });
            return JSON.stringify({ total: total, pending: pending });
          };
          function dagSecureBackground(node) {
            if (!node || node.nodeType !== 1) return;
            var background = '';
            try { background = window.getComputedStyle(node).backgroundImage || ''; } catch (_) {}
            if (!background || background === 'none') return;
            if (window.__dagExtraKosherEnabled === true &&
                !(node.closest && node.closest('header,nav,button,[role="button"],[role="navigation"]')) &&
                !/\b(logo|icon|sprite|flag|payment)\b/.test(dagNormalizedText([node.className, node.id].join(' ')))) {
              node.style.setProperty('background-image', 'none', 'important');
              node.setAttribute('data-dag-extra-kosher-background', 'true');
              return;
            }
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
          function dagAllowedCaptchaFrame(frame) {
            if (!frame || !window.location || window.location.protocol !== 'https:') return false;
            try {
              var rawSource = frame.getAttribute('src') || '';
              if (!rawSource || rawSource === 'about:blank') return false;
              var source = new URL(rawSource, document.baseURI);
              var provider = source.hostname.toLowerCase();
              if (source.protocol !== 'https:') return false;
              var allowed =
                ((provider === 'www.google.com' || provider === 'www.recaptcha.net') &&
                  source.pathname.indexOf('/recaptcha/') === 0) ||
                (provider === 'challenges.cloudflare.com' &&
                  source.pathname.indexOf('/cdn-cgi/challenge-platform/') === 0) ||
                (provider === 'newassets.hcaptcha.com' &&
                  source.pathname.indexOf('/captcha/') === 0);
              if (allowed && window.$DagCaptchaBridgeName &&
                  window.__dagCaptchaSessionRequested !== true) {
                window.__dagCaptchaSessionRequested = true;
                try {
                  window.$DagCaptchaBridgeName.postMessage(JSON.stringify({
                    action: '$DagCaptchaSessionStart',
                    url: source.href
                  }));
                } catch (_) {}
              }
              return allowed;
            } catch (_) {
              return false;
            }
          }
          function dagDeferIncompleteCaptchaFrame(frame) {
            if (!frame || frame.getAttribute('data-dag-captcha-deferred') === 'true') return false;
            var rawSource = frame.getAttribute('src') || '';
            if (rawSource && rawSource !== 'about:blank') return false;
            frame.setAttribute('data-dag-captcha-deferred', 'true');
            window.requestAnimationFrame(function() {
              if (!frame.isConnected) return;
              dagSecureNode(frame, false);
            });
            return true;
          }
          function dagSecureNode(node, includeDescendants) {
            if (!node || node.nodeType !== 1) return;
            var tag = node.tagName ? node.tagName.toLowerCase() : '';
            if (tag === 'iframe') {
              if (dagAllowedCaptchaFrame(node)) {
                if (node.getAttribute('data-dag-safe-captcha') !== 'true') {
                  node.setAttribute('data-dag-safe-captcha', 'true');
                }
                return;
              }
              if (dagDeferIncompleteCaptchaFrame(node)) return;
              node.remove();
              return;
            }
            if (tag === 'video' || tag === 'audio' || tag === 'canvas') {
              node.remove();
              return;
            }
            if (tag === 'source') {
              var parentTag = node.parentElement && node.parentElement.tagName ? node.parentElement.tagName.toLowerCase() : '';
              if (parentTag === 'video' || parentTag === 'audio') node.remove();
              return;
            }
            if (tag === 'img') {
              dagQueueImage(node);
            }
            dagSecureBackground(node);
            if (includeDescendants !== true) return;
            node.querySelectorAll && node.querySelectorAll('video,audio,video source,audio source,canvas,iframe').forEach(dagSecureNode);
            node.querySelectorAll && node.querySelectorAll('img').forEach(function(image) {
              dagQueueImage(image);
            });
            node.querySelectorAll && node.querySelectorAll('*').forEach(dagSecureBackground);
          }
          var dagSecurityQueue = [];
          var dagSecurityQueued = new WeakSet();
          var dagSecurityFlushScheduled = false;
          function dagScheduleSecurityFlush() {
            if (dagSecurityFlushScheduled || !dagSecurityQueue.length) return;
            dagSecurityFlushScheduled = true;
            window.requestAnimationFrame(function() {
              dagSecurityFlushScheduled = false;
              var remaining = ${DagDomSecurityBatchPolicy.MaximumNodesPerFrame};
              while (remaining > 0 && dagSecurityQueue.length) {
                var current = dagSecurityQueue.shift();
                remaining -= 1;
                if (!current || !current.isConnected) continue;
                dagSecureNode(current, false);
                if (!current.isConnected) continue;
                var child = current.firstElementChild;
                while (child) {
                  dagQueueSecurityNode(child);
                  child = child.nextElementSibling;
                }
              }
              dagScheduleSecurityFlush();
            });
          }
          function dagQueueSecurityNode(node) {
            if (!node || node.nodeType !== 1 || dagSecurityQueued.has(node)) return;
            dagSecurityQueued.add(node);
            dagSecurityQueue.push(node);
            dagScheduleSecurityFlush();
          }
          function dagRemoveCalibrationMarkers() {
            document.querySelectorAll('[data-dag-calibration-marker="true"]').forEach(function(marker) {
              marker.remove();
            });
          }
          window.__dagImageCalibrationDecisions = window.__dagImageCalibrationDecisions || Object.create(null);
          window.__dagImageCalibrationProbes = window.__dagImageCalibrationProbes || Object.create(null);
          function dagCalibrationKey(source) {
            try { return new URL(source || '', document.baseURI).href.split('#')[0]; } catch (_) { return source || ''; }
          }
          window.__dagSetImageCalibrationDecision = function(source, decision) {
            if (!source || !decision) return;
            var key = dagCalibrationKey(source);
            window.__dagImageCalibrationDecisions[key] = decision;
            delete window.__dagImageCalibrationProbes[key];
            if (window.__dagVisualCalibrationEnabled) window.requestAnimationFrame(window.__dagRefreshCalibrationMarkers);
          };
          window.__dagRefreshCalibrationMarkers = function() {
            dagRemoveCalibrationMarkers();
            if (!window.__dagVisualCalibrationEnabled || !document.body || !window.$DagCalibrationBridgeName) return;
            document.querySelectorAll('img').forEach(function(image) {
              if (!image || !image.isConnected || image.getAttribute('data-dag-manually-blocked') === 'true') return;
              var rect;
              try { rect = image.getBoundingClientRect(); } catch (_) { return; }
              if (rect.width < 48 || rect.height < 48 || rect.bottom < 0 || rect.top > window.innerHeight) return;
              var source = dagHttpsImageSource(image);
              if (!source) return;
              var sourceKey = dagCalibrationKey(source);
              var decision = window.__dagImageCalibrationDecisions[sourceKey];
              if (!decision) {
                if (!window.__dagImageCalibrationProbes[sourceKey]) {
                  window.__dagImageCalibrationProbes[sourceKey] = true;
                  try {
                    window.$DagCalibrationBridgeName.postMessage(JSON.stringify({ action: 'probe', url: source }));
                  } catch (_) {}
                  window.setTimeout(function() {
                    delete window.__dagImageCalibrationProbes[sourceKey];
                    if (window.__dagVisualCalibrationEnabled) window.requestAnimationFrame(window.__dagRefreshCalibrationMarkers);
                  }, 500);
                }
                return;
              }
              var marker = document.createElement('div');
              marker.setAttribute('data-dag-calibration-marker', 'true');
              marker.style.cssText = 'position:fixed;z-index:2147483647;display:flex;gap:4px;padding:3px;border-radius:20px;background:rgba(15,23,42,.82);box-shadow:0 2px 7px rgba(0,0,0,.55);';
              marker.style.left = Math.max(4, rect.right - 78) + 'px';
              marker.style.top = Math.max(4, rect.top + 4) + 'px';
              function addCalibrationButton(label, action, color, ariaLabel) {
                var button = document.createElement('button');
                button.type = 'button';
                button.setAttribute('aria-label', ariaLabel);
                button.textContent = label;
                button.style.cssText = 'width:32px;height:32px;border:2px solid white;border-radius:16px;background:' + color + ';color:white;font:700 18px/27px sans-serif;padding:0;';
                button.addEventListener('click', function(event) {
                  event.preventDefault();
                  event.stopPropagation();
                  marker.remove();
                  try {
                    window.$DagCalibrationBridgeName.postMessage(JSON.stringify({ action: action, url: source }));
                  } catch (_) {}
                }, true);
                marker.appendChild(button);
              }
              addCalibrationButton('✓', 'allow', '#087f5b', 'Marcar foto como permitida para calibración');
              addCalibrationButton('×', 'block', '#d71920', 'Marcar foto como prohibida para calibración');
              document.body.appendChild(marker);
            });
          };
          window.__dagSetVisualCalibrationMode = function(enabled) {
            window.__dagVisualCalibrationEnabled = enabled === true;
            if (document.documentElement) {
              if (window.__dagVisualCalibrationEnabled) {
                document.documentElement.setAttribute('data-dag-dev-calibration', 'true');
              } else {
                document.documentElement.removeAttribute('data-dag-dev-calibration');
              }
            }
            window.__dagRefreshCalibrationMarkers();
          };
          if (!window.__dagCalibrationMarkerEvents) {
            window.__dagCalibrationMarkerEvents = true;
            window.addEventListener('scroll', function() {
              if (window.__dagVisualCalibrationEnabled) window.requestAnimationFrame(window.__dagRefreshCalibrationMarkers);
            }, true);
            window.addEventListener('resize', function() {
              if (window.__dagVisualCalibrationEnabled) window.requestAnimationFrame(window.__dagRefreshCalibrationMarkers);
            });
          }
          dagSecureNode(document.documentElement, true);
          if (document.documentElement) {
            if (window.__dagExtraKosherEnabled) {
              document.documentElement.setAttribute('data-dag-extra-kosher', 'true');
            } else {
              document.documentElement.removeAttribute('data-dag-extra-kosher');
            }
          }
          var style = document.getElementById('dag-safe-style');
          if (!style) {
            style = document.createElement('style');
            style.id = 'dag-safe-style';
            style.textContent = 'video,audio,canvas,iframe:not([data-dag-safe-captcha="true"]) { display:none !important; } img[data-dag-kosher-blurred="true"], img[data-dag-extra-kosher-blurred="true"] { filter: blur(48px) saturate(0.05) brightness(0.82) !important; transform: scale(1.14) !important; } html[data-dag-dev-calibration="true"] img[data-dag-kosher-blurred="true"]:not([data-dag-extra-kosher-blurred="true"]) { filter:none !important; transform:none !important; }';
            document.documentElement.appendChild(style);
          }
          if (!window.__dagSafeObserver) {
            window.__dagSafeObserver = new MutationObserver(function(records) {
              var calibrationNeedsRefresh = false;
              records.forEach(function(record) {
                record.addedNodes.forEach(function(node) {
                  dagQueueSecurityNode(node);
                  if (!(node.nodeType === 1 && node.getAttribute('data-dag-calibration-marker') === 'true')) {
                    calibrationNeedsRefresh = true;
                  }
                });
                if (record.type === 'attributes') {
                  dagSecureNode(record.target, false);
                  if (record.target.getAttribute('data-dag-calibration-marker') !== 'true') {
                    calibrationNeedsRefresh = true;
                  }
                }
              });
              if (calibrationNeedsRefresh && window.__dagVisualCalibrationEnabled) {
                window.requestAnimationFrame(window.__dagRefreshCalibrationMarkers);
              }
            });
            window.__dagSafeObserver.observe(document.documentElement, {
              childList: true,
              subtree: true,
              attributes: true,
              attributeFilter: [
                'src', 'srcset', 'data-src', 'data-lazy-src', 'data-srcset', 'data-lazy-srcset',
                'style', 'class', 'alt', 'title', 'aria-label', 'loading'
              ]
            });
          }
          return document.body ? document.body.innerText.substring(0,24000) : '';
        })();
        """.trimIndent(),
    ) { encoded ->
        val text = encoded.decodeJavascriptString()
        if (text.isNullOrBlank() && attempt < MaximumTextExtractionRetries && isAttachedToWindow) {
            postDelayed(
                {
                    runCatching {
                        sanitizeAndExtractVisibleText(extraKosherEnabled, attempt + 1, callback)
                    }.onFailure { callback(null) }
                },
                TextExtractionRetryDelayMillis * (attempt + 1L),
            )
        } else {
            callback(text)
        }
    }
}

private fun WebView.awaitDagViewportImages(
    startedAtMillis: Long = SystemClock.elapsedRealtime(),
    networkImagesEnabled: Boolean = false,
    onProgress: (Int, Int) -> Unit,
    callback: () -> Unit,
) {
    val prepareViewportScript =
        "(function(){ return window.__dagPrepareViewportImages " +
            "? window.__dagPrepareViewportImages(false) : null; })();"
    evaluateJavascript(
        prepareViewportScript,
    ) { encoded ->
        if (!networkImagesEnabled) settings.blockNetworkImage = false
        val status =
            encoded.decodeJavascriptString()?.let { value ->
                runCatching { org.json.JSONObject(value) }.getOrNull()
            }
        val pending = status?.optInt("pending", 0) ?: 0
        val total = status?.optInt("total", 0) ?: 0
        onProgress((total - pending).coerceAtLeast(0), total)
        val elapsed = SystemClock.elapsedRealtime() - startedAtMillis
        when (dagViewportReadinessAction(pending, elapsed)) {
            DagViewportReadinessAction.Ready -> callback()
            DagViewportReadinessAction.Wait ->
                if (isAttachedToWindow) {
                    postDelayed(
                        {
                            awaitDagViewportImages(
                                startedAtMillis,
                                networkImagesEnabled = true,
                                onProgress,
                                callback,
                            )
                        },
                        DagViewportReadinessPolicy.PollDelayMillis,
                    )
                }
            DagViewportReadinessAction.HidePending ->
                evaluateJavascript(
                    "(function(){ return window.__dagPrepareViewportImages " +
                        "? window.__dagPrepareViewportImages(true) : null; })();",
                ) { callback() }
        }
    }
}

internal enum class DagViewportReadinessAction {
    Ready,
    Wait,
    HidePending,
}

internal fun dagViewportReadinessAction(
    pendingImages: Int,
    elapsedMillis: Long,
): DagViewportReadinessAction =
    when {
        pendingImages <= 0 -> DagViewportReadinessAction.Ready
        elapsedMillis >= DagViewportReadinessPolicy.MaximumWaitMillis -> DagViewportReadinessAction.HidePending
        else -> DagViewportReadinessAction.Wait
    }

internal object DagViewportReadinessPolicy {
    // Resolve the visible viewport first. Nearby lazy photos begin loading just
    // before scrolling reaches them instead of competing with initial content.
    const val PreparedViewportCount = 1
    const val PrefetchViewportCount = 1
    const val MaximumWaitMillis = 8_000L
    const val PollDelayMillis = 150L
    const val VisualSettleMillis = 300L
}

internal object DagDomSecurityBatchPolicy {
    const val MaximumNodesPerFrame = 48
}

internal fun isDagCaptchaProviderUrl(url: String): Boolean =
    runCatching {
        val parsed = java.net.URI(url)
        if (parsed.scheme != "https") return@runCatching false
        val host = parsed.host?.lowercase().orEmpty()
        val path = parsed.path.orEmpty()
        ((host == "www.google.com" || host == "www.recaptcha.net") && path.startsWith("/recaptcha/")) ||
            (host == "challenges.cloudflare.com" && path.startsWith("/cdn-cgi/challenge-platform/")) ||
            (host == "newassets.hcaptcha.com" && path.startsWith("/captcha/"))
    }.getOrDefault(false)

internal fun isDagCaptchaSessionResourceUrl(url: String): Boolean =
    runCatching {
        val parsed = java.net.URI(url)
        if (parsed.scheme != "https") return@runCatching false
        val host = parsed.host?.lowercase().orEmpty()
        val path = parsed.path.orEmpty()
        isDagCaptchaProviderUrl(url) ||
            (host == "www.gstatic.com" && path.startsWith("/recaptcha/"))
    }.getOrDefault(false)

internal fun dagDomSecurityBatchCount(nodeCount: Int): Int {
    if (nodeCount <= 0) return 0
    return (nodeCount + DagDomSecurityBatchPolicy.MaximumNodesPerFrame - 1) /
        DagDomSecurityBatchPolicy.MaximumNodesPerFrame
}

private fun String?.decodeJavascriptString(): String? {
    if (this == null || this == "null") return null
    return runCatching { JSONArray("[$this]").optString(0).takeIf { it.isNotBlank() } }.getOrNull()
}

private const val MaximumTextExtractionRetries = 2
private const val TextExtractionRetryDelayMillis = 700L
private const val PageCommitInspectionDelayMillis = 1_000L
private const val DagCalibrationBridgeName = "dagCalibrationBridge"
private const val DagCaptchaBridgeName = "dagCaptchaBridge"
private const val DagCaptchaSessionStart = "captcha_session_start"
private const val DagCaptchaSessionDurationMillis = 120_000L
private const val DagCalibrationActionProbe = "probe"
private const val DagCalibrationActionBlock = "block"
private const val DagCalibrationActionAllow = "allow"
