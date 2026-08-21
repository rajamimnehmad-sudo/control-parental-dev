package com.contentfilter.feature.activation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contentfilter.core.ui.GloshColors
import com.contentfilter.core.ui.GloshSpacing
import com.contentfilter.core.ui.GloshWordmark
import com.contentfilter.core.ui.ProductCard
import com.contentfilter.core.ui.ProgressActionButton
import com.contentfilter.core.ui.PremiumFeedbackBanner as FeedbackBanner

@Composable
fun ActivationRoute(
    modifier: Modifier = Modifier,
    notice: String = "",
    viewModel: ActivationViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle()
    ActivationScreen(
        state = state.value,
        onActivationCodeChanged = viewModel::onActivationCodeChanged,
        onActivate = viewModel::activate,
        notice = notice,
        modifier = modifier,
    )
}

@Composable
fun ActivationScreen(
    state: ActivationUiState,
    onActivationCodeChanged: (String) -> Unit,
    onActivate: () -> Unit,
    notice: String = "",
    modifier: Modifier = Modifier,
) {
    val bannerText = notice.ifBlank { state.message }
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(GloshColors.Bone)
                .padding(horizontal = GloshSpacing.PageHorizontal, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        GloshWordmark()
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Activá Glosh", style = MaterialTheme.typography.headlineMedium, color = GloshColors.Graphite)
            Text(
                "Ingresá el token que te dio tu administrador para vincular este teléfono.",
                style = MaterialTheme.typography.bodyLarge,
                color = GloshColors.Muted,
            )
        }
        if (bannerText.isNotBlank()) {
            FeedbackBanner(
                text = bannerText,
                isError = notice.isNotBlank() || bannerText.startsWith("No se pudo"),
            )
        }
        ProductCard {
            Text("Token de activación", style = MaterialTheme.typography.titleMedium, color = GloshColors.Graphite)
            Text(
                "Es temporal y sirve únicamente para vincular este dispositivo.",
                style = MaterialTheme.typography.bodySmall,
                color = GloshColors.Muted,
            )
            OutlinedTextField(
                value = state.activationCode,
                onValueChange = onActivationCodeChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Token") },
                singleLine = true,
            )
            ProgressActionButton(
                onClick = onActivate,
                enabled = !state.isLoading,
                loading = state.isLoading,
                loadingText = "Vinculando…",
                successText = "Dispositivo vinculado",
                text = "Vincular dispositivo",
            )
        }
    }
}
