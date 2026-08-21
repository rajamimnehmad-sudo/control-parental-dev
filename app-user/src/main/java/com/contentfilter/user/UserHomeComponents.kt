package com.contentfilter.user

import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.contentfilter.core.ui.GloshIconBubble
import com.contentfilter.core.ui.GloshShapes
import com.contentfilter.core.ui.GloshSpacing
import com.contentfilter.core.ui.GloshStatusPill
import com.contentfilter.core.ui.GloshSurfaceCard
import com.contentfilter.core.ui.GloshWordmark
import com.contentfilter.core.ui.ProductGlyph
import com.contentfilter.core.ui.ProductIcon
import com.contentfilter.feature.accessibility.service.DeviceAdminController
import com.contentfilter.feature.requests.RequestsViewModel
import com.contentfilter.feature.status.SystemStatusViewModel
import com.contentfilter.user.announcements.UserAnnouncementsViewModel
import com.contentfilter.user.apps.AppIcon
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

    val limitItems = remember(appsState) { nearLimitItems(appsState) }
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
        limitItems = limitItems,
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
    limitItems: List<UserHomeLimitItem>,
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
    Column(
        modifier = Modifier.fillMaxSize().background(GloshColors.Bone),
    ) {
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
                    bottom = 28.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(GloshSpacing.Section),
        ) {
            item {
                ProtectionOverviewCard(
                    protectionLevel = protectionLevel,
                    vpnActive = vpnActive,
                    accessibilityActive = accessibilityActive,
                    deviceAdminActive = deviceAdminActive,
                    syncHealthy = syncHealthy,
                    licenseHealthy = licenseHealthy,
                    onActivateVpn = onActivateVpn,
                    onActivateAccessibility = onActivateAccessibility,
                    onActivateDeviceAdmin = onActivateDeviceAdmin,
                    onOpenSettings = onOpenSettings,
                )
            }

            if (updateState.shouldShowOnHome) {
                item {
                    UpdateHomeCard(state = updateState, onUpdateNow = onUpdateNow)
                }
            }

            if (limitItems.isNotEmpty()) {
                item {
                    Text(
                        "Tu tiempo de hoy",
                        style = MaterialTheme.typography.labelLarge,
                        color = GloshColors.Muted,
                    )
                }
                items(limitItems.take(MaxHomeLimits), key = UserHomeLimitItem::id) { item ->
                    LimitHomeCard(item = item, onClick = onMyApps)
                }
                if (limitItems.size > MaxHomeLimits) {
                    item {
                        TextButton(onClick = onMyApps) {
                            Text("Ver todos los límites", color = GloshColors.Graphite)
                        }
                    }
                }
            }

            item {
                RequestsHomeCard(pendingRequests = pendingRequests, onClick = onRequests)
            }

            if (removalAuthorized) {
                item {
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
                .background(GloshColors.Bone)
                .statusBarsPadding()
                .padding(
                    start = GloshSpacing.PageHorizontal,
                    top = 12.dp,
                    end = GloshSpacing.PageHorizontal,
                    bottom = 20.dp,
                ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
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
                    ProductGlyph(ProductIcon.Bell, GloshColors.Graphite, Modifier.size(23.dp))
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
                style = MaterialTheme.typography.headlineSmall,
                color = GloshColors.Graphite,
            )
            if (communityName.isNotBlank()) {
                Text(communityName, style = MaterialTheme.typography.bodyMedium, color = GloshColors.Muted)
            }
        }
    }
}

