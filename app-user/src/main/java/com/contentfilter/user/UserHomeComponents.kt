package com.contentfilter.user

import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contentfilter.core.domain.model.ProtectionLevel
import com.contentfilter.core.ui.GloshColors
import com.contentfilter.core.ui.GloshShapes
import com.contentfilter.core.ui.GloshSpacing
import com.contentfilter.core.ui.GloshStatusPill
import com.contentfilter.core.ui.GloshWordmark
import com.contentfilter.core.ui.ProductGlyph
import com.contentfilter.core.ui.ProductIcon
import com.contentfilter.feature.accessibility.service.DeviceAdminController
import com.contentfilter.feature.requests.RequestsViewModel
import com.contentfilter.feature.status.SystemStatusViewModel
import com.contentfilter.user.announcements.UserAnnouncementsViewModel
import com.contentfilter.user.apps.AppIcon
import com.contentfilter.user.apps.MyAppsUiState
import com.contentfilter.user.apps.MyAppsViewModel
import com.contentfilter.user.protection.ProtectionViewModel
import com.contentfilter.user.updates.UpdatesStatus
import com.contentfilter.user.updates.UpdatesUiState

@Composable
internal fun UserHomeRoute(
    onRequests: () -> Unit,
    onAnnouncements: () -> Unit,
    onMyApps: () -> Unit,
    updateState: UpdatesUiState,
    onUpdateNow: () -> Unit,
    onActivateVpn: () -> Unit,
    onActivateAccessibility: () -> Unit,
    onActivateDeviceAdmin: () -> Unit,
    onOpenSettings: () -> Unit,
    requestsViewModel: RequestsViewModel = hiltViewModel(),
    homeViewModel: UserHomeViewModel = hiltViewModel(),
    statusViewModel: SystemStatusViewModel = hiltViewModel(),
    appsViewModel: MyAppsViewModel = hiltViewModel(),
    announcementsViewModel: UserAnnouncementsViewModel = hiltViewModel(),
    protectionViewModel: ProtectionViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val requestsState by requestsViewModel.uiState.collectAsStateWithLifecycle()
    val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val statusState by statusViewModel.uiState.collectAsStateWithLifecycle()
    val appsState by appsViewModel.uiState.collectAsStateWithLifecycle()
    val announcementsState by announcementsViewModel.state.collectAsStateWithLifecycle()
    val protectionState by protectionViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { announcementsViewModel.refresh() }

    val dailyItems = remember(appsState) { dailyUsageItems(appsState) }
    val vpnActive = statusState.vpnState == ActiveStateLabel
    val accessibilityActive = statusState.accessibilityState == ActiveStateLabel
    val deviceAdminActive = statusState.deviceAdminState == ActiveStateLabel
    val syncHealthy = statusState.syncState == ActiveStateLabel
    val licenseHealthy = statusState.activationState.isHealthyLicenseState()

    UserHomeScreen(
        greeting = homeState.greeting,
        communityName = statusState.communityName,
        protectionLevel = statusState.protectionLevel,
        announcementCount = announcementsState.unreadCount,
        pendingRequests = requestsState.pendingCount,
        dailyItems = dailyItems,
        appsLastRefreshedAtEpochMillis = appsState.lastRefreshedAtEpochMillis,
        updateState = updateState,
        vpnActive = vpnActive,
        accessibilityActive = accessibilityActive,
        deviceAdminActive = deviceAdminActive,
        syncHealthy = syncHealthy,
        licenseHealthy = licenseHealthy,
        removalAuthorized = protectionState.removalAuthorized,
        removalAuthorizationMessage = protectionState.message,
        onAnnouncements = onAnnouncements,
        onRequests = onRequests,
        onMyApps = onMyApps,
        onUpdateNow = onUpdateNow,
        onActivateVpn = onActivateVpn,
        onActivateAccessibility = onActivateAccessibility,
        onActivateDeviceAdmin = onActivateDeviceAdmin,
        onOpenSettings = onOpenSettings,
        onCancelRemovalAuthorization = protectionViewModel::cancelRemovalAuthorization,
        onAuthorizedRemoval = {
            if (protectionState.removalAuthorized) {
                context
                    .getSystemService(DevicePolicyManager::class.java)
                    .removeActiveAdmin(DeviceAdminController.component(context))
                context.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${context.packageName}")))
            }
        },
    )
}

