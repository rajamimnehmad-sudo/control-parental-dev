package com.contentfilter.admin.rules

import com.contentfilter.core.domain.model.AppGroup
import com.contentfilter.core.domain.model.ComponentState
import com.contentfilter.core.domain.model.DailyLimit
import com.contentfilter.core.domain.model.Device
import com.contentfilter.core.domain.model.DeviceProtectionAlert
import com.contentfilter.core.domain.model.ExtraTimeGrant
import com.contentfilter.core.domain.model.InstalledApp
import com.contentfilter.core.domain.model.PolicyLevel
import com.contentfilter.core.domain.model.PolicyRule
import com.contentfilter.core.domain.model.PolicyTargetType
import com.contentfilter.core.domain.model.RuleAction
import com.contentfilter.core.domain.model.RuleScope
import com.contentfilter.core.domain.model.SearchEngineCatalog
import com.contentfilter.core.domain.model.WebNavigationPolicy
import com.contentfilter.core.domain.model.dagEnabled
import com.contentfilter.core.domain.model.dagExtraKosherEnabled
import com.contentfilter.core.domain.model.externalSearchResultsAllowed
import com.contentfilter.core.domain.model.safeSearchEnabled
import com.contentfilter.core.domain.model.webNavigationBlocked
import java.time.Duration
import java.time.Instant
import java.util.UUID

internal fun normalizeTarget(
    scope: RuleScope,
    rawTarget: String,
): String? {
    val trimmed = rawTarget.trim()
    return when (scope) {
        RuleScope.App -> trimmed.takeIf { PackageNameRegex.matches(it) }
        RuleScope.Domain -> trimmed.toDomainOrNull()
        else -> null
    }
}

internal fun normalizeLimitTarget(
    targetType: PolicyTargetType,
    rawTarget: String,
): String? =
    when (targetType) {
        PolicyTargetType.App -> normalizeTarget(RuleScope.App, rawTarget)
        PolicyTargetType.Domain -> normalizeTarget(RuleScope.Domain, rawTarget)
        else -> null
    }

internal fun List<InstalledApp>.toAppControls(
    rules: List<PolicyRule>,
    limits: List<DailyLimit>,
    grants: List<ExtraTimeGrant>,
    appGroups: List<AppGroup>,
    nowEpochMillis: Long,
    devices: List<Device>,
    pendingAllowed: Map<String, Boolean>,
): List<AppControlUiState> {
    val devicesById = devices.associateBy { it.id }
    val groupsByPackage =
        appGroups
            .filter { it.enabled }
            .flatMap { group ->
                group.apps
                    .filter { it.enabled }
                    .map { app -> app.packageName to group }
            }.toMap()
    return preferAppsWithIcons()
        .distinctBy { it.packageName }
        .map { app ->
            val effectiveRule = rules.effectiveAppRule(app.packageName)
            val appGroup = groupsByPackage[app.packageName]
            val extraTimeRemainingMinutes = grants.activeExtraTimeRemainingMinutes(app.packageName, nowEpochMillis)
            val limit =
                limits.firstOrNull {
                    it.enabled &&
                        it.targetType == PolicyTargetType.App &&
                        it.target == app.packageName
                }
            val confirmedAllowed =
                extraTimeRemainingMinutes != null ||
                    limit != null ||
                    effectiveRule?.action != RuleAction.Block
            AppControlUiState(
                appName = app.appName,
                packageName = app.packageName,
                versionName = app.versionName,
                isSystemApp = app.isSystemApp,
                iconBase64 = app.iconBase64,
                deviceName = devicesById[app.deviceId]?.displayName ?: "Usuario",
                allowed = pendingAllowed[app.packageName] ?: confirmedAllowed,
                confirmedAllowed = confirmedAllowed,
                dailyLimitMinutes = limit?.limitMinutes,
                extraTimeRemainingMinutes = extraTimeRemainingMinutes,
                groupName = appGroup?.name,
                groupLimitMinutes = appGroup?.limitMinutes,
                isUpdating = pendingAllowed.containsKey(app.packageName),
            )
        }
        .sortedWith(
            compareBy<AppControlUiState> { it.accessSortOrder }
                .thenBy { it.appName.lowercase() }
                .thenBy { it.packageName },
        )
}

