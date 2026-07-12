package com.contentfilter.core.sync.engine

import android.util.Log
import androidx.room.withTransaction
import com.contentfilter.core.database.AppDatabase
import com.contentfilter.core.database.dao.AccessRequestDao
import com.contentfilter.core.database.dao.AccountDao
import com.contentfilter.core.database.dao.AppGroupDao
import com.contentfilter.core.database.dao.DailyLimitDao
import com.contentfilter.core.database.dao.DeviceDao
import com.contentfilter.core.database.dao.ExtraTimeGrantDao
import com.contentfilter.core.database.dao.PolicyDao
import com.contentfilter.core.database.entity.PolicyEntity
import com.contentfilter.core.database.entity.PolicyRuleEntity
import com.contentfilter.core.network.dto.RemoteAccessRequestDto
import com.contentfilter.core.network.dto.RemoteAccountDto
import com.contentfilter.core.network.dto.RemoteAppGroupAppDto
import com.contentfilter.core.network.dto.RemoteAppGroupDto
import com.contentfilter.core.network.dto.RemoteDailyLimitDto
import com.contentfilter.core.network.dto.RemoteDeviceDto
import com.contentfilter.core.network.dto.RemoteExtraTimeGrantDto
import com.contentfilter.core.network.dto.RemotePolicyDto
import com.contentfilter.core.network.dto.RemotePolicyRuleDto
import javax.inject.Inject

