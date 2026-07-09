package com.contentfilter.admin.rules

import com.contentfilter.core.domain.model.DailyLimit
import com.contentfilter.core.domain.model.PolicyRule
import com.contentfilter.core.domain.model.RuleAction

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
    val searchEnginesAllowed: Boolean = false,
    val internetSaving: Boolean = false,
    val pendingInternetBlocked: Boolean? = null,
    val pendingSearchEnginesAllowed: Boolean? = null,
    val limitPackageName: String = "",
    val limitMinutes: String = "",
    val appSearchQuery: String = "",
    val groupName: String = "",
    val groupMinutes: String = "",
    val groupSelectedPackages: Set<String> = emptySet(),
    val editingGroupId: String? = null,
    val groupSaving: Boolean = false,
    val pendingAppGroupDeleteIds: Set<String> = emptySet(),
    val selectedAction: RuleAction = RuleAction.Block,
    val pendingAppAllowed: Map<String, Boolean> = emptyMap(),
    val pendingDeviceDeleteIds: Set<String> = emptySet(),
    val pairingUserName: String = "",
    val pairingCode: String = "",
    val pairingExpiresAt: String = "",
    val pairingLoading: Boolean = false,
    val offlineMode: Boolean = true,
    val message: String = "",
)

data class AppGroupUiState(
    val id: String,
    val name: String,
    val limitMinutes: Int,
    val resetLabel: String,
    val appPackages: List<String>,
)

data class UserDeviceUiState(
    val id: String,
    val name: String,
    val status: UserDeviceStatus,
    val lastSeenLabel: String,
    val appCount: Int,
    val protectionAlert: String? = null,
    val userLabel: String = "Usuario",
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
