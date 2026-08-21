package com.contentfilter.user.apps

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contentfilter.core.ui.GloshColors
import com.contentfilter.core.ui.GloshShapes
import com.contentfilter.core.ui.GloshSpacing
import com.contentfilter.core.ui.GloshSurfaceCard
import com.contentfilter.core.ui.ProductGlyph
import com.contentfilter.core.ui.ProductIcon
import com.contentfilter.core.ui.ProductPageHeader
import com.contentfilter.core.ui.ProductSectionHeader
import com.contentfilter.core.ui.StatusChip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun MyAppsRoute(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    viewModel: MyAppsViewModel = hiltViewModel(),
) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshApps() }
    val state = viewModel.uiState.collectAsStateWithLifecycle()
    MyAppsScreen(
        state = state.value,
        onSearchChanged = viewModel::onSearchChanged,
        onRefreshApps = viewModel::refreshApps,
        onRequestAccess = viewModel::requestAccess,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
private fun MyAppsScreen(
    state: MyAppsUiState,
    onSearchChanged: (String) -> Unit,
    onRefreshApps: () -> Unit,
    onRequestAccess: (String) -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var quickFilter by remember { mutableStateOf(MyAppsQuickFilter.All) }
    var searchExpanded by remember { mutableStateOf(state.searchQuery.isNotBlank()) }
    val groupedPackages = remember(state.appGroups) { state.appGroups.flatMap { it.packageNames }.toSet() }
    val searchedApps =
        remember(state.apps, state.searchQuery) {
            val query = state.searchQuery.trim().lowercase()
            if (query.isBlank()) state.apps else state.apps.filter {
                it.name.lowercase().contains(query) || it.packageName.lowercase().contains(query)
            }
        }
    val ungroupedApps = remember(searchedApps, groupedPackages) { searchedApps.filter { it.packageName !in groupedPackages } }
    val visibleApps =
        remember(ungroupedApps, quickFilter) {
            ungroupedApps.filter { app ->
                when (quickFilter) {
                    MyAppsQuickFilter.All -> true
                    MyAppsQuickFilter.WithTime -> app.dailyLimitMinutes != null || app.extraTimeRemainingMinutes != null
                    MyAppsQuickFilter.Blocked ->
                        app.status == AppAccessStatus.Blocked ||
                            app.status == AppAccessStatus.RequiresAuthorization ||
                            app.status == AppAccessStatus.LimitReached
                    MyAppsQuickFilter.InGroup -> false
                }
            }
        }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(GloshColors.Bone)
                .statusBarsPadding()
                .padding(horizontal = GloshSpacing.PageHorizontal, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ProductPageHeader(
            title = "Mis apps",
            subtitle = "${state.apps.size} apps · acceso y tiempo disponible",
            onBack = onBack,
        )
        AppsToolbar(
            apps = ungroupedApps,
            groups = state.appGroups,
            selected = quickFilter,
            searchQuery = state.searchQuery,
            searchExpanded = searchExpanded,
            refreshing = state.isRefreshing,
            message = state.message,
            lastRefreshedAtEpochMillis = state.lastRefreshedAtEpochMillis,
            onSelected = { quickFilter = it },
            onSearchExpandedChanged = { expanded ->
                searchExpanded = expanded
                if (!expanded) onSearchChanged("")
            },
            onSearchChanged = onSearchChanged,
            onRefreshApps = onRefreshApps,
        )
        if (state.isRefreshing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = GloshColors.Graphite)
        }

        val emptyStateMessage = myAppsEmptyStateMessage(state.apps.isEmpty(), state.isRefreshing)
        if (emptyStateMessage != null) {
            EmptyAppsCard(emptyStateMessage)
        } else if (quickFilter == MyAppsQuickFilter.InGroup) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { ProductSectionHeader("Grupos de apps", count = state.appGroups.size) }
                items(state.appGroups, key = { it.id }) { group ->
                    MyAppGroupCard(
                        group = group,
                        apps = searchedApps.filter { it.packageName in group.packageNames },
                    )
                }
            }
        } else {
            MyAppsNativeList(
                apps = visibleApps,
                scrollResetKey = "${quickFilter.name}:${state.searchQuery.trim()}",
                onRequestAccess = onRequestAccess,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun EmptyAppsCard(text: String) {
    GloshSurfaceCard {
        Text("Todavía no hay apps para mostrar", style = MaterialTheme.typography.titleMedium, color = GloshColors.Graphite)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = GloshColors.Muted)
    }
}

internal fun myAppsEmptyStateMessage(
    appsEmpty: Boolean,
    refreshing: Boolean,
): String? =
    when {
        !appsEmpty -> null
        refreshing -> "Buscando aplicaciones instaladas…"
        else -> "Abrí o actualizá Glosh para detectar las apps instaladas."
    }

@Composable
private fun AppsToolbar(
    apps: List<MyAppItemUiState>,
    groups: List<MyAppGroupUiState>,
    selected: MyAppsQuickFilter,
    searchQuery: String,
    searchExpanded: Boolean,
    refreshing: Boolean,
    message: String,
    lastRefreshedAtEpochMillis: Long?,
    onSelected: (MyAppsQuickFilter) -> Unit,
    onSearchExpandedChanged: (Boolean) -> Unit,
    onSearchChanged: (String) -> Unit,
    onRefreshApps: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(searchExpanded) {
        if (searchExpanded) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (searchExpanded) {
                OutlinedTextField(
                    modifier = Modifier.weight(1f).focusRequester(focusRequester),
                    value = searchQuery,
                    onValueChange = onSearchChanged,
                    placeholder = { Text("Buscar app") },
                    leadingIcon = { ProductGlyph(ProductIcon.Search, GloshColors.Muted, Modifier.size(22.dp)) },
                    singleLine = true,
                    shape = GloshShapes.Card,
                )
            } else {
                Text(
                    text = myAppsRefreshStatus(message, refreshing, lastRefreshedAtEpochMillis),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (message.isRefreshError()) GloshColors.Danger else GloshColors.Muted,
                    maxLines = 2,
                )
            }
            ToolbarCircleButton(onClick = onRefreshApps, enabled = !refreshing) {
                ProductGlyph(
                    ProductIcon.Refresh,
                    if (refreshing) GloshColors.Muted.copy(alpha = 0.45f) else GloshColors.Muted,
                    Modifier.size(22.dp).semantics { contentDescription = "Actualizar apps" },
                )
            }
            ToolbarCircleButton(onClick = { onSearchExpandedChanged(!searchExpanded) }) {
                ProductGlyph(
                    ProductIcon.Search,
                    GloshColors.Graphite,
                    Modifier.size(22.dp).semantics { contentDescription = "Buscar app" },
                )
            }
        }
        LazyRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(MyAppsQuickFilter.entries) { filter ->
                AppFilterBanner(
                    filter = filter,
                    selected = selected == filter,
                    count = filter.count(apps, groups),
                    onClick = { onSelected(filter) },
                )
            }
        }
    }
}

