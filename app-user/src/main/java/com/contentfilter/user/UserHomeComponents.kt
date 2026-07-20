package com.contentfilter.user

import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contentfilter.core.domain.model.ProtectionLevel
import com.contentfilter.core.ui.ProductAppBackground
import com.contentfilter.core.ui.ProductFeatureTile
import com.contentfilter.core.ui.ProductGlyph
import com.contentfilter.core.ui.ProductIcon
import com.contentfilter.core.ui.ProductSun
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
    UserHomeTab(
        greeting = homeState.greeting,
        protectionLevel = statusState.protectionLevel,
        vpnState = statusState.vpnState,
        accessibilityState = statusState.accessibilityState,
        deviceAdminState = statusState.deviceAdminState,
        syncState = statusState.syncState,
        activationState = statusState.activationState,
        communityName = statusState.communityName,
        announcementCount = announcementsState.unreadCount,
        updateState = updateState,
        limitItems = limitItems,
        pendingRequests = requestsState.pendingCount,
        onRequests = onRequests,
        onAnnouncements = onAnnouncements,
        onMyApps = onMyApps,
        onUpdateNow = onUpdateNow,
        onActivateVpn = onActivateVpn,
        onActivateAccessibility = onActivateAccessibility,
        onActivateDeviceAdmin = onActivateDeviceAdmin,
        onOpenSettings = onOpenSettings,
        removalAuthorized = protectionState.removalAuthorized,
        removalAuthorizationMessage = protectionState.message,
        onCancelRemovalAuthorization = protectionViewModel::cancelRemovalAuthorization,
        onAuthorizedRemoval = {
            if (protectionState.removalAuthorized) {
                context
                    .getSystemService(DevicePolicyManager::class.java)
                    .removeActiveAdmin(DeviceAdminController.component(context))
                context.startActivity(
                    Intent(Intent.ACTION_DELETE, Uri.parse("package:${context.packageName}")),
                )
            }
        },
    )
}

@Composable
private fun UserHomeTab(
    greeting: String,
    protectionLevel: ProtectionLevel,
    vpnState: String,
    accessibilityState: String,
    deviceAdminState: String,
    syncState: String,
    activationState: String,
    communityName: String,
    announcementCount: Int,
    updateState: UpdatesUiState,
    limitItems: List<UserHomeLimitItem>,
    pendingRequests: Int,
    onRequests: () -> Unit,
    onAnnouncements: () -> Unit,
    onMyApps: () -> Unit,
    onUpdateNow: () -> Unit,
    onActivateVpn: () -> Unit,
    onActivateAccessibility: () -> Unit,
    onActivateDeviceAdmin: () -> Unit,
    onOpenSettings: () -> Unit,
    removalAuthorized: Boolean,
    removalAuthorizationMessage: String,
    onCancelRemovalAuthorization: () -> Unit,
    onAuthorizedRemoval: () -> Unit,
) {
    var expandedSection by rememberSaveable { mutableStateOf<UserHomeSection?>(null) }
    Column(modifier = Modifier.fillMaxSize().background(ProductAppBackground)) {
        UserHomeHeader(
            greeting = greeting,
            protectionLevel = protectionLevel,
            vpnState = vpnState,
            accessibilityState = accessibilityState,
            deviceAdminState = deviceAdminState,
            syncState = syncState,
            activationState = activationState,
            communityName = communityName,
            announcementCount = announcementCount,
            expanded = expandedSection == UserHomeSection.Protection,
            onToggleProtection = {
                expandedSection =
                    if (expandedSection == UserHomeSection.Protection) null else UserHomeSection.Protection
            },
            onAnnouncements = onAnnouncements,
            onActivateVpn = onActivateVpn,
            onActivateAccessibility = onActivateAccessibility,
            onActivateDeviceAdmin = onActivateDeviceAdmin,
            onOpenSettings = onOpenSettings,
            removalAuthorized = removalAuthorized,
            removalAuthorizationMessage = removalAuthorizationMessage,
            onCancelRemovalAuthorization = onCancelRemovalAuthorization,
            onAuthorizedRemoval = onAuthorizedRemoval,
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (updateState.shouldShowOnHome) {
                item {
                    UserHomeUpdateCard(
                        state = updateState,
                        onUpdateNow = onUpdateNow,
                    )
                }
            }
            if (limitItems.isNotEmpty()) {
                item {
                    UserHomeLimitsCard(
                        items = limitItems,
                        expanded = expandedSection == UserHomeSection.Limits,
                        onToggle = {
                            expandedSection =
                                if (expandedSection == UserHomeSection.Limits) null else UserHomeSection.Limits
                        },
                        onMyApps = onMyApps,
                    )
                }
            }
            item {
                ProductFeatureTile(
                    icon = ProductIcon.Requests,
                    title = "Solicitudes pendientes",
                    subtitle =
                        if (pendingRequests == 0) {
                            "No hay pedidos pendientes"
                        } else {
                            "$pendingRequests pendientes · tocá para ver"
                        },
                    accent = ProductSun,
                    onClick = onRequests,
                )
            }
        }
    }
}

