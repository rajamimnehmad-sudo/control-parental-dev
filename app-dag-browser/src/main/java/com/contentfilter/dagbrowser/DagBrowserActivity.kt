package com.contentfilter.dagbrowser

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.app.role.RoleManager
import android.content.ComponentCallbacks2
import android.content.Intent
import android.content.pm.PackageManager
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
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ListView
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.core.content.FileProvider
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import org.json.JSONObject
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.StorageController
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebResponse
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class DagBrowserActivity : Activity() {
    private lateinit var geckoView: GeckoView
    private lateinit var addressInput: EditText
    private lateinit var newPageButton: ImageButton
    private lateinit var securityButton: ImageButton
    private lateinit var goButton: ImageButton
    private lateinit var tabButton: TextView
    private lateinit var menuButton: ImageButton
    private lateinit var safetyOverlay: View
    private lateinit var safetyProgress: ProgressBar
    private lateinit var safetyTitle: TextView
    private lateinit var safetyDetail: TextView
    private lateinit var tabSwitcher: DagTabSwitcherView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var tabPersistence: DagTabPersistence
    private lateinit var historyPersistence: DagHistoryPersistence
    private lateinit var imageAnalyzer: DagImageAnalyzer
    private lateinit var runtime: GeckoRuntime

    private val handler = Handler(Looper.getMainLooper())
    private val performanceTracker = DagPerformanceTracker(SystemClock::elapsedRealtime)
    private val tabs = mutableListOf<BrowserTab>()
    private val thumbnailExecutor = Executors.newSingleThreadExecutor()
    private val downloadExecutor = Executors.newSingleThreadExecutor()
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
    private var activeDownload: ActiveDownload? = null
    private var downloadDialog: AlertDialog? = null
    private var downloadsScreen: Dialog? = null
    private var activeChoicePrompt: ActiveChoicePrompt? = null
    private var backInvokedCallback: OnBackInvokedCallback? = null
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
                                        senderTab.previewDocumentToken =
                                            payload.optString("documentToken")
                                                .takeIf(PreviewDocumentTokenPattern::matches)
                                        if (packageName.endsWith(".dev")) {
                                            Log.i(
                                                TabPreviewLogTag,
                                                "barrier tab=${senderTab.id} " +
                                                    "token=${senderTab.previewDocumentToken != null}",
                                            )
                                        }
                                        completeProtectedLoad(senderTab)
                                    } else if (
                                        payload.optString("type") == PreviewEligibilityMessage &&
                                        payload.optInt("version") == ProtectionProtocolVersion &&
                                        sender.isTopLevel
                                    ) {
                                        applyPreviewEligibility(senderTab, payload)
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
                                                        "matched=${payload.optInt("matchedCount", -1)}",
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
        historyPersistence = DagHistoryPersistence(applicationContext)
        imageAnalyzer = DagOnDeviceImageAnalyzer.create(applicationContext)
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
        geckoView = findViewById(R.id.gecko_view)
        addressInput = findViewById(R.id.address_input)
        newPageButton = findViewById(R.id.new_page_button)
        securityButton = findViewById(R.id.security_button)
        goButton = findViewById(R.id.go_button)
        tabButton = findViewById(R.id.tab_button)
        menuButton = findViewById(R.id.menu_button)
        safetyOverlay = findViewById(R.id.safety_overlay)
        safetyProgress = findViewById(R.id.safety_progress)
        safetyTitle = findViewById(R.id.safety_title)
        safetyDetail = findViewById(R.id.safety_detail)
        tabSwitcher = findViewById(R.id.tab_switcher)
        swipeRefresh = findViewById(R.id.swipe_refresh)
    }

    private fun configureControls() {
        setNavigationControlsEnabled(false)
        goButton.setOnClickListener { navigateFromInput() }
        newPageButton.setOnClickListener { createTab(switchToTab = true) }
        securityButton.setOnClickListener { showSecurityDetails() }
        swipeRefresh.setOnRefreshListener { reloadFromPullGesture() }
        addressInput.setOnEditorActionListener { _, actionId, event ->
            val submitted =
                actionId == EditorInfo.IME_ACTION_GO ||
                    (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP)
            if (submitted) navigateFromInput()
            submitted
        }
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

                override fun onCloseAllTabs() {
                    confirmCloseAllTabs()
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

                override fun onCrash(session: GeckoSession) {
                    recoverClosedSession(tab)
                }

                override fun onKill(session: GeckoSession) {
                    recoverClosedSession(tab)
                }

                override fun onExternalResponse(
                    session: GeckoSession,
                    response: WebResponse,
                ) {
                    handleDownloadResponse(tab, response)
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
                    val downloadGesture =
                        DagDownloadPolicy.recordGesture(
                            requestUrl = request.uri,
                            triggerUrl = request.triggerUri,
                            currentPageUrl = tab.url,
                            tabRevision = tab.navigationRevision,
                            pageVisible = tab.displayState == TabDisplayState.Visible,
                            hasUserGesture = request.hasUserGesture,
                            opensNewWindow =
                                request.target == GeckoSession.NavigationDelegate.TARGET_WINDOW_NEW,
                            nowMillis = SystemClock.elapsedRealtime(),
                        )
                    if (request.hasUserGesture) {
                        tab.downloadGesture = downloadGesture
                    }
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
                    swipeRefresh.isRefreshing = false
                    tab.downloadGesture = null
                    tab.navigationRevision += 1
                    if (tab.displayState != TabDisplayState.Loading) {
                        invalidateTabThumbnail(tab)
                    }
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
                    swipeRefresh.isRefreshing = false
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
        val rows = flattenChoiceRows(prompt.choices)
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
            object : ArrayAdapter<ChoicePromptRow>(this, rowLayout, rows) {
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

    private fun flattenChoiceRows(
        choices: Array<GeckoSession.PromptDelegate.ChoicePrompt.Choice>,
        groupLabels: List<String> = emptyList(),
    ): List<ChoicePromptRow> =
        buildList {
            choices.forEach { choice ->
                if (choice.separator) return@forEach
                val label = choice.label.trim().take(MaxChoiceLabelLength)
                val children = choice.items
                if (children != null) {
                    val nextGroups =
                        if (label.isBlank()) groupLabels else groupLabels + label
                    addAll(flattenChoiceRows(children, nextGroups))
                } else {
                    val visibleLabel =
                        (groupLabels + label.takeIf(String::isNotBlank).orEmpty())
                            .filter(String::isNotBlank)
                            .joinToString(ChoiceGroupSeparator)
                            .ifBlank { getString(R.string.unnamed_option) }
                            .take(MaxChoiceLabelLength)
                    add(
                        ChoicePromptRow(
                            choice = choice,
                            label = visibleLabel,
                            enabled = !choice.disabled,
                            selected = choice.selected,
                        ),
                    )
                }
            }
        }

    private fun dismissActiveChoicePrompt() {
        val active = activeChoicePrompt ?: return
        activeChoicePrompt = null
        active.dismissPrompt()
    }

    private fun handleDownloadResponse(
        tab: BrowserTab,
        response: WebResponse,
    ) {
        val input = response.body
        if (activeDownload != null || input == null || tab !== activeTab) {
            input?.closeQuietly()
            if (activeDownload != null) {
                Toast.makeText(this, R.string.download_one_at_a_time, Toast.LENGTH_LONG).show()
            }
            return
        }
        val headers = response.headers
        val decision =
            DagDownloadPolicy.decide(
                gesture = tab.downloadGesture,
                candidate =
                    DagDownloadCandidate(
                        responseUrl = response.uri,
                        currentPageUrl = tab.url,
                        currentTabRevision = tab.navigationRevision,
                        secure = response.isSecure,
                        redirected = response.redirected,
                        statusCode = response.statusCode,
                        mimeType = DagDownloadPolicy.header(headers, "Content-Type"),
                        declaredBytes = DagDownloadPolicy.declaredLength(headers),
                        suggestedFileName =
                            DagDownloadPolicy.suggestedFileName(headers, response.uri),
                        nowMillis = SystemClock.elapsedRealtime(),
                    ),
            )
        tab.downloadGesture = null
        when (decision) {
            is DagDownloadDecision.Block -> {
                input.closeQuietly()
                Toast.makeText(this, R.string.download_blocked, Toast.LENGTH_LONG).show()
            }
            is DagDownloadDecision.Allow -> confirmDownload(tab, decision.download, input)
        }
    }

    private fun confirmDownload(
        tab: BrowserTab,
        download: DagAllowedDownload,
        input: InputStream,
    ) {
        if (activeDownload != null) {
            input.closeQuietly()
            return
        }
        val task = ActiveDownload(tab = tab, metadata = download, input = input)
        activeDownload = task
        val size = readableByteCount(download.declaredBytes)
        downloadDialog =
            AlertDialog.Builder(this)
                .setTitle(R.string.download_confirm_title)
                .setMessage(
                    getString(
                        R.string.download_confirm_detail,
                        download.fileName,
                        download.host,
                        DagDownloadPolicy.PdfMimeType,
                        size,
                    ),
                )
                .setPositiveButton(R.string.download) { _, _ -> startDownload(task) }
                .setNegativeButton(R.string.cancel) { _, _ -> cancelDownload(task) }
                .setOnCancelListener { cancelDownload(task) }
                .create()
                .also(AlertDialog::show)
    }

    private fun startDownload(task: ActiveDownload) {
        if (activeDownload !== task || task.cancelled) return
        val progress =
            ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                progress = 0
                setPadding(48, 0, 48, 0)
            }
        downloadDialog =
            AlertDialog.Builder(this)
                .setTitle(R.string.download_in_progress)
                .setMessage(getString(R.string.download_progress, 0))
                .setView(progress)
                .setNegativeButton(R.string.cancel, null)
                .setOnCancelListener { cancelDownload(task) }
                .create()
                .also { dialog ->
                    dialog.setOnShowListener {
                        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                            cancelDownload(task)
                        }
                    }
                    dialog.show()
                }
        downloadExecutor.execute {
            val result = runCatching { saveDownload(task, progress) }
            handler.post {
                if (activeDownload !== task) return@post
                activeDownload = null
                downloadDialog?.dismiss()
                downloadDialog = null
                result.fold(
                    onSuccess = { file ->
                        showDownloadReady(file, task.metadata)
                    },
                    onFailure = {
                        showDownloadFailure(task)
                    },
                )
            }
        }
    }

    private fun saveDownload(
        task: ActiveDownload,
        progress: ProgressBar,
    ): File {
        val directory = File(filesDir, DownloadsDirectory).apply { mkdirs() }
        check(directory.isDirectory)
        val partial = File.createTempFile("dag-", ".part", directory)
        task.partialFile = partial
        try {
            var total = 0L
            task.input.use { input ->
                partial.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DownloadBufferBytes)
                    while (true) {
                        check(!task.cancelled) { "cancelled" }
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        check(total <= task.metadata.declaredBytes && total <= DagDownloadPolicy.MaxBytes) {
                            "size_mismatch"
                        }
                        output.write(buffer, 0, read)
                        val percent = ((total * 100) / task.metadata.declaredBytes).toInt()
                        handler.post {
                            if (activeDownload === task && !task.cancelled) {
                                progress.progress = percent
                                downloadDialog?.setMessage(getString(R.string.download_progress, percent))
                            }
                        }
                    }
                }
            }
            check(total == task.metadata.declaredBytes) { "size_mismatch" }
            check(isValidPdf(partial)) { "invalid_pdf" }
            val destination = uniqueDownloadFile(directory, task.metadata.fileName)
            Files.move(
                partial.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
            )
            task.partialFile = null
            return destination
        } catch (failure: Throwable) {
            partial.delete()
            task.partialFile = null
            throw failure
        }
    }

    private fun cancelDownload(task: ActiveDownload) {
        if (activeDownload !== task) return
        task.cancelled = true
        task.input.closeQuietly()
        task.partialFile?.delete()
        activeDownload = null
        downloadDialog?.dismiss()
        downloadDialog = null
        Toast.makeText(this, R.string.download_cancelled, Toast.LENGTH_SHORT).show()
    }

    private fun showDownloadReady(
        file: File,
        metadata: DagAllowedDownload,
    ) {
        AlertDialog.Builder(this)
            .setTitle(R.string.download_complete)
            .setMessage(metadata.fileName)
            .setPositiveButton(R.string.open) { _, _ -> openDownloadedPdf(file) }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun showDownloadFailure(task: ActiveDownload) {
        if (task.cancelled || isFinishing || isDestroyed) return
        AlertDialog.Builder(this)
            .setTitle(R.string.download_failed)
            .setMessage(R.string.download_failed_detail)
            .setPositiveButton(R.string.retry) { _, _ -> retryDownload(task) }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun retryDownload(task: ActiveDownload) {
        val tab = task.tab
        if (tab !== activeTab || !tabs.contains(tab) || tab.displayState != TabDisplayState.Visible) return
        tab.downloadGesture =
            DagDownloadGesture(
                targetUrl = task.metadata.responseUrl,
                pageUrl = tab.url,
                tabRevision = tab.navigationRevision,
                createdAtMillis = SystemClock.elapsedRealtime(),
            )
        tab.session.loadUri(task.metadata.responseUrl)
    }

    private fun openDownloadedPdf(file: File) {
        val uri =
            FileProvider.getUriForFile(
                this,
                "$packageName.downloads.fileprovider",
                file,
            )
        val intent =
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, DagDownloadPolicy.PdfMimeType)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { startActivity(Intent.createChooser(intent, getString(R.string.open_download))) }
            .onFailure {
                Toast.makeText(this, R.string.no_pdf_viewer, Toast.LENGTH_LONG).show()
            }
    }

    private fun isValidPdf(file: File): Boolean {
        RandomAccessFile(file, "r").use { input ->
            val header = ByteArray(minOf(PdfHeaderBytes, input.length().toInt()))
            input.readFully(header)
            val tailSize = minOf(PdfTailBytes.toLong(), input.length()).toInt()
            val tail = ByteArray(tailSize)
            input.seek(input.length() - tailSize)
            input.readFully(tail)
            return DagDownloadPolicy.looksLikePdf(header, tail)
        }
    }

    private fun uniqueDownloadFile(
        directory: File,
        requestedName: String,
    ): File {
        val direct = File(directory, requestedName)
        if (!direct.exists()) return direct
        val stem = requestedName.removeSuffix(".pdf")
        for (suffix in 2..MaxDuplicateFileSuffix) {
            val candidate = File(directory, "$stem ($suffix).pdf")
            if (!candidate.exists()) return candidate
        }
        return File(directory, "$stem-${System.currentTimeMillis()}.pdf")
    }

    private fun readableByteCount(bytes: Long): String =
        String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))

    private fun InputStream.closeQuietly() {
        runCatching { close() }
    }

    private fun createTab(
        switchToTab: Boolean,
        initialUrl: String? = null,
        restoredTab: DagPersistedTab? = null,
    ): BrowserTab? {
        if (!extensionReady || !DagTabCapacityPolicy.canCreate(tabs.size)) {
            if (!DagTabCapacityPolicy.canCreate(tabs.size)) {
                showTabSwitcher()
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
        if (tab.displayState != TabDisplayState.Loading) {
            invalidateTabThumbnail(tab)
        }
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
        recordHistory(tab)
        if (tab === activeTab) {
            revealProtectedPage()
            if (tabSwitcher.isOpen()) captureActiveTabThumbnail()
        }
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
        invalidateTabThumbnail(tab)
        tab.waitingForBarrier = false
        cancelBarrierTimeout(tab)
        tab.displayState = TabDisplayState.Closed
        if (tab === activeTab) renderActiveTab()
    }

    private fun showBlockedNavigation(tab: BrowserTab) {
        invalidateTabThumbnail(tab)
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
        newPageButton.isEnabled = enabled
        securityButton.isEnabled = enabled
        goButton.isEnabled = enabled
        val alpha = if (enabled) EnabledControlAlpha else DisabledControlAlpha
        addressInput.alpha = alpha
        newPageButton.alpha = alpha
        securityButton.alpha = alpha
        goButton.alpha = alpha
    }

    private fun reloadFromPullGesture() {
        val tab = activeTab
        if (!extensionReady || tab == null || tab.url == InitialBlankPage) {
            swipeRefresh.isRefreshing = false
            return
        }
        beginProtectedLoad(tab, startNewPerformanceNavigation = true)
        tab.session.reload()
        handler.postDelayed({ swipeRefresh.isRefreshing = false }, PullRefreshTimeoutMillis)
    }

    private fun showSecurityDetails() {
        val tab = activeTab
        val secureConnection = tab?.url?.startsWith("https://", ignoreCase = true) == true
        val pageProtection = tab?.displayState == TabDisplayState.Visible && !tab.waitingForBarrier
        val newPage = tab == null || tab.url == InitialBlankPage
        val host = tab?.url?.let { runCatching { Uri.parse(it).host }.getOrNull() }.orEmpty()
        val pageLabel = host.ifBlank { getString(R.string.security_new_page) }
        val connectionLabel =
            when {
                newPage -> R.string.security_connection_unavailable
                secureConnection -> R.string.security_secure_connection
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
                append(getString(R.string.security_detail))
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
        val previousTab = activeTab
        if (
            previousTab != null &&
            previousTab.thumbnail == null &&
            canCaptureThumbnail(previousTab)
        ) {
            captureActiveTabThumbnail {
                switchToWithoutCapture(tab)
            }
            return
        }
        switchToWithoutCapture(tab)
    }

    private fun switchToWithoutCapture(tab: BrowserTab) {
        if (!tabs.contains(tab) || tab === activeTab) return
        dismissActiveChoicePrompt()
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
                try {
                    thumbnailExecutor.execute {
                        val scaled = scaleThumbnail(bitmap)
                        if (scaled !== bitmap) bitmap.recycle()
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
                                )
                            ) {
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
            previewRestricted = tab.previewRestricted,
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
        tab.thumbnail?.recycle()
        tab.thumbnail = null
        refreshTabSwitcher()
    }

    private fun scaleThumbnail(source: Bitmap): Bitmap {
        val scale =
            minOf(
                1f,
                DagTabCapacityPolicy.ThumbnailWidth.toFloat() / source.width,
                DagTabCapacityPolicy.ThumbnailHeight.toFloat() / source.height,
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
                    R.id.menu_reload -> {
                        activeTab?.session?.reload()
                        true
                    }
                    R.id.menu_history -> {
                        showHistory()
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
                    R.id.menu_downloads -> {
                        showDownloads()
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
        }.show()
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
        val detail =
            if (packageInfo?.versionName.isNullOrBlank()) {
                getString(R.string.about_version_unavailable)
            } else {
                getString(
                    R.string.about_version_detail,
                    packageInfo.versionName,
                    packageInfo.longVersionCode,
                )
            }
        AlertDialog.Builder(this)
            .setTitle(R.string.about_dag)
            .setMessage(detail)
            .setPositiveButton(R.string.close, null)
            .show()
    }

    private fun recordHistory(tab: BrowserTab) {
        if (tab.url == InitialBlankPage || restorableUrl(tab.url) == null) return
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

    private fun showHistory() {
        val entries = historyPersistence.load(::isRestorableUrl)
        if (entries.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle(R.string.history)
                .setMessage(R.string.history_empty)
                .setPositiveButton(R.string.close, null)
                .show()
            return
        }
        val labels =
            entries.map { entry ->
                val host =
                    runCatching { Uri.parse(entry.url).host }
                        .getOrNull()
                        ?.removePrefix("www.")
                        .orEmpty()
                entry.title.takeIf(String::isNotBlank)?.let { "$it\n$host" } ?: host
            }
        AlertDialog.Builder(this)
            .setTitle(R.string.history)
            .setItems(labels.toTypedArray()) { _, index -> openHistoryEntry(entries[index]) }
            .setNeutralButton(R.string.clear_history) { _, _ ->
                historyPersistence.clear()
                Toast.makeText(this, R.string.history_cleared, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.close, null)
            .show()
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

    private fun showDownloads() {
        downloadsScreen?.dismiss()
        val dialog = Dialog(this)
        downloadsScreen = dialog
        dialog.setContentView(R.layout.view_dag_downloads)
        val list = dialog.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.downloads_list)
        val emptyState = dialog.findViewById<TextView>(R.id.downloads_empty_state)
        lateinit var adapter: DagDownloadsAdapter
        adapter =
            DagDownloadsAdapter(
                onOpen = { file ->
                    if (isStoredDownload(file)) openDownloadedPdf(file)
                    dialog.dismiss()
                },
                onDelete = { file -> confirmDeleteDownload(file) { renderDownloads(adapter, list, emptyState) } },
            )
        list.adapter = adapter
        dialog.findViewById<View>(R.id.downloads_close).setOnClickListener { dialog.dismiss() }
        dialog.setOnDismissListener {
            if (downloadsScreen === dialog) downloadsScreen = null
        }
        dialog.show()
        dialog.window?.setLayout(
            android.view.WindowManager.LayoutParams.MATCH_PARENT,
            android.view.WindowManager.LayoutParams.MATCH_PARENT,
        )
        renderDownloads(adapter, list, emptyState)
    }

    private fun renderDownloads(
        adapter: DagDownloadsAdapter,
        list: androidx.recyclerview.widget.RecyclerView,
        emptyState: TextView,
    ) {
        val files = downloadedPdfs()
        adapter.submit(files)
        list.visibility = if (files.isEmpty()) View.GONE else View.VISIBLE
        emptyState.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showDownloadedFile(file: File) {
        if (!isStoredDownload(file)) return
        AlertDialog.Builder(this)
            .setTitle(file.name)
            .setMessage(readableByteCount(file.length()))
            .setPositiveButton(R.string.open) { _, _ -> openDownloadedPdf(file) }
            .setNegativeButton(R.string.delete) { _, _ -> confirmDeleteDownload(file) }
            .setNeutralButton(R.string.close, null)
            .show()
    }

    private fun confirmDeleteDownload(
        file: File,
        onDeleted: () -> Unit = {},
    ) {
        if (!isStoredDownload(file)) return
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_download_title)
            .setMessage(file.name)
            .setPositiveButton(R.string.delete) { _, _ ->
                val message =
                    if (file.delete()) {
                        R.string.download_deleted
                    } else {
                        R.string.download_delete_failed
                    }
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                onDeleted()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun downloadedPdfs(): List<File> =
        File(filesDir, DownloadsDirectory)
            .listFiles { file -> file.isFile && file.name.endsWith(".pdf", ignoreCase = true) }
            .orEmpty()
            .sortedByDescending(File::lastModified)

    private fun isStoredDownload(file: File): Boolean {
        val directory = File(filesDir, DownloadsDirectory)
        return file.isFile &&
            file.name.endsWith(".pdf", ignoreCase = true) &&
            runCatching { file.canonicalFile.parentFile == directory.canonicalFile }.getOrDefault(false)
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

    private fun confirmCloseAllTabs() {
        if (tabs.size <= 1) return
        AlertDialog.Builder(this)
            .setTitle(R.string.close_all_tabs_title)
            .setMessage(getString(R.string.close_all_tabs_detail, tabs.size))
            .setPositiveButton(R.string.close_all_tabs) { _, _ ->
                tabSwitcher.hide()
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
        historyPersistence.clear()
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
            DagBackAction.CloseTabSwitcher -> tabSwitcher.hide()
            DagBackAction.GoBackInPage -> tab?.session?.goBack()
            DagBackAction.GoHome -> tab?.let(::goHome)
            DagBackAction.ExitBrowser -> finish()
        }
    }

    override fun onStart() {
        super.onStart()
        activeTab?.let { setTabActivity(it, active = true) }
    }

    override fun onStop() {
        dismissActiveChoicePrompt()
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
            tab.previewRevision += 1
            tab.thumbnail?.recycle()
            tab.thumbnail = null
        }
        refreshTabSwitcher()
    }

    override fun onDestroy() {
        dismissActiveChoicePrompt()
        persistTabsNow()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            backInvokedCallback?.let(onBackInvokedDispatcher::unregisterOnBackInvokedCallback)
            backInvokedCallback = null
        }
        activeDownload?.let(::cancelDownload)
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
        downloadExecutor.shutdownNow()
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

    private data class ChoicePromptRow(
        val choice: GeckoSession.PromptDelegate.ChoicePrompt.Choice,
        val label: String,
        val enabled: Boolean,
        val selected: Boolean,
    )

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
        var displayState: TabDisplayState = TabDisplayState.Ready,
        var barrierTimeout: Runnable? = null,
        var needsRestore: Boolean = false,
        var thumbnail: Bitmap? = null,
        var previewRevision: Long = 0,
        var previewDocumentToken: String? = null,
        var previewEligibilityToken: String? = null,
        var previewRestricted: Boolean = true,
        var recovering: Boolean = false,
        var navigationRevision: Long = 0,
        var downloadGesture: DagDownloadGesture? = null,
    )

    private class ActiveDownload(
        val tab: BrowserTab,
        val metadata: DagAllowedDownload,
        val input: InputStream,
        @Volatile var cancelled: Boolean = false,
        @Volatile var partialFile: File? = null,
    )

    private companion object {
        const val ExtensionLocation = "resource://android/assets/dag-protection/"
        const val ExtensionId = "dag-protection@glosh.local"
        const val NativeApp = "glosh.dag.protection"
        const val BarrierReadyMessage = "barrier-ready"
        const val PreviewEligibilityMessage = "tab-preview-eligibility"
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
        const val BackNavigationLogTag = "DagBackNavigation"
        const val TabPreviewLogTag = "DagTabPreview"
        const val BarrierTimeoutMillis = 12_000L
        const val InitialBlankPage = "about:blank"
        const val MaxTabLabelLength = 36
        const val PersistTabsDelayMillis = 250L
        const val ThumbnailCaptureTimeoutMillis = 1_200L
        const val ThumbnailCaptureRetryDelayMillis = 120L
        const val ThumbnailCaptureRetries = 1
        const val PullRefreshTimeoutMillis = 2_500L
        const val DownloadsDirectory = "downloads"
        const val DownloadBufferBytes = 16 * 1024
        const val PdfHeaderBytes = 8
        const val PdfTailBytes = 4 * 1024
        const val MaxDuplicateFileSuffix = 999
        val PreviewDocumentTokenPattern = Regex("^document_[a-f0-9]{1,16}$")
        const val EnabledControlAlpha = 1f
        const val DisabledControlAlpha = 0.45f
        const val EnabledChoiceAlpha = 1f
        const val DisabledChoiceAlpha = 0.38f
        const val MaxChoiceLabelLength = 200
        const val ChoiceGroupSeparator = " — "
        const val DefaultBrowserRoleRequestCode = 4_201
        const val BrowserSetupPreferences = "dag-browser-setup"
        const val DefaultBrowserPromptShownKey = "default-browser-prompt-shown"
    }
}
