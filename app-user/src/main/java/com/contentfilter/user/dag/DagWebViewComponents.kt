package com.contentfilter.user.dag

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.ViewGroup
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnLayout
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.contentfilter.user.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.util.concurrent.atomic.AtomicLong

private val dagClassifierDisposalScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun DagWebContent(
    state: DagBrowserUiState,
    onBackFromBrowser: () -> Unit,
    onNavigationRequested: (String) -> Boolean,
    onPageStarted: (String) -> Boolean,
    onPageTextReady: (String, String, String?, DagImagePageSummary) -> Unit,
    onViewportImagesReady: (String) -> Unit,
    onViewportImageProgress: (String, Int, Int) -> Unit,
    onCalibrationCandidate: (ByteArray, DagImageClassification) -> Unit,
    visualCalibrationEnabled: Boolean,
    onManualCalibrationCandidate: (ByteArray, DagImageClassification) -> Unit,
    onManualBlurReviewCandidate: (ByteArray, DagImageClassification) -> Unit,
    onBlockedAction: (String) -> Unit,
    onPageBlocked: (String) -> Unit,
    onRendererGone: () -> Unit,
    onWebViewChanged: (WebView?) -> Unit,
    onNavigationStateChanged: (Boolean, Boolean) -> Unit,
) {
    val context = LocalContext.current
    val sharedImageClassifiers = remember(context) { DagSharedImageClassifiers(context) }
    val imageClassifiers =
        remember(context, sharedImageClassifiers) {
            List(DagImageDeliveryPolicy.MaximumConcurrentClassifications) {
                DagImageClassifier(context, sharedImageClassifiers)
            }
        }
    val currentCalibrationCandidate by rememberUpdatedState(onCalibrationCandidate)
    val currentManualCalibrationCandidate by rememberUpdatedState(onManualCalibrationCandidate)
    val currentManualBlurReviewCandidate by rememberUpdatedState(onManualBlurReviewCandidate)
    val currentVisualCalibrationEnabled by rememberUpdatedState(visualCalibrationEnabled)
    val currentExtraKosherEnabled by rememberUpdatedState(state.dagExtraKosherEnabled)
    val currentPageStatus by rememberUpdatedState(state.pageStatus)
    val currentReviewCandidate by rememberUpdatedState(state.reviewCandidate)
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
    var pendingNavigationUrl by remember { mutableStateOf<String?>(null) }

    BackHandler {
        if (webView?.canGoBack() == true) webView?.goBack() else onBackFromBrowser()
    }
    LaunchedEffect(state.dagEnabled) {
        if (!state.dagEnabled) {
            pendingNavigationUrl = null
            webView?.stopLoading()
            webView?.loadUrl("about:blank")
        }
    }
    LaunchedEffect(sharedImageClassifiers) {
        withContext(Dispatchers.Default) { sharedImageClassifiers.prepare() }
    }
    val programmaticNavigationKey = dagProgrammaticNavigationEffectKey(state)
    LaunchedEffect(webView, programmaticNavigationKey) {
        state.requestedUrl?.let { url ->
            val view = webView ?: return@let
            if (url.startsWith("https://", ignoreCase = true) && view.url != url) {
                pendingNavigationUrl = url
                // The first navigation can otherwise start while AndroidView
                // still reports a zero layout height. Chromium then keeps a
                // zero CSS layout viewport for that renderer even after
                // innerHeight updates, breaking every vh/dvh based site.
                view.doOnLayout {
                    if (
                        pendingNavigationUrl == url &&
                        view.width > 0 &&
                        view.height > 0 &&
                        view.url != url
                    ) {
                        pendingNavigationUrl = null
                        view.loadUrl(url)
                    }
                }
            }
        }
    }
    LaunchedEffect(webView, state.pageStatus, state.requestedUrl) {
        if (state.pageStatus == DagPageStatus.Visible) {
            webView?.evaluateJavascript(
                "(function(){if(window.__dagReleaseContentGuard){window.__dagReleaseContentGuard();}})();",
                null,
            )
        }
    }
    LaunchedEffect(webView, visualCalibrationEnabled) {
        val view = webView
        if (imageLoader.setDevCalibrationRevealEnabled(visualCalibrationEnabled)) {
            view?.url?.takeIf { it.startsWith("https://", ignoreCase = true) }?.let {
                view.reload()
            }
        } else if (state.pageStatus == DagPageStatus.Visible) {
            view?.setDagVisualCalibrationMode(visualCalibrationEnabled)
        }
    }
    LaunchedEffect(webView, state.dagExtraKosherEnabled) {
        val view = webView ?: return@LaunchedEffect
        view.url?.takeIf { it.startsWith("https://", ignoreCase = true) }?.let {
            view.reload()
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            scheduleDagClassifierDisposal(
                cancelLoader = imageLoader::cancel,
                schedule = { cleanup ->
                    dagClassifierDisposalScope.launch { cleanup() }
                },
                classifierClosers = imageClassifiers.map { classifier -> classifier::close },
                sharedCloser = sharedImageClassifiers::close,
            )
            webView?.stopLoading()
            webView?.destroy()
            webView = null
            onWebViewChanged(null)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            // Keep the native WebView in a normal measured layer while DAG's
            // opaque Compose layer below covers unapproved content. Combined
            // with explicit MATCH_PARENT params, Chromium receives a real CSS
            // layout viewport instead of the zero-height viewport that
            // collapses vh/dvh menus and responsive page shells.
            modifier = Modifier.fillMaxSize(),
            factory = {
                WebView(context).apply {
                    layoutParams =
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                    webView = this
                    onWebViewChanged(this)
                    val dagWebView = this
                    val cookieManager = CookieManager.getInstance()
                    val pageUrlTracker = DagPageUrlTracker()
                    val sanitizerCoordinator = DagSanitizerCoordinator()
                    var captchaSessionRevision = 0
                    val captchaSessionExpiresAt = AtomicLong(0L)
                    lateinit var handleContentChanged: (WebView, String, Boolean, Boolean) -> Unit

                    fun closeCaptchaSession() {
                        captchaSessionRevision += 1
                        captchaSessionExpiresAt.set(0L)
                        cookieManager.setAcceptThirdPartyCookies(dagWebView, false)
                        dagWebView.evaluateJavascript(
                            """
                            (function() {
                              window.__dagCaptchaSessionRequested = false;
                              document.querySelectorAll('iframe[data-dag-safe-captcha="true"]').forEach(function(frame) {
                                frame.removeAttribute('data-dag-captcha-reloaded');
                              });
                            })();
                            """.trimIndent(),
                            null,
                        )
                    }

                    val documentStartSecurityAvailable =
                        configureDagSettings(
                            calibrationDecision = imageLoader::manualCalibrationDecision,
                            onImagePriorityRequested = imageLoader::prioritizeImageUrls,
                            onManualImageReported = { action, imageUrl ->
                                imageLoader.takeManualCalibrationCandidate(imageUrl)?.let { candidate ->
                                    when (action) {
                                        DagCalibrationActionBlock ->
                                            currentManualCalibrationCandidate(
                                                candidate.thumbnail,
                                                candidate.classification,
                                            )
                                        DagCalibrationActionReviewBlur ->
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
                            onContentChanged = { view, url, routeChanged, urgent ->
                                handleContentChanged(view, url, routeChanged, urgent)
                            },
                        )
                    setBackgroundColor(android.graphics.Color.WHITE)
                    cookieManager.apply {
                        setAcceptCookie(true)
                        setAcceptThirdPartyCookies(dagWebView, false)
                    }
                    webChromeClient = DagChromeClient(onBlockedAction)

                    fun prepareViewport(
                        view: WebView,
                        url: String,
                    ) {
                        if (preparedUrl == url) return
                        preparedUrl = url
                        // Viewport preparation only needs the sanitizer to be
                        // installed. Retrying an empty text extraction here
                        // delayed image readiness by 700 + 1,400 ms on SPA
                        // shells even when the parallel page inspection had
                        // already obtained enough text to classify the page.
                        sanitizerCoordinator.extract(
                            view = view,
                            documentRevision = sanitizerCoordinator.currentRevision(),
                            extraKosherEnabled = currentExtraKosherEnabled,
                        ) {
                            view.setDagVisualCalibrationMode(currentVisualCalibrationEnabled)
                            view.setDagImageCalibrationDecisions(imageLoader.manualCalibrationDecisions())
                            view.prepareDagViewportImagesFailClosed(
                                onProgress = { resolved, total -> onViewportImageProgress(url, resolved, total) },
                            ) {
                                if (preparedUrl == url) onViewportImagesReady(url)
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
                        view.sanitizeAndExtractVisibleText(
                            coordinator = sanitizerCoordinator,
                            extraKosherEnabled = currentExtraKosherEnabled,
                        ) { text ->
                            onPageTextReady(url, view.title.orEmpty(), text, imageLoader.pageSummary())
                        }
                    }
                    handleContentChanged = { view, url, routeChanged, urgent ->
                        if (routeChanged || urgent) {
                            closeCaptchaSession()
                            inspectedUrl = null
                            preparedUrl = null
                            if (routeChanged) {
                                pageUrlTracker.update(url)
                                imageLoader.resetPage()
                            }
                            if (onPageStarted(url)) {
                                inspectPage(view, url)
                            }
                        } else {
                            // Re-check meaningful SPA/fetch content in the
                            // background. A blocked result still hides the page;
                            // ordinary UI churn does not flash the whole browser.
                            // During the initial decision, repeated carousel/class
                            // mutations must not continually cancel the active
                            // classifier. Risky mutations use the urgent path
                            // above; benign re-checks begin once the page is live.
                            val recoveringUnreadablePage =
                                dagIsRecoverableUnreadablePage(
                                    pageStatus = currentPageStatus,
                                    reviewCandidate = currentReviewCandidate,
                                    url = url,
                                )
                            if (currentPageStatus == DagPageStatus.Visible || recoveringUnreadablePage) {
                                view.sanitizeAndExtractVisibleText(
                                    coordinator = sanitizerCoordinator,
                                    extraKosherEnabled = currentExtraKosherEnabled,
                                ) { text ->
                                    if (!recoveringUnreadablePage || !text.isNullOrBlank()) {
                                        onPageTextReady(url, view.title.orEmpty(), text, imageLoader.pageSummary())
                                    }
                                    if (recoveringUnreadablePage && !text.isNullOrBlank()) {
                                        preparedUrl = null
                                        prepareViewport(view, url)
                                    }
                                }
                            }
                        }
                    }
                    webViewClient =
                        DagWebViewClient(
                            onNavigationRequested = onNavigationRequested,
                            onStarted = { url ->
                                closeCaptchaSession()
                                inspectedUrl = null
                                preparedUrl = null
                                sanitizerCoordinator.onNewDocument()
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
                            pageUrlTracker = pageUrlTracker,
                            documentStartSecuritySupported = documentStartSecurityAvailable,
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

internal fun scheduleDagClassifierDisposal(
    cancelLoader: () -> Unit,
    schedule: (() -> Unit) -> Unit,
    classifierClosers: List<() -> Unit>,
    sharedCloser: () -> Unit,
) {
    cancelLoader()
    schedule {
        classifierClosers.forEach { closer -> runCatching(closer) }
        runCatching(sharedCloser)
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun WebView.configureDagSettings(
    calibrationDecision: (String) -> DagImageDecision?,
    onImagePriorityRequested: (Collection<String>) -> Unit,
    onManualImageReported: (String, String) -> Unit,
    onCaptchaDetected: (String) -> Unit,
    onContentChanged: (WebView, String, Boolean, Boolean) -> Unit,
): Boolean {
    val supportsDocumentStartScript = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
    val calibrationMessageLimiter =
        DagWebMessageRateLimiter(
            maximumMessages = DagWebMessagePolicy.MaximumCalibrationMessagesPerWindow,
            windowMillis = DagWebMessagePolicy.CalibrationWindowMillis,
        )
    val imagePriorityMessageLimiter =
        DagWebMessageRateLimiter(
            maximumMessages = DagWebMessagePolicy.MaximumImagePriorityMessagesPerWindow,
            windowMillis = DagWebMessagePolicy.ImagePriorityWindowMillis,
        )
    val captchaMessageLimiter =
        DagWebMessageRateLimiter(
            maximumMessages = DagWebMessagePolicy.MaximumCaptchaMessagesPerWindow,
            windowMillis = DagWebMessagePolicy.CaptchaWindowMillis,
        )
    val contentMessageLimiter =
        DagWebMessageRateLimiter(
            maximumMessages = DagWebMessagePolicy.MaximumContentMessagesPerWindow,
            windowMillis = DagWebMessagePolicy.ContentWindowMillis,
        )
    settings.javaScriptEnabled = documentStartSecurityAvailable(supportsDocumentStartScript)
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
            val data =
                message.data.takeIf {
                    dagWebMessagePayloadAllowed(it, DagWebMessagePolicy.MaximumControlPayloadBytes) &&
                        calibrationMessageLimiter.tryAcquire()
                } ?: return@addWebMessageListener
            val payload = runCatching { org.json.JSONObject(data) }.getOrNull() ?: return@addWebMessageListener
            val action = payload.optString("action")
            val imageUrl = payload.optString("url").takeIf { it.startsWith("https://") } ?: return@addWebMessageListener
            if (action == DagCalibrationActionProbe) {
                calibrationDecision(imageUrl)?.let { view.setDagImageCalibrationDecision(imageUrl, it) }
                return@addWebMessageListener
            }
            if (action == DagCalibrationActionBlock || action == DagCalibrationActionReviewBlur) {
                onManualImageReported(action, imageUrl)
            }
        }
    }
    if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
        WebViewCompat.addWebMessageListener(
            this,
            DagImagePriorityBridgeName,
            setOf("*"),
        ) { view, message, sourceOrigin, isMainFrame, _ ->
            if (!isMainFrame || sourceOrigin.scheme != "https") return@addWebMessageListener
            val data =
                message.data.takeIf {
                    dagWebMessagePayloadAllowed(it, DagWebMessagePolicy.MaximumImagePriorityPayloadBytes) &&
                        imagePriorityMessageLimiter.tryAcquire()
                } ?: return@addWebMessageListener
            val payload =
                runCatching { org.json.JSONObject(data) }.getOrNull()
                    ?: return@addWebMessageListener
            if (payload.optString("action") != DagImagePriorityAction) return@addWebMessageListener
            val documentUrl =
                payload
                    .optString("page")
                    .takeIf { it.startsWith("https://", ignoreCase = true) }
                    ?: return@addWebMessageListener
            if (
                view.url.orEmpty().substringBefore('#') != documentUrl.substringBefore('#') ||
                android.net.Uri.parse(documentUrl).host != sourceOrigin.host
            ) {
                return@addWebMessageListener
            }
            val urls = payload.optJSONArray("urls") ?: return@addWebMessageListener
            onImagePriorityRequested(
                buildList {
                    repeat(minOf(urls.length(), DagMaximumPriorityUrlsPerMessage)) { index ->
                        urls.optString(index)
                            .takeIf { it.startsWith("https://", ignoreCase = true) }
                            ?.let(::add)
                    }
                },
            )
        }
        WebViewCompat.addWebMessageListener(
            this,
            DagCaptchaBridgeName,
            setOf("*"),
        ) { _, message, sourceOrigin, isMainFrame, _ ->
            if (!isMainFrame || sourceOrigin.scheme != "https") return@addWebMessageListener
            val data =
                message.data.takeIf {
                    dagWebMessagePayloadAllowed(it, DagWebMessagePolicy.MaximumControlPayloadBytes) &&
                        captchaMessageLimiter.tryAcquire()
                } ?: return@addWebMessageListener
            val payload = runCatching { org.json.JSONObject(data) }.getOrNull()
            val captchaUrl = payload?.optString("url").orEmpty()
            if (payload?.optString("action") == DagCaptchaSessionStart && isDagCaptchaProviderUrl(captchaUrl)) {
                onCaptchaDetected(captchaUrl)
            }
        }
        WebViewCompat.addWebMessageListener(
            this,
            DagContentBridgeName,
            setOf("*"),
        ) { view, message, sourceOrigin, isMainFrame, _ ->
            if (!isMainFrame || sourceOrigin.scheme != "https") return@addWebMessageListener
            val data =
                message.data.takeIf {
                    dagWebMessagePayloadAllowed(it, DagWebMessagePolicy.MaximumControlPayloadBytes) &&
                        contentMessageLimiter.tryAcquire()
                } ?: return@addWebMessageListener
            val payload =
                runCatching { org.json.JSONObject(data) }.getOrNull()
                    ?: return@addWebMessageListener
            if (payload.optString("action") != DagContentChangedAction) return@addWebMessageListener
            val documentUrl =
                payload
                    .optString("url")
                    .takeIf { it.length <= DagMaximumDocumentUrlLength }
                    ?.takeIf { it.startsWith("https://", ignoreCase = true) }
                    ?: return@addWebMessageListener
            if (
                !dagSameOrigin(view.url.orEmpty(), documentUrl) ||
                android.net.Uri.parse(documentUrl).host != sourceOrigin.host
            ) {
                return@addWebMessageListener
            }
            onContentChanged(
                view,
                documentUrl,
                payload.optBoolean("routeChanged", false),
                payload.optBoolean("urgent", false),
            )
        }
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
    settings.useWideViewPort = true
    settings.loadWithOverviewMode = false
    return supportsDocumentStartScript
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

internal val DagDocumentStartScript =
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
        function dagInstallImagePolicyAtDocumentStart() {
          var dagPolicyParent = document.head;
          if (!dagPolicyParent) return false;
          var existingPolicy = document.getElementById('$DagImageSchemeCspMetaId');
          if (existingPolicy) return true;
          var dagImagePolicy = document.createElement('meta');
          dagImagePolicy.id = '$DagImageSchemeCspMetaId';
          dagImagePolicy.setAttribute('http-equiv', 'Content-Security-Policy');
          dagImagePolicy.setAttribute('content', '$DagImageSchemeCspContent');
          dagPolicyParent.insertBefore(dagImagePolicy, dagPolicyParent.firstChild);
          window.__dagImageSchemeCspInstalled = true;
          return true;
        }
        if (!dagInstallImagePolicyAtDocumentStart()) {
          var dagPolicyObserver = new MutationObserver(function() {
            if (!dagInstallImagePolicyAtDocumentStart()) return;
            dagPolicyObserver.disconnect();
          });
          dagPolicyObserver.observe(document, { childList: true, subtree: true });
        }
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
        function dagStopMedia(event) {
          var media = event && event.target;
          if (!media || !(media instanceof HTMLMediaElement)) return;
          try {
            media.muted = true;
            media.pause();
          } catch (_) {}
        }
        document.addEventListener('play', dagStopMedia, true);
        document.addEventListener('playing', dagStopMedia, true);
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
    private val onNavigationRequested: (String) -> Boolean,
    private val onStarted: (String) -> Boolean,
    private val onCommitted: (WebView, String) -> Unit,
    private val onFinished: (WebView, String) -> Unit,
    private val onBlocked: (String) -> Unit,
    private val onRendererGone: (WebView) -> Unit,
    private val imageLoader: DagImageResourceLoader,
    private val captchaSessionActive: () -> Boolean,
    private val pageUrlTracker: DagPageUrlTracker,
    private val documentStartSecuritySupported: Boolean,
) : WebViewClient() {
    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest,
    ): Boolean {
        if (request.isForMainFrame && !documentStartSecurityAvailable(documentStartSecuritySupported)) {
            view.stopLoading()
            onBlocked(DagDocumentStartSecurityUnsupportedMessage)
            return true
        }
        val target = request.url
        if (target.scheme != "https") {
            onBlocked("DAG bloqueó una navegación no segura.")
            return true
        }
        val currentHost = Uri.parse(view.url.orEmpty()).host
        if (request.isForMainFrame && currentHost != null && target.host != currentHost) {
            val targetUrl = target.toString()
            return !onNavigationRequested(targetUrl)
        }
        return false
    }

    override fun onPageStarted(
        view: WebView,
        url: String,
        favicon: Bitmap?,
    ) {
        if (!documentStartSecurityAvailable(documentStartSecuritySupported)) {
            view.stopLoading()
            onBlocked(DagDocumentStartSecurityUnsupportedMessage)
            return
        }
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

internal enum class DagSanitizerRequestAction {
    Install,
    WaitForInstall,
    Extract,
}

internal class DagSanitizerDocumentState {
    private var documentRevision = 0L
    private var installedRevision = -1L
    private var installingRevision = -1L

    fun onNewDocument(): Long {
        documentRevision += 1L
        installedRevision = -1L
        installingRevision = -1L
        return documentRevision
    }

    fun currentRevision(): Long = documentRevision

    fun isCurrent(revision: Long): Boolean = revision == documentRevision

    fun request(revision: Long): DagSanitizerRequestAction? {
        if (!isCurrent(revision)) return null
        return when (revision) {
            installedRevision -> DagSanitizerRequestAction.Extract
            installingRevision -> DagSanitizerRequestAction.WaitForInstall
            else -> {
                installingRevision = revision
                DagSanitizerRequestAction.Install
            }
        }
    }

    fun completeInstall(
        revision: Long,
        succeeded: Boolean,
    ): Boolean {
        if (!isCurrent(revision) || installingRevision != revision) return false
        installingRevision = -1L
        installedRevision = revision.takeIf { succeeded } ?: -1L
        return true
    }
}

private class DagSanitizerCoordinator {
    private val state = DagSanitizerDocumentState()
    private val pendingCallbacks = mutableMapOf<Long, MutableList<(String?) -> Unit>>()

    fun onNewDocument() {
        state.onNewDocument()
        pendingCallbacks.clear()
    }

    fun currentRevision(): Long = state.currentRevision()

    fun isCurrent(revision: Long): Boolean = state.isCurrent(revision)

    fun extract(
        view: WebView,
        documentRevision: Long,
        extraKosherEnabled: Boolean,
        callback: (String?) -> Unit,
    ) {
        when (state.request(documentRevision) ?: return) {
            DagSanitizerRequestAction.Extract ->
                view.extractDagVisibleText { text ->
                    if (state.isCurrent(documentRevision)) callback(text)
                }
            DagSanitizerRequestAction.WaitForInstall ->
                pendingCallbacks.getOrPut(documentRevision, ::mutableListOf).add(callback)
            DagSanitizerRequestAction.Install -> {
                pendingCallbacks.getOrPut(documentRevision, ::mutableListOf).add(callback)
                view.installDagSanitizerAndExtractVisibleText(extraKosherEnabled) { installed, text ->
                    if (!state.completeInstall(documentRevision, installed)) {
                        pendingCallbacks.remove(documentRevision)
                        return@installDagSanitizerAndExtractVisibleText
                    }
                    val callbacks = pendingCallbacks.remove(documentRevision).orEmpty()
                    callbacks.forEach { pending -> pending(if (installed) text else null) }
                }
            }
        }
    }
}

private fun WebView.sanitizeAndExtractVisibleText(
    coordinator: DagSanitizerCoordinator,
    extraKosherEnabled: Boolean,
    attempt: Int = 0,
    documentRevision: Long = coordinator.currentRevision(),
    callback: (String?) -> Unit,
) {
    if (!coordinator.isCurrent(documentRevision)) return
    coordinator.extract(
        view = this,
        documentRevision = documentRevision,
        extraKosherEnabled = extraKosherEnabled,
    ) { text ->
        if (!coordinator.isCurrent(documentRevision)) return@extract
        if (
            dagShouldRetryTextExtraction(
                text = text,
                attempt = attempt,
                isAttachedToWindow = isAttachedToWindow,
            )
        ) {
            postDelayed(
                {
                    runCatching {
                        sanitizeAndExtractVisibleText(
                            coordinator = coordinator,
                            extraKosherEnabled = extraKosherEnabled,
                            attempt = attempt + 1,
                            documentRevision = documentRevision,
                            callback = callback,
                        )
                    }.onFailure { callback(null) }
                },
                DagTextExtractionRetryPolicy.BaseDelayMillis * (attempt + 1L),
            )
        } else {
            callback(text)
        }
    }
}

private fun WebView.extractDagVisibleText(callback: (String?) -> Unit) {
    evaluateJavascript(
        "(function(){return document.body ? document.body.innerText.substring(0,24000) : '';})();",
    ) { encoded ->
        callback(encoded.decodeJavascriptString())
    }
}

private fun WebView.installDagSanitizerAndExtractVisibleText(
    extraKosherEnabled: Boolean,
    callback: (Boolean, String?) -> Unit,
) {
    evaluateJavascript(
        """
        (function() {
          function dagInstallImageSchemeCsp() {
            var parent = document.head;
            if (!parent && document.documentElement) {
              parent = document.createElement('head');
              document.documentElement.insertBefore(
                parent,
                document.documentElement.firstChild
              );
            }
            if (!parent) return false;
            var policy = document.getElementById('$DagImageSchemeCspMetaId');
            var policyIsComplete =
              policy &&
              policy.tagName &&
              policy.tagName.toLowerCase() === 'meta' &&
              (policy.getAttribute('http-equiv') || '').toLowerCase() ===
                'content-security-policy' &&
              policy.getAttribute('content') === '$DagImageSchemeCspContent';
            if (policy && !policyIsComplete) {
              policy.remove();
              policy = null;
            }
            if (!policy) {
              policy = document.createElement('meta');
              policy.id = '$DagImageSchemeCspMetaId';
              policy.setAttribute('http-equiv', 'Content-Security-Policy');
              policy.setAttribute('content', '$DagImageSchemeCspContent');
              parent.insertBefore(policy, parent.firstChild);
            }
            window.__dagImageSchemeCspInstalled = true;
            return true;
          }
          dagInstallImageSchemeCsp();
          window.__dagExtraKosherEnabled = ${if (extraKosherEnabled) "true" else "false"};
          var dagIntimatePattern = /$DagIntimateImageJavaScriptPattern/i;
          function dagNormalizedText(value) {
            return (value || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();
          }
          var dagContentNotifyTimer = 0;
          var dagContentNotifyTimerUrgent = false;
          var dagContentRoutePending = false;
          var dagContentUrgentPending = false;
          var dagLastObservedHref = location.href;
          var dagLastContentSignature = '';
          var dagLastContentCheckAt = -1;
          var dagDynamicRiskPattern = /$DagDynamicRiskJavaScriptPattern/i;
          var dagSafeStyleText = 'video,audio,canvas { visibility:hidden !important; } object,embed,svg image,svg foreignObject,iframe:not([data-dag-safe-captcha="true"]) { display:none !important; } img[data-dag-kosher-blurred="true"], img[data-dag-extra-kosher-blurred="true"] { filter: blur(48px) saturate(0.05) brightness(0.82) !important; transform: scale(1.14) !important; } html[data-dag-dev-calibration="true"] img[data-dag-kosher-blurred="true"]:not([data-dag-extra-kosher-blurred="true"]) { filter:none !important; transform:none !important; } $DagBlockedPseudoBackgroundCss';
          function dagContentNow() {
            return window.performance ? window.performance.now() : Date.now();
          }
          function dagEnsureSafeStyle() {
            var root = document.documentElement;
            if (!root) return;
            var style = document.getElementById('dag-safe-style');
            if (style && (!style.tagName || style.tagName.toLowerCase() !== 'style')) {
              style.remove();
              style = null;
            }
            if (!style) {
              style = document.createElement('style');
              style.id = 'dag-safe-style';
              root.appendChild(style);
            }
            if (style.textContent !== dagSafeStyleText) style.textContent = dagSafeStyleText;
          }
          function dagSetContentGuard() {
            var parent = document.head || document.documentElement;
            if (!parent) return;
            var guard = document.getElementById('dag-content-guard');
            if (guard && (!guard.tagName || guard.tagName.toLowerCase() !== 'style')) {
              guard.remove();
              guard = null;
            }
            if (!guard) {
              guard = document.createElement('style');
              guard.id = 'dag-content-guard';
              parent.appendChild(guard);
            }
            // Cover the page without changing its rendered-text semantics.
            // `visibility:hidden` makes body.innerText empty in Chromium and
            // would therefore turn an urgent SPA re-check into an accidental
            // "blank page" approval.
            var guardText =
              'html::before{content:""!important;position:fixed!important;inset:0!important;' +
              'display:block!important;background:#fff!important;z-index:2147483647!important;' +
              'pointer-events:auto!important;opacity:1!important}';
            if (guard.textContent !== guardText) guard.textContent = guardText;
          }
          window.__dagReleaseContentGuard = function() {
            var guard = document.getElementById('dag-content-guard');
            if (guard) guard.remove();
          };
          function dagContentSignature() {
            var text = document.body ? (document.body.innerText || '').substring(0, 24000) : '';
            var hash = 2166136261;
            for (var index = 0; index < text.length; index += 1) {
              hash ^= text.charCodeAt(index);
              hash = Math.imul(hash, 16777619);
            }
            return location.href + '|' + text.length + '|' + (hash >>> 0);
          }
          function dagScheduleContentCheck(routeChanged, urgent) {
            dagContentRoutePending = dagContentRoutePending || routeChanged === true;
            dagContentUrgentPending = dagContentUrgentPending || urgent === true;
            if (dagContentRoutePending || dagContentUrgentPending) dagSetContentGuard();
            var mustRunUrgently = dagContentRoutePending || dagContentUrgentPending;
            // Keep the first normal deadline instead of extending it for every
            // animation/mutation. An urgent deadline also stays fixed once
            // scheduled, so harmless churn cannot postpone a safety re-check.
            if (dagContentNotifyTimer) {
              if (dagContentNotifyTimerUrgent || !mustRunUrgently) return;
              window.clearTimeout(dagContentNotifyTimer);
            }
            var now = dagContentNow();
            var normalDelay = ${DagContentMutationPolicy.NormalDebounceMillis};
            if (!mustRunUrgently && dagLastContentCheckAt >= 0) {
              normalDelay = Math.max(
                normalDelay,
                ${DagContentMutationPolicy.NormalThrottleMillis} -
                  Math.max(0, now - dagLastContentCheckAt)
              );
            }
            dagContentNotifyTimerUrgent = mustRunUrgently;
            dagContentNotifyTimer = window.setTimeout(function() {
              dagContentNotifyTimer = 0;
              dagContentNotifyTimerUrgent = false;
              dagLastContentCheckAt = dagContentNow();
              var signature = dagContentSignature();
              var route = dagContentRoutePending;
              var urgentChange = dagContentUrgentPending;
              dagContentRoutePending = false;
              dagContentUrgentPending = false;
              if (!route && !urgentChange && signature === dagLastContentSignature) return;
              dagLastContentSignature = signature;
              if (!window.$DagContentBridgeName) return;
              try {
                window.$DagContentBridgeName.postMessage(JSON.stringify({
                  action: '$DagContentChangedAction',
                  url: location.href,
                  routeChanged: route,
                  urgent: urgentChange
                }));
              } catch (_) {}
            }, mustRunUrgently ? ${DagContentMutationPolicy.UrgentDelayMillis} : normalDelay);
          }
          function dagCheckRouteChange() {
            if (location.href === dagLastObservedHref) return;
            dagLastObservedHref = location.href;
            dagScheduleContentCheck(true, true);
          }
          window.addEventListener('popstate', dagCheckRouteChange, true);
          window.addEventListener('hashchange', dagCheckRouteChange, true);
          ['pushState', 'replaceState'].forEach(function(methodName) {
            var original = window.history && window.history[methodName];
            if (typeof original !== 'function') return;
            window.history[methodName] = function() {
              var result = original.apply(this, arguments);
              dagCheckRouteChange();
              return result;
            };
          });
          window.setInterval(dagCheckRouteChange, 250);
          var dagInternalImageAttributes = new WeakMap();
          var dagInternalStyleMutations = new WeakMap();
          function dagWriteInternalStyle(node, writer) {
            if (!node || typeof writer !== 'function') return;
            var before = node.getAttribute('style') || '';
            writer();
            var value = node.getAttribute('style') || '';
            if (value === before) return;
            var existing = dagInternalStyleMutations.get(node);
            dagInternalStyleMutations.set(node, {
              value: value,
              count: existing ? existing.count + 1 : 1
            });
          }
          function dagConsumeInternalStyleMutation(node) {
            var expected = node && dagInternalStyleMutations.get(node);
            if (!expected) return false;
            if ((node.getAttribute('style') || '') !== expected.value) {
              dagInternalStyleMutations.delete(node);
              return false;
            }
            expected.count -= 1;
            if (expected.count <= 0) dagInternalStyleMutations.delete(node);
            return true;
          }
          var dagSourceAttributeNames = {
            'src': true,
            'srcset': true,
            'data-src': true,
            'data-lazy-src': true,
            'data-srcset': true,
            'data-lazy-srcset': true
          };
          function dagSetInternalImageAttribute(node, name, value) {
            var attributes = dagInternalImageAttributes.get(node);
            if (!attributes) {
              attributes = Object.create(null);
              dagInternalImageAttributes.set(node, attributes);
            }
            var existing = attributes[name];
            attributes[name] = {
              value: String(value),
              count: existing && existing.value === String(value) ? existing.count + 1 : 1
            };
            node.setAttribute(name, value);
          }
          function dagConsumeInternalImageMutation(node, name) {
            var attributes = dagInternalImageAttributes.get(node);
            var expected = attributes && attributes[name];
            if (!expected) return false;
            if (node.getAttribute(name) !== expected.value) {
              delete attributes[name];
              return false;
            }
            expected.count -= 1;
            if (expected.count <= 0) delete attributes[name];
            return true;
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
          function dagResolvedHttpsImageUrl(raw, allowPriorityUrl) {
            if (!raw) return '';
            var firstCandidate = raw.split(',')[0].trim().split(/\s+/)[0];
            if (!firstCandidate) return '';
            try {
              var resolved = new URL(firstCandidate, document.baseURI);
              if (resolved.protocol !== 'https:') return '';
              if (!allowPriorityUrl &&
                  resolved.pathname.indexOf('/.dag-safe-image/') === 0) return '';
              resolved.hash = '';
              return resolved.href;
            } catch (_) {
              return '';
            }
          }
          function dagSelectedImageSource(image) {
            // Source mutations are observed before Chromium necessarily updates
            // currentSrc. Prefer that fresh external value so carousels never
            // reclassify or display the previous responsive candidate.
            var observedSource = dagResolvedHttpsImageUrl(image.getAttribute('data-dag-observed-source'), false);
            if (observedSource) return observedSource;
            var currentSource = dagResolvedHttpsImageUrl(image.currentSrc, false);
            if (currentSource) return currentSource;
            var directSource = dagResolvedHttpsImageUrl(image.getAttribute('src'), false);
            if (directSource) return directSource;
            var activeSource =
              dagResolvedHttpsImageUrl(image.currentSrc, true) ||
              dagResolvedHttpsImageUrl(image.getAttribute('src'), true);
            var existingSource = image.getAttribute('data-dag-priority-source') || '';
            if (activeSource.indexOf('/.dag-safe-image/') >= 0 && existingSource) {
              return existingSource;
            }
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
              pictureSource && pictureSource.getAttribute('srcset')
            ];
            for (var index = 0; index < candidates.length; index++) {
              var candidate = dagResolvedHttpsImageUrl(candidates[index], false);
              if (candidate) return candidate;
            }
            return '';
          }
          function dagHttpsImageSource(image) {
            return dagSelectedImageSource(image) ||
              dagResolvedHttpsImageUrl(image.currentSrc, true) ||
              dagResolvedHttpsImageUrl(image.getAttribute('src'), true);
          }
          var dagImageFastLaneRemaining = ${DagImageFastLanePolicy.MaximumImagesPerDocument};
          var dagImageFastLaneImages = new WeakSet();
          var dagPendingPriorityUrls = Object.create(null);
          var dagPriorityFlushScheduled = false;
          function dagCollectImageSources(image) {
            var selected = dagSelectedImageSource(image);
            return selected ? [selected] : [];
          }
          function dagQueuePriorityUrls(urls) {
            if (!urls || !urls.length || !window.$DagImagePriorityBridgeName) return;
            urls.forEach(function(url) { dagPendingPriorityUrls[url] = true; });
            if (dagPriorityFlushScheduled) return;
            dagPriorityFlushScheduled = true;
            window.setTimeout(function() {
              dagPriorityFlushScheduled = false;
              var pending = Object.keys(dagPendingPriorityUrls);
              dagPendingPriorityUrls = Object.create(null);
              for (var offset = 0; offset < pending.length; offset += $DagMaximumPriorityUrlsPerMessage) {
                try {
                  window.$DagImagePriorityBridgeName.postMessage(JSON.stringify({
                    action: '$DagImagePriorityAction',
                    page: location.href,
                    urls: pending.slice(offset, offset + $DagMaximumPriorityUrlsPerMessage)
                  }));
                } catch (_) {}
              }
            }, 0);
          }
          function dagPriorityUrl(raw) {
            try {
              var resolved = new URL(raw, document.baseURI);
              if (resolved.protocol !== 'https:') return raw;
              if (resolved.pathname.indexOf('/.dag-safe-image/') === 0) return resolved.href;
              resolved.hash = '';
              var encoded = window.btoa(unescape(encodeURIComponent(resolved.href)))
                .replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '');
              return resolved.origin + '/.dag-safe-image/' + encoded + '.png';
            } catch (_) {
              return raw;
            }
          }
          function dagPrioritySrcset(raw) {
            if (!raw) return raw;
            return raw.split(',').map(function(candidate) {
              var parts = candidate.trim().split(/\s+/);
              if (!parts[0]) return candidate;
              var resolved = dagResolvedHttpsImageUrl(parts[0], false);
              // The browser can choose a different responsive candidate after
              // layout, zoom or orientation changes. Every HTTPS candidate must
              // therefore stay behind the same synthetic classification broker.
              if (resolved) parts[0] = dagPriorityUrl(parts[0]);
              return parts.join(' ');
            }).join(', ');
          }
          var dagSyntheticRequestQueue = [];
          var dagSyntheticRequestActive = 0;
          function dagSyntheticRequestScore(image) {
            try {
              var rect = image.getBoundingClientRect();
              var viewportHeight = window.innerHeight || 0;
              var visible = rect.bottom >= 0 && rect.top <= viewportHeight &&
                rect.right >= 0 && rect.left <= (window.innerWidth || 0);
              var area = Math.max(0, rect.width * rect.height);
              if (!visible) return area;
              // Resolve the page in reading order. Sorting visible requests by
              // area alone starves small category/menu images below larger
              // product cards, leaving conspicuous blank controls for seconds.
              var top = Math.max(0, Math.min(viewportHeight, rect.top));
              return 1000000000000 + Math.max(0, viewportHeight - top) * 1000000 + area;
            } catch (_) {
              return 0;
            }
          }
          function dagReleaseSyntheticRequest(image) {
            if (!image || !image.hasAttribute('data-dag-priority-active-signature')) return;
            image.removeAttribute('data-dag-priority-active-signature');
            dagSyntheticRequestActive = Math.max(0, dagSyntheticRequestActive - 1);
            dagPumpSyntheticRequests();
          }
          function dagReleaseSyntheticSubtree(node) {
            if (!node || node.nodeType !== 1) return;
            var images = [];
            var seenImages = new WeakSet();
            function dagRememberReleasedImage(image) {
              if (!image || seenImages.has(image)) return;
              seenImages.add(image);
              images.push(image);
            }
            if (node.tagName && node.tagName.toLowerCase() === 'img') {
              dagRememberReleasedImage(node);
            }
            node.querySelectorAll &&
              node.querySelectorAll(
                'img[data-dag-priority-active-signature],img[data-dag-image-fast-lane="true"]'
              ).forEach(function(image) {
                dagRememberReleasedImage(image);
              });
            images.forEach(function(image) {
              image.removeAttribute('data-dag-priority-queued');
              dagReleaseSyntheticRequest(image);
              if (window.__dagImageObserver) window.__dagImageObserver.unobserve(image);
              if (window.__dagImageResizeObserver) window.__dagImageResizeObserver.unobserve(image);
              if (!image.isConnected &&
                  image.getAttribute('data-dag-image-fast-lane') === 'true' &&
                  dagImageFastLaneImages.delete(image)) {
                image.removeAttribute('data-dag-image-fast-lane');
                dagImageFastLaneRemaining = Math.min(
                  ${DagImageFastLanePolicy.MaximumImagesPerDocument},
                  dagImageFastLaneRemaining + 1
                );
              }
            });
          }
          function dagStartSyntheticRequest(item) {
            var image = item.image;
            if (!image || !image.isConnected ||
                image.getAttribute('data-dag-priority-source') !== item.signature) return false;
            image.removeAttribute('data-dag-priority-queued');
            image.setAttribute('data-dag-priority-active-signature', item.signature);
            dagSyntheticRequestActive += 1;
            window.setTimeout(function() {
              if (image.getAttribute('data-dag-priority-active-signature') !== item.signature) return;
              image.removeAttribute('data-dag-image-priority-pending');
              image.setAttribute('data-dag-image-terminal', 'unavailable');
              dagWriteInternalStyle(image, function() {
                image.style.removeProperty('visibility');
              });
              dagReleaseSyntheticRequest(image);
            }, ${DagImageRequestPolicy.SyntheticRequestTimeoutMillis});
            window.setTimeout(function() {
              if (!image.isConnected ||
                  image.getAttribute('data-dag-priority-source') !== item.signature ||
                  image.getAttribute('data-dag-priority-active-signature') !== item.signature) {
                dagReleaseSyntheticRequest(image);
                return;
              }
              var picture = image.closest && image.closest('picture');
              picture && picture.querySelectorAll('source').forEach(function(source) {
                var sourceSet =
                  source.getAttribute('srcset') ||
                  source.getAttribute('data-srcset') ||
                  source.getAttribute('data-lazy-srcset');
                if (sourceSet) {
                  dagSetInternalImageAttribute(
                    source,
                    'srcset',
                    dagPrioritySrcset(sourceSet)
                  );
                }
              });
              var imageSet =
                image.getAttribute('srcset') ||
                image.getAttribute('data-srcset') ||
                image.getAttribute('data-lazy-srcset');
              if (imageSet) {
                dagSetInternalImageAttribute(
                  image,
                  'srcset',
                  dagPrioritySrcset(imageSet)
                );
              }
              dagSetInternalImageAttribute(image, 'src', dagPriorityUrl(item.sources[0]));
            }, 20);
            return true;
          }
          function dagPumpSyntheticRequests() {
            if (!dagSyntheticRequestQueue.length ||
                dagSyntheticRequestActive >= ${DagImageRequestPolicy.MaximumConcurrentSyntheticRequests}) return;
            dagSyntheticRequestQueue.sort(function(left, right) { return right.score - left.score; });
            var attempts = dagSyntheticRequestQueue.length;
            while (attempts > 0 &&
                dagSyntheticRequestQueue.length &&
                dagSyntheticRequestActive < ${DagImageRequestPolicy.MaximumConcurrentSyntheticRequests}) {
              attempts -= 1;
              var item = dagSyntheticRequestQueue.shift();
              if (!item.image || !item.image.isConnected ||
                  item.image.getAttribute('data-dag-priority-source') !== item.signature) {
                continue;
              }
              if (item.image.hasAttribute('data-dag-priority-active-signature')) {
                dagSyntheticRequestQueue.push(item);
                continue;
              }
              dagStartSyntheticRequest(item);
            }
          }
          function dagRequestPrioritizedImage(image, sources) {
            if (!image || !sources.length) return;
            var signature = sources.join('|');
            var previousSignature = image.getAttribute('data-dag-priority-source') || '';
            var activeSource = image.currentSrc || image.getAttribute('src') || '';
            var alreadySynthetic = activeSource.indexOf('/.dag-safe-image/') >= 0;
            if (previousSignature === signature &&
                (alreadySynthetic || image.getAttribute('data-dag-image-priority-pending') === 'true')) return;
            image.setAttribute('data-dag-priority-source', signature);
            image.setAttribute('data-dag-image-priority-pending', 'true');
            dagQueuePriorityUrls(sources);
            if (image.getAttribute('data-dag-priority-events') !== 'true') {
              image.setAttribute('data-dag-priority-events', 'true');
              var completePriorityImage = function(event) {
                var activeSignature = image.getAttribute('data-dag-priority-active-signature') || '';
                if (!activeSignature) return;
                var activeUrl = image.currentSrc || image.getAttribute('src') || '';
                if (activeUrl.indexOf('/.dag-safe-image/') < 0) return;
                dagReleaseSyntheticRequest(image);
                if (image.getAttribute('data-dag-priority-source') !== activeSignature) {
                  dagQueueImage(image);
                  return;
                }
                image.removeAttribute('data-dag-image-priority-pending');
                if (event && event.type === 'error') {
                  image.setAttribute('data-dag-image-terminal', 'unavailable');
                } else {
                  image.removeAttribute('data-dag-image-terminal');
                }
                dagWriteInternalStyle(image, function() {
                  image.style.removeProperty('visibility');
                });
              };
              image.addEventListener('load', completePriorityImage);
              image.addEventListener('error', completePriorityImage);
            }
            if (previousSignature !== signature ||
                image.getAttribute('data-dag-priority-queued') !== 'true') {
              image.setAttribute('data-dag-priority-queued', 'true');
              dagSyntheticRequestQueue.push({
                image: image,
                sources: sources,
                signature: signature,
                score: dagSyntheticRequestScore(image)
              });
            }
            dagPumpSyntheticRequests();
          }
          function dagLoadImage(image) {
            if (!image || !image.isConnected) return;
            dagBlurIntimateImage(image);
            dagApplyExtraKosherImage(image);
            var source = dagHttpsImageSource(image);
            if (!source) {
              dagWriteInternalStyle(image, function() {
                image.style.setProperty('visibility', 'hidden', 'important');
              });
              image.setAttribute('data-dag-image-terminal', 'unavailable');
              return;
            }
            var prioritySources = dagCollectImageSources(image);
            if (prioritySources.length) dagRequestPrioritizedImage(image, prioritySources);
            image.removeAttribute('data-dag-held-src');
            dagWriteInternalStyle(image, function() {
              image.style.removeProperty('visibility');
            });
            var activeSource = image.currentSrc || image.getAttribute('src') || '';
            try {
              activeSource = activeSource ? new URL(activeSource, document.baseURI).href : '';
            } catch (_) {
              activeSource = '';
            }
            if (!activeSource || activeSource.indexOf('https://') !== 0) {
              dagSetInternalImageAttribute(image, 'src', source);
            }
            image.setAttribute('data-dag-image-ready', 'true');
            if (window.__dagVisualCalibrationEnabled) window.__dagRefreshCalibrationMarkers();
          }
          function dagRenderableImageRect(image) {
            var rect;
            try { rect = image.getBoundingClientRect(); } catch (_) { return null; }
            if (rect.width <= 0 || rect.height <= 0) return null;
            var current = image;
            var depth = 0;
            while (current && depth < 6) {
              var style;
              try { style = window.getComputedStyle(current); } catch (_) { return null; }
              if (style.display === 'none' || style.visibility === 'hidden' || style.opacity === '0') {
                return null;
              }
              current = current.parentElement;
              depth += 1;
            }
            if (rect.width < 4 || rect.height < 4) {
              var marker = [
                image.getAttribute('alt'),
                image.getAttribute('title'),
                image.getAttribute('width'),
                image.getAttribute('height')
              ].join('');
              var source = dagSelectedImageSource(image);
              if (!marker && !/\.(avif|gif|heic|heif|jpe?g|png|webp)(?:[?#.]|$)/i.test(source)) {
                return null;
              }
            }
            return rect;
          }
          function dagFastLaneStyleAllowsImage(image) {
            var current = image;
            var depth = 0;
            while (current && depth < 6) {
              var style;
              try { style = window.getComputedStyle(current); } catch (_) { return false; }
              if (style.display === 'none' || style.visibility === 'hidden' || style.opacity === '0') {
                return false;
              }
              current = current.parentElement;
              depth += 1;
            }
            return true;
          }
          function dagTryImageFastLane(image, fromDynamicSubtree) {
            if (!image || !image.isConnected) return false;
            if (dagImageFastLaneImages.has(image)) {
              dagLoadImage(image);
              return true;
            }
            if (dagImageFastLaneRemaining <= 0) return false;
            var loading = (image.getAttribute('loading') || 'auto').trim().toLowerCase();
            if (loading !== 'auto' && loading !== 'eager') return false;
            if (!dagFastLaneStyleAllowsImage(image)) return false;
            var source = dagSelectedImageSource(image);
            if (!source || source.indexOf('https://') !== 0) return false;
            var rect;
            try { rect = image.getBoundingClientRect(); } catch (_) { return false; }
            var hasRenderableGeometry = rect.width > 0 && rect.height > 0;
            if (hasRenderableGeometry && fromDynamicSubtree !== true) return false;
            dagImageFastLaneImages.add(image);
            dagImageFastLaneRemaining -= 1;
            image.setAttribute('data-dag-image-fast-lane', 'true');
            dagLoadImage(image);
            return true;
          }
          function dagPrimeDynamicImageFastLane(node) {
            if (!node || node.nodeType !== 1 || dagImageFastLaneRemaining <= 0) return;
            if (node.tagName && node.tagName.toLowerCase() === 'img') {
              dagTryImageFastLane(node, true);
              return;
            }
            if (!node.querySelectorAll) return;
            var images = node.querySelectorAll('img');
            for (var index = 0; index < images.length && dagImageFastLaneRemaining > 0; index += 1) {
              dagTryImageFastLane(images[index], true);
            }
          }
          function dagImageArea(image) {
            var rect = dagRenderableImageRect(image);
            return rect ? rect.width * rect.height : 0;
          }
          function dagQueueImage(image) {
            if (!image) return;
            dagBlurIntimateImage(image);
            dagApplyExtraKosherImage(image);
            // The first few eager/auto images may have no intrinsic geometry
            // yet, or arrive nested inside a large hydrated subtree. Start
            // those through the same synthetic broker before the bounded DOM
            // walk reaches them; every later image keeps the ordinary lazy
            // viewport path.
            if (dagTryImageFastLane(image, false)) return;
            if (window.ResizeObserver) {
              if (!window.__dagImageResizeObserver) {
                window.__dagImageResizeObserver = new ResizeObserver(function(entries) {
                  entries.forEach(function(entry) {
                    var target = entry.target;
                    if (!target || !target.isConnected || !dagRenderableImageRect(target)) return;
                    window.__dagImageResizeObserver.unobserve(target);
                    dagQueueImage(target);
                  });
                });
              }
              if (dagRenderableImageRect(image)) {
                window.__dagImageResizeObserver.unobserve(image);
              } else {
                window.__dagImageResizeObserver.observe(image);
              }
            }
            if (window.IntersectionObserver) {
              if (image.getAttribute('loading') !== 'lazy') {
                dagSetInternalImageAttribute(image, 'loading', 'lazy');
              }
              if (!window.__dagImageObserver) {
                var dagPrefetchMargin = Math.max(
                  (window.innerHeight || 0) * ${DagViewportReadinessPolicy.PrefetchViewportCount},
                  0
                );
                window.__dagImageObserver = new IntersectionObserver(function(entries) {
                  Array.from(entries).sort(function(left, right) {
                    return dagImageArea(right.target) - dagImageArea(left.target);
                  }).forEach(function(entry) {
                    if (!entry.isIntersecting) return;
                    if (!dagRenderableImageRect(entry.target)) return;
                    window.__dagImageObserver.unobserve(entry.target);
                    dagLoadImage(entry.target);
                  });
                }, { rootMargin: dagPrefetchMargin + 'px 0px' });
              }
              // A responsive app can insert an image while its container is
              // still display:none/zero-sized and reveal it later. Chromium
              // may keep that target in the same non-renderable intersection
              // state, so merely observing it again does not enqueue the real
              // source. Reset the observation whenever the bounded DOM queue
              // revisits the node (for example after a menu/carousel/class
              // change) so the now-visible geometry gets a fresh callback.
              window.__dagImageObserver.unobserve(image);
              window.__dagImageObserver.observe(image);
              return;
            }
            var rect = dagRenderableImageRect(image);
            if (rect &&
                rect.bottom >= 0 && rect.top <= (window.innerHeight || 0) * 2) {
              dagLoadImage(image);
            }
          }
          window.__dagPrepareViewportImages = function(ignorePending) {
            var backgroundsReady = !window.__dagPrepareVisibleBackgrounds ||
              window.__dagPrepareVisibleBackgrounds();
            var viewportHeight = Math.max(window.innerHeight || 0, 1);
            var preparationLimit = viewportHeight * ${DagViewportReadinessPolicy.PreparedViewportCount};
            var viewportWidth = Math.max(window.innerWidth || 0, 1);
            var total = 0;
            var pending = 0;
            var candidates = [];
            document.querySelectorAll('img').forEach(function(image) {
              if (!image || !image.isConnected) return;
              var rect = dagRenderableImageRect(image);
              if (!rect) return;
              if (rect.bottom < 0 || rect.top > preparationLimit ||
                  rect.right < 0 || rect.left > viewportWidth) return;
              candidates.push({ image: image, rect: rect });
            });
            candidates.sort(function(left, right) {
              return (right.rect.width * right.rect.height) -
                (left.rect.width * left.rect.height);
            }).forEach(function(candidate) {
              var image = candidate.image;
              var rect = candidate.rect;
              var visibleNow = rect.top <= viewportHeight && rect.bottom >= 0;
              dagLoadImage(image);
              if (!visibleNow || rect.width < 24 || rect.height < 24) return;
              total += 1;
              if (image.hasAttribute('data-dag-image-terminal')) return;
              if (image.getAttribute('data-dag-image-priority-pending') === 'true') {
                if (!ignorePending) pending += 1;
                return;
              }
              if (image.complete && image.naturalWidth > 0) return;
              if (image.complete && image.naturalWidth === 0) {
                if (!ignorePending) pending += 1;
                return;
              }
              if (ignorePending) return;
              pending += 1;
            });
            return JSON.stringify({
              total: total + (backgroundsReady ? 0 : 1),
              pending: pending + (backgroundsReady ? 0 : 1)
            });
          };
          var dagBackgroundStates = new WeakMap();
          var dagBackgroundLoads = Object.create(null);
          var dagBackgroundLoadQueue = [];
          var dagBackgroundLoadActive = 0;
          function dagPumpBackgroundLoads() {
            while (dagBackgroundLoadQueue.length &&
                dagBackgroundLoadActive < ${DagImageRequestPolicy.MaximumConcurrentBackgroundRequests}) {
              var item = dagBackgroundLoadQueue.shift();
              dagBackgroundLoadActive += 1;
              var preload = new Image();
              var finish = function() {
                preload.onload = null;
                preload.onerror = null;
                dagBackgroundLoadActive = Math.max(0, dagBackgroundLoadActive - 1);
                item.resolve();
                dagPumpBackgroundLoads();
              };
              preload.onload = finish;
              preload.onerror = finish;
              preload.src = dagPriorityUrl(item.url);
            }
          }
          function dagPreloadBackground(url) {
            if (dagBackgroundLoads[url]) return dagBackgroundLoads[url];
            dagQueuePriorityUrls([url]);
            dagBackgroundLoads[url] = new Promise(function(resolve) {
              dagBackgroundLoadQueue.push({ url: url, resolve: resolve });
              dagPumpBackgroundLoads();
            });
            return dagBackgroundLoads[url];
          }
          function dagBackgroundDetails(background) {
            var urls = [];
            var valid = true;
            if (/url\(\s*["']?\s*(?:$DagUnsafeCssImageSchemeNamesJavaScriptPattern):/i.test(background)) {
              return { valid: false, urls: [], rewritten: 'none' };
            }
            var rewritten = background.replace(/url\(["']?([^"')]+)["']?\)/g, function(_, raw) {
              if (/$DagUnsafeCssImageSchemeJavaScriptPattern/i.test((raw || '').trim())) {
                valid = false;
                return 'none';
              }
              var resolved = dagResolvedHttpsImageUrl(raw, false);
              if (!resolved) {
                valid = false;
                return 'none';
              }
              urls.push(resolved);
              return 'url("' + dagPriorityUrl(resolved) + '")';
            });
            return { valid: valid, urls: urls, rewritten: rewritten };
          }
          function dagBackgroundIsVisible(node) {
            try {
              var rect = node.getBoundingClientRect();
              return rect.width > 0 && rect.height > 0 &&
                rect.bottom >= 0 && rect.top <= (window.innerHeight || 0) &&
                rect.right >= 0 && rect.left <= (window.innerWidth || 0);
            } catch (_) {
              return false;
            }
          }
          function dagObserveBackground(node) {
            if (!window.IntersectionObserver) return;
            if (!window.__dagBackgroundObserver) {
              window.__dagBackgroundObserver = new IntersectionObserver(function(entries) {
                entries.forEach(function(entry) {
                  if (!entry.isIntersecting) return;
                  window.__dagBackgroundObserver.unobserve(entry.target);
                  dagSecureBackground(entry.target, true);
                });
              });
            }
            window.__dagBackgroundObserver.observe(node);
          }
          function dagSecurePseudoBackground(node, pseudo, blockedAttribute) {
            var background = '';
            try {
              background = window.getComputedStyle(node, pseudo).backgroundImage || '';
            } catch (_) {}
            if (!background || background === 'none') return;
            var details = dagBackgroundDetails(background);
            if (!details.valid ||
                (${DagBackgroundSecurityPolicy.PseudoElementUrlsRequireFailClosed} &&
                  details.urls.length > 0)) {
              node.setAttribute(blockedAttribute, 'true');
            }
          }
          function dagSecureBackground(node, forceLoad) {
            if (!node || node.nodeType !== 1) return;
            dagSecurePseudoBackground(
              node,
              '::before',
              '$DagBlockedBeforeBackgroundAttribute'
            );
            dagSecurePseudoBackground(
              node,
              '::after',
              '$DagBlockedAfterBackgroundAttribute'
            );
            var existingState = dagBackgroundStates.get(node);
            if (existingState && (existingState.phase === 'loading' || existingState.phase === 'applying')) {
              var controlledInline = node.style.getPropertyValue('background-image') || '';
              if (controlledInline === 'none' ||
                  controlledInline.indexOf('/.dag-safe-image/') >= 0) return;
            }
            var background = '';
            try { background = window.getComputedStyle(node).backgroundImage || ''; } catch (_) {}
            if (!background || background === 'none') return;
            if (window.__dagExtraKosherEnabled === true &&
                !(node.closest && node.closest('header,nav,button,[role="button"],[role="navigation"]')) &&
                !/\b(logo|icon|sprite|flag|payment)\b/.test(dagNormalizedText([node.className, node.id].join(' ')))) {
              dagWriteInternalStyle(node, function() {
                node.style.setProperty('background-image', 'none', 'important');
              });
              node.setAttribute('data-dag-extra-kosher-background', 'true');
              return;
            }
            var details = dagBackgroundDetails(background);
            if (!details.valid) {
              dagWriteInternalStyle(node, function() {
                node.style.setProperty('background-image', 'none', 'important');
              });
              return;
            }
            if (!details.urls.length) return;
            if (existingState && existingState.signature === background &&
                existingState.phase === 'ready') return;
            if (forceLoad !== true && !dagBackgroundIsVisible(node)) {
              dagBackgroundStates.set(node, {
                signature: background,
                phase: 'observed'
              });
              dagObserveBackground(node);
              return;
            }
            var token = {};
            var originalInlineValue = node.style.getPropertyValue('background-image');
            var originalInlinePriority = node.style.getPropertyPriority('background-image');
            dagBackgroundStates.set(node, {
              signature: background,
              phase: 'loading',
              token: token
            });
            dagWriteInternalStyle(node, function() {
              node.style.setProperty('background-image', 'none', 'important');
            });
            Promise.all(details.urls.map(dagPreloadBackground)).then(function() {
              var state = dagBackgroundStates.get(node);
              if (!node.isConnected || !state || state.token !== token) return;
              state.phase = 'applying';
              dagWriteInternalStyle(node, function() {
                node.style.setProperty('background-image', details.rewritten, 'important');
              });
              window.requestAnimationFrame(function() {
                var current = dagBackgroundStates.get(node);
                if (!node.isConnected || !current || current.token !== token) return;
                dagWriteInternalStyle(node, function() {
                  if (originalInlineValue) {
                    node.style.setProperty(
                      'background-image',
                      originalInlineValue,
                      originalInlinePriority
                    );
                  } else {
                    node.style.removeProperty('background-image');
                  }
                });
                current.phase = 'ready';
              });
            });
          }
          function dagHandleImageSourceMutation(node, attributeName) {
            if (!node || !attributeName) return;
            if (dagConsumeInternalImageMutation(node, attributeName)) return;
            if (!dagSourceAttributeNames[attributeName]) return;
            var tag = node.tagName ? node.tagName.toLowerCase() : '';
            var image =
              tag === 'img' ? node :
              (tag === 'source' && node.parentElement &&
                node.parentElement.tagName.toLowerCase() === 'picture'
                ? node.parentElement.querySelector('img') : null);
            if (!image) return;
            var observed = dagResolvedHttpsImageUrl(node.getAttribute(attributeName), false);
            if (observed) {
              image.setAttribute('data-dag-observed-source', observed);
            } else {
              image.removeAttribute('data-dag-observed-source');
            }
            // Invalidate and release the old slot before scheduling the new
            // responsive source. Otherwise three rapid src/srcset mutations can
            // permanently exhaust the JavaScript-side request budget.
            $DagImageSourceMutationResetJavaScript
            dagReleaseSyntheticRequest(image);
            dagQueueImage(image);
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
          function dagReplaceActiveMediaElement(node) {
            if (!node) return;
            var parent = node.parentNode;
            var rect = null;
            var computed = null;
            try {
              rect = node.getBoundingClientRect();
              computed = window.getComputedStyle(node);
            } catch (_) {}
            var tag = node.tagName ? node.tagName.toLowerCase() : '';
            if (tag === 'canvas') {
              try {
                node.width = 0;
                node.height = 0;
              } catch (_) {}
            } else {
              try {
                node.muted = true;
                node.pause();
              } catch (_) {}
              node.removeAttribute('autoplay');
              node.removeAttribute('loop');
              node.removeAttribute('src');
              node.querySelectorAll('source').forEach(function(source) {
                source.removeAttribute('src');
                source.removeAttribute('srcset');
              });
              try {
                node.srcObject = null;
                node.load();
              } catch (_) {}
            }
            if (!parent) {
              node.remove();
              return;
            }
            var inline =
              computed && computed.display &&
              computed.display.indexOf('inline') === 0;
            var placeholder = document.createElement(inline ? 'span' : 'div');
            placeholder.setAttribute('data-dag-media-placeholder', 'true');
            placeholder.setAttribute('aria-hidden', 'true');
            placeholder.style.setProperty('display', inline ? 'inline-block' : 'block', 'important');
            placeholder.style.setProperty('box-sizing', 'border-box', 'important');
            placeholder.style.setProperty('visibility', 'hidden', 'important');
            placeholder.style.setProperty('pointer-events', 'none', 'important');
            placeholder.style.setProperty('overflow', 'hidden', 'important');
            if (rect && rect.width > 0 && rect.height > 0) {
              placeholder.style.setProperty('width', rect.width + 'px', 'important');
              placeholder.style.setProperty('height', rect.height + 'px', 'important');
            }
            if (computed) {
              placeholder.style.marginTop = computed.marginTop;
              placeholder.style.marginRight = computed.marginRight;
              placeholder.style.marginBottom = computed.marginBottom;
              placeholder.style.marginLeft = computed.marginLeft;
              placeholder.style.verticalAlign = computed.verticalAlign;
              placeholder.style.gridArea = computed.gridArea;
              placeholder.style.alignSelf = computed.alignSelf;
            }
            parent.replaceChild(placeholder, node);
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
              dagReplaceActiveMediaElement(node);
              return;
            }
            if (tag === 'object' || tag === 'embed' || tag === 'image' || tag === 'foreignobject') {
              node.remove();
              return;
            }
            if (tag === 'use') {
              var svgReference =
                node.getAttribute('href') ||
                node.getAttribute('xlink:href') ||
                '';
              if (svgReference && svgReference.charAt(0) !== '#') node.remove();
              return;
            }
            if (tag === 'source') {
              var parentTag = node.parentElement && node.parentElement.tagName ? node.parentElement.tagName.toLowerCase() : '';
              if (parentTag === 'video' || parentTag === 'audio') {
                dagReplaceActiveMediaElement(node.parentElement);
                return;
              }
              if (parentTag === 'picture') {
                var responsiveAttribute =
                  node.hasAttribute('srcset') ? 'srcset' :
                  (node.hasAttribute('data-srcset') ? 'data-srcset' :
                    (node.hasAttribute('data-lazy-srcset') ? 'data-lazy-srcset' : ''));
                if (responsiveAttribute) dagHandleImageSourceMutation(node, responsiveAttribute);
                var responsiveImage = node.parentElement.querySelector('img');
                if (responsiveImage) dagQueueImage(responsiveImage);
              }
              return;
            }
            if (tag === 'img') {
              dagQueueImage(node);
            }
            dagSecureBackground(node);
            if (includeDescendants !== true) return;
            node.querySelectorAll && node.querySelectorAll(
              'video,audio,video source,audio source,canvas,iframe,object,embed,svg image,svg foreignObject,svg use'
            ).forEach(dagSecureNode);
            node.querySelectorAll && node.querySelectorAll('img').forEach(function(image) {
              dagQueueImage(image);
            });
          }
          var dagSecurityQueue = [];
          var dagSecurityQueueIndex = 0;
          var dagSecurityQueued = new WeakSet();
          var dagSecurityFlushScheduled = false;
          var dagBackgroundRescanAgain = false;
          function dagSecureVisibleBackgroundsNow() {
            if (!document.elementFromPoint) return;
            var viewportWidth = Math.max(window.innerWidth || 0, 1);
            var viewportHeight = Math.max(window.innerHeight || 0, 1);
            var remaining = ${DagBackgroundSecurityPolicy.MaximumVisibleNodesPerSynchronousPass};
            var seen = new WeakSet();
            for (var row = 0;
                row < ${DagBackgroundSecurityPolicy.VisibleSampleRows} && remaining > 0;
                row += 1) {
              for (var column = 0;
                  column < ${DagBackgroundSecurityPolicy.VisibleSampleColumns} && remaining > 0;
                  column += 1) {
                var x = (column + 0.5) * viewportWidth /
                  ${DagBackgroundSecurityPolicy.VisibleSampleColumns};
                var y = (row + 0.5) * viewportHeight /
                  ${DagBackgroundSecurityPolicy.VisibleSampleRows};
                var elements = document.elementsFromPoint ?
                  document.elementsFromPoint(x, y) : [document.elementFromPoint(x, y)];
                for (var index = 0; index < elements.length && remaining > 0; index += 1) {
                  var node = elements[index];
                  if (!node || seen.has(node)) continue;
                  seen.add(node);
                  remaining -= 1;
                  dagSecureBackground(node, true);
                }
              }
            }
          }
          function dagRunVisibleBackgroundPass() {
            dagSecureVisibleBackgroundsNow();
          }
          function dagRunTargetedVisibleBackgroundPass(nodes) {
            var seen = new WeakSet();
            (nodes || []).forEach(function(node) {
              if (!node || node.nodeType !== 1 || seen.has(node)) return;
              seen.add(node);
              if (dagBackgroundIsVisible(node)) dagSecureBackground(node, true);
            });
            dagSecureVisibleBackgroundsNow();
          }
          function dagFlushSecurityBatch() {
            var remaining = ${DagDomSecurityBatchPolicy.MaximumNodesPerFrame};
            while (remaining > 0 && dagSecurityQueueIndex < dagSecurityQueue.length) {
              var current = dagSecurityQueue[dagSecurityQueueIndex++];
              remaining -= 1;
              if (current) dagSecurityQueued.delete(current);
              if (!current || !current.isConnected) continue;
              dagSecureNode(current, false);
              if (!current.isConnected) continue;
              var child = current.firstElementChild;
              while (child) {
                dagQueueSecurityNode(child);
                child = child.nextElementSibling;
              }
            }
            if (dagSecurityQueueIndex >= dagSecurityQueue.length) {
              dagSecurityQueue = [];
              dagSecurityQueueIndex = 0;
              if (dagBackgroundRescanAgain) {
                dagBackgroundRescanAgain = false;
                dagQueueSecurityNode(document.documentElement);
              }
            }
            dagScheduleSecurityFlush();
          }
          function dagScheduleSecurityFlush() {
            if (dagSecurityFlushScheduled || !dagSecurityQueue.length) return;
            dagSecurityFlushScheduled = true;
            window.requestAnimationFrame(function() {
              dagSecurityFlushScheduled = false;
              dagFlushSecurityBatch();
            });
          }
          function dagQueueSecurityNode(node) {
            if (!node || node.nodeType !== 1 || dagSecurityQueued.has(node)) return false;
            dagSecurityQueued.add(node);
            dagSecurityQueue.push(node);
            dagScheduleSecurityFlush();
            return true;
          }
          function dagRequestBackgroundNodes(nodes, runSynchronously) {
            (nodes || []).forEach(function(node) {
              dagQueueSecurityNode(node);
            });
            if (runSynchronously === true) dagRunTargetedVisibleBackgroundPass(nodes);
          }
          function dagRequestBackgroundRescan(runSynchronously) {
            if (!dagQueueSecurityNode(document.documentElement)) {
              dagBackgroundRescanAgain = true;
            }
            if (runSynchronously === true) dagRunVisibleBackgroundPass();
          }
          window.__dagPrepareVisibleBackgrounds = function() {
            return ${DagBackgroundSecurityPolicy.VisibleSampleMakesViewportReady};
          };
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
              var reviewBlur = decision !== 'allowed' || image.getAttribute('data-dag-kosher-blurred') === 'true';
              var marker = document.createElement('button');
              marker.type = 'button';
              marker.setAttribute('data-dag-calibration-marker', 'true');
              marker.setAttribute('aria-label', reviewBlur ? 'Revisar posible falso positivo' : 'Marcar foto como inapropiada');
              marker.textContent = reviewBlur ? 'R' : '×';
              marker.style.cssText = 'position:fixed;z-index:2147483647;width:34px;height:34px;border:2px solid white;border-radius:17px;background:' + (reviewBlur ? '#087f8c' : '#d71920') + ';color:white;font:700 ' + (reviewBlur ? '17px/29px' : '25px/27px') + ' sans-serif;padding:0;box-shadow:0 2px 7px rgba(0,0,0,.55);';
              marker.style.left = Math.max(4, rect.right - 38) + 'px';
              marker.style.top = Math.max(4, rect.top + 4) + 'px';
              marker.addEventListener('click', function(event) {
                event.preventDefault();
                event.stopPropagation();
                image.setAttribute('data-dag-manually-blocked', 'true');
                if (!reviewBlur) {
                  dagWriteInternalStyle(image, function() {
                    image.style.setProperty('filter', 'blur(48px) saturate(0.05) brightness(0.82)', 'important');
                    image.style.setProperty('transform', 'scale(1.14)', 'important');
                  });
                }
                marker.remove();
                try {
                  window.$DagCalibrationBridgeName.postMessage(JSON.stringify({ action: reviewBlur ? 'review_blur' : 'block', url: source }));
                } catch (_) {}
              }, true);
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
          // Remove active visual channels and enqueue viewport images immediately.
          // The document-start CSP blocks non-HTTPS image schemes at Chromium
          // level; this bounded pass only rewrites HTTPS backgrounds through
          // the native classifier without delaying or hiding page UI.
          dagSecureNode(document.documentElement, true);
          dagRequestBackgroundRescan(
            ${DagBackgroundSecurityPolicy.InitialScanRunsSynchronously}
          );
          if (document.documentElement) {
            if (window.__dagExtraKosherEnabled) {
              document.documentElement.setAttribute('data-dag-extra-kosher', 'true');
            } else {
              document.documentElement.removeAttribute('data-dag-extra-kosher');
            }
          }
          dagEnsureSafeStyle();
          function dagIsInternalStyle(styleNode) {
            if (!styleNode || !styleNode.id) return false;
            return styleNode.id === 'dag-safe-style' ||
              styleNode.id === 'dag-content-guard';
          }
          function dagStyleElementForNode(node) {
            if (!node) return null;
            var element = node.nodeType === 1 ? node : node.parentElement;
            if (!element || !element.tagName) return null;
            return element.tagName.toLowerCase() === 'style' ? element : null;
          }
          function dagIsStylesheetLink(node) {
            if (!dagIsStylesheetLinkElement(node)) return false;
            return (node.getAttribute('rel') || '').toLowerCase().split(/\s+/)
              .indexOf('stylesheet') >= 0;
          }
          function dagIsStylesheetLinkElement(node) {
            return !!node && node.nodeType === 1 && !!node.tagName &&
              node.tagName.toLowerCase() === 'link';
          }
          function dagShouldScanAddedSubtree(node) {
            if (!node || node.nodeType !== 1) return false;
            if (dagStyleElementForNode(node) || dagIsStylesheetLinkElement(node)) return false;
            if (node.getAttribute('data-dag-calibration-marker') === 'true' ||
                node.getAttribute('data-dag-media-placeholder') === 'true') return false;
            return true;
          }
          function dagStylesheetLinksInSubtree(node) {
            var links = [];
            if (!node || node.nodeType !== 1) return links;
            if (dagIsStylesheetLink(node)) links.push(node);
            node.querySelectorAll &&
              node.querySelectorAll('link[rel~="stylesheet" i]').forEach(function(link) {
                links.push(link);
              });
            return links;
          }
          var dagStylesheetLinkRescans = new WeakMap();
          function dagStopTrackingStylesheetLink(link, rescan) {
            var token = link && dagStylesheetLinkRescans.get(link);
            if (!token || token.finished) return;
            token.finished = true;
            link.removeEventListener('load', token.finish);
            link.removeEventListener('error', token.finish);
            if (token.timeout) window.clearTimeout(token.timeout);
            dagStylesheetLinkRescans.delete(link);
            if (rescan !== false) {
              dagRequestBackgroundRescan(
                ${DagBackgroundSecurityPolicy.StylesheetScanRunsSynchronously}
              );
            }
          }
          function dagTrackStylesheetLink(link) {
            if (!dagIsStylesheetLink(link) || !link.isConnected) return;
            dagStopTrackingStylesheetLink(link, false);
            var token = {
              finished: false,
              finish: null,
              timeout: 0
            };
            token.finish = function() {
              dagStopTrackingStylesheetLink(link, true);
            };
            dagStylesheetLinkRescans.set(link, token);
            link.addEventListener('load', token.finish);
            link.addEventListener('error', token.finish);
            token.timeout = window.setTimeout(
              token.finish,
              ${DagBackgroundSecurityPolicy.StylesheetLoadRescanTimeoutMillis}
            );
            window.setTimeout(function() {
              if (!token.finished && link.sheet) token.finish();
            }, 0);
          }
          function dagMutationTouchesPageStyle(record) {
            var targetStyle = dagStyleElementForNode(record && record.target);
            if (targetStyle && !dagIsInternalStyle(targetStyle)) return true;
            if (dagIsStylesheetLink(record && record.target)) return true;
            if (dagIsStylesheetLinkElement(record && record.target) &&
                $DagStylesheetLinkMutationAttributeCheckJavaScript) return true;
            if (!record) return false;
            var changedNodeLists = [record.addedNodes, record.removedNodes];
            for (var listIndex = 0; listIndex < changedNodeLists.length; listIndex += 1) {
              var changedNodes = changedNodeLists[listIndex];
              if (!changedNodes) continue;
              for (var index = 0; index < changedNodes.length; index += 1) {
                var changed = changedNodes[index];
                var changedStyle = dagStyleElementForNode(changed);
                if (changedStyle && !dagIsInternalStyle(changedStyle)) return true;
                if (!changed || changed.nodeType !== 1 || !changed.querySelector) continue;
                if (dagStylesheetLinksInSubtree(changed).length) return true;
                var nestedStyle = changed.querySelector(
                  'style:not(#dag-safe-style):not(#dag-content-guard)'
                );
                if (nestedStyle) return true;
              }
            }
            return false;
          }
          document.querySelectorAll('link[rel~="stylesheet" i]').forEach(function(link) {
            dagTrackStylesheetLink(link);
          });
          {
            function dagMutationText(node) {
              if (!node) return '';
              var element = node.nodeType === 1 ? node : node.parentElement;
              if (!element || !element.isConnected) return '';
              if (element.id === 'dag-content-guard' ||
                  (element.closest &&
                    element.closest('script,style,noscript,template,head,svg,canvas,[aria-hidden="true"]'))) {
                return '';
              }
              return (node.textContent || '').substring(0, 2000);
            }
            var dagSafeObserver = new MutationObserver(function(records) {
              var calibrationNeedsRefresh = false;
              var contentChanged = false;
              var urgentContentChange = false;
              var rootReplaced = false;
              var stylesheetChanged = false;
              var addedBackgroundRoots = [];
              // Release every removed fast-lane node before priming any
              // replacement from this hydration batch.
              records.forEach(function(record) {
                stylesheetChanged =
                  stylesheetChanged || dagMutationTouchesPageStyle(record);
                record.removedNodes.forEach(function(node) {
                  dagReleaseSyntheticSubtree(node);
                  var removedLinks = dagStylesheetLinksInSubtree(node);
                  if (removedLinks.length) stylesheetChanged = true;
                  removedLinks.forEach(function(link) {
                    dagStopTrackingStylesheetLink(link, false);
                  });
                });
              });
              records.forEach(function(record) {
                record.addedNodes.forEach(function(node) {
                  if ($DagDocumentRootReplacementJavaScript) {
                    rootReplaced = true;
                    dagSetContentGuard();
                    dagEnsureSafeStyle();
                  }
                  dagPrimeDynamicImageFastLane(node);
                  dagStylesheetLinksInSubtree(node).forEach(dagTrackStylesheetLink);
                  if (dagShouldScanAddedSubtree(node)) addedBackgroundRoots.push(node);
                  if (!(node.nodeType === 1 && node.getAttribute('data-dag-calibration-marker') === 'true')) {
                    calibrationNeedsRefresh = true;
                    if (!rootReplaced) {
                      var nodeText = dagMutationText(node);
                      if (nodeText.trim()) {
                        contentChanged = true;
                        urgentContentChange = urgentContentChange || dagDynamicRiskPattern.test(nodeText);
                      }
                    }
                  }
                });
                if (record.type === 'characterData') {
                  var changedText = dagMutationText(record.target);
                  if (changedText.trim()) {
                    contentChanged = true;
                    urgentContentChange = urgentContentChange || dagDynamicRiskPattern.test(changedText);
                  }
                }
                if (record.type === 'attributes') {
                  if (dagIsStylesheetLinkElement(record.target) &&
                      $DagStylesheetLinkMutationAttributeCheckJavaScript) {
                    dagStopTrackingStylesheetLink(record.target, false);
                    if (dagIsStylesheetLink(record.target)) {
                      dagTrackStylesheetLink(record.target);
                    }
                  }
                  dagHandleImageSourceMutation(record.target, record.attributeName);
                  dagSecureNode(record.target, false);
                  if (record.target.getAttribute('data-dag-calibration-marker') !== 'true') {
                    calibrationNeedsRefresh = true;
                  }
                }
              });
              // Observing the stable Document keeps this callback alive when an
              // SPA uses documentElement.replaceWith(...) or document.open().
              // Restore sanitizer state after a root replacement and enqueue
              // background rewriting without synchronously walking the tree.
              if (rootReplaced && document.documentElement) {
                if (window.__dagExtraKosherEnabled) {
                  document.documentElement.setAttribute('data-dag-extra-kosher', 'true');
                }
                dagSecureNode(document.documentElement, false);
                dagInstallImageSchemeCsp();
                dagRequestBackgroundRescan(
                  ${DagBackgroundSecurityPolicy.StylesheetScanRunsSynchronously}
                );
                contentChanged = true;
                urgentContentChange = true;
              } else {
                dagEnsureSafeStyle();
                if (dagContentRoutePending || dagContentUrgentPending) dagSetContentGuard();
              }
              if (calibrationNeedsRefresh && window.__dagVisualCalibrationEnabled) {
                window.requestAnimationFrame(window.__dagRefreshCalibrationMarkers);
              }
              if (stylesheetChanged && !rootReplaced) {
                dagRequestBackgroundRescan(
                  ${DagBackgroundSecurityPolicy.StylesheetScanRunsSynchronously}
                );
              } else if (addedBackgroundRoots.length && !rootReplaced) {
                dagRequestBackgroundNodes(
                  addedBackgroundRoots,
                  ${DagBackgroundSecurityPolicy.AddedSubtreeScanRunsSynchronously}
                );
              }
              if (contentChanged) dagScheduleContentCheck(false, urgentContentChange);
            });
            dagSafeObserver.observe($DagSanitizerObserverTargetJavaScript, {
              childList: true,
              subtree: true,
              characterData: true,
              attributes: true,
              attributeFilter: [
                $DagObservedSecurityAttributesJavaScript
              ]
            });
          }
          {
            var dagStyleQueue = [];
            var dagStyleQueueIndex = 0;
            var dagStyleQueued = new WeakSet();
            var dagLastLayoutSubtreeScan = new WeakMap();
            var dagStyleFlushScheduled = false;
            var dagDeferredLayoutRoots = [];
            var dagDeferredLayoutQueued = new WeakSet();
            var dagDeferredLayoutTimer = 0;
            function dagScheduleDeferredLayoutFlush() {
              if (dagDeferredLayoutTimer || !dagDeferredLayoutRoots.length) return;
              var now = window.performance ? window.performance.now() : Date.now();
              var nextDue = dagDeferredLayoutRoots.reduce(function(earliest, item) {
                return Math.min(earliest, item.due);
              }, Number.MAX_SAFE_INTEGER);
              dagDeferredLayoutTimer = window.setTimeout(function() {
                dagDeferredLayoutTimer = 0;
                var currentNow = window.performance ? window.performance.now() : Date.now();
                var ready = [];
                var pending = [];
                dagDeferredLayoutRoots.forEach(function(item) {
                  if (item.due <= currentNow) {
                    dagDeferredLayoutQueued.delete(item.node);
                    if (item.node && item.node.isConnected) {
                      dagLastLayoutSubtreeScan.set(item.node, currentNow);
                      ready.push(item.node);
                    }
                  } else {
                    pending.push(item);
                  }
                });
                dagDeferredLayoutRoots = pending;
                if (ready.length) dagRequestBackgroundNodes(ready, false);
                dagScheduleDeferredLayoutFlush();
              }, Math.max(0, nextDue - now));
            }
            function dagDeferLayoutSecurityScan(node, delay) {
              if (!node || dagDeferredLayoutQueued.has(node)) return;
              dagDeferredLayoutQueued.add(node);
              dagDeferredLayoutRoots.push({
                node: node,
                due: (window.performance ? window.performance.now() : Date.now()) + delay
              });
              dagScheduleDeferredLayoutFlush();
            }
            function dagScheduleStyleFlush() {
              if (dagStyleFlushScheduled || !dagStyleQueue.length) return;
              dagStyleFlushScheduled = true;
              window.requestAnimationFrame(function() {
                dagStyleFlushScheduled = false;
                var remaining = ${DagDomSecurityBatchPolicy.MaximumNodesPerFrame};
                while (remaining > 0 && dagStyleQueueIndex < dagStyleQueue.length) {
                  var node = dagStyleQueue[dagStyleQueueIndex++];
                  remaining -= 1;
                  dagStyleQueued.delete(node);
                  if (node && node.isConnected) dagSecureBackground(node);
                }
                if (dagStyleQueueIndex >= dagStyleQueue.length) {
                  dagStyleQueue = [];
                  dagStyleQueueIndex = 0;
                }
                dagScheduleStyleFlush();
              });
            }
            var dagStyleObserver = new MutationObserver(function(records) {
              var visibleTextMayHaveChanged = false;
              var backgroundTargets = [];
              var backgroundTargetSet = new WeakSet();
              var immediateLayoutRoots = [];
              var layoutDecisionMade = new WeakSet();
              records.forEach(function(record) {
                var node = record.target;
                if (!node) return;
                if (record.attributeName === 'style' &&
                    dagConsumeInternalStyleMutation(node)) return;
                if (!backgroundTargetSet.has(node)) {
                  backgroundTargetSet.add(node);
                  backgroundTargets.push(node);
                }
                if (!dagStyleQueued.has(node)) {
                  dagStyleQueued.add(node);
                  dagStyleQueue.push(node);
                }
                // A class/style mutation on an ancestor can change backgrounds
                // anywhere in a newly opened menu. Revisit that subtree in the
                // same bounded security queue. Inline animations receive a
                // cooldown; ResizeObserver still wakes images immediately when
                // their hidden/zero-size container becomes renderable.
                if (!layoutDecisionMade.has(node)) {
                  layoutDecisionMade.add(node);
                  var now = window.performance ? window.performance.now() : Date.now();
                  var lastScan = dagLastLayoutSubtreeScan.get(node) || 0;
                  if (now - lastScan >= ${DagDomSecurityBatchPolicy.InlineStyleSubtreeCooldownMillis}) {
                    dagLastLayoutSubtreeScan.set(node, now);
                    immediateLayoutRoots.push(node);
                  } else {
                    dagDeferLayoutSecurityScan(
                      node,
                      ${DagDomSecurityBatchPolicy.InlineStyleSubtreeCooldownMillis} -
                        Math.max(0, now - lastScan)
                    );
                  }
                }
                var tag = node.tagName ? node.tagName.toLowerCase() : '';
                if (node.isConnected &&
                    node.id !== 'dag-content-guard' &&
                    node.id !== 'dag-safe-style' &&
                    !/^(?:img|picture|source|video|audio|canvas|iframe|object|embed|svg|path|use)$/.test(tag) &&
                    !(node.closest &&
                      node.closest('[data-dag-calibration-marker="true"],[data-dag-media-placeholder="true"]'))) {
                  visibleTextMayHaveChanged = true;
                }
              });
              if (backgroundTargets.length) {
                immediateLayoutRoots.forEach(dagQueueSecurityNode);
                if (${DagBackgroundSecurityPolicy.VisibilityTargetScanRunsSynchronously}) {
                  dagRunTargetedVisibleBackgroundPass(backgroundTargets);
                }
              }
              dagScheduleStyleFlush();
              // class/style/hidden/open can reveal text without inserting a
              // node. Let the shared signature/debounce/throttle do one bounded
              // full-text check instead of reading innerText per mutation.
              if (visibleTextMayHaveChanged) dagScheduleContentCheck(false, false);
            });
            dagStyleObserver.observe($DagSanitizerObserverTargetJavaScript, {
              subtree: true,
              attributes: true,
              attributeFilter: [
                $DagObservedVisibilityAttributesJavaScript
              ]
            });
          }
          dagLastContentSignature = dagContentSignature();
          dagLastContentCheckAt = dagContentNow();
          return '$DagSanitizerInstallSuccessPrefix' +
            (document.body ? document.body.innerText.substring(0,24000) : '');
        })();
        """.trimIndent(),
    ) { encoded ->
        val result = encoded.decodeJavascriptString()
        val installed = result?.startsWith(DagSanitizerInstallSuccessPrefix) == true
        callback(
            installed,
            result
                ?.takeIf { installed }
                ?.removePrefix(DagSanitizerInstallSuccessPrefix)
                ?.takeIf(String::isNotBlank),
        )
    }
}

private fun WebView.prepareDagViewportImagesFailClosed(
    onProgress: (Int, Int) -> Unit,
    callback: () -> Unit,
) {
    // Queue the visible raster resources in one renderer roundtrip, but never
    // wait for their delivery here. DagWebViewClient.shouldInterceptRequest
    // sends every image through DagImageResourceLoader; undecided or failed
    // resources return its unavailable placeholder, so renderer polling only
    // delays safe text and controls when a busy site postpones callbacks.
    val ignorePending = !DagViewportReadinessPolicy.PendingImagesBlockPageReveal
    val prepareViewportScript =
        "(function(){ return window.__dagPrepareViewportImages " +
            "? window.__dagPrepareViewportImages($ignorePending) : null; })();"
    evaluateJavascript(
        prepareViewportScript,
    ) { encoded ->
        val status =
            encoded.decodeJavascriptString()?.let { value ->
                runCatching { org.json.JSONObject(value) }.getOrNull()
            }
        val pending = status?.optInt("pending", 0) ?: 0
        val total = status?.optInt("total", 0) ?: 0
        onProgress((total - pending).coerceAtLeast(0), total)
        callback()
    }
}

internal object DagViewportReadinessPolicy {
    // Resolve the visible viewport first. Offscreen photos begin loading when
    // scrolling reaches them instead of competing with initial content. Pending
    // photos are already fail-closed behind the transparent synthetic response,
    // so they must never hold back the usable page while classification continues.
    const val PreparedViewportCount = 1
    const val PrefetchViewportCount = 0
    const val PendingImagesBlockPageReveal = false
}

internal object DagImageRequestPolicy {
    // Native delivery still caps combined downloads/decodes at four. Let all
    // four slots carry ordinary visible images when no CSS background is
    // active; a background request simply waits for a native slot.
    const val MaximumConcurrentSyntheticRequests = 4
    const val MaximumConcurrentBackgroundRequests = 1
    const val SyntheticRequestTimeoutMillis = 16_500L
}

internal object DagImageFastLanePolicy {
    const val MaximumImagesPerDocument = 6
}

internal fun dagImageFastLaneEligible(
    sourceIsHttps: Boolean,
    loadingAttribute: String?,
    hasRenderableGeometry: Boolean,
    fromDynamicSubtree: Boolean,
    styleAllowsLoading: Boolean,
    alreadySelected: Int,
): Boolean {
    val loading = loadingAttribute.orEmpty().trim().lowercase().ifEmpty { "auto" }
    return sourceIsHttps &&
        styleAllowsLoading &&
        alreadySelected in 0 until DagImageFastLanePolicy.MaximumImagesPerDocument &&
        (loading == "auto" || loading == "eager") &&
        (!hasRenderableGeometry || fromDynamicSubtree)
}

internal fun dagImageFastLaneRemainingAfterRelease(
    currentRemaining: Int,
    wasTrackedFastLaneImage: Boolean,
    isConnected: Boolean,
): Int {
    val boundedCurrent = currentRemaining.coerceIn(0, DagImageFastLanePolicy.MaximumImagesPerDocument)
    return if (wasTrackedFastLaneImage && !isConnected) {
        (boundedCurrent + 1).coerceAtMost(DagImageFastLanePolicy.MaximumImagesPerDocument)
    } else {
        boundedCurrent
    }
}

internal object DagBackgroundSecurityPolicy {
    const val InitialScanRunsSynchronously = true
    const val StylesheetScanRunsSynchronously = false
    const val AddedSubtreeScanRunsSynchronously = false
    const val VisibilityTargetScanRunsSynchronously = false
    const val PseudoElementUrlsRequireFailClosed = true
    const val VisibleSampleColumns = 3
    const val VisibleSampleRows = 3
    const val MaximumVisibleNodesPerSynchronousPass = 24
    const val VisibleSampleMakesViewportReady = true
    const val StylesheetLoadRescanTimeoutMillis = 10_000L
}

internal val DagStylesheetLinkMutationAttributes =
    listOf(
        "href",
        "rel",
        "media",
        "disabled",
    )
internal const val DagUnsafeCssImageSchemeNamesJavaScriptPattern =
    "data|blob|file|content|javascript"
internal const val DagUnsafeCssImageSchemeJavaScriptPattern =
    "^(?:" + DagUnsafeCssImageSchemeNamesJavaScriptPattern + "):"
internal const val DagImageSchemeCspMetaId = "dag-image-scheme-csp"
internal const val DagImageSchemeCspContent = "img-src https:"
internal const val DagDocumentStartSecurityUnsupportedMessage =
    "DAG necesita una versión actualizada de Android System WebView o Chrome para navegar de forma segura."

internal fun documentStartSecurityAvailable(featureSupported: Boolean): Boolean = featureSupported

internal const val DagBlockedBeforeBackgroundAttribute = "data-dag-blocked-before-background"
internal const val DagBlockedAfterBackgroundAttribute = "data-dag-blocked-after-background"
internal val DagBlockedPseudoBackgroundCss =
    "html[$DagBlockedBeforeBackgroundAttribute=\"true\"]::before," +
        "html[$DagBlockedAfterBackgroundAttribute=\"true\"]::after," +
        "html body[$DagBlockedBeforeBackgroundAttribute=\"true\"]::before," +
        "html body[$DagBlockedAfterBackgroundAttribute=\"true\"]::after," +
        "html body [$DagBlockedBeforeBackgroundAttribute=\"true\"]::before," +
        "html body [$DagBlockedAfterBackgroundAttribute=\"true\"]::after{" +
        "background-image:none!important}"

internal fun dagCssBackgroundSourceRequiresFailClosed(rawSource: String): Boolean =
    Regex(
        DagUnsafeCssImageSchemeJavaScriptPattern,
        RegexOption.IGNORE_CASE,
    ).containsMatchIn(rawSource.trim())

internal fun dagCssBackgroundRequiresFailClosed(backgroundImage: String): Boolean =
    Regex(
        """url\(\s*["']?\s*(?:$DagUnsafeCssImageSchemeNamesJavaScriptPattern):""",
        RegexOption.IGNORE_CASE,
    ).containsMatchIn(backgroundImage) ||
        Regex(
            """url\(["']?([^"')]+)["']?\)""",
            RegexOption.IGNORE_CASE,
        ).findAll(backgroundImage)
            .any { match -> dagCssBackgroundSourceRequiresFailClosed(match.groupValues[1]) }

internal object DagDomSecurityBatchPolicy {
    const val MaximumNodesPerFrame = 48
    const val InlineStyleSubtreeCooldownMillis = 500L
}

internal object DagContentMutationPolicy {
    const val UrgentDelayMillis = 40L
    const val NormalDebounceMillis = 650L
    const val NormalThrottleMillis = 5_000L
}

internal fun dagProgrammaticNavigationEffectKey(state: DagBrowserUiState): Long = state.navigationRevision

internal object DagTextExtractionRetryPolicy {
    const val MaximumRetries = 2
    const val BaseDelayMillis = 700L
}

internal fun dagShouldRetryTextExtraction(
    text: String?,
    attempt: Int,
    isAttachedToWindow: Boolean,
): Boolean =
    text.isNullOrBlank() &&
        attempt < DagTextExtractionRetryPolicy.MaximumRetries &&
        isAttachedToWindow

internal val DagDynamicRiskJavaScriptPattern =
    """(?:\b(?:porn(?:o|ograf[ií]a|ography)?|xxx|nudes?|nudity|naked|desnud[oa]s?|desnudez|sex(?:o|ual(?:idad)?)?|escort|prostituci[oó]n|dating|hookup|citas|casino|apuestas?|betting|poker|cannabis|marijuana|marihuana|coca[ií]na|cocaine|drugs?|drogas?|gore|violence|violent|violencia|murder|asesinato|tortura)\b|פורנו|פורנוגרפיה|עירום|זנות|מין מפורש|הכרויות|הימורים|קזינו|קנאביס|סמים|אלימות|רצח|עינויים)"""

internal object DagWebMessagePolicy {
    const val MaximumControlPayloadBytes = 16 * 1_024
    const val MaximumImagePriorityPayloadBytes = 64 * 1_024
    const val MaximumCalibrationMessagesPerWindow = 128
    const val CalibrationWindowMillis = 5_000L
    const val MaximumImagePriorityMessagesPerWindow = 16
    const val ImagePriorityWindowMillis = 1_000L
    const val MaximumCaptchaMessagesPerWindow = 4
    const val CaptchaWindowMillis = 10_000L
    const val MaximumContentMessagesPerWindow = 8
    const val ContentWindowMillis = 5_000L
}

internal fun dagWebMessagePayloadAllowed(
    payload: String?,
    maximumBytes: Int,
): Boolean {
    if (payload == null || maximumBytes <= 0 || payload.length > maximumBytes) return false
    return payload.toByteArray(Charsets.UTF_8).size <= maximumBytes
}

internal class DagWebMessageRateLimiter(
    private val maximumMessages: Int,
    private val windowMillis: Long,
    private val clock: () -> Long = { SystemClock.elapsedRealtime() },
) {
    private var windowStartedAtMillis = Long.MIN_VALUE
    private var messagesInWindow = 0

    init {
        require(maximumMessages > 0)
        require(windowMillis > 0)
    }

    @Synchronized
    fun tryAcquire(): Boolean {
        val now = clock()
        if (
            windowStartedAtMillis == Long.MIN_VALUE ||
            now < windowStartedAtMillis ||
            now - windowStartedAtMillis >= windowMillis
        ) {
            windowStartedAtMillis = now
            messagesInWindow = 0
        }
        if (messagesInWindow >= maximumMessages) return false
        messagesInWindow += 1
        return true
    }
}

internal val DagObservedSecurityAttributes =
    listOf(
        "src",
        "srcset",
        "data-src",
        "data-lazy-src",
        "data-srcset",
        "data-lazy-srcset",
        "alt",
        "title",
        "aria-label",
        "loading",
        "href",
        "rel",
        "media",
        "disabled",
        "xlink:href",
        "data",
    )

internal val DagObservedVisibilityAttributes =
    listOf(
        "class",
        "style",
        "hidden",
        "open",
    )

internal val DagImageSourceMutationResetAttributes =
    listOf(
        "data-dag-priority-source",
        "data-dag-priority-queued",
        "data-dag-image-priority-pending",
        "data-dag-image-ready",
        "data-dag-image-terminal",
    )

private val DagObservedSecurityAttributesJavaScript =
    DagObservedSecurityAttributes.joinToString(separator = ",") { attribute -> "'$attribute'" }

private val DagObservedVisibilityAttributesJavaScript =
    DagObservedVisibilityAttributes.joinToString(separator = ",") { attribute -> "'$attribute'" }

private val DagStylesheetLinkMutationAttributeCheckJavaScript =
    DagStylesheetLinkMutationAttributes.joinToString(
        prefix = "(",
        postfix = ")",
        separator = " || ",
    ) { attribute -> "record.attributeName === '$attribute'" }

private val DagImageSourceMutationResetJavaScript =
    DagImageSourceMutationResetAttributes.joinToString(separator = "\n") { attribute ->
        "image.removeAttribute('$attribute');"
    }

internal const val DagSanitizerObserverTargetJavaScript = "document"
internal const val DagDocumentRootReplacementJavaScript = "node === document.documentElement"

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

// The sanitizer is already installed at commit and retries blank shells. A
// short settle captures meaningful text without adding a full second to every
// navigation; later SPA mutations are reclassified independently.
private const val PageCommitInspectionDelayMillis = 300L
private const val DagCalibrationBridgeName = "dagCalibrationBridge"
private const val DagCaptchaBridgeName = "dagCaptchaBridge"
private const val DagImagePriorityBridgeName = "dagImagePriorityBridge"
private const val DagImagePriorityAction = "prioritize"
private const val DagMaximumPriorityUrlsPerMessage = 96
private const val DagContentBridgeName = "dagContentBridge"
private const val DagContentChangedAction = "content_changed"
private const val DagMaximumDocumentUrlLength = 4_096
private const val DagSanitizerInstallSuccessPrefix = "__dag_native_sanitizer_installed__:"
private const val DagCaptchaSessionStart = "captcha_session_start"
private const val DagCaptchaSessionDurationMillis = 120_000L
private const val DagCalibrationActionProbe = "probe"
private const val DagCalibrationActionBlock = "block"
private const val DagCalibrationActionReviewBlur = "review_blur"
