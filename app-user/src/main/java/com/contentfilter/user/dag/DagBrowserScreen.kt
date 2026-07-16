package com.contentfilter.user.dag

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.net.http.SslError
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
import com.contentfilter.core.ui.PremiumFishMascot
import com.contentfilter.core.ui.ProductLargeFeatureCard
import com.contentfilter.core.ui.ProductViolet
import com.contentfilter.core.ui.ProductVisualPage
import kotlinx.coroutines.delay
import org.json.JSONArray
import java.text.DateFormat
import java.util.Date
import java.util.UUID
import kotlin.math.cos
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
            tabs = saved.tabs.map { DagTab(id = it.id, snapshot = it.snapshot) }
            activeTabId = saved.activeTabId
            viewModel.restoreTab(saved.tabs.first { it.id == saved.activeTabId }.snapshot)
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
        tabsExpanded = false
    }

    fun newTab() {
        if (tabs.size >= MaximumTabs) return
        persistActiveTab()
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
        modifier = modifier.then(if (standalone) Modifier.statusBarsPadding() else Modifier),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (state.view == DagView.Start) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = ::newTab,
                        enabled = tabs.size < MaximumTabs,
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
                                text = { Text("Borrar caché de navegación") },
                                onClick = {
                                    menuExpanded = false
                                    clearBrowsingCacheDialog = true
                                },
                            )
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
                        enabled = tabs.size < MaximumTabs,
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
                                text = { Text("Borrar caché de navegación") },
                                onClick = {
                                    menuExpanded = false
                                    clearBrowsingCacheDialog = true
                                },
                            )
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
                    DagSearchSuggestions(state.suggestions, viewModel::onAddressChanged)
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
                        onAddressChanged = viewModel::onAddressChanged,
                        onSubmit = viewModel::submitAddress,
                    )
                DagView.Results -> DagResultsContent(state.results, viewModel::openResult)
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
                        onBackFromBrowser = viewModel::backFromBrowser,
                        onNavigate = viewModel::requestNavigation,
                        onPageStarted = viewModel::onPageStarted,
                        onPageTextReady = viewModel::onPageTextReady,
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
}

private data class DagTab(
    val id: String = UUID.randomUUID().toString(),
    val snapshot: DagTabSnapshot,
    val preview: ImageBitmap? = null,
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
    SideEffect {
        view.context.findActivity()?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    DisposableEffect(view) {
        onDispose {
            view.context.findActivity()?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = true
                    isAppearanceLightNavigationBars = true
                }
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
    Box(modifier = modifier) {
        content()
        Canvas(modifier = Modifier.fillMaxSize()) {
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
                        Text("Pestañas", style = MaterialTheme.typography.titleLarge)
                        Text("${tabs.size} de $MaximumTabs", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row {
                        TextButton(onClick = onCloseAll, enabled = tabs.size > 1) { Text("Cerrar todas") }
                        TextButton(onClick = onNew, enabled = tabs.size < MaximumTabs) { Text("＋ Nueva") }
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    gridItems(tabs, key = { it.id }) { tab ->
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
                                    } ?: PremiumFishMascot(modifier = Modifier.width(72.dp).height(58.dp))
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
private const val TabPersistenceDebounceMillis = 500L

private fun DagTabSnapshot.tabLabel(position: Int): String =
    when (view) {
        DagView.Browser -> DagContentClassifier.domainFrom(requestedUrl.orEmpty()).ifBlank { "Pestaña $position" }
        DagView.Results -> address.ifBlank { "Resultados" }
        DagView.History -> "Historial"
        DagView.Start -> "Nueva pestaña"
    }

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun DagStartContent(
    address: String,
    suggestions: List<String>,
    analyzing: Boolean,
    onAddressChanged: (String) -> Unit,
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
            modifier = Modifier.fillMaxWidth().height(56.dp),
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
            DagSearchSuggestions(suggestions, onAddressChanged)
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
    onOpen: (DagSearchResult) -> Unit,
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
    onBlockedAction: (String) -> Unit,
    onPageBlocked: (String) -> Unit,
    onRendererGone: () -> Unit,
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
                    setBackgroundColor(android.graphics.Color.WHITE)
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
              dagBlurIntimateImage(node);
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
            style.textContent = 'video,audio,canvas,iframe { display:none !important; } img[data-dag-kosher-blurred="true"] { filter: blur(48px) saturate(0.05) brightness(0.82) !important; transform: scale(1.14) !important; }';
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
              attributeFilter: ['src', 'srcset', 'style', 'class', 'alt', 'title', 'aria-label']
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

private fun String?.decodeJavascriptString(): String? {
    if (this == null || this == "null") return null
    return runCatching { JSONArray("[$this]").optString(0).takeIf { it.isNotBlank() } }.getOrNull()
}

private const val MaximumTextExtractionRetries = 2
private const val TextExtractionRetryDelayMillis = 700L
