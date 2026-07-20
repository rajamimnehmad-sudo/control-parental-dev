package com.contentfilter.admin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contentfilter.admin.dashboard.DashboardViewModel
import com.contentfilter.core.domain.help.HelpAction
import com.contentfilter.core.domain.help.HelpAudience
import com.contentfilter.core.domain.help.HelpContext
import com.contentfilter.core.ui.AppHelpAssistantScreen

@Composable
internal fun AdminHelpRoute(
    onBack: () -> Unit,
    onAction: (HelpAction) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val users = state.protectedUsers
    AppHelpAssistantScreen(
        context =
            HelpContext(
                audience = HelpAudience.Admin,
                offline = state.syncState != "Enabled",
                possibleUninstall = users.any { it.possibleUninstall },
                protectionNeedsAttention = users.any { it.hasConfirmedProblem || it.requiresVerification },
                vpnActive = users.none { it.vpnProblem },
                accessibilityActive = users.none { it.accessibilityProblem },
                uninstallProtectionActive = users.none { it.deviceAdminProblem },
            ),
        onBack = onBack,
        onAction = onAction,
    )
}
