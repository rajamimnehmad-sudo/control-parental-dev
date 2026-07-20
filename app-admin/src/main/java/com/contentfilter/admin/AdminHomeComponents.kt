package com.contentfilter.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contentfilter.admin.announcements.AdminAnnouncementsViewModel
import com.contentfilter.admin.dashboard.DashboardViewModel
import com.contentfilter.admin.dashboard.ProtectedUserHealthUiState
import com.contentfilter.core.domain.model.LicenseState
import com.contentfilter.core.domain.model.allowsProtection
import com.contentfilter.core.ui.ProductGlyph
import com.contentfilter.core.ui.ProductIcon

@Composable
internal fun HomeTab(
    onCreateUser: () -> Unit,
    onRequests: () -> Unit,
    onProtectionStatus: () -> Unit,
    onAnnouncements: () -> Unit,
) {
    val dashboardViewModel: DashboardViewModel = hiltViewModel()
    val dashboardState by dashboardViewModel.uiState.collectAsStateWithLifecycle()
    val announcementsViewModel: AdminAnnouncementsViewModel = hiltViewModel()
    val announcementsState by announcementsViewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { announcementsViewModel.refresh() }
    Column(modifier = Modifier.fillMaxSize().background(AppBackground)) {
        AdminHomeHeader(
            administratorName = dashboardState.guideName.ifBlank { "Administrador" },
            communityName = dashboardState.communityName,
            licenseState = dashboardState.licenseState,
            licenseExpiresAtEpochMillis = dashboardState.licenseExpiresAtEpochMillis,
            announcementCount = announcementsState.unreadCount,
            onAnnouncements = onAnnouncements,
        )
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ProtectionSummaryCard(
                users = dashboardState.protectedUsers,
                licenseState = dashboardState.licenseState,
                onClick = onProtectionStatus,
            )
            FeatureTile(
                icon = Icons.Filled.Person,
                title = "Agregar usuario",
                subtitle = "Crear y vincular un nuevo usuario",
                accent = Sky,
                onClick = onCreateUser,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatCard(
                    value = dashboardState.activeUserCount.toString(),
                    label = "usuarios activos",
                    accent = Teal,
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    value = dashboardState.pendingRequests.toString(),
                    label = "solicitudes pendientes",
                    accent = Sun,
                    modifier = Modifier.weight(1f).clickable(onClick = onRequests),
                )
            }
        }
    }
}

@Composable
private fun AdminHomeHeader(
    administratorName: String,
    communityName: String,
    licenseState: LicenseState,
    licenseExpiresAtEpochMillis: Long?,
    announcementCount: Int,
    onAnnouncements: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(listOf(Color(0xFF172033), Color(0xFF263A5A))),
                    shape = RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp),
                )
                .statusBarsPadding()
                .padding(start = 20.dp, top = 18.dp, end = 14.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Hola, $administratorName (ADM)",
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
            Box {
                IconButton(
                    onClick = onAnnouncements,
                    modifier = Modifier.semantics { contentDescription = "Abrir avisos de Superweb" },
                ) {
                    ProductGlyph(
                        icon = ProductIcon.Bell,
                        color = Color.White,
                        modifier = Modifier.size(25.dp),
                    )
                }
                if (announcementCount > 0) {
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .size(20.dp)
                                .background(Color(0xFFFF5C65), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = announcementCount.coerceAtMost(99).toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                        )
                    }
                }
            }
        }
        Text(
            modifier =
                Modifier
                    .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            text = licenseSummary(licenseState, licenseExpiresAtEpochMillis),
            style = MaterialTheme.typography.labelMedium,
            color = licenseState.homeColor,
        )
    }
}

