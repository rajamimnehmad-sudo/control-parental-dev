package com.contentfilter.admin

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
import com.contentfilter.admin.auth.AdminAuthRoute
import com.contentfilter.admin.dashboard.DashboardRoute
import com.contentfilter.admin.devices.DevicesRoute
import com.contentfilter.admin.requests.AdminRequestsRoute
import com.contentfilter.admin.rules.RulesRoute
import com.contentfilter.admin.updates.AdminUpdatesRoute
import com.contentfilter.core.ui.ContentFilterTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ContentFilterTheme {
                AdminAppRoot(modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun AdminAppRoot(modifier: Modifier = Modifier) {
    var destination by rememberSaveable { mutableStateOf(AdminDestination.Dashboard) }
    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar {
                AdminDestination.entries.forEach { item ->
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
                AdminDestination.Login -> AdminAuthRoute()
                AdminDestination.Dashboard -> DashboardRoute()
                AdminDestination.Devices -> DevicesRoute()
                AdminDestination.Requests -> AdminRequestsRoute()
                AdminDestination.Rules -> RulesRoute()
                AdminDestination.Updates -> AdminUpdatesRoute()
            }
        }
    }
}

private enum class AdminDestination(val label: String) {
    Login("Login"),
    Dashboard("Panel"),
    Devices("Dispositivos"),
    Requests("Solicitudes"),
    Rules("Reglas"),
    Updates("Actualizaciones"),
}
