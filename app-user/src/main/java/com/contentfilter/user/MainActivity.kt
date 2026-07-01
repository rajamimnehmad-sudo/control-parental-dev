package com.contentfilter.user

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.contentfilter.core.ui.ContentFilterTheme
import com.contentfilter.feature.activation.ActivationRoute
import com.contentfilter.feature.block.BlockScreen
import com.contentfilter.feature.onboarding.OnboardingScreen
import com.contentfilter.feature.requests.RequestsRoute
import com.contentfilter.feature.status.SystemStatusRoute
import com.contentfilter.feature.usage.UsageRoute
import com.contentfilter.user.updates.UpdatesRoute
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ContentFilterTheme {
                UserAppRoot(modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun UserAppRoot(modifier: Modifier = Modifier) {
    var destination by rememberSaveable { mutableStateOf(UserDestination.Onboarding) }
    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar {
                UserDestination.entries.forEach { item ->
                    NavigationBarItem(
                        selected = destination == item,
                        onClick = { destination = item },
                        icon = {},
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (destination) {
                UserDestination.Onboarding -> OnboardingScreen()
                UserDestination.Activation -> ActivationRoute()
                UserDestination.Status -> SystemStatusRoute()
                UserDestination.Requests -> RequestsRoute()
                UserDestination.Usage -> UsageRoute()
                UserDestination.Updates -> UpdatesRoute()
                UserDestination.Block -> BlockScreen(onRequestAccess = { destination = UserDestination.Requests })
            }
        }
    }
}

private enum class UserDestination(val label: String) {
    Onboarding("Inicio"),
    Activation("Activar"),
    Status("Estado"),
    Requests("Solicitudes"),
    Usage("Uso"),
    Updates("Actualizaciones"),
    Block("Bloqueo"),
}
