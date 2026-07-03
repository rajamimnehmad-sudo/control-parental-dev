package com.contentfilter.feature.usage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

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
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "Uso diario", style = MaterialTheme.typography.headlineSmall)
        Text(text = state.localDate, style = MaterialTheme.typography.bodyMedium)
        if (state.items.isEmpty()) {
            Text(text = "Todavía no hay uso registrado.", style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.items) { item ->
                    Text(
                        text = "${item.packageName}: ${item.usedMinutes} min",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}