@Composable
private fun ProtectionSummaryCard(
    users: List<ProtectedUserHealthUiState>,
    licenseState: LicenseState,
    onClick: () -> Unit,
) {
    val affectedCount = users.count(ProtectedUserHealthUiState::hasConfirmedProblem)
    val pendingCount = users.count(ProtectedUserHealthUiState::requiresVerification)
    val criticalCount = users.count(ProtectedUserHealthUiState::possibleUninstall)
    val cardState = protectionCardState(licenseState, users.size, affectedCount, pendingCount, criticalCount)
    val accent = cardState.color
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.10f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProtectionShield(
                count = if (criticalCount > 0) criticalCount else affectedCount,
                requiresAttention = affectedCount > 0 || pendingCount > 0 || !licenseState.allowsProtection(),
                color = accent,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Estado de protección de usuarios", style = MaterialTheme.typography.titleMedium, color = Ink)
                Text(
                    text = cardState.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = accent,
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = accent)
        }
    }
}

@Composable
private fun ProtectionShield(
    count: Int,
    requiresAttention: Boolean,
    color: Color,
) {
    Box(
        modifier = Modifier.size(52.dp).background(color.copy(alpha = 0.14f), RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
    ) {
        ProductGlyph(
            icon = if (requiresAttention) ProductIcon.ShieldAlert else ProductIcon.ShieldCheck,
            color = color,
            modifier = Modifier.size(32.dp),
        )
        if (count > 0) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(19.dp)
                        .background(color, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    count.coerceAtMost(99).toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
internal fun ProtectionStatusRoute(
    onOpenUser: (String) -> Unit,
    onOpenAlerts: () -> Unit,
) {
    val viewModel: DashboardViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val affectedUsers = state.usersRequiringAttention
    val pendingUsers = state.usersPendingVerification
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!state.licenseState.allowsProtection()) {
            LargeFeatureCard(
                title = "Protección suspendida por licencia",
                subtitle = "Revisa el estado de la licencia en Cuenta para recuperar la protección.",
                accent = MaterialTheme.colorScheme.error,
            )
        } else if (affectedUsers.isEmpty() && pendingUsers.isEmpty() && state.protectedUsers.isNotEmpty()) {
            LargeFeatureCard(
                title = "Todos protegidos",
                subtitle = "VPN, Accesibilidad y Administrador del dispositivo están activos.",
                accent = Color(0xFF17895D),
            )
        } else if (affectedUsers.isEmpty() && pendingUsers.isEmpty()) {
            LargeFeatureCard(
                title = "Sin usuarios vinculados",
                subtitle = "Agrega un usuario para comenzar a supervisar su protección.",
                accent = Sun,
            )
        }
        if (affectedUsers.isNotEmpty()) {
            affectedUsers.forEach { user ->
                val critical = user.possibleUninstall
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onOpenUser(user.id) },
                    shape = RoundedCornerShape(18.dp),
                    colors =
                        CardDefaults.cardColors(
                            containerColor =
                                if (critical) Color(0xFFFFDAD6) else MaterialTheme.colorScheme.errorContainer,
                        ),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (critical) {
                            Text(
                                "ALERTA MÁXIMA",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color(0xFFB00020),
                            )
                            Text("App Usuario posiblemente desinstalada", style = MaterialTheme.typography.titleMedium)
                            Text(user.name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "No se reinstala sola. Abrí el usuario para ver los pasos de recuperación.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        } else {
                            Text(user.name, style = MaterialTheme.typography.titleMedium)
                        }
                        if (user.vpnProblem) Text("VPN desactivada")
                        if (user.accessibilityProblem) Text("Accesibilidad desactivada")
                        if (user.deviceAdminProblem) Text("Administrador del dispositivo desactivado")
                    }
                }
            }
        }
        if (pendingUsers.isNotEmpty()) {
            Text("Pendientes de verificación", style = MaterialTheme.typography.titleMedium, color = Ink)
            pendingUsers.forEach { user ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onOpenUser(user.id) },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Sun.copy(alpha = 0.16f)),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(user.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (user.isRecentlySeen) {
                                "Esperando confirmar todos los componentes"
                            } else {
                                "Sin conexión reciente"
                            },
                        )
                    }
                }
            }
        }
        OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onOpenAlerts) {
            Text("Ver alertas recientes")
        }
    }
}
