package com.contentfilter.user.apps

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contentfilter.core.ui.FeedbackBanner
import com.contentfilter.core.ui.ProductAppBackground
import com.contentfilter.core.ui.ProductCard
import com.contentfilter.core.ui.ProductPageHeader
import com.contentfilter.core.ui.ProductSectionHeader
import com.contentfilter.core.ui.StatusChip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun MyAppsRoute(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    viewModel: MyAppsViewModel = hiltViewModel(),
) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshApps()
    }
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
            if (query.isBlank()) {
                state.apps
            } else {
                state.apps.filter {
                    it.name.lowercase().contains(query) || it.packageName.lowercase().contains(query)
                }
            }
        }
    val ungroupedApps =
        remember(searchedApps, groupedPackages) { searchedApps.filter { it.packageName !in groupedPackages } }
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
                .background(ProductAppBackground)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ProductPageHeader(
            title = "Mis apps",
            subtitle = "${state.apps.size} apps · permisos y tiempo disponible",
            onBack = onBack,
        )
        if (state.message.isNotBlank()) {
            FeedbackBanner(state.message, isError = state.message.startsWith("No se pudo"))
        }
        AppsToolbar(
            apps = ungroupedApps,
            groups = state.appGroups,
            selected = quickFilter,
            searchQuery = state.searchQuery,
            searchExpanded = searchExpanded,
            refreshing = state.isRefreshing,
            onSelected = { quickFilter = it },
            onSearchExpandedChanged = { expanded ->
                searchExpanded = expanded
                if (!expanded) onSearchChanged("")
            },
            onSearchChanged = onSearchChanged,
            onRefreshApps = onRefreshApps,
        )
        if (state.apps.isEmpty()) {
            Text("No hay apps detectadas todavía.")
        } else if (quickFilter == MyAppsQuickFilter.InGroup) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    ProductSectionHeader("Apps en grupo", count = state.appGroups.size)
                }
                items(state.appGroups, key = { it.id }) { group ->
                    MyAppGroupCard(
                        group = group,
                        apps = searchedApps.filter { it.packageName in group.packageNames },
                    )
                }
                item {
                    HorizontalDivider()
                }
            }
        } else {
            MyAppsNativeList(
                apps = visibleApps,
                onRequestAccess = onRequestAccess,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun AppsToolbar(
    apps: List<MyAppItemUiState>,
    groups: List<MyAppGroupUiState>,
    selected: MyAppsQuickFilter,
    searchQuery: String,
    searchExpanded: Boolean,
    refreshing: Boolean,
    onSelected: (MyAppsQuickFilter) -> Unit,
    onSearchExpandedChanged: (Boolean) -> Unit,
    onSearchChanged: (String) -> Unit,
    onRefreshApps: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    androidx.compose.runtime.LaunchedEffect(searchExpanded) {
        if (searchExpanded) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (searchExpanded) {
            OutlinedTextField(
                modifier =
                    Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                value = searchQuery,
                onValueChange = onSearchChanged,
                label = { Text("Buscar app") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
            )
        } else {
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(MyAppsQuickFilter.entries) { filter ->
                    AppFilterBanner(
                        filter = filter,
                        selected = selected == filter,
                        count = filter.count(apps, groups),
                        onClick = { onSelected(filter) },
                        modifier = Modifier.width(148.dp),
                    )
                }
            }
        }
        ToolbarCircleButton(onClick = onRefreshApps, enabled = !refreshing) {
            Icon(
                Icons.Filled.Refresh,
                contentDescription = "Actualizar",
                tint = if (refreshing) UserMuted.copy(alpha = 0.45f) else UserMuted,
                modifier =
                    Modifier
                        .size(22.dp)
                        .graphicsLayer { if (refreshing) rotationZ = 20f },
            )
        }
        ToolbarCircleButton(onClick = { onSearchExpandedChanged(!searchExpanded) }) {
            Icon(
                Icons.Filled.Search,
                contentDescription = "Buscar app",
                tint = UserInk,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

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
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = if (enabled) 1f else 0.72f))
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun AppFilterBanner(
    filter: MyAppsQuickFilter,
    selected: Boolean,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier =
            modifier
                .height(54.dp)
                .clip(shape)
                .background(if (selected) Color(0xFFE9EEF0) else Color.White, shape)
                .border(1.dp, if (selected) Color(0xFFB7C0C7) else Color(0xFFE1E7EA), shape)
                .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(filter.color),
            )
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    filter.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = UserInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("$count apps", style = MaterialTheme.typography.labelSmall, color = UserMuted)
            }
        }
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
            MyAppsQuickFilter.InGroup -> "Apps en grupo"
        }

private val MyAppsQuickFilter.color: Color
    get() =
        when (this) {
            MyAppsQuickFilter.All -> Color(0xFF64748B)
            MyAppsQuickFilter.WithTime -> Color(0xFFF9A825)
            MyAppsQuickFilter.Blocked -> Color(0xFFC62828)
            MyAppsQuickFilter.InGroup -> Color(0xFF00A6D6)
        }

@Composable
private fun MyAppGroupCard(
    group: MyAppGroupUiState,
    apps: List<MyAppItemUiState>,
) {
    var expanded by remember(group.id) { mutableStateOf(false) }
    ProductCard(onClick = { expanded = !expanded }) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(group.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${group.appCount} apps · reinicia 12 PM",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                StatusChip("${group.usedMinutes}/${group.limitMinutes}m", MaterialTheme.colorScheme.primary)
            }
            GroupIconStack(apps = apps, totalCount = group.appCount)
            LinearProgressIndicator(
                progress = { group.progress },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(group.label, style = MaterialTheme.typography.bodySmall)
            if (expanded) {
                if (apps.isEmpty()) {
                    Text(
                        text = "No se detectaron apps instaladas para este grupo.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        apps.forEach { app ->
                            GroupAppRow(app)
                        }
                    }
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .height(40.dp),
        ) {
            apps.take(5).forEachIndexed { index, app ->
                Box(modifier = Modifier.offset { IntOffset(index * 34, 0) }) {
                    AppIcon(app.name, app.iconBase64, size = 34)
                }
            }
        }
        if (totalCount > 5) {
            StatusChip("+${totalCount - 5}", MaterialTheme.colorScheme.primary)
        }
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
            Text(app.name, style = MaterialTheme.typography.bodyMedium)
            Text(app.limitText, style = MaterialTheme.typography.bodySmall)
        }
        StatusLabel(app.status, app.extraTimeRemainingMinutes)
    }
}

