package com.contentfilter.user.dag

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.net.http.SslError
import android.os.SystemClock
import android.provider.Settings
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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.contentfilter.core.domain.model.AccessRequest
import com.contentfilter.core.domain.model.RequestStatus
import com.contentfilter.core.ui.ProductLargeFeatureCard
import com.contentfilter.core.ui.ProductViolet
import com.contentfilter.core.ui.ProductVisualPage
import com.contentfilter.user.BuildConfig
import kotlinx.coroutines.delay
import org.json.JSONArray
import java.net.URI
import java.text.DateFormat
import java.util.Date
import java.util.UUID
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import androidx.compose.foundation.lazy.grid.items as gridItems

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

    LaunchedEffect(Unit) {
        val saved = viewModel.loadTabSession()
        if (saved != null) {
            tabs =
                saved.tabs.map {
                    DagTab(id = it.id, snapshot = it.snapshot, lastUsedAtEpochMillis = it.lastUsedAtEpochMillis)
                }
            activeTabId = saved.activeTabId
            viewModel.restoreTab(saved.tabs.first { it.id == saved.activeTabId }.snapshot)
            tabs =
                tabs.map {
                    if (it.id == activeTabId) it.copy(lastUsedAtEpochMillis = System.currentTimeMillis()) else it
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
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = ::newTab,
                        enabled =
                            tabs.size < MaximumTabs ||
                                viewModel.captureTab().isEmptyTab() ||
                                tabs.any { it.snapshot.isEmptyTab() },
                        modifier = Modifier.width(44.dp).semantics { contentDescription = "Nueva pestaña" },
                    ) { Text("＋", style = MaterialTheme.typography.titleLarge) }
                    TextButton(
                        onClick = {
                            persistActiveTab()
                            tabsExpanded = true
                        },
                        modifier = Modifier.width(44.dp).semantics { contentDescription = "Pestañas: ${tabs.size}" },
                    ) { Text(tabs.size.toString()) }
                    Box {
                        TextButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.width(44.dp).semantics { contentDescription = "Menú de DAG" },
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
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = viewModel::showStart,
                        modifier = Modifier.width(40.dp).semantics { contentDescription = "Inicio" },
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
                                        if (focusState.isFocused && !addressFocused) viewModel.onAddressChanged("")
                                        addressFocused = focusState.isFocused
                                    },
                            value = if (analyzing) "" else state.address,
                            onValueChange = viewModel::onAddressChanged,
                            placeholder = {
                                if (analyzing) DagAnalyzingText() else Text("Buscar o escribir dirección")
                            },
                            readOnly = analyzing,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall,
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
                                            Color.Transparent
                                        },
                                    unfocusedContainerColor =
                                        if (analyzing) DagNeonViolet.copy(alpha = 0.08f) else Color.Transparent,
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
                        modifier = Modifier.width(40.dp).semantics { contentDescription = "Nueva pestaña" },
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
                                    .width(40.dp)
                                    .semantics { contentDescription = "Pestañas: ${tabs.size}" },
                        ) { Text(tabs.size.toString()) }
                    }
                    Box {
                        TextButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.width(40.dp).semantics { contentDescription = "Menú de DAG" },
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
                DagView.Start ->
                    DagStartContent(
                        address = state.address,
                        suggestions = state.suggestions,
                        analyzing = analyzing,
                        analysisProgress = state.analysisProgress,
                        onAddressChanged = viewModel::onAddressChanged,
                        onSuggestionSelected = viewModel::selectSuggestion,
                        onSubmit = viewModel::submitAddress,
                    )
                DagView.Results ->
                    DagResultsContent(
                        results = state.results,
                        canLoadMore = state.canLoadMoreResults,
                        loading = state.loading,
                        onOpen = viewModel::openResult,
                        onLoadMore = viewModel::loadMoreResults,
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
}

private data class DagTab(
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

private val DagNeonCyan = Color(0xFF00D8FF)
private val DagNeonViolet = Color(0xFF8C6CFF)
private const val DagPreferencesName = "dag_browser_preferences"
private const val DagThemePreferenceKey = "theme_preference"

@Composable
private fun DagAnalysisFrame(
    analyzing: Boolean,
    progress: Float,
    modifier: Modifier = Modifier,
    cornerRadius: Dp,
    content: @Composable () -> Unit,
) {
    if (!analyzing || !dagAnimationsEnabled()) {
        Box(modifier = modifier) { content() }
        return
    }
    val transition = rememberInfiniteTransition(label = "dag-analysis-glow")
    val angle by
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 1_800, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "dag-analysis-angle",
        )
    val visibleProgress = progress.coerceIn(0f, 1f)
    Box(modifier = modifier) {
        content()
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRoundRect(
                brush =
                    Brush.horizontalGradient(
                        colors =
                            listOf(
                                DagNeonCyan.copy(alpha = 0.03f),
                                DagNeonViolet.copy(alpha = 0.13f),
                                Color(0xFFFF4FD8).copy(alpha = 0.06f),
                            ),
                    ),
                size = androidx.compose.ui.geometry.Size(size.width * visibleProgress, size.height),
                cornerRadius =
                    androidx.compose.ui.geometry.CornerRadius(
                        cornerRadius.toPx(),
                        cornerRadius.toPx(),
                    ),
            )
            val radians = Math.toRadians(angle.toDouble())
            val direction =
                Offset(
                    x = (cos(radians) * size.width).toFloat(),
                    y = (sin(radians) * size.height).toFloat(),
                )
            drawRoundRect(
                brush =
                    Brush.linearGradient(
                        colors = listOf(DagNeonCyan, DagNeonViolet, Color(0xFFFF4FD8), DagNeonCyan),
                        start = center - direction,
                        end = center + direction,
                    ),
                cornerRadius =
                    androidx.compose.ui.geometry.CornerRadius(
                        cornerRadius.toPx(),
                        cornerRadius.toPx(),
                    ),
                style = Stroke(width = 3.dp.toPx()),
            )
        }
    }
}

