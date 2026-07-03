package com.contentfilter.core.network.remote

import com.contentfilter.core.network.dto.RemotePolicyDto
import com.contentfilter.core.network.dto.RemotePolicyRuleDto
import javax.inject.Inject

class SupabaseRemotePolicyRepository
    @Inject
    constructor(
        private val client: SupabaseRestClient,
    ) : RemotePolicyRepository {
        override suspend fun pullPolicies(updatedAfterIso: String?): RemoteResult<List<RemotePolicyDto>> =
            client.selectUpdatedSince(SupabaseTable.Policies, updatedAfterIso).mapArray(RemotePolicyDto::fromJson)

        override suspend fun pullPolicyRules(updatedAfterIso: String?): RemoteResult<List<RemotePolicyRuleDto>> =
            client.selectUpdatedSince(
                SupabaseTable.PolicyRules,
                updatedAfterIso,
            ).mapArray(RemotePolicyRuleDto::fromJson)

        override suspend fun upsertPolicy(policy: RemotePolicyDto): RemoteResult<Unit> =
            client.upsert(SupabaseTable.Policies, policy.toJson())

        override suspend fun upsertPolicyRule(rule: RemotePolicyRuleDto): RemoteResult<Unit> =
            client.upsert(SupabaseTable.PolicyRules, rule.toJson())
    }
