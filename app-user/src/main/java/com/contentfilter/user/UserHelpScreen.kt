package com.contentfilter.user

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contentfilter.core.domain.help.HelpAction
import com.contentfilter.core.domain.help.HelpAudience
import com.contentfilter.core.domain.help.HelpContext
import com.contentfilter.core.ui.AppHelpAssistantScreen
import com.contentfilter.feature.status.SystemStatusViewModel

@Composable
internal fun UserHelpRoute(
    onBack: () -> Unit,
    onAction: (HelpAction) -> Unit,
    statusViewModel: SystemStatusViewModel = hiltViewModel(),
) {
    val state by statusViewModel.uiState.collectAsStateWithLifecycle()
    val vpnActive = state.vpnState == "Activa"
    val accessibilityActive = state.accessibilityState == "Activa"
    val uninstallProtectionActive = state.deviceAdminState == "Activa"
    AppHelpAssistantScreen(
        context =
            HelpContext(
                audience = HelpAudience.User,
                offline = state.syncState != "Activa",
                protectionNeedsAttention = !vpnActive || !accessibilityActive || !uninstallProtectionActive,
                vpnActive = vpnActive,
                accessibilityActive = accessibilityActive,
                uninstallProtectionActive = uninstallProtectionActive,
            ),
        onBack = onBack,
        onAction = onAction,
    )
}