internal fun List<InstalledApp>.forSelectedUserDevice(
    selectedDeviceId: String,
    devices: List<Device>,
): List<InstalledApp> {
    val userDeviceIds = devices.userDeviceIds()
    val directApps = filter { it.deviceId == selectedDeviceId }
    if (userDeviceIds.size != 1 || selectedDeviceId !in userDeviceIds) return directApps
    val orphanApps = filter { it.deviceId !in userDeviceIds }
    return (directApps + orphanApps).preferAppsWithIcons().distinctBy { it.packageName }
}

internal fun List<InstalledApp>.preferAppsWithIcons(): List<InstalledApp> =
    sortedWith(
        compareByDescending<InstalledApp> { it.iconBase64.hasVisibleIcon() }
            .thenByDescending { it.updatedAtEpochMillis },
    )

private fun String?.hasVisibleIcon(): Boolean = !isNullOrBlank()

internal fun List<PolicyRule>.internetBlocked(): Boolean = webNavigationBlocked()

internal fun List<PolicyRule>.externalSearchResultsAllowedForWeb(): Boolean = externalSearchResultsAllowed()

internal fun List<PolicyRule>.safeSearchEnabledForWeb(): Boolean = safeSearchEnabled()

internal fun List<PolicyRule>.internetBlockRules(): List<PolicyRule> =
    filter {
        it.scope == RuleScope.Domain &&
            (it.target == DomainWildcard || it.target == WebNavigationPolicy.RuleTarget) &&
            it.action == RuleAction.Block
    }

internal fun List<PolicyRule>.webPolicyRevision(): Int =
    asSequence()
        .filter { it.scope == RuleScope.Domain }
        .map { "${it.target}:${it.action}:${it.enabled}:${it.priority}" }
        .sorted()
        .joinToString("|")
        .hashCode()

internal fun List<PolicyRule>.webPolicyPreferences(): WebPolicyPreferences =
    WebPolicyPreferences(
        webNavigationBlocked = webNavigationBlocked(),
        externalSearchResultsAllowed = externalSearchResultsAllowed(),
        safeSearchEnabled = safeSearchEnabled(),
        dagEnabled = dagEnabled(),
        dagExtraKosherEnabled = dagExtraKosherEnabled(),
    )

