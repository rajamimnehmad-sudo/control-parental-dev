package com.contentfilter.feature.usage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contentfilter.core.ui.ProductCard
import com.contentfilter.core.ui.ProductLargeFeatureCard
import com.contentfilter.core.ui.ProductMint
import com.contentfilter.core.ui.ProductVisualPage

@Composable
fun UsageRoute(
    modifier: Modifier = Modifier,
    viewModel: UsageViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle()
    UsageScreen(state = state.value, modifier = modifier)
}

@Composable
fun UsageScreen(
    state: UsageUiState,
    modifier: Modifier = Modifier,
) {
    ProductVisualPage(
        modifier = modifier,
        title = "Uso diario",
        subtitle = state.localDate,
    ) {
        ProductLargeFeatureCard(
            title = "Tiempo de hoy",
            subtitle = "Registro local de uso de apps para aplicar límites diarios.",
            accent = ProductMint,
        )
        if (state.items.isEmpty()) {
            Text(text = "Todavía no hay uso registrado.", style = MaterialTheme.typography.bodyMedium)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.items.forEach { item ->
                    ProductCard {
                    Text(
                        text = "${item.packageName}: ${item.usedMinutes} min",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    }
                }
            }
        }
    }
}