@Composable
private fun UserHomeScreen(
    greeting: String,
    communityName: String,
    protectionLevel: ProtectionLevel,
    announcementCount: Int,
    pendingRequests: Int,
    dailyItems: List<UserHomeLimitItem>,
    appsLastRefreshedAtEpochMillis: Long?,
    updateState: UpdatesUiState,
    vpnActive: Boolean,
    accessibilityActive: Boolean,
    deviceAdminActive: Boolean,
    syncHealthy: Boolean,
    licenseHealthy: Boolean,
    removalAuthorized: Boolean,
    removalAuthorizationMessage: String,
    onAnnouncements: () -> Unit,
    onRequests: () -> Unit,
    onMyApps: () -> Unit,
    onUpdateNow: () -> Unit,
    onActivateVpn: () -> Unit,
    onActivateAccessibility: () -> Unit,
    onActivateDeviceAdmin: () -> Unit,
    onOpenSettings: () -> Unit,
    onCancelRemovalAuthorization: () -> Unit,
    onAuthorizedRemoval: () -> Unit,
) {
    val issues =
        remember(vpnActive, accessibilityActive, deviceAdminActive, syncHealthy, licenseHealthy) {
            buildList {
                if (!vpnActive) add(HomeRepairItem("Protección de Internet", onActivateVpn))
                if (!accessibilityActive) add(HomeRepairItem("Protección de apps", onActivateAccessibility))
                if (!deviceAdminActive) add(HomeRepairItem("Protección contra desinstalación", onActivateDeviceAdmin))
                if (!syncHealthy) add(HomeRepairItem("Conexión con el administrador", onOpenSettings))
                if (!licenseHealthy) add(HomeRepairItem("Estado de la licencia", onOpenSettings))
            }
        }

    Column(modifier = Modifier.fillMaxSize().background(GloshColors.Bone)) {
        UserHomeTopBar(
            greeting = greeting,
            communityName = communityName,
            announcementCount = announcementCount,
            onAnnouncements = onAnnouncements,
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding =
                PaddingValues(
                    start = GloshSpacing.PageHorizontal,
                    top = 4.dp,
                    end = GloshSpacing.PageHorizontal,
                    bottom = 30.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item {
                ProtectionHero(
                    protectionLevel = protectionLevel,
                    syncHealthy = syncHealthy,
                    licenseHealthy = licenseHealthy,
                    issues = issues,
                )
            }

            if (dailyItems.isNotEmpty()) {
                item {
                    DailyOverview(
                        items = dailyItems.take(2),
                        lastRefreshedAtEpochMillis = appsLastRefreshedAtEpochMillis,
                        onClick = onMyApps,
                    )
                }
            }

            item {
                HomeSectionHeader(
                    title = "Ahora",
                    action = "Ver todas",
                    onAction = onMyApps,
                )
            }
            if (dailyItems.isEmpty()) {
                item {
                    HomePlainRow(
                        icon = ProductIcon.Apps,
                        title = "Tus apps",
                        subtitle = "Consultá qué podés usar y tus límites de hoy.",
                        trailing = null,
                        onClick = onMyApps,
                    )
                }
            } else {
                dailyItems.take(MaxHomeLimits).forEach { item ->
                    item(key = item.id) {
                        DailyAppRow(item = item, onClick = onMyApps)
                    }
                }
            }

            item {
                RequestsRow(pendingRequests = pendingRequests, onClick = onRequests)
            }

            if (announcementCount > 0) {
                item {
                    HomePlainRow(
                        icon = ProductIcon.Bell,
                        title = "Avisos",
                        subtitle = "$announcementCount sin leer",
                        trailing = announcementCount.toString(),
                        onClick = onAnnouncements,
                    )
                }
            }

            if (updateState.shouldShowOnHome) {
                item {
                    HomeSectionHeader(title = "Glosh")
                    UpdateHomeBlock(state = updateState, onUpdateNow = onUpdateNow)
                }
            }

            if (removalAuthorized) {
                item {
                    HomeSectionHeader(title = "Acción autorizada")
                    RemovalAuthorizationCard(
                        message = removalAuthorizationMessage,
                        onCancel = onCancelRemovalAuthorization,
                        onUninstall = onAuthorizedRemoval,
                    )
                }
            }
        }
    }
}

@Composable
private fun UserHomeTopBar(
    greeting: String,
    communityName: String,
    announcementCount: Int,
    onAnnouncements: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(
                    start = GloshSpacing.PageHorizontal,
                    top = 12.dp,
                    end = GloshSpacing.PageHorizontal,
                    bottom = 16.dp,
                ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            GloshWordmark(modifier = Modifier.weight(1f))
            Box {
                IconButton(
                    onClick = onAnnouncements,
                    modifier =
                        Modifier
                            .size(44.dp)
                            .background(GloshColors.Surface, CircleShape)
                            .semantics { contentDescription = "Abrir avisos" },
                ) {
                    ProductGlyph(ProductIcon.Bell, GloshColors.Graphite, Modifier.size(22.dp))
                }
                if (announcementCount > 0) {
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .size(18.dp)
                                .background(GloshColors.Lime, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            announcementCount.coerceAtMost(99).toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = GloshColors.Graphite,
                        )
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                greeting.ifBlank { "Hola" },
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = GloshColors.Graphite,
            )
            Text(
                if (communityName.isBlank()) "Tu día en Glosh" else communityName,
                style = MaterialTheme.typography.bodyMedium,
                color = GloshColors.Muted,
            )
        }
    }
}

@Composable
private fun ProtectionHero(
    protectionLevel: ProtectionLevel,
    syncHealthy: Boolean,
    licenseHealthy: Boolean,
    issues: List<HomeRepairItem>,
) {
    val visual = protectionVisual(protectionLevel)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = GloshShapes.LargeCard,
        colors = CardDefaults.cardColors(containerColor = GloshColors.Graphite),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(modifier = Modifier.size(9.dp).background(visual.accent, CircleShape))
                Text(
                    visual.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = GloshColors.Surface,
                )
                Text(
                    if (syncHealthy) "Sincronización activa" else "Esperando sincronización",
                    style = MaterialTheme.typography.labelSmall,
                    color = GloshColors.Surface.copy(alpha = 0.72f),
                )
            }
            Text(
                visual.headline,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = GloshColors.Surface,
            )
            Text(
                visual.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = GloshColors.Surface.copy(alpha = 0.78f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeroMetaPill(if (licenseHealthy) "Licencia activa" else "Revisar licencia")
                HeroMetaPill("DEV ${BuildConfig.VERSION_CODE}")
            }
            issues.take(2).forEach { issue ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(GloshShapes.Small)
                            .background(GloshColors.Surface.copy(alpha = 0.08f))
                            .clickable(onClick = issue.onRepair)
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    ProductGlyph(ProductIcon.ShieldAlert, GloshColors.Lime, Modifier.size(18.dp))
                    Text(
                        issue.label,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = GloshColors.Surface,
                    )
                    Text("Reparar", style = MaterialTheme.typography.labelMedium, color = GloshColors.Lime)
                }
            }
        }
    }
}