@Composable
private fun ProtectionOverviewCard(
    protectionLevel: ProtectionLevel,
    vpnActive: Boolean,
    accessibilityActive: Boolean,
    deviceAdminActive: Boolean,
    syncHealthy: Boolean,
    licenseHealthy: Boolean,
    onActivateVpn: () -> Unit,
    onActivateAccessibility: () -> Unit,
    onActivateDeviceAdmin: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val visual = protectionVisual(protectionLevel)
    val issues =
        buildList {
            if (!vpnActive) add(HomeRepairItem("Protección de Internet", onActivateVpn))
            if (!accessibilityActive) add(HomeRepairItem("Protección de apps", onActivateAccessibility))
            if (!deviceAdminActive) add(HomeRepairItem("Protección contra desinstalación", onActivateDeviceAdmin))
            if (!syncHealthy) add(HomeRepairItem("Conexión con el administrador", onOpenSettings))
            if (!licenseHealthy) add(HomeRepairItem("Estado de la licencia", onOpenSettings))
        }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = GloshShapes.LargeCard,
        colors = CardDefaults.cardColors(containerColor = GloshColors.Surface),
        border = BorderStroke(1.dp, GloshColors.Line),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(52.dp).background(visual.softColor, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    ProductGlyph(visual.icon, visual.color, Modifier.size(29.dp))
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(visual.title, style = MaterialTheme.typography.titleLarge, color = GloshColors.Graphite)
                    Text(visual.subtitle, style = MaterialTheme.typography.bodyMedium, color = GloshColors.Muted)
                }
            }

            if (issues.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    issues.forEach { issue ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .background(GloshColors.WarningSoft, GloshShapes.Small)
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(modifier = Modifier.size(7.dp).background(GloshColors.Warning, CircleShape))
                            Text(
                                issue.label,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                color = GloshColors.Graphite,
                            )
                            TextButton(onClick = issue.onRepair) {
                                Text("Reparar", color = GloshColors.Graphite)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LimitHomeCard(
    item: UserHomeLimitItem,
    onClick: () -> Unit,
) {
    GloshSurfaceCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val firstIcon = item.icons.firstOrNull()
            AppIcon(firstIcon?.name ?: item.title, firstIcon?.iconBase64, size = 40)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    item.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                    color = GloshColors.Graphite,
                )
                Text(
                    if (item.remainingMinutes == 0) "Límite alcanzado" else "Quedan ${item.remainingMinutes} min",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.remainingMinutes == 0) GloshColors.Danger else GloshColors.Muted,
                )
            }
        }
        LinearProgressIndicator(
            progress = { item.progress },
            modifier = Modifier.fillMaxWidth(),
            color =
                when {
                    item.progress >= 0.90f -> GloshColors.Danger
                    item.progress >= 0.80f -> GloshColors.Warning
                    else -> GloshColors.Graphite
                },
            trackColor = GloshColors.SurfaceMuted,
        )
    }
}

@Composable
private fun RequestsHomeCard(
    pendingRequests: Int,
    onClick: () -> Unit,
) {
    GloshSurfaceCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GloshIconBubble(ProductIcon.Requests)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Solicitudes", style = MaterialTheme.typography.titleMedium, color = GloshColors.Graphite)
                Text(
                    if (pendingRequests == 0) "No tenés pedidos pendientes" else "$pendingRequests esperando respuesta",
                    style = MaterialTheme.typography.bodySmall,
                    color = GloshColors.Muted,
                )
            }
            if (pendingRequests > 0) {
                GloshStatusPill(pendingRequests.toString(), GloshColors.Warning)
            } else {
                ProductGlyph(ProductIcon.ChevronRight, GloshColors.Muted, Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun UpdateHomeCard(
    state: UpdatesUiState,
    onUpdateNow: () -> Unit,
) {
    GloshSurfaceCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GloshIconBubble(ProductIcon.Update)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Actualización disponible", style = MaterialTheme.typography.titleMedium, color = GloshColors.Graphite)
                Text(state.homeUpdateMessage, style = MaterialTheme.typography.bodySmall, color = GloshColors.Muted)
            }
        }
        if (state.status == UpdatesStatus.Downloading) {
            LinearProgressIndicator(
                progress = { (state.downloadProgressPercent ?: 0) / 100f },
                modifier = Modifier.fillMaxWidth(),
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
            Button(modifier = Modifier.fillMaxWidth(), onClick = onUninstall) {
                Text("Desinstalar ahora")
            }
            OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onCancel) {
                Text("Cancelar autorización")
            }
        }
    }
}

private data class HomeRepairItem(
    val label: String,
    val onRepair: () -> Unit,
)

private data class ProtectionVisual(
    val title: String,
    val subtitle: String,
    val color: androidx.compose.ui.graphics.Color,
    val softColor: androidx.compose.ui.graphics.Color,
    val icon: ProductIcon,
)

private fun protectionVisual(level: ProtectionLevel): ProtectionVisual =
    when (level) {
        ProtectionLevel.Protected ->
            ProtectionVisual(
                title = "Protección activa",
                subtitle = "Todo está funcionando correctamente.",
                color = GloshColors.Positive,
                softColor = GloshColors.PositiveSoft,
                icon = ProductIcon.ShieldCheck,
            )
        ProtectionLevel.Warning ->
            ProtectionVisual(
                title = "Hay algo por revisar",
                subtitle = "Podés resolverlo desde acá.",
                color = GloshColors.Warning,
                softColor = GloshColors.WarningSoft,
                icon = ProductIcon.ShieldAlert,
            )
        ProtectionLevel.Unprotected ->
            ProtectionVisual(
                title = "Protección incompleta",
                subtitle = "Completá los pasos pendientes para quedar protegido.",
                color = GloshColors.Danger,
                softColor = GloshColors.DangerSoft,
                icon = ProductIcon.ShieldAlert,
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
