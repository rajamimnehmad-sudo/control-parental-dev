package com.contentfilter.admin.rules

import com.contentfilter.core.domain.model.DailyLimit
import com.contentfilter.core.domain.model.Device
import com.contentfilter.core.domain.model.ExtraTimeGrant
import com.contentfilter.core.domain.model.AppGroup
import com.contentfilter.core.domain.model.ComponentState
import com.contentfilter.core.domain.model.PolicyRule
import com.contentfilter.core.domain.model.PolicyTargetType
import com.contentfilter.core.domain.model.RuleAction
import com.contentfilter.core.domain.model.RuleScope
import com.contentfilter.core.domain.model.SearchEngineCatalog
import com.contentfilter.core.domain.model.WebNavigationPolicy
import com.contentfilter.core.domain.model.googleResultsAllowed
import com.contentfilter.core.domain.model.safeSearchEnabled
import com.contentfilter.core.domain.model.webImagesBlocked
import com.contentfilter.core.domain.model.webNavigationBlocked
import com.contentfilter.core.network.dto.RemoteInstalledAppDto
import java.time.Duration
import java.time.Instant

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

internal fun List<RemoteInstalledAppDto>.toAppControls(
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

internal fun List<RemoteInstalledAppDto>.forSelectedUserDevice(
    selectedDeviceId: String,
    devices: List<Device>,
): List<RemoteInstalledAppDto> {
    val userDeviceIds = devices.userDeviceIds()
    val directApps = filter { it.deviceId == selectedDeviceId }
    if (userDeviceIds.size != 1 || selectedDeviceId !in userDeviceIds) return directApps
    val orphanApps = filter { it.deviceId !in userDeviceIds }
    return (directApps + orphanApps).preferAppsWithIcons().distinctBy { it.packageName }
}

internal fun List<RemoteInstalledAppDto>.preferAppsWithIcons(): List<RemoteInstalledAppDto> =
    sortedWith(
        compareByDescending<RemoteInstalledAppDto> { it.iconBase64.hasVisibleIcon() }
            .thenByDescending { it.updatedAt },
    )

private fun String?.hasVisibleIcon(): Boolean = !isNullOrBlank()

internal fun List<PolicyRule>.internetBlocked(): Boolean =
    webNavigationBlocked()

internal fun List<PolicyRule>.googleResultsAllowedForWeb(): Boolean = googleResultsAllowed()

internal fun List<PolicyRule>.imagesBlockedForWeb(): Boolean = webImagesBlocked()

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

internal fun List<PolicyRule>.searchEnginesAllowed(): Boolean =
    SearchProtectionDomains.none { domain ->
        any {
            it.enabled &&
                it.scope == RuleScope.Domain &&
                it.target == domain &&
                it.action == RuleAction.Block
        }
    }

internal fun List<PolicyRule>.searchEnginesStateConfirmed(allowed: Boolean): Boolean =
    if (allowed) {
        searchEnginesAllowed()
    } else {
        SearchProtectionDomains.all { domain ->
            any {
                it.enabled &&
                    it.scope == RuleScope.Domain &&
                    it.target == domain &&
                    it.action == RuleAction.Block
            }
        } &&
            SearchProtectionDomains.none { domain ->
                any {
                    it.enabled &&
                        it.scope == RuleScope.Domain &&
                        it.target == domain &&
                        it.action == RuleAction.Allow
                }
            }
    }

internal fun List<PolicyRule>.searchEngineBlockRules(): List<PolicyRule> =
    filter {
        it.scope == RuleScope.Domain &&
            it.target in SearchProtectionDomains &&
            it.action == RuleAction.Block
    }

internal fun List<Device>.toUserDevices(apps: List<RemoteInstalledAppDto>): List<UserDeviceUiState> {
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
                    ?.mapNotNull { it.updatedAt.toEpochMillisOrNull() }
                    ?.maxOrNull()
            val lastSeen = device?.lastSeenAtEpochMillis ?: newestAppSeen
            val status =
                when {
                    device?.vpnState == ComponentState.Disabled ||
                        device?.accessibilityState == ComponentState.Disabled -> UserDeviceStatus.Unprotected
                    lastSeen == null -> UserDeviceStatus.Unknown
                    System.currentTimeMillis() - lastSeen <= ActiveDeviceWindowMillis -> UserDeviceStatus.Active
                    else -> UserDeviceStatus.Inactive
                }
            UserDeviceUiState(
                id = deviceId,
                name = device?.displayName ?: "Usuario",
                status = status,
                lastSeenLabel = lastSeen.toLastSeenLabel(),
                appCount = appsByDevice[deviceId]?.distinctBy { it.packageName }?.size ?: 0,
                protectionAlert = device?.protectionAlert,
                userLabel = "Usuario",
            )
        }.sortedWith(
            compareByDescending<UserDeviceUiState> { it.appCount > 0 }
                .thenBy { it.status.sortOrder }
                .thenBy { it.name.lowercase() },
        )
}

private fun List<Device>.userDeviceIds(): List<String> =
    filter { device -> device.appRole != "admin" }.map { it.id }

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
internal const val ActiveDeviceWindowMillis = 15 * 60 * 1000L
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
internal val SecureDnsDomains = SearchEngineCatalog.secureDnsDomains
internal val SearchProtectionDomains = (SearchEngineDomains + SecureDnsDomains).distinct()
internal val YouTubeWebDomains =
    listOf(
        "youtube.com",
        "youtubei.googleapis.com",
        "googlevideo.com",
        "ytimg.com",
    )
