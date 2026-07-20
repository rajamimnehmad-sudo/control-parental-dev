package com.contentfilter.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.contentfilter.core.domain.model.LicenseState
import com.contentfilter.core.domain.model.allowsProtection
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@Composable
internal fun VisualPage(
    title: String,
    subtitle: String,
    headerAction: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(AppBackground)
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        PageHeader(title = title, subtitle = subtitle, action = headerAction)
        content()
    }
}

@Composable
internal fun PageHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        onBack?.let {
            IconButton(onClick = it) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Ink)
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = Ink)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MutedInk,
            )
        }
        action?.invoke()
    }
}

@Composable
internal fun LargeFeatureCard(
    title: String,
    subtitle: String,
    accent: Color,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(72.dp)
                        .background(accent.copy(alpha = 0.16f), RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center,
            ) {
                MiniIllustration(accent = accent)
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = Ink)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MutedInk)
            }
        }
    }
}

@Composable
internal fun FeatureTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(54.dp)
                        .background(accent.copy(alpha = 0.18f), RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = accent, modifier = Modifier.size(28.dp))
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Ink)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MutedInk)
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
internal fun StatCard(
    value: String,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .background(Color.White, RoundedCornerShape(24.dp))
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(34.dp)
                    .background(accent.copy(alpha = 0.16f), CircleShape),
        )
        Text(value, style = MaterialTheme.typography.titleLarge, color = Ink)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MutedInk)
    }
}

@Composable
private fun MiniIllustration(accent: Color) {
    Box(modifier = Modifier.size(46.dp)) {
        Box(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .size(38.dp)
                    .background(Color.White, RoundedCornerShape(12.dp)),
        )
        Box(
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .size(18.dp)
                    .background(accent, CircleShape),
        )
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .width(28.dp)
                    .height(10.dp)
                    .background(accent.copy(alpha = 0.65f), CircleShape),
        )
    }
}

@Composable
internal fun NavGlyph(
    icon: ImageVector,
    selected: Boolean,
) {
    Box(
        modifier =
            Modifier
                .size(if (selected) 34.dp else 30.dp)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    CircleShape,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) Color.White else MutedInk,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
internal fun SectionContainer(
    title: String,
    subtitle: String,
    onBack: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(AppBackground),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PageHeader(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 10.dp, top = 18.dp, end = 18.dp),
            title = title,
            subtitle = subtitle,
            onBack = onBack,
        )
        Box(modifier = Modifier.weight(1f)) {
            content()
        }
    }
}

internal val AppBackground = Color(0xFFF2F8F7)
internal val Ink = Color(0xFF162235)
internal val MutedInk = Color(0xFF68758A)
internal val Teal = Color(0xFF13BFAE)
internal val Sky = Color(0xFF2C9AF4)
internal val Sun = Color(0xFFFFC849)
internal val Mint = Color(0xFF55E1B8)
internal val Violet = Color(0xFF6C63FF)

internal fun licenseSummary(
    state: LicenseState,
    expiresAtEpochMillis: Long?,
): String {
    val daysRemaining =
        expiresAtEpochMillis?.let { expiresAt ->
            ChronoUnit.DAYS.between(
                Instant.now().atZone(ArgentinaZone).toLocalDate(),
                Instant.ofEpochMilli(expiresAt).atZone(ArgentinaZone).toLocalDate(),
            ).coerceAtLeast(0)
        }
    val stateLabel =
        when (state) {
            LicenseState.Active -> "Licencia activa"
            LicenseState.Scheduled -> "Licencia programada"
            LicenseState.ExpiringSoon -> "Licencia por vencer"
            LicenseState.Expired -> "Licencia vencida"
            LicenseState.GracePeriod -> "Licencia en período de gracia"
            LicenseState.Suspended -> "Licencia suspendida"
            LicenseState.PendingActivation -> "Licencia pendiente"
        }
    return if (daysRemaining != null && state in setOf(LicenseState.Active, LicenseState.ExpiringSoon, LicenseState.GracePeriod)) {
        val remainingLabel =
            if (daysRemaining == 1L) {
                "1 día restante"
            } else {
                "$daysRemaining días restantes"
            }
        "$stateLabel · $remainingLabel"
    } else {
        stateLabel
    }
}