@Composable
private fun UserHomeHeader(
    greeting: String,
    protectionLevel: ProtectionLevel,
    vpnState: String,
    accessibilityState: String,
    deviceAdminState: String,
    syncState: String,
    activationState: String,
    communityName: String,
    announcementCount: Int,
    expanded: Boolean,
    onToggleProtection: () -> Unit,
    onAnnouncements: () -> Unit,
    onActivateVpn: () -> Unit,
    onActivateAccessibility: () -> Unit,
    onActivateDeviceAdmin: () -> Unit,
    onOpenSettings: () -> Unit,
    removalAuthorized: Boolean,
    removalAuthorizationMessage: String,
    onCancelRemovalAuthorization: () -> Unit,
    onAuthorizedRemoval: () -> Unit,
) {
    val statusLabel =
        when (protectionLevel) {
            ProtectionLevel.Protected -> "Protección activa"
            ProtectionLevel.Warning -> "Protección por revisar"
            ProtectionLevel.Unprotected -> "Protección incompleta"
        }
    val statusColor =
        when (protectionLevel) {
            ProtectionLevel.Protected -> Color(0xFF72E6AA)
            ProtectionLevel.Warning -> Color(0xFFFFD166)
            ProtectionLevel.Unprotected -> Color(0xFFFF8A80)
        }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .animateContentSize()
                .background(
                    brush = Brush.linearGradient(listOf(UserHomeHeaderTop, UserHomeHeaderBottom)),
                    shape = RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp),
                )
                .statusBarsPadding()
                .padding(start = 20.dp, top = 18.dp, end = 14.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                )
                if (communityName.isNotBlank()) {
                    Text(
                        text = communityName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.72f),
                    )
                }
            }
            UserHomeAnnouncementButton(
                count = announcementCount,
                onClick = onAnnouncements,
            )
        }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                    .clickable(onClick = onToggleProtection)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.labelLarge,
                color = statusColor,
            )
            Text(
                text = if (expanded) "⌃" else "⌄",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White.copy(alpha = 0.82f),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                UserProtectionDetailRow(
                    label = "VPN",
                    state = vpnState,
                    active = vpnState == ActiveStateLabel,
                    help = "Aceptá la conexión VPN para proteger Internet.",
                    actionLabel = "Activar VPN",
                    onAction = onActivateVpn,
                )
                UserProtectionDetailRow(
                    label = "Accesibilidad",
                    state = accessibilityState,
                    active = accessibilityState == ActiveStateLabel,
                    help = "Abrí Accesibilidad, elegí Content Filter y activalo.",
                    actionLabel = "Abrir Accesibilidad",
                    onAction = onActivateAccessibility,
                )
                UserProtectionDetailRow(
                    label = "Desinstalación",
                    state = deviceAdminState,
                    active = deviceAdminState == ActiveStateLabel,
                    help =
                        if (removalAuthorized) {
                            "La desinstalación está autorizada temporalmente."
                        } else {
                            "Confirmá la protección contra desinstalación de Android."
                        },
                    actionLabel = "Activar protección",
                    onAction = onActivateDeviceAdmin,
                    actionVisible = !removalAuthorized,
                )
                UserProtectionDetailRow(
                    label = "Sincronización",
                    state = syncState,
                    active = syncState == ActiveStateLabel,
                    help = "Revisá la conexión y actualizá el estado desde Ajustes.",
                    actionLabel = "Abrir Ajustes",
                    onAction = onOpenSettings,
                )
                UserProtectionDetailRow(
                    label = "Licencia",
                    state = activationState,
                    active = activationState.isHealthyLicenseState(),
                    help = "El administrador debe revisar el enlace o la licencia.",
                    actionLabel = "Ver estado",
                    onAction = onOpenSettings,
                )
                if (removalAuthorized) {
                    UserRemovalAuthorizationPanel(
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
private fun UserRemovalAuthorizationPanel(
    message: String,
    onCancel: () -> Unit,
    onUninstall: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "Desinstalación autorizada temporalmente",
            style = MaterialTheme.typography.labelLarge,
            color = ReviewProtectionColor,
        )
        if (message.startsWith("No se pudo")) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFFFB4AB),
            )
        }
        Button(modifier = Modifier.fillMaxWidth(), onClick = onUninstall) {
            Text("Desinstalar ahora")
        }
        TextButton(modifier = Modifier.align(Alignment.End), onClick = onCancel) {
            Text("Cancelar autorización", color = Color.White.copy(alpha = 0.82f))
        }
    }
}

