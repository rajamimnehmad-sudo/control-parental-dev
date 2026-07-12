package com.contentfilter.admin.rules

import com.contentfilter.core.domain.model.AppGroup
import com.contentfilter.core.domain.model.ComponentState
import com.contentfilter.core.domain.model.DailyLimit
import com.contentfilter.core.domain.model.Device
import com.contentfilter.core.domain.model.ExtraTimeGrant
import com.contentfilter.core.domain.model.InstalledApp
import com.contentfilter.core.domain.model.PolicyLevel
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

internal fun List<PolicyRule>.webNavigationModeChanges(
    blocked: Boolean,
    imagesBlocked: Boolean,
    safeSearchEnabled: Boolean,
    identitySeed: String? = null,
): List<PolicyRule> {
    val working = toMutableList()
    val changes = linkedMapOf<String, PolicyRule>()

    fun plan(
        target: String,
        action: RuleAction,
        enabled: Boolean,
        priority: Int,
        createWhenDisabled: Boolean = false,
    ) {
        val existing =
            working
                .filter { it.scope == RuleScope.Domain && it.target == target && it.action == action }
                .sortedWith(
                    compareByDescending<PolicyRule> { it.enabled }
                        .thenByDescending { it.priority }
                        .thenBy { it.id },
                )
        val canonical =
            existing.firstOrNull()
                ?: if (enabled || createWhenDisabled) {
                    PolicyRule(
                        id = webRuleId(identitySeed, target, action),
                        level = PolicyLevel.Account,
                        scope = RuleScope.Domain,
                        target = target,
                        action = action,
                        priority = priority,
                        enabled = enabled,
                    )
                } else {
                    return
                }
        val desired = canonical.copy(enabled = enabled, priority = priority)
        if (canonical !in working) working += canonical
        if (desired != canonical) {
            working[working.indexOfFirst { it.id == canonical.id }] = desired
            changes[desired.id] = desired
        } else if (existing.isEmpty()) {
            changes[desired.id] = desired
        }
        existing.drop(1).forEach { duplicate ->
            if (duplicate.enabled || duplicate.priority != priority) {
                val disabled = duplicate.copy(enabled = false, priority = priority)
                working[working.indexOfFirst { it.id == duplicate.id }] = disabled
                changes[disabled.id] = disabled
            }
        }
    }

    plan(
        target = WebNavigationPolicy.RuleTarget,
        action = RuleAction.Block,
        enabled = blocked,
        priority = WebNavigationBlockPriority,
        createWhenDisabled = true,
    )
    plan(DomainWildcard, RuleAction.Block, enabled = false, priority = InternetBlockPriority)

    if (blocked) {
        SearchEngineDomains.forEach { domain ->
            plan(domain, RuleAction.Allow, enabled = false, priority = AllowDomainPriority)
            plan(domain, RuleAction.Block, enabled = true, priority = SearchEngineBlockPriority)
        }
        SecureDnsDomains.forEach { domain ->
            plan(domain, RuleAction.Allow, enabled = false, priority = AllowDomainPriority)
            plan(domain, RuleAction.Block, enabled = true, priority = SearchEngineBlockPriority)
        }
        plan(
            WebNavigationPolicy.GoogleResultsAllowedTarget,
            RuleAction.Allow,
            enabled = false,
            priority = WebNavigationBlockPriority + 10,
        )
        WebNavigationPolicy.GoogleSearchDomains.forEach { domain ->
            plan(domain, RuleAction.Allow, enabled = false, priority = AllowDomainPriority)
        }
        plan(
            WebNavigationPolicy.ImagesBlockedTarget,
            RuleAction.Block,
            enabled = imagesBlocked,
            priority = WebNavigationBlockPriority + 20,
        )
        WebNavigationPolicy.ImageDomains.forEach { domain ->
            plan(
                target = domain,
                action = RuleAction.Block,
                enabled = imagesBlocked,
                priority = SearchEngineBlockPriority + 10,
            )
        }
        plan(
            WebNavigationPolicy.SafeSearchTarget,
            RuleAction.Allow,
            enabled = safeSearchEnabled,
            priority = WebNavigationBlockPriority + 30,
        )
        WebNavigationPolicy.UnsafeSearchDomains.forEach { domain ->
            plan(domain, RuleAction.Block, enabled = safeSearchEnabled, priority = SearchEngineBlockPriority)
        }
    } else {
        SearchEngineDomains.forEach { domain ->
            plan(domain, RuleAction.Allow, enabled = true, priority = AllowDomainPriority)
            plan(domain, RuleAction.Block, enabled = false, priority = SearchEngineBlockPriority)
        }
        SecureDnsDomains.forEach { domain ->
            plan(domain, RuleAction.Allow, enabled = false, priority = AllowDomainPriority)
            plan(domain, RuleAction.Block, enabled = false, priority = SearchEngineBlockPriority)
        }
        plan(
            WebNavigationPolicy.GoogleResultsAllowedTarget,
            RuleAction.Allow,
            enabled = false,
            priority = WebNavigationBlockPriority + 10,
        )
        WebNavigationPolicy.GoogleSearchDomains.forEach { domain ->
            plan(domain, RuleAction.Allow, enabled = false, priority = AllowDomainPriority)
        }
        plan(
            WebNavigationPolicy.ImagesBlockedTarget,
            RuleAction.Block,
            enabled = false,
            priority = WebNavigationBlockPriority + 20,
        )
        WebNavigationPolicy.ImageDomains.forEach { domain ->
            plan(
                target = domain,
                action = RuleAction.Block,
                enabled = false,
                priority = SearchEngineBlockPriority + 10,
            )
        }
        WebNavigationPolicy.UnsafeSearchDomains.forEach { domain ->
            plan(domain, RuleAction.Block, enabled = false, priority = SearchEngineBlockPriority)
        }
        working
            .asSequence()
            .filter {
                it.enabled &&
                    it.scope == RuleScope.Domain &&
                    it.action == RuleAction.Block &&
                    it.target in LegacyWebAuxiliaryBlockTargets
            }.map { it.target }
            .distinct()
            .forEach { target ->
                val priority = working.filter { it.target == target }.maxOf { it.priority }
                plan(target, RuleAction.Block, enabled = false, priority = priority)
            }
    }
    return changes.values.toList()
}

