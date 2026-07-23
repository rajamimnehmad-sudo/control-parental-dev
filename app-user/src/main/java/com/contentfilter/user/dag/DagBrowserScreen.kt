package com.contentfilter.user.dag

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contentfilter.core.ui.ProductLargeFeatureCard
import com.contentfilter.core.ui.ProductViolet
import com.contentfilter.core.ui.ProductVisualPage
import com.contentfilter.user.BuildConfig
import kotlinx.coroutines.delay
import java.net.URI
import java.util.UUID

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun DagBrowserRoute(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    standalone: Boolean = false,
    viewModel: DagBrowserViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    var themePreference by remember { mutableStateOf(loadDagThemePreference(context)) }
    DagBrowserTheme(themePreference) {
        DagBrowserContent(
            modifier = modifier,
            onBack = onBack,
            standalone = standalone,
            viewModel = viewModel,
            themePreference = themePreference,
            onThemePreferenceChanged = { selected ->
                themePreference = selected
                saveDagThemePreference(context, selected)
            },
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun DagBrowserContent(
    modifier: Modifier,
    onBack: () -> Unit,
    standalone: Boolean,
    viewModel: DagBrowserViewModel,
    themePreference: DagThemePreference,
    onThemePreferenceChanged: (DagThemePreference) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(state.view, state.loading) {
        if (state.loading || state.view == DagView.Results || state.view == DagView.Browser) {
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
    var clearBrowsingCacheDialog by remember { mutableStateOf(false) }
    var visualCalibrationDialog by remember { mutableStateOf(false) }
    var visualCalibrationEnabled by remember { mutableStateOf(false) }
    var addressFocused by remember { mutableStateOf(false) }
    var activeWebView by remember { mutableStateOf<WebView?>(null) }
    var browserCanGoBack by remember { mutableStateOf(false) }
    var browserCanGoForward by remember { mutableStateOf(false) }
    var tabs by remember { mutableStateOf(listOf(DagTab(snapshot = viewModel.captureTab()))) }
    var activeTabId by remember { mutableStateOf(tabs.first().id) }
    var tabsRestored by remember { mutableStateOf(false) }
    var allowedGeolocationOrigins by remember { mutableStateOf(emptySet<String>()) }
    var pendingGeolocationOrigin by remember { mutableStateOf<String?>(null) }
    var pendingGeolocationDecision by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    val locationPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            val allowed = grants.values.any { it }
            val origin = pendingGeolocationOrigin
            if (allowed && origin != null) allowedGeolocationOrigins = allowedGeolocationOrigins + origin
            pendingGeolocationDecision?.invoke(allowed)
            pendingGeolocationOrigin = null
            pendingGeolocationDecision = null
        }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshCalibration()
    }

    LaunchedEffect(Unit) {
        val saved = viewModel.loadTabSession()
        if (saved != null) {
            val restoredTabs =
                saved.tabs.map {
                    DagTab(id = it.id, snapshot = it.snapshot, lastUsedAtEpochMillis = it.lastUsedAtEpochMillis)
                }
            val reusableHome =
                restoredTabs.maxByOrNull { if (it.snapshot.isEmptyTab()) it.lastUsedAtEpochMillis else Long.MIN_VALUE }
                    ?.takeIf { it.snapshot.isEmptyTab() }
            viewModel.openNewTab()
            val homeTab = reusableHome ?: DagTab(snapshot = viewModel.captureTab())
            tabs =
                if (reusableHome != null) {
                    restoredTabs
                } else {
                    (restoredTabs.sortedByDescending(DagTab::lastUsedAtEpochMillis).take(MaximumTabs - 1) + homeTab)
                }
            activeTabId = homeTab.id
            tabs =
                tabs.map {
                    if (it.id == activeTabId) it.copy(snapshot = viewModel.captureTab(), lastUsedAtEpochMillis = System.currentTimeMillis()) else it
                }
        }
        tabsRestored = true
    }

    LaunchedEffect(
        tabs,
        activeTabId,
        state.address,
        state.view,
        state.pageStatus,
        state.results,
        state.requestedUrl,
        tabsRestored,
    ) {
        if (!tabsRestored) return@LaunchedEffect
        delay(TabPersistenceDebounceMillis)
        val currentSnapshot = viewModel.captureTab().forPersistence()
        val savedTabs =
            tabs.map { tab ->
                DagSavedTab(
                    id = tab.id,
                    snapshot = if (tab.id == activeTabId) currentSnapshot else tab.snapshot.forPersistence(),
                    lastUsedAtEpochMillis = tab.lastUsedAtEpochMillis,
                )
            }
        viewModel.saveTabSession(DagSavedTabSession(activeTabId = activeTabId, tabs = savedTabs))
    }

    fun persistActiveTab() {
        tabs = tabs.map { tab -> if (tab.id == activeTabId) tab.copy(snapshot = viewModel.captureTab()) else tab }
    }

    fun captureActiveTabPreview() {
        val view = activeWebView ?: return
        if (state.pageStatus != DagPageStatus.Visible || view.width <= 0 || view.height <= 0) return
        val bitmap = Bitmap.createBitmap(TabPreviewWidth, TabPreviewHeight, Bitmap.Config.RGB_565)
        val canvas = Canvas(bitmap)
        canvas.scale(TabPreviewWidth.toFloat() / view.width, TabPreviewHeight.toFloat() / view.height)
        view.draw(canvas)
        tabs =
            tabs.map { tab ->
                if (tab.id == activeTabId) tab.copy(preview = bitmap.asImageBitmap()) else tab
            }
    }

    fun openTab(tab: DagTab) {
        persistActiveTab()
        activeTabId = tab.id
        viewModel.restoreTab(tab.snapshot)
        tabs =
            tabs.map {
                if (it.id == tab.id) it.copy(snapshot = tab.snapshot, lastUsedAtEpochMillis = System.currentTimeMillis()) else it
            }
        tabsExpanded = false
    }

    fun newTab() {
        persistActiveTab()
        val currentSnapshot = viewModel.captureTab()
        val reusable =
            tabs
                .map { if (it.id == activeTabId) it.copy(snapshot = currentSnapshot) else it }
                .filter { it.snapshot.isEmptyTab() }
                .maxByOrNull { it.lastUsedAtEpochMillis }
        if (reusable != null) {
            activeTabId = reusable.id
            viewModel.restoreTab(reusable.snapshot)
            tabs =
                tabs.map {
                    if (it.id == reusable.id) {
                        it.copy(snapshot = reusable.snapshot, lastUsedAtEpochMillis = System.currentTimeMillis())
                    } else {
                        it
                    }
                }
            tabsExpanded = false
            return
        }
        if (tabs.size >= MaximumTabs) return
        viewModel.openNewTab()
        val tab = DagTab(snapshot = viewModel.captureTab())
        tabs = tabs + tab
        activeTabId = tab.id
        tabsExpanded = false
    }

    fun closeAllTabs() {
        viewModel.clearTabSession()
        viewModel.openNewTab()
        val replacement = DagTab(snapshot = viewModel.captureTab())
        tabs = listOf(replacement)
        activeTabId = replacement.id
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

    val analyzing = state.loading || (state.view == DagView.Browser && state.pageStatus == DagPageStatus.Loading)

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().then(if (standalone) Modifier.statusBarsPadding() else Modifier),
        ) {
            if (state.view == DagView.Start) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "DAG",
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    TextButton(
                        onClick = ::newTab,
                        enabled =
                            tabs.size < MaximumTabs ||
                                viewModel.captureTab().isEmptyTab() ||
                                tabs.any { it.snapshot.isEmptyTab() },
                        modifier = Modifier.width(48.dp).semantics { contentDescription = "Nueva pestaña" },
                    ) { Text("＋", style = MaterialTheme.typography.titleLarge) }
                    TextButton(
                        onClick = {
                            persistActiveTab()
                            tabsExpanded = true
                        },
                        modifier = Modifier.width(48.dp).semantics { contentDescription = "Pestañas: ${tabs.size}" },
                    ) { Text(tabs.size.toString(), style = MaterialTheme.typography.labelLarge) }
                    Box {
                        TextButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.width(48.dp).semantics { contentDescription = "Menú de DAG" },
                        ) { Text("⋮", style = MaterialTheme.typography.headlineSmall) }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("Historial") },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.showHistory()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Pendientes de revisión") },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.showReviewRequests()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Borrar caché de navegación") },
                                onClick = {
                                    menuExpanded = false
                                    clearBrowsingCacheDialog = true
                                },
                            )
                            if (BuildConfig.DAG_VISUAL_CALIBRATION_AVAILABLE) {
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (state.calibrationVersion > 0) {
                                                "Calibración aplicada: #${state.calibrationVersion}"
                                            } else {
                                                "Calibración aplicada: base"
                                            },
                                        )
                                    },
                                    enabled = false,
                                    onClick = {},
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (visualCalibrationEnabled) {
                                                "✓ Calibración DEV"
                                            } else {
                                                "Calibración DEV"
                                            },
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        if (visualCalibrationEnabled) {
                                            visualCalibrationEnabled = false
                                        } else {
                                            visualCalibrationDialog = true
                                        }
                                    },
                                )
                            }
                            HorizontalDivider()
                            DagThemePreference.entries.forEach { preference ->
                                DropdownMenuItem(
                                    text = {
                                        Text("${if (themePreference == preference) "✓ " else ""}${preference.label}")
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onThemePreferenceChanged(preference)
                                    },
                                )
                            }
                        }
                    }
                }
            }
            if (state.view != DagView.Start) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = viewModel::showStart,
                        modifier = Modifier.width(48.dp).semantics { contentDescription = "Inicio" },
                    ) {
                        Text("⌂", style = MaterialTheme.typography.titleLarge)
                    }
                    DagAnalysisFrame(
                        analyzing = analyzing,
                        progress = state.analysisProgress,
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(52.dp),
                        cornerRadius = 26.dp,
                    ) {
                        OutlinedTextField(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .onFocusChanged { focusState ->
                                        if (focusState.isFocused && !addressFocused) {
                                            if (analyzing) {
                                                viewModel.beginAddressEdit()
                                            } else {
                                                viewModel.onAddressChanged(
                                                    "",
                                                )
                                            }
                                        }
                                        addressFocused = focusState.isFocused
                                    },
                            value = if (analyzing) "" else state.address,
                            onValueChange = viewModel::onAddressChanged,
                            placeholder = {
                                if (analyzing) DagAnalyzingText() else Text("Buscar o escribir dirección")
                            },
                            readOnly = false,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            shape = RoundedCornerShape(26.dp),
                            colors =
                                OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    cursorColor = MaterialTheme.colorScheme.primary,
                                    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    focusedBorderColor = if (analyzing) Color.Transparent else MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = if (analyzing) Color.Transparent else MaterialTheme.colorScheme.outline,
                                    focusedContainerColor =
                                        if (analyzing) {
                                            DagNeonCyan.copy(
                                                alpha = 0.10f,
                                            )
                                        } else {
                                            MaterialTheme.colorScheme.surface
                                        },
                                    unfocusedContainerColor =
                                        if (analyzing) {
                                            DagNeonViolet.copy(alpha = 0.08f)
                                        } else {
                                            MaterialTheme.colorScheme.surface
                                        },
                                ),
                            keyboardOptions =
                                androidx.compose.foundation.text.KeyboardOptions(
                                    imeAction = ImeAction.Go,
                                ),
                            keyboardActions =
                                androidx.compose.foundation.text.KeyboardActions(
                                    onGo = { viewModel.submitAddress() },
                                ),
                        )
                    }
                    TextButton(
                        onClick = ::newTab,
                        enabled =
                            tabs.size < MaximumTabs ||
                                viewModel.captureTab().isEmptyTab() ||
                                tabs.any { it.snapshot.isEmptyTab() },
                        modifier = Modifier.width(48.dp).semantics { contentDescription = "Nueva pestaña" },
                    ) { Text("＋", style = MaterialTheme.typography.titleLarge) }
                    Box {
                        TextButton(
                            onClick = {
                                captureActiveTabPreview()
                                persistActiveTab()
                                tabsExpanded = true
                            },
                            modifier =
                                Modifier
                                    .width(48.dp)
                                    .semantics { contentDescription = "Pestañas: ${tabs.size}" },
                        ) { Text(tabs.size.toString(), style = MaterialTheme.typography.labelLarge) }
                    }
                    Box {
                        TextButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.width(48.dp).semantics { contentDescription = "Menú de DAG" },
                        ) {
                            Text("⋮", style = MaterialTheme.typography.headlineSmall)
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("Atrás") },
                                enabled =
                                    state.view != DagView.Start &&
                                        (state.view != DagView.Browser || browserCanGoBack || state.results.isNotEmpty()),
                                onClick = {
                                    menuExpanded = false
                                    if (state.view == DagView.Browser && activeWebView?.canGoBack() == true) {
                                        activeWebView?.goBack()
                                    } else {
                                        if (state.view == DagView.Browser) {
                                            viewModel.backFromBrowser()
                                        } else {
                                            viewModel.showStart()
                                        }
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Adelante") },
                                enabled = state.view == DagView.Browser && browserCanGoForward,
                                onClick = {
                                    menuExpanded = false
                                    activeWebView?.goForward()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Actualizar") },
                                enabled = state.view == DagView.Browser,
                                onClick = {
                                    menuExpanded = false
                                    activeWebView?.reload()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Historial") },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.showHistory()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Pendientes de revisión") },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.showReviewRequests()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Compartir enlace") },
                                enabled = state.view == DagView.Browser && state.pageStatus == DagPageStatus.Visible,
                                onClick = {
                                    menuExpanded = false
                                    state.requestedUrl?.let { shareDagUrl(context, it) }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Borrar caché de navegación") },
                                onClick = {
                                    menuExpanded = false
                                    clearBrowsingCacheDialog = true
                                },
                            )
                            if (BuildConfig.DAG_VISUAL_CALIBRATION_AVAILABLE) {
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (state.calibrationVersion > 0) {
                                                "Calibración aplicada: #${state.calibrationVersion}"
                                            } else {
                                                "Calibración aplicada: base"
                                            },
                                        )
                                    },
                                    enabled = false,
                                    onClick = {},
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (visualCalibrationEnabled) {
                                                "✓ Calibración DEV"
                                            } else {
                                                "Calibración DEV"
                                            },
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        if (visualCalibrationEnabled) {
                                            visualCalibrationEnabled = false
                                        } else {
                                            visualCalibrationDialog = true
                                        }
                                    },
                                )
                            }
                            HorizontalDivider()
                            DagThemePreference.entries.forEach { preference ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "${if (themePreference == preference) "✓ " else ""}${preference.label}",
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onThemePreferenceChanged(preference)
                                    },
                                )
                            }
                        }
                    }
                }
                HorizontalDivider()
                if (addressFocused && state.suggestions.isNotEmpty() && !analyzing) {
                    DagSearchSuggestions(state.suggestions, viewModel::selectSuggestion)
                }
            }

            if (state.message.isNotBlank() && !analyzing) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 1.dp,
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
                DagView.Start ->
                    DagStartContent(
                        address = state.address,
                        suggestions = state.suggestions,
                        analyzing = analyzing,
                        analysisProgress = state.analysisProgress,
                        onAddressChanged = viewModel::onAddressChanged,
                        onSuggestionSelected = viewModel::selectSuggestion,
                        onSubmit = viewModel::submitAddress,
                        onBeginEdit = viewModel::beginAddressEdit,
                        recentHistory = state.history,
                        onRecentSiteSelected = viewModel::openHistory,
                    )
                DagView.Results ->
                    DagResultsContent(
                        results = state.results,
                        query = state.searchQuery,
                        canLoadMore = state.canLoadMoreResults,
                        loading = state.loading,
                        onOpen = viewModel::openResult,
                        onLoadMore = viewModel::loadMoreResults,
                        onCorrectedSearch = viewModel::search,
                    )
                DagView.History ->
                    DagHistoryContent(
                        history = state.history,
                        onOpen = viewModel::openHistory,
                        onDelete = viewModel::deleteHistory,
                        onClear = viewModel::clearHistory,
                    )
                DagView.Reviews ->
                    DagReviewRequestsContent(
                        requests = state.reviewRequests,
                        onOpenApproved = viewModel::openApprovedReview,
                    )
                DagView.Browser ->
                    DagWebContent(
                        state = state,
                        onBackFromBrowser = viewModel::backFromBrowser,
                        onNavigate = viewModel::requestNavigation,
                        onPageStarted = viewModel::onPageStarted,
                        onPageTextReady = viewModel::onPageTextReady,
                        onViewportImagesReady = viewModel::onViewportImagesReady,
                        onViewportImageProgress = viewModel::onViewportImageProgress,
                        onCalibrationCandidate = viewModel::submitDagCalibrationCandidate,
                        visualCalibrationEnabled = visualCalibrationEnabled,
                        onManualCalibrationCandidate = viewModel::submitDagManualCalibrationCandidate,
                        onManualBlurReviewCandidate = viewModel::submitDagManualBlurReviewCandidate,
                        onBlockedAction = viewModel::onBrowserBlockedAction,
                        onGeolocationPrompt = { origin, decision ->
                            if (origin in allowedGeolocationOrigins) {
                                decision(true)
                            } else {
                                pendingGeolocationDecision?.invoke(false)
                                pendingGeolocationOrigin = origin
                                pendingGeolocationDecision = decision
                            }
                        },
                        onPageBlocked = viewModel::onPageBlocked,
                        onRendererGone = viewModel::onBrowserRendererGone,
                        onWebViewChanged = { activeWebView = it },
                        onNavigationStateChanged = { canGoBack, canGoForward ->
                            browserCanGoBack = canGoBack
                            browserCanGoForward = canGoForward
                        },
                    )
            }
        }
    }

    BackHandler(enabled = WindowInsets.isImeVisible) {
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    if (tabsExpanded) {
        DagTabSwitcher(
            tabs = tabs,
            activeTabId = activeTabId,
            currentSnapshot = viewModel.captureTab(),
            onDismiss = { tabsExpanded = false },
            onOpen = ::openTab,
            onClose = ::closeTab,
            onNew = ::newTab,
            onCloseAll = ::closeAllTabs,
        )
    }

    if (clearBrowsingCacheDialog) {
        AlertDialog(
            onDismissRequest = { clearBrowsingCacheDialog = false },
            title = { Text("¿Borrar caché de navegación?") },
            text = {
                Text(
                    "DAG volverá a analizar por completo las páginas. El historial, los inicios de sesión y las pestañas no se borrarán.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        activeWebView?.clearCache(true)
                        viewModel.clearPageApprovals()
                        clearBrowsingCacheDialog = false
                    },
                ) { Text("Borrar") }
            },
            dismissButton = {
                TextButton(onClick = { clearBrowsingCacheDialog = false }) { Text("Cancelar") }
            },
        )
    }

    if (visualCalibrationDialog) {
        AlertDialog(
            onDismissRequest = { visualCalibrationDialog = false },
            title = { Text("¿Activar Calibración DEV?") },
            text = {
                Text(
                    "Sólo para pruebas: DAG recargará la página y mostrará todas las fotos originales sin difuminar. Usá X si una foto permitida debería bloquearse y R si una foto que DAG habría difuminado debería revisarse como posible falso positivo. Al desactivar el modo, se restaura la protección normal.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        visualCalibrationEnabled = true
                        visualCalibrationDialog = false
                    },
                ) { Text("Activar") }
            },
            dismissButton = {
                TextButton(onClick = { visualCalibrationDialog = false }) { Text("Cancelar") }
            },
        )
    }

    pendingGeolocationOrigin?.let { origin ->
        AlertDialog(
            onDismissRequest = {
                pendingGeolocationDecision?.invoke(false)
                pendingGeolocationOrigin = null
                pendingGeolocationDecision = null
            },
            title = { Text("¿Permitir ubicación?") },
            text = {
                Text(
                    "${runCatching { URI(origin).host }.getOrNull() ?: origin} podrá usar tu ubicación durante esta sesión de DAG.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val alreadyGranted =
                            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                                PackageManager.PERMISSION_GRANTED ||
                                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                                PackageManager.PERMISSION_GRANTED
                        if (alreadyGranted) {
                            allowedGeolocationOrigins = allowedGeolocationOrigins + origin
                            pendingGeolocationDecision?.invoke(true)
                            pendingGeolocationOrigin = null
                            pendingGeolocationDecision = null
                        } else {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                ),
                            )
                        }
                    },
                ) { Text("Permitir") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingGeolocationDecision?.invoke(false)
                        pendingGeolocationOrigin = null
                        pendingGeolocationDecision = null
                    },
                ) { Text("No permitir") }
            },
        )
    }
}