@Composable
private fun myAppsRefreshStatus(
    message: String,
    refreshing: Boolean,
    lastRefreshedAtEpochMillis: Long?,
): String {
    var nowEpochMillis by remember(lastRefreshedAtEpochMillis) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lastRefreshedAtEpochMillis) {
        if (lastRefreshedAtEpochMillis != null) {
            while (true) {
                delay(60_000)
                nowEpochMillis = System.currentTimeMillis()
            }
        }
    }
    return myAppsRefreshStatusText(message, refreshing, lastRefreshedAtEpochMillis, nowEpochMillis)
}

internal fun myAppsRefreshStatusText(
    message: String,
    refreshing: Boolean,
    lastRefreshedAtEpochMillis: Long?,
    nowEpochMillis: Long,
): String =
    when {
        refreshing -> "Actualizando…"
        message.isRefreshError() -> message
        message.startsWith("Solicitud") || message.startsWith("Enviando") -> message
        lastRefreshedAtEpochMillis != null -> {
            val minutes = ((nowEpochMillis - lastRefreshedAtEpochMillis).coerceAtLeast(0L) / 60_000L)
            if (minutes == 0L) "Actualizado ahora" else "Actualizado hace $minutes min"
        }
        else -> "Listo para actualizar"
    }

private fun String.isRefreshError(): Boolean =
    startsWith("No se pudo") || startsWith("No se pudieron") || startsWith("Sin conexión")

@Composable
private fun ToolbarCircleButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier =
            Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(GloshColors.Surface.copy(alpha = if (enabled) 1f else 0.72f))
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun AppFilterBanner(
    filter: MyAppsQuickFilter,
    selected: Boolean,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier =
            modifier
                .height(40.dp)
                .clip(GloshShapes.Pill)
                .background(if (selected) GloshColors.Lime else GloshColors.Surface)
                .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
                .padding(horizontal = 13.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(filter.label, style = MaterialTheme.typography.labelLarge, color = GloshColors.Graphite, maxLines = 1)
        Text(count.toString(), style = MaterialTheme.typography.labelSmall, color = GloshColors.Muted)
    }
}

private fun MyAppsQuickFilter.count(
    apps: List<MyAppItemUiState>,
    groups: List<MyAppGroupUiState>,
): Int =
    when (this) {
        MyAppsQuickFilter.All -> apps.size
        MyAppsQuickFilter.WithTime -> apps.count { it.dailyLimitMinutes != null || it.extraTimeRemainingMinutes != null }
        MyAppsQuickFilter.Blocked ->
            apps.count {
                it.status == AppAccessStatus.Blocked ||
                    it.status == AppAccessStatus.RequiresAuthorization ||
                    it.status == AppAccessStatus.LimitReached
            }
        MyAppsQuickFilter.InGroup -> groups.sumOf { it.appCount }
    }

private val MyAppsQuickFilter.label: String
    get() =
        when (this) {
            MyAppsQuickFilter.All -> "Todas"
            MyAppsQuickFilter.WithTime -> "Con tiempo"
            MyAppsQuickFilter.Blocked -> "Bloqueadas"
            MyAppsQuickFilter.InGroup -> "En grupos"
        }

