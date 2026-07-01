package com.contentfilter.core.network.remote

import com.contentfilter.core.network.dto.RemotePolicyDto
import com.contentfilter.core.network.dto.RemotePolicyRuleDto

interface RemotePolicyRepository {
    suspend fun pullPolicies(updatedAfterIso: String?): RemoteResult<List<RemotePolicyDto>>

    suspend fun pullPolicyRules(updatedAfterIso: String?): RemoteResult<List<RemotePolicyRuleDto>>

    suspend fun upsertPolicy(policy: RemotePolicyDto): RemoteResult<Unit>

    suspend fun upsertPolicyRule(rule: RemotePolicyRuleDto): RemoteResult<Unit>
}
