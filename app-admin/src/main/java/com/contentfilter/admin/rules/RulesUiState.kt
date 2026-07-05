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
    val appPackageName: String = "",
    val domain: String = "",
    val domainLimitDomain: String = "",
    val domainLimitMinutes: String = "",
    val allowDomain: String = "",
    val allowDomainMinutes: String = "",
    val internetBlocked: Boolean = false,
    val googleSearchAllowed: Boolean = false,
    val pendingInternetBlocked: Boolean? = null,
    val pendingGoogleSearchAllowed: Boolean? = null,
    val limitPackageName: String = "",
    val limitMinutes: String = "",
    val appSearchQuery: String = "",
    val selectedAction: RuleAction = RuleAction.Block,
    val pendingAppAllowed: Map<String, Boolean> = emptyMap(),
    val pendingDeviceDeleteIds: Set<String> = emptySet(),
    val offlineMode: Boolean = true,
    val message: String = "",
)

data class UserDeviceUiState(
    val id: String,
    val name: String,
    val active: Boolean,
    val lastSeenLabel: String,
    val appCount: Int,
)

data class AppControlUiState(
    val appName: String,
    val packageName: String,
    val versionName: String?,
    val isSystemApp: Boolean,
    val iconBase64: String?,
    val deviceName: String,
    val allowed: Boolean,
    val dailyLimitMinutes: Int?,
    val isUpdating: Boolean = false,
)