@Composable
private fun HeroMetaPill(text: String) {
    Box(
        modifier =
            Modifier
                .clip(GloshShapes.Pill)
                .background(GloshColors.Surface.copy(alpha = 0.09f))
                .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = GloshColors.Surface.copy(alpha = 0.88f))
    }
}

@Composable
private fun DailyOverview(
    items: List<UserHomeLimitItem>,
    lastRefreshedAtEpochMillis: Long?,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 18.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            items.forEach { item ->
                Column(
                    modifier = Modifier.weight(1f).clickable(onClick = onClick),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        item.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = GloshColors.Muted,
                    )
                    Text(
                        if (item.remainingMinutes == 0) "Límite alcanzado" else "${item.remainingMinutes} min",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (item.remainingMinutes == 0) GloshColors.Danger else GloshColors.Graphite,
                    )
                    Text(
                        "${item.usedMinutes} de ${item.limitMinutes} min usados",
                        style = MaterialTheme.typography.bodySmall,
                        color = GloshColors.Muted,
                    )
                    LinearProgressIndicator(
                        progress = { item.progress },
                        modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                        color = usageProgressColor(item.progress),
                        trackColor = GloshColors.Line,
                    )
                }
            }
        }
        Text(
            homeAppsRefreshLabel(lastRefreshedAtEpochMillis),
            style = MaterialTheme.typography.labelSmall,
            color = GloshColors.Muted,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(GloshColors.Line))
    }
}

