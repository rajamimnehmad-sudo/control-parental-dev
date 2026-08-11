package com.contentfilter.dagbrowser

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.app.role.RoleManager
import android.content.ComponentCallbacks2
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ListView
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.recyclerview.widget.LinearLayoutManager
import org.json.JSONObject
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.StorageController
import org.mozilla.geckoview.WebExtension
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class DagBrowserActivity : Activity() {
    private lateinit var geckoView: GeckoView
    private lateinit var navigationSnapshot: ImageView
    private lateinit var browserRoot: View
    private lateinit var browserToolbar: View
    private lateinit var addressBar: View
    private lateinit var addressInput: EditText
    private lateinit var newPageButton: ImageButton
    private lateinit var securityButton: ImageButton
    private lateinit var goButton: ImageButton
    private lateinit var tabButton: TextView
    private lateinit var menuButton: ImageButton
    private lateinit var pageLoadProgress: ProgressBar
    private lateinit var safetyOverlay: View
    private lateinit var safetyCard: View
    private lateinit var safetyIcon: ImageView
    private lateinit var safetyShimmer: View
    private lateinit var safetyProgress: ProgressBar
    private lateinit var safetyTitle: TextView
    private lateinit var safetyDetail: TextView
    private lateinit var tabSwitcher: DagTabSwitcherView
    private lateinit var tabPersistence: DagTabPersistence
    private lateinit var tabThumbnailStore: DagTabThumbnailStore
    private lateinit var historyPersistence: DagHistoryPersistence
    private lateinit var favoritesPersistence: DagFavoritesPersistence
    private lateinit var imageAnalyzer: DagImageAnalyzer
    private lateinit var runtime: GeckoRuntime

    private val handler = Handler(Looper.getMainLooper())
    private val performanceTracker = DagPerformanceTracker(SystemClock::elapsedRealtime)
    private val tabs = mutableListOf<BrowserTab>()
    private val analyzerInitializationExecutor = Executors.newSingleThreadExecutor()
    private val thumbnailExecutor = Executors.newSingleThreadExecutor()
    private val mediaAnalysisSequence = AtomicLong(0L)
    private val mediaAnalysisThreadSequence = AtomicLong(0L)
    private val mediaAnalysisLifecycleGeneration = AtomicLong(0L)
    private val mediaAnalysisAccepting = AtomicBoolean(true)
    private val mediaAnalysisQueue = DagBoundedMediaTaskQueue(MediaAnalysisQueueCapacity)
    private val mediaDocumentRegistry = DagMediaDocumentRegistry()
    private val mediaAnalysisThreadFactory =
        ThreadFactory { work ->
            Thread(
                {
                    android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
                    work.run()
                },
                "dag-media-analysis-${mediaAnalysisThreadSequence.incrementAndGet()}",
            )
        }

    @Volatile
    private var activeMediaDecisionPort: WebExtension.Port? = null
    private val mediaAnalysisExecutor =
        ThreadPoolExecutor(
            MediaAnalysisThreads,
            MediaAnalysisThreads,
            0L,
            TimeUnit.MILLISECONDS,
            mediaAnalysisQueue,
            mediaAnalysisThreadFactory,
        )
    private var protectionExtension: WebExtension? = null
    private var extensionReady = false
    private var activeTab: BrowserTab? = null
    private var nextTabId = 1L
    private var nextTabActivationSequence = 1L
    private var restoringTabs = false
    private var pendingExternalUrl: String? = null
    private var pageListScreen: Dialog? = null
    private var browserMenu: PopupMenu? = null
    private var activeChoicePrompt: ActiveChoicePrompt? = null
    private var backInvokedCallback: OnBackInvokedCallback? = null
    private var loadingShimmerAnimator: ObjectAnimator? = null
    private var navigationFrameBitmap: Bitmap? = null
    private var navigationFrameTabId: Long? = null
    private var tabThumbnailResidencyRequested = false
    private val persistTabsRunnable = Runnable(::persistTabsNow)
    private val restoreMediaAnalysisParallelism =
        Runnable { setMediaAnalysisParallelism(MediaAnalysisThreads) }

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
                        connectContentPort(port, sender, senderTab)
                    }
                    correctExtension &&
                        sender.session == null &&
                        sender.environmentType == WebExtension.MessageSender.ENV_TYPE_EXTENSION -> {
                        connectDecisionPort(port)
                    }
                    else -> port.disconnect()
                }
            }
        }

    private fun connectContentPort(
        port: WebExtension.Port,
        sender: WebExtension.MessageSender,
        senderTab: BrowserTab,
    ) {
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
                    handleContentPortMessage(payload, sender, senderTab)
                }
            },
        )
    }

    private fun handleContentPortMessage(
        payload: JSONObject,
        sender: WebExtension.MessageSender,
        senderTab: BrowserTab,
    ) {
        if (
            payload.optInt("version") != ProtectionProtocolVersion ||
            !sender.isTopLevel
        ) {
            return
        }

        when (payload.optString("type")) {
            BarrierReadyMessage -> handleBarrierReady(payload, senderTab)
            DocumentSanitizedReadyMessage -> handleDocumentSanitizedReady(senderTab, payload)
            PreviewEligibilityMessage -> applyPreviewEligibility(senderTab, payload)
        }
    }

    private fun handleBarrierReady(
        payload: JSONObject,
        senderTab: BrowserTab,
    ) {
        if (!senderTab.waitingForBarrier) return
        senderTab.previewDocumentToken =
            payload.optString("documentToken")
                .takeIf(PreviewDocumentTokenPattern::matches)
        if (packageName.endsWith(".dev")) {
            Log.i(
                TabPreviewLogTag,
                "barrier tab=${senderTab.id} token=${senderTab.previewDocumentToken != null}",
            )
        }
        confirmProtectedBarrier(senderTab)
    }

    private fun handleDocumentSanitizedReady(
        senderTab: BrowserTab,
        payload: JSONObject,
    ) {
        if (!senderTab.waitingForBarrier) return
        val documentToken =
            payload.optString("documentToken")
                .takeIf(PreviewDocumentTokenPattern::matches)
                ?: return
        if (documentToken != senderTab.previewDocumentToken) return
        senderTab.documentSanitizedForNavigation = true
        maybeCompleteProtectedLoad(senderTab)
    }

    private fun connectDecisionPort(port: WebExtension.Port) {
        beginMediaDecisionPort(port)
        if (packageName.endsWith(".dev")) {
            Log.i(MediaTransportLogTag, "decision_port=connected")
        }
        port.setDelegate(
            object : WebExtension.PortDelegate {
                override fun onPortMessage(
                    message: Any,
                    sourcePort: WebExtension.Port,
                ) {
                    handleDecisionPortMessage(message, sourcePort)
                }
            },
        )
    }

    private fun handleDecisionPortMessage(
        message: Any,
        sourcePort: WebExtension.Port,
    ) {
        if (sourcePort !== activeMediaDecisionPort) return
        val payload = message as? JSONObject ?: return
        if (payload.optInt("version") != ProtectionProtocolVersion) return

        when (payload.optString("type")) {
            MediaBytesMessage -> mediaBytesDecisionFromPort(payload, sourcePort)
            MediaDocumentCurrentMessage -> handleMediaDocumentCurrent(payload)
            MediaDocumentRetiredMessage -> handleMediaDocumentRetired(payload)
            ViewportImagesReadyMessage -> handleViewportImagesReady(payload)
        }
    }

    private fun handleMediaDocumentCurrent(payload: JSONObject) {
        val identity = mediaDocumentIdentity(payload) ?: return
        mediaDocumentRegistry.markCurrent(identity.tabId, identity.documentToken)
        mediaAnalysisQueue.discardMatching { task ->
            task.documentIdentity?.let {
                it.tabId == identity.tabId && it != identity
            } == true
        }
    }

    private fun handleMediaDocumentRetired(payload: JSONObject) {
        val identity = mediaDocumentIdentity(payload) ?: return
        mediaDocumentRegistry.retire(identity.tabId, identity.documentToken)
        mediaAnalysisQueue.discardMatching { task -> task.documentIdentity == identity }
    }

    private fun handleViewportImagesReady(payload: JSONObject) {
        val identity = mediaDocumentIdentity(payload) ?: return
        if (!mediaDocumentRegistry.isCurrent(identity.tabId, identity.documentToken)) return
        val documentToken = payload.optString("documentToken")
        if (!PreviewDocumentTokenPattern.matches(documentToken)) return
        val tab = tabs.firstOrNull { it.previewDocumentToken == documentToken } ?: return
        if (!tab.waitingForBarrier) return
        if (tab === activeTab) recordPerformanceMetric(DagPerformanceMetric.ViewportImagesReady)
        tab.protectedContentReadyForNavigation = true
        maybeCompleteProtectedLoad(tab)
    }

    private fun mediaDocumentIdentity(payload: JSONObject): DagMediaDocumentIdentity? {
        val tabId = payload.optInt("tabId", -1)
        val documentKey = payload.optString("documentKey")
        if (tabId < 0 || !PreviewDocumentTokenPattern.matches(documentKey)) return null
        return DagMediaDocumentIdentity(tabId, documentKey)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.GLOSHIA_LAB_FIXTURE) Log.i("DagLabHarness", "activity_onCreate")
        setContentView(R.layout.activity_dag_browser)
        pendingExternalUrl = safeExternalUrl(intent)
        applySystemBarInsets()
        tabPersistence = DagTabPersistence(applicationContext)
        tabThumbnailStore = DagTabThumbnailStore(applicationContext)
        historyPersistence = DagHistoryPersistence(applicationContext)
        favoritesPersistence = DagFavoritesPersistence(applicationContext)
        bindViews()
        configureControls()
        registerModernBackCallback()
        installProtectionExtension()
    }

    private fun registerModernBackCallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val callback = OnBackInvokedCallback(::handleBackNavigation)
        onBackInvokedDispatcher.registerOnBackInvokedCallback(
            OnBackInvokedDispatcher.PRIORITY_DEFAULT,
            callback,
        )
        backInvokedCallback = callback
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
        browserRoot = findViewById(R.id.browser_root)
        browserToolbar = findViewById(R.id.browser_toolbar)
        addressBar = findViewById(R.id.address_bar)
        geckoView = findViewById(R.id.gecko_view)
        navigationSnapshot = findViewById(R.id.navigation_snapshot)
        addressInput = findViewById(R.id.address_input)
        newPageButton = findViewById(R.id.new_page_button)
        securityButton = findViewById(R.id.security_button)
        goButton = findViewById(R.id.go_button)
        tabButton = findViewById(R.id.tab_button)
        menuButton = findViewById(R.id.menu_button)
        pageLoadProgress = findViewById(R.id.page_load_progress)
        safetyOverlay = findViewById(R.id.safety_overlay)
        safetyCard = findViewById(R.id.safety_card)
        safetyIcon = findViewById(R.id.safety_icon)
        safetyShimmer = findViewById(R.id.safety_shimmer)
        safetyProgress = findViewById(R.id.safety_progress)
        safetyTitle = findViewById(R.id.safety_title)
        safetyDetail = findViewById(R.id.safety_detail)
        tabSwitcher = findViewById(R.id.tab_switcher)
    }

    private fun configureControls() {
        setNavigationControlsEnabled(false)
        goButton.setOnClickListener {
            if (shouldShowReloadAction()) reloadActivePage() else navigateFromInput()
        }
        newPageButton.setOnClickListener { createTab(switchToTab = true) }
        securityButton.setOnClickListener { showSecurityDetails() }
        addressInput.setOnEditorActionListener { _, actionId, event ->
            val submitted =
                actionId == EditorInfo.IME_ACTION_GO ||
                    (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP)
            if (submitted) navigateFromInput()
            submitted
        }
        addressInput.setOnFocusChangeListener { _, _ -> updateAddressActionButton() }
        tabButton.setOnClickListener { showTabSwitcher() }
        menuButton.setOnClickListener { showBrowserMenu() }
        browserMenu = createBrowserMenu()
        tabSwitcher.setListener(
            object : DagTabSwitcherView.Listener {
                override fun onTabSelected(tabId: Long) {
                    val tab = tabs.firstOrNull { it.id == tabId } ?: return
                    hideTabSwitcher()
                    switchTo(tab)
                }

                override fun onTabClosed(tabId: Long) {
                    tabs.firstOrNull { it.id == tabId }?.let(::closeTab)
                    refreshTabSwitcher()
                }

                override fun onNewTab() {
                    hideTabSwitcher()
                    createTab(switchToTab = true)
                }

                override fun onCloseAllTabs() {
                    confirmCloseAllTabs()
                }

                override fun onTabsReordered(tabIds: List<Long>) {
                    reorderTabs(tabIds)
                }

                override fun onSwitcherClosed() {
                    hideTabSwitcher()
                }
            },
        )
    }

    private fun configureSession(tab: BrowserTab) {
        tab.session.promptDelegate =
            object : GeckoSession.PromptDelegate {
                override fun onChoicePrompt(
                    session: GeckoSession,
                    prompt: GeckoSession.PromptDelegate.ChoicePrompt,
                ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> = showChoicePrompt(session, prompt)
            }
        tab.session.contentDelegate =
            object : GeckoSession.ContentDelegate {
                override fun onTitleChange(
                    session: GeckoSession,
                    title: String?,
                ) {
                    tab.title = title.orEmpty()
                    if (tab.displayState == TabDisplayState.Visible) recordHistory(tab)
                    schedulePersistTabs()
                    refreshTabSwitcher()
                }

                override fun onFirstContentfulPaint(session: GeckoSession) {
                    if (!tab.waitingForBarrier) return
                    tab.protectedContentReadyForNavigation = true
                    maybeCompleteProtectedLoad(tab)
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
                                createTab(switchToTab = true, initialUrl = decision.url)
                                GeckoResult.fromValue(AllowOrDeny.DENY)
                            }
                        }
                    }
                    return when (
                        val decision =
                            DagNavigationPolicy.decideLoad(
                                url = request.uri,
                                opensNewWindow = false,
                            )
                    ) {
                        DagLoadDecision.Allow -> {
                            maybeCoverAcceptedNavigation(tab, request)
                            GeckoResult.fromValue(AllowOrDeny.ALLOW)
                        }
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
                    if (DagNavigationPolicy.sanitizeTopLevel(uri) != uri) {
                        return null
                    }
                    val newTab =
                        createTab(
                            switchToTab = false,
                            initialUrl = uri,
                            reuseBlank = false,
                        ) ?: return null
                    newTab.needsRestore = false
                    switchTo(newTab)
                    return GeckoResult.fromValue(newTab.session)
                }
            }
        tab.session.progressDelegate =
            object : GeckoSession.ProgressDelegate {
                override fun onPageStart(
                    session: GeckoSession,
                    url: String,
                ) {
                    tab.loadProgress = MinimumPageLoadProgress
                    val keepCurrentPageVisible =
                        tab.keepCurrentPageVisibleDuringReload &&
                            DagLoadTransitionPolicy.targetsSameDocument(tab.url, url)
                    if (tab === activeTab && !keepCurrentPageVisible) {
                        showNavigationSnapshot(tab)
                    }
                    if (tab.displayState != TabDisplayState.Loading) {
                        markTabThumbnailStale(tab)
                    }
                    tab.url = url
                    tab.needsRestore = false
                    schedulePersistTabs()
                    refreshTabSwitcher()
                    if (url == InitialBlankPage) {
                        tab.loadProgress = 0
                        tab.title = ""
                        cancelBarrierTimeout(tab)
                        tab.waitingForBarrier = false
                        tab.displayState = TabDisplayState.Ready
                        if (tab === activeTab) renderActiveTab()
                    } else {
                        if (tab === activeTab) addressInput.setText(url)
                        beginProtectedLoad(
                            tab = tab,
                            keepCurrentPageVisible = keepCurrentPageVisible,
                        )
                    }
                }

                override fun onProgressChange(
                    session: GeckoSession,
                    progress: Int,
                ) {
                    tab.loadProgress = progress.coerceIn(MinimumPageLoadProgress, 100)
                    if (tab === activeTab) renderPageLoadProgress(tab)
                }

                override fun onPageStop(
                    session: GeckoSession,
                    success: Boolean,
                ) {
                    finishPageLoadProgress(tab)
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

    private fun showChoicePrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.ChoicePrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
        if (session !== activeTab?.session || isFinishing || isDestroyed) {
            return GeckoResult.fromValue(prompt.dismiss())
        }
        dismissActiveChoicePrompt()
        val rows = DagChoicePromptPolicy.flatten(prompt.choices, getString(R.string.unnamed_option))
        if (rows.isEmpty()) {
            return GeckoResult.fromValue(prompt.dismiss())
        }

        val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
        val multiple = prompt.type == GeckoSession.PromptDelegate.ChoicePrompt.Type.MULTIPLE
        val rowLayout =
            if (multiple) {
                android.R.layout.simple_list_item_multiple_choice
            } else {
                android.R.layout.simple_list_item_single_choice
            }
        val adapter =
            object : ArrayAdapter<DagChoicePromptRow>(this, rowLayout, rows) {
                override fun isEnabled(position: Int): Boolean = rows[position].enabled

                override fun getView(
                    position: Int,
                    convertView: View?,
                    parent: ViewGroup,
                ): View =
                    super.getView(position, convertView, parent).also { rowView ->
                        rowView.findViewById<TextView>(android.R.id.text1)?.apply {
                            text = rows[position].label
                            isEnabled = rows[position].enabled
                            alpha =
                                if (rows[position].enabled) {
                                    EnabledChoiceAlpha
                                } else {
                                    DisabledChoiceAlpha
                                }
                        }
                    }
            }
        val listView =
            ListView(this).apply {
                choiceMode =
                    if (multiple) {
                        ListView.CHOICE_MODE_MULTIPLE
                    } else {
                        ListView.CHOICE_MODE_SINGLE
                    }
                this.adapter = adapter
                rows.forEachIndexed { index, row ->
                    if (row.selected) setItemChecked(index, true)
                }
            }

        var completed = false
        lateinit var dialog: AlertDialog

        fun complete(response: GeckoSession.PromptDelegate.PromptResponse) {
            if (completed) return
            completed = true
            if (activeChoicePrompt?.dialog === dialog) activeChoicePrompt = null
            result.complete(response)
        }

        val builder =
            AlertDialog.Builder(this)
                .setTitle(if (multiple) R.string.choose_options else R.string.choose_option)
                .setView(listView)
                .setNegativeButton(R.string.cancel) { _, _ -> complete(prompt.dismiss()) }

        if (multiple) {
            builder.setPositiveButton(R.string.confirm) { _, _ ->
                val selected =
                    rows.indices
                        .filter { rows[it].enabled && listView.isItemChecked(it) }
                        .map { rows[it].choice }
                        .toTypedArray()
                complete(prompt.confirm(selected))
            }
        }

        dialog = builder.create()
        listView.setOnItemClickListener { _, _, position, _ ->
            val row = rows[position]
            if (!row.enabled || multiple) return@setOnItemClickListener
            complete(prompt.confirm(row.choice))
            dialog.dismiss()
        }
        dialog.setOnCancelListener { complete(prompt.dismiss()) }
        dialog.setOnDismissListener {
            if (!completed) complete(prompt.dismiss())
        }
        activeChoicePrompt =
            ActiveChoicePrompt(
                dialog = dialog,
                dismissPrompt = {
                    complete(prompt.dismiss())
                    dialog.dismiss()
                },
            )
        dialog.show()
        return result
    }

    private fun dismissActiveChoicePrompt() {
        val active = activeChoicePrompt ?: return
        activeChoicePrompt = null
        active.dismissPrompt()
    }

    private fun createTab(
        switchToTab: Boolean,
        initialUrl: String? = null,
        restoredTab: DagPersistedTab? = null,
        privateTab: Boolean = false,
        reuseBlank: Boolean = true,
    ): BrowserTab? {
        if (!extensionReady || !DagTabCapacityPolicy.canCreate(tabs.size)) {
            if (!DagTabCapacityPolicy.canCreate(tabs.size)) {
                showTabSwitcher()
                Toast.makeText(this, R.string.tab_limit_reached, Toast.LENGTH_SHORT).show()
            }
            return null
        }
        if (protectionExtension == null) return null
        if (reuseBlank && restoredTab == null && initialUrl == null) {
            tabs.firstOrNull { it.url == InitialBlankPage && it.isPrivate == privateTab }?.let { blankTab ->
                if (switchToTab) switchTo(blankTab)
                return blankTab
            }
        }
        val requestedUrl = restoredTab?.url ?: initialUrl ?: InitialBlankPage
        val tab =
            BrowserTab(
                id = nextTabId++,
                session =
                    GeckoSession(
                        GeckoSessionSettings.Builder()
                            .usePrivateMode(privateTab)
                            .suspendMediaWhenInactive(true)
                            .build(),
                    ),
                url = requestedUrl,
                title = restoredTab?.title.orEmpty(),
                isPrivate = privateTab,
                needsRestore = requestedUrl != InitialBlankPage,
                previewKey =
                    restoredTab?.previewKey
                        ?.takeIf(DagTabThumbnailKeyPolicy::isValid)
                        ?: UUID.randomUUID().toString().replace("-", ""),
            )
        tabs += tab
        if (restoredTab != null) restoreTabThumbnail(tab)
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
        analyzerInitializationExecutor.execute {
            val analyzer =
                if (BuildConfig.GLOSHIA_VISUAL_ENABLED) {
                    DagLifecycleImageAnalyzer(
                        DagOnDeviceImageAnalyzer.create(applicationContext).also {
                            if (BuildConfig.GLOSHIA_LAB_FIXTURE) {
                                Log.i("DagLabHarness", "analyzer_ready")
                            }
                        },
                    )
                } else {
                    UnavailableDagImageAnalyzer
                }
            handler.post {
                if (isFinishing || isDestroyed || !mediaAnalysisAccepting.get()) {
                    (analyzer as? AutoCloseable)?.close()
                } else {
                    imageAnalyzer = analyzer
                    clearInterceptedMediaCacheAfterUpdate()
                }
            }
            analyzerInitializationExecutor.shutdown()
        }
        runtime = DagGeckoRuntime.get(this)
    }

    private fun clearInterceptedMediaCacheAfterUpdate() {
        val preferences = getSharedPreferences(CacheMaintenancePreferences, MODE_PRIVATE)
        val recordedRevision = preferences.getInt(CacheMaintenanceRevisionKey, 0)
        if (recordedRevision == InterceptedMediaCacheRevision) {
            ensureProtectionExtension()
            return
        }
        if (recordedRevision == 0) {
            // Existing installs used versionCode as the cache key, which forced a full Gecko cache
            // clear after every APK. Migrate without clearing: fresh installs have no stale cache,
            // and existing responses still cross the response filter when read from cache.
            preferences.edit()
                .putInt(CacheMaintenanceRevisionKey, InterceptedMediaCacheRevision)
                .remove(LegacyCacheMaintenanceVersionKey)
                .apply()
            ensureProtectionExtension()
            return
        }
        runtime.storageController.clearData(StorageController.ClearFlags.ALL_CACHES).accept(
            {
                preferences.edit()
                    .putInt(CacheMaintenanceRevisionKey, InterceptedMediaCacheRevision)
                    .apply()
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) ensureProtectionExtension()
                }
            },
            {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) ensureProtectionExtension()
                }
            },
        )
    }

    private fun ensureProtectionExtension() {
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
        showNavigationSnapshot(tab)
        beginProtectedLoad(tab, startNewPerformanceNavigation = true)
        tab.session.loadUri(safeUrl)
    }

    private fun shouldShowReloadAction(): Boolean {
        val tab = activeTab ?: return false
        return tab.url != InitialBlankPage && !addressInput.hasFocus()
    }

    private fun updateAddressActionButton() {
        val reload = shouldShowReloadAction()
        goButton.setImageResource(if (reload) R.drawable.ic_dag_reload else R.drawable.ic_dag_search)
        goButton.contentDescription = getString(if (reload) R.string.reload else R.string.search)
    }

    private fun reloadActivePage() {
        val tab = activeTab ?: return
        if (!extensionReady || tab.url == InitialBlankPage || !tab.session.isOpen) return
        beginProtectedLoad(
            tab = tab,
            startNewPerformanceNavigation = true,
            keepCurrentPageVisible = tab.displayState == TabDisplayState.Visible,
        )
        tab.session.reload()
    }

    private fun maybeCoverAcceptedNavigation(
        tab: BrowserTab,
        request: GeckoSession.NavigationDelegate.LoadRequest,
    ) {
        if (
            DagLoadTransitionPolicy.shouldCover(
                currentUrl = tab.url,
                targetUrl = request.uri,
                targetsCurrentWindow =
                    request.target == GeckoSession.NavigationDelegate.TARGET_WINDOW_CURRENT,
                pageVisible = tab.displayState == TabDisplayState.Visible,
                barrierAlreadyWaiting =
                    tab.waitingForBarrier && !tab.keepCurrentPageVisibleDuringReload,
            )
        ) {
            showNavigationSnapshot(tab)
            beginProtectedLoad(
                tab = tab,
                startNewPerformanceNavigation = true,
            )
        }
    }

    private fun showNavigationSnapshot(tab: BrowserTab) {
        if (navigationFrameTabId != tab.id || navigationFrameBitmap == null) return
        navigationSnapshot.visibility = View.VISIBLE
    }

    private fun clearNavigationSnapshot() {
        navigationSnapshot.visibility = View.GONE
    }

    private fun updateNavigationFrame(
        tab: BrowserTab,
        frame: Bitmap,
    ): Boolean {
        if (
            tab !== activeTab ||
            tab.previewRestricted
        ) {
            return false
        }
        val previous = navigationFrameBitmap
        navigationFrameBitmap = frame
        navigationFrameTabId = tab.id
        navigationSnapshot.setImageBitmap(frame)
        previous?.recycle()
        return true
    }

    private fun releaseNavigationFrame() {
        clearNavigationSnapshot()
        navigationSnapshot.setImageDrawable(null)
        navigationFrameBitmap?.recycle()
        navigationFrameBitmap = null
        navigationFrameTabId = null
    }

    private fun beginProtectedLoad(
        tab: BrowserTab,
        startNewPerformanceNavigation: Boolean = false,
        keepCurrentPageVisible: Boolean = false,
    ) {
        if (tab.displayState != TabDisplayState.Loading) {
            markTabThumbnailStale(tab)
        }
        if (tab === activeTab && (startNewPerformanceNavigation || !tab.waitingForBarrier)) {
            recordPerformanceEvent(performanceTracker.begin())
        }
        tab.waitingForBarrier = true
        tab.barrierReadyForNavigation = false
        tab.protectedContentReadyForNavigation = false
        tab.documentSanitizedForNavigation = false
        tab.keepCurrentPageVisibleDuringReload =
            keepCurrentPageVisible && tab.displayState == TabDisplayState.Visible
        if (!tab.keepCurrentPageVisibleDuringReload) {
            tab.displayState = TabDisplayState.Loading
        }
        scheduleBarrierTimeout(tab)
        if (tab === activeTab) renderActiveTab()
    }

    private fun confirmProtectedBarrier(tab: BrowserTab) {
        tab.barrierReadyForNavigation = true
        maybeCompleteProtectedLoad(tab)
    }

    private fun maybeCompleteProtectedLoad(tab: BrowserTab) {
        if (
            !tab.barrierReadyForNavigation ||
            !tab.protectedContentReadyForNavigation ||
            !tab.documentSanitizedForNavigation
        ) {
            return
        }
        tab.waitingForBarrier = false
        tab.keepCurrentPageVisibleDuringReload = false
        cancelBarrierTimeout(tab)
        tab.displayState = TabDisplayState.Visible
        recordHistory(tab)
        if (tab === activeTab) {
            revealProtectedPage()
            geckoView.postOnAnimation(::clearNavigationSnapshot)
            captureActiveTabThumbnail()
        }
    }

    private fun revealProtectedPage() {
        updateLoadingShimmer(enabled = false)
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
                    tab.keepCurrentPageVisibleDuringReload = false
                    tab.displayState = TabDisplayState.Closed
                    tab.loadProgress = 0
                    tab.barrierTimeout = null
                    if (tab === activeTab) {
                        clearNavigationSnapshot()
                        renderActiveTab()
                    }
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

    private fun beginMediaDecisionPort(port: WebExtension.Port) {
        // Keep the transport unavailable while the previous connection is invalidated. The new
        // port cannot deliver messages until its delegate is installed immediately after this call.
        activeMediaDecisionPort = null
        mediaAnalysisLifecycleGeneration.incrementAndGet()
        mediaAnalysisQueue.discardMatching { true }
        mediaDocumentRegistry.clear()
        activeMediaDecisionPort = port
    }

    private fun mediaBytesDecisionFromPort(
        payload: JSONObject,
        port: WebExtension.Port,
    ) {
        val candidateId = payload.optString("candidateId").take(MaxMediaCandidateIdLength)
        val priority = DagMediaAnalysisPriority.fromWire(payload.optString("priority"))
        if (!mediaAnalysisAccepting.get() || activeMediaDecisionPort !== port) return
        val documentIdentity = mediaDocumentIdentity(payload)
        if (documentIdentity == null) {
            runCatching { port.postMessage(decisionPayload(expiredMediaDecision(candidateId))) }
            return
        }
        val generation = mediaAnalysisLifecycleGeneration.get()
        val completeDecision: (DagMediaDecision) -> Unit = completeDecision@{ decision ->
            if (!mediaAnalysisAccepting.get() || activeMediaDecisionPort !== port) {
                return@completeDecision
            }
            handler.post {
                if (
                    mediaAnalysisAccepting.get() &&
                    activeMediaDecisionPort === port &&
                    !isFinishing &&
                    !isDestroyed
                ) {
                    runCatching { port.postMessage(decisionPayload(decision)) }
                }
            }
        }
        val remainingMillis =
            DagMediaAnalysisDeadline.remainingMillis(
                sentAtEpochMillis = payload.optLong("sentAtEpochMillis", -1L),
                nowEpochMillis = System.currentTimeMillis(),
                lifetimeMillis = MediaAnalysisLifetimeMillis,
                allowedFutureSkewMillis = MediaAnalysisAllowedFutureSkewMillis,
            )
        if (remainingMillis == null) {
            completeDecision(expiredMediaDecision(candidateId))
            return
        }
        val queuedAt = SystemClock.elapsedRealtime()
        val lease =
            DagMediaAnalysisLease(
                generation = generation,
                deadlineElapsedRealtime = queuedAt + remainingMillis,
                currentGeneration = mediaAnalysisLifecycleGeneration::get,
                elapsedRealtime = SystemClock::elapsedRealtime,
                acceptingWork = mediaAnalysisAccepting::get,
                documentCurrent = {
                    mediaDocumentRegistry.isCurrent(
                        documentIdentity.tabId,
                        documentIdentity.documentToken,
                    )
                },
            )
        if (!lease.canContinue() || activeMediaDecisionPort !== port) {
            completeDecision(expiredMediaDecision(candidateId))
            return
        }
        try {
            mediaAnalysisExecutor.execute(
                DagPrioritizedMediaTask(
                    priority = priority,
                    sequence = mediaAnalysisSequence.getAndIncrement(),
                    documentIdentity = documentIdentity,
                    onDiscard = {
                        completeDecision(expiredMediaDecision(candidateId))
                    },
                ) {
                    val queueWaitMillis =
                        (SystemClock.elapsedRealtime() - queuedAt).coerceAtLeast(0L)
                    val decision =
                        if (!lease.canContinue()) {
                            expiredMediaDecision(candidateId)
                        } else {
                            runCatching {
                                mediaBytesDecision(payload, priority, queueWaitMillis, lease)
                            }.getOrElse {
                                DagMediaDecision(
                                    candidateId = candidateId,
                                    action = DagMediaAction.Block,
                                    reason = DagMediaBytesPolicy.InvalidPayloadReason,
                                )
                            }
                        }
                    completeDecision(decision)
                },
            )
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

    private fun mediaBytesDecision(
        payload: JSONObject,
        priority: DagMediaAnalysisPriority,
        queueWaitMillis: Long,
        lease: DagMediaAnalysisLease,
    ): DagMediaDecision {
        val startedAt = SystemClock.elapsedRealtime()
        val trace = DagMediaPipelineTrace()
        val bytesPayload =
            DagMediaBytesPayload(
                candidateId = payload.optString("candidateId"),
                sourceUrl = payload.optString("sourceUrl"),
                declaredByteLength = payload.optInt("byteLength", -1),
                bytesBase64 = payload.optString("bytesBase64"),
            )
        val decision =
            DagMediaBytesPolicy.decide(
                payload = bytesPayload,
                analyzer = imageAnalyzer,
                classificationMode =
                    if (BuildConfig.GLOSHIA_VISUAL_ENABLED) {
                        DagMediaClassificationMode.Enabled
                    } else {
                        DagMediaClassificationMode.DisabledForDevCompatibility
                    },
                redactionMode =
                    if (BuildConfig.GLOSHIA_LAB_FIXTURE) {
                        DagMediaRedactionMode.LabStrongFrosted
                    } else {
                        DagMediaRedactionMode.Disabled
                    },
                trace = trace,
                workGuard = lease,
            )
        val currentDecision =
            if (lease.canContinue()) decision else expiredMediaDecision(bytesPayload.candidateId)
        val deliverableDecision =
            if (currentDecision.action == DagMediaAction.Block && currentDecision.replacementBytesBase64 == null) {
                currentDecision.copy(
                    replacementBytesBase64 =
                        DagBlockedImagePlaceholder.renderBase64(
                            currentDecision.imageWidth,
                            currentDecision.imageHeight,
                        ),
                )
            } else {
                currentDecision
            }
        if (packageName.endsWith(".dev") || BuildConfig.GLOSHIA_LAB_FIXTURE) {
            val score =
                currentDecision.filterProbability?.let {
                    " score=${String.format(Locale.US, "%.4f", it)}"
                }.orEmpty()
            val fullScore =
                trace.fullImageProbability?.let {
                    " full_score=${String.format(Locale.US, "%.4f", it)}"
                }.orEmpty()
            Log.i(
                MediaTransportLogTag,
                "pipeline path=intercept " +
                    "priority=${priority.name.lowercase(Locale.US)} " +
                    "bytes=${bytesPayload.declaredByteLength} " +
                    "bridge_ms=${bridgeElapsedMillis(payload)} queue_ms=$queueWaitMillis " +
                    "base64_ms=${trace.metric(DagMediaPipelineStage.Base64Decode)} " +
                    "vector_ms=${trace.metric(DagMediaPipelineStage.SafeVectorCheck)} " +
                    "bounds_ms=${trace.metric(DagMediaPipelineStage.BoundsRead)} " +
                    "preprocess_ms=${trace.metric(DagMediaPipelineStage.Preprocess)} " +
                    "inference_ms=${trace.metric(DagMediaPipelineStage.Inference)} " +
                    "inferences=${trace.inferenceCount} prepared=${trace.preparedImageCount} " +
                    "regional=${trace.regionalImageCount} action=${currentDecision.action.wireValue} " +
                    "size=${currentDecision.imageWidth ?: 0}x${currentDecision.imageHeight ?: 0} " +
                    "replacement_chars=${currentDecision.replacementBytesBase64?.length ?: 0} " +
                    "reason=${currentDecision.reason}$score$fullScore " +
                    "basis=${trace.decisionBasis.wireValue} " +
                    "native_ms=${SystemClock.elapsedRealtime() - startedAt}",
            )
        }
        return deliverableDecision
    }

    private fun expiredMediaDecision(candidateId: String) =
        DagMediaDecision(
            candidateId = candidateId.take(MaxMediaCandidateIdLength),
            action = DagMediaAction.Block,
            reason = DagMediaBytesPolicy.AnalysisExpiredReason,
        )

    private fun bridgeElapsedMillis(payload: JSONObject): Long {
        val sentAt = payload.optLong("sentAtEpochMillis", -1L)
        if (sentAt <= 0L) return -1L
        return (System.currentTimeMillis() - sentAt).takeIf { it in 0..MaxPipelineMetricMillis } ?: -1L
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> beginTouchInteraction()
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL,
            -> finishTouchInteraction()
        }
        return super.dispatchTouchEvent(event)
    }

    private fun beginTouchInteraction() {
        handler.removeCallbacks(restoreMediaAnalysisParallelism)
        setMediaAnalysisParallelism(DagMediaInteractionPolicy.ActiveAnalysisThreads)
    }

    private fun finishTouchInteraction() {
        handler.removeCallbacks(restoreMediaAnalysisParallelism)
        handler.postDelayed(
            restoreMediaAnalysisParallelism,
            DagMediaInteractionPolicy.RestoreDelayMillis,
        )
    }

    private fun setMediaAnalysisParallelism(threads: Int) {
        if (mediaAnalysisExecutor.isShutdown || mediaAnalysisExecutor.corePoolSize == threads) return
        if (threads < mediaAnalysisExecutor.corePoolSize) {
            mediaAnalysisExecutor.corePoolSize = threads
            mediaAnalysisExecutor.maximumPoolSize = threads
        } else {
            mediaAnalysisExecutor.maximumPoolSize = threads
            mediaAnalysisExecutor.corePoolSize = threads
        }
        if (packageName.endsWith(".dev")) {
            Log.i(MediaTransportLogTag, "interaction_threads=$threads")
        }
    }

    private fun DagMediaPipelineTrace.metric(stage: DagMediaPipelineStage): String =
        String.format(Locale.US, "%.2f", elapsedMillis(stage))

    private fun decisionPayload(decision: DagMediaDecision): JSONObject {
        return JSONObject()
            .put("type", MediaDecisionMessage)
            .put("version", ProtectionProtocolVersion)
            .put("candidateId", decision.candidateId)
            .put("action", decision.action.wireValue)
            .put("reason", decision.reason)
            .apply {
                decision.imageWidth?.let { put("imageWidth", it) }
                decision.imageHeight?.let { put("imageHeight", it) }
                decision.replacementBytesBase64?.let { put("replacementBytesBase64", it) }
            }
    }

    private fun showClosedPage(tab: BrowserTab) {
        invalidateTabThumbnail(tab)
        tab.waitingForBarrier = false
        tab.keepCurrentPageVisibleDuringReload = false
        cancelBarrierTimeout(tab)
        tab.displayState = TabDisplayState.Closed
        tab.loadProgress = 0
        if (tab === activeTab) {
            clearNavigationSnapshot()
            renderActiveTab()
        }
    }

    private fun showBlockedNavigation(tab: BrowserTab) {
        invalidateTabThumbnail(tab)
        tab.waitingForBarrier = false
        tab.keepCurrentPageVisibleDuringReload = false
        cancelBarrierTimeout(tab)
        tab.displayState = TabDisplayState.Blocked
        tab.loadProgress = 0
        if (tab === activeTab) {
            clearNavigationSnapshot()
            renderActiveTab()
        }
    }

    private fun showOverlay(
        title: String,
        detail: String,
        spinning: Boolean,
        shimmer: Boolean = false,
    ) {
        safetyOverlay.visibility = View.VISIBLE
        if (shimmer) {
            safetyCard.setBackgroundColor(Color.TRANSPARENT)
        } else {
            safetyCard.setBackgroundResource(R.drawable.dag_overlay_card_background)
        }
        safetyIcon.visibility = if (shimmer) View.GONE else View.VISIBLE
        safetyProgress.visibility = if (spinning && !shimmer) View.VISIBLE else View.GONE
        safetyTitle.text = title
        safetyTitle.visibility = if (title.isBlank()) View.GONE else View.VISIBLE
        safetyDetail.text = detail
        safetyDetail.visibility = if (detail.isBlank()) View.GONE else View.VISIBLE
        updateLoadingShimmer(shimmer)
    }

    private fun updateLoadingShimmer(enabled: Boolean) {
        if (!enabled) {
            loadingShimmerAnimator?.cancel()
            loadingShimmerAnimator = null
            safetyShimmer.translationX = 0f
            safetyShimmer.visibility = View.GONE
            return
        }
        safetyShimmer.visibility = View.VISIBLE
        if (loadingShimmerAnimator != null) return
        val travel = 180f * resources.displayMetrics.density
        loadingShimmerAnimator =
            ObjectAnimator.ofFloat(safetyShimmer, View.TRANSLATION_X, -travel, travel).apply {
                duration = LoadingShimmerDurationMillis
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.RESTART
                start()
            }
    }

    private fun setNavigationControlsEnabled(enabled: Boolean) {
        addressInput.isEnabled = enabled
        newPageButton.isEnabled = enabled
        securityButton.isEnabled = enabled
        goButton.isEnabled = enabled
        val alpha = if (enabled) EnabledControlAlpha else DisabledControlAlpha
        addressInput.alpha = alpha
        newPageButton.alpha = alpha
        securityButton.alpha = alpha
        goButton.alpha = alpha
    }

    private fun showSecurityDetails() {
        val tab = activeTab
        val secureConnection = tab?.url?.startsWith("https://", ignoreCase = true) == true
        val insecureConnection = tab?.url?.startsWith("http://", ignoreCase = true) == true
        val pageProtection = tab?.displayState == TabDisplayState.Visible && !tab.waitingForBarrier
        val newPage = tab == null || tab.url == InitialBlankPage
        val host = tab?.url?.let { runCatching { Uri.parse(it).host }.getOrNull() }.orEmpty()
        val pageLabel = host.ifBlank { getString(R.string.security_new_page) }
        val connectionLabel =
            when {
                newPage -> R.string.security_connection_unavailable
                secureConnection -> R.string.security_secure_connection
                insecureConnection -> R.string.security_insecure_connection
                else -> R.string.navigation_blocked
            }
        val protectionLabel =
            when {
                newPage -> R.string.security_dag_ready
                pageProtection -> R.string.security_dag_protection
                else -> R.string.status_closed
            }
        val message =
            buildString {
                append(pageLabel)
                append("\n\n")
                append("• ")
                append(getString(connectionLabel))
                append("\n• ")
                append(getString(protectionLabel))
                append("\n\n")
                append(
                    getString(
                        if (insecureConnection) {
                            R.string.security_insecure_detail
                        } else {
                            R.string.security_detail
                        },
                    ),
                )
            }
        AlertDialog.Builder(this)
            .setTitle(R.string.security_title)
            .setMessage(message)
            .setPositiveButton(R.string.close, null)
            .show()
    }

    private fun goHome(tab: BrowserTab) {
        if (!extensionReady || !tab.session.isOpen) return
        invalidateTabThumbnail(tab)
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
        switchToWithoutCapture(tab)
    }

    private fun switchToWithoutCapture(tab: BrowserTab) {
        if (!tabs.contains(tab) || tab === activeTab) return
        releaseNavigationFrame()
        dismissActiveChoicePrompt()
        activeTab?.let { setTabActivity(it, active = false) }
        if (activeTab != null) runCatching { geckoView.releaseSession() }
        activeTab = tab
        tab.lastActivatedSequence = nextTabActivationSequence++
        ensureSessionOpen(tab)
        geckoView.setSession(tab.session)
        setTabActivity(tab, active = true)
        restoreTabIfNeeded(tab)
        renderActiveTab()
        schedulePersistTabs()
        refreshTabSwitcher()
        handler.post(::hibernateLeastRecentSessions)
    }

    private fun hibernateLeastRecentSessions() {
        val sessions =
            tabs.map { tab ->
                DagOpenTabSession(
                    tabId = tab.id,
                    active = tab === activeTab,
                    open = tab.session.isOpen,
                    lastActivatedSequence = tab.lastActivatedSequence,
                )
            }
        val tabIds = DagTabSessionPolicy.sessionsToHibernate(sessions)
        tabs.filter { it.id in tabIds }.forEach(::hibernateTab)
    }

    private fun hibernateInactiveTabs() {
        tabs.filter { it !== activeTab && it.session.isOpen }.forEach(::hibernateTab)
    }

    private fun hibernateTab(tab: BrowserTab) {
        if (tab === activeTab || !tab.session.isOpen) return
        cancelBarrierTimeout(tab)
        tab.waitingForBarrier = false
        tab.keepCurrentPageVisibleDuringReload = false
        tab.canGoBack = false
        tab.needsRestore = tab.url != InitialBlankPage && restorableUrl(tab.url) != null
        tab.displayState = TabDisplayState.Ready
        tab.loadProgress = 0
        tab.previewDocumentToken = null
        tab.previewEligibilityToken = null
        tab.previewRestricted = true
        setTabActivity(tab, active = false)
        runCatching { tab.session.close() }
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
        tab.loadProgress = 0
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
        renderPageLoadProgress(tab)
        setNavigationControlsEnabled(extensionReady)
        updateAddressActionButton()
        updateTabButton()
        updatePrivateAppearance(tab)
        if (tab.url == InitialBlankPage) {
            addressInput.text.clear()
        } else if (!addressInput.hasFocus()) {
            addressInput.setText(tab.url)
        }
        when (tab.displayState) {
            TabDisplayState.Ready -> showReady()
            TabDisplayState.Loading -> {
                updateLoadingShimmer(enabled = false)
                geckoView.visibility = View.INVISIBLE
                safetyOverlay.visibility = View.GONE
            }
            TabDisplayState.Visible -> {
                updateLoadingShimmer(enabled = false)
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

    private fun renderPageLoadProgress(tab: BrowserTab) {
        val visible = tab.url != InitialBlankPage && tab.loadProgress in 1..100
        pageLoadProgress.visibility = if (visible) View.VISIBLE else View.GONE
        if (visible) pageLoadProgress.setProgress(tab.loadProgress, true)
    }

    private fun finishPageLoadProgress(tab: BrowserTab) {
        tab.loadProgress = 100
        if (tab === activeTab) renderPageLoadProgress(tab)
        handler.postDelayed(
            {
                if (tabs.contains(tab) && tab.loadProgress == 100) {
                    tab.loadProgress = 0
                    if (tab === activeTab) renderPageLoadProgress(tab)
                }
            },
            PageLoadProgressCompletionDelayMillis,
        )
    }

    private fun updateTabButton() {
        tabButton.text = tabs.size.coerceAtLeast(1).toString()
    }

    private fun updatePrivateAppearance(tab: BrowserTab) {
        val privateTab = tab.isPrivate
        val toolbarColor = getColor(if (privateTab) R.color.dag_private_navy else R.color.dag_navy)
        browserRoot.setBackgroundColor(toolbarColor)
        browserToolbar.setBackgroundColor(toolbarColor)
        addressBar.setBackgroundResource(
            if (privateTab) R.drawable.dag_private_address_background else R.drawable.dag_address_background,
        )
        addressInput.setTextColor(getColor(if (privateTab) R.color.dag_private_text else R.color.dag_text))
        tabButton.setBackgroundResource(
            if (privateTab) R.drawable.dag_private_tab_button_background else R.drawable.dag_tab_button_background,
        )
        addressInput.hint = getString(if (privateTab) R.string.private_tab else R.string.address_hint)
        tabButton.contentDescription = getString(if (privateTab) R.string.private_tab else R.string.tabs)
    }

    private fun showTabSwitcher() {
        if (tabs.isEmpty()) return
        tabThumbnailResidencyRequested = true
        captureActiveTabThumbnail()
        tabSwitcher.show(tabCards())
        tabs.filter { it.thumbnail == null }.forEach(::restoreTabThumbnail)
    }

    private fun hideTabSwitcher() {
        tabThumbnailResidencyRequested = false
        tabSwitcher.hide()
        releaseTabThumbnails()
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

    private fun captureActiveTabThumbnail(onCaptureReady: () -> Unit = {}) {
        captureActiveTabThumbnailAttempt(
            retriesRemaining = ThumbnailCaptureRetries,
            onCaptureReady = onCaptureReady,
        )
    }

    private fun captureActiveTabThumbnailAttempt(
        retriesRemaining: Int,
        onCaptureReady: () -> Unit,
    ) {
        val tab = activeTab ?: return onCaptureReady()
        if (!canCaptureThumbnail(tab)) {
            if (packageName.endsWith(".dev")) {
                Log.i(
                    TabPreviewLogTag,
                    "capture_skip tab=${tab.id} view=${geckoView.visibility} " +
                        "open=${tab.session.isOpen} state=${tab.displayState} " +
                        "document=${tab.previewDocumentToken != null} " +
                        "eligible=${tab.previewEligibilityToken == tab.previewDocumentToken} " +
                        "restricted=${tab.previewRestricted}",
                )
            }
            onCaptureReady()
            return
        }
        val request = DagTabPreviewRequest(tab.id, tab.previewRevision)
        var completionPending = true
        var captureExpired = false
        val completeOnce: () -> Unit = {
            if (completionPending) {
                completionPending = false
                onCaptureReady()
            }
        }
        val retryOrComplete: () -> Unit = retryOrComplete@{
            if (!completionPending) return@retryOrComplete
            completionPending = false
            if (
                retriesRemaining > 0 &&
                activeTab === tab &&
                canCaptureThumbnail(tab)
            ) {
                handler.postDelayed(
                    {
                        captureActiveTabThumbnailAttempt(
                            retriesRemaining = retriesRemaining - 1,
                            onCaptureReady = onCaptureReady,
                        )
                    },
                    ThumbnailCaptureRetryDelayMillis,
                )
            } else {
                onCaptureReady()
            }
        }
        val timeout =
            Runnable {
                captureExpired = true
                if (packageName.endsWith(".dev")) {
                    Log.i(TabPreviewLogTag, "capture_timeout tab=${tab.id} retries=$retriesRemaining")
                }
                retryOrComplete()
            }
        handler.postDelayed(timeout, ThumbnailCaptureTimeoutMillis)
        geckoView.capturePixels().accept(
            { bitmap ->
                handler.removeCallbacks(timeout)
                val acceptsBitmap = !captureExpired && activeTab === tab
                if (packageName.endsWith(".dev")) {
                    Log.i(
                        TabPreviewLogTag,
                        "capture_result tab=${tab.id} bitmap=${bitmap?.width}x${bitmap?.height} " +
                            "accepted=$acceptsBitmap",
                    )
                }
                completeOnce()
                if (bitmap == null || bitmap.width <= 0 || bitmap.height <= 0) return@accept
                if (
                    !acceptsBitmap ||
                    isFinishing ||
                    isDestroyed ||
                    thumbnailExecutor.isShutdown
                ) {
                    bitmap.recycle()
                    return@accept
                }
                val bitmapOwnedByNavigationFrame = updateNavigationFrame(tab, bitmap)
                try {
                    thumbnailExecutor.execute {
                        val scaled = scaleThumbnail(bitmap)
                        if (!bitmapOwnedByNavigationFrame) bitmap.recycle()
                        val encoded =
                            if (tab.isPrivate) {
                                null
                            } else {
                                tabThumbnailStore.encode(scaled)
                            }
                        handler.post {
                            if (
                                isFinishing ||
                                isDestroyed ||
                                !tabs.contains(tab) ||
                                !DagTabPreviewPolicy.acceptsResult(
                                    request = request,
                                    currentTabId = tab.id,
                                    currentRevision = tab.previewRevision,
                                    pageVisible = tab.displayState == TabDisplayState.Visible,
                                    restricted = tab.previewRestricted,
                                )
                            ) {
                                scaled.recycle()
                            } else {
                                tab.thumbnailStale = false
                                if (encoded != null) persistTabThumbnail(tab.previewKey, encoded)
                                if (tabThumbnailResidencyRequested) {
                                    tab.thumbnail = scaled
                                    refreshTabSwitcher()
                                } else {
                                    scaled.recycle()
                                }
                            }
                        }
                    }
                } catch (_: RejectedExecutionException) {
                    if (!bitmapOwnedByNavigationFrame) bitmap.recycle()
                }
            },
            {
                handler.removeCallbacks(timeout)
                if (packageName.endsWith(".dev")) {
                    Log.i(TabPreviewLogTag, "capture_error tab=${tab.id} retries=$retriesRemaining")
                }
                retryOrComplete()
            },
        )
    }

    private fun canCaptureThumbnail(tab: BrowserTab): Boolean =
        DagTabPreviewPolicy.canCapture(
            viewVisible = geckoView.visibility == View.VISIBLE,
            sessionOpen = tab.session.isOpen,
            pageVisible = tab.displayState == TabDisplayState.Visible,
            eligibilityConfirmed =
                tab.previewDocumentToken != null &&
                    tab.previewEligibilityToken == tab.previewDocumentToken,
            restricted = tab.previewRestricted,
        )

    private fun applyPreviewEligibility(
        tab: BrowserTab,
        payload: JSONObject,
    ) {
        val token =
            payload.optString("documentToken")
                .takeIf(PreviewDocumentTokenPattern::matches)
                ?: return
        if (token != tab.previewDocumentToken) {
            if (packageName.endsWith(".dev")) {
                Log.i(TabPreviewLogTag, "eligibility_mismatch tab=${tab.id}")
            }
            return
        }
        tab.previewEligibilityToken = token
        tab.previewRestricted = payload.optBoolean("restricted", true)
        if (packageName.endsWith(".dev")) {
            Log.i(
                TabPreviewLogTag,
                "eligibility tab=${tab.id} restricted=${tab.previewRestricted}",
            )
        }
        if (tab.previewRestricted) {
            invalidateTabThumbnail(tab)
        } else if (tab === activeTab) {
            captureActiveTabThumbnail()
        }
    }

    private fun invalidateTabThumbnail(tab: BrowserTab) {
        tab.previewRevision += 1
        tab.thumbnail = null
        tab.thumbnailStale = false
        deletePersistedTabThumbnail(tab.previewKey)
        refreshTabSwitcher()
    }

    private fun markTabThumbnailStale(tab: BrowserTab) {
        tab.previewRevision += 1
        tab.thumbnailStale = true
        deletePersistedTabThumbnail(tab.previewKey)
        refreshTabSwitcher()
    }

    private fun persistTabThumbnail(
        key: String,
        encoded: ByteArray,
    ) {
        if (thumbnailExecutor.isShutdown) return
        runCatching {
            thumbnailExecutor.execute { tabThumbnailStore.save(key, encoded) }
        }
    }

    private fun restoreTabThumbnail(tab: BrowserTab) {
        if (tab.isPrivate || thumbnailExecutor.isShutdown || tab.thumbnail != null) return
        val revision = tab.previewRevision
        val key = tab.previewKey
        runCatching {
            thumbnailExecutor.execute {
                val restored = tabThumbnailStore.load(key) ?: return@execute
                handler.post {
                    if (
                        isFinishing ||
                        isDestroyed ||
                        !tabs.contains(tab) ||
                        tab.previewRevision != revision ||
                        tab.thumbnailStale ||
                        tab.thumbnail != null ||
                        !tabThumbnailResidencyRequested
                    ) {
                        restored.recycle()
                    } else {
                        tab.thumbnail = restored
                        refreshTabSwitcher()
                    }
                }
            }
        }
    }

    private fun deletePersistedTabThumbnail(key: String) {
        if (thumbnailExecutor.isShutdown) return
        runCatching {
            thumbnailExecutor.execute { tabThumbnailStore.delete(key) }
        }
    }

    private fun scaleThumbnail(source: Bitmap): Bitmap {
        val scale =
            minOf(
                1f,
                DagTabCapacityPolicy.ThumbnailWidth.toFloat() / source.width,
                DagTabCapacityPolicy.ThumbnailHeight.toFloat() / source.height,
            )
        if (scale >= 1f) {
            return source.copy(Bitmap.Config.ARGB_8888, false)
        }
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
        val persistentTabs = tabs.filterNot(BrowserTab::isPrivate)
        if (persistentTabs.isEmpty()) {
            tabPersistence.clear()
            return
        }
        val activeIndex = persistentTabs.indexOf(activeTab).coerceAtLeast(0)
        tabPersistence.save(
            DagPersistedTabs(
                tabs =
                    persistentTabs.map {
                        DagPersistedTab(
                            url = restorableUrl(it.url) ?: InitialBlankPage,
                            title = it.title,
                            previewKey = it.previewKey,
                        )
                    },
                activeIndex = activeIndex,
            ),
        )
        val retainedPreviewKeys = persistentTabs.map(BrowserTab::previewKey).toSet()
        if (!thumbnailExecutor.isShutdown) {
            runCatching {
                thumbnailExecutor.execute { tabThumbnailStore.retain(retainedPreviewKeys) }
            }
        }
    }

    private fun showBrowserMenu() {
        val popup = browserMenu ?: createBrowserMenu().also { browserMenu = it }
        popup.menu.findItem(R.id.menu_default_browser)?.isVisible = !isDefaultBrowser()
        popup.show()
    }

    private fun createBrowserMenu(): PopupMenu =
        PopupMenu(this, menuButton, Gravity.END).apply {
            inflate(R.menu.dag_browser_menu)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.menu_reload -> {
                        reloadActivePage()
                        true
                    }
                    R.id.menu_history -> {
                        showHistory()
                        true
                    }
                    R.id.menu_favorites -> {
                        showFavorites()
                        true
                    }
                    R.id.menu_add_favorite -> {
                        toggleActiveFavorite()
                        true
                    }
                    R.id.menu_new_private_tab -> {
                        createTab(switchToTab = true, privateTab = true)
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
                    R.id.menu_about -> {
                        showAboutDag()
                        true
                    }
                    else -> false
                }
            }
        }

    private fun showAboutDag() {
        val packageInfo =
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packageManager.getPackageInfo(
                        packageName,
                        PackageManager.PackageInfoFlags.of(0),
                    )
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.getPackageInfo(packageName, 0)
                }
            }.getOrNull()
        val dagVersionDetail =
            if (packageInfo?.versionName.isNullOrBlank()) {
                getString(R.string.about_version_unavailable)
            } else {
                getString(
                    R.string.about_version_detail,
                    packageInfo.versionName,
                    packageInfo.longVersionCode,
                )
            }
        val detail =
            getString(
                R.string.about_visual_model_detail,
                dagVersionDetail,
                DagVisualModelInfo.PublicName,
                DagVisualModelInfo.FunctionalVersion,
                DagVisualModelInfo.ModelAssetPath,
                DagVisualModelInfo.ShortSha256,
                DagVisualModelInfo.Runtime,
                DagVisualModelInfo.PolicyVersion,
            )
        AlertDialog.Builder(this)
            .setTitle(R.string.about_dag)
            .setMessage(detail)
            .setPositiveButton(R.string.close, null)
            .show()
    }

    private fun recordHistory(tab: BrowserTab) {
        if (tab.isPrivate || tab.url == InitialBlankPage || restorableUrl(tab.url) == null) return
        historyPersistence.record(
            entry =
                DagHistoryEntry(
                    url = tab.url,
                    title = tab.title,
                    visitedAtMillis = System.currentTimeMillis(),
                ),
            isAllowedUrl = ::isRestorableUrl,
        )
    }

    private fun toggleActiveFavorite() {
        val tab = activeTab ?: return
        val safeUrl = restorableUrl(tab.url) ?: return
        val added = favoritesPersistence.toggle(DagFavorite(safeUrl, tab.title), ::isRestorableUrl)
        Toast.makeText(
            this,
            if (added) R.string.favorite_added else R.string.favorite_removed,
            Toast.LENGTH_SHORT,
        ).show()
    }

    private fun showFavorites() {
        val entries = favoritesPersistence.load(::isRestorableUrl)
        val items =
            entries.map { entry ->
                DagPageListItem(
                    title = entry.title.takeIf(String::isNotBlank) ?: pageHost(entry.url),
                    host = pageHost(entry.url),
                    detail = getString(R.string.favorites),
                    url = entry.url,
                )
            }
        showPageListScreen(
            titleRes = R.string.favorites,
            subtitleRes = R.string.favorites_subtitle,
            emptyRes = R.string.favorites_empty,
            clearRes = R.string.clear_favorites,
            items = items,
            onOpen = { item -> openFavorite(DagFavorite(item.url, item.title)) },
            onClear = { favoritesPersistence.clear() },
        )
    }

    private fun pageHost(url: String): String =
        runCatching { Uri.parse(url).host?.removePrefix("www.").orEmpty() }.getOrDefault("")

    private fun openFavorite(entry: DagFavorite) {
        val safeUrl = restorableUrl(entry.url) ?: return
        val tab = activeTab
        if (tab == null) {
            createTab(switchToTab = true, initialUrl = safeUrl)
            return
        }
        beginProtectedLoad(tab, startNewPerformanceNavigation = true)
        tab.session.loadUri(safeUrl)
    }

    private fun showHistory() {
        val entries = historyPersistence.load(::isRestorableUrl)
        val items =
            entries.map { entry ->
                DagPageListItem(
                    title = entry.title.takeIf(String::isNotBlank) ?: pageHost(entry.url),
                    host = pageHost(entry.url),
                    detail =
                        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                            .format(Date(entry.visitedAtMillis)),
                    url = entry.url,
                    thumbnail = tabs.firstOrNull { it.url == entry.url }?.thumbnail,
                )
            }
        showPageListScreen(
            titleRes = R.string.history,
            subtitleRes = R.string.history_subtitle,
            emptyRes = R.string.history_empty,
            clearRes = R.string.clear_history,
            items = items,
            onOpen = { item ->
                val entry = entries.firstOrNull { it.url == item.url } ?: return@showPageListScreen
                openHistoryEntry(entry)
            },
            onClear = {
                historyPersistence.clear()
                Toast.makeText(this, R.string.history_cleared, Toast.LENGTH_SHORT).show()
            },
        )
    }

    private fun showPageListScreen(
        titleRes: Int,
        subtitleRes: Int,
        emptyRes: Int,
        clearRes: Int,
        items: List<DagPageListItem>,
        onOpen: (DagPageListItem) -> Unit,
        onClear: () -> Unit,
    ) {
        pageListScreen?.dismiss()
        val dialog = Dialog(this)
        pageListScreen = dialog
        dialog.setContentView(R.layout.view_dag_page_list)
        val list = dialog.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.page_list_recycler)
        val emptyState = dialog.findViewById<TextView>(R.id.page_list_empty)
        val clearButton = dialog.findViewById<Button>(R.id.page_list_clear)
        val adapter =
            DagPageListAdapter(
                onOpen = { item ->
                    dialog.dismiss()
                    onOpen(item)
                },
                onDelete = null,
            )
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter
        dialog.findViewById<TextView>(R.id.page_list_title).setText(titleRes)
        dialog.findViewById<TextView>(R.id.page_list_subtitle).setText(subtitleRes)
        dialog.findViewById<ImageButton>(R.id.page_list_close).setOnClickListener { dialog.dismiss() }
        clearButton.setText(clearRes)
        clearButton.isEnabled = items.isNotEmpty()
        clearButton.setOnClickListener {
            onClear()
            dialog.dismiss()
        }
        emptyState.setText(emptyRes)
        dialog.setOnDismissListener {
            if (pageListScreen === dialog) pageListScreen = null
        }
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            android.view.WindowManager.LayoutParams.MATCH_PARENT,
            android.view.WindowManager.LayoutParams.MATCH_PARENT,
        )
        adapter.submit(items)
        list.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        emptyState.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun openHistoryEntry(entry: DagHistoryEntry) {
        val safeUrl = restorableUrl(entry.url) ?: return
        val tab = activeTab
        if (tab == null) {
            createTab(switchToTab = true, initialUrl = safeUrl)
            return
        }
        beginProtectedLoad(tab, startNewPerformanceNavigation = true)
        tab.session.loadUri(safeUrl)
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
        if (BuildConfig.GLOSHIA_LAB_FIXTURE) {
            Log.i("DagLabHarness", "default_browser_prompt_skipped")
            return
        }
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

    private fun confirmCloseAllTabs() {
        if (tabs.size <= 1) return
        AlertDialog.Builder(this)
            .setTitle(R.string.close_all_tabs_title)
            .setMessage(resources.getQuantityString(R.plurals.close_all_tabs_detail, tabs.size, tabs.size))
            .setPositiveButton(R.string.close_all_tabs) { _, _ ->
                hideTabSwitcher()
                resetTabs()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
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
        disposeTab(tab, deletePersistedPreview = true)
        updateTabButton()
        when {
            !wasActive -> Unit
            tabs.isEmpty() -> createTab(switchToTab = true)
            else -> switchTo(tabs[oldIndex.coerceAtMost(tabs.lastIndex)])
        }
        schedulePersistTabs()
        refreshTabSwitcher()
    }

    private fun disposeTab(
        tab: BrowserTab,
        deletePersistedPreview: Boolean,
    ) {
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
        tab.thumbnail = null
        if (deletePersistedPreview) deletePersistedTabThumbnail(tab.previewKey)
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
        historyPersistence.clear()
        if (!thumbnailExecutor.isShutdown) {
            runCatching { thumbnailExecutor.execute(tabThumbnailStore::clear) }
        }
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
        tabs.forEach { disposeTab(it, deletePersistedPreview = true) }
        tabs.clear()
        updateTabButton()
        createTab(switchToTab = true)
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        handleBackNavigation()
    }

    private fun handleBackNavigation() {
        val tab = activeTab
        val action =
            DagBackNavigationPolicy.decide(
                addressEditing = addressInput.hasFocus(),
                tabSwitcherOpen = tabSwitcher.isOpen(),
                hasActiveTab = tab != null,
                canGoBackInPage = tab?.canGoBack == true,
                isHome =
                    DagBackNavigationPolicy.isTerminalHome(
                        blankDocument = tab?.url == InitialBlankPage,
                        protectedPageVisible = tab?.displayState == TabDisplayState.Visible,
                    ),
            )
        if (packageName.endsWith(".dev")) {
            Log.i(
                BackNavigationLogTag,
                "action=$action tab=${tab?.id} blank=${tab?.url == InitialBlankPage} " +
                    "state=${tab?.displayState} view=${geckoView.visibility} " +
                    "canGoBack=${tab?.canGoBack} tabs=${tabs.size}",
            )
        }
        when (action) {
            DagBackAction.CloseKeyboard -> {
                addressInput.clearFocus()
                getSystemService(InputMethodManager::class.java)
                    .hideSoftInputFromWindow(addressInput.windowToken, 0)
            }
            DagBackAction.CloseTabSwitcher -> hideTabSwitcher()
            DagBackAction.GoBackInPage -> tab?.session?.goBack()
            DagBackAction.GoHome -> tab?.let(::goHome)
            DagBackAction.ExitBrowser -> finish()
        }
    }

    override fun onStart() {
        super.onStart()
        if (tabSwitcher.isOpen()) {
            tabThumbnailResidencyRequested = true
            tabs.filter { it.thumbnail == null }.forEach(::restoreTabThumbnail)
        }
        activeTab?.let { setTabActivity(it, active = true) }
    }

    override fun onStop() {
        dismissActiveChoicePrompt()
        persistTabsNow()
        tabThumbnailResidencyRequested = false
        releaseTabThumbnails()
        activeTab?.let { setTabActivity(it, active = false) }
        hibernateInactiveTabs()
        super.onStop()
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            releaseNavigationFrame()
            releaseTabThumbnails()
            hibernateInactiveTabs()
        }
    }

    private fun releaseTabThumbnails() {
        tabs.forEach { tab ->
            tab.previewRevision += 1
            tab.thumbnail = null
        }
        refreshTabSwitcher()
    }

    override fun onDestroy() {
        mediaAnalysisAccepting.set(false)
        mediaAnalysisLifecycleGeneration.incrementAndGet()
        activeMediaDecisionPort = null
        mediaAnalysisQueue.discardMatching { true }
        mediaDocumentRegistry.clear()
        dismissActiveChoicePrompt()
        updateLoadingShimmer(enabled = false)
        persistTabsNow()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            backInvokedCallback?.let(onBackInvokedDispatcher::unregisterOnBackInvokedCallback)
            backInvokedCallback = null
        }
        handler.removeCallbacksAndMessages(null)
        releaseNavigationFrame()
        protectionExtension?.let { extension ->
            extension.setMessageDelegate(null, NativeApp)
            tabs.forEach { tab ->
                runCatching {
                    tab.session.webExtensionController.setMessageDelegate(extension, null, NativeApp)
                }
            }
        }
        protectionExtension = null
        analyzerInitializationExecutor.shutdownNow()
        thumbnailExecutor.shutdownNow()
        mediaAnalysisExecutor.shutdownNow()
        if (this::imageAnalyzer.isInitialized) {
            (imageAnalyzer as? AutoCloseable)?.close()
        }
        tabs.forEach { tab ->
            cancelBarrierTimeout(tab)
            setTabActivity(tab, active = false)
        }
        if (activeTab != null) {
            runCatching { geckoView.releaseSession() }
        }
        tabs.forEach { disposeTab(it, deletePersistedPreview = false) }
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

    private data class ActiveChoicePrompt(
        val dialog: AlertDialog,
        val dismissPrompt: () -> Unit,
    )

    private class BrowserTab(
        val id: Long,
        val session: GeckoSession,
        var url: String = InitialBlankPage,
        var title: String = "",
        var canGoBack: Boolean = false,
        var waitingForBarrier: Boolean = false,
        var keepCurrentPageVisibleDuringReload: Boolean = false,
        var barrierReadyForNavigation: Boolean = false,
        var protectedContentReadyForNavigation: Boolean = false,
        var documentSanitizedForNavigation: Boolean = false,
        var displayState: TabDisplayState = TabDisplayState.Ready,
        var barrierTimeout: Runnable? = null,
        var needsRestore: Boolean = false,
        var thumbnail: Bitmap? = null,
        var previewRevision: Long = 0,
        var previewDocumentToken: String? = null,
        var previewEligibilityToken: String? = null,
        var previewRestricted: Boolean = true,
        var recovering: Boolean = false,
        var isPrivate: Boolean = false,
        var thumbnailStale: Boolean = false,
        var lastActivatedSequence: Long = 0,
        var loadProgress: Int = 0,
        val previewKey: String,
    )

    private companion object {
        const val ExtensionLocation = "resource://android/assets/dag-protection/"
        const val ExtensionId = "dag-protection@glosh.local"
        const val NativeApp = "glosh.dag.protection"
        const val BarrierReadyMessage = "barrier-ready"
        const val PreviewEligibilityMessage = "tab-preview-eligibility"
        const val MediaBytesMessage = "media-bytes"
        const val MediaDecisionMessage = "media-decision"
        const val MediaDocumentCurrentMessage = "media-document-current"
        const val MediaDocumentRetiredMessage = "media-document-retired"
        const val ViewportImagesReadyMessage = "viewport-images-ready"
        const val DocumentSanitizedReadyMessage = "document-sanitized-ready"
        const val ProtectionProtocolVersion = 2
        const val CacheMaintenancePreferences = "dag-cache-maintenance"
        const val CacheMaintenanceRevisionKey = "intercepted-media-cache-revision"
        const val LegacyCacheMaintenanceVersionKey = "last-cache-clear-version"
        const val InterceptedMediaCacheRevision = 5
        const val MinimumPageLoadProgress = 5
        const val PageLoadProgressCompletionDelayMillis = 160L
        const val MaxMediaCandidateIdLength = 80
        const val MediaAnalysisThreads = 2
        const val MediaAnalysisQueueCapacity = 8
        const val MediaAnalysisLifetimeMillis = 2_250L
        const val MediaAnalysisAllowedFutureSkewMillis = 250L
        const val MediaTransportLogTag = "DagMediaTransport"
        const val MaxPipelineMetricMillis = 60_000L
        const val PerformanceLogTag = "DagPerformance"
        const val BackNavigationLogTag = "DagBackNavigation"
        const val TabPreviewLogTag = "DagTabPreview"
        const val BarrierTimeoutMillis = 12_000L
        const val InitialBlankPage = "about:blank"
        const val MaxTabLabelLength = 36
        const val PersistTabsDelayMillis = 250L
        const val ThumbnailCaptureTimeoutMillis = 1_200L
        const val ThumbnailCaptureRetryDelayMillis = 120L
        const val ThumbnailCaptureRetries = 1
        val PreviewDocumentTokenPattern = Regex("^document_[a-f0-9]{1,16}$")
        const val EnabledControlAlpha = 1f
        const val DisabledControlAlpha = 0.45f
        const val EnabledChoiceAlpha = 1f
        const val DisabledChoiceAlpha = 0.38f
        const val DefaultBrowserRoleRequestCode = 4_201
        const val BrowserSetupPreferences = "dag-browser-setup"
        const val DefaultBrowserPromptShownKey = "default-browser-prompt-shown"
        const val LoadingShimmerDurationMillis = 850L
    }
}
