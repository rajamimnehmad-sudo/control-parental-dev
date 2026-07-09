package com.contentfilter.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

enum class ActionButtonTone {
    Primary,
    Destructive,
}

@Composable
fun ProgressActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
    loading: Boolean = false,
    loadingText: String = "Guardando...",
    successText: String = "Listo",
    error: Boolean = false,
    errorText: String = "No se pudo",
    tone: ActionButtonTone = ActionButtonTone.Primary,
) {
    var visualState by remember { mutableStateOf(ActionVisualState.Idle) }
    var hadLoading by remember { mutableStateOf(false) }

    LaunchedEffect(loading, error) {
        if (loading) {
            hadLoading = true
            visualState = ActionVisualState.Loading
            return@LaunchedEffect
        }
        if (hadLoading) {
            visualState =
                if (error) {
                    ActionVisualState.Error
                } else {
                    ActionVisualState.Success
                }
            delay(1_200)
            visualState = ActionVisualState.Idle
            hadLoading = false
        }
    }

    val colors =
        when (visualState) {
            ActionVisualState.Success ->
                ButtonDefaults.buttonColors(
                    containerColor = SuccessGreen,
                    contentColor = Color.White,
                    disabledContainerColor = SuccessGreen,
                    disabledContentColor = Color.White,
                )
            ActionVisualState.Error ->
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                    disabledContainerColor = MaterialTheme.colorScheme.error,
                    disabledContentColor = MaterialTheme.colorScheme.onError,
                )
            else ->
                when (tone) {
                    ActionButtonTone.Primary -> ButtonDefaults.buttonColors()
                    ActionButtonTone.Destructive ->
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                            disabledContainerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.55f),
                            disabledContentColor = MaterialTheme.colorScheme.onError,
                        )
                }
        }
    val buttonText =
        when (visualState) {
            ActionVisualState.Loading -> loadingText
            ActionVisualState.Success -> successText
            ActionVisualState.Error -> errorText
            ActionVisualState.Idle -> text
        }

    Button(
        modifier = modifier,
        onClick = onClick,
        enabled = enabled && !loading && visualState != ActionVisualState.Loading,
        colors = colors,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (visualState == ActionVisualState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = Color.White,
                )
            }
            Text(buttonText)
        }
    }
}

private enum class ActionVisualState {
    Idle,
    Loading,
    Success,
    Error,
}

private val SuccessGreen = Color(0xFF00A650)