class RoomRemoteApplier
    @Inject
    constructor(
        private val database: AppDatabase,
        private val accountDao: AccountDao,
        private val policyDao: PolicyDao,
        private val deviceDao: DeviceDao,
        private val dailyLimitDao: DailyLimitDao,
        private val accessRequestDao: AccessRequestDao,
        private val extraTimeGrantDao: ExtraTimeGrantDao,
        private val appGroupDao: AppGroupDao,
    ) : RemoteApplier {
        override suspend fun applyPolicyBundle(
            policy: RemotePolicyDto,
            rules: List<RemotePolicyRuleDto>,
            limits: List<RemoteDailyLimitDto>,
            groups: List<RemoteAppGroupDto>,
            groupApps: List<RemoteAppGroupAppDto>,
        ): Boolean =
            database.withTransaction {
                if (rules.any { it.policyId != policy.id } || limits.any { it.policyId != policy.id }) {
                    Log.w(
                        LogTag,
                        "Rejected inconsistent policy bundle policyId=${policy.id.take(8)} revision=${policy.version}",
                    )
                    return@withTransaction false
                }
                val incoming = policy.toEntity()
                val currentById = policyDao.policyById(policy.id)
                val currentActive =
                    policy.deviceId?.let { policyDao.activePolicyForDevice(it) }
                        ?: policyDao.activePolicy()
                if (!shouldApplyRemotePolicy(incoming, currentById, currentActive)) {
                    Log.i(
                        LogTag,
                        "Skipped stale policy bundle policyId=${policy.id.take(8)} revision=${policy.version} " +
                            "localRevision=${currentById?.version ?: currentActive?.version}",
                    )
                    return@withTransaction false
                }
                if (policy.active && policy.deletedAt == null) {
                    policy.deviceId?.let { policyDao.deactivateOtherPoliciesForDevice(policy.id, it) }
                        ?: policyDao.deactivateOtherPoliciesWithoutDevice(policy.id)
                }
                policyDao.upsertPolicy(incoming)
                policyDao.deleteRulesForPolicy(policy.id)
                applyPolicyRules(rules)
                applyDailyLimits(limits)
                applyAppGroups(groups)
                applyAppGroupApps(groupApps)
                val webNavigationBlocked =
                    rules.any {
                        it.enabled &&
                            it.deletedAt == null &&
                            it.scope == "Domain" &&
                            it.action == "Block" &&
                            it.target == "__web_navigation_blocked__"
                    }
                Log.i(
                    LogTag,
                    "Applied complete policy bundle policyId=${policy.id.take(8)} revision=${policy.version} " +
                        "rules=${rules.size} webNavigationBlocked=$webNavigationBlocked",
                )
                true
            }

        override suspend fun applyAccounts(values: List<RemoteAccountDto>) {
            values.forEach { account ->
                if (account.deletedAt == null) {
                    accountDao.upsert(account.toEntity())
                } else {
                    accountDao.deleteById(account.id)
                }
            }
        }

        override suspend fun applyPolicies(values: List<RemotePolicyDto>) {
            values.forEach { policy ->
                val incoming = policy.toEntity()
                val currentById = policyDao.policyById(policy.id)
                val policyDeviceId = policy.deviceId
                val currentActive =
                    if (policyDeviceId != null) {
                        policyDao.activePolicyForDevice(policyDeviceId)
                    } else {
                        policyDao.activePolicy()
                    }
                if (!shouldApplyRemotePolicy(incoming, currentById, currentActive)) {
                    Log.i(
                        LogTag,
                        "Skipped stale policy id=${policy.id.take(8)} deviceId=${policy.deviceId?.take(8) ?: "none"} " +
                            "remoteVersion=${incoming.version} localVersion=${currentById?.version ?: currentActive?.version}",
                    )
                    return@forEach
                }
                if (policy.active && policy.deletedAt == null) {
                    val deviceId = policy.deviceId
                    if (deviceId != null) {
                        policyDao.deactivateOtherPoliciesForDevice(policy.id, deviceId)
                    } else {
                        policyDao.deactivateOtherPoliciesWithoutDevice(policy.id)
                    }
                }
                policyDao.upsertPolicy(incoming)
            }
        }

        override suspend fun applyDevices(values: List<RemoteDeviceDto>) {
            if (values.isNotEmpty()) {
                Log.i(
                    LogTag,
                    "Applying remote devices count=${values.size} active=${values.count { it.deletedAt == null }}",
                )
            }
            values.forEach { device ->
                if (device.deletedAt == null) {
                    deviceDao.upsert(device.toEntity())
                } else {
                    deviceDao.deleteById(device.id)
                }
            }
        }

        override suspend fun applyPolicyRules(values: List<RemotePolicyRuleDto>) {
            values.forEach { rule ->
                val incoming = rule.toEntity()
                val current = policyDao.ruleById(rule.id)
                if (!shouldApplyRemoteRule(incoming, current)) {
                    Log.i(
                        LogTag,
                        "Skipped stale policy rule id=${rule.id.take(8)} policyId=${rule.policyId.take(8)} " +
                            "remoteUpdatedAt=${incoming.updatedAtEpochMillis} localUpdatedAt=${current?.updatedAtEpochMillis}",
                    )
                    return@forEach
                }
                if (rule.deletedAt == null) {
                    policyDao.upsertRule(incoming)
                } else {
                    policyDao.deleteRuleById(rule.id)
                }
            }
        }

        override suspend fun applyDailyLimits(values: List<RemoteDailyLimitDto>) {
            values.forEach { limit ->
                val incoming = limit.toEntity()
                val current = dailyLimitDao.byId(limit.id)
                if (current != null && incoming.updatedAtEpochMillis < current.updatedAtEpochMillis) {
                    Log.i(
                        LogTag,
                        "Skipped stale daily limit id=${limit.id.take(8)} " +
                            "remoteUpdatedAt=${incoming.updatedAtEpochMillis} localUpdatedAt=${current.updatedAtEpochMillis}",
                    )
                    return@forEach
                }
                if (limit.deletedAt == null) {
                    dailyLimitDao.upsert(incoming)
                } else {
                    dailyLimitDao.upsert(incoming.copy(enabled = false))
                }
            }
        }

        override suspend fun applyAppGroups(values: List<RemoteAppGroupDto>) {
            values.forEach { group ->
                val incoming = group.toEntity()
                val current = appGroupDao.groupById(group.id)
                if (current != null && incoming.updatedAtEpochMillis < current.updatedAtEpochMillis) return@forEach
                if (group.deletedAt == null) {
                    appGroupDao.upsertGroup(incoming)
                } else {
                    appGroupDao.upsertGroup(incoming.copy(enabled = false))
                }
            }
        }

        override suspend fun applyAppGroupApps(values: List<RemoteAppGroupAppDto>) {
            values.forEach { app ->
                val incoming = app.toEntity()
                val current = appGroupDao.appById(app.id)
                if (current != null && incoming.updatedAtEpochMillis < current.updatedAtEpochMillis) return@forEach
                if (app.deletedAt == null) {
                    appGroupDao.upsertApp(incoming)
                } else {
                    appGroupDao.upsertApp(incoming.copy(enabled = false))
                }
            }
        }

        override suspend fun applyAccessRequests(values: List<RemoteAccessRequestDto>) {
            if (values.isNotEmpty()) {
                Log.i(LogTag, "Applying remote access requests count=${values.size}")
            }
            values.forEach { request ->
                if (request.deletedAt == null) {
                    accessRequestDao.upsert(request.toEntity())
                } else {
                    accessRequestDao.deleteById(request.id)
                }
            }
        }

        override suspend fun applyExtraTimeGrants(values: List<RemoteExtraTimeGrantDto>) {
            values.forEach { grant ->
                if (grant.deletedAt == null) {
                    extraTimeGrantDao.upsert(grant.toEntity())
                } else {
                    extraTimeGrantDao.deleteById(grant.id)
                }
            }
        }

        private companion object {
            const val LogTag = "RoomRemoteApplier"
        }
    }

internal fun shouldApplyRemotePolicy(
    incoming: PolicyEntity,
    currentById: PolicyEntity?,
    currentActive: PolicyEntity?,
): Boolean {
    if (currentById != null && incoming.isOlderThan(currentById)) return false
    if (
        incoming.active &&
        currentActive != null &&
        currentActive.id != incoming.id &&
        !incoming.isNewerThan(currentActive)
    ) {
        return false
    }
    return true
}

internal fun shouldApplyRemoteRule(
    incoming: PolicyRuleEntity,
    current: PolicyRuleEntity?,
): Boolean = current == null || incoming.updatedAtEpochMillis >= current.updatedAtEpochMillis

private fun PolicyEntity.isOlderThan(other: PolicyEntity): Boolean =
    version < other.version || (version == other.version && updatedAtEpochMillis < other.updatedAtEpochMillis)

private fun PolicyEntity.isNewerThan(other: PolicyEntity): Boolean =
    version > other.version || (version == other.version && updatedAtEpochMillis > other.updatedAtEpochMillis)
