package com.contentfilter.feature.activation

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contentfilter.core.ui.PremiumFeedbackBanner as FeedbackBanner
import com.contentfilter.core.ui.ProgressActionButton
import com.contentfilter.core.ui.ProductCard
import com.contentfilter.core.ui.ProductLargeFeatureCard
import com.contentfilter.core.ui.ProductTeal
import com.contentfilter.core.ui.ProductVisualPage

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
    ProductVisualPage(
        modifier = modifier,
        title = "Enlazar dispositivo",
        subtitle = "Ingresá el token que te dio tu administrador",
    ) {
        ProductLargeFeatureCard(
            title = "Dispositivo protegido",
            subtitle = "Este teléfono se conecta a tu comunidad con un token temporal.",
            accent = ProductTeal,
        )
        if (state.activated) {
            FeedbackBanner(text = state.message)
        } else {
            if (notice.isNotBlank()) {
                FeedbackBanner(text = notice, isError = true)
            }
            ProductCard {
                OutlinedTextField(
                    value = state.activationCode,
                    onValueChange = onActivationCodeChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Token del administrador") },
                    singleLine = true,
                )
                ProgressActionButton(
                    onClick = onActivate,
                    enabled = !state.isLoading,
                    loading = state.isLoading,
                    loadingText = "Enlazando...",
                    successText = "Dispositivo enlazado",
                    text = "Enlazar",
                )
            }
            FeedbackBanner(text = state.message, isError = state.message.startsWith("No se pudo"))
        }
    }
}