@Composable
private fun HomeSectionHeader(
    title: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = GloshColors.Graphite,
        )
        if (action != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(action, style = MaterialTheme.typography.labelMedium, color = GloshColors.Muted)
            }
        }
    }
}

@Composable
private fun DailyAppRow(
    item: UserHomeLimitItem,
    onClick: () -> Unit,
) {
    val icon = item.icons.firstOrNull()
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppIcon(icon?.name ?: item.title, icon?.iconBase64, size = 40)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                item.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
                color = GloshColors.Graphite,
            )
            Text(
                "${item.usedMinutes} min usados · ${item.remainingMinutes} min disponibles",
                style = MaterialTheme.typography.bodySmall,
                color = GloshColors.Muted,
            )
        }
        HomeTrailingPill(
            text = if (item.remainingMinutes == 0) "Bloqueada" else "${item.remainingMinutes} min",
            danger = item.remainingMinutes == 0,
        )
    }
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(GloshColors.Line))
}

@Composable
private fun RequestsRow(
    pendingRequests: Int,
    onClick: () -> Unit,
) {
    HomePlainRow(
        icon = ProductIcon.Requests,
        title = if (pendingRequests == 0) "Solicitudes" else "Tenés $pendingRequests pedido${if (pendingRequests == 1) "" else "s"} pendiente${if (pendingRequests == 1) "" else "s"}",
        subtitle = if (pendingRequests == 0) "No hay nada esperando respuesta." else "Seguí el estado desde acá.",
        trailing = if (pendingRequests > 0) "Esperando" else null,
        trailingWarning = pendingRequests > 0,
        onClick = onClick,
    )
}

@Composable
private fun HomePlainRow(
    icon: ProductIcon,
    title: String,
    subtitle: String,
    trailing: String?,
    trailingWarning: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(42.dp).clip(GloshShapes.Small).background(GloshColors.Surface),
            contentAlignment = Alignment.Center,
        ) {
            ProductGlyph(icon, GloshColors.Graphite, Modifier.size(21.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = GloshColors.Graphite)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = GloshColors.Muted)
        }
        if (trailing != null) {
            HomeTrailingPill(text = trailing, warning = trailingWarning)
        } else {
            ProductGlyph(ProductIcon.ChevronRight, GloshColors.Muted, Modifier.size(21.dp))
        }
    }
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(GloshColors.Line))
}