@Composable
private fun DagAnalyzingText() {
    if (!dagAnimationsEnabled()) {
        Text("Analizando…")
        return
    }
    var dots by remember { mutableIntStateOf(1) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(350L)
            dots = dots % 3 + 1
        }
    }
    Text("Analizando${".".repeat(dots)}")
}

@Composable
private fun dagAnimationsEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
        }.getOrDefault(true)
    }
}

@Composable
private fun DagTabSwitcher(
    tabs: List<DagTab>,
    activeTabId: String,
    currentSnapshot: DagTabSnapshot,
    onDismiss: () -> Unit,
    onOpen: (DagTab) -> Unit,
    onClose: (DagTab) -> Unit,
    onNew: () -> Unit,
    onCloseAll: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxSize().padding(vertical = 24.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Pestañas recientes", style = MaterialTheme.typography.titleLarge)
                        Text("${tabs.size} de $MaximumTabs", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row {
                        TextButton(onClick = onCloseAll, enabled = tabs.size > 1) { Text("Cerrar todas") }
                        TextButton(
                            onClick = onNew,
                            enabled =
                                tabs.size < MaximumTabs ||
                                    currentSnapshot.isEmptyTab() ||
                                    tabs.any { it.snapshot.isEmptyTab() },
                        ) { Text("＋ Nueva") }
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    gridItems(tabs.sortedByDescending(DagTab::lastUsedAtEpochMillis), key = { it.id }) { tab ->
                        val snapshot = if (tab.id == activeTabId) currentSnapshot else tab.snapshot
                        Surface(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpen(tab.copy(snapshot = snapshot)) },
                            shape = RoundedCornerShape(16.dp),
                            border =
                                if (tab.id == activeTabId) {
                                    androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                                } else {
                                    null
                                },
                            tonalElevation = 2.dp,
                        ) {
                            Column {
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .height(170.dp)
                                            .background(MaterialTheme.colorScheme.surfaceContainer),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    tab.preview?.let { preview ->
                                        Image(
                                            bitmap = preview,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop,
                                        )
                                    } ?: Text(
                                        text = "DAG",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.titleLarge,
                                    )
                                    if (tab.id == activeTabId) {
                                        Surface(
                                            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                        ) {
                                            Text(
                                                "Actual",
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            )
                                        }
                                    }
                                    TextButton(
                                        onClick = { onClose(tab) },
                                        modifier =
                                            Modifier
                                                .align(Alignment.TopEnd)
                                                .semantics { contentDescription = "Cerrar pestaña" },
                                    ) { Text("×") }
                                }
                                Text(
                                    snapshot.tabLabel(tabs.indexOf(tab) + 1),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val TabPreviewWidth = 240
private const val TabPreviewHeight = 360
private const val MaximumTabs = 8
private const val MaxVisibleReviewRequests = 50
private const val TabPersistenceDebounceMillis = 500L

private fun DagTabSnapshot.tabLabel(position: Int): String =
    when (view) {
        DagView.Browser -> DagContentClassifier.domainFrom(requestedUrl.orEmpty()).ifBlank { "Pestaña $position" }
        DagView.Results -> address.ifBlank { "Resultados" }
        DagView.History -> "Historial"
        DagView.Reviews -> "Revisiones"
        DagView.Start -> "Nueva pestaña"
    }

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun DagStartContent(
    address: String,
    suggestions: List<String>,
    analyzing: Boolean,
    analysisProgress: Float,
    onAddressChanged: (String) -> Unit,
    onSuggestionSelected: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val keyboardVisible = WindowInsets.isImeVisible
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Spacer(Modifier.height(if (keyboardVisible) 56.dp else 112.dp))
        Text("DAG", style = MaterialTheme.typography.headlineLarge)
        Text(
            "Internet kosher con protección local",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        DagAnalysisFrame(
            analyzing = analyzing,
            progress = if (analyzing) analysisProgress else 0f,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .semantics {
                        if (analyzing) {
                            stateDescription = "${(analysisProgress * 100).roundToInt()} por ciento analizado"
                        }
                    },
            cornerRadius = 28.dp,
        ) {
            OutlinedTextField(
                value = if (analyzing) "" else address,
                onValueChange = onAddressChanged,
                modifier = Modifier.fillMaxSize(),
                placeholder = { if (analyzing) DagAnalyzingText() else Text("Buscar en Internet") },
                readOnly = analyzing,
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        cursorColor = MaterialTheme.colorScheme.primary,
                        focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        focusedBorderColor = if (analyzing) Color.Transparent else MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = if (analyzing) Color.Transparent else MaterialTheme.colorScheme.outline,
                        focusedContainerColor = if (analyzing) DagNeonCyan.copy(alpha = 0.10f) else Color.Transparent,
                        unfocusedContainerColor =
                            if (analyzing) {
                                DagNeonViolet.copy(
                                    alpha = 0.08f,
                                )
                            } else {
                                Color.Transparent
                            },
                    ),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onGo = { onSubmit() }),
                trailingIcon =
                    if (analyzing) {
                        null
                    } else {
                        { TextButton(onClick = onSubmit, enabled = address.isNotBlank()) { Text("Ir") } }
                    },
            )
        }
        if (suggestions.isNotEmpty() && !analyzing) {
            DagSearchSuggestions(suggestions, onSuggestionSelected)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Las páginas y sus imágenes se analizan antes de mostrarse.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DagSearchSuggestions(
    suggestions: List<String>,
    onSelect: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column {
            suggestions.forEach { suggestion ->
                Text(
                    text = suggestion,
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(suggestion) }.padding(14.dp, 10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun DagResultsContent(
    results: List<DagSearchResult>,
    canLoadMore: Boolean,
    loading: Boolean,
    onOpen: (DagSearchResult) -> Unit,
    onLoadMore: () -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(results, key = { it.url }) { result ->
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = result.classification.decision != DagClassification.Blocked) {
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (result.classification.decision == DagClassification.Uncertain) {
                    Text(
                        "DAG analizará la página antes de mostrarla",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
        }
        if (canLoadMore) {
            item(key = "more-results") {
                TextButton(
                    onClick = onLoadMore,
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                ) {
                    Text(if (loading) "Buscando…" else "Más resultados · consume 1 búsqueda")
                }
            }
        }
    }
}

@Composable
private fun DagReviewRequestsContent(
    requests: List<AccessRequest>,
    onOpenApproved: (AccessRequest) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text("Revisiones de DAG", style = MaterialTheme.typography.titleLarge)
            Text("Estados guardados en este teléfono", style = MaterialTheme.typography.bodySmall)
        }
        if (requests.isEmpty()) {
            Text(
                "Todavía no pediste revisiones de sitios.",
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(requests.take(MaxVisibleReviewRequests), key = AccessRequest::id) { request ->
                    val approved = request.status == RequestStatus.Approved
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = approved) { onOpenApproved(request) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            request.targetDomain ?: request.target,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (approved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(dagReviewStatusLabel(request.status), style = MaterialTheme.typography.labelLarge)
                        Text(
                            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                                .format(Date(request.createdAtEpochMillis)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (approved) {
                            Text(
                                "Tocá para abrir el sitio con la protección de DAG",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                }
            }
        }
    }
}

internal fun dagReviewStatusLabel(status: RequestStatus): String =
    when (status) {
        RequestStatus.PendingLocal -> "Pendiente de envío"
        RequestStatus.PendingRemote -> "Pendiente de revisión"
        RequestStatus.Approved -> "Aprobada"
        RequestStatus.Rejected -> "Rechazada"
        RequestStatus.Expired -> "Expirada"
    }

@Composable
private fun DagHistoryContent(
    history: List<DagHistoryEntry>,
    onOpen: (DagHistoryEntry) -> Unit,
    onDelete: (String) -> Unit,
    onClear: () -> Unit,
) {
    var confirmClear by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text("Historial", style = MaterialTheme.typography.titleLarge)
            Text("Guardado solo en este teléfono", style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = { confirmClear = true }, enabled = history.isNotEmpty()) { Text("Borrar todo") }
    }
    if (history.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Todavía no hay búsquedas ni páginas visitadas.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(history, key = { _, entry -> entry.id }) { index, entry ->
                val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(entry.visitedAtEpochMillis))
                val previousDate =
                    history.getOrNull(index - 1)?.let {
                        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it.visitedAtEpochMillis))
                    }
                if (date != previousDate) {
                    Text(
                        date,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(entry) }
                            .padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        modifier = Modifier.width(38.dp).height(38.dp),
                        shape = RoundedCornerShape(19.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(if (entry.type == DagHistoryType.Search) "⌕" else "↗")
                        }
                    }
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(
                            entry.title ?: entry.value,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            if (entry.type == DagHistoryType.Search) {
                                "Búsqueda"
                            } else {
                                DagContentClassifier.domainFrom(entry.url.orEmpty())
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(entry.visitedAtEpochMillis)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = { onDelete(entry.id) },
                        modifier = Modifier.semantics { contentDescription = "Borrar del historial" },
                    ) { Text("×") }
                }
                HorizontalDivider(modifier = Modifier.padding(start = 66.dp))
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
    val imageClassifier = remember(context) { DagImageClassifier(context) }
    val currentCalibrationCandidate by rememberUpdatedState(onCalibrationCandidate)
    val currentManualCalibrationCandidate by rememberUpdatedState(onManualCalibrationCandidate)
    val currentManualBlurReviewCandidate by rememberUpdatedState(onManualBlurReviewCandidate)
    val imageLoader =
        remember(imageClassifier) {
            DagImageResourceLoader(
                classifier = imageClassifier,
                onCalibrationCandidate = { image, classification ->
                    currentCalibrationCandidate(image, classification)
                },
            )
        }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var inspectedUrl by remember { mutableStateOf<String?>(null) }
    var preparedUrl by remember { mutableStateOf<String?>(null) }

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
            if (url.startsWith("https://", ignoreCase = true) && webView?.url != url) {
                webView?.settings?.blockNetworkImage = true
                webView?.loadUrl(url)
            }
        }
    }
    LaunchedEffect(webView, visualCalibrationEnabled) {
        val view = webView
        if (imageLoader.setDevCalibrationRevealEnabled(visualCalibrationEnabled)) {
            view?.url?.takeIf { it.startsWith("https://", ignoreCase = true) }?.let {
                view.settings.blockNetworkImage = true
                view.reload()
            }
        } else if (state.pageStatus == DagPageStatus.Visible) {
            view?.setDagVisualCalibrationMode(visualCalibrationEnabled)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            webView?.stopLoading()
            webView?.destroy()
            webView = null
            onWebViewChanged(null)
            imageLoader.cancel()
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
                    configureDagSettings(
                        calibrationDecision = imageLoader::manualCalibrationDecision,
                        onManualImageReported = { action, imageUrl ->
                            imageLoader.takeManualCalibrationCandidate(imageUrl)?.let { candidate ->
                                when (action) {
                                    DagCalibrationActionBlock ->
                                        currentManualCalibrationCandidate(candidate.thumbnail, candidate.classification)
                                    DagCalibrationActionReviewBlur ->
                                        currentManualBlurReviewCandidate(
                                            candidate.thumbnail,
                                            candidate.classification,
                                        )
                                }
                            }
                        },
                    )
                    setBackgroundColor(android.graphics.Color.WHITE)
                    val dagWebView = this
                    CookieManager.getInstance().apply {
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
                        view.sanitizeAndExtractVisibleText {
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
                        view.sanitizeAndExtractVisibleText { text ->
                            onPageTextReady(url, view.title.orEmpty(), text, imageLoader.pageSummary())
                        }
                    }
                    webViewClient =
                        DagWebViewClient(
                            onNavigate = onNavigate,
                            onStarted = { url ->
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
                                view.setDagVisualCalibrationMode(visualCalibrationEnabled)
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
            if (action == DagCalibrationActionBlock || action == DagCalibrationActionReviewBlur) {
                onManualImageReported(action, imageUrl)
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
    private val onCommitted: (WebView, String) -> Unit,
    private val onFinished: (WebView, String) -> Unit,
    private val onBlocked: (String) -> Unit,
    private val onRendererGone: (WebView) -> Unit,
    private val imageLoader: DagImageResourceLoader,
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
    ): WebResourceResponse? = imageLoader.intercept(request, pageUrlTracker.current())
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
    attempt: Int = 0,
    callback: (String?) -> Unit,
) {
    evaluateJavascript(
        """
        (function() {
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
            var marker = dagNormalizedText([image.className, image.id, image.getAttribute('alt')].join(' '));
            return !/\b(logo|icon|sprite|flag|payment)\b/.test(marker);
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
            var lazy = image.getAttribute('loading') === 'lazy' ||
              image.hasAttribute('data-src') || image.hasAttribute('data-lazy-src') ||
              image.hasAttribute('data-srcset') || image.hasAttribute('data-lazy-srcset') ||
              image.hasAttribute('data-dag-held-src');
            if (lazy && window.IntersectionObserver && image.getAttribute('data-dag-image-ready') !== 'true') {
              if (!window.__dagImageObserver) {
                var dagPrefetchMargin = Math.max((window.innerHeight || 0) * 2, 1200);
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
            if (!frame || !window.location) return false;
            var pageHost = (window.location.hostname || '').toLowerCase();
            var pagePath = window.location.pathname || '';
            if (pageHost !== 'buenosaires.gob.ar' || pagePath !== '/licenciasdeconducir/consulta-de-infracciones/') {
              return false;
            }
            try {
              var source = new URL(frame.getAttribute('src') || '', document.baseURI);
              var provider = source.hostname.toLowerCase();
              return source.protocol === 'https:' &&
                (provider === 'www.google.com' || provider === 'www.recaptcha.net') &&
                source.pathname.indexOf('/recaptcha/') === 0;
            } catch (_) {
              return false;
            }
          }
          function dagSecureNode(node) {
            if (!node || node.nodeType !== 1) return;
            var tag = node.tagName ? node.tagName.toLowerCase() : '';
            if (tag === 'iframe') {
              if (dagAllowedCaptchaFrame(node)) {
                if (node.getAttribute('data-dag-safe-captcha') !== 'true') {
                  node.setAttribute('data-dag-safe-captcha', 'true');
                }
                return;
              }
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
            node.querySelectorAll && node.querySelectorAll('video,audio,video source,audio source,canvas,iframe').forEach(dagSecureNode);
            node.querySelectorAll && node.querySelectorAll('img').forEach(function(image) {
              dagQueueImage(image);
            });
            node.querySelectorAll && node.querySelectorAll('*').forEach(dagSecureBackground);
          }
          function dagRemoveCalibrationMarkers() {
            document.querySelectorAll('[data-dag-calibration-marker="true"]').forEach(function(marker) {
              marker.remove();
            });
          }
          window.__dagImageCalibrationDecisions = window.__dagImageCalibrationDecisions || Object.create(null);
          window.__dagImageCalibrationProbes = window.__dagImageCalibrationProbes || Object.create(null);
          window.__dagSetImageCalibrationDecision = function(source, decision) {
            if (!source || !decision) return;
            window.__dagImageCalibrationDecisions[source] = decision;
            delete window.__dagImageCalibrationProbes[source];
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
              var decision = window.__dagImageCalibrationDecisions[source];
              if (!decision) {
                if (!window.__dagImageCalibrationProbes[source]) {
                  window.__dagImageCalibrationProbes[source] = true;
                  try {
                    window.$DagCalibrationBridgeName.postMessage(JSON.stringify({ action: 'probe', url: source }));
                  } catch (_) {}
                  window.setTimeout(function() {
                    delete window.__dagImageCalibrationProbes[source];
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
                  image.style.setProperty('filter', 'blur(48px) saturate(0.05) brightness(0.82)', 'important');
                  image.style.setProperty('transform', 'scale(1.14)', 'important');
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
          dagSecureNode(document.documentElement);
          var style = document.getElementById('dag-safe-style');
          if (!style) {
            style = document.createElement('style');
            style.id = 'dag-safe-style';
            style.textContent = 'video,audio,canvas,iframe:not([data-dag-safe-captcha="true"]) { display:none !important; } img[data-dag-kosher-blurred="true"] { filter: blur(48px) saturate(0.05) brightness(0.82) !important; transform: scale(1.14) !important; } html[data-dag-dev-calibration="true"] img[data-dag-kosher-blurred="true"] { filter:none !important; transform:none !important; }';
            document.documentElement.appendChild(style);
          }
          if (!window.__dagSafeObserver) {
            window.__dagSafeObserver = new MutationObserver(function(records) {
              var calibrationNeedsRefresh = false;
              records.forEach(function(record) {
                record.addedNodes.forEach(function(node) {
                  dagSecureNode(node);
                  if (!(node.nodeType === 1 && node.getAttribute('data-dag-calibration-marker') === 'true')) {
                    calibrationNeedsRefresh = true;
                  }
                });
                if (record.type === 'attributes') {
                  dagSecureNode(record.target);
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
                { runCatching { sanitizeAndExtractVisibleText(attempt + 1, callback) }.onFailure { callback(null) } },
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
    const val PreparedViewportCount = 3
    const val MaximumWaitMillis = 8_000L
    const val PollDelayMillis = 150L
    const val VisualSettleMillis = 1_200L
}

private fun String?.decodeJavascriptString(): String? {
    if (this == null || this == "null") return null
    return runCatching { JSONArray("[$this]").optString(0).takeIf { it.isNotBlank() } }.getOrNull()
}

private const val MaximumTextExtractionRetries = 2
private const val TextExtractionRetryDelayMillis = 700L
private const val PageCommitInspectionDelayMillis = 1_000L
private const val DagCalibrationBridgeName = "dagCalibrationBridge"
private const val DagCalibrationActionProbe = "probe"
private const val DagCalibrationActionBlock = "block"
private const val DagCalibrationActionReviewBlur = "review_blur"