@Composable
private fun MyAppGroupCard(
    group: MyAppGroupUiState,
    apps: List<MyAppItemUiState>,
) {
    var expanded by remember(group.id) { mutableStateOf(false) }
    GloshSurfaceCard(onClick = { expanded = !expanded }) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(group.name, style = MaterialTheme.typography.titleMedium, color = GloshColors.Graphite)
                Text("${group.appCount} apps", style = MaterialTheme.typography.bodySmall, color = GloshColors.Muted)
            }
            StatusChip("${group.usedMinutes}/${group.limitMinutes} min", GloshColors.Warning)
        }
        GroupIconStack(apps = apps, totalCount = group.appCount)
        LinearProgressIndicator(
            progress = { group.progress },
            modifier = Modifier.fillMaxWidth(),
            color = if (group.progress >= 0.9f) GloshColors.Danger else GloshColors.Graphite,
            trackColor = GloshColors.SurfaceMuted,
        )
        Text(group.label, style = MaterialTheme.typography.bodySmall, color = GloshColors.Muted)
        if (expanded) {
            if (apps.isEmpty()) {
                Text("No se detectaron apps instaladas para este grupo.", style = MaterialTheme.typography.bodySmall, color = GloshColors.Muted)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    apps.forEach { app -> GroupAppRow(app) }
                }
            }
        }
    }
}

@Composable
private fun GroupIconStack(
    apps: List<MyAppItemUiState>,
    totalCount: Int,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
            apps.take(5).forEach { app -> AppIcon(app.name, app.iconBase64, size = 34) }
        }
        if (totalCount > 5) StatusChip("+${totalCount - 5}", GloshColors.Graphite)
    }
}

@Composable
private fun GroupAppRow(app: MyAppItemUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app.name, app.iconBase64, size = 32)
        Column(modifier = Modifier.weight(1f)) {
            Text(app.name, style = MaterialTheme.typography.bodyMedium, color = GloshColors.Graphite)
            Text(app.limitText, style = MaterialTheme.typography.bodySmall, color = GloshColors.Muted)
        }
        StatusLabel(app.status, app.extraTimeRemainingMinutes)
    }
}

@Composable
private fun StatusLabel(
    status: AppAccessStatus,
    extraTimeRemainingMinutes: Int?,
) {
    StatusChip(status.displayName(extraTimeRemainingMinutes), status.statusColor())
}

@Composable
internal fun AppIcon(
    name: String,
    iconBase64: String?,
    size: Int = 40,
) {
    var bitmap by remember(iconBase64) { mutableStateOf(iconBase64?.let(AppIconBitmapCache::get)) }
    LaunchedEffect(iconBase64) {
        if (bitmap == null && iconBase64 != null) {
            bitmap =
                withContext(Dispatchers.Default) {
                    runCatching {
                        val bytes = Base64.decode(iconBase64, Base64.DEFAULT)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.also { decoded -> AppIconBitmapCache.put(iconBase64, decoded) }
                    }.getOrNull()
                }
        }
    }
    val displayedBitmap = bitmap
    if (displayedBitmap != null) {
        Image(
            bitmap = displayedBitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.size(size.dp).clip(CircleShape),
        )
    } else {
        FallbackIcon(name, size)
    }
}

private object AppIconBitmapCache : LruCache<String, Bitmap>(8 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
}

@Composable
private fun FallbackIcon(
    name: String,
    size: Int,
) {
    Box(
        modifier = Modifier.size(size.dp).clip(CircleShape).background(GloshColors.LimeSoft),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.firstOrNull()?.uppercaseChar()?.toString().orEmpty(),
            style = MaterialTheme.typography.titleMedium,
            color = GloshColors.Graphite,
        )
    }
}

private fun AppAccessStatus.displayName(extraTimeRemainingMinutes: Int? = null): String =
    when (this) {
        AppAccessStatus.Allowed -> "Permitida"
        AppAccessStatus.Limited -> "Con límite"
        AppAccessStatus.LimitReached -> "Límite agotado"
        AppAccessStatus.ExtraTime -> extraTimeRemainingMinutes?.let { "Extra ${it}m" } ?: "Tiempo extra"
        AppAccessStatus.Blocked -> "Bloqueada"
        AppAccessStatus.RequiresAuthorization -> "Necesita permiso"
        AppAccessStatus.WaitingAuthorization -> "Esperando permiso"
        AppAccessStatus.WaitingExtraTime -> "Esperando tiempo"
    }

private fun AppAccessStatus.statusColor(): Color =
    when (this) {
        AppAccessStatus.Allowed,
        AppAccessStatus.ExtraTime,
        -> GloshColors.Positive
        AppAccessStatus.Limited,
        AppAccessStatus.LimitReached,
        AppAccessStatus.WaitingExtraTime,
        -> GloshColors.Warning
        AppAccessStatus.Blocked,
        AppAccessStatus.RequiresAuthorization,
        AppAccessStatus.WaitingAuthorization,
        -> GloshColors.Danger
    }