internal fun List<PolicyRule>.webPolicyChanges(
    desired: WebPolicyPreferences,
    deviceId: String,
): List<PolicyRule> {
    val working = toMutableList()
    val changes = linkedMapOf<String, PolicyRule>()

    fun planCanonical(
        target: String,
        action: RuleAction,
        enabled: Boolean,
        priority: Int,
    ) {
        val canonicalId = webRuleId(deviceId, target, action)
        val existing = working.filter { it.scope == RuleScope.Domain && it.target == target && it.action == action }
        val canonical =
            existing.firstOrNull { it.id == canonicalId }
                ?: PolicyRule(
                    id = canonicalId,
                    level = PolicyLevel.Account,
                    scope = RuleScope.Domain,
                    target = target,
                    action = action,
                    priority = priority,
                    enabled = enabled,
                )
        val desiredRule = canonical.copy(enabled = enabled, priority = priority)
        if (canonical !in working) working += canonical
        if (desiredRule != canonical || existing.none { it.id == canonicalId }) {
            working[working.indexOfFirst { it.id == canonicalId }] = desiredRule
            changes[desiredRule.id] = desiredRule
        }
        existing.filterNot { it.id == canonicalId }.forEach { duplicate ->
            if (duplicate.enabled) {
                val disabled = duplicate.copy(enabled = false)
                working[working.indexOfFirst { it.id == duplicate.id }] = disabled
                changes[disabled.id] = disabled
            }
        }
    }

    planCanonical(
        target = WebNavigationPolicy.RuleTarget,
        action = RuleAction.Block,
        enabled = desired.webNavigationBlocked,
        priority = WebNavigationBlockPriority,
    )
    planCanonical(
        target = WebNavigationPolicy.ExternalSearchResultsAllowedTarget,
        action = RuleAction.Allow,
        enabled = desired.externalSearchResultsAllowed,
        priority = WebNavigationBlockPriority + 10,
    )
    planCanonical(
        target = WebNavigationPolicy.SafeSearchTarget,
        action = RuleAction.Allow,
        enabled = true,
        priority = WebNavigationBlockPriority + 20,
    )
    planCanonical(
        target = WebNavigationPolicy.DagEnabledTarget,
        action = RuleAction.Allow,
        enabled = desired.dagEnabled,
        priority = WebNavigationBlockPriority + 30,
    )
    planCanonical(
        target = WebNavigationPolicy.DagExtraKosherTarget,
        action = RuleAction.Allow,
        enabled = desired.dagExtraKosherEnabled,
        priority = WebNavigationBlockPriority + 40,
    )

    working
        .filter { rule ->
            rule.enabled &&
                rule.scope == RuleScope.Domain &&
                rule.target in LegacyWebGeneratedTargets &&
                !rule.isCanonicalWebPreference()
        }.forEach { legacy ->
            val disabled = legacy.copy(enabled = false)
            working[working.indexOfFirst { it.id == legacy.id }] = disabled
            changes[disabled.id] = disabled
        }
    return changes.values.toList()
}

private fun PolicyRule.isCanonicalWebPreference(): Boolean =
    (target == WebNavigationPolicy.RuleTarget && action == RuleAction.Block) ||
        (target == WebNavigationPolicy.ExternalSearchResultsAllowedTarget && action == RuleAction.Allow) ||
        (target == WebNavigationPolicy.SafeSearchTarget && action == RuleAction.Allow) ||
        (target == WebNavigationPolicy.DagEnabledTarget && action == RuleAction.Allow) ||
        (target == WebNavigationPolicy.DagExtraKosherTarget && action == RuleAction.Allow)

internal fun webRuleId(
    deviceId: String,
    target: String,
    action: RuleAction,
): String = UUID.nameUUIDFromBytes("web:$deviceId:$target:${action.name}".toByteArray()).toString()

internal data class WebPolicyPreferences(
    val webNavigationBlocked: Boolean,
    val externalSearchResultsAllowed: Boolean,
    val safeSearchEnabled: Boolean,
    val dagEnabled: Boolean,
    val dagExtraKosherEnabled: Boolean,
)

internal enum class WebPolicyPreference {
    NavigationBlocked,
    ExternalSearchResultsAllowed,
    SafeSearchEnabled,
    DagEnabled,
    DagExtraKosherEnabled,
}

internal fun WebPolicyPreferences.withPreference(
    preference: WebPolicyPreference,
    enabled: Boolean,
): WebPolicyPreferences =
    when (preference) {
        WebPolicyPreference.NavigationBlocked -> copy(webNavigationBlocked = enabled)
        WebPolicyPreference.ExternalSearchResultsAllowed -> copy(externalSearchResultsAllowed = enabled)
        WebPolicyPreference.SafeSearchEnabled -> copy(safeSearchEnabled = true)
        WebPolicyPreference.DagEnabled -> copy(dagEnabled = enabled)
        WebPolicyPreference.DagExtraKosherEnabled -> copy(dagExtraKosherEnabled = enabled)
    }

internal fun List<PolicyRule>.webPolicyPreferenceChanges(
    preference: WebPolicyPreference,
    enabled: Boolean,
    deviceId: String,
): List<PolicyRule> =
    webPolicyChanges(
        desired = webPolicyPreferences().withPreference(preference, enabled),
        deviceId = deviceId,
    )

