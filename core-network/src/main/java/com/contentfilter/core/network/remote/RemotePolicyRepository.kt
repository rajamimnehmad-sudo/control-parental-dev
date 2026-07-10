package com.contentfilter.core.network.remote

import com.contentfilter.core.network.dto.RemotePolicyDto
import com.contentfilter.core.network.dto.RemotePolicyRuleDto

interface RemotePolicyRepository {
    suspend fun pullPolicies(updatedAfterIso: String?): RemoteResult<List<RemotePolicyDto>>

    suspend fun pullPolicyRules(updatedAfterIso: String?): RemoteResult<List<RemotePolicyRuleDto>>

    suspend fun pullPoliciesForDevice(deviceId: String): RemoteResult<List<RemotePolicyDto>>

    suspend fun pullPolicyById(policyId: String): RemoteResult<List<RemotePolicyDto>>

    suspend fun pullPolicyRulesForPolicy(policyId: String): RemoteResult<List<RemotePolicyRuleDto>>

    suspend fun upsertPolicy(policy: RemotePolicyDto): RemoteResult<Unit>

    suspend fun upsertPolicyRule(rule: RemotePolicyRuleDto): RemoteResult<Unit>

    suspend fun notifyPolicyChanged(
        requestId: String,
        deviceId: String,
        policyId: String,
        revision: Long,
    ): RemoteResult<Unit>
}
