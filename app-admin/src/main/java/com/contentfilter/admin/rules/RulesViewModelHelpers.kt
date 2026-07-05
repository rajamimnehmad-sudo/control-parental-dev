package com.contentfilter.admin.rules

import com.contentfilter.core.domain.model.DailyLimit
import com.contentfilter.core.domain.model.Device
import com.contentfilter.core.domain.model.PolicyRule
import com.contentfilter.core.domain.model.PolicyTargetType
import com.contentfilter.core.domain.model.RuleAction
import com.contentfilter.core.domain.model.RuleScope
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
    devices: List<Device>,
    pendingAllowed: Map<String, Boolean>,
): List<AppControlUiState> {
    val devicesById = devices.associateBy { it.id }
    return distinctBy { it.packageName }
        .map { app ->
            val effectiveRule = rules.effectiveAppRule(app.packageName)
            val limit =
                limits.firstOrNull {
                    it.enabled &&
                        it.targetType == PolicyTargetType.App &&
                        it.target == app.packageName
                }
            AppControlUiState(
                appName = app.appName,
                packageName = app.packageName,
                versionName = app.versionName,
                isSystemApp = app.isSystemApp,
                iconBase64 = app.iconBase64,
                deviceName = devicesById[app.deviceId]?.displayName ?: "Usuario",
                allowed = pendingAllowed[app.packageName] ?: (effectiveRule?.action != RuleAction.Block),
                dailyLimitMinutes = limit?.limitMinutes,
                isUpdating = pendingAllowed.containsKey(app.packageName),
            )
        }
        .sortedWith(compareBy({ it.deviceName.lowercase() }, { it.appName.lowercase() }, { it.packageName }))
}

internal fun List<RemoteInstalledAppDto>.forSelectedUserDevice(
    selectedDeviceId: String,
    devices: List<Device>,
): List<RemoteInstalledAppDto> {
    val userDeviceIds = devices.userDeviceIds()
    val directApps = filter { it.deviceId == selectedDeviceId }
    if (userDeviceIds.size != 1 || selectedDeviceId !in userDeviceIds) return directApps
    val orphanApps = filter { it.deviceId !in userDeviceIds }
    return (directApps + orphanApps).distinctBy { it.packageName }
}

internal fun List<PolicyRule>.internetBlocked(): Boolean =
    internetBlockRules().any { it.enabled }

internal fun List<PolicyRule>.internetBlockRules(): List<PolicyRule> =
    filter {
        it.scope == RuleScope.Domain &&
            it.target == DomainWildcard &&
            it.action == RuleAction.Block
    }

internal fun List<PolicyRule>.googleSearchAllowed(): Boolean =
    GoogleSearchDomains.all { domain ->
        any {
            it.enabled &&
                it.scope == RuleScope.Domain &&
                it.target == domain &&
                it.action == RuleAction.Allow
        }
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
            UserDeviceStatus.Inactive -> 1
            UserDeviceStatus.Unknown -> 2
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

internal fun String.expandBlockedDomainFamily(): List<String> =
    if (this in YouTubeWebDomains) {
        YouTubeWebDomains
    } else {
        listOf(this)
    }

private fun List<PolicyRule>.effectiveAppRule(packageName: String): PolicyRule? =
    asSequence()
        .filter { it.enabled && it.scope == RuleScope.App && it.target == packageName }
        .sortedWith(
            compareByDescending<PolicyRule> { it.level.specificity }
                .thenByDescending { it.priority },
        )
        .firstOrNull()

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
internal const val DomainWildcard = "*"
internal const val InternetBlockPriority = 10
internal const val BlockDomainPriority = 2_000
internal const val AllowDomainPriority = 1_000
internal const val LogTag = "RulesViewModel"
internal val GoogleSearchDomains =
    listOf(
        "google.com",
    )
internal val YouTubeWebDomains =
    listOf(
        "youtube.com",
        "youtubei.googleapis.com",
        "googlevideo.com",
        "ytimg.com",
    )