internal fun RulesUiState.withPendingWebPreference(
    deviceId: String,
    preference: WebPolicyPreference,
    enabled: Boolean,
): RulesUiState =
    when (preference) {
        WebPolicyPreference.NavigationBlocked ->
            copy(pendingInternetBlockedByDevice = pendingInternetBlockedByDevice + (deviceId to enabled))
        WebPolicyPreference.ExternalSearchResultsAllowed ->
            copy(
                pendingExternalSearchResultsAllowedByDevice =
                    pendingExternalSearchResultsAllowedByDevice + (deviceId to enabled),
            )
        WebPolicyPreference.SafeSearchEnabled ->
            copy(pendingSafeSearchEnabledByDevice = pendingSafeSearchEnabledByDevice + (deviceId to enabled))
        WebPolicyPreference.DagEnabled ->
            copy(pendingDagEnabledByDevice = pendingDagEnabledByDevice + (deviceId to enabled))
        WebPolicyPreference.DagExtraKosherEnabled ->
            copy(pendingDagExtraKosherEnabledByDevice = pendingDagExtraKosherEnabledByDevice + (deviceId to enabled))
    }

internal fun RulesUiState.clearPendingWebPreference(
    deviceId: String,
    preference: WebPolicyPreference,
): RulesUiState =
    when (preference) {
        WebPolicyPreference.NavigationBlocked ->
            copy(pendingInternetBlockedByDevice = pendingInternetBlockedByDevice - deviceId)
        WebPolicyPreference.ExternalSearchResultsAllowed ->
            copy(
                pendingExternalSearchResultsAllowedByDevice = pendingExternalSearchResultsAllowedByDevice - deviceId,
            )
        WebPolicyPreference.SafeSearchEnabled ->
            copy(pendingSafeSearchEnabledByDevice = pendingSafeSearchEnabledByDevice - deviceId)
        WebPolicyPreference.DagEnabled ->
            copy(pendingDagEnabledByDevice = pendingDagEnabledByDevice - deviceId)
        WebPolicyPreference.DagExtraKosherEnabled ->
            copy(pendingDagExtraKosherEnabledByDevice = pendingDagExtraKosherEnabledByDevice - deviceId)
    }

internal fun RulesUiState.withPendingAppAllowed(
    deviceId: String,
    packageName: String,
    allowed: Boolean,
): RulesUiState =
    copy(
        pendingAppAllowedByDevice =
            pendingAppAllowedByDevice +
                (deviceId to (pendingAppAllowedByDevice[deviceId].orEmpty() + (packageName to allowed))),
    )

internal fun RulesUiState.clearPendingAppAllowed(
    deviceId: String,
    packageName: String,
): RulesUiState {
    val remainingForDevice = pendingAppAllowedByDevice[deviceId].orEmpty() - packageName
    return copy(
        pendingAppAllowedByDevice =
            if (remainingForDevice.isEmpty()) {
                pendingAppAllowedByDevice - deviceId
            } else {
                pendingAppAllowedByDevice + (deviceId to remainingForDevice)
            },
    )
}

internal fun scheduleSavingKey(
    deviceId: String,
    scope: RuleScope,
    target: String,
): String = "$deviceId:${scope.name}:$target"

internal fun RulesUiState.recoveryCodeFor(deviceId: String): String =
    recoveryCode.takeIf { recoveryCodeDeviceId == deviceId }.orEmpty()

internal fun List<PolicyRule>.webNavigationOpenWithoutAuxiliaryBlocks(): Boolean =
    !webNavigationBlocked() && activeWebAuxiliaryBlockCount() == 0

internal fun List<PolicyRule>.activeDomainBlockCount(): Int =
    count { it.enabled && it.scope == RuleScope.Domain && it.action == RuleAction.Block }

internal fun List<PolicyRule>.activeWebAuxiliaryBlockCount(): Int =
    count {
        it.enabled &&
            it.scope == RuleScope.Domain &&
            it.action == RuleAction.Block &&
            it.target in LegacyWebGeneratedDomainTargets
    }

