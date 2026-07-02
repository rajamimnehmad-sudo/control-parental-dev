package com.contentfilter.core.sync.engine

import android.util.Log
import com.contentfilter.core.database.dao.AccessRequestDao
import com.contentfilter.core.database.dao.DailyLimitDao
import com.contentfilter.core.database.dao.DeviceDao
import com.contentfilter.core.database.dao.ExtraTimeGrantDao
import com.contentfilter.core.database.dao.PolicyDao
import com.contentfilter.core.network.dto.RemoteAccessRequestDto
import com.contentfilter.core.network.dto.RemoteDailyLimitDto
import com.contentfilter.core.network.dto.RemoteDeviceDto
import com.contentfilter.core.network.dto.RemoteExtraTimeGrantDto
import com.contentfilter.core.network.dto.RemotePolicyDto
import com.contentfilter.core.network.dto.RemotePolicyRuleDto
import javax.inject.Inject

class RoomRemoteApplier
    @Inject
    constructor(
        private val policyDao: PolicyDao,
        private val deviceDao: DeviceDao,
        private val dailyLimitDao: DailyLimitDao,
        private val accessRequestDao: AccessRequestDao,
        private val extraTimeGrantDao: ExtraTimeGrantDao,
    ) {
        suspend fun applyPolicies(values: List<RemotePolicyDto>) {
            values.forEach { policyDao.upsertPolicy(it.toEntity()) }
        }

        suspend fun applyDevices(values: List<RemoteDeviceDto>) {
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
                if (rule.deletedAt == null) {
                    policyDao.upsertRule(rule.toEntity())
                } else {
                    policyDao.deleteRuleById(rule.id)
                }
            }
        }

        suspend fun applyDailyLimits(values: List<RemoteDailyLimitDto>) {
            values.forEach { dailyLimitDao.upsert(it.toEntity()) }
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
