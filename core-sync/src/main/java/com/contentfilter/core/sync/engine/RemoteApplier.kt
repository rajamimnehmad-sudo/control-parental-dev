package com.contentfilter.core.sync.engine

import com.contentfilter.core.network.dto.RemoteAccessRequestDto
import com.contentfilter.core.network.dto.RemoteAccountDto
import com.contentfilter.core.network.dto.RemoteAppGroupAppDto
import com.contentfilter.core.network.dto.RemoteAppGroupDto
import com.contentfilter.core.network.dto.RemoteDailyLimitDto
import com.contentfilter.core.network.dto.RemoteDeviceDto
import com.contentfilter.core.network.dto.RemoteExtraTimeGrantDto
import com.contentfilter.core.network.dto.RemotePolicyDto
import com.contentfilter.core.network.dto.RemotePolicyRuleDto

interface RemoteApplier {
    suspend fun applyPolicyBundle(
        policy: RemotePolicyDto,
        rules: List<RemotePolicyRuleDto>,
        limits: List<RemoteDailyLimitDto>,
        groups: List<RemoteAppGroupDto>,
        groupApps: List<RemoteAppGroupAppDto>,
    ): Boolean

    suspend fun applyAccounts(values: List<RemoteAccountDto>)

    suspend fun applyPolicies(values: List<RemotePolicyDto>)

    suspend fun applyDevices(values: List<RemoteDeviceDto>)

    suspend fun applyPolicyRules(values: List<RemotePolicyRuleDto>)

    suspend fun applyDailyLimits(values: List<RemoteDailyLimitDto>)

    suspend fun applyAppGroups(values: List<RemoteAppGroupDto>)

    suspend fun applyAppGroupApps(values: List<RemoteAppGroupAppDto>)

    suspend fun applyAccessRequests(values: List<RemoteAccessRequestDto>)

    suspend fun applyExtraTimeGrants(values: List<RemoteExtraTimeGrantDto>)
}