internal fun List<Device>.toUserDevices(apps: List<InstalledApp>): List<UserDeviceUiState> {
    val devicesById = associateBy { it.id }
    val localUserDeviceIds = userDeviceIds()
    val appsByDevice =
        if (localUserDeviceIds.size == 1) {
            apps.groupBy { app ->
                if (app.deviceId in localUserDeviceIds) app.deviceId else localUserDeviceIds.first()
            }
        } else {
            apps.filter { it.deviceId in localUserDeviceIds }.groupBy { it.deviceId }
        }
    return localUserDeviceIds
        .distinct()
        .map { deviceId ->
            val device = devicesById[deviceId]
            val newestAppSeen =
                appsByDevice[deviceId]
                    ?.map { it.updatedAtEpochMillis }
                    ?.maxOrNull()
            val lastSeen = device?.lastSeenAtEpochMillis ?: newestAppSeen
            val protectionComplete =
                device?.vpnState == ComponentState.Enabled &&
                    device.accessibilityState == ComponentState.Enabled &&
                    device.deviceAdminState == ComponentState.Enabled
            val status =
                when {
                    lastSeen == null -> UserDeviceStatus.Unknown
                    System.currentTimeMillis() - lastSeen > OfflineDeviceWindowMillis -> UserDeviceStatus.Inactive
                    !protectionComplete -> UserDeviceStatus.Unprotected
                    else -> UserDeviceStatus.Active
                }
            UserDeviceUiState(
                id = deviceId,
                accountId = device?.accountId.orEmpty(),
                name = device?.displayName ?: "Usuario",
                status = status,
                lastSeenLabel = lastSeen.toLastSeenLabel(),
                appCount = appsByDevice[deviceId]?.distinctBy { it.packageName }?.size ?: 0,
                protectionAlert =
                    device?.let {
                        DeviceProtectionAlert.fromStates(
                            it.vpnState,
                            it.accessibilityState,
                            it.deviceAdminState,
                        )
                    },
                protectionComplete = protectionComplete,
                vpnState = device?.vpnState.displayName(),
                accessibilityState = device?.accessibilityState.displayName(),
                deviceAdminState = device?.deviceAdminState.displayName(),
                userLabel = "Usuario",
            )
        }.sortedWith(
            compareByDescending<UserDeviceUiState> { it.appCount > 0 }
                .thenBy { it.status.sortOrder }
                .thenBy { it.name.lowercase() },
        )
}

private fun ComponentState?.displayName(): String =
    when (this) {
        ComponentState.Enabled -> "Activa"
        ComponentState.Disabled -> "Inactiva"
        ComponentState.Warning -> "Con advertencias"
        ComponentState.Unknown, null -> "Desconocida"
    }

private fun List<Device>.userDeviceIds(): List<String> = filter { device -> device.appRole != "admin" }.map { it.id }

private val UserDeviceStatus.sortOrder: Int
    get() =
        when (this) {
            UserDeviceStatus.Active -> 0
            UserDeviceStatus.Unprotected -> 1
            UserDeviceStatus.Inactive -> 2
            UserDeviceStatus.Unknown -> 3
        }

private val AppControlUiState.accessSortOrder: Int
    get() =
        when {
            !confirmedAllowed && extraTimeRemainingMinutes == null -> 0
            extraTimeRemainingMinutes != null || dailyLimitMinutes != null -> 1
            else -> 2
        }

internal fun List<UserDeviceUiState>.selectedDeviceId(requestedDeviceId: String?): String? {
    return firstOrNull { it.id == requestedDeviceId }?.id
}

internal fun List<AppControlUiState>.filterBySearch(query: String): List<AppControlUiState> {
    val normalized = query.trim().lowercase()
    if (normalized.isBlank()) return this
    return filter {
        it.appName.lowercase().contains(normalized) ||
            it.packageName.lowercase().contains(normalized) ||
            it.deviceName.lowercase().contains(normalized)
    }
}