internal data class DagTab(
    val id: String = UUID.randomUUID().toString(),
    val snapshot: DagTabSnapshot,
    val preview: ImageBitmap? = null,
    val lastUsedAtEpochMillis: Long = System.currentTimeMillis(),
)

private fun DagTabSnapshot.forPersistence(): DagTabSnapshot =
    copy(
        pageStatus = if (view == DagView.Browser) DagPageStatus.Loading else DagPageStatus.Idle,
        message = "",
        reviewCandidate = null,
    )

internal enum class DagThemePreference(
    val label: String,
) {
    System("Tema: según dispositivo"),
    Light("Tema: claro"),
    Dark("Tema: oscuro"),
}

internal fun dagUsesDarkTheme(
    preference: DagThemePreference,
    systemDark: Boolean,
): Boolean =
    when (preference) {
        DagThemePreference.System -> systemDark
        DagThemePreference.Light -> false
        DagThemePreference.Dark -> true
    }

@Composable
private fun DagBrowserTheme(
    preference: DagThemePreference,
    content: @Composable () -> Unit,
) {
    val dark = dagUsesDarkTheme(preference, isSystemInDarkTheme())
    val view = LocalView.current
    val window = view.context.findActivity()?.window
    val insetsController = remember(view, window) { window?.let { WindowCompat.getInsetsController(it, view) } }
    val originalAppearance =
        remember(insetsController) {
            insetsController?.let { it.isAppearanceLightStatusBars to it.isAppearanceLightNavigationBars }
        }
    SideEffect {
        insetsController?.apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
    DisposableEffect(insetsController, originalAppearance) {
        onDispose {
            originalAppearance?.let { (lightStatusBars, lightNavigationBars) ->
                insetsController?.isAppearanceLightStatusBars = lightStatusBars
                insetsController?.isAppearanceLightNavigationBars = lightNavigationBars
            }
        }
    }
    MaterialTheme(
        colorScheme = if (dark) DagDarkScheme else DagLightScheme,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        content = content,
    )
}

private fun shareDagUrl(
    context: Context,
    url: String,
) {
    val safeUrl = dagShareableUrl(url) ?: return
    val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, safeUrl)
    context.startActivity(Intent.createChooser(intent, "Compartir enlace"))
}