@Composable
private fun UserHomeAnnouncementButton(
    count: Int,
    onClick: () -> Unit,
) {
    Box {
        IconButton(
            onClick = onClick,
            modifier = Modifier.semantics { contentDescription = "Abrir avisos" },
        ) {
            ProductGlyph(
                icon = ProductIcon.Bell,
                color = Color.White,
                modifier = Modifier.size(25.dp),
            )
        }
        if (count > 0) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(20.dp)
                        .background(Color(0xFFFF5C65), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = count.coerceAtMost(99).toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun UserProtectionDetailRow(
    label: String,
    state: String,
    active: Boolean,
    help: String,
    actionLabel: String,
    onAction: () -> Unit,
    actionVisible: Boolean = true,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = Color.White)
            Text(
                text = state,
                style = MaterialTheme.typography.labelMedium,
                color = if (active) ActiveProtectionColor else ReviewProtectionColor,
            )
        }
        if (!active) {
            Text(
                modifier = Modifier.padding(top = 4.dp),
                text = help,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.72f),
            )
            if (actionVisible) {
                TextButton(
                    modifier = Modifier.align(Alignment.End),
                    onClick = onAction,
                ) {
                    Text(actionLabel, color = ReviewProtectionColor)
                }
            }
        }
    }
}

@Composable
private fun UserHomeUpdateCard(
    state: UpdatesUiState,
    onUpdateNow: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF3FF)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Nueva actualización disponible", style = MaterialTheme.typography.titleMedium)
            Text(
                text = state.homeUpdateMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF4A6075),
            )
            if (state.status == UpdatesStatus.Downloading) {
                LinearProgressIndicator(
                    progress = { (state.downloadProgressPercent ?: 0) / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Button(onClick = onUpdateNow) {
                    Text(if (state.status == UpdatesStatus.Available) "Actualizar ahora" else "Reintentar")
                }
            }
        }
    }
}

@Composable
private fun UserHomeLimitsCard(
    items: List<UserHomeLimitItem>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onMyApps: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize().clickable(onClick = onToggle),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Cerca del límite", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "${items.size} ${if (items.size == 1) "límite requiere" else "límites requieren"} atención",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF667085),
                    )
                }
                Text(
                    text = if (expanded) "⌃" else "⌄",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color(0xFF44546A),
                )
            }
            UserHomeLimitQueue(items)
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    items.take(MaxExpandedHomeLimits).forEach { item ->
                        UserHomeLimitRow(item)
                    }
                    TextButton(
                        modifier = Modifier.align(Alignment.End),
                        onClick = onMyApps,
                    ) {
                        Text("Ver todas en Mis apps")
                    }
                }
            }
        }
    }
}

