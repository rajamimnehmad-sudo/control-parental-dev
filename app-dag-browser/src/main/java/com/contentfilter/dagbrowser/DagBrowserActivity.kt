package com.contentfilter.dagbrowser

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
                val trustedSender =
                    nativeApp == NativeApp &&
                        sender.session === session &&
                        sender.environmentType == WebExtension.MessageSender.ENV_TYPE_CONTENT_SCRIPT
                if (!trustedSender || payload.optInt("version") != ProtectionProtocolVersion) {
                    return null
                }

                return when (payload.optString("type")) {
                    BarrierReadyMessage -> {
                        if (sender.isTopLevel && waitingForBarrier) {
                            waitingForBarrier = false
                            handler.removeCallbacks(barrierTimeout)
                            revealProtectedPage()
                        }
                        null
                    }
                    MediaCandidateMessage -> GeckoResult.fromValue(mediaDecisionPayload(payload))
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
        beginProtectedLoad()
        session.loadUri(safeUrl)
    }

    private fun beginProtectedLoad() {
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
    }

    private fun mediaDecisionPayload(payload: JSONObject): JSONObject {
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
        const val MediaDecisionMessage = "media-decision"
        const val ProtectionProtocolVersion = 1
        const val BarrierTimeoutMillis = 12_000L
        const val InitialBlankPage = "about:blank"
    }
}