internal fun String.toDomainOrNull(): String? {
    val normalized = trim().lowercase()
    val withoutScheme =
        normalized
            .substringAfter("://", missingDelimiterValue = normalized)
            .substringBefore("/")
            .substringBefore("?")
            .substringBefore("#")
            .substringBefore(":")
            .removeSuffix(".")
            .removePrefix("www.")
    return withoutScheme.takeIf { DomainRegex.matches(it) }
}

private fun List<PolicyRule>.effectiveAppRule(packageName: String): PolicyRule? =
    asSequence()
        .filter { it.enabled && it.scope == RuleScope.App && it.target == packageName }
        .sortedWith(
            compareByDescending<PolicyRule> { it.level.specificity }
                .thenByDescending { it.priority },
        )
        .firstOrNull()

private fun List<ExtraTimeGrant>.activeExtraTimeRemainingMinutes(
    packageName: String,
    nowEpochMillis: Long,
): Int? =
    asSequence()
        .filter {
            it.targetType == PolicyTargetType.App &&
                it.target == packageName &&
                it.validUntilEpochMillis > nowEpochMillis
        }
        .map { it.validUntilEpochMillis }
        .maxOrNull()
        ?.remainingMinutesFrom(nowEpochMillis)

private fun Long.remainingMinutesFrom(nowEpochMillis: Long): Int =
    ((this - nowEpochMillis + MinuteMillis - 1) / MinuteMillis).toInt().coerceAtLeast(1)

private fun String.toEpochMillisOrNull(): Long? = runCatching { Instant.parse(this).toEpochMilli() }.getOrNull()

private fun Long?.toLastSeenLabel(): String =
    this?.let { value ->
        val minutes = Duration.ofMillis((System.currentTimeMillis() - value).coerceAtLeast(0)).toMinutes()
        when {
            minutes < 1 -> "ahora"
            minutes < 60 -> "hace ${minutes}m"
            minutes < 24 * 60 -> "hace ${minutes / 60}h"
            else -> "hace ${minutes / (24 * 60)}d"
        }
    } ?: "sin señal"

internal val PackageNameRegex = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")
internal val DomainRegex = Regex("^(?=.{1,253}$)([a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$")
internal const val OfflineDeviceWindowMillis = 24 * 60 * 60 * 1000L
internal const val SwitchHoldMillis = 2_500L
internal const val RoomConfirmTimeoutMillis = 5_000L
internal const val MinuteMillis = 60_000L

// Domain wildcard: enabled Block means whitelist mode; disabled means general Internet is open.
internal const val DomainWildcard = "*"
internal const val InternetBlockPriority = 10
internal const val AllowDomainPriority = 1_000
internal const val SearchEngineBlockPriority = 3_000
internal const val WebNavigationBlockPriority = WebNavigationPolicy.RulePriority
internal const val MaxDiagnosticValueLength = 80
internal const val LogTag = "RulesViewModel"
internal val SearchEngineDomains = SearchEngineCatalog.searchEngineDomains
internal val SearchSupportDomains = SearchEngineCatalog.searchSupportDomains
internal val SecureDnsDomains = SearchEngineCatalog.secureDnsDomains
internal val SearchProtectionDomains = (SearchEngineDomains + SearchSupportDomains + SecureDnsDomains).distinct()
internal val LegacyWebGeneratedDomainTargets =
    (
        SearchProtectionDomains +
            WebNavigationPolicy.UnsafeSearchDomains +
            setOf(DomainWildcard)
    ).toSet()
internal val LegacyWebGeneratedTargets =
    LegacyWebGeneratedDomainTargets +
        setOf(
            WebNavigationPolicy.RuleTarget,
            WebNavigationPolicy.ExternalSearchResultsAllowedTarget,
            WebNavigationPolicy.LegacyGoogleResultsAllowedTarget,
            WebNavigationPolicy.SafeSearchTarget,
            WebNavigationPolicy.DagEnabledTarget,
        )
internal val LegacyWebAuxiliaryBlockTargets = LegacyWebGeneratedTargets
internal val YouTubeWebDomains =
    listOf(
        "youtube.com",
        "youtubei.googleapis.com",
        "googlevideo.com",
        "ytimg.com",
    )
