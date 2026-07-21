package com.contentfilter.admin.rules

import com.contentfilter.core.domain.model.DailyLimit
import com.contentfilter.core.domain.model.DeviceProtectionControl
import com.contentfilter.core.domain.model.PolicyRule
import com.contentfilter.core.domain.model.RuleAction
import com.contentfilter.core.domain.model.WebProtectionSemantics

data class RulesUiState(
    val rules: List<PolicyRule> = emptyList(),
    val limits: List<DailyLimit> = emptyList(),
    val userDevices: List<UserDeviceUiState> = emptyList(),
    val selectedDeviceId: String? = null,
    val appControls: List<AppControlUiState> = emptyList(),
    val appGroups: List<AppGroupUiState> = emptyList(),
    val appPackageName: String = "",
    val domainLimitDomain: String = "",
    val domainLimitMinutes: String = "",
    val allowDomain: String = "",
    val allowDomainMinutes: String = "",
    val internetBlocked: Boolean = false,
    val externalSearchResultsAllowed: Boolean = true,
    val safeSearchEnabled: Boolean = true,
    val dagEnabled: Boolean = false,
    val dagExtraKosherEnabled: Boolean = false,
    val dagEntitled: Boolean = false,
    val internetSavingDeviceIds: Set<String> = emptySet(),
    val pendingInternetBlockedByDevice: Map<String, Boolean> = emptyMap(),
    val pendingExternalSearchResultsAllowedByDevice: Map<String, Boolean> = emptyMap(),
    val pendingSafeSearchEnabledByDevice: Map<String, Boolean> = emptyMap(),
    val pendingDagEnabledByDevice: Map<String, Boolean> = emptyMap(),
    val pendingDagExtraKosherEnabledByDevice: Map<String, Boolean> = emptyMap(),
    val limitPackageName: String = "",
    val limitMinutes: String = "",
    val appSearchQuery: String = "",
    val groupName: String = "",
    val groupMinutes: String = "",
    val groupSelectedPackages: Set<String> = emptySet(),
    val editingGroupId: String? = null,
    val groupSavingDeviceIds: Set<String> = emptySet(),
    val pendingAppGroupDeleteIds: Set<String> = emptySet(),
    val selectedAction: RuleAction = RuleAction.Block,
    val pendingAppAllowedByDevice: Map<String, Map<String, Boolean>> = emptyMap(),
    val scheduleSavingKeys: Set<String> = emptySet(),
    val appRefreshDeviceIds: Set<String> = emptySet(),
    val pendingDeviceDeleteIds: Set<String> = emptySet(),
    val pairingUserName: String = "",
    val pairingCode: String = "",
    val pairingExpiresAt: String = "",
    val pairingLoading: Boolean = false,
    val relinkCode: String = "",
    val relinkExpiresAt: String = "",
    val relinkDeviceId: String? = null,
    val relinkLoadingDeviceIds: Set<String> = emptySet(),
    val archivedUsers: List<ArchivedUserUiState> = emptyList(),
    val archivedUsersLoading: Boolean = false,
    val restoreLoadingArchiveIds: Set<String> = emptySet(),
    val restorePairingCode: String = "",
    val restorePairingExpiresAt: String = "",
    val restorePairingUserName: String = "",
    val protectionControls: Map<String, DeviceProtectionControl> = emptyMap(),
    val protectionLoadingDeviceIds: Set<String> = emptySet(),
    val recoveryCode: String = "",
    val recoveryCodeDeviceId: String? = null,
    val recoveryKitRemainingByDevice: Map<String, Int> = emptyMap(),
    val devicesRefreshing: Boolean = false,
    val devicesLastRefreshedAtEpochMillis: Long? = null,
    val devicesRefreshError: String? = null,
    val offlineMode: Boolean = true,
    val message: String = "",
) {
    val internetSaving: Boolean
        get() = selectedDeviceId in internetSavingDeviceIds

    val pendingInternetBlocked: Boolean?
        get() = selectedDeviceId?.let(pendingInternetBlockedByDevice::get)

    val pendingExternalSearchResultsAllowed: Boolean?
        get() = selectedDeviceId?.let(pendingExternalSearchResultsAllowedByDevice::get)

    val pendingSafeSearchEnabled: Boolean?
        get() = selectedDeviceId?.let(pendingSafeSearchEnabledByDevice::get)

    val pendingDagEnabled: Boolean?
        get() = selectedDeviceId?.let(pendingDagEnabledByDevice::get)

    val pendingDagExtraKosherEnabled: Boolean?
        get() = selectedDeviceId?.let(pendingDagExtraKosherEnabledByDevice::get)

    val pendingAppAllowed: Map<String, Boolean>
        get() = selectedDeviceId?.let(pendingAppAllowedByDevice::get).orEmpty()

    val groupSaving: Boolean
        get() = selectedDeviceId in groupSavingDeviceIds

    val internetMode: InternetMode
        get() = InternetMode.fromBlocked(internetBlocked)

    val onlyResultsEnabled: Boolean
        get() = WebProtectionSemantics.onlyResultsEnabled(externalSearchResultsAllowed)

    val pendingOnlyResultsEnabled: Boolean?
        get() =
            pendingExternalSearchResultsAllowed?.let(WebProtectionSemantics::onlyResultsEnabled)

    val webLayersVisible: Boolean
        get() = internetMode == InternetMode.Open
}