@Composable
private fun UserHomeLimitQueue(items: List<UserHomeLimitItem>) {
    val icons =
        remember(items) {
            items
                .flatMap { item -> item.icons.ifEmpty { listOf(UserHomeAppIcon(item.title, null)) } }
                .distinctBy { "${it.name}:${it.iconBase64}" }
        }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.weight(1f).height(44.dp)) {
            icons.take(MaxQueueIcons).forEachIndexed { index, icon ->
                Box(
                    modifier =
                        Modifier
                            .offset(x = (index * QueueIconOffset).dp)
                            .zIndex(index.toFloat())
                            .size(40.dp)
                            .background(Color.White, CircleShape)
                            .border(2.dp, Color.White, CircleShape)
                            .padding(2.dp),
                ) {
                    AppIcon(icon.name, icon.iconBase64, size = 32)
                }
            }
        }
        if (icons.size > MaxQueueIcons) {
            Text(
                text = "+${icons.size - MaxQueueIcons}",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF44546A),
            )
        }
    }
}

@Composable
private fun UserHomeLimitRow(item: UserHomeLimitItem) {
    val progressColor =
        when {
            item.progress >= 0.90f -> Color(0xFFC62828)
            item.progress >= 0.80f -> Color(0xFFDA7B00)
            else -> Color(0xFF2C7BE5)
        }
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UserHomeItemIconStack(item)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                )
                Text(
                    text = if (item.kind == UserHomeLimitKind.Group) "Grupo de apps" else "Aplicación",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF667085),
                )
            }
            Text(
                text =
                    if (item.remainingMinutes == 0) {
                        "Límite alcanzado"
                    } else {
                        "Quedan ${item.remainingMinutes} min"
                    },
                style = MaterialTheme.typography.labelMedium,
                color = progressColor,
            )
        }
        LinearProgressIndicator(
            progress = { item.progress },
            modifier = Modifier.fillMaxWidth(),
            color = progressColor,
            trackColor = progressColor.copy(alpha = 0.14f),
        )
        Text(
            text = "${item.usedMinutes} de ${item.limitMinutes} min usados hoy",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF667085),
        )
    }
}

@Composable
private fun UserHomeItemIconStack(item: UserHomeLimitItem) {
    val icons = item.icons.ifEmpty { listOf(UserHomeAppIcon(item.title, null)) }
    Box(modifier = Modifier.size(width = 94.dp, height = 38.dp)) {
        icons.take(MaxItemIcons).forEachIndexed { index, icon ->
            Box(
                modifier =
                    Modifier
                        .offset(x = (index * ItemIconOffset).dp)
                        .zIndex(index.toFloat())
                        .size(36.dp)
                        .background(Color.White, CircleShape)
                        .border(2.dp, Color.White, CircleShape)
                        .padding(2.dp),
            ) {
                AppIcon(icon.name, icon.iconBase64, size = 28)
            }
        }
    }
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
                manifest?.let { "Versión ${it.versionName} (${it.versionCode}) lista para instalar." }
                    ?: "Hay una versión nueva lista para instalar."
            UpdatesStatus.Downloading -> "Descargando y verificando… ${downloadProgressPercent ?: 0}%"
            UpdatesStatus.ChecksumFailed -> "La descarga no pasó la verificación de seguridad."
            UpdatesStatus.DownloadFailed -> "No se pudo descargar la actualización."
            else -> ""
        }

private fun String.isHealthyLicenseState(): Boolean =
    this == "Activada" || this == "Por vencer" || this == "Periodo de gracia"

private enum class UserHomeSection {
    Protection,
    Limits,
}

internal val UserHomeHeaderTop = Color(0xFF172033)
private val UserHomeHeaderBottom = Color(0xFF263A5A)
private val ActiveProtectionColor = Color(0xFF72E6AA)
private val ReviewProtectionColor = Color(0xFFFFD166)
private const val ActiveStateLabel = "Activa"
private const val MaxQueueIcons = 7
private const val QueueIconOffset = 27
private const val MaxItemIcons = 4
private const val ItemIconOffset = 18
private const val MaxExpandedHomeLimits = 8