internal fun List<PolicyRule>.googleResultsModeChanges(
    allowed: Boolean,
    webBlocked: Boolean,
    deviceId: String,
): List<PolicyRule> {
    val cleanupChanges =
        if (webBlocked) {
            emptyList()
        } else {
            webNavigationModeChanges(
                blocked = false,
                imagesBlocked = false,
                safeSearchEnabled = safeSearchEnabled(),
                identitySeed = deviceId,
            )
        }
    val working = applyPolicyRuleChanges(cleanupChanges).toMutableList()
    val changes = linkedMapOf<String, PolicyRule>()
    cleanupChanges.forEach { changes[it.id] = it }

    fun planAllow(
        target: String,
        enabled: Boolean,
        priority: Int,
        createWhenDisabled: Boolean = false,
    ) {
        val existing =
            working
                .filter { it.scope == RuleScope.Domain && it.target == target && it.action == RuleAction.Allow }
                .sortedWith(
                    compareByDescending<PolicyRule> { it.enabled }
                        .thenByDescending { it.priority }
                        .thenBy { it.id },
                )
        val canonical =
            existing.firstOrNull()
                ?: if (enabled || createWhenDisabled) {
                    PolicyRule(
                        id = webRuleId(deviceId, target, RuleAction.Allow),
                        level = PolicyLevel.Account,
                        scope = RuleScope.Domain,
                        target = target,
                        action = RuleAction.Allow,
                        priority = priority,
                        enabled = enabled,
                    )
                } else {
                    return
                }
        val desired = canonical.copy(enabled = enabled, priority = priority)
        if (canonical !in working) working += canonical
        if (desired != canonical || existing.isEmpty()) {
            working[working.indexOfFirst { it.id == canonical.id }] = desired
            changes[desired.id] = desired
        }
        existing.drop(1).forEach { duplicate ->
            if (duplicate.enabled || duplicate.priority != priority) {
                val disabled = duplicate.copy(enabled = false, priority = priority)
                working[working.indexOfFirst { it.id == duplicate.id }] = disabled
                changes[disabled.id] = disabled
            }
        }
    }

    planAllow(
        target = WebNavigationPolicy.GoogleResultsAllowedTarget,
        enabled = allowed,
        priority = WebNavigationBlockPriority + 10,
        createWhenDisabled = true,
    )
    WebNavigationPolicy.GoogleSearchDomains.forEach { domain ->
        planAllow(domain, enabled = allowed, priority = AllowDomainPriority)
    }
    return changes.values.toList()
}

private fun List<PolicyRule>.applyPolicyRuleChanges(changes: List<PolicyRule>): List<PolicyRule> {
    val changesById = changes.associateBy { it.id }
    return map { changesById[it.id] ?: it } + changes.filter { change -> none { it.id == change.id } }
}

private fun webRuleId(
    identitySeed: String?,
    target: String,
    action: RuleAction,
): String =
    identitySeed
        ?.let { seed -> UUID.nameUUIDFromBytes("web:$seed:$target:${action.name}".toByteArray()).toString() }
        ?: UUID.randomUUID().toString()

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

internal fun List<PolicyRule>.webNavigationOpenWithoutAuxiliaryBlocks(): Boolean =
    !webNavigationBlocked() && activeWebAuxiliaryBlockCount() == 0

internal fun List<PolicyRule>.activeDomainBlockCount(): Int =
    count { it.enabled && it.scope == RuleScope.Domain && it.action == RuleAction.Block }

internal fun List<PolicyRule>.activeWebAuxiliaryBlockCount(): Int =
    count {
        it.enabled &&
            it.scope == RuleScope.Domain &&
            it.action == RuleAction.Block &&
            it.target in LegacyWebAuxiliaryBlockTargets
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
internal val SearchSupportDomains = SearchEngineCatalog.searchSupportDomains
internal val SecureDnsDomains = SearchEngineCatalog.secureDnsDomains
internal val SearchProtectionDomains = (SearchEngineDomains + SearchSupportDomains + SecureDnsDomains).distinct()
internal val LegacyWebAuxiliaryBlockTargets =
    (
        SearchProtectionDomains +
            WebNavigationPolicy.ImageDomains +
            WebNavigationPolicy.UnsafeSearchDomains +
            setOf(
                DomainWildcard,
                WebNavigationPolicy.RuleTarget,
                WebNavigationPolicy.ImagesBlockedTarget,
            )
    ).toSet()
internal val YouTubeWebDomains =
    listOf(
        "youtube.com",
        "youtubei.googleapis.com",
        "googlevideo.com",
        "ytimg.com",
    )