enum class InternetMode {
    Open,
    Blocked,
    ;

    companion object {
        fun fromBlocked(blocked: Boolean): InternetMode = if (blocked) Blocked else Open
    }
}

internal data class WebPanelPresentation(
    val headline: String,
    val activeLayers: List<String>,
    val showLayers: Boolean,
)

internal fun RulesUiState.webPanelPresentation(): WebPanelPresentation {
    if (!webLayersVisible) {
        return WebPanelPresentation(
            headline = "Internet bloqueado",
            activeLayers = emptyList(),
            showLayers = false,
        )
    }
    val layers =
        buildList {
            if (safeSearchEnabled) add("SafeSearch")
            if (onlyResultsEnabled) add("Solo resultados")
            if (dagEnabled) add("DAG")
            if (dagExtraKosherEnabled) add("Extra Kosher")
        }
    return WebPanelPresentation(
        headline = if (layers.isEmpty()) "Internet totalmente abierto" else "Internet abierto con protecciones",
        activeLayers = layers,
        showLayers = true,
    )
}

data class AppGroupUiState(
    val id: String,
    val name: String,
    val limitMinutes: Int,
    val resetLabel: String,
    val appPackages: List<String>,
)

data class UserDeviceUiState(
    val id: String,
    val accountId: String,
    val name: String,
    val status: UserDeviceStatus,
    val lastSeenLabel: String,
    val appCount: Int,
    val protectionAlert: String? = null,
    val possibleUninstall: Boolean = false,
    val protectionComplete: Boolean = false,
    val confirmedProtectionFailure: Boolean = false,
    val protectionVerificationPending: Boolean = false,
    val vpnState: String = "Desconocida",
    val accessibilityState: String = "Desconocida",
    val deviceAdminState: String = "Desconocida",
    val userLabel: String = "Usuario",
)

data class ArchivedUserUiState(
    val archiveId: String,
    val deviceId: String,
    val name: String,
    val archivedAtLabel: String,
    val canRestore: Boolean,
)

enum class UserDeviceStatus {
    Active,
    Unprotected,
    Inactive,
    Unknown,
}

data class AppControlUiState(
    val appName: String,
    val packageName: String,
    val versionName: String?,
    val isSystemApp: Boolean,
    val iconBase64: String?,
    val deviceName: String,
    val allowed: Boolean,
    val confirmedAllowed: Boolean,
    val dailyLimitMinutes: Int?,
    val extraTimeRemainingMinutes: Int? = null,
    val groupName: String? = null,
    val groupLimitMinutes: Int? = null,
    val isUpdating: Boolean = false,
)
