package com.contentfilter.feature.status

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contentfilter.core.ui.ProductCard
import com.contentfilter.core.ui.ProductLargeFeatureCard
import com.contentfilter.core.ui.ProductMint
import com.contentfilter.core.ui.ProductSky
import com.contentfilter.core.ui.ProductVisualPage
import com.contentfilter.core.ui.StatusBadge
import com.contentfilter.feature.vpn.service.VpnController

@Composable
fun SystemStatusRoute(
    modifier: Modifier = Modifier,
    viewModel: SystemStatusViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val isVpnRunning =
        VpnController
            .observeRunning(context)
            .collectAsStateWithLifecycle(initialValue = VpnController.isRunning(context))
    SystemStatusScreen(
        state = state.value.withVpnRunning(isVpnRunning.value),
        modifier = modifier,
    )
}

@Composable
fun SystemStatusScreen(
    state: SystemStatusUiState,
    modifier: Modifier = Modifier,
) {
    ProductVisualPage(
        modifier = modifier,
        title = state.title,
        subtitle = state.summary,
    ) {
        ProductLargeFeatureCard(
            title = "Protección activa",
            subtitle = "Estado del dispositivo, sincronización y servicios necesarios.",
            accent = ProductMint,
        )
        ProductCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusBadge(level = state.protectionLevel)
                if (state.communityName.isNotBlank()) {
                    StatusLine("Comunidad", state.communityName)
                }
                if (state.guideName.isNotBlank()) {
                    StatusLine("Guía", state.guideName)
                }
                StatusLine("VPN", state.vpnState)
                StatusLine("Accesibilidad", state.accessibilityState)
                StatusLine("Sincronización", state.syncState)
                StatusLine("Activación", state.activationState)
                StatusLine("Versión", state.appVersion)
            }
        }
        ProductLargeFeatureCard(
            title = "Apps primero",
            subtitle = "El bloqueo web sigue congelado temporalmente. El control activo principal es por apps.",
            accent = ProductSky,
        )
    }
}

@Composable
private fun StatusLine(
    label: String,
    value: String,
) {
    Text(text = "$label: $value", style = MaterialTheme.typography.bodyMedium)
}
