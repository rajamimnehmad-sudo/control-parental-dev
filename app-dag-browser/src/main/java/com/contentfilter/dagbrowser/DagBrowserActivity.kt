package com.contentfilter.dagbrowser

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import org.json.JSONObject
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebExtension
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class DagBrowserActivity : Activity() {
    private lateinit var geckoView: GeckoView
    private lateinit var addressInput: EditText
    private lateinit var backButton: Button
    private lateinit var reloadButton: Button
    private lateinit var safetyOverlay: View
    private lateinit var safetyProgress: ProgressBar
    private lateinit var safetyTitle: TextView
    private lateinit var safetyDetail: TextView

    private val session = GeckoSession()
    private val handler = Handler(Looper.getMainLooper())
    private val performanceTracker = DagPerformanceTracker(SystemClock::elapsedRealtime)
    private val mediaAnalysisExecutor =
        ThreadPoolExecutor(
            MediaAnalysisThreads,
            MediaAnalysisThreads,
            0L,
            TimeUnit.MILLISECONDS,
            ArrayBlockingQueue(MediaAnalysisQueueCapacity),
        )
    private var protectionExtension: WebExtension? = null
    private var sessionOpened = false
    private var extensionReady = false
    private var canGoBack = false
    private var waitingForBarrier = false

    private val barrierTimeout =
        Runnable {
            if (waitingForBarrier) {
                waitingForBarrier = false
                showClosedPage()
            }
        }

    private val messageDelegate =
        object : WebExtension.MessageDelegate {
            override fun onMessage(
                nativeApp: String,
                message: Any,
                sender: WebExtension.MessageSender,
            ): GeckoResult<Any>? {
                val payload = message as? JSONObject ?: return null
                val correctExtension =
                    nativeApp == NativeApp &&
                        sender.webExtension.id == ExtensionId
                if (!correctExtension || payload.optInt("version") != ProtectionProtocolVersion) {
                    return null
                }

                return when (payload.optString("type")) {
                    BarrierReadyMessage -> {
                        if (isTrustedContentSender(sender) && sender.isTopLevel && waitingForBarrier) {
                            waitingForBarrier = false
                            handler.removeCallbacks(barrierTimeout)
                            revealProtectedPage()
                        }
                        null
                    }
                    MediaCandidateMessage ->
                        if (isTrustedContentSender(sender)) {
                            GeckoResult.fromValue(metadataDecisionPayload(payload))
                        } else {
                            null
                        }
                    MediaBytesMessage ->
                        if (isTrustedExtensionSender(sender)) {
                            mediaBytesDecisionAsync(payload)
                        } else {
                            null
                        }
                    ViewportImagesReadyMessage -> {
                        if (isTrustedExtensionSender(sender)) {
                            recordPerformanceMetric(DagPerformanceMetric.ViewportImagesReady)
                        }
                        null
                    }
                    else -> null
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dag_browser)
        bindViews()
        configureControls()
        configureSession()
        installProtectionExtension()
    }

    private fun bindViews() {
        geckoView = findViewById(R.id.gecko_view)
        addressInput = findViewById(R.id.address_input)
        backButton = findViewById(R.id.back_button)
        reloadButton = findViewById(R.id.reload_button)
        safetyOverlay = findViewById(R.id.safety_overlay)
        safetyProgress = findViewById(R.id.safety_progress)
        safetyTitle = findViewById(R.id.safety_title)
        safetyDetail = findViewById(R.id.safety_detail)
    }

    private fun configureControls() {
        findViewById<Button>(R.id.go_button).setOnClickListener { navigateFromInput() }
        addressInput.setOnEditorActionListener { _, actionId, event ->
            val submitted =
                actionId == EditorInfo.IME_ACTION_GO ||
                    (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP)
            if (submitted) navigateFromInput()
            submitted
        }
        backButton.setOnClickListener {
            if (sessionOpened && canGoBack) session.goBack()
        }
        reloadButton.setOnClickListener {
            if (sessionOpened && extensionReady) session.reload()
        }
    }

    private fun configureSession() {
        session.contentDelegate = object : GeckoSession.ContentDelegate {}
        session.navigationDelegate =
            object : GeckoSession.NavigationDelegate {
                override fun onCanGoBack(
                    session: GeckoSession,
                    canGoBack: Boolean,
                ) {
                    this@DagBrowserActivity.canGoBack = canGoBack
                    backButton.isEnabled = canGoBack
                }

                override fun onLoadRequest(
                    session: GeckoSession,
                    request: GeckoSession.NavigationDelegate.LoadRequest,
                ): GeckoResult<AllowOrDeny> {
                    return when (
                        val decision =
                            DagNavigationPolicy.decideLoad(
                                url = request.uri,
                                opensNewWindow =
                                    request.target == GeckoSession.NavigationDelegate.TARGET_WINDOW_NEW,
                            )
                    ) {
                        DagLoadDecision.Allow -> GeckoResult.fromValue(AllowOrDeny.ALLOW)
                        DagLoadDecision.Block -> {
                            showBlockedNavigation()
                            GeckoResult.fromValue(AllowOrDeny.DENY)
                        }
                        is DagLoadDecision.Redirect -> {
                            beginProtectedLoad()
                            session.loadUri(decision.url)
                            GeckoResult.fromValue(AllowOrDeny.DENY)
                        }
                    }
                }

                override fun onNewSession(
                    session: GeckoSession,
                    uri: String,
                ): GeckoResult<GeckoSession>? {
                    when (val decision = DagNavigationPolicy.decideLoad(uri, opensNewWindow = true)) {
                        DagLoadDecision.Allow -> Unit
                        DagLoadDecision.Block -> showBlockedNavigation()
                        is DagLoadDecision.Redirect -> {
                            beginProtectedLoad()
                            handler.post { session.loadUri(decision.url) }
                        }
                    }
                    return null
                }
            }
        session.progressDelegate =
            object : GeckoSession.ProgressDelegate {
                override fun onPageStart(
                    session: GeckoSession,
                    url: String,
                ) {
                    addressInput.setText(url)
                    if (url == InitialBlankPage) {
                        waitingForBarrier = false
                        handler.removeCallbacks(barrierTimeout)
                        if (extensionReady) showReady()
                    } else {
                        beginProtectedLoad()
                    }
                }

                override fun onPageStop(
                    session: GeckoSession,
                    success: Boolean,
                ) {
                    recordPerformanceMetric(
                        metric = DagPerformanceMetric.PageAnalysisReady,
                        detail = "success=$success",
                    )
                    if (!success && waitingForBarrier) {
                        waitingForBarrier = false
                        handler.removeCallbacks(barrierTimeout)
                        showClosedPage()
                    }
                }
            }
    }

    private fun installProtectionExtension() {
        showOverlay(
            title = getString(R.string.preparing_protection),
            detail = "",
            spinning = true,
        )
        val runtime = DagGeckoRuntime.get(this)
        runtime.webExtensionController
            .ensureBuiltIn(ExtensionLocation, ExtensionId)
            .accept(
                { extension ->
                    runOnUiThread {
                        when {
                            isFinishing || isDestroyed -> Unit
                            extension == null -> showExtensionFailure()
                            else -> {
                                protectionExtension = extension
                                extension.setMessageDelegate(messageDelegate, NativeApp)
                                session.webExtensionController.setMessageDelegate(extension, messageDelegate, NativeApp)
                                session.open(runtime)
                                sessionOpened = true
                                geckoView.setSession(session)
                                extensionReady = true
                                showReady()
                            }
                        }
                    }
                },
                {
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed) showExtensionFailure()
                    }
                },
            )
    }

    private fun showExtensionFailure() {
        extensionReady = false
        showOverlay(
            title = getString(R.string.extension_failed),
            detail = getString(R.string.extension_failed_detail),
            spinning = false,
        )
    }

    private fun showReady() {
        geckoView.visibility = View.INVISIBLE
        showOverlay(
            title = getString(R.string.ready),
            detail = getString(R.string.ready_detail),
            spinning = false,
        )
    }

    private fun navigateFromInput() {
        if (!extensionReady || !sessionOpened) return
        addressInput.clearFocus()
        getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(addressInput.windowToken, 0)
        val safeUrl = DagNavigationPolicy.fromUserInput(addressInput.text.toString())
        if (safeUrl == null) {
            showBlockedNavigation()
            return
        }
        beginProtectedLoad(startNewPerformanceNavigation = true)
        session.loadUri(safeUrl)
    }

    private fun beginProtectedLoad(startNewPerformanceNavigation: Boolean = false) {
        if (startNewPerformanceNavigation || !waitingForBarrier) {
            recordPerformanceEvent(performanceTracker.begin())
        }
        waitingForBarrier = true
        geckoView.visibility = View.INVISIBLE
        showOverlay(
            title = getString(R.string.loading_protected_page),
            detail = getString(R.string.ready_detail),
            spinning = true,
        )
        handler.removeCallbacks(barrierTimeout)
        handler.postDelayed(barrierTimeout, BarrierTimeoutMillis)
    }

    private fun revealProtectedPage() {
        geckoView.visibility = View.VISIBLE
        safetyOverlay.visibility = View.GONE
        recordPerformanceMetric(DagPerformanceMetric.PageVisible)
    }

    private fun recordPerformanceMetric(
        metric: DagPerformanceMetric,
        detail: String = "",
    ) {
        performanceTracker.mark(metric)?.let { recordPerformanceEvent(it, detail) }
    }

    private fun recordPerformanceEvent(
        event: DagPerformanceEvent,
        detail: String = "",
    ) {
        if (!packageName.endsWith(".dev")) return
        val detailSuffix = if (detail.isBlank()) "" else " $detail"
        Log.i(
            PerformanceLogTag,
            "navigation=${event.navigationId} metric=${event.metric.wireValue} " +
                "elapsed_ms=${event.elapsedMillis}$detailSuffix",
        )
    }

    private fun isTrustedContentSender(sender: WebExtension.MessageSender): Boolean =
        sender.session === session &&
            sender.environmentType == WebExtension.MessageSender.ENV_TYPE_CONTENT_SCRIPT

    private fun isTrustedExtensionSender(sender: WebExtension.MessageSender): Boolean =
        sender.session == null &&
            sender.environmentType == WebExtension.MessageSender.ENV_TYPE_EXTENSION

    private fun metadataDecisionPayload(payload: JSONObject): JSONObject {
        val candidate =
            DagMediaCandidate(
                candidateId = payload.optString("candidateId"),
                sourceUrl = payload.optString("sourceUrl"),
                documentUrl = payload.optString("documentUrl"),
                altText = payload.optString("altText"),
                width = payload.optInt("width", -1),
                height = payload.optInt("height", -1),
            )
        val decision = DagMediaAnalysisPolicy.decide(candidate)
        return decisionPayload(decision)
    }

    private fun mediaBytesDecisionAsync(payload: JSONObject): GeckoResult<Any> {
        val result = GeckoResult<Any>(handler)
        try {
            mediaAnalysisExecutor.execute {
                val decision =
                    runCatching { mediaBytesDecision(payload) }
                        .getOrElse {
                            DagMediaDecision(
                                candidateId = payload.optString("candidateId").take(MaxMediaCandidateIdLength),
                                action = DagMediaAction.Block,
                                reason = DagMediaBytesPolicy.InvalidPayloadReason,
                            )
                        }
                result.complete(decisionPayload(decision))
            }
        } catch (_: RejectedExecutionException) {
            result.complete(
                decisionPayload(
                    DagMediaDecision(
                        candidateId = payload.optString("candidateId").take(MaxMediaCandidateIdLength),
                        action = DagMediaAction.Block,
                        reason = DagMediaBytesPolicy.AnalyzerBusyReason,
                    ),
                ),
            )
        }
        return result
    }

    private fun mediaBytesDecision(payload: JSONObject): DagMediaDecision {
        val startedAt = SystemClock.elapsedRealtime()
        val bytesPayload =
            DagMediaBytesPayload(
                candidateId = payload.optString("candidateId"),
                sourceUrl = payload.optString("sourceUrl"),
                declaredByteLength = payload.optInt("byteLength", -1),
                bytesBase64 = payload.optString("bytesBase64"),
            )
        val decision = DagMediaBytesPolicy.decide(bytesPayload)
        if (packageName.endsWith(".dev")) {
            Log.i(
                MediaTransportLogTag,
                "bytes=${bytesPayload.declaredByteLength} reason=${decision.reason} " +
                    "elapsed_ms=${SystemClock.elapsedRealtime() - startedAt}",
            )
        }
        return decision
    }

    private fun decisionPayload(decision: DagMediaDecision): JSONObject {
        return JSONObject()
            .put("type", MediaDecisionMessage)
            .put("version", ProtectionProtocolVersion)
            .put("candidateId", decision.candidateId)
            .put("action", decision.action.wireValue)
            .put("reason", decision.reason)
    }

    private fun showClosedPage() {
        geckoView.visibility = View.INVISIBLE
        showOverlay(
            title = getString(R.string.barrier_not_confirmed),
            detail = getString(R.string.barrier_not_confirmed_detail),
            spinning = false,
        )
    }

    private fun showBlockedNavigation() {
        waitingForBarrier = false
        handler.removeCallbacks(barrierTimeout)
        geckoView.visibility = View.INVISIBLE
        showOverlay(
            title = getString(R.string.navigation_blocked),
            detail = getString(R.string.navigation_blocked_detail),
            spinning = false,
        )
    }

    private fun showOverlay(
        title: String,
        detail: String,
        spinning: Boolean,
    ) {
        safetyOverlay.visibility = View.VISIBLE
        safetyProgress.visibility = if (spinning) View.VISIBLE else View.GONE
        safetyTitle.text = title
        safetyDetail.text = detail
        safetyDetail.visibility = if (detail.isBlank()) View.GONE else View.VISIBLE
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (sessionOpened && canGoBack) {
            session.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(barrierTimeout)
        protectionExtension?.let { extension ->
            extension.setMessageDelegate(null, NativeApp)
            session.webExtensionController.setMessageDelegate(extension, null, NativeApp)
        }
        protectionExtension = null
        mediaAnalysisExecutor.shutdownNow()
        if (sessionOpened) {
            geckoView.releaseSession()
            session.close()
        }
        super.onDestroy()
    }

    private companion object {
        const val ExtensionLocation = "resource://android/assets/dag-protection/"
        const val ExtensionId = "dag-protection@glosh.local"
        const val NativeApp = "glosh.dag.protection"
        const val BarrierReadyMessage = "barrier-ready"
        const val MediaCandidateMessage = "media-candidate"
        const val MediaBytesMessage = "media-bytes"
        const val MediaDecisionMessage = "media-decision"
        const val ViewportImagesReadyMessage = "viewport-images-ready"
        const val ProtectionProtocolVersion = 1
        const val MaxMediaCandidateIdLength = 80
        const val MediaAnalysisThreads = 2
        const val MediaAnalysisQueueCapacity = 8
        const val MediaTransportLogTag = "DagMediaTransport"
        const val PerformanceLogTag = "DagPerformance"
        const val BarrierTimeoutMillis = 12_000L
        const val InitialBlankPage = "about:blank"
    }
}