@Composable
private fun StatusLabel(
    status: AppAccessStatus,
    extraTimeRemainingMinutes: Int?,
) {
    val color = status.statusColor()
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(color.copy(alpha = 0.18f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = status.displayName(extraTimeRemainingMinutes),
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
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
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.also { decoded ->
                            AppIconBitmapCache.put(iconBase64, decoded)
                        }
                    }.getOrNull()
                }
        }
    }
    val displayedBitmap = bitmap
    if (displayedBitmap != null) {
        Image(
            bitmap = displayedBitmap.asImageBitmap(),
            contentDescription = null,
            modifier =
                Modifier
                    .size(size.dp)
                    .clip(CircleShape),
        )
    } else {
        FallbackIcon(name, size)
    }
}

private object AppIconBitmapCache : LruCache<String, Bitmap>(8 * 1024 * 1024) {
    override fun sizeOf(
        key: String,
        value: Bitmap,
    ): Int = value.allocationByteCount
}

@Composable
private fun FallbackIcon(
    name: String,
    size: Int,
) {
    Box(
        modifier =
            Modifier
                .size(size.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.firstOrNull()?.uppercaseChar()?.toString().orEmpty(),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

private fun AppAccessStatus.displayName(extraTimeRemainingMinutes: Int? = null): String =
    when (this) {
        AppAccessStatus.Allowed -> "Permitida"
        AppAccessStatus.Limited -> "Con límite"
        AppAccessStatus.LimitReached -> "Límite agotado"
        AppAccessStatus.ExtraTime -> extraTimeRemainingMinutes?.let { "Extra ${it}m" } ?: "Tiempo extra activo"
        AppAccessStatus.Blocked -> "Bloqueada"
        AppAccessStatus.RequiresAuthorization -> "Requiere autorización"
        AppAccessStatus.WaitingAuthorization -> "Esperando autorización"
        AppAccessStatus.WaitingExtraTime -> "Esperando más tiempo"
    }

private fun AppAccessStatus.statusColor(): Color =
    when (this) {
        AppAccessStatus.Allowed,
        AppAccessStatus.ExtraTime,
        -> AllowedGreen
        AppAccessStatus.Limited,
        AppAccessStatus.LimitReached,
        AppAccessStatus.WaitingExtraTime,
        -> WarningYellow
        AppAccessStatus.Blocked,
        AppAccessStatus.RequiresAuthorization,
        AppAccessStatus.WaitingAuthorization,
        -> BlockedRed
    }

private val AllowedGreen = Color(0xFF2E7D32)
private val BlockedRed = Color(0xFFC62828)
private val WarningYellow = Color(0xFFF9A825)
private val UserInk = Color(0xFF162235)
private val UserMuted = Color(0xFF68758A)