@Composable
private fun HomeTrailingPill(
    text: String,
    warning: Boolean = false,
    danger: Boolean = false,
) {
    val background =
        when {
            danger -> GloshColors.DangerSoft
            warning -> GloshColors.WarningSoft
            else -> GloshColors.PositiveSoft
        }
    val foreground =
        when {
            danger -> GloshColors.Danger
            warning -> GloshColors.Warning
            else -> GloshColors.Positive
        }
    Box(
        modifier =
            Modifier
                .clip(GloshShapes.Pill)
                .background(background)
                .padding(horizontal = 9.dp, vertical = 6.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = foreground, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun UpdateHomeBlock(
    state: UpdatesUiState,
    onUpdateNow: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ProductGlyph(ProductIcon.Update, GloshColors.Graphite, Modifier.size(22.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Actualización disponible", style = MaterialTheme.typography.titleMedium, color = GloshColors.Graphite)
                Text(state.homeUpdateMessage, style = MaterialTheme.typography.bodySmall, color = GloshColors.Muted)
            }
        }
        if (state.status == UpdatesStatus.Downloading) {
            LinearProgressIndicator(
                progress = { (state.downloadProgressPercent ?: 0) / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = GloshColors.Graphite,
                trackColor = GloshColors.Line,
            )
        } else {
            Button(modifier = Modifier.fillMaxWidth(), onClick = onUpdateNow) {
                Text(if (state.status == UpdatesStatus.Available) "Actualizar" else "Reintentar")
            }
        }
    }
}

@Composable
private fun RemovalAuthorizationCard(
    message: String,
    onCancel: () -> Unit,
    onUninstall: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = GloshShapes.Card,
        colors = CardDefaults.cardColors(containerColor = GloshColors.DangerSoft),
        border = BorderStroke(1.dp, GloshColors.Danger.copy(alpha = 0.18f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Desinstalación autorizada", style = MaterialTheme.typography.titleMedium, color = GloshColors.Graphite)
            Text(
                message.ifBlank { "La autorización es temporal." },
                style = MaterialTheme.typography.bodySmall,
                color = GloshColors.Muted,
            )
            Button(modifier = Modifier.fillMaxWidth(), onClick = onUninstall) { Text("Desinstalar ahora") }
            OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onCancel) { Text("Cancelar autorización") }
        }
    }
}

private fun dailyUsageItems(state: MyAppsUiState): List<UserHomeLimitItem> =
    state.apps
        .mapNotNull { app ->
            val limit = app.dailyLimitMinutes ?: return@mapNotNull null
            if (limit <= 0) return@mapNotNull null
            UserHomeLimitItem(
                id = "app:${app.packageName}",
                title = app.name,
                kind = UserHomeLimitKind.App,
                usedMinutes = app.usedMinutes.coerceAtLeast(0),
                limitMinutes = limit,
                icons = listOf(UserHomeAppIcon(app.name, app.iconBase64)),
            )
        }
        .sortedWith(
            compareByDescending<UserHomeLimitItem> { it.progress }
                .thenBy { it.remainingMinutes }
                .thenBy { it.title.lowercase() },
        )

private fun homeAppsRefreshLabel(lastRefreshedAtEpochMillis: Long?): String {
    if (lastRefreshedAtEpochMillis == null) return "Apps listas para actualizar"
    val minutes = ((System.currentTimeMillis() - lastRefreshedAtEpochMillis).coerceAtLeast(0L) / 60_000L)
    return if (minutes == 0L) "Apps actualizadas ahora" else "Apps actualizadas hace $minutes min"
}

private fun usageProgressColor(progress: Float) =
    when {
        progress >= 0.90f -> GloshColors.Danger
        progress >= 0.80f -> GloshColors.Warning
        else -> GloshColors.Graphite
    }

private data class HomeRepairItem(
    val label: String,
    val onRepair: () -> Unit,
)

private data class ProtectionVisual(
    val title: String,
    val headline: String,
    val subtitle: String,
    val accent: androidx.compose.ui.graphics.Color,
)

private fun protectionVisual(level: ProtectionLevel): ProtectionVisual =
    when (level) {
        ProtectionLevel.Protected ->
            ProtectionVisual(
                title = "Protección activa",
                headline = "Todo está funcionando bien",
                subtitle = "Internet, apps y protección están aplicados correctamente.",
                accent = GloshColors.Lime,
            )
        ProtectionLevel.Warning ->
            ProtectionVisual(
                title = "Hay algo por revisar",
                headline = "Glosh necesita una acción",
                subtitle = "Podés resolver lo pendiente sin entrar en detalles técnicos.",
                accent = GloshColors.Warning,
            )
        ProtectionLevel.Unprotected ->
            ProtectionVisual(
                title = "Protección incompleta",
                headline = "Completá la protección",
                subtitle = "Hay pasos necesarios para volver a quedar protegido.",
                accent = GloshColors.Danger,
            )
    }

private val UpdatesUiState.shouldShowOnHome: Boolean
    get() =
        status == UpdatesStatus.Available ||
            status == UpdatesStatus.Downloading ||
            status == UpdatesStatus.DownloadFailed ||
            status == UpdatesStatus.ChecksumFailed

private val UpdatesUiState.homeUpdateMessage: String
    get() =
        when (status) {
            UpdatesStatus.Available ->
                manifest?.let { "Versión ${it.versionName} lista para instalar." }
                    ?: "Hay una versión nueva lista para instalar."
            UpdatesStatus.Downloading -> "Descargando… ${downloadProgressPercent ?: 0}%"
            UpdatesStatus.ChecksumFailed -> "La descarga no pasó la verificación de seguridad."
            UpdatesStatus.DownloadFailed -> "No se pudo descargar la actualización."
            else -> ""
        }

private fun String.isHealthyLicenseState(): Boolean =
    this == "Activada" || this == "Por vencer" || this == "Periodo de gracia"

internal val UserHomeHeaderTop = GloshColors.Bone
private const val ActiveStateLabel = "Activa"
private const val MaxHomeLimits = 3
