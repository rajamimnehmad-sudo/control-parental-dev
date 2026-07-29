package com.contentfilter.dagbrowser

import android.app.Activity
import android.app.AlertDialog
import android.app.role.RoleManager
import android.content.ComponentCallbacks2
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.StorageController
import org.mozilla.geckoview.WebExtension
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class DagBrowserActivity : Activity() {
    private lateinit var geckoView: GeckoView
    private lateinit var addressInput: EditText
    private lateinit var homeButton: ImageButton
    private lateinit var goButton: ImageButton
    private lateinit var tabButton: TextView
    private lateinit var menuButton: ImageButton
    private lateinit var safetyOverlay: View
    private lateinit var safetyProgress: ProgressBar
    private lateinit var safetyTitle: TextView
    private lateinit var safetyDetail: TextView
    private lateinit var tabSwitcher: DagTabSwitcherView
    private lateinit var tabPersistence: DagTabPersistence
    private lateinit var imageAnalyzer: DagImageAnalyzer
    private lateinit var runtime: GeckoRuntime

    private val handler = Handler(Looper.getMainLooper())
    private val performanceTracker = DagPerformanceTracker(SystemClock::elapsedRealtime)
    private val tabs = mutableListOf<BrowserTab>()
    private val thumbnailExecutor = Executors.newSingleThreadExecutor()
    private val mediaAnalysisExecutor =
        ThreadPoolExecutor(
            MediaAnalysisThreads,
            MediaAnalysisThreads,
            0L,
            TimeUnit.MILLISECONDS,
            ArrayBlockingQueue(MediaAnalysisQueueCapacity),
        )
    private var protectionExtension: WebExtension? = null
    private var extensionReady = false
    private var activeTab: BrowserTab? = null
    private var nextTabId = 1L
    private var restoringTabs = false
    private var pendingExternalUrl: String? = null
    private val persistTabsRunnable = Runnable(::persistTabsNow)

    private val messageDelegate =
        object : WebExtension.MessageDelegate {
            override fun onConnect(port: WebExtension.Port) {
                val sender = port.sender
                val correctExtension = sender.webExtension.id == ExtensionId
                val senderTab = tabs.firstOrNull { it.session === sender.session }
                when {
                    correctExtension &&
                        senderTab != null &&
                        sender.environmentType == WebExtension.MessageSender.ENV_TYPE_CONTENT_SCRIPT -> {
                        if (packageName.endsWith(".dev")) {
                            Log.i(MediaTransportLogTag, "content_port=connected")
                        }
                        port.setDelegate(
                            object : WebExtension.PortDelegate {
                                override fun onPortMessage(
                                    message: Any,
                                    sourcePort: WebExtension.Port,
                                ) {
                                    val payload = message as? JSONObject ?: return
                                    if (
                                        payload.optString("type") == BarrierReadyMessage &&
                                        payload.optInt("version") == ProtectionProtocolVersion &&
                                        sender.isTopLevel &&
                                        senderTab.waitingForBarrier
                                    ) {
                                        completeProtectedLoad(senderTab)
                                    }
                                }
                            },
                        )
                    }
                    correctExtension &&
                        sender.session == null &&
                        sender.environmentType == WebExtension.MessageSender.ENV_TYPE_EXTENSION -> {
                        if (packageName.endsWith(".dev")) {
                            Log.i(MediaTransportLogTag, "decision_port=connected")
                        }
                        port.setDelegate(
                            object : WebExtension.PortDelegate {
                                override fun onPortMessage(
                                    message: Any,
                                    sourcePort: WebExtension.Port,
                                ) {
                                    val payload = message as? JSONObject ?: return
                                    if (payload.optInt("version") != ProtectionProtocolVersion) {
                                        return
                                    }
                                    when (payload.optString("type")) {
                                        MediaBytesMessage -> mediaBytesDecisionFromPort(payload, sourcePort)
                                        MediaPresentationStatusMessage -> {
                                            if (packageName.endsWith(".dev")) {
                                                Log.i(
                                                    MediaTransportLogTag,
                                                    "presentation action=${payload.optString("action")} " +
                                                        "frame=${payload.optInt("frameId", -1)} " +
                                                        "matched=${payload.optInt("matchedCount", -1)} " +
                                                        "states=${payload.optString("matchedStates").take(900)} " +
                                                        "source=${payload.optString("sourceUrl").take(160)}",
                                                )
                                            }
                                        }
                                        ViewportImagesReadyMessage ->
                                            recordPerformanceMetric(DagPerformanceMetric.ViewportImagesReady)
                                    }
                                }
                            },
                        )
                    }
                    else -> port.disconnect()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dag_browser)
        pendingExternalUrl = safeExternalUrl(intent)
        applySystemBarInsets()
        tabPersistence = DagTabPersistence(applicationContext)
        imageAnalyzer = DagOnDeviceImageAnalyzer.create(applicationContext)
        bindViews()
        configureControls()
        installProtectionExtension()
    }

    private fun applySystemBarInsets() {
        findViewById<View>(R.id.browser_root).setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val systemBars = insets.getInsets(WindowInsets.Type.systemBars())
                view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(
                    insets.systemWindowInsetLeft,
                    insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight,
                    insets.systemWindowInsetBottom,
                )
            }
            insets
        }
    }

    private fun bindViews() {
        geckoView = findViewById(R.id.gecko_view)
        addressInput = findViewById(R.id.address_input)
        homeButton = findViewById(R.id.home_button)
        goButton = findViewById(R.id.go_button)
        tabButton = findViewById(R.id.tab_button)
        menuButton = findViewById(R.id.menu_button)
        safetyOverlay = findViewById(R.id.safety_overlay)
        safetyProgress = findViewById(R.id.safety_progress)
        safetyTitle = findViewById(R.id.safety_title)
        safetyDetail = findViewById(R.id.safety_detail)
        tabSwitcher = findViewById(R.id.tab_switcher)
    }

    private fun configureControls() {
        setNavigationControlsEnabled(false)
        goButton.setOnClickListener { navigateFromInput() }
        addressInput.setOnEditorActionListener { _, actionId, event ->
            val submitted =
                actionId == EditorInfo.IME_ACTION_GO ||
                    (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP)
            if (submitted) navigateFromInput()
            submitted
        }
        homeButton.setOnClickListener { activeTab?.let(::goHome) }
        tabButton.setOnClickListener { showTabSwitcher() }
        menuButton.setOnClickListener { showBrowserMenu() }
        tabSwitcher.setListener(
            object : DagTabSwitcherView.Listener {
                override fun onTabSelected(tabId: Long) {
                    val tab = tabs.firstOrNull { it.id == tabId } ?: return
                    tabSwitcher.hide()
                    switchTo(tab)
                }

                override fun onTabClosed(tabId: Long) {
                    tabs.firstOrNull { it.id == tabId }?.let(::closeTab)
                    refreshTabSwitcher()
                }

                override fun onNewTab() {
                    tabSwitcher.hide()
                    createTab(switchToTab = true)
                }

                override fun onTabsReordered(tabIds: List<Long>) {
                    reorderTabs(tabIds)
                }

                override fun onSwitcherClosed() {
                    tabSwitcher.hide()
                }
            },
        )
    }

    private fun configureSession(tab: BrowserTab) {
        tab.session.contentDelegate =
            object : GeckoSession.ContentDelegate {
                override fun onTitleChange(
                    session: GeckoSession,
                    title: String?,
                ) {
                    tab.title = title.orEmpty()
                    schedulePersistTabs()
                    refreshTabSwitcher()
                }

                override fun onCrash(session: GeckoSession) {
                    recoverClosedSession(tab)
                }

                override fun onKill(session: GeckoSession) {
                    recoverClosedSession(tab)
                }
            }
        tab.session.navigationDelegate =
            object : GeckoSession.NavigationDelegate {
                override fun onCanGoBack(
                    session: GeckoSession,
                    canGoBack: Boolean,
                ) {
                    tab.canGoBack = canGoBack
                }

                override fun onLoadRequest(
                    session: GeckoSession,
                    request: GeckoSession.NavigationDelegate.LoadRequest,
                ): GeckoResult<AllowOrDeny> {
                    if (request.target == GeckoSession.NavigationDelegate.TARGET_WINDOW_NEW) {
                        openNewTabForUri(request.uri)
                        return GeckoResult.fromValue(AllowOrDeny.DENY)
                    }
                    return when (
                        val decision =
                            DagNavigationPolicy.decideLoad(
                                url = request.uri,
                                opensNewWindow = false,
                            )
                    ) {
                        DagLoadDecision.Allow -> GeckoResult.fromValue(AllowOrDeny.ALLOW)
                        DagLoadDecision.Block -> {
                            showBlockedNavigation(tab)
                            GeckoResult.fromValue(AllowOrDeny.DENY)
                        }
                        is DagLoadDecision.BlockExternalApp -> {
                            handleExternalAppLink(tab, decision.httpsFallback)
                            GeckoResult.fromValue(AllowOrDeny.DENY)
                        }
                        is DagLoadDecision.Redirect -> {
                            beginProtectedLoad(tab)
                            tab.session.loadUri(decision.url)
                            GeckoResult.fromValue(AllowOrDeny.DENY)
                        }
                    }
                }

                override fun onNewSession(
                    session: GeckoSession,
                    uri: String,
                ): GeckoResult<GeckoSession>? {
                    openNewTabForUri(uri)
                    return null
                }
            }
        tab.session.progressDelegate =
            object : GeckoSession.ProgressDelegate {
                override fun onPageStart(
                    session: GeckoSession,
                    url: String,
                ) {
                    tab.url = url
                    tab.needsRestore = false
                    schedulePersistTabs()
                    refreshTabSwitcher()
                    if (url == InitialBlankPage) {
                        tab.title = ""
                        cancelBarrierTimeout(tab)
                        tab.waitingForBarrier = false
                        tab.displayState = TabDisplayState.Ready
                        if (tab === activeTab) renderActiveTab()
                    } else {
                        if (tab === activeTab) addressInput.setText(url)
                        beginProtectedLoad(tab)
                    }
                }

                override fun onPageStop(
                    session: GeckoSession,
                    success: Boolean,
                ) {
                    if (tab === activeTab) {
                        recordPerformanceMetric(
                            metric = DagPerformanceMetric.PageAnalysisReady,
                            detail = "success=$success",
                        )
                    }
                    if (!success && tab.waitingForBarrier) {
                        tab.waitingForBarrier = false
                        cancelBarrierTimeout(tab)
                        showClosedPage(tab)
                    }
                    schedulePersistTabs()
                }
            }
    }

    private fun createTab(
        switchToTab: Boolean,
        initialUrl: String? = null,
        restoredTab: DagPersistedTab? = null,
    ): BrowserTab? {
        if (!extensionReady || tabs.size >= MaxTabs) {
            if (tabs.size >= MaxTabs) {
                Toast.makeText(this, R.string.tab_limit_reached, Toast.LENGTH_SHORT).show()
            }
            return null
        }
        if (protectionExtension == null) return null
        val requestedUrl = restoredTab?.url ?: initialUrl ?: InitialBlankPage
        val tab =
            BrowserTab(
                id = nextTabId++,
                session =
                    GeckoSession(
                        GeckoSessionSettings.Builder()
                            .suspendMediaWhenInactive(true)
                            .build(),
                    ),
                url = requestedUrl,
                title = restoredTab?.title.orEmpty(),
                needsRestore = requestedUrl != InitialBlankPage,
            )
        tabs += tab
        if (restoredTab == null || switchToTab) {
            ensureSessionOpen(tab)
            setTabActivity(tab, active = false)
        }
        updateTabButton()
        schedulePersistTabs()
        refreshTabSwitcher()
        if (switchToTab) switchTo(tab)
        return tab
    }

    private fun restoreTabsOrCreate() {
        val savedState = tabPersistence.load(::isRestorableUrl)
        if (savedState == null) {
            createTab(switchToTab = true)
            return
        }
        restoringTabs = true
        val restored =
            savedState.tabs.mapNotNull { saved ->
                createTab(switchToTab = false, restoredTab = saved)
            }
        restoringTabs = false
        if (restored.isEmpty()) {
            createTab(switchToTab = true)
        } else {
            switchTo(restored[savedState.activeIndex.coerceIn(restored.indices)])
            schedulePersistTabs()
        }
    }

    private fun openNewTabForUri(uri: String) {
        when (val decision = DagNavigationPolicy.decideLoad(uri, opensNewWindow = true)) {
            DagLoadDecision.Allow -> createTab(switchToTab = true, initialUrl = uri)
            DagLoadDecision.Block -> activeTab?.let(::showBlockedNavigation)
            is DagLoadDecision.BlockExternalApp ->
                activeTab?.let { handleExternalAppLink(it, decision.httpsFallback) }
            is DagLoadDecision.Redirect -> createTab(switchToTab = true, initialUrl = decision.url)
        }
    }

    private fun handleExternalAppLink(
        tab: BrowserTab,
        httpsFallback: String?,
    ) {
        if (httpsFallback != null && httpsFallback != tab.url) {
            beginProtectedLoad(tab, startNewPerformanceNavigation = true)
            tab.session.loadUri(httpsFallback)
        }
        Toast.makeText(this, R.string.external_app_link_kept_in_dag, Toast.LENGTH_SHORT).show()
    }

    private fun installProtectionExtension() {
        showOverlay(
            title = getString(R.string.preparing_protection),
            detail = "",
            spinning = true,
        )
        runtime = DagGeckoRuntime.get(this)
        runtime.webExtensionController
            .installBuiltIn(ExtensionLocation)
            .accept(
                { extension ->
                    runOnUiThread {
                        when {
                            isFinishing || isDestroyed -> Unit
                            extension == null -> showExtensionFailure()
                            else -> {
                                protectionExtension = extension
                                extension.setMessageDelegate(messageDelegate, NativeApp)
                                extensionReady = true
                                restoreTabsOrCreate()
                                consumePendingExternalUrl()
                                maybeOfferDefaultBrowserSetup()
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
        setNavigationControlsEnabled(false)
        showOverlay(
            title = getString(R.string.extension_failed),
            detail = getString(R.string.extension_failed_detail),
            spinning = false,
        )
    }

    private fun showReady() {
        geckoView.visibility = View.INVISIBLE
        setNavigationControlsEnabled(true)
        showOverlay(
            title = getString(R.string.ready),
            detail = getString(R.string.ready_detail),
            spinning = false,
        )
    }

    private fun navigateFromInput() {
        val tab = activeTab ?: return
        if (!extensionReady || !tab.session.isOpen) return
        addressInput.clearFocus()
        getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(addressInput.windowToken, 0)
        val safeUrl = DagNavigationPolicy.fromUserInput(addressInput.text.toString())
        if (safeUrl == null) {
            showBlockedNavigation(tab)
            return
        }
        beginProtectedLoad(tab, startNewPerformanceNavigation = true)
        tab.session.loadUri(safeUrl)
    }

    private fun beginProtectedLoad(
        tab: BrowserTab,
        startNewPerformanceNavigation: Boolean = false,
    ) {
        if (tab === activeTab && (startNewPerformanceNavigation || !tab.waitingForBarrier)) {
            recordPerformanceEvent(performanceTracker.begin())
        }
        tab.waitingForBarrier = true
        tab.displayState = TabDisplayState.Loading
        scheduleBarrierTimeout(tab)
        if (tab === activeTab) renderActiveTab()
    }

    private fun completeProtectedLoad(tab: BrowserTab) {
        tab.waitingForBarrier = false
        cancelBarrierTimeout(tab)
        tab.displayState = TabDisplayState.Visible
        if (tab === activeTab) revealProtectedPage()
    }

    private fun revealProtectedPage() {
        geckoView.visibility = View.VISIBLE
        safetyOverlay.visibility = View.GONE
        recordPerformanceMetric(DagPerformanceMetric.PageVisible)
    }

    private fun scheduleBarrierTimeout(tab: BrowserTab) {
        cancelBarrierTimeout(tab)
        val timeout =
            Runnable {
                if (tab.waitingForBarrier && tabs.contains(tab)) {
                    tab.waitingForBarrier = false
                    tab.displayState = TabDisplayState.Closed
                    tab.barrierTimeout = null
                    if (tab === activeTab) renderActiveTab()
                }
            }
        tab.barrierTimeout = timeout
        handler.postDelayed(timeout, BarrierTimeoutMillis)
    }

    private fun cancelBarrierTimeout(tab: BrowserTab) {
        tab.barrierTimeout?.let(handler::removeCallbacks)
        tab.barrierTimeout = null
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

    private fun mediaBytesDecisionFromPort(
        payload: JSONObject,
        port: WebExtension.Port,
    ) {
        val candidateId = payload.optString("candidateId").take(MaxMediaCandidateIdLength)
        val completeDecision: (DagMediaDecision) -> Unit = { decision ->
            handler.post {
                runCatching { port.postMessage(decisionPayload(decision)) }
            }
        }
        try {
            mediaAnalysisExecutor.execute {
                completeDecision(
                    runCatching { mediaBytesDecision(payload) }
                        .getOrElse {
                            DagMediaDecision(
                                candidateId = candidateId,
                                action = DagMediaAction.Block,
                                reason = DagMediaBytesPolicy.InvalidPayloadReason,
                            )
                        },
                )
            }
        } catch (_: RejectedExecutionException) {
            completeDecision(
                DagMediaDecision(
                    candidateId = candidateId,
                    action = DagMediaAction.Block,
                    reason = DagMediaBytesPolicy.AnalyzerBusyReason,
                ),
            )
        }
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
        val decision = DagMediaBytesPolicy.decide(bytesPayload, analyzer = imageAnalyzer)
        if (packageName.endsWith(".dev")) {
            val score =
                decision.filterProbability?.let {
                    " score=${String.format(Locale.US, "%.4f", it)}"
                }.orEmpty()
            Log.i(
                MediaTransportLogTag,
                "bytes=${bytesPayload.declaredByteLength} reason=${decision.reason}$score " +
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

    private fun showClosedPage(tab: BrowserTab) {
        tab.waitingForBarrier = false
        cancelBarrierTimeout(tab)
        tab.displayState = TabDisplayState.Closed
        if (tab === activeTab) renderActiveTab()
    }

    private fun showBlockedNavigation(tab: BrowserTab) {
        tab.waitingForBarrier = false
        cancelBarrierTimeout(tab)
        tab.displayState = TabDisplayState.Blocked
        if (tab === activeTab) renderActiveTab()
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

    private fun setNavigationControlsEnabled(enabled: Boolean) {
        addressInput.isEnabled = enabled
        homeButton.isEnabled = enabled
        goButton.isEnabled = enabled
        val alpha = if (enabled) EnabledControlAlpha else DisabledControlAlpha
        addressInput.alpha = alpha
        homeButton.alpha = alpha
        goButton.alpha = alpha
    }

    private fun goHome(tab: BrowserTab) {
        if (!extensionReady || !tab.session.isOpen) return
        addressInput.clearFocus()
        getSystemService(InputMethodManager::class.java)
            .hideSoftInputFromWindow(addressInput.windowToken, 0)
        tab.session.loadUri(InitialBlankPage)
    }

    private fun switchTo(tab: BrowserTab) {
        if (!tabs.contains(tab)) return
        if (tab === activeTab) {
            restoreTabIfNeeded(tab)
            renderActiveTab()
            return
        }
        activeTab?.let { setTabActivity(it, active = false) }
        if (activeTab != null) runCatching { geckoView.releaseSession() }
        activeTab = tab
        ensureSessionOpen(tab)
        geckoView.setSession(tab.session)
        setTabActivity(tab, active = true)
        restoreTabIfNeeded(tab)
        renderActiveTab()
        schedulePersistTabs()
        refreshTabSwitcher()
    }

    private fun setTabActivity(
        tab: BrowserTab,
        active: Boolean,
    ) {
        if (!tab.session.isOpen) return
        runCatching { tab.session.setActive(active) }
        runCatching { tab.session.setFocused(active) }
        runCatching {
            tab.session.setPriorityHint(
                if (active) GeckoSession.PRIORITY_HIGH else GeckoSession.PRIORITY_DEFAULT,
            )
        }
        runCatching { runtime.webExtensionController.setTabActive(tab.session, active) }
    }

    private fun ensureSessionOpen(tab: BrowserTab) {
        if (tab.session.isOpen) return
        val extension = protectionExtension ?: return
        configureSession(tab)
        tab.session.webExtensionController.setMessageDelegate(extension, messageDelegate, NativeApp)
        tab.session.open(runtime)
    }

    private fun restoreTabIfNeeded(tab: BrowserTab) {
        if (!tab.needsRestore || tab.url == InitialBlankPage || !tab.session.isOpen) return
        val safeUrl = restorableUrl(tab.url)
        if (safeUrl == null) {
            tab.needsRestore = false
            showBlockedNavigation(tab)
            return
        }
        tab.needsRestore = false
        beginProtectedLoad(tab, startNewPerformanceNavigation = true)
        tab.session.loadUri(safeUrl)
    }

    private fun recoverClosedSession(tab: BrowserTab) {
        if (!tabs.contains(tab) || isFinishing || isDestroyed || tab.recovering) return
        tab.recovering = true
        tab.waitingForBarrier = false
        cancelBarrierTimeout(tab)
        tab.canGoBack = false
        tab.needsRestore = tab.url != InitialBlankPage
        tab.displayState = TabDisplayState.Ready
        handler.post {
            if (!tabs.contains(tab) || isFinishing || isDestroyed) {
                tab.recovering = false
                return@post
            }
            if (tab.session.isOpen) runCatching { tab.session.close() }
            tab.recovering = false
            if (tab !== activeTab) return@post
            runCatching { geckoView.releaseSession() }
            ensureSessionOpen(tab)
            geckoView.setSession(tab.session)
            setTabActivity(tab, active = true)
            restoreTabIfNeeded(tab)
            renderActiveTab()
        }
    }

    private fun renderActiveTab() {
        val tab = activeTab ?: return
        setNavigationControlsEnabled(extensionReady)
        updateTabButton()
        if (tab.url == InitialBlankPage) {
            addressInput.text.clear()
        } else if (!addressInput.hasFocus()) {
            addressInput.setText(tab.url)
        }
        when (tab.displayState) {
            TabDisplayState.Ready -> showReady()
            TabDisplayState.Loading -> {
                geckoView.visibility = View.INVISIBLE
                showOverlay(
                    title = getString(R.string.loading_protected_page),
                    detail = getString(R.string.loading_protected_detail),
                    spinning = true,
                )
            }
            TabDisplayState.Visible -> {
                geckoView.visibility = View.VISIBLE
                safetyOverlay.visibility = View.GONE
            }
            TabDisplayState.Closed -> {
                geckoView.visibility = View.INVISIBLE
                showOverlay(
                    title = getString(R.string.barrier_not_confirmed),
                    detail = getString(R.string.barrier_not_confirmed_detail),
                    spinning = false,
                )
            }
            TabDisplayState.Blocked -> {
                geckoView.visibility = View.INVISIBLE
                showOverlay(
                    title = getString(R.string.navigation_blocked),
                    detail = getString(R.string.navigation_blocked_detail),
                    spinning = false,
                )
            }
        }
    }

    private fun updateTabButton() {
        tabButton.text = tabs.size.coerceAtLeast(1).toString()
    }

    private fun showTabSwitcher() {
        if (tabs.isEmpty()) return
        captureActiveTabThumbnail()
        tabSwitcher.show(tabCards())
    }

    private fun refreshTabSwitcher() {
        if (tabSwitcher.isOpen()) tabSwitcher.render(tabCards())
    }

    private fun tabCards(): List<DagTabCard> =
        tabs.map { tab ->
            val label = tabLabel(tab)
            DagTabCard(
                id = tab.id,
                title = tab.title.takeIf(String::isNotBlank) ?: label,
                host = label,
                active = tab === activeTab,
                thumbnail = tab.thumbnail,
            )
        }

    private fun reorderTabs(tabIds: List<Long>) {
        if (tabIds.size != tabs.size || tabIds.toSet().size != tabs.size) return
        val byId = tabs.associateBy(BrowserTab::id)
        val reordered = tabIds.mapNotNull(byId::get)
        if (reordered.size != tabs.size) return
        tabs.clear()
        tabs.addAll(reordered)
        schedulePersistTabs()
    }

    private fun captureActiveTabThumbnail() {
        val tab = activeTab ?: return
        if (
            geckoView.visibility != View.VISIBLE ||
            !tab.session.isOpen ||
            tab.displayState != TabDisplayState.Visible
        ) {
            return
        }
        geckoView.capturePixels().accept(
            { bitmap ->
                if (bitmap == null || bitmap.width <= 0 || bitmap.height <= 0) return@accept
                if (isFinishing || isDestroyed || thumbnailExecutor.isShutdown) {
                    bitmap.recycle()
                    return@accept
                }
                try {
                    thumbnailExecutor.execute {
                        val scaled = scaleThumbnail(bitmap)
                        if (scaled !== bitmap) bitmap.recycle()
                        handler.post {
                            if (isFinishing || isDestroyed || !tabs.contains(tab)) {
                                scaled.recycle()
                            } else {
                                tab.thumbnail?.takeIf { it !== scaled }?.recycle()
                                tab.thumbnail = scaled
                                refreshTabSwitcher()
                            }
                        }
                    }
                } catch (_: RejectedExecutionException) {
                    bitmap.recycle()
                }
            },
            {},
        )
    }

    private fun scaleThumbnail(source: Bitmap): Bitmap {
        val scale =
            minOf(
                1f,
                ThumbnailWidth.toFloat() / source.width,
                ThumbnailHeight.toFloat() / source.height,
            )
        if (scale >= 1f) return source
        return Bitmap.createScaledBitmap(
            source,
            (source.width * scale).toInt().coerceAtLeast(1),
            (source.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    }

    private fun tabLabel(tab: BrowserTab): String {
        if (tab.url == InitialBlankPage) return getString(R.string.new_tab_title)
        return runCatching { Uri.parse(tab.url).host }
            .getOrNull()
            ?.removePrefix("www.")
            ?.takeIf(String::isNotBlank)
            ?: tab.url.take(MaxTabLabelLength)
    }

    private fun isRestorableUrl(url: String): Boolean = restorableUrl(url) != null

    private fun restorableUrl(url: String): String? =
        when (val decision = DagNavigationPolicy.decideLoad(url, opensNewWindow = false)) {
            DagLoadDecision.Allow -> url
            DagLoadDecision.Block -> null
            is DagLoadDecision.BlockExternalApp -> null
            is DagLoadDecision.Redirect -> decision.url
        }

    private fun schedulePersistTabs() {
        if (restoringTabs || !extensionReady) return
        handler.removeCallbacks(persistTabsRunnable)
        handler.postDelayed(persistTabsRunnable, PersistTabsDelayMillis)
    }

    private fun persistTabsNow() {
        handler.removeCallbacks(persistTabsRunnable)
        if (!extensionReady || tabs.isEmpty()) return
        val activeIndex = tabs.indexOf(activeTab).coerceAtLeast(0)
        tabPersistence.save(
            DagPersistedTabs(
                tabs =
                    tabs.map {
                        DagPersistedTab(
                            url = restorableUrl(it.url) ?: InitialBlankPage,
                            title = it.title,
                        )
                    },
                activeIndex = activeIndex,
            ),
        )
    }

    private fun showBrowserMenu() {
        PopupMenu(this, menuButton).apply {
            inflate(R.menu.dag_browser_menu)
            menu.findItem(R.id.menu_default_browser)?.isVisible = !isDefaultBrowser()
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.menu_new_tab -> createTab(switchToTab = true) != null
                    R.id.menu_reload -> {
                        activeTab?.session?.reload()
                        true
                    }
                    R.id.menu_default_browser -> {
                        requestDefaultBrowserRole()
                        true
                    }
                    R.id.menu_close_tab -> {
                        closeActiveTab()
                        true
                    }
                    R.id.menu_clear_cache -> {
                        clearCache()
                        true
                    }
                    R.id.menu_clear_browsing_data -> {
                        confirmClearBrowsingData()
                        true
                    }
                    else -> false
                }
            }
        }.show()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingExternalUrl = safeExternalUrl(intent)
        if (extensionReady) consumePendingExternalUrl()
    }

    @Deprecated("Android browser role still reports through the Activity result callback.")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != DefaultBrowserRoleRequestCode) return
        Toast.makeText(
            this,
            if (isDefaultBrowser()) {
                R.string.default_browser_confirmed
            } else {
                R.string.default_browser_not_confirmed
            },
            Toast.LENGTH_LONG,
        ).show()
    }

    private fun safeExternalUrl(sourceIntent: Intent?): String? {
        if (sourceIntent?.action != Intent.ACTION_VIEW) return null
        val rawUrl = sourceIntent.dataString ?: return null
        return restorableUrl(rawUrl)
    }

    private fun consumePendingExternalUrl() {
        val url = pendingExternalUrl ?: return
        pendingExternalUrl = null
        val tab = activeTab
        if (tab == null) {
            createTab(switchToTab = true, initialUrl = url)
            return
        }
        if (tab.url == InitialBlankPage && tab.session.isOpen) {
            beginProtectedLoad(tab, startNewPerformanceNavigation = true)
            tab.session.loadUri(url)
        } else {
            createTab(switchToTab = true, initialUrl = url)
        }
    }

    private fun maybeOfferDefaultBrowserSetup() {
        if (isDefaultBrowser()) return
        val preferences = getSharedPreferences(BrowserSetupPreferences, MODE_PRIVATE)
        if (preferences.getBoolean(DefaultBrowserPromptShownKey, false)) return
        preferences.edit().putBoolean(DefaultBrowserPromptShownKey, true).apply()
        AlertDialog.Builder(this)
            .setTitle(R.string.default_browser_title)
            .setMessage(R.string.default_browser_detail)
            .setPositiveButton(R.string.configure_now) { _, _ -> requestDefaultBrowserRole() }
            .setNegativeButton(R.string.later, null)
            .show()
    }

    private fun requestDefaultBrowserRole() {
        val roleManager = getSystemService(RoleManager::class.java)
        when {
            roleManager == null || !roleManager.isRoleAvailable(RoleManager.ROLE_BROWSER) ->
                Toast.makeText(this, R.string.default_browser_not_confirmed, Toast.LENGTH_LONG).show()
            roleManager.isRoleHeld(RoleManager.ROLE_BROWSER) ->
                Toast.makeText(this, R.string.default_browser_active, Toast.LENGTH_SHORT).show()
            else ->
                startActivityForResult(
                    roleManager.createRequestRoleIntent(RoleManager.ROLE_BROWSER),
                    DefaultBrowserRoleRequestCode,
                )
        }
    }

    private fun isDefaultBrowser(): Boolean {
        val roleManager = getSystemService(RoleManager::class.java) ?: return false
        return roleManager.isRoleAvailable(RoleManager.ROLE_BROWSER) &&
            roleManager.isRoleHeld(RoleManager.ROLE_BROWSER)
    }

    private fun closeActiveTab() {
        activeTab?.let(::closeTab)
    }

    private fun closeTab(tab: BrowserTab) {
        val oldIndex = tabs.indexOf(tab)
        if (oldIndex < 0) return
        val wasActive = tab === activeTab
        if (wasActive) {
            setTabActivity(tab, active = false)
            runCatching { geckoView.releaseSession() }
            activeTab = null
        }
        tabs.removeAt(oldIndex)
        disposeTab(tab)
        updateTabButton()
        when {
            !wasActive -> Unit
            tabs.isEmpty() -> createTab(switchToTab = true)
            else -> switchTo(tabs[oldIndex.coerceAtMost(tabs.lastIndex)])
        }
        schedulePersistTabs()
        refreshTabSwitcher()
    }

    private fun disposeTab(tab: BrowserTab) {
        cancelBarrierTimeout(tab)
        protectionExtension?.let { extension ->
            runCatching {
                tab.session.webExtensionController.setMessageDelegate(extension, null, NativeApp)
            }
        }
        if (tab.session.isOpen) {
            setTabActivity(tab, active = false)
            tab.session.close()
        }
        tab.thumbnail?.recycle()
        tab.thumbnail = null
    }

    private fun clearCache() {
        runtime.storageController.clearData(StorageController.ClearFlags.ALL_CACHES).accept(
            {
                runOnUiThread {
                    Toast.makeText(this, R.string.cache_cleared, Toast.LENGTH_SHORT).show()
                }
            },
            {
                runOnUiThread {
                    Toast.makeText(this, R.string.storage_clear_failed, Toast.LENGTH_SHORT).show()
                }
            },
        )
    }

    private fun confirmClearBrowsingData() {
        AlertDialog.Builder(this)
            .setTitle(R.string.clear_browsing_data_title)
            .setMessage(R.string.clear_browsing_data_detail)
            .setPositiveButton(R.string.delete) { _, _ -> clearBrowsingData() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun clearBrowsingData() {
        tabPersistence.clear()
        val flags =
            StorageController.ClearFlags.ALL_CACHES or
                StorageController.ClearFlags.SITE_DATA or
                StorageController.ClearFlags.AUTH_SESSIONS
        runtime.storageController.clearData(flags).accept(
            {
                runOnUiThread {
                    resetTabs()
                    Toast.makeText(this, R.string.browsing_data_cleared, Toast.LENGTH_SHORT).show()
                }
            },
            {
                runOnUiThread {
                    Toast.makeText(this, R.string.storage_clear_failed, Toast.LENGTH_SHORT).show()
                }
            },
        )
    }

    private fun resetTabs() {
        if (activeTab != null) runCatching { geckoView.releaseSession() }
        activeTab = null
        tabs.forEach(::disposeTab)
        tabs.clear()
        updateTabButton()
        createTab(switchToTab = true)
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (tabSwitcher.isOpen()) {
            tabSwitcher.hide()
            return
        }
        val tab = activeTab
        if (tab != null && tab.canGoBack) {
            tab.session.goBack()
        } else if (tab != null) {
            goHome(tab)
        } else {
            super.onBackPressed()
        }
    }

    override fun onStart() {
        super.onStart()
        activeTab?.let { setTabActivity(it, active = true) }
    }

    override fun onStop() {
        persistTabsNow()
        activeTab?.let { setTabActivity(it, active = false) }
        super.onStop()
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            releaseTabThumbnails()
        }
    }

    private fun releaseTabThumbnails() {
        tabs.forEach { tab ->
            tab.thumbnail?.recycle()
            tab.thumbnail = null
        }
        refreshTabSwitcher()
    }

    override fun onDestroy() {
        persistTabsNow()
        handler.removeCallbacksAndMessages(null)
        protectionExtension?.let { extension ->
            extension.setMessageDelegate(null, NativeApp)
            tabs.forEach { tab ->
                runCatching {
                    tab.session.webExtensionController.setMessageDelegate(extension, null, NativeApp)
                }
            }
        }
        protectionExtension = null
        thumbnailExecutor.shutdownNow()
        mediaAnalysisExecutor.shutdownNow()
        (imageAnalyzer as? AutoCloseable)?.close()
        tabs.forEach { tab ->
            cancelBarrierTimeout(tab)
            setTabActivity(tab, active = false)
        }
        if (activeTab != null) {
            runCatching { geckoView.releaseSession() }
        }
        tabs.forEach(::disposeTab)
        tabs.clear()
        activeTab = null
        super.onDestroy()
    }

    private enum class TabDisplayState {
        Ready,
        Loading,
        Visible,
        Closed,
        Blocked,
    }

    private class BrowserTab(
        val id: Long,
        val session: GeckoSession,
        var url: String = InitialBlankPage,
        var title: String = "",
        var canGoBack: Boolean = false,
        var waitingForBarrier: Boolean = false,
        var displayState: TabDisplayState = TabDisplayState.Ready,
        var barrierTimeout: Runnable? = null,
        var needsRestore: Boolean = false,
        var thumbnail: Bitmap? = null,
        var recovering: Boolean = false,
    )

    private companion object {
        const val ExtensionLocation = "resource://android/assets/dag-protection/"
        const val ExtensionId = "dag-protection@glosh.local"
        const val NativeApp = "glosh.dag.protection"
        const val BarrierReadyMessage = "barrier-ready"
        const val MediaBytesMessage = "media-bytes"
        const val MediaDecisionMessage = "media-decision"
        const val MediaPresentationStatusMessage = "media-presentation-status"
        const val ViewportImagesReadyMessage = "viewport-images-ready"
        const val ProtectionProtocolVersion = 1
        const val MaxMediaCandidateIdLength = 80
        const val MediaAnalysisThreads = 2
        const val MediaAnalysisQueueCapacity = 8
        const val MediaTransportLogTag = "DagMediaTransport"
        const val PerformanceLogTag = "DagPerformance"
        const val BarrierTimeoutMillis = 12_000L
        const val InitialBlankPage = "about:blank"
        const val MaxTabs = 8
        const val MaxTabLabelLength = 36
        const val PersistTabsDelayMillis = 250L
        const val ThumbnailWidth = 300
        const val ThumbnailHeight = 450
        const val EnabledControlAlpha = 1f
        const val DisabledControlAlpha = 0.45f
        const val DefaultBrowserRoleRequestCode = 4_201
        const val BrowserSetupPreferences = "dag-browser-setup"
        const val DefaultBrowserPromptShownKey = "default-browser-prompt-shown"
    }
}
