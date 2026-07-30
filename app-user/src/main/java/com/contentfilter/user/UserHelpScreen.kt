package com.contentfilter.user

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contentfilter.core.domain.help.HelpAction
import com.contentfilter.core.domain.help.HelpAudience
import com.contentfilter.core.domain.help.HelpContext
import com.contentfilter.core.ui.AppHelpAssistantScreen
import com.contentfilter.feature.status.SystemStatusViewModel
import com.contentfilter.user.browser.ProtectedBrowserLauncher
import com.contentfilter.user.help.UserHelpReportViewModel

@Composable
internal fun UserHelpRoute(
    onBack: () -> Unit,
    onAction: (HelpAction) -> Unit,
    statusViewModel: SystemStatusViewModel = hiltViewModel(),
    reportViewModel: UserHelpReportViewModel = hiltViewModel(),
) {
    val androidContext = LocalContext.current
    val state by statusViewModel.uiState.collectAsStateWithLifecycle()
    val vpnActive = state.vpnState == "Activa"
    val accessibilityActive = state.accessibilityState == "Activa"
    val uninstallProtectionActive = state.deviceAdminState == "Activa"
    val helpContext =
        HelpContext(
            audience = HelpAudience.User,
            offline = state.syncState != "Activa",
            protectionNeedsAttention = !vpnActive || !accessibilityActive || !uninstallProtectionActive,
            vpnActive = vpnActive,
            accessibilityActive = accessibilityActive,
            uninstallProtectionActive = uninstallProtectionActive,
            dagInstalled =
                BuildConfig.DAG_BROWSER_V3_BRIDGE_AVAILABLE &&
                    ProtectedBrowserLauncher.isInstalled(androidContext),
        )
    AppHelpAssistantScreen(
        context = helpContext,
        onBack = onBack,
        onAction = onAction,
        onAutomaticReport = { reportViewModel.report(it, helpContext) },
    )
}