internal fun dagShareableUrl(url: String): String? =
    runCatching {
        URI(url).takeIf { it.scheme.equals("https", ignoreCase = true) && !it.host.isNullOrBlank() }?.toString()
    }.getOrNull()

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}

private fun loadDagThemePreference(context: Context): DagThemePreference {
    val stored =
        context.applicationContext
            .getSharedPreferences(DagPreferencesName, Context.MODE_PRIVATE)
            .getString(DagThemePreferenceKey, null)
    return DagThemePreference.entries.firstOrNull { it.name == stored } ?: DagThemePreference.System
}

private fun saveDagThemePreference(
    context: Context,
    preference: DagThemePreference,
) {
    context.applicationContext
        .getSharedPreferences(DagPreferencesName, Context.MODE_PRIVATE)
        .edit()
        .putString(DagThemePreferenceKey, preference.name)
        .apply()
}

private val DagLightScheme =
    lightColorScheme(
        primary = Color(0xFF006C8F),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFC4E8FF),
        onPrimaryContainer = Color(0xFF001F2A),
        background = Color(0xFFF7F9FC),
        onBackground = Color(0xFF111417),
        surface = Color.White,
        onSurface = Color(0xFF111417),
        surfaceVariant = Color(0xFFE8EEF3),
        onSurfaceVariant = Color(0xFF3E484E),
        outline = Color(0xFF6F797F),
        outlineVariant = Color(0xFFBFC8CE),
    )

private val DagDarkScheme =
    darkColorScheme(
        primary = Color(0xFF74D1FA),
        onPrimary = Color(0xFF003548),
        primaryContainer = Color(0xFF004D67),
        onPrimaryContainer = Color(0xFFC4E8FF),
        background = Color(0xFF101417),
        onBackground = Color(0xFFE1E3E6),
        surface = Color(0xFF181C20),
        onSurface = Color(0xFFE1E3E6),
        surfaceVariant = Color(0xFF3F484D),
        onSurfaceVariant = Color(0xFFBFC8CE),
        outline = Color(0xFF899298),
        outlineVariant = Color(0xFF3F484D),
    )

internal val DagNeonCyan = Color(0xFF00D8FF)
internal val DagNeonViolet = Color(0xFF8C6CFF)
private const val DagPreferencesName = "dag_browser_preferences"
private const val DagThemePreferenceKey = "theme_preference"