internal fun syncStatusLabel(
    offlineMode: Boolean,
    syncState: String,
): String {
    if (offlineMode) return "sin conexión"
    return when (syncState.lowercase()) {
        "enabled", "active" -> "sincronización activa"
        "warning" -> "sincronización con advertencias"
        "disabled" -> "sincronización desactivada"
        else -> "sincronización pendiente"
    }
}

internal fun formatArgentinaDate(epochMillis: Long): String {
    val date = Instant.ofEpochMilli(epochMillis).atZone(ArgentinaZone).toLocalDate()
    return "%02d/%02d/%04d".format(date.dayOfMonth, date.monthValue, date.year)
}

internal fun licenseEffectText(state: LicenseState): String =
    when (state) {
        LicenseState.Active -> "La protección y las configuraciones están habilitadas."
        LicenseState.ExpiringSoon -> "La protección continúa activa; conviene renovar antes del vencimiento."
        LicenseState.GracePeriod -> "La protección continúa durante el período de gracia."
        LicenseState.Scheduled -> "La licencia todavía no comenzó; la protección permanece suspendida."
        LicenseState.Expired -> "La licencia venció y la protección está suspendida."
        LicenseState.Suspended -> "La licencia fue suspendida y no permite aplicar protección."
        LicenseState.PendingActivation -> "La licencia aún no está activa; la protección permanece suspendida."
    }

internal val LicenseState.homeColor: Color
    get() =
        when (this) {
            LicenseState.Active -> Color(0xFF89E4B3)
            LicenseState.ExpiringSoon, LicenseState.GracePeriod, LicenseState.Scheduled -> Color(0xFFFFD166)
            LicenseState.Expired, LicenseState.Suspended, LicenseState.PendingActivation -> Color(0xFFFF8A8A)
        }

internal val LicenseState.accountColor: Color
    get() =
        when (this) {
            LicenseState.Active -> Color(0xFF17895D)
            LicenseState.ExpiringSoon, LicenseState.GracePeriod, LicenseState.Scheduled -> Color(0xFF9A6700)
            LicenseState.Expired, LicenseState.Suspended, LicenseState.PendingActivation -> Color(0xFFBA1A1A)
        }

internal enum class ProtectionCardState(
    val summary: String,
    val color: Color,
) {
    Healthy("Todos los usuarios están protegidos", Color(0xFF17895D)),
    Critical("ALERTA MÁXIMA · posible desinstalación", Color(0xFFB00020)),
    NeedsAttention("Hay usuarios que requieren atención", Color(0xFFBA1A1A)),
    PendingVerification("Hay estados pendientes de verificar", Color(0xFF9A6700)),
    LicenseBlocked("Protección suspendida por licencia", Color(0xFFBA1A1A)),
    NoUsers("Todavía no hay usuarios vinculados", Color(0xFF9A6700)),
}

internal fun protectionCardState(
    licenseState: LicenseState,
    userCount: Int,
    affectedCount: Int,
    pendingCount: Int,
    criticalCount: Int = 0,
): ProtectionCardState =
    when {
        !licenseState.allowsProtection() -> ProtectionCardState.LicenseBlocked
        criticalCount > 0 -> ProtectionCardState.Critical
        affectedCount > 0 -> ProtectionCardState.NeedsAttention
        userCount == 0 -> ProtectionCardState.NoUsers
        pendingCount > 0 -> ProtectionCardState.PendingVerification
        else -> ProtectionCardState.Healthy
    }

internal val ArgentinaZone: ZoneId = ZoneId.of("America/Argentina/Buenos_Aires")
