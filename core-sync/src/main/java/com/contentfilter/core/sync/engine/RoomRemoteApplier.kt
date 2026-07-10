package com.contentfilter.core.sync.engine

import android.util.Log
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
        private val accountDao: AccountDao,
        private val policyDao: PolicyDao,
        private val deviceDao: DeviceDao,
        private val dailyLimitDao: DailyLimitDao,
        private val accessRequestDao: AccessRequestDao,
        private val extraTimeGrantDao: ExtraTimeGrantDao,
        private val appGroupDao: AppGroupDao,
    ) {
        suspend fun applyAccounts(values: List<RemoteAccountDto>) {
            values.forEach { account ->
                if (account.deletedAt == null) {
                    accountDao.upsert(account.toEntity())
                } else {
                    accountDao.deleteById(account.id)
                }
            }
        }

        suspend fun applyPolicies(values: List<RemotePolicyDto>) {
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

        suspend fun applyDevices(values: List<RemoteDeviceDto>) {
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

        suspend fun applyPolicyRules(values: List<RemotePolicyRuleDto>) {
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

        suspend fun applyDailyLimits(values: List<RemoteDailyLimitDto>) {
            values.forEach { limit ->
                if (limit.deletedAt == null) {
                    dailyLimitDao.upsert(limit.toEntity())
                } else {
                    dailyLimitDao.deleteById(limit.id)
                }
            }
        }

        suspend fun applyAppGroups(values: List<RemoteAppGroupDto>) {
            values.forEach { group ->
                if (group.deletedAt == null) {
                    appGroupDao.upsertGroup(group.toEntity())
                } else {
                    appGroupDao.deleteGroupById(group.id)
                }
            }
        }

        suspend fun applyAppGroupApps(values: List<RemoteAppGroupAppDto>) {
            values.forEach { app ->
                if (app.deletedAt == null) {
                    appGroupDao.upsertApp(app.toEntity())
                } else {
                    appGroupDao.deleteAppById(app.id)
                }
            }
        }

        suspend fun applyAccessRequests(values: List<RemoteAccessRequestDto>) {
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

        suspend fun applyExtraTimeGrants(values: List<RemoteExtraTimeGrantDto>) {
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
